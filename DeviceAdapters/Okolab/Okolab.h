///////////////////////////////////////////////////////////////////////////////
// FILE:          Okolab.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device adapter
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       BSD
//
//

#ifndef _OKOLAB_H_
#define _OKOLAB_H_

#define _VERSION_ "20130910"

#define _LOG_ 1 // Set this to 0 to disable logging
//#define _DEBUG_ // Undefine this to disable debbugging features 

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE			 10001
#define ERR_PORT_CHANGE_FORBIDDEN    10002
#define ERR_DETECT_FAILED            10003


//////////////////////////////////////////////////////////////////////////////
//  Okolab   G e n e r i c   D e v i c e    A d a p t e r
//////////////////////////////////////////////////////////////////////////////


class OkolabDevice
{
 public:
	OkolabDevice();
	~OkolabDevice();

    int GetProductId();
	void SetProductId(int id);
    int GetDeviceId();
	void SetDeviceId(int id);


    bool OCSConnectionOpen(unsigned short PortNo, char* IPAddress);
    void OCSConnectionClose();
    bool OCSSendRcvdCommand(char *cmd, char *par1, char *par2, char *par3);
	int  OCSGetLastAnswerError();
    bool OCSRunning();
    void OCSStart();
    int  OCSStop();

	int IsDeviceConnected(int product_id); 
    int IsDeviceWorking(int product_id);

	int GetCommPort(char *strcommport);
    int SetCommPort(int port);

	int GetValue(double& val);

	int GetSetPoint(double& sp);
    int SetSetPoint(double sp);

    int TryConnectDevice(int iport);

    MM::DeviceDetectionStatus Detect(void);

 protected:
    int product_id;
    int device_id;

	SOCKET s; 
	char* ipaddress_;
    unsigned short ipport_;

	int rcv_statuscode;
    char rcv_answer[51];

    std::string version_;

    bool initialized_;
	std::string port_;

 private:
    char last_sent_cmd[51];
	int last_error_ncode; // number of last error (IE: if error='e12' then last_error_ncode=12);
};



#endif //_OKOLAB_H_
