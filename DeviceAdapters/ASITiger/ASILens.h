///////////////////////////////////////////////////////////////////////////////
// FILE:          ASILens.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI Tunable Lens Device Adapter
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//				  Modified for Tunable lens by Vik (vik@asiimaging.com)	05/2017
//					
//
// BASED ON:      ASIStage.h and others
//

#ifndef _ASILens_H_
#define _ASILens_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class CLens : public ASIPeripheralBase<CStageBase, CLens>
{
public:
   CLens(const char* name);
   ~CLens() { }
  
   // Device API
   // ----------
   int Initialize();
   bool Busy();

   // Lens API
   // -----------
   int Stop();
   int Home();

   // the step size is the programming unit for dimensions and is integer
   // see http://micro-manager.3463995.n2.nabble.com/what-are-quot-steps-quot-for-stages-td7580724.html
   double GetStepSize() {return stepSizeUm_;}
   int GetPositionSteps(long& steps);
   int SetPositionSteps(long steps);
   int SetPositionUm(double pos);
   int GetPositionUm(double& pos);
   int SetRelativePositionUm(double d);
   int GetLimits(double& min, double& max);
   int SetOrigin();

   bool IsContinuousFocusDrive() const { return false; }  // todo figure out what this means and if it's accurate

   int IsStageSequenceable(bool& isSequenceable) const { isSequenceable = ttl_trigger_enabled_; return DEVICE_OK; }
   int GetStageSequenceMaxLength(long& nrEvents) const { nrEvents = ring_buffer_capacity_; return DEVICE_OK; }

   // special firmware required for sequence (TTL triggering)
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();

   // action interface
   // ----------------
   int OnSaveCardSettings     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRefreshProperties    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowerLim             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUpperLim             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLensMode            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickFastSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickSlowSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickMirror       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnJoystickSelect       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelFastSpeed       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelSlowSpeed       (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWheelMirror          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMotorControl         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisPolarity         (MM::PropertyBase* pProp, MM::ActionType eAct);
   // single axis properties
   int OnSAAmplitude          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAOffset             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPeriod             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAMode               (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPattern            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAAdvanced           (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAClkSrc             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAClkPol             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLOut             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSATTLPol             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSAPatternByte        (MM::PropertyBase* pProp, MM::ActionType eAct);
   // ring buffer properties
   int OnRBDelayBetweenPoints (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBMode               (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBTrigger            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRBRunning            (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUseSequence          (MM::PropertyBase* pProp, MM::ActionType eAct);
   //Others
   int OnVector				  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTTLin				  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTTLout			      (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double unitMult_;
   double stepSizeUm_;
   string axisLetter_;
   bool ring_buffer_supported_;
   long ring_buffer_capacity_;
   bool ttl_trigger_supported_;
   bool ttl_trigger_enabled_;
   std::vector<double> sequence_;

   // private helper functions
   int OnSaveJoystickSettings();
};

#endif //_ASIPiezo_H_
