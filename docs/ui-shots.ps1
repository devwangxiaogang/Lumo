<#
  ui-shots.ps1 - Lumo visual-verification screenshot runner (Windows PowerShell 5.1)

  Prerequisites:
    1. Device lock held via docs\device-lock.ps1 (this script does NOT take the lock)
    2. APK built at app\build\outputs\apk\debug\app-debug.apk

  Usage:
    powershell -ExecutionPolicy Bypass -File docs\ui-shots.ps1 -Install -Theme light -Prefix ui-day
    powershell -ExecutionPolicy Bypass -File docs\ui-shots.ps1 -Theme dark -Prefix ui-night
  Output: docs\smoke\<Prefix>-*.png
#>
param(
    [switch]$Install,
    [ValidateSet('keep', 'light', 'dark')][string]$Theme = 'keep',
    [string]$Prefix = 'ui',
    [string]$Serial = '55QCS4Z5VGGU8HLV'
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$outDir = Join-Path $root 'docs\smoke'
$pkg = 'com.yuguang.lumo'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Run-Adb { & adb -s $Serial @args }
function Log { param($m) Write-Host "[shots] $m" }

$state = (Run-Adb get-state 2>&1) -join ''
if ($state -notmatch 'device') { throw "device $Serial not available: $state" }

if ($Install) {
    if (-not (Test-Path $apk)) { throw "APK missing: $apk" }
    Log ("install {0:N1} MB" -f ((Get-Item $apk).Length / 1MB))
    Run-Adb install -r $apk | Out-Null
}

if ($Theme -ne 'keep') {
    $mode = if ($Theme -eq 'dark') { 'yes' } else { 'no' }
    Run-Adb shell cmd uimode night $mode | Out-Null
    Log "theme = $Theme"
    Start-Sleep -Milliseconds 1200
}

Run-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Run-Adb shell wm dismiss-keyguard | Out-Null
Run-Adb shell am force-stop $pkg | Out-Null
Run-Adb shell am start -W -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Milliseconds 3000

function Shot {
    param([string]$Name)
    $path = Join-Path $outDir "$Prefix-$Name.png"
    Run-Adb shell screencap -p /sdcard/_shot.png | Out-Null
    Run-Adb pull /sdcard/_shot.png $path 2>&1 | Out-Null
    Run-Adb shell rm /sdcard/_shot.png | Out-Null
    Log "  -> $Name"
}

function Get-UiDoc {
    Run-Adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    $tmp = Join-Path $env:TEMP 'lumo-ui.xml'
    Run-Adb pull /sdcard/ui.xml $tmp 2>&1 | Out-Null
    if (-not (Test-Path $tmp)) { return $null }
    return [xml](Get-Content $tmp -Encoding UTF8)
}

function Tap-Text {
    param([string]$Text, [int]$TimeoutSec = 8, [int]$Index = 0)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $doc = Get-UiDoc
            if ($doc) {
                $hits = @($doc.SelectNodes('//node[@text]') | Where-Object { $_.text -like "*$Text*" })
                if ($hits.Count -gt $Index) {
                    $b = $hits[$Index].bounds
                    if ($b -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                        $x = ([int]$Matches[1] + [int]$Matches[3]) / 2
                        $y = ([int]$Matches[2] + [int]$Matches[4]) / 2
                        Run-Adb shell input tap ([int]$x) ([int]$y) | Out-Null
                        Start-Sleep -Milliseconds 1100
                        return $true
                    }
                }
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    Log "  !! control not found: $Text"
    return $false
}

function Back-Home {
    for ($i = 0; $i -lt 3; $i++) { Run-Adb shell input keyevent 4 | Out-Null; Start-Sleep -Milliseconds 450 }
    $top = (Run-Adb shell dumpsys activity activities 2>&1 | Select-String -Pattern 'topResumedActivity' | Select-Object -First 1)
    if ("$top" -notmatch $pkg) {
        Run-Adb shell am start -n "$pkg/.MainActivity" | Out-Null
        Start-Sleep -Milliseconds 2000
    }
}

Log 'home'
Shot 'home'

Log 'chat'
if (Tap-Text '新对话') {
    Start-Sleep -Milliseconds 700
    Tap-Text '开始' | Out-Null          # 模型确认弹窗
    Start-Sleep -Milliseconds 1200
    Shot 'chat'
    Tap-Text '输入消息' | Out-Null       # 聚焦输入条，看键盘态
    Start-Sleep -Milliseconds 900
    Shot 'chat-input'
}

Back-Home
Log 'translate'
if (Tap-Text '翻译') { Shot 'translate' }

Back-Home
Log 'voice'
if (Tap-Text '语音') { Shot 'voice' }

Back-Home
Log 'settings'
if (Tap-Text '我') {
    Start-Sleep -Milliseconds 600
    if (Tap-Text '设置') {
        Start-Sleep -Milliseconds 900
        Shot 'settings'
        if (Tap-Text '主题') { Start-Sleep -Milliseconds 900; Shot 'appearance' }
    }
}

$crash = (Run-Adb logcat -d -b crash 2>&1 | Select-String -Pattern "FATAL EXCEPTION|ANR in $pkg")
if ($crash) { Log '!!! crash/ANR found:'; $crash | ForEach-Object { Write-Host "  $_" } }
else { Log 'no crash / no ANR' }

Log "done: $outDir\$Prefix-*.png"
