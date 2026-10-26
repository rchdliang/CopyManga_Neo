param(
    [Parameter(Mandatory = $true)][string]$Destination
)
$ErrorActionPreference = 'Stop'

# Match the documented UCRT toolchain; verify bytes before extraction/execution.
$name = 'llvm-mingw-20261020-ucrt-x86_64'
$expectedSha256 = 'fd5462b7f5d93b2ff981fcd3f9c7e427e47f3203c64ea91c27bcc444eebd74cd'
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$archive = Join-Path $Destination "$name.zip"
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -Uri "https://github.com/mstorsjo/llvm-mingw/releases/download/20261020/$name.zip" -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedSha256) {
    throw 'llvm-mingw archive SHA256 mismatch'
}
Expand-Archive -LiteralPath $archive -DestinationPath $Destination -Force
$toolchain = (Join-Path $Destination $name).Replace('\', '/')
foreach ($required in @('bin/x86_64-w64-mingw32-clang.exe', 'bin/x86_64-w64-mingw32-clang++.exe', 'LICENSE.TXT')) {
    if (-not (Test-Path -LiteralPath (Join-Path $toolchain $required))) {
        throw "Incomplete llvm-mingw toolchain: missing $required"
    }
}
$env:LLVM_MINGW_ROOT = $toolchain
if ($env:GITHUB_ENV) {
    "LLVM_MINGW_ROOT=$toolchain" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
}
Write-Host "Verified llvm-mingw 20261020 UCRT: $toolchain"
