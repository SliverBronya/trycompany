package com.ruoyi.system.ai;

import com.ruoyi.system.domain.TzKnowledgeBase;

/**
 * 知识库检索命中项：条目 + 相关度得分。
 *
 * @author tianzhen
 */
public class KnowledgeHit
{
    private final TzKnowledgeBase entry;
    private final double score;

    public KnowledgeHit(TzKnowledgeBase entry, double score)
    {
        this.entry = entry;
        this.score = score;
    }

    public TzKnowledgeBase getEntry()
    {
        return entry;
    }

    public double getScore()
    {
        return score;
    }

    @Override
    public String toString()
    {
        return "KnowledgeHit{" + (entry == null ? "null" : entry.getDiseaseName())
                + ", score=" + score + "}";
    }
}
