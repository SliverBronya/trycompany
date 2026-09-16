package com.ruoyi.system.company;

import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户的「公司作用域」。
 *
 * 所有需要隔离的业务查询都从这里取值，不要各自去读 sys_user ——
 * 规则散在 6 个 Service 里，改一次规则就要改 6 处，迟早漏一处，
 * 而漏掉那一处的表现是「能看见别的公司数据」，界面上完全看不出来。
 *
 * 约定：
 *   - 超级管理员（user_id = 1）返回 null，表示**不做过滤**（与若依 data_scope=1 一致）
 *   - 普通用户返回自己所属公司的 company_id，Mapper 里据此加等值条件
 *   - 还没加入任何公司的用户返回 NO_COMPANY（一个不可能命中的哨兵值），
 *     这样他查到的列表是空的，而不是因为过滤条件缺失看到全量数据
 */
@Component
public class TzCompanyContext
{
    /** 尚未加入公司时用的哨兵值：任何真实 company_id 都不等于它，所以查询结果为空 */
    public static final Long NO_COMPANY = -1L;

    /** 当前用户是否为超级管理员（若依约定：user_id = 1 即超管，不看角色表） */
    public boolean isSuperAdmin()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser != null && loginUser.getUser() != null && loginUser.getUser().isAdmin();
    }

    /**
     * 当前用户的公司ID。
     *
     * @return 超管返回 null（不过滤）；已加入公司的返回其 company_id；未加入的返回 {@link #NO_COMPANY}
     */
    public Long currentCompanyId()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser() == null)
        {
            return NO_COMPANY;
        }
        if (loginUser.getUser().isAdmin())
        {
            return null;
        }
        Long companyId = loginUser.getUser().getCompanyId();
        return companyId == null ? NO_COMPANY : companyId;
    }

    /** 当前用户的部门ID（用于给新数据打归属标记） */
    public Long currentDeptId()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser() == null)
        {
            return null;
        }
        return loginUser.getUser().getDeptId();
    }
}
