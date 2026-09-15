package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.TzMarketPrice;

/**
 * 柑橘行情价格 数据层
 *
 * @author tianzhen
 */
public interface TzMarketPriceMapper
{
    /**
     * 查询行情信息
     *
     * @param priceId 行情ID
     * @return 行情信息
     */
    public TzMarketPrice selectTzMarketPriceById(Long priceId);

    /**
     * 查询行情列表
     *
     * @param tzMarketPrice 行情信息
     * @return 行情集合
     */
    public List<TzMarketPrice> selectTzMarketPriceList(TzMarketPrice tzMarketPrice);

    /**
     * 新增行情
     *
     * @param tzMarketPrice 行情信息
     * @return 结果
     */
    public int insertTzMarketPrice(TzMarketPrice tzMarketPrice);

    /**
     * 修改行情
     *
     * @param tzMarketPrice 行情信息
     * @return 结果
     */
    public int updateTzMarketPrice(TzMarketPrice tzMarketPrice);

    /**
     * 删除行情
     *
     * @param priceId 行情ID
     * @return 结果
     */
    public int deleteTzMarketPriceById(Long priceId);

    /**
     * 批量删除行情
     *
     * @param priceIds 需要删除的行情ID
     * @return 结果
     */
    public int deleteTzMarketPriceByIds(Long[] priceIds);
}
