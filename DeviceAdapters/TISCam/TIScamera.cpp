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
//  these IEEE1394 cameras were tested:
//  DMK 21AF04 , DMK 21BF04 , DMK 31BF03
//  DFK 21BF04 , DFK 31BF03 , DFK 41BF02
//  these USB cameras were tested:
//  DFK 71UC02 , DFK 72BUC02
//
// debug with:
// method 1 = attach to process javaw.exe
// or
// method 2 = Command Line for debugging: 
// Program:  "C:\Programme\Java\jre1.6.0_03\bin\javaw.exe"
// Parameter: -Xmx640m -cp D:\PROGRA~1\MICRO-~1.3\ij.jar ij.ImageJ -ijpath D:\PROGRA~1\MICRO-~1.3\

/*
These files are required by the DLL:

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
The files VC100*.* shall be reachable from the path specifier.
*/



#ifdef WIN32
  #define WIN32_LEAN_AND_MEAN
  #include <windows.h>
  #include <Shlobj.h>
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "TIScamera.h"
#include <string>
#include <sstream>
#include <iomanip>
#include <math.h>
#include <iostream>

// temp
#include "stdio.h"

#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

using namespace std;
using namespace DShowLib;

// global constants
const char* g_DeviceName           = "TIS_DCAM";
const char* g_Keyword_PixelSize    = "Device PixelSize";
const char* g_Keyword_SerialNumber = "Device SerialNumber";
const char* g_FlipH                = "Device Flip_H";
const char* g_FlipV                = "Device Flip_V";
const char* g_Keyword_Brightness   = "Property Brightness";
const char* g_Keyword_Gain         = "Property Gain";
const char* g_Keyword_Gain_Auto    = "Property Gain_Auto";
const char* g_Keyword_WhiteBalance = "Property White_Balance";
const char* g_Keyword_WhiteBalance_Auto = "Property White_Balance_Auto";
const char* g_On                   = "On";
const char* g_Off                  = "Off";
const char* g_Keyword_AutoExposure = "Exposure Auto";
const char* g_Keyword_DeNoise      = "DeNoise";


// singleton instance
CTIScamera* CTIScamera::instance_ = 0;
unsigned int CTIScamera::refCount_ = 0;

// global driver thread lock
MMThreadLock g_DriverLock;

static bool bApiAvailable_s;


DShowLib::Grabber*                 pGrabber;
DShowLib::tFrameHandlerSinkPtr     pSink;
DShowLib::tMemBufferCollectionPtr  pCollection;


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
   AddAvailableDeviceName(g_DeviceName, "The Imaging Source");
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
   {
      return CTIScamera::GetInstance();
   }
   
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

   lCCD_Width(0),
   lCCD_Height(0),
   uiCCD_BitsPerPixel(8),
 
binSize_(1),
currentExpMS_(12.34), //ms

   busy_(false),
   bColor_(false),
   sequenceRunning_(false),
   sequencePaused_(false),

//startTime_(0),

   flipH_(false),
   flipV_(false),
   FPS_(10),
   Brightness_(10),
   WhiteBalance_(10),
   Gain_(0),
   DeNoiseLevel_(0),

   roiX_(0),
   roiY_(0),
   roiXSize_(0),
   roiYSize_(0),

   sequenceStartTime_(0),

   interval_ms_ (0),
   seqThread_(0),

   stopOnOverflow_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   m_pSimpleProperties = NULL;
   seqThread_ = new AcqSequenceThread(this); 

   // create a pre-initialization property and list all the available cameras
   char szPath[MAX_PATH];
   XMLPath = "";

   if (SHGetSpecialFolderPath( 0, szPath,CSIDL_APPDATA , 0  ) == TRUE)
   {
      XMLPath = szPath;
      XMLPath += "\\device.xml";
   }
}



CTIScamera::~CTIScamera()
{
   DriverGuard dg(this);
   delete seqThread_;

   refCount_--;
   if (refCount_ == 0) {
     if(initialized_)
     {
        DShowLib::ExitLibrary();
        delete pGrabber;
        pGrabber = 0;
        Shutdown();
     }

     if( m_pSimpleProperties != NULL )
     {
        delete m_pSimpleProperties;
     }
     // clear the instance pointer
     instance_ = 0;
   }
}


