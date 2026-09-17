package com.ruoyi.web.controller.tianzhen;

import java.util.HashMap;
import java.util.Map;
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
import com.ruoyi.system.ai.AiProperties;
import com.ruoyi.system.ai.DiagnosisResult;
import com.ruoyi.system.ai.DiagnosisService;
import com.ruoyi.system.ai.LlmClient;

/**
 * AI 诊断 信息操作处理
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/diagnosis")
public class TzDiagnosisController extends BaseController
{
    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiProperties aiProperties;

    /**
     * 对指定巡田记录发起诊断。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   是否允许调用大模型，前端可给一个「强制走保底」的开关
     */
    @PreAuthorize("@ss.hasPermi('tz:record:diagnose')")
    @Log(title = "AI 诊断", businessType = BusinessType.OTHER)
    @PostMapping("/diagnose")
    public AjaxResult diagnose(@RequestParam Long recordId,
                               @RequestParam(defaultValue = "true") boolean useLlm)
    {
        DiagnosisResult result = diagnosisService.diagnose(recordId, useLlm);
        return success(result);
    }

    /**
     * 查询某条巡田记录当前的诊断结论（只读，不重新推理）。
     */
    @PreAuthorize("@ss.hasPermi('tz:record:query')")
    @GetMapping("/result/{recordId}")
    public AjaxResult result(@PathVariable Long recordId)
    {
        return success(diagnosisService.selectResult(recordId));
    }

    /**
     * 暴露当前 AI 配置状态给前端。
     *
     * 有了它，界面可以在没配 key 时明确提示「当前为预置保底模式」，
     * 而不是让用户以为看到的结论来自大模型。
     */
    @GetMapping("/config")
    public AjaxResult config()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("llmAvailable", llmClient.isAvailable());
        data.put("visionAvailable", aiProperties.isVisionAvailable());
        data.put("textAvailable", aiProperties.isTextAvailable());
        data.put("enabled", aiProperties.isEnabled());
        data.put("provider", llmClient.getProvider());
        data.put("visionProvider", aiProperties.resolveProvider(true));
        data.put("textProvider", aiProperties.resolveProvider(false));
        data.put("visionModel", aiProperties.getVisionModel());
        data.put("textModel", aiProperties.getTextModel());
        data.put("minConfidence", aiProperties.getMinConfidence());
        data.put("ragTopN", aiProperties.getRagTopN());
        data.put("disclaimer", aiProperties.getDisclaimer());
        boolean visionAvailable = llmClient.isVisionAvailable();
        boolean textAvailable = llmClient.isTextAvailable();
        // 文本和视觉是两条独立链路。此前只要 DeepSeek 文本 Key 可用，就把整套系统
        // 标成“已接入多模态模型”，巡田图片实际走降级时用户完全看不出来。
        data.put("mode", visionAvailable ? "vision-llm" : (textAvailable ? "text-llm" : "preset"));
        data.put("modeText", visionAvailable
                ? "图片诊断与文本问答均已接入大模型"
                : (textAvailable
                    ? "文本问答已接入 DeepSeek；图片诊断使用预置样张与知识库兜底"
                    : "未配置大模型 API Key，当前为预置映射 + 知识库检索模式"));
        return success(data);
    }
}
