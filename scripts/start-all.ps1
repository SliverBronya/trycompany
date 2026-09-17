<#
    田诊助手 · 一键启动

    ┌──────────────────────────────────────────────────────────┐
    │  依次确保 MySQL → Redis → 后端 → 前端 都在跑。            │
    │  已经起来的不会重复启动，所以这个脚本随时可以再跑一遍，   │
    │  当「检查 + 补齐」用。                                    │
    └──────────────────────────────────────────────────────────┘

    用法（在仓库根目录，或直接双击根目录的「一键启动.bat」）：
        powershell -NoProfile -File scripts\start-all.ps1
        powershell -NoProfile -File scripts\start-all.ps1 -NoFrontend   # 只起后端，跑接口自测用

    大模型密钥从 scripts\.secrets.local 读取（用 scripts\set-keys.ps1 配置）。
    没有密钥也能完整启动，只是 AI 链路退化为「预置映射 + 知识库检索」，
    这是设计内的降级，不是故障。

    日志写在 logs\ 下，按组件分文件。全部后台运行、不弹窗，要停用 stop-all.ps1。

    本文件必须存为 UTF-8 带 BOM（见 check-env.ps1 顶部说明），改动后注意别丢了。
#>

param(
    [switch]$NoFrontend
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$Root      = Split-Path -Parent $PSScriptRoot
$RuoYiRoot = "C:\RuoYi"
$JdkPath   = Join-Path $RuoYiRoot "toolchain\jdk17"
$NodePath  = Join-Path $RuoYiRoot "toolchain\node20"
$MysqlExe  = Join-Path $RuoYiRoot "mysql-8.0\bin\mysqld.exe"
$MysqlIni  = Join-Path $RuoYiRoot "mysql-8.0\my.ini"
$RedisExe  = Join-Path $RuoYiRoot "redis\redis-server.exe"
$RedisConf = Join-Path $RuoYiRoot "redis\redis.windows.conf"
$Jar       = Join-Path $Root "backend\ruoyi-admin\target\ruoyi-admin.jar"
$LogDir    = Join-Path $Root "logs"

$BackendPort  = 18080
$FrontendPort = 18081
$MysqlPort    = 13306
$RedisPort    = 16379

$script:Started = 0

function Head ($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 62) }
function Info($m)  { Write-Host "  $m" }
function Good($m)  { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m)  { Write-Host "  $m" -ForegroundColor Red }
function Warn($m)  { Write-Host "  $m" -ForegroundColor Yellow }

function Test-Port($port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", $port)
        $c.Close()
        return $true
    } catch { return $false }
}

function Wait-Port($port, $seconds, $label) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Seconds 1
    }
    Bad "$label 在 $seconds 秒内没有监听 $port，看 logs\ 下的日志"
    return $false
}

# 后台起一个进程，输出重定向到 logs\<名字>.log。
# 不加 -Wait：这几个都要长期运行，脚本得能返回。
# 不用 -NoNewWindow：那样输出会混进当前控制台，把后面的检查结果冲得看不出来。
function Start-Detached($name, $exe, $argList, $workDir) {
    $out = Join-Path $LogDir "$name.log"
    $err = Join-Path $LogDir "$name.err.log"
    Start-Process -FilePath $exe -ArgumentList $argList -WorkingDirectory $workDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null
    $script:Started++
    Info "已启动 $name，日志：logs\$name.log"
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# ================================================================ 载入密钥
# 必须在启动后端之前完成：Start-Process 会继承当前进程的环境变量，
# 所以这里设进 $env:，后端起来时就已经带着 key 了。
$SecretsPath = Join-Path $PSScriptRoot ".secrets.local"
$keyNames = @("TZ_AI_API_KEY", "TZ_AI_VISION_API_KEY", "TZ_AI_TEXT_API_KEY",
              "TZ_PASSWORD", "TZ_DB_PASSWORD")
foreach ($k in $keyNames) { Set-Item -Path "env:$k" -Value "" }

if (Test-Path $SecretsPath) {
    foreach ($line in (Get-Content -Path $SecretsPath -Encoding UTF8)) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        $kv = $line -split '=', 2
        if ($kv.Count -eq 2) {
            $k = $kv[0].Trim(); $v = $kv[1].Trim()
            if ($keyNames -contains $k) { Set-Item -Path "env:$k" -Value $v }
        }
    }
}

