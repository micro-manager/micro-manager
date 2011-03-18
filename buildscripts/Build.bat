echo stop any instances that might already be running.
pskill javaw.exe
pskill java.exe

cd \projects\micromanager
echo working directory is 
cd

IF NOT "%1"=="FULL" GOTO UPDATEMMTREE

pushd ..\3rdparty
svn cleanup --non-interactive
rem - been having trouble updating from boost.org, but don't need to do that anyhow for now.
svn update --force --ignore-externals --non-interactive
popd
pushd ..\3rdpartypublic
echo update 3rdpartypublic tree from the repository
svn cleanup --non-interactive
rem - been having trouble updating from boost.org, but don't need to do that anyhow for now.
svn update --force --ignore-externals --non-interactive
popd

:UPDATEMMTREE
echo update micromanager tree from the repository
svn cleanup --non-interactive
svn update --non-interactive
pushd SecretDeviceAdapters
svn cleanup --non-interactive
svn update --non-interactive
popd

echo Building native C++ libraries....

echo setup include path for Vizual Studio....
pushd
c:
set include=
if exist "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\" goto probably_x64
pushd "\Program Files\Microsoft Visual Studio 9.0\VC\"
goto setvcvars
:probably_x64
pushd "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\
:setvcvars
call vcvarsall.bat
popd 
popd


set include=d:\projects\3rdpartypublic\boost;%include%

echo include path is:
set include

echo continue working in:
cd
set buildswitch=BUILD
IF "%1%"=="FULL" SET buildswitch=REBUILD

echo building core with command:
echo devenv /%buildswitch% "Release|Win32" .\MMCore\MMCore.vcproj
devenv /%buildswitch% "Release|Win32" .\MMCore\MMCore.vcproj

echo building python wrapper with command:
echo devenv /%buildswitch% Release .\MMCorePy_wrap\MMCorePy_wrap.sln
devenv /%buildswitch% Release .\MMCorePy_wrap\MMCorePy_wrap.sln
copy .\bin_Win32\MMCorePy.py .\Install_Win32\micro-manager
copy .\bin_Win32\_MMCorePy.pyd .\Install_Win32\micro-manager
copy .\MMCorePy_wrap\MMCoreWrapDemo.py .\Install_Win32\micro-manager

echo building Java wrapper with command:
echo devenv /%buildswitch% "Release|Win32" .\MMCoreJ_wrap\MMCoreJ_wrap.sln
devenv /%buildswitch% "Release|Win32" .\MMCoreJ_wrap\MMCoreJ_wrap.sln


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
set TARGETNAME=MMSetup32BIT_%mmversion%_%YYYYMMDD%.exe
sed -i "s/\"1\.4.*/\"%mmversion%  %YYYYMMDD%\";/"  MMStudioMainFrame.java
goto continuebuild
:releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%\";/"  MMStudioMainFrame.java
set TARGETNAME=MMSetup32BIT_%mmversion%.exe
:continuebuild
popd

rem remove any installer package with exactly the same name as the current output
echo trying to delete \Projects\micromanager\Install_Win32\Output\MMSetup_.exe 
del \Projects\micromanager\Install_Win32\Output\MMSetup_.exe 
echo trying to delete \Projects\micromanager\Install_Win32\Output\%TARGETNAME%
del \Projects\micromanager\Install_Win32\Output\%TARGETNAME%

ECHO incremental build of Java components...

set cleantarget=
IF "%1%"=="FULL" SET cleantarget=clean

PUSHD \projects\micromanager\mmStudio\src
echo building mmStudio with command:
echo call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build32.xml %cleantarget% compile build buildMMReader
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build32.xml %cleantarget% compile build buildMMReader
POPD

rem haven't got to the bottom of this yet, but Pixel Calibrator and Slide Explorer need this jar file there....
copy \projects\micromanager\bin_Win32\plugins\Micro-Manager\MMJ_.jar \projects\micromanager\bin_Win32\

PUSHD acqEngine
call build.bat
POPD

PUSHD autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build32.xml %cleantarget% compile build
POPD

pushd plugins\Bleach
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

echo building pixelcalibrator
pushd plugins\PixelCalibrator 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Projector
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Recall
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\SlideExplorer
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\StageControl
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build
popd

pushd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build 
popd

pushd plugins\Big
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml %cleantarget% compile build 
popd



set DEVICELISTBUILDER=1
cd mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build32.xml install makeDeviceList packInstaller
set DEVICELISTBUILDER=""

pushd \Projects\micromanager\Install_Win32\Output
rename MMSetup_.exe  %TARGETNAME%
popd

\Projects\micromanager\Install_Win32\Output\%TARGETNAME%  /silent

ECHO "Done installing"
pscp -i c:\projects\MM.ppk -batch /projects/micromanager/Install_Win32/Output/%TARGETNAME% MM@valelab.ucsf.edu:./public_html/nightlyBuilds/1.4/Windows/%TARGETNAME%
