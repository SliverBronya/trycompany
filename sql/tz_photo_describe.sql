-- ============================================================================
-- 田诊助手｜照片自动生成「症状描述」—— 数据库变更
-- ============================================================================
--
-- 背景
--   巡田记录原来强制人工填写「症状描述」才允许保存。改成「照片必填、描述由 AI
--   看照片自动生成、用户可再改」之后，需要一处让「观察记录」脱离大模型也能落地的
--   位置：没配 TZ_AI_API_KEY 时（演示默认就是这种状态），已登记的演示样张必须
--   照样能把描述填出来，否则这个功能在离线演示里就是空的。
--
-- 变更内容
--   1. tz_image_preset 增加 symptom_text 列 —— 预置的「照片上看到了什么」。
--      它和 diagnosis_basis 是两件事：basis 回答「为什么是这个结论」，
--      symptom_text 回答「看到了什么」，只有后者能拿去填症状描述。
--   2. 回填三条演示样张的描述。文案直接取自 seed-demo.py 里登记这三张图时用的
--      症状文本，两处保持一致，避免「脚本新建的和库里已有的是两份不一样的话」。
--
-- 为什么用 symptom_text 而不是复用 diagnosis_basis
--   basis 的原文是「演示示意图：……，与知识库溃疡病条目描述一致。」——把这种带
--   结论与来源交代的句子直接当症状描述填进表单，用户看到的就是一句结论，
--   而不是观察记录，检索也会被无关词干扰。
--
-- 本脚本可重复执行。要收回：DROP COLUMN symptom_text 即可（新代码会退化成
-- 只走大模型生成，未配 key 时提示用户自行填写）。
--
-- 用法：
--   C:\RuoYi\mysql-8.0\bin\mysql.exe --host=127.0.0.1 --port=13306 ^
--     --user=root --password=Root@123456 --database=ry-vue ^
--     --default-character-set=utf8mb4 < sql\tz_photo_describe.sql
-- ============================================================================

-- ---------------------------------------------------------------- 1. 加列
-- MySQL 8 的 ALTER TABLE 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema
-- 判一下再动态执行。这样重复运行不会因为「Duplicate column name」中断脚本 ——
-- 上面那条 mysql 命令是整体投喂的，中途报错会让后面的回填静默跳过。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'tz_image_preset'
    AND COLUMN_NAME  = 'symptom_text'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `tz_image_preset` ADD COLUMN `symptom_text` varchar(1000) DEFAULT NULL COMMENT ''预置症状描述（离线保底时用于自动填描述）'' AFTER `diagnosis_name`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 2. 回填演示样张
-- 只补空值，不覆盖已有内容：运营若在界面上改过描述，重跑本脚本不该把它冲掉。
UPDATE `tz_image_preset`
SET `symptom_text` = '叶片正反面均有近圆形病斑，中央呈木栓化隆起并开裂，病斑周围有黄色晕圈，叶背病斑隆起更明显。近期连续阴雨后转晴，果园通风一般。'
WHERE `image_name` = 'demo-ulcer.png'
  AND (`symptom_text` IS NULL OR `symptom_text` = '');

UPDATE `tz_image_preset`
SET `symptom_text` = '叶面密布针尖大小的灰白色失绿斑点，远看整片叶呈灰黄色；翻看叶背可见细小的红色虫体与少量蛛丝。近半月高温干旱未降雨。'
WHERE `image_name` = 'demo-mite.png'
  AND (`symptom_text` IS NULL OR `symptom_text` = '');

UPDATE `tz_image_preset`
SET `symptom_text` = '中下部老叶的叶脉间发黄，呈典型的倒 V 形黄化，叶脉及附近组织仍为绿色；新叶基本正常。果园为砂质土，往年未补过镁肥。'
WHERE `image_name` = 'demo-mg.png'
  AND (`symptom_text` IS NULL OR `symptom_text` = '');

-- ---------------------------------------------------------------- 3. 结果
SELECT preset_id, image_name, diagnosis_name,
       CASE WHEN symptom_text IS NULL OR symptom_text = '' THEN '(空)' ELSE LEFT(symptom_text, 30) END AS symptom_head
FROM tz_image_preset
ORDER BY preset_id;
