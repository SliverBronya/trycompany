package com.ruoyi.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 农技问答消息对象 tz_qa_message
 *
 * 一问一答各存一行，role 区分。会话是为了让农户能回头翻「上次那个问题怎么说的」，
 * 因此消息只增不改：任何一次修改都会让历史记录失去作为凭据的价值。
 *
 * @author tianzhen
 */
public class TzQaMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 会话ID */
    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 角色（user/assistant） */
    @NotBlank(message = "角色不能为空")
    @Size(max = 16, message = "角色长度不能超过16个字符")
    private String role;

    /** 消息内容 */
    private String content;

    /** 用户上传图片地址 */
    @Size(max = 500, message = "图片地址长度不能超过500个字符")
    private String imageUrl;

    /** RAG命中的知识库来源标注 */
    @Size(max = 1000, message = "来源标注长度不能超过1000个字符")
    private String sources;

    /** 应答状态（0正常 1无匹配建议咨询农技员 2降级） */
    private String answerStatus;

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public String getSources()
    {
        return sources;
    }

    public void setSources(String sources)
    {
        this.sources = sources;
    }

    public String getAnswerStatus()
    {
        return answerStatus;
    }

    public void setAnswerStatus(String answerStatus)
    {
        this.answerStatus = answerStatus;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("messageId", getMessageId())
            .append("sessionId", getSessionId())
            .append("role", getRole())
            .append("content", getContent())
            .append("imageUrl", getImageUrl())
            .append("sources", getSources())
            .append("answerStatus", getAnswerStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .toString();
    }
}
