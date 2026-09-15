package com.ruoyi.system.ai;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzKnowledgeBaseService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 防治建议生成。
 *
 * 这一步是整个产品主张的落点：**诊断只回答「是什么」，建议才回答「怎么办」**，
 * 而建议恰恰是最容易出事的地方 —— 一句编造的「稀释 800 倍液」看起来完全合理，
 * 却可能让农户赔掉一季收成。
 *
 * 所以这里做了三层约束：
 *   1) 提示词层：明令禁止编造用量，只能复述知识库已收录的内容
 *   2) 后置校验层：{@link DosageGuard} 逐条比对（数值, 单位）对，知识库没有的一律抹掉
 *   3) 兜底层：大模型不可用时不硬撑，直接用知识库条目原文组装一份建议
 *
 * 三层都过了之后，还会把「建议来源」和「拦截了几处用量」如实回写给记录，
 * 让界面上的人能自己判断这份建议的分量。
 *
 * @author tianzhen
 */
@Service
public class SuggestionService
{
    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    /** 建议来源：大模型生成 */
    public static final String SOURCE_LLM = "llm";

    /** 建议来源：知识库条目原文组装 */
    public static final String SOURCE_KB = "kb";

    @Autowired
    private ITzScoutingRecordService scoutingRecordService;

    @Autowired
    private ITzKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private DosageGuard dosageGuard;

    @Autowired
    private AiCallLogRecorder callLogRecorder;

    @Autowired
    private AiProperties properties;

    /**
     * 为一条已完成诊断的巡田记录生成防治建议，并回写到记录上。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   本次是否允许调用大模型
     * @return 建议结果
     */
    public SuggestionResult suggest(Long recordId, boolean useLlm)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }
        // 没有结论就没有建议的依据。这里明确拦住，好过生成一份「针对未知问题」的建议。
        if (StringUtils.isBlank(record.getDiagnosisName()))
        {
            throw new ServiceException("该巡田记录尚未完成诊断，请先执行 AI 诊断，再生成防治建议。");
        }

        SuggestionResult result = new SuggestionResult();
        result.setRecordId(recordId);
        result.setDiagnosisName(record.getDiagnosisName());
        result.setDisclaimer(properties.getDisclaimer());

        TzKnowledgeBase entry = resolveEntry(record);
        if (entry != null)
        {
            result.setKnowledgeId(entry.getKnowledgeId());
            result.setKnowledgeName(entry.getDiseaseName());
            result.setKnowledgeSource(entry.getSource());
        }

        // ---- 先试大模型 ----
        boolean generated = false;
        String degradeReason;
        if (!useLlm)
        {
            degradeReason = "本次请求指定不使用大模型";
        }
        else if (!llmClient.isAvailable())
        {
            degradeReason = "未配置大模型 API Key（设置环境变量 TZ_AI_API_KEY 后自动启用）";
        }
        else
        {
            degradeReason = tryLlm(record, entry, result);
            generated = degradeReason == null;
        }

        // ---- 兜底：直接用知识库原文组装 ----
        if (!generated)
        {
            buildFromKnowledgeBase(record, entry, result);
            result.setSource(SOURCE_KB);
            result.setSourceText("知识库条目原文组装（未使用大模型）");
            result.setDegraded(true);
            result.setDegradeReason(degradeReason);
        }

        // ---- 合规后置校验：两条路径都要过 ----
        // 兜底路径的文本本来取自知识库，理论上必然通过；仍然过一遍，
        // 是为了让「知识库里用药说明与防治措施自相矛盾」这种数据问题在界面上暴露出来。
        applyDosageGuard(result, entry);

        persist(record, result);
        return result;
    }

    /**
     * 读取某条记录已存的防治建议，不重新生成。
     *
     * @param recordId 巡田记录ID
     * @return 建议结果；尚未生成时 suggestion 为空
     */
    public SuggestionResult selectResult(Long recordId)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }

        SuggestionResult result = new SuggestionResult();
        result.setRecordId(recordId);
        result.setDiagnosisName(record.getDiagnosisName());
        result.setSuggestion(record.getSuggestion());
        result.setSource(record.getSuggestionSource());
        result.setSourceText(sourceText(record.getSuggestionSource()));
        result.setDegraded(SOURCE_KB.equals(record.getSuggestionSource()));
        result.setDosageGuardHit("1".equals(record.getDosageGuardHit()));
        result.setKnowledgeId(record.getKnowledgeId());
        result.setDisclaimer(properties.getDisclaimer());

        if (record.getKnowledgeId() != null)
        {
            TzKnowledgeBase entry = knowledgeBaseService.selectTzKnowledgeBaseById(record.getKnowledgeId());
            if (entry != null)
            {
                result.setKnowledgeName(entry.getDiseaseName());
                result.setKnowledgeSource(entry.getSource());
            }
        }
        return result;
    }

    private String sourceText(String source)
    {
        if (SOURCE_LLM.equals(source)) return "大模型生成（用药与用量经知识库比对校验）";
        if (SOURCE_KB.equals(source)) return "知识库条目原文组装（未使用大模型）";
        return "尚未生成防治建议";
    }

    // ------------------------------------------------------------------ 生成路径

    /**
     * 走大模型生成建议。返回 null 表示成功（结果已写入 result），非空表示失败原因。
     */
    private String tryLlm(TzScoutingRecord record, TzKnowledgeBase entry, SuggestionResult result)
    {
        String systemPrompt = promptBuilder.suggestionSystemPrompt();
        String userPrompt = promptBuilder.suggestionUserPrompt(record, entry);

        LlmResponse response = llmClient.chat(systemPrompt, userPrompt);
        result.setModel(response.getModel());
        result.setLatencyMs(response.getLatencyMs());

        if (!response.isSuccess())
        {
            String reason = "大模型调用失败：" + StringUtils.defaultIfBlank(response.getErrorMsg(), "未知错误");
            callLogRecorder.record("suggest", record.getRecordId(), response, true, reason);
            return reason;
        }

        Map<String, Object> parsed = LlmJson.parse(response.getContent());
        String recognition = LlmJson.str(parsed, "recognition", null);
        if (StringUtils.isBlank(recognition))
        {
            String reason = "大模型返回内容无法解析为有效建议";
            callLogRecorder.record("suggest", record.getRecordId(), response, true, reason);
            return reason;
        }

        result.setSuggestion(assemble(
                recognition,
                LlmJson.str(parsed, "basis", null),
                LlmJson.str(parsed, "prevention", null),
                LlmJson.str(parsed, "medicine", null),
                LlmJson.str(parsed, "safety", null),
                entry == null ? null : entry.getSource(),
                LlmJson.str(parsed, "disclaimer", properties.getDisclaimer())));
        result.setSource(SOURCE_LLM);
        result.setSourceText("大模型生成（" + response.getModel() + "，耗时 "
                + response.getLatencyMs() + " ms），用药与用量经知识库比对校验");
        result.setDegraded(false);

        callLogRecorder.record("suggest", record.getRecordId(), response, false, null);
        return null;
    }

    /**
     * 兜底路径：不调模型，直接把知识库条目组装成四段式建议。
     *
     * 这条路径的价值在于「没有 key 也交付得出去」，而且交付的内容**逐字来自知识库**，
     * 不存在模型自由发挥的空间。
     */
    private void buildFromKnowledgeBase(TzScoutingRecord record, TzKnowledgeBase entry, SuggestionResult result)
    {
        StringBuilder recognition = new StringBuilder(record.getDiagnosisName());
        if (record.getConfidence() != null)
        {
            recognition.append("（置信度 ").append(record.getConfidence().stripTrailingZeros().toPlainString()).append("%）");
        }

        String prevention;
        String medicine;
        String safety;
        if (entry == null)
        {
            prevention = "植保知识库中暂无与该结论对应的条目，无法给出成体系的技术措施。"
                    + "建议联系当地农技员现场查看后确定防治方案。";
            medicine = "未收录";
            safety = "在得到农技员确认前，请勿自行施用农药。";
        }
        else
        {
            prevention = StringUtils.defaultIfBlank(entry.getPrevention(), "知识库该条目暂未填写防治措施。");
            medicine = StringUtils.defaultIfBlank(entry.getMedicineNote(), "未收录");
            safety = StringUtils.defaultIfBlank(entry.getSafetyNote(), "施药请遵守农药标签与安全间隔期要求。");
        }

        result.setSuggestion(assemble(
                recognition.toString(),
                StringUtils.defaultIfBlank(record.getDiagnosisBasis(), null),
                prevention,
                medicine,
                safety,
                entry == null ? null : entry.getSource(),
                properties.getDisclaimer()));
    }

    /**
     * 把各段内容拼成一份可读的四段式建议。
     *
     * 「资料来源」必须落在正文里，而不是只存进数据库字段 —— 农技员真正会复制、
     * 会转给别人、会写进报告的是这段文本，来源不在里面，「建议可溯」就是一句空话。
     *
     * 存的是**纯文本**而不是 HTML：建议要能进导出、进报告、进消息推送，
     * 一旦在这里掺了标记，每个消费方都得再做一次剥离。
     */
    private String assemble(String recognition, String basis, String prevention,
                            String medicine, String safety, String source, String disclaimer)
    {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "识别结论", recognition);
        appendSection(sb, "判断依据", basis);
        appendSection(sb, "防治建议", prevention);
        appendSection(sb, "用药说明", medicine);
        appendSection(sb, "注意事项", safety);
        appendSection(sb, "资料来源", source);
        appendSection(sb, "免责声明", disclaimer);
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String title, String body)
    {
        if (StringUtils.isBlank(body))
        {
            return;
        }
        if (sb.length() > 0)
        {
            sb.append("\n\n");
        }
        sb.append("【").append(title).append("】\n").append(body.trim());
    }

    // ------------------------------------------------------------------ 剂量校验

    private void applyDosageGuard(SuggestionResult result, TzKnowledgeBase entry)
    {
        if (!properties.isDosageGuardEnabled())
        {
            return;
        }

        String medicineNote = entry == null ? null : entry.getMedicineNote();
        DosageGuardResult guard = dosageGuard.check(result.getSuggestion(), medicineNote);

        result.setAcceptedDosages(guard.getAccepted());
        if (guard.isHit())
        {
            result.setSuggestion(guard.getText());
            result.setDosageGuardHit(true);
            result.setRejectedDosages(guard.getRejected());
            result.setGuardExplain(guard.explain());
            log.warn("防治建议中的 {} 处用量因知识库未收录被拦截，记录ID={}", guard.getRejected().size(),
                    result.getRecordId());
        }
        else
        {
            result.setDosageGuardHit(false);
        }
    }

    // ------------------------------------------------------------------ 落库与工具

    private void persist(TzScoutingRecord record, SuggestionResult result)
    {
        TzScoutingRecord update = new TzScoutingRecord();
        update.setRecordId(record.getRecordId());
        update.setSuggestion(result.getSuggestion());
        update.setSuggestionSource(result.getSource());
        update.setDosageGuardHit(result.isDosageGuardHit() ? "1" : "0");
        scoutingRecordService.updateTzScoutingRecord(update);
    }

    /**
     * 找到本次建议所依据的知识库条目。
     *
     * 优先用诊断阶段已经绑定的条目ID —— 那是诊断时的实际依据，不该在建议阶段被重新检索改掉；
     * 只有在诊断未能绑定（例如人工录入的结论）时，才退回按病名精确匹配。
     */
    private TzKnowledgeBase resolveEntry(TzScoutingRecord record)
    {
        if (record.getKnowledgeId() != null)
        {
            TzKnowledgeBase entry = knowledgeBaseService.selectTzKnowledgeBaseById(record.getKnowledgeId());
            if (entry != null)
            {
                return entry;
            }
        }
        if (StringUtils.isNotBlank(record.getDiagnosisName()))
        {
            return knowledgeBaseService.selectTzKnowledgeBaseByName(record.getDiagnosisName().trim());
        }
        return null;
    }
}
