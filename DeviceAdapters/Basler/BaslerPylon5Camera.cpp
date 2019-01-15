//////////////////////////////////////////////////////////////////////////////
// FILE:          BAslerAce.cpp
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device Adapter for Basler Ace Camera
//
// Copyright 2018 Henry Pinkard
// Copyright 2019 SMA extended for supporting Bayer, RGB formats
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
# include <pylon/PylonGUI.h>

// Namespace for using pylon objects.
using namespace Pylon;
using namespace GenApi;
using namespace GenICam;

#include "BaslerPylon5Camera.h"
#include <sstream>
#include <math.h>
#include "ModuleInterface.h"
#include "DeviceUtils.h"
#include <vector>



using namespace std;

const char* g_BaslerCameraDeviceName = "BaslerAce";

const char* g_PropertyChannel = "PropertyNAme";
static const char* g_PixelType_8bit = "8bit";
static const char* g_PixelType_16bit = "16bit";
static const char* g_PixelType_10packedbit = "10bit";
static const char* g_PixelType_12packedbit = "12bit";


static const char* g_PixelType_8bitRGBA = "8bitBGRA";
const char* g_PixelType_8bitRGB =       "8bitRGB";
const char* g_PixelType_8bitBGR =       "8bitBGR";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_BaslerCameraDeviceName, MM::CameraDevice, "Basler Ace Camera");
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
	bitDepth_(8),
	imgBuffer_(NULL),
	colorCamera_(true),
	pixelType_("Undefined"),
	sensorReadoutMode_("Undefined"),
	shutterMode_("None"),
	nodeMap_(NULL),
	initialized_(false)

{


	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

	//pre-init properties

}

BaslerCamera::~BaslerCamera()
{
	if(imgBuffer_ != NULL)
		free(imgBuffer_);
	free(Buffer4ContinuesShot);
}

/**
* Obtains device name.
*/
void BaslerCamera::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_BaslerCameraDeviceName);
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
		camera_ = new CInstantCamera(CTlFactory::GetInstance().CreateFirstDevice()); // returns a pointer to the device

		// initialize the pylon image formatter.
		converter = new CImageFormatConverter();
		converter->OutputPixelFormat = PixelType_BGRA8packed;

		// Name
		int ret = CreateProperty(MM::g_Keyword_Name, g_BaslerCameraDeviceName, MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		// Description
		ret = CreateProperty(MM::g_Keyword_Description, "Basler Ace device adapter", MM::String, true);
		if (DEVICE_OK != ret)
			return ret;

		Pylon::String_t modelName = camera_->GetDeviceInfo().GetModelName();
		//Get information about camera (e.g. height, width, byte depth)

		//Call before reading/writing any parameters
		camera_->Open();
		// Get the camera nodeMap_ object.
		nodeMap_ = &camera_->GetNodeMap();
		const CIntegerPtr width = nodeMap_->GetNode("Width");
		maxWidth_ = (unsigned int) width->GetMax();
		const CIntegerPtr height = nodeMap_->GetNode("Height");
		maxHeight_ = (unsigned int) height->GetMax();
	#if (!_DEBUG)
			ClearROI();// to be enabled for release
	#endif

		long bytes = (long) (height->GetValue() * width->GetValue() * 4) ;
		Buffer4ContinuesShot = malloc(bytes);


		//Exposure
		CFloatPtr exposure( nodeMap_->GetNode( "ExposureTime"));
		exposure_us_ = exposure->GetValue();
		exposureMax_ = exposure->GetMax();
		exposureMin_ = exposure->GetMin();

		//Pixel type
		CPropertyAction *pAct = new CPropertyAction (this, &BaslerCamera::OnPixelType);
		ret = CreateProperty(MM::g_Keyword_PixelType, "NA", MM::String, false, pAct);
		assert(ret == DEVICE_OK);

		vector<string> pixelTypeValues;

		CEnumerationPtr pixelFormat( nodeMap_->GetNode( "PixelFormat"));
		if (IsAvailable( pixelFormat->GetEntryByName( "Mono8"))) {
			pixelTypeValues.push_back("Mono8");
			pixelFormat->FromString("Mono8");
			pixelType_ = "Mono8";	
		}
		//if (IsAvailable( pixelFormat->GetEntryByName( "Mono10"))) {
		//	pixelTypeValues.push_back("Mono10");
		//	pixelFormat->FromString("Mono10");
		//	pixelType_ = "Mono10";
		//}
		//if (IsAvailable( pixelFormat->GetEntryByName( "Mono12"))) {
		//	pixelTypeValues.push_back("Mono12");
		//	pixelFormat->FromString("Mono12");
		//	pixelType_ = "Mono12"; //default to using highest bit depth
		//}
		if (IsAvailable( pixelFormat->GetEntryByName("BayerRG8"))) {
			pixelTypeValues.push_back("BayerRG8");
			pixelFormat->FromString("BayerRG8");
			pixelType_ = "BayerRG8" ; //default to using highest bit depth		
		}
		if (IsAvailable( pixelFormat->GetEntryByName( "BayerBG8"))) {
			pixelTypeValues.push_back("BayerBG8");
			pixelFormat->FromString("BayerBG8");
			pixelType_ = "BayerBG8" ; //default to using highest bit depth		
		}
		if (IsAvailable( pixelFormat->GetEntryByName( "BGR8"))) {
			pixelTypeValues.push_back("BGR8");
			pixelFormat->FromString("BGR8");
			pixelType_ = "BGR8" ; //default to using highest bit depth		
		}
		if (IsAvailable( pixelFormat->GetEntryByName( "RGB8"))) {
			pixelTypeValues.push_back("RGB8");
			pixelFormat->FromString("RGB8");
			pixelType_ = "RGB8" ; //default to using highest bit depth		
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
		//preparation for sequences
		camera_->RegisterImageEventHandler( new CircularBufferInserter(this), RegistrationMode_Append, Cleanup_Delete);


		initialized_ = true;
		return DEVICE_OK;	
	}
	catch (exception &ex)
	{		
	   std::cerr << ex.what() << std::endl;
	   throw;
	}

	
}

