<#
    田诊助手 内网穿透（ngrok）

    把本机前端挂到公网，让别人用手机流量直接打开。

    为什么只开一条隧道：/dev-api 是 vite 在服务端转给 18080 的，浏览器只认一个域名，
    所以不用为后端再开第二条。

    用法（在仓库根目录）：
        powershell -NoProfile -File scripts\ngrok.ps1
        powershell -NoProfile -File scripts\ngrok.ps1 -Port 18081 -NgrokExe "D:\tools\ngrok.exe"

    跑完会把公网地址打出来。停隧道：scripts\stop-all.ps1（它会把 ngrok 一起收掉）。

    前提：
      1. ngrok.exe 可用（默认找 C:\Users\Mendel\ngrok\ngrok.exe，也会先从 PATH 找）
      2. authtoken 已经写进 %LOCALAPPDATA%\ngrok\ngrok.yml（ngrok config add-authtoken <token>）

    本文件必须存为 UTF-8 带 BOM（见 check-env.ps1 顶部说明），改动后注意别丢了。
#>

param(
    [int]    $Port     = 18081,
    [string] $NgrokExe = ""
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$Root    = Split-Path -Parent $PSScriptRoot
$LogDir  = Join-Path $Root "logs"
$LogFile = Join-Path $LogDir "ngrok-agent.log"

function Head ($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }
function Info($m)  { Write-Host "  $m" }
function Good($m)  { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m)  { Write-Host "  $m" -ForegroundColor Red }

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

Write-Host ""
Write-Host "田诊助手 内网穿透（ngrok）" -ForegroundColor White
Write-Host ("=" * 58)

# ---------------------------------------------------------------- 找 ngrok
Head "1. 确认 ngrok"
$exe = ""
if ($NgrokExe) {
    if (Test-Path $NgrokExe) { $exe = (Resolve-Path $NgrokExe).Path }
} else {
    $cmd = Get-Command ngrok -ErrorAction SilentlyContinue
    if ($cmd) { $exe = $cmd.Source }
    if (-not $exe) {
        foreach ($p in @(
            "C:\Users\Mendel\ngrok\ngrok.exe",
            (Join-Path $env:LOCALAPPDATA "ngrok\ngrok.exe"),
            (Join-Path $env:USERPROFILE "ngrok.exe")
        )) { if (Test-Path $p) { $exe = $p; break } }
    }
}
if (-not $exe) {
    Bad "找不到 ngrok.exe。装一个，或用 -NgrokExe 指定路径。"
    exit 1
}
Good "ngrok：$exe"
Info ((& $exe version 2>&1 | Out-String).Trim())

# ---------------------------------------------------------------- 前置检查
Head "2. 前置检查"
$cfg = Join-Path $env:LOCALAPPDATA "ngrok\ngrok.yml"
if (Test-Path $cfg) {
    Good "authtoken 配置：$cfg"
} else {
    Bad "没有 $cfg —— 先跑一次：ngrok config add-authtoken <你的token>"
    exit 1
}

$t = New-Object System.Net.Sockets.TcpClient
try {
    $t.Connect("127.0.0.1", $Port)
    $t.Close()
    Good "本机 $Port 在监听"
} catch {
    Bad "本机 $Port 没在监听 —— 先把服务起起来：powershell -NoProfile -File scripts\start-all.ps1"
    exit 1
}

# ---------------------------------------------------------------- 清掉旧隧道
Head "3. 清掉可能残留的旧隧道"
$old = @(Get-CimInstance Win32_Process -Filter "Name='ngrok.exe'" -ErrorAction SilentlyContinue)
if ($old.Count -eq 0) {
    Info "没有旧进程"
} else {
    foreach ($p in $old) {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Info "已停止旧 ngrok（PID $($p.ProcessId)）"
        } catch {
            Bad "停不掉 PID $($p.ProcessId)：$($_.Exception.Message)"
        }
    }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------- 关键：清代理变量
# ngrok 免费版不允许走 http/s 代理：一旦继承了 HTTP_PROXY / HTTPS_PROXY，它会直接退出并报
# ERR_NGROK_9009（"Running the agent with an http/s proxy is a Pay-as-you-go feature"）。
# 有些环境（IDE、沙箱、公司网络代理）会自动注入这几个变量，所以这里显式清掉再起
# ——Start-Process 起的子进程继承的是当前进程的环境。
# 不这么做的话，现象是「ngrok 一闪而过、什么都不输出」，只有翻 logs\ngrok-agent.log 才看得到原因。
Head "4. 清理代理环境变量"
$cleared = 0
foreach ($k in @("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")) {
    if (Test-Path "Env:$k") {
        Info "清除 $k = $((Get-Item "Env:$k").Value)"
        Remove-Item "Env:$k" -Force -ErrorAction SilentlyContinue
        $cleared++
    }
}
if ($cleared -eq 0) { Info "本来就没有代理变量" } 
Good "代理变量已清（ngrok 免费版必须直连）"

# ---------------------------------------------------------------- 起隧道
Head "5. 建立隧道"
Remove-Item $LogFile -Force -ErrorAction SilentlyContinue
Start-Process -FilePath $exe -ArgumentList "http", "$Port", "--log=$LogFile" `
    -WorkingDirectory (Split-Path -Parent $exe) -WindowStyle Hidden | Out-Null
Info "已后台启动，日志：logs\ngrok-agent.log"

$url = ""
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:4040/api/tunnels" -UseBasicParsing -TimeoutSec 5
        $j = $r.Content | ConvertFrom-Json
        $url = ($j.tunnels | Where-Object { $_.public_url -like "https://*" } | Select-Object -First 1).public_url
        if ($url) { break }
    } catch { }
}

Write-Host ""
Write-Host ("=" * 58)
if ($url) {
    Write-Host "公网地址：$url" -ForegroundColor Green
    Write-Host ""
    Write-Host "  登录页  $url/login"
    Write-Host "  注册页  $url/register"
    Write-Host "  隧道状态  http://127.0.0.1:4040"
    Write-Host ""
    Write-Host "  注意：ngrok 免费版每次重启都换一个随机子域，发出去的链接要跟着改。" -ForegroundColor Yellow
    Write-Host "        另外免费版首次用浏览器打开可能先弹一个 ngrok 提示页，点过去即可。" -ForegroundColor Yellow
} else {
    Bad "30 秒内没等到隧道。下面是 logs\ngrok-agent.log 的最后几行："
    Get-Content $LogFile -Tail 6 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
}
Write-Host ("=" * 58)
if (-not $url) { exit 1 }
