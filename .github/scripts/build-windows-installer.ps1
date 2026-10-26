param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+(\.\d+)?$')][string]$AppVersion,
    [Parameter(Mandatory = $true)][ValidateSet('x64', 'arm64')][string]$Architecture,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$Compiler = $env:ISCC,
    [ValidateSet('native', 'jvm')][string]$Runtime = 'native'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$AppImage = (Resolve-Path -LiteralPath $AppImage).Path
$requiredFiles = if ($Runtime -eq 'jvm') {
    @('CopyMangaNeo.exe', 'app/CopyMangaNeo.cfg', 'runtime/bin/server/jvm.dll')
} else { @('CopyMangaNeo.exe') }
foreach ($required in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $AppImage $required))) {
        throw "Incomplete application image: missing $required"
    }
}
if ($Runtime -eq 'native') {
    if (Test-Path -LiteralPath (Join-Path $AppImage 'runtime/bin/server/jvm.dll')) {
        throw 'Expected a Native Image distribution, received a JVM application image'
    }
    $nativeLibraries = @(Get-ChildItem -LiteralPath $AppImage -Filter '*.dll' -Recurse)
    if ($nativeLibraries.Count -eq 0) { throw 'Native Image companion libraries missing' }
}
if (-not $Compiler) {
    $onPath = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($onPath) { $Compiler = $onPath.Source }
}
if (-not $Compiler) {
    foreach ($base in @(${env:ProgramFiles(x86)}, $env:ProgramFiles, "$env:LOCALAPPDATA\Programs")) {
        $candidate = Join-Path $base 'Inno Setup 6/ISCC.exe'
        if (Test-Path -LiteralPath $candidate) { $Compiler = $candidate; break }
    }
}
if (-not $Compiler -or -not (Test-Path -LiteralPath $Compiler)) {
    throw 'Install Inno Setup 6.5+ or set ISCC to the full path of ISCC.exe.'
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$installer = Join-Path $OutputDirectory "CopyMangaNeo-$AppVersion-windows-$Architecture-setup.exe"
# 编译失败不能留下看似本次成功生成的旧文件。
if (Test-Path -LiteralPath $installer) { Remove-Item -LiteralPath $installer }
& $Compiler "/DAppImage=$AppImage" "/DAppVersion=$AppVersion" "/DAppArchitecture=$Architecture" "/DAppRuntime=$Runtime" "/DOutputDirectory=$OutputDirectory" (Join-Path $repoRoot 'desktopApp/installer/CopyMangaNeo.iss')
if ($LASTEXITCODE -ne 0) { throw "Inno Setup compilation failed ($LASTEXITCODE)" }
if (-not (Test-Path -LiteralPath $installer)) { throw 'Installer output missing' }
$stream = [IO.File]::OpenRead($installer)
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $hash = [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
    $stream.Dispose()
}
"$hash  $([IO.Path]::GetFileName($installer))" | Set-Content -LiteralPath "$installer.sha256" -Encoding ascii
Write-Host "Installer: $installer"
