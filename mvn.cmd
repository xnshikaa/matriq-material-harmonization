@echo off
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\tools\maven\bin;%PATH%"
"C:\tools\maven\bin\mvn.cmd" %*
