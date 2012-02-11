rem Build script for CRISP plugin

rem clean
rmdir /S/Q build
del Gaussian.jar

rem compile
md build
javac -sourcepath ./source -d build -source 1.5 -target 1.5 -g ^
-classpath ../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar; ^
./source/*.java

rem make jars
jar cf Gaussian.jar -C ./build .

rem install
copy /Y Gaussian.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins 