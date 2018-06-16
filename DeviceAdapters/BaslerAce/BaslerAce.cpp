///////////////////////////////////////////////////////////////////////////////
// FILE:          BAslerAce.cpp
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device Adapter for Basler Ace Camera
//
// Copyright 2018 Henry Pinkard
//
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


#include <pylon/PylonIncludes.h>
//#include <pylon/usb/PylonUsbIncludes.h>
#include <pylon/usb/_BaslerUsbCameraParams.h>

// Namespace for using pylon objects.
using namespace Pylon;
using namespace GenApi;

#include "BaslerAce.h"
#include <sstream>
#include <math.h>
#include "ModuleInterface.h"
#include "DeviceUtils.h"

using namespace std;

const char* g_BaslerCameraDeviceName = "BaslerAce";

const char* g_PropertyChannel = "PropertyNAme";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_BaslerCameraDeviceName, MM::CameraDevice, "Basler Ace Monochrome Camera");
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
BaslerCamera::BaslerCamera():
CCameraBase<BaslerCamera> (),
	maxWidth_(0),
	maxHeight_(0),
	exposure_us_(0),
	exposureMax_(0),
	exposureMin_(0),
	gainMax_(0),
	gainMin_(0),
	pixelType_("Undefined"),
	sensorReadoutMode_("Undefined"),
	shutterMode_("None"),
	imgBuffer_(NULL),
	nodeMap_(NULL),
	initialized_(false)
{


	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

	//pre-init properties

}

BaslerCamera::~BaslerCamera()
{
	free(imgBuffer_);
}

/**
* Obtains device name.
*/
void BaslerCamera::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_BaslerCameraDeviceName);
}

