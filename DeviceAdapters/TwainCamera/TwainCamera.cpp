///////////////////////////////////////////////////////////////////////////////
// FILE:          TwainCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   generic Twain camera adapter
//                
// COPYRIGHT:     University of California, San Francisco, 2009
//                
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

#include "TwainCamera.h"
#include "TwainDevice.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>

#include "dbgbox.h"

using namespace std;
int TwainCamera::imageSizeW_ = 512;
int TwainCamera::imageSizeH_ = 512;
const double TwainCamera::nominalPixelSizeUm_ = 1.0;

const char* g_CameraDeviceName = "TwainCam";
#define ThisCameraType TwainCamera

const char* g_ChannelName = "Single channel";
const char* g_Unknown = "Unknown";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bit = "32bitRGB";

// constants for naming color modes
const char* g_ColorMode_Grayscale = "Grayscale";
const char* g_ColorMode_RGB = "RGB-32bit";


// g_hinstDLL holds this DLL's instance handle. It is initialized in response
// to the DLL_PROCESS_ATTACH message. This handle is passed to CreateWindow()
// when a window is created, just before opening the data source manager.

HINSTANCE g_hinstDLL;

BOOL APIENTRY DllMain(HINSTANCE hinstDLL,
                      DWORD ul_reason_for_call,
                      LPVOID /*lpReserved*/)
{
   switch (ul_reason_for_call)
   {
      case DLL_PROCESS_ATTACH:
         g_hinstDLL = hinstDLL;
         break;
   }
   return TRUE;
}


// mutex
static MMThreadLock g_lock;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "Twain camera");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

	MM::Device* pD = NULL;
	try
	{
		// decide which device class to create based on the deviceName parameter
		if (strcmp(deviceName, g_CameraDeviceName) == 0)
		{
			// create camera
			pD = new TwainCamera();
		}
	}

	catch(TwainBad& ex)
	{
		
		std::ostringstream  messs;
		messs << " TwainDevice construction fails: " << ex.ReasonText() << std::endl;
		OutputDebugString(messs.str().c_str());
	}
	catch(...)
	{
		std::ostringstream  messs;
		messs << " TwainDevice construction fails: unrecognized exception" << std::endl;
		OutputDebugString(messs.str().c_str());
	}

	return pD;  

}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// ThisCameraType must be defined 

#include "CameraSequenceThread.h"






///////////////////////////////////////////////////////////////////////////////
// TwainCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* TwainCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
TwainCamera::TwainCamera() : 
CCameraBase<TwainCamera> (),
initialized_(false),
busy_(false),
readoutUs_(0),
color_(true),
rawBuffer_(0),
stopOnOverflow_(true),
pTwainDevice_(NULL),
cameraStarted_(false),
stopRequest_(false),
thd_(NULL)
{
	imageSizeW_ = 1;
	imageSizeH_ = 1;

	// create a pre-initialization property and list all the available cameras

	// Spot sends us the Model Name + (serial number)
   CPropertyAction *pAct = new CPropertyAction (this, &TwainCamera::OnCamera);
   CreateProperty("TwainCamera", "", MM::String, false, pAct, true);
   AddAllowedValue( "TwainCamera", ""); // no camera yet


	pTwainDevice_ = new TwainDevice(this);

	std::vector<std::string> cams = pTwainDevice_->AvailableSources();
	std::vector<std::string>::iterator ii;
	for( ii = cams.begin(); cams.end()!=ii; ++ii)
	{
		AddAllowedValue( "TwainCamera", (*ii).c_str());
	}



   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();

   thd_ = new CameraSequenceThread(this);
}

