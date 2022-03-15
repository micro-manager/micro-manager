/*
File:		MCL_NanoDrive_XYStage.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "MCL_Common.h"
#include "MCL_NanoDrive_XYStage.h"

#include <vector>
#include <string>

MCL_NanoDrive_XYStage::MCL_NanoDrive_XYStage():
	calibrationX_(0),
	calibrationY_(0),
	commandedX_(0),
	commandedY_(0),
	firstWriteX_(true),
	firstWriteY_(true),
	handle_(0),
	initialized_(false),
	lowerLimitX_(0),
	lowerLimitY_(0),
	settlingTimeX_ms_(100),
	settlingTimeY_ms_(100),
	upperLimitX_(0),
	upperLimitY_(0)
{
	// Basically only set error messages in the constructor
	InitializeDefaultErrorMessages();

	// MCL error messages 
	SetErrorText(MCL_GENERAL_ERROR, "MCL Error: General Error");
	SetErrorText(MCL_DEV_ERROR, "MCL Error: Error transferring data to device");
	SetErrorText(MCL_DEV_NOT_ATTACHED, "MCL Error: Device not attached");
	SetErrorText(MCL_USAGE_ERROR, "MCL Error: Using a function from library device does not support");
	SetErrorText(MCL_DEV_NOT_READY, "MCL Error: Device not ready");
	SetErrorText(MCL_ARGUMENT_ERROR, "MCL Error: Argument out of range");
	SetErrorText(MCL_INVALID_AXIS, "MCL Error: Invalid axis");
	SetErrorText(MCL_INVALID_HANDLE, "MCL Error: Handle not valid");
	SetErrorText(MCL_INVALID_DRIVER, "MCL Error: Invalid Driver");
}

MCL_NanoDrive_XYStage::~MCL_NanoDrive_XYStage()
{
	Shutdown();
}

void MCL_NanoDrive_XYStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

int MCL_NanoDrive_XYStage::Initialize()
{
	int err = DEVICE_OK;

	HandleListLock();
	err = InitDeviceAdapter();
	HandleListUnlock();

	return err;
}

int MCL_NanoDrive_XYStage::Shutdown()
{
	HandleListLock();

	HandleListType device(handle_, XY_TYPE, axisX_, axisY_);
	HandleListRemoveSingleItem(device);
	if (!HandleExistsOnLockedList(handle_))
	{
		MCL_ReleaseHandle(handle_);
	}
	handle_ = 0;
	initialized_ = false;

	HandleListUnlock();

	return DEVICE_OK;
}

double MCL_NanoDrive_XYStage::GetStepSize()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MCL_NanoDrive_XYStage::SetPositionSteps(long x, long y)
{
	return SetPositionUm(x * stepSizeX_um_, y * stepSizeY_um_);
}

int MCL_NanoDrive_XYStage::GetPositionSteps(long &x, long &y)
{
	double xPos = 0.0, yPos = 0.0;
	int err = GetPositionUm(xPos, yPos);
	if (err != DEVICE_OK)
		return err;

	x = (long)(xPos / stepSizeX_um_);
	y = (long)(yPos / stepSizeY_um_);

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetRelativePositionUm(double x, double y)
{
	double comX = 0.0; double comY = 0.0;
	GetLastCommandedPosition(comX, comY);
	double commandFromOriginX = comX + lowerLimitX_;
	double commandFromOriginY = comY + lowerLimitY_;
	return SetPositionUm(x + commandFromOriginX, y + commandFromOriginY);
}

int MCL_NanoDrive_XYStage::Home()
{
	return SetPositionUm(lowerLimitX_, lowerLimitY_);
}

int MCL_NanoDrive_XYStage::Stop()
{
	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetOrigin()
{
	int err;

	// calculate new lower & upper limits
	lowerLimitX_ = -commandedX_;
	upperLimitX_ = calibrationX_ - commandedX_;
	lowerLimitY_ = -commandedY_;
	upperLimitY_ = calibrationY_ - commandedY_;

	// make sure to set the limits for the property browser
	err = SetPropertyLimits(g_Keyword_SetPosXUm, lowerLimitX_, upperLimitX_);
	if (err != DEVICE_OK)
		return err;

	err = SetPropertyLimits(g_Keyword_SetPosYUm, lowerLimitY_, upperLimitY_);
	if (err != DEVICE_OK)
		return err;

	err = UpdateStatus();
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::GetLimits(double&, double&)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MCL_NanoDrive_XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
	xMin = lowerLimitX_;
	xMax = upperLimitX_;
	yMin = lowerLimitY_;
	yMax = upperLimitY_;

	return DEVICE_OK;
} 

int MCL_NanoDrive_XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	xMin = (long)(lowerLimitX_ / stepSizeX_um_);
	yMin = (long)(lowerLimitY_ / stepSizeY_um_);
	xMax = (long)(upperLimitX_ / stepSizeX_um_);
	yMax = (long)(upperLimitY_ / stepSizeY_um_);
	
	return DEVICE_OK;
}

double MCL_NanoDrive_XYStage::GetStepSizeXUm()
{
	return stepSizeX_um_;
}

double MCL_NanoDrive_XYStage::GetStepSizeYUm()
{
	return stepSizeY_um_;
}

int MCL_NanoDrive_XYStage::IsXYStageSequenceable(bool& isStageSequenceable) const
{
	isStageSequenceable = false;
	return DEVICE_OK;
}

//////////////////////
//  ActionHandlers  //
//////////////////////
int MCL_NanoDrive_XYStage::OnLowerLimitX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(lowerLimitX_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newLowerLimit = 0.0;
		pProp->Get(newLowerLimit);

		if (newLowerLimit == lowerLimitX_)
			return DEVICE_OK;

		if (newLowerLimit < -calibrationX_)
			newLowerLimit = -calibrationX_;
		else if (newLowerLimit > 0.0)
			newLowerLimit = 0.0;

		lowerLimitX_ = newLowerLimit;
		upperLimitX_ = newLowerLimit + calibrationX_;
		err = SetPropertyLimits(g_Keyword_SetPosXUm, lowerLimitX_, upperLimitX_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnUpperLimitX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(upperLimitX_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newUpperLimit = 0.0;
		pProp->Get(newUpperLimit);

		if (newUpperLimit == upperLimitX_)
			return DEVICE_OK;

		if (newUpperLimit > calibrationX_)
			newUpperLimit = calibrationX_;
		else if (newUpperLimit < 0.0)
			newUpperLimit = 0.0;

		upperLimitX_ = newUpperLimit;
		lowerLimitX_ = newUpperLimit - calibrationX_;
		err = SetPropertyLimits(g_Keyword_SetPosXUm, lowerLimitX_, upperLimitX_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnLowerLimitY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(lowerLimitY_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newLowerLimit = 0.0;
		pProp->Get(newLowerLimit);

		if (newLowerLimit == lowerLimitY_)
			return DEVICE_OK;

		if (newLowerLimit < -calibrationY_)
			newLowerLimit = -calibrationY_;
		else if (newLowerLimit > 0.0)
			newLowerLimit = 0.0;

		lowerLimitY_ = newLowerLimit;
		upperLimitY_ = newLowerLimit + calibrationY_;
		err = SetPropertyLimits(g_Keyword_SetPosYUm, lowerLimitY_, upperLimitY_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}
int MCL_NanoDrive_XYStage::OnUpperLimitY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(upperLimitY_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newUpperLimit = 0.0;
		pProp->Get(newUpperLimit);

		if (newUpperLimit == upperLimitY_)
			return DEVICE_OK;

		if (newUpperLimit > calibrationY_)
			newUpperLimit = calibrationY_;
		else if (newUpperLimit < 0.0)
			newUpperLimit = 0.0;

		upperLimitY_ = newUpperLimit;
		lowerLimitY_ = newUpperLimit - calibrationY_;
		err = SetPropertyLimits(g_Keyword_SetPosYUm, lowerLimitY_, upperLimitY_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnPositionXUm(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		double xPos = 0.0;
		double yPos = 0.0;
		err = GetPositionUm(xPos, yPos);
		if (err != DEVICE_OK)
			return err;
		pProp->Set(xPos);
	}
	else if (eAct == MM::AfterSet)
	{	
		double xPos = 0.0;
		pProp->Get(xPos);  

		if (xPos > upperLimitX_)
		{
			xPos = upperLimitX_;
		}
		else if (xPos < lowerLimitX_)
		{
			xPos = lowerLimitX_;
		}
		err = SetPositionXUm(xPos, true);
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnPositionYUm(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		double xPos = 0.0;
		double yPos = 0.0;
		err = GetPositionUm(xPos, yPos);
		if (err != DEVICE_OK)
			return err;
		pProp->Set(yPos);
	}
	else if (eAct == MM::AfterSet)
	{
		double yPos = 0.0;
		pProp->Get(yPos);

		if (yPos > upperLimitY_)
		{ 
			yPos = upperLimitY_;
		}
		else if (yPos < lowerLimitY_)
		{ 
			yPos = lowerLimitY_;
		}
		err = SetPositionYUm(yPos, true);
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnSettlingTimeXMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeX_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
		long settleTime;
		pProp->Get(settleTime);

		if (settleTime < 0)
			settleTime = 0;

		settlingTimeX_ms_ = settleTime;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnSettlingTimeYMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeY_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
		long settleTime;
		pProp->Get(settleTime);

		if (settleTime < 0)
			settleTime = 0;

		settlingTimeY_ms_ = settleTime;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string message;
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{ 		
		pProp->Set(g_Value_No);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);
		if (message.compare(g_Value_Yes) == 0)
			err = SetOrigin();
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnCommandChangedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   double ignoreZ;
	   MCL_GetCommandedPosition(&commandedX_, &commandedY_, &ignoreZ, handle_);
	   pProp->Set(commandedX_);
   }

   return err;
}

int MCL_NanoDrive_XYStage::OnCommandChangedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   double ignoreZ;
	   MCL_GetCommandedPosition(&commandedX_, &commandedY_, &ignoreZ, handle_);
	   pProp->Set(commandedY_);
   }

   return err;
}

int MCL_NanoDrive_XYStage::InitDeviceAdapter()
{
	if (initialized_)
		return DEVICE_OK;

	// Attempt to acquire a device/axis for this adapter.
	int ret = MCL_SUCCESS;
	ret = AcquireDeviceHandle(XY_TYPE, handle_, axisX_);
	if (ret != MCL_SUCCESS)
		return ret;
	axisY_ = axisX_ + 1;

	ProductInformation pi;
	memset(&pi, 0, sizeof(ProductInformation));
	ret = MCL_GetProductInfo(&pi, handle_);
	if (ret != MCL_SUCCESS)
		return ret;

	serialNumber_ = MCL_GetSerialNumber(handle_);
	if (serialNumber_ < 0)
		return serialNumber_;

	calibrationX_ = MCL_GetCalibration(axisX_, handle_);
	if (calibrationX_ < 0)
		return (int)calibrationX_;
	lowerLimitX_ = 0.0;
	upperLimitX_ = calibrationX_;

	calibrationY_ = MCL_GetCalibration(axisY_, handle_);
	if (calibrationY_ < 0)
		return (int)calibrationY_;
	lowerLimitY_ = 0.0;
	upperLimitY_ = calibrationY_;

	switch (pi.DAC_resolution)
	{
	case 16:
		stepSizeX_um_ = calibrationX_ / NUM_STEPS_16;
		stepSizeY_um_ = calibrationY_ / NUM_STEPS_16;
		is20Bit_ = false;
		break;
	case 20:
		stepSizeX_um_ = calibrationX_ / NUM_STEPS_20;
		stepSizeY_um_ = calibrationY_ / NUM_STEPS_20;
		is20Bit_ = true;
		break;
	default:
		return MCL_GENERAL_ERROR;
	}
	supportsLastCommanded_ = (pi.FirmwareProfile & PROFILE_BIT_SUPPORTS_LASTCOMMANDED) != 0;

	int err = CreateDeviceProperties();
	if (err != DEVICE_OK)
		return err;

	err = UpdateStatus();
	if (err != DEVICE_OK)
		return err;

	initialized_ = true;

	return err;
}

int MCL_NanoDrive_XYStage::CreateDeviceProperties()
{
	int err = DEVICE_OK;
	int valueBufferSize = 100;
	char valueBuffer[100];

	// Allowed values
	std::vector<std::string> yesNoList;
	yesNoList.push_back(g_Value_No);
	yesNoList.push_back(g_Value_Yes);

	/// Read-only device properties

	// Name property (read-only)
	err = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
	if (err != DEVICE_OK) {
		return err;
	}

	// Description property (read-only)
	err = CreateProperty(MM::g_Keyword_Description, g_Value_XYStageDescription, MM::String, true);
	if (err != DEVICE_OK) {
		return err;
	}

	// See what device you are connected too (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", handle_);
	err = CreateProperty(g_Keyword_Handle, valueBuffer, MM::Integer, true);
	if (err != DEVICE_OK)
		return err;

	// Device serial number (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", MCL_GetSerialNumber(handle_));
	err = CreateProperty(g_Keyword_SerialNumber, valueBuffer, MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// Calibration value for X axis (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", calibrationX_);
	err = CreateProperty(g_Keyword_CalibrationX, valueBuffer, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Calibration value for Y axis (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", calibrationY_);
	err = CreateProperty(g_Keyword_CalibrationY, valueBuffer, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Device axis used. (read-only)
	char* axisDescription = "XY axes";
	switch (axisX_)
	{
	case XAXIS:
		axisDescription[0] = 'X';
		axisDescription[1] = 'Y';
		break;
	case ZAXIS:
		axisDescription[0] = 'Z';
		axisDescription[1] = 'A';
		break;
	}
	err = CreateProperty(g_Keyword_DeviceAxisInUse, axisDescription, MM::String, true);
	if (err != DEVICE_OK)
	{
		return err;
	}

	/// Action properties

	// Lower x limit property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", lowerLimitX_);
	err = CreateProperty(
		g_Keyword_LowerLimitX,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnLowerLimitX)
	);
	if (err != DEVICE_OK)
		return err;	

	// Upper x limit property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", upperLimitX_);
	err = CreateProperty(
		g_Keyword_UpperLimitX,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnUpperLimitX)
	);
	if (err != DEVICE_OK)
		return err;

	// Lower y limit property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", lowerLimitY_);
	err = CreateProperty(
		g_Keyword_LowerLimitY,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnLowerLimitY)
	);
	if (err != DEVICE_OK)
		return err;

	// Upper x limit property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", upperLimitY_);
	err = CreateProperty(
		g_Keyword_UpperLimitY,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnUpperLimitY)
	);
	if (err != DEVICE_OK)
		return err;

	// X position (um)
	double xPos = MCL_SingleReadN(axisX_, handle_);
	if ((int)xPos < 0)
		return (int)xPos;
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", xPos);
	err = CreateProperty(
		g_Keyword_SetPosXUm,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnPositionXUm)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetPropertyLimits(g_Keyword_SetPosXUm, lowerLimitX_, upperLimitX_);
	if (err != DEVICE_OK)
		return err;

	// Y position (um)
	double yPos = MCL_SingleReadN(axisY_, handle_);
	if ((int)yPos < 0)
		return (int)yPos;
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", yPos);
	err = CreateProperty(
		g_Keyword_SetPosYUm,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnPositionYUm)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetPropertyLimits(g_Keyword_SetPosYUm, lowerLimitY_, upperLimitY_);
	if (err != DEVICE_OK)
		return err;

	// Settling time X axis property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", settlingTimeX_ms_);
	err = CreateProperty(
		g_Keyword_SettlingTimeX,
		valueBuffer, 
		MM::Integer,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSettlingTimeXMs)
	);
	if (err != DEVICE_OK)
		return err;

	// Settling time Y axis property
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", settlingTimeY_ms_);
	err = CreateProperty(
		g_Keyword_SettlingTimeY,
		valueBuffer,
		MM::Integer,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSettlingTimeYMs)
	);
	if (err != DEVICE_OK)
		return err;

	// Set the origin at the current position
	err = CreateProperty(
		g_Keyword_SetOrigin,
		g_Value_No,
		MM::String,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSetOrigin)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_SetOrigin, yesNoList);
	if (err != DEVICE_OK)
		return err;
	 
	//Current Commanded Position X
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", commandedX_);
	err = CreateProperty(
		g_Keyword_CommandedX,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnCommandChangedX)
	);
	if (err != DEVICE_OK)
		return err;

	//Current Commanded Position X
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", commandedY_);
	err = CreateProperty(
		g_Keyword_CommandedY,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnCommandChangedY)
	);
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetPositionXUm(double x, bool pauseForSettingTime)
{
	// position to write to is pos (position from origin) - lowerLimit (lower bounds)
	double positionWithoutOriginAdjustment = x - lowerLimitX_;
	if (positionWithoutOriginAdjustment < 0.0)
		positionWithoutOriginAdjustment = 0.0;
	// check if we need to move.
	if ((positionWithoutOriginAdjustment == commandedX_) && !firstWriteX_)
		return DEVICE_OK;

	int err = MCL_SingleWriteN(positionWithoutOriginAdjustment, axisX_, handle_);
	if (err != MCL_SUCCESS)
		return err;
	firstWriteX_ = false;

	// Update the commanded position
	if (supportsLastCommanded_)
	{
		double x, y, z;
		MCL_GetCommandedPosition(&x, &y, &z, handle_);
		switch (axisX_)
		{
		case XAXIS:
			commandedX_ = x;
			break;
		case ZAXIS:
			commandedX_ = z;
			break;
		}
	}
	else
	{
		// Record the command if the device does not keep track.
		commandedX_ = positionWithoutOriginAdjustment;
	}

	if (pauseForSettingTime)
		Sleep(settlingTimeX_ms_);

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetPositionYUm(double y, bool pauseForSettingTime)
{
	// position to write to is pos (position from origin) - lowerLimit (lower bounds)
	double positionWithoutOriginAdjustment = y - lowerLimitY_;
	if (positionWithoutOriginAdjustment < 0.0)
		positionWithoutOriginAdjustment = 0.0;
	// check if we need to move.
	if ((positionWithoutOriginAdjustment == commandedY_) && !firstWriteY_)
		return DEVICE_OK;

	int err = MCL_SingleWriteN(positionWithoutOriginAdjustment, axisY_, handle_);
	if (err != MCL_SUCCESS)
		return err;
	firstWriteY_ = false;

	// Update the commanded position
	if (supportsLastCommanded_ && (axisY_ == YAXIS))
	{
		double x, y, z;
		MCL_GetCommandedPosition(&x, &y, &z, handle_);
		commandedY_ = y;
	}
	else
	{
		// Record the command if the device does not keep track.
		commandedY_ = positionWithoutOriginAdjustment;
	}

	if (pauseForSettingTime)
		Sleep(settlingTimeY_ms_);

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetPositionUm(double x, double y)
{
	int err = DEVICE_OK;
	bool preMoveFirstWriteX = firstWriteX_;
	bool preMoveFirstWriteY = firstWriteY_;
	double preMoveCommandX = commandedX_;
	double preMoveCommandY = commandedY_;
	bool xMoved = false;
	bool yMoved = false;

	err = SetPositionXUm(x, false);
	if (err != DEVICE_OK)
		return err;
	xMoved = preMoveFirstWriteX || (preMoveCommandX != commandedX_);

	err = SetPositionYUm(y, false);
	if (err != DEVICE_OK)
		return err;
	yMoved = preMoveFirstWriteY || (preMoveCommandY != commandedY_);

	// Takes longer for y to move or x didn't move
	if ((!xMoved  && yMoved) || ((settlingTimeY_ms_ >= settlingTimeX_ms_) && yMoved))
		Sleep(settlingTimeY_ms_);
	// otherwise x took longer to move or y didn't move
	else if (xMoved)
		Sleep(settlingTimeX_ms_);
	// otherwise there was no movement

	// Read the new position.
	double newX = 0.0, newY = 0.0;
	err = GetPositionUm(newX, newY);
	if (err != DEVICE_OK)
		return err;

	this->OnXYStagePositionChanged(newX, newY);
	this->UpdateStatus();
	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::GetPositionUm(double& x, double& y)
{
	double xReadVal = MCL_SingleReadN(axisX_, handle_);
	if ((int)xReadVal < 0)
		return (int)xReadVal;
	x = xReadVal + lowerLimitX_;

	double yReadVal = MCL_SingleReadN(axisY_, handle_);
	if ((int)yReadVal < 0)
		return (int)yReadVal;
	y = yReadVal + lowerLimitY_;

	return DEVICE_OK;
}

void MCL_NanoDrive_XYStage::GetLastCommandedPosition(double& x, double& y)
{
	if (supportsLastCommanded_)
	{
		double x, y, z;
		MCL_GetCommandedPosition(&x, &y, &z, handle_);
		switch (axisX_)
		{
			case XAXIS: 
				commandedX_ = x;
				commandedY_ = y;
				break;
			case ZAXIS: 
				commandedX_ = z; 
				if (firstWriteY_)
				{
					commandedY_ = MCL_SingleReadN(axisY_, handle_);
				}
				break;
		}
	}
	else
	{
		if (firstWriteX_)
		{
			commandedX_ = MCL_SingleReadN(axisX_, handle_);
		}
		if (firstWriteY_)
		{
			commandedY_ = MCL_SingleReadN(axisY_, handle_);
		}
	}
	x = commandedX_;
	y = commandedY_;
}