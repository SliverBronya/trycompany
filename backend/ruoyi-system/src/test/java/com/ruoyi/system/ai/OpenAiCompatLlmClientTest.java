package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * 大模型客户端的重试策略单测。
 *
 * 这组用例钉的是一条被真实数据推翻过的假设：代码原本把 **4xx 一律当作不可重试**，
 * 注释写的是「多半是 key 或模型名不对」。接上智谱免费档实测后才发现，免费模型
 * 大比例请求会被 429 挡回来（同一个模型十分钟前一次就成功、十分钟后连挂 9 次），
 * 而 429 也是 4xx —— 于是最该重试的情况恰恰被跳过了。
 *
 * 代价对比是这组用例存在的理由：429 返回只要 200–500 毫秒，超时一次要等 90 秒。
 * 所以两者的重试预算必须分开算，用同一个 maxRetries 会把 90 秒的代价套到限流上。
 *
 * 桩一律用 doThrow/doReturn 而不是 when().thenX() 链：后者要在一层嵌套泛型里同时
 * 解出 exchange 的 T 和 when 的 T，Mockito 这边推不出来（编译期就报「找不到合适的
 * thenReturn」）。do 系列的返回值是 Stubber，不参与泛型推导，稳。
 *
 * @author tianzhen
 */
class OpenAiCompatLlmClientTest
{
    private OpenAiCompatLlmClient client;
    private RestTemplate restTemplate;
    private AiProperties properties;

    @BeforeEach
    void setUp()
    {
        client = new OpenAiCompatLlmClient();
        restTemplate = mock(RestTemplate.class);

        properties = new AiProperties();
        properties.setApiKey("test-key");
        properties.setProvider("zhipu");
        properties.setBaseUrl("https://example.invalid/api/paas/v4");
        properties.setTextModel("glm-4-flash-250414");
        properties.setVisionModel("glm-4.6v-flash");
        properties.setTemperature(0.2D);
        properties.setMaxRetries(1);
        properties.setRateLimitRetries(4);
        // 单测不真等：退避是产品行为，但让每个用例都睡几秒只会让人不想跑测试
        properties.setRateLimitBackoffMs(0);

        ReflectionTestUtils.setField(client, "properties", properties);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    // ------------------------------------------------------------------ 限流

    @Test
    @DisplayName("被限流后按退避重试，第二次成功则整体算成功")
    void retriesAfterRateLimitAndSucceeds()
    {
        stubCalls(rateLimited(), okResponse("{\"diagnosisName\":\"柑橘溃疡病\"}"));

        LlmResponse response = client.chat("system", "user");

        assertTrue(response.isSuccess(), "限流重试后应当成功");
        assertEquals("{\"diagnosisName\":\"柑橘溃疡病\"}", response.getContent());
        assertEquals("zhipu", response.getProvider());
        verifyCalls(2);
    }

    @Test
    @DisplayName("限流预算用尽后如实失败，且失败原因里说清是限流、重试了几次")
    void givesUpAfterRateLimitBudgetExhausted()
    {
        properties.setRateLimitRetries(2);
        stubCalls(rateLimited());

        LlmResponse response = client.chat("system", "user");

        assertFalse(response.isSuccess());
        // 1 次首发 + 2 次限流重试，不能多打一次
        verifyCalls(3);
        assertTrue(response.getErrorMsg().contains("429"), "错误里必须留着 HTTP 状态码：" + response.getErrorMsg());
        assertTrue(response.getErrorMsg().contains("仍被限流"), "要说明是重试完仍被限流：" + response.getErrorMsg());
    }

    @Test
    @DisplayName("限流重试不吃普通重试的预算")
    void rateLimitRetriesDoNotConsumeNormalBudget()
    {
        // 普通重试给 0 次：两套预算若被合成一个，这组连一次限流重试都做不成
        properties.setMaxRetries(0);
        properties.setRateLimitRetries(3);
        stubCalls(rateLimited());

        LlmResponse response = client.chat("system", "user");

        assertFalse(response.isSuccess());
        verifyCalls(4);
    }

    // ------------------------------------------------------------------ 其余 4xx / 5xx

    @Test
    @DisplayName("模型名写错这类 4xx 不重试，白等没有意义")
    void doesNotRetryOtherClientErrors()
    {
        stubCalls(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "模型不存在".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        LlmResponse response = client.chat("system", "user");

        assertFalse(response.isSuccess());
        verifyCalls(1);
        assertTrue(response.getErrorMsg().contains("400"), "响应体要带回来给排查用：" + response.getErrorMsg());
    }

    @Test
    @DisplayName("5xx 按 maxRetries 重试，恢复后算成功")
    void retriesServerErrorsWithinMaxRetries()
    {
        properties.setMaxRetries(2);
        stubCalls(serverError(), serverError(), okResponse("{\"answer\":\"好的\"}"));

        LlmResponse response = client.chat("system", "user");

        assertTrue(response.isSuccess());
        verifyCalls(3);
    }

    @Test
    @DisplayName("5xx 超出 maxRetries 后不再打接口")
    void stopsRetryingServerErrorsAfterBudget()
    {
        properties.setMaxRetries(1);
        stubCalls(serverError());

        LlmResponse response = client.chat("system", "user");

        assertFalse(response.isSuccess());
        verifyCalls(2);
    }

    // ------------------------------------------------------------------ 未配置 key

    @Test
    @DisplayName("没配 key 时直接失败，一个请求都不发")
    void failsFastWhenApiKeyMissing()
    {
        properties.setApiKey("");

        LlmResponse response = client.chat("system", "user");

        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMsg().contains("API Key"), "要说清是 key 没配：" + response.getErrorMsg());
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(Map.class));
    }

