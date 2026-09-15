package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.mapper.TzKnowledgeBaseMapper;

/**
 * 拿**真实知识库语料**验证检索阈值。
 *
 * 为什么单独有这个测试类：{@link RagServiceTest} 用的语料是 3 条手写的 mock，
 * 打分与分差阈值从来没有在真实语料上跑过。这个盲区是真实存在的 ——
 * 2026-09-13 知识库从 5 条扩到 15 条后第一次真跑，立刻发现「叶面密布灰白色失绿小点」
 * 这类简写描述会被拒答。当时的第一个反应是「扩库把分差压窄了」，但把同一批查询
 * 在 5 条与 15 条两份语料上各跑一遍后，**所有用例的采信结论完全一致**，
 * 说明这不是扩库引入的回退，而是阈值一直如此、只是从未被测过。
 * 这个类就是把那次对照固化下来，免得下次改知识库时再重新怀疑一遍。
 *
 * 夹具 kb-corpus.json 是当前种子的真实快照。**修改 sql/tz_knowledge_seed.sql
 * 后应同步刷新夹具**（否则断言测的是旧语料）：
 *     登录后 GET /tz/knowledge/list?pageSize=100，
 *     取 diseaseName / symptoms / triggerConditions / prevention / medicineNote / safetyNote
 *     写成 src/test/resources/kb-corpus.json。
 *
 * ⚠️ 这里断言的是**采信结论**，不是具体得分。得分数值会随分词与权重的任何微调而变，
 * 断言死数字只会让测试变得又脆又难维护；而「哪类描述该采信、哪类该拒答」是产品契约。
 *
 * @author tianzhen
 */
@ExtendWith(MockitoExtension.class)
class RagServiceRealCorpusTest
{
    @Mock
    private TzKnowledgeBaseMapper knowledgeBaseMapper;

    private RagService ragService;

