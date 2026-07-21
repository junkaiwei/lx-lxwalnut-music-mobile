[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('release-default', 'debug-custom')]
    [string]$Scenario,

    [Parameter(Mandatory)]
    [string]$OutputDirectory,

    [string]$AppPackageName,
    [string]$AppDeepLinkScheme,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdkRoot = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }),
    [string]$ReleaseApk,
    [string]$ArtifactSourceCommit,
    [string]$DeviceSerial,
    [string]$ActivityName = 'com.lxwalnut.music.mobile.MainActivity'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$androidRoot = Join-Path $repoRoot 'android'
$identityPath = Join-Path $repoRoot 'src\config\buildIdentity.ts'
$gradle = Join-Path $androidRoot 'gradlew.bat'

if (-not $AppPackageName) {
    $AppPackageName = if ($Scenario -eq 'release-default') { 'com.lxwalnut.music.mobile' } else { 'com.lxwalnut.music.phase0audit' }
}
if (-not $AppDeepLinkScheme) {
    $AppDeepLinkScheme = if ($Scenario -eq 'release-default') { 'lxmusic' } else { 'lxphaseaudit' }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'JAVA_HOME must point to a JDK containing bin\java.exe.'
}
if (-not $AndroidSdkRoot -or -not (Test-Path $AndroidSdkRoot)) {
    throw 'ANDROID_SDK_ROOT (or ANDROID_HOME) must point to an Android SDK.'
}

# Evidence is only trustworthy when it starts from a committed, unmodified tree.
$initialStatus = (& git -C $repoRoot status --porcelain | Out-String).Trim()
if ($initialStatus) {
    throw "Refusing to capture evidence from a dirty working tree:`n$initialStatus"
}
$head = (& git -C $repoRoot rev-parse HEAD).Trim()

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdkRoot
$env:ANDROID_SDK_ROOT = $AndroidSdkRoot

