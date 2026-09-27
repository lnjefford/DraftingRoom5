[CmdletBinding()]
param(
    [ValidateSet('Fast', 'Changed', 'Commit', 'Release')]
    [string]$Tier = 'Fast',
    [ValidateSet('Auto', 'App', 'Wear', 'All')]
    [string]$Scope = 'Auto',
    [string[]]$Tests = @(),
    [string[]]$ScreenshotTests = @(),
    [switch]$UpdateScreenshots,
    [switch]$PlanOnly
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gradle = Join-Path $repositoryRoot 'gradlew.ps1'
$verificationStarted = Get-Date
$timings = [System.Collections.Generic.List[object]]::new()

function Add-Timing([string]$Label, [System.Diagnostics.Stopwatch]$Stopwatch) {
    $timings.Add([pscustomobject]@{
        Phase = $Label
        Seconds = [Math]::Round($Stopwatch.Elapsed.TotalSeconds, 1)
    })
}

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
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $first = Invoke-GradleAttempt $Label $Arguments 1
        if ($first.ExitCode -eq 0) { return }

        $lockFailure = $first.Text -match 'AccessDeniedException|Access is denied|Unable to delete directory|being used by another process|Could not connect to Kotlin compile daemon'
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
    } finally {
        $stopwatch.Stop()
        Add-Timing $Label $stopwatch
    }
}

function Assert-Whitespace {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Write-Host "`n[Whitespace] git diff --check"
        & git -C $repositoryRoot diff --check
        if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed.' }
    } finally {
        $stopwatch.Stop()
        Add-Timing 'Whitespace' $stopwatch
    }
}

