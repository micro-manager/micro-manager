///////////////////////////////////////////////////////////////////////////////
// FILE:          RaptorEPIX.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the Raptor camera device adaptor using EPIX frame grabber.
//				  Supported cameras: Falcon, Kite, OWL, Osprey and Kingfisher.
//				  DemoCamera.cpp modified by KBIS for Raptor Photonics camera support
//                
// AUTHOR:        DB @ KBIS, 7/27/2015
//
// COPYRIGHT:     Raptor Photonics Ltd, (2011-2015)
// LICENSE:       License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//
 
//#define MMLINUX32

#include "RaptorEPIX.h"
#include <cstdio> 
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>

#include <iostream>
#include <sys/stat.h>
#if defined (MMLINUX32) || defined(MMLINUX64)
	#include <sys/time.h>
	#include <stdio.h>
	#include <unistd.h>
	#define sprintf_s snprintf
	void Sleep(double x) {usleep(1000.0*x);};
#endif

	
#define _RAPTOR_CAMERA_KITE 1
#define _RAPTOR_CAMERA_OWL  2
#define _RAPTOR_CAMERA_FALCON 4
#define _RAPTOR_CAMERA_COMMONCMDS1 8
#define _RAPTOR_CAMERA_RGB 16
#define _RAPTOR_CAMERA_COMMONCMDS1_RGB (_RAPTOR_CAMERA_COMMONCMDS1 + _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_OWL_320  (_RAPTOR_CAMERA_OWL + 1024)
#define _RAPTOR_CAMERA_OWL_640  (_RAPTOR_CAMERA_OWL + 2048)
#define _RAPTOR_CAMERA_OWL_NINOX_640  (_RAPTOR_CAMERA_OWL + 4096)


#define _RAPTOR_CAMERA_OSPREY				( 32 + _RAPTOR_CAMERA_COMMONCMDS1)
#define _RAPTOR_CAMERA_KINGFISHER_674		( 64 + _RAPTOR_CAMERA_COMMONCMDS1)
#define _RAPTOR_CAMERA_KINGFISHER_694		(128 + _RAPTOR_CAMERA_COMMONCMDS1)
#define _RAPTOR_CAMERA_CYGNET				(256 + _RAPTOR_CAMERA_COMMONCMDS1)
#define _RAPTOR_CAMERA_UNKNOWN1				(512 + _RAPTOR_CAMERA_COMMONCMDS1)
#define _RAPTOR_CAMERA_EAGLE				(8192 + _RAPTOR_CAMERA_COMMONCMDS1)

#define _RAPTOR_CAMERA_OSPREY_RGB			(_RAPTOR_CAMERA_OSPREY			+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_KINGFISHER_674_RGB	(_RAPTOR_CAMERA_KINGFISHER_674	+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_KINGFISHER_694_RGB	(_RAPTOR_CAMERA_KINGFISHER_694	+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_CYGNET_RGB			(_RAPTOR_CAMERA_CYGNET			+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_UNKNOWN1_RGB			(_RAPTOR_CAMERA_UNKNOWN1		+ _RAPTOR_CAMERA_RGB)

#define _RAPTOR_CAMERA_OSPREY_BASE			32
#define _RAPTOR_CAMERA_KINGFISHER_BASE		(64 + 128)
#define _RAPTOR_CAMERA_CYGNET_BASE			256
#define _RAPTOR_CAMERA_UNKNOWN_BASE			512
#define _RAPTOR_CAMERA_EAGLE_BASE			8192


#define _IS_CAMERA_OWL_FAMILY ((cameraType_ & _RAPTOR_CAMERA_OWL)>0)
#define _NOT_CAMERA_OWL_FAMILY ((cameraType_ & _RAPTOR_CAMERA_OWL)==0)

#define LIVEPAIRTEST 0
#undef DOLIVEPAIR

using namespace std;
const double CRaptorEPIX::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "RaptorEPIX.dll" library

// Falcon                 =>           EFIS
// Kite                   =>           ATOR
// Osprey                 =>           IRIS

#ifdef HORIBA_COMPILE
#define RAPTOR "Horiba"
#define FALCON "EFIS"
#define KITE   "ATOR"
#define OSPREY "IRIS"
#define OWL    "Undefined"
#define KINGFISHER "Undefined"
#define CYGNET "Undefined"
#define NINOX "Undefined"
#define EAGLE "Undefined"
#else
#define RAPTOR "Raptor"
#define FALCON "Falcon"
#define KITE   "Kite"
#define OSPREY "Osprey"
#define OWL "Owl"
#define KINGFISHER "Kingfisher"
#define CYGNET "Cygnet"
#define NINOX "Ninox"
#define EAGLE "Eagle"
#endif
#define SPC " "
#define CAM "Camera"

const char* g_RaptorCameraKITEDeviceName			= RAPTOR SPC KITE SPC CAM;
const char* g_RaptorCameraOwl320DeviceName			= RAPTOR SPC OWL SPC CAM " 320";
const char* g_RaptorCameraOwl640DeviceName			= RAPTOR SPC OWL SPC CAM " 640";
const char* g_RaptorCameraOwlNinox640DeviceName		= RAPTOR SPC NINOX SPC CAM " 640";
const char* g_RaptorCameraFalconDeviceName			= RAPTOR SPC FALCON SPC CAM;
const char* g_RaptorCameraEagleDeviceName			= RAPTOR SPC EAGLE SPC CAM;
const char* g_RaptorCameraOspreyDeviceName			= RAPTOR SPC OSPREY SPC CAM;
const char* g_RaptorCameraOspreyRGBDeviceName		= RAPTOR SPC OSPREY " RGB " CAM;
const char* g_RaptorCameraKingfisher674DeviceName	= RAPTOR SPC KINGFISHER " 674";
const char* g_RaptorCameraKingfisher694DeviceName	= RAPTOR SPC KINGFISHER " 694";
const char* g_RaptorCameraKingfisher674RGBDeviceName= RAPTOR SPC KINGFISHER " 674 RGB";
const char* g_RaptorCameraKingfisher694RGBDeviceName= RAPTOR SPC KINGFISHER " 694 RGB";
const char* g_RaptorCameraCygnetDeviceName			= RAPTOR SPC CYGNET SPC CAM;
const char* g_RaptorCameraCygnetRGBDeviceName		= RAPTOR SPC CYGNET " RGB " CAM;
const char* g_RaptorCameraUnknown1DeviceName		= RAPTOR SPC "XCAP Export";
const char* g_RaptorCameraUnknown1RGBDeviceName		= RAPTOR SPC "XCAP RGB Export";

const char* g_RaptorKITE = KITE;
const char* g_RaptorOwl320 = OWL " 320";
const char* g_RaptorOwl640 = OWL " 640";
const char* g_RaptorNinox640 = NINOX " 640";
const char* g_RaptorEagle = EAGLE;
const char* g_RaptorFalcon = FALCON;
const char* g_RaptorOsprey = OSPREY;
const char* g_RaptorOspreyRGB = OSPREY " RGB";
const char* g_RaptorKingfisher674 = KINGFISHER " 674";
const char* g_RaptorKingfisher694 = KINGFISHER " 694";
const char* g_RaptorKingfisher674RGB = KINGFISHER " 674 RGB";
const char* g_RaptorKingfisher694RGB = KINGFISHER " 694 RGB";
const char* g_RaptorCygnet		= CYGNET;
const char* g_RaptorCygnetRGB   = CYGNET " RGB";
const char* g_RaptorUnknown1    = "XCAP Export";
const char* g_RaptorUnknown1RGB = "XCAP Export RGB";

/*
	const char* g_RaptorCameraKITEDeviceName			= "Raptor KITE Camera";
	const char* g_RaptorCameraOWLDeviceName				= "Raptor OWL Camera";
	const char* g_RaptorCameraFalconDeviceName			= "Raptor Falcon Camera";
	const char* g_RaptorCameraOspreyDeviceName			= "Raptor Osprey Camera";
	const char* g_RaptorCameraOspreyRGBDeviceName		= "Raptor Osprey RGB Camera";
	const char* g_RaptorCameraKingfisher674DeviceName	= "Raptor Kingfisher 674";
	const char* g_RaptorCameraKingfisher694DeviceName	= "Raptor Kingfisher 694";
	const char* g_RaptorCameraKingfisher674RGBDeviceName= "Raptor Kingfisher 674 RGB";
	const char* g_RaptorCameraKingfisher694RGBDeviceName= "Raptor Kingfisher 694 RGB";
	const char* g_RaptorCameraCygnetDeviceName			= "Raptor Cygnet Camera";
	const char* g_RaptorCameraCygnetRGBDeviceName		= "Raptor Cygnet RGB Camera";
	const char* g_RaptorCameraUnknown1DeviceName		= "Raptor XCAP Export";
	const char* g_RaptorCameraUnknown1RGBDeviceName		= "Raptor XCAP RGB Export";

	const char* g_RaptorKITE = "KITE";
	const char* g_RaptorOWL  = "OWL";
	const char* g_RaptorFalcon = "Falcon";
	const char* g_RaptorOsprey = "Osprey";
	const char* g_RaptorOspreyRGB = "Osprey RGB";
	const char* g_RaptorKingfisher674 = "Kingfisher 674";
	const char* g_RaptorKingfisher694 = "Kingfisher 694";
	const char* g_RaptorKingfisher674RGB = "Kingfisher 674 RGB";
	const char* g_RaptorKingfisher694RGB = "Kingfisher 694 RGB";
	const char* g_RaptorCygnet		= "Cygnet";
	const char* g_RaptorCygnetRGB   = "Cygnet RGB";
	const char* g_RaptorUnknown1    = "XCAP Export";
	const char* g_RaptorUnknown1RGB = "XCAP Export RGB";
*/

const char* g_Keyword_ExposureMax = "Exposure Max";
const char* g_Keyword_PCBTemp     = "Temp PCB (oC)";
const char* g_Keyword_CCDTemp     = "Temp CCD (oC)";
const char* g_Keyword_SensorTemp     = "Temp Sensor (oC)";
const char* g_Keyword_MicroReset  = "Micro Reset";
const char* g_Keyword_TestPattern = "Test Pattern";
const char* g_Keyword_TECooler    = "TE Cooler";
const char* g_Keyword_TECFan      = "TEC Fan";
const char* g_Keyword_TECooler_neg5oC = "Set Point -5oC";
const char* g_Keyword_TECooler_neg20oC = "Set Point -20oC";
const char* g_Keyword_TECooler_Reset = "Temp Trip Reset";

const char* g_Keyword_AntiBloom   = "Anti-Bloom";

const char* g_Keyword_ROI_AOI_Left    = "ROI X";
const char* g_Keyword_ROI_AOI_Top     = "ROI Y";
const char* g_Keyword_ROI_AOI_Width   = "ROI X Size";
const char* g_Keyword_ROI_AOI_Height  = "ROI Y Size";
const char* g_Keyword_AGC_AOI_Left    = "ROI (AGC) X";
const char* g_Keyword_AGC_AOI_Top     = "ROI (AGC) Y";
const char* g_Keyword_AGC_AOI_Width   = "ROI (AGC) X Size";
const char* g_Keyword_AGC_AOI_Height  = "ROI (AGC) Y Size";

const char* g_Keyword_AOI_Left    = g_Keyword_ROI_AOI_Left;
const char* g_Keyword_AOI_Top     = g_Keyword_ROI_AOI_Top;
const char* g_Keyword_AOI_Width   = g_Keyword_ROI_AOI_Width;
const char* g_Keyword_AOI_Height  = g_Keyword_ROI_AOI_Height;

const char* g_Keyword_UseAOI      = "ROI Use";
const char* g_Keyword_TECSetPoint = "TEC Set Point (oC)"; 
const char* g_Keyword_NUCState    = "Xpert: NUC State";
const char* g_Keyword_NUCState0    = "0: Offset Corrected";
const char* g_Keyword_NUCState1    = "1: Offset+Gain Corr";
const char* g_Keyword_NUCState2    = "2: Normal";
const char* g_Keyword_NUCState3    = "3: Offset+Gain+Dark";
const char* g_Keyword_NUCState4    = "4: 8bit Offset /32";
const char* g_Keyword_NUCState5    = "5: 8bit Dark *2^19";
const char* g_Keyword_NUCState6    = "6: 8bit Gain /128";
const char* g_Keyword_NUCState7a   = "7: Off+Gain+Dark+BAD";
const char* g_Keyword_NUCState7b   = "7: Reserved Map";
const char* g_Keyword_NUCState8    = "8: Ramp Test Pattern";
const char* g_Keyword_PeakAvgLevel = "Xpert: AE Peak/Avg Level";
const char* g_Keyword_AGCSpeed     = "Xpert: AE Gain Speed";
const char* g_Keyword_ExpSpeed     = "Xpert: AE Exp Speed";
const char* g_Keyword_AutoExpLevel = "Xpert: AE Set Level";
const char* g_Keyword_UseSerialLog = "Xpert: Use Serial Log";
const char* g_Keyword_EPIXUnit     = "EPIX_Unit";
const char* g_Keyword_EPIXUnit2    = "EPIX Unit";
const char* g_Keyword_EPIXMultiUnitMask    = "EPIX Units to Open";

const char* g_Keyword_HighGain      = "High Gain";
const char* g_Keyword_ROIAppearance = "ROI Appearance";
const char* g_Keyword_AutoExposure = "Exposure: Auto";
const char* g_Keyword_TrigDelay   = "Ext. Trig. Delay (ms)";
const char* g_Keyword_TrigITR   = "Live: Integrate Then Read";
const char* g_Keyword_TrigFFR   = "Live: Fixed Frame Rate (FFR)";
const char* g_Keyword_ExtTrigger  = "Ext. Trigger";
const char* g_Keyword_ExtTrigger_posTrig = "On: (Ext. +ve Edge)";
const char* g_Keyword_ExtTrigger_negTrig = "On: (Ext. -ve Edge)";
const char* g_Keyword_ExtTrigger_Abort = "Abort Exposure";
const char* g_Keyword_CaptureMode = "Capture Mode";
const char* g_Keyword_CaptureMode_SnapShot = "Snapshot";
const char* g_Keyword_CaptureMode_Live = "Live";
const char* g_Keyword_FixedFrameRate = "Frame Rate (FPS)";
const char* g_Keyword_FrameRate = "Frame Rate";
const char* g_Keyword_FrameRate_25Hz = "25 Hz";
const char* g_Keyword_FrameRate_30Hz = "30 Hz";
const char* g_Keyword_FrameRate_50Hz = "50 Hz";
const char* g_Keyword_FrameRate_60Hz = "60 Hz";
const char* g_Keyword_FrameRate_90Hz = "90 Hz";
const char* g_Keyword_FrameRate_120Hz = "120 Hz";
const char* g_Keyword_FrameRate_User = "User Defined";
const char* g_Keyword_FrameRate_ExtTrig = "Ext. Trig.";
const char* g_Keyword_ROI_Normal = "Normal";
const char* g_Keyword_ROI_Bright = "Highlight";
const char* g_Keyword_ROI_Dark   = "Border";
const char* g_Keyword_VideoPeak   = "Video Peak";
const char* g_Keyword_VideoAvg    = "Video Avg";
const char* g_Keyword_BuildInfo   = "Build Info";
const char* g_Keyword_FrameRateUser = "Frame Rate (User)";
const char* g_Keyword_BlackOffset = "Black Offset";
const char* g_Keyword_ForceUpdate = "Force Update";
const char* g_Keyword_On	      = "On";
const char* g_Keyword_Off	      = "Off";
const char* g_Keyword_PostCaptureROI = "Xpert: Post Snap Crop";
const char* g_Keyword_Defaults = "Use Defaults";
const char* g_Keyword_UseDefaults = "Use Defaults";
const char* g_Keyword_FrameAccumulate = "Frame Average";
const char* g_Keyword_TriggerTimeout = "Trig. Timeout (sec)";
const char* g_Keyword_FrameInterval = "Frame Interval (ms)";
const char* g_Keyword_HDR = "High Dynamic Range";
const char* g_Keyword_NUCMap = "View NUC Map";
const char* g_Keyword_DebayerMethod = "Color Debayer";
const char* g_Keyword_Debayer_Bilinear = "On";
const char* g_Keyword_Debayer_Nearest = "Nearest";
const char* g_Keyword_Debayer_None = "Off";

const char* g_Keyword_HorizontalFlip = "Horizontal Flip";
const char* g_Keyword_InvertVideo = "Invert Video";
const char* g_Keyword_BadPixel = "Show Bad Pixels";
const char* g_Keyword_ImageSharpen = "Image Sharpen";

const char* g_Keyword_ReadoutRate = "Readout Rate (MHz)";
const char* g_Keyword_ReadoutRate2 = "Readout Rate (kHz)";
const char* g_Keyword_ReadoutMode = "Readout Mode";
const char* g_Keyword_ReadoutMode_Baseline = "Baseline Clamped";
const char* g_Keyword_ReadoutMode_CDS	   = "CDS (default)";
const char* g_Keyword_ReadoutMode_TestPattern = "Test Pattern";
const char* g_Keyword_ReadoutMode_Normal = "Normal";

const char* g_Keyword_ShutterMode		   = "Shutter Mode";
const char* g_Keyword_ShutterMode_Closed   = "Closed";
const char* g_Keyword_ShutterMode_Open     = "Open";
const char* g_Keyword_ShutterMode_Exposure = "Exposure";

const char* g_Keyword_ShutterDelayOpen  = "Shutter Delay Open";
const char* g_Keyword_ShutterDelayClose = "Shutter Delay Close";

const char* g_Keyword_HighPreAmpGain	   = "High Pre-Amp Gain";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8 bit";
const char* g_PixelType_16bit = "16 bit";
const char* g_PixelType_24bitRGB = "24 bitRGB";
const char* g_PixelType_32bitRGB = "32 bitRGB";
const char* g_PixelType_64bitRGB = "64 bitRGB";
const char* g_PixelType_32bit = "32 bit";  // floating point greyscale

MMThreadLock g_serialLock_[64];
FILE *fidSerial[64];
long gUseSerialLog[64];
double gClockZero[64];
double gClockCurrent[64];
void mySleep(double nTime_ms);
double myClock();
int g_PIXCI_DriverLoaded = 0;
unsigned char g_ucSerialBuf[256];
int g_SerialOK=false;
bool g_bCheckSum = false;

int serialWriteReadCmd(int unitopenmap, int unit, unsigned char* bufin, int insize, unsigned char* bufout, int outsize, bool bChkSum=true ) ;

//*********** Start EPIX Setup *****************
/*
 *  Set number of expected PIXCI(R) image boards, from 1 to 4.
 *  The XCLIB Simple 'C' Functions expect that the boards are
 *  identical and operated at the same resolution.
 *
 *  For PIXCI(R) imaging boards with multiple, functional units,
 *  the XCLIB presents the two halves of the
 *  PIXCI\*(Rg\ E1DB, E4DB, ECB2, EL1DB, ELS2, SI2, or SV7 imaging boards
 *  or the four quarters of the PIXCI\*(Rg\ SI4 imaging board
 *  as two or four independent PIXCI\*(Rg\ imaging boards, respectively.
 *
 */
extern "C" {
#if defined (MMLINUX32) || defined(MMLINUX64)
    #include "/usr/local/xclib/xcliball.h"
#elif defined(WIN64)
	#include "..\..\SecretDeviceAdapters\RaptorEPIX\XCLIB64\xcliball.h"
#else
	#include "..\..\SecretDeviceAdapters\RaptorEPIX\XCLIB32\xcliball.h"
#endif
}

//G:\Program Files\EPIX\XCLIB

//#if !defined(UNITS)
//    #define UNITS	1
//	#define UNITMASK 1
//#endif
/*
int UNITS=1;
int UNITMASK=1;
int UNITSOPENMAP=1;
int UNITSMAP=1;
*/
//#define UNITSMAP   1 /* ((UNITMASKNITS)-1)*/  /* shorthand - bitmap of all units */
//#if !defined(UNITSOPENMAP)
//    #define UNITSOPENMAP UNITSMAP
//#endif 

static	pxvbtime_t  lastcapttime[1] = {0};		// when was image last captured

#if defined(WIN64)
	#define FORMATFILE_LOAD_KITE   "XCAP\xcapRaptorKITE64.fmt"  // loaded from file during execution
	//#define FORMATFILE_LOAD_OWL    "XCAP\xcapRaptorOWL640x480_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_320    "XCAP\xcapRaptorOWL-CL-320_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_640    "XCAP\xcapRaptorOWL-CL-640_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_NINOX_640    "XCAP\xcapRaptorNinox-640_64B.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_NINOX_640_BIN1    "XCAP\xcapRaptorNinox-640_64_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_EAGLE "XCAP\xcapRaptorEagle_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN1 "XCAP\xcapRaptorEagle_64A_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN2 "XCAP\xcapRaptorEagle_64A_Bin2.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN4 "XCAP\xcapRaptorEagle_64A_Bin4.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_FALCON "XCAP\xcapRaptorFalcon64.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694	   "XCAP\xcapRaptorKingFisher694_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN1 "XCAP\xcapRaptorKingFisher694_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN2 "XCAP\xcapRaptorKingFisher694_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN4 "XCAP\xcapRaptorKingFisher694_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674	    "XCAP\xcapRaptorKingFisher674_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN1 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN2 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN4 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694_RGB	   "XCAP\xcapRaptorKingFisher694RGB_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN1 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN2 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN4 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB	   "XCAP\xcapRaptorKingFisher674RGB_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN1 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN2 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN4 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution


	#define FORMATFILE_LOAD_CYGNET					"XCAP\xcapRaptorCygnet_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN1				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN2				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN4				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB				"XCAP\xcapRaptorCygnetRGB_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN1			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN2			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN4			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_UNKNOWN1				"XCAP\xcapRaptorExport64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN1			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN2			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN4			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB			"XCAP\xcapRaptorExportRGB64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN1		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN2		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN4		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_OSPREY "XCAP\xcapRaptorOsprey64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN1 "XCAP\xcapRaptorOsprey64Bin1B.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN2 "XCAP\xcapRaptorOsprey64Bin2B.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN4 "XCAP\xcapRaptorOsprey64Bin4B.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_OSPREY_RGB "XCAP\xcapRaptorOspreyRGB64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN1 "XCAP\xcapRaptorOspreyRGB64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN2 "XCAP\xcapRaptorOspreyRGB64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN4 "XCAP\xcapRaptorOspreyRGB64Bin1.fmt"  // loaded from file during execution
	
	//#define FORMATFILE_LOAD_OSPREY_RGB "XCAP\xcapRaptorKingFisher694RGB64.fmt"  // loaded from file during execution
	//#define FORMATFILE_LOAD_OSPREY_RGB_BIN1 "XCAP\xcapRaptorKingFisher694RGB64_Bin1.fmt"  // loaded from file during execution
	//#define FORMATFILE_LOAD_OSPREY_RGB_BIN2 "XCAP\xcapRaptorKingFisher694RGB64_Bin1.fmt"  // loaded from file during execution
	//#define FORMATFILE_LOAD_OSPREY_RGB_BIN4 "XCAP\xcapRaptorKingFisher694RGB64_Bin1.fmt"  // loaded from file during execution

#elif defined(WIN32)
	#define FORMATFILE_LOAD_KITE   "XCAP\xcapRaptorKITE.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL    "XCAP\xcapRaptorOWL-CL-320.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_OWL_320    "XCAP\xcapRaptorOWL-CL-320_32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_640    "XCAP\xcapRaptorOWL-CL-640_32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_NINOX_640    "XCAP\xcapRaptorNinox-640_32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL_NINOX_640_BIN1    "XCAP\xcapRaptorNinox-640_32_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_EAGLE "XCAP\xcapRaptorEagle_32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN1 "XCAP\xcapRaptorEagle_32_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN2 "XCAP\xcapRaptorEagle_32_Bin2.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_EAGLE_BIN4 "XCAP\xcapRaptorEagle_32_Bin4.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_FALCON "XCAP\xcapRaptorFalcon.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY "XCAP\xcapRaptorOsprey.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN1 "XCAP\xcapRaptorOspreyBin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN2 "XCAP\xcapRaptorOspreyBin2.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN4 "XCAP\xcapRaptorOspreyBin4.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB "XCAP\xcapRaptorOspreyRGB32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN1 "XCAP\xcapRaptorOspreyRGB32Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN2 "XCAP\xcapRaptorOspreyRGB32Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN4 "XCAP\xcapRaptorOspreyRGB32Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694	   "XCAP\xcapRaptorKingFisher694_32A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN1 "XCAP\xcapRaptorKingFisher694_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN2 "XCAP\xcapRaptorKingFisher694_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN4 "XCAP\xcapRaptorKingFisher694_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674	   "XCAP\xcapRaptorKingFisher674_32A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN1 "XCAP\xcapRaptorKingFisher674_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN2 "XCAP\xcapRaptorKingFisher674_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN4 "XCAP\xcapRaptorKingFisher674_32ABin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694_RGB	   "XCAP\xcapRaptorKingFisher694RGB_32A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN1 "XCAP\xcapRaptorKingFisher694RGB_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN2 "XCAP\xcapRaptorKingFisher694RGB_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN4 "XCAP\xcapRaptorKingFisher694RGB_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB	   "XCAP\xcapRaptorKingFisher674RGB_32A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN1 "XCAP\xcapRaptorKingFisher674RGB_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN2 "XCAP\xcapRaptorKingFisher674RGB_32ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN4 "XCAP\xcapRaptorKingFisher674RGB_32ABin1.fmt"  // loaded from file during execution


	#define FORMATFILE_LOAD_CYGNET					"XCAP\xcapRaptorCygnet_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN1				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN2				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN4				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB				"XCAP\xcapRaptorCygnetRGB_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN1			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN2			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN4			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_UNKNOWN1				"XCAP\xcapRaptorExport64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN1			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN2			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN4			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB			"XCAP\xcapRaptorExportRGB64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN1		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN2		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN4		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution


#elif defined(MMLINUX32)
	#define FORMATFILE_LOAD_KITE   "XCAP\xcapRaptorKITE_Linux32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL    "XCAP\xcapRaptorOWL-CL-320_Linux32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_FALCON "XCAP\xcapRaptorFalcon_Linux32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY "XCAP\xcapRaptorOsprey_Linux32.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN1 "XCAP\xcapRaptorOsprey_Linux32_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN2 "XCAP\xcapRaptorOsprey_Linux32_Bin2.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN4 "XCAP\xcapRaptorOsprey_Linux32_Bin4.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694	   "XCAP\xcapRaptorKingFisher694_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN1 "XCAP\xcapRaptorKingFisher694_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN2 "XCAP\xcapRaptorKingFisher694_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN4 "XCAP\xcapRaptorKingFisher694_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674	    "XCAP\xcapRaptorKingFisher674_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN1 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN2 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN4 "XCAP\xcapRaptorKingFisher674_64A_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694_RGB	   "XCAP\xcapRaptorKingFisher694RGB_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN1 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN2 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN4 "XCAP\xcapRaptorKingFisher694RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB	   "XCAP\xcapRaptorKingFisher674RGB_64A.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN1 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN2 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN4 "XCAP\xcapRaptorKingFisher674RGB_64ABin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_CYGNET					"XCAP\xcapRaptorCygnet_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN1				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN2				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN4				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB				"XCAP\xcapRaptorCygnetRGB_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN1			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN2			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN4			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_UNKNOWN1				"XCAP\xcapRaptorExport64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN1			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN2			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN4			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB			"XCAP\xcapRaptorExportRGB64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN1		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN2		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN4		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution

#elif defined(MMLINUX64)
	#define FORMATFILE_LOAD_KITE   "XCAP\xcapRaptorKITE_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OWL    "XCAP\xcapRaptorOWL-CL-320_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_FALCON "XCAP\xcapRaptorFalcon_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY "XCAP\xcapRaptorOsprey_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN1 "XCAP\xcapRaptorOsprey_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN2 "XCAP\xcapRaptorOsprey_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_BIN4 "XCAP\xcapRaptorOsprey_Linux64_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_OSPREY_RGB "XCAP\xcapRaptorOspreyRGB_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN1 "XCAP\xcapRaptorOspreyRGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN2 "XCAP\xcapRaptorOspreyRGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_OSPREY_RGB_BIN4 "XCAP\xcapRaptorOspreyRGB_Linux64_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694	   "XCAP\xcapRaptorKingFisher694_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN1 "XCAP\xcapRaptorKingFisher694_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN2 "XCAP\xcapRaptorKingFisher694_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_BIN4 "XCAP\xcapRaptorKingFisher694_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674	   "XCAP\xcapRaptorKingFisher674_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN1 "XCAP\xcapRaptorKingFisher674_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN2 "XCAP\xcapRaptorKingFisher674_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_BIN4 "XCAP\xcapRaptorKingFisher674_Linux64_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_KINGFISHER694_RGB	"XCAP\xcapRaptorKingFisher694RGB_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN1  "XCAP\xcapRaptorKingFisher694RGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN2  "XCAP\xcapRaptorKingFisher694RGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER694_RGB_BIN4  "XCAP\xcapRaptorKingFisher694RGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB	"XCAP\xcapRaptorKingFisher674RGB_Linux64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN1  "XCAP\xcapRaptorKingFisher674RGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN2  "XCAP\xcapRaptorKingFisher674RGB_Linux64_Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_KINGFISHER674_RGB_BIN4  "XCAP\xcapRaptorKingFisher674RGB_Linux64_Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_CYGNET					"XCAP\xcapRaptorCygnet_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN1				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN2				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_BIN4				"XCAP\xcapRaptorCygnet_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB				"XCAP\xcapRaptorCygnetRGB_64.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN1			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN2			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_CYGNET_RGB_BIN4			"XCAP\xcapRaptorCygnetRGB_64Bin1.fmt"  // loaded from file during execution

	#define FORMATFILE_LOAD_UNKNOWN1				"XCAP\xcapRaptorExport64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN1			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN2			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_BIN4			"XCAP\xcapRaptorExportNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB			"XCAP\xcapRaptorExportRGB64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN1		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN2		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
	#define FORMATFILE_LOAD_UNKNOWN1_RGB_BIN4		"XCAP\xcapRaptorExportRGBNoSerial64bit.fmt"  // loaded from file during execution
#endif
//#define FORMATFILE_COMP   "xcvidset.fmt"  // or compiled into this application

/*
 *  Optionally, set driver configuration parameters.
 *  These are normally left to the default, "".
 *  The actual driver configuration parameters include the
 *  desired PIXCI(R) imaging boards, but to make configuation easier,
 *  code, below, will automatically add board selection to this.
 */
#if !defined(DRIVERPARMS)
  //#define DRIVERPARMS "-QU 0"   // don't use interrupts
    #define DRIVERPARMS ""	  // default
#endif

_cDcl(_dllpxlib,_cfunfcc,int)
pxd_setVideoResolution(
    int     unitmap,	    // usual
    int     xdim,	    // pixels per line
    int     ydim,	    // pixels per column
    int     hoffset,	    // video hoffset
    int     voffset	    // video voffset
){
    int     r = 0, r1;
    int     u, umap, multiple = 0;
    struct xclibs *xc;

    xc = pxd_xclibEscape(0, 0, 0);
    if (!xc)
	return(PXERNOTOPEN);
    {
	#if USEINTERNALAPI   // using internal API
	    xclib_DeclareVidStateStructs2(vidstate, pxdstatep->devinfo[0].s.model);
	    xclib_InitVidStateStructs2(vidstate, pxdstatep->devinfo[0].s.model);
	#else
	    xclib_DeclareVidStateStructs2(vidstate, pxd_infoModel(unitmap));
	    xclib_InitVidStateStructs2(vidstate, pxd_infoModel(unitmap));
	#endif

	#if 1|MULTIPLEFORMATS
	    // We might have compiled for multiple formats, but it may not be active.
	    if (xc->pxlib.getState(&xc->pxlib, 0, PXMODE_DIGI+1, &vidstate) >= 0)
		multiple = 1;
	    for (u = 0, umap = unitmap; u < PXMAX_UNITS && umap; umap>>=1, u++) {
		if (!(umap&1))
		    continue;
		xc->pxlib.getState(&xc->pxlib, 0, multiple? PXMODE_DIGI+u: PXMODE_DIGI, &vidstate);
		vidstate.vidformat->xviddim[PXLHCM_MAX] = xdim;
		vidstate.vidformat->xdatdim[PXLHCM_MAX] = xdim;
		vidstate.vidformat->yviddim[PXLHCM_MAX] = ydim;
		vidstate.vidformat->ydatdim[PXLHCM_MAX] = ydim;
		vidstate.vidformat->xvidoffset[PXLHCM_MAX] = xdim;
		vidstate.vidformat->yvidoffset[PXLHCM_MAX] = ydim;
		vidstate.vidformat->xviddim[PXLHCM_MOD] = 0;
		vidstate.vidformat->xdatdim[PXLHCM_MOD] = 0;
		vidstate.vidformat->yviddim[PXLHCM_MOD] = 0;
		vidstate.vidformat->ydatdim[PXLHCM_MOD] = 0;
		vidstate.vidformat->is.hoffset = hoffset;
		vidstate.vidformat->is.voffset = voffset;
		//
/*		vidstate.vidres->x.datsamples = xdim;
		vidstate.vidres->x.vidsamples = xdim;
		vidstate.vidres->x.vidoffsend = xdim-1;
		vidstate.vidres->line.pd = xdim*2;
		vidstate.vidres->line.pdod = xdim*2;
		vidstate.vidres->line.pdodal = xdim*2; 
		vidstate.vidres->line.pdodalsf = xdim*2;
		vidstate.vidres->field.pd = xdim*ydim;
		vidstate.vidres->field.pdod = xdim*ydim+8;
		vidstate.vidres->field.pdodal = xdim*ydim+64;
		vidstate.vidres->field.pdodalsf = xdim*ydim+64;

		vidstate.vidres->x.setmaxdatsamples = 3;
		vidstate.vidres->x.setmaxvidsamples = 3;
		vidstate.vidres->y.setmaxdatsamples = 3;
		vidstate.vidres->y.setmaxvidsamples = 3;
*/
		vidstate.vidres->x.setmaxdatsamples = 1;
		vidstate.vidres->x.setmaxvidsamples = 1;
		vidstate.vidres->y.setmaxdatsamples = 1;
		vidstate.vidres->y.setmaxvidsamples = 1;
		vidstate.vidres->setmaxdatfields    = 1;
		vidstate.vidres->setmaxdatphylds    = 1;
		r1 = xc->pxlib.defineState(&xc->pxlib, 0, multiple? PXMODE_DIGI+u: PXMODE_DIGI, &vidstate);
		r = min(r, r1);
		if (!multiple)
		    break;
	    }
	    r1 = pxd_xclibEscaped(unitmap, 0, 0);
	    r = min(r, r1);
	    return(r);
	#else
	    ?
	#endif
    }
}


