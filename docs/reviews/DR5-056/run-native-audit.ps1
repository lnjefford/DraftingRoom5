param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$BuildRoot,
    [Parameter(Mandatory=$true)][string]$EvidenceDirectory,
    [switch]$SkipAppInstall
)
$ErrorActionPreference = 'Stop'
$repo = Resolve-Path "$PSScriptRoot/../../.."
$adb = Join-Path $repo 'tools/android-sdk/platform-tools/adb.exe'
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
if (-not $SkipAppInstall) {
    & $adb -s $Serial install -r "$BuildRoot/app/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE) { throw 'App installation failed' }
}
& $adb -s $Serial install -r "$BuildRoot/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if ($LASTEXITCODE) { throw 'Test installation failed' }
& $adb -s $Serial shell appwidget grantbind --package dev.draftingroom5 --user 0
try {
    $result = & $adb -s $Serial shell am instrument -w dev.draftingroom5.test/dev.draftingroom5.WidgetAuditInstrumentation
    $result | Set-Content "$EvidenceDirectory/instrumentation.txt"
    $result
    if (($result -join "`n") -notmatch 'PASS: all native widget assertions') {
        throw 'Native assertions failed; inspect instrumentation.txt'
    }
    # Export private test evidence through run-as using a binary-safe process pipe.
    $archive = Join-Path $EvidenceDirectory 'native-renders.tar'
    $start = [Diagnostics.ProcessStartInfo]::new($adb)
    $start.Arguments = "-s $Serial exec-out run-as dev.draftingroom5 tar -C files/widget-audit -cf - ."
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.CreateNoWindow = $true
    $process = [Diagnostics.Process]::Start($start)
    $file = [IO.File]::Create($archive)
    try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
    $process.WaitForExit()
    if ($process.ExitCode) { throw 'Native render export failed' }
    & tar -xf $archive -C $EvidenceDirectory
    if ($LASTEXITCODE) { throw 'Native render extraction failed' }
    Remove-Item -LiteralPath $archive
} finally {
    & $adb -s $Serial shell appwidget revokebind --package dev.draftingroom5 --user 0
}
