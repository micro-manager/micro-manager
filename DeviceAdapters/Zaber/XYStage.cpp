///////////////////////////////////////////////////////////////////////////////
// FILE:          XYStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   XYStage Device Adapter
//                
// AUTHOR:        Soleil Lapierre, David Goosen & Athabasca Witschi (contact@zaber.com)
//                
// COPYRIGHT:     Zaber Technologies, 2017
//
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
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "XYStage.h"

const char* g_XYStageName = "XYStage";
const char* g_XYStageDescription = "Zaber XY Stage";

using namespace std;


////////////////////////////////////////////////////////////////////////////////
// XYStage & Device API methods
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
	ZaberBase(this),
	deviceAddressX_(1),
	deviceAddressY_(1),
	deviceAddressYInitialized_(false),
	rangeMeasured_(false),
	homingTimeoutMs_(20000),
	stepSizeXUm_(0.15625), //=1000*pitch[mm]/(motorsteps*64)  (for ASR100B120B: pitch=2 mm, motorsteps=200)
	stepSizeYUm_(0.15625),
	convFactor_(1.6384),
	axisX_(2),
	axisY_(1),
	resolutionX_(64),
	resolutionY_(64),
	motorStepsX_(200),
	motorStepsY_(200),
	linearMotionX_(2.0),
	linearMotionY_(2.0)
{
	this->LogMessage("XYStage::XYStage\n", true);

	InitializeDefaultErrorMessages();
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, g_Msg_PORT_CHANGE_FORBIDDEN);
	SetErrorText(ERR_DRIVER_DISABLED, g_Msg_DRIVER_DISABLED);
	SetErrorText(ERR_BUSY_TIMEOUT, g_Msg_BUSY_TIMEOUT);
	SetErrorText(ERR_AXIS_COUNT, g_Msg_AXIS_COUNT);
	SetErrorText(ERR_COMMAND_REJECTED, g_Msg_COMMAND_REJECTED);
	SetErrorText(ERR_NO_REFERENCE_POS, g_Msg_NO_REFERENCE_POS);
	SetErrorText(ERR_SETTING_FAILED, g_Msg_SETTING_FAILED);
	SetErrorText(ERR_INVALID_DEVICE_NUM, g_Msg_INVALID_DEVICE_NUM);

	// Pre-initialization properties
	CreateProperty(MM::g_Keyword_Name, g_XYStageName, MM::String, true);
	CreateProperty(MM::g_Keyword_Description, "Zaber XY stage driver adapter", MM::String, true);

	CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	pAct = new CPropertyAction (this, &XYStage::OnDeviceAddress);
	CreateIntegerProperty("Controller Device Number", deviceAddressX_, false, pAct, true);
	SetPropertyLimits("Controller Device Number", 1, 99);
	
	pAct = new CPropertyAction (this, &XYStage::OnDeviceAddressY);
	CreateStringProperty("Controller Device Number (Y Axis)", "", false, pAct, true);

	pAct = new CPropertyAction(this, &XYStage::OnAxisX);
	CreateIntegerProperty("Axis Number (X Axis)", axisX_, false, pAct, true);
	SetPropertyLimits("Axis Number (X Axis)", 1, 9);

	pAct = new CPropertyAction(this, &XYStage::OnAxisY);
	CreateIntegerProperty("Axis Number (Y Axis)", axisY_, false, pAct, true);
	SetPropertyLimits("Axis Number (Y Axis)", 1, 9);

	pAct = new CPropertyAction(this, &XYStage::OnMotorStepsX);
	CreateIntegerProperty("Motor Steps Per Rev (X Axis)", motorStepsX_, false, pAct, true);

	pAct = new CPropertyAction(this, &XYStage::OnMotorStepsY);
	CreateIntegerProperty("Motor Steps Per Rev (Y Axis)", motorStepsY_, false, pAct, true);

	pAct = new CPropertyAction(this, &XYStage::OnLinearMotionX);
	CreateFloatProperty("Linear Motion Per Motor Rev (X Axis) [mm]", linearMotionX_, false, pAct, true);

	pAct = new CPropertyAction(this, &XYStage::OnLinearMotionY);
	CreateFloatProperty("Linear Motion Per Motor Rev (Y Axis) [mm]", linearMotionY_, false, pAct, true);

	this->SetProperty(MM::g_Keyword_Transpose_MirrorX, "0");
	this->SetProperty(MM::g_Keyword_Transpose_MirrorY, "0");
}


XYStage::~XYStage()
{
	this->LogMessage("XYStage::~XYStage\n", true);
	Shutdown();
}


