///////////////////////////////////////////////////////////////////////////////
// FILE:          OpticalSectioningUtility.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Combines an SLM and a Camera to produce optical sections.
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 4/6/2010
// COPYRIGHT:     University of California, San Francisco, 2010
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

#ifndef _OPTICALSECTIONINGUTILITY_H_
#define _OPTICALSECTIONINGUTILITY_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#define ERR_INVALID_DEVICE_NAME            10001

class OpticalSectioningUtility : public CCameraBase<OpticalSectioningUtility> 
{
public:
   OpticalSectioningUtility();
   ~OpticalSectioningUtility();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long GetImageBufferSize() const;
   double GetExposure() const;
   void SetExposure(double exp);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
   int ClearROI();
   double GetPixelSizeUm() const;
   int GetBinning() const;
   int SetBinning(int bS);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

private:
   int OnPhysicalCamera(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSLM(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStripeWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int SetupSLMImages();
   void acquireOneFrame(int frameIndex, unsigned short * rawpix, long numPixels);

   MM::Camera * physicalCamera_;
   MM::SLM * slm_;
   std::string physicalCameraName_;
   std::string slmName_;

   std::vector<unsigned char *> slmImages_;
   unsigned short * outPixels_;
   long numPixels_;
   long lambda_;
};


#endif // _OPTICALSECTIONINGUTILITY_H_