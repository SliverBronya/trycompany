package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 剂量校验单测。
 *
 * 这是整个项目里最该有测试的一段代码：它的职责是拦截编造的农药用量，
 * 一旦逻辑写漏，界面上看不出任何异常 —— 用户看到的就是一条理直气壮的假建议。
 *
 * 下面 MEDICINE_NOTE 里的数字**只是校验器的测试夹具**，用来模拟「知识库里已经有了
 * 经审核的用量」这一状态，不代表任何真实的用药推荐。
 *
 * @author tianzhen
 */
class DosageGuardTest
{
    private final DosageGuard guard = new DosageGuard();

    /** 测试夹具：模拟一条已收录若干用量的知识库 medicine_note */
    private static final String MEDICINE_NOTE =
            "可选用的药剂包括 代森锰锌 800倍液、氢氧化铜 1500倍液，"
          + "或 每亩用 30克 的 某可湿性粉剂，安全间隔期 14天。";

    @Test
    @DisplayName("知识库里没有的用量必须被拦截")
    void rejectsDosageNotInKnowledgeBase()
    {
        String generated = "建议喷施 吡唑醚菌酯 2000倍液，连喷 2次。";
        DosageGuardResult result = guard.check(generated, MEDICINE_NOTE);

        assertTrue(result.isHit(), "2000倍液 未收录，必须拦截");
        assertTrue(result.getRejected().contains("2000倍液"), "拦截项应记录原始表述");
        assertFalse(result.getText().contains("2000倍液"), "原文中的用量应被抹掉");
        assertTrue(result.getText().contains("以农药标签为准"), "应替换为可执行的指引");
    }

    @Test
    @DisplayName("知识库里有的用量应原样放行")
    void acceptsDosagePresentInKnowledgeBase()
    {
        String generated = "可选用 代森锰锌 800倍液 进行防治。";
        DosageGuardResult result = guard.check(generated, MEDICINE_NOTE);

        assertFalse(result.isHit(), "800倍液 已在知识库中，不该拦");
        assertEquals(generated, result.getText(), "放行时不得改动原文");
        assertTrue(result.getAccepted().contains("800倍液"), "应记录通过的用量");
    }

    @Test
    @DisplayName("数字相同但单位不同，属于两回事，必须拦截")
    void rejectsWhenNumberMatchesButUnitDiffers()
    {
        // 知识库写的是 1500倍液，模型说 1500克/亩 —— 数字对得上，含义完全不同
        String generated = "建议每亩施用 1500克。";
        DosageGuardResult result = guard.check(generated, MEDICINE_NOTE);

        assertTrue(result.isHit(), "1500克 与知识库中的 1500倍液 不是同一个用量，必须拦");
    }

    @Test
    @DisplayName("区间用量需两端都被收录才放行")
    void rangeRequiresBothEndpointsKnown()
    {
        String noteWithRange = "稀释 1000-1500倍液 喷雾。";

        DosageGuardResult bothKnown = guard.check("稀释 1000-1500倍液 喷雾。", noteWithRange);
        assertFalse(bothKnown.isHit(), "两端都收录时应放行");

        DosageGuardResult oneUnknown = guard.check("稀释 1000-2000倍液 喷雾。", noteWithRange);
        assertTrue(oneUnknown.isHit(), "2000倍 未收录，整个区间应被拦");
    }

    @Test
    @DisplayName("知识库没有用药说明时，任何用量都不能放行")
    void rejectsEverythingWhenKnowledgeBaseIsSilent()
    {
        String generated = "建议使用 500倍液 喷雾，每亩 20毫升。";
        DosageGuardResult result = guard.check(generated, null);

        assertTrue(result.isHit(), "知识库为空时不该放行任何用量");
        assertEquals(2, result.getRejected().size(), "两处用量都应被拦下：" + result.getRejected());
        assertTrue(result.explain().contains("请以农药产品标签为准"), "应给出可执行指引");
    }

    @Test
    @DisplayName("非用量的数字不能被误伤")
    void doesNotTouchNonDosageNumbers()
    {
        String generated = "该病在 3年 生以上果树、每年 5次 新梢期均可能发生，连续 2次 风雨后加重。";
        DosageGuardResult result = guard.check(generated, MEDICINE_NOTE);

        assertFalse(result.isHit(), "年份、次数不是用量，不该拦截");
        assertEquals(generated, result.getText(), "不该改动原文");
    }

