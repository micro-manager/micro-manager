echo Update the version number in MMStudioMainFrame
set mmversion=""
set YYYYMMDD=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable
pushd .\mmstudio\src\org\micromanager
rem for nightly builds we put the version + the date-stamp
if "%1%" == "RELEASE" goto releaseversion
sed -i "s/\"1\.3.*/\"%mmversion%  %YYYYMMDD%\";/"  MMStudioMainFrame.java
goto continuebuild
:releaseversion
sed -i "s/\"1\.3.*/\"%mmversion%\";/"  MMStudioMainFrame.java
:continuebuild
popd

ECHO incremental build of Java components...

cd \projects\micromanager1.3\mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build.xml compileMMStudio buildMMStudio buildMMReader
cd ..\..

cd autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml compileAutofocus buildAutofocus 
cd ..

cd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml  compileMMTracking buildMMTracking 
cd ..\..

cd mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -verbose -buildfile ../build.xml install


ECHO "Done installing"
EXIT /B

