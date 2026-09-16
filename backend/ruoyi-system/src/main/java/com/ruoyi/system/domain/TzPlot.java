package com.ruoyi.system.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 巡田地块对象 tz_plot
 *
 * @author tianzhen
 */
public class TzPlot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 地块ID */
    private Long plotId;

    /**
     * 归属公司ID。
     * 由 Service 在查询前按当前用户填好；超级管理员此项为 null，表示不过滤。
     */
    private Long companyId;

    /** 归属部门ID（若依 @DataScope 用） */
    private Long deptId;

    /** 地块名称 */
    @Excel(name = "地块名称")
    @NotBlank(message = "地块名称不能为空")
    @Size(max = 100, message = "地块名称长度不能超过100个字符")
    private String plotName;

    /** 作物类型 */
    @Excel(name = "作物类型")
    private String cropType;

    /** 面积（亩） */
    @Excel(name = "面积(亩)")
    private BigDecimal area;

    /** 地块位置 */
    @Excel(name = "地块位置")
    private String location;

    /** 责任人/种植户 */
    @Excel(name = "责任人")
    private String ownerName;

    /** 种植年份 */
    @Excel(name = "种植年份")
    private Integer plantYear;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getPlotId()
    {
        return plotId;
    }

    public void setPlotId(Long plotId)
    {
        this.plotId = plotId;
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

    public BigDecimal getArea()
    {
        return area;
    }

    public void setArea(BigDecimal area)
    {
        this.area = area;
    }

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public Integer getPlantYear()
    {
        return plantYear;
    }

    public void setPlantYear(Integer plantYear)
    {
        this.plantYear = plantYear;
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
            .append("plotId", getPlotId())
            .append("plotName", getPlotName())
            .append("cropType", getCropType())
            .append("area", getArea())
            .append("location", getLocation())
            .append("ownerName", getOwnerName())
            .append("plantYear", getPlantYear())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
