/*
//////////////////////////////////////////////////////////////////////////////
// FILE:          BAslerAce.cpp
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device Adapter for Basler Ace Camera
//
// Copyright 2018 Henry Pinkard
// Copyright 2019 SMA extended for supporting Bayer,Mono12, Mono16 and  RGB formats
// Copyright 2019 SMA add binning support
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
// list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice, this
// list of conditions and the following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors may
// be used to endorse or promote products derived from this software without specific
// prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
// SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
// ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.

sma : 28.04.2019 Take some changes to be able to compile in Linux
sma : 04.05.2019 Bugfix in 12bit image format and add parameter Sensor Width and Height
sma : 06.05.2019 Improvement in Gain range handling. In some camera model the gain range is depends on selected pixel format.
sma : 22.05.2019 prepared for Mac build
sma : 06.03.2020 pylon version has been switched to V 6.1
sma : 06.03.2020 camera class has been switched to CBaslerUniversalInstantCamera but not all code lines rewritten. In future you profit from the advantage of CBaslerUniversalInstantCamera for sure.
iei : 08.06.2020 add support for additional camera properties; initialize camera by serial number
iei : 18.06.2020 added trigger source property, removing hard-coded Line 1; fixed bug with binnigFactor
sma : 20.06.2020 project and cpp files name has been renamed
sma : 23.06.2020 old project and cpp files name has been removed
sma : 24.04.2020 Drop down binning values on main GUI working properly. issue with Compiler issue with VS2010 fixed.
sma : 15.07.2020 issue in saving config file fixed
sma : 20.07.2020 Variable type of 
sma : 04.08.2020 scope removed in enums
*/



#include <pylon/PylonIncludes.h>
// Include file to use pylon universal instant camera parameters.
#include <pylon/BaslerUniversalInstantCamera.h>


// Namespace for using pylon objects.
using namespace Pylon;
// Namespace for using pylon universal instant camera parameters.
using namespace Basler_UniversalCameraParams;
using namespace GenApi;
using namespace GenICam;

#include "BaslerPylonCamera.h"
#include <sstream>
#include <math.h>
#include "ModuleInterface.h"
#include "DeviceUtils.h"
#include <vector>


#ifdef PYLON_UNIX_BUILD
typedef int BOOL;
#define TRUE 1
#define FALSE 0 

#ifndef _LINUX_STDDEF_H
#define _LINUX_STDDEF_H

#undef NULL
#if defined(__cplusplus)
#define NULL 0
#else
#define NULL ((void *)0)
#endif
#endif
#endif


using namespace std;

const char* g_BaslerCameraDeviceName = "BaslerCamera";

static const char* g_PropertyChannel = "PropertyNAme";
static const char* g_PixelType_8bit = "8bit mono";
static const char* g_PixelType_10bit = "10bit mono";
static const char* g_PixelType_12bit = "12bit mono";
static const char* g_PixelType_16bit = "16bit mono";
static const char* g_PixelType_10packedbit = "10bit mono";
static const char* g_PixelType_12packedbit = "12bit mono";


static const char* g_PixelType_8bitRGBA = "8bitBGRA";
static const  char* g_PixelType_8bitRGB = "8bitRGB";
static const  char* g_PixelType_8bitBGR = "8bitBGR";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////




MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_BaslerCameraDeviceName, MM::CameraDevice, "Basler  Camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_BaslerCameraDeviceName) == 0) {
		// create camera
		return new BaslerCamera();
	}
	// ...supplied name not recognized
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// BitFlowCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* Constructor.
*/
BaslerCamera::BaslerCamera() :
	CCameraBase<BaslerCamera>(),
	maxWidth_(0),
	maxHeight_(0),
	exposure_us_(0),
	exposureMax_(0),
	exposureMin_(0),
	gainMax_(0),
	gainMin_(0),
	bitDepth_(8),
	temperatureState_("Undefined"),
	reverseX_("0"),
	reverseY_("0"),
	imgBuffer_(NULL),
	Buffer4ContinuesShot(NULL),
	colorCamera_(true),
	pixelType_("Undefined"),
	sensorReadoutMode_("Undefined"),
	shutterMode_("None"),
	nodeMap_(NULL),
	initialized_(false),
	pTempHandler_(NULL),
	camera_(NULL)
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_SERIAL_NUMBER_REQUIRED, "Serial number is required");
	SetErrorText(ERR_SERIAL_NUMBER_NOT_FOUND, "No camera with the given serial number was found");
	SetErrorText(ERR_CANNOT_CONNECT, "Cannot connect to camera; it may be in use");

	CreateStringProperty("SerialNumber", "Undefined", false, 0, true);

	//pre-init properties
	PylonInitialize(); // Initialize/Terminate is reference counted by Pylon

					   // Get the available cameras. TODO: This can be very slow and perhaps the
					   // result should be cached.
					  //  or setting up TL Filter, currently it enumerates all pylon TL, eg. GigE , USB Camemu, CXP, CL etc..
	DeviceInfoList_t devices;
	if (CTlFactory::GetInstance().EnumerateDevices(devices) == 0)
	{
		AddToLog("No camera present.");
		PylonTerminate();
		throw RUNTIME_EXCEPTION("No camera present.");
	}

	bool first = true;
	for (DeviceInfoList_t::const_iterator it = devices.begin(), end = devices.end();
		it != end;
		++it)
	{
		const CDeviceInfo& device = *it;
		String_t s = device.GetSerialNumber();
		AddAllowedValue("SerialNumber", s.c_str());

		if (first)
		{
			SetProperty("SerialNumber", s.c_str());
			first = false;
		}
	}

	PylonTerminate();
}

BaslerCamera::~BaslerCamera()
{
	if (imgBuffer_ != NULL)
	{
		free(imgBuffer_);
	}
	if (Buffer4ContinuesShot != NULL)
	{
		free(Buffer4ContinuesShot);
	}
}

/**
* Obtains device name.
*/
void BaslerCamera::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_BaslerCameraDeviceName);
}
/// <summary>
/// convert the EDeviceAccessiblityInfo to string 
/// </summary>
/// <param name="AccessiblityInfo"></param>
/// <returns></returns>
std::string BaslerCamera::EnumToString(EDeviceAccessiblityInfo AccessiblityInfo)
{
	switch (AccessiblityInfo)
	{
	case Accessibility_Unknown:
		return "Unknown Device Status";
	case Accessibility_Ok:
		return "Device  is ready to be open";
	case Accessibility_Opened:
		return "Device  is ready to be open";
	case Accessibility_OpenedExclusively:
		return  "Device is currently opened exclusively by another application ";
	case Accessibility_NotReachable:
		return  "Device is not reachable ";
	}
	return "Unknown";
}

