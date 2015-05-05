///////////////////////////////////////////////////////////////////////////////
// FILE:          MaiTai.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters : TwoPhoton
//-----------------------------------------------------------------------------
// DESCRIPTION:   Virtual shutter class for controlling EOMs
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
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
//                
// AUTHOR:        Nenad Amodaj, Henry Pinkard
//                

#include "TwoPhoton.h"
#include <sstream>
#include "DeviceUtils.h"

using namespace std;

const char* g_VShutterDeviceName = "V2Shutter";

const char* g_PropertyShutter = "Shutter";

const char* g_PropertyDev1 = "DAC1";
const char* g_PropertyDev2 = "DAC2";

///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
VirtualShutter::VirtualShutter() : initialized_(false) {
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

}

VirtualShutter::~VirtualShutter()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void VirtualShutter::GetName(char* name) const {
   CDeviceUtils::CopyLimitedString(name, g_VShutterDeviceName);
}

/**
 * Intializes the hardware.
 */
int VirtualShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_VShutterDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for D/A channels", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // device 1
   ret = CreateProperty(g_PropertyDev1, "", MM::String, false);
   assert(ret == DEVICE_OK);

   // device 2
   ret = CreateProperty(g_PropertyDev2, "", MM::String, false);
   assert(ret == DEVICE_OK);

   CPropertyAction* pAct = new CPropertyAction (this, &VirtualShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}
int VirtualShutter::SetOpen(bool open) {
   char dev1[MM::MaxStrLength];
   GetProperty(g_PropertyDev1, dev1);

   char dev2[MM::MaxStrLength];
   GetProperty(g_PropertyDev2, dev2);

   if (strlen(dev1) > 0)
   {
      MM::SignalIO* pDev1 = GetCoreCallback()->GetSignalIODevice(this, dev1);
      int ret = pDev1->SetGateOpen(open);
      if (ret != DEVICE_OK)
         return ret;
   }

   if (strlen(dev2) > 0)
   {
      MM::SignalIO* pDev2 = GetCoreCallback()->GetSignalIODevice(this, dev2);
      int ret = pDev2->SetGateOpen(open);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


int VirtualShutter::GetOpen(bool& open) {
   char dev1[MM::MaxStrLength];
   GetProperty(g_PropertyDev1, dev1);

   char dev2[MM::MaxStrLength];
   GetProperty(g_PropertyDev2, dev2);

   bool open1(false);

   if (strlen(dev1) > 0)
   {
      MM::SignalIO* pDev1 = GetCoreCallback()->GetSignalIODevice(this, dev1);
      if (pDev1)
      {
         int ret = pDev1->GetGateOpen(open1);
         if (ret != DEVICE_OK)
            return ret;
      }
      else
         return ERR_UNKNOWN_DA_DEVICE;
   }

   bool open2;

   if (strlen(dev2) > 0)
   {
      MM::SignalIO* pDev2 = GetCoreCallback()->GetSignalIODevice(this, dev2);
      if (pDev2)
      {
         int ret = pDev2->GetGateOpen(open2);
         if (ret != DEVICE_OK)
            return ret;
      }
      else
         return ERR_UNKNOWN_DA_DEVICE;
   }
   else
      open2 = open1;


   assert(open1 == open2);
   open = open1;

   return DEVICE_OK;
}


int VirtualShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetOpen(open);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(open ? "1" : "0");
   }
   else if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare("1") == 0)
         return SetOpen(true);
      else
         return SetOpen(false);
   }

   return DEVICE_OK;
}
