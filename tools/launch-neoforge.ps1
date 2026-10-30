# Launches the real NeoForge 1.21.1 instance so the built jar is exercised exactly as a player
# would exercise it.
#
# PURE ASCII ON PURPOSE. Windows PowerShell 5.1 decodes a BOM-less .ps1 as GBK, so a Chinese
# literal here would silently become a different path. All paths are passed in or derived.
#
# NeoForge does not start through the vanilla main class: it boots via BootstrapLauncher, with the
# module path and the --launchTarget argument both supplied here. The classpath and the argument
# lists come from the instance's own version JSON so they can never drift from what the launcher
# installed.

param(
    [Parameter(Mandatory = $true)][string]$InstanceDir,
    [Parameter(Mandatory = $true)][string]$Java,
    [string]$GameDir = '',
    [int]$Seconds = 150,
    [string]$ExtraJvmArgs = ''
)

$ErrorActionPreference = 'Stop'

$json = Join-Path $InstanceDir ((Split-Path -Leaf $InstanceDir) + '.json')
if (-not (Test-Path $json)) { throw "version json not found: $json" }
$version = Get-Content $json -Raw | ConvertFrom-Json

$libRoot = 'D:\1.21.1F-NF\.minecraft\libraries'
if (-not $GameDir) { $GameDir = 'D:\1.21.1F-NF\.minecraft' }

function Resolve-Lib([string]$name) {
    # name is group:artifact:version[:classifier]
    $parts = $name.Split(':')
    $group = $parts[0]; $artifact = $parts[1]; $ver = $parts[2]
    $classifier = if ($parts.Count -gt 3) { $parts[3] } else { '' }
    $groupPath = $group.Replace('.', '\')
    $file = if ($classifier) { "$artifact-$ver-$classifier.jar" } else { "$artifact-$ver.jar" }
    $candidate = Join-Path $libRoot "$groupPath\$artifact\$ver\$file"
    if (Test-Path $candidate) { return $candidate }
    return $null
}

$classpath = New-Object System.Collections.Generic.List[string]
$modulePath = New-Object System.Collections.Generic.List[string]
foreach ($lib in $version.libraries) {
    $resolved = Resolve-Lib $lib.name
    if (-not $resolved) { continue }
    # Bootstraplauncher and its friends belong on the module path, everything else on the classpath.
    if ($lib.name -match 'bootstraplauncher|securejarhandler|asm|JarJar|mergetool') {
        $modulePath.Add($resolved)
    } else {
        $classpath.Add($resolved)
    }
}

# The game itself. NeoForge looks up a module named 'minecraft', so the deobfuscated jar is
# made into a named module and put on the module path rather than the classpath. The installed
# obfuscated jar cannot serve: the mod is compiled against Mojang names.
$projectRoot = Split-Path -Parent $PSScriptRoot
$moduleJar = Join-Path $projectRoot '_build/nf-mc/minecraft-module.jar'
if (-not (Test-Path $moduleJar)) { throw ('missing module jar: ' + $moduleJar) }
$classpath.Add($moduleJar)

$assets = Join-Path $GameDir 'assets'
$natives = Join-Path $GameDir 'natives'
New-Item -ItemType Directory -Force -Path $natives | Out-Null

$runDir = $InstanceDir
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$libraryDirArg = '-DlibraryDirectory=' + $libRoot
$ignoreListArg = '-DignoreList=client-extra,1.21.1-NeoForge.jar,minecraft-module.jar'
$libPathArg = '-Djava.library.path=' + $natives
$lwjglPathArg = '-Dorg.lwjgl.librarypath=' + $natives

$arguments = @(
    '-Xmx3G',
    $libraryDirArg,
    '-Dminecraft.launcher.brand=dev',
    '-Dminecraft.launcher.version=1.0',
    $ignoreListArg,
    $libPathArg,
    $lwjglPathArg,
    '-Dfml.earlyprogresswindow=false',
    '--module-path', ($modulePath -join ';'),
    '--add-modules', 'ALL-MODULE-PATH',
    '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler',
    '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler',
    '--add-opens', 'java.base/sun.security.util=cpw.mods.securejarhandler',
    '--add-opens', 'jdk.naming.dns/com.sun.jndi.dns=java.naming',
    '-p', ($modulePath -join ';'),
    '-cp', ($classpath -join ';'),
    'cpw.mods.bootstraplauncher.BootstrapLauncher',
    '--launchTarget', 'forgeclient',
    '--fml.fmlVersion', '4.0.45',
    '--fml.neoForgeVersion', '21.1.256',
    '--fml.neoFormVersion', '20240808.144430',
    '--fml.mcVersion', '1.21.1',
    '--gameDir', $runDir,
    '--assetsDir', $assets,
    '--assetIndex', '17',
    '--username', 'Dev',
    '--uuid', '00000000000000000000000000000000',
    '--accessToken', '0',
    '--version', '1.21.1-NeoForge',
    '--userType', 'legacy',
    '--versionType', 'release'
)

if ($ExtraJvmArgs) {
    $extra = $ExtraJvmArgs.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
    $arguments = $extra + $arguments
}

$out = Join-Path $runDir 'nf-stdout.log'
$err = Join-Path $runDir 'nf-stderr.log'
Remove-Item $out, $err -Force -ErrorAction SilentlyContinue

Write-Host ("java    : " + $Java)
Write-Host ("mods    : " + (Join-Path $InstanceDir 'mods'))
Write-Host ("gameDir : " + $runDir)

$quoted = $arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
$commandLine = '"' + $Java + '" ' + ($quoted -join ' ')

Write-Host ("launching: " + $Java)
Write-Host ("args    : " + $arguments.Count)
$cmd = '/c ""' + $Java + '" ' + ($quoted -join ' ') + ' > "' + $out + '" 2> "' + $err + '""'
Start-Process -FilePath 'cmd.exe' -ArgumentList $cmd -WorkingDirectory $runDir -WindowStyle Minimized | Out-Null

if ($Seconds -gt 0) {
    Start-Sleep -Seconds $Seconds
    $still = Get-Process -Name java -ErrorAction SilentlyContinue
    if ($still) { Write-Host "java still running after $Seconds s" }
    else { Write-Host "java exited" }
}
