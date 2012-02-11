///////////////////////////////////////////////////////////////////////////////
// FILE:          Rapp.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Rapp UGA40 adapter
// COPYRIGHT:     University of California, San Francisco, 2012
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
// AUTHOR:        Arthur Edelstein
//                Thanks to Andre Ratz

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "Rapp.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

#include <iostream>
using namespace std;

const char* g_RappScannerName = "RappScanner";

#define ERR_PORT_CHANGE_FORBIDDEN    10004

#define GALVO_RANGE 4096

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_RappScannerName, "RappScanner");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_RappScannerName) == 0)
   {
      RappScanner* s = new RappScanner();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MM::DeviceDetectionStatus RappScannerDetect(MM::Device& /*device*/, MM::Core& /*core*/, std::string portToCheck, double /*answerTimeoutMs*/)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
  
   return result;
}


///////////////////////////////////////////////////////////////////////////////
// RappScanner
//
RappScanner::RappScanner() :
   initialized_(false), port_(""), calibrationMode_(0), polygonAccuracy_(10), polygonMinRectSize_(pointf(10,10)),
   ttlTriggered_(0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_RappScannerName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Rapp UGA-40 galvo phototargeting adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &RappScanner::OnPort);
   
   obsROE_Device* dev = new obsROE_Device();

	std::vector<std::string> s = dev->SearchDevices();
	if (s.size() <= 0)
	{
      s.push_back(std::string("Undefined"));
	}

   CreateProperty("VirtualComPort", s.at(0).c_str(), MM::String, false, pAct, true);
	for (unsigned int i = 0; i < s.size(); i++)
      AddAllowedValue("VirtualComPort", s.at(i).c_str());


}  

RappScanner::~RappScanner()
{
   Shutdown();
}

void RappScanner::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_RappScannerName);
}


MM::DeviceDetectionStatus RappScanner::DetectDevice(void)
{   
   return MM::Misconfigured;
}

int RappScanner::Initialize()
{
   UGA_ = new obsROE_Device();
   
   UGA_->Connect(port_.c_str());
   if (UGA_->IsConnected()) {
      UGA_->UseMaxCalibration(true);
      UGA_->SetCalibrationMode(false, false);
      RunDummyCalibration();
      UGA_->CenterSpot();
      currentX_ = 0;
      currentY_ = 0;
      initialized_ = true;
   } else {
      initialized_ = false;
      return DEVICE_NOT_CONNECTED;
   }

   CPropertyAction* pAct = new CPropertyAction(this, &RappScanner::OnCalibrationMode);
   CreateProperty("CalibrationMode", "0", MM::Integer, false, pAct);
   AddAllowedValue("CalibrationMode", "1");
   AddAllowedValue("CalibrationMode", "0");

   pAct = new CPropertyAction(this, &RappScanner::OnSequence);
   CreateProperty("Sequence", "", MM::String, false, pAct);

   pAct = new CPropertyAction(this, &RappScanner::OnTTLTriggered);
   CreateProperty("TTLTriggered", "0", MM::Integer, false, pAct);
   AddAllowedValue("TTLTriggered", "0");
   AddAllowedValue("TTLTriggered", "1");

   return DEVICE_OK;
}

int RappScanner::Shutdown()
{
   int result = DEVICE_NOT_CONNECTED;
   if (initialized_)
   {
      if (UGA_->IsConnected()) {
         if (UGA_->Disconnect()) {
            initialized_ = false;
            result = DEVICE_OK;
         }
      }

   delete UGA_;
   }
   return result;
}

bool RappScanner::Busy()
{
   return false;
}


/////////////////////////////
// Galvo API
/////////////////////////////
int RappScanner::PointAndFire(double x, double y, double pulseTime_us)
{
   int result = DEVICE_OK;
   pointf xy((float) x,(float) y);
   UGA_->ClickAndFire(xy, (int) pulseTime_us, false);
   return result;
}


