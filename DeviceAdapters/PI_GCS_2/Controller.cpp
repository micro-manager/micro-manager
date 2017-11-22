///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS_DLL.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL Controller Driver
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
// CVS:           $Id: Controller.cpp,v 1.14, 2014-03-31 12:51:24Z, Steffen Rau$
//

#include "Controller.h"

const char* g_msg_CNTR_POS_OUT_OF_LIMITS = "Position out of limits";
const char* g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO = "Unallowable move attempted on unreferenced axis, or move attempted with servo off";
const char* g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL = "Selected axis is controlled by joystick";
const char* g_msg_CNTR_INVALID_AXIS_IDENTIFIER = "Invalid axis identifier";
const char* g_msg_CNTR_ILLEGAL_AXIS = "Illegal axis";
const char* g_msg_CNTR_VEL_OUT_OF_LIMITS= "Velocity out of limits";
const char* g_msg_CNTR_ON_LIMIT_SWITCH= "The connected stage has driven into a limit switch, some controllers need CLR to resume operation";
const char* g_msg_CNTR_MOTION_ERROR= "Motion error: position error too large, servo is switched off automatically";
const char* g_msg_MOTION_ERROR= "Motion error: position error too large, servo is switched off automatically";
const char* g_msg_CNTR_PARAM_OUT_OF_RANGE= "Parameter out of range";
const char* g_msg_NO_CONTROLLER_FOUND= "No controller found with specified name";
const char* g_msg_DLL_NOT_FOUND= "Invalid DLL Name or DLL not found";
const char* g_msg_INVALID_INTERFACE_NAME= "Invalid Interface Type";
const char* g_msg_INVALID_INTERFACE_PARAMETER= "Invalid Interface Parameter";


//////////////////////////////////

std::map<std::string, PIController*> PIController::allControllersByLabel_;

PIController::PIController(const std::string& label)
   : umToDefaultUnit_(0.001)
   , logsink_(NULL)
   , logdevice_(NULL)
   , gcs2_(true)
   , label_(label)
   , onlyIDSTAGEvalid_(false)
   , referenceMoveActive_(false)
   , m_ControllerError(PI_CNTR_NO_ERROR)
{
	allControllersByLabel_[label_] = this;
}

PIController::~PIController()
{
    logsink_   = NULL;
    logdevice_ = NULL;
}

void PIController::LogMessage(const std::string& msg) const
{
	if (logsink_==NULL ||logdevice_==NULL)
		return;
	logsink_->LogMessage(logdevice_, msg.c_str(), true);
}

PIController* PIController::GetByLabel(const std::string& label)
{
    std::map< std::string, PIController*>::iterator ctrl = allControllersByLabel_.find (label);
	if ( ctrl == allControllersByLabel_.end())
		return NULL;
	return (*ctrl).second;
}

void PIController::DeleteByLabel(const std::string& label)
{
    std::map< std::string, PIController*>::iterator ctrl = allControllersByLabel_.find (label);
	if ( ctrl == allControllersByLabel_.end())
		return;
	delete (*ctrl).second;
	allControllersByLabel_.erase(label);
}

int PIController::InitStage(const std::string& axisName, const std::string& stageType)
{
    if (HasCST() && !stageType.empty())
    {
        std::string stageType_local = stageType;
        if (onlyIDSTAGEvalid_)
        {
            if (strcmp(stageType.c_str(), "NOSTAGE") != 0)
            {
                stageType_local = "ID-STAGE";
            }
        }
        if (!CST(axisName, stageType_local))
        {
            return GetTranslatedError();
        }
    }
    if (HasINI())
    {
        if (!INI(axisName))
        {
            return GetTranslatedError();
        }

    }
    if (HasSVO())
    {
        if (!SVO(axisName, TRUE))
        {
            return GetTranslatedError();
        }
    }
    return DEVICE_OK;
}

bool PIController::IsBusy()
{
	BOOL BUSY = FALSE;
	if (referenceMoveActive_)
	{
	    LogMessage(std::string("PIController::IsBusy(): active referenceMoveActive_"));
		if (HasIsReferencing() && IsReferencing("", &BUSY))
		{	
			if (BUSY)
			{
			    LogMessage(std::string("PIController::IsBusy(): IsReferencing ->BUSY"));
				return true;
			}
		}
		BOOL BREADY = TRUE;
		if (HasIsControllerReady() && IsControllerReady(&BREADY))
		{	
			if (!BREADY)
			{
			    LogMessage(std::string("PIController::IsBusy(): IsControllerReady -> not READY"));
				return true;
			}
		}
	}
	if (HasIsMoving() && IsMoving("", &BUSY))
	{	
		if (BUSY)
		{
		    LogMessage(std::string("PIController::IsBusy(): IsMoving ->BUSY"));
			return true;
		}
	}

	referenceMoveActive_ = false;
	return false;
}

