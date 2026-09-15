<#
    田诊助手 环境自检

    用途：换台机器、或者答辩前，先跑这个确认「能不能跑起来」，再去查业务。
    逐项探测 JDK17 / Maven / Node20 / MySQL / Redis / 后端端口 / 前端端口，
    每项直接打印结论，最后汇总。

    用法（在仓库根目录）：
        powershell -NoProfile -File scripts\check-env.ps1

    说明一：这台机器上的 JDK/Maven/MySQL/Redis 由课程离线包放在 C:\RuoYi 下，
    刻意没有加进系统 PATH（PATH 上的 java 是 JDK 8，Spring Boot 3 用不了）。
    所以下面既查 PATH，也查 C:\RuoYi 的固定位置。

    说明二：本文件必须以「UTF-8 带 BOM」保存。Windows PowerShell 5.1 读 .ps1
    时不看编码声明，没有 BOM 就按 GBK 解析，中文会散成乱码并连累字符串解析报
    「缺少终止符」。改完这个文件记得确认 BOM 还在（用 VS Code 看右下角编码，
    或 PowerShell 7 的 pwsh 执行 —— 它默认按 UTF-8 读，不吃这个亏）。
#>

$ErrorActionPreference = "Continue"

# 让中文在管道/重定向后也正常。仅在控制台不支持时兜底，失败不影响检查。
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$RuoYiRoot = "C:\RuoYi"
$JdkPath   = Join-Path $RuoYiRoot "toolchain\jdk17"
$MavenPath = Join-Path $RuoYiRoot "apache-maven-3.9.9"
$NodePath  = Join-Path $RuoYiRoot "toolchain\node20"
$MysqlExe  = Join-Path $RuoYiRoot "mysql-8.0\bin\mysql.exe"
$RedisExe  = Join-Path $RuoYiRoot "redis\redis-server.exe"

$BackendPort  = 18080
$FrontendPort = 18081
$MysqlPort    = 13306
$RedisPort    = 16379
$DbName       = "ry-vue"
$DbUser       = "root"

# 数据库口令：优先环境变量，其次 scripts\.secrets.local（该文件被 git 忽略）。
# 不写默认值是因为仓库是公开的 —— 口令落在脚本里会随 fork 与复制一路传下去。
function Get-DbPassword {
    if ($env:TZ_DB_PASSWORD) { return $env:TZ_DB_PASSWORD }
    $secret = Join-Path $PSScriptRoot ".secrets.local"
    if (Test-Path $secret) {
        foreach ($line in [System.IO.File]::ReadAllLines($secret, [System.Text.Encoding]::UTF8)) {
            if ($line -match '^\s*TZ_DB_PASSWORD\s*=\s*(.+)$') { return $Matches[1].Trim() }
        }
    }
    return $null
}

$DbPassword = Get-DbPassword

$script:Pass = 0
$script:Fail = 0
$script:Warn = 0

function Ok   ($m) { Write-Host "  [通过] $m" -ForegroundColor Green;  $script:Pass++ }
function Bad  ($m) { Write-Host "  [失败] $m" -ForegroundColor Red;    $script:Fail++ }
function Warn ($m) { Write-Host "  [注意] $m" -ForegroundColor Yellow; $script:Warn++ }
function Head ($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }

function Test-Port($port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", $port)
        $c.Close()
        return $true
    } catch {
        return $false
    }
}