/**
* Initializes the hardware.
*/
int BaslerCamera::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	try
	{
		// Before using any pylon methods, the pylon runtime must be initialized. 
		PylonInitialize();

		char serialNumber[MM::MaxStrLength];
		GetProperty("SerialNumber", serialNumber);
		if (strlen(serialNumber) == 0 || strcmp(serialNumber, "Undefined") == 0)
			return ERR_SERIAL_NUMBER_REQUIRED;

		CDeviceInfo deviceInfo;
		deviceInfo.SetSerialNumber(String_t(serialNumber));

		// Get the transport layer factory.
		CTlFactory& tlFactory = CTlFactory::GetInstance();
		initialized_ = false;

		// This checks, among other things, that the camera is not already in use.
		// Without that check, the following CreateDevice() may crash on duplicate
		// serial number. Unfortunately, this call is slow.
		Pylon::EDeviceAccessiblityInfo isAccessableInfo;

		if (!tlFactory.IsDeviceAccessible(deviceInfo, Control, &isAccessableInfo))
		{
			AddToLog("trying to open camera with SN " + std::string(deviceInfo.GetSerialNumber()));
			AddToLog("The current state of selected camera " + EnumToString(isAccessableInfo));
			return ERR_CANNOT_CONNECT;
		}

		IPylonDevice* device = tlFactory.CreateDevice(deviceInfo);
		if (!device)
		{
			return ERR_CANNOT_CONNECT;
		}
		else
		{
			if (camera_ && camera_->IsPylonDeviceAttached())
			{
				camera_->DestroyDevice();
			}
			camera_ = new CBaslerUniversalInstantCamera(device);
			initialized_ = true;
		}


		stringstream msg;
		msg << "using camera " << camera_->GetDeviceInfo().GetFriendlyName();
		AddToLog(msg.str());
		// initialize the pylon image formatter.
		converter = new CImageFormatConverter();
		converter->OutputPixelFormat = PixelType_BGRA8packed;

		// Name
		int ret = CreateProperty(MM::g_Keyword_Name, g_BaslerCameraDeviceName, MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		// Description
		ret = CreateProperty(MM::g_Keyword_Description, "Basler Camera device adapter", MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		// Serial Number
		ret = CreateProperty(MM::g_Keyword_CameraID, String_t(serialNumber), MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		//Pylon::String_t modelName = camera_->GetDeviceInfo().GetModelName();
		//Get information about camera (e.g. height, width, byte depth)
		//check if given Camera support event.
		camera_->GrabCameraEvents = true;

		camera_->Open();
		// Get the camera nodeMap_ object.
		nodeMap_ = &camera_->GetNodeMap();


		if (camera_->EventSelector.IsWritable())
		{
			if (camera_->EventSelector.CanSetValue(EventSelector_CriticalTemperature) &&
				camera_->EventSelector.CanSetValue(EventSelector_OverTemperature)
				)
			{
				pTempHandler_ = new CTempCameraEventHandler(this);
				// register camera events
				camera_->RegisterCameraEventHandler(pTempHandler_, "EventCriticalTemperatureData", TempCritical, RegistrationMode_Append, Cleanup_None);
				camera_->RegisterCameraEventHandler(pTempHandler_, "EventOverTemperatureData", TempOverTemp, RegistrationMode_Append, Cleanup_None);
			}
		}

		//Register Genicam Callback to be informed if on any changes on  resulting frame rate. 
		if (IsAvailable(camera_->ResultingFrameRate))
		{
			GenApi::Register(camera_->ResultingFrameRate.GetNode(), *this, &BaslerCamera::ResultingFramerateCallback);
		}
		else if (IsAvailable(camera_->ResultingFrameRateAbs))
		{
			//camera_->ResultingFrameRateAbs.ImposeMin(1);
			GenApi::Register(camera_->ResultingFrameRateAbs.GetNode(), *this, &BaslerCamera::ResultingFramerateCallback);
		}

		//Register Camera events

		if (IsAvailable(camera_->DeviceTemperature))
		{
			stringstream strMsg;
			strMsg << "current device Temperature " << camera_->DeviceTemperature.ToString();
			AddToLog(strMsg.str());
			if (camera_->EventSelector.TrySetValue(EventSelector_CriticalTemperature))
			{
				camera_->EventSelector.SetValue(EventSelector_CriticalTemperature);
				camera_->EventNotification.SetValue(EventNotification_On);
			}
			if (camera_->EventSelector.TrySetValue(EventSelector_OverTemperature))
			{
				camera_->EventSelector.SetValue(EventSelector_OverTemperature);
				camera_->EventNotification.SetValue(EventNotification_On);
			}
		}


		//Sensor size
		const CIntegerPtr width = nodeMap_->GetNode("Width");
		// maxWidth_ = (unsigned int) width->GetMax();
		maxWidth_ = (unsigned int)CIntegerPtr(nodeMap_->GetNode("WidthMax"))->GetValue();
		const CIntegerPtr height = nodeMap_->GetNode("Height");
		// maxHeight_ = (unsigned int) height->GetMax();
		maxHeight_ = (unsigned int)CIntegerPtr(nodeMap_->GetNode("HeightMax"))->GetValue();


		if (IsAvailable(width))
		{
			CPropertyAction* pAct = new CPropertyAction(this, &BaslerCamera::OnWidth);
			ret = CreateProperty("SensorWidth", CDeviceUtils::ConvertToString((int)width->GetValue()), MM::Integer, false, pAct);
			SetPropertyLimits("SensorWidth", (double)width->GetMin(), (double)width->GetMax());
			assert(ret == DEVICE_OK);
		}
		if (IsAvailable(height))
		{
			CPropertyAction* pAct = new CPropertyAction(this, &BaslerCamera::OnHeight);
			ret = CreateProperty("SensorHeight", CDeviceUtils::ConvertToString((int)height->GetValue()), MM::Integer, false, pAct);
			SetPropertyLimits("SensorHeight", (double)height->GetMin(), (double)height->GetMax());
			assert(ret == DEVICE_OK);
		}

		//end of Sensor size


#if (!_DEBUG)
		ClearROI();// to be enabled for release
#else 
		{
			ReduceImageSize(200, 200);
			if (camera_->IsGigE())
			{
				CIntegerPtr(camera_->GetTLNodeMap().GetNode("HeartbeatTimeout"))->SetValue(24 * 1000);
			}
		}
		if (IsAvailable(camera_->TestImageSelector))
		{
			camera_->TestImageSelector.TrySetValue(TestImageSelector_Testimage4);
		}
#endif


		long bytes = (long)(height->GetValue() * width->GetValue() * 4);
		Buffer4ContinuesShot = malloc(bytes);


		//Exposure
		CFloatPtr exposure(nodeMap_->GetNode("ExposureTime"));
		CFloatPtr ExposureTimeAbs(nodeMap_->GetNode("ExposureTimeAbs"));
		if (IsAvailable(exposure))
		{
			// USB cameras
			exposure_us_ = exposure->GetValue();
			exposureMax_ = exposure->GetMax();
			exposureMin_ = exposure->GetMin();

		}
		else if (IsAvailable(ExposureTimeAbs))
		{   // GigE
			exposure_us_ = ExposureTimeAbs->GetValue();
			exposureMax_ = ExposureTimeAbs->GetMax();
			exposureMin_ = ExposureTimeAbs->GetMin();
		}
	/*	CPropertyAction* pAct = new CPropertyAction(this, &BaslerCamera::OnExposure);
		ret = CreateProperty("Exposure", CDeviceUtils::ConvertToString((long)exposure->GetValue()), MM::Float, false, pAct);
		SetPropertyLimits("Exposure", exposureMin_, exposureMax_);
		assert(ret == DEVICE_OK);*/


		//Pixel type
		CPropertyAction* pAct = new CPropertyAction(this, &BaslerCamera::OnPixelType);
		ret = CreateProperty(MM::g_Keyword_PixelType, "NA", MM::String, false, pAct);
		assert(ret == DEVICE_OK);

		vector<string> pixelTypeValues;

		CEnumerationPtr pixelFormat(nodeMap_->GetNode("PixelFormat"));
		if (IsAvailable(pixelFormat->GetEntryByName("Mono10"))) {
			pixelTypeValues.push_back("Mono10");
			pixelFormat->FromString("Mono10");
			pixelType_ = "Mono10";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("Mono12"))) {
			pixelTypeValues.push_back("Mono12");
			pixelFormat->FromString("Mono12");
			pixelType_ = "Mono12";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("Mono16"))) {
			pixelTypeValues.push_back("Mono16");
			pixelFormat->FromString("Mono16");
			pixelType_ = "Mono16"; //default to using highest bit depth
		}
		if (IsAvailable(pixelFormat->GetEntryByName("Mono8"))) {
			pixelTypeValues.push_back("Mono8");
			pixelFormat->FromString("Mono8");
			pixelType_ = "Mono8";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("BGR8"))) {
			pixelTypeValues.push_back("BGR8");
			pixelFormat->FromString("BGR8");
			pixelType_ = "BGR8";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("RGB8"))) {
			pixelTypeValues.push_back("RGB8");
			pixelFormat->FromString("RGB8");
			pixelType_ = "RGB8";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("BayerRG8"))) {
			pixelTypeValues.push_back("BayerRG8");
			pixelFormat->FromString("BayerRG8");
			pixelType_ = "BayerRG8";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("BayerBG8"))) {
			pixelTypeValues.push_back("BayerBG8");
			pixelFormat->FromString("BayerBG8");
			pixelType_ = "BayerBG8";
		}
		if (IsAvailable(pixelFormat->GetEntryByName("BayerGR8"))) {
			pixelTypeValues.push_back("BayerGR8");
			pixelFormat->FromString("BayerGR8");
			pixelType_ = "BayerGR8";
		}
		SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);

		/////Temperature//////
		if (IsAvailable(camera_->DeviceTemperature) || IsAvailable(camera_->TemperatureAbs))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnTemperature);
			temperature_ = "N/A";
			ret = CreateProperty("Temperature", temperature_.c_str(), MM::String, true, pAct);
			if (DEVICE_OK != ret)
			{
				return ret;
			}
		}

		/////Temperature Status//////
		if (IsAvailable(camera_->TemperatureState))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnTemperatureState);
			temperatureState_ = "N/A";
			ret = CreateProperty("TemperatureState", temperatureState_.c_str(), MM::String, true, pAct);

			if (DEVICE_OK != ret)
			{
				return ret;
			}
		}

		/////AutoGain//////
		CEnumerationPtr gainAuto(nodeMap_->GetNode("GainAuto"));
		if (IsWritable(gainAuto))
		{

			if (gainAuto != NULL && IsAvailable(gainAuto))
			{
				pAct = new CPropertyAction(this, &BaslerCamera::OnAutoGain);
				ret = CreateProperty("GainAuto", "NA", MM::String, false, pAct);
				vector<string> LSPVals;
				NodeList_t entries;
				LSPVals.push_back("Off");
				gainAuto->GetEntries(entries);
				for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
				{
					CEnumEntryPtr pEnumEntry(*it);
					string strValue = pEnumEntry->GetSymbolic().c_str();
					if (IsAvailable(*it) && strValue != "Off")
					{
						LSPVals.push_back(strValue);
					}
				}
				SetAllowedValues("GainAuto", LSPVals);
			}
		}
		/////AutoExposure//////
		CEnumerationPtr ExposureAuto(nodeMap_->GetNode("ExposureAuto"));
		if (IsWritable(ExposureAuto))
		{

			if (ExposureAuto != NULL && IsAvailable(ExposureAuto))
			{
				pAct = new CPropertyAction(this, &BaslerCamera::OnAutoExpore);
				ret = CreateProperty("ExposureAuto", "NA", MM::String, false, pAct);
				vector<string> LSPVals;
				NodeList_t entries;
				LSPVals.push_back("Off");
				ExposureAuto->GetEntries(entries);
				for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
				{
					CEnumEntryPtr pEnumEntry(*it);
					string strValue = pEnumEntry->GetSymbolic().c_str();
					if (IsAvailable(*it) && strValue != "Off")
					{
						LSPVals.push_back(strValue);
					}
				}
				SetAllowedValues("ExposureAuto", LSPVals);
			}
		}

		//get gain limits and value
		CFloatPtr gain(nodeMap_->GetNode("Gain"));
		CIntegerPtr GainRaw(nodeMap_->GetNode("GainRaw"));

		if (IsAvailable(gain))
		{
			gainMax_ = gain->GetMax();
			gainMin_ = gain->GetMin();
			gain_ = gain->GetValue();
		}
		else if (IsAvailable(GainRaw))
		{
			gainMax_ = (double)GainRaw->GetMax();
			gainMin_ = (double)GainRaw->GetMin();
			gain_ = (double)GainRaw->GetValue();
		}


		//make property
		pAct = new CPropertyAction(this, &BaslerCamera::OnGain);
		ret = CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
		SetPropertyLimits(MM::g_Keyword_Gain, gainMin_, gainMax_);

		/////Offset//////
		CFloatPtr BlackLevel(nodeMap_->GetNode("BlackLevel"));
		CIntegerPtr BlackLevelRaw(nodeMap_->GetNode("BlackLevelRaw"));

		if (IsAvailable(BlackLevel))
		{
			offsetMax_ = BlackLevel->GetMax();
			offsetMin_ = BlackLevel->GetMin();
			offset_ = BlackLevel->GetValue();

		}
		else if (IsAvailable(BlackLevelRaw))
		{
			offsetMax_ = (double)BlackLevelRaw->GetMax();
			offsetMin_ = (double)BlackLevelRaw->GetMin();
			offset_ = (double)BlackLevelRaw->GetValue();
		}


		//make property
		pAct = new CPropertyAction(this, &BaslerCamera::OnOffset);
		ret = CreateProperty(MM::g_Keyword_Offset, "1.0", MM::Float, false, pAct);
		SetPropertyLimits(MM::g_Keyword_Offset, offsetMin_, offsetMax_);

		////Sensor readout//////
		if (IsAvailable(camera_->SensorReadoutMode))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnSensorReadoutMode);
			ret = CreateProperty("SensorReadoutMode", "NA", MM::String, false, pAct);
			vector<string> vals;
			NodeList_t entries;
			camera_->SensorReadoutMode.GetEntries(entries);
			for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
			{
				CEnumEntryPtr pEnumEntry(*it);
				string strValue = pEnumEntry->GetSymbolic().c_str();
				if (IsAvailable(*it) && strValue != "Off")
				{
					vals.push_back(strValue);
				}
			}
			SetAllowedValues("SensorReadoutMode", vals);
		}

		CEnumerationPtr LightSourcePreset(nodeMap_->GetNode("LightSourcePreset"));
		if (LightSourcePreset != NULL && IsAvailable(LightSourcePreset))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnLightSourcePreset);
			ret = CreateProperty("LightSourcePreset", "NA", MM::String, false, pAct);
			vector<string> LSPVals;
			NodeList_t entries;
			LSPVals.push_back("Off");
			LightSourcePreset->GetEntries(entries);
			for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
			{
				CEnumEntryPtr pEnumEntry(*it);
				string strValue = pEnumEntry->GetSymbolic().c_str();
				if (IsAvailable(*it) && strValue != "Off")
				{
					LSPVals.push_back(strValue);
				}
			}
			SetAllowedValues("LightSourcePreset", LSPVals);
		}


		/////Trigger Mode//////
		CEnumerationPtr TriggerMode(nodeMap_->GetNode("TriggerMode"));
		if (IsAvailable(TriggerMode))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnTriggerMode);
			ret = CreateProperty("TriggerMode", "Off", MM::String, false, pAct);
			vector<string> LSPVals;
			LSPVals.push_back("Off");
			LSPVals.push_back("On");
			SetAllowedValues("TriggerMode", LSPVals);
		}
		/////Trigger Source//////
		CEnumerationPtr triggersource(nodeMap_->GetNode("TriggerSource"));
		if (IsWritable(triggersource))
		{
			if (triggersource != NULL && IsAvailable(triggersource))
			{
				pAct = new CPropertyAction(this, &BaslerCamera::OnTriggerSource);
				ret = CreateProperty("TriggerSource", "NA", MM::String, false, pAct);
				vector<string> LSPVals;
				NodeList_t entries;
				triggersource->GetEntries(entries);
				for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
				{
					CEnumEntryPtr pEnumEntry(*it);
					string strValue = pEnumEntry->GetSymbolic().c_str();
					//Software Execute button not implement yet.
					if (IsAvailable(*it) && strValue.find("Software") == std::string::npos)
					{
						LSPVals.push_back(strValue);
					}
				}
				SetAllowedValues("TriggerSource", LSPVals);
			}
		}
		////Shutter mode//////	
		CEnumerationPtr shutterMode(nodeMap_->GetNode("ShutterMode"));
		if (IsAvailable(shutterMode))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnShutterMode);
			ret = CreateProperty("ShutterMode", "NA", MM::String, false, pAct);
			vector<string> shutterVals;

			if (IsAvailable(shutterMode->GetEntryByName("Global")))
			{
				shutterVals.push_back("Global");
			}
			if (IsAvailable(shutterMode->GetEntryByName("Rolling"))) {
				shutterVals.push_back("Rolling");
			}
			if (IsAvailable(shutterMode->GetEntryByName("GlobalResetRelease"))) {
				shutterVals.push_back("GlobalResetRelease");
			}
			SetAllowedValues("ShutterMode", shutterVals);
		}

		////DeviceLinkThroughputLimit for USB Camera//////

		if (camera_->IsUsb())
		{
			CIntegerPtr DeviceLinkThroughputLimit(nodeMap_->GetNode("DeviceLinkThroughputLimit"));
			if (IsAvailable(DeviceLinkThroughputLimit))
			{
				int64_t val = DeviceLinkThroughputLimit->GetValue();
				pAct = new CPropertyAction(this, &BaslerCamera::OnDeviceLinkThroughputLimit);
				ret = CreateProperty("DeviceLinkThroughputLimit", CDeviceUtils::ConvertToString((long)val), MM::Integer, false, pAct);
				SetPropertyLimits("DeviceLinkThroughputLimit", (double)DeviceLinkThroughputLimit->GetMin(), (double)DeviceLinkThroughputLimit->GetMax());
				assert(ret == DEVICE_OK);
			}
		}
		////Inter packet delay for GigE Camera//////

		if (camera_->IsGigE())
		{
			CIntegerPtr GevSCPD(nodeMap_->GetNode("GevSCPD"));
			if (IsAvailable(GevSCPD))
			{
				pAct = new CPropertyAction(this, &BaslerCamera::OnInterPacketDelay);
				ret = CreateProperty("InterPacketDelay", CDeviceUtils::ConvertToString((long)GevSCPD->GetValue()), MM::Integer, false, pAct);
				SetPropertyLimits("InterPacketDelay", (double)GevSCPD->GetMin(), (double)GevSCPD->GetMax());
				assert(ret == DEVICE_OK);
			}
		}

		/////Reverse X//////
		if (IsAvailable(camera_->ReverseX))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnReverseX);
			ret = CreateProperty("ReverseX", "0", MM::String, false, pAct);
			vector<string> reverseXVals;
			reverseXVals.push_back("0");
			reverseXVals.push_back("1");
			SetAllowedValues("ReverseX", reverseXVals);
		}

		/////Reverse Y//////
		if (IsAvailable(camera_->ReverseY))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnReverseY);
			ret = CreateProperty("ReverseY", "0", MM::String, false, pAct);
			vector<string> reverseYVals;
			reverseYVals.push_back("0");
			reverseYVals.push_back("1");
			SetAllowedValues("ReverseY", reverseYVals);
		}

		//////ResultingFramerate
		if (IsAvailable(camera_->ResultingFrameRate) || IsAvailable(camera_->ResultingFrameRateAbs))
		{
			ResultingFrameRatePrevious = camera_->ResultingFrameRate.GetValueOrDefault(ResultingFrameRatePrevious);
			ResultingFrameRatePrevious = camera_->ResultingFrameRateAbs.GetValueOrDefault(ResultingFrameRatePrevious);
			std::ostringstream oss;
			oss << ResultingFrameRatePrevious;
			pAct = new CPropertyAction(this, &BaslerCamera::OnResultingFramerate);
			ret = CreateProperty("ResultingFrameRate", oss.str().c_str(), MM::String, true, pAct);
			if (DEVICE_OK != ret)
			{
				return ret;
			}

		}
		/////Set Acquisition AcquisitionFrameRateEnable//////
		if (IsAvailable(camera_->AcquisitionFrameRateEnable))
		{
			pAct = new CPropertyAction(this, &BaslerCamera::OnAcqFramerateEnable);
			ret = CreateProperty("AcquisitionFramerateEnable", "0", MM::String, false, pAct);
			vector<string> setAcqFrmVals;
			setAcqFrmVals.push_back("0");
			setAcqFrmVals.push_back("1");
			SetAllowedValues("AcquisitionFramerateEnable", setAcqFrmVals);
		}

		/////Acquisition Frame rate//////
		{
			if (IsAvailable(camera_->AcquisitionFrameRate))
			{
				// it is not necessary to use full range to 
				acqFramerateMax_ = camera_->AcquisitionFrameRate.GetMax();
				acqFramerateMin_ = camera_->AcquisitionFrameRate.GetMin();
				acqFramerate_ = camera_->AcquisitionFrameRate.GetValue();

			}
			else if (IsAvailable(camera_->AcquisitionFrameRateAbs))
			{
				acqFramerateMax_ = camera_->AcquisitionFrameRateAbs.GetMax();
				acqFramerateMin_ = camera_->AcquisitionFrameRateAbs.GetMin();
				acqFramerate_ = camera_->AcquisitionFrameRateAbs.GetValue();

			}
			pAct = new CPropertyAction(this, &BaslerCamera::OnAcqFramerate);
			ret = CreateProperty("AcquisitionFramerate", "100", MM::String, false, pAct);
			//SetPropertyLimits("AcquisitionFramerate", acqFramerateMin_, acqFramerateMax_);
			assert(ret == DEVICE_OK);
		}


		//// binning
		pAct = new CPropertyAction(this, &BaslerCamera::OnBinning);
		ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
		SetPropertyLimits(MM::g_Keyword_Binning, 1, 1);
		assert(ret == DEVICE_OK);

		vector<string> binValues;


		CIntegerPtr BinningHorizontal(nodeMap_->GetNode("BinningHorizontal"));
		CIntegerPtr BinningVertical(nodeMap_->GetNode("BinningVertical"));

		if (IsAvailable(BinningHorizontal) && IsAvailable(BinningVertical))
		{
			//assumed that BinningHorizontal and BinningVertical allow same steps
			int64_t min = BinningHorizontal->GetMin();
			int64_t max = BinningHorizontal->GetMax();
			SetPropertyLimits(MM::g_Keyword_Binning, (double)min, (double)max);

			for (int x = 1; x <= max; x++)
			{
				std::ostringstream oss;
				oss << x;
				binValues.push_back(oss.str());
				AddAllowedValue(MM::g_Keyword_Binning, oss.str().c_str());
			}
			binningFactor_.assign(CDeviceUtils::ConvertToString((long)BinningHorizontal->GetValue()));
			CheckForBinningMode(pAct);
		}
		else
		{
			binValues.push_back("1");
			binningFactor_.assign("1");
		}

		// synchronize all properties
		// --------------------------
		ret = UpdateStatus();
		if (ret != DEVICE_OK)
			return ret;

		//preparation for snaps
		ResizeSnapBuffer();
		//preparation for sequences	
		//camera_->RegisterImageEventHandler( &ImageHandler_, RegistrationMode_Append, Cleanup_Delete);	
		initialized_ = true;
	}
	catch (const GenericException & e)
	{
		// Error handling.
		AddToLog(e.GetDescription());
		cerr << "An exception occurred." << endl
			<< e.GetDescription() << endl;
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}



