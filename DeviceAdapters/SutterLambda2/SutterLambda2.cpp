///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterLambda2.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nick Anthony, Oct. 2018
//
// CVS:           $Id$
//

#ifdef WIN32
#include <windows.h>
#endif
#include "FixSnprintf.h"

#include "SutterHub.h"
#include "SutterWheel.h"
#include "SutterShutter.h"
#include <vector>
#include <memory>
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>


// Device Names
const char* g_HubName = "SutterHub";
const char* g_WheelAName = "Wheel-A";
const char* g_WheelBName = "Wheel-B";
const char* g_WheelCName = "Wheel-C";
const char* g_ShutterAName = "Shutter-A";
const char* g_ShutterBName = "Shutter-B";
const char* g_LambdaVF5Name = "VF-5";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_HubName, MM::HubDevice, "Lambda Controller Hub");
	RegisterDevice(g_WheelAName, MM::StateDevice, "Lambda 10 filter wheel A");
    RegisterDevice(g_WheelBName, MM::StateDevice, "Lambda 10 filter wheel B");
    RegisterDevice(g_WheelCName, MM::StateDevice, "Lambda 10 wheel C (10-3 only)");
    RegisterDevice(g_ShutterAName, MM::ShutterDevice, "Lambda 10 shutter A");
    RegisterDevice(g_ShutterBName, MM::ShutterDevice, "Lambda 10 shutter B");
    RegisterDevice(g_LambdaVF5Name, MM::StateDevice, "Lambda VF-5 (10-3 only)");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_HubName) == 0) {
	   SutterHub* pHub = new SutterHub(g_HubName);
	   return pHub;
   }
   else if (strcmp(deviceName, g_WheelAName) == 0)
   {
      // create Wheel A
      Wheel* pWheel = new Wheel(g_WheelAName, 0);
      return pWheel;
   }
   else if (strcmp(deviceName, g_WheelBName) == 0)
   {
      // create Wheel B
      Wheel* pWheel = new Wheel(g_WheelBName, 1);
      return pWheel;
   }
   else if (strcmp(deviceName, g_WheelCName) == 0)
   {
      // create Wheel C
      Wheel* pWheel = new Wheel(g_WheelCName, 2);
      return pWheel;
   }
   else if (strcmp(deviceName, g_ShutterAName) == 0)
   {
      // create Shutter A
      Shutter* pShutter = new Shutter(g_ShutterAName, 0);
      return pShutter;
   }
   else if (strcmp(deviceName, g_ShutterBName) == 0)
   {
      // create Shutter B
      Shutter* pShutter = new Shutter(g_ShutterBName, 1);
      return pShutter;
   }
   else if (strcmp(deviceName, g_LambdaVF5Name) == 0)
   {
	   //Create Lambda VF-5 tunable filter
	   return new LambdaVF5(g_LambdaVF5Name);
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
