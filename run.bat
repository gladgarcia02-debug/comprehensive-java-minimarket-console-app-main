@echo off
setlocal

set "ROOT=%~dp0"
set "MAIN_CLASS=Main"
if /I "%~1"=="web" set "MAIN_CLASS=WebServer"
pushd "%ROOT%"

where mvn >nul 2>nul
if not errorlevel 1 (
    mvn -f "%ROOT%minimarketkompleks\pom.xml" -Dexec.mainClass=com.conveniencestore.%MAIN_CLASS% exec:java
    set "EXIT_CODE=%errorlevel%"
    popd
    exit /b %EXIT_CODE%
)

where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK not found. Install a JDK and make sure javac is on PATH.
    popd
    exit /b 1
)

set "DRIVER=%USERPROFILE%\.m2\repository\org\postgresql\postgresql\42.7.13\postgresql-42.7.13.jar"
if not exist "%DRIVER%" (
    echo PostgreSQL JDBC driver not found at:
    echo %DRIVER%
    echo Install Maven once to download project dependencies, then run this script again.
    popd
    exit /b 1
)

set "SOURCE=%ROOT%minimarketkompleks\src\main\java"
set "RESOURCES=%ROOT%minimarketkompleks\src\main\resources"
set "OUTPUT=%ROOT%minimarketkompleks\target\classes"
if not exist "%OUTPUT%" mkdir "%OUTPUT%"

set "SOURCES="
for /r "%SOURCE%" %%F in (*.java) do call :add_source "%%F"

javac -encoding UTF-8 -source 8 -target 8 -cp "%DRIVER%" -d "%OUTPUT%" %SOURCES%
if errorlevel 1 (
    popd
    exit /b 1
)

xcopy "%RESOURCES%\*" "%OUTPUT%\" /E /I /Y >nul
if errorlevel 1 (
    echo Could not copy application resources.
    popd
    exit /b 1
)

java -cp "%OUTPUT%;%DRIVER%" com.conveniencestore.%MAIN_CLASS%
set "EXIT_CODE=%errorlevel%"
popd
exit /b %EXIT_CODE%

:add_source
set "SOURCES=%SOURCES% %1"
exit /b 0
