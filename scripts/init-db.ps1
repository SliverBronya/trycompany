<#
    田诊助手｜初始化数据库

    把「照着文档一个个导入 SQL」变成一条命令。按依赖顺序导入全部脚本，
    每一步都检查退出码，最后校验关键数据是否到位。

    用法（在项目根目录）：
        powershell -NoProfile -File scripts\init-db.ps1
        powershell -NoProfile -File scripts\init-db.ps1 -Db ry-vue
        powershell -NoProfile -File scripts\init-db.ps1 -SkipBase      # 若依基础表已导入过时跳过

    口令来源（与 check-env.ps1 一致）：环境变量 TZ_DB_PASSWORD，
    或 scripts\.secrets.local 里的 TZ_DB_PASSWORD=...

    ⚠️ 两个实现上的坑，改这个脚本前先看：
    1. **必须用相对路径给 mysql**。项目路径里有中文（企业实训），
       而 mysql.exe 收到的命令行参数按系统 ANSI 代码页解读 —— 绝对路径会被读成
       乱码（企业实训 → 浼佷笟瀹炶）然后报 "Failed to open file"。
       所以先 Set-Location 到项目根目录，只传 sql/tz_xxx.sql 这种 ASCII 相对路径。
    2. **不要解析 mysql 的输出文字**。它按 UTF-8 输出，而 PowerShell 控制台按本地
       代码页解，中文结果是乱码（统计看板 → 缁熻鐪嬫澘）。校验只用数字，
       数字不受编码影响。数据本身没问题，别被乱码误导。
#>

param(
    [string]$Db = "ry-vue",
    # 别人的机器上 mysql 未必在这个路径 / 端口，所以留出参数
    [string]$MysqlExe = "C:\RuoYi\mysql-8.0\bin\mysql.exe",
    [int]$Port = 13306,
    [string]$User = "root",
    [switch]$SkipBase
)

$ErrorActionPreference = "Continue"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

function Info($m) { Write-Host "  $m" }
function Good($m) { Write-Host "  $m" -ForegroundColor Green }
function Bad ($m) { Write-Host "  $m" -ForegroundColor Red }
function Step($m) { Write-Host ""; Write-Host $m -ForegroundColor Cyan; Write-Host ("-" * 58) }

$Root = Split-Path -Parent $PSScriptRoot

# ---------------------------------------------------------------- 口令
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

Write-Host ""
Write-Host "田诊助手 · 初始化数据库" -ForegroundColor White
Write-Host ("=" * 58)

if (-not (Test-Path $MysqlExe)) { Bad "找不到 mysql 客户端：$MysqlExe"; exit 1 }
$DbPassword = Get-DbPassword
if (-not $DbPassword) {
    Bad "没取到数据库口令。任选一种方式提供："
    Info "  1) 设置环境变量 TZ_DB_PASSWORD"
    Info "  2) 在 scripts\.secrets.local 写一行 TZ_DB_PASSWORD=你的口令"
    Info "     （照 scripts\.secrets.local.example 复制一份即可）"
    exit 1
}

# 切到项目根目录 —— 见文件头第 1 条，中文路径不能进命令行
Set-Location $Root

$common = @(
    "--host=127.0.0.1", "--port=$Port", "--user=$User", "--password=$DbPassword",
    "--default-character-set=utf8mb4", "--batch", "--skip-column-names"
)

# 跑一条 SQL，只返回第一个标量（用于校验；只用数字，避开中文乱码）
function Sql($query, $database) {
    $argv = @()
    $argv += $common
    if ($database) { $argv += "--database=$database" }
    $argv += @("-e", $query)
    $errFile = [IO.Path]::GetTempFileName()
    try {
        $out = & $MysqlExe @argv 2>$errFile
        if ($LASTEXITCODE -ne 0) { return $null }
        return ($out | Where-Object { "$_" -ne "" } | Select-Object -First 1)
    } finally { Remove-Item $errFile -ErrorAction SilentlyContinue }
}

# 导入一个 .sql 文件（走 mysql 客户端的 source 内建命令）
function Import-Sql($relativePath) {
    if (-not (Test-Path $relativePath)) {
        Bad "找不到 $relativePath"
        return $false
    }
    $errFile = [IO.Path]::GetTempFileName()
    try {
        $argv = @()
        $argv += $common
        $argv += @("--database=$Db", "-e", "source $relativePath")
        $out = & $MysqlExe @argv 2>$errFile
        $code = $LASTEXITCODE
        if ($code -ne 0) {
            $err = (Get-Content -Raw $errFile -ErrorAction SilentlyContinue)
            Bad "导入失败：$relativePath"
            Info "  $($err -replace '\s+', ' ')".Substring(0, [Math]::Min(220, $err.Length + 12))
            return $false
        }
        return $true
    } finally { Remove-Item $errFile -ErrorAction SilentlyContinue }
}

