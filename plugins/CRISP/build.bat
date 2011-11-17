rem Build script for CRISP plugin

rem clean
rmdir /S/Q build
del CRISP.jar

rem compile
md build
javac -sourcepath ./src -d build -source 1.5 -target 1.5 -g ^
-classpath ../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar; ^
./src/org/micromanager/CRISP/*.java

rem make jars
jar cf CRISP.jar -C ./build .

rem install
copy /Y CRISP.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins 