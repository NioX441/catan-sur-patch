@echo off
rem Wendet den Patch auf ..\Client\code an (Backup: code.orig).
rem Benoetigt ein JDK ab Version 24 (java + javac im PATH).
rem Erneutes Ausfuehren baut immer vom Original code.orig aus neu.
setlocal
cd /d "%~dp0"
set CLIENT=%~dp0..\Client
set BUILD=%~dp0build

if exist "%BUILD%" rmdir /s /q "%BUILD%"
java src\CatanSavePatch.java "%CLIENT%" --dump "%BUILD%\dec" || goto :fail
javac --release 8 -Xlint:-options -nowarn -cp "%BUILD%\dec" -d "%BUILD%\classes" ^
  src\de\brettspielwelt\client\base\SaveDirFix.java ^
  src\de\brettspielwelt\client\base\BugFix.java ^
  src\de\brettspielwelt\client\games\boards\BoardFix.java ^
  src\de\brettspielwelt\client\tool\ChatFix.java ^
  src\de\brettspielwelt\server\games\SuRFix.java || goto :fail
java src\CatanSavePatch.java "%CLIENT%" "%BUILD%\classes" || goto :fail
rmdir /s /q "%BUILD%\dec"
echo.
echo Patch erfolgreich installiert.
pause
exit /b 0

:fail
echo.
echo FEHLER - am Spiel wurde nichts veraendert, falls kein "Fertig." erschienen ist.
pause
exit /b 1
