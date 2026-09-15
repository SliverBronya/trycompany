-- ============================================================================
-- 田诊助手｜左侧菜单重组 —— 数据库变更
-- ============================================================================
--
-- 目标结构（全部平铺在顶层，不再有「田诊助手」「移动端」两层目录）：
--
--   1. 田诊助手首页   ← 原来叫「首页」，是前端常量路由（改 router/index.js 里的 meta.title）
--   2. 统计看板       ← 原「首页看板」，只改名
--   3. 地块管理
--   4. 巡田记录
--   5. 复查任务
--   6. 植保知识库
--   7. 农技问答       ← 从「移动端」目录下提上来
--
--   行情发布 / 供求发布  → 暂时关闭（visible=1 隐藏，菜单行与权限串保留）
--
-- 为什么「关闭」用 visible=1 而不是删菜单行或把 status 置 1：
--   ① 权限串必须留在库里。项目约定「权限串必须与菜单 SQL 同名同量」，
--      test-api.py 的 case_perms 会交叉核对每个业务权限串都有对应菜单行；
--      删了行，页面里的 v-hasPermi 按钮会被整块从 DOM 摘掉，而且查不出来。
--   ② 只是「暂时」关闭。visible=1 只影响侧边栏显示，路由仍在，
--      想恢复把 visible 改回 0 即可，不用改代码。
--
-- 本脚本可重复执行。
--
-- 用法：
--   C:\RuoYi\mysql-8.0\bin\mysql.exe --host=127.0.0.1 --port=13306 ^
--     --user=root --password=Root@123456 --database=ry-vue ^
--     --default-character-set=utf8mb4 < sql\tz_menu_flatten.sql
-- ============================================================================

-- ---------------------------------------------------------------- 1. 业务菜单上移到顶层
-- 原「首页看板」→「统计看板」
UPDATE `sys_menu` SET `menu_name`='统计看板', `parent_id`=0, `order_num`=1, `icon`='chart'
WHERE `menu_id`=2001;

UPDATE `sys_menu` SET `parent_id`=0, `order_num`=2 WHERE `menu_id`=2002;   -- 地块管理
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=3 WHERE `menu_id`=2003;   -- 巡田记录
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=4 WHERE `menu_id`=2004;   -- 复查任务
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=5 WHERE `menu_id`=2005;   -- 植保知识库

-- 农技问答从「移动端」目录提上来
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=6, `icon`='message'
WHERE `menu_id`=2011;

-- 三个系统目录挪到业务菜单之后。
-- 不挪不行：原来它们是 1/2/3，与业务菜单撞号（系统监控=2、统计看板也是 2），
-- 而并列时的先后由插入顺序决定，侧边栏里就会出现「统计看板夹在系统监控和系统工具之间」
-- 这种既难看又解释不了的排列。
UPDATE `sys_menu` SET `order_num`=10 WHERE `menu_id`=1;   -- 系统管理
UPDATE `sys_menu` SET `order_num`=11 WHERE `menu_id`=2;   -- 系统监控
UPDATE `sys_menu` SET `order_num`=12 WHERE `menu_id`=3;   -- 系统工具

-- ---------------------------------------------------------------- 2. 行情与供求暂时关闭
-- parent_id 也要一并挂到顶层：它们的父目录「移动端」在下一步会被删掉，
-- 留着 2010 就成了孤儿行 —— 侧边栏反正是看不见了，但想把 visible 改回 0 恢复时，
-- 会发现自己根本没地方出现，而这件事从数据上完全看不出来。
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=7, `visible`='1' WHERE `menu_id`=2012;
UPDATE `sys_menu` SET `parent_id`=0, `order_num`=8, `visible`='1' WHERE `menu_id`=2013;

-- ---------------------------------------------------------------- 3. 清掉若依残留外链
-- 「若依官网」指向 http://ruoyi.vip，与田诊助手无关，和之前从导航栏摘掉的
-- 「源码地址」「文档地址」是同一类残留。它是外链菜单（menu_type=M + http 地址），
-- 藏在侧边栏里点一下就跳走，演示时很容易误触。
UPDATE `sys_menu` SET `visible`='1' WHERE `menu_id`=4;

-- ---------------------------------------------------------------- 4. 删掉两层目录
-- 只删目录本身；子菜单已经在上面挂到顶层了。
-- 先删角色授权行，避免留下指向不存在菜单的孤儿授权。
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (2000, 2010);
DELETE FROM `sys_menu`      WHERE `menu_id` IN (2000, 2010);

-- ---------------------------------------------------------------- 5. 结果
SELECT m.menu_id, m.parent_id, m.menu_name, m.menu_type, m.path, m.visible, m.order_num,
       (SELECT COUNT(*) FROM sys_role_menu rm WHERE rm.menu_id=m.menu_id AND rm.role_id=2) AS granted_to_common
FROM `sys_menu` m
WHERE m.parent_id=0 OR m.menu_id IN (2001,2002,2003,2004,2005,2011,2012,2013)
ORDER BY m.parent_id, m.order_num, m.menu_id;
