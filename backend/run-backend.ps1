$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;C:\tools\maven\bin;$env:PATH"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting MATRIQ Backend (Spring Boot on port 8080)" -ForegroundColor Green
Write-Host "Java Home: $env:JAVA_HOME" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

Set-Location $PSScriptRoot
& "C:\tools\maven\bin\mvn.cmd" spring-boot:run
