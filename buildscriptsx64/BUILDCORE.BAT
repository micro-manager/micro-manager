ECHO building CORE
PUSHD "\Program Files (x86)\Microsoft Visual Studio 9.0\VC\"
call vcvarsall.bat
POPD
 
PUSHD "\projects\micromanager\"
devenv /BUILD "Release|x64" .\MMCore\MMCore.vcproj
POPD

EXIT /B

