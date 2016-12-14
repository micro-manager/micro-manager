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

#include <algorithm>
#include <utility>
#include <vector>


const char* const g_DeviceName_Hub = "VTiSIMHub";
const char* const g_DeviceName_LaserShutter = "LaserShutter";
const char* const g_DeviceName_Lasers = "Lasers";
const char* const g_DeviceName_Scanner = "Scanner";
const char* const g_DeviceName_PinholeArray = "PinholeArray";

const char* const g_PropName_Scanning = "Scanning";
const char* const g_PropName_ScanRate = "Scan Rate (Hz)";
const char* const g_PropName_ScanWidth = "Scan Width";
const char* const g_PropName_ScanOffset = "Scan Offset";
const char* const g_PropName_ActualRate = "Actual Scan Rate (Hz)";
const char* const g_PropName_FinePosition = "Fine Step Position";
const char* const g_PropName_PinholeSize = "Pinhole Size (um)";
const char* const g_PropVal_Off = "Off";
const char* const g_PropVal_On = "On";


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
   if (strcmp(name, g_DeviceName_Scanner) == 0)
      return new VTiSIMScanner();
   if (strcmp(name, g_DeviceName_PinholeArray) == 0)
      return new VTiSIMPinholeArray();

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

   LONG major, minor, rev, build;
   DWORD err = vti_GetDllVersionInfo(&major, &minor, &rev, &build);
   if (err == VTI_SUCCESS)
   {
      char s[MM::MaxStrLength];
      snprintf(s, MM::MaxStrLength, "%d.%d.%d.%d",
         (int)major, (int)minor, (int)rev, (int)build);
      int err = CreateStringProperty("DLLVersion", s, true);
      if (err != DEVICE_OK)
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

   pDev = new VTiSIMScanner();
   if (pDev)
      AddInstalledDevice(pDev);

   pDev = new VTiSIMPinholeArray();
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

   int mmerr = OnPropertyChanged(MM::g_Keyword_State, open ? "1" : "0");
   if (mmerr != DEVICE_OK)
      return mmerr;

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


VTiSIMScanner::VTiSIMScanner() :
   minRate_(1),
   maxRate_(1000),
   minWidth_(0),
   maxWidth_(4095),
   scanRate_(150),
   scanWidth_(1600),
   scanOffset_(0), // 0 is always an allowed offset
   actualRate_(0.0f)
{
   SetErrorText(VTI_ERR_TIMEOUT_OCCURRED, "Timeout occurred");
   SetErrorText(VTI_ERR_DEVICE_NOT_FOUND, "Device not found");
   SetErrorText(VTI_ERR_NOT_INITIALISED, "Device not initialized");
   SetErrorText(VTI_ERR_NOT_SUPPORTED, "Operation not supported");
}


VTiSIMScanner::~VTiSIMScanner()
{
}


int VTiSIMScanner::Initialize()
{
   int err = CreateStringProperty(g_PropName_Scanning, g_PropVal_Off, false,
      new CPropertyAction(this, &VTiSIMScanner::OnStartStop));
   if (err != DEVICE_OK)
      return err;
   err = AddAllowedValue(g_PropName_Scanning, g_PropVal_Off);
   if (err != DEVICE_OK)
      return err;
   err = AddAllowedValue(g_PropName_Scanning, g_PropVal_On);
   if (err != DEVICE_OK)
      return err;

   DWORD vterr = vti_GetScanRateRange(VTiHub()->GetScanAndMotorHandle(),
      &minRate_, &maxRate_);
   if (vterr != VTI_SUCCESS)
      return vterr;
   if (scanRate_ < minRate_)
      scanRate_ = minRate_;
   if (scanRate_ > maxRate_)
      scanRate_ = maxRate_;
   err = CreateIntegerProperty(g_PropName_ScanRate, scanRate_, false,
      new CPropertyAction(this, &VTiSIMScanner::OnScanRate));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(g_PropName_ScanRate, minRate_, maxRate_);
   if (err != DEVICE_OK)
      return err;

   vterr = vti_GetScanWidthRange(VTiHub()->GetScanAndMotorHandle(),
      &minWidth_, &maxWidth_);
   if (vterr != VTI_SUCCESS)
      return vterr;
   if (scanWidth_ < minWidth_)
      scanWidth_ = minWidth_;
   if (scanWidth_ > maxWidth_)
      scanWidth_ = maxWidth_;
   err = CreateIntegerProperty(g_PropName_ScanWidth, scanWidth_, false,
      new CPropertyAction(this, &VTiSIMScanner::OnScanWidth));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(g_PropName_ScanWidth, minWidth_, maxWidth_);
   if (err != DEVICE_OK)
      return err;

   err = CreateFloatProperty(g_PropName_ActualRate, actualRate_, true,
      new CPropertyAction(this, &VTiSIMScanner::OnActualScanRate));
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(g_PropName_ScanOffset, scanOffset_, false,
      new CPropertyAction(this, &VTiSIMScanner::OnScanOffset));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(g_PropName_ScanOffset, 0, GetMaxOffset());
   if (err != DEVICE_OK)
      return err;
   
   return DoStartStopScan(false);
}


int VTiSIMScanner::Shutdown()
{
   return DoStartStopScan(false);
}


void VTiSIMScanner::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_Scanner);
}


