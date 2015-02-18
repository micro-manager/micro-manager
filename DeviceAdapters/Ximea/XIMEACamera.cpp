///////////////////////////////////////////////////////////////////////////////
// FILE:          XIMEACamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XIMEA camera module.
//                
// AUTHOR:        Marian Zajko, <marian.zajko@ximea.com>
// COPYRIGHT:     Marian Zajko and XIMEA GmbH, Münster, 2011
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

#include "XIMEACamera.h"
#include "../../MMDevice/ModuleInterface.h"

using namespace std;
DWORD numDevices = 0;
DWORD numOpenDevs = 0;
vector<string> avail_devs;

/////////////////////////////////////////////////////

const char* g_API_Version     = "API Version";
const char* g_Driver_version  = "Driver Version";
const char* g_MCU_version     = "MCU Version";
const char* g_FPGA_version    = "FPGA Version";

/////////////////////////////////////////////////////

const char* g_Data_Format      = "Data format";
const char* g_PixelType_Mono8  = "Mono 8";
const char* g_PixelType_Raw8   = "RAW 8";
const char* g_PixelType_Mono10 = "Mono 10";
const char* g_PixelType_Raw10  = "RAW 10";
const char* g_PixelType_Mono12 = "Mono 12";
const char* g_PixelType_Raw12  = "RAW 12";
const char* g_PixelType_Mono14 = "Mono 14";
const char* g_PixelType_Raw14  = "RAW 14";
const char* g_PixelType_RGB32  = "RGB 32";

/////////////////////////////////////////////////////

const char* g_SensorTaps       = "Sensor taps";

/////////////////////////////////////////////////////

const char* g_Trigger_Mode     = "Trigger mode";
const char* g_Trigger_Off      = "Trigger off";
const char* g_HW_Rising        = "Rising edge";
const char* g_HW_Falling       = "Falling edge";

/////////////////////////////////////////////////////

const char* g_Gpi_1              = "GPI port 1";
const char* g_Gpi_2              = "GPI port 2";
const char* g_Gpi_3              = "GPI port 3";
const char* g_Gpi_4              = "GPI port 4";

const char* g_Gpo_1              = "GPO port 1";
const char* g_Gpo_2              = "GPO port 2";
const char* g_Gpo_3              = "GPO port 3";
const char* g_Gpo_4              = "GPO port 4";

const char* g_Gpi_Off            = "Input off";
const char* g_Gpi_Trigger        = "Trigger input";

const char* g_Gpo_Off            = "Output off";
const char* g_Gpo_On             = "Output on";
const char* g_Gpo_FrameActive    = "Frame active";
const char* g_Gpo_FrameActiveNeg = "Frame active neg.";
const char* g_Gpo_ExpActive      = "Exposure active";
const char* g_Gpo_ExpActiveNeg   = "Exposure active neg.";

const char* g_Gpo_FrmTrgWait     = "Frame trig. wait";
const char* g_Gpo_FrmTrgWaitNeg  = "Frame trig. wait neg.";
const char* g_Gpo_ExpPulse       = "Exposure pulse"; 
const char* g_Gpo_ExpPulseNeg    = "Exposure pulse neg."; 
const char* g_Gpo_Busy           = "Busy"; 
const char* g_Gpo_BusyNeg        = "Busy neg."; 

/////////////////////////////////////////////////////

const char* g_Wb_Red             = "White ballance red";
const char* g_Wb_Green           = "White ballance green";
const char* g_Wb_Blue            = "White ballance blue";

const char* g_AWB                = "Auto white balance";
const char* g_AWB_On             = "On";
const char* g_AWB_Off            = "Off";

const char* g_Gamma_Y            = "Gamma Y";
const char* g_Gamma_C            = "Gamma C";
const char* g_Sharpness          = "Sharpness";

const char* g_CcMatrix           = "Color corr. matrix";

/////////////////////////////////////////////////////

const char* g_AEAG               = "Auto exposure/gain";
const char* g_AEAG_On            = "On";
const char* g_AEAG_Off           = "Off";

const char* g_AEAG_ExpPrio       = "AEAG Exposure priority";
const char* g_AEAG_ExpLim        = "AEAG Exposure limit";
const char* g_AEAG_GainLim       = "AEAG Gain limit";
const char* g_AEAG_Level         = "AEAG Intensity level";

/////////////////////////////////////////////////////

const char* g_BPC                = "Bad pixel correction";
const char* g_BPC_On             = "On";
const char* g_BPC_Off            = "Off";

/////////////////////////////////////////////////////

const char* g_Cooling            = "Cooling";
const char* g_Cooling_On         = "On";
const char* g_Cooling_Off        = "Off";

const char* g_Cooling_Temp       = "Cooling - Target temp";
const char* g_Chip_Temp          = "Cooling - Chip temperature";
const char* g_House_Temp         = "Cooling - Housing temperature";

/////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/***********************************************************************
* Update available devices list
*/
void UpdateDevList()
{
	avail_devs.clear();
	xiGetNumberDevices( &numDevices);
	for(DWORD i = 0; i < numDevices; i++){
		char buf[256] = "";
		DWORD deviceId = 0;
		mmGetDevice(i, &deviceId);
		sprintf(buf, "%8X", deviceId);
		avail_devs.push_back(buf);
		RegisterDevice(buf, MM::CameraDevice, "XIMEA camera adapter");
	}
}

