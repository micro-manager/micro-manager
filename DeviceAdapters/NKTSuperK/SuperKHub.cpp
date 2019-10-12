///////////////////////////////////////////////////////////////////////////////
// FILE:          SuperKHub.cpp
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

extern std::map<uint8_t, const char*> g_devices;


SuperKHub::SuperKHub() {
	MMThreadGuard myLock(getLock());
	setNKTErrorText();
	SetErrorText(DEVICE_SERIAL_TIMEOUT, "Serial port timed out without receiving a response.");

	CPropertyAction* pAct = new CPropertyAction(this, &SuperKHub::onPort);
	CreateProperty("ComPort", "Undefined", MM::String, false, pAct, true);

	//Find NKT ports and add them a property options.
	unsigned short maxLen = 255;
	char ports[255];
	NKTPDLL::getAllPorts(ports, &maxLen);
	int ret =  NKTPDLL::openPorts(ports, 1, 0); //Scan all available ports and open the ones that are recognized as NKT devices
	//if (ret!=0){return result;}
	char detectedPorts[255];
	NKTPDLL::getOpenPorts(detectedPorts, &maxLen); //string of comma separated port names
	ret = NKTPDLL::closePorts("");
	std::string detectedPortsStr(detectedPorts);
	size_t pos = 0;
	std::string token;
	std::string delimiter = ",";
	while (true) {
		pos = detectedPortsStr.find(delimiter);
		token = detectedPortsStr.substr(0, pos);
		AddAllowedValue("ComPort", token.c_str());
		detectedPortsStr.erase(0, pos + delimiter.length());
		if (pos == std::string::npos) {break;}
	}
}

SuperKHub::~SuperKHub() {} //Some device adapters call shutdown here. However that causes shutdown to be called twice.

//********Device API*********//
int SuperKHub::Initialize() {
	MMThreadGuard myLock(getLock());
	int ret = NKTPDLL::openPorts(port_.c_str(), 1, 1) + PortResultsOffset; //Usage of the NKT sdk Dll requires we use their port opening mechanics rather than micromanager's.
	if (ret!=PortResultsOffset){return ret;}
	ret = populateDeviceAddressMap(); //We need to populate deviceAddressMap_ or we won't be able to instantiate our devices.
	if (ret!=0){return ret;}
	return DEVICE_OK;
}

int SuperKHub::Shutdown() {
	MMThreadGuard myLock(getLock());
	//int ret = NKTPDLL::closePorts(port_.c_str()); ///for some reason we are usually not able to close the port this is a problem, it won't close, it also won't reopen. what a mess.
	//if ((ret!=0)){return ret;}
	return DEVICE_OK;
}

//*******Hub API*********//
int SuperKHub::DetectInstalledDevices() {
	unsigned char maxTypes = 255;
	unsigned char types[255]; 
	int ret = NKTPDLL::deviceGetAllTypes(port_.c_str(), types, &maxTypes) + DeviceResultsOffset;
	if (ret!=DeviceResultsOffset) { return ret;}
	for (uint8_t i=0; i<maxTypes; i++) {
		if (types[i] == 0) { continue; } //No device detected at address `i`
		else {
			try{
				const char* devName = g_devices.at(types[i]); //Some of the NKT devices do not have a class in this device adapter. in that case pDev will be 0 and will not be added.
				MM::Device* pDev = CreateDevice(devName); //Important to know that the instance created here will not be the one that actually gets used.
				if (pDev) {
					AddInstalledDevice(pDev);
				}
			} catch (const std::out_of_range& /** oor **/) {} // If a device type is in `types` but not in g_devices then an error will occur. this device isn't supported anyway though.
		}
	}
	return DEVICE_OK;
}

int SuperKHub::populateDeviceAddressMap() {
	unsigned char maxTypes = 255;
	unsigned char types[255]; 
	int ret = NKTPDLL::deviceGetAllTypes(port_.c_str(), types, &maxTypes) + DeviceResultsOffset;
	if (ret!=DeviceResultsOffset) { return ret;}
	for (uint8_t i=0; i<maxTypes; i++) {
		if (types[i] == 0) { continue; } //No device detected at address `i`
		else {
			const char* devName;
			try{
				devName = g_devices.at(types[i]); //Some of the NKT devices do not have a class in this device adapter. in that case pDev will be 0 and will not be added.
			} catch (const std::out_of_range& /** oor **/) { continue;}// If a device type is in `types` but not in g_devices then an error will occur. this device isn't supported anyway though.
			MM::Device* pDev = CreateDevice(devName); //Important to know that the instance created here will not be the one that actually gets used.
			if (pDev) {
				deviceAddressMap_[types[i]] = i; //Create an entry showing what address to find device type at.
			}	
		}
	}
	return DEVICE_OK;
}

//******Properties*******//
int SuperKHub::onPort(MM::PropertyBase* pProp, MM::ActionType pAct) {
	if (pAct == MM::BeforeGet) {
		pProp->Set(port_.c_str());
	}
	else if (pAct == MM::AfterSet) {
		pProp->Get(port_);
	}
	return DEVICE_OK;
}

std::string SuperKHub::getPort() {
	return port_;
}

uint8_t SuperKHub::getDeviceAddress(SuperKDevice* devPtr) { //This can throw an out of range error.
	return deviceAddressMap_.at(devPtr->getNKTType());
}


int SuperKHub::registerReadU8(SuperKDevice* dev, uint8_t regId, uint8_t* val) {
	MMThreadGuard myLock(getLock()); //get thread lock
	int ret = NKTPDLL::registerReadU8(port_.c_str(), dev->getNKTAddress(), regId, val, -1) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;
}

int SuperKHub::registerReadU16(SuperKDevice* dev, uint8_t regId, uint16_t* val) {
	MMThreadGuard myLock(getLock()); //get thread lock
	int ret = NKTPDLL::registerReadU16(port_.c_str(), dev->getNKTAddress(), regId, val, -1) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;
}

int SuperKHub::registerWriteU8(SuperKDevice* dev, uint8_t regId, uint8_t val) {
	MMThreadGuard myLock(getLock()); //get thread lock
	int ret = NKTPDLL::registerWriteU8(port_.c_str(), dev->getNKTAddress(), regId, val, -1) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;
}

int SuperKHub::registerWriteU16(SuperKDevice* dev, uint8_t regId, uint16_t val) {
	MMThreadGuard myLock(getLock()); //get thread lock
	int ret = NKTPDLL::registerWriteU16(port_.c_str(), dev->getNKTAddress(), regId, val, -1) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;;
}

int SuperKHub::registerReadS16(SuperKDevice* dev, uint8_t regId, int16_t* val) {
	MMThreadGuard myLock(getLock()); //get thread lock
	int ret = NKTPDLL::registerReadS16(port_.c_str(), dev->getNKTAddress(), regId, val, -1) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;
}

int SuperKHub::deviceGetStatusBits(SuperKDevice* dev, unsigned long* val) {
	MMThreadGuard myLock(getLock());
	int ret = NKTPDLL::deviceGetStatusBits(port_.c_str(), dev->getNKTAddress(), val) + RegisterResultsOffset;
	if (ret!=RegisterResultsOffset){return ret;}
	return DEVICE_OK;
}

void SuperKHub::setNKTErrorText() {
	for (int i=0; i<16; i++) {
		int errNum = i + RegisterResultsOffset;
		char newstr[255];
		sprintf(newstr, "NKT SDK RegisterResults Error Code: %d", errNum - RegisterResultsOffset);
		SetErrorText(errNum, newstr);
	}
	for (int i=0; i<7; i++) {
		int errNum = i + DeviceResultsOffset;
		char newstr[255];
		sprintf(newstr, "NKT SDK DeviceResults Error Code: %d", errNum - DeviceResultsOffset);
		SetErrorText(errNum, newstr);
	}
	for (int i=0; i<5; i++) {
		int errNum = i + PortResultsOffset;
		char newstr[255];
		sprintf(newstr, "NKT SDK PortResults Error Code: %d", errNum - PortResultsOffset);
		SetErrorText(errNum, newstr);
	}
}