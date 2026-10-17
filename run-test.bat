@echo off
rem Launches the RuneLite developer client with this plugin loaded.
rem The JDK comes from org.gradle.java.home in %USERPROFILE%\.gradle\gradle.properties (not committed).
call gradlew.bat testPlugin
