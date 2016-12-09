///////////////////////////////////////////////////////////////////////////////
// FILE:          AmScope.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation code for the micro-manager integrated
//				  AmScope camera adapter.
//                
// AUTHOR:        MaheshKumar Reddy Kakuturu
//                
// COPYRIGHT:     Angstrom Science Inc., 2016
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

#include "AmScope.h"
#include "ModuleInterface.h"

//#include <iostream>
//#include <fstream>

using namespace std;

#ifndef TDIBWIDTHBYTES
#define TDIBWIDTHBYTES(bits)	(((bits) + 31) / 32 * 4)
#endif

const char* g_CameraName = "AmScope";
const char* g_AutoFocusDeviceName = "ASAutoFocus";
const char* g_HubDeviceName = "DHub";

const char* g_PixelType_8bit = "GREY8";
const char* g_PixelType_32bitRGB = "RGB32";

const char* g_CameraModelProperty = "Model";
const char* g_CameraModel_A = "MU1403";

const char* g_PixelResolution1 = "1x1";// "4096*3286";
const char* g_PixelResolution2 = "2x2";// "2048*1644";
const char* g_PixelResolution3 = "4x4";// "1024*822";

const char* NoHubError = "Parent Hub not defined.";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraName, MM::CameraDevice, "AmScope");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
      // create camera
      return new AmScope();
   }
   else if (strcmp(deviceName, g_AutoFocusDeviceName) == 0)
   {
      // create autoFocus
      return new AmScopeAutoFocus();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// AmScope implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* AmScope constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
AmScope::AmScope() :
	CCameraBase<AmScope> (),
	dPhase_(0),
	binning_(1),
	autoExposure_(1),
	exposureTarget_(127),
	autoLevelRange_(1),
	autoWhitebalance_(1),
    aGain_(100),
	bytesPerPixel_(4),
	resolution_(1),
	initialized_(false),
	exposureMs_(100),
	bitDepth_(8),
	roiX_(0),
	roiY_(0),
	flipHorizontal_("FALSE"),
	flipVertical_("FALSE"),
	sequenceStartTime_(0),
	sequenceMaxLength_(100),
	fastImage_(false),
	nComponents_(4),
	frameRate_(60),
	thd_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // Description property
   int ret = CreateProperty(MM::g_Keyword_Description, "AmScope Camera Adapter", MM::String, true);
   assert(ret == DEVICE_OK);

   // camera type pre-initialization property
   ret = CreateProperty(g_CameraModelProperty, g_CameraModel_A, MM::String, false, 0, true);
   assert(ret == DEVICE_OK);

   vector<string> modelValues;
   modelValues.push_back(g_CameraModel_A);

   ret = SetAllowedValues(g_CameraModelProperty, modelValues);
   assert(ret == DEVICE_OK);

    m_header.biSize = sizeof(BITMAPINFOHEADER);
	m_header.biPlanes = 1;
	m_header.biBitCount = 24;

   // create live video thread
   thd_ = new SequenceThread(this);
}

