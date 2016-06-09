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
// AUTHOR:        Arthur Edelstein, 2012
//                Special thanks to Andre Ratz

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

const char* g_RappScannerName = "RappScanner";

#define ERR_PORT_CHANGE_FORBIDDEN    10004

#define GALVO_RANGE 4096

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_RappScannerName, MM::GalvoDevice, "Rapp UGA-40 Scanner");
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
   initialized_(false), port_(""), calibrationMode_(0), polygonAccuracy_(10), polygonMinRectSize_(10),
   ttlTriggered_("Rising Edge"), rasterFrequency_(500), spotSize_(10), laser2_(false), pulseTime_us_(500000),
   stopOnOverflow_(false)
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
	// Other devices may have send signals to the UGA 40.  If we did not find any, try one more time.
	if (s.size() <= 0)
	{
       s = dev->SearchDevices();
	}
	if (s.size() <= 0)
	{
       s.push_back(std::string("Undefined"));
	}

   // The following line crashes hardware wizard if compiled in a Debug configuration:
   CreateProperty("VirtualComPort", s.at(0).c_str(), MM::String, false, pAct, true);
	for (unsigned int i = 0; i < s.size(); i++)
   {
      AddAllowedValue("VirtualComPort", s.at(i).c_str());
   }

   delete dev;
}  

RappScanner::~RappScanner()
{
   Shutdown();
}

void RappScanner::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_RappScannerName);
}

int RappScanner::Initialize()
{
   UGA_ = new obsROE_Device();
   
   UGA_->Connect(port_.c_str());

   // Other devices may have send signals to the UGA 40.  If connection failed, try one more time to clear this up
   if (!UGA_->IsConnected()) 
   {
      UGA_->Connect(port_.c_str());
   }

   if (UGA_->IsConnected()) 
   {
      UGA_->UseMaxCalibration(false);
      UGA_->SetCalibrationMode(false, false);
	  UGA_->SetCalibrationMode(false, true);
      RunDummyCalibration(false);
	  RunDummyCalibration(true);
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
   CreateProperty("TTLTriggered", "Rising Edge", MM::String, false, pAct);
   AddAllowedValue("TTLTriggered", "Rising Edge");
   AddAllowedValue("TTLTriggered", "Falling Edge");

   pAct = new CPropertyAction(this, &RappScanner::OnSpotSize);
   CreateProperty("SpotSize", "0", MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &RappScanner::OnRasterFrequency);
   CreateProperty("RasterFrequency_Hz", "500", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &RappScanner::OnAccuracy);
   CreateProperty("AccuracyPercent", "10", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &RappScanner::OnMinimumRectSize);
   CreateProperty("MinimumRectSize", "250", MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &RappScanner::OnLaser);
   CreateProperty("Laser", "1", MM::Integer, false, pAct);
   AddAllowedValue("Laser", "1");
   AddAllowedValue("Laser", "2");

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

   pointf xy((float) x,(float) y);
   bool success = UGA_->ClickAndFire(xy, (int) pulseTime_us, laser2_);
   return success ? DEVICE_OK : DEVICE_ERR;
}

int RappScanner::SetSpotInterval(double pulseTime_us)
{
	pulseTime_us_ = pulseTime_us;
	return DEVICE_OK;
}

int RappScanner::SetIlluminationState(bool on)
{
   bool success = (bool) UGA_->SetCalibrationMode(on, laser2_);
   return success ? DEVICE_OK : DEVICE_ERR;
}

int RappScanner::SetPosition(double x, double y)
{
   bool success = UGA_->SetDevicePosition((int) x,(int) y);
   return success ? DEVICE_OK : DEVICE_ERR;
}



int RappScanner::GetPosition(double& x, double& y)
{
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
   if (polygons_.size() < (unsigned) (1+polygonIndex)) {
      polygons_.resize(1+polygonIndex);
   }
   polygons_.at(polygonIndex).push_back(pointf((float) x, (float) y));
   return DEVICE_OK;
}

int RappScanner::DeletePolygons()
{
   polygons_.clear();
   UGA_->DeletePolygons();
   return DEVICE_OK;
}

int RappScanner::LoadPolygons()
{
   tRectList rectangles;

   pointf minRectDimensions((float) polygonMinRectSize_, (float) polygonMinRectSize_);
   for (unsigned polygonIndex=0;polygonIndex<polygons_.size();++polygonIndex)
   {
      UGA_->CreateA(polygons_.at(polygonIndex), polygonAccuracy_, minRectDimensions, &rectangles, laser2_);
   }

   return DEVICE_OK;
}



int RappScanner::SetPolygonRepetitions(int repetitions)
{
   tStringList sequenceList;
   int n = (int) polygons_.size();
   sequenceList.push_back(std::string(laser2_ ? "on2" : "on"));
   for (int i=0; i<n; ++i)
   {
	  tPointList polygon = polygons_.at(i);
	  if (polygon.size() >= 3)
	  {
         stringstream cmd;
         cmd << "poly," << i;
		 sequenceList.push_back(cmd.str());
	  }
	  else
	  {
		 stringstream cmd1;
		 cmd1 << "gotoxy" << (laser2_ ? "2" : "") << "," << polygon.at(0).x << "," << polygon.at(0).y;
		 sequenceList.push_back(cmd1.str());
		 sequenceList.push_back(std::string(laser2_ ? "on2" : "on"));
		 stringstream cmd2;
		 cmd2 << "wait," << ((long) pulseTime_us_);
		 sequenceList.push_back(cmd2.str());
	  }
      
   }
   if (repetitions > 1)
   {
      stringstream repeat;
      repeat << "repeat," << (repetitions - 1);
      sequenceList.push_back(repeat.str());
   }
   sequenceList.push_back(std::string("off"));
   
   return SafeStoreSequence(sequenceList);
}

int RappScanner::RunPolygons()
{
   return UGA_->RunSequence(false) ? DEVICE_OK : DEVICE_ERR; 
}

int RappScanner::RunSequence()
{
   if (sequence_.size() > 0)
   {
      std::string sequence2 = replaceChar(sequence_, ':', ',');
      tStringList sequenceList = split(sequence2, ' ');
   
      return SafeStoreSequence(sequenceList);
   }
   
   return UGA_->RunSequence(false) ? DEVICE_OK : DEVICE_ERR; 
}

int RappScanner::StopSequence()
{

   if (UGA_->AbortSequence())
   {
      return DEVICE_OK;
   }
   else
   {
      return DEVICE_ERR;
   }
}

int RappScanner::GetChannel(char* channelName)
{
	CDeviceUtils::CopyLimitedString(channelName, laser2_ ? "2" : "1");
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
      if (UGA_->SetCalibrationMode(calibrationMode_ == 1, laser2_)) {
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
   }

   return DEVICE_OK;
}

int RappScanner::OnTTLTriggered(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ttlTriggered_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ttlTriggered_);
      if (0 == ttlTriggered_.compare("Rising Edge"))
      {
         UGA_->SetTriggerBehavior(RisingEdge);
      }
      else
      {
         UGA_->SetTriggerBehavior(FallingEdge);
      }
   }

   return DEVICE_OK;
}

