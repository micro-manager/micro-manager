#pragma once
///////////////////////////////////////////////////////////////////////////////
// FILE:          E600.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Yodn E600 light source controller adapter
// COPYRIGHT:     
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
// AUTHOR:        BJI MBQ (mbaoqi@outlook.com)
///////////////////////////////////////////////////////////////////////////////
#ifndef E600_H
#define E600_H

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"

#include <string>
#include <vector>
#include <iomanip>

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Error codes
///////////////////////////////////////////////////////////////////////////////
#define ERR_PORT_CHANGE_FORBIDDEN    10004

class PollingThread;

class E600Controller : public CShutterBase<E600Controller>
{
public:
	E600Controller(const char* name);
	~E600Controller();

	friend class PollingThread;

	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnErrorCode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLampSwitch(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnChannelUse(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnChannelTemperature(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnChannelUseTime(MM::PropertyBase* pProp, MM::ActionType eAct, long index);


	int SetOpen(bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);

	static MMThreadLock& GetLock() { return lock_; }

protected:
	
	unsigned char channelUse_[3];
	unsigned char channelIntensitiesValue_[3];
	bool channelIntensitiesUpdate_[3];
	unsigned char channelUseState_[3];
	bool channelUseStateUpdate_[3];
	unsigned char channelTemperature_[3];
	unsigned int channelUseHours_[3];
	unsigned char errorCode_;
	unsigned char lampState_;
	bool lampStateUpdate_;
	
	bool globalState_;

	int Update();

private:

	bool isDisconnect_;
	bool initialized_;
	std::string name_;
	int error_;
	MM::MMTime changedTime_;
	std::string port_;
	double answerTimeoutMs_;
	static MMThreadLock lock_;
	PollingThread* mThread_;

	 int SendData(unsigned char *data, unsigned int size);
	 int ReadData(unsigned char *data, unsigned int size);

	void CreateMainVersionProperty();
	void CreatePanelVersionProperty();

	void SetIntensity(long intensity, long index);
	long SetTemperatureTransform(const long in);
	void SetErrorCodeStr(const unsigned char errorCode, std::string &str);
	void Purge();
	int HandleErrors();
   std::string IToString(int in);

	E600Controller& operator=(E600Controller &) { assert(false); return *this; }
};


class PollingThread : public MMDeviceThreadBase
{

public:
	PollingThread(E600Controller& aController);
	~PollingThread();
	PollingThread & operator=(const PollingThread &)
	{
		return *this;
	}
	
	int svc();
	int open(void*) { return 0; }
	int close(unsigned long) { return 0; }

	void Start();
	void Stop() { stop_ = true; }
	

private:
	E600Controller& aController_;
	bool stop_;
};


#endif // E600_H
