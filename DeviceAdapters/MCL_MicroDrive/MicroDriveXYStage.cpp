/*
File:		MicroDriveXYStage.cpp
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#include "MicroDriveXYStage.h"

#include <math.h>
#include <string.h>

using namespace std;

MicroDriveXYStage::MicroDriveXYStage(): 
   busy_(false),
   initialized_(false),
   rounding_(0),
   relative_(true)
{
   InitializeDefaultErrorMessages();
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
   int err;

   if (initialized_)
      return DEVICE_OK;

   MCLhandle_ = MCL_InitHandle();
   if (MCLhandle_ == 0)
	   return MCL_INVALID_HANDLE;

   err = MCL_MicroDriveInformation(&encoderResolution_, &stepSize_mm_, &maxVelocity_, &minVelocity_, MCLhandle_);
   if (err != MCL_SUCCESS)
	   return err;

   CreateMicroDriveXYProperties();

   err = UpdateStatus();
   if (err != DEVICE_OK)
      return err;

   initialized_ = true;

   return DEVICE_OK;
}

int MicroDriveXYStage::CreateMicroDriveXYProperties()
{
	int err;
	char iToChar[25];

	vector<string> yesNoList;
    yesNoList.push_back(g_Listword_No);
    yesNoList.push_back(g_Listword_Yes);

	vector<string> AbsRelPosList;
    AbsRelPosList.push_back(g_Listword_AbsPos);
    AbsRelPosList.push_back(g_Listword_RelPos);

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
	sprintf(iToChar, "%d", MCLhandle_);
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

	/// Action properties

	// Change X position (mm)
	CPropertyAction* pAct = new CPropertyAction(this, &MicroDriveXYStage::OnPositionXmm);
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

	// Position type (absolute/relative)
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnPositionTypeAbsRel);
	err = CreateProperty(g_Keyword_PositionTypeAbsRel, "Relative", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err; 
	err = SetAllowedValues(g_Keyword_PositionTypeAbsRel, AbsRelPosList);
	if (err != DEVICE_OK)
		return err;

	// Change x&y position at same time (mm)
	pAct = new CPropertyAction(this, &MicroDriveXYStage::OnPositionXYmm);
	err = CreateProperty(g_Keyword_SetPosXYmm, "X=0.00 Y=0.00", MM::String, false, pAct);
	if (err != DEVICE_OK)
		return err;

	return DEVICE_OK;
}

int MicroDriveXYStage::Shutdown()
{
	initialized_ = false;

	MCL_ReleaseHandle(MCLhandle_);

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

int MicroDriveXYStage::SetPositionMm(double x, double y)
{
	int err;

	if (!relative_)
	{
		///Calculate the absolute position.
		double xCurrent, yCurrent;
		err = MCL_MicroDriveReadEncoders(&xCurrent, &yCurrent, MCLhandle_);
		if (err != MCL_SUCCESS)
			return err;

		x -= xCurrent;
		y -= yCurrent;
	}

	bool noXMovement = (fabs(x) < stepSize_mm_); 
	bool noYMovement = (fabs(y) < stepSize_mm_);

	if (noXMovement && noYMovement)
	{
		///No movement	
	}
	else if (noXMovement || XMoveBlocked(x))
	{ 
		err = MCL_MicroDriveMoveProfile(YAXIS, maxVelocity_, y, rounding_, MCLhandle_);
		if (err != MCL_SUCCESS)
			return err;

		PauseDeviceDistanceMm(fabs(y));
	}
	else if (noYMovement || YMoveBlocked(y))
	{
		err = MCL_MicroDriveMoveProfile(XAXIS, maxVelocity_, x, rounding_, MCLhandle_);
		if (err != MCL_SUCCESS)
			return err;

		PauseDeviceDistanceMm(fabs(x));
	}
	else 
	{
		err = MCL_MicroDriveMoveProfileXY(maxVelocity_, x, rounding_, maxVelocity_, y, rounding_, MCLhandle_);
		if (err != MCL_SUCCESS)
			return err;

		///Wait for the longest movement	
		if (fabs(x) > fabs(y))
			PauseDeviceDistanceMm(fabs(x));
		else
			PauseDeviceDistanceMm(fabs(y));
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::GetPositionMm(double& x, double& y)
{
	int err = MCL_MicroDriveReadEncoders(&x, &y, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

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

void MicroDriveXYStage::PauseDeviceDistanceMm(double distance)
{
	int milliseconds;

	milliseconds = (int)ceil((1000 * (distance/maxVelocity_)) * 1.25);
	MCL_DeviceAttached(milliseconds, MCLhandle_);
}

int MicroDriveXYStage::Stop()
{
	int err = MCL_MicroDriveStop(NULL, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MicroDriveXYStage::SetOrigin()
{
	int err = MCL_MicroDriveResetEncoders(NULL, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	return DEVICE_OK;
}

int MicroDriveXYStage::GetLimits(double& /*lower*/, double& /*upper*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MicroDriveXYStage::GetLimits(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int MicroDriveXYStage::Calibrate()
{
	int err;
	double xPosOrig;
	double yPosOrig;
	double xPosLimit;
	double yPosLimit;

	err = MCL_MicroDriveReadEncoders(&xPosOrig, &yPosOrig, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	err = MoveToForwardLimits();
	if (err != DEVICE_OK)
		return err;

	err = MCL_MicroDriveReadEncoders(&xPosLimit, &yPosLimit, MCLhandle_);
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
	unsigned char status = 0;

	MCL_MicroDriveStatus(&status, MCLhandle_);

	bool atReverseLimit = ((status & X_REVERSE_LIMIT) == 0);
	bool atForwardLimit = ((status & X_FORWARD_LIMIT) == 0);

	if(atReverseLimit && possNewPos < 0)
		return true;
	else if (atForwardLimit && possNewPos > 0)
		return true;
	return false;
}

bool MicroDriveXYStage::YMoveBlocked(double possNewPos)
{
	unsigned char status = 0;

	MCL_MicroDriveStatus(&status, MCLhandle_);

	bool atReverseLimit = ((status & Y_REVERSE_LIMIT) == 0);
	bool atForwardLimit = ((status & Y_FORWARD_LIMIT) == 0);

	if (atReverseLimit && possNewPos < 0)
		return true;
	else if (atForwardLimit && possNewPos > 0)
		return true;
	return false;
}

int MicroDriveXYStage::MoveToForwardLimits()
{
	int err;
	unsigned char status = 0;

	err = MCL_MicroDriveStatus(&status, MCLhandle_);
	if(err != MCL_SUCCESS)	
		return err;

	while ((status & BOTH_FORWARD_LIMITS) != 0)
	{ 
		bool usingRelative = relative_;
		
		relative_ = true;
		err = SetPositionMm(4.0, 4.0);
		relative_ = usingRelative;

		if (err != DEVICE_OK)
			return err;

		err = MCL_MicroDriveStatus(&status, MCLhandle_);
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

	if (relative_)
	{
		err = MCL_MicroDriveReadEncoders(&xPos, &yPos, MCLhandle_);
		if (err != MCL_SUCCESS)
			return err;

		err = SetPositionMm(-xPos, -yPos);
		if (err != DEVICE_OK)
			return err;
	}
	else 
	{
		err = SetPositionMm(0.0, 0.0);
		if (err != DEVICE_OK)
			return err;
	}

	///Finish the motion if one axis hit its limit first.
	err = MCL_MicroDriveReadEncoders(&xPos, &yPos, MCLhandle_);
	if (err != MCL_SUCCESS)
		return err;

	bool yBlocked = (YMoveBlocked(1) || YMoveBlocked(-1));
	bool xBlocked = (XMoveBlocked(1) || XMoveBlocked(-1));
	
	bool xNotFinished = xPos != 0 && yBlocked;
	bool yNotFinished = yPos != 0 && xBlocked;

	if(xNotFinished || yNotFinished)
	{
		if (relative_)
		{
			err = SetPositionMm(-xPos, -yPos);
			if (err != DEVICE_OK)
				return err;
		} 
		else 
		{
			err = SetPositionMm(0, 0);
			if (err != DEVICE_OK)
				return err;
		}
	}

	return DEVICE_OK;
}

int MicroDriveXYStage::OnPositionXmm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int err;
	double pos;
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(0.0);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(pos);

		if (relative_)
		{
			err = SetPositionMm(pos, 0); 
		}
		else 
		{
			double x, y;
			err = MCL_MicroDriveReadEncoders(&x, &y, MCLhandle_);
			if(err != MCL_SUCCESS)
				return err;
			err = SetPositionMm(pos, y);
		}

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
		pProp->Set(0.0);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(pos);

		if (relative_)
		{
			err = SetPositionMm(0, pos);
		}
		else 
		{
			double x, y;
			err = MCL_MicroDriveReadEncoders(&x, &y, MCLhandle_);		
			if(err != MCL_SUCCESS)
				return err;
			err = SetPositionMm(x, pos);
		}

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

int MicroDriveXYStage::OnPositionTypeAbsRel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		// do nothing keep cached property
	}
	else if (eAct == MM::AfterSet)
	{
		string message;
		pProp->Get(message);

		if (message.compare(g_Listword_RelPos) == 0)
		{
			relative_ = true;
		}
		else if (message.compare(g_Listword_AbsPos) == 0)
		{
			relative_ = false;
		}
		else 
		{ 
			return ERR_UNKNOWN_MODE;
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
		pProp->Set("X=0.00 Y=0.00");
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