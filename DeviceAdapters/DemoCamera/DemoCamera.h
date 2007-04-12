///////////////////////////////////////////////////////////////////////////////
// FILE:          DemoCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//

#ifndef _DEMOCAMERA_H_
#define _DEMOCAMERA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103


//////////////////////////////////////////////////////////////////////////////
// CDemoCamera class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////
class CDemoCamera : public CCameraBase<CDemoCamera>  
{
public:
   CDemoCamera();
   ~CDemoCamera();
  
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

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   static const int imageSize_=512;

   ImgBuffer img_;
   bool initialized_;
   bool busy_;
   long readoutUs_;
   long readoutStartUs_;

   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();
};


//////////////////////////////////////////////////////////////////////////////
// CDemoFilterWheel class
// Simulation of the filter changer (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoFilterWheel : public CStateDeviceBase<CDemoFilterWheel>
{
public:
   CDemoFilterWheel();
   ~CDemoFilterWheel();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool busy_;
   long numPos_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoLightPath class
// Simulation of the microscope light path switch (state device)
//////////////////////////////////////////////////////////////////////////////
class CDemoLightPath : public CStateDeviceBase<CDemoLightPath>
{
public:
   CDemoLightPath();
   ~CDemoLightPath();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool busy_;
   long numPos_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoObjectiveTurret class
// Simulation of the objective changer (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoObjectiveTurret : public CStateDeviceBase<CDemoObjectiveTurret>
{
public:
   CDemoObjectiveTurret();
   ~CDemoObjectiveTurret();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool busy_;
   long numPos_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CDemoStage : public CStageBase<CDemoStage>
{
public:
   CDemoStage();
   ~CDemoStage();

   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   virtual int SetPositionUm(double pos) {pos_um_ = pos; return DEVICE_OK;}
   virtual int GetPositionUm(double& pos) {pos = pos_um_; return DEVICE_OK;}
   virtual double GetStepSize() {return stepSize_um_;}
   virtual int SetPositionSteps(long steps) {pos_um_ = steps * stepSize_um_; return DEVICE_OK;}
   virtual int GetPositionSteps(long& steps) {steps = (long)(pos_um_ / stepSize_um_); return DEVICE_OK;}
   virtual int SetOrigin() {return DEVICE_OK;}
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double pos_um_;
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CDemoXYStage : public CXYStageBase<CDemoXYStage>
{
public:
   CDemoXYStage();
   ~CDemoXYStage();

   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   virtual int SetPositionUm(double x, double y) {posX_um_ = x; posY_um_ = y; return DEVICE_OK;}
   virtual int GetPositionUm(double& x, double& y) {x = posX_um_; y = posY_um_; return DEVICE_OK;}
   virtual double GetStepSize() {return stepSize_um_;}
   virtual int SetPositionSteps(long x, long y)
   {
      posX_um_ = x * stepSize_um_;
      posY_um_ = y * stepSize_um_;
      return DEVICE_OK;
   }
   virtual int GetPositionSteps(long& x, long& y)
   {
      x = (long)(posX_um_ / stepSize_um_);
      y = (long)(posY_um_ / stepSize_um_);
      return DEVICE_OK;
   }
   virtual int Home() {return DEVICE_OK;}
   virtual int Stop() {return DEVICE_OK;}
   virtual int SetOrigin() {return DEVICE_OK;}//jizhen 4/12/2007
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   virtual int GetLimits(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimit_; xMax = upperLimit_;
      yMin = lowerLimit_; yMax = upperLimit_;
      return DEVICE_OK;
   }

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double posX_um_;
   double posY_um_;
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

#endif //_DEMOCAMERA_H_
