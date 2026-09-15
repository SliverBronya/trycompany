package com.ruoyi.web.controller.tianzhen;

import java.util.Date;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.ai.ImageDescriptionService;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田记录 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/record")
public class TzScoutingRecordController extends BaseController
{
    @Autowired
    private ITzScoutingRecordService tzScoutingRecordService;

    @Autowired
    private ImageDescriptionService imageDescriptionService;

    /**
     * 查询巡田记录列表
     */
    @PreAuthorize("@ss.hasPermi('tz:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzScoutingRecord tzScoutingRecord)
    {
        startPage();
        List<TzScoutingRecord> list = tzScoutingRecordService.selectTzScoutingRecordList(tzScoutingRecord);
        return getDataTable(list);
    }

    /**
     * 获取巡田记录详细信息（含诊断依据、防治建议、巡田报告全文）
     */
    @PreAuthorize("@ss.hasPermi('tz:record:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable Long recordId)
    {
        return success(tzScoutingRecordService.selectTzScoutingRecordById(recordId));
    }

    /**
     * 新增巡田记录（只登记现场信息，诊断由 /tz/diagnosis/diagnose 触发）
     *
     * <p>照片是硬性要求：整套诊断以图像为主要依据，预置样张匹配更是只看图片
     * 哈希，没有照片连保底路径都走不了。文字描述则不强制 —— 用户可以点「AI 识别」
     * 让系统先写一版，也可以自己写；两者都没有时记录照样存得下，
     * 只是诊断会退回「仅依据文字」的那条路径。
     */
    @PreAuthorize("@ss.hasPermi('tz:record:add')")
    @Log(title = "巡田记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzScoutingRecord tzScoutingRecord)
    {
        if (StringUtils.isBlank(tzScoutingRecord.getImageUrl()))
        {
            throw new ServiceException("请先上传现场照片：诊断以图像为主要依据，没有照片无法给出结论。");
        }
        if (tzScoutingRecord.getScoutTime() == null)
        {
            tzScoutingRecord.setScoutTime(new Date());
        }
        if (tzScoutingRecord.getStatus() == null)
        {
            tzScoutingRecord.setStatus("0");
        }
        tzScoutingRecord.setCreateBy(getUsername());
        tzScoutingRecordService.insertTzScoutingRecord(tzScoutingRecord);
        // 返回主键，前端拿 recordId 继续调诊断接口
        return success(tzScoutingRecord);
    }

    /**
     * 根据刚上传的照片自动生成「症状描述」，供登记表单预填。
     *
     * <p>之所以按 imageUrl 而不是 recordId 取图：这个动作发生在记录保存<b>之前</b>，
     * 此刻还没有主键。照片读完即用，不落任何业务数据。
     *
     * <p>权限用 add 或 edit 任一即可。让新建的人用不了这个功能，
     * 等于把「照片必填」和「描述自动生成」这两条自相矛盾地拆开。
     */
    @PreAuthorize("@ss.hasPermi('tz:record:add') or @ss.hasPermi('tz:record:edit')")
    @Log(title = "照片识别", businessType = BusinessType.OTHER)
    @PostMapping("/describe-image")
    public AjaxResult describeImage(@RequestParam String imageUrl,
                                    @RequestParam(required = false) String plantPart,
                                    @RequestParam(required = false) String cropType)
    {
        return success(imageDescriptionService.describe(imageUrl, cropType, plantPart));
    }

    /**
     * 修改巡田记录
     */
    @PreAuthorize("@ss.hasPermi('tz:record:edit')")
    @Log(title = "巡田记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzScoutingRecord tzScoutingRecord)
    {
        tzScoutingRecord.setUpdateBy(getUsername());
        return toAjax(tzScoutingRecordService.updateTzScoutingRecord(tzScoutingRecord));
    }

    /**
     * 删除巡田记录（级联删除其复查任务）
     */
    @PreAuthorize("@ss.hasPermi('tz:record:remove')")
    @Log(title = "巡田记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(tzScoutingRecordService.deleteTzScoutingRecordByIds(recordIds));
    }

    /**
     * 导出巡田记录
     */
    @PreAuthorize("@ss.hasPermi('tz:record:export')")
    @Log(title = "巡田记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzScoutingRecord tzScoutingRecord)
    {
        List<TzScoutingRecord> list = tzScoutingRecordService.selectTzScoutingRecordList(tzScoutingRecord);
        ExcelUtil<TzScoutingRecord> util = new ExcelUtil<TzScoutingRecord>(TzScoutingRecord.class);
        util.exportExcel(response, list, "巡田记录数据");
    }
}
