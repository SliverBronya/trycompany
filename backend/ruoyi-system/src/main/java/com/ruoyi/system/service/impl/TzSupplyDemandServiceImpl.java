package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzSupplyDemand;
import com.ruoyi.system.company.TzCompanyContext;
import com.ruoyi.system.mapper.TzSupplyDemandMapper;
import com.ruoyi.system.service.ITzSupplyDemandService;

/**
 * 农产品供求信息 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzSupplyDemandServiceImpl implements ITzSupplyDemandService
{
    /** 已发布 */
    private static final String STATUS_PUBLISHED = "0";

    /** 已下架 */
    private static final String STATUS_OFFLINE = "1";

    /** 已成交 */
    private static final String STATUS_DEALT = "2";

    @Autowired
    private TzSupplyDemandMapper tzSupplyDemandMapper;

    @Autowired
    private TzCompanyContext companyContext;

    /**
     * 查询供求信息
     *
     * @param infoId 供求信息ID
     * @return 供求信息
     */
    @Override
    public TzSupplyDemand selectTzSupplyDemandById(Long infoId)
    {
        return tzSupplyDemandMapper.selectTzSupplyDemandById(infoId);
    }

    /**
     * 查询供求列表
     *
     * @param tzSupplyDemand 供求信息
     * @return 供求集合
     */
    @Override
    public List<TzSupplyDemand> selectTzSupplyDemandList(TzSupplyDemand tzSupplyDemand)
    {
        tzSupplyDemand.setCompanyId(companyContext.currentCompanyId());
        return tzSupplyDemandMapper.selectTzSupplyDemandList(tzSupplyDemand);
    }

    /**
     * 新增供求信息
     *
     * @param tzSupplyDemand 供求信息
     * @return 结果
     */
    @Override
    public int insertTzSupplyDemand(TzSupplyDemand tzSupplyDemand)
    {
        tzSupplyDemand.setCompanyId(companyContext.currentCompanyId());
        tzSupplyDemand.setDeptId(companyContext.currentDeptId());
        return tzSupplyDemandMapper.insertTzSupplyDemand(tzSupplyDemand);
    }

    /**
     * 修改供求信息
     *
     * @param tzSupplyDemand 供求信息
     * @return 结果
     */
    @Override
    public int updateTzSupplyDemand(TzSupplyDemand tzSupplyDemand)
    {
        return tzSupplyDemandMapper.updateTzSupplyDemand(tzSupplyDemand);
    }

    /**
     * 变更供求信息的发布状态
     *
     * <p>单独开一个方法、而不是直接走 updateTzSupplyDemand，是因为「改状态」和「改内容」
     * 是两件校验要求完全不同的事：改内容要填全品种、联系人、电话，改状态只需要 ID 和新状态。
     * 如果合并成一条路，要么改状态时被迫先读一遍完整记录再整体写回（读改写会覆盖掉并发修改），
     * 要么就得给必填校验做分组，两条都比多这一个方法复杂。
     *
     * <p>mapper 那条 update 的 &lt;set&gt; 是动态的，这里只塞了 infoId 与 status，
     * 其余字段为 null 便不会进 SQL，因此不会把内容清空。
     */
    @Override
    public int changeInfoStatus(TzSupplyDemand tzSupplyDemand)
    {
        if (tzSupplyDemand == null || tzSupplyDemand.getInfoId() == null)
        {
            throw new ServiceException("缺少供求信息ID");
        }
        String status = tzSupplyDemand.getStatus();
        if (!STATUS_PUBLISHED.equals(status) && !STATUS_OFFLINE.equals(status) && !STATUS_DEALT.equals(status))
        {
            throw new ServiceException("状态只能是 0（已发布）、1（已下架）或 2（已成交）");
        }
        return tzSupplyDemandMapper.updateTzSupplyDemand(tzSupplyDemand);
    }

    /**
     * 删除供求信息
     *
     * @param infoId 供求信息ID
     * @return 结果
     */
    @Override
    public int deleteTzSupplyDemandById(Long infoId)
    {
        return tzSupplyDemandMapper.deleteTzSupplyDemandById(infoId);
    }

    /**
     * 批量删除供求信息
     *
     * @param infoIds 需要删除的供求信息ID
     * @return 结果
     */
    @Override
    public int deleteTzSupplyDemandByIds(Long[] infoIds)
    {
        return tzSupplyDemandMapper.deleteTzSupplyDemandByIds(infoIds);
    }
}
