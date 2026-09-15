package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzFollowUpTask;

/**
 * 巡田复查任务 服务层
 *
 * @author tianzhen
 */
public interface ITzFollowUpTaskService
{
    /**
     * 查询复查任务
     *
     * @param taskId 复查任务ID
     * @return 复查任务
     */
    public TzFollowUpTask selectTzFollowUpTaskById(Long taskId);

    /**
     * 查询复查任务列表
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
     * 标记复查完成 / 改期（前端「完成复查」按钮）
     *
     * @param tzFollowUpTask 含 taskId、status、note 的任务
     * @return 结果
     */
    public int changeTaskStatus(TzFollowUpTask tzFollowUpTask);
}