    @Test
    @DisplayName("浓度百分比同样受管，且只拦未收录的那一处")
    void guardsPercentageConcentration()
    {
        assertTrue(guard.check("喷施 25% 悬浮剂。", MEDICINE_NOTE).isHit(),
                "知识库未收录 25%，应拦");

        // 知识库收录了 2000倍液 但没收录 25%，于是同一条建议里只该拦一处
        DosageGuardResult result = guard.check("喷施 25% 悬浮剂，稀释 2000倍液 喷雾。",
                "可用 2000倍液 喷雾。");
        assertTrue(result.isHit());
        assertEquals(1, result.getRejected().size(), "只该拦 25%：" + result.getRejected());
        assertTrue(result.getRejected().contains("25%"), "被拦的应是 25%");
        assertTrue(result.getText().contains("2000倍液"), "已收录的用量不该被动");
    }

    @Test
    @DisplayName("空文本与无用量文本都安全")
    void handlesEmptyAndCleanText()
    {
        assertFalse(guard.check(null, MEDICINE_NOTE).isHit());
        assertFalse(guard.check("", MEDICINE_NOTE).isHit());
        assertFalse(guard.check("加强修剪、改善通风透光，保护天敌。", MEDICINE_NOTE).isHit());
        assertFalse(guard.containsDosage("加强修剪、改善通风透光。"));
        assertTrue(guard.containsDosage("稀释 800倍液。"));
    }

    @Test
    @DisplayName("单位别名应归一，倍液与倍视为同一单位")
    void normalizesUnitAliases()
    {
        // 知识库写「倍」，模型写「倍液」，是同一个用量
        DosageGuardResult result = guard.check("稀释 800倍液 喷雾。", "稀释 800倍 喷雾。");
        assertFalse(result.isHit(), "倍与倍液应归一为同一单位");
    }

    @Test
    @DisplayName("拦截记录应去重，但提示语不得复述被拦下的数字")
    void explainIsDeduplicated()
    {
        DosageGuardResult result = guard.check("用 2000倍液 或 2000倍液 处理。", MEDICINE_NOTE);
        assertTrue(result.isHit());
        assertEquals(1, result.getRejected().size(), "同一表述多次出现只记一次");
        assertTrue(result.getRejected().get(0).contains("2000倍液"), "审计通道要能看出拦的是哪一条");

        // 提示语会被拼进正文、进而被巡田报告整段引用，所以不能出现数字本身
        assertFalse(result.explain().contains("2000倍液"), "提示语不得复述被拦截的用量：实际 " + result.explain());
        assertTrue(result.explain().contains("1 处"), "只报处数即可");
        assertTrue(result.explain().contains("请以农药产品标签为准"), "仍要给出可执行指引");
    }

    @Test
    @DisplayName("置信度不是用药量，不得被当成未收录用量抹掉")
    void keepsConfidenceFigureIntact()
    {
        // 这是建议正文里真实出现过的一句话。置信度由我方模板拼入，
        // 早期版本会用「（用量待确认，请以农药标签为准）」把它替换掉，
        // 用户看到的是「柑橘溃疡病（置信度 （用量待确认，请以农药标签为准））」。
        String generated = "柑橘溃疡病（置信度 90%）";
        DosageGuardResult result = guard.check(generated, MEDICINE_NOTE);

        assertFalse(result.isHit(), "比率指标不是用量，不该拦：" + result.getRejected());
        assertEquals(generated, result.getText(), "原文应逐字保留");
        assertFalse(result.getAccepted().contains("90%"), "它也不是可用量，不该进 accepted");
    }

    @Test
    @DisplayName("发病率、防效一类的比率同样不受用量校验管辖")
    void keepsRateFiguresIntact()
    {
        assertFalse(guard.check("该园发病率 80%，防效 65%。", MEDICINE_NOTE).isHit());
        assertFalse(guard.check("病株率 5%，被害率 12%。", MEDICINE_NOTE).isHit());
    }

    @Test
    @DisplayName("比率与真用量同句出现时，只拦真用量")
    void rateContextDoesNotShieldRealDosage()
    {
        DosageGuardResult result = guard.check("发病率 80% 的果园可喷 2000倍液。", MEDICINE_NOTE);

        assertTrue(result.isHit(), "2000倍液 未收录，必须拦");
        assertEquals(1, result.getRejected().size(), "只该拦一处：" + result.getRejected());
        assertEquals("2000倍液", result.getRejected().get(0));
        assertTrue(result.getText().contains("发病率 80%"), "比率应原样留在正文里");
    }

    @Test
    @DisplayName("「度」不是比率语境，「浓度 0.5%」这类真用量必须继续受管")
    void doesNotTreatConcentrationAsRateContext()
    {
        // 边界是刻意收窄的：出现「浓度」时后面那个百分数就是货真价实的用量。
        // 若有人把 RATE_CONTEXT 顺手扩成「度」，这条会立刻失败。
        DosageGuardResult result = guard.check("稀释至 浓度 0.5% 后喷施。", MEDICINE_NOTE);

        assertTrue(result.isHit(), "浓度百分数是真用量，必须拦");
        assertTrue(result.getRejected().contains("0.5%"), "实际：" + result.getRejected());
    }
}
