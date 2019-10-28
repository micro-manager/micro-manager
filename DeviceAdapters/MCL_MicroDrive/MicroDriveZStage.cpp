/*
File:		MicroDriveZStage.cpp
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#include "MicroDriveZStage.h"
#include "mdutils.h"

#include <math.h>
#include <string.h>

#include <vector>
using namespace std;

MCL_MicroDrive_ZStage::MCL_MicroDrive_ZStage() :
	handle_(0),
	serialNumber_(0),
	pid_(0),
	axis_(0),
	stepSize_mm_(0.0),
	encoderResolution_(0.0),
	maxVelocity_(0.0),
	minVelocity_(0.0),
	velocity_(0.0),
	busy_(false),
	initialized_(false),
	encoded_(false),
	lastZ_(0.0),
	iterativeMoves_(false),
	imRetry_(0),
	imToleranceUm_(.250),
	deviceHasTirfModuleAxis_(false),
	axisIsTirfModule_(false),
	tirfModCalibrationMm_(0.0)
{
	InitializeDefaultErrorMessages();

	//MCL error Messages
	SetErrorText(MCL_GENERAL_ERROR, "MCL Error: General Error");
	SetErrorText(MCL_DEV_ERROR, "MCL Error: Error transferring data to device");
	SetErrorText(MCL_DEV_NOT_ATTACHED, "MCL Error: Device not attached");
	SetErrorText(MCL_USAGE_ERROR, "MCL Error: Using a function from library device does not support");
	SetErrorText(MCL_DEV_NOT_READY, "MCL Error: Device not ready");
	SetErrorText(MCL_ARGUMENT_ERROR, "MCL Error: Argument out of range");
	SetErrorText(MCL_INVALID_AXIS, "MCL Error: Device trying to use unsupported axis");
	SetErrorText(MCL_INVALID_HANDLE, "MCL Error: Handle not valid");

 	// Encoders present?
	CPropertyAction* pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnEncoded);
	CreateProperty(g_Keyword_Encoded, "Yes", MM::String, false, pAct, true);
}


MCL_MicroDrive_ZStage::~MCL_MicroDrive_ZStage()
{
	Shutdown();
}


void MCL_MicroDrive_ZStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_StageDeviceName);
}


int MCL_MicroDrive_ZStage::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	if (MCL_CorrectDriverVersion() == false)
		return MCL_INVALID_DRIVER;

	// BEGIN LOCKING
	HandleListLock(); 

	int err = DEVICE_OK;

	// Look for a new device that can function as a stage.
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
			// Two axis systems should be controlled by a XY Stage class.
			if (devicePid == MICRODRIVE || devicePid == NC_MICRODRIVE)
			{
				validDevice = false;
				skippedHandles.push_back(deviceHandle);
			}
			else
			{
				validDevice = true;

				// Discover all avaialble axes
				MCL_GetAxisInfo(&deviceAxisBitmap, deviceHandle);

				// Choose an available axis.
				deviceAxis = ChooseAvailableStageAxis(devicePid, deviceAxisBitmap, deviceHandle);
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
				// Skip two axis systems.
				if (devicePid == MICRODRIVE || devicePid == NC_MICRODRIVE)
					continue;

				// Discover all avaialble axes
				MCL_GetAxisInfo(&deviceAxisBitmap, deviceHandle);

				// Choose an available axis.
				deviceAxis = ChooseAvailableStageAxis(devicePid, deviceAxisBitmap, deviceHandle);
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
	if(err == MCL_SUCCESS)
	{
		deviceHasTirfModuleAxis_ = true;
		if (IsAxisADefaultTirfModuleAxis(devicePid, deviceAxisBitmap, deviceAxis))
		{
			axisIsTirfModule_ = true;
		}
	}

	double ignore1, ignore2;
	err = MCL_MDInformation(&encoderResolution_, &stepSize_mm_, &maxVelocity_, &ignore1, &ignore2, &minVelocity_, deviceHandle);
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

	CreateZStageProperties();

	err = UpdateStatus();
	if (err != DEVICE_OK)
	{
		HandleListUnlock();
		return err;
	}

	// Add device to global list
	HandleListType device(deviceHandle, STAGE_TYPE, deviceAxis, 0);
	HandleListAddToLockedList(device);

	axis_ = deviceAxis;
	handle_ = deviceHandle;
	pid_ = devicePid;

	initialized_ = true;

	HandleListUnlock();
	return err;
}


int MCL_MicroDrive_ZStage::Shutdown()
{
	//BEGIN LOCKING
	HandleListLock();

	HandleListType device(handle_, STAGE_TYPE, axis_, 0);
	HandleListRemoveSingleItem(device);
	if(!HandleExistsOnLockedList(handle_))
		MCL_ReleaseHandle(handle_);

	handle_ = 0;
	initialized_ = false;

	HandleListUnlock();
	//END LOCKING

	return DEVICE_OK;
}


bool MCL_MicroDrive_ZStage::Busy()
{
	return busy_;
}


double MCL_MicroDrive_ZStage::GetStepSize()
{
	return stepSize_mm_;
}


int MCL_MicroDrive_ZStage::SetPositionUm(double z)
{
	return SetPositionMm(z / 1000.0);
}

	
int MCL_MicroDrive_ZStage::GetPositionUm(double& z)
{
	int err = GetPositionMm(z);
	z *= 1000.0;

	return err;
}


int MCL_MicroDrive_ZStage::SetRelativePositionUm(double z)
{
	return SetRelativePositionMm(z/1000.0);
}


int MCL_MicroDrive_ZStage::SetPositionSteps(long z)
{
	return SetPositionMm(z * stepSize_mm_);
}


int MCL_MicroDrive_ZStage::GetPositionSteps(long& z)
{
	int err;
	double getZ;

	err = GetPositionMm(getZ);

	z = (long) (getZ / stepSize_mm_);

	return err;
}


int MCL_MicroDrive_ZStage::SetOrigin()
{
	int err = MCL_SUCCESS;
	if (encoded_ && axis_ < M5AXIS)
	{
		err = MCL_MDResetEncoder(axis_, NULL, handle_);
		if (err != MCL_SUCCESS)
			return err;
	}
	lastZ_ = 0;
	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::FindEpi()
{
	if (axisIsTirfModule_ == false)
		return DEVICE_OK;

	unsigned short status;
	unsigned short mask = LimitBitMask(pid_, axis_, REVERSE);

	MCL_MDStatus(&status, handle_);

	// Move the stage to its reverse limit.
	while ((status & mask) == mask)
	{
		SetRelativePositionMm(-.5);
		MCL_MDStatus(&status, handle_);
	}

	// Set the orgin at the reverse limit.
	SetOrigin();

	// Move the calibration distance to find epi.
	SetPositionMm(tirfModCalibrationMm_);

	// Set the orgin at epi.
	SetOrigin();

	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::GetLimits(double& /*lower*/, double& /*upper*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}


bool MCL_MicroDrive_ZStage::IsContinuousFocusDrive() const
{
	return false;
}


int MCL_MicroDrive_ZStage::OnPositionMm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	double pos;
	if (eAct == MM::BeforeGet)
	{
		double z;
		GetPositionMm(z);
		pProp->Set(z);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(pos);
		
		double z;
		err = GetPositionMm(z);
		if(err != MCL_SUCCESS)
			return err;
		err = SetPositionMm(pos);
		
		if (err != DEVICE_OK)
			return err;
	}
		
	return DEVICE_OK;
}
	 
	
int MCL_MicroDrive_ZStage::OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct)
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

	
int MCL_MicroDrive_ZStage::OnCalibrate(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnReturnToOrigin(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
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


int MCL_MicroDrive_ZStage::OnEncoded(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnIterativeMove(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnImRetry(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnImToleranceUm(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::OnIsTirfModule(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(axisIsTirfModule_ ? g_Listword_Yes : g_Listword_No);
	}
	else if (eAct == MM::AfterSet)
	{
		string message;
		pProp->Get(message);
		axisIsTirfModule_ = (message.compare(g_Listword_Yes) == 0);
	}
	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::OnFindEpi(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int MCL_MicroDrive_ZStage::CreateZStageProperties()
{
	int err;
	char iToChar[25];

	vector<string> yesNoList;
	yesNoList.push_back(g_Listword_No);
	yesNoList.push_back(g_Listword_Yes);

	/// Read only properties
		
	// Name property
	err = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// Description property
	err = CreateProperty(MM::g_Keyword_Description, "Stage Driver", MM::String, true);
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

	// Minimum velocity
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

	///// Action properties

	// Change velocity
	sprintf(iToChar, "%f", maxVelocity_);
	CPropertyAction* pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnVelocity);
	err = CreateProperty("Velocity", iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Change position (mm)
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnPositionMm);
	err = CreateProperty(g_Keyword_SetPosZmm, "0", MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set origin at current position (reset encoders)
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnSetOrigin);
	err = CreateProperty(g_Keyword_SetOriginHere, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_SetOriginHere, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Calibrate
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnCalibrate);
	err = CreateProperty(g_Keyword_Calibrate, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_Calibrate, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Return to origin
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnReturnToOrigin);
	err = CreateProperty(g_Keyword_ReturnToOrigin, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_ReturnToOrigin, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Iterative Moves
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnIterativeMove);
	err = CreateProperty(g_Keyword_IterativeMove, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;
	err = SetAllowedValues(g_Keyword_IterativeMove, yesNoList);
	if (err != DEVICE_OK)
		return err;

	// Iterative Retries
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnImRetry);
	err = CreateProperty(g_Keyword_ImRetry, "0", MM::Integer, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Iterative Tolerance
	sprintf(iToChar, "%f", imToleranceUm_);
	pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnImToleranceUm);
	err = CreateProperty(g_Keyword_ImTolerance, iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	if (deviceHasTirfModuleAxis_)
	{
		// Axis is tirfModule
		pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnIsTirfModule);
		err = CreateProperty(g_Keyword_IsTirfModuleAxis, axisIsTirfModule_ ? g_Listword_Yes : g_Listword_No, MM::String, false, pAct);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_IsTirfModuleAxis, yesNoList);
		if (err != DEVICE_OK)
			return err;

		// Find Epi
		pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnFindEpi);
		err = CreateProperty(g_Keyword_FindEpi, "No", MM::String, false, pAct);
		if (err != DEVICE_OK)
			return err;
		err = SetAllowedValues(g_Keyword_FindEpi, yesNoList);
		if (err != DEVICE_OK)
			return err;
	}

	return DEVICE_OK;
}

int MCL_MicroDrive_ZStage::SetPositionMm(double goalZ)
{
	int err;
	int currentRetries = 0;
	bool moveFinished = false;

	//Calculate the absolute position.
	double zCurrent;
	err = GetPositionMm(zCurrent);
	if (err != MCL_SUCCESS)
		return err;

	do 
	{
		double zMove = goalZ - zCurrent;
		int startingMicroSteps = 0;
		int endingMicroSteps = 0;
		err = MCL_MDCurrentPositionM(axis_, &startingMicroSteps, handle_);
		if (err != MCL_SUCCESS)
			return err;

		err = MCL_MDMove(axis_, velocity_, zMove, handle_);
		if (err != MCL_SUCCESS)
				return err;
		busy_ = true;
		PauseDevice();
		busy_ = false;

		err = MCL_MDCurrentPositionM(axis_, &endingMicroSteps, handle_);
		if (err != MCL_SUCCESS)
			return err;

		lastZ_ += (endingMicroSteps - startingMicroSteps) * stepSize_mm_;

		// Update current position
		err = GetPositionMm(zCurrent);
		if (err != MCL_SUCCESS)
			return err;
		Sleep(50);
		err = GetPositionMm(zCurrent);
		if (err != MCL_SUCCESS)
			return err;

		if(iterativeMoves_ && encoded_)
		{
			double absDiffUmZ = abs(goalZ - zCurrent) * 1000.0; 
			bool zInTolerance = absDiffUmZ < imToleranceUm_;
			if(zInTolerance)
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


int MCL_MicroDrive_ZStage::GetPositionMm(double& z)
{
	if (encoded_ && axis_ < M5AXIS) 
	{
		double tempM1, tempM2, tempM3, tempM4;
		int err = MCL_MDReadEncoders(&tempM1, &tempM2, &tempM3, &tempM4, handle_);
		if (err != MCL_SUCCESS)
			return err;
		switch (axis_)
		{
			case M1AXIS:
				z = tempM1;
				break;
			case M2AXIS:
				z = tempM2;
				break;
			case M3AXIS:
				z = tempM3;
				break;
			case M4AXIS:
				z = tempM4;
				break;
		}
	}
	else
	{
		z = lastZ_;
	}
	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::SetRelativePositionMm(double z)
{
	int err;

	//Calculate the absolute position.
	double zCurrent;
	err = GetPositionMm(zCurrent);
	if (err != MCL_SUCCESS)
		return err;

	double zGoal = zCurrent + z;

	return SetPositionMm(zGoal);
}


int MCL_MicroDrive_ZStage::Calibrate()
{
	int err;
	double zPosOrig;
	double zPosLimit;

	err = GetPositionMm(zPosOrig);
	if (err != MCL_SUCCESS)
		return err;

	err = MoveToForwardLimit();
	if (err != DEVICE_OK)
		return err;

	err = GetPositionMm(zPosLimit);
	if (err != MCL_SUCCESS)
		return err;

	err = SetOrigin();
	if (err != DEVICE_OK)
		return err;

	err = SetPositionMm((zPosOrig - zPosLimit));
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}
	
	
int MCL_MicroDrive_ZStage::MoveToForwardLimit()
{
	int err;
	unsigned short status = 0;

	err = MCL_MDStatus(&status, handle_);
	if(err != MCL_SUCCESS)	
		return err;

	unsigned short bitMask = LimitBitMask(pid_, axis_, FORWARD);
	while ((status & bitMask) != 0)
	{ 
		err = SetRelativePositionUm(4000);
		if (err != DEVICE_OK)
			return err;

		err = MCL_MDStatus(&status, handle_);
		if (err != MCL_SUCCESS)	
			return err;
	}
	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::ReturnToOrigin()
{
	return SetPositionMm(0.0);
}


void MCL_MicroDrive_ZStage::PauseDevice()
{
	MCL_MicroDriveWait(handle_);
}


// The handle list must be locked when calling this function.
int MCL_MicroDrive_ZStage::ChooseAvailableStageAxis(unsigned short pid, unsigned char axisBitmap, int handle)
{
	int ordersize = 6;
	int order[] = { M1AXIS, M2AXIS, M3AXIS, M4AXIS, M5AXIS, M6AXIS };

	switch (pid)
	{
		// These devices should be used as XY Stage devices.
		case MICRODRIVE:
		case NC_MICRODRIVE:
			return 0;
		case MICRODRIVE3:
		{
			int neworder[] = { M3AXIS, M2AXIS, M1AXIS, 0, 0, 0 };
			copy(neworder, neworder + ordersize, order);
			break;
		}
		// For 4 and 6 axis systems leave M1/M2 for an XY Stage.
		case MICRODRIVE4:
		{
			int neworder[] = { M3AXIS, M4AXIS, 0, 0, 0, 0 };
			copy(neworder, neworder + ordersize, order);
			break;
		}
		case MICRODRIVE6:
		{
			int neworder[] = { M3AXIS, M6AXIS, M4AXIS, M5AXIS, 0, 0 };
			copy(neworder, neworder + ordersize, order);
			break;
		}
		case MICRODRIVE1:
		{
			int neworder[] = { M1AXIS, M2AXIS, M3AXIS, 0, 0, 0 };
			copy(neworder, neworder + ordersize, order);
			break;
		}
			// Use the standard order.
		default:
			break;
	}

	int axis = 0;
	for (int ii = 0; ii < ordersize; ii++)
	{
		if (order[ii] == 0)
			break;

		// Check that the axis is valid.
		int bitmap = 0x1 << (order[ii] - 1);
		if ((axisBitmap & bitmap) != bitmap)
			continue;

		HandleListType device(handle, STAGE_TYPE, order[ii], 0);
		if (HandleExistsOnLockedList(device) == false)
		{
			axis = order[ii];
			break;
		}
	}
	return axis;
}