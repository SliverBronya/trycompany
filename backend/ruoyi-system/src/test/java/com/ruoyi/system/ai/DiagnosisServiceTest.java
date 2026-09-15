package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzImagePreset;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzScoutingRecord;
import com.ruoyi.system.service.ITzKnowledgeBaseService;
import com.ruoyi.system.service.ITzScoutingRecordService;

/**
 * 诊断编排单测 —— 三级降级链路。
 *
 * 这组用例覆盖的是产品的核心主张：「配了 key 就用真 AI，没配或调用失败也照样能用，
 * 而且一定告诉用户这次结论是怎么来的」。四条分支都必须有测试：
 *
 *   预置命中 → 大模型成功 → 大模型失败/低置信度降级 → 什么都没有时如实报错
 *
 * 最后一条尤其重要：宁可明确说「给不出结论」，也不能编一个。
 *
 * @author tianzhen
 */
class DiagnosisServiceTest
{
    private static final Long RECORD_ID = 1001L;

    private DiagnosisService service;
    private ITzScoutingRecordService recordService;
    private ITzKnowledgeBaseService knowledgeService;
    private PresetDiagnosisResolver presetResolver;
    private RagService ragService;
    private PromptBuilder promptBuilder;
    private LlmClient llmClient;
    private AiCallLogRecorder callLogRecorder;
    private AiProperties properties;

    @BeforeEach
    void setUp()
    {
        service = new DiagnosisService();
        recordService = mock(ITzScoutingRecordService.class);
        knowledgeService = mock(ITzKnowledgeBaseService.class);
        presetResolver = mock(PresetDiagnosisResolver.class);
        ragService = mock(RagService.class);
        promptBuilder = mock(PromptBuilder.class);
        llmClient = mock(LlmClient.class);
        callLogRecorder = mock(AiCallLogRecorder.class);

        properties = new AiProperties();
        properties.setRagTopN(3);
        properties.setMinConfidence(60D);
        properties.setDisclaimer("测试用免责声明");

        ReflectionTestUtils.setField(service, "scoutingRecordService", recordService);
        ReflectionTestUtils.setField(service, "knowledgeBaseService", knowledgeService);
        ReflectionTestUtils.setField(service, "presetResolver", presetResolver);
        ReflectionTestUtils.setField(service, "ragService", ragService);
        ReflectionTestUtils.setField(service, "promptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "properties", properties);

        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(record());
        when(promptBuilder.diagnosisSystemPrompt()).thenReturn("system");
        when(promptBuilder.diagnosisUserPrompt(any(), any(), any(), anyBoolean())).thenReturn("user");
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(new ArrayList<KnowledgeHit>());
        when(presetResolver.resolve(any())).thenReturn(null);
        // 让 mock 的采信判断与真实规则保持一致：夹具每次只造一条命中，
        // 对应真实实现里「只有一条命中，看绝对分是否过最低线」那一支。
        when(ragService.isConfident(any())).thenAnswer(invocation -> {
            List<KnowledgeHit> hits = invocation.getArgument(0);
            return hits != null && !hits.isEmpty() && hits.get(0).getScore() >= 3D;
        });
    }

    // ------------------------------------------------------------------ 1 预置保底

    @Test
    @DisplayName("命中预置映射时直接返回标准结论，且完全不调用大模型")
    void presetHitShortCircuitsBeforeLlm()
    {
        TzImagePreset preset = new TzImagePreset();
        preset.setDiagnosisName("柑橘溃疡病");
        preset.setConfidence(new BigDecimal("92.5"));
        preset.setRiskLevel("2");
        preset.setDiagnosisBasis("预置依据");
        preset.setKnowledgeId(7L);
        when(presetResolver.resolve(any())).thenReturn(preset);

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("柑橘溃疡病", result.getDiagnosisName());
        assertEquals(0, new BigDecimal("92.5").compareTo(result.getConfidence()));
        assertEquals("preset", result.getSource());
        assertFalse(result.isDegraded(), "预置路径是正常路径，不该标记为降级");
        assertNotNull(result.getSourceText(), "必须能说明结论来自哪条路径");
        assertEquals(Long.valueOf(7L), result.getKnowledgeId(), "应带上知识库出处");
        verify(llmClient, never()).isAvailable();
        verify(llmClient, never()).chatWithImage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("记录里没存哈希时，按图片现算一个再去查预置表")
    void computesHashWhenRecordHasNone() throws IOException
    {
        // 造一个真实存在的「上传文件」，把 record 指过去，并不给它 imageHash
        Path root = Files.createTempDirectory("tz-upload");
        String originalProfile = RuoYiConfig.getProfile();
        try
        {
            new RuoYiConfig().setProfile(root.toString());
            Path file = root.resolve("upload/2026/09/12/leaf.png");
            Files.createDirectories(file.getParent());
            Files.write(file, "leaf-photo".getBytes(StandardCharsets.UTF_8));

            TzScoutingRecord plain = record();
            plain.setImageHash(null);
            plain.setImageUrl("/profile/upload/2026/09/12/leaf.png");
            when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(plain);
            when(presetResolver.resolve(any())).thenReturn(null);

            // 预置表没登记这张图、又关掉了大模型、知识库也检索不到 ——
            // 结束在明确拒答上，这正是这种情况下应有的行为。
            assertThrows(ServiceException.class, () -> service.diagnose(RECORD_ID, false));

            // 关键断言：查预置表时拿到的是一个真算出来的哈希，而不是 null。
            // 这个字段此前没有任何一处写它，等于保底路径一直没生效过 —— 这条用例钉住它。
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(presetResolver).resolve(hashCaptor.capture());
            String hash = hashCaptor.getValue();
            assertNotNull(hash, "没存哈希时必须按图片现算，不能就这么把保底路径放过去");
            assertTrue(hash.matches("[0-9a-f]{32}"), "应是小写十六进制 MD5，实际：" + hash);
        }
        finally
        {
            new RuoYiConfig().setProfile(originalProfile);
            deleteQuietly(root);
        }
    }

    /** 递归删掉临时上传目录，避免把测试产物留在系统临时目录里 */
    private static void deleteQuietly(Path root)
    {
        try (java.util.stream.Stream<Path> paths = Files.walk(root))
        {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try
                {
                    Files.deleteIfExists(p);
                }
                catch (IOException ignored)
                {
                    // 清理失败不影响用例结论
                }
            });
        }
        catch (IOException ignored)
        {
            // 同上
        }
    }

