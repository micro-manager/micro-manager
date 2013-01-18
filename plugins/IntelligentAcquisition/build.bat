rem Build script for IntelligentAcquisition plugin

rem clean
rmdir /S/Q build
del IntelligentAcquisition.jar

rem compile
md build
javac -sourcepath ./src -d build -source 1.5 -target 1.5 -g ^
-classpath ../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar; ^
./src/org/micromanager/intelligentacquisition/*.java

rem make jars
jar cf IntelligentAcquisition.jar -C ./build .

rem install
copy /Y IntelligentAcquisition.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins 