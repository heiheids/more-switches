# Launch the real Fabric test instance so the built jar is exercised exactly as a player
# would exercise it.
#
# PURE ASCII ON PURPOSE. Windows PowerShell 5.1 decodes a BOM-less .ps1 as GBK, so a Chinese
# literal here silently becomes a different path (one run created a garbage directory on D:\
# because of exactly that). All paths are derived from $PSScriptRoot instead.
#
#   powershell -ExecutionPolicy Bypass -File tools\launch.ps1 -Seconds 170
#   powershell -ExecutionPolicy Bypass -File tools\launch.ps1 -QuickPlay "save name"
#   powershell -ExecutionPolicy Bypass -File tools\launch.ps1 -NoLaunch   # print, do not start
#
# The classpath comes from the instance's own version JSON, so it can never drift from the
# loader the instance actually runs.

param(
    # The instance and JDK are machine-specific, so they are parameters, not constants: nothing
    # here assumes a particular user or install location. Set MC_INSTANCE_DIR and JAVA_HOME, or
    # pass -InstanceDir / -Java explicitly.
    [string]$InstanceDir = $(if ($env:MC_INSTANCE_DIR) { $env:MC_INSTANCE_DIR } else { '' }),
    [string]$GameDir = '',
    [string]$Libraries = '',
    [string]$Java = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }),
    [string]$RunDir = '',
    [int]$Seconds = 170,
    [string]$QuickPlay = '',
    [string]$ExtraJvmArgs = '',
    [switch]$NoLaunch,
    # Dev only: the instance JSON passes --demo, which opens the demo notice and swallows input.
    [switch]$DropDemo
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $RunDir) { $RunDir = Join-Path $ProjectRoot '_build\run' }

if (-not $InstanceDir) {
    throw 'set MC_INSTANCE_DIR or pass -InstanceDir, e.g. <minecraft>\versions\<version>'
}
if (-not (Test-Path $Java)) { throw "Java 25 runtime not found: $Java (set JAVA_HOME or pass -Java)" }
if (-not (Test-Path $InstanceDir)) { throw "instance not found: $InstanceDir" }

if (-not $GameDir) { $GameDir = $InstanceDir }
if (-not (Test-Path (Join-Path $GameDir 'mods'))) { throw "no mods folder under gameDir: $GameDir" }

if (-not $Libraries) {
    # instance dir is <root>\versions\<name>; libraries sit next to versions/ and assets/
    $root = Split-Path (Split-Path $InstanceDir -Parent) -Parent
    $Libraries = Join-Path $root 'libraries'
}
$assetsRoot = Join-Path (Split-Path (Split-Path $InstanceDir -Parent) -Parent) 'assets'

$versionName = Split-Path $InstanceDir -Leaf
$jsonFile = Join-Path $InstanceDir ($versionName + '.json')
if (-not (Test-Path $jsonFile)) { throw "version json not found: $jsonFile" }
$version = Get-Content -LiteralPath $jsonFile -Raw -Encoding UTF8 | ConvertFrom-Json

# ---- classpath ---------------------------------------------------------------
$cp = New-Object System.Collections.Generic.List[string]
foreach ($lib in $version.libraries) {
    if (-not $lib.name) { continue }
    $parts = $lib.name.Split(':')
    if ($parts.Count -lt 3) { continue }
    $rel = (($parts[0] -replace '\.', '/')) + "/$($parts[1])/$($parts[2])/$($parts[1])-$($parts[2])"
    if ($parts.Count -ge 4) { $rel += "-$($parts[3])" }
    $full = Join-Path $Libraries ($rel + '.jar')
    if (Test-Path $full) { $cp.Add($full) }
}
$clientJar = Join-Path $InstanceDir ($versionName + '.jar')
if (-not (Test-Path $clientJar)) { throw "game jar not found: $clientJar" }
$cp.Add($clientJar)
$classpath = ($cp -join ';')

# ---- output files ------------------------------------------------------------
New-Item -ItemType Directory -Force $RunDir | Out-Null
$gameOutFile = Join-Path $RunDir 'game-stdout.log'
$gameErrFile = Join-Path $RunDir 'game-stderr.log'
foreach ($f in @($gameOutFile, $gameErrFile)) {
    if (Test-Path $f) { Remove-Item -LiteralPath $f -Force }
}

# ---- jvm arguments -----------------------------------------------------------
# The LWJGL natives ship as *-natives-windows.jar on the classpath; LWJGL extracts them to a
# directory it can write. In this sandbox only the project is writable, so the extraction dir
# and the temp dir are both pointed inside it. Without that, the client dies at startup with
# "Failed to locate library: lwjgl.dll".
$nativesDir = Join-Path $RunDir 'natives'
$tempDir = Join-Path $RunDir 'tmp'
New-Item -ItemType Directory -Force $nativesDir | Out-Null
New-Item -ItemType Directory -Force $tempDir | Out-Null

$jvm = New-Object System.Collections.Generic.List[string]
$jvm.Add('-Xmx4G')
$jvm.Add('-Dfile.encoding=UTF-8')
$jvm.Add('-Dstdout.encoding=UTF-8')
$jvm.Add('-Dstderr.encoding=UTF-8')
$jvm.Add('-Djava.io.tmpdir=' + $tempDir)
$jvm.Add('-Dorg.lwjgl.system.SharedLibraryExtractPath=' + $nativesDir)
$jvm.Add('-Dorg.lwjgl.system.librarypath=' + $nativesDir)
$jvm.Add('-Djna.tmpdir=' + $tempDir)
$log4jConfig = Join-Path $InstanceDir 'log4j2.xml'
if (Test-Path $log4jConfig) { $jvm.Add('-Dlog4j.configurationFile=' + $log4jConfig) }
$jvm.Add('-Dminecraft.client.jar=' + $clientJar)
$jvm.Add('-Dminecraft.launcher.brand=DSH')
$jvm.Add('-Dminecraft.launcher.version=1.0')
$jvm.Add('-DlibraryDirectory=' + $Libraries)
$jvm.Add('-Djava.net.preferIPv6Addresses=system')
$jvm.Add('--sun-misc-unsafe-memory-access=allow')
$jvm.Add('--enable-native-access=ALL-UNNAMED')
$jvm.Add('--add-opens')
$jvm.Add('java.base/java.lang.invoke=ALL-UNNAMED')
$jvm.Add('--add-exports')
$jvm.Add('java.base/jdk.internal.misc=ALL-UNNAMED')
$jvm.Add('-XX:StackShadowPages=32')
if ($ExtraJvmArgs) {
    foreach ($extra in ($ExtraJvmArgs -split '\s+')) {
        if ($extra) { $jvm.Add($extra) }
    }
}
$jvm.Add('-cp')
$jvm.Add($classpath)
# The main class must sit between the JVM arguments and the game arguments, otherwise the
# game's --username-style flags are read as JVM options.
$jvm.Add($version.mainClass)

# ---- game arguments ----------------------------------------------------------
$subs = @{
    '${auth_player_name}'   = 'xzbzx'
    '${version_name}'       = $versionName
    '${game_directory}'     = $GameDir
    '${assets_root}'        = $assetsRoot
    '${assets_index_name}'  = $version.assetIndex.id
    '${auth_uuid}'          = '15f0a72930034171ac704f7c230c730f'
    '${auth_access_token}'  = '0'
    '${clientid}'           = ''
    '${auth_xuid}'          = ''
    '${version_type}'       = 'DSH'
    '${resolution_width}'   = '1280'
    '${resolution_height}'  = '720'
}

function Expand-Placeholders([string]$value) {
    foreach ($key in $subs.Keys) { $value = $value.Replace($key, $subs[$key]) }
    return $value
}

function Test-Rules($rules) {
    if (-not $rules) { return $true }
    $allowed = $false
    foreach ($rule in $rules) {
        if ($rule.action -eq 'allow') {
            if ($rule.os -and $rule.os.name -and $rule.os.name -ne 'windows') { continue }
            $allowed = $true
        } elseif ($rule.action -eq 'disallow') {
            if ($rule.os -and $rule.os.name -and $rule.os.name -ne 'windows') { continue }
            $allowed = $false
        }
    }
    return $allowed
}

# The version JSON lists --quickPlayPath / --quickPlaySingleplayer / --quickPlayMultiplayer /
# --quickPlayRealms with empty values. Passing those as bare flags makes the game consume the
# NEXT argument as their value, so they are dropped and QuickPlay is added explicitly below.
$DROP_EMPTY_FLAGS = @('--quickPlayPath', '--quickPlaySingleplayer', '--quickPlayMultiplayer', '--quickPlayRealms')

$gameArgs = New-Object System.Collections.Generic.List[string]
foreach ($arg in $version.arguments.game) {
    if ($arg -is [string]) {
        if ($DROP_EMPTY_FLAGS -contains $arg) { continue }
        if ($DropDemo -and $arg -eq '--demo') { continue }
        $value = Expand-Placeholders $arg
        if ($value -match '\$\{') { continue }
        if ($value -eq '') { continue }
        $gameArgs.Add($value)
    } elseif ($arg.rules) {
        if (-not (Test-Rules $arg.rules)) { continue }
        foreach ($v in $arg.value) {
            $expanded = Expand-Placeholders $v
            if ($expanded -match '\$\{') { continue }
            if ($DROP_EMPTY_FLAGS -contains $expanded) { continue }
            if ($expanded -eq '') { continue }
            $gameArgs.Add($expanded)
        }
    }
}
if ($QuickPlay) {
    $gameArgs.Add('--quickPlaySingleplayer')
    $gameArgs.Add($QuickPlay)
}

function Quote-Arg([string]$value) {
    if ($value -eq '') { return '""' }
    if ($value -notmatch '[\s"]') { return $value }
    return '"' + ($value -replace '"', '\"') + '"'
}

$allArgs = @(@($jvm) + @($gameArgs) | Where-Object { $_ -ne $null -and $_ -ne '' })
$commandLine = ($allArgs | ForEach-Object { Quote-Arg $_ }) -join ' '

Write-Host ("== instance : {0}" -f $InstanceDir)
Write-Host ("== gameDir  : {0}" -f $GameDir)
Write-Host ("== run dir  : {0}" -f $RunDir)
Write-Host ("== classpath: {0} entries" -f $cp.Count)

if ($NoLaunch) {
    Write-Host "== command =="
    Write-Host $commandLine
    return
}

Write-Host ("== starting the client for {0}s" -f $Seconds)
$process = Start-Process -FilePath $Java -ArgumentList $commandLine -WorkingDirectory $GameDir `
    -RedirectStandardOutput $gameOutFile -RedirectStandardError $gameErrFile `
    -PassThru -WindowStyle Normal

$deadline = (Get-Date).AddSeconds($Seconds)
while (-not $process.HasExited -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $process.Refresh()
}
if (-not $process.HasExited) {
    Write-Host "== stopping the process tree"
    & taskkill /PID $process.Id /T /F | Out-Null
}

Write-Host "== done; stdout: $gameOutFile"