bool VTiSIMScanner::Busy()
{
   return false;
}


int VTiSIMScanner::OnScanRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(scanRate_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == scanRate_)
         return DEVICE_OK;
      return DoSetScanRate(v);
   }
   return DEVICE_OK;
}


int VTiSIMScanner::OnScanWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(scanWidth_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == scanWidth_)
         return DEVICE_OK;
      return DoSetScanWidth(v);
   }
   return DEVICE_OK;
}


int VTiSIMScanner::OnScanOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(scanOffset_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == scanOffset_)
         return DEVICE_OK;
      return DoSetScanOffset(v);
   }
   return DEVICE_OK;
}


int VTiSIMScanner::OnStartStop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool scanning;
      int err = DoGetScanning(scanning);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(scanning ? g_PropVal_On : g_PropVal_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string s;
      pProp->Get(s);
      bool shouldScan = (s == g_PropVal_On);
      bool scanning;
      int err = DoGetScanning(scanning);
      if (err != DEVICE_OK)
         return err;
      if (shouldScan == scanning)
         return DEVICE_OK;
      return DoStartStopScan(shouldScan);
   }
   return DEVICE_OK;
}


int VTiSIMScanner::OnActualScanRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<double>(actualRate_));
   }
   return DEVICE_OK;
}


VTiSIMHub* VTiSIMScanner::VTiHub()
{
   return static_cast<VTiSIMHub*>(GetParentHub());
}


