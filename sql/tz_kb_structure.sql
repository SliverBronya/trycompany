-- ============================================================================
-- 田诊助手｜知识库结构化改造 —— 数据库变更
-- ============================================================================
--
-- 背景
--   用户反馈「AI 诊断总是偏向柑橘溃疡病」。实测（15 条知识库 × 15 种病害描述）
--   定位到三个叠加原因，其中两个与知识库结构直接相关：
--
--     1) 检索只按「字面共现」打分，而溃疡病症状文本里「病斑 / 叶片 / 隆起 / 褐色 /
--        晕圈」这类通用词最多，任何叶斑类描述都会先命中它 —— 通用词没有被降权。
--     2) 知识库没有任何「本病独有特征」与「与易混病怎么区分」的信息，
--        溃疡病（木栓化火山口状开裂、黄色晕圈、两面隆起）与疮痂病（叶背圆锥形
--        瘤状突起、叶正面漏斗状凹陷）在库里长得几乎一样，模型无从分辨。
--
-- 本次新增三个字段，职责严格分开：
--
--   key_features   —— 本病**特征性**表现，阳性描述。**参与检索打分且权重最高**。
--                     只写「有/是」，不写「与X的区别」，避免把别的病名带进来
--                     造成串扰（查「圆锥形突起」绝不能命中溃疡病条目）。
--   differential   —— 与易混病的鉴别说明。**不参与打分**，只作为上下文喂给模型，
--                     让它逐条比对后给出结论。这里必然会出现别的病名，因此不能检索。
--   typical_image  —— 典型症状图片地址，供知识库页面展示。
--
-- 本脚本可重复执行。
--
-- 用法：
--   C:\RuoYi\mysql-8.0\bin\mysql.exe --host=127.0.0.1 --port=13306 ^
--     --user=root --password=<你的数据库口令> --database=ry-vue ^
--     --default-character-set=utf8mb4 < sql\tz_kb_structure.sql
-- ============================================================================

-- ---------------------------------------------------------------- 1. 加字段
SET @s := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tz_knowledge_base' AND COLUMN_NAME='key_features');
SET @ddl := IF(@s=0,
  'ALTER TABLE `tz_knowledge_base` ADD COLUMN `key_features` varchar(600) DEFAULT NULL COMMENT ''特征性表现（本病独有，参与检索加权）'' AFTER `symptoms`',
  'DO 0');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tz_knowledge_base' AND COLUMN_NAME='differential');
SET @ddl := IF(@s=0,
  'ALTER TABLE `tz_knowledge_base` ADD COLUMN `differential` varchar(1200) DEFAULT NULL COMMENT ''鉴别要点（与易混病怎么区分，仅供模型比对，不参与检索）'' AFTER `key_features`',
  'DO 0');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tz_knowledge_base' AND COLUMN_NAME='typical_image');
SET @ddl := IF(@s=0,
  'ALTER TABLE `tz_knowledge_base` ADD COLUMN `typical_image` varchar(500) DEFAULT NULL COMMENT ''典型症状图片地址'' AFTER `differential`',
  'DO 0');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------- 2. 特征性表现
-- 每条只写「本病看得见、别的病少见的」特征，尽量使用植物病理学的标准说法，
-- 这样识图模型只要按要求描述，就更容易命中正确的条目。

