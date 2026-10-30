# ============================================================================
#  食光 Foodie —— 一键启动
#
#  一般用法：双击桌面上的「启动食光Foodie.bat」
#  也可以在本目录右键用 PowerShell 运行本脚本
#
#  可选参数：
#    -Rebuild    强制重新编译后端（改了 Java 代码后需要）
#    -NoBrowser  不自动打开浏览器（排查问题时用）
#    -NoWait     启动后不等待回车（自动化/测试用）
# ============================================================================
[CmdletBinding()]
param(
    [switch]$Rebuild,
    [switch]$NoBrowser,
    [switch]$NoWait
)

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch { }

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BackendDir  = Join-Path $ProjectRoot 'backend'
$JarPath     = Join-Path $BackendDir 'target\backend-0.0.1-SNAPSHOT.jar'
$LogDir      = Join-Path $ProjectRoot 'logs'
$RunLog      = Join-Path $LogDir 'backend-run.log'
$RunErrLog   = Join-Path $LogDir 'backend-run.err.log'
$PidFile     = Join-Path $LogDir 'backend.pid'
$AppUrl      = 'http://localhost:8080'
$HealthUrl   = 'http://localhost:8080/actuator/health'
$StartTimeoutSeconds = 180

function Show-Title([string]$text) {
    Write-Host ''
    Write-Host '================================================' -ForegroundColor DarkCyan
    Write-Host "   $text" -ForegroundColor Cyan
    Write-Host '================================================' -ForegroundColor DarkCyan
}
function Show-Step([int]$n, [int]$total, [string]$text) {
    Write-Host ("[{0}/{1}] {2}" -f $n, $total, $text) -ForegroundColor White
}
function Show-Ok([string]$text)   { Write-Host "      [OK]   $text" -ForegroundColor Green }
function Show-Warn([string]$text) { Write-Host "      [警告] $text" -ForegroundColor Yellow }
function Show-Fail([string]$text) { Write-Host "      [失败] $text" -ForegroundColor Red }

function Test-AppReady {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3
        return ($response.StatusCode -eq 200)
    } catch {
        return $false
    }
}

function Test-PortBusy([int]$port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
        return [bool]$conn
    } catch {
        return $false
    }
}

function Wait-ForEnter {
    if ($NoWait) { return }
    Write-Host ''
    Read-Host '按回车键继续' | Out-Null
}

function Show-LogTail {
    if (Test-Path $RunLog) {
        Write-Host '      ------- 后端日志最后 25 行 -------' -ForegroundColor DarkGray
        Get-Content $RunLog -Tail 25 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
        Write-Host '      --------------------------------' -ForegroundColor DarkGray
    }
}

try { $Host.UI.RawUI.WindowTitle = '食光 Foodie 一键启动' } catch { }

Show-Title '食光 Foodie 一键启动'
Write-Host "   项目目录：$ProjectRoot"
Write-Host "   访问地址：$AppUrl"

$exitCode = 0
$startedByMe = $null

