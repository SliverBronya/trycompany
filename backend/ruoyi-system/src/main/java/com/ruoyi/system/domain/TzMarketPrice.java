package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 柑橘行情价格对象 tz_market_price
 *
 * 说明：行情只做信息展示，不构成交易建议；每条行情都必须写明数据来源（source），
 * 前端展示价格时与来源一并呈现，避免出现无法溯源的“编造行情”。
 *
 * @author tianzhen
 */
public class TzMarketPrice extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 行情ID */
    private Long priceId;

    /** 作物类型 */
    @Excel(name = "作物类型")
    @Size(max = 50, message = "作物类型长度不能超过50个字符")
    private String cropType;

    /** 细分品种（如沃柑/砂糖橘） */
    @Excel(name = "细分品种")
    @Size(max = 50, message = "细分品种长度不能超过50个字符")
    private String variety;

    /** 价格类型（1产地价 2批发价） */
    @Excel(name = "价格类型", readConverterExp = "1=产地价,2=批发价")
    private String priceType;

    /** 产区/市场 */
    @Excel(name = "产区/市场")
    @Size(max = 100, message = "产区/市场长度不能超过100个字符")
    private String region;

    /** 价格（元/斤） */
    @Excel(name = "价格(元/斤)")
    private BigDecimal price;

    /** 计价单位 */
    @Excel(name = "计价单位")
    @Size(max = 20, message = "计价单位长度不能超过20个字符")
    private String priceUnit;

    /** 涨跌幅（%） */
    @Excel(name = "涨跌幅(%)")
    private BigDecimal changeRate;

    /** 行情日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "行情日期", width = 20, dateFormat = "yyyy-MM-dd")
    private Date priceDate;

    /**
     * 数据来源
     *
     * 合规要求：行情展示必须能说清数据从哪来，不允许编造来源，因此该字段必填。
     */
    @Excel(name = "数据来源")
    @NotBlank(message = "数据来源不能为空")
    @Size(max = 255, message = "数据来源长度不能超过255个字符")
    private String source;

    public Long getPriceId()
    {
        return priceId;
    }

    public void setPriceId(Long priceId)
    {
        this.priceId = priceId;
    }

    public String getCropType()
    {
        return cropType;
    }

    public void setCropType(String cropType)
    {
        this.cropType = cropType;
    }

    public String getVariety()
    {
        return variety;
    }

    public void setVariety(String variety)
    {
        this.variety = variety;
    }

    public String getPriceType()
    {
        return priceType;
    }

    public void setPriceType(String priceType)
    {
        this.priceType = priceType;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getPriceUnit()
    {
        return priceUnit;
    }

    public void setPriceUnit(String priceUnit)
    {
        this.priceUnit = priceUnit;
    }

    public BigDecimal getChangeRate()
    {
        return changeRate;
    }

    public void setChangeRate(BigDecimal changeRate)
    {
        this.changeRate = changeRate;
    }

    public Date getPriceDate()
    {
        return priceDate;
    }

    public void setPriceDate(Date priceDate)
    {
        this.priceDate = priceDate;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("priceId", getPriceId())
            .append("cropType", getCropType())
            .append("variety", getVariety())
            .append("priceType", getPriceType())
            .append("region", getRegion())
            .append("price", getPrice())
            .append("priceUnit", getPriceUnit())
            .append("changeRate", getChangeRate())
            .append("priceDate", getPriceDate())
            .append("source", getSource())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
