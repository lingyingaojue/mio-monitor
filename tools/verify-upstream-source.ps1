#requires -Version 5.1
<#
.SYNOPSIS
  校验本地 git tag 与 GitHub Release 的官方源码包是否逐文件一致。

.DESCRIPTION
  1. 从仓库 origin 推导 GitHub owner/repo；
  2. 下载 https://github.com/<owner>/<repo>/archive/refs/tags/<Tag>.zip；
  3. 用 `git -c core.autocrlf=false archive` 导出本地 tag 树（避免 CRLF 转换误差）；
  4. 逐文件比较 SHA-256，输出 only-in-release / only-in-base / content-diff。

.PARAMETER Tag
  要校验的 release tag，默认 v2.0。

.PARAMETER Repo
  仓库根目录，默认脚本上一级目录。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/verify-upstream-source.ps1
  powershell -ExecutionPolicy Bypass -File tools/verify-upstream-source.ps1 -Tag v2.0
#>
param(
    [string]$Tag = 'v2.0',
    [string]$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mio-upstream-" + [guid]::NewGuid().ToString('N'))

function Get-FileManifest([string]$Dir) {
    $map = @{}
    Get-ChildItem -Path $Dir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($Dir.Length + 1).Replace('\', '/')
        $map[$rel] = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash
    }
    return $map
}

try {
    $git = Get-Command git -ErrorAction Stop
    $origin = (& $git.Source -C $Repo config --get remote.origin.url 2>$null)
    if (-not $origin) { throw "无法读取 $Repo 的 remote.origin.url" }
    $origin = $origin.Trim()

    $m = [regex]::Match($origin, 'github\.com[:/](?<owner>[^/]+)/(?<repo>[^/]+?)(?:\.git)?$')
    if (-not $m.Success) { throw "origin 不是 GitHub 地址：$origin" }
    $owner = $m.Groups['owner'].Value
    $repoName = $m.Groups['repo'].Value

    $commit = (& $git.Source -C $Repo rev-parse "$Tag^{commit}").Trim()
    $archiveUrl = "https://github.com/$owner/$repoName/archive/refs/tags/$Tag.zip"

    New-Item -ItemType Directory -Path $tempRoot | Out-Null
    $releaseZip = Join-Path $tempRoot 'release.zip'
    $baseZip = Join-Path $tempRoot 'base.zip'
    $releaseDir = Join-Path $tempRoot 'release'
    $baseDir = Join-Path $tempRoot 'base'

    Write-Host "仓库:       $owner/$repoName"
    Write-Host "Tag:        $Tag -> $commit"
    Write-Host "源码包:     $archiveUrl"

    Invoke-WebRequest -Uri $archiveUrl -OutFile $releaseZip -UseBasicParsing -TimeoutSec 180
    $sha = (Get-FileHash -Path $releaseZip -Algorithm SHA256).Hash
    Write-Host "SHA-256:    $sha"

    Expand-Archive -Path $releaseZip -DestinationPath $releaseDir -Force
    $releaseRoot = Get-ChildItem -Path $releaseDir -Directory | Select-Object -First 1
    if (-not $releaseRoot) { throw "源码包解压后没有根目录" }

    & $git.Source -C $Repo -c core.autocrlf=false archive --format=zip --output=$baseZip $Tag
    if ($LASTEXITCODE -ne 0) { throw "git archive $Tag 失败" }
    Expand-Archive -Path $baseZip -DestinationPath $baseDir -Force

    $release = Get-FileManifest $releaseRoot.FullName
    $base = Get-FileManifest $baseDir
    $onlyRelease = @($release.Keys | Where-Object { -not $base.ContainsKey($_) } | Sort-Object)
    $onlyBase = @($base.Keys | Where-Object { -not $release.ContainsKey($_) } | Sort-Object)
    $diff = @($release.Keys | Where-Object { $base.ContainsKey($_) -and $base[$_] -ne $release[$_] } | Sort-Object)

    Write-Host "Release 文件数: $($release.Count)"
    Write-Host "本地文件数:    $($base.Count)"
    Write-Host "only-in-release: $($onlyRelease.Count)"
    Write-Host "only-in-base:    $($onlyBase.Count)"
    Write-Host "content-diff:    $($diff.Count)"

    ($onlyRelease + $onlyBase + $diff) | Select-Object -First 30 | ForEach-Object { Write-Host "  差异: $_" }

    if ($onlyRelease.Count -eq 0 -and $onlyBase.Count -eq 0 -and $diff.Count -eq 0) {
        Write-Host "PASS: 本地 $Tag 树与官方 Release 源码包逐文件一致。" -ForegroundColor Green
        exit 0
    }

    Write-Host "FAIL: 发现差异，请检查上面的文件清单。" -ForegroundColor Red
    Write-Host "临时文件保留在: $tempRoot"
    exit 1
}
catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}
