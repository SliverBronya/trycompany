-- ============================================================================
-- 田诊助手｜多公司 / 部门 / 岗位权限 —— 数据归属改造（A 阶段）
-- ============================================================================
--
-- 前置：已导入 tz_schema.sql
-- 可重复执行：建表用 IF NOT EXISTS；加列用下面的存储过程绕开
--             MySQL 不支持的 "ADD COLUMN IF NOT EXISTS"；回填用 COALESCE 兜底。
--
-- 设计要点（三个"不同"分别靠什么实现）：
--   公司隔离   业务表加 company_id + dept_id，查询按当前用户所属公司过滤
--   部门权限   复用若依的 sys_dept 树 + @DataScope（原生能力，不必重造）
--   岗位权限   sys_post 绑不同的 sys_role（原生能力）
--
-- company_id 与 dept_id 的分工：
--   company_id → tz_company.company_id，表示"属于哪家公司"，用于等值过滤
--   dept_id    → sys_dept.dept_id，表示"属于哪个部门"，给若依的 @DataScope 用
--   两者都要：只有 dept_id 的话，跨部门统计要递归查树；只有 company_id 的话，
--   若依那套 data_scope 机制就接不上，部门级权限得全部自己写。
-- ============================================================================

-- ---------------------------------------------------------------- 1. 公司表
-- 为什么不直接复用 sys_dept：公司要存联系人、规模、认证状态、到期时间，
-- sys_dept 只有 name/phone/leader 这些。但公司**对应**一个顶层 sys_dept 节点，
-- 这样部门树天然挂在公司下面，若依的 data_scope 才能顺着树往下算。
CREATE TABLE IF NOT EXISTS tz_company (
  company_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公司ID',
  company_name   VARCHAR(100) NOT NULL                COMMENT '公司名称',
  dept_id        BIGINT       DEFAULT NULL            COMMENT '对应的顶层部门ID（sys_dept.dept_id）',
  contact_name   VARCHAR(50)  DEFAULT NULL            COMMENT '联系人',
  contact_phone  VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
  scale          VARCHAR(30)  DEFAULT NULL            COMMENT '种植规模，如 500亩',
  address        VARCHAR(200) DEFAULT NULL            COMMENT '所在地',
  owner_user_id  BIGINT       DEFAULT NULL            COMMENT '创建者（自动成为公司管理员）',
  status         CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '状态（0正常 1停用）',
  expire_time    DATETIME     DEFAULT NULL            COMMENT '服务到期时间（可选，留空表示不限）',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (company_id),
  KEY idx_tz_company_dept (dept_id),
  UNIQUE KEY uk_tz_company_name (company_name)
) ENGINE=InnoDB AUTO_INCREMENT=100 COMMENT='公司（多租户主体）';

-- ---------------------------------------------------------------- 2. 邀请表
CREATE TABLE IF NOT EXISTS tz_invite (
  invite_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '邀请ID',
  company_id     BIGINT       NOT NULL                COMMENT '目标公司',
  code           VARCHAR(32)  NOT NULL                COMMENT '邀请码（发给对方的字符串）',
  dept_id        BIGINT       DEFAULT NULL            COMMENT '加入后所属部门',
  post_id        BIGINT       DEFAULT NULL            COMMENT '加入后所属岗位',
  role_id        BIGINT       DEFAULT NULL            COMMENT '加入后授予的角色',
  expire_time    DATETIME     DEFAULT NULL            COMMENT '过期时间（NULL 表示长期有效）',
  max_uses       INT          NOT NULL DEFAULT 1      COMMENT '最多可用次数',
  used_count     INT          NOT NULL DEFAULT 0      COMMENT '已使用次数',
  status         CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '状态（0有效 1已用完 2已作废）',
  invitee_user_id BIGINT      DEFAULT NULL            COMMENT '实际接受邀请的用户',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '发起人',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (invite_id),
  UNIQUE KEY uk_tz_invite_code (code),
  KEY idx_tz_invite_company (company_id)
) ENGINE=InnoDB COMMENT='邀请码';

-- ---------------------------------------------------------------- 3. 加列工具
-- MySQL 没有 "ADD COLUMN IF NOT EXISTS"，直接用 ALTER 第二次跑会报错。
-- 用存储过程先查 information_schema 再决定要不要加，脚本才能重复执行。
DROP PROCEDURE IF EXISTS tz_add_col;
DELIMITER $$
CREATE PROCEDURE tz_add_col(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_col
  ) THEN
    SET @s = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_ddl);
    PREPARE st FROM @s;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------- 4. 业务表加归属字段
CALL tz_add_col('tz_plot',            'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER plot_id');
CALL tz_add_col('tz_plot',            'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_scouting_record', 'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER record_id');
CALL tz_add_col('tz_scouting_record', 'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_follow_up_task',  'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER task_id');
CALL tz_add_col('tz_follow_up_task',  'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_qa_session',      'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER session_id');
CALL tz_add_col('tz_qa_session',      'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_market_price',    'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER price_id');
CALL tz_add_col('tz_market_price',    'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_supply_demand',   'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER info_id');
CALL tz_add_col('tz_supply_demand',   'dept_id',    'dept_id BIGINT DEFAULT NULL COMMENT ''归属部门'' AFTER company_id');
CALL tz_add_col('tz_ai_call_log',     'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''归属公司'' AFTER log_id');

-- 用户归属公司（一人一公司，所以直接挂在 sys_user 上，不用中间表）
CALL tz_add_col('sys_user',           'company_id', 'company_id BIGINT DEFAULT NULL COMMENT ''所属公司（NULL=尚未加入任何公司）'' AFTER dept_id');

DROP PROCEDURE IF EXISTS tz_add_col;

-- 知识库、预置样张**不加**：它们是平台级共享数据，隔离了反而每个公司都要重录一遍。

-- ---------------------------------------------------------------- 5. 默认公司
-- 存量数据必须有归属，否则改完老用户什么都看不到。
-- 直接沿用若依自带的顶层部门（100 若依科技），这样部门树不用动。
INSERT INTO tz_company (company_id, company_name, dept_id, contact_name, scale, status, create_by, create_time, remark)
SELECT 1, '默认公司', 100, '系统管理员', '', '0', 'system', NOW(), '存量数据迁移时自动创建，可改名'
WHERE NOT EXISTS (SELECT 1 FROM tz_company WHERE company_id = 1);

-- ---------------------------------------------------------------- 6. 回填存量数据
-- dept_id 取"创建人所在部门"，取不到就落到顶层部门 100，保证不为空。
-- 用 create_by（若依存的是登录名）反查 sys_user，比写死一个部门更贴近真实归属。
UPDATE tz_plot t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_scouting_record t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_follow_up_task t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_qa_session t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_market_price t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_supply_demand t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1, t.dept_id = COALESCE(u.dept_id, 100)
WHERE t.company_id IS NULL OR t.dept_id IS NULL;

UPDATE tz_ai_call_log t
  LEFT JOIN sys_user u ON u.user_name = t.create_by
SET t.company_id = 1
WHERE t.company_id IS NULL;

-- --------------------------- 现有用户全部划到默认公司。
-- 不做这一步，老用户登录后会因为 company_id 为空而看不到任何数据 ——
-- 那是个"升级后系统坏了"的观感，而根因只是一次遗漏的回填。
UPDATE sys_user SET company_id = 1 WHERE company_id IS NULL;