    // ------------------------------------------------------------------ 夹具

    /**
     * 按顺序安排应答：给 n 个 Throwable 表示前 n 次都失败，可选给第 n+1 个成功响应。
     * 只给 Throwable 时，多出来的调用继续抛最后一个异常。
     */
    private void stubCalls(Object... responses)
    {
        org.mockito.stubbing.Stubber stubber = null;
        for (Object response : responses)
        {
            if (response instanceof Throwable)
            {
                stubber = (stubber == null) ? doThrow((Throwable) response) : stubber.doThrow((Throwable) response);
            }
            else
            {
                stubber = (stubber == null) ? doReturn(response) : stubber.doReturn(response);
            }
        }
        if (responses.length > 0 && responses[responses.length - 1] instanceof Throwable)
        {
            stubber = stubber.doThrow((Throwable) responses[responses.length - 1]);
        }
        stubber.when(restTemplate).exchange(anyString(), any(HttpMethod.class), any(), eq(Map.class));
    }

    private void verifyCalls(int expected)
    {
        verify(restTemplate, times(expected)).exchange(anyString(), any(HttpMethod.class), any(), eq(Map.class));
    }

    private HttpClientErrorException rateLimited()
    {
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"1305\",\"message\":\"该模型当前访问量过大，请您稍后再试\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    private HttpServerErrorException serverError()
    {
        return HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    /**
     * 造一个 OpenAI 兼容格式的成功响应。
     *
     * 这里刻意用裸 Map 而不是 Map.of：桩方法的签名是 ResponseEntity&lt;Map&gt;，
     * 而 Map.of 推出来的 Map&lt;String,Object&gt; 与它不是同一参数化类型，赋值过不去。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ResponseEntity<Map> okResponse(String content)
    {
        Map message = new LinkedHashMap();
        message.put("role", "assistant");
        message.put("content", content);

        Map choice = new LinkedHashMap();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map body = new LinkedHashMap();
        body.put("choices", List.of(choice));
        return ResponseEntity.ok(body);
    }
}
