<#
    田诊助手｜把本地口令换成占位符（仅在要把仓库设为**公开**时使用）

    本机开发用的口令有两类：
      · 数据库 root 口令        —— 存在 application-druid.yml 与几个 SQL/脚本注释里
      · 演示账号 admin 的口令   —— 存在文档与测试脚本里

    它们只对 localhost 有意义，泄露的实际危害有限；但仓库一旦公开，
    别人拿着演示账号口令 + 你的公网地址就能登进演示站。
    所以：**建议直接建私有仓库**；确需公开，就跑一次这个脚本。

    ⚠️ 跑完后本机也连不上数据库了 —— 需要把 application-druid.yml 里
       password 改回真实口令才跑得起来。脚本会把原文件备份成 *.bak，
       改回来就是把 .bak 覆盖回去。

    用法（在项目根目录）：
        powershell -NoProfile -File scripts\sanitize-secrets.ps1
        powershell -NoProfile -File scripts\sanitize-secrets.ps1 -Restore   # 还原
#>

param([switch]$Restore)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

function Info($m) { Write-Host "  $m" }
function Good($m) { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m) { Write-Host "  $m" -ForegroundColor Red }

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$DbPwd    = 'Root@123456'
$DbPlaceholder    = 'CHANGE_ME_DB_PASSWORD'
$AdminPwd = 'wXsRUPApemfuyNt6'
$AdminPlaceholder = 'CHANGE_ME_ADMIN_PASSWORD'

# 需要处理的目标（只动这些文件，不做全库替换 —— 全库替换容易误伤）
$Targets = @(
    'backend\ruoyi-admin\src\main\resources\application-druid.yml',
    'docs\操作手册.md',
    'docs\项目架构概览.md',
    'scripts\check-env.ps1',
    'scripts\seed-demo.py',
    'scripts\test-api.py',
    'sql\role_common_menu_backup_20260913.sql',
    'sql\tz_kb_structure.sql',
    'sql\tz_menu_flatten.sql',
    'sql\tz_photo_describe.sql'
)

Write-Host ""
if ($Restore) { Write-Host "还原口令（从 .bak）" -ForegroundColor White }
else          { Write-Host "替换口令为占位符" -ForegroundColor White }
Write-Host ("=" * 58)

$changed = 0
foreach ($rel in $Targets) {
    $p = Join-Path $Root $rel
    if (-not (Test-Path $p)) { continue }
    $bak = "$p.bak"

    if ($Restore) {
        if (Test-Path $bak) {
            Copy-Item $bak $p -Force
            Remove-Item $bak -Force
            Info "已还原 $rel"
            $changed++
        }
        continue
    }

    $text = [System.IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)
    if ($text -notmatch [regex]::Escape($DbPwd) -and $text -notmatch [regex]::Escape($AdminPwd)) { continue }

    Copy-Item $p $bak -Force
    $text = $text.Replace($DbPwd, $DbPlaceholder).Replace($AdminPwd, $AdminPlaceholder)
    # 不写 BOM：这几个文件里 .ps1 之外的都用无 BOM
    [System.IO.File]::WriteAllText($p, $text, (New-Object Text.UTF8Encoding($false)))
    Info "已处理 $rel（原文件备份为 $rel.bak）"
    $changed++
}

Write-Host ""
Write-Host ("=" * 58)
if ($changed -eq 0) {
    Info "没有需要处理的文件（可能已经处理过，或路径变了）"
} elseif ($Restore) {
    Good "已还原 $changed 个文件"
} else {
    Good "已处理 $changed 个文件"
    Info ""
    Info "接下来："
    Info "  1. 再跑一次 push-to-github.ps1 推送"
    Info "  2. 本机要恢复运行：把 application-druid.yml 的 password 改回真实口令"
    Info "     （或执行 powershell -NoProfile -File scripts\sanitize-secrets.ps1 -Restore）"
}
