///////////////////////////////////////////////////////////////////////////////
// FILE:          TIScamera.h
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
#ifndef _TIS_CAMERA_H_
#define _TIS_CAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <map>


// error codes
#define ERR_BUFFER_ALLOCATION_FAILED     1001
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE  1002
#define ERR_BUSY_ACQUIRING               1003
#define ERR_INTERNAL_BUFFER_FULL         1004
#define ERR_NO_CAMERA_FOUND              1005


// forward declaration
class AcqSequenceThread;



//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class CTIScamera : public CCameraBase<CTIScamera>
{
public:

   friend class AcqSequenceThread;

	// the only public ctor
   CTIScamera();
   ~CTIScamera();
   
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy();
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned int* GetImageBufferAsRGB32();
   unsigned GetNumberOfComponents() const;
   int GetComponentName(unsigned channel, char* name);
   long GetImageBufferSize() const;
   unsigned GetImageWidth()  const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);
   void SetExposure(double exp_ms);
   double GetExposure() const;
   int SetROI(unsigned  x, unsigned  y, unsigned  xSize, unsigned  ySize);
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
   int ClearROI();

   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
//fde   int StartSequenceAcquisition(double interval_ms);
   int StopSequenceAcquisition();
   int RestartSequenceAcquisition();
   int PrepareSequenceAcqusition();
   bool IsCapturing();

   // action interface
   // ----------------
   int OnBinning     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBrightness  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);

/*
   int OnCamera      (MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnGain        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnContrast    (MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnGammaMode   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma       (MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnRedGain     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlueGain    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGreenGain   (MM::PropertyBase* pProp, MM::ActionType eAct);
*/

   // custom interface for the burst thread
   int PushImage();


private:
   bool initialized_;
   double nominalPixelSizeUm_;
   long lCCD_Width, lCCD_Height;
   int iCCD_BitsPerPixel;
   unsigned int numberOfChannels_;
   bool busy_;
   bool bColor_;
   bool sequenceRunning_;
   double dExp_;
   bool flipH_;
   bool flipV_;
   double FPS_;
   long Brightness_;

   ImgBuffer img_;

   int ResizeImageBuffer();

   int bitDepth_;

   unsigned roiX_;
   unsigned roiY_;
   unsigned roiXSize_;
   unsigned roiYSize_;

   bool acquiring_;
   double interval_ms_;
   long frameCount_;
   long lastImage_;
   unsigned long imageCounter_;
   unsigned long sequenceLength_;

   AcqSequenceThread* seqThread_; // burst mode thread

};

/*
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
   public:
      AcqSequenceThread(CTIScamera* camera) : 
         intervalMs_(100.0), numImages_(1), busy_(false), stop_(false), camera_(camera) {}
      ~AcqSequenceThread() {}
      int svc (void);

      void SetInterval(double intervalMs) {intervalMs_ = intervalMs;}
      void SetLength(long images) {numImages_ = images;}
      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

   private:
      double intervalMs_;
      long numImages_;
      bool busy_;
      bool stop_;
      CTIScamera* camera_;
};



#endif //_TIS_CAMERA_H_
