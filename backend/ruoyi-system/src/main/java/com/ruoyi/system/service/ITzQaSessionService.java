package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.TzQaSession;

/**
 * 农技问答会话 服务层
 *
 * @author tianzhen
 */
public interface ITzQaSessionService
{
    /**
     * 查询会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public TzQaSession selectTzQaSessionById(Long sessionId);

    /**
     * 查询会话列表
     *
     * @param tzQaSession 会话信息
     * @return 会话集合
     */
    public List<TzQaSession> selectTzQaSessionList(TzQaSession tzQaSession);

    /**
     * 新增会话
     *
     * @param tzQaSession 会话信息
     * @return 结果
     */
    public int insertTzQaSession(TzQaSession tzQaSession);

    /**
     * 修改会话
     *
     * @param tzQaSession 会话信息
     * @return 结果
     */
    public int updateTzQaSession(TzQaSession tzQaSession);

    /**
     * 删除会话（连同其下全部消息一起删，不留孤儿数据）
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    public int deleteTzQaSessionById(Long sessionId);

    /**
     * 批量删除会话（连同其下全部消息一起删）
     *
     * @param sessionIds 需要删除的会话ID
     * @return 结果
     */
    public int deleteTzQaSessionByIds(Long[] sessionIds);
}
