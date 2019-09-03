///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIDac.c
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI Signal DAC adapter
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
// BASED ON:      ASILED.c, ASIPmt.c and others
//


#include "ASIDac.h"
#include "ASITiger.h"
#include "ASIHub.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"
#include <iostream>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CDAC
//
CDAC::CDAC(const char* name) :
	ASIPeripheralBase< ::CSignalIOBase, CDAC >(name),
	unitMult_(g_DACDefaultUnitMult),  // later will try to read actual setting
	axisLetter_(g_EmptyAxisLetterStr)  // value determined by extended name
{
	if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
	{
		axisLetter_ = GetAxisLetterFromExtName(name);
		CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
	}
}//end of CDAC constructor

int CDAC::Initialize()
{
	// call generic Initialize first, this gets hub
	RETURN_ON_MM_ERROR(PeripheralInitialize());

	ostringstream command;
	command.str("");
	CPropertyAction* pAct;
	double tmp;

	// create MM description; this doesn't work during hardware configuration wizard but will work afterwards
	command.str("");
	command << g_DacDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
	CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

	// read the unit multiplier
	// ASI's unit multiplier is how many units per volt, so divide by 1 here to get units per volt, divide by 1000 to get units in millivolts
	// except for GetSignal() and SetSignal() which deals in Volts, we will make all properties units millivolt
	command.str("");
	command << "UM " << axisLetter_ << "? ";
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":"));
	RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
	unitMult_ = tmp / 1000;
	command.str("");

	// refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
	pAct = new CPropertyAction(this, &CDAC::OnRefreshProperties);
	CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
	AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
	AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

	// save settings to controller if requested
	pAct = new CPropertyAction(this, &CDAC::OnSaveCardSettings);
	CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
	AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
	AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
	AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
	AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
	AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

	//Getting limits
	maxvolts_ = 2.048;
	minvolts_ = 0;
	GetMaxVolts(maxvolts_);
	GetMinVolts(minvolts_);

	//Set DAC card voltage range 0 to 2.048V or -10 to +10V
	pAct = new CPropertyAction(this, &CDAC::OnDACMode);
	CreateProperty(g_DACModePropertyName, "0", MM::String, false, pAct);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_0);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_1);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_2);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_4);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_5);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_6);
	AddAllowedValue(g_DACModePropertyName, g_DACOutputMode_7);
	UpdateProperty(g_DACModePropertyName);

	//Max and Min Voltages as read only property
	command.str("");
	command << maxvolts_;
	CreateProperty(g_DACMaxVoltsPropertyName, command.str().c_str(), MM::Float, true);
	

	command.str("");
	command << minvolts_;
	CreateProperty(g_DACMinVoltsPropertyName, command.str().c_str(), MM::Float, true);
	

	//DAC gate open or closed
	pAct = new CPropertyAction(this, &CDAC::OnDACGate);
	CreateProperty(g_DACGatePropertyName, "0", MM::String, false, pAct);
	AddAllowedValue(g_DACGatePropertyName, g_OpenState);
	AddAllowedValue(g_DACGatePropertyName, g_ClosedState);
	UpdateProperty(g_DACGatePropertyName);


	// filter cut-off frequency
	pAct = new CPropertyAction(this, &CDAC::OnCutoffFreq);
	CreateProperty(g_ScannerCutoffFilterPropertyName, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_ScannerCutoffFilterPropertyName, 0.1, 650);
	UpdateProperty(g_ScannerCutoffFilterPropertyName);

	//DAC Voltage set 
	pAct = new CPropertyAction(this, &CDAC::OnDACVoltage);
	CreateProperty(g_DACVoltageName, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_DACVoltageName, minvolts_ * 1000, maxvolts_ * 1000);
	UpdateProperty(g_DACVoltageName);

	///////////////////// Optional Modules ////////////////////////////////////


   // get build info so we can add optional properties
	build_info_type build;
	RETURN_ON_MM_ERROR(hub_->GetBuildInfo(addressChar_, build));

	// add single-axis properties if supported
	// Normally we check for version, but this card is always going to have v3.30 and above
	if (build.vAxesProps[0] & BIT5)//     
	{
		// copied from ASIMMirror.cpp
		pAct = new CPropertyAction(this, &CDAC::OnSAAmplitude);
		CreateProperty(g_SAAmplitudeDACPropertyName, "0", MM::Float, false, pAct);
		SetPropertyLimits(g_SAAmplitudeDACPropertyName, 0, (maxvolts_ - minvolts_) * 1000);
		UpdateProperty(g_SAAmplitudeDACPropertyName);
		pAct = new CPropertyAction(this, &CDAC::OnSAOffset);
		CreateProperty(g_SAOffsetDACPropertyName, "0", MM::Float, false, pAct);
		SetPropertyLimits(g_SAOffsetDACPropertyName, minvolts_ * 1000, maxvolts_ * 1000);
		UpdateProperty(g_SAOffsetDACPropertyName);
		pAct = new CPropertyAction(this, &CDAC::OnSAPeriod);
		CreateProperty(g_SAPeriodPropertyName, "0", MM::Integer, false, pAct);
		UpdateProperty(g_SAPeriodPropertyName);
		pAct = new CPropertyAction(this, &CDAC::OnSAMode);
		CreateProperty(g_SAModePropertyName, g_SAMode_0, MM::String, false, pAct);
		AddAllowedValue(g_SAModePropertyName, g_SAMode_0);
		AddAllowedValue(g_SAModePropertyName, g_SAMode_1);
		AddAllowedValue(g_SAModePropertyName, g_SAMode_2);
		AddAllowedValue(g_SAModePropertyName, g_SAMode_3);
		UpdateProperty(g_SAModePropertyName);
		pAct = new CPropertyAction(this, &CDAC::OnSAPattern);
		CreateProperty(g_SAPatternPropertyName, g_SAPattern_0, MM::String, false, pAct);
		AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_0);
		AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_1);
		AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_2);
		if (FirmwareVersionAtLeast(3.14))
		{	//sin pattern was implemeted much later atleast firmware 3.14 needed
			AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_3);
		}
		UpdateProperty(g_SAPatternPropertyName);
		// generates a set of additional advanced properties that are rarely used
		pAct = new CPropertyAction(this, &CDAC::OnSAAdvanced);
		CreateProperty(g_AdvancedSAPropertiesPropertyName, g_NoState, MM::String, false, pAct);
		AddAllowedValue(g_AdvancedSAPropertiesPropertyName, g_NoState);
		AddAllowedValue(g_AdvancedSAPropertiesPropertyName, g_YesState);
		UpdateProperty(g_AdvancedSAPropertiesPropertyName);
	}

	// add ring buffer properties if supported (starting version 2.81)
	// Normally we check for version, but this card is always going to have v3.30 and above
	if (build.vAxesProps[0] & BIT1)
	{
		// get the number of ring buffer positions from the BU X output
		string rb_define = hub_->GetDefineString(build, "RING BUFFER");

		ring_buffer_capacity_ = 0;
		if (rb_define.size() > 12)
		{
			ring_buffer_capacity_ = atol(rb_define.substr(11).c_str());
		}

		if (ring_buffer_capacity_ != 0)
		{
			ring_buffer_supported_ = true;

			pAct = new CPropertyAction(this, &CDAC::OnRBMode);
			CreateProperty(g_RB_ModePropertyName, g_RB_OnePoint_1, MM::String, false, pAct);
			AddAllowedValue(g_RB_ModePropertyName, g_RB_OnePoint_1);
			AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayOnce_2);
			AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayRepeat_3);
			UpdateProperty(g_RB_ModePropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnRBDelayBetweenPoints);
			CreateProperty(g_RB_DelayPropertyName, "0", MM::Integer, false, pAct);
			UpdateProperty(g_RB_DelayPropertyName);

			// "do it" property to do TTL trigger via serial
			pAct = new CPropertyAction(this, &CDAC::OnRBTrigger);
			CreateProperty(g_RB_TriggerPropertyName, g_IdleState, MM::String, false, pAct);
			AddAllowedValue(g_RB_TriggerPropertyName, g_IdleState, 0);
			AddAllowedValue(g_RB_TriggerPropertyName, g_DoItState, 1);
			AddAllowedValue(g_RB_TriggerPropertyName, g_DoneState, 2);
			UpdateProperty(g_RB_TriggerPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnRBRunning);
			CreateProperty(g_RB_AutoplayRunningPropertyName, g_NoState, MM::String, false, pAct);
			AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_NoState);
			AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_YesState);
			UpdateProperty(g_RB_AutoplayRunningPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnUseSequence);
			CreateProperty(g_UseSequencePropertyName, g_NoState, MM::String, false, pAct);
			AddAllowedValue(g_UseSequencePropertyName, g_NoState);
			AddAllowedValue(g_UseSequencePropertyName, g_YesState);
			ttl_trigger_enabled_ = false;

			//Because DASequence API for SignalIO devices isn't exposed thru MMDEvices, we are adding a few extra properties to make this feature more accessible

			//This prop lets user execute StartDASequence(), StopDASequence(),ClearDASequence(),SendDASequence()
			pAct = new CPropertyAction(this, &CDAC::OnRBSequenceState);
			CreateProperty(g_RBSequenceStatePropertyName, g_IdleState, MM::String, false, pAct);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_IdleState, 0);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_RBSequenceStart, 1);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_RBSequenceStop, 2);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_RBSequenceClearSeq, 3);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_RBSequenceSendSeq, 4);
			AddAllowedValue(g_RBSequenceStatePropertyName, g_DoneState, 5);
			
			
			// this prop lets user do AddToDASequence()
			pAct = new CPropertyAction(this, &CDAC::OnAddtoRBSequence);
			CreateProperty(g_AddtoRBSequencePropertyName, "0", MM::Float, false, pAct);
			SetPropertyLimits(g_AddtoRBSequencePropertyName, 0, (maxvolts_ - minvolts_) * 1000);

		}

	}
	// Normally we check for version, but this card is always going to have v3.30 and above
	if (hub_->IsDefinePresent(build, "IN0_INT") && ring_buffer_supported_)
	{
		ttl_trigger_supported_ = true;

		//TTL IN mode
		pAct = new CPropertyAction(this, &CDAC::OnTTLin);
		CreateProperty(g_TTLinName, "0", MM::Integer, false, pAct);
		SetPropertyLimits(g_TTLinName, 0, 30);
		UpdateProperty(g_TTLinName);
		//TTL Out Mode
		pAct = new CPropertyAction(this, &CDAC::OnTTLout);
		CreateProperty(g_TTLoutName, "0", MM::Integer, false, pAct);
		SetPropertyLimits(g_TTLoutName, 0, 30);
		UpdateProperty(g_TTLoutName);

	}


	////////////////////// Before we Bail /////////////////////////////////////
	initialized_ = true;
	return DEVICE_OK;

}//end of   CDAC::Initialize()


