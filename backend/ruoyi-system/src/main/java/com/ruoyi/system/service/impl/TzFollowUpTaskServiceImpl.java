package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzFollowUpTask;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.mapper.TzFollowUpTaskMapper;
import com.ruoyi.system.service.ITzFollowUpTaskService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田复查任务 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzFollowUpTaskServiceImpl implements ITzFollowUpTaskService
{
    /** 已复查 */
    private static final String STATUS_DONE = "1";

    /** 巡田记录状态：已复查 */
    private static final String RECORD_STATUS_DONE = "4";

    @Autowired
    private TzFollowUpTaskMapper tzFollowUpTaskMapper;

    @Autowired
    private ITzScoutingRecordService tzScoutingRecordService;

    /**
     * 查询复查任务
     *
     * @param taskId 复查任务ID
     * @return 复查任务
     */
    @Override
    public TzFollowUpTask selectTzFollowUpTaskById(Long taskId)
    {
        return tzFollowUpTaskMapper.selectTzFollowUpTaskById(taskId);
    }

    /**
     * 查询复查任务列表
     *
     * 查询前先刷新逾期状态，保证列表里的「已逾期」是当下算出来的，而不是靠定时任务。
     *
     * @param tzFollowUpTask 复查任务
     * @return 复查任务集合
     */
    @Override
    public List<TzFollowUpTask> selectTzFollowUpTaskList(TzFollowUpTask tzFollowUpTask)
    {
        tzFollowUpTaskMapper.refreshOverdueTasks();
        return tzFollowUpTaskMapper.selectTzFollowUpTaskList(tzFollowUpTask);
    }

    /**
     * 新增复查任务
     *
     * @param tzFollowUpTask 复查任务
     * @return 结果
     */
    @Override
    public int insertTzFollowUpTask(TzFollowUpTask tzFollowUpTask)
    {
        return tzFollowUpTaskMapper.insertTzFollowUpTask(tzFollowUpTask);
    }

    /**
     * 修改复查任务
     *
     * @param tzFollowUpTask 复查任务
     * @return 结果
     */
    @Override
    public int updateTzFollowUpTask(TzFollowUpTask tzFollowUpTask)
    {
        return tzFollowUpTaskMapper.updateTzFollowUpTask(tzFollowUpTask);
    }

    /**
     * 删除复查任务
     *
     * @param taskId 复查任务ID
     * @return 结果
     */
    @Override
    public int deleteTzFollowUpTaskById(Long taskId)
    {
        return tzFollowUpTaskMapper.deleteTzFollowUpTaskById(taskId);
    }

    /**
     * 批量删除复查任务
     *
     * @param taskIds 需要删除的复查任务ID
     * @return 结果
     */
    @Override
    public int deleteTzFollowUpTaskByIds(Long[] taskIds)
    {
        return tzFollowUpTaskMapper.deleteTzFollowUpTaskByIds(taskIds);
    }

    /**
     * 标记复查完成 / 改期
     *
     * 复查完成时同步把关联的巡田记录推进到「已复查」，闭环才算真的合上 ——
     * 否则任务列表显示已办结、巡田记录却永远停在「待复查」，两边对不上账。
     *
     * @param tzFollowUpTask 含 taskId、status、note 的任务
     * @return 结果
     */
    @Override
    public int changeTaskStatus(TzFollowUpTask tzFollowUpTask)
    {
        boolean done = STATUS_DONE.equals(tzFollowUpTask.getStatus());
        if (done && tzFollowUpTask.getFinishTime() == null)
        {
            tzFollowUpTask.setFinishTime(new Date());
        }

        int rows = tzFollowUpTaskMapper.updateTzFollowUpTask(tzFollowUpTask);

        if (rows > 0 && done)
        {
            TzFollowUpTask saved = tzFollowUpTaskMapper.selectTzFollowUpTaskById(tzFollowUpTask.getTaskId());
            if (saved != null && saved.getRecordId() != null)
            {
                TzScoutingRecord update = new TzScoutingRecord();
                update.setRecordId(saved.getRecordId());
                update.setStatus(RECORD_STATUS_DONE);
                tzScoutingRecordService.updateTzScoutingRecord(update);
            }
        }
        return rows;
    }
}
