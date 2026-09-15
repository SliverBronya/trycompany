package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzPlot;
import com.ruoyi.system.mapper.TzPlotMapper;
import com.ruoyi.system.service.ITzPlotService;

/**
 * 巡田地块 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzPlotServiceImpl implements ITzPlotService
{
    @Autowired
    private TzPlotMapper tzPlotMapper;

    /**
     * 查询地块信息
     *
     * @param plotId 地块ID
     * @return 地块信息
     */
    @Override
    public TzPlot selectTzPlotById(Long plotId)
    {
        return tzPlotMapper.selectTzPlotById(plotId);
    }

    /**
     * 查询地块列表
     *
     * @param tzPlot 地块信息
     * @return 地块集合
     */
    @Override
    public List<TzPlot> selectTzPlotList(TzPlot tzPlot)
    {
        return tzPlotMapper.selectTzPlotList(tzPlot);
    }

    /**
     * 新增地块
     *
     * @param tzPlot 地块信息
     * @return 结果
     */
    @Override
    public int insertTzPlot(TzPlot tzPlot)
    {
        return tzPlotMapper.insertTzPlot(tzPlot);
    }

    /**
     * 修改地块
     *
     * @param tzPlot 地块信息
     * @return 结果
     */
    @Override
    public int updateTzPlot(TzPlot tzPlot)
    {
        return tzPlotMapper.updateTzPlot(tzPlot);
    }

    /**
     * 删除地块（存在巡田记录时拒绝删除）
     *
     * @param plotId 地块ID
     * @return 结果
     */
    @Override
    public int deleteTzPlotById(Long plotId)
    {
        int recordCount = tzPlotMapper.countRecordByPlotId(plotId);
        if (recordCount > 0)
        {
            throw new ServiceException("该地块下已存在 " + recordCount + " 条巡田记录，请先删除记录后再删除地块");
        }
        return tzPlotMapper.deleteTzPlotById(plotId);
    }

    /**
     * 批量删除地块
     *
     * @param plotIds 需要删除的地块ID
     * @return 结果
     */
    @Override
    public int deleteTzPlotByIds(Long[] plotIds)
    {
        for (Long plotId : plotIds)
        {
            TzPlot plot = tzPlotMapper.selectTzPlotById(plotId);
            int recordCount = tzPlotMapper.countRecordByPlotId(plotId);
            if (recordCount > 0)
            {
                String name = plot != null ? plot.getPlotName() : String.valueOf(plotId);
                throw new ServiceException("地块「" + name + "」下已存在 " + recordCount + " 条巡田记录，不允许删除");
            }
        }
        return tzPlotMapper.deleteTzPlotByIds(plotIds);
    }

    /**
     * 校验地块名称是否唯一
     *
     * @param tzPlot 地块信息
     * @return true 唯一 / false 重复
     */
    @Override
    public boolean checkPlotNameUnique(TzPlot tzPlot)
    {
        Long plotId = tzPlot.getPlotId() == null ? -1L : tzPlot.getPlotId();
        TzPlot query = new TzPlot();
        query.setPlotName(tzPlot.getPlotName());
        List<TzPlot> list = tzPlotMapper.selectTzPlotList(query);
        for (TzPlot existing : list)
        {
            if (tzPlot.getPlotName().equals(existing.getPlotName()) && !existing.getPlotId().equals(plotId))
            {
                return false;
            }
        }
        return true;
    }
}
