///////////////////////////////////////////////////////////////////////////////
// FILE:          Varia.cpp
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


#define NOMINMAX //To get c++ min and max to work
#include "SuperK.h"

extern const char* g_VariaName;

SuperKVaria::SuperKVaria(): name_(g_VariaName), SuperKDevice(0x68), wavelength_(632), bandwidth_(10) { 
	InitializeDefaultErrorMessages();
	SetErrorText(DEVICE_SERIAL_TIMEOUT, "Serial port timed out without receiving a response.");
		// Name
	CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "NKT Photonics SuperK Varia Filter", MM::String, true);
}

SuperKVaria::~SuperKVaria() {
	Shutdown();
}


//********Device API*********//
int SuperKVaria::Initialize() {
	hub_ = dynamic_cast<SuperKHub*>(GetParentHub());
	try {
		setNKTAddress(hub_ -> getDeviceAddress(this)); 
	} catch (const std::out_of_range& /** oor **/) {
		SetErrorText(99, "SuperKVaria Filter was not found in the SuperKHub list of connected devices.");
		return 99;
	}

	//For some reason Micromanager tries accessing properties before initialization. For this reason we don't create properties until after initialization.
	//MonitorInput
	CPropertyAction* pAct = new CPropertyAction(this, &SuperKVaria::onMonitorInput);
	CreateProperty("MonitorInput", "0", MM::Float, true, pAct, false);

	//Bandwidth
	pAct = new CPropertyAction(this, &SuperKVaria::onBandwidth);
	CreateProperty("Bandwidth (nm)", "10", MM::Float, false, pAct, false);
	SetPropertyLimits("Bandwidth (nm)", 1, 100);

	//Wavelength
	pAct = new CPropertyAction(this, &SuperKVaria::onWavelength);
	CreateProperty("Wavelength", "632", MM::Float, false, pAct, false);
	SetPropertyLimits("Wavelength", 405, 845);


	//Short Wave Pass
	pAct = new CPropertyAction(this, &SuperKVaria::onSWP);
	CreateProperty("Short Wave Pass", "632", MM::Float, false, pAct, false);
	SetPropertyLimits("Short Wave Pass", 400, 850);

	//Long Wave Pass
	pAct = new CPropertyAction(this, &SuperKVaria::onLWP);
	CreateProperty("Long Wave Pass", "632", MM::Float, false, pAct, false);
	SetPropertyLimits("Long Wave Pass", 400, 850);

	//Set the hardware to make sure all variables are consistent
	SetProperty("Wavelength", "632");
	SetProperty("Bandwidth", "10");

	return DEVICE_OK;
}

int SuperKVaria::Shutdown() {
	return DEVICE_OK;
}

void SuperKVaria::GetName(char* pName) const {
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(pName, name_.c_str());
}

bool SuperKVaria::Busy() {
	unsigned long status;
	hub_->deviceGetStatusBits(this, &status);
	if (status & 0x7000) { //One of the three filters is moving.
		return true;
	} else {
		return false;
	}
}

//******Properties*******//
int SuperKVaria::onMonitorInput(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet) {
		uint16_t val;
		hub_->registerReadU16(this, 0x13, &val);
		float percent = ((float)val) / 10;//convert from units of 0.1% to %
		pProp->Set(percent);
	}
	return DEVICE_OK;
}

int SuperKVaria::onBandwidth(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(bandwidth_);
	}
	if (eAct == MM::AfterSet) {
		pProp->Get(bandwidth_);
		return updateFilters();
	}
	return DEVICE_OK;
}

int SuperKVaria::onWavelength(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(wavelength_);
	}
	if (eAct == MM::AfterSet) {
		pProp->Get(wavelength_);
		return updateFilters();
	}
	return DEVICE_OK;
}

int SuperKVaria::onLWP(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		uint16_t val;
		int ret = hub_->registerReadU16(this, 0x34, &val);
		if (ret!=DEVICE_OK){return ret;}
		lwp_ = ((double) val) / 10;
		pProp->Set(lwp_);
	}
	if (eAct == MM::AfterSet) {
		pProp->Get(lwp_);
		uint16_t val = (uint16_t)(lwp_ * 10); //Convert from units of nm to 0.1nm
		int ret = hub_->registerWriteU16(this, 0x34, val); //LWP 
		if (ret!=DEVICE_OK){return ret;}
	}
	return DEVICE_OK;
}

int SuperKVaria::onSWP(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		uint16_t val;
		int ret = hub_->registerReadU16(this, 0x33, &val);
		if (ret!=DEVICE_OK){return ret;}
		swp_ = ((double) val) / 10;
		pProp->Set(swp_);
	}
	if (eAct == MM::AfterSet) {
		pProp->Get(swp_);
		uint16_t val = (uint16_t)(swp_ * 10); //Convert from units of nm to 0.1nm
		int ret = hub_->registerWriteU16(this, 0x33, val); //SWP
		if (ret!=DEVICE_OK){return ret;}
	}
	return DEVICE_OK;
}

int SuperKVaria::updateFilters() {
	double lwp = wavelength_ - bandwidth_ / 2;
	double swp = wavelength_ + bandwidth_ / 2;
	lwp = std::max(std::min(lwp, 850.0), 400.0); //Clamp the filter value to the allowed range.
	swp = std::max(std::min(swp, 850.0), 400.0); //Clamp the filter value to the allowed range.
	int ret = DEVICE_OK;
	if (lwp != lwp_) {
		ret = SetProperty("Long Wave Pass", std::to_string((long double)lwp).c_str());
		if (ret!=DEVICE_OK){return ret;}
	}
	if (swp != swp_) {
		ret = SetProperty("Short Wave Pass", std::to_string((long double)swp).c_str());
		if (ret!=DEVICE_OK){return ret;}
	}
	return DEVICE_OK;
}