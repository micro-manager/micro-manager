///////////////////////////////////////////////////////////////////////////////
// FILE:       Vincent.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Vincent Uniblitz controller adapter
//                
// AUTHOR: Nico Stuurman, 02/27/2006
//
//

#ifndef _CONIX_H_
#define _CONIX_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_COMMAND          10002
#define ERR_UNKNOWN_POSITION         10003
#define ERR_HALT_COMMAND             10004
#define ERR_UNRECOGNIZED_ANSWER      10005
#define ERR_OFFSET                   11000

class QuadFluor : public CStateDeviceBase<QuadFluor>
{
public:
   QuadFluor();
   ~QuadFluor();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

    

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetPosition(int& position);
   int SetPosition(int position);
   int ExecuteCommand(const std::string& cmd);

   bool initialized_;
   unsigned numPos_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
   // Has a command been sent to which no answer has been received yet?
   bool pendingCommand_;
};


#endif //_CONIX_H_
