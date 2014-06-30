///////////////////////////////////////////////////////////////////////////////
// FILE:          ActiveHmdControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab Active Humidifier  device adapter
//                
// AUTHOR:        Marco Di Pasqua @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_HMD_CONTROL_
#define _OKOLAB_HMD_CONTROL_

class HmdControl_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   HMD  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class HmdControl: public OkolabDevice , public CGenericBase<HmdControl> 
{
 public:
	HmdControl();
	~HmdControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();
    MM::DeviceDetectionStatus DetectDevice(void);

	// internal API 
	bool WakeUp();
	int GetConc(double& val);
	int GetSetPoint(double& sp);
	int SetSetPoint(double sp);
	int GetConnected(long& temp);
	int GetCommPort(char *strcommport);
	int SetCommPort(long& commport);
    int GetVersion();

	int IsConnected();
	void UpdateGui();
    void UpdatePropertyGui(char *PropName, char *PropVal);

    void RefreshThread_Start();
    void RefreshThread_Stop();

	// action interface
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGetVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetConc(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
	int connected_;
    HmdControl_RefreshThread *rthread_;
};



class HmdControl_RefreshThread : public MMDeviceThreadBase
{
 public:
    HmdControl_RefreshThread(HmdControl &oDevice);
    ~HmdControl_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	HmdControl& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_#TEMPLATE#_CONTROL_
