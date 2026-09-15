package com.ruoyi.system.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.TzAiCallLog;
import com.ruoyi.system.mapper.TzAiCallLogMapper;

/**
 * AI 调用日志记录。
 *
 * 单独抽出来是因为它有一条硬性要求：**绝不能因为记日志失败而让业务失败**。
 * 演示时数据库出点小状况，用户该拿到诊断结论还得拿到。所以这里把异常全吞掉，
 * 只留一条 warn 日志。
 *
 * @author tianzhen
 */
@Component
public class AiCallLogRecorder
{
    private static final Logger log = LoggerFactory.getLogger(AiCallLogRecorder.class);

    @Autowired
    private TzAiCallLogMapper aiCallLogMapper;

    /**
     * 记录一次调用。
     *
     * @param bizType      业务类型（diagnose / suggest / report / qa）
     * @param bizId        业务ID
     * @param response     模型返回，可为 null（表示压根没发起调用）
     * @param fallbackUsed 是否走了降级
     * @param note         补充说明，通常是降级原因
     */
    public void record(String bizType, Object bizId, LlmResponse response, boolean fallbackUsed, String note)
    {
        try
        {
            TzAiCallLog entity = new TzAiCallLog();
            entity.setBizType(bizType);
            entity.setBizId(bizId == null ? null : String.valueOf(bizId));
            entity.setFallbackUsed(fallbackUsed ? "1" : "0");

            if (response != null)
            {
                entity.setProvider(response.getProvider());
                entity.setModel(response.getModel());
                entity.setPromptDigest(response.getPromptDigest());
                entity.setLatencyMs((int) response.getLatencyMs());
                entity.setSuccess(response.isSuccess() ? "1" : "0");
                // 调用成功但结论被弃用（如置信度不足）时，errorMsg 位置留给降级原因，
                // 否则这一栏是空的，事后查不出「明明是成功调用为什么没用它的结果」
                entity.setErrorMsg(trim(
                        response.isSuccess() ? note : response.getErrorMsg(), 1000));
            }
            else
            {
                entity.setSuccess("0");
                entity.setErrorMsg(trim(note, 1000));
            }

            aiCallLogMapper.insertTzAiCallLog(entity);
        }
        catch (Exception e)
        {
            log.warn("写入 AI 调用日志失败（不影响业务）：{}", e.getMessage());
        }
    }

    private String trim(String text, int max)
    {
        if (text == null)
        {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
