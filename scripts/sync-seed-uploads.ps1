# 用途：将数据库种子图片复制到本地上传目录，避免初始化数据中的 /uploads 图片 404。
# 输入：-SourceDir 种子上传资源目录，默认 database/assets/uploads。
# 输出：复制后的本地上传目录，默认 weblog-backend/uploads。
param(
    [string]$SourceDir = "database/assets/uploads",
    [string]$TargetDir = "weblog-backend/uploads"
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$sourcePath = Join-Path $root $SourceDir
$targetPath = Join-Path $root $TargetDir

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Seed uploads directory not found: $sourcePath"
}

New-Item -ItemType Directory -Force -Path $targetPath | Out-Null
Copy-Item -Path (Join-Path $sourcePath "*") -Destination $targetPath -Recurse -Force

Write-Host "Seed uploads synced from $sourcePath to $targetPath"
