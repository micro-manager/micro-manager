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

ECHO incremental build of Java components...
cd \projects\micromanager\mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build.xml compileMMStudio buildMMStudio buildMMReader
cd ..\..

cd autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml compileAutofocus buildAutofocus 
cd ..

cd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml  compileMMTracking buildMMTracking 
cd ..\..

set DEVICELISTBUILDER=1
cd mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -verbose -buildfile ../build.xml install makeDeviceList packInstaller
set DEVICELISTBUILDER=""

pushd \Projects\micromanager\Install\Output
rename MMSetup_.exe  MMSetup_%mmversion%_%YYYYMMDD%.exe
popd

\Projects\micromanager\Install\Output\MMSetup_%mmversion%_%YYYYMMDD%.exe  /silent

ECHO "Done installing"
EXIT /B