    @BeforeEach
    void setUp() throws Exception
    {
        ragService = new RagService();
        ReflectionTestUtils.setField(ragService, "knowledgeBaseMapper", knowledgeBaseMapper);

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("kb-corpus.json"))
        {
            List<TzKnowledgeBase> corpus = new ObjectMapper().readValue(in,
                    new TypeReference<List<TzKnowledgeBase>>() {});
            assertTrue(corpus.size() >= 15, "夹具疑似损坏，条数=" + corpus.size());
            when(knowledgeBaseMapper.selectTzKnowledgeBaseList(any())).thenReturn(corpus);
        }
    }

    private boolean confidentOn(String query)
    {
        return ragService.isConfident(ragService.retrieve(query, 5));
    }

    @Test
    @DisplayName("知识库原文级的症状描述必须采信且命中正确条目")
    void acceptsFaithfulDescriptions()
    {
        String[] queries = {
            "叶片上出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈",
            "叶片出现许多灰白色失绿小点，随后叶片变为灰白色至暗黄色，翻看叶背可见细小的红色螨体及蛛丝状物",
            "中下部老叶的叶脉间发黄，呈倒V形黄化，叶脉及附近组织仍为绿色",
        };
        for (String q : queries)
        {
            List<KnowledgeHit> hits = ragService.retrieve(q, 5);
            assertTrue(ragService.isConfident(hits), "真实描述应采信：" + q);
            assertTrue(hits.get(0).getScore() > hits.get(1).getScore(), "应正确排序：" + q);
        }
    }

    @Test
    @DisplayName("泛词与无关问题一律拒答 —— 这是阈值防的主要风险")
    void rejectsGenericAndIrrelevant()
    {
        assertFalse(confidentOn("柑橘"), "光一个作物名不该给结论");
        assertFalse(confidentOn("柑橘病虫害怎么防治"), "泛问法不该给结论");
        assertFalse(confidentOn("今天天气不错，我想问问怎么给果树浇水施肥比较好呢"), "无关问题不该给结论");
        assertFalse(confidentOn("我家的树最近看起来好像有点不太对劲，具体哪里不对我也说不上来"),
                "超长废话不该给结论");
    }

    @Test
    @DisplayName("简写但准确的描述应当采信 —— 这条代价在引入 IDF 后已经消除")
    void acceptsAbbreviatedDescriptions()
    {
        // 改造前这三条都会被拒：绝对分或分差不达标，而卡住它们的其实是通用词虚增的噪声。
        // 引入 IDF 后通用词被压权，稀有词（火山口、倒V形、圆锥形）才抬得动分数。
        assertTrue(confidentOn("叶面密布灰白色失绿小点，叶背有细小红色虫体"),
                "简写描述应采信（实测绝对分约 31.6、分差约 20.9）");
        assertTrue(confidentOn("老叶叶脉间发黄，呈倒V形，叶脉仍绿"),
                "简写描述应采信（实测绝对分约 20.9、分差约 14.8）");
        assertTrue(confidentOn("叶片有近圆形病斑，周围有黄色晕圈"),
                "简写描述应采信（实测绝对分约 22.6）");
    }

    @Test
    @DisplayName("回归：多种病害的标准描述不得都被判成柑橘溃疡病")
    void doesNotCollapseToCanker()
    {
        // 这是用户报上来的原始问题：无论描述什么病，结论都往溃疡病上靠。
        // 成因是溃疡病的症状文本最长、通用词最多，旧打分下谁都要先撞上它。
        // 这里逐条钉住 —— 认错可以（有几条本来就高度相似），但不许全部塌到一个病上。
        String[][] cases = {
            {"柑橘红蜘蛛", "叶面密布针尖大小的灰白色失绿小点，远看整叶发灰发黄，叶背有细小红色虫体和少量蛛丝"},
            {"柑橘潜叶蛾", "新叶上有弯弯曲曲的白色虫道，弯来弯去像画的一样，叶片边缘卷曲"},
            {"柑橘介壳虫", "枝干和叶片上附着一个个褐色小硬壳，密密麻麻固定不动，叶子发黄长势弱"},
            {"柑橘蚜虫", "嫩梢和嫩叶上爬满绿色小虫，新叶皱缩卷曲，叶面有蜜露发亮还招蚂蚁"},
            {"柑橘缺镁", "中下部老叶叶脉间发黄呈倒V形，叶脉及附近仍为绿色，新叶基本正常"},
            {"柑橘炭疽病", "叶片和果实上有圆形或不规则褐色病斑，上面有小黑点排成轮纹，后期腐烂落果"},
            {"柑橘树脂病", "树干和枝条上流出褐色胶状物，皮层腐烂发软有酒糟味，叶片黄化"},
            {"柑橘实蝇", "果实表面有针尖大的产卵孔，周围发黄发软，果肉腐烂发臭，里面有白色小蛆"},
            {"柑橘锈壁虱", "果实和叶片表面变成黑褐色或锈褐色，像被开水烫过，果皮粗糙失去光泽"},
            {"柑橘木虱", "嫩梢上有很小的绿色虫子会跳，新叶扭曲变形，嫩芽上有白色蜡丝"},
            {"柑橘溃疡病", "叶片两面有近圆形病斑，中央木栓化隆起开裂，周围有明显黄色晕圈，叶背隆起更明显"},
            {"柑橘疮痂病", "新叶和幼果上有小的圆锥形突起，表面粗糙木栓化，叶片扭曲变形"},
        };

        int correct = 0;
        int canker = 0;
        for (String[] c : cases)
        {
            List<KnowledgeHit> hits = ragService.retrieve("柑橘 " + c[1], 5);
            if (hits.isEmpty())
            {
                continue;
            }
            String got = hits.get(0).getEntry().getDiseaseName();
            if (c[0].equals(got))
            {
                correct++;
            }
            if ("柑橘溃疡病".equals(got) && !"柑橘溃疡病".equals(c[0]))
            {
                canker++;
            }
        }

        // 实测 12 条里命中 11 条；留一格余量，避免下次微调权重时因一条边界用例就红。
        assertTrue(correct >= 10, "标准描述应绝大多数命中，实际 " + correct + "/" + cases.length);
        // 关键断言：溃疡病不能再当「默认答案」。改造前这一项是 3 条。
        assertTrue(canker <= 1, "不得把别的病都判成溃疡病，实际误判 " + canker + " 条");
    }

    @Test
    @DisplayName("泛问法即使能蹭到分数也要被挡在采信线外")
    void rejectsVagueEvenWhenItScores()
    {
        // 「叶片发黄」会靠「片发」这个跨词边界的生僻二元组蹭到约 9.6 分，
        // 是实测里唯一一条分数接近采信线的泛问法。MIN_SCORE=11 就是为了分开它
        // 和最低的一条真描述（介壳虫的教科书式写法 13.9）。
        assertFalse(confidentOn("叶片发黄"), "过于笼统的描述不该给结论");
        assertFalse(confidentOn("叶子上有虫子"), "过于笼统的描述不该给结论");
    }
}