//*********** End EPIX Setup *****************


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_RaptorCameraFalconDeviceName,    MM::CameraDevice, g_RaptorCameraFalconDeviceName);
   RegisterDevice(g_RaptorCameraKITEDeviceName,      MM::CameraDevice, g_RaptorCameraKITEDeviceName);
   RegisterDevice(g_RaptorCameraOspreyDeviceName,    MM::CameraDevice, g_RaptorCameraOspreyDeviceName);
   RegisterDevice(g_RaptorCameraOspreyRGBDeviceName, MM::CameraDevice, g_RaptorCameraOspreyRGBDeviceName);

#ifndef HORIBA_COMPILE
   RegisterDevice(g_RaptorCameraOwl320DeviceName,       MM::CameraDevice, g_RaptorCameraOwl320DeviceName);
   RegisterDevice(g_RaptorCameraOwl640DeviceName,       MM::CameraDevice, g_RaptorCameraOwl640DeviceName);
   RegisterDevice(g_RaptorCameraOwlNinox640DeviceName,  MM::CameraDevice, g_RaptorCameraOwlNinox640DeviceName);
   RegisterDevice(g_RaptorCameraEagleDeviceName,        MM::CameraDevice, g_RaptorCameraEagleDeviceName);

   RegisterDevice(g_RaptorCameraKingfisher674DeviceName, MM::CameraDevice, g_RaptorCameraKingfisher674DeviceName);
   RegisterDevice(g_RaptorCameraKingfisher694DeviceName, MM::CameraDevice, g_RaptorCameraKingfisher694DeviceName);
   RegisterDevice(g_RaptorCameraKingfisher674RGBDeviceName, MM::CameraDevice, g_RaptorCameraKingfisher674RGBDeviceName);
   RegisterDevice(g_RaptorCameraKingfisher694RGBDeviceName, MM::CameraDevice, g_RaptorCameraKingfisher694RGBDeviceName);
#endif
/*   AddAvailableDeviceName(g_RaptorCameraCygnetDeviceName, g_RaptorCameraCygnetDeviceName);
   AddAvailableDeviceName(g_RaptorCameraCygnetRGBDeviceName, g_RaptorCameraCygnetRGBDeviceName);
   AddAvailableDeviceName(g_RaptorCameraUnknown1DeviceName, g_RaptorCameraUnknown1DeviceName);
   AddAvailableDeviceName(g_RaptorCameraUnknown1RGBDeviceName, g_RaptorCameraUnknown1RGBDeviceName);
   */
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_RaptorCameraKITEDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_KITE);
   }
   else if (strcmp(deviceName, g_RaptorCameraOwl320DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_OWL_320);
   }
   else if (strcmp(deviceName, g_RaptorCameraOwl640DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_OWL_640);
   }
   else if (strcmp(deviceName, g_RaptorCameraOwlNinox640DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_OWL_NINOX_640);
   }
   else if (strcmp(deviceName, g_RaptorCameraEagleDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_EAGLE);
   }
   else if (strcmp(deviceName, g_RaptorCameraFalconDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_FALCON);
   }
   else if (strcmp(deviceName, g_RaptorCameraOspreyDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_OSPREY);
   }
   else if (strcmp(deviceName, g_RaptorCameraOspreyRGBDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_OSPREY_RGB);
   }
   else if (strcmp(deviceName, g_RaptorCameraKingfisher674DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_KINGFISHER_674);
   }
   else if (strcmp(deviceName, g_RaptorCameraKingfisher694DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_KINGFISHER_694);
   }
   else if (strcmp(deviceName, g_RaptorCameraKingfisher674RGBDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_KINGFISHER_674_RGB);
   }
   else if (strcmp(deviceName, g_RaptorCameraKingfisher694RGBDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_KINGFISHER_694_RGB);
   }
/*   else if (strcmp(deviceName, g_RaptorCameraCygnetDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_CYGNET);
   }
   else if (strcmp(deviceName, g_RaptorCameraCygnetRGBDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_CYGNET_RGB);
   }
   else if (strcmp(deviceName, g_RaptorCameraUnknown1DeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_UNKNOWN1);
   }
   else if (strcmp(deviceName, g_RaptorCameraUnknown1RGBDeviceName) == 0)
   {
      // create camera
      return new CRaptorEPIX(_RAPTOR_CAMERA_UNKNOWN1_RGB);
   }*/
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CRaptorEPIX implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CRaptorEPIX constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CRaptorEPIX::CRaptorEPIX(int nCameraType) :
   CCameraBase<CRaptorEPIX> (),
   cameraType_(0),
   dPhase_(0),
   exposure_(0),
   exposureMax_(1000),
   initialized_(false),
   serialOK_(false),
   readoutUs_(0.0),
   scanMode_(1),
   bitDepth_(16),
   roiX_(0),
   roiY_(0),
   roiSnapX_(0),
   roiSnapY_(0),
   snapWidth_(0),
   snapHeight_(0),
   snapBin_(1),
   sequenceStartTime_(0), 
	binSize_(1),
	cameraCCDXSize_(888),
	cameraCCDYSize_(888),
   nComponents_(1),
   pDemoResourceLock_(0),
   triggerDevice_(""),
	dropPixels_(false),
	saturatePixels_(false),
	fractionOfPixelsToDropOrSaturate_(0.002),
	EMGain_(0),
	Gain_(0),
	useAOI_(false),
	updatingAOI_(false),
    AOILeft_(0),
    AOITop_(0),
    AOIWidth_(0),
    AOIHeight_(0),
    LastAOILeft_(0),
    LastAOITop_(0),
    LastAOIWidth_(0),
    LastAOIHeight_(0),
	triggerMode_(0),
	captureMode_(0),
	ForceUpdate_(true),
	TestPattern_(0),
	ExtTrigStatus_(0),
	AntiBloom_(0),
	FPGACtrl_(0),
	PostCaptureROI_(false),
    TECSetPoint_(15),
    OWLNUCState_(0),
    OWLPeakAvgLevel_(0),
    OWLAGCSpeed_(0),
    OWLROIAppearance_(0),
	OWLAutoExp_(0),
	OWLHighGain_(0),
	OWLBlackOffset_(0),
	OWLTrigDelay_(0.0),
	OWLFrameRate_(0.0),
    serialNum_(0),
    buildDateDD_(0),
    buildDateMM_(0),
    buildDateYY_(0),
    EPROM_ADC_Cal_0C_(0),
    EPROM_ADC_Cal_40C_(0),
    EPROM_DAC_Cal_0C_(0),
    EPROM_DAC_Cal_40C_(0),
	dPCBTemp_(0),
	nPCBTempCount_(0),
	dCCDTemp_(0),
	nCCDTempCount_(0),
	nCCDTempCalibrated_(0),
	FrameAccumulate_(1),
	FrameCount_(0),
	fieldCount_(-1),
	fieldCount1_(-1),
	fieldCount2_(-1),
	bSuspend_(true),
	triggerTimeout_(5),
	liveMode_(0),
	trigSnap_(0),
	nDebayerMethod_(2),
	frameInterval_(0.0),
	nSerialBlock_(0),
	nCapturing_(0),
	UNITS(1),
	UNITMASK(1),
	UNITSOPENMAP(1),
	UNITSMAP(1),
	bHorizontalFlip_(true),
	bInvertVideo_(false),
	bBadPixel_(false),
	bImageSharpen_(false),
	nShutterMode_(0),
	bHighPreAmpGain_(true),
	dShutterDelayOpen_(19.66),
	dShutterDelayClose_(49.15),
	readoutMode_(0)
{

	cameraType_ = nCameraType;
	switch(nCameraType)
	{
		case _RAPTOR_CAMERA_KITE:   
			cameraName_ = g_RaptorKITE;
			cameraDeviceName_ = g_RaptorCameraKITEDeviceName; break;
		case _RAPTOR_CAMERA_OWL_320:    
			cameraName_ = g_RaptorOwl320;
			cameraDeviceName_ = g_RaptorCameraOwl320DeviceName; break;
		case _RAPTOR_CAMERA_OWL_640:    
			cameraName_ = g_RaptorOwl640;
			cameraDeviceName_ = g_RaptorCameraOwl320DeviceName; break;
		case _RAPTOR_CAMERA_OWL_NINOX_640:    
			cameraName_ = g_RaptorNinox640;
			cameraDeviceName_ = g_RaptorCameraOwlNinox640DeviceName; break;
		case _RAPTOR_CAMERA_EAGLE:    
			cameraName_ = g_RaptorEagle;
			cameraDeviceName_ = g_RaptorCameraEagleDeviceName; break;
		case _RAPTOR_CAMERA_FALCON: 
			cameraName_ = g_RaptorFalcon;
			cameraDeviceName_ = g_RaptorCameraFalconDeviceName; break;
		case _RAPTOR_CAMERA_OSPREY: 
			cameraName_ = g_RaptorOsprey;
			cameraDeviceName_ = g_RaptorCameraOspreyDeviceName; break;
		case _RAPTOR_CAMERA_OSPREY_RGB: 
			cameraName_ = g_RaptorOspreyRGB;
			cameraDeviceName_ = g_RaptorCameraOspreyRGBDeviceName; break;

		case _RAPTOR_CAMERA_KINGFISHER_674: 
			cameraName_ = g_RaptorKingfisher674;
			cameraDeviceName_ = g_RaptorCameraKingfisher674DeviceName; break;
		case _RAPTOR_CAMERA_KINGFISHER_694: 
			cameraName_ = g_RaptorKingfisher694;
			cameraDeviceName_ = g_RaptorCameraKingfisher694DeviceName; break;
		case _RAPTOR_CAMERA_KINGFISHER_674_RGB: 
			cameraName_ = g_RaptorKingfisher674RGB;
			cameraDeviceName_ = g_RaptorCameraKingfisher674RGBDeviceName; break;
		case _RAPTOR_CAMERA_KINGFISHER_694_RGB: 
			cameraName_ = g_RaptorKingfisher694RGB;
			cameraDeviceName_ = g_RaptorCameraKingfisher694RGBDeviceName; break;

/*		case _RAPTOR_CAMERA_CYGNET: 
			cameraName_ = g_RaptorCygnet;
			cameraDeviceName_ = g_RaptorCameraCygnetDeviceName; break;
		case _RAPTOR_CAMERA_CYGNET_RGB: 
			cameraName_ = g_RaptorCygnetRGB;
			cameraDeviceName_ = g_RaptorCameraCygnetRGBDeviceName; break;
		case _RAPTOR_CAMERA_UNKNOWN1: 
			cameraName_ = g_RaptorUnknown1;
			cameraDeviceName_ = g_RaptorCameraUnknown1DeviceName; break;
		case _RAPTOR_CAMERA_UNKNOWN1_RGB: 
			cameraName_ = g_RaptorUnknown1RGB;
			cameraDeviceName_ = g_RaptorCameraUnknown1RGBDeviceName; break;
*/	}

   memset(testProperty_,0,sizeof(testProperty_));

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
   myReadoutStartTime_ = myClock();

	UNITS=1;
	UNITMASK=1;
	UNITSOPENMAP=1;
	UNITSMAP=1;
	MULTIUNITMASK=1;
	
   CPropertyAction* pAct = new CPropertyAction (this, &CRaptorEPIX::OnEPIXUnit);
   CreateProperty(g_Keyword_EPIXUnit, "1", MM::Integer, false, pAct, true);
  
   vector<string> EPIXUnits;
   EPIXUnits.push_back("1");
   EPIXUnits.push_back("2");
   EPIXUnits.push_back("3");
   EPIXUnits.push_back("4");
   int nRet = SetAllowedValues(g_Keyword_EPIXUnit, EPIXUnits);

   pAct = new CPropertyAction (this, &CRaptorEPIX::OnEPIXMultiUnitMask);
   CreateProperty(g_Keyword_EPIXMultiUnitMask, "1", MM::Integer, false, pAct, true);
   nRet = SetAllowedValues(g_Keyword_EPIXMultiUnitMask, EPIXUnits);

   pDemoResourceLock_ = new MMThreadLock();
   thd_ = new MySequenceThread(this);
}

/**
* CRaptorEPIX destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CRaptorEPIX::~CRaptorEPIX()
{
	StopSequenceAcquisition();
	delete thd_;
	delete pDemoResourceLock_;
	if (initialized_)
	{
		pxd_PIXCIclose();
		g_PIXCI_DriverLoaded = 0;
	}
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CRaptorEPIX::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
	CDeviceUtils::CopyLimitedString(name, cameraDeviceName_);
}

double CRaptorEPIX::GetMicroVersion() const
{
	// get micro version
	unsigned char bufin[] = {0x56, 0x50};
	unsigned char buf[256];
	int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  2, buf, 256 );
	int v1, v2;

	double dValue = 0.0;
	if(ret>2)
	{
		v1 = buf[ret-3];
		v2 = buf[ret-2];
		dValue = double(v1) + double(v2)/10.0;
	}

	return dValue; 
}
void CRaptorEPIX::SetMicroReset() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	// get micro version
	int loop=0;

	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0 || (cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
	{
		unsigned char bufin[] = {0x55, 0x99, 0x66, 0x11, 0x50, 0xEB};
		unsigned char bufin2[] = {0x4F, 0x11, 0x50, 0x0E};		
		unsigned char buf[256];
		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin, 6, buf, 256 );

		int ret=0, loop=0;
		bool bBooted=false;
		do
		{
			Sleep(500);
			ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2, 4, buf, 256 );
			if(ret>0 && buf[0]==0x50)
				bBooted = true;
			loop++;
		}
		while(!bBooted && loop<10);

		SetSystemState(0x12);
		unsigned char nState;
		nState = GetSystemState();
		loop=0;
		while((nState==0xFF || nState!=0x16) && loop<20)
		{
			Sleep(500);
			nState = GetSystemState();
			loop++;
		}
	}
	else
	{
		unsigned char bufin[] = {0x55, 0x50};
		unsigned char buf[256];
		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin, 2, buf, 256 );

		loop=0;
		while(SetSystemState(0x12)==0 && loop<10)
		{
			Sleep(1000);
			loop++;
		}
	}

	DisableMicro();
	thd_->Resume();
}

double CRaptorEPIX::GetFPGAVersion() const
{
	thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char v1=0, v2=0;
	double dValue;

	serialReadRaptorRegister1(UNITMASK, 0x7E, &v1 ) ;
	serialReadRaptorRegister1(UNITMASK, 0x7F, &v2 ) ;

	DisableMicro(); thd_->Resume();

	dValue = double(v1) ;
	if(v2<10)
		dValue += double(v2)/10.0;
	else if(v2<100) 
		dValue += double(v2)/100.0;
	else 
	    dValue += double(v2)/1000.0;
	return dValue;
}

int CRaptorEPIX::ConvertTECTempToValue(double dTemp) const
{
	int nValue=-1;

	if(EPROM_DAC_Cal_0C_>0 && EPROM_DAC_Cal_40C_>0)
	{
		double m, c;
		m = ((double(EPROM_DAC_Cal_40C_) - double(EPROM_DAC_Cal_0C_)))/40.0;
		c = EPROM_DAC_Cal_0C_;
		nValue = (unsigned int)(dTemp*m + c);
	}
	return nValue;
}

double CRaptorEPIX::ConvertTECValueToTemp(int nValue) const
{
	double dValue = -999;
	if(EPROM_DAC_Cal_0C_>0 && EPROM_DAC_Cal_40C_>0)
	{
		double m, c;
		if(EPROM_DAC_Cal_40C_ - EPROM_DAC_Cal_0C_ == 0)
			m = 0.0;
		else
			m = 40.0 / ((double(EPROM_DAC_Cal_40C_) - double(EPROM_DAC_Cal_0C_)));
		c = - m*double(EPROM_DAC_Cal_0C_);
		dValue = m*double(nValue) + c;
		dValue = floor(dValue*10+0.5)/10.0;
	}
	return dValue;
}

double CRaptorEPIX::GetTECSetPoint() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	double dValue=-999.0;

   if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) > 0)
   {
		unsigned int nValue=0; 
		unsigned char v1=0, v2=0;

		serialReadRaptorRegister1(UNITMASK, 0x03, &v1 ) ;
		serialReadRaptorRegister1(UNITMASK, 0x04, &v2 ) ;
 
		DisableMicro(); thd_->Resume();

		nValue = (((int)v1&0x0F)<<8) + ((int)v2) ;

		dValue = ConvertTECValueToTemp(nValue);

   }
   else if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) > 0 || (cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
   {
		unsigned int nValue=0; 
		unsigned char v1=0, v2=0;

		serialReadRaptorRegister1(UNITMASK, 0xFB, &v1 ) ;
		serialReadRaptorRegister1(UNITMASK, 0xFA, &v2 ) ;
 
		DisableMicro(); thd_->Resume();

		nValue = (((int)v1&0x0F)<<8) + ((int)v2) ;

		dValue = ConvertTECValueToTemp(nValue);

   }
   else
   {

		unsigned char bufin1[] = {0x53, 0x98, 0x01, 0x02, 0x50};
		unsigned char bufin2[] = {0x53, 0x99, 0x02, 0x50};
		unsigned char buf[256];
		unsigned int nValue=0; 

		int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  5, buf, 256 );
			ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  4, buf, 256 );
 
		DisableMicro(); thd_->Resume();

		if(ret>2)
		{
			nValue = ((int)buf[ret-2]) + (((int)buf[ret-3])<<8) ;
			nValue = nValue >> 4;

			dValue = ConvertTECValueToTemp(nValue);

		}
   }
	return dValue;
}

void CRaptorEPIX::SetTECSetPoint(double dValue) 
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

   if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) > 0)
   {
		unsigned char bufin1[] = {0x53, 0xE0, 0x02, 0x03, 0x00, 0x50};
		unsigned char bufin2[] = {0x53, 0xE0, 0x02, 0x04, 0x00, 0x50};
		unsigned char buf[256];
		unsigned int nValue=0;

		if(EPROM_DAC_Cal_0C_>0 && EPROM_DAC_Cal_40C_>0)
			nValue = ConvertTECTempToValue(dValue);
		else
			return;

		if(nValue<0 || nValue > 0x0FFF)
			return;

		bufin1[4] = (unsigned char)((nValue&0x0F00)>>8);
		bufin2[4] = (unsigned char)((nValue&0x00FF));

		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1, 6, buf, 256 );
		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2, 6, buf, 256 );

   }   
   else if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) > 0  || (cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
   {
		unsigned char bufin1[] = {0x53, 0xE0, 0x02, 0xFB, 0x00, 0x50};
		unsigned char bufin2[] = {0x53, 0xE0, 0x02, 0xFA, 0x00, 0x50};
		unsigned char buf[256];
		unsigned int nValue=0;

		if(EPROM_DAC_Cal_0C_>0 && EPROM_DAC_Cal_40C_>0)
			nValue = ConvertTECTempToValue(dValue);
		else
			return;

		if(nValue<0 || nValue > 0x0FFF)
			return;

		bufin1[4] = (unsigned char)((nValue&0x0F00)>>8);
		bufin2[4] = (unsigned char)((nValue&0x00FF));

		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1, 6, buf, 256 );
		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2, 6, buf, 256 );

   }
   else
   {
		unsigned char bufin1[] = {0x53, 0x98, 0x03, 0x22, 0x00, 0x00, 0x50};
		unsigned char buf[256];
		unsigned int nValue=0;

		if(EPROM_DAC_Cal_0C_>0 && EPROM_DAC_Cal_40C_>0)
			nValue = ConvertTECTempToValue(dValue);
		else
			return;

		if(nValue<0 || nValue > 0x0FFF)
			return;


		bufin1[4] = (unsigned char)((nValue&0x0FF0)>>4);
		bufin1[5] = (unsigned char)((nValue&0x000F))<<4;

		serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  7, buf, 256 );
   }
	DisableMicro(); thd_->Resume();
}


unsigned long long CRaptorEPIX::Convert4UCharToULong(unsigned char* val) const
{
	unsigned long long lValue = 0;
	lValue += (unsigned long long)val[3];
	lValue += ((unsigned long long)val[2])<<8;
	lValue += ((unsigned long long)val[1])<<16;
	lValue += ((unsigned long long)val[0])<<24;

	return lValue;
}
unsigned long long CRaptorEPIX::Convert5UCharToULong(unsigned char* val) const
{
	unsigned long long lValue = 0;
	lValue += (unsigned long long)val[4];
	lValue += ((unsigned long long)val[3])<<8;
	lValue += ((unsigned long long)val[2])<<16;
	lValue += ((unsigned long long)val[1])<<24;
	lValue += ((unsigned long long)val[0])<<32;

	return lValue;
}

double CRaptorEPIX::GetTrigDelay() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val[] = {0,0,0,0};
	unsigned long long lValue = 0;
	double dValue = 0.0;

	serialReadRaptorRegister1(UNITMASK, 0xE9, &val[0] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xEA, &val[1] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xEB, &val[2] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xEC, &val[3] ) ;

	DisableMicro(); thd_->Resume();

	lValue = Convert4UCharToULong(&val[0]);

	if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		dValue = ((double)lValue) / 40e3;
	else
		dValue = ((double)lValue) / 160e3;

	return dValue;

}

void CRaptorEPIX::SetTrigDelay(double dVal) 
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val[] = {0,0,0,0};
	unsigned long lValue = 0;
	double dValue = 0.0;
	if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		dValue = double(dVal/1000.0) * 40e6;
	else
		dValue = double(dVal/1000.0) * 160e6;
	lValue = (unsigned long)dValue;

	val[0] = (unsigned char)((lValue&0xFF000000)>>24);
	val[1] = (unsigned char)((lValue&0x00FF0000)>>16);
	val[2] = (unsigned char)((lValue&0x0000FF00)>>8);
	val[3] = (unsigned char)((lValue&0x000000FF));

	serialWriteRaptorRegister1(UNITMASK, 0xE9, val[0] ) ;
	serialWriteRaptorRegister1(UNITMASK, 0xEA, val[1] ) ;
	serialWriteRaptorRegister1(UNITMASK, 0xEB, val[2] ) ;
	serialWriteRaptorRegister1(UNITMASK, 0xEC, val[3] ) ;

	DisableMicro(); thd_->Resume();
}




int CRaptorEPIX::DisableMicro() const
{
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 && (nSerialBlock_==0))
	{
		unsigned char bufin[] = {0x54, 0x50};
		unsigned char buf[256];

		return serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  2, buf, 256 );
	}
	else
		return 0;
}

int CRaptorEPIX::SetSystemState(unsigned char nState) const
{
	unsigned char bufin[] = {0x4F, 0x00, 0x50, 0x00};
	unsigned char buf[256];
	if(g_bCheckSum)
		bufin[1] = nState | 0x40;
	else
		bufin[1] = nState ;

	bufin[3] = bufin[0] ^ bufin[1] ^ bufin[2] ;

	return serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  4, buf, 256, false );
	
}

unsigned char CRaptorEPIX::GetSystemState() const
{
	unsigned char bufin[] = {0x49, 0x50};
	unsigned char buf[256];
	unsigned char nState;

	int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  2, buf, 256 );

	nState = 0xFF;

	if(ret>1)
		nState = buf[ret-2];

	if(g_bCheckSum)
		nState &= 0xBF;

	return nState;
}


int CRaptorEPIX::GetSerialNumber() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	SetSystemState(0x13);
	unsigned char buf[256];
	int nValue;

	// get serial number
	unsigned char bufin1[] = {0x53, 0xAE, 0x05, 0x01, 0x00, 0x00, 0x02, 0x00, 0x50};
	unsigned char bufin2[] = {0x53, 0xAF, 0x02, 0x50}; 
	int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  9, buf, 256 );
	ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  4, buf, 256 );

	nValue = -1;
	if(ret>2)
		nValue = (int(buf[ret-2])<<8) + buf[ret-3];
	else if(ret==2)
		nValue = (int(buf[1])<<8) + buf[0];

	SetSystemState(0x12);

	DisableMicro(); thd_->Resume();
	return nValue;
}

int CRaptorEPIX::SetVideoFormat(int cameraType_, char* driverparms)
{
#ifdef _MSC_VER
#pragma warning (push)
// Suppress warnings triggered by "-2147483648" in the #included *.fmt files
// (an MSVC quirk)
#pragma warning (disable: 4146)
#endif

	int ret=-1;
	 
	//ret = pxd_PIXCIopen(driverparms, "Default", NULL); 
	struct stat fileStat;

	if(cameraType_ == _RAPTOR_CAMERA_KITE)
	{
		if(stat(FORMATFILE_LOAD_KITE,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_KITE);		
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{
				ret = 0;
				#include FORMATFILE_LOAD_KITE
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(cameraType_ == _RAPTOR_CAMERA_OWL_320)
	{
		if(stat(FORMATFILE_LOAD_OWL_320,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_OWL_320);
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{ 
				ret = 0;
				#include FORMATFILE_LOAD_OWL_320
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(cameraType_ == _RAPTOR_CAMERA_OWL_640)
	{
		if(stat(FORMATFILE_LOAD_OWL_640,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_OWL_640);
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{ 
				ret = 0;
				#include FORMATFILE_LOAD_OWL_640
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640)
	{
		if(stat(FORMATFILE_LOAD_OWL_NINOX_640,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_OWL_NINOX_640);
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{ 
				ret = 0;
				#include FORMATFILE_LOAD_OWL_NINOX_640
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(cameraType_ == _RAPTOR_CAMERA_EAGLE)
	{
		if(stat(FORMATFILE_LOAD_EAGLE,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_EAGLE);
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{ 
				ret = 0;
				#include FORMATFILE_LOAD_EAGLE
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
	{
		if(stat(FORMATFILE_LOAD_FALCON,&fileStat) >= 0) 
			ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_FALCON);
		if(ret<0)
		{
			ret = pxd_PIXCIopen(driverparms, "Default", "");
			if (ret < 0)
			{
				pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			}
			//else
			if(ret!=-24)
			{
				ret = 0;
				#include FORMATFILE_LOAD_FALCON
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
		}
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0)
	{
		// *************************************************//
		if(cameraType_ == _RAPTOR_CAMERA_OSPREY)
		{
			if(stat(FORMATFILE_LOAD_OSPREY,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_OSPREY);
			if(ret<0)
			{
				ret = 0;
				if(g_PIXCI_DriverLoaded==0)
					ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				else
					g_PIXCI_DriverLoaded = 1;
				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_OSPREY
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
		else if(cameraType_ == _RAPTOR_CAMERA_OSPREY_RGB)
		{
			if(stat(FORMATFILE_LOAD_OSPREY_RGB,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_OSPREY_RGB);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_OSPREY_RGB
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
		else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674)
		{
			if(stat(FORMATFILE_LOAD_KINGFISHER674,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_KINGFISHER674);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_KINGFISHER674
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
		else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674_RGB)
		{
			if(stat(FORMATFILE_LOAD_KINGFISHER674_RGB,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_KINGFISHER674_RGB);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_KINGFISHER674_RGB
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
		else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694)
		{
			if(stat(FORMATFILE_LOAD_KINGFISHER694,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_KINGFISHER694);
			if(ret<0)
			{
				ret = 0;
				if(g_PIXCI_DriverLoaded==0)
					ret = pxd_PIXCIopen(driverparms, "Default", "");

				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				else
					g_PIXCI_DriverLoaded = 1;

				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_KINGFISHER694
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
		else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694_RGB)
		{
			if(stat(FORMATFILE_LOAD_KINGFISHER694_RGB,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_KINGFISHER694_RGB);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
				{
					pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
					MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
				}
				//else
				if(ret!=-24)
				{
					ret = 0;
					#include FORMATFILE_LOAD_KINGFISHER694_RGB
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
		// *************************************************//
/*		else if(cameraType_ == _RAPTOR_CAMERA_CYGNET)
		{
			if(stat(FORMATFILE_LOAD_CYGNET,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_CYGNET);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
					pxd_mesgFault(UNITSMAP);
				//else
				{
					ret = 0;
					#include FORMATFILE_LOAD_CYGNET
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
*/		// *************************************************//
/*		else if(cameraType_ == _RAPTOR_CAMERA_CYGNET_RGB)
		{
			if(stat(FORMATFILE_LOAD_CYGNET_RGB,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_CYGNET_RGB);
			if(ret<0)
			{
				ret = pxd_PIXCIopen(driverparms, "Default", "");
				if (ret < 0)
					pxd_mesgFault(UNITSMAP);
				//else
				{
					ret = 0;
					#include FORMATFILE_LOAD_CYGNET_RGB
					pxd_videoFormatAsIncludedInit(0);
					ret = pxd_videoFormatAsIncluded(0);
				}
			}
		}
*/		// *************************************************//
/*		else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1)
		{
			if(stat(FORMATFILE_LOAD_UNKNOWN1,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_UNKNOWN1);
		}
*/		// *************************************************//
/*		else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1_RGB)
		{
			if(stat(FORMATFILE_LOAD_UNKNOWN1_RGB,&fileStat) >= 0) 
				ret = pxd_PIXCIopen(driverparms, "", FORMATFILE_LOAD_UNKNOWN1_RGB);
		}
*/	}
	DisableMicro();
	return ret;

#ifdef _MSC_VER
#pragma warning (pop)
#endif
}

/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int CRaptorEPIX::Initialize()
{
	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	gClockZero[UNITSOPENMAP] = myClock();

	double d1,d2,d3;

	d1 = myClock();
	Sleep(30);
	d2 = myClock();
	d3 = 1000.0*(d2 - d1);
 
   if (initialized_)
      return DEVICE_OK;

	//
	// Open the PIXCI(R) imaging board.
	// If this program were to only support a single PIXCI(R)
	// imaging board, the first parameter could be simplified to:
	//
	//	if (pxd_PIXCIopen("", FORMAT, NULL) < 0)
	//	    pxd__mesgFault(1);
	//
	// But, for the sake of multiple PIXCI(R) imaging boards
	// specify which units are to be used.
	//

   UNITMASK = 1 << (UNITSOPENMAP-1);

   int mmask = 1;
   if(MULTIUNITMASK==1)
	   mmask = 1 << (UNITSOPENMAP-1);
   else if(MULTIUNITMASK==2)
	   mmask = 3;
   else if(MULTIUNITMASK==3)
	   mmask = 7;
   else if(MULTIUNITMASK==4)
	   mmask = 15;

   if((mmask & UNITMASK) == 0)
	   mmask |= UNITMASK;
   
	char driverparms[80];
	driverparms[sizeof(driverparms)-1] = 0; // this & snprintf: overly conservative - avoids warning messages
	sprintf_s(driverparms, sizeof(driverparms)-1, "-DM 0x%x %s", mmask, DRIVERPARMS);

	if(mmask == UNITMASK)
		UNITMASK = 1;
	else
		UNITMASK = UNITSOPENMAP;
	UNITSMAP = UNITMASK;

	int ret=-1;

/*	ret = pxd_PIXCIopen(driverparms, "Default", "");
	if (ret < 0)
	{
		pxd_PIXCIclose();
		ret = pxd_PIXCIopen(driverparms, "Default", "");
	}
	pxd_PIXCIclose();
*/	

	ret = SetVideoFormat(cameraType_, driverparms);

    if (ret < 0 && 0)
	{
		pxd_PIXCIclose();
		UNITSOPENMAP=2;
		sprintf_s(driverparms, sizeof(driverparms)-1, "-DM 0x%x %s", UNITSOPENMAP, DRIVERPARMS);
		ret = SetVideoFormat(cameraType_, driverparms);

		if (ret < 0)
		{
			UNITSOPENMAP=1;
			pxd_mesgFault(UNITSMAP);
#if defined (WIN32) || defined(WIN64)		
			MessageBox(NULL, pxd_mesgErrorCode(ret), "pxd_PIXCIopen", MB_OK|MB_TASKMODAL);
#endif
			pxd_PIXCIclose();
			return DEVICE_NOT_CONNECTED;
		}
	}

	if(_IS_CAMERA_OWL_FAMILY)
	{
		g_Keyword_AOI_Left    = g_Keyword_AGC_AOI_Left;
		g_Keyword_AOI_Top     = g_Keyword_AGC_AOI_Top;
		g_Keyword_AOI_Width   = g_Keyword_AGC_AOI_Width;
		g_Keyword_AOI_Height  = g_Keyword_AGC_AOI_Height;
	}

    if (ret < 0)
	{
		pxd_mesgFault(UNITSMAP);
		pxd_PIXCIclose();
		g_PIXCI_DriverLoaded = 0;
		return DEVICE_NOT_CONNECTED;
	}

	cameraCCDXSize_ = pxd_imageXdim();
	cameraCCDYSize_ = pxd_imageYdim();
	nBPP_ = 2;
    img2_.Resize(cameraCCDXSize_, cameraCCDYSize_, nBPP_);

	if((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0)
	    img3_.Resize(cameraCCDXSize_, cameraCCDYSize_, 4*nBPP_);
	else
	    img3_.Resize(cameraCCDXSize_, cameraCCDYSize_, 2*nBPP_); 

	ret = pxd_serialConfigure(UNITSMAP, 0, 115200, 8, 0, 1, 0, 0, 0);

	if(ret==0)
	{
		serialOK_ = true;
		g_SerialOK = true;
	}

	nSerialBlock_ = 1;

	char profilepath[1000];
	char strLog[1024];
#if defined (WIN32) || defined(WIN64)
	ExpandEnvironmentStrings("%userprofile%",profilepath,1000);
	sprintf_s(strLog,1024,"%s\\%sEPIX_Serial_Log_Unit%d.%d.txt",profilepath,RAPTOR,UNITSOPENMAP,GetCurrentProcessId());

#else
	sprintf_s(strLog,1024,"/tmp/%sEPIX_Serial_Log_Unit%d.%d.txt",RAPTOR,UNITSOPENMAP,getpid());
#endif
	fidSerial[UNITSOPENMAP] = fopen(strLog,"a");
	//fidSerial = 0;

	if(fidSerial[UNITSOPENMAP])
		gUseSerialLog[UNITSOPENMAP] = 1;
	else
		gUseSerialLog[UNITSOPENMAP] = 0;

	time_t rawtime;
	time ( &rawtime );
	if(fidSerial[UNITSOPENMAP]) 
		fprintf(fidSerial[UNITSOPENMAP], "Time: %s\n\n", ctime (&rawtime) );

	double dMicroVersion, dFPGAVersion;
	int nSerial, nBinning;
	int OWLVideoPeak=0, OWLVideoAvg=0;
 
	SetSystemState(0x12);

	ret = GetSystemState();

	dMicroVersion = GetMicroVersion();
	dFPGAVersion  = GetFPGAVersion();

	if(dMicroVersion==0.0 && dFPGAVersion==0.0)
	{
		pxd_PIXCIclose();
		g_PIXCI_DriverLoaded = 0;
		return DEVICE_NOT_CONNECTED;
	}

	nSerial       = GetSerialNumber();
	
	SetExtTrigStatus(0); 
	SetLiveVideo(0);

	if(_IS_CAMERA_OWL_FAMILY || (((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0))
	{
		GetEPROMManuData();
	}
 
	dPCBTemp_	  = GetPCBTemp();
	dCCDTemp_	  = GetCCDTemp();
	nBinning	  = GetBinningFactor();

	if(_IS_CAMERA_OWL_FAMILY)
	{
		TECSetPoint_ = GetTECSetPoint();
		OWLNUCState_	= GetNUCState();
		OWLPeakAvgLevel_= GetPeakAvgLevel();
		int nExp, nAGC;
		GetAGCExpSpeed(&nAGC, &nExp);
		OWLAGCSpeed_ = nAGC;
  	    OWLExpSpeed_ = nExp;
		OWLROIAppearance_ = GetROIAppearance();
		OWLAutoExp_		= GetAutoExposure();
		OWLAutoLevel_	= GetAutoLevel();
		OWLHighGain_	= GetHighGain();
		OWLVideoPeak	= GetVideoPeak();
		OWLVideoAvg		= GetVideoAvg();
		OWLTrigDelay_	= GetTrigDelay();
		OWLFrameRate_   = GetFrameRate();
		OWLBlackOffset_ = GetBlackOffset();
	}

	SetROI(0, 0, cameraCCDXSize_, cameraCCDYSize_);


   // set property list
   // -----------------

   // Name

   int nRet = CreateProperty(MM::g_Keyword_Name, cameraDeviceName_, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet; 

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, RAPTOR "EPIX Device Adapter", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   //nRet = CreateProperty("Device Adapter", "v1.9.3, 12/18/2014, (OWL 640)", MM::String, true); 
   nRet = CreateProperty("Device Adapter", "v1.12.7, 9/27/2015", MM::String, true); 
   if (DEVICE_OK != nRet)
      return nRet;
 
   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, cameraName_, MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   std::ostringstream osSerial, osMicroVersion, osFPGAVersion, osBin, osPCBTemp, osCCDTemp, osExpSpeed, osAGCSpeed, osPkAvg, osAutoLevel, osVideoPeak, osVideoAvg, osTrigDelay, osFrameRate, osBlackOffset, osModel, osUnit;
   osSerial << nSerial;
   osMicroVersion << dMicroVersion;
   osFPGAVersion  << dFPGAVersion;
   osBin << nBinning;
   osPCBTemp << dPCBTemp_;
   osCCDTemp << dCCDTemp_;

   osExpSpeed << OWLExpSpeed_;
   osAGCSpeed << OWLAGCSpeed_;
   osPkAvg << OWLPeakAvgLevel_;
   osAutoLevel << OWLAutoLevel_;
   osVideoPeak << OWLVideoPeak;
   osVideoAvg << OWLVideoAvg;
   osTrigDelay << OWLTrigDelay_;
   osFrameRate << OWLFrameRate_;
   osBlackOffset << OWLBlackOffset_;
   osUnit << UNITSOPENMAP;

   nRet = CreateProperty(MM::g_Keyword_CameraID, osSerial.str().c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);

   // MicroVersion
   nRet = CreateProperty("Micro Version", osMicroVersion.str().c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);

   // FPGA Version
   nRet = CreateProperty("FPGA Version", osFPGAVersion.str().c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);

   nRet = CreateProperty("EPIX Driver", pxd_infoDriverId(), MM::String, true);
   assert(nRet == DEVICE_OK);

   //nRet = CreateProperty("EPIX Include", pxd_infoIncludeId(), MM::String, true);
   //assert(nRet == DEVICE_OK);

   nRet = CreateProperty("EPIX Library", pxd_infoLibraryId(), MM::String, true);
   assert(nRet == DEVICE_OK);

	switch(pxd_infoModel(UNITMASK))
	{
		case PIXCI_A:		  osModel << "PIXCI(R) A Imaging Board"; break ;
		case PIXCI_A310:      osModel << "PIXCI(R) A310 Imaging Board"; break ;
		case PIXCI_CL1:       osModel << "PIXCI(R) CL1 Imaging Board"; break ;
		case PIXCI_CL2:       osModel << "PIXCI(R) CL2 Imaging Board"; break ;
		case PIXCI_CL3SD:     osModel << "PIXCI(R) CL3SD Imaging Board"; break ;
		case PIXCI_D:         osModel << "PIXCI(R) D, D24, D32 Imaging Board"; break ;
		case PIXCI_D24:       osModel << "PIXCI(R) D24 Imaging Board"; break ;
		case PIXCI_D32:       osModel << "PIXCI(R) D32 Imaging Board"; break ;
		case PIXCI_D2X:       osModel << "PIXCI(R) D2X Imaging Board"; break ;
		case PIXCI_D3X:       osModel << "PIXCI(R) D3X Imaging Board"; break ;
		case PIXCI_D3XE:      osModel << "PIXCI® D3XE Frame Grabber"; break ;
		case PIXCI_E1:        osModel << "PIXCI(R) E1 Imaging Board"; break ;
		case PIXCI_E1DB:      osModel << "PIXCI(R) E1DB Imaging Board"; break ;
		case PIXCI_E4:        osModel << "PIXCI(R) E4 Imaging Board"; break ;
		case PIXCI_E4DB:      osModel << "PIXCI(R) E4DB Imaging Board"; break ;
		case PIXCI_E8:        osModel << "PIXCI(R) E8 Imaging Board"; break ;
		case PIXCI_E8CAM:     osModel << "PIXCI(R) E8CAM Imaging Board"; break ;
		case PIXCI_E8DB:      osModel << "PIXCI(R) E8DB Imaging Board"; break ;
		case PIXCI_EB1:       osModel << "PIXCI(R) EB1 Imaging Board"; break ;
		case PIXCI_EB1POCL:   osModel << "PIXCI(R) EB1-PoCL Imaging Board"; break ;
		case PIXCI_EC1:       osModel << "PIXCI(R) EC1 Imaging Board"; break ;
		case PIXCI_ECB1:      osModel << "PIXCI(R) ECB1 Imaging Board"; break ;
		case PIXCI_ECB134:    osModel << "PIXCI(R) ECB1-34 Imaging Board"; break ;
		case PIXCI_ECB2:      osModel << "PIXCI(R) ECB2 Imaging Board"; break ;
		case PIXCI_EL1:       osModel << "PIXCI(R) EL1 Imaging Board"; break ;
		case PIXCI_EL1DB:     osModel << "PIXCI(R) EL1DB Imaging Board"; break ;
		case PIXCI_ELS2:      osModel << "PIXCI(R) ELS2 Imaging Board"; break ;
		case PIXCI_SI:        osModel << "PIXCI(R) SI Imaging Board"; break ;
		case PIXCI_SI2:       osModel << "PIXCI(R) SI2 Imaging Board"; break ;
		case PIXCI_SI4:       osModel << "PIXCI(R) SI4 Imaging Board"; break ;
		case PIXCI_SI1:       osModel << "PIXCI(R) SI1 Imaging Board"; break ;
		case PIXCI_SV2:       osModel << "PIXCI(R) SV2 Imaging Board"; break ;
		case PIXCI_SV3:       osModel << "PIXCI(R) SV3 Imaging Board"; break ;
		case PIXCI_SV4:       osModel << "PIXCI(R) SV4 Imaging Board"; break ;
		case PIXCI_SV5:       osModel << "PIXCI(R) SV5, SV5A, SV5B, SV5L Imaging Board"; break ;
		case PIXCI_SV7:       osModel << "PIXCI(R) SV7 Imaging Board"; break ;
		case PIXCI_SV8:       osModel << "PIXCI(R) SV8 Imaging Board"; break ;
		
		default:	 		  osModel << "Unknown"; break;
	}


   nRet = CreateProperty("EPIX Model", osModel.str().c_str(), MM::String, true);
   assert(nRet == DEVICE_OK);



   // PCB Temp
   CPropertyAction *pAct = new CPropertyAction (this, &CRaptorEPIX::OnPCBTemp);
   nRet = CreateProperty(g_Keyword_PCBTemp, osPCBTemp.str().c_str(), MM::Float, true, pAct);
   assert(nRet == DEVICE_OK);

   // CCD Temp
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnCCDTemp);
   if(_IS_CAMERA_OWL_FAMILY )
	    nRet = CreateProperty(g_Keyword_SensorTemp, osCCDTemp.str().c_str(), MM::Float, true, pAct);
   else
		nRet = CreateProperty(g_Keyword_CCDTemp, osCCDTemp.str().c_str(), MM::String, true, pAct);
   assert(nRet == DEVICE_OK);

   // binning
   if(_NOT_CAMERA_OWL_FAMILY )
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnBinning);
	   nRet = CreateProperty(MM::g_Keyword_Binning, osBin.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   vector<string> binning;
	   if((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0)
	   {
		   binning.push_back("1");
		   binning.push_back("2");
		   binning.push_back("4");
		   binning.push_back("6");
		   binning.push_back("8");
		   binning.push_back("10");
		   binning.push_back("12");
		   binning.push_back("14");
		   binning.push_back("16");
	   }
	   else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674 || cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694  )
	   {
		   binning.push_back("1");
		   binning.push_back("2");
		   binning.push_back("3");
		   binning.push_back("4");
		   binning.push_back("5");
		   binning.push_back("6");
		   binning.push_back("7");
		   binning.push_back("8");
		   binning.push_back("9");
		   binning.push_back("10");
		   binning.push_back("11");
		   binning.push_back("12");
		   binning.push_back("13");
		   binning.push_back("14");
		   binning.push_back("15");
		   binning.push_back("16");
	   }
	   else if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
	   {
		   binning.push_back("1");
		   binning.push_back("2");
		   binning.push_back("4");
		   binning.push_back("8");
		   binning.push_back("16");
		   binning.push_back("32");
		   binning.push_back("64");
	   }
	   else if(cameraType_ == _RAPTOR_CAMERA_KITE || (((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0))
	   {
		   binning.push_back("1");
		   binning.push_back("2");
		   binning.push_back("4");
	   }
	   else
	   {
		   binning.push_back("1");
		   binning.push_back("2");
		   binning.push_back("3");
		   binning.push_back("4");
		   binning.push_back("5");
	   }

	   nRet = SetAllowedValues(MM::g_Keyword_Binning, binning);
	   if (nRet != DEVICE_OK)
		  return nRet;
	   SetProperty(MM::g_Keyword_Binning, osBin.str().c_str());
   }
   else
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnBinning);
	   nRet = CreateProperty(MM::g_Keyword_Binning, osBin.str().c_str(), MM::Integer, true, pAct);
	   assert(nRet == DEVICE_OK);
	   vector<string> binning;
	   binning.push_back("1");
	   nRet = SetAllowedValues(MM::g_Keyword_Binning, binning);
	   if (nRet != DEVICE_OK) 
		  return nRet;
	   SetProperty(MM::g_Keyword_Binning, osBin.str().c_str());

   }


   // pixel type
   vector<string> pixelTypeValues;
   if((cameraType_ & _RAPTOR_CAMERA_RGB)>0)
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnPixelType);
	   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);

	   pixelTypeValues.push_back(g_PixelType_16bit);
	   pixelTypeValues.push_back(g_PixelType_64bitRGB);

	   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	   if (nRet != DEVICE_OK)
		  return nRet;

		SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
   }
   else
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnPixelType);
	   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);

	   vector<string> pixelTypeValues;
	//   pixelTypeValues.push_back(g_PixelType_8bit);
	   pixelTypeValues.push_back(g_PixelType_16bit); 
	//	pixelTypeValues.push_back(g_PixelType_32bitRGB);
	//	pixelTypeValues.push_back(g_PixelType_64bitRGB);
	//  pixelTypeValues.push_back(::g_PixelType_32bit); 

	   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	   if (nRet != DEVICE_OK)
		  return nRet;

		SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
   }


   // Bit depth
   //pAct = new CPropertyAction (this, &CRaptorEPIX::OnBitDepth);
   if((((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0))
   {
	   if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 || (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		   nRet = CreateProperty("BitDepth", "16", MM::Integer, true);
	   else
		   nRet = CreateProperty("BitDepth", "12", MM::Integer, true);
   }
   else if(_IS_CAMERA_OWL_FAMILY)
	   nRet = CreateProperty("BitDepth", "14", MM::Integer, true);
   else
	   nRet = CreateProperty("BitDepth", "16", MM::Integer, true);
   assert(nRet == DEVICE_OK);

   //vector<string> bitDepths;
//   bitDepths.push_back("8");
//   bitDepths.push_back("10");
//   bitDepths.push_back("12");
//   bitDepths.push_back("14");
   //bitDepths.push_back("16");
//   bitDepths.push_back("32");
   //nRet = SetAllowedValues("BitDepth", bitDepths);
   //if (nRet != DEVICE_OK)
   //   return nRet;

   //SetProperty("BitDepth", "16");
   
	pAct = new CPropertyAction (this, &CRaptorEPIX::OnFrameAccumulate);
	nRet = CreateProperty(g_Keyword_FrameAccumulate, "1", MM::Integer, false, pAct);
	assert(nRet == DEVICE_OK);
	vector<string> frameAcc;
	frameAcc.push_back("1");
	frameAcc.push_back("2");
	frameAcc.push_back("4");
	frameAcc.push_back("8");
	frameAcc.push_back("16");
	nRet = SetAllowedValues(g_Keyword_FrameAccumulate, frameAcc);

	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0 && (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE))
	{
		pAct = new CPropertyAction (this, &CRaptorEPIX::OnReadoutRate);
		nRet = CreateProperty(g_Keyword_ReadoutRate2, "2000", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		vector<string> readoutRate;
		readoutRate.push_back("75");
		readoutRate.push_back("2000");
		nRet = SetAllowedValues(g_Keyword_ReadoutRate2, readoutRate);

		pAct = new CPropertyAction (this, &CRaptorEPIX::OnReadoutMode);
		nRet = CreateProperty(g_Keyword_ReadoutMode, g_Keyword_ReadoutMode_Normal, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		vector<string> readoutMode;
		readoutMode.push_back(g_Keyword_ReadoutMode_Normal);
		readoutMode.push_back(g_Keyword_ReadoutMode_TestPattern);
		nRet = SetAllowedValues(g_Keyword_ReadoutMode, readoutMode);

		readoutMode_ = 0x01;
 	    SetReadoutMode((unsigned char)readoutMode_) ;

	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0 && (cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE))
	{
		pAct = new CPropertyAction (this, &CRaptorEPIX::OnReadoutRate);
		nRet = CreateProperty(g_Keyword_ReadoutRate, "20", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		vector<string> readoutRate;
		readoutRate.push_back("1");
		readoutRate.push_back("5");
		readoutRate.push_back("20");
		nRet = SetAllowedValues(g_Keyword_ReadoutRate, readoutRate);

		pAct = new CPropertyAction (this, &CRaptorEPIX::OnReadoutMode);
		nRet = CreateProperty(g_Keyword_ReadoutMode, g_Keyword_ReadoutMode_CDS, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		vector<string> readoutMode;
		readoutMode.push_back(g_Keyword_ReadoutMode_Baseline);
		readoutMode.push_back(g_Keyword_ReadoutMode_CDS);
		readoutMode.push_back(g_Keyword_ReadoutMode_TestPattern);
		nRet = SetAllowedValues(g_Keyword_ReadoutMode, readoutMode);
	}

   // exposure
   double dExp = 0.0;
   dExp = GetExposure();
   std::ostringstream osExp, osGain, osCCDX, osCCDY;
   osExp << dExp;

   pAct = new CPropertyAction(this, &CRaptorEPIX::OnMaximumExposure);
   nRet = CreateProperty(g_Keyword_ExposureMax, "1000", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   //SetPropertyLimits(g_Keyword_ExposureMax, 100, 200000);

   pAct = new CPropertyAction(this, &CRaptorEPIX::OnTriggerTimeout);
   nRet = CreateProperty(g_Keyword_TriggerTimeout, "5", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   pAct = new CPropertyAction(this, &CRaptorEPIX::OnUseSerialLog);
   nRet = CreateProperty(g_Keyword_UseSerialLog, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
	vector<string> yesNo;
	yesNo.push_back("0");
	yesNo.push_back("1");
	nRet = SetAllowedValues(g_Keyword_UseSerialLog, yesNo);

   pAct = new CPropertyAction(this, &CRaptorEPIX::OnFrameInterval);
   nRet = CreateProperty(g_Keyword_FrameInterval, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   pAct = new CPropertyAction (this, &CRaptorEPIX::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, osExp.str().c_str(), MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Exposure, 0, 1000);

   nRet = CreateProperty(g_Keyword_EPIXUnit2, osUnit.str().c_str(), MM::Integer, true);
   assert(nRet == DEVICE_OK);


	//CPropertyActionEx *pActX = 0;
	// create an extended (i.e. array) properties 1 through 4
	
/*    std::string propName = "EM Gain";
	pActX = new CPropertyActionEx(this, &CRaptorEPIX::OnEMGain);
	nRet = CreateProperty(propName.c_str(), "0.", MM::Float, false, pActX);
	SetPropertyLimits(propName.c_str(), 0, 3500);
*/
	
	// scan mode
/*   pAct = new CPropertyAction (this, &CRaptorEPIX::OnScanMode);
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false, pAct); 
   assert(nRet == DEVICE_OK);
   AddAllowedValue("ScanMode","1");
   AddAllowedValue("ScanMode","2");
   AddAllowedValue("ScanMode","3");
*/
   // camera gain
   if(_IS_CAMERA_OWL_FAMILY)
   {
	   double dGain=0;
	   dGain = GetGain();
	   osGain << dGain;
	   pAct = new CPropertyAction(this, &CRaptorEPIX::OnGain);
	   nRet = CreateProperty(MM::g_Keyword_Gain, osGain.str().c_str(), MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(MM::g_Keyword_Gain, 0, 48);
   }
   else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0)  && ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0) )
   {
	   double dGain=0.0;
	   dGain = GetGain();
	   dGain = dGain/512.0;
	   osGain << dGain;
	   pAct = new CPropertyAction(this, &CRaptorEPIX::OnGain);
	   nRet = CreateProperty(MM::g_Keyword_Gain, osGain.str().c_str(), MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);

	   SetPropertyLimits(MM::g_Keyword_Gain, 1, 128);
   }
   else if( ((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)==0) )
   {
	   long lGain=0;
	   lGain = GetEMGain();
	   osGain << lGain;
	   pAct = new CPropertyAction(this, &CRaptorEPIX::OnGain);
	   nRet = CreateProperty(MM::g_Keyword_Gain, osGain.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);

	   SetPropertyLimits(MM::g_Keyword_Gain, 0, 3500);
   } 

   if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
   {
	   pAct = new CPropertyAction(this, &CRaptorEPIX::OnFixedFrameRate);
	   nRet = CreateProperty(g_Keyword_FixedFrameRate, "0", MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);
   }

   if(((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) > 0) && 0)
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTECSetPoint);
	   nRet = CreateProperty(g_Keyword_TECSetPoint, "0", MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_TECSetPoint, ceil(ConvertTECValueToTemp(0)), floor(ConvertTECValueToTemp(0x0FFF)));
	   TECSetPoint_ = 0.0;
	   SetTECSetPoint(TECSetPoint_);
   }


   if(_IS_CAMERA_OWL_FAMILY)
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnFrameRateUser);
	   nRet = CreateProperty(g_Keyword_FrameRateUser, osFrameRate.str().c_str(), MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnFrameRate);
	   nRet = CreateProperty(g_Keyword_FrameRate, g_Keyword_FrameRate_25Hz, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);

	   vector<string> frameRates;
	   frameRates.push_back(g_Keyword_FrameRate_25Hz);
	   frameRates.push_back(g_Keyword_FrameRate_30Hz);
	   frameRates.push_back(g_Keyword_FrameRate_50Hz);
	   frameRates.push_back(g_Keyword_FrameRate_60Hz);
	   frameRates.push_back(g_Keyword_FrameRate_User);
	   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640)
	   {
		   frameRates.push_back(g_Keyword_FrameRate_90Hz);
		   frameRates.push_back(g_Keyword_FrameRate_120Hz);
	   }
	   //frameRates.push_back(g_Keyword_FrameRate_ExtTrig);
	   nRet = SetAllowedValues(g_Keyword_FrameRate, frameRates);
	   if (nRet != DEVICE_OK)
	      return nRet;

	   SetProperty(g_Keyword_FrameRate, g_Keyword_FrameRate_25Hz);

	   ////////////////////////////////////////////////////////////////
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTECSetPoint);

	   if(cameraType_ == _RAPTOR_CAMERA_OWL_640  )
	   {
		   nRet = CreateProperty(g_Keyword_TECSetPoint, "15", MM::Float, false, pAct);
		   assert(nRet == DEVICE_OK);
		   SetPropertyLimits(g_Keyword_TECSetPoint, ceil(ConvertTECValueToTemp(0)), floor(ConvertTECValueToTemp(0x0FFF)));
		   TECSetPoint_ = 15.0;
		   SetTECSetPoint(TECSetPoint_);
	   }
	   else if(cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	   {
		   nRet = CreateProperty(g_Keyword_TECSetPoint, "-15", MM::Float, false, pAct);
		   assert(nRet == DEVICE_OK);
		   SetPropertyLimits(g_Keyword_TECSetPoint, ceil(ConvertTECValueToTemp(0)), floor(ConvertTECValueToTemp(0x0FFF)));
		   TECSetPoint_ = -15.0;
		   SetTECSetPoint(TECSetPoint_);
	   }
	   else
	   {
		   nRet = CreateProperty(g_Keyword_TECSetPoint, "15", MM::Float, false, pAct);
		   assert(nRet == DEVICE_OK);
		   SetPropertyLimits(g_Keyword_TECSetPoint, ceil(ConvertTECValueToTemp(0)), floor(ConvertTECValueToTemp(0x0FFF)));
		   TECSetPoint_ = 15.0;
		   SetTECSetPoint(TECSetPoint_);
	   }

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnHighGain);
	   nRet = CreateProperty(g_Keyword_HighGain, g_Keyword_On, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_HighGain, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_HighGain, g_Keyword_On);
	   OWLHighGain_ = 1;
	   SetHighGain(OWLHighGain_);
   
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAutoExposure);
	   nRet = CreateProperty(g_Keyword_AutoExposure, g_Keyword_On, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_AutoExposure, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_AutoExposure, g_Keyword_On);
	   OWLAutoExp_ = 1;
	   SetAutoExposure(OWLAutoExp_);
   
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnPeakAvgLevel);
	   nRet = CreateProperty(g_Keyword_PeakAvgLevel, osPkAvg.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_PeakAvgLevel, 0, 255);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAutoLevel);
	   nRet = CreateProperty(g_Keyword_AutoExpLevel, osAutoLevel.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_AutoExpLevel, 0, 0x3FFF);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnBlackOffset);
	   nRet = CreateProperty(g_Keyword_BlackOffset, osBlackOffset.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_BlackOffset, 0, 0x3FFF);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTrigDelay);
	   nRet = CreateProperty(g_Keyword_TrigDelay, osTrigDelay.str().c_str(), MM::Float, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_TrigDelay, 0, 104);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnROIAppearance);
	   nRet = CreateProperty(g_Keyword_ROIAppearance, g_Keyword_ROI_Normal, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_ROIAppearance, g_Keyword_ROI_Normal);
	   AddAllowedValue(g_Keyword_ROIAppearance, g_Keyword_ROI_Bright);
	   AddAllowedValue(g_Keyword_ROIAppearance, g_Keyword_ROI_Dark);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnNUCState);
	   nRet = CreateProperty(g_Keyword_NUCState, g_Keyword_NUCState3, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState0);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState1);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState2);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState3);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState4);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState5);
	   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState6);

	   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	   {
		   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState7b);
		   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState8);
	   }
	   else
		   AddAllowedValue(g_Keyword_NUCState, g_Keyword_NUCState7a);
	   
	   OWLNUCState_ = 0x60;
	   SetNUCState(OWLNUCState_);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAGCSpeed);
	   nRet = CreateProperty(g_Keyword_AGCSpeed, osAGCSpeed.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_AGCSpeed, 0, 15);
	   
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnExpSpeed);
	   nRet = CreateProperty(g_Keyword_ExpSpeed, osExpSpeed.str().c_str(), MM::Integer, false, pAct);
	   assert(nRet == DEVICE_OK);
	   SetPropertyLimits(g_Keyword_ExpSpeed, 0, 15);


	   CPropertyAction *pAct = new CPropertyAction (this, &CRaptorEPIX::OnVideoPeak);
	   nRet = CreateProperty(g_Keyword_VideoPeak, osVideoPeak.str().c_str(), MM::Integer, true, pAct);
	   assert(nRet == DEVICE_OK);

	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnVideoAvg);
		nRet = CreateProperty(g_Keyword_VideoAvg, osVideoAvg.str().c_str(), MM::Integer, true, pAct);
	   assert(nRet == DEVICE_OK);

	   char strBuildInfo[256];
	   sprintf_s(strBuildInfo, 256, "%s %d/%d/20%d", buildCode_, buildDateMM_, buildDateDD_, buildDateYY_);
	   nRet = CreateProperty(g_Keyword_BuildInfo, strBuildInfo, MM::String, true);
	   assert(nRet == DEVICE_OK);

	   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	   {
   		   pAct = new CPropertyAction (this, &CRaptorEPIX::OnHorizontalFlip);
		   nRet = CreateProperty(g_Keyword_HorizontalFlip, g_Keyword_On, MM::String, false, pAct);
		   assert(nRet == DEVICE_OK);
		   AddAllowedValue(g_Keyword_HorizontalFlip, g_Keyword_Off);
		   AddAllowedValue(g_Keyword_HorizontalFlip, g_Keyword_On);

   		   pAct = new CPropertyAction (this, &CRaptorEPIX::OnBadPixel);
		   nRet = CreateProperty(g_Keyword_BadPixel, g_Keyword_On, MM::String, false, pAct);
		   assert(nRet == DEVICE_OK);
		   AddAllowedValue(g_Keyword_BadPixel, g_Keyword_Off);
		   AddAllowedValue(g_Keyword_BadPixel, g_Keyword_On);

   		   pAct = new CPropertyAction (this, &CRaptorEPIX::OnInvertVideo);
		   nRet = CreateProperty(g_Keyword_InvertVideo, g_Keyword_On, MM::String, false, pAct);
		   assert(nRet == DEVICE_OK);
		   AddAllowedValue(g_Keyword_InvertVideo, g_Keyword_Off);
		   AddAllowedValue(g_Keyword_InvertVideo, g_Keyword_On);

   		   pAct = new CPropertyAction (this, &CRaptorEPIX::OnImageSharpen);
		   nRet = CreateProperty(g_Keyword_ImageSharpen, g_Keyword_On, MM::String, false, pAct);
		   assert(nRet == DEVICE_OK);
		   AddAllowedValue(g_Keyword_ImageSharpen, g_Keyword_Off);
		   AddAllowedValue(g_Keyword_ImageSharpen, g_Keyword_On);
	   }
   }
   // camera offset
/*   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // camera temperature
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_CCDTemperature, -100, 10);

   // readout time
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK); 
*/

    if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
	{
		pAct = new CPropertyAction (this, &CRaptorEPIX::OnShutterMode);
		nRet = CreateProperty(g_Keyword_ShutterMode, g_Keyword_ShutterMode_Closed, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		AddAllowedValue(g_Keyword_ShutterMode, g_Keyword_ShutterMode_Closed);
		AddAllowedValue(g_Keyword_ShutterMode, g_Keyword_ShutterMode_Open);
		AddAllowedValue(g_Keyword_ShutterMode, g_Keyword_ShutterMode_Exposure);
//		nShutterMode_ = 0x02;
//		SetShutterMode(nShutterMode_);

		pAct = new CPropertyAction (this, &CRaptorEPIX::OnShutterDelayOpen);
		nRet = CreateProperty(g_Keyword_ShutterDelayOpen, "19.66", MM::Float, false, pAct);

		pAct = new CPropertyAction (this, &CRaptorEPIX::OnShutterDelayClose);
		nRet = CreateProperty(g_Keyword_ShutterDelayClose, "49.16", MM::Float, false, pAct);

		pAct = new CPropertyAction (this, &CRaptorEPIX::OnHighPreAmpGain);
		nRet = CreateProperty(g_Keyword_HighPreAmpGain, g_Keyword_On, MM::String, false, pAct);
	    assert(nRet == DEVICE_OK);
	    AddAllowedValue(g_Keyword_HighPreAmpGain, g_Keyword_Off);
	    AddAllowedValue(g_Keyword_HighPreAmpGain, g_Keyword_On);
	    bHighPreAmpGain_ = 1;
 	    SetHighPreAmpGain(bHighPreAmpGain_);
	}

    if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0 )
	{
		pAct = new CPropertyAction (this, &CRaptorEPIX::OnTECSetPoint);
		nRet = CreateProperty(g_Keyword_TECSetPoint, "15", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_TECSetPoint, ceil(ConvertTECValueToTemp(0)), floor(ConvertTECValueToTemp(0x0FFF)));
		TECSetPoint_ = 15.0;
		SetTECSetPoint(TECSetPoint_);
	}

   pAct = new CPropertyAction (this, &CRaptorEPIX::OnForceUpdate);
   nRet = CreateProperty(g_Keyword_ForceUpdate, g_Keyword_On, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_ForceUpdate, g_Keyword_Off);
   AddAllowedValue(g_Keyword_ForceUpdate, g_Keyword_On); 
   ForceUpdate_ = true;

   pAct = new CPropertyAction (this, &CRaptorEPIX::OnUseDefaults);
   nRet = CreateProperty(g_Keyword_Defaults, g_Keyword_Off, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_Defaults, g_Keyword_Off);
   AddAllowedValue(g_Keyword_Defaults, g_Keyword_UseDefaults); 
   
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnPostCaptureROI);
   nRet = CreateProperty(g_Keyword_PostCaptureROI, g_Keyword_On, MM::String, false, pAct);

   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_PostCaptureROI, g_Keyword_Off);
   AddAllowedValue(g_Keyword_PostCaptureROI, g_Keyword_On); 
   if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))	
  	  PostCaptureROI_ = false;
   else
      PostCaptureROI_ = false;

   // CCD size of the camera we are modeling
   osCCDX << cameraCCDXSize_ ;
   osCCDY << cameraCCDYSize_ ;
   CreateProperty("CCD X Pixels", osCCDX.str().c_str(), MM::Integer, true);
   CreateProperty("CCD Y Pixels", osCCDY.str().c_str(), MM::Integer, true);

   // Trigger device
