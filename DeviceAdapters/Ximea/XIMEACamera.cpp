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

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/***********************************************************************
* List all supported hardware devices here
*/
MODULE_API void InitializeModuleData()
{
	DWORD numDevices = 0;
	XI_RETURN ret = xiGetNumberDevices(&numDevices);
	if(ret != XI_OK)
	{
		// camera enumeration failed
		return;
	}

	for(DWORD i = 0; i < numDevices; i++)
	{
		char dev_sn[DEV_SN_LEN] = "";
		ret = xiGetDeviceInfoString(i, XI_PRM_DEVICE_SN, dev_sn, DEV_SN_LEN);
		if(ret == XI_OK)
		{
			RegisterDevice(dev_sn, MM::CameraDevice, "XIMEA camera adapter");
		}
	}
}

//***********************************************************************

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == NULL)
	{
		return NULL;
	}

	DWORD numDevices = 0;
	XI_RETURN ret = xiGetNumberDevices(&numDevices);
	if(ret != XI_OK)
	{
		// camera enumeration failed
		return NULL;
	}
	if(numDevices == 0)
	{
		// no device connected
		return NULL;
	}

	// create new camera object for selected device
	for(DWORD i = 0; i < numDevices; i++)
	{
		char camera_sn[DEV_SN_LEN] = "";
		ret = xiGetDeviceInfoString(i, XI_PRM_DEVICE_SN, camera_sn, DEV_SN_LEN);
		if(ret != XI_OK)
		{
			// reading of device info failed
			continue;
		}
		if (strcmp(deviceName, camera_sn) == 0)
		{
			return new XimeaCamera(deviceName);
		}
	}
	return NULL;
}

//***********************************************************************

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// XimeaCamera implementation
/***********************************************************************
* XimeaCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
XimeaCamera::XimeaCamera(const char* name) :
	camera(NULL),
	readoutStartTime_(0),
	sequenceStartTime_(0),
	seq_thd_(NULL),
	device_name(name),
	initialized_(false),
	img_(NULL),
	nComponents_(1),
	bytesPerPixel_(1),
	imageCounter_(0),
	stopOnOverflow_(false),
	isAcqRunning(false),
	roiX_(0),
	roiY_(0)
{
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();
}

/***********************************************************************
* XimeaCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.s
*/
XimeaCamera::~XimeaCamera()
{
	if(camera)
	{
		delete camera;
		camera = NULL;
	}
}

/***********************************************************************
* Obtains device name.
* Required by the MM::Device API.
*/
void XimeaCamera::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, device_name.c_str());
}

/***********************************************************************
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int XimeaCamera::Initialize()
{
	if (initialized_)
	{
		return DEVICE_OK;
	}

	// -------------------------------------------------------------------------------------
	// Open camera
	if(camera == NULL)
	{
		camera = new xiAPIplus_Camera();
		if(camera == NULL)
		{
			LogMessage("Failed to allocate XiSequenceThread()");
			return DEVICE_OUT_OF_MEMORY;
		}
	}

	try
	{
		// open camera
		camera->OpenBySN(device_name.c_str());
		initialized_ = true;

		// set default exposure
		//camera->SetXIAPIParamInt(XI_PRM_EXPOSURE, DEF_EXP_TIME);
		
		// clear current parameter list
		cam_params.clear();
		
		// load XML manifest
		camera->LoadCameraManifest();
		ParseCameraManifest(camera->GetCameraManifest());
		camera->FreeCameraManifest();
		CreateCameraProperties();

      // create binning property needed by Micro-Manager GUI
      CPropertyAction *pAct = new CPropertyAction (this, &XimeaCamera::OnBinning);
	   int ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
      assert(ret == DEVICE_OK);

      int maxBin = 0;
	   vector<string> binningValues;
      camera->GetXIAPIParamInt(XI_PRM_DOWNSAMPLING XI_PRM_INFO_MAX, &maxBin);
	   for(int i = 1; i <= maxBin; i++) {
         try {
            camera->SetXIAPIParamInt(XI_PRM_DOWNSAMPLING, i); // will throw exception if it fails
			   char buf[16];
			   sprintf(buf, "%d", i);
			   binningValues.push_back(buf);
         } catch(xiAPIplus_Exception exc) { /* No need to log or take action */}
      }
      camera->SetXIAPIParamInt(XI_PRM_DOWNSAMPLING, 1);
	   ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);
      assert(ret == DEVICE_OK);

		// prepare internal image buffer
		int width = camera->GetXIAPIParamInt(XI_PRM_WIDTH);
		int height = camera->GetXIAPIParamInt(XI_PRM_HEIGHT);
	
		img_ = new ImgBuffer(width, height, bytesPerPixel_);
		if(img_ == NULL)
		{
			LogMessage("Failed to allocate ImgBuffer");
			return DEVICE_OUT_OF_MEMORY;
		}
		ResizeImageBuffer();

		// set acquisition timeout
		camera->SetNextImageTimeout_ms(DEFAULT_ACQ_TOUT_MS);
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "Initialize(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_ERR;
	}
	catch(...)
	{
		LogMessage("Initialize(): Unknown exception");									\
		return DEVICE_ERR;
	}

	// -------------------------------------------------------------------------------------
	// call the base class method to set-up default error codes/messages
	seq_thd_ = new XiSequenceThread(this);
	if(seq_thd_ == NULL)
	{
		LogMessage("Failed to allocate XiSequenceThread()");
		return DEVICE_OUT_OF_MEMORY;
	}

	//-------------------------------------------------------------------------------------
	// synchronize all properties
	return UpdateStatus();
}

