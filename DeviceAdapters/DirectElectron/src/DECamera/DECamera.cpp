///////////////////////////////////////////////////////////////////////////////
// FILE:			 DECamera.cpp
// PROJECT:		 Micro-Manager
// SUBSYSTEM:	  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:	Direct Electron camera plugin. 
//
// AUTHOR:		  Sunny Chow, sunny.chow@acm.org, 07/28/2010

#include "DECamera.h"
#include <cstdio>
#include <string>
#include <iostream>
#include <math.h>
#include "MMDevice/ModuleInterface.h"
#include "MMCore/Error.h"
#include "DEExceptions.h"
#include <sstream>
#include <boost/exception/all.hpp>
#include <boost/lexical_cast.hpp>

using namespace std;	

// External names used used by the rest of the system
// to load particular device from the "DECamera.dll" library
const char* g_CameraDeviceName = "Direct Electron Camera";

// Convert error codes into exceptions
struct dev_error : virtual boost::exception { };
void throwIfError(int ret)
{
	if (ret != DEVICE_OK)
		throw dev_error() << boost::errinfo_at_line(ret);
}

const char* g_Property_IpAddress = "IP Address";
const char* g_Property_ReadPort = "Read Port";
const char* g_Property_WritePort = "Write Port";
const char* g_Property_CameraName = "Camera Name";

// Direct Electron Specific Property Labels.  These properties
// are expected for each camera.
const char* g_Property_DE_ImageWidth = "Image Size X";
const char* g_Property_DE_ImageHeight = "Image Size Y";
const char* g_Property_DE_RoiOffsetX = "ROI Offset X";
const char* g_Property_DE_RoiOffsetY = "ROI Offset Y";
const char* g_Property_DE_RoiDimensionX = "ROI Dimension X";
const char* g_Property_DE_RoiDimensionY = "ROI Dimension Y";
const char* g_Property_DE_SensorSizeX = "Sensor Size X";
const char* g_Property_DE_SensorSizeY = "Sensor Size Y";
const char* g_Property_DE_FrameTimeout = "Image Acquisition Timeout (seconds)";
const char* g_Property_DE_AcquisitionMode = "Acquisition Mode";
const char* g_Property_DE_Acquisition_LiveMode = "Live Mode";
const char* g_Property_DE_Acquisition_SingleCapture = "Single Capture";
const char* g_Property_DE_Acquisition_BurstMode = "Burst Mode";

// Optional properties but expected by Micro Manager
const char* g_Property_DE_PixelSizeX = "Pixel Size X";
const char* g_Property_DE_PixelSizeY = "Pixel Size Y";
const char* g_Property_DE_ExposureTime = "Exposure Time (seconds)";
const char* g_Property_DE_BinningX = "Binning X";
const char* g_Property_DE_BinningY = "Binning Y";

#define DEVICE_INCONSISTENT_STATE 5001
const char* g_DECamera_InconsistentStateMessage = "Operation failed. Please try again.";

// Minimal communication timeout
const int DE_minimal_communication_timeout = 30; //60 seconds of minimal timeout to account for network overhead and server response

// Custom error for custom messages
#define DEVICE_CUSTOM_ERROR 36

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

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_CameraDeviceName, "Direct Electron Camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
	{
		// create camera
		return new CDECamera();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CDECamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CDECamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CDECamera::CDECamera() :
	CCameraBase<CDECamera> (),
	initialized_(false),
	initializedProperties_(false),
	readoutUs_(0.0),
	bitDepth_(16),
	sensorSizeX_(0),
	sensorSizeY_(0)
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
	readoutStartTime_ = GetCurrentMMTime();
	//custom error codes/messages	
	SetErrorText(DEVICE_INCONSISTENT_STATE, g_DECamera_InconsistentStateMessage);	

	// Set up the correct proxy->  In the future, we may want to 
	// add some logic to choose what proxy to select.
	this->proxy_ = new DEProtoProxy();

	// Create a properties that needs to be pre-initialized 
	CreateProperty(g_Property_CameraName, "DE-12 DDD Sensor", MM::String, false, NULL, true);
	CreateProperty(g_Property_IpAddress, "127.0.0.1", MM::String, false, NULL, true);
	CreateProperty(g_Property_ReadPort, "48880", MM::Integer, false, NULL, true);
	CreateProperty(g_Property_WritePort, "48879", MM::Integer, false, NULL, true);
	SetPropertyLimits(g_Property_ReadPort, 0, 65535);
	SetPropertyLimits(g_Property_WritePort, 0, 65535);

	exposureEnabled_ = false;
	current_roi_offset_.x = 0; current_roi_offset_.y=0;
	current_binning_factor_ = 1;
}

