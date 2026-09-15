package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.TzAiCallLog;

/**
 * AI 调用日志 数据层
 *
 * @author tianzhen
 */
public interface TzAiCallLogMapper
{
    /**
     * 查询调用日志列表
     *
     * @param tzAiCallLog 调用日志
     * @return 调用日志集合
     */
    public List<TzAiCallLog> selectTzAiCallLogList(TzAiCallLog tzAiCallLog);

    /**
     * 新增调用日志
     *
     * @param tzAiCallLog 调用日志
     * @return 结果
     */
    public int insertTzAiCallLog(TzAiCallLog tzAiCallLog);

    /**
     * 清空调用日志
     *
     * @return 结果
     */
    public int cleanTzAiCallLog();
}
