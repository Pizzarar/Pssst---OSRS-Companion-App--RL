@echo off
rem Launches the RuneLite developer client with this plugin loaded.
rem The JDK comes from org.gradle.java.home in %USERPROFILE%\.gradle\gradle.properties (not committed).
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" forward tcp:8789 tcp:8789 >nul 2>&1
)
call gradlew.bat testPlugin
