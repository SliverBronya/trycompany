package com.ruoyi.system.ai;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.TzImagePreset;
import com.ruoyi.system.mapper.TzImagePresetMapper;

/**
 * 预置图片诊断解析（路线 A 保底）。
 *
 * 演示场景对稳定性的要求高于对「智能」的要求：同一张样张反复演示，
 * 结论必须每次都一样，不能因为网络抖动或额度耗尽而变。
 * 所以演示样张预先算出 MD5 与标准结论绑定，命中即返回，不经过大模型。
 *
 * 这条路径也顺便承担了「接入真实识别模型之前的产品形态」：
 * 上传的图片只要登记过，就能给出稳定、可解释、有出处的结论。
 *
 * @author tianzhen
 */
@Component
public class PresetDiagnosisResolver
{
    @Autowired
    private TzImagePresetMapper imagePresetMapper;

    /**
     * 按图片 MD5 查预置结论。
     *
     * @param imageHash 图片 MD5，为空直接视为未命中
     * @return 命中的预置映射，未命中返回 null
     */
    public TzImagePreset resolve(String imageHash)
    {
        if (StringUtils.isBlank(imageHash))
        {
            return null;
        }
        return imagePresetMapper.selectTzImagePresetByHash(imageHash.trim().toLowerCase());
    }
}
