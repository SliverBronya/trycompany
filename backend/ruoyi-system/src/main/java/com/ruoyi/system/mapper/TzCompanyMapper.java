package com.ruoyi.system.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.TzCompany;

/**
 * 公司 数据层
 *
 * @author tianzhen
 */
public interface TzCompanyMapper
{
    public TzCompany selectTzCompanyById(Long companyId);

    public TzCompany checkCompanyNameUnique(String companyName);

    /** 按用户查他所属的公司（一人一公司，等值即可） */
    public TzCompany selectTzCompanyByUserId(Long userId);

    public int insertTzCompany(TzCompany tzCompany);

    public int updateTzCompany(TzCompany tzCompany);

    /** 成员列表（含岗位/角色/部门） */
    public List<HashMap<String, Object>> selectMembersByCompanyId(Long companyId);

    /** 移出成员：清掉归属，账号保留 */
    public int clearUserCompany(Long userId);

    /** 改成员岗位 */
    public int updateUserDept(@org.apache.ibatis.annotations.Param("userId") Long userId,
                             @org.apache.ibatis.annotations.Param("companyId") Long companyId,
                             @org.apache.ibatis.annotations.Param("deptId") Long deptId);
}