int BaslerCamera::CheckForBinningMode(CPropertyAction* pAct)
{
	// Binning Mode
	INodeMap& nodeMap(camera_->GetNodeMap());
	CEnumerationPtr BinningModeHorizontal(nodeMap.GetNode("BinningModeHorizontal"));
	CEnumerationPtr BinningModeVertical(nodeMap.GetNode("BinningModeVertical"));
	if (IsAvailable(BinningModeVertical) && IsAvailable(BinningModeHorizontal))
	{
		pAct = new CPropertyAction(this, &BaslerCamera::OnBinningMode);

		vector<string> LSPVals;
		NodeList_t entries;
		// assumed BinningHorizontalMode & BinningVerticalMode same entries
		BinningModeVertical->GetEntries(entries);
		for (NodeList_t::iterator it = entries.begin(); it != entries.end(); ++it)
		{
			CEnumEntryPtr pEnumEntry(*it);
			if (it == entries.begin())
			{
				CreateProperty("BinningMode", pEnumEntry->GetSymbolic().c_str(), MM::String, false, pAct);
			}

			LSPVals.push_back(pEnumEntry->GetSymbolic().c_str());
		}
		SetAllowedValues("BinningMode", LSPVals);
		return DEVICE_OK;
	}
	return DEVICE_CAN_NOT_SET_PROPERTY;
}
/*

int BaslerCamera::SetProperty(const char* name, const char* value)
{
	int nRet = __super::SetProperty( name, value );
	return nRet;
} /*

/**
* Shuts down (unloads) the device.
*/
int BaslerCamera::Shutdown()
{
	if (!camera_)
	{
		camera_->Close();
		delete camera_;
	}
	initialized_ = false;
	PylonTerminate();
	return DEVICE_OK;
}

