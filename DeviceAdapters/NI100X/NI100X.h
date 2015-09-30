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
#include <boost/lexical_cast.hpp>


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

// Generic DAQmx device. This unifies logic that is shared
// across the other classes in this file.
class DAQDevice
{
public:
   DAQDevice();
   virtual ~DAQDevice() {};

   void SetContext(MM::Core* core, MM::Device* device);
   std::vector<std::string> GetDevices();
   std::vector<std::string> GetDigitalPortsForDevice(std::string device);
   std::vector<std::string> GetAnalogPortsForDevice(std::string device);
   std::string GetPort(std::string line);

   int OnTriggeringEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSampleRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSupportsTriggering(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInputTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct);
   virtual int TestTriggering() = 0;

protected:
   void SetDeviceName();
   int CreateTriggerProperties();
   int SetupTask();
   void CancelTask();
   int SetupClockInput(int numVals);
   int LogError(int error, const char* func);
   std::string GetNextEntry(std::string line, size_t& index);

   TaskHandle task_;
   // Name of card (e.g. in NI-MAX).
   std::string deviceName_;
   // Output line or lines to use.
   std::string channel_;
   // Auto-detected output port to use.
   std::string port_;
   // Input line to listen on for hardware triggers.
   std::string inputTrigger_;
   // Frequency at which to sample the input trigger line.
   long samplesPerSec_;
   // The user has allowed triggering to happen.
   bool isTriggeringEnabled_;
   // Our current configuration is physically capable of
   // triggering.
   bool supportsTriggering_;
   // Maximum allowed sequence to load onto card.
   long maxSequenceLength_;
   // Indicates if we have loaded a sequence onto the card and
   // am waiting for trigger inputs.
   bool amPreparedToTrigger_;

private:
   MM::Core* core_;
   MM::Device* device_;
};

//////////////////////////////////////////////////////////////////////////////
// SignalIO class
// Analog output
//////////////////////////////////////////////////////////////////////////////

class AnalogIO : public CSignalIOBase<AnalogIO>, public DAQDevice
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
   int IsDASequenceable(bool& isSequenceable) const
   {
	   isSequenceable = supportsTriggering_ && isTriggeringEnabled_;
	   return DEVICE_OK;
   }
   int GetDASequenceMaxLength(long& maxLength) const
   {
	   maxLength = maxSequenceLength_;
	   return DEVICE_OK;
   }
   int StartDASequence();
   int StopDASequence();
   int ClearDASequence();
   int AddToDASequence(double);
   int SendDASequence();

   // Inherited from DAQDevice
   int TestTriggering();


   // action interface
   // ----------------
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
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
   bool gateOpen_;

   std::vector<double> sequence_;

   int LoadBuffer();
   int ApplyVoltage(double v);
   long GetListIndex();

   bool demo_;
};

class DigitalIO : public CStateDeviceBase<DigitalIO>, public DAQDevice
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
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequenceLength(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   void AddPorts(std::string deviceName);
   void AddPort(std::string line);
   int SetupDigitalTriggering(uInt32* sequence, long numVals);
   int TestTriggering();
   int LoadBuffer(uInt32* sequence, long numVals);

   bool initialized_;
   bool busy_;
   long numPos_;
   bool open_;
   int state_;
};
