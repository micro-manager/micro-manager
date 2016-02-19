///////////////////////////////////////////////////////////////////////////////
// FILE:          CO2O2BLControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab CO2O2 BL device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_CO2O2BL_CONTROL_
#define _OKOLAB_CO2O2BL_CONTROL_

class CO2O2BLControl_RefreshThread;

//////////////////////////////////////////////////////////////////////////////
//   CO2-O2 BL  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class CO2O2BLControl: public OkolabDevice , public CGenericBase<CO2O2BLControl> 
{
 public:
	CO2O2BLControl();
	~CO2O2BLControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();
    MM::DeviceDetectionStatus DetectDevice(void);

	// internal API 
	bool WakeUp();

	int GetCO2Conc(double& conc);
	int GetO2Conc(double& conc);

	int GetCO2SetPoint(double& sp);
	int SetCO2SetPoint(double sp);

	int GetO2SetPoint(double& sp);
	int SetO2SetPoint(double sp);

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
	int OnGetCO2Conc(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetO2Conc(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetCO2SetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetCO2SetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetO2SetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetO2SetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
	int connected_;
    CO2O2BLControl_RefreshThread *rthread_;
};



class CO2O2BLControl_RefreshThread : public MMDeviceThreadBase
{
 public:
    CO2O2BLControl_RefreshThread(CO2O2BLControl &oDevice);
	 ~CO2O2BLControl_RefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	CO2O2BLControl& okoDevice_;
    bool stop_;
	int sleepmillis_;
};

#endif // _OKOLAB_CO2O2BL_CONTROL_
