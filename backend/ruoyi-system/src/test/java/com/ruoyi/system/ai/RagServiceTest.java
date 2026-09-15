package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.mapper.TzKnowledgeBaseMapper;

/**
 * 知识库关键词检索单测。
 *
 * 这套打分是「降级路径」唯一的判断依据，也是「建议可溯」的前提：
 * 检错了条目，后面给出的防治建议就是张冠李戴，而且看起来还挺像回事。
 *
 * <p><b>职责边界</b>：本类用的是 3 条手写 mock 语料，只验证**排序与分支**
 * （该命中谁、不该命中谁）。**绝对分数与采信阈值不放这里** —— 3 条语料算出来的
 * IDF 与真实 15 条完全不是一回事，把阈值断言写在这是自欺欺人。
 * 「哪类描述该采信、哪类该拒答」是产品契约，归
 * {@link RagServiceRealCorpusTest}（真实语料 + 快照夹具）管。
 *
 * @author tianzhen
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest
{
    @Mock
    private TzKnowledgeBaseMapper knowledgeBaseMapper;

    private RagService ragService;
    private List<TzKnowledgeBase> corpus;

    @BeforeEach
    void setUp()
    {
        ragService = new RagService();
        ReflectionTestUtils.setField(ragService, "knowledgeBaseMapper", knowledgeBaseMapper);

        corpus = new ArrayList<>();
        corpus.add(entry(1L, "柑橘溃疡病",
                "叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈，叶背隆起更明显",
                "高温高湿多雨季节发病重，潜叶蛾造成伤口会加重发病"));
        corpus.add(entry(2L, "柑橘红蜘蛛",
                "叶片出现灰白色失绿小点，随后变为灰白色，翻看叶背可见细小红色螨体",
                "高温干旱条件下繁殖极快，长期使用广谱杀虫剂杀伤天敌后容易暴发"));
        corpus.add(entry(3L, "柑橘缺镁",
                "老叶呈倒V字形黄化，叶脉间失绿变黄而叶基部保持绿色",
                "酸性砂质土壤及长期大量施用钾肥钙肥的果园容易发生"));
    }

    /** 按需打桩：空输入类用例不应查询数据库，所以不能放进 setUp 统一打桩 */
    private void stubCorpus()
    {
        when(knowledgeBaseMapper.selectTzKnowledgeBaseList(any())).thenReturn(corpus);
    }

    @Test
    @DisplayName("按症状关键词应命中对应条目，且正确排序")
    void ranksMatchingEntryFirst()
    {
        stubCorpus();
        List<KnowledgeHit> hits = ragService.retrieve(
                "叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈", 3);

        assertTrue(hits.size() >= 1, "应至少命中一条");
        assertEquals("柑橘溃疡病", hits.get(0).getEntry().getDiseaseName(),
                "最相关的应是溃疡病，实际：" + hits);
    }

    @Test
    @DisplayName("换个症状描述应命中不同条目，而不是永远返回第一条")
    void discriminatesBetweenDiseases()
    {
        stubCorpus();
        List<KnowledgeHit> hits = ragService.retrieve(
                "叶背有细小的红色螨体，叶片灰白色失绿小点，高温干旱", 3);

        assertEquals("柑橘红蜘蛛", hits.get(0).getEntry().getDiseaseName(), "实际：" + hits);
    }

    @Test
    @DisplayName("直接写出病名时应以最高分命中该条目")
    void matchesByDiseaseName()
    {
        stubCorpus();
        List<KnowledgeHit> hits = ragService.retrieve("柑橘缺镁", 3);

        assertEquals("柑橘缺镁", hits.get(0).getEntry().getDiseaseName(), "实际：" + hits);
        assertTrue(ragService.isConfident(hits), "直接写出病名应达到可采信标准");
    }

    @Test
    @DisplayName("简洁但准确的描述要能排到第一位 —— 不能因为说得短就排序吃亏")
    void shortButPreciseDescriptionRanksFirst()
    {
        stubCorpus();
        String brief = "叶片出现近圆形病斑，周围有黄色晕圈";

        List<KnowledgeHit> hits = ragService.retrieve(brief, 3);

        assertFalse(hits.isEmpty(), "应至少命中一条");
        assertEquals("柑橘溃疡病", hits.get(0).getEntry().getDiseaseName(),
                "这是溃疡病的典型描述，应排第一，实际：" + hits);
        // 至于它够不够采信线，由 RagServiceRealCorpusTest 在真实语料上判定 ——
        // 3 条 mock 语料算出的分数量纲说明不了任何事
    }

    @Test
    @DisplayName("带上作物名前缀不该改变排序 —— 分差对常数偏移免疫")
    void cropPrefixDoesNotChangeVerdict()
    {
        stubCorpus();
        String brief = "叶片出现近圆形病斑，周围有黄色晕圈";

        List<KnowledgeHit> plain = ragService.retrieve(brief, 3);
        List<KnowledgeHit> withCrop = ragService.retrieve("柑橘 " + brief, 3);

        assertFalse(plain.isEmpty(), "实际：" + brief);
        assertFalse(withCrop.isEmpty(), "检索串带上作物名后不该反而什么都检不到");
        assertEquals(plain.get(0).getEntry().getDiseaseName(), withCrop.get(0).getEntry().getDiseaseName(),
                "作物名前缀会在每个条目上加一个近似常数分，不该改变谁排第一");
    }

    @Test
    @DisplayName("领先幅度不足时不采信：绝对分再高，几条咬在一起就是没区分度")
    void rejectsWhenTopDoesNotLeadSecond()
    {
        // 这里只验「分差」这条规则本身，所以分数要造在最低分线之上才测得到它
        assertFalse(ragService.isConfident(hitList(20D, 19.47D)), "差 0.53 分，没有区分度");
        assertFalse(ragService.isConfident(hitList(40D, 38.5D)), "差 1.5 分，仍不够");
        assertTrue(ragService.isConfident(hitList(40D, 5D)), "差 35 分，可采信");
        assertTrue(ragService.isConfident(hitList(14.2D, 12.0D)), "差 2.2 分，刚过线");
    }

    @Test
    @DisplayName("绝对分过低时一律不采信，哪怕只有一条命中")
    void rejectsWhenAbsoluteScoreTooLow()
    {
        assertFalse(ragService.isConfident(hitList(9.5D, 0.1D)), "证据量本身就不够");
        assertFalse(ragService.isConfident(hitList(10.9D)), "只有一条命中也要过最低分线");
        assertTrue(ragService.isConfident(hitList(11.5D)), "只有一条命中时看绝对分");
    }

    @Test
    @DisplayName("空结果不采信")
    void rejectsEmptyHits()
    {
        assertFalse(ragService.isConfident(null));
        assertFalse(ragService.isConfident(new ArrayList<KnowledgeHit>()));
    }

    @Test
    @DisplayName("完全无关的描述不应硬凑出结论")
    void returnsNothingForIrrelevantQuery()
    {
        stubCorpus();
        assertTrue(ragService.retrieve("zzz qqq www", 3).isEmpty(), "无关内容不该命中任何条目");
        assertNull(ragService.best("zzz qqq www"), "best 也应返回 null 而不是硬给一条");
    }

    @Test
    @DisplayName("空输入安全返回空列表")
    void handlesBlankInput()
    {
        assertTrue(ragService.retrieve(null, 3).isEmpty());
        assertTrue(ragService.retrieve("", 3).isEmpty());
        assertTrue(ragService.retrieve("   ", 3).isEmpty());
        assertNull(ragService.best(null));
    }

    @Test
    @DisplayName("topN 限制生效")
    void respectsTopN()
    {
        stubCorpus();
        assertEquals(1, ragService.retrieve("柑橘溃疡病 红蜘蛛 缺镁 叶片 病斑 黄化", 1).size());
        assertEquals(2, ragService.retrieve("柑橘溃疡病 红蜘蛛 缺镁 叶片 病斑 黄化", 2).size());
    }

    @Test
    @DisplayName("停用条目不应被检索到")
    void ignoresDisabledEntries()
    {
        stubCorpus();
        // 服务层查的是 status=0，mapper 的过滤由 XML 保证；
        // 这里验证的是「传下去的查询条件确实带了 status=0」
        ragService.retrieve("柑橘溃疡病", 3);

        org.mockito.ArgumentCaptor<TzKnowledgeBase> captor =
                org.mockito.ArgumentCaptor.forClass(TzKnowledgeBase.class);
        org.mockito.Mockito.verify(knowledgeBaseMapper).selectTzKnowledgeBaseList(captor.capture());
        assertEquals("0", captor.getValue().getStatus(), "检索只应覆盖启用中的条目");
    }

    @Test
    @DisplayName("只凭一个通用词命不中任何条目，不该硬给结论")
    void genericWordAloneIsNotEnough()
    {
        stubCorpus();
        assertNull(ragService.best("叶片"),
                "「叶片」这种到处都有的词不构成诊断依据，应返回 null 让人去补充描述");
    }

    /** 构造一组只有分数有意义的命中项，用于单测采信规则本身 */
    private List<KnowledgeHit> hitList(double... scores)
    {
        List<KnowledgeHit> list = new ArrayList<>();
        for (double score : scores)
        {
            list.add(new KnowledgeHit(new TzKnowledgeBase(), score));
        }
        return list;
    }

    private TzKnowledgeBase entry(Long id, String name, String symptoms, String trigger)
    {
        TzKnowledgeBase e = new TzKnowledgeBase();
        e.setKnowledgeId(id);
        e.setCropType("柑橘");
        e.setDiseaseName(name);
        e.setSymptoms(symptoms);
        e.setTriggerConditions(trigger);
        e.setPrevention("加强栽培管理，改善通风透光");
        e.setMedicineNote("药剂种类与用量以农药标签为准");
        e.setSource("单测夹具");
        e.setStatus("0");
        return e;
    }
}
