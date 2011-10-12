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
// CVS:           $Id: Controller.cpp,v 1.7, 2010-12-09 12:04:30Z, Rachel Bach$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Controller.h"

const char* g_msg_CNTR_POS_OUT_OF_LIMITS = "Position out of limits";
const char* g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO = "Unallowable move attempted on unreferenced axis, or move attempted with servo off";
const char* g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL = "Selected axis is controlled by joystick";

//////////////////////////////////

std::map<std::string, PIController*> PIController::allControllersByLabel_;

PIController::PIController(const std::string& label):
gcs2_(true),
label_(label),
umToDefaultUnit_(0.001),
onlyIDSTAGEvalid_(false)
{
	allControllersByLabel_[label_] = this;
}

PIController::~PIController()
{
}

PIController* PIController::GetByLabel(const std::string& label)
{
	if (allControllersByLabel_.find(label) == allControllersByLabel_.end())
		return NULL;
	return allControllersByLabel_[label];
}

void PIController::DeleteByLabel(const std::string& label)
{
	if (allControllersByLabel_.find(label) == allControllersByLabel_.end())
		return;
	delete allControllersByLabel_[label];
	allControllersByLabel_.erase(label);
}

int PIController::InitStage(const std::string& axisName, const std::string& stageType)
{
   if (HasCST())
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
         return DEVICE_INVALID_PROPERTY_VALUE;
	}
	if (HasINI())
	{
		if (!INI(axisName))
			return DEVICE_ERR;
	}
	if (HasSVO())
	{
		if (!SVO(axisName, TRUE))
			return DEVICE_ERR;
	}
	return DEVICE_OK;
}

bool PIController::IsBusy()
{
	BOOL BUSY;
	if (HasIsReferencing() && IsReferencing("", &BUSY))
	{	
		if (BUSY)
			return true;
	}
	if (HasIsControllerReady() && IsControllerReady(&BUSY))
	{	
		if (!BUSY)
			return true;
	}
	if (HasIsMoving() && IsMoving("", &BUSY))
	{	
		if (BUSY)
			return true;
	}

	return false;

}

std::string PIController::MakeAxesString(const std::string &axis1Name, const std::string &axis2Name)
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

	if (homingMode == "REF")
	{
		if (HasFRF())
		{
			if (FRF(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else if (HasREF())
		{
			if (REF(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else
			return DEVICE_OK;
	}
	else if (homingMode == "MNL")
	{
		if (HasFNL())
		{
			if (FNL(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else if (HasMNL())
		{
			if (MNL(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else
			return DEVICE_OK;
	}
	else if (homingMode == "MPL")
	{
		if (HasFPL())
		{
			if (FPL(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else if (HasMPL())
		{
			if (MPL(axesNames))
				return DEVICE_OK;
			else
				return DEVICE_ERR;
		}
		else
			return DEVICE_OK;
	}
	else return DEVICE_INVALID_PROPERTY_VALUE;
}

int PIController::TranslateError( int err /*= PI_CNTR_NO_ERROR*/ )
{
	if (err == PI_CNTR_NO_ERROR)
	{
		err = GetError();
	}

	switch (err)
	{
	case(PI_CNTR_POS_OUT_OF_LIMITS):
		return ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS;
		break;
	case(PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO):
		return ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO;
		break;
	case(PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL):
		return ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL;
		break;
	default:
		return DEVICE_ERR;
	}
	return DEVICE_ERR;
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
	while (qJON(nrJoysticks + 1, state))
	{
		nrJoysticks++;
	}
	GetError();
	return nrJoysticks;
}

int PIController::OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct, int joystick)
{
   if (eAct == MM::BeforeGet)
   {
	   int state;
	   if (!qJON(joystick, state))
	   {
		   return TranslateError( GetError() );
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
		   return TranslateError( GetError() );
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


