ECHO building native libraries...
cd "\Program Files\Microsoft Visual Studio 9.0\VC\"

set include=
call vcvarsall.bat
set include=d:\projects\3rdpartypublic\boost;%include%
 
cd "\projects\micromanager\"
devenv /REBUILD Release .\MMCore\MMCore.vcproj
if "%1" == "withpython" call mmbuild-python.bat
devenv /REBUILD Release .\MMCoreJ_wrap\MMCoreJ_wrap.sln

buildscripts\mmbuild-java.bat
EXIT /B

