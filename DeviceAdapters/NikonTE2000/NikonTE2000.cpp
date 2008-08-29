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
const char* g_AnalyzerName = "Analyzer";
const char* g_FilterBlockName = "FilterBlock";
const char* g_LampName = "Lamp";
const char* g_EpiShutterName = "Epi-Shutter";
const char* g_UniblitzShutterName = "Uniblitz-Shutter";
const char* g_FocusName = "Focus";
const char* g_AutoFocusName = "PerfectFocus";
const char* g_HubName = "TE2000";
const char* g_Control = "Control";
const char* g_ControlMicroscope = "Microscope";
const char* g_ControlPad = "Control Pad";

using namespace std;

// global constants
const char* g_VersionName = "Version";
const char* g_ModelName = "Version";

TEHub g_hub;
bool g_PFSinstalled = false;

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
   AddAvailableDeviceName(g_AnalyzerName, "Analyzer control");
   AddAvailableDeviceName(g_FilterBlockName, "Filter changer");
   AddAvailableDeviceName(g_LampName, "Halogen lamp");
   AddAvailableDeviceName(g_EpiShutterName, "Epi-fluorescence shutter");
   AddAvailableDeviceName(g_UniblitzShutterName, "Uniblitz shutter");
   AddAvailableDeviceName(g_AutoFocusName,  "PFS autofocus device");
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

   else if (strcmp(deviceName, g_OpticalPathName) == 0)
   {
      // create path
      return new OpticalPath;
   }
   else if (strcmp(deviceName, g_AnalyzerName) == 0)
   {
      return new Analyzer;
   }
   else if (strcmp(deviceName, g_FilterBlockName) == 0)
   {
      return new FilterBlock;
   }
   else if (strcmp(deviceName, g_LampName) == 0)
   {
      return new Lamp;
   }
   else if (strcmp(deviceName, g_EpiShutterName) == 0)
   {
      return new EpiShutter;
   }
   else if (strcmp(deviceName, g_UniblitzShutterName) == 0)
   {
      return new UniblitzShutter;
   }
   else if (strcmp(deviceName, g_AutoFocusName) == 0)
   {
      return new PerfectFocus;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Nosepiece
// ~~~~~~~~~~

Hub::Hub() : initialized_(false), name_(g_HubName)
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   SetErrorText(DEVICE_SERIAL_INVALID_RESPONSE, "The response was not recognized");

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
   for (unsigned i=0; i<numPos_; i++)
   {
      ostringstream os;
      os << "Position-" << i+1;
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
      pos -= 1;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      pos += 1;
      if (pos > (long)numPos_ || pos < 1)
      {
         // restore current position
         int oldPos;
         int ret = g_hub.GetNosepiecePosition(*this, *GetCoreCallback(), oldPos);
         if (ret != 0)
            return ret;
         pProp->Set((long)oldPos-1); // revert
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
      os << i;
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
      os << "Position-" << i+1;
      SetPositionLabel(i, os.str().c_str());
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
      pos -= 1;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      pos += 1;
      if (pos > (long)numPos_ || pos < 1)
      {
         // restore current position
         int oldPos;
         int ret = g_hub.GetFilterBlockPosition(*this, *GetCoreCallback(), oldPos);
         if (ret != 0)
            return ret;
         pProp->Set((long)oldPos-1); // revert
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
      if (g_PFSinstalled)
      {
         // check if the perfect focus is active
         // and ignore this command if it is
         int status = PFS_DISABLED;
         g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);
         if (status == PFS_RUNNING || status == PFS_JUST_PINT)
            return DEVICE_OK;
      }

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
// AnalyzerPath
// ~~~~~~~~~~~~

Analyzer::Analyzer() : initialized_(false), numPos_(3), name_(g_AnalyzerName)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
}

Analyzer::~Analyzer()
{
   Shutdown();
}

void Analyzer::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Analyzer::Busy()
{
   return g_hub.IsAnalyzerBusy(*this, *GetCoreCallback());
}

int Analyzer::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 analyzer", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Analyzer::OnState);
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
   SetPositionLabel(0, "Undefined");
   SetPositionLabel(1, "Inserted");
   SetPositionLabel(2, "Extracted");
  
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Analyzer::Shutdown()
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

int Analyzer::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetAnalyzerPosition(*this, *GetCoreCallback(), pos);
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
         int ret = g_hub.GetAnalyzerPosition(*this, *GetCoreCallback(), oldPos);
         if (ret != DEVICE_OK)
            return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_hub.SetAnalyzerPosition(*this, *GetCoreCallback(), (int)pos);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Lamp
// ~~~~

Lamp::Lamp() : initialized_(false), name_(g_LampName), changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   
   EnableDelay();
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
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if(interval < MM::MMTime(1000.0 * GetDelayMs()))
      return true;
   else
      return g_hub.IsLampBusy(*this, *GetCoreCallback());
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

   // Control (Remote Pad or Microscope)
   // -------
   pAct = new CPropertyAction (this, &Lamp::OnControl);
   ret = CreateProperty(g_Control, g_ControlMicroscope, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Control, g_ControlMicroscope);
   AddAllowedValue(g_Control, g_ControlPad);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   changedTime_ = GetCurrentMMTime();

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
    // make sure that we have control
   int ret = g_hub.SetLampControlTarget(*this, *GetCoreCallback(), LAMP_TARGET_PAD);   
   if (ret != DEVICE_OK)
      return ret;

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
     changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}


int Lamp::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // make sure that we have control
   int ret = g_hub.SetLampControlTarget(*this, *GetCoreCallback(), LAMP_TARGET_PAD);   
   if (ret != DEVICE_OK)
      return ret;

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

int Lamp::OnControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   int ret, target;
   if (eAct == MM::BeforeGet)
   {
      ret = g_hub.GetLampControlTarget(*this, *GetCoreCallback(), target);
      if (ret != DEVICE_OK)
         return ret;
      if (target == LAMP_TARGET_PAD)
         pProp->Set(g_ControlPad);
      else
         pProp->Set(g_ControlMicroscope);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string controlTarget;
      pProp->Get(controlTarget);
      if (controlTarget == g_ControlPad)
         target = LAMP_TARGET_PAD;
      else
         target = LAMP_TARGET_MICROSCOPE;
      ret = g_hub.SetLampControlTarget(*this, *GetCoreCallback(), target);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// EpiShutter
// ~~~~

EpiShutter::EpiShutter() : initialized_(false), name_(g_EpiShutterName), changedTime_(0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   
   EnableDelay();
}

EpiShutter::~EpiShutter()
{
   Shutdown();
}

void EpiShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool EpiShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if(interval < MM::MMTime(1000.0 * GetDelayMs()))
      return true;
   else
      return false;
      //return g_hub.IsLampBusy(*this, *GetCoreCallback());
}

int EpiShutter::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 Epi-Illumination Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &EpiShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   changedTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}

int EpiShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int EpiShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty(MM::g_Keyword_State, "1");
   else
      return SetProperty(MM::g_Keyword_State, "0");
}

int EpiShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int EpiShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// EpiShutter Action handlers
///////////////////////////////////////////////////////////////////////////////

int EpiShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // Our definition: 0=Closed, 1 = Open, Nikon definition: 0=undefined, 1=Open, 2=Closed
  if (eAct == MM::BeforeGet)
   {
      int state;
      int ret = g_hub.GetEpiShutterStatus(*this, *GetCoreCallback(), state);
      if (ret != DEVICE_OK)
         return ret;
      // translate between MicroManager and Nikon definitions of state:
      if (state == 2)
         state = 0;
      // TODO: when state==0 return error 
      pProp->Set((long)state);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      // translate between MicroManager and Nikon definitions of state:
      if (state == 0)
         state = 2;
      // apply the value
      int ret = g_hub.SetEpiShutterStatus(*this, *GetCoreCallback(), (int)state);
      if (ret != DEVICE_OK)
         return ret;
     changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// UnliBlitzShutters
// ~~~~

UniblitzShutter::UniblitzShutter() : 
   initialized_(false), 
   name_(g_EpiShutterName), 
   shutterNr_(1), 
   state_(0),
   changedTime_(0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   
   EnableDelay();
   CPropertyAction* pAct = new CPropertyAction (this, &UniblitzShutter::OnShutterNumber);
   CreateProperty("Shutter Number", "1", MM::Integer, false, pAct, true);

   AddAllowedValue("Shutter Number", "1");
   AddAllowedValue("Shutter Number", "2");
   AddAllowedValue("Shutter Number", "3");
   // 7 is used to open/close all Uniblitz shutters simulanuously
   AddAllowedValue("Shutter Number", "7");
}

UniblitzShutter::~UniblitzShutter()
{
   Shutdown();
}

void UniblitzShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool UniblitzShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if(interval < MM::MMTime(1000.0 * GetDelayMs()))
      return true;
   else
      return false;
}

