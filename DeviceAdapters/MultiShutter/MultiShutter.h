///////////////////////////////////////////////////////////////////////////////
// FILE:          MultiShutter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter to combined multiple physical shutters into on logical shutter
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 10/02/2008
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

#ifndef _MULTISHUTTER_H_
#define _MULTISHUTTER_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_DEVICE_NAME            10001

class MultiShutter : public CShutterBase<MultiShutter>
{
public:
   MultiShutter();
   ~MultiShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open) {open = open_; return DEVICE_OK;}
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnPhysicalShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

private:
   std::vector<std::string> availableShutters_;
   std::vector<std::string> usedShutters_;
   std::vector<MM::Shutter*> physicalShutters_;
   long nrPhysicalShutters_;
   bool open_;
   bool initialized_;
};

#endif //_MULTISHUTTER_H_
