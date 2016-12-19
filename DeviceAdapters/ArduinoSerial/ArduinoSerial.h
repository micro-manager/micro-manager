 //////////////////////////////////////////////////////////////////////////////
// FILE:          Arduino.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino board
//                Needs accompanying firmware to be installed on the board
// COPYRIGHT:     University of California, Berkeley, 2016
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016  
//
//

#ifndef _Arduino_H_
#define _Arduino_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109


class CArduinoSerial: public  CDeviceBase<MM::Generic, CArduinoSerial>
{
public:
   CArduinoSerial();
   ~CArduinoSerial();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();

   bool Busy();
   void GetName(char *) const;

   // action interface
   // ----------------
      int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
	        int OnSerialCommand(MM::PropertyBase* pPropt, MM::ActionType eAct);


private:
   
	   MM::MMTime changedTime_;
   bool initialized_;
   std::string name_;
   std::string port_;
   std::string lastCommand_;
   bool portAvailable_;
      MMThreadLock lock_;
	     bool timedOutputActive_;

	bool IsPortAvailable() {return portAvailable_;}
	int WriteToPort(long lnValue);
	void SetTimedOutput(bool active) {timedOutputActive_ = active;}
	
   	 MMThreadLock& GetLock() {return lock_;}



};

#endif //_Arduino_H_
