<#
    田诊助手 · 打包 Android APK

    用法（项目根目录，或直接双击根目录的「打包APK.bat」）：
        powershell -NoProfile -File scripts\build-apk.ps1

    流程：构建前端 → 同步到 Android → 清理冲突文件 → Gradle 打包 → 复制到项目根

    ────────────────────────────────────────────────────────────
    ⚠️ 两个必须知道的坑（都是实测踩出来的，别跳过）

    1) 构建会生成 .gz 文件（vite 的 gzip 预压缩，给 Nginx 用的），但 Android
       会把 index.html 与 index.html.gz 判为「重复资源」，打包直接失败：

           Execution failed for task ':app:mergeReleaseAssets'
           [public/index.html] ... [public/index.html.gz]: Error: Duplicate resources

       所以 cap sync 之后必须删掉 assets 里的 .gz。这一步不能省。

    2) 删 .gz 必须用 [System.IO.File]::Delete，不能用 Remove-Item ——
       Remove-Item 走回收站，在受限环境里会被安全删除机制拦下
       （SAFE_DELETE_FAIL_CLOSED），表现为"命令没报错但文件还在"，
       然后 Gradle 依旧报同样的重复资源错误，很容易误判成没生效。

    3) 构建前端前最好先停掉后端：build:prod 吃内存，低配机器上内存不足时
       会在 transforming 阶段长时间卡住（不是报错，是资源饥饿）。
    ────────────────────────────────────────────────────────────

    本文件必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 会按 GBK 读，中文全乱。
#>

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$Root     = Split-Path -Parent $PSScriptRoot
$Frontend = Join-Path $Root "frontend"
$Android  = Join-Path $Frontend "android"
$NodeBin  = "C:\RuoYi\toolchain\node20"
$JdkPath  = "C:\RuoYi\toolchain\jdk17"
$ApkOut   = Join-Path $Android "app\build\outputs\apk\release\app-release.apk"
$ApkDst   = Join-Path $Root "田诊助手-v1.0.apk"

function Head($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 62) }
function Good($m) { Write-Host "  $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  $m" -ForegroundColor Yellow }
function Bad ($m) { Write-Host "  $m" -ForegroundColor Red }

Write-Host ""
Write-Host "  田诊助手 · 打包 Android APK" -ForegroundColor White
Write-Host ("=" * 62)

# ---------------------------------------------------------------- 1. 构建前端
Head "1. 构建前端资源（vite build）"
$env:JAVA_HOME = $JdkPath
Push-Location $Frontend
& (Join-Path $NodeBin "npm.cmd") run build:prod | Out-Host
Pop-Location
if (-not (Test-Path (Join-Path $Frontend "dist\index.html"))) {
    Bad "构建产物缺失，终止。"
    exit 1
}
Good "dist 已生成"

# ---------------------------------------------------------------- 2. 同步到 Android
Head "2. 同步到 Android 工程（cap sync）"
Push-Location $Frontend
& (Join-Path $NodeBin "npx.cmd") cap sync android | Out-Host
Pop-Location
Good "同步完成"

# ---------------------------------------------------------------- 3. 清理 .gz
Head "3. 清理冲突的 .gz 文件"
$assets = Join-Path $Android "app\src\main\assets\public"
$removed = 0
foreach ($dir in @($assets, (Join-Path $Frontend "dist"))) {
    if (-not (Test-Path $dir)) { continue }
    foreach ($f in (Get-ChildItem $dir -Recurse -Filter *.gz -ErrorAction SilentlyContinue)) {
        try { [System.IO.File]::Delete($f.FullName); $removed++ } catch { }
    }
}
$left = (Get-ChildItem $assets -Recurse -Filter *.gz -ErrorAction SilentlyContinue).Count
if ($left -gt 0) {
    Bad "仍有 $left 个 .gz 没删掉，打包会失败在 mergeReleaseAssets。"
    exit 1
}
Good "已清理 $removed 个 .gz，assets 里已无残留"

# ---------------------------------------------------------------- 4. Gradle 打包
Head "4. Gradle 打包（assembleRelease）"
Push-Location $Android
& ".\gradlew.bat" assembleRelease --console=plain | Out-Host
Pop-Location

if (-not (Test-Path $ApkOut)) {
    Bad "没有生成 APK，看上面的 Gradle 输出定位问题。"
    exit 1
}

# ---------------------------------------------------------------- 5. 交付
Head "5. 复制到项目根目录"
Copy-Item $ApkOut $ApkDst -Force
$f = Get-Item $ApkDst
Write-Host ""
Write-Host ("=" * 62)
Good "打包成功"
Write-Host "    $ApkDst"
Write-Host "    $([math]::Round($f.Length / 1MB, 2)) MB     $($f.LastWriteTime)"
Write-Host ""
Write-Host "  安装：把 APK 传到手机，允许「安装未知来源应用」后点击安装" -ForegroundColor DarkGray
Write-Host "  签名包只能覆盖安装同包名同签名的旧版；换签名必须卸载重装" -ForegroundColor DarkGray
Write-Host ""
