[Setup]
OutputDir=\projects\micromanager
OutputBaseFilename=MMDeviceKit-win-x86-x64-Dev45-Mod6a
DefaultDirName=C:/Program Files/MMDeviceKit-win-Dev45
VersionInfoVersion=45
VersionInfoCompany=micro-manager.org
VersionInfoCopyright=University of California San Francisco
AppCopyright=Unviersity of California San Francisco
AppName=Micro-Manager-1.4 DeviceKit
AppVerName=DeviceKit for Device API version 44 Module API version 6
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
Source: ..\DeviceAdapters\Nikon\Nikon.cpp; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.h; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\Nikon.vcproj; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\Nikon\license.txt; DestDir: {app}\DeviceAdapters\Nikon
Source: ..\DeviceAdapters\SimpleAutofocus\*; DestDir: {app}\DeviceAdapters\SimpleAutofocus

; documentation file

Source: ..\doc\DeviceKitDocumentation.url; DestDir: {app}\

; boost stuff, specific to VS2008 for now....
; if they want different binaries, they'll need to build them

Source: ..\..\3rdpartypublic\boost\stage_Win32\lib\libboost_date_time-vc90-mt-1_46_1.lib; DestDir: {app}\lib_Win32
Source: ..\..\3rdpartypublic\boost\stage_Win32\lib\libboost_date_time-vc90-mt-gd-1_46_1.lib; DestDir: {app}\lib_Win32
Source: ..\..\3rdpartypublic\boost\stage_Win32\lib\libboost_thread-vc90-mt-1_46_1.lib; DestDir: {app}\lib_Win32
Source: ..\..\3rdpartypublic\boost\stage_Win32\lib\libboost_thread-vc90-mt-gd-1_46_1.lib; DestDir: {app}\lib_Win32

Source: ..\..\3rdpartypublic\boost\stage_x64\lib\libboost_date_time-vc90-mt-1_46_1.lib; DestDir: {app}\lib_x64
Source: ..\..\3rdpartypublic\boost\stage_x64\lib\libboost_date_time-vc90-mt-gd-1_46_1.lib; DestDir: {app}\lib_x64
Source: ..\..\3rdpartypublic\boost\stage_x64\lib\libboost_thread-vc90-mt-1_46_1.lib; DestDir: {app}\lib_x64
Source: ..\..\3rdpartypublic\boost\stage_x64\lib\libboost_thread-vc90-mt-gd-1_46_1.lib; DestDir: {app}\lib_x64


; test files
Source: ..\lib_Win32\MMCored.lib; DestDir: {app}\lib_Win32
Source: ..\lib_Win32\MMCorer.lib; DestDir: {app}\lib_Win32
Source: ..\lib_x64\MMCored.lib; DestDir: {app}\lib_x64
Source: ..\lib_x64\MMCorer.lib; DestDir: {app}\lib_x64
Source: ..\Test_Programs\ModuleTest\ModuleTest.cpp; DestDir: {app}\Test_Programs\ModuleTest
Source: ..\Test_Programs\ModuleTest\ModuleTest.vcproj; DestDir: {app}\Test_Programs\ModuleTest
Source: ..\Test_Programs\ModuleTest\ModuleTest.sln; DestDir: {app}\Test_Programs\ModuleTest

Source: ..\Test_Programs\MMCoreTest\*; DestDir: {app}\Test_Programs\MMCoreTest
; pickup whatever matching device adapters we've put into the running directory
Source: ..\Test_Programs\MMCoreTest\Win32\Release\*; DestDir: {app}\Test_Programs\MMCoreTest\Win32\Release
Source: ..\Test_Programs\MMCoreTest\x64\Release\*; DestDir: {app}\Test_Programs\MMCoreTest\x64\Release 
Source: ..\Test_Programs\MMCoreTest\Win32\Debug\*; DestDir: {app}\Test_Programs\MMCoreTest\Win32\Debug
Source: ..\Test_Programs\MMCoreTest\x64\Debug\*; DestDir: {app}\Test_Programs\MMCoreTest\x64\Debug

; MMCore files
Source: ..\MMCore\*; DestDir: {app}\MMCore

; scripting files
; confusing to include these with the core_test
;Source: ..\scripts\*; DestDir: {app}\scripts
