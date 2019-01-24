///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterLambda.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/26/2005
//                Nico Stuurman, Oct. 2010
//				  Nick Anthony, Oct. 2018
//
// CVS:           $Id$
//

#ifndef _SUTTER_LAMBDA_H_
#define _SUTTER_LAMBDA_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_SHUTTER_MODE     10006
#define ERR_UNKNOWN_SHUTTER_ND       10007
#define ERR_NO_ANSWER                10008




class SutterHub : public HubBase<SutterHub>
{
public:
	SutterHub(const char* name);
	~SutterHub();

	//Device API
	int Initialize();
	int Shutdown();
	void GetName(char* pName) const;
	bool Busy();

	bool SupportsDeviceDetection() { return true; };
	MM::DeviceDetectionStatus DetectDevice();

	//Hub API
	int DetectInstalledDevices();

	//Action API
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAnswerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMotorsEnabled(MM::PropertyBase* pProp, MM::ActionType eAct);

	//From old sutterutil class
	
	int GetControllerType(std::string& type, std::string& id);
	int GetStatus(std::vector<unsigned char>& status);
	int SetCommand(const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, std::vector<unsigned char>& response);
	int SetCommand(const std::vector<unsigned char> command, const std::vector<unsigned char> altEcho);
	int SetCommand(const std::vector<unsigned char> command);
private:
	std::string port_;
	bool mEnabled_;
	bool busy_;
	bool initialized_;
	std::string name_;
	MMThreadLock lock_;
	//Make comms thread safe
	MMThreadLock& GetLock() { return lock_; };
	int GoOnline(); //Transfer control to serial.

};


#endif //_SUTTER_LAMBDA_H_