/***********************************************************************
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int XimeaCamera::Shutdown()
{
	int ret = DEVICE_OK;
	if(initialized_)
	{
		try
		{
			camera->StopAcquisition();
			camera->Close();
			cam_params.clear();
		}
		catch(xiAPIplus_Exception exc)
		{
			string err_msg = "Shutdown(): " + exc.GetDescription();
			LogMessage(err_msg);
			ret = DEVICE_ERR;
		}
		catch(...)
		{
			LogMessage("Shutdown(): Unknown exception");
			return DEVICE_ERR;
		}

		if(img_)
		{
			delete img_;
			img_ = NULL;
		}

		if(seq_thd_)
		{
			delete seq_thd_;
			seq_thd_ = NULL;
		}
	}
	initialized_ = false;
	return ret;
}

/***********************************************************************
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int XimeaCamera::SnapImage()
{
	// start camera acquisition
	try
	{
		if (!isAcqRunning)
		{
			camera->StartAcquisition();
		}

		camera->GetNextImage(&image);

		if (image.GetPadding_X() == 0)
		{
			img_->SetPixels(image.GetPixels());
		}
		else
		{
			img_->SetPixelsPadded(image.GetPixels(), image.GetPadding_X());
		}

		// store timestamp dataS
		readoutStartTime_.sec_ = image.GetTimeStampSec();
		readoutStartTime_.uSec_ = image.GetTimeStampUSec();

		if (!isAcqRunning)
		{
			camera->StopAcquisition();
		}
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "SnapImage(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_SNAP_IMAGE_FAILED;
	}
	catch(...)
	{
		LogMessage("SnapImage(): Unknown exception");
		return DEVICE_SNAP_IMAGE_FAILED;
	}

	// use time of first successfully captured frame for sequence start
	if (sequenceStartTime_.sec_ == 0 && sequenceStartTime_.uSec_ == 0)
	{
		sequenceStartTime_ = readoutStartTime_;
	}

	return DEVICE_OK;
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
const unsigned char* XimeaCamera::GetImageBuffer()
{
	return const_cast<unsigned char*>(img_->GetPixels());
}

/***********************************************************************
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned XimeaCamera::GetImageWidth() const
{
	return img_->Width();
}

/***********************************************************************
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned XimeaCamera::GetImageHeight() const
{
	return img_->Height();
}

/***********************************************************************
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned XimeaCamera::GetImageBytesPerPixel() const
{
	return bytesPerPixel_;
}

/***********************************************************************
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned XimeaCamera::GetBitDepth() const
{
	int bitDepth = 0;
	try
	{
		bitDepth = camera->GetXIAPIParamInt(XI_PRM_IMAGE_DATA_BIT_DEPTH);
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "SnapImage(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_SNAP_IMAGE_FAILED;
	}
	catch(...)
	{
		LogMessage("SnapImage(): Unknown exception");
		return DEVICE_SNAP_IMAGE_FAILED;
	}
	return bitDepth;
}

/***********************************************************************
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long XimeaCamera::GetImageBufferSize() const
{
	return img_->Width() * img_->Height() * bytesPerPixel_;
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
int XimeaCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	if (xSize == 0 && ySize == 0)
	{
		// effectively clear ROI
		return ClearROI();
	}
	else
	{
		try
		{
			// apply ROI
			int width_inc = camera->GetXIAPIParamInt(XI_PRM_WIDTH XI_PRM_INFO_INCREMENT);
			int width = xSize - (xSize % width_inc);

			int height_inc = camera->GetXIAPIParamInt(XI_PRM_HEIGHT XI_PRM_INFO_INCREMENT);
			int height = ySize - (ySize % height_inc);

			int off_x_inc = camera->GetXIAPIParamInt(XI_PRM_OFFSET_X XI_PRM_INFO_INCREMENT);
			int off_x = x - (x % off_x_inc);

			int off_y_inc = camera->GetXIAPIParamInt(XI_PRM_OFFSET_Y XI_PRM_INFO_INCREMENT);
			int off_y = y - (y % off_y_inc);

			camera->SetXIAPIParamInt(XI_PRM_WIDTH, width);
			camera->SetXIAPIParamInt(XI_PRM_HEIGHT, height);
			camera->SetXIAPIParamInt(XI_PRM_OFFSET_X, off_x);
			camera->SetXIAPIParamInt(XI_PRM_OFFSET_Y, off_y);
			img_->Resize(width, height);
			roiX_ = off_x;
			roiY_ = off_y;
			UpdateRoiParams();
		}
		catch(xiAPIplus_Exception exc)
		{
			string err_msg = "SetROI(): " + exc.GetDescription();
			LogMessage(err_msg);
			return DEVICE_INVALID_PROPERTY_VALUE;
		}
		catch(...)
		{
			LogMessage("SetROI(): Unknown exception");
			return DEVICE_ERR;
		}
	}
	return DEVICE_OK;
}

/***********************************************************************
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int XimeaCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	int width = 0, height = 0, offx = 0, offy = 0;
	try
	{
		width = camera->GetXIAPIParamInt(XI_PRM_WIDTH);
		height = camera->GetXIAPIParamInt(XI_PRM_HEIGHT);
		offx = camera->GetXIAPIParamInt(XI_PRM_OFFSET_X);
		offy = camera->GetXIAPIParamInt(XI_PRM_OFFSET_Y);
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "GetROI(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_INVALID_PROPERTY_VALUE;
	}
	catch(...)
	{
		LogMessage("GetROI(): Unknown exception");
		return DEVICE_ERR;
	}

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
int XimeaCamera::ClearROI()
{
	try
	{
		int width = 0, height = 0;
		camera->SetXIAPIParamInt(XI_PRM_OFFSET_X, 0);
		camera->SetXIAPIParamInt(XI_PRM_OFFSET_Y, 0);
		camera->GetXIAPIParamInt(XI_PRM_WIDTH XI_PRM_INFO_MAX, &width);
		camera->GetXIAPIParamInt(XI_PRM_HEIGHT XI_PRM_INFO_MAX, &height);

		camera->SetXIAPIParamInt(XI_PRM_WIDTH, width);
		camera->SetXIAPIParamInt(XI_PRM_HEIGHT, height);
		UpdateRoiParams();
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "GetROI(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_INVALID_PROPERTY_VALUE;
	}
	catch(...)
	{
		LogMessage("GetROI(): Unknown exception");
		return DEVICE_ERR;
	}

	ResizeImageBuffer();
	return DEVICE_OK;
}

/***********************************************************************
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double XimeaCamera::GetExposure() const
{
	double exposure_ms = 0;
	try
	{
		int exposure_us = 0;
		camera->GetXIAPIParamInt(XI_PRM_EXPOSURE, &exposure_us);
		exposure_ms = (double) exposure_us / 1000.0;
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "GetExposure(): " + exc.GetDescription();
		LogMessage(err_msg);
	}
	catch(...)
	{
		LogMessage("GetExposure(): Unknown exception");
	}
	return exposure_ms;
}

/***********************************************************************
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void XimeaCamera::SetExposure(double exp)
{
	try
	{
		int exposure_us = (int) (exp * 1000.0);
		camera->SetXIAPIParamInt(XI_PRM_EXPOSURE, exposure_us);
		UpdateProperty("Exposure time");
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "SetExposure(): " + exc.GetDescription();
		LogMessage(err_msg);
	}
	catch(...)
	{
		LogMessage("SetExposure(): Unknown exception");
	}
}

/***********************************************************************
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int XimeaCamera::GetBinning() const
{
	int binning = 1;
	try
	{
		camera->GetXIAPIParamInt(XI_PRM_DOWNSAMPLING, &binning);		
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "GetBinning(): " + exc.GetDescription();
		LogMessage(err_msg);
	}
	catch(...)
	{
		LogMessage("GetBinning(): Unknown exception");
	}
	return binning;
}

/***********************************************************************
* Sets binning factor.
* Required by the MM::Camera API.
*/
int XimeaCamera::SetBinning(int binF)
{
	try
	{
		camera->SetXIAPIParamInt(XI_PRM_DOWNSAMPLING, binF);
		UpdateStatus();
		ResizeImageBuffer();
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "SetBinning(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_ERR;
	}
	catch(...)
	{
		LogMessage("SetBinning(): Unknown exception");
		return DEVICE_ERR;
	}

	return DEVICE_OK;
}
//////////////////////////////////////////////////////////////

