#if MMArch == "x64"
#  define MMArch_x86amd64 "amd64"
#  define MMArch_x86x64 "x64"
#  define MMArch_bits "64"
#elif MMArch == "Win32"
#  define MMArch_x86amd64 "x86"
#  define MMArch_x86x64 "x86"
#  define MMArch_bits "32"
#else
#  error MMArch must be set to "x64" or "Win32" before including this file
#endif

[Setup]
AppName=Micro-Manager-1.4
AppVerName=Micro-Manager-1.4
AppPublisher=UCSF
AppPublisherURL=http://www.micro-manager.org
AppSupportURL=http://www.micro-manager.org
AppUpdatesURL=http://www.micro-manager.org
DefaultDirName=C:/Program Files/Micro-Manager-1.4
DefaultGroupName=Micro-Manager-1.4
OutputBaseFilename=MMSetup_{#MMArch_bits}bit
Compression=lzma
SolidCompression=true
VersionInfoVersion=1.4
VersionInfoCompany=(c)University of California San Francisco
VersionInfoCopyright=(c)University of California San Francisco, (c)100XImaging Inc
AppCopyright=University of California San Francisco, 100XImaging Inc
ShowLanguageDialog=yes
AppVersion=1.4
AppID=31830087-F23D-4198-B67D-AD4A2A69147F
#if MMArch == "x64"
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
#endif

[Languages]
Name: eng; MessagesFile: compiler:Default.isl

[Tasks]
Name: desktopicon; Description: {cm:CreateDesktopIcon}; GroupDescription: {cm:AdditionalIcons}; Flags: unchecked

[Files]
; the entire redistributable set
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.ATL\atl90.dll ; DestDir: {app}\Microsoft.VC90.ATL; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.ATL\Microsoft.VC90.ATL.manifest ; DestDir: {app}\Microsoft.VC90.ATL; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.CRT\msvcm90.dll ; DestDir: {app}\Microsoft.VC90.CRT; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.CRT\msvcp90.dll ; DestDir: {app}\Microsoft.VC90.CRT; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.CRT\msvcr90.dll ; DestDir: {app}\Microsoft.VC90.CRT; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.CRT\Microsoft.VC90.CRT.manifest ; DestDir: {app}\Microsoft.VC90.CRT; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFC\mfc90.dll ; DestDir: {app}\Microsoft.VC90.MFC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFC\mfc90u.dll ; DestDir: {app}\Microsoft.VC90.MFC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFC\mfcm90.dll ; DestDir: {app}\Microsoft.VC90.MFC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFC\mfcm90u.dll ; DestDir: {app}\Microsoft.VC90.MFC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFC\Microsoft.VC90.MFC.manifest ; DestDir: {app}\Microsoft.VC90.MFC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90CHS.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90CHT.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90DEU.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90ENU.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90ESN.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90ESP.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90FRA.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90ITA.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90JPN.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\MFC90KOR.dll ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.MFCLOC\Microsoft.VC90.MFCLOC.manifest ; DestDir: {app}\Microsoft.VC90.MFCLOC; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.OPENMP\vcomp90.dll ; DestDir: {app}\Microsoft.VC90.OPENMP; Flags: ignoreversion
Source: ..\..\3rdparty\Microsoft\VisualC++\lib\{#MMArch_x86amd64}\Microsoft.VC90.OPENMP\Microsoft.VC90.OpenMP.manifest ; DestDir: {app}\Microsoft.VC90.OPENMP; Flags: ignoreversion


#if MMArch == "x64"
Source: ..\..\3rdparty\jre\* ; DestDir: {app}\jre; Flags: ignoreversion recursesubdirs createallsubdirs
#else
Source: ..\..\3rdparty\jre6_32\* ; DestDir: {app}\jre; Flags: ignoreversion recursesubdirs createallsubdirs
#endif


; Vendor DLLs
; Please keep in ASCII lexical order!
; TODO Include these from a separate file
#if MMArch == "x64"
Source: ..\..\3rdpartypublic\hidapi\hidapi-0.7.0\windows\x64\Release\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\drivers\K8061\amd64\libusb0.dll; DestDir: {app}; DestName: libusb0.dll; Flags: ignoreversion


Source: ..\stage\Release\x64\FxLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\LaserCombinerSDK64.dll; DestDir: {app}; Flags: ignoreversion


Source: ..\stage\Release\x64\PCO_Kamlib64.dll; DestDir: {app}; Flags: ignoreversion


Source: ..\stage\Release\x64\SysInfo.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\XCLIBW64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\atmcd64d.dll; DestDir: {app}; Flags: ignoreversion

Source: ..\stage\Release\x64\hrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\inpoutx64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mcam64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mcammr64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mrc564.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
#else
Source: ..\..\3rdpartypublic\hidapi\hidapi-0.7.0\windows\Win32\Release\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\drivers\K8061\x86\libusb0_x86.dll; DestDir: {app}; DestName: libusb0.dll; Flags: ignoreversion
Source: ..\stage\Release\Win32\DSLRRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\FireCamJ.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\FxLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\LaserCombinerSDK.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\MexJCam.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\NKRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\PCO_Kamlib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\PSRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\ProcessLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\SysInfo.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\XCLIBWNT.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\atmcd32d.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\camconj.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\hrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\inpout32.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mcam.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mcammr.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mrc5.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\usb_main.bin; DestDir: {app}; Flags: ignoreversion
#endif

Source: ..\stage\Release\{#MMArch}\MMCoreJ_wrap.dll; DestDir: {app}; Flags: ignoreversion

; device adapters
Source: ..\stage\Release\{#MMArch}\mmgr_dal_*.dll; DestDir: {app}; Flags: ignoreversion

; python wrapper
Source: ..\stage\Release\{#MMArch}\_MMCorePy.pyd; DestDir: {app}; Flags: ignoreversion skipifsourcedoesntexist
Source: ..\stage\Release\{#MMArch}\MMCorePy.py; DestDir: {app}; Flags: ignoreversion skipifsourcedoesntexist
Source: ..\stage\Release\{#MMArch}\MMCoreWrapDemo.py; DestDir: {app}; Flags: ignoreversion skipifsourcedoesntexist

; beanshell scripts
Source: ..\scripts\*; DestDir: {app}\scripts; Flags: ignoreversion

; configuration files
Source: ..\stage\Release\{#MMArch}\MMConfig_demo.cfg; DestDir: {app}; Flags: ignoreversion

; ImageJ files
Source: ..\stage\Release\{#MMArch}\ImageJ.exe; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\ImageJ.cfg; DestDir: {app}; Flags: onlyifdoesntexist; Permissions: users-modify
Source: ..\..\3rdpartypublic\classext\ij.jar; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\IJ_Prefs.txt; DestDir: {app}; Flags: onlyifdoesntexist
Source: ..\stage\Release\{#MMArch}\macros\*; DestDir: {app}\macros; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\plugins\*; DestDir: {app}\plugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\mmplugins\*; DestDir: {app}\mmplugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\mmautofocus\*; DestDir: {app}\mmautofocus; Flags: ignoreversion recursesubdirs createallsubdirs

; NOTE: Don't use "Flags: ignoreversion" on any shared system files

[DIRS]
Name: "{app}"; Permissions: users-modify
; TODO Test if subdir permissions really need to be set here.
Name: "{app}\macros"; Permissions: users-modify
Name: "{app}\plugins"; Permissions: users-modify
Name: "{app}\mmplugins"; Permissions: users-modify
Name: "{app}\mmautofocus"; Permissions: users-modify

[Icons]
Name: {group}\Micro-Manager-1.4; Filename: {app}\ImageJ.exe; WorkingDir: {app}
Name: {group}\{cm:UninstallProgram,Micro-Manager-1.4}; Filename: {uninstallexe}
Name: {commondesktop}\Micro-Manager 1.4; Filename: {app}\ImageJ.exe; Tasks: desktopicon; WorkingDir: {app}; IconIndex: 0

[Run]
Filename: "{app}\ImageJ.exe"; WorkingDir: "{app}"; Description: {cm:LaunchProgram,Micro-Manager-1.4}; Flags: nowait postinstall
