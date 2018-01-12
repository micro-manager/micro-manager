///////////////////////////////////////////////////////////////////////////////
// FILE:          MoticCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Motic camera device adapter for Windows
// COPYRIGHT:     2012 Motic China Group Co., Ltd.
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Motic

#include "MoticCamera.h"
#include "ModuleInterface.h"
#include "MoticImageDevicesProxy.h"
#include <algorithm>
#include <iterator>
//#define _LOG_OUT_
const char* g_CameraName = "MoticCam";

const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supported hardware devices here
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraName, MM::CameraDevice, "Motic Camera Adapter");
#ifdef _LOG_OUT_
   OutputDebugString("InitializeModuleData");
#endif
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
#ifdef _LOG_OUT_
   OutputDebugString(deviceName);
#endif
   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraName) == 0)
   {
#ifdef _LOG_OUT_
   OutputDebugString("Create Motic Camera");
#endif
      // create camera
      return new CMoticCamera();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
#ifdef _LOG_OUT_
  OutputDebugString("Delete Camera");
#endif
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CMoticCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

/**
* CMoticCamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
CMoticCamera::CMoticCamera() :
   m_iBinning(1),
   m_dGain(1.0),
   m_iBytesPerPixel(4),
   m_bInitialized(false),
   m_dExposurems(10.0),
   m_dMinGain(1.0),
   m_dMaxGain(1.0),
   m_iRoiX(0),
   m_iRoiY(0),
   //m_thd(0),
   m_bShow(false),
   m_pBuffer(0),
   m_iBufferSize(0),
   m_iBitCounts(24),
   m_bROI(false),
   m_iCurDeviceIdx(-1),
   m_lMinExposure(0),
   m_lMaxExposure(0),
   stopOnOverflow(false),
   m_bNeedPush(false)
{
#ifdef _LOG_OUT_
  OutputDebugString("New Motic Camera");
#endif
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   // setup additional error codes/messages
   SetErrorText(ERR_NO_CAMERAS_FOUND, "No cameras found.  Please connect a Motic camera and turn it on");
   SetErrorText(ERR_BUSY_ACQUIRING,   "Motic camera is already acquiring images.");
   SetErrorText(ERR_SOFTWARE_TRIGGER_FAILED, "Motic camera is not in software trigger mode.");     
}

/**
* CMoticCamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CMoticCamera::~CMoticCamera()
{
#ifdef _LOG_OUT_
  OutputDebugString("~CMoticCamera");
#endif
   if (m_bInitialized)
      Shutdown();

   StopSequenceAcquisition();
   //delete m_thd;
   if(m_pBuffer)
   {
     delete[]m_pBuffer;
     m_pBuffer = 0;
   }
   m_iBufferSize = 0;
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void CMoticCamera::GetName(char* name) const
{
#ifdef _LOG_OUT_
  OutputDebugString("GetName");
#endif
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraName);
#ifdef _LOG_OUT_
   OutputDebugString(name);
#endif
}

/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int CMoticCamera::Initialize()
{
   if (m_bInitialized)
      return DEVICE_OK;

#ifdef _LOG_OUT_
   OutputDebugString("Initialize");
#endif
   // set property list
   // -----------------
   if(MIDP_Open(0) != 0)
   {
     return ERR_NO_CAMERAS_FOUND;
   }
   m_iDevices = MIDP_GetCameraCount();
   if(m_iDevices < 1)
     return ERR_NO_CAMERAS_FOUND;
   m_iCurDeviceIdx = MIDP_GetCurCameraIndex();
   int ret;
   CPropertyAction* pAct;

   wchar_t strName[MAX_PATH];
   char sName[MAX_PATH];
   MIDP_GetCameraName(m_iCurDeviceIdx, strName, MAX_PATH);
   WideCharToMultiByte(CP_ACP, 0, strName, MAX_PATH, sName, MAX_PATH, NULL, NULL);
   pAct = new CPropertyAction(this, &CMoticCamera::OnDevice);
   ret = CreateProperty(g_Keyword_Cameras,sName, MM::String, false, pAct);
   //assert(ret == DEVICE_OK);
   /*vector<string> vName*/;
   m_vDevices.clear();
   for(int i = 0; i < m_iDevices; i++)
   {   
     MIDP_GetCameraName(i, strName, MAX_PATH);
     WideCharToMultiByte(CP_ACP, 0, strName, MAX_PATH, sName, MAX_PATH, NULL, NULL);
     m_vDevices.push_back(sName);
   }
   ret = SetAllowedValues(g_Keyword_Cameras, m_vDevices);
   
   pAct = new CPropertyAction(this, &CMoticCamera::OnShowUI);
   ret = CreateProperty(g_Keyword_MoticUI, "off", MM::String, false, pAct);
   vector<string> vShow;
   vShow.push_back("on");
   vShow.push_back("off");
   ret = SetAllowedValues(g_Keyword_MoticUI, vShow);
   m_bShow = false;
   assert(ret == DEVICE_OK);

   ret = InitDevice();
   if(ret == DEVICE_OK)
   {
     m_bInitialized = true;
   }
#ifdef _LOG_OUT_
   OutputDebugString("Initialize OK");
#endif
   return ret;   
}

