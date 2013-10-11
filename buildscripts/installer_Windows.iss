;;
;; Set up parameters
;;

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

#define MMStageDir "..\stage\Release\" + MMArch


;;
;; Basic installer setup
;;

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


;;
;; Visual C++ redistributables
;;

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


;;
;; Micro-Manager application files
;;

[Files]

; Java Runtime
Source: {#MMStageDir}\jre\*; DestDir: {app}\jre; Flags: ignoreversion recursesubdirs createallsubdirs

; Vendor DLLs
; Please keep in ASCII lexical order!
; TODO Include these from a separate file
#if MMArch == "x64"
Source: {#MMStageDir}\FxLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\LaserCombinerSDK64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\PCO_Kamlib64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\SysInfo.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\TIS_DShowLib10_x64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\TIS_UDSHL10_x64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\XCLIBW64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\atmcd64d.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\hrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\inpoutx64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\libusb0.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mcam64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mcammr64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mrc564.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mrfw64.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
#else
Source: {#MMStageDir}\DSLRRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\FireCamJ.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\FxLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\LaserCombinerSDK.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\MexJCam.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\NKRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\PCO_Kamlib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\PSRemoteLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\ProcessLib.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\SysInfo.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\TIS_DShowLib10.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\TIS_UDSHL10.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\XCLIBWNT.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\atmcd32d.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\camconj.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\hidapi.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\hrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\inpout32.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\libusb0.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mcam.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mcammr.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mrc5.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mrfw.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\opencv_core231.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\opencv_highgui231.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\usb_main.bin; DestDir: {app}; Flags: ignoreversion
#endif

; Olympus IX*3 control module
Source: {#MMStageDir}\OlympusIX3Control\*; DestDir: {app}\OlympusIX3Control; Flags: ignoreversion recursesubdirs createallsubdirs

; Java wrapper
Source: {#MMStageDir}\MMCoreJ_wrap.dll; DestDir: {app}; Flags: ignoreversion

; Device adapters
Source: {#MMStageDir}\mmgr_dal_*.dll; DestDir: {app}; Flags: ignoreversion

; Python wrapper
Source: {#MMStageDir}\_MMCorePy.pyd; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\MMCorePy.py; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\MMCoreWrapDemo.py; DestDir: {app}; Flags: ignoreversion

; BeanShell scripts
Source: {#MMStageDir}\scripts\*; DestDir: {app}\scripts; Flags: ignoreversion

; Configuration files
Source: {#MMStageDir}\MMConfig_demo.cfg; DestDir: {app}; Flags: ignoreversion

; ImageJ files
Source: {#MMStageDir}\ImageJ.exe; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\ImageJ.cfg; DestDir: {app}; Flags: onlyifdoesntexist; Permissions: users-modify
Source: {#MMStageDir}\ij.jar; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\IJ_Prefs.txt; DestDir: {app}; Flags: onlyifdoesntexist
Source: {#MMStageDir}\macros\*; DestDir: {app}\macros; Flags: ignoreversion recursesubdirs createallsubdirs
Source: {#MMStageDir}\plugins\*; DestDir: {app}\plugins; Flags: ignoreversion recursesubdirs createallsubdirs

; Plugins
Source: {#MMStageDir}\mmplugins\*; DestDir: {app}\mmplugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: {#MMStageDir}\mmautofocus\*; DestDir: {app}\mmautofocus; Flags: ignoreversion recursesubdirs createallsubdirs


;;
;; Additional install settings
;;

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
