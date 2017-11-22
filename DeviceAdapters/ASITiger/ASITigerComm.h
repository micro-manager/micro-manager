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

#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"


///////////////////////////////////////////////////////
// ***** TIGER-specific *******************************
///////////////////////////////////////////////////////

// Because the comm card is also a device in the TG-1000 system it would
// be nice if it also went through the ASIPeripheralBase class like the
// other device adapters (and then ASIPeripheralBase and ASIBase could be
// merged).  However, this was impossible because the other peripheral
// adapters need to store a pointer to the hub device.  One solution would
// be to remove ASIHub from the inheritance heirarchy and leave it as a
// utility class, but some of its core commands require it to inherit from
// HubBase in DeviceBase.h, at which point it would have to be merged
// with TigerComm to work.  So that refactoring path was abandoned for now.
// - Jon (14-May-2014)
class CTigerCommHub : public ASIHub
{
public:
   CTigerCommHub();
   ~CTigerCommHub() { }

   // Device API
   int Initialize();
   bool Busy();

   // Hub API
   bool SupportsDeviceDetection(void);
   MM::DeviceDetectionStatus DetectDevice();
   int DetectInstalledDevices();

private:
   int TalkToTiger();
};



#endif //_ASITigerComm_H_