# ---------------------------------------------------------------- 1. 建库
Step "1. 建库 $Db"
$exists = Sql "select count(*) from information_schema.schemata where schema_name='$Db'" $null
if ($exists -eq "1") {
    Good "数据库已存在，直接沿用"
} else {
    Sql "create database if not exists ``$Db`` default character set utf8mb4" $null | Out-Null
    $exists = Sql "select count(*) from information_schema.schemata where schema_name='$Db'" $null
    if ($exists -eq "1") { Good "已创建（utf8mb4）" } else { Bad "建库失败，检查 MySQL 是否在跑（端口 $Port）"; exit 1 }
}

# ---------------------------------------------------------------- 2. 按顺序导入
# 顺序是有依赖的，不能调换：
#   tz_kb_structure 要 UPDATE 已有的 15 条 → 必须在 tz_knowledge_seed 之后
#   tz_menu_flatten 要 UPDATE 菜单行     → 必须在 tz_menu 之后
$Plan = @(
    @{ f = 'backend/sql/ry_20260417.sql'; d = '若依基础表（含 sys_menu / sys_role / sys_user 等）'; base = $true },
    @{ f = 'backend/sql/quartz.sql';      d = '定时任务表';                                   base = $true },
    @{ f = 'sql/tz_schema.sql';           d = '业务表 10 张（tz_ 前缀，CREATE TABLE IF NOT EXISTS）' },
    @{ f = 'sql/tz_menu.sql';             d = '菜单与权限（先删后插，可重复执行）' },
    @{ f = 'sql/tz_knowledge_seed.sql';   d = '知识库初始 15 条' },
    @{ f = 'sql/tz_kb_structure.sql';     d = '知识库加「特征性表现 / 鉴别要点 / 典型图片」三栏并写入内容' },
    @{ f = 'sql/tz_kb_expand.sql';        d = '知识库扩容到 27 条（115～126）' },
    @{ f = 'sql/tz_photo_describe.sql';   d = '预置样张加 symptom_text（照片自动生成描述的落点）' },
    @{ f = 'sql/tz_menu_flatten.sql';     d = '菜单扁平化 + 关闭行情/供求' },
    @{ f = 'sql/tz_register.sql';         d = '自助注册开关与默认角色' }
)

Step "2. 导入 SQL（共 $($Plan.Count) 个，按依赖顺序）"
$failed = 0
$i = 0
foreach ($step in $Plan) {
    $i++
    if ($step.base -and $SkipBase) { Info "$i/$($Plan.Count)  跳过（-SkipBase）  $($step.f)"; continue }
    if (Import-Sql $step.f) {
        Good "$i/$($Plan.Count)  $($step.f)"
        Info "        $($step.d)"
    } else {
        $failed++
        Info "        $($step.d)"
        Bad "后续步骤依赖它，已中止"
        break
    }
}
if ($failed -gt 0) { Write-Host ""; Bad "初始化中断，请先解决上面那个错误"; exit 1 }

# ---------------------------------------------------------------- 3. 校验
Step "3. 校验"
$checks = @(
    @{ q = "select count(*) from information_schema.tables where table_schema='$Db' and table_name like 'tz\_%'"; want = 10; label = "业务表" },
    @{ q = "select count(*) from tz_knowledge_base";        want = 27; label = "知识库条目" },
    @{ q = "select count(*) from tz_knowledge_base where key_features is not null"; want = 27; label = "有特征性表现的条目" },
    @{ q = "select count(*) from tz_menu where menu_id >= 2000"; want = 44; label = "田诊相关菜单" },
    @{ q = "select count(*) from information_schema.columns where table_schema='$Db' and table_name='tz_image_preset' and column_name='symptom_text'"; want = 1; label = "预置样张 symptom_text 列" }
)
foreach ($c in $checks) {
    $got = Sql $c.q $Db
    if ($got -eq "$($c.want)") { Good "$($c.label)：$got" }
    else { Bad "$($c.label)：期望 $($c.want)，实际 $got" }
}

# ---------------------------------------------------------------- 4. 下一步
Write-Host ""
Write-Host ("=" * 58)
Good "数据库初始化完成"
Info ""
Info "接下来："
Info "  1) 配置数据源：" 
Info "     copy backend\ruoyi-admin\src\main\resources\application-druid.yml.example ^"
Info "          backend\ruoyi-admin\src\main\resources\application-druid.yml"
Info "     并把 password 改成你本机 MySQL 口令"
Info "  2) 起服务：mvn -o -B -f backend\pom.xml -DskipTests package"
Info "             java -jar backend\ruoyi-admin\target\ruoyi-admin.jar --server.port=18080"
Info "             cd frontend ; npm install ; npm run dev -- --host 127.0.0.1 --port 18081"
Info "  3) 生成演示数据（走真实接口，顺带全链路回归）：python scripts\seed-demo.py"
Info "  4) 环境自检：powershell -NoProfile -File scripts\check-env.ps1"
Info ""
Info "默认账号 admin / admin123（若依初始口令，登录后请立即修改）"