int BaslerCamera::SnapImage()
{
	try
	{
		camera_->StartGrabbing(1, GrabStrategy_OneByOne, GrabLoop_ProvidedByUser);
		// This smart pointer will receive the grab result data.
		//When all smart pointers referencing a Grab Result Data object go out of scope, the grab result's image buffer is reused for grabbing
		CGrabResultPtr ptrGrabResult;
		int timeout_ms = 5000;
		if (!camera_->RetrieveResult(timeout_ms, ptrGrabResult, TimeoutHandling_ThrowException)) {
			return DEVICE_ERR;
		}
		if (!ptrGrabResult->GrabSucceeded()) {
			return DEVICE_ERR;
		}
		if (ptrGrabResult->GetPayloadSize() != imgBufferSize_)
		{// due to parameter change on  binning
			ResizeSnapBuffer();
		}
		CopyToImageBuffer(ptrGrabResult);

	}
	catch (const GenericException & e)
	{
		// Error handling.
		AddToLog(e.GetDescription());
		cerr << "An exception occurred." << endl
			<< e.GetDescription() << endl;
	}
	return DEVICE_OK;
}

void BaslerCamera::CopyToImageBuffer(CGrabResultPtr ptrGrabResult)
{
	const char* subject("Bayer");
	bool IsByerFormat = false;
	string currentPixelFormat = Pylon::CPixelTypeMapper::GetNameByPixelType(ptrGrabResult->GetPixelType());
	std::size_t found = currentPixelFormat.find(subject);
	if (found != std::string::npos)
	{
		IsByerFormat = true;
	}
	if (ptrGrabResult->GetPixelType() == PixelType_Mono8)
	{
		// Workaround : OnPixelType call back will not be fired always.
		nComponents_ = 1;
		bitDepth_ = 8;

		//copy image buffer to a snap buffer allocated by device adapter
		const void* buffer = ptrGrabResult->GetBuffer();
		memcpy(imgBuffer_, buffer, GetImageBufferSize());
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
	}
	else if (ptrGrabResult->GetPixelType() == PixelType_Mono16 || ptrGrabResult->GetPixelType() == PixelType_Mono10 || ptrGrabResult->GetPixelType() == PixelType_Mono12)
	{
		//copy image buffer to a snap buffer allocated by device adapter
		void* buffer = ptrGrabResult->GetBuffer();
		memcpy(imgBuffer_, buffer, ptrGrabResult->GetPayloadSize());
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_12bit);
	}
	else if (IsByerFormat || ptrGrabResult->GetPixelType() == PixelType_RGB8packed)
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		converter->Convert(imgBuffer_, GetImageBufferSize(), ptrGrabResult);
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bitRGBA);
	}
	else if (ptrGrabResult->GetPixelType() == PixelType_BGR8packed)
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bitBGR);
		RGBPackedtoRGB(imgBuffer_, ptrGrabResult);
	}
}