bool XimeaCamera::SupportsMultiROI()
{
	/*TODO
	Add MultiROI support
	*/
	return false;
}

bool XimeaCamera::IsMultiROISet()
{
	/*TODO
	Add MultiROI support
	*/
	return false;
}

int XimeaCamera::GetMultiROICount(unsigned& /* count */)
{
	/*TODO
	Add MultiROI support
	*/
	return DEVICE_ERR;
}

int XimeaCamera::SetMultiROI(const unsigned* /*xs */, const unsigned* /* ys */, const unsigned* /* widths */, 
            const unsigned* /* heights */, unsigned /* numROIs */)
{
	/*TODO
	Add MultiROI support
	*/
	return DEVICE_ERR;
}

int XimeaCamera::GetMultiROI(unsigned*  /*xs */, unsigned* /* ys */, unsigned* /* widths */, unsigned* /* heights */, 
            unsigned* /* length */)
{
	/*TODO
	Add MultiROI support
	*/
	return DEVICE_ERR;
}

/***********************************************************************
* Required by the MM::Camera API
* Please implement this yourself and do not rely on the base class implementation
* The Base class implementation is deprecated and will be removed shortly
*/
int XimeaCamera::StartSequenceAcquisition(double interval)
{
	return StartSequenceAcquisition(LONG_MAX, interval, false);
}

