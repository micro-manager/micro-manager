///////////////////////////////////////////////////////////////////////////////
// FILE:          ASILED.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI LED shutter device adapter
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 05/2014
//
// BASED ON:      ASIStage.h and others
//

#ifndef _ASILED_H_
#define _ASILED_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class CLED : public ASIPeripheralBase<CShutterBase, CLED>
{
public:
   CLED(const char* name);
   ~CLED() { }
  
   // Device API
   // ----------
   int Initialize();
   bool Busy() { return false; }

   // Shutter API
   // -----------
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double /*deltaT*/) { return DEVICE_UNSUPPORTED_COMMAND;  }

   // action interface
   // ----------------
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState                (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCurrentLimit         (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool open_;       // true when LED turned on
   int intensity_;   // intensity from 1 to 100 (controller reports 0 intensity if off, we use that to set open_)
   int channel_;     // 0 for LED on 2-axis card, 1-4 for TGLED card
   char channelAxisChar_;
   int UpdateOpenIntensity();
};

#endif //_ASILED_H_
