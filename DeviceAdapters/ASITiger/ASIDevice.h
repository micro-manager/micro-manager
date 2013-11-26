///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIDevice.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI generic device class, MODULE_API items, and common defines
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
// BASED ON:      ASIStage.h
//

#ifndef _ASIDevice_H_
#define _ASIDevice_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "ASITiger.h"
#include <string>

using namespace std;

// important forward declarations
class ASIDevice; // forward declaration of friend class defined in this file
class ASIHub;    // forward declaration of Hub class which the ASIDevice class stores a pointer to

////////////////////////////////////////////////////////////////
// *********** generic ASI device class *************************
// purpose to to take care of all common/generic ASI functionality
// define ASIDeviceBase which has friend ASIDevice so we can access methods
//   of CDeviceBase (e.g. property functions) without multiple inheritance
// use ASIDevice as parent of other classes
////////////////////////////////////////////////////////////////
class ASIDeviceBase : public CDeviceBase<MM::Device, ASIDeviceBase>
{
public:
   ASIDeviceBase() { }
   ~ASIDeviceBase() { }

   // use friend so we can have classes that inherit from ASIDevice and CXYStageBase or similar (DeviceBase.h)
   friend class ASIDevice;
};

class ASIDevice
{
public:
   ASIDevice(MM::Device *device, const char* name);
   virtual ~ASIDevice() { Shutdown(); }  // virtual destructor required for abstract class

   // Device API
   int Initialize(bool skipFirmware = false);       // should be implemented in child class as well as here, similar to a constructor
   int Shutdown();                        // probably OK to let this implementation stand
   void GetName(char* pszName) const;     // probably OK to let this implementation stand
   bool Busy() { return false; }          // should be implemented in child class

protected:
   ASIDeviceBase *deviceASI_;    // used to call functions in CDeviceBase (e.g. property methods)
   string addressChar_;    // address within hub, in single character (possibly extended ASCII)

   bool initialized_;      // used to signal that device properties have been read from controller
   bool refreshProps_;
   double firmwareVersion_;
   ASIHub *hub_;           // used for serial communication
   int ret_;               // return code for use with Micro-manager functions
   string addressString_;  // address within hub, in hex format

   void InitializeASIErrorMessages();

   // related to creating "extended" names containing address and axis letters
   bool IsExtendedName(const char* name) const;
   string GetAxisLetterFromExtName(const char* name, int position = 0) const;
   string GetHexAddrFromExtName(const char* name) const;

private:
   string ConvertToTigerRawAddress(const string &s) const;
};


#endif //_ASIDevice_H_
