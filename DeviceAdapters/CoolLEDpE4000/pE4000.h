///////////////////////////////////////////////////////////////////////////////
// FILE:          pE4000.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   pE-4000 light source controller adapter
// COPYRIGHT:     CoolLED Ltd, UK, 2017
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
// AUTHOR:        Jinting Guo, jinting.guo@coolled.com, 12/07/2017

#ifndef PE4000_H
#define PE4000_H

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
//#include <iostream>
#include <vector>
using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004

class PollingThread;

class Controller : public CShutterBase<Controller>
{
public:
	Controller(const char* name);
	~Controller();

	friend class PollingThread;

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	// action interface
	int OnChannelWave(MM::PropertyBase* pProp, MM::ActionType eAct, long channel);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnChannelState(MM::PropertyBase* pProp, MM::ActionType eAct, long channel);
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLockPod(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Shutter API
	int SetOpen(bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);

	static MMThreadLock& GetLock() { return lock_; }


protected:
	long channelIntensities_[4];
	long channelSelection_[4];
	bool globalState_;
	string channelWave_[4];

	void GetIntensity(long& intensity, long index);
	void GetUpdate();

private:

	bool initialized_;
	std::string name_;
	bool busy_;
	int error_;
	MM::MMTime changedTime_;
	bool selectionUpdated_[4];
	bool intensityUpdated_[4];
	bool waveUpdated_[4];
	bool globalStateUpdated_;

	std::string port_;
	unsigned char buf_[1000];
	string buf_string_;

	double answerTimeoutMs_;

	static MMThreadLock lock_;
	PollingThread* mThread_;

	void SetIntensity(long intensity, long index);
	void ReadGreeting();
	void GeneratePropertyLockPod();
	void GeneratePropertyIntensity();
	void GenerateChannelSelector();
	void GeneratePropertyState();
	void GenerateChannelState();
	void GenerateDescription();
	void StripString(string& StringToModify);
	void Send(string cmd);
	void ReceiveOneLine();
	void Purge();
	int HandleErrors();
	
	Controller& operator=(Controller& /*rhs*/) { assert(false); return *this; }
};

class PollingThread : public MMDeviceThreadBase
{
public:
	PollingThread(Controller& aController);
	~PollingThread();
	int svc();
	int open(void*) { return 0; }
	int close(unsigned long) { return 0; }

	void Start();
	void Stop() { stop_ = true; }
	PollingThread & operator=(const PollingThread &)
	{
		return *this;
	}


private:
	long state_;
	Controller& aController_;
	bool stop_;
};



#endif // PE4000_H
