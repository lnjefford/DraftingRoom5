param(
    [string]$Serial = 'emulator-5554',
    [string]$BuildRoot = '.tooling/dr5-065-build',
    [string]$EvidenceDirectory = 'docs/reviews/DR5-065/native'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../../..").Path
$adb = Join-Path $repo 'tools/android-sdk/platform-tools/adb.exe'
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
& $adb -s $Serial install -r "$BuildRoot/app/outputs/apk/debug/app-debug.apk"
if ($LASTEXITCODE) { throw 'App installation failed; preserve existing app data and investigate signing.' }
& $adb -s $Serial install -r "$BuildRoot/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if ($LASTEXITCODE) { throw 'Native test installation failed' }
$result = & $adb -s $Serial shell am instrument -w -e audit progression dev.draftingroom5.test/dev.draftingroom5.WidgetAuditInstrumentation
$result | Set-Content "$EvidenceDirectory/instrumentation.txt"
$result
$start = [Diagnostics.ProcessStartInfo]::new($adb)
$start.Arguments = "-s $Serial exec-out run-as dev.draftingroom5 tar -C files/progression-audit -cf - ."
$start.UseShellExecute = $false
$start.RedirectStandardOutput = $true
$start.CreateNoWindow = $true
$process = [Diagnostics.Process]::Start($start)
$archive = Join-Path $EvidenceDirectory 'native-evidence.tar'
$file = [IO.File]::Create($archive)
try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
$process.WaitForExit()
if ($process.ExitCode) { throw 'Native evidence export failed' }
& tar -xf $archive -C $EvidenceDirectory
if ($LASTEXITCODE) { throw 'Native evidence extraction failed' }
Remove-Item -LiteralPath $archive
if (($result -join "`n") -notmatch 'PASS: Android API' -or ($result -join "`n") -match 'FAIL:') {
    throw 'Native audit failed; inspect retained evidence.'
}
