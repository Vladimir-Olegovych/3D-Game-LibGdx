@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%"

set "VLC_DIR=%SCRIPT_DIR%vlc"
set "VLC_PLUGIN_PATH=%VLC_DIR%\plugins"

java "-Djna.library.path=%VLC_DIR%" -jar "%SCRIPT_DIR%app-all.jar" %*

popd
endlocal