//   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTriggerDevice); 
//   CreateProperty("TriggerDevice","", MM::String, false, pAct);

   // TE Cooler
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTECooler);
   nRet = CreateProperty(g_Keyword_TECooler,g_Keyword_Off, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_TECooler, g_Keyword_Off);
   if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0) )
	   AddAllowedValue(g_Keyword_TECooler, g_Keyword_On);
   else
   {
	   AddAllowedValue(g_Keyword_TECooler, g_Keyword_TECooler_neg5oC);
	   AddAllowedValue(g_Keyword_TECooler, g_Keyword_TECooler_neg20oC);
   }
   if(((cameraType_ & (_RAPTOR_CAMERA_EAGLE_BASE)) > 0))
	   AddAllowedValue(g_Keyword_TECooler, g_Keyword_TECooler_Reset);


   // TE Fan
   if(cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTECFan);
	   nRet = CreateProperty(g_Keyword_TECFan,g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_TECFan, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_TECFan, g_Keyword_On);
   }

   // AntiBloom
   if(_NOT_CAMERA_OWL_FAMILY && ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)==0))
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAntiBloom);
	   nRet = CreateProperty(g_Keyword_AntiBloom, g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_AntiBloom, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_AntiBloom, g_Keyword_On);
   

	   // TestPattern
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTestPattern);
	   nRet = CreateProperty(g_Keyword_TestPattern, g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_TestPattern, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_TestPattern, g_Keyword_On);
   }
   // HDR
    if((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0)
	{
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnHighDynamicRange); 
	   nRet = CreateProperty(g_Keyword_HDR, g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_HDR, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_HDR, g_Keyword_On);
	}

   // AOI
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnUseAOI);
   nRet = CreateProperty(g_Keyword_UseAOI, g_Keyword_Off, MM::String, false, pAct); 
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_UseAOI, g_Keyword_Off);
   AddAllowedValue(g_Keyword_UseAOI, g_Keyword_On);

   // AOI Left
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAOILeft);
   nRet = CreateProperty(g_Keyword_AOI_Left, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_AOI_Left, 0, cameraCCDXSize_-1);

   // AOI Right
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAOITop);
   nRet = CreateProperty(g_Keyword_AOI_Top, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_AOI_Top, 0, cameraCCDYSize_-1);

   // AOI Width
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAOIWidth);
   nRet = CreateProperty(g_Keyword_AOI_Width, osCCDX.str().c_str(), MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_AOI_Width, 1, cameraCCDXSize_);

   // AOI Height
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnAOIHeight);
   nRet = CreateProperty(g_Keyword_AOI_Height, osCCDY.str().c_str(), MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_AOI_Height, 1, cameraCCDYSize_); 

   // Micro Reset
   pAct = new CPropertyAction (this, &CRaptorEPIX::OnMicroReset);
   nRet = CreateProperty(g_Keyword_MicroReset, g_Keyword_Off, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_MicroReset, g_Keyword_Off);
   AddAllowedValue(g_Keyword_MicroReset, "Reset Now");

   // External Trigger
   if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnTrigger);
	   nRet = CreateProperty(g_Keyword_ExtTrigger, g_Keyword_TrigITR, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_TrigITR);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_TrigFFR);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_ExtTrigger_posTrig);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_ExtTrigger_negTrig);
	   if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_ExtTrigger_Abort);
	   triggerMode_ = 0;
   }
   else
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnExtTrigger);
	   nRet = CreateProperty(g_Keyword_ExtTrigger, g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_ExtTrigger_posTrig);
	   AddAllowedValue(g_Keyword_ExtTrigger, g_Keyword_ExtTrigger_negTrig);
	   triggerMode_ = 0;
   }

   if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0) && ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0) )
   {
	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnNUCMap);
	   nRet = CreateProperty(g_Keyword_NUCMap, g_Keyword_Off, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   AddAllowedValue(g_Keyword_NUCMap, g_Keyword_Off);
	   AddAllowedValue(g_Keyword_NUCMap, g_Keyword_On);
   }

   if(((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0) ) //&& ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0) )
   {
   	   pAct = new CPropertyAction (this, &CRaptorEPIX::OnDebayerMethod);
	   nRet = CreateProperty(g_Keyword_DebayerMethod, g_Keyword_Debayer_Bilinear, MM::String, false, pAct);
	   assert(nRet == DEVICE_OK);
	   //AddAllowedValue(g_Keyword_DebayerMethod, g_Keyword_Debayer_Nearest);
	   AddAllowedValue(g_Keyword_DebayerMethod, g_Keyword_Debayer_Bilinear);
	   AddAllowedValue(g_Keyword_DebayerMethod, g_Keyword_Debayer_None);
   }

   if(((cameraType_ & (_RAPTOR_CAMERA_KINGFISHER_BASE)) > 0))
   {
		SetReadoutMode(0x01);
		//int val = GetReadoutClock();
		SetReadoutClock(20);
		readoutRate_ = 20;
		readoutMode_ = 1;
   }
   if(((cameraType_ & (_RAPTOR_CAMERA_EAGLE_BASE)) > 0))
   {
		SetReadoutMode(0x01);
		//int val = GetReadoutClock();
		SetReadoutClock(2);
		readoutRate_ = 2;
		readoutMode_ = 1;
		SetShutterMode(2);
   }

	if(_IS_CAMERA_OWL_FAMILY || (((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)) > 0))
	{
		if(EPROM_ADC_Cal_0C_==0 || EPROM_ADC_Cal_40C_==0 || EPROM_ADC_Cal_0C_ == EPROM_ADC_Cal_40C_)
			GetEPROMManuData();
	}


   // Capture Mode
/*   pAct = new CPropertyAction (this, &CRaptorEPIX::OnCaptureMode);
   nRet = CreateProperty(g_Keyword_CaptureMode, g_Keyword_CaptureMode_Live, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(g_Keyword_CaptureMode, g_Keyword_CaptureMode_SnapShot);
   AddAllowedValue(g_Keyword_CaptureMode, g_Keyword_CaptureMode_Live);
   captureMode_ = 1;
*/
   // synchronize all properties
   // --------------------------
   ForceUpdate_ = true;
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;
   ForceUpdate_ = false;

   // setup the buffer
   // ----------------
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

   AOILeft_  = 0;
   AOITop_   = 0;
   AOIWidth_ = img_.Width();
   AOIHeight_= img_.Height();


#ifdef TESTRESOURCELOCKING
   TestResourceLocking(true);
   LogMessage("TestResourceLocking OK",true);
#endif

	initialized_ = true; 
	
   	captureMode_ = GetLiveVideo();
	triggerMode_ = GetExtTrigStatus();
	ExtTrigStatus_ = 0;
	SetExtTrigStatus(0);
	triggerMode_ = GetExtTrigStatus();
	//SetLiveVideo(captureMode_>0);

   if(_IS_CAMERA_OWL_FAMILY)
   {
		serialWriteRaptorRegister1(UNITMASK, 0xF9, 0x60 ) ;

	   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
			SetFPGACtrl(0x87);
	   else
			SetFPGACtrl(0x03);

   }
   else
   {
	   fieldCount_ = pxd_capturedFieldCount(UNITMASK);

		if(fieldCount_==0)
		{
			//SetLiveVideo(1);
			pxd_goLive(UNITMASK, 1);
			//while(pxd_capturedFieldCount(UNITMASK)==0);
			//liveMode_ = 1;
			liveMode_ = 0;
		}

		SetLiveVideo(0);
		captureMode_ = 0;
		liveMode_ = 0;
	    SetExtTrigStatus(0); 
   }

	//OnPropertyChanged(g_Keyword_ForceUpdate, g_Keyword_Off);

   // initialize image buffer
   GenerateEmptyImage(img_);

   //**pxd_goLive(UNITMASK, 1);
#ifdef DOLIVEPAIR
   if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
#endif
	   pxd_goLive(UNITMASK, 1);


	nSerialBlock_=0;
	DisableMicro();

   return DEVICE_OK;


}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems. 
*/
int CRaptorEPIX::Shutdown()
{
	SetLiveVideo(false);
	SetExtTrigStatus(0);
	SetEMGain(0);

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
			  SetFPGACtrl(130);
		  else
			  SetFPGACtrl(2);
	  }

	g_bCheckSum = false;
	SetSystemState(0x12);

	DisableMicro();

	if (initialized_)
		pxd_PIXCIclose();

	g_PIXCI_DriverLoaded = 0;
    initialized_ = false;
	g_SerialOK = false;
	serialOK_ = false;

/*	char driverparms[80];
	driverparms[sizeof(driverparms)-1] = 0; // this & snprintf: overly conservative - avoids warning messages
	sprintf_s(driverparms, sizeof(driverparms)-1, "-DM 0x%x %s", 1, DRIVERPARMS);

	int ret = pxd_PIXCIopen(driverparms, "Default", "");
	pxd_PIXCIclose();
*/
    return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CRaptorEPIX::SnapImage()
{
	nCapturing_=1;

	static int callCounter = 0;
	++callCounter;
	int ret = 0;

	if(FrameAccumulate_>1)
	{
		unsigned long* pBuf3  = NULL;
				
		unsigned short* pBuf1 = const_cast<unsigned short*>((unsigned short*)img_.GetPixels());
		unsigned long* pPix3;
		unsigned short* pPix1; //, *pPix2;
		long numPixels = img_.Width()*img_.Height();
		long numPixels3 = img3_.Width()*img3_.Height();

		if(binSize_==1 && 0)
		{
			pBuf3 = const_cast<unsigned long*>((unsigned long*)img3_.GetPixels());
			memset(pBuf3, 0, numPixels3*img3_.Depth());
		}
		else
		{
			pBuf3 = (unsigned long*) (new unsigned long [numPixels3*nComponents_]);
			memset(pBuf3, 0, numPixels*nComponents_*sizeof(unsigned long));
		}

		SetLiveVideo(1);
		DisableMicro();

		fieldCount_ = pxd_capturedFieldCount(UNITMASK);

		int nColor;
		nColor = nComponents_;
		if(nDebayerMethod_==0)
			nColor = 1;

		for(int ii=0;ii<FrameAccumulate_;ii++)
		{
			if(!thd_->IsStopped() && thd_->IsSuspended())
			{
				nCapturing_=0;
				return 0;
			}

			pPix1 = pBuf1;
			pPix3 = pBuf3;
			pxd_goSnap(UNITMASK, 1);
			ret = GetNewEPIXImage(img_, exposure_); 
			if(ret<0)
			{
				nCapturing_=0;
				LogMessage("Trigger Timeout");
				return DEVICE_SNAP_IMAGE_FAILED;
			}

			if(nColor>1)
			{
				pPix3 = (unsigned long*)pBuf3;
				for(long pp=0;pp<numPixels*nComponents_;pp++)
				{
					*pPix3++ += (unsigned long)(*pPix1++)/((unsigned long)FrameAccumulate_);
				}
			}
			else
			{
				for(long pp=0;pp<numPixels;pp++)
				{
					*pPix3++ += (unsigned long)(*pPix1++);
				}
			}
		}
		pPix1 = pBuf1;
		pPix3 = pBuf3;
		if(nColor==1)
		{
			for(long pp=0;pp<numPixels;pp++)
			{
				*pPix1++ = (unsigned short)((*pPix3++)/(unsigned long)FrameAccumulate_);
			}
		}
		else
		{
			//pPix2 = (unsigned short*)pBuf3;
			//memcpy(pPix1, pPix2, img_.Width()*img_.Height()*img_.Depth());
			for(long pp=0;pp<numPixels*nComponents_;pp++)
			{
				if(*pPix3<=65535)
					*pPix1++ = (unsigned short)(*pPix3);
				else
					*pPix1++ = (unsigned short)(65535);
				pPix3++;
			}

		}
		//if(binSize_>1)
			delete [] pBuf3;
	}
	else
	{
	   MM::MMTime startTime = GetCurrentMMTime();
	   //double expUs = exposure_ * 1000.0; 

	   if(_NOT_CAMERA_OWL_FAMILY) 
	   {
		   if(thd_->IsStopped() || trigSnap_) 
		   {
//			   int u=0;
			   int err;
			   //= pxd_goAbortLive(UNITMASK); 
				fieldCount_ = pxd_capturedFieldCount(UNITMASK);
/*				if(fieldCount_==0 &&0)
				{
					SetLiveVideo(1);
					err = pxd_goLive(UNITMASK, 1);
					Sleep(30);
					while(pxd_capturedFieldCount(UNITMASK)==0)
						Sleep(30);
					liveMode_ = 1;
				}
*/ 
			   if(liveMode_)
			   {
				   pxd_goAbortLive(UNITMASK);
				   SetLiveVideo(0);
				   while(GetLiveVideo()!=0)
				   {
					   Sleep(30);
					   SetLiveVideo(0);
				   }
					
					liveMode_ = 0;
			   }

				fieldCount_ = pxd_capturedFieldCount(UNITMASK);

				err = pxd_goSnap(UNITMASK, 1);

				double curTime = myClock();
				myFrameDiffTime_ = curTime - myReadoutStartTime_;
			    mySnapLastTime_ = curTime;  
				myReadoutStartTime_ = curTime;   
				if(mySequenceStartTime_==0.0)
				{
					mySequenceStartTime_ = myReadoutStartTime_;
					myFrameDiffTime_ = 0.0;
				}
				if(triggerMode_<=1)
					SetExtTrigStatus(1);					
				else
					SetExtTrigStatus((unsigned char)triggerMode_);
				DisableMicro();
		   }
		   else
		   {
			   if(liveMode_==0)
			   {
				    SetExtTrigStatus(0); 
					SetLiveVideo(1);
				   while(GetLiveVideo()==0)
				   {
					   Sleep(30);
					   SetLiveVideo(1);
				   }
					if(triggerMode_>1)
						SetExtTrigStatus((unsigned char)triggerMode_);

					liveMode_ = 1;
					DisableMicro();
			   }
		   }
	   }
	   else
	   {
			if(triggerMode_==0)
			{
#ifdef DOLIVEPAIR
				if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
#endif
					pxd_goLive(UNITMASK, 1);
			}
	   }
	
	   //ret = GetNewEPIXImage(img_,exposure_); 

/*	   MM::MMTime s0(0,0);
	   MM::MMTime t2 = GetCurrentMMTime();
	   if( s0 < startTime )
	   {
		  // ensure wait time is non-negative
		  long naptime = (long)(0.5 + expUs - (double)(t2-startTime).getUsec());
		  if( naptime < 1)
			 naptime = 1;
		  // longest possible nap is about 38 minutes
		  CDeviceUtils::NapMicros((unsigned long) naptime);
		  //mySleep(double(naptime)/1000.0);
	   }
	   else
	   { 
		  std::cerr << "You are operating this device adapter without setting the core callback, timing functions aren't yet available" << std::endl;
		  // called without the core callback probably in off line test program
		  // need way to build the core in the test program

	   }
*/	
	nCapturing_=0;
	}

/*   MM::MMTime s0(0,0);
   MM::MMTime t2 = GetCurrentMMTime();
   if( s0 < startTime )
   {
      // ensure wait time is non-negative
      long naptime = (long)(0.5 + expUs - (double)(t2-startTime).getUsec());
      if( naptime < 1)
         naptime = 1;
      // longest possible nap is about 38 minutes
      CDeviceUtils::NapMicros((unsigned long) naptime);
   }
   else
   {
      std::cerr << "You are operating this device adapter without setting the core callback, timing functions aren't yet available" << std::endl;
      // called without the core callback probably in off line test program
      // need way to build the core in the test program

   }
   */
   readoutStartTime_ = GetCurrentMMTime();

   
   return ret;




}


/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* CRaptorEPIX::GetImageBuffer()
{

   MMThreadGuard g(imgPixelsLock_);
   MM::MMTime readoutTime(readoutUs_);
   int ret1 = 0;

//   while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}	

   if(thd_->IsStopped() && trigSnap_==0 && FrameAccumulate_==1)
   {
	   nCapturing_=1;
	   ret1 = GetNewEPIXImage(img_,exposure_); 
	   nCapturing_=0;
   }

	if(ret1<0)
	{
		LogMessage("Trigger Timeout");
		return NULL;
	}

   unsigned char *pB = (unsigned char*)(img_.GetPixels());
   return pB;
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CRaptorEPIX::GetImageWidth() const
{

   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CRaptorEPIX::GetImageHeight() const
{

   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CRaptorEPIX::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CRaptorEPIX::GetBitDepth() const
{
   return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CRaptorEPIX::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* This demo implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CRaptorEPIX::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if(IsCapturing())
       return DEVICE_CAMERA_BUSY_ACQUIRING;

   //MMThreadGuard g(imgPixelsLock_);
   thd_->Suspend(); MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);
   
   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
      ResizeImageBuffer();
      roiX_ = 0;
      roiY_ = 0;
	  roiSnapX_ = 0;
	  roiSnapY_ = 0;

	  SetROIStatus(cameraCCDXSize_, cameraCCDYSize_, 0, 0);

	  AOIWidth_ = img_.Width()*binSize_;
	  AOIHeight_= img_.Height()*binSize_;
	  AOILeft_  = 0;
  	  AOITop_   = 0;
	  useAOI_   = false;
	  UpdateProperty(g_Keyword_AOI_Left);
	  UpdateProperty(g_Keyword_AOI_Top);
	  UpdateProperty(g_Keyword_AOI_Width);
	  UpdateProperty(g_Keyword_AOI_Height);
   }
   else  
   {
	  if((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0)
	  {
		  x = (x/2)*2;
		  y = (y/2)*2;
		  xSize = (xSize/2)*2;
		  ySize = (ySize/2)*2;
	  }

	  // apply ROI
	  if(_IS_CAMERA_OWL_FAMILY && PostCaptureROI_==0)
	      img_.Resize(cameraCCDXSize_, cameraCCDYSize_);
	  else
		  img_.Resize(xSize, ySize);

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  SetROIStatus(xSize, ySize, x, y);
		  roiX_ = x;
		  roiY_ = y;
	  }
	  else
	  {
/*		  roiX_ = roiSnapX_ + x*snapBin_;
		  roiY_ = roiSnapY_ + y*snapBin_;
		  SetROIStatus(xSize*snapBin_, ySize*snapBin_, (roiX_/snapBin_)*snapBin_, (roiY_/snapBin_)*snapBin_);
*/	  

		  roiX_ = x*snapBin_;
		  roiY_ = y*snapBin_;
		  SetROIStatus(xSize*snapBin_, ySize*snapBin_, (roiX_), (roiY_));
	  }


	  if(_IS_CAMERA_OWL_FAMILY && PostCaptureROI_==0)
	  {
		AOIWidth_ = (xSize/4)*4;
		AOIHeight_= (ySize/4)*4;
		AOILeft_  = (x/4)*4;
		AOITop_   = (y/4)*4;
	  }
	  else
	  {
		  if(PostCaptureROI_)
		  {
			AOIWidth_ = xSize*snapBin_;
			AOIHeight_= ySize*snapBin_;
			AOILeft_  = (roiX_/snapBin_)*snapBin_;
			AOITop_   = (roiY_/snapBin_)*snapBin_;
/*			AOIWidth_ = xSize;
			AOIHeight_= ySize;
			AOILeft_  = roiX_;
			AOITop_   = roiY_;
*/		  }
		  else
		  {
			GetROIStatus(&xSize, &ySize, &roiX_, &roiY_);

			AOIWidth_ = xSize/binSize_;
			AOIHeight_= ySize/binSize_;
			AOILeft_  = roiX_/binSize_;
			AOITop_   = roiY_/binSize_;
		  }
	  }

	  // apply ROI
	  if(_IS_CAMERA_OWL_FAMILY && PostCaptureROI_==0)
	      img_.Resize(cameraCCDXSize_, cameraCCDYSize_);
	  else
		  img_.Resize(AOIWidth_, AOIHeight_);

	  useAOI_   = true;
	  UpdateProperty(g_Keyword_AOI_Left);
	  UpdateProperty(g_Keyword_AOI_Top);
	  UpdateProperty(g_Keyword_AOI_Width);
	  UpdateProperty(g_Keyword_AOI_Height);
      UpdateProperty(g_Keyword_UseAOI);


   }

   DisableMicro(); thd_->Resume();
   return DEVICE_OK;
}


/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CRaptorEPIX::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   xSize = img_.Width();
   ySize = img_.Height();

   if(xSize<=0 && ySize<=0)
	   GetROIStatus(&xSize, &ySize, &roiX_, &roiY_);

   x = roiX_;
   y = roiY_;

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CRaptorEPIX::ClearROI()
{
   //MMThreadGuard g(imgPixelsLock_);
   thd_->Suspend(); MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);

   ResizeImageBuffer();
   roiX_ = 0;
   roiY_ = 0;

   SetROIStatus(img_.Width()*binSize_, img_.Height()*binSize_, roiX_, roiY_);
      
	AOIWidth_ = img_.Width()*binSize_;
	AOIHeight_= img_.Height()*binSize_;
	AOILeft_  = 0;
	AOITop_   = 0;
	useAOI_ = false;
	UpdateProperty(g_Keyword_AOI_Left);
	UpdateProperty(g_Keyword_AOI_Top);
	UpdateProperty(g_Keyword_AOI_Width);
	UpdateProperty(g_Keyword_AOI_Height);
	UpdateProperty(g_Keyword_UseAOI);

	DisableMicro(); thd_->Resume();
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CRaptorEPIX::GetExposure() const
{ 
	if(exposure_>0)
		return exposure_;

	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	double dExp = GetExposureCore();
	DisableMicro(); thd_->Resume();

    return dExp; 
}
 
double CRaptorEPIX::GetExposureCore() const
{
	unsigned char val[5] ={0,0,0,0,0};
	unsigned long long uExp = 0;
	
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 || (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0 || (cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0)
	{
		serialReadRaptorRegister1(UNITMASK, 0xED, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xEE, &val[1] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xEF, &val[2] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xF0, &val[3] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xF1, &val[4] ) ;
		uExp = Convert5UCharToULong(&val[0]);
	}
	else
	{
		serialReadRaptorRegister1(UNITMASK, 0xEE, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xEF, &val[1] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xF0, &val[2] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xF1, &val[3] ) ;
		uExp = Convert4UCharToULong(&val[0]);
	}

	
	double dExp = double(uExp);
 
	if(cameraType_ == _RAPTOR_CAMERA_KITE)
		dExp /= double(20e3);
	else if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		dExp /= double(40e3);
	else if(_IS_CAMERA_OWL_FAMILY)
		dExp /= double(160e3);
	else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
		dExp /= double(36e3);
	else if(((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0))
		dExp /= double(80e3);
	else if(((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) > 0))
		dExp /= double(20e3);
	else if(((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) > 0))
		dExp /= double(40e3);

	return dExp;
}

int CRaptorEPIX::GetReadoutMode() const
{
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)==0 && (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)==0 )
		return -1;

	thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char v1=0;

	serialReadRaptorRegister1(UNITMASK, 0xF7, &v1 ) ;

	DisableMicro(); thd_->Resume();

	return v1;
}

int CRaptorEPIX::SetReadoutMode(unsigned char v1 )
{
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)==0 && (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)==0 )
		return -1;

	thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	
	serialWriteRaptorRegister1(UNITMASK, 0xF7, v1 ) ;

	DisableMicro(); thd_->Resume();

	return 0;
}

int CRaptorEPIX::SetReadoutClock(int nRate ) 
{
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) )
	{

		thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		unsigned char v1=0x01, v2=0x01;

		if(nRate==5)
		{
			v1=0x04;
			v2=0x02;
		}
		else if(nRate==1)
		{
			v1=0x12;
			v2=0x10;
		}

		serialWriteRaptorRegister1(UNITMASK, 0xA3, v1 ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xA4, v2 ) ;

		DisableMicro(); thd_->Resume();

		return v1;
	}
	else if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) )
	{

		thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		unsigned char v1=0x02, v2=0x02;

		if(nRate==1)
		{
			v1=0x43;
			v2=0x80;
		}

		serialWriteRaptorRegister1(UNITMASK, 0xA3, v1 ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xA4, v2 ) ;

		DisableMicro(); thd_->Resume();

		return v1;
	}
	
	return -1;
}

int CRaptorEPIX::GetReadoutClock( ) const
{
	if( (cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)==0 && (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)==0 )
		return -1;

	thd_->Suspend(); 	MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char v1=0, v2=0;
	int nRate = 0;

	serialReadRaptorRegister1(UNITMASK, 0xA3, &v1 ) ;
	serialReadRaptorRegister1(UNITMASK, 0xA4, &v2 ) ;

	if( (cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE))
	{

		if(v1==0x04 && v2==0x02)
		{
			nRate = 5;
		}
		else if(v1==0x12 && v2==0x10)
		{
			nRate = 1;
		}
		else if(v1==0x01 && v2==0x01)
		{
			nRate = 20;
		}
	}
	else if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
	{
		if(v1==0x02 && v2==0x02)
		{
			nRate = 2;
		}
		else if(v1==0x43 && v2==0x80)
		{
			nRate = 1;
		}

	}

	DisableMicro(); thd_->Resume();

	return nRate;
}

double CRaptorEPIX::GetFrameRate() const
{ 
	if(_NOT_CAMERA_OWL_FAMILY)
		return 0.0;
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char val[4] ={0,0,0,0};
	
	serialReadRaptorRegister1(UNITMASK, 0xDD, &val[0] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xDE, &val[1] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xDF, &val[2] ) ;
	serialReadRaptorRegister1(UNITMASK, 0xE0, &val[3] ) ;
	DisableMicro(); thd_->Resume(); 

	unsigned long long nRate = Convert4UCharToULong(&val[0]);

	double dRate = 0.0;
	if(nRate>0)
	{
		if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
			dRate = 40e6/double(nRate);
		else
			dRate = 5e6/double(nRate);
		dRate = floor(dRate*100+0.5)/100.0;
	}
	return dRate; 
}
double CRaptorEPIX::GetFixedFrameRate() const
{ 
	if((cameraType_ & (~_RAPTOR_CAMERA_COMMONCMDS1))==0)
		return 0.0;
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char val[5] ={0,0,0,0,0};
	unsigned long long nRate;
	double maxRate;
	
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 || (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
	{
		serialReadRaptorRegister1(UNITMASK, 0xDC, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xDD, &val[1] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xDE, &val[2] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xDF, &val[3] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xE0, &val[4] ) ;

		nRate = Convert5UCharToULong(&val[0]);
		maxRate = 20e6;
		if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
			maxRate = 40e6;
	}
	else
	{
		serialReadRaptorRegister1(UNITMASK, 0xDD, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xDE, &val[1] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xDF, &val[2] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xE0, &val[3] ) ;

		nRate = Convert4UCharToULong(&val[0]);
		maxRate = 80e6;
	}
	DisableMicro(); thd_->Resume(); 

	double dRate = 0.0;
	if(nRate>0)
	{
		dRate = maxRate/double(nRate);
		dRate = floor(dRate*100+0.5)/100.0;
	}
	return dRate; 
}

int CRaptorEPIX::serialWriteRaptorRegister1(int unit, unsigned char nReg, unsigned char val ) const
{
	if(!serialOK_)
		return -1;

	int ret;
	unsigned char bufin[]  = {0x53, 0xE0, 0x02, 0x00, 0x00, 0x50};
	unsigned char buf[256]; 

	bufin[3] = nReg ;
	bufin[4] = val ;
	ret = serialWriteReadCmd(UNITSOPENMAP, unit, bufin, 6, buf, 256 );
	
	if(ret<0) 
		pxd_mesgFault(UNITSMAP);

	return ret;
}


int CRaptorEPIX::serialReadRaptorRegister1(int unit, unsigned char nReg, unsigned char* val ) const
{
	if(bSuspend_)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	}

	if(!serialOK_)
		return -1;

	int ret;
	unsigned char bufin[]  = {0x53, 0xE0, 0x01, 0xFF, 0x50};
	unsigned char bufin2[] = {0x53, 0xE1, 0x01, 0x50};
	unsigned char buf[256];

	int pos=0;
	bufin[3] = nReg;
	ret = serialWriteReadCmd(UNITSOPENMAP, unit, bufin,  5, buf, 256 );
	ret = serialWriteReadCmd(UNITSOPENMAP, unit, bufin2, 4, buf, 256 );

	if(bSuspend_)
		DisableMicro(); thd_->Resume();

	if(ret>0) 
		pos = ret; 
	else if(ret<0) 
		pxd_mesgFault(UNITSMAP);

	if(pos>1 && buf[pos-1]==0x50) 
	{
		*val = buf[pos-2];
		return pos; 
	}
	else
		return -ret;

}

int CRaptorEPIX::SetLiveVideo(bool bLive) const
{
	//thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	int ret;

	if(_IS_CAMERA_OWL_FAMILY)
		return -1;	

	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		if(bLive)
			ret = serialWriteRaptorRegister1(UNITMASK, 0xD4, ((unsigned char)ExtTrigStatus_) | 0x04);		
		else
			ret = serialWriteRaptorRegister1(UNITMASK, 0xD4, ((unsigned char)ExtTrigStatus_) & ~0x04);		
		
		if(g_bCheckSum)
			SetSystemState(0x12);
	}
	else
	{
		if(bLive)
		{
			//ret = serialWriteRaptorRegister1(UNITMASK, 0xEC, 0x55);
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF2, 0x10);
		}
		else
		{
			//ret = serialWriteRaptorRegister1(UNITMASK, 0xEC, 0x55);
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF2, 0x00);		
		}
	}
	//thd_->Resume();
	if(g_bCheckSum)
		GetSystemState();
	return ret;

}
int CRaptorEPIX::GetLiveVideo() const
{
	int ret;
	unsigned char val;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		if(triggerMode_)
			return 0x00;	
		else
			return 0x10;	
	}
	
	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		ret = serialReadRaptorRegister1(UNITMASK, 0xD4, &val);
		return (val & 0x04)>0;	
	}
	else	
		ret = serialReadRaptorRegister1(UNITMASK, 0xF2, &val);
	//thd_->Resume();

	return (int)val;
}

