package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.TzFollowUpTask;

/**
 * 巡田复查任务 数据层
 *
 * @author tianzhen
 */
public interface TzFollowUpTaskMapper
{
    /**
     * 查询复查任务
     *
     * @param taskId 复查任务ID
     * @return 复查任务
     */
    public TzFollowUpTask selectTzFollowUpTaskById(Long taskId);

    /**
     * 查询复查任务列表（含地块名称/诊断结果）
     *
     * @param tzFollowUpTask 复查任务
     * @return 复查任务集合
     */
    public List<TzFollowUpTask> selectTzFollowUpTaskList(TzFollowUpTask tzFollowUpTask);

    /**
     * 新增复查任务
     *
     * @param tzFollowUpTask 复查任务
     * @return 结果
     */
    public int insertTzFollowUpTask(TzFollowUpTask tzFollowUpTask);

    /**
     * 修改复查任务
     *
     * @param tzFollowUpTask 复查任务
     * @return 结果
     */
    public int updateTzFollowUpTask(TzFollowUpTask tzFollowUpTask);

    /**
     * 删除复查任务
     *
     * @param taskId 复查任务ID
     * @return 结果
     */
    public int deleteTzFollowUpTaskById(Long taskId);

    /**
     * 批量删除复查任务
     *
     * @param taskIds 需要删除的复查任务ID
     * @return 结果
     */
    public int deleteTzFollowUpTaskByIds(Long[] taskIds);

    /**
     * 按巡田记录删除其关联的复查任务（删记录时级联清理）
     *
     * @param recordIds 巡田记录ID数组
     * @return 结果
     */
    public int deleteTzFollowUpTaskByRecordIds(Long[] recordIds);

    /**
     * 把已过截止日期且仍待复查的任务刷成「已逾期」
     *
     * @return 结果
     */
    public int refreshOverdueTasks();

    /**
     * 首页看板：待复查任务数
     *
     * @return 数量
     */
    public int countPendingTasks();
}