/**
* AmScope destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
AmScope::~AmScope()
{
   if (initialized_)
   {
      Shutdown();
   }

   delete thd_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void AmScope::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraName);
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int AmScope::Initialize()
{
	if (initialized_)
	{
		return DEVICE_OK;
	}

	// Get all the connected camera components
    unsigned cnt = Toupcam_Enum(m_ti);
	if (cnt == 0) // None of the AnScope cameras are connected, return error
	{
		return DEVICE_NOT_CONNECTED;
	}
	
	// Currently we are dealing with only first recognized camera
	std::wstring id(L"@"); // starting with @ Opens the camera in RGB gain mode, otherwise Temp/Tint mode
	id += m_ti[0].id;	
	m_Htoupcam = Toupcam_Open(id.c_str());
	//m_Htoupcam = Toupcam_Open(m_ti[0].id);
	if (m_Htoupcam == NULL) // can not communicate with camera, return error
	{
		return DEVICE_NOT_CONNECTED;
	}

	Toupcam_put_Option(m_Htoupcam, TOUPCAM_OPTION_BITDEPTH, 0); // Set to use 8 bit depth
	Toupcam_put_Option(m_Htoupcam, TOUPCAM_OPTION_RAW, 0); // 0 means RGB mode, 1 means RAW mode
	Toupcam_put_Option(m_Htoupcam, TOUPCAM_OPTION_WBGAIN, 0); // Turn off the builtin white balance gain
	Toupcam_put_Chrome(m_Htoupcam, FALSE); // Put camera in 24 bit RGB color mode
	Toupcam_put_Mode(m_Htoupcam, TRUE); // Set Bin mode by default

	// Start camera to generate image pixels
	Toupcam_StartPullModeWithCallback(m_Htoupcam, NULL, NULL);
	Toupcam_put_AutoExpoEnable(m_Htoupcam, TRUE); // Enable the camera auto exposure
	Toupcam_put_HZ(m_Htoupcam, 0); //  0: 60Hz alternating current, 1: 50Hz alternating current, 2: direct current
	Toupcam_put_AutoExpoTarget(m_Htoupcam, (unsigned short)exposureTarget_); // Set the auto exposure target value
	Toupcam_LevelRangeAuto(m_Htoupcam); // Set the RGB color level to default auto range (0 - 255)
	

   // set property list
   // -----------------	
   
   // Auto exposure
   CPropertyAction *pAct = new CPropertyAction (this, &AmScope::OnAutoExposure);
   int ret = CreateProperty("ExposureAuto", "1", MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> autoExposureValues;
   autoExposureValues.push_back("1");
   autoExposureValues.push_back("0"); 

    ret = SetAllowedValues("ExposureAuto", autoExposureValues);
   assert(ret == DEVICE_OK);

   // Auto exposure target
   pAct = new CPropertyAction (this, &AmScope::OnAutoExposureTarget);
   ret = CreateIntegerProperty("ExposureAutoTarget", exposureTarget_, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("ExposureAutoTarget", 16, 235);


   // Pixel type
   pAct = new CPropertyAction (this, &AmScope::OnPixelType);
   ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> pixelTypeValues;   
   pixelTypeValues.push_back(g_PixelType_32bitRGB);
   pixelTypeValues.push_back(g_PixelType_8bit);   

   ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   assert(ret == DEVICE_OK);

   // Bit depth
   pAct = new CPropertyAction (this, &AmScope::OnBitDepth);
   ret = CreateIntegerProperty("BitDepth", 8, true, pAct);
   assert(ret == DEVICE_OK);

   vector<string> bitDepths;
   bitDepths.push_back("8");
   ret = SetAllowedValues("BitDepth", bitDepths);
   assert(ret == DEVICE_OK);

   // exposure
   pAct = new CPropertyAction (this, &AmScope::OnExposureTime);
   unsigned int nMin, nMax, nDef;
   Toupcam_get_ExpTimeRange(m_Htoupcam, &nMin, &nMax, &nDef);
   Toupcam_get_ExpoTime(m_Htoupcam, &nDef);
   exposureMs_ = nDef / 1000;
   defaultExposureTime_ = (long)exposureMs_;
   ret = CreateIntegerProperty("ExposureTime(ms)", nDef, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("ExposureTime(ms)", nMin / 1000, nMax / 1000);
   
   // Analog Gain
   pAct = new CPropertyAction (this, &AmScope::OnAGain);
   unsigned short nMinG, nMaxG, nDefG;
   Toupcam_get_ExpoAGainRange(m_Htoupcam, &nMinG, &nMaxG, &nDefG);
   defaultAGain_ = aGain_ = nMaxG;
   ret = CreateIntegerProperty("ExposureGain", nMaxG, false, pAct);
   assert(ret == DEVICE_OK);
   SetPropertyLimits("ExposureGain", nMinG, nMaxG);

   // White Balance Gain in RGB gain mode   
   Toupcam_get_WhiteBalanceGain(m_Htoupcam, wbGain_);
   pAct = new CPropertyAction (this, &AmScope::OnWhiteBalanceRGain);
   CreateIntegerProperty("WhiteBalanceRGain", wbGain_[0], false, pAct);
   SetPropertyLimits("WhiteBalanceRGain", -128, 128);
   pAct = new CPropertyAction (this, &AmScope::OnWhiteBalanceGGain);
   CreateIntegerProperty("WhiteBalanceGGain", wbGain_[1], false, pAct);
   SetPropertyLimits("WhiteBalanceGGain", -128, 128);
   pAct = new CPropertyAction (this, &AmScope::OnWhiteBalanceBGain);
   CreateIntegerProperty("WhiteBalanceBGain", wbGain_[2], false, pAct);
   SetPropertyLimits("WhiteBalanceBGain", -128, 128);

   // Auto level range
   pAct = new CPropertyAction (this, &AmScope::OnAutoLevelRange);
   ret = CreateProperty("LevelRangeAFull", "1", MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> autoLRValues;
   autoLRValues.push_back("1");
   autoLRValues.push_back("0"); 

   ret = SetAllowedValues("LevelRangeAFull", autoLRValues);
   assert(ret == DEVICE_OK);

   // White Balance Gain in RGB gain mode   
   Toupcam_get_LevelRange(m_Htoupcam, aLow_, aHigh_);
    pAct = new CPropertyAction (this, &AmScope::OnLevelRangeRMin);
   CreateIntegerProperty("LevelRangeRMin", aLow_[0], false, pAct);
   SetPropertyLimits("LevelRangeRMin", 0, 255);
   pAct = new CPropertyAction (this, &AmScope::OnLevelRangeGMin);
   CreateIntegerProperty("LevelRangeGMin", aLow_[1], false, pAct);
   SetPropertyLimits("LevelRangeGMin", 0, 255);
   pAct = new CPropertyAction (this, &AmScope::OnLevelRangeBMin);
   CreateIntegerProperty("LevelRangeBMin", aLow_[2], false, pAct);
   SetPropertyLimits("LevelRangeBMin", 0, 255);
   pAct = new CPropertyAction (this, &AmScope::OnLevelRangeRMax);
   CreateIntegerProperty("LevelRangeRMax", aHigh_[0], false, pAct);
   SetPropertyLimits("LevelRangeRMax", 0, 255);
   pAct = new CPropertyAction (this, &AmScope::OnLevelRangeGMax);
   CreateIntegerProperty("LevelRangeGMax", aHigh_[1], false, pAct);
   SetPropertyLimits("LevelRangeGMax", 0, 255);
   pAct = new CPropertyAction (this, &AmScope::OnLevelRangeBMax);
   CreateIntegerProperty("LevelRangeBMax", aHigh_[2], false, pAct);
   SetPropertyLimits("LevelRangeBMax", 0, 255);

   // Serial number
   char sn[32];
   Toupcam_get_SerialNumber(m_Htoupcam, sn);
   CreateStringProperty("SerialNumber", sn, true);

   // Physical pixel size (um)
   Toupcam_get_PixelSize(m_Htoupcam, 0, &orgPixelSizeXUm_, &orgPixelSizeYUm_);
   nominalPixelSizeUm_ = orgPixelSizeXUm_;
   pixelSizeXUm_ = (float)nominalPixelSizeUm_;
   pixelSizeYUm_ = orgPixelSizeYUm_;
   pAct = new CPropertyAction (this, &AmScope::OnPixelSizeXUm);
   CreateFloatProperty("PixelSizeX(um)", pixelSizeXUm_, true, pAct);
   pAct = new CPropertyAction (this, &AmScope::OnPixelSizeYUm);
   CreateFloatProperty("PixelSizeY(um)", pixelSizeYUm_, true, pAct);

   // CCD size of the camera we are modeling
   pAct = new CPropertyAction (this, &AmScope::OnCameraCCDXSize);
   CreateIntegerProperty("CameraCCDXSize", IMAGE_WIDTH, true, pAct);
   pAct = new CPropertyAction (this, &AmScope::OnCameraCCDYSize);
   CreateIntegerProperty("CameraCCDYSize", IMAGE_HEIGHT, true, pAct);

   // Resolution selection of the camera
   // Currently supports 3 resolutions
   pAct = new CPropertyAction (this, &AmScope::OnPixelResolusion);
   ret = CreateProperty(MM::g_Keyword_Binning, g_PixelResolution1, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> pixelResolutionValues;
   pixelResolutionValues.push_back(g_PixelResolution1);
   pixelResolutionValues.push_back(g_PixelResolution2);
   pixelResolutionValues.push_back(g_PixelResolution3);

   ret = SetAllowedValues(MM::g_Keyword_Binning, pixelResolutionValues);
   assert(ret == DEVICE_OK);

   // Horizontal flip
   pAct = new CPropertyAction (this, &AmScope::OnFlipHorizontal);
   ret = CreateStringProperty("FlipHorizontal", flipHorizontal_.c_str(), false, pAct);
   assert(ret == DEVICE_OK);   

   vector<string> flipValues;
   flipValues.push_back("FALSE");
   flipValues.push_back("TRUE");

   ret = SetAllowedValues("FlipHorizontal", flipValues);
   assert(ret == DEVICE_OK);

   // Vertical flip
   pAct = new CPropertyAction (this, &AmScope::OnFlipVertical);
   ret = CreateStringProperty("FlipVertical", flipVertical_.c_str(), false, pAct);
   assert(ret == DEVICE_OK);
   
   ret = SetAllowedValues("FlipVertical", flipValues);
   assert(ret == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   // setup the buffer
   // ----------------
   CreateCameraImageBuffer();
   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   // initialize image buffer
   GenerateEmptyImage();

   initialized_ = true;
   return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int AmScope::Shutdown()
{
	if (initialized_ == false)
	{
		return DEVICE_OK;
	}

	if (m_Htoupcam)
	{
		Toupcam_Close(m_Htoupcam);
		m_Htoupcam = NULL;
	}

   initialized_ = false;
   return DEVICE_OK;
}

/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int AmScope::SnapImage()
{
	if (!fastImage_)
	{
		GenerateCameraImage();
	}

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
const unsigned char* AmScope::GetImageBuffer()
{
   return const_cast<unsigned char*>(img_.GetPixels());
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned AmScope::GetImageWidth() const
{
   return img_.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned AmScope::GetImageHeight() const
{
   return img_.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned AmScope::GetImageBytesPerPixel() const
{
   return img_.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned AmScope::GetBitDepth() const
{
	return bitDepth_;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long AmScope::GetImageBufferSize() const
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
int AmScope::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
	   ClearROI();
   }
   else
   {
	   unsigned inX = x;
	   unsigned inY = y;
	   unsigned imgWidth = 4096;
	   unsigned imgHeight = 3286;

	   if (resolution_ == 1)
	   {
		   imgWidth = 4096;
		   imgHeight = 3286;
	   }
	   else if (resolution_ == 2)
	   {
		   imgWidth = 2048;
		   imgHeight = 1644;
	   }
	   else if (resolution_ == 3)
	   {
		   imgWidth = 1024;
		   imgHeight = 822;
	   }

	   // apply ROI
	   // x offset, must be even number
	   // y offset, must be even number
	   // width, must be even number and must not be less than 16 for this camera
	   // height, must be even number and must not be less than 16 for this camera
	   if (xSize < 16)
	   {
		   xSize = 16;
	   }
	   if (ySize < 16)
	   {
		   ySize = 16;
	   }

		x= (x % 2) == 0 ? x : x - 1;
		y= (y % 2) == 0 ? y : y - 1;
		// AmScope is expecting width as multiples of 4
		// Make the camera ROI width to match camera expectation
		xSize= (xSize % 4) == 0 ? xSize : xSize - (xSize % 4);
		ySize= (ySize % 2) == 0 ? ySize : ySize - 1;

		// Micro Manager ROI origin is always at top left corner
		if (flipVertical_.compare("FALSE") == 0)
		{
			y = imgHeight - (y + ySize);
		}

		if (flipHorizontal_.compare("TRUE") == 0)
		{
			x = imgWidth - (x + xSize);
		}

	    MMThreadGuard g(imgPixelsLock_);
		// Stop image streaming before changing the ROI
		if (SUCCEEDED(Toupcam_Stop(m_Htoupcam)))
		{
			// Set new ROI to the camera
			if (SUCCEEDED(Toupcam_put_Roi(m_Htoupcam, x, y, xSize, ySize)))
			{
				// Create image buffer to read from camera
				CreateCameraImageBuffer();
				// re-size the micro manager image buffer to match camera image buffer
				ResizeImageBuffer();

				roiX_ = inX;
				roiY_ = inY;
			}

			// Restart generating live stream from the camera
			Toupcam_StartPullModeWithCallback(m_Htoupcam, NULL, NULL);
		}
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int AmScope::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
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
int AmScope::ClearROI()
{
	Toupcam_Stop(m_Htoupcam);

	// Clear the ROI and restore the original size
	if (SUCCEEDED(Toupcam_put_Roi(m_Htoupcam, 0, 0, 0, 0)))
	{
		CreateCameraImageBuffer();
		ResizeImageBuffer();

		// Re-set the x and y offsets for ROI
		roiX_ = 0;
		roiY_ = 0;
	}

	Toupcam_StartPullModeWithCallback(m_Htoupcam, NULL, NULL);
      
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double AmScope::GetExposure() const
{
   return exposureMs_;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void AmScope::SetExposure(double exp)
{
	if (exposureMs_ != exp)
	{
		exposureMs_ = exp;
		Toupcam_put_ExpoTime(m_Htoupcam, (unsigned int)(exposureMs_ * 1000));
	}
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int AmScope::GetBinning() const
{
	return binning_;
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int AmScope::SetBinning(int binF)
{
	binning_ = binF;
   return SetProperty("Binning-Software", CDeviceUtils::ConvertToString(binF));
}

//int AmScope::PrepareSequenceAcqusition()
//{
//   if (IsCapturing())
//      return DEVICE_CAMERA_BUSY_ACQUIRING;
//
//   int ret = GetCoreCallback()->PrepareForAcq(this);
//   if (ret != DEVICE_OK)
//      return ret;
//
//   return DEVICE_OK;
//}


/**
 * Required by the MM::Camera API
 * Please implement this yourself and do not rely on the base class implementation
 * The Base class implementation is deprecated and will be removed shortly
 */
