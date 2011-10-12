///////////////////////////////////////////////////////////////////////////////
// FILE:          PIXYStage_DLL.cpp
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
// CVS:           $Id: PIXYStage_DLL.cpp,v 1.10, 2011-08-05 05:35:45Z, Steffen Rau$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PIXYStage_DLL.h"
#include "Controller.h"

const char* PIXYStage::DeviceName_ = "PIXYStage";

const char* g_PI_XYStageAxisXName = "Axis X: Name";
const char* g_PI_XYStageAxisXStageType = "Axis X: Stage";
const char* g_PI_XYStageAxisXHoming = "Axis X: HomingMode";
const char* g_PI_XYStageAxisYName = "Axis Y: Name";
const char* g_PI_XYStageAxisYStageType = "Axis Y: Stage";
const char* g_PI_XYStageAxisYHoming = "Axis Y: HomingMode";
const char* g_PI_XYStageControllerName = "Controller Name";
const char* g_PI_XYStageControllerNameYAxis = "Controller Name for Y axis";


// valid interface types: "PCI", "RS-232"
// for Interface type "RS-232" Interface Parameter is a string: "<portnumber>;<baudrate>"
// for Interface type "PCI" Interface Parameter is a string: "<board index>"

// valid homing modes: "REF", "PLM", "NLM"

///////////////////////////////////////////////////////////////////////////////
// PIXYStage

PIXYStage::PIXYStage() :
   CXYStageBase<PIXYStage>(),
   axisXName_("A"),
   axisXStageType_("DEFAULT_STAGE"),
   axisXHomingMode_("REF"),
   axisYName_("B"),
   axisYStageType_("DEFAULT_STAGE"),
   axisYHomingMode_("REF"),
   controllerName_(""),
   controllerNameYAxis_(""),
   stepSize_um_(0.01),
   initialized_(false),
   originX_(0.0),
   originY_(0.0),
   ctrl_(NULL)
   //answerTimeoutMs_(1000),
   //axisLimitUm_(500.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   SetErrorText(ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS, g_msg_CNTR_POS_OUT_OF_LIMITS);
   SetErrorText(ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO, g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO);
   SetErrorText(ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL, g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL);

   // Name
   CreateProperty(MM::g_Keyword_Name, DeviceName_, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) GCS DLL Adapter", MM::String, true);

   CPropertyAction* pAct;

   // Controller name
   pAct = new CPropertyAction (this, &PIXYStage::OnControllerName);
   CreateProperty(g_PI_XYStageControllerName, controllerName_.c_str(), MM::String, false, pAct, true);

   // Axis X name
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisXName);
   CreateProperty(g_PI_XYStageAxisXName, axisXName_.c_str(), MM::String, false, pAct, true);
   
   // Axis X stage type
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisXStageType);
   CreateProperty(g_PI_XYStageAxisXStageType, axisXStageType_.c_str(), MM::String, false, pAct, true);
   
   // Axis X homing mode
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisXHoming);
   CreateProperty(g_PI_XYStageAxisXHoming, axisXHomingMode_.c_str(), MM::String, false, pAct, true);
   
   // Axis Y name
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisYName);
   CreateProperty(g_PI_XYStageAxisYName, axisYName_.c_str(), MM::String, false, pAct, true);
   
   // Axis Y stage type
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisYStageType);
   CreateProperty(g_PI_XYStageAxisYStageType, axisYStageType_.c_str(), MM::String, false, pAct, true);
   
   // Axis Y homing mode
   pAct = new CPropertyAction (this, &PIXYStage::OnAxisYHoming);
   CreateProperty(g_PI_XYStageAxisYHoming, axisYHomingMode_.c_str(), MM::String, false, pAct, true);

   // Controller name for Y axis
   pAct = new CPropertyAction (this, &PIXYStage::OnControllerNameYAxis);
   CreateProperty(g_PI_XYStageControllerNameYAxis, controllerNameYAxis_.c_str(), MM::String, false, pAct, true);
}

PIXYStage::~PIXYStage()
{
   Shutdown();
}

void PIXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, DeviceName_);
}

