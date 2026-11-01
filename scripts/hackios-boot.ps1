param(
    [string]$Uefi = '',
    [string]$Apfs = '',
    [switch]$Pick,
    [int]$Seconds = 600,
    [switch]$NoReboot,
    [switch]$Screenshot,
    [switch]$ForceUpload,
    [switch]$CheckOnly,
    [switch]$Wifi
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms

# ---------------- 配置 ----------------
$script:Root   = Split-Path -Parent $PSScriptRoot
$script:BinDir = Join-Path $script:Root 'bin'
$ADB     = Join-Path $script:BinDir 'adb.exe'
$FB      = Join-Path $script:BinDir 'fastboot.exe'
if (-not (Test-Path $ADB)) {                 # 兼容仍放在 C:\platform-tools 的情况
    $ADB = 'C:\platform-tools\adb.exe'
    $FB  = 'C:\platform-tools\fastboot.exe'
}
$HELPER  = '/mnt/c/hakios/hakios-run.sh'
$DISTRO  = 'Ubuntu-24.04'
$WSLUSER = 'dev'
$script:LogDir = Join-Path $script:Root 'logs'
if (-not (Test-Path $script:LogDir)) { New-Item -ItemType Directory -Path $script:LogDir -Force | Out-Null }
$script:StepNo = 0
$script:Docker = 'C:\hakios'

function Say   { param($m, $c = 'Gray') Write-Host $m -ForegroundColor $c }
function Step  { param($m) $script:StepNo++; Write-Host ("`n[{0}] {1}" -f $script:StepNo, $m) -ForegroundColor Cyan }
function Ok    { param($m) Write-Host ("    OK  " + $m) -ForegroundColor Green }
function Warn  { param($m) Write-Host ("     !  " + $m) -ForegroundColor Yellow }
function Fail  { param($m) Write-Host ("    x   " + $m) -ForegroundColor Red }
function Pause-Exit { if ($env:HAKIOS_NOPAUSE -ne '1') { Read-Host '按回车退出' } }

function Die {
    param($m, $hint)
    Fail $m
    if ($hint) { Write-Host ("        提示：" + $hint) -ForegroundColor DarkYellow }
    Write-Host ''
    Pause-Exit
    exit 1
}

function Invoke-Native {
    param([string]$exe, [string[]]$argv = @())
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = (($argv | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $proc = [System.Diagnostics.Process]::Start($psi)
    $out = $proc.StandardOutput.ReadToEnd()
    $err = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    return ($out + $err)
}

function Wsl {
    param([string]$Action, [string[]]$Extra = @())
    & wsl.exe -d $DISTRO -u $WSLUSER -- bash $HELPER $Action @Extra
}

function WslText {
    param([string]$Action, [string[]]$Extra = @())
    return ((Wsl $Action $Extra | Out-String).Trim())
}

function Wait-For {
    param([scriptblock]$Test, [int]$TimeoutSec = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (& $Test) { return $true }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Test-Fastboot { (Invoke-Native $FB @('devices')) -match '\t\s*fastboot' }
function Test-Android  { (Invoke-Native $ADB @('devices')) -match '\t\s*(device|recovery|sideload)' }
function Get-Product {
    $o = Invoke-Native $FB @('getvar', 'product')
    if ($o -match 'product:\s*(\S+)') { return $Matches[1] }
    return ''
}
function Get-LoaderState { return (WslText 'state') }

function Select-ImageFile {
    param([string]$Caption, [string]$Filter)
    $dlg = New-Object System.Windows.Forms.OpenFileDialog
    $dlg.Title = $Caption
    $dlg.Filter = $Filter
    $dlg.CheckFileExists = $true
    $dlg.Multiselect = $false
    if (Test-Path $script:Docker) { $dlg.InitialDirectory = $script:Docker }
    if ($dlg.ShowDialog() -eq 'OK') { return $dlg.FileName }
    return ''
}

function ConvertTo-WslPath {
    param([string]$Path)
    $full = (Resolve-Path -LiteralPath $Path).Path
    if ($full -match '^([A-Za-z]):\\(.*)$') {
        $drive = $Matches[1].ToLower()
        $rest = $Matches[2] -replace '\\', '/'
        return "/mnt/$drive/$rest"
    }
    return $full
}

function Get-ApfsMagic {
    param([string]$Path)
    try {
        $fs = [System.IO.File]::OpenRead($Path)
        $buf = New-Object byte[] 64
        $null = $fs.Read($buf, 0, 64)
        $fs.Close()
        return [System.Text.Encoding]::ASCII.GetString($buf, 32, 4)
    } catch { return '' }
}

# ---------------- 选择镜像 ----------------
if ($Pick -or [string]::IsNullOrWhiteSpace($Uefi) -or [string]::IsNullOrWhiteSpace($Apfs)) {
    Write-Host '请选择要刷入的镜像（取消则退出）' -ForegroundColor Cyan
    if ([string]::IsNullOrWhiteSpace($Uefi)) {
        $Uefi = Select-ImageFile '选择 UEFI 启动镜像（.img）' 'UEFI 镜像 (*.img)|*.img|所有文件 (*.*)|*.*'
        if ([string]::IsNullOrWhiteSpace($Uefi)) { exit 0 }
    }
    if ([string]::IsNullOrWhiteSpace($Apfs)) {
        $Apfs = Select-ImageFile '选择 APFS 系统镜像（.apfs）' 'APFS 镜像 (*.apfs)|*.apfs|所有文件 (*.*)|*.*'
        if ([string]::IsNullOrWhiteSpace($Apfs)) { exit 0 }
    }
}

$Uefi = (Resolve-Path -LiteralPath $Uefi).Path
$Apfs = (Resolve-Path -LiteralPath $Apfs).Path
$apfsWsl  = ConvertTo-WslPath $Apfs
$apfsName = Split-Path $Apfs -Leaf

if ($Wifi) { $mode = 'wifi' } else { $mode = 'nowifi' }
if ((-not $Wifi) -and ($apfsName -match 'wifi')) {
    Write-Host '注意：所选镜像名含 wifi，但未加 -Wifi，将以 wifi=0 启动。' -ForegroundColor Yellow
}

# ---------------- 开场 ----------------
Clear-Host
Say '===============================================' 'DarkCyan'
Say '  HakiOS 一键刷入   raphael / 纯内存，不写分区' 'Cyan'
Say '===============================================' 'DarkCyan'
Say ('  UEFI : ' + $Uefi) 'DarkGray'
Say ('  APFS : ' + $Apfs) 'DarkGray'
Say ("  模式 " + $mode + "   运行 " + $Seconds + " 秒   截图:" + $Screenshot.IsPresent + "   结束后重启:" + (-not $NoReboot)) 'DarkGray'

try {
    Start-Transcript -Path (Join-Path $script:LogDir ("run-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".log")) -Force | Out-Null
} catch { }

# ---------------- 1. 检查 ----------------
Step '检查环境与所选镜像'
foreach ($f in @($ADB, $FB)) { if (-not (Test-Path $f)) { Die "找不到 $f" '组件目录 bin 下应有 adb.exe / fastboot.exe' } }
Ok 'adb / fastboot'

if (-not (Test-Path $Uefi)) { Die ('UEFI 镜像不存在：' + $Uefi) }
$uefiSize = (Get-Item $Uefi).Length
if ($uefiSize -lt 512KB) { Die ('UEFI 镜像可疑，仅 ' + [math]::Round($uefiSize / 1KB) + ' KB') '通常应在 1 MB 以上' }
Ok ('UEFI  ' + [math]::Round($uefiSize / 1MB, 1) + ' MB   ' + (Split-Path $Uefi -Leaf))

$apfsSize = (Get-Item $Apfs).Length
if ($apfsSize -lt 64MB) { Die ('APFS 镜像过小：' + [math]::Round($apfsSize / 1MB, 1) + ' MB') '完整镜像通常 2 GB 以上' }
$magic = Get-ApfsMagic $Apfs
if ($magic -ne 'NXSB') { Warn ('APFS 魔数异常：' + $magic + '（可能不是 APFS 容器）') }
else { Ok ('APFS  ' + $apfsName + '  ' + [math]::Round($apfsSize / 1GB, 2) + ' GB   魔数 NXSB') }

if (-not (Test-Path 'C:\hakios\hakios-run.sh')) { Die '缺少辅助脚本 C:\hakios\hakios-run.sh' }
$probe = (Invoke-Native 'wsl.exe' @('-d', $DISTRO, '-u', $WSLUSER, '--', 'bash', $HELPER, 'check'))
if ($probe -notmatch 'YES') { Warn 'WSL 里没有默认镜像（不影响使用所选镜像）' } else { Ok ('WSL  ' + $DISTRO) }

# ---------------- 2. Bootloader ----------------
Step '让手机进入 Bootloader'
if (Test-Android) {
    Ok 'Android / Recovery 已连接'
    if (-not $CheckOnly) {
        Invoke-Native $ADB @('reboot', 'bootloader') | Out-Null
        Say '    等待进入 Bootloader…' 'DarkGray'
        if (-not (Wait-For { Test-Fastboot } 60)) { Die '60 秒内没进入 Bootloader' '手动：关机后按住 电源+音量下' }
        Ok '已进入 Bootloader'
    }
} elseif (Test-Fastboot) {
    Ok '已处于 Bootloader'
} else {
    Die '没有检测到设备' '插好数据线；Android 下需开 USB 调试，或手动进 Bootloader'
}

# ---------------- 3. loader ----------------
Step '准备 loader'
$needBoot = $true
if (-not $CheckOnly) {
    $state = Get-LoaderState
    if ($state -eq 'loader') {
        Ok 'loader 正常，跳过加载'
        $needBoot = $false
    } else {
        $product = Get-Product
        if ($product -eq 'xnuloader') {
            Warn 'loader 已失效（会话超时），自动复位重载'
            Invoke-Native $FB @('reboot') | Out-Null
            if (-not (Wait-For { Test-Android } 90)) { Die '复位后没能回到 Android' '长按电源 10 秒，再重跑' }
            Invoke-Native $ADB @('reboot', 'bootloader') | Out-Null
            if (-not (Wait-For { Test-Fastboot } 60)) { Die '没能重新进入 Bootloader' }
            Ok '已复位并回到 Bootloader'
        }
    }
}

if ($needBoot -and -not $CheckOnly) {
    (Invoke-Native $FB @('boot', $Uefi)) -split "`r?`n" | Where-Object { $_ -ne '' } | ForEach-Object { Say ('    ' + $_) 'DarkGray' }
    Say '    等待 loader 就绪（约 20 秒）…' 'DarkGray'
    if (-not (Wait-For { (Get-Product) -eq 'xnuloader' } 60)) { Die 'UEFI 没有驻留（product 不是 xnuloader）' '换一个 UEFI 镜像再试' }
    Ok 'product: xnuloader'
    if (-not (Wait-For { (Get-LoaderState) -eq 'loader' } 25)) { Die ('loader 状态异常：' + (Get-LoaderState)) '重跑脚本' }
    Ok 'xl state: loader'
}

if ($CheckOnly) {
    Write-Host ''
    Say '检查完毕，一切就绪（未改动任何东西）。' 'Green'
    Write-Host ''
    Pause-Exit
    exit 0
}

# ---------------- 4. 上传 ----------------
Step ('上传 ' + $apfsName + '（' + [math]::Round($apfsSize / 1GB, 2) + ' GB）')
$stamp = Join-Path $env:TEMP ('hakios-upload-' + $apfsName + '.stamp')
$needUpload = $true
if (-not $ForceUpload -and (Test-Path $stamp)) {
    $st = Get-Item $stamp
    if (((Get-Date) - $st.LastWriteTime).TotalMinutes -lt 20) {
        Warn '本会话刚上传过同一镜像，跳过（-ForceUpload 可强制重传）'
        $needUpload = $false
    }
}
if ($needUpload) {
    $t0 = Get-Date
    Wsl 'ramdisk' @($apfsWsl)
    $code = $LASTEXITCODE
    if ($code -eq 124) { Die '上传超时（15 分钟）' 'loader 可能失效；重跑会自动复位' }
    if ($code -ne 0) { Die ('镜像上传失败，退出码 ' + $code) '换 USB 口重试；中断后重跑会续传' }
    Set-Content -Path $stamp -Value $apfsSize -Force
    Ok ('上传完成，用时 ' + [math]::Round(((Get-Date) - $t0).TotalSeconds) + ' 秒')
}

# ---------------- 5. 运行 ----------------
Step ('启动 iOS（运行 ' + $Seconds + ' 秒）')
if ($Screenshot) {
    $shot = '/mnt/c/hakios/screen-' + (Get-Date -Format 'HHmmss') + '.png'
    Start-Process wsl.exe -ArgumentList '-d', $DISTRO, '-u', $WSLUSER, '--', 'bash', $HELPER, 'shot', $shot, '110' -WindowStyle Hidden
    Say ('    将在 110 秒后自动截图：' + $shot) 'DarkGray'
}
Say '    手机先闪内核文本，然后出现 iOS 桌面；期间不要拔线。' 'DarkGray'
Write-Host ''

Wsl 'run' @([string]$Seconds, $mode)
$runExit = $LASTEXITCODE
Remove-Item $stamp -Force -ErrorAction SilentlyContinue

if ($runExit -ne 0) { Warn ('xl run 退出码 ' + $runExit) } else { Ok '本轮到点结束，内核已交回 loader' }

# ---------------- 6. 收尾 ----------------
if (-not $NoReboot) {
    Step '重启回 Android'
    Wsl 'reboot' | Out-Null
    if (Wait-For { Test-Android } 60) { Ok '已回到 Android' } else { Warn '手机正在重启，稍等片刻' }
} else {
    Step '保留在 loader'
    Say '    可继续 ./xl run，或 ./xl reboot 回 Android' 'DarkGray'
}

Write-Host ''
Say '===============================================' 'DarkCyan'
Say '  完成。日志：脚本同目录 run-*.log' 'Green'
Say '===============================================' 'DarkCyan'
Write-Host ''
Pause-Exit