UPDATE `tz_knowledge_base` SET `key_features`='病斑中央木栓化隆起并呈火山口状开裂；周围有明显黄色晕圈；叶片正反两面均隆起；初为黄色油浸状小点；后期可穿孔；枝梢与果实同样发病' WHERE `knowledge_id`=100;
UPDATE `tz_knowledge_base` SET `key_features`='病斑自叶尖或叶缘开始、半圆形或不规则形；病健交界明显；潮湿时出现朱红色黏质小点（分生孢子堆）或同心轮纹状小黑点；病斑平而不隆起；枝梢受害自上而下枯死呈灰白色' WHERE `knowledge_id`=101;
UPDATE `tz_knowledge_base` SET `key_features`='叶面密布针尖大小的灰白色失绿小点；远看整叶呈灰白至暗黄色、失去光泽；翻看叶背可见细小红色或暗红色螨体与蛛丝状物；严重时全叶焦枯脱落' WHERE `knowledge_id`=102;
UPDATE `tz_knowledge_base` SET `key_features`='枝干和枝叶果上固着密集的灰白色或褐色蚧壳、可剥离；叶片发黄、枝梢枯萎；分泌蜜露诱发煤烟病使枝叶果面覆盖黑色霉层；果实表面出现黄绿色斑点' WHERE `knowledge_id`=103;
UPDATE `tz_knowledge_base` SET `key_features`='症状先出现在中下部老叶；叶脉间失绿呈典型倒 V 字形，叶基部沿主脉两侧仍保持绿色；新叶基本正常；严重时叶片提早脱落、果实变小' WHERE `knowledge_id`=104;
UPDATE `tz_knowledge_base` SET `key_features`='只危害幼嫩叶片、枝梢和幼果；病斑仅发生在叶背并向叶背呈圆锥形或瘤状突起；叶正面相应凹陷形如漏斗；病斑表面蜡黄色、木栓化、粗糙；病斑连片时叶片扭曲畸形；幼果形成圆锥形木栓化瘤状突起、果小皮厚味酸' WHERE `knowledge_id`=105;
UPDATE `tz_knowledge_base` SET `key_features`='果面产生褐色或黑褐色点状、线状、环形硬胶质小粒点，手摸有砂纸感；幼果期形成的针尖状黑点无法消除；枝干病部松软、渗出褐色胶液、有恶臭（流胶型）；成熟及贮藏期多自蒂部发病、果心腐烂快于果皮（穿心烂）' WHERE `knowledge_id`=106;
UPDATE `tz_knowledge_base` SET `key_features`='叶、枝、果表面覆盖黑色或暗褐色霉层；霉层附生于表面、不侵入组织；较厚绒状的可用手擦成片脱落、薄纸状的易撕下自然脱落；阻抑光合作用使树势衰弱' WHERE `knowledge_id`=107;
UPDATE `tz_knowledge_base` SET `key_features`='成虫和若虫聚集在嫩梢嫩叶上刺吸；成虫受惊会跳、停息时腹部末端常翘起约 45 度；若虫分泌白色蜡丝和蜜露；嫩梢受害后萎缩枯死；是柑橘黄龙病唯一的传毒媒介' WHERE `knowledge_id`=108;
UPDATE `tz_knowledge_base` SET `key_features`='幼虫在嫩叶表皮下钻蛀取食叶肉，形成弯曲的白色或银色虫道（俗称鬼画符）；被害叶片卷曲、变硬、易脱落；造成的伤口会显著加重柑橘溃疡病的发生' WHERE `knowledge_id`=109;
UPDATE `tz_knowledge_base` SET `key_features`='成蚜和若蚜群集于嫩梢、嫩叶、花蕾上刺吸；嫩叶卷曲、皱缩、畸形；分泌蜜露使叶面发亮并招引蚂蚁；诱发煤烟病使枝叶果面覆盖黑色霉层' WHERE `knowledge_id`=110;
UPDATE `tz_knowledge_base` SET `key_features`='果皮油胞被破坏，初呈灰褐色后变黑褐色形成黑皮果；叶片呈暗褐色失去光泽、严重时落叶；虫体极小肉眼难以辨认、没有蛛丝；生产中常以果面是否出现黑褐色锈斑作为判断依据' WHERE `knowledge_id`=111;
UPDATE `tz_knowledge_base` SET `key_features`='成虫产卵于幼果内、果面有针尖大的产卵孔；受害果未熟先黄、提前脱落；剖开果实可见白色幼虫（蛆）在果内蛀食果肉；造成大量落果' WHERE `knowledge_id`=112;
UPDATE `tz_knowledge_base` SET `key_features`='症状出现在新叶：新梢纤细、节间变短、呈直立的矮丛状；病叶变小、直立、窄小；叶肉褪绿呈淡绿至黄色斑点、黄绿相间的斑驳；果实小、果皮光滑变厚、着色不良；症状多表现在秋梢' WHERE `knowledge_id`=113;
UPDATE `tz_knowledge_base` SET `key_features`='叶脉及相邻组织黄化并常木栓化、黄化斑驳而不对称；病叶变硬并向**外**弯曲；病株矮化、出现季节外多花且多脱落；结不整形小果、色淡略带绿、果轴硬化、品质低劣；根系腐朽、树势逐年衰弱至全株死亡；属检疫性病害' WHERE `knowledge_id`=114;

