///////////////////////////////////////////////////////////////////////////////
// FILE:          TIScamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   TIS (TheImagingSource) camera module
//                
// AUTHOR:        Falk Dettmar, falk.dettmar@marzhauser-st.de, 02/26/2010
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2010
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//
//  these TIS cameras were tested
//  21AF04  21BF04
//  31AF03  31BF03
//  41AF02  41BF02
//
//

/*
Include these TIS files to the MM target directory:

TIS Runtime DLLs for Visual Studio™ 2008 generated applications:
(seem to work also with Visual Studio™ 2010 without any restrictions)
For 32 bit:
TIS_DShowLib08.dll 
TIS_UDSHL08_vc9.dll 
For 64 bit:
TIS_DShowLib08_x64.dll 
TIS_UDSHL08_vc9_x64.dll 

TIS Device Adapters:
Please add all files with the extension VDA from the appropriate directory for 32 or 64 bit. 

TIS Codec Adapters:
Please add all files with the extension TCA from the appropriate directory for 32 or 64 bit.

TIS Standard Frame Filters:
If there are own frame filters created, add all files with the extension FTF

System:
Use the VC runtime installations from Microsoft™.
*/



#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#endif
#include "../../MMDevice/ModuleInterface.h"

#include "TIScamera.h"


#include "..\..\..\3rdparty\TheImagingSource\classlib\include\tisudshl.h"
//#include "..\..\..\3rdparty\TheImagingSource\classlib\include\FilterFactory.h"
#include <algorithm>


#include "SimplePropertyAccess.h"


#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

#include <string>
#include <sstream>
#include <cmath>	//for ceil



// temp
#include "stdio.h"

bool requestShutdown;
using namespace std;
using namespace DShowLib;

// global constants
const char* g_DeviceName           = "TIS_DCAM";
const char* g_Keyword_PixelSize    = "PixelSize";
const char* g_Keyword_SerialNumber = "SerialNumber";
const char* g_FlipH                = "Flip_H";
const char* g_FlipV                = "Flip_V";
const char* g_FPS                  = "FPS";
const char* g_Keyword_Brightness   = "Brightness";
const char* g_On                   = "On";
const char* g_Off                  = "Off";
const char* g_Keyword_AutoExposure = "AutoExposure";

DShowLib::Grabber*                 pGrabber;
//  DShowLib::IFrameFilter*            pFilter;
DShowLib::tFrameHandlerSinkPtr     pSink;
DShowLib::tMemBufferCollectionPtr  pCollection;

static bool bApiAvailable_s;

#define NUMBER_OF_BUFFERS 1

BYTE* pBuf[NUMBER_OF_BUFFERS];


// ------------------------------ DLL main --------------------------------------
//
// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, DWORD  ul_reason_for_call, LPVOID /*lpReserved*/ ) 
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:

	case DLL_THREAD_ATTACH:
		break;
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
		requestShutdown = true;
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
   AddAvailableDeviceName(g_DeviceName, "TIScam");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// the device name will contain the name specified by the TIS driver
// so at the beginning of time we need to allow the 'device' to be created
// with an empty name, so that the pre-initialization properties can be filled in
MODULE_API MM::Device* CreateDevice(const char* szDeviceName)
{
   if (szDeviceName == 0)
      return 0;

   string strName(szDeviceName);
   
   if (strcmp(szDeviceName, g_DeviceName) == 0)
      return new CTIScamera();
   
   return 0;
}


/*==============================================================================
CTIScamera constructor/destructor
Setup default all variables and create device properties required to exist
before intialization. In this case, no such properties were required. All
properties will be created in the Initialize() method.

As a general guideline Micro-Manager devices do not access hardware in the
the constructor. We should do as little as possible in the constructor and
perform most of the initialization in the Initialize() method.
==============================================================================*/
CTIScamera::CTIScamera() : CCameraBase<CTIScamera> (),
   initialized_(false),

   lCCD_Width(100),
   lCCD_Height(100),
   iCCD_BitsPerPixel(8),
 
   numberOfChannels_(1),
   busy_(false),
   bColor_(false),
   sequenceRunning_(false),
   dExp_(100), //ms
   flipH_(false),
   flipV_(false),
   FPS_(10),
   Brightness_(10),

   bitDepth_(8),

   roiX_(0),
   roiY_(0),
   roiXSize_(0),
   roiYSize_(0),

   nominalPixelSizeUm_(1.0),

   acquiring_(false),
   interval_ms_ (0)



