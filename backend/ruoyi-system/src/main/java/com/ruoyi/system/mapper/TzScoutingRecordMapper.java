package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.TzScoutingRecord;

/**
 * 巡田记录 数据层
 *
 * @author tianzhen
 */
public interface TzScoutingRecordMapper
{
    /**
     * 查询巡田记录（含地块名称/作物类型）
     *
     * @param recordId 巡田记录ID
     * @return 巡田记录
     */
    public TzScoutingRecord selectTzScoutingRecordById(@Param("recordId") Long recordId,
                                                        @Param("companyId") Long companyId);

    /**
     * 查询巡田记录列表（含地块名称/作物类型）
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
     * 删除巡田记录
     *
     * @param recordId 巡田记录ID
     * @return 结果
     */
    public int deleteTzScoutingRecordById(@Param("recordId") Long recordId,
                                          @Param("companyId") Long companyId);

    /**
     * 批量删除巡田记录
     *
     * @param recordIds 需要删除的巡田记录ID
     * @return 结果
     */
    public int deleteTzScoutingRecordByIds(@Param("recordIds") Long[] recordIds,
                                           @Param("companyId") Long companyId);

    /**
     * 首页看板：一行汇总指标（总数/本月/高风险/已诊断/平均置信度）
     *
     * @return 汇总指标
     */
    public Map<String, Object> selectDashboardSummary(@Param("companyId") Long companyId);

    /**
     * 首页看板：按诊断结果聚合的数量统计
     *
     * @return [{name, value}]
     */
    public List<Map<String, Object>> selectDiagnosisDistribution(@Param("companyId") Long companyId);

    /**
     * 首页看板：按风险等级聚合的数量统计
     *
     * @return [{name, value}]
     */
    public List<Map<String, Object>> selectRiskDistribution(@Param("companyId") Long companyId);

    /**
     * 首页看板：近 N 天的巡田量趋势
     *
     * @param days 天数
     * @return [{date, count}]
     */
    public List<Map<String, Object>> selectScoutTrend(@Param("days") int days,
                                                       @Param("companyId") Long companyId);

    /**
     * 首页看板：诊断来源占比（preset/llm/fallback/manual）
     *
     * @return [{name, value}]
     */
    public List<Map<String, Object>> selectSourceDistribution(@Param("companyId") Long companyId);
}
