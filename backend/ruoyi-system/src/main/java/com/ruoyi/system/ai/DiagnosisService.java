package com.ruoyi.system.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzKnowledgeBaseService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田诊断编排。
 *
 * 三级链路，逐级降级，任何一级失败都不会让用户空手而归：
 *
 *   1) 预置映射   —— 图片 MD5 命中登记过的样张，直接给标准结论（演示 100% 稳定）
 *   2) 大模型初诊 —— 配了 key 才走，读图 + 症状描述 + 知识库片段，输出结构化 JSON
 *   3) 关键词降级 —— 前两条都不通，退回知识库关键词检索，给一个低置信度的可解释结论
 *   4) 都不行     —— 明确报错，绝不编一个结论出来
 *
 * 第 3 级有一条例外：**模型已经看过照片、只是把握不足时，不再退到关键词检索**。
 * 退回去等于丢掉图像证据、只按文字再猜一次，而纯文字检索正是「什么病都像溃疡病」
 * 的来源（溃疡病的症状文本里「病斑/隆起/晕圈」这类通用词最多）。
 * 那种情况会把模型那个不采信的结论作为线索写进拒绝原因。
 *
 * 每一次结果都带 source 与 degradeReason，前端如实展示，不把降级结果冒充成 AI 结论。
 *
 * @author tianzhen
 */
@Service
public class DiagnosisService
{
    /** 关键词降级路径的置信度上限——降级结果不该显得和大模型一样可信 */
    private static final BigDecimal FALLBACK_CONFIDENCE = new BigDecimal("55");

    @Autowired
    private ITzScoutingRecordService scoutingRecordService;

    @Autowired
    private ITzKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private PresetDiagnosisResolver presetResolver;

    @Autowired
    private RagService ragService;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiCallLogRecorder callLogRecorder;

    @Autowired
    private AiProperties properties;

