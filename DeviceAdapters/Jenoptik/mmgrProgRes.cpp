///////////////////////////////////////////////////////////////////////////////
// FILE:          mmgrProgRes.cpp
// PROJECT:       Micro-Manager ProgRes Camera Driver
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of Jenoptik ProgRes camera driver.
//                
// AUTHOR:        Jiri Kominek, jiri.kominek@dvi.elcom.cz, 2009-06-01
//
// COPYRIGHT:     Jenoptik
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
// CVS:           $Id: ProgRes.cpp 2091 2009-02-08 05:13:51Z nico $
//

#include "mmgrProgRes.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <atltrace.h>
#include "sdk/MexExl.h"
#include "resource.h"
using namespace std;
const double CProgRes::nominalPixelSizeUm_ = 1.0;

int __stdcall ImgFinalProc(unsigned long status, mexImg *pImg, unsigned long UserValue);
void __stdcall FocusCallback(int focus,unsigned long UserValue);
BOOL CALLBACK TriggerWaitProc(HWND hwndDlg, 
                         UINT message, 
                         WPARAM wParam, 
                         LPARAM lParam);

// External names used used by the rest of the system
// to load particular device from the "ProgRes.dll" library
const char* g_CameraDeviceName = "Jenoptik-ProgRes";
const char* g_ChannelName = "Single channel";
const char* g_Unknown = "Unknown";

// constants for naming color modes
const char* g_ColorMode_Grayscale = "Grayscale";
const char* g_ColorMode_RGB = "RGB-32bit";

// constants for ciCode names
const char* g_ciCode_Standard = "Standard";
const char* g_ciCode_HighQuality = "High Quality";
const char* g_ciCode_VeryFast = "Very Fast";

// constants for trigger in
const char* g_triggerIn_No = "No";
const char* g_triggerIn_SignalToLow = "Low Signal";
const char* g_triggerIn_SignalToHigh = "High Signal";
const char* g_triggerIn_ToggleSignal = "Toggle Signal";
const char* g_triggerIn_Rising = "Rising";
const char* g_triggerIn_Falling = "Falling";

// constants for trigger out
const char* g_triggerOut_No = "No";
const char* g_triggerOut_SignalToLow = "Signal to Low";
const char* g_triggerOut_SignalToHigh = "Signal to High";
const char* g_triggerOut_ToggleSignal = "Toggle Signal";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bit = "RGB-32bit";

// Camera Properties keywords
const static char* g_Keyword_CameraCoolingEnable = "CameraCoolingEnable";
const static char* g_Keyword_CameraTriggerInEnable = "CameraTriggerInEnable";
const static char* g_Keyword_CameraTriggerOutStartExposure = "CameraTriggerOutOnStartExposure";
const static char* g_Keyword_CameraTriggerOutEndExposure = "CameraTriggerOutOnEndExposure";
const static char* g_Keyword_CameraTriggerOutEndTransfer = "CameraTriggerOutOnEndTransfer";
const static char* g_Keyword_CameraTriggerOutLevel = "CameraTriggerOutLevel";

// Snap Image Properties keywords
const static char* g_Keyword_UseBlackRef = "BlackReferenceEnable";
const static char* g_Keyword_UseWhiteRef = "WhiteReferenceEnable";
const static char* g_Keyword_UseWhiteBalance = "WhiteBalanceEnable";
const static char* g_Keyword_ciCode = "ColourInterpolationCode";
const static char* g_Keyword_correctColors = "ColourCorrectionMatrix";
const static char* g_Keyword_AcqMode = "AcquisitionMode";
const static char* g_Keyword_WhiteBalanceRed = "WBRed";
const static char* g_Keyword_WhiteBalanceGreen = "WBGreen";
const static char* g_Keyword_WhiteBalanceBlue = "WBBlue";
const static char* g_Keyword_GammaEnable = "GammaEnable";
const static char* g_Keyword_GammaValue = "GammaValue";
const static char* g_Keyword_EqualizerEnable = "EqualizerEnable";
const static char* g_Keyword_EqualizerLimit = "EqualizerLimit";
const static char* g_Keyword_SaturationControlMode = "SaturationControlMode";
const static char* g_Keyword_SaturationControlValue = "SaturationControlValue";
const static char* g_Keyword_AutoExposureControl = "AutoExposureControl";
const static char* g_Keyword_AutoExposureControlTargetBrightness = "AutoExposureControlTargetBrightness";
const static char* g_Keyword_FocusState = "FocusState";
const static char* g_Keyword_FocusValue = "FocusValue";
const static char* g_Keyword_FocusRed = "FocusRed";
const static char* g_Keyword_FocusGreen = "FocusGreen";
const static char* g_Keyword_FocusBlue = "FocusBlue";
unsigned __int64 g_TriggeredGUID = 0;
mexAcquisParams* g_pAcqProperties = NULL;
bool* g_pSnapFinished;

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraDeviceName, "Jenoptik_ProgRes_Camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	ATLTRACE ("%s\n",__FUNCTION__);
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new CProgRes();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	ATLTRACE ("DeleteDevice\n");
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CProgRes implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CProgRes constructor.
* Setup default all variables and create device properties required to exist
* before intialization.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CProgRes::CProgRes() :
CCameraBase<CProgRes> (),
initialized_(false),
readoutUs_(0.0),
scanMode_(1),
color_(false),
m_SnapFinished (false),
m_pRed (NULL),
m_pGreen (NULL),
m_pBlue (NULL),
m_TriggerIn(0)
{
	ATLTRACE ("%s\n",__FUNCTION__);

	ZeroMemory (&exp_control_, sizeof (exp_control_));
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
	
	SetErrorText(ERR_OPEN_FAILED, "Camera cannot be opened");
	SetErrorText(ERR_NO_CAMERA, "No camera found");
	SetErrorText(ERR_GET_SERIAL, "Error reading camera's serial number");
	SetErrorText(ERR_GET_TYPE_SUMMARY, "Error reading camera type summary");
	SetErrorText(ERR_ACQ_PARAMS_ERR, "Incorrect acquisition parameters");
	SetErrorText(ERR_ACQ_EXPOSURE, "Incorrect exposure time");
	SetErrorText(ERR_ACQ_GAMMA, "Incorrect gamma parameters");
	SetErrorText(ERR_ACQ_GAIN, "Incorrect gain value");
	SetErrorText(ERR_ACQ_WHITE_BALANCE, "Incorrect white balance value");
	SetErrorText(ERR_ACQ_FOCUS, "Incorrect focus parameters");
	SetErrorText(ERR_ACQ_SATURATION, "Incorrect saturation parameters");
	SetErrorText(ERR_ACQ_EQUALIZER, "Incorrect equalizer parameters");
	SetErrorText(ERR_ACQ_COOLING, "Incorrect cooling parameters");
	SetErrorText(ERR_ACQ_TRIGGER_IN, "Incorrect trigger in parameters");
	SetErrorText(ERR_ACQ_TRIGGER_OUT, "Incorrect trigger out parameters");
	SetErrorText(ERR_ACQ_TRIGGER_LEVEL, "Incorrect trigger level");
	SetErrorText(ERR_ACQ_TRIGGER_ABORTED, "Wait for trigger aborted by user");
	readoutStartTime_ = GetCurrentMMTime();
	InitializeCriticalSection (&m_CSec);
   LogMessage("CProgRes ctor completed", true); 
}

