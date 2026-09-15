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
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.service.ITzKnowledgeBaseService;

/**
 * 柑橘植保知识库 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/knowledge")
public class TzKnowledgeBaseController extends BaseController
{
    @Autowired
    private ITzKnowledgeBaseService tzKnowledgeBaseService;

    /**
     * 查询知识库列表
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzKnowledgeBase tzKnowledgeBase)
    {
        startPage();
        List<TzKnowledgeBase> list = tzKnowledgeBaseService.selectTzKnowledgeBaseList(tzKnowledgeBase);
        return getDataTable(list);
    }

    /**
     * 获取知识库条目详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:query')")
    @GetMapping(value = "/{knowledgeId}")
    public AjaxResult getInfo(@PathVariable Long knowledgeId)
    {
        return success(tzKnowledgeBaseService.selectTzKnowledgeBaseById(knowledgeId));
    }

    /**
     * 新增知识库条目
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:add')")
    @Log(title = "植保知识库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzKnowledgeBase tzKnowledgeBase)
    {
        tzKnowledgeBase.setCreateBy(getUsername());
        return toAjax(tzKnowledgeBaseService.insertTzKnowledgeBase(tzKnowledgeBase));
    }

    /**
     * 修改知识库条目
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:edit')")
    @Log(title = "植保知识库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzKnowledgeBase tzKnowledgeBase)
    {
        tzKnowledgeBase.setUpdateBy(getUsername());
        return toAjax(tzKnowledgeBaseService.updateTzKnowledgeBase(tzKnowledgeBase));
    }

    /**
     * 删除知识库条目
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:remove')")
    @Log(title = "植保知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{knowledgeIds}")
    public AjaxResult remove(@PathVariable Long[] knowledgeIds)
    {
        return toAjax(tzKnowledgeBaseService.deleteTzKnowledgeBaseByIds(knowledgeIds));
    }

    /**
     * 导出知识库列表
     */
    @PreAuthorize("@ss.hasPermi('tz:knowledge:export')")
    @Log(title = "植保知识库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzKnowledgeBase tzKnowledgeBase)
    {
        List<TzKnowledgeBase> list = tzKnowledgeBaseService.selectTzKnowledgeBaseList(tzKnowledgeBase);
        ExcelUtil<TzKnowledgeBase> util = new ExcelUtil<TzKnowledgeBase>(TzKnowledgeBase.class);
        util.exportExcel(response, list, "植保知识库数据");
    }
}
