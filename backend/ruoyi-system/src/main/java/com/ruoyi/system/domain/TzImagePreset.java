package com.ruoyi.system.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 预置图片诊断映射对象 tz_image_preset
 *
 * 路线 A「保底」的实现载体：演示前把样张的 MD5 与标准诊断结论绑定，
 * 现场无论网络与 API key 是否可用，同一张图必得同一结论，演示链路 100% 稳定。
 *
 * @author tianzhen
 */
public class TzImagePreset extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 预置映射ID */
    private Long presetId;

    /** 图片MD5（匹配键） */
    @NotBlank(message = "图片MD5不能为空")
    @Size(max = 64, message = "图片MD5长度不能超过64个字符")
    private String imageHash;

    /** 图片名称 */
    @Excel(name = "图片名称")
    private String imageName;

    /** 图片访问地址 */
    private String imageUrl;

    /** 作物类型 */
    @Excel(name = "作物类型")
    private String cropType;

    /** 预置诊断结论 */
    @Excel(name = "诊断结论")
    @NotBlank(message = "诊断结论不能为空")
    private String diagnosisName;

    /**
     * 预置症状描述。
     *
     * <p>和 {@link #diagnosisBasis} 是两件事：basis 回答「为什么得出这个结论」，
     * 本字段回答「照片上看到了什么」。单独存一列，是为了让「未接入大模型时
     * 也能自动填好症状描述」这条离线保底路径有据可依 —— 它来自登记时的人工观察，
     * 不是现场现编出来的。
     */
    @Excel(name = "预置症状描述")
    private String symptomText;

    /** 预置置信度 */
    @Excel(name = "置信度")
    private BigDecimal confidence;

    /** 预置风险等级（1低 2中 3高） */
    @Excel(name = "风险等级", readConverterExp = "1=低风险,2=中风险,3=高风险")
    private String riskLevel;

    /** 判断依据（演示话术的一部分，讲清「为什么是这个结论」） */
    private String diagnosisBasis;

    /** 关联知识库条目ID */
    private Long knowledgeId;

    /** 状态（0启用 1停用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getPresetId()
    {
        return presetId;
    }

    public void setPresetId(Long presetId)
    {
        this.presetId = presetId;
    }

    public String getImageHash()
    {
        return imageHash;
    }

    public void setImageHash(String imageHash)
    {
        this.imageHash = imageHash;
    }

    public String getImageName()
    {
        return imageName;
    }

    public void setImageName(String imageName)
    {
        this.imageName = imageName;
    }

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public String getCropType()
    {
        return cropType;
    }

    public void setCropType(String cropType)
    {
        this.cropType = cropType;
    }

    public String getDiagnosisName()
    {
        return diagnosisName;
    }

    public void setDiagnosisName(String diagnosisName)
    {
        this.diagnosisName = diagnosisName;
    }

    public String getSymptomText()
    {
        return symptomText;
    }

    public void setSymptomText(String symptomText)
    {
        this.symptomText = symptomText;
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

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("presetId", getPresetId())
            .append("imageHash", getImageHash())
            .append("imageName", getImageName())
            .append("diagnosisName", getDiagnosisName())
            .append("confidence", getConfidence())
            .append("riskLevel", getRiskLevel())
            .append("knowledgeId", getKnowledgeId())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
