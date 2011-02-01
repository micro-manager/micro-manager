///////////////////////////////////////////////////////////////////////////////
// FILE:          SpotDevice.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Diagnostic Spot Camera Implementation
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           
//

#pragma once
#include <string>
#include <map>
#include <vector>
#include <sstream>

#ifdef _WINDOWS
#include "../../../3rdparty/Spot/WINDOWS/SpotCam/SpotCam.h"
#define SpotAPI(X) (*p##X)
#else
// #define SpotAPI(X) (std::cerr << __LINE__ << ": Calling Spot API: " #X << std::endl, X)
#define SpotAPI(X) (X)
#ifdef __APPLE__
//EF for Spot header V5.0
//typedef struct
//{
//   int left, top;
//   int right, bottom;
//} SPOT_RECT;
//
//typedef SPOT_RECT Rect;
typedef struct
{
	short left, top;
	short right, bottom;
} RECT;
#define FALSE 0
#define TRUE 1

#define TARGET_OS_MAC 1
#include <SpotCam/SpotCam.h>
#endif
#endif

#include "CodeUtility.h"
namespace ExposureComputationImageTypeNS
{
enum ExposureComputationImageType { BrightField = 1, DarkField = 2 };
}

namespace TriggerModesNS
{
enum TriggerModes { None = 0, Edge = 1, Bulb = 2 };
}
// per communication from Diagnostic Instruments software engineers, value of 3 indicates Preemptable clearing

namespace ClearingModesNS
{
enum ClearingModes { Continuous = 1, Preemptable =3, Never=4 };  //  header file seems to be obsolete, only modes
}


using namespace ExposureComputationImageTypeNS;
using namespace TriggerModesNS;
using namespace ClearingModesNS;



// on Flex camera are 1 and 3

// from diagnostic spot example:

//#define USE_SPOTWAITFORSTATUSCHANGE


typedef struct
{
   char szModelNumber[11], szSerialNumber[21], szRevisionNumber[11];
   int  nMaxImageWidth, nMaxImageHeight, nMinImageWidth, nMinImageHeight;
   int  nMosaicPattern, nMaxPixelResolutionLevel, nNumReadoutCircuits, nNumGainPorts0;
   BOOL bCanDoColor, bHasMosaicSensor, bDoesMultiShotColor, bHasFilterWheel;
   BOOL bHasSlider, bCanDetectSliderPosition, bCanComputeExposure;
   BOOL bCanDoEdgeTrigger, bCanDoBulbTrigger, bCanSetTriggerActiveState;
   BOOL bCanReadSensorTemperature, bCanRegulateSensorTemperature;
   BOOL bCanDoAccurateTTLOutputAndTriggerDelayTiming;
   BOOL bCanDoLiveMode, bCanShiftImageSensor, bCanDoLiveImageScaling;
   BOOL bIs1394FireWireCamera;
} CAMERA_INFORMATION_STRUCT;



// typedefs for API functions
// from diagnostic spot example:
typedef int (WINAPI *SPOTSETVALUE)(short nParam, void *pValue);
typedef int (WINAPI *SPOTGETVALUE)(short nParam, void *pValue);
typedef int (WINAPI *SPOTGETCAMERAATTRIBUTES)(DWORD *pdwAttributes);
typedef void (WINAPI *SPOTGETVERSIONINFO2)(SPOT_VERSION_STRUCT2 *pstVerInfo);
typedef int (WINAPI *SPOTCOMPUTEEXPOSURE2)(SPOT_EXPOSURE_STRUCT2 *pstExposure);
typedef int (WINAPI *SPOTCOMPUTEWHITEBALANCE)(SPOT_WHITE_BAL_STRUCT *pstWhiteBal);
typedef int (WINAPI *SPOTGETIMAGE)(short nBPP, BOOL bQuickPic, short nSkipLines, void *pImageBuffer,
                                   long *plRedPixelCnts, long *plGreenPixelCnts, long *plBluePixelCnts);