////////////////////////////////////////////////////////////////////////////////
// XYStage & Device API methods
///////////////////////////////////////////////////////////////////////////////

int XYStage::Initialize()
{
	if (initialized_) 
	{
		return DEVICE_OK;
	}

	core_ = GetCoreCallback();
	
	this->LogMessage("XYStage::Initialize\n", true);

	int ret = ClearPort();
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// Disable alert messages.
	ret = SetSetting(deviceAddressX_, 0, "comm.alert", 0);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	if (!IsSingleController())
	{
		ret = SetSetting(deviceAddressY_, 0, "comm.alert", 0);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}
	else
	{
		// Ensure dual-axis controller.
		long axisCount;
		ret = GetSetting(deviceAddressX_, 0, "system.axiscount", axisCount);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}

		if (axisCount < 2)
		{
			return ERR_AXIS_COUNT;
		}
	}

	// Calculate step size.
	ret = GetSetting(deviceAddressX_, axisX_, "resolution", resolutionX_);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	ret = GetSetting(deviceAddressY_, axisY_, "resolution", resolutionY_);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	stepSizeXUm_ = ((double)linearMotionX_/(double)motorStepsX_)*(1/(double)resolutionX_)*1000;
	stepSizeYUm_ = ((double)linearMotionY_/(double)motorStepsY_)*(1/(double)resolutionY_)*1000;

	CPropertyAction* pAct;
	// Initialize Speed (in mm/s)
	pAct = new CPropertyAction (this, &XYStage::OnSpeedX);
	ret = CreateFloatProperty("Speed X [mm/s]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
	ret = CreateFloatProperty("Speed Y [mm/s]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// Initialize Acceleration (in m/s�)
	pAct = new CPropertyAction (this, &XYStage::OnAccelX);
	ret = CreateFloatProperty("Acceleration X [m/s^2]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	pAct = new CPropertyAction (this, &XYStage::OnAccelY);
	ret = CreateFloatProperty("Acceleration Y [m/s^2]", 0.0, false, pAct);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	ret = UpdateStatus();
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	initialized_ = true;
	return DEVICE_OK;
}


int XYStage::Shutdown()
{
	this->LogMessage("XYStage::Shutdown\n", true);
	if (initialized_)
	{
		initialized_ = false;
		rangeMeasured_ = false;
	}

	return DEVICE_OK;
}


void XYStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_XYStageName);
}


bool XYStage::Busy()
{
	this->LogMessage("XYStage::Busy\n", true);
	return IsBusy(deviceAddressX_) || ((!IsSingleController()) && IsBusy(deviceAddressY_));
}



int XYStage::GetPositionSteps(long& x, long& y)
{
	this->LogMessage("XYStage::GetPositionSteps\n", true);

	int ret = GetSetting(deviceAddressX_, axisX_, "pos", x);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	return GetSetting(deviceAddressY_, axisY_, "pos", y);
}


int XYStage::SetPositionSteps(long x, long y)
{
	this->LogMessage("XYStage::SetPositionSteps\n", true);
	return SendXYMoveCommand("abs", x, y);
}


int XYStage::SetRelativePositionSteps(long x, long y)
{   
	this->LogMessage("XYStage::SetRelativePositionSteps\n", true);
	return SendXYMoveCommand("rel", x, y);
}


int XYStage::Move(double vx, double vy)
{
	this->LogMessage("XYStage::Move\n", true);

	// convert vx and vy from mm/s to Zaber data values
	long vxData = nint(vx*convFactor_*1000/stepSizeXUm_);
	long vyData = nint(vy*convFactor_*1000/stepSizeXUm_);
	return SendXYMoveCommand("vel", vxData, vyData);
}


int XYStage::Stop()
{
	this->LogMessage("XYStage::Stop\n", true);
	int result1 = ZaberBase::Stop(deviceAddressX_);
	if (IsSingleController())
	{
		return result1;
	}

	// If we are using two controllers, stop should always try to stop both
	// even if the first one produces an error. In the case where they both
	// produce errors, the second error code is returned; otherwise whichever
	// code is not DEVICE_OK.
	int result2 = ZaberBase::Stop(deviceAddressY_);
	if (DEVICE_OK != result2)
	{
		return result2;
	}

	return result1;
}


/** Calibrates the stage then moves it to Micro-Manager's origin.
 * 
 * Assumes the stage is oriented with the long/X axis travelling left-right.
 * "Default" orientation is with cable connectors at the top right,
 * transposeMirrorX = 0, and transposeMirrorY = 0.
 * Home will also go to the Micro-Manager origin if the connectors are
 * at the bottom left, transposeMirrorX = 1, and transposeMirrorY = 1.
 */
