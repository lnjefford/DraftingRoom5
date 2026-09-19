$ErrorActionPreference = 'Stop'
$repositoryRoot = $PSScriptRoot
$gradleArguments = $args

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
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $repositoryRoot '.gradle-user-home'
}
if (-not $env:ANDROID_USER_HOME) {
    $env:ANDROID_USER_HOME = Join-Path $repositoryRoot '.gradle-user-home/android-user-home'
}
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null

$javaMajor = Get-CompatibleJavaMajor $selectedJavaHome
Write-Host "DraftingRoom5 Gradle: Java $javaMajor from $selectedJavaHome"
& (Join-Path $repositoryRoot 'gradlew.bat') @gradleArguments
exit $LASTEXITCODE
