package com.ruoyi.web.controller.tianzhen;

import java.util.List;
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
import com.ruoyi.system.domain.TzImagePreset;
import com.ruoyi.system.service.ITzImagePresetService;

/**
 * 预置图片映射 信息操作处理
 *
 * 演示样张的登记入口。样张 MD5 由 scripts/register-preset.py 批量算好写入，
 * 也可以在这里手工维护。
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/preset")
public class TzImagePresetController extends BaseController
{
    @Autowired
    private ITzImagePresetService tzImagePresetService;

    /**
     * 查询预置映射列表
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzImagePreset tzImagePreset)
    {
        startPage();
        List<TzImagePreset> list = tzImagePresetService.selectTzImagePresetList(tzImagePreset);
        return getDataTable(list);
    }

    /**
     * 获取预置映射详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:query')")
    @GetMapping(value = "/{presetId}")
    public AjaxResult getInfo(@PathVariable Long presetId)
    {
        return success(tzImagePresetService.selectTzImagePresetById(presetId));
    }

    /**
     * 新增预置映射
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:add')")
    @Log(title = "预置样张映射", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzImagePreset tzImagePreset)
    {
        if (tzImagePreset.getStatus() == null)
        {
            tzImagePreset.setStatus("0");
        }
        if (tzImagePreset.getImageHash() != null)
        {
            tzImagePreset.setImageHash(tzImagePreset.getImageHash().trim().toLowerCase());
        }
        tzImagePreset.setCreateBy(getUsername());
        tzImagePresetService.insertTzImagePreset(tzImagePreset);
        return success(tzImagePreset);
    }

    /**
     * 修改预置映射
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:edit')")
    @Log(title = "预置样张映射", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzImagePreset tzImagePreset)
    {
        tzImagePreset.setUpdateBy(getUsername());
        return toAjax(tzImagePresetService.updateTzImagePreset(tzImagePreset));
    }

    /**
     * 删除预置映射
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:remove')")
    @Log(title = "预置样张映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{presetIds}")
    public AjaxResult remove(@PathVariable Long[] presetIds)
    {
        return toAjax(tzImagePresetService.deleteTzImagePresetByIds(presetIds));
    }
}