CTIScamera* CTIScamera::GetInstance()
{
   instance_ = new CTIScamera();
   refCount_++;
   return instance_;
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



/*==============================================================================
Tells us if device is still processing asynchronous command.
Required by the MM::Device API.
==============================================================================*/
/*
bool CTIScamera::Busy()
{
   //camera should be in busy state during exposure
   //IsCapturing() is used for determining if sequence thread is run
   return busy_;
}
*/






///////////////////////////////////////////////////////////////////////////////
// Function name   : CTIScamera::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CTIScamera::Initialize()
{
   if (initialized_) return DEVICE_OK;

   int nRet = DEVICE_OK;
   CPropertyAction *pAct = NULL;


   // Device Adapter Name
//   nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
//   assert(nRet == DEVICE_OK);

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "TIS DirectShow driver module", MM::String, true);
   if (nRet != DEVICE_OK) return nRet;

   // Camera Name
//   nRet = CreateProperty(MM::g_Keyword_CameraName, "The Imaging Source", MM::String, true);
//   assert(nRet == DEVICE_OK);


   if (!DShowLib::InitLibrary()) //(DWORD)0
   {
      MessageBox(
         NULL,
         "TIScam InitLibrary failed. Version 3.2 or above expected. Wrong library version?",
         "Initialisation",
         MB_ICONSTOP | MB_OK
         );
      return DEVICE_ERR;
   }

   pGrabber = new DShowLib::Grabber();
   assert(pGrabber);

   pSink = DShowLib::FrameHandlerSink::create(1);
   //set the sink.
   pGrabber->setSinkType(pSink);


   // Add the ROI Flip Filter. Add ROI filter first, then the RotateFilter has less to do.
   pROIFilter        = FilterLoader::createFilter("ROI");
   pRotateFlipFilter = FilterLoader::createFilter("Rotate Flip");
   pDeNoiseFilter    = FilterLoader::createFilter("DeNoise");

   tFrameFilterList filterList;
   if( pROIFilter != NULL )
   {
      filterList.push_back( pROIFilter.get() );
   }

   if( pRotateFlipFilter != NULL )
   {
      filterList.push_back( pRotateFlipFilter.get() );    
   }

   if( pDeNoiseFilter != NULL )
   {
      filterList.push_back( pDeNoiseFilter.get() );    
   }
   pGrabber->setDeviceFrameFilters( filterList );


   pGrabber->loadDeviceStateFromFile(XMLPath); //last used/stored selected camera device and settings

   DShowLib::Grabber::tFPSListPtr        FPSListPointer    = pGrabber->getAvailableFPS();
   DShowLib::Grabber::tVidCapDevListPtr  VidCapDevListPtr  = pGrabber->getAvailableVideoCaptureDevices();
   DShowLib::Grabber::tVidNrmListPtr     VidNrmListPtr     = pGrabber->getAvailableVideoNorms();
   DShowLib::Grabber::tVidFmtListPtr     VidFmtListPtr     = pGrabber->getAvailableVideoFormats();
   DShowLib::Grabber::tVidFmtDescListPtr VidFmtDescListPtr = pGrabber->getAvailableVideoFormatDescs();


   //Create the select of available devices
   pSelectDevice = new CPropertyAction(this, &CTIScamera::OnSelectDevice);
   nRet = CreateProperty(MM::g_Keyword_Name,"No devices found", MM::String,false,pSelectDevice,false);
   AddAllowedValue(MM::g_Keyword_Name,"Please select a device!");
   AddAllowedValue(MM::g_Keyword_Name,"Click here for device selection dialog.");

   pShowProperties = new CPropertyAction(this, &CTIScamera::OnShowPropertyDialog);
   nRet = CreateProperty("Device Properties","", MM::String,false,pShowProperties,false);
   if (nRet != DEVICE_OK) return nRet;
   AddAllowedValue("Device Properties","Show device property dialog.");
   AddAllowedValue("Device Properties","Click here for device property dialog.");

   nRet = CreateProperty(MM::g_Keyword_CameraName, pGrabber->getDev().c_str(), MM::String, true);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnFlipHorizontal);
   nRet = CreateProperty(g_FlipH, g_Off, MM::String, false, pAct);
   if (nRet != DEVICE_OK) return nRet;
   AddAllowedValue(g_FlipH,g_On);
   AddAllowedValue(g_FlipH,g_Off);

   pAct = new CPropertyAction (this, &CTIScamera::OnFlipVertical);
   nRet = CreateProperty(g_FlipV, g_Off, MM::String, false, pAct);
   if (nRet != DEVICE_OK) return nRet;
   AddAllowedValue(g_FlipV,g_On);
   AddAllowedValue(g_FlipV,g_Off);

   nRet = CreateProperty(g_Keyword_SerialNumber, "n/a", MM::String, false, 0);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnBrightness);
   nRet = CreateProperty(g_Keyword_Brightness,CDeviceUtils::ConvertToString(Brightness_), MM::Integer, false,pAct);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnGain);
   nRet = CreateProperty(g_Keyword_Gain, CDeviceUtils::ConvertToString(Gain_), MM::Integer, false, pAct);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnGainAuto);
   nRet = CreateProperty(g_Keyword_Gain_Auto, "n/a", MM::String, false, pAct);
   if (nRet != DEVICE_OK) return nRet;
   AddAllowedValue(g_Keyword_Gain_Auto,g_On);
   AddAllowedValue(g_Keyword_Gain_Auto,g_Off);

   pAct = new CPropertyAction (this, &CTIScamera::OnWhiteBalance);
   nRet = CreateProperty(g_Keyword_WhiteBalance, CDeviceUtils::ConvertToString(WhiteBalance_), MM::Integer, false, pAct);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnWhiteBalanceAuto);
   nRet = CreateProperty(g_Keyword_WhiteBalance_Auto, "n/a", MM::String, false, pAct);
   if (nRet != DEVICE_OK) return nRet;
   AddAllowedValue(g_Keyword_WhiteBalance_Auto,g_On);
   AddAllowedValue(g_Keyword_WhiteBalance_Auto,g_Off);


   pAct = new CPropertyAction (this, &CTIScamera::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK) return nRet;

   pAct = new CPropertyAction (this, &CTIScamera::OnAutoExposure);
   nRet = CreateProperty(g_Keyword_AutoExposure, g_On,  MM::String, false, pAct);
   if (nRet != DEVICE_OK) return nRet;

   vector<string> AutoExposureValues;
   AutoExposureValues.push_back(g_On);
   AutoExposureValues.push_back(g_Off);
   nRet = SetAllowedValues(g_Keyword_AutoExposure, AutoExposureValues);

   pAct = new CPropertyAction (this, &CTIScamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   pAct = new CPropertyAction (this, &CTIScamera::OnDeNoise);
   nRet = CreateProperty(g_Keyword_DeNoise, "n/a", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   if(pGrabber->isDevValid())
   {
      return SetupProperties();
   }
   return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////
//
int CTIScamera::SetupProperties()
{
   pExposureRange = NULL;
   pExposureAuto = NULL;

   // Create the simple camera property access class
   if( m_pSimpleProperties != NULL )
   {
      delete m_pSimpleProperties;
   }

   m_pSimpleProperties = new CSimplePropertyAccess( pGrabber->getAvailableVCDProperties() );

   // determine if the camera is able to do color
   if( pGrabber->getVideoFormat().toString().substr(0,4) == "Y800")
   {
      bColor_ = false;
      // pSink = DShowLib::FrameHandlerSink::create(DShowLib::eY800,NUMBER_OF_BUFFERS);  // 8 bit grayscale
   }
   else
   {
      bColor_ = true;
      // pSink = DShowLib::FrameHandlerSink::create(DShowLib::eRGB32,NUMBER_OF_BUFFERS); // RGB
   }



   //we use snap mode
   pSink->setSnapMode(true);

   //set the sink.
//   pGrabber->setSinkType(pSink);


   //property Serial Number (read only)
   LARGE_INTEGER iSerNum;
   if (pGrabber->getDev().getSerialNumber(iSerNum.QuadPart) == false) iSerNum.QuadPart = 0;
   std::ostringstream ossSerNum;
   ossSerNum << "0x" << std::hex << iSerNum.QuadPart << '\0';
   string SerNum = ossSerNum.str();
   SetProperty(g_Keyword_SerialNumber,  SerNum.c_str());

   long lMin, lMax;

   //MessageBox(NULL,"test","test",MB_OK);

   tIVCDPropertyItemsPtr pItems = pGrabber->getAvailableVCDProperties();
   if( pItems != 0 )
   { 
      // Try to find the exposure item. 
      tIVCDPropertyItemPtr pExposureItem = pItems->findItem( VCDID_Exposure );
      if( pExposureItem != 0 )
      { 
         // Try to find the value and auto elements 
         tIVCDPropertyElementPtr pExposureValueElement = pExposureItem->findElement( VCDElement_Value );
         tIVCDPropertyElementPtr pExposureAutoElement  = pExposureItem->findElement( VCDElement_Auto );

         // If an auto element exists, try to acquire a switch interface 
         if( pExposureAutoElement != 0 )
         { 
            pExposureAutoElement->getInterfacePtr( pExposureAuto );
         } 

         // If a value element exists, try to acquire a range interface 
         if( pExposureValueElement != 0 )
         { 
            pExposureValueElement->getInterfacePtr( pExposureRange );
			currentExpMS_ = pExposureRange->getValue() * 1000;
			SetProperty( MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(currentExpMS_));
			double dMinExp = pExposureRange->getRangeMin() * 1000; //convert s to ms
			double dMaxExp = pExposureRange->getRangeMax() * 1000; //convert s to ms
            SetPropertyLimits( MM::g_Keyword_Exposure, dMinExp, dMaxExp );
         }
      }
   }


   if (!m_pSimpleProperties->isAvailable(VCDID_Brightness))
      SetProperty(g_Keyword_Brightness, "n/a");
   else
   {
      lMin        = m_pSimpleProperties->getRangeMin(VCDID_Brightness);
      lMax        = m_pSimpleProperties->getRangeMax(VCDID_Brightness);
      Brightness_ = m_pSimpleProperties->getValue   (VCDID_Brightness);

      SetProperty(g_Keyword_Brightness, CDeviceUtils::ConvertToString(Brightness_));
      SetPropertyLimits(g_Keyword_Brightness, lMin, lMax);
   }

   if (!m_pSimpleProperties->isAvailable(VCDID_Gain))
      SetProperty(g_Keyword_Gain, "n/a");
   else
   {
      lMin  = m_pSimpleProperties->getRangeMin(VCDID_Gain);
      lMax  = m_pSimpleProperties->getRangeMax(VCDID_Gain);
      Gain_ = m_pSimpleProperties->getValue   (VCDID_Gain);

      SetProperty(g_Keyword_Gain, CDeviceUtils::ConvertToString(Gain_));
      SetPropertyLimits(g_Keyword_Gain, (double)lMin, (double)lMax);

      if( m_pSimpleProperties->isAutoAvailable(VCDID_Gain) )
      {
         if( m_pSimpleProperties->getAuto(VCDID_Gain))
         {
            SetProperty(g_Keyword_Gain_Auto,g_On);
         }
         else
         {
            SetProperty(g_Keyword_Gain_Auto,g_Off);
         }
      }
   }


   if(!m_pSimpleProperties->isAvailable(VCDID_WhiteBalance))
      SetProperty(g_Keyword_WhiteBalance, "n/a");
   else
   {
      //range is typical from 0 to 255
      lMin          = m_pSimpleProperties->getRangeMin(VCDID_WhiteBalance);
      lMax          = m_pSimpleProperties->getRangeMax(VCDID_WhiteBalance);
      WhiteBalance_ = m_pSimpleProperties->getValue   (VCDID_WhiteBalance);

      SetProperty(g_Keyword_WhiteBalance, CDeviceUtils::ConvertToString(WhiteBalance_));
      if (lMax != 0) SetPropertyLimits(g_Keyword_WhiteBalance, (double)lMin, (double)lMax);

      if( m_pSimpleProperties->isAutoAvailable(VCDID_WhiteBalance) )
      {
         if( m_pSimpleProperties->getAuto(VCDID_WhiteBalance))
         {
            SetProperty(g_Keyword_WhiteBalance_Auto,g_On);
         }
         else
         {
            SetProperty(g_Keyword_WhiteBalance_Auto,g_Off);
         }
      }
      else SetProperty(g_Keyword_WhiteBalance_Auto, "n/a");
   }
 




   // Prepare the live mode, to get the output size of the sink.
   pGrabber->prepareLive(ACTIVEMOVIE);

   


   // Retrieve the output type and dimension of the handler sink.
   // The dimension of the sink could be different from the VideoFormat, when
   // you use filters.
#ifdef TRUE
   DShowLib::FrameTypeInfo info;
   pSink->getOutputFrameType(info);
   //sink oriented data size
   lCCD_Width         = info.dim.cx;
   lCCD_Height        = info.dim.cy;
   uiCCD_BitsPerPixel = info.getBitsPerPixel();
#else
   //Camera oriented data size
   lCCD_Width         = pGrabber->getAcqSizeMaxX();
   lCCD_Height        = pGrabber->getAcqSizeMaxY();
   if (bColor_) uiCCD_BitsPerPixel = 32;
   else         uiCCD_BitsPerPixel = 8;
   //   tColorformatEnum cf = info.getColorformat();
#endif

   roiX_ = 0;
   roiY_ = 0;
   roiXSize_ = lCCD_Width;
   roiYSize_ = lCCD_Height;
   img_.Resize(roiXSize_, roiYSize_, uiCCD_BitsPerPixel / 8);

   // Allocate NUMBER_OF_BUFFERS image buffers of the above (info) buffer size.
   for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
   {
	  if (pBuf[ii] != 0)
      {
		 delete pBuf[ii];
		 pBuf[ii] = 0;
	  }
      pBuf[ii] = new BYTE[info.buffersize];
      assert(pBuf[ii]);
   }

// Create a new MemBuffer collection that uses our own image buffers.
   pCollection = DShowLib::MemBufferCollection::create(info, NUMBER_OF_BUFFERS, pBuf);
   if (pCollection == 0) return DEVICE_ERR;
   if (!pSink->setMemBufferCollection(pCollection)) return DEVICE_ERR;

   pGrabber->startLive(ACTIVEMOVIE);

//#define _WORKAROUND_FOR_PIXEL_CALIBRATOR
#ifdef _WORKAROUND_FOR_PIXEL_CALIBRATOR
//Start simple workaround to ensure square picture for beta pixel calibration
   SnapImage();
   lCCD_Width = lCCD_Height;
   ClearROI();
//End workaround
#endif



   // binning
   vector<string> binValues;
   binValues.push_back("1");
//   binValues.push_back("2");
//   binValues.push_back("4");
//   binValues.push_back("8");
   LogMessage("Setting Allowed Binning settings", true);
   SetAllowedValues(MM::g_Keyword_Binning, binValues);



   // DeNoise
   vector<string> DeNoiseValues;
   DeNoiseValues.push_back("0");
   DeNoiseValues.push_back("1");
   DeNoiseValues.push_back("2");
   DeNoiseValues.push_back("3");
   DeNoiseValues.push_back("4");
   DeNoiseValues.push_back("5");
   DeNoiseValues.push_back("6");
   LogMessage("Setting some DeNoise settings", true);
   SetAllowedValues(g_Keyword_DeNoise, DeNoiseValues);
   initialized_ = true;

   	if( pDeNoiseFilter != NULL )
	{
      string buf;
      pGrabber->stopLive();
      pDeNoiseFilter->setParameter( "DeNoise Level", DeNoiseLevel_ );
      pGrabber->startLive(ACTIVEMOVIE);
	}

   // initialize image buffer
   return SnapImage();

}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int CTIScamera::OnSelectDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int Result = DEVICE_OK;
	if (eAct == MM::BeforeGet)
	{
		if( pGrabber->isDevValid() )
		{
			string Text = pGrabber->getDev().toString() + " ";
			Text += pGrabber->getVideoFormat().c_str();
			pProp->Set(Text.c_str());
		}
		else
		{
			DShowLib::Grabber::tVidCapDevListPtr pVidCapDevList = pGrabber->getAvailableVideoCaptureDevices();
			if ((pVidCapDevList == 0) || (pVidCapDevList->empty()))
			{
				pProp->Set("No devices found");
			}
			else
			{
				pProp->Set("Please select a device!");
			}
		}
	}

	else if (eAct == MM::AfterSet)
	{
		initialized_ = false;
		pGrabber->stopLive();

/*
pGrabber->stopLive();
pGrabber->closeDev();
*/
		pGrabber->showDevicePage();

		if( pGrabber->isDevValid() )
		{
       		pGrabber->openDev(1);
            pGrabber->startLive(ACTIVEMOVIE);
			initialized_ = true;
			pGrabber->saveDeviceStateToFile(XMLPath);
			SetupProperties();
		}
		else
		{
			pProp->Set("No video capture device set!");
			Result = DEVICE_ERR;
		}
	}

	return Result;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int CTIScamera::OnShowPropertyDialog(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if( pGrabber->isDevValid() )
		{
			pProp->Set("Click here for Device Property dialog");
		}
		else
		{
			pProp->Set("No video capture device set!");
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if( pGrabber->isDevValid() )
		{
			pGrabber->stopLive();
			pGrabber->closeDev();
			pGrabber->showVCDPropertyPage();
			pProp->Set("Click here for Device Property dialog");
			pGrabber->saveDeviceStateToFile(XMLPath);
		}
		else
		{
			pProp->Set("No video capture device set!");
		}
	}

	return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// Function name   : CTIScamera::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CTIScamera::Shutdown()
{
   DriverGuard dg(this);

   if (initialized_)
   {
   }
   initialized_ = false;

   // this is called from CPluginManager::UnloadDevice
   // which doesn't guarantee that Initialize has been called

   if( 0 != pGrabber)
   {
      pGrabber->stopLive();
      pGrabber->closeDev();
   }
   for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
   {
      delete pBuf[ii];
   }
   delete pGrabber;
   pGrabber = 0;

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
  busy_ = true;

  bool was_off = false;
  if (!pGrabber->isLive())
  {
    was_off = true;
    pGrabber->startLive(ACTIVEMOVIE);
  }

  pSink->snapImages(1); //,2000);  //this command blocks until exposure is finished or timeout after 2000ms

  if (was_off)
	  {
		  pGrabber->stopLive();
  }
  busy_ = false;

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

  unsigned int bitDepth = 8; //assume 8 as default


  if( pGrabber->isDevValid())
  {
	DShowLib::FrameTypeInfo info;
	pSink->getOutputFrameType(info);
	bitDepth = info.getBitsPerPixel();
	if (info.getColorformat() == eRGB32) bitDepth = bitDepth / 4;
	else if (info.getColorformat() == eRGB24) bitDepth = bitDepth / 3;
  }
  return bitDepth;
}


/*==============================================================================
Returns the number of channels in this image.  This is '1' for grayscale cameras,
and '4' for RGB cameras.
==============================================================================*/
unsigned CTIScamera::GetNumberOfComponents() const
{
  // treat any 8 or 16 bit image as monochrome for now
  unsigned int nc = 1;

  if( pGrabber->isDevValid())
  {	
    DShowLib::FrameTypeInfo info;
	pSink->getOutputFrameType(info);
    const tColorformatEnum cf = info.getColorformat();

	if      (cf == eRGB32)
	  nc = 4;
    else if (cf == eRGB24)
	  nc = 3; // user will see a message that this pixel type is not supported
    else if (cf == eInvalidColorformat)
	  nc = 0; // user will see a message that this pixel type is not supported
      
      // there are several 32 bit modes?
      //if( 32 == info.getBitsPerPixel())
      //   nc = 4;
  }

  return nc;
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
   GetCoreCallback()->OnExposureChanged(this, dExp_ms);
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
/*
   DShowLib::FrameTypeInfo info;
   pSink->getOutputFrameType(info);
   unsigned char* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
   memcpy(pixBuffer, pBuf[0], min(GetImageBufferSize(),(long)info.buffersize));
   // capture complete
   return (unsigned char*) pixBuffer;
*/

   return (unsigned char*) pBuf[0];
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
   return img_.Depth();
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
   unsigned roiX_loc = roiX_;  
   unsigned roiY_loc = roiY_;

   roiX_     = roiX_loc + uX;
   roiY_     = roiY_loc + uY;
   roiXSize_ = uXSize;
   roiYSize_ = uYSize;
   RecalculateROI();
   ResizeImageBuffer();
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
   roiX_ = 0;
   roiY_ = 0;
   roiXSize_ = lCCD_Width;
   roiYSize_ = lCCD_Height;
   RecalculateROI();
   ResizeImageBuffer();
   return DEVICE_OK;
}



/*==============================================================================
Starts continuous acquisition.
==============================================================================*/
int CTIScamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   int ret;
   DriverGuard dg(this);
      if (sequenceRunning_)
         return DEVICE_CAMERA_BUSY_ACQUIRING;

//   if (acquiring_)
//      return DEVICE_CAMERA_BUSY_ACQUIRING;



   sequenceRunning_ = true;

   stopOnOverflow_ = stopOnOverflow;
   interval_ms_ = interval_ms;   


   lastImage_  = 0;

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
   double interval = max(readoutTime, currentExpMS_);
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
   sequenceStartTime_ = GetCurrentMMTime();
   imageCounter_ = 0;
   sequenceLength_ = numImages;

   seqThread_->SetLength(numImages);

   seqThread_->Start();

//   acquiring_ = true;

   LogMessage("Acquisition thread started");

   return DEVICE_OK;
}



int CTIScamera::RestartSequenceAcquisition()
{
   return StartSequenceAcquisition(sequenceLength_ - imageCounter_, interval_ms_, stopOnOverflow_);
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
*
==============================================================================*/
void CTIScamera::RecalculateROI()
{
   if (pROIFilter != NULL)
   {
      long lLeft   = roiX_;
      long lTop    = roiY_;
      long lWidth  = roiXSize_;
      long lHeight = roiYSize_;

      pGrabber->stopLive();

      pROIFilter->setParameter("Left",lLeft);
      pROIFilter->setParameter("Top",lTop);
      pROIFilter->setParameter("Width",lWidth);
      pROIFilter->setParameter("Height",lHeight);

      pGrabber->startLive(ACTIVEMOVIE);


      for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
      {
         delete pBuf[ii];
      }

      // Retrieve the output type and dimension of the handler sink.
      // The dimension of the sink could be different from the VideoFormat, when
      // you use filters.
      DShowLib::FrameTypeInfo info;
      pSink->getOutputFrameType(info);

      // adjust software variables to use the same values like the camera hardware
      roiXSize_ = info.dim.cx;
      roiYSize_ = info.dim.cy;

      // Allocate NUMBER_OF_BUFFERS image buffers of the above (info) buffer size.
      for (int ii = 0; ii < NUMBER_OF_BUFFERS; ++ii)
      {
         pBuf[ii] = new BYTE[info.buffersize];
         assert(pBuf[ii]);
      }

   	// Create a new MemBuffer collection that uses our own image buffers.
      pCollection = DShowLib::MemBufferCollection::create(info, NUMBER_OF_BUFFERS, pBuf);
      pSink->setMemBufferCollection(pCollection);
   }
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

   // Prepare the live mode, to get the output size of the sink.
   pGrabber->prepareLive(ACTIVEMOVIE);

   // Retrieve the output type and dimension of the handler sink.
   // The dimension of the sink could be different from the VideoFormat, when
   // you use filters.
#ifdef TRUE
   DShowLib::FrameTypeInfo info;
   pSink->getOutputFrameType(info);
   //sink oriented data size
   lCCD_Width         = info.dim.cx;
   lCCD_Height        = info.dim.cy;
   uiCCD_BitsPerPixel = info.getBitsPerPixel();
#else
   //Camera oriented data size
   lCCD_Width         = pGrabber->getAcqSizeMaxX();
   lCCD_Height        = pGrabber->getAcqSizeMaxY();
   if (bColor_) uiCCD_BitsPerPixel = 32;
   else         uiCCD_BitsPerPixel = 8;
   //   tColorformatEnum cf = info.getColorformat();
#endif

   int byteDepth = uiCCD_BitsPerPixel >> 4;

   img_.Resize(roiXSize_/binSize, roiYSize_/binSize, byteDepth);

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
            img_.Resize(roiXSize_/binFactor, roiYSize_/binFactor);
            ret=DEVICE_OK;
         }
         else
         {
            // on failure reset default binning of 1
            img_.Resize(roiXSize_, roiYSize_);
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
	double dExp = 0.0;
	// exposure property is stored in milliseconds,
	// while the driver returns the value in seconds
	if (eAct == MM::BeforeGet)
	{
		if (pExposureRange != NULL)
		{
			dExp = pExposureRange->getValue();
			dExp = dExp * 1000; //convert s to ms
			pProp->Set(dExp);
		}
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(dExp); //ms
		if ( fabs(dExp - currentExpMS_) > 0.1 )
		{
			if (pExposureRange != NULL)
			{
				bool bCanBeSet = true;
				if (pExposureAuto != NULL)
				{
					bCanBeSet = !pExposureAuto->getSwitch();
				}
				if (!bCanBeSet) return DEVICE_CAN_NOT_SET_PROPERTY; //auto exposure is ON

      			if (pExposureRange != NULL)
				{
					currentExpMS_ = dExp;
					dExp = dExp / 1000; //convert ms to s
					pExposureRange->setValue(dExp);
				}
			}
		}
	}
	return DEVICE_OK;
}


// Auto Exposure
int CTIScamera::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if( pExposureAuto != NULL )		
		{
			pProp->Set(pExposureAuto->getSwitch()?g_On:g_Off );
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if( pExposureAuto != NULL )		
		{
			string OnOff;
			pProp->Get(OnOff);
			pExposureAuto->setSwitch(OnOff == g_On);
		}
	}

   return DEVICE_OK;
}


////////////////////////////////////////////////////////////////////////////////
//
int CTIScamera::OnFlipHorizontal(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (pRotateFlipFilter == NULL) return DEVICE_INVALID_PROPERTY;
   
   if (eAct == MM::BeforeGet)
	{
	   bool bFlip = false; 
	   pRotateFlipFilter->getParameter( "Flip H", bFlip );

	   if( bFlip )
		   pProp->Set(g_On);
	   else
		   pProp->Set(g_Off);
	}
	else if (eAct == MM::AfterSet)
	{
	   string Flip;
	   pProp->Get(Flip);
			
	   pRotateFlipFilter->setParameter( "Flip H", Flip == g_On );
	}
   return DEVICE_OK;
}

////////////////////////////////////////////////////////////////////////////////
//
int CTIScamera::OnFlipVertical(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (pRotateFlipFilter == NULL) return DEVICE_INVALID_PROPERTY;

	if (eAct == MM::BeforeGet)
	{
		bool bFlip = false; 
		pRotateFlipFilter->getParameter( "Flip V", bFlip );

		if( bFlip )
			pProp->Set(g_On);
		else
			pProp->Set(g_Off);
	}
	else if (eAct == MM::AfterSet)
	{
		string Flip;
		pProp->Get(Flip);
			
		pRotateFlipFilter->setParameter( "Flip V", Flip == g_On );
	}
	return DEVICE_OK;
}


////////////////////////////////////////////////////////////////////////////////
//
int CTIScamera::OnDeNoise(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if( pDeNoiseFilter != NULL )
	{
		if (eAct == MM::BeforeGet)
		{
			long lDeNoise;
			pDeNoiseFilter->getParameter( "DeNoise Level", lDeNoise );
			pProp->Set(CDeviceUtils::ConvertToString(lDeNoise));
		}
		else if (eAct == MM::AfterSet)
		{
         string buf;
         pProp->Get(buf);
         long lDeNoise = atoi(buf.c_str());
         pGrabber->stopLive();
		 pDeNoiseFilter->setParameter( "DeNoise Level", lDeNoise );
         pGrabber->startLive(ACTIVEMOVIE);
		}
	}
	else
	{
		pProp->Set("n/a");
	}

	return DEVICE_OK;
}


// Brightness
int CTIScamera::OnBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // brightness property is stored in milliseconds,
   // while the driver returns the value in seconds
	if( m_pSimpleProperties != NULL )
	{
		if (eAct == MM::BeforeGet)
		{
			if (m_pSimpleProperties->isAvailable(VCDID_Brightness))
			{
				long lValue;
				lValue = m_pSimpleProperties->getValue(VCDID_Brightness);
				Brightness_  = lValue;
				pProp->Set(Brightness_);
			}
		}
		else if (eAct == MM::AfterSet)
		{
			long Brightness;
			pProp->Get(Brightness);

			if (m_pSimpleProperties->isAvailable(VCDID_Brightness))
			{
				m_pSimpleProperties->setValue(VCDID_Brightness, Brightness);
			}

			Brightness_ = Brightness;
		}
	}
   return DEVICE_OK;
}


// Gain event handler
int CTIScamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	bool bCanBeSet = true;
	if( m_pSimpleProperties != NULL )
	{
		if (m_pSimpleProperties->isAutoAvailable(VCDID_Gain))
		{
			bCanBeSet =  !m_pSimpleProperties->getAuto(VCDID_Gain) ;
		}

		if (eAct == MM::BeforeGet)
		{
			if (m_pSimpleProperties->isAvailable(VCDID_Gain))
			{
				long lValue;
				lValue = m_pSimpleProperties->getValue(VCDID_Gain);
				Gain_  = lValue;
				pProp->Set(Gain_);
			}
		}
		else if (eAct == MM::AfterSet)
		{
			if(!bCanBeSet) return DEVICE_CAN_NOT_SET_PROPERTY; // Means automatic is enabled.

         long Gain;
			pProp->Get(Gain);

			if (m_pSimpleProperties->isAvailable(VCDID_Gain))
			{
				m_pSimpleProperties->setValue(VCDID_Gain, Gain);
			}
		}
	}
   return DEVICE_OK;
}



