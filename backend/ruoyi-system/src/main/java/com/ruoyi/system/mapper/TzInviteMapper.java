package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.TzInvite;

/**
 * 邀请码 数据层
 *
 * @author tianzhen
 */
public interface TzInviteMapper
{
    public TzInvite selectTzInviteById(Long inviteId);

    public TzInvite selectTzInviteByCode(String code);

    public List<TzInvite> selectTzInviteList(TzInvite tzInvite);

    public int insertTzInvite(TzInvite tzInvite);

    /** 用掉一次：次数与状态原子更新，避免「查了再改」之间被并发用掉 */
    public int consumeTzInvite(@Param("inviteId") Long inviteId, @Param("userId") Long userId);

    public int cancelTzInvite(Long inviteId);
}
