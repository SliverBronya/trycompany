package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI 调用日志对象 tz_ai_call_log
 *
 * 记录每一次大模型调用的落点：用了哪家、哪个模型、耗时、是否失败、是否走了降级。
 * 有了它，答辩时「这次结果是真 AI 还是保底」是可查证的，而不是靠嘴说。
 *
 * 注意：本表不存 prompt 全文与图片，只存摘要（prompt_digest），避免把用户数据与
 * 大体积内容堆进日志表。
 *
 * @author tianzhen
 */
public class TzAiCallLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 业务类型（diagnose 诊断 / suggest 建议 / report 报告 / qa 问答） */
    private String bizType;

    /** 业务ID（如巡田记录ID） */
    private String bizId;

    /** 服务商 */
    private String provider;

    /** 模型名 */
    private String model;

    /** 提示词摘要 */
    private String promptDigest;

    /** 耗时（毫秒） */
    private Integer latencyMs;

    /** 是否成功（0失败 1成功） */
    private String success;

    /** 是否走了降级（0否 1是） */
    private String fallbackUsed;

    /** 错误信息 */
    private String errorMsg;

    public Long getLogId()
    {
        return logId;
    }

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
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

    public String getPromptDigest()
    {
        return promptDigest;
    }

    public void setPromptDigest(String promptDigest)
    {
        this.promptDigest = promptDigest;
    }

    public Integer getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs)
    {
        this.latencyMs = latencyMs;
    }

    public String getSuccess()
    {
        return success;
    }

    public void setSuccess(String success)
    {
        this.success = success;
    }

    public String getFallbackUsed()
    {
        return fallbackUsed;
    }

    public void setFallbackUsed(String fallbackUsed)
    {
        this.fallbackUsed = fallbackUsed;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("bizType", getBizType())
            .append("bizId", getBizId())
            .append("provider", getProvider())
            .append("model", getModel())
            .append("latencyMs", getLatencyMs())
            .append("success", getSuccess())
            .append("fallbackUsed", getFallbackUsed())
            .append("errorMsg", getErrorMsg())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