/***********************************************************************
* Stop and wait for the Sequence thread finished
*/
int XimeaCamera::StopSequenceAcquisition()
{
	isAcqRunning = false;
	// stop image acquisition
	try
	{
		camera->StopAcquisition();
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "StopSequenceAcquisition(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_ERR;
	}
	catch(...)
	{
		LogMessage("StopSequenceAcquisition(): Unknown exception");
		return DEVICE_ERR;
	}

	if (!seq_thd_->IsStopped())
	{
		seq_thd_->Stop();
		seq_thd_->wait();
	}
	return DEVICE_OK;
}

/***********************************************************************
* Simple implementation of Sequence Acquisition
* A sequence acquisition should run on its own thread and transport new images
* coming of the camera into the MMCore circular buffer.
*/
int XimeaCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	if (IsCapturing())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	// reset camera timestamp for cameras that support it
	try
	{
		camera->SetXIAPIParamInt(XI_PRM_TS_RST_SOURCE, XI_TS_RST_SRC_SW);
		camera->SetXIAPIParamInt(XI_PRM_TS_RST_MODE, XI_TS_RST_ARM_ONCE);

	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "Timestamp reset error: " + exc.GetDescription();
		LogMessage(err_msg);
	}
	catch(...)
	{
		LogMessage("Timestamp reset error: Unknown exception");
	}

	// start image acquisition
	try
	{
		camera->StartAcquisition();
		isAcqRunning = true;
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "StartSequenceAcquisition(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_ERR;
	}
	catch(...)
	{
		LogMessage("StartSequenceAcquisition(): Unknown exception");
		return DEVICE_ERR;
	}

	sequenceStartTime_.sec_ = 0;
	sequenceStartTime_.uSec_ = 0;

	imageCounter_ = 0;
	seq_thd_->Start( numImages, interval_ms);
	stopOnOverflow_ = stopOnOverflow;
	return DEVICE_OK;
}

/***********************************************************************
* Inserts Image and MetaData into MMCore circular Buffer
*/
int XimeaCamera::InsertImage()
{
	int ret = DEVICE_OK;

	MM::MMTime timeStamp = readoutStartTime_;
	char label[MM::MaxStrLength];
	this->GetLabel(label);

	// Important:  metadata about the image are generated here:
	Metadata md;
	double exp_time = (double)image.GetExpTime()/1000;
	md.put(MM::g_Keyword_Meatdata_Exposure, CDeviceUtils::ConvertToString(exp_time));
	md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
	md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
	md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
	md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
	md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString((long) roiX_));
	md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString((long) roiY_));
	md.put(MM::g_Keyword_Gain, CDeviceUtils::ConvertToString(image.GetGain()));
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
	}
	else
	{
		return ret;
	}
}

/***********************************************************************
* Do actual capturing
* Called from inside the thread
*/
int XimeaCamera::CaptureImage (void)
{
	int ret = DEVICE_ERR;

	ret = SnapImage();
	if (ret != DEVICE_OK)
	{
		return ret;
	}

	ret = InsertImage();
	return ret;
};

/***********************************************************************
* called from the thread function before exit
*/
void XimeaCamera::OnThreadExiting() throw()
{
	try
	{
		LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
		GetCoreCallback()?GetCoreCallback()->AcqFinished(this,0) : DEVICE_OK;
	}
	catch(...)
	{
		LogMessage(g_Msg_EXCEPTION_IN_ON_THREAD_EXITING, false);
	}
}

//***********************************************************************

bool XimeaCamera::IsCapturing()
{
	return !seq_thd_->IsStopped();
}

///////////////////////////////////////////////////////////////////////////////
// XimeaCamera Action handlers
///////////////////////////////////////////////////////////////////////////////


int XimeaCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
	if (eAct == MM::AfterSet)
	{
		long binSize;
		pProp->Get(binSize);
		return SetBinning(binSize);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)GetBinning());
	}

	return ret;
}