int CDAC::SetSignalmv(double millivolts) {

	ostringstream command; command.str("");
	command << "M " << axisLetter_ << "=" << millivolts * unitMult_;
	return hub_->QueryCommandVerify(command.str(), ":A");

}

int CDAC::SetSignal(double volts) {


	return SetSignalmv(volts * 1000);

}

int CDAC::GetSignalmv(double &millivolts) {
	ostringstream command; command.str("");
	command << "W " << axisLetter_;
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterPosition2(millivolts));
	millivolts = millivolts / unitMult_;
	return DEVICE_OK;
}

int CDAC::GetSignal(double &volts) {

	double mvolts;

	RETURN_ON_MM_ERROR(GetSignalmv(mvolts));
	volts = mvolts / 1000;
	return DEVICE_OK;
}

int CDAC::GetLimits(double& minVolts, double& maxVolts) { minVolts = minvolts_; maxVolts = maxvolts_; return DEVICE_OK; };


int CDAC::SetGateOpen(bool open) {

	ostringstream command;

	if (open) {
		command.str("");
		command << "MC " << axisLetter_ << "+";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	else {
		command.str("");
		command << "MC " << axisLetter_ << "-";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::GetGateOpen(bool &open) {

	ostringstream command;
	long tmp;
	command.str("");
	command << "MC " << axisLetter_ << "? ";

	open = false;//incase of error we return that gate is closed
	// "MC <Axis>?" replies look like ":A 1"
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterPosition(3, tmp));

	if (tmp == 1) open = true;

	return DEVICE_OK;
}

int CDAC::GetMaxVolts(double &volts)
{
	ostringstream command;

	command.str("");
	command << "SU " << axisLetter_ << "? ";
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(volts));
	return DEVICE_OK;
}

int CDAC::GetMinVolts(double &volts)
{
	ostringstream command;

	command.str("");
	command << "SL " << axisLetter_ << "? ";
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(volts));
	return DEVICE_OK;
}

