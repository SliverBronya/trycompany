package com.ruoyi.system.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 农产品供求信息对象 tz_supply_demand
 *
 * 说明：供求信息仅提供撮合线索，平台不担保交易、不代收货款，
 * 联系方式由发布者自行填写，请谨慎甄别。
 *
 * @author tianzhen
 */
public class TzSupplyDemand extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 供求信息ID */
    private Long infoId;
    /**
     * 归属公司ID。
     * 由 Service 在查询前按当前用户填好；超级管理员此项为 null，表示不过滤。
     */
    private Long companyId;

    /** 归属部门ID（若依 @DataScope 用） */
    private Long deptId;


    /** 信息类型（1供应 2求购） */
    @Excel(name = "信息类型", readConverterExp = "1=供应,2=求购")
    @NotBlank(message = "信息类型不能为空")
    private String infoType;

    /** 作物类型 */
    @Excel(name = "作物类型")
    @NotBlank(message = "品种不能为空")
    @Size(max = 50, message = "作物类型长度不能超过50个字符")
    private String cropType;

    /** 细分品种 */
    @Excel(name = "细分品种")
    @Size(max = 50, message = "细分品种长度不能超过50个字符")
    private String variety;

    /** 数量（吨） */
    @Excel(name = "数量(吨)")
    private BigDecimal quantity;

    /** 期望价格（描述性，如"面议""3元/斤以上"） */
    @Excel(name = "期望价格")
    @Size(max = 50, message = "期望价格长度不能超过50个字符")
    private String priceExpect;

    /** 产区/所在地 */
    @Excel(name = "产区/所在地")
    @Size(max = 100, message = "产区/所在地长度不能超过100个字符")
    private String region;

    /** 联系人 */
    @Excel(name = "联系人")
    @NotBlank(message = "联系人不能为空")
    @Size(max = 64, message = "联系人长度不能超过64个字符")
    private String contactName;

    /** 联系电话 */
    @Excel(name = "联系电话")
    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    private String contactPhone;

    /** 详细描述 */
    @Excel(name = "详细描述")
    @Size(max = 1000, message = "详细描述长度不能超过1000个字符")
    private String description;

    /** 状态（0已发布 1已下架 2已成交） */
    @Excel(name = "状态", readConverterExp = "0=已发布,1=已下架,2=已成交")
    private String status;

    public Long getInfoId()
    {
        return infoId;
    }

    public void setInfoId(Long infoId)
    {
        this.infoId = infoId;
    }

    public Long getCompanyId()
    {
        return companyId;
    }

    public void setCompanyId(Long companyId)
    {
        this.companyId = companyId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }


    public String getInfoType()
    {
        return infoType;
    }

    public void setInfoType(String infoType)
    {
        this.infoType = infoType;
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

    public BigDecimal getQuantity()
    {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity)
    {
        this.quantity = quantity;
    }

    public String getPriceExpect()
    {
        return priceExpect;
    }

    public void setPriceExpect(String priceExpect)
    {
        this.priceExpect = priceExpect;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
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
            .append("infoId", getInfoId())
            .append("infoType", getInfoType())
            .append("cropType", getCropType())
            .append("variety", getVariety())
            .append("quantity", getQuantity())
            .append("priceExpect", getPriceExpect())
            .append("region", getRegion())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("description", getDescription())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
