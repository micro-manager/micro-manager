///////////////////////////////////////////////////////////////////////////////
// FILE:          PIZStage_DLL.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL ZStage
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PIZStage_DLL.cpp,v 1.7, 2011-09-20 08:31:11Z, Steffen Rau$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PIZStage_DLL.h"
#include "Controller.h"

const char* PIZStage::DeviceName_ = "PIZStage";
const char* g_PI_ZStageAxisName = "Axis";
const char* g_PI_ZStageAxisLimitUm = "Limit_um";
const char* g_PI_ZStageStageType = "Stage";
//const char* g_PI_ZStageHoming = "HomingMode";
const char* g_PI_ZStageStepSize = "StepSizeUm";
const char* g_PI_ZStageControllerName = "Controller Name";




///////////////////////////////////////////////////////////////////////////////
// PIZStage

PIZStage::PIZStage() :
   stepSizeUm_(0.1),
   initialized_(false),
    controllerName_(""),
    axisLimitUm_(500.0),
   axisName_("A"),
   stageType_("DEFAULT_STAGE"),
   ctrl_(NULL)
   //homingMode_("REF")
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS, g_msg_CNTR_POS_OUT_OF_LIMITS);
   SetErrorText(ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO, g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO);
   SetErrorText(ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL, g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL);

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, DeviceName_, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) GCS DLL Adapter", MM::String, true);

   CPropertyAction* pAct;


   // Controller name
   pAct = new CPropertyAction (this, &PIZStage::OnControllerName);
   CreateProperty(g_PI_ZStageControllerName, controllerName_.c_str(), MM::String, false, pAct, true);

   // Axis name
   pAct = new CPropertyAction (this, &PIZStage::OnAxisName);
   CreateProperty(g_PI_ZStageAxisName, axisName_.c_str(), MM::String, false, pAct, true);
   
   // Axis stage type
   pAct = new CPropertyAction (this, &PIZStage::OnStageType);
   CreateProperty(g_PI_ZStageStageType, stageType_.c_str(), MM::String, false, pAct, true);
   
   //// Axis homing mode
   //pAct = new CPropertyAction (this, &PIZStage::OnHoming);
   //CreateProperty(g_PI_ZStageHoming, homingMode_.c_str(), MM::String, false, pAct, true);
   
   // axis limit in um
   pAct = new CPropertyAction (this, &PIZStage::OnAxisLimit);
   CreateProperty(g_PI_ZStageAxisLimitUm, "500.0", MM::Float, false, pAct, true);

   // axis limits (assumed symmetrical)
   pAct = new CPropertyAction (this, &PIZStage::OnPosition);
   CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(MM::g_Keyword_Position, 0/*-axisLimitUm_*/, axisLimitUm_);

}

PIZStage::~PIZStage()
{
   Shutdown();
}

void PIZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, DeviceName_);
}

int PIZStage::Initialize()
{
   MM::Device* device = GetDevice(controllerName_.c_str());
   if (device == NULL)
	   return DEVICE_ERR;//ERR_GCS_PI_NO_CONTROLLER_FOUND;

   int ret = device->Initialize();
   if (ret != DEVICE_OK)
	   return ret;

   ctrl_ = PIController::GetByLabel(controllerName_);
   if (ctrl_ == NULL)
	   return DEVICE_ERR;//ERR_GCS_PI_NO_CONTROLLER_FOUND;

   std::string  sBuffer;
   ctrl_->qIDN(sBuffer);
   LogMessage(std::string("Connected to: ") + sBuffer);

   ret = ctrl_->InitStage(axisName_, stageType_);
   if (ret != DEVICE_OK)
   {
	   LogMessage("Cannot init axis");
	   return ret;
   }

     // axis limits (assumed symmetrical)
   CPropertyAction* pAct = new CPropertyAction (this, &PIZStage::OnHoming);
   CreateProperty("HOMING", "", MM::String, false, pAct);

   pAct = new CPropertyAction (this, &PIZStage::OnVelocity);
   CreateProperty("Velocity", "", MM::Float, false, pAct);

   initialized_ = true;
   return DEVICE_OK;
}

int PIZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool PIZStage::Busy()
{
   return ctrl_->IsBusy();
}

int PIZStage::SetPositionSteps(long steps)
{
   double pos = steps * stepSizeUm_;
   return SetPositionUm(pos);
}

int PIZStage::GetPositionSteps(long& steps)
{
   double pos;
   int ret = GetPositionUm(pos);
   if (ret != DEVICE_OK)
      return ret;
   steps = (long) ((pos / stepSizeUm_) + 0.5);
   return DEVICE_OK;
}
  
int PIZStage::SetPositionUm(double pos)
{
	pos *= ctrl_->umToDefaultUnit_;
	if (ctrl_->MOV(axisName_, &pos) == FALSE)
		return ctrl_->TranslateError();

	return DEVICE_OK;
}

int PIZStage::GetPositionUm(double& pos)
{
	if (ctrl_->qPOS(axisName_, &pos) == FALSE)
		return DEVICE_ERR;

	pos /= ctrl_->umToDefaultUnit_;
	return DEVICE_OK;
}

int PIZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int PIZStage::GetLimits(double& min, double& max)
{
	min = 0;
	max = axisLimitUm_;
	return DEVICE_OK;
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int PIZStage::OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(controllerName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(controllerName_);
   }

   return DEVICE_OK;
}



int PIZStage::OnAxisName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisName_);
   }

   return DEVICE_OK;
}

int PIZStage::OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(stepSizeUm_);
   }

   return DEVICE_OK;
}

int PIZStage::OnAxisLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisLimitUm_);
	  SetPropertyLimits(MM::g_Keyword_Position, 0/*-axisLimitUm_*/, axisLimitUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisLimitUm_);
   }

   return DEVICE_OK;
}

int PIZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         return ret;

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
         return ret;

   }

   return DEVICE_OK;
}

int PIZStage::OnStageType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stageType_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(stageType_);
   }

   return DEVICE_OK;
}

int PIZStage::OnHoming(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
			pProp->Set(0.0);
   }
   else if (eAct == MM::AfterSet)
   {
	   std::string homingMode;
	   pProp->Get(homingMode);
		int ret = ctrl_->Home( axisName_, homingMode );
		if (ret != DEVICE_OK)
			return ret;
   }

   return DEVICE_OK;
}

int PIZStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double velocity = 0.0;
	  if (ctrl_->qVEL(axisName_, &velocity))
	     pProp->Set(velocity);
	  else
         pProp->Set(0.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double velocity = 0.0;
	  pProp->Get(velocity);
      if (!ctrl_->VEL( axisName_, &velocity ))
         return ctrl_->TranslateError();
   }

   return DEVICE_OK;
}
