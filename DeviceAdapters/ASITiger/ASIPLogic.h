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

class CPLogic : public ASIPeripheralBase<CShutterBase, CPLogic>
{
public:
   CPLogic(const char* name);
   ~CPLogic() { }
  
   // Device API
   // ----------
   int Initialize();
   bool Busy() { return false; }

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double /*deltaT*/) { return DEVICE_UNSUPPORTED_COMMAND; }

   // action interface
   // ----------------
   int OnPLogicMode           (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetShutterChannel    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPLogicOutputState    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrontpanelOutputState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBackplaneOutputState (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerSource        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClearAllCellStates   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetCardPreset        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPointerPosition      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellType         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellConfig       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellInput1       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellInput2       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellInput3       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellInput4       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEditCellUpdates      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAdvancedProperties   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCellType             (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnCellConfig           (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInput1               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInput2               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInput3               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnInput4               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnIOType               (MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnIOSourceAddress      (MM::PropertyBase* pProp, MM::ActionType eAct, long index);


private:
   string axisLetter_;
   unsigned int numCells_;
   unsigned int currentPosition_;  // cached value of current position
//   static const int NUM_CELLS = 16;
   bool useAsdiSPIMShutter_;
   bool shutterOpen_;
   bool advancedPropsEnabled_;
   bool editCellUpdates_;

   int SetShutterChannel();
   int SetPositionDirectly(unsigned int position);
   int GetCellPropertyName(long index, string suffix, char* name);
   int GetIOPropertyName(long index, string suffix, char* name);
   int RefreshAdvancedCellPropertyValues(long index);
   int RefreshCurrentPosition();
   int RefreshEditCellPropertyValues();
};

#endif //_ASIPLOGIC_H_
