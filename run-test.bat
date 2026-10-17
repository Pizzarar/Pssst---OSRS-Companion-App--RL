@echo off
set JAVA_HOME=C:\Users\aubro\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat testPlugin
