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
import com.ruoyi.system.domain.TzPlot;
import com.ruoyi.system.service.ITzPlotService;

/**
 * 巡田地块 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/plot")
public class TzPlotController extends BaseController
{
    @Autowired
    private ITzPlotService tzPlotService;

    /**
     * 查询地块列表
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzPlot tzPlot)
    {
        startPage();
        List<TzPlot> list = tzPlotService.selectTzPlotList(tzPlot);
        return getDataTable(list);
    }

    /**
     * 查询地块下拉选项（不分页，供巡田记录/复查任务表单选择）
     */
    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        TzPlot query = new TzPlot();
        query.setStatus("0");
        return success(tzPlotService.selectTzPlotList(query));
    }

    /**
     * 获取地块详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:query')")
    @GetMapping(value = "/{plotId}")
    public AjaxResult getInfo(@PathVariable Long plotId)
    {
        return success(tzPlotService.selectTzPlotById(plotId));
    }

    /**
     * 新增地块
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:add')")
    @Log(title = "巡田地块", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzPlot tzPlot)
    {
        if (!tzPlotService.checkPlotNameUnique(tzPlot))
        {
            return error("新增地块「" + tzPlot.getPlotName() + "」失败，地块名称已存在");
        }
        tzPlot.setCreateBy(getUsername());
        return toAjax(tzPlotService.insertTzPlot(tzPlot));
    }

    /**
     * 修改地块
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:edit')")
    @Log(title = "巡田地块", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzPlot tzPlot)
    {
        if (!tzPlotService.checkPlotNameUnique(tzPlot))
        {
            return error("修改地块「" + tzPlot.getPlotName() + "」失败，地块名称已存在");
        }
        tzPlot.setUpdateBy(getUsername());
        return toAjax(tzPlotService.updateTzPlot(tzPlot));
    }

    /**
     * 删除地块
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:remove')")
    @Log(title = "巡田地块", businessType = BusinessType.DELETE)
    @DeleteMapping("/{plotIds}")
    public AjaxResult remove(@PathVariable Long[] plotIds)
    {
        return toAjax(tzPlotService.deleteTzPlotByIds(plotIds));
    }

    /**
     * 导出地块列表
     */
    @PreAuthorize("@ss.hasPermi('tz:plot:export')")
    @Log(title = "巡田地块", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzPlot tzPlot)
    {
        List<TzPlot> list = tzPlotService.selectTzPlotList(tzPlot);
        ExcelUtil<TzPlot> util = new ExcelUtil<TzPlot>(TzPlot.class);
        util.exportExcel(response, list, "巡田地块数据");
    }
}