std::string PIController::MakeAxesString(const std::string &axis1Name, const std::string &axis2Name) const
{
	if (gcs2_)
		return axis1Name + " \n" + axis2Name;
	else
		return axis1Name + axis2Name;
}

int PIController::Home(const std::string &axesNames, const std::string &homingMode)
{
	if (homingMode.empty())
	     return DEVICE_OK;

	if ( (homingMode == "REF") || (homingMode == "FRF") )
	{
		if (HasFRF())
		{
			if (FRF(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else if (HasREF())
		{
			if (REF(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else
			return DEVICE_OK;
	}
	else if ( (homingMode == "MNL") || (homingMode == "FNL") )
	{
		if (HasFNL())
		{
			if (FNL(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else if (HasMNL())
		{
			if (MNL(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else
			return DEVICE_OK;
	}
	else if ( (homingMode == "MPL") || (homingMode == "FPL") )
	{
		if (HasFPL())
		{
			if (FPL(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else if (HasMPL())
		{
			if (MPL(axesNames))
			{
				referenceMoveActive_ = true;
				return DEVICE_OK;
			}
			else
				return GetTranslatedError();
		}
		else
			return DEVICE_OK;
	}
	else
	{
		return DEVICE_INVALID_PROPERTY_VALUE;
	}
}

bool PIController::CheckError(bool &hasCmdFlag)
{
	int err = GetError();
	if (err == PI_CNTR_UNKNOWN_COMMAND)
	{
		hasCmdFlag = false;
		return true;
	}
	m_ControllerError = err;
	return ( err == PI_CNTR_NO_ERROR );
}

bool PIController::CheckError(void)
{
	m_ControllerError = GetError();
	return ( m_ControllerError == PI_CNTR_NO_ERROR );
}


int PIController::GetTranslatedError()
{
	return TranslateError(GetError());
}

int PIController::TranslateError( int err /*= PI_CNTR_NO_ERROR*/ )
{
	if (err == PI_CNTR_NO_ERROR)
	{
		err = GetError();
	}

	switch (err)
	{
	case(PI_CNTR_NO_ERROR):
		return DEVICE_OK;
	case(PI_CNTR_POS_OUT_OF_LIMITS):
		return ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS;
	case(PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO):
		return ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO;
	case(PI_CNTR_INVALID_AXIS_IDENTIFIER):
		return ERR_GCS_PI_CNTR_INVALID_AXIS_IDENTIFIER;
	case(PI_CNTR_ILLEGAL_AXIS):
		return ERR_GCS_PI_CNTR_ILLEGAL_AXIS;
	case(PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL):
		return ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL;
	case(PI_CNTR_VEL_OUT_OF_LIMITS):
		return ERR_GCS_PI_CNTR_VEL_OUT_OF_LIMITS;
	case(PI_CNTR_ON_LIMIT_SWITCH):
		return ERR_GCS_PI_CNTR_ON_LIMIT_SWITCH;
	case(PI_CNTR_MOTION_ERROR):
		return ERR_GCS_PI_CNTR_MOTION_ERROR;
	case(PI_MOTION_ERROR):
		return ERR_GCS_PI_MOTION_ERROR;
	case(PI_CNTR_PARAM_OUT_OF_RANGE):
		return ERR_GCS_PI_CNTR_PARAM_OUT_OF_RANGE;

	default:
		return DEVICE_ERR;
	}
}

std::vector<std::string> PIController::tokenize(const std::string& lines)
{
	std::vector<std::string> tokens;
	if (lines.empty())
	{
		tokens.push_back("");
		return tokens;
	}
	size_t pos;
	size_t offset = 0;
	do
	{
		pos = lines.find_first_of('\n', offset);
		tokens.push_back( lines.substr(offset, pos-offset));
		offset = pos+1;
	} while (pos != std::string::npos);
	if (lines[lines.length()-1] == '\n')
		tokens.pop_back();

	return tokens;
}

int PIController::FindNrJoysticks()
{
	if (!HasJON())
		return 0;

	int nrJoysticks = 0;
	int state;
	while (qJON(nrJoysticks + 1, state) && nrJoysticks < 5)
	{
		nrJoysticks++;
	}
	GetTranslatedError();
	return nrJoysticks;
}

int PIController::OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct, int joystick)
{
    if (eAct == MM::BeforeGet)
    {
        int state;
        if (!qJON(joystick, state))
        {
            return GetTranslatedError();
        }
        pProp->Set(long(state));
    }
    else if (eAct == MM::AfterSet)
    {
        long lstate;
        pProp->Get(lstate);
        int state = int(lstate);
        if (!JON( joystick, state ))
        {
            return GetTranslatedError();
        }
    }

    return DEVICE_OK;
}

int PIController::GetNrOutputChannels()
{
	if (!Has_qTPC())
		return 0;

   int nrOutputChannels = 0;
   qTPC(nrOutputChannels);

   GetError();
	return nrOutputChannels;
}