int VTiSIMScanner::DoSetScanRate(int rateHz)
{
   if (rateHz < minRate_)
      rateHz = minRate_;
   if (rateHz > maxRate_)
      rateHz = maxRate_;

   scanRate_ = rateHz;

   bool scanning;
   int err = DoGetScanning(scanning);
   if (err != DEVICE_OK)
      return err;

   if (scanning)
   {
      err = DoStartStopScan(true);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


int VTiSIMScanner::DoSetScanWidth(int width)
{
   if (width < minWidth_)
      width = minWidth_;
   if (width > maxWidth_)
      width = maxWidth_;

   scanWidth_ = width;

   // Update offset range (and value, if necessary)
   int newMaxOffset = GetMaxOffset();
   if (scanOffset_ > newMaxOffset)
   {
      scanOffset_ = newMaxOffset;
   }
   int err = SetPropertyLimits(g_PropName_ScanOffset, 0, newMaxOffset);
   char s[MM::MaxStrLength];
   snprintf(s, MM::MaxStrLength, "%d", scanOffset_);
   err = OnPropertyChanged(g_PropName_ScanOffset, s);
   if (err != DEVICE_OK)
      return err;

   bool scanning;
   err = DoGetScanning(scanning);
   if (err != DEVICE_OK)
      return err;

   if (scanning)
   {
      err = DoStartStopScan(true);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


int VTiSIMScanner::DoSetScanOffset(int offset)
{
   if (offset < 0)
      offset = 0;
   if (offset > GetMaxOffset())
      offset = GetMaxOffset();

   scanOffset_ = offset;

   bool scanning;
   int err = DoGetScanning(scanning);
   if (err != DEVICE_OK)
      return err;

   if (scanning)
   {
      err = DoStartStopScan(true);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


int VTiSIMScanner::DoStartStopScan(bool shouldScan)
{
   float newActualRate = 0.0f;

   if (shouldScan)
   {
      DWORD err = vti_StartScan(VTiHub()->GetScanAndMotorHandle(),
         scanRate_, scanWidth_, scanOffset_);
      if (err != VTI_SUCCESS)
         return err;

      err = vti_GetActualScanRate(VTiHub()->GetScanAndMotorHandle(),
         &newActualRate);
      if (err != VTI_SUCCESS)
         return err;
   }
   else
   {
      DWORD err = vti_StopScan(VTiHub()->GetScanAndMotorHandle());
      if (err != VTI_SUCCESS)
         return err;

      newActualRate = 0.0f;
   }

   if (newActualRate != actualRate_)
   {
      actualRate_ = newActualRate;
      char s[MM::MaxStrLength];
      snprintf(s, MM::MaxStrLength, "%f", static_cast<double>(actualRate_));
      int mmerr = OnPropertyChanged(g_PropName_ActualRate, s);
      if (mmerr != DEVICE_OK)
         return mmerr;
   }

   return DEVICE_OK;
}


int VTiSIMScanner::DoGetScanning(bool& scanning)
{
   BOOL flag;
   DWORD err = vti_IsScanning(VTiHub()->GetScanAndMotorHandle(), &flag);
   if (err != VTI_SUCCESS)
      return err;
   scanning = (flag != FALSE);
   return DEVICE_OK;
}


VTiSIMPinholeArray::VTiSIMPinholeArray() :
   minFinePosition_(0),
   maxFinePosition_(1),
   curFinePosition_(6000)
{
   memset(pinholePositions_, 0, sizeof(pinholePositions_));

   SetErrorText(VTI_ERR_TIMEOUT_OCCURRED, "Timeout occurred");
   SetErrorText(VTI_ERR_DEVICE_NOT_FOUND, "Device not found");
   SetErrorText(VTI_ERR_NOT_INITIALISED, "Device not initialized");
   SetErrorText(VTI_ERR_NOT_SUPPORTED, "Operation not supported");
}


VTiSIMPinholeArray::~VTiSIMPinholeArray()
{
}


int VTiSIMPinholeArray::Initialize()
{
   DWORD vterr = vti_GetMotorRange(VTiHub()->GetScanAndMotorHandle(),
      VTI_MOTOR_PINHOLE_ARRAY, &minFinePosition_, &maxFinePosition_);
   if (vterr != VTI_SUCCESS)
      return vterr;

   int err = DoGetPinholePositions(pinholePositions_);
   if (err != DEVICE_OK)
      return err;

   // Initialize our fine position to that of the 64 um pinhole, which is the
   // default pinhole where we will move to below after properties are set up.
   curFinePosition_ = pinholePositions_[VTI_PINHOLE_64_MICRON];

   err = CreateIntegerProperty(g_PropName_FinePosition, curFinePosition_, false,
      new CPropertyAction(this, &VTiSIMPinholeArray::OnFinePosition));
   if (err != DEVICE_OK)
      return err;
   err = SetPropertyLimits(g_PropName_FinePosition,
      minFinePosition_, maxFinePosition_);
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(g_PropName_PinholeSize,
      GetPinholeSizeUmForIndex(VTI_PINHOLE_64_MICRON), false,
      new CPropertyAction(this, &VTiSIMPinholeArray::OnPinholeSize));
   if (err != DEVICE_OK)
      return err;
   for (int i = 0; i < nSizes; ++i)
   {
      char s[MM::MaxStrLength];
      snprintf(s, MM::MaxStrLength, "%d", GetPinholeSizeUmForIndex(i));
      err = AddAllowedValue(g_PropName_PinholeSize, s);
      if (err != DEVICE_OK)
         return err;
   }

   err = DoSetFinePosition(curFinePosition_);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int VTiSIMPinholeArray::Shutdown()
{
   return DEVICE_OK;
}


void VTiSIMPinholeArray::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_PinholeArray);
}


bool VTiSIMPinholeArray::Busy()
{
   return false;
}


int VTiSIMPinholeArray::OnFinePosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(curFinePosition_));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      if (v == curFinePosition_)
         return DEVICE_OK;
      int err = DoSetFinePosition(v);
      if (err != DEVICE_OK)
         return err;
   }
   return DEVICE_OK;
}


int VTiSIMPinholeArray::OnPinholeSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int sizeUm = GetPinholeSizeUmForIndex(GetNearestPinholeIndex(curFinePosition_));
      pProp->Set(static_cast<long>(sizeUm));
   }
   else if (eAct == MM::AfterSet)
   {
      long v;
      pProp->Get(v);
      int index = GetPinholeSizeIndex(v);
      if (index < 0 || index >= nSizes)
         return DEVICE_ERR; // Shouldn't happen

      int finePos = pinholePositions_[index];
      if (finePos == curFinePosition_)
         return DEVICE_OK;

      int err = DoSetFinePosition(finePos);
      if (err != DEVICE_OK)
         return err;
   }
   return DEVICE_OK;
}


