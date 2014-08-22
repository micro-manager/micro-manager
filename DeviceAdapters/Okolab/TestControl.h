///////////////////////////////////////////////////////////////////////////////
// FILE:          TestControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab Test Control adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_TEST_CONTROL_
#define _OKOLAB_TEST_CONTROL_

class TestRefreshThread;

//////////////////////////////////////////////////////////////////////////////
//  T e s t   C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////


//class TestControl: public OkolabDevice, public CCameraBase<TestControl>
class TestControl: public CGenericBase<TestControl>
{
 public:
	TestControl();
	~TestControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();

	// internal API 
	bool WakeUp();
	int GetRand(double& rndnum);
	int TestAction(char *straction);

    void RefreshThread_Start();
    void RefreshThread_Stop();

	void UpdateGui();
	void UpdatePropertyGui(double new_val);

	// action interface
    int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnGetRand(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnTestAction(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
    TestRefreshThread *mthread_;
    bool initialized_;
	std::string port_;
};



class TestRefreshThread : public MMDeviceThreadBase
{
 public:
    TestRefreshThread(TestControl &oDevice);
    ~TestRefreshThread();

	int svc();
	void Start();
    void Stop() {stop_=true;}

private:
	TestControl& okoDevice_;
    bool stop_;
};




#endif //_OKOLAB_TEST_CONTROL_
