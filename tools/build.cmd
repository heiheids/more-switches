@echo off
rem Build helper for a restricted development sandbox (pure ASCII on purpose).
rem
rem In that sandbox the JVM may only write inside the project, and java.util.zip reports every zip
rem as read-only, so Loom cannot write jars. Both are handled below:
rem   * GRADLE_USER_HOME lives under _build\ so nothing is written outside the project
rem   * org.gradle.jvmargs injects a recompiled jdk.zipfs without the writability check
rem
rem On a normal machine none of this is needed: run
rem     gradle build
rem with JDK 25 on PATH and GRADLE_USER_HOME left at its default.
rem
rem The JDK and Gradle locations are taken from the environment so this file carries no
rem machine-specific paths. Set them before calling, for example:
rem     set "JAVA_HOME=C:\path\to\jdk-25"
rem     set "GRADLE_BIN=C:\path\to\gradle-9.7.1\bin\gradle.bat"

setlocal

set "PROJECT=%~dp0.."
for %%I in ("%PROJECT%") do set "PROJECT=%%~fI"

if not defined JAVA_HOME (
  echo ERROR: JAVA_HOME is not set ^(a JDK 25 is required by Minecraft 26.3^)
  exit /b 2
)
if not defined GRADLE_BIN (
  set "GRADLE_BIN=gradle.bat"
)

set "GRADLE_USER_HOME=%PROJECT%\_build\gradle-home"
set "ZIPFS=%PROJECT%\_build\tools\zipfsfix\out"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: no java.exe under JAVA_HOME=%JAVA_HOME%
  exit /b 2
)

if "%1"=="" (
  set "TASK=build"
) else (
  set "TASK=%*"
)

echo == JAVA_HOME=%JAVA_HOME%
echo == GRADLE_USER_HOME=%GRADLE_USER_HOME%
echo == task: %TASK%

pushd "%PROJECT%"
call "%GRADLE_BIN%" %TASK% --console=plain --no-daemon
set "CODE=%ERRORLEVEL%"
popd

echo == gradle exit %CODE%
exit /b %CODE%
