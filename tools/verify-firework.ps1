# Firework-cooldown verification driver.
#
# Launches nothing: it drives a client that is already in a world. Input is sent with SendInput
# (not PostMessage), because Minecraft 26.3 reads input through SDL raw input, which ignores
# synthetic window messages but does see SendInput once the window has focus.
#
# Screenshots are written after each step so the state is visible without guessing.
#
# Pure ASCII; all paths derive from $PSScriptRoot.

param(
    [string]$SaveDir = '',
    [switch]$OnlyClick
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$shotDir = Join-Path $ProjectRoot '_build\shots'
New-Item -ItemType Directory -Force $shotDir | Out-Null

Add-Type -AssemblyName System.Drawing
Add-Type -Namespace Si -Name Native -MemberDefinition @'
[StructLayout(LayoutKind.Sequential)]
public struct INPUT { public uint type; public InputUnion U; }
[StructLayout(LayoutKind.Explicit)]
public struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; [FieldOffset(0)] public HARDWAREINPUT hi; }
[StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
[StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
[StructLayout(LayoutKind.Sequential)] public struct HARDWAREINPUT { public uint uMsg; public ushort wParamL; public ushort wParamH; }

[DllImport("user32.dll", SetLastError = true)] public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
[DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
[DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
[DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT p);
[StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
[StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
'@

$INPUT_MOUSE = 0
$INPUT_KEYBOARD = 1
$KEYEVENTF_KEYUP = 0x0002
$KEYEVENTF_UNICODE = 0x0004
$MOUSEEVENTF_RIGHTDOWN = 0x0008
$MOUSEEVENTF_RIGHTUP = 0x0010
$MOUSEEVENTF_LEFTDOWN = 0x0002
$MOUSEEVENTF_LEFTUP = 0x0004

function Get-GameWindow {
    Get-Process java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1
}

function Focus-Game($proc) {
    [Si.Native]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 250
}

function Send-KeyDown([int]$vk) {
    $inputs = New-Object 'Si.Native+INPUT[]' 1
    $inputs[0].type = $INPUT_KEYBOARD
    $inputs[0].U.ki.wVk = [uint16]$vk
    [Si.Native]::SendInput(1, $inputs, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
}

function Send-KeyUp([int]$vk) {
    $inputs = New-Object 'Si.Native+INPUT[]' 1
    $inputs[0].type = $INPUT_KEYBOARD
    $inputs[0].U.ki.wVk = [uint16]$vk
    $inputs[0].U.ki.dwFlags = $KEYEVENTF_KEYUP
    [Si.Native]::SendInput(1, $inputs, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
}

function Send-Key([int]$vk) {
    Send-KeyDown $vk
    Start-Sleep -Milliseconds 60
    Send-KeyUp $vk
    Start-Sleep -Milliseconds 150
}

function Send-Unicode([string]$text) {
    foreach ($ch in $text.ToCharArray()) {
        $down = New-Object 'Si.Native+INPUT[]' 1
        $down[0].type = $INPUT_KEYBOARD
        $down[0].U.ki.wScan = [uint16][int]$ch
        $down[0].U.ki.dwFlags = $KEYEVENTF_UNICODE
        [Si.Native]::SendInput(1, $down, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null

        $up = New-Object 'Si.Native+INPUT[]' 1
        $up[0].type = $INPUT_KEYBOARD
        $up[0].U.ki.wScan = [uint16][int]$ch
        $up[0].U.ki.dwFlags = $KEYEVENTF_UNICODE -bor $KEYEVENTF_KEYUP
        [Si.Native]::SendInput(1, $up, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null

        Start-Sleep -Milliseconds 20
    }
}

function Send-RightClick {
    $down = New-Object 'Si.Native+INPUT[]' 1
    $down[0].type = $INPUT_MOUSE
    $down[0].U.mi.dwFlags = $MOUSEEVENTF_RIGHTDOWN
    [Si.Native]::SendInput(1, $down, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
    Start-Sleep -Milliseconds 60
    $up = New-Object 'Si.Native+INPUT[]' 1
    $up[0].type = $INPUT_MOUSE
    $up[0].U.mi.dwFlags = $MOUSEEVENTF_RIGHTUP
    [Si.Native]::SendInput(1, $up, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
}

function Save-Shot([string]$name) {
    $proc = Get-GameWindow
    if (-not $proc) { return }
    $r = New-Object Si.Native+RECT
    [Si.Native]::GetWindowRect($proc.MainWindowHandle, [ref]$r) | Out-Null
    $w = $r.Right - $r.Left; $ht = $r.Bottom - $r.Top
    $bmp = New-Object System.Drawing.Bitmap $w, $ht
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
    $bmp.Save((Join-Path $shotDir ($name + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Host ("== shot {0}" -f $name)
}

$proc = Get-GameWindow
if (-not $proc) { throw 'no Minecraft window found' }
Write-Host ("== window: {0} (pid {1})" -f $proc.MainWindowTitle, $proc.Id)

Focus-Game $proc
$r = New-Object Si.Native+RECT
[Si.Native]::GetWindowRect($proc.MainWindowHandle, [ref]$r) | Out-Null

# The demo build shows a "trial time" notice after a while, and it swallows all input until it is
# dismissed. Its "continue playing" button sits just right of centre, near the bottom.
function Dismiss-DemoNotice {
    $cx = $r.Left + [int](($r.Right - $r.Left) / 2)
    $cy = $r.Top + [int](($r.Bottom - $r.Top) * 0.80)
    [Si.Native]::SetCursorPos($cx, $cy) | Out-Null
    Start-Sleep -Milliseconds 200
    Send-LeftClick
    Start-Sleep -Milliseconds 700
}

function Send-LeftClick {
    $down = New-Object 'Si.Native+INPUT[]' 1
    $down[0].type = $INPUT_MOUSE
    $down[0].U.mi.dwFlags = $MOUSEEVENTF_LEFTDOWN
    [Si.Native]::SendInput(1, $down, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
    Start-Sleep -Milliseconds 60
    $up = New-Object 'Si.Native+INPUT[]' 1
    $up[0].type = $INPUT_MOUSE
    $up[0].U.mi.dwFlags = $MOUSEEVENTF_LEFTUP
    [Si.Native]::SendInput(1, $up, [System.Runtime.InteropServices.Marshal]::SizeOf([type]'Si.Native+INPUT')) | Out-Null
}

Dismiss-DemoNotice

if (-not $OnlyClick) {
    # Aim slightly above the middle so a rocket placed on the ground is plausible.
    [Si.Native]::SetCursorPos(($r.Left + [int](($r.Right-$r.Left)/2)), ($r.Top + [int](($r.Bottom-$r.Top)/2))) | Out-Null
    Start-Sleep -Milliseconds 300

    Write-Host '== give rockets'
    Send-Key 0x54            # T = chat
    Start-Sleep -Milliseconds 600
    Save-Shot 'f1-chat-open'
    Send-Unicode '/give @s minecraft:firework_rocket 64'
    Start-Sleep -Milliseconds 400
    Save-Shot 'f2-command-typed'
    Send-Key 0x0D            # Enter
    Start-Sleep -Seconds 2
    Save-Shot 'f3-after-give'
}

Write-Host '== click 1'
Focus-Game $proc
Send-RightClick
Start-Sleep -Milliseconds 400
Write-Host '== click 2 (inside the 3 s window)'
Send-RightClick
Start-Sleep -Milliseconds 1200
Write-Host '== click 3 (after the window)'
Send-RightClick
Start-Sleep -Seconds 2
Save-Shot 'f4-after-clicks'

Write-Host '== firework log lines'
$logFile = Join-Path $ProjectRoot '_build\run\game-stdout.log'
if (Test-Path $logFile) {
    Get-Content -LiteralPath $logFile -Encoding UTF8 |
        Select-String -Pattern 'firework rocket' | ForEach-Object { $_.Line }
}