{
   // create a pre-initialization property and list all the available cameras

/*
// TIS sends us the Model Name + (serial number)
   CPropertyAction *pAct = new CPropertyAction (this, &CTIScamera::OnCamera);
   CreateProperty("TISCamera", "", MM::String, false, pAct, true);
   AddAllowedValue( "TISCamera", "cam1");
*/


   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   seqThread_ = new AcqSequenceThread(this); 


}



CTIScamera::~CTIScamera()
{
   delete seqThread_;

   if(initialized_)
   {
      DShowLib::ExitLibrary();
      delete pGrabber;
      Shutdown();
   }
}


/*==============================================================================
Obtains device name.
Required by the MM::Device API.
==============================================================================*/
void CTIScamera::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter. 
   CDeviceUtils::CopyLimitedString(name, g_DeviceName);
}



bool CTIScamera::Busy()
{
   return busy_;
}







///////////////////////////////////////////////////////////////////////////////
// Function name   : CTIScamera::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CTIScamera::Initialize()
{
CPropertyAction *pAct = NULL;
   if (initialized_) return DEVICE_OK;

   // Device Adapter Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "TIS generic 1394 driver module", MM::String, true);
   if (nRet != DEVICE_OK) return nRet;


   // Initialize the library.
   // If you have a trial version, the license key is 0 without quotation marks
   // Example: if(!DShowLib::InitLibrary( 0 ))
   //
   // If you have a licensed version (standard or professional), the license
   // key is a string in quotation marks. The license key has to be identical to 
   // the license key entered during the IC Imaging Control setup.
   // Example: if( !DShowLib::InitLibrary( "XXXXXXX" ))

   if (!DShowLib::InitLibrary(0))
   {
      LogMessage("TIScam InitLibrary failed. Wrong license key?", true);
      int msgboxID = MessageBox(
         NULL,
         "TIScam InitLibrary failed. Wrong license key?",
         "Initialisation",
         MB_ICONSTOP | MB_OK
         );
      return DEVICE_ERR;
   }
   pGrabber = new DShowLib::Grabber();
   assert(pGrabber);


#define TIS_DIALOG_BOX_WORKAROUND
#ifdef TIS_DIALOG_BOX_WORKAROUND
   pGrabber->showDevicePage();
#else
   DShowLib::Grabber::tVidCapDevListPtr pVidCapDevList = pGrabber->getAvailableVideoCaptureDevices();
   if ((pVidCapDevList == 0) || (pVidCapDevList->empty()))
   {
      delete pGrabber;
      return DEVICE_ERR; // no device available
   }
//the following line fails and exits with access violation during reading (root cause unknown)
   pGrabber->openDev(pVidCapDevList->at(0)); //open the (one and only) 1st device
#endif

   if (!pGrabber->isDevValid())
   {
      LogMessage("TIScam no valid camera was selected", true);

      int msgboxID2 = MessageBox(
         NULL,
         "TIScam no valid camera was selected",
         "Initialisation",
         MB_ICONSTOP | MB_OK
         );
      return DEVICE_ERR;
   }


   // read Camera Name
   nRet = CreateProperty(MM::g_Keyword_CameraName, pGrabber->getDev().c_str(), MM::String, true);
   if (nRet != DEVICE_OK) return nRet;

   // determine if the camera is able to do color
   if (pGrabber->getDev().c_str()[1] == 'F')
   {
      bColor_ = true;
      pSink = DShowLib::FrameHandlerSink::create(DShowLib::eRGB32,1); // RGB
   }
   else
   {
      bColor_ = false;
      pSink = DShowLib::FrameHandlerSink::create(DShowLib::eY800,1);  // 8 bit grayscale
   }


   //property PixelSize (read only)
   if ((strstr(pGrabber->getDev().c_str(), "21AF04")) || (strstr(pGrabber->getDev().c_str(), "21BF04")))
   {
      nominalPixelSizeUm_ = 5.6; //µm
   }
   else
   if ((strstr(pGrabber->getDev().c_str(), "31AF03")) || (strstr(pGrabber->getDev().c_str(), "31BF03"))
    || (strstr(pGrabber->getDev().c_str(), "41AF02")) || (strstr(pGrabber->getDev().c_str(), "41BF02")))
   {
     nominalPixelSizeUm_ = 4.65; //µm
   }
   else nominalPixelSizeUm_ = 1.0;

   nRet = CreateProperty(g_Keyword_PixelSize, CDeviceUtils::ConvertToString(nominalPixelSizeUm_), MM::Float, true, pAct);
   if (nRet != DEVICE_OK) return nRet;


   //property Serial Number (read only)
   LARGE_INTEGER iSerNum;
   if (pGrabber->getDev().getSerialNumber(iSerNum.QuadPart) == false) iSerNum.QuadPart = 0;
   std::ostringstream ossSerNum;
   ossSerNum << "0x" << std::hex << iSerNum.QuadPart << '\0';
   string SerNum = ossSerNum.str();
   nRet = CreateProperty(g_Keyword_SerialNumber, SerNum.c_str(), MM::String, true, pAct);
   if (nRet != DEVICE_OK) return nRet;

   //property FlipH (read only)
   if (pGrabber->isFlipHAvailable())
   {
     flipH_ = pGrabber->getFlipH();
     nRet = CreateProperty(g_FlipH, CDeviceUtils::ConvertToString(flipH_), MM::String, true, pAct);
     if (nRet != DEVICE_OK) return nRet;
   }

   //property FlipV (read only)
   if (pGrabber->isFlipVAvailable())
   {
     flipV_ = pGrabber->getFlipV();
     nRet = CreateProperty(g_FlipV, CDeviceUtils::ConvertToString(flipV_), MM::String, true, pAct);
     if (nRet != DEVICE_OK) return nRet;
   }

   //property frames per second (read only)
   if (pGrabber->isFrameRateListAvailable())
   {
     FPS_ = pGrabber->getFPS();
     nRet = CreateProperty(g_FPS, CDeviceUtils::ConvertToString(FPS_), MM::Float, true, pAct);
     if (nRet != DEVICE_OK) return nRet;
   }

   CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());

   long lMin, lMax, lValue;

   if (prop.isAvailable(VCDID_Exposure))
   {
      //range is typical from -13 to 5 (2^-13 = 1/8192 to 2^5 = 32) in seconds
      lMin   = prop.getRangeMin(VCDID_Exposure);
      lMax   = prop.getRangeMax(VCDID_Exposure);
      lValue = prop.getValue   (VCDID_Exposure);
           
      dExp_       = pow((double)2, (double)lValue) * 1000;  //convert s to ms
	   double dMin = pow((double)2, (double)lMin)   * 1000;  //convert s to ms
	   double dMax = pow((double)2, (double)lMax)   * 1000;  //convert s to ms

      pAct = new CPropertyAction (this, &CTIScamera::OnExposure);
      nRet = CreateProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp_), MM::Float, false, pAct);
      if (nRet != DEVICE_OK) return nRet;
      SetPropertyLimits(MM::g_Keyword_Exposure, dMin, dMax);

      if (prop.isAutoAvailable(VCDID_Exposure))
      {
         pAct = new CPropertyAction (this, &CTIScamera::OnAutoExposure);
         if (prop.getAuto(VCDID_Exposure)) nRet = CreateProperty(g_Keyword_AutoExposure, g_On,  MM::String, false, pAct);
	      else                              nRet = CreateProperty(g_Keyword_AutoExposure, g_Off, MM::String, false, pAct);
         if (nRet != DEVICE_OK) return nRet;

         vector<string> AutoExposureValues;
         AutoExposureValues.push_back(g_On);
         AutoExposureValues.push_back(g_Off);
         nRet = SetAllowedValues(g_Keyword_AutoExposure, AutoExposureValues);
         if (nRet != DEVICE_OK) return nRet;
      }
   }

   if (prop.isAvailable(VCDID_Brightness))
   {
      //range is typical from 0 to 255
      lMin        = prop.getRangeMin(VCDID_Brightness);
      lMax        = prop.getRangeMax(VCDID_Brightness);
      Brightness_ = prop.getValue   (VCDID_Brightness);
           
      pAct = new CPropertyAction (this, &CTIScamera::OnBrightness);
      nRet = CreateProperty(g_Keyword_Brightness, CDeviceUtils::ConvertToString(Brightness_), MM::Integer, false, pAct);
      if (nRet != DEVICE_OK) return nRet;
      SetPropertyLimits(g_Keyword_Brightness, lMin, lMax);

   }






