<#
    田诊助手｜推送到 GitHub

    为什么需要你自己跑一次：推送要 GitHub 凭据，而凭据管理器的首次登录
    必须交互（弹浏览器），在非交互的自动化环境里跑不了。
    这个脚本跑完一次之后，凭据就被 Windows 凭据管理器记住了，
    以后 `git push` 直接可用。

    用法（在项目根目录）：
        powershell -NoProfile -File scripts\push-to-github.ps1 -Repo https://github.com/用户名/仓库名.git

    参数：
        -Repo    你在 GitHub 上建好的**空**仓库地址（不要勾选自动创建 README）
        -Branch  分支名，默认 main

    关于口令：这个脚本里**不含任何真实口令**。泄漏检查的做法是从
    scripts\.secrets.local（被 git 忽略的本机文件）读出真实值再去比对 ——
    把口令写进检查脚本本身，就等于换个地方继续泄露。
#>

param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [string]$Branch = "main"
)

$ErrorActionPreference = "Continue"
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
if (-not (Test-Path (Join-Path $Root ".git"))) { Bad "当前目录不是 git 仓库：$Root"; exit 1 }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Bad "找不到 git，请先装 Git for Windows"; exit 1 }
if ($Repo -notmatch '^https://github\.com/.+/.+\.git$') {
    Bad "仓库地址格式不对：$Repo"
    Info "应形如：https://github.com/用户名/仓库名.git"
    exit 1
}

# ---------------------------------------------------------------- 2. 提交前泄漏检查
Write-Host ""
Write-Host "1. 提交前泄漏检查" -ForegroundColor Cyan
Write-Host ("-" * 58)

$leak = $false

# 2.1 从本机文件读真实口令，反过来查仓库里有没有它们
$secretFile = Join-Path $PSScriptRoot ".secrets.local"
if (Test-Path $secretFile) {
    $secrets = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($secretFile, [System.Text.Encoding]::UTF8)) {
        if ($line -match '^\s*([A-Z_]+)\s*=\s*(.+?)\s*$') { $secrets[$Matches[1]] = $Matches[2] }
    }
    $found = 0
    foreach ($key in $secrets.Keys) {
        $val = $secrets[$key]
        if ($val -like 'CHANGE_ME*') { continue }
        $hit = git grep -l -F $val -- . 2>$null
        if ($LASTEXITCODE -eq 0 -and $hit) {
            $leak = $true
            $found++
            Bad "$key 的值出现在提交内容里："
            $hit | ForEach-Object { Info "  $_" }
        }
    }
    if ($found -eq 0) { Good "本机口令未出现在任何提交内容中" }
} else {
    Info "未找到 scripts\.secrets.local，跳过口令比对"
}

# 2.2 高价值凭据单独查一遍：大模型 Key 一旦泄露是真花钱的
$keyHit = git grep -l -E 'sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_' -- . 2>$null
if ($LASTEXITCODE -eq 0 -and $keyHit) {
    $leak = $true
    Bad "疑似 API Key / Token："
    $keyHit | ForEach-Object { Info "  $_" }
} else {
    Good "未发现 API Key / Token（大模型 Key 只走 TZ_AI_API_KEY 环境变量，从不落盘）"
}

# 2.3 该被忽略的目录有没有混进来
$untracked = $false
foreach ($p in @('logs/', 'frontend/node_modules/', 'frontend/dist/', 'backend/ruoyi-admin/target/',
                 'backend/ruoyi-admin/src/main/resources/application-druid.yml')) {
    git ls-files --error-unmatch $p 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $untracked = $true; Bad "$p 仍在版本库中"; }
}
if (-not $untracked) { Good "构建产物 / 日志 / 数据源配置均已排除" }
if ($untracked) { $leak = $true }

if ($leak) {
    Write-Host ""
    Bad "检查未通过，已中止推送。请先处理上面列出的问题。"
    exit 2
}

# ---------------------------------------------------------------- 3. 工作区状态
Write-Host ""
Write-Host "2. 工作区状态" -ForegroundColor Cyan
Write-Host ("-" * 58)
$dirty = git status --porcelain
if ($dirty) {
    Info "有未提交的改动，将一并提交："
    $dirty | Select-Object -First 10 | ForEach-Object { Info "  $_" }
    git add -A
    git commit -m "chore: 推送前自动提交未保存的改动" | Out-Null
    Good "已自动提交"
} else {
    Good "工作区干净"
}

# ---------------------------------------------------------------- 4. 配置远端
Write-Host ""
Write-Host "3. 配置远端" -ForegroundColor Cyan
Write-Host ("-" * 58)
$existing = git remote 2>$null
if ($existing -contains "origin") {
    git remote set-url origin $Repo
    Good "已更新 origin → $Repo"
} else {
    git remote add origin $Repo
    Good "已添加 origin → $Repo"
}

# ---------------------------------------------------------------- 5. 推送
Write-Host ""
Write-Host "4. 推送（首次会弹出 GitHub 登录窗口）" -ForegroundColor Cyan
Write-Host ("-" * 58)

git push -u origin $Branch
if ($LASTEXITCODE -ne 0) {
    Bad "推送失败。常见原因："
    Info "  · 远端仓库不是空的（建仓库时勾了 README）→ 删掉重建一个空仓库"
    Info "  · 仓库地址写错，或该账号没有写权限"
    Info "  · 网络不通（本项目环境走代理，git 会自动读 HTTPS_PROXY）"
    Info "  · 首次登录窗口被关掉了 → 重跑一次本脚本"
    exit 1
}

# ---------------------------------------------------------------- 6. 汇总
Write-Host ""
Write-Host ("=" * 58)
Good "推送完成：$Repo"
Info "分支：$Branch    提交：$(git rev-parse --short HEAD)"
Info ""
Info "凭据已被 Windows 凭据管理器记住，以后改完直接 git push 即可。"
Info "查看状态：git status    查看提交：git log --oneline"
