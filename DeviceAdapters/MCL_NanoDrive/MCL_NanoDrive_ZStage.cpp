/*
File:		MCL_NanoDrive_ZStage.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "MCL_Common.h"
#include "MCL_NanoDrive_ZStage.h"

#include <vector>
#include <string>

MCL_NanoDrive_ZStage::MCL_NanoDrive_ZStage() :	
	axisUsedForTirfControl_(false),
	canSupportSeq_(false),
	commandedZ_(0),
	firstWrite_(true),
	handle_(0),
	ignoreZMovesInTirfLock_(false),
	initialized_(false),
	settlingTimeZ_ms_(100),
	seqMaxSize_(0),
	shiftSequence_(false),
	supportsLastCommanded_(false),
	supportsSeq_(false)
{
	// Basically only construct error messages in the constructor
	InitializeDefaultErrorMessages();
	
	// MCL error messages 
	SetErrorText(MCL_GENERAL_ERROR, "MCL Error: General Error");
	SetErrorText(MCL_DEV_ERROR, "MCL Error: Error transferring data to device");
	SetErrorText(MCL_DEV_NOT_ATTACHED, "MCL Error: Device not attached");
	SetErrorText(MCL_USAGE_ERROR, "MCL Error: Using a function from library device does not support");
	SetErrorText(MCL_DEV_NOT_READY, "MCL Error: Device not ready");
	SetErrorText(MCL_ARGUMENT_ERROR, "MCL Error: Argument out of range");
	SetErrorText(MCL_INVALID_AXIS, "MCL Error: Device trying to use unsupported axis");
	SetErrorText(MCL_INVALID_HANDLE, "MCL Error: Handle not valid");
	SetErrorText(MCL_BLOCKED_BY_TIRFLOCK, "Axis unable to move because it is maintaining Tirf-Lock");
}

MCL_NanoDrive_ZStage::~MCL_NanoDrive_ZStage()
{
	Shutdown();
}

void MCL_NanoDrive_ZStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_StageDeviceName);
}

int MCL_NanoDrive_ZStage::Initialize()
{
	int err = DEVICE_OK;

	HandleListLock(); 
	err = InitDeviceAdapter();
	HandleListUnlock();

	return err;
}

int MCL_NanoDrive_ZStage::Shutdown()
{
	HandleListLock();

	HandleListType device(handle_, Z_TYPE, axis_, 0);
	HandleListRemoveSingleItem(device);
	if (!HandleExistsOnLockedList(handle_))
		MCL_ReleaseHandle(handle_);
	handle_ = 0;
	initialized_ = false;	

	HandleListUnlock();

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SetPositionUm(double pos)
{
	if (axisUsedForTirfControl_ == true)
	{
		if (ignoreZMovesInTirfLock_ == true)
			return DEVICE_OK;
		else
			return MCL_BLOCKED_BY_TIRFLOCK;
	}

	// position to write to is pos (position from origin) - lowerLimit (lower bounds)
	double positionWithoutOriginAdjustment = pos - lowerLimit_;
	if (positionWithoutOriginAdjustment < 0.0)
		positionWithoutOriginAdjustment = 0.0;
	// check if we need to move.
	if((positionWithoutOriginAdjustment == commandedZ_) && !firstWrite_)
		return DEVICE_OK;

	int err = MCL_SingleWriteN(positionWithoutOriginAdjustment, axis_, handle_);
	if (err != MCL_SUCCESS)
		return err;
	firstWrite_ = false;

	// Update the commanded position
	if (!supportsLastCommanded_ || axis_ == AAXIS)
	{
		// Record the command if the device does not keep track.
		commandedZ_ = positionWithoutOriginAdjustment;
	}
	else 
	{
		// Otherwise read it from the device.
		double x, y, z;
		MCL_GetCommandedPosition(&x, &y, &z, handle_);
		switch (axis_)
		{
			case XAXIS: commandedZ_ = x; break;
			case YAXIS: commandedZ_ = y; break;
			case ZAXIS: commandedZ_ = z; break;
		}
	}

	// Wait for nanopositioner to move.
	Sleep(settlingTimeZ_ms_);

	double newPos = 0.0;
	err = GetPositionUm(newPos);
	if (err != DEVICE_OK)
		return err;

	this->OnStagePositionChanged(newPos);
	this->UpdateStatus();
	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::GetPositionUm(double& pos)
{
	double readVal = MCL_SingleReadN(axis_, handle_);
	/// 20 bit systems can read slightly below zero.  Truncate the result to check for errors.
	if ((int)readVal < 0)
		return (int)readVal;
	pos = readVal + lowerLimit_;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SetRelativePositionUm(double d)
{
	double currentlyCommandedPosition = GetLastCommandedPosition();
	double currentlyCommandedPositionFromOrigin = currentlyCommandedPosition + lowerLimit_;
	return SetPositionUm(currentlyCommandedPositionFromOrigin + d);
}

double MCL_NanoDrive_ZStage::GetStepSize()
{
	return stepSize_um_;
}

int MCL_NanoDrive_ZStage::SetPositionSteps(long steps)
{
	double pos = stepSize_um_ * steps;
	return SetPositionUm(pos);
}

int MCL_NanoDrive_ZStage::GetPositionSteps(long& steps)
{
	double pos = 0.0;
	int err = GetPositionUm(pos);
	if (err != DEVICE_OK)
		return err;

	steps = (long)(pos / stepSize_um_);

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SetOrigin()
{
	int err;

	lowerLimit_ = -commandedZ_;
	upperLimit_ = calibration_ - commandedZ_;

	// set the new limits (limits on the slidebar)
	err = SetPropertyLimits(g_Keyword_SetPosZUm, lowerLimit_, upperLimit_);
	if (err != DEVICE_OK)
		return err;

	err = UpdateStatus();
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::GetLimits(double& lower, double& upper)
{
	lower = lowerLimit_;
	upper = upperLimit_;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = supportsSeq_;
	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::GetStageSequenceMaxLength(long& nrEvents)const 
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	nrEvents = seqMaxSize_;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::StartStageSequence()
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	int err = MCL_SequenceStart(handle_);
	if(err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::StopStageSequence() 
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	int err = MCL_SequenceStop(handle_);
	if(err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::ClearStageSequence()
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	sequence_.clear();
	int err = MCL_SequenceClear(handle_);
	if(err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::AddToStageSequence(double position)
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	sequence_.push_back(position);

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SendStageSequence()
{
	if(!supportsSeq_)
		return DEVICE_UNSUPPORTED_COMMAND;

	if(sequence_.size() < 0)
		return MCL_SEQ_NOT_VALID;

	double *seqCopy = new double[sequence_.size()];
	if(shiftSequence_)
	{
		if(sequence_.size() == 1)
			seqCopy[0] = sequence_[0];
		else
		{
			memcpy(seqCopy, &sequence_[1], (sequence_.size() - 1) * sizeof(double));
			seqCopy[sequence_.size() - 1] = sequence_[0];
		}
	}
	else
	{
		memcpy(seqCopy, &sequence_[0], sequence_.size() * sizeof(double));
	}

	int err = MCL_SequenceLoad(axis_, seqCopy, (int) sequence_.size(), handle_);
	delete [] seqCopy;

	return err;
}

bool MCL_NanoDrive_ZStage::IsContinuousFocusDrive() const
{
	return false;
}

////////////////////
///ActionHandlers
////////////////////
int MCL_NanoDrive_ZStage::OnLowerLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(lowerLimit_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newLowerLimit = 0.0;
		pProp->Get(newLowerLimit);

		if (newLowerLimit == lowerLimit_)
			return DEVICE_OK;

		if (newLowerLimit < -calibration_)
			newLowerLimit = -calibration_;
		else if (newLowerLimit > 0.0)
			newLowerLimit = 0.0;

		lowerLimit_ = newLowerLimit;
		upperLimit_ = newLowerLimit + calibration_;
		err = SetPropertyLimits(g_Keyword_SetPosZUm, lowerLimit_, upperLimit_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}

int MCL_NanoDrive_ZStage::OnUpperLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(upperLimit_);
	}
	else if (eAct == MM::AfterSet)
	{
		double newUpperLimit = 0.0;
		pProp->Get(newUpperLimit);

		if (newUpperLimit == upperLimit_)
			return DEVICE_OK;

		if (newUpperLimit > calibration_)
			newUpperLimit = calibration_;
		else if (newUpperLimit < 0.0)
			newUpperLimit = 0.0;

		upperLimit_ = newUpperLimit;
		lowerLimit_ = newUpperLimit - calibration_;
		err = SetPropertyLimits(g_Keyword_SetPosZUm, lowerLimit_, upperLimit_);
		if (err != DEVICE_OK)
			return err;
		UpdateStatus();
	}

	return err;
}

int MCL_NanoDrive_ZStage::OnPositionUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double pos;
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   double zPos = 0.0;
	   err = GetPositionUm(zPos);
	   if (err != DEVICE_OK)
		   return err;
	   pProp->Set(zPos);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos);
	  if (pos > upperLimit_)
	  {
		  pos = upperLimit_;
	  }
	  else if (pos < lowerLimit_)
	  {
		  pos = lowerLimit_;
	  }
      err = SetPositionUm(pos);
   }

   return err;
}

int MCL_NanoDrive_ZStage::OnSettlingTimeZMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeZ_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
		long settleTime;
		pProp->Get(settleTime);

		if (settleTime < 0)
			settleTime = 0;

		settlingTimeZ_ms_ = settleTime;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int MCL_NanoDrive_ZStage::OnCommandChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   pProp->Set(GetLastCommandedPosition());
   }
   else if (eAct == MM::AfterSet)
   {
	   // Disallow this property when using being for tirf control.
	   if (axisUsedForTirfControl_ == true)
	   {
		   return DEVICE_OK;
	   }

		double newVal = 0.0;
		pProp->Get(newVal);

		err = MCL_SingleWriteN(newVal, axis_, handle_);
		if(err != DEVICE_OK)
		{
			return err;
		}
		pProp->Set(GetLastCommandedPosition());
   }

   return err;
}

int MCL_NanoDrive_ZStage::OnTLC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// This property is used by the TIRF plugin to make adjustments to the stage.  
	// It is set with a negative commanded position, positive commands are ignored.
	// The property is set this way so that Multi-Dimensional Acquistion is unable
	// to reset this property when the acquisition finishes. MDA snapshots properties
	// when it begins and resets them when it finishes.

	int err = DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(GetLastCommandedPosition());
	}
	else if (eAct == MM::AfterSet)
	{
		double newVal = 0.0;
		pProp->Get(newVal);
		if (newVal > 0)
		{
			pProp->Set(commandedZ_);
			return DEVICE_OK;
		}

		// adjust the set val;
		newVal *= -1.0;
		err = MCL_SingleWriteN(newVal, axis_, handle_);
		if (err != DEVICE_OK)
		{
			return err;
		}
		pProp->Set(GetLastCommandedPosition());
	}

	return err;
}

int MCL_NanoDrive_ZStage::OnSetSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if(supportsSeq_)
			pProp->Set("Yes");
		else
			pProp->Set("No");
	}
	else if (eAct == MM::AfterSet)
	{
		std::string message;
		pProp->Get(message);

		if (message.compare("Yes") == 0)
			supportsSeq_ = true;
		else
			supportsSeq_ = false;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::OnSetShiftSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if(shiftSequence_)
			pProp->Set("Yes");
		else
			pProp->Set("No");
	}
	else if (eAct == MM::AfterSet)
	{
		std::string message;
		pProp->Get(message);

		if (message.compare("Yes") == 0)
			shiftSequence_ = true;
		else
			shiftSequence_ = false;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::OnSetTirfLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if (axisUsedForTirfControl_)
			pProp->Set("Yes");
		else
			pProp->Set("No");
	}
	else if (eAct == MM::AfterSet)
	{
		std::string message;
		pProp->Get(message);

		if (message.compare("Yes") == 0)
			axisUsedForTirfControl_ = true;
		else
			axisUsedForTirfControl_ = false;
	}
	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::OnSetTirfBlockedAction(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		if (ignoreZMovesInTirfLock_)
			pProp->Set("Ignore");
		else
			pProp->Set("Error");
	}
	else if (eAct == MM::AfterSet)
	{
		std::string message;
		pProp->Get(message);

		if (message.compare("Ignore") == 0)
			ignoreZMovesInTirfLock_ = true;
		else
			ignoreZMovesInTirfLock_ = false;
	}
	return DEVICE_OK;
}


int HandleListCount();
int MCL_NanoDrive_ZStage::InitDeviceAdapter()
{
	if (initialized_)
		return DEVICE_OK;

	// Attempt to acquire a device/axis for this adapter.
	int ret = MCL_SUCCESS;
	ret = AcquireDeviceHandle(Z_TYPE, handle_, axis_);
	if (ret != MCL_SUCCESS)
	{
		return ret - (HandleListCount() * 1000);
	}

	ProductInformation pi;
	memset(&pi, 0, sizeof(ProductInformation));
	ret = MCL_GetProductInfo(&pi, handle_);
	if (ret != MCL_SUCCESS)
		return ret;

	serialNumber_ = MCL_GetSerialNumber(handle_);
	if (serialNumber_ < 0)
		return (int)serialNumber_;

	calibration_ = MCL_GetCalibration(axis_, handle_);
	if ((int)calibration_ < 0)
		return (int)calibration_;
	upperLimit_ = calibration_;
	lowerLimit_ = 0.0;

	switch (pi.DAC_resolution)
	{
	case 16:
		stepSize_um_ = calibration_ / NUM_STEPS_16;
		dacBits_ = NUM_STEPS_16;
		break;
	case 20:
		stepSize_um_ = calibration_ / NUM_STEPS_20;
		dacBits_ = NUM_STEPS_20;
		break;
	default:
		return MCL_GENERAL_ERROR;
	}

	if ((pi.FirmwareProfile & PROFILE_BIT_SUPPORTS_SEQ) != 0)
	{
		canSupportSeq_ = true;
		supportsSeq_ = true;
		ret = MCL_SequenceGetMax(&seqMaxSize_, handle_);
		if (ret != MCL_SUCCESS)
			return ret;
	}
	supportsLastCommanded_ = (pi.FirmwareProfile & PROFILE_BIT_SUPPORTS_LASTCOMMANDED) != 0;

	int err = CreateZStageProperties();
	if (err != DEVICE_OK)
		return err;

	err = UpdateStatus();
	if (err != DEVICE_OK)
		return err;

	initialized_ = true;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::CreateZStageProperties()
{
	int err = DEVICE_OK;
	int valueBufferSize = 100;
	char valueBuffer[100];

	// Allowed values
	std::vector<std::string> yesNoList;
	yesNoList.push_back(g_Value_No);
	yesNoList.push_back(g_Value_Yes);
	std::vector<std::string> tirfZAction;
	tirfZAction.push_back(g_Value_Ignore);
	tirfZAction.push_back(g_Value_Error);

	/// Read only properties

	// Name property (read-only)
	err = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
	if (err != DEVICE_OK)
		return err;
   
	// Description property (read-only)
	err = CreateProperty(MM::g_Keyword_Description, g_Value_ZStageDescription, MM::String, true);
	if (err != DEVICE_OK)
		return err;

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

	// Look at the calibration value (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f",calibration_);
	err = CreateProperty(g_Keyword_Calibration, valueBuffer, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Number of DAC values (read-only)
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", dacBits_);
	err = CreateProperty(g_Keyword_DacSteps, valueBuffer, MM::Integer, true);
	if (err != DEVICE_OK)
		return err;

	// Device axis used. (read-only)
	char* axisDescription = "X axis";
	switch (axis_)
	{
	case XAXIS:
		//axisDescription[0] = 'X';
		axisDescription = "X axis";
		break;
	case YAXIS:
		//axisDescription[0] = 'Y';
		axisDescription = "Y axis";
		break;
	case ZAXIS:
		//axisDescription[0] = 'Z';
		axisDescription = "Z axis";
		break;
	case AAXIS:
		//axisDescription[0] = 'A';
		axisDescription = "A axis";
		break;
	}
	err = CreateProperty(g_Keyword_DeviceAxisInUse, axisDescription, MM::String, true);
	if (err != DEVICE_OK)
	{
		return err;
	}
	
	/// Action properties

	// Lower limit
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", lowerLimit_);
	err = CreateProperty(
		g_Keyword_LowerLimit,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnLowerLimit)
	);
	if (err != DEVICE_OK)
		return err;

	// Upper limit
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", upperLimit_);
	err = CreateProperty(
		g_Keyword_UpperLimit,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnUpperLimit)
	);
	if (err != DEVICE_OK)
		return err;

	// Z position (um)
	double zPos = MCL_SingleReadN(axis_, handle_);
	if ((int)zPos < 0)
		return (int) zPos;
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", zPos);
	err = CreateProperty(
		g_Keyword_SetPosZUm,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnPositionUm)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetPropertyLimits(g_Keyword_SetPosZUm, lowerLimit_, upperLimit_); 
	if (err != DEVICE_OK)
		return err;

	// Settling time (ms) for the device
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%d", settlingTimeZ_ms_);
	err = CreateProperty(
		g_Keyword_SettlingTime,
		valueBuffer, MM::Integer,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSettlingTimeZMs)
	);
	if (err != DEVICE_OK)
		return err;

	// Set the origin at the current position
	err = CreateProperty(
		g_Keyword_SetOrigin,
		g_Value_No,
		MM::String,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetOrigin)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_SetOrigin, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Current Commanded Position
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", commandedZ_);
	err = CreateProperty(
		g_Keyword_CommandedZ,
		valueBuffer,
		MM::Float,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnCommandChanged)
	);
	if (err != DEVICE_OK)
		return err;

	if(canSupportSeq_)
    {
		err = CreateProperty(
			g_Keyword_SetSequence,
			g_Value_No,
			MM::String,
			false,
			new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetSequence)
		);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_SetSequence, yesNoList);
		if (err != DEVICE_OK)
			return err;

		err = CreateProperty(
			g_Keyword_ShiftSequence,
			g_Value_No, 
			MM::String, 
			false,
			new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetShiftSequence)
		);
		if(err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_ShiftSequence, yesNoList);
		if (err != DEVICE_OK)
			return err;
    }

	// Properties to support TirfLock
	err = CreateProperty(
		g_Keyword_AxisUsedForTirf, 
		g_Value_No,
		MM::String,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetTirfLock)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_AxisUsedForTirf, yesNoList);
	if (err != DEVICE_OK)
		return err;

	err = CreateProperty(
		g_Keyword_ActionOnBlockedZMove,
		g_Value_Error,
		MM::String,
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetTirfBlockedAction)
	);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_ActionOnBlockedZMove, tirfZAction);
	if (err != DEVICE_OK)
		return err;

	// Property used by TIRF-LOCK plugin.
	memset(valueBuffer, 0, valueBufferSize);
	sprintf_s(valueBuffer, valueBufferSize, "%f", commandedZ_);
	err = CreateProperty(
		g_Keyword_TLC,
		valueBuffer, 
		MM::Float, 
		false,
		new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnTLC)
	);
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

double MCL_NanoDrive_ZStage::GetLastCommandedPosition()
{
	if (supportsLastCommanded_ && axis_ != AAXIS)
	{
		double x, y, z;
		MCL_GetCommandedPosition(&x, &y, &z, handle_);
		switch (axis_)
		{
		case XAXIS: commandedZ_ = x; break;
		case YAXIS: commandedZ_ = y; break;
		case ZAXIS: commandedZ_ = z; break;
		}
	}
	else if (firstWrite_)
	{
		commandedZ_ = MCL_SingleReadN(axis_, handle_);
	}
	return commandedZ_;
}