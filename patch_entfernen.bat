@echo off
rem Stellt das originale Klassenarchiv wieder her. Profile und Spielstaende bleiben erhalten.
setlocal
set CLIENT=%~dp0..\Client
if not exist "%CLIENT%\code.orig" (
  echo Kein Backup code.orig gefunden - Patch ist wohl nicht installiert.
  pause
  exit /b 1
)
copy /y "%CLIENT%\code.orig" "%CLIENT%\code" >nul && del "%CLIENT%\code.orig"
echo Original wiederhergestellt.
pause