$sharedKey = -not [string]::IsNullOrWhiteSpace($env:TZ_AI_API_KEY)
$visionOwn = -not [string]::IsNullOrWhiteSpace($env:TZ_AI_VISION_API_KEY)
$textOwn   = -not [string]::IsNullOrWhiteSpace($env:TZ_AI_TEXT_API_KEY)
$visionOk  = $sharedKey -or $visionOwn
$textOk    = $sharedKey -or $textOwn
$anyKey    = $visionOk -or $textOk

# ================================================================ 标题
Clear-Host
Write-Host ""
Write-Host "  ████████╗██╗ █████╗ ███╗   ██╗███████╗██╗  ██╗███████╗███╗   ██╗" -ForegroundColor DarkGreen
Write-Host "  ╚══██╔══╝██║██╔══██╗████╗  ██║╚══███╔╝██║  ██║██╔════╝████╗  ██║" -ForegroundColor DarkGreen
Write-Host "     ██║   ██║███████║██╔██╗ ██║  ███╔╝ ███████║█████╗  ██╔██╗ ██║" -ForegroundColor Green
Write-Host "     ██║   ██║██╔══██║██║╚██╗██║ ███╔╝  ██╔══██║██╔══╝  ██║╚██╗██║" -ForegroundColor Green
Write-Host "     ██║   ██║██║  ██║██║ ╚████║███████╗██║  ██║███████╗██║ ╚████║" -ForegroundColor Cyan
Write-Host "     ╚═╝   ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═══╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  田诊助手 · 柑橘植保巡田与诊断系统   一键启动" -ForegroundColor White
Write-Host "  ============================================================" -ForegroundColor DarkGray

# ---- 大模型状态（启动前先报，省得起来后才发现没接上）----
Write-Host ""
Write-Host "  大模型" -NoNewline -ForegroundColor White
if ($anyKey) {
    Write-Host "  已配置密钥" -ForegroundColor Green
    if ($visionOk) { Write-Host "    视觉模型（读巡田照片）  ✔ 就绪" -ForegroundColor Green }
    else          { Write-Host "    视觉模型（读巡田照片）  ✘ 无密钥，读图将走降级" -ForegroundColor Yellow }
    if ($textOk)   { Write-Host "    文本模型（问答/报告）   ✔ 就绪" -ForegroundColor Green }
    else          { Write-Host "    文本模型（问答/报告）   ✘ 无密钥，问答将走降级" -ForegroundColor Yellow }
} else {
    Write-Host "  未配置密钥 —— 按设计降级运行" -ForegroundColor Yellow
    Write-Host "    系统会用「预置样张映射 + 知识库检索」作答，功能完整、可正常演示，" -ForegroundColor DarkGray
    Write-Host "    但「AI 初诊」这一环不会真正调用大模型。" -ForegroundColor DarkGray
    Write-Host "    要接上：powershell -NoProfile -File scripts\set-keys.ps1" -ForegroundColor Cyan
}

# ================================================================ MySQL
Head "1. MySQL（$MysqlPort）"
if (Test-Port $MysqlPort) {
    Good "已在运行"
} else {
    if (-not (Test-Path $MysqlExe)) { Bad "找不到 $MysqlExe" }
    else {
        Start-Detached "mysql" $MysqlExe @("--defaults-file=$MysqlIni", "--console") $RuoYiRoot
        if (Wait-Port $MysqlPort 60 "MySQL") { Good "已就绪" }
    }
}

