///////////////////////////////////////////////////////////////////////////////
// FILE:          ASILED.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI programmable logic card device adapter
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

#ifndef _ASIPLOGIC_H_
#define _ASIPLOGIC_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class CPLogic : public ASIPeripheralBase<CGenericBase, CPLogic>
{
public:
   CPLogic(const char* name);
   ~CPLogic() { Shutdown(); }
  
   // Device API
   // ----------
   int Initialize();
   bool Busy() { return false; }

   // action interface
   // ----------------
   int OnPLogicOutputState    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrontpanelOutputState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBackplaneOutputState (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerSource        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClearCellState       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetCardPreset        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPointerPosition      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAdvancedProperties   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCellType             (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnCellConfig           (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInputX               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInputY               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInputZ               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInputF               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnIOType               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnIOSourceAddress      (MM::PropertyBase* pProp, MM::ActionType eAct, long index);


private:
   string axisLetter_;
   unsigned int numCells_;
   unsigned int currentPosition_;  // cached value of current position
//   static const int NUM_CELLS = 16;

   int SetPosition(unsigned int position);
   int GetCellPropertyName(long index, string suffix, char* name);
   int GetIOPropertyName(long index, string suffix, char* name);
   int RefreshCellPropertyValues(long index);
   int RefreshCurrentPosition();
};

#endif //_ASIPLOGIC_H_
