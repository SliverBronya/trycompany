package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzQaMessage;
import com.ruoyi.system.domain.TzQaSession;
import com.ruoyi.system.mapper.TzQaSessionMapper;
import com.ruoyi.system.service.ITzQaMessageService;
import com.ruoyi.system.service.ITzQaSessionService;

/**
 * 农技问答。
 *
 * 与「诊断 → 建议」那条线共用同一套纪律，只是入口从一张病叶照片换成了一句提问：
 * 先检索知识库，**证据够才答**，不够就明说不够。问答比诊断更容易出事 ——
 * 诊断至少还有照片约束着话题，问答可以问出任何东西，而农户会照着答的去做。
 *
 * 三条路径，全部在 {@link #answerStatus} 上如实标注：
 *   1) 知识库检索有区分度 → 用条目原文组装回答（source=kb）
 *   2) 知识库没覆盖但大模型可用 → 允许给通用建议，但提示词禁止出现任何
 *      农药名称与用量，且后置的剂量校验会把数字全部拦下（source=llm）
 *   3) 两条都不可用 → 明确拒答，并告诉用户怎么补充描述（answerStatus=1）
 *
 * 会话与消息落库，是因为农户需要回头翻「上次那个问题怎么说的」，
 * 也是「防治效果可留痕」这条产品主张在问答侧的体现。
 *
 * @author tianzhen
 */
@Service
public class QaService
{
    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    /** 回答来源：知识库条目原文组装 */
    public static final String SOURCE_KB = "kb";

    /** 回答来源：大模型生成 */
    public static final String SOURCE_LLM = "llm";

    /** 应答状态：正常作答 */
    public static final String STATUS_OK = "0";

    /** 应答状态：知识库无匹配，已建议咨询农技员 */
    public static final String STATUS_NO_MATCH = "1";

    /** 应答状态：降级（期望走大模型但未成功，回答改由知识库组装） */
    public static final String STATUS_DEGRADED = "2";

    /** 角色 */
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /** 会话标题取首问前多少个字 */
    private static final int TITLE_MAX = 30;

    /**
     * 无模型可用时的兜底文案。写清「为什么答不了」和「怎么办」，比一句「无法回答」有用。
     *
     * <p>注意：这条文案只在**大模型也调不通**时出现（没配 key、或调用失败）。
     * 知识库没收录但模型可用时是要正常回答的 —— 用通用农艺知识答，
     * 由 {@link #answerWithoutKnowledge} 走模型那条分支。
     */
    private static final String REFUSAL_TEXT =
            "当前没有可用的农技问答模型（未配置大模型 API Key，或调用未成功），本次无法作答。\n\n"
          + "配置 TZ_AI_API_KEY 后即可正常提问 —— 届时知识库没收录的问题也会用通用农艺知识回答。\n\n"
          + "如果情况紧急，请直接联系当地农技员现场查看。";

    @Autowired
    private ITzQaSessionService qaSessionService;

    @Autowired
    private ITzQaMessageService qaMessageService;

    @Autowired
    private TzQaSessionMapper qaSessionMapper;

