package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.company.TzCompanyContext;
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

    @Autowired
    private TzCompanyContext companyContext;

    /**
     * 查询地块信息
     *
     * 只按主键查是不够的：拿到别的公司的地块ID 一样能打开。所以查出来之后
     * 还要核对归属 —— 单个对象的越权，列表过滤是拦不住的。
     *
     * @param plotId 地块ID
     * @return 地块信息（跨公司访问返回 null）
     */
    @Override
    public TzPlot selectTzPlotById(Long plotId)
    {
        TzPlot plot = tzPlotMapper.selectTzPlotById(plotId);
        if (plot == null)
        {
            return null;
        }
        Long companyId = companyContext.currentCompanyId();
        if (companyId != null && !companyId.equals(plot.getCompanyId()))
        {
            return null;
        }
        return plot;
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
        tzPlot.setCompanyId(companyContext.currentCompanyId());
        return tzPlotMapper.selectTzPlotList(tzPlot);
    }

    /**
     * 新增地块
     *
     * 归属在这里打标，不指望前端传 —— 前端传的公司ID 是不可信的。
     *
     * @param tzPlot 地块信息
     * @return 结果
     */
    @Override
    public int insertTzPlot(TzPlot tzPlot)
    {
        tzPlot.setCompanyId(companyContext.currentCompanyId());
        tzPlot.setDeptId(companyContext.currentDeptId());
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
        // 带上作用域：公司不匹配时 SQL 影响 0 行，避免改到别的公司的数据
        tzPlot.setCompanyId(companyContext.currentCompanyId());
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
        return tzPlotMapper.deleteTzPlotById(plotId, companyContext.currentCompanyId());
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
        return tzPlotMapper.deleteTzPlotByIds(plotIds, companyContext.currentCompanyId());
    }

    /**
     * 校验地块名称是否唯一
     *
     * 按公司判重：不同公司可以有同名地块（"1号地"这种名字太常见了），
     * 跨公司判重会让别人建不了自己本来就有的名字。
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
        query.setCompanyId(companyContext.currentCompanyId());
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
