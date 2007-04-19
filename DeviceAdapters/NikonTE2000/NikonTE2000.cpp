///////////////////////////////////////////////////////////////////////////////
// FILE:          NikonTE2000.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon TE2000 adapter.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco, 2006
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
// CVS:           $Id$
//

#ifdef WIN32
   #define snprintf _snprintf 
#endif

#include "NikonTE2000.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "TEHub.h"
#include <sstream>

// state devices
const char* g_NosepieceName = "Nosepiece";
const char* g_ShutterName = "Shutter";
const char* g_OpticalPathName = "OpticalPath";
const char* g_FilterBlockName = "FilterBlock";
const char* g_LampName = "Lamp";
const char* g_FocusName = "Focus";
const char* g_AutoFocusName = "PerfectFocus";
const char* g_HubName = "TE2000";

using namespace std;

// global constants
const char* g_VersionName = "Version";
const char* g_ModelName = "Version";

TEHub g_hub;


//#ifdef WIN32
//   BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
//                          DWORD  ul_reason_for_call, 
//                          LPVOID /*lpReserved*/
//		   			 )
//   {
//   	switch (ul_reason_for_call)
//   	{
//   	   case DLL_PROCESS_ATTACH:
//         break;
//   	
//         case DLL_THREAD_ATTACH:
//         break;
//
//   	   case DLL_THREAD_DETACH:
//         break;
//
//   	   case DLL_PROCESS_DETACH:
//         break;
//   	}
//      
//      return TRUE;
//   }
//#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_HubName, "TE2000 controller - required for all other devices");
   AddAvailableDeviceName(g_NosepieceName, "Nosepiece (objective turret)");
   AddAvailableDeviceName(g_FocusName, "Z-stage");
   AddAvailableDeviceName(g_OpticalPathName, "Optical path switch - camera, eyepiece, etc.");
   AddAvailableDeviceName(g_FilterBlockName, "Filter changer");
   AddAvailableDeviceName(g_LampName, "Halogen lamp");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_NosepieceName) == 0)
   {
      // create nosepiece
      return new Nosepiece;
   }
   else if (strcmp(deviceName, g_FocusName) == 0)
   {
      // create shutter
      return new FocusStage;
   }
   else if (strcmp(deviceName, g_HubName) == 0)
   {
      // create TE2000 hub
      return new Hub;
   }

   // TODO: implement Nikon shutter support 
   //else if (strcmp(deviceName, g_ShutterName) == 0)
   //{
   //   // create shutter
   //   return new Shutter;
   //}

   else if (strcmp(deviceName, g_OpticalPathName) == 0)
   {
      // create path
      return new OpticalPath;
   }
   else if (strcmp(deviceName, g_FilterBlockName) == 0)
   {
      return new FilterBlock;
   }
   else if (strcmp(deviceName, g_LampName) == 0)
   {
      return new Lamp;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Nosepeiece
// ~~~~~~~~~~

Hub::Hub() : initialized_(false), name_(g_HubName)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 hub", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // try to obtain version info
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;
   string ver;
   ret = g_hub.GetVersion(*this, *GetCoreCallback(), ver);
   if (ret != DEVICE_OK)
      return ret;

   // Version
   ret = CreateProperty(MM::g_Keyword_Version, ver.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // get type info
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;
   int model;
   ret = g_hub.GetModelType(*this, *GetCoreCallback(), model);
   if (ret != DEVICE_OK)
      return ret;

   // model type
   ostringstream os;
   os << model;
   ret = CreateProperty("ModelType", os.str().c_str(), MM::Integer, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(port_);
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Nosepeiece
// ~~~~~~~~~~

Nosepiece::Nosepiece() : initialized_(false), numPos_(6), name_(g_NosepieceName)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
}

Nosepiece::~Nosepiece()
{
   Shutdown();
}

void Nosepiece::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Nosepiece::Busy()
{
   return g_hub.IsNosepieceBusy(*this, *GetCoreCallback());
}

int Nosepiece::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 nosepiece adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Nosepiece::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   // <<< TODO: clear the confusion about the index
   for (unsigned i=0; i<numPos_; i++)
   {
      ostringstream os;
      os << "State-" << i;
      SetPositionLabel(i, os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Nosepiece::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Nosepiece::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetNosepiecePosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos > (long)numPos_ || pos < 1)
      {
         // restore current position
         int oldPos;
         int ret = g_hub.GetNosepiecePosition(*this, *GetCoreCallback(), oldPos);
         if (ret != 0)
            return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_hub.SetNosepiecePosition(*this, *GetCoreCallback(), (int)pos);
      if (ret != 0)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FilterBlock
// ~~~~~~~~~~~

FilterBlock::FilterBlock() : initialized_(false), numPos_(6), name_(g_FilterBlockName)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
}

FilterBlock::~FilterBlock()
{
   Shutdown();
}

void FilterBlock::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool FilterBlock::Busy()
{
   return g_hub.IsFilterBlockBusy(*this, *GetCoreCallback());
}

int FilterBlock::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 filter block changeover adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &FilterBlock::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set allowed states
   for (unsigned i=0; i<numPos_; i++)
   {
      ostringstream os;
      os << i+1;
      AddAllowedValue(MM::g_Keyword_State, os.str().c_str());
   }

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   for (unsigned i=0; i<numPos_; i++)
   {
      ostringstream os;
      os << "State-" << i+1;
      SetPositionLabel(i+1, os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int FilterBlock::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int FilterBlock::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetFilterBlockPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos > (long)numPos_ || pos < 1)
      {
         // restore current position
         int oldPos;
         int ret = g_hub.GetFilterBlockPosition(*this, *GetCoreCallback(), oldPos);
         if (ret != 0)
            return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_hub.SetFilterBlockPosition(*this, *GetCoreCallback(), (int)pos);
      if (ret != 0)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FocusStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

FocusStage::FocusStage() : 
   stepSize_nm_(100),
   busy_(false),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(20000.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
}

FocusStage::~FocusStage()
{
   Shutdown();
}

void FocusStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_FocusName);
}

int FocusStage::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_FocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &FocusStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // StepSize
   // --------
   const char* stepSizeName = "FocusStepSize_(nm)";
   ret = g_hub.GetFocusStepSize(*this, *GetCoreCallback(), stepSize_nm_);
   if (ret != DEVICE_OK)
      return ret;
   char buffer[4];
   sprintf(buffer, "%d", stepSize_nm_);
   pAct = new CPropertyAction (this, &FocusStage::OnStepSize);
   ret = CreateProperty(stepSizeName, buffer, MM::String, false, pAct, true);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(stepSizeName, "25");
   AddAllowedValue(stepSizeName, "50");
   AddAllowedValue(stepSizeName, "100");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;



   initialized_ = true;

   return DEVICE_OK;
}

int FocusStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool FocusStage::Busy()
{
   return g_hub.IsFocusBusy(*this, *GetCoreCallback());
}

int FocusStage::SetPositionUm(double pos)
{
   return SetProperty(MM::g_Keyword_Position, CDeviceUtils::ConvertToString(pos));
}

int FocusStage::GetPositionUm(double& pos)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Position, buf);
   if (ret != DEVICE_OK)
      return ret;
   pos = atof(buf);
   return DEVICE_OK;
}