int XimeaCamera::OnPropertyChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// acquisition timeout is not among standard parameters
	if(pProp->GetName() == CAM_PARAM_ACQ_TIMEOUT)
	{
		if (eAct == MM::AfterSet)
		{
			long value = 0;
			pProp->Get(value);
			camera->SetNextImageTimeout_ms(value);
		}
		else if(eAct == MM::BeforeGet)
		{
			long value = camera->GetNextImageTimeout_ms();
			pProp->Set(value);
		}
		return DEVICE_OK;
	}

	// search camera parameters
	XimeaParam* param = GetXimeaParam(pProp->GetName());
	if(param == NULL)
	{
		return DEVICE_INVALID_PROPERTY;
	}

	string param_name = param->GetXiParamName();
	try
	{
		if (eAct == MM::AfterSet)
		{
			switch(param->GetParamType())
			{
			case type_command:
			case type_int:
				{
					long value = 0;
					pProp->Get(value);
					// exposure time values must be devided by 1000
					if(param->GetXiParamName() == XI_PRM_EXPOSURE ||
						param->GetXiParamName() == XI_PRM_AE_MAX_LIMIT)
					{
						value = value * 1000;
					}
					camera->SetXIAPIParamInt(param_name.c_str(), (int) value);
				}
				break;
			case type_float:
				{
					double value = 0;
					pProp->Get(value);
					camera->SetXIAPIParamFloat(param_name.c_str(), (float) value);
				}
				break;
			case type_enum:
				{
					try
					{
						string value = "";
						pProp->Get(value);
						int enum_value = param->GetEnumValue(value);
						camera->SetXIAPIParamInt(param_name.c_str(), enum_value);
					}
					catch(std::exception exc)
					{
						string exc_text = exc.what();
						string err_msg = "OnPropertyChange ERROR:  " + exc_text + ", " + pProp->GetName();
						LogMessage(err_msg);
					}
				}
				break;
			case type_bool:
				{
					string value = "";
					pProp->Get(value);
					int cmd_val = 0;
					if( value == BOOL_VALUE_ON)                
						cmd_val = 1;
					else if( value == BOOL_VALUE_OFF)         
						cmd_val = 0;
					else 
						return DEVICE_INVALID_PROPERTY_VALUE;

					camera->SetXIAPIParamInt(param_name.c_str(), cmd_val);
				}
				break;
			case type_string:
				{
					string value = "";
					pProp->Get(value);
					camera->SetXIAPIParamString(param_name.c_str(), value.c_str(), (unsigned int) value.size());
				}
				break;
			default:
				{
					string err_msg = "OnPropertyChange ERROR: invalid type, " + pProp->GetName();
					LogMessage(err_msg);
					return DEVICE_INVALID_PROPERTY_TYPE;
				}
			}

			// resize image buffer when data format and downsampling are changed
			if(param->GetXiParamName() == XI_PRM_IMAGE_DATA_FORMAT ||
				param->GetXiParamName() == XI_PRM_DOWNSAMPLING ||
				param->GetXiParamName() == XI_PRM_DOWNSAMPLING_TYPE)
			{
				ResizeImageBuffer();
			}
		}
		else if(eAct == MM::BeforeGet)
		{
			// do not read write-only parameters
			if(param->GetAccessType() == acces_write)
			{
				return DEVICE_OK;
			}

			switch(param->GetParamType())
			{
			case type_command:
			case type_int:
				{
					long value = camera->GetXIAPIParamInt(param_name);
					// exposure time values must be devided by 1000
					if(param->GetXiParamName() == XI_PRM_EXPOSURE ||
						param->GetXiParamName() == XI_PRM_AE_MAX_LIMIT)
					{
						double d_value = (double) value / 1000.0; 
						pProp->Set(d_value);
					}
					else
					{
						pProp->Set(value);
					}
				}
				break;
			case type_float:
				{
					double value = camera->GetXIAPIParamFloat(param_name);
					pProp->Set(value);
				}
				break;
			case type_enum:
				{
					int value = camera->GetXIAPIParamInt(param_name);
					string enum_name = param->GetEnumName(value);
					pProp->Set(enum_name.c_str());
				}
				break;
			case type_bool:
				{
					int value = camera->GetXIAPIParamInt(param_name);
					if(value == 0) pProp->Set(BOOL_VALUE_OFF);
					else           pProp->Set(BOOL_VALUE_ON);
				}
				break;
			case type_string:
				{
					string value = camera->GetParamString(param_name);
					pProp->Set(value.c_str());
				}
				break;
			default:
				{
					string err_msg = "OnPropertyChange ERROR: invalid type, " + pProp->GetName();
					LogMessage(err_msg);
					return DEVICE_INVALID_PROPERTY_TYPE;
				}
			}
		}
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "OnPropertyChange(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_INVALID_PROPERTY_VALUE;
	}
	catch(...)
	{
		LogMessage("OnPropertyChange(): Unknown exception");
		return DEVICE_INVALID_PROPERTY_VALUE;
	}

	return DEVICE_OK;
}

//***********************************************************************

XimeaParam* XimeaCamera::GetXimeaParam(string param_name, bool use_xiapi_param_name)
{
	XimeaParam* p = NULL;
	for (std::vector<XimeaParam>::iterator param = cam_params.begin() ; param != cam_params.end(); ++param)
	{
		if(use_xiapi_param_name)
		{
			if(param->GetXiParamName() == param_name)
			{
				p = &*param;
				break;
			}
		}
		else
		{
			if(param->GetName() == param_name)
			{
				p = &*param;
				break;
			}
		}
	}
	return p;
}