/*
#ifdef _DEBUG
   smart_com<DShowLib::IFrameFilter> pFilter = DShowLib::FilterLoader::createFilter("ROI");
#else
   smart_com<IFrameFilter> pFilter = FilterLoader::createFilter("ROI");
#endif
*/


   //we use snap mode
   pSink->setSnapMode(true);

   //set the sink.
   pGrabber->setSinkType(pSink);

   // Prepare the live mode, to get the output size of the sink.
#ifdef _DEBUG
   if (!pGrabber->prepareLive(true)) return DEVICE_ERR;
#else
   if (!pGrabber->prepareLive(false)) return DEVICE_ERR;
#endif
   // Retrieve the output type and dimension of the handler sink.
   // The dimension of the sink could be different from the VideoFormat, when
   // you use filters.
   DShowLib::FrameTypeInfo info;
   pSink->getOutputFrameType(info);

   //sink oriented data size
   lCCD_Width        = info.dim.cx;
   lCCD_Height       = info.dim.cy;
   iCCD_BitsPerPixel = info.getBitsPerPixel();
//   tColorformatEnum cf = info.getColorformat();


   img_.Resize(lCCD_Width, lCCD_Height, iCCD_BitsPerPixel/8);


   // Allocate NUMBER_OF_BUFFERS image buffers of the above (info) buffer size.
   for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
   {
      pBuf[ii] = new BYTE[info.buffersize];
   }

	// Create a new MemBuffer collection that uses our own image buffers.
   pCollection = DShowLib::MemBufferCollection::create(info, NUMBER_OF_BUFFERS, pBuf);
   if (pCollection == 0) return DEVICE_ERR;
   if (!pSink->setMemBufferCollection(pCollection)) return DEVICE_ERR;


