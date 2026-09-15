package com.ruoyi.system.ai;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 巡田报告结果。
 *
 * 报告是给合作社、给上级看的交付物，所以除了正文，还要带上「这份报告的结论从哪来」，
 * 以及自动排出去的复查任务 —— 报告不是终点，闭环才是。
 *
 * @author tianzhen
 */
public class ReportResult
{
    /** 巡田记录ID */
    private Long recordId;

    /** 报告正文（已转义的 HTML） */
    private String reportText;

    /** 报告生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reportTime;

    /** 本次是否复用了记录上已有的建议，而不是重新生成 */
    private boolean suggestionReused;

    /** 建议来源（llm / kb） */
    private String suggestionSource;

    /** 剂量校验是否触发拦截 */
    private boolean dosageGuardHit;

    /** 复查任务ID（自动创建或已存在的未完成任务） */
    private Long followUpTaskId;

    /** 复查任务标题 */
    private String followUpTaskTitle;

    /** 复查截止日期（due_date 是 date 列，按天出即可，别把时分秒漏给前端） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date followUpDueDate;

    /** 是否新创建了复查任务（false 表示复用了已存在的未完成任务） */
    private boolean followUpTaskCreated;

    /** 免责声明 */
    private String disclaimer;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getReportText()
    {
        return reportText;
    }

    public void setReportText(String reportText)
    {
        this.reportText = reportText;
    }

    public Date getReportTime()
    {
        return reportTime;
    }

    public void setReportTime(Date reportTime)
    {
        this.reportTime = reportTime;
    }

    public boolean isSuggestionReused()
    {
        return suggestionReused;
    }

    public void setSuggestionReused(boolean suggestionReused)
    {
        this.suggestionReused = suggestionReused;
    }

    public String getSuggestionSource()
    {
        return suggestionSource;
    }

    public void setSuggestionSource(String suggestionSource)
    {
        this.suggestionSource = suggestionSource;
    }

    public boolean isDosageGuardHit()
    {
        return dosageGuardHit;
    }

    public void setDosageGuardHit(boolean dosageGuardHit)
    {
        this.dosageGuardHit = dosageGuardHit;
    }

    public Long getFollowUpTaskId()
    {
        return followUpTaskId;
    }

    public void setFollowUpTaskId(Long followUpTaskId)
    {
        this.followUpTaskId = followUpTaskId;
    }

    public String getFollowUpTaskTitle()
    {
        return followUpTaskTitle;
    }

    public void setFollowUpTaskTitle(String followUpTaskTitle)
    {
        this.followUpTaskTitle = followUpTaskTitle;
    }

    public Date getFollowUpDueDate()
    {
        return followUpDueDate;
    }

    public void setFollowUpDueDate(Date followUpDueDate)
    {
        this.followUpDueDate = followUpDueDate;
    }

    public boolean isFollowUpTaskCreated()
    {
        return followUpTaskCreated;
    }

    public void setFollowUpTaskCreated(boolean followUpTaskCreated)
    {
        this.followUpTaskCreated = followUpTaskCreated;
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
