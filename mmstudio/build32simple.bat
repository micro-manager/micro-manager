rem Building Micro-Manager 32 bit

rem clean
rmdir /S/Q build
del MMJ_.jar
del MMReader_.jar

rem compile
md build
javac -sourcepath ./src -d build -source 1.5 -target 1.5 -g ^
-classpath ../../3rdpartypublic/classext/*;../MMCoreJ_wrap/MMCoreJ.jar; ^
./src/org/micromanager/utils/AutofocusBase.java

rem copying files
md build\org\micromanager\icons
copy /Y src\org\micromanager\icons\*.png build\org\micromanager\icons\
copy /Y src\org\micromanager\icons\*.gif build\org\micromanager\icons\
copy /Y src\org\micromanager\conf\*.html build\org\micromanager\conf\

rem make jars
copy /Y bin\plugins_mmstudio.config build\plugins.config
jar -cfm MMJ_.jar ./src/MANIFEST.MF -C ./build .
copy /Y bin\plugins_reader.config build\plugins.config
jar -cfm MMReader_.jar ./src/MANIFEST.MF -C ./build .

rem install
del ..\Install_Win32\micro-manager\mmgr_dal_*.dll
del ..\Install_Win32\micro-manager\MMCoreJ_wrap.dll
copy /Y ..\DeviceAdapters\TetheredCam\*.dll ..\Install_Win32\micro-manager\
copy /Y ..\..\3rdpartypublic\classext\data.json.jar ..\Install_Win32\micro-manager\plugins\Micro-Manager\
move /Y ..\Install_Win32\micro-manager\plugins\ij.jar ..\Install_Win32\micro-manager\
copy /Y ..\..\3rdpartypublic\JavaLauncher\ImageJ.exe ..\Install_Win32\micro-manager\
copy /Y MMJ_.jar ..\Install_Win32\micro-manager\
copy /Y MMJ_.jar ..\bin32\plugins\Micro-Manager\
copy /Y ..\scripts\*.bsh ..\Install_Win32\micro-manager\scripts\
copy /Y ..\bin32\mmgr_dal_*.dll ..\Install_Win32\micro-manager\
copy /Y ..\bin32\MMCoreJ_wrap.dll ..\Install_Win32\micro-manager\
copy /Y ..\bin32\MMConfig_demo.cfg ..\Install_Win32\micro-manager\

rem Make device list
java -cp plugins\Micro-Manager\MMJ_.jar;plugins\Micro-Manager\MMCoreJ.jar DeviceListBuilder NotdeviceDiscoveryEnabled
java -cp plugins\Micro-Manager\MMJ_.jar;plugins\Micro-Manager\MMCoreJ.jar DeviceListBuilder deviceDiscoveryEnabled

rem Create Installation Package
pushdir ..\Install_Win32\micro-manager\
..\..\3rdparty\Inno_Setup_5\iscc.exe /F MMSetup_ ../MM-ImageJ-Install32.iss
popdir