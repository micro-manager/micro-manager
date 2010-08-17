ECHO Building Java components...
cd \projects\micromanager

pushd mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build.xml cleanMMStudio compileMMStudio buildMMStudio buildMMReader
popd

pushd autofocus
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml cleanAutofocus compileAutofocus buildAutofocus 
popd


pushd plugins\Tracker 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml cleanMMTracking compileMMTracking buildMMTracking 
popd

pushd plugins\PixelCalibrator 
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile build.xml cleanMMPixelCalibrator compileMMPixelCalibrator buildMMPixelCalibrator
popd


set DEVICELISTBUILDER=1
pushd  mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ../build.xml install makeDeviceList packInstaller
set DEVICELISTBUILDER=""
popd
