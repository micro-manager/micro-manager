///////////////////////////////////////////////////////////////////////////////
// FILE:          OCSControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab OCS device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_OCS_CONTROL_
#define _OKOLAB_OCS_CONTROL_

//////////////////////////////////////////////////////////////////////////////
//   OCS  C o n t r o l    D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////

class OCSControl: public OkolabDevice , public CGenericBase<OCSControl> 
{
 public:
	OCSControl();
	~OCSControl();
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
    bool Busy();

	// internal API 
	bool WakeUp();
	int GetStatus();
	int SetStatus(int status);
    int GetVersion();

	// action interface
    int OnGetVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
};

#endif // _OKOLAB_OCS_CONTROL_
