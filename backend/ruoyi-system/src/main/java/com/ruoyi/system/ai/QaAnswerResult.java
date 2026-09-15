package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 农技问答一次应答的结果。
 *
 * 与诊断/建议一致，这里也把「这句话是谁说的」写进结果：
 * {@link #source} 说明答案来自知识库还是大模型，{@link #answerStatus} 说明
 * 这次到底答没答上来。农户看不懂置信度，但看得懂「知识库里没查到」。
 *
 * @author tianzhen
 */
public class QaAnswerResult
{
    /** 会话ID */
    private Long sessionId;

    /** 本次提问 */
    private String question;

    /** 本次回答（纯文本） */
    private String answer;

    /** 回答来源：kb=知识库条目原文组装，llm=大模型生成；未作答时为 null */
    private String source;

    /** 来源的中文说明，直接展示给用户 */
    private String sourceText;

    /** 应答状态（0正常 1无匹配已建议咨询农技员 2降级） */
    private String answerStatus;

    /** 是否降级（本次未得到大模型结果） */
    private boolean degraded;

    /** 降级原因 */
    private String degradeReason;

    /** 依据的知识库条目ID */
    private Long knowledgeId;

    /** 依据的知识库条目名称 */
    private String knowledgeName;

    /** 依据的知识库条目资料来源 */
    private String knowledgeSource;

    /** 命中的知识条目名称，供前端展示检索过程 */
    private List<String> matchedKnowledge = new ArrayList<>();

    /** 剂量校验是否命中拦截 */
    private boolean dosageGuardHit;

    /** 被拦截的用量表述 */
    private List<String> rejectedDosages = new ArrayList<>();

    /** 通过校验的用量表述 */
    private List<String> acceptedDosages = new ArrayList<>();

    /** 剂量校验说明 */
    private String guardExplain;

    /** 大模型不可用且知识库未覆盖：明确告知「本次不作答」 */
    private boolean refused;

    /** 免责声明 */
    private String disclaimer;

    /** 回答消息ID（落库后回填） */
    private Long messageId;

    /** 实际使用的模型 */
    private String model;

    /** 本次调用耗时（毫秒） */
    private Long latencyMs;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public String getAnswer()
    {
        return answer;
    }

    public void setAnswer(String answer)
    {
        this.answer = answer;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSourceText()
    {
        return sourceText;
    }

    public void setSourceText(String sourceText)
    {
        this.sourceText = sourceText;
    }

    public String getAnswerStatus()
    {
        return answerStatus;
    }

    public void setAnswerStatus(String answerStatus)
    {
        this.answerStatus = answerStatus;
    }

    public boolean isDegraded()
    {
        return degraded;
    }

    public void setDegraded(boolean degraded)
    {
        this.degraded = degraded;
    }

    public String getDegradeReason()
    {
        return degradeReason;
    }

    public void setDegradeReason(String degradeReason)
    {
        this.degradeReason = degradeReason;
    }

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public String getKnowledgeName()
    {
        return knowledgeName;
    }

    public void setKnowledgeName(String knowledgeName)
    {
        this.knowledgeName = knowledgeName;
    }

    public String getKnowledgeSource()
    {
        return knowledgeSource;
    }

    public void setKnowledgeSource(String knowledgeSource)
    {
        this.knowledgeSource = knowledgeSource;
    }

    public List<String> getMatchedKnowledge()
    {
        return matchedKnowledge;
    }

    public void setMatchedKnowledge(List<String> matchedKnowledge)
    {
        this.matchedKnowledge = matchedKnowledge;
    }

    public boolean isDosageGuardHit()
    {
        return dosageGuardHit;
    }

    public void setDosageGuardHit(boolean dosageGuardHit)
    {
        this.dosageGuardHit = dosageGuardHit;
    }

    public List<String> getRejectedDosages()
    {
        return rejectedDosages;
    }

    public void setRejectedDosages(List<String> rejectedDosages)
    {
        this.rejectedDosages = rejectedDosages;
    }

    public List<String> getAcceptedDosages()
    {
        return acceptedDosages;
    }

    public void setAcceptedDosages(List<String> acceptedDosages)
    {
        this.acceptedDosages = acceptedDosages;
    }

    public String getGuardExplain()
    {
        return guardExplain;
    }

    public void setGuardExplain(String guardExplain)
    {
        this.guardExplain = guardExplain;
    }

    public boolean isRefused()
    {
        return refused;
    }

    public void setRefused(boolean refused)
    {
        this.refused = refused;
    }

    public String getDisclaimer()
    {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer)
    {
        this.disclaimer = disclaimer;
    }

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Long getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs)
    {
        this.latencyMs = latencyMs;
    }
}
