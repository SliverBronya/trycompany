package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 巡田复查任务对象 tz_follow_up_task
 *
 * 由巡田报告生成时自动写入，形成「发现风险 → 安排复查 → 跟踪闭环」的留痕。
 *
 * @author tianzhen
 */
public class TzFollowUpTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 复查任务ID */
    private Long taskId;
    /**
     * 归属公司ID。
     * 由 Service 在查询前按当前用户填好；超级管理员此项为 null，表示不过滤。
     */
    private Long companyId;

    /** 归属部门ID（若依 @DataScope 用） */
    private Long deptId;


    /** 关联巡田记录ID */
    @NotNull(message = "关联巡田记录不能为空")
    private Long recordId;

    /** 地块ID（冗余，便于按地块查询） */
    private Long plotId;

    /** 复查任务标题 */
    @Excel(name = "复查任务")
    @NotBlank(message = "复查任务标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String taskTitle;

    /** 复查截止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "截止日期", width = 20, dateFormat = "yyyy-MM-dd")
    private Date dueDate;

    /** 状态（0待复查 1已复查 2已逾期） */
    @Excel(name = "状态", readConverterExp = "0=待复查,1=已复查,2=已逾期")
    private String status;

    /**
     * 复查结论：病害相对上次诊断的发展趋势。
     *
     * 这是「完成复查」真正该留下的东西 —— 有了它，同一地块的多次复查才连成
     * 一条发展曲线，才看得出上次的防治到底有没有效果。
     */
    @Excel(name = "复查结论",
           readConverterExp = "controlled=已控制,shrinking=好转,stable=持平,worsening=加重,unknown=无法判断")
    private String reviewResult;

    /** 复查现场照片路径（与巡田记录同格式，存 /profile/... 相对路径） */
    private String reviewImage;

    /** 复查前已采取的措施（用了什么药、怎么处理的），用于复盘防治有效性 */
    @Excel(name = "已采取的措施")
    @Size(max = 500, message = "措施说明长度不能超过500个字符")
    private String measureTaken;

    /** 后续安排 */
    @Excel(name = "后续安排",
           readConverterExp = "none=无需处理,observe=继续观察,recheck=需再复查,treat=需再施药,expert=请专家现场查看")
    private String nextAction;

    /** 下次复查日期（nextAction 为 recheck 时填写） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "下次复查", width = 20, dateFormat = "yyyy-MM-dd")
    private Date nextDate;

    /** 复查备注 */
    @Excel(name = "复查备注")
    @Size(max = 500, message = "复查备注长度不能超过500个字符")
    private String note;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    // ------------------------------------------------------------------
    // 非持久化字段：列表联表展示用
    // ------------------------------------------------------------------

    /** 地块名称（联表 tz_plot） */
    @Excel(name = "地块名称")
    private String plotName;

    /** 诊断结果（联表 tz_scouting_record） */
    @Excel(name = "诊断结果")
    private String diagnosisName;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getCompanyId()
    {
        return companyId;
    }

    public void setCompanyId(Long companyId)
    {
        this.companyId = companyId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }


    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getPlotId()
    {
        return plotId;
    }

    public void setPlotId(Long plotId)
    {
        this.plotId = plotId;
    }

    public String getTaskTitle()
    {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle)
    {
        this.taskTitle = taskTitle;
    }

    public Date getDueDate()
    {
        return dueDate;
    }

    public void setDueDate(Date dueDate)
    {
        this.dueDate = dueDate;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getReviewResult()
    {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult)
    {
        this.reviewResult = reviewResult;
    }

    public String getReviewImage()
    {
        return reviewImage;
    }

    public void setReviewImage(String reviewImage)
    {
        this.reviewImage = reviewImage;
    }

    public String getMeasureTaken()
    {
        return measureTaken;
    }

    public void setMeasureTaken(String measureTaken)
    {
        this.measureTaken = measureTaken;
    }

    public String getNextAction()
    {
        return nextAction;
    }

    public void setNextAction(String nextAction)
    {
        this.nextAction = nextAction;
    }

    public Date getNextDate()
    {
        return nextDate;
    }

    public void setNextDate(Date nextDate)
    {
        this.nextDate = nextDate;
    }

    public String getNote()
    {
        return note;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public Date getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(Date finishTime)
    {
        this.finishTime = finishTime;
    }

    public String getPlotName()
    {
        return plotName;
    }

    public void setPlotName(String plotName)
    {
        this.plotName = plotName;
    }

    public String getDiagnosisName()
    {
        return diagnosisName;
    }

    public void setDiagnosisName(String diagnosisName)
    {
        this.diagnosisName = diagnosisName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("taskId", getTaskId())
            .append("recordId", getRecordId())
            .append("plotId", getPlotId())
            .append("taskTitle", getTaskTitle())
            .append("dueDate", getDueDate())
            .append("status", getStatus())
            .append("reviewResult", getReviewResult())
            .append("reviewImage", getReviewImage())
            .append("measureTaken", getMeasureTaken())
            .append("nextAction", getNextAction())
            .append("nextDate", getNextDate())
            .append("note", getNote())
            .append("finishTime", getFinishTime())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