int RappScanner::OnSpotSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(spotSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(spotSize_);
      UGA_->DefineSpotSize((float) spotSize_, laser2_);
   }

   return DEVICE_OK;
}

int RappScanner::OnRasterFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(rasterFrequency_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(rasterFrequency_);
      UGA_->SetROIFrequence(rasterFrequency_, laser2_); // [sic]
   }

   return DEVICE_OK;
}

int RappScanner::OnAccuracy(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(polygonAccuracy_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(polygonAccuracy_);
   }

   return DEVICE_OK;
}

int RappScanner::OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long laser;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) (laser2_ ? 2 : 1));
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(laser);
      laser2_ = (laser == 2);
   }
   return DEVICE_OK;
}

int RappScanner::OnMinimumRectSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(polygonMinRectSize_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(polygonMinRectSize_);
   }

   return DEVICE_OK;
}


/////////////////////////////
// Helper Functions
/////////////////////////////

int RappScanner::SafeStoreSequence(tStringList sequenceList)
{
   double workLoad = UGA_->GetWorkLoad(sequenceList);
   if (workLoad >= 100) {
      return DEVICE_OUT_OF_MEMORY;
   }

   return UGA_->StoreSequence(sequenceList) ? DEVICE_OK : DEVICE_ERR;
}

void RappScanner::RunDummyCalibration(bool laser2)
{
   int side = 4096;

   UGA_->SetCalibrationMode(true, laser2);

   UGA_->SetAOIEdge(Up, 0, laser2);
   UGA_->SetAOIEdge(Down, side-1, laser2);
   UGA_->SetAOIEdge(Left, 0, laser2);
   UGA_->SetAOIEdge(Right, side-1, laser2);

   pointf p0(0, 0);
   pointf p1((float) side-1, 0);
   pointf p2(0, (float) side-1);
   pointf p3((float) side-1, (float) side-1);

   UGA_->UseMaxCalibration(false);
   UGA_->InitializeCalibration(4, laser2);

   UGA_->CenterSpot();
   UGA_->MoveLaser(Up, side/2 - 1);
   UGA_->MoveLaser(Left, side/2 - 1);
   UGA_->SetCalibrationPoint(false, 0, p0, laser2); 
   UGA_->MoveLaser(Right, side);
   UGA_->SetCalibrationPoint(false, 1, p1, laser2);
   UGA_->MoveLaser(Down, side);
   UGA_->SetCalibrationPoint(false, 3, p3, laser2); //Point-ID 2->3
   UGA_->MoveLaser(Left, side);
   UGA_->SetCalibrationPoint(false, 2, p2, laser2); //Point-ID 3->2

   UGA_->SetCalibrationMode(calibrationMode_ == 1, laser2);
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

std::string replaceChar(std::string str, char ch1, char ch2) {
  std::string str2(str);
  for (unsigned i = 0; i < str2.length(); ++i) {
    if (str2[i] == ch1)
      str2[i] = ch2;
  }
  return str2;
}
