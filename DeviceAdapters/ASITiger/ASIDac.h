///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIDac.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI DAC
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
// AUTHOR:        Vikram Kopuri (vik@asiimaging.com) 08/2019
//
// BASED ON:      ASIStage.h and others
//

#ifndef _ASIDAC_H_
#define _ASIDAC_H_

#include "ASIPeripheralBase.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"


class CDAC : public ASIPeripheralBase<CSignalIOBase, CDAC>
{
public:
	CDAC(const char* name);
	~CDAC() { }

	// Device API
	int Initialize();
	bool Busy() { return false; };

	// DAC API
	int SetGateOpen(bool open);
	int GetGateOpen(bool& open);
	int SetSignal(double volts);
	int GetSignal(double& volts);
	int GetLimits(double& minVolts, double& maxVolts);

	// Sequence commands ie Ring Buffer needs TTL 
	
	int IsDASequenceable(bool& isSequenceable) const { isSequenceable = ttl_trigger_enabled_; return DEVICE_OK; }
	int GetDASequenceMaxLength(long &nrEvents) const { nrEvents = ring_buffer_capacity_; return DEVICE_OK; }
	int StartDASequence();
	int StopDASequence();
	int ClearDASequence();
	int AddToDASequence(double voltage);
	int SendDASequence();

	// action interface
	int OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct);

	//Signal DAC specific properties
	int OnDACMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDACVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDACGate(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCutoffFreq(MM::PropertyBase* pProp, MM::ActionType eAct);

	// single axis properties
	int OnSAAmplitude(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAPeriod(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAAdvanced(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAClkSrc(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAClkPol(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSATTLOut(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSATTLPol(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSAPatternByte(MM::PropertyBase* pProp, MM::ActionType eAct);

	// ring buffer properties
	int OnRBDelayBetweenPoints(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRBMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRBTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRBRunning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnUseSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnRBSequenceState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAddtoRBSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	//Others
	int OnTTLin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTTLout(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	double unitMult_;
	string axisLetter_;
	double maxvolts_;
	double minvolts_;
	bool ring_buffer_supported_;
	long ring_buffer_capacity_;
	bool ttl_trigger_supported_;
	bool ttl_trigger_enabled_;
	std::vector<double> sequence_; // carries data in volts

	int GetMaxVolts(double &volts);
	int GetMinVolts(double &volts);
	int SetSignalmv(double millivolts);
	int GetSignalmv(double& millivolts);

	

};



#endif//_ASIDAC_H_