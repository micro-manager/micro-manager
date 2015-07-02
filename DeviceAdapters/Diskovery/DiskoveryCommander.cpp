///////////////////////////////////////////////////////////////////////////////
// FILE:       DiskoveryCommander.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
// AUTHOR:     Nico Stuurman
// COPYRIGHT:  Regenst of the University of California, 2015
// 
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Adapter for the Spectral/Andor/Oxford Instruments Diskovery 1 spinning disk confocal
// microscope system
//
// LICENSE: BSD
  
#include "Diskovery.h"

// Diskovery commands
const char* g_VersionHWMajor = "Q:VERSION_HW_MAJOR";
const char* g_VersionHWMinor = "Q:VERSION_HW_MINOR";
const char* g_VersionHWRevision = "Q:VERSION_HW_REVISION";
const char* g_VersionFWMajor = "Q:VERSION_FW_MAJOR";
const char* g_VersionFWMinor = "Q:VERSION_FW_MINOR";
const char* g_VersionFWRevision = "Q:VERSION_FW_REVISION";
const char* g_ManufactureYear = "Q:MANUFACTURE_YEAR";
const char* g_ManufactureMonth = "Q:MANUFACTURE_MONTH";
const char* g_ManufactureDay = "Q:MANUFACTURE_DAY";
const char* g_SetPresetSD = "A:PRESET_SD,";

/**
 * Class that sends commands to the Diskovery1
 */
DiskoveryCommander::DiskoveryCommander(
      MM::Device& device, 
      MM::Core& core, 
      std::string serialPort, 
      DiskoveryModel* model) :
   device_(device),
   core_(core),
   model_(model),
   port_(serialPort)
{
}

DiskoveryCommander::~DiskoveryCommander()
{
}   

int DiskoveryCommander::Initialize()
{
   // Request the stuff that does not change
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR(SendCommand(g_VersionHWMajor));
   RETURN_ON_MM_ERROR(SendCommand(g_VersionHWMinor));
   RETURN_ON_MM_ERROR(SendCommand(g_VersionHWRevision));
   RETURN_ON_MM_ERROR(SendCommand(g_VersionFWMajor));
   RETURN_ON_MM_ERROR(SendCommand(g_VersionFWMinor));
   RETURN_ON_MM_ERROR(SendCommand(g_VersionFWRevision));
   CDeviceUtils::SleepMs(10);
   RETURN_ON_MM_ERROR(SendCommand(g_ManufactureYear));
   RETURN_ON_MM_ERROR(SendCommand(g_ManufactureMonth));
   RETURN_ON_MM_ERROR(SendCommand(g_ManufactureDay));

   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetSD(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetSD << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SendCommand(const char* command)
{
   int ret = core_.SetSerialCommand(&device_, port_.c_str(), command, "\n");
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