/**
* Shuts down (unloads) the device.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* Required by the MM::Device API.
*/
int CMoticCamera::Shutdown()
{
#ifdef _LOG_OUT_
  OutputDebugString("Shutdown");
#endif
  if(m_bInitialized)
  {
    MIDP_Close();
  }
   m_bInitialized = false;
   return DEVICE_OK;
}

inline void CoverImage8( BYTE* pSour, BYTE* pDest, int sz, int m_iBytesPerPixel ) 
{
  switch(m_iBytesPerPixel)
  {
  case 1://8->8
    {
      memcpy(pDest, pSour, sz);
    }
    break;
  case 2://8->16
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i <sz; i++)
      {
        *pD++  = ((*pSour++)<<8);
      }
    }
    break;
  case 4://8->32
    {
      for(int i = 0; i < sz; i++)
      {
        *pDest ++ = *pSour;
        *pDest ++ = *pSour;
        *pDest ++ = *pSour;
        pDest++;
        pSour++;
      }
    }
    break;
  case 8://8->64
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i < sz; i++)
      {
        *pD ++ = ((*pSour)<<8);
        *pD ++ = ((*pSour)<<8);
        *pD ++ = ((*pSour)<<8);
        *pD ++;
        pSour++;
      }
    }
    break;
  }

}

inline void CoverImage16( BYTE* pSour, BYTE* pDest, int sz, int m_iBytesPerPixel ) 
{
  unsigned short*pS = (unsigned short*)pSour;
  switch(m_iBytesPerPixel)
  {
  case 1://16->8
    {      
      for(int i = 0; i <sz; i++)
      {
        *pDest++  = ((*pS++)>>8);
      }
    }
    break;
  case 2://16->16
    {
      memcpy(pDest, pSour, sz*2);
    }
    break;
  case 4://16->32
    {      
      for(int i = 0; i < sz; i++)
      {
        BYTE val = ((*pS)>>8);
        *pDest ++ = val;
        *pDest ++ = val;
        *pDest ++ = val;
        pDest++;
        pS++;
      }
    }
    break;
  case 8://16->64
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i < sz; i++)
      {
        *pD ++ = *pS;
        *pD ++ = *pS;
        *pD ++ = *pS;
        pD ++;
        pS++;
      }
    }
    break;
  }
}

inline void CoverImage24( BYTE* pSour, BYTE* pDest, int sz, int m_iBytesPerPixel ) 
{
  switch(m_iBytesPerPixel)
  {
  case 1://24->8
    {
      for(int i = 0; i < sz; i++)
      {
        *pDest++ = static_cast<BYTE>((pSour[0]*299 + pSour[1]*587 + pSour[3]*114) / 1000);
        pSour += 3;
      }
    }
    break;
  case 2://24->16
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i <sz; i++)
      {
        *pD++  = (((pSour[0]*299 + pSour[1]*587 + pSour[3]*114)/1000)<<8);
        pSour += 3;
      }
    }
    break;
  case 4://24->32
    {
      for(int i = 0; i < sz; i++)
      {
        *pDest ++ = *pSour++;
        *pDest ++ = *pSour++;
        *pDest ++ = *pSour++;
        pDest++;
      }
    }
    break;
  case 8://24->64
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i < sz; i++)
      {
        *pD ++ = ((*pSour++)<<8);
        *pD ++ = ((*pSour++)<<8);
        *pD ++ = ((*pSour++)<<8);
        *pD ++;     
      }
    }
    break;
  }
}

inline void CoverImage48( BYTE* pSour, BYTE* pDest, int sz, int m_iBytesPerPixel ) 
{
  unsigned short* pS = (unsigned short*)pSour;
  switch(m_iBytesPerPixel)
  {
  case 1://48->8
    {
      for(int i = 0; i < sz; i++)
      {
        *pDest++ = (((pS[0]*299 + pS[1]*587 + pS[3]*114)/1000)>>8);
        pS += 3;
      }
    }
    break;
  case 2://48->16
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i <sz; i++)
      {
        *pD++  = (pS[0]*299 + pS[1]*587 + pS[3]*114)/1000;
        pS += 3;
      }
    }
    break;
  case 4://48->32
    {
      for(int i = 0; i < sz; i++)
      {
        *pDest ++ = ((*pS++)>>8);
        *pDest ++ = ((*pS++)>>8);
        *pDest ++ = ((*pS++)>>8);
        pDest++;
      }
    }
    break;
  case 8://48->64
    {
      unsigned short* pD = (unsigned short*)pDest;
      for(int i = 0; i < sz; i++)
      {
        *pD ++ = *pS++;
        *pD ++ = *pS++;
        *pD ++ = *pS++;
        *pD ++;     
      }
    }
    break;
  }
}


