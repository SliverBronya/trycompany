package com.ruoyi.system.service.impl;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.company.TzCompanyContext;
import com.ruoyi.system.domain.TzCompany;
import com.ruoyi.system.domain.TzInvite;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.TzCompanyMapper;
import com.ruoyi.system.mapper.TzInviteMapper;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.ITzCompanyService;

/**
 * 公司与邀请 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzCompanyServiceImpl implements ITzCompanyService
{
    /** 公司管理员角色。由 sql/tz_company_role.sql 创建（data_scope=4，本部门及以下） */
    public static final long ROLE_COMPANY_ADMIN = 3L;

    /** 普通成员角色（若依自带，含田诊全部菜单） */
    public static final long ROLE_MEMBER = 2L;

    @Autowired
    private TzCompanyMapper companyMapper;

    @Autowired
    private TzInviteMapper inviteMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private TzCompanyContext companyContext;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------ 创建

    /**
     * 创建公司（免审核）。
     *
     * 创建者自动成为该公司管理员，并归属到这家公司 ——
     * 否则建完公司自己反而看不到数据，还得再走一遍邀请流程，那太荒谬了。
     */
    @Override
    @Transactional
    public TzCompany createCompany(TzCompany company, SysUser user)
    {
        if (user.getCompanyId() != null)
        {
            throw new ServiceException("你已属于「" + companyNameOf(user.getCompanyId()) + "」，一人只能属于一家公司");
        }
        if (companyMapper.checkCompanyNameUnique(company.getCompanyName()) != null)
        {
            throw new ServiceException("公司名称「" + company.getCompanyName() + "」已存在");
        }

        // 1. 建顶层部门节点：公司的部门树从这里往下长
        SysDept dept = new SysDept();
        dept.setParentId(0L);
        dept.setDeptName(company.getCompanyName());
        dept.setOrderNum(1);
        dept.setStatus("0");
        dept.setCreateBy(user.getUserName());
        deptMapper.insertDept(dept);

        // 2. 建公司记录，创建者记为 owner
        company.setDeptId(dept.getDeptId());
        company.setOwnerUserId(user.getUserId());
        company.setStatus("0");
        company.setCreateBy(user.getUserName());
        companyMapper.insertTzCompany(company);

        // 3. 创建者归入这家公司并授管理员角色
        user.setCompanyId(company.getCompanyId());
        user.setDeptId(dept.getDeptId());
        updateUserCompany(user.getUserId(), company.getCompanyId(), dept.getDeptId());
        userService.insertUserAuth(user.getUserId(), new Long[] { ROLE_COMPANY_ADMIN, ROLE_MEMBER });

        return company;
    }

    // ------------------------------------------------------------ 查询

    @Override
    public TzCompany getMyCompany(Long userId)
    {
        return companyMapper.selectTzCompanyByUserId(userId);
    }

    @Override
    public List<HashMap<String, Object>> getMembers(Long userId)
    {
        Long companyId = requireMyCompanyId(userId);
        return companyMapper.selectMembersByCompanyId(companyId);
    }

    // ------------------------------------------------------------ 成员管理

    @Override
    @Transactional
    public int removeMember(Long targetUserId, SysUser operator)
    {
        Long companyId = requireCompanyIdAsAdmin(operator);
        TzCompany company = companyMapper.selectTzCompanyById(companyId);

        if (targetUserId.equals(company.getOwnerUserId()))
        {
            throw new ServiceException("公司创建者不能被移出（要停用公司请在系统管理里操作）");
        }
        if (targetUserId.equals(operator.getUserId()))
        {
            throw new ServiceException("不能移出自己");
        }
        int n = companyMapper.clearUserCompany(targetUserId);
        if (n == 0)
        {
            throw new ServiceException("该成员不存在或不属于你的公司");
        }
        return n;
    }

    @Override
    public int updateMemberDept(Long targetUserId, Long deptId, SysUser operator)
    {
        Long companyId = requireCompanyIdAsAdmin(operator);
        return companyMapper.updateUserDept(targetUserId, companyId, deptId);
    }

    // ------------------------------------------------------------ 邀请

    @Override
    @Transactional
    public TzInvite createInvite(TzInvite invite, SysUser operator)
    {
        Long companyId = requireCompanyIdAsAdmin(operator);
        invite.setCompanyId(companyId);
        invite.setCode(generateCode());
        if (invite.getMaxUses() == null || invite.getMaxUses() <= 0)
        {
            invite.setMaxUses(1);
        }
        if (invite.getExpireTime() == null)
        {
            // 默认 7 天有效：够把码发到人手里，也不会一直挂着一个敞开的口子
            invite.setExpireTime(DateUtils.addDays(new Date(), 7));
        }
        if (invite.getRoleId() == null)
        {
            invite.setRoleId(ROLE_MEMBER);
        }
        invite.setCreateBy(operator.getUserName());
        inviteMapper.insertTzInvite(invite);
        return invite;
    }

    @Override
    public List<TzInvite> listInvites(SysUser operator)
    {
        Long companyId = requireCompanyIdAsAdmin(operator);
        TzInvite query = new TzInvite();
        query.setCompanyId(companyId);
        return inviteMapper.selectTzInviteList(query);
    }

    @Override
    public int cancelInvite(Long inviteId, SysUser operator)
    {
        Long companyId = requireCompanyIdAsAdmin(operator);
        TzInvite invite = inviteMapper.selectTzInviteById(inviteId);
        if (invite == null || !companyId.equals(invite.getCompanyId()))
        {
            throw new ServiceException("邀请码不存在或不属于你的公司");
        }
        return inviteMapper.cancelTzInvite(inviteId);
    }

    /**
     * 按邀请码查看详情。未登录可读 —— 收到码的人得先知道「是谁邀请我、加入哪家」
     * 才会决定要不要注册。这里只吐公司名和有效期，不吐内部信息。
     */
    @Override
    public Map<String, Object> getInviteByCode(String code)
    {
        TzInvite invite = inviteMapper.selectTzInviteByCode(code);
        Map<String, Object> result = new HashMap<>();
        if (invite == null)
        {
            result.put("valid", false);
            result.put("reason", "邀请码不存在");
            return result;
        }
        result.put("companyName", invite.getCompanyName());
        result.put("expireTime", invite.getExpireTime());
        result.put("maxUses", invite.getMaxUses());
        result.put("usedCount", invite.getUsedCount());
        boolean expired = invite.getExpireTime() != null && invite.getExpireTime().before(new Date());
        boolean usedUp = invite.getUsedCount() >= invite.getMaxUses();
        boolean cancelled = "2".equals(invite.getStatus());
        result.put("valid", "0".equals(invite.getStatus()) && !expired && !usedUp);
        if (expired) result.put("reason", "邀请码已过期");
        else if (usedUp) result.put("reason", "邀请码已被用完");
        else if (cancelled) result.put("reason", "邀请码已作废");
        return result;
    }

    /**
     * 接受邀请。
     *
     * 已加入公司的人不能再接受 —— 一次校验不做，就会出现
     * 「A 公司的员工点了 B 公司的邀请码，两边数据都乱了」的事故。
     */
    @Override
    @Transactional
    public TzCompany acceptInvite(String code, SysUser user)
    {
        if (user.getCompanyId() != null)
        {
            throw new ServiceException("你已属于「" + companyNameOf(user.getCompanyId()) + "」，退出后才能接受新的邀请");
        }
        TzInvite invite = inviteMapper.selectTzInviteByCode(code);
        if (invite == null)
        {
            throw new ServiceException("邀请码不存在");
        }
        if ("2".equals(invite.getStatus()))
        {
            throw new ServiceException("邀请码已作废");
        }
        if (invite.getExpireTime() != null && invite.getExpireTime().before(new Date()))
        {
            throw new ServiceException("邀请码已过期");
        }
        if (invite.getUsedCount() >= invite.getMaxUses())
        {
            throw new ServiceException("邀请码已被用完");
        }

        // 原子地用掉一次：两个新用户同时用同一个只剩 1 次的码，只能成功一个
        if (inviteMapper.consumeTzInvite(invite.getInviteId(), user.getUserId()) == 0)
        {
            throw new ServiceException("邀请码已被用完");
        }

        user.setCompanyId(invite.getCompanyId());
        Long deptId = invite.getDeptId() != null ? invite.getDeptId()
                : companyMapper.selectTzCompanyById(invite.getCompanyId()).getDeptId();
        user.setDeptId(deptId);
        updateUserCompany(user.getUserId(), invite.getCompanyId(), deptId);

        Long roleId = invite.getRoleId() != null ? invite.getRoleId() : ROLE_MEMBER;
        userService.insertUserAuth(user.getUserId(), new Long[] { roleId });

        return companyMapper.selectTzCompanyById(invite.getCompanyId());
    }

    // ------------------------------------------------------------ 内部工具

    /** 取「我所属的公司ID」，并确认确实存在（防脏数据） */
    private Long requireMyCompanyId(Long userId)
    {
        TzCompany company = companyMapper.selectTzCompanyByUserId(userId);
        if (company == null)
        {
            throw new ServiceException("你还没有加入任何公司");
        }
        return company.getCompanyId();
    }

    /** 取「我所属的公司ID」，并确认当前用户是这家公司的管理员或超管 */
    private Long requireCompanyIdAsAdmin(SysUser operator)
    {
        if (companyContext.isSuperAdmin())
        {
            Long companyId = operator.getCompanyId();
            if (companyId == null)
            {
                throw new ServiceException("请先在请求里指明要操作的公司");
            }
            return companyId;
        }
        TzCompany company = companyMapper.selectTzCompanyByUserId(operator.getUserId());
        if (company == null)
        {
            throw new ServiceException("你还没有加入任何公司");
        }
        if (!operator.getUserId().equals(company.getOwnerUserId()))
        {
            throw new ServiceException("只有公司管理员才能执行此操作");
        }
        return company.getCompanyId();
    }

    /** 邀请码：8 位大写字母+数字，去掉易混淆的 0/O/1/I */
    private String generateCode()
    {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++)
        {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String companyNameOf(Long companyId)
    {
        TzCompany company = companyMapper.selectTzCompanyById(companyId);
        return company != null ? company.getCompanyName() : String.valueOf(companyId);
    }

    private void updateUserCompany(Long userId, Long companyId, Long deptId)
    {
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setCompanyId(companyId);
        update.setDeptId(deptId);
        userMapper.updateUser(update);
    }
}
