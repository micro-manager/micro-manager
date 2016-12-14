// Micro-Manager device adapter for VisiTech iSIM
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; version 2.1.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida <mark@open-imaging.com>

#include "VTiSIM.h"

#include "ModuleInterface.h"

#include <VisiSDK.h>


const char* const g_DeviceName_Hub = "VTiSIMHub";


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceName_Hub, MM::HubDevice, "VT-iSIM system");
}


MODULE_API MM::Device* CreateDevice(const char* name)
{
   if (!name)
      return 0;

   if (strcmp(name, g_DeviceName_Hub) == 0)
      return new VTiSIMHub();

   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


VTiSIMHub::VTiSIMHub() :
   hAotfControl(0),
   hScanAndMotorControl(0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(VTI_ERR_TIMEOUT_OCCURRED, "Timeout occurred");
   SetErrorText(VTI_ERR_DEVICE_NOT_FOUND, "Device not found");
   SetErrorText(VTI_ERR_NOT_INITIALISED, "Device not initialized, or initialization failed");
   SetErrorText(VTI_ERR_ALREADY_INITIALISED, "Device already initialized");
}


VTiSIMHub::~VTiSIMHub()
{
}


int VTiSIMHub::Initialize()
{
   if (!hAotfControl)
   {
      DWORD err = vti_Initialise(VTI_HARDWARE_AOTF_USB, &hAotfControl);
      if (err != VTI_SUCCESS)
         return err;
   }

   if (!hScanAndMotorControl)
   {
      DWORD err = vti_Initialise(VTI_HARDWARE_VTINFINITY_4, &hScanAndMotorControl);
      if (err != VTI_SUCCESS)
         return err;
   }

   return DEVICE_OK;
}


int VTiSIMHub::Shutdown()
{
   int lastErr = DEVICE_OK;

   if (hScanAndMotorControl)
   {
      DWORD err = vti_UnInitialise(&hScanAndMotorControl);
      if (err != VTI_SUCCESS)
      {
         lastErr = err;
      }
   }

   if (hAotfControl)
   {
      DWORD err = vti_UnInitialise(&hAotfControl);
      if (err != VTI_SUCCESS)
      {
         lastErr = err;
      }
   }

   return lastErr;
}


void VTiSIMHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_Hub);
}


bool VTiSIMHub::Busy()
{
   return false;
}


int VTiSIMHub::DetectInstalledDevices()
{
   ClearInstalledDevices();
   return DEVICE_OK;
}