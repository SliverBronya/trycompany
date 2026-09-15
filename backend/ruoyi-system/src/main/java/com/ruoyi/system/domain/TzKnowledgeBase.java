package com.ruoyi.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 柑橘植保知识库对象 tz_knowledge_base
 *
 * 说明：medicine_note 是剂量校验（DosageGuard）的唯一依据 —— AI 生成建议中出现的
 * 任何用量数值，都必须能在此字段中找到，否则会被拦截。
 *
 * @author tianzhen
 */
public class TzKnowledgeBase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 知识库ID */
    private Long knowledgeId;

    /** 作物类型 */
    @Excel(name = "作物类型")
    private String cropType;

    /** 病虫害/缺素名称 */
    @Excel(name = "名称")
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过100个字符")
    private String diseaseName;

    /** 类别（1病害 2虫害 3营养问题） */
    @Excel(name = "类别", readConverterExp = "1=病害,2=虫害,3=营养问题")
    private String category;

    /** 症状描述 */
    @Excel(name = "症状描述")
    private String symptoms;

    /**
     * 特征性表现（本病独有）。
     *
     * <p>与 symptoms 的分工：symptoms 是完整的症状叙述，读起来像教科书；
     * key_features 只摘出「本病看得见、别的病少见」的那几条，用途是**检索**——
     * 它参与打分且权重最高，因为一个词出现在这里，意味着它对这个病有区分度。
     *
     * <p>写这个字段有条硬规矩：**只写阳性描述（有/是/呈），不写「与X病的区别」**。
     * 一旦写进别的病名，检索就会串味 —— 查「圆锥形突起」会命中溃疡病条目里
     * 那句「与疮痂病区别：疮痂病呈圆锥形突起」。要写鉴别，写进 differential。
     */
    @Excel(name = "特征性表现")
    private String keyFeatures;

    /**
     * 鉴别要点（与易混病怎么区分）。
     *
     * <p>**不参与检索打分**，只作为上下文交给大模型，让它逐条比对后给结论。
     * 这个字段里必然出现别的病名，这正是它的用途；也正因为如此，
     * 它一旦参与打分就会把条目之间搅在一起。
     */
    private String differential;

    /** 典型症状图片地址 */
    private String typicalImage;

    /** 发生条件 */
    private String triggerConditions;

    /** 防治措施 */
    private String prevention;

    /** 用药注意 */
    private String medicineNote;

    /** 安全说明 */
    private String safetyNote;

    /** 资料来源 */
    @Excel(name = "资料来源")
    @NotBlank(message = "资料来源不能为空")
    @Size(max = 255, message = "资料来源长度不能超过255个字符")
    private String source;

    /** 状态（0启用 1停用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getKnowledgeId()
    {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId)
    {
        this.knowledgeId = knowledgeId;
    }

    public String getCropType()
    {
        return cropType;
    }

    public void setCropType(String cropType)
    {
        this.cropType = cropType;
    }

    public String getDiseaseName()
    {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName)
    {
        this.diseaseName = diseaseName;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getSymptoms()
    {
        return symptoms;
    }

    public void setSymptoms(String symptoms)
    {
        this.symptoms = symptoms;
    }

    public String getKeyFeatures()
    {
        return keyFeatures;
    }

    public void setKeyFeatures(String keyFeatures)
    {
        this.keyFeatures = keyFeatures;
    }

    public String getDifferential()
    {
        return differential;
    }

    public void setDifferential(String differential)
    {
        this.differential = differential;
    }

    public String getTypicalImage()
    {
        return typicalImage;
    }

    public void setTypicalImage(String typicalImage)
    {
        this.typicalImage = typicalImage;
    }

    public String getTriggerConditions()
    {
        return triggerConditions;
    }

    public void setTriggerConditions(String triggerConditions)
    {
        this.triggerConditions = triggerConditions;
    }

    public String getPrevention()
    {
        return prevention;
    }

    public void setPrevention(String prevention)
    {
        this.prevention = prevention;
    }

    public String getMedicineNote()
    {
        return medicineNote;
    }

    public void setMedicineNote(String medicineNote)
    {
        this.medicineNote = medicineNote;
    }

    public String getSafetyNote()
    {
        return safetyNote;
    }

    public void setSafetyNote(String safetyNote)
    {
        this.safetyNote = safetyNote;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
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
            .append("knowledgeId", getKnowledgeId())
            .append("cropType", getCropType())
            .append("diseaseName", getDiseaseName())
            .append("category", getCategory())
            .append("symptoms", getSymptoms())
            .append("keyFeatures", getKeyFeatures())
            .append("differential", getDifferential())
            .append("typicalImage", getTypicalImage())
            .append("triggerConditions", getTriggerConditions())
            .append("prevention", getPrevention())
            .append("medicineNote", getMedicineNote())
            .append("safetyNote", getSafetyNote())
            .append("source", getSource())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
