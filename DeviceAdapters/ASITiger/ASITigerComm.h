///////////////////////////////////////////////////////////////////////////////
// FILE:          ASITigerComm.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI TIGER controller adapter
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.h, ASIFW1000.h, Arduino.h, and DemoCamera.h
//

#ifndef _ASITigerComm_H_
#define _ASITigerComm_H_

#include "ASIDevice.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"


///////////////////////////////////////////////////////
// ***** TIGER-specific *******************************
///////////////////////////////////////////////////////

class CTigerCommHub : public ASIHub
{
public:
   CTigerCommHub();
   ~CTigerCommHub();

   // Device API
   int Initialize();
   bool Busy();                               // we'll define say the Hub is busy when motors attached to it are running
   int Shutdown() { return ASIDevice::Shutdown(); }                      // probably OK to let this implementation stand
   void GetName(char* pszName) const { ASIDevice::GetName(pszName); }    // probably OK to let this implementation stand

   // Hub API
   MM::DeviceDetectionStatus DetectDevice();
   int DetectInstalledDevices();
   MM::Device* CTigerCommHub::CreatePeripheralDevice(const char* adapterName);

   // for use of other ASI methods
   string ConvertToTigerRawAddress(const string &s);  // returns single character but since could be extended ASCII keep in string

private:
   int TalkToTiger();
};



#endif //_ASITigerComm_H_
