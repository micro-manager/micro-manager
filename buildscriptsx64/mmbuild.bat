ECHO building native libraries...
pushd
cd "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
popd
 
pushd
cd "\projects\micromanager\"
devenv /BUILD "Release|x64" .\MMCore\MMCore.vcproj
if "%1" == "withpython" call mmbuild-python.bat
devenv /BUILD "Release|x64" .\MMCoreJ_wrap\MMCoreJ_wrap.sln
popd

buildscriptsx64\mmbuild-java.bat
EXIT /B