/**
* Returns pixel data.
*/
const unsigned char* BaslerCamera::GetImageBuffer()
{
	return (unsigned char*)imgBuffer_;
}

unsigned BaslerCamera::GetImageWidth() const
{
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	return(unsigned)width->GetValue();
}

unsigned BaslerCamera::GetImageHeight() const
{
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	return (unsigned)height->GetValue();
}

/**
* Returns image buffer pixel depth in bytes.
*/
unsigned BaslerCamera::GetImageBytesPerPixel() const
{
	const char* subject("Bayer");
	std::size_t found = pixelType_.find(subject);

	if (pixelType_ == "Mono8") {
		return 1;
	}
	else if (pixelType_ == "Mono10" || pixelType_ == "Mono12" || pixelType_ == "Mono16") {
		return 2;
	}
	else if (found != std::string::npos || pixelType_ == "BGR8" || pixelType_ == "RGB8") {
		return 4;
	}
	assert(0); //shouldn't happen
	return 0;
}

/**
* Returns the bit depth (dynamic range) of the pixel.
*/
unsigned BaslerCamera::GetBitDepth() const
{
	const char* subject("Bayer");
	std::size_t found = pixelType_.find(subject);

	if (pixelType_ == "Mono8") {
		return 8;
	}
	else if (pixelType_ == "Mono10") {
		return 10;
	}
	else if (pixelType_ == "Mono12") {
		return 12;
	}
	else if (pixelType_ == "Mono16") {
		return 16;
	}
	else if (found != std::string::npos || pixelType_ == "BGR8" || pixelType_ == "RGB8") {
		return 8;
	}
	assert(0); //shoudlnt happen
	return 0;
}