///////////////////////////////////////////////////////////////////////////////
// Private XimeaCamera methods
///////////////////////////////////////////////////////////////////////////////

void XimeaCamera::UpdateRoiParams()
{
	UpdateProperty("Image width");
	UpdateProperty("Image height");
	UpdateProperty("ROI OffsetX");
	UpdateProperty("ROI OffsetY");
}

//***********************************************************************

void XimeaCamera::CreateCameraProperties()
{
	// remove unsupported pixel formats
	XimeaParam* param = GetXimeaParam(XI_PRM_IMAGE_DATA_FORMAT, true);
	if(param != NULL)
	{
		param->RemoveEnumItem("Transport Data");
		param->RemoveEnumItem("RGB24");
		param->RemoveEnumItem("RGB Planar");
	}

	for (std::vector<XimeaParam>::iterator param = cam_params.begin() ; param != cam_params.end(); ++param)
	{
		MM::PropertyType property_type = MM::Undef;
		string property_value = "";
		vector<string> enum_values;
		bool is_read_only = true;

		// check if parameter is not already in list
		if(HasProperty(param->GetName().c_str()))
		{
			continue;
		}

		// prepare readonly flag
		if(param->GetAccessType() == acces_write ||
		   param->GetAccessType() ==  access_readwrite)
		{
			is_read_only = false;
		}

		try
		{
			switch(param->GetParamType())
			{
			case type_int:
				{
					property_type = MM::Integer;
					ostringstream convert;
					int int_value = camera->GetXIAPIParamInt(param->GetXiParamName());

					// exposure time values must be devided by 1000
					if(param->GetXiParamName() == XI_PRM_EXPOSURE ||
						param->GetXiParamName() == XI_PRM_AE_MAX_LIMIT)
					{
						int_value = int_value/ 1000;			
					}

					convert << int_value;
					property_value = convert.str();
				}
				break;
			case type_float:
				{
					property_type = MM::Float;
					property_value = camera->GetParamString(param->GetXiParamName());
				}
				break;
			case type_enum:
				{
					property_type = MM::String;
					enum_values = param->GetEnumValues();
					property_value = enum_values.at(0);

					// enum parameters with one value will be skipped
					if(enum_values.size() <= 1)
					{
						continue;
					}
				}
				break;
			case type_bool:
				{
					property_type = MM::String;
					param->AddEnumValue(BOOL_VALUE_OFF, 0);
					param->AddEnumValue(BOOL_VALUE_ON, 1);
					enum_values = param->GetEnumValues();

					string def_val = param->GetAppDefault();
					if(def_val == "1")
					{
						property_value = enum_values.at(1);
						camera->SetXIAPIParamInt(param->GetXiParamName().c_str(), 1);
					}
					else
					{
						property_value = enum_values.at(0);
					}
				}
				break;
			case type_string:
				{
					// path string parameters will be skipped
					if(param->IsParamTypePath())
					{
						continue;
					}
					property_type = MM::String;
					property_value = camera->GetParamString(param->GetXiParamName());
				}
			
				break;
			case type_command:
				{
					property_type = MM::Integer;
					property_value = "0";
				}
				break;
			default:
				{
					string msg = "Invalid parameter property type: " + param->GetName();
					LogMessage(msg);
					continue;
				}
			}
		}
		catch(xiAPIplus_Exception exc)
		{
			string err_msg = "CreateCameraProperties() failed to read property value: " + exc.GetDescription();
			LogMessage(err_msg);
			continue;
		}
		catch(...)
		{
			LogMessage("CreateCameraProperties() property preparation: Unknown exception");
			continue;
		}

		int ret = DEVICE_OK;
		CPropertyAction *pAct = new CPropertyAction (this, &XimeaCamera::OnPropertyChange);
		assert(pAct != NULL);

		// create MM property
		ret = CreateProperty(param->GetName().c_str(), property_value.c_str(), property_type, is_read_only, pAct);
		assert(ret == DEVICE_OK);

		// update min/max values
		if(param->GetParamType() == type_int    ||
			param->GetParamType() == type_float ||
			param->GetParamType() == type_command)
		{
			double min = 0;
			double max = 0;
			string param_min = param->GetXiParamName() + XI_PRM_INFO_MIN;
			string param_max = param->GetXiParamName() + XI_PRM_INFO_MAX;

			try
			{
				min = camera->GetXIAPIParamFloat(param_min);
				max = camera->GetXIAPIParamFloat(param_max);

				// exposure time values must be devided by 1000
				if(param->GetXiParamName() == XI_PRM_EXPOSURE ||
					param->GetXiParamName() == XI_PRM_AE_MAX_LIMIT)
				{
					min = min / 1000.0;
					max = max / 1000.0;
				}
				// values must be different
				if(min != max)
				{
					ret = SetPropertyLimits(param->GetName().c_str(), min, max);
					assert(ret == DEVICE_OK);				
				}
			}
			catch(xiAPIplus_Exception exc)
			{
				string err_msg = "CreateCameraProperties(): " + exc.GetDescription();
				LogMessage(err_msg);
			}
			catch(...)
			{
				LogMessage("CreateCameraProperties(): Unknown exception");
			}
			// log success of parameter adding
			LogMessage("Added parameter " + param->GetName());
		}

		// update enum values of property
		if(param->GetParamType() == type_enum ||
			param->GetParamType() == type_bool)
		{
			ret = SetAllowedValues(param->GetName().c_str(), enum_values);
			//assert(ret == DEVICE_OK);
		}
	}

	// add acquisition timeout param
	{
		int ret = DEVICE_OK;
		CPropertyAction *pAct = new CPropertyAction (this, &XimeaCamera::OnPropertyChange);
		assert(pAct != NULL);

		// create MM property
		ret = CreateProperty(CAM_PARAM_ACQ_TIMEOUT, DEFAULT_ACQ_TOUT_STR, MM::Integer, false, pAct);
		assert(ret == DEVICE_OK);

		ret = SetPropertyLimits(CAM_PARAM_ACQ_TIMEOUT, ACQ_TIMEOUT_MIN_MS, ACQ_TIMEOUT_MAX_MS);
		assert(ret == DEVICE_OK);
	}
}

