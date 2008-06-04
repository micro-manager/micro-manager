///////////////////////////////////////////////////////////////////////////////
// FILE:          NikonAZ100.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon AZ100 adapter.
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 5/22/1008
// COPYRIGHT:     University of California San Francisco, 2008
// LICENSE:       TODO!!!
// 

#ifdef WIN32
   #define snprintf _snprintf 
#endif

#include "NikonAZ100.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "AZHub.h"
#include <sstream>

// state devices
const char* g_NosepieceName = "Nosepiece";
const char* g_FilterBlockName = "FilterBlock";
const char* g_FocusName = "Focus";
const char* g_HubName = "AZ100";
const char* g_ZoomName = "Zoom";
const char* g_Control = "ControlMode";
const char* g_ControlComputer = "Computer";
const char* g_ControlManual = "Manual";

using namespace std;

// global constants
const char* g_VersionName = "Version";
const char* g_ModelName = "Version";

AZHub g_hub;

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
   AddAvailableDeviceName(g_HubName, "AZ100 controller - required for all other devices");
   AddAvailableDeviceName(g_NosepieceName, "Nosepiece (objective turret)");
   AddAvailableDeviceName(g_FocusName, "Z-stage");
   AddAvailableDeviceName(g_ZoomName, "Zoom");
   //AddAvailableDeviceName(g_FilterBlockName, "Filter changer");
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
      // create AZ100 hub
      return new Hub;
   }
   else if (strcmp(deviceName, g_FilterBlockName) == 0)
   {
      return new FilterBlock;
   }
   else if (strcmp(deviceName, g_ZoomName) == 0)
   {
      return new Zoom;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Hub
// ~~~~~~~~~~

Hub::Hub() : initialized_(false), name_(g_HubName)
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   SetErrorText(DEVICE_SERIAL_INVALID_RESPONSE, "The response was not recognized");
   SetErrorText(23000, "Please switch the control mode to computer first");

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
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon AZ100 hub", MM::String, true);
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
   /*
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
   */

   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnControl);
   ret = CreateProperty(g_Control, g_ControlComputer, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Control, g_ControlComputer);
   AddAllowedValue(g_Control, g_ControlManual);

   // make sure the hub is initialized:
   ret = g_hub.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   // we now know that we have computer control mode (taken care of by the hub initialization)
   controlMode_ = g_ControlComputer;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Hub::Shutdown()
{
   // make sure the hub is Uninitialized:
   int ret = g_hub.Shutdown(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

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

int Hub::OnControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(controlMode_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      std::string controlMode;
      pProp->Get(controlMode);
      if (controlMode == g_ControlComputer) {
         if (controlMode != controlMode_) {
            ret = g_hub.SetControlMode(*this, *GetCoreCallback(), computer);
            if (ret != DEVICE_OK)
               return ret;
            controlMode_ = controlMode;
         }
      } else if (controlMode == g_ControlManual) {
         if (controlMode != controlMode_) {
            ret = g_hub.SetControlMode(*this, *GetCoreCallback(), manual);
            if (ret != DEVICE_OK)
               return ret;
            controlMode_ = controlMode;
         }
      } else
         return ERR_INVALID_CONTROL_PARAMATER;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Nosepeiece
// ~~~~~~~~~~

Nosepiece::Nosepiece() : initialized_(false), numPos_(3), name_(g_NosepieceName)
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
   return false;
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
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon AZ100 nosepiece adapter", MM::String, true);
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

   // make sure the hub is initialized:
   ret = g_hub.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Nosepiece::Shutdown()
{
   // make sure the hub is Uninitialized:
   int ret = g_hub.Shutdown(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

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
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon AZ100 filter block changeover adapter", MM::String, true);
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

   // make sure the hub is initialized:
   ret = g_hub.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int FilterBlock::Shutdown()
{
   // make sure the hub is Uninitialized:
   int ret = g_hub.Shutdown(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

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

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FocusStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

FocusStage::FocusStage() : 
   stepSize_nm_(100),
   busy_(false),
   initialized_(false),
   lowerLimit_(4046),
   upperLimit_(18500.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   SetErrorText(23000, "Please switch the control mode to computer first");
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
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon AZ100 focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // make sure the hub is initialized:
   ret = g_hub.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &FocusStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   char buf[MM::MaxStrLength];
   if (GetProperty(MM::g_Keyword_Position, buf) != DEVICE_OK)
      return ERR_TYPE_NOT_DETECTED;

   SetPropertyLimits(MM::g_Keyword_Position, lowerLimit_, upperLimit_);

   /*
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
*/
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int FocusStage::Shutdown()
{
   // make sure the hub is Uninitialized:
   int ret = g_hub.Shutdown(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

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
// Zoom
// ~~~~~~~~~~~

Zoom::Zoom() :
   lowerLimit_(1.0),
   upperLimit_(8.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   SetErrorText(23000, "Please switch the control mode to computer first");
}


Zoom::~Zoom()
{
   Shutdown();
}


void Zoom::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZoomName);
}

int Zoom::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ZoomName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon AZ100 zoom adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // make sure the hub is initialized:
   ret = g_hub.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &Zoom::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   char buf[MM::MaxStrLength];
   if (GetProperty(MM::g_Keyword_Position, buf) != DEVICE_OK)
      return ERR_TYPE_NOT_DETECTED;

   SetPropertyLimits(MM::g_Keyword_Position, lowerLimit_, upperLimit_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Zoom::Shutdown()
{
   // make sure the hub is Uninitialized:
   int ret = g_hub.Shutdown(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return DEVICE_OK;

   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Zoom::Busy()
{
   return g_hub.IsZoomBusy(*this, *GetCoreCallback());
}

double Zoom::GetMagnification()
{
   double zoom;
   char zoomStr[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Position, zoomStr);
   zoom = atof(zoomStr);
   return zoom;
}

int Zoom::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double pos;
   if (eAct == MM::BeforeGet)
   {
      int ret = g_hub.GetZoomPosition(*this, *GetCoreCallback(), pos);
      if (ret != 0)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos);
      int ret = g_hub.SetZoomPosition(*this, *GetCoreCallback(), pos);
      if (ret != 0)
         return ret;
   }

   return DEVICE_OK;
}
