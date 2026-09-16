package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzMarketPrice;
import com.ruoyi.system.company.TzCompanyContext;
import com.ruoyi.system.mapper.TzMarketPriceMapper;
import com.ruoyi.system.service.ITzMarketPriceService;

/**
 * 柑橘行情价格 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzMarketPriceServiceImpl implements ITzMarketPriceService
{
    @Autowired
    private TzMarketPriceMapper tzMarketPriceMapper;

    @Autowired
    private TzCompanyContext companyContext;

    /**
     * 查询行情信息
     *
     * @param priceId 行情ID
     * @return 行情信息
     */
    @Override
    public TzMarketPrice selectTzMarketPriceById(Long priceId)
    {
        return tzMarketPriceMapper.selectTzMarketPriceById(priceId);
    }

    /**
     * 查询行情列表
     *
     * @param tzMarketPrice 行情信息
     * @return 行情集合
     */
    @Override
    public List<TzMarketPrice> selectTzMarketPriceList(TzMarketPrice tzMarketPrice)
    {
        tzMarketPrice.setCompanyId(companyContext.currentCompanyId());
        return tzMarketPriceMapper.selectTzMarketPriceList(tzMarketPrice);
    }

    /**
     * 新增行情
     *
     * @param tzMarketPrice 行情信息
     * @return 结果
     */
    @Override
    public int insertTzMarketPrice(TzMarketPrice tzMarketPrice)
    {
        tzMarketPrice.setCompanyId(companyContext.currentCompanyId());
        tzMarketPrice.setDeptId(companyContext.currentDeptId());
        return tzMarketPriceMapper.insertTzMarketPrice(tzMarketPrice);
    }

    /**
     * 修改行情
     *
     * @param tzMarketPrice 行情信息
     * @return 结果
     */
    @Override
    public int updateTzMarketPrice(TzMarketPrice tzMarketPrice)
    {
        return tzMarketPriceMapper.updateTzMarketPrice(tzMarketPrice);
    }

    /**
     * 删除行情
     *
     * @param priceId 行情ID
     * @return 结果
     */
    @Override
    public int deleteTzMarketPriceById(Long priceId)
    {
        return tzMarketPriceMapper.deleteTzMarketPriceById(priceId);
    }

    /**
     * 批量删除行情
     *
     * @param priceIds 需要删除的行情ID
     * @return 结果
     */
    @Override
    public int deleteTzMarketPriceByIds(Long[] priceIds)
    {
        return tzMarketPriceMapper.deleteTzMarketPriceByIds(priceIds);
    }
}
