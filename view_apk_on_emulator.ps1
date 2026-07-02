$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$SdkRoot = "G:\Android\Sdk"
$AvdHome = "G:\Android\avd"
$AvdName = "VsingerSudoku_API36"
$Device = "emulator-5554"
$Apk = Join-Path $Root "android\build\outputs\VsingerSudoku-debug.apk"

$Emulator = Join-Path $SdkRoot "emulator\emulator.exe"
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"

foreach ($path in @($Emulator, $Adb)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing Android tool: $path"
    }
}

if (-not (Test-Path -LiteralPath $Apk)) {
    Write-Host "APK not found, building first..."
    powershell -ExecutionPolicy Bypass -File (Join-Path $Root "build_android_apk.ps1")
}

$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_AVD_HOME = $AvdHome

Write-Host "==> Start adb server"
& $Adb start-server | Out-Host

$deviceLines = & $Adb devices
$hasDevice = $false
foreach ($line in $deviceLines) {
    if ($line -match "^$([regex]::Escape($Device))\s+device\b") {
        $hasDevice = $true
    }
}

if (-not $hasDevice) {
    Write-Host "==> Launch emulator: $AvdName"
    Start-Process -FilePath $Emulator -ArgumentList @(
        "-avd", $AvdName,
        "-gpu", "swiftshader_indirect",
        "-no-snapshot-load",
        "-netdelay", "none",
        "-netspeed", "full"
    )
}

Write-Host "==> Wait for emulator device"
$ready = $false
for ($i = 0; $i -lt 180; $i++) {
    Start-Sleep -Seconds 2
    $state = ""
    try {
        $state = (& $Adb -s $Device get-state 2>$null | Select-Object -First 1).Trim()
    } catch {
        $state = ""
    }
    if ($state -eq "device") {
        $ready = $true
        break
    }
}
if (-not $ready) {
    throw "Emulator did not become available through adb."
}

Write-Host "==> Wait for Android boot"
$booted = $false
for ($i = 0; $i -lt 180; $i++) {
    Start-Sleep -Seconds 2
    $boot = ""
    try {
        $boot = (& $Adb -s $Device shell getprop sys.boot_completed 2>$null | Select-Object -First 1).Trim()
    } catch {
        $boot = ""
    }
    if ($boot -eq "1") {
        $booted = $true
        break
    }
}
if (-not $booted) {
    throw "Emulator connected, but Android did not finish booting."
}

Write-Host "==> Install APK"
& $Adb -s $Device install -r -d --no-incremental $Apk | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed with exit code $LASTEXITCODE"
}

Write-Host "==> Launch app"
& $Adb -s $Device shell am start -n "com.vsinger.sudoku/.MainActivity" | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "adb launch failed with exit code $LASTEXITCODE"
}

Write-Host "Done. The emulator window should now show the app."