/**
* Intializes the hardware.
*/
int BaslerCamera::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	// Before using any pylon methods, the pylon runtime must be initialized. 
	PylonInitialize();
	camera_ = new CInstantCamera(CTlFactory::GetInstance().CreateFirstDevice()); // returns a pointer to the device

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_BaslerCameraDeviceName, MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "Basler Ace monochrome device adapter", MM::String, true);
	if (DEVICE_OK != ret)
		return ret;



	Pylon::String_t modelName = camera_->GetDeviceInfo().GetModelName();
	//Get information about camera (e.g. height, width, bytte depth)

	//Call before reading/writing any paramters
	camera_->Open();
	// Get the camera nodeMap_ object.
	nodeMap_ = &camera_->GetNodeMap();
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	maxWidth_ = width->GetMax();
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	maxHeight_ = height->GetMax();
	ClearROI();

	//Exposure
	CFloatPtr exposure( nodeMap_->GetNode( "ExposureTime"));
	exposure_us_ = exposure->GetValue();
	exposureMax_ = exposure->GetMax();
	exposureMin_ = exposure->GetMin();

	//Pixel type
	CPropertyAction *pAct = new CPropertyAction (this, &BaslerCamera::OnPixelType);
	ret = CreateProperty(MM::g_Keyword_PixelType, "NA", MM::String, false, pAct);
	vector<string> pixelTypeValues;
	CEnumerationPtr pixelFormat( nodeMap_->GetNode( "PixelFormat"));
	if (IsAvailable( pixelFormat->GetEntryByName( "Mono8"))) {
		pixelTypeValues.push_back("Mono8");
		pixelFormat->FromString("Mono8");
		pixelType_ = "Mono8";
	}
	if (IsAvailable( pixelFormat->GetEntryByName( "Mono10"))) {
		pixelTypeValues.push_back("Mono10");
		pixelFormat->FromString("Mono10");
		pixelType_ = "Mono10";
	}
	if (IsAvailable( pixelFormat->GetEntryByName( "Mono12"))) {
		pixelTypeValues.push_back("Mono12");
		pixelFormat->FromString("Mono12");
		pixelType_ = "Mono12"; //default to using highest bit depth
	}
	SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);

	/////Gain//////
	//Turn off auto gain
	CEnumerationPtr gainAuto( nodeMap_->GetNode( "GainAuto"));
	if ( IsWritable( gainAuto)) {
		gainAuto->FromString("Off");
	}
	//get gain limits and value
	CFloatPtr gain( nodeMap_->GetNode( "Gain"));	 
	gainMax_ = gain->GetMax();
	gainMin_ = gain->GetMin();
	gain_ = gain->GetValue();
	//make property
	pAct = new CPropertyAction (this, &BaslerCamera::OnGain);
	ret = CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
	SetPropertyLimits(MM::g_Keyword_Gain, gainMin_, gainMax_);

	/////Offset//////
	CFloatPtr offset( nodeMap_->GetNode( "BlackLevel"));	 
	offsetMax_ = offset->GetMax();
	offsetMin_ = offset->GetMin();
	offset_ = offset->GetValue();
	//make property
	pAct = new CPropertyAction (this, &BaslerCamera::OnOffset);
	ret = CreateProperty(MM::g_Keyword_Offset, "1.0", MM::Float, false, pAct);
	SetPropertyLimits(MM::g_Keyword_Offset, offsetMin_, offsetMax_);

	////Sensor readout//////
	//pAct = new CPropertyAction (this, &BaslerCamera::OnSensorReadoutMode);
	//ret = CreateProperty("SensorReadoutMode", "NA", MM::String, false, pAct);
	//vector<string> vals;
	//CEnumerationPtr sensorReadout( nodeMap_->GetNode( "SensorReadoutMode"));
	// if ( IsAvailable( sensorReadout->GetEntryByName( "Normal"))) {
	//	 vals.push_back("Normal");
	// }
	// if ( IsAvailable( sensorReadout->GetEntryByName( "Fast"))) {
	//	 vals.push_back("Fast");
	// }
	// SetAllowedValues("SensorReadoutMode", vals);


	////Shutter mode//////
	pAct = new CPropertyAction (this, &BaslerCamera::OnShutterMode);
	ret = CreateProperty("ShutterMode", "NA", MM::String, false, pAct);
	vector<string> shutterVals;
	CEnumerationPtr shutterMode( nodeMap_->GetNode( "ShutterMode"));
	if ( IsAvailable( shutterMode->GetEntryByName( "Global"))) {
		shutterVals.push_back("Global");
	}
	if ( IsAvailable( shutterMode->GetEntryByName( "Rolling"))) {
		shutterVals.push_back("Rolling");
	}
	if ( IsAvailable( shutterMode->GetEntryByName( "GlobalResetRelease"))) {
		shutterVals.push_back("GlobalResetRelease");
	}
	SetAllowedValues("ShutterMode", shutterVals);



	//// binning
	pAct = new CPropertyAction (this, &BaslerCamera::OnBinning);
	ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> binValues;
	binValues.push_back("1");
	ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
	if (ret != DEVICE_OK)
		return ret;

	// synchronize all properties
	// --------------------------
	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	//preparation for snaps
	ResizeSnapBuffer();
	//preperation for sequences
	camera_->RegisterImageEventHandler( new CircularBufferInserter(this), RegistrationMode_Append, Cleanup_Delete);


	initialized_ = true;
	return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
*/
int BaslerCamera::Shutdown()
{
	camera_->Close();
	delete camera_;
	initialized_ = false;
	PylonTerminate();  
	return DEVICE_OK;
}

int BaslerCamera::SnapImage()
{ 
	camera_->StartGrabbing( 1, GrabStrategy_OneByOne, GrabLoop_ProvidedByUser);
	// This smart pointer will receive the grab result data.
	//When all smart pointers referencing a Grab Result Data object go out of scope, the grab result's image buffer is reused for grabbing
	CGrabResultPtr ptrGrabResult;
	int timeout_ms = 5000;
	if (!camera_->RetrieveResult( timeout_ms, ptrGrabResult, TimeoutHandling_ThrowException)) {
		return DEVICE_ERR;
	}
	if (!ptrGrabResult->GrabSucceeded()) {
		return DEVICE_ERR;		
	}
	//copy image buffer to a snap buffer allocated by device adapter
	const void* buffer = ptrGrabResult->GetBuffer();
	memcpy(imgBuffer_, buffer, GetImageBufferSize());

	return DEVICE_OK;
}