/**
* Performs exposure and grabs a single image.
* This function should block during the actual exposure and return immediately afterwards 
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CMoticCamera::SnapImage()
{
#ifdef _LOG_OUT_
  OutputDebugString("SnapImage");
#endif
long x,y, bits, channel;
 if(MIDP_GetFormat(&x,&y, &bits, &channel) == 0)
 {   
   ReAllocalBuffer(channel*bits*x*y/8);
 }
#ifdef _LOG_OUT_
  OutputDebugString("MIDP_GetFrameEx");
#endif
  int w = m_img.Width();
  int h = m_img.Height();  
  int iTry = 0;
  while( 0 != MIDP_GetFrameEx((unsigned char*)m_pBuffer, m_iRoiX, m_iRoiY, w, h, w*m_iBitCounts/8, true)||m_bNeedPush)
  {
#ifdef _LOG_OUT_
    OutputDebugString("MIDP_GetFrameEx-ERR");
#endif
    if(m_bNeedPush)
    {
      m_bNeedPush = false;
      Sleep(30);
      continue;
    }
    Sleep(100);
    if(+iTry > 10)
      break;    
  }
  
#ifdef _LOG_OUT_
  OutputDebugString("Convert Image");
#endif
  BYTE* pDest = m_img.GetPixelsRW(); 
  int size = w*h;
  switch(m_iBitCounts)
  {
  case 8:
    CoverImage8(m_pBuffer, pDest, size, m_iBytesPerPixel);
    break;
  case 16:
    CoverImage16(m_pBuffer, pDest, size, m_iBytesPerPixel);
    break;
  case 24:
    CoverImage24(m_pBuffer, pDest, size, m_iBytesPerPixel);
    break;
  case 48:
    CoverImage48(m_pBuffer, pDest, size, m_iBytesPerPixel);
    break;
  default:
#ifdef _LOG_OUT_
    OutputDebugString("Error bit counts");
#endif
    break;
  } 

#ifdef _LOG_OUT_
  OutputDebugString("SnapImage OK");
#endif
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
const unsigned char* CMoticCamera::GetImageBuffer()
{
#ifdef _LOG_OUT_
  OutputDebugString("GetImageBuffer");
#endif
   return const_cast<unsigned char*>(m_img.GetPixels());
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMoticCamera::GetImageWidth() const
{
 #ifdef _LOG_OUT_
   OutputDebugString("GetImageWidth");
 #endif
   return m_img.Width();
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned CMoticCamera::GetImageHeight() const
{
 #ifdef _LOG_OUT_
   OutputDebugString("GetImageHeight");
 #endif
   return m_img.Height();
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned CMoticCamera::GetImageBytesPerPixel() const
{
#ifdef _LOG_OUT_
  OutputDebugString("GetImageBytes");
#endif
   return m_img.Depth();
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned CMoticCamera::GetBitDepth() const
{
 #ifdef _LOG_OUT_
   OutputDebugString("GetBitDepth");
 #endif
  if(m_iBytesPerPixel == 1 || m_iBytesPerPixel == 4)
   return 8;
  else
    return 16;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CMoticCamera::GetImageBufferSize() const
{
 #ifdef _LOG_OUT_
   OutputDebugString("GetImageBufferSize");
 #endif
   return m_img.Width() * m_img.Height() * GetImageBytesPerPixel();
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
int CMoticCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
 #ifdef _LOG_OUT_
   OutputDebugString("SetROI");
 #endif
   if (xSize == 0 && ySize == 0)
   {   
      // effectively clear ROI
      ResizeImageBuffer();
      m_iRoiX = 0;
      m_iRoiY = 0;
       m_bROI = false;
   }
   else
   {
     m_bROI = true;
     // apply ROI
     m_img.Resize(xSize, ySize);
     m_iRoiX = x;
     m_iRoiY = y;
      
   }
 #ifdef _LOG_OUT_
    OutputDebugString("SetROI OK");
 #endif
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int CMoticCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
 #ifdef _LOG_OUT_
   OutputDebugString("GetROI");
 #endif
   x = m_iRoiX;
   y = m_iRoiY;

   xSize = m_img.Width();
   ySize = m_img.Height();
 #ifdef _LOG_OUT_
    OutputDebugString("GetROI OK");
 #endif
   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int CMoticCamera::ClearROI()
{
#ifdef _LOG_OUT_
  OutputDebugString("ClearROI");
#endif
   ResizeImageBuffer();
   m_iRoiX = 0;
   m_iRoiY = 0;
   m_bROI = false;
#ifdef _LOG_OUT_
   OutputDebugString("ClearROI OK");
#endif    
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double CMoticCamera::GetExposure() const
{
#ifdef _LOG_OUT_
  OutputDebugString("GetExposure");
#endif
   return m_dExposurems;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void CMoticCamera::SetExposure(double exp)
{
#ifdef _LOG_OUT_
  OutputDebugString("SetExposure");
#endif
   m_dExposurems = exp;  
   long curExp = (long)(m_dExposurems*100);
   MIDP_SetExposureTime(curExp);
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int CMoticCamera::GetBinning() const
{
#ifdef _LOG_OUT_
  OutputDebugString("GetBinning");
#endif
   return m_iBinning;
}

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int CMoticCamera::SetBinning(int binF)
{
#ifdef _LOG_OUT_
  OutputDebugString("SetBinning");
#endif
  MIDP_SelectResByIndex(m_iBinning);
  ResizeImageBuffer();
  return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

int CMoticCamera::PrepareSequenceAcqusition()
{
#ifdef _LOG_OUT_
  OutputDebugString("PrepareSequenceAcqusition");
#endif
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;
#ifdef _LOG_OUT_
   OutputDebugString("PrepareSequenceAcqusition OK");
#endif
   return DEVICE_OK;
}


// /**
//  * Required by the MM::Camera API
//  * Please implement this yourself and do not rely on the base class implementation
//  * The Base class implementation is deprecated and will be removed shortly
//  */
// int CMoticCamera::StartSequenceAcquisition(double interval) 
// {
// #ifdef _LOG_OUT_
//   OutputDebugString("StartSequenceAcquisition");
// #endif
// 
//    return StartSequenceAcquisition(LONG_MAX, interval, false);            
// }
// 
// /**                                                                       
// * Stop and wait for the Sequence thread finished                                   
// */                                                                        
// int CMoticCamera::StopSequenceAcquisition()                                     
// {   
// #ifdef _LOG_OUT_
//   OutputDebugString("StopSequenceAcquisition");
// #endif
//    if (!thd_->IsStopped()) {
//       thd_->Stop();                                                       
//       thd_->wait();                                                       
//    }                                                                      
// #ifdef _LOG_OUT_
//    OutputDebugString("StopSequenceAcquisition OK");
// #endif                                                                          
//    return DEVICE_OK;                                                      
// } 
// 
// /**
// * Simple implementation of Sequence Acquisition
// * A sequence acquisition should run on its own thread and transport new images
// * coming of the camera into the MMCore circular buffer.
// */
// int CMoticCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
// {
// #ifdef _LOG_OUT_
//   OutputDebugString("StartSequenceAcquisition");
// #endif
//    if (IsCapturing())
//       return DEVICE_CAMERA_BUSY_ACQUIRING;
// 
//    int ret = GetCoreCallback()->PrepareForAcq(this);
//    if (ret != DEVICE_OK)
//       return ret;
//    //sequenceStartTime_ = GetCurrentMMTime();
//    //imageCounter_ = 0;
//    thd_->Start(numImages,interval_ms);
//    stopOnOverflow_ = stopOnOverflow;
// #ifdef _LOG_OUT_
//    OutputDebugString("StartSequenceAcquisition OK");
// #endif
//    return DEVICE_OK;
// }

