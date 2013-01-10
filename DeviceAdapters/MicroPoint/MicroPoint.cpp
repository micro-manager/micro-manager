///////////////////////////////////////////////////////////////////////////////
// FILE:          MicroPoint.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Andor MicroPoint adapter
// COPYRIGHT:     University of California, San Francisco, 2013
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
// AUTHOR:        Arthur Edelstein, 2013
//                Special thanks to Michael Mohammadi

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "MicroPoint.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

#include <iostream>

const char* g_MicroPointScannerName = "MicroPoint";

#define ERR_PORT_CHANGE_FORBIDDEN    10004

#define GALVO_RANGE 255

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_MicroPointScannerName, "MicroPoint");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_MicroPointScannerName) == 0)
   {
      MicroPoint* s = new MicroPoint();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MM::DeviceDetectionStatus MicroPointDetect(MM::Device& /*device*/, MM::Core& /*core*/, std::string portToCheck, double /*answerTimeoutMs*/)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
  
   return result;
}


///////////////////////////////////////////////////////////////////////////////
// MicroPoint
//
MicroPoint::MicroPoint() :
   initialized_(false), x_(0), y_(0)
   {
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_MicroPointScannerName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "MicroPoint galvo phototargeting adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &MicroPoint::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}  

MicroPoint::~MicroPoint()
{
   Shutdown();
}

void MicroPoint::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_MicroPointScannerName);
}


MM::DeviceDetectionStatus MicroPoint::DetectDevice(void)
{   
   return MM::Misconfigured;
}

int MicroPoint::Initialize()
{
   initialized_ = true;
   return DEVICE_OK;
}

int MicroPoint::Shutdown()
{
   return DEVICE_OK;
}

bool MicroPoint::Busy()
{
   return false;
}


/////////////////////////////
// Galvo API
/////////////////////////////
int MicroPoint::PointAndFire(double x, double y, double pulseTime_us)
{
   this->SetPosition(x, y);
   this->SetIlluminationState(true);
   return DEVICE_OK;
}

int MicroPoint::SetSpotInterval(double pulseTime_us)
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::SetIlluminationState(bool on)
{
   if (on) // Fire!
   {
      unsigned char buf[] = { 'C', 0x02,
                              'C', 0x00     };
      this->WriteToComPort(port_.c_str(), buf, 4);
   }
   return DEVICE_OK;
}

int MicroPoint::SetPosition(double x, double y)
{
   x_ = x;
   y_ = y;
   unsigned char xpos = (unsigned char) x;
   unsigned char ypos = (unsigned char) y;
   unsigned char buf[] = { '!', 'A', xpos,
                           '!', 'B', ypos,
                           'A', 0x00,
                           'B', 0x00      };

   this->WriteToComPort(port_.c_str(), buf, 10);
   CDeviceUtils::SleepMs(50);
   return DEVICE_OK;
}

int MicroPoint::GetPosition(double& x, double& y)
{
   x = x_;
   y = y_;
   return DEVICE_OK;
}

double MicroPoint::GetXRange()
{
   return (double) GALVO_RANGE;
}

double MicroPoint::GetYRange()
{
   return (double) GALVO_RANGE;
}

int MicroPoint::AddPolygonVertex(int polygonIndex, double x, double y)
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::DeletePolygons()
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::LoadPolygons()
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::SetPolygonRepetitions(int repetitions)
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::RunPolygons()
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::RunSequence()
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::StopSequence()
{
   return DEVICE_NOT_YET_IMPLEMENTED;
}

int MicroPoint::GetChannel(char* channelName)
{
	CDeviceUtils::CopyLimitedString(channelName,"Default");
	return DEVICE_OK;
}

/////////////////////////////
// Property Action Handlers
/////////////////////////////

int MicroPoint::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}