/**
* CProgRes destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CProgRes::~CProgRes()
{
	ATLTRACE ("%s\n",__FUNCTION__);
	DeleteCriticalSection (&m_CSec);
   LogMessage("CProgRes dtor completed", true); 
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CProgRes::GetName(char* name) const
{
	// We just return the name we use for referring to this
	// device adapter.
	ATLTRACE ("%s\n",__FUNCTION__);
	CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
* Tells us if device is still processing asynchronous command.
* Required by the MM:Device API.
*/
bool CProgRes::Busy()
{
	ATLTRACE ("%s\n",__FUNCTION__);
   //Camera should be in busy state during exposure
   //IsCapturing() is used for determining if sequence thread is run
   return busy_;
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
int CProgRes::Initialize()
{
	ATLTRACE ("%s\n",__FUNCTION__);
	long status = 0;

	if (initialized_)
		return DEVICE_OK;

	// Load SDK
   LogMessage("CProgRes attempt LoadMexDLL", true); 
	status = LoadMexDLL(0, NULL, "mmgr_dal_ProgRes.dll", FALSE);
	if (status == 0)
		return DEVICE_ERR;

	// Init both FireWire and USB cameras
   LogMessage("CProgRes attempt mexInit2", true); 
	status = mexInit2(NULL,0UL,(unsigned long)(mex_firewire_cameras | mex_usb_cameras | mex_usb_heartbeat_off), NULL);
	if (status != NOERR)
	{
		mexExit();
		return DEVICE_ERR;
	}

	unsigned int camera_count = 1;
	status = mexFindCameras (&camera_count, &m_GUID);
	if (status != NOERR || camera_count == 0)
	{
		mexExit();
		return ERR_NO_CAMERA;
	}

	char camera_name[MAX_CAMERA_NAME_LENGTH];
	status = mexOpenCamera(m_GUID);
	if (status == NOERR)
	{
		char serial[16] = "";
		status = mexGetCameraTypeSummary(m_GUID, &m_CameraTypeSummary);
		if (status != NOERR)
		{
			mexExit();
			return ERR_GET_TYPE_SUMMARY;
		}
		status = mexGetSerialNumberString (m_GUID, serial);
		if (status != NOERR)
		{
			mexExit();
			return ERR_GET_SERIAL;
		}

		snprintf (camera_name, MAX_CAMERA_NAME_LENGTH, "%s, SN:%s", m_CameraTypeSummary.TypeName, serial);
	}
	else
	{
		mexExit();
		return ERR_OPEN_FAILED;
	}

	// set property list
   // -----------------

	// Camera Device Name
	int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
	if (DEVICE_OK != nRet)
		return nRet;

	// Description
	nRet = CreateProperty(MM::g_Keyword_Description, "Jenoptik ProgRes Camera Device Adapter", MM::String, true);
	if (DEVICE_OK != nRet)
		return nRet;

	// Camera Name
	nRet = CreateProperty(MM::g_Keyword_CameraName, "Jenoptik ProgRes", MM::String, false);
	assert(nRet == DEVICE_OK);

	// Camera ID	
	nRet = CreateProperty(MM::g_Keyword_CameraID, camera_name, MM::String, true);
	assert(nRet == DEVICE_OK);

	// acquisition modes
	unsigned int i;
	CPropertyAction *pAct = new CPropertyAction (this, &CProgRes::OnAcqMode);
	for (i = 0; i < m_CameraTypeSummary.CountOfAMs; i++)
	{
		if (i == 0)
		{
			nRet = CreateProperty(g_Keyword_AcqMode, m_CameraTypeSummary.AcqProps[i].AcqModeName, MM::String, false, pAct);
			assert(nRet == DEVICE_OK);
		}
		nRet = AddAllowedValue(g_Keyword_AcqMode, m_CameraTypeSummary.AcqProps[i].AcqModeName, m_CameraTypeSummary.AcqProps[i].AcqModeId);
		assert(nRet == DEVICE_OK);
	}
	//SetProperty (g_Keyword_AcqMode, m_CameraTypeSummary.AcqProps[i-1].AcqModeName); 

	// colour mode
	vector<string> colorValues;
	colorValues.push_back(g_ColorMode_Grayscale);
	if (m_CameraTypeSummary.ColorCode)
		colorValues.push_back(g_ColorMode_RGB);

	pAct = new CPropertyAction (this, &CProgRes::OnColorMode);
	nRet = CreateProperty(MM::g_Keyword_ColorMode, g_ColorMode_Grayscale, MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	nRet = SetAllowedValues(MM::g_Keyword_ColorMode, colorValues);
	color_ = false;

	// pixel type
	pAct = new CPropertyAction (this, &CProgRes::OnPixelType);
	nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
	assert(nRet == DEVICE_OK);
	nRet = SetPixelTypesValues();
	assert(nRet == DEVICE_OK);

	// exposure
	pAct = new CPropertyAction (this, &CProgRes::OnExposure);
	nRet = CreateProperty(MM::g_Keyword_Exposure, "100.0", MM::Float, false, pAct);
	assert(nRet == DEVICE_OK);
	SetPropertyLimits(MM::g_Keyword_Exposure, 0.0, 100000.0);

	// camera gain
	if (m_CameraTypeSummary.SupportedFeatures[mexFeatHardwareGainControl])
	{
		pAct = new CPropertyAction (this, &CProgRes::OnGain);
		nRet = CreateProperty(MM::g_Keyword_Gain, "0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(MM::g_Keyword_Gain, (double)m_CameraTypeSummary.GainRange[0], (double)m_CameraTypeSummary.GainRange[1]);
	}

	// gamma
	if (m_CameraTypeSummary.SupportedFeatures[mexFeatGammaProcessing])
	{
		pAct = new CPropertyAction (this, &CProgRes::OnGammaEnable);
		nRet = CreateProperty(g_Keyword_GammaEnable, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_GammaEnable, 0, 1);
		gamma_.bActive = 0;

		pAct = new CPropertyAction (this, &CProgRes::OnGammaValue);
		nRet = CreateProperty(g_Keyword_GammaValue, "1.0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_GammaValue, 0.2, 5.0);
		gamma_.gamma = 1.0;
	}

	// Colour interpolation
	nRet = CreateProperty(g_Keyword_ciCode, g_ciCode_Standard, MM::String, false);
	assert(nRet == DEVICE_OK);
	nRet = AddAllowedValue(g_Keyword_ciCode, g_ciCode_Standard, 0);
	assert(nRet == DEVICE_OK);
	nRet = AddAllowedValue(g_Keyword_ciCode, g_ciCode_HighQuality, 1);
	assert(nRet == DEVICE_OK);
	nRet = AddAllowedValue(g_Keyword_ciCode, g_ciCode_VeryFast, 2);
	assert(nRet == DEVICE_OK);

	// Colour Matrix Processing
	nRet = CreateProperty(g_Keyword_correctColors, "0", MM::Integer, false);
	assert(nRet == DEVICE_OK);
	SetPropertyLimits(g_Keyword_correctColors, 0, 3);

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatBrightnessControl])
	{
		// Saturation control mode
		nRet = CreateProperty(g_Keyword_AutoExposureControl, "0", MM::Integer, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_AutoExposureControl, 0, 1);

		// Target Brightness
		nRet = CreateProperty(g_Keyword_AutoExposureControlTargetBrightness, "0.0", MM::Float, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_AutoExposureControlTargetBrightness, 0.0, 1.0);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatGrayBalance])
	{
		// White balance enable
		nRet = CreateProperty(g_Keyword_UseWhiteBalance, "0", MM::Integer, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_UseWhiteBalance, 0, 1);

		// White balance red
		pAct = new CPropertyAction (this, &CProgRes::OnWBRed);
		nRet = CreateProperty(g_Keyword_WhiteBalanceRed, "1.0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_WhiteBalanceRed, -10.0, 10.0);
		// White balance green
		pAct = new CPropertyAction (this, &CProgRes::OnWBGreen);
		nRet = CreateProperty(g_Keyword_WhiteBalanceGreen, "1.0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_WhiteBalanceGreen, -10.0, 10.0);
		// White balance blue
		pAct = new CPropertyAction (this, &CProgRes::OnWBBlue);
		nRet = CreateProperty(g_Keyword_WhiteBalanceBlue, "1.0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_WhiteBalanceBlue, -10.0, 10.0);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatFocusMeasuring])
	{
	   // Focus State
		pAct = new CPropertyAction (this, &CProgRes::OnFocusState);
		nRet = CreateProperty(g_Keyword_FocusState, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_FocusState, 0, 1);

		// Focus Red State
		pAct = new CPropertyAction (this, &CProgRes::OnFocusRed);
		nRet = CreateProperty(g_Keyword_FocusRed, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_FocusRed, 0, 1);

		// Focus Green State
		pAct = new CPropertyAction (this, &CProgRes::OnFocusGreen);
		nRet = CreateProperty(g_Keyword_FocusGreen, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_FocusGreen, 0, 1);

		// Focus Blue State
		pAct = new CPropertyAction (this, &CProgRes::OnFocusBlue);
		nRet = CreateProperty(g_Keyword_FocusBlue, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_FocusBlue, 0, 1);

		// Focus Value
		pAct = new CPropertyAction (this, &CProgRes::OnFocusValue);
		nRet = CreateProperty(g_Keyword_FocusValue, "0", MM::Integer, true, pAct);
		assert(nRet == DEVICE_OK);
   }

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatEqualizer])
	{
		// Equalizer State
		pAct = new CPropertyAction (this, &CProgRes::OnEqualizerEnable);
		nRet = CreateProperty(g_Keyword_EqualizerEnable, "0", MM::Integer, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_EqualizerEnable, 0, 1);

		// Equalizer Limit
		pAct = new CPropertyAction (this, &CProgRes::OnEqualizerLimit);
		nRet = CreateProperty(g_Keyword_EqualizerLimit, "0.0", MM::Float, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_EqualizerLimit, 0.0, 1.0);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatSaturationControl])
	{
		// Saturation control state
		pAct = new CPropertyAction (this, &CProgRes::OnSaturationMode);
		nRet = CreateProperty(g_Keyword_SaturationControlMode, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_SaturationControlMode, 0, 3);

		// Saturation control value
		pAct = new CPropertyAction (this, &CProgRes::OnSaturationValue);
		nRet = CreateProperty(g_Keyword_SaturationControlValue, "0.0", MM::Float, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_SaturationControlValue, -1.0, 3.0);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatCooling])
	{
		// Cooling state
		pAct = new CPropertyAction (this, &CProgRes::OnCameraCooling);
		nRet = CreateProperty(g_Keyword_CameraCoolingEnable, "0", MM::Integer, false, pAct);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_CameraCoolingEnable, 0, 1);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatInputTrigger])
	{
		// Enable trigger in
		pAct = new CPropertyAction (this, &CProgRes::OnTriggerInEnable);
		nRet = CreateProperty(g_Keyword_CameraTriggerInEnable, g_triggerIn_No, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_No, 0);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_SignalToLow, 1);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_SignalToHigh, 2);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_ToggleSignal, 3);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_Rising, 4);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerInEnable, g_triggerIn_Falling, 5);
		assert(nRet == DEVICE_OK);
	}

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatOutputTrigger])
	{
		// Enable trigger out on exposure start
		pAct = new CPropertyAction (this, &CProgRes::OnTriggerOutOnStartExposure);
		nRet = CreateProperty(g_Keyword_CameraTriggerOutStartExposure, g_triggerOut_No, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutStartExposure, g_triggerOut_No, 0);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutStartExposure, g_triggerOut_SignalToLow, 1);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutStartExposure, g_triggerOut_SignalToHigh, 2);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutStartExposure, g_triggerOut_ToggleSignal, 3);
		assert(nRet == DEVICE_OK);

		// Enable trigger out on exposure end
		pAct = new CPropertyAction (this, &CProgRes::OnTriggerOutOnEndExposure);
		nRet = CreateProperty(g_Keyword_CameraTriggerOutEndExposure, g_triggerOut_No, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndExposure, g_triggerOut_No, 0);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndExposure, g_triggerOut_SignalToLow, 1);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndExposure, g_triggerOut_SignalToHigh, 2);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndExposure, g_triggerOut_ToggleSignal, 3);
		assert(nRet == DEVICE_OK);
		// Enable trigger out on finished transfer
		pAct = new CPropertyAction (this, &CProgRes::OnTriggerOutOnEndTransfer);
		nRet = CreateProperty(g_Keyword_CameraTriggerOutEndTransfer, g_triggerOut_No, MM::String, false, pAct);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndTransfer, g_triggerOut_No, 0);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndTransfer, g_triggerOut_SignalToLow, 1);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndTransfer, g_triggerOut_SignalToHigh, 2);
		assert(nRet == DEVICE_OK);
		nRet = AddAllowedValue(g_Keyword_CameraTriggerOutEndTransfer, g_triggerOut_ToggleSignal, 3);
		assert(nRet == DEVICE_OK);
		// Trigger out level
		nRet = CreateProperty(g_Keyword_CameraTriggerOutLevel, "0", MM::Integer, false);
		assert(nRet == DEVICE_OK);
		SetPropertyLimits(g_Keyword_CameraTriggerOutLevel, 0, 1);
	}
/*
	// Use Black Ref
   pAct = new CPropertyAction (this, &CProgRes::OnSnapUseBlackRef);
   nRet = CreateProperty(g_Keyword_UseBlackRef, "0", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(g_Keyword_UseBlackRef, 0, 1);
*/
   // readout time
   pAct = new CPropertyAction (this, &CProgRes::OnReadoutTime);
   nRet = CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   // binning
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, true);
   assert(nRet == DEVICE_OK);

/*   nRet = SetAllowedBinning();
   if (nRet != DEVICE_OK)
      return nRet;
*/

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   // initialize image buffer twice - workaround for colour images
   m_AcqParams.runas = runas_snap;
   SetupAcquisition();
   ClearROI();
   SnapImage();
   return SnapImage();
}

/**
* Returns the number of physical channels in the image.
*/
unsigned int CProgRes::GetNumberOfChannels() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
	return 1;
}

int CProgRes::GetChannelName(unsigned int channel, char* name)
{
	ATLTRACE ("%s\n",__FUNCTION__);
    int ret = DEVICE_OK;
   if(channel == 0)
   {
      CDeviceUtils::CopyLimitedString(name, g_ChannelName);
      ret = DEVICE_OK;
   }
   else
   {
      CDeviceUtils::CopyLimitedString(name, g_Unknown);
      ret = DEVICE_NONEXISTENT_CHANNEL;
   }
   return ret;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CProgRes::Shutdown()
{
	ATLTRACE ("%s\n",__FUNCTION__);
	StopSequenceAcquisition();
	if (initialized_)
		mexCloseCamera(m_GUID);
	FreeMexDLL();
    //delete[] rawBuffer_;
	//rawBuffer_=0;
	initialized_ = false;
	return DEVICE_OK;
}

int CProgRes::SetupAcquisition(int live)
{
	ATLTRACE ("%s\n",__FUNCTION__);

	readoutStartTime_ = GetCurrentMMTime();
	m_AcqParams.addTicks = 0;
	m_AcqParams.buseblackref = 0;
	m_AcqParams.busewhiteref = 0;
	m_AcqParams.Bytes = sizeof (m_AcqParams);
	m_AcqParams.completionUser = reinterpret_cast<unsigned long>(this);
	m_AcqParams.notifymask = NotifyMask(0,0,0,0);
	m_AcqParams.progressProc = NULL;
	m_AcqParams.progressUser = 0;
	m_AcqParams.complProc = &ImgFinalProc;
	m_AcqParams.stdTicks = 0;
	m_AcqParams.Version = MEXACQUISPARAMSVERSION;
	m_AcqParams.ccdtransferCode = 1;

	long data = 0;
	char property_value[1026];
	GetCurrentPropertyData(g_Keyword_ciCode, data);
	m_AcqParams.ciCode = (int) data;

	GetProperty(g_Keyword_correctColors, property_value);
	m_AcqParams.correctColors = atoi (property_value);

	GetCurrentPropertyData(g_Keyword_AcqMode, data);
	m_AcqParams.mode = (mexAcqMode) data;

	if (m_CameraTypeSummary.SupportedFeatures[mexFeatGrayBalance])
	{
		GetProperty(g_Keyword_UseWhiteBalance, property_value);
		m_AcqParams.bwhitebalance = atoi (property_value);
	}
	else
		m_AcqParams.bwhitebalance = 0;

	int status = DEVICE_OK;

	if (live && m_CameraTypeSummary.SupportedFeatures[mexFeatBrightnessControl])
	{
		GetProperty(g_Keyword_AutoExposureControl, property_value);
		exp_control_.bActive = atoi (property_value);
		exp_control_.pExpCallback = NULL;
		unsigned int x,y,cx,cy;
		GetROI (x,y,cx,cy);
		RECT roi;
		SetRect (&roi,x,y,cx,cy);
		memcpy (&exp_control_.roi,&roi, sizeof (exp_control_.roi));
		GetProperty(g_Keyword_AutoExposureControlTargetBrightness, property_value);
		exp_control_.targetbrightness = atof (property_value);
		//status = mexActivateExposureControl (m_GUID, &exp_control_, (long) this);
		if (status != DEVICE_OK)
			return ERR_ACQ_PARAMS_ERR;
	}

	// setup the buffer and clear ROI
	// ----------------
	return ClearROI();
}

/**
* Performs exposure and grabs a single image.
* Required by the MM::Camera API.
*/
int CProgRes::SnapImage()
{
	ATLTRACE ("%s\n",__FUNCTION__);

	int status = SetupAcquisition();
	if (status != DEVICE_OK)
		return ERR_ACQ_PARAMS_ERR;

	m_AcqParams.runas = runas_snap;

	m_SnapFinished = false;
	busy_ = true;

	if (m_TriggerIn != 0)
	{
		g_TriggeredGUID = m_GUID;
		g_pAcqProperties = &m_AcqParams;
		g_pSnapFinished = &m_SnapFinished;
		status = DialogBox((HINSTANCE)GetModuleHandle(), MAKEINTRESOURCE(IDD_TRIGGER_IN_WAIT), NULL, TriggerWaitProc);
		if (status)
		{
			busy_ = false;
			return ERR_ACQ_TRIGGER_ABORTED;
		}
	}
	else
	{
		status = mexGrab (m_GUID, &m_AcqParams, NULL);
		if (status != NOERR)
		{
			busy_ = false;
			return ERR_ACQ_PARAMS_ERR;
		}

		while (!m_SnapFinished)	Sleep (0);
	}
	busy_ = false;

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
const unsigned char* CProgRes::GetImageBuffer()
{
	ATLTRACE ("%s\n",__FUNCTION__);
   while (GetCurrentMMTime() - readoutStartTime_ < MM::MMTime(readoutUs_)) {CDeviceUtils::SleepMs(5);}
   return m_Image.GetPixels();
}

/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/
const unsigned int* CProgRes::GetImageBufferAsRGB32()
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return (unsigned int*) GetImageBuffer();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CProgRes::GetImageWidth() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return m_Image.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CProgRes::GetImageHeight() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return m_Image.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CProgRes::GetImageBytesPerPixel() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return m_Image.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CProgRes::GetBitDepth() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return 8 * GetImageBytesPerPixel();
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CProgRes::GetImageBufferSize() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return m_Image.Width() * m_Image.Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CProgRes::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	roi_.bottom = ySize;
	roi_.left = x;
	roi_.right = xSize;
	roi_.top = y;
	RecalculateROI();
	ResizeImageBuffer();

	return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CProgRes::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	x = roi_.left;
	y = roi_.top;

	xSize = roi_.right;
	ySize = roi_.bottom;

	return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CProgRes::ClearROI()
{
	ATLTRACE ("%s\n",__FUNCTION__);
	roi_.top = 0;
	roi_.left = 0;

	mexImageInfo image_info;
	RECT rc_sensor;
	// take size of complete picture
	SetRect (&rc_sensor, 0,0,m_CameraTypeSummary.SensorX,m_CameraTypeSummary.SensorY);
	m_AcqParams.rcsensorBounds = rc_sensor;
	mexGetAcquisitionInfo (m_GUID, &m_AcqParams, &image_info);

	roi_.right = image_info.DimX;
	roi_.bottom = image_info.DimY;

	RecalculateROI();
	return ResizeImageBuffer();
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CProgRes::GetExposure() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
      return 0.0;
   return atof(buf);
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CProgRes::SetExposure(double exp)
{
	ATLTRACE ("%s\n",__FUNCTION__);
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CProgRes::GetBinning() const
{
	ATLTRACE ("%s\n",__FUNCTION__);
   return 1;
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CProgRes::SetBinning(int binFactor)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	binFactor;
   return 1;
}

int CProgRes::SetPixelTypesValues(){
	ATLTRACE ("%s\n",__FUNCTION__);
   int ret = DEVICE_OK;
   vector<string> pixelTypeValues;
   if(color_)
   {
      pixelTypeValues.push_back(g_PixelType_32bit);
   }else
   {
      pixelTypeValues.push_back(g_PixelType_8bit);
      pixelTypeValues.push_back(g_PixelType_16bit);
   }
   ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// CProgRes Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CProgRes::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;
	pProp;

	switch(eAct)
	{
	case MM::AfterSet:
		break;
	case MM::BeforeGet:
		{
			ret=DEVICE_OK;
		}break;
	}
	return ret;
}

/**
* Handles "UseBlackRef" property.
*/
int CProgRes::OnSnapUseBlackRef(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
   int ret = DEVICE_OK;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         string camera;
         pProp->Get(camera);
      }break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/**
* Handles "PixelType" property.
*/
int CProgRes::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;
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
            m_Image.Resize(m_Image.Width(), m_Image.Height(), 1);
            ret=DEVICE_OK;
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
            m_Image.Resize(m_Image.Width(), m_Image.Height(), 2);
            ret=DEVICE_OK;
         }
         else if (pixelType.compare(g_PixelType_32bit) == 0)
         {
            m_Image.Resize(m_Image.Width(), m_Image.Height(), 4);
            ret=DEVICE_OK;
         }
         else
         {
            // on error switch to default pixel type
            m_Image.Resize(m_Image.Width(), m_Image.Height(), 1);
            pProp->Set(g_PixelType_8bit);
            ret = ERR_UNKNOWN_MODE;
         }
      }break;
   case MM::BeforeGet:
      {
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
   
}

/**
* Handles "ReadoutTime" property.
*/
int CProgRes::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
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

/**
* Handles "ColorMode" property.
*/
int CProgRes::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
   int ret = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      if(IsCapturing())
         return DEVICE_CAN_NOT_SET_PROPERTY;

      string pixelType;
      pProp->Get(pixelType);

      if (pixelType.compare(g_ColorMode_Grayscale) == 0)
      {
         color_ = false;
         SetPixelTypesValues();
         SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
      }
      else if (pixelType.compare(g_ColorMode_RGB) == 0)
      {
         color_ = true;
         SetPixelTypesValues();
         SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bit);
      }
      else
      {
         // on error switch to default pixel type
         color_ = false;
         return ERR_UNKNOWN_MODE;
      }
      ret = ResizeImageBuffer();
      OnPropertiesChanged(); // notify GUI to update
   }
   else if (eAct == MM::BeforeGet)
   {
      ret = DEVICE_OK;
   }

   return ret;
}

/**
* Handles exposure time.
*/
int CProgRes::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
   int ret = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		double exposure;
		pProp->Get(exposure);
		ret = mexSetExposureTime2 (m_GUID, (unsigned long) (exposure * 1000.0), TRUE);
		if (ret != NOERROR)
			ret = ERR_ACQ_EXPOSURE;
		OnPropertiesChanged(); // notify GUI to update
   }
   else if (eAct == MM::BeforeGet)
   {
		ret = DEVICE_OK;
   }

   return ret;
}

/**
* Handles camera gain
*/
int CProgRes::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		double gain;
		pProp->Get(gain);
		ret = mexSetGain (m_GUID, gain);
		if (ret != NOERROR)
			ret = ERR_ACQ_GAIN;
		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles acquisition mode change
*/
int CProgRes::OnAcqMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	int ret = DEVICE_OK;
	pProp;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		ret = SetupAcquisition();

		OnPropertiesChanged(); // notify GUI to update
	}

	return ret;
}

/**
* Handles gamma processing
*/
int CProgRes::OnGammaEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long gamma_state;
		pProp->Get(gamma_state);
		gamma_.bActive = (gamma_state != 0);
		ret = mexActivateGammaProcessing (m_GUID, &gamma_);
		if (ret != NOERROR)
			ret = ERR_ACQ_GAMMA;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnGammaValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		double gamma;
		pProp->Get(gamma);
		gamma_.gamma = gamma;
		ret = mexActivateGammaProcessing (m_GUID, &gamma_);
		if (ret != NOERROR)
			ret = ERR_ACQ_GAMMA;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles white balance values
