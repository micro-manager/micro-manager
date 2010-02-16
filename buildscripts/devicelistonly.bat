echo Update the version number in MMStudioMainFrame
set mmversion=""
set YYYYMMDD=""
call buildscripts\setmmversionvariable
call buildscripts\setyyyymmddvariable


pushd  mmStudio\src
call \projects\3rdparty\apache-ant-1.6.5\bin\ant -verbose -buildfile ../build.xml makeDeviceList
popd

