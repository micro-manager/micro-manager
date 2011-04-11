///////////////////////////////////////////////////////////////////////////////
// FILE:          Sensicam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sensicam camera module
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//
// Modified May 14th 2007 by Liisa Hirvonen, King's College London

// N.B. set tabs and indent to 3 spaces in your source editor

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#endif
#include "../../MMDevice/ModuleInterface.h"
#include "Sensicam.h"
#include "../../../3rdparty/PCO/Windows/SDK_515/sencam.h"
#include "../../../3rdparty/PCO/Windows/SDK_515/errcodes.h"
#include "../../../3rdparty/PCO/Windows/SDK_515/ccd_types.h"
#include "../../../3rdparty/PCO/Windows/SDK_515/cam_types.h"
#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

#include <string>
#include <sstream>
#include <cmath>	// Liisa: for ceil



// temp
#include "stdio.h"

using namespace std;

CSensicam* CSensicam::m_pInstance = 0;
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";

#ifdef WIN32
// Windows dll entry routine
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

   #define snprintf _snprintf
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName("Sensicam", "PCO Sensicam camera adapter");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* pszDeviceName)
{
   if (pszDeviceName == 0)
      return 0;

   string strName(pszDeviceName);
   
   if (strName == "Sensicam")
      return CSensicam::GetInstance();
   
   return 0;
}


///////////////////////////////////////////////////////////////////////////////
// CSensicam constructor/destructor

CSensicam::CSensicam() :
   CCameraBase<CSensicam> (),
   sequenceRunning_(false),
   m_bInitialized(false),
   m_bBusy(false),
   m_dExposure(0.0),
   sthd_(0), 
   stopOnOverflow_(false),
   pixelDepth_(2),
   pictime_(0.0)
{
   // initialize the array of camera settings
   m_nBoard = 0;
   m_nCameraType = 0;
   m_pszTimes[0] = 0;
   m_nSubMode = 0;
   m_nMode = 0;
   m_nTrig = 0;
   m_nRoiXMax = 0;
   m_nRoiXMin = 0;
   m_nRoiYMax = 0;
   m_nRoiYMin = 0;
   m_nHBin = 0;
   m_nVBin = 0;
   m_nCCDType = 0; // read-only
   m_nTimesLen = MMSENSICAM_MAX_STRLEN;

   InitializeDefaultErrorMessages();
   sthd_ = new SequenceThread(this);
}

CSensicam::~CSensicam()
{
   if (m_bInitialized)
      Shutdown();

   m_pInstance = 0;
}

// set camera board
int CSensicam::OnBoard(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int nErr = 0;
   if (eAct == MM::AfterSet)
   {
      long lVal;
      pProp->Get(lVal);
      nErr = SET_BOARD((int)lVal);
      if (nErr == 0)
         m_nBoard = (int)lVal;   
   }
   return nErr;
}

// Camera type
int CSensicam::OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // get the camera type
      int nErr = GET_CAMERA_TYP(m_nBoard, &m_nCameraType);  
      if (nErr != 0)
         return nErr;
    
      switch(m_nCameraType)
      {
         case FASTEXP:
            // Fast Shutter
            pProp->Set("FastShutter");
         break;

         case LONGEXP:
            // Long exposure
            pProp->Set("Long exposure");
         break;

         case OEM:
            // Long exposure
            pProp->Set("Long exposure OEM");
         break;
      
		/* Liisa */ 
		 case 8:
            pProp->Set("Long exposure");
         break;
		/* end Liisa */ 
		
         default:
            return ERR_UNKNOWN_CAMERA_TYPE;
         break;
      }
   }
   return DEVICE_OK;
}

// CCD type
int CSensicam::OnCCDType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // get the CCD type
      int nErr = GET_CAMERA_CCD(m_nBoard, &m_nCCDType);  
      if (nErr != 0)
         return nErr;

	  switch(m_nCCDType)
      {
         case CCD74:
            pProp->Set("CCD74 640x480 BW");
         break;

         case CCD74C:
            pProp->Set("CCD74 640x480 color");
         break;

         case CCD85:
            pProp->Set("CCD85 1280x1024 BW");
         break;

         case CCD85C:
            pProp->Set("CCD85 1280x1024 color");
         break;

         case CCD285QE:
            pProp->Set("CCD285 1376x1040 BW");
         break;

         case CCD285QEF:
            pProp->Set("CCD285 1376x1040 BW fast mode");
         break;

         case CCD285QED:
            pProp->Set("CCD285 1376x1040 BW double mode");
         break;

         default:
            // invalid type
            return ERR_UNKNOWN_CAMERA_TYPE;
         break;
      }
   }
   return DEVICE_OK;
}

