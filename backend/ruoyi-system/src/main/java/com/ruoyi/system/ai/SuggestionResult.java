package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 防治建议结果。
 *
 * 和诊断结果一样，这里必须能回答「这条建议是怎么来的、哪些数字被拦下了」：
 *   - source / sourceText  建议来自大模型还是知识库原文
 *   - dosageGuardHit       是否触发了剂量拦截
 *   - rejectedDosages      被拦下的具体用量表述
 *   - knowledgeId/source   建议对应的知识库条目与文献出处
 *
 * @author tianzhen
 */
public class SuggestionResult
{
    /** 巡田记录ID */
    private Long recordId;

    /** 诊断结论 */
    private String diagnosisName;

    /** 四段式防治建议全文 */
    private String suggestion;

    /** 建议来源（llm 大模型生成 / kb 知识库原文组装） */
    private String source;

    /** 来源说明，直接展示给用户 */
    private String sourceText;

    /** 是否走了降级路径 */
    private boolean degraded;

    /** 降级原因 */
    private String degradeReason;

    /** 命中的知识库条目ID */
    private Long knowledgeId;

    /** 知识库条目标题 */
    private String knowledgeName;

    /** 知识库条目的资料来源（「建议可溯」的依据） */
    private String knowledgeSource;

    /** 剂量校验是否触发拦截 */
    private boolean dosageGuardHit;

    /** 被拦截的用量表述 */
    private List<String> rejectedDosages = new ArrayList<>();

    /** 通过校验的用量表述（这些数字在知识库里有出处） */
    private List<String> acceptedDosages = new ArrayList<>();

    /** 拦截说明，解释为什么数字被抹掉了 */
    private String guardExplain;

    /** 免责声明 */
    private String disclaimer;

    /** 模型名（走大模型时有值） */
    private String model;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getDiagnosisName()
    {
        return diagnosisName;
    }

    public void setDiagnosisName(String diagnosisName)
    {
        this.diagnosisName = diagnosisName;
    }

    public String getSuggestion()
    {
        return suggestion;
    }

    public void setSuggestion(String suggestion)
    {
        this.suggestion = suggestion;
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
        this.rejectedDosages = rejectedDosages == null ? new ArrayList<String>() : rejectedDosages;
    }

    public List<String> getAcceptedDosages()
    {
        return acceptedDosages;
    }

    public void setAcceptedDosages(List<String> acceptedDosages)
    {
        this.acceptedDosages = acceptedDosages == null ? new ArrayList<String>() : acceptedDosages;
    }

    public String getGuardExplain()
    {
        return guardExplain;
    }

    public void setGuardExplain(String guardExplain)
    {
        this.guardExplain = guardExplain;
    }

    public String getDisclaimer()
    {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer)
    {
        this.disclaimer = disclaimer;
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