// GainAuto event handler
int CTIScamera::OnGainAuto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // brightness property is stored in milliseconds,
   // while the driver returns the value in seconds
	
	if( m_pSimpleProperties != NULL )
	{
		if (eAct == MM::BeforeGet)
		{
			if (m_pSimpleProperties->isAutoAvailable(VCDID_Gain))
			{
				if( m_pSimpleProperties->getAuto(VCDID_Gain))
					pProp->Set(g_On);
				else
					pProp->Set(g_Off);
			}
			else
			{
				pProp->Set("n/a");
			}
		}
		else if (eAct == MM::AfterSet)
		{
			if (m_pSimpleProperties->isAutoAvailable(VCDID_Gain))
			{
				string Gain;
				pProp->Get(Gain);
				m_pSimpleProperties->setAuto(VCDID_Gain,Gain==g_On);
			}
		}
	}
   return DEVICE_OK;
}




int CTIScamera::OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	bool bCanBeSet = true;
	if( m_pSimpleProperties != NULL )
	{
		if (m_pSimpleProperties->isAutoAvailable(VCDID_WhiteBalance))
		{
			bCanBeSet = !m_pSimpleProperties->getAuto(VCDID_WhiteBalance);
		}

		if (eAct == MM::BeforeGet)
		{
			if (m_pSimpleProperties->isAvailable(VCDID_WhiteBalance))
			{
				long lValue;
				lValue = m_pSimpleProperties->getValue(VCDID_WhiteBalance);
				WhiteBalance_  = lValue;
				pProp->Set(WhiteBalance_);
			}
		}
		else if (eAct == MM::AfterSet)
		{
			if(!bCanBeSet) return DEVICE_CAN_NOT_SET_PROPERTY; // Means automatic is enabled.

         long WhiteBalance;
			pProp->Get(WhiteBalance);
			if (m_pSimpleProperties->isAvailable(VCDID_WhiteBalance))
			{
				m_pSimpleProperties->setValue(VCDID_WhiteBalance, WhiteBalance);
			}
		}
	}
   return DEVICE_OK;
}



