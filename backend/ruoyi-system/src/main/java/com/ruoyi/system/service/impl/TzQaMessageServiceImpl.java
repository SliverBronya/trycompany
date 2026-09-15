package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.TzQaMessage;
import com.ruoyi.system.mapper.TzQaMessageMapper;
import com.ruoyi.system.service.ITzQaMessageService;

/**
 * 农技问答消息 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzQaMessageServiceImpl implements ITzQaMessageService
{
    @Autowired
    private TzQaMessageMapper tzQaMessageMapper;

    /**
     * 查询消息信息
     *
     * @param messageId 消息ID
     * @return 消息信息
     */
    @Override
    public TzQaMessage selectTzQaMessageById(Long messageId)
    {
        return tzQaMessageMapper.selectTzQaMessageById(messageId);
    }

    /**
     * 查询消息列表
     *
     * @param tzQaMessage 消息信息
     * @return 消息集合
     */
    @Override
    public List<TzQaMessage> selectTzQaMessageList(TzQaMessage tzQaMessage)
    {
        return tzQaMessageMapper.selectTzQaMessageList(tzQaMessage);
    }

    /**
     * 新增消息
     *
     * @param tzQaMessage 消息信息
     * @return 结果
     */
    @Override
    public int insertTzQaMessage(TzQaMessage tzQaMessage)
    {
        return tzQaMessageMapper.insertTzQaMessage(tzQaMessage);
    }

    /**
     * 删除消息
     *
     * @param messageId 消息ID
     * @return 结果
     */
    @Override
    public int deleteTzQaMessageById(Long messageId)
    {
        return tzQaMessageMapper.deleteTzQaMessageById(messageId);
    }

    /**
     * 批量删除消息
     *
     * @param messageIds 需要删除的消息ID
     * @return 结果
     */
    @Override
    public int deleteTzQaMessageByIds(Long[] messageIds)
    {
        return tzQaMessageMapper.deleteTzQaMessageByIds(messageIds);
    }

    /**
     * 按会话删除全部消息
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    @Override
    public int deleteTzQaMessageBySessionId(Long sessionId)
    {
        return tzQaMessageMapper.deleteTzQaMessageBySessionId(sessionId);
    }
}
