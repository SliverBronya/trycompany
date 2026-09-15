package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.mapper.TzKnowledgeBaseMapper;

/**
 * 知识库检索（RAG 的「R」）。
 *
 * <p>为什么不上向量库：首版知识库只有十几条，引入 embedding 就要多一个外部依赖、
 * 多一份调用成本，且离线不可用、结果不可解释。用字面特征打分在这个量级上够用，
 * 而且每一次命中都能说清「为什么是它」，答辩时经得起追问。
 * 知识库规模上去之后，替换本类实现即可，调用方无感。
 *
 * <h3>为什么给通用词降权（IDF）</h3>
 *
 * <p>第一版按「字面共现个数」打分，结果系统性偏向柑橘溃疡病：它的症状文本里
 * 「病斑 / 叶片 / 隆起 / 褐色 / 晕圈」这类词最多，于是任何叶斑类描述都会先命中它，
 * 而疮痂病、炭疽病这些同样表现为叶斑的条目永远排在后面。实测 15 种病害的
 * 标准描述，只有 6 条能查到正确的病（另 9 条直接拒答），而带图时又总是落到溃疡病。
 *
 * <p>根子在于「柑橘」「叶片」「病斑」这些词**对判断没有区分度** —— 它们出现在
 * 每个条目里，谁的字多谁占便宜。所以引入 IDF：一个词在越多条目里出现，
 * 它对本次判断的贡献越小。这样「火山口状开裂」「圆锥形瘤状突起」「白色蜡丝」
 * 这类只在个别条目里出现的词才会真正起作用，也正是这些词在区分病害。
 *
 * <h3>字段权重与两个新字段的分工</h3>
 *
 * <p>{@code key_features}（特征性表现）权重最高，仅次于病名 —— 这个字段是为检索
 * 专门写的，只放「本病看得见、别的病少见」的阳性描述。
 * {@code differential}（鉴别要点）**刻意不参与打分**：它里面必然写着别的病名
 * （「与疮痂病的区别：疮痂病呈圆锥形突起」），一旦参与打分，查「圆锥形突起」
 * 就会命中溃疡病条目，等于把偏向换个方向又演一遍。它只作为上下文交给模型做比对。
 *
 * @author tianzhen
 */
@Service
public class RagService
{
    /** 各字段的权重：名称命中远比正文命中更有说服力 */
    private static final double W_NAME = 10D;
    /** 特征性表现：为检索而写，仅次于病名 */
    private static final double W_FEATURES = 8D;
    private static final double W_SYMPTOMS = 4D;
    private static final double W_TRIGGER = 2D;
    private static final double W_PREVENTION = 1D;
    private static final double W_MEDICINE = 1D;
    private static final double W_SAFETY = 1D;

    /** 查询串里出现完整病名时的额外加分 */
    private static final double NAME_CONTAIN_BONUS = 50D;

    /**
     * 采信一条结论所需的最低相关度。
     *
     * <p>引入 IDF 后分数的量纲变了（不再因为通用词多而虚高），这个值与
     * {@link #MIN_GAP} 都是在**真实 15 条语料上重新实测**校准的，见 {@link #MIN_GAP}。
     *
     * <p>11 卡在实测暴露的空档里：泛问法「叶片发黄」能蹭到 9.57
     * （它靠「片发」这个跨词边界的生僻二元组 —— 该串只出现在介壳虫条目的
     * 「叶片发黄」里，于是被 IDF 当成了稀有特征），而最弱的一条正当描述
     * （介壳虫的教科书式写法）是 13.94。9.57 与 13.94 之间没有样本。
     *
     * <p>这里宁可定得偏高：把一条真描述挡回去，用户补两句话就好；
     * 而放一条泛问进来，系统会给出一个像模像样的错结论。
     */
    private static final double MIN_SCORE = 11D;