try {
    # ---------------------------------------------------------------- 1 已在运行？
    Show-Step 1 5 '检查服务是否已经在运行 ...'
    if (Test-AppReady) {
        Show-Ok '服务已经在运行，不需要重复启动'
        if (-not $NoBrowser) { Start-Process $AppUrl }
        Write-Host ''
        Write-Host '   已在浏览器中打开 http://localhost:8080' -ForegroundColor Gray
        Write-Host '   如果要重启，请先运行桌面上的「停止食光Foodie.bat」' -ForegroundColor Gray
        Wait-ForEnter
        exit 0
    }
    Show-Ok '尚未运行，继续启动'

    # ---------------------------------------------------------------- 2 Java
    Show-Step 2 5 '检查 Java 运行环境 ...'
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCommand) {
        Show-Fail '没有找到 java 命令'
        Write-Host ''
        Write-Host '   请先安装 JDK 21 或更高版本，并把 java 加入 PATH。' -ForegroundColor Yellow
        Show-LogTail
        $exitCode = 1
        Wait-ForEnter
        exit 1
    }
    $javaVersion = (& java -version 2>&1 | Select-Object -First 1)
    Show-Ok "$javaVersion"

    # ---------------------------------------------------------------- 3 MySQL
    Show-Step 3 5 '检查 MySQL 服务 ...'
    $mysqlService = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
    if (-not $mysqlService) {
        $mysqlService = Get-Service | Where-Object { $_.Name -like 'MySQL*' } | Select-Object -First 1
    }
    if (-not $mysqlService) {
        Show-Fail '没有找到 MySQL 服务'
        Write-Host ''
        Write-Host '   请先安装并启动 MySQL 8，然后重试。' -ForegroundColor Yellow
        $exitCode = 1
        Wait-ForEnter
        exit 1
    }
    if ($mysqlService.Status -eq 'Running') {
        Show-Ok "MySQL 服务 $($mysqlService.Name) 正在运行"
    } else {
        Show-Warn "MySQL 服务 $($mysqlService.Name) 未运行，尝试启动 ..."
        try {
            Start-Service $mysqlService.Name
            Start-Sleep -Seconds 3
            Show-Ok '已启动'
        } catch {
            Show-Fail "启动失败：$($_.Exception.Message)"
            Write-Host ''
            Write-Host '   请手动启动 MySQL 服务（可能需要管理员权限）后重试。' -ForegroundColor Yellow
            $exitCode = 1
            Wait-ForEnter
            exit 1
        }
    }

    # ---------------------------------------------------------------- 4 后端程序
    Show-Step 4 5 '检查后端程序（jar）...'
    if ($Rebuild -or -not (Test-Path $JarPath)) {
        Show-Warn '需要先编译后端，首次构建可能要几分钟，请耐心等待 ...'
        Push-Location $BackendDir
        try {
            & .\mvnw.cmd -B -DskipTests package
            $buildOk = ($LASTEXITCODE -eq 0)
        } finally {
            Pop-Location
        }
        if (-not $buildOk -or -not (Test-Path $JarPath)) {
            Show-Fail '后端编译失败，请查看上方 Maven 输出'
            $exitCode = 1
            Wait-ForEnter
            exit 1
        }
    }
    $jarMb = [math]::Round((Get-Item $JarPath).Length / 1MB, 1)
    Show-Ok "已就绪（$jarMb MB）"

    # ---------------------------------------------------------------- 5 启动
    Show-Step 5 5 "启动后端服务（约 20 秒，上限 $StartTimeoutSeconds 秒）..."
    if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    Remove-Item $RunLog, $RunErrLog, $PidFile -Force -ErrorAction SilentlyContinue

    $process = Start-Process `
        -FilePath 'java' `
        -ArgumentList "-jar `"$JarPath`"" `
        -WorkingDirectory $BackendDir `
        -RedirectStandardOutput $RunLog `
        -RedirectStandardError $RunErrLog `
        -WindowStyle Hidden `
        -PassThru
    $startedByMe = $process
    $process.Id | Out-File -FilePath $PidFile -Encoding ascii -Force

    $deadline = (Get-Date).AddSeconds($StartTimeoutSeconds)
    $ready = $false
    Write-Host -NoNewline '      等待就绪'
    while ((Get-Date) -lt $deadline) {
        if (Test-AppReady) { $ready = $true; break }
        if ($process.HasExited) { break }
        Start-Sleep -Milliseconds 900
        Write-Host -NoNewline '.'
    }
    Write-Host ''

    if (-not $ready) {
        Show-Fail '启动超时或进程提前退出'
        Show-LogTail
        Write-Host ''
        Write-Host '   常见原因：' -ForegroundColor Yellow
        Write-Host '     1. 数据库密码不对 —— 检查 backend\application-local.yml' -ForegroundColor Yellow
        Write-Host '     2. MySQL 未就绪或端口不是 3306' -ForegroundColor Yellow
        if (Test-PortBusy 8080) {
            Write-Host '     3. 8080 端口已被别的程序占用，请先关闭它' -ForegroundColor Yellow
        }
        & taskkill /PID $process.Id /T /F 2>$null | Out-Null
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
        $startedByMe = $null
        $exitCode = 1
        Wait-ForEnter
        exit 1
    }

    Show-Ok '启动成功！'
    if (-not $NoBrowser) { Start-Process $AppUrl }

    Write-Host ''
    Write-Host '  ==============================================' -ForegroundColor DarkGreen
    Write-Host '   应用已就绪' -ForegroundColor Green
    Write-Host '  ==============================================' -ForegroundColor DarkGreen
    Write-Host "   访问地址：$AppUrl" -ForegroundColor White
    if (-not $NoBrowser) { Write-Host '   （浏览器应该已经自动打开）' -ForegroundColor Gray }
    Write-Host ''
    Write-Host '   演示账号：' -ForegroundColor White
    Write-Host '     管理员  admin / 123456' -ForegroundColor Gray
    Write-Host '     普通用户 demo  / demo1234' -ForegroundColor Gray
    Write-Host ''
    Write-Host "   运行日志：$RunLog" -ForegroundColor Gray
    Write-Host ''
    Write-Host '  ----------------------------------------------' -ForegroundColor DarkGray
    Write-Host '   关闭此窗口 = 停止服务' -ForegroundColor Yellow
    Write-Host '   （若窗口是强制关掉的，请双击「停止食光Foodie.bat」）' -ForegroundColor DarkGray
    Write-Host '  ----------------------------------------------' -ForegroundColor DarkGray

    if (-not $NoWait) {
        Read-Host '按回车键停止服务并关闭窗口'
    }
} catch {
    Show-Fail "发生未预期的错误：$($_.Exception.Message)"
    Show-LogTail
    $exitCode = 1
    Wait-ForEnter
} finally {
    # -NoWait 的语义是「启动完就走、把服务留在后台」，用于自动化调用，
    # 因此不在退出时清理；交互式使用才会在回车后停掉服务。
    #
    # 这里刻意复用 stop-foodie.ps1，而不是自己再写一遍 taskkill：
    # 停止逻辑要处理「启动器 shim 与真实 JVM 是两个进程」「优雅停机导致端口延迟释放」
    # 这些坑，重复实现两份必然走样。
    if ($startedByMe -and -not $NoWait) {
        Write-Host ''
        Write-Host '正在停止服务 ...' -ForegroundColor Cyan
        & (Join-Path $PSScriptRoot 'stop-foodie.ps1') -NoWait
    }
}

exit $exitCode