//////////////// Sequence Stuff/////////////////////////////


int CDAC::StartDASequence()// enables TTL triggering; doesn't actually start anything going on controller
{
	ostringstream command; command.str("");
	if (!ttl_trigger_supported_)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	// ensure that ringbuffer pointer points to first entry and
	// that we only trigger the first axis (assume only 1 axis on piezo card)
	command << addressChar_ << "RM Y=1 Z=0";
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));

	command.str("");
	command << addressChar_ << "TTL X=1";  // switch on TTL triggering
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	return DEVICE_OK;
}

int CDAC::StopDASequence()
// disables TTL triggering; doesn't actually stop anything already happening on controller
{
	ostringstream command; command.str("");
	if (!ttl_trigger_supported_)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	command << addressChar_ << "TTL X=0";  // switch off TTL triggering
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	return DEVICE_OK;
}

int CDAC::ClearDASequence()
{
	ostringstream command; command.str("");
	if (!ttl_trigger_supported_)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	sequence_.clear();
	command << addressChar_ << "RM X=0";  // clear ring buffer
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	return DEVICE_OK;
}
int CDAC::AddToDASequence(double voltage)
{
	if (!ttl_trigger_supported_)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	sequence_.push_back(voltage);
	return DEVICE_OK;
}


int CDAC::SendDASequence()
{
	ostringstream command; command.str("");
	if (!ttl_trigger_supported_)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	command << addressChar_ << "RM X=0"; // clear ring buffer
	RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	for (unsigned i = 0; i < sequence_.size(); i++)  // send new points
	{
		command.str("");
		command << "LD " << axisLetter_ << "=" << sequence_[i] * 1000 * unitMult_;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}

	return DEVICE_OK;
}



