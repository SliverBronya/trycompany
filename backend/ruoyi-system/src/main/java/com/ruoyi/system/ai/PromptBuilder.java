package com.ruoyi.system.ai;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzScoutingRecord;

/**
 * 提示词组装。
 *
 * PRD 反复强调「不编造剂量、建议要有依据、必须带免责」。这三条不能只写在文档里，
 * 必须落到提示词的硬约束上 —— 所以系统提示词里明确禁止编造用药信息、
 * 明确要求只依据给定的知识库片段作答，并强制带上免责声明字段。
 *
 * 输出统一要求为 JSON：结构化才好做后置校验（尤其是剂量校验），
 * 自由文本没法可靠地做合规拦截。
 *
 * @author tianzhen
 */
@Component
public class PromptBuilder
{
    /**
     * 所有任务共用的硬性约束（不含角色定位）。
     *
     * 单独拎出来是因为「约束」和「角色」是两件事：约束是安全底线，任何任务都不许松；
     * 角色是该任务的知识范围，问答链路就该比诊断链路宽得多。
     */
    private static final String COMMON_RULES =
            "\n"
          + "必须遵守以下硬性约束，违反任意一条都视为回答失败：\n"
          + "1. 只输出一个 JSON 对象，不要输出任何 JSON 之外的说明文字，不要用 markdown 代码块包裹。\n"
          + "2. 【禁止编造用药信息】凡涉及农药名称、稀释倍数、施用浓度、每亩用量，"
          + "只能复述「知识库参考」中已经明确写出的内容。知识库没写的，一律填 \"未收录\"，"
          + "绝对不允许凭常识或记忆补一个数字。\n"
          + "3. 结论要基于可见的症状特征与给出的知识库依据，不要臆测。\n"
          + "4. 拿不准就降低 confidence，宁可说没把握，也不要给一个看起来很确定的错结论。\n"
          + "5. 你的结论是辅助判断，不能替代农技人员现场诊断，这一点要体现在 advise 字段里。\n";

    /** 诊断/建议/照片描述三条链路共用：聚焦柑橘，因为知识库就是柑橘的 */
    private static final String SYSTEM_PROMPT =
            "你是一名服务于基层农技员的柑橘植保辅助诊断助手。\n"
          + COMMON_RULES;

    /**
     * 问答链路专用的角色：不限定作物与话题。
     *
     * 原来问答套用的是「柑橘植保诊断助手」这个角色，结果问什么都往柑橘上靠 ——
     * 而农技员和种植户实际会问水稻、蔬菜、其他果树，也会问天气影响、栽培管理、
     * 土肥水管理这些和柑橘不相干的事。把角色放宽成通用农业技术顾问，
     * 模型才能发挥它本来就有的能力。
     *
     * 注意：放宽的只是「知识范围」，上面五条硬性约束一条不减 ——
     * 尤其第 2 条不许编造剂量，那是安全底线，不是能力边界。
     */
    private static final String QA_SYSTEM_PROMPT =
            "你是一名服务于基层农技员与种植户的农业技术顾问，熟悉大田作物、蔬菜、果树与园艺，"
          + "也了解栽培管理、土壤肥料、病虫害防治、农业气象、采后处理与农产品市场等周边话题。\n"
          + COMMON_RULES;

    /**
     * 诊断任务的系统提示词。
     */
    public String diagnosisSystemPrompt()
    {
        return SYSTEM_PROMPT
             + "\n输出 JSON 字段（全部必填）：\n"
             + "{\n"
             + "  \"diagnosisName\": \"病虫害或缺素名称，如 柑橘溃疡病\",\n"
             + "  \"confidence\": 0 到 100 的数字，表示你对该结论的把握,\n"
             + "  \"riskLevel\": \"1\" 或 \"2\" 或 \"3\"，分别代表低、中、高风险,\n"
             + "  \"basis\": \"判断依据，说明你看到了哪些症状特征支持这个结论\",\n"
             + "  \"alternatives\": \"可能的其他解释，没有就填 无\"\n"
             + "}\n";
    }