    @Autowired
    private RagService ragService;

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
     * 提问并取得回答。
     *
     * @param sessionId 会话ID，为空则新建会话
     * @param question  问题原文
     * @param useLlm    本次是否允许调用大模型
     * @return 应答结果
     */
    @Transactional
    public QaAnswerResult ask(Long sessionId, String question, boolean useLlm)
    {
        if (StringUtils.isBlank(question))
        {
            throw new ServiceException("请输入您想问的问题。");
        }
        String trimmed = question.trim();

        TzQaSession session = resolveSession(sessionId, trimmed);
        saveMessage(session.getSessionId(), ROLE_USER, trimmed, null, null, null);

        QaAnswerResult result = new QaAnswerResult();
        result.setSessionId(session.getSessionId());
        result.setQuestion(trimmed);
        result.setDisclaimer(properties.getDisclaimer());

        List<KnowledgeHit> hits = ragService.retrieve(trimmed, properties.getRagTopN());
        result.setMatchedKnowledge(hits.stream()
                .map(hit -> hit.getEntry().getDiseaseName())
                .collect(Collectors.toList()));

        boolean confident = ragService.isConfident(hits);
        TzKnowledgeBase entry = confident ? hits.get(0).getEntry() : null;
        if (entry != null)
        {
            result.setKnowledgeId(entry.getKnowledgeId());
            result.setKnowledgeName(entry.getDiseaseName());
            result.setKnowledgeSource(entry.getSource());
        }

        if (confident)
        {
            answerFromKnowledge(entry, trimmed, useLlm, result);
        }
        else
        {
            answerWithoutKnowledge(trimmed, useLlm, result);
        }

        // 剂量校验：无论走哪条路径都过一遍。
        // 知识库路径传条目原文，大模型路径传 null —— 没有条目时任何用量都无从核对，
        // 全数拦截才是正确的保守行为。
        applyDosageGuard(result, entry);

        result.setAnswer(buildAnswerText(result));

        TzQaMessage saved = saveMessage(session.getSessionId(), ROLE_ASSISTANT, result.getAnswer(),
                null, sourcesText(result), result.getAnswerStatus());
        result.setMessageId(saved.getMessageId());
        return result;
    }

    /**
     * 读取一个会话的完整问答记录。
     *
     * @param sessionId 会话ID
     * @return 按时间正序的消息列表
     */
    public List<TzQaMessage> listMessages(Long sessionId)
    {
        if (sessionId == null)
        {
            return new ArrayList<>();
        }
        TzQaMessage query = new TzQaMessage();
        query.setSessionId(sessionId);
        return qaMessageService.selectTzQaMessageList(query);
    }

    // ------------------------------------------------------------------ 两条作答路径

    /**
     * 知识库检索有区分度：答案以条目原文为准，大模型只负责组织语言。
     */
    private void answerFromKnowledge(TzKnowledgeBase entry, String question, boolean useLlm, QaAnswerResult result)
    {
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
            degradeReason = tryLlm(question, entry, result);
            if (degradeReason == null)
            {
                return;
            }
        }

