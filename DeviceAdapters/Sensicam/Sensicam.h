///////////////////////////////////////////////////////////////////////////////
// FILE:          Sensicam.h
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
//                License text is included with the source distribution.
// CVS:           $Id$
//
#ifndef _SENSICAM_H_
#define _SENSICAM_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <map>

#define MMSENSICAM_MAX_STRLEN    50
#define ERR_UNKNOWN_CAMERA_TYPE  11
#define ERR_TIMEOUT              12

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CSensicam : public CCameraBase<CSensicam>
{
public:
   static CSensicam* GetInstance()
   {
      if (!m_pInstance)
         m_pInstance = new CSensicam();
      return m_pInstance;
   }
   ~CSensicam();
   
   // MMDevice API
   int Initialize();
   int Shutdown();
   
   void GetName(char* pszName) const {CDeviceUtils::CopyLimitedString(pszName, "Sensicam");}
   bool Busy() {return m_bBusy;}
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();} 
   unsigned GetBitDepth() const;
   int GetBinning() const;
   int SetBinning(int binSize);
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   double GetExposure() const {return m_dExposure;}
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize); 
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize); 
   int ClearROI();

   // action interface
   int OnBoard(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCCDType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);

 
   /*
   int OnMode(CPropertyBase* pProp, ActionType eAct);
   int OnSubMode(CPropertyBase* pProp, ActionType eAct);
   int OnTrigger(CPropertyBase* pProp, ActionType eAct);
   int OnRoiXMax(CPropertyBase* pProp, ActionType eAct);
   int OnRoiYMax(CPropertyBase* pProp, ActionType eAct);
   int OnRoiYMin(CPropertyBase* pProp, ActionType eAct);
   int OnHBin(CPropertyBase* pProp, ActionType eAct);
   int OnVBin(CPropertyBase* pProp, ActionType eAct);
   int OnCCDType(CPropertyBase* pProp, ActionType eAct);   
   */

private:
   CSensicam();
   int ResizeImageBuffer();

   static CSensicam* m_pInstance;
   ImgBuffer img_;
   int pixelDepth_;
   float pictime_;
   double m_dExposure; 
   bool m_bBusy;
   bool m_bInitialized;

   // sensicam data
   int m_nBoard;
   int m_nCameraType;
   char m_pszTimes[MMSENSICAM_MAX_STRLEN];
   int m_nTimesLen;
   int m_nSubMode;
   int m_nMode;
   int m_nTrig;
   int m_nRoiXMax;
   int m_nRoiXMin;
   int m_nRoiYMax;
   int m_nRoiYMin;
   int m_nHBin;
   int m_nVBin;
   int m_nCCDType;
   int roiXMaxFull_;
   int roiYMaxFull_;
};

#endif //_SENSICAM_H_
