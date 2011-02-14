These are driver sources to use the Canon and Nikon cameras in micro-manager from www.micro-manager.org .
Compiled with MMDeviceKit-win-34-01-20100119
Tested with micro-manager 1.3.47 on Windows XP.
Provided "as is".

Compilation instructions on Windows:
- Install micro-manager 1.3.47 (or later)
- Install micro-manager device driver kit MMDeviceKit-win-34-01-20100119.zip (or later)
- Install and configure DSLRRemote, NKRemote or PSRemote from http://www.breezesys.com
- unzip TetheredCam-Sources.zip sources to C:\Program Files\Micro-Manager-1.3\MMDeviceKit-win-34-01\DeviceAdapters\TetheredCam
- compile solution TetheredCamera.sln
- copy the following files to C:\Program Files\Micro-Manager-1.3\ 
	DSLRRemoteLib.dll
	NKRemoteLib.dll
	PSRemoteLib.dll
	mmgr_dal_DSLRRemoteCamera.dll
 	mmgr_dal_NKRemoteCamera.dll
	mmgr_dal_PSRemoteCamera.dll
- (re)start DSLRRemote, NKRemote or PSRemote
- (re)start Micro-manager

Information about the camera drivers is available from http://www.kdvelectronics.eu/focusdrive/MicroManager.html
Information about micro-manager is available from http://www.micro-manager.org


KH 20110104
Project files corrected so that correct device adapter dll files are built for Release.

KDV 20110114 Bugfix: ROI was wrong if ROI button clicked twice in succession. Code cleanup.

KDV 20110117 Bugfix: ROI was wrong if both binning and ROI changed in same snap

KDV 20110209 8/16 bit colors added; raw image decoding through libraw added.

Steps taken to add libraw to the VC++ project:
- download LibRaw-0.13.1.zip from http://www.libraw.org, unzip.
- remove version number from directory name, changing directory name to "LibRaw"
- add to project:
  from directory LibRaw\internal:
    dcraw_common.cpp  dcraw_fileio.cpp  demosaic_packs.cpp
  from directory LibRaw\src:
    libraw_c_api.cpp  libraw_cxx.cpp
- to project>properties>configuration properties>c/c++>additional include directories:
    add LibRaw
- to project>properties>configuration properties>c/c++>preprocessor>preprocessor definitions:
    add LIBRAW_NODLL
- select files:
    dcraw_common.cpp  dcraw_fileio.cpp  demosaic_packs.cpp  libraw_c_api.cpp  libraw_cxx.cpp
  right click and set properties>configuration properties>c/c++>warning level to "Off (/W0)"
- choose configuration: "Release"
  select files:
    dcraw_common.cpp  dcraw_fileio.cpp  demosaic_packs.cpp  libraw_c_api.cpp  libraw_cxx.cpp
  right click and set properties>configuration properties>c/c++>optimization> "Maximize speed (/O2)"
  and properties>configuration properties>c/c++>code generation>enable c++ exceptions> "Yes (/EHsc)"

  This corresponds to the settings in LibRaw/Makefile.msvc for libraw_static (/EHsc /O2 /W0 /DLIBRAW_NODLL)