/*
 * Inserts Image and MetaData into MMCore circular Buffer
 */
int CMoticCamera::InsertImage()
{
#ifdef _LOG_OUT_
  OutputDebugString("InsertImage");
#endif
   MM::MMTime timeStamp = this->GetCurrentMMTime();
   char label[MM::MaxStrLength];
   this->GetLabel(label);
 
   // Important:  metadata about the image are generated here:
   Metadata md;

   const unsigned char* img;

   #ifdef	LOG_ENABLED
   //sLogMessage("insert image in micro manager pool\r\n");
   #endif

   // call to GetImageBuffer will complete any pending capture
   img = GetImageBuffer();
   if (img == 0) 
   {	
     return DEVICE_ERR;
   }

   int ret = GetCoreCallback()->InsertImage(this, img, GetImageWidth(), 
     GetImageHeight(), GetImageBytesPerPixel());

   if (!stopOnOverflow && ret == DEVICE_BUFFER_OVERFLOW)
   {
     // do not stop on overflow - just reset the buffer
     GetCoreCallback()->ClearImageBuffer(this);
     return(GetCoreCallback()->InsertImage(this, img, 
       GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel()));
   } 
   else
   {
     return ret;
   }
#ifdef _LOG_OUT_
   OutputDebugString("InsertImage OK");
#endif
   return DEVICE_OK;
}


