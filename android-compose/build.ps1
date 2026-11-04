# Compose 版构建（Gradle + AGP 9 + Kotlin 2.4 + Compose）
# 用法：powershell -ExecutionPolicy Bypass -File android-compose\build.ps1
param(
    [string]$Gradle = 'C:\Users\35923\gradle\gradle-9.3.1\bin\gradle.bat',
    [string]$Jdk    = 'C:\Users\35923\android-build\jdk\jdk-17.0.21.0.2+1',
    [string]$Sdk    = 'C:\Users\35923\android-sdk'
)
$ErrorActionPreference = 'Stop'
$proj = $PSScriptRoot
"android.useAndroidX=true`nandroid.nonTransitiveRClass=false`nandroid.suppressUnsupportedCompileSdk=37`norg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8`norg.gradle.java.home=$($Jdk -replace '\\','\\')`norg.gradle.parallel=true`norg.gradle.caching=true" |
    Set-Content (Join-Path $proj 'gradle.properties') -Encoding UTF8 -NoNewline
"sdk.dir=$($Sdk -replace '\\','/')" | Set-Content (Join-Path $proj 'local.properties') -Encoding UTF8 -NoNewline

$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $Sdk
& $Gradle -p $proj :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败" }
$apk = Join-Path $proj 'app\build\outputs\apk\debug\app-debug.apk'
"OK  $apk  " + [math]::Round((Get-Item $apk).Length / 1MB, 2) + " MB"