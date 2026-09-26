@echo off
rem Double-click to build the mod in this folder and copy the jar into your Modrinth instance.
rem The first run asks which instance to use and remembers it in mods-folder.txt.
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
	echo Java is not installed. Run this in PowerShell, then try again:
	echo     winget install EclipseAdoptium.Temurin.21.JDK
	goto :fail
)

rem Start from an empty build\libs so only the new version can be picked up below.
if exist build\libs rmdir /s /q build\libs
echo Building the mod...
call gradlew.bat build
if errorlevel 1 (
	echo.
	echo BUILD FAILED. Send a screenshot of the errors above.
	goto :fail
)

set "JAR="
for %%F in (build\libs\*.jar) do (
	echo %%~nxF| findstr /i /c:"-sources" >nul || set "JAR=%%F"
)
if not defined JAR (
	echo Build finished but no jar was found in build\libs.
	goto :fail
)

set "MODS="
if exist mods-folder.txt set /p MODS=<mods-folder.txt
if defined MODS if exist "%MODS%" goto :install
call :ask_mods_folder
if not defined MODS goto :fail

:install
del /q "%MODS%\altartestclient-*.jar" >nul 2>nul
copy /y "%JAR%" "%MODS%\" >nul
if errorlevel 1 (
	echo Could not copy the jar into "%MODS%".
	echo Close Minecraft first, then run this again.
	goto :fail
)
echo.
echo Done. Installed %JAR%
echo into %MODS%
echo Start or restart Minecraft to load it.
pause
exit /b 0

:ask_mods_folder
set "PROFILES=%APPDATA%\ModrinthApp\profiles"
if not exist "%PROFILES%" goto :ask_path
echo.
echo Your Modrinth instances:
dir /b /ad "%PROFILES%"
echo.
set "NAME="
set /p "NAME=Type the instance name exactly as shown above: "
if not defined NAME goto :eof
if not exist "%PROFILES%\%NAME%" (
	echo No instance called "%NAME%".
	set "MODS="
	goto :eof
)
set "MODS=%PROFILES%\%NAME%\mods"
goto :save

:ask_path
set /p "MODS=Paste the full path of your mods folder: "
if not defined MODS goto :eof
set "MODS=%MODS:"=%"

:save
if not exist "%MODS%" mkdir "%MODS%"
> mods-folder.txt echo %MODS%
goto :eof

:fail
echo.
pause
exit /b 1
