///////////////////////////////////////////////////////////////////////////////
// FILE:       Ludl.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN bus adapater
//   

#ifndef _ZEISSCAN_H_
#define _ZEISSCAN_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004                                   
#define ERR_SET_POSITION_FAILED      10005                                   
#define ERR_INVALID_STEP_SIZE        10006                                   
#define ERR_LOW_LEVEL_MODE_FAILED    10007                                   
#define ERR_INVALID_MODE             10008 

class ZeissScope : public CGenericBase<ZeissScope>
{
   public:
      ZeissScope();
      ~ZeissScope();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      bool initialized_;
      double answerTimeoutMs_;
      std::string port_;
};

#endif // _ZeissCAN_H_
