<#
.SYNOPSIS
  Measures Luna's frame timing on the TV while sending remote keys.
.EXAMPLE
  powershell -File tools\perf\measure.ps1 -Label "home: card to card" -Keys DPAD_RIGHT,DPAD_RIGHT,DPAD_LEFT
  powershell -File tools\perf\measure.ps1 -ColdStart
  powershell -File tools\perf\measure.ps1 -Label "home idle" -Idle
#>
param(
    [string]$Label = "frames",
    [AllowEmptyCollection()][AllowEmptyString()][string[]]$Keys = @("DPAD_RIGHT", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_LEFT"),
    [string]$Device = $env:LUNA_TV,
    [string]$Adb = "$PSScriptRoot\..\platform-tools\adb.exe",
    [switch]$ColdStart,
    # Measure with no key presses (ambient animation only)
    [switch]$Idle
)
if ($ColdStart) {
    for ($i = 0; $i -lt 3; $i++) {
        & $Adb -s $Device shell am force-stop com.luna.browser; Start-Sleep 2
        & $Adb -s $Device shell am start -W -n com.luna.browser/.MainActivity | Select-String "TotalTime"
        Start-Sleep 4
    }
    return
}
# "-Keys A,B" arrives as one string when run with -File: split it.
$Keys = if ($Idle) { @() } else { @($Keys | ForEach-Object { $_ -split "," } | Where-Object { $_ }) }
& $Adb -s $Device shell dumpsys gfxinfo com.luna.browser reset | Out-Null
foreach ($k in $Keys) { & $Adb -s $Device shell input keyevent "KEYCODE_$k"; Start-Sleep -Milliseconds 400 }
Start-Sleep 1
$tmp = Join-Path $env:TEMP "luna-framestats.txt"
& $Adb -s $Device exec-out dumpsys gfxinfo com.luna.browser framestats | Out-File -Encoding utf8 $tmp
"=== $Label"
python "$PSScriptRoot\frames.py" $tmp
