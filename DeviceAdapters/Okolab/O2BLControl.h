///////////////////////////////////////////////////////////////////////////////
// FILE:          O2BLControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab O2 BL device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_O2BL_CONTROL_
#define _OKOLAB_O2BL_CONTROL_

class O2BLControl_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   O2BL  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class O2BLControl: public OkolabDevice , public CGenericBase<O2BLControl> 
{
 public:
	O2BLControl();
	~O2BLControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();
    MM::DeviceDetectionStatus DetectDevice(void);

	// internal API 
	bool WakeUp();
	int GetConc(double& temp);
	int GetSetPoint(double& sp);
	int SetSetPoint(double sp);
	int GetConnected(long& temp);
	int GetCommPort(char *strcomport);
	int SetCommPort(long& comport);
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
    int OnCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
	int connected_;
    O2BLControl_RefreshThread *rthread_;
};



class O2BLControl_RefreshThread : public MMDeviceThreadBase
{
 public:
    O2BLControl_RefreshThread(O2BLControl &oDevice);
	 ~O2BLControl_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	O2BLControl& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_O2BL_CONTROL_
