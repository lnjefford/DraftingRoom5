[CmdletBinding()]
param(
    [ValidateSet('Fast', 'Commit', 'Release')]
    [string]$Tier = 'Fast',
    [string[]]$Tests = @(),
    [string[]]$ScreenshotTests = @(),
    [switch]$UpdateScreenshots
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.ps1'
$verificationStarted = Get-Date

function Invoke-GradleAttempt([string]$Label, [string[]]$Arguments, [int]$Attempt) {
    $logRoot = if ($env:DR5_BUILD_ROOT) {
        Join-Path $env:DR5_BUILD_ROOT 'verification-logs'
    } else {
        Join-Path ([System.IO.Path]::GetTempPath()) 'DraftingRoom5-verification-logs'
    }
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $safeLabel = $Label -replace '[^A-Za-z0-9_.-]', '-'
    $log = Join-Path $logRoot "$safeLabel-attempt-$Attempt.log"
    Write-Host "`n[$Label] ./gradlew.ps1 $($Arguments -join ' ')"
    & $gradle @Arguments 2>&1 | Tee-Object -FilePath $log
    return @{ ExitCode = $LASTEXITCODE; Log = $log; Text = (Get-Content -LiteralPath $log -Raw) }
}

function Invoke-GradleChecked([string]$Label, [string[]]$Arguments) {
    $first = Invoke-GradleAttempt $Label $Arguments 1
    if ($first.ExitCode -eq 0) { return }

    $lockFailure = $first.Text -match 'AccessDeniedException|Unable to delete directory|being used by another process|Could not connect to Kotlin compile daemon'
    $memoryFailure = $first.Text -match 'OutOfMemoryError|Java heap space|GC overhead limit exceeded'
    if (-not $lockFailure -and -not $memoryFailure) {
        throw "$Label failed. See $($first.Log)"
    }

    $reason = if ($memoryFailure) { 'memory pressure' } else { 'a transient file lock' }
    Write-Warning "$Label encountered $reason. Stopping Gradle and retrying once with one worker."
    & $gradle --stop | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Could not stop Gradle before retrying $Label." }
    $retryArguments = @('--max-workers=1') + $Arguments
    $second = Invoke-GradleAttempt $Label $retryArguments 2
    if ($second.ExitCode -ne 0) { throw "$Label failed after automatic recovery. See $($second.Log)" }
}

function Assert-Whitespace {
    Write-Host "`n[Whitespace] git diff --check"
    & git -C $repositoryRoot diff --check
    if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed.' }
}

function Unit-TestArguments {
    $arguments = [System.Collections.Generic.List[string]]::new()
    $arguments.Add('testDebugUnitTest')
    foreach ($test in $Tests) { $arguments.Add('--tests'); $arguments.Add($test) }
    return $arguments.ToArray()
}

function Screenshot-Arguments([string]$Task) {
    $arguments = [System.Collections.Generic.List[string]]::new()
    $arguments.Add($Task)
    foreach ($test in $ScreenshotTests) { $arguments.Add('--tests'); $arguments.Add($test) }
    return $arguments.ToArray()
}

Push-Location $repositoryRoot
try {
    Assert-Whitespace
    switch ($Tier) {
        'Fast' {
            Invoke-GradleChecked 'Focused unit tests' (Unit-TestArguments)
        }
        'Commit' {
            Invoke-GradleChecked 'Unit tests' @('testDebugUnitTest')
            Invoke-GradleChecked 'Lint' @('lintDebug')
            Invoke-GradleChecked 'Debug APK' @('assembleDebug')
        }
        'Release' {
            Invoke-GradleChecked 'Unit tests' @('testDebugUnitTest')
            Invoke-GradleChecked 'Lint' @('lintDebug')
            Invoke-GradleChecked 'Debug APK' @('assembleDebug')
            if ($UpdateScreenshots) {
                Invoke-GradleChecked 'Update screenshot references' (Screenshot-Arguments 'updateDebugScreenshotTest')
            }
            Invoke-GradleChecked 'Screenshot validation' (Screenshot-Arguments 'validateDebugScreenshotTest')
        }
    }
} finally {
    Pop-Location
}

$elapsed = (Get-Date) - $verificationStarted
Write-Host "`nVerification tier '$Tier' passed in $([Math]::Round($elapsed.TotalSeconds, 1)) seconds."
