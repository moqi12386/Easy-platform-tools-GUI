# 用 Android build-tools 直接打 APK（不依赖 Gradle）
# 用法：powershell -ExecutionPolicy Bypass -File android\build-apk.ps1
param(
    [string]$SdkRoot = 'C:\Users\35923\android-build',
    [string]$JdkRoot  = 'C:\Users\35923\android-build\jdk'
)
$ErrorActionPreference = 'Stop'

$proj = $PSScriptRoot
$out  = Join-Path $proj 'out'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out, "$out\classes", "$out\dex" -Force | Out-Null

$aapt2     = (Get-ChildItem $SdkRoot -Recurse -Filter aapt2.exe     | Select-Object -First 1).FullName
$zipalign  = (Get-ChildItem $SdkRoot -Recurse -Filter zipalign.exe  | Select-Object -First 1).FullName
$d8        = (Get-ChildItem $SdkRoot -Recurse -Filter d8.bat        | Select-Object -First 1).FullName
$apksigner = (Get-ChildItem $SdkRoot -Recurse -Filter apksigner.bat | Select-Object -First 1).FullName
$androidJar= (Get-ChildItem $SdkRoot -Recurse -Filter android.jar   | Select-Object -First 1).FullName
$keytool   = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
if (-not $keytool) { $keytool = (Get-ChildItem (Split-Path (Get-Command java).Source) -Filter keytool.exe | Select-Object -First 1).FullName }

foreach ($t in @($aapt2, $zipalign, $d8, $apksigner, $androidJar, $keytool)) {
    if (-not $t -or -not (Test-Path $t)) { throw "缺少构建工具：$t" }
}
$jdk = (Get-ChildItem $JdkRoot -Directory | Select-Object -First 1)
if (-not $jdk) { throw "找不到便携 JDK：$JdkRoot" }
$jdk = $jdk.FullName
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;" + $env:PATH
$javac = Join-Path $jdk "bin\javac.exe"

Write-Host "aapt2      $aapt2"
Write-Host "android.jar $androidJar"

# 1) 链接清单，产出无 dex 的 base.apk
$baseApk = Join-Path $out 'base.apk'
& $aapt2 link -o $baseApk --manifest (Join-Path $proj 'AndroidManifest.xml') `
    -I $androidJar --min-sdk-version 26 --target-sdk-version 33 --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

# 2) 编译 Java
$sources = Get-ChildItem (Join-Path $proj 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 8 -encoding UTF-8 -nowarn -cp $androidJar -d "$out\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

# 3) d8 → classes.dex
$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& $d8 --release --lib $androidJar --min-api 26 --output "$out\dex" $classes
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }

# 4) 把 classes.dex 塞进 apk
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($baseApk, 'Update')
$dex = Join-Path $out 'dex\classes.dex'
$entry = $zip.GetEntry('classes.dex')
if ($entry) { $entry.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex, 'classes.dex') | Out-Null
$zip.Dispose()

# 5) 对齐 + 签名
$aligned = Join-Path $out 'aligned.apk'
& $zipalign -f 4 $baseApk $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

$ks = Join-Path $proj 'easypt.keystore'
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -storepass android -keypass android `
        -alias easypt -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Easy Platform Tools, O=EasyPT, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool 失败" }
}

$final = Join-Path $proj 'EasyPT.apk'
if (Test-Path $final) { Remove-Item $final -Force }
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --min-sdk-version 26 --out $final $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner 失败" }

& $apksigner verify --min-sdk-version 26 $final
if ($LASTEXITCODE -ne 0) { throw "签名校验失败" }

Write-Host ("OK  " + $final + "  " + [math]::Round((Get-Item $final).Length / 1KB, 1) + " KB")
