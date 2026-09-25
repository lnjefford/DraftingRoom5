$ErrorActionPreference = 'Stop'
$repositoryRoot = $PSScriptRoot
$gradleArguments = $args

function Get-RepositoryKey([string]$Path) {
    $normalized = [System.IO.Path]::GetFullPath($Path).TrimEnd('\').ToLowerInvariant()
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($normalized))
        return ([System.BitConverter]::ToString($digest).Replace('-', '').Substring(0, 12).ToLowerInvariant())
    } finally {
        $sha.Dispose()
    }
}

function Get-CompatibleJavaMajor([string]$JavaHome) {
    if ([string]::IsNullOrWhiteSpace($JavaHome)) { return $null }
    $java = Join-Path $JavaHome 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { return $null }
    try {
        $versionOutput = (& $java -version 2>&1 | Out-String)
        if ($versionOutput -match 'version\s+"(?:(1)\.)?(\d+)') {
            return [int]$Matches[2]
        }
    } catch {
        return $null
    }
    return $null
}

$javaCandidates = [System.Collections.Generic.List[string]]::new()
if ($env:JAVA_HOME) { $javaCandidates.Add($env:JAVA_HOME) }

$sharedJdkRoot = Join-Path $HOME '.jdks'
if (Test-Path -LiteralPath $sharedJdkRoot -PathType Container) {
    Get-ChildItem -LiteralPath $sharedJdkRoot -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { $javaCandidates.Add($_.FullName) }
}

$localJdkRoot = Join-Path $repositoryRoot '.tooling/jdk17'
if (Test-Path -LiteralPath $localJdkRoot -PathType Container) {
    Get-ChildItem -LiteralPath $localJdkRoot -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { $javaCandidates.Add($_.FullName) }
}

$toolsRoot = Join-Path $repositoryRoot 'tools'
if (Test-Path -LiteralPath $toolsRoot -PathType Container) {
    Get-ChildItem -LiteralPath $toolsRoot -Directory -Filter 'jdk-*' |
        Sort-Object Name -Descending |
        ForEach-Object { $javaCandidates.Add($_.FullName) }
}

$javaCandidates.Add((Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'))
if (${env:ProgramFiles(x86)}) {
    $javaCandidates.Add((Join-Path ${env:ProgramFiles(x86)} 'Android/Android Studio/jbr'))
}

$selectedJavaHome = $null
foreach ($candidate in $javaCandidates) {
    $major = Get-CompatibleJavaMajor $candidate
    if ($null -ne $major -and $major -ge 17) {
        $selectedJavaHome = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}

if (-not $selectedJavaHome) {
    throw 'DraftingRoom5 requires Java 17 or newer. Set JAVA_HOME to a compatible JDK, install Android Studio, or place a JDK under .tooling/jdk17/.'
}

$env:JAVA_HOME = $selectedJavaHome
$env:Path = "$(Join-Path $selectedJavaHome 'bin');$env:Path"
$localStateParent = if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path ([System.IO.Path]::GetTempPath()) 'DraftingRoom5'
} else {
    Join-Path $env:LOCALAPPDATA 'DraftingRoom5'
}
$repositoryKey = Get-RepositoryKey $repositoryRoot
$repositoryStateRoot = Join-Path $localStateParent "repositories/$repositoryKey"
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $localStateParent 'gradle-user-home'
}
if (-not $env:ANDROID_USER_HOME) {
    $env:ANDROID_USER_HOME = Join-Path $localStateParent 'android-user-home'
}
if (-not $env:DR5_BUILD_ROOT) {
    $env:DR5_BUILD_ROOT = Join-Path $repositoryStateRoot 'build'
}
$projectCache = Join-Path $repositoryStateRoot 'project-cache'
New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME,$env:ANDROID_USER_HOME,$env:DR5_BUILD_ROOT,$projectCache -Force | Out-Null

$hasProjectCache = $gradleArguments | Where-Object { $_ -eq '--project-cache-dir' -or $_ -like '--project-cache-dir=*' }
if (-not $hasProjectCache) {
    $gradleArguments = @('--project-cache-dir', $projectCache) + $gradleArguments
}

$javaMajor = Get-CompatibleJavaMajor $selectedJavaHome
Write-Host "DraftingRoom5 Gradle: Java $javaMajor from $selectedJavaHome"
Write-Host "DraftingRoom5 Gradle: isolated state at $repositoryStateRoot"
& (Join-Path $repositoryRoot 'gradlew.bat') @gradleArguments
exit $LASTEXITCODE
