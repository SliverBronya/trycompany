# 品牌资源

田诊助手的标识资源。**实际生效的文件不在这里**，本目录存放源文件与说明。

## 当前使用的标记

一张绿色渐变的图形：左侧弧形体 + 三条信号弧 + 放大镜。

## 生效的文件（改这些才会真的变）

| 路径 | 用途 |
| --- | --- |
| `frontend/src/assets/logo/tianzhen-logo.svg` | **网站全部品牌位置** |
| `frontend/public/favicon.ico` | 浏览器标签页 |
| `frontend/android/app/src/main/res/mipmap-*/ic_launcher*.png` | APP 图标 |
| `frontend/android/app/src/main/res/drawable/ic_launcher_background.xml` | APP 图标背景层 |
| `frontend/android/app/src/main/res/values/ic_launcher_background.xml` | 同上（颜色值，两处必须一致） |

网站上所有出现标记的地方都走 `frontend/src/components/TzBrandMark/index.vue`，
它再引用那个 SVG。所以**改一个 SVG 文件就能覆盖**侧边栏、登录页、移动端首页。

## 本目录的文件

- `logo-mark.png` —— 从品牌图抠出的透明底标记（955×824），各尺寸图标由它派生
- `方案预览.png`、`品牌标记-实景对照.png` —— 效果确认用
- `1-圆中空叶.svg` 等 6 个 —— 之前做的一批候选方案（**未采用**，留档）

## 换 logo 的步骤

1. 先确认项目里有没有现成的矢量版（搜 `assets/logo/`）。
   **如果有，直接改配色比嵌位图好得多** —— 一份源文件派生所有尺寸，任何分辨率都清晰。
2. 没有矢量版再抠图。**图形是彩色的、底是白/灰的，用饱和度色键，不要用 AI 抠图**
   （AI 会把边界清晰的白色卡片误判成前景）。参考 `.workbuddy/logos/chroma.py`。
3. 用 `.workbuddy/logos/derive.py` 派生 favicon 与各密度图标。
   注意自适应图标**前景只占画布短边的 62%**，再大就会被启动器裁掉边缘。
4. 背景层的 `drawable/` 与 `values/` 两处颜色要一起改。
5. 跑一次 `.workbuddy/logos/context-shot.py` 生成实景对照图，
   确认它在**深色背景**上也立得住（侧边栏是深墨绿，最容易翻车）。
6. 重新构建前端与 APK（后者用 `scripts/build-apk.ps1`）。
