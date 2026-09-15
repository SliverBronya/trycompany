-- ----------------------------------------------------------------------------
-- 田诊助手 菜单与权限
-- 前置：已导入 ry_20260417.sql 与 tz_schema.sql
-- 说明：
--   1. menu_id 段 2000-2199 专供本项目，避开若依原生已用的 1-1060。
--   2. 每个前端 v-hasPermi 用到的权限串都必须有对应 F 行，
--      否则 hasPermi 指令会把按钮节点直接从 DOM 移除（不是隐藏）。
--   3. component 写 views/ 下的相对路径、不带 .vue，如 tianzhen/plot/index。
--   4. 本脚本可重复执行（先按段删除再插入）。
-- ----------------------------------------------------------------------------

-- 幂等：清掉旧的本项目菜单与其角色关联
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 2000 AND 2199;
DELETE FROM `sys_menu`      WHERE `menu_id` BETWEEN 2000 AND 2199;

-- ----------------------------
-- 一级目录：田诊助手（PC 后台，P0）
-- ----------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2000, '田诊助手', 0, 1, 'tz', NULL, '', '', 1, 0, 'M', '0', '0', '', 'guide', 'admin', sysdate(), '', NULL, '田诊助手-植保巡田目录'),
(2001, '首页看板', 2000, 1, 'dashboard', 'tianzhen/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'tz:dashboard:list', 'dashboard', 'admin', sysdate(), '', NULL, '首页数据看板'),
(2002, '地块管理', 2000, 2, 'plot', 'tianzhen/plot/index', '', '', 1, 0, 'C', '0', '0', 'tz:plot:list', 'tree', 'admin', sysdate(), '', NULL, '巡田地块管理'),
(2003, '巡田记录', 2000, 3, 'record', 'tianzhen/record/index', '', '', 1, 0, 'C', '0', '0', 'tz:record:list', 'list', 'admin', sysdate(), '', NULL, '巡田记录与AI诊断'),
(2004, '复查任务', 2000, 4, 'followup', 'tianzhen/followup/index', '', '', 1, 0, 'C', '0', '0', 'tz:followup:list', 'time', 'admin', sysdate(), '', NULL, '巡田复查任务'),
(2005, '植保知识库', 2000, 5, 'knowledge', 'tianzhen/knowledge/index', '', '', 1, 0, 'C', '0', '0', 'tz:knowledge:list', 'education', 'admin', sysdate(), '', NULL, '柑橘植保知识库');

-- ----------------------------
-- 一级目录：移动端（P1，H5）
-- ----------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2010, '移动端', 0, 2, 'h5', NULL, '', '', 1, 0, 'M', '0', '0', '', 'phone', 'admin', sysdate(), '', NULL, '面向农户的移动端入口'),
(2011, '农技问答', 2010, 1, 'qa', 'tianzhen/qa/index', '', '', 1, 0, 'C', '0', '0', 'tz:qa:list', 'message', 'admin', sysdate(), '', NULL, '农技问答与产销助手'),
(2012, '行情查询', 2010, 2, 'market', 'tianzhen/market/index', '', '', 1, 0, 'C', '0', '0', 'tz:market:list', 'chart', 'admin', sysdate(), '', NULL, '柑橘行情查询'),
(2013, '供求发布', 2010, 3, 'supply', 'tianzhen/supply/index', '', '', 1, 0, 'C', '0', '0', 'tz:supply:list', 'shopping', 'admin', sysdate(), '', NULL, '供求信息发布与撮合');

-- ----------------------------
-- 按钮权限（F）：首页看板
-- ----------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2006, '看板查询', 2001, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:dashboard:query', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限（F）：地块管理
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2021, '地块查询', 2002, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:plot:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2022, '地块新增', 2002, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:plot:add',    '#', 'admin', sysdate(), '', NULL, ''),
(2023, '地块修改', 2002, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:plot:edit',   '#', 'admin', sysdate(), '', NULL, ''),
(2024, '地块删除', 2002, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:plot:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2025, '地块导出', 2002, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:plot:export', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限（F）：巡田记录
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2031, '记录查询', 2003, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:query',    '#', 'admin', sysdate(), '', NULL, ''),
(2032, '记录新增', 2003, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:add',      '#', 'admin', sysdate(), '', NULL, ''),
(2033, '记录修改', 2003, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:edit',     '#', 'admin', sysdate(), '', NULL, ''),
(2034, '记录删除', 2003, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:remove',   '#', 'admin', sysdate(), '', NULL, ''),
(2035, 'AI诊断',   2003, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:diagnose', '#', 'admin', sysdate(), '', NULL, '触发AI病虫害诊断'),
(2036, '生成报告', 2003, 6, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:report',   '#', 'admin', sysdate(), '', NULL, '生成巡田报告与复查任务'),
(2037, '记录导出', 2003, 7, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:record:export',   '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限（F）：复查任务
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2041, '复查查询', 2004, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:followup:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2042, '复查修改', 2004, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:followup:edit',   '#', 'admin', sysdate(), '', NULL, ''),
(2043, '复查删除', 2004, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:followup:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2044, '复查完成', 2004, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:followup:finish', '#', 'admin', sysdate(), '', NULL, '标记复查完成'),
(2045, '复查导出', 2004, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:followup:export', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限（F）：植保知识库
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2051, '知识查询', 2005, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:knowledge:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2052, '知识新增', 2005, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:knowledge:add',    '#', 'admin', sysdate(), '', NULL, ''),
(2053, '知识修改', 2005, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:knowledge:edit',   '#', 'admin', sysdate(), '', NULL, ''),
(2054, '知识删除', 2005, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:knowledge:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2055, '知识导出', 2005, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:knowledge:export', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限（F）：移动端
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES
(2111, '问答查询', 2011, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:qa:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2112, '问答提问', 2011, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:qa:add',    '#', 'admin', sysdate(), '', NULL, ''),
(2113, '问答删除', 2011, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:qa:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2121, '行情查询', 2012, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:market:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2122, '行情新增', 2012, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:market:add',    '#', 'admin', sysdate(), '', NULL, ''),
(2123, '行情修改', 2012, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:market:edit',   '#', 'admin', sysdate(), '', NULL, ''),
(2124, '行情删除', 2012, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:market:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2125, '行情导出', 2012, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:market:export', '#', 'admin', sysdate(), '', NULL, ''),
(2131, '供求查询', 2013, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:supply:query',  '#', 'admin', sysdate(), '', NULL, ''),
(2132, '供求新增', 2013, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:supply:add',    '#', 'admin', sysdate(), '', NULL, ''),
(2133, '供求修改', 2013, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:supply:edit',   '#', 'admin', sysdate(), '', NULL, ''),
(2134, '供求删除', 2013, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:supply:remove', '#', 'admin', sysdate(), '', NULL, ''),
(2135, '供求导出', 2013, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'tz:supply:export', '#', 'admin', sysdate(), '', NULL, '');

-- ----------------------------
-- 角色授权
-- admin 用户（user_id=1）在若依中通过 isAdmin 直接可见全部菜单，
-- 但为保证普通角色可用、以及便于通过 UI 分配，显式授予两个角色。
-- ----------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 2000 AND 2199;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 2000 AND 2199;