int FocusStage::SetPositionSteps(long steps)
{
   if (stepSize_nm_ == 0.0)
      return DEVICE_UNSUPPORTED_COMMAND;
   return SetPositionUm(steps * stepSize_nm_ / 1000); 
}

int FocusStage::GetPositionSteps(long& steps)
{
   if (stepSize_nm_ == 0.0)
      return DEVICE_UNSUPPORTED_COMMAND;
   double posUm;
   int ret = GetPositionUm(posUm);
   if (ret != DEVICE_OK)
      return ret;

   steps = (long)(posUm / ((double)stepSize_nm_ / 1000) + 0.5);
   return DEVICE_OK;
}

int FocusStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int FocusStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetFocusPosition(*this, *GetCoreCallback(), pos);
      if (ret != 0)
         return ret;
      pProp->Set((double) (pos * ((double)stepSize_nm_ / 1000)) );
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      int focusPos = (int)(pos/((double)stepSize_nm_ / 1000.0) + 0.5);
      int ret = g_hub.SetFocusPosition(*this, *GetCoreCallback(), focusPos);
      if (ret != 0)
         return ret;
   }

   return DEVICE_OK;
}


/*
 * 
 */
int FocusStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   printf ("In OnStepSize\n");
   if (eAct == MM::BeforeGet)
   {
      int ret = g_hub.GetFocusStepSize(*this, *GetCoreCallback(), stepSize_nm_);
      if (ret != 0)
         return ret;
      if (stepSize_nm_ == 100)
         pProp->Set("100");
      else if (stepSize_nm_ == 50)
         pProp->Set("50");
      else if (stepSize_nm_ == 25)
         pProp->Set("25");
   }
   else if (eAct == MM::AfterSet)
   {
      string stepSize;
      pProp->Get(stepSize);
      stepSize_nm_ = atoi(stepSize.c_str());
      int ret = g_hub.SetFocusStepSize(*this, *GetCoreCallback(), stepSize_nm_);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// OpticalPath
// ~~~~~~~~~~~

OpticalPath::OpticalPath() : initialized_(false), numPos_(5), name_(g_OpticalPathName)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
}

