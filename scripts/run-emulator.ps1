#Requires -Version 5.1
<#
.SYNOPSIS
  Build the Calories debug APK and run it on an Android emulator.

.DESCRIPTION
  1. Resolves the Android SDK
  2. Starts an AVD if no device/emulator is online
  3. Builds ./gradlew :app:assembleDebug
  4. Installs and launches the app

.PARAMETER AvdName
  AVD to start when no emulator is running. If omitted, uses the first available AVD.

.PARAMETER SkipBuild
  Skip Gradle build and only install/launch the existing debug APK.

.EXAMPLE
  .\scripts\run-emulator.ps1

.EXAMPLE
  .\scripts\run-emulator.ps1 -AvdName Pixel_8_API_34
#>
[CmdletBinding()]
param(
    [string]$AvdName,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AppId = "com.example.calories"
$LaunchActivity = "com.example.calories.ui.auth.LoginActivity"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Resolve-AndroidSdk {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    )

    $localProps = Join-Path $ProjectRoot "local.properties"
    if (Test-Path $localProps) {
        $sdkLine = Get-Content $localProps | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkDir = ($sdkLine -split '=', 2)[1].Trim()
            # Gradle escapes backslashes: C\:\\Users\\...
            $sdkDir = $sdkDir -replace '\\\\', '\' -replace '\\:', ':'
            $candidates = @($sdkDir) + $candidates
        }
    }

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Android SDK not found. Set ANDROID_HOME or create local.properties with sdk.dir=..."
}

function Get-SdkTool([string]$SdkRoot, [string]$RelativePath) {
    $full = Join-Path $SdkRoot $RelativePath
    if (-not (Test-Path $full)) {
        throw "Required SDK tool not found: $full"
    }
    return $full
}

function Get-OnlineDevices([string]$Adb) {
    & $Adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '\S' -and $_ -match '\tdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] }
}

