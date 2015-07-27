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
   int GetDASequenceMaxLength(long&) const {return 0;}
   int StartDASequence() {return DEVICE_UNSUPPORTED_COMMAND;}
   int StopDASequence() {return DEVICE_OK;}
   int LoadDASequence(std::vector<double>) const {return DEVICE_UNSUPPORTED_COMMAND;}
   int ClearDASequence(){return DEVICE_UNSUPPORTED_COMMAND;}
   int AddToDASequence(double) {return DEVICE_UNSUPPORTED_COMMAND;}
   int SendDASequence() {return DEVICE_UNSUPPORTED_COMMAND;}

   // action interface
   // ----------------
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPercent(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDisable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVD(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDemo(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool initialized_;
   bool busy_;
   bool disable_;
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
   long GetListIndex();

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
   int OnTriggeringEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInputTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   static const int SAMPLES_PER_SEC = 1000;
   std::string getNextEntry(std::string line, size_t& index);
   void addPorts(std::string deviceName);
   void addPort(std::string line);
   int setupTask();
   void cancelTask();
   int testTriggering();
   int setupTriggering(uInt32* sequence, long numVals);
   int logError(int error, const char* func);

   bool initialized_;
   bool busy_;
   long numPos_;
   TaskHandle task_;
   std::string deviceName_;
   std::string channel_;
   std::string inputTrigger_;
   bool supportsTriggering_;
   bool isTriggeringEnabled_;
   long maxSequenceLength_;
   bool open_;
   int state_;
};
