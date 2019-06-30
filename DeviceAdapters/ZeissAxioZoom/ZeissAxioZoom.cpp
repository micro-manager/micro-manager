///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissAxioZoom.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN29 bus controller, see Zeiss CAN29 bus documentation
//                
// AUTHOR: Nico Stuurman, 6/18/2007 - 
//
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (permission re-granted on 7/3/2008, 7/1/2009//                after changes to the code).
//                If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications. 
//                
//                This library is free software; you can redistribute it and/or
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





#include "ZeissAxioZoom.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>


ZeissHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Zeiss Devices
const char* g_ZeissDeviceName = "ZeissScope";

// convenience strings
const char* g_COperationMode = "Operation Mode";
const char* g_CExternalShutter = "Shutter";
const char* g_focusMethod = "Focus Method";
const char* g_focusThisPosition = "Measure";
const char* g_focusLastPosition = "Last Position";
const char* g_focusApplyPosition = "Apply";

///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZeissDeviceName, MM::GenericDevice, "Zeiss AxioZoom controlled through serial interface");
   RegisterDevice(g_ZeissMotorFocus, MM::StageDevice, "Motor Focus");
   RegisterDevice(g_ZeissXYStage, MM::XYStageDevice, "XYStage");
   RegisterDevice(g_ZeissOpticsUnit, MM::GenericDevice, "OpticsUnit");
   RegisterDevice(g_ZeissFluoTube, MM::ShutterDevice, "Reflector"); 
   RegisterDevice(g_ZeissDL450, MM::StateDevice, "DL450");
}

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_ZeissDeviceName) == 0)
      return new ZeissScope();
   else if (strcmp(deviceName, g_ZeissXYStage) == 0)
	   return new XYStageX();
   else if (strcmp(deviceName, g_ZeissMotorFocus) == 0)
      return new MotorFocus(g_ZeissMotorFocus, "MotorFocus");
   else if (strcmp(deviceName, g_ZeissOpticsUnit) == 0)
      return new OpticsUnit(g_ZeissOpticsUnit, "OpticsUnit");
   else if (strcmp(deviceName, g_ZeissFluoTube) == 0)
      return new FluoTube();
   else if (strcmp(deviceName, g_ZeissDL450) == 0)
      return new LampDL450();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// ZeissScope
//
ZeissScope::ZeissScope() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(500)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_ANSWER_TIMEOUT, "The Zeiss microscope does not answer.  Is it switched on and connected to this computer?");
   SetErrorText(ERR_PORT_NOT_OPEN, "The communication port to the microscope can not be opened");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZeissDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Zeiss microscope CAN bus adapter", MM::String, true);
   
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissScope::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // AnswerTimeOut                                                                  
   pAct = new CPropertyAction (this, &ZeissScope::OnAnswerTimeOut);
   CreateProperty("AnswerTimeOut", "250", MM::Float, false, pAct, true);
}

ZeissScope::~ZeissScope() 
{
   if (g_hub.monitoringThread_ != 0) {
      g_hub.monitoringThread_->Stop();
      g_hub.monitoringThread_->wait();
      delete g_hub.monitoringThread_;
      g_hub.monitoringThread_ = 0;
   }
   g_hub.scopeInitialized_ = false;
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZeissScope::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_hub.port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // revert
         pProp->Set(g_hub.port_.c_str());
         // return ERR_PORT_CHANGE_FORBIDDEN;
      } else {
         // take this port.  TODO: should we check if this is a valid port?
         pProp->Get(g_hub.port_);
         // set flags indicating we have a port
         g_hub.portInitialized_ = true;
         initialized_ = true;
      }
   }

   return DEVICE_OK;
}

int ZeissScope::OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_hub.GetTimeOutTime().getMsec());
   } else if (eAct == MM::AfterSet) {
      double tmp;
      pProp->Get(tmp);
      g_hub.SetTimeOutTime(MM::MMTime(tmp * 1000.0));
   }

   return DEVICE_OK;
}

int ZeissScope::Initialize() 
{
   // Version
   string version;
   int ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Microscope Version", version.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
    
   ret = UpdateStatus();
   if (DEVICE_OK != ret)
      return ret;

   initialized_ = true;
   return 0;
}

int ZeissScope::Shutdown() 
{
   return 0;
}

void ZeissScope::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDeviceName);
}

bool ZeissScope::Busy() 
{
   return false;
}