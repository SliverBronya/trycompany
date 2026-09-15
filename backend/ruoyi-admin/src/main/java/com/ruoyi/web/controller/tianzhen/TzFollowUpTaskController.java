package com.ruoyi.web.controller.tianzhen;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.TzFollowUpTask;
import com.ruoyi.system.service.ITzFollowUpTaskService;

/**
 * 巡田复查任务 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/followup")
public class TzFollowUpTaskController extends BaseController
{
    @Autowired
    private ITzFollowUpTaskService tzFollowUpTaskService;

    /**
     * 查询复查任务列表
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzFollowUpTask tzFollowUpTask)
    {
        startPage();
        List<TzFollowUpTask> list = tzFollowUpTaskService.selectTzFollowUpTaskList(tzFollowUpTask);
        return getDataTable(list);
    }

    /**
     * 获取复查任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:query')")
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable Long taskId)
    {
        return success(tzFollowUpTaskService.selectTzFollowUpTaskById(taskId));
    }

    /**
     * 新增复查任务（一般由巡田报告自动生成，此接口用于手工补录）
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:edit')")
    @Log(title = "复查任务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzFollowUpTask tzFollowUpTask)
    {
        if (tzFollowUpTask.getStatus() == null)
        {
            tzFollowUpTask.setStatus("0");
        }
        tzFollowUpTask.setCreateBy(getUsername());
        return toAjax(tzFollowUpTaskService.insertTzFollowUpTask(tzFollowUpTask));
    }

    /**
     * 修改复查任务
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:edit')")
    @Log(title = "复查任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzFollowUpTask tzFollowUpTask)
    {
        tzFollowUpTask.setUpdateBy(getUsername());
        return toAjax(tzFollowUpTaskService.updateTzFollowUpTask(tzFollowUpTask));
    }

    /**
     * 完成复查 / 改期
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:finish')")
    @Log(title = "复查任务", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult changeStatus(@RequestBody TzFollowUpTask tzFollowUpTask)
    {
        tzFollowUpTask.setUpdateBy(getUsername());
        return toAjax(tzFollowUpTaskService.changeTaskStatus(tzFollowUpTask));
    }

    /**
     * 删除复查任务
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:remove')")
    @Log(title = "复查任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(tzFollowUpTaskService.deleteTzFollowUpTaskByIds(taskIds));
    }

    /**
     * 导出复查任务
     */
    @PreAuthorize("@ss.hasPermi('tz:followup:export')")
    @Log(title = "复查任务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzFollowUpTask tzFollowUpTask)
    {
        List<TzFollowUpTask> list = tzFollowUpTaskService.selectTzFollowUpTaskList(tzFollowUpTask);
        ExcelUtil<TzFollowUpTask> util = new ExcelUtil<TzFollowUpTask>(TzFollowUpTask.class);
        util.exportExcel(response, list, "复查任务数据");
    }
}
