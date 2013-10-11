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


[InstallDelete]
; Remove VC++ 2008 Redistributable Package installed by previous versions
Type: filesandordirs; Name: {app}\Microsoft.VC90.ATL
Type: filesandordirs; Name: {app}\Microsoft.VC90.CRT
Type: filesandordirs; Name: {app}\Microsoft.VC90.MFC
Type: filesandordirs; Name: {app}\Microsoft.VC90.MFCLOC
Type: filesandordirs; Name: {app}\Microsoft.VC90.OPENMP

[Files]
Source: "..\..\3rdparty\Microsoft\vcredist\2008SP1\vcredist_{#MMArch_x86x64}.exe"; DestDir: "{app}"; DestName: "vcredist_{#MMArch_x86x64}_2008SP1.exe"; Flags: deleteafterinstall
Source: "..\..\3rdparty\Microsoft\vcredist\2010SP1\vcredist_{#MMArch_x86x64}.exe"; DestDir: "{app}"; DestName: "vcredist_{#MMArch_x86x64}_2010SP1.exe"; Flags: deleteafterinstall

[Run]
Filename: "{app}\vcredist_{#MMArch_x86x64}_2008SP1.exe"; Parameters: "/q"; Description: "Microsoft Visual C++ 2008 SP1 Redistributable Package"; StatusMsg: "Installing Microsoft Visual C++ 2008 SP1 Redistributable Package"
Filename: "{app}\vcredist_{#MMArch_x86x64}_2010SP1.exe"; Parameters: "/q"; Description: "Microsoft Visual C++ 2010 SP1 Redistributable Package"; StatusMsg: "Installing Microsoft Visual C++ 2010 SP1 Redistributable Package"


[Files]

; Java Runtime
Source: ..\stage\Release\{#MMArch}\jre\*; DestDir: {app}\jre; Flags: ignoreversion recursesubdirs createallsubdirs

; Vendor DLLs
; Please keep in ASCII lexical order!
; TODO Include these from a separate file
#if MMArch == "x64"
Source: ..\stage\Release\x64\FxLib.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\LaserCombinerSDK64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\PCO_Kamlib64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\SysInfo.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\TIS_DShowLib10_x64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\TIS_UDSHL10_x64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\XCLIBW64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\atmcd64d.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\hrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\inpoutx64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\libusb0.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mcam64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mcammr64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mrc564.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\mrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\x64\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
#else
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
Source: ..\stage\Release\Win32\TIS_DShowLib10.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\TIS_UDSHL10.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\XCLIBWNT.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\atmcd32d.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\camconj.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\hrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\inpout32.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\libusb0.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mcam.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mcammr.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mrc5.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\mrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\Win32\usb_main.bin; DestDir: {app}; Flags: ignoreversion
#endif

; Olympus IX*3 control module
Source: ..\stage\Release\{#MMArch}\OlympusIX3Control\*; DestDir: {app}\OlympusIX3Control; Flags: ignoreversion recursesubdirs createallsubdirs

; Java wrapper
Source: ..\stage\Release\{#MMArch}\MMCoreJ_wrap.dll; DestDir: {app}; Flags: ignoreversion

; device adapters
Source: ..\stage\Release\{#MMArch}\mmgr_dal_*.dll; DestDir: {app}; Flags: ignoreversion

; python wrapper
Source: ..\stage\Release\{#MMArch}\_MMCorePy.pyd; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\MMCorePy.py; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\MMCoreWrapDemo.py; DestDir: {app}; Flags: ignoreversion

; beanshell scripts
Source: ..\stage\Release\{#MMArch}\scripts\*; DestDir: {app}\scripts; Flags: ignoreversion

; configuration files
Source: ..\stage\Release\{#MMArch}\MMConfig_demo.cfg; DestDir: {app}; Flags: ignoreversion

; ImageJ files
Source: ..\stage\Release\{#MMArch}\ImageJ.exe; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\ImageJ.cfg; DestDir: {app}; Flags: onlyifdoesntexist; Permissions: users-modify
Source: ..\stage\Release\{#MMArch}\ij.jar; DestDir: {app}; Flags: ignoreversion
Source: ..\stage\Release\{#MMArch}\IJ_Prefs.txt; DestDir: {app}; Flags: onlyifdoesntexist
Source: ..\stage\Release\{#MMArch}\macros\*; DestDir: {app}\macros; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\plugins\*; DestDir: {app}\plugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\mmplugins\*; DestDir: {app}\mmplugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: ..\stage\Release\{#MMArch}\mmautofocus\*; DestDir: {app}\mmautofocus; Flags: ignoreversion recursesubdirs createallsubdirs

; NOTE: Don't use "Flags: ignoreversion" on any shared system files

[Dirs]
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
Filename: "{app}\ImageJ.exe"; WorkingDir: "{app}"; Description: {cm:LaunchProgram,Micro-Manager-1.4}; Flags: nowait postinstall skipifsilent