int PIXYStage::Initialize()
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

   std::string sBuffer;
   ctrl_->qIDN(sBuffer);
   LogMessage(std::string("Connected to: ") + sBuffer);

   ctrlYAxis_ = NULL;
   PIController* ctrlForYAxis = ctrl_;
   if (controllerNameYAxis_ != "")
   {
	   MM::Device* device = GetDevice(controllerNameYAxis_.c_str());
	   if (device == NULL)
		   return DEVICE_ERR;//ERR_GCS_PI_NO_CONTROLLER_FOUND;

	   int ret = device->Initialize();
	   if (ret != DEVICE_OK)
		   return ret;

	   ctrlYAxis_ = PIController::GetByLabel(controllerNameYAxis_);
	   if (ctrlYAxis_ == NULL)
		   return DEVICE_ERR;//ERR_GCS_PI_NO_CONTROLLER_FOUND;

	   std::string sBuffer;
	   ctrlYAxis_->qIDN(sBuffer);
	   LogMessage(std::string("Y axis Connected to: ") + sBuffer);
	   ctrlForYAxis = ctrlYAxis_;
   }

   ret = ctrl_->InitStage(axisXName_, axisXStageType_);
   if (ret != DEVICE_OK)
   {
	   LogMessage("Cannot init axis x");
	   return ret;
   }

   ret = ctrlForYAxis->InitStage(axisYName_, axisYStageType_);
   if (ret != DEVICE_OK)
   {
	   LogMessage("Cannot init axis y");
	   return ret;
   }

   CPropertyAction* pAct = new CPropertyAction (this, &PIXYStage::OnXVelocity);
   CreateProperty("Axis X: Velocity", "", MM::Float, false, pAct);

   pAct = new CPropertyAction (this, &PIXYStage::OnYVelocity);
   CreateProperty("Axis Y: Velocity", "", MM::Float, false, pAct);

   initialized_ = true;
   return DEVICE_OK;
}

int PIXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool PIXYStage::Busy()
{
   bool busy = ctrl_->IsBusy();
   if (ctrlYAxis_ != NULL)
   {
	   busy |= ctrlYAxis_->IsBusy();
   }
   return busy;
}

double PIXYStage::GetStepSize()
{
	return stepSize_um_;
}

int PIXYStage::SetPositionSteps(long x, long y)
{
	//if (ctrl_->MOV_ == NULL)
	//	return DEVICE_UNSUPPORTED_COMMAND;

	double umToDefaultUnitYAxis = ctrl_->umToDefaultUnit_;
	if (ctrlYAxis_ != NULL)
	{
		umToDefaultUnitYAxis = ctrlYAxis_->umToDefaultUnit_;
	}

	double pos[2];
	pos[0] = x * stepSize_um_ * ctrl_->umToDefaultUnit_;
	pos[1] = y * stepSize_um_ * umToDefaultUnitYAxis;
	
	pos[0] += originX_;
	pos[1] += originY_;
	if (ctrlYAxis_ == NULL)
	{
		if (!ctrl_->MOV(axisXName_, axisYName_, pos))
			return ctrl_->TranslateError();
	}
	else
	{
		if (!ctrl_->MOV(axisXName_, &(pos[0])))
			return ctrl_->TranslateError();
		if (!ctrlYAxis_->MOV(axisYName_, &(pos[1])))
			return ctrlYAxis_->TranslateError();
	}

	return DEVICE_OK;

}

int PIXYStage::GetPositionSteps(long &x, long &y)
{
	double pos[2];
	double umToDefaultUnitYAxis = ctrl_->umToDefaultUnit_;
	if (ctrlYAxis_ == NULL)
	{
		if (!ctrl_->qPOS(axisXName_, axisYName_, pos))
			return ctrl_->TranslateError();
	}
	else
	{
		if (!ctrl_->qPOS(axisXName_, &(pos[0])))
			return ctrl_->TranslateError();
		if (!ctrlYAxis_->qPOS(axisYName_, &(pos[1])))
			return ctrlYAxis_->TranslateError();
		umToDefaultUnitYAxis = ctrlYAxis_->umToDefaultUnit_;
	}

	pos[0] -= originX_;
	pos[1] -= originY_;

	x = long(pos[0] / (stepSize_um_ * ctrl_->umToDefaultUnit_));
	y = long(pos[1] / (stepSize_um_ * umToDefaultUnitYAxis));
	return DEVICE_OK;
}