void CRaptorEPIX::SetBinningFactor(int nBin) 
{
#ifdef _MSC_VER
#pragma warning (push)
// Suppress warnings triggered by "-2147483648" in the #included *.fmt files
// (an MSVC quirk)
#pragma warning (disable: 4146)
#endif

//	unsigned char bufin1[] = {0x53, 0xE0, 0x02, 0xEA, 0x00, 0x50};
//	unsigned char buf[256];
	unsigned char val=0;

	if(_IS_CAMERA_OWL_FAMILY)
		return ;	

	if(nBin==1)
		val = 0x00;
	else if(nBin==2)
		val = 0x11;
	else if(nBin==3)
		val = 0x22;
	else if(nBin==4)
		val = 0x33;
	else if(nBin==5)
		val = 0x44;
	else if(nBin==6)
		val = 0x55;
	else if(nBin==7)
		val = 0x66;
	else if(nBin==8)
		val = 0x77;
	else if(nBin==9)
		val = 0x88;
	else if(nBin==10)
		val = 0x99;
	else if(nBin==11)
		val = 0xAA;
	else if(nBin==12)
		val = 0xBB;
	else if(nBin==13)
		val = 0xCC;
	else if(nBin==14)
		val = 0xDD;
	else if(nBin==15)
		val = 0xEE;
	else if(nBin==16)
		val = 0xFF;

	int ret;
	if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
	{
		serialWriteRaptorRegister1(UNITMASK, 0xA1, (nBin-1)&0xFF);
		serialWriteRaptorRegister1(UNITMASK, 0xA2, (nBin-1)&0xFF);

		if(nBin==1)
		{
			#include FORMATFILE_LOAD_EAGLE_BIN1
			pxd_videoFormatAsIncludedInit(0);
			ret = pxd_videoFormatAsIncluded(0);
		}
		else if(nBin==2)
		{
			#include FORMATFILE_LOAD_EAGLE_BIN2
			pxd_videoFormatAsIncludedInit(0);
			ret = pxd_videoFormatAsIncluded(0);
		}
		else if(nBin>=4)
		{
			#include FORMATFILE_LOAD_EAGLE_BIN4
			pxd_videoFormatAsIncludedInit(0);
			ret = pxd_videoFormatAsIncluded(0);
		}		

		if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
		{
			unsigned char val1;
			serialReadRaptorRegister1(UNITMASK, 0xD4, &val1 ) ;
			val1 |= 0x08;
			serialWriteRaptorRegister1(UNITMASK, 0xD4, val1 ) ;		
			if(g_bCheckSum)
				SetSystemState(0x12);
		}

	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		if(cameraType_ != _RAPTOR_CAMERA_KINGFISHER_674 && cameraType_ != _RAPTOR_CAMERA_KINGFISHER_694	)
		{
			if(nBin==1)
				val = 0x00;
			else if(nBin==2)
				val = 0x11;
			else if(nBin==4)
				val = 0x22;
		}

		pxd_goAbortLive(UNITMASK);

		if(((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0))
			serialWriteRaptorRegister1(UNITMASK, 0xDB, val);
		else
		{
			serialWriteRaptorRegister1(UNITMASK, 0xA1, val&0x0F);
			serialWriteRaptorRegister1(UNITMASK, 0xA2, val&0x0F);
		}
/*
#define _RAPTOR_CAMERA_OSPREY_RGB			(_RAPTOR_CAMERA_OSPREY			+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_KINGFISHER_674_RGB	(_RAPTOR_CAMERA_KINGFISHER_674	+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_KINGFISHER_694_RGB	(_RAPTOR_CAMERA_KINGFISHER_694	+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_CYGNET_RGB			(_RAPTOR_CAMERA_CYGNET			+ _RAPTOR_CAMERA_RGB)
#define _RAPTOR_CAMERA_UNKNOWN1_RGB			(_RAPTOR_CAMERA_UNKNOWN1		+ _RAPTOR_CAMERA_RGB)
*/

		
		if(nBin==2)
		{
/*			ret = 0;
			#include FORMATFILE_LOAD_OSPREY_BIN2
			pxd_videoFormatAsIncludedInit(0);
			ret = pxd_videoFormatAsIncluded(0);
*/			ret = 0;
			if(cameraType_ == _RAPTOR_CAMERA_OSPREY	)
			{
				#include FORMATFILE_LOAD_OSPREY_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET	)	
			{
				#include FORMATFILE_LOAD_CYGNET_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN2
			}
*/			else if(cameraType_ == _RAPTOR_CAMERA_OSPREY_RGB	)
			{
				#include FORMATFILE_LOAD_OSPREY_RGB_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_RGB_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_RGB_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET_RGB	)	
			{
				#include FORMATFILE_LOAD_CYGNET_RGB_BIN2
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1_RGB)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN1
			}
*/		}
		else if(nBin==4)
		{
/*			ret = 0;
			#include FORMATFILE_LOAD_OSPREY_BIN4
			pxd_videoFormatAsIncludedInit(0);
			ret = pxd_videoFormatAsIncluded(0);
*/			ret = 0;
			if(cameraType_ == _RAPTOR_CAMERA_OSPREY	)
			{
				#include FORMATFILE_LOAD_OSPREY_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET	)	
			{
				#include FORMATFILE_LOAD_CYGNET_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN4
			}
*/			else if(cameraType_ == _RAPTOR_CAMERA_OSPREY_RGB	)
			{
				#include FORMATFILE_LOAD_OSPREY_RGB_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_RGB_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_RGB_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET_RGB	)	
			{
				#include FORMATFILE_LOAD_CYGNET_RGB_BIN4
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1_RGB)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN4
			}
*/		}
		else
		{
			ret = 0;
			if(cameraType_ == _RAPTOR_CAMERA_OSPREY	)
			{
				#include FORMATFILE_LOAD_OSPREY_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET	)	
			{
				#include FORMATFILE_LOAD_CYGNET_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN1
			}
*/			else if(cameraType_ == _RAPTOR_CAMERA_OSPREY_RGB	)
			{
				#include FORMATFILE_LOAD_OSPREY_RGB_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER674_RGB_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694_RGB	)
			{
				#include FORMATFILE_LOAD_KINGFISHER694_RGB_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
/*			else if(cameraType_ == _RAPTOR_CAMERA_CYGNET_RGB	)	
			{
				#include FORMATFILE_LOAD_CYGNET_RGB_BIN1
				pxd_videoFormatAsIncludedInit(0);
				ret = pxd_videoFormatAsIncluded(0);
			}
			else if(cameraType_ == _RAPTOR_CAMERA_UNKNOWN1_RGB)
			{
				//#include FORMATFILE_LOAD_UNKNOWN1_BIN1
			}
*/		}
		if(triggerMode_==0)
		{
			#ifdef DOLIVEPAIR
			   if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
			#endif			   
				   pxd_goLive(UNITMASK, 1);
		}

		if(exposure_>0)
			SetExposure(exposure_, true);
	}
	else
	{
		serialWriteRaptorRegister1(UNITMASK, 0xEB, val);
	}
//	serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );

#ifdef _MSC_VER
#pragma warning (pop)
#endif
}

int serialWriteReadCmd(int unitopenmap, int unit, unsigned char* bufin, int insize, unsigned char* bufout, int outsize, bool bChkSum ) 
{
	if(g_SerialOK==false)
		return -100;

	bChkSum &= g_bCheckSum;

	MMThreadGuard g(g_serialLock_[unitopenmap]);
	int nBigLoop=0, ret=0;
	do
	{
		nBigLoop++;

		int ii;
		if(pxd_serialRead( unit, 0, NULL, 0)>0)
		{
			ret = pxd_serialRead( unit, 0, (char*)bufout, outsize);
			if(fidSerial[unitopenmap] && gUseSerialLog[unitopenmap])
			{
				gClockCurrent[unitopenmap] = myClock();
				fprintf(fidSerial[unitopenmap], "Time: %0.8f\t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]) );

				fprintf(fidSerial[unitopenmap], "FB: ");
				for(ii=0; ii<ret; ii++) 
					fprintf(fidSerial[unitopenmap], "0x%02X ",bufout[ii]);
				fprintf(fidSerial[unitopenmap], "\n");
			}
		}
		int tick = 0;
		while(pxd_serialWrite(unit, 0, NULL, 0)<insize && tick<100)
		{
			tick++;
			mySleep(1);
		}
		if(tick==100)
			tick=100;

		insize = insize < 256 ? insize : 255;
		unsigned char ucChk = 0;
		for(ii=0;ii<insize;ii++)
		{
			g_ucSerialBuf[ii] = bufin[ii] ;
			ucChk ^= bufin[ii];
		}
		
		int insize2;
		insize2 = insize;
		if(bChkSum)
		{
			g_ucSerialBuf[insize] = ucChk;
			insize2++;
		}

		//ret = pxd_serialWrite(unit, 0, (char*)bufin, insize);
		ret = pxd_serialWrite(unit, 0, (char*)g_ucSerialBuf, insize2);
		if(ret<0)
			ret = pxd_serialWrite(unit, 0, (char*)g_ucSerialBuf, insize2);

		if(fidSerial[unitopenmap] && gUseSerialLog[unitopenmap])
		{
			gClockCurrent[unitopenmap] = myClock();
			fprintf(fidSerial[unitopenmap], "Time: %0.8f \t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]));

			fprintf(fidSerial[unitopenmap], "TX: ");
			for(ii=0; ii<insize2; ii++)
				fprintf(fidSerial[unitopenmap], "0x%02X ",g_ucSerialBuf[ii]);
			fprintf(fidSerial[unitopenmap], "\n");
		}
		tick=0;
		while(pxd_serialRead( unit, 0, NULL, 0)==0 && tick<100)
		{
			tick++;
			mySleep(1);
		}
		if(tick==100 )
		{
			tick=0;
		}
		int bytes, morebytes;
		bytes = pxd_serialRead( unit, 0, (char*)bufout, outsize); 
		mySleep(1);
		morebytes = pxd_serialRead( unit, 0, NULL, 0);

		tick=0;
		if(bytes==0 || morebytes>0)
		{
			while((bytes<1 || (bufout[bytes-2] != 0x50 && bufout[bytes-1] != 0x50) || morebytes>0) && tick<100)
			{
				bytes += pxd_serialRead( unit, 0, (char*)&bufout[bytes], outsize-bytes); 
				mySleep(1);
				morebytes = pxd_serialRead( unit, 0, NULL, 0);
				tick++;
			}
		}
		ret = bytes;

		if(((bChkSum && bytes==2) || (!bChkSum && bytes>=1))  && bufout[0]>0x50 && bufout[0]<=0x55)
		{
			gClockCurrent[unitopenmap] = myClock();
			fprintf(fidSerial[unitopenmap], "Time: %0.8f \t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]) );
			fprintf(fidSerial[unitopenmap], "CMD ERROR: 0x%02X\n",bufout[0]);
			ret = 0x50 - bufout[0];
		}
		else if(bChkSum && (bytes>1 && bufout[bytes-1]!=ucChk && bufout[bytes-1]!=0x50))
		{
			gClockCurrent[unitopenmap] = myClock();
			fprintf(fidSerial[unitopenmap], "Time: %0.8f \t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]) );
			fprintf(fidSerial[unitopenmap], "CHKSUM ERROR: 0x%02X, 0x%02X\n",bufout[bytes-1], ucChk);
		}
		if(bChkSum && bytes<2)
		{
			gClockCurrent[unitopenmap] = myClock();
			fprintf(fidSerial[unitopenmap], "Time: %0.8f \t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]) );
			fprintf(fidSerial[unitopenmap], "RESPONSE ERROR: \n");
		}

		if(bChkSum && bytes>0 && bufout[bytes-2]==0x50)
			ret = bytes-1;
		
		if(fidSerial[unitopenmap] && gUseSerialLog[unitopenmap])
		{
			gClockCurrent[unitopenmap] = myClock();
			fprintf(fidSerial[unitopenmap], "Time: %0.8f \t", double(gClockCurrent[unitopenmap] - gClockZero[unitopenmap]) );
			fprintf(fidSerial[unitopenmap], "RX: ");
			for(ii=0; ii<bytes; ii++)
				fprintf(fidSerial[unitopenmap], "0x%02X ",bufout[ii]);
			fprintf(fidSerial[unitopenmap], "\n");
		}
		if(nBigLoop>1)
			Sleep(100);
	}
	while(ret<0 && nBigLoop<3);

	return ret;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CRaptorEPIX::SetExposure(double exp, bool bUpdate)
{ 
	exposure_ = exp;

	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]); 

	if(bUpdate)
	{
		SetMaxExposureFixedFrameRate();
		exp = exposure_ ;
		SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
	}

	if(cameraType_ == _RAPTOR_CAMERA_KITE)
	{
		if(exp>214.0*1000.0)
			exp = 214.0*1000.0;
		exp *= double(20e3);
	}
	else if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	{
		if(exp>26.8*1000.0)
			exp = 26.8*1000.0;
		exp *= double(40e3);
		if(exp<20)
			exp = 20;		
	}
	else if(_IS_CAMERA_OWL_FAMILY)
	{
		if(exp>26.8*1000.0)
			exp = 26.8*1000.0;
		exp *= double(160e3);
		if(exp<80)
			exp = 80;
	}
	else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
	{
		if(exp>119.0*1000.0)
			exp=119.0*1000.0;
		exp *= double(36e3);
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0))
	{
		if(exp>3.8*3600*1000.0)
			exp = 3.8*3600*1000.0;
		exp *= double(80e3);
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) > 0))
	{
		if(exp>15.2*3600*1000.0)
			exp = 15.2*3600*1000.0;
		exp *= double(20e3);
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) > 0))
	{
		if(exp>7.6*3600*1000.0)
			exp = 7.6*3600*1000.0;
		exp *= double(40e3);
	}

	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		unsigned char val1;
		serialReadRaptorRegister1(UNITMASK, 0xD4, &val1 ) ;
		val1 |= 0x08;
		serialWriteRaptorRegister1(UNITMASK, 0xD4, val1 ) ;		
		if(g_bCheckSum)
			SetSystemState(0x12);
	}
	unsigned long long lExp = (unsigned long long)(exp);
	unsigned char ucExp[5];
	ucExp[0] = (unsigned char)( (lExp & 0xFF00000000L) >> 32 );	
	ucExp[1] = (unsigned char)( (lExp & 0x00FF000000L) >> 24 );	
	ucExp[2] = (unsigned char)( (lExp & 0x0000FF0000L) >> 16 );	
	ucExp[3] = (unsigned char)( (lExp & 0x000000FF00L) >>  8 );	
	ucExp[4] = (unsigned char)( (lExp & 0x00000000FFL) );	
 
	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE) || (cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) || (cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE))
	{
		serialWriteRaptorRegister1(UNITMASK, 0xED, ucExp[0]);
		serialWriteRaptorRegister1(UNITMASK, 0xEE, ucExp[1]);
		serialWriteRaptorRegister1(UNITMASK, 0xEF, ucExp[2]);
		serialWriteRaptorRegister1(UNITMASK, 0xF0, ucExp[3]);
		serialWriteRaptorRegister1(UNITMASK, 0xF1, ucExp[4]);		
	}
	else
	{
		serialWriteRaptorRegister1(UNITMASK, 0xEE, ucExp[1]);
		serialWriteRaptorRegister1(UNITMASK, 0xEF, ucExp[2]);
		serialWriteRaptorRegister1(UNITMASK, 0xF0, ucExp[3]);
		serialWriteRaptorRegister1(UNITMASK, 0xF1, ucExp[4]);
	}

	exposure_ = GetExposureCore(); 

	if(exposure_ > exposureMax_)
	{
		exposureMax_ = exposure_;
		SetPropertyLimits(MM::g_Keyword_Exposure, 0, exposureMax_);
	}
//	if(exposure_ < 10000.0 && exposureMax_>=10000)
//	{
//		exposureMax_ = 10000.0;
//		SetPropertyLimits(MM::g_Keyword_Exposure, 0, exposureMax_);
//	}	
	
	GetSystemState();

	DisableMicro();

	this->thd_->Resume();
}

void CRaptorEPIX::SetFrameRate(double dFrameRate)
{ 
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned long nRate=0;

	if(_NOT_CAMERA_OWL_FAMILY)
		return;

	if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		nRate = (unsigned long)(40e6/dFrameRate);
	else
		nRate = (unsigned long)(5e6/dFrameRate);
	
	unsigned char ucRate[4];
	ucRate[0] = (unsigned char)( (nRate & 0xFF000000L) >> 24 );	
	ucRate[1] = (unsigned char)( (nRate & 0x00FF0000L) >> 16 );	
	ucRate[2] = (unsigned char)( (nRate & 0x0000FF00L) >> 8  );	
	ucRate[3] = (unsigned char)( (nRate & 0x000000FFL) );	
 
	serialWriteRaptorRegister1(UNITMASK, 0xDD, ucRate[0]);
	serialWriteRaptorRegister1(UNITMASK, 0xDE, ucRate[1]);
	serialWriteRaptorRegister1(UNITMASK, 0xDF, ucRate[2]);
	serialWriteRaptorRegister1(UNITMASK, 0xE0, ucRate[3]);

	DisableMicro(); thd_->Resume();
}
void CRaptorEPIX::SetFixedFrameRate(double dFrameRate)
{ 
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned long long nRate=0;

	if((cameraType_ & (~_RAPTOR_CAMERA_COMMONCMDS1))==0)
		return;

	if(cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)
		nRate = (unsigned long long)(20e6/dFrameRate);
	else if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
		nRate = (unsigned long long)(40e6/dFrameRate);
	else
		nRate = (unsigned long long)(80e6/dFrameRate);
	
	unsigned char ucRate[5];
	ucRate[0] = (unsigned char)( (nRate & 0xFF00000000L) >> 32 );	
	ucRate[1] = (unsigned char)( (nRate & 0x00FF000000L) >> 24 );	
	ucRate[2] = (unsigned char)( (nRate & 0x0000FF0000L) >> 16 );	
	ucRate[3] = (unsigned char)( (nRate & 0x000000FF00L) >> 8  );	
	ucRate[4] = (unsigned char)( (nRate & 0x00000000FFL) );	
 
	if(cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)
	{
		serialWriteRaptorRegister1(UNITMASK, 0xDC, ucRate[0]);
		serialWriteRaptorRegister1(UNITMASK, 0xDD, ucRate[1]);
		serialWriteRaptorRegister1(UNITMASK, 0xDE, ucRate[2]);
		serialWriteRaptorRegister1(UNITMASK, 0xDF, ucRate[3]);
		serialWriteRaptorRegister1(UNITMASK, 0xE0, ucRate[4]);
	}
	else
	{
		serialWriteRaptorRegister1(UNITMASK, 0xDD, ucRate[1]);
		serialWriteRaptorRegister1(UNITMASK, 0xDE, ucRate[2]);
		serialWriteRaptorRegister1(UNITMASK, 0xDF, ucRate[3]);
		serialWriteRaptorRegister1(UNITMASK, 0xE0, ucRate[4]);
	}

	DisableMicro(); thd_->Resume();
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CRaptorEPIX::GetBinning() const
{

   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(buf);
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CRaptorEPIX::SetBinning(int binF)
{

   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int CRaptorEPIX::SetAllowedBinning() 
{

   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CRaptorEPIX::StartSequenceAcquisition(double interval) {

   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CRaptorEPIX::StopSequenceAcquisition()                                     
{                                                                         
	thd_->Resume();
   if (!thd_->IsStopped()) {
      thd_->Stop();                                                       
      thd_->wait();                                                       
   }                                    

	  if(_NOT_CAMERA_OWL_FAMILY) 
	   {
		   if(initialized_)
			   pxd_goAbortLive(UNITMASK);
		   SetLiveVideo(0);
		   trigSnap_ = 0;
		   DisableMicro();
	  }
                                                                          
   return DEVICE_OK;                                                      
} 

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CRaptorEPIX::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;
   
   //gClockZero[UNITSOPENMAP] = myClock();
   imageCounter_ = 0;
   trigSnap_ = (frameInterval_>exposure_);

   if(_NOT_CAMERA_OWL_FAMILY) 
   {
	   SetExtTrigStatus(0);
	   SetLiveVideo(0);
	   while(GetLiveVideo()!=0)
	   {
		   Sleep(30);
		   SetLiveVideo(0);
	   }
	   int err = pxd_goAbortLive(UNITMASK);
	   fieldCount_ = pxd_capturedFieldCount(UNITMASK);
	   fieldCount0_ = fieldCount_;

	   if(trigSnap_==0)
	   {
#ifdef DOLIVEPAIR
			if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
#endif	       //if(err!=LIVEPAIRTEST)
			   err = pxd_goLive(UNITMASK, 1);
	   }

	   	   	
	   if(triggerMode_>1)
		   SetExtTrigStatus((unsigned char)triggerMode_);
	   else if(trigSnap_==0)
	   {
		   SetLiveVideo(1);

		   while(GetLiveVideo()==0)
		   { 
			   Sleep(30);
			   SetLiveVideo(1);
		   }
	   }

	   liveMode_ = 1;
   }
	sequenceStartTime_ = GetCurrentMMTime();
	mySequenceStartTime_ = 0.0;
	mySnapLastTime_ = 0.0;
	
	DisableMicro();

   thd_->Start(numImages,interval_ms);
   stopOnOverflow_ = stopOnOverflow;
   return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CRaptorEPIX::InsertImage()
{
   MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);

   MM::MMTime timeStamp;
	   timeStamp = readoutStartTime_;

//	double dCurrentClock1 = myClock();
	int ret1 = 0;
	if(FrameAccumulate_==1)
	{
		nCapturing_=1;
		ret1 = GetNewEPIXImage(img_,exposure_); 
		nCapturing_=0;
		if(ret1<0)
		{
			LogMessage("Trigger Timeout");
			return DEVICE_SNAP_IMAGE_FAILED;
		}
	}
	double dCurrentClock2 = myClock();

   //MM::MMTime timeStamp = this->GetCurrentMMTime();

   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;

/*   Metadata myMetadata = GetMetaData();
   // Copy the metadata inserted by other processes:
   std::vector<std::string> keys = metadata_.GetKeys();
//   for (unsigned int i= 0; i < keys.size(); i++) {
//      md.put(keys[i], metadata_.GetSingleTag(keys[i].c_str()).GetValue().c_str());
//   }

   for (unsigned int i= 0; i < keys.size(); i++) {
      MetadataSingleTag mst = metadata_.GetSingleTag(keys[i].c_str());
      md.PutTag(mst.GetName(), mst.GetDevice(), mst.GetValue());
   }
*/
   md.put("Camera", label);
   md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
   md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
   //md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString(fieldCount_));
   md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
   //md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(fieldCount_));
   md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
   md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 
   //md.put("FieldCount", CDeviceUtils::ConvertToString( (long) fieldCount_)); 
 
//	MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
//	mstStartTime.SetValue(CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
//	md.SetTag(mstStartTime);

	MetadataSingleTag mst1("Interval Wait Time", label, true);
	mst1.SetValue(CDeviceUtils::ConvertToString(myIntervalWaitTime_*1000.0));
	md.SetTag(mst1);

	MetadataSingleTag mst4("Frame Diff Time", label, true);
	mst4.SetValue(CDeviceUtils::ConvertToString(myFrameDiffTime_*1000.0));
	md.SetTag(mst4);

	
	MetadataSingleTag mst3(MM::g_Keyword_Elapsed_Time_ms, label, true);
	mst3.SetValue(CDeviceUtils::ConvertToString((myReadoutStartTime_ - mySequenceStartTime_)*1000.0));
	md.SetTag(mst3);

	dCurrentClock2 = myClock();

	if(trigSnap_)
	{
		MetadataSingleTag mst5("Trigger to Capture Time", label, true);
		mst5.SetValue(CDeviceUtils::ConvertToString((myCaptureTime_ - myReadoutStartTime_)*1000.0));
		md.SetTag(mst5);

		MetadataSingleTag mst6("Trigger to Data Time", label, true);
		mst6.SetValue(CDeviceUtils::ConvertToString((myCaptureTime2_ - myReadoutStartTime_)*1000.0));
		md.SetTag(mst6);
	}
	MetadataSingleTag mst("Field Count", label, true);
	//mst.SetValue(CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
	mst.SetValue(CDeviceUtils::ConvertToString(fieldCount_));
	md.SetTag(mst);

	MetadataSingleTag mst2("Field Buffer", label, true);
	//mst.SetValue(CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
	mst2.SetValue(CDeviceUtils::ConvertToString(fieldBuffer_));
	md.SetTag(mst2);

/*	MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
	mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_));      
	md.SetTag(mstCount);

	MetadataSingleTag mstB(MM::g_Keyword_Binning, label, true);
	mstB.SetValue(CDeviceUtils::ConvertToString(binSize_));      
	md.SetTag(mstB);
*/

   imageCounter_++;
 
   char buf[MM::MaxStrLength];
   //GetProperty(MM::g_Keyword_Binning, buf);
   sprintf_s(buf, MM::MaxStrLength, "%ld", binSize_);
   md.put(MM::g_Keyword_Binning, buf);

   MMThreadGuard g(imgPixelsLock_);

   const unsigned char* pI = GetImageBuffer();
   unsigned int w = GetImageWidth();
   unsigned int h = GetImageHeight();
   unsigned int b = GetImageBytesPerPixel();

   //int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b) ;//, &md);
   //int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);

   int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
   	  if(fidSerial[UNITSOPENMAP] && gUseSerialLog[UNITSOPENMAP])
			fprintf(fidSerial[UNITSOPENMAP],"*** Insert Image Buffer Overflow - Resetting ***\n");

      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
      return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
   } else
      return ret;


}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int CRaptorEPIX::ThreadRun (void)
{


	int ret=DEVICE_ERR;
   
   // Trigger
   if (triggerDevice_.length() > 0) {
      MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
      if (triggerDev != 0) {
      	//char label[256];
      	//triggerDev->GetLabel(label);
      	LogMessage("trigger requested");
      	triggerDev->SetProperty("Trigger","+");
      }
   }
   
	
   MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);
   
   double dCurrentClock = myClock();
   double frameDiff = frameInterval_/1000.0;

   myIntervalWaitTime_ = mySnapLastTime_ + frameDiff - dCurrentClock;
   if(mySnapLastTime_==0.0) 
		myIntervalWaitTime_ = 0.0;

   if(frameInterval_>0.0 && mySnapLastTime_>0.0 && dCurrentClock < mySnapLastTime_ + frameDiff)
   {
		while(dCurrentClock < mySnapLastTime_ + frameDiff)
			dCurrentClock = myClock();
   }

   readoutStartTime_ = this->GetCurrentMMTime();
   
   ret = SnapImage();
 
   if (ret != DEVICE_OK)
   {
      return DEVICE_OK;
   }

/*	if(trigSnap_==0 && 0)
	{
		myReadoutStartTime_ = myClock();   
		if(mySequenceStartTime_==0.0)
			mySequenceStartTime_ = myReadoutStartTime_;
	}*/
   ret = InsertImage();

   if (ret != DEVICE_OK)
   {
      return DEVICE_OK;
   }
   return ret;
};

bool CRaptorEPIX::IsCapturing() {
   return !thd_->IsStopped() || (nCapturing_>0);
}

/*
 * called from the thread function before exit 
 */
void CRaptorEPIX::OnThreadExiting() throw()
{
   try
   {
	  if(_NOT_CAMERA_OWL_FAMILY) 
	   {
		   SetLiveVideo(0);
		   pxd_goAbortLive(UNITMASK);
		   trigSnap_ = 0;
	  }

      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


MySequenceThread::MySequenceThread(CRaptorEPIX* pCam)
   :intervalMs_(default_intervalMS)
   ,numImages_(default_numImages)
   ,imageCounter_(0)
   ,stop_(true)
   ,suspend_(false)
   ,camera_(pCam)
   ,startTime_(0)
   ,actualDuration_(0)
   ,lastFrameTime_(0)
{};

MySequenceThread::~MySequenceThread() {};

void MySequenceThread::Stop() {
   MMThreadGuard(this->stopLock_);
   stop_=true;
}

void MySequenceThread::Start(long numImages, double intervalMs)
{
   MMThreadGuard(this->stopLock_);
   MMThreadGuard(this->suspendLock_);
   numImages_=numImages;
   intervalMs_=intervalMs;
   imageCounter_=0;
   stop_ = false;
   suspend_=false;
   activate();
   actualDuration_ = 0;
   startTime_= camera_->GetCurrentMMTime();
   lastFrameTime_ = 0;
}

bool MySequenceThread::IsStopped(){
   MMThreadGuard(this->stopLock_);
   return stop_;
}

void MySequenceThread::Suspend() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

bool MySequenceThread::IsSuspended() {
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

void MySequenceThread::Resume() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

int MySequenceThread::svc(void) throw()
{
   int ret=DEVICE_ERR;
   try 
   {
      do
      {  
         ret=camera_->ThreadRun();
		 while(IsSuspended() && !IsStopped())
		 { 
			 Sleep(1);
		 }
      } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
      if (IsStopped())
         camera_->LogMessage("SeqAcquisition interrupted by the user\n");
   }catch(...){
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   stop_=true;
   actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
   camera_->OnThreadExiting();

   return ret;
}


///////////////////////////////////////////////////////////////////////////////
// CRaptorEPIX Action handlers
///////////////////////////////////////////////////////////////////////////////

/*
* this Read Only property will update whenever any property is modified
*/

int CRaptorEPIX::OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(testProperty_[indexx]);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(testProperty_[indexx]);
   }
	return DEVICE_OK;

}

int CRaptorEPIX::SetEMGain(long nGain)
{
	if(cameraType_==_RAPTOR_CAMERA_KITE || cameraType_==_RAPTOR_CAMERA_FALCON)
	{

		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		if(_IS_CAMERA_OWL_FAMILY)
			return -1;	

		unsigned char ucGain[2];
		ucGain[0] = (unsigned char)( (nGain & 0x00000F00L) >> 8  );	
		ucGain[1] = (unsigned char)( (nGain & 0x000000FFL) );	
 
		int ret;
		ret = serialWriteRaptorRegister1(UNITMASK, 0x09, ucGain[0]);
		ret = serialWriteRaptorRegister1(UNITMASK, 0x0A, ucGain[1]);

		DisableMicro(); thd_->Resume();
		if(ret>0)
			return 0; 
		else
			return -1;
	}
	else
		return 0;
}

void CRaptorEPIX::SetHighDynamicRange(int nHDR)
{
	if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1))
		return;	
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	if(nHDR>0)
	{
  	   if(exposure_ < 420.0)
	   {
			exposure_ = 420.0;
			SetExposure(exposure_, false);
	   }
		serialWriteRaptorRegister1(UNITMASK, 0xF7, 0x72 ) ;
	}
	else
	{
		SetMaxExposureFixedFrameRate();
		SetExposure(exposure_, false);
		serialWriteRaptorRegister1(UNITMASK, 0xF7, 0x60 ) ;
	}

	DisableMicro(); thd_->Resume();
}

void CRaptorEPIX::SetNUCMap(int nNUC)
{
	if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1))
		return;	
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	if(nNUC>0)
		serialWriteRaptorRegister1(UNITMASK, 0xF7, 0x40 ) ;
	else
		serialWriteRaptorRegister1(UNITMASK, 0xF7, 0x60 ) ;

	DisableMicro(); thd_->Resume();
}

int CRaptorEPIX::SetGain(double dGain)
{
	if(_NOT_CAMERA_OWL_FAMILY && ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1)==0))
		return -1;	
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	int nGain = 1;
	if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	{
		nGain = (int)(dGain*256.0);
	}
	else if(_IS_CAMERA_OWL_FAMILY)
	{
		dGain = pow(10.0, dGain/20.0);
		nGain = (int)(dGain*256.0);
	}
	else
		nGain = (int)(dGain*512.0);

	if(nGain>0xFFFF)
		nGain = 0xFFFF;
	else if(nGain<0)
		nGain = 0;

	unsigned char ucGain[2];
	ucGain[0] = (unsigned char)( (nGain & 0xFF00) >> 8 );	
	ucGain[1] = (unsigned char)( (nGain & 0x00FF) );	
 
	int ret;
	if(_IS_CAMERA_OWL_FAMILY)
	{
		ret = serialWriteRaptorRegister1(UNITMASK, 0xC6, ucGain[0]);
		ret = serialWriteRaptorRegister1(UNITMASK, 0xC7, ucGain[1]);
	}
	else 
	{
		ret = serialWriteRaptorRegister1(UNITMASK, 0xD5, ucGain[0]);
		ret = serialWriteRaptorRegister1(UNITMASK, 0xD6, ucGain[1]);
	}
	DisableMicro(); thd_->Resume();
	if(ret>0)
		return 0; 
	else
		return -1;
}

long CRaptorEPIX::GetEMGain() const
{
	if(cameraType_==_RAPTOR_CAMERA_KITE || cameraType_==_RAPTOR_CAMERA_FALCON)
	{

		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		unsigned char val[2] ={0,0};

		serialReadRaptorRegister1(UNITMASK, 0x09, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x0A, &val[1] ) ;
		DisableMicro(); thd_->Resume();

		long gain = (long(val[0]&0x0F)<<8) + long(val[1]);

		return gain; 
	}
	else
		return 0;
}

int CRaptorEPIX::GetHighDynamicRange() const
{
	if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1 ))
		return -1;
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val = 0;

	serialReadRaptorRegister1(UNITMASK, 0xF7, &val ) ;
	DisableMicro(); thd_->Resume();

    return (val==0x72); 
}

int CRaptorEPIX::GetNUCMap() const
{
	if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1 ))
		return -1;
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val = 0;

	serialReadRaptorRegister1(UNITMASK, 0xF7, &val ) ;
	DisableMicro(); thd_->Resume();

    return (val==0x40); 
}

double CRaptorEPIX::GetGain() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val[2] ={0,0};

	double gain = 0.0;
	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		serialReadRaptorRegister1(UNITMASK, 0xD5, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xD6, &val[1] ) ;
		DisableMicro(); thd_->Resume();

		gain = (double(val[0])*256.0 + double(val[1]))/512.0;
	}
	else
	{
		serialReadRaptorRegister1(UNITMASK, 0xC6, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xC7, &val[1] ) ;
		DisableMicro(); thd_->Resume();

		gain = double(val[0]) + (double(val[1])/256.0);

		if(!(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
			gain = 20.0*log10(gain);
	}

    return gain; 
}

int CRaptorEPIX::GetBloomingStatus() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val[2] ={0,0};
	int value ;

	serialReadRaptorRegister1(UNITMASK, 0x0F, &val[0] ) ;
	serialReadRaptorRegister1(UNITMASK, 0x10, &val[1] ) ;
	DisableMicro(); thd_->Resume();

	value = (int(val[0]&0x0F)<<8) + int(val[1]);

	if(cameraType_ == _RAPTOR_CAMERA_KITE)
	{
		if(value==2300)
			return 1;
		else if(value==1750)
			return 0;
		else
			return -1;
	}
	else if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		return -1;
	}
	else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
	{
		if(value==2700)
			return 1;
		else if(value==2389)
			return 0;
		else
			return -1;
	}
	return -1;
}

void CRaptorEPIX::SetBloomingStatus(int val) const
{
	
	unsigned char ucVal[2] ={0,0};
	if(val==1)
	{
		if(cameraType_ == _RAPTOR_CAMERA_KITE)
		{
			ucVal[0] = 0x08;
			ucVal[1] = 0xFC;
		}
		else if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
		{
			return;
		}
		else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
		{
			ucVal[0] = 0x0A;
			ucVal[1] = 0x8C;
		}

	}
	else
	{
		if(cameraType_ == _RAPTOR_CAMERA_KITE)
		{
			ucVal[0] = 0x06;
			ucVal[1] = 0xD6;		
		}
		else if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
		{
			return;
		}
		else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
		{
			ucVal[0] = 0x09;
			ucVal[1] = 0x55;
		}

	}
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	serialWriteRaptorRegister1(UNITMASK, 0x0F, ucVal[0] ) ;
	serialWriteRaptorRegister1(UNITMASK, 0x10, ucVal[1] ) ;
	DisableMicro(); thd_->Resume();
}


double CRaptorEPIX::GetPCBTemp(bool bSnap) const
{
	if(bSuspend_ && !bSnap)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	}
	unsigned char val[2] ={0,0};
	double value=-999.0 ; 

	if(_IS_CAMERA_OWL_FAMILY && !(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
	{
		unsigned char bufin[] = {0x53, 0x97, 0x02, 0x50}; 
		unsigned char buf[256];
		int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  4, buf, 256 );
		if(ret>2)
		{
			val[0] = buf[ret-3];
			val[1] = buf[ret-2];
			value = (double)(val[0]) + 0.5*double((val[1]&0x80)>0);
		}
		if(bSuspend_ && !bSnap)
		{
			DisableMicro(); thd_->Resume();
		}
		return value;
	}
	else
	{
		serialReadRaptorRegister1(UNITMASK, 0x70, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x71, &val[1] ) ;

		value = (double(val[0]<<8) + double(val[1]))/16.0;

		value = floor(value*10.0+0.5)/10.0;
	}
	if(bSuspend_ && !bSnap)
	{
		DisableMicro(); thd_->Resume();
	}
    return value; 
}

double CRaptorEPIX::GetCCDTemp(bool bSnap) 
{
	unsigned char val[2] ={0,0};
	double value = -999;
	nCCDTempCalibrated_ = 0;

	if(bSuspend_ && !bSnap)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	}
	if(_IS_CAMERA_OWL_FAMILY && !(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
	{
		unsigned char bufin[] = {0x53, 0x91, 0x02, 0x50}; 
		unsigned char buf[256];
		int ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin,  4, buf, 256 );
		if(ret>2)
		{
			val[0] = buf[ret-3];
			val[1] = buf[ret-2];
			value = ((double)(val[0]))*256 + double(val[1]);

			if(EPROM_ADC_Cal_0C_>0 && EPROM_ADC_Cal_40C_>0 && EPROM_ADC_Cal_0C_ != EPROM_ADC_Cal_40C_)
			{
				double m, c;
				m = 40.0 / ((double(EPROM_ADC_Cal_40C_) - double(EPROM_ADC_Cal_0C_)));
				c =  - m*double(EPROM_ADC_Cal_0C_);
				value = (m*value + c);
				value = double(int(value*10.0+0.5))/10.0;
				nCCDTempCalibrated_ = 1;
			}
			else
				nCCDTempCalibrated_ = 0;
		}
		else
			nCCDTempCalibrated_ = 0;

		if(bSuspend_ && !bSnap)
		{
			DisableMicro(); thd_->Resume();
		}
		
		return value;
	}
	else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
	{
		serialReadRaptorRegister1(UNITMASK, 0x6F, &val[0] ) ;

		value = 28.63636363 - double(val[0])/2.2;

		value = floor(value*10.0+0.5)/10.0;

		if(bSuspend_ && !bSnap)
		{
			DisableMicro(); thd_->Resume();
		}
		nCCDTempCalibrated_ = 1;
	    return value; 

	}
	else if(cameraType_ == _RAPTOR_CAMERA_KITE)
	{

		serialReadRaptorRegister1(UNITMASK, 0x6E, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x6F, &val[1] ) ;

		value = (long(val[0]&0x0F)<<8) + long(val[1]);

		value = value*5.0/2048.0;
		value = 100.0*(value/(10.0-value));
		value = log(value/27.404)/(-0.0446);
		value = floor(value*10.0+0.5)/10.0;

		if(bSuspend_ && !bSnap)
		{
			DisableMicro(); thd_->Resume();
		}
		nCCDTempCalibrated_ = 1;
	    return value; 
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0) || (cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 ))
	{
		serialReadRaptorRegister1(UNITMASK, 0x6E, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x6F, &val[1] ) ;

		value = (long(val[0]&0x0F)<<8) + long(val[1]);

		if(value>2)
		{
			if(EPROM_ADC_Cal_0C_>0 && EPROM_ADC_Cal_40C_>0 && (EPROM_ADC_Cal_0C_ != EPROM_ADC_Cal_40C_) )
			{
				double m, c;
				m = 40.0 / ((double(EPROM_ADC_Cal_40C_) - double(EPROM_ADC_Cal_0C_)));
				c =  - m*double(EPROM_ADC_Cal_0C_);
				value = (m*value + c);
				value = double(int(value*10.0+0.5))/10.0;
				nCCDTempCalibrated_ = 1;
			}
			else
				nCCDTempCalibrated_ = 0;

		}
		else
			nCCDTempCalibrated_ = 0;

		if(bSuspend_ && !bSnap)
		{
			DisableMicro(); thd_->Resume();
		}
		return value;
	}
	if(bSuspend_ && !bSnap)
	{
		DisableMicro(); thd_->Resume();
	}
	return value;
}

