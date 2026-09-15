package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 大模型 JSON 输出的宽容解析单测。
 *
 * 提示词里写了「只输出 JSON」，但现实中模型会用代码块包、会先寒暄一句、
 * 会多个尾逗号。解析一旦脆，演示现场就是白屏。这里把见过的脏数据形态都固化下来。
 *
 * @author tianzhen
 */
class LlmJsonTest
{
    @Test
    @DisplayName("标准 JSON 直接解析")
    void parsesPlainJson()
    {
        Map<String, Object> map = LlmJson.parse(
                "{\"diagnosisName\":\"柑橘溃疡病\",\"confidence\":88,\"riskLevel\":\"2\"}");

        assertEquals("柑橘溃疡病", LlmJson.str(map, "diagnosisName", null));
        assertEquals(0, new BigDecimal("88").compareTo(LlmJson.num(map, "confidence", BigDecimal.ZERO)));
        assertEquals("2", LlmJson.code(map, "riskLevel", null));
    }

    @Test
    @DisplayName("markdown 代码块包裹能剥掉")
    void stripsCodeFence()
    {
        String raw = "```json\n{\"diagnosisName\":\"柑橘炭疽病\",\"confidence\":75}\n```";
        Map<String, Object> map = LlmJson.parse(raw);

        assertEquals("柑橘炭疽病", LlmJson.str(map, "diagnosisName", null));
    }

    @Test
    @DisplayName("前后带寒暄文字也能截出 JSON")
    void slicesJsonFromProse()
    {
        String raw = "好的，根据图片和描述，我的判断如下：\n"
                + "{\"diagnosisName\":\"柑橘红蜘蛛\",\"confidence\":80}\n"
                + "以上结论仅供参考。";
        Map<String, Object> map = LlmJson.parse(raw);

        assertEquals("柑橘红蜘蛛", LlmJson.str(map, "diagnosisName", null));
    }

    @Test
    @DisplayName("结尾多逗号这种脏数据要能容错")
    void toleratesTrailingComma()
    {
        Map<String, Object> map = LlmJson.parse("{\"diagnosisName\":\"柑橘缺镁\",\"confidence\":70,}");

        assertEquals("柑橘缺镁", LlmJson.str(map, "diagnosisName", null));
    }

    @Test
    @DisplayName("置信度：字符串、带单位、比例值都能收敛到百分制")
    void normalizesConfidence()
    {
        assertEquals(0, new BigDecimal("85").compareTo(
                LlmJson.num(LlmJson.parse("{\"confidence\":\"85\"}"), "confidence", BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("85").compareTo(
                LlmJson.num(LlmJson.parse("{\"confidence\":\"85分\"}"), "confidence", BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("85").compareTo(
                LlmJson.num(LlmJson.parse("{\"confidence\":0.85}"), "confidence", BigDecimal.ZERO)),
                "0-1 的小数应视作比例换算成百分制");
        assertEquals(0, new BigDecimal("100").compareTo(
                LlmJson.num(LlmJson.parse("{\"confidence\":120}"), "confidence", BigDecimal.ZERO)),
                "越界值应被截断到 100");
        assertEquals(0, new BigDecimal("0").compareTo(
                LlmJson.num(LlmJson.parse("{\"confidence\":-5}"), "confidence", BigDecimal.ZERO)),
                "负值应被截断到 0");
    }

    @Test
    @DisplayName("风险等级中文也能映射成枚举码")
    void mapsChineseRiskLevel()
    {
        assertEquals("1", LlmJson.code(LlmJson.parse("{\"riskLevel\":\"低风险\"}"), "riskLevel", null));
        assertEquals("2", LlmJson.code(LlmJson.parse("{\"riskLevel\":\"中风险\"}"), "riskLevel", null));
        assertEquals("3", LlmJson.code(LlmJson.parse("{\"riskLevel\":\"高风险\"}"), "riskLevel", null));
        assertEquals("2", LlmJson.code(LlmJson.parse("{\"riskLevel\":\"2\"}"), "riskLevel", null));
    }

    @Test
    @DisplayName("解析不出来时返回空 Map，而不是抛异常炸掉整个请求")
    void returnsEmptyMapOnGarbage()
    {
        assertTrue(LlmJson.parse(null).isEmpty());
        assertTrue(LlmJson.parse("").isEmpty());
        assertTrue(LlmJson.parse("模型今天不想干活").isEmpty());
        assertTrue(LlmJson.parse("{不是合法JSON}").isEmpty());
    }

    @Test
    @DisplayName("缺字段时返回默认值，不返回 null 字符串")
    void fallsBackToDefaults()
    {
        Map<String, Object> map = LlmJson.parse("{\"diagnosisName\":\"柑橘溃疡病\"}");

        assertEquals("未给出", LlmJson.str(map, "basis", "未给出"));
        assertEquals("2", LlmJson.code(map, "riskLevel", "2"));
        assertEquals(0, BigDecimal.ZERO.compareTo(LlmJson.num(map, "confidence", BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("空字符串字段按缺失处理")
    void treatsBlankAsMissing()
    {
        Map<String, Object> map = LlmJson.parse("{\"basis\":\"   \"}");
        assertEquals("未给出", LlmJson.str(map, "basis", "未给出"));
    }

    @Test
    @DisplayName("分条字段被模型写成 JSON 数组时，拼成按行文本而不是 [a, b]")
    void joinsArrayFieldIntoLines()
    {
        // 实测遇到的真实偏差：提示词要的是字符串、并要求「分条列出」，
        // 模型就干脆返一个数组。直接 String.valueOf 会得到 "[第一条。, 第二条。]"，
        // 填进界面看着像系统坏了。
        Map<String, Object> map = LlmJson.parse(
                "{\"guidance\":[\"选择晴天施用。\",\"沿滴水线开沟。\",\"施后覆土浇水。\"]}");

        String text = LlmJson.str(map, "guidance", null);

        assertEquals("选择晴天施用。\n沿滴水线开沟。\n施后覆土浇水。", text);
        assertTrue(!text.contains("["), "不应残留方括号：" + text);
    }

    @Test
    @DisplayName("数组里混了空白项时跳过，不留空行")
    void skipsBlankItemsInArray()
    {
        Map<String, Object> map = LlmJson.parse("{\"guidance\":[\"第一条\",\"  \",null,\"第二条\"]}");
        assertEquals("第一条\n第二条", LlmJson.str(map, "guidance", null));
    }
}