int BaslerCamera::SetProperty(const char* name, const char* value)
{
	int nRet = __super::SetProperty( name, value );
	return nRet;
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
	try
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

		CopyToImageBuffer(ptrGrabResult);

		return DEVICE_OK;
	}
	catch (exception &ex)
	{		
	   std::cerr << ex.what() << std::endl;
	   throw;
	}
	
}

void BaslerCamera::CopyToImageBuffer(CGrabResultPtr ptrGrabResult)
{
	char* subject ("Bayer");
	bool IsByerFormat = false;
	string currentPixelFormat = Pylon::CPixelTypeMapper::GetNameByPixelType(ptrGrabResult->GetPixelType()) ;
	std::size_t found = currentPixelFormat.find(subject);
	if (found != std::string::npos)
	{
		IsByerFormat = true;
	}
	if(ptrGrabResult->GetPixelType() == PixelType_Mono8)
	{
       // Workaround : OnPixelType call back will not be fired always.
	    nComponents_ = 1;
        bitDepth_ = 8;

		//copy image buffer to a snap buffer allocated by device adapter
		const void* buffer = ptrGrabResult->GetBuffer();
		memcpy(imgBuffer_, buffer, GetImageBufferSize());
		SetProperty( MM::g_Keyword_PixelType, g_PixelType_8bit);
	}
	else if (ptrGrabResult->GetPixelType() == PixelType_Mono10 ||
	   ptrGrabResult->GetPixelType() == PixelType_Mono12 || ptrGrabResult->GetPixelType() == PixelType_Mono16)
	{
		 nComponents_ = 2;
        bitDepth_ = 16;

		//copy image buffer to a snap buffer allocated by device adapter
		const void* buffer = ptrGrabResult->GetBuffer();
		memcpy(imgBuffer_, buffer, GetImageBufferSize());
		SetProperty( MM::g_Keyword_PixelType, g_PixelType_16bit);
	
	}
	else if (IsByerFormat || ptrGrabResult->GetPixelType() == PixelType_RGB8packed )
	{
		nComponents_ = 4;
        bitDepth_ = 8;

		CPylonImage image;
		converter->Convert(imgBuffer_,GetImageBufferSize(),ptrGrabResult);
		SetProperty( MM::g_Keyword_PixelType, g_PixelType_8bitRGBA);	    
	}
	else if (ptrGrabResult->GetPixelType() == PixelType_BGR8packed  )
	{
		nComponents_ = 4;
        bitDepth_ = 8;
		SetProperty( MM::g_Keyword_PixelType, g_PixelType_8bitBGR);
		RGBPackedtoRGB(imgBuffer_,ptrGrabResult);
			
	}
	/*else if (ptrGrabResult->GetPixelType() == EPixelType::PixelType_RGB8packed )   // for unknown reason MM changes the color channel
	{
		nComponents_ = 4;
        bitDepth_ = 8;
		SetProperty( MM::g_Keyword_PixelType, s_PixelType_8bitRGB);	
		RGBPackedtoRGB(imgBuffer_,ptrGrabResult);		
	}*/
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
	return(unsigned) width->GetValue();
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
	if (pixelType_ == "Mono8") {
		return 1;
	} else if (pixelType_ == "Mono10") {
		return 2;
	} else if (pixelType_ == "Mono12" || pixelType_ == "Mono16" ) {
		return 2;
	}
	else if (pixelType_ == "BayerRG8") {
		return 4;
	}
	else if (pixelType_ == "BGR8" || pixelType_ == "RGB8" ) {
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
	if (pixelType_ == "Mono8") {
		return 8;
	} else if (pixelType_ == "Mono10") {
		return 10;
	} else if (pixelType_ == "Mono12" || pixelType_ == "Mono16") {
		return 16;
	}
	else if (pixelType_ == "BayerRG8" || pixelType_ == "BGR8" || pixelType_ == "RGB8") {	
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
		xSize = (unsigned int) width->GetMin();
	}
	if (ySize < height->GetMin()) {
		ySize = (unsigned int) height->GetMin();
	}
	if (x < offsetX->GetMin()) {
		x = (unsigned int) offsetX->GetMin();
	}
	if (y < offsetY->GetMin()) {
		y = (unsigned int) offsetY->GetMin();
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
	x = (unsigned int) offsetX->GetValue();
	y = (unsigned int) offsetY->GetValue();
	xSize = (unsigned int) width->GetValue();
	ySize = (unsigned int) height->GetValue();
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
	if(IsWritable(exposure))
	{
	    exposure->SetValue(exp);
	    exposure_us_ = exp;
	}

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

int BaslerCamera::StartSequenceAcquisition(long numImages, double /* interval_ms */, bool /* stopOnOverflow */){
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK) {
		return ret;
	}
	camera_->StartGrabbing(numImages, GrabStrategy_OneByOne, GrabLoop_ProvidedByInstantCamera);
	return DEVICE_OK;
}

int BaslerCamera::StartSequenceAcquisition(double /* interval_ms */) {
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
	long bytes = GetImageBufferSize() ;
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

unsigned  BaslerCamera::GetNumberOfComponents() const		
  { 
	  return nComponents_;
  };

int BaslerCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	CEnumerationPtr pixelFormat( nodeMap_->GetNode( "PixelFormat"));

	if (eAct == MM::AfterSet) {
		pProp->Get(pixelType_);
		pixelFormat->FromString(pixelType_.c_str());
	} else if (eAct == MM::BeforeGet) {	
		pixelType_.assign(pixelFormat->ToString().c_str());
		pProp->Set(pixelType_.c_str());
	}

    char* subject ("Bayer");
	std::size_t found = pixelFormat->ToString().find(subject);
		
	if(pixelFormat->ToString().compare("Mono8") == 0 )
	{
		nComponents_ = 1;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType,g_PixelType_8bit);
	}
	if(pixelFormat->ToString().compare("Mono10") == 0 || pixelFormat->ToString().compare("Mono12") == 0 ||pixelFormat->ToString().compare("Mono16") == 0 )
	{
		nComponents_ = 2;
		bitDepth_ = 16;
		SetProperty(MM::g_Keyword_PixelType,g_PixelType_16bit);
	}


	else if (found != std::string::npos )
	{
		nComponents_ = 4;
		bitDepth_ = 8;
		SetProperty(MM::g_Keyword_PixelType,g_PixelType_8bitRGBA);
	}
	else if(pixelFormat->ToString().compare("BGR8") == 0 )
	{
		nComponents_ = 4;
		bitDepth_ = 8;	
		SetProperty(MM::g_Keyword_PixelType,g_PixelType_8bitBGR);
	}
	else if(pixelFormat->ToString().compare("RGB8") == 0 )
	{
		nComponents_ = 4;
        bitDepth_ = 8;	
		SetProperty(MM::g_Keyword_PixelType,g_PixelType_8bitRGB);
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


void BaslerCamera::RGBPackedtoRGB(void* destbuffer,const CGrabResultPtr& ptrGrabResult)
{

	char* buffer = (char*)ptrGrabResult->GetBuffer();
	unsigned int srcOffset = 0;
	unsigned int dstOffset = 0;
	size_t Payloadsize = ptrGrabResult->GetPayloadSize()/3;	
	for(size_t i=0; i < Payloadsize; ++i)
	{
		memcpy((char*)destbuffer+dstOffset, buffer+srcOffset,3);
		srcOffset += 3;
		dstOffset += 4;
	}
	
}




CircularBufferInserter::CircularBufferInserter(BaslerCamera* dev):
dev_(dev)
{}

void CircularBufferInserter::OnImageGrabbed( CInstantCamera& /* camera */, const CGrabResultPtr& ptrGrabResult)
{
	
   // char label[MM::MaxStrLength];
 
   // Important:  metadata about the image are generated here:
   Metadata md;
   md.put("Camera", "");
  
   md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) ptrGrabResult->GetWidth())); 
   md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long)  ptrGrabResult->GetHeight())); 
   md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString( (long)  ptrGrabResult->GetImageNumber())); 

	// Image grabbed successfully?
	if (ptrGrabResult->GrabSucceeded())
	{
		char* subject ("Bayer");
		bool IsByerFormat = false;
		string currentPixelFormat = Pylon::CPixelTypeMapper::GetNameByPixelType(ptrGrabResult->GetPixelType()) ;
		std::size_t found = currentPixelFormat.find(subject);
		if (found != std::string::npos)
		{
			IsByerFormat = true;
		}
		if(ptrGrabResult->GetPixelType() == PixelType_Mono8)
		{

		//copy to intermediate buffer
		int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*) ptrGrabResult->GetBuffer(),
			(unsigned) dev_->GetImageWidth(), (unsigned ) dev_->GetImageHeight(), 
			(unsigned) dev_->GetImageBytesPerPixel(),1,md.Serialize().c_str(),FALSE);
		if (ret == DEVICE_BUFFER_OVERFLOW) {
			//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
			dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}
		}
		else if( IsByerFormat || ptrGrabResult->GetPixelType() == PixelType_RGB8packed)
		{
			CPylonImage image ;
			dev_->converter->Convert(image,ptrGrabResult);
					
			//copy to intermediate buffer
			int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*)image.GetBuffer(),
				(unsigned) dev_->GetImageWidth(), (unsigned ) dev_->GetImageHeight(), 
				(unsigned) dev_->GetImageBytesPerPixel(),1,md.Serialize().c_str(),FALSE);
			if (ret == DEVICE_BUFFER_OVERFLOW) {
				//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
				dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}	
		}else if(ptrGrabResult->GetPixelType() ==  PixelType_BGR8packed )
		{
			 dev_->RGBPackedtoRGB(dev_->Buffer4ContinuesShot,ptrGrabResult);
			 //copy to intermediate buffer
			int ret = dev_->GetCoreCallback()->InsertImage(dev_, (const unsigned char*)dev_->Buffer4ContinuesShot,
				(unsigned) dev_->GetImageWidth(), (unsigned ) dev_->GetImageHeight(), 
				(unsigned) dev_->GetImageBytesPerPixel(),1,md.Serialize().c_str(),FALSE);
			if (ret == DEVICE_BUFFER_OVERFLOW) 
			{
				//if circular buffer overflows, just clear it and keep putting stuff in so live mode can continue
				dev_->GetCoreCallback()->ClearImageBuffer(dev_);
			}
		}
	} 
	else
	{
		//TODO: error handling

	}
}
