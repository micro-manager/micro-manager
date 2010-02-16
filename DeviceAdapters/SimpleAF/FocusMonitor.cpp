///////////////////////////////////////////////////////////////////////////////
// FILE:          FocusMonitor.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ImageProcessor device for monitoring focusing score
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, February 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010, http://www.100ximaging.com
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "SimpleAF.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <cmath>
#include <sstream>
#include <ctime>


using namespace std;

extern const char* g_FocusMonitorDeviceName;


FocusMonitor::FocusMonitor() : 
   initialized_(false), pAFDevice_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   
}

FocusMonitor::~FocusMonitor()
{
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void FocusMonitor::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_FocusMonitorDeviceName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool FocusMonitor::Busy()
{
   return false;
}

/**
 * Intializes the module.
 * Required by the MM::Device API.
 */
int FocusMonitor::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_FocusMonitorDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Focus score real-time monitoring", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // stdDev
   CPropertyAction *pAct = new CPropertyAction (this, &FocusMonitor::OnAFDevice);
   nRet = CreateProperty("AFDevice", "", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 */
int FocusMonitor::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int FocusMonitor::Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth)
{
   // TODO: calculate score

   return DEVICE_OK;
}

int FocusMonitor::OnAFDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(afDevice_);
      pAFDevice_ = 0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(afDevice_.c_str());
   }

   return DEVICE_OK; 
}
