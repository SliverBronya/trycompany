package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzFollowUpTask;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzFollowUpTaskService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 巡田报告生成单测。
 *
 * 报告是这份产品的交付物，也是唯一一处把用户输入直接拼进 HTML 的地方，
 * 所以这里有两组必须守住的用例：**闭环是否合上**（报告生成即排复查、不重复排）
 * 与 **注入是否被挡住**（症状描述里的脚本标签不能落进报告）。
 *
 * @author tianzhen
 */
class ReportServiceTest
{
    private static final Long RECORD_ID = 1001L;

    private ReportService service;
    private ITzScoutingRecordService recordService;
    private ITzFollowUpTaskService taskService;
    private SuggestionService suggestionService;
    private AiProperties properties;

    @BeforeEach
    void setUp()
    {
        service = new ReportService();
        recordService = mock(ITzScoutingRecordService.class);
        taskService = mock(ITzFollowUpTaskService.class);
        suggestionService = mock(SuggestionService.class);

        properties = new AiProperties();
        properties.setDisclaimer("测试用免责声明");

        ReflectionTestUtils.setField(service, "scoutingRecordService", recordService);
        ReflectionTestUtils.setField(service, "followUpTaskService", taskService);
        ReflectionTestUtils.setField(service, "suggestionService", suggestionService);
        ReflectionTestUtils.setField(service, "properties", properties);

        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(reportedReadyRecord());
        when(taskService.selectTzFollowUpTaskList(any())).thenReturn(new ArrayList<TzFollowUpTask>());
        // 模拟 MyBatis 的 useGeneratedKeys：真实运行时主键是插入后回填的，mock 得自己补上
        when(taskService.insertTzFollowUpTask(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, TzFollowUpTask.class).setTaskId(555L);
            return 1;
        });
    }

    // ------------------------------------------------------------------ 前置校验

    @Test
    @DisplayName("未诊断的记录不生成报告，先让用户把诊断做完")
    void refusesWhenNotDiagnosed()
    {
        TzScoutingRecord blank = reportedReadyRecord();
        blank.setDiagnosisName(null);
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(blank);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.generate(RECORD_ID, true));
        assertTrue(ex.getMessage().contains("尚未完成诊断"), "实际：" + ex.getMessage());
        verify(taskService, never()).insertTzFollowUpTask(any());
    }

    @Test
    @DisplayName("记录不存在时明确报错")
    void refusesWhenRecordMissing()
    {
        when(recordService.selectTzScoutingRecordById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.generate(999L, true));
    }

    // ------------------------------------------------------------------ 建议复用

    @Test
    @DisplayName("记录上已有建议时直接复用，不重新生成 —— 同一份报告不能前后不一致")
    void reusesExistingSuggestion()
    {
        ReportResult result = service.generate(RECORD_ID, true);

        assertTrue(result.isSuggestionReused());
        verify(suggestionService, never()).suggest(anyLong(), anyBoolean());
        assertTrue(result.getReportText().contains("剪除病叶并集中烧毁"), "报告应包含已有建议");
    }

    @Test
    @DisplayName("记录上还没有建议时才去生成，并取回写入后的最新记录")
    void generatesSuggestionWhenMissing()
    {
        TzScoutingRecord withoutSuggestion = reportedReadyRecord();
        withoutSuggestion.setSuggestion(null);
        TzScoutingRecord withSuggestion = reportedReadyRecord();
        when(recordService.selectTzScoutingRecordById(RECORD_ID))
                .thenReturn(withoutSuggestion, withSuggestion);

        SuggestionResult generated = new SuggestionResult();
        generated.setSource(SuggestionService.SOURCE_KB);
        generated.setDosageGuardHit(true);
        when(suggestionService.suggest(RECORD_ID, true)).thenReturn(generated);

        ReportResult result = service.generate(RECORD_ID, true);

        assertFalse(result.isSuggestionReused());
        assertEquals(SuggestionService.SOURCE_KB, result.getSuggestionSource());
        assertTrue(result.isDosageGuardHit(), "拦截状态应传递到报告结果");
        verify(suggestionService).suggest(RECORD_ID, true);
    }

    @Test
    @DisplayName("剂量被拦截过时，报告正文要明确警示，不能静默")
    void warnsWhenDosageWasBlocked()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setDosageGuardHit("1");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        assertTrue(service.generate(RECORD_ID, true).getReportText().contains("已按合规要求拦截"),
                "报告里必须留下拦截痕迹");
    }

    // ------------------------------------------------------------------ 闭环：复查任务

    @Test
    @DisplayName("生成报告即自动安排复查任务，闭环才算合上")
    void createsFollowUpTaskOnGenerate()
    {
        ReportResult result = service.generate(RECORD_ID, true);

        assertTrue(result.isFollowUpTaskCreated());
        assertNotNull(result.getFollowUpTaskId());
        assertNotNull(result.getFollowUpDueDate());
        assertTrue(result.getReportText().contains(result.getFollowUpTaskTitle()),
                "报告正文里应写清复查事项");

        org.mockito.ArgumentCaptor<TzFollowUpTask> captor =
                org.mockito.ArgumentCaptor.forClass(TzFollowUpTask.class);
        verify(taskService).insertTzFollowUpTask(captor.capture());
        TzFollowUpTask task = captor.getValue();

        assertEquals(RECORD_ID, task.getRecordId());
        assertEquals(Long.valueOf(10L), task.getPlotId());
        assertEquals("0", task.getStatus(), "新任务应是待复查");
        assertTrue(task.getTaskTitle().contains("东坡示范园"), "标题应带地块名，实际：" + task.getTaskTitle());
        assertTrue(task.getTaskTitle().contains("柑橘溃疡病"));
    }

    @Test
    @DisplayName("复查期限按风险等级排：高 3 天、中 7 天、低 14 天")
    void dueDateFollowsRiskLevel()
    {
        assertEquals(3L, dueDaysFor("3"), "高风险要尽快回头看");
        assertEquals(7L, dueDaysFor("2"));
        assertEquals(14L, dueDaysFor("1"));
        assertEquals(7L, dueDaysFor(null), "未评估风险时按中风险处理");
    }

    @Test
    @DisplayName("已有未完成的复查任务时不再重复创建，避免同一件事排两遍")
    void doesNotDuplicateOpenTask()
    {
        TzFollowUpTask existing = new TzFollowUpTask();
        existing.setTaskId(77L);
        existing.setRecordId(RECORD_ID);
        existing.setTaskTitle("复查「东坡示范园」柑橘溃疡病防治效果");
        existing.setStatus("0");
        existing.setDueDate(new Date());
        when(taskService.selectTzFollowUpTaskList(any()))
                .thenReturn(Collections.singletonList(existing));

        ReportResult result = service.generate(RECORD_ID, true);

        assertFalse(result.isFollowUpTaskCreated());
        assertEquals(Long.valueOf(77L), result.getFollowUpTaskId(), "应复用已有任务");
        verify(taskService, never()).insertTzFollowUpTask(any());
    }

    @Test
    @DisplayName("已逾期的任务仍算未完成，也不该重复创建")
    void treatsOverdueTaskAsOpen()
    {
        TzFollowUpTask overdue = new TzFollowUpTask();
        overdue.setTaskId(88L);
        overdue.setStatus("2");
        when(taskService.selectTzFollowUpTaskList(any())).thenReturn(Collections.singletonList(overdue));

        assertFalse(service.generate(RECORD_ID, true).isFollowUpTaskCreated());
        verify(taskService, never()).insertTzFollowUpTask(any());
    }

    @Test
    @DisplayName("上一条任务已复查完毕时另排一次 —— 病情反复是常事，不能不再跟踪")
    void schedulesAgainAfterPreviousTaskDone()
    {
        TzFollowUpTask done = new TzFollowUpTask();
        done.setTaskId(99L);
        done.setStatus("1");
        when(taskService.selectTzFollowUpTaskList(any())).thenReturn(Collections.singletonList(done));

        assertTrue(service.generate(RECORD_ID, true).isFollowUpTaskCreated(),
                "已完成的历史任务不构成「已经排过」");
        verify(taskService).insertTzFollowUpTask(any());
    }

    @Test
    @DisplayName("任务标题超长时截断，不能撑爆字段")
    void truncatesOverlongTaskTitle()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setPlotName(repeat("东", 300));
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        service.generate(RECORD_ID, true);

        org.mockito.ArgumentCaptor<TzFollowUpTask> captor =
                org.mockito.ArgumentCaptor.forClass(TzFollowUpTask.class);
        verify(taskService).insertTzFollowUpTask(captor.capture());
        assertTrue(captor.getValue().getTaskTitle().length() <= 200,
                "实际长度：" + captor.getValue().getTaskTitle().length());
    }

    // ------------------------------------------------------------------ 注入防护

    @Test
    @DisplayName("症状描述里的脚本标签不能落进报告 —— 报告是 v-html 渲染的")
    void escapesScriptInSymptomText()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setSymptomText("<script>alert('xss')</script>叶片有黄斑");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        String html = service.generate(RECORD_ID, true).getReportText();

        assertFalse(html.contains("<script>"), "报告里出现了可执行脚本，实际片段：" + snippetAround(html, "script"));
        assertTrue(html.contains("&lt;script&gt;"), "应转义后原样展示");
        assertTrue(html.contains("叶片有黄斑"), "正常文字仍应保留");
    }

    @Test
    @DisplayName("大模型输出里的标记同样要被转义，模型输出也是不可信输入")
    void escapesMarkupFromSuggestionText()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setSuggestion("<img src=x onerror=alert(1)>【识别结论】\n柑橘溃疡病");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        String html = service.generate(RECORD_ID, true).getReportText();

        assertFalse(html.contains("<img"), "实际片段：" + snippetAround(html, "img"));
        assertTrue(html.contains("&lt;img"));
    }

    // ------------------------------------------------------------------ 状态机

    @Test
    @DisplayName("首次生成报告时把记录推进到「已生成报告」")
    void advancesStatusToReported()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setStatus("1");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        service.generate(RECORD_ID, true);

        assertEquals("2", savedRecord().getStatus());
    }

    @Test
    @DisplayName("已走到复查阶段的记录重新生成报告时状态不被回退")
    void doesNotRegressStatusFromFollowUpStage()
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setStatus("4");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        service.generate(RECORD_ID, true);

        assertNull(savedRecord().getStatus(), "状态不该被覆盖，否则复查进度会凭空消失");
    }

    // ------------------------------------------------------------------ 只读查询

    @Test
    @DisplayName("只读查询直接返回已存正文，不重新生成")
    void selectReportIsReadOnly()
    {
        ReportResult result = service.selectReport(RECORD_ID);

        assertEquals("已生成的报告正文", result.getReportText());
        verify(suggestionService, never()).suggest(anyLong(), anyBoolean());
        verify(taskService, never()).insertTzFollowUpTask(any());
    }

    // ------------------------------------------------------------------ 夹具与工具

    private TzScoutingRecord savedRecord()
    {
        org.mockito.ArgumentCaptor<TzScoutingRecord> captor =
                org.mockito.ArgumentCaptor.forClass(TzScoutingRecord.class);
        verify(recordService).updateTzScoutingRecord(captor.capture());
        return captor.getValue();
    }

    private long dueDaysFor(String riskLevel)
    {
        TzScoutingRecord record = reportedReadyRecord();
        record.setRiskLevel(riskLevel);
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        ReportResult result = service.generate(RECORD_ID, true);
        LocalDate due = result.getFollowUpDueDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(LocalDate.now(), due);
    }

    private String snippetAround(String html, String needle)
    {
        int i = html.indexOf(needle);
        return i < 0 ? "（未出现）" : html.substring(Math.max(0, i - 40), Math.min(html.length(), i + 40));
    }

    private String repeat(String s, int times)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++)
        {
            sb.append(s);
        }
        return sb.toString();
    }

    private TzScoutingRecord reportedReadyRecord()
    {
        TzScoutingRecord r = new TzScoutingRecord();
        r.setRecordId(RECORD_ID);
        r.setPlotId(10L);
        r.setPlotName("东坡示范园");
        r.setCropType("柑橘");
        r.setScoutTime(new Date());
        r.setPlantPart("1");
        r.setSeverity("2");
        r.setSymptomText("叶片出现近圆形病斑，周围有黄色晕圈");
        r.setDiagnosisName("柑橘溃疡病");
        r.setConfidence(new BigDecimal("85"));
        r.setRiskLevel("2");
        r.setDiagnosisBasis("叶片近圆形病斑，中央木栓化隆起");
        r.setDiagnosisSource("llm");
        r.setKnowledgeId(1L);
        r.setSuggestion("【识别结论】\n柑橘溃疡病\n\n【防治建议】\n剪除病叶并集中烧毁");
        r.setSuggestionSource(SuggestionService.SOURCE_KB);
        r.setDosageGuardHit("0");
        r.setReportText("已生成的报告正文");
        r.setReportTime(new Date());
        r.setStatus("1");
        r.setCreateBy("admin");
        return r;
    }
}
