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
#define ERR_OPEN_DEVICE_FAILED 200


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
      int handleDAQError(int ret, TaskHandle taskHandle);
      int WriteToDevice(long lnValue);
      int initializeDevice();

      TaskHandle taskHandle_;
      MM::MMTime changedTime_;
      long state_;
      std::string name_;
      std::string deviceName_;
      bool inverted_;
      bool open_;
      bool initialized_;
};

#endif // _NIDAQ_H_
