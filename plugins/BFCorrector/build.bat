rem Build script for BFCorrector plugin

rem clean
rmdir /S/Q build
del BFCorrector.jar

rem compile
md build
javac -sourcepath ./src -d build -source 1.5 -target 1.5 -g ^
-classpath ../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar; ^
./src/org/micromanager/bfcorrector/*.java

rem make jars
jar cf BFCorrector.jar -C ./build .

rem install
copy /Y BFCorrector.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins 