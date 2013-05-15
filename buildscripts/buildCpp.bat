@setlocal
@if "%ECHO_MODE%"=="" set ECHO_MODE=off
@echo %ECHO_MODE%

rem Build the C++ components of Micro-Manager, using Visual Studio 2008
rem See below (:USAGE) for usage.

set ARCH=
if "%1"=="x64" (
    set PLATFORM=x64
    set ARCH=64
    shift
) else if "%1"=="Win32" (
    set PLATFORM=Win32
    set ARCH=32
    shift
)

set CONFIGURATION=Release

set buildswitch=/build
if "%1"=="rebuild" (
    set buildswitch=/rebuild
    shift
)
if "%1"=="REBUILD" (
    set buildswitch=/rebuild
    shift
)

if "%ARCH%"=="" (
    call :USAGE
    goto :EOF
)
if not "%1"=="" (
    call :USAGE
    goto :EOF
)
call :DO_BUILD
goto :EOF


:USAGE
@echo.
@echo Usage: buildCpp.bat PLATFORM [rebuild]
@echo PLATFORM is either 'Win32' or 'x64'.
@echo If 'rebuild' is given, a full rebuild is performed.
goto :EOF


:DO_BUILD
@echo building C++ libraries...

set BOOT_DRIVE=%windir:~0,2%
if "%BOOT_DRIVE%"=="" set BOOT_DRIVE=C:
set VC_PATH=%BOOT_DRIVE%\Program Files (x86)\Microsoft Visual Studio 9.0\VC\
if not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set VC_PATH=%BOOT_DRIVE%\Program Files\Microsoft Visual Studio 9.0\VC\
)
pushd "%VC_PATH%"
call vcvarsall.bat
@echo %ECHO_MODE%
popd

set SRC_ROOT=%~dp0..
pushd %SRC_ROOT%
for %%I in (
    .\MMCore\MMCore.vcproj
    .\MMCoreJ_wrap\MMCoreJ_wrap.sln
    .\MMCorePy_wrap\MMCorePy_wrap.sln
) do (
    @echo building %%I...
    start /wait vcexpress %%I %buildswitch% "%CONFIGURATION%|%PLATFORM%"
)
popd

goto :EOF