// GainAuto event handler
int CTIScamera::OnWhiteBalanceAuto(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // brightness property is stored in milliseconds,
   // while the driver returns the value in seconds
	
	if( m_pSimpleProperties != NULL )
	{
		if (eAct == MM::BeforeGet)
		{
			if (m_pSimpleProperties->isAutoAvailable(VCDID_WhiteBalance))
			{
				if( m_pSimpleProperties->getAuto(VCDID_WhiteBalance))
					pProp->Set(g_On);
				else
					pProp->Set(g_Off);
			}
			else
			{
				pProp->Set("n/a");
			}
		}
		else if (eAct == MM::AfterSet)
		{
			if (m_pSimpleProperties->isAutoAvailable(VCDID_WhiteBalance))
			{
				string WhiteBalance;
				pProp->Get(WhiteBalance);
				m_pSimpleProperties->setAuto(VCDID_WhiteBalance,WhiteBalance==g_On );
			}
		}
	}
   return DEVICE_OK;
}




   /**
   * Stop Seq sequence acquisition
   * This is the function for internal use and can/should be called from the thread
   */
   int CTIScamera::StopCameraAcquisition()
   {
/*
	   {
         DriverGuard dg(this);
         if (!sequenceRunning_)
            return DEVICE_OK;

         LogMessage("Stopped sequence acquisition");
         AbortAcquisition();
         int status = DRV_ACQUIRING;
         int error = DRV_SUCCESS;
         while (error == DRV_SUCCESS && status == DRV_ACQUIRING) {
           error = GetStatus(&status); 
         }

         sequenceRunning_ = false;

         UpdateSnapTriggerMode();
      }
*/
      sequenceRunning_ = false;
      MM::Core* cb = GetCoreCallback();
      if (cb)
         return cb->AcqFinished(this, 0);
      else
         return DEVICE_OK;
   }



   int CTIScamera::StopSequenceAcquisition(bool temporary)
   {
      {
         DriverGuard dg(this);
         sequencePaused_ = temporary;
         StopCameraAcquisition();
      }

      seqThread_->Stop();
      seqThread_->wait();

      return DEVICE_OK;
   }

   // Stops Sequence acquisition
   // This is for external use only (if called from the sequence acquisition thread, deadlock will ensue!
   int CTIScamera::StopSequenceAcquisition()
   {
      return StopSequenceAcquisition(false);
   }