////////////////
// action handlers

int CDAC::OnDACVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	double tmp = 0.0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;


		RETURN_ON_MM_ERROR(GetSignalmv(tmp));
		pProp->Set(tmp);
		return DEVICE_OK;

	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		RETURN_ON_MM_ERROR(SetSignalmv(tmp));


	}
	return DEVICE_OK;
}// end of OnDacVoltage

int CDAC::OnDACGate(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	bool gtmp;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;


		RETURN_ON_MM_ERROR(GetGateOpen(gtmp));

		if (gtmp) {//true

			pProp->Set(g_OpenState);
		}
		else {

			pProp->Set(g_ClosedState);
		}


		return DEVICE_OK;

	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_OpenState) == 0) {
			gtmp = true;
		}
		else {
			gtmp = false;
		}

		RETURN_ON_MM_ERROR(SetGateOpen(gtmp));

	}
	return DEVICE_OK;
}// end of OnDacVoltage


int CDAC::OnDACMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{	//Will need controller restart for settings to take effect
	ostringstream command; command.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		ostringstream response; response.str("");
		command << "PR " << axisLetter_ << "?"; //On SIGNAL_DAC this is on PR(system flag) instead of PM because I needed upper and lower limits to change too 
		response << ":A " << axisLetter_ << "="; // Reply for PR command is ":A H=6 <CR><LF>"
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));

		bool success = 0;
		switch (tmp)
		{
		case 1: success = pProp->Set(g_DACOutputMode_1); break;
		case 0: success = pProp->Set(g_DACOutputMode_0); break;
		case 2: success = pProp->Set(g_DACOutputMode_2);  break;
		case 4: success = pProp->Set(g_DACOutputMode_4);  break;
		case 5: success = pProp->Set(g_DACOutputMode_5);  break;
		case 6: success = pProp->Set(g_DACOutputMode_6);  break;
		case 7: success = pProp->Set(g_DACOutputMode_7);  break;
		default: success = 0; break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_DACOutputMode_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_DACOutputMode_1) == 0)
			tmp = 1;
		else if (tmpstr.compare(g_DACOutputMode_2) == 0)
			tmp = 2;
		else if (tmpstr.compare(g_DACOutputMode_4) == 0)
			tmp = 4;
		else if (tmpstr.compare(g_DACOutputMode_5) == 0)
			tmp = 5;
		else if (tmpstr.compare(g_DACOutputMode_6) == 0)
			tmp = 6;
		else if (tmpstr.compare(g_DACOutputMode_7) == 0)
			tmp = 7;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		command << "PR " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}// end of OnDacMode


