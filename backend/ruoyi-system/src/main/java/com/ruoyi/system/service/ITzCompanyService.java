package com.ruoyi.system.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.TzCompany;
import com.ruoyi.system.domain.TzInvite;

/**
 * 公司与邀请 服务层
 *
 * @author tianzhen
 */
public interface ITzCompanyService
{
    /** 创建公司（免审核，创建者自动成为该公司管理员） */
    public TzCompany createCompany(TzCompany company, SysUser user);

    /** 我所属的公司（未加入返回 null） */
    public TzCompany getMyCompany(Long userId);

    /** 成员列表（限本公司成员可见） */
    public List<HashMap<String, Object>> getMembers(Long userId);

    /** 移出成员（限公司管理员；不能移创建者和自己） */
    public int removeMember(Long targetUserId, SysUser operator);

    /** 改成员岗位/部门（限公司管理员） */
    public int updateMemberDept(Long targetUserId, Long deptId, SysUser operator);

    /** 生成邀请码（限公司管理员） */
    public TzInvite createInvite(TzInvite invite, SysUser operator);

    /** 本公司的邀请码列表 */
    public List<TzInvite> listInvites(SysUser operator);

    /** 作废邀请码（限公司管理员） */
    public int cancelInvite(Long inviteId, SysUser operator);

    /** 按邀请码查看详情（未登录可读：给「收到邀请码的人」先看看是谁邀请的） */
    public Map<String, Object> getInviteByCode(String code);

    /** 接受邀请：绑定公司/部门，授予角色 */
    public TzCompany acceptInvite(String code, SysUser user);
}
