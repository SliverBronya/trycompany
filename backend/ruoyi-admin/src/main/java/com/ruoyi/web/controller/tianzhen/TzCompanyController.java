package com.ruoyi.web.controller.tianzhen;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.TzCompany;
import com.ruoyi.system.domain.TzInvite;
import com.ruoyi.system.service.ITzCompanyService;

/**
 * 公司与邀请
 *
 * 权限说明：这里**不挂 @PreAuthorize 菜单权限**，而是把「谁能做什么」
 * 写在 Service 里按归属判断 —— 公司管理是业务逻辑，不是系统管理菜单，
 * 用菜单权限控制会要求每家公司去配一遍角色，反而漏。
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/company")
public class TzCompanyController extends BaseController
{
    @Autowired
    private ITzCompanyService companyService;

    private SysUser currentUser()
    {
        return SecurityUtils.getLoginUser().getUser();
    }

    /**
     * 创建公司（免审核）。创建者自动成为这家公司的管理员。
     */
    @PostMapping
    public AjaxResult create(@RequestBody TzCompany company)
    {
        return success(companyService.createCompany(company, currentUser()));
    }

    /**
     * 我所属的公司（未加入返回空 data）
     */
    @GetMapping("/mine")
    public AjaxResult mine()
    {
        return success(companyService.getMyCompany(currentUser().getUserId()));
    }

    /**
     * 成员列表（限本公司成员可见）
     */
    @GetMapping("/members")
    public AjaxResult members()
    {
        return success(companyService.getMembers(currentUser().getUserId()));
    }

    /**
     * 移出成员（限公司管理员；创建者与本人不能移）
     */
    @DeleteMapping("/members/{userId}")
    public AjaxResult removeMember(@PathVariable Long userId)
    {
        return toAjax(companyService.removeMember(userId, currentUser()));
    }

    /**
     * 改成员岗位（换部门）
     */
    @PutMapping("/members/{userId}/dept/{deptId}")
    public AjaxResult updateMemberDept(@PathVariable Long userId, @PathVariable Long deptId)
    {
        return toAjax(companyService.updateMemberDept(userId, deptId, currentUser()));
    }

    /**
     * 生成邀请码（限公司管理员）。默认 7 天有效、可用 1 次。
     */
    @PostMapping("/invites")
    public AjaxResult createInvite(@RequestBody TzInvite invite)
    {
        return success(companyService.createInvite(invite, currentUser()));
    }

    /**
     * 本公司的邀请码列表（限公司管理员）
     */
    @GetMapping("/invites")
    public AjaxResult listInvites()
    {
        return success(companyService.listInvites(currentUser()));
    }

    /**
     * 作废邀请码
     */
    @DeleteMapping("/invites/{inviteId}")
    public AjaxResult cancelInvite(@PathVariable Long inviteId)
    {
        return toAjax(companyService.cancelInvite(inviteId, currentUser()));
    }
}
