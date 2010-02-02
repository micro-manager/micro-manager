[Setup]
OutputDir=C:\projects\micromanager1.3\Install\Output
OutputBaseFilename=MMDeviceKit-win-34-01
DefaultDirName=C:/Program Files/Micro-Manager-1.3/MMDeviceKit-win-34-01
VersionInfoVersion=34
VersionInfoCompany=micro-manager.org
VersionInfoCopyright=University of California San Francisco
AppCopyright=Unviersity of California San Francisco
AppName=Micro-Manager-1.3 DeviceKit
AppVerName=DeviceKit for API version 34
ShowLanguageDialog=yes
DisableDirPage=false

[Files]
; driver files
Source: ..\MMDevice\DeviceBase.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceThreads.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceUtils.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\DeviceUtils.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\ImageMetadata.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\ImgBuffer.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\ImgBuffer.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\license.txt; DestDir: {app}\MMDevice
Source: ..\MMDevice\MMDevice.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\MMDeviceConstants.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\ModuleInterface.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\ModuleInterface.h; DestDir: {app}\MMDevice
Source: ..\MMDevice\Property.cpp; DestDir: {app}\MMDevice
Source: ..\MMDevice\Property.h; DestDir: {app}\MMDevice
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
Source: ..\Test_Programs\Test_MMCore_devkit\Test_MMCore_devkit.cpp; DestDir: {app}\Test_MMCore_devkit
Source: ..\Test_Programs\Test_MMCore_devkit\Test_MMCore_devkit.vcproj; DestDir: {app}\Test_MMCore_devkit
Source: ..\Test_Programs\Test_MMCore_devkit\Test_MMCore_devkit.sln; DestDir: {app}\Test_MMCore_devkit

; MMCore files
Source: ..\MMCore\MMCore.h; DestDir: {app}\MMCore
Source: ..\MMCore\PluginManager.h; DestDir: {app}\MMCore
Source: ..\MMCore\Configuration.h; DestDir: {app}\MMCore
Source: ..\MMCore\Error.h; DestDir: {app}\MMCore
Source: ..\MMCore\ErrorCodes.h; DestDir: {app}\MMCore
Source: ..\MMCore\license.txt; DestDir: {app}\MMCore

; scripting files
Source: ..\scripts\init.bsh; DestDir: {app}\scripts
Source: ..\scripts\camera_test.bsh; DestDir: {app}\scripts
Source: ..\scripts\config_test.bsh; DestDir: {app}\scripts