int CSensicam::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(m_dExposure);
   else if (eAct == MM::AfterSet)
   {
	  bool wasCapturing = false;
      int nErr;

	  if (IsCapturing())
	  {
		  wasCapturing = (0 < sthd_->GetLength());
		  StopSequenceAcquisition();
	  }
      pProp->Get(m_dExposure);
      sprintf(m_pszTimes, "0,%d,-1,-1", (int)m_dExposure); 
      nErr = SET_COC(m_nMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                     m_nHBin, m_nVBin, m_pszTimes);
	  if (0!=nErr)
	  {
	     std::ostringstream logMe;
		  logMe<<"Sensicam::OnExposure SET_COC returns "<<nErr<<endl;
		  LogMessage(logMe.str().c_str());
	  }
      if (IsSensicamError(nErr))
         return nErr;

	  // Liisa: m_nTimesLen needs to be updated otherwise if the string is now longer that
	  // previously, GET_COC_SETTING will not be able to read the string
	  nErr = TESTCOC(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                  &m_nHBin, &m_nVBin, m_pszTimes, &m_nTimesLen);
	  if (0!=nErr)
	  {
	     std::ostringstream logMe;
		  logMe<<"Sensicam::OnExposure TESTCOC returns "<<nErr<<endl;
		  LogMessage(logMe.str().c_str());
	  }
      if (IsSensicamError(nErr))
         return nErr;

	  // end Liisa

	  if(wasCapturing)  // resume sequence if necessary
	  {

		  double dummy = this->m_dExposure ; // argument is not used
		  StartSequenceAcquisition(sthd_->GetLength(), dummy, this->stopOnOverflow_ );
	  }
   }
   return DEVICE_OK;
}

// Binning
int CSensicam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int nMode;
   int nErr = GET_COC_SETTING(&nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                           &m_nHBin, &m_nVBin, m_pszTimes, m_nTimesLen);
	if (0!=nErr)
	{
		std::ostringstream logMe;
		logMe<<"Sensicam::OnBinning GET_COC_SETTING returns "<<nErr<<endl;
		LogMessage(logMe.str().c_str());
	}
   if (IsSensicamError(nErr))
      return nErr;

   if (eAct == MM::AfterSet)
   {
      long bin;
      pProp->Get(bin);
      m_nHBin = bin;
      m_nVBin = bin;
      int nErr = SET_COC(nMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                         m_nHBin, m_nVBin, m_pszTimes);
		if (0!=nErr)
		{
			std::ostringstream logMe;
         logMe<<"Sensicam::OnBinning SET_COC returns "<<nErr<< ",hbin " << m_nHBin << ", vbin "<<m_nVBin<< std::endl;
			LogMessage(logMe.str().c_str());
		}
		//if (IsSensicamError(nErr))
	   //   return nErr;
   
		return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)m_nHBin);
   }
   return DEVICE_OK;
}

// Pixel type
int CSensicam::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string pixType;
      pProp->Get(pixType);
      if (pixType.compare(g_PixelType_16bit) == 0)
         pixelDepth_ = 2;
      else if (pixType.compare(g_PixelType_8bit) == 0)
         pixelDepth_ = 1;
      else
      {
         assert(!"Unsupported pixel type");
         return DEVICE_INTERNAL_INCONSISTENCY;
      }
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (pixelDepth_ == 1)
         pProp->Set(g_PixelType_8bit);
      else if (pixelDepth_ == 2)
         pProp->Set(g_PixelType_16bit);
      else
      {
         assert(!"Unsupported pixel type");
         return DEVICE_INTERNAL_INCONSISTENCY;
      }
   }
   return DEVICE_OK;
}

