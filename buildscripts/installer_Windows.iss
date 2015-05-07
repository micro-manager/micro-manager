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

[InstallDelete]
; Remove plugins from toplevel, where they used to reside; at least some of
; them will now be installed in subdirectories of mmplugins.
Type: files; Name: "{app}\mmplugins\ASIdiSPIM.jar"
Type: files; Name: "{app}\mmplugins\AcquireMultipleRegions.jar"
Type: files; Name: "{app}\mmplugins\BFCorrector.jar"
Type: files; Name: "{app}\mmplugins\Big.jar"
Type: files; Name: "{app}\mmplugins\CRISP.jar"
Type: files; Name: "{app}\mmplugins\ClojureEditor.jar"
Type: files; Name: "{app}\mmplugins\DLLAutoReloader.jar"
Type: files; Name: "{app}\mmplugins\DataBrowser.jar"
Type: files; Name: "{app}\mmplugins\Gaussian.jar"
Type: files; Name: "{app}\mmplugins\HCS.jar"
Type: files; Name: "{app}\mmplugins\ImageFlipper.jar"
Type: files; Name: "{app}\mmplugins\IntelligentAcquisition.jar"
Type: files; Name: "{app}\mmplugins\MMTracker.jar"
Type: files; Name: "{app}\mmplugins\MultiCamera.jar"
Type: files; Name: "{app}\mmplugins\MultiChannelShading.jar"
Type: files; Name: "{app}\mmplugins\PixelCalibrator.jar"
Type: files; Name: "{app}\mmplugins\Projector.jar"
Type: files; Name: "{app}\mmplugins\Recall.jar"
Type: files; Name: "{app}\mmplugins\SlideExplorer.jar"
Type: files; Name: "{app}\mmplugins\SlideExplorer2.jar"
Type: files; Name: "{app}\mmplugins\SplitView.jar"
Type: files; Name: "{app}\mmplugins\StageControl.jar"
Type: files; Name: "{app}\mmplugins\pgFocus.jar"
; PixelCalibrator was moved to Beta, but now is back at toplevel.
Type: files; Name: "{app}\mmplugins\Beta\PixelCalibrator.jar"
; PatternOverlay was placed in Image Processing, but was moved to the toplevel.
Type: files; Name: "{app}\mmplugins\Image_Processing\PatternOverlay.jar"
; Image Procssing was renamed to On-The-Fly Proccessors
Type: files; Name: "{app}\mmplugins\Image_Processing\ImageFlipper.jar"
Type: files; Name: "{app}\mmplugins\Image_Processing\MultiChannelShading.jar"
Type: files; Name: "{app}\mmplugins\Image_Processing\SplitView.jar"

; Renamed to NewportCONEX
Type: files; Name: "{app}\mmgr_dal_CONEX.dll"

; Retired
Type: files; Name: "{app}\mmgr_dal_Hamamatsu.dll"

[Files]

; ImageJ files
Source: {#MMStageDir}\ImageJ.exe; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\ij.jar; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\ImageJ.cfg; DestDir: {app}; Flags: onlyifdoesntexist; Permissions: users-modify
Source: {#MMStageDir}\IJ_Prefs.txt; DestDir: {app}; Flags: onlyifdoesntexist; Permissions: users-modify
Source: {#MMStageDir}\macros\*; DestDir: {app}\macros; Flags: ignoreversion recursesubdirs createallsubdirs

; ImageJ plugins, including Micro-Manager jars
Source: {#MMStageDir}\plugins\*; DestDir: {app}\plugins; Flags: ignoreversion recursesubdirs createallsubdirs

; DLLs (Java wrapper, device adapters, and vendor DLLs)
; (Note: *.dll covers it all, but the separate patterns result in a cleaner install order)
Source: {#MMStageDir}\MMCoreJ_wrap.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\mmgr_dal_*.dll; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\*.dll; DestDir: {app}; Flags: ignoreversion

; Olympus IX*3 control module
Source: {#MMStageDir}\OlympusIX3Control\*; DestDir: {app}\OlympusIX3Control; Flags: ignoreversion recursesubdirs createallsubdirs

; Micro-Manager plugins
Source: {#MMStageDir}\mmplugins\*; DestDir: {app}\mmplugins; Flags: ignoreversion recursesubdirs createallsubdirs
Source: {#MMStageDir}\mmautofocus\*; DestDir: {app}\mmautofocus; Flags: ignoreversion recursesubdirs createallsubdirs

; BeanShell scripts
Source: {#MMStageDir}\scripts\*; DestDir: {app}\scripts; Flags: ignoreversion

; Configuration files
Source: {#MMStageDir}\MMConfig_demo.cfg; DestDir: {app}; Flags: ignoreversion

; Python wrapper
Source: {#MMStageDir}\_MMCorePy.pyd; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\MMCorePy.py; DestDir: {app}; Flags: ignoreversion
Source: {#MMStageDir}\MMCoreWrapDemo.py; DestDir: {app}; Flags: ignoreversion

; MATLAB utility script
Source: {#MMStageDir}\StartMMStudio.m; DestDir: {app}; Flags: ignoreversion

; Java Runtime
Source: {#MMStageDir}\jre\*; DestDir: {app}\jre; Flags: ignoreversion recursesubdirs createallsubdirs


;;
;; Additional install settings
;;

[Dirs]
Name: "{app}"; Permissions: users-modify
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