-- ---------------------------------------------------------------- 3. 鉴别要点
-- 这里必然出现别的病名（这正是它的用途），所以**不参与检索打分**，
-- 只作为上下文交给模型做比对。谁与谁容易混，按田间实际发生频率排序。

UPDATE `tz_knowledge_base` SET `differential`='最易与柑橘疮痂病混淆，区分看三处：① 病斑大小 —— 溃疡病约 3～5 毫米，疮痂病多为 1～2 毫米；② 隆起方向 —— 溃疡病叶片正反两面都隆起，疮痂病只在叶背呈圆锥形瘤状突起、叶正面凹陷如漏斗；③ 有无黄晕与开裂 —— 溃疡病周围有明显黄色晕圈、中央木栓化后呈火山口状开裂，疮痂病无黄色晕圈、不火山口状开裂。与炭疽病区分：炭疽病病斑大而不规则、平而不隆起、潮湿时有朱红色黏点或同心轮纹小黑点，无黄色晕圈。另需注意溃疡病与潜叶蛾关系密切，虫道伤口边缘出现木栓化隆起病斑时应优先考虑溃疡病。' WHERE `knowledge_id`=100;
UPDATE `tz_knowledge_base` SET `differential`='与溃疡病区分：炭疽病病斑自叶尖叶缘开始、形状不规则、黄褐至灰褐色、平而不隆起、病健交界明显，潮湿时有朱红色黏质小点或同心轮纹状小黑点，没有黄色晕圈、也不呈火山口状开裂。与疮痂病区分：疮痂病病斑小而隆起、呈圆锥形木栓化突起且只危害幼嫩组织，炭疽病病斑大而平、老叶新叶均可受害。与黑点病（树脂病）区分：黑点病是褐色硬胶质小粒点、手摸有砂纸感；炭疽病是较大病斑配朱红黏点。' WHERE `knowledge_id`=101;
UPDATE `tz_knowledge_base` SET `differential`='最容易与柑橘锈壁虱（锈蜘蛛）混淆：红蜘蛛为害叶片形成针尖大小灰白色失绿小点、远看叶片灰白至暗黄，翻看叶背可见细小红色螨体与蛛丝状物；锈壁虱主要为害果皮造成灰褐至黑褐锈斑、形成黑皮果，虫体肉眼难辨且没有蛛丝。与缺素黄化区分：红蜘蛛的失绿呈边界清晰的密集细点、可查见螨体，缺镁缺锌的黄化沿叶脉呈规律的倒 V 形或斑驳状、看不到螨体。' WHERE `knowledge_id`=102;
UPDATE `tz_knowledge_base` SET `differential`='与蚜虫区分：介壳虫固着不动、体表覆有灰白或褐色蚧壳且可剥离，蚜虫群集嫩梢、能活动、无蚧壳。与木虱区分：木虱成虫会跳、若虫有白色蜡丝，介壳虫不动也无蜡丝。与煤烟病的关系要理清：叶面黑色霉层是煤烟病本身，介壳虫分泌的蜜露只是诱因；看到黑色霉层时要同时翻查枝条有无密集蚧壳，有则先治介壳虫。' WHERE `knowledge_id`=103;
UPDATE `tz_knowledge_base` SET `differential`='与缺锌区分最关键：缺镁先出现在中下部**老叶**、叶脉间呈倒 V 形黄化、叶基部沿主脉仍绿；缺锌出现在**新叶**、表现为节间缩短、小枝丛生、叶片变小窄小、叶脉间黄色斑驳。与黄龙病区分：缺镁黄化规律对称、叶脉清晰保持绿色、无整株衰退；黄龙病黄化斑驳而不对称、叶脉常木栓化、病叶变硬外弯、病株矮化。与自然老叶衰老区分：缺镁黄化呈特定的倒 V 形且成片出现，老叶自然黄化则是均匀褪绿。' WHERE `knowledge_id`=104;
UPDATE `tz_knowledge_base` SET `differential`='最易与溃疡病混淆，区分看三处：① 病斑大小 —— 疮痂病多为 1～2 毫米，溃疡病约 3～5 毫米；② 隆起方向 —— 疮痂病只在叶背呈圆锥形或瘤状突起、叶正面凹陷如漏斗，溃疡病叶片正反两面均隆起；③ 黄晕与开裂 —— 疮痂病无黄色晕圈、不呈火山口状开裂，溃疡病两者都有。与炭疽病区分：炭疽病病斑大而不规则、不隆起、有朱红色黏点。另一条重要线索：疮痂病只侵染幼嫩组织（新叶、幼果、嫩梢），老叶通常不受害；若中下部老叶也有大量相似病斑，更应考虑溃疡病或其他病因。' WHERE `knowledge_id`=105;
UPDATE `tz_knowledge_base` SET `differential`='与溃疡病区分：树脂病（黑点病）在果面形成褐色硬胶质小粒点、手摸有砂纸感、单个可辨认，溃疡病是木栓化隆起并火山口状开裂的大病斑、有黄色晕圈。与炭疽病区分：炭疽病病斑大而不规则、潮湿时有朱红色黏点，树脂病为细小硬粒点。与锈壁虱区分：锈壁虱造成果皮成片锈褐至黑褐的油胞破坏斑，树脂病是嵌入果皮的硬粒点、擦不掉但有颗粒感。枝干流胶型要与脚腐病区分：都表现为皮层腐烂渗出，需刮开病皮看木质部变色范围与气味（树脂病有恶臭）。' WHERE `knowledge_id`=106;
UPDATE `tz_knowledge_base` SET `differential`='首要原则：煤烟病不是独立侵染的病害，而是蚜虫、介壳虫、木虱、粉虱分泌的蜜露诱发的表面附生霉层 —— 看到煤烟病必须先找虫、治虫，只擦霉层治不好。与黑点病（树脂病）区分：煤烟病的煤层附在表面、能成片擦掉或撕下，黑点病是褐色硬胶质小粒点、嵌在果皮里、擦不掉且手摸有砂纸感。与疮痂病区分：疮痂病是木栓化圆锥形突起，煤烟病是黑色霉层。' WHERE `knowledge_id`=107;
UPDATE `tz_knowledge_base` SET `differential`='与蚜虫区分：木虱成虫会跳、停息时腹部末端常翘起约 45 度、若虫分泌白色蜡丝，蚜虫无蜡丝、不跳、密集取食且叶面有蜜露发亮并招蚂蚁。与介壳虫区分：木虱能活动，介壳虫固着不动且覆有蚧壳。与潜叶蛾区分：潜叶蛾在叶片内蛀成弯曲虫道，木虱在嫩梢表面刺吸。**处置上的特殊要求**：木虱是黄龙病唯一传毒媒介，在嫩梢上发现木虱或白色蜡丝时，即使叶片尚无黄化也要按黄龙病风险处置，及时统一用药并上报。' WHERE `knowledge_id`=108;
UPDATE `tz_knowledge_base` SET `differential`='潜叶蛾本身造成的弯曲虫道（鬼画符）识别度高，不易与其他病害混淆，关键是**不要就此收工**：它造成的伤口会显著加重溃疡病，要沿着虫道边缘检查有无隆起木栓化病斑与黄色晕圈 —— 有则说明溃疡病已随之发生，需一并防治。与斑潜蝇区分：潜叶蛾虫道较粗、多沿叶缘并伴叶片卷曲变硬；斑潜蝇虫道更细、常呈块状分布。与蚜虫木虱区分：后两者都在叶面表面取食、不钻入叶内。' WHERE `knowledge_id`=109;
UPDATE `tz_knowledge_base` SET `differential`='与木虱区分：蚜虫无白色蜡丝、不会跳、密集固定在嫩梢取食；木虱成虫会跳、若虫有白色蜡丝。与介壳虫区分：蚜虫能爬行、无蚧壳；介壳虫固着不动、覆有蚧壳。与煤烟病的关系要理清：叶面黑色霉层是煤烟病，蚜虫分泌的蜜露只是诱因，治蚜是控煤烟病的前提，只擦霉层无效。' WHERE `knowledge_id`=110;
UPDATE `tz_knowledge_base` SET `differential`='与红蜘蛛区分：锈壁虱为害果皮造成灰褐转黑褐的锈斑、形成黑皮果，虫体极小肉眼难辨、没有蛛丝；红蜘蛛为害叶片形成针尖大小的灰白失绿小点、远看叶呈灰白至暗黄，翻看叶背可见红色螨体与蛛丝。与树脂病（黑点病）区分：黑点病是褐色硬胶质小粒点、手摸有砂纸感、颗粒可逐个辨认；锈壁虱是成片的油胞破坏锈斑，边界不如粒点清晰。与药害区分：药害多在施药后短期内成片出现且位置与喷雾落点一致，锈壁虱为害随虫口扩展、由果面下部向上加重。' WHERE `knowledge_id`=111;
UPDATE `tz_knowledge_base` SET `differential`='与炭疽病、树脂病引起的落果区分：实蝇为害的果实先黄后落、果面有针尖大产卵孔，**剖开果内可见白色幼虫（蛆）**；炭疽病落果果面有不规则褐色病斑与朱红色黏点、果内无虫；树脂病落果多自蒂部腐烂、果面有硬胶质黑点、果内无虫。与吸果夜蛾区分：吸果夜蛾刺吸后留有小孔并有汁液外流，果内无幼虫。与生理落果区分：生理落果多为小果且果面无产卵孔。' WHERE `knowledge_id`=112;
UPDATE `tz_knowledge_base` SET `differential`='**最需要与柑橘黄龙病鉴别**，二者都会出现叶脉间黄化与斑驳，务必分清：缺锌的症状出现在新叶、叶片变小直立窄小、节间缩短、小枝丛生呈矮丛状，黄绿相间的斑驳但叶脉形态正常、不发生木栓化，**整株不发生系统性衰退，一般不出现果实畸形**；黄龙病为叶脉及相邻组织黄化、叶脉常木栓化，病叶变硬并向外弯曲，病株矮化、出现季节外多花且多脱落，结不整形小果、果轴硬化、味苦，根系腐朽并逐年衰弱至死。若田间无法区分，按黄龙病风险处理并送检，不可仅凭症状自行处置。与缺镁区分：缺锌在新叶、缺镁在老叶且呈倒 V 形。' WHERE `knowledge_id`=113;
UPDATE `tz_knowledge_base` SET `differential`='田间识别的关键在于与缺锌、缺镁等缺素黄化相区分：① 黄化形态 —— 黄龙病为不对称的斑驳（黄绿相间、边界不清），缺镁为对称的倒 V 形、缺锌为新叶变小直立呈矮丛状斑驳；② 叶脉 —— 黄龙病叶脉及相邻组织黄化并常木栓化，缺素一般叶脉保持绿色、不发生木栓化；③ 叶片形态 —— 黄龙病病叶变硬并向外弯曲，缺素叶形随缺素类型改变但不会变硬外弯；④ 整株表现 —— 黄龙病有矮化、季节外多花且多脱落、果实畸形（红鼻子果）、果轴硬化味苦、根系腐朽，缺素一般无整株矮化与果实畸形。发现疑似病株应立即上报并送检取样，不可仅凭田间症状自行处置，更不能自行拔除或外运苗木。' WHERE `knowledge_id`=114;