*/
int CProgRes::OnWBRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		pProp->Get(wbred_);
		ret = mexSetWhiteBalance (m_GUID, wbred_, wbgreen_, wbblue_);
		if (ret != NOERROR)
			ret = ERR_ACQ_WHITE_BALANCE;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}
int CProgRes::OnWBGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		pProp->Get(wbgreen_);
		ret = mexSetWhiteBalance (m_GUID, wbred_, wbgreen_, wbblue_);
		if (ret != NOERROR)
			ret = ERR_ACQ_WHITE_BALANCE;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}
int CProgRes::OnWBBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		pProp->Get(wbblue_);
		ret = mexSetWhiteBalance (m_GUID, wbred_, wbgreen_, wbblue_);
		if (ret != NOERROR)
			ret = ERR_ACQ_WHITE_BALANCE;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles focus
*/
int CProgRes::OnFocusState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		if (value != 0)
			focus_.pFocusCallback = FocusCallback;
		else
			focus_.pFocusCallback = NULL;
		ret = mexSetFocusCallback (m_GUID, &focus_, (unsigned long) this);
		if (ret != NOERROR)
			ret = ERR_ACQ_FOCUS;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnFocusRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		focus_.bChannelSelect[0] = value;
		ret = mexSetFocusCallback (m_GUID, &focus_, (unsigned long) this);
		if (ret != NOERROR)
			ret = ERR_ACQ_FOCUS;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnFocusGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		focus_.bChannelSelect[1] = value;
		ret = mexSetFocusCallback (m_GUID, &focus_, (unsigned long) this);
		if (ret != NOERROR)
			ret = ERR_ACQ_FOCUS;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnFocusBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		focus_.bChannelSelect[2] = value;
		ret = mexSetFocusCallback (m_GUID, &focus_, (unsigned long) this);
		if (ret != NOERROR)
			ret = ERR_ACQ_FOCUS;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnFocusValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(m_FocusValue);
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles equalizer
*/
int CProgRes::OnEqualizerEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		equalizer_.bEnable = value;
		ret = mexSetEqualizer (m_GUID, &equalizer_);
		if (ret != NOERROR)
			ret = ERR_ACQ_EQUALIZER;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnEqualizerLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		double value;
		pProp->Get(value);
		equalizer_.Limit = value;
		ret = mexSetEqualizer (m_GUID, &equalizer_);
		if (ret != NOERROR)
			ret = ERR_ACQ_EQUALIZER;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles saturation
*/
int CProgRes::OnSaturationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		saturation_.bActive = value;
		ret = mexActivateSaturationControl (m_GUID, &saturation_);
		if (ret != NOERROR)
			ret = ERR_ACQ_SATURATION;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnSaturationValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		double value;
		pProp->Get(value);
		saturation_.sc = value;
		ret = mexActivateSaturationControl (m_GUID, &saturation_);
		if (ret != NOERROR)
			ret = ERR_ACQ_SATURATION;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles cooling
*/
int CProgRes::OnCameraCooling(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		saturation_.bActive = value;
		ret = mexActivatePeltier (m_GUID, (value != 0));
		if (ret != NOERROR)
			ret = ERR_ACQ_COOLING;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles trigger in
*/
int CProgRes::OnTriggerInEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		std::string property_value;
		pProp->Get(property_value);
		if (property_value.compare(g_triggerIn_No) != 0)
		{
			if (property_value.compare (g_triggerIn_SignalToLow) == 0)
				m_TriggerIn = 1;
			else
				if (property_value.compare (g_triggerIn_SignalToHigh) == 0)
					m_TriggerIn = 2;
					else
						if (property_value.compare (g_triggerOut_ToggleSignal) == 0)
							m_TriggerIn = 5;
						else
							if (property_value.compare (g_triggerIn_Falling) == 0)
								m_TriggerIn = 3;
							else
								if (property_value.compare (g_triggerIn_Rising) == 0)
									m_TriggerIn = 4;
								else
									m_TriggerIn = 0;
		}
		else
			m_TriggerIn = 0;

		ret = mexActivateTriggerIn (m_GUID, m_TriggerIn);
		if (ret != NOERROR)
			ret = ERR_ACQ_TRIGGER_IN;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

/**
* Handles trigger out
*/
int CProgRes::OnTriggerOutOnStartExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		std::string property_value;
		pProp->Get(property_value);
		if (property_value.compare(g_triggerOut_No) != 0)
		{
			if (property_value.compare (g_triggerOut_SignalToLow) == 0)
				trigger_.condition[0] = 1;
			else
				if (property_value.compare (g_triggerOut_SignalToHigh) == 0)
					trigger_.condition[0] = 2;
					else
						if (property_value.compare (g_triggerOut_ToggleSignal) == 0)
							trigger_.condition[0] = 3;
						else
							trigger_.condition[0] = 0;
		}
		else
			trigger_.condition[0] = 0;

		ret = mexActivateTriggerOut (m_GUID, &trigger_);
		if (ret != NOERROR)
			ret = ERR_ACQ_TRIGGER_OUT;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}
int CProgRes::OnTriggerOutOnEndExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		std::string property_value;
		pProp->Get(property_value);
		if (property_value.compare(g_triggerOut_No) != 0)
		{
			if (property_value.compare (g_triggerOut_SignalToLow) == 0)
				trigger_.condition[1] = 1;
			else
				if (property_value.compare (g_triggerOut_SignalToHigh) == 0)
					trigger_.condition[1] = 2;
					else
						if (property_value.compare (g_triggerOut_ToggleSignal) == 0)
							trigger_.condition[1] = 3;
						else
							trigger_.condition[1] = 0;
		}
		else
			trigger_.condition[1] = 0;
		ret = mexActivateTriggerOut (m_GUID, &trigger_);
		if (ret != NOERROR)
			ret = ERR_ACQ_TRIGGER_OUT;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}
int CProgRes::OnTriggerOutOnEndTransfer(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		std::string property_value;
		pProp->Get(property_value);
		if (property_value.compare(g_triggerOut_No) != 0)
		{
			if (property_value.compare (g_triggerOut_SignalToLow) == 0)
				trigger_.condition[2] = 1;
			else
				if (property_value.compare (g_triggerOut_SignalToHigh) == 0)
					trigger_.condition[2] = 2;
					else
						if (property_value.compare (g_triggerOut_ToggleSignal) == 0)
							trigger_.condition[2] = 3;
						else
							trigger_.condition[2] = 0;
		}
		else
			trigger_.condition[2] = 0;
		ret = mexActivateTriggerOut (m_GUID, &trigger_);
		if (ret != NOERROR)
			ret = ERR_ACQ_TRIGGER_OUT;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}

int CProgRes::OnTriggerOutLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ATLTRACE ("%s\n",__FUNCTION__);
	 int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		if(IsCapturing())
			return DEVICE_CAN_NOT_SET_PROPERTY;

		long value;
		pProp->Get(value);
		ret = mexSetTriggerOut (m_GUID, value);
		if (ret != NOERROR)
			ret = ERR_ACQ_TRIGGER_LEVEL;

		OnPropertiesChanged(); // notify GUI to update
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret;
}
///////////////////////////////////////////////////////////////////////////////
// Private CProgRes methods
///////////////////////////////////////////////////////////////////////////////

void CProgRes::RecalculateROI ()
{
	mexAcquisParams acq_params;
	mexImageInfo		image_info;

	memcpy (&acq_params, &m_AcqParams, sizeof (acq_params));
	RECT rc_sensor;
	// take size of complete picture
	SetRect (&rc_sensor, 0,0,m_CameraTypeSummary.SensorX,m_CameraTypeSummary.SensorY);
	acq_params.rcsensorBounds = rc_sensor;
	mexGetAcquisitionInfo (m_GUID, &acq_params, &image_info);

	double width_ratio = (double)m_CameraTypeSummary.SensorX / (double)image_info.DimX;
	double height_ratio = (double)m_CameraTypeSummary.SensorY / (double)image_info.DimY;
	// recalculate with respect to roi_
	SetRect (&rc_sensor, (int) (width_ratio * roi_.left), (int) (height_ratio * roi_.top),(int)(width_ratio * roi_.right), (int) (height_ratio * roi_.bottom));
	m_AcqParams.rcsensorBounds = rc_sensor;
}

/**
* Sync internal image buffer size to the chosen property values.
*/
int CProgRes::ResizeImageBuffer()
{
	ATLTRACE ("%s\n",__FUNCTION__);
   char buf[MM::MaxStrLength];
   int ret;

   ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

   int byteDepth = 1;
   if (strcmp(buf, g_PixelType_16bit) == 0)
      byteDepth = 2;
   else
	if (strcmp(buf, g_PixelType_32bit) == 0)
	      byteDepth = 4;

	mexImageInfo		image_info;
	long err = 0;

	err = mexGetAcquisitionInfo (m_GUID, &m_AcqParams, &image_info);
	if(err!=NOERR)
		return err;

	m_Image.Resize(image_info.DimX, image_info.DimY, byteDepth);

   return DEVICE_OK;
}
/*
int CProgRes::SetAllowedBinning() 
{
   return DEVICE_OK;
}
*/
void CProgRes::SetStopTime()
{
	ATLTRACE ("%s\n",__FUNCTION__);
	readoutStopTime_ = GetCurrentMMTime ();
}

/**
* Starts continuous acquisition.
*
*/
int CProgRes::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	ATLTRACE ("%s\n",__FUNCTION__);

	if (IsCapturing())
		return ERR_BUSY_ACQUIRING;

	stopOnOverflow_ = stopOnOverflow;
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	// make sure the circular buffer is properly sized
	GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

	double actualIntervalMs = max(GetExposure(), interval_ms);
	SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs)); 

	SetupAcquisition(1);
	if ((m_CameraTypeSummary.type == camC7) &&
		((m_AcqParams.mode==mexShot_Raw) || (m_AcqParams.mode==mexC7_colshutter) || 
		(m_AcqParams.mode==mexC7_colstd) || (m_AcqParams.mode == mexC7_rawshutter))
		)
	{
		MessageBox (NULL, "Selected acquisition mode is not supported in live preview.","Warning",MB_OK);
		return ERR_ACQ_PARAMS_ERR;
	}
	m_AcqParams.runas = runas_cclive;
	busy_ = true;
	ret = mexGrab (m_GUID, &m_AcqParams, NULL);
	if (ret != NOERR)
	{
		busy_ = false;
		return ERR_ACQ_PARAMS_ERR;
	}

	busy_ = false;

	acquiring_ = true;
	thd_->Start(numImages, actualIntervalMs);

	return DEVICE_OK;
}

/**
 * Stops acquisition
 */
int CProgRes::StopSequenceAcquisition()
{   
	ATLTRACE ("%s\n",__FUNCTION__);
      if (!thd_->IsStopped()) {
         thd_->Stop();
         thd_->wait();
      }

	mexAbortAcquisition (m_GUID);
	acquiring_ = false;
   printf("Stopped camera streaming.\n");
  return DEVICE_OK;
}

int CProgRes::PushImage()
{
	EnterCriticalSection (&m_CSec);
	int ret = GetCoreCallback()->InsertImage(this, m_Image);
	if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		GetCoreCallback()->ClearImageBuffer(this);
		// repeat the insert
		ret = GetCoreCallback()->InsertImage(this, m_Image);
	}
	LeaveCriticalSection (&m_CSec);
	return ret;
}

int CProgRes::ThreadRun()
{
	
   int ret = PushImage();
   if (ret != DEVICE_OK)
   {
      // error occured so the acquisition must be stopped
	   mexAbortAcquisition (m_GUID);
      LogMessage("Overflow or image dimension mismatch!\n");
   }
   CDeviceUtils::SleepMs((long)GetExposure());
   return ret;
}
