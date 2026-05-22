@echo off
setlocal
cd /d "%~dp0..\.."
python tools\catalog_editor\catalog_editor.py
if errorlevel 1 (
  echo.
  echo Catalog editor failed. Make sure Python 3 is installed and available as "python".
  pause
)