typedef int (WINAPI *SPOTGETSEQUENTIALIMAGES)(int nNumImages, int nIntervalMSec, BOOL bAutoExposeOnEach,
                                              BOOL bUseTriggerOnEach, BOOL bDeferProcessing, void **ppImageBuffers);
typedef int (WINAPI *SPOTGETLIVEIMAGES)(BOOL bComputeExposure, short nFilterColor, short nRotateDirection,
                                        BOOL bFlipHoriz, BOOL bFlipVert, void *pImageBuffer);
typedef int (WINAPI *SPOTRETRIEVESEQUENTIALIMAGE)(void *pImageBuffer);
typedef void (WINAPI *SPOTSETABORTFLAG)(BOOL *pbAbort);
typedef void (WINAPI *SPOTSETCALLBACK)(SPOTCALLBACK pfnCallback, DWORD dwUserData);
typedef void (WINAPI *SPOTSETDEVICENOTIFICATIONCALLBACK)(SPOTDEVNOTIFYCALLBACK pfnCallback, DWORD dwUserData);
typedef void (WINAPI *SPOTCLEARSTATUS)(void);
typedef int (WINAPI *SPOTINIT)(void);
typedef int (WINAPI *SPOTEXIT)(void);
typedef int (WINAPI *SPOTFINDDEVICES)(SPOT_DEVICE_STRUCT *pstDevices, int *pnNumDevices);
typedef int (WINAPI *SPOTGETEXPOSURETIMESTAMP)(SPOT_TIMESTAMP_STRUCT *pstTimestamp);
typedef int (WINAPI *SPOTGETSENSORCURRENTTEMPERATURE)(short *pnTemperature, BOOL *pbIsNewValue);
typedef int (WINAPI *SPOTGETSENSOREXPOSURETEMPERATURE)(short *pnTemperature);
typedef BOOL (WINAPI *SPOTWAITFORSTATUSCHANGE)(int *pnStatus, long *plInfo, int nTimeoutMSec);
typedef int (WINAPI *SPOTGETCAMERAERRORCODE)(void);

typedef int (WINAPI *SPOTQUERYSTATUS)(BOOL bAbort, long *plInfo );
typedef int (WINAPI *SPOTGETACTUALGAIN)( short port, short gain, float* pvalue);


// forward declaration allows decoupling of Camera class from implementation class.
class SpotCamera;

class SpotDevice
{
public:
	SpotDevice(SpotCamera* pCamera__);
	bool DefineAPI();

	bool Initialize(std::string cameraName);
	void ShutdownCamera(void ); // exit the current camera leave spotcam.dll library loaded
	char* GetRawSpotImage();
	void SetupImageSequence(  const int nimages =SPOT_INFINITEIMAGES , const int interval = SPOT_INTERVALSHORTASPOSSIBLE);
	void SetupNonStopSequence(void);

	// returns the exposure time most recently calculated or set
	double ExposureTime(void); // exposure time in milliseconds, 
	// set the desired expsoure time, specified in milliseconds
	void ExposureTime(double value);
	// valid exposure range, milliseconds
	void ExposureLimits( double& minExp__, double& maxExp__);


	char* GetNextSequentialImage( unsigned int& imheight, unsigned int& imwidth, char& bytesppixel);
	~SpotDevice(void);
	std::map<int,std::string> AvailableSpotCameras(void){ return availableSpotCameras_;} ;
	// set ROI values
	void SetROI( const unsigned int x__, const unsigned int y__,  const unsigned intxSize__, const unsigned int ySize__);
	// get ROI values - the 'coordinate system' is the original pixel sensor array
	void GetROI(unsigned int& x__, unsigned int& y__, unsigned int& xSize__, unsigned int & ySize__, bool getFullSensorSize__ = false);

