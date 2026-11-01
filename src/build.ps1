# 把 AndroidTool.cs 编译成 AndroidTool.exe
$ErrorActionPreference = 'Stop'

$srcDir = $PSScriptRoot
$root   = Split-Path $srcDir -Parent
$csc    = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path $csc)) { $csc = 'C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe' }
if (-not (Test-Path $csc)) { throw '找不到 csc.exe（.NET Framework 4.x 缺失）' }

$ico = Join-Path $srcDir 'app.ico'
if (-not (Test-Path $ico)) { & (Join-Path $srcDir 'make-icon.ps1') }

$out = Join-Path $root 'Easy-platform-tools-GUI.exe'
if (Test-Path $out) { Remove-Item $out -Force }

$cscArgs = @(
    '/nologo'
    '/target:winexe'
    '/platform:anycpu'
    '/optimize+'
    '/codepage:65001'
    "/out:$out"
    "/win32icon:$ico"
    "/win32manifest:$(Join-Path $srcDir 'app.manifest')"
    '/reference:System.dll'
    '/reference:System.Drawing.dll'
    '/reference:System.Windows.Forms.dll'
    '/reference:System.IO.Compression.dll'
    '/reference:System.IO.Compression.FileSystem.dll'
    (Join-Path $srcDir 'EasyPlatformTools.cs')
)

Write-Host ('csc -> ' + $out)
& $csc $cscArgs
if ($LASTEXITCODE -ne 0) { throw "编译失败，退出码 $LASTEXITCODE" }
Write-Host ('OK  ' + (Get-Item $out).Length + ' bytes')