    /**
     * 采信一条结论所需的「领先幅度」：第一名得分要比第二名高出多少分。
     *
     * <p>为什么不能只看绝对分：得分随描述长度线性增长，一条又短又准的描述
     * 会吃亏，而一条又长又含糊的描述能靠蹭分轻松上去。反过来，绝对分也挡不住泛词。
     *
     * <p>为什么用**分差**而不是**倍数**：检索串里会带上作物名（「柑橘」），
     * 而所有病名都以它开头，于是每个条目都被加上一个近乎相同的常数分。
     * 常数会把倍数压扁，分差对常数偏移免疫。
     *
     * <p>⚠️ 下面这组数字是在「IDF + 特征性表现字段」这套新打分下**实测**出来的
     * （15 条知识库、检索串按生产方式前置「柑橘」，`ThresholdCalibrationTest` 的探针输出）：
     * <pre>
     *                                 绝对分        分差       结果
     *   知识库原文级描述             26.4~73.4   15.1~60.0   3/3  命中
     *   教科书式简短描述（15 种病）  13.9~59.0    1.3~45.6   13/15 命中
     *   简写但准确（4 条）           20.6~31.6   13.1~20.9   4/4  命中
     *   泛词 / 无关 / 废话            4.2~9.6     0.3~4.6    0/6  全部拒答
     * </pre>
     * 对比改造前：教科书式描述只命中 6/15，另 9 条全部拒答；简写描述 0/4 全被拒。
     * 也就是说这一版**同时做到了「少拒答」和「少误判」**，而不是拿一个换另一个。
     *
     * <p>2.0 这条线不负责拦住所有错判 —— 实测里「黄龙病被认成缺锌」那一条分差 3.40，
     * 明显过了线。那种错**不该靠阈值解决**：继续往上抬阈值只会把真描述一起挡掉。
     * 它的正解是让大模型拿着两条的 differential 去比对（这两者症状高度相似，
     * 正是知识库里写明了要鉴别的一对），所以 {@link #isConfident} 只负责
     * 「有没有候选值得看」，「候选里选谁」交给下游。
     */
    private static final double MIN_GAP = 2D;

    @Autowired
    private TzKnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 按查询串检索知识库。
     *
     * @param query 查询串（通常是症状描述 + 部位 + 作物）
     * @param topN  最多返回条数
     * @return 按相关度降序的命中项，只含得分大于 0 的
     */
    public List<KnowledgeHit> retrieve(String query, int topN)
    {
        List<KnowledgeHit> hits = new ArrayList<>();
        if (StringUtils.isBlank(query))
        {
            return hits;
        }

        Set<String> grams = bigrams(query);
        if (grams.isEmpty())
        {
            return hits;
        }

        TzKnowledgeBase condition = new TzKnowledgeBase();
        condition.setStatus("0");
        List<TzKnowledgeBase> entries = knowledgeBaseMapper.selectTzKnowledgeBaseList(condition);
        if (entries.isEmpty())
        {
            return hits;
        }

        // 先算每个 gram 的「稀有度」，再打分。两步走是因为 IDF 要用到全体条目：
        // 「柑橘」出现 15 次（无区分度），「火山口」只出现 1 次（很关键）。
        Map<String, Double> idf = idf(grams, entries);
        String normalizedQuery = normalize(query);

        for (TzKnowledgeBase entry : entries)
        {
            double score = score(entry, grams, idf, normalizedQuery);
            if (score > 0)
            {
                hits.add(new KnowledgeHit(entry, score));
            }
        }

        hits.sort(Comparator.comparingDouble(KnowledgeHit::getScore).reversed());
        return hits.size() > topN ? new ArrayList<>(hits.subList(0, topN)) : hits;
    }

    /**
     * 取相关度最高的一条，证据不足则返回 null（宁可说「没把握」也不硬给结论）。
     *
     * @param query 查询串
     * @return 命中项或 null
     */
    public KnowledgeHit best(String query)
    {
        // 取 2 条而不是 1 条：领先幅度要看得到第二名才判断得了
        List<KnowledgeHit> hits = retrieve(query, 2);
        return isConfident(hits) ? hits.get(0) : null;
    }

    /**
     * 判断这组检索结果是否足以支撑一个结论。
     *
     * <p>这是「不硬给结论」这条产品原则的落点：既要第一名本身有足够证据（绝对分），
     * 也要它明显甩开第二名（领先幅度）。只看前者会把泛词放进来，
     * 只看后者会让两个都很差的条目互相比烂。
     *
     * <p>有了 IDF 之后，弱查询（「柑橘」「叶片发黄」这类）的绝对分会自然落到
     * {@link #MIN_SCORE} 之下 —— 不再需要单独写一套「查询太弱就返回空」的规则，
     * 强度判断是从打分里长出来的，而不是另加的一个阈值。
     *
     * @param hits 按相关度降序的命中项
     * @return 是否可采信
     */
    public boolean isConfident(List<KnowledgeHit> hits)
    {
        if (hits == null || hits.isEmpty())
        {
            return false;
        }
        double top = hits.get(0).getScore();
        if (top < MIN_SCORE)
        {
            return false;
        }
        // 只有一条命中时没有可比对象，此时唯一的证据就是它的绝对分
        if (hits.size() == 1)
        {
            return true;
        }
        return top - hits.get(1).getScore() >= MIN_GAP;
    }

    // ------------------------------------------------------------------ 打分

