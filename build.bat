@echo off
setlocal
cd /d "%~dp0"
if not exist build mkdir build
if not exist build\Dependencies mkdir build\Dependencies
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" (
  echo C# compiler was not found.
  exit /b 1
)
"%CSC%" /nologo /warn:4 /warnaserror+ /target:winexe /optimize+ /platform:anycpu /win32manifest:app.manifest /win32icon:icon.ico /out:build\YTDownloader.exe /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll src\Program.cs || exit /b 1
if not exist build\Dependencies\yt-dlp.exe copy /y yt-dlp.exe build\Dependencies\ >nul || exit /b 1
if not exist build\Dependencies\ffmpeg.exe copy /y ffmpeg.exe build\Dependencies\ >nul || exit /b 1
if not exist build\Dependencies\ffprobe.exe copy /y ffprobe.exe build\Dependencies\ >nul || exit /b 1
if not exist build\Dependencies\deno.exe copy /y deno.exe build\Dependencies\ >nul || exit /b 1
if not exist build\Downloads mkdir build\Downloads
echo Built build\YTDownloader.exe
exit /b 0
