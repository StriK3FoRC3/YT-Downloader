@echo off
setlocal
cd /d "%~dp0.."
if not exist build mkdir build
"%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /warn:4 /warnaserror+ /target:exe /main:YTDownloader.UiSmokeTests /win32manifest:app.manifest /out:build\UiSmokeTests.exe /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll src\Program.cs tests\UiSmokeTests.cs || exit /b 1
build\UiSmokeTests.exe
exit /b %errorlevel%
