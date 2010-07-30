ECHO building native libraries...

set include=

rem cd "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\"
pushd "c:\Program Files \Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
popd 


set include=d:\projects\3rdpartypublic\boost;%include%

set include

pushd "\projects\micromanager\"
devenv /BUILD "Release|Win32" .\MMCore\MMCore.vcproj
if "%1" == "withpython" call buildscripts\mmincrementalbuild-python.bat
devenv /BUILD "Release|Win32" .\MMCoreJ_wrap\MMCoreJ_wrap.sln
popd


buildscripts\mmincrementalbuild-java.bat NIGHTLY
EXIT /B

