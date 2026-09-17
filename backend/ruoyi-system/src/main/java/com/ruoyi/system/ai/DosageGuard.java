package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 剂量校验（合规硬约束的落地）。
 *
 * PRD 反复强调「不编造农药剂量」。这条路光靠提示词约束是靠不住的 ——
 * 大模型在演示现场给一个「看起来很合理」的稀释倍数，谁也看不出来那是编的。
 * 所以必须在模型输出之后再过一道机器校验：
 *
 *   从生成文本里抽出所有带单位的用量表述 → 与知识库该条目的 medicine_note 比对
 *   → 知识库里没有的，一律从文本中抹掉并说明原因。
 *
 * 校验的是「数值+单位」这个组合对，而不是裸数字。因为知识库写了 1500 倍液，
 * 模型说 1500 克/亩，数字对得上但单位变了，这是两回事，必须拦。
 *
 * 宁可错拦、不可放过：拦多了只是提示语啰嗦，漏一条就是一次合规事故。
 *
 * @author tianzhen
 */
@Component
public class DosageGuard
{
    /**
     * 用量表述识别。分组 1、2 是可选的区间两端，分组 3 是单位。
     * 单位按长度降序排列，保证 倍液 优先于 倍、千克/亩 优先于 千克 被匹配到。
     */
    private static final Pattern DOSAGE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)"
          + "(?:\\s*[-~～至]\\s*(\\d+(?:\\.\\d+)?))?"
          + "\\s*"
          + "(倍液|倍|千克/亩|公斤/亩|kg/亩|克/亩|g/亩|毫升/亩|ml/亩|升/亩|L/亩"
          + "|毫克/千克|mg/kg|克/升|毫升/升"
          + "|千克|公斤|kg|毫克|mg|毫升|ml|升|ppm|%|‰"
          + "|克|g|L)(?![a-zA-Z0-9])",
            Pattern.CASE_INSENSITIVE);

    /**
     * 「比率 / 效果指标」语境：紧跟在数值前面的字是这两个之一（或「置信度」）时，
     * 后面的百分数、千分数不是用药量，而是发病率、防效、把握程度一类的描述。
     *
     * 为什么需要这条：本项目的建议正文里，「置信度 90%」是我们自己的模板拼进去的元信息，
     * 而 % 又在受管单位里，于是它会被当成未收录用量抹掉，用户看到的是
     * 「柑橘溃疡病（置信度 （用量待确认，请以农药标签为准））」这种读不通的句子。
     * 拦错一个真用量是合规事故，把置信度改写掉同样是交付事故 —— 两者都要避免。
     *
     * ⚠️ 认「率」「效」「置信度」，以及明确的环境量词「湿度」，
     *    但**不认泛化的「度」**：因为「浓度 0.5%」是真用量，恰恰必须继续受管。
     *
     * 加「湿度」的由来：「湿度控制在 60%-70%」是大棚管理类问答的常见说法，
     * 被当成用量抹掉后会变成「湿度控制在（用量待确认，请以农药标签为准）-（用量待确认…）」
     * —— 一句话读不通，而且它显然不是在讲用药。这是放开问答范围（不再限定柑橘）
     * 之后立刻暴露出来的：以前只答柑橘病害，很少出现"控温控湿"这类管理性问题。
     *
     * 只加「湿度」这一个确切词，不扩成泛化的「度」—— 一旦收「度」，
     * 「浓度 0.5%」就会一起被放过，那才是真正的合规事故。
     */
    private static final Pattern RATE_CONTEXT = Pattern.compile("(率|效|置信度|湿度)$");

    /** 单位别名归一：同一种单位的不同写法不该被当成两个东西 */
    private static final String[][] UNIT_ALIASES = {
            { "倍液", "倍" },
            { "g/亩", "克/亩" }, { "kg/亩", "千克/亩" }, { "公斤/亩", "千克/亩" },
            { "ml/亩", "毫升/亩" }, { "L/亩", "升/亩" },
            { "mg/kg", "毫克/千克" },
            { "g", "克" }, { "kg", "千克" }, { "mg", "毫克" },
            { "ml", "毫升" }, { "L", "升" },
    };

    /**
     * 校验并清洗一段文本。
     *
     * @param text         待校验文本（通常是大模型生成的防治建议）
     * @param medicineNote 知识库中该条目的用药说明，是唯一可信依据；为空表示什么都没收录
     * @return 校验结果，含清洗后的文本与被拦截项
     */
    public DosageGuardResult check(String text, String medicineNote)
    {
        if (StringUtils.isBlank(text))
        {
            return new DosageGuardResult(text, new ArrayList<String>(), new ArrayList<String>());
        }

        Set<String> allowed = extractPairs(medicineNote);
        List<String> rejected = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        Set<String> rejectedDistinct = new LinkedHashSet<>();

        Matcher matcher = DOSAGE_PATTERN.matcher(text);
        StringBuilder cleaned = new StringBuilder();

        while (matcher.find())
        {
            // 比率 / 效果指标不是用量：原样放过，既不入 accepted 也不入 rejected
            if (isRateFigure(text, matcher))
            {
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            String matched = matcher.group().trim();
            List<String> pairs = toPairs(matcher.group(1), matcher.group(2), matcher.group(3));

            boolean allAllowed = !pairs.isEmpty();
            for (String pair : pairs)
            {
                if (!allowed.contains(pair))
                {
                    allAllowed = false;
                    break;
                }
            }

            if (allAllowed)
            {
                accepted.add(matched);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matched));
            }
            else
            {
                rejected.add(matched);
                rejectedDistinct.add(matched);
                // 抹掉具体数字，留一句可执行的指引，而不是留个空格让句子读不通
                matcher.appendReplacement(cleaned,
                        Matcher.quoteReplacement("（用量待确认，请以农药标签为准）"));
            }
        }
        matcher.appendTail(cleaned);

        return new DosageGuardResult(cleaned.toString(), new ArrayList<>(rejectedDistinct), accepted);
    }

    /**
     * 文本里是否含有任何用量表述（用于「知识库为空却整段在讲用药」这类判断）。
     */
    public boolean containsDosage(String text)
    {
        return StringUtils.isNotBlank(text) && DOSAGE_PATTERN.matcher(text).find();
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 从文本中抽出全部「数值+单位」对。区间会展开成两个端点，
     * 于是「10-15克」只有在知识库同时收录 10 克与 15 克时才算通过。
     */
    private Set<String> extractPairs(String text)
    {
        Set<String> pairs = new HashSet<>();
        if (StringUtils.isBlank(text))
        {
            return pairs;
        }
        Matcher matcher = DOSAGE_PATTERN.matcher(text);
        while (matcher.find())
        {
            // 白名单同样排除比率/效果指标：知识库里写「防效 80%」不该把 80% 变成可用量
            if (isRateFigure(text, matcher))
            {
                continue;
            }
            pairs.addAll(toPairs(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return pairs;
    }

    /**
     * 判断这一处匹配是不是「比率 / 效果指标」而不是用量。
     *
     * 只看紧跟其前的字，并忽略其间空白 —— 模板里写的是「置信度 90%」，
     * 数字前面隔了一个空格，不忽略空白就认不出「度」这个后缀。
     */
    private boolean isRateFigure(String text, Matcher matcher)
    {
        String unit = matcher.group(3);
        if (unit == null)
        {
            return false;
        }

        String normalized = normalizeUnit(unit);
        if (!"%".equals(normalized) && !"‰".equals(normalized))
        {
            return false;
        }

        String before = text.substring(0, matcher.start()).replaceAll("\\s+$", "");
        return RATE_CONTEXT.matcher(before).find();
    }

    private List<String> toPairs(String low, String high, String unit)
    {
        String normalizedUnit = normalizeUnit(unit);
        List<String> pairs = new ArrayList<>();
        if (StringUtils.isNotBlank(low))
        {
            pairs.add(trimNumber(low) + normalizedUnit);
        }
        if (StringUtils.isNotBlank(high))
        {
            pairs.add(trimNumber(high) + normalizedUnit);
        }
        return pairs;
    }

    private String normalizeUnit(String unit)
    {
        if (unit == null)
        {
            return "";
        }
        String trimmed = unit.trim();
        for (String[] alias : UNIT_ALIASES)
        {
            if (alias[0].equalsIgnoreCase(trimmed))
            {
                return alias[1];
            }
        }
        return trimmed;
    }

    /** 3.0 与 3 视为同一个数，避免因为小数点写法差异误拦 */
    private String trimNumber(String number)
    {
        String trimmed = number.trim();
        if (trimmed.contains("."))
        {
            trimmed = trimmed.replaceAll("0+$", "");
            trimmed = trimmed.replaceAll("\\.$", "");
        }
        return trimmed;
    }

    /** 供单测与排障查看当前识别的单位集合 */
    protected static List<String> knownUnits()
    {
        List<String> units = new ArrayList<>();
        for (String[] alias : UNIT_ALIASES)
        {
            units.add(alias[0]);
        }
        return Arrays.asList("倍", "克/亩", "毫升/亩", "%", "‰", "ppm");
    }
}