/**
* Returns the size in bytes of the image buffer.
*/
long BaslerCamera::GetImageBufferSize() const
{
	return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int BaslerCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	const CIntegerPtr offsetX = nodeMap_->GetNode("OffsetX");
	const CIntegerPtr offsetY = nodeMap_->GetNode("OffsetY");
	x -= (x % offsetX->GetInc());
	y -= (y % offsetY->GetInc());
	xSize -= (xSize % width->GetInc());
	ySize -= (ySize % height->GetInc());
	if (xSize < width->GetMin()) {
		xSize = (unsigned int)width->GetMin();
	}
	if (ySize < height->GetMin()) {
		ySize = (unsigned int)height->GetMin();
	}
	if (x < offsetX->GetMin()) {
		x = (unsigned int)offsetX->GetMin();
	}
	if (y < offsetY->GetMin()) {
		y = (unsigned int)offsetY->GetMin();
	}
	width->SetValue(xSize);
	height->SetValue(ySize);
	offsetX->SetValue(x);
	offsetY->SetValue(y);
	return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
*/
int BaslerCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	const CIntegerPtr offsetX = nodeMap_->GetNode("OffsetX");
	const CIntegerPtr offsetY = nodeMap_->GetNode("OffsetY");
	x = (unsigned int)offsetX->GetValue();
	y = (unsigned int)offsetY->GetValue();
	xSize = (unsigned int)width->GetValue();
	ySize = (unsigned int)height->GetValue();
	return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
*/
int BaslerCamera::ClearROI()
{
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	const CIntegerPtr offsetX = nodeMap_->GetNode("OffsetX");
	const CIntegerPtr offsetY = nodeMap_->GetNode("OffsetY");
	offsetX->SetValue(0);
	offsetY->SetValue(0);
	width->SetValue(maxWidth_);
	height->SetValue(maxHeight_);
	return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double BaslerCamera::GetExposure() const
{
	return exposure_us_ / 1000.0;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void BaslerCamera::SetExposure(double exp)
{
	exp *= 1000; //convert to us
	if (exp > exposureMax_) {
		exp = exposureMax_;
	}
	else if (exp < exposureMin_) {
		exp = exposureMin_;
	}

	CFloatPtr ExposureTime(nodeMap_->GetNode("ExposureTime"));
	CIntegerPtr ExposureTimeRaw(nodeMap_->GetNode("ExposureTimeRaw"));
	if (camera_->IsGigE() && IsWritable(ExposureTimeRaw))
	{
		ExposureTimeRaw->SetValue((int64_t)exp);
		exposure_us_ = exp;
	}
	else if (camera_->IsUsb() && IsWritable(ExposureTime))
	{
		ExposureTime->SetValue(exp);
		exposure_us_ = exp;
	}
}

/**
* Returns the current binning factor.
*/
int BaslerCamera::GetBinning() const
{
	return  std::atoi(binningFactor_.c_str());
}

int BaslerCamera::SetBinning(int binFactor)
{
	cout << "SetBinning called\n";
	if (binFactor > 1 && binFactor < 4) {
		return DEVICE_OK;
	}
	return DEVICE_UNSUPPORTED_COMMAND;
}

int BaslerCamera::StartSequenceAcquisition(long numImages, double /* interval_ms */, bool /* stopOnOverflow */) {

	ImageHandler_ = new CircularBufferInserter(this);
	camera_->RegisterImageEventHandler(ImageHandler_, RegistrationMode_Append, Cleanup_Delete);
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK) {
		return ret;
	}
	camera_->StartGrabbing(numImages, GrabStrategy_OneByOne, GrabLoop_ProvidedByInstantCamera);
	return DEVICE_OK;
}

int BaslerCamera::StartSequenceAcquisition(double /* interval_ms */) {
	ImageHandler_ = new CircularBufferInserter(this);
	camera_->RegisterImageEventHandler(ImageHandler_, RegistrationMode_Append, Cleanup_Delete);
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK) {
		return ret;
	}
	camera_->StartGrabbing(GrabStrategy_OneByOne, GrabLoop_ProvidedByInstantCamera);
	return DEVICE_OK;
}

bool BaslerCamera::IsCapturing()
{
	return camera_->IsGrabbing();
}

int BaslerCamera::StopSequenceAcquisition()
{
	if (camera_->IsGrabbing())
	{
		camera_->StopGrabbing();
		GetCoreCallback()->AcqFinished(this, 0);
		camera_->DeregisterImageEventHandler(ImageHandler_);
	}
	return DEVICE_OK;
}

int BaslerCamera::PrepareSequenceAcqusition()
{
	// nothing to prepare
	return DEVICE_OK;
}

void BaslerCamera::ResizeSnapBuffer() {

	free(imgBuffer_);
	long bytes = GetImageBufferSize();
	imgBuffer_ = malloc(bytes);
	imgBufferSize_ = bytes;
}




//////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int BaslerCamera::OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string TriggerSource_;
	if (eAct == MM::AfterSet) {
		pProp->Get(TriggerSource_);
		CEnumerationPtr TriggerSource(nodeMap_->GetNode("TriggerSource"));
		TriggerSource->FromString(TriggerSource_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CEnumerationPtr TriggerSource(nodeMap_->GetNode("TriggerSource"));
		gcstring val = TriggerSource->ToString();
		//camemu has only Software trigger.
		if (val.find("Software") == string::npos)
		{
			const char* s = val.c_str();
			pProp->Set(s);
		}
	}
	return DEVICE_OK;
}

int BaslerCamera::OnBinningMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CEnumerationPtr BinningModeHorizontal(nodeMap_->GetNode("BinningModeHorizontal"));
	CEnumerationPtr BinningModeVertical(nodeMap_->GetNode("BinningModeVertical"));

	if (eAct == MM::AfterSet)
	{
		if (IsAvailable(BinningModeVertical) && IsAvailable(BinningModeVertical))
		{
			try
			{
				string binningMode;
				pProp->Get(binningMode);
				BinningModeHorizontal->FromString(binningMode.c_str());
				BinningModeVertical->FromString(binningMode.c_str());
			}
			catch (const GenericException & e)
			{
				// Error handling.
				AddToLog(e.GetDescription());
				cerr << "An exception occurred." << endl
					<< e.GetDescription() << endl;
			}
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		try {
			if (IsAvailable(BinningModeVertical) && IsAvailable(BinningModeVertical))
			{
				pProp->Set(BinningModeHorizontal->ToString());
			}
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	return DEVICE_OK;
}
int BaslerCamera::OnHeight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CIntegerPtr Height(nodeMap_->GetNode("Height"));
	std::string strval;
	if (eAct == MM::AfterSet)
	{
		bool Isgrabbing = camera_->IsGrabbing();

		if (IsAvailable(Height))
		{
			try
			{
				if (Isgrabbing)
				{
					camera_->StopGrabbing();
				}
				pProp->Get(strval);
				int64_t val = std::atoi(strval.c_str());
				int64_t inc = Height->GetInc();
				Height->SetValue(val - (val % inc));
				if (Isgrabbing)
				{
					camera_->StartGrabbing();
				}
			}
			catch (const GenericException & e)
			{
				// Error handling.
				AddToLog(e.GetDescription());
				cerr << "An exception occurred." << endl
					<< e.GetDescription() << endl;
			}
		}
	}
	else if (eAct == MM::BeforeGet) {

		try {
			if (IsAvailable(Height))
			{
				pProp->Set((long)Height->GetValue());
			}
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	return DEVICE_OK;
}

int BaslerCamera::OnWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CIntegerPtr Width(nodeMap_->GetNode("Width"));
	std::string strval;
	if (eAct == MM::AfterSet)
	{
		bool Isgrabbing = camera_->IsGrabbing();

		if (IsAvailable(Width))
		{
			try
			{
				if (Isgrabbing)
				{
					camera_->StopGrabbing();
				}
				pProp->Get(strval);
				int64_t val = std::atoi(strval.c_str());
				int64_t inc = Width->GetInc();
				Width->SetValue(val - (val % inc));
				if (Isgrabbing)
				{
					camera_->StartGrabbing();
				}
				//pProp->Set(Width->GetValue());

			}
			catch (const GenericException & e)
			{
				// Error handling.
				AddToLog(e.GetDescription());
				cerr << "An exception occurred." << endl
					<< e.GetDescription() << endl;
			}
		}
	}
	else if (eAct == MM::BeforeGet) {
		try {
			if (IsAvailable(Width))
			{
				pProp->Set((long)Width->GetValue());
			}
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	return DEVICE_OK;
}


int BaslerCamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		if (IsWritable(camera_->ExposureTime) || IsWritable(camera_->ExposureTimeAbs))
		{
			try
			{
				pProp->Get(exposure_us_);
				exposure_us_ = camera_->ExposureTime.TrySetValue(exposure_us_);
				exposure_us_ = camera_->ExposureTimeAbs.TrySetValue(exposure_us_);
			}
			catch (const GenericException & e)
			{
				// Error handling.
				AddToLog(e.GetDescription());
				cerr << "An exception occurred." << endl
					<< e.GetDescription() << endl;
			}
		}
	}
	else if (eAct == MM::BeforeGet) {

		try {
			if (IsAvailable(camera_->ExposureTime) && IsAvailable(camera_->ExposureTimeAbs))
			{
				exposure_us_ = camera_->ExposureTime.GetValueOrDefault(exposure_us_);
				exposure_us_ = camera_->ExposureTimeAbs.GetValueOrDefault(exposure_us_);
				pProp->Set(exposure_us_);
			}
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	return DEVICE_OK;
}

int BaslerCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CIntegerPtr BinningHorizontal(nodeMap_->GetNode("BinningHorizontal"));
	CIntegerPtr BinningVertical(nodeMap_->GetNode("BinningVertical"));

	if (eAct == MM::AfterSet)
	{
		bool Isgrabbing = camera_->IsGrabbing();

		if (IsAvailable(BinningHorizontal) && IsAvailable(BinningHorizontal))
		{
			try
			{
				if (Isgrabbing)
				{
					camera_->StopGrabbing();
				}
				pProp->Get(binningFactor_);
				int64_t val = std::atoi(binningFactor_.c_str());
				BinningHorizontal->SetValue(val);
				BinningVertical->SetValue(val);
				if (Isgrabbing)
				{
					camera_->StartGrabbing();
				}
				pProp->Set(binningFactor_.c_str());
			}
			catch (const GenericException & e)
			{
				// Error handling.
				AddToLog(e.GetDescription());
				cerr << "An exception occurred." << endl
					<< e.GetDescription() << endl;
			}
		}
	}
	else if (eAct == MM::BeforeGet) {

		try {
			if (IsAvailable(BinningHorizontal) && IsAvailable(BinningHorizontal))
			{
				binningFactor_ = CDeviceUtils::ConvertToString((long)BinningHorizontal->GetValue());
				pProp->Set((long)BinningHorizontal->GetValue());
			}
			else
			{
				pProp->Set("1");
			}
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	return DEVICE_OK;
}

unsigned  BaslerCamera::GetNumberOfComponents() const
{
	return nComponents_;
};

int BaslerCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	bool isGrabing = camera_->IsGrabbing();
	if (isGrabing)
	{
		camera_->StopGrabbing();
	}

	CEnumerationPtr pixelFormat(nodeMap_->GetNode("PixelFormat"));

	if (eAct == MM::AfterSet) {
		pProp->Get(pixelType_);
		pixelFormat->FromString(pixelType_.c_str());

		CFloatPtr offset(nodeMap_->GetNode("BlackLevel"));
		offsetMax_ = offset->GetMax();
		offsetMin_ = offset->GetMin();
		SetPropertyLimits(MM::g_Keyword_Offset, offsetMin_, offsetMax_);
	}
	else if (eAct == MM::BeforeGet) {
		pixelType_.assign(pixelFormat->ToString().c_str());
		pProp->Set(pixelType_.c_str());
	}
	const char* subject("Bayer");
	std::size_t found = pixelFormat->ToString().find(subject);

	if (pixelFormat->ToString().compare("Mono8") == 0)
	{
		nComponents_ = 1;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);

	}
	if (pixelFormat->ToString().compare("Mono10") == 0)
	{
		nComponents_ = 1;
		bitDepth_ = 10;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_10bit);
	}
	if (pixelFormat->ToString().compare("Mono12") == 0)
	{
		nComponents_ = 1;
		bitDepth_ = 12;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_12bit);
	}
	if (pixelFormat->ToString().compare("Mono16") == 0)
	{
		nComponents_ = 1;
		bitDepth_ = 16;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
	}
	else if (found != std::string::npos)
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bitRGBA);
	}
	else if (pixelFormat->ToString().compare("BGR8") == 0)
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bitBGR);
	}
	else if (pixelFormat->ToString().compare("RGB8") == 0)
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bitRGB);
	}
	if (isGrabing)
	{
		camera_->StartGrabbing();
	}
	return DEVICE_OK;
}


int BaslerCamera::OnSensorReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	try
	{
		if (IsAvailable(camera_->SensorReadoutMode))
		{
			string Sensormode = "";
			if (eAct == MM::AfterSet) {
				pProp->Get(Sensormode);
				camera_->SensorReadoutMode.TrySetValue(Sensormode.c_str());
				pProp->Set(camera_->SensorReadoutMode.ToString());
			}
			else if (eAct == MM::BeforeGet) {
				pProp->Set(camera_->SensorReadoutMode.ToString());
			}
		}
	}
	catch (const GenericException & e)
	{
		// Error handling.
		AddToLog(e.GetDescription());
		cout << "An exception occurred." << endl << e.GetDescription() << endl;
		cerr << "An exception occurred." << endl
			<< e.GetDescription() << endl;
	}
	return DEVICE_OK;
}

