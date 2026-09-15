package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzKnowledgeBase;
import com.ruoyi.system.domain.TzQaMessage;
import com.ruoyi.system.domain.TzQaSession;
import com.ruoyi.system.mapper.TzQaSessionMapper;
import com.ruoyi.system.service.ITzQaMessageService;
import com.ruoyi.system.service.ITzQaSessionService;

/**
 * 农技问答单测。
 *
 * 问答比诊断更危险：诊断有照片约束着话题，问答可以问出任何东西，
 * 而农户会照着答的去做。所以这组用例守的是三条：**没有依据就不答**、
 * **知识库没收录的用量一个都不许出现**、**答完之后能说清依据是哪一条**。
 *
 * 剂量校验用真实的 {@link DosageGuard}：要验证的正是「校验器确实接在问答链路上」。
 *
 * @author tianzhen
 */
class QaServiceTest
{
    private static final Long SESSION_ID = 7001L;
    private static final Long KNOWLEDGE_ID = 1L;

    private QaService service;
    private ITzQaSessionService sessionService;
    private ITzQaMessageService messageService;
    private TzQaSessionMapper sessionMapper;
    private RagService ragService;
    private PromptBuilder promptBuilder;
    private LlmClient llmClient;
    private AiCallLogRecorder callLogRecorder;
    private AiProperties properties;

    @BeforeEach
    void setUp()
    {
        service = new QaService();
        sessionService = mock(ITzQaSessionService.class);
        messageService = mock(ITzQaMessageService.class);
        sessionMapper = mock(TzQaSessionMapper.class);
        ragService = mock(RagService.class);
        promptBuilder = mock(PromptBuilder.class);
        llmClient = mock(LlmClient.class);
        callLogRecorder = mock(AiCallLogRecorder.class);

        properties = new AiProperties();
        properties.setDisclaimer("测试用免责声明");
        properties.setRagTopN(3);

        ReflectionTestUtils.setField(service, "qaSessionService", sessionService);
        ReflectionTestUtils.setField(service, "qaMessageService", messageService);
        ReflectionTestUtils.setField(service, "qaSessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "ragService", ragService);
        ReflectionTestUtils.setField(service, "promptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "dosageGuard", new DosageGuard());
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "properties", properties);

        when(sessionService.selectTzQaSessionById(SESSION_ID)).thenReturn(openSession());
        when(promptBuilder.qaSystemPrompt()).thenReturn("system");
        when(promptBuilder.qaUserPrompt(anyString(), any())).thenReturn("user");
        // 模拟 MyBatis 的 useGeneratedKeys：主键是插入后回填的，mock 得自己补上
        when(messageService.insertTzQaMessage(any())).thenAnswer(invocation -> {
            TzQaMessage saved = invocation.getArgument(0);
            if (saved.getMessageId() == null)
            {
                saved.setMessageId(9001L);
            }
            return 1;
        });
        when(sessionService.insertTzQaSession(any())).thenAnswer(invocation -> {
            TzQaSession saved = invocation.getArgument(0);
            saved.setSessionId(SESSION_ID);
            return 1;
        });
    }

    // ------------------------------------------------------------------ 前置校验

    @Test
    @DisplayName("空问题直接拒绝，不占用会话也不落一条空消息")
    void rejectsBlankQuestion()
    {
        assertThrows(ServiceException.class, () -> service.ask(null, null, true));
        assertThrows(ServiceException.class, () -> service.ask(null, "   ", true));

        verify(messageService, never()).insertTzQaMessage(any());
        verify(ragService, never()).retrieve(any(), anyInt());
    }

    @Test
    @DisplayName("会话不存在时明确报错，而不是悄悄新建一个")
    void rejectsMissingSession()
    {
        when(sessionService.selectTzQaSessionById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.ask(999L, "叶片发黄怎么办", true));
    }

    @Test
    @DisplayName("会话已关闭时拒绝追问，并提示开新会话")
    void rejectsClosedSession()
    {
        TzQaSession closed = openSession();
        closed.setStatus("1");
        when(sessionService.selectTzQaSessionById(SESSION_ID)).thenReturn(closed);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.ask(SESSION_ID, "叶片发黄怎么办", true));
        assertTrue(ex.getMessage().contains("已关闭"), "实际：" + ex.getMessage());
    }

    // ------------------------------------------------------------------ 有依据才答