bool CMoticCamera::IsCapturing() 
{
  if(CCameraBase::IsCapturing())
  {
#ifdef _LOG_OUT_
    OutputDebugString("IsCapturing true");
#endif
    return true;
  }
#ifdef _LOG_OUT_
  OutputDebugString("IsCapturing false");
#endif
  NeedToPush();
  return false;
}


///////////////////////////////////////////////////////////////////////////////
// CMoticCamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Handles "Binning" property.
*/
int CMoticCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 #ifdef _LOG_OUT_
   OutputDebugString("OnBinning");
 #endif
   if (eAct == MM::AfterSet)
   {
 #ifdef _LOG_OUT_
      OutputDebugString("AfterSet");
 #endif
      long binSize;
      pProp->Get(binSize);
      m_iBinning = (int)binSize;
      MIDP_SelectResByIndex(m_iBinning);
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
 #ifdef _LOG_OUT_
      OutputDebugString("BeforeGet");
 #endif
     m_iBinning = MIDP_GetCurResolutionIndex();
      pProp->Set((long)m_iBinning);
   }
 #ifdef _LOG_OUT_
    OutputDebugString("OnBinning OK");
 #endif
   return DEVICE_OK;
}

/**
* Handles "PixelType" property.
*/
int CMoticCamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef _LOG_OUT_
  OutputDebugString("OnPixelType");
#endif
    if (eAct == MM::AfterSet)
    {
      StopSequenceAcquisition();
       string val;
       pProp->Get(val);
       if (val.compare(g_PixelType_8bit) == 0)
       {
         MIDP_SetBitCount(8);
         Sleep(1000);
          m_iBytesPerPixel = 1;
          m_iBitCounts = 8;
       }
       else if(val.compare(g_PixelType_16bit) == 0)
       {
         MIDP_SetBitCount(16);
         Sleep(1000);
         m_iBytesPerPixel = 2;
         m_iBitCounts = 16;
       }      
       else if (val.compare(g_PixelType_32bitRGB) == 0)
       {
         MIDP_SetBitCount(8);
         Sleep(1000);
         m_iBytesPerPixel = 4;
         m_iBitCounts = 24;
       }
       else if(val.compare(g_PixelType_64bitRGB) == 0)
       {
         MIDP_SetBitCount(16);
          Sleep(1000);
         m_iBytesPerPixel = 8;
         m_iBitCounts = 48;
       }
       else
          assert(false);
       ////////////////////////////////
       
       //////////////////////////////////////////////////////////////////////////
       char buf[MM::MaxStrLength];
       GetProperty(MM::g_Keyword_PixelType, buf);
       std::string pixelType(buf);
       if (pixelType.compare(g_PixelType_8bit) == 0)
       {
         if(m_iBytesPerPixel == 2)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);           
         }
         else if(m_iBytesPerPixel == 4)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
         }
         else if(m_iBytesPerPixel == 8)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
         }
       }
       else if (pixelType.compare(g_PixelType_16bit) == 0)
       {
         if(m_iBytesPerPixel == 1)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
         }
         else if(m_iBytesPerPixel == 4)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
         }
         else if(m_iBytesPerPixel == 8)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
         }
       }
       else if (pixelType.compare(g_PixelType_32bitRGB) == 0)
       {
         if(m_iBytesPerPixel == 1)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
         }
         else if(m_iBytesPerPixel == 2)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
         }        
         else if(m_iBytesPerPixel == 8)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_64bitRGB);
         }
       }
       else if (pixelType.compare(g_PixelType_64bitRGB) == 0)
       {
         if(m_iBytesPerPixel == 1)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_8bit);
         }
         else if(m_iBytesPerPixel == 2)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_16bit);
         }
         else if(m_iBytesPerPixel == 4)
         {
           SetProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB);
         }        
       }
       SaveToReg(m_iBytesPerPixel);
       
       ResizeImageBuffer();
    }
    else if (eAct == MM::BeforeGet)
    {
       if (m_iBytesPerPixel == 1)
          pProp->Set(g_PixelType_8bit);
       else if(m_iBytesPerPixel == 2)
         pProp->Set(g_PixelType_16bit);
       else if (m_iBytesPerPixel == 4)
          pProp->Set(g_PixelType_32bitRGB);
       else if(m_iBytesPerPixel == 8)
         pProp->Set(g_PixelType_64bitRGB);
       else
          assert(false); // this should never happen
    }
#ifdef _LOG_OUT_
    OutputDebugString("OnPixelType OK");
#endif
   return DEVICE_OK;
}

/**
* Handles "Gain" property.
*/
int CMoticCamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef _LOG_OUT_
  OutputDebugString("OnGain");
