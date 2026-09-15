package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.mapper.TzKnowledgeBaseMapper;
import com.ruoyi.system.service.ITzKnowledgeBaseService;

/**
 * 柑橘植保知识库 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzKnowledgeBaseServiceImpl implements ITzKnowledgeBaseService
{
    @Autowired
    private TzKnowledgeBaseMapper tzKnowledgeBaseMapper;

    /**
     * 查询知识库条目
     *
     * @param knowledgeId 知识库ID
     * @return 知识库条目
     */
    @Override
    public TzKnowledgeBase selectTzKnowledgeBaseById(Long knowledgeId)
    {
        return tzKnowledgeBaseMapper.selectTzKnowledgeBaseById(knowledgeId);
    }

    /**
     * 查询知识库列表
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 知识库集合
     */
    @Override
    public List<TzKnowledgeBase> selectTzKnowledgeBaseList(TzKnowledgeBase tzKnowledgeBase)
    {
        return tzKnowledgeBaseMapper.selectTzKnowledgeBaseList(tzKnowledgeBase);
    }

    /**
     * 按名称精确匹配（诊断结果 → 知识库条目）
     *
     * @param diseaseName 病虫害/缺素名称
     * @return 知识库条目
     */
    @Override
    public TzKnowledgeBase selectTzKnowledgeBaseByName(String diseaseName)
    {
        return tzKnowledgeBaseMapper.selectTzKnowledgeBaseByName(diseaseName);
    }

    /**
     * 新增知识库条目
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 结果
     */
    @Override
    public int insertTzKnowledgeBase(TzKnowledgeBase tzKnowledgeBase)
    {
        return tzKnowledgeBaseMapper.insertTzKnowledgeBase(tzKnowledgeBase);
    }

    /**
     * 修改知识库条目
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 结果
     */
    @Override
    public int updateTzKnowledgeBase(TzKnowledgeBase tzKnowledgeBase)
    {
        return tzKnowledgeBaseMapper.updateTzKnowledgeBase(tzKnowledgeBase);
    }

    /**
     * 删除知识库条目
     *
     * @param knowledgeId 知识库ID
     * @return 结果
     */
    @Override
    public int deleteTzKnowledgeBaseById(Long knowledgeId)
    {
        return tzKnowledgeBaseMapper.deleteTzKnowledgeBaseById(knowledgeId);
    }

    /**
     * 批量删除知识库条目
     *
     * @param knowledgeIds 需要删除的知识库ID
     * @return 结果
     */
    @Override
    public int deleteTzKnowledgeBaseByIds(Long[] knowledgeIds)
    {
        return tzKnowledgeBaseMapper.deleteTzKnowledgeBaseByIds(knowledgeIds);
    }
}