/*==============================================================================
Continuous acquisition
==============================================================================*/
int AcqSequenceThread::svc(void)
{
   long imageCounter(0);

   do
   {
      // wait until the frame becomes available - waits in PushImage t.b.d.
	  
      if (!pGrabber->isLive())
      {
        pGrabber->startLive(ACTIVEMOVIE);
      }
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

      CDeviceUtils::SleepMs(waitTime_);

   } while (!stop_ && (imageCounter < numImages_));


   if (stop_)
   {
      printf("Acquisition interrupted by the user\n");
      camera_->StopCameraAcquisition();
	  return 0;
   }


//   camera_->RestartSnapMode();
   printf("Acquisition completed.\n");

   camera_->StopCameraAcquisition();

   return 0;
}


/*==============================================================================
Waits for new image and inserts it into the circular buffer
==============================================================================*/
int CTIScamera::PushImage()
{
   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;
   md.put("Camera", label);
   md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(sequenceStartTime_.getMsec()));
   md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - sequenceStartTime_).getMsec()));
   md.put(MM::g_Keyword_Metadata_ImageNumber, CDeviceUtils::ConvertToString(imageCounter_));
   md.put(MM::g_Keyword_Binning, binSize_);
   md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString( (long) roiX_)); 
   md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString( (long) roiY_)); 

   MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
   mstStartTime.SetValue(CDeviceUtils::ConvertToString(startTime_.getMsec()));
   md.SetTag(mstStartTime);

   MetadataSingleTag mst(MM::g_Keyword_Elapsed_Time_ms, label, true);
   mst.SetValue(CDeviceUtils::ConvertToString(timeStamp.getMsec()));
   md.SetTag(mst);

   MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
   mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_));      
   md.SetTag(mstCount);

   MetadataSingleTag mstB(MM::g_Keyword_Binning, label, true);
   mstB.SetValue(CDeviceUtils::ConvertToString(binSize_));      
   md.SetTag(mstB);


   // Copy the metadata inserted by other processes:
   std::vector<std::string> keys = GetTagKeys();
   for (unsigned int i= 0; i < keys.size(); i++) {
      md.put(keys[i], GetTagValue(keys[i].c_str()).c_str());
   }

   char buf[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, buf);
   md.put(MM::g_Keyword_Binning, buf);

   imageCounter_++;

	