	// properties:
	void SelectedCameraIndex(const short value){selectedCameraIndexRequested_ = value;};
	short SelectedCameraIndex(void){ return selectedCameraIndexRequested_;};
	bool AutoExposure( void) const;
	void AutoExposure(const bool v__);
	void BinSize(const short value__);
	short BinSize(void);
	short Gain(void);
	void Gain(const short value__);
	double ActualGain(void);
	std::vector<short> SpotDevice::BinSizes();
	std::vector<short> PossibleIntegerGains( const short bitdepth__);
	std::string PixelSize(void);
	std::vector<short> PossibleBitDepths(void);
	short BitDepth(void);
	void BitDepth(short value);
	short BitDepthPerChannel(void){ return ( CanDoColor() ? BitDepth()/3 : BitDepth());};
	TriggerModes TriggerMode(void);
	void TriggerMode( const TriggerModes value__);
	
	// todo :
	//N.B. the color Spot cameras currently return monochrome when set to do pixel binning.
	// Per Diagnostic Instruments documentation, the camera should be queried for capability to do
	// color binning
	bool CanDoColor(void){ return (FALSE!=stCameraInfo_.bCanDoColor);};
	bool CanComputeExposure(void) { return (FALSE!=stCameraInfo_.bCanComputeExposure );};
	bool CanDoEdgeTrigger(void) { return ( FALSE!=stCameraInfo_.bCanDoEdgeTrigger);};
	bool CanDoBulbTrigger(void) { return ( FALSE!=stCameraInfo_.bCanDoBulbTrigger);};
	bool CanSetTriggerActiveState(void) {return ( FALSE!=stCameraInfo_.bCanSetTriggerActiveState);};

	bool DoesMultiShotColor(void){ return (FALSE!=stCameraInfo_.bDoesMultiShotColor);};


  // stCameraInfo_.bHasMosaicSensor = dwAttributes & SPOT_ATTR_MOSAIC;
   //stCameraInfo_.bDoesMultiShotColor = dwAttributes & SPOT_ATTR_COLORFILTER;
   //stCameraInfo_.bHasFilterWheel = dwAttributes & SPOT_ATTR_FILTERWHEEL;
   //stCameraInfo_.bHasSlider = dwAttributes & SPOT_ATTR_SLIDER;
   //stCameraInfo_.bCanDetectSliderPosition = dwAttributes & SPOT_ATTR_SLIDERPOSITIONDETECTION;

	bool CanReadSensorTemperature(void) {return (FALSE!=stCameraInfo_.bCanReadSensorTemperature);};
	bool CanRegulateSensorTemperature(void) {return (FALSE!= stCameraInfo_.bCanRegulateSensorTemperature);};
	bool CanDoAccurateTTLOutputAndTriggerDelayTiming(void) {return ( FALSE!=stCameraInfo_.bCanDoAccurateTTLOutputAndTriggerDelayTiming);};// = dwAttributes & SPOT_ATTR_ACCURATETTLDELAYTIMING;
  
	//stCameraInfo_.bCanDoLiveMode = dwAttributes & SPOT_ATTR_LIVEMODE;
   //stCameraInfo_.bCanShiftImageSensor = dwAttributes & SPOT_ATTR_SENSORSHIFTING;
   //stCameraInfo_.bIs1394FireWireCamera = dwAttributes & SPOT_ATTR_1394;
   //stCameraInfo_.bCanDoLiveImageScaling = dwAttributes & SPOT_ATTR_LIVEHISTOGRAM;

	void ExposureComputationImageSetting( const ExposureComputationImageType v);
	ExposureComputationImageType ExposureComputationImageSetting(void);
	
	double SensorTemperature(void);
	double SensorTemperatureSetpoint(void);
	void SensorTemperatureSetpoint(const double value__);
	std::pair<double,double> RegulatedTemperatureLimits(void);

	bool ChipDefectCorrection( void); // get current setting
	void ChipDefectCorrection(const bool value__); // set

	std::vector<ClearingModes> PossibleClearingModes(void);
	ClearingModes ClearingMode(void);
	void ClearingMode(ClearingModes value__);

	short NoiseFilterPercent(void);
	void NoiseFilterPercent(short value__);
	std::string ColorOrder(void);
	void ExposureTimeForMultiShot(double value__, long componentIndex__);
	double ExposureTimeForMultiShot( long componentIndex__);

