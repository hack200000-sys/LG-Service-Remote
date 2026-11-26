@echo off
setlocal EnableDelayedExpansion
title LG Service Remote - ADB control
REM Needs adb.exe (Android platform-tools) in PATH or in this folder,
REM and an IR-blaster phone with USB debugging ON.
set PKG=com.dxbmr.lgservice
set ACT=%PKG%/.MainActivity
set APK=%~dp0app-debug.apk

where adb >nul 2>nul || (if exist "%~dp0adb.exe" (set "PATH=%~dp0;%PATH%") else (echo adb not found. Put platform-tools in PATH or next to this script.& pause & exit /b 1))

:check
adb start-server >nul
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do if "%%b"=="device" set DEV=%%a
if not defined DEV (echo No phone found. Connect USB, enable USB debugging, accept the prompt.& pause & goto check)
echo Phone: %DEV%
adb shell pm list features | findstr /i consumerir >nul && (echo IR blaster: YES) || (echo IR blaster: NOT FOUND - only network commands will work)
adb shell pm list packages | findstr /i %PKG% >nul || (
  if exist "%APK%" (echo Installing app... & adb install -r "%APK%") else (echo App not installed and %APK% missing.& pause & exit /b 1)
)

:menu
echo.
echo ===== LG 43UK660H - point the phone at the TV =====
echo  1  Open INSTALLER MENU (hold Settings + code)
echo  2  IR In-Start (factory menu)
echo  3  IR EZ-Adjust (service menu)
echo  4  Send a key (OK/BACK/UP/DOWN/LEFT/RIGHT/SETTINGS/HOME/EXIT/POWER/0-9)
echo  5  Type digits
echo  6  NETWORK In-Start (by TV IP)
echo  7  NETWORK EZ-Adjust (by TV IP)
echo  8  Show app log
echo  0  Exit
set /p C=Choice: 
if "%C%"=="1" (set /p CODE=Installer code [1105]: & if "!CODE!"=="" set CODE=1105& call :run --es cmd installer --es code !CODE!)
if "%C%"=="2" call :run --es cmd instart
if "%C%"=="3" call :run --es cmd ezadjust
if "%C%"=="4" (set /p K=Key: & call :run --es cmd key --es key !K!)
if "%C%"=="5" (set /p D=Digits: & call :run --es cmd digits --es code !D!)
if "%C%"=="6" (set /p IP=TV IP: & call :run --es cmd net_instart --es ip !IP!)
if "%C%"=="7" (set /p IP=TV IP: & call :run --es cmd net_ezadjust --es ip !IP!)
if "%C%"=="8" (adb logcat -d -s LGSR:I | more)
if "%C%"=="0" exit /b 0
goto menu

:run
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n %ACT% %*
exit /b
