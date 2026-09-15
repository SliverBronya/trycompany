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
import com.ruoyi.system.domain.TzMarketPrice;
import com.ruoyi.system.service.ITzMarketPriceService;

/**
 * 柑橘行情价格 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/market")
public class TzMarketPriceController extends BaseController
{
    @Autowired
    private ITzMarketPriceService tzMarketPriceService;

    /**
     * 查询行情列表
     */
    @PreAuthorize("@ss.hasPermi('tz:market:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzMarketPrice tzMarketPrice)
    {
        startPage();
        List<TzMarketPrice> list = tzMarketPriceService.selectTzMarketPriceList(tzMarketPrice);
        return getDataTable(list);
    }

    /**
     * 获取行情详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:market:query')")
    @GetMapping(value = "/{priceId}")
    public AjaxResult getInfo(@PathVariable Long priceId)
    {
        return success(tzMarketPriceService.selectTzMarketPriceById(priceId));
    }

    /**
     * 新增行情
     */
    @PreAuthorize("@ss.hasPermi('tz:market:add')")
    @Log(title = "柑橘行情", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzMarketPrice tzMarketPrice)
    {
        tzMarketPrice.setCreateBy(getUsername());
        return toAjax(tzMarketPriceService.insertTzMarketPrice(tzMarketPrice));
    }

    /**
     * 修改行情
     */
    @PreAuthorize("@ss.hasPermi('tz:market:edit')")
    @Log(title = "柑橘行情", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzMarketPrice tzMarketPrice)
    {
        tzMarketPrice.setUpdateBy(getUsername());
        return toAjax(tzMarketPriceService.updateTzMarketPrice(tzMarketPrice));
    }

    /**
     * 删除行情
     */
    @PreAuthorize("@ss.hasPermi('tz:market:remove')")
    @Log(title = "柑橘行情", businessType = BusinessType.DELETE)
    @DeleteMapping("/{priceIds}")
    public AjaxResult remove(@PathVariable Long[] priceIds)
    {
        return toAjax(tzMarketPriceService.deleteTzMarketPriceByIds(priceIds));
    }

    /**
     * 导出行情列表
     */
    @PreAuthorize("@ss.hasPermi('tz:market:export')")
    @Log(title = "柑橘行情", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzMarketPrice tzMarketPrice)
    {
        List<TzMarketPrice> list = tzMarketPriceService.selectTzMarketPriceList(tzMarketPrice);
        ExcelUtil<TzMarketPrice> util = new ExcelUtil<TzMarketPrice>(TzMarketPrice.class);
        util.exportExcel(response, list, "柑橘行情数据");
    }
}