int AmScope::StartSequenceAcquisition(double interval) {

   return StartSequenceAcquisition(LONG_MAX, interval, false);            
}

/**                                                                       
* Stop and wait for the Sequence thread finished                                   
*/                                                                        
int AmScope::StopSequenceAcquisition()                                     
{                                                                         
   if (!thd_->IsStopped())
   {
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
int AmScope::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (IsCapturing())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   sequenceStartTime_ = GetCurrentMMTime();
   imageCounter_ = 0;
   thd_->Start(numImages,interval_ms);
   stopOnOverflow_ = stopOnOverflow;

   return DEVICE_OK;
}

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int AmScope::InsertImage()
{
   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   MMThreadGuard g(imgPixelsLock_);
   // Important:  metadata about the image are generated here:
   Metadata md;
   md.put("Camera", label);
  md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
  md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
  md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString( imageCounter_ ));
  md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_));
  md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_));
  
  const unsigned char* pI = GetImageBuffer();
  unsigned int w = GetImageWidth();
  unsigned int h = GetImageHeight();
  unsigned int b = GetImageBytesPerPixel();  

  int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str() );

  if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
  {  
    // do not stop on overflow - just reset the buffer
    GetCoreCallback()->ClearImageBuffer(this);
    // don't process this same image again...
    ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
  }
  
  if (ret == DEVICE_OK)
  {
    imageCounter_++;
  }

  return ret;
}

