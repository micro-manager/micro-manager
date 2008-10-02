///////////////////////////////////////////////////////////////////////////////
// FILE:          DAZShutter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter to make a DA device act like a shutter (useful for diode lasers)
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 09/29/2008
// COPYRIGHT:     University of California, San Francisco, 2008
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

#ifndef _DASHUTTER_H_
#define _DASHUTTER_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_DEVICE_NAME            10001
#define ERR_NO_DA_DEVICE                   10002
#define ERR_VOLT_OUT_OF_RANGE              10003
#define ERR_POS_OUT_OF_RANGE               10004
#define ERR_NO_DA_DEVICE_FOUND             10005

class DAShutter : public CShutterBase<DAShutter>
{
public:
   DAShutter();
   ~DAShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName_;
   MM::SignalIO* DADevice_;
   bool initialized_;
};

#endif //_DASHUTTER_H_
