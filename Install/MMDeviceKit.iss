[Setup]
OutputDir=C:\projects\micromanager1.2\Install\Output
OutputBaseFilename=MMDeviceKit-win-26-00
DefaultDirName=C:/Program Files/Micro-Manager1.2/MMDeviceKit-win-26_00
VersionInfoVersion=26
VersionInfoCompany=micro-manager.org
VersionInfoCopyright=University of California San Francisco
AppCopyright=Unviersity of California San Francisco
AppName=Micro-Manager1.2 DeviceKit
AppVerName=DeviceKit for API version 26
ShowLanguageDialog=yes
DisableDirPage=true

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
Source: ..\DeviceAdapters\DemoCamera\license.txt; DestDir: {app}\DeviceAdapters\DemoCamera
Source: ..\DeviceAdapters\Sensicam\Sensicam.cpp; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\Sensicam.h; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\Sensicam.vcproj; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\DeviceAdapters\Sensicam\license.txt; DestDir: {app}\DeviceAdapters\Sensicam
Source: ..\doc\MMDeviceKit.doc; DestDir: {app}
Source: ..\DeviceAdapters\Nikon\Nikon.cpp; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.h; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.vcproj; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\license.txt; DestDir: {app}\DeviceAdapters\Nikon

; test files
Source: ..\lib\MMCored.lib; DestDir: {app}\lib
Source: ..\lib\MMCorer.lib; DestDir: {app}\lib
Source: ..\lib\ACE.lib; DestDir: {app}\lib
Source: ..\lib\ACEd.lib; DestDir: {app}\lib
Source: ..\Test_MMCore_devkit\Test_MMCore_devkit.cpp; DestDir: {app}\Test_MMCore_devkit
Source: ..\Test_MMCore_devkit\Test_MMCore_devkit.vcproj; DestDir: {app}\Test_MMCore_devkit
Source: ..\Test_MMCore_devkit\Test_MMCore_devkit.sln; DestDir: {app}\Test_MMCore_devkit
Source: ..\bin\ACE.dll; DestDir: {app}\bin
Source: ..\bin\ACEd.dll; DestDir: {app}\bin

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