    @Test
    @DisplayName("知识库命中时以条目原文作答，并带上资料来源与免责声明")
    void answersFromKnowledgeBase()
    {
        confidentHit();

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", false);

        assertEquals(QaService.SOURCE_KB, result.getSource());
        assertEquals(QaService.STATUS_OK, result.getAnswerStatus());
        assertFalse(result.isRefused());
        assertTrue(result.getAnswer().contains("柑橘溃疡病"), "实际：" + result.getAnswer());
        assertTrue(result.getAnswer().contains("剪除病叶"), "应包含知识库的防治措施，实际：" + result.getAnswer());
        assertTrue(result.getAnswer().contains("《测试来源》"), "建议要可溯，正文里必须带资料来源");
        assertTrue(result.getAnswer().contains("测试用免责声明"), "必须带免责声明");
        assertEquals(KNOWLEDGE_ID, result.getKnowledgeId());
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("检索无区分度且大模型不可用时明确拒答，不硬凑一个答案")
    void refusesWhenNothingToRelyOn()
    {
        noConfidentHit();
        when(llmClient.isAvailable()).thenReturn(false);

        QaAnswerResult result = service.ask(SESSION_ID, "柑橘什么时候施冬肥", true);

        assertTrue(result.isRefused());
        assertEquals(QaService.STATUS_NO_MATCH, result.getAnswerStatus());
        assertEquals(null, result.getSource());
        assertTrue(result.getAnswer().contains("无法作答"), "实际：" + result.getAnswer());
        // 拒答的原因要说准：这里是「模型不可用」，不是「知识库没收录」。
        // 曾经文案写成「知识库未收录该问题，本次不作答」，会让人误以为
        // 系统只认知识库、知识库没有就永远答不了 —— 实际上配好 key 就能答。
        assertTrue(result.getAnswer().contains("TZ_AI_API_KEY"),
                "拒答时要告诉用户怎样才恢复得了，实际：" + result.getAnswer());
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("拒答时不追加剂量校验说明 —— 没作答就没什么可校验的")
    void refusalHasNoDosageSection()
    {
        noConfidentHit();
        when(llmClient.isAvailable()).thenReturn(false);

        QaAnswerResult result = service.ask(SESSION_ID, "柑橘什么时候施冬肥", true);

        assertFalse(result.isDosageGuardHit());
        assertFalse(result.getAnswer().contains("剂量校验"), "实际：" + result.getAnswer());
    }

    @Test
    @DisplayName("检索无区分度但大模型可用时给通用建议，且不得参考任何知识库条目")
    void fallsBackToLlmWithoutKnowledge()
    {
        noConfidentHit();
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"answer\":\"冬肥一般在采果后施\",\"guidance\":\"沿树冠滴水线开沟施入有机肥\","
              + "\"basis\":\"通用栽培管理经验\",\"disclaimer\":\"仅供参考\"}",
                "dashscope", "qwen-plus", 700L, "digest"));

        QaAnswerResult result = service.ask(SESSION_ID, "柑橘什么时候施冬肥", true);

        assertEquals(QaService.SOURCE_LLM, result.getSource());
        assertEquals(QaService.STATUS_OK, result.getAnswerStatus());
        assertTrue(result.getAnswer().contains("采果后施"), "实际：" + result.getAnswer());
        assertTrue(result.getSourceText().contains("知识库未收录"), "必须明说这次没有知识库依据");
        assertTrue(result.getSourceText().contains("通用农艺知识"),
                "来源要说清是「模型的通用知识作答」，不是「知识库降级」，实际：" + result.getSourceText());

        // 提示词的第二个参数是知识库条目，无依据时必须是 null
        ArgumentCaptor<TzKnowledgeBase> captor = ArgumentCaptor.forClass(TzKnowledgeBase.class);
        verify(promptBuilder).qaUserPrompt(anyString(), captor.capture());
        assertEquals(null, captor.getValue(), "无依据时不得把任何条目塞给模型当依据");
    }

    // ------------------------------------------------------------------ 合规核心：剂量

