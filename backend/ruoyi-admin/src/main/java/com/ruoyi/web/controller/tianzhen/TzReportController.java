package com.ruoyi.web.controller.tianzhen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.ai.ReportResult;
import com.ruoyi.system.ai.ReportService;

/**
 * 巡田报告 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/report")
public class TzReportController extends BaseController
{
    @Autowired
    private ReportService reportService;

    /**
     * 生成巡田报告，并自动安排复查任务。
     *
     * 报告是「发现风险 → 安排复查」的触发点：生成即排复查，两件事绑在一起，
     * 才不会出现「报告发了但没人回头看」的情况。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   记录上还没有防治建议时，是否允许调用大模型来生成
     */
    @PreAuthorize("@ss.hasPermi('tz:record:report')")
    @Log(title = "巡田报告", businessType = BusinessType.OTHER)
    @PostMapping("/generate/{recordId}")
    public AjaxResult generate(@PathVariable Long recordId,
                               @RequestParam(defaultValue = "true") boolean useLlm)
    {
        ReportResult result = reportService.generate(recordId, useLlm);
        return success(result);
    }

    /**
     * 查询某条巡田记录已生成的报告（只读，不重新生成）。
     */
    @PreAuthorize("@ss.hasPermi('tz:record:query')")
    @GetMapping("/result/{recordId}")
    public AjaxResult result(@PathVariable Long recordId)
    {
        return success(reportService.selectReport(recordId));
    }
}
