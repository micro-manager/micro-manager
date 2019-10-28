/*
File:		MicroDriveXYStage.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "MicroDriveXYStage.h"
#include "mdutils.h"

#include <math.h>
#include <string.h>

#include <vector>
using namespace std;

MicroDriveXYStage::MicroDriveXYStage(): 
	handle_(0),
	serialNumber_(0),
	pid_(0),
	axis1_(0),
	axis2_(0),
	stepSize_mm_(0.0),
	encoderResolution_(0.0),
	maxVelocity_(0.0),
	minVelocity_(0.0),
	velocity_(0.0),
	busy_(false),
	initialized_(false),
	encoded_(false),
	lastX_(0),
	lastY_(0),
	iterativeMoves_(false),
	imRetry_(0),
	imToleranceUm_(.250),
	deviceHasTirfModuleAxis_(false),
	axis1IsTirfModule_(false),
	axis2IsTirfModule_(false),
	tirfModCalibrationMm_(0.0)
{
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

 	// Encoders present?
	CPropertyAction* pAct = new CPropertyAction(this, &MicroDriveXYStage::OnEncoded);
	CreateProperty(g_Keyword_Encoded, "Yes", MM::String, false, pAct, true);
}

MicroDriveXYStage::~MicroDriveXYStage()
{
	Shutdown();
}

bool MicroDriveXYStage::Busy()
{
	return busy_;
}

void MicroDriveXYStage::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_XYStageDeviceName);
}

int MicroDriveXYStage::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	if (MCL_CorrectDriverVersion() == false)
		return MCL_INVALID_DRIVER;

	// BEGIN LOCKING
	HandleListLock();

	int err = DEVICE_OK;

	// Look for a new device that can function as a XY stage.
	vector<int> skippedHandles;
	bool validDevice = false;
	int deviceHandle = 0;
	int deviceAxis = 0;
	unsigned short devicePid = 0;
	unsigned char deviceAxisBitmap = 0;
	do
	{
		deviceHandle = MCL_InitHandle();
		if (deviceHandle != 0)
		{
			MCL_GetProductID(&devicePid, deviceHandle);
			// Single axis systems should be controlled by a Stage class.
			if (devicePid == MICRODRIVE1)
			{
				validDevice = false;
				skippedHandles.push_back(deviceHandle);
			}
			else
			{
				// Discover all avaialble axes
				MCL_GetAxisInfo(&deviceAxisBitmap, deviceHandle);

				// Choose an available axis.
				deviceAxis = ChooseAvailableXYStageAxes(devicePid, deviceAxisBitmap, deviceHandle);
				if (deviceAxis != 0)
					validDevice = true;
			}
		}
	} while (deviceHandle != 0 && validDevice == false);

	// Release extra handles acquired while looking for a new device.
	for (vector<int>::iterator it = skippedHandles.begin(); it != skippedHandles.end(); ++it)
	{
		MCL_ReleaseHandle(*it);
	}

	bool foundDevice = deviceHandle != 0 && validDevice == true;

	// If we did not find a new device matching our criteria.  Search through the devices that 
	// we already control for an availble axis.
	if (foundDevice == false)
	{
		int numExistingHandles = MCL_NumberOfCurrentHandles();
		if (numExistingHandles > 0)
		{
			int* existingHandles = new int[numExistingHandles];
			MCL_GetAllHandles(existingHandles, numExistingHandles);
			for (int ii = 0; ii < numExistingHandles; ii++)
			{
				deviceHandle = existingHandles[ii];

				MCL_GetProductID(&devicePid, deviceHandle);
				// Skip single axis systems.
				if (devicePid == MICRODRIVE1)
					continue;

				// Discover all avaialble axes
				MCL_GetAxisInfo(&deviceAxisBitmap, deviceHandle);

				// Choose an available axis.
				deviceAxis = ChooseAvailableXYStageAxes(devicePid, deviceAxisBitmap, deviceHandle);
				if (deviceAxis != 0)
				{
					foundDevice = true;
					break;
				}
			}
			delete[] existingHandles;
		}
	}

	if (foundDevice == false)
	{
		HandleListUnlock();
		return MCL_INVALID_HANDLE;
	}

	// Discover device information.
	err = MCL_GetTirfModuleCalibration(&tirfModCalibrationMm_, deviceHandle);
	if (err == MCL_SUCCESS)
	{
		deviceHasTirfModuleAxis_ = true;
		if (IsAxisADefaultTirfModuleAxis(devicePid, deviceAxisBitmap, deviceAxis))
		{
			axis1IsTirfModule_ = true;
		}
		if (IsAxisADefaultTirfModuleAxis(devicePid, deviceAxisBitmap, deviceAxis+1))
		{
			axis2IsTirfModule_ = true;
		}
	}

	err = MCL_MDInformation(&encoderResolution_, &stepSize_mm_, &maxVelocity_, &maxVelocityTwoAxis_, &maxVelocityThreeAxis_, &minVelocity_, deviceHandle);
	if (err != MCL_SUCCESS)
	{
		HandleListUnlock();
		return err;
	}
	velocity_ = maxVelocity_;

	// Create properties
	char velErrText[50];
	sprintf(velErrText, "Velocity must be between %f and %f", minVelocity_, maxVelocity_);
	SetErrorText(INVALID_VELOCITY, velErrText);

	CreateMicroDriveXYProperties();

	err = UpdateStatus();
	if (err != DEVICE_OK)
	{
		HandleListUnlock();
		return err;
	}

	// Add device to global list
	HandleListType device(deviceHandle, XYSTAGE_TYPE, deviceAxis, deviceAxis+1);
	HandleListAddToLockedList(device);

	axis1_ = deviceAxis;
	axis2_ = deviceAxis + 1;
	handle_ = deviceHandle;
	pid_ = devicePid;

	initialized_ = true;
	
	HandleListUnlock();
	return err;
}

int MicroDriveXYStage::CreateMicroDriveXYProperties()
{
	int err;
	char iToChar[25];

	vector<string> yesNoList;
    yesNoList.push_back(g_Listword_No);
    yesNoList.push_back(g_Listword_Yes);

	/// Read only properties
	
	// Name property
	err = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// Description property
	err = CreateProperty(MM::g_Keyword_Description, "XY Stage Driver", MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// Device handle
	sprintf(iToChar, "%d", handle_);
	err = CreateProperty("Handle", iToChar, MM::Integer, true);
	if (err != DEVICE_OK)
		return err;

	// Maximum velocity
	sprintf(iToChar, "%f", maxVelocity_);
	err = CreateProperty("Maximum velocity (mm/s)", iToChar, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Minumum velocity
	sprintf(iToChar, "%f", minVelocity_);
	err = CreateProperty("Minimum velocity (mm/s)", iToChar, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	if (deviceHasTirfModuleAxis_)
	{
		sprintf(iToChar, "%f", tirfModCalibrationMm_);
		err = CreateProperty("Distance to epi", iToChar, MM::Float, true);
		if (err != DEVICE_OK)
			return err;
	}

	/// Action properties

	// Change velocity
	sprintf(iToChar, "%f", maxVelocity_);
	CPropertyAction* pAct = new CPropertyAction(this, &MicroDriveXYStage::OnVelocity);
	err = CreateProperty("Velocity", iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Change X position (mm)
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnPositionXmm);
	err = CreateProperty(g_Keyword_SetPosXmm, "0", MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Change Y position (mm)
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnPositionYmm);
	err = CreateProperty(g_Keyword_SetPosYmm, "0", MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set origin at current position (reset encoders)
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnSetOriginHere);
	err = CreateProperty(g_Keyword_SetOriginHere, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
    err = SetAllowedValues(g_Keyword_SetOriginHere, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Calibrate
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnCalibrate);
	err = CreateProperty(g_Keyword_Calibrate, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_Calibrate, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Return to origin
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnReturnToOrigin);
	err = CreateProperty(g_Keyword_ReturnToOrigin, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_ReturnToOrigin, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Iterative Moves
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnIterativeMove);
	err = CreateProperty(g_Keyword_IterativeMove, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_IterativeMove, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Iterative Retries
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnImRetry);
	err = CreateProperty(g_Keyword_ImRetry, "0", MM::Integer, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Iterative Tolerance
	sprintf(iToChar, "%f", imToleranceUm_);
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnImToleranceUm);
	err = CreateProperty(g_Keyword_ImTolerance, iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	if (deviceHasTirfModuleAxis_)
	{
		// Axis is tirfModule
		pAct = new CPropertyAction(this, &MicroDriveXYStage::OnIsTirfModuleAxis1);
		err = CreateProperty(g_Keyword_IsTirfModuleAxis1, axis1IsTirfModule_ ? g_Listword_Yes : g_Listword_No, MM::String, false, pAct);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_IsTirfModuleAxis1, yesNoList);
		if (err != DEVICE_OK)
			return err;

		pAct = new CPropertyAction(this, &MicroDriveXYStage::OnIsTirfModuleAxis2);
		err = CreateProperty(g_Keyword_IsTirfModuleAxis2, axis2IsTirfModule_ ? g_Listword_Yes : g_Listword_No, MM::String, false, pAct);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_IsTirfModuleAxis2, yesNoList);
		if (err != DEVICE_OK)
			return err;

		// Find Epi
		pAct = new CPropertyAction(this, &MicroDriveXYStage::OnFindEpi);
		err = CreateProperty(g_Keyword_FindEpi, "No", MM::String, false, pAct);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_FindEpi, yesNoList);
		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::Shutdown()
{
	HandleListLock();

	HandleListType device(handle_, XYSTAGE_TYPE, axis1_, axis2_);
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

int MicroDriveXYStage::SetPositionUm(double x, double y)
{
	return SetPositionMm(x / 1000.0, y / 1000.0);
}

int MicroDriveXYStage::GetPositionUm(double& x, double& y)
{
	int err = GetPositionMm(x, y);
	x *= 1000.0;
	y *= 1000.0;

	return err;
}

int MicroDriveXYStage::SetPositionMm(double goalX, double goalY)
{
	int err;
	int currentRetries = 0;
	bool moveFinished = false;

	//Calculate the absolute position.
	double xCurrent, yCurrent;
	err = GetPositionMm(xCurrent, yCurrent);
	if (err != MCL_SUCCESS)
		return err;

	do 
	{
		double xMove = goalX - xCurrent;
		double yMove = goalY - yCurrent;
		int startingMicroStepsX = 0;
		int endingMicroStepsX = 0;
		int startingMicroStepsY = 0;
		int endingMicroStepsY = 0;
		err = MCL_MDCurrentPositionM(axis1_, &startingMicroStepsX, handle_);
		if (err != MCL_SUCCESS)
			return err;
		err = MCL_MDCurrentPositionM(axis2_, &startingMicroStepsY, handle_);
		if (err != MCL_SUCCESS)
			return err;

		bool noXMovement = (fabs(xMove) < stepSize_mm_); 
		bool noYMovement = (fabs(yMove) < stepSize_mm_);

		if (noXMovement && noYMovement)
		{
			///No movement	
		}
		else if (noXMovement || XMoveBlocked(xMove))
		{ 
			err = MCL_MDMove(axis2_, velocity_, yMove, handle_);
			if (err != MCL_SUCCESS)
				return err;
		}
		else if (noYMovement || YMoveBlocked(yMove))
		{
			err = MCL_MDMove(axis1_, velocity_, xMove, handle_);
			if (err != MCL_SUCCESS)
				return err;
		}
		else 
		{
			double twoAxisVelocity = min(maxVelocityTwoAxis_, velocity_);
			err = MCL_MDMoveThreeAxes(axis1_, twoAxisVelocity, xMove,
									  axis2_, twoAxisVelocity, yMove,
									  0, 0, 0,
									  handle_);
			if (err != MCL_SUCCESS)
				return err;
		}

		busy_ = true;
		PauseDevice();
		busy_ = false;

		err = MCL_MDCurrentPositionM(axis1_, &endingMicroStepsX, handle_);
		if (err != MCL_SUCCESS)
			return err;
		err = MCL_MDCurrentPositionM(axis2_, &endingMicroStepsY, handle_);
		if (err != MCL_SUCCESS)
			return err;
		lastX_ += (endingMicroStepsX - startingMicroStepsX) * stepSize_mm_;
		lastY_ += (endingMicroStepsY - startingMicroStepsY) * stepSize_mm_;

		// Update current position
		err = GetPositionMm(xCurrent, yCurrent);
		if (err != MCL_SUCCESS)
			return err;
		Sleep(50);
		err = GetPositionMm(xCurrent, yCurrent);
		if (err != MCL_SUCCESS)
			return err;

		if(iterativeMoves_ && encoded_)
		{
			double absDiffUmX = abs(goalX - xCurrent) * 1000.0; 
			double absDiffUmY = abs(goalY - yCurrent) * 1000.0;
			bool xInTolerance = noXMovement || (!noXMovement && absDiffUmX < imToleranceUm_);
			bool yInTolerance = noYMovement || (!noYMovement && absDiffUmY < imToleranceUm_);
			
			if(xInTolerance && yInTolerance)
			{
				moveFinished = true;
			}
			else 
			{
				currentRetries++;
				if(currentRetries <= imRetry_)
				{
					moveFinished = false;
				}
				else
				{
					moveFinished = true;
				}
			}
		}
		else
		{
			moveFinished = true;
		}
	} while( !moveFinished );

	return DEVICE_OK;
}

int MicroDriveXYStage::SetRelativePositionUm(double dx, double dy){

	int err;
	err = SetRelativePositionMm(dx/1000, dy/1000);

	return err;
}

int MicroDriveXYStage::SetRelativePositionMm(double x, double y){

	int err;
	bool mirrorX, mirrorY;
    GetOrientation(mirrorX, mirrorY);

    if (mirrorX)
		x = -x;
    if (mirrorY)
        y = -y;

	//Calculate the absolute position.
	double xCurrent, yCurrent;
	err = GetPositionMm(xCurrent, yCurrent);
	if (err != MCL_SUCCESS)
		return err;

	double xGoal = xCurrent + x;
	double yGoal = yCurrent + y;

	return SetPositionMm(xGoal, yGoal);
}

int MicroDriveXYStage::GetPositionMm(double& x, double& y)
{
   if (encoded_) {
	   double m1, m2, m3, m4;
	   int err = MCL_MDReadEncoders(&m1, &m2, &m3, &m4, handle_);
	   if (err != MCL_SUCCESS)
		   return err;
	   switch (axis1_)
	   {
		   case M1AXIS:
			   x = m1;
			   break;
		   case M2AXIS:
			   x = m2;
			   break;
		   case M3AXIS:
			   x = m3;
			   break;
		   case M4AXIS:
			   x = m4;
			   break;
		   default:
			   x = lastX_;
			   break;
	   }
	   switch (axis2_)
	   {
		   case M1AXIS:
			   y = m1;
			   break;
		   case M2AXIS:
			   y = m2;
			   break;
		   case M3AXIS:
			   y = m3;
			   break;
		   case M4AXIS:
			   y = m4;
			   break;
		   default:
			   y = lastY_;
			   break;
	   }
   } else {
      x = lastX_;
      y = lastY_;
   }

	return DEVICE_OK;
}

double MicroDriveXYStage::GetStepSize()
{
	return stepSize_mm_;
}

int MicroDriveXYStage::SetPositionSteps(long x, long y)
{
	return SetPositionMm(x * stepSize_mm_, y * stepSize_mm_);
}

int MicroDriveXYStage::GetPositionSteps(long& x, long& y)
{
	int err;
	double getX, getY;

	err = GetPositionMm(getX, getY);

	x = (long) (getX / stepSize_mm_);
	y = (long) (getY / stepSize_mm_);

	return err;
}

int MicroDriveXYStage::Home()
{
	return MoveToForwardLimits();
}

void MicroDriveXYStage::PauseDevice()
{
	MCL_MicroDriveWait(handle_);
}

int MicroDriveXYStage::Stop()
{
	int err = MCL_MDStop(NULL, handle_);
	if (err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MicroDriveXYStage::SetOrigin()
{
	int err = MCL_SUCCESS;
	if (encoded_ && axis1_ < M5AXIS)
	{
		err = MCL_MDResetEncoder(axis1_, NULL, handle_);
		if (err != MCL_SUCCESS)
			return err;
	}
	if (encoded_ && axis2_ < M5AXIS)
	{
		err = MCL_MDResetEncoder(axis2_, NULL, handle_);
		if (err != MCL_SUCCESS)
			return err;
	}
	lastX_ = 0;
	lastY_ = 0;

	return DEVICE_OK;
}

int MicroDriveXYStage::GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MicroDriveXYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

double MicroDriveXYStage::GetStepSizeXUm()
{
	return stepSize_mm_;
}

double MicroDriveXYStage::GetStepSizeYUm()
{
	return stepSize_mm_;
}

int MicroDriveXYStage::Calibrate()
{
	int err;
	double xPosOrig;
	double yPosOrig;
	double xPosLimit;
	double yPosLimit;

	err = GetPositionMm(xPosOrig, yPosOrig);
	if (err != MCL_SUCCESS)
		return err;

	err = MoveToForwardLimits();
	if (err != DEVICE_OK)
		return err;

	err = GetPositionMm(xPosLimit, yPosLimit);
	if (err != MCL_SUCCESS)
		return err;

	err = SetOrigin();
	if (err != DEVICE_OK)
		return err;

	err = SetPositionMm((xPosOrig - xPosLimit), (yPosOrig - yPosLimit));
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

bool MicroDriveXYStage::XMoveBlocked(double possNewPos)
{
	unsigned short status = 0;

	MCL_MDStatus(&status, handle_);

	unsigned short revLimitBitMask = LimitBitMask(pid_, axis1_, REVERSE);
	unsigned short fowLimitBitMask = LimitBitMask(pid_, axis1_, FORWARD);

	bool atReverseLimit = ((status & revLimitBitMask) == 0);
	bool atForwardLimit = ((status & fowLimitBitMask) == 0);

	if(atReverseLimit && possNewPos < 0)
		return true;
	else if (atForwardLimit && possNewPos > 0)
		return true;
	return false;
}

bool MicroDriveXYStage::YMoveBlocked(double possNewPos)
{
	unsigned short status = 0;

	MCL_MDStatus(&status, handle_);

	unsigned short revLimitBitMask = LimitBitMask(pid_, axis2_, REVERSE);
	unsigned short fowLimitBitMask = LimitBitMask(pid_, axis2_, FORWARD);

	bool atReverseLimit = ((status & revLimitBitMask) == 0);
	bool atForwardLimit = ((status & fowLimitBitMask) == 0);

	if (atReverseLimit && possNewPos < 0)
		return true;
	else if (atForwardLimit && possNewPos > 0)
		return true;
	return false;
}

void MicroDriveXYStage::GetOrientation(bool& mirrorX, bool& mirrorY) 
{
	char val[MM::MaxStrLength];
	int ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorX, val);
	assert(ret == DEVICE_OK);
	mirrorX = strcmp(val, "1") == 0 ? true : false;

	ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorY, val);
	assert(ret == DEVICE_OK);
	mirrorY = strcmp(val, "1") == 0 ? true : false;
}

int MicroDriveXYStage::MoveToForwardLimits()
{
	int err;
	unsigned short status = 0;

	err = MCL_MDStatus(&status, handle_);
	if(err != MCL_SUCCESS)	
		return err;

	unsigned short axis1ForwardLimitBitMask = LimitBitMask(pid_, axis1_, FORWARD);
	unsigned short axis2ForwardLimitBitMask = LimitBitMask(pid_, axis2_, FORWARD);
	unsigned short bothLimits = axis1ForwardLimitBitMask | axis2ForwardLimitBitMask;

	while ((status & bothLimits) != 0)
	{ 
		err = SetRelativePositionUm(4000, 4000);

		if (err != DEVICE_OK)
			return err;

		err = MCL_MDStatus(&status, handle_);
		if (err != MCL_SUCCESS)	
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::ReturnToOrigin()
{
	int err;
	double xPos;
	double yPos;

	err = SetPositionMm(0.0, 0.0);
	if (err != DEVICE_OK)
		return err;

	///Finish the motion if one axis hit its limit first.
	err = GetPositionMm(xPos, yPos);
	if (err != MCL_SUCCESS)
		return err;

	bool yBlocked = (YMoveBlocked(1) || YMoveBlocked(-1));
	bool xBlocked = (XMoveBlocked(1) || XMoveBlocked(-1));
	
	bool xNotFinished = xPos != 0 && yBlocked;
	bool yNotFinished = yPos != 0 && xBlocked;

	if(xNotFinished || yNotFinished)
	{
		err = SetPositionMm(0, 0);
		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::FindEpi()
{
	if ((axis1IsTirfModule_ == false) && (axis2IsTirfModule_ == false))
		return DEVICE_OK;

	int epiAxis = 0;
	double a1Find = 0.0;
	double a2Find = 0.0;
	double a1Epi = 0.0;
	double a2Epi = 0.0;
	if (axis1IsTirfModule_)
	{
		epiAxis = axis1_;
		a1Find = -.5;
		a1Epi = tirfModCalibrationMm_;
	}
	else if(axis2IsTirfModule_)
	{
		epiAxis = axis2_;
		a2Find = -.5;
		a2Epi = tirfModCalibrationMm_;
	}

	unsigned short status;
	unsigned short mask = LimitBitMask(pid_, epiAxis, REVERSE);

	MCL_MDStatus(&status, handle_);

	// Move the stage to its reverse limit.
	while ((status & mask) == mask)
	{
		SetRelativePositionMm(a1Find, a2Find);
		MCL_MDStatus(&status, handle_);
	}

	// Set the orgin of the epi axis at the reverse limit.
	int err = MCL_SUCCESS;
	if (encoded_ && epiAxis < M5AXIS)
	{
		err = MCL_MDResetEncoder(epiAxis, NULL, handle_);
		if (err != MCL_SUCCESS)
			return err;
	}
	if (epiAxis == axis1_)
		lastX_ = 0;
	else
		lastY_ = 0;

	// Move the calibration distance to find epi.
	SetPositionMm(a1Epi, a2Epi);

	// Set the orgin of the epi axis at epi.
	if (encoded_ && epiAxis < M5AXIS)
	{
		err = MCL_MDResetEncoder(epiAxis, NULL, handle_);
		if (err != MCL_SUCCESS)
			return err;
	}
	if (epiAxis == axis1_)
		lastX_ = 0;
	else
		lastY_ = 0;

	return DEVICE_OK;
}


int MicroDriveXYStage::OnPositionXmm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	double pos;
	if (eAct == MM::BeforeGet)
	{
		double x, y;
		GetPositionMm(x, y);
		pProp->Set(x);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(pos);

		double x, y;
		err = GetPositionMm(x, y);
		if(err != MCL_SUCCESS)
			return err;
		err = SetPositionMm(pos, y);

		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnPositionYmm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	double pos;
	
	if (eAct == MM::BeforeGet)
	{
		double x, y;
		GetPositionMm(x, y);
		pProp->Set(y);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(pos);

		double x, y;
		err = GetPositionMm(x, y);		
		if(err != MCL_SUCCESS)
			return err;
		err = SetPositionMm(x, pos);

		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnSetOriginHere(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	string message;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);

		if (message.compare(g_Listword_Yes) == 0)
		{
			err = SetOrigin();
			if (err != DEVICE_OK)
			{
				return err;
			}
		}
	} 

	return DEVICE_OK;
}

int MicroDriveXYStage::OnCalibrate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	string message;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);

		if (message.compare(g_Listword_Yes) == 0)
		{
			err = Calibrate();
			if (err != DEVICE_OK)
				return err;
		}
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnReturnToOrigin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	string message;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);

		if (message.compare(g_Listword_Yes) == 0)
		{
			err = ReturnToOrigin();
			if (err != DEVICE_OK)
				return err;
		}
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnPositionXYmm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	double x, y;
	string input;
	vector<string> tokenInput;
	char* pEnd;

	if (eAct == MM::BeforeGet)
	{ 
		double x, y;
		GetPositionMm(x, y);
		char iToChar[30];
		sprintf(iToChar, "X = %f Y = %f", x, y);
		pProp->Set(iToChar);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(input);

		CDeviceUtils::Tokenize(input, tokenInput, "X=");

		x = strtod(tokenInput[0].c_str(), &pEnd);
	    y = strtod(tokenInput[1].c_str(), &pEnd);

		err = SetPositionMm(x, y);
		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct){

	double vel;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(velocity_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(vel);

		if (vel <  minVelocity_ || vel > maxVelocity_){
			return INVALID_VELOCITY;
		}
		
		velocity_ = vel;
	}
	
	return DEVICE_OK;
}

int MicroDriveXYStage::OnEncoded(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
      pProp->Set(encoded_ ? g_Listword_Yes : g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
   		string message;
		pProp->Get(message);
		encoded_ = (message.compare(g_Listword_Yes) == 0);
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnIterativeMove(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
	    pProp->Set(iterativeMoves_ ? g_Listword_Yes : g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
	   	string message;
		pProp->Get(message);
		iterativeMoves_ = (message.compare(g_Listword_Yes) == 0);
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnImRetry(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)imRetry_);
	}
	else if (eAct == MM::AfterSet)
	{
		long retries = 0;
		pProp->Get(retries);
		imRetry_ = retries;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnImToleranceUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(imToleranceUm_);
	}
	else if (eAct == MM::AfterSet)
	{
		double tol;
		pProp->Get(tol);
		imToleranceUm_ = tol;
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::IsXYStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

int MicroDriveXYStage::OnIsTirfModuleAxis1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axis1IsTirfModule_ ? g_Listword_Yes : g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		string message;
		pProp->Get(message);
		axis1IsTirfModule_ = (message.compare(g_Listword_Yes) == 0);
	}
	return DEVICE_OK;
}

int MicroDriveXYStage::OnIsTirfModuleAxis2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axis2IsTirfModule_ ? g_Listword_Yes : g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		string message;
		pProp->Get(message);
		axis2IsTirfModule_ = (message.compare(g_Listword_Yes) == 0);
	}
	return DEVICE_OK;
}

int MicroDriveXYStage::OnFindEpi(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	string message;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);

		if (message.compare(g_Listword_Yes) == 0)
		{
			err = FindEpi();
			if (err != DEVICE_OK)
				return err;
		}
	}
	return DEVICE_OK;
}

// The handle list must be locked when calling this function.
int MicroDriveXYStage::ChooseAvailableXYStageAxes(unsigned short pid, unsigned char axisBitmap, int handle)
{
	int ordersize = 2;
	int order[] = { M1AXIS, M4AXIS};

	switch (pid)
	{
		case MICRODRIVE:
		case NC_MICRODRIVE:
		case MICRODRIVE3:
		case MICRODRIVE4:
			order[1] = 0;
			break;
		// Use the standard order.
		default:
			break;
	}

	int axis = 0;
	for (int ii = 0; ii < ordersize; ii++)
	{
		if (order[ii] == 0)
			break;

		// Check that both axes are valid.
		int xBitmap = 0x1 << (order[ii] - 1);
		int yBitmap = 0x1 << order[ii];
		if (((axisBitmap & xBitmap) != xBitmap) ||
			((axisBitmap & yBitmap) != yBitmap))
			continue;

		HandleListType device(handle, XYSTAGE_TYPE, order[ii], order[ii] + 1);
		if (HandleExistsOnLockedList(device) == false)
		{
			axis = order[ii];
			break;
		}
	}
	return axis;
}