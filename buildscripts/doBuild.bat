rem Parameters are:
rem doBuild.bat Win32|x64 FULL|INCREMENTAL RELEASE|NORELEASE UPLOAD|NOUPLOAD

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

IF NOT "%DO_FULL_BUILD%"=="FULL" GOTO UPDATEMMTREE

pushd ..\3rdparty
svn cleanup --non-interactive
svn update --accept postpone --force --ignore-externals --non-interactive
popd
pushd ..\3rdpartypublic
echo update 3rdpartypublic tree from the repository
svn cleanup --non-interactive
rem - been having trouble updating from boost.org, but don't need to do that anyhow for now.
svn update --accept postpone --force --ignore-externals --non-interactive
popd

:UPDATEMMTREE
echo update micromanager tree from the repository
svn cleanup --non-interactive
svn update --accept postpone --non-interactive
pushd SecretDeviceAdapters
svn cleanup --non-interactive
svn update --accept postpone --non-interactive
popd

echo Building native C++ libraries....

echo setup include path for Visual Studio....
set include=
if "%PROCESSOR_ARCHITECTURE%" == "AMD64" goto _x64
pushd "\Program Files\Microsoft Visual Studio 9.0\VC\"
goto setvcvars
:_x64
pushd "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\
:setvcvars
call vcvarsall.bat
popd

set include=d:\projects\3rdpartypublic\boost;%include%

echo include path is:
set include

echo continue working in:
cd
set buildswitch=/build
IF "%DO_FULL_BUILD%"=="FULL" SET buildswitch=/rebuild

echo building core with command:
echo start /wait vcexpress .\MMCore\MMCore.vcproj %buildswitch% "Release|%PLATFORM%"
start /wait vcexpress .\MMCore\MMCore.vcproj %buildswitch% "Release|%PLATFORM%"

echo building python wrapper with command:
echo start /wait vcexpress .\MMCorePy_wrap\MMCorePy_wrap.sln %buildswitch% "Release|%PLATFORM%"
start /wait vcexpress .\MMCorePy_wrap\MMCorePy_wrap.sln %buildswitch% "Release|%PLATFORM%"
copy .\bin_%PLATFORM%\MMCorePy.py .\Install_%PLATFORM%\micro-manager
copy .\bin_%PLATFORM%\_MMCorePy.pyd .\Install_%PLATFORM%\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install_%PLATFORM%\micro-manager


echo building Java wrapper with command:
echo start /wait vcexpress .\MMCoreJ_wrap\MMCoreJ_wrap.sln %buildswitch% "Release|%PLATFORM%"
start /wait vcexpress .\MMCoreJ_wrap\MMCoreJ_wrap.sln %buildswitch% "Release|%PLATFORM%"



echo Update the version number in MMVersion.java
set mmversion=""
set YYYYMMDD=""
set TARGETNAME=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable
pushd .\mmstudio\src\org\micromanager
del MMVersion.java
svn update --non-interactive
rem for nightly builds we put the version + the date-stamp
rem arg2 is either RELEASE OR NIGHTLY
if "%DO_RELEASE_BUILD%" == "RELEASE" goto releaseversion
set TARGETNAME=MMSetup%ARCH%BIT_%mmversion%_%YYYYMMDD%.exe
sed -i "s/\"1\.4.*/\"%mmversion%  %YYYYMMDD%\";/"  MMVersion.java
goto continuebuild
:releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%\";/"  MMVersion.java
set TARGETNAME=MMSetup%ARCH%BIT_%mmversion%.exe
:continuebuild
popd

rem remove any installer package with exactly the same name as the current output
echo trying to delete \Projects\micromanager\Install_%PLATFORM%\Output\MMSetup_.exe
del \Projects\micromanager\Install_%PLATFORM%\Output\MMSetup_.exe
echo trying to delete \Projects\micromanager\Install_%PLATFORM%\Output\%TARGETNAME%
del \Projects\micromanager\Install_%PLATFORM%\Output\%TARGETNAME%

ECHO incremental build of Java components...

set cleantarget=
IF "%DO_FULL_BUILD%"=="FULL" SET cleantarget=clean

PUSHD \projects\micromanager\mmStudio\src
echo building mmStudio with command:
echo call ant -buildfile ../build%ARCH%.xml %cleantarget% compile build buildMMReader
call ant -buildfile ../build%ARCH%.xml %cleantarget% compile build buildMMReader
POPD

rem haven't got to the bottom of this yet, but Pixel Calibrator and Slide Explorer need this jar file there....
copy \projects\micromanager\bin_%PLATFORM%\plugins\Micro-Manager\MMJ_.jar \projects\micromanager\bin_%PLATFORM%\

pushd buildscripts
call buildJars %DO_FULL_BUILD%
popd

pushd mmStudio\src
call ant -buildfile ../build%ARCH%.xml install packInstaller
popd

pushd \Projects\micromanager\Install_%PLATFORM%\Output
rename MMSetup_.exe  %TARGETNAME%
popd

REM -- try to install on build machine
if "%PLATFORM%"=="x64" (
    if not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
        goto CANTINSTALLHERE
    )
)
\Projects\micromanager\Install_%PLATFORM%\Output\%TARGETNAME%  /silent
ECHO "Done installing"
:CANTINSTALLHERE
IF NOT "%DO_UPLOAD%" == "UPLOAD" GOTO FINISH
pscp -i c:\projects\MM.ppk -batch /projects/micromanager/Install_%PLATFORM%/Output/%TARGETNAME% arthur@valelab.ucsf.edu:../MM/public_html/nightlyBuilds/1.4/Windows/%TARGETNAME%
:FINISH

pushd .\mmstudio\src\org\micromanager
del MMVersion.java
svn update --non-interactive
popd

echo %date% - %time%