/**
* Returns pixel data.
*/
const unsigned char* BaslerCamera::GetImageBuffer()
{  
	return (unsigned char*) imgBuffer_;
}

unsigned BaslerCamera::GetImageWidth() const
{
	const CIntegerPtr width = nodeMap_->GetNode("Width");
	return width->GetValue();
}

unsigned BaslerCamera::GetImageHeight() const
{
	const CIntegerPtr height = nodeMap_->GetNode("Height");
	return height->GetValue();
}

/**
* Returns image buffer pixel depth in bytes.
*/
unsigned BaslerCamera::GetImageBytesPerPixel() const
{
	if (pixelType_ == "Mono8") {
		return 1;
	} else if (pixelType_ == "Mono10") {
		return 2;
	} else if (pixelType_ == "Mono12") {
		return 2;
	}
	assert(0); //shouldn't happen
	return 0;
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
*/
unsigned BaslerCamera::GetBitDepth() const
{
	if (pixelType_ == "Mono8") {
		return 8;
	} else if (pixelType_ == "Mono10") {
		return 10;
	} else if (pixelType_ == "Mono12") {
		return 12;
	}
	assert(0); //shoudlnt happen
	return 0;
}

/**
* Returns the size in bytes of the image buffer.
*/
long BaslerCamera::GetImageBufferSize() const
{
	return GetImageWidth()*GetImageHeight()*GetImageBytesPerPixel();
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
		xSize = width->GetMin();
	}
	if (ySize < height->GetMin()) {
		ySize = height->GetMin();
	}
	if (x < offsetX->GetMin()) {
		x = offsetX->GetMin();
	}
	if (y < offsetY->GetMin()) {
		y = offsetY->GetMin();
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
	x = offsetX->GetValue();
	y = offsetY->GetValue();
	xSize = width->GetValue();
	ySize = height->GetValue();
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
	} else if (exp < exposureMin_) {
		exp = exposureMin_;
	}
	INodeMap& nodeMap_ = camera_->GetNodeMap();
	CFloatPtr exposure( nodeMap_.GetNode( "ExposureTime"));
	exposure->SetValue(exp);
	exposure_us_ = exp;
}

/**
* Returns the current binning factor.
*/
int BaslerCamera::GetBinning() const
{
	return 1;
}

int BaslerCamera::SetBinning(int binFactor)
{
	if (binFactor == 1) {
		return DEVICE_OK;
	}
	return DEVICE_UNSUPPORTED_COMMAND;
}

int BaslerCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow){
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK) {
		return ret;
	}
	camera_->StartGrabbing(numImages, GrabStrategy_OneByOne, GrabLoop_ProvidedByInstantCamera);
	return DEVICE_OK;
}

int BaslerCamera::StartSequenceAcquisition(double interval_ms) {
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK) {
		return ret;
	}
	camera_->StartGrabbing( GrabStrategy_OneByOne, GrabLoop_ProvidedByInstantCamera);
	return DEVICE_OK;
}

bool BaslerCamera::IsCapturing()
{
	return camera_->IsGrabbing();
}

int BaslerCamera::StopSequenceAcquisition()
{
	camera_->StopGrabbing();
	GetCoreCallback()->AcqFinished(this, 0);
	return DEVICE_OK;
}

int BaslerCamera::PrepareSequenceAcqusition()
{
	// nothing to prepare
	return DEVICE_OK;
}

void BaslerCamera::ResizeSnapBuffer() {
	free(imgBuffer_);
	int bytes = GetImageBufferSize();
	imgBuffer_ = malloc(bytes);
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int BaslerCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set("1");
	}
	return DEVICE_OK;
}

int BaslerCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(pixelType_);
		CEnumerationPtr pixelFormat( nodeMap_->GetNode( "PixelFormat"));
		pixelFormat->FromString(pixelType_.c_str());
	} else if (eAct == MM::BeforeGet) {
		CEnumerationPtr pixelFormat( nodeMap_->GetNode( "PixelFormat"));
		pixelType_.assign(pixelFormat->ToString().c_str());
		pProp->Set(pixelType_.c_str());
	}
	return DEVICE_OK;
}

//Might need to find some way to access expert features to do this 
//int BaslerCamera::OnSensorReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//	if (eAct == MM::AfterSet) {
//		pProp->Get(sensorReadoutMode_);
//		CEnumerationPtr sensorReadoutMode( nodeMap_->GetNode( "SensorReadoutMode"));
//		sensorReadoutMode->FromString(sensorReadoutMode_.c_str());
//	} else if (eAct == MM::BeforeGet) {
//		CEnumerationPtr sensorReadoutMode( nodeMap_->GetNode( "SensorReadoutMode"));
//		gcstring gc = sensorReadoutMode->ToString();
//		const char* s = gc.c_str();
//		sensorReadoutMode_.assign(s);
//		pProp->Set(sensorReadoutMode_.c_str());
//	}
//   return DEVICE_OK;
//}

int BaslerCamera::OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(shutterMode_);
		CEnumerationPtr shutterMode( nodeMap_->GetNode( "ShutterMode"));
		shutterMode->FromString(shutterMode_.c_str());
	} else if (eAct == MM::BeforeGet) {
		CEnumerationPtr shutterMode( nodeMap_->GetNode( "ShutterMode"));
		gcstring gc = shutterMode->ToString();
		const char* s = gc.c_str();
		shutterMode_.assign(s);
		pProp->Set(shutterMode_.c_str());
	}
	return DEVICE_OK;
}

int BaslerCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(gain_);
		if (gain_ > gainMax_) {
			gain_ = gainMax_;
		}
		if (gain_ < gainMin_) {
			gain_ = gainMin_;
		}
		CFloatPtr gain( nodeMap_->GetNode( "Gain"));	 
		gain->SetValue(gain_);
	} else if (eAct == MM::BeforeGet) {
		CFloatPtr gain( nodeMap_->GetNode( "Gain"));
		gain_ = gain->GetValue();
		pProp->Set(gain_);
	}
	return DEVICE_OK;
}

int BaslerCamera::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet) {
		pProp->Get(offset_);
		if (offset_ > offsetMax_) {
			offset_ = offsetMax_;
		}
		if (offset_ < offsetMin_) {
			offset_ = offsetMin_;
		}
		CFloatPtr offset( nodeMap_->GetNode( "BlackLevel"));	 
		offset->SetValue(offset_);
	} else if (eAct == MM::BeforeGet) {
		CFloatPtr offset( nodeMap_->GetNode( "BlackLevel"));
		offset_ = offset->GetValue();
		pProp->Set(offset_);
	}
	return DEVICE_OK;
}




CircularBufferInserter::CircularBufferInserter(BaslerCamera* dev):
dev_(dev)
{}

void CircularBufferInserter::OnImageGrabbed( CInstantCamera& camera, const CGrabResultPtr& ptrGrabResult)
{
	// Image grabbed successfully?
	if (ptrGrabResult->GrabSucceeded()) {
		//copy to intermediate buffer
		int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*) ptrGrabResult->GetBuffer(),
			(unsigned) dev_->GetImageWidth(), (unsigned ) dev_->GetImageHeight(), 
			(unsigned) dev_->GetImageBytesPerPixel());
		if (ret == DEVICE_BUFFER_OVERFLOW) {
			//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
			dev_->GetCoreCallback()->ClearImageBuffer(dev_);
		}

	} else {
		//TODO: error handling

	}
}