int CDAC::OnCutoffFreq(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	double tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "B " << axisLetter_ << "?";
		response << ":" << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		command << "B " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string tmpstr;
	ostringstream command; command.str("");
	if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		pProp->Get(tmpstr);
		command << addressChar_ << "SS ";
		if (tmpstr.compare(g_SaveSettingsOrig) == 0)
			return DEVICE_OK;
		if (tmpstr.compare(g_SaveSettingsDone) == 0)
			return DEVICE_OK;
		if (tmpstr.compare(g_SaveSettingsX) == 0)
			command << 'X';
		else if (tmpstr.compare(g_SaveSettingsY) == 0)
			command << 'Y';
		else if (tmpstr.compare(g_SaveSettingsZ) == 0)
			command << 'Z';
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A", (long)200));  // note 200ms delay added
		pProp->Set(g_SaveSettingsDone);
		command.str(""); command << g_SaveSettingsDone;
		RETURN_ON_MM_ERROR(hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()));
	}
	return DEVICE_OK;
}

int CDAC::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string tmpstr;
	if (eAct == MM::AfterSet) {
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_YesState) == 0)
			refreshProps_ = true;
		else
			refreshProps_ = false;
	}
	return DEVICE_OK;
}



///////////////////////////////////// Single Axis Functions/////////////////////////////