int UniblitzShutter::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon TE2000 Uniblitz Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &UniblitzShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Since we can not query for the state of the shutter, close it to get it into a known state
   ret = SetOpen(false);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   changedTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}

int UniblitzShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int UniblitzShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty(MM::g_Keyword_State, "1");
   else
      return SetProperty(MM::g_Keyword_State, "0");
}

int UniblitzShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int UniblitzShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int UniblitzShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // Our definition: 0=Closed, 1 = Open, Nikon definition: 0=undefined, 1=Closed, 2=Open
  if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)state_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(state_);
      // apply the value (convert from MM to Nikon state value)
      int ret = g_hub.SetUniblitzStatus(*this, *GetCoreCallback(), shutterNr_, (int)state_ + 1);
      if (ret != DEVICE_OK)
         return ret;

      changedTime_ = GetCurrentMMTime();
   }
   return DEVICE_OK;
}

int UniblitzShutter::OnShutterNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
   {
      pProp->Set(shutterNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      // Let MMCore take care of value checking
      pProp->Get(shutterNr_);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// PerfectFocus
// ~~~~~~~~~~~~

PerfectFocus::PerfectFocus() :
   initialized_(false)

{
   // Custom error messages
   SetErrorText(ERR_PFS_NOT_CONNECTED,"Perfect Focus device not found.  Is it switched on and connected?");
}

PerfectFocus::~PerfectFocus()
{
}
      
void PerfectFocus::GetName(char* /*pszName*/) const
{
}

bool PerfectFocus::Busy()
{
   int status = 0;
   g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);

   if (/* status == PFS_WAIT || */ status == PFS_SEARCHING || status == PFS_SEARCHING_2)
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
   
   // Version (this also functions as a check that the PF is attached ann switched ON
   string version;
   ret = g_hub.GetPFocusVersion(*this, *GetCoreCallback(), version);
   if (ret != DEVICE_OK)
      return ERR_PFS_NOT_CONNECTED;
   ret = CreateProperty("Version", version.c_str(), MM::String, true);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   g_PFSinstalled = true;
   return DEVICE_OK;
}


int PerfectFocus::Shutdown()
{
   initialized_ = false;
   g_PFSinstalled = false;
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

   if (status == PFS_RUNNING || status == PFS_JUST_PINT)
      state = true;
   else
      state = false;

   return DEVICE_OK;
}

bool PerfectFocus::IsContinuousFocusLocked()
{
   int status;
   int ret = g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);
   if (ret != DEVICE_OK)
      return false;

   if (status == PFS_JUST_PINT)
      return true;
   else
      return false;
}

int PerfectFocus::FullFocus()
{
   return g_hub.SetPFocusOn(*this, *GetCoreCallback());
}

int PerfectFocus::IncrementalFocus()
{
   return g_hub.SetPFocusOn(*this, *GetCoreCallback());
}


int GetFocusScore(double& /*score*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
