param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+(\.\d+)?$')][string]$AppVersion,
    [Parameter(Mandatory = $true)][ValidatePattern('^\d+\.\d+\.\d+(\.\d+)?\+\d+$')][string]$ArtifactVersion,
    [Parameter(Mandatory = $true)][ValidateSet('x64')][string]$Architecture,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
# 与输出目录采用相同的 .NET 路径规范化，展开 CI 临时目录中的 8.3 短路径。
$AppImage = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $AppImage).Path)
if (-not (Test-Path -LiteralPath (Join-Path $AppImage 'CopyMangaNeo.exe') -PathType Leaf)) {
    throw 'Native application executable missing'
}
if (Test-Path -LiteralPath (Join-Path $AppImage 'runtime/bin/server/jvm.dll')) {
    throw 'Expected a Native Image distribution, received a JVM application image'
}
if (@(Get-ChildItem -LiteralPath $AppImage -Filter '*.dll' -Recurse).Count -eq 0) {
    throw 'Native Image companion libraries missing'
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ($OutputDirectory.Equals($AppImage, [StringComparison]::OrdinalIgnoreCase) -or
    $OutputDirectory.StartsWith($AppImage.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ZIP output directory must be outside the application image'
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$archive = Join-Path $OutputDirectory "CopyMangaNeo_windows_${ArtifactVersion}_${Architecture}_portable.zip"
$temporaryArchive = Join-Path $OutputDirectory ('.portable-' + [Guid]::NewGuid().ToString('N') + '.zip')
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    # .NET ZIP API 保留隐藏文件；不使用会跳过隐藏文件的 Compress-Archive。
    [IO.Compression.ZipFile]::CreateFromDirectory($AppImage, $temporaryArchive, [IO.Compression.CompressionLevel]::Optimal, $false)
    $zip = [IO.Compression.ZipFile]::Open($temporaryArchive, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $existing = $zip.GetEntry('.nucleus-executable-type')
        if ($existing) { $existing.Delete() }
        $marker = $zip.CreateEntry('.nucleus-executable-type')
        $writer = [IO.StreamWriter]::new($marker.Open(), [Text.UTF8Encoding]::new($false))
        try { $writer.Write("exe`n$AppVersion`n") } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
    Move-Item -LiteralPath $temporaryArchive -Destination $archive -Force
    $stream = [IO.File]::OpenRead($archive)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { $hash = [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $sha256.Dispose(); $stream.Dispose() }
    "$hash  $([IO.Path]::GetFileName($archive))" | Set-Content -LiteralPath "$archive.sha256" -Encoding ascii
    Write-Host "Portable ZIP: $archive"
} finally {
    if (Test-Path -LiteralPath $temporaryArchive) { Remove-Item -LiteralPath $temporaryArchive }
}