/**
* CDECamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CDECamera::~CDECamera()
{
	delete proxy_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CDECamera::GetName(char* name) const
{
	// We just return the name we use for referring to this
	// device adapter.
	CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
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
int CDECamera::Initialize()
{
	try {	
		if (initialized_)
		{
			this->proxy_->close();
		}
		// Connect to Direct Electron Server
		// Get the properties first.
		char ip[MM::MaxStrLength];
		GetProperty(g_Property_IpAddress, ip);
		char ports[MM::MaxStrLength];
		GetProperty(g_Property_ReadPort, ports);
		port read = (port)atol(ports);
		GetProperty(g_Property_WritePort, ports);
		port write = (port)atol(ports);
		
		bool success = this->proxy_->connect(ip, read, write);
		if (!success) return DEVICE_NOT_CONNECTED;

		LogMessage("Connected to the Direct Electron Camera server", false);

		// Set the camera we are going to use.
		char name[MM::MaxStrLength];
		GetProperty(g_Property_CameraName, name);	
		if (!this->proxy_->set_CameraName(name))
		{
			SetErrorText(DEVICE_CUSTOM_ERROR, "Requested camera not supported by the server.  (Is the camera name set correctly?)");
			return DEVICE_CUSTOM_ERROR;
		}

		this->camera_name_ = string(name);
		LogMessage("Active Camera Set to " + this->camera_name_, false);

		// Initialize properties after connection is established.
		int nRet = this->InitializeProperties();
		if (nRet != DEVICE_OK) return nRet;

		// Get the property values.
		this->proxy_->get_Property(g_Property_DE_SensorSizeX, this->sensorSizeX_);
		this->proxy_->get_Property(g_Property_DE_SensorSizeY, this->sensorSizeY_);

		if( this->sensorSizeX_ <=0 || this->sensorSizeY_ <=0 )
			return DEVICE_ERR; // do not proceed if the remote camera is not correctly set up

		// Save Default ROI to proxy->
		this->proxy_->set_Property(g_Property_DE_BinningX, 1);
		this->proxy_->set_Property(g_Property_DE_BinningY, 1);
		this->proxy_->set_Property(g_Property_DE_RoiOffsetX, 0);
		this->proxy_->set_Property(g_Property_DE_RoiOffsetY, 0);
		this->proxy_->set_Property(g_Property_DE_RoiDimensionX, this->sensorSizeX_);
		this->proxy_->set_Property(g_Property_DE_RoiDimensionY, this->sensorSizeY_);
		current_roi_offset_.x = 0; current_roi_offset_.y=0; 
				
		// Lastly load optional properties group 1
		pixelSize_.x = 6; //default value for DE-12
		pixelSize_.y = 6; //default value for DE-12	
		try {
			this->proxy_->get_Property(g_Property_DE_PixelSizeX, pixelSize_.x);
			this->proxy_->get_Property(g_Property_DE_PixelSizeY, pixelSize_.y);			
		}
		catch (const CommandException& e){
			// Ignore optional parameters.			
		}		

		// Lastly load optional properties group 2
		exposureTime_ = 0.0;	//default
		try {
			this->proxy_->get_Property(g_Property_DE_ExposureTime, exposureTime_); 
			exposureTime_ = exposureTime_*1000; // convert to millisec
		}
		catch (const CommandException& e){
			// Ignore optional parameters.			
		}
		this->proxy_->set_ImageTimeout((size_t)(exposureTime_/1000*1.5 + DE_minimal_communication_timeout));

		// synchronize all properties
		// --------------------------
		nRet = UpdateStatus();
		if (nRet != DEVICE_OK)
			return nRet;
		
		initialized_ = true;
		
		// initialize image buffer
		this->ResizeImageBuffer();	
	}
	catch (const std::exception& e){
		return BoostToMMError(e);
	}

	
	return DEVICE_OK;
}

int CDECamera::BoostToMMError(const std::exception& e) 
{
	using std::endl;
	std::stringstream errorOut;
	std::stringstream detailedInfo;
	errorOut << "Error encountered in DECamera" << endl;
	
	// Am I a boost system error?
	const boost::system::system_error* se = dynamic_cast<const boost::system::system_error*>(&e);
	if (se != NULL)
	{
		errorOut << "Internal system error has occured." << endl;
		errorOut << " Message: " << se->code().message() << endl;
		errorOut << " Code: " << se->code().value() << endl;
	}
	else 
	{
		errorOut << e.what() << ": ";

		// Format packet if available. 
		std::string const * message = boost::get_error_info<errorMessage>(e);
		DEPacket const * pkt = boost::get_error_info<errorPacket>(e);

		if (message != NULL)
			errorOut << *message <<endl;
		if (pkt != NULL)
			errorOut << "Packet: (" <<  pkt->DebugString() << ") " << endl;
	}
	errorOut << "Please check CoreLog.txt for more information" << endl;

	detailedInfo << "--- Start Exception Diagnostics Information --- " << endl;
	detailedInfo << boost::diagnostic_information(e); 
	detailedInfo << "--- End Exception Diagnostics Information --- " << endl;
	
	SetErrorText(DEVICE_CUSTOM_ERROR, errorOut.str().c_str());
	// May want to use diagnostic_information(errorOut) instead and get rid of manually filling 
	// file and line information in.
	LogMessage(detailedInfo.str(), false);

	return DEVICE_CUSTOM_ERROR;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CDECamera::Shutdown()
{
	initialized_ = false;
	this->proxy_->close(); 
	return DEVICE_OK;
}

/**
 * Continuous sequence acquisition
 */
