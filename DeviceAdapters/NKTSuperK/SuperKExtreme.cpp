///////////////////////////////////////////////////////////////////////////////
// FILE:          SuperKExtreme.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SuperK Laser by NKT Photonics
// COPYRIGHT:     2019 Backman Biophotonics Lab, Northwestern University
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Nick Anthony

#include "SuperK.h"

extern const char* g_ExtremeName;

SuperKExtreme::SuperKExtreme(): name_(g_ExtremeName), SuperKDevice(0x60), emissionChangedTime_(0), emissionOn_(false) {
	InitializeDefaultErrorMessages();
	SetErrorText(DEVICE_SERIAL_TIMEOUT, "Serial port timed out without receiving a response.");

	// Name
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "NKT Photonics SuperK Extreme Laser", MM::String, true);
}

SuperKExtreme::~SuperKExtreme() {} //Some device adapters call shutdown here. However that causes shutdown to be called twice.

//********Device API*********//
int SuperKExtreme::Initialize() {
	hub_ = dynamic_cast<SuperKHub*>(GetParentHub());
	try {
		setNKTAddress(hub_ -> getDeviceAddress(this)); 
	} catch (const std::out_of_range& /** oor **/) {
		SetErrorText(99, "SuperKExtreme Laser was not found in the SuperKHub list of connected devices.");
		return 99;
	}

	//For some reason Micromanager tries accessing properties before initialization. For this reason we don't create properties until after initialization.
	//Emission On
	CPropertyAction* pAct = new CPropertyAction(this, &SuperKExtreme::onEmission);
	CreateProperty("Emission Enabled", "False", MM::String, false, pAct, false);
	AddAllowedValue("Emission Enabled", "True");
	AddAllowedValue("Emission Enabled", "False");

	//Set Power
	pAct = new CPropertyAction(this, &SuperKExtreme::onPower);
	CreateProperty("Power %", "0", MM::Float, false, pAct, false);
	SetPropertyLimits("Power %", 0, 100);

	//Get InletTemperature
	pAct = new CPropertyAction(this, &SuperKExtreme::onInletTemperature);
	CreateProperty("Inlet Temperature (C)", "0", MM::Float, true, pAct, false); 
	
	return DEVICE_OK;
}

int SuperKExtreme::Shutdown() {
	SetProperty("Emission Enabled", "False");
	return DEVICE_OK;
}

void SuperKExtreme::GetName(char* pName) const {
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(pName, name_.c_str());
}


//******Properties*******//

int SuperKExtreme::onEmission(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		bool open;
		GetOpen(open);
		if (open) {
			pProp->Set("True");
		} else {
			pProp->Set("False");
		}
	}
	else if (eAct == MM::AfterSet) {
		std::string enabled;
		pProp->Get(enabled);
		int ret;
		if (enabled.compare("True") == 0) {
			ret = SetOpen(true);
		} else if (enabled.compare("False") == 0) {
			ret = SetOpen(false);
		} else {
			return 666;
		}
		if (ret!=DEVICE_OK){return ret;}
	}
	return DEVICE_OK;
}

int SuperKExtreme::onPower(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		uint16_t val;
		hub_->registerReadU16(this, 0x37, &val);
		pProp -> Set(((float) val) / 10); //Convert for units of 0.1C to 1C
	} else if (eAct == MM::AfterSet) {
		double power;
		pProp->Get(power);
		uint16_t val = (uint16_t)(power * 10); //Convert for units of C to 0.1C
		hub_->registerWriteU16(this, 0x37, val);
	}
	return DEVICE_OK;
}

int SuperKExtreme::onInletTemperature(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		int16_t val;
		hub_->registerReadS16(this, 0x11, &val);
		pProp -> Set(((float) val) / 10); //Convert for units of 0.1C to 1C
	}
	return DEVICE_OK;
}


//******Shutter API*******//
int SuperKExtreme::SetOpen(bool open) {
	int ret;
	uint8_t val;
	if (open) {
		val = 3;
	} else {
		val = 0;
	}
	ret = hub_->registerWriteU8(this, 0x30, val);	
	if (ret!=0) { return ret;}
	emissionOn_ = open;
	emissionChangedTime_ = GetCurrentMMTime();
	return DEVICE_OK;
}

int SuperKExtreme::GetOpen(bool& open) {
	double elapsedTime = 0;
	do {elapsedTime = (GetCurrentMMTime() - emissionChangedTime_).getMsec();}
	while (elapsedTime < 10) ; //Wait until the emission setting has had at least 10ms before checking the register
	unsigned long statusBits;
	int ret = hub_->deviceGetStatusBits(this, &statusBits);
	if (ret!=DEVICE_OK){return ret;}
	if (statusBits & 0x0001){ //Bit 0 of statusBits indicates if emission is on.
		emissionOn_ = true;
	} else {
		emissionOn_ = false;
	}
	open = emissionOn_;
	return DEVICE_OK;
}

int SuperKExtreme::Fire(double /* deltaT */)
{
   // Not supported
   return DEVICE_UNSUPPORTED_COMMAND;
}