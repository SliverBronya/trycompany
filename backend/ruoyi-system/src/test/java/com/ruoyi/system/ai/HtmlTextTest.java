package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 报告文本转义单测。
 *
 * 报告正文是拼成 HTML 存库、前端 v-html 渲染的，所以这里每一条用例都对应
 * 一个真实的注入面：症状描述由农技员手填、建议与依据来自大模型输出。
 *
 * @author tianzhen
 */
class HtmlTextTest
{
    @Test
    @DisplayName("尖括号必须被转义，脚本标签不能原样落进报告")
    void escapesScriptTag()
    {
        String escaped = HtmlText.escape("<script>alert(1)</script>");

        assertFalse(escaped.contains("<script>"), "转义后不该还有可执行的标签");
        assertTrue(escaped.contains("&lt;script&gt;"), "实际：" + escaped);
    }

    @Test
    @DisplayName("转义后可以安全放进属性与文本节点")
    void escapesQuotesAndAmpersand()
    {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", HtmlText.escape("&<>\"'"));
    }

    @Test
    @DisplayName("& 只转义一次，不能出现 &amp;amp; 这种双重转义")
    void doesNotDoubleEscape()
    {
        assertEquals("&amp;lt;", HtmlText.escape("&lt;"));
    }

    @Test
    @DisplayName("换行转换：nl2br 出标签，pre 保持原样交给 CSS 排版")
    void handlesNewlines()
    {
        assertEquals("第一行<br/>第二行", HtmlText.nl2br("第一行\n第二行"));
        assertEquals("第一行<br/>第二行", HtmlText.nl2br("第一行\r\n第二行"), "CRLF 应归一");
        assertEquals("第一行\n第二行", HtmlText.pre("第一行\n第二行"), "pre 不该插入任何标签");
    }

    @Test
    @DisplayName("空值与 null 安全，返回空串而不是 null")
    void handlesEmptyInput()
    {
        assertEquals("", HtmlText.escape(null));
        assertEquals("", HtmlText.escape(""));
        assertEquals("", HtmlText.nl2br(null));
        assertEquals("", HtmlText.pre(null));
    }

    @Test
    @DisplayName("换行转换前先转义，不能绕过过滤")
    void escapesBeforeConvertingNewlines()
    {
        String result = HtmlText.nl2br("<img src=x onerror=alert(1)>\n下一行");

        assertFalse(result.contains("<img"), "实际：" + result);
        assertTrue(result.contains("&lt;img"));
    }
}
