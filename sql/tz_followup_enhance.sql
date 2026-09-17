-- ---------------------------------------------------------------
-- 复查任务增强：让「完成复查」留下有用的信息
--
-- 背景：原表只有 status（0待复查 / 1已复查），点「完成复查」只是把状态从 0 改成 1，
-- 不记录任何观察结果。于是复查这个动作**没有产出**：过几天回来看了一眼，
-- 系统里留不下"病斑扩大了还是缩小了""上次打的药有没有用"，
-- 下次遇到同类情况也翻不出可参考的历史。这就是"功能单一、只有是或否"的根源。
--
-- 本次补上复查现场真正该记的四件事：
--   review_result  这次看到的病害发展趋势（对比上次诊断）
--   review_image   复查时拍的照片，可与原诊断照片对照
--   measure_taken  两次巡田之间实际做了什么处理
--   next_action    接下来怎么安排（含下次复查日期）
--
-- 幂等：本脚本可重复执行，已存在的列会跳过，不会报 duplicate column。
-- 用法：mysql --host=127.0.0.1 --port=13306 -uroot -p ry-vue < sql/tz_followup_enhance.sql
-- ---------------------------------------------------------------

DROP PROCEDURE IF EXISTS tz_enhance_followup_task;

DELIMITER $$

CREATE PROCEDURE tz_enhance_followup_task()
BEGIN
    DECLARE db VARCHAR(64);
    SET db = DATABASE();

    -- 复查结论：病害相对上次诊断的发展趋势
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = db AND TABLE_NAME = 'tz_follow_up_task'
                     AND COLUMN_NAME = 'review_result') THEN
        ALTER TABLE `tz_follow_up_task`
            ADD COLUMN `review_result` varchar(20) DEFAULT NULL
            COMMENT '复查结论（controlled已控制/shrinking好转/stable持平/worsening加重/unknown无法判断）'
            AFTER `status`;
    END IF;

    -- 复查现场照片
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = db AND TABLE_NAME = 'tz_follow_up_task'
                     AND COLUMN_NAME = 'review_image') THEN
        ALTER TABLE `tz_follow_up_task`
            ADD COLUMN `review_image` varchar(500) DEFAULT NULL
            COMMENT '复查现场照片路径（与巡田记录一样，存 /profile/... 相对路径）'
            AFTER `review_result`;
    END IF;

    -- 两次巡田之间实际采取的措施
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = db AND TABLE_NAME = 'tz_follow_up_task'
                     AND COLUMN_NAME = 'measure_taken') THEN
        ALTER TABLE `tz_follow_up_task`
            ADD COLUMN `measure_taken` varchar(500) DEFAULT NULL
            COMMENT '复查前已采取的措施（用了什么药、怎么处理的），用于复盘防治有效性'
            AFTER `review_image`;
    END IF;

    -- 后续安排
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = db AND TABLE_NAME = 'tz_follow_up_task'
                     AND COLUMN_NAME = 'next_action') THEN
        ALTER TABLE `tz_follow_up_task`
            ADD COLUMN `next_action` varchar(20) DEFAULT NULL
            COMMENT '后续安排（none无需处理/observe继续观察/recheck需再复查/treat需再施药/expert请专家现场查看）'
            AFTER `measure_taken`;
    END IF;

    -- 下次复查日期
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = db AND TABLE_NAME = 'tz_follow_up_task'
                     AND COLUMN_NAME = 'next_date') THEN
        ALTER TABLE `tz_follow_up_task`
            ADD COLUMN `next_date` date DEFAULT NULL
            COMMENT '下次复查日期（next_action 为 recheck 时填写）'
            AFTER `next_action`;
    END IF;
END$$

DELIMITER ;

CALL tz_enhance_followup_task();

DROP PROCEDURE tz_enhance_followup_task;

-- 查一下结果
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tz_follow_up_task'
ORDER BY ORDINAL_POSITION;