//int CDECamera::StartSequenceAcquisition(double interval)
//{
//	// By passing LONG_MAX, this is how we let the system know 
//	// we intend to do a continuous sequence acquisition.
//	return StartSequenceAcquisition(LONG_MAX, interval, false);
//}

/**
 * Used for both continuous and single sequence acquisition
 * If numImages == 1, assume we're only capturing a single shot.
 */
int CDECamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

	if (IsCapturing())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq((CCameraBase<CDECamera>*)this);
	if (ret != DEVICE_OK)
		return ret;

	// Get the final width and height of the image.
	try { 
		// Live_Mode.  Check for 0.0 is based on how MMStudioMainFrame.java initiates the live mode
		// acquisition.
		if (interval_ms == 0.0 && this->HasProperty(g_Property_DE_AcquisitionMode)) {
			this->SetProperty(g_Property_DE_AcquisitionMode, g_Property_DE_Acquisition_LiveMode);
		}
	 
	    this->SetupCapture_();
	}

	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}	

	 // Start thread.
	thd_->Start(numImages,interval_ms);
	stopOnOverflow_ = stopOnOverflow;
	return DEVICE_OK;
}


/**
 * Used for stopping a live sequence and resetting the acquisition mode to be Snapshot mode
 */
int CDECamera::StopSequenceAcquisition()
{

	try {
		// Call base method to finish up acquisition.
		CCameraBase<CDECamera>::StopSequenceAcquisition();

		if (this->HasProperty(g_Property_DE_AcquisitionMode))
			this->SetProperty(g_Property_DE_AcquisitionMode, g_Property_DE_Acquisition_SingleCapture);
	}
	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}
	return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CDECamera::SnapImage()
{
	LogMessage("SnapImage...", false);

	try {
		// Snap Image mode.  (Note, multi-d also uses this!, we'll need a way to distinguish the two
		// if we want to use Burst Mode.)
		if (this->HasProperty(g_Property_DE_AcquisitionMode))
			this->SetProperty(g_Property_DE_AcquisitionMode, g_Property_DE_Acquisition_SingleCapture);

		this->SetupCapture_();
		this->SnapSingleFrame_();
	}
	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}	

	return DEVICE_OK;
}