    // ------------------------------------------------------------------ 2 大模型成功

    @Test
    @DisplayName("大模型调用成功且置信度达标时采用其结论")
    void usesLlmResultWhenConfident()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"diagnosisName\":\"柑橘炭疽病\",\"confidence\":85,\"riskLevel\":\"2\",\"basis\":\"病斑有朱红色小点\"}",
                "dashscope", "qwen-vl-plus", 1234L, "digest"));
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(3L, "柑橘炭疽病"), 30D));
        when(knowledgeService.selectTzKnowledgeBaseByName("柑橘炭疽病")).thenReturn(entry(3L, "柑橘炭疽病"));

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("柑橘炭疽病", result.getDiagnosisName());
        assertEquals("llm", result.getSource());
        assertFalse(result.isDegraded());
        assertEquals(Long.valueOf(3L), result.getKnowledgeId(), "应把结论绑定到知识库条目");
        assertEquals("qwen-vl-plus", result.getModel());
        assertEquals(Long.valueOf(1234L), result.getLatencyMs());
        verify(callLogRecorder).record(eq("diagnose"), eq(RECORD_ID), any(), eq(false), isNull());
    }

    // ------------------------------------------------------------------ 3 降级：无 key

    @Test
    @DisplayName("未配置 API Key 时不发起调用，降级为知识库检索并说明原因")
    void degradesWhenNoApiKey()
    {
        when(llmClient.isAvailable()).thenReturn(false);
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 42D));

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("fallback", result.getSource());
        assertTrue(result.isDegraded());
        assertEquals("柑橘溃疡病", result.getDiagnosisName());
        assertEquals(Long.valueOf(1L), result.getKnowledgeId());
        assertTrue(result.getDegradeReason().contains("API Key"),
                "降级原因必须能说清楚，实际：" + result.getDegradeReason());
        assertTrue(result.getConfidence().doubleValue() < 60D,
                "降级结论的置信度应低于大模型阈值，不该显得同样可信");
        verify(llmClient, never()).chat(any(), any());
        verify(llmClient, never()).chatWithImage(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ 4 降级：调用失败

    @Test
    @DisplayName("大模型调用失败时降级，而不是把异常抛给用户")
    void degradesWhenLlmCallFails()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.fail(
                "HTTP 401 Unauthorized", "dashscope", "qwen-vl-plus", 300L, "digest"));
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(2L, "柑橘红蜘蛛"), 25D));

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("fallback", result.getSource());
        assertEquals("柑橘红蜘蛛", result.getDiagnosisName());
        assertTrue(result.getDegradeReason().contains("大模型调用失败"),
                "应记录失败原因，实际：" + result.getDegradeReason());
    }

    // ------------------------------------------------------------------ 5 降级：置信度不足

    @Test
    @DisplayName("大模型置信度低于阈值时不予采信，转降级路径")
    void discardsLowConfidenceLlmResult()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"diagnosisName\":\"疑似黄龙病\",\"confidence\":35,\"riskLevel\":\"3\",\"basis\":\"不太确定\"}",
                "dashscope", "qwen-vl-plus", 900L, "digest"));
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 20D));

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("fallback", result.getSource(), "低置信度的模型结论不该被采用");
        assertEquals("柑橘溃疡病", result.getDiagnosisName(), "应改用检索到的条目");
        assertTrue(result.getDegradeReason().contains("置信度"),
                "应说明是因为置信度不足，实际：" + result.getDegradeReason());
    }

    // ------------------------------------------------------------------ 6 降级：模型输出不可解析

    @Test
    @DisplayName("大模型返回非 JSON 垃圾内容时降级")
    void degradesWhenLlmOutputUnparsable()
    {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "抱歉，我无法根据这张图片做出判断。", "dashscope", "qwen-vl-plus", 500L, "digest"));
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 20D));

        DiagnosisResult result = service.diagnose(RECORD_ID, true);

        assertEquals("fallback", result.getSource());
        assertTrue(result.getDegradeReason().contains("无法解析"),
                "实际：" + result.getDegradeReason());
    }

    // ------------------------------------------------------------------ 7 明确拒绝

    @Test
    @DisplayName("全链路都给不出结论时必须明确报错，绝不编造")
    void refusesWhenNothingIsAvailable()
    {
        when(llmClient.isAvailable()).thenReturn(false);
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(new ArrayList<KnowledgeHit>());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.diagnose(RECORD_ID, true));

        assertTrue(ex.getMessage().contains("无法给出可靠结论"), "实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("农技员"), "应给出下一步建议");
        verify(recordService, never()).updateTzScoutingRecord(any());
    }

    @Test
    @DisplayName("检索到了条目但相关度不足时，同样不硬给结论")
    void refusesWhenRetrievalIsNotConfident()
    {
        when(llmClient.isAvailable()).thenReturn(false);
        // 分数低于 KnowledgeHit.isConfident() 的阈值
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 2D));

        assertThrows(ServiceException.class, () -> service.diagnose(RECORD_ID, true));
    }

    @Test
    @DisplayName("useLlm=false 时强制执行保底路径")
    void respectsUseLlmFlag()
    {
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 42D));

        DiagnosisResult result = service.diagnose(RECORD_ID, false);

        assertEquals("fallback", result.getSource());
        assertTrue(result.getDegradeReason().contains("指定不使用大模型"),
                "实际：" + result.getDegradeReason());
        verify(llmClient, never()).isAvailable();
    }

    // ------------------------------------------------------------------ 8 落库

    @Test
    @DisplayName("诊断结论必须回写到巡田记录，且默认进入「已诊断」状态")
    void persistsDiagnosisBackToRecord()
    {
        when(llmClient.isAvailable()).thenReturn(false);
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 42D));

        service.diagnose(RECORD_ID, true);

        org.mockito.ArgumentCaptor<TzScoutingRecord> captor =
                org.mockito.ArgumentCaptor.forClass(TzScoutingRecord.class);
        verify(recordService).updateTzScoutingRecord(captor.capture());
        TzScoutingRecord saved = captor.getValue();

        assertEquals(RECORD_ID, saved.getRecordId());
        assertEquals("柑橘溃疡病", saved.getDiagnosisName());
        assertEquals("fallback", saved.getDiagnosisSource());
        assertEquals("1", saved.getStatus());
        assertEquals(Long.valueOf(1L), saved.getKnowledgeId());
    }

    @Test
    @DisplayName("已生成报告（状态2）的记录重新诊断时不应回退状态")
    void doesNotRegressReportedStatus()
    {
        TzScoutingRecord reported = record();
        reported.setStatus("2");
        when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(reported);
        when(llmClient.isAvailable()).thenReturn(false);
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits(entry(1L, "柑橘溃疡病"), 42D));

        service.diagnose(RECORD_ID, true);

        org.mockito.ArgumentCaptor<TzScoutingRecord> captor =
                org.mockito.ArgumentCaptor.forClass(TzScoutingRecord.class);
        verify(recordService).updateTzScoutingRecord(captor.capture());
        assertNull(captor.getValue().getStatus(),
                "状态不该被回写覆盖，否则报告会「消失」");
    }

    @Test
    @DisplayName("记录不存在时明确报错")
    void failsOnMissingRecord()
    {
        when(recordService.selectTzScoutingRecordById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.diagnose(999L, true));
    }

    // ------------------------------------------------------------------ 夹具

    @Test
    @DisplayName("模型看过照片但把握不足时，不得退回纯文字检索去凑一个名字")
    void doesNotFallBackToKeywordWhenLlmSawTheImage() throws IOException
    {
        // 这是「什么病都像溃疡病」最后的来源：模型看着图说没把握，
        // 系统却退回字面检索、按文字又猜一次，而纯文字检索天然偏向溃疡病。
        Path root = Files.createTempDirectory("tz-upload-fallback");
        String originalProfile = RuoYiConfig.getProfile();
        try
        {
            new RuoYiConfig().setProfile(root.toString());
            Path file = root.resolve("upload/2026/09/14/leaf.png");
            Files.createDirectories(file.getParent());
            Files.write(file, "leaf-photo".getBytes(StandardCharsets.UTF_8));

            TzScoutingRecord withImage = record();
            withImage.setImageUrl("/profile/upload/2026/09/14/leaf.png");
            when(recordService.selectTzScoutingRecordById(RECORD_ID)).thenReturn(withImage);

            // 检索这边给出一个「很像溃疡病」的命中：如果代码退回去，就会用它作为结论
            List<KnowledgeHit> hits = new ArrayList<>();
            hits.add(new KnowledgeHit(entry(100L, "柑橘溃疡病"), 30D));
            when(ragService.retrieve(anyString(), anyInt())).thenReturn(hits);
            when(ragService.isConfident(any())).thenReturn(true);

            when(llmClient.isAvailable()).thenReturn(true);
            when(llmClient.chatWithImage(any(), any(), any(), any())).thenReturn(
                    LlmResponse.ok("{\"diagnosisName\":\"柑橘疮痂病\",\"confidence\":55,"
                            + "\"riskLevel\":\"1\",\"basis\":\"边界模糊\",\"alternatives\":\"无\"}",
                            "zhipu", "glm-4.1v", 900L, "d"));

            ServiceException e = assertThrows(ServiceException.class,
                    () -> service.diagnose(RECORD_ID, true));

            assertTrue(e.getMessage().contains("柑橘疮痂病"),
                    "要把模型那个不采信的结论作为线索告诉用户，实际：" + e.getMessage());
            assertTrue(e.getMessage().contains("55"), "要说清把握有多少，实际：" + e.getMessage());
            assertFalse(e.getMessage().contains("溃疡病"),
                    "绝不能把纯文字检索猜出来的名字塞回来，实际：" + e.getMessage());
        }
        finally
        {
            new RuoYiConfig().setProfile(originalProfile);
        }
    }

    private TzScoutingRecord record()
    {
        TzScoutingRecord r = new TzScoutingRecord();
        r.setRecordId(RECORD_ID);
        r.setPlotId(10L);
        r.setCropType("柑橘");
        r.setPlantPart("1");
        r.setSeverity("2");
        r.setSymptomText("叶片出现近圆形病斑，周围有黄色晕圈");
        r.setStatus("0");
        return r;
    }

    private TzKnowledgeBase entry(Long id, String name)
    {
        TzKnowledgeBase e = new TzKnowledgeBase();
        e.setKnowledgeId(id);
        e.setDiseaseName(name);
        e.setStatus("0");
        return e;
    }

    private List<KnowledgeHit> hits(TzKnowledgeBase entry, double score)
    {
        List<KnowledgeHit> list = new ArrayList<>();
        list.add(new KnowledgeHit(entry, score));
        return list;
    }
}
