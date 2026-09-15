<#
    田诊助手 一键停止

    用法（在仓库根目录）：
        powershell -NoProfile -File scripts\stop-all.ps1                  # 停前端 + 后端 + ngrok
        powershell -NoProfile -File scripts\stop-all.ps1 -IncludeInfra    # 连 MySQL/Redis 一起停

    默认不停 MySQL/Redis —— 它们重启一次要几十秒，而留着不影响任何事。

    为什么要专门写个脚本：后端进程握着 ruoyi-admin.jar，不停掉就打包会失败在
    spring-boot:repackage 的 jar 改名那一步。每次改完代码，先 stop 再 build。

    本文件必须存为 UTF-8 带 BOM（见 check-env.ps1 顶部说明），改动后注意别丢了。
#>

param(
    [switch]$IncludeInfra
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$script:Stopped = 0
$script:Missed  = 0

function Head ($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }

# 找进程的条件是「映像名在允许列表里」且「命令行含某个特征串」。
#
# 两个条件都不可少：
#   * 只按映像名（Get-Process java | Stop-Process）会连这台机器上别的 Java 工作负载
#     一起杀掉，不行。
#   * 只按命令行特征串也不行 —— 实测踩到过：Claude Code 自己那些 bash.exe 包装进程
#     的命令行里就带着 `--port 18081`（因为它们在执行启动前端的命令），只按字符串匹配
#     会把正在干活的 shell 一起杀掉。加上映像名过滤就干净了。
function Stop-Matching($name, $imageNames, $pattern) {
    $targets = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
                 Where-Object {
                     $_.CommandLine -and
                     $_.CommandLine -like "*$pattern*" -and
                     $imageNames -contains $_.Name
                 })

    if ($targets.Count -eq 0) {
        Write-Host "  $name 未在运行"
        $script:Missed++
        return
    }

    foreach ($p in $targets) {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Write-Host "  已停止 $name（PID $($p.ProcessId)）" -ForegroundColor Green
            $script:Stopped++
        } catch {
            Write-Host "  停不掉 $name（PID $($p.ProcessId)）：$($_.Exception.Message)" -ForegroundColor Red
            Write-Host "     可能需要以管理员身份运行 PowerShell" -ForegroundColor Yellow
        }
    }
}

Write-Host ""
Write-Host "田诊助手 一键停止" -ForegroundColor White
Write-Host ("=" * 58)

Head "1. 前端"
# 匹配 --port 18081：vite 进程和它外面的 npm 包装进程命令行里都有这一段，一次收干净。
# 只杀 vite 的话，npm 那个父进程会留在那儿等一个已经没了的子进程。
Stop-Matching "前端（vite / npm）" @("node.exe") "--port 18081"

Head "2. 后端"
Stop-Matching "后端（ruoyi-admin.jar）" @("java.exe") "ruoyi-admin.jar"

# ngrok 的特征串就用 ngrok.exe —— 启动命令里是完整路径 "C:\...\ngrok\ngrok.exe" http 18081，
# 用镜像名当特征串既能命中，又不会误伤别的东西。
# 隧道开着的时候，你以为停了服务，公网其实还连着一台正在跑的服务，很容易忘。
Head "3. ngrok 公网隧道"
Stop-Matching "ngrok（公网隧道）" @("ngrok.exe") "ngrok.exe"

if ($IncludeInfra) {
    Head "4. Redis"
    Stop-Matching "Redis" @("redis-server.exe") "RuoYi\redis\redis-server.exe"

    Head "5. MySQL"
    # mysqld 在 Windows 上会有父子两个进程（启动器 + 实际服务），特征串能同时命中，正好。
    Stop-Matching "MySQL" @("mysqld.exe") "mysql-8.0\bin\mysqld.exe"
} else {
    Head "4. MySQL / Redis"
    Write-Host "  按默认保留（要一起停加 -IncludeInfra）"
}

Write-Host ""
Write-Host ("=" * 58)
Write-Host "本次停止 $script:Stopped 个进程。" -ForegroundColor Green
if ($script:Stopped -gt 0) {
    Write-Host "现在可以安全地重新打包后端了。"
}
Write-Host ("=" * 58)
