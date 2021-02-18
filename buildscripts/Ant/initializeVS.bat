@echo off
rem
rem   Determines path to VsDevCmd.bat and runs it which initializes the Visual Studio dev console environment
rem   Defaults to 32bit. If the argument to this bat file is "64" then 64bit will be used.
for /f "usebackq delims=#" %%a in (`"%programfiles(x86)%\Microsoft Visual Studio\Installer\vswhere" -latest -property installationPath`) do set VsDevCmd_Path=%%a\Common7\Tools\VsDevCmd.bat

if [%1] equ [64] (
  "%VsDevCmd_Path%" -arch=amd64
) else (
  "%VsDevCmd_Path%"
)

set VsDevCmd_Path=