int CSensicam::OnGain(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   // ntohing to do  - just use the default value
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : CSensicam::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CSensicam::Initialize()
{
   // setup PCO camera
   // ----------------

   // Name
   int nRet = CreateProperty("Name", "Sensicam", MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   // Description
   nRet = CreateProperty("Description", "Sensicam driver module", MM::String, true);
   if (nRet != DEVICE_OK)
      return nRet;

   // Board
   // TODO: >>> figure out how to instantiate multiple cameras on different boards
   CPropertyAction* pAct = new CPropertyAction (this, &CSensicam::OnBoard);
   nRet = CreateProperty("Board", "0", MM::Integer, true, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   UpdateProperty("Board");
  
   // Initialize the camera
   int nErr = SET_INIT(1);
   if (nErr != 0)
      return nErr;

   // camera type (read-only)
   pAct = new CPropertyAction (this, &CSensicam::OnCameraType);
   nRet = CreateProperty("CameraType", "", MM::String, true, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   UpdateProperty("CameraType");

   m_nTimesLen = MMSENSICAM_MAX_STRLEN;
   m_dExposure = 10;

   switch(m_nCameraType)
   {
      case FASTEXP:
         // Fast Shutter
         m_nMode = M_FAST;
         m_nSubMode = NORMALFAST;
         m_nTrig = 0;
         //1ms exposure time
         m_dExposure = 1;
         snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure * 1.0e06);
      break;

      case LONGEXP:
         // Long exposure
         m_nMode = M_LONG;
         m_nSubMode = NORMALLONG;
         m_nTrig = 0;
         //10ms exposure time
         snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;

      case OEM:
         // Long exposure
         m_nMode = M_LONG;
         m_nSubMode = NORMALLONG;
         m_nTrig = 0;
         //10ms exposure time
         snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;

	  /* Liisa */
      case 8:
         m_nMode = M_LONG;
         m_nSubMode = NORMALLONG;
         m_nTrig = 0;
         //10ms exposure time
         snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;
	  /* end Liisa */
      
      default:
         // invalid type
         SET_INIT(0);
         return ERR_UNKNOWN_CAMERA_TYPE;
      break;
   }

   // CCD type (read-only)
   pAct = new CPropertyAction (this, &CSensicam::OnCCDType);
   nRet = CreateProperty("CCDType", "", MM::String, true, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   UpdateProperty("CCDType");

   // Binning
   pAct = new CPropertyAction (this, &CSensicam::OnBinning);
   m_nHBin = m_nVBin = 1;
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   binValues.push_back("8");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   switch(m_nCCDType)
   {
      case CCD74:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 20;
         m_nRoiYMax = 15;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD74C:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 20;
         m_nRoiYMax = 15;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD85:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 40;
         m_nRoiYMax = 32;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD85C:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 40;
         m_nRoiYMax = 32;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD285QE:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 43;
         m_nRoiYMax = 33;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD285QEF:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 43;
         m_nRoiYMax = 33;
         m_nHBin = m_nVBin = 1;
      break;

      case CCD285QED:
         m_nRoiXMin = m_nRoiYMin = 1;
         m_nRoiXMax = 43;
         m_nRoiYMax = 33;
         m_nHBin = m_nVBin = 1;
      break;

      default:
         // invalid type
         SET_INIT(0);
         return ERR_UNKNOWN_CAMERA_TYPE;
      break;
   }

   // establish full frame limits
   roiXMaxFull_ = m_nRoiXMax;
   roiYMaxFull_ = m_nRoiYMax;

   // Pixel type
   pAct = new CPropertyAction (this, &CSensicam::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   vector<string> pixTypes;
   pixTypes.push_back(g_PixelType_16bit);
   pixTypes.push_back(g_PixelType_8bit);
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixTypes);
   if (nRet != DEVICE_OK)
      return nRet;

   // Gain
   pAct = new CPropertyAction (this, &CSensicam::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   // Exposure
   pAct = new CPropertyAction (this, &CSensicam::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   //test if SET_COC gets right values 
   int nFullMode = m_nMode + (m_nSubMode << 16);
   nErr = TESTCOC(&nFullMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                  &m_nHBin, &m_nVBin, m_pszTimes, &m_nTimesLen);
   if (nErr != 0)
   {
      SET_INIT(0);
      return nErr;
   }

   nErr = SET_COC(nFullMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                  m_nHBin, m_nVBin, m_pszTimes);
   if (nErr != 0)
   {
      SET_INIT(0);
      return nErr;
   }

   nErr = ResizeImageBuffer();
   if (nErr != DEVICE_OK)
	   return nErr;
   m_bInitialized = true;

   // set additional properties as read-only for now

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CSensicam::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CSensicam::Shutdown()
{
   m_bInitialized = false;
   SET_INIT(0);
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CSensicam::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer
// Return type     : bool 

int CSensicam::SnapImage()
{
	// >>> set COC, change later to more efficient exposure settings
   pictime_ = GET_COCTIME()+ GET_BELTIME();

   // Liisa: this needs to be here for correct ROI after ClearROI()
	int nErr = SET_COC(m_nMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                        m_nHBin, m_nVBin, m_pszTimes);
	// end Liisa
	if (0!=nErr)
	{
		std::ostringstream logMe;
		logMe<<"Sensicam::SnapImage SET_COC returns "<<nErr<<endl;
		LogMessage(logMe.str().c_str());
	}
	if (IsSensicamError(nErr))
		return nErr;
	
	// start camera single picture
   nErr = RUN_COC(4);

	if (0!=nErr)
	{
		std::ostringstream logMe;
		logMe<<"Sensicam::SnapImage RUN_COC returns "<<nErr<<endl;
		LogMessage(logMe.str().c_str());
	}
	if (IsSensicamError(nErr))
		return nErr;
   
   // wait for picture
   int nWaittime = static_cast<int> (pictime_/1000.0 + 100);

   unsigned int uT1, uT2;
   int nPicstat;
   uT1=GetTickCount();
   nErr = 0;

   do
   {
      nErr = GET_IMAGE_STATUS(&nPicstat);
		if(IsSensicamWarning(nErr))
		{
			std::ostringstream logMe;
			logMe<<"Sensicam::SnapImage GET_IMAGE_STATUS returns "<<nErr<<endl;
			LogMessage(logMe.str().c_str());
		}
      uT2=GetTickCount();
   }
   while((!IsSensicamError(nErr)) && (uT2 < uT1 + nWaittime)&&((nPicstat & 0x02)!= 0));

	if (IsSensicamError(nErr))
	{
		std::ostringstream logMe;
		logMe<<"Sensicam::SnapImage GET_IMAGE_STATUS returns "<<nErr<<endl;
		LogMessage(logMe.str().c_str());
		return nErr;
	}

   if((nPicstat & 0x02) != 0)
      return ERR_TIMEOUT; // timeout
      //return 0;

   return DEVICE_OK;
}

unsigned CSensicam::GetBitDepth() const
{
   if (img_.Depth() == 1)
      return 8;
   else if (img_.Depth() == 2)
      return 12; // <<< TODO: obtain this setting from the hardware
   else
   {
      assert(!"unsupported bytes per pixel count");
      return 0; // should not happen
   }
}

int CSensicam::GetBinning () const
{
   return m_nHBin;
}

int CSensicam::SetBinning (int binSize) 
{
   ostringstream os;                                                         
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());                                                                                     
} 

///////////////////////////////////////////////////////////////////////////////
// Function name   : char* CSensicam::GetImageBuffer
// Description     : Returns the raw image buffer
// Return type     : const unsigned 

const unsigned char* CSensicam::GetImageBuffer()
{
	int nErr = 0;
	if (img_.Depth() == 2) {
		nErr = READ_IMAGE_12BIT (0, img_.Width(), img_.Height(), 
                              (unsigned short*) const_cast<unsigned char*>(img_.GetPixels()));
	}
	else if (img_.Depth() == 1) {
      nErr = READ_IMAGE_8BIT (0, img_.Width(), img_.Height(), 
                              const_cast<unsigned char*>(img_.GetPixels()));
	}
	else {
      assert(!"Unsupported pixel depth.");
	}

   if (nErr != 0)
      return 0;

   //stop camera, ( not really needed with RUN_COC(4); ) 
   STOP_COC(0);

   return img_.GetPixels();
}

int CSensicam::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
   int nErr = GET_COC_SETTING(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                       &m_nHBin, &m_nVBin, m_pszTimes, m_nTimesLen);
   if (nErr != 0)
      return nErr;

   // Liisa: changed these to round up, else uX or uY < 32 rounds to zero, Sensicam needs min 1.
   m_nRoiXMin = (int) ceil( ( (double) uX * m_nHBin / 32) );
   m_nRoiYMin = (int) ceil( ( (double) uY * m_nHBin / 32) );
   m_nRoiXMax = (int) ceil( ( ( (double) uX + uXSize) * m_nHBin / 32) -1 );
   m_nRoiYMax = (int) ceil( ( ( (double) uY + uYSize) * m_nHBin / 32) -1 );

   nErr = SET_COC(m_nMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                        m_nHBin, m_nVBin, m_pszTimes);
   if(nErr != 0)
   {
      GET_COC_SETTING(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                       &m_nHBin, &m_nVBin, m_pszTimes, m_nTimesLen);
	  ResizeImageBuffer();
      return nErr;
   }

   nErr = ResizeImageBuffer();
   if (nErr != 0)
	   return nErr;

   return DEVICE_OK;
}

int CSensicam::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
   int nErr = GET_COC_SETTING(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                       &m_nHBin, &m_nVBin, m_pszTimes, m_nTimesLen);

   if (nErr != 0)
      return nErr;

   uX = m_nRoiXMin * 32;
   uY = m_nRoiYMin * 32;

   uXSize = (m_nRoiXMax - m_nRoiXMin + 1) * 32;
   uYSize = (m_nRoiYMax - m_nRoiYMin + 1) * 32;

   return DEVICE_OK;
}

int CSensicam::ClearROI()
{
   int roiXMin = 1;
   int roiYMin = 1;
   int nErr = SET_COC(m_nMode, m_nTrig, roiXMin, roiXMaxFull_, roiYMin, roiYMaxFull_,
                      m_nHBin, m_nVBin, m_pszTimes);

   if(nErr != 0)
	   return nErr;
   
   // Liisa: read the current ROI to the variables to be used in SnapImage
   // Although the values set by SET_COC are correct here, it goes wrong somewhere later
   // and in SnapImage the old ROI is used
   nErr = GET_COC_SETTING(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
		&m_nHBin, &m_nVBin, m_pszTimes, m_nTimesLen);
   // end Liisa

   if(nErr != 0)
	   return nErr;

   nErr = ResizeImageBuffer();
   
	if (nErr != 0)
		return nErr;

	return DEVICE_OK;
}

void CSensicam::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

int CSensicam::ResizeImageBuffer()
{
	// get image size
	int nWidth = 1, nHeight = 1;
	int nErr = GET_IMAGE_SIZE(&nWidth, &nHeight);
	if (nErr != 0)
	{
		SET_INIT(0);
		return nErr;
	}
	assert(pixelDepth_ == 1 || pixelDepth_ == 2);
	img_.Resize(nWidth, nHeight, pixelDepth_);
	return DEVICE_OK;
}

int CSensicam::StartSequenceAcquisition(long numImages, double /*interval_ms*/, bool stopOnOverflow)
{
   if (Busy() || sequenceRunning_)
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;
   sequenceRunning_ = true;
   sthd_->SetLength(numImages);
   sthd_->Start();
   stopOnOverflow_ = stopOnOverflow;
   return DEVICE_OK;
}

int CSensicam::StopSequenceAcquisition()
{
   sthd_->Stop();
   sthd_->wait();
   sequenceRunning_ = false;
   return DEVICE_OK;
}

bool CSensicam::IsCapturing()
{
   return sequenceRunning_;
}

int CSensicam::InsertImage()
{
   const unsigned char* img = GetImageBuffer();
   if (img == 0) 
      return ERR_TIMEOUT;

   int ret = GetCoreCallback()->InsertImage(this, img, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());



   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      return GetCoreCallback()->InsertImage(this, img, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
   } else
      return ret;
}


int CSensicam::SequenceThread::svc()
{
   long count(0);
   while (!stop_ && count < numImages_)
   {
      int ret = camera_->SnapImage();
      if (ret != DEVICE_OK)
      {
         camera_->StopSequenceAcquisition();
         return 1;
      }

      ret = camera_->InsertImage();
      if (ret != DEVICE_OK)
      {
         camera_->StopSequenceAcquisition();
         return 1;
      }
		// removed this 20090728 KH
      //CDeviceUtils::SleepMs(20);
      count++;
   }
   return 0;
}