VTiSIMHub* VTiSIMPinholeArray::VTiHub()
{
   return static_cast<VTiSIMHub*>(GetParentHub());
}


int VTiSIMPinholeArray::DoGetPinholePositions(int* positions)
{
   for (int i = 0; i < nSizes; ++i)
   {
      vt_int32 pos;

      VTI_EX_PARAM param;
      memset(&param, 0, sizeof(param)); // Just in case
      param.ParamOption = i;
      param.ArrayBytes = sizeof(vt_int32);
      param.pArray = &pos;

      DWORD err = vti_GetExtendedFeature(VTiHub()->GetScanAndMotorHandle(),
         VTI_FEATURE_GET_PINHOLE_SETTING, &param, sizeof(vt_int32));
      if (err != VTI_SUCCESS)
         return err;

      positions[i] = pos;
   }
   return DEVICE_OK;
}


int VTiSIMPinholeArray::DoSetFinePosition(int position)
{
   if (position < minFinePosition_)
      position = minFinePosition_;
   if (position > maxFinePosition_)
      position = maxFinePosition_;

   DWORD err = vti_MoveMotor(VTiHub()->GetScanAndMotorHandle(),
      VTI_MOTOR_PINHOLE_ARRAY, position);
   if (err != VTI_SUCCESS)
      return err;

   curFinePosition_ = position;
   return DEVICE_OK;
}


int VTiSIMPinholeArray::GetNearestPinholeIndex(int finePosition) const
{
   // There are only several (actually, 7) pinhole positions, so we just do
   // the simplest thing: find the index of the position whose distance from
   // the given fine position is smallest. This avoids making assumptions
   // about the fine positions being monotonous.

   std::vector< std::pair<int, int> > distToIndex;
   for (int i = 0; i < nSizes; ++i)
   {
      int dist = finePosition - pinholePositions_[i];
      if (dist < 0)
         dist = -dist;
      distToIndex.push_back(std::make_pair(dist, i));
   }
   std::sort(distToIndex.begin(), distToIndex.end());
   return distToIndex[0].second;
}


int VTiSIMPinholeArray::GetPinholeSizeUmForIndex(int index) const
{
   switch (index)
   {
      case VTI_PINHOLE_30_MICRON: return 30;
      case VTI_PINHOLE_40_MICRON: return 40;
      case VTI_PINHOLE_50_MICRON: return 50;
      case VTI_PINHOLE_64_MICRON: return 64;
      case VTI_PINHOLE_25_MICRON: return 25;
      case VTI_PINHOLE_15_MICRON: return 15;
      case VTI_PINHOLE_10_MICRON: return 10;
      default: return 0; // Shouldn't happen
   }
}


int VTiSIMPinholeArray::GetPinholeSizeIndex(int sizeUm) const
{
   switch (sizeUm)
   {
      case 30: return VTI_PINHOLE_30_MICRON;
      case 40: return VTI_PINHOLE_40_MICRON;
      case 50: return VTI_PINHOLE_50_MICRON;
      case 64: return VTI_PINHOLE_64_MICRON;
      case 25: return VTI_PINHOLE_25_MICRON;
      case 15: return VTI_PINHOLE_15_MICRON;
      case 10: return VTI_PINHOLE_10_MICRON;
      default: return 0;
   }
}