int CRaptorEPIX::GetFPGACtrl() const
{
	unsigned char val;
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	serialReadRaptorRegister1(UNITMASK, 0x00, &val ) ;
	DisableMicro(); thd_->Resume();
    return (int)val; 
}
int CRaptorEPIX::GetEPROMManuData() 
{ 
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char bufin1[] = {0x53, 0xAE, 0x05, 0x01, 0x00, 0x00, 0x02, 0x00, 0x50};
	unsigned char bufin2[] = {0x53, 0xAF, 0x12, 0x50};
	unsigned char buf[256];

	SetSystemState(0x13);
	int ret;
	
	ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  9, buf, 256 );
	if(ret>0)
		ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  4, buf, 256 );
	SetSystemState(0x12);

	int numFF=0;
	if(ret>=18)
	{
		
		for(int ii=0; ii<ret; ii++)
			numFF += int(buf[ii]==0xFF)+int(buf[ii]==0x00);
	}

	if(ret<18 || numFF>1)
	{
		SetSystemState(0x13);
		ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  9, buf, 256 );
		if(ret>0)
			ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  4, buf, 256 );
	}

	SetSystemState(0x12);
	DisableMicro(); thd_->Resume();

	if(ret<0)
		return ret;

	if(ret>18)
	{
	   serialNum_ = buf[ret-19] + (int(buf[ret-18])<<8);
	   buildDateDD_  = buf[ret-17];
	   buildDateMM_  = buf[ret-16];
	   buildDateYY_  = buf[ret-15];
	   memcpy(&buildCode_[0], &buf[ret-14], 5);
	   buildCode_[5] = 0;
	   EPROM_ADC_Cal_0C_  = buf[ret-9] + (int(buf[ret-8])<<8);
	   EPROM_ADC_Cal_40C_ = buf[ret-7] + (int(buf[ret-6])<<8);
	   EPROM_DAC_Cal_0C_  = buf[ret-5] + (int(buf[ret-4])<<8);
	   EPROM_DAC_Cal_40C_ = buf[ret-3] + (int(buf[ret-2])<<8);
	   
	   return 0;
	}
	else if(ret==18)
	{
	   serialNum_ = buf[ret-19] + (int(buf[ret-18])<<8);
	   buildDateDD_  = buf[ret-17];
	   buildDateMM_  = buf[ret-16];
	   buildDateYY_  = buf[ret-15];
	   memcpy(&buildCode_[0], &buf[ret-14], 5);
	   buildCode_[5] = 0;
	   EPROM_ADC_Cal_0C_  = buf[ret-9] + (int(buf[ret-8])<<8);
	   EPROM_ADC_Cal_40C_ = buf[ret-7] + (int(buf[ret-6])<<8);
	   EPROM_DAC_Cal_0C_  = buf[ret-5] + (int(buf[ret-4])<<8);
	   EPROM_DAC_Cal_40C_ = buf[ret-3] + (int(buf[ret-2])<<8);
	   
	   return 0;
	}
	return -1;
}



void CRaptorEPIX::SetFPGACtrl(unsigned char val) const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	serialWriteRaptorRegister1(UNITMASK, 0x00, val ) ;
	DisableMicro(); thd_->Resume();
}

int CRaptorEPIX::GetExtTrigStatus() const
{
	unsigned char val;
	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{ 
		serialReadRaptorRegister1(UNITMASK, 0xD4, &val ) ;
		return int(val & ~0x04);
	}
	else if(_IS_CAMERA_OWL_FAMILY)
		serialReadRaptorRegister1(UNITMASK, 0xF2, &val ) ;
	else
		serialReadRaptorRegister1(UNITMASK, 0xEA, &val ) ;

    return (int)val; 
}


int CRaptorEPIX::GetBinningFactor() const
{
	unsigned char val, valx, valy;
	int bin=0;

	if(_IS_CAMERA_OWL_FAMILY)
		return 1;

	if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
	{
		serialReadRaptorRegister1(UNITMASK, 0xA1, &valx ) ;
		serialReadRaptorRegister1(UNITMASK, 0xA1, &valy ) ;
		bin = valx+1;
		return bin;
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		if(((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0))
			serialReadRaptorRegister1(UNITMASK, 0xDB, &val ) ;
		else
			serialReadRaptorRegister1(UNITMASK, 0xA1, &val ) ;
	}
	else
		serialReadRaptorRegister1(UNITMASK, 0xEB, &val ) ;

	if(val==0x00)
		bin = 1;
	else if(val==0x11)
		bin = 2;
	else if(val==0x22)
		bin = 3;
	else if(val==0x33)
		bin = 4;
	else if(val==0x44)
		bin = 5;
	else
		bin = (int)(val&0x0F)+1;

    return bin; 
}


void CRaptorEPIX::SetExtTrigStatus(unsigned char val) const
{
	if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	{
		serialWriteRaptorRegister1(UNITMASK, 0xD4, val | 0x08) ;
		if(g_bCheckSum)
			SetSystemState(0x12);
	}
	else if(_IS_CAMERA_OWL_FAMILY)
	{
		if(val==1)
			val = 0;

		if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		{
			unsigned char val2 = 0;
			serialReadRaptorRegister1(UNITMASK, 0xF2, &val2 ) ;
			val2 &= 0x9F;
			val2 |= val;
			serialWriteRaptorRegister1(UNITMASK, 0xF2, val2 ) ;
		}
		else
		{
			serialWriteRaptorRegister1(UNITMASK, 0xF2, val ) ;
		}
	}
	else 
		serialWriteRaptorRegister1(UNITMASK, 0xEA, val ) ;	
	if(g_bCheckSum)
		GetSystemState();
}

void CRaptorEPIX::GetROIStatus(unsigned int *nWidth, unsigned int *nHeight, unsigned int *nXOffset, unsigned int *nYOffset) const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	unsigned char val[2] ={0,0};

	if(PostCaptureROI_)
	{
		*nXOffset = AOILeft_;
		*nYOffset = AOITop_;
		*nWidth   = AOIWidth_;
		*nHeight  = AOIHeight_;
	}
	else
	{
		if(_IS_CAMERA_OWL_FAMILY)
		{
			serialReadRaptorRegister1(UNITMASK, 0x32, &val[0] ) ;
			*nXOffset = 4*(int)(val[0]);
			serialReadRaptorRegister1(UNITMASK, 0x33, &val[0] ) ;
			*nYOffset = 4*(int)(val[0]);
			serialReadRaptorRegister1(UNITMASK, 0x35, &val[0] ) ;
			*nWidth = 4*(int)(val[0]);
			serialReadRaptorRegister1(UNITMASK, 0x36, &val[0] ) ;
			*nHeight = 4*(int)(val[0]);
		}
		else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
		{	
			//return;
			if(((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0))
			{
				serialReadRaptorRegister1(UNITMASK, 0xD9, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xDA, &val[1] ) ;
				*nXOffset = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				serialReadRaptorRegister1(UNITMASK, 0xD7, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xD8, &val[1] ) ;
				*nWidth = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				unsigned char bufin1[] = {0x53, 0xE0, 0x02, 0xF3, 0x03, 0x50};
				unsigned char bufin2[] = {0x53, 0xE0, 0x02, 0xF4, 0x00, 0x50};
				unsigned char buf[256];
				int ret;
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  6, buf, 256 );
				serialReadRaptorRegister1(UNITMASK, 0x73, &val[1] ) ;
				bufin1[4] = 0x04;
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  6, buf, 256 );
				serialReadRaptorRegister1(UNITMASK, 0x73, &val[0] ) ;
				*nYOffset = (int(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				bufin1[4] = 0x01;
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  6, buf, 256 );
				serialReadRaptorRegister1(UNITMASK, 0x73, &val[1] ) ;
				bufin1[4] = 0x02;
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
				ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin2,  6, buf, 256 );
				serialReadRaptorRegister1(UNITMASK, 0x73, &val[0] ) ;
				*nHeight = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);
			}
			else
			{
				val[0] = 0; val[1] = 0;
				serialReadRaptorRegister1(UNITMASK, 0xB6, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xB7, &val[1] ) ;
				*nXOffset = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				serialReadRaptorRegister1(UNITMASK, 0xB4, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xB5, &val[1] ) ;
				*nWidth = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				serialReadRaptorRegister1(UNITMASK, 0xBA, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xBB, &val[1] ) ;
				*nYOffset = (int(val[0])<<8) + (unsigned int)(val[1]);

				val[0] = 0; val[1] = 0;
				serialReadRaptorRegister1(UNITMASK, 0xB8, &val[0] ) ;
				serialReadRaptorRegister1(UNITMASK, 0xB9, &val[1] ) ;
				*nHeight = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);
			}
		}
		else
		{	
			serialReadRaptorRegister1(UNITMASK, 0xDD, &val[1] ) ;
			serialReadRaptorRegister1(UNITMASK, 0xDE, &val[0] ) ;
			*nXOffset = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);
			val[0] = 0; val[1] = 0;
			serialReadRaptorRegister1(UNITMASK, 0xDF, &val[1] ) ;
			serialReadRaptorRegister1(UNITMASK, 0xE0, &val[0] ) ;
			*nWidth = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);
			val[0] = 0; val[1] = 0;
			serialReadRaptorRegister1(UNITMASK, 0xD9, &val[1] ) ;
			serialReadRaptorRegister1(UNITMASK, 0xDA, &val[0] ) ;
			*nYOffset = (int(val[0])<<8) + (unsigned int)(val[1]);
			val[0] = 0; val[1] = 0;
			serialReadRaptorRegister1(UNITMASK, 0xDB, &val[1] ) ;
			serialReadRaptorRegister1(UNITMASK, 0xDC, &val[0] ) ;
			*nHeight = ((unsigned int)(val[0])<<8) + (unsigned int)(val[1]);
		}
	}
	DisableMicro(); thd_->Resume();
}

void CRaptorEPIX::SetROIStatus(unsigned int nWidth, unsigned int nHeight, unsigned int nXOffset, unsigned int nYOffset) const
{
	unsigned char val[2] ={0,0};

	if(PostCaptureROI_)
	{
		nXOffset = 0;
		nYOffset = 0;
		nWidth   = cameraCCDXSize_;
		nHeight  = cameraCCDYSize_;
	}

	if(_IS_CAMERA_OWL_FAMILY) 
	{
		val[0] = (unsigned char)(nXOffset/4);
		serialWriteRaptorRegister1(UNITMASK, 0x32, val[0] ) ;
		val[0] = (unsigned char)(nYOffset/4);
		serialWriteRaptorRegister1(UNITMASK, 0x33, val[0] ) ;
		val[0] = (unsigned char)(nWidth/4);
		serialWriteRaptorRegister1(UNITMASK, 0x35, val[0] ) ;
		val[0] = (unsigned char)(nHeight/4);
		serialWriteRaptorRegister1(UNITMASK, 0x36, val[0] ) ;
	}
	else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0)) 
	{
		if(nWidth==0)
			return;
//return;
		unsigned char val1=0; 
		int err = 0;
		int ret;
//		serialReadRaptorRegister1(UNITMASK, 0xD4, &val1 ) ;
//		val1 |= 0x08;
//		serialWriteRaptorRegister1(UNITMASK, 0xD4, val1 ) ;

		// set offset to 0 first
		if((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0) 
		{
			ret = serialWriteRaptorRegister1(UNITMASK, 0xD9, 0x00 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xDA, 0x00 ) ;

			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x83 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, 0x00 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x84 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, 0x00 ) ;

		}
		else
		{
			serialWriteRaptorRegister1(UNITMASK, 0xB6, 0x00 ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xB7, 0x00 ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xBA, 0x00 ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xBB, 0x00 ) ;
		}

		if(binSize_>2)
		{
			nWidth  = (nWidth/binSize_)*binSize_;
			nHeight = (nHeight/binSize_)*binSize_;
		}
		else
		{
			nWidth  = (nWidth/2)*2;
			nHeight = (nHeight/2)*2;
		}

		val[0] = ((nWidth&0xFF00)>>8);
		val[1] =  (nWidth&0x00FF);

		if((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0) 
		{
			serialWriteRaptorRegister1(UNITMASK, 0xD7, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xD8, val[1] ) ;
		}
		else
		{
			serialWriteRaptorRegister1(UNITMASK, 0xB4, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xB5, val[1] ) ;
		}

		val[0] = ((nXOffset&0xFF00)>>8);
		val[1] =  (nXOffset&0x00FF);
		if((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0) 
		{
			serialWriteRaptorRegister1(UNITMASK, 0xD9, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xDA, val[1] ) ;
		}
		else
		{
			serialWriteRaptorRegister1(UNITMASK, 0xB6, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xB7, val[1] ) ;
		}

		if((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE) > 0) 
		{
	
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x81 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, 0x00 ) ;

			val[0] = ((nHeight&0xFF00)>>8);
			val[1] =  (nHeight&0x00FF);

			//bufin1[4] = 0x82;
			//ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x82 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, val[0] ) ;
			//bufin1[4] = 0x81;
			//ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x81 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, val[1] ) ;

			//unsigned char bufin1[] = {0x53, 0xE0, 0x02, 0xF3, 0x84, 0x50};
			//unsigned char buf[256];
			
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x83 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, 0x00 ) ;
			val[0] = ((nYOffset&0xFF00)>>8);
			val[1] =  (nYOffset&0x00FF);
			//bufin1[4] = 0x83;
			//ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x84 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, val[0] ) ;
			//ret = serialWriteReadCmd(UNITSOPENMAP, UNITMASK, bufin1,  6, buf, 256 );
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF3, 0x83 ) ;
			ret = serialWriteRaptorRegister1(UNITMASK, 0xF4, val[1] ) ;

		}
		else
		{
			val[0] = ((nHeight&0xFF00)>>8);
			val[1] =  (nHeight&0x00FF);
			serialWriteRaptorRegister1(UNITMASK, 0xB8, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xB9, val[1] ) ;

			val[0] = ((nYOffset&0xFF00)>>8);
			val[1] =  (nYOffset&0x00FF);
			serialWriteRaptorRegister1(UNITMASK, 0xBA, val[0] ) ;
			serialWriteRaptorRegister1(UNITMASK, 0xBB, val[1] ) ;
		}

		serialReadRaptorRegister1(UNITMASK, 0xD4, &val1 ) ;
		val1 |= 0x08;
		serialWriteRaptorRegister1(UNITMASK, 0xD4, val1 ) ;

		if(g_bCheckSum)
			SetSystemState(0x12);

		if((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0)
			err = pxd_setVideoResolution(UNITMASK, nWidth, nHeight, 0, 0);
		else
			err = pxd_setVideoResolution(UNITMASK, nWidth/binSize_, nHeight/binSize_, 0, 0);

	   if(liveMode_==0) 
	   {
			//pxd_goSnap(UNITMASK, 1);
		   if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) > 0) 
			   SetExtTrigStatus(0);					
		   else
			   SetExtTrigStatus(1);					
	   }

	}
	else
	{
		serialWriteRaptorRegister1(UNITMASK, 0xDD, 0x00 ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xDE, 0x00 ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xD9, 0x00 ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xDA, 0x00 ) ;

		val[0] = ((nWidth&0xFF00)>>8);
		val[1] =  (nWidth&0x00FF);
		serialWriteRaptorRegister1(UNITMASK, 0xDF, val[1] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xE0, val[0] ) ;
		
		val[0] = ((nXOffset&0xFF00)>>8);
		val[1] =  (nXOffset&0x00FF);
		serialWriteRaptorRegister1(UNITMASK, 0xDD, val[1] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xDE, val[0] ) ;
	
		val[0] = ((nHeight&0xFF00)>>8);
		val[1] =  (nHeight&0x00FF);
		serialWriteRaptorRegister1(UNITMASK, 0xDB, val[1] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xDC, val[0] ) ;

		val[0] = ((nYOffset&0xFF00)>>8);
		val[1] =  (nYOffset&0x00FF);
		serialWriteRaptorRegister1(UNITMASK, 0xD9, val[1] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xDA, val[0] ) ;
		
	}	

	GetSystemState();
}


void CRaptorEPIX::SetNUCState(int val) 
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialWriteRaptorRegister1(UNITMASK, 0xF9, (unsigned char)val ) ;
		DisableMicro(); thd_->Resume();
	}

}
int  CRaptorEPIX::GetNUCState() const
{
	unsigned char val = 0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0xF9, &val ) ;
		DisableMicro(); thd_->Resume();
	}

	return val;
}
int  CRaptorEPIX::GetPeakAvgLevel() const
{
	unsigned char val = 0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x2D, &val ) ;
		DisableMicro(); thd_->Resume();
	}

	return val;
}
void CRaptorEPIX::SetPeakAvgLevel(int val)
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialWriteRaptorRegister1(UNITMASK, 0x2D, (unsigned char)val ) ;
		DisableMicro(); thd_->Resume();
	}
}
void CRaptorEPIX::SetAutoLevel(int val)
{
	unsigned char ucval[]={0,0};
 
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	val = val << 2;
	ucval[0] = (val&0xFF00)>>8;
	ucval[1] = (val&0x00FC);

	if(_IS_CAMERA_OWL_FAMILY)
	{
		serialWriteRaptorRegister1(UNITMASK, 0x23, ucval[0] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0x24, ucval[1] ) ;
	}
	DisableMicro(); thd_->Resume();
}
int  CRaptorEPIX::GetAutoLevel() const
{
	unsigned char ucval[] = {0,0};
	int val=0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x23, &ucval[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x24, &ucval[1] ) ;
		DisableMicro(); thd_->Resume();
	}

	val = (int)(ucval[1]) + (((int)ucval[0])<<8) ;
	val = val >> 2;

	return val;
}
void CRaptorEPIX::SetBlackOffset(int val)
{
	unsigned char ucval[]={0,0};
 
	ucval[0] = (val&0x3F00)>>8;
	ucval[1] = (val&0x00FF);

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialWriteRaptorRegister1(UNITMASK, 0xB8, ucval[0] ) ;
		serialWriteRaptorRegister1(UNITMASK, 0xB9, ucval[1] ) ;
		DisableMicro(); thd_->Resume();
	}
}
int  CRaptorEPIX::GetBlackOffset() const
{
	unsigned char ucval[] = {0,0};
	int val=0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		serialReadRaptorRegister1(UNITMASK, 0xB8, &ucval[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xB9, &ucval[1] ) ;

		DisableMicro(); thd_->Resume();
	}

	val = (int)(ucval[1]) + (((int)ucval[0])<<8) ;

	return val;
}
int  CRaptorEPIX::GetVideoPeak() const
{
	unsigned char ucval[] = {0,0};
	int val=0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x5E, &ucval[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x5F, &ucval[1] ) ;
		DisableMicro(); thd_->Resume();
	}

	val = (int)(ucval[1]) + (((int)ucval[0]&0x3F)<<8) ;
	
	return val;
}
int  CRaptorEPIX::GetVideoAvg() const
{
	unsigned char ucval[] = {0,0};
	int val=0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x60, &ucval[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0x61, &ucval[1] ) ;
		DisableMicro(); thd_->Resume();
	}

	val = (int)(ucval[1]) + (((int)ucval[0]&0x3F)<<8) ;
	
	return val;
}
void CRaptorEPIX::SetAGCExpSpeed(int nAGC, int nExp)
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		unsigned char val;
		val  = (unsigned char)(nExp&0x0F);
		val += ((unsigned char)(nAGC&0x0F))<<4;

		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialWriteRaptorRegister1(UNITMASK, 0x2F, val ) ;
		DisableMicro(); thd_->Resume();
	}
}
int  CRaptorEPIX::GetAGCExpSpeed(int *nAGC, int *nExp) const
{
	unsigned char val = 0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x2F, &val ) ;
		DisableMicro(); thd_->Resume();
	}

	*nExp = (val&0x0F);
	*nAGC = (val&0xF0)>>4;

	return val;
}
void CRaptorEPIX::SetROIAppearance(int val)
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialWriteRaptorRegister1(UNITMASK, 0x31, (unsigned char)val ) ;
		DisableMicro(); thd_->Resume();
	}
}
int  CRaptorEPIX::GetROIAppearance() const
{
	unsigned char val = 0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0x31, &val ) ;
		DisableMicro(); thd_->Resume();
	}

	return val;
}
void CRaptorEPIX::SetAutoExposure(int nVal)
{
  if(_IS_CAMERA_OWL_FAMILY)
  {
	  unsigned char val;
	  val = (unsigned char) GetFPGACtrl();
	  if(nVal==0)
		SetFPGACtrl(val & 0xFD);
	  else 
		SetFPGACtrl(val | 0x02);
  }
}
int  CRaptorEPIX::GetAutoExposure() const
{
    unsigned char val;
    val = (unsigned char) GetFPGACtrl();

	return ((val&0x02) > 0);
}

void CRaptorEPIX::SetHighPreAmpGain(int nVal)
{
  	if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
    {
	  unsigned char val;
	  val = (unsigned char) GetFPGACtrl();
	  if(nVal==1)
		SetFPGACtrl(val & 0x7F);
	  else 
		SetFPGACtrl(val | 0x80);
  }
}
int CRaptorEPIX::GetHighPreAmpGain() const
{
    unsigned char val;
    val = (unsigned char) GetFPGACtrl();

	return ((val&0x80) == 0);
}
void CRaptorEPIX::SetHighGain(int val)
{
  if(_IS_CAMERA_OWL_FAMILY)
  {
	  thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		{
			unsigned char val2 = 0;
			serialReadRaptorRegister1(UNITMASK, 0xF2, &val2 ) ;
			if(val)
				val2 |= 0x06 ;
			else
				val2 &= 0xF9 ;
			serialWriteRaptorRegister1(UNITMASK, 0xF2, val2 ) ;  
		}
		else
		{
			if(val>0)
			{
				serialWriteRaptorRegister1(UNITMASK, 0xE4, 0x2F ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE5, 0xFC ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE6, 0x00 ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE7, 0x04 ) ;  
			}
			else
			{
				serialWriteRaptorRegister1(UNITMASK, 0xE4, 0x3F ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE5, 0xFC ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE6, 0x00 ) ;  
				serialWriteRaptorRegister1(UNITMASK, 0xE7, 0x04 ) ;  
			}
		}
	  DisableMicro(); thd_->Resume();
  } 
}
int  CRaptorEPIX::GetHighGain() const
{
	unsigned char val[] = {0,0,0,0};

	if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		unsigned char val2 = 0;
		serialReadRaptorRegister1(UNITMASK, 0xF2, &val2 ) ;
		DisableMicro(); thd_->Resume();
		return ((val2 & 0x06)>0) ? 1 : 0;
	}
	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0xE4, &val[0] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xE5, &val[1] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xE6, &val[2] ) ;
		serialReadRaptorRegister1(UNITMASK, 0xE7, &val[3] ) ;
		DisableMicro(); thd_->Resume();
	}
	if(val[0]==0x2F && val[1]==0xFC && val[2]==0x00 && val[3]==0x04)
	{
		return 1;
	}

	return 0;
} 


int CRaptorEPIX::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	  {
		  if(ForceUpdate_)
			  Gain_ = GetGain();
		  pProp->Set(Gain_);
	  }
	  else
	  {
		  if(ForceUpdate_)
			  EMGain_ = GetEMGain();
		  pProp->Set(EMGain_);
	  }
   }
   else if (eAct == MM::AfterSet)
   {
      std::ostringstream os;
	  if(_IS_CAMERA_OWL_FAMILY || ((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
	  {
		pProp->Get(Gain_);
		SetGain(Gain_);

        std::ostringstream os;
        os << Gain_;
	  }
	  else
	  {
		pProp->Get(EMGain_);
		SetEMGain(EMGain_);

        os << EMGain_;
	  }
      OnPropertyChanged(MM::g_Keyword_Gain, os.str().c_str());
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnTrigDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  if(ForceUpdate_)
			  OWLTrigDelay_ = GetTrigDelay();
		  pProp->Set(OWLTrigDelay_);
	  }
   }
   else if (eAct == MM::AfterSet)
   {
	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		pProp->Get(OWLTrigDelay_);
		SetTrigDelay(OWLTrigDelay_);
	  }
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnTriggerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(triggerTimeout_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(triggerTimeout_);
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnUseSerialLog(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(gUseSerialLog[UNITSOPENMAP]);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(gUseSerialLog[UNITSOPENMAP]);
	  if(fidSerial[UNITSOPENMAP])
	  {
		  if(gUseSerialLog[UNITSOPENMAP])
			  fprintf(fidSerial[UNITSOPENMAP],"*** Serial Log ON ***\n");
		  else
			  fprintf(fidSerial[UNITSOPENMAP],"*** Serial Log OFF ***\n");
	  }
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnFrameInterval(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(frameInterval_);
   }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(frameInterval_);
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnVideoPeak(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
	   {
		  long val = GetVideoPeak();
	      pProp->Set(val);
	   }
   }
   else if (eAct == MM::AfterSet)
   {
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnVideoAvg(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	if(ForceUpdate_)
	{
	  long val = GetVideoAvg();
      pProp->Set(val);
	}
   }
   else if (eAct == MM::AfterSet)
   {
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		if(!IsCapturing()) //(nCCDTempCount_>10)
		{
	  	  dCCDTemp_ = GetCCDTemp();
		  nCCDTempCount_ = 0;
		}
		else
			nCCDTempCount_++;

		if(nCCDTempCalibrated_)
			sprintf_s(strCCDTemp_, 64, "%.2f", dCCDTemp_);
		else
			sprintf_s(strCCDTemp_, 64, "%.0f (Uncalibrated)", dCCDTemp_);
		
	  pProp->Set(strCCDTemp_);
   }
   else if (eAct == MM::AfterSet)
   {
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnPCBTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(!IsCapturing()) //(nPCBTempCount_>10)
	   {
		  dPCBTemp_ = GetPCBTemp();
		  pProp->Set(dPCBTemp_);
		  nPCBTempCount_ = 0;
	   }
	   else
		   nPCBTempCount_++;
   }
   else if (eAct == MM::AfterSet)
   {
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnMicroReset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
         string strReset;
         pProp->Get(strReset);

         if (strReset.compare("Reset Now") == 0)
		 {	
//		   if (IsCapturing())
//			  return DEVICE_CAMERA_BUSY_ACQUIRING;
			    thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

			SetMicroReset();
			DisableMicro(); thd_->Resume();

			bool bForce = ForceUpdate_; 
		    ForceUpdate_ = true;
		    UpdateStatus();
		    ForceUpdate_ = bForce;

			//OnPropertyChanged(g_Keyword_ForceUpdate, g_Keyword_Off);
		 }
         pProp->Set("Off");
   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnExtTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	
   if (eAct == MM::BeforeGet)
   {
	  long val = ExtTrigStatus_;
	  if(ForceUpdate_ )
		  ExtTrigStatus_ = GetExtTrigStatus();

	  // convert to Falcon/KITE status values
	  if(_IS_CAMERA_OWL_FAMILY)
	  {
			if((val&0x40)==0)
				val=0;
			else if((val&0x20)==0)
				val=6; 
			else
				val=2;
	  }

	  if(val == 0)
	      pProp->Set(g_Keyword_Off);
	  else if(val == 2)
	      pProp->Set(g_Keyword_ExtTrigger_posTrig);
	  else if(val == 6)
	      pProp->Set(g_Keyword_ExtTrigger_negTrig);

	  ExtTrigStatus_ = val;
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
	   unsigned char val=0;

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_ExtTrigger_posTrig)==0)
			   val = 0x60;
		   else if(mode.compare(g_Keyword_ExtTrigger_negTrig)==0)
			   val = 0x40;
	   }
	   else
	   {
		   if(mode.compare(g_Keyword_ExtTrigger_posTrig)==0)
			   val = 2;
		   else if(mode.compare(g_Keyword_ExtTrigger_negTrig)==0)
			   val = 6;
	   }
	
	   thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	   SetLiveVideo(0);

	   SetExtTrigStatus(val);
	   triggerMode_ = val;
	   ExtTrigStatus_ = val;

//	   if(val==0 || 1)
	   {
		   SetLiveVideo(true);
		   captureMode_ = 1;
		   liveMode_ = 1;
	   }
/*	   else
	   {
		   SetLiveVideo(false);
		   captureMode_ = 0;
		   liveMode_ = 0;
	   }
*/	   DisableMicro(); thd_->Resume();
   }
   
   return DEVICE_OK;
}
int CRaptorEPIX::SetMaxExposureFixedFrameRate()
{
   if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1))
	   return DEVICE_OK;

   if(ExtTrigStatus_ != 0x02)
		return DEVICE_OK;

   double dMaxExp = 0.0, dVal1, dVal2, dMaxFrameRate;
   dMaxFrameRate = 80*1e6/(img_.Height()*1028+27989);
   FixedFrameRate_ = FixedFrameRate_ < dMaxFrameRate ? FixedFrameRate_ : dMaxFrameRate ;

   dVal1 = (1000.0/FixedFrameRate_) - (58.0/1000.0);
   dVal2 = (1000.0/dMaxFrameRate) - (58.0/1000.0);

   dMaxExp = dVal1 > dVal2 ? dVal1 : dVal2;

   if(exposure_ > dMaxExp)
   {
		exposure_ = dMaxExp;
		SetExposure(exposure_, false);
   }
   return DEVICE_OK;
}

int CRaptorEPIX::OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(!(cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1 ))
	   return DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_ )
		  ExtTrigStatus_ = GetExtTrigStatus();
	  long val = ExtTrigStatus_;

	  if(val & 0x40)
	  {
		  if(val & 0x80)
			  pProp->Set(g_Keyword_ExtTrigger_posTrig);
		  else
			  pProp->Set(g_Keyword_ExtTrigger_negTrig);
	  }
	  else
	  {
		  if(val & 0x02)
		      pProp->Set(g_Keyword_TrigFFR);
		  else
		      pProp->Set(g_Keyword_TrigITR);
	  }
	  ExtTrigStatus_ = val;
	  triggerMode_   = val & 0x40;
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
	   unsigned char val=0;
	   bool bAbort=false;

	   if(mode.compare(g_Keyword_TrigITR)==0)
		   val = 0x00;
	   else if(mode.compare(g_Keyword_TrigFFR)==0)
		   val = 0x02;
	   else if(mode.compare(g_Keyword_ExtTrigger_posTrig)==0)
		   val = 0x40|0x80;
	   else if(mode.compare(g_Keyword_ExtTrigger_negTrig)==0)
		   val = 0x40;
	   else if(mode.compare(g_Keyword_ExtTrigger_Abort)==0)
	   {
		   bAbort = true;
		   val = (unsigned char)ExtTrigStatus_ | 0x08 ;
	   }
	
	   thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	   //pxd_goAbortLive(UNITMASK);
	   SetLiveVideo(0);
	   SetExtTrigStatus(val);
	   triggerMode_ = val & 0x40;
	   ExtTrigStatus_ = val;
	   if(liveMode_)
	   {
			SetLiveVideo(1);
	   }
	   else if(bAbort==false)
	   {
			SetExtTrigStatus(1);
	   }

	   SetMaxExposureFixedFrameRate();

//	   if(val==0 || 1)
	   {
	   }
/*	   else
	   {
		   SetLiveVideo(false);
		   captureMode_ = 0;
		   liveMode_ = 0;
	   }
*/	   DisableMicro(); thd_->Resume();
   }
   
   return DEVICE_OK;
}


int CRaptorEPIX::OnFixedFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  FixedFrameRate_ = GetFixedFrameRate();
	  pProp->Set(FixedFrameRate_);	 
   }
   else if (eAct == MM::AfterSet)
   {
	   double val;
	   pProp->Get(val);
	   
	   FixedFrameRate_ = val;
	   SetMaxExposureFixedFrameRate();
	   if(FixedFrameRate_>0)
	   {
		   frameInterval_ = 0;
		   SetFixedFrameRate(FixedFrameRate_);
	   }
	   
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnFrameRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  OWLFrameRate_ = GetFrameRate();
	  int val = int(OWLFrameRate_);

	  if(val == 25)
	      pProp->Set(g_Keyword_FrameRate_25Hz);
	  else if(val == 30)
	      pProp->Set(g_Keyword_FrameRate_30Hz);
	  else if(val == 50)
	      pProp->Set(g_Keyword_FrameRate_50Hz);
	  else if(val == 60)
	      pProp->Set(g_Keyword_FrameRate_60Hz);
	  else if(val == 90)
	      pProp->Set(g_Keyword_FrameRate_90Hz);
	  else if(val == 120)
	      pProp->Set(g_Keyword_FrameRate_120Hz);
	  else
	      pProp->Set(g_Keyword_FrameRate_User);
	 
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
	   int val=0;
	   if(mode.compare(g_Keyword_FrameRate_25Hz)==0)
		   val = 25;
	   else if(mode.compare(g_Keyword_FrameRate_30Hz)==0)
		   val = 30;
	   else if(mode.compare(g_Keyword_FrameRate_50Hz)==0)
		   val = 50;
	   else if(mode.compare(g_Keyword_FrameRate_60Hz)==0)
		   val = 60;
	   else if(mode.compare(g_Keyword_FrameRate_90Hz)==0)
		   val = 90;
	   else if(mode.compare(g_Keyword_FrameRate_120Hz)==0)
		   val = 120;
	   else if(mode.compare(g_Keyword_FrameRate_User)==0)
		   val = 0;
	   
	   if(val>0)
		   SetFrameRate(val);

	   OWLFrameRate_ = val;

   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnFrameAccumulate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(FrameAccumulate_);
	 
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(FrameAccumulate_);
   }
	return DEVICE_OK;

}
int CRaptorEPIX::OnFrameRateUser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLFrameRate_ = GetFrameRate();
      pProp->Set(OWLFrameRate_);	 
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(OWLFrameRate_);
	   if(OWLFrameRate_>0)
		   SetFrameRate(OWLFrameRate_);
   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnCaptureMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
	   {
		  long val = GetLiveVideo();
		  captureMode_ = ((val&0x10)>0);
	   }
	  if(captureMode_ == 0)
	      pProp->Set(g_Keyword_CaptureMode_SnapShot);
	  else if(captureMode_ == 1)
	      pProp->Set(g_Keyword_CaptureMode_Live);
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
	   int val=0;
	   if(mode.compare(g_Keyword_CaptureMode_SnapShot)==0)
		   val = 0;
	   else if(mode.compare(g_Keyword_CaptureMode_Live)==0)
		   val = 1;

	   thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
	   SetLiveVideo(val>0);
	   DisableMicro(); thd_->Resume();

	   captureMode_ = val;
   }
	return DEVICE_OK;

}


