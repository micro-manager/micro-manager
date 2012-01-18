///////////////////////////////////////////////////////////////////////////////
// FILE:          Rapp.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Rapp UGA40 adapter
// COPYRIGHT:     University of California, San Francisco, 2012
//                All rights reserved
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
//
// AUTHOR:        Arthur Edelstein
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "Rapp.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>


#include <iostream>
using namespace std;

const char* g_RappScannerName = "RappScanner";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_RappScannerName, "Rapp Scanner");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_RappScannerName) == 0)
   {
      RappScanner* s = new RappScanner();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MM::DeviceDetectionStatus RappScannerDetect(MM::Device& device, MM::Core& core, std::string portToCheck, double answerTimeoutMs)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
  
   return result;
}


///////////////////////////////////////////////////////////////////////////////
// RappScanner
//
RappScanner::RappScanner() :
   initialized_(false), port_(""), calibrationMode_(1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_RappScannerName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Rapp UGA-40 galvo phototargeting adapter", MM::String, true);
}

RappScanner::~RappScanner()
{
   Shutdown();
}

void RappScanner::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_RappScannerName);
}


MM::DeviceDetectionStatus RappScanner::DetectDevice(void)
{

   
   return MM::Misconfigured;
}

int RappScanner::Initialize()
{
   UGA_ = new obsROE_Device();
   tStringList devs = UGA_->SearchDevices();
   if (devs.size() < 1)
      return DEVICE_NOT_CONNECTED;

   port_ = devs[0];

   UGA_->Connect(port_.c_str());
   if (UGA_->IsConnected()) {
      initialized_ = true;
   } else {
      initialized_ = false;
      return DEVICE_NOT_CONNECTED;
   }

   
   CPropertyAction* pAct = new CPropertyAction(this, &RappScanner::OnCalibrationMode);
   CreateProperty("CalibrationMode", "1", MM::Integer, false, pAct);
   AddAllowedValue("CalibrationMode", "1");
   AddAllowedValue("CalibrationMode", "0");
   
}

int RappScanner::Shutdown()
{
   int result = DEVICE_NOT_CONNECTED;
   if (initialized_)
   {
      if (UGA_->IsConnected()) {
         if (UGA_->Disconnect()) {
            initialized_ = false;
            result = DEVICE_OK;
         }
      }
   }
   if (UGA_ != NULL)
      delete UGA_;
   return result;
}

bool RappScanner::Busy()
{
   return false;
}

int RappScanner::OnCalibrationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int result = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(calibrationMode_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(calibrationMode_);
      if (UGA_->SetCalibrationMode(calibrationMode_ ? 1 : 0, false)) {
         result = DEVICE_OK;
      } else {
         result = DEVICE_NOT_CONNECTED;
      }
   }
   
   return result;
}