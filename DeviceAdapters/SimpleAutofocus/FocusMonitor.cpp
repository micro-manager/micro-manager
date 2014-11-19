///////////////////////////////////////////////////////////////////////////////
// FILE:          FocusMonitor.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ImageProcessor device for monitoring focusing score
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, February 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010, http://www.100ximaging.com
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
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "SimpleAutofocus.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <cmath>
#include <sstream>
#include <ctime>


using namespace std;

extern const char* g_FocusMonitorDeviceName;
const char* g_PropertyAFDevice = "AFDevice";
const char* g_PropertyScore = "AFScore";
const char* g_PropertyThreshold = "Threshold";
const char* g_PropertyDelaySec = "Delay_sec";
const char* g_PropertyOnOff = "Track";
const char* g_PropertyCorrect = "Correct";


const char* g_ON = "ON";
const char* g_OFF = "OFF";

FocusMonitor::FocusMonitor() : 
   initialized_(false),
   delayThd_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

}

FocusMonitor::~FocusMonitor()
{
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void FocusMonitor::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_FocusMonitorDeviceName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool FocusMonitor::Busy()
{
   return false;
}

/**
 * Intializes the module.
 * Required by the MM::Device API.
 */
int FocusMonitor::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   delayThd_ = new AFThread(this);

   // set property list
   // -----------------   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_FocusMonitorDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Focus score real-time monitoring", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // device
   nRet = CreateProperty(g_PropertyDelaySec, "1", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   nRet = CreateProperty(g_PropertyScore, "0.0", MM::Float, true);
   assert(nRet == DEVICE_OK);

   nRet = CreateProperty(g_PropertyThreshold, "0.0", MM::Float, false);
   assert(nRet == DEVICE_OK);

   nRet = CreateProperty(g_PropertyOnOff, g_OFF, MM::String, false);
   assert(nRet == DEVICE_OK);

   vector<string> vals;
   vals.push_back(g_OFF);
   vals.push_back(g_ON);
   nRet = SetAllowedValues(g_PropertyOnOff, vals);
   if (nRet != DEVICE_OK)
      return nRet;

   //CPropertyAction *pAct = new CPropertyAction(this, &FocusMonitor::OnCorrect);
   nRet = CreateProperty(g_PropertyCorrect, g_OFF, MM::String, false);
   assert(nRet == DEVICE_OK);
   int ret = SetAllowedValues(g_PropertyCorrect, vals);
   if (ret != DEVICE_OK)
      return ret;

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 */
int FocusMonitor::Shutdown()
{
   delete delayThd_;
   initialized_ = false;
   return DEVICE_OK;
}

int FocusMonitor::Process(unsigned char* /* buffer */, unsigned /*width*/, unsigned /*height*/, unsigned /* byteDepth*/)
{
   if (!IsPropertyEqualTo(g_PropertyOnOff, g_ON))
      return DEVICE_OK; // processor inactive

   MM::AutoFocus* afDev = GetCoreCallback()->GetAutoFocus(this);

   double score(0.0);
   if (afDev)
      return afDev->GetCurrentFocusScore(score);
   //else
   return ERR_IP_NO_AF_DEVICE;

   /*
   // keep size constant
   if (scoreQueue_.size() == QUEUE_SIZE)
      scoreQueue_.pop();

   scoreQueue_.push(score);

   // update the property
   int ret = SetProperty(g_PropertyScore, CDeviceUtils::ConvertToString(score));
   assert(ret == DEVICE_OK);

   return DEVICE_OK;
   */
}


int FocusMonitor::OnCorrect(MM::PropertyBase* /* pProp */, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      
   }
   else if (eAct == MM::BeforeGet)
   {
   }

   return DEVICE_OK; 
}

int FocusMonitor::DoAF()
{
   MM::AutoFocus* afDev = GetCoreCallback()->GetAutoFocus(this);
   if (afDev)
      return afDev->IncrementalFocus();
   else
      return ERR_IP_NO_AF_DEVICE;
}

int FocusMonitor::AcqBeforeFrame()
{
   return DEVICE_OK;
}

int FocusMonitor::AcqAfterFrame()
{
   double score = scoreQueue_.back();
   // decide if we need to start af procedure
   double threshold(0.0);
   int ret = GetProperty(g_PropertyThreshold, threshold);
   if (ret != DEVICE_OK) {
      return ret;
   }

   if (score < threshold)
   {
      return DoAF();
   }
   return DEVICE_OK;
}
