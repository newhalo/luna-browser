<#
.SYNOPSIS
  Opens each site from sites.txt in Luna on the TV and reports how well ads and pop-ups are handled.

.DESCRIPTION
  Needs: a debug build of Luna installed on the TV, adb over Wi-Fi, Node.js 22+ on this PC.
  For every site: open it, wait, inspect the page through DevTools (inspect.js), collect the
  "LunaFilter" log lines (what was blocked and by which rule), press OK once at the pointer to
  provoke click-hijacks, check the page is still on the same site, and take a screenshot.
  Writes out/report.md and out/<host>.png.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\sitecheck\sitecheck.ps1 -Device 192.168.31.27:5555
#>
param(
    [string]$Device = "192.168.31.27:5555",
    [string]$Adb = "$PSScriptRoot\..\platform-tools\adb.exe",
    [string]$Sites = "$PSScriptRoot\sites.txt",
    [int]$LoadSeconds = 15
)
$ErrorActionPreference = "Continue"
$out = Join-Path $PSScriptRoot "out"
New-Item -ItemType Directory -Force $out | Out-Null
$cdp = Join-Path $PSScriptRoot "cdp.js"
$inspect = Join-Path $PSScriptRoot "inspect.js"

function adb { & $Adb -s $Device @args }
function siteOf([string]$h) { $p = $h.ToLower().Replace("www.", "").Split("."); if ($p.Count -le 2) { $p -join "." } else { ($p[-2..-1]) -join "." } }
function inspectPage { $j = node $cdp $inspect; try { $j | ConvertFrom-Json } catch { [pscustomobject]@{ error = "$j" } } }

& $Adb connect $Device | Out-Null
adb shell input keyevent KEYCODE_WAKEUP
$list = Get-Content $Sites | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith("#") }
$rows = @()
$details = @()

foreach ($site in $list) {
    $siteHost = ([uri]$site).Host
    Write-Host "== $site"
    adb logcat -c
    adb shell am start -a android.intent.action.VIEW -d $site -n com.luna.browser/.MainActivity | Out-Null
    Start-Sleep $LoadSeconds
    $procId = (adb shell pidof com.luna.browser).Trim()
    adb forward tcp:9222 "localabstract:webview_devtools_remote_$procId" | Out-Null

    $before = inspectPage
    # Press OK at the pointer (centre of the page) — the classic pop-under trigger.
    adb shell input keyevent KEYCODE_DPAD_CENTER
    Start-Sleep 5
    $after = inspectPage
    $png = Join-Path $out ("{0}.png" -f $siteHost)
    adb shell screencap -p /sdcard/sitecheck.png | Out-Null
    adb pull /sdcard/sitecheck.png $png | Out-Null

    $blocked = adb logcat -d -s LunaFilter:D | Select-String "blocked "
    $blockedHosts = $blocked | ForEach-Object { if ($_ -match 'blocked https?://([^/ ]+)') { $Matches[1] } } | Group-Object | Sort-Object Count -Descending
    $finalHost = if ($after.url) { ([uri]$after.url).Host } else { "?" }
    $hijacked = (siteOf $finalHost) -ne (siteOf $siteHost)
    $loaded = -not $before.error

    $verdict = if (-not $loaded) { "⚠️ Không mở được" }
        elseif ($hijacked) { "❌ Bị chuyển sang $finalHost" }
        elseif ($before.overlays -gt 0 -or $before.adSlots.Count -gt 0 -or $before.fixedBanners -gt 0) { "🟡 Còn quảng cáo hiển thị" }
        else { "✅ Sạch" }
    $rows += "| $siteHost | $verdict | $($blocked.Count) | $($before.adSlots.Count) / $($before.fixedBanners) | $($before.overlays) | $(if ($before.guard) { 'có' } else { 'không' }) |"
    $details += "### $siteHost`n- Tiêu đề: $($before.title)`n- Trang sau khi bấm: $($after.url)`n- Video / iframe: $($before.videos) / $($before.iframes)`n" +
        "- Đã chặn theo tên miền: " + (($blockedHosts | Select-Object -First 10 | ForEach-Object { "$($_.Name) ($($_.Count))" }) -join ", ") + "`n" +
        "- Ô quảng cáo còn thấy: " + (($before.adSlots) -join "; ") + "`n" +
        "- Tên miền bên thứ ba trang đã gọi: " + (($before.thirdParty) -join ", ") + "`n" +
        "- Ảnh chụp: out/$siteHost.png`n"
}
adb forward --remove tcp:9222 | Out-Null
adb shell input keyevent KEYCODE_MEDIA_PAUSE

$date = Get-Date -Format "yyyy-MM-dd HH:mm"
$report = @"
# Kiểm tra chặn quảng cáo — $date

| Trang | Kết quả | Request bị chặn | Ô quảng cáo / banner dính | Lớp phủ vô hình | Bộ chống popup |
|---|---|---|---|---|---|
$($rows -join "`n")

$($details -join "`n")
"@
$report | Out-File -Encoding utf8 (Join-Path $out "report.md")
Write-Host "Report: $(Join-Path $out 'report.md')"
