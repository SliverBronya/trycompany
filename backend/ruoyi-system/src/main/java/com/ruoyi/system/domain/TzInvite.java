package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 邀请码对象 tz_invite
 *
 * @author tianzhen
 */
public class TzInvite extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 邀请ID */
    private Long inviteId;

    /** 目标公司ID */
    private Long companyId;

    /** 公司名称（展示用，非表字段） */
    private String companyName;

    /** 邀请码 */
    @Excel(name = "邀请码")
    private String code;

    /** 加入后所属部门 */
    private Long deptId;

    /** 加入后所属岗位 */
    private Long postId;

    /** 加入后授予的角色 */
    private Long roleId;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    /** 最多可用次数 */
    private Integer maxUses;

    /** 已使用次数 */
    private Integer usedCount;

    /** 状态（0有效 1已用完 2已作废） */
    private String status;

    /** 实际接受邀请的用户 */
    private Long inviteeUserId;

    public Long getInviteId() { return inviteId; }
    public void setInviteId(Long inviteId) { this.inviteId = inviteId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getInviteeUserId() { return inviteeUserId; }
    public void setInviteeUserId(Long inviteeUserId) { this.inviteeUserId = inviteeUserId; }
}
