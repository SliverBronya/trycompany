package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 剂量校验结果。
 *
 * @author tianzhen
 */
public class DosageGuardResult
{
    /** 校验后的文本（被拦截的用量表述已替换为提示语） */
    private final String text;

    /** 被拦截的原始表述 */
    private final List<String> rejected;

    /** 原文中通过校验的用量表述，便于前端展示「这几处有知识库出处」 */
    private final List<String> accepted;

    public DosageGuardResult(String text, List<String> rejected, List<String> accepted)
    {
        this.text = text;
        this.rejected = rejected == null ? Collections.<String>emptyList() : rejected;
        this.accepted = accepted == null ? Collections.<String>emptyList() : accepted;
    }

    public String getText()
    {
        return text;
    }

    public List<String> getRejected()
    {
        return rejected;
    }

    public List<String> getAccepted()
    {
        return accepted;
    }

    /** 是否发生了拦截 */
    public boolean isHit()
    {
        return !rejected.isEmpty();
    }

    /**
     * 拦截提示语。只说「拦了几处」，不复述被拦下的数字本身。
     *
     * <p>这里刻意不把 rejected 的内容拼进来，因为这段文字会被追加进正文，而正文是
     * 直接给农户看、并且会被巡田报告整段引用的。一个未经知识库核实的用量，只要出现
     * 在正文里，哪怕前面写着「已拦截」，农户照样能照着打下去 —— 「已拦截」这个标签
     * 拦不住任何人，反而因为写在最后、语气肯定，读起来更像是在确认这个数字。
     *
     * <p>要查究竟拦掉了哪几条，用 {@link #getRejected()}：它会随响应返回、
     * 在界面上以独立的红色标签列出、并写入调用日志，是一条与正文分开的审计通道。
     */
    public String explain()
    {
        if (rejected.isEmpty())
        {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : rejected)
        {
            if (!cleaned.contains(item))
            {
                cleaned.add(item);
            }
        }
        return "本次有 " + cleaned.size() + " 处用量表述未在植保知识库中收录，已按合规要求移除，"
                + "正文中相应位置已改为提示语。具体用量请以农药产品标签为准，或咨询当地农技员。";
    }
}
