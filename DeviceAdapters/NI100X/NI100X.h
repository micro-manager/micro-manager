///////////////////////////////////////////////////////////////////////////////
// FILE:          NI100X.h
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   NI control with multiple devices
//                
// AUTHOR:        Nenad Amodaj, January 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010
//                

#pragma once

#include "DeviceBase.h"
#include "NIDAQmx.h"
#include <string>
#include <map>
#include <set>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_CHANNEL 411
#define ERR_HARDWARE_NOT_INITIALIZED 412
#define ERR_UNKNOWN_POSITION 413
#define ERR_INITIALIZE_FAILED 414
#define ERR_WRITE_FAILED 415
#define ERR_CLOSE_FAILED 416
#define ERR_BOARD_NOT_FOUND 417
#define ERR_DEPTH_IDX_OUT_OF_RANGE 418
#define ERR_WRONG_DEPTH_LIST 419
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_OFFSET 10100

//////////////////////////////////////////////////////////////////////////////
// SignalIO class
// Analog output
//////////////////////////////////////////////////////////////////////////////

class AnalogIO : public CSignalIOBase<AnalogIO>  
{
public:
   AnalogIO();
   ~AnalogIO();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy() {return false;}
  
   // SignalIO api
   // ------------
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& /*volts*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = minV_; maxVolts = maxV_; return DEVICE_OK;}
   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   int GetDASequenceMaxLength(long& nrEvents) const {return 0;}
   int StartDASequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
   int StopDASequence() const {return DEVICE_OK;}
   int LoadDASequence(std::vector<double> voltages) const {return DEVICE_UNSUPPORTED_COMMAND;}
   int ClearDASequence(){return DEVICE_UNSUPPORTED_COMMAND;}
   int AddToDASequence(double voltage) {return DEVICE_UNSUPPORTED_COMMAND;}
   int SendDASequence() const {return DEVICE_UNSUPPORTED_COMMAND;}

   // action interface
   // ----------------
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPercent(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnZ(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVD(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDepthListSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDemo(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPropertyListIdx(MM::PropertyBase* pProp, MM::ActionType eAct);

   // specific 2-photon related interface
   int ApplyDepthControl(double z, int list);
   int ApplyDepthControl(double z);

private:
   bool initialized_;
   bool busy_;
   double minV_;
   double maxV_;
   double volts_;
   double gatedVolts_;
   unsigned int encoding_;
   unsigned int resolution_;
   std::string channel_;
   bool gateOpen_;
   TaskHandle task_;
   int ApplyVoltage(double v);
   double GetInterpolatedV(double z, int listIdx);
   long GetListIndex();

   struct DepthList
   {
      std::vector<double> zList_;
      std::vector<double> vList_;
   };
   std::vector<DepthList> lists_;
   int depthIdx_;
   bool demo_;
};

class DigitalIO : public CStateDeviceBase<DigitalIO>  
{
public:
   DigitalIO();
   ~DigitalIO();
  
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
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:

   bool initialized_;
   bool busy_;
   long numPos_;
   std::string channel_;
   TaskHandle task_;
   int state_;
};

class PIZStage : public CStageBase<PIZStage>
{
public:
   PIZStage();
   ~PIZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);
  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  int GetStageSequenceMaxLength(long& nrEvents) const {nrEvents = 0; return DEVICE_OK;}
  int StartStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  int StopStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  int LoadStageSequence(std::vector<double> positions) const {return DEVICE_UNSUPPORTED_COMMAND;}
  int ClearStageSequence() {return DEVICE_UNSUPPORTED_COMMAND;}
  int AddToStageSequence(double position) {return DEVICE_UNSUPPORTED_COMMAND;}
  int SendStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInterface(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnDepthControl(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   bool GetValue(std::string& sMessage, double& pos);
   bool waitForResponse();
   double getAxisLimit();

   std::string port_;
   std::string axisName_;
   double stepSizeUm_;
   bool initialized_;
   long curSteps_;
   double answerTimeoutMs_;
   double pos_;

};

class PIGCSZStage : public CStageBase<PIGCSZStage>
{
public:
   PIGCSZStage();
   ~PIGCSZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);
  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  int GetStageSequenceMaxLength(long& nrEvents) const {nrEvents = 0; return DEVICE_OK;}
  int StartStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  int StopStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  int LoadStageSequence(std::vector<double> positions) const {return DEVICE_UNSUPPORTED_COMMAND;}
  int ClearStageSequence() {return DEVICE_UNSUPPORTED_COMMAND;}
  int AddToStageSequence(double position) {return DEVICE_UNSUPPORTED_COMMAND;}
  int SendStageSequence() const {return DEVICE_UNSUPPORTED_COMMAND;}
  bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDepthControl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDepthList(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);
   bool GetValue(std::string& sMessage, double& dval);
   bool GetValue(std::string& sMessage, long& lval);
   bool ExtractValue(std::string& sMessage);
   int GetError();
   bool waitForResponse();
   void ApplyDepthControl(double pos);

   std::string port_;
   std::string axisName_;
   bool checkIsMoving_;
   double stepSizeUm_;
   bool initialized_;
   long curSteps_;
   double answerTimeoutMs_;
   double axisLimitUm_;
   int depthList_;
   double posUm_;
};
