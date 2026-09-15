package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzImagePreset;

/**
 * 照片自动描述单测。
 *
 * <p>这条链路的产品主张是「拍照就能把描述填出来，填不出来就明说」，
 * 关键是三条：预置样张能离线填、大模型能读图填、两条都不通时<b>不许编</b>。
 * 另外两条容易漏的边界也一并钉住：
 *
 * <ul>
 *   <li>预置样张只登记了结论、忘了登记描述时，不能就此把整条链路堵死，
 *       要继续往大模型走；</li>
 *   <li>模型自报「照片看不清」时要采信它，不能把一段编出来的描述填进表单 ——
 *       这段文字会一路带进知识库检索和巡田报告。</li>
 * </ul>
 *
 * @author tianzhen
 */
class ImageDescriptionServiceTest
{
    private static final String IMAGE_URL = "/profile/upload/2026/09/14/leaf.png";

    private ImageDescriptionService service;
    private PresetDiagnosisResolver presetResolver;
    private PromptBuilder promptBuilder;
    private LlmClient llmClient;
    private AiCallLogRecorder callLogRecorder;
    private AiProperties properties;

    private Path uploadRoot;

    @BeforeEach
    void setUp() throws IOException
    {
        service = new ImageDescriptionService();
        presetResolver = mock(PresetDiagnosisResolver.class);
        promptBuilder = mock(PromptBuilder.class);
        llmClient = mock(LlmClient.class);
        callLogRecorder = mock(AiCallLogRecorder.class);

        properties = new AiProperties();
        properties.setDisclaimer("测试用免责声明");

        ReflectionTestUtils.setField(service, "presetResolver", presetResolver);
        ReflectionTestUtils.setField(service, "promptBuilder", promptBuilder);
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "callLogRecorder", callLogRecorder);
        ReflectionTestUtils.setField(service, "properties", properties);

        // 造一个真实存在的「上传文件」：读取字节这条路上不能靠 mock，
        // 因为绕开它正好会把「地址能定位到文件」这个最容易出错的环节一起跳过去
        uploadRoot = Files.createTempDirectory("tz-describe");
        new RuoYiConfig().setProfile(uploadRoot.toString());
        Path file = uploadRoot.resolve("upload/2026/09/14/leaf.png");
        Files.createDirectories(file.getParent());
        Files.write(file, "leaf-photo".getBytes(StandardCharsets.UTF_8));

        when(promptBuilder.describeSystemPrompt()).thenReturn("system");
        when(promptBuilder.describeUserPrompt(any(), any())).thenReturn("user");
        when(presetResolver.resolve(any())).thenReturn(null);
        when(llmClient.isAvailable()).thenReturn(true);
    }

    // ------------------------------------------------------------------ 入参与预置

    @Test
    @DisplayName("没有照片地址时直接拒绝，不静默返回空描述")
    void blankImageUrlRejected()
    {
        assertThrows(ServiceException.class, () -> service.describe("  ", "柑橘", "1"));
    }

    @Test
    @DisplayName("命中预置样张且登记过描述：直接采用，完全不调用大模型")
    void presetWithSymptomShortCircuitsBeforeLlm()
    {
        TzImagePreset preset = new TzImagePreset();
        preset.setImageName("demo-mg.png");
        preset.setSymptomText("中下部老叶叶脉间呈倒 V 形黄化，叶脉仍为绿色。");
        when(presetResolver.resolve(any())).thenReturn(preset);

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertTrue(result.isSuccess());
        assertEquals("preset", result.getSource());
        assertEquals("中下部老叶叶脉间呈倒 V 形黄化，叶脉仍为绿色。", result.getDescription());
        assertTrue(result.getSourceText().contains("demo-mg.png"), "要说清描述是从哪张登记样张来的");
        assertNull(result.getDegradeReason());
        verify(llmClient, never()).isAvailable();
        verify(llmClient, never()).chatWithImage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("命中预置样张但没登记描述：继续走大模型，不把链路堵死")
    void presetWithoutSymptomFallsThroughToLlm()
    {
        TzImagePreset preset = new TzImagePreset();
        preset.setImageName("demo-ulcer.png");
        preset.setSymptomText(null);
        when(presetResolver.resolve(any())).thenReturn(preset);
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok(
                        "{\"description\":\"叶片有近圆形病斑，中央木栓化隆起。\",\"observable\":true}",
                        "zhipu", "glm-4.1v-thinking-flash", 3210L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertTrue(result.isSuccess());
        assertEquals("llm", result.getSource());
        assertEquals("叶片有近圆形病斑，中央木栓化隆起。", result.getDescription());
        assertEquals(Long.valueOf(3210L), result.getLatencyMs());
    }

    // ------------------------------------------------------------------ 大模型路径

    @Test
    @DisplayName("未配 API Key 时如实说明没能生成，而不是返回一段空描述")
    void unavailableLlmReportsReason()
    {
        when(llmClient.isAvailable()).thenReturn(false);

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertFalse(result.isSuccess());
        assertNull(result.getDescription());
        assertTrue(result.getDegradeReason().contains("API Key"), "原因要能让用户看懂该怎么解决");
        verify(llmClient, never()).chatWithImage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("模型明确说照片看不清：采纳它的判断，不把描述填进表单")
    void unobservableImageIsRejected()
    {
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok(
                        "{\"description\":\"照片模糊，无法辨认。\",\"observable\":false}",
                        "zhipu", "glm-4.1v-thinking-flash", 2100L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertFalse(result.isSuccess());
        assertNull(result.getDescription());
        assertTrue(result.getDegradeReason().contains("清晰度"));
    }

    @Test
    @DisplayName("调用失败时把服务商给的原因带出来，不吞掉")
    void llmFailureKeepsReason()
    {
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.fail("HTTP 401：invalid api key", "zhipu", "glm-4.1v-thinking-flash", 120L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertFalse(result.isSuccess());
        assertTrue(result.getDegradeReason().contains("401"), "真实错误要透传，否则用户只能盲猜");
        assertNull(result.getDescription());
    }

    @Test
    @DisplayName("返回内容解析不出描述时，不把空串当成成功")
    void unparsableContentIsFailure()
    {
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok("抱歉，我无法处理这张图片。", "zhipu", "glm-4.1v-thinking-flash", 800L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertFalse(result.isSuccess());
        assertNull(result.getDescription());
    }

    @Test
    @DisplayName("缺少 observable 字段时不因此拒答，避免提示词小偏差白丢一次识别")
    void missingObservableFieldIsTreatedAsUsable()
    {
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok(
                        "{\"description\":\"叶面有灰白色失绿小点。\"}",
                        "zhipu", "glm-4.1v-thinking-flash", 1500L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertTrue(result.isSuccess());
        assertEquals("叶面有灰白色失绿小点。", result.getDescription());
    }

    @Test
    @DisplayName("描述里的换行被压平、超长被截断：它要占一行输入框，不是排版稿")
    void descriptionIsFlattenedAndCapped()
    {
        // 这里的 \\n 是 JSON 里的转义序列（两个字符：反斜杠 + n），不是 Java 的换行。
        // 直接塞真换行会得到一段非法 JSON，测的就变成「解析失败」而不是「压平换行」了
        StringBuilder longText = new StringBuilder("叶面密布小点。\\n\\n翻看叶背可见细小虫体。");
        while (longText.length() < 900)
        {
            longText.append("另外还有许多细节需要一并记录。");
        }
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok(
                        "{\"description\":\"" + longText + "\",\"observable\":true}",
                        "zhipu", "glm-4.1v-thinking-flash", 1600L, "digest"));

        ImageDescriptionResult result = service.describe(IMAGE_URL, "柑橘", "1");

        assertTrue(result.isSuccess());
        assertFalse(result.getDescription().contains("\n"), "换行要压成空格");
        assertTrue(result.getDescription().length() <= 500, "超长要截断，否则表单里没法看");
    }

    @Test
    @DisplayName("图片地址是带域名的完整 URL 时同样能读到文件")
    void absoluteUrlIsAccepted() throws IOException
    {
        when(llmClient.chatWithImage(any(), anyString(), anyString(), anyString()))
                .thenReturn(LlmResponse.ok(
                        "{\"description\":\"叶片有近圆形病斑。\",\"observable\":true}",
                        "zhipu", "glm-4.1v-thinking-flash", 900L, "digest"));

        // 演示数据是用脚本播种的，存的是完整地址；只认相对路径的话，
        // 这些图会被静默当成「无图」
        ImageDescriptionResult result = service.describe(
                "http://127.0.0.1:18080" + IMAGE_URL, "柑橘", "1");

        assertTrue(result.isSuccess());
        assertEquals("叶片有近圆形病斑。", result.getDescription());
    }

    @Test
    @DisplayName("图片文件不存在时明确说读不到，不去调模型")
    void missingFileIsReportedBeforeCallingLlm()
    {
        when(llmClient.isAvailable()).thenReturn(true);

        ImageDescriptionResult result = service.describe("/profile/upload/2026/09/14/nope.png", "柑橘", "1");

        assertFalse(result.isSuccess());
        assertTrue(result.getDegradeReason().contains("读不到"));
        verify(llmClient, never()).chatWithImage(any(), any(), any(), any());
    }
}
