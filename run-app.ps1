$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSCommandPath
Set-Location $projectRoot

$avdName = 'Medium_Phone_API_37.0'
$applicationId = 'com.garethevans.church.opensongtablet'
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'

$sdkDirectory = $env:ANDROID_SDK_ROOT
if (-not $sdkDirectory) {
    $sdkDirectory = $env:ANDROID_HOME
}
if (-not $sdkDirectory -and (Test-Path (Join-Path $projectRoot 'local.properties'))) {
    $sdkLine = Get-Content (Join-Path $projectRoot 'local.properties') |
        Where-Object { $_ -match '^sdk\.dir=' } |
        Select-Object -First 1
    if ($sdkLine) {
        $sdkDirectory = $sdkLine.Substring($sdkLine.IndexOf('=') + 1).Replace('\\', '\')
    }
}
if (-not $sdkDirectory) {
    $sdkDirectory = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$adbPath = Join-Path $sdkDirectory 'platform-tools\adb.exe'
$emulatorPath = Join-Path $sdkDirectory 'emulator\emulator.exe'
if (-not (Test-Path $adbPath)) {
    throw "adb was not found at '$adbPath'. Check local.properties or ANDROID_SDK_ROOT."
}
if (-not (Test-Path $emulatorPath)) {
    throw "The Android emulator was not found at '$emulatorPath'. Install it from Android Studio's SDK Manager."
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaCandidates = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path $env:ProgramFiles 'Java\jdk-21.0.11')
    )
    $javaHome = $javaCandidates |
        Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } |
        Select-Object -First 1
    if ($javaHome) {
        $env:JAVA_HOME = $javaHome
    }
}

$availableAvds = @(& $emulatorPath -list-avds)
if ($LASTEXITCODE -ne 0 -or $availableAvds -notcontains $avdName) {
    throw "The AVD '$avdName' was not found. Create or rename it in Android Studio's Device Manager."
}

Write-Host 'Starting the Android emulator if it is not already running...'
& $adbPath start-server | Out-Null
$deviceLines = @(& $adbPath devices)
$hasEmulatorTransport = $deviceLines |
    Where-Object { $_ -match '^emulator-\d+\s+(device|offline)\b' } |
    Select-Object -First 1
$runningAvd = Get-CimInstance Win32_Process -Filter "Name = 'qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match [regex]::Escape($avdName) } |
    Select-Object -First 1

if (-not $hasEmulatorTransport -and -not $runningAvd) {
    Start-Process -FilePath $emulatorPath -ArgumentList @('-avd', $avdName, '-no-snapshot', '-gpu', 'angle_indirect') | Out-Null
}

Write-Host 'Building the latest debug APK...'
& (Join-Path $projectRoot 'gradlew.bat') --console=plain :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path $apkPath)) {
    throw "The build completed but the APK was not found at '$apkPath'."
}

Write-Host 'Waiting for Android to finish booting...'
$serial = $null
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    $deviceLines = @(& $adbPath devices)
    foreach ($line in $deviceLines) {
        if ($line -match '^(emulator-\d+)\s+device\b') {
            $candidateSerial = $Matches[1]
            $bootState = (& $adbPath -s $candidateSerial shell getprop sys.boot_completed 2>$null |
                Select-Object -First 1)
            if ("$bootState".Trim() -eq '1') {
                $serial = $candidateSerial
                break
            }
        }
    }
    if ($serial) {
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $serial) {
    throw 'The emulator did not finish booting within 180 seconds. Check the emulator window and try again.'
}

Write-Host "Installing OpenSong on $serial..."
& $adbPath -s $serial install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK installation failed with exit code $LASTEXITCODE."
}

Write-Host 'Launching OpenSong...'
& $adbPath -s $serial shell monkey -p $applicationId 1
if ($LASTEXITCODE -ne 0) {
    throw "OpenSong launch failed with exit code $LASTEXITCODE."
}
Write-Host 'OpenSong is running in the emulator.'