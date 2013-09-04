///////////////////////////////////////////////////////////////////////////////
// FILE:          #TEMPLATE#Control.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab #TEMPLATE#  device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_#TEMPLATE#_CONTROL_
#define _OKOLAB_#TEMPLATE#_CONTROL_

class #TEMPLATE#Control_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   #TEMPLATE#  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class #TEMPLATE#Control: public OkolabDevice , public CGenericBase<#TEMPLATE#Control> 
{
 public:
	#TEMPLATE#Control();
	~#TEMPLATE#Control();
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

	int OnCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
	int connected_;
    #TEMPLATE#Control_RefreshThread *rthread_;
};



class #TEMPLATE#Control_RefreshThread : public MMDeviceThreadBase
{
 public:
    #TEMPLATE#Control_RefreshThread(#TEMPLATE#Control &oDevice);
	 ~#TEMPLATE#Control_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	#TEMPLATE#Control& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_#TEMPLATE#_CONTROL_