    /**
     * 对一条巡田记录做诊断，并把结论回写到记录上。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   本次是否允许调用大模型（用户可在界面上关掉，强制走保底路径）
     * @return 诊断结果
     */
    public DiagnosisResult diagnose(Long recordId, boolean useLlm)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }

        DiagnosisResult result = new DiagnosisResult();
        result.setRecordId(recordId);
        result.setDisclaimer(properties.getDisclaimer());

        // 知识库检索结果在整条链路里都要用：既作为大模型的上下文，也是降级路径的结论来源
        String query = buildQuery(record);
        List<KnowledgeHit> hits = ragService.retrieve(query, properties.getRagTopN());

        // 检索结果只是「候选」，没有区分度时不能当证据交给大模型。
        //
        // 曾经无论检索可不可信都把 topN 原样塞进提示词，于是一张没有文字描述的照片
        // 会拿到「实蝇 / 木虱 / 缺锌」这种与画面毫无关系的条目作为「知识库参考」，
        // 模型只能从里面挑一个，给出一个 60 分的错误结论 —— 界面上看起来
        // 是「AI 自信地诊断错了」，实际是把垃圾当证据喂了进去。
        // 现在：只有检索真正有区分度时才作为证据传入，否则明确告诉模型「没有可靠参考」。
        List<KnowledgeHit> evidence = ragService.isConfident(hits) ? hits : new ArrayList<>();
        result.setMatchedKnowledge(toNames(evidence));

        // ---- 第 1 级：预置映射保底 ----
        if (tryPreset(record, result))
        {
            persist(record, result);
            callLogRecorder.record("diagnose", recordId, null, false, "命中预置样张映射，未调用大模型");
            return result;
        }

        // ---- 第 2 级：大模型初诊 ----
        // 图片只读一次，往下传：既要判断本次有没有图，也要交给模型。
        // 读两次等于把一张几 MB 的照片从磁盘搬两遍。
        byte[] imageBytes = UploadedImage.read(record.getImageUrl());
        boolean hasImage = imageBytes != null && imageBytes.length > 0;

        String degradeReason = null;
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
            degradeReason = tryLlm(record, evidence, imageBytes, result);
            if (degradeReason == null)
            {
                persist(record, result);
                return result;
            }
            // 模型看过照片、给了一个把握不足的结论时，**不再退到关键词检索**。
            //
            // 退回去等于把图像证据整个丢掉，只按文字再猜一次 —— 而纯文字检索恰好
            // 就是「什么病都像溃疡病」的来源（溃疡病的症状文本里通用词最多）。
            // 一边是模型看着图说不确定，一边是字面匹配给出一个自信的名字，
            // 采信后者就是把「猜的」当成「看到的」。
            // 这种情况如实说清楚，并把模型那个不采信的结论作为线索告诉用户。
            if (hasImage && StringUtils.isNotBlank(result.getDiagnosisName()))
            {
                throw new ServiceException("看过照片后，最可能是「" + result.getDiagnosisName()
                        + "」，但把握只有 " + result.getConfidence() + "，低于 "
                        + properties.getMinConfidence() + " 的采信线，本次不作为结论。"
                        + "建议补充症状描述，或换一张更清晰、离病斑更近的照片重试。");
            }
        }

        // ---- 第 3 级：关键词检索降级 ----
        if (tryKeywordFallback(record, hits, result, degradeReason))
        {
            persist(record, result);
            callLogRecorder.record("diagnose", recordId, null, true, degradeReason);
            return result;
        }

        // ---- 第 4 级：如实说不知道 ----
        callLogRecorder.record("diagnose", recordId, null, true, degradeReason);
        throw new ServiceException("暂时无法给出可靠结论。原因：" + degradeReason
                + "；同时知识库中也没有检索到足够相关的条目。"
                + "建议补充症状描述，或直接联系当地农技员现场查看。");
    }

    /**
     * 读取某条巡田记录已存的诊断结论，不重新推理。
     *
     * 与 {@link #diagnose} 的区别很重要：详情页刷新、报告页回看都不该再打一次大模型，
     * 既浪费额度，也会让同一条记录每次看到的结论不一样。
     *
     * @param recordId 巡田记录ID
     * @return 诊断结果；该记录尚未诊断时 diagnosisName 为空
     */
    public DiagnosisResult selectResult(Long recordId)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }

        DiagnosisResult result = new DiagnosisResult();
        result.setRecordId(recordId);
        result.setDiagnosisName(record.getDiagnosisName());
        result.setConfidence(record.getConfidence());
        result.setRiskLevel(record.getRiskLevel());
        result.setDiagnosisBasis(record.getDiagnosisBasis());
        result.setKnowledgeId(record.getKnowledgeId());
        result.setSource(record.getDiagnosisSource());
        result.setSourceText(sourceText(record.getDiagnosisSource()));
        result.setDegraded("fallback".equals(record.getDiagnosisSource()));
        result.setDisclaimer(properties.getDisclaimer());

        if (record.getKnowledgeId() != null)
        {
            TzKnowledgeBase entry = knowledgeBaseService.selectTzKnowledgeBaseById(record.getKnowledgeId());
            if (entry != null)
            {
                List<String> names = new ArrayList<>();
                names.add(entry.getDiseaseName());
                result.setMatchedKnowledge(names);
            }
        }
        return result;
    }

    private String sourceText(String source)
    {
        if ("preset".equals(source)) return "预置样张映射（演示保底路径，结论不经过大模型）";
        if ("llm".equals(source)) return "多模态大模型初诊";
        if ("fallback".equals(source)) return "知识库关键词检索降级（未使用大模型）";
        if ("manual".equals(source)) return "人工录入";
        return "尚无诊断记录";
    }

    // ------------------------------------------------------------------ 各级链路

    private boolean tryPreset(TzScoutingRecord record, DiagnosisResult result)
    {
        // 正常情况下哈希在记录保存时就写好了；这里再兜一次，是因为用 SQL 直接导入的
        // 记录、或者修复前留下的老数据都没有这个值，而它们恰恰是最需要走保底路径的
        // 演示数据 —— 让它们因为一个可以现算的字段而静默退到别的链路，不值当。
        String imageHash = record.getImageHash();
        if (StringUtils.isBlank(imageHash))
        {
            imageHash = ImageHash.ofUploadedFile(record.getImageUrl());
        }

        com.ruoyi.system.domain.TzImagePreset preset = presetResolver.resolve(imageHash);
        if (preset == null)
        {
            return false;
        }

        result.setDiagnosisName(preset.getDiagnosisName());
        result.setConfidence(preset.getConfidence());
        result.setRiskLevel(preset.getRiskLevel());
        result.setDiagnosisBasis(preset.getDiagnosisBasis());
        result.setSource("preset");
        result.setSourceText("预置样张映射（演示保底路径，结论不经过大模型）");
        result.setDegraded(false);
        result.setKnowledgeId(resolveKnowledgeId(preset.getDiagnosisName(), preset.getKnowledgeId()));
        return true;
    }

    /**
     * 走大模型。返回 null 表示成功，返回非空字符串表示失败原因（即降级理由）。
     *
     * @param evidence   检索确有区分度时传入的知识库条目；否则为空列表，
     *                   提示词会据此改口，不让模型把「没参考」当成「必须低分」
     * @param imageBytes 已读出的本地图片字节，为空表示本次没有可用照片
     */
    private String tryLlm(TzScoutingRecord record, List<KnowledgeHit> evidence,
                          byte[] imageBytes, DiagnosisResult result)
    {
        boolean hasImage = imageBytes != null && imageBytes.length > 0;

        String systemPrompt = promptBuilder.diagnosisSystemPrompt();
        String userPrompt = promptBuilder.diagnosisUserPrompt(record, record.getCropType(), evidence, hasImage);

        LlmResponse response = hasImage
                ? llmClient.chatWithImage(imageBytes, UploadedImage.mimeType(record.getImageUrl()), systemPrompt, userPrompt)
                : llmClient.chat(systemPrompt, userPrompt);

        result.setProvider(response.getProvider());
        result.setModel(response.getModel());
        result.setLatencyMs(response.getLatencyMs());

        if (!response.isSuccess())
        {
            String reason = "大模型调用失败：" + StringUtils.defaultIfBlank(response.getErrorMsg(), "未知错误");
            callLogRecorder.record("diagnose", record.getRecordId(), response, true, reason);
            return reason;
        }

        Map<String, Object> parsed = LlmJson.parse(response.getContent());
        String name = LlmJson.str(parsed, "diagnosisName", null);
        if (StringUtils.isBlank(name))
        {
            String reason = "大模型返回内容无法解析为有效结论";
            callLogRecorder.record("diagnose", record.getRecordId(), response, true, reason);
            return reason;
        }

        BigDecimal confidence = LlmJson.num(parsed, "confidence", BigDecimal.ZERO);
        if (confidence.doubleValue() < properties.getMinConfidence())
        {
            // 把模型这个「不采信但可能是线索」的结论留在 result 上：
            // 调用方在「有图」时会把它作为线索写进拒绝原因 —— 用户宁可看到
            // 「最可能是 X，但把握只有 55」，也不想看到系统沉默或者换一个
            // 纯文字猜出来的名字。注意这里只填不落库，它不构成结论。
            result.setDiagnosisName(name);
            result.setConfidence(confidence);

            String reason = "大模型结论置信度 " + confidence + " 低于阈值 "
                    + properties.getMinConfidence() + "，不予采信";
            callLogRecorder.record("diagnose", record.getRecordId(), response, true, reason);
            return reason;
        }

        result.setDiagnosisName(name);
        result.setConfidence(confidence);
        result.setRiskLevel(LlmJson.code(parsed, "riskLevel", "2"));
        result.setDiagnosisBasis(LlmJson.str(parsed, "basis", "大模型未给出判断依据"));
        result.setAlternatives(LlmJson.str(parsed, "alternatives", null));
        result.setSource("llm");
        result.setSourceText("多模态大模型初诊（" + response.getModel() + "，耗时 "
                + response.getLatencyMs() + " ms）");
        result.setDegraded(false);
        result.setKnowledgeId(resolveKnowledgeId(name, null));

        callLogRecorder.record("diagnose", record.getRecordId(), response, false, null);
        return null;
    }

    private boolean tryKeywordFallback(TzScoutingRecord record, List<KnowledgeHit> hits,
                                       DiagnosisResult result, String degradeReason)
    {
        // 采信与否看的是**整组结果**有没有区分度，不是单条的绝对分
        if (!ragService.isConfident(hits))
        {
            return false;
        }
        KnowledgeHit top = hits.get(0);
        TzKnowledgeBase entry = top.getEntry();
        result.setDiagnosisName(entry.getDiseaseName());
        result.setConfidence(FALLBACK_CONFIDENCE);
        result.setRiskLevel(StringUtils.defaultIfBlank(record.getSeverity(), "2"));
        result.setDiagnosisBasis("依据症状描述在植保知识库中检索到高相关条目「" + entry.getDiseaseName()
                + "」（相关度 " + Math.round(top.getScore()) + "）。该结论未经过图像识别，"
                + "依据为知识库中该条目的症状描述，请结合田间实际情况复核。");
        result.setKnowledgeId(entry.getKnowledgeId());
        result.setSource("fallback");
        result.setSourceText("知识库关键词检索降级（未使用大模型）");
        result.setDegraded(true);
        result.setDegradeReason(degradeReason);
        return true;
    }

    // ------------------------------------------------------------------ 落库与工具

    private void persist(TzScoutingRecord record, DiagnosisResult result)
    {
        TzScoutingRecord update = new TzScoutingRecord();
        update.setRecordId(record.getRecordId());
        update.setDiagnosisName(result.getDiagnosisName());
        update.setConfidence(result.getConfidence());
        update.setRiskLevel(result.getRiskLevel());
        update.setDiagnosisBasis(result.getDiagnosisBasis());
        update.setDiagnosisSource(result.getSource());
        update.setKnowledgeId(result.getKnowledgeId());
        // 已有报告的不回退状态，避免重跑诊断把「已生成报告」打回「已诊断」
        if (record.getStatus() == null || "0".equals(record.getStatus()) || "1".equals(record.getStatus()))
        {
            update.setStatus("1");
        }
        update.setUpdateBy(record.getUpdateBy());
        scoutingRecordService.updateTzScoutingRecord(update);
    }

    /**
     * 用病名去知识库取条目ID：优先用已知ID，否则按名称精确匹配。
     * 有了它，「建议可溯」才有落脚点——前端能直接跳到对应的知识库条目。
     */
    private Long resolveKnowledgeId(String diseaseName, Long knownId)
    {
        if (knownId != null)
        {
            return knownId;
        }
        if (StringUtils.isBlank(diseaseName))
        {
            return null;
        }
        TzKnowledgeBase entry = knowledgeBaseService.selectTzKnowledgeBaseByName(diseaseName.trim());
        return entry == null ? null : entry.getKnowledgeId();
    }

    private String buildQuery(TzScoutingRecord record)
    {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(record.getCropType()))
        {
            sb.append(record.getCropType()).append(' ');
        }
        sb.append(StringUtils.defaultString(record.getSymptomText()));
        return sb.toString().trim();
    }

    private List<String> toNames(List<KnowledgeHit> hits)
    {
        List<String> names = new ArrayList<>();
        for (KnowledgeHit hit : hits)
        {
            if (hit.getEntry() != null && StringUtils.isNotBlank(hit.getEntry().getDiseaseName()))
            {
                names.add(hit.getEntry().getDiseaseName());
            }
        }
        return names;
    }
}