function Get-ChangedFiles {
    $tracked = @(& git -C $repositoryRoot diff --name-only --diff-filter=ACMRTUXB HEAD --)
    if ($LASTEXITCODE -ne 0) { throw 'Could not read tracked changes from Git.' }
    $untracked = @(& git -C $repositoryRoot ls-files --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Could not read untracked files from Git.' }
    return @($tracked + $untracked |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { $_.Replace('\', '/') } |
        Sort-Object -Unique)
}

function Test-VersionMetadataOnly([string]$Path) {
    if ($Path -notin @('app/build.gradle.kts', 'wear/build.gradle.kts')) { return $false }
    $changedLines = @(& git -C $repositoryRoot diff --unified=0 HEAD -- $Path |
        Where-Object { $_ -match '^[+-]' -and $_ -notmatch '^(---|\+\+\+)' } |
        ForEach-Object { $_.Substring(1).Trim() })
    if ($changedLines.Count -eq 0) { return $false }
    return @($changedLines | Where-Object { $_ -notmatch '^(versionCode|versionName)\s*=' }).Count -eq 0
}

function Test-LauncherOnlyPath([string]$Path) {
    if ($Path -match '/res/mipmap' -or $Path -match '/res/drawable/launcher_') { return $true }
    if ($Path -match '/AndroidManifest\.xml$') {
        $changedLines = @(& git -C $repositoryRoot diff --unified=0 HEAD -- $Path |
            Where-Object { $_ -match '^[+-]' -and $_ -notmatch '^(---|\+\+\+)' } |
            ForEach-Object { $_.Substring(1).Trim() } |
            Where-Object { $_ })
        return $changedLines.Count -gt 0 -and
            @($changedLines | Where-Object { $_ -notmatch '^android:(icon|roundIcon)=' }).Count -eq 0
    }
    return $false
}

function Test-VisualPath([string]$Path) {
    if (Test-LauncherOnlyPath $Path) { return $false }
    if ($Path -match '/src/screenshotTest/' -or
        $Path -match '/res/(layout|drawable|font|anim|animator|transition)/' -or
        $Path -match '/res/values/(colors|styles|themes).*\.xml$') {
        return $true
    }
    if ($Path -match '\.(kt|java)$') {
        $absolutePath = Join-Path $repositoryRoot $Path
        return (Test-Path -LiteralPath $absolutePath) -and
            (Select-String -LiteralPath $absolutePath -Pattern '@Composable' -Quiet)
    }
    return $false
}

function Assert-ChangedFileSyntax([string[]]$Paths) {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        foreach ($path in $Paths) {
            $absolutePath = Join-Path $repositoryRoot $path
            if (-not (Test-Path -LiteralPath $absolutePath)) { continue }
            if ($path -match '\.ps1$') {
                $tokens = $null
                $errors = $null
                [System.Management.Automation.Language.Parser]::ParseFile(
                    $absolutePath,
                    [ref]$tokens,
                    [ref]$errors
                ) | Out-Null
                if ($errors.Count -gt 0) {
                    throw ('PowerShell syntax failed for {0}: {1}' -f $path, $errors[0].Message)
                }
            }
            if ($path -match '\.xml$') {
                try {
                    [xml](Get-Content -LiteralPath $absolutePath -Raw) | Out-Null
                } catch {
                    throw ('XML syntax failed for {0}: {1}' -f $path, $_.Exception.Message)
                }
            }
        }
    } finally {
        $stopwatch.Stop()
        Add-Timing 'Changed-file syntax' $stopwatch
    }
}

function Get-ChangedPlan([string[]]$Paths) {
    if ($Paths.Count -eq 0) {
        throw 'Changed validation found no working-tree changes. Use Commit or Release for a clean tree.'
    }

    $needsApp = $Scope -in @('App', 'All')
    $needsWear = $Scope -in @('Wear', 'All')
    $runAppTests = $Scope -in @('App', 'All')
    $runWearTests = $Scope -in @('Wear', 'All')
    $appScreenshots = $false
    $wearScreenshots = $false
    $toolingOnly = $Scope -eq 'Auto'

    if ($Scope -eq 'Auto') {
        foreach ($path in $Paths) {
            $versionOnly = Test-VersionMetadataOnly $path
            if ($path -like 'app/*') {
                $needsApp = $true
                $toolingOnly = $false
                if (-not $versionOnly -and $path -match '\.(kt|java|gradle|kts)$') { $runAppTests = $true }
                if (Test-VisualPath $path) { $appScreenshots = $true }
                continue
            }
            if ($path -like 'wear/*') {
                $needsWear = $true
                $toolingOnly = $false
                if (-not $versionOnly -and $path -match '\.(kt|java|gradle|kts)$') { $runWearTests = $true }
                if (Test-VisualPath $path) { $wearScreenshots = $true }
                continue
            }
            if ($path -like 'shared/*') {
                $needsApp = $true
                $needsWear = $true
                $runAppTests = $true
                $runWearTests = $true
                $toolingOnly = $false
                continue
            }
            if ($path -match '^(build\.gradle|settings\.gradle|gradle\.properties|gradle/|buildSrc/)') {
                $needsApp = $true
                $needsWear = $true
                $runAppTests = $true
                $runWearTests = $true
                $toolingOnly = $false
                continue
            }
            if ($path -notmatch '^(\.github/|docs/|tools/|AGENTS\.md$|README)') {
                $needsApp = $true
                $needsWear = $true
                $runAppTests = $true
                $runWearTests = $true
                $toolingOnly = $false
            }
        }
    }

    return [pscustomobject]@{
        NeedsApp = $needsApp
        NeedsWear = $needsWear
        RunAppTests = $runAppTests
        RunWearTests = $runWearTests
        AppScreenshots = $appScreenshots
        WearScreenshots = $wearScreenshots
        ToolingOnly = $toolingOnly
    }
}

function Write-ChangedPlan([string[]]$Paths, [object]$Plan) {
    $scopeNames = @()
    if ($Plan.NeedsApp) { $scopeNames += 'app' }
    if ($Plan.NeedsWear) { $scopeNames += 'wear' }
    if ($Plan.ToolingOnly) { $scopeNames += 'tooling' }
    Write-Host "`n[Plan] $($Paths.Count) changed file(s); scope: $($scopeNames -join ', ')"
    Write-Host "[Plan] app tests=$($Plan.RunAppTests), wear tests=$($Plan.RunWearTests), app screenshots=$($Plan.AppScreenshots), wear screenshots=$($Plan.WearScreenshots)"
    foreach ($path in $Paths) { Write-Host "  $path" }
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

function Invoke-ChangedValidation {
    $paths = Get-ChangedFiles
    $plan = Get-ChangedPlan $paths
    Write-ChangedPlan $paths $plan
    if ($PlanOnly) { return }

    Assert-Whitespace
    Assert-ChangedFileSyntax $paths
    if ($plan.ToolingOnly) { return }

    if ($plan.NeedsApp) {
        if ($plan.RunAppTests) {
            Invoke-GradleChecked 'Changed app tests' @(':app:testDebugUnitTest')
        }
        Invoke-GradleChecked 'Changed app lint and APK' @(':app:lintDebug', ':app:assembleDebug')
        if ($plan.AppScreenshots) {
            Invoke-GradleChecked 'Changed app screenshots' (Screenshot-Arguments ':app:validateDebugScreenshotTest')
        }
    }
    if ($plan.NeedsWear) {
        $wearTasks = [System.Collections.Generic.List[string]]::new()
        if ($plan.RunWearTests) { $wearTasks.Add(':wear:testDebugUnitTest') }
        $wearTasks.Add(':wear:lintDebug')
        $wearTasks.Add(':wear:assembleDebug')
        Invoke-GradleChecked 'Changed Wear checks' $wearTasks.ToArray()
        if ($plan.WearScreenshots) {
            Invoke-GradleChecked 'Changed Wear screenshots' (Screenshot-Arguments ':wear:validateDebugScreenshotTest')
        }
    }
}

function Write-TimingSummary {
    if ($timings.Count -eq 0) { return }
    Write-Host "`n[Timing]"
    foreach ($timing in $timings) {
        Write-Host ("  {0,-30} {1,8:N1}s" -f $timing.Phase, $timing.Seconds)
    }
}

Push-Location $repositoryRoot
try {
    switch ($Tier) {
        'Fast' {
            Assert-Whitespace
            Invoke-GradleChecked 'Focused unit tests' (Unit-TestArguments)
        }
        'Changed' {
            Invoke-ChangedValidation
        }
        'Commit' {
            Assert-Whitespace
            Invoke-GradleChecked 'Unit tests' @('testDebugUnitTest')
            Invoke-GradleChecked 'Lint' @('lintDebug')
            Invoke-GradleChecked 'Debug APK' @('assembleDebug')
        }
        'Release' {
            Assert-Whitespace
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
    Write-TimingSummary
}

$elapsed = (Get-Date) - $verificationStarted
Write-Host "`nVerification tier '$Tier' passed in $([Math]::Round($elapsed.TotalSeconds, 1)) seconds."