/**
* TwainCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
TwainCamera::~TwainCamera()
{
   delete[] rawBuffer_;
   delete pTwainDevice_;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void TwainCamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
* Tells us if device is still processing asynchronous command.
* Required by the MM:Device API.
*/
bool TwainCamera::Busy()
{
   //Camera should be in busy state during exposure
   //IsCapturing() is used for determining if sequence thread is run
   //ToDo: guard for thread-safety
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
int TwainCamera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Twain Camera Device ", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, "Twain Camera", MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V2.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &TwainCamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

	//todo (1) add a new property which represents the acquisition mod supported by the specific camera
	// OR (2) use the binning property as a map to the different acuisition modes supported by the camera
   vector<string> binValues;
   binValues.push_back("1");

   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // pixel type
   pAct = new CPropertyAction (this, &TwainCamera::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bit, MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   color_=true;
   nRet = SetPixelTypesValues();
   assert(nRet == DEVICE_OK);

   // exposure
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false);
   assert(nRet == DEVICE_OK);

   // scan mode
   nRet = CreateProperty("ScanMode", "1", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // Vendor Specific Properties
	//todo  MOVE THIS TO THE CTOR so that it can be a configuration item!!!!!!!
   pAct = new CPropertyAction (this, &TwainCamera::OnVendorSettings);
	nRet = CreateProperty("vendor settings", "Hide", MM::String, false, pAct);
	AddAllowedValue("vendor settings","Show");
	AddAllowedValue("vendor settings","Hide");
   assert(nRet == DEVICE_OK);

	ostringstream mezzz;
	mezzz << __FILE__ << " " << __LINE__ << " TwainCamera::Initialize created all properties ";
	DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	

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

	
	initialized_ = true;


	// initialize image buffer
	//return SnapImage();

	nRet = StartTwainCamera();
	//nRet = DEVICE_OK;
	return nRet;
	
}

int TwainCamera::StartTwainCamera(void)

{
	int nRet = DEVICE_OK;
   MM::MMTime startTime = GetCurrentMMTime();
   //double exp = GetExposure();

	try
	{
		pTwainDevice_->SelectAndOpenSource(pTwainDevice_->CurrentSource());
		ostringstream mezzz;
		mezzz << __FILE__ << " " << __LINE__ << " SelectAndOpenSource opened " << pTwainDevice_->CurrentSource();
		DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);

	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Source Selection fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}

	try
	{
		pTwainDevice_->EnableCamera(true);
		ostringstream mezzz;
		mezzz << __FILE__ << " " << __LINE__ << " EnableCamera true ";
		DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);		
		
		uint16_t imheight;
		uint16_t imwidth;
		unsigned int depth;
		char bytesppixel;
		int binsize = 1; //todo!!!!!
		pTwainDevice_->GetActualImageSize(imheight, imwidth, bytesppixel);
		depth = static_cast<unsigned int>(bytesppixel);

		mezzz.clear();
		mezzz << __FILE__ << " " << __LINE__ << " GetActualImageSize returns  imheight " << imheight <<" imwidth " << imwidth << " depth " << depth;
		DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	

		// a µManager thing.....
		if (3 == depth)
		{
			depth = 4;
		}

		if(( img_[0].Width() != imwidth) || (img_[0].Height() != imheight) || (img_[0].Depth() != depth))
		{
			ResizeImageBuffer(imwidth,imheight,depth,binsize);
			mezzz.clear();
			mezzz << __FILE__ << " " << __LINE__ << " ResizeImageBuffer returned ";
			DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	
		}
		pTwainDevice_->EnableCamera(false);
		mezzz.clear();
		mezzz << __FILE__ << " " << __LINE__ << " EnableCamera false returned  ";
		DbgBox(mezzz.str(), " twain debug", MB_OK&MB_SYSTEMMODAL);	

	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Image acquisition sequence fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}
	catch(...)
	{
		std::ostringstream  messs;
		messs << " TwainDevice StartTwainCamera sequence fails:  an unknown exception in the Twain .ds." << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}
	cameraStarted_ = true;
	return nRet;

}

