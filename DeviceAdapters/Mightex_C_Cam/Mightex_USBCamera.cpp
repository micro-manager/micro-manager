///////////////////////////////////////////////////////////////////////////////
// FILE:          Mightex_USBCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the Mightex USB camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Yihui, mightexsystem.com, 05/30/2014
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                100X Imaging Inc, 2008
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
// CVS:           $Id: Mightex_USBCamera.h 12531 2014-05-30 16:25:20Z mark $
//

#include "Mightex_USBCamera.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>
#include <iostream>

#include "BUF_USBCCDCamera_SDK_Stdcall.h"


using namespace std;
const double CMightex_BUF_USBCCDCamera::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;
int OnExposureCnt = 0;

// External names used used by the rest of the system
// to load particular device from the "Mightex_USBCamera.dll" library
//const char* g_CameraDeviceName = "Mightex_USBCamera";
const char* g_CameraBUFCCDDeviceName = "Mightex_BUF_USBCCDCamera";
const char* g_Keyword_Resolution = "Resolution";
const char* g_Keyword_XStart = "X_Offset";
const char* g_Keyword_YStart = "Y_Offset";

//const char* g_Keyword_MyExposure = "Exposure Time"; 
//const char* g_Keyword_MyGain = "Gain Value"; 

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_32bitRGB = "32bitRGB";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   //RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "Mightex USB Camera");
   RegisterDevice(g_CameraBUFCCDDeviceName, MM::CameraDevice, "Mightex Buffer USB CCD Camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraBUFCCDDeviceName) == 0)
   {
      // create camera
      return new CMightex_BUF_USBCCDCamera();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


//////////////////////////////////////////////////////////////////////////
enum eDEVICETYPE {ICX424M, ICX424C, ICX205AL, ICX205AK, ICX285AL, ICX285AQ, ICX445AL, ICX445AQ, ICX274AL, ICX274AQ};
enum eWORKMODE { CONTINUE_MODE , EXT_TRIGGER_MODE };

const int NORMAL_FRAMES = 4;
const int TRIGGER_FRAMES= 24;
const int CAMERA_BUFFER_BW[] ={ 8, 8, 8, 8, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 4, 4 };
const int CAMERA_BUFFER_COLOR[] ={ 8, 8, 8, 8, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 4, 4 };

const int  MAX_RESOLUTIONS = 15;
const int  MAX_RESOLUTIONS_V032 = 3;  
const int  MAX_RESOLUTIONS_M001 = 13;
const int  MAX_RESOLUTIONS_T001 = 14;
const int  MAX_RESOLUTIONS_P001 = 15;
const int  MAX_EXPOSURETIME[]={5, 10, 100, 750};

const int DEFAULT_WIDTH = 640;//1392;
const int DEFAULT_HEIGHT = 480;//1040;
const int DEFAULT_SIZE = DEFAULT_WIDTH*DEFAULT_HEIGHT;

/********************************************** 
 * 
 *  struct declarations
 * 
 **********************************************/

static struct { int width; int height; int frameSize; }
s_vidFrameSize[] = 
{
	{  640,  120,  640* 120},
	{  640,  160,  640* 160},
	{  640,  240,  640* 240}, 
	{  640,  480,  640* 480}, 
	{ 1280,  240, 1280* 240}, 
	{ 1392,  256, 1392* 256}, 
	{ 1280,  320, 1280* 320},
	{ 1392,  344, 1392* 344}, 
	{ 1616,  308, 1616* 308}, 
	{ 1280,  480, 1280* 480},
	{ 1616,  410, 1616* 410}, 
	{ 1392,  520, 1392* 520}, 
	{ 1616,  616, 1616* 616}, 
	{ 1280,  960, 1280* 960}, 
	{ 1392, 1040, 1392*1040}, 
	{ 1616, 1232, 1616*1232}, 
};

const char g_Res[MAX_RESOLUTIONS+1][10] = 
{
	{ "640*120"},
	{ "640*160"},
	{ "640*240"}, 
	{ "640*480"}, 
	{ "1280*240"}, 
	{ "1280*320"},
	{ "1280*480"},
	{ "1280*960"}, 
	{ "1392*256"}, 
	{ "1392*344"}, 
	{ "1392*520"}, 
	{ "1392*1040"}, 
	{ "1616*308"}, 
	{ "1616*410"}, 
	{ "1616*616"}, 
	{ "1616*1232"}, 
};

//////////////////////////////////////////////////////////////////////////
//Camera API function pointer variables
BUFCCDUSB_InitDevicePtr BUFCCDUSB_InitDevice;
BUFCCDUSB_UnInitDevicePtr BUFCCDUSB_UnInitDevice;
BUFCCDUSB_GetModuleNoSerialNoPtr BUFCCDUSB_GetModuleNoSerialNo;
BUFCCDUSB_AddDeviceToWorkingSetPtr BUFCCDUSB_AddDeviceToWorkingSet;
BUFCCDUSB_RemoveDeviceFromWorkingSetPtr BUFCCDUSB_RemoveDeviceFromWorkingSet;
BUFCCDUSB_ActiveDeviceInWorkingSetPtr BUFCCDUSB_ActiveDeviceInWorkingSet;
BUFCCDUSB_StartCameraEnginePtr BUFCCDUSB_StartCameraEngine;
BUFCCDUSB_StopCameraEnginePtr BUFCCDUSB_StopCameraEngine;
BUFCCDUSB_SetCameraWorkModePtr BUFCCDUSB_SetCameraWorkMode;
BUFCCDUSB_StartFrameGrabPtr BUFCCDUSB_StartFrameGrab;
BUFCCDUSB_StopFrameGrabPtr BUFCCDUSB_StopFrameGrab;
BUFCCDUSB_SetCustomizedResolutionPtr BUFCCDUSB_SetCustomizedResolution;
BUFCCDUSB_SetExposureTimePtr BUFCCDUSB_SetExposureTime;
BUFCCDUSB_SetFrameTimePtr BUFCCDUSB_SetFrameTime;
BUFCCDUSB_SetXYStartPtr BUFCCDUSB_SetXYStart;
BUFCCDUSB_SetGainsPtr BUFCCDUSB_SetGains;
BUFCCDUSB_SetGammaPtr BUFCCDUSB_SetGamma;
BUFCCDUSB_SetBWModePtr BUFCCDUSB_SetBWMode;
BUFCCDUSB_InstallFrameHookerPtr BUFCCDUSB_InstallFrameHooker;
BUFCCDUSB_InstallUSBDeviceHookerPtr BUFCCDUSB_InstallUSBDeviceHooker;
//////////////////////////////////////////////////////////////////////////

MMThreadLock g_imgPixelsLock_;
unsigned char *g_pImage;
int g_frameSize = DEFAULT_SIZE;
int g_frameSize_width = DEFAULT_WIDTH;
int g_deviceColorType = 0;
int g_InstanceCount = 0;
long g_xStart = 0;

/////////////////////////////////////////////////////////////////////////////
//CallBack Function

void CameraFaultCallBack( int ImageType )
{
	// Note: It's recommended to stop the engine and close the application
	BUFCCDUSB_StopCameraEngine();
	BUFCCDUSB_UnInitDevice();
}

void FrameCallBack(TProcessedDataProperty *Attributes, unsigned char *BytePtr)
{

	MMThreadGuard g_g(g_imgPixelsLock_);
	//memcpy(g_pImage, BytePtr, g_frameSize);

	{
		bool isInROI;
		if(Attributes->Bin == 0)
		{
			if(g_frameSize_width == Attributes->Column)
			{
				memcpy(g_pImage, BytePtr, g_frameSize);
				return;
			}

			BYTE *p = g_pImage;
			BYTE *q = BytePtr;

			for(int i = 0; i < Attributes->Row; i++)
			{
				for(int k = 0; k < Attributes->Column; k++)
				{
					isInROI = false;
					if(k < g_frameSize_width+g_xStart && k >= g_xStart)
						isInROI = true;
					if ( isInROI == true )
					{
						if (g_deviceColorType) // Color
							for(int j = 0; j < 3; j++)
							{
								*p = *q;
								p++;
								q++;
							}
						else
						{// Mono
								*p = *q;
								p++;
								q++;
						}
					}
					else
					{
						if (g_deviceColorType)
							// Color
						{
							q++;
							q++;
							q++;
						}
						else // Mono
							q++;
					}
				}
			}
			p = NULL;
			q = NULL;
		}
	}
}


int CMightex_BUF_USBCCDCamera::InitCamera()
{
	if(g_InstanceCount > 1)
		return DEVICE_ERR;

  HDll = LoadLibrary("BUF_USBCCDCamera_SDK_MM.dll");
  if (HDll)
  {
	BUFCCDUSB_InitDevice = (BUFCCDUSB_InitDevicePtr)GetProcAddress(HDll,"BUFCCDUSB_InitDevice");
	BUFCCDUSB_UnInitDevice = (BUFCCDUSB_UnInitDevicePtr)GetProcAddress(HDll,"BUFCCDUSB_UnInitDevice");
	BUFCCDUSB_GetModuleNoSerialNo = (BUFCCDUSB_GetModuleNoSerialNoPtr)GetProcAddress(HDll,"BUFCCDUSB_GetModuleNoSerialNo");
	BUFCCDUSB_AddDeviceToWorkingSet = (BUFCCDUSB_AddDeviceToWorkingSetPtr) GetProcAddress(HDll,"BUFCCDUSB_AddDeviceToWorkingSet");
	BUFCCDUSB_RemoveDeviceFromWorkingSet = (BUFCCDUSB_RemoveDeviceFromWorkingSetPtr)GetProcAddress(HDll,"BUFCCDUSB_RemoveDeviceFromWorkingSet");
	BUFCCDUSB_ActiveDeviceInWorkingSet = (BUFCCDUSB_ActiveDeviceInWorkingSetPtr)GetProcAddress(HDll,"BUFCCDUSB_ActiveDeviceInWorkingSet");
	BUFCCDUSB_StartCameraEngine = (BUFCCDUSB_StartCameraEnginePtr)GetProcAddress(HDll,"BUFCCDUSB_StartCameraEngine");
	BUFCCDUSB_StopCameraEngine = (BUFCCDUSB_StopCameraEnginePtr)GetProcAddress(HDll,"BUFCCDUSB_StopCameraEngine");
	BUFCCDUSB_SetCameraWorkMode = (BUFCCDUSB_SetCameraWorkModePtr)GetProcAddress(HDll,"BUFCCDUSB_SetCameraWorkMode");
	BUFCCDUSB_StartFrameGrab = (BUFCCDUSB_StartFrameGrabPtr)GetProcAddress(HDll,"BUFCCDUSB_StartFrameGrab");
	BUFCCDUSB_StopFrameGrab = (BUFCCDUSB_StopFrameGrabPtr)GetProcAddress(HDll,"BUFCCDUSB_StopFrameGrab");
	BUFCCDUSB_SetCustomizedResolution = (BUFCCDUSB_SetCustomizedResolutionPtr)GetProcAddress(HDll,"BUFCCDUSB_SetCustomizedResolution");
	BUFCCDUSB_SetExposureTime = (BUFCCDUSB_SetExposureTimePtr)GetProcAddress(HDll,"BUFCCDUSB_SetExposureTime");
	BUFCCDUSB_SetFrameTime = (BUFCCDUSB_SetFrameTimePtr)GetProcAddress(HDll,"BUFCCDUSB_SetFrameTime");
	BUFCCDUSB_SetXYStart = (BUFCCDUSB_SetXYStartPtr)GetProcAddress(HDll,"BUFCCDUSB_SetXYStart");
	BUFCCDUSB_SetGains = (BUFCCDUSB_SetGainsPtr)GetProcAddress(HDll,"BUFCCDUSB_SetGains");
	BUFCCDUSB_SetGamma = (BUFCCDUSB_SetGammaPtr)GetProcAddress(HDll,"BUFCCDUSB_SetGamma");
	BUFCCDUSB_SetBWMode = (BUFCCDUSB_SetBWModePtr)GetProcAddress(HDll,"BUFCCDUSB_SetBWMode");
	BUFCCDUSB_InstallFrameHooker = (BUFCCDUSB_InstallFrameHookerPtr)GetProcAddress(HDll,"BUFCCDUSB_InstallFrameHooker");
	BUFCCDUSB_InstallUSBDeviceHooker = (BUFCCDUSB_InstallUSBDeviceHookerPtr)GetProcAddress(HDll,"BUFCCDUSB_InstallUSBDeviceHooker");
  }
  else
		return DEVICE_ERR;

	int g_cameraCount = BUFCCDUSB_InitDevice();
	if (g_cameraCount == 0)
	{
		BUFCCDUSB_UnInitDevice();
		return DEVICE_NOT_CONNECTED;
	}

	char ModuleNo[32];
	char SerialNo[32];
	if(BUFCCDUSB_GetModuleNoSerialNo(1, ModuleNo, SerialNo) == -1)
	{
		;
	}
	else
	{
		//remove string spaces
		char *s_ModuleNo = strchr(ModuleNo, ' ');
		if(s_ModuleNo)
			*s_ModuleNo = '\0';
		sprintf(camNames, "%s:%s\0", ModuleNo, SerialNo);
	}

		BUFCCDUSB_AddDeviceToWorkingSet(1);
		BUFCCDUSB_ActiveDeviceInWorkingSet(1, 0);

	BUFCCDUSB_StartCameraEngine( NULL, 8);
	BUFCCDUSB_InstallFrameHooker( 1, FrameCallBack );
	BUFCCDUSB_InstallUSBDeviceHooker( CameraFaultCallBack );

	//////////////////////////////////////////////////////////////////////////
	// GetDeviceType
	
	if(strstr(camNames, "BG04") != NULL) 
		deviceType = ICX424M;

	else if(strstr(camNames, "CG04") != NULL) 
		deviceType = ICX424C;

	else if(strstr(camNames, "B013") != NULL)
	{
		if(strstr(camNames, "CC") != NULL)
			deviceType = ICX205AL;
		else if(strstr(camNames, "CX") != NULL)//CX
			deviceType = ICX285AL;
		else //CG
			deviceType = ICX445AL;
	}

	else if(strstr(camNames, "C013") != NULL) 
	{
		if(strstr(camNames, "CC") != NULL)
			deviceType = ICX205AK;
		else if(strstr(camNames, "CX") != NULL)//CX
			deviceType = ICX285AQ;
		else //CG
			deviceType = ICX445AQ;
	}
	
	else if(strstr(camNames, "B020") != NULL) 
		deviceType = ICX274AL;

	else if(strstr(camNames, "C020") != NULL) 
		deviceType = ICX274AQ;
	
	else
		return DEVICE_ERR;

     switch (deviceType) {
        case ICX424C:
        case ICX205AK:
        case ICX285AQ:
        case ICX445AQ:
        case ICX274AQ:
			deviceColorType = 1;
			break;
       default: 
			deviceColorType = 0;
	}

	//////////////////////////////////////////////////////////////////////////

	
	if ((deviceType == ICX424C)||(deviceType == ICX424M))
		MAX_RESOLUTION = MAX_RESOLUTIONS_V032;
	else if (deviceType == ICX205AL||deviceType == ICX205AK||
		deviceType == ICX285AL||deviceType == ICX285AQ)
		MAX_RESOLUTION = MAX_RESOLUTIONS_T001;
	else if ((deviceType == ICX274AQ)||(deviceType == ICX274AL))
		MAX_RESOLUTION = MAX_RESOLUTIONS_P001;
	else
		MAX_RESOLUTION = MAX_RESOLUTIONS_M001;

	s_MAX_RESOLUTION = MAX_RESOLUTION;
	if(MAX_RESOLUTION == MAX_RESOLUTIONS_T001)
		s_MAX_RESOLUTION = 11;
	if(MAX_RESOLUTION == MAX_RESOLUTIONS_M001)
		s_MAX_RESOLUTION = 7;
	
	int frameSize;
	if(deviceColorType)
	{
		g_frameSize = DEFAULT_SIZE*3;
		frameSize = s_vidFrameSize[MAX_RESOLUTION].frameSize*3;
	}
	else
		frameSize = s_vidFrameSize[MAX_RESOLUTION].frameSize;
	g_pImage = new BYTE[frameSize];
	ZeroMemory(g_pImage, frameSize);

	g_deviceColorType = deviceColorType;

	g_xStart = 0;
	yStart = 0;
	h_Mirror = 0;
	v_Flip = 0;

	BUFCCDUSB_SetCustomizedResolution(1, 
			s_vidFrameSize[MAX_RESOLUTION].width, DEFAULT_HEIGHT, 0, GetCameraBufferCount(DEFAULT_WIDTH, DEFAULT_HEIGHT));

	is_initCamera = true;
    LogMessage("After InitCamera CMightex_BUF_USBCCDCamera\n");

    return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::GetCameraBufferCount(int width, int height)
{
	
	int frameSize = width * height;
	int tmpIndex = MAX_RESOLUTIONS;
	while (frameSize <= s_vidFrameSize[tmpIndex].frameSize )
	{
		tmpIndex--;
		if(tmpIndex < 0) break;
	}
	
	if (deviceColorType == 0)
		return CAMERA_BUFFER_BW[tmpIndex+1];	
	else
		return CAMERA_BUFFER_COLOR[tmpIndex+1];	
	
	return NORMAL_FRAMES;
}


///////////////////////////////////////////////////////////////////////////////
// CMightex_BUF_USBCCDCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CMightex_BUF_USBCCDCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CMightex_BUF_USBCCDCamera::CMightex_BUF_USBCCDCamera() :
   CCameraBase<CMightex_BUF_USBCCDCamera> (),
   dPhase_(0),
   initialized_(false),
   readoutUs_(0.0),
   scanMode_(1),
   bitDepth_(8),
   roiX_(0),
   roiY_(0),
   sequenceStartTime_(0),
   isSequenceable_(false),
   sequenceMaxLength_(100),
   sequenceRunning_(false),
   sequenceIndex_(0),
	binSize_(1),
	cameraCCDXSize_(DEFAULT_WIDTH),
	cameraCCDYSize_(DEFAULT_HEIGHT),
   ccdT_ (0.0),
   triggerDevice_(""),
   stopOnOverflow_(false),
	dropPixels_(false),
   fastImage_(false),
   saturatePixels_(false),
	fractionOfPixelsToDropOrSaturate_(0.002),
   pDemoResourceLock_(0),
   nComponents_(4)
{
   memset(testProperty_,0,sizeof(testProperty_));

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
   pDemoResourceLock_ = new MMThreadLock();
   thd_ = new MySequenceThread(this);

   // parent ID display
   //CreateHubIDProperty();

	g_InstanceCount++;
	is_initCamera = false;
	HDll = NULL;
}

/**
* CMightex_BUF_USBCCDCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CMightex_BUF_USBCCDCamera::~CMightex_BUF_USBCCDCamera()
{

   StopSequenceAcquisition();
   delete thd_;
   delete pDemoResourceLock_;

   g_InstanceCount--;
   LogMessage("After ~CMightex_BUF_USBCCDCamera\n");
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CMightex_BUF_USBCCDCamera::GetName(char* name) const
{
   // Return the name used to referr to this device adapte
   CDeviceUtils::CopyLimitedString(name, g_CameraBUFCCDDeviceName);
   //CDeviceUtils::CopyLimitedString(name, camNames);
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
int CMightex_BUF_USBCCDCamera::Initialize()
{
   LogMessage("Before Initialize CMightex_BUF_USBCCDCamera\n");

   if (initialized_)
      return DEVICE_OK;

   int nRet = InitCamera();
   if( nRet != DEVICE_OK)
      return nRet;

   // set property list
   // -----------------

   // Name
   nRet = CreateStringProperty(MM::g_Keyword_Name, g_CameraBUFCCDDeviceName, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateStringProperty(MM::g_Keyword_Description, "Mightex USB Camera Device Adapter", true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateStringProperty(MM::g_Keyword_CameraName, "Mightex_USBCamera-MultiMode", true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateStringProperty(MM::g_Keyword_CameraID, "V1.0", true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnBinning);
   nRet = CreateIntegerProperty(MM::g_Keyword_Binning, 1, false, pAct);
   assert(nRet == DEVICE_OK);

   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnPixelType);
   	if(deviceColorType)
	   nRet = CreateStringProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, false, pAct);
	else
	   nRet = CreateStringProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_8bit);
   	if(deviceColorType)
		pixelTypeValues.push_back(g_PixelType_32bitRGB);
	else
		nComponents_ = 1;

   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Bit depth
   nRet = CreateStringProperty("BitDepth", "8", true);
   assert(nRet == DEVICE_OK);

   //pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnBitDepth);
   //nRet = CreateIntegerProperty("BitDepth", 8, false, pAct);
   //assert(nRet == DEVICE_OK);

   //vector<string> bitDepths;
   //bitDepths.push_back("8");
   //bitDepths.push_back("10");
   //bitDepths.push_back("12");
   //bitDepths.push_back("14");
   //bitDepths.push_back("16");
   //bitDepths.push_back("32");
   //nRet = SetAllowedValues("BitDepth", bitDepths);
   //if (nRet != DEVICE_OK)
   //   return nRet;

   // exposure
   nRet = CreateIntegerProperty(MM::g_Keyword_Exposure, 20, true);
   assert(nRet == DEVICE_OK);

   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnExposure);
   //nRet = CreateIntegerProperty(MM::g_Keyword_Exposure, 20, false, pAct);
   nRet = CreateIntegerProperty("Exposure Time", 20, false, pAct);
   assert(nRet == DEVICE_OK);
   //SetPropertyLimits(MM::g_Keyword_Exposure, 0, 100);
   SetPropertyLimits("Exposure Time",1, 100);
   
   // camera gain
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnGain);
   //nRet = CreateIntegerProperty(MM::g_Keyword_Gain, 14, false, pAct);
   nRet = CreateIntegerProperty("Gain Value", 14, false, pAct);
   assert(nRet == DEVICE_OK);
   //SetPropertyLimits(MM::g_Keyword_Gain, 6, 42);
   SetPropertyLimits("Gain Value", 6, 42);

   // Resolution
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnResolution);
   nRet = CreateStringProperty(g_Keyword_Resolution, g_Res[0], false, pAct);
   assert(nRet == DEVICE_OK);

   // XStart
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnXStart);
   nRet = CreateIntegerProperty(g_Keyword_XStart, 0, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_XStart, 0, s_vidFrameSize[MAX_RESOLUTION].width - DEFAULT_WIDTH);
   // YStart
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnYStart);
   nRet = CreateIntegerProperty(g_Keyword_YStart, 0, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_YStart, 0, s_vidFrameSize[MAX_RESOLUTION].height - DEFAULT_HEIGHT);

   // H_Mirror
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnH_Mirror);
   nRet = CreateIntegerProperty("H_Mirror", 0, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> h_Mirrors;
   h_Mirrors.push_back("0");
   h_Mirrors.push_back("1");
   nRet = SetAllowedValues("H_Mirror", h_Mirrors);
   if (nRet != DEVICE_OK)
      return nRet;

   // V_Flip
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnV_Flip);
   nRet = CreateIntegerProperty("V_Flip", 0, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> v_Flips;
   v_Flips.push_back("0");
   v_Flips.push_back("1");
   nRet = SetAllowedValues("V_Flip", v_Flips);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> ResValues;
   for(int i = 0; i <= s_MAX_RESOLUTION; i++)
	   ResValues.push_back(g_Res[i]);
   nRet = SetAllowedValues(g_Keyword_Resolution, ResValues);
   if (nRet != DEVICE_OK)
      return nRet;

/*
	CPropertyActionEx *pActX = 0;
	// create an extended (i.e. array) properties 1 through 4
	
	for(int ij = 1; ij < 7;++ij)
	{
      std::ostringstream os;
      os<<ij;
      std::string propName = "TestProperty" + os.str();
		pActX = new CPropertyActionEx(this, &CMightex_BUF_USBCCDCamera::OnTestProperty, ij);
      nRet = CreateFloatProperty(propName.c_str(), 0., false, pActX);
      if(0!=(ij%5))
      {
         // try several different limit ranges
         double upperLimit = (double)ij*pow(10.,(double)(((ij%2)?-1:1)*ij));
         double lowerLimit = (ij%3)?-upperLimit:0.;
         SetPropertyLimits(propName.c_str(), lowerLimit, upperLimit);
      }
	}

   //pAct = new CPropertyAction(this, &CMightex_BUF_USBCCDCamera::OnSwitch);
   //nRet = CreateIntegerProperty("Switch", 0, false, pAct);
   //SetPropertyLimits("Switch", 8, 1004);
	
	
	// scan mode
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnScanMode);
   nRet = CreateIntegerProperty("ScanMode", 1, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue("ScanMode","1");
   AddAllowedValue("ScanMode","2");
   AddAllowedValue("ScanMode","3");

   // camera offset
   nRet = CreateIntegerProperty(MM::g_Keyword_Offset, 0, false);
   assert(nRet == DEVICE_OK);

   // camera temperature
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnCCDTemp);
   nRet = CreateFloatProperty(MM::g_Keyword_CCDTemperature, 0, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_CCDTemperature, -100, 10);

   // camera temperature RO
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnCCDTemp);
   nRet = CreateFloatProperty("CCDTemperature RO", 0, true, pAct);
   assert(nRet == DEVICE_OK);

   // readout time
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnReadoutTime);
   nRet = CreateFloatProperty(MM::g_Keyword_ReadoutTime, 0, false, pAct);
   assert(nRet == DEVICE_OK);

   // CCD size of the camera we are modeling
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnCameraCCDXSize);
   CreateIntegerProperty("OnCameraCCDXSize", 512, false, pAct);
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnCameraCCDYSize);
   CreateIntegerProperty("OnCameraCCDYSize", 512, false, pAct);

   // Trigger device
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnTriggerDevice);
   CreateStringProperty("TriggerDevice", "", false, pAct);

   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnDropPixels);
   CreateIntegerProperty("DropPixels", 0, false, pAct);
   AddAllowedValue("DropPixels", "0");
   AddAllowedValue("DropPixels", "1");

	pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnSaturatePixels);
   CreateIntegerProperty("SaturatePixels", 0, false, pAct);
   AddAllowedValue("SaturatePixels", "0");
   AddAllowedValue("SaturatePixels", "1");

   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnFastImage);
   CreateIntegerProperty("FastImage", 0, false, pAct);
   AddAllowedValue("FastImage", "0");
   AddAllowedValue("FastImage", "1");

   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnFractionOfPixelsToDropOrSaturate);
   CreateFloatProperty("FractionOfPixelsToDropOrSaturate", 0.002, false, pAct);
	SetPropertyLimits("FractionOfPixelsToDropOrSaturate", 0., 0.1);

   // Whether or not to use exposure time sequencing
   pAct = new CPropertyAction (this, &CMightex_BUF_USBCCDCamera::OnIsSequenceable);
   std::string propName = "UseExposureSequences";
   CreateStringProperty(propName.c_str(), "No", false, pAct);
   AddAllowedValue(propName.c_str(), "Yes");
   AddAllowedValue(propName.c_str(), "No");
*/
   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;


   // setup the buffer
   // ----------------
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
      return nRet;

#ifdef TESTRESOURCELOCKING
   TestResourceLocking(true);
   LogMessage("TestResourceLocking OK",true);
#endif


   initialized_ = true;

   // initialize image buffer
   GenerateEmptyImage(img_);

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
int CMightex_BUF_USBCCDCamera::Shutdown()
{
   initialized_ = false;

    LogMessage("Before Shutdown\n");
	//if(g_InstanceCount > 1)
	//	 return DEVICE_OK;

	if(HDll){
		BUFCCDUSB_InstallFrameHooker( 1, NULL );
		BUFCCDUSB_InstallUSBDeviceHooker( NULL );
        //LogMessage("Before BUFCCDUSB_StopCameraEngine\n");
		BUFCCDUSB_StopCameraEngine();
        //LogMessage("After BUFCCDUSB_StopCameraEngine\n");
		BUFCCDUSB_RemoveDeviceFromWorkingSet(1);
		//LogMessage("After BUFCCDUSB_RemoveDeviceFromWorkingSet\n");
		BUFCCDUSB_UnInitDevice();
		LogMessage("After BUFCCDUSB_UnInitDevice\n");

		 FreeLibrary( HDll );
		LogMessage("After FreeLibrary\n");
		 //HDll = NULL;
	}

   if(!is_initCamera)
		 return DEVICE_OK;

	if(g_pImage)
	{
		LogMessage("Before delete g_pImage\n");
		delete []g_pImage;
		g_pImage = NULL;
	}

	is_initCamera = false;
    //LogMessage("After Shutdown\n");

   return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CMightex_BUF_USBCCDCamera::SnapImage()
{

	static int callCounter = 0;
	++callCounter;

   MM::MMTime startTime = GetCurrentMMTime();
   double exp = GetExposure();
   if (sequenceRunning_ && IsCapturing()) 
   {
      exp = GetSequenceExposure();
   }

   GenerateSyntheticImage(img_, exp);

   MM::MMTime s0(0,0);
   if( s0 < startTime )
   {
      while (exp > (GetCurrentMMTime() - startTime).getMsec())
      {
         CDeviceUtils::SleepMs(1);
      }		
   }
   else
   {
      std::cerr << "You are operating this device adapter without setting the core callback, timing functions aren't yet available" << std::endl;
      // called without the core callback probably in off line test program
      // need way to build the core in the test program

   }
   readoutStartTime_ = GetCurrentMMTime();

   return DEVICE_OK;
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
const unsigned char* CMightex_BUF_USBCCDCamera::GetImageBuffer()
{

   MMThreadGuard g(imgPixelsLock_);
   MM::MMTime readoutTime(readoutUs_);
   while (readoutTime > (GetCurrentMMTime() - readoutStartTime_)) {}		
   unsigned char *pB = (unsigned char*)(img_.GetPixels());

   MMThreadGuard g_g(g_imgPixelsLock_);
   if(deviceColorType == 0)
		memcpy(img_.GetPixelsRW(), g_pImage, g_frameSize);
   else
	   if(GetImageBytesPerPixel() == 4)
			RGB3toRGB4((char *)g_pImage, (char *) img_.GetPixelsRW(), img_.Width(), img_.Height());
	   else
			RGB3toRGB1((char *)g_pImage, (char *) img_.GetPixelsRW(), img_.Width(), img_.Height());

   return pB;
}

/**
* Converts three-byte image to four bytes
*/
void CMightex_BUF_USBCCDCamera::RGB3toRGB4(const char* srcPixels, char* destPixels, int width, int height)
{
   // nasty padding loop
   unsigned int srcOffset = 0;
   unsigned int dstOffset = 0;
   int totalSize = width * height;
   for(int i=0; i < totalSize; i++){
      memcpy(destPixels+dstOffset, srcPixels+srcOffset,3);
      srcOffset += 3;
      dstOffset += 4;
   }
}

/**
* Converts three-byte image to one bytes
*/
void CMightex_BUF_USBCCDCamera::RGB3toRGB1(const char* srcPixels, char* destPixels, int width, int height)
{
   // nasty padding loop
   unsigned int srcOffset = 0;
   unsigned int dstOffset = 0;
   int totalSize = width * height;
   for(int i=0; i < totalSize; i++){
      memcpy(destPixels+dstOffset, srcPixels+srcOffset,1);
      srcOffset += 3;
      dstOffset += 1;
   }
}


/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMightex_BUF_USBCCDCamera::GetImageWidth() const
{

   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMightex_BUF_USBCCDCamera::GetImageHeight() const
{

   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CMightex_BUF_USBCCDCamera::GetImageBytesPerPixel() const
{

   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CMightex_BUF_USBCCDCamera::GetBitDepth() const
{

   return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CMightex_BUF_USBCCDCamera::GetImageBufferSize() const
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
int CMightex_BUF_USBCCDCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{

   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
      ResizeImageBuffer();
      roiX_ = 0;
      roiY_ = 0;
   }
   else
   {
      // apply ROI
      img_.Resize(xSize, ySize);
      roiX_ = x;
      roiY_ = y;
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CMightex_BUF_USBCCDCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{

   x = roiX_;
   y = roiY_;

   xSize = img_.Width();
   ySize = img_.Height();

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CMightex_BUF_USBCCDCamera::ClearROI()
{

   ResizeImageBuffer();
   roiX_ = 0;
   roiY_ = 0;
      
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CMightex_BUF_USBCCDCamera::GetExposure() const
{

   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
      return 0.0;
   return atof(buf);
}

/**
 * Returns the current exposure from a sequence and increases the sequence counter
 * Used for exposure sequences
 */
double CMightex_BUF_USBCCDCamera::GetSequenceExposure() 
{
   if (exposureSequence_.size() == 0) 
      return this->GetExposure();

   double exposure = exposureSequence_[sequenceIndex_];

   sequenceIndex_++;
   if (sequenceIndex_ >= exposureSequence_.size())
      sequenceIndex_ = 0;

   return exposure;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CMightex_BUF_USBCCDCamera::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
   GetCoreCallback()->OnExposureChanged(this, exp);;
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CMightex_BUF_USBCCDCamera::GetBinning() const
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
int CMightex_BUF_USBCCDCamera::SetBinning(int binF)
{

   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int CMightex_BUF_USBCCDCamera::IsExposureSequenceable(bool& isSequenceable) const
{
   isSequenceable = isSequenceable_;
   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::GetExposureSequenceMaxLength(long& nrEvents)
{
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   nrEvents = sequenceMaxLength_;
   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::StartExposureSequence()
{
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   // may need thread lock
   sequenceRunning_ = true;
   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::StopExposureSequence()
{
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   // may need thread lock
   sequenceRunning_ = false;
   sequenceIndex_ = 0;
   return DEVICE_OK;
}

/**
 * Clears the list of exposures used in sequences
 */
int CMightex_BUF_USBCCDCamera::ClearExposureSequence()
{
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   exposureSequence_.clear();
   return DEVICE_OK;
}

/**
 * Adds an exposure to a list of exposures used in sequences
 */
int CMightex_BUF_USBCCDCamera::AddToExposureSequence(double exposureTime_ms) 
{
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   exposureSequence_.push_back(exposureTime_ms);
   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::SendExposureSequence() const {
   if (!isSequenceable_) {
      return DEVICE_UNSUPPORTED_COMMAND;
   }

   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::SetAllowedBinning() 
{

   vector<string> binValues;
   binValues.push_back("1");
/*   binValues.push_back("2");
   if (scanMode_ < 3)
      binValues.push_back("4");
   if (scanMode_ < 2)
      binValues.push_back("8");
   if (binSize_ == 8 && scanMode_ == 3) {
      SetProperty(MM::g_Keyword_Binning, "2");
   } else if (binSize_ == 8 && scanMode_ == 2) {
      SetProperty(MM::g_Keyword_Binning, "4");
   } else if (binSize_ == 4 && scanMode_ == 3) {
      SetProperty(MM::g_Keyword_Binning, "2");
   }
*/      
   LogMessage("Setting Allowed Binning settings", true);
   return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int CMightex_BUF_USBCCDCamera::StartSequenceAcquisition(double interval) {

	BUFCCDUSB_ActiveDeviceInWorkingSet(1, 1);
	BUFCCDUSB_StartFrameGrab( GRAB_FRAME_FOREVER );

   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int CMightex_BUF_USBCCDCamera::StopSequenceAcquisition()                                     
{
   if (IsCallbackRegistered())
   {
	   ;
   }

  if(is_initCamera){
	  LogMessage("before BUFCCDUSB_StopFrameGrab\n");
	  BUFCCDUSB_StopFrameGrab();
	  BUFCCDUSB_ActiveDeviceInWorkingSet(1, 0);
	}

   if (!thd_->IsStopped()) {
      thd_->Stop();                                                       
      thd_->wait();
   }                                                                      
                                                                          
   return DEVICE_OK;                                                      
} 

/**
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int CMightex_BUF_USBCCDCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;
   sequenceStartTime_ = GetCurrentMMTime();
   imageCounter_ = 0;
   thd_->Start(numImages,interval_ms);
   stopOnOverflow_ = stopOnOverflow;
   return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CMightex_BUF_USBCCDCamera::InsertImage()
{

   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;
   md.put("Camera", label);
   md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
   md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
   md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
   md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 

   imageCounter_++;

   char buf[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, buf);
   md.put(MM::g_Keyword_Binning, buf);

   MMThreadGuard g(imgPixelsLock_);

   const unsigned char* pI;
   pI = GetImageBuffer();

   unsigned int w = GetImageWidth();
   unsigned int h = GetImageHeight();
   unsigned int b = GetImageBytesPerPixel();

   int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
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
int CMightex_BUF_USBCCDCamera::ThreadRun (MM::MMTime startTime)
{

   int ret=DEVICE_ERR;
   
   // Trigger
   if (triggerDevice_.length() > 0) {
      MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
      if (triggerDev != 0) {
      	LogMessage("trigger requested");
      	triggerDev->SetProperty("Trigger","+");
      }
   }
   
   if (!fastImage_)
   {
      GenerateSyntheticImage(img_, GetSequenceExposure());
   }

   ret = InsertImage();
     

   while (((double) (this->GetCurrentMMTime() - startTime).getMsec() / imageCounter_) < this->GetSequenceExposure())
   {
      CDeviceUtils::SleepMs(1);
   }

   if (ret != DEVICE_OK)
   {
      return ret;
   }
   return ret;
};

bool CMightex_BUF_USBCCDCamera::IsCapturing() {
   return !thd_->IsStopped();
}

/*
 * called from the thread function before exit 
 */
void CMightex_BUF_USBCCDCamera::OnThreadExiting() throw()
{
   try
   {
      LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
      GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0):DEVICE_OK;
   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
   }
}


MySequenceThread::MySequenceThread(CMightex_BUF_USBCCDCamera* pCam)
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
         ret=camera_->ThreadRun(startTime_);
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
// CMightex_BUF_USBCCDCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/*
* this Read Only property will update whenever any property is modified
*/
/*
int CMightex_BUF_USBCCDCamera::OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
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
*/
//int CMightex_BUF_USBCCDCamera::OnSwitch(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
   // use cached values
//   return DEVICE_OK;
//}

/**
* Handles "Binning" property.
*/
int CMightex_BUF_USBCCDCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         //if(IsCapturing())
         //   return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);
			//if(binFactor > 0 && binFactor < 10)
			{
			//	img_.Resize(cameraCCDXSize_/binFactor, cameraCCDYSize_/binFactor);
			//	binSize_ = binFactor;
            //std::ostringstream os;
            //os << binSize_;
            //OnPropertyChanged("Binning", os.str().c_str());
				ret=DEVICE_OK;
			}
      }break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
			pProp->Set(binSize_);
      }break;
   default:
      break;
   }
   return ret; 
}

/**
* Handles "PixelType" property.
*/
int CMightex_BUF_USBCCDCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
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
            ret=DEVICE_OK;
         }
			else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
			{
            nComponents_ = 4;
            img_.Resize(img_.Width(), img_.Height(), 4);
            ret=DEVICE_OK;
			}
         else
         {
            // on error switch to default pixel type
            nComponents_ = 1;
            img_.Resize(img_.Width(), img_.Height(), 1);
            pProp->Set(g_PixelType_8bit);
            ret = ERR_UNKNOWN_MODE;
         }
      } break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
      } break;
   default:
      break;
   }
   return ret; 
}

/**
* Handles "BitDepth" property.
*/
/*
int CMightex_BUF_USBCCDCamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
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
				{
				   bytesPerPixel = 1;
				}
         }
			else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
			{
				bytesPerPixel = 4;
			}
			img_.Resize(img_.Width(), img_.Height(), bytesPerPixel);

      } break;
   case MM::BeforeGet:
      {
         pProp->Set((long)bitDepth_);
         ret=DEVICE_OK;
      } break;
   default:
      break;
   }
   return ret; 
}

//
// Handles "ReadoutTime" property.
//
int CMightex_BUF_USBCCDCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMightex_BUF_USBCCDCamera::OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMightex_BUF_USBCCDCamera::OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      long tvalue = 0;
      pProp->Get(tvalue);
		fastImage_ = (0==tvalue)?false:true;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(fastImage_?1L:0L);
   }

   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMightex_BUF_USBCCDCamera::OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct)
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

//
// Handles "ScanMode" property.
// Changes allowed Binning values to test whether the UI updates properly
//
int CMightex_BUF_USBCCDCamera::OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct)
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




int CMightex_BUF_USBCCDCamera::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
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

int CMightex_BUF_USBCCDCamera::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMightex_BUF_USBCCDCamera::OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int CMightex_BUF_USBCCDCamera::OnCCDTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ccdT_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ccdT_);
   }
   return DEVICE_OK;
}

int CMightex_BUF_USBCCDCamera::OnIsSequenceable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string val = "Yes";
   if (eAct == MM::BeforeGet)
   {
      if (!isSequenceable_) 
      {
         val = "No";
      }
      pProp->Set(val.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      isSequenceable_ = false;
      pProp->Get(val);
      if (val == "Yes") 
      {
         isSequenceable_ = true;
      }
   }

   return DEVICE_OK;
}
*/

///////////////////////////////////////////////////////////////////////////////
// Private CMightex_BUF_USBCCDCamera methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CMightex_BUF_USBCCDCamera::ResizeImageBuffer()
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
	else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
      byteDepth = 4;
	}

   img_.Resize(cameraCCDXSize_/binSize_, cameraCCDYSize_/binSize_, byteDepth);
   return DEVICE_OK;
}

void CMightex_BUF_USBCCDCamera::GenerateEmptyImage(ImgBuffer& img)
{
   MMThreadGuard g(imgPixelsLock_);
   if (img.Height() == 0 || img.Width() == 0 || img.Depth() == 0)
      return;
   unsigned char* pBuf = const_cast<unsigned char*>(img.GetPixels());
   memset(pBuf, 0, img.Height()*img.Width()*img.Depth());
}



/**
* Generate a spatial sine wave.
*/
void CMightex_BUF_USBCCDCamera::GenerateSyntheticImage(ImgBuffer& img, double exp)
{ 
  
   MMThreadGuard g(imgPixelsLock_);

}


void CMightex_BUF_USBCCDCamera::TestResourceLocking(const bool recurse)
{
   MMThreadGuard g(*pDemoResourceLock_);
   if(recurse)
      TestResourceLocking(false);
}

///////////////////////////////////////////////////////////////////////////////
// new: OnExposure 
///////////////////////////////////////////////////////////////////////////////
int CMightex_BUF_USBCCDCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
	     long exposure;
         pProp->Get(exposure);
		 //OnExposureCnt++;
		 //if (OnExposureCnt >= 10)
		 //	MessageBox( NULL, "try", "test", MB_OK);
		 if(BUFCCDUSB_SetExposureTime(1, exposure * 20) == -1)
		    return DEVICE_ERR;
         ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
      } break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

// handles gain property

int CMightex_BUF_USBCCDCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         long gain;
         pProp->Get(gain);
		if(BUFCCDUSB_SetGains(1, gain, gain, gain) == -1)
			return DEVICE_ERR;
		 ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
		 ret=DEVICE_OK;
      }break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

/**
* Handles "Resolution" property.
*/
int CMightex_BUF_USBCCDCamera::OnResolution(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
       if(IsCapturing())
          return DEVICE_CAMERA_BUSY_ACQUIRING;

		 std::string resolution;
       pProp->Get(resolution);

		 std::istringstream iss(resolution);
		 string width, height;
		 getline(iss,width,'*');
		 getline(iss,height);
		 
		 long w = atoi(width.c_str());
		 long h = atoi(height.c_str());

		if(BUFCCDUSB_SetCustomizedResolution(1, 
			s_vidFrameSize[MAX_RESOLUTION].width, h, 0, GetCameraBufferCount(w, h)))
		{
			cameraCCDXSize_ = w;
			cameraCCDYSize_ = h;
			if(deviceColorType)
				g_frameSize = w * h * 3;
			else
				g_frameSize = w * h;
			g_frameSize_width = w;

			SetPropertyLimits(g_Keyword_XStart, 0, s_vidFrameSize[MAX_RESOLUTION].width - w);
			SetPropertyLimits(g_Keyword_YStart, 0, s_vidFrameSize[MAX_RESOLUTION].height - h);
			SetProperty(g_Keyword_XStart, "0");
			SetProperty(g_Keyword_YStart, "0");
			g_xStart = 0;
			yStart = 0;
		}
		else
			 return DEVICE_ERR;

		 if(!(cameraCCDXSize_ > 0) || !(cameraCCDYSize_ > 0))
			 return DEVICE_ERR;
		 ret = ResizeImageBuffer();
		 if (ret != DEVICE_OK) return ret;
      } break;
   case MM::BeforeGet:
      {
		  std::ostringstream oss;
		  oss << CDeviceUtils::ConvertToString(cameraCCDXSize_) << "x";
		  oss << CDeviceUtils::ConvertToString(cameraCCDYSize_);
		  pProp->Set(oss.str().c_str());
		  
         ret=DEVICE_OK;
      } break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

/**
* Handles "XStart" property.
*/
int CMightex_BUF_USBCCDCamera::OnXStart(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         pProp->Get(g_xStart);
		 ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
		 ret=DEVICE_OK;
      }break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

/**
* Handles "YStart" property.
*/
int CMightex_BUF_USBCCDCamera::OnYStart(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         long y;
         pProp->Get(y);
		 int m = y % 8;
		 if(m < 4)
			 y -= m;
		 else
			 y += 8-m;
		if(BUFCCDUSB_SetXYStart(1, 0, y) == -1)
			return DEVICE_ERR;
		 yStart = y;
		 ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
		 ret=DEVICE_OK;
      }break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

/**
* Handles "H_Mirror" property.
*/
int CMightex_BUF_USBCCDCamera::OnH_Mirror(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         long h;
         pProp->Get(h);
		if(BUFCCDUSB_SetBWMode(0, h, v_Flip) == -1)
			return DEVICE_ERR;
		 h_Mirror = h;

		std::ostringstream oss;
		  oss << CDeviceUtils::ConvertToString(s_vidFrameSize[MAX_RESOLUTION].width - cameraCCDXSize_ - g_xStart);
		SetProperty(g_Keyword_XStart, oss.str().c_str());
		 ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
		 ret=DEVICE_OK;
      }break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}

/**
* Handles "V_Flip" property.
*/
int CMightex_BUF_USBCCDCamera::OnV_Flip(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         long v;
         pProp->Get(v);
		if(BUFCCDUSB_SetBWMode(0, h_Mirror, v) == -1)
			return DEVICE_ERR;
		 v_Flip = v;
		 ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
		 ret=DEVICE_OK;
      }break;
   case MM::NoAction:
      break;
   case MM::IsSequenceable:
   case MM::AfterLoadSequence:
   case MM::StartSequence:
   case MM::StopSequence:
      return DEVICE_PROPERTY_NOT_SEQUENCEABLE;
      break;
   }
   return ret; 
}