int PIXYStage::Home()
{
	if (	(axisXHomingMode_ == axisYHomingMode_)
		&&	(ctrlYAxis_ == NULL)
		)
	{
		int err = ctrl_->Home( ctrl_->MakeAxesString(axisXName_, axisYName_), axisXHomingMode_ );
		if (err != DEVICE_OK)
			return err;
		//while( Busy() ) {};
		return DEVICE_OK;
	}

	int ret = ctrl_->Home( axisXName_, axisXHomingMode_ );
	if (ret != DEVICE_OK)
		return ret;

	while( Busy() ) {};

	if (ctrlYAxis_ == NULL)
	{
		ret = ctrl_->Home( axisYName_, axisYHomingMode_ );
	}
	else
	{
		ret = ctrlYAxis_->Home( axisYName_, axisYHomingMode_ );
	}
	if (ret != DEVICE_OK)
		return ret;

	//while( Busy() ) {};

	return DEVICE_OK;
}

int PIXYStage::Stop()
{
	//if (ctrl_->STP_ == NULL)
	//	return DEVICE_UNSUPPORTED_COMMAND;
	bool bStop1 = ctrl_->STP();
	bool bStop2 = true;
	if (ctrlYAxis_ != NULL)
	{
		bStop2 = ctrlYAxis_->STP();
	}
	if (bStop1 && bStop2)
		return DEVICE_OK;
	return DEVICE_ERR;
}

int PIXYStage::SetOrigin()
{
	// Todo: use DFH() is possible
	double pos[2];
	if (ctrlYAxis_ == NULL)
	{
		if (!ctrl_->qPOS(axisXName_, axisYName_, &(pos[0])))
			return DEVICE_ERR;
	}
	else
	{
		if (!ctrl_->qPOS(axisXName_, &(pos[0])))
			return DEVICE_ERR;
		if (!ctrlYAxis_->qPOS(axisYName_, &(pos[1])))
			return DEVICE_ERR;
	}

	originX_ = pos[0];
	originY_ = pos[1];

	return DEVICE_OK;
}

int PIXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int PIXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

double PIXYStage::GetStepSizeXUm()
{
	return stepSize_um_;
}

double PIXYStage::GetStepSizeYUm()
{
	return stepSize_um_;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int PIXYStage::OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int PIXYStage::OnControllerNameYAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(controllerNameYAxis_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(controllerNameYAxis_);
   }

   return DEVICE_OK;
}


int PIXYStage::OnAxisXName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisXName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisXName_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnAxisXStageType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisXStageType_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisXStageType_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnAxisXHoming(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisXHomingMode_ .c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisXHomingMode_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnAxisYName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisYName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisYName_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnAxisYStageType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisYStageType_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisYStageType_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnAxisYHoming(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisYHomingMode_ .c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisYHomingMode_);
   }

   return DEVICE_OK;
}

int PIXYStage::OnXVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double velocity = 0.0;
	  if (ctrl_->qVEL(axisXName_, &velocity))
	     pProp->Set(velocity);
	  else
         pProp->Set(0.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double velocity = 0.0;
	  pProp->Get(velocity);
      if (!ctrl_->VEL( axisXName_, &velocity ))
         return ctrl_->TranslateError();
   }

   return DEVICE_OK;
}


int PIXYStage::OnYVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   PIController* ctrl = ctrl_;
   if (ctrlYAxis_ != NULL)
	   ctrl = ctrlYAxis_;

   if (eAct == MM::BeforeGet)
   {
      double velocity = 0.0;
	  if (ctrl->qVEL(axisYName_, &velocity))
	     pProp->Set(velocity);
	  else
         pProp->Set(0.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double velocity = 0.0;
	  pProp->Get(velocity);
      if (!ctrl->VEL( axisYName_, &velocity ))
         return ctrl_->TranslateError();
      return DEVICE_OK;
   }

   return DEVICE_OK;
}
