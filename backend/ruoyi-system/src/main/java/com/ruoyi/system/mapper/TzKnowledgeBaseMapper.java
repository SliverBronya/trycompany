package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.TzKnowledgeBase;

/**
 * 柑橘植保知识库 数据层
 *
 * @author tianzhen
 */
public interface TzKnowledgeBaseMapper
{
    /**
     * 查询知识库条目
     *
     * @param knowledgeId 知识库ID
     * @return 知识库条目
     */
    public TzKnowledgeBase selectTzKnowledgeBaseById(Long knowledgeId);

    /**
     * 查询知识库列表
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 知识库集合
     */
    public List<TzKnowledgeBase> selectTzKnowledgeBaseList(TzKnowledgeBase tzKnowledgeBase);

    /**
     * 按名称精确匹配（诊断结果 → 知识库条目）
     *
     * @param diseaseName 病虫害/缺素名称
     * @return 知识库条目
     */
    public TzKnowledgeBase selectTzKnowledgeBaseByName(String diseaseName);

    /**
     * 新增知识库条目
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 结果
     */
    public int insertTzKnowledgeBase(TzKnowledgeBase tzKnowledgeBase);

    /**
     * 修改知识库条目
     *
     * @param tzKnowledgeBase 知识库条目
     * @return 结果
     */
    public int updateTzKnowledgeBase(TzKnowledgeBase tzKnowledgeBase);

    /**
     * 删除知识库条目
     *
     * @param knowledgeId 知识库ID
     * @return 结果
     */
    public int deleteTzKnowledgeBaseById(Long knowledgeId);

    /**
     * 批量删除知识库条目
     *
     * @param knowledgeIds 需要删除的知识库ID
     * @return 结果
     */
    public int deleteTzKnowledgeBaseByIds(Long[] knowledgeIds);
}
