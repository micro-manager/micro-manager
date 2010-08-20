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
del \Projects\micromanager\Install_Win32\Output\MMSetup_.exe 
del \Projects\micromanager\Install_Win32\Output\MMSetupx86_%mmversion%_%YYYYMMDD%.exe

ECHO Building Java components...
pushd mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build32.xml cleanMMStudio compileMMStudio buildMMStudio buildMMReader
popd

rem haven't got to the bottom of this yet, but Pixel Calibrator and Slide Explorer need this jar file there....
copy \projects\micromanager\bin_Win32\plugins\Micro-Manager\MMJ_.jar \projects\micromanager\bin_Win32\

pushd autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build32.xml cleanAutofocus compileAutofocus buildAutofocus 
popd


pushd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build32.xml cleanMMTracking compileMMTracking buildMMTracking 
popd

pushd plugins\PixelCalibrator 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build32.xml cleanMMPixelCalibrator compileMMPixelCalibrator buildMMPixelCalibrator
popd

pushd plugins\SlideExplorer 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml cleanMMSlideExplorer compileMMSlideExplorer buildMMSlideExplorer
popd


set DEVICELISTBUILDER=1
pushd  mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build32.xml install makeDeviceList packInstaller
set DEVICELISTBUILDER=""
popd


pushd \Projects\micromanager\Install_Win32\Output
rename MMSetup_.exe  MMSetup_%mmversion%_%YYYYMMDD%.exe
popd

\Projects\micromanager\Install_Win32\Output\MMSetup_%mmversion%_%YYYYMMDD%.exe  /silent

ECHO "Done"
EXIT /B