#ifdef _DEBUG
   if (!pGrabber->startLive(true)) return DEVICE_ERR;
#else
   if (!pGrabber->startLive(false)) return DEVICE_ERR;
#endif



   // binning
   pAct = new CPropertyAction (this, &CTIScamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
//   binValues.push_back("2");
//   binValues.push_back("4");
//   binValues.push_back("8");
   LogMessage("Setting Allowed Binning settings", true);
   SetAllowedValues(MM::g_Keyword_Binning, binValues);





   initialized_ = true;

   // initialize image buffer
   return SnapImage();
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : CTIScamera::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CTIScamera::Shutdown()
{
   initialized_ = false;

   pGrabber->stopLive();
   pGrabber->closeDev();
   for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
   {
      delete pBuf[ii];
   }

   return DEVICE_OK;
}



/*==============================================================================
Performs exposure and grabs a single image.
Required by the MM::Camera API.
SnapImage should start the image exposure in the camera and block until
the exposure is finished.  It should not wait for read-out and transfer of data.
Return DEVICE_OK on succes, error code otherwise.
==============================================================================*/
int CTIScamera::SnapImage()
{
   if (!pGrabber->isLive()) pGrabber->startLive(true);
   pSink->snapImages(1,2000);  //this command blocks until exposure is finished or timeout after 2000ms
   return DEVICE_OK;
}



/*==============================================================================
Returns the bit depth (dynamic range) of the pixel.
This does not affect the buffer size, it just gives the client application
a guideline on how to interpret pixel values.
Required by the MM::Camera API.
==============================================================================*/
unsigned CTIScamera::GetBitDepth() const
{
//   return (img_.Depth() * 8);
  // read current pixel type from camera
	/*
  unsigned int bitDepth  = (unsigned int) pImplementation_->BitDepth();
  if( 16 < bitDepth)
	  bitDepth /= 3;
	  */
  return 8;

}


/*==============================================================================
Returns the number of channels in this image.  This is '1' for grayscale cameras,
and '4' for RGB cameras.
==============================================================================*/
unsigned CTIScamera::GetNumberOfComponents() const
{
  return numberOfChannels_; //this is always 1 (also for RGB)
}



int CTIScamera::GetComponentName(unsigned channel, char* name)
{
  if (!bColor_ && (channel > 0))  return DEVICE_NONEXISTENT_CHANNEL;      
  
  switch (channel)
  {
  case 0:      
    if (!bColor_) 
      CDeviceUtils::CopyLimitedString(name, "Grayscale");
    else
      CDeviceUtils::CopyLimitedString(name, "B");
    break;

  case 1:
    CDeviceUtils::CopyLimitedString(name, "G");
    break;

  case 2:
    CDeviceUtils::CopyLimitedString(name, "R");
    break;

  default:
    return DEVICE_NONEXISTENT_CHANNEL;
    break;
  }
  return DEVICE_OK;
}
 





/*==============================================================================
Returns the current binning factor.
Required by the MM::Camera API.
==============================================================================*/
int CTIScamera::GetBinning () const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return 1;
   return atoi(buf);
}

