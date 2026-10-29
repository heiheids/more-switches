# Grab the game window into a PNG, after clicking it so its GL surface is really on screen.
#
# Two problems had to be solved for automation to see the game:
#   1. CopyFromScreen reads the desktop, so a window that is behind the automation window is
#      captured as whatever covers it.
#   2. A Minecraft window that has never been activated is not composited, so it comes back black.
# Clicking once inside the window fixes (2); bringing it to the top fixes (1).
#
# Pure ASCII; paths derive from $PSScriptRoot.

param(
    [string]$Out = '',
    [int]$WarmupMs = 2500
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Out) { $Out = Join-Path $ProjectRoot '_build\shots\game.png' }
New-Item -ItemType Directory -Force (Split-Path -Parent $Out) | Out-Null

Add-Type -AssemblyName System.Drawing
Add-Type -Namespace Gs -Name Native -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
[DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
[DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
[DllImport("user32.dll")] public static extern void mouse_event(uint flags, int dx, int dy, uint data, IntPtr extra);
public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
'@

$proc = Get-Process java -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
if (-not $proc) { throw 'no Minecraft window found' }

$hwnd = $proc.MainWindowHandle
[Gs.Native]::ShowWindow($hwnd, 9) | Out-Null
[Gs.Native]::SetWindowPos($hwnd, [IntPtr](-1), 0, 0, 0, 0, 0x0001 -bor 0x0002 -bor 0x0040) | Out-Null
[Gs.Native]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 500

$r = New-Object Gs.Native+RECT
[Gs.Native]::GetWindowRect($hwnd, [ref]$r) | Out-Null
$w = $r.Right - $r.Left
$ht = $r.Bottom - $r.Top
if ($w -le 0 -or $ht -le 0) { throw "bad window rect ${w}x${ht}" }

# Click once inside the client area to make the GL surface visible, then park the cursor outside
# the window so no tooltip is captured.
$clickX = $r.Left + [int]($w / 2)
$clickY = $r.Top + [int]($ht / 2)
[Gs.Native]::SetCursorPos($clickX, $clickY) | Out-Null
Start-Sleep -Milliseconds 150
[Gs.Native]::mouse_event(0x0002, 0, 0, 0, [IntPtr]::Zero)   # LEFTDOWN
Start-Sleep -Milliseconds 50
[Gs.Native]::mouse_event(0x0004, 0, 0, 0, [IntPtr]::Zero)   # LEFTUP
Start-Sleep -Milliseconds $WarmupMs
[Gs.Native]::SetCursorPos(0, 0) | Out-Null
Start-Sleep -Milliseconds 700

$bmp = New-Object System.Drawing.Bitmap $w, $ht
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

$check = [System.Drawing.Bitmap]::FromFile($Out)
$pixel = $check.GetPixel([int]($w / 2), [int]($ht * 0.9))
$check.Dispose()
Write-Host ("saved {0} ({1}x{2}), bottom-centre pixel ({3},{4},{5})" -f $Out, $w, $ht, $pixel.R, $pixel.G, $pixel.B)
if ($pixel.R -lt 6 -and $pixel.G -lt 6 -and $pixel.B -lt 6) {
    Write-Host 'WARNING: the capture looks black -- the window may not be composited yet'
}
