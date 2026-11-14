<#
.SYNOPSIS
  Measures Luna's frame timing on the TV while sending remote keys.
.EXAMPLE
  powershell -File tools\perf\measure.ps1 -Label "home: card to card" -Keys DPAD_RIGHT,DPAD_RIGHT,DPAD_LEFT
  powershell -File tools\perf\measure.ps1 -ColdStart
#>
param(
    [string]$Label = "frames",
    [string[]]$Keys = @("DPAD_RIGHT", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_LEFT"),
    [string]$Device = "192.168.31.27:5555",
    [string]$Adb = "$PSScriptRoot\..\platform-tools\adb.exe",
    [switch]$ColdStart
)
if ($ColdStart) {
    for ($i = 0; $i -lt 3; $i++) {
        & $Adb -s $Device shell am force-stop com.luna.browser; Start-Sleep 2
        & $Adb -s $Device shell am start -W -n com.luna.browser/.MainActivity | Select-String "TotalTime"
        Start-Sleep 4
    }
    return
}
& $Adb -s $Device shell dumpsys gfxinfo com.luna.browser reset | Out-Null
foreach ($k in $Keys) { & $Adb -s $Device shell input keyevent "KEYCODE_$k"; Start-Sleep -Milliseconds 400 }
Start-Sleep 1
$tmp = Join-Path $env:TEMP "luna-framestats.txt"
& $Adb -s $Device exec-out dumpsys gfxinfo com.luna.browser framestats > $tmp
"=== $Label"
python "$PSScriptRoot\frames.py" $tmp