/*==============================================================================
Sets binning factor.
Required by the MM::Camera API.
==============================================================================*/
int CTIScamera::SetBinning (int binFactor) 
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
} 



/*==============================================================================
Returns the size in bytes of the image buffer.
Required by the MM::Camera API.
==============================================================================*/
long CTIScamera::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}



/*==============================================================================
Returns image buffer X-size in pixels.
Required by the MM::Camera API.
==============================================================================*/
unsigned CTIScamera::GetImageWidth() const 
{
   return img_.Width();
}



/*==============================================================================
Returns image buffer Y-size in pixels.
Required by the MM::Camera API.
==============================================================================*/
unsigned CTIScamera::GetImageHeight() const 
{
   return img_.Height();
}



/*==============================================================================
Returns image buffer pixel depth in bytes.
Required by the MM::Camera API.
==============================================================================*/
unsigned CTIScamera::GetImageBytesPerPixel() const
{
   return img_.Depth() / GetNumberOfComponents();
}


/*==============================================================================
Returns the current exposure setting in milliseconds.
Required by the MM::Camera API.
==============================================================================*/
double CTIScamera::GetExposure() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
      return 0.0;
   return atof(buf);
}



/*==============================================================================
Set exposure in milliseconds.
Required by the MM::Camera API.
==============================================================================*/
void CTIScamera::SetExposure(double dExp_ms)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp_ms));
}


/*==============================================================================
Returns pixel data.
Required by the MM::Camera API.
GetImageBuffer will be called shortly after SnapImage returns.  
Use it to wait for camera read-out and transfer of data into memory
Return a pointer to a buffer containing the image data
The calling program will assume the size of the buffer based on the values
obtained from GetImageBufferSize(), which in turn should be consistent with
values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
The calling program also assumes that camera never changes the size of
the pixel buffer on its own. In other words, the buffer can change only if
appropriate properties are set (such as binning, pixel type, etc.)
==============================================================================*/
const unsigned char* CTIScamera::GetImageBuffer()
{
   unsigned char* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());

   memcpy(pixBuffer, pBuf[0], GetImageBufferSize());

   // capture complete
   return (unsigned char*) pixBuffer;
}



const unsigned int* CTIScamera::GetImageBufferAsRGB32()
{

   void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());

   memcpy(pixBuffer, pBuf[0], GetImageBufferSize());

//   snapInProgress_ = false;

   // capture complete
   return (unsigned int*)pixBuffer;

}




