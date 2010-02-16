///////////////////////////////////////////////////////////////////////////////
// FILE:          AOTF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   
//
// AUTHOR:        Lukas Kapitein / Erwin Peterman 24/08/2009


#ifndef _AOTF_H_
#define _AOTF_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_COMMAND_ERROR            10201
#define ERR_PARAMETER_ERROR          10202
#define ERR_RECEIVE_BUFFER_OVERFLOW  10204
#define ERR_COMMAND_OVERFLOW         10206
#define ERR_PROCESSING_INHIBIT       10207
#define ERR_PROCESSING_STOP_ERROR    10208

#define ERR_OFFSET 10100
#define ERR_AOTF_OFFSET 10200
#define ERR_INTENSILIGHTSHUTTER_OFFSET 10300



class AOTF : public CShutterBase<AOTF>
{
public:
   AOTF();
   ~AOTF();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   // ---------
   int SetOpen(bool open);
   int GetOpen(bool& open);
   int Fire(double /*interval*/) {return DEVICE_UNSUPPORTED_COMMAND; }


   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);

private:

   int SetIntensity(int intensity);
	
   int SetShutterPosition(bool state);
   //int GetVersion();

   // MMCore name of serial port
   std::string port_;
   // Time it takes after issuing Close command to close the shutter         
   double closingTimeMs_;                                                    
   // Time it takes after issuing Open command to open the shutter           
   double openingTimeMs_;                                                    
   // Command exchange with MMCore                                           
   std::string command_;           
   // close (0) or open (1)
   int state_;
   bool initialized_;
   // channel that we are currently working on 
   std::string activeChannel_;
   // version string returned by device
   //std::string version_;
   double answerTimeoutMs_;
   //intensity
   int intensity_;
   
};

#endif //_AOTF_H_