void CDECamera::SetupCapture_()
{
	LogMessage("SetupCapture_", false);
	// Get the final width and height of the image.
	if (HasProperty(g_Property_DE_FrameTimeout))
	{
		 MM::PropertyType type;
		 GetPropertyType(g_Property_DE_FrameTimeout, type);
		 if (type == MM::Float)
		 {		
			  double frameTime = 0; 
			  this->proxy_->get_Property(g_Property_DE_FrameTimeout, frameTime);
			  this->proxy_->set_ImageTimeout(abs((int)ceil(frameTime))+DE_minimal_communication_timeout);
		 }
	}
}

void CDECamera::SnapSingleFrame_()
{
	boost::lock_guard<boost::mutex> lock(this->acqmutex_);
	static int callCounter = 0;
	++callCounter;

	LogMessage("SnapSingleFrame...", false);

	MM::MMTime startTime = GetCurrentMMTime();
	
	 this->ResizeImageBuffer(); // reallocates as necessary.
	this->proxy_->get_Image( img_.GetPixelsRW(), this->GetImageBufferSize() );

	double exp = 0;
	while (GetCurrentMMTime() - startTime < MM::MMTime(exp)) {}
	readoutStartTime_ = GetCurrentMMTime();
}

/** 
 * Overriding so we can distinguish snapping a single frame and doing a continuous sequence 
 * acquisition.
 */
int CDECamera::ThreadRun (void)
{
	// Safely snaps a single frames or returns a Micro Manager
	// error.
	try {
		this->SnapSingleFrame_();
	}
	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}	

	return this->InsertImage();
}

