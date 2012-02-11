rem Build script for ImageFlipper plugin

rem clean
rmdir /S/Q build
del ImageFlipper.jar

rem compile
md build
javac -sourcepath ./src -d build -source 1.5 -target 1.5 -g ^
-classpath ../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar; ^
./src/org/micromanager/imageflipper/*.java

rem make jars
jar cf ImageFlipper.jar -C ./build .

rem install
copy /Y ImageFlipper.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins 