-- ============================================================================
-- 田诊助手｜公司管理员角色（多公司权限 B 阶段配套）
-- ============================================================================
--
-- 公司管理员要能：看本公司全部数据 + 管成员 + 发邀请码。
-- 菜单权限直接复制「普通角色」的那一套（田诊全部功能），
-- 省得菜单加一个就得两个角色同步一次——两个角色的差异只在数据归属，不在菜单。
--
-- 可重复执行：先查再插。
-- ============================================================================

INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
SELECT 3, '公司管理员', 'company_admin', 3, '4', 1, 1, '0', '0', 'system', NOW(),
       '公司创建者自动获得。data_scope=4（本部门及以下），配合公司=顶层部门的设计，正好覆盖全公司'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_id = 3);

-- 菜单权限与普通角色一致（查不到就跳过，兼容 role 2 尚未授权的极端情况）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id FROM sys_role_menu m
WHERE m.role_id = 2
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = 3 AND x.menu_id = m.menu_id);

-- 公司管理员专属：清掉「仅本人」的默认限制，让 data_scope=4 生效
UPDATE sys_role SET data_scope = '4' WHERE role_id = 3 AND data_scope <> '4';
