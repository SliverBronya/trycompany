package com.ruoyi.web.controller.tianzhen;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ITzCompanyService;

/**
 * 邀请码（接受邀请）
 *
 * 「查看详情」不要求登录 —— 收到码的人得先知道是谁邀请他、加入哪家，
 * 才会决定要不要注册。详情里只有公司名和有效期，不吐内部信息。
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/invite")
public class TzInviteController extends BaseController
{
    @Autowired
    private ITzCompanyService companyService;

    /** 查看邀请详情（未登录可读） */
    @GetMapping("/{code}")
    public AjaxResult getByCode(@PathVariable String code)
    {
        Map<String, Object> info = companyService.getInviteByCode(code);
        return success(info);
    }

    /**
     * 接受邀请（需登录，且尚未加入任何公司）
     */
    @PostMapping("/{code}/accept")
    public AjaxResult accept(@PathVariable String code)
    {
        return success(companyService.acceptInvite(code, SecurityUtils.getLoginUser().getUser()));
    }
}