#endif
   if (eAct == MM::AfterSet)
   {
      double dGain;
      pProp->Get(dGain);
      m_dGain = static_cast<float>(dGain);
      MIDP_SetGain(m_dGain);
   }
   else if (eAct == MM::BeforeGet)
   {     
      pProp->Set(m_dGain);
   }
#ifdef _LOG_OUT_
   OutputDebugString("OnGain OK");
#endif
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Private CMoticCamera methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int CMoticCamera::ResizeImageBuffer()
{
  StopSequenceAcquisition();
#ifdef _LOG_OUT_
  OutputDebugString("ResizeImageBuffer");
#endif
  int w = m_vBinning[2*m_iBinning];
  int h = m_vBinning[2*m_iBinning + 1];
   m_img.Resize(w, h, m_iBytesPerPixel);
   ReAllocalBuffer(w*h*m_iBitCounts/8);
#ifdef _LOG_OUT_
   char cOut[100];
   wsprintf(cOut, "%d--%d-%d", m_img.Width(), m_img.Height(), m_iBitCounts);
   OutputDebugString(cOut);
#endif

   return DEVICE_OK;
}

///**
// * Generate an image with fixed value for all pixels
// */
//void CMoticCamera::GenerateImage()
//{
//   //const int maxValue = (1 << MAX_BIT_DEPTH) - 1; // max for the 12 bit camera
//   //const double maxExp = 1000;
//   //double step = maxValue/maxExp;
//   //unsigned char* pBuf = const_cast<unsigned char*>(m_img.GetPixels());
//   //memset(pBuf, (int) (step * max(m_dExposurems, maxExp)), m_img.Height()*m_img.Width()*m_img.Depth());
//}

int CMoticCamera::OnDevice( MM::PropertyBase* pProp, MM::ActionType eAct )
{
#ifdef _LOG_OUT_
  OutputDebugString("OnDevice");
#endif
  if (eAct == MM::AfterSet)
  {
    string strName;
    pProp->Get(strName);

    vector<string>::const_iterator begin = m_vDevices.begin();
    vector<string>::const_iterator end = m_vDevices.end();
    vector<string>::const_iterator it = find(begin, end, strName);
    vector<string>::difference_type idx = distance(begin, it);
    if (it != end && idx != m_iCurDeviceIdx)
    {
      StopSequenceAcquisition();
      if(MIDP_SelectCamera(static_cast<long>(idx)) != 0)
        return DEVICE_ERR;
      m_iCurDeviceIdx = MIDP_GetCurCameraIndex();
      InitDevice();
    }   
  }
  else if (eAct == MM::BeforeGet)
  {    
    if(m_iCurDeviceIdx >= 0 && m_iCurDeviceIdx < m_iDevices)
    {
      string strName = m_vDevices[m_iCurDeviceIdx];
      pProp->Set(strName.c_str());
    }
  }

  return DEVICE_OK;
}

void CMoticCamera::InitBinning()
{
#ifdef _LOG_OUT_
  OutputDebugString("InitBinning");
#endif
  m_vBinning.clear();
  int c = MIDP_GetResolutionCount();
  for(int i = 0; i < c; i++)
  {
    long x, y;
    MIDP_GetResolution(i, &x, &y);
    m_vBinning.push_back(x);
    m_vBinning.push_back(y);
  }
  m_iBinning = MIDP_GetCurResolutionIndex();
  char bin[3];
      
  // binning
  CPropertyAction *pAct = new CPropertyAction (this, &CMoticCamera::OnBinning);
  int ret = CreateProperty(MM::g_Keyword_Binning, _itoa(m_iBinning,bin, 10), MM::Integer, false, pAct);
  //assert(ret == DEVICE_OK);

  vector<string> binningValues;
  for(int i = 0; i < c; i++)
  {
    binningValues.push_back(_itoa(i,bin, 10));
  }  

  ret = SetAllowedValues(MM::g_Keyword_Binning, binningValues);
#ifdef _LOG_OUT_
  OutputDebugString("InitBinning OK");
#endif
  assert(ret == DEVICE_OK);
}

int CMoticCamera::OnShowUI( MM::PropertyBase* pProp, MM::ActionType eAct )
{
#ifdef _LOG_OUT_
  OutputDebugString("OnShowUI");
#endif
  if (eAct == MM::AfterSet)
  {
#ifdef _LOG_OUT_
    OutputDebugString("OnShowUI AfterSet");
#endif
    string val;
    pProp->Get(val);
    if (val.compare("on") == 0)
    {
      MIDP_Show(2);
      m_bShow = true;
    }
    else if (val.compare("off") == 0)
    {
      MIDP_Show(0);
      m_bShow = false;
    }
    else
    {
      assert(false);
    }
  }
  else if(eAct == MM::BeforeGet)
  {
#ifdef _LOG_OUT_
    OutputDebugString("OnShowUI BeforeGet");
#endif
    if(m_bShow)
    {
      pProp->Set("on");
    }
    else
    {
      pProp->Set("off");
    }
  }
#ifdef _LOG_OUT_
  OutputDebugString("OnShowUI OK");
#endif
  return DEVICE_OK;
}

