<#
    田诊助手｜推送到 GitHub

    为什么需要你自己跑一次：推送要 GitHub 凭据，而凭据管理器的首次登录
    必须交互（弹浏览器），在非交互的自动化环境里跑不了。
    这个脚本跑完一次之后，凭据就被 Windows 凭据管理器记住了，
    以后 `git push` 直接可用。

    用法（在项目根目录）：
        powershell -NoProfile -File scripts\push-to-github.ps1 -Repo https://github.com/用户名/仓库名.git

    参数：
        -Repo    你在 GitHub 上建好的空仓库地址（不要勾选自动创建 README）
        -Branch  分支名，默认 main
        -Private 仅提示用：提醒你仓库该建成私有

    前提：先在 GitHub 网页上建一个**空仓库**（不要初始化 README/.gitignore/LICENSE），
          否则首次推送会因为远端已有提交而被拒。
#>

param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

function Info($m) { Write-Host "  $m" }
function Good($m) { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m) { Write-Host "  $m" -ForegroundColor Red }

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host ""
Write-Host "田诊助手 → GitHub" -ForegroundColor White
Write-Host ("=" * 58)

# ---------------------------------------------------------------- 1. 前置检查
if (-not (Test-Path (Join-Path $Root ".git"))) {
    Bad "当前目录不是 git 仓库：$Root"
    exit 1
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Bad "找不到 git，请先安装 Git for Windows"
    exit 1
}

if ($Repo -notmatch '^https://github\.com/.+/.+\.git$') {
    Bad "仓库地址格式不对：$Repo"
    Info "应形如：https://github.com/用户名/仓库名.git"
    exit 1
}

# ---------------------------------------------------------------- 2. 确认没有敏感信息
Write-Host ""
Write-Host "1. 提交前敏感信息检查" -ForegroundColor Cyan
Write-Host ("-" * 58)

$patterns = @{
    '大模型 API Key' = '1f6f0e0c7de3476886122201757443b3'
    'admin 口令'     = 'wXsRUPApemfuyNt6'
}
$leak = $false
foreach ($name in $patterns.Keys) {
    $found = git grep -l -F $patterns[$name] -- . 2>$null
    if ($LASTEXITCODE -eq 0 -and $found) {
        $leak = $true
        Bad "$name 出现在："
        $found | ForEach-Object { Info "  $_" }
    }
}
if (-not $leak) { Good "未发现 API Key（API Key 只走 TZ_AI_API_KEY 环境变量，从不落盘）" }

Info ""
Info "注意：application-druid.yml 与本手册里带着本机数据库口令与演示账号口令，"
Info "      它们只对 localhost 有意义，但**如果仓库设为公开**，"
Info "      任何人拿到公网地址就能用演示账号登录。建议建**私有仓库**；"
Info "      确需公开，先跑 scripts\sanitize-secrets.ps1 把口令换成占位符。"

# ---------------------------------------------------------------- 3. 配置远端
Write-Host ""
Write-Host "2. 配置远端" -ForegroundColor Cyan
Write-Host ("-" * 58)

$existing = git remote 2>$null
if ($existing -contains "origin") {
    git remote set-url origin $Repo
    Good "已更新 origin → $Repo"
} else {
    git remote add origin $Repo
    Good "已添加 origin → $Repo"
}

# ---------------------------------------------------------------- 4. 推送
Write-Host ""
Write-Host "3. 推送（首次会弹出 GitHub 登录窗口）" -ForegroundColor Cyan
Write-Host ("-" * 58)

git push -u origin $Branch
if ($LASTEXITCODE -ne 0) {
    Bad "推送失败。常见原因："
    Info "  · 远端仓库不是空的（建仓库时勾了 README）→ 先在网页上删掉仓库重建一个空的"
    Info "  · 仓库地址写错，或该账号没有这个仓库的写权限"
    Info "  · 网络不通（本项目环境走代理，git 会自动读 HTTPS_PROXY）"
    exit 1
}

# ---------------------------------------------------------------- 5. 汇总
Write-Host ""
Write-Host ("=" * 58)
Good "推送完成：$Repo"
Info "分支：$Branch    提交：$(git rev-parse --short HEAD)"
Info ""
Info "凭据已被 Windows 凭据管理器记住，以后改完直接 git push 即可。"
Info "查看状态：git status    查看提交：git log --oneline"
