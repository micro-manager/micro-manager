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
// CVS:           $Id: PIGCSControllerCom.cpp,v 1.13, 2014-03-31 12:51:24Z, Steffen Rau$
//


#include "PIGCSControllerCom.h"
#include "PI_GCS_2.h"
#include <algorithm>
#include <sstream>


const char* PIGCSControllerComDevice::DeviceName_ = "PI_GCSController";
const char* PIGCSControllerComDevice::UmToDefaultUnitName_ = "um in default unit";

PIGCSControllerComDevice::PIGCSControllerComDevice()
: umToDefaultUnit_(0.001)
, port_("")
, lastError_(DEVICE_OK)
, initialized_(false)
, bShowProperty_UmToDefaultUnit_(true)
, ctrl_(NULL)
{
   InitializeDefaultErrorMessages ();
   SetErrorText (ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS,              g_msg_CNTR_POS_OUT_OF_LIMITS);
   SetErrorText (ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO,   g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO);
   SetErrorText (ERR_GCS_PI_CNTR_AXIS_UNDER_JOYSTICK_CONTROL,    g_msg_CNTR_AXIS_UNDER_JOYSTICK_CONTROL);
   SetErrorText (ERR_GCS_PI_CNTR_INVALID_AXIS_IDENTIFIER,        g_msg_CNTR_INVALID_AXIS_IDENTIFIER);
   SetErrorText (ERR_GCS_PI_CNTR_ILLEGAL_AXIS,                   g_msg_CNTR_ILLEGAL_AXIS);
   SetErrorText (ERR_GCS_PI_CNTR_VEL_OUT_OF_LIMITS,              g_msg_CNTR_VEL_OUT_OF_LIMITS);
   SetErrorText (ERR_GCS_PI_CNTR_ON_LIMIT_SWITCH,                g_msg_CNTR_ON_LIMIT_SWITCH);
   SetErrorText (ERR_GCS_PI_CNTR_MOTION_ERROR,                   g_msg_CNTR_MOTION_ERROR);
   SetErrorText (ERR_GCS_PI_MOTION_ERROR,                        g_msg_MOTION_ERROR);
   SetErrorText (ERR_GCS_PI_CNTR_PARAM_OUT_OF_RANGE,             g_msg_CNTR_PARAM_OUT_OF_RANGE);
   SetErrorText (ERR_GCS_PI_NO_CONTROLLER_FOUND,                 g_msg_NO_CONTROLLER_FOUND);
}

PIGCSControllerComDevice::~PIGCSControllerComDevice()
{
	Shutdown();
    ctrl_ = NULL;
}

void PIGCSControllerComDevice::SetFactor_UmToDefaultUnit(double dUmToDefaultUnit, bool bHideProperty)
{
   umToDefaultUnit_ = dUmToDefaultUnit;
   if (bHideProperty)
   {
      bShowProperty_UmToDefaultUnit_ = false;
   }

}

void PIGCSControllerComDevice::CreateProperties()
{
   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, DeviceName_, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) GCS DLL Adapter", MM::String, true);

   CPropertyAction* pAct;

   // Port
   pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   if (bShowProperty_UmToDefaultUnit_)
   {
      // axis limit in um
      pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnUmInDefaultUnit);
      CreateProperty(PIGCSControllerComDevice::UmToDefaultUnitName_, "0.001", MM::Float, false, pAct, true);
   }
}

int PIGCSControllerComDevice::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int PIGCSControllerComDevice::OnUmInDefaultUnit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(umToDefaultUnit_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(umToDefaultUnit_);
   }

   return DEVICE_OK;
}


int PIGCSControllerComDevice::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	char szLabel[MM::MaxStrLength];
	GetLabel(szLabel);
	ctrl_ = new PIGCSControllerCom(szLabel, this, GetCoreCallback()); 

   int ret = ctrl_->Connect();
   if (ret != DEVICE_OK)
   {
      LogMessage(std::string("Cannot connect"));
      Shutdown();
	  return ret;
   }

   ctrl_->umToDefaultUnit_ = umToDefaultUnit_;
   int nrJoysticks = ctrl_->FindNrJoysticks();
	if (nrJoysticks > 0)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnJoystick1);
		CreateProperty("Joystick 1", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 1)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnJoystick2);
		CreateProperty("Joystick 2", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 2)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnJoystick3);
		CreateProperty("Joystick 3", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 3)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerComDevice::OnJoystick4);
		CreateProperty("Joystick 4", "0" , MM::Integer, false, pAct);
	}

   if (ctrl_->HasONL())
   {
      int nrOutputChannels = ctrl_->GetNrOutputChannels();
      if (nrOutputChannels > 0)
      {
         std::vector<int> outputChannels(nrOutputChannels);
         std::vector<int> values(nrOutputChannels, 1);
         for (int i = 0; i < nrOutputChannels; i++)
         {
            outputChannels[i] = i+1;
         }
         ctrl_->ONL(outputChannels, values);
      }
   }

   initialized_ = true;
   return ret;
}

