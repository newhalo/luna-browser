<#
.SYNOPSIS
  Opens each site from a list in Luna on a TV and reports how well ads and pop-ups are handled.

.DESCRIPTION
  Needs: a DEBUG build of Luna installed on the TV, adb over Wi-Fi, Node.js 22+ on this PC.
  For every site: open it, wait, inspect the page through DevTools (inspect.js), collect the
  "LunaFilter" log lines (what was blocked and by which rule), press OK once at the pointer to
  provoke click-hijacks, check the page is still on the same site, and take a screenshot.
  Writes out/report.md and out/<host>.png (both git-ignored).

  Sites come from sites.local.txt (git-ignored, your own list) when it exists, otherwise sites.txt.

.PARAMETER Device
  adb address of the TV, e.g. 192.168.1.50:5555. Defaults to the LUNA_TV environment variable.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\sitecheck\sitecheck.ps1 -Device 192.168.1.50:5555
#>
param(
    [string]$Device = $env:LUNA_TV,
    [string]$Adb = "adb",
    [string]$Sites = "",
    [int]$LoadSeconds = 15
)
$ErrorActionPreference = "Continue"
if (-not $Device) { throw "Pass -Device <ip:5555> or set the LUNA_TV environment variable." }
if (-not $Sites) {
    $local = Join-Path $PSScriptRoot "sites.local.txt"
    $Sites = if (Test-Path $local) { $local } else { Join-Path $PSScriptRoot "sites.txt" }
}
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
    # Press OK at the pointer (centre of the page): the classic pop-under trigger.
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

    $verdict = if (-not $loaded) { "[WARN] could not load" }
        elseif ($hijacked) { "[FAIL] redirected to $finalHost" }
        elseif ($before.overlays -gt 0 -or $before.adSlots.Count -gt 0 -or $before.fixedBanners -gt 0) { "[WARN] ads still visible" }
        else { "[OK] clean" }
    $rows += "| $siteHost | $verdict | $($blocked.Count) | $($before.adSlots.Count) / $($before.fixedBanners) | $($before.overlays) | $(if ($before.guard) { 'yes' } else { 'no' }) |"
    $details += "### $siteHost`n- Title: $($before.title)`n- Page after pressing OK: $($after.url)`n- Videos / iframes: $($before.videos) / $($before.iframes)`n" +
        "- Blocked by host: " + (($blockedHosts | Select-Object -First 10 | ForEach-Object { "$($_.Name) ($($_.Count))" }) -join ", ") + "`n" +
        "- Ad slots still visible: " + (($before.adSlots) -join "; ") + "`n" +
        "- Third-party hosts the page called: " + (($before.thirdParty) -join ", ") + "`n" +
        "- Screenshot: out/$siteHost.png`n"
}
adb forward --remove tcp:9222 | Out-Null
adb shell input keyevent KEYCODE_MEDIA_PAUSE

$date = Get-Date -Format "yyyy-MM-dd HH:mm"
$report = @"
# Ad-blocking check - $date

| Site | Result | Blocked requests | Ad slots / sticky banners | Invisible overlays | Pop-up guard |
|---|---|---|---|---|---|
$($rows -join "`n")

$($details -join "`n")
"@
$report | Out-File -Encoding utf8 (Join-Path $out "report.md")
Write-Host "Report: $(Join-Path $out 'report.md')"