int XYStage::Home()
{
	this->LogMessage("XYStage::Home\n", true);

	rangeMeasured_ = false;

	// calibrate stage
	int ret = SendAndPollUntilIdle(deviceAddressX_, 0, "tools findrange", homingTimeoutMs_);
	if (ret == ERR_COMMAND_REJECTED) // Some stages don't support the findrange command. Instead we have to home them before moving.
	{
		ret = SendAndPollUntilIdle(deviceAddressX_, 0, "home", homingTimeoutMs_);
	}

	if (ret != DEVICE_OK)
	{
		return ret;
	}

	if (!IsSingleController())
	{
		ret = SendAndPollUntilIdle(deviceAddressY_, 0, "tools findrange", homingTimeoutMs_);
		if (ret == ERR_COMMAND_REJECTED) // Some stages don't support the findrange command. Instead we have to home them before moving.
		{
			ret = SendAndPollUntilIdle(deviceAddressY_, 0, "home", homingTimeoutMs_);
		}

		if (ret != DEVICE_OK)
		{
			return ret;
		}
	}

	rangeMeasured_ = true;

	// go to origin
	bool mirrorX, mirrorY;
	GetOrientation(mirrorX, mirrorY);

	string cmdX = (mirrorX ? "move max" : "move min");
	ret = SendAndPollUntilIdle(deviceAddressX_, axisX_, cmdX, homingTimeoutMs_);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	string cmdY = (mirrorY ? "move max" : "move min");
	ret = SendAndPollUntilIdle(deviceAddressY_, axisY_, cmdY, homingTimeoutMs_);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}	

	this->LogMessage("XYStage::Home COMPLETE!\n", true);
	return DEVICE_OK;
}


int XYStage::SetOrigin()
/* Sets the Origin of the MM coordinate system using default implementation of SetAdapterOriginUm(x,y) (in DeviceBase.h)
 * The Zaber coordinate system is NOT zeroed.
 */
{
	this->LogMessage("XYStage::SetOrigin\n", true); 
	this->LogMessage("XYStage::SetOrigin calling SetAdapterOriginUm(0,0)\n", true); 
	return SetAdapterOriginUm(0,0);
}


int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	this->LogMessage("XYStage::GetStepLimits\n", true);

	if (!rangeMeasured_)
	{
		return ERR_NO_REFERENCE_POS;
	}
  
	int ret = GetLimits(deviceAddressX_, axisX_, xMin, xMax);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	return GetLimits(deviceAddressY_, axisY_, yMin, yMax);
}


int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
	this->LogMessage("XYStage::GetLimitsUm\n", true); 

	if (!rangeMeasured_) return ERR_NO_REFERENCE_POS;

	long xMinSteps, xMaxSteps, yMinSteps, yMaxSteps;
	int ret = GetStepLimits(xMinSteps, xMaxSteps, yMinSteps, yMaxSteps);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	// convert to microns
	xMin = xMinSteps * stepSizeXUm_;
	xMax = xMaxSteps * stepSizeXUm_;
	yMin = yMinSteps * stepSizeYUm_;
	yMax = yMaxSteps * stepSizeYUm_;

	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Private helper functions
///////////////////////////////////////////////////////////////////////////////

int XYStage::SendXYMoveCommand(string type, long x, long y) const
{
	this->LogMessage("XYStage::SendXYMoveCommand\n", true);

	int ret = SendMoveCommand(deviceAddressX_, axisX_, type, x);
	if (ret != DEVICE_OK) 
	{
		return ret;
	}

	return SendMoveCommand(deviceAddressY_, axisY_, type, y);
}


