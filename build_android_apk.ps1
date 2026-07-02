$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AndroidProject = Join-Path $Root "android"
$SdkRoot = "G:\Android\Sdk"
$BuildTools = Join-Path $SdkRoot "build-tools\36.0.0"
$Platform = Join-Path $SdkRoot "platforms\android-36\android.jar"

$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"

function Assert-LastExitCode($Step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

foreach ($tool in @($Aapt2, $D8, $ZipAlign, $ApkSigner, $Platform)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "Missing Android build dependency: $tool"
    }
}

$Javac = (Get-Command javac -ErrorAction Stop).Source
$Jar = (Get-Command jar -ErrorAction Stop).Source
$Keytool = (Get-Command keytool -ErrorAction Stop).Source

$BuildDir = Join-Path $AndroidProject "build"
$GenDir = Join-Path $BuildDir "generated"
$ClassDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
$OutDir = Join-Path $BuildDir "outputs"
$CompiledRes = Join-Path $BuildDir "compiled-res.zip"
$UnsignedApk = Join-Path $BuildDir "unsigned.apk"
$AlignedApk = Join-Path $BuildDir "aligned.apk"
$FinalApk = Join-Path $OutDir "VsingerSudoku-debug.apk"
$Manifest = Join-Path $AndroidProject "src\main\AndroidManifest.xml"
$ResDir = Join-Path $AndroidProject "src\main\res"
$SrcDir = Join-Path $AndroidProject "src\main\java"
$Keystore = Join-Path $AndroidProject "debug.keystore"

if (Test-Path -LiteralPath $BuildDir) {
    Remove-Item -LiteralPath $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $GenDir, $ClassDir, $DexDir, $OutDir | Out-Null

Write-Host "==> Compile Android resources"
& $Aapt2 compile --dir $ResDir -o $CompiledRes
Assert-LastExitCode "aapt2 compile"

Write-Host "==> Link Android package"
& $Aapt2 link `
    -o $UnsignedApk `
    -I $Platform `
    --manifest $Manifest `
    --java $GenDir `
    --min-sdk-version 23 `
    --target-sdk-version 36 `
    --auto-add-overlay `
    $CompiledRes
Assert-LastExitCode "aapt2 link"

Write-Host "==> Compile Java"
$Sources = @()
$Sources += Get-ChildItem -LiteralPath $SrcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$Sources += Get-ChildItem -LiteralPath $GenDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $Javac -encoding UTF-8 -source 8 -target 8 -classpath $Platform -d $ClassDir @Sources
Assert-LastExitCode "javac"

Write-Host "==> Convert classes to DEX"
$ClassFiles = @(Get-ChildItem -LiteralPath $ClassDir -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& $D8 --release --min-api 23 --lib $Platform --output $DexDir @ClassFiles
Assert-LastExitCode "d8"

Write-Host "==> Add DEX to APK"
& $Jar uf $UnsignedApk -C $DexDir classes.dex
Assert-LastExitCode "jar update"

Write-Host "==> Zipalign"
& $ZipAlign -f 4 $UnsignedApk $AlignedApk
Assert-LastExitCode "zipalign"

if (-not (Test-Path -LiteralPath $Keystore)) {
    Write-Host "==> Create debug keystore"
    & $Keytool -genkeypair `
        -v `
        -keystore $Keystore `
        -storepass android `
        -alias androiddebugkey `
        -keypass android `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    Assert-LastExitCode "keytool"
}

Write-Host "==> Sign APK"
& $ApkSigner sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $FinalApk `
    $AlignedApk
Assert-LastExitCode "apksigner sign"

Write-Host "==> Verify APK"
& $ApkSigner verify --verbose $FinalApk
Assert-LastExitCode "apksigner verify"

Write-Host ""
Write-Host "APK built: $FinalApk"