int RappScanner::SetPosition(double x, double y)
{
   // This function will hopefully be able to call SetDevicePosition(x,y) in the future.
   UGA_->SetDevicePosition((int) x,(int) y);
   UGA_->CurrentPosition();
   // Move(x - currentX_, y - currentY_);
   return DEVICE_OK;
}



int RappScanner::GetPosition(double& x, double& y)
{
   // This function will hopefully be able to call SetDevicePosition(x,y) in the future.
   pointf p = UGA_->CurrentPosition();
   x = p.x;
   y = p.y;
   return DEVICE_OK;
}


double RappScanner::GetXRange()
{
   return (double) GALVO_RANGE;
}


double RappScanner::GetYRange()
{
   return (double) GALVO_RANGE;
}

int RappScanner::AddPolygonVertex(int polygonIndex, double x, double y)
{
   polygons_.at(polygonIndex).push_back(pointf((float) x, (float) y));
   return DEVICE_OK;
}

int RappScanner::DeletePolygons()
{
   polygons_.clear();
   return DEVICE_OK;
}


int RappScanner::RunSequence()
{
   tRectList rectangles;
   for (unsigned polygonIndex=0;polygonIndex<polygons_.size();++polygonIndex)
   {
      UGA_->CreateA(polygons_.at(polygonIndex), polygonAccuracy_, polygonMinRectSize_, &rectangles, false);
   }
   UGA_->RunSequence(false);
   return DEVICE_OK;
}

/////////////////////////////
// Property Action Handlers
/////////////////////////////

int RappScanner::OnCalibrationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int result = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(calibrationMode_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(calibrationMode_);
      if (UGA_->SetCalibrationMode(calibrationMode_ == 1, false)) {
         result = DEVICE_OK;
      } else {
         result = DEVICE_NOT_CONNECTED;
      }
   }
   
   return result;
}

int RappScanner::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int RappScanner::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(sequence_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(sequence_);

      if (sequence_.size() > 0)
      {
         tStringList sequenceList = split(sequence_, ' ');

         if (!UGA_->StoreSequence(sequenceList))
         {
            return DEVICE_ERR;
         } 
      }
   }

   return DEVICE_OK;
}

int RappScanner::OnTTLTriggered(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ttlTriggered_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ttlTriggered_);
   }

   return DEVICE_OK;
}

/////////////////////////////
// Helper Functions
/////////////////////////////

void RappScanner::RunDummyCalibration()
{
  UGA_->SetCalibrationMode(true, false);

  int side = 4096;

  UGA_->SetAOIEdge(Up, 0, false);
  UGA_->SetAOIEdge(Down, side-1, false);
  UGA_->SetAOIEdge(Left, 0, false);
  UGA_->SetAOIEdge(Right, side-1, false);

  pointf p0(0, 0);
  pointf p1((float) side-1, 0);
  pointf p2((float) side-1, (float) side-1);
  pointf p3(0, (float) side-1);

  UGA_->CenterSpot();
  UGA_->MoveLaser(Up, side/2-1);
  UGA_->MoveLaser(Left, side/2-1);

  pointf xy = UGA_->CurrentPosition();

  UGA_->InitializeCalibration(4, false);
  UGA_->SetCalibrationPoint(false, 0, p0, false);
  UGA_->MoveLaser(Right, side);
  UGA_->SetCalibrationPoint(false, 1, p1, false);
  UGA_->MoveLaser(Down, side);
  UGA_->SetCalibrationPoint(false, 2, p2, false);
  UGA_->MoveLaser(Left, side);
  UGA_->SetCalibrationPoint(false, 3, p3, false);

  UGA_->SetCalibrationMode(calibrationMode_ == 1, false);
}

std::vector<std::string> & split(const std::string &s, char delim, std::vector<std::string> &elems) {
    std::stringstream ss(s);
    std::string item;
    while(std::getline(ss, item, delim)) {
        elems.push_back(item);
    }
    return elems;
}

std::vector<std::string> split(const std::string &s, char delim) {
    std::vector<std::string> elems;
    return split(s, delim, elems);
}
