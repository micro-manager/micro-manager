///////////////////////////////////////////////////////////////////////////////
// FILE:          Hamamatsu.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Hamamatsu camera module based on DCAM API
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/24/2005
//
// CVS:           $Id$
//
#ifndef _HAMAMATSU_H_
#define _HAMAMATSU_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#ifdef WIN32
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2004-10/inc/dcamapi.h"
#else
#include <Carbon/Carbon.h>
//#include <Carbon/MacTypes.h>
#include <dcamapi/dcamapi.h>
// This DCAM constant is not declared in the dcam header files on the Mac
typedef enum DCAMERR
{
   DCAMERR_NOTSUPPORT       = 0x80000f03   /* function is not supported */
};
#endif
#include <string>
#include <map>

// error codes
#define ERR_BUFFER_ALLOCATION_FAILED 101
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE 102

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CHamamatsu : public CCameraBase<CHamamatsu>
{
public:
   static CHamamatsu* GetInstance();
   ~CHamamatsu();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   
   void GetName(char* pszName) const;
   bool Busy() {return m_bBusy;}
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();} 
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   unsigned GetBitDepth() const;
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize);
   int ClearROI();

   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   CHamamatsu();
   int ResizeImageBuffer();
   int ShutdownImageBuffer();
   bool IsFeatureSupported(int featureId);

   static CHamamatsu* m_pInstance;
   static unsigned refCount_;
   ImgBuffer img_;
   bool m_bBusy;
   bool m_bInitialized;
   bool snapInProgress_;
   long lnBin_;

   HINSTANCE m_hDCAMModule; // DCAM dll handle
   HDCAM m_hDCAM;
};

#endif //_HAMAMATSU_H_