-- ---------------------------------------------------------------- 4. 典型图片
-- 现有素材只有三张演示示意图（程序绘制、图内已印「非真实病叶照片」）。
-- 先按病名把这三张挂上，让页面有东西可看；其余条目留空，界面上会显示「暂无图片」。
-- 正式使用前必须换成实拍照片 —— 演示图当典型图片只是过渡，不可用于对外培训材料。
UPDATE `tz_knowledge_base` SET `typical_image`=(SELECT p.image_url FROM tz_image_preset p WHERE p.diagnosis_name='柑橘溃疡病' ORDER BY p.preset_id LIMIT 1) WHERE `knowledge_id`=100;
UPDATE `tz_knowledge_base` SET `typical_image`=(SELECT p.image_url FROM tz_image_preset p WHERE p.diagnosis_name='柑橘红蜘蛛' ORDER BY p.preset_id LIMIT 1) WHERE `knowledge_id`=102;
UPDATE `tz_knowledge_base` SET `typical_image`=(SELECT p.image_url FROM tz_image_preset p WHERE p.diagnosis_name='柑橘缺镁' ORDER BY p.preset_id LIMIT 1) WHERE `knowledge_id`=104;

-- ---------------------------------------------------------------- 5. 结果
SELECT knowledge_id, disease_name,
       CASE WHEN key_features IS NULL THEN '(空)' ELSE LEFT(key_features,26) END AS feat,
       CASE WHEN differential IS NULL THEN '(空)' ELSE LEFT(differential,22) END AS diff,
       CASE WHEN typical_image IS NULL THEN '—' ELSE '有图' END AS img
FROM tz_knowledge_base ORDER BY knowledge_id;
