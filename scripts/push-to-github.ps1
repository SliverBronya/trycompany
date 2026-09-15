<#
    田诊助手｜建仓库 + 推送到 GitHub

    两种用法，按需选一种。

    ── 用法一：一步到位（自动创建仓库，推荐）────────────────────────────
        powershell -NoProfile -File scripts\push-to-github.ps1 -CreateRepo -Name tianzhen-assistant

      脚本会**安全地提示你输入 GitHub Token**（输入时屏幕不回显），然后：
        用 API 建仓库 → 配置远端 → 推送。
      加 -Visibility private 可建成私有仓库。

    ── 用法二：仓库已经建好了 ──────────────────────────────────────
        powershell -NoProfile -File scripts\push-to-github.ps1 -Repo https://github.com/用户名/仓库名.git

    ── 关于凭据（重要）────────────────────────────────────────────
    · **GitHub 账号密码不能用于 Git 推送**。2021 年 8 月起 GitHub 已停用密码认证，
      必须用 Personal Access Token 或 SSH 密钥。给密码会直接被拒。
    · Token 怎么建：https://github.com/settings/tokens → Generate new token (classic)
        - Note 随便填，Expiration 选最短（7 天足够）
        - 勾选 **repo** 这一整项（建仓库与推送都需要它）
        - 生成后形如 ghp_xxxxxxxx，**只显示一次**，当场复制
    · **推完立刻去同一个页面把它 Revoke 掉**，它只是搬运一次代码，不需要长期凭据。
    · 也可用环境变量传，省得每次输入：$env:GITHUB_TOKEN = "ghp_xxx"
#>

param(
    # 用法一：自动创建仓库（需要 Token）
    [switch]$CreateRepo,
    # 新仓库名（用法一时必填）
    [string]$Name,
    # 仓库可见性
    [ValidateSet("public", "private")][string]$Visibility = "public",

    # 用法二：已有仓库的地址
    [string]$Repo,
    # 分支名
    [string]$Branch = "main"
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
# PowerShell 5.1 默认可能还在用 TLS 1.0，调 GitHub API 会被直接拒
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

function Info($m) { Write-Host "  $m" }
function Good($m) { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m) { Write-Host "  $m" -ForegroundColor Red }
function Step($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host ""
Write-Host "田诊助手 → GitHub" -ForegroundColor White
Write-Host ("=" * 58)

# ---------------------------------------------------------------- 0. 参数校验
if (-not $CreateRepo -and -not $Repo) {
    Bad "请二选一："
    Info "  建仓库并推送： -CreateRepo -Name 仓库名"
    Info "  推送已有仓库： -Repo https://github.com/用户名/仓库名.git"
    exit 1
}
if ($CreateRepo -and -not $Name) { Bad "-CreateRepo 需要同时用 -Name 指定仓库名"; exit 1 }
if (-not (Test-Path (Join-Path $Root ".git"))) { Bad "当前目录不是 git 仓库：$Root"; exit 1 }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Bad "找不到 git"; exit 1 }

# 环境里注入了 HTTP(S)_PROXY，而 .NET 默认代理走 IE 设置、不认这些变量。
# 不显式指过去的话 API 调用会一直超时，报错只说「无法连接」，看不出是代理没生效。
$proxyArgs = @{}
foreach ($v in @($env:HTTPS_PROXY, $env:HTTP_PROXY, $env:https_proxy, $env:http_proxy)) {
    if ($v) { $proxyArgs["Proxy"] = $v; break }
}

# ---------------------------------------------------------------- 1. 泄漏检查
Step "1. 提交前泄漏检查"

$leak = $false

# 从本机文件读真实口令，反过来查仓库里有没有它们。
# 脚本里不写口令本身，否则检查工具自己就成了泄漏源。
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
            $leak = $true; $found++
            Bad "$key 的值出现在提交内容里："
            $hit | ForEach-Object { Info "  $_" }
        }
    }
    if ($found -eq 0) { Good "本机口令未出现在任何提交内容中" }
} else {
    Info "未找到 scripts\.secrets.local，跳过口令比对"
}

# 高价值凭据单独查一遍。模式要最小长度、并排除本脚本自身 ——
# 否则那串模式字面量会命中自己（踩过一次）。
$keyPattern = 'sk-[A-Za-z0-9]{20,}|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}'
$keyHit = git grep -l -E $keyPattern -- . ':!scripts/push-to-github.ps1' 2>$null
if ($LASTEXITCODE -eq 0 -and $keyHit) {
    $leak = $true
    Bad "疑似 API Key / Token："
    $keyHit | ForEach-Object { Info "  $_" }
} else {
    Good "未发现 API Key / Token（大模型 Key 只走 TZ_AI_API_KEY 环境变量，从不落盘）"
}

