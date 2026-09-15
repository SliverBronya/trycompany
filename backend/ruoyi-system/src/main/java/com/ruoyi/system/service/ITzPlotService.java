package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzPlot;

/**
 * 巡田地块 服务层
 *
 * @author tianzhen
 */
public interface ITzPlotService
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
     * 删除地块（存在巡田记录时拒绝删除，避免产生孤儿记录）
     *
     * @param plotId 地块ID
     * @return 结果
     */
    public int deleteTzPlotById(Long plotId);

    /**
     * 批量删除地块
     *
     * @param plotIds 需要删除的地块ID
     * @return 结果
     */
    public int deleteTzPlotByIds(Long[] plotIds);

    /**
     * 校验地块名称是否唯一
     *
     * @param tzPlot 地块信息
     * @return true 唯一 / false 重复
     */
    public boolean checkPlotNameUnique(TzPlot tzPlot);
}
