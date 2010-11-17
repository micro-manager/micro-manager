///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI ProScan controller adapter
//
// COPYRIGHT:     Andor Technology PLC,
//                University of California, San Francisco
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jizhen Zhao (j.zhao@andor.com) based on code by Nenad Amodaj, April 2007, modified by Nico Stuurman, 12/2007
//

#ifndef _ASIStage_H_
#define _ASIStage_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

//#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_NOT_LOCKED               10011
#define ERR_NOT_CALIBRATED           10012

#define ERR_OFFSET 10100

//#define ERR_UNKNOWN_COMMAND "Unknown Command"
#define ERR_UNRECOGNIZED_AXIS_PARAMETERS "Unrecognized Axis Parameters"
#define ERR_MISSING_PARAMETERS "Missing Parameters"
#define ERR_PARAMETER_OUTOF_RANGE "Parameter Out of Range"
#define ERR_UNDEFINED ERROR "Undefined Error"
// eof from Prior


int ClearPort(MM::Device& device, MM::Core& core, std::string port);

MM::DeviceDetectionStatus ASICheckSerialPort(MM::Device& device, MM::Core& core, std::string port, double ato);

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // so far, only the XYStage attempts to get the controller status on initialization, so
   // that's where the device detection is going for now
   MM::DeviceDetectionStatus DetectDevice(void);

   // XYStage API
   // -----------
  int SetPositionSteps(long x, long y);
  int SetRelativePositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int Home();
  int Stop();
  //bool XyIsBusy();//jizhen
  int SetOrigin();
  int Calibrate();
  int Calibrate1();
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  double GetStepSizeXUm() {return stepSizeXUm_;}
  double GetStepSizeYUm() {return stepSizeYUm_;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOverShoot(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWait(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMotorCtrl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNrMoveRepetitions(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSMirror(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSSwapXY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int GetPositionStepsSingle(char axis, long& steps);
   int SetAxisDirection();
   bool hasCommand(std::string commnand);
   void Wait();
  
   bool initialized_;
   std::string port_;
   double stepSizeXUm_;
   double stepSizeYUm_;
   // This variable convert the floating point number provided by ASI (expressing 10ths of microns) into a long
   double ASISerialUnit_;
   bool motorOn_;
   long nrMoveRepetitions_;
   int joyStickSpeedFast_;
   int joyStickSpeedSlow_;
   bool joyStickMirror_;
   bool joyStickSwapXY_;
   double answerTimeoutMs_;
   bool stopSignal_;
};

class ZStage : public CStageBase<ZStage>
{
public:
   ZStage();
   ~ZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   MM::DeviceDetectionStatus DetectDevice(void);

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int Calibrate();
  int GetLimits(double& min, double& max);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   int Autofocus(long param);
   //int GetResolution(double& res);

   bool initialized_;
   std::string port_;
   std::string axis_;
   double stepSizeUm_;
   double answerTimeoutMs_;
   long curSteps_;
};


class CRIF : public CAutoFocusBase<CRIF>
{
public:
   CRIF();
   ~CRIF();

   //MMDevice API
   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state);
   virtual int GetContinuousFocusing(bool& state);
   virtual bool IsContinuousFocusLocked();
   virtual int FullFocus();
   virtual int IncrementalFocus();
   virtual int GetLastFocusScore(double& score);
   virtual int GetCurrentFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual int GetOffset(double& offset);
   virtual int SetOffset(double offset);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetFocusState(std::string& focusState);
   int SetFocusState(std::string focusState);
   int WaitForAcknowledgement();
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);

   bool initialized_;
   bool justCalibrated_;
   std::string port_;
   double stepSizeUm_;
   std::string focusState_;
   long waitAfterLock_;
   std::string axis_;
};


class AZ100Turret : public CStateDeviceBase<AZ100Turret>
{
public:
   AZ100Turret();
   ~AZ100Turret();

   //MMDevice API
   bool Busy();
   void GetName(char* pszName) const;
   unsigned long GetNumberOfPositions()const {return numPos_;}

   int Initialize();
   int Shutdown();

   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;                                                             
   bool busy_;                                                               
   bool initialized_;                                                        
   std::string port_;
   MM::MMTime changedTime_;                                                  
   long position_; 
};
#endif //_ASIStage_H_
