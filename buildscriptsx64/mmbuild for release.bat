ECHO building native libraries...
cd "\Program Files\Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
 
cd "\projects\micromanager\"
devenv /REBUILD Release .\MMCore\MMCore.vcproj
if "%1" == "withpython" call mmbuild-python.bat
devenv /REBUILD Release .\MMCoreJ_wrap\MMCoreJ_wrap.sln

buildscripts\mmbuild-java.bat RELEASE
EXIT /B

