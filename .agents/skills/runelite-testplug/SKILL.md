---
name: runelite-testplug
description: "Launches the localized RuneLite developer client for testing. Trigger this skill whenever the user types /testplug."
---

# RuneLite Test Launcher (/testplug)

When triggered, do NOT execute the Gradle task or launcher in the background. Instead, follow these steps:

1. Create or overwrite `run-test.bat` in `L:\Dev\Plugins\PssstRunelite\run-test.bat` with the following content:
   ```cmd
   @echo off
   set JAVA_HOME=C:\Users\aubro\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2
   set PATH=%JAVA_HOME%\bin;%PATH%
   call gradlew.bat testPlugin
   ```

2. Output a brief reminder on refreshing the Jagex Account session token:
   - Run RuneLite with `--insecure-write-credentials` to write fresh session credentials to `%USERPROFILE%\.runelite\credentials.properties`.

3. Instruct the user to run `.\run-test.bat` directly in their own terminal so the RuneLite GUI opens visibly on their screen.