// Create properties that can be initialized later.  Called only once.
int CDECamera::InitializeProperties()
{
	if (this->initializedProperties_) return DEVICE_OK;
	initializedProperties_ = true;

	int device_status = DEVICE_OK;
	try {
		//// Local Properties.
		// Description
		throwIfError(CreateProperty(MM::g_Keyword_Description, "Direct Electron Camera", MM::String, true));

		// CameraName
		throwIfError(CreateProperty(MM::g_Keyword_CameraName, camera_name_.c_str(), MM::String, true));

		// CameraID
		//throwIfError(CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true));

		// binning
		CPropertyAction *pAct = new CPropertyAction (this, &CDECamera::OnBinning); 
		CreateProperty(MM::g_Keyword_Binning, "", MM::Integer, false, pAct);
		
		//// Remote Properties.
		// Get all properties from the camera.
		std::vector<string> properties;
		try {
			this->proxy_->get_Properties(properties);	
			
			for (std::vector<string>::iterator it = properties.begin(); it != properties.end(); it++)
			{
				PropertyHelper propSettings;
				this->proxy_->get_PropertySettings(*it, propSettings);
				// Set Property.
				this->SetupProperty(*it, propSettings);		  
			}
		}
		catch (const std::exception& e){
			return BoostToMMError(e);
		}

		SetAllowedBinning();		
	}
	catch(boost::exception& e)
	{
		int const* mi = boost::get_error_info<boost::errinfo_at_line>(e);
		device_status = (mi) ? *mi : DEVICE_ERR;
	}


	return device_status;
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
const unsigned char* CDECamera::GetImageBuffer()
{
	MM::MMTime curTime = GetCurrentMMTime();
	MM::MMTime readoutTime(readoutUs_);
	while (readoutTime > (curTime - readoutStartTime_)) {}
	return img_.GetPixels();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CDECamera::GetImageWidth() const
{
	return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CDECamera::GetImageHeight() const
{
	return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CDECamera::GetImageBytesPerPixel() const
{
	return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CDECamera::GetBitDepth() const
{
	return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CDECamera::GetImageBufferSize() const
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
* appropriately cropping each frame.  Values are set with binning as 1.
* This demo implementation ignores the position coordinates and just crops the buffer.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int CDECamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	boost::lock_guard<boost::mutex> lock(this->acqmutex_);
	try {			
			IntPair new_roi_offset_; 
			//NOTE: x, y is relative to the existing ROI, instead of the original sensor. 
			new_roi_offset_.x = current_roi_offset_.x + x*current_binning_factor_; //NOTE: x, y is relative to the existing ROI, instead of the original sensor. 
			new_roi_offset_.y = current_roi_offset_.y + y*current_binning_factor_; // if binning is enabled, the offset is calculated based from binned image						
			this->proxy_->set_Property(g_Property_DE_RoiOffsetX, (int&)new_roi_offset_.x);
			this->proxy_->set_Property(g_Property_DE_RoiOffsetY, (int&)new_roi_offset_.y);
			this->proxy_->set_Property(g_Property_DE_RoiDimensionX, (int&)xSize*current_binning_factor_); //binning needs to be considered as well
			this->proxy_->set_Property(g_Property_DE_RoiDimensionY, (int&)ySize*current_binning_factor_);

			int xo, yo, xd, yd; 
			this->proxy_->get_Property(g_Property_DE_RoiOffsetX, xo);
			this->proxy_->get_Property(g_Property_DE_RoiOffsetY, yo);
			this->proxy_->get_Property(g_Property_DE_RoiDimensionX, xd);
			this->proxy_->get_Property(g_Property_DE_RoiDimensionY, yd);

			current_roi_offset_.x = xo; //Store the current ROI. 
			current_roi_offset_.y = yo;
	}
	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}
 
	return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.  Return ROI in terms of the current binning size.
*/
int CDECamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	try {
		this->proxy_->get_Property(g_Property_DE_RoiOffsetX, (int&)x);
		this->proxy_->get_Property(g_Property_DE_RoiOffsetY, (int&)y);
		this->proxy_->get_Property(g_Property_DE_RoiDimensionX, (int&)xSize);
		this->proxy_->get_Property(g_Property_DE_RoiDimensionY, (int&)ySize);
	 }
	catch (const std::exception& e)
	{
		return BoostToMMError(e);
	}
	return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CDECamera::ClearROI()
{
	
	try {
		// Set to the maximum ROI when binning is 1.  When SnapImage occurs, a 
		// Synchronize will occur causing the REAL roi dimensions to be returned.
		this->proxy_->set_Property(g_Property_DE_RoiOffsetX, 0);
		this->proxy_->set_Property(g_Property_DE_RoiOffsetY, 0);
		this->proxy_->set_Property(g_Property_DE_RoiDimensionX, this->sensorSizeX_);
		this->proxy_->set_Property(g_Property_DE_RoiDimensionY, this->sensorSizeY_);
		current_roi_offset_.x = 0; current_roi_offset_.y = 0; 
	}
	catch (const std::exception& e){
		return BoostToMMError(e);
	}

	return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CDECamera::GetExposure() const
{
	if (exposureEnabled_ == false) return 0.0;
	double doubleTemp=0.0;
	bool retval = false;
	try {
		retval = this->proxy_->get_Property(g_Property_DE_ExposureTime, doubleTemp);
	}
	catch (const std::exception& e){
	}
	if(retval){
		return doubleTemp*1000; //get latest value from the server (cannot update local copy as the method is const)
	} else
		return exposureTime_; //return local copy
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CDECamera::SetExposure(double exp)
{	
	if (exposureEnabled_ == false) return;
	if(IsCapturing()) return; // do not continue when camera is capturing (live mode)
	this->exposureTime_ = exp; //store in the local variable
	//try to set exposure on remote server
	double doubleTemp = exp/1000; 
	try {		
		if(this->proxy_->set_Property(g_Property_DE_ExposureTime, doubleTemp)){
			doubleTemp = 0.0;
			//verify setting from the server
			if(this->proxy_->get_Property(g_Property_DE_ExposureTime, doubleTemp)){
				this->exposureTime_ = doubleTemp*1000; //update local variable
				this->proxy_->set_ImageTimeout((size_t)(doubleTemp*1.5 + DE_minimal_communication_timeout));
			}
		}
	}
	// Will fail silently with a message in the log
	// as Micro manager does not allow us to return an error here.
	catch (const std::exception& e){
		BoostToMMError(e);
	}
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CDECamera::GetBinning() const
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
int CDECamera::SetBinning(int binFactor)
{  
  return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}

/**
 * Sets the allowed binning values.
 */
int CDECamera::SetAllowedBinning() 
{
	// Server has already set the values.
	if (this->HasProperty(MM::g_Keyword_Binning) && this->GetNumberOfPropertyValues(MM::g_Keyword_Binning) > 0)
		return DEVICE_OK;

	vector<string> binValues;
	// Use values from Binning X
	LogMessage("Setting Allowed Binning settings", true);
	return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}


///////////////////////////////////////////////////////////////////////////////
// CDECamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CDECamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	try {
		if(IsCapturing())
			return DEVICE_CAMERA_BUSY_ACQUIRING; // do not continue when camera is capturing (live mode)

		if ( MM::AfterSet == eAct)
		{
			// the user just set the new value for the property, so we have to
			// apply this value to the 'hardware'.
			long binFactor;
			pProp->Get(binFactor);
			current_binning_factor_ = binFactor;

			SetProperty(g_Property_DE_BinningX, CDeviceUtils::ConvertToString(binFactor));
			SetProperty(g_Property_DE_BinningY, CDeviceUtils::ConvertToString(binFactor));
		}
		else if (MM::BeforeGet == eAct)
		{
			// the user is requesting the current value for the property, so
			// either ask the 'hardware' or let the system return the value
			// cached in the property.
			long binFactor;
			this->proxy_->get_Property(g_Property_DE_BinningX, (int&)binFactor);
			this->proxy_->get_Property(g_Property_DE_BinningY, (int&)binFactor);
			
			current_binning_factor_ = binFactor;
			pProp->Set((long)binFactor);
		}
	}
	catch (const std::exception& e){
		return BoostToMMError(e);
	}
	return ret; 
}

int CDECamera::OnProperty(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// Check to see if I've encountered this property before.  If not, map the 
	// the propertybase to the previous label.  Currently lastLabel_ is 
	// set by SetupProperty.  SetupProperty calls ApplyProperty which triggers
	// the calling of this function.
	if (reverseLookup_.find(pProp) == reverseLookup_.end())
	{
		this->reverseLookup_[pProp] = this->lastLabel_;
		return DEVICE_OK;
	}

	// Code for the actual handler.
	int ret = DEVICE_OK;
	
	string name = reverseLookup_[pProp];
	string strTemp;
	double dblTemp;
	float flTemp;
	int intTemp;
	long longTemp;
	try {
		if(IsCapturing())
			return DEVICE_CAMERA_BUSY_ACQUIRING; // do not continue when camera is capturing (live mode)

		if (eAct == MM::AfterSet)
		{
			switch (pProp->GetType())
			{
			case MM::String:
				pProp->Get(strTemp);
				this->proxy_->set_Property(name, strTemp);
				break;
			case MM::Float:
				pProp->Get(dblTemp);
				flTemp = dblTemp;
				this->proxy_->set_Property(name, flTemp);
				break;
			case MM::Integer:
				pProp->Get(longTemp);
				intTemp = (int)longTemp;
				this->proxy_->set_Property(name, intTemp);
				break;
			default:
				return DEVICE_INVALID_PROPERTY_TYPE;
			}
		}
		else if (eAct == MM::BeforeGet)
		{
			switch (pProp->GetType())
			{
			case MM::String:
				this->proxy_->get_Property(name, strTemp);
				pProp->Set(strTemp.c_str());
				break;
			case MM::Float:
				this->proxy_->get_Property(name, flTemp);
				pProp->Set(flTemp);
				break;
			case MM::Integer:
				this->proxy_->get_Property(name, intTemp);
				pProp->Set((long)intTemp);
				break;
			default:
				return DEVICE_INVALID_PROPERTY_TYPE;
			}
		}
	}
	catch (const std::exception& e){
		return BoostToMMError(e);
	}
	return ret;
}


/**
 * Helper function that's type safe to set property values.
 * Uses OnProperty logic to do the actual setting.
 */
int CDECamera::SetPropertyVal(string name, int val)
{
	MM::PropertyType pt;
	if (GetPropertyType(name.c_str(), pt) != DEVICE_OK)
	{
		SetErrorText(DEVICE_CUSTOM_ERROR, "Property was not found.");
		return DEVICE_CUSTOM_ERROR;
	}

	if (pt != MM::Integer)
	{
		SetErrorText(DEVICE_CUSTOM_ERROR, "Property was of the wrong type.");
		return DEVICE_CUSTOM_ERROR;
	}

	int nRet = 0;
	try {
		nRet = SetProperty(name.c_str(), boost::lexical_cast<string>(val).c_str());
	}
	catch (const std::exception &e)
	{
		return BoostToMMError(e);
	}
	return nRet;
}


/**
 * Helper function that's type safe to retrieve property values.
 * Uses OnProperty logic to do the actual setting.
 */
int CDECamera::GetPropertyVal(string name, int& out)
{
	MM::PropertyType pt;
	if (GetPropertyType(name.c_str(), pt) != DEVICE_OK)
	{
			SetErrorText(DEVICE_CUSTOM_ERROR, "Property was not found.");
			return DEVICE_CUSTOM_ERROR;
	}

	if (pt != MM::Integer)
	{
			SetErrorText(DEVICE_CUSTOM_ERROR, "Property was of the wrong type.");
			return DEVICE_CUSTOM_ERROR;
	}

	int nRet = 0;
	try {
		char buf[MM::MaxStrLength];
		if ((nRet = GetProperty(name.c_str(), buf)) == DEVICE_OK)
			out = boost::lexical_cast<int>(buf);
	}
	catch (const std::exception &e)
	{
		return BoostToMMError(e);
	}
	return nRet;
}

///////////////////////////////////////////////////////////////////////////////
// Private CDECamera methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CDECamera::ResizeImageBuffer()
{
	int width = 0;
	int height = 0;

	this->proxy_->get_Property(g_Property_DE_ImageWidth, width);
	this->proxy_->get_Property(g_Property_DE_ImageHeight, height);

	img_.Resize(width, height, this->bitDepth_/8);

	return DEVICE_OK;
}

double CDECamera::GetNominalPixelSizeUm() const
{
	return pixelSize_.x;
}

double CDECamera::GetPixelSizeUm() const
{
	return pixelSize_.x  * this->GetBinning();
}

void CDECamera::SetupProperty(string label, PropertyHelper settings)
{
	CPropertyAction* pAct = new CPropertyAction(this, &CDECamera::OnProperty);
	vector<string> values;
	boost::tuple<double, double> range;
	
	// Keep list of special case properties.  If so enable and move on.
	if ( label.compare(g_Property_DE_ExposureTime) == 0 )
	{
		exposureEnabled_ = true;		
	}

	// Check to see if property already exists.  if so, ignore.
	if (HasProperty(label.c_str()))
		return; 
	
	LogMessage(label.c_str(), true);	 

	switch (settings.GetType())
	{
	case PropertyHelper::Allow_All:		
		CreateProperty(label.c_str(), "", convertType(settings.GetProperty()), false, pAct);			
		break;
	case PropertyHelper::Range:
		CreateProperty(label.c_str(), "", convertType(settings.GetProperty()), false, pAct);			
		range = settings.GetRange();		
		SetPropertyLimits(label.c_str(), range.get<0>(), range.get<1>());			
		break;
	case PropertyHelper::Set:
		CreateProperty(label.c_str(), "", convertType(settings.GetProperty()), false, pAct);			
		settings.GetSet(values);		
		SetAllowedValues(label.c_str(), values);		
		break;
	case PropertyHelper::ReadOnly:		
		CreateProperty(label.c_str(), "", convertType(settings.GetProperty()), true, pAct);			
		break;
	default: 
		return;
	}

	this->lastLabel_ = label;
	this->ApplyProperty(label.c_str());

	return;
}

MM::PropertyType CDECamera::convertType(const PropertyHelper::PropertyType& type)
{
	switch (type)
	{
		case PropertyHelper::Float:
			return MM::Float;	
		case PropertyHelper::String:
			return MM::String;	
		case PropertyHelper::Integer:
			return MM::Integer;	
	}
	return MM::Undef;
}

int CDECamera::IsExposureSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}
