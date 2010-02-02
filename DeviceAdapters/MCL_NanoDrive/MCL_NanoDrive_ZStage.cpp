/*
File:		MCL_NanoDrive_ZStage.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "MCL_NanoDrive_ZStage.h"

#include <vector>
#include <string>

MCL_NanoDrive_ZStage::MCL_NanoDrive_ZStage() :	
   busy_(false),
   initialized_(false),
   settlingTimeZ_ms_(100),
   curZpos_(0),
   firstWrite_(true),
   MCLhandle_(0)
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
}

MCL_NanoDrive_ZStage::~MCL_NanoDrive_ZStage()
{
	Shutdown();
}

void MCL_NanoDrive_ZStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_StageDeviceName);
}

bool MCL_NanoDrive_ZStage::Busy()
{
	return busy_;
}

int MCL_NanoDrive_ZStage::Initialize()
{
// BEGIN LOCKING -> make sure to unlock before any return call
HandleListLock(); 

	int err = DEVICE_OK;
	int possHandle = 0;
	bool valid = false;

	ProductInformation pi = {NULL, 0, 0, 0, 0, 0};
   
	if (initialized_)
	   goto ZSTAGE_INIT_EXIT;

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

		MCL_GetProductInfo(&pi, possHandle);
		
		// By default use the Z axis.
		bool validAxis = ((pi.axis_bitmap & VALIDZ) == VALIDZ);
		if (validAxis)
		{
			axis_  = ZAXIS;
		}
		// Otherwise use the X or Y axis of a single axis system.
		else if ( (pi.axis_bitmap & (VALIDX | VALIDY) ) != (VALIDX | VALIDY) )
		{
			if ( (pi.axis_bitmap & AXIS_MASK) == VALIDX )
			{
				axis_ = XAXIS;
				validAxis = true;
			}

			if ( (pi.axis_bitmap & AXIS_MASK) == VALIDY )
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

   calibration_ = MCL_GetCalibration(axis_, MCLhandle_);

   if ((int)calibration_ < 0)
   {
	   err = (int) calibration_;
	   goto ZSTAGE_INIT_EXIT;
   }

   if (pi.Product_id == 0x1230 || pi.Product_id == 0x1253 || pi.Product_id == 0x2201 || 
	   pi.Product_id == 0x2203 || pi.Product_id == 0x2253)
   { 
		// 20 bit system
		stepSize_um_ = calibration_ / NUM_STEPS_20;
   }
   else 
   { 
		stepSize_um_ = calibration_ / NUM_STEPS_16;
   }

   upperLimit_ = MCL_GetCalibration(axis_, MCLhandle_);
   if (upperLimit_ < 0)
   { 
	   err = (int) upperLimit_;
	   goto ZSTAGE_INIT_EXIT;
   }

   lowerLimit_ = 0;

   curZpos_ = MCL_SingleReadN(axis_, MCLhandle_);
   if ((int) curZpos_ < 0)
   {
	   err = (int) curZpos_;
	   goto ZSTAGE_INIT_EXIT;
   }

   serialNumber_ = MCL_GetSerialNumber(MCLhandle_);
   if (serialNumber_ < 0)
   {
	   err = (int) serialNumber_;
	   goto ZSTAGE_INIT_EXIT;
   }

   err = CreateZStageProperties();
   if (err != DEVICE_OK)
   {
	   goto ZSTAGE_INIT_EXIT;
   }
  
   err = UpdateStatus();
   if (err != DEVICE_OK)
   {
	   goto ZSTAGE_INIT_EXIT;
   }

   initialized_ = true; 


ZSTAGE_INIT_EXIT:
	HandleListUnlock();

	return err;
}


int MCL_NanoDrive_ZStage::Shutdown()
{
	
// BEGIN LOCKING
HandleListLock();

   HandleListType * device = new HandleListType(MCLhandle_, Z_TYPE);
  
   HandleListRemoveSingleItem(device);
	
   if (!HandleExistsOnLockedList(MCLhandle_))
		MCL_ReleaseHandle(MCLhandle_);
   
   delete device;

   MCLhandle_ = 0;

   initialized_ = false;

HandleListUnlock();
// END LOCKING

   return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::CreateZStageProperties()
{
	int err;
	char iToChar[25];
	double zPos;

	/// Read only properties

	// Name property (read-only)
	err = CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);
	if (err != DEVICE_OK)
		return err;
   
	// Description property (read-only)
	err = CreateProperty(MM::g_Keyword_Description, "ZStage driver", MM::String, true);
	if (err != DEVICE_OK)
		return err;

	// See what device you are connected too (read-only)
	sprintf(iToChar, "%d", MCLhandle_);
	err = CreateProperty("Device Handle", iToChar, MM::Integer, true);
	if (err != DEVICE_OK)
		return err;

	// Look at the calibration value (read-only)
	sprintf(iToChar, "%f",calibration_);
	err = CreateProperty("Calibration", iToChar, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Lowest measurement (in um) the device can be set to (read-only)
	sprintf(iToChar, "%f",lowerLimit_);
	err = CreateProperty("Lower Limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK)
		return err;
    
	// Highest measurement (in um) the device can be set to (read-only)
	sprintf(iToChar, "%f",upperLimit_);
	err = CreateProperty("Upper Limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK)
		return err;

	// Device serial number (read-only)
	sprintf(iToChar, "%d", MCL_GetSerialNumber(MCLhandle_));
	err = CreateProperty("Serial Number", iToChar, MM::String, true);
	if (err != DEVICE_OK)
		return err;

    if (axis_ == ZAXIS)
	{
		sprintf(iToChar, "Z axis");
	}
	else if (axis_ == XAXIS)
	{
		sprintf(iToChar, "X axis");
	}
	else 
	{
	   sprintf(iToChar, "Y axis");
	}
 
	err = CreateProperty("Axis being used as Z axis", iToChar, MM::String, true);
	if (err != DEVICE_OK)
	{
		return err;
	}

	/// Action properties
   
	// Change Z position (um)
	zPos = MCL_SingleReadN(axis_, MCLhandle_);
	if ((int)zPos < 0)
		return (int) zPos;

	sprintf(iToChar, "%f", zPos);
	CPropertyAction* pAct = new CPropertyAction (this, &MCL_NanoDrive_ZStage::OnPositionUm);
	err = CreateProperty(g_Keyword_SetPosZUm, iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set the limits i.e. make a slide bar for setting the position
	err = SetPropertyLimits(g_Keyword_SetPosZUm, lowerLimit_, upperLimit_); 
	if (err != DEVICE_OK)
		return err;

	// Settling time (ms) for the device
	sprintf(iToChar, "%d", settlingTimeZ_ms_);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSettlingTimeZMs);
	err = CreateProperty("Settling time Z axis (ms)", iToChar, MM::Integer, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set the origin at the current position
	pAct = new CPropertyAction(this, &MCL_NanoDrive_ZStage::OnSetOrigin);
	err = CreateProperty(g_Keyword_SetOrigin, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Allows user to choose either "Yes" or "No" for wanting to set the origin
	std::vector<std::string> yesNoList;
	yesNoList.push_back("No");
	yesNoList.push_back("Yes");
	SetAllowedValues(g_Keyword_SetOrigin, yesNoList);

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SetPositionUm(double pos)
{
	int err;
		
	if (pos == curZpos_ && !firstWrite_)
		return DEVICE_OK;

	// position to write to is pos (position from origin) - lowerLimit (lower bounds)
	err = MCL_SingleWriteN(pos - lowerLimit_, axis_, MCLhandle_);  
	if (err != MCL_SUCCESS)
		return err;

	curZpos_ = pos;

	MCL_DeviceAttached(settlingTimeZ_ms_, MCLhandle_);

	firstWrite_ = false;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::GetPositionUm(double& pos)
{
	pos = MCL_SingleReadN(axis_, MCLhandle_);
	/// 20 bit systems can read slightly below zero.  Truncate the result to check for errors.
	if ((int)pos < 0)
		return (int) pos;

	return DEVICE_OK;
}

double MCL_NanoDrive_ZStage::GetStepSize()
{
	return stepSize_um_;
}

int MCL_NanoDrive_ZStage::SetPositionSteps(long steps)
{
	int err;
	double pos;

	pos = stepSize_um_ * steps;

	err = SetPositionUm(pos);
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::GetPositionSteps(long& steps)
{
	int err;
	double pos;

	err = GetPositionUm(pos);
	if (err != DEVICE_OK)
		return err;

	steps = (long)(pos / stepSize_um_);

	return DEVICE_OK;
}

int MCL_NanoDrive_ZStage::SetOrigin()
{
	int err;

	// set the new limits (limits on the slidebar)
	lowerLimit_ = lowerLimit_ - curZpos_;
	upperLimit_ = upperLimit_ - curZpos_;

	curZpos_ = 0;

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

//////////////////////
///ActionHandlers
//////////////////////
int MCL_NanoDrive_ZStage::OnPositionUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double pos;
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   pProp->Set(curZpos_);
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
	long settleTime;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeZ_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
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
		pProp->Set("No");
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);

		if (message.compare("Yes") == 0)
			err = SetOrigin();
	}

	return err;
}