//***********************************************************************

bool GoChildNode(pugi::xml_node* node)
{
	pugi::xml_node temp_nd = node->first_child();
	if(temp_nd == NULL)
	{
		return false;
	}
	else
	{
		*node = temp_nd;
		return true;
	}
}

bool GoSiblingNode(pugi::xml_node* node)
{
	pugi::xml_node temp_nd = node->next_sibling();
	if(temp_nd == NULL)
	{
		return false;
	}
	else
	{
		*node = temp_nd;
		return true;
	}
}

bool GoParentNode(pugi::xml_node* node)
{
	pugi::xml_node temp_nd = node->parent();
	if(temp_nd == NULL)
	{
		return false;
	}
	else
	{
		*node = temp_nd;
		return true;
	}
}

void XimeaCamera::ParseCameraManifest(char* manifest)
{
	std::vector<std::string> check_feats;
	check_feats.push_back(TAG_DISP_NAME);
	check_feats.push_back(TAG_ACCES_MODE);
	check_feats.push_back(TAG_XIAPI_PARAM);
	check_feats.push_back(TAG_ENUM_ENTRY);
	check_feats.push_back(TAG_IS_PATH);

	pugi::xml_document doc;
	if(doc.load(manifest))
	{
		pugi::xml_node curr_nd = doc.root();
		GoChildNode(&curr_nd);;
		do
		{
			GoChildNode(&curr_nd);;
			do
			{
				// ignore empty parameter groups
				if (curr_nd.name() == NULL)
				{
					continue;
				}

				XimeaParam param;
				std::string node_type = curr_nd.name();

				// if group then go to first child
				if (node_type == TAG_GROUP_NAME)
				{
					GoChildNode(&curr_nd);
					node_type = curr_nd.name();
				}

				// assign parameter type
				if (node_type == TAG_INTEGER_FEAT)     param.SetParamType(type_int);
				if (node_type == TAG_BOOLEAN_FEAT)     param.SetParamType(type_bool);
				if (node_type == TAG_ENUMERATION_FEAT) param.SetParamType(type_enum);
				if (node_type == TAG_COMMAND_FEAT)     param.SetParamType(type_command);
				if (node_type == TAG_FLOAT_FEAT)       param.SetParamType(type_float);
				if (node_type == TAG_STRING_FEAT)      param.SetParamType(type_string);
				if (param.GetParamType() != type_undef)
				{
					// evaluate XML feature content
					for (unsigned int f_num = 0; f_num < check_feats.size(); f_num++)
					{
						string feat = check_feats.at(f_num).c_str();
						string value = ReadNodeData(curr_nd, feat);
						
						if(feat.empty())
						{
							continue;
						}

						if (feat == TAG_DISP_NAME)	 param.SetName(value);
						if (feat == TAG_ACCES_MODE)  param.SetAccessType(value);
						if (feat == TAG_XIAPI_PARAM) param.SetXiParamName(value);
						if (feat == TAG_APP_DEFAULT) param.SetAppDefault(value);
						if (feat == TAG_IS_PATH && value == "1")
						{
							param.SetParamTypePath();
						}
						if (feat == TAG_ENUM_ENTRY && param.GetParamType() == type_enum)
						{
							int num_enums = CountNodes(curr_nd, TAG_ENUM_ENTRY);
							GoChildNode(&curr_nd);
							do
							{
								string sub_node = curr_nd.name();
								if (sub_node == TAG_ENUM_ENTRY)
								{
									for (int i = 0; i < num_enums; i++)
									{
										string enum_name = ReadNodeData(curr_nd, TAG_DISP_NAME);
										int   enum_value = ReadNodeDataInt(curr_nd, TAG_ENUM_VALUE);
										param.AddEnumValue(enum_name, enum_value);
										GoSiblingNode(&curr_nd);
									}
									// all enums read
									break;
								}
							}
							while (GoSiblingNode(&curr_nd));
							GoParentNode(&curr_nd);
						}
					}
				}

				// add parameter if defined
				if (param.GetParamType() != type_undef)
				{
					cam_params.push_back(param);
				}
			}
			while (GoSiblingNode(&curr_nd));

			// continue to next parameter type group
			GoParentNode(&curr_nd);
		}
		while (GoSiblingNode(&curr_nd));
	}
	else
	{
		LogMessage("ParseCameraManifest() ERROR: Failed to parse camera manifest");
	}
}

