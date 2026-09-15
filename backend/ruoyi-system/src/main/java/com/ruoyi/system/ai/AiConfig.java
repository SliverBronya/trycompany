package com.ruoyi.system.ai;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * AI 相关 Bean 装配。
 *
 * 这里刻意用具名 Bean + @Qualifier 注入，而不是直接 @Autowired RestTemplate：
 * 全局裸奔的 RestTemplate 一旦后面再出现第二个同类 Bean 就会启动即冲突，
 * 具名隔离掉这个隐患。超时也在这里按「读图慢」的实际需要单独设定。
 *
 * @author tianzhen
 */
@Configuration
public class AiConfig
{
    /**
     * 专供大模型调用的 RestTemplate。
     */
    @Bean("tzAiRestTemplate")
    public RestTemplate tzAiRestTemplate(AiProperties properties)
    {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }
}