    /**
     * 诊断任务的用户提示词。
     *
     * @param record   巡田记录（含作物、部位、症状、严重度）
     * @param cropType 作物类型
     * @param evidence 检索**确有区分度**时才传入的知识库条目，作为唯一可信的用药依据；
     *                 检索不自信时调用方传空列表，这里的措辞会随之改变（见方法内注释）
     * @param hasImage 本次是否附带了图片
     */
    public String diagnosisUserPrompt(TzScoutingRecord record, String cropType,
                                      List<KnowledgeHit> evidence, boolean hasImage)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【本次巡田信息】\n");
        sb.append("作物：").append(StringUtils.defaultIfBlank(cropType, "柑橘")).append("\n");
        sb.append("发生部位：").append(plantPartText(record.getPlantPart())).append("\n");
        sb.append("严重程度：").append(severityText(record.getSeverity())).append("\n");
        sb.append("农技员描述的症状：")
          .append(StringUtils.defaultIfBlank(record.getSymptomText(), "（未填写）")).append("\n");
        if (hasImage) {
            sb.append("现场照片：已随本条消息附上，请结合图像特征判断。\n");
        } else {
            sb.append("现场照片：本次没有可用照片，请仅依据文字描述判断，并在 basis 中说明这一点。\n");
        }

        sb.append("\n【知识库参考】\n");
        if (evidence == null || evidence.isEmpty()) {
            // 这里分两种情况，措辞必须不同。曾经统一写成「confidence 不应高于 50」，
            // 而系统采信阈值是 60，于是一张清楚的照片也必然被拒 —— 等于把「没有文字描述」
            // 直接判成「无法诊断」，与「拍照就能登记」的产品目标相悖。
            if (hasImage) {
                sb.append("知识库这次没有检索到可靠的参考条目（这不代表植株没问题，只是描述与条目对不上）。\n")
                  .append("请完全依据照片与文字描述判断；confidence 按你实际看到的把握如实给，")
                  .append("既不因为缺少参考就压低，也不许凭印象高估。用药信息一律填 \"未收录\"。\n");
            } else {
                sb.append("既没有可用的照片，知识库也没有检索到可靠的参考条目，")
                  .append("此时你不具备判断条件：diagnosisName 填 \"无法判断\"，")
                  .append("confidence 填 0 到 30 之间的数，用药信息一律填 \"未收录\"。\n");
            }
        } else {
            int i = 1;
            for (KnowledgeHit hit : evidence) {
                TzKnowledgeBase e = hit.getEntry();
                sb.append("--- 条目 ").append(i++).append("（相关度 ").append(Math.round(hit.getScore()))
                  .append("）---\n");
                sb.append("名称：").append(e.getDiseaseName()).append("\n");
                sb.append("特征性表现（判这个病的关键依据）：")
                  .append(StringUtils.defaultIfBlank(e.getKeyFeatures(), "无")).append("\n");
                sb.append("完整症状：").append(StringUtils.defaultIfBlank(e.getSymptoms(), "无")).append("\n");
                sb.append("鉴别要点（与易混病怎么区分）：")
                  .append(StringUtils.defaultIfBlank(e.getDifferential(), "无")).append("\n");
                sb.append("发生条件：").append(StringUtils.defaultIfBlank(e.getTriggerConditions(), "无")).append("\n");
                sb.append("防治措施：").append(StringUtils.defaultIfBlank(e.getPrevention(), "无")).append("\n");
                sb.append("用药注意：").append(StringUtils.defaultIfBlank(e.getMedicineNote(), "未收录")).append("\n");
                sb.append("安全说明：").append(StringUtils.defaultIfBlank(e.getSafetyNote(), "无")).append("\n");
            }
            sb.append("以上只是候选，不是答案。若候选多于一条，请逐条比对「鉴别要点」，"
                    + "先排除不吻合的，再给出结论，并在 basis 里写清你排除了哪个、依据是什么。"
                    + "若它们与照片/描述都不符，以你看到的为准，并据此给出 confidence。\n");
        }