    @Test
    @DisplayName("知识库未覆盖时，大模型给出的任何用量都要被拦下")
    void blocksEveryDosageWhenKnowledgeIsEmpty()
    {
        noConfidentHit();
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"answer\":\"叶片发黄多为缺素\",\"guidance\":\"可叶面喷施 0.2% 硫酸镁 500倍液，"
              + "每亩追施 10 公斤硫酸钾\",\"basis\":\"通用经验\",\"disclaimer\":\"仅供参考\"}",
                "dashscope", "qwen-plus", 700L, "digest"));

        QaAnswerResult result = service.ask(SESSION_ID, "柑橘叶片发黄怎么办", true);

        assertTrue(result.isDosageGuardHit(), "没有知识库依据时，任何具体用量都不该被放行");
        assertFalse(result.getAnswer().contains("500倍液"), "实际：" + result.getAnswer());
        assertFalse(result.getAnswer().contains("10 公斤"), "实际：" + result.getAnswer());
        assertTrue(result.getAnswer().contains("请以农药标签为准"), "抹掉数字后要留下可执行的指引");
        assertTrue(result.getRejectedDosages().size() >= 2, "实际：" + result.getRejectedDosages());
        assertTrue(result.getAcceptedDosages().isEmpty());
    }

    @Test
    @DisplayName("知识库命中时，与条目一致的用量放行、不一致的拦截")
    void onlyAllowsDosageRecordedInKnowledge()
    {
        confidentHit();
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(LlmResponse.ok(
                "{\"answer\":\"是柑橘溃疡病\",\"guidance\":\"可喷施 波尔多液 200倍液；也可用 多菌灵 800倍液\","
              + "\"basis\":\"叶片有黄色晕圈\",\"disclaimer\":\"仅供参考\"}",
                "dashscope", "qwen-plus", 900L, "digest"));

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", true);

        assertEquals(QaService.SOURCE_LLM, result.getSource());
        assertTrue(result.isDosageGuardHit());
        assertTrue(result.getAnswer().contains("200倍液"), "知识库收录的用量应放行，实际：" + result.getAnswer());
        assertFalse(result.getAnswer().contains("800倍液"), "知识库没收录的用量必须拦下");
        assertTrue(result.getRejectedDosages().stream().anyMatch(item -> item.contains("800倍")),
                "拦截记录里要能看出拦的是哪一条，实际：" + result.getRejectedDosages());
    }

    // ------------------------------------------------------------------ 降级

    @Test
    @DisplayName("大模型调用失败时降级到知识库，并如实标注降级与原因")
    void degradesToKnowledgeWhenLlmFails()
    {
        confidentHit();
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmResponse.fail("连接超时", "dashscope", "qwen-plus", 30000L, "digest"));

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", true);

        assertEquals(QaService.SOURCE_KB, result.getSource());
        assertEquals(QaService.STATUS_DEGRADED, result.getAnswerStatus());
        assertTrue(result.isDegraded());
        assertTrue(result.getDegradeReason().contains("连接超时"), "实际：" + result.getDegradeReason());
        assertTrue(result.getAnswer().contains("柑橘溃疡病"), "降级了也要交付出内容");
        verify(callLogRecorder).record(anyString(), any(), any(), org.mockito.ArgumentMatchers.eq(true), anyString());
    }

    @Test
    @DisplayName("用户主动关掉大模型不算降级 —— 那是他自己的选择")
    void explicitNoLlmIsNotADegradation()
    {
        confidentHit();
        when(llmClient.isAvailable()).thenReturn(true);

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", false);

        assertEquals(QaService.SOURCE_KB, result.getSource());
        assertEquals(QaService.STATUS_OK, result.getAnswerStatus());
        assertFalse(result.isDegraded(), "指定不用大模型不该被记成降级");
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("未配置 Key 走知识库不算降级，因为本来就没什么可降的")
    void missingKeyIsNotADegradation()
    {
        confidentHit();
        when(llmClient.isAvailable()).thenReturn(false);

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", true);

        assertEquals(QaService.STATUS_OK, result.getAnswerStatus());
        assertFalse(result.isDegraded());
    }

    @Test
    @DisplayName("大模型返回内容无法解析时也要降级，不能把空答案当成回答")
    void degradesWhenLlmContentUnparsable()
    {
        confidentHit();
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString()))
                .thenReturn(LlmResponse.ok("这不是 JSON", "dashscope", "qwen-plus", 500L, "digest"));

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", true);

        assertEquals(QaService.SOURCE_KB, result.getSource());
        assertEquals(QaService.STATUS_DEGRADED, result.getAnswerStatus());
        assertTrue(result.getAnswer().contains("柑橘溃疡病"));
    }

    // ------------------------------------------------------------------ 会话与留痕

    @Test
    @DisplayName("同一会话连续追问要沿用会话，不新建")
    void reusesExistingSession()
    {
        confidentHit();

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", false);

        assertEquals(SESSION_ID, result.getSessionId());
        verify(sessionService, never()).insertTzQaSession(any());
        // 一次提问落库两条消息（问 + 答），计数按「条」走，所以是 2 次各 +1
        verify(sessionMapper, times(2)).increaseMsgCount(SESSION_ID, 1);
    }

    @Test
    @DisplayName("不传会话ID时新建会话，标题取首问前 30 字")
    void createsSessionWhenAbsent()
    {
        confidentHit();
        String longQuestion = "我家的柑橘树叶片上出现了很多近圆形的病斑而且中央木栓化隆起周围还有黄色晕圈这是怎么回事";

        QaAnswerResult result = service.ask(null, longQuestion, false);

        ArgumentCaptor<TzQaSession> captor = ArgumentCaptor.forClass(TzQaSession.class);
        verify(sessionService).insertTzQaSession(captor.capture());
        String title = captor.getValue().getSessionTitle();
        assertEquals(30, title.length(), "标题应截断到 30 字，实际：" + title);
        assertTrue(longQuestion.startsWith(title));
        assertEquals(SESSION_ID, result.getSessionId(), "新建会话的ID要回填给调用方");
    }

    @Test
    @DisplayName("一问一答各落一条消息，回答消息要带上来源标注与应答状态")
    void persistsBothMessagesWithTraceability()
    {
        confidentHit();

        QaAnswerResult result = service.ask(SESSION_ID, "叶片有黄色晕圈是什么病", false);

        ArgumentCaptor<TzQaMessage> captor = ArgumentCaptor.forClass(TzQaMessage.class);
        verify(messageService, org.mockito.Mockito.times(2)).insertTzQaMessage(captor.capture());
        List<TzQaMessage> saved = captor.getAllValues();

        TzQaMessage question = saved.get(0);
        assertEquals("user", question.getRole());
        assertEquals("叶片有黄色晕圈是什么病", question.getContent());
        assertEquals(SESSION_ID, question.getSessionId());

        TzQaMessage answer = saved.get(1);
        assertEquals("assistant", answer.getRole());
        assertEquals(QaService.STATUS_OK, answer.getAnswerStatus());
        assertNotNull(answer.getSources(), "翻历史记录时要能看出当时依据的是哪一条");
        assertTrue(answer.getSources().contains("柑橘溃疡病"), "实际：" + answer.getSources());
        assertEquals(result.getMessageId(), answer.getMessageId());
    }

    @Test
    @DisplayName("未命中任何条目时来源标注留空，不写一个假的来源")
    void noSourceLabelWhenNothingMatched()
    {
        noConfidentHit();
        when(llmClient.isAvailable()).thenReturn(false);

        service.ask(SESSION_ID, "柑橘什么时候施冬肥", true);

        ArgumentCaptor<TzQaMessage> captor = ArgumentCaptor.forClass(TzQaMessage.class);
        verify(messageService, org.mockito.Mockito.times(2)).insertTzQaMessage(captor.capture());
        assertEquals(null, captor.getAllValues().get(1).getSources());
    }

    // ------------------------------------------------------------------ 夹具

    private void confidentHit()
    {
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(hitList(knowledgeEntry()));
        when(ragService.isConfident(any())).thenReturn(true);
    }

    private void noConfidentHit()
    {
        when(ragService.retrieve(anyString(), anyInt())).thenReturn(new ArrayList<KnowledgeHit>());
        when(ragService.isConfident(any())).thenReturn(false);
    }

    private List<KnowledgeHit> hitList(TzKnowledgeBase entry)
    {
        List<KnowledgeHit> hits = new ArrayList<>();
        hits.add(new KnowledgeHit(entry, 40D));
        return hits;
    }

    private TzKnowledgeBase knowledgeEntry()
    {
        TzKnowledgeBase entry = new TzKnowledgeBase();
        entry.setKnowledgeId(KNOWLEDGE_ID);
        entry.setCropType("柑橘");
        entry.setDiseaseName("柑橘溃疡病");
        entry.setSymptoms("叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈");
        entry.setPrevention("剪除病叶并集中烧毁，加强通风透光");
        // 只收录了 200倍液 这一条用量，用来验证 800倍液 会被拦
        entry.setMedicineNote("可喷施 波尔多液 200倍液");
        entry.setSafetyNote("注意安全间隔期");
        entry.setSource("《测试来源》");
        entry.setStatus("0");
        return entry;
    }

    private TzQaSession openSession()
    {
        TzQaSession session = new TzQaSession();
        session.setSessionId(SESSION_ID);
        session.setSessionTitle("叶片有黄色晕圈是什么病");
        session.setStatus("0");
        session.setMsgCount(0);
        return session;
    }
}