int TwainCamera::SetPixelTypesValues(){
   int ret = DEVICE_ERR;
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


/**
* Returns the number of physical channels in the image.
*/
unsigned int TwainCamera::GetNumberOfComponents() const
{
   unsigned int n = 0;
   char buf[MM::MaxStrLength];

   (void)GetProperty(MM::g_Keyword_PixelType, buf);
   if (strcmp(buf, g_PixelType_8bit) == 0)
      n = 1;
   else if (strcmp(buf, g_PixelType_16bit) == 0)
      n = 1;
   else if (strcmp(buf, g_PixelType_32bit) == 0)
      n = 4;
   
   return n;
}

int TwainCamera::GetComponentName(unsigned int channel, char* name)
{
   int ret=DEVICE_ERR;
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
int TwainCamera::Shutdown()
{
	// todo finish this
   initialized_ = false;
   StopSequenceAcquisition();
   delete[] rawBuffer_;
   rawBuffer_ = 0;
   return DEVICE_OK;
}

int TwainCamera::SnapImage()
{

	//todo  move 'nexttwainimage' to GetImage!!!!!!!!
	
	if(!cameraStarted_) 
	{
		int nRet = StartTwainCamera();
		if (DEVICE_OK != nRet) return nRet;
	}

   MM::MMTime startTime = GetCurrentMMTime();
   //double exp = GetExposure();

	try
	{
		pTwainDevice_->SelectAndOpenSource(pTwainDevice_->CurrentSource());
	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Source Selection fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}

	try
	{
		pTwainDevice_->EnableCamera(true);
	//	NextTwainImageIntoImageBuffer(img_[0]);
   //	pTwainDevice_->EnableCamera(false);
	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice SnapImage acquisition sequence fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}
	catch(...)
	{
		std::ostringstream  messs;
		messs << " TwainDevice SnapImage fails in the Twain .ds:  an unknown exception." << std::endl;
		LogMessage(messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_ERR;
	}


   readoutStartTime_ = GetCurrentMMTime();
   CDeviceUtils::SleepMs((long)GetExposure());

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
const unsigned char* TwainCamera::GetImageBuffer()
{
	try
	{
		NextTwainImageIntoImageBuffer(img_[0]);
		pTwainDevice_->EnableCamera(false);
	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Image acquisition sequence fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		//return DEVICE_LOCALLY_DEFINED_ERROR;
	}
	catch(...)
	{
		std::ostringstream  messs;
		messs << " TwainDevice GetImageBuffer fails in the Twain .ds:  an unknown exception" << std::endl;
		LogMessage(messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		//return DEVICE_ERR;
	}

//image processor now called from core


   while (GetCurrentMMTime() - readoutStartTime_ < MM::MMTime(readoutUs_)) {CDeviceUtils::SleepMs(5);}
   unsigned long singleChannelSize = img_[0].Width() * img_[0].Height() * img_[0].Depth();

   memcpy(rawBuffer_, img_[0].GetPixels(), singleChannelSize);

   return rawBuffer_;
}


/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/
const unsigned int* TwainCamera::GetImageBufferAsRGB32()
{
   return (unsigned int*) GetImageBuffer();
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned TwainCamera::GetImageWidth() const
{
   return img_[0].Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned TwainCamera::GetImageHeight() const
{
   return img_[0].Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned TwainCamera::GetImageBytesPerPixel() const
{
   return img_[0].Depth();
	
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned TwainCamera::GetBitDepth() const
{
   return 8 * GetImageBytesPerPixel();
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long TwainCamera::GetImageBufferSize() const
{
   return img_[0].Width() * img_[0].Height() * GetImageBytesPerPixel();
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int TwainCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if(IsCapturing())
      return ERR_BUSY_ACQIRING;

   if (xSize == 0 && ySize == 0)
      // effectively clear ROI
      ResizeImageBuffer();
   else
   {
      char buf[MM::MaxStrLength];
      int ret = GetProperty(MM::g_Keyword_Binning, buf);
      if (ret != DEVICE_OK)
         return ret;
      long binSize = atol(buf);

      ret = GetProperty(MM::g_Keyword_PixelType, buf);
      if (ret != DEVICE_OK)
         return ret;

      int byteDepth=1;
      if (strcmp(buf, g_PixelType_8bit) == 0)
         byteDepth = 1;
      else if (strcmp(buf, g_PixelType_16bit) == 0)
         byteDepth = 2;
      else if (strcmp(buf, g_PixelType_32bit) == 0)
         byteDepth = 4;
      else
         return DEVICE_ERR;

      // apply ROI

		try
		{
			// WATCH OUT HERE! for an image spanning, let's say 1, 1, 1024, 1024, Twain will return an image size of 1023 x 1023

			uint16_t top = static_cast<uint16_t>(y);
			uint16_t left = static_cast<uint16_t>(x);
			uint16_t bottom = static_cast<uint16_t>(y + ySize);
			uint16_t right = static_cast<uint16_t>( x + xSize);
			uint16_t actualwidth, actualheight;
			unsigned char actualdepth;
			pTwainDevice_->SetROIRectangle( left,  top,  right,  bottom, &actualwidth, &actualheight, &actualdepth  );
			// for 3 color image µManager expects R,G,B,transparency
			if (3 == actualdepth)
			{
				actualdepth = 4;
			}
			ResizeImageBuffer(actualwidth*binSize, actualheight*binSize, actualdepth, binSize);

		}
		catch( TwainBad& ex)
		{
			std::ostringstream  messs;
			messs << " TwainDevice ROI operation fails: " << ex.ReasonText() << std::endl;
			LogMessage(messs.str().c_str());
			SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
			OutputDebugString(messs.str().c_str());
			return DEVICE_LOCALLY_DEFINED_ERROR;
		}
   }

   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int TwainCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   //x = 0;
   //y = 0;

   //xSize = img_[0].Width();
   //ySize = img_[0].Height();
	try
	{
		uint16_t top;
		uint16_t left;
		uint16_t bottom;
		uint16_t right;

		pTwainDevice_->GetROIRectangle( left,  top,  right,  bottom );
		x = left;
		y = top;
		xSize = right - left;
		ySize = bottom - top;
	}

	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice ROI fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_LOCALLY_DEFINED_ERROR;
	}


   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int TwainCamera::ClearROI()
{

	if(!cameraStarted_) 
	{
		int nRet = StartTwainCamera();
		if (DEVICE_OK != nRet) return nRet;
	}
   if (Busy())
		return ERR_BUSY_ACQIRING;

	uint16_t top;
	uint16_t left;
	uint16_t bottom;
	uint16_t right;

	pTwainDevice_->GetWholeCaptureRectangle( left,  top,  right,  bottom );
	
	unsigned int x = left;
	unsigned int y = top;
	unsigned int xSize = right - left + 1;
	unsigned int ySize = bottom - top + 1;
	SetROI(x, y, xSize, ySize);

   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double TwainCamera::GetExposure() const
{
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
void TwainCamera::SetExposure(double exp)
{

   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int TwainCamera::GetBinning() const
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
int TwainCamera::SetBinning(int binFactor)
{
   if(IsCapturing())
      return ERR_BUSY_ACQIRING;

   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}


///////////////////////////////////////////////////////////////////////////////
// TwainCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

int TwainCamera::OnVendorSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int nRet = DEVICE_OK;
	if(  MM::AfterSet == eAct)
	{
		std::string value;
		pProp->Get(value);
		if( "Show" == value)
		{
			try
			{
				pTwainDevice_->LaunchVendorSettings();
			}
			catch(TwainBad& ex)
			{
				std::ostringstream  messs;
				messs << " TwainDevice LaunchVendorSettings: " << ex.ReasonText() << std::endl;
				LogMessage(messs.str().c_str());
				SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR,messs.str().c_str());
				nRet = DEVICE_LOCALLY_DEFINED_ERROR;
			}
			pProp->Set("Hide");
		}
		
	}

	else if(MM::BeforeGet == eAct)
	{
		pProp->Set("Hide");

	}
		
	return nRet;
}

/**
* Handles "Binning" property.
*/
int TwainCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{


   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAN_NOT_SET_PROPERTY;
         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);

         if (binFactor > 0 && binFactor < 10)
         {
		
					if(!cameraStarted_) 
					{
						int nRet = StartTwainCamera();
						if (DEVICE_OK != nRet) return nRet;
					}
	            ret = ResizeImageBuffer(imageSizeW_, imageSizeH_, img_[0].Depth() , binFactor);

         }
         else
         {
            // on failure reset default binning of 1
            ResizeImageBuffer();
            pProp->Set(1L);
            return ERR_UNKNOWN_MODE;
         }
      }break;
   case MM::BeforeGet:
      {
         // the user is requesting the current value for the property, so
         // either ask the 'hardware' or let the system return the value
         // cached in the property.
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}






/**
* Handles "PixelType" property.
*/

int TwainCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAN_NOT_SET_PROPERTY;

         string pixelType;
         pProp->Get(pixelType);

         //
         if (pixelType.compare(g_PixelType_8bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 1);
         }
         else if (pixelType.compare(g_PixelType_16bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 2);
         }
         else if (pixelType.compare(g_PixelType_32bit) == 0)
         {
            ret = ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 4);
         }
         else
         {
            // on error switch to default pixel type
            pProp->Set(g_PixelType_8bit);
            ResizeImageBuffer(img_[0].Width(), img_[0].Height(), 1);
            ret = ERR_UNKNOWN_MODE;
         }

      }break;
   case MM::BeforeGet:
      {
         // the user is requesting the current value for the property, so
         // either ask the 'hardware' or let the system return the value
         // cached in the property.
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/**
* Handles "ReadoutTime" property.
*/
int TwainCamera::OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::AfterSet)
   {
      if (Busy())
         return ERR_BUSY_ACQIRING;

      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = (long)(readoutMs * 1000.0);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}




int TwainCamera::OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// get the list of cameras from the driver (i.e. the dll)
	std::vector<std::string> cams = pTwainDevice_->AvailableSources();
	std::vector<std::string>::iterator ii;

	// match to the name which was set
   if (eAct == MM::AfterSet)
   {
      pProp->Get(deviceName_);

		for(ii = cams.begin(); ii!= cams.end(); ++ii)
		{
			if( deviceName_ == *ii)
			{
				pTwainDevice_->CurrentlySelectedSource(deviceName_);
				break;
			}
		}
   }
   else if (eAct == MM::BeforeGet)
   {

		pProp->Set(pTwainDevice_->CurrentlySelectedSource().c_str());
   }
   return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// Private TwainCamera methods
///////////////////////////////////////////////////////////////////////////////
/**
* Sync internal image buffer size to the chosen property values.
*/
int TwainCamera::ResizeImageBuffer(int imageSizeW /*= imageSize_*/, int imageSizeH /*= imageSize_*/)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return ret;
   long binSize = atol(buf);

   ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
      return ret;

   int byteDepth=1;
   if (strcmp(buf, g_PixelType_8bit) == 0)
      byteDepth = 1;
   else if (strcmp(buf, g_PixelType_16bit) == 0)
      byteDepth = 2;
   else if (strcmp(buf, g_PixelType_32bit) == 0)
      byteDepth = 4;
   else
      return DEVICE_ERR;

   return ResizeImageBuffer(imageSizeW, imageSizeH, byteDepth, binSize);

}
/**
* Sync internal image buffer size to the chosen property values.
*/
int TwainCamera::ResizeImageBuffer(int imageSizeW, int imageSizeH, int byteDepth, int binSize /*=1*/)
{

   img_[0].Resize(imageSizeW/binSize, imageSizeH/binSize, byteDepth);

   delete[] rawBuffer_;
   rawBuffer_ = new unsigned char[img_[0].Width() * img_[0].Height() * img_[0].Depth()];
   return DEVICE_OK;
}


int TwainCamera::NextTwainImageIntoImageBuffer(ImgBuffer& img)
{
	int nRet = DEVICE_OK;
	if(!cameraStarted_) 
	{
		nRet = StartTwainCamera();
		if (DEVICE_OK != nRet) return nRet;
	}

	if ( NULL == pTwainDevice_) 
		return DEVICE_ERR;


	int  bytesPerPixel; // always 4 for RGB
	// values from the device
	int sourceheight, sourcewidth, sourcedepth;
	char cdepth;
	char *pData;
	pData = 	pTwainDevice_->GetImage( sourceheight, sourcewidth, cdepth  );


	sourcedepth = (int)cdepth;
	if (3 == sourcedepth)
	{
		bytesPerPixel = 4;
	}
	else
	{
		bytesPerPixel = (int)sourcedepth;
	}


	if( (sourceheight != (int)img.Height()) || (sourcewidth != (int)img.Width()))
	{
		this->ResizeImageBuffer( sourcewidth, sourceheight, bytesPerPixel);
	}


	int destdepth = img.Depth();
	int destwidth = img.Width();
	int destheight = img.Height();

	//memset(ptemp, 0, destdepth*destwidth*destheight);

	// handle case where buffer doesn't match returned image size
	int xdest, ydest;//, xsource, ysource;
	int roffsetdest, goffsetdest, boffsetdest;
	int roffsetsource, goffsetsource, boffsetsource;

	int workingwidth = min(destwidth, sourcewidth);
	int workingheight = min(destheight, sourceheight);

	unsigned char* ptemp = img.GetPixelsRW();

	memset(ptemp,0, destdepth*destwidth*destheight);

#if 0 // no left right swap needed
	for( ydest = 0; ydest < workingheight; ++ydest)
	{
		for( xdest = 0; xdest < workingwidth; ++xdest)
		{
			roffsetdest = xdest*destdepth + destdepth*ydest*destwidth;
			goffsetdest = roffsetdest + 1;
			boffsetdest = goffsetdest + 1;

			roffsetsource = xdest*sourcedepth + sourcedepth*ydest*sourcewidth;
			goffsetsource = roffsetsource + 1;
			boffsetsource = goffsetsource + 1;
			ptemp[roffsetdest] = pData[roffsetsource];
			ptemp[goffsetdest] = pData[goffsetsource];			
			ptemp[boffsetdest] = pData[boffsetsource];
		}
	}
#endif



	for( ydest = 0; ydest < workingheight; ++ydest)
	{
		for( int xsource  = 0; xsource < workingwidth; ++xsource)
		{
			xdest = workingwidth - 1 - xsource;
			roffsetdest = xdest*destdepth + destdepth*ydest*destwidth;
			goffsetdest = roffsetdest + 1;
			boffsetdest = goffsetdest + 1;

			roffsetsource = xsource*sourcedepth + sourcedepth*ydest*sourcewidth;
			goffsetsource = roffsetsource + 1;
			boffsetsource = goffsetsource + 1;
			ptemp[roffsetdest] = pData[roffsetsource];
			ptemp[goffsetdest] = pData[goffsetsource];			
			ptemp[boffsetdest] = pData[boffsetsource];
		}
	}

	//img.SetPixels(ptemp);


	return nRet;

}

/**
* Starts continuous acquisition.
*
*/
int TwainCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{

	if(!cameraStarted_) 
	{
		int nRet = StartTwainCamera();
		if (DEVICE_OK != nRet) return nRet;
	}

	//pTwainDevice_->EnableCamera(true);
   ostringstream os;
   os << "Started camera streaming with an interval of " << interval_ms << " ms, for " << numImages << " images.\n";
   printf("%s", os.str().c_str());
   if (IsCapturing())
      return ERR_BUSY_ACQIRING;

   stopOnOverflow_ = stopOnOverflow;
   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

   double actualIntervalMs = max(GetExposure(), interval_ms);
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualIntervalMs)); 

   thd_->Start(numImages,actualIntervalMs);

   return DEVICE_OK;
}

int TwainCamera::PushImage()
{
	if(!cameraStarted_) 
	{
		int nRet = StartTwainCamera();
		if (DEVICE_OK != nRet) return nRet;
	}

	try
	{
		// TODO: call core to prepare for image snap
		pTwainDevice_->EnableCamera(true);
		NextTwainImageIntoImageBuffer(img_[0]);
		pTwainDevice_->EnableCamera(false);
	}
	catch( TwainBad& ex)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Image acquistion sequence fails: " << ex.ReasonText() << std::endl;
		LogMessage(messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_ERR;
	}
	catch(...)
	{
		std::ostringstream  messs;
		messs << " TwainDevice Image acquistion sequence fails:  an unknown exception." << std::endl;
		LogMessage(messs.str().c_str());
		OutputDebugString(messs.str().c_str());
		return DEVICE_ERR;
	}



   // process image
   // imageprocessor now called from core

   // insert image into the circular buffer
   GetImageBuffer(); // this effectively copies images to rawBuffer_

   // insert all three channels at once
   int ret = GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // repeat the insert
      return GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   } else
      return ret;
}
int TwainCamera::StopSequenceAcquisition()
{
	int nRet = this->CCameraBase<TwainCamera>::StopSequenceAcquisition();

	return nRet;
}

bool TwainCamera::IsCapturing() {
   return !thd_->IsStopped();
}

int TwainCamera::ThreadRun()
{
	int ret = SnapImage();
	if (DEVICE_OK != ret)
	{
		return ret;
	}

	const unsigned char* pI = GetImageBuffer();
	if (NULL == pI)
		return DEVICE_ERR;

	// insert all three channels at once
   ret = GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // repeat the insert
      return GetCoreCallback()->InsertMultiChannel(this, rawBuffer_, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   } else
      return ret;



}
