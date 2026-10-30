# ============================================================================
#  食光 Foodie —— 停止服务
#
#  一般用法：双击桌面上的「停止食光Foodie.bat」
#  什么时候需要它：启动窗口被强制关闭（Alt+F4 / 任务管理器）后，
#  后端进程可能还活着，用这个脚本来收尾。
# ============================================================================
[CmdletBinding()]
param(
    [switch]$NoWait
)

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogDir    = Join-Path $ProjectRoot 'logs'
$PidFile   = Join-Path $LogDir 'backend.pid'
$HealthUrl = 'http://localhost:8080/actuator/health'
$AppPort   = 8080

try { $Host.UI.RawUI.WindowTitle = '食光 Foodie 停止服务' } catch { }

function Test-AppReady {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2
        return ($response.StatusCode -eq 200)
    } catch {
        return $false
    }
}

# 用 netstat 而不是 Get-NetTCPConnection：
# 后者在部分受限环境下会静默返回空结果，导致「明明在跑却判定没有」。
function Get-ListeningPid([int]$port) {
    $lines = netstat -ano | Select-String ":$port\s" | Select-String 'LISTENING'
    foreach ($line in $lines) {
        $parts = @($line.Line.Trim() -split '\s+')
        $candidate = $parts[-1]
        if ($candidate -match '^\d+$' -and [int]$candidate -ne 0) {
            return [int]$candidate
        }
    }
    return $null
}

function Stop-ProcessTree([int]$targetPid) {
    & taskkill /PID $targetPid /T /F 2>$null | Out-Null
    for ($i = 0; $i -lt 16; $i++) {
        if (-not (Get-Process -Id $targetPid -ErrorAction SilentlyContinue)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return (-not (Get-Process -Id $targetPid -ErrorAction SilentlyContinue))
}

Write-Host ''
Write-Host '================================================' -ForegroundColor DarkCyan
Write-Host '   停止食光 Foodie' -ForegroundColor Cyan
Write-Host '================================================' -ForegroundColor DarkCyan
Write-Host ''

$targets = New-Object System.Collections.ArrayList

# 1) 上次启动时记录的 PID
if (Test-Path $PidFile) {
    $rawPid = (Get-Content $PidFile -Raw -ErrorAction SilentlyContinue)
    if ($rawPid) { $rawPid = $rawPid.Trim() }
    if ($rawPid -match '^\d+$') { [void]$targets.Add([int]$rawPid) }
}

# 2) 真正在监听 8080 的进程（最可靠，不依赖 PID 文件）
$listeningPid = Get-ListeningPid -port $AppPort
if ($listeningPid) { [void]$targets.Add($listeningPid) }

$uniqueTargets = @($targets | Select-Object -Unique)

if ($uniqueTargets.Count -eq 0 -and -not (Test-AppReady)) {
    Write-Host '   [OK] 没有发现正在运行的服务。' -ForegroundColor Green
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
} else {
    $killedAny = $false
    foreach ($target in $uniqueTargets) {
        $proc = Get-Process -Id $target -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "   正在结束进程 PID=$target（$($proc.ProcessName)）..." -ForegroundColor Gray
            if (Stop-ProcessTree -targetPid $target) { $killedAny = $true }
        }
    }

    # 轮询等待端口真正释放：Spring Boot 是「优雅停机」，
    # 杀掉进程后端口还要几秒才消失。这里必须等，否则会误报「仍有残留」。
    $released = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (-not (Get-ListeningPid -port $AppPort) -and -not (Test-AppReady)) {
            $released = $true
            break
        }
    }

    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue

    Write-Host ''
    if ($released) {
        Write-Host '   [OK] 服务已停止。' -ForegroundColor Green
    } else {
        Write-Host '   [警告] 端口 8080 仍被占用，可能有残留进程。' -ForegroundColor Yellow
        $stillPid = Get-ListeningPid -port $AppPort
        if ($stillPid) {
            Write-Host "          占用者 PID=$stillPid，请在任务管理器里结束它后重试。" -ForegroundColor Yellow
        }
    }
}

Write-Host ''
if (-not $NoWait) {
    Read-Host '按回车键关闭此窗口' | Out-Null
}
