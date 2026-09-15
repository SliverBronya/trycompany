package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzImagePreset;
import com.ruoyi.system.mapper.TzImagePresetMapper;
import com.ruoyi.system.service.ITzImagePresetService;

/**
 * 预置图片诊断映射 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzImagePresetServiceImpl implements ITzImagePresetService
{
    @Autowired
    private TzImagePresetMapper tzImagePresetMapper;

    /**
     * 查询预置映射
     *
     * @param presetId 预置映射ID
     * @return 预置映射
     */
    @Override
    public TzImagePreset selectTzImagePresetById(Long presetId)
    {
        return tzImagePresetMapper.selectTzImagePresetById(presetId);
    }

    /**
     * 查询预置映射列表
     *
     * @param tzImagePreset 预置映射
     * @return 预置映射集合
     */
    @Override
    public List<TzImagePreset> selectTzImagePresetList(TzImagePreset tzImagePreset)
    {
        return tzImagePresetMapper.selectTzImagePresetList(tzImagePreset);
    }

    /**
     * 新增预置映射
     *
     * @param tzImagePreset 预置映射
     * @return 结果
     */
    @Override
    public int insertTzImagePreset(TzImagePreset tzImagePreset)
    {
        return tzImagePresetMapper.insertTzImagePreset(tzImagePreset);
    }

    /**
     * 修改预置映射
     *
     * @param tzImagePreset 预置映射
     * @return 结果
     */
    @Override
    public int updateTzImagePreset(TzImagePreset tzImagePreset)
    {
        return tzImagePresetMapper.updateTzImagePreset(tzImagePreset);
    }

    /**
     * 删除预置映射
     *
     * @param presetId 预置映射ID
     * @return 结果
     */
    @Override
    public int deleteTzImagePresetById(Long presetId)
    {
        return tzImagePresetMapper.deleteTzImagePresetById(presetId);
    }

    /**
     * 批量删除预置映射
     *
     * @param presetIds 需要删除的预置映射ID
     * @return 结果
     */
    @Override
    public int deleteTzImagePresetByIds(Long[] presetIds)
    {
        return tzImagePresetMapper.deleteTzImagePresetByIds(presetIds);
    }
}
