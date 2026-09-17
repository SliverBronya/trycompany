<#
    田诊助手 · 大模型密钥配置

    用法（在仓库根目录）：
        powershell -NoProfile -File scripts\set-keys.ps1

    会依次询问两个密钥，**直接回车表示跳过（保持原值不变）**。
    结果写入 scripts\.secrets.local —— 该文件已被 .gitignore 排除，不会进仓库。

    ── 两个密钥是什么关系 ──────────────────────────────
    视觉模型（读巡田照片）与文本模型（问答、报告润色）可以各配一个 key。
    当前视觉使用智谱、文本使用 DeepSeek，两个服务商各有自己的密钥：
        TZ_AI_API_KEY       供视觉模型使用
        TZ_AI_TEXT_API_KEY  供 DeepSeek 文本模型使用
    如果两路使用同一服务商，只填共用密钥也可以；不同服务商时不要混用 key。

    ── 本文件的编码 ────────────────────────────────────
    必须存为 UTF-8 带 BOM，否则 PowerShell 5.1 会按 GBK 读，中文全乱。
#>

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$SecretsPath = Join-Path $PSScriptRoot ".secrets.local"

# ---------------------------------------------------------------- 读取现有配置
$config = [ordered]@{}
if (Test-Path $SecretsPath) {
    foreach ($line in (Get-Content -Path $SecretsPath -Encoding UTF8)) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        $kv = $line -split '=', 2
        if ($kv.Count -eq 2) { $config[$kv[0].Trim()] = $kv[1].Trim() }
    }
}

function Show-Current($name) {
    $cur = $config[$name]
    if ([string]::IsNullOrWhiteSpace($cur)) { return "（当前为空）" }
    $head = $cur.Substring(0, [Math]::Min(6, $cur.Length))
    return "（已设置：${head}…）"
}

function Ask-Key($name, $label, $hint) {
    Write-Host ""
    Write-Host "  $label " -NoNewline -ForegroundColor Cyan
    Write-Host (Show-Current $name) -ForegroundColor DarkGray
    Write-Host "    $hint" -ForegroundColor DarkGray
    $v = Read-Host "    回车跳过，或粘贴新值"
    if (-not [string]::IsNullOrWhiteSpace($v)) {
        $config[$name] = $v.Trim()
        Write-Host "    ✓ 已更新" -ForegroundColor Green
    } else {
        Write-Host "    - 保持原值" -ForegroundColor DarkGray
    }
}

# ---------------------------------------------------------------- 交互
Write-Host ""
Write-Host "田诊助手 · 大模型密钥配置" -ForegroundColor White
Write-Host ("=" * 60)
Write-Host "  留空回车 = 不修改。想清空某一项，输入一个短横线 -" -ForegroundColor DarkGray

Ask-Key "TZ_AI_API_KEY" `
    "① 视觉模型密钥（智谱 GLM）" `
    "当前用于读巡田照片。"

Ask-Key "TZ_AI_VISION_API_KEY" `
    "② 视觉模型专用密钥（可留空）" `
    "留空 = 用①。只有视觉要单独走另一家/另一个额度时才填。"

Ask-Key "TZ_AI_TEXT_API_KEY" `
    "③ 文本模型密钥（DeepSeek）" `
    "当前用于问答、建议和报告相关文本生成。"

# 输入短横线表示清空
foreach ($k in @("TZ_AI_API_KEY", "TZ_AI_VISION_API_KEY", "TZ_AI_TEXT_API_KEY")) {
    if ($config[$k] -eq "-") { $config[$k] = "" }
}

# 这两个不是大模型密钥，但同一份文件里，缺了就补上默认值
if (-not $config.Contains("TZ_PASSWORD"))    { $config["TZ_PASSWORD"] = "" }
if (-not $config.Contains("TZ_DB_PASSWORD")) { $config["TZ_DB_PASSWORD"] = "" }

# ---------------------------------------------------------------- 写回
$lines = @()
$lines += "# 本机专用口令 —— 这个文件被 .gitignore 排除，不会进仓库"
$lines += "# 供 scripts\start-all.ps1、test-api.py、seed-demo.py、check-env.ps1 读取"
$lines += ""
$lines += "# ---- 大模型密钥（用 scripts\set-keys.ps1 修改）----"
$lines += "# 视觉模型密钥；留空则视觉链路自动降级为预置映射 + 知识库检索"
$lines += "TZ_AI_API_KEY=$($config['TZ_AI_API_KEY'])"
$lines += "# 文本模型密钥；当前 DeepSeek 文本服务商需要单独的 key"
$lines += "TZ_AI_VISION_API_KEY=$($config['TZ_AI_VISION_API_KEY'])"
$lines += "TZ_AI_TEXT_API_KEY=$($config['TZ_AI_TEXT_API_KEY'])"
$lines += ""
$lines += "# ---- 应用账号与数据库口令 ----"
$lines += "TZ_PASSWORD=$($config['TZ_PASSWORD'])"
$lines += "TZ_DB_PASSWORD=$($config['TZ_DB_PASSWORD'])"
$lines += ""

# UTF-8 带 BOM，避免下次读回来时中文乱码
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllText($SecretsPath, ($lines -join "`r`n"), $utf8Bom)

# ---------------------------------------------------------------- 汇总
Write-Host ""
Write-Host ("=" * 60)
Write-Host "已写入 $SecretsPath" -ForegroundColor Green
Write-Host ""
$shared = -not [string]::IsNullOrWhiteSpace($config["TZ_AI_API_KEY"])
$vision = -not [string]::IsNullOrWhiteSpace($config["TZ_AI_VISION_API_KEY"])
$text   = -not [string]::IsNullOrWhiteSpace($config["TZ_AI_TEXT_API_KEY"])

if ($shared) {
    Write-Host "  视觉/共用密钥 ..... 已配置" -ForegroundColor Green
    if ($text) { Write-Host "  文本模型 ........... 使用独立 DeepSeek 密钥" -ForegroundColor Green }
    else { Write-Host "  文本模型 ........... 回退到共用密钥" -ForegroundColor Yellow }
} else {
    Write-Host "  视觉/共用密钥 ..... 未配置" -ForegroundColor DarkGray
    if ($vision) { Write-Host "  视觉模型 ......... 用自己的密钥" -ForegroundColor Green }
    else { Write-Host "  视觉模型 ......... 无可用密钥（读图将降级）" -ForegroundColor Yellow }
    if ($text) { Write-Host "  文本模型 ......... 用自己的密钥" -ForegroundColor Green }
    else { Write-Host "  文本模型 ......... 无可用密钥（问答将降级）" -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "现在可以运行：  powershell -NoProfile -File scripts\start-all.ps1" -ForegroundColor Cyan
Write-Host "（或直接双击根目录的「一键启动.bat」）" -ForegroundColor DarkGray
Write-Host ""