/***********************************************************************
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
	UpdateDevList();
}

//***********************************************************************

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;
	
	xiGetNumberDevices( &numDevices);
	if(numDevices == 0)		
		return 0;

	if(avail_devs.size() == 0)
		UpdateDevList();

	for(DWORD i = 0; i < numDevices; i++){
		if (strcmp(deviceName, avail_devs[i].c_str()) == 0)
			return new XIMEACamera(deviceName);
	}

	// ...supplied name not recognized
	return 0;
}

//***********************************************************************

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// XIMEACamera implementation
/***********************************************************************
* XIMEACamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
XIMEACamera::XIMEACamera(const char* name) :
	name_(name),
	handle (0),
	binning_ (1),
	tapcnt_ (1),
	acqTout_(0),
	bytesPerPixel_(1),
	gain_(0.8),
	adc_(0),
	exposureMs_(10.0),
	nComponents_(1),
	initialized_(false),
	isTrg_(false),
	roiX_(0),
	roiY_(0),
	readoutStartTime_(0),
	sequenceStartTime_(0),
	imageCounter_(0),
	stopOnOverflow_(false),
	isAcqRunning(false)
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
	thd_ = new MySequenceThread(this);
}

/***********************************************************************
* XIMEACamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
XIMEACamera::~XIMEACamera()
{
	if (initialized_)
		Shutdown();
	delete thd_;
}

/***********************************************************************
* Obtains device name.
* Required by the MM::Device API.
*/
void XIMEACamera::GetName(char* name) const
{
	// Do not use xiGetParamString(handle, XI_PRM_DEVICE_NAME, ...) here,
	// because we may be called before Initialize().
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

/***********************************************************************
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int XIMEACamera::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	// -------------------------------------------------------------------------------------
	// Open camera device
	int ret = xiOpenDevice( numOpenDevs, &handle);
	if(ret != XI_OK) 
		return DEVICE_ERR;

	// reset camera timestamp
	ret = xiSetParamInt(handle, XI_PRM_TS_RST_SOURCE, XI_TS_RST_SRC_SW);
	if (ret != XI_OK) return DEVICE_ERR;

	ret = xiSetParamInt(handle, XI_PRM_TS_RST_MODE, XI_TS_RST_ARM_ONCE);
	if (ret != XI_OK) return DEVICE_ERR;
	
	// -------------------------------------------------------------------------------------
	// Set property list
	// -------------------------------------------------------------------------------------
	// camera identification
	char buf[256]="";
	
	xiGetParamString( handle, XI_PRM_DEVICE_SN, buf, 256);
	ret = CreateProperty(MM::g_Keyword_CameraID, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_DEVICE_NAME, buf, 256);
	ret = CreateProperty(MM::g_Keyword_CameraName, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_DEVICE_TYPE, buf, 256);
	ret = CreateProperty(MM::g_Keyword_Description, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_API_VERSION, buf, 256);
	ret = CreateProperty(g_API_Version, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_DRV_VERSION, buf, 256);
	ret = CreateProperty(g_Driver_version, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_MCU1_VERSION, buf, 256);
	ret = CreateProperty(g_MCU_version, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	xiGetParamString( handle, XI_PRM_FPGA1_VERSION, buf, 256);
	ret = CreateProperty(g_FPGA_version, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	// -------------------------------------------------------------------------------------
	// binning
	CPropertyAction *pAct = new CPropertyAction (this, &XIMEACamera::OnBinning);
	ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);

	int maxBin = 0;
	vector<string> binningValues;

	xiGetParamInt( handle, XI_PRM_DOWNSAMPLING XI_PRM_INFO_MAX, &maxBin);

	for(int i = 1; i <= maxBin; i++){
		if(xiSetParamInt(handle, XI_PRM_DOWNSAMPLING, i) == XI_OK){
			char buf[16];
			sprintf(buf, "%d", i);
			binningValues.push_back(buf);
		}
	}

	xiSetParamInt(handle, XI_PRM_DOWNSAMPLING, 1);
	ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);
	assert(ret == DEVICE_OK);


	// -------------------------------------------------------------------------------------
	// data format
	int isColor  = 0;
	DWORD family=0;
	xiGetParamInt( handle, XI_PRM_IMAGE_IS_COLOR, &isColor);
	mmGetModelFamily( handle, &family);
	xiGetParamInt(handle, XI_PRM_OUTPUT_DATA_BIT_DEPTH, &adc_);

	pAct = new CPropertyAction (this, &XIMEACamera::OnDataFormat);
	ret = CreateProperty(g_Data_Format, g_PixelType_Mono8, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> pixelTypeValues;
	pixelTypeValues.push_back(g_PixelType_Mono8);
	pixelTypeValues.push_back(g_PixelType_Mono10);
	pixelTypeValues.push_back(g_PixelType_Mono12);
	
	if(family == FAMILY_MR || (family == FAMILY_MD && !isColor)) 
		pixelTypeValues.push_back(g_PixelType_Mono14); 

	if(isColor){
		pixelTypeValues.push_back(g_PixelType_Raw8);
		pixelTypeValues.push_back(g_PixelType_Raw10);
		pixelTypeValues.push_back(g_PixelType_Raw12);
		if(family==FAMILY_MR || family == FAMILY_MD) 
			pixelTypeValues.push_back(g_PixelType_Raw14); 
		pixelTypeValues.push_back(g_PixelType_RGB32); 
	}

	ret = SetAllowedValues(g_Data_Format, pixelTypeValues);
	assert(ret == DEVICE_OK);

	// -------------------------------------------------------------------------------------
	// sensor taps for MD cameras
	if(family == FAMILY_MD)
	{
		pAct = new CPropertyAction (this, &XIMEACamera::OnSensorTaps);
		ret = CreateProperty(g_SensorTaps, "1", MM::Integer, false, pAct);
		assert(ret == DEVICE_OK);
	
		int maxTaps = 0;
		vector<string> tapsValues;
		xiGetParamInt( handle, XI_PRM_SENSOR_TAPS XI_PRM_INFO_MAX, &maxTaps);

		for(int i = 1; i <= maxTaps; i++){
			if(xiSetParamInt(handle, XI_PRM_SENSOR_TAPS, i) == XI_OK){
				char buf[16];
				sprintf(buf, "%d", i);
				binningValues.push_back(buf);
			}
		}

		xiSetParamInt(handle, XI_PRM_SENSOR_TAPS, 1);
		ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);
		assert(ret == DEVICE_OK);
	}

	// -------------------------------------------------------------------------------------
	// gain
	pAct = new CPropertyAction (this, &XIMEACamera::OnGain);
	ret = CreateProperty(MM::g_Keyword_Gain, "0.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	float minG = 0, maxG = 0;
	xiGetParamFloat( handle, XI_PRM_GAIN XI_PRM_INFO_MIN, &minG);
	xiGetParamFloat( handle, XI_PRM_GAIN XI_PRM_INFO_MAX, &maxG);

	SetPropertyLimits(MM::g_Keyword_Gain, minG, maxG);

	// -------------------------------------------------------------------------------------
	// acq timeout
	acqTout_ = 5000;
	pAct = new CPropertyAction (this, &XIMEACamera::OnAcqTout);
	ret = CreateProperty(MM::g_Keyword_CoreTimeoutMs, "5000.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	//-------------------------------------------------------------------------------------
	// Trigger
	pAct = new CPropertyAction (this, &XIMEACamera::OnTrigger);
	ret = xiSetParamInt( handle, XI_PRM_TRG_SOURCE, XI_TRG_OFF);
	assert(ret == DEVICE_OK);
	ret = CreateProperty(g_Trigger_Mode, g_Trigger_Off, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> triggerTypeValues;
	triggerTypeValues.push_back(g_Trigger_Off);
	triggerTypeValues.push_back(g_HW_Rising); 
	triggerTypeValues.push_back(g_HW_Falling); 

	ret = SetAllowedValues(g_Trigger_Mode, triggerTypeValues);
	assert(ret == DEVICE_OK);

	//-------------------------------------------------------------------------------------
	// GPIO selection
	xiGetParamString( handle, XI_PRM_DEVICE_TYPE, buf, 256);
	vector<string> gpiTypeValues;
	gpiTypeValues.push_back(g_Gpi_Off);
	gpiTypeValues.push_back(g_Gpi_Trigger);

	if(strcmp( buf, "1394") == 0){
		// use supported modes
		vector<string> gpoTypeValues;
		gpoTypeValues.push_back(g_Gpo_FrameActive);
		gpoTypeValues.push_back(g_Gpo_FrameActiveNeg);
		gpoTypeValues.push_back(g_Gpo_ExpActive);
		gpoTypeValues.push_back(g_Gpo_ExpActiveNeg);
		// init GPO port 1 for firewire cameras
		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo1);
		ret = CreateProperty(g_Gpo_1, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_1, gpoTypeValues);
		xiSetParamInt( handle, XI_PRM_GPO_SELECTOR, 1);
		xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_ACTIVE);
		assert(ret == DEVICE_OK);
	} else if(strcmp(buf, "USB3.0") == 0){
		vector<string> gpoTypeValues;
		gpoTypeValues.push_back(g_Gpo_Off);
		gpoTypeValues.push_back(g_Gpo_On);
		gpoTypeValues.push_back(g_Gpo_FrameActive);
		gpoTypeValues.push_back(g_Gpo_FrameActiveNeg);
		gpoTypeValues.push_back(g_Gpo_ExpActive);
		gpoTypeValues.push_back(g_Gpo_ExpActiveNeg);
		gpoTypeValues.push_back(g_Gpo_FrmTrgWait);
		gpoTypeValues.push_back(g_Gpo_FrmTrgWaitNeg);
		gpoTypeValues.push_back(g_Gpo_ExpPulse);
		gpoTypeValues.push_back(g_Gpo_ExpPulseNeg);
		gpoTypeValues.push_back(g_Gpo_Busy);
		gpoTypeValues.push_back(g_Gpo_BusyNeg);
		// init GPI1
		pAct = new CPropertyAction (this, &XIMEACamera::OnGpi1);
		ret = CreateProperty(g_Gpi_1, g_Gpi_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpi_1, gpiTypeValues);
		assert(ret == DEVICE_OK);
		xiSetParamInt( handle, XI_PRM_GPI_SELECTOR, 1);
		xiSetParamInt( handle, XI_PRM_GPI_MODE, XI_GPI_OFF);
		// init GPO1
		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo1);
		ret = CreateProperty(g_Gpo_1, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_1, gpoTypeValues);
		assert(ret == DEVICE_OK);
		xiSetParamInt( handle, XI_PRM_GPO_SELECTOR, 1);
		xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_ACTIVE);

		// MD cameras have additional input and output
		if(family == FAMILY_MD)
		{
			pAct = new CPropertyAction (this, &XIMEACamera::OnGpi2);
			ret = CreateProperty(g_Gpi_2, g_Gpi_Off, MM::String, false, pAct);
			assert(ret == DEVICE_OK);
			ret = SetAllowedValues(g_Gpi_2, gpiTypeValues);
			assert(ret == DEVICE_OK);

			pAct = new CPropertyAction (this, &XIMEACamera::OnGpo2);
			ret = CreateProperty(g_Gpo_2, g_Gpo_FrameActive, MM::String, false, pAct);
			assert(ret == DEVICE_OK);
			ret = SetAllowedValues(g_Gpo_2, gpoTypeValues);
			assert(ret == DEVICE_OK);
		}

	} else if((strcmp(buf, "USB2.0") == 0) || strcmp(buf, "CURRERA") == 0){
		vector<string> gpoTypeValues;
		if((strcmp(buf, "USB2.0") == 0)){
			gpoTypeValues.push_back(g_Gpo_Off);
			gpoTypeValues.push_back(g_Gpo_On);
			gpoTypeValues.push_back(g_Gpo_FrameActive);
		} else {
			gpoTypeValues.push_back(g_Gpo_Off);
			gpoTypeValues.push_back(g_Gpo_On);
			gpoTypeValues.push_back(g_Gpo_FrameActive);
			gpoTypeValues.push_back(g_Gpo_FrameActiveNeg);
			gpoTypeValues.push_back(g_Gpo_ExpActive);
			gpoTypeValues.push_back(g_Gpo_ExpActiveNeg);
		}
		
		// init GPI 1-4 port
		pAct = new CPropertyAction (this, &XIMEACamera::OnGpi1);
		ret = CreateProperty(g_Gpi_1, g_Gpi_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpi_1, gpiTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpi2);
		ret = CreateProperty(g_Gpi_2, g_Gpi_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpi_2, gpiTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpi3);
		ret = CreateProperty(g_Gpi_3, g_Gpi_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpi_3, gpiTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpi4);
		ret = CreateProperty(g_Gpi_4, g_Gpi_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpi_4, gpiTypeValues);
		assert(ret == DEVICE_OK);

		// init GPO 1-4 port
		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo1);
		ret = CreateProperty(g_Gpo_1, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_1, gpoTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo2);
		ret = CreateProperty(g_Gpo_2, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_2, gpoTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo3);
		ret = CreateProperty(g_Gpo_3, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_3, gpoTypeValues);
		assert(ret == DEVICE_OK);

		pAct = new CPropertyAction (this, &XIMEACamera::OnGpo4);
		ret = CreateProperty(g_Gpo_4, g_Gpo_FrameActive, MM::String, false, pAct);
		assert(ret == DEVICE_OK);
		ret = SetAllowedValues(g_Gpo_4, gpoTypeValues);
		assert(ret == DEVICE_OK);
	}

	//-------------------------------------------------------------------------------------
	// Color correction
	if(isColor){
		pAct = new CPropertyAction (this, &XIMEACamera::OnWbRed);
		ret = CreateProperty(g_Wb_Red, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		float min = 0, max = 0;
		xiGetParamFloat( handle, XI_PRM_WB_KR XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_WB_KR XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Wb_Red, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnWbGreen);
		ret = CreateProperty(g_Wb_Green, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_WB_KG XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_WB_KG XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Wb_Green, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnWbBlue);
		ret = CreateProperty(g_Wb_Blue, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_WB_KB XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_WB_KB XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Wb_Blue, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnAWB);
		ret = CreateProperty(g_AWB, g_AWB_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);

		vector<string> awbTypeValues;
		awbTypeValues.push_back(g_AWB_On);
		awbTypeValues.push_back(g_AWB_Off); 

		ret = SetAllowedValues(g_AWB, awbTypeValues);
		assert(ret == DEVICE_OK);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnGammaY);
		ret = CreateProperty(g_Gamma_Y, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_GAMMAY XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_GAMMAY XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Gamma_Y, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnGammaC);
		ret = CreateProperty(g_Gamma_C, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_GAMMAC XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_GAMMAC XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Gamma_C, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnSharpness);
		ret = CreateProperty(g_Sharpness, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_SHARPNESS XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_SHARPNESS XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Sharpness, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnCcMatrix);
		string ccmtx = "";
		
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 4; j++){
				if(i == j && i < 3 && j < 3)
					ccmtx.append("1.00|");
				else if(i == 3 && j == 2)
					ccmtx.append("1.00|");
				else
					ccmtx.append("0.00|");
			}
		}
		ccmtx.erase(ccmtx.length() - 1);
		
		ret = CreateProperty(g_CcMatrix, ccmtx.c_str(), MM::String, false, pAct);
		assert(ret == DEVICE_OK);
	}
	//-------------------------------------------------------------------------------------
	// Auto exposure/gain
	pAct = new CPropertyAction (this, &XIMEACamera::OnAEAG);
	ret = CreateProperty(g_AEAG, g_AEAG_Off, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> aeagTypeValues;
	aeagTypeValues.push_back(g_AEAG_On);
	aeagTypeValues.push_back(g_AEAG_Off); 

	ret = SetAllowedValues(g_AEAG, aeagTypeValues);
	assert(ret == DEVICE_OK);
	///////////////////////////////////////////////////////////////
	pAct = new CPropertyAction (this, &XIMEACamera::OnExpPrio);
	ret = CreateProperty(g_AEAG_ExpPrio, "0.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	float min = 0, max = 0;
	xiGetParamFloat( handle, XI_PRM_EXP_PRIORITY XI_PRM_INFO_MIN, &min);
	xiGetParamFloat( handle, XI_PRM_EXP_PRIORITY XI_PRM_INFO_MAX, &max);
	SetPropertyLimits(g_AEAG_ExpPrio, min, max);
	///////////////////////////////////////////////////////////////
	pAct = new CPropertyAction (this, &XIMEACamera::OnExpLim);
	ret = CreateProperty(g_AEAG_ExpLim, "0.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	xiGetParamFloat( handle, XI_PRM_AE_MAX_LIMIT XI_PRM_INFO_MIN, &min);
	xiGetParamFloat( handle, XI_PRM_AE_MAX_LIMIT XI_PRM_INFO_MAX, &max);
	SetPropertyLimits(g_AEAG_ExpLim, min, max/1000);
	///////////////////////////////////////////////////////////////
	pAct = new CPropertyAction (this, &XIMEACamera::OnGainLim);
	ret = CreateProperty(g_AEAG_GainLim, "0.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	xiGetParamFloat( handle, XI_PRM_AG_MAX_LIMIT XI_PRM_INFO_MIN, &min);
	xiGetParamFloat( handle, XI_PRM_AG_MAX_LIMIT XI_PRM_INFO_MAX, &max);
	SetPropertyLimits(g_AEAG_GainLim, min, max);
	///////////////////////////////////////////////////////////////
	pAct = new CPropertyAction (this, &XIMEACamera::OnLevel);
	ret = CreateProperty(g_AEAG_Level, "0.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	xiGetParamFloat( handle, XI_PRM_AEAG_LEVEL XI_PRM_INFO_MIN, &min);
	xiGetParamFloat( handle, XI_PRM_AEAG_LEVEL XI_PRM_INFO_MAX, &max);
	SetPropertyLimits(g_AEAG_Level, min, max);
	
	//-------------------------------------------------------------------------------------
	// Bad Pixels Correction
	pAct = new CPropertyAction (this, &XIMEACamera::OnBpc);
	ret = CreateProperty(g_BPC, g_BPC_Off, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> bpcValues;
	bpcValues.push_back(g_BPC_On);
	bpcValues.push_back(g_BPC_Off); 

	ret = SetAllowedValues(g_BPC, bpcValues);
	assert(ret == DEVICE_OK);

	//-------------------------------------------------------------------------------------
	// Temperature control
	int isCooled = 0;
	xiGetParamInt(handle, XI_PRM_IS_COOLED, &isCooled);
	if(isCooled){
		pAct = new CPropertyAction (this, &XIMEACamera::OnCooling);
		ret = CreateProperty(g_Cooling, g_Cooling_Off, MM::String, false, pAct);
		assert(ret == DEVICE_OK);

		vector<string> coolingTypeValues;
		coolingTypeValues.push_back(g_Cooling_On);
		coolingTypeValues.push_back(g_Cooling_Off); 

		ret = SetAllowedValues(g_Cooling, coolingTypeValues);
		assert(ret == DEVICE_OK);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnTargetTemp);
		ret = CreateProperty(g_Cooling_Temp, "0.0", MM::Float, false, pAct);
		assert(ret == DEVICE_OK);

		xiGetParamFloat( handle, XI_PRM_TARGET_TEMP XI_PRM_INFO_MIN, &min);
		xiGetParamFloat( handle, XI_PRM_TARGET_TEMP XI_PRM_INFO_MAX, &max);
		SetPropertyLimits(g_Cooling_Temp, min, max);
		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnChipTemp);
		ret = CreateProperty(g_Chip_Temp, "0.0", MM::Float, true, pAct);
		assert(ret == DEVICE_OK);

		///////////////////////////////////////////////////////////////
		pAct = new CPropertyAction (this, &XIMEACamera::OnHousTemp);
		ret = CreateProperty(g_House_Temp, "0.0", MM::Float, true, pAct);
		assert(ret == DEVICE_OK);
	}

	//-------------------------------------------------------------------------------------
	// synchronize all properties
	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	// setup the buffer
	// ----------------
	int width = 0, height = 0;
	xiGetParamInt( handle, XI_PRM_WIDTH, &width);
	xiGetParamInt( handle, XI_PRM_HEIGHT, &height);
		
	img_ = new ImgBuffer(  width, height, bytesPerPixel_);	
	ret = ResizeImageBuffer();
	if (ret != DEVICE_OK)
		return ret;
	
	// set default exposure
	ret = xiSetParamInt( handle, XI_PRM_EXPOSURE, (int)exposureMs_*1000);
	if (ret != DEVICE_OK)
		return ret;

	memset(&image, 0, sizeof(XI_IMG));
	numOpenDevs++;
	initialized_ = true;
	return DEVICE_OK;
}

/***********************************************************************
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int XIMEACamera::Shutdown()
{
	if(initialized_){
		numOpenDevs--;
		xiStopAcquisition(handle);
		xiCloseDevice(handle);
		delete img_;
	}
	initialized_ = false;
	return DEVICE_OK;
}

/***********************************************************************
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int XIMEACamera::SnapImage()
{
	image.size = sizeof(XI_IMG);
	int ret = DEVICE_OK;
	if (!isAcqRunning)
	{
		if (xiStartAcquisition(handle) != XI_OK)
			return DEVICE_ERR;		
	}
	
	ret = xiGetImage( handle, (DWORD)acqTout_, &image);

	if (!isAcqRunning)
	{
		if (xiStopAcquisition(handle) != XI_OK)
			return DEVICE_ERR;
	}	

	readoutStartTime_.sec_ = image.tsSec;
	readoutStartTime_.uSec_ = image.tsUSec;

	// use time of first successfully captured frame for sequence start
	if (sequenceStartTime_.sec_ == 0 && sequenceStartTime_.uSec_ == 0)
	{
		sequenceStartTime_ = readoutStartTime_;
	}

	return ret;
}

/***********************************************************************
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* XIMEACamera::GetImageBuffer()
{
	if(image.padding_x == 0)
		img_->SetPixels(image.bp);
	else
		img_->SetPixelsPadded(image.bp, image.padding_x);
	return const_cast<unsigned char*>(img_->GetPixels());
}

/***********************************************************************
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned XIMEACamera::GetImageWidth() const
{
	return img_->Width();
}

/***********************************************************************
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned XIMEACamera::GetImageHeight() const
{
	return img_->Height();
}

/***********************************************************************
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned XIMEACamera::GetImageBytesPerPixel() const
{
	int bpp = 0, frm = 0;
	xiGetParamInt( handle, XI_PRM_IMAGE_DATA_FORMAT, &frm);
	switch(frm)
	{
	case XI_MONO8      : 
	case XI_RAW8       :
		bpp = 1; break;
	case XI_MONO16     : 
	case XI_RAW16      :
		bpp = 2; break;
	case XI_RGB32      : 
		bpp = 4; break;
	default:
		assert(false); // this should never happen
	}
	return bpp;
} 

/***********************************************************************
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned XIMEACamera::GetBitDepth() const
{
	return adc_;
}

/***********************************************************************
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long XIMEACamera::GetImageBufferSize() const
{
	return img_->Width() * img_->Height() * GetImageBytesPerPixel();
}

/***********************************************************************
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
int XIMEACamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	int ret = DEVICE_OK;
	if (xSize == 0 && ySize == 0)
	{
		// effectively clear ROI
		int width = 0, height = 0;
		xiSetParamInt( handle, XI_PRM_OFFSET_X, 0);
		xiSetParamInt( handle, XI_PRM_OFFSET_Y, 0);
		xiGetParamInt( handle, XI_PRM_WIDTH XI_PRM_INFO_MAX, &width);
		xiGetParamInt( handle, XI_PRM_HEIGHT XI_PRM_INFO_MAX, &height);
		
		xiSetParamInt( handle, XI_PRM_WIDTH, width - (width%4));
		xiSetParamInt( handle, XI_PRM_HEIGHT, height);

		ResizeImageBuffer();
		roiX_ = 0;
		roiY_ = 0;
	}
	else
	{
		// apply ROI
		ret = xiSetParamInt( handle, XI_PRM_WIDTH, xSize - (xSize%4));
		if(ret != XI_OK)
			return ret;

		ret = xiSetParamInt( handle, XI_PRM_HEIGHT, ySize - (ySize%2));
		if(ret != XI_OK)
			return ret;

		ret = xiSetParamInt( handle, XI_PRM_OFFSET_X, x - (x%2));
		if(ret != XI_OK)
			return ret;
		
		ret = xiSetParamInt( handle, XI_PRM_OFFSET_Y, y - (y%2));
		if(ret != XI_OK)
			return ret;
		

		img_->Resize(xSize - (xSize%4), ySize - (ySize%2));
		roiX_ = x - (x%2);
		roiY_ = y - (y%2);
	}
	return ret;
}

/***********************************************************************
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int XIMEACamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	int width = 0, height = 0, offx = 0, offy = 0;
	xiGetParamInt( handle, XI_PRM_WIDTH, &width);
	xiGetParamInt( handle, XI_PRM_HEIGHT, &height);
	xiGetParamInt( handle, XI_PRM_OFFSET_X, &offx);
	xiGetParamInt( handle, XI_PRM_OFFSET_Y, &offy);
	
	x = offx;
	y = offy;
	xSize = width;
	ySize = height;
	return DEVICE_OK;
}

/***********************************************************************
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int XIMEACamera::ClearROI()
{
	int width = 0, height = 0;
	xiSetParamInt( handle, XI_PRM_OFFSET_X, 0);
	xiSetParamInt( handle, XI_PRM_OFFSET_Y, 0);
	xiGetParamInt( handle, XI_PRM_WIDTH XI_PRM_INFO_MAX, &width);
	xiGetParamInt( handle, XI_PRM_HEIGHT XI_PRM_INFO_MAX, &height);

	xiSetParamInt( handle, XI_PRM_WIDTH, width - (width%4));
	xiSetParamInt( handle, XI_PRM_HEIGHT, height);

	ResizeImageBuffer();
	roiX_ = 0;
	roiY_ = 0;

	return DEVICE_OK;
}

/***********************************************************************
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double XIMEACamera::GetExposure() const
{
	return exposureMs_;
}

/***********************************************************************
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void XIMEACamera::SetExposure(double exp)
{
	if(exposureMs_ != exp){
		int ret = xiSetParamInt( handle, XI_PRM_EXPOSURE, (int)exp*1000);
		if(ret == XI_OK)
			exposureMs_ = exp;
	}
}

/***********************************************************************
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int XIMEACamera::GetBinning() const
{
	return binning_;
}

/***********************************************************************
* Sets binning factor.
* Required by the MM::Camera API.
*/
int XIMEACamera::SetBinning(int binF)
{
	(void)xiSetParamInt( handle, XI_PRM_DOWNSAMPLING, binF);
	ResizeImageBuffer();
	return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

/***********************************************************************
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int XIMEACamera::StartSequenceAcquisition(double interval)
{
	return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/***********************************************************************                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int XIMEACamera::StopSequenceAcquisition()                                     
{
	isAcqRunning = false;
	// stop image acquisition
	XI_RETURN ret = xiStopAcquisition(handle);
	if (ret != XI_OK) return DEVICE_ERR;

	if (!thd_->IsStopped()) {
		thd_->Stop();                                                       
		thd_->wait();                                                       
	}                                                                      
	return DEVICE_OK;                                                       
} 

/***********************************************************************
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int XIMEACamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	if (IsCapturing())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	// reset camera timestamp
	ret = xiSetParamInt(handle, XI_PRM_TS_RST_SOURCE, XI_TS_RST_SRC_SW);
	if (ret != XI_OK) return DEVICE_ERR;

	ret = xiSetParamInt(handle, XI_PRM_TS_RST_MODE, XI_TS_RST_ARM_ONCE);
	if (ret != XI_OK) return DEVICE_ERR;

	// start image acquisition	
	ret = xiStartAcquisition(handle);
	if (ret != XI_OK) return DEVICE_ERR;
	isAcqRunning = true;

	sequenceStartTime_.sec_ = 0;
	sequenceStartTime_.uSec_ = 0;

	imageCounter_ = 0;
	thd_->Start( numImages, interval_ms);
	stopOnOverflow_ = stopOnOverflow;
	return DEVICE_OK;
}

/***********************************************************************
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int XIMEACamera::InsertImage()
{
	int ret = DEVICE_OK;

	MM::MMTime timeStamp = readoutStartTime_;
	char label[MM::MaxStrLength];
	this->GetLabel(label);

	// Important:  metadata about the image are generated here:
	Metadata md;
	md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
	md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
	md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
	md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
	md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 
	
	imageCounter_++;

	char buf[MM::MaxStrLength];
	GetProperty(MM::g_Keyword_Binning, buf);
	md.put(MM::g_Keyword_Binning, buf);

	MMThreadGuard g(imgPixelsLock_);
	const unsigned char* pI = GetImageBuffer();
	unsigned int w = GetImageWidth();
	unsigned int h = GetImageHeight();
	unsigned int b = GetImageBytesPerPixel();

	ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
	if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		GetCoreCallback()->ClearImageBuffer(this);
		// don't process this same image again...
		// return GetCoreCallback()->InsertImage(this, pI, w, h, b, &md, false);
		return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
	} else
		return ret;
}


/***********************************************************************
 * Do actual capturing
 * Called from inside the thread  
 */
int XIMEACamera::ThreadRun (void)
{
   int ret = DEVICE_ERR;
   
   ret = SnapImage();
   if (ret != DEVICE_OK)
      return ret;

   ret = InsertImage();
   return ret;
};


/***********************************************************************
 * called from the thread function before exit 
 */
void XIMEACamera::OnThreadExiting() throw()
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

//***********************************************************************

bool XIMEACamera::IsCapturing() 
{
	return !thd_->IsStopped();
}

///////////////////////////////////////////////////////////////////////////////
// XIMEACamera Action handlers
/***********************************************************************
* Handles "Binning" property.
*/
int XIMEACamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		long binSize;
		pProp->Get(binSize);
		binning_ = (int)binSize;
		ret = xiSetParamInt( handle, XI_PRM_DOWNSAMPLING, binning_);

		int width = 0;
		xiGetParamInt( handle, XI_PRM_WIDTH XI_PRM_INFO_MAX, &width);
		xiSetParamInt( handle, XI_PRM_WIDTH, width - (width%4));
		return ResizeImageBuffer();
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)binning_);
	}

	return ret;
}

/***********************************************************************
* Handles "PixelType" property.
*/
int XIMEACamera::OnDataFormat(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		string val;
		int img_format=0;
		bool update_bpp = true;
		pProp->Get(val);
		int ret = DEVICE_ERR;
		//MONO8
		if (val.compare(g_PixelType_Mono8) == 0){ 
			img_format=XI_MONO8;
			update_bpp=false;
			bytesPerPixel_ = 1;
			nComponents_ = 1;
		//RAW8
		}else if (val.compare(g_PixelType_Raw8) == 0){ 
			img_format=XI_RAW8;
			update_bpp=false;
			bytesPerPixel_ = 1;
			nComponents_ = 1;
		//MONO10
		}else if (val.compare(g_PixelType_Mono10) == 0){
			img_format=XI_MONO16;
			adc_=10;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//RAW10
		}else if (val.compare(g_PixelType_Raw10) == 0){
			img_format=XI_RAW16;
			adc_=10;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//MONO12
		}else if (val.compare(g_PixelType_Mono12) == 0){
			img_format=XI_MONO16;
			adc_=12;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//RAW12
		}else if (val.compare(g_PixelType_Raw12) == 0){
			img_format=XI_RAW16;
			adc_=12;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//MONO14
		}else if (val.compare(g_PixelType_Mono14) == 0){
			img_format=XI_MONO16;
			adc_=14;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//RAW14
		}else if (val.compare(g_PixelType_Raw14) == 0){
			img_format=XI_RAW16;
			adc_=14;
			bytesPerPixel_ = 2;
			nComponents_ = 1;
		//RGB32
		}else if (val.compare(g_PixelType_RGB32) == 0){
			img_format=XI_RGB32;
			adc_=12;
			bytesPerPixel_ = 4;
			nComponents_ = 4;
		}else
			assert(false);
		
		ret = xiSetParamInt( handle, XI_PRM_IMAGE_DATA_FORMAT, img_format);
		if(ret != XI_OK) return ret;
		if(update_bpp)
		{
			ret = xiSetParamInt( handle, XI_PRM_OUTPUT_DATA_BIT_DEPTH, adc_);
			if(ret != XI_OK) return ret;
		}
		ResizeImageBuffer();
		return ret;
	}
	else if (eAct == MM::BeforeGet)
	{
		int frm = 0;
		xiGetParamInt( handle, XI_PRM_IMAGE_DATA_FORMAT, &frm);
		switch(frm)
		{
		case XI_MONO8:
			pProp->Set(g_PixelType_Mono8); break;
		case XI_RAW8:
			pProp->Set(g_PixelType_Raw8); break;
		case XI_MONO16:
			switch(adc_)
			{
				case 10 : pProp->Set(g_PixelType_Mono10); break;
				case 12 : pProp->Set(g_PixelType_Mono12); break;
				case 14 : pProp->Set(g_PixelType_Mono14); break;
			}
			break;
		case XI_RAW16:
			switch(adc_)
			{
				case 10 : pProp->Set(g_PixelType_Raw10); break;
				case 12 : pProp->Set(g_PixelType_Raw12); break;
				case 14 : pProp->Set(g_PixelType_Raw14); break;
			}
			break;
		case XI_RGB32:
			pProp->Set(g_PixelType_RGB32); break;
		default:
			assert(false); // this should never happen
		}		
	}

	return DEVICE_OK;
}

/***********************************************************************
* Handles "SensorTaps" property.
*/
int XIMEACamera::OnSensorTaps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		long tapCnt;
		pProp->Get(tapCnt);
		tapcnt_ = (int)tapCnt;
		ret = xiSetParamInt( handle, XI_PRM_SENSOR_TAPS, tapcnt_);
		ResizeImageBuffer();
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)tapcnt_);
	}

	return ret;
}

/***********************************************************************
* Handles "Gain" property.
*/
int XIMEACamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		pProp->Get(gain_);
		xiSetParamFloat( handle, XI_PRM_GAIN, (float)gain_);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(gain_);
	}

	return DEVICE_OK;
}

/***********************************************************************
* Handles "Acq. timeout" property.
*/
int XIMEACamera::OnAcqTout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
		pProp->Get(acqTout_);
	
	else if (eAct == MM::BeforeGet)
		pProp->Set(acqTout_);
	
	return DEVICE_OK;
}

/***********************************************************************
* Handles "Trigger mode" property.
*/
int XIMEACamera::OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		int ret = DEVICE_OK;
		string val;
		pProp->Get(val);
		if (val.compare(g_Trigger_Off) == 0){
			ret = xiSetParamInt( handle, XI_PRM_TRG_SOURCE, XI_TRG_OFF);
			isTrg_=false;
		}else if (val.compare(g_HW_Rising) == 0){
			ret = xiSetParamInt( handle, XI_PRM_TRG_SOURCE, XI_TRG_EDGE_RISING);
			isTrg_=true;
		}else if (val.compare(g_HW_Falling) == 0){
			ret = xiSetParamInt( handle, XI_PRM_TRG_SOURCE, XI_TRG_EDGE_FALLING);
			isTrg_=true;
		}else
			assert(false);

		return ret;
	}
	else if (eAct == MM::BeforeGet)
	{
		int trgMd = XI_TRG_OFF;
		xiGetParamInt( handle, XI_PRM_TRG_SOURCE, &trgMd);
		
		switch(trgMd)
		{
		case XI_TRG_OFF          : pProp->Set(g_Trigger_Off); break;
		case XI_TRG_EDGE_RISING  : pProp->Set(g_HW_Rising); break;
		case XI_TRG_EDGE_FALLING : pProp->Set(g_HW_Falling); break;
		default: assert(false); // this should never happen
		}
	}

	return DEVICE_OK;
}

/***********************************************************************
* Handles "GPI mode" property.
*/

int setGpiMode(void* handle, int portNum, string val)
{
	int ret = XI_OK;
	xiSetParamInt( handle, XI_PRM_GPI_SELECTOR, portNum);

	if(strcmp(val.c_str(), g_Gpi_Off) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPI_MODE, XI_GPI_OFF);
	else if(strcmp(val.c_str(), g_Gpi_Trigger) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPI_MODE, XI_GPI_TRIGGER);
	else
		assert(false); // this should never happen
	return ret;
}

string getGpiMode(void* handle, int portNum)
{
	int mode = 0;
	string val = "";
	xiSetParamInt( handle, XI_PRM_GPI_SELECTOR, portNum);
	xiGetParamInt( handle, XI_PRM_GPI_MODE, &mode);

	if(mode == XI_GPI_OFF)
		val = g_Gpi_Off;
	else if(mode == XI_GPI_TRIGGER)
		val = g_Gpi_Trigger;
	else
		assert(false); // this should never happen
	return val;
}

int XIMEACamera::OnGpi1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpiMode(handle, 1, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpiMode( handle, 1).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpi2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpiMode(handle, 2, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpiMode( handle, 2).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpi3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpiMode(handle, 3, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpiMode( handle, 3).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpi4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpiMode(handle, 4, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpiMode( handle, 4).c_str());
	}
	return ret;

}

/***********************************************************************
* Handles "GPO mode" property.
*/
int setGpoMode(void* handle, int portNum, string val)
{
	int ret = XI_OK;
	xiSetParamInt( handle, XI_PRM_GPO_SELECTOR, portNum);

	if(strcmp(val.c_str(), g_Gpo_Off) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_OFF);
	else if(strcmp(val.c_str(), g_Gpo_On) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_ON);
	else if(strcmp(val.c_str(), g_Gpo_FrameActive) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_ACTIVE);
	else if(strcmp(val.c_str(), g_Gpo_FrameActiveNeg) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_ACTIVE_NEG);
	else if(strcmp(val.c_str(), g_Gpo_ExpActive) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_EXPOSURE_ACTIVE);
	else if(strcmp(val.c_str(), g_Gpo_ExpActiveNeg) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_EXPOSURE_ACTIVE_NEG);
	else if(strcmp(val.c_str(), g_Gpo_FrmTrgWait) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_TRIGGER_WAIT);		
	else if(strcmp(val.c_str(), g_Gpo_FrmTrgWaitNeg) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_FRAME_TRIGGER_WAIT_NEG);		
	else if(strcmp(val.c_str(), g_Gpo_ExpPulse) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_EXPOSURE_PULSE);
	else if(strcmp(val.c_str(), g_Gpo_ExpPulseNeg) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_EXPOSURE_PULSE_NEG);
	else if(strcmp(val.c_str(), g_Gpo_Busy) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_BUSY);
	else if(strcmp(val.c_str(), g_Gpo_BusyNeg) == 0)
		ret = xiSetParamInt( handle, XI_PRM_GPO_MODE, XI_GPO_BUSY_NEG);
	else
		assert(false); // this should never happen
	return ret;
}

string getGpoMode(void* handle, int portNum)
{
	int mode = 0;
	string val = "";
	xiSetParamInt( handle, XI_PRM_GPO_SELECTOR, portNum);
	xiGetParamInt( handle, XI_PRM_GPO_MODE, &mode);

	switch(mode)
	{
		case XI_GPO_OFF                     : val = g_Gpo_Off; break;
		case XI_GPO_ON                      : val = g_Gpo_On; break;
		case XI_GPO_FRAME_ACTIVE            : val = g_Gpo_FrameActive; break;
		case XI_GPO_FRAME_ACTIVE_NEG        : val = g_Gpo_FrameActiveNeg; break;
		case XI_GPO_EXPOSURE_ACTIVE         : val = g_Gpo_ExpActive; break;
		case XI_GPO_EXPOSURE_ACTIVE_NEG     : val = g_Gpo_ExpActiveNeg; break;
		case XI_GPO_FRAME_TRIGGER_WAIT      : val = g_Gpo_FrmTrgWait; break;
		case XI_GPO_FRAME_TRIGGER_WAIT_NEG  : val = g_Gpo_FrmTrgWaitNeg; break;
		case XI_GPO_EXPOSURE_PULSE          : val = g_Gpo_ExpPulse; break;
		case XI_GPO_EXPOSURE_PULSE_NEG      : val = g_Gpo_ExpPulseNeg; break;
		case XI_GPO_BUSY                    : val = g_Gpo_Busy; break;
		case XI_GPO_BUSY_NEG                : val = g_Gpo_BusyNeg; break;		
		default: assert(false); // this should never happen
	
	}
	return val;
}

int XIMEACamera::OnGpo1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpoMode(handle, 1, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpoMode( handle, 1).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpo2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpoMode(handle, 2, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpoMode( handle, 2).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpo3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpoMode(handle, 3, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpoMode( handle, 3).c_str());
	}
	return ret;
}

int XIMEACamera::OnGpo4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		ret = setGpoMode(handle, 4, val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(getGpoMode( handle, 4).c_str());
	}
	return ret;
}

/***********************************************************************
* Handles "WB red" property.
*/
int XIMEACamera::OnWbRed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat(handle, XI_PRM_WB_KR, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat( handle, XI_PRM_WB_KR, &val);
		pProp->Set((double) val);
	}
	return ret;
}

/***********************************************************************
* Handles "WB green" property.
*/
int XIMEACamera::OnWbGreen(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat(handle, XI_PRM_WB_KG, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat( handle, XI_PRM_WB_KG, &val);
		pProp->Set((double) val);
	}
	return ret;
}

/***********************************************************************
* Handles "WB blue" property.
*/
int XIMEACamera::OnWbBlue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat(handle, XI_PRM_WB_KB, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat( handle, XI_PRM_WB_KB, &val);
		pProp->Set((double) val);
	}
	return ret;
}

/***********************************************************************
* Handles "AWB" property.
*/
int XIMEACamera::OnAWB(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	string val;
	if (eAct == MM::AfterSet)
	{
		pProp->Get(val);
		if(strcmp(val.c_str(), g_AWB_On) == 0){
			ret = xiSetParamInt( handle, XI_PRM_AUTO_WB, 1);
		} else if(strcmp(val.c_str(), g_AWB_Off) == 0){
			ret = xiSetParamInt( handle, XI_PRM_AUTO_WB, 0);
		} else
			assert(false);
	}
	else if (eAct == MM::BeforeGet)
	{
		int awb = 0;
		xiGetParamInt( handle, XI_PRM_AUTO_WB, &awb);
		if(awb) val = g_AWB_On;
		else    val = g_AWB_Off;
		pProp->Set(val.c_str());
	}
	return ret;
}

/***********************************************************************
* Handles "GammaY" property.
*/
int XIMEACamera::OnGammaY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val = 0;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_GAMMAY, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_GAMMAY, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "GammaC" property.
*/
int XIMEACamera::OnGammaC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val = 0;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_GAMMAC, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_GAMMAC, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Sharpness" property.
*/
int XIMEACamera::OnSharpness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val = 0;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_SHARPNESS, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_SHARPNESS, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Color correction matrix" property.
*/
int XIMEACamera::OnCcMatrix(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		char buf[32] = "";
		char * pch;
		
		string val = "";
		pProp->Get(val);
		
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 4; j++){
				if(!i && !j) pch = strtok ((char*)val.c_str(),"|");
				else 		 pch = strtok (NULL, "|");
				if(pch == NULL) return DEVICE_INVALID_PROPERTY_VALUE;
				sprintf( buf, "ccMTX%d%d", i, j);
				ret = xiSetParamFloat( handle, buf,(float)atof(pch)); 
				if(ret != XI_OK) break;
			}
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		string ccmtx = "";
		for(int i = 0; i < 4; i++){
			for(int j = 0; j < 4; j++){
				char buf[32] = "";
				float val = 0;
				sprintf( buf, "ccMTX%d%d", i, j);
				xiGetParamFloat( handle, buf, &val);
				sprintf( buf, "%.2f|", val);
				ccmtx.append(buf);
			}
		}
		ccmtx.erase(ccmtx.length() - 1);
		pProp->Set(ccmtx.c_str());
	}

	return ret;
}

/***********************************************************************
* Handles "Auto exposure/gain" property.
*/
int XIMEACamera::OnAEAG(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		if(strcmp(val.c_str(), g_AEAG_On) == 0)
			ret = xiSetParamInt( handle, XI_PRM_AEAG, 1);
		else if(strcmp(val.c_str(), g_AEAG_Off) == 0)
			ret = xiSetParamInt( handle, XI_PRM_AEAG, 0);
		else
			assert(false);
	}
	else if (eAct == MM::BeforeGet)
	{
		int val = 0;
		xiGetParamInt( handle, XI_PRM_AEAG, &val);
		if(val) pProp->Set(g_AEAG_On);
		else    pProp->Set(g_AEAG_Off);
	}
	return ret;
}

/***********************************************************************
* Handles "Auto exposure/gain exposure priority" property.
*/
int XIMEACamera::OnExpPrio(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_EXP_PRIORITY, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_EXP_PRIORITY, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Auto exposure/gain exposure limit" property.
*/
int XIMEACamera::OnExpLim(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_AE_MAX_LIMIT, (float)(val*1000));
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_AE_MAX_LIMIT, &val);
		pProp->Set(val/1000);
	}
	return ret;
}

/***********************************************************************
* Handles "Auto exposure/gain gain limit" property.
*/
int XIMEACamera::OnGainLim(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_AG_MAX_LIMIT, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_AG_MAX_LIMIT, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Auto exposure/gain maximum intensity level" property.
*/
int XIMEACamera::OnLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_AEAG_LEVEL, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_AEAG_LEVEL, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Bad pixel correction" property.
*/
int XIMEACamera::OnBpc(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		if(strcmp(val.c_str(), g_BPC_On) == 0)
			ret = xiSetParamInt( handle, XI_PRM_BPC, 1);
		else if(strcmp(val.c_str(), g_BPC_Off) == 0)
			ret = xiSetParamInt( handle, XI_PRM_BPC, 0);
		else
			assert(false);
	}
	else if (eAct == MM::BeforeGet)
	{
		int val = 0;
		xiGetParamInt( handle, XI_PRM_BPC, &val);
		if(val) pProp->Set(g_BPC_On);
		else    pProp->Set(g_BPC_Off);
	}
	return ret;
}


/***********************************************************************
* Handles "Cooling" property.
*/
int XIMEACamera::OnCooling(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		string val;
		pProp->Get(val);
		if(strcmp(val.c_str(), g_Cooling_On) == 0)
			ret = xiSetParamInt( handle, XI_PRM_COOLING, 1);
		else if(strcmp(val.c_str(), g_Cooling_Off) == 0)
			ret = xiSetParamInt( handle, XI_PRM_COOLING, 0);
		else
			assert(false);
	}
	else if (eAct == MM::BeforeGet)
	{
		int val = 0;
		xiGetParamInt( handle, XI_PRM_COOLING, &val);
		if(val) pProp->Set(g_Cooling_On);
		else    pProp->Set(g_Cooling_Off);
	}
	return ret;
}

/***********************************************************************
* Handles "Cooling target temperature" property.
*/
int XIMEACamera::OnTargetTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	
	if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		ret = xiSetParamFloat( handle, XI_PRM_TARGET_TEMP, (float)val);
	}
	else if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_TARGET_TEMP, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Cooling chip temperature" property.
*/
int XIMEACamera::OnChipTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_CHIP_TEMP, &val);
		pProp->Set(val);
	}
	return ret;
}

/***********************************************************************
* Handles "Cooling housing temperature" property.
*/
int XIMEACamera::OnHousTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	
	if (eAct == MM::BeforeGet)
	{
		float val = 0;
		xiGetParamFloat(handle, XI_PRM_HOUS_TEMP, &val);
		pProp->Set(val);
	}
	return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Private XIMEACamera methods
///////////////////////////////////////////////////////////////////////////////

/***********************************************************************
* Sync internal image buffer size to the chosen property values.
*/
int XIMEACamera::ResizeImageBuffer()
{
	int width = 0, height = 0, frm = 0;
	xiGetParamInt( handle, XI_PRM_WIDTH, &width);
	xiGetParamInt( handle, XI_PRM_HEIGHT, &height);
	xiGetParamInt( handle, XI_PRM_IMAGE_DATA_FORMAT, &frm);
	switch(frm)
	{
	case XI_MONO8  : 
	case XI_RAW8   : bytesPerPixel_ = 1; break;
	case XI_MONO16 : 
	case XI_RAW16  : bytesPerPixel_ = 2; break;
	case XI_RGB32  : bytesPerPixel_ = 4; break;
	default: assert(false); // this should never happen
	}

	img_->Resize(width, height, bytesPerPixel_);
		
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Image acquisition thread
///////////////////////////////////////////////////////////////////////////////

MySequenceThread::MySequenceThread(XIMEACamera* pCam)
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

//***********************************************************************

MySequenceThread::~MySequenceThread() {};

//***********************************************************************

void MySequenceThread::Stop() {
   MMThreadGuard(this->stopLock_);
   stop_=true;
}

//***********************************************************************

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

//***********************************************************************

bool MySequenceThread::IsStopped(){
   MMThreadGuard(this->stopLock_);
   return stop_;
}

//***********************************************************************

void MySequenceThread::Suspend() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = true;
}

//***********************************************************************

bool MySequenceThread::IsSuspended() {
   MMThreadGuard(this->suspendLock_);
   return suspend_;
}

//***********************************************************************

void MySequenceThread::Resume() {
   MMThreadGuard(this->suspendLock_);
   suspend_ = false;
}

//***********************************************************************

int MySequenceThread::svc(void) throw()
{
   int ret=DEVICE_ERR;
   try 
   {
      do
      {  
         ret = camera_->ThreadRun();
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
