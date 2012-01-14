echo %date% - %time%

echo stop any instances that might already be running.
pskill javaw.exe
pskill java.exe

cd /d %~dp0\..
echo working directory is 
cd

IF NOT "%1"=="FULL" GOTO UPDATEMMTREE

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

echo setup include path for Vizual Studio....
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
IF "%1%"=="FULL" SET buildswitch=/rebuild

echo building core with command:
echo start /wait vcexpress .\MMCore\MMCore.vcproj %buildswitch% "Release|x64"
start /wait vcexpress .\MMCore\MMCore.vcproj %buildswitch% "Release|x64"

echo building python wrapper with command:
echo start /wait vcexpress .\MMCorePy_wrap\MMCorePy_wrap.sln %buildswitch% "Release|x64"
start /wait vcexpress .\MMCorePy_wrap\MMCorePy_wrap.sln %buildswitch% "Release|x64"
copy .\bin_x64\MMCorePy.py .\Install_x64\micro-manager
copy .\bin_x64\_MMCorePy.pyd .\Install_x64\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install_x64\micro-manager

echo building Java wrapper with command:
echo start /wait vcexpress .\MMCoreJ_wrap\MMCoreJ_wrap.sln %buildswitch% "Release|x64"
start /wait vcexpress .\MMCoreJ_wrap\MMCoreJ_wrap.sln %buildswitch% "Release|x64"


echo Update the version number in MMStudioMainFrame
set mmversion=""
set YYYYMMDD=""
set TARGETNAME=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable
pushd .\mmstudio\src\org\micromanager
rem for nightly builds we put the version + the date-stamp
rem arg2 is either RELEASE OR NIGHTLY
if "%2%" == "RELEASE" goto releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%  %YYYYMMDD%\";/"  MMStudioMainFrame.java
set TARGETNAME=MMSetup64BIT_%mmversion%_%YYYYMMDD%.exe
goto continuebuild
:releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%\";/"  MMStudioMainFrame.java
set TARGETNAME=MMSetup64BIT_%mmversion%.exe
:continuebuild
popd

rem remove any installer package with exactly the same name as the current output
echo trying to delete \Projects\micromanager\Install_x64\Output\MMSetup_.exe 
del \Projects\micromanager\Install_x64\Output\MMSetup_.exe 
echo trying to delete \Projects\micromanager\Install_x64\Output\%TARGETNAME%
del \Projects\micromanager\Install_x64\Output\%TARGETNAME%

ECHO incremental build of Java components...

set cleantarget=
IF "%1%"=="FULL" SET cleantarget=clean

PUSHD \projects\micromanager\mmStudio\src
echo building mmStudio with command:
echo call ant -buildfile ../build64.xml %cleantarget% compile build buildMMReader
call ant -buildfile ../build64.xml %cleantarget% compile build buildMMReader
POPD

rem haven't got to the bottom of this yet, but Pixel Calibrator and Slide Explorer need this jar file there....
copy \projects\micromanager\bin_x64\plugins\Micro-Manager\MMJ_.jar \projects\micromanager\bin_x64\

pushd buildscripts
call buildJars %1
popd

set DEVICELISTBUILDER=1
pushd mmStudio\src
if "%PROCESSOR_ARCHITECTURE%" == "AMD64" GOTO AMD64BUILDMACHINE
call ant -buildfile ../build64.xml install packInstaller
GOTO NOTAMD64
:AMD64BUILDMACHINE
call ant -buildfile ../build64.xml install makeDeviceList packInstaller
:NOTAMD64
popd
set DEVICELISTBUILDER=""

pushd \Projects\micromanager\Install_x64\Output
rename MMSetup_.exe  %TARGETNAME%
popd

REM -- try to install on build machine
if "%PROCESSOR_ARCHITECTURE%" == "AMD64" GOTO AMD64BUILDMACHINE2
GOTO CANTINSTALLHERE
:AMD64BUILDMACHINE2
\Projects\micromanager\Install_x64\Output\%TARGETNAME% /silent
ECHO "Done installing"
:CANTINSTALLHERE

IF NOT "%3%" == "UPLOAD" GOTO FINISH
pscp -i c:\projects\MM.ppk -batch /projects/micromanager/Install_x64/Output/%TARGETNAME% arthur@valelab.ucsf.edu:../MM/public_html/nightlyBuilds/1.4/Windows/%TARGETNAME%
:FINISH

echo %date% - %time%
