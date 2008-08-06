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
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetAllowedBinning();
   static const int imageSize_=512;
   static const double nominalPixelSizeUm_;

   ImgBuffer img_;
   bool initialized_;
   bool busy_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   long scanMode_;

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
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   MM::MMTime changedTime_;
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
   long numPos_;
   bool busy_;
   bool initialized_;
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
   long numPos_;
   bool busy_;
   bool initialized_;
   long position_;
};

//////////////////////////////////////////////////////////////////////////////
// CDemoStateDevice class
// Simulation of a state device in which the number of states can be specified (state device)
//////////////////////////////////////////////////////////////////////////////

class CDemoStateDevice : public CStateDeviceBase<CDemoStateDevice>
{
public:
   CDemoStateDevice();
   ~CDemoStateDevice();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNumberOfStates(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   bool busy_;
   bool initialized_;
   MM::MMTime changedTime_;
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
   double stepSize_um_;
   double pos_um_;
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
   virtual int SetAdapterOriginUm(double x, double y) {posX_um_ = x; posY_um_ = y; return DEVICE_OK;}
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimit_; xMax = upperLimit_;
      yMin = lowerLimit_; yMax = upperLimit_;
      return DEVICE_OK;
   }

   virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   double GetStepSizeXUm() {return stepSize_um_;}
   double GetStepSizeYUm() {return stepSize_um_;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_um_;
   double posX_um_;
   double posY_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoAutoFocus class
// Simulation of the auto-focusing module
//////////////////////////////////////////////////////////////////////////////
class DemoAutoFocus : public CAutoFocusBase<DemoAutoFocus>
{
public:
   DemoAutoFocus() : 
      running_(false), 
      busy_(false), 
      initialized_(false)  
   {}

   ~DemoAutoFocus() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state) {running_ = state; return DEVICE_OK;}
   virtual int GetContinuousFocusing(bool& state) {state = running_; return DEVICE_OK;}
   virtual bool IsContinuousFocusLocked() {return running_;}
   virtual int Focus() {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual int GetFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}

private:
   bool running_;
   bool busy_;
   bool initialized_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoShutter class
// Simulation of shutter device
//////////////////////////////////////////////////////////////////////////////
class DemoShutter : public CShutterBase<DemoShutter>
{
public:
   DemoShutter() : state_(false), initialized_(false), changedTime_(0.0) {
      EnableDelay(); // signals that the dealy setting will be used
   }
   ~DemoShutter() {}

   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true) {state_ = open; changedTime_ = GetCurrentMMTime(); return DEVICE_OK;}
   int GetOpen(bool& open) {open = state_; return DEVICE_OK;}
   int Fire(double /*deltaT*/) {return DEVICE_UNSUPPORTED_COMMAND;}

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool state_;
   bool initialized_;
   MM::MMTime changedTime_;
};

//////////////////////////////////////////////////////////////////////////////
// DemoShutter class
// Simulation of shutter device
//////////////////////////////////////////////////////////////////////////////
class DemoDA : public CSignalIOBase<DemoDA>
{
public:
   DemoDA () : volt_(0) {}
   ~DemoDA () {}

   int Shutdown() {return DEVICE_OK;}
   void GetName(char* name) const {name = "Demo DA";}
   int SetSignal(double volts) {volt_ = volts; return DEVICE_OK;}
   int GetSignal(double& volts) {volts = volt_; return DEVICE_OK;}
   int GetLimits(double& minVolts, double& maxVolts) {minVolts=0.0; maxVolts= 0.0; return DEVICE_OK;}
   bool Busy() {return false;}
   int Initialize() {return DEVICE_OK;}


private:
   double volt_;
};


#endif //_DEMOCAMERA_H_
