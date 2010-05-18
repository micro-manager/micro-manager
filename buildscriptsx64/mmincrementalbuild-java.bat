echo Update the version number in MMStudioMainFrame
set mmversion=""
set YYYYMMDD=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable
pushd .\mmstudio\src\org\micromanager
rem for nightly builds we put the version + the date-stamp
if "%1%" == "RELEASE" goto releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%  %YYYYMMDD%\";/"  MMStudioMainFrame.java
goto continuebuild
:releaseversion
sed -i "s/\"1\.4.*/\"%mmversion%\";/"  MMStudioMainFrame.java
:continuebuild
popd

rem remove any installer package with exactly the same name as the current output
del \Projects\micromanager\Install\Output\MMSetup_.exe 
del \Projects\micromanager\Install\Output\MMSetup_%mmversion%_%YYYYMMDD%.exe

ECHO building NativeGUI
pushd NativeGUI
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml buildNativeGUI installNativeGUI
popd


ECHO building mmStudio
cd \projects\micromanager\mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build64.xml compileMMStudio buildMMStudio buildMMReader
cd ..\..

ECHO building autofocus
cd autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build64.xml compileAutofocus buildAutofocus 
cd ..

echo building tracker
cd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build64.xml  compileMMTracking buildMMTracking 
cd ..\..

echo building pixelcalibrator
pushd plugins\PixelCalibrator 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build64.xml compileMMPixelCalibrator buildMMPixelCalibrator
popd

rem echo building device list
rem set DEVICELISTBUILDER=1
rem cd mmStudio\src
rem call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build64.xml install makeDeviceList packInstaller
rem set DEVICELISTBUILDER=""

pushd \Projects\micromanager\Install64\Output
rename MMSetup_.exe  MMSetupx64_%mmversion%_%YYYYMMDD%.exe
popd

REM   WON'T WORK ON BUILD MACHINE \Projects\micromanager\Install64\Output\MMSetup_%mmversion%_%YYYYMMDD%.exe  /silent

ECHO "Done installing"
EXIT /B

