$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function T($base64) {
  [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($base64))
}

$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.txt"
$defaultVersion = "1.0.0"
$outputDir = Join-Path $root "release"
$sourceApk = Join-Path $root "android\app\build\outputs\apk\debug\app-debug.apk"

Set-Location $root

Write-Host ""
Write-Host "=========================================="
Write-Host ("        " + (T "5bC85bq35Zu+5LygIEFQSyDkuIDplK7miZPljIU="))
Write-Host "=========================================="
Write-Host ""

if (Test-Path $versionFile) {
  $savedVersion = (Get-Content $versionFile -Raw).Trim()
  if ($savedVersion) {
    $defaultVersion = $savedVersion
  }
}

Write-Host ((T "5b2T5YmN6buY6K6k54mI5pys5Y+377ya") + $defaultVersion)
$inputVersion = Read-Host (T "6K+36L6T5YWl54mI5pys5Y+377yM55u05o6l5Zue6L2m5L2/55So6buY6K6k54mI5pys5Y+3")

if ([string]::IsNullOrWhiteSpace($inputVersion)) {
  $appVersion = $defaultVersion
} else {
  $appVersion = $inputVersion.Trim()
}

if ($appVersion -notmatch '^\d+\.\d+\.\d+$') {
  Write-Host (T "54mI5pys5Y+35qC85byP5LiN5q2j56Gu77yM6K+35L2/55So57G75Ly8IDEuMC4wIOeahOagvOW8j+OAgg==")
  exit 1
}

$parts = $appVersion.Split(".") | ForEach-Object { [int]$_ }
$versionCode = $parts[0] * 10000 + $parts[1] * 100 + $parts[2]
$nextVersion = "$($parts[0]).$($parts[1]).$($parts[2] + 1)"

Write-Host ""
Write-Host ((T "5pys5qyh5omT5YyF54mI5pys5Y+377ya") + $appVersion)
Write-Host (T "5q2j5Zyo5pu05paw54mI5pys5Y+36YWN572uLi4u")

$packagePath = Join-Path $root "package.json"
$packageJson = Get-Content $packagePath -Raw | ConvertFrom-Json
$packageJson.version = $appVersion
[System.IO.File]::WriteAllText($packagePath, ($packageJson | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))

$gradlePath = Join-Path $root "android\app\build.gradle"
$gradleText = Get-Content $gradlePath -Raw
$gradleText = $gradleText -replace 'versionCode\s+\d+', "versionCode $versionCode"
$gradleText = $gradleText -replace 'versionName\s+"[^"]+"', "versionName `"$appVersion`""
[System.IO.File]::WriteAllText($gradlePath, $gradleText, [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host (T "5q2j5Zyo5p6E5bu6IEFQS++8jOivt+eojeWAmS4uLg==")
& npm.cmd run package:android
if ($LASTEXITCODE -ne 0) {
  Write-Host ""
  Write-Host (T "5omT5YyF5aSx6LSl77yM6K+35p+l55yL5LiK5pa56ZSZ6K+v5L+h5oGv44CC")
  exit $LASTEXITCODE
}

if (!(Test-Path $sourceApk)) {
  Write-Host ""
  Write-Host (T "5omT5YyF5aSx6LSl77ya5rKh5pyJ5om+5Yiw55Sf5oiQ55qEIEFQS+OAgg==")
  Write-Host ((T "5pyf5pyb6Lev5b6E77ya") + $sourceApk)
  exit 1
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$outputApk = Join-Path $outputDir ((T "5bC85bq35Zu+5LygLQ==") + "$appVersion.apk")
Copy-Item -Force $sourceApk $outputApk
Set-Content $versionFile $nextVersion -Encoding ASCII

Write-Host ""
Write-Host "=========================================="
Write-Host (T "5omT5YyF5oiQ5Yqf77yB")
Write-Host ((T "QVBLIOaWh+S7tu+8mg==") + $outputApk)
Write-Host ((T "5LiL5qyh6buY6K6k54mI5pys5Y+377ya") + $nextVersion)
Write-Host "=========================================="
Write-Host ""
