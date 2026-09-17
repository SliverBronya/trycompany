package com.ruoyi.system.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * 兼容 OpenAI /chat/completions 协议的客户端实现。
 *
 * 覆盖国产主流服务商（DashScope 兼容模式、智谱 GLM、火山方舟豆包、DeepSeek 等），
 * 换服务商通常只需改 application.yml 里的 base-url 和模型名，代码不动。
 *
 * 容错设计：
 *  - 不抛异常，失败一律转成 {@link LlmResponse#fail}，由上层决定降级路径；
 *  - 只要求模型「尽量」输出 JSON，解析交给上层做宽容解析，避免个别服务商
 *    不支持 response_format 时整个链路挂掉；
 *  - 5xx / 网络抖动按 maxRetries 重试；4xx（多半是 key 或模型名不对）不重试，
 *    唯一的例外是 429 —— 它是「排队」不是「配置错」，单独按 rateLimitRetries 退避重试。
 *
 * @author tianzhen
 */
@Component
public class OpenAiCompatLlmClient implements LlmClient
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatLlmClient.class);

    /** 提示词摘要入库长度上限，与 tz_ai_call_log.prompt_digest 对齐 */
    private static final int DIGEST_MAX = 480;

    @Autowired
    private AiProperties properties;

    @Autowired
    @Qualifier("tzAiRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public boolean isAvailable()
    {
        return properties.isAvailable();
    }

    @Override
    public String getProvider()
    {
        return properties.getProvider();
    }

    @Override
    public LlmResponse chat(String systemPrompt, String userPrompt)
    {
        return doChat(properties.getTextModel(), systemPrompt, userPrompt, null, null);
    }

    @Override
    public LlmResponse chatWithImage(byte[] imageBytes, String mimeType, String systemPrompt, String userPrompt)
    {
        return doChat(properties.getVisionModel(), systemPrompt, userPrompt, imageBytes, mimeType);
    }

    // ------------------------------------------------------------------ 内部实现

    private LlmResponse doChat(String model, String systemPrompt, String userPrompt,
                               byte[] imageBytes, String mimeType)
    {
        String digest = digest(systemPrompt, userPrompt);
        long started = System.currentTimeMillis();

        if (!properties.isAvailable())
        {
            return LlmResponse.fail("未配置大模型 API Key（tz.ai.api-key 为空）", properties.getProvider(), model,
                    System.currentTimeMillis() - started, digest);
        }

        String lastError = null;
        // 两套预算分开算：普通故障（5xx / 超时）用 maxRetries，限流用 rateLimitRetries。
        // 合成一个不行 —— 超时一次就要等 90 秒，拿同样的次数去重试限流，用户会被钉在页面上。
        int normalLeft = Math.max(0, properties.getMaxRetries());
        int rateLeft = Math.max(0, properties.getRateLimitRetries());
        int attempt = 0;

        while (true)
        {
            attempt++;
            try
            {
                String content = invoke(model, systemPrompt, userPrompt, imageBytes, mimeType);
                return LlmResponse.ok(content, properties.getProvider(), model,
                        System.currentTimeMillis() - started, digest);
            }
            catch (NonRetryableException e)
            {
                // 明确不可重试（服务商返回 error 体、非 2xx 等）
                lastError = e.getMessage();
                break;
            }
            catch (HttpClientErrorException e)
            {
                String message = "HTTP " + e.getStatusCode() + "：" + abbreviate(e.getResponseBodyAsString());

                // 429 是「排队」，不是「配置错」。免费档（智谱 Flash 系列）实测大比例请求被挡在这里，
                // 而它返回得极快（多数 200–500 ms），所以退避重试的代价远低于直接放弃：
                // 放弃的结果是用户看到「知识库降级」而不是模型结论，还会以为是自己 key 没配通。
                if (e.getStatusCode().value() == 429 && rateLeft > 0)
                {
                    rateLeft--;
                    lastError = message;
                    long wait = (long) properties.getRateLimitBackoffMs() * attempt;
                    log.warn("服务商限流（第 {} 次尝试），{} ms 后重试，剩余限流重试 {} 次", attempt, wait, rateLeft);
                    if (!sleepQuietly(wait))
                    {
                        lastError = message + "（等待重试时被中断）";
                        break;
                    }
                    continue;
                }
                if (e.getStatusCode().value() == 429)
                {
                    message = message + "（已退避重试 " + properties.getRateLimitRetries() + " 次仍被限流）";
                }
                // 其余 4xx：多半是 key 无效或模型名写错，重试也是同样结果
                lastError = message;
                break;
            }
            catch (RuntimeException e)
            {
                // 5xx、超时、网络抖动 —— 值得按 maxRetries 再试
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (normalLeft <= 0)
                {
                    break;
                }
                normalLeft--;
                log.warn("大模型调用失败（第 {} 次尝试），剩余重试 {} 次：{}", attempt, normalLeft, lastError);
            }
        }

        return LlmResponse.fail(lastError, properties.getProvider(), model,
                System.currentTimeMillis() - started, digest);
    }

    @SuppressWarnings("unchecked")
    private String invoke(String model, String systemPrompt, String userPrompt,
                          byte[] imageBytes, String mimeType)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        /* 按「本次是不是视觉调用」取 key：支持视觉/文本各配一个，
           只配了一个时 resolveApiKey 会自动回退，不会因此调不通 */
        boolean visionCall = imageBytes != null && imageBytes.length > 0;
        headers.setBearerAuth(properties.resolveApiKey(visionCall));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", properties.getTemperature());
        payload.put("messages", buildMessages(systemPrompt, userPrompt, imageBytes, mimeType));

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.resolveChatUrl(), HttpMethod.POST,
                new HttpEntity<>(payload, headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
        {
            throw new NonRetryableException("HTTP " + response.getStatusCode() + "，响应体为空或非成功状态");
        }
        return extractContent((Map<String, Object>) response.getBody());
    }

    /**
     * 组装 messages。带图时 user content 用多模态数组格式，
     * 图片以 data URL 内联，避免服务端去拉一个它够不着的本地地址。
     */
    private List<Map<String, Object>> buildMessages(String systemPrompt, String userPrompt,
                                                   byte[] imageBytes, String mimeType)
    {
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);

        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");

        if (imageBytes != null && imageBytes.length > 0)
        {
            String dataUrl = "data:" + (mimeType == null ? "image/jpeg" : mimeType)
                    + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", dataUrl);
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", userPrompt);

            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(textPart);
            parts.add(imagePart);
            user.put("content", parts);
        }
        else
        {
            user.put("content", userPrompt);
        }

        messages.add(user);
        return messages;
    }

    /**
     * 从响应里取正文。content 在部分服务商那里是字符串，
     * 在另一些（如带 reasoning 的模型）会是分段数组，两种都要能吃下。
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> body)
    {
        Object error = body.get("error");
        if (error != null)
        {
            throw new NonRetryableException("服务商返回错误：" + error);
        }

        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty())
        {
            throw new IllegalStateException("响应中没有 choices 字段");
        }

        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map))
        {
            throw new IllegalStateException("choices 结构异常");
        }

        Object message = ((Map<String, Object>) first).get("message");
        if (!(message instanceof Map))
        {
            throw new IllegalStateException("响应中缺少 message 字段");
        }

        Object content = ((Map<String, Object>) message).get("content");
        if (content instanceof String)
        {
            return (String) content;
        }
        if (content instanceof List)
        {
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<Object>) content)
            {
                if (part instanceof Map)
                {
                    Object text = ((Map<String, Object>) part).get("text");
                    if (text != null)
                    {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }

        throw new IllegalStateException("无法解析 message.content");
    }

    private String digest(String systemPrompt, String userPrompt)
    {
        String merged = "system: " + systemPrompt + "\nuser: " + userPrompt;
        merged = merged.replaceAll("\\s+", " ").trim();
        return merged.length() > DIGEST_MAX ? merged.substring(0, DIGEST_MAX) : merged;
    }

    private String abbreviate(String text)
    {
        if (text == null)
        {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    /**
     * 限流退避等待。
     *
     * @return true 表示等满了；false 表示等待期间线程被中断，调用方应立刻放弃重试
     */
    private boolean sleepQuietly(long millis)
    {
        if (millis <= 0L)
        {
            return true;
        }
        try
        {
            Thread.sleep(millis);
            return true;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 标记不该重试的错误（如 401/400），重试只是白等。 */
    private static class NonRetryableException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        NonRetryableException(String message)
        {
            super(message);
        }
    }
}
