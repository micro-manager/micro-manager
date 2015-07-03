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
const char* g_ProductModel = "Q:PRODUCT_MODEL";
const char* g_SerialNumber = "Q:PRODUCT_SERIAL_NO";
const char* g_SetPresetSD = "A:PRESET_SD,";
const char* g_GetPresetSD = "Q:PRESET_SD";
const char* g_SetPresetWF = "A:PRESET_WF,";
const char* g_GetPresetWF = "Q:PRESET_WF";
const char* g_SetPresetFilter = "A:PRESET_FILTER_W,";
const char* g_GetPresetFilter = "Q:PRESET_FILTER_W";
const char* g_SetPresetIris = "A:PRESET_IRIS,";
const char* g_GetPresetIris = "Q:PRESET_IRIS";
const char* g_SetPresetTIRF = "A:PRESET_PX,";
const char* g_GetPresetTIRF = "Q:PRESET_PX";
const char* g_SetMotorRunningSD = "A:MOTOR_RUNNING_SD,";
const char* g_GetMotorRunningSD = "Q:MOTOR_RUNNING_SD";

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

/**
 * Queries the controller for its current settings.
 * Answer will be read by the Listening thread that should already 
 * have been started.
 * Sends the commands in batches of 3 with 50 ms waits in between
 * to avoid overlaoding the controller (this worked with the demo unit
 * I had, adjust if there are problems querying on startup).
 */
int DiskoveryCommander::Initialize()
{
   // Request the stuff that does not change
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_VersionHWMajor) );
   RETURN_ON_MM_ERROR( SendCommand(g_VersionHWMinor) );
   RETURN_ON_MM_ERROR( SendCommand(g_VersionHWRevision) );
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_VersionFWMajor) );
   RETURN_ON_MM_ERROR( SendCommand(g_VersionFWMinor) );
   RETURN_ON_MM_ERROR( SendCommand(g_VersionFWRevision) );
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_ManufactureYear) );
   RETURN_ON_MM_ERROR( SendCommand(g_ManufactureMonth) );
   RETURN_ON_MM_ERROR( SendCommand(g_ManufactureDay) );
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_SerialNumber) );
   RETURN_ON_MM_ERROR( SendCommand(g_GetPresetSD) );
   RETURN_ON_MM_ERROR( SendCommand(g_GetPresetWF) );
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_GetPresetFilter) );
   RETURN_ON_MM_ERROR( SendCommand(g_GetPresetIris) );
   RETURN_ON_MM_ERROR( SendCommand(g_GetPresetTIRF) );
   CDeviceUtils::SleepMs(50);
   RETURN_ON_MM_ERROR( SendCommand(g_GetMotorRunningSD) );

   return DEVICE_OK;
}

/**
 * This function is used to send an inocous command
 */
int DiskoveryCommander::GetProductModel()
{
   RETURN_ON_MM_ERROR( SendCommand(g_ProductModel) );
   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetSD(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetSD << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetWF(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetWF << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetFilter(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetFilter << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetIris(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetIris << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SetPresetTIRF(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetPresetTIRF << pos;
   RETURN_ON_MM_ERROR( SendCommand(os.str().c_str()) );

   return DEVICE_OK;
}

int DiskoveryCommander::SetMotorRunningSD(uint16_t pos)
{
   std::ostringstream os;
   os << g_SetMotorRunningSD << pos;
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

