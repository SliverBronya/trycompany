package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.domain.TzQaSession;
import com.ruoyi.system.mapper.TzQaMessageMapper;
import com.ruoyi.system.mapper.TzQaSessionMapper;
import com.ruoyi.system.service.ITzQaSessionService;

/**
 * 农技问答会话 服务层实现
 *
 * @author tianzhen
 */
@Service
public class TzQaSessionServiceImpl implements ITzQaSessionService
{
    @Autowired
    private TzQaSessionMapper tzQaSessionMapper;

    @Autowired
    private TzQaMessageMapper tzQaMessageMapper;

    /**
     * 查询会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    @Override
    public TzQaSession selectTzQaSessionById(Long sessionId)
    {
        return tzQaSessionMapper.selectTzQaSessionById(sessionId);
    }

    /**
     * 查询会话列表
     *
     * @param tzQaSession 会话信息
     * @return 会话集合
     */
    @Override
    public List<TzQaSession> selectTzQaSessionList(TzQaSession tzQaSession)
    {
        return tzQaSessionMapper.selectTzQaSessionList(tzQaSession);
    }

    /**
     * 新增会话
     *
     * @param tzQaSession 会话信息
     * @return 结果
     */
    @Override
    public int insertTzQaSession(TzQaSession tzQaSession)
    {
        return tzQaSessionMapper.insertTzQaSession(tzQaSession);
    }

    /**
     * 修改会话
     *
     * @param tzQaSession 会话信息
     * @return 结果
     */
    @Override
    public int updateTzQaSession(TzQaSession tzQaSession)
    {
        return tzQaSessionMapper.updateTzQaSession(tzQaSession);
    }

    /**
     * 删除会话（连同其下全部消息一起删）
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteTzQaSessionById(Long sessionId)
    {
        tzQaMessageMapper.deleteTzQaMessageBySessionId(sessionId);
        return tzQaSessionMapper.deleteTzQaSessionById(sessionId);
    }

    /**
     * 批量删除会话（连同其下全部消息一起删）
     *
     * @param sessionIds 需要删除的会话ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteTzQaSessionByIds(Long[] sessionIds)
    {
        for (Long sessionId : sessionIds)
        {
            tzQaMessageMapper.deleteTzQaMessageBySessionId(sessionId);
        }
        return tzQaSessionMapper.deleteTzQaSessionByIds(sessionIds);
    }
}