int XYStage::OnSpeed(long address, long axis, MM::PropertyBase* pProp, MM::ActionType eAct) const
{
	this->LogMessage("XYStage::OnSpeed\n", true);

	double stepSize = (axis == axisX_) ? stepSizeXUm_ : stepSizeYUm_;

	if (eAct == MM::BeforeGet)
	{
		long speedData;
		int ret = GetSetting(address, axis, "maxspeed", speedData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}

		// convert to mm/s
		double speed = (speedData/convFactor_)*stepSize/1000;
		pProp->Set(speed);
	}
	else if (eAct == MM::AfterSet)
	{
		double speed;
		pProp->Get(speed);

		// convert to data
		long speedData = nint(speed*convFactor_*1000/stepSize);
		if (speedData == 0 && speed != 0) speedData = 1; // Avoid clipping to 0.

		int ret = SetSetting(address, axis, "maxspeed", speedData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}

	return DEVICE_OK;
}


int XYStage::OnAccel(long address, long axis, MM::PropertyBase* pProp, MM::ActionType eAct) const
{
	this->LogMessage("XYStage::OnAccel\n", true);

	double stepSize = (axis == axisX_) ? stepSizeXUm_ : stepSizeYUm_;

	if (eAct == MM::BeforeGet)
	{
		long accelData;
		int ret = GetSetting(address, axis, "accel", accelData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}

		// convert to m/s�
		double accel = (accelData*10/convFactor_)*stepSize/1000;
		pProp->Set(accel);
	}
	else if (eAct == MM::AfterSet)
	{
		double accel;
		pProp->Get(accel);

		// convert to data
		long accelData = nint(accel*convFactor_*100/(stepSize));
		if (accelData == 0 && accel != 0) accelData = 1; // Only set accel to 0 if user intended it.

		int ret = SetSetting(address, axis, "accel", accelData);
		if (ret != DEVICE_OK) 
		{
			return ret;
		}
	}

	return DEVICE_OK;
}


void XYStage::GetOrientation(bool& mirrorX, bool& mirrorY) 
{
	// copied from DeviceBase.h
	this->LogMessage("XYStage::GetOrientation\n", true);

	char val[MM::MaxStrLength];
	int ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorX, val);
	assert(ret == DEVICE_OK);
	mirrorX = strcmp(val, "1") == 0 ? true : false;

	ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorY, val);
	assert(ret == DEVICE_OK);
	mirrorY = strcmp(val, "1") == 0 ? true : false;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
	ostringstream os;
	os << "XYStage::OnPort(" << pProp << ", " << eAct << ")\n";
	this->LogMessage(os.str().c_str(), false);


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


int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
	this->LogMessage("XYStage::OnSpeedX\n", true);

	return OnSpeed(deviceAddressX_, axisX_, pProp, eAct);
}


int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnSpeedY\n", true);

	return OnSpeed(deviceAddressY_, axisY_, pProp, eAct);
}


int XYStage::OnAccelX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnAccelX\n", true);

	return OnAccel(deviceAddressX_, axisX_, pProp, eAct);
}


int XYStage::OnAccelY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnAccelY\n", true);

	return OnAccel(deviceAddressY_, axisY_, pProp, eAct);
}


int XYStage::OnAxisX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnAxisX\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axisX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(axisX_);
	}

	return DEVICE_OK;
}


int XYStage::OnAxisY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnAxisY\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axisY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(axisY_);
	}

	return DEVICE_OK;
}


int XYStage::OnMotorStepsX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnMotorStepsX\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(motorStepsX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(motorStepsX_);
	}

	return DEVICE_OK;
}


int XYStage::OnMotorStepsY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnMotorStepsY\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(motorStepsY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(motorStepsY_);
	}

	return DEVICE_OK;
}


int XYStage::OnLinearMotionX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::LinearMotionX\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(linearMotionX_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(linearMotionX_);
	}

	return DEVICE_OK;
}


int XYStage::OnLinearMotionY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::LinearMotionY\n", true);

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(linearMotionY_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(linearMotionY_);
	}

	return DEVICE_OK;
}


// Handle controller address changes for the X-axis or two-axis controller.
int XYStage::OnDeviceAddress(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnDeviceAddress\n", true);

	if (eAct == MM::AfterSet)
	{
		pProp->Get(deviceAddressX_);

		// If the Y-axis address has not been set yet, initialize it to be the same
		// as the X-axis address. This is to aid carrying over legacy configuration files
		// and so users only have to set one property if they are using a two-axis controller.
		if (!deviceAddressYInitialized_)
		{
			deviceAddressY_ = deviceAddressX_;
			UpdateProperty("Controller Device Number (Y Axis)");
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(deviceAddressX_);
	}

	return DEVICE_OK;
}


// Handle Y-axis controller address changes for the composite X-Y case (two different controllers).
// This property is registered as a string so that null or empty are legal values, and we
// can default to using a single device number in that case.
int XYStage::OnDeviceAddressY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	this->LogMessage("XYStage::OnDeviceAddressY\n", true);

	if (eAct == MM::AfterSet)
	{
		std::string numBuf;
		pProp->Get(numBuf);
		if (numBuf.length() > 0)
		{
			long val = atol(numBuf.c_str());
			if ((val < 1) || (val > 99))
			{
				return ERR_INVALID_DEVICE_NUM;
			}

			deviceAddressY_ = val;
			deviceAddressYInitialized_ = true;
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		ostringstream num;
		if (deviceAddressYInitialized_)
		{
			num << deviceAddressY_;
		}

		pProp->Set(num.str().c_str());
	}

	return DEVICE_OK;
}
