package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.TzQaSession;

/**
 * 农技问答会话 数据层
 *
 * @author tianzhen
 */
public interface TzQaSessionMapper
{
    /**
     * 查询会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    public TzQaSession selectTzQaSessionById(@Param("sessionId") Long sessionId,
                                              @Param("companyId") Long companyId);

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
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 结果
     */
    public int deleteTzQaSessionById(@Param("sessionId") Long sessionId,
                                     @Param("companyId") Long companyId);

    /**
     * 批量删除会话
     *
     * @param sessionIds 需要删除的会话ID
     * @return 结果
     */
    public int deleteTzQaSessionByIds(@Param("sessionIds") Long[] sessionIds,
                                      @Param("companyId") Long companyId);

    /**
     * 消息条数累加。
     *
     * <p>用 SQL 自增而不是「查出来加一再写回」：同一会话连续追问时，
     * 读改写会把两次提问的计数覆盖成同一个值。
     *
     * <p>要带 delta 是因为一次提问落库的是两条消息（问 + 答）。原先固定 +1，
     * 于是一个 4 条消息的会话界面上显示「2 条」—— 计数单位是「轮」，
     * 而界面上写的是「条」。现在由写消息的那处按条数累加，单位对齐。
     *
     * @param sessionId 会话ID
     * @param delta     本次新增的消息条数
     * @return 结果
     */
    public int increaseMsgCount(@Param("sessionId") Long sessionId, @Param("delta") int delta);
}