/*==============================================================================
Sets the camera Region Of Interest.
Required by the MM::Camera API.
This command will change the dimensions of the image.
Depending on the hardware capabilities the camera may not be able to configure the
exact dimensions requested - but should try do as close as possible.
If the hardware does not have this capability the software should simulate the ROI by
appropriately cropping each frame.
@param x - top-left corner coordinate
@param y - top-left corner coordinate
@param xSize - width
@param ySize - height
==============================================================================*/
int CTIScamera::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   roiX_     = uX;
   roiY_     = uY;
   roiXSize_ = uXSize;
   roiYSize_ = uYSize;
   return DEVICE_OK;
}



/*==============================================================================
Returns the actual dimensions of the current ROI.
==============================================================================*/
int CTIScamera::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
   uX     = roiX_;
   uY     = roiY_;
   uXSize = roiXSize_;
   uYSize = roiYSize_;
   return DEVICE_OK;
}



/*==============================================================================
Clear the current ROI.
==============================================================================*/
int CTIScamera::ClearROI()
{
   ResizeImageBuffer();
   roiX_ = 0;
   roiY_ = 0;
   roiXSize_ = lCCD_Width;
   roiYSize_ = lCCD_Height;
   return DEVICE_OK;
}



/*==============================================================================
Starts continuous acquisition.
==============================================================================*/
int CTIScamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   int ret;
   sequenceRunning_ = true;

   if (acquiring_)
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   stopOnOverflow_ = stopOnOverflow;
   interval_ms_ = interval_ms;   


   frameCount_ = 0;
   lastImage_ = 0;

   ostringstream os;
   os << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());


   // prepare the camera
/*
   currentBufferSize_ = frameBufferSize_;
   if (numImages < currentBufferSize_)
      currentBufferSize_ = numImages;
   ret = ResizeImageBuffer(currentBufferSize_);
   if (ret != DEVICE_OK)
      return ret;
*/

   double readoutTime;
   char rT[MM::MaxStrLength];
   ret = GetProperty(MM::g_Keyword_ReadoutTime, rT);
   readoutTime = atof(rT);

   os.clear();
   double interval = max(readoutTime, dExp_);
   os << interval;
   SetProperty(MM::g_Keyword_ActualInterval_ms, os.str().c_str());

   // prepare the core
   ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      ResizeImageBuffer();
      return ret;
   }

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());

   // start thread
   imageCounter_ = 0;
   sequenceLength_ = numImages;

   seqThread_->SetLength(numImages);

   seqThread_->Start();

   acquiring_ = true;

   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}



int CTIScamera::StopSequenceAcquisition()
{
   sequenceRunning_ = false;
   acquiring_ = false;

   seqThread_->Stop();
   seqThread_->wait();
 
//   return RestartSnapMode();

   return DEVICE_OK;
}



int CTIScamera::RestartSequenceAcquisition()
{
   return StartSequenceAcquisition(sequenceLength_ - imageCounter_, interval_ms_, stopOnOverflow_);
}



int CTIScamera::PrepareSequenceAcqusition()
{
   return DEVICE_OK;
}


/*==============================================================================
Flag to indicate whether Sequence Acquisition is currently running.
Return true when Sequence acquisition is activce, false otherwise
==============================================================================*/
bool CTIScamera::IsCapturing()
{
   return sequenceRunning_;
}



/*==============================================================================
* Sync internal image buffer size to the chosen property values.
==============================================================================*/
int CTIScamera::ResizeImageBuffer()
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
      return ret;
   long binSize = atol(buf);

   int byteDepth = 1;

   img_.Resize(lCCD_Width/binSize, lCCD_Height/binSize, byteDepth);
   return DEVICE_OK;
}










