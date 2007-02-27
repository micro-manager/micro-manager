ECHO building native libraries...
devenv /BUILD Release ./MMCoreJ_wrap/MMCoreJ_wrap.sln

ECHO Building Java components...
cd mmStudio\src
\projects\3rdparty\apache-ant-1.6.5\bin\ant -buildfile ..\build.xml cleanMMStudio buildMMStudio
cd ..\..