int CRaptorEPIX::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  exposure_ = GetExposure();
	  pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
	    exposure_ = 0;
		pProp->Get(exposure_);
		SetMaxExposureFixedFrameRate();
		SetExposure(exposure_, false);

        std::ostringstream os, os2;
        os << exposure_;
        OnPropertyChanged(MM::g_Keyword_Exposure, os.str().c_str());

        os2 << exposureMax_;
        OnPropertyChanged(g_Keyword_ExposureMax, os2.str().c_str());

   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnTECSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
	      if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) == 0)
			  TECSetPoint_ = GetTECSetPoint();
	   pProp->Set(TECSetPoint_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(TECSetPoint_);
		SetTECSetPoint(TECSetPoint_);
   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnNUCState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLNUCState_ = GetNUCState();

	   int nuc = OWLNUCState_>>5;

	  if(nuc == 0)
		  pProp->Set(g_Keyword_NUCState0);
	  else if(nuc == 1)
		  pProp->Set(g_Keyword_NUCState1);
	  else if(nuc == 2)
		  pProp->Set(g_Keyword_NUCState2);
	  else if(nuc == 3)
		  pProp->Set(g_Keyword_NUCState3);
	  else if(nuc == 4)
		  pProp->Set(g_Keyword_NUCState4);
	  else if(nuc == 5)
		  pProp->Set(g_Keyword_NUCState5);
	  else if(nuc == 6)
		  pProp->Set(g_Keyword_NUCState6);
	  else if(nuc == 7)
		  pProp->Set(g_Keyword_NUCState7a);

	  if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
	  {
		  if((OWLNUCState_&0x10)>0)
		  {
			  if(nuc==4)
				  pProp->Set(g_Keyword_NUCState7b);
			  else if(nuc==0)
				  pProp->Set(g_Keyword_NUCState8);
		  }
	  }

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
//	   unsigned char val=0;

	   if(_IS_CAMERA_OWL_FAMILY)
	   {

		   if(mode.compare(g_Keyword_NUCState0)==0)
			   OWLNUCState_ = 0;
		   else if(mode.compare(g_Keyword_NUCState1)==0)
			   OWLNUCState_ = 1<<5;
		   else if(mode.compare(g_Keyword_NUCState2)==0)
			   OWLNUCState_ = 2<<5;
		   else if(mode.compare(g_Keyword_NUCState3)==0)
			   OWLNUCState_ = 3<<5;
		   else if(mode.compare(g_Keyword_NUCState4)==0)
			   OWLNUCState_ = 4<<5;
		   else if(mode.compare(g_Keyword_NUCState5)==0)
			   OWLNUCState_ = 5<<5;
		   else if(mode.compare(g_Keyword_NUCState6)==0)
			   OWLNUCState_ = 6<<5;
		   else if(mode.compare(g_Keyword_NUCState7a)==0)
			   OWLNUCState_ = 7<<5;
		   else

		   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
		   {
			   if(mode.compare(g_Keyword_NUCState7b)==0)
					OWLNUCState_ = 0x90;
				else if(mode.compare(g_Keyword_NUCState8)==0)
					OWLNUCState_ = 0x10;
		   }
		   	if(bBadPixel_)
				OWLNUCState_ |= 0x01;

		   SetNUCState(OWLNUCState_);   		
	   }
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnPeakAvgLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLPeakAvgLevel_ = GetPeakAvgLevel();
	  pProp->Set(OWLPeakAvgLevel_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(OWLPeakAvgLevel_); 
		SetPeakAvgLevel(OWLPeakAvgLevel_);
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnAutoLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLAutoLevel_ = GetAutoLevel();
	  pProp->Set(OWLAutoLevel_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(OWLAutoLevel_);
		SetAutoLevel(OWLAutoLevel_);
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnBlackOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLBlackOffset_ = GetBlackOffset();
	  pProp->Set(OWLBlackOffset_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(OWLBlackOffset_);
		SetBlackOffset(OWLBlackOffset_);
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnAGCSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
	   {
		  int nExp, nAGC;
		  GetAGCExpSpeed(&nAGC, &nExp);
		  OWLAGCSpeed_ = nAGC;
		  OWLExpSpeed_ = nExp;
	   }
	  pProp->Set(OWLAGCSpeed_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(OWLAGCSpeed_);
		  int nExp, nAGC;
		  GetAGCExpSpeed(&nAGC, &nExp);
		  OWLExpSpeed_ = nExp;
		SetAGCExpSpeed(OWLAGCSpeed_, OWLExpSpeed_);
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnExpSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
	   {
		  int nExp, nAGC;
		  GetAGCExpSpeed(&nAGC, &nExp);
		  OWLAGCSpeed_ = nAGC;
		  OWLExpSpeed_ = nExp;
	   }
	  pProp->Set(OWLExpSpeed_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(OWLExpSpeed_);
		  int nExp, nAGC;
		  GetAGCExpSpeed(&nAGC, &nExp);
		  OWLAGCSpeed_ = nAGC;
		SetAGCExpSpeed(OWLAGCSpeed_, OWLExpSpeed_);
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnROIAppearance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		OWLROIAppearance_ = GetROIAppearance();

	  if(OWLROIAppearance_ == 0)
		  pProp->Set(g_Keyword_ROI_Normal);
	  else if(OWLROIAppearance_ == 0x80)
		  pProp->Set(g_Keyword_ROI_Bright);
	  else if(OWLROIAppearance_ == 0x40)
		  pProp->Set(g_Keyword_ROI_Dark);
	  
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
//	   unsigned char val=0;

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_ROI_Normal)==0)
			   OWLROIAppearance_ = 0;
		   else if(mode.compare(g_Keyword_ROI_Bright)==0)
			   OWLROIAppearance_ = 0x80;
		   else if(mode.compare(g_Keyword_ROI_Dark)==0)
			   OWLROIAppearance_ = 0x40;
		   
		SetROIAppearance(OWLROIAppearance_);
	   }
   }
	return DEVICE_OK;
}

/////////////////////////////////////////////////////////
int CRaptorEPIX::OnHorizontalFlip(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
		  bHorizontalFlip_ = GetHorizontalFlip();
	  if(bHorizontalFlip_ == false)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   bHorizontalFlip_ = false;
		   else 
			   bHorizontalFlip_ = true;
		   
		   SetHorizontalFlip(bHorizontalFlip_);
	   }
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnBadPixel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
		  bBadPixel_ = GetBadPixel();
	  if(bBadPixel_ == false)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   bBadPixel_ = false;
		   else 
			   bBadPixel_ = true;
		   
		   SetBadPixel(bBadPixel_);
	   }
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnInvertVideo(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
		  bInvertVideo_ = GetInvertVideo();
	  if(bInvertVideo_ == false)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   bInvertVideo_ = false;
		   else 
			   bInvertVideo_ = true;
		   
		   SetInvertVideo(bInvertVideo_);
	   }
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnImageSharpen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
		  bImageSharpen_ = GetImageSharpen();
	  if(bImageSharpen_ == false)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   bImageSharpen_ = false;
		   else 
			   bImageSharpen_ = true;
		   
		   SetImageSharpen(bImageSharpen_);
	   }
   }
	return DEVICE_OK;
}
int CRaptorEPIX::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
	  {
		  nShutterMode_ = GetShutterMode();
	  }
	  if(nShutterMode_==0x00)
		  pProp->Set(g_Keyword_ShutterMode_Closed);
	  else if(nShutterMode_==0x01)
		  pProp->Set(g_Keyword_ShutterMode_Open);
	  else if(nShutterMode_==0x02)
		  pProp->Set(g_Keyword_ShutterMode_Exposure);
	  
   }
   else if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_ShutterMode_Closed)==0)
		nShutterMode_ = 0x00;
	  else if(mode.compare(g_Keyword_ShutterMode_Open)==0)
		nShutterMode_ = 0x01;
	  else if(mode.compare(g_Keyword_ShutterMode_Exposure)==0)
		nShutterMode_ = 0x02;

	  SetShutterMode(nShutterMode_);
   }
	return DEVICE_OK;
}

void CRaptorEPIX::SetShutterMode(int nMode)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char nVal = 0;
	nVal = (unsigned char)nMode;
    if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialWriteRaptorRegister1(UNITMASK, 0xA5, nVal);

	DisableMicro(); thd_->Resume();
}

int CRaptorEPIX::GetShutterMode()
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char nVal = 0;
	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialReadRaptorRegister1(UNITMASK, 0xA5, &nVal);
	
	DisableMicro(); thd_->Resume();
	return (int)nVal;
}

int CRaptorEPIX::OnShutterDelayOpen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
	  {
		  dShutterDelayOpen_ = GetShutterDelayOpen();
	  }
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(dShutterDelayOpen_);

	  SetShutterDelayOpen(dShutterDelayOpen_);
	  dShutterDelayOpen_ = GetShutterDelayOpen();
   }
	return DEVICE_OK;
}

int CRaptorEPIX::OnShutterDelayClose(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) 
	  {
		  dShutterDelayClose_ = GetShutterDelayClose();
	  }
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(dShutterDelayClose_);

	  SetShutterDelayClose(dShutterDelayClose_);
	  dShutterDelayClose_ = GetShutterDelayClose();
   }
	return DEVICE_OK;
}

double CRaptorEPIX::GetShutterDelayOpen() const
{

	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char nVal = 0;
	double dVal = 0.0;
	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialReadRaptorRegister1(UNITMASK, 0xA6, &nVal);

	dVal = double(nVal)*1.6384;
	DisableMicro(); thd_->Resume();
	return dVal;
}

double CRaptorEPIX::GetShutterDelayClose() const
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char nVal = 0;
	double dVal = 0.0;
	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialReadRaptorRegister1(UNITMASK, 0xA7, &nVal);

	dVal = double(nVal)*1.6384;
	DisableMicro(); thd_->Resume();
	return dVal;
}

void CRaptorEPIX::SetShutterDelayOpen(double dVal)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char uVal = 0;
	int nVal = 0;
	nVal = int((dVal/1.6384)+0.5);
	if(nVal>255)
		nVal = 255;

	uVal = (unsigned char)nVal;

	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialWriteRaptorRegister1(UNITMASK, 0xA6, uVal);

	DisableMicro(); thd_->Resume();
}

void CRaptorEPIX::SetShutterDelayClose(double dVal)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	unsigned char uVal = 0;
	int nVal = 0;

	nVal = int((dVal/1.6384)+0.5);
	if(nVal>255)
		nVal = 255;
	uVal = (unsigned char)nVal;

	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0)
		serialWriteRaptorRegister1(UNITMASK, 0xA7, uVal);

	DisableMicro(); thd_->Resume();
}


void CRaptorEPIX::SetHorizontalFlip(bool val) 
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		unsigned char ctrl;
		ctrl = (unsigned char) GetFPGACtrl();
		if(val)
			SetFPGACtrl(ctrl | 0x80);
		else
			SetFPGACtrl(ctrl & 0x7F);
	}
}
void CRaptorEPIX::SetBadPixel(bool val) 
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		unsigned char nuc;
		nuc = (unsigned char) GetNUCState();
		if(val)
			nuc |= 0x01;
		else
			nuc &= 0xFE;
		SetNUCState(nuc);
	}
}
void CRaptorEPIX::SetInvertVideo(bool val) 
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		unsigned char ctrl;
		ctrl = (unsigned char) GetFPGACtrl();
		if(val)
			SetFPGACtrl(ctrl | 0x40);
		else
			SetFPGACtrl(ctrl & 0xBF);
	}
}
void CRaptorEPIX::SetImageSharpen(bool val) 
{
	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		if(val)
			serialWriteRaptorRegister1(UNITMASK, 0xFD, 0x43 ) ;
		else
			serialWriteRaptorRegister1(UNITMASK, 0xFD, 0x22 ) ;
		DisableMicro(); thd_->Resume();
	}
}
bool CRaptorEPIX::GetHorizontalFlip() const
{
	unsigned char val = 0;
	if(_IS_CAMERA_OWL_FAMILY)
	{
		val = (unsigned char) GetFPGACtrl();
	}
	return ((val & 0x80)>0);
}
bool CRaptorEPIX::GetBadPixel() const
{
	unsigned char val = 0;
	if(_IS_CAMERA_OWL_FAMILY)
	{
		val = (unsigned char) GetNUCState();
	}
	return ((val & 0x01)>0);
}
bool CRaptorEPIX::GetInvertVideo() const
{
	unsigned char val = 0;
	if(_IS_CAMERA_OWL_FAMILY)
	{
		val = (unsigned char) GetFPGACtrl();
	}
	return ((val & 0x40)>0);
}
bool CRaptorEPIX::GetImageSharpen() const
{
	unsigned char val = 0;

	if(_IS_CAMERA_OWL_FAMILY)
	{
		thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
		serialReadRaptorRegister1(UNITMASK, 0xFD, &val ) ;
		DisableMicro(); thd_->Resume();
	}

	return (val==0x43);
}
 
/////////////////////////////////////////////////////////
int CRaptorEPIX::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_) OWLAutoExp_ = GetAutoExposure();
	  if(OWLAutoExp_ == 0)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
//	   unsigned char val=0;

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   OWLAutoExp_ = 0;
		   else 
			   OWLAutoExp_ = 1;
		   
		   SetAutoExposure(OWLAutoExp_);

		   if(OWLAutoExp_==0)
		   {
				Gain_ = GetGain();
 			    exposure_ = GetExposure();
		   }
	   }
   }
	return DEVICE_OK;
}

int CRaptorEPIX::OnHighGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  OWLHighGain_ = GetHighGain();
	  if(OWLHighGain_ == 0)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

	   if(_IS_CAMERA_OWL_FAMILY)
	   {
		   if(mode.compare(g_Keyword_Off)==0)
			   OWLHighGain_ = 0;
		   else 
			   OWLHighGain_ = 1;
		   
		   SetHighGain(OWLHighGain_);
	   }
   }
	return DEVICE_OK;
}

int CRaptorEPIX::OnHighPreAmpGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   if(ForceUpdate_)
		  bHighPreAmpGain_ = GetHighPreAmpGain()>0;
	  if(bHighPreAmpGain_==false)
		  pProp->Set(g_Keyword_Off);
	  else 
		  pProp->Set(g_Keyword_On);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);

		if(mode.compare(g_Keyword_Off)==0)
			bHighPreAmpGain_ = false;
		else 
			bHighPreAmpGain_ = true;
		   
		SetHighPreAmpGain(bHighPreAmpGain_);
   }
	return DEVICE_OK;
}

int CRaptorEPIX::OnForceUpdate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  pProp->Set(g_Keyword_On);
	  else 
		  pProp->Set(g_Keyword_Off);

   }
   else if (eAct == MM::AfterSet)
   {
	   string mode;
	   pProp->Get(mode);
	   if(mode.compare(g_Keyword_Off)==0)
		   ForceUpdate_ = false;
	   else 
	   {
		   if (IsCapturing())
			  return DEVICE_CAMERA_BUSY_ACQUIRING;

		   ForceUpdate_ = true;
	   }
   }
   return DEVICE_OK;
}

int CRaptorEPIX::OnUseDefaults(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) 
   {
	  pProp->Set(g_Keyword_Off);
   }
   else if (eAct == MM::AfterSet)
   {
	   string mode; 
	   pProp->Get(mode);
	   if(mode.compare(g_Keyword_Off)==0)
		   return DEVICE_OK;
	   else 
	   {
		   if (IsCapturing())
			  return DEVICE_CAMERA_BUSY_ACQUIRING;
 	       thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		   if(_NOT_CAMERA_OWL_FAMILY)
			   serialWriteRaptorRegister1(UNITMASK, 0xF2, 0x00);

		   pxd_goUnLive(UNITMASK);

		   int nRet;

		   /**********************************/
			cameraCCDXSize_ = pxd_imageXdim();
			cameraCCDYSize_ = pxd_imageYdim();
		    img2_.Resize(cameraCCDXSize_, cameraCCDYSize_, nBPP_);

			SetROI(0, 0, cameraCCDXSize_, cameraCCDYSize_);

		   if(_IS_CAMERA_OWL_FAMILY)
		   {
			   OWLAGCSpeed_ = 7;
			   OWLExpSpeed_ = 7;
			   OWLAutoLevel_ = 8192;
			   OWLPeakAvgLevel_ = 128;
			   OWLBlackOffset_ = 0;
			   SetAGCExpSpeed(OWLAGCSpeed_, OWLExpSpeed_);
  	   		   SetPeakAvgLevel(OWLPeakAvgLevel_);
			   SetAutoLevel(OWLAutoLevel_);
  	   		   SetBlackOffset(OWLBlackOffset_);

			   unsigned char val;
			   val = (unsigned char) GetFPGACtrl();
			   SetFPGACtrl(val | 0x01);

			   if(cameraType_ == _RAPTOR_CAMERA_OWL_640 || cameraType_ == _RAPTOR_CAMERA_OWL_NINOX_640 )
				   TECSetPoint_ = -15.0;
			   else
				   TECSetPoint_ = 15.0;

			   SetTECSetPoint(TECSetPoint_);

			   OWLHighGain_ = 1;  
			   SetHighGain(OWLHighGain_);
		   
			   OWLAutoExp_ = 1;
			   SetAutoExposure(OWLAutoExp_);
		   
			   OWLNUCState_ = 0x60;
			   SetNUCState(OWLNUCState_);

		       OWLFrameRate_ = 25;
			   SetFrameRate(OWLFrameRate_);

			   OWLROIAppearance_ = 0;
  	   		   SetROIAppearance(OWLROIAppearance_);


		   }
		   triggerMode_ = 0;

		   captureMode_ = 1;

		   nRet = UpdateStatus();
		   nRet = ResizeImageBuffer();

		   AOILeft_  = 0;
		   AOITop_   = 0;
		   AOIWidth_ = img_.Width();
		   AOIHeight_= img_.Height();

			initialized_ = true; 
			g_SerialOK = true;

   			captureMode_ = 1;
			triggerMode_ = 0;
			ExtTrigStatus_ = 0;
			SetExtTrigStatus((unsigned char)triggerMode_);
			SetLiveVideo(captureMode_>0);

		   if(_IS_CAMERA_OWL_FAMILY)
		   {
				serialWriteRaptorRegister1(UNITMASK, 0xF9, 0x60 ) ;
				SetFPGACtrl(0x03);
		   }

		   //pxd_goLive(UNITMASK, 1);
#ifdef DOLIVEPAIR
				if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
#endif			   
		   pxd_goLive(UNITMASK, 1);
		   if(_NOT_CAMERA_OWL_FAMILY)
			   serialWriteRaptorRegister1(UNITMASK, 0xF2, 0x10);


		   DisableMicro(); thd_->Resume();
		   /**********************************/


	   }
   }
   return DEVICE_OK;
}

int CRaptorEPIX::OnPostCaptureROI(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  if(PostCaptureROI_)
		  pProp->Set(g_Keyword_On);
	  else 
		  pProp->Set(g_Keyword_Off);

   }
   else if (eAct == MM::AfterSet)
   {
//       if(IsCapturing())
//            return DEVICE_CAMERA_BUSY_ACQUIRING;
	      thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);


	   string mode;
	   pProp->Get(mode);
	   if(mode.compare(g_Keyword_Off)==0)
		   PostCaptureROI_ = false;
	   else 
		   PostCaptureROI_ = true;

	   UpdateAOI();
	   DisableMicro(); thd_->Resume();
   }
   return DEVICE_OK;
}


int CRaptorEPIX::OnMaximumExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  pProp->Set(exposureMax_);
   }
   else if (eAct == MM::AfterSet)
   {
		pProp->Get(exposureMax_);

		SetPropertyLimits(MM::g_Keyword_Exposure, 0, exposureMax_);

		if(exposure_>exposureMax_)
		{
			exposure_ = exposureMax_;
			SetExposure(exposure_,false);
		}	

        //std::ostringstream os;
        //os << exposureMax_;
        //OnPropertyChanged(g_Keyword_ExposureMax, os.str().c_str());
   }
	return DEVICE_OK;

}


/**
* Handles "Binning" property.
*/
int CRaptorEPIX::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	//thd_->Suspend(); MMThreadGuard g(imgPixelsLock_);

   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);
			if(binFactor > 0 && binFactor <= 64)
			{

				int nX, nY;
				nX = img_.Width()*binSize_;
				nY = img_.Height()*binSize_;

				if(nX+binSize_>=cameraCCDXSize_)
					nX = cameraCCDXSize_;
				if(nY+binSize_>=cameraCCDYSize_)
					nY = cameraCCDYSize_;

				if((cameraType_ & (_RAPTOR_CAMERA_RGB))>0)
					SetBinningFactor(1);
				else
					SetBinningFactor(binFactor);
				//binFactor = GetBinningFactor();

				nX = int(nX/binFactor)*binFactor;
				nY = int(nY/binFactor)*binFactor;

				img_.Resize(nX/binFactor, nY/binFactor);
				binSize_ = binFactor;

				SetROIStatus(nX, nY, (roiX_/binFactor)*binFactor, (roiY_/binFactor)*binFactor );

				unsigned xSize, ySize;
				if(PostCaptureROI_)
				{
					AOIWidth_  = nX;
					AOIHeight_ = nY;
					AOILeft_   = (roiX_/binFactor)*binFactor ;
					AOITop_    = (roiY_/binFactor)*binFactor;
				}
				else
				{
					GetROIStatus(&xSize, &ySize, &roiX_, &roiY_);
					AOIWidth_  = xSize;
					AOIHeight_ = ySize;
					AOILeft_   = roiX_;
					AOITop_    = roiY_;
				}

				std::ostringstream os;
				os << binSize_;

/*			   if(((cameraType_ & (_RAPTOR_CAMERA_RGB)) > 0) || (cameraType_ == _RAPTOR_CAMERA_KINGFISHER_674 || cameraType_ == _RAPTOR_CAMERA_KINGFISHER_694  ))
			   {
					 char str[16];
					 sprintf_s(str,16,"%02d",binSize_);
					OnPropertyChanged("Binning", str);
					ret=DEVICE_OK;
			   }
			   else*/
			   {
					OnPropertyChanged("Binning", os.str().c_str());
					ret=DEVICE_OK;
			   }
			}
      }break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
		 if(ForceUpdate_)
		 {
			int nBin = GetBinningFactor();
			binSize_ = (nBin & 0x0F);
		 }

 		 pProp->Set(binSize_);
      }break;
   }
   return ret; 
}

/**
* Handles "PixelType" property.
*/
int CRaptorEPIX::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string pixelType;
         pProp->Get(pixelType);

         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
            nComponents_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 1);
            bitDepth_ = 8;
			nBPP_ = 1;
            ret=DEVICE_OK;
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
            nComponents_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 2);
			nBPP_ = 2;
            ret=DEVICE_OK;
			nDebayerMethod_ = 0;
         }
			else if ( pixelType.compare(g_PixelType_24bitRGB) == 0)
			{
            nComponents_ = 3;
			nBPP_ = 3;
            img_.Resize(img_.Width(), img_.Height(), 3);
            ret=DEVICE_OK;
			}
			else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
			{
            nComponents_ = 4;
			nBPP_ = 4;
            img_.Resize(img_.Width(), img_.Height(), 4);
            ret=DEVICE_OK;
			}
			else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
			{
            nComponents_ = 4;
			nBPP_ = 8;
            img_.Resize(img_.Width(), img_.Height(), 8);
            ret=DEVICE_OK;
			if(nDebayerMethod_ == 0)
				nDebayerMethod_ = 2;
			}
         else if ( pixelType.compare(g_PixelType_32bit) == 0)
			{
            nComponents_ = 1;
			nBPP_ = 4;
            img_.Resize(img_.Width(), img_.Height(), 4);
            ret=DEVICE_OK;
			}
         else
         {
            // on error switch to default pixel type
            nComponents_ = 1;
			nBPP_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 1);
            pProp->Set(g_PixelType_8bit);
            ret = ERR_UNKNOWN_MODE;
         }
      } break;
   case MM::BeforeGet:
      {
         long bytesPerPixel = GetImageBytesPerPixel();
         if (bytesPerPixel == 1)
         	pProp->Set(g_PixelType_8bit);
         else if (bytesPerPixel == 2)
         	pProp->Set(g_PixelType_16bit);
         else if (bytesPerPixel == 4)
         {
            if(4 == this->nComponents_) // todo SEPARATE bitdepth from #components
				   pProp->Set(g_PixelType_32bitRGB);
            else if( 1 == nComponents_)
               pProp->Set(::g_PixelType_32bit);
         }
         else if (bytesPerPixel == 8) // todo SEPARATE bitdepth from #components
				pProp->Set(g_PixelType_64bitRGB);
			else
				pProp->Set(g_PixelType_8bit);
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/**
* Handles "BitDepth" property.
*/
int CRaptorEPIX::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         long bitDepth;
         pProp->Get(bitDepth);

			unsigned int bytesPerComponent;

         switch (bitDepth) {
            case 8:
					bytesPerComponent = 1;
               bitDepth_ = 8;
               ret=DEVICE_OK;
            break;
            case 10:
					bytesPerComponent = 2;
               bitDepth_ = 10;
               ret=DEVICE_OK;
            break;
            case 12:
					bytesPerComponent = 2;
               bitDepth_ = 12;
               ret=DEVICE_OK;
            break;
            case 14:
					bytesPerComponent = 2;
               bitDepth_ = 14;
               ret=DEVICE_OK;
            break;
            case 16:
					bytesPerComponent = 2;
               bitDepth_ = 16;
               ret=DEVICE_OK;
            break;
            case 32:
               bytesPerComponent = 4;
               bitDepth_ = 32; 
               ret=DEVICE_OK;
            break;
            default: 
               // on error switch to default pixel type
					bytesPerComponent = 1;

               pProp->Set((long)8);
               bitDepth_ = 8;
               ret = ERR_UNKNOWN_MODE;
            break;
         }
			char buf[MM::MaxStrLength];
			GetProperty(MM::g_Keyword_PixelType, buf);
			std::string pixelType(buf);
			unsigned int bytesPerPixel = 1;
			

         // automagickally change pixel type when bit depth exceeds possible value
         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
				if( 2 == bytesPerComponent)
				{
					SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
					bytesPerPixel = 2;
				}
				else if ( 4 == bytesPerComponent)
            {
					SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bit);
					bytesPerPixel = 4;

            }else
				{
				   bytesPerPixel = 1;
				}
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
				bytesPerPixel = 2;
         }
			else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
			{
				bytesPerPixel = 4;
			}
			else if ( pixelType.compare(g_PixelType_32bit) == 0)
			{
				bytesPerPixel = 4;
			}
			else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
			{
				bytesPerPixel = 8;
			}
			img_.Resize(img_.Width(), img_.Height(), bytesPerPixel);

      } break;
   case MM::BeforeGet:
      {
         pProp->Set((long)bitDepth_);
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}
/**
* Handles "ReadoutTime" property.
*/
int CRaptorEPIX::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = readoutMs * 1000.0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		dropPixels_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(dropPixels_?1L:0L);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		saturatePixels_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(saturatePixels_?1L:0L);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      double tvalue = 0;
      pProp->Get(tvalue);
		fractionOfPixelsToDropOrSaturate_ = tvalue;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(fractionOfPixelsToDropOrSaturate_);
   }

   return DEVICE_OK;
}


int CRaptorEPIX::OnEPIXMultiUnitMask(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long val = 0;
      pProp->Get(val);

	  if(val!=MULTIUNITMASK)
	  {
		MULTIUNITMASK = (int)val;
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)MULTIUNITMASK);
   }

   return DEVICE_OK;
}
int CRaptorEPIX::OnEPIXUnit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long val = 0;
      pProp->Get(val);

	  if(val!=UNITSOPENMAP)
	  {
		UNITSOPENMAP = (int)val;
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
	   long val = UNITSOPENMAP; 
       pProp->Set(val);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnTECooler(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

   if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  unsigned char val;
		  val = (unsigned char) GetFPGACtrl();
		  if(mode.compare(g_Keyword_Off)==0)
			SetFPGACtrl(val & 0xFE);
		  else if(mode.compare(g_Keyword_On)==0)
			SetFPGACtrl(val | 0x01);
	  }
	  else
	  {
		  unsigned char val;
		  val = (unsigned char)GetFPGACtrl();
		  if(mode.compare(g_Keyword_Off)==0)
			SetFPGACtrl(val&0xFE);
		  else if(mode.compare(g_Keyword_On)==0)
			SetFPGACtrl(val|0x01);
		  else if(mode.compare(g_Keyword_TECooler_neg5oC)==0)
			SetFPGACtrl(0x01);
		  else if(mode.compare(g_Keyword_TECooler_neg20oC)==0)
		  {
			if(cameraType_ == _RAPTOR_CAMERA_KITE)
				SetFPGACtrl(3);
			else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
				SetFPGACtrl(0x11);
		  }	
		  else if(mode.compare(g_Keyword_TECooler_Reset)==0)
			SetFPGACtrl(val|0x02);

		  
	  }
	  FPGACtrl_ = GetFPGACtrl();
   }
   else if (eAct == MM::BeforeGet)
   {
	  int val;
      if(ForceUpdate_)
		  FPGACtrl_ = GetFPGACtrl();
	  val = FPGACtrl_ ;

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  if((val&0x01) > 0)
			  pProp->Set(g_Keyword_On);
		  else
			  pProp->Set(g_Keyword_Off);
	  }
	  else
	  {
		  if((val&0x01) == 0)
			  pProp->Set(g_Keyword_Off);
		  else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0))
		  {
			  pProp->Set(g_Keyword_On);
		  }
		  else if(cameraType_ == _RAPTOR_CAMERA_FALCON)
		  {
			  if(val==1)
				  pProp->Set(g_Keyword_TECooler_neg5oC);
			  else if(val==0x11)
				  pProp->Set(g_Keyword_TECooler_neg20oC);
		  }
		  else if(cameraType_ == _RAPTOR_CAMERA_KITE)
		  {
			  if(val==1)
				  pProp->Set(g_Keyword_TECooler_neg5oC);
			  else if(val==3)
				  pProp->Set(g_Keyword_TECooler_neg20oC); 
		  }
	  }	  
   }

   DisableMicro(); thd_->Resume();

   return DEVICE_OK;
}


int CRaptorEPIX::OnTECFan(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

   if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  unsigned char val;
		  val = (unsigned char) GetFPGACtrl();
		  if(mode.compare(g_Keyword_Off)==0)
			SetFPGACtrl(val & 0xFB);
		  else if(mode.compare(g_Keyword_On)==0)
			SetFPGACtrl(val | 0x04);
	  }

	  FPGACtrl_ = GetFPGACtrl();
   }
   else if (eAct == MM::BeforeGet)
   {
	  int val;
      if(ForceUpdate_)
		  FPGACtrl_ = GetFPGACtrl();
	  val = FPGACtrl_ ;

	  if(_IS_CAMERA_OWL_FAMILY)
	  {
		  if((val&0x04) > 0)
			  pProp->Set(g_Keyword_On);
		  else
			  pProp->Set(g_Keyword_Off);
	  }
   }

   DisableMicro(); thd_->Resume();

   return DEVICE_OK;
}

int CRaptorEPIX::OnAntiBloom(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  int val=0;
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_On)==0)
		val = 1;

	  SetBloomingStatus(val) ;
	  AntiBloom_ = val;
   }
   else if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  AntiBloom_ = GetBloomingStatus() ;

	  if(AntiBloom_==1)
         pProp->Set(g_Keyword_On);
	  else
	     pProp->Set(g_Keyword_Off);

   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) )
	   {
		  double val=0;
		  pProp->Get(val);

		  if(val==2000)
			  SetReadoutClock(2) ;
		  else 
			  SetReadoutClock(1) ;
		  readoutRate_ = val;
	   }
	   else
	   {
		  long val=0;
		  pProp->Get(val);

		  SetReadoutClock(val) ;
		  readoutRate_ = val;
	   }
   }
   else if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
	  {
		  if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE) )
		  {
			  int val = GetReadoutClock() ;
			  if(val==1)
				  readoutRate_ = 75;
			  else
				  readoutRate_ = 2000;
		  }
		  else
		  {
			  readoutRate_ = GetReadoutClock() ;
			  pProp->Set(readoutRate_);
		  }
	  }
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_ReadoutMode_Baseline)==0)
		readoutMode_ = 0;
	  else if(mode.compare(g_Keyword_ReadoutMode_CDS)==0 || mode.compare(g_Keyword_ReadoutMode_Normal)==0)
		readoutMode_ = 1;
	  else if(mode.compare(g_Keyword_ReadoutMode_TestPattern)==0)
		readoutMode_ = 4;
	  
	  SetReadoutMode((unsigned char)readoutMode_) ;
   }
   else if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  readoutMode_ = GetReadoutMode() ;

	  if(cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)
	  {
		if(readoutMode_ == 4)
			pProp->Set(g_Keyword_ReadoutMode_TestPattern);
		else
			pProp->Set(g_Keyword_ReadoutMode_Normal);
	  }
	  else
	  {
		  if(readoutMode_ == 0)
			  pProp->Set(g_Keyword_ReadoutMode_Baseline);
		  else if(readoutMode_ == 1)
			  pProp->Set(g_Keyword_ReadoutMode_CDS);
		  else if(readoutMode_ == 4)
			  pProp->Set(g_Keyword_ReadoutMode_TestPattern);
	  }
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnHighDynamicRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  int val=0;
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_On)==0)
		val = 1;

	  SetHighDynamicRange(val) ;
	  HighDynamicRange_ = val;
   }
   else if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  HighDynamicRange_ = GetHighDynamicRange() ;

	  if(HighDynamicRange_==1)
         pProp->Set(g_Keyword_On);
	  else
	     pProp->Set(g_Keyword_Off);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnNUCMap(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  int val=0;
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_On)==0)
		val = 1;

	  SetNUCMap(val) ;
	  NUCMap_ = val;
   }
   else if (eAct == MM::BeforeGet)
   {
	  if(ForceUpdate_)
		  NUCMap_ = GetNUCMap() ;

	  if(NUCMap_==1)
         pProp->Set(g_Keyword_On);
	  else
	     pProp->Set(g_Keyword_Off);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnTestPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

   if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_On)==0)
		serialWriteRaptorRegister1(UNITMASK, 0xE1, 0x04);
	  else
		serialWriteRaptorRegister1(UNITMASK, 0xE1, 0x00);
   }
   else if (eAct == MM::BeforeGet)
   {
	   unsigned char val;

	   if(ForceUpdate_)
	   {
		   serialReadRaptorRegister1(UNITMASK, 0xE1, &val);
		   TestPattern_ = val;
	   }

	   if(TestPattern_==0x04)
		 pProp->Set(g_Keyword_On);
	   else
	     pProp->Set(g_Keyword_Off);

   }
   DisableMicro(); thd_->Resume();
 
   return DEVICE_OK;
}
int CRaptorEPIX::OnDebayerMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_Debayer_None)==0)
		nDebayerMethod_ = 0;
	  else if(mode.compare(g_Keyword_Debayer_Nearest)==0)
		nDebayerMethod_ = 1;
	  else if(mode.compare(g_Keyword_Debayer_Bilinear)==0)
		nDebayerMethod_ = 2;		

	  if(nDebayerMethod_==0)
  		SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
	  else
  		SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);


   }
   else if (eAct == MM::BeforeGet)
   {
//	   unsigned char val;

	   if(nDebayerMethod_==0)
		 pProp->Set(g_Keyword_Debayer_None);
	   else if(nDebayerMethod_==1)
	     pProp->Set(g_Keyword_Debayer_Nearest);
	   else if(nDebayerMethod_==2)
	     pProp->Set(g_Keyword_Debayer_Bilinear);
   }
 
   return DEVICE_OK;
}