/**
* Handles "Binning" property.
*/
int CTIScamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch(eAct)
   {
   case MM::AfterSet:
      {
         if(IsCapturing())
            return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);

         if (binFactor > 0 && binFactor < 10)
         {
            img_.Resize(lCCD_Width/binFactor, lCCD_Height/binFactor);
            ret=DEVICE_OK;
         }
         else
         {
            // on failure reset default binning of 1
            img_.Resize(lCCD_Width, lCCD_Height);
            pProp->Set(1L);
            ret = DEVICE_ERR;
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


// Exposure Time
int CTIScamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());
      if (prop.isAvailable(VCDID_Exposure))
      {
         long lValue; //range is typical from -13 to 5 (2^-13 = 1/8192 to 2^5 = 32) in seconds
         lValue = prop.getValue(VCDID_Exposure);
         dExp_       = pow((double)2, (double)lValue) * 1000;  //convert s to ms
         pProp->Set(dExp_); //in ms
      }
   }
   else if (eAct == MM::AfterSet)
   {
//      bool acquiring = IsCapturing();
//      if (acquiring) StopSequenceAcquisition();

      double dExp;
      pProp->Get(dExp);

      CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());
      if (prop.isAvailable(VCDID_Exposure))
      {
         long lValue = floor(0.5 + log(dExp/1000)/log((double)2)); //convert ms to s
         prop.setValue(VCDID_Exposure, lValue);
      }
	  
	   dExp_ = dExp;

//      if (acquiring) RestartSequenceAcquisition();
   }
   return DEVICE_OK;
}


// Auto Exposure
int CTIScamera::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}





// Brightness
int CTIScamera::OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // brightness property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());
      if (prop.isAvailable(VCDID_Brightness))
      {
         long lValue;
         lValue = prop.getValue(VCDID_Brightness);
         Brightness_  = lValue;
         pProp->Set(Brightness_);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      long Brightness;
      pProp->Get(Brightness);

      CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());
      if (prop.isAvailable(VCDID_Brightness))
      {
         prop.setValue(VCDID_Brightness, Brightness);
      }
	  
	   Brightness_ = Brightness;

   }
   return DEVICE_OK;
}




/*==============================================================================
Continuous acquisition
==============================================================================*/
int AcqSequenceThread::svc(void)
{
   long imageCounter(0);

   long lValue;
   double dExp = 1000;  //default in ms

   CSimplePropertyAccess prop(pGrabber->getAvailableVCDProperties());
   if (prop.isAvailable(VCDID_Exposure))
   {
      //range is typical from -13 to 5 (2^-13 = 1/8192 to 2^5 = 32) in seconds
      lValue = prop.getValue   (VCDID_Exposure);
      dExp = pow((double)2, (double)lValue) * 1000;  //convert s to ms
   }

   do
   {
       // wait until the frame becomes available - waits in PushImage t.b.d.
      long lnTimeOut = (long) ((dExp + 50.0) * 1000.0);

      if (!pGrabber->isLive()) pGrabber->startLive(true);
      pSink->snapImages(1,(DWORD)-1);

      int ret = camera_->PushImage();
      if (ret != DEVICE_OK)
      {
	       ostringstream os;
          os << "PushImage() failed with errorcode: " << ret;
          camera_->LogMessage(os.str().c_str());
          Stop();
          return 2;
      }



      //printf("Acquired frame %ld.\n", imageCounter);                         
      imageCounter++;
   } while (!stop_ && imageCounter < numImages_);

   if (stop_)
   {
      printf("Acquisition interrupted by the user\n");
      return 0;
   }


//   camera_->RestartSnapMode();
   printf("Acquisition completed.\n");
   return 0;
}


/*==============================================================================
Waits for new image and inserts it into the circular buffer
==============================================================================*/
int CTIScamera::PushImage()
{

   frameCount_++;

   // get pixels
   void* imgPtr;
   imgPtr = pBuf[0];

   // process image
   MM::ImageProcessor* ip = GetCoreCallback()->GetImageProcessor(this);      
   if (ip)                                                                   
   {                                                                         
      int ret = ip->Process((unsigned char*) imgPtr, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
      if (ret != DEVICE_OK)
	  {
//		 acquiring = false;
         return ret;
	  }
   }                                                                         
   // This method inserts new image in the circular buffer (residing in MMCore)
   int ret = GetCoreCallback()->InsertImage(this, (unsigned char*) imgPtr,      
                                           GetImageWidth(),                  
                                           GetImageHeight(),                 
                                           GetImageBytesPerPixel());

   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      ret = GetCoreCallback()->InsertImage(this, (unsigned char*) imgPtr,      
                                           GetImageWidth(),                  
                                           GetImageHeight(),                 
                                           GetImageBytesPerPixel());
   }

   return ret;
}










