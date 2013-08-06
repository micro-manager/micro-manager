@rem We reset the echo mode after any "call" below to keep it consistent.
@set ECHO_MODE=on
@echo %ECHO_MODE%

rem Build script for official nightly and release builds.
rem Sorry, won't work for anybody else (easily, at least).

rem Parameters are:
rem nightly_build.bat Win32|x64 FULL|INCREMENTAL RELEASE|NIGHTLY UPLOAD|NOUPLOAD

echo %date% - %time%

if "%1"=="x64" (
    set PLATFORM=x64
    set ARCH=64
) else (
    set PLATFORM=Win32
    set ARCH=32
)
set DO_FULL_BUILD=%2
set DO_RELEASE_BUILD=%3
set DO_UPLOAD=%4

echo stop any instances that might already be running.
pskill javaw.exe
pskill java.exe

cd /d %~dp0\..
echo working directory is
cd

if "%DO_FULL_BUILD%"=="FULL" (
    pushd ..\3rdparty
    svn cleanup --non-interactive
    svn update --accept postpone --force --ignore-externals --non-interactive
    popd

    pushd ..\3rdpartypublic
    echo update 3rdpartypublic tree from the repository
    svn cleanup --non-interactive
    svn update --accept postpone --force --ignore-externals --non-interactive
    popd
)

echo update micromanager tree from the repository
svn cleanup --non-interactive
svn update --accept postpone --non-interactive
pushd SecretDeviceAdapters
svn cleanup --non-interactive
svn update --accept postpone --non-interactive
popd

echo Update the version number in MMVersion.java
set mmversion=""
set YYYYMMDD=""
set TARGETNAME=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable
@echo %ECHO_MODE%
pushd .\mmstudio\src\org\micromanager
del MMVersion.java
svn update --non-interactive
rem for nightly builds we put the version + the date-stamp
if "%DO_RELEASE_BUILD%"=="RELEASE" (
    set TARGETNAME=MMSetup%ARCH%BIT_%mmversion%.exe
    sed -i "s/\"1\.4.*/\"%mmversion%\";/"  MMVersion.java
) else (
    set TARGETNAME=MMSetup%ARCH%BIT_%mmversion%_%YYYYMMDD%.exe
    sed -i "s/\"1\.4.*/\"%mmversion%  %YYYYMMDD%\";/"  MMVersion.java
)
popd

rem remove any installer package with exactly the same name as the current output
del \Projects\micromanager\Install_%PLATFORM%\Output\MMSetup_.exe
del \Projects\micromanager\Install_%PLATFORM%\Output\%TARGETNAME%

call buildscripts\build_all.bat %PLATFORM% %DO_FULL_BUILD%
@echo %ECHO_MODE%
pushd Install_%PLATFORM%\Output
rename MMSetup_.exe %TARGETNAME%
popd

rem -- try to install on build machine
set DO_INSTALL=YES
if "%PLATFORM%"=="x64" (
    if not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
        set DO_INSTALL=NO
    )
)
if "%DO_INSTALL%"=="YES" (
    Install_%PLATFORM%\Output\%TARGETNAME%  /silent
    ECHO "Done installing"
)

if "%DO_UPLOAD%"=="UPLOAD" (
    pscp -i c:\projects\MM.ppk -batch Install_%PLATFORM%/Output/%TARGETNAME% arthur@valelab.ucsf.edu:../MM/public_html/nightlyBuilds/1.4/Windows/%TARGETNAME%
)

pushd .\mmstudio\src\org\micromanager
del MMVersion.java
svn update --non-interactive
popd

echo %date% - %time%
