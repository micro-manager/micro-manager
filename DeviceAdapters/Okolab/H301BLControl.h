///////////////////////////////////////////////////////////////////////////////
// FILE:          H201BLControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab H301 T BL  device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_H301BL_CONTROL_
#define _OKOLAB_H301BL_CONTROL_

class H301BLControl_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   H301BL   C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class H301BLControl: public OkolabDevice , public CGenericBase<H301BLControl> 
{
 public:
	H301BLControl();
	~H301BLControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();
    MM::DeviceDetectionStatus DetectDevice(void);

	// internal API 
	bool WakeUp();
	int GetTemp(double& temp);
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
	int OnGetTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
	int connected_;
    H301BLControl_RefreshThread *rthread_;
};



class H301BLControl_RefreshThread : public MMDeviceThreadBase
{
 public:
    H301BLControl_RefreshThread(H301BLControl &oDevice);
    ~H301BLControl_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	H301BLControl& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_H301BL_CONTROL_
