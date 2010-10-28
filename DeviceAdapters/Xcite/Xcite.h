///////////////////////////////////////////////////////////////////////////////
// FILE:          Xcite.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   EXFO X-Cite 120 PC controller adaptor 
// COPYRIGHT:     INRIA Rocquencourt, France
//                University Paris Diderot, France, 2010 
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
// AUTHOR:        Jannis Uhlendorf (jannis.uhlendorf@inria.fr) 2010
//                This code is based on the Vincent Uniblitz controller adapter
//                by Nico Stuurman, 2006


#ifndef _XCITE_H_
#define _XCITE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
#define ERR_PORT_CHANGE_FORBIDDEN    10004



class Xcite120PC : public CShutterBase<Xcite120PC>
{
public:
   Xcite120PC();
   ~Xcite120PC();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPanelLock(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLampState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   int ExecuteCommand( const std::string& cmd, char* input=NULL, int input_len=0, std::string* ret=NULL); 

   bool initialized_;
   // MMCore name of serial port
   std::string port_;
   bool is_open_;
   std::string lamp_intensity_;
   std::string is_locked_;
   std::string lamp_state_;
   double exposure_time_s_;
};

#endif //_XCITESHUTTER_H_
