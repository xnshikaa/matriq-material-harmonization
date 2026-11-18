@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\tools\maven\bin;%PATH%"
if not defined MATRIQ_SECURE_COOKIE set "MATRIQ_SECURE_COOKIE=false"
cd /d "%~dp0"
call mvn.cmd spring-boot:run