$aapt = Get-ChildItem (Join-Path $AndroidSdkRoot 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1
if (-not $aapt) { throw 'No aapt.exe was found under Android SDK build-tools.' }

$adb = Join-Path $AndroidSdkRoot 'platform-tools\adb.exe'
if ($DeviceSerial -and -not (Test-Path $adb)) { throw 'Device capture requires platform-tools\adb.exe.' }
if ($ReleaseApk -and -not (Test-Path $ReleaseApk)) { throw "Release APK does not exist: $ReleaseApk" }
if ($ReleaseApk -and -not $DeviceSerial) { throw 'Release APK capture requires -DeviceSerial.' }
if ($DeviceSerial -and -not $ReleaseApk) { throw 'Device capture requires -ReleaseApk.' }

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputRoot = (Resolve-Path $OutputDirectory).Path
$commandsFile = Join-Path $outputRoot 'commands.txt'

function Write-Utf8File([string]$Path, [object[]]$Lines) {
    $text = ($Lines | Out-String).TrimEnd() + [Environment]::NewLine
    [System.IO.File]::WriteAllText($Path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Invoke-Native([string]$WorkingDirectory, [string]$FilePath, [string[]]$Arguments) {
    # Gradle's batch wrapper must be called by cmd.exe so its Java child remains in
    # the wait chain. cmd also captures stderr without PowerShell reclassifying it.
    $stdoutPath = [System.IO.Path]::GetTempFileName()
    $stderrPath = [System.IO.Path]::GetTempFileName()
    $encodedArguments = (($Arguments | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' ')
    $commandPrefix = if ([System.IO.Path]::GetExtension($FilePath) -ieq '.bat') { 'call ' } else { '' }
    $command = $commandPrefix + '"' + $FilePath + '" ' + $encodedArguments + ' 1>"' + $stdoutPath + '" 2>"' + $stderrPath + '"'
    $previousLocation = Get-Location
    if ([System.IO.Path]::GetExtension($FilePath) -ieq '.bat') {
        # The condition documents why batch files require `call`; all commands use
        # the same direct cmd invocation so Java and adb output are captured alike.
    }
    try {
        Set-Location $WorkingDirectory
        & $env:ComSpec /d /c $command
        $exitCode = $LASTEXITCODE
        $stdout = [System.IO.File]::ReadAllText($stdoutPath)
        $stderr = [System.IO.File]::ReadAllText($stderrPath)
    } finally {
        Set-Location $previousLocation
        Remove-Item -LiteralPath $stdoutPath, $stderrPath -ErrorAction SilentlyContinue
    }
    return [pscustomobject]@{ Output = @($stdout.TrimEnd(), $stderr.TrimEnd()); ExitCode = $exitCode }
}

function Invoke-Checked([string]$Name, [string]$WorkingDirectory, [string]$FilePath, [string[]]$Arguments) {
    Add-Content -LiteralPath $commandsFile -Encoding utf8 "name=$Name`nworking_directory=$WorkingDirectory`ncommand=$FilePath $($Arguments -join ' ')`n"
    Push-Location $WorkingDirectory
    try {
        $result = Invoke-Native $WorkingDirectory $FilePath $Arguments
        Write-Utf8File (Join-Path $outputRoot "$Name.txt") $result.Output
        if ($result.ExitCode -ne 0) { throw "$Name failed with exit code $($result.ExitCode)." }
    } finally {
        Pop-Location
    }
}

$identityBefore = [System.IO.File]::ReadAllBytes($identityPath)
try {
    $javaCall = Invoke-Native $repoRoot (Join-Path $JavaHome 'bin\java.exe') @('-version')
    $gradleCall = Invoke-Native $androidRoot $gradle @('-version')
    $aaptCall = Invoke-Native $repoRoot $aapt @('version')
    if ($javaCall.ExitCode -ne 0 -or $gradleCall.ExitCode -ne 0 -or $aaptCall.ExitCode -ne 0) {
        throw 'Failed to obtain Java, Gradle, or aapt version for provenance.'
    }
    $javaVersion = $javaCall.Output
    $gradleVersion = $gradleCall.Output
    $aaptVersion = $aaptCall.Output
    $provenance = @(
        "head=$head",
        'git_status_porcelain=',
        "scenario=$Scenario",
        "APP_PACKAGE_NAME=$AppPackageName",
        "APP_DEEP_LINK_SCHEME=$AppDeepLinkScheme",
        "java_home=$JavaHome",
        "android_sdk=$AndroidSdkRoot",
        "aapt=$aapt",
        'java_version:', $javaVersion,
        'gradle_version:', $gradleVersion,
        'aapt_version:', $aaptVersion
    )
    if ($ArtifactSourceCommit) { $provenance += "artifact_source_commit=$ArtifactSourceCommit" }
    if ($ReleaseApk) {
        $provenance += "release_apk=$((Resolve-Path $ReleaseApk).Path)"
        $provenance += "release_apk_sha256=$((Get-FileHash $ReleaseApk -Algorithm SHA256).Hash)"
    }
    if ($DeviceSerial) { $provenance += "device_serial=$DeviceSerial" }
    Write-Utf8File (Join-Path $outputRoot 'provenance.txt') $provenance

    $packageArgs = @("-PAPP_PACKAGE_NAME=$AppPackageName", "-PAPP_DEEP_LINK_SCHEME=$AppDeepLinkScheme")
    if ($Scenario -eq 'release-default') {
        Invoke-Checked 'releaseRuntimeClasspath' $androidRoot $gradle @(':app:dependencies', '--configuration', 'releaseRuntimeClasspath')
        Invoke-Checked 'dependencyInsight-media3-common' $androidRoot $gradle @(':app:dependencyInsight', '--configuration', 'releaseRuntimeClasspath', '--dependency', 'androidx.media3:media3-common')
        Invoke-Checked 'dependencyInsight-media3-exoplayer' $androidRoot $gradle @(':app:dependencyInsight', '--configuration', 'releaseRuntimeClasspath', '--dependency', 'androidx.media3:media3-exoplayer')
        Invoke-Checked 'dependencyInsight-media3-session' $androidRoot $gradle @(':app:dependencyInsight', '--configuration', 'releaseRuntimeClasspath', '--dependency', 'androidx.media3:media3-session')
        Invoke-Checked 'processReleaseMainManifest' $androidRoot $gradle (@(':app:processReleaseMainManifest') + $packageArgs)
        $variant = 'release'
        $manifestTask = 'processReleaseMainManifest'
    } else {
        Invoke-Checked 'assembleDebug' $androidRoot $gradle (@(':app:assembleDebug', ':app:processDebugMainManifest') + $packageArgs)
        $variant = 'debug'
        $manifestTask = 'processDebugMainManifest'
    }

    $mergedManifest = Join-Path $androidRoot "app\build\intermediates\merged_manifest\$variant\$manifestTask\AndroidManifest.xml"
    $mergerReport = Join-Path $androidRoot "app\build\outputs\logs\manifest-merger-$variant-report.txt"
    $blameReport = Join-Path $androidRoot "app\build\intermediates\manifest_merge_blame_file\$variant\$manifestTask\manifest-merger-blame-$variant-report.txt"
    foreach ($path in @($mergedManifest, $mergerReport, $blameReport)) {
        if (-not (Test-Path $path)) { throw "Expected generated evidence was not found: $path" }
    }
    Copy-Item $mergedManifest (Join-Path $outputRoot 'merged-AndroidManifest.xml') -Force
    Copy-Item $mergerReport (Join-Path $outputRoot 'manifest-merger-report.txt') -Force
    Copy-Item $blameReport (Join-Path $outputRoot 'manifest-merger-blame-report.txt') -Force
    Select-String -Path $mergedManifest -Pattern 'package="|android:scheme="|android:authorities=|MusicWidgetProvider|\.widget\.(PLAY_PAUSE|PREV|NEXT|UPDATE)' |
        ForEach-Object { $_.Line } | Set-Content (Join-Path $outputRoot 'identity-and-components.txt') -Encoding utf8

    if ($Scenario -eq 'debug-custom') {
        $apk = Get-ChildItem (Join-Path $androidRoot 'app\build\outputs\apk\debug') -Filter '*x86_64.apk' |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $apk) { throw 'Debug x86_64 APK was not generated.' }
        Invoke-Checked 'apk-badging' $repoRoot $aapt @('dump', 'badging', $apk.FullName)
        Invoke-Checked 'apk-manifest-tree' $repoRoot $aapt @('dump', 'xmltree', $apk.FullName, 'AndroidManifest.xml')
        Write-Utf8File (Join-Path $outputRoot 'apk-sha256.txt') (Get-FileHash $apk.FullName -Algorithm SHA256 | Format-List | Out-String)
    }

    if ($ReleaseApk) {
        Invoke-Checked 'apk-badging' $repoRoot $aapt @('dump', 'badging', (Resolve-Path $ReleaseApk).Path)
        Invoke-Checked 'apk-manifest-tree' $repoRoot $aapt @('dump', 'xmltree', (Resolve-Path $ReleaseApk).Path, 'AndroidManifest.xml')
        Invoke-Checked 'device-properties' $repoRoot $adb @('-s', $DeviceSerial, 'shell', 'getprop')
        Invoke-Checked 'adb-logcat-clear' $repoRoot $adb @('-s', $DeviceSerial, 'logcat', '-c')
        Invoke-Checked 'adb-push-release-apk' $repoRoot $adb @('-s', $DeviceSerial, 'push', (Resolve-Path $ReleaseApk).Path, '/data/local/tmp/media3-phase0-release.apk')
        Invoke-Checked 'install-standard' $repoRoot $adb @('-s', $DeviceSerial, 'shell', 'pm', 'install', '-r', '/data/local/tmp/media3-phase0-release.apk')
        Invoke-Checked 'cold-start' $repoRoot $adb @('-s', $DeviceSerial, 'shell', 'am', 'start', '-W', '-n', "$AppPackageName/$ActivityName")
        Start-Sleep -Seconds 8
        Invoke-Checked 'androidruntime-logcat' $repoRoot $adb @('-s', $DeviceSerial, 'logcat', '-d', '-v', 'brief', 'AndroidRuntime:E', '*:S')
        Invoke-Checked 'uiautomator-dump' $repoRoot $adb @('-s', $DeviceSerial, 'shell', 'uiautomator', 'dump', '/sdcard/media3-phase0-window.xml')
        Invoke-Checked 'uiautomator-pull' $repoRoot $adb @('-s', $DeviceSerial, 'pull', '/sdcard/media3-phase0-window.xml', (Join-Path $outputRoot 'window.xml'))
    }
} finally {
    # Gradle regenerates this tracked JS identity file; restore the clean input after capture.
    [System.IO.File]::WriteAllBytes($identityPath, $identityBefore)
}

$postStatus = (& git -C $repoRoot status --porcelain | Out-String).TrimEnd()
Write-Utf8File (Join-Path $outputRoot 'post-capture-status.txt') @($postStatus)
