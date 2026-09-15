package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzImagePreset;

/**
 * 预置图片诊断映射 服务层
 *
 * @author tianzhen
 */
public interface ITzImagePresetService
{
    /**
     * 查询预置映射
     *
     * @param presetId 预置映射ID
     * @return 预置映射
     */
    public TzImagePreset selectTzImagePresetById(Long presetId);

    /**
     * 查询预置映射列表
     *
     * @param tzImagePreset 预置映射
     * @return 预置映射集合
     */
    public List<TzImagePreset> selectTzImagePresetList(TzImagePreset tzImagePreset);

    /**
     * 新增预置映射
     *
     * @param tzImagePreset 预置映射
     * @return 结果
     */
    public int insertTzImagePreset(TzImagePreset tzImagePreset);

    /**
     * 修改预置映射
     *
     * @param tzImagePreset 预置映射
     * @return 结果
     */
    public int updateTzImagePreset(TzImagePreset tzImagePreset);

    /**
     * 删除预置映射
     *
     * @param presetId 预置映射ID
     * @return 结果
     */
    public int deleteTzImagePresetById(Long presetId);

    /**
     * 批量删除预置映射
     *
     * @param presetIds 需要删除的预置映射ID
     * @return 结果
     */
    public int deleteTzImagePresetByIds(Long[] presetIds);
}
