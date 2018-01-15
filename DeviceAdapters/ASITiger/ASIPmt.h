///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIPmt.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI PMT device adapter
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Vikram Kopuri (vik@asiimaging.com) 04/2016
//
// BASED ON:      ASIStage.h and others
//

#ifndef _ASIPMT_H_
#define _ASIPMT_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"


class CPMT : public ASIPeripheralBase<CSignalIOBase, CPMT>
{
public:
   CPMT(const char* name);
   ~CPMT() { }

   // Device API
   int Initialize();
   bool Busy(){return false;};
   
   // ADC API
   int SetGateOpen(bool open); //reset overload
   int GetGateOpen(bool& open); //overload
   int SetSignal(double /*volts*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = -1024; maxVolts = 1024; return DEVICE_OK;};

   // Sequence commands are not supported on this device
   // DeviceBase parent class handles returning DEVICE_UNSUPPORTED_COMMAND for everything except IsDASequenceable
   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain                 (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAverage              (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPMTSignal			  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPMTOverload    	  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOverloadReset        (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int channel_; 
   char channelAxisChar_;
   string axisLetter_;
   int gain_;
   int avg_length_;
   int UpdateGain();
   int UpdateAvg();
 };

#endif //_ASIPMT_H_
