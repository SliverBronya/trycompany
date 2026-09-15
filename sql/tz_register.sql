-- 开放自助注册（田诊助手）
--
-- 做三件事：
--   1. 打开注册开关。上游 seed（backend/sql/ry_20260417.sql 第 552 行）里这一项是 false。
--   2. 新增「注册用户默认角色」配置项。上游若依注册出来的账号是**没有任何角色**的：
--      能登录，但菜单和按钮全空，等于注册了个寂寞。默认给 role_id=2
--      （「普通角色」，已被收敛为「田诊助手全功能、无后台管理」，不含任何系统管理权限）。
--   3. 可重复执行（先删后插 / UPDATE 幂等）。
--
-- 执行（在仓库根目录）：
--   C:\RuoYi\mysql-8.0\bin\mysql.exe --host=127.0.0.1 --port=13306 --user=root --password=*** ^
--       --database=ry-vue < sql\tz_register.sql
--
-- 执行完**必须**清 Redis 缓存，否则后端读到的还是旧值（参数值是缓存在 Redis 里的）：
--   C:\RuoYi\redis\redis-cli.exe -p 16379 DEL "sys_config:sys.account.registerUser" "sys_config:sys.account.registerRoleId"
--
-- 要收回注册能力（例如演示结束）：把 sys.account.registerUser 改回 'false' 并清缓存即可，
-- 前端登录页的「立即注册」会自动消失，后端 /register 也会直接拒绝。

SET NAMES utf8mb4;

-- 1. 打开注册开关
UPDATE sys_config
   SET config_value = 'true',
       update_time  = NOW()
 WHERE config_key = 'sys.account.registerUser';

-- 2. 注册用户的默认角色
DELETE FROM sys_config WHERE config_key = 'sys.account.registerRoleId';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
VALUES ('账号自助-注册用户默认角色', 'sys.account.registerRoleId', '2', 'Y', 'admin', NOW(),
        '注册用户自动授予的角色ID，多个用逗号分隔；留空则不授角色（即回到上游若依行为）');

-- 3. 回显确认
SELECT config_key, config_value FROM sys_config
 WHERE config_key IN ('sys.account.registerUser', 'sys.account.registerRoleId');