void CMoticCamera::InitPixelType()
{
#ifdef _LOG_OUT_
  OutputDebugString("InitPixelType");
#endif
  long x,y, bits, channels;
  if(0 == MIDP_GetFormat(&x, &y, &bits, &channels))
  {
#ifdef _LOG_OUT_
    OutputDebugString("MIDP_GetFormat Okay");
#endif
    m_iBitCounts = bits*channels;//8->1,16->2,24->3(32->4),48->6(64->16)

  }  
 // pixel type
  CPropertyAction*pAct = new CPropertyAction (this, &CMoticCamera::OnPixelType);
  int ret; 
  vector<string> pixelTypeValues;
  if(channels == 1)
  {
    ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, false, pAct);   
    pixelTypeValues.push_back(g_PixelType_8bit); 
    m_iBytesPerPixel = 1;
   // if(MIDP_Has16Bits() == 0)
    {
      pixelTypeValues.push_back(g_PixelType_16bit);
    }
  }
  else if(channels == 3)
  {
    ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_32bitRGB, MM::String, false, pAct);
    pixelTypeValues.push_back(g_PixelType_8bit);
    pixelTypeValues.push_back(g_PixelType_32bitRGB);
   // if(MIDP_Has16Bits() == 0)
    {
      pixelTypeValues.push_back(g_PixelType_16bit);
      pixelTypeValues.push_back(g_PixelType_64bitRGB);
    }
    m_iBytesPerPixel = 4;
  }
  ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);  
#ifdef _LOG_OUT_
  OutputDebugString("InitPixelType OK");
#endif
  assert(ret == DEVICE_OK);
  
  switch(ReadFromReg())
  {
  case 1: 
    MIDP_SetBitCount(8);
    Sleep(1000);
    m_iBytesPerPixel = 1;
    m_iBitCounts = 8;
   
    break;
  case 2:
    MIDP_SetBitCount(16);
    Sleep(1000);
    m_iBytesPerPixel = 2;
    m_iBitCounts = 16;   
    break;
  case 4:
    MIDP_SetBitCount(8);
    Sleep(1000);
    m_iBytesPerPixel = 4;
    m_iBitCounts = 24;    
    break;
  case 8:
    MIDP_SetBitCount(16);
    Sleep(1000);
    m_iBytesPerPixel = 8;
    m_iBitCounts = 48;    
    break;
  default:
    break;
  }
}

void CMoticCamera::InitGain()
{
#ifdef _LOG_OUT_
  OutputDebugString("InitGain");
#endif
  
  MIDP_GetGainRange(&m_dMinGain, &m_dMaxGain);
  CPropertyAction *pAct = new CPropertyAction (this, &CMoticCamera::OnGain);
   CreateProperty(MM::g_Keyword_Gain, "1.0", MM::Float, false, pAct);
   //assert(ret == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_Gain, m_dMinGain, m_dMaxGain);
   m_dGain = 1.0;
}

void CMoticCamera::InitExposure()
{
#ifdef _LOG_OUT_
  OutputDebugString("InitExposure");
#endif
  MIDP_GetExposureTimeRange(&m_lMinExposure, &m_lMaxExposure);
  long curExp = 0;
  MIDP_GetExposureTime(&curExp); 
  m_dExposurems = curExp/100.0;
  char buf[10];
  sprintf(buf, "%0.1f\0", m_dExposurems);
  CPropertyAction *pAct = new CPropertyAction (this, &CMoticCamera::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, buf, MM::Float, false, pAct);
   SetPropertyLimits(MM::g_Keyword_Exposure, (double)m_lMinExposure/100.0, (double)m_lMaxExposure/100.0);
  // m_dExposurems = 10.0;
}

int CMoticCamera::OnExposure( MM::PropertyBase* pProp, MM::ActionType eAct )
{
#ifdef _LOG_OUT_
  OutputDebugString("OnExposure");
#endif
  if (eAct == MM::AfterSet)
  {
#ifdef _LOG_OUT_
    OutputDebugString("AfterSet");
#endif
    pProp->Get(m_dExposurems);
    long curExp = (long)(m_dExposurems*100);
    MIDP_SetExposureTime(curExp);
  }
  else if (eAct == MM::BeforeGet)
  {
#ifdef _LOG_OUT_
    OutputDebugString("m_dExposurems");
#endif
    long curExp;
    MIDP_GetExposureTime(&curExp);
    m_dExposurems = curExp/100.0;
    pProp->Set(m_dExposurems);
  }
#ifdef _LOG_OUT_
  OutputDebugString("OnExposure OK");
#endif
  return DEVICE_OK;
}

