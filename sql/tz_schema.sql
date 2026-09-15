-- ----------------------------------------------------------------------------
-- 田诊助手（柑橘版）业务表结构
-- 依赖：先导入 ry_20260417.sql、quartz.sql，并在 ry-vue 库中执行本文件
-- 约定：反引号、IF NOT EXISTS、实体前缀主键、全列 COMMENT、审计列、utf8mb4
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1. 巡田地块表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_plot` (
  `plot_id`       bigint(20)     NOT NULL AUTO_INCREMENT              COMMENT '地块ID',
  `plot_name`     varchar(100)   NOT NULL                             COMMENT '地块名称',
  `crop_type`     varchar(50)    DEFAULT '柑橘'                       COMMENT '作物类型',
  `area`          decimal(10,2)  DEFAULT NULL                         COMMENT '面积（亩）',
  `location`      varchar(255)   DEFAULT NULL                         COMMENT '地块位置',
  `owner_name`    varchar(64)    DEFAULT NULL                         COMMENT '责任人/种植户',
  `plant_year`    int(4)         DEFAULT NULL                         COMMENT '种植年份',
  `status`        char(1)        DEFAULT '0'                          COMMENT '状态（0正常 1停用）',
  `create_by`     varchar(64)    DEFAULT ''                           COMMENT '创建者',
  `create_time`   datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  `update_by`     varchar(64)    DEFAULT ''                           COMMENT '更新者',
  `update_time`   datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`        varchar(500)   DEFAULT NULL                         COMMENT '备注',
  PRIMARY KEY (`plot_id`),
  KEY `idx_plot_name` (`plot_name`),
  KEY `idx_crop_type` (`crop_type`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='巡田地块表';

-- ----------------------------
-- 2. 柑橘植保知识库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_knowledge_base` (
  `knowledge_id`       bigint(20)     NOT NULL AUTO_INCREMENT        COMMENT '知识库ID',
  `crop_type`          varchar(50)    NOT NULL DEFAULT '柑橘'         COMMENT '作物类型',
  `disease_name`       varchar(100)   NOT NULL                        COMMENT '病虫害/缺素名称',
  `category`           char(1)        DEFAULT NULL                    COMMENT '类别（1病害 2虫害 3营养问题）',
  `symptoms`           text                                           COMMENT '症状描述（用于检索匹配）',
  `trigger_conditions` text                                           COMMENT '发生条件',
  `prevention`         text                                           COMMENT '防治措施',
  `medicine_note`      text                                           COMMENT '用药注意（剂量校验的唯一依据）',
  `safety_note`        text                                           COMMENT '安全说明',
  `source`             varchar(255)   NOT NULL                        COMMENT '资料来源（植保站/知网/农技推广网）',
  `status`             char(1)        DEFAULT '0'                     COMMENT '状态（0启用 1停用）',
  `create_by`          varchar(64)    DEFAULT ''                      COMMENT '创建者',
  `create_time`        datetime       DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_by`          varchar(64)    DEFAULT ''                      COMMENT '更新者',
  `update_time`        datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`             varchar(500)   DEFAULT NULL                    COMMENT '备注',
  PRIMARY KEY (`knowledge_id`),
  UNIQUE KEY `uk_crop_disease` (`crop_type`, `disease_name`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='柑橘植保知识库表';

-- ----------------------------
-- 3. 巡田记录表（三、AI诊断与报告链路的主表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_scouting_record` (
  `record_id`            bigint(20)     NOT NULL AUTO_INCREMENT       COMMENT '巡田记录ID',
  `plot_id`              bigint(20)     NOT NULL                      COMMENT '地块ID',
  `scout_time`           datetime       DEFAULT NULL                  COMMENT '巡田时间',
  `image_url`            varchar(500)   DEFAULT NULL                  COMMENT '现场图片访问地址',
  `image_hash`           varchar(64)    DEFAULT NULL                  COMMENT '图片MD5（预置映射匹配用）',
  `plant_part`           varchar(32)    DEFAULT NULL                  COMMENT '发生部位（1叶片 2果实 3枝干 4根部）',
  `symptom_text`         varchar(1000)  DEFAULT NULL                  COMMENT '症状描述',
  `severity`             char(1)        DEFAULT NULL                  COMMENT '严重度（1轻 2中 3重）',
  `diagnosis_name`       varchar(100)   DEFAULT NULL                  COMMENT '诊断病虫害/缺素名称',
  `confidence`           decimal(5,2)   DEFAULT NULL                  COMMENT '置信度（0-100）',
  `risk_level`           char(1)        DEFAULT NULL                  COMMENT '风险等级（1低 2中 3高）',
  `diagnosis_basis`      varchar(1000)  DEFAULT NULL                  COMMENT '判断依据',
  `diagnosis_source`     varchar(20)    DEFAULT NULL                  COMMENT '诊断来源（preset预置 llm大模型 fallback降级 manual人工）',
  `knowledge_id`         bigint(20)     DEFAULT NULL                  COMMENT '命中的知识库条目ID',
  `suggestion`           text                                         COMMENT 'RAG四段式防治建议',
  `suggestion_source`    varchar(500)   DEFAULT NULL                  COMMENT '建议来源标注',
  `dosage_guard_hit`     char(1)        DEFAULT '0'                   COMMENT '剂量校验是否拦截（0未拦截 1已拦截）',
  `report_text`          longtext                                     COMMENT '巡田报告全文',
  `report_time`          datetime       DEFAULT NULL                  COMMENT '报告生成时间',
  `status`               char(1)        DEFAULT '0'                   COMMENT '状态（0待诊断 1已诊断 2已生成报告 3待复查 4已复查）',
  `create_by`            varchar(64)    DEFAULT ''                    COMMENT '创建者',
  `create_time`          datetime       DEFAULT CURRENT_TIMESTAMP     COMMENT '创建时间',
  `update_by`            varchar(64)    DEFAULT ''                    COMMENT '更新者',
  `update_time`          datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`               varchar(500)   DEFAULT NULL                  COMMENT '备注',
  PRIMARY KEY (`record_id`),
  KEY `idx_plot_id` (`plot_id`),
  KEY `idx_status` (`status`),
  KEY `idx_diagnosis_name` (`diagnosis_name`),
  KEY `idx_image_hash` (`image_hash`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='巡田记录表';

-- ----------------------------
-- 4. 复查任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_follow_up_task` (
  `task_id`       bigint(20)     NOT NULL AUTO_INCREMENT              COMMENT '复查任务ID',
  `record_id`     bigint(20)     NOT NULL                             COMMENT '关联巡田记录ID',
  `plot_id`       bigint(20)     DEFAULT NULL                         COMMENT '地块ID（冗余，便于按地块查询）',
  `task_title`    varchar(200)   NOT NULL                             COMMENT '复查任务标题',
  `due_date`      date           DEFAULT NULL                         COMMENT '复查截止日期',
  `status`        char(1)        DEFAULT '0'                          COMMENT '状态（0待复查 1已复查 2已逾期）',
  `note`          varchar(500)   DEFAULT NULL                         COMMENT '复查备注',
  `finish_time`   datetime       DEFAULT NULL                         COMMENT '完成时间',
  `create_by`     varchar(64)    DEFAULT ''                           COMMENT '创建者',
  `create_time`   datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  `update_by`     varchar(64)    DEFAULT ''                           COMMENT '更新者',
  `update_time`   datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`        varchar(500)   DEFAULT NULL                         COMMENT '备注',
  PRIMARY KEY (`task_id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_plot_id` (`plot_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='巡田复查任务表';

-- ----------------------------
-- 5. 预置图片映射表（演示保底路线，PRD 路线A）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_image_preset` (
  `preset_id`        bigint(20)     NOT NULL AUTO_INCREMENT           COMMENT '预置映射ID',
  `image_hash`       varchar(64)    NOT NULL                          COMMENT '图片MD5（小写）',
  `image_name`       varchar(255)   DEFAULT NULL                      COMMENT '示例图文件名',
  `image_url`        varchar(500)   DEFAULT NULL                      COMMENT '示例图访问地址',
  `crop_type`        varchar(50)    DEFAULT '柑橘'                     COMMENT '作物类型',
  `diagnosis_name`   varchar(100)   NOT NULL                          COMMENT '预置诊断名称',
  `symptom_text`     varchar(1000)  DEFAULT NULL                      COMMENT '预置症状描述（离线保底时用于自动填描述）',
  `confidence`       decimal(5,2)   DEFAULT NULL                      COMMENT '预置置信度',
  `risk_level`       char(1)        DEFAULT NULL                      COMMENT '预置风险等级（1低 2中 3高）',
  `diagnosis_basis`  varchar(1000)  DEFAULT NULL                      COMMENT '预置判断依据',
  `knowledge_id`     bigint(20)     DEFAULT NULL                      COMMENT '关联知识库条目ID',
  `status`           char(1)        DEFAULT '0'                       COMMENT '状态（0启用 1停用）',
  `create_by`        varchar(64)    DEFAULT ''                        COMMENT '创建者',
  `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `update_by`        varchar(64)    DEFAULT ''                        COMMENT '更新者',
  `update_time`      datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`           varchar(500)   DEFAULT NULL                      COMMENT '备注',
  PRIMARY KEY (`preset_id`),
  UNIQUE KEY `uk_image_hash` (`image_hash`),
  KEY `idx_diagnosis_name` (`diagnosis_name`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='AI诊断预置图片映射表';

-- ----------------------------
-- 6. AI 调用日志表（可观测性：排查降级、超时、异常）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_ai_call_log` (
  `log_id`         bigint(20)     NOT NULL AUTO_INCREMENT             COMMENT '日志ID',
  `biz_type`       varchar(32)    NOT NULL                            COMMENT '业务类型（diagnose/suggestion/report/qa）',
  `biz_id`         varchar(64)    DEFAULT NULL                        COMMENT '业务主键（如巡田记录ID）',
  `provider`       varchar(64)    DEFAULT NULL                        COMMENT '服务商',
  `model`          varchar(64)    DEFAULT NULL                        COMMENT '模型名',
  `prompt_digest`  varchar(500)   DEFAULT NULL                        COMMENT '提示词摘要（截断，不含密钥）',
  `latency_ms`     int(11)        DEFAULT NULL                        COMMENT '耗时（毫秒）',
  `success`        char(1)        DEFAULT '1'                         COMMENT '是否成功（1成功 0失败）',
  `fallback_used`  char(1)        DEFAULT '0'                         COMMENT '是否降级到预置映射（1是）',
  `error_msg`      varchar(1000)  DEFAULT NULL                        COMMENT '错误信息',
  `create_by`      varchar(64)    DEFAULT ''                          COMMENT '创建者',
  `create_time`    datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';

-- ----------------------------
-- 7. 农技问答会话表（P1）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_qa_session` (
  `session_id`     bigint(20)     NOT NULL AUTO_INCREMENT             COMMENT '会话ID',
  `session_title`  varchar(200)   DEFAULT NULL                        COMMENT '会话标题（取首问前若干字）',
  `user_id`        bigint(20)     DEFAULT NULL                        COMMENT '用户ID',
  `user_name`      varchar(64)    DEFAULT NULL                        COMMENT '用户名',
  `msg_count`      int(11)        DEFAULT '0'                         COMMENT '消息条数',
  `status`         char(1)        DEFAULT '0'                         COMMENT '状态（0正常 1已关闭）',
  `create_by`      varchar(64)    DEFAULT ''                          COMMENT '创建者',
  `create_time`    datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  `update_by`      varchar(64)    DEFAULT ''                          COMMENT '更新者',
  `update_time`    datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`         varchar(500)   DEFAULT NULL                        COMMENT '备注',
  PRIMARY KEY (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='农技问答会话表';

-- ----------------------------
-- 8. 农技问答消息表（P1）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_qa_message` (
  `message_id`     bigint(20)     NOT NULL AUTO_INCREMENT             COMMENT '消息ID',
  `session_id`     bigint(20)     NOT NULL                            COMMENT '会话ID',
  `role`           varchar(16)    NOT NULL                            COMMENT '角色（user/assistant）',
  `content`        text                                               COMMENT '消息内容',
  `image_url`      varchar(500)   DEFAULT NULL                        COMMENT '用户上传图片地址',
  `sources`        varchar(1000)  DEFAULT NULL                        COMMENT 'RAG命中的知识库来源标注',
  `answer_status`  char(1)        DEFAULT '0'                         COMMENT '应答状态（0正常 1无匹配建议咨询农技员 2降级）',
  `create_by`      varchar(64)    DEFAULT ''                          COMMENT '创建者',
  `create_time`    datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  PRIMARY KEY (`message_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='农技问答消息表';

-- ----------------------------
-- 9. 行情价格表（P1，仅展示、标注来源与更新日期，不做交易）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_market_price` (
  `price_id`       bigint(20)     NOT NULL AUTO_INCREMENT             COMMENT '行情ID',
  `crop_type`      varchar(50)    NOT NULL DEFAULT '柑橘'              COMMENT '品种',
  `variety`        varchar(50)    DEFAULT NULL                        COMMENT '细分品种（如沃柑/砂糖橘）',
  `price_type`     char(1)        DEFAULT '1'                         COMMENT '价格类型（1产地价 2批发价）',
  `region`         varchar(100)   DEFAULT NULL                        COMMENT '产区/市场',
  `price`          decimal(10,2)  DEFAULT NULL                        COMMENT '价格（元/斤）',
  `price_unit`     varchar(20)    DEFAULT '元/斤'                      COMMENT '计价单位',
  `change_rate`    decimal(6,2)   DEFAULT NULL                        COMMENT '涨跌幅（%）',
  `price_date`     date           DEFAULT NULL                        COMMENT '行情日期',
  `source`         varchar(255)   NOT NULL                            COMMENT '数据来源（必填，合规要求）',
  `create_by`      varchar(64)    DEFAULT ''                          COMMENT '创建者',
  `create_time`    datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  `update_by`      varchar(64)    DEFAULT ''                          COMMENT '更新者',
  `update_time`    datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`         varchar(500)   DEFAULT NULL                        COMMENT '备注',
  PRIMARY KEY (`price_id`),
  KEY `idx_crop_variety` (`crop_type`, `variety`),
  KEY `idx_price_date` (`price_date`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='农产品行情价格表';

-- ----------------------------
-- 10. 供求信息表（P1，仅撮合线索，不担保交易）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tz_supply_demand` (
  `info_id`        bigint(20)     NOT NULL AUTO_INCREMENT             COMMENT '供求信息ID',
  `info_type`      char(1)        NOT NULL                            COMMENT '信息类型（1供应 2求购）',
  `crop_type`      varchar(50)    NOT NULL DEFAULT '柑橘'              COMMENT '品种',
  `variety`        varchar(50)    DEFAULT NULL                        COMMENT '细分品种',
  `quantity`       decimal(12,2)  DEFAULT NULL                        COMMENT '数量（吨）',
  `price_expect`   varchar(50)    DEFAULT NULL                        COMMENT '期望价格（描述性，如"面议""3元/斤以上"）',
  `region`         varchar(100)   DEFAULT NULL                        COMMENT '产区/所在地',
  `contact_name`   varchar(64)    DEFAULT NULL                        COMMENT '联系人',
  `contact_phone`  varchar(20)    DEFAULT NULL                        COMMENT '联系电话',
  `description`    varchar(1000)  DEFAULT NULL                        COMMENT '详细描述',
  `status`         char(1)        DEFAULT '0'                         COMMENT '状态（0已发布 1已下架 2已成交）',
  `create_by`      varchar(64)    DEFAULT ''                          COMMENT '创建者',
  `create_time`    datetime       DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
  `update_by`      varchar(64)    DEFAULT ''                          COMMENT '更新者',
  `update_time`    datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark`         varchar(500)   DEFAULT NULL                        COMMENT '备注',
  PRIMARY KEY (`info_id`),
  KEY `idx_info_type` (`info_type`),
  KEY `idx_crop_type` (`crop_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='农产品供求信息表';


-- ============================================================================
-- 字典数据
-- 说明：前端 useDict() 依赖 sys_dict_type / sys_dict_data，缺字典会导致下拉框与
--       dict-tag 渲染为空。ID 段 100+/1000+ 避开若依原生小号段。
-- ============================================================================

INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`) VALUES
(100, '作物类型',   'tz_crop_type',      '0', 'admin', sysdate(), '田诊助手-作物类型'),
(101, '发生部位',   'tz_plant_part',     '0', 'admin', sysdate(), '田诊助手-病害发生部位'),
(102, '严重程度',   'tz_severity',       '0', 'admin', sysdate(), '田诊助手-症状严重度'),
(103, '风险等级',   'tz_risk_level',     '0', 'admin', sysdate(), '田诊助手-风险等级'),
(104, '问题类别',   'tz_disease_category','0','admin', sysdate(), '田诊助手-病虫害/缺素类别'),
(105, '巡田状态',   'tz_record_status',  '0', 'admin', sysdate(), '田诊助手-巡田记录状态'),
(106, '复查状态',   'tz_task_status',    '0', 'admin', sysdate(), '田诊助手-复查任务状态'),
(107, '诊断来源',   'tz_diagnosis_source','0','admin', sysdate(), '田诊助手-诊断结果来源'),
(108, '价格类型',   'tz_price_type',     '0', 'admin', sysdate(), '田诊助手-行情价格类型'),
(109, '供求类型',   'tz_info_type',      '0', 'admin', sysdate(), '田诊助手-供求信息类型'),
(110, '供求状态',   'tz_supply_status',  '0', 'admin', sysdate(), '田诊助手-供求信息状态');

INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`) VALUES
-- 作物类型
(1000, 1, '柑橘',   '柑橘',   'tz_crop_type', '', 'info',    'Y', '0', 'admin', sysdate(), ''),
(1001, 2, '芒果',   '芒果',   'tz_crop_type', '', 'info',    'N', '0', 'admin', sysdate(), '架构预留'),
(1002, 3, '水稻',   '水稻',   'tz_crop_type', '', 'info',    'N', '0', 'admin', sysdate(), '架构预留'),
-- 发生部位
(1010, 1, '叶片',   '1', 'tz_plant_part', '', 'primary', 'Y', '0', 'admin', sysdate(), ''),
(1011, 2, '果实',   '2', 'tz_plant_part', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
(1012, 3, '枝干',   '3', 'tz_plant_part', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
(1013, 4, '根部',   '4', 'tz_plant_part', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
-- 严重程度
(1020, 1, '轻度',   '1', 'tz_severity', '', 'success', 'Y', '0', 'admin', sysdate(), '零星发生'),
(1021, 2, '中度',   '2', 'tz_severity', '', 'warning', 'N', '0', 'admin', sysdate(), '已见扩散'),
(1022, 3, '重度',   '3', 'tz_severity', '', 'danger',  'N', '0', 'admin', sysdate(), '成片发生'),
-- 风险等级
(1030, 1, '低风险', '1', 'tz_risk_level', '', 'success', 'Y', '0', 'admin', sysdate(), ''),
(1031, 2, '中风险', '2', 'tz_risk_level', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
(1032, 3, '高风险', '3', 'tz_risk_level', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
-- 问题类别
(1040, 1, '病害',     '1', 'tz_disease_category', '', 'danger',  'Y', '0', 'admin', sysdate(), ''),
(1041, 2, '虫害',     '2', 'tz_disease_category', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
(1042, 3, '营养问题', '3', 'tz_disease_category', '', 'info',    'N', '0', 'admin', sysdate(), '缺素等生理性问题'),
-- 巡田状态
(1050, 1, '待诊断',     '0', 'tz_record_status', '', 'info',    'Y', '0', 'admin', sysdate(), ''),
(1051, 2, '已诊断',     '1', 'tz_record_status', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
(1052, 3, '已生成报告', '2', 'tz_record_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
(1053, 4, '待复查',     '3', 'tz_record_status', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
(1054, 5, '已复查',     '4', 'tz_record_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
-- 复查状态
(1060, 1, '待复查', '0', 'tz_task_status', '', 'warning', 'Y', '0', 'admin', sysdate(), ''),
(1061, 2, '已复查', '1', 'tz_task_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
(1062, 3, '已逾期', '2', 'tz_task_status', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
-- 诊断来源
(1070, 1, '预置映射', 'preset',   'tz_diagnosis_source', '', 'success', 'N', '0', 'admin', sysdate(), '演示保底路线'),
(1071, 2, '大模型',   'llm',      'tz_diagnosis_source', '', 'primary', 'N', '0', 'admin', sysdate(), '多模态大模型初诊'),
(1072, 3, '降级结果', 'fallback', 'tz_diagnosis_source', '', 'warning', 'N', '0', 'admin', sysdate(), '大模型不可用时的降级'),
(1073, 4, '人工录入', 'manual',   'tz_diagnosis_source', '', 'info',    'N', '0', 'admin', sysdate(), ''),
-- 价格类型
(1080, 1, '产地价', '1', 'tz_price_type', '', 'primary', 'Y', '0', 'admin', sysdate(), ''),
(1081, 2, '批发价', '2', 'tz_price_type', '', 'info',    'N', '0', 'admin', sysdate(), ''),
-- 供求类型
(1090, 1, '供应', '1', 'tz_info_type', '', 'success', 'Y', '0', 'admin', sysdate(), ''),
(1091, 2, '求购', '2', 'tz_info_type', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
-- 供求状态
(1100, 1, '已发布', '0', 'tz_supply_status', '', 'success', 'Y', '0', 'admin', sysdate(), ''),
(1101, 2, '已下架', '1', 'tz_supply_status', '', 'info',    'N', '0', 'admin', sysdate(), ''),
(1102, 3, '已成交', '2', 'tz_supply_status', '', 'primary', 'N', '0', 'admin', sysdate(), '');