function Wait-ForDevice([string]$Adb, [int]$TimeoutSec = 180) {
    Write-Step "Waiting for device (up to ${TimeoutSec}s)..."
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $online = @(Get-OnlineDevices $Adb)
        if ($online.Count -gt 0) {
            & $Adb wait-for-device | Out-Null
            $boot = & $Adb shell getprop sys.boot_completed 2>$null
            if (($boot | Out-String).Trim() -eq "1") {
                Write-Host "Device ready: $($online[0])"
                return $online[0]
            }
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for emulator/device to become ready."
}

function Get-EmulatorPathDirs([string]$SdkRoot) {
    $emuDir = Join-Path $SdkRoot "emulator"
    return @(
        $emuDir,
        (Join-Path $emuDir "lib64"),
        (Join-Path $emuDir "lib64\qt\lib"),
        (Join-Path $SdkRoot "platform-tools")
    ) | Where-Object { Test-Path $_ }
}

function Ensure-EmulatorPath([string]$SdkRoot) {
    # Emulator/qemu need ROOT, lib64, and lib64\qt\lib on PATH for Windows DLL load.
    $extra = @(Get-EmulatorPathDirs $SdkRoot)
    $pathParts = @($env:PATH -split ';' | Where-Object { $_ -and $_.Trim() -ne "" })
    foreach ($dir in $extra) {
        if ($pathParts -notcontains $dir) {
            $pathParts = @($dir) + $pathParts
        }
    }
    $env:PATH = $pathParts -join ';'
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot

    $qtPlugins = Join-Path $SdkRoot "emulator\lib64\qt\plugins"
    if (Test-Path $qtPlugins) {
        $env:QT_PLUGIN_PATH = $qtPlugins
        $env:QT_QPA_PLATFORM_PLUGIN_PATH = $qtPlugins
    }
    $webEngine = Join-Path $SdkRoot "emulator\lib64\qt\libexec\QtWebEngineProcess.exe"
    if (Test-Path $webEngine) {
        $env:QTWEBENGINEPROCESS_PATH = $webEngine
    }
}

function Ensure-QemuDlls([string]$SdkRoot) {
    # Windows loads DLLs from the EXE directory first. Link critical DLLs from
    # emulator root + lib64 + qt\lib next to qemu so load works even if PATH races.
    $emuDir = Join-Path $SdkRoot "emulator"
    $qemuDir = Join-Path $emuDir "qemu\windows-x86_64"
    if (-not (Test-Path $qemuDir)) {
        throw "QEMU directory not found: $qemuDir"
    }

    $sources = @(
        (Get-ChildItem -Path $emuDir -Filter "*.dll" -File -ErrorAction SilentlyContinue)
        (Get-ChildItem -Path (Join-Path $emuDir "lib64") -Filter "*.dll" -File -ErrorAction SilentlyContinue)
        (Get-ChildItem -Path (Join-Path $emuDir "lib64\qt\lib") -Filter "*.dll" -File -ErrorAction SilentlyContinue)
    )

    $linked = 0
    foreach ($dll in $sources) {
        if (-not $dll) { continue }
        $dest = Join-Path $qemuDir $dll.Name
        if (Test-Path $dest) { continue }
        try {
            New-Item -ItemType HardLink -Path $dest -Target $dll.FullName -ErrorAction Stop | Out-Null
            $linked++
        } catch {
            Copy-Item -LiteralPath $dll.FullName -Destination $dest -Force
            $linked++
        }
    }
    if ($linked -gt 0) {
        Write-Host "Linked/copied $linked emulator DLL(s) next to qemu."
    }
}

function Start-EmulatorIfNeeded([string]$Emulator, [string]$Adb, [string]$PreferredAvd) {
    $online = @(Get-OnlineDevices $Adb)
    if ($online.Count -gt 0) {
        Write-Host "Using existing device: $($online[0])"
        return
    }

    Write-Step "No device online - listing AVDs..."
    $avds = @(& $Emulator -list-avds 2>$null | Where-Object { $_.Trim() -ne "" })
    if ($avds.Count -eq 0) {
        throw "No Android Virtual Devices found. Create one in Android Studio (Device Manager)."
    }

    $target = $PreferredAvd
    if ([string]::IsNullOrWhiteSpace($target)) {
        $target = $avds[0]
    } elseif ($avds -notcontains $target) {
        throw "AVD '$target' not found. Available: $($avds -join ', ')"
    }

    Write-Step "Starting emulator: $target"
    $emuDir = Split-Path -Parent $Emulator
    # Launch via cmd `start` so PATH/QT_* from this session are inherited,
    # but the emulator is detached (survives after this script exits).
    $args = "/c start `"`" /D `"$emuDir`" `"$Emulator`" -avd $target -netdelay none -netspeed full"
    Start-Process -FilePath "$env:ComSpec" -ArgumentList $args -WindowStyle Hidden | Out-Null
}

# --- main ---
Push-Location $ProjectRoot
try {
    Write-Step "Resolving Android SDK"
    $sdk = Resolve-AndroidSdk
    Write-Host "SDK: $sdk"
    Ensure-EmulatorPath $sdk
    Ensure-QemuDlls $sdk
    $adb = Get-SdkTool $sdk "platform-tools\adb.exe"
    $emulator = Get-SdkTool $sdk "emulator\emulator.exe"

    Start-EmulatorIfNeeded -Emulator $emulator -Adb $adb -PreferredAvd $AvdName
    $null = Wait-ForDevice -Adb $adb

    if (-not $SkipBuild) {
        Write-Step "Building debug APK"
        $gradlew = Join-Path $ProjectRoot "gradlew.bat"
        if (-not (Test-Path $gradlew)) {
            throw "gradlew.bat not found at $ProjectRoot"
        }
        & $gradlew ":app:assembleDebug" --quiet
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed (exit $LASTEXITCODE)."
        }
    }

    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath. Run without -SkipBuild first."
    }

    Write-Step "Installing $AppId"
    & $adb install -r -t $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "adb install failed (exit $LASTEXITCODE)."
    }

    Write-Step "Launching $LaunchActivity"
    & $adb shell am start -n "${AppId}/${LaunchActivity}"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to launch activity (exit $LASTEXITCODE)."
    }

    Write-Host ""
    Write-Host "Done. App is running on the emulator." -ForegroundColor Green
}
finally {
    Pop-Location
}
