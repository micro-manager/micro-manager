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
const char* const g_DeviceName_LaserShutter = "LaserShutter";
const char* const g_DeviceName_Lasers = "Lasers";


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
   if (strcmp(name, g_DeviceName_LaserShutter) == 0)
      return new VTiSIMLaserShutter();
   if (strcmp(name, g_DeviceName_Lasers) == 0)
      return new VTiSIMLasers();

   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


VTiSIMHub::VTiSIMHub() :
   hAotfControl_(0),
   hScanAndMotorControl_(0)
{
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
   if (!hAotfControl_)
   {
      DWORD err = vti_Initialise(VTI_HARDWARE_AOTF_USB, &hAotfControl_);
      if (err != VTI_SUCCESS)
         return err;
   }

   if (!hScanAndMotorControl_)
   {
      DWORD err = vti_Initialise(VTI_HARDWARE_VTINFINITY_4, &hScanAndMotorControl_);
      if (err != VTI_SUCCESS)
         return err;
   }

   return DEVICE_OK;
}


int VTiSIMHub::Shutdown()
{
   int lastErr = DEVICE_OK;

   if (hScanAndMotorControl_)
   {
      DWORD err = vti_UnInitialise(&hScanAndMotorControl_);
      if (err != VTI_SUCCESS)
      {
         lastErr = err;
      }
   }

   if (hAotfControl_)
   {
      DWORD err = vti_UnInitialise(&hAotfControl_);
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
   MM::Device* pDev = new VTiSIMLaserShutter();
   if (pDev)
      AddInstalledDevice(pDev);
   pDev = new VTiSIMLasers();
   if (pDev)
      AddInstalledDevice(pDev);
   return DEVICE_OK;
}


VTiSIMLaserShutter::VTiSIMLaserShutter() :
   isOpen_(false)
{
   SetErrorText(VTI_ERR_TIMEOUT_OCCURRED, "Timeout occurred");
   SetErrorText(VTI_ERR_DEVICE_NOT_FOUND, "Device not found");
   SetErrorText(VTI_ERR_NOT_INITIALISED, "Device not initialized");
   SetErrorText(VTI_ERR_NOT_SUPPORTED, "Operation not supported");
   SetErrorText(VTI_ERR_INCORRECT_MODE, "AOTF is in manual mode");
}


VTiSIMLaserShutter::~VTiSIMLaserShutter()
{
}


int VTiSIMLaserShutter::Initialize()
{
   int err = CreateIntegerProperty(MM::g_Keyword_State, 0, false,
      new CPropertyAction(this, &VTiSIMLaserShutter::OnState));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(MM::g_Keyword_State, 0, 1);
   if (err != DEVICE_OK)
      return err;

   // Sync with our memory of state
   return DoSetOpen(isOpen_);
}


int VTiSIMLaserShutter::Shutdown()
{
   // Always turn off on shutdown
   return SetOpen(false);
}


void VTiSIMLaserShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_LaserShutter);
}


bool VTiSIMLaserShutter::Busy()
{
   return false;
}


int VTiSIMLaserShutter::GetOpen(bool& open)
{
   open = isOpen_;
   return DEVICE_OK;
}


int VTiSIMLaserShutter::SetOpen(bool open)
{
   if (open == isOpen_)
      return DEVICE_OK;
   return DoSetOpen(open);
}


int VTiSIMLaserShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(isOpen_ ? 1L : 0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      return SetOpen(v != 0);
   }
   return DEVICE_OK;
}


VTiSIMHub* VTiSIMLaserShutter::VTiHub()
{
   return static_cast<VTiSIMHub*>(GetParentHub());
}


int VTiSIMLaserShutter::DoSetOpen(bool open)
{
   DWORD err = vti_SetShutter(VTiHub()->GetAOTFHandle(), open);
   if (err != VTI_SUCCESS)
      return err;
   isOpen_ = open;
   return DEVICE_OK;
}


VTiSIMLasers::VTiSIMLasers() :
   curChan_(0)
{
   memset(intensities_, 0, sizeof(intensities_));

   SetErrorText(VTI_ERR_TIMEOUT_OCCURRED, "Timeout occurred");
   SetErrorText(VTI_ERR_DEVICE_NOT_FOUND, "Device not found");
   SetErrorText(VTI_ERR_NOT_INITIALISED, "Device not initialized");
   SetErrorText(VTI_ERR_NOT_SUPPORTED, "Operation not supported");
   SetErrorText(VTI_ERR_INCORRECT_MODE, "AOTF is in manual mode");
}


VTiSIMLasers::~VTiSIMLasers()
{
}


int VTiSIMLasers::Initialize()
{
   char s[MM::MaxStrLength];
   for (long i = 0; i < nChannels; ++i)
   {
      snprintf(s, MM::MaxStrLength, "Laser-%ld", i);
      SetPositionLabel(i, s);
   }

   int err = CreateIntegerProperty(MM::g_Keyword_State, 0, false,
      new CPropertyAction(this, &VTiSIMLasers::OnState));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(MM::g_Keyword_State, 0, nChannels - 1);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MM::g_Keyword_Label, "", false,
      new CPropertyAction(this, &CStateBase::OnLabel));
   if (err != DEVICE_OK)
      return err;

   for (long i = 0; i < nChannels; ++i)
   {
      snprintf(s, MM::MaxStrLength, "Intensity-%ld", i);
      err = CreateIntegerProperty(s, intensities_[i], false,
         new CPropertyActionEx(this, &VTiSIMLasers::OnIntensity, i));
      if (err != DEVICE_OK)
         return err;
      err = SetPropertyLimits(s, 0, 100);
      if (err != DEVICE_OK)
         return err;
   }

   // Sync with our memory of state
   return DoSetChannel(curChan_);
}


int VTiSIMLasers::Shutdown()
{
   return DEVICE_OK;
}


void VTiSIMLasers::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_Lasers);
}


bool VTiSIMLasers::Busy()
{
   return false;
}


unsigned long VTiSIMLasers::GetNumberOfPositions() const
{
   return nChannels;
}


int VTiSIMLasers::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(curChan_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == curChan_)
         return DEVICE_OK;
      return DoSetChannel(v);
   }
   return DEVICE_OK;
}


int VTiSIMLasers::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long chan)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(intensities_[chan]));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == intensities_[chan])
         return DEVICE_OK;
      return DoSetIntensity(chan, v);
   }
   return DEVICE_OK;
}


VTiSIMHub* VTiSIMLasers::VTiHub()
{
   return static_cast<VTiSIMHub*>(GetParentHub());
}


int VTiSIMLasers::DoSetChannel(int chan)
{
   if (chan < 0 || chan >= nChannels)
      return DEVICE_ERR; // Shouldn't happen

   DWORD err = vti_SetTTLBitmask(VTiHub()->GetAOTFHandle(), 1 << chan);
   if (err != VTI_SUCCESS)
      return err;
   curChan_ = chan;
   return DEVICE_OK;
}


int VTiSIMLasers::DoSetIntensity(int chan, int percentage)
{
   if (chan < 0 || chan >= nChannels)
      return DEVICE_ERR; // Shouldn't happen

   if (percentage < 0) // Shouldn't happen
      percentage = 0;
   if (percentage > 100) // Shouldn't happen
      percentage = 100;

   DWORD err = vti_SetIntensity(VTiHub()->GetAOTFHandle(), chan, percentage);
   if (err != VTI_SUCCESS)
      return err;
   intensities_[chan] = percentage;
   return DEVICE_OK;
}