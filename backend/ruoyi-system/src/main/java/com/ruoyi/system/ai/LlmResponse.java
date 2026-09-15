package com.ruoyi.system.ai;

/**
 * 一次大模型调用的原始返回。
 *
 * @author tianzhen
 */
public class LlmResponse
{
    /** 模型输出的正文 */
    private String content;

    /** 实际使用的服务商 */
    private String provider;

    /** 实际使用的模型名 */
    private String model;

    /** 耗时（毫秒） */
    private long latencyMs;

    /** 是否成功 */
    private boolean success;

    /** 失败原因（成功时为 null） */
    private String errorMsg;

    /** 提示词摘要，写入调用日志 */
    private String promptDigest;

    public static LlmResponse ok(String content, String provider, String model, long latencyMs, String promptDigest)
    {
        LlmResponse r = new LlmResponse();
        r.content = content;
        r.provider = provider;
        r.model = model;
        r.latencyMs = latencyMs;
        r.promptDigest = promptDigest;
        r.success = true;
        return r;
    }

    public static LlmResponse fail(String errorMsg, String provider, String model, long latencyMs, String promptDigest)
    {
        LlmResponse r = new LlmResponse();
        r.errorMsg = errorMsg;
        r.provider = provider;
        r.model = model;
        r.latencyMs = latencyMs;
        r.promptDigest = promptDigest;
        r.success = false;
        return r;
    }

    public String getContent()
    {
        return content;
    }

    public String getProvider()
    {
        return provider;
    }

    public String getModel()
    {
        return model;
    }

    public long getLatencyMs()
    {
        return latencyMs;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public String getPromptDigest()
    {
        return promptDigest;
    }
}
