<#
.SYNOPSIS
    Script otomatis untuk rilis hybrid Echo Desktop.
    Hanya mengunggah file yang hash-nya berubah (manifest + ~1-5 MB file).

.EXAMPLE
    .\publish-release.ps1 -Build 2 -Notes "Perbaikan bug audio dan optimasi updater"
#>

param(
    [Parameter(Mandatory=$true)]
    [int]$Build,
    [string]$Notes = "Pembaruan Echo Desktop Build $Build",
    [string]$Repo = "Syauqi227/panlist"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Memulai Proses Persiapan Rilis Echo Desktop (Build $Build) ===" -ForegroundColor Cyan

# 1. Coba ambil manifest rilis sebelumnya dari GitHub
$prevManifest = "desktop\update-manifest-previous.json"
try {
    Write-Host "Mengambil manifest rilis sebelumnya dari GitHub ($Repo)..." -ForegroundColor Yellow
    $latestReleaseJson = gh api "repos/$Repo/releases/latest" | ConvertFrom-Json
    $manifestAsset = $latestReleaseJson.assets | Where-Object { $_.name -eq "update-manifest.json" }
    if ($manifestAsset) {
        Invoke-WebRequest -Uri $manifestAsset.browser_download_url -OutFile $prevManifest
        Write-Host "Manifest rilis sebelumnya berhasil diunduh ke $prevManifest." -ForegroundColor Green
    } else {
        Write-Host "Tidak ada update-manifest.json di rilis terakhir. Ini akan dianggap sebagai rilis dasar penuh." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Catatan: Tidak dapat mengunduh manifest sebelumnya (${_}). Melanjutkan tanpa manifest lama." -ForegroundColor DarkGray
}

# 2. Jalankan Gradle untuk membangun distributable dan generate manifest rilis
Write-Host "Membangun aplikasi dan menghasilkan update manifest..." -ForegroundColor Cyan
if (Test-Path $prevManifest) {
    .\gradlew.bat :desktop:createDistributable :desktop:generateUpdateManifest "-Papp.build=$Build" "-PpreviousManifest=$prevManifest"
} else {
    .\gradlew.bat :desktop:createDistributable :desktop:generateUpdateManifest "-Papp.build=$Build"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build gagal! Periksa error di atas."
    exit $LASTEXITCODE
}

# 3. Cek hasil file yang akan diunggah
$releaseFilesDir = "desktop\build\update-release-files"
if (-not (Test-Path $releaseFilesDir)) {
    Write-Error "Direktori $releaseFilesDir tidak ditemukan!"
    exit 1
}

$filesToUpload = Get-ChildItem -Path $releaseFilesDir -File
Write-Host "`n=== File yang Siap Diunggah (${filesToUpload.Count} file) ===" -ForegroundColor Green
$totalSize = 0
foreach ($file in $filesToUpload) {
    $sizeKb = [math]::Round($file.Length / 1KB, 2)
    $totalSize += $file.Length
    Write-Host " - $($file.Name) ($sizeKb KB)" -ForegroundColor White
}
$totalSizeMb = [math]::Round($totalSize / 1MB, 2)
Write-Host "Total ukuran unggahan: $totalSizeMb MB (hanya file yang berubah!)" -ForegroundColor Green

$tagName = "v1.3.0-b$Build"
Write-Host "`nPerintah untuk publikasi rilis ke GitHub:" -ForegroundColor Cyan
Write-Host "gh release create $tagName $releaseFilesDir\* --title `"Echo 1.3.0 (Build $Build)`" --notes `"$Notes`"" -ForegroundColor Yellow