void CRaptorEPIX::UpdateAOI()
{
	roiSnapX_ = 0;
	roiSnapY_ = 0;
	snapBin_  = 1;

	if(_IS_CAMERA_OWL_FAMILY) 
	{
		AOIWidth_  /=4;
		AOIHeight_ /=4;
		AOILeft_   /=4;
		AOITop_    /=4;
		AOIWidth_  *=4;
		AOIHeight_ *=4;
		AOILeft_   *=4;
		AOITop_    *=4;
	}
	SetROIStatus(AOIWidth_, AOIHeight_, AOILeft_, AOITop_);
	GetROIStatus(&AOIWidth_, &AOIHeight_, &AOILeft_, &AOITop_);


	  if(_IS_CAMERA_OWL_FAMILY && PostCaptureROI_==0)
	      img_.Resize(cameraCCDXSize_, cameraCCDYSize_);
	  else
		img_.Resize(AOIWidth_/binSize_, AOIHeight_/binSize_);
}

int CRaptorEPIX::OnUseAOI(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

	  string mode;
      pProp->Get(mode);

	  if(mode.compare(g_Keyword_On)==0)
	  {
		useAOI_ = true;
	   thd_->Stop(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

	   if(LastAOIWidth_>0 && LastAOIHeight_>0)
	   {
			AOIWidth_  = LastAOIWidth_  ;
			AOIHeight_ = LastAOIHeight_ ;
			AOILeft_   = LastAOILeft_   ;
			AOITop_    = LastAOITop_    ;
	   }

		img_.Resize(AOIWidth_/binSize_, AOIHeight_/binSize_);
		UpdateAOI();
		DisableMicro(); thd_->Resume();
	  }
	  else
	  {
		LastAOIWidth_  = AOIWidth_  ;
		LastAOIHeight_ = AOIHeight_ ;
		LastAOILeft_   = AOILeft_   ;
		LastAOITop_    = AOITop_    ;
		useAOI_ = false;
		ClearROI();
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
		if(useAOI_)
			pProp->Set(g_Keyword_On);
		else
			pProp->Set(g_Keyword_Off);

//	    UpdateAOI();
   }

   return DEVICE_OK; 
}

int CRaptorEPIX::OnAOILeft(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;
	   thd_->Stop(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		unsigned x, xSize, y, ySize;
		long lx;
		pProp->Get(lx);

	    if((cameraType_ & _RAPTOR_CAMERA_RGB)>0)
			lx = (lx/2)*2;

		x = (unsigned)lx;
		AOILeft_ = x;
		xSize = AOIWidth_;
		ySize = AOIHeight_;
		y = AOITop_;

		if(x+xSize > (unsigned)cameraCCDXSize_)
		{
			xSize = cameraCCDXSize_ - x;
			AOIWidth_ = xSize;
			std::ostringstream osXS;
			osXS << xSize;
			SetProperty(g_Keyword_AOI_Width, osXS.str().c_str());
			if(useAOI_)
				img_.Resize(xSize/binSize_,ySize/binSize_);
		}
		if(useAOI_)
		{
			UpdateAOI();
		}
		DisableMicro(); thd_->Resume();
   }
   else if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)AOILeft_);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnAOITop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;
	   thd_->Stop(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		unsigned x, y, xSize, ySize;
		long ly;
		pProp->Get(ly);
	    if((cameraType_ & _RAPTOR_CAMERA_RGB)>0)
			ly = (ly/2)*2;

		y = (unsigned)ly;
		AOITop_ = y;
		xSize = AOIWidth_;
		ySize = AOIHeight_;
		x = AOILeft_;

		if(y+ySize > (unsigned)cameraCCDYSize_)
		{
			ySize = cameraCCDYSize_ - y;
			AOIHeight_ = ySize;
			std::ostringstream osYS;
			osYS << ySize;
			SetProperty(g_Keyword_AOI_Height, osYS.str().c_str());
			if(useAOI_)
				img_.Resize(xSize/binSize_, ySize/binSize_);
		}
		if(useAOI_)
		{
			UpdateAOI();
		}
	   //UpdateAOI(false);
		DisableMicro(); thd_->Resume();
   }
   else if (eAct == MM::BeforeGet) 
   {
	   pProp->Set((long)AOITop_);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnAOIWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   if (eAct == MM::AfterSet)
   {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;
		thd_->Stop(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);


		unsigned x, y, xSize, ySize;
		long lxSize;
		pProp->Get(lxSize);
		if(lxSize >= cameraCCDXSize_-1)
			lxSize = cameraCCDXSize_;

	    if((cameraType_ & _RAPTOR_CAMERA_RGB)>0)
			lxSize = (lxSize/2)*2;

		xSize = (unsigned)lxSize;
		AOIWidth_ = xSize;
		ySize = AOIHeight_;
		x = AOILeft_;
		y = AOITop_;

		if(x+xSize > (unsigned)cameraCCDXSize_)
		{
			x = cameraCCDXSize_ - xSize;
			AOILeft_ = x;
			std::ostringstream osX;
			osX << x;
			SetProperty(g_Keyword_AOI_Left, osX.str().c_str());
		}
		if(useAOI_)
		{
			img_.Resize(xSize/binSize_,ySize/binSize_);
			UpdateAOI();
		}
		DisableMicro(); thd_->Resume();
   }
   else if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)AOIWidth_);
   }

   return DEVICE_OK;
}

int CRaptorEPIX::OnAOIHeight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;
	   thd_->Stop(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);

		unsigned x, y, xSize, ySize;
		long lySize;
		pProp->Get(lySize);
		if(lySize >= cameraCCDYSize_-1)
			lySize = cameraCCDYSize_;

	    if((cameraType_ & _RAPTOR_CAMERA_RGB)>0)
			lySize = (lySize/2)*2;

		ySize = (unsigned)lySize;
		AOIHeight_ = ySize;
		xSize = AOIWidth_;
		x = AOILeft_;
		y = AOITop_;

		if(y+ySize > (unsigned)cameraCCDYSize_)
		{
			y = cameraCCDYSize_ - ySize;
			AOITop_ = y;
			std::ostringstream osY;
			osY << y;
			SetProperty(g_Keyword_AOI_Top, osY.str().c_str());
		}
		if(useAOI_)
		{
			img_.Resize(xSize/binSize_, ySize/binSize_);
			UpdateAOI();
		}
		DisableMicro(); thd_->Resume();
   }
   else if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)AOIHeight_);
   }

   return DEVICE_OK;
}


/*
* Handles "ScanMode" property.
* Changes allowed Binning values to test whether the UI updates properly
*/
int CRaptorEPIX::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 

   if (eAct == MM::AfterSet) {
      pProp->Get(scanMode_);
      SetAllowedBinning();
      if (initialized_) {
         int ret = OnPropertiesChanged();
         if (ret != DEVICE_OK)
            return ret;
      }
   } else if (eAct == MM::BeforeGet) {
      LogMessage("Reading property ScanMode", true);
      pProp->Set(scanMode_);
   }
   return DEVICE_OK;
}




int CRaptorEPIX::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
		pProp->Set(cameraCCDXSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDXSize_)
		{
			cameraCCDXSize_ = value;
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
		pProp->Set(cameraCCDYSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (33000 < value))
			return DEVICE_ERR;  // invalid image size
		if( value != cameraCCDYSize_)
		{
			cameraCCDYSize_ = value;
			img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_);
		}
   }
	return DEVICE_OK;

}

int CRaptorEPIX::OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerDevice_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerDevice_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Private CRaptorEPIX methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CRaptorEPIX::ResizeImageBuffer()
{

   char buf[MM::MaxStrLength];
   //int ret = GetProperty(MM::g_Keyword_Binning, buf);
   //if (ret != DEVICE_OK)
   //   return ret;
   //binSize_ = atol(buf);

   int ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

	std::string pixelType(buf);
	int byteDepth = 0;

   if (pixelType.compare(g_PixelType_8bit) == 0)
   {
      byteDepth = 1;
   }
   else if (pixelType.compare(g_PixelType_16bit) == 0)
   {
      byteDepth = 2;
   }
	else if ( pixelType.compare(g_PixelType_24bitRGB) == 0)
	{
      byteDepth = 3;
	}
	else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
      byteDepth = 4;
	}
	else if ( pixelType.compare(g_PixelType_32bit) == 0)
	{
      byteDepth = 4;
	}
	else if ( pixelType.compare(g_PixelType_64bitRGB) == 0)
	{
      byteDepth = 8;
	}

   img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_, byteDepth);
   return DEVICE_OK;
}

void CRaptorEPIX::GenerateEmptyImage(ImgBuffer& img)
{
   //thd_->Suspend(); MMThreadGuard g(imgPixelsLock_);
   thd_->Suspend(); MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);

   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;
   unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
   memset(pBuf, 0, img.Height()*img.Width()*img.Depth());
   thd_->Suspend(); MMThreadGuard g(g_serialLock_[UNITSOPENMAP]);
}


void mySleep(double nTime_ms)
{
#if defined (MMLINUX32) || defined(MMLINUX64)
	Sleep(nTime_ms);
#else

	if(nTime_ms<0.0 )
		return;

	if(nTime_ms!=nTime_ms)
		return;

	double clock_current, clock_start, dSleep;

	clock_start = myClock();

	if(nTime_ms>=200)
	{
#if defined (WIN32) || defined(WIN64)
		Sleep((DWORD)(nTime_ms-100));
#else
		Sleep((int)(nTime_ms-100));
#endif
	}


	clock_current = myClock();

	dSleep = (nTime_ms)/1000.0f;

	while(dSleep > (clock_current - clock_start))
	{
		clock_current = myClock();
	}
	dSleep = clock_current - clock_start;
#endif
}

double myClock()
{
#if defined (WIN32) || defined(WIN64)
	bool bAccurate=true;
	double dTime;

	static LARGE_INTEGER m_ticksPerSecond = {0};
	static LARGE_INTEGER m_ticksPerSecondOld = {0};
	static double dLastQPFUpdate = 0.0; 
	static double dStartTime = 0.0;
	static clock_t clockStart;
	clock_t clockCurrent;
	LARGE_INTEGER tick;   // A point in time

	// get the high resolution counter's accuracy
	if(m_ticksPerSecond.QuadPart==0)
	{
		QueryPerformanceFrequency(&m_ticksPerSecond);
	}
	// what time is it?
 
	if(bAccurate)
	{
		QueryPerformanceCounter(&tick);
		dTime = double(tick.QuadPart)/double(m_ticksPerSecond.QuadPart);
	}
	else
	{
		clockCurrent = clock();
		dTime = double(clockCurrent)/double(CLOCKS_PER_SEC);
	}

	return dTime;
#else
	double dTime;

	struct timeval start;

        long seconds, useconds;    

        gettimeofday(&start, NULL);

        dTime = (double)start.tv_sec;
	dTime += ((double)start.tv_usec)/1.0e6;

	return dTime;

#endif
}
 
/**
* Get new image.
*/
int CRaptorEPIX::GetNewEPIXImage(ImgBuffer& img, double exp)
{
	if(!thd_->IsStopped() && thd_->IsSuspended())
		return DEVICE_OK;

   //thd_->Suspend(); MMThreadGuard g(imgPixelsLock_); 
   MMThreadGuard g2(g_serialLock_[UNITSOPENMAP]);

 
	//std::string pixelType;
	char bufch[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_PixelType, bufch);
	std::string pixelType(bufch);

	if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return DEVICE_SNAP_IMAGE_FAILED;

    int err = 0;
    //
    // Has a new field or frame been captured
    // since the last time we checked?
    // This check is redundant when using Events
    // (at least with a single unit) but it doesn't hurt.
    //
	
/*	if(captureMode_==0 && triggerMode_==0 && 0)
	{
		captureMode_ = 1;
		fieldCount_ = pxd_capturedFieldCount(UNITMASK);
	   fieldCount1_ = pxd_buffersFieldCount(UNITMASK,1);
	   fieldCount2_ = pxd_buffersFieldCount(UNITMASK,2);

	   err = pxd_goUnLive(1); 
		err = pxd_goLivePair(1, 1, 2);
	   if(pxd_goLivePair(1, 1, 2)!=0)
		   pxd_goLive(1, 1);
		//err = pxd_goLive(1, 1);
		SetLiveVideo(true);
	}
*/

	double triggerTimeout1 = triggerTimeout_ > 2*exposure_/1000.0 ? triggerTimeout_ : 2*exposure_/1000.0 ;

	if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 )
	{
		triggerTimeout1 += 1.5*double(img_.Width())*double(img_.Height())/(double(readoutRate_)*1e6);
	}
	if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0 )
	{
		triggerTimeout1 += 3.0*double(binSize_)*double(img_.Width())*double(img_.Height())/(double(readoutRate_)*1e3);
	}
	

    for (int u = 0; u < 1; u++) 
	{

		pxbuffer_t  buf = 1;
	    unsigned short* pBuf  = const_cast<unsigned short*>((unsigned short*)img.GetPixels());
		unsigned short* pBuf2 = const_cast<unsigned short*>((unsigned short*)img2_.GetPixels());
		unsigned short* pBuf3 = const_cast<unsigned short*>((unsigned short*)img3_.GetPixels());
		int bufsize  = img.Height()*img.Width()*img.Depth();
		int bufsize2 = img2_.Height()*img2_.Width()*img2_.Depth();
		int bufsize3 = img3_.Height()*img3_.Width()*img3_.Depth();
		ulong loop = 0;
		ulong timeout = 100;
		if(timeout < (ulong)(2*exp))
			timeout = (ulong)(2*exp);

		if((cameraType_ & _RAPTOR_CAMERA_EAGLE_BASE)>0 )
		{
			timeout += ulong(3.0*double(binSize_)*double(img_.Width())*double(img_.Height())/(double(readoutRate_)));
		}

		//if(captureMode_==0 || triggerMode_>0)
		if(triggerMode_>0)
		{
			pxd_goAbortLive(UNITMASK); 
			captureMode_ = 0;

			if(triggerMode_==0)
			{
				if(_NOT_CAMERA_OWL_FAMILY) 
				{
					SetExtTrigStatus(1);
					ExtTrigStatus_ = 0;
				}
			}
			else 
			{
				//SetExtTrigStatus(triggerMode_);
				ExtTrigStatus_ = triggerMode_;

//				Sleep(exp*10);
//				SetExtTrigStatus(triggerMode_|1);
			} 

			lastcapttime[u] = pxd_capturedFieldCount(UNITMASK);

			//SetLiveVideo(true);
			if(triggerMode_==0)
			{
				buf = 1;
				err = pxd_doSnap(UNITMASK, buf, timeout); 
				if(err<0)
					return -1;
				
				//SetLiveVideo(false);
			}
			else
			{
				err = pxd_doSnap(UNITMASK, buf, (ulong)(triggerTimeout1*1000)); 
				if(err<0)
					return -1;
			}
			fieldCount_ = pxd_capturedFieldCount(UNITMASK);
			
		}		
		else
		{ 
			//** pxvbtime_t lasttime = pxd_capturedFieldCount(UNITMASK);
			//SetLiveVideo(true);
			//SetExtTrigStatus(0); 
			triggerMode_ = 0; 

			if(!thd_->IsStopped() && thd_->IsSuspended())
				return DEVICE_OK;

			if(!thd_->IsStopped() && imageCounter_>=0  && trigSnap_==0)
			{
				//** err = pxd_goUnLive(UNITMASK);
				buf = pxd_capturedBuffer(UNITMASK);
				if(buf==0)
				{
#ifdef DOLIVEPAIR
					if(pxd_goLivePair(UNITMASK, 1, 2)!=LIVEPAIRTEST)
#endif				   //if(err!=LIVEPAIRTEST)
					   pxd_goLive(UNITMASK, 1);
				}
				long fc;
				fc = pxd_capturedFieldCount(UNITMASK);
				//while(((fc==fieldCount_) || buf==0) && !thd_->IsStopped() )
				double time1, time2;
				time1 = myClock();
				time2 = time1;
				while(((fc==fieldCount_)) && !thd_->IsStopped() && time2-time1 < triggerTimeout1 )
				{
					//buf = pxd_capturedBuffer(UNITMASK);
					//if(buf==0)
					//	err = pxd_goLivePair(UNITMASK, 1,2);

					//fc1 = pxd_buffersFieldCount(UNITMASK,1);
					//fc2 = pxd_buffersFieldCount(UNITMASK,2);
					//if(fc1>fc2)
					//	fc = fc1;
					//else
					//	fc = fc2;

					//if(fc != pxd_capturedFieldCount(UNITMASK))
					fc = pxd_capturedFieldCount(UNITMASK);
					time2 = myClock();
				}

				if(time2-time1 >= triggerTimeout1)
					return -1;

				double curTime = myClock();
				myCaptureTime_ = curTime;
				buf = pxd_capturedBuffer(UNITMASK); 
				fieldBuffer_ = buf;
				
				if(buf==0)
					return -1;
				fieldCount_ = pxd_capturedFieldCount(UNITMASK);
				buf = buf;
  		        readoutStartTime_ = GetCurrentMMTime();

				/*uint32 ticks[2];
				pxd_buffersSysTicks2(UNITMASK, buf, ticks);

				long long lticks[2];
				lticks[0] = ticks[0];
				lticks[1] = ticks[1];
				lticks[1] = lticks[1] << 32; 
				lticks[0] += lticks[1];

				//double curTime = double(lticks[0])/1e7;
				*/
				if(mySequenceStartTime_==0.0)
				{
					mySequenceStartTime_ = curTime;
					myFrameDiffTime_ = 0.0;
				}
				else
					myFrameDiffTime_ = curTime - myReadoutStartTime_;
				myReadoutStartTime_ = curTime;   
			}
			else
			{ 
				buf = 1;
				err = 0;				
//				err = pxd_goUnLive(UNITMASK);
				//err = pxd_goAbortLive(UNITMASK);
				int fc1 = pxd_capturedFieldCount(UNITMASK);

				if(triggerMode_>1)
					timeout = (ulong)(triggerTimeout1*1000.0);
 
/*				err = pxd_doSnap(UNITMASK, buf, timeout); 
				int loop=0;
				while(err!=0 && loop<10)
				{
					loop++;
					err = pxd_doSnap(UNITMASK, buf, timeout); 
				}
*/
				ulong timeoutMax = 1000;

				double clockStart = myClock();
				double clockTimeout = clockStart + double(timeoutMax>timeout?timeoutMax:timeout)/1000.0;


				while(err==0 && fc1 == fieldCount_ && myClock() < clockTimeout)
				{
					mySleep(1);
					loop++;
					fc1 = pxd_capturedFieldCount(UNITMASK);
				}

				while(err == PXERRESOURCEBUSY && loop<(timeoutMax>timeout?timeoutMax:timeout))
				{
					err = pxd_goAbortLive(UNITMASK);
					err = pxd_doSnap(UNITMASK, buf, timeout); 
					loop++;
				} 

				fieldCount_ = pxd_capturedFieldCount(UNITMASK);
				buf = pxd_capturedBuffer(UNITMASK); 
				fieldBuffer_ = buf;
				myCaptureTime_ = myClock();
			      //readoutStartTime_ = GetCurrentMMTime();
				//err = pxd_goAbortLive(UNITMASK);
			}
			
			//** lastcapttime[u] = pxd_capturedFieldCount(UNITMASK);
			//** err2 = pxd_goUnLive(UNITMASK);
		}

		unsigned xSize, ySize, roiX, roiY;
	    //GetROIStatus(&xSize, &ySize, &roiX, &roiY);
		xSize = img_.Width();
		ySize = img_.Height();
		roiX = roiX_;
		roiY = roiY_;

		unsigned nStep;
		if(PostCaptureROI_) 
		{
			nStep = (cameraCCDXSize_/binSize_) ;
		}
		else
			nStep = img.Width();

		if(cameraType_ == _RAPTOR_CAMERA_KITE) 
		{
			if((binSize_==1 && (nStep!=(unsigned)cameraCCDXSize_)) || binSize_==2)
				nStep++;
//			else if(binSize_==4 && ((roiX/2)%2==1) || (nStep*(unsigned)binSize_!=xSize) )
//				nStep++;
		}
		else if(cameraType_ == _RAPTOR_CAMERA_FALCON) 
		{
			if(binSize_== 3 || binSize_== 5)
			{
				if(AOIWidth_==(unsigned)cameraCCDXSize_)
					nStep++;
			}
		}   

		if(cameraCCDXSize_==(long)xSize && cameraCCDYSize_==(long)ySize && binSize_==1 && PostCaptureROI_==0)
		{			
			if((cameraType_ & _RAPTOR_CAMERA_RGB)>0 )
			{
				if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 || ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0) )
				{
					if(nDebayerMethod_==2)
						err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_, cameraCCDYSize_, pBuf, bufsize/2, "BGRx");
					else
						err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_, cameraCCDYSize_, pBuf, bufsize/2, "Bayer");
				}
				else
				{
					err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_, cameraCCDYSize_, &pBuf2[0], bufsize2/2, "Grey");
					if(err>0)
						MyDebayer((unsigned short*)pBuf2, (unsigned short*)pBuf, cameraCCDXSize_, cameraCCDYSize_, cameraCCDXSize_*4, nDebayerMethod_);
				}
			}
			else
			{
				err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_, cameraCCDYSize_, pBuf, bufsize, "Grey");				
			}

		}
		else
		{
			memset(pBuf, 0, bufsize);
			memset(pBuf2, 0, bufsize2);

			if((cameraType_ & _RAPTOR_CAMERA_RGB)>0 )
			{
				if(binSize_>1)
				{
					if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0 || (cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0)
					{
						if(nDebayerMethod_==2)
							err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf3, bufsize3, "BGRx");
						else
							err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf3, bufsize3, "Bayer");
						//err = pxd_readushort(UNITMASK, buf, 0, 0, xSize, ySize, pBuf, bufsize, "BGRx");
						if(err==-1) // hack for changing binning and ROI without snapping 
						{
							xSize /= binSize_;
							ySize /= binSize_;

							if(nDebayerMethod_==2)
								err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf3, bufsize3, "BGRx");
							else
								err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf3, bufsize3, "Bayer");
						}
					}
					else
					{
						err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf2, bufsize2, "Grey");
						if(err==-1) // hack for changing binning and ROI without snapping 
						{
							xSize /= binSize_;
							ySize /= binSize_;
							err = pxd_readushort(UNITMASK, buf, 0, 0, xSize*binSize_, ySize*binSize_, pBuf2, bufsize2, "Grey");
						}
					}
					if(err>0 && (((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0) || ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0)))
					{
						if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)==0 && ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)==0))
							MyDebayer((unsigned short*)pBuf2, (unsigned short*)pBuf3, xSize*binSize_, ySize*binSize_, xSize*binSize_*4, nDebayerMethod_);

						unsigned short *p1, *p2, *p3, *p4, *q1, q2;
						for(unsigned int yy=0; yy<ySize; yy++)
						{
							p1 = &pBuf3[yy*xSize*binSize_*binSize_*4];
							p2 = &pBuf3[(yy*binSize_+1)*xSize*binSize_*4];
							p3 = &pBuf3[(yy*binSize_+2)*xSize*binSize_*4];
							p4 = &pBuf3[(yy*binSize_+3)*xSize*binSize_*4];
							q1 = &pBuf[yy*xSize*4];
							for(unsigned int xx=0; xx<xSize; xx++)
							{
								if(binSize_==2)
								{
									for(int cc=0; cc<4; cc++)
									{
										q1[cc] = p1[cc] + p1[cc+4] + p2[cc] + p2[cc+4];
									}
								}
								else //if(binSize_==4)
								{
									for(int cc=0; cc<4; cc++)
									{
										q1[cc] = 0;
										for(int xxx=1;xxx<binSize_;xxx++)
										{
											q2 = p1[cc + 4*xxx] + p2[cc + 4*xxx] + p3[cc + 4*xxx] + p4[cc + 4*xxx];
											if(long(q1[cc])+long(q2)<=65535)
												q1[cc] += q2;
											else
												q1[cc] = 65535;
										}
									}
								}

								q1 += 4;
								p1 += 4*binSize_;
								p2 += 4*binSize_;
								p3 += 4*binSize_;
								p4 += 4*binSize_;
							}
						}
					}
				}
				else
				{
					if((cameraType_ & _RAPTOR_CAMERA_KINGFISHER_BASE)>0  || ((cameraType_ & _RAPTOR_CAMERA_OSPREY_BASE)>0))
					{
						err = pxd_readushort(UNITMASK, buf, 0, 0, xSize, ySize, pBuf, bufsize, "BGRx");
					}
					else
					{
						err = pxd_readushort(UNITMASK, buf, 0, 0, xSize, ySize, pBuf2, bufsize2, "Grey");
						if(err>0)
							MyDebayer((unsigned short*)pBuf2, (unsigned short*)pBuf, xSize, ySize, xSize*4, nDebayerMethod_);
					}
				}
			}
			else if(((cameraType_ & _RAPTOR_CAMERA_COMMONCMDS1) > 0)) 
				//err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_, pBuf2, bufsize2, "Grey");
				err = pxd_readushort(UNITMASK, buf, 0, 0, xSize, ySize, pBuf2, bufsize2, "Grey");
			else
				err = pxd_readushort(UNITMASK, buf, 0, 0, cameraCCDXSize_, cameraCCDYSize_, pBuf2, bufsize2, "Grey");
 
			if((cameraType_ & _RAPTOR_CAMERA_RGB)==0 )
			{
				if(_IS_CAMERA_OWL_FAMILY) 
				{
					nStep = cameraCCDXSize_ ;

					if(PostCaptureROI_) 
					{
						for(unsigned int yy=0; yy<img.Height();yy++)
							memcpy(&pBuf[(yy)*img.Width()], &pBuf2[(yy+AOITop_)*nStep+AOILeft_], img.Width()*sizeof(unsigned short));
					}
					else
					{
						for(unsigned int yy=0; yy<img.Height();yy++)
							memcpy(&pBuf[yy*img.Width()], &pBuf2[(yy)*nStep], img.Width()*sizeof(unsigned short));
					}
				}
				else if(PostCaptureROI_) 
				{
					nStep = cameraCCDXSize_/binSize_ ;

					for(unsigned int yy=0; yy<img.Height();yy++)
						memcpy(&pBuf[(yy)*img.Width()], &pBuf2[(yy+(AOITop_/binSize_))*nStep+(AOILeft_/binSize_)], img.Width()*sizeof(unsigned short));
				}
				else 
				{
					for(unsigned int yy=0; yy<img.Height();yy++)
						memcpy(&pBuf[yy*img.Width()], &pBuf2[(yy)*nStep], img.Width()*sizeof(unsigned short));
				}
			}
		}

	    roiSnapX_ = roiX;
	    roiSnapY_ = roiY;
	    snapWidth_ = img.Width();
	    snapHeight_ = img.Height();
		snapBin_ = binSize_;
 
		
		FrameCount_++;
/*		if(!IsCapturing() && 0)
		{
			FrameCount_ = 0;
			exposure_ = GetExposureCore(); 
			dPCBTemp_ = GetPCBTemp(true);
			dCCDTemp_ = GetCCDTemp(true);
		}
*/

/*		bSuspend_ = false;
		exposure_ = GetExposureCore();
		if(_IS_CAMERA_OWL_FAMILY) 
		{
			dPCBTemp_ = GetPCBTemp();
			dCCDTemp_ = GetCCDTemp();
		}
		bSuspend_ = true;
*/ 
//** 		if(captureMode_>0)
//** 			err2 = pxd_goLive(UNITMASK, 1);

		//if(thd_->IsStopped())
		//	err = pxd_goLivePair(UNITMASK, 1,2);

		if (err < 0)
		{
#if defined(WIN32) || defined(WIN64)
			MessageBox(NULL, pxd_mesgErrorCode(err), "pxd_readushort", MB_OK|MB_TASKMODAL);
#else
			LogMessage(pxd_mesgErrorCode(err));
#endif
			return DEVICE_SNAP_IMAGE_FAILED;
		}

    } 

	myCaptureTime2_ = myClock();
	return DEVICE_OK;
}

void CRaptorEPIX::MyDebayer(unsigned short* pInput, unsigned short* pOutput, int nWidth, int nHeight, int nStep, int nMethod)
{
	unsigned short *r, *g, *b, *a, *p0, *p1, *p2, *p3;

	if(nMethod==0)
	{
		for(int yy=0; yy<nHeight; yy++)
			memcpy(&pOutput[yy*nWidth], &pInput[yy*nWidth], nWidth*sizeof(unsigned short));

/*		for(int yy=0; yy<nHeight; yy+=2)
		{
			p1 = &pInput[yy*nWidth];
			p2 = &pInput[(yy+1)*nWidth];
			b = &pOutput[yy*nWidth*4+0];
			g = &pOutput[yy*nWidth*4+1];
			r = &pOutput[yy*nWidth*4+2]; 
			a = &pOutput[yy*nWidth*4+3];

			for(int xx=0; xx<nWidth; xx+=2)
			{
				r[0] = p1[0];
				r[4] = 0;
				r[nStep] = 0;
				r[nStep+4] = 0;

				g[0] = 0;
				g[4] = p1[1];
				g[nStep]   = p2[0];
				g[nStep+4] = 0;

				b[0] = 0;
				b[4] = 0;
				b[nStep]   = 0;
				b[nStep+4] = p2[1];

				a[0] = 0;
				a[4] = 0;
				a[nStep]   = 0;
				a[nStep+4] = 0;

				r+=8; 
				g+=8;
				b+=8;
				a+=8;

				p1+=2;
				p2+=2;
			}
		}*/
	}
	else if(nMethod==1)
	{
		for(int yy=0; yy<nHeight; yy+=2)
		{
			p1 = &pInput[yy*nWidth];
			p2 = &pInput[(yy+1)*nWidth];
			b = &pOutput[yy*nWidth*4+0];
			g = &pOutput[yy*nWidth*4+1];
			r = &pOutput[yy*nWidth*4+2]; 
			a = &pOutput[yy*nWidth*4+3];

			for(int xx=0; xx<nWidth; xx+=2)
			{
				r[0] = p1[0];
				r[4] = p1[0];
				r[nStep] = p1[0];
				r[nStep+4] = p1[0];

				g[0] = p1[1];
				g[4] = p1[1];
				g[nStep]   = p2[0];
				g[nStep+4] = p2[0];

				b[0] = p2[1];
				b[4] = p2[1];
				b[nStep]   = p2[1];
				b[nStep+4] = p2[1];

				a[0] = 0;
				a[4] = 0;
				a[nStep]   = 0;
				a[nStep+4] = 0;

				r+=8; 
				g+=8;
				b+=8;
				a+=8;

				p1+=2;
				p2+=2;
			}
		}
	}
	else if(nMethod==2)
	{
		for(int yy=0; yy<nHeight; yy+=2)
		{
			p0 = &pInput[(yy-1)*nWidth];
			p1 = &pInput[yy*nWidth];
			p2 = &pInput[(yy+1)*nWidth];
			p3 = &pInput[(yy+2)*nWidth];
			b = &pOutput[yy*nWidth*4+0];
			g = &pOutput[yy*nWidth*4+1];
			r = &pOutput[yy*nWidth*4+2]; 
			a = &pOutput[yy*nWidth*4+3];

			for(int xx=0; xx<nWidth; xx+=2)
			{
				if(xx==1050 && yy==1148)
					xx=1050;
				if(xx==1050 && yy==1150)
					xx=1050;

				if(xx>0 && yy>0 && xx<(nWidth-2) && yy<(nHeight-2))
				{
					r[0] = p1[0];
					r[4] = (unsigned short)((int(p1[0]) + int(p1[2]))/2);
					r[nStep] = (unsigned short)((int(p1[0]) + int(p3[0]))/2);
					r[nStep+4] = (unsigned short)((int(p1[0]) + int(p1[2]) + int(p3[0]) + int(p3[2]))/4);
				
					g[0] = (unsigned short)((int(p1[1]) + int(p1[-1]) + int(p2[0]) + int(p0[0]))/4);
					g[4] = p1[1];
					g[nStep]   = p2[0];
					g[nStep+4] = (unsigned short)((int(p1[1]) + int(p2[0]) + int(p3[1]) + int(p2[2]))/4);

					b[0] = (unsigned short)((int(p2[1]) + int(p2[-1]) + int(p0[1]) + int(p0[-1]))/4);
					b[4] = (unsigned short)((int(p2[1]) + int(p0[1]))/2);
					b[nStep]   = (unsigned short)((int(p2[1]) + int(p2[-1]))/2);
					b[nStep+4] = p2[1]; 

					a[0] = 0;
					a[4] = 0;
					a[nStep]   = 0;
					a[nStep+4] = 0;
				}
				else
				{
					r[0] = p1[0];
					r[4] = p1[0];
					r[nStep] = p1[0];
					r[nStep+4] = p1[0];

					g[0] = p1[1];
					g[4] = p1[1];
					g[nStep]   = p2[0];
					g[nStep+4] = p2[0];

					b[0] = p2[1];
					b[4] = p2[1];
					b[nStep]   = p2[1];
					b[nStep+4] = p2[1];

					a[0] = 0;
					a[4] = 0;
					a[nStep]   = 0;
					a[nStep+4] = 0;
				}
				r+=8; 
				g+=8;
				b+=8;
				a+=8;

				p0+=2;
				p1+=2;
				p2+=2;
				p3+=2;
			}
		}
	}
}

void CRaptorEPIX::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if(recurse)
      TestResourceLocking(false);
}

