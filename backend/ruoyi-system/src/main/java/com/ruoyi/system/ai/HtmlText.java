package com.ruoyi.system.ai;

import org.apache.commons.lang3.StringUtils;

/**
 * 报告文本的 HTML 转义。
 *
 * 单独抽出来是因为它是报告页唯一的安全边界：巡田报告是拼成 HTML 存库、
 * 前端用 v-html 渲染的，而其中相当一部分内容来自**不可信输入** ——
 * 农技员手填的症状描述、以及大模型的输出（大模型的输入里就混着农技员的文字，
 * 一句精心构造的症状描述足以让它回吐出任意标记）。任何一处漏转义，
 * 报告页就成了可被注入的执行点。
 *
 * 所以规则很简单：**凡是拼进 HTML 的动态内容，一律先过 escape()**。
 *
 * @author tianzhen
 */
public final class HtmlText
{
    private HtmlText()
    {
    }

    /**
     * 转义 HTML 特殊字符。
     *
     * 注意 & 必须最先替换，否则后续替换产生的 &amp; 会被二次转义成 &amp;amp;。
     */
    public static String escape(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 转义后把换行转成 &lt;br/&gt;，用于正文段落。
     */
    public static String nl2br(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return escape(normalized).replace("\n", "<br/>");
    }

    /**
     * 转义后原样保留换行，交给前端用 CSS（white-space: pre-wrap）排版。
     * 用于多行的防治建议，避免把 <br/> 拼进每一行。
     */
    public static String pre(String text)
    {
        return escape(text);
    }
}