int CMoticCamera::InitDevice()
{ 
   //Bining
   InitBinning();

   InitPixelType();  
  
   InitGain();

   InitExposure();


   // synchronize all properties
   // --------------------------
 
   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
     return ret;

   // setup the buffer
   // ----------------
   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
     return ret;

  
   return DEVICE_OK;
}

void CMoticCamera::ReAllocalBuffer(int size)
{
  if(size <= m_iBufferSize)return;
  if(m_pBuffer)
  {
    delete[] m_pBuffer;
  }   
  m_iBufferSize = size;
  m_pBuffer = new BYTE[size];
}

void CMoticCamera::SaveToReg( int pixelsize )
{
  TCHAR deviceName[256]; 
  TCHAR strReg[MAX_PATH];  
  
#ifdef _UNICODE
  MIDP_GetCameraName(MIDP_GetCurCameraIndex(), deviceName, 256);
  swprintf(strReg, TEXT("Software\\Motic China Group Co., Ltd.\\MicroManager\\%s"), deviceName);
#else
  MIDP_GetCameraNameA(MIDP_GetCurCameraIndex(), deviceName, 256);
  sprintf(strReg, TEXT("Software\\Motic China Group Co., Ltd.\\MicroManager\\%s"), deviceName);
#endif

  HKEY hKey;
  DWORD dwDisp; 
  if(::RegCreateKeyEx(HKEY_CURRENT_USER, strReg, 0, NULL, REG_OPTION_NON_VOLATILE, KEY_WRITE, NULL, &hKey, &dwDisp) == ERROR_SUCCESS)
  {
    DWORD val = pixelsize;
    DWORD size = sizeof(DWORD);   
    ::RegSetValueEx(hKey, TEXT("pixel"), NULL, REG_DWORD, (BYTE*)&val, size);
    ::RegCloseKey(hKey);
  }
}

int CMoticCamera::ReadFromReg()
{
  TCHAR deviceName[256]; 
  TCHAR strReg[MAX_PATH];  

#ifdef _UNICODE
  MIDP_GetCameraName(MIDP_GetCurCameraIndex(), deviceName, 256);
  swprintf(strReg, TEXT("Software\\Motic China Group Co., Ltd.\\MicroManager\\%s"), deviceName);
#else
  MIDP_GetCameraNameA(MIDP_GetCurCameraIndex(), deviceName, 256);
  sprintf(strReg, TEXT("Software\\Motic China Group Co., Ltd.\\MicroManager\\%s"), deviceName);
#endif
  int pixel = -1;
  HKEY hKey; 
  if(::RegOpenKeyEx(HKEY_CURRENT_USER, strReg, 0, KEY_QUERY_VALUE, &hKey) == ERROR_SUCCESS)
  {
    DWORD val;
    DWORD size = sizeof(DWORD);
    if(::RegQueryValueEx(hKey, TEXT("pixel"), NULL, NULL, (BYTE*)&val, &size) == ERROR_SUCCESS)
    {
      pixel = val;      
    }
    ::RegCloseKey(hKey);
  }
  return pixel;
}

void CMoticCamera::NeedToPush()
{
  m_bNeedPush = true;
// #ifdef _LOG_OUT_
//   OutputDebugString("NeedToPush");
// #endif
//   if(!m_pBuffer)return;
// #ifdef _LOG_OUT_
//   OutputDebugString("NeedToPush1");
// #endif
//  /* long x,y, bits, channel;
//   if(MIDP_GetFormat(&x,&y, &bits, &channel) == 0)
//   {   
// #ifdef _LOG_OUT_
//     OutputDebugString("NeedToPush0");
// #endif
//     ReAllocalBuffer(channel*bits*x*y/8);
//   }
// #ifdef _LOG_OUT_
//   OutputDebugString("NeedToPush2");
// #endif*/
//   int w = m_img.Width();
//   int h = m_img.Height();  
// #ifdef _LOG_OUT_  
//   char buffer[100];
//   sprintf(buffer, "%p-%d(%dx%dx%d/8)\0", m_pBuffer, m_iBufferSize, w, h, m_iBitCounts);
//   OutputDebugString(buffer);
// #endif
//   MIDP_GetFrameEx((unsigned char*)m_pBuffer, m_iRoiX, m_iRoiY, w, h, w*m_iBitCounts/8, true);
// #ifdef _LOG_OUT_
//   OutputDebugString("NeedToPush OK");
// #endif
}