    /**
     * 计算每个 gram 的逆文档频率。
     *
     * <p>{@code idf = log(1 + N / (1 + df))}，df 是含有该词的条目数。
     * 15 条语料下：出现在全部 15 条里的词约 0.66，只出现在 1 条里的词约 2.14，
     * 差三倍左右 —— 足够让「火山口」压过「病斑」，又不会让某个冷僻词一出现就把分数
     * 抬到失真（这是没敢用更陡的公式的原因：几条知识库里的表述并不严格统一，
     * 一个偶然的错别字不该主导结论）。
     */
    private Map<String, Double> idf(Set<String> grams, List<TzKnowledgeBase> entries)
    {
        int total = entries.size();
        Map<String, Double> result = new HashMap<>(grams.size() * 2);
        for (String gram : grams)
        {
            int df = 0;
            for (TzKnowledgeBase entry : entries)
            {
                if (searchText(entry).contains(gram))
                {
                    df++;
                }
            }
            result.put(gram, Math.log(1D + (double) total / (1D + df)));
        }
        return result;
    }

    /**
     * 参与「这个词有多少条提到」统计的正文。
     *
     * <p>刻意**不含 differential**：那个字段里写着别的病名与别的病的症状描述，
     * 算进来会把「圆锥形突起」也算成溃疡病条目提到过的词。
     */
    private String searchText(TzKnowledgeBase entry)
    {
        return normalize(StringUtils.defaultString(entry.getDiseaseName())
                + StringUtils.defaultString(entry.getKeyFeatures())
                + StringUtils.defaultString(entry.getSymptoms())
                + StringUtils.defaultString(entry.getTriggerConditions())
                + StringUtils.defaultString(entry.getPrevention())
                + StringUtils.defaultString(entry.getMedicineNote())
                + StringUtils.defaultString(entry.getSafetyNote()));
    }

    private double score(TzKnowledgeBase entry, Set<String> grams, Map<String, Double> idf, String normalizedQuery)
    {
        double score = 0D;
        score += W_NAME * density(grams, idf, entry.getDiseaseName());
        // 特征性表现是专门为检索写的字段：一个词出现在这里，说明它对判这个病有区分度
        score += W_FEATURES * density(grams, idf, entry.getKeyFeatures());
        score += W_SYMPTOMS * density(grams, idf, entry.getSymptoms());
        score += W_TRIGGER * density(grams, idf, entry.getTriggerConditions());
        score += W_PREVENTION * density(grams, idf, entry.getPrevention());
        score += W_MEDICINE * density(grams, idf, entry.getMedicineNote());
        score += W_SAFETY * density(grams, idf, entry.getSafetyNote());

        String name = normalize(entry.getDiseaseName());
        if (StringUtils.isNotBlank(name) && normalizedQuery.contains(name))
        {
            score += NAME_CONTAIN_BONUS;
        }
        return score;
    }

    /**
     * 二元组在字段正文中的命中**加权**密度：命中的词越关键分越高，
     * 用 sqrt 压一下长度，避免长文本仅因为字多就赢过短文本。
     *
     * <p>与第一版的区别只有一处 —— 原来每个命中都记 1 分，现在记它的 IDF。
     * 这一处改动就解决了「溃疡病症状写得最长所以什么都像它」。
     */
    private double density(Set<String> grams, Map<String, Double> idf, String field)
    {
        if (StringUtils.isBlank(field))
        {
            return 0D;
        }
        String normalizedField = normalize(field);
        if (normalizedField.isEmpty())
        {
            return 0D;
        }

        double weighted = 0D;
        for (String gram : grams)
        {
            if (normalizedField.contains(gram))
            {
                weighted += idf.getOrDefault(gram, 1D);
            }
        }
        if (weighted <= 0D)
        {
            return 0D;
        }
        return weighted / Math.sqrt(normalizedField.length());
    }

    // ------------------------------------------------------------------ 文本处理

    /** 中文分词器不可用时，用滑窗二元组当作特征，足够表达「溃疡」「病斑」这类词感 */
    private Set<String> bigrams(String text)
    {
        String normalized = normalize(text);
        Set<String> grams = new LinkedHashSet<>();
        if (normalized.length() == 1)
        {
            grams.add(normalized);
            return grams;
        }
        for (int i = 0; i + 2 <= normalized.length(); i++)
        {
            grams.add(normalized.substring(i, i + 2));
        }
        return grams;
    }

    /** 只保留中日韩汉字、字母、数字，抹平标点与空白带来的噪声 */
    private String normalize(String text)
    {
        if (text == null)
        {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toLowerCase().toCharArray())
        {
            if (Character.isLetterOrDigit(c))
            {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
