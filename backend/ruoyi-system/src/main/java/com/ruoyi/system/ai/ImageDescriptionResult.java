package com.ruoyi.system.ai;

/**
 * 照片自动描述结果。
 *
 * <p>与 {@link DiagnosisResult} 一样，除了正文还带上「这段文字怎么来的」：
 * 来源、是否降级、降级原因、用了哪个模型、耗时。前端据此在描述框下面标一句
 * 「AI 识别，可修改」或者「来自预置样张登记」，用户才知道该不该信任它。
 *
 * <p>{@link #success} 为 false 时 {@link #description} 必定为空 —— 宁可让用户
 * 自己写，也不给一段看起来像描述、实际是模型编的文字。
 *
 * @author tianzhen
 */
public class ImageDescriptionResult
{
    /** 本次描述对应的图片地址，用于前端丢弃过期响应（连传两张图时只认最后一张） */
    private String imageUrl;

    /** 是否成功拿到描述 */
    private boolean success;

    /** 症状描述正文；失败时为空 */
    private String description;

    /** 来源：llm 大模型识别 / preset 预置样张登记 */
    private String source;

    /** 来源的中文说明，直接给前端展示 */
    private String sourceText;

    /** 未能自动生成的原因，成功时为空 */
    private String degradeReason;

    /** 实际调用的服务商 */
    private String provider;

    /** 实际调用的模型 */
    private String model;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    /** 免责声明，随描述一起带给前端 */
    private String disclaimer;

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSourceText()
    {
        return sourceText;
    }

    public void setSourceText(String sourceText)
    {
        this.sourceText = sourceText;
    }

    public String getDegradeReason()
    {
        return degradeReason;
    }

    public void setDegradeReason(String degradeReason)
    {
        this.degradeReason = degradeReason;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Long getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs)
    {
        this.latencyMs = latencyMs;
    }

    public String getDisclaimer()
    {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer)
    {
        this.disclaimer = disclaimer;
    }
}
