ECHO building native libraries...

rem cd "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\"
pushd "\Program Files \Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
popd 

pushd "\projects\micromanager\"
devenv /BUILD "Release|x64" .\MMCore\MMCore.vcproj
if "%1" == "withpython" call buildscriptsx64\mmincrementalbuild-python.bat
devenv /BUILD "Release|x64" .\MMCoreJ_wrap\MMCoreJ_wrap_x64.sln
popd


buildscriptsx64\mmincrementalbuild-java.bat NIGHTLY
EXIT /B

