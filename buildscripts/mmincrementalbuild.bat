ECHO building native libraries...
cd "\Program Files\Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
 
cd "\projects\micromanager\"
devenv /BUILD Release .\MMCore\MMCore.vcproj
if "%1" == "withpython" call buildscripts\mmincrementalbuild-python.bat
devenv /BUILD Release .\MMCoreJ_wrap\MMCoreJ_wrap.sln

buildscripts\mmincrementalbuild-java.bat NIGHTLY
EXIT /B

