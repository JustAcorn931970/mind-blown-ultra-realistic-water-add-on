@echo off
setlocal EnableExtensions
pushd "%~dp0"

rem Build with authentic Minecraft 26.3 + Fabric dependencies. No patched bytecode or API stubs.
set "GRADLE_VERSION=9.8.2"
set "TOOLS_DIR=%~dp0.tools"
set "GRADLE_HOME=%TOOLS_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%TOOLS_DIR%\gradle-%GRADLE_VERSION%-bin.zip"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: A JDK is required, not just a Java runtime.
  echo Install JDK 25, set JAVA_HOME to its folder, then run build.bat again.
  echo Minecraft 26.3 requires Java 25.
  pause
  exit /b 1
)

for /f "tokens=2" %%V in ('javac -version 2^>^&1') do set "JAVAC_VERSION=%%V"
for /f "tokens=1 delims=." %%V in ("%JAVAC_VERSION%") do set "JAVAC_MAJOR=%%V"
if not defined JAVAC_MAJOR (
  echo ERROR: Could not determine javac version.
  pause
  exit /b 1
)
if %JAVAC_MAJOR% LSS 25 (
  echo ERROR: JDK 25 or newer is required. Found javac %JAVAC_VERSION%.
  echo Install JDK 25, set JAVA_HOME, then run this again.
  pause
  exit /b 1
)

echo Using JDK %JAVAC_VERSION%.
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Downloading official Gradle %GRADLE_VERSION%...
  if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'; Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%TOOLS_DIR%' -Force; Remove-Item -LiteralPath '%GRADLE_ZIP%' -Force"
  if errorlevel 1 (
    echo ERROR: Could not download Gradle. Check your internet connection and try again.
    pause
    exit /b 1
  )
)

if /i "%~1"=="test" (
  echo Launching the development client for testing...
  call "%GRADLE_HOME%\bin\gradle.bat" --no-daemon runClient
) else (
  echo Building Wind Blower against real Minecraft 26.3 and Fabric API dependencies...
  call "%GRADLE_HOME%\bin\gradle.bat" --no-daemon clean build
)
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Do not install a JAR from an unsuccessful build.
  echo Copy the last 40-60 lines from this window and send them for diagnosis.
  pause
  exit /b 1
)
if /i not "%~1"=="test" (
  echo.
  echo BUILD SUCCEEDED.
  echo The newly compiled JAR is in: %~dp0build\libs\
  dir /b "%~dp0build\libs\*.jar"
)
pause
popd
