#include "MCL_MicroDrive_ZStage.h"

#include <math.h>
#include <string.h>

#include <iostream>
#include <fstream>

using namespace std;

MCL_MicroDrive_ZStage::MCL_MicroDrive_ZStage() :
	busy_(false),
	initialized_(false),
	settlingTimeZ_ms_(100),
	MCLhandle_(0),
	isMD1_(false),
	iterativeMoves_(false),
	imRetry_(0),
	imToleranceUm_(.250)
{
	
	// Basically only construct error messages in the constructor
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

	int err;

 	// Encoders present?
	CPropertyAction* pAct = new CPropertyAction(this, &MCL_MicroDrive_ZStage::OnEncoded);
	err = CreateProperty(g_Keyword_Encoded, "Yes", MM::String, false, pAct, true);
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
	// BEGIN LOCKING -> make sure to unlock before any return call
	HandleListLock(); 

	int err = DEVICE_OK;
	std::stringstream ss;
	bool valid = false;
	unsigned char axisBitmap;
	int possHandle = 0;

	if (initialized_)
	{
		err = DEVICE_OK;
		goto ZSTAGE_INIT_EXIT;
	}

	if(!MCL_CorrectDriverVersion())
	{
		err = MCL_INVALID_DRIVER;
		goto ZSTAGE_INIT_EXIT;
	}

	int numHandles = MCL_GrabAllHandles();
	if (numHandles == 0)
	{
		err = MCL_INVALID_HANDLE;
		goto ZSTAGE_INIT_EXIT;
	}

	int* handlesToUseOrRelease = NULL;
	handlesToUseOrRelease = (int*) malloc(sizeof(int*) * numHandles);
	MCL_GetAllHandles(handlesToUseOrRelease, numHandles);

	HandleListType* device = (HandleListType*)GlobalHeapAllocate(sizeof(HandleListType));
	device->Initialize(possHandle, Z_TYPE);

	for (int i = 0; i < numHandles; i++)
	{
		possHandle = handlesToUseOrRelease[i];
		device->setHandle(possHandle);

		MCL_GetAxisInfo(&axisBitmap, possHandle);
		
		// By default use the Z axis.
		bool validAxis = ((axisBitmap & VALIDZ) == VALIDZ);
		if (validAxis)
		{
			axis_  = ZAXIS;
		}
		// Otherwise use the X or Y axis of a single axis system.
		else if ( (axisBitmap & (VALIDX | VALIDY) ) != (VALIDX | VALIDY) )
		{
			if ( (axisBitmap & AXIS_MASK) == VALIDX )
			{
				axis_ = XAXIS;
				validAxis = true;
			}

			if ( (axisBitmap & AXIS_MASK) == VALIDY )
			{
				axis_ = YAXIS;
				validAxis = true;
			}
		}

		if ((validAxis) && (!HandleExistsOnLockedList(device)) && (possHandle > 0))
		{ 
			valid = true;

			HandleListAddToLockedList(device);
			MCLhandle_ = possHandle;

			unsigned short PID;
			MCL_GetProductID(&PID, MCLhandle_);

			if(PID == 0x2501)
				isMD1_ = true;
			else
				isMD1_ = false;

			// release handles not in use.
			for (int j = i+1; j < numHandles; j++)
			{
				possHandle = handlesToUseOrRelease[j];
				if (!HandleExistsOnLockedList(possHandle) && possHandle > 0)
				{	
					MCL_ReleaseHandle(possHandle);
				}
			}
			break; // done, no need to check further
		}
		else 
		{ 
			if (!HandleExistsOnLockedList(possHandle) && possHandle > 0)
			{
				MCL_ReleaseHandle(possHandle);
			}
		}
   }
   free (handlesToUseOrRelease);

   if (!valid)
   {
	   GlobalHeapFree(device);
	   err = MCL_INVALID_HANDLE;
	   goto ZSTAGE_INIT_EXIT;
   }

	if(isMD1_)
		MCL_MD1Information(&encoderResolution_, &stepSize_mm_, &maxVelocity_, &minVelocity_, MCLhandle_);
	else
	{
		double ignore1, ignore2;
		err = MCL_MicroDriveInformation(&encoderResolution_, &stepSize_mm_, &maxVelocity_, &ignore1, &ignore2, &minVelocity_, MCLhandle_);
	}
	ss << "Detected Encoder Resolution: " << encoderResolution_;
	this->LogMessage(ss.str());

	if (err != MCL_SUCCESS)
		goto ZSTAGE_INIT_EXIT;

	velocity_ = maxVelocity_;

	char velErrText[50];
	sprintf(velErrText, "Velocity must be between %f and %f", minVelocity_, maxVelocity_);
	SetErrorText(INVALID_VELOCITY, velErrText);

	CreateZStageProperties();

	err = UpdateStatus();
	if (err != DEVICE_OK)
		goto ZSTAGE_INIT_EXIT;

	initialized_ = true;

	ZSTAGE_INIT_EXIT:
		HandleListUnlock();
		return err;
}


