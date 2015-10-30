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


MM::DeviceDetectionStatus ASICheckSerialPort(MM::Device& device, MM::Core& core, std::string port, double ato);

// N.B. Concrete device classes deriving ASIBase must set core_ in
// Initialize().
class ASIBase
{
public:
   ASIBase(MM::Device *device, const char *prefix);
   virtual ~ASIBase();

   int ClearPort(void);
   int CheckDeviceStatus(void);
   int SendCommand(const char *command) const;
   int QueryCommandACK(const char *command);
   int QueryCommand(const char *command, std::string &answer) const;

protected:
   bool oldstage_;
   MM::Core *core_;
   bool initialized_;
   MM::Device *device_;
   std::string oldstagePrefix_;
   std::string port_;
};

class XYStage : public CXYStageBase<XYStage>, public ASIBase
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
  int SetOrigin();
  int Calibrate();
  int Calibrate1();
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  double GetStepSizeXUm() {return stepSizeXUm_;}
  double GetStepSizeYUm() {return stepSizeYUm_;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOverShoot(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWait(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int GetMaxSpeed(char * maxSpeedStr);
   int OnMotorCtrl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCompileDate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBuildName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNrMoveRepetitions(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSMirror(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSSwapXY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJSSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialResponse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommandOnlySendChanged(MM::PropertyBase* pProp, MM::ActionType eAct);
   int GetPositionStepsSingle(char axis, long& steps);
   int SetAxisDirection();
   bool hasCommand(std::string commnand);
   void Wait();
   static std::string EscapeControlCharacters(const std::string v);
   static std::string UnescapeControlCharacters(const std::string v0 );
  
   double stepSizeXUm_;
   double stepSizeYUm_;
   double maxSpeed_;
   // This variable convert the floating point number provided by ASI (expressing 10ths of microns) into a long
   double ASISerialUnit_;
   bool motorOn_;
   int joyStickSpeedFast_;
   int joyStickSpeedSlow_;
   bool joyStickMirror_;
   long nrMoveRepetitions_;
   double answerTimeoutMs_;
   bool stopSignal_;
   bool serialOnlySendChanged_;        // if true the serial command is only sent when it has changed
   std::string manualSerialAnswer_; // last answer received when the SerialCommand property was used
   bool post2010firmware_;      // true if compile date is 2010 or later
};

class ZStage : public CStageBase<ZStage>, public ASIBase
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

  bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFastSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRingBufferSize(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = sequenceable_; return DEVICE_OK;}
   int GetStageSequenceMaxLength(long& nrEvents) const  {nrEvents = nrEvents_; return DEVICE_OK;}
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();

private:
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOverShoot(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWait(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int GetMaxSpeed(char * maxSpeedStr);
   int OnMotorCtrl(MM::PropertyBase* pProp, MM::ActionType eAct);
   bool HasRingBuffer();
   int GetControllerInfo();
   int ExecuteCommand(const std::string& cmd, std::string& response);
   int Autofocus(long param);
   //int GetResolution(double& res);
   bool hasCommand(std::string commnand);

   std::vector<double> sequence_;
   std::string axis_;
   unsigned int axisNr_;
   double stepSizeUm_;
   double answerTimeoutMs_;
   bool sequenceable_;
   bool runningFastSequence_;
   bool hasRingBuffer_;
   long nrEvents_;
   long curSteps_;
   double maxSpeed_;
   bool motorOn_;
};


class CRIF : public CAutoFocusBase<CRIF>, public ASIBase
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
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);

   bool justCalibrated_;
   std::string axis_;
   double stepSizeUm_;
   std::string focusState_;
   long waitAfterLock_;
};


class CRISP : public CAutoFocusBase<CRISP>, public ASIBase
{
public:
   CRISP();
   ~CRISP();

   //MMDevice API
   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   MM::DeviceDetectionStatus DetectDevice(void);

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state);
   virtual int GetContinuousFocusing(bool& state);
   virtual bool IsContinuousFocusLocked();
   virtual int FullFocus();
   virtual int IncrementalFocus();
   virtual int GetLastFocusScore(double& score);
   virtual int GetCurrentFocusScore(double& score);
   virtual int GetOffset(double& offset);
   virtual int SetOffset(double offset);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNA(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLockRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLEDIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNumAvg(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCalGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGainMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocusCurve(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocusCurveData(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnSNR(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDitherError(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLogAmpAGC(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetFocusState(std::string& focusState);
   int SetFocusState(std::string focusState);
   int ForceSetFocusState(std::string focusState);
   int GetValue(std::string cmd, float& val);
   int SetCommand(std::string cmd);

   static const int SIZE_OF_FC_ARRAY = 24;
   std::string focusCurveData_[SIZE_OF_FC_ARRAY];
   std::string axis_;
   long ledIntensity_;
   double na_;
   std::string focusState_;
   long waitAfterLock_;
   int answerTimeoutMs_;
};

class AZ100Turret : public CStateDeviceBase<AZ100Turret>, public ASIBase
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
   MM::MMTime changedTime_;                                                  
   long position_; 
};

class StateDevice : public CStateDeviceBase<StateDevice>, public ASIBase
{
public:
   StateDevice();
   ~StateDevice();

   //MMDevice API
   bool Busy();
   void GetName(char* pszName) const;
   unsigned long GetNumberOfPositions()const {return numPos_;}

   int Initialize();
   int Shutdown();
   MM::DeviceDetectionStatus DetectDevice(void);

   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnNumPositions(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long numPos_;
   std::string axis_;
   long position_;
   double answerTimeoutMs_;

   int UpdateCurrentPosition();
};

class LED : public CShutterBase<LED>, public ASIBase
{
public:
   LED();
   ~LED();

   int Initialize();
   int Shutdown();

   MM::DeviceDetectionStatus DetectDevice(void);

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API                                             
   int SetOpen (bool open = true);                            
   int GetOpen(bool& open);                                   
   int Fire(double deltaT);                                   
                                                              
   // action interface                                        
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int IsOpen(bool* open); // queries the device rather than using a cached value
   int CurrentIntensity(long* intensity); // queries the device rather than using a cached value
   bool open_;
   long intensity_;
   std::string name_;
   int answerTimeoutMs_;
   
};
   


#endif //_ASIStage_H_
