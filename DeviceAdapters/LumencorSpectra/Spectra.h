///////////////////////////////////////////////////////////////////////////////
// FILE:          Spectra.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Lumencor Light Engine driver
//                Spectra
//
// AUTHOR:        Louis Ashford
// COPYRIGHT:     Ashford Solutions LLC
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifndef _SPECTRA_H_
#define _SPECTRA_H_

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
#define ERR_Lumencor_OFFSET 10200

enum ColorNameT {VIOLET,CYAN,GREEN,RED,BLUE,TEAL,WHITE,ALL,YGFILTER,SHUTTER};


class Spectra : public CShutterBase<Spectra>
{
public:
   Spectra();
   ~Spectra();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   // ---------
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double /*interval*/) {return DEVICE_UNSUPPORTED_COMMAND; }

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetLE_Type(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInitLE(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnRedEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGreenEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCyanEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVioletEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTealEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlueEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteEnable(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnYGFilterEnable(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnRedValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGreenValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCyanValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVioletValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTealValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlueValue(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteValue(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetShutterPosition(bool state);
   int GetVersion();
   int SendColorLevelCmd(ColorNameT ColorName, int ColorLevel);
   int SendColorEnableCmd(ColorNameT ColorName, bool State, char* EnableMask);
   int InitLE();

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
   std::string version_;
   double answerTimeoutMs_;
   // Current Color Selected
   std::string ActiveColor_;
   
};


#endif //_SPECTRA_H_