/**
 * Returns the current exposure from a sequence and increases the sequence counter
 * Used for exposure sequences
 */
double AmScope::GetSequenceExposure() 
{
   if (exposureSequence_.size() == 0) 
   {
      return this->GetExposure();
   }

   double exposure = exposureSequence_[sequenceIndex_];

   sequenceIndex_++;
   if (sequenceIndex_ >= exposureSequence_.size())
   {
      sequenceIndex_ = 0;
   }

   return exposure;
}

/*
 * Do actual capturing
 * Called from inside the thread  
 */
int AmScope::RunSequenceOnThread(MM::MMTime startTime)
{
   int ret=DEVICE_ERR;
   
   // Trigger
   if (triggerDevice_.length() > 0)
   {
      MM::Device* triggerDev = GetDevice(triggerDevice_.c_str());
      if (triggerDev != 0)
	  {
      	LogMessage("trigger requested");
      	triggerDev->SetProperty("Trigger","+");
      }
   }

   //double exposure = GetSequenceExposure();

   if (!fastImage_)
   {
      GenerateCameraImage();
   }

   // Simulate exposure duration
   /*double finishTime = exposure * (imageCounter_ + 1);
   while ((GetCurrentMMTime() - startTime).getMsec() < finishTime)
   {
      CDeviceUtils::SleepMs(1);
   }*/

   ret = InsertImage();

   return ret;
}

