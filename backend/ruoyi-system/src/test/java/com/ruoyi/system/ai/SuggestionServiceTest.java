package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzKnowledgeBaseService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 防治建议生成单测。
 *
 * 这组用例守的是产品最容易翻车的地方：**一份看起来很像回事、但用量是编出来的建议**。
 * 所以剂量校验用的是真实的 {@link DosageGuard} 而不是 mock —— 这里要验证的正是
 * 「校验器确实接在了建议链路上」，mock 掉就什么都没验证。
 *
 * @author tianzhen
 */
class SuggestionServiceTest
{
    private static final Long RECORD_ID = 1001L;
    private static final Long KNOWLEDGE_ID = 1L;

    private SuggestionService service;
    private ITzScoutingRecordService recordService;
    private ITzKnowledgeBaseService knowledgeService;
    private PromptBuilder promptBuilder;
    private LlmClient llmClient;
    private AiCallLogRecorder callLogRecorder;
    private AiProperties properties;

    @BeforeEach
    void setUp()
    {
        service = new SuggestionService();
        recordService = mock(ITzScoutingRecordService.class);
        knowledgeService = mock(ITzKnowledgeBaseService.class);
        promptBuilder = mock(PromptBuilder.class);
        llmClient = mock(LlmClient.class);
        callLogRecorder = mock(AiCallLogRecorder.class);

        properties = new AiProperties();
        properties.setDisclaimer("测试用免责声明");

        ReflectionTestUtils.setField(service, "scoutingRecordService", recordService);
        ReflectionTestUtils.setField(service, "knowledgeBaseService", knowledgeService);
        ReflectionTestUtils.setField(service, "promptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "dosageGuard", new DosageGuard());
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "properties", properties);

        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(diagnosedRecord());
        when(knowledgeService.selectTzKnowledgeBaseById(KNOWLEDGE_ID)).thenReturn(knowledgeEntry());
        when(promptBuilder.suggestionSystemPrompt()).thenReturn("system");
        when(promptBuilder.suggestionUserPrompt(any(), any())).thenReturn("user");
    }

    // ------------------------------------------------------------------ 前置校验

