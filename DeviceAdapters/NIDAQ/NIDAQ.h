///////////////////////////////////////////////////////////////////////////////
// FILE:          NIDAQ.h
// PROJECT:       Micro-Manager
// SUBSYTEM:      DevcieAdapters
//
// DESCRIPTION:   Adapter for National Instruments IO devices
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 10/04/2008
//
//

#ifndef _NIDAQ_H_
#define _NIDAQ_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../../3rdParty/trunk/NationalInstruments/NI-DAQmxBase/includes/NIDAQmxBase.h"

////////////////////////////////////////////////
// Error codes
#define ERR_OPEN_DEVICE_FAILED      200
#define ERR_INVALID_DIGITAL_PATTERN 201
#define ERR_INVALID_REPEAT_NR       202
#define ERR_DP_NOT_INITIALIZED      203


class NIDAQDO : public CShutterBase<NIDAQDO>
{
   public:
      NIDAQDO();
      ~NIDAQDO();

      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();

      // Shutter API
      int SetOpen(bool open = true);
      int GetOpen(bool& open) {open = open_; return DEVICE_OK;}
      int Fire(double deltaT) {return DEVICE_UNSUPPORTED_COMMAND;}

      // Action Interface
      int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnLogic(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      int HandleDAQError(int ret, TaskHandle taskHandle);
      int WriteToDevice(long lnValue);
      int InitializeDevice();

      TaskHandle taskHandle_;
      MM::MMTime changedTime_;
      long state_;
      std::string name_;
      std::string deviceName_;
      bool inverted_;
      bool open_;
      bool initialized_;
};

class NIDAQAO : public CSignalIOBase<NIDAQAO>
{
   public:
      NIDAQAO();
      ~NIDAQAO();

      // MMDevice API
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();

      // SIgnal IO API
      int SetGateOpen(bool open);
      int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;}
      int SetSignal(double volts);
      int GetSignal (double& volts) {volts = volts_; return DEVICE_OK;}
      int GetLimits (double& minVolts, double& maxVolts) {minVolts = minVolt_; maxVolts = maxVolt_; return DEVICE_OK;}

      // Action Interface
      int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnVolt(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      int HandleDAQError(int ret, TaskHandle taskHandle);
      int WriteToDevice(double volt);
      int InitializeDevice();

      TaskHandle taskHandle_;
      MM::MMTime changedTime_;
      std::string deviceName_;
      bool initialized_;
      bool busy_;
      double volts_;
      double minVolt_;
      double maxVolt_;
      bool gateOpen_;
};

class NIDAQDPattern : public CGenericBase<NIDAQDPattern>
{
   public:
      NIDAQDPattern();
      ~NIDAQDPattern();

      // MMDevice API
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();

      // Action Interface
      int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnExternalClockPort(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnEdge(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnDigitalPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnRepeat(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      int HandleDAQError(int ret, TaskHandle taskHandle);
      int InitializeDevice();

      std::string digitalPattern_;
      std::string edge_;
      std::string status_;
      int nrRepeats_;
      int samples_;
      static const int maxNrSamples_ = 8;
      uInt32 data_[maxNrSamples_];

      TaskHandle taskHandle_;
      MM::MMTime changedTime_;
      std::string externalClockPort_;
      std::string deviceName_;
      bool initialized_;
};


#endif // _NIDAQ_H_