/**
* Returns the image capturing status.
*/
bool AmScope::IsCapturing()
{
   return !thd_->IsStopped();
}


///////////////////////////////////////////////////////////////////////////////
// AmScope Action handlers
///////////////////////////////////////////////////////////////////////////////
/**
* Handles "Auto Exposure" property.
*/
int AmScope::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   string val;
	   pProp->Get(val);

	   if (val.compare("1") == 0)
	   {
		   autoExposure_ = 1;
	   }
	   else
	   {
		   autoExposure_ = 0;
	   }

	  if (autoExposure_ == 1)
	  {
		  Toupcam_put_AutoExpoEnable(m_Htoupcam, TRUE);
	  }
	  else
	  {
		  Toupcam_put_AutoExpoEnable(m_Htoupcam, FALSE);
		  Toupcam_put_ExpoTime(m_Htoupcam, (unsigned int)(exposureMs_ * 1000));
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(autoExposure_);
   }

   return DEVICE_OK;
}

/**
* Handles "Auto Exposure Target" property.
*/
int AmScope::OnAutoExposureTarget(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		pProp->Get(exposureTarget_);

		Toupcam_put_AutoExpoTarget(m_Htoupcam, (unsigned short)exposureTarget_);
	}
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposureTarget_);
   }
	return DEVICE_OK;
}

/**
* Handles "Exposure Time" property.
*/
int AmScope::OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(exposureMs_);
	  
	  Toupcam_put_ExpoTime(m_Htoupcam, (unsigned int)(exposureMs_ * 1000));
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposureMs_);
   }

   return DEVICE_OK;
}

/**
* Handles "Analog gain" property.
*/
int AmScope::OnAGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(aGain_);

	  Toupcam_put_ExpoAGain(m_Htoupcam, (unsigned short)aGain_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(aGain_);
   }

   return DEVICE_OK;
}

/**
* Handles "White Balance R gain" property.
*/
int AmScope::OnWhiteBalanceRGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   long wbRGain;
      pProp->Get(wbRGain);

	  wbGain_[0] = wbRGain;

	  Toupcam_put_WhiteBalanceGain(m_Htoupcam, wbGain_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)wbGain_[0]);
   }

   return DEVICE_OK;
}

/**
* Handles "White Balance G gain" property.
*/
int AmScope::OnWhiteBalanceGGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   long wbGGain;
      pProp->Get(wbGGain);
	  wbGain_[1] = wbGGain;

	  Toupcam_put_WhiteBalanceGain(m_Htoupcam, wbGain_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)wbGain_[1]);
   }

   return DEVICE_OK;
}

/**
* Handles "White Balance B gain" property.
*/
int AmScope::OnWhiteBalanceBGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   long wbBGain;
      pProp->Get(wbBGain);
	  wbGain_[2] = wbBGain;

	  Toupcam_put_WhiteBalanceGain(m_Htoupcam, wbGain_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)wbGain_[2]);
   }

   return DEVICE_OK;
}

/**
* Handles "PixelType" property.
*/
int AmScope::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   if(IsCapturing())
	   {
		   return DEVICE_CAMERA_BUSY_ACQUIRING;
	   }

      string val;
      pProp->Get(val);
      if (val.compare(g_PixelType_8bit) == 0)
	  {
		  nComponents_ = 1;
		  bytesPerPixel_ = 1;
		  bitDepth_ = 8;
		  Toupcam_put_Chrome(m_Htoupcam, TRUE); // put camera in 8bit grey scale monochrome mode
	  }
	  else if (val.compare(g_PixelType_32bitRGB) == 0)
	  {
		  nComponents_ = 4;
		  bytesPerPixel_ = 4;
		  bitDepth_ = 8;
		  Toupcam_put_Chrome(m_Htoupcam, FALSE); // put camera in 24 bit RGB color mode
	  }
      else
         assert(false); // this should never happen

	  CreateCameraImageBuffer();
      ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (bytesPerPixel_ == 1)
	  {
         pProp->Set(g_PixelType_8bit);
	  }
	  else if (bytesPerPixel_ == 4)
	  {
         pProp->Set(g_PixelType_32bitRGB);
	  }
      else
         assert(false); // this should never happen
   }

   return DEVICE_OK;
}

