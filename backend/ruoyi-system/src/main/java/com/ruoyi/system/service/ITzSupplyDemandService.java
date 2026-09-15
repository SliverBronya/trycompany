package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzSupplyDemand;

/**
 * 农产品供求信息 服务层
 *
 * @author tianzhen
 */
public interface ITzSupplyDemandService
{
    /**
     * 查询供求信息
     *
     * @param infoId 供求信息ID
     * @return 供求信息
     */
    public TzSupplyDemand selectTzSupplyDemandById(Long infoId);

    /**
     * 查询供求列表
     *
     * @param tzSupplyDemand 供求信息
     * @return 供求集合
     */
    public List<TzSupplyDemand> selectTzSupplyDemandList(TzSupplyDemand tzSupplyDemand);

    /**
     * 新增供求信息
     *
     * @param tzSupplyDemand 供求信息
     * @return 结果
     */
    public int insertTzSupplyDemand(TzSupplyDemand tzSupplyDemand);

    /**
     * 修改供求信息
     *
     * @param tzSupplyDemand 供求信息
     * @return 结果
     */
    public int updateTzSupplyDemand(TzSupplyDemand tzSupplyDemand);

    /**
     * 变更供求信息的发布状态（发布 / 下架 / 成交）
     *
     * @param tzSupplyDemand 至少含 infoId 与 status
     * @return 结果
     */
    public int changeInfoStatus(TzSupplyDemand tzSupplyDemand);

    /**
     * 删除供求信息
     *
     * @param infoId 供求信息ID
     * @return 结果
     */
    public int deleteTzSupplyDemandById(Long infoId);

    /**
     * 批量删除供求信息
     *
     * @param infoIds 需要删除的供求信息ID
     * @return 结果
     */
    public int deleteTzSupplyDemandByIds(Long[] infoIds);
}
