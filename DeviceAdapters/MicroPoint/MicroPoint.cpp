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
//                Thanks to Sophie Dumont, Mary Elting, and Michael Mohammadi

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
   RegisterDevice(g_MicroPointScannerName, MM::GalvoDevice, "MicroPoint");
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
///////////////////////////////////////////////////////////////////////////////
MicroPoint::MicroPoint() :
   initialized_(false), x_(0), y_(0), attenuatorPosition_(0)
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

int MicroPoint::WriteBytes(unsigned char* buf, int numBytes)
{
   int ret = WriteToComPort(port_.c_str(), buf, numBytes);
   CDeviceUtils::SleepMs(50);
   return ret;
}

int MicroPoint::Initialize()
{
   ConfigurePortDirectionRegisters();
   int ret = CreateAttenuatorProperty();
   if (ret != DEVICE_OK)
   {
      return ret;
   }
   CreateRepetitionsProperty();
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

int MicroPoint::ConfigurePortDirectionRegisters()
{
   int ret;
   unsigned char bufA[] = {'!','A',0};
   ret = WriteBytes(bufA, 3);
   if (ret != DEVICE_OK) {
      return ret;
   }

   unsigned char bufB[] = {'!','B',0};
   ret = WriteBytes(bufB, 3);
   if (ret != DEVICE_OK) {
      return ret;
   }

   unsigned char bufC[] = {'!','C',16+8+4};
   ret = WriteBytes(bufC, 3);
   if (ret != DEVICE_OK) {
      return ret;
   }
   
   return DEVICE_OK;
}


////////////////////////////////
// Attenuator private functions
////////////////////////////////

double MicroPoint::AttenuatorTransmissionFromIndex(long n)
{
   // Indices go from 0 to 89.
   // Transmission values of the attenuator go from 0.1% to 100%, on a log scale.
   return (double) 0.1 * pow((double) (100. / 0.1), (double) n / (double) 89);
}

int MicroPoint::StepAttenuatorPosition(bool positive)
{
   unsigned char buf[] = {'C', (positive ? 0xc0 : 0x80), 'C', 0x00};
   return WriteBytes(buf, 4);
}

int MicroPoint::MoveAttenuator(long steps)
{
   if (steps != 0)
   {
      unsigned char buf[] = {'A', 0, 'B', 0};
      WriteBytes(buf, 4);
   
      for (long i=0; i<labs(steps); ++i)
      {
         StepAttenuatorPosition(steps > 0);
      }
   }
   return DEVICE_OK;
}

bool MicroPoint::IsAttenuatorHome()
{
   unsigned char buf[] = {'c'};
   WriteBytes(buf, 1);
   unsigned char response[1];
   unsigned long read;
   ReadFromComPort(port_.c_str(), response, 1, read);
   return (0 != (response[0] & 0x10));    // When true we are at home.
}

long MicroPoint::FindAttenuatorPosition()
{
   long startingIndex = 0;

   // Go backwards until we hit the 0 position.
   while(!IsAttenuatorHome() && (startingIndex<100))
   {
      StepAttenuatorPosition(false);
      ++startingIndex;
   }
   if (!IsAttenuatorHome())
   {
      return -1;
   }

   // Make sure we are ready to step out of home.
   if (startingIndex == 0)
   {
      // Take one step out of home.
      while(IsAttenuatorHome())
      {
         StepAttenuatorPosition(true);
      }
      // Step back home.
      StepAttenuatorPosition(false);
   }

   // Now move back to original position.
   MoveAttenuator(startingIndex);
   return startingIndex;
}

int MicroPoint::CreateRepetitionsProperty()
{
   repetitions_ = 1;
   CPropertyAction* pAct = new CPropertyAction (this, &MicroPoint::OnRepetitions);
   CreateProperty("Repetitions", "1", MM::Integer, false, pAct);
   SetPropertyLimits("Repetitions", 1, 100);
   return DEVICE_OK;
}

int MicroPoint::CreateAttenuatorProperty()
{
   attenuatorPosition_ = FindAttenuatorPosition();
   if (attenuatorPosition_ < 0)
   {
      return DEVICE_UNKNOWN_POSITION;
   }

   CPropertyAction* pAct = new CPropertyAction (this, &MicroPoint::OnAttenuator);
   CreateProperty("AttenuatorTransmittance", "0.01", MM::String, false, pAct);
   for (long i=0;i<90;++i)
   {
      char text[32];
      snprintf(text, 32, "%.2f%%", AttenuatorTransmissionFromIndex(i));
      AddAllowedValue("AttenuatorTransmittance", text, i);
      if (i==attenuatorPosition_)
      {
         attenuatorText_.assign(text);
      }
   }

   return DEVICE_OK;
}

/////////////////////////////
// Galvo API
/////////////////////////////
int MicroPoint::PointAndFire(double x, double y, double /*pulseTime_us*/)
{
   this->SetPosition(x, y);
   for (long i=0; i<repetitions_; ++i)
   {
      this->SetIlluminationState(true);
   }
   return DEVICE_OK;
}

int MicroPoint::SetSpotInterval(double /*pulseTime_us*/)
{
   return DEVICE_OK; // ignore
}

int MicroPoint::SetIlluminationState(bool on)
{
   if (on) // Fire!
   {
      unsigned char buf[] = { 'C', 0x02,
                              'C', 0x00     };
      WriteBytes(buf, 4);
   }
   return DEVICE_OK;
}

int MicroPoint::SetPosition(double x, double y)
{
   x_ = x;
   y_ = y;
   unsigned char xpos = (unsigned char) x;
   unsigned char ypos = (unsigned char) y;
   unsigned char buf[] = { 'A', xpos, 'B', ypos };

   WriteBytes(buf, sizeof(buf));
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
   if (polygons_.size() <  (unsigned) (1 + polygonIndex))
   {
      polygons_.resize(polygonIndex + 1);
   }
   polygons_[polygonIndex].push_back(std::pair<double,double>(x,y));
   
   return DEVICE_OK;
}

int MicroPoint::DeletePolygons()
{
   polygons_.clear();
   return DEVICE_OK;
}

int MicroPoint::LoadPolygons()
{
   // Do nothing -- MicroPoint controller doesn't store polygons.
   return DEVICE_OK;
}

int MicroPoint::SetPolygonRepetitions(int repetitions)
{
   polygonRepetitions_ = repetitions;
   return DEVICE_OK;
}

int MicroPoint::RunPolygons()
{
   for (int j=0; j<polygonRepetitions_; ++j)
   {
      for (int i=0; i< (int) polygons_.size(); ++i)
      {
         double x = polygons_[i][0].first;
         double y = polygons_[i][0].second;
         PointAndFire(x,y,0);
      }
   }
   return DEVICE_OK;
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

int MicroPoint::OnAttenuator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(attenuatorText_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      long desiredPosition;
      pProp->Get(attenuatorText_);
      ((MM::Property*) pProp)->GetData(attenuatorText_.c_str(), desiredPosition);
      MoveAttenuator(desiredPosition - attenuatorPosition_);
      attenuatorPosition_ = desiredPosition;
   }
   return DEVICE_OK;
}

int MicroPoint::OnRepetitions(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(repetitions_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(repetitions_);
   }
   return DEVICE_OK;
}