/**
* Handles "BitDepth" property.
*/
int AmScope::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
		 {
            return DEVICE_CAMERA_BUSY_ACQUIRING;
		 }

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
		bytesPerPixel_ = 1;			

         // automagickally change pixel type when bit depth exceeds possible value
         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
			 bytesPerPixel_ = 1;
         }
		 else if ( pixelType.compare(g_PixelType_32bitRGB) == 0)
		 {
			 bytesPerPixel_ = 4;
		 }

		 ResizeImageBuffer();

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

/**
* Handles "CameraCCDXSize" property.
*/
int AmScope::OnCameraCCDXSize(MM::PropertyBase* pProp , MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
    {
		pProp->Set(IMAGE_WIDTH);
    }
    else if (eAct == MM::AfterSet)
    {
		long value;
        pProp->Get(value);
	    if ( (value < 16) || (4096 < value))
		{
			return DEVICE_ERR;  // invalid image size
		}

	    if( value != IMAGE_WIDTH)
	    {
		    IMAGE_WIDTH = value;
		    ResizeImageBuffer();
	    }
    }
	return DEVICE_OK;
}

/**
* Handles "CameraCCDYSize" property.
*/
int AmScope::OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(IMAGE_HEIGHT);
   }
   else if (eAct == MM::AfterSet)
   {
      long value;
      pProp->Get(value);
		if ( (value < 16) || (3286 < value))
		{
			return DEVICE_ERR;  // invalid image size
		}

		if( value != IMAGE_HEIGHT)
		{
			IMAGE_HEIGHT = value;
			ResizeImageBuffer();
		}
   }
	return DEVICE_OK;
}

/**
* Handles "Resolution" property.
*/
int AmScope::OnPixelResolusion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   if(IsCapturing())
	   {
		   return DEVICE_CAMERA_BUSY_ACQUIRING;
	   }

      string val;
      pProp->Get(val);
      if (val.compare(g_PixelResolution1) == 0)
	  {
		  resolution_ = 1;
		  pixelSizeXUm_ = orgPixelSizeXUm_;
		  pixelSizeYUm_ = orgPixelSizeYUm_;

		  Toupcam_Stop(m_Htoupcam); // put camera in stop mode
		  Toupcam_put_eSize(m_Htoupcam, 0); // sets the resolution 4096 * 3286
	  }
	  else if (val.compare(g_PixelResolution2) == 0)
	  {
		  resolution_ = 2;
		  pixelSizeXUm_ = orgPixelSizeXUm_ * 2;
		  pixelSizeYUm_ = orgPixelSizeYUm_ * 2;

		  Toupcam_Stop(m_Htoupcam); // put camera in stop mode
		  Toupcam_put_eSize(m_Htoupcam, 1); // sets the resolution 2048 * 1644
	  }
	  else if (val.compare(g_PixelResolution3) == 0)
	  {
		  resolution_ = 3;
		  pixelSizeXUm_ = orgPixelSizeXUm_ * 4;
		  pixelSizeYUm_ = orgPixelSizeYUm_ * 4;

		  Toupcam_Stop(m_Htoupcam); // put camera in stop mode
		  Toupcam_put_eSize(m_Htoupcam, 2); // sets the resolution 1024 * 822
	  }
      else
         assert(false); // this should never happen

	  Toupcam_StartPullModeWithCallback(m_Htoupcam, NULL, NULL);

	  CreateCameraImageBuffer();
	  ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (resolution_ == 1)
	  {
         pProp->Set(g_PixelResolution1);
	  }
	  else if (resolution_ == 2)
	  {
         pProp->Set(g_PixelResolution2);
	  }
	  else if (resolution_ == 3)
	  {
         pProp->Set(g_PixelResolution3);
	  }
      else
         assert(false); // this should never happen
   }

   return DEVICE_OK;
}

/**
* Handles Flip Horizontal
*/
int AmScope::OnFlipHorizontal(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	   pProp->Set(flipHorizontal_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(flipHorizontal_);

	   BOOL b = false;
	  if (flipHorizontal_.compare("TRUE") == 0)
	  {
		  b = true;
	  }

	  Toupcam_put_HFlip(m_Htoupcam, b);
   }
   return DEVICE_OK;
}

/**
* Handles Flip Vertical
*/
int AmScope::OnFlipVertical(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(flipVertical_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(flipVertical_);

	  BOOL b = false;
	  if (flipVertical_.compare("TRUE") == 0)
	  {
		  b = true;
	  }

	  Toupcam_put_VFlip(m_Htoupcam, b);
   }
   return DEVICE_OK;
}

