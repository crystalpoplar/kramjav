@echo off
setlocal

set "PROJECT_ROOT=%~dp0.."
set "JAR_PATH=%PROJECT_ROOT%\target\kramjav-0.0.1-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
    echo Building the Spring Boot JAR before starting the service...
    call "%PROJECT_ROOT%\mvnw.cmd" clean package -DskipTests
    if errorlevel 1 (
        exit /b %errorlevel%
    )
)

java -jar "%JAR_PATH%"
