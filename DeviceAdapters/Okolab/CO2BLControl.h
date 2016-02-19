///////////////////////////////////////////////////////////////////////////////
// FILE:          CO2BLControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab CO2BL  device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_CO2BL_CONTROL_
#define _OKOLAB_CO2BL_CONTROL_

class CO2BLControl_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   CO2BL  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class CO2BLControl: public OkolabDevice , public CGenericBase<CO2BLControl> 
{
 public:
	CO2BLControl();
	~CO2BLControl();
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
    CO2BLControl_RefreshThread *rthread_;
};



class CO2BLControl_RefreshThread : public MMDeviceThreadBase
{
 public:
    CO2BLControl_RefreshThread(CO2BLControl &oDevice);
    ~CO2BLControl_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	CO2BLControl& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_#TEMPLATE#_CONTROL_