/**
* Handles Auto level range property
*/
int AmScope::OnAutoLevelRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   if (autoLevelRange_ == 1)
	   {
		   pProp->Set("1");
	   }
	   else
	   {
		   pProp->Set("0");
	   }
   }
   else if (eAct == MM::AfterSet)
   {
	   string val;
      pProp->Get(val);

	  if (val.compare("1") == 0)
	  {
		 autoLevelRange_ = 1;
	  }
	  else
	  {
		  autoLevelRange_ = 0;
	  }

	  if (autoLevelRange_ == 1)
	  {
		  Toupcam_LevelRangeAuto(m_Htoupcam);
		  // Update the range values after auto level set
		  Toupcam_get_LevelRange(m_Htoupcam, aLow_, aHigh_);
	  }
	  else
	  {
		  Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
	  }
   }
   return DEVICE_OK;
}

/**
* Handles level range R min property
*/
int AmScope::OnLevelRangeRMin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aLow_[2]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val > aHigh_[2])		// min should not go beyond max
	   {
		   val = aHigh_[2];
	   }

	   aLow_[2] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles level range G min property
*/
int AmScope::OnLevelRangeGMin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aLow_[1]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val > aHigh_[1])		// min should not go beyond max
	   {
		   val = aHigh_[1];
	   }

	   aLow_[1] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles level range B min property
*/
int AmScope::OnLevelRangeBMin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aLow_[0]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val > aHigh_[0])		// min should not go beyond max
	   {
		   val = aHigh_[0];
	   }

	   aLow_[0] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles level range R max property
*/
int AmScope::OnLevelRangeRMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aHigh_[2]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val < aLow_[2])		// max should not go below min
	   {
		   val = aLow_[2];
	   }

	   aHigh_[2] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles level range G max property
*/
int AmScope::OnLevelRangeGMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aHigh_[1]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val < aLow_[1])		// max should not go below min
	   {
		   val = aLow_[1];
	   }

	   aHigh_[1] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles level range B max property
*/
int AmScope::OnLevelRangeBMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set((long)aHigh_[0]);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   if (val < aLow_[0])		// max should not go below min
	   {
		   val = aLow_[0];
	   }

	   aHigh_[0] = (unsigned short)val;
	   autoLevelRange_ = 0;

	   Toupcam_put_LevelRange(m_Htoupcam, aLow_, aHigh_);
   }
   return DEVICE_OK;
}

/**
* Handles Pixel Size X property
*/
int AmScope::OnPixelSizeXUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set(pixelSizeXUm_);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   pixelSizeXUm_ = (float)val;
   }
   return DEVICE_OK;
}

/**
* Handles Pixel Size Y property
*/
int AmScope::OnPixelSizeYUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
   {
	   pProp->Set(pixelSizeYUm_);
   }
   else if (eAct == MM::AfterSet)
   {
	   long val;
       pProp->Get(val);
	   pixelSizeYUm_ = (float)val;
   }
   return DEVICE_OK;
}

/**
* Handles fast image property
*/
//int AmScope::OnFastImage(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//   if (eAct == MM::AfterSet)
//   {
//      long tvalue = 0;
//      pProp->Get(tvalue);
//		fastImage_ = (0==tvalue)?false:true;
//   }
//   else if (eAct == MM::BeforeGet)
//   {
//      pProp->Set(fastImage_?1L:0L);
//   }
//
//   return DEVICE_OK;
//}


///////////////////////////////////////////////////////////////////////////////
// Private AmScope methods
///////////////////////////////////////////////////////////////////////////////
/**
* Create internal image buffer to the chosen pixel type property values.
*/
void AmScope::CreateCameraImageBuffer()
{
	unsigned int xOffset, yOffset;
	Toupcam_get_Roi(m_Htoupcam, &xOffset, &yOffset, (unsigned*)&m_header.biWidth, (unsigned*)&m_header.biHeight);
	IMAGE_WIDTH = m_header.biWidth;
	IMAGE_HEIGHT = m_header.biHeight;	

	char buf[MM::MaxStrLength];
    GetProperty(MM::g_Keyword_PixelType, buf);
    std::string pixelType(buf);

	if (pixelType.compare(g_PixelType_8bit) == 0)
	{
		m_header.biBitCount = 8;
	}
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
		m_header.biBitCount = 24;
	}
	else
	{
		m_header.biBitCount = 24;
	}

	MMThreadGuard g(imgPixelsLock_);

	if (m_pData)
	{
		free(m_pData);
		m_pData = NULL;
	}
	m_header.biSizeImage = TDIBWIDTHBYTES(IMAGE_WIDTH * m_header.biBitCount) * IMAGE_HEIGHT;
	m_pData = (BYTE*)malloc(m_header.biSizeImage);
}

