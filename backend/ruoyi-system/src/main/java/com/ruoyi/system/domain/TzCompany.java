package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 公司对象 tz_company
 *
 * 一家对应一个顶层 sys_dept 节点（dept_id），部门树挂它下面，
 * 若依的 data_scope 才能顺着树往下算。
 *
 * @author tianzhen
 */
public class TzCompany extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 公司ID */
    private Long companyId;

    /** 公司名称 */
    private String companyName;

    /** 对应的顶层部门ID */
    private Long deptId;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 种植规模 */
    private String scale;

    /** 所在地 */
    private String address;

    /** 创建者用户ID（自动成为公司管理员） */
    private Long ownerUserId;

    /** 状态（0正常 1停用） */
    private String status;

    /** 服务到期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    /** 成员数（列表展示用，非表字段） */
    private Long memberCount;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }

    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }
}
