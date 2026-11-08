$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot "target\kramjav-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "Building the Spring Boot JAR before starting the service..."
    & (Join-Path $projectRoot "mvnw.cmd") clean package -DskipTests
    if (-not $?) {
        throw "The build failed while creating the Spring Boot JAR."
    }
}

$javaCommand = Get-Command java -ErrorAction Stop
Write-Host "Starting kramjav on http://127.0.0.1:8080"
& $javaCommand.Source -jar $jarPath