    @Test
    @DisplayName("记录未诊断时明确拒绝，不生成一份「针对未知问题」的建议")
    void refusesWhenNotDiagnosed()
    {
        TzScoutingRecord blank = diagnosedRecord();
        blank.setDiagnosisName(null);
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(blank);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.suggest(RECORD_ID, true));
        assertTrue(ex.getMessage().contains("尚未完成诊断"), "实际：" + ex.getMessage());
        verify(llmClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("记录不存在时明确报错")
    void refusesWhenRecordMissing()
    {
        when(recordService.selectTzScoutingRecordById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.suggest(999L, true));
    }

    // ------------------------------------------------------------------ 合规核心

    @Test
    @DisplayName("大模型编造的用量必须被拦下并替换，同时如实记录拦截")
    void blocksInventedDosageFromLlm()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"recognition\":\"柑橘溃疡病\",\"basis\":\"叶片有黄色晕圈\","
              + "\"prevention\":\"剪除病叶并集中烧毁\",\"medicine\":\"建议喷施 吡唑醚菌酯 2000倍液\","
              + "\"safety\":\"注意轮换用药\",\"disclaimer\":\"仅供参考\"}",
                "dashscope", "qwen-plus", 800L, "digest"));

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertTrue(result.isDosageGuardHit(), "2000倍液 未收录，必须触发拦截");
        assertFalse(result.getSuggestion().contains("2000倍液"),
                "编造的用量不该留在最终建议里，实际：" + result.getSuggestion());
        assertTrue(result.getSuggestion().contains("（用量待确认，请以农药标签为准）"),
                "应替换成可执行的指引，实际：" + result.getSuggestion());
        assertTrue(result.getRejectedDosages().contains("2000倍液"));
        assertNotNull(result.getGuardExplain());
        // 被拦的是用量，不是整段建议 —— 其余内容仍要交付给用户
        assertTrue(result.getSuggestion().contains("剪除病叶并集中烧毁"), "有效的农业措施不该被一起抹掉");
    }

    @Test
    @DisplayName("知识库里有的用量原样放行，不误伤")
    void keepsDosageThatExistsInKnowledgeBase()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"recognition\":\"柑橘溃疡病\",\"basis\":\"叶片有黄色晕圈\","
              + "\"prevention\":\"剪除病叶\",\"medicine\":\"可选 代森锰锌 800倍液\","
              + "\"safety\":\"注意轮换用药\",\"disclaimer\":\"仅供参考\"}",
                "dashscope", "qwen-plus", 700L, "digest"));

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertFalse(result.isDosageGuardHit(), "800倍液 已收录，不该拦");
        assertTrue(result.getSuggestion().contains("800倍液"));
        assertTrue(result.getAcceptedDosages().contains("800倍液"));
    }

    @Test
    @DisplayName("拦截结果必须回写到巡田记录，界面才看得到")
    void persistsDosageGuardFlag()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"recognition\":\"柑橘溃疡病\",\"basis\":\"x\",\"prevention\":\"y\","
              + "\"medicine\":\"建议 3000倍液\",\"safety\":\"z\",\"disclaimer\":\"d\"}",
                "dashscope", "qwen-plus", 100L, "digest"));

        service.suggest(RECORD_ID, true);

        org.mockito.ArgumentCaptor<TzScoutingRecord> captor =
                org.mockito.ArgumentCaptor.forClass(TzScoutingRecord.class);
        verify(recordService).updateTzScoutingRecord(captor.capture());
        TzScoutingRecord saved = captor.getValue();

        assertEquals(RECORD_ID, saved.getRecordId());
        assertEquals("1", saved.getDosageGuardHit());
        assertEquals(SuggestionService.SOURCE_LLM, saved.getSuggestionSource());
        assertNotNull(saved.getSuggestion());
    }

    // ------------------------------------------------------------------ 降级兜底

    @Test
    @DisplayName("未配置 API Key 时不发起调用，改用知识库原文组装")
    void degradesToKnowledgeBaseWhenNoApiKey()
    {
        when(llmClient.isAvailable()).thenReturn(false);

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertEquals(SuggestionService.SOURCE_KB, result.getSource());
        assertTrue(result.isDegraded());
        assertTrue(result.getDegradeReason().contains("API Key"), "实际：" + result.getDegradeReason());
        verify(llmClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("降级建议的内容逐字来自知识库，不存在自由发挥的空间")
    void degradedSuggestionComesFromKnowledgeBase()
    {
        when(llmClient.isAvailable()).thenReturn(false);

        String suggestion = service.suggest(RECORD_ID, true).getSuggestion();

        assertTrue(suggestion.contains("剪除病叶并集中烧毁"), "应包含知识库的防治措施");
        assertTrue(suggestion.contains("代森锰锌 800倍液"), "应包含知识库的用药说明");
        assertTrue(suggestion.contains("安全间隔期以标签为准"), "应包含知识库的安全说明");
        assertTrue(suggestion.contains("示例数据，来源待补充"), "应带上资料来源，建议要可溯");
        assertTrue(suggestion.contains("测试用免责声明"), "必须带免责声明");
        assertTrue(suggestion.contains("柑橘溃疡病"), "应包含诊断结论");
    }

    @Test
    @DisplayName("useLlm=false 时强制走知识库路径")
    void respectsUseLlmFlag()
    {
        SuggestionResult result = service.suggest(RECORD_ID, false);

        assertEquals(SuggestionService.SOURCE_KB, result.getSource());
        assertTrue(result.getDegradeReason().contains("指定不使用大模型"), "实际：" + result.getDegradeReason());
        verify(llmClient, never()).isAvailable();
    }

    @Test
    @DisplayName("大模型调用失败时降级，而不是把异常抛给用户")
    void degradesWhenLlmCallFails()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.fail(
                "HTTP 401 Unauthorized", "dashscope", "qwen-plus", 300L, "digest"));

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertEquals(SuggestionService.SOURCE_KB, result.getSource());
        assertTrue(result.getDegradeReason().contains("大模型调用失败"), "实际：" + result.getDegradeReason());
    }

    @Test
    @DisplayName("大模型返回不可解析内容时降级")
    void degradesWhenOutputUnparsable()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "这个问题我需要更多信息。", "dashscope", "qwen-plus", 300L, "digest"));

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertEquals(SuggestionService.SOURCE_KB, result.getSource());
        assertTrue(result.getDegradeReason().contains("无法解析"), "实际：" + result.getDegradeReason());
    }

    @Test
    @DisplayName("知识库没有对应条目时，兜底建议必须劝阻自行用药")
    void degradedWithoutEntryAdvisesConsultingTechnician()
    {
        TzScoutingRecord record = diagnosedRecord();
        record.setKnowledgeId(null);
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);
        when(knowledgeService.selectTzKnowledgeBaseByName("柑橘溃疡病")).thenReturn(null);
        when(llmClient.isAvailable()).thenReturn(false);

        String suggestion = service.suggest(RECORD_ID, true).getSuggestion();

        assertTrue(suggestion.contains("农技员"), "应引导去咨询农技员，实际：" + suggestion);
        assertTrue(suggestion.contains("未收录"), "用药部分应如实写未收录");
    }

    // ------------------------------------------------------------------ 条目定位

    @Test
    @DisplayName("优先使用诊断阶段绑定的条目，不再按病名重新检索")
    void prefersBoundKnowledgeId()
    {
        when(llmClient.isAvailable()).thenReturn(false);

        SuggestionResult result = service.suggest(RECORD_ID, true);

        assertEquals(KNOWLEDGE_ID, result.getKnowledgeId());
        assertEquals("柑橘溃疡病", result.getKnowledgeName());
        assertEquals("示例数据，来源待补充", result.getKnowledgeSource());
        verify(knowledgeService, never()).selectTzKnowledgeBaseByName(anyString());
    }

    @Test
    @DisplayName("诊断未绑定条目时退回按病名精确匹配")
    void fallsBackToNameLookup()
    {
        TzScoutingRecord record = diagnosedRecord();
        record.setKnowledgeId(null);
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);
        when(knowledgeService.selectTzKnowledgeBaseByName("柑橘溃疡病")).thenReturn(knowledgeEntry());
        when(llmClient.isAvailable()).thenReturn(false);

        assertEquals(KNOWLEDGE_ID, service.suggest(RECORD_ID, true).getKnowledgeId());
        verify(knowledgeService).selectTzKnowledgeBaseByName("柑橘溃疡病");
    }

    // ------------------------------------------------------------------ 只读查询

    @Test
    @DisplayName("只读查询不重新生成，也不调用大模型")
    void selectResultIsReadOnly()
    {
        TzScoutingRecord record = diagnosedRecord();
        record.setSuggestion("【识别结论】\n柑橘溃疡病");
        record.setSuggestionSource(SuggestionService.SOURCE_KB);
        record.setDosageGuardHit("1");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record);

        SuggestionResult result = service.selectResult(RECORD_ID);

        assertEquals("【识别结论】\n柑橘溃疡病", result.getSuggestion());
        assertTrue(result.isDosageGuardHit());
        verify(llmClient, never()).isAvailable();
        verify(llmClient, never()).chat(any(), any());
    }

    // ------------------------------------------------------------------ 夹具

    private TzScoutingRecord diagnosedRecord()
    {
        TzScoutingRecord r = new TzScoutingRecord();
        r.setRecordId(RECORD_ID);
        r.setPlotId(10L);
        r.setPlotName("东坡示范园");
        r.setCropType("柑橘");
        r.setPlantPart("1");
        r.setSeverity("2");
        r.setSymptomText("叶片出现近圆形病斑，周围有黄色晕圈");
        r.setDiagnosisName("柑橘溃疡病");
        r.setConfidence(new BigDecimal("85"));
        r.setRiskLevel("2");
        r.setDiagnosisBasis("叶片近圆形病斑，中央木栓化隆起");
        r.setDiagnosisSource("llm");
        r.setKnowledgeId(KNOWLEDGE_ID);
        r.setStatus("1");
        return r;
    }

    private TzKnowledgeBase knowledgeEntry()
    {
        TzKnowledgeBase e = new TzKnowledgeBase();
        e.setKnowledgeId(KNOWLEDGE_ID);
        e.setCropType("柑橘");
        e.setDiseaseName("柑橘溃疡病");
        e.setPrevention("剪除病叶并集中烧毁，加强通风透光");
        e.setMedicineNote("可选 代森锰锌 800倍液");
        e.setSafetyNote("安全间隔期以标签为准，注意轮换用药");
        e.setSource("示例数据，来源待补充");
        e.setStatus("0");
        return e;
    }
}
