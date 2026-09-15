package com.ruoyi.system.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * 诊断结果。除了结论本身，还刻意带上「这个结论怎么来的」——
 * 来源、是否降级、降级原因、用了哪个模型、耗时。
 *
 * 这样前端能把「预置保底 / 真大模型 / 关键词降级」直接标出来，
 * 演示时不会含糊其辞，答辩被问「这是真的 AI 吗」也有据可答。
 *
 * @author tianzhen
 */
public class DiagnosisResult
{
    /** 巡田记录ID */
    private Long recordId;

    /** 诊断结论 */
    private String diagnosisName;

    /** 置信度 0-100 */
    private BigDecimal confidence;

    /** 风险等级 1低 2中 3高 */
    private String riskLevel;

    /** 判断依据 */
    private String diagnosisBasis;

    /** 其他可能解释 */
    private String alternatives;

    /** 命中的知识库条目ID */
    private Long knowledgeId;

    /** 结果来源：preset 预置映射 / llm 大模型 / fallback 关键词降级 / manual 人工 */
    private String source;

    /** 来源的中文说明，直接给前端展示 */
    private String sourceText;

    /** 是否发生了降级 */
    private boolean degraded;

    /** 降级原因，未降级时为空 */
    private String degradeReason;

    /** 知识库检索命中的条目名称，用于「依据可溯」展示 */
    private List<String> matchedKnowledge;

    /** 实际调用的服务商 */
    private String provider;

    /** 实际调用的模型 */
    private String model;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    /** 免责声明 */
    private String disclaimer;

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

    public BigDecimal getConfidence()
    {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence)
    {
        this.confidence = confidence;
    }

    public String getRiskLevel()
    {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel)
    {
        this.riskLevel = riskLevel;
    }

    public String getDiagnosisBasis()
    {
        return diagnosisBasis;
    }

    public void setDiagnosisBasis(String diagnosisBasis)
    {
        this.diagnosisBasis = diagnosisBasis;
    }

    public String getAlternatives()
    {
        return alternatives;
    }

    public void setAlternatives(String alternatives)
    {
        this.alternatives = alternatives;
    }

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
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

    public List<String> getMatchedKnowledge()
    {
        return matchedKnowledge;
    }

    public void setMatchedKnowledge(List<String> matchedKnowledge)
    {
        this.matchedKnowledge = matchedKnowledge;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
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

    public String getDisclaimer()
    {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer)
    {
        this.disclaimer = disclaimer;
    }
}
