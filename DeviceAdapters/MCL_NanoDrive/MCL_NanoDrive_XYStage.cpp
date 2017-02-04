/*
File:		MCL_NanoDrive_XYStage.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "MCL_NanoDrive_XYStage.h"

#include <vector>
#include <string>

MCL_NanoDrive_XYStage::MCL_NanoDrive_XYStage():
	busy_(false),
	initialized_(false),
	settlingTimeX_ms_(100),
	settlingTimeY_ms_(100),
	curXpos_(0),
	curYpos_(0),
	commandedX_(0),
	commandedY_(0),
	firstWriteX_(true),
	firstWriteY_(true),
	MCLhandle_(0)
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

bool MCL_NanoDrive_XYStage::Busy()
{
	return busy_;
}

void MCL_NanoDrive_XYStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

int MCL_NanoDrive_XYStage::Initialize()
{
	// BEGIN LOCKING
	HandleListLock();

   int err = DEVICE_OK;
   int possHandle = 0;
   bool valid = false; 
   
   ProductInformation pi;
   memset(&pi, 0, sizeof(ProductInformation));

   if (initialized_)
   { 
	  // If already initialized, no need to continue
	  goto INIT_ERROR;
   }

   if(!MCL_CorrectDriverVersion())
   {
	   err = MCL_INVALID_DRIVER;
	   goto INIT_ERROR;
   }

   int numHandles = MCL_GrabAllHandles();
   if (numHandles == 0)
   { 
	   // No handles, i.e. no devices currently attached
	   err = MCL_INVALID_HANDLE;
	   goto INIT_ERROR;
   }

   int* handlesToUseOrRelease = NULL;
   handlesToUseOrRelease = (int*) malloc(sizeof(int*) * numHandles);
   MCL_GetAllHandles(handlesToUseOrRelease, numHandles);

   HandleListType* device = (HandleListType*)GlobalHeapAllocate(sizeof(HandleListType));
   device->Initialize(possHandle, XY_TYPE);
   for (int i = 0; i < numHandles; i++)
   {   
	   
		possHandle = handlesToUseOrRelease[i];
		device->setHandle(possHandle);

		MCL_GetProductInfo(&pi, possHandle);
		
		// check to see which axes are valid
		bool validXaxis = ((pi.axis_bitmap & VALIDX) == VALIDX);
		bool validYaxis = ((pi.axis_bitmap & VALIDY) == VALIDY);

		if ( (validXaxis && validYaxis) && 
			 (!HandleExistsOnLockedList(device)) &&
			 (possHandle > 0) )
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
			break; // found a valid handle, so no need to check any further
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
	   goto INIT_ERROR;
   }

	xMin_ = 0;
	yMin_ = 0;

	xMax_ = MCL_GetCalibration(XAXIS, MCLhandle_);
	if (xMax_ < 0)
	{
		err = (int) xMax_;
		goto INIT_ERROR;
	}
	
	yMax_ = MCL_GetCalibration(YAXIS, MCLhandle_);
	if (yMax_ < 0)
	{
		err = (int) yMax_;
		goto INIT_ERROR;
	}

	if (pi.Product_id == 0x1230 || pi.Product_id == 0x1253 || pi.Product_id == 0x2201 || 
		pi.Product_id == 0x2203 || pi.Product_id == 0x2253)
	{
		stepSizeX_um_ = xMax_ / NUM_STEPS_20;
		stepSizeY_um_ = yMax_ / NUM_STEPS_20;
		is20Bit_ = true;
	}
	else 
	{
		stepSizeX_um_ = xMax_ / NUM_STEPS_16;
		stepSizeY_um_ = yMax_ / NUM_STEPS_16;
		is20Bit_ = false;
	}
	
	curXpos_ = MCL_SingleReadN(XAXIS, MCLhandle_);
	if ((int)curXpos_ < 0)
	{ 
		err = (int) curXpos_;
		goto INIT_ERROR;
	}

	curYpos_ = MCL_SingleReadN(YAXIS, MCLhandle_);
	if ((int)curYpos_ < 0)
	{  
		err = (int) curYpos_;
		goto INIT_ERROR;
	}

	serialNumber_ = MCL_GetSerialNumber(MCLhandle_);
	if (serialNumber_ < 0)
	{
		err = (int) serialNumber_;
		goto INIT_ERROR;
	}

	err = SetDeviceProperties();
	if (err != DEVICE_OK)
	{
		goto INIT_ERROR;
	}

	err = UpdateStatus();
	if (err != DEVICE_OK)
	{
		goto INIT_ERROR;
	}

	initialized_ = true;

INIT_ERROR:

// END LOCKING	
HandleListUnlock();

	return err;
}

int MCL_NanoDrive_XYStage::Shutdown()
{
HandleListLock();

   HandleListType * device = new HandleListType(MCLhandle_, XY_TYPE);

   HandleListRemoveSingleItem(device);

   if (!HandleExistsOnLockedList(MCLhandle_))
   {
		MCL_ReleaseHandle(MCLhandle_);
   }

   delete device;

   MCLhandle_ = 0;

   initialized_ = false;

HandleListUnlock();

   return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetDeviceProperties()
{
	int err;
	char iToChar[25];
	double origXpos, origYpos;

	/// Read-only device properties

	// Name property
	err = CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Description property
	err = CreateProperty(MM::g_Keyword_Description, "XY Stage Driver", MM::String, true);
	if (err != DEVICE_OK){
		return err;
	}

	// MCL handle property
	sprintf(iToChar, "%d", MCLhandle_);
	err = CreateProperty("Handle", iToChar, MM::String, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Lower x limit property
	sprintf(iToChar, "%f", xMin_);
	err = CreateProperty("Lower x limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Lower y limit property
	sprintf(iToChar, "%f", yMin_);
	err = CreateProperty("Lower y limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Upper x limit property
	sprintf(iToChar, "%f", xMax_);
	err = CreateProperty("Upper x limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Upper y limit property
	sprintf(iToChar, "%f", yMax_);
	err = CreateProperty("Upper y limit", iToChar, MM::Float, true);
	if (err != DEVICE_OK){
		return err;
	}

	// Serial number
	sprintf(iToChar, "%d", MCL_GetSerialNumber(MCLhandle_));
	err = CreateProperty("Serial number", iToChar, MM::String, true);
	if (err != DEVICE_OK){
		return err;
	}

	/// Action handlers

	// Change x position
	origXpos = MCL_SingleReadN(XAXIS, MCLhandle_);
	if ((int)origXpos < 0)
		return (int) origXpos;

	sprintf(iToChar, "%f", origXpos);
	CPropertyAction* pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnPositionXUm);
	err = CreateProperty(g_Keyword_SetPosXUm, iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set limits i.e. create a slide bar for the x position
	err = SetPropertyLimits(g_Keyword_SetPosXUm, xMin_, xMax_); 
	if (err != DEVICE_OK)
		return err;

	// Change y position
	origYpos = MCL_SingleReadN(YAXIS, MCLhandle_);
	if ((int)origYpos < 0)
		return (int) origYpos;

	sprintf(iToChar, "%f", origYpos);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnPositionYUm);
	err = CreateProperty(g_Keyword_SetPosYUm, iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set limits i.e. make a slidebar for the y position
	err = SetPropertyLimits(g_Keyword_SetPosYUm, yMin_, yMax_); 
	if (err != DEVICE_OK)
		return err;

	// Settling time X axis property
	sprintf(iToChar, "%d", settlingTimeX_ms_);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSettlingTimeXMs);
	err = CreateProperty("Settling Time X axis (ms)", iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Settling time Y axis property
	sprintf(iToChar, "%d", settlingTimeY_ms_);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSettlingTimeYMs);
	err = CreateProperty("Settling Time Y axis (ms)", iToChar, MM::Float, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Set origin at the current position property
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnSetOrigin);
	err = CreateProperty(g_Keyword_SetOrigin, "No", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;

	// Allows user to choose either "Yes" or "No" for wanting to set the origin
	std::vector<std::string> yesNoList;
	yesNoList.push_back("No");
	yesNoList.push_back("Yes");
	err = SetAllowedValues(g_Keyword_SetOrigin, yesNoList);
	if (err != DEVICE_OK)
		return err;


	//Current Commanded Position
	sprintf(iToChar, "%f", commandedX_);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnCommandChangedX);
	err = CreateProperty("CommandedX",iToChar, MM::Float, true, pAct);

	//Current Commanded Position
	sprintf(iToChar, "%f", commandedY_);
	pAct = new CPropertyAction(this, &MCL_NanoDrive_XYStage::OnCommandChangedY);
	err = CreateProperty("CommandedY",iToChar, MM::Float, true, pAct);

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetPositionUm(double x, double y)
{
	int err;
	bool xMoved = false;
	bool yMoved = false;

	if (x != curXpos_ || firstWriteX_)
	{
		err = SetPositionXUm(x);
		if (err != DEVICE_OK)
			return err;

		xMoved = true;
	}

	if (y != curYpos_ || firstWriteY_)
	{
		err = SetPositionYUm(y);
		if (err != DEVICE_OK)
			return err;

		yMoved = true;
	}

	// takes longer for y to move or x didn't move
	if ((!xMoved  && yMoved)|| (settlingTimeY_ms_ >= settlingTimeX_ms_ && yMoved)) 
		PauseDevice(YAXIS);

	// otherwise x took longer to move or y didn't move
	else if (xMoved)
		PauseDevice(XAXIS);

	// other case is that neither moved

	this->OnXYStagePositionChanged(x, y);
	
	this->UpdateStatus();

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetRelativePositionUm(double x, double y)
{
	double currX = 0;
	double currY = 0;

	GetPositionUm(currX, currY);
	return SetPositionUm(x + currX, y + currY);
}

int MCL_NanoDrive_XYStage::SetPositionXUm(double x)
{
	int err;

	double pos = x - xMin_;
	if(pos < 0.0)
		pos = 0.0;

	// new position is x (wanted position from current origin) - minimum possible value
	err = MCL_SingleWriteN(pos, XAXIS, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	curXpos_ = x;

	firstWriteX_ = false;

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetPositionYUm(double y)
{
	int err;

	double pos = y - yMin_;
	if(pos < 0.0)
		pos = 0.0;

	// new position is y (wanted position from origin) - minimum possible value
	err = MCL_SingleWriteN(pos, YAXIS, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	curYpos_ = y;

	firstWriteY_ = false;

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::GetPositionUm(double& x, double& y)
{
	// note 20 bit systems can read slighlty below 0, so truncate
	x = MCL_SingleReadN(XAXIS, MCLhandle_);
	if ((int)x < 0)
		return (int) x;

	// note 20 bit systems can read slighlty below 0, so truncate
	y = MCL_SingleReadN(YAXIS, MCLhandle_);
	if ((int)y < 0)
		return (int) y;

	return DEVICE_OK;
}

double MCL_NanoDrive_XYStage::GetStepSize()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MCL_NanoDrive_XYStage::SetPositionSteps(long x, long y)
{
	int err = DEVICE_OK;

	err = SetPositionUm(x * stepSizeX_um_, y * stepSizeY_um_);

	return err;
}

int MCL_NanoDrive_XYStage::GetPositionSteps(long &x, long &y)
{
	int err;
	double xPos, yPos;

	err = GetPositionUm(xPos, yPos);
	if (err != DEVICE_OK)
		return err;

	x = (long) (xPos / stepSizeX_um_);
	y = (long) (yPos / stepSizeY_um_);

	return DEVICE_OK;
}

void MCL_NanoDrive_XYStage::PauseDevice(int axis)
{
	if (axis == XAXIS)
		MCL_DeviceAttached(settlingTimeX_ms_, MCLhandle_);
	else if (axis == YAXIS)
		MCL_DeviceAttached(settlingTimeY_ms_, MCLhandle_);
}

int MCL_NanoDrive_XYStage::Home()
{
	int err = DEVICE_OK;

	err = SetPositionUm(xMin_, yMin_);

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::Stop()
{
	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::SetOrigin()
{
	int err;

	// calculate new lower & upper limits
	xMin_ = xMin_ - curXpos_;
	xMax_ = xMax_ - curXpos_;

	yMin_ = yMin_ - curYpos_;
	yMax_ = yMax_ - curYpos_;
	
	// new position is 0, 0
	curXpos_ = 0;
	curYpos_ = 0;

	// make sure to set the limits for the property browser
	err = SetPropertyLimits(g_Keyword_SetPosXUm, xMin_, xMax_);
	if (err != DEVICE_OK)
		return err;

	err = SetPropertyLimits(g_Keyword_SetPosYUm, yMin_, yMax_);
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
	xMin = xMin_;
	xMax = xMax_;
	yMin = yMin_;
	yMax = yMax_;

	return DEVICE_OK;
} 

int MCL_NanoDrive_XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	xMin = yMin = 0;
	xMax = yMax = is20Bit_ ? NUM_STEPS_20 : NUM_STEPS_16;

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
int MCL_NanoDrive_XYStage::OnPositionXUm(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	int err;
	double xPos;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(curXpos_);
	}
	else if (eAct == MM::AfterSet)
	{	
		// get user input
		pProp->Get(xPos);  

		if (xPos > xMax_)
		{
			xPos = xMax_;
		}
		else if (xPos < xMin_)
		{
			xPos = xMin_;
		}

		// Move only if the position command is new or the first write.
		if (xPos != curXpos_ || firstWriteX_)
		{ 
			err = SetPositionXUm(xPos);
			if (err != DEVICE_OK){
				return err;
			}

			PauseDevice(XAXIS);
		}
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnPositionYUm(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	int err;
	double yPos;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(curYpos_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(yPos);

		if (yPos > yMax_)
		{ 
			yPos = yMax_;
		}
		else if (yPos < yMin_)
		{ 
			yPos = yMin_;
		}

		// Move only if the position command is new or the first write.
		if (yPos != curYpos_ || firstWriteY_)
		{
			err = SetPositionYUm(yPos);
			if (err != DEVICE_OK){
				return err;
			}

			PauseDevice(YAXIS);
		}
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnSettlingTimeXMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long settleTime;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeX_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(settleTime);

		if (settleTime < 0)
			settleTime = 0;

		settlingTimeX_ms_ = settleTime;
	}

	return DEVICE_OK;
}

int MCL_NanoDrive_XYStage::OnSettlingTimeYMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long settleTime;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)settlingTimeY_ms_);
	}
	else if (eAct == MM::AfterSet)
	{
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
		pProp->Set("No");
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(message);
		if (message.compare("Yes") == 0)
		{
			err = SetOrigin();

		}
	}

	return err;
}

int MCL_NanoDrive_XYStage::OnCommandChangedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int err = DEVICE_OK;

   if (eAct == MM::BeforeGet)
   {
	   double ignoreZ;
	   MCL_GetCommandedPosition(&commandedX_, &commandedY_, &ignoreZ, MCLhandle_);
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
	   MCL_GetCommandedPosition(&commandedX_, &commandedY_, &ignoreZ, MCLhandle_);
	   pProp->Set(commandedY_);
   }

   return err;
}
