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
import com.ruoyi.system.ai.SuggestionResult;
import com.ruoyi.system.ai.SuggestionService;

/**
 * 防治建议 信息操作处理
 *
 * 权限说明：生成建议与 AI 诊断共用 tz:record:diagnose。二者都是「对某条巡田记录发起一次
 * AI 辅助处理」，拆成两个权限串只会让角色配置多一步、收益为零；而只读查询仍走
 * tz:record:query，与诊断结论的可见性保持一致。
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/suggestion")
public class TzSuggestionController extends BaseController
{
    @Autowired
    private SuggestionService suggestionService;

    /**
     * 为指定巡田记录生成防治建议（含剂量合规校验）。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   是否允许调用大模型；为 false 时直接用知识库条目原文组装
     */
    @PreAuthorize("@ss.hasPermi('tz:record:diagnose')")
    @Log(title = "防治建议", businessType = BusinessType.OTHER)
    @PostMapping("/generate/{recordId}")
    public AjaxResult generate(@PathVariable Long recordId,
                               @RequestParam(defaultValue = "true") boolean useLlm)
    {
        SuggestionResult result = suggestionService.suggest(recordId, useLlm);
        return success(result);
    }

    /**
     * 查询某条巡田记录已生成的防治建议（只读，不重新生成）。
     */
    @PreAuthorize("@ss.hasPermi('tz:record:query')")
    @GetMapping("/result/{recordId}")
    public AjaxResult result(@PathVariable Long recordId)
    {
        return success(suggestionService.selectResult(recordId));
    }
}
