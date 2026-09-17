package com.ruoyi.system.ai;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 田诊助手 AI 相关配置，对应 application.yml 中的 tz.ai.*
 *
 * 设计要点：API Key 走 ${TZ_AI_API_KEY:} 环境变量占位，配置文件里不出现明文。
 * 未配置 key 时 {@link #isAvailable()} 为 false，整条链路自动退化为
 * 「预置映射 + 关键词检索」，功能完整可演示，只是不再调用大模型。
 *
 * @author tianzhen
 */
@Component
@ConfigurationProperties(prefix = "tz.ai")
public class AiProperties
{
    /** 是否启用大模型调用（关掉则只走预置映射与关键词检索） */
    private boolean enabled = true;

    /** 服务商标识，写进调用日志，便于分辨结果来自哪家 */
    private String provider = "dashscope";

    /** OpenAI 兼容接口的基础地址（不含 /chat/completions） */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** API Key，务必用环境变量注入，不要写死在配置里 */
    private String apiKey = "";

    /**
     * 视觉模型专用 Key，留空则回退到 {@link #apiKey}。
     *
     * 拆成两个是为将来留口子：视觉与文本可能来自不同服务商，或同一家的两个额度。
     * 现在两者共用同一个智谱 key，所以只填 apiKey 也完全正常 —— 回退逻辑在
     * {@link #resolveApiKey(boolean)} 里，不会因为少填一个就整条链路不可用。
     */
    private String visionApiKey = "";

    /** 文本模型专用 Key，留空则回退到 {@link #apiKey} */
    private String textApiKey = "";

    /** 多模态模型名（要能读图） */
    private String visionModel = "qwen-vl-plus";

    /** 纯文本模型名（问答、报告润色） */
    private String textModel = "qwen-plus";

    /** 读超时（秒）。大模型读图偏慢，别设太短 */
    private int timeoutSeconds = 90;

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** 失败重试次数（不含首次）。只作用于 5xx、超时、网络抖动 */
    private int maxRetries = 1;

    /**
     * 限流（HTTP 429）的额外重试次数。
     *
     * 与 maxRetries 分开，是因为两者代价差两个数量级：超时一次要等 90 秒，
     * 而限流返回只要 200–500 毫秒。免费模型档（智谱 Flash 系列）实测大比例请求
     * 会被限流，不单独给它预算，等于把「模型结论」直接让给「知识库降级」。
     */
    private int rateLimitRetries = 4;

    /** 限流重试的退避基数（毫秒），第 n 次等待 n × 该值 */
    private int rateLimitBackoffMs = 400;

    /** 采样温度，诊断要稳定，取低值 */
    private double temperature = 0.2D;

    /** 检索增强时带几条知识库条目进提示词 */
    private int ragTopN = 3;

    /** 低于此置信度不采信大模型结论，转降级路径 */
    private double minConfidence = 60D;

    /** 是否启用剂量校验（合规硬约束，除非调试否则不要关） */
    private boolean dosageGuardEnabled = true;

    /** 统一的免责声明，所有对外输出都带上 */
    private String disclaimer = "本结论由 AI 辅助生成，仅作为巡田参考，不能替代农技人员的现场诊断与农药标签说明。";

    /**
     * 取本次调用该用的 Key。
     *
     * 有专用 key 就用专用的，没有就回退到通用 key —— 这样「只配一个 key」
     * 和「视觉/文本分开配」两种用法都能跑，不必让使用者先搞清楚该填哪个。
     *
     * @param vision 本次是否为视觉模型调用（带了图）
     */
    public String resolveApiKey(boolean vision)
    {
        String specific = vision ? visionApiKey : textApiKey;
        return StringUtils.isNotBlank(specific) ? specific : apiKey;
    }

    /**
     * 大模型调用是否可用：开关打开，且至少配了一个 key。
     */
    public boolean isAvailable()
    {
        if (!enabled)
        {
            return false;
        }
        return StringUtils.isNotBlank(apiKey)
                || StringUtils.isNotBlank(visionApiKey)
                || StringUtils.isNotBlank(textApiKey);
    }

    /**
     * 拼出 chat/completions 的完整地址，容忍 baseUrl 结尾带不带斜杠。
     */
    public String resolveChatUrl()
    {
        String base = StringUtils.defaultString(baseUrl).trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getVisionApiKey()
    {
        return visionApiKey;
    }

    public void setVisionApiKey(String visionApiKey)
    {
        this.visionApiKey = visionApiKey;
    }

    public String getTextApiKey()
    {
        return textApiKey;
    }

    public void setTextApiKey(String textApiKey)
    {
        this.textApiKey = textApiKey;
    }

    public String getVisionModel()
    {
        return visionModel;
    }

    public void setVisionModel(String visionModel)
    {
        this.visionModel = visionModel;
    }

    public String getTextModel()
    {
        return textModel;
    }

    public void setTextModel(String textModel)
    {
        this.textModel = textModel;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getConnectTimeoutSeconds()
    {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds)
    {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries)
    {
        this.maxRetries = maxRetries;
    }

    public int getRateLimitRetries()
    {
        return rateLimitRetries;
    }

    public void setRateLimitRetries(int rateLimitRetries)
    {
        this.rateLimitRetries = rateLimitRetries;
    }

    public int getRateLimitBackoffMs()
    {
        return rateLimitBackoffMs;
    }

    public void setRateLimitBackoffMs(int rateLimitBackoffMs)
    {
        this.rateLimitBackoffMs = rateLimitBackoffMs;
    }

    public double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(double temperature)
    {
        this.temperature = temperature;
    }

    public int getRagTopN()
    {
        return ragTopN;
    }

    public void setRagTopN(int ragTopN)
    {
        this.ragTopN = ragTopN;
    }

    public double getMinConfidence()
    {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence)
    {
        this.minConfidence = minConfidence;
    }

    public boolean isDosageGuardEnabled()
    {
        return dosageGuardEnabled;
    }

    public void setDosageGuardEnabled(boolean dosageGuardEnabled)
    {
        this.dosageGuardEnabled = dosageGuardEnabled;
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
