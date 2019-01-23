///////////////////////////////////////////////////////////////////////////////
// FILE:          VarispecLCTF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   VarispecLCTF Polarization Adapter
//
//
// AUTHOR:        Rudolf Oldenbourg, MBL, w/ Arthur Edelstein and Karl Hoover, UCSF, Sept, Oct 2010
//				  modified Amitabh Verma Apr. 17, 2012
// COPYRIGHT:     
// LICENSE:       
//

#ifndef _VarispecLCTF_H_
#define _VarispecLCTF_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
#define ERR_PORT_CHANGE_FORBIDDEN 109

class VarispecLCTF : public CGenericBase<VarispecLCTF>
{
public:
	VarispecLCTF();
	~VarispecLCTF();

	// Device API
	// ---------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	

	//      int Initialize(MM::Device& device, MM::Core& core);
	int DeInitialize() {initialized_ = false; return DEVICE_OK;};
	bool Initialized() {return initialized_;};
	  
	// device discovery
	bool SupportsDeviceDetection(void);
	MM::DeviceDetectionStatus DetectDevice(void);

	// action interface
	// ---------------
	int OnDelay (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBaud	(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWavelength (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialNumber (MM::PropertyBase* pProp, MM::ActionType eAct);	  
	int OnSendToVarispecLCTF (MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGetFromVarispecLCTF (MM::PropertyBase* pProp, MM::ActionType eAct);
	  
private:
	// Command exchange with MMCore
	std::string port_;
	std::string baud_;
	bool initialized_;
	bool initializedDelay_;
	double answerTimeoutMs_;
	double wavelength_; // the cached value
	std::string serialnum_;
	std::string sendToVarispecLCTF_;
	std::string getFromVarispecLCTF_;
	MM::MMTime changedTime_;
	MM::MMTime delay_;
	std::vector<double> sequence_;

	int sendCmd(std::string cmd, std::string& out);	//Send a command and save the response in `out`.
	int sendCmd(std::string cmd);	//Send a command that does not repond with any extra information.
	int getStatus();
	bool reportsBusy();
};




#endif //_VarispecLCTF_H_