//   MMThreadGuard g(imgPixelsLock_);

//   DriverGuard dg(this);

   // get pixels
   const unsigned char* pI;
   pI = GetImageBuffer();

   unsigned int w = GetImageWidth();
   unsigned int h = GetImageHeight();
   unsigned int b = GetImageBytesPerPixel();

   int ret = GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str());
   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      // don't process this same image again...
      return GetCoreCallback()->InsertImage(this, pI, w, h, b, md.Serialize().c_str(), false);
   } else
      return ret;






}




DriverGuard::DriverGuard(const CTIScamera * cam)
{
   g_DriverLock.Lock();
   if (cam != 0)
   {
#ifdef UNDERTEST
	  if (cam->GetNumberOfWorkableCameras() > 1)
      {
       // must be defined as 32bit in order to compile on 64bit systems
	   // since GetCurrentCamera only takes 32bit
         int32 currentCamera;
         GetCurrentCamera(&currentCamera);
         if (currentCamera != cam->GetMyCameraID())
         {
            int ret = SetCurrentCamera(cam->GetMyCameraID());
            if (ret != DRV_SUCCESS)
               printf("Error switching active camera");
         }
	  }
#endif
   }
}


DriverGuard::~DriverGuard()
{
   g_DriverLock.Unlock();
}