# 查询一个标量，失败返回 $null 并把 stderr 留在 $script:SqlError。
#
# 参数写成一整个数组再 splat，而不是 "-P$MysqlPort" 这种连写：
# PowerShell 不会在连写的短选项里展开变量，mysql 会原样收到字符串 '$MysqlPort'
# 并报 Unknown suffix '$'。这件事踩过一次，注释留在这里。
#
# stderr 单独收走，是因为 mysql 每次都会往 stderr 打一句
# 「Using a password on the command line interface can be insecure」，
# 和 2>&1 混在一起就会把查询结果污染成一个多行字符串。
$script:SqlError = $null
function Sql($query) {
    # 没有口令就别往下走了：mysql 收到空的 --password= 会去读终端，
    # 在非交互环境里表现为「卡住」，很难看出真正原因。
    if (-not $DbPassword) {
        $script:SqlError = "未取到数据库口令：请设置环境变量 TZ_DB_PASSWORD，" +
                           "或在 scripts\.secrets.local 写一行 TZ_DB_PASSWORD=你的口令"
        return $null
    }
    # 用 --database 选库，下面的查询就都写裸表名。
    # 不这么做的话得写 `ry-vue`.tz_knowledge_base —— 库名里有连字符，不加反引号
    # MySQL 会把它当成 ry 减 vue.tz_knowledge_base，报语法错；而在 PowerShell 双引号
    # 串里写反引号又要再转义一层（`` `` ``），没必要绕这一圈。
    $argv = @(
        "--host=127.0.0.1", "--port=$MysqlPort", "--user=$DbUser", "--password=$DbPassword",
        "--database=$DbName",
        "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
        "-e", $query
    )
    $errFile = [IO.Path]::GetTempFileName()
    try {
        $out = & $MysqlExe @argv 2>$errFile
        if ($LASTEXITCODE -ne 0) {
            $script:SqlError = (Get-Content -Raw $errFile -ErrorAction SilentlyContinue)
            return $null
        }
        return ($out | Where-Object { "$_" -ne "" } | Select-Object -First 1)
    } catch {
        $script:SqlError = $_.Exception.Message
        return $null
    } finally {
        Remove-Item $errFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "田诊助手 环境自检" -ForegroundColor White
Write-Host ("=" * 58)

# ---------------------------------------------------------------- JDK17
Head "1. JDK 17"

$javaExe = Join-Path $JdkPath "bin\java.exe"
if (Test-Path $javaExe) {
    $v = & $javaExe -version 2>&1 | Select-Object -First 1
    Ok "找到 JDK17：$javaExe"
    Write-Host "         $v"
} else {
    Bad "未找到 $javaExe（后端构建与运行都需要它）"
}

# PATH 上的 java 往往是 JDK8，明确点出来，省得后面报 class file version 61.0 时懵
$pathJava = Get-Command java -ErrorAction SilentlyContinue
if ($pathJava) {
    $pv = & java -version 2>&1 | Select-Object -First 1
    if ("$pv" -match '"1\.8') {
        Warn "PATH 上的 java 是 JDK 8（$($pathJava.Source)）。构建/启动务必显式指定 JDK17，否则会报 class file version 61.0"
    } else {
        Ok "PATH 上的 java 也可用：$pv"
    }
}

$env:JAVA_HOME = $JdkPath

# ---------------------------------------------------------------- Maven
Head "2. Maven 与离线仓库"

$mvnCmd = Join-Path $MavenPath "bin\mvn.cmd"
if (Test-Path $mvnCmd) {
    Ok "找到 Maven：$mvnCmd"
} else {
    Bad "未找到 $mvnCmd"
}

$repo = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "实训环境及系统资料\aicd\05-Maven\maven-repository"
if (Test-Path $repo) {
    Ok "找到离线仓库：$repo"
    if (Test-Path (Join-Path $repo "org\springframework\boot")) {
        Ok "离线仓库里有 spring-boot 依赖"
    } else {
        Bad "离线仓库里没有 spring-boot，离线构建会缺依赖"
    }
} else {
    Bad "未找到离线仓库（构建时必须带 -Dmaven.repo.local 指向它）"
}

$userM2 = Join-Path $env:USERPROFILE ".m2\repository"
if ((Test-Path $userM2) -and (Get-ChildItem $userM2 -ErrorAction SilentlyContinue)) {
    Ok "~/.m2/repository 非空（不影响，我们仍用离线仓库）"
} else {
    Warn "~/.m2/repository 是空的 —— 所以构建必须带 -o 和 -Dmaven.repo.local，别指望它能自己下载"
}

# ---------------------------------------------------------------- Node
Head "3. Node 20 与前端依赖"

$nodeExe = Join-Path $NodePath "node.exe"
if (Test-Path $nodeExe) {
    $nv = & $nodeExe -v
    Ok "找到 Node：$nodeExe（$nv）"
} else {
    $pn = Get-Command node -ErrorAction SilentlyContinue
    if ($pn) { Warn "未找到 $nodeExe，但 PATH 上有 node $(& node -v)" }
    else     { Bad "未找到 Node（前端构建与开发服务器都需要）" }
}

$modules = Join-Path (Split-Path -Parent $PSScriptRoot) "frontend\node_modules"
if (Test-Path $modules) {
    $n = (Get-ChildItem $modules -Directory -ErrorAction SilentlyContinue).Count
    if ($n -gt 100) { Ok "frontend\node_modules 已就绪（$n 个包）" }
    else            { Bad "frontend\node_modules 不完整（只有 $n 个目录），需要重新安装依赖" }
} else {
    Bad "frontend\node_modules 不存在，需要先安装前端依赖"
}

# ---------------------------------------------------------------- MySQL
Head "4. MySQL（127.0.0.1:$MysqlPort）"

if (Test-Port $MysqlPort) {
    Ok "端口 $MysqlPort 可连接"
    if (Test-Path $MysqlExe) {
        $tables = Sql "select count(*) from information_schema.tables where table_schema='$DbName';"
        if ($null -ne $tables) {
            Ok "库 $DbName 可访问，共 $tables 张表"

            $tz = Sql "select count(*) from information_schema.tables where table_schema='$DbName' and table_name like 'tz\_%';"
            if ($null -eq $tz)    { Bad "业务表数查不出来。mysql 报：$script:SqlError" }
            elseif ([int]$tz -ge 9) { Ok "业务表 $tz 张（tz_ 前缀）" }
            else                  { Bad "业务表只有 $tz 张，建表脚本可能没导全（sql\tz_schema.sql）" }

            $kb = Sql "select count(*) from tz_knowledge_base;"
            if ($null -eq $kb)      { Bad "植保知识库查不出来。mysql 报：$script:SqlError" }
            elseif ([int]$kb -gt 0) { Ok "植保知识库 $kb 条（sql\tz_knowledge_seed.sql）" }
            else                    { Bad "植保知识库是空的，AI 诊断与问答都会没有依据" }

            $menu = Sql "select count(*) from sys_menu where menu_id between 2000 and 2199;"
            if ($null -eq $menu)      { Bad "业务菜单查不出来。mysql 报：$script:SqlError" }
            elseif ([int]$menu -gt 0) { Ok "业务菜单 $menu 项（sql\tz_menu.sql）" }
            else                      { Bad "业务菜单没导入，登录后看不到田诊助手" }

            $preset = Sql "select count(*) from tz_image_preset;"
            if ($null -eq $preset)      { Warn "预置样张查不出来。mysql 报：$script:SqlError" }
            elseif ([int]$preset -gt 0) { Ok "预置样张 $preset 条（演示保底路径可用）" }
            else                        { Warn "预置样张是空的，演示时走不到「同图同结论」的保底路径；可跑 python scripts\seed-demo.py" }
        } else {
            Bad "连得上端口但查不了库，账号或库名可能不对。mysql 报：$script:SqlError"
        }
    } else {
        Warn "端口通，但没找到 mysql 客户端 $MysqlExe，无法进一步核对数据"
    }
} else {
    Bad "端口 $MysqlPort 连不上，MySQL 没启动（它是独立进程，不是 Windows 服务）"
}

# ---------------------------------------------------------------- Redis
Head "5. Redis（127.0.0.1:$RedisPort）"

if (Test-Port $RedisPort) {
    Ok "端口 $RedisPort 可连接"
} else {
    Bad "端口 $RedisPort 连不上，Redis 没启动（若依的缓存与登录态依赖它）"
    if (Test-Path $RedisExe) { Write-Host "         启动：$RedisExe" }
}

# ---------------------------------------------------------------- 应用端口
Head "6. 应用端口"

if (Test-Port $BackendPort) {
    Ok "后端 $BackendPort 已在运行"
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$BackendPort/captchaImage" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { Ok "后端接口有响应（/captchaImage 200）" }
    } catch {
        Warn "端口开着但接口没正常响应：$($_.Exception.Message)"
    }
} else {
    Warn "后端 $BackendPort 未运行（自测脚本 test-api.py 需要它先起来）"
}

if (Test-Port $FrontendPort) {
    Ok "前端 $FrontendPort 已在运行"
} else {
    Warn "前端 $FrontendPort 未运行（演示要开界面时需要）"
}

# ---------------------------------------------------------------- 汇总
Write-Host ""
Write-Host ("=" * 58)
$color = if ($script:Fail -gt 0) { "Red" } else { "Green" }
Write-Host "合计：通过 $script:Pass，失败 $script:Fail，注意 $script:Warn" -ForegroundColor $color
Write-Host ("=" * 58)

if ($script:Fail -gt 0) {
    Write-Host ""
    Write-Host "有失败项时，先解决失败项再查业务问题。" -ForegroundColor Yellow
    exit 1
}
exit 0