        sb.append("\n请按要求输出 JSON。若知识库参考与照片/描述不符，以你判断的真实结论为准，"
                + "但用药信息仍然只能取自上述知识库条目。\n");
        return sb.toString();
    }

    /**
     * 防治建议任务的系统提示词。四段式结构来自 PRD 对报告格式的要求。
     */
    public String suggestionSystemPrompt()
    {
        return SYSTEM_PROMPT
             + "\n输出 JSON 字段（全部必填）：\n"
             + "{\n"
             + "  \"recognition\": \"第一段：识别结论，一句话说清是什么问题\",\n"
             + "  \"basis\": \"第二段：判断依据，列出支持该结论的症状特征\",\n"
             + "  \"prevention\": \"第三段：防治建议，分农业措施与药剂措施；药剂措施只能来自知识库参考\",\n"
             + "  \"medicine\": \"第三段中的用药部分单独抽出：药名、用法用量。知识库未收录的写 未收录\",\n"
             + "  \"safety\": \"第四段：注意事项与安全说明，含安全间隔期、个人防护、抗性轮换等\",\n"
             + "  \"disclaimer\": \"免责声明，说明本结论为辅助参考、不能替代现场诊断与农药标签\"\n"
             + "}\n";
    }

    /**
     * 防治建议任务的用户提示词。
     */
    public String suggestionUserPrompt(TzScoutingRecord record, TzKnowledgeBase entry)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【诊断结论】\n");
        sb.append("作物：").append(StringUtils.defaultIfBlank(record.getCropType(), "柑橘")).append("\n");
        sb.append("结论：").append(StringUtils.defaultIfBlank(record.getDiagnosisName(), "未给出")).append("\n");
        sb.append("风险等级：").append(riskText(record.getRiskLevel())).append("\n");
        sb.append("判断依据：").append(StringUtils.defaultIfBlank(record.getDiagnosisBasis(), "无")).append("\n");

        sb.append("\n【知识库参考】\n");
        if (entry == null) {
            sb.append("未匹配到知识库条目。此时必须先提示「建议咨询当地农技员」，"
                    + "用药部分一律填 \"未收录\"。\n");
        } else {
            sb.append("名称：").append(entry.getDiseaseName()).append("\n");
            sb.append("防治措施：").append(StringUtils.defaultIfBlank(entry.getPrevention(), "无")).append("\n");
            sb.append("用药注意：").append(StringUtils.defaultIfBlank(entry.getMedicineNote(), "未收录")).append("\n");
            sb.append("安全说明：").append(StringUtils.defaultIfBlank(entry.getSafetyNote(), "无")).append("\n");
            sb.append("资料来源：").append(StringUtils.defaultIfBlank(entry.getSource(), "无")).append("\n");
        }

        sb.append("\n请按四段式要求输出 JSON。再次强调：用药名称与用量只能来自上面的知识库参考。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 农技问答

    /**
     * 农技问答的系统提示词。
     *
     * <p>与诊断/建议不同，问答的输入是自由的：农户可能问「什么时候施冬肥」这种
     * 知识库里根本没有的栽培管理问题。所以这里必须**允许模型用自己的农艺知识回答** ——
     * 十几条知识库覆盖不到的问题如果全都会得到一句「请咨询当地农技员」，
     * 那是把用户推走，不是回答问题。
     *
     * <p>但用药这条线不能松，松的位置很明确：
     * **可以讲哪一类药剂、什么作用机理、什么时候打、打在哪儿；一律不给具体剂量数字。**
     * 理由不是「保守」，是各地登记用药与禁限用目录不同 —— 一个通用数字换到别的产区
     * 可能直接是违规或药害。给不出数字不是能力不足，是这条线的规矩。
     * 后置的 {@link DosageGuard} 按同一口径校验：正文里的用量表述必须能在知识库里找到。
     */
    public String qaSystemPrompt()
    {
        return QA_SYSTEM_PROMPT
             + "\n本次任务：回答农技员或种植户提出的农业技术问题。作物与话题都不设限。\n"
             + "6. 【范围不限于柑橘】问什么就答什么，不要硬往柑橘上靠。\n"
             + "   知识库目前只收录柑橘条目，这不构成你回答范围的边界："
             + "若提问涉及其他作物或其他话题，直接按通用农业知识正常作答，"
             + "不必声明「知识库只收录柑橘」，更不要把柑橘的做法套到别的作物上。\n"
             + "7. 【可以用你自己的农艺知识】不要因为知识库没收录就缩手缩脚：\n"
             + "   - 知识库有相关条目：以条目为准，同时可以用你的知识把它讲透，"
             + "补上条目没写到的农艺背景；\n"
             + "   - 知识库没收录：**照样要正面回答**，用通用的农艺知识与田间经验给出可行做法。"
             + "只回一句「知识库未收录，请咨询农技员」等于没回答，那是最差的一种答复。\n"
             + "8. 【第 2 条在本任务放宽】用药这条线这样划：\n"
             + "   - 可以说：药剂类别或作用机理（如铜制剂、矿物油、生物菌剂、内吸性杀菌剂）、"
             + "施药时机、施药部位、轮换原则，以及为什么这么打；\n"
             + "   - 不要说：具体稀释倍数、施用浓度、每亩用量等**任何剂量数字**。"
             + "剂量只能来自「知识库参考」里明确写出的内容；知识库没写，就把做法讲清楚，不要凑数字。\n"
             + "   - 涉及用药时，guidance 末尾必须提醒：具体药剂与用量按农药标签和当地规定执行。\n"
             + "9. 【说话方式】像一位懂行的老技术员在田间讲话：先给结论，再说怎么做，把道理讲清楚。"
             + "不要堆套话，不要一段段背教科书，不要为了显得全面而罗列与此问无关的条目。\n"
             + "10. 【出处不许编】sources 用来说明你这次回答**站在哪类知识上**，逐条一行。\n"
             + "   能写清具体文献或机构就写；想不出确切名称的，写知识领域与原理\n"
             + "   （如「植物营养学中微量元素缺素诊断的常规方法」「该作物栽培管理中水肥调控的通行做法」），\n"
             + "   让人知道结论的依据类型也是有用的。\n"
             + "   **绝不编造文件名、机构名或期号** —— 编出来的出处比没有出处更危险，\n"
             + "   它会让这条回答看起来可溯而实际无处可查。\n"
             + "\n输出 JSON 字段（全部必填）：\n"
             + "{\n"
             + "  \"answer\": \"针对问题的直接回答，两三句话讲清楚\",\n"
             + "  \"guidance\": \"具体怎么做，分条列出可执行的措施\",\n"
             + "  \"basis\": \"这样回答的依据；依据知识库时说明是哪一条，依据通用知识时说明是通用经验\",\n"
             + "  \"sources\": [\"本次回答所依据的知识来源或知识领域，逐条一行\"],\n"
             + "  \"disclaimer\": \"免责声明，说明本回答为辅助参考、不能替代现场诊断与农药标签\"\n"
             + "}\n";
    }

    /**
     * 农技问答的用户提示词。
     *
     * @param question 用户提问原文
     * @param entry    检索命中的知识库条目；证据不足时为 null
     */
    public String qaUserPrompt(String question, TzKnowledgeBase entry)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户提问】\n");
        sb.append(StringUtils.defaultIfBlank(question, "无")).append("\n");

        sb.append("\n【知识库参考】\n");
        if (entry == null)
        {
            sb.append("未匹配到知识库条目（检索结果不具区分度）。\n");
            sb.append("按系统提示词第 7、8 条处理：**用通用农艺知识正面回答**，")
                    .append("不要因为没收录就只说「请咨询农技员」；可以讲药剂类别、作用机理与施药时机，")
                    .append("但不要给出任何具体剂量数字，并在 basis 里说明这是通用经验而非本地实测结论。\n");
        } else {
            sb.append("名称：").append(entry.getDiseaseName()).append("\n");
            sb.append("特征性表现：").append(StringUtils.defaultIfBlank(entry.getKeyFeatures(), "无")).append("\n");
            sb.append("症状：").append(StringUtils.defaultIfBlank(entry.getSymptoms(), "无")).append("\n");
            sb.append("鉴别要点：").append(StringUtils.defaultIfBlank(entry.getDifferential(), "无")).append("\n");
            sb.append("发生条件：").append(StringUtils.defaultIfBlank(entry.getTriggerConditions(), "无")).append("\n");
            sb.append("防治措施：").append(StringUtils.defaultIfBlank(entry.getPrevention(), "无")).append("\n");
            sb.append("用药注意：").append(StringUtils.defaultIfBlank(entry.getMedicineNote(), "未收录")).append("\n");
            sb.append("安全说明：").append(StringUtils.defaultIfBlank(entry.getSafetyNote(), "无")).append("\n");
            sb.append("资料来源：").append(StringUtils.defaultIfBlank(entry.getSource(), "无")).append("\n");
        }

        sb.append("\n请按要求输出 JSON。再次强调：用药名称与用量只能来自上面的知识库参考。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 照片自动描述

    /**
     * 照片描述任务的系统提示词。
     *
     * <p>这条任务和诊断只差一个「要不要下结论」，所以必须把边界写死 ——
     * 模型看图之后极容易顺手写出病名和用药，而这段文字会被存成「症状描述」、
     * 拿去检索知识库、还会被写进巡田报告。一旦它里面带了结论，
     * 「描述 → 诊断」这个先后顺序就名存实亡了：用户看到的是结论，
     * 会以为系统已经诊断过；而报告里也会出现一段来路不明的结论。
     *
     * <p>所以第 7 条是硬约束：不写病名、不写成因、不写怎么治。
     *
     * <p>第 6 条反过来给一份**逐项清单**。这不是排版偏好，是实测换来的：
     * 最初只写「只描述看得见的内容」，模型交回来的是一句「叶片上有多个浅色斑点，
     * 分布在不同位置，无明显霉层、虫体」—— 正确，但空。同一张照片里明明有
     * 「中央色深、周围黄色晕圈、密布、近圆形隆起」，模型一个都没提。
     * 给它一张要逐项交代的清单，它才会去找这些特征。
     * 描述太笼统的代价不在描述本身：知识库检索是拿这段文字当查询串的，
     * 少了「黄色晕圈」这种高区分度的词，就可能匹配不到条目、直接掉进拒答。
     */
    public String describeSystemPrompt()
    {
        return SYSTEM_PROMPT
             + "\n本次任务：为一张柑橘巡田现场照片写「症状描述」，供后续检索与报告使用。\n"
             + "6. 按下面这份清单逐项看，把**看得到的**都写出来：\n"
             + "   ① 部位：叶片 / 果实 / 枝干；是叶面还是叶背；老叶还是新叶（新叶还是老叶关系到是不是缺素）；\n"
             + "   ② 数量与分布：零星还是密布，集中在叶缘、叶脉附近，还是布满整叶；\n"
             + "   ③ 单个病斑：形状（近圆形、不规则、条状）、大小（大约几毫米）、边界清晰还是模糊；\n"
             + "   ④ 颜色对比：中央与边缘各是什么颜色，周围有没有黄色晕圈 —— 这一条判断价值最高，"
             + "看得到就一定要写；\n"
             + "   ⑤ 立体形态（同样是关键，别漏）：病斑是平贴的、还是隆起/凸起/木栓化/粗糙；"
             + "中央有没有开裂、凹陷或呈火山口状；是叶片两面都隆起，还是只在一面突起、另一面凹陷；\n"
             + "   ⑥ 表面附着物：有没有霉层（黑色/灰色/白色）、粉状物、锈褐色斑、蜡丝或蛛丝；\n"
             + "   ⑦ 其他：虫体、虫粪、孔洞、弯曲虫道、叶片卷曲畸形的方向。\n"
             + "7. 【不要下结论】不要出现病名或虫名，不要推测成因，不要写防治方法或用药。"
             + "你负责客观描述，结论由后面的诊断环节给出。\n"
             + "8. 确实看不清的项略过即可，**但不要把看得见的也一并省掉** —— 描述越笼统，"
             + "后面的知识库检索越匹配不到条目。尤其是 ④ ⑤ 两条：很多病害的区别"
             + "（比如溃疡病与疮痂病）就差在「有没有黄色晕圈」「隆起在哪一面」上，"
             + "漏掉这两条，描述就失去了判断价值。同时不要为了把话说满而补「可能与某某有关」这类推测。\n"
             + "9. 用农技员向同事口头描述田间所见的口吻，连贯写两到四句话，不超过 250 字；"
             + "不要分点、不要小标题、不要把上面的序号抄进来；"
             + "句首不要用指示语（「这张照片里」「这柑橘叶片上」之类），直接从症状写起。\n"
             + "\n输出 JSON 字段（全部必填）：\n"
             + "{\n"
             + "  \"description\": \"症状描述正文，一段连贯的话\",\n"
             + "  \"observable\": true 或 false，照片是否清晰到足以作出上述描述（模糊、过暗、"
             + "拍的不是植物或叶片占画面太小，一律填 false）\n"
             + "}\n";
    }

    /**
     * 照片描述任务的用户提示词。
     *
     * @param cropType  作物类型
     * @param plantPart 用户已选的拍摄部位（可空）
     */
    public String describeUserPrompt(String cropType, String plantPart)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("【本次拍摄信息】\n");
        sb.append("作物：").append(StringUtils.defaultIfBlank(cropType, "柑橘")).append("\n");
        sb.append("部位：").append(plantPartText(plantPart)).append("\n");
        sb.append("\n请只描述这张照片里看得见的症状，按要求输出 JSON。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 字典转文字

    private String plantPartText(String code)
    {
        if ("1".equals(code)) return "叶片";
        if ("2".equals(code)) return "果实";
        if ("3".equals(code)) return "枝干";
        if ("4".equals(code)) return "根部";
        return "未指明";
    }

    private String severityText(String code)
    {
        if ("1".equals(code)) return "轻度";
        if ("2".equals(code)) return "中度";
        if ("3".equals(code)) return "重度";
        return "未评估";
    }

    private String riskText(String code)
    {
        if ("1".equals(code)) return "低风险";
        if ("2".equals(code)) return "中风险";
        if ("3".equals(code)) return "高风险";
        return "未评估";
    }
}
