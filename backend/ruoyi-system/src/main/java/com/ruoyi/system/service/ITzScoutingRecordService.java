package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.TzScoutingRecord;

/**
 * 巡田记录 服务层
 *
 * @author tianzhen
 */
public interface ITzScoutingRecordService
{
    /**
     * 查询巡田记录
     *
     * @param recordId 巡田记录ID
     * @return 巡田记录
     */
    public TzScoutingRecord selectTzScoutingRecordById(Long recordId);

    /**
     * 查询巡田记录列表
     *
     * @param tzScoutingRecord 巡田记录
     * @return 巡田记录集合
     */
    public List<TzScoutingRecord> selectTzScoutingRecordList(TzScoutingRecord tzScoutingRecord);

    /**
     * 新增巡田记录
     *
     * @param tzScoutingRecord 巡田记录
     * @return 结果
     */
    public int insertTzScoutingRecord(TzScoutingRecord tzScoutingRecord);

    /**
     * 修改巡田记录
     *
     * @param tzScoutingRecord 巡田记录
     * @return 结果
     */
    public int updateTzScoutingRecord(TzScoutingRecord tzScoutingRecord);

    /**
     * 删除巡田记录（同时清理其复查任务）
     *
     * @param recordId 巡田记录ID
     * @return 结果
     */
    public int deleteTzScoutingRecordById(Long recordId);

    /**
     * 批量删除巡田记录（同时清理其复查任务）
     *
     * @param recordIds 需要删除的巡田记录ID
     * @return 结果
     */
    public int deleteTzScoutingRecordByIds(Long[] recordIds);

    /**
     * 首页看板统计数据
     *
     * @return 汇总指标
     */
    public Map<String, Object> selectDashboardStats();

    /**
     * 首页看板图表数据
     *
     * @return 各维度分布与趋势
     */
    public Map<String, Object> selectDashboardCharts();
}