int CDAC::OnSAAdvanced(MM::PropertyBase* pProp, MM::ActionType eAct)
// special property, when set to "yes" it creates a set of little-used properties that can be manipulated thereafter
{
	if (eAct == MM::BeforeGet)
	{
		return DEVICE_OK; // do nothing
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_YesState) == 0)
		{
			CPropertyAction* pAct;

			pAct = new CPropertyAction(this, &CDAC::OnSAClkSrc);
			CreateProperty(g_SAClkSrcPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
			AddAllowedValue(g_SAClkSrcPropertyName, g_SAClkSrc_0);
			AddAllowedValue(g_SAClkSrcPropertyName, g_SAClkSrc_1);
			UpdateProperty(g_SAClkSrcPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnSAClkPol);
			CreateProperty(g_SAClkPolPropertyName, g_SAClkPol_0, MM::String, false, pAct);
			AddAllowedValue(g_SAClkPolPropertyName, g_SAClkPol_0);
			AddAllowedValue(g_SAClkPolPropertyName, g_SAClkPol_1);
			UpdateProperty(g_SAClkPolPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnSATTLOut);
			CreateProperty(g_SATTLOutPropertyName, g_SATTLOut_0, MM::String, false, pAct);
			AddAllowedValue(g_SATTLOutPropertyName, g_SATTLOut_0);
			AddAllowedValue(g_SATTLOutPropertyName, g_SATTLOut_1);
			UpdateProperty(g_SATTLOutPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnSATTLPol);
			CreateProperty(g_SATTLPolPropertyName, g_SATTLPol_0, MM::String, false, pAct);
			AddAllowedValue(g_SATTLPolPropertyName, g_SATTLPol_0);
			AddAllowedValue(g_SATTLPolPropertyName, g_SATTLPol_1);
			UpdateProperty(g_SATTLPolPropertyName);

			pAct = new CPropertyAction(this, &CDAC::OnSAPatternByte);
			CreateProperty(g_SAPatternModePropertyName, "0", MM::Integer, false, pAct);
			SetPropertyLimits(g_SAPatternModePropertyName, 0, 255);
			UpdateProperty(g_SAPatternModePropertyName);
		}
	}
	return DEVICE_OK;
}

int CDAC::OnSAAmplitude(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	double tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAA " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		tmp = tmp / unitMult_;
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		command << "SAA " << axisLetter_ << "=" << tmp * unitMult_;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSAOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	double tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAO " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		tmp = tmp / unitMult_;
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		command << "SAO " << axisLetter_ << "=" << tmp * unitMult_;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSAPeriod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAF " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		command << "SAF " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSAMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	static bool justSet = false;
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_ && !justSet)
			return DEVICE_OK;
		command << "SAM " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SAMode_0); break;
		case 1: success = pProp->Set(g_SAMode_1); break;
		case 2: success = pProp->Set(g_SAMode_2); break;
		case 3: success = pProp->Set(g_SAMode_3); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
		justSet = false;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SAMode_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SAMode_1) == 0)
			tmp = 1;
		else if (tmpstr.compare(g_SAMode_2) == 0)
			tmp = 2;
		else if (tmpstr.compare(g_SAMode_3) == 0)
			tmp = 3;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		command << "SAM " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
		// get the updated value right away
		justSet = true;
		return OnSAMode(pProp, MM::BeforeGet);
	}
	return DEVICE_OK;
}

int CDAC::OnSAPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		tmp = tmp & ((long)(BIT2 | BIT1 | BIT0));  // zero all but the lowest 3 bits
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SAPattern_0); break;
		case 1: success = pProp->Set(g_SAPattern_1); break;
		case 2: success = pProp->Set(g_SAPattern_2); break;
		case 3: success = pProp->Set(g_SAPattern_3); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SAPattern_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SAPattern_1) == 0)
			tmp = 1;
		else if (tmpstr.compare(g_SAPattern_2) == 0)
			tmp = 2;
		else if (tmpstr.compare(g_SAPattern_3) == 0)
			tmp = 3;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		// have to get current settings and then modify bits 0-2 from there
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		long current;
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(current));
		current = current & (~(long)(BIT2 | BIT1 | BIT0));  // set lowest 3 bits to zero
		tmp += current;
		command.str("");
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSAPatternByte(MM::PropertyBase* pProp, MM::ActionType eAct)
// get every single time
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tmp);
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}