# ================================================================ Redis
Head "2. Redis（$RedisPort）"
if (Test-Port $RedisPort) {
    Good "已在运行"
} else {
    if (-not (Test-Path $RedisExe)) { Bad "找不到 $RedisExe" }
    else {
        # --save "" 与 --appendonly no：这是演示用的缓存，不做持久化，
        # 免得 redis 目录里堆一堆 dump.rdb。跟机器上原来的启动参数保持一致。
        Start-Detached "redis" $RedisExe @($RedisConf, "--bind", "127.0.0.1", "--port", "$RedisPort", "--save", '""', "--appendonly", "no") $RuoYiRoot
        if (Wait-Port $RedisPort 30 "Redis") { Good "已就绪" }
    }
}

# ================================================================ 后端
Head "3. 后端（$BackendPort）"
if (Test-Port $BackendPort) {
    Good "已在运行"
    Warn "注意：如果刚改过密钥，需要先停掉它再重启才能生效（stop-all.ps1）"
} else {
    if (-not (Test-Path $Jar)) {
        Bad "找不到 $Jar —— 还没构建过。先跑构建（见 docs\操作手册.md），再回来启动。"
    } else {
        $java = Join-Path $JdkPath "bin\java.exe"
        # --server.port 显式指定：环境里的 SERVER_PORT 会被 Spring 宽松绑定捡走，
        # 优先级高于 application.yml，不加这个参数后端会跑去抢别的端口。
        Start-Detached "backend" $java @("-jar", $Jar, "--server.port=$BackendPort") $Root
        if (Wait-Port $BackendPort 90 "后端") {
            # 端口通了不等于 Spring 上下文就绪。刚起来那几秒登录会报「用户不存在」，
            # 连试 5 次还会触发若依的账号锁定，所以这里多等一会儿再探接口。
            Start-Sleep -Seconds 5
            try {
                $r = Invoke-WebRequest -Uri "http://127.0.0.1:$BackendPort/captchaImage" -UseBasicParsing -TimeoutSec 10
                if ($r.StatusCode -eq 200) { Good "已就绪，接口有响应" }
            } catch {
                Bad "端口起来了但接口没响应：$($_.Exception.Message)"
            }
        }
    }
}

# ================================================================ 前端
Head "4. 前端（$FrontendPort）"
if ($NoFrontend) {
    Info "按参数跳过（-NoFrontend）"
} elseif (Test-Port $FrontendPort) {
    Good "已在运行"
} else {
    $npm = Join-Path $NodePath "npm.cmd"
    if (-not (Test-Path $npm)) { Bad "找不到 $npm" }
    elseif (-not (Test-Path (Join-Path $Root "frontend\node_modules"))) {
        Bad "frontend\node_modules 不存在，先装依赖"
    } else {
        Start-Detached "frontend" $npm @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$FrontendPort") (Join-Path $Root "frontend")
        if (Wait-Port $FrontendPort 90 "前端") { Good "已就绪" }
    }
}

# ================================================================ 汇总
Write-Host ""
Write-Host "  ============================================================" -ForegroundColor DarkGray
if ($script:Started -eq 0) {
    Write-Host "  全部组件此前已在运行，本次没有启动任何进程。" -ForegroundColor Green
} else {
    Write-Host "  本次启动了 $script:Started 个进程。" -ForegroundColor Green
}

Write-Host ""
Write-Host "  访问地址" -ForegroundColor White
Write-Host ""
Write-Host "    电脑端   http://127.0.0.1:$FrontendPort" -ForegroundColor Cyan
Write-Host "    手机端   http://127.0.0.1:$FrontendPort/m/home   （窄屏会自动跳到这里）" -ForegroundColor Cyan
Write-Host ""
Write-Host "  登录账号 admin    口令见 scripts\.secrets.local 的 TZ_PASSWORD" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  端口  MySQL $MysqlPort   Redis $RedisPort   后端 $BackendPort   前端 $FrontendPort" -ForegroundColor DarkGray
Write-Host "  停止   powershell -NoProfile -File scripts\stop-all.ps1" -ForegroundColor DarkGray
Write-Host "  改密钥 powershell -NoProfile -File scripts\set-keys.ps1" -ForegroundColor DarkGray
Write-Host ""
