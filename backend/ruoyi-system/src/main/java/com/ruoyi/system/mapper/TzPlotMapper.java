package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.TzPlot;

/**
 * 巡田地块 数据层
 *
 * @author tianzhen
 */
public interface TzPlotMapper
{
    /**
     * 查询地块信息
     *
     * @param plotId 地块ID
     * @return 地块信息
     */
    public TzPlot selectTzPlotById(Long plotId);

    /**
     * 查询地块列表
     *
     * @param tzPlot 地块信息
     * @return 地块集合
     */
    public List<TzPlot> selectTzPlotList(TzPlot tzPlot);

    /**
     * 新增地块
     *
     * @param tzPlot 地块信息
     * @return 结果
     */
    public int insertTzPlot(TzPlot tzPlot);

    /**
     * 修改地块
     *
     * @param tzPlot 地块信息
     * @return 结果
     */
    public int updateTzPlot(TzPlot tzPlot);

    /**
     * 删除地块
     *
     * @param plotId 地块ID
     * @param companyId 公司作用域（超管传 null 表示不过滤；用于拦住跨公司删除）
     * @return 结果
     */
    public int deleteTzPlotById(@Param("plotId") Long plotId, @Param("companyId") Long companyId);

    /**
     * 批量删除地块
     *
     * @param plotIds 需要删除的地块ID
     * @param companyId 公司作用域（超管传 null 表示不过滤）
     * @return 结果
     */
    public int deleteTzPlotByIds(@Param("plotIds") Long[] plotIds, @Param("companyId") Long companyId);

    /**
     * 统计某地块下的巡田记录数（删除前校验，防止留下孤儿记录）
     *
     * @param plotId 地块ID
     * @return 记录数
     */
    public int countRecordByPlotId(Long plotId);

    /**
     * 首页看板：地块数量统计
     *
     * @param tzPlot 过滤条件（可为 null 表示全部）
     * @return 数量
     */
    public int countTzPlot(TzPlot tzPlot);
}