	bool WaitForStatusChanged(const int completionState);
	void StopDevice(void);
	// This function is called by the SpotCam driver
	static void WINAPI CalledBackfromDriver(int iStatus, long lInfo, DWORD dwUserData);

private:
	// Spot Index & Spot Description
	std::map<int,std::string> availableSpotCameras_;
	SpotCamera* pMMCamera_;

	// this is the buffer read from the driver, rasters may be padded
	void* voidStarBuffer_;

	// this is the one-and-only compact, raw image buffer
	char* pbuf_;
	unsigned long sizeofbuf_;
	
	// bit depth of the acquistion
	int bitDepthOfADC_;

	// snap image timing
	double t0_;

public:


// code from here down to "end of spot example" adapted from example kindly provided by Diagnostic Instruments (Spot)

/*
http://www.diaginc.com/downloads/public/sdk/SpotCam.zip
*/

// To use SpotWaitForStatusChange (polling mode) instead of a callback, uncomment-out the next line
//#define USE_SPOTWAITFORSTATUSCHANGE

	int   nNumCameras_;
	short selectedCameraIndexRequested_;
	BOOL  bAbortFlag_;
	//BOOL bBusy;

	int   nImageWidth_, nImageHeight_, nImageBitDepth_;
	//BITMAPINFOHEADER *pBitmapInfo_;
	//BYTE  *pCameraPixelData_;
	//void* voidStarBuffer_;
	long nBufSize_;
	CAMERA_INFORMATION_STRUCT stCameraInfo_;
#ifdef _WINDOWS
	HINSTANCE hSpotCamDLL_;


	//API function declarations
	SPOTSETVALUE pSpotSetValue;
	SPOTGETVALUE pSpotGetValue;
	SPOTGETCAMERAATTRIBUTES pSpotGetCameraAttributes;
	SPOTGETVERSIONINFO2 pSpotGetVersionInfo2;
	SPOTCOMPUTEEXPOSURE2 pSpotComputeExposure2;
	SPOTCOMPUTEWHITEBALANCE pSpotComputeWhiteBalance;
	SPOTGETIMAGE pSpotGetImage;
	SPOTGETLIVEIMAGES pSpotGetLiveImages;
	SPOTGETSEQUENTIALIMAGES pSpotGetSequentialImages;
	SPOTRETRIEVESEQUENTIALIMAGE pSpotRetrieveSequentialImage;
	SPOTSETABORTFLAG pSpotSetAbortFlag;
	SPOTSETCALLBACK pSpotSetCallback;
	SPOTSETDEVICENOTIFICATIONCALLBACK pSpotSetDeviceNotificationCallback;
	SPOTCLEARSTATUS pSpotClearStatus;
	SPOTINIT pSpotInit;
	SPOTEXIT pSpotExit;
	SPOTFINDDEVICES pSpotFindDevices;
	SPOTGETEXPOSURETIMESTAMP pSpotGetExposureTimestamp;
	SPOTGETSENSORCURRENTTEMPERATURE pSpotGetSensorCurrentTemperature;
	SPOTGETSENSOREXPOSURETEMPERATURE pSpotGetSensorExposureTemperature;
	SPOTWAITFORSTATUSCHANGE pSpotWaitForStatusChange;
	SPOTGETCAMERAERRORCODE pSpotGetCameraErrorCode;
	SPOTQUERYSTATUS pSpotQueryStatus;
	SPOTGETACTUALGAIN pSpotGetActualGain;

#endif

	void InitializeCamera(void);

	//void ShutDown(void);
	//void GetWhiteBalance(void);
	//void ReadTemperature(void);

	// return true if the return value indicates success (Warning or SPOT_SUCCESS)
	// pass back a string message in case of warning or error 
	// 20090917 Kh
	// bassed on Display(Warning,Error) in SpotExample.c 
	bool ProcessSpotCode(const int spotCode, std::string& message);


};



class SpotBad  // exception to throw upon error in Spot device
{
public:

	SpotBad(){};
	SpotBad(const std::string areason):reason_(areason){};
	SpotBad(const char *const ptext):reason_(ptext){ };
	const char* ReasonText(void){ return reason_.c_str();};
	std::string reason_;

};








