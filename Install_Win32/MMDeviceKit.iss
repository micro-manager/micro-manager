[Setup]
OutputDir=C:\projects\micromanager
OutputBaseFilename=MMDeviceKit-win-38
DefaultDirName=C:/Program Files/Micro-Manager1.4/MMDeviceKit-win-38
VersionInfoVersion=38
VersionInfoCompany=micro-manager.org
VersionInfoCopyright=University of California San Francisco
AppCopyright=Unviersity of California San Francisco
AppName=Micro-Manager-1.4 DeviceKit
AppVerName=DeviceKit for API version 38
ShowLanguageDialog=yes
DisableDirPage=false

[Dirs]
Name:   "{app}\bin_Win32"
Name:   "{app}\bin_x64"

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
Source: ..\DeviceAdapters\DemoCamera\WriteCompactTiffRGB.h; DestDir: {app}\DeviceAdapters\DemoCamera
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
Source: ..\lib_Win32\MMCored.lib; DestDir: {app}\lib_Win32
Source: ..\lib_Win32\MMCorer.lib; DestDir: {app}\lib_Win32
Source: ..\lib_x64\MMCored.lib; DestDir: {app}\lib_x64
Source: ..\lib_x64\MMCorer.lib; DestDir: {app}\lib_x64
Source: ..\Test_Programs\ModuleTest\ModuleTest.cpp; DestDir: {app}\Test_Programs\ModuleTest
Source: ..\Test_Programs\ModuleTest\ModuleTest.vcproj; DestDir: {app}\Test_Programs\ModuleTest
Source: ..\Test_Programs\ModuleTest\ModuleTest.sln; DestDir: {app}\Test_Programs\ModuleTest

; MMCore files
Source: ..\MMCore\MMCore.h; DestDir: {app}\MMCore
Source: ..\MMCore\PluginManager.h; DestDir: {app}\MMCore
Source: ..\MMCore\PluginManager.cpp; DestDir: {app}\MMCore

Source: ..\MMCore\Configuration.h; DestDir: {app}\MMCore
Source: ..\MMCore\Error.h; DestDir: {app}\MMCore
Source: ..\MMCore\ErrorCodes.h; DestDir: {app}\MMCore
Source: ..\MMCore\license.txt; DestDir: {app}\MMCore

; scripting files
Source: ..\scripts\init.bsh; DestDir: {app}\scripts
Source: ..\scripts\camera_test.bsh; DestDir: {app}\scripts
Source: ..\scripts\config_test.bsh; DestDir: {app}\scripts
