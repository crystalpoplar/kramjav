param(
    [string]$ServiceName = "KramjavAI",
    [string]$DisplayName = "Kramjav AI Service",
    [string]$Description = "Windows service for the local Ollama-backed Spring Boot API.",
    [string]$NssmPath = "C:\\nssm\\win64\\nssm.exe"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $NssmPath)) {
    throw "NSSM was not found at '$NssmPath'. Download it from https://nssm.cc and try again."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot "target\kramjav-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "Building the Spring Boot JAR before installing the Windows service..."
    & (Join-Path $projectRoot "mvnw.cmd") clean package -DskipTests
    if (-not $?) {
        throw "The build failed while creating the Spring Boot JAR."
    }
}

$javaCommand = Get-Command java -ErrorAction Stop

& $NssmPath install $ServiceName $javaCommand.Source
& $NssmPath set $ServiceName AppParameters "-jar \"$jarPath\""
& $NssmPath set $ServiceName AppDirectory $projectRoot
& $NssmPath set $ServiceName DisplayName $DisplayName
& $NssmPath set $ServiceName Description $Description
& $NssmPath set $ServiceName Start SERVICE_AUTO_START
& $NssmPath start $ServiceName

Write-Host "Installed and started service '$ServiceName'."
Write-Host "Use 'sc query $ServiceName' or 'nssm status $ServiceName' to verify its status."
