///////////////////////////////////////////////////////////////////////////////
// FILE:          Thorlabs.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD Controller
//
// COPYRIGHT:     Thorlabs, 2011
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 2011
//

#ifdef WIN32
   #include <windows.h>
#endif
#include "FixSnprintf.h"

#include "Thorlabs.h"
#include "XYStage.h"
#include "MotorZStage.h"
#include "PiezoZStage.h"
#include "IntegratedFilterWheel.h"
#include <ModuleInterface.h>
#include <MMDevice.h>
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>

///////////
// properties
///////////

const char* g_XYStageDeviceName = "XYStage";
const char* g_PiezoZStageDeviceName = "PiezoZStage";
const char* g_MotorZStageDeviceName = "MotorZStage";
const char* g_WheelDeviceName = "FilterWheel";

const char* g_SerialNumberProp = "SerialNumber";
const char* g_ModelNumberProp = "ModelNumber";
const char* g_SWVersionProp = "SoftwareVersion";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Utility
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears receive buffer of any content.
 * To be used before sending commands to make sure that we are not catching
 * residual error messages or previous unhandled responses.
 */
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   while ((int) read == bufSize)
   {
      int ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Thorlabs BD102 XY Stage");
   RegisterDevice(g_PiezoZStageDeviceName, MM::StageDevice, "Thorlabs piezo Z Stage");
   RegisterDevice(g_MotorZStageDeviceName, MM::StageDevice, "Thorlabs Motor Z Stage");
   RegisterDevice(g_WheelDeviceName, MM::StateDevice, "Integrated filter wheel");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* xyStage = new XYStage();
      return xyStage;
   }
   if (strcmp(deviceName, g_PiezoZStageDeviceName) == 0)
   {
      PiezoZStage* stage = new PiezoZStage();
      return stage;
   }
   if (strcmp(deviceName, g_MotorZStageDeviceName) == 0)
   {
      MotorZStage* stage = new MotorZStage();
      return stage;
   }
   if (strcmp(deviceName, g_WheelDeviceName) == 0)
   {
      IntegratedFilterWheel* wheel = new IntegratedFilterWheel();
      return wheel;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

