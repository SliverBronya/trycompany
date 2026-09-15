package com.ruoyi.web.controller.tianzhen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 田诊助手首页看板
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/dashboard")
public class TzDashboardController extends BaseController
{
    @Autowired
    private ITzScoutingRecordService tzScoutingRecordService;

    /**
     * 看板顶部指标卡
     */
    @PreAuthorize("@ss.hasPermi('tz:dashboard:list')")
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return success(tzScoutingRecordService.selectDashboardStats());
    }

    /**
     * 看板图表数据（诊断分布 / 风险分布 / 来源占比 / 巡田趋势）
     */
    @PreAuthorize("@ss.hasPermi('tz:dashboard:list')")
    @GetMapping("/charts")
    public AjaxResult charts()
    {
        return success(tzScoutingRecordService.selectDashboardCharts());
    }
}