OpticalPath::~OpticalPath()
{
   Shutdown();
}

void OpticalPath::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool OpticalPath::Busy()
{
   return g_hub.IsOpticalPathBusy(*this, *GetCoreCallback());
}

int OpticalPath::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 optical path adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &OpticalPath::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set allowed states
   AddAllowedValue(MM::g_Keyword_State, "1");
   AddAllowedValue(MM::g_Keyword_State, "2");
   AddAllowedValue(MM::g_Keyword_State, "3");
   AddAllowedValue(MM::g_Keyword_State, "4");
   AddAllowedValue(MM::g_Keyword_State, "5");


   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   SetPositionLabel(1, "Monitor(100)");
   SetPositionLabel(2, "Monitor(20)-Right(80)");
   SetPositionLabel(3, "Bottom(100)");
   SetPositionLabel(4, "Monitor(20)-Front(80)");
   SetPositionLabel(5, "Left(100)");
  
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int OpticalPath::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int OpticalPath::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetOpticalPathPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos > (long)numPos_ || pos < 1)
      {
         // restore current position
         int oldPos;
         int ret = g_hub.GetOpticalPathPosition(*this, *GetCoreCallback(), oldPos);
         if (ret != DEVICE_OK)
            return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_hub.SetOpticalPathPosition(*this, *GetCoreCallback(), (int)pos);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Lamp
// ~~~~

Lamp::Lamp() : initialized_(false), name_(g_LampName), openTimeUs_(0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
}

Lamp::~Lamp()
{
   Shutdown();
}

void Lamp::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Lamp::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;
   //if (stat <= 0 || interval < GetDelayMs())
   // >> NOTE: if the stat is in the error state the device ramains forever busy!
   if(interval/1000.0 < GetDelayMs())
   {
      return true;
   }
   else
   {
      return g_hub.IsLampBusy(*this, *GetCoreCallback());
   }
}

int Lamp::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 lamp adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &Lamp::OnOnOff);
   ret = CreateProperty("OnOff", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue("OnOff", "0");
   AddAllowedValue("OnOff", "1");

   // Voltage
   // -------
   pAct = new CPropertyAction (this, &Lamp::OnVoltage);
   ret = CreateProperty("Voltage", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   openTimeUs_ = GetClockTicksUs();

   return DEVICE_OK;
}

int Lamp::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Lamp::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int Lamp::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int Lamp::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Lamp::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int state;
      int ret = g_hub.GetLampOnOff(*this, *GetCoreCallback(), state);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)state);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      // apply the value
      int ret = g_hub.SetLampOnOff(*this, *GetCoreCallback(), (int)state);
      if (ret != DEVICE_OK)
         return ret;
      openTimeUs_ = GetClockTicksUs();
   }

   return DEVICE_OK;
}


int Lamp::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double voltage;
      int ret = g_hub.GetLampVoltage(*this, *GetCoreCallback(), voltage);
         
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(voltage);
   }
   else if (eAct == MM::AfterSet)
   {
      double voltage;
      pProp->Get(voltage);
      int ret = g_hub.GetLampVoltage(*this, *GetCoreCallback(), voltage);
         
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// PerfectFocus
// ~~~~~~~~~~~~

PerfectFocus::PerfectFocus() :
   initialized_(false)
{
}

PerfectFocus::~PerfectFocus()
{
}
      
void PerfectFocus::GetName(char* pszName) const
{
}

bool PerfectFocus::Busy()
{
   int status = 0;
   g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);

   if (status == PFS_WAIT || status == PFS_SEARCHING || status == PFS_SEARCHING_2)
      return true;
   else
      return false;
}

int PerfectFocus::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_AutoFocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon Perfect Focus (PFS) adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int PerfectFocus::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int PerfectFocus::SetContinuousFocusing(bool state)
{
   if (state)
      return g_hub.SetPFocusOn(*this, *GetCoreCallback());
   else
      return g_hub.SetPFocusOff(*this, *GetCoreCallback());
}

int PerfectFocus::GetContinuousFocusing(bool& state)
{
   int status;
   int ret = g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);
   if (ret != DEVICE_OK)
      return ret;

   if (status == PFS_RUNNING)
      state = true;
   else
      state = false;

   return DEVICE_OK;
}

int PerfectFocus::Focus()
{
   return g_hub.SetPFocusOn(*this, *GetCoreCallback());
}

int GetFocusScore(double& /*score*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
