//////////////////////////////////////////////////////////////////////////////
// FILE:          NeoPixel.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino board controlling Adafruit NeoPixel
//                Needs accompanying firmware to be installed on the board
// COPYRIGHT:     University of California, San Francisco, 2018
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 09/18/2018
//


#ifndef _NeoPixel_H_
#define _NeoPixel_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

// Error codes
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109
class NeoPixelShutter : public CShutterBase<NeoPixelShutter>
{
public:
   NeoPixelShutter();
   ~NeoPixelShutter();

   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();
   
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnColor(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnAllActive(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnSelectPixel(MM::PropertyBase* pProp, MM::ActionType eAct);
 
private:                                                      
   int GetFirmwareVersion(int& version);
   MM::MMTime changedTime_;                                   
   bool open_;
   bool portAvailable_;
   bool initialized_;                                         
   std::string name_;                                         
   std::string port_;
   std::string color_;
   std::string activeState_;
   static MMThreadLock lock_;
   int version_;
};   


#endif
