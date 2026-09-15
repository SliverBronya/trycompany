# 田诊助手 · 柑橘病虫害 AI 诊断系统

面向柑橘种植的巡田诊断工具：**拍一张病叶照片，AI 给出初诊与可追溯的防治建议**。

基于 [若依 RuoYi-Vue 3.9.2](https://gitee.com/y_project/RuoYi-Vue) 二次开发。

---

## 下载

### 方式一：直接下压缩包（不需要装 git）

👉 **[点此下载 ZIP](https://github.com/SliverBronya/trycompany/archive/refs/heads/main.zip)**

也可以打开仓库页面 → 绿色的 **Code** 按钮 → **Download ZIP**。
（想下某个固定版本，见 [Releases](https://github.com/SliverBronya/trycompany/tags) 页。）

### 方式二：克隆仓库

```bash
# HTTPS（公开仓库，不需要账号）
git clone https://github.com/SliverBronya/trycompany.git

# 或用 SSH（需要你自己的密钥已加到 GitHub）
git clone git@github.com:SliverBronya/trycompany.git
```

> 如果 SSH 报 `Connection refused`，说明 22 端口不通，改走 GitHub 的 443 通道：
> 在 `~/.ssh/config` 里加
> ```
> Host github.com
>     HostName ssh.github.com
>     Port 443
>     User git
> ```

### ⚠️ 下载后有一件事必须先做，否则跑不起来

仓库里**没有** `application-druid.yml`，也没有 `scripts/.secrets.local` ——
它们含本机口令，按设计不进版本库。照模板复制一份即可（详见下方「快速开始」第 2 步）：

```bash
copy backend\ruoyi-admin\src\main\resources\application-druid.yml.example ^
     backend\ruoyi-admin\src\main\resources\application-druid.yml
copy scripts\.secrets.local.example scripts\.secrets.local
```

### 只想「看看效果」而不想装环境？

那不用下载代码 —— 本项目的运行依赖有 MySQL 8 + Redis + JDK 17 + Node 20，
装齐比看一次要费时得多。直接让作者给你一个演示地址打开即可（演示地址是临时的，
需要时现开）。**注意**：演示站是公开可访问的，别在上面录入真实生产数据。

---

## 它解决什么问题

农技员巡田时看到病叶，回到办公室查书、翻资料、问人 —— 一趟下来半小时，结论还未必有出处。
这个系统把这一步压缩成「拍照 → 30 秒内拿到初诊 + 依据」。

**但它不假装自己什么都知道。** 这是整个项目的核心设计取向：

- 结论可以没有，但不能编。所有对外输出都带 `来源` / `是否降级` / `降级原因`
- 用药与用量**只能来自知识库**，模型自己编的剂量一律被后置校验拦下
- 没把握时明确说「本次无法给出可靠结论」，并说明卡在哪一环
- 识图模型先写症状描述，用户在此基础上改 —— 但描述里**禁止出现病名**，
  否则「描述 → 诊断」的先后顺序就名存实亡

---

## 核心能力

### AI 诊断四级兜底

```
1. 预置样张映射   图片 MD5 命中登记过的样张 → 直接给标准结论（100% 稳定，离线可用）
2. 多模态大模型   读图 + 症状描述 + 知识库候选 → 结构化 JSON 结论
3. 知识库降级     前两条不通 → 关键词检索给一个低置信度的可解释结论
4. 明确拒答       都不行 → 说清原因，绝不凑一个结论出来
```

**第 3 级有一条例外**：模型已经看过照片、只是把握不足时，**不再退回关键词检索**。
退回去等于丢掉图像证据、只按文字再猜一次 —— 那正是「什么病都像溃疡病」的来源。
这种情况会把模型那个不采信的结论作为线索告诉用户：「最可能是 X，但把握只有 55」。

### RAG 检索：为什么给通用词降权

第一版按「字面共现个数」打分，结果系统性偏向柑橘溃疡病 —— 它的症状文本里
「病斑 / 叶片 / 隆起 / 晕圈」这类词最多，任何叶斑描述都会先命中它。

引入 **IDF**（一个词在越多条目里出现，贡献越小）之后，实测：

| 输入 | 改造前 | 改造后 |
|---|---|---|
| 15 种病害的标准描述 | 命中 6/15，9 条拒答 | **命中 13/15** |
| 简写但准确的描述 | 0/4 全被拒 | **4/4 全命中** |
| 泛词 / 无关 / 废话 | 全拒答 | 全拒答 |

**同时做到少拒答与少误判**，而不是拿一个换另一个。

### 知识库结构

27 条柑橘病虫害与缺素条目，三个字段职责严格分开：

| 字段 | 用途 | 说明 |
|---|---|---|
| `key_features` 特征性表现 | **参与检索，权重最高** | 只写「本病看得见、别的病少见」的阳性描述 |
| `differential` 鉴别要点 | **不参与检索**，只交给模型比对 | 这里必然写着别的病名，参与打分会造成串扰 |
| `typical_image` 典型图片 | 知识库页面展示 | |

### 剂量校验（DosageGuard）

AI 生成的建议里凡出现用量数值，都被拿去和知识库 `medicine_note` 逐条比对：
**收录的放行，没收录的一律拦截并替换为提示语**。

### 电脑端 + 手机端两套 UI

窄屏（≤768px）自动进入 `/m/*` 手机版：底部 5 个 tab、拍照直接调摄像头、
识图填描述、提交后自动诊断一步到位。不是桌面版的缩放，是独立的一套页面。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.5.16 · JDK 17 · MyBatis · MySQL 8 · Redis |
| 前端 | Vue 3 · Vite 6 · Element Plus · Pinia · ECharts |
| 基座 | 若依 RuoYi-Vue 3.9.2 |
| 大模型 | 视觉 `glm-4.1v-thinking-flash` · 文本 `glm-4-flash-250414`（OpenAI 兼容协议） |

---

## 快速开始

### 1. 准备数据库与缓存

```bash
# MySQL 8：建库 ry-vue，导入
backend/sql/ry_20260417.sql        # 若依基础表
backend/sql/quartz.sql             # 定时任务表
sql/tz_schema.sql                  # 业务表
sql/tz_menu.sql                    # 菜单与权限
sql/tz_knowledge_seed.sql          # 知识库初始 15 条
sql/tz_kb_structure.sql            # 知识库加特征性表现/鉴别要点/典型图片三栏
sql/tz_kb_expand.sql               # 知识库扩到 27 条

# Redis：默认端口 6379，本项目开发环境用 16379
```

### 2. 配置本机参数（**仓库里没有真实口令**）

数据源配置与脚本口令都不在版本库里，首次部署照模板复制一份：

```bash
# 数据源配置
copy backend\ruoyi-admin\src\main\resources\application-druid.yml.example ^
     backend\ruoyi-admin\src\main\resources\application-druid.yml
# 然后把 password 改成你本机 MySQL 的口令

# 脚本口令（test-api.py / seed-demo.py / check-env.ps1 读取）
copy scripts\.secrets.local.example scripts\.secrets.local
# 然后填入真实口令
```

> 这两类文件都在 `.gitignore` 里。口令属于部署参数，不该跟代码一起走。

### 3. 配置大模型（可选，不配也能完整演示）

大模型 Key **只走环境变量，从不落盘**：

```powershell
$env:TZ_AI_API_KEY = "你的智谱 key"
```

不配的话系统自动退化为「预置样张映射 + 知识库检索」，功能完整可演示，
界面会明确标出当前处于哪种模式。

换服务商需要改四项：`provider` / `base-url` / `vision-model` / `text-model`。

### 4. 构建与启动

```powershell
# 后端
set JAVA_HOME=<你的 jdk17>
mvn -o -B -f backend\pom.xml -DskipTests package
java -jar backend\ruoyi-admin\target\ruoyi-admin.jar --server.port=18080

# 前端
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 18081
```

访问 http://127.0.0.1:18081 。默认端口：后端 18080、前端 18081、MySQL 13306、Redis 16379。

---

## 验证

```powershell
# 后端单元测试（134 项，纯逻辑，不依赖数据库与外部 API）
mvn -o -B -f backend\pom.xml test

# 接口端到端回归（184 项，需要后端已在运行）
python scripts\test-api.py

# 环境自检（17 项）
powershell -NoProfile -File scripts\check-env.ps1

# 前端生产构建
cd frontend && npm run build:prod

# 演示数据（生成样式图、地块、巡田记录，并跑通诊断→建议→报告全链路）
python scripts\seed-demo.py
```

---

## 目录结构

```
backend/                        若依后端
  ruoyi-admin/                  启动模块 + 自定义控制器
    src/main/java/com/ruoyi/web/controller/tianzhen/   12 个 Tz*Controller
  ruoyi-system/                 业务核心
    src/main/java/com/ruoyi/system/ai/                 AI 层（最该读的包）
    src/main/java/com/ruoyi/system/domain/             实体
    src/main/resources/mapper/tianzhen/                Mapper XML
frontend/                       Vue3 前端
  src/views/tianzhen/           电脑端页面
  src/views/mobile/             手机端页面（/m/*）
  src/utils/tzImage.js          图片地址归一化（本仓库统一出口）
sql/                            业务表结构与数据变更脚本（可重复执行）
scripts/                        构建、启动、自检、演示数据、接口回归
docs/                           操作手册、架构概览、AI 链路说明、答辩话术
```

---

## 几个踩过的坑（写在代码注释里，也写在这里）

- **`allowPublicKeyRetrieval=true` 不能省**：MySQL8 的 `caching_sha2_password` 在
  `useSSL=false` 时要向服务端取 RSA 公钥，默认被驱动拒绝。这个错只在 MySQL 刚重启、
  缓存为空、第一条连接就是 JDBC 时出现，非常像「环境没起来」。
- **图片地址不可信绝对 URL**：上传接口返回 `http://127.0.0.1:18080/...`，
  手机通过公网访问时那个 host 指向手机自己 → 图片全裂。统一走
  `utils/tzImage.js` 丢掉 origin 只取路径（详见该文件注释）。
- **Druid 监控台必须关**：它的 stat 视图会连 SQL 参数值一起列出，
  而 `/druid/**` 是 permitAll、默认口令又是人尽皆知的 `ruoyi/123456`。
- **`.ps1` 必须存 UTF-8 带 BOM**，否则 PowerShell 5.1 按 GBK 读会乱码。

---

## 已知限制

- 知识库只有 27 条，**部分条目的用药只写了药剂类别、没写具体配比** ——
  没有本地植保站情报可依时不编数字。拿到当地病虫情报后按原文补。
- 新增条目的「资料来源」标注为「通用技术资料（待本地核实）」，
  不写具体文件名 —— 编出来的出处比留白更危险。
- 手机端是网页版，未打包成原生 App。
- 多公司 / 部门 / 岗位的数据隔离**尚未实现**，方案见
  `docs/多公司部门岗位权限方案.md`。

---

## 免责声明

本系统为**辅助判断工具**，不能替代农技人员现场诊断。
所有用药请以农药标签与当地植保部门的推荐为准，并遵守当地禁限用农药规定。
