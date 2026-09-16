package com.ruoyi.system.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.ai.ImageHash;
import com.ruoyi.system.domain.TzPlot;
import com.ruoyi.system.company.TzCompanyContext;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.mapper.TzFollowUpTaskMapper;
import com.ruoyi.system.mapper.TzPlotMapper;
import com.ruoyi.system.mapper.TzScoutingRecordMapper;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田记录 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzScoutingRecordServiceImpl implements ITzScoutingRecordService
{
    /** 趋势图默认回看天数 */
    private static final int TREND_DAYS = 14;

    @Autowired
    private TzScoutingRecordMapper tzScoutingRecordMapper;

    @Autowired
    private TzCompanyContext companyContext;

    @Autowired
    private TzFollowUpTaskMapper tzFollowUpTaskMapper;

    @Autowired
    private TzPlotMapper tzPlotMapper;

    /**
     * 查询巡田记录
     *
     * @param recordId 巡田记录ID
     * @return 巡田记录
     */
    @Override
    public TzScoutingRecord selectTzScoutingRecordById(Long recordId)
    {
        return tzScoutingRecordMapper.selectTzScoutingRecordById(recordId);
    }

    /**
     * 查询巡田记录列表
     *
     * @param tzScoutingRecord 巡田记录
     * @return 巡田记录集合
     */
    @Override
    public List<TzScoutingRecord> selectTzScoutingRecordList(TzScoutingRecord tzScoutingRecord)
    {
        tzScoutingRecord.setCompanyId(companyContext.currentCompanyId());
        return tzScoutingRecordMapper.selectTzScoutingRecordList(tzScoutingRecord);
    }

    /**
     * 新增巡田记录
     *
     * @param tzScoutingRecord 巡田记录
     * @return 结果
     */
    @Override
    public int insertTzScoutingRecord(TzScoutingRecord tzScoutingRecord)
    {
        tzScoutingRecord.setCompanyId(companyContext.currentCompanyId());
        tzScoutingRecord.setDeptId(companyContext.currentDeptId());
        fillImageHash(tzScoutingRecord);
        return tzScoutingRecordMapper.insertTzScoutingRecord(tzScoutingRecord);
    }

    /**
     * 落库前把图片 MD5 补上。
     *
     * <p>这个字段是预置样张匹配的唯一入口，之前没有任何一处写它 ——
     * DiagnosisService 读到的永远是 null，于是「命中样张就给稳定结论」这条保底链路
     * 实际上从来没有生效过，界面上却写着会去查。放在这里算，是因为记录保存是
     * 图片归属关系唯一的确定点。
     *
     * <p>只在字段还是空的时候算：调用方若显式给了哈希（例如从别处导入的登记数据），
     * 就尊重它，不去覆盖。
     */
    private void fillImageHash(TzScoutingRecord record)
    {
        if (record == null || StringUtils.isNotBlank(record.getImageHash()))
        {
            return;
        }
        String hash = ImageHash.ofUploadedFile(record.getImageUrl());
        if (hash != null)
        {
            record.setImageHash(hash);
        }
    }

    /**
     * 修改巡田记录
     *
     * @param tzScoutingRecord 巡田记录
     * @return 结果
     */
    @Override
    public int updateTzScoutingRecord(TzScoutingRecord tzScoutingRecord)
    {
        // 换图后哈希必须跟着换，否则新图会命中旧图登记的那条结论。
        // 没传 imageUrl 时 fillImageHash 不会动它，而 update 语句是动态拼接的，
        // 未赋值的列不会进 SQL，因此也不会把已有的哈希清空。
        fillImageHash(tzScoutingRecord);
        return tzScoutingRecordMapper.updateTzScoutingRecord(tzScoutingRecord);
    }

    /**
     * 删除巡田记录（同时清理其复查任务）
     *
     * @param recordId 巡田记录ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteTzScoutingRecordById(Long recordId)
    {
        tzFollowUpTaskMapper.deleteTzFollowUpTaskByRecordIds(new Long[] { recordId });
        return tzScoutingRecordMapper.deleteTzScoutingRecordById(recordId);
    }

    /**
     * 批量删除巡田记录（同时清理其复查任务）
     *
     * @param recordIds 需要删除的巡田记录ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteTzScoutingRecordByIds(Long[] recordIds)
    {
        tzFollowUpTaskMapper.deleteTzFollowUpTaskByRecordIds(recordIds);
        return tzScoutingRecordMapper.deleteTzScoutingRecordByIds(recordIds);
    }

    /**
     * 首页看板统计数据
     *
     * @return 汇总指标
     */
    @Override
    public Map<String, Object> selectDashboardStats()
    {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> summary = tzScoutingRecordMapper.selectDashboardSummary();
        stats.putAll(summary == null ? new HashMap<String, Object>() : summary);
        stats.put("plotCount", tzPlotMapper.countTzPlot(new TzPlot()));
        stats.put("pendingTaskCount", tzFollowUpTaskMapper.countPendingTasks());
        return stats;
    }

    /**
     * 首页看板图表数据
     *
     * @return 各维度分布与趋势
     */
    @Override
    public Map<String, Object> selectDashboardCharts()
    {
        Map<String, Object> charts = new HashMap<>();
        charts.put("diagnosisDistribution", tzScoutingRecordMapper.selectDiagnosisDistribution());
        charts.put("riskDistribution", tzScoutingRecordMapper.selectRiskDistribution());
        charts.put("sourceDistribution", tzScoutingRecordMapper.selectSourceDistribution());
        charts.put("scoutTrend", tzScoutingRecordMapper.selectScoutTrend(TREND_DAYS));
        return charts;
    }
}