int CDAC::OnSAClkSrc(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		tmp = tmp & ((long)(BIT7));  // zero all but bit 7
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SAClkSrc_0); break;
		case BIT7: success = pProp->Set(g_SAClkSrc_1); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SAClkSrc_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SAClkSrc_1) == 0)
			tmp = BIT7;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		// have to get current settings and then modify bit 7 from there
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		long current;
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(current));
		current = current & (~(long)(BIT7));  // clear bit 7
		tmp += current;
		command.str("");
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSAClkPol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		tmp = tmp & ((long)(BIT6));  // zero all but bit 6
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SAClkPol_0); break;
		case BIT6: success = pProp->Set(g_SAClkPol_1); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SAClkPol_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SAClkPol_1) == 0)
			tmp = BIT6;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		// have to get current settings and then modify bit 6 from there
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		long current;
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(current));
		current = current & (~(long)(BIT6));  // clear bit 6
		tmp += current;
		command.str("");
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSATTLOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		tmp = tmp & ((long)(BIT5));  // zero all but bit 5
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SATTLOut_0); break;
		case BIT5: success = pProp->Set(g_SATTLOut_1); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SATTLOut_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SATTLOut_1) == 0)
			tmp = BIT5;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		// have to get current settings and then modify bit 5 from there
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		long current;
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(current));
		current = current & (~(long)(BIT5));  // clear bit 5
		tmp += current;
		command.str("");
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

int CDAC::OnSATTLPol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		tmp = tmp & ((long)(BIT4));  // zero all but bit 4
		switch (tmp)
		{
		case 0: success = pProp->Set(g_SATTLPol_0); break;
		case BIT4: success = pProp->Set(g_SATTLPol_1); break;
		default:success = 0;                      break;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_SATTLPol_0) == 0)
			tmp = 0;
		else if (tmpstr.compare(g_SATTLPol_1) == 0)
			tmp = BIT4;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		// have to get current settings and then modify bit 4 from there
		command << "SAP " << axisLetter_ << "?";
		response << ":A " << axisLetter_ << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		long current;
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(current));
		current = current & (~(long)(BIT4));  // clear bit 4
		tmp += current;
		command.str("");
		command << "SAP " << axisLetter_ << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
	}
	return DEVICE_OK;
}

/////////////////////////////////////// RING BUFFER //////////////////////////


int CDAC::OnRBMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	string pseudoAxisChar = FirmwareVersionAtLeast(2.89) ? "F" : "X";
	long tmp;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << addressChar_ << "RM " << pseudoAxisChar << "?";
		response << ":A " << pseudoAxisChar << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (tmp >= 128)
		{
			tmp -= 128;  // remove the "running now" code if present
		}
		bool success;
		switch (tmp)
		{
		case 1: success = pProp->Set(g_RB_OnePoint_1); break;
		case 2: success = pProp->Set(g_RB_PlayOnce_2); break;
		case 3: success = pProp->Set(g_RB_PlayRepeat_3); break;
		default: success = false;
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_RB_OnePoint_1) == 0)
			tmp = 1;
		else if (tmpstr.compare(g_RB_PlayOnce_2) == 0)
			tmp = 2;
		else if (tmpstr.compare(g_RB_PlayRepeat_3) == 0)
			tmp = 3;
		else
			return DEVICE_INVALID_PROPERTY_VALUE;
		command << addressChar_ << "RM " << pseudoAxisChar << "=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
		RETURN_ON_MM_ERROR(hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), tmpstr.c_str()));
	}
	return DEVICE_OK;
}

int CDAC::OnRBTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	if (eAct == MM::BeforeGet) {
		pProp->Set(g_IdleState);
	}
	else  if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_DoItState) == 0)
		{
			command << addressChar_ << "RM";
			RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
			pProp->Set(g_DoneState);
		}
	}
	return DEVICE_OK;
}

