<#
    田诊助手 一键启动

    依次确保 MySQL → Redis → 后端 → 前端 都在跑。已经起来的不会重复启动，
    所以这个脚本随时可以再跑一遍，当"检查 + 补齐"用。

    用法（在仓库根目录）：
        powershell -NoProfile -File scripts\start-all.ps1
        powershell -NoProfile -File scripts\start-all.ps1 -NoFrontend   # 只起后端，跑接口自测用

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

function Head ($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }
function Info($m)  { Write-Host "  $m" }
function Good($m)  { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m)  { Write-Host "  $m" -ForegroundColor Red }

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

Write-Host ""
Write-Host "田诊助手 一键启动" -ForegroundColor White
Write-Host ("=" * 58)

# ---------------------------------------------------------------- MySQL
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

# ---------------------------------------------------------------- Redis
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

# ---------------------------------------------------------------- 后端
Head "3. 后端（$BackendPort）"
if (Test-Port $BackendPort) {
    Good "已在运行"
} else {
    if (-not (Test-Path $Jar)) {
        Bad "找不到 $Jar —— 还没构建过。先跑构建（见 docs\操作手册.md），再回来启动。"
    } else {
        $java = Join-Path $JdkPath "bin\java.exe"
        Start-Detached "backend" $java @("-jar", $Jar) $Root
        if (Wait-Port $BackendPort 90 "后端") {
            try {
                $r = Invoke-WebRequest -Uri "http://127.0.0.1:$BackendPort/captchaImage" -UseBasicParsing -TimeoutSec 10
                if ($r.StatusCode -eq 200) { Good "已就绪，接口有响应" }
            } catch {
                Bad "端口起来了但接口没响应：$($_.Exception.Message)"
            }
        }
    }
}

# ---------------------------------------------------------------- 前端
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
        if (Wait-Port $FrontendPort 90 "前端") { Good "已就绪：http://127.0.0.1:$FrontendPort" }
    }
}

# ---------------------------------------------------------------- 汇总
Write-Host ""
Write-Host ("=" * 58)
if ($script:Started -eq 0) {
    Write-Host "全部组件此前已在运行，本次没有启动任何进程。" -ForegroundColor Green
} else {
    Write-Host "本次启动了 $script:Started 个进程。" -ForegroundColor Green
}
Write-Host "访问：http://127.0.0.1:$FrontendPort    登录：admin（口令见 docs\操作手册.md，已不是 admin123）"
Write-Host "停止：powershell -NoProfile -File scripts\stop-all.ps1"
Write-Host ("=" * 58)
