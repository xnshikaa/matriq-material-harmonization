@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\tools\maven\bin;%PATH%"

echo ========================================================
echo Starting MATRIQ Backend (Spring Boot on port 8080)
echo ========================================================

cd /d "%~dp0backend"
call "C:\tools\maven\bin\mvn.cmd" spring-boot:run
pause
