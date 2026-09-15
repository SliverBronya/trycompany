package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzQaMessage;

/**
 * 农技问答消息 服务层
 *
 * @author tianzhen
 */
public interface ITzQaMessageService
{
    /**
     * 查询消息信息
     *
     * @param messageId 消息ID
     * @return 消息信息
     */
    public TzQaMessage selectTzQaMessageById(Long messageId);

    /**
     * 查询消息列表
     *
     * @param tzQaMessage 消息信息
     * @return 消息集合
     */
    public List<TzQaMessage> selectTzQaMessageList(TzQaMessage tzQaMessage);

    /**
     * 新增消息
     *
     * @param tzQaMessage 消息信息
     * @return 结果
     */
    public int insertTzQaMessage(TzQaMessage tzQaMessage);

    /**
     * 删除消息
     *
     * @param messageId 消息ID
     * @return 结果
     */
    public int deleteTzQaMessageById(Long messageId);

    /**
     * 批量删除消息
     *
     * @param messageIds 需要删除的消息ID
     * @return 结果
     */
    public int deleteTzQaMessageByIds(Long[] messageIds);

    /**
     * 按会话删除全部消息
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    public int deleteTzQaMessageBySessionId(Long sessionId);
}