//***********************************************************************

string XimeaCamera::ReadNodeData(pugi::xml_node nd, string feature_name)
{
	string value = "";
	pugi::xpath_node_set features = nd.select_nodes(feature_name.c_str());
	for (pugi::xpath_node_set::const_iterator it = features.begin(); it != features.end(); ++it)
	{
		pugi::xpath_node prop_node = *it;
		if(it->node().name() == feature_name)
		{
			value = it->node().first_child().value();
			break;
		}
	}
	return value;
}

//***********************************************************************

int XimeaCamera::ReadNodeDataInt(pugi::xml_node nd, string feature_name)
{
	string str_val = ReadNodeData(nd, feature_name);
	// alias of size_t
	std::string::size_type sz;
	int value = std::stoi (str_val, &sz);
	return value;
}

//***********************************************************************

int XimeaCamera::CountNodes(pugi::xml_node nd, string node_name)
{
	int node_count = 0;

	pugi::xpath_node_set features = nd.select_nodes(node_name.c_str());
	for (pugi::xpath_node_set::const_iterator it = features.begin(); it != features.end(); ++it)
	{
		pugi::xpath_node prop_node = *it;
		if(it->node().name() == node_name)
		{
			node_count++;
		}
	}
	return node_count;
}

/***********************************************************************
* Sync internal image buffer size to the chosen property values.
*/
int XimeaCamera::ResizeImageBuffer()
{
	int width = 0;
	int height = 0;
	int frm = 0;

	try
	{
		width = camera->GetXIAPIParamInt(XI_PRM_WIDTH);
		height = camera->GetXIAPIParamInt(XI_PRM_HEIGHT);
		frm = camera->GetXIAPIParamInt(XI_PRM_IMAGE_DATA_FORMAT);
	}
	catch(xiAPIplus_Exception exc)
	{
		string err_msg = "ResizeImageBuffer(): " + exc.GetDescription();
		LogMessage(err_msg);
		return DEVICE_ERR;
	}
	catch(...)
	{
		LogMessage("ResizeImageBuffer(): Unknown exception");
		return DEVICE_ERR;
	}

	switch(frm)
	{
	case XI_MONO8:
	case XI_RAW8: 
		bytesPerPixel_ = 1; 
		nComponents_ = 1;
		break;
	case XI_MONO16:
	case XI_RAW16: 
		bytesPerPixel_ = 2;
		nComponents_ = 1;
		break;
	case XI_RGB32: 
		bytesPerPixel_ = 4; 
		nComponents_ = 4;
		break;
	default: assert(false); // this should never happen
	}

	img_->Resize(width, height, bytesPerPixel_);
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Image acquisition thread
///////////////////////////////////////////////////////////////////////////////

XiSequenceThread::XiSequenceThread(XimeaCamera* pCam)
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

XiSequenceThread::~XiSequenceThread() {};

//***********************************************************************

void XiSequenceThread::Stop() {
	MMThreadGuard(this->stopLock_);
	stop_=true;
}

//***********************************************************************

void XiSequenceThread::Start(long numImages, double intervalMs)
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

bool XiSequenceThread::IsStopped()
{
	MMThreadGuard(this->stopLock_);
	return stop_;
}

//***********************************************************************

void XiSequenceThread::Suspend()
{
	MMThreadGuard(this->suspendLock_);
	suspend_ = true;
}

//***********************************************************************

bool XiSequenceThread::IsSuspended()
{
	MMThreadGuard(this->suspendLock_);
	return suspend_;
}

//***********************************************************************

void XiSequenceThread::Resume()
{
	MMThreadGuard(this->suspendLock_);
	suspend_ = false;
}

//***********************************************************************

int XiSequenceThread::svc(void) throw()
{
	int ret = DEVICE_ERR;
	try
	{
		do
		{
			ret = camera_->CaptureImage();
		}
		while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);

		if (IsStopped())
		{
			camera_->LogMessage("SeqAcquisition interrupted by the user\n");
		}
	}
	catch(...)
	{
		camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
	}

	stop_=true;
	actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
	camera_->OnThreadExiting();
	return ret;
}

///////////////////////////////////////////////////////////////////////////////