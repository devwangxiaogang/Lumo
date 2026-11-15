# 语光 Lumo 1.3 设备冒烟脚本（Windows PowerShell）
# 用途：安装 debug 包，冷启动，抓 FATAL 日志，逐页打开并留存截图，供人眼复核。
# 用法：powershell -ExecutionPolicy Bypass -File docs\v13-smoke.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pkg = 'com.yuguang.lumo'
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$shots = Join-Path $root 'docs\smoke'
New-Item -ItemType Directory -Force -Path $shots | Out-Null

if (-not (Test-Path $apk)) { throw "APK 不存在，请先运行 .\gradlew.bat :app:assembleDebug" }

Write-Host '[1/5] 安装 APK' -ForegroundColor Cyan
& adb install -r $apk | Out-Host

Write-Host '[2/5] 清日志并冷启动' -ForegroundColor Cyan
& adb logcat -c
& adb shell am force-stop $pkg
& adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 4

Write-Host '[3/5] 首页截图' -ForegroundColor Cyan
& adb exec-out screencap -p > (Join-Path $shots '01-home.png')

Write-Host '[4/5] 抓取崩溃与 ANR' -ForegroundColor Cyan
$log = Join-Path $shots 'logcat.txt'
& adb logcat -d -v time | Out-File -Encoding utf8 $log
$fatal = Select-String -Path $log -Pattern 'FATAL EXCEPTION|ANR in com.yuguang.lumo' -SimpleMatch:$false
if ($fatal) {
    Write-Host '发现崩溃/ANR：' -ForegroundColor Red
    $fatal | ForEach-Object { Write-Host $_.Line }
    exit 1
}

Write-Host '[5/5] 结论' -ForegroundColor Green
Write-Host "冷启动无 FATAL/ANR。截图与日志：$shots"
Write-Host '继续人工冒烟：擂台一局 → 分支切换 → 设置/用量统计 → 翻译/面对面。'