int PIGCSControllerComDevice::Shutdown()
{
	if (!initialized_)
		return DEVICE_OK;
	char szLabel[MM::MaxStrLength];
	GetLabel(szLabel);
	PIController::DeleteByLabel(szLabel);
	initialized_ = false;

	return DEVICE_OK;
}

bool PIGCSControllerComDevice::Busy()
{
   return false;
}

void PIGCSControllerComDevice::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, DeviceName_);
}

bool PIGCSControllerComDevice::SendGCSCommand(unsigned char singlebyte)
{
   int ret = WriteToComPort(port_.c_str(), &singlebyte, 1);
   if (ret != DEVICE_OK)
   {
	   lastError_ = ret;
      return false;
   }
   return true;
}

bool PIGCSControllerComDevice::SendGCSCommand(const std::string command)
{
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\n");
   if (ret != DEVICE_OK)
   {
	   lastError_ = ret;
      return false;
   }
   return true;
}

bool PIGCSControllerComDevice::GCSCommandWithAnswer(const std::string command, std::vector<std::string>& answer, int nExpectedLines)
{
	if (!SendGCSCommand(command))
		return false;
	return ReadGCSAnswer(answer, nExpectedLines);
}

bool PIGCSControllerComDevice::GCSCommandWithAnswer(unsigned char singlebyte, std::vector<std::string>& answer, int nExpectedLines)
{
	if (!SendGCSCommand(singlebyte))
		return false;
	return ReadGCSAnswer(answer, nExpectedLines);
}

bool PIGCSControllerComDevice::ReadGCSAnswer(std::vector<std::string>& answer, int nExpectedLines)
{
    answer.clear();
    std::string line;
    do
    {
        // block/wait for acknowledge, or until we time out;
        int ret = GetSerialAnswer(port_.c_str(), "\n", line);
        if (ret != DEVICE_OK)
        {
            lastError_ = ret;
            return false;
        }
        answer.push_back(line);
    } while( !line.empty() && line[line.length()-1] == ' ' );
    if ( (nExpectedLines >=0) && (int(answer.size()) != nExpectedLines) )
    {
        return false;
    }
    return true;
}

int PIGCSControllerComDevice::OnJoystick1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 1);
}

int PIGCSControllerComDevice::OnJoystick2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 2);
}

int PIGCSControllerComDevice::OnJoystick3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 3);
}

int PIGCSControllerComDevice::OnJoystick4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 4);
}

//////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////
PIGCSControllerCom::PIGCSControllerCom(const std::string& label, PIGCSControllerComDevice* proxy, MM::Core* logsink)
    : PIController(label)
    , deviceProxy_(proxy)
    , hasCST_   (false)
    , hasSVO_   (true)
    , hasINI_   (false)
    , hasJON_   (true)
    , hasVEL_   (true)
    , has_qTPC_ (true)
    , hasONL_   (true)
{
	PIController::logsink_  = logsink;
	PIController::logdevice_= proxy;
}

PIGCSControllerCom::~PIGCSControllerCom()
{
    deviceProxy_ = NULL;
}

int PIGCSControllerCom::Connect()
{
	std::string answer;
	if (!qIDN(answer))
	{
		return deviceProxy_->GetLastError();
	}

	return DEVICE_OK;
}



bool PIGCSControllerCom::qIDN(std::string& sIDN)
{
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer("*IDN?", answer))
		return false;
	sIDN = answer[0];
	return true;
}

bool PIGCSControllerCom::INI(const std::string& axis)
{
	if (!hasINI_)
	{
		return false;
	}
	std::ostringstream command;
    command << "INI " << axis;
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}

	return CheckError(hasINI_);
}

bool PIGCSControllerCom::CST(const std::string& axis, const std::string& stagetype)
{
	if (!hasCST_)
	{
		return false;
	}
	std::ostringstream command;
   command << "CST " << axis<<" "<<stagetype;
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}

	return CheckError(hasCST_);
}

bool PIGCSControllerCom::SVO(const std::string& axis, BOOL svo)
{
	if (!hasSVO_)
	{
		return false;
	}
	std::ostringstream command;
	command << "SVO " << axis<<" "<< ((svo==TRUE)?"1":"0");
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}

	return CheckError(hasSVO_);
};

int PIGCSControllerCom::GetError()
{
	if (PI_CNTR_NO_ERROR != m_ControllerError)
	{
		int error = m_ControllerError;
		m_ControllerError = PI_CNTR_NO_ERROR;
		return error;
	}
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer("ERR?", answer))
		return false;
	long error;
	if (!GetValue(answer[0], error))
		return COM_ERROR;
	return error;
}

bool PIGCSControllerCom::IsControllerReady( BOOL* ready)
{
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer( (unsigned char)(7), answer, 1))
		return false;
	*ready = ((unsigned char)(answer[0][0]) == (unsigned char)(128 + '1'));
	return true;
}

bool PIGCSControllerCom::IsMoving(const std::string& /*axes*/, BOOL* moving)
{
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer( (unsigned char)(5), answer, 1))
		return false;
	long value;
	if (!GetValue(answer[0], value))
		return false;
	*moving = value != 0;
	return true;
}

