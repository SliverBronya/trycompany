package com.ruoyi.system.ai;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzFollowUpTask;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzFollowUpTaskService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田报告生成。
 *
 * PRD 里「报告靠手写」是农技员最痛的环节，所以这里要做的不只是拼一段文字，
 * 而是把闭环合上：**报告生成的同时，按风险等级自动排一次复查任务**。
 * 没有这一步，「防治效果无留痕、补贴申报无台账」两个问题都还在。
 *
 * 两条实现上的取舍：
 *   - 已经在记录上的建议直接复用，不重新生成。同一份报告里翻两次水，用户看到的内容会前后不一致。
 *   - 报告正文以 HTML 落库，但其中每一处动态内容都过 {@link HtmlText#escape}。
 *
 * @author tianzhen
 */
@Service
public class ReportService
{
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 报告编号前缀 */
    private static final String REPORT_NO_PREFIX = "TZ";

    /** 复查任务标题长度上限，与 tz_follow_up_task.task_title 的字段长度一致 */
    private static final int TASK_TITLE_MAX = 200;

    /** 各风险等级的复查间隔（天）：风险越高，越要尽快回头看效果 */
    private static final int DUE_DAYS_HIGH = 3;
    private static final int DUE_DAYS_MIDDLE = 7;
    private static final int DUE_DAYS_LOW = 14;

    @Autowired
    private ITzScoutingRecordService scoutingRecordService;

    @Autowired
    private ITzFollowUpTaskService followUpTaskService;

    @Autowired
    private SuggestionService suggestionService;

    @Autowired
    private AiProperties properties;

    /**
     * 生成巡田报告，并自动安排复查任务。
     *
     * @param recordId 巡田记录ID
     * @param useLlm   记录上还没有防治建议时，是否允许调用大模型来生成
     * @return 报告结果
     */
    @Transactional
    public ReportResult generate(Long recordId, boolean useLlm)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }
        if (StringUtils.isBlank(record.getDiagnosisName()))
        {
            throw new ServiceException("该巡田记录尚未完成诊断，请先执行 AI 诊断，再生成巡田报告。");
        }

        ReportResult result = new ReportResult();
        result.setRecordId(recordId);
        result.setDisclaimer(properties.getDisclaimer());

        // ---- 建议：有就复用，没有才生成 ----
        if (StringUtils.isNotBlank(record.getSuggestion()))
        {
            result.setSuggestionReused(true);
            result.setSuggestionSource(record.getSuggestionSource());
            result.setDosageGuardHit("1".equals(record.getDosageGuardHit()));
        }
        else
        {
            SuggestionResult suggestion = suggestionService.suggest(recordId, useLlm);
            result.setSuggestionReused(false);
            result.setSuggestionSource(suggestion.getSource());
            result.setDosageGuardHit(suggestion.isDosageGuardHit());
            // 建议已落库，重新取一次记录，让报告正文用的是刚写进去的那一版
            record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        }

        // ---- 复查任务：先安排，再写进报告正文 ----
        TzFollowUpTask task = ensureFollowUpTask(record, result);

        // ---- 报告正文 ----
        Date now = new Date();
        String html = buildReportHtml(record, task, now);
        result.setReportText(html);
        result.setReportTime(now);

        TzScoutingRecord update = new TzScoutingRecord();
        update.setRecordId(recordId);
        update.setReportText(html);
        update.setReportTime(now);
        // 只在「待诊断 / 已诊断」阶段推进到「已生成报告」。
        // 状态已经走到「待复查 / 已复查」的记录重新生成报告时不能被打回来，
        // 否则复查进度会凭空消失。
        if (record.getStatus() == null || "0".equals(record.getStatus()) || "1".equals(record.getStatus()))
        {
            update.setStatus("2");
        }
        scoutingRecordService.updateTzScoutingRecord(update);

        return result;
    }

    /**
     * 读取已生成的报告，不重新生成。
     */
    public ReportResult selectReport(Long recordId)
    {
        TzScoutingRecord record = scoutingRecordService.selectTzScoutingRecordById(recordId);
        if (record == null)
        {
            throw new ServiceException("巡田记录不存在：" + recordId);
        }

        ReportResult result = new ReportResult();
        result.setRecordId(recordId);
        result.setReportText(record.getReportText());
        result.setReportTime(record.getReportTime());
        result.setSuggestionSource(record.getSuggestionSource());
        result.setDosageGuardHit("1".equals(record.getDosageGuardHit()));
        result.setSuggestionReused(true);
        result.setDisclaimer(properties.getDisclaimer());

        TzFollowUpTask query = new TzFollowUpTask();
        query.setRecordId(recordId);
        List<TzFollowUpTask> tasks = followUpTaskService.selectTzFollowUpTaskList(query);
        if (!tasks.isEmpty())
        {
            TzFollowUpTask task = tasks.get(0);
            result.setFollowUpTaskId(task.getTaskId());
            result.setFollowUpTaskTitle(task.getTaskTitle());
            result.setFollowUpDueDate(task.getDueDate());
        }
        return result;
    }

    // ------------------------------------------------------------------ 复查任务

    /**
     * 确保这条巡田记录有一个未完成的复查任务，并把结果写进 result。
     *
     * 已经是「已复查」的历史任务不算数，会另排一次 —— 复查之后病情反复是常事，
     * 不能因为上个月查过一次就不再跟踪。
     */
    private TzFollowUpTask ensureFollowUpTask(TzScoutingRecord record, ReportResult result)
    {
        TzFollowUpTask query = new TzFollowUpTask();
        query.setRecordId(record.getRecordId());
        List<TzFollowUpTask> existing = followUpTaskService.selectTzFollowUpTaskList(query);
        for (TzFollowUpTask task : existing)
        {
            // status 0 待复查 / 2 已逾期，都属于「还没做」
            if ("0".equals(task.getStatus()) || "2".equals(task.getStatus()))
            {
                log.info("巡田记录 {} 已存在未完成的复查任务 {}，本次不再重复创建",
                        record.getRecordId(), task.getTaskId());
                fillTask(result, task, false);
                return task;
            }
        }

        TzFollowUpTask task = new TzFollowUpTask();
        task.setRecordId(record.getRecordId());
        task.setPlotId(record.getPlotId());
        task.setTaskTitle(buildTaskTitle(record));
        task.setDueDate(dueDate(record.getRiskLevel()));
        task.setStatus("0");
        task.setNote(buildTaskNote(record));
        task.setCreateBy(record.getCreateBy());
        followUpTaskService.insertTzFollowUpTask(task);
        fillTask(result, task, true);
        return task;
    }

    /**
     * 「本次是否新建了任务」只能在这里判：新建与复用的任务状态都是「待复查」，
     * 从任务对象本身推不出来。
     */
    private void fillTask(ReportResult result, TzFollowUpTask task, boolean created)
    {
        result.setFollowUpTaskId(task.getTaskId());
        result.setFollowUpTaskTitle(task.getTaskTitle());
        result.setFollowUpDueDate(task.getDueDate());
        result.setFollowUpTaskCreated(created);
    }

    private String buildTaskTitle(TzScoutingRecord record)
    {
        String plot = StringUtils.defaultIfBlank(record.getPlotName(), "未指定地块");
        String title = "复查「" + plot + "」" + record.getDiagnosisName() + "防治效果";
        return title.length() > TASK_TITLE_MAX ? title.substring(0, TASK_TITLE_MAX) : title;
    }

    private String buildTaskNote(TzScoutingRecord record)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("由巡田报告自动生成。");
        if (StringUtils.isNotBlank(record.getRiskLevel()))
        {
            sb.append("风险等级：").append(riskText(record.getRiskLevel())).append("。");
        }
        sb.append("复查时请重点确认：病斑是否停止扩展、新梢是否再有发病、用药后有无药害表现。");
        String note = sb.toString();
        return note.length() > 500 ? note.substring(0, 500) : note;
    }

    /**
     * 按风险等级推算复查日期：高风险 3 天、中风险 7 天、低风险 14 天。
     */
    private Date dueDate(String riskLevel)
    {
        int days = DUE_DAYS_MIDDLE;
        if ("3".equals(riskLevel))
        {
            days = DUE_DAYS_HIGH;
        }
        else if ("1".equals(riskLevel))
        {
            days = DUE_DAYS_LOW;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    // ------------------------------------------------------------------ 报告正文

    /**
     * 拼报告 HTML。
     *
     * 这里每一处动态内容都必须过 HtmlText：症状描述是农技员手填的，
     * 建议与依据里混着大模型的输出，两个来源都不可信。
     */
    private String buildReportHtml(TzScoutingRecord record, TzFollowUpTask task, Date now)
    {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd");

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"tz-report\">");

        sb.append("<h2>柑橘巡田报告</h2>");
        sb.append("<p class=\"tz-report-meta\">报告编号 ")
          .append(HtmlText.escape(REPORT_NO_PREFIX + "-" + record.getRecordId()))
          .append("　生成时间 ").append(HtmlText.escape(df.format(now)))
          .append("　生成人 ").append(HtmlText.escape(StringUtils.defaultIfBlank(record.getCreateBy(), "未记录")))
          .append("</p>");

        // 一、基本信息
        sb.append("<h3>一、巡田基本信息</h3>");
        sb.append("<table class=\"tz-report-table\">");
        row(sb, "地块", StringUtils.defaultIfBlank(record.getPlotName(), "未指定"));
        row(sb, "作物", StringUtils.defaultIfBlank(record.getCropType(), "柑橘"));
        row(sb, "巡田时间", record.getScoutTime() == null ? "未记录" : df.format(record.getScoutTime()));
        row(sb, "发生部位", plantPartText(record.getPlantPart()));
        row(sb, "严重程度", severityText(record.getSeverity()));
        row(sb, "症状描述", StringUtils.defaultIfBlank(record.getSymptomText(), "（未填写）"));
        sb.append("</table>");

        // 二、诊断结论
        sb.append("<h3>二、诊断结论</h3>");
        sb.append("<table class=\"tz-report-table\">");
        row(sb, "诊断结论", record.getDiagnosisName());
        row(sb, "风险等级", riskText(record.getRiskLevel()));
        row(sb, "置信度", record.getConfidence() == null
                ? "未给出" : record.getConfidence().stripTrailingZeros().toPlainString() + "%");
        row(sb, "结论来源", diagnosisSourceText(record.getDiagnosisSource()));
        row(sb, "判断依据", StringUtils.defaultIfBlank(record.getDiagnosisBasis(), "未给出"));
        sb.append("</table>");
        if ("fallback".equals(record.getDiagnosisSource()) || "preset".equals(record.getDiagnosisSource()))
        {
            sb.append("<p class=\"tz-report-warn\">本次结论未经过大模型图像识别，"
                    + "由知识库检索或预置样张给出，请结合田间实际复核后采用。</p>");
        }

        // 三、防治建议
        sb.append("<h3>三、防治建议</h3>");
        if (StringUtils.isBlank(record.getSuggestion()))
        {
            sb.append("<p class=\"tz-report-warn\">本次未生成防治建议。</p>");
        }
        else
        {
            sb.append("<div class=\"tz-report-pre\">")
              .append(HtmlText.pre(record.getSuggestion()))
              .append("</div>");
        }
        if ("1".equals(record.getDosageGuardHit()))
        {
            sb.append("<p class=\"tz-report-warn\">本建议中的部分农药用量未在植保知识库中收录，"
                    + "已按合规要求拦截并替换为指引。具体用量请以农药产品标签为准。</p>");
        }

        // 四、复查安排
        sb.append("<h3>四、复查安排</h3>");
        sb.append("<table class=\"tz-report-table\">");
        row(sb, "复查事项", task.getTaskTitle());
        row(sb, "复查期限", task.getDueDate() == null ? "未安排" : day.format(task.getDueDate()));
        row(sb, "复查要求", StringUtils.defaultIfBlank(task.getNote(), "—"));
        sb.append("</table>");

        // 五、溯源与免责
        sb.append("<h3>五、数据溯源与免责声明</h3>");
        sb.append("<table class=\"tz-report-table\">");
        row(sb, "建议来源", suggestionSourceText(record.getSuggestionSource()));
        row(sb, "知识库条目", knowledgeText(record));
        sb.append("</table>");
        sb.append("<p class=\"tz-report-disclaimer\">")
          .append(HtmlText.escape(properties.getDisclaimer())).append("</p>");

        sb.append("</div>");
        return sb.toString();
    }

    private void row(StringBuilder sb, String label, String value)
    {
        sb.append("<tr><th>").append(HtmlText.escape(label)).append("</th><td>")
          .append(HtmlText.nl2br(StringUtils.defaultIfBlank(value, "—")))
          .append("</td></tr>");
    }

    private String knowledgeText(TzScoutingRecord record)
    {
        if (record.getKnowledgeId() == null)
        {
            return "未关联知识库条目";
        }
        return "已关联知识库条目 #" + record.getKnowledgeId() + "（可在植保知识库中查看原文与出处）";
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

    private String diagnosisSourceText(String source)
    {
        if ("preset".equals(source)) return "预置样张映射（演示保底路径）";
        if ("llm".equals(source)) return "多模态大模型初诊";
        if ("fallback".equals(source)) return "知识库关键词检索降级";
        if ("manual".equals(source)) return "人工录入";
        return "未记录";
    }

    private String suggestionSourceText(String source)
    {
        if (SuggestionService.SOURCE_LLM.equals(source)) return "大模型生成（用药用量经知识库校验）";
        if (SuggestionService.SOURCE_KB.equals(source)) return "知识库条目原文组装";
        return "未记录";
    }
}
