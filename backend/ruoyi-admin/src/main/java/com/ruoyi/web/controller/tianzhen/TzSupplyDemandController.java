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
import com.ruoyi.system.domain.TzSupplyDemand;
import com.ruoyi.system.service.ITzSupplyDemandService;

/**
 * 农产品供求信息 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/supply")
public class TzSupplyDemandController extends BaseController
{
    @Autowired
    private ITzSupplyDemandService tzSupplyDemandService;

    /**
     * 查询供求列表
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzSupplyDemand tzSupplyDemand)
    {
        startPage();
        List<TzSupplyDemand> list = tzSupplyDemandService.selectTzSupplyDemandList(tzSupplyDemand);
        return getDataTable(list);
    }

    /**
     * 获取供求详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:query')")
    @GetMapping(value = "/{infoId}")
    public AjaxResult getInfo(@PathVariable Long infoId)
    {
        return success(tzSupplyDemandService.selectTzSupplyDemandById(infoId));
    }

    /**
     * 新增供求信息
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:add')")
    @Log(title = "供求信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzSupplyDemand tzSupplyDemand)
    {
        tzSupplyDemand.setCreateBy(getUsername());
        return toAjax(tzSupplyDemandService.insertTzSupplyDemand(tzSupplyDemand));
    }

    /**
     * 修改供求信息
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:edit')")
    @Log(title = "供求信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzSupplyDemand tzSupplyDemand)
    {
        tzSupplyDemand.setUpdateBy(getUsername());
        return toAjax(tzSupplyDemandService.updateTzSupplyDemand(tzSupplyDemand));
    }

    /**
     * 变更发布状态（发布 / 下架 / 成交）
     *
     * <p>刻意不加 @Validated：这里只提交 infoId 与新状态，走完整校验会被
     * 「品种、联系人、电话不能为空」挡回来，而那几项本次压根没打算改。
     * 状态取值由 service 校验。与 /tz/followup/status 是同一套做法。
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:edit')")
    @Log(title = "供求信息", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult changeStatus(@RequestBody TzSupplyDemand tzSupplyDemand)
    {
        tzSupplyDemand.setUpdateBy(getUsername());
        return toAjax(tzSupplyDemandService.changeInfoStatus(tzSupplyDemand));
    }

    /**
     * 删除供求信息
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:remove')")
    @Log(title = "供求信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoIds}")
    public AjaxResult remove(@PathVariable Long[] infoIds)
    {
        return toAjax(tzSupplyDemandService.deleteTzSupplyDemandByIds(infoIds));
    }

    /**
     * 导出供求列表
     */
    @PreAuthorize("@ss.hasPermi('tz:supply:export')")
    @Log(title = "供求信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzSupplyDemand tzSupplyDemand)
    {
        List<TzSupplyDemand> list = tzSupplyDemandService.selectTzSupplyDemandList(tzSupplyDemand);
        ExcelUtil<TzSupplyDemand> util = new ExcelUtil<TzSupplyDemand>(TzSupplyDemand.class);
        util.exportExcel(response, list, "供求信息数据");
    }
}