bool PIGCSControllerCom::qPOS(const std::string& axis, double* position)
{
	std::ostringstream command;
	command << "POS? " << axis;
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer(command.str(), answer, 1))
		return false;
	double value;
	if (!GetValue(answer[0], value))
		return false;
	*position = value;
	return true;
}

bool PIGCSControllerCom::qPOS(const std::string& axis1, const std::string& axis2, double* position)
{
	std::ostringstream command;
	command << "POS? " << axis1 <<" " << axis2;
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer(command.str(), answer, 2))
		return false;
	double value[2];
	if (!GetValue(answer[0], value[0]))
		return false;
	if (!GetValue(answer[1], value[1]))
		return false;
	position[0] = value[0];
	position[1] = value[1];
	return true;
}

bool PIGCSControllerCom::MOV(const std::string& axis, const double* target)
{
	std::ostringstream command;
	command << "MOV " << axis<<" "<< *target;
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	//No error check due to performance issues
	return true;
}

bool PIGCSControllerCom::MOV(const std::string& axis1, const std::string& axis2, const double* target)
{
	std::ostringstream command;
	command << "MOV " << axis1<<" "<< target[0]<< " "<< axis2<<" "<< target[1];
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	//No error check due to performance issues
	return true;
}

bool PIGCSControllerCom::FRF(const std::string& axes)
{
	std::ostringstream command;
	command << "FRF " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

bool PIGCSControllerCom::REF(const std::string& axes)
{
	std::ostringstream command;
	command << "REF " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

bool PIGCSControllerCom::MNL(const std::string& axes)
{
	std::ostringstream command;
	command << "MNL " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

bool PIGCSControllerCom::FNL(const std::string& axes)
{
	std::ostringstream command;
	command << "FNL " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

bool PIGCSControllerCom::FPL(const std::string& axes)
{
	std::ostringstream command;
	command << "FPL " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

bool PIGCSControllerCom::MPL(const std::string& axes)
{
	std::ostringstream command;
	command << "MPL " << ConvertToAxesStringWithSpaces(axes);
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError();
}

std::string PIGCSControllerCom::ConvertToAxesStringWithSpaces(const std::string& axes) const
{
	if (!gcs2_)
		return axes;

	std::string axesstring;
	std::vector<std::string> lines = tokenize(axes);
	std::vector<std::string>::iterator line;
	for (line = lines.begin(); line != lines.end(); ++line)
	{
		axesstring += (*line) + " ";
	}
	return axesstring;
}

bool PIGCSControllerCom::STP()
{
	return deviceProxy_->SendGCSCommand( (unsigned char)(24));
}

bool PIGCSControllerCom::JON(int joystick, int state)
{
	if (!hasJON_)
	{
		return false;
	}
	std::ostringstream command;
	command << "JON " << joystick<<" "<< state;
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}

	return CheckError(hasJON_);
};

bool PIGCSControllerCom::qJON(int joystick, int& state)
{
	if (!hasJON_)
	{
		return false;
	}
	std::ostringstream command;
	command << "JON? " << joystick;
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer(command.str(), answer, 1))
	{
		return false;
	}
	double value;
	if (!GetValue(answer[0], value))
		return false;
	state = (value >0.9);
	return CheckError(hasJON_);
};

bool PIGCSControllerCom::VEL(const std::string& axis, const double* velocity)
{
	if (!hasVEL_)
	{
		return false;
	}
	std::ostringstream command;
	command << "VEL " << axis<<" "<< *velocity;
	if (!deviceProxy_->SendGCSCommand( command.str() ))
	{
		return false;
	}
	return CheckError(hasVEL_);
}

bool PIGCSControllerCom::qVEL(const std::string& axis, double* velocity)
{
	if (!hasVEL_)
	{
		return false;
	}
	std::ostringstream command;
	command << "VEL? " << axis;
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer(command.str(), answer, 1))
		return false;
	double value;
	if (!GetValue(answer[0], value))
		return false;
	*velocity = value;
	return CheckError(hasVEL_);
}

bool PIGCSControllerCom::qTPC(int& nrOutputChannels)
{
	if (!has_qTPC_)
	{
		return false;
	}
	std::ostringstream command;
	command << "TPC?";
	std::vector<std::string> answer;
	if (!deviceProxy_->GCSCommandWithAnswer(command.str(), answer, 1))
	{
		return false;
	}

	double value;
	if (!GetValue(answer[0], value))
		return false;
	nrOutputChannels = int(value + 0.1);
	return CheckError(has_qTPC_);
}

bool PIGCSControllerCom::ONL(const std::vector<int> outputChannels, const std::vector<int> values)
{
    size_t nrChannels = outputChannels.size();

    if (nrChannels < 1)
        return true;

    std::ostringstream command;
    command << "ONL";

    size_t i = 0;
    for (; i < nrChannels; i++)
    {
        command << " " << outputChannels[i] << " " << values[i];
    }
    if (!deviceProxy_->SendGCSCommand( command.str() ))
    {
        return false;
    }
    return CheckError(hasONL_);
}
