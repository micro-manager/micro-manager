[Setup]
OutputDir=C:\projects\MicroManage\Install\Output
OutputBaseFilename=MMDeviceKit-win-xx
DefaultDirName=C:/Program Files/Micro-Manager/MMDeviceKit-win-14_03
VersionInfoVersion=14
VersionInfoCompany=micro-manager.org
VersionInfoCopyright=University of California San Francisco
AppCopyright=Unviersity of California San Francisco
AppName=Micro-Manager DeviceKit
AppVerName=DeviceKit for API version 14
ShowLanguageDialog=yes

[Files]
; driver files
Source: ..\MMDevice\Property.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceBase.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceUtils.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceUtils.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\ImgBuffer.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\ImgBuffer.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\license.txt; DestDir: {app}\MMDevice
Source: ..\MMDevice\MMDevice.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\MMDeviceConstants.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\ModuleInterface.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\ModuleInterface.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\Property.cpp; DestDir: {app}\MMDevice
Source: ..\DeviceAdapters\DemoCamera\DemoCamera.cpp; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\DemoCamera\DemoCamera.h; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\DemoCamera\DemoCamera.vcproj; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\DemoCamera\DemoCamera_vc8.vcproj; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\DemoCamera\license.txt; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\Sensicam\Sensicam.cpp; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\Sensicam.h; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\Sensicam.vcproj; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\Sensicam_vc8.vcproj; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\license.txt; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\doc\MMDeviceKit.doc; DestDir: {app}
Source: ..\DeviceAdapters\Nikon\Nikon.cpp; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.h; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.vcproj; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon_vc8.vcproj; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\license.txt; DestDir: {app}\DeviceAdapters\Nikon

; test files
Source: ..\lib\MMCored.lib; DestDir: {app}\lib
Source: ..\lib\MMCorer.lib; DestDir: {app}\lib
Source: ..\lib\MMCored_vc8.lib; DestDir: {app}\lib
Source: ..\lib\MMCorer_vc8.lib; DestDir: {app}\lib
Source: ..\lib\ACEs.lib; DestDir: {app}\lib
Source: ..\lib\ACEsd.lib; DestDir: {app}\lib
Source: ..\Test_MMCore\Test_MMCore.cpp; DestDir: {app}\Test_MMCore
Source: ..\Test_MMCore\Test_MMCore.vcproj; DestDir: {app}\Test_MMCore
Source: ..\Test_MMCore\Test_MMCore.sln; DestDir: {app}\Test_MMCore
Source: ..\Test_MMCore\Test_MMCore_vc8.vcproj; DestDir: {app}\Test_MMCore
Source: ..\Test_MMCore\Test_MMCore_vc8.sln; DestDir: {app}\Test_MMCore
Source: ..\bin\MMConfig_Demo.cfg; DestDir: {app}\bin

; MMCore files
Source: ..\MMCore\MMCore.h; DestDir: {app}\MMCore
Source: ..\MMCore\PluginManager.h; DestDir: {app}\MMCore
Source: ..\MMCore\Error.h; DestDir: {app}\MMCore
Source: ..\MMCore\ErrorCodes.h; DestDir: {app}\MMCore
Source: ..\MMCore\license.txt; DestDir: {app}\MMCore

; scripting files
Source: ..\scripts\init.bsh; DestDir: {app}\scripts
Source: ..\scripts\camera_test.bsh; DestDir: {app}\scripts
Source: ..\scripts\config_test.bsh; DestDir: {app}\scripts