int BaslerCamera::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	try
	{
		string TriggerMode_;
		CEnumerationPtr TriggerMode(nodeMap_->GetNode("TriggerMode"));
		if (TriggerMode != NULL && IsAvailable(TriggerMode))
		{
			if (eAct == MM::AfterSet) {
				pProp->Get(TriggerMode_);
				TriggerMode->FromString(TriggerMode_.c_str());
				pProp->Set(TriggerMode->ToString().c_str());
			}
			else if (eAct == MM::BeforeGet) {
				pProp->Set(TriggerMode->ToString().c_str());
			}
		}
	}
	catch (const GenericException & e)
	{
		// Error handling.
		AddToLog(e.GetDescription());
		cout << "An exception occurred." << endl << e.GetDescription() << endl;
		cerr << "An exception occurred." << endl
			<< e.GetDescription() << endl;
	}
	return DEVICE_OK;
}

int BaslerCamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		if (camera_->GetSfncVersion() >= Sfnc_2_0_0)
		{
			if (IsAvailable(camera_->DeviceTemperature))
			{
				temperature_ = camera_->DeviceTemperature.ToString();
			}
		}
		else
		{
			if (IsAvailable(camera_->TemperatureAbs))
			{
				temperature_ = camera_->TemperatureAbs.ToString();
			}
		}
		pProp->Set(temperature_.c_str());
	}
	return DEVICE_OK;
}