int CDAC::OnRBRunning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	string pseudoAxisChar = FirmwareVersionAtLeast(2.89) ? "F" : "X";
	long tmp = 0;
	static bool justSet;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_ && !justSet)
			return DEVICE_OK;
		command << addressChar_ << "RM " << pseudoAxisChar << "?";
		response << ":A " << pseudoAxisChar << "=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		bool success;
		if (tmp >= 128)
		{
			success = pProp->Set(g_YesState);
		}
		else
		{
			success = pProp->Set(g_NoState);
		}
		if (!success)
			return DEVICE_INVALID_PROPERTY_VALUE;
		justSet = false;
	}
	else if (eAct == MM::AfterSet)
	{
		justSet = true;
		return OnRBRunning(pProp, MM::BeforeGet);
		// TODO determine how to handle this with shared properties since ring buffer is per-card and not per-axis
		// the reason this property exists (and why it's not a read-only property) are a bit hazy as of mid-2017
	}
	return DEVICE_OK;
}

int CDAC::OnRBDelayBetweenPoints(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	long tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << addressChar_ << "RT Z?";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A Z="));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		pProp->Get(tmp);
		command << addressChar_ << "RT Z=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
		command.str(""); command << tmp;
		RETURN_ON_MM_ERROR(hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()));
	}
	return DEVICE_OK;
}



int CDAC::OnUseSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	if (eAct == MM::BeforeGet)
	{
		if (ttl_trigger_enabled_)
			pProp->Set(g_YesState);
		else
			pProp->Set(g_NoState);
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);
		ttl_trigger_enabled_ = (ttl_trigger_supported_ && (tmpstr.compare(g_YesState) == 0));
		return OnUseSequence(pProp, MM::BeforeGet);  // refresh value
	}
	return DEVICE_OK;
}

int CDAC::OnRBSequenceState(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(g_IdleState);
	}
	else  if (eAct == MM::AfterSet) {
		
		string tmpstr;
		pProp->Get(tmpstr);
		if (tmpstr.compare(g_RBSequenceStart) == 0)	        RETURN_ON_MM_ERROR(StartDASequence());
		else if (tmpstr.compare(g_RBSequenceStop) == 0)     RETURN_ON_MM_ERROR(StopDASequence());
		else if (tmpstr.compare(g_RBSequenceClearSeq) == 0) RETURN_ON_MM_ERROR(ClearDASequence());
		else if (tmpstr.compare(g_RBSequenceSendSeq) == 0)  RETURN_ON_MM_ERROR(SendDASequence());
		
		pProp->Set(g_DoneState);
		
	}
	return DEVICE_OK;

}

int CDAC::OnAddtoRBSequence(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(0.00);
	}
	else  if (eAct == MM::AfterSet) {
		double tmp;
		pProp->Get(tmp);

		tmp = tmp / 1000; //to go from mv to volts
		RETURN_ON_MM_ERROR(AddToDASequence(tmp));
		
	}
	return DEVICE_OK;

}

////////////////////////////////////// TTL ////////////////////////////////

int CDAC::OnTTLin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	double tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << addressChar_ << "TTL X?";
		response << ":A X=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		pProp->Get(tmp);
		command << addressChar_ << "TTL X=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
		command.str(""); command << tmp;
		RETURN_ON_MM_ERROR(hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()));
	}
	return DEVICE_OK;
}

int CDAC::OnTTLout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	ostringstream command; command.str("");
	ostringstream response; response.str("");
	double tmp = 0;
	if (eAct == MM::BeforeGet)
	{
		if (!refreshProps_ && initialized_)
			return DEVICE_OK;
		command << addressChar_ << "TTL Y?";
		response << ":A Y=";
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), response.str()));
		RETURN_ON_MM_ERROR(hub_->ParseAnswerAfterEquals(tmp));
		if (!pProp->Set(tmp))
			return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		if (hub_->UpdatingSharedProperties())
			return DEVICE_OK;
		pProp->Get(tmp);
		command << addressChar_ << "TTL Y=" << tmp;
		RETURN_ON_MM_ERROR(hub_->QueryCommandVerify(command.str(), ":A"));
		command.str(""); command << tmp;
		RETURN_ON_MM_ERROR(hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()));
	}
	return DEVICE_OK;
}

