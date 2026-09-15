package com.ruoyi.system.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 农技问答会话对象 tz_qa_session
 *
 * @author tianzhen
 */
public class TzQaSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long sessionId;

    /** 会话标题（取首问前若干字） */
    @Excel(name = "会话标题")
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String sessionTitle;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    @Excel(name = "用户名")
    private String userName;

    /** 消息条数 */
    @Excel(name = "消息条数")
    private Integer msgCount;

    /** 状态（0正常 1已关闭） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=已关闭")
    private String status;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getSessionTitle()
    {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle)
    {
        this.sessionTitle = sessionTitle;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public Integer getMsgCount()
    {
        return msgCount;
    }

    public void setMsgCount(Integer msgCount)
    {
        this.msgCount = msgCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("sessionId", getSessionId())
            .append("sessionTitle", getSessionTitle())
            .append("userId", getUserId())
            .append("userName", getUserName())
            .append("msgCount", getMsgCount())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
