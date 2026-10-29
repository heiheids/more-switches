# Print the exact strings the mod will show, by reading its language files the same way the mod does.
#
# Rendering a screenshot of a background GL window is unreliable on this machine (the window comes
# back all black), so the wording is verified at the source instead: this script loads the two
# JSON files, applies the same language-selection rule as ModLang, and prints every line the
# settings screen draws for each switch. Any leftover Chinese in an English build shows up as
# Chinese characters in the English column.

param(
    [string]$Language = 'auto'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$langDir = Join-Path $ProjectRoot 'src\main\resources\assets\moreswitches\lang'

$zh = Get-Content (Join-Path $langDir 'zh_cn.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$en = Get-Content (Join-Path $langDir 'en_us.json') -Raw -Encoding UTF8 | ConvertFrom-Json

$switches = @('window_lock', 'firework_cooldown', 'rain_sleep', 'drop_key_lock', 'fullscreen_key_lock', 'effect_particles_off', 'light_preview', 'moving_light', 'resource_pack_warning_off', 'campfire_smoke_off', 'tutorial_off')

# ModLang rule: "auto" follows the game language, otherwise the pinned choice wins.
$gameLanguage = 'zh_cn'
if ($Language -eq 'auto') { $active = $gameLanguage } else { $active = $Language }
$table = if ($active -eq 'zh_cn') { $zh } else { $en }

function Get-Text($key) {
    $value = $table.$key
    if ($null -eq $value) { return "<missing: $key>" }
    return $value
}

Write-Host ("== active language: {0}" -f $active)
Write-Host ("== title   : {0}" -f (Get-Text 'moreswitches.screen.title'))
Write-Host ("== subtitle: {0}" -f (Get-Text 'moreswitches.screen.title_alt'))
Write-Host ("== hint    : {0}" -f (Get-Text 'moreswitches.screen.hint').Replace('%s', 'J'))
Write-Host ("== done    : {0}" -f (Get-Text 'moreswitches.screen.done'))
Write-Host ("== language: {0}" -f (Get-Text 'moreswitches.screen.language').Replace('%s', (Get-Text "moreswitches.screen.language.$active")))

foreach ($id in $switches) {
    Write-Host ''
    Write-Host ("-- {0}" -f $id)
    Write-Host ("   name : {0}" -f (Get-Text "moreswitches.switch.$id.name"))
    Write-Host ("   desc : {0}" -f (Get-Text "moreswitches.switch.$id.desc"))
}

# Key-set parity: a key present in one file but not the other is a missing translation.
$zhKeys = $zh.PSObject.Properties.Name
$enKeys = $en.PSObject.Properties.Name
$onlyZh = $zhKeys | Where-Object { $enKeys -notcontains $_ }
$onlyEn = $enKeys | Where-Object { $zhKeys -notcontains $_ }
Write-Host ''
Write-Host ("== key count: zh {0} / en {1}" -f $zhKeys.Count, $enKeys.Count)
if ($onlyZh) { Write-Host ("== only in zh: " + ($onlyZh -join ', ')) }
if ($onlyEn) { Write-Host ("== only in en: " + ($onlyEn -join ', ')) }
if (-not $onlyZh -and -not $onlyEn) { Write-Host '== key sets match' }
