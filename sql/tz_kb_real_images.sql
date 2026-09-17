-- ---------------------------------------------------------------
-- 知识库配图：用真实病害照片替换程序生成的示意图
--
-- 背景：知识库 21 个条目里，只有 3 个有图，而且都是 seed 脚本生成的
-- 示意图（demo-*.png），其余 18 个为空。手机上打开条目看不到任何参考图，
-- 「看图对症状」这件事就无从谈起 —— 而这是知识库最主要的用法。
--
-- 图片来源：**复用项目里已有的巡田记录照片**，不是从网上新找的。
-- 理由有三：
--   1. 准确性可控。网上按关键词搜到的图，很可能挂着"柑橘溃疡病"的名字
--      实际是别的病，而溃疡病与疮痂病本来就极易混；在植保场景里配错图
--      比没有图更糟，会直接诱导误判。
--   2. 无版权风险。
--   3. 本机网络无法访问境外图床（commons.wikimedia.org / google 均不通），
--      客观上也只能用已有素材。
--
-- 这 5 张图在写入前**逐张目视核对过**内容与病害名相符
-- （例：溃疡病那张是果实上的木栓化隆起病斑 + 黄色晕圈，典型症状）。
-- 但仍建议由农技人员在演示前最终确认一遍。
--
-- 找不到对应真实照片的条目一律**保持为空**，不放示意图充数。
-- 幂等：可重复执行。
--
-- 用法：mysql --host=127.0.0.1 --port=13306 -uroot -p ry-vue < sql/tz_kb_real_images.sql
-- ---------------------------------------------------------------

-- 柑橘溃疡病：果实上的木栓化隆起病斑，周围黄色晕圈（已核对）
UPDATE `tz_knowledge_base`
SET `typical_image` = '/profile/upload/2026/09/14/柑橘溃疡病_20260914115243A001.png'
WHERE `knowledge_id` = 100;

-- 柑橘炭疽病（已核对）
UPDATE `tz_knowledge_base`
SET `typical_image` = '/profile/upload/2026/09/14/柑橘炭疽病_20260914144203A003.jpg'
WHERE `knowledge_id` = 101;

-- 柑橘红蜘蛛：叶片上密布灰白色失绿小点（已核对）
UPDATE `tz_knowledge_base`
SET `typical_image` = '/profile/upload/2026/09/14/柑橘红蜘蛛_20260914143938A001.jpg'
WHERE `knowledge_id` = 102;

-- 柑橘疮痂病：叶片上的病斑与叶面皱缩（已核对；该图分辨率一般，
-- 且疮痂病与溃疡病叶部症状易混，演示时建议配合「鉴别要点」文字一起看）
UPDATE `tz_knowledge_base`
SET `typical_image` = '/profile/upload/2026/09/14/柑橘疮痂病_20260914114919A001.jpg'
WHERE `knowledge_id` = 105;

-- 柑橘黄龙病（已核对）
UPDATE `tz_knowledge_base`
SET `typical_image` = '/profile/upload/2026/09/14/柑橘黄龙病_20260914144322A004.jpg'
WHERE `knowledge_id` = 114;

-- 其余条目保持为空：宁可没有参考图，也不放一张对不上的图。
-- 缺镁(104) 目前仍是示意图 demo-mg_*.png —— 系统里没有对应的真实照片，
-- 暂时保留，等拿到真实照片后用 scripts/import-kb-image.ps1 替换。

SELECT knowledge_id AS id, disease_name AS name,
       IFNULL(NULLIF(typical_image, ''), '(空)') AS img
FROM tz_knowledge_base
WHERE knowledge_id BETWEEN 100 AND 120
ORDER BY knowledge_id;