int BaslerCamera::OnTemperatureState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		CEnumerationPtr ptrtemperatureState_(nodeMap_->GetNode("TemperatureState"));
		temperatureState_.assign(ptrtemperatureState_->ToString().c_str());
		pProp->Set(temperatureState_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnReverseX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(reverseX_);
		CBooleanPtr reverseX(nodeMap_->GetNode("ReverseX"));
		reverseX->FromString(reverseX_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CBooleanPtr reverseX(nodeMap_->GetNode("ReverseX"));
		reverseX_.assign(reverseX->ToString().c_str());
		pProp->Set(reverseX_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnReverseY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(reverseY_);
		CBooleanPtr reverseY(nodeMap_->GetNode("ReverseY"));
		reverseY->FromString(reverseY_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CBooleanPtr reverseY(nodeMap_->GetNode("ReverseY"));
		reverseY_.assign(reverseY->ToString().c_str());
		pProp->Set(reverseY_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnAcqFramerateEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(setAcqFrm_);
		CBooleanPtr setAcqFrm(nodeMap_->GetNode("AcquisitionFrameRateEnable"));
		setAcqFrm->FromString(setAcqFrm_.c_str());

	}
	else if (eAct == MM::BeforeGet) {
		CBooleanPtr setAcqFrm(nodeMap_->GetNode("AcquisitionFrameRateEnable"));
		setAcqFrm_.assign(setAcqFrm->ToString().c_str());
		pProp->Set(setAcqFrm_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnAcqFramerate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(acqFramerate_);
		camera_->AcquisitionFrameRateAbs.TrySetValue(acqFramerate_);
		camera_->AcquisitionFrameRate.TrySetValue(acqFramerate_);
	}
	else if (eAct == MM::BeforeGet) {
		if (IsAvailable(camera_->AcquisitionFrameRate))
		{
			acqFramerate_ = camera_->AcquisitionFrameRate.GetValue();
		}
		else if (IsAvailable(camera_->AcquisitionFrameRateAbs))
		{
			acqFramerate_ = camera_->AcquisitionFrameRateAbs.GetValue();
		}
		std::ostringstream oss;
		//oss << std::fixed << std::setfill('0') << std::setprecision(2) << acqFramerate_;
		oss << acqFramerate_;
		pProp->Set(oss.str().c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnAutoGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string GainAuto_;
	if (eAct == MM::AfterSet) {
		pProp->Get(GainAuto_);
		CEnumerationPtr GainAuto(nodeMap_->GetNode("GainAuto"));
		GainAuto->FromString(GainAuto_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CEnumerationPtr GainAuto(nodeMap_->GetNode("GainAuto"));
		gcstring val = GainAuto->ToString();
		const char* s = val.c_str();
		pProp->Set(s);
	}
	return DEVICE_OK;
}

void  BaslerCamera::ResultingFramerateCallback(GenApi::INode* pNode)
{
	double currentvalue = -1;
	if (CFloatPtr(pNode) && IsReadable(pNode))
	{   //USB camera
		currentvalue = CFloatPtr(pNode)->GetValue();
	}
	else if (CIntegerPtr(pNode) && IsReadable(pNode))
	{ //Gige camera
		currentvalue = static_cast<double>(CIntegerPtr(pNode)->GetValue());
	}
	stringstream ss;
	ss << currentvalue;

	SetProperty("ResultingFrameRate", ss.str().c_str());

}
int BaslerCamera::OnResultingFramerate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		if (IsAvailable(camera_->ResultingFrameRateAbs))
		{
			pProp->Set(camera_->ResultingFrameRateAbs.GetValue());
		}
		else if (IsAvailable(camera_->ResultingFrameRate))
		{
			pProp->Set(camera_->ResultingFrameRate.GetValue());
		}
	}
	return DEVICE_OK;
}
int BaslerCamera::OnAutoExpore(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string ExposureAuto_;
	if (eAct == MM::AfterSet) {
		pProp->Get(ExposureAuto_);
		CEnumerationPtr ExposureAuto(nodeMap_->GetNode("ExposureAuto"));
		ExposureAuto->FromString(ExposureAuto_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CEnumerationPtr ExposureAuto(nodeMap_->GetNode("ExposureAuto"));
		gcstring val = ExposureAuto->ToString();
		const char* s = val.c_str();
		pProp->Set(s);
	}
	return DEVICE_OK;
}



int BaslerCamera::OnLightSourcePreset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string LightSourcePreset_;
	if (eAct == MM::AfterSet) {
		pProp->Get(LightSourcePreset_);
		CEnumerationPtr LightSourcePreset(nodeMap_->GetNode("LightSourcePreset"));
		LightSourcePreset->FromString(LightSourcePreset_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CEnumerationPtr LightSourcePreset(nodeMap_->GetNode("LightSourcePreset"));
		gcstring val = LightSourcePreset->ToString();
		const char* s = val.c_str();
		pProp->Set(s);
	}
	return DEVICE_OK;
}

int BaslerCamera::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(shutterMode_);
		CEnumerationPtr shutterMode(nodeMap_->GetNode("ShutterMode"));
		shutterMode->FromString(shutterMode_.c_str());
	}
	else if (eAct == MM::BeforeGet) {
		CEnumerationPtr shutterMode(nodeMap_->GetNode("ShutterMode"));
		gcstring gc = shutterMode->ToString();
		const char* s = gc.c_str();
		shutterMode_.assign(s);
		pProp->Set(shutterMode_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnDeviceLinkThroughputLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CIntegerPtr DeviceLinkThroughputLimit(nodeMap_->GetNode("DeviceLinkThroughputLimit"));
	if (IsAvailable(DeviceLinkThroughputLimit))
	{
		if (eAct == MM::AfterSet && IsWritable(DeviceLinkThroughputLimit))
		{
			long val;
			pProp->Get(val);
			DeviceLinkThroughputLimit->SetValue(val);
			DeviceLinkThroughputLimit_ = DeviceLinkThroughputLimit->GetValue();
		}
		else if (eAct == MM::BeforeGet)
		{
			DeviceLinkThroughputLimit_ = DeviceLinkThroughputLimit->GetValue();
			pProp->Set(CDeviceUtils::ConvertToString((long)DeviceLinkThroughputLimit_));
		}
	}

	return DEVICE_OK;
}

int BaslerCamera::OnInterPacketDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CIntegerPtr GevSCPD(nodeMap_->GetNode("GevSCPD"));
	if (IsAvailable(GevSCPD))
	{
		if (eAct == MM::AfterSet && IsWritable(GevSCPD))
		{
			long val;
			pProp->Get(val);
			GevSCPD->SetValue(val);
			InterPacketDelay_ = GevSCPD->GetValue();
		}
		else if (eAct == MM::BeforeGet)
		{
			InterPacketDelay_ = GevSCPD->GetValue();
			pProp->Set(CDeviceUtils::ConvertToString((long)InterPacketDelay_));
		}
	}
	return DEVICE_OK;
}

int BaslerCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	try
	{
		CFloatPtr gain(nodeMap_->GetNode("Gain"));
		CIntegerPtr GainRaw(nodeMap_->GetNode("GainRaw"));
		if (eAct == MM::AfterSet) {
			pProp->Get(gain_);
			if (gain_ > gainMax_) {
				gain_ = gainMax_;
			}
			if (gain_ < gainMin_) {
				gain_ = gainMin_;
			}
			if (IsAvailable(gain))
			{
				// the range gain depends on Pixel format sometimes.
				if (gain->GetMin() <= gain_ && gain->GetMax() >= gain_)
				{
					gain->SetValue(gain_);
				}
				else
				{
					AddToLog("gain value out of range");
					gainMax_ = gain->GetMax();
					gainMin_ = gain->GetMin();
					gain_ = gain->GetValue();
					SetPropertyLimits(MM::g_Keyword_Gain, gainMin_, gainMax_);
					pProp->Set(gain_);
				}
			}
			else if (IsAvailable(GainRaw))
			{
				// the range gain depends on Pixel format sometimes.
				if (GainRaw->GetMin() <= gain_ && GainRaw->GetMax() >= gain_)
				{
					GainRaw->SetValue((int64_t)(gain_));
				}
				else
				{
					AddToLog("gain value out of range");
					gainMax_ = gain->GetMax();
					gainMin_ = gain->GetMin();
					gain_ = gain->GetValue();
					SetPropertyLimits(MM::g_Keyword_Gain, gainMin_, gainMax_);
					pProp->Set(gain_);
				}
			}
		}
		else if (eAct == MM::BeforeGet) {

			if (IsAvailable(gain))
			{
				gain_ = gain->GetValue();
				pProp->Set(gain_);
			}
			else if (IsAvailable(GainRaw))
			{
				gain_ = (double)GainRaw->GetValue();
				pProp->Set(gain_);
				cout << "Gain Raw set successfully" << gain_ << endl;
			}
		}
	}
	catch (const GenericException & e)
	{
		// Error handling.
		AddToLog(e.GetDescription());
		cerr << "An exception occurred." << endl
			<< e.GetDescription() << endl;
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int BaslerCamera::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CFloatPtr offset(nodeMap_->GetNode("BlackLevel"));
	CFloatPtr offsetRaw(nodeMap_->GetNode("BlackLevelRaw"));
	if (eAct == MM::AfterSet) {
		pProp->Get(offset_);
		if (offset_ > offsetMax_) {
			offset_ = offsetMax_;
		}
		if (offset_ < offsetMin_) {
			offset_ = offsetMin_;
		}
		if (IsAvailable(offset))
		{
			offset->SetValue(offset_);
		}
		else if (IsAvailable(offsetRaw))
		{
			offsetRaw->SetValue(offset_);
		}
	}
	else if (eAct == MM::BeforeGet) {
		if (IsAvailable(offset))
		{
			offset_ = offset->GetValue();
			pProp->Set(offset_);
		}
		else if (IsAvailable(offsetRaw))
		{
			offset_ = offsetRaw->GetValue();
			pProp->Set(offset_);
		}
	}
	return DEVICE_OK;
}

void BaslerCamera::ReduceImageSize(int64_t Width, int64_t Height)
{
	// This function is just for debug purpose
	if (!camera_->IsOpen())
	{
		camera_->Open();
	}
	// Get the camera nodeMap_ object.
	nodeMap_ = &camera_->GetNodeMap();
	int64_t inc = 1;
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	if (width->GetMax() >= Width)
	{
		inc = width->GetInc();
		width->SetValue(Width - (Width % inc));
	}
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	if (height->GetMax() >= Height)
	{
		inc = height->GetInc();
		height->SetValue(Height - (Height % inc));
	}
}


void BaslerCamera::RGBPackedtoRGB(void* destbuffer, const CGrabResultPtr& ptrGrabResult)
{
	char* buffer = (char*)ptrGrabResult->GetBuffer();
	unsigned int srcOffset = 0;
	unsigned int dstOffset = 0;
	size_t Payloadsize = ptrGrabResult->GetPayloadSize() / 3;
	for (size_t i = 0; i < Payloadsize; ++i)
	{
		memcpy((char*)destbuffer + dstOffset, buffer + srcOffset, 3);
		srcOffset += 3;
		dstOffset += 4;
	}
}


void BaslerCamera::AddToLog(std::string msg)
{
	LogMessage(msg, false);
}

void BaslerCamera::UpdateTemperature()
{
	if (camera_->GetSfncVersion() >= Sfnc_2_0_0)
	{
		if (IsAvailable(camera_->DeviceTemperature))
		{
			int ret;
			temperature_ = camera_->DeviceTemperature.ToString();
			temperatureState_ = camera_->TemperatureState.ToString();
			ret = SetProperty("Temperature", temperatureState_.c_str());
			assert(ret == DEVICE_OK);
			ret = UpdateProperty("Temperature");
			assert(ret == DEVICE_OK);
			ret = SetProperty("TemperatureState", temperatureState_.c_str());
			assert(ret == DEVICE_OK);
			ret = UpdateProperty("TemperatureState");
			assert(ret == DEVICE_OK);
		}
	}
}

CTempCameraEventHandler::CTempCameraEventHandler(BaslerCamera* dev) :
	dev_(dev)
{}

void CTempCameraEventHandler::OnCameraEvent(CBaslerUniversalInstantCamera& camera, intptr_t userProvidedId, GenApi::INode* pNode)
{
	{
		(void)camera;
		(void)userProvidedId;
		CParameter value(pNode);
		if (value.IsValid())
		{
			stringstream  msg;
			msg << "Temperature State of camera has been changed to : " << camera.TemperatureState.ToString();
			dev_->AddToLog(msg.str());
			dev_->UpdateTemperature();
		}
		std::cout << std::endl;
	}
}

CircularBufferInserter::CircularBufferInserter(BaslerCamera* dev) :
	dev_(dev)
{}

void CircularBufferInserter::OnImageGrabbed(CInstantCamera& /* camera */, const CGrabResultPtr& ptrGrabResult)
{

	// char label[MM::MaxStrLength];

	// Important:  meta data about the image are generated here:
	Metadata md;
	md.put("Camera", "");
	md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString((long)ptrGrabResult->GetWidth()));
	md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString((long)ptrGrabResult->GetHeight()));
	md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString((long)ptrGrabResult->GetImageNumber()));
	md.put(MM::g_Keyword_Meatdata_Exposure, dev_->GetExposure());
	// Image grabbed successfully?
	if (ptrGrabResult->GrabSucceeded())
	{
		const char* subject("Bayer");
		bool IsByerFormat = false;
		string currentPixelFormat = Pylon::CPixelTypeMapper::GetNameByPixelType(ptrGrabResult->GetPixelType());
		std::size_t found = currentPixelFormat.find(subject);
		if (found != std::string::npos)
		{
			IsByerFormat = true;
		}
		if (ptrGrabResult->GetPixelType() == PixelType_Mono8 || ptrGrabResult->GetPixelType() == PixelType_Mono12 ||
			ptrGrabResult->GetPixelType() == PixelType_Mono10 || ptrGrabResult->GetPixelType() == PixelType_Mono16
			)
		{

			//copy to intermediate buffer
			int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*)ptrGrabResult->GetBuffer(),
				(unsigned)ptrGrabResult->GetWidth(), (unsigned)ptrGrabResult->GetHeight(),
				(unsigned)dev_->GetImageBytesPerPixel(), 1, md.Serialize().c_str(), FALSE);
			if (ret == DEVICE_BUFFER_OVERFLOW) {
				//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
				dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}
		}
		else if (IsByerFormat || ptrGrabResult->GetPixelType() == PixelType_RGB8packed)
		{
			CPylonImage image;
			dev_->converter->Convert(image, ptrGrabResult);

			//copy to intermediate buffer
			int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*)image.GetBuffer(),
				(unsigned)dev_->GetImageWidth(), (unsigned)dev_->GetImageHeight(),
				(unsigned)dev_->GetImageBytesPerPixel(), 1, md.Serialize().c_str(), FALSE);
			if (ret == DEVICE_BUFFER_OVERFLOW) {
				//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
				dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}
		}
		else if (ptrGrabResult->GetPixelType() == PixelType_BGR8packed)
		{
			dev_->RGBPackedtoRGB(dev_->Buffer4ContinuesShot, ptrGrabResult);
			//copy to intermediate buffer
			int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*)dev_->Buffer4ContinuesShot,
				(unsigned)dev_->GetImageWidth(), (unsigned)dev_->GetImageHeight(),
				(unsigned)dev_->GetImageBytesPerPixel(), 1, md.Serialize().c_str(), FALSE);
			if (ret == DEVICE_BUFFER_OVERFLOW)
			{
				//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
				dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}
		}
	}
	else
	{
		std::stringstream ss;
		ss << "Error: " << ptrGrabResult->GetErrorCode() << " " << ptrGrabResult->GetErrorDescription() << endl;
		dev_->AddToLog(ss.str());
	}
}
