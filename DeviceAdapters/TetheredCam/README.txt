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


