package com.ruoyi.system.ai;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

/**
 * 大模型 JSON 输出的宽容解析。
 *
 * 即使提示词里写了「只输出 JSON」，实际仍会遇到：
 * 用 ```json 包起来、前后带一句寒暄、结尾多个逗号等情况。
 * 直接 JSON.parse 会在演示现场翻车，所以这里做三步兜底：
 *   剥代码块 → 截取首个 { 到最后一个 } → 容错解析。
 *
 * @author tianzhen
 */
public final class LlmJson
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmJson()
    {
    }

    /**
     * 从模型输出中解析出 JSON 对象。
     *
     * @param raw 模型原始输出
     * @return 解析结果；无法解析时返回空 Map（调用方按「没拿到有效结论」处理）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String raw)
    {
        if (StringUtils.isBlank(raw))
        {
            return new LinkedHashMap<>();
        }

        String candidate = stripCodeFence(raw.trim());
        candidate = sliceJsonObject(candidate);
        if (StringUtils.isBlank(candidate))
        {
            return new LinkedHashMap<>();
        }

        try
        {
            return MAPPER.readValue(candidate, Map.class);
        }
        catch (Exception ignored)
        {
            // 常见脏数据：结尾多了逗号
            try
            {
                return MAPPER.readValue(candidate.replaceAll(",\\s*([}\\]])", "$1"), Map.class);
            }
            catch (Exception e)
            {
                return new LinkedHashMap<>();
            }
        }
    }

    /**
     * 取字符串字段，缺失或为空时返回默认值。
     *
     * <p>模型有时会**把「分条列出」的字段写成 JSON 数组**（提示词里要的是字符串，
     * 但这是很常见的偏差）。直接 {@code String.valueOf} 会得到
     * {@code [第一条。, 第二条。]} 这种带方括号与逗号的样子，填进界面看着就像系统坏了。
     * 这里统一把数组按行拼成文本，每项占一行。
     */
    public static String str(Map<String, Object> map, String key, String defaultValue)
    {
        Object value = map == null ? null : map.get(key);
        if (value == null)
        {
            return defaultValue;
        }

        if (value instanceof List)
        {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) value)
            {
                if (item == null)
                {
                    continue;
                }
                String line = String.valueOf(item).trim();
                if (line.isEmpty())
                {
                    continue;
                }
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(line);
            }
            String joined = sb.toString().trim();
            return joined.isEmpty() ? defaultValue : joined;
        }

        String text = String.valueOf(value).trim();
        // 模型偶尔会照着提示词把「无」「未收录」原样写回来，这属于有效值，不能当空
        return text.isEmpty() ? defaultValue : text;
    }

    /**
     * 取数值字段。模型有时返回 "85"、有时返回 "85分"、有时返回 0.85，
     * 这里统一收敛到 0-100 的置信度语义上。
     */
    public static BigDecimal num(Map<String, Object> map, String key, BigDecimal defaultValue)
    {
        Object value = map == null ? null : map.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (value instanceof Number)
        {
            return normalize((Number) value);
        }

        String text = String.valueOf(value).replaceAll("[^0-9.]", "");
        if (StringUtils.isBlank(text))
        {
            return defaultValue;
        }
        try
        {
            return normalize(new BigDecimal(text));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    /**
     * 取 1/2/3 这类枚举码，容忍模型返回「中风险」这种中文。
     */
    public static String code(Map<String, Object> map, String key, String defaultValue)
    {
        String text = str(map, key, null);
        if (text == null)
        {
            return defaultValue;
        }
        if (text.contains("1") || text.contains("低")) return "1";
        if (text.contains("2") || text.contains("中")) return "2";
        if (text.contains("3") || text.contains("高")) return "3";
        return defaultValue;
    }

    // ------------------------------------------------------------------ 内部

    private static BigDecimal normalize(Number raw)
    {
        double d = raw.doubleValue();
        // 模型可能返回 0-1 的概率值，按 <1 视为比例换算成百分制
        if (d > 0D && d < 1D)
        {
            d = d * 100D;
        }
        if (d < 0D) d = 0D;
        if (d > 100D) d = 100D;
        return BigDecimal.valueOf(Math.round(d * 100D) / 100D);
    }

    private static String stripCodeFence(String text)
    {
        String result = text;
        if (result.startsWith("```"))
        {
            int firstNewline = result.indexOf('\n');
            if (firstNewline > 0)
            {
                result = result.substring(firstNewline + 1);
            }
            int lastFence = result.lastIndexOf("```");
            if (lastFence >= 0)
            {
                result = result.substring(0, lastFence);
            }
        }
        return result.trim();
    }

    private static String sliceJsonObject(String text)
    {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            return "";
        }
        return text.substring(start, end + 1);
    }
}