foreach ($p in @('logs/', 'frontend/node_modules/', 'frontend/dist/', 'backend/ruoyi-admin/target/',
                 'backend/ruoyi-admin/src/main/resources/application-druid.yml', 'scripts/.secrets.local')) {
    git ls-files --error-unmatch $p 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $leak = $true; Bad "$p 仍在版本库中" }
}
if (-not $leak) { Good "构建产物 / 日志 / 数据源配置 / 本机口令文件均已排除" }

if ($leak) {
    Write-Host ""
    Bad "检查未通过，已中止。请先处理上面列出的问题。"
    exit 2
}

# ---------------------------------------------------------------- 2. 工作区
Step "2. 工作区状态"
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

# ---------------------------------------------------------------- 3. 仓库地址
$remoteUrl = $Repo

if ($CreateRepo) {
    Step "3. 用 Token 创建仓库"

    $token = $env:GITHUB_TOKEN
    if (-not $token) {
        Info "请输入 GitHub Personal Access Token（需要 repo 权限）"
        Info "输入时屏幕不会回显，粘贴后直接回车即可"
        Info "还没有？到 https://github.com/settings/tokens 生成：Expiration 选最短，勾选 repo"
        Write-Host ""
        $secure = Read-Host "  Token" -AsSecureString
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $token = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    }
    if (-not $token) { Bad "没有拿到 Token，退出"; exit 1 }

    $headers = @{
        Authorization = "token $token"
        "User-Agent"  = "tianzhen-push"
        Accept        = "application/vnd.github+json"
    }

    # 3.1 取账号名 —— 顺带就是一次 Token 有效性验证
    try {
        $me = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers @proxyArgs -TimeoutSec 30
        Good "Token 有效，账号：$($me.login)"
    } catch {
        Bad "Token 验证失败：$($_.Exception.Message)"
        Info "常见原因：Token 过期 / 权限没勾 repo / 代理不通"
        exit 1
    }

    # 3.2 建仓库（空仓库，不初始化 README，否则首次推送会被拒）
    $body = @{ name = $Name; private = ($Visibility -eq "private"); auto_init = $false } | ConvertTo-Json
    try {
        $created = Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Method Post -Headers $headers `
                                     -Body $body -ContentType "application/json" @proxyArgs -TimeoutSec 60
        Good "仓库已创建：$($created.html_url)"
    } catch {
        Bad "建仓库失败：$($_.Exception.Message)"
        Info "仓库名已存在？换个 -Name，或改用「推送已有仓库」那种用法"
        exit 1
    }

    $remoteUrl = "https://github.com/$($me.login)/$Name.git"

    # 3.3 推送凭据放在**命令行 URL** 里，而不是写进 .git/config ——
    #     命令只存在于这次进程，配置文件却会留在磁盘上。
    $pushUrl = "https://$($me.login):$token@github.com/$($me.login)/$Name.git"
} else {
    Step "3. 使用已有仓库"
    if ($remoteUrl -notmatch '^https://github\.com/.+/.+\.git$') {
        Bad "仓库地址格式不对：$remoteUrl"
        Info "应形如：https://github.com/用户名/仓库名.git"
        exit 1
    }
    Info "目标：$remoteUrl"
    $pushUrl = $remoteUrl
}

# ---------------------------------------------------------------- 4. 配置远端
Step "4. 配置远端"
$existing = git remote 2>$null
if ($existing -contains "origin") { git remote set-url origin $remoteUrl; Good "已更新 origin" }
else { git remote add origin $remoteUrl; Good "已添加 origin" }
Info "origin = $remoteUrl（不含 Token，凭据不落盘）"

# ---------------------------------------------------------------- 5. 推送
Step "5. 推送"
if (-not $CreateRepo) {
    Info "首次会弹出 GitHub 登录窗口 —— 注意用密码登不上去，要用 Token 或浏览器授权"
}

git push $pushUrl "HEAD:refs/heads/$Branch" 2>&1 | ForEach-Object { Info $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Bad "推送失败。常见原因："
    Info "  · 远端仓库不是空的（建仓库时勾了 README）→ 删掉重建一个空的"
    Info "  · 地址写错 / 没有写权限"
    Info "  · 网络不通（git 会自动读 HTTPS_PROXY）"
    exit 1
}

git branch --set-upstream-to="origin/$Branch" $Branch 2>$null | Out-Null

# ---------------------------------------------------------------- 6. 汇总
Write-Host ""
Write-Host ("=" * 58)
Good "推送完成：$remoteUrl"
Info "分支：$Branch    提交：$(git rev-parse --short HEAD)"
Info ""
if ($CreateRepo) {
    Write-Host "  ⚠️ 现在就去做一件事：把刚才那个 Token 吊销掉" -ForegroundColor Yellow
    Info "     https://github.com/settings/tokens → 找到刚建的那个 → Delete"
    Info "     它已经用完了，留着只会多一个泄露点。"
    Info ""
}
Info "以后改完直接 git push（凭据会被 Windows 凭据管理器记住）。"
