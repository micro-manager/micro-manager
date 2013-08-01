@rem We reset the echo mode after any "call" below to keep it consistent.
@set ECHO_MODE=on
@echo %ECHO_MODE%

rem This is an experimental build script that is less dependent on absolute paths
rem than the nightly build script (doBuild.bat).
rem It is a work in progress and may be replaced with an Ant build file in the future.
rem As it stands, it should do the same thing as doBuild.bat except for things that
rem are specific to the nightly build (including setting the version number).

rem Parameters are:
rem build_all.bat Win32|x64 FULL|INCREMENTAL

if "%1"=="x64" (
    set PLATFORM=x64
    set ARCH=64
) else (
    set PLATFORM=Win32
    set ARCH=32
)
set DO_FULL_BUILD=%2

cd /d %~dp0\..
echo working directory is
cd

if "%DO_FULL_BUILD%"=="FULL" (
    call buildscripts\buildCpp.bat %PLATFORM% REBUILD
    @echo %ECHO_MODE%
) else (
    call buildscripts\buildCpp.bat %PLATFORM%
    @echo %ECHO_MODE%
)

rem MMCorePy needs to be manually staged
rem (MMCoreJ is staged by the Java build process)
copy .\bin_%PLATFORM%\MMCorePy.py .\Install_%PLATFORM%\micro-manager
copy .\bin_%PLATFORM%\_MMCorePy.pyd .\Install_%PLATFORM%\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install_%PLATFORM%\micro-manager

set cleantarget=
if "%DO_FULL_BUILD%"=="FULL" SET cleantarget=clean

pushd mmStudio\src
call ant -f ../build%ARCH%.xml %cleantarget% compile build buildMMReader
@echo %ECHO_MODE%
popd

pushd buildscripts
call buildJars %DO_FULL_BUILD%
@echo %ECHO_MODE%
popd

pushd mmStudio\src
call ant -f ../build%ARCH%.xml install packInstaller
@echo %ECHO_MODE%
popd
