package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 巡田记录对象 tz_scouting_record
 *
 * 这是「巡田 → 诊断 → 建议 → 报告 → 复查」闭环的主表：
 *   plotId + imageUrl + symptomText  →  diagnose()   → diagnosisName/confidence/riskLevel
 *                                    →  suggest()    → suggestion / dosageGuardHit
 *                                    →  generateReport() → reportText
 *                                    →  同时写入 tz_follow_up_task
 *
 * @author tianzhen
 */
public class TzScoutingRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 巡田记录ID */
    private Long recordId;

    /** 地块ID */
    private Long plotId;

    /** 巡田时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "巡田时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date scoutTime;

    /** 现场图片访问地址 */
    private String imageUrl;

    /** 图片MD5（预置映射匹配用） */
    private String imageHash;

    /** 发生部位（1叶片 2果实 3枝干 4根部） */
    @Excel(name = "发生部位", readConverterExp = "1=叶片,2=果实,3=枝干,4=根部")
    private String plantPart;

    /** 症状描述 */
    @Excel(name = "症状描述")
    @Size(max = 1000, message = "症状描述长度不能超过1000个字符")
    private String symptomText;

    /** 严重度（1轻 2中 3重） */
    @Excel(name = "严重度", readConverterExp = "1=轻度,2=中度,3=重度")
    private String severity;

    /** 诊断病虫害/缺素名称 */
    @Excel(name = "诊断结果")
    private String diagnosisName;

    /** 置信度（0-100） */
    @Excel(name = "置信度")
    private BigDecimal confidence;

    /** 风险等级（1低 2中 3高） */
    @Excel(name = "风险等级", readConverterExp = "1=低风险,2=中风险,3=高风险")
    private String riskLevel;

    /** 判断依据 */
    private String diagnosisBasis;

    /** 诊断来源（preset预置 llm大模型 fallback降级 manual人工） */
    @Excel(name = "诊断来源")
    private String diagnosisSource;

    /** 命中的知识库条目ID */
    private Long knowledgeId;

    /** RAG四段式防治建议 */
    private String suggestion;

    /** 建议来源标注 */
    private String suggestionSource;

    /** 剂量校验是否拦截（0未拦截 1已拦截） */
    private String dosageGuardHit;

    /** 巡田报告全文 */
    private String reportText;

    /** 报告生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reportTime;

    /** 状态（0待诊断 1已诊断 2已生成报告 3待复查 4已复查） */
    @Excel(name = "状态", readConverterExp = "0=待诊断,1=已诊断,2=已生成报告,3=待复查,4=已复查")
    private String status;

    // ------------------------------------------------------------------
    // 非持久化字段：列表联表展示用
    // ------------------------------------------------------------------

    /** 地块名称（联表 tz_plot） */
    @Excel(name = "地块名称")
    private String plotName;

    /** 作物类型（联表 tz_plot） */
    private String cropType;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    @NotNull(message = "地块不能为空")
    public Long getPlotId()
    {
        return plotId;
    }

    public void setPlotId(Long plotId)
    {
        this.plotId = plotId;
    }

    public Date getScoutTime()
    {
        return scoutTime;
    }

    public void setScoutTime(Date scoutTime)
    {
        this.scoutTime = scoutTime;
    }

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public String getImageHash()
    {
        return imageHash;
    }

    public void setImageHash(String imageHash)
    {
        this.imageHash = imageHash;
    }

    public String getPlantPart()
    {
        return plantPart;
    }

    public void setPlantPart(String plantPart)
    {
        this.plantPart = plantPart;
    }

    public String getSymptomText()
    {
        return symptomText;
    }

    public void setSymptomText(String symptomText)
    {
        this.symptomText = symptomText;
    }

    public String getSeverity()
    {
        return severity;
    }

    public void setSeverity(String severity)
    {
        this.severity = severity;
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

    public String getDiagnosisSource()
    {
        return diagnosisSource;
    }

    public void setDiagnosisSource(String diagnosisSource)
    {
        this.diagnosisSource = diagnosisSource;
    }

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public String getSuggestion()
    {
        return suggestion;
    }

    public void setSuggestion(String suggestion)
    {
        this.suggestion = suggestion;
    }

    public String getSuggestionSource()
    {
        return suggestionSource;
    }

    public void setSuggestionSource(String suggestionSource)
    {
        this.suggestionSource = suggestionSource;
    }

    public String getDosageGuardHit()
    {
        return dosageGuardHit;
    }

    public void setDosageGuardHit(String dosageGuardHit)
    {
        this.dosageGuardHit = dosageGuardHit;
    }

    public String getReportText()
    {
        return reportText;
    }

    public void setReportText(String reportText)
    {
        this.reportText = reportText;
    }

    public Date getReportTime()
    {
        return reportTime;
    }

    public void setReportTime(Date reportTime)
    {
        this.reportTime = reportTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPlotName()
    {
        return plotName;
    }

    public void setPlotName(String plotName)
    {
        this.plotName = plotName;
    }

    public String getCropType()
    {
        return cropType;
    }

    public void setCropType(String cropType)
    {
        this.cropType = cropType;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("recordId", getRecordId())
            .append("plotId", getPlotId())
            .append("scoutTime", getScoutTime())
            .append("imageUrl", getImageUrl())
            .append("plantPart", getPlantPart())
            .append("symptomText", getSymptomText())
            .append("severity", getSeverity())
            .append("diagnosisName", getDiagnosisName())
            .append("confidence", getConfidence())
            .append("riskLevel", getRiskLevel())
            .append("diagnosisSource", getDiagnosisSource())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