/**
* Sync internal image buffer size to the chosen property values.
*/
int AmScope::ResizeImageBuffer()
{
	char buf[MM::MaxStrLength];

   int ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

	std::string pixelType(buf);

    if (pixelType.compare(g_PixelType_8bit) == 0)
    {
		bytesPerPixel_ = 1;
    }
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
      bytesPerPixel_ = 4;
	}
	MMThreadGuard g(imgPixelsLock_);

	img_.Resize(IMAGE_WIDTH/binning_, IMAGE_HEIGHT/binning_, bytesPerPixel_);

    return DEVICE_OK;
}

/**
 * Generate an image with fixed value for all pixels
 */
void AmScope::GenerateEmptyImage()
{
   MMThreadGuard g(imgPixelsLock_);
   if (img_.Height() == 0 || img_.Width() == 0 || img_.Depth() == 0)
   {
      return;
   }

   unsigned char* pBuf = const_cast<unsigned char*>(img_.GetPixels());
   memset(pBuf, 0, img_.Height() * img_.Width() * img_.Depth());
}

/**
 * Generate an image with live stream value for each pixel
 */
void AmScope::GenerateCameraImage()
{
	char buf[MM::MaxStrLength];
    GetProperty(MM::g_Keyword_PixelType, buf);
    std::string pixelType(buf);

	MMThreadGuard g(imgPixelsLock_);

	unsigned nWidth = 0, nHeight = 0;
	if (pixelType.compare(g_PixelType_8bit) == 0)
	{
		HRESULT hr = Toupcam_PullImage(m_Htoupcam, m_pData, 8, &nWidth, &nHeight);

		if (FAILED(hr))
		{
			return;
		}
	}
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
		HRESULT hr = Toupcam_PullImage(m_Htoupcam, m_pData, 24, &nWidth, &nHeight);

		if (FAILED(hr))
		{
			return;
		}
	}	

	// Make sure the read and write buffer sizes are equal
	if ((nWidth/binning_ != img_.Width()) || (nHeight/binning_ != img_.Height()))
	{
		return;
	}

	if (pixelType.compare(g_PixelType_8bit) == 0)
	{
		unsigned char* pBuf = const_cast<unsigned char*>(img_.GetPixels());
		unsigned char* pIn = m_pData;
		int counter = 0;
		for (unsigned int j = 0; j < nHeight; j++)
		{
			for (unsigned int k = 0; k < nWidth; k++)
			{
				long lIndex = nWidth * j + k;
				*(pBuf + lIndex) = pIn[counter++];
			}
		}
	}	
	else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
	{
		unsigned int * pBuf = (unsigned int*)(img_.GetPixels());
		unsigned char* pIn = m_pData;
		int counter = 0;	
		unsigned char rgbBytes[4];
		rgbBytes[3] = 0;
		for (unsigned int j = 0; j < nHeight; j++)
		{
			for (unsigned int k = 0; k < nWidth; k++)
			{
				long lIndex = nWidth * j + k;
				rgbBytes[0] = pIn[counter++];
				rgbBytes[1] = pIn[counter++];
				rgbBytes[2] = pIn[counter++];
				*(pBuf + lIndex) = *(unsigned long*)(&rgbBytes[0]);
			}
		}
	}	
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// AmScopeAutoFocus implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
void AmScopeAutoFocus::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_AutoFocusDeviceName);
}

int AmScopeAutoFocus::Initialize()
{
   ASHub* pHub = static_cast<ASHub*>(GetParentHub());
   if (pHub)
   {
      char hubLabel[MM::MaxStrLength];
      pHub->GetLabel(hubLabel);
      SetParentID(hubLabel); // for backward comp.
   }
   else
   {
      LogMessage(NoHubError);
   }

   if (initialized_)
   {
      return DEVICE_OK;
   }

   // set property list
   // -----------------

   // Name
   int ret = CreateStringProperty(MM::g_Keyword_Name, g_AutoFocusDeviceName, true);
   if (DEVICE_OK != ret)
   {
      return ret;
   }

   // Description
   ret = CreateStringProperty(MM::g_Keyword_Description, "Camera auto-focus adapter", true);
   if (DEVICE_OK != ret)
   {
      return ret;
   }

   running_ = false;   

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   initialized_ = true;

   return DEVICE_OK;
}

int ASHub::Initialize()
{
  	initialized_ = true;
 
	return DEVICE_OK;
}

int ASHub::DetectInstalledDevices()
{  
   ClearInstalledDevices();

   // make sure this method is called before we look for available devices
   InitializeModuleData();

   char hubName[MM::MaxStrLength];
   GetName(hubName); // this device name
   for (unsigned i=0; i<GetNumberOfDevices(); i++)
   { 
      char deviceName[MM::MaxStrLength];
      bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
      if (success && (strcmp(hubName, deviceName) != 0))
      {
         MM::Device* pDev = CreateDevice(deviceName);
         AddInstalledDevice(pDev);
      }
   }
   return DEVICE_OK; 
}

void ASHub::GetName(char* pName) const
{
   CDeviceUtils::CopyLimitedString(pName, g_HubDeviceName);
}

// End of AmScopeAutoFocus
//

