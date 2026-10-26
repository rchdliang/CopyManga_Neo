param(
    [string]$Adb = 'adb',
    [string]$Serial,
    [ValidateRange(1, 20)][int]$Runs = 5,
    [ValidateRange(0, 3)][int]$WarmupRuns = 1,
    [ValidateRange(2, 60)][int]$ContentWaitSeconds = 12,
    [ValidateRange(1, 30)][int]$CooldownSeconds = 1,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot ('../build/reports/startup/' + (Get-Date -Format 'yyyyMMdd-HHmmss')))
)

# 仅重启显式启用探针的 CMP 包；不清数据、不清全局 logcat、不修改设备设置。
$ErrorActionPreference = 'Stop'
$packageName = 'com.rchdliang.copymanga_neo'
$componentName = "$packageName/.MainActivity"
$targetArguments = if ($Serial) { @('-s', $Serial) } else { @() }
function Invoke-Adb {
    param([Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $result = & $Adb @targetArguments @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}

Invoke-Adb @('shell', 'pm', 'path', $packageName) | Out-Null
if (Test-Path -LiteralPath $OutputDirectory) { throw "Output directory already exists: $OutputDirectory" }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$device = @{
    model = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')) -join ''
    android = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.release')) -join ''
    package = $packageName
    capturedAt = (Get-Date -Format o)
    note = 'Process-cold starts after force-stop; filesystem cache retained. Opt-in probes required for either build type. Draw markers exclude GPU presentation and image completion.'
}
$samples = @()
for ($index = 1; $index -le ($WarmupRuns + $Runs); $index++) {
    Invoke-Adb @('shell', 'am', 'force-stop', $packageName) | Out-Null
    Start-Sleep -Seconds $CooldownSeconds
    $launch = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', $componentName)
    if (($launch -join "`n") -notmatch 'Status: ok') { throw ($launch -join "`n") }
    if (($launch -join "`n") -notmatch 'LaunchState: COLD') { throw 'Expected a process-cold start' }
    $appProcessId = ((Invoke-Adb @('shell', 'pidof', $packageName)) -join '').Trim()
    if ($appProcessId -notmatch '^\d+$') { throw 'No unique CMP process after launch' }
    $deadline = (Get-Date).AddSeconds($ContentWaitSeconds)
    do {
        $probeLines = Invoke-Adb @('logcat', '-d', "--pid=$appProcessId", '-v', 'brief', '-s', 'StartupTiming:D', '*:S')
        $joined = $probeLines -join "`n"
        if ($joined -match 'phase=home_(content|error)_draw ') { break }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    $phases = @{}
    foreach ($line in $probeLines) {
        if ($line -match 'phase=(\S+) totalMs=(\d+)(?: durationMs=(\d+))?(?: cpuMs=(\d+))?') {
            $phases[$Matches[1]] = @{
                totalMs = [long]$Matches[2]
                durationMs = if ($Matches[3]) { [long]$Matches[3] } else { $null }
                cpuMs = if ($Matches[4]) { [long]$Matches[4] } else { $null }
            }
        }
    }
    if (!$phases.ContainsKey('shell_draw')) { throw "No shell_draw probe: $joined" }
    $isWarmup = $index -le $WarmupRuns
    $stem = if ($isWarmup) { "warmup-$index" } else { 'run-' + ($index - $WarmupRuns) }
    $probeLines | Set-Content -LiteralPath (Join-Path $OutputDirectory "$stem-probes.txt") -Encoding utf8
    $launch | Set-Content -LiteralPath (Join-Path $OutputDirectory "$stem-am-start.txt") -Encoding utf8
    $totalTime = if (($launch -join "`n") -match 'TotalTime: (\d+)') { [long]$Matches[1] } else { $null }
    $sample = @{ run = $stem; warmup = $isWarmup; systemTotalTimeMs = $totalTime; phases = $phases }
    $samples += $sample
    @{ device = $device; samples = $samples } | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'measurements.json') -Encoding utf8
    $homeFrame = if ($phases.ContainsKey('home_first_draw')) { $phases['home_first_draw'].totalMs } else { $phases['home_loading_draw'].totalMs }
    Write-Output "$stem core=$($phases['core_ready'].totalMs)ms home=$($homeFrame)ms content=$($phases['home_content_draw'].totalMs)ms system=$($totalTime)ms"
}
@{ device = $device; samples = $samples } | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'measurements.json') -Encoding utf8
Write-Output "Saved: $OutputDirectory"