        buildFromKnowledgeBase(entry, result);
        result.setSource(SOURCE_KB);
        result.setSourceText("知识库条目原文组装（未使用大模型）");
        result.setAnswerStatus(STATUS_OK);
        // 用户明确关掉大模型不算「降级」，那是他自己选的；只有想调没调成才记为降级
        boolean wantedLlm = useLlm && llmClient.isAvailable();
        result.setDegraded(wantedLlm);
        result.setDegradeReason(degradeReason);
        if (wantedLlm)
        {
            result.setAnswerStatus(STATUS_DEGRADED);
        }
    }

    /**
     * 知识库没有覆盖：能调大模型就给通用建议，调不了就明确拒答。
     *
     * 这里**不**退化成「随便答点什么」——没有依据的回答，风险比不回答更高。
     */
    private void answerWithoutKnowledge(String question, boolean useLlm, QaAnswerResult result)
    {
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
            degradeReason = tryLlm(question, null, result);
            if (degradeReason == null)
            {
                return;
            }
        }

        result.setAnswer(REFUSAL_TEXT);
        result.setSource(null);
        result.setSourceText("知识库无匹配条目，未作答");
        result.setAnswerStatus(STATUS_NO_MATCH);
        result.setRefused(true);
        result.setDegraded(false);
        result.setDegradeReason(degradeReason);
        log.info("农技问答未作答：知识库检索无区分度且大模型不可用（{}）", degradeReason);
    }

    /**
     * 走大模型。返回 null 表示成功（结果已写入 result），非空表示失败原因。
     */
    private String tryLlm(String question, TzKnowledgeBase entry, QaAnswerResult result)
    {
        LlmResponse response = llmClient.chat(promptBuilder.qaSystemPrompt(),
                promptBuilder.qaUserPrompt(question, entry));
        result.setModel(response.getModel());
        result.setLatencyMs(response.getLatencyMs());

        if (!response.isSuccess())
        {
            String reason = "大模型调用失败：" + StringUtils.defaultIfBlank(response.getErrorMsg(), "未知错误");
            callLogRecorder.record("qa", result.getSessionId(), response, true, reason);
            return reason;
        }

        Map<String, Object> parsed = LlmJson.parse(response.getContent());
        String answer = LlmJson.str(parsed, "answer", null);
        if (StringUtils.isBlank(answer))
        {
            String reason = "大模型返回内容无法解析为有效回答";
            callLogRecorder.record("qa", result.getSessionId(), response, true, reason);
            return reason;
        }

        result.setAnswer(assemble(
                answer,
                LlmJson.str(parsed, "guidance", null),
                LlmJson.str(parsed, "basis", null),
                entry == null ? null : entry.getSource(),
                LlmJson.str(parsed, "sources", null),
                LlmJson.str(parsed, "disclaimer", properties.getDisclaimer())));
        result.setSource(SOURCE_LLM);
        result.setSourceText(entry == null
                ? "大模型基于通用农艺知识回答（" + response.getModel() + "，耗时 " + response.getLatencyMs()
                        + " ms）；知识库未收录该问题，回答中不含具体剂量"
                : "大模型生成（" + response.getModel() + "，耗时 " + response.getLatencyMs()
                        + " ms），依据知识库条目组织，用药与用量经比对校验");
        result.setAnswerStatus(STATUS_OK);
        result.setDegraded(false);
        result.setRefused(false);

        callLogRecorder.record("qa", result.getSessionId(), response, false, null);
        return null;
    }

    /**
     * 兜底：不调模型，直接把知识库条目组装成回答。
     *
     * 这条路径的每一句都逐字来自知识库，模型没有任何发挥余地，
     * 因此即便没有 API Key，系统也能交付一个「说得出依据」的回答。
     */
    private void buildFromKnowledgeBase(TzKnowledgeBase entry, QaAnswerResult result)
    {
        result.setAnswer(assemble(
                "根据植保知识库，您描述的情况与「" + entry.getDiseaseName() + "」相符。",
                StringUtils.defaultIfBlank(entry.getPrevention(), "知识库该条目暂未填写防治措施。"),
                StringUtils.defaultIfBlank(entry.getSymptoms(), null),
                entry.getSource(),
                null,
                properties.getDisclaimer()));
        result.setRefused(false);
    }

    // ------------------------------------------------------------------ 文本组装

    /**
     * 把各段拼成一份可读的回答。
     *
     * 「资料来源」和「免责声明」都落在正文里而不是只存数据库 —— 农户会复制、
     * 会转发给邻居、会截图发群里，来源不在正文里，可追溯就是一句空话。
     *
     * @param source     本地知识库条目的原文出处（已核实，系统据此校验用量）
     * @param references 模型本次回答所依据的**自述出处**。与 source 性质完全不同：
     *                   它由模型口述、未经系统校验，可能不成立。单独成节并标注清楚，
     *                   是为了既不失去线索，也不让它冒充「已核实来源」。
     */
    private String assemble(String answer, String guidance, String basis, String source,
                            String references, String disclaimer)
    {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "回答", answer);
        appendSection(sb, "具体建议", guidance);
        appendSection(sb, "判断依据", basis);
        appendSection(sb, "资料来源", source);
        appendReferences(sb, references);
        appendSection(sb, "免责声明", disclaimer);
        return sb.toString().trim();
    }

    /**
     * 单独渲染「本次参考来源」。
     *
     * 不与【资料来源】合并，因为两者可信度不同：知识库出处是系统校验过、
     * 且用药是照着它比的；模型自述出处只是线索。混在一起会让读者以为都核实过。
     */
    private void appendReferences(StringBuilder sb, String references)
    {
        if (StringUtils.isBlank(references))
        {
            return;
        }
        sb.append("\n【本次参考来源】\n");
        for (String line : references.split("\\r?\\n"))
        {
            String one = line.trim();
            if (one.isEmpty())
            {
                continue;
            }
            sb.append("· ").append(one).append("\n");
        }
        sb.append("（以上出处由模型自述，未经本地知识库与系统校验，请自行核实）\n");
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

    /** 剂量校验命中时，把拦截说明追加到正文里，让用户看到系统确实动过手 */
    private String buildAnswerText(QaAnswerResult result)
    {
        String text = result.getAnswer();
        if (result.isDosageGuardHit() && StringUtils.isNotBlank(result.getGuardExplain()))
        {
            text = text + "\n\n【剂量校验】\n" + result.getGuardExplain();
        }
        return text;
    }

    // ------------------------------------------------------------------ 剂量校验

    private void applyDosageGuard(QaAnswerResult result, TzKnowledgeBase entry)
    {
        if (!properties.isDosageGuardEnabled() || result.isRefused() || StringUtils.isBlank(result.getAnswer()))
        {
            return;
        }

        String medicineNote = entry == null ? null : entry.getMedicineNote();
        DosageGuardResult guard = dosageGuard.check(result.getAnswer(), medicineNote);
        result.setAcceptedDosages(guard.getAccepted());

        if (guard.isHit())
        {
            result.setAnswer(guard.getText());
            result.setDosageGuardHit(true);
            result.setRejectedDosages(guard.getRejected());
            result.setGuardExplain(guard.explain());
            log.warn("农技问答中的 {} 处用量因知识库未收录被拦截，会话ID={}", guard.getRejected().size(),
                    result.getSessionId());
        }
        else
        {
            result.setDosageGuardHit(false);
        }
    }

    // ------------------------------------------------------------------ 会话与消息

    private TzQaSession resolveSession(Long sessionId, String question)
    {
        if (sessionId == null)
        {
            TzQaSession session = new TzQaSession();
            session.setSessionTitle(question.length() > TITLE_MAX ? question.substring(0, TITLE_MAX) : question);
            session.setMsgCount(0);
            session.setStatus("0");
            try
            {
                session.setUserId(SecurityUtils.getUserId());
                session.setUserName(SecurityUtils.getUsername());
            }
            catch (Exception e)
            {
                // 无登录上下文的调用（例如脚本自测）不该让提问失败，留空即可
                session.setUserName("anonymous");
            }
            qaSessionService.insertTzQaSession(session);
            return session;
        }

        TzQaSession session = qaSessionService.selectTzQaSessionById(sessionId);
        if (session == null)
        {
            throw new ServiceException("会话不存在：" + sessionId);
        }
        if ("1".equals(session.getStatus()))
        {
            throw new ServiceException("该会话已关闭，请开启新会话提问。");
        }
        return session;
    }

    private TzQaMessage saveMessage(Long sessionId, String role, String content,
                                    String imageUrl, String sources, String answerStatus)
    {
        TzQaMessage message = new TzQaMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setImageUrl(imageUrl);
        message.setSources(sources);
        message.setAnswerStatus(answerStatus);
        try
        {
            message.setCreateBy(SecurityUtils.getUsername());
        }
        catch (Exception e)
        {
            message.setCreateBy("anonymous");
        }
        qaMessageService.insertTzQaMessage(message);
        // 计数就放在写消息的这一个出口上，而不是让每个调用方自己去加：
        // 会话列表展示的是「条」，只要有一条消息落了库就必须 +1，两处分开写迟早对不上。
        qaSessionMapper.increaseMsgCount(sessionId, 1);
        return message;
    }

    /** 来源标注写进消息里，翻历史记录时能直接看出当时依据的是哪一条 */
    private String sourcesText(QaAnswerResult result)
    {
        if (result.getMatchedKnowledge().isEmpty())
        {
            return null;
        }
        return String.join("、", result.getMatchedKnowledge());
    }
}
