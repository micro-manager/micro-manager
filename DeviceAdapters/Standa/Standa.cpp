///////////////////////////////////////////////////////////////////////////////
// FILE:          Standa.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Standa device adapters: 8SMC1-USBhF Microstep Driver
//
// COPYRIGHT:     Leslie Lab, McGill University, Montreal, 2013
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Clarence Leung, clarence.leung@mail.mcgill.ca, 2013
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Standa.h"

const char* StandaZStage::DeviceName_ = "StandaZStage";
const char* g_Standa_ZStageAxisLimitUm = "Limit_um";
const char* g_Standa_ZStageDeviceNumber = "Device Number";

static USMC_Devices deviceList;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(StandaZStage::DeviceName_, MM::StageDevice, "Standa Z Stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
   {
      return 0;
   }

   if (strcmp(deviceName, StandaZStage::DeviceName_) == 0)
   {
      StandaZStage* s = new StandaZStage();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// ZStage

StandaZStage::StandaZStage() :
   initialized_(false),
   stepSizeUm_(0.001),
   axisLimitUm_(500.0),
   stepDivisor_(8),
   stageSpeed_(2000.0),
   timeOutTimer_(0),
   deviceString_(""),
   deviceNumber_(0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, StandaZStage::DeviceName_, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Standa Z-stage driver adapter", MM::String, true);

   CPropertyAction* pAct;

   // Device Number
   pAct = new CPropertyAction (this, &StandaZStage::OnDeviceNumber);
   CreateProperty(g_Standa_ZStageDeviceNumber, "Undefined", MM::String, false, pAct, true);

   // Axis Limit in um
   pAct = new CPropertyAction (this, &StandaZStage::OnAxisLimit);
   CreateProperty(g_Standa_ZStageAxisLimitUm, "500.0", MM::Float, false, pAct, true);

   // Axis limits (assumed symmetrical)
   pAct = new CPropertyAction (this, &StandaZStage::OnPosition);
   CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(MM::g_Keyword_Position, 0, axisLimitUm_);
}

StandaZStage::~StandaZStage()
{
   Shutdown();
}

void StandaZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, StandaZStage::DeviceName_);
}

bool StandaZStage::Busy()
{
   if (timeOutTimer_ == 0)
   {
      return false;
   }

   if (timeOutTimer_->expired(GetCurrentMMTime()))
   {
      return false;
   }

   return true;
}

int StandaZStage::Initialize()
{
   // Initialize the list of devices to search for the stage
   if (USMC_Init(deviceList))
   {
      return DEVICE_ERR;
   }

   // Parse the device number from the user-entered string
   deviceNumber_ = atoi(deviceString_.c_str());

   // Attempt to get the current state of the device
   if (USMC_GetState(deviceNumber_, currentState_))
   {
      return DEVICE_ERR;
   }

   // Attempt to get the current position, in steps
   int ret = GetPositionSteps(curSteps_);

   if (ret != DEVICE_OK)
   {
      return ret;
   }

   initialized_ = true;
   return DEVICE_OK;
}

int StandaZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }

   return DEVICE_OK;
}

int StandaZStage::GetPositionUm(double &pos)
{
   long steps;
   int ret = GetPositionSteps(steps);

   if (ret != DEVICE_OK)
   {
      return ret;
   }

   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}

int StandaZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   int ret = SetPositionSteps(steps);

   if (ret != DEVICE_OK)
   {
      return ret;
   }

   return OnStagePositionChanged(pos);
}

int StandaZStage::GetPositionSteps(long& steps) {
   // Note that steps on the Standa stage refer to micro-steps,
   // which are one-eighth of a full step.
   if (USMC_GetState(deviceNumber_, currentState_))
   {
      return DEVICE_ERR;
   }

   steps = (long) currentState_.CurPos;

   return DEVICE_OK;
}

int StandaZStage::SetPositionSteps(long steps)
{
   // Get the initial starting parameters
   if (USMC_GetStartParameters(deviceNumber_, startParameters_))
   {
      return DEVICE_ERR;
   }

   // Set the step divisor to our desired step divisor
   startParameters_.SDivisor = stepDivisor_;

   // Start the movement of the stepper motor
   if (USMC_Start(deviceNumber_, (int) steps, stageSpeed_, startParameters_))
   {
      return DEVICE_ERR;
   }

   return DEVICE_OK;
}

int StandaZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int StandaZStage::GetLimits(double& min, double& max)
{
   min = 0;
   max = axisLimitUm_;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int StandaZStage::OnAxisLimit(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisLimitUm_);
      SetPropertyLimits(MM::g_Keyword_Position, 0, axisLimitUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisLimitUm_);
   }

   return DEVICE_OK;
}

int StandaZStage::OnDeviceNumber(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(deviceString_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(deviceString_);
   }

   return DEVICE_OK;
}

int StandaZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (!initialized_)
      {
         pProp->Set(0.0);
         return DEVICE_OK;
      }
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
      {
         return ret;
      }

      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
       if (!initialized_)
      {
         return DEVICE_OK;
      }
     double pos;
      pProp->Get(pos);
      int ret = SetPositionUm(pos);
      if (ret != DEVICE_OK)
      {
         return ret;
      }
   }

   return DEVICE_OK;
}