int MCL_MicroDrive_ZStage::Shutdown()
{
	//BEGIN LOCKING
	HandleListLock();

	HandleListType * device = new HandleListType(MCLhandle_, Z_TYPE);

	HandleListRemoveSingleItem(device);

	if(!HandleExistsOnLockedList(MCLhandle_))
		MCL_ReleaseHandle(MCLhandle_);

	delete device;

	MCLhandle_ = 0;

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
		
	if(isMD1_)
		err = MCL_MD1ResetEncoder(NULL, MCLhandle_);
	else
		err = MCL_MicroDriveResetZEncoder(NULL, MCLhandle_);

	if (err != MCL_SUCCESS)
		return err;

	lastZ_ = 0;
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


int MCL_MicroDrive_ZStage::GetStageSequenceMaxLength(long& /*nrEvents*/) const
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::StartStageSequence() const
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::StopStageSequence() const
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::LoadStageSequence(std::vector<double> /*positions*/) const
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


bool MCL_MicroDrive_ZStage::IsContinuousFocusDrive() const
{
	return false;
}


int MCL_MicroDrive_ZStage::ClearStageSequence()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::AddToStageSequence(double /*position*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}


int MCL_MicroDrive_ZStage::SendStageSequence() const
{
	return DEVICE_UNSUPPORTED_COMMAND;
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
	err = CreateProperty(MM::g_Keyword_Description, "Z Stage Driver", MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// Device handle
	sprintf(iToChar, "%d", MCLhandle_);
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
		if(isMD1_)
			err = MCL_MD1MoveProfile(velocity_, zMove, rounding_, MCLhandle_);
		else
			err = MCL_MicroDriveMoveProfile(axis_, velocity_, zMove, rounding_, MCLhandle_);
		if (err != MCL_SUCCESS)
				return err;
		lastZ_ += zMove;

		PauseDevice();

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
	double tempX, tempY, tempZ;

	if (encoded_) 
	{
		if(isMD1_)
		{
			int err = MCL_MD1ReadEncoder(&z, MCLhandle_);
			if (err != MCL_SUCCESS)
				return err;
		}
		else
		{
			int err = MCL_MicroDriveReadEncoders(&tempX, &tempY, &tempZ, MCLhandle_);
			if (err != MCL_SUCCESS)
				return err;

			if(axis_ == ZAXIS)
				z = tempZ;
			else if(axis_ == XAXIS)
				z = tempX;
			else if(axis_ == YAXIS)
				z = tempY;
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
	unsigned char status = 0;

	err = MCL_MicroDriveStatus(&status, MCLhandle_);
	if(err != MCL_SUCCESS)	
		return err;

	int LIMIT = 0;

	if(isMD1_)
		LIMIT = Y_FORWARD_LIMIT;
	else if(axis_ == XAXIS)
		LIMIT = X_FORWARD_LIMIT;
	else if(axis_ == YAXIS)
		LIMIT = Y_FORWARD_LIMIT;
	else if(axis_ == ZAXIS)
		LIMIT = Z_FORWARD_LIMIT;


	while ((status & LIMIT) != 0)
	{ 
		err = SetRelativePositionUm(4000);

		if (err != DEVICE_OK)
			return err;

		err = MCL_MicroDriveStatus(&status, MCLhandle_);
		if (err != MCL_SUCCESS)	
			return err;
	}

	return DEVICE_OK;
}


int MCL_MicroDrive_ZStage::ReturnToOrigin()
{
	int err;
	err = SetPositionMm(0.0);
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}


void MCL_MicroDrive_ZStage::PauseDevice()
{
	int milliseconds;

	milliseconds = MCL_MicroDriveWait(MCLhandle_);

	MCL_DeviceAttached(milliseconds + 1, MCLhandle_);
}