@echo off
setlocal

set "ROOT=%~dp0"
set "SOURCE=%ROOT%minimarketkompleks\src\main\java"
set "OUTPUT=%ROOT%minimarketkompleks\target\classes"

where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK not found. Install a JDK and make sure javac is on PATH.
    exit /b 1
)

if not exist "%OUTPUT%" mkdir "%OUTPUT%"

set "SOURCES="
for /r "%SOURCE%" %%F in (*.java) do call :add_source "%%F"

javac -encoding UTF-8 -d "%OUTPUT%" %SOURCES%
if errorlevel 1 exit /b 1

java -cp "%OUTPUT%" com.conveniencestore.Main
exit /b %errorlevel%

:add_source
set "SOURCES=%SOURCES% %1"
exit /b 0
