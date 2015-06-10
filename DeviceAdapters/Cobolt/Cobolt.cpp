///////////////////////////////////////////////////////////////////////////////
// FILE:          Coboltcpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls power levels of Cobolt lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2009
// LICENSE:       LGPL
// AUTHORS:       Karl Bellve, Karl.Bellve@umassmed.edu
//                with contribution from alexis Maizel, alexis.maizel@cos.uni-heidelberg.de
//

#include "Cobolt.h"

const char* g_DeviceCoboltName = "Cobolt";

const char* g_SendTerm = "\r";
const char* g_RecvTerm = "\r\n";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
    RegisterDevice(g_DeviceCoboltName, MM::ShutterDevice, "Cobolt Laser Controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
        return 0;
    
    if (strcmp(deviceName, g_DeviceCoboltName) == 0)
    {
        return new Cobolt;
    }
    
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Cobolt

Cobolt::Cobolt() :
port_("Undefined"),
initialized_(false),
busy_(false),
maxPower_(0.00),
laserOn_("On"),
laserStatus_("Undefined"),
interlock_ ("Interlock Open"),
fault_("No Fault"),
serialNumber_("0"),
version_("0")
{
    InitializeDefaultErrorMessages();
    SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
    
    // Name
    CreateProperty(MM::g_Keyword_Name, g_DeviceCoboltName, MM::String, true);
    
    // Description
    CreateProperty(MM::g_Keyword_Description, "Cobolt Laser Power Controller", MM::String, true);
    
    // Port
    CPropertyAction* pAct = new CPropertyAction (this, &Cobolt::OnPort);
    CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
    // Max Power
    pAct = new CPropertyAction (this, &Cobolt::OnPowerMax);
    CreateProperty("Max Power", "Undefined", MM::Float, false, pAct, true);
  
}

Cobolt::~Cobolt()
{
    Shutdown();
}

void Cobolt::GetName(char* Name) const
{
    CDeviceUtils::CopyLimitedString(Name, g_DeviceCoboltName);
}

int Cobolt::Initialize()
{
    CPropertyAction* pAct = new CPropertyAction (this, &Cobolt::OnPowerSetPoint);
    int nRet = CreateProperty("PowerSetpoint", "0.00", MM::Float, false, pAct);
   if (DEVICE_OK != nRet)
        return nRet;
    CreateProperty("Minimum Laser Power", "0", MM::Float, true);
   // CreateProperty("Maximum Laser Power", "60", MM::Float, true);
    pAct = new CPropertyAction (this, &Cobolt::OnPowerSetMax);
    nRet = CreateProperty("Maximum Laser Power", "0", MM::Float, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnLaserOnOff);
    nRet = CreateProperty("Laser", "Off", MM::String, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    std::vector<std::string> commands;
    commands.push_back("Off");
    commands.push_back("On");
    SetAllowedValues("Laser", commands);
    
    
    pAct = new CPropertyAction (this, &Cobolt::OnHours);
    nRet = CreateProperty("Hours", "0.00", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnCurrent);
    nRet = CreateProperty("Current", "0.00", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnLaserStatus);
    nRet = CreateProperty("LaserStatus", "Off", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnInterlock);
    nRet = CreateProperty("Interlock", "Interlock Open", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnFault);
    nRet = CreateProperty("Fault", "No Fault", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnSerialNumber);
    nRet = CreateProperty("Serial Number", "0", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnVersion);
    nRet = CreateProperty("Version", "0", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnPowerStatus);
    nRet = CreateProperty("OutputPower", "0.00", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    nRet = UpdateStatus();
    if (nRet != DEVICE_OK)
        return nRet;
    
    LaserOnOff(false);
    
    initialized_ = true;
    return DEVICE_OK;
}


int Cobolt::Shutdown()
{
    if (initialized_)
    {
        LaserOnOff(false);
        initialized_ = false;
        
    }
    return DEVICE_OK;
}

bool Cobolt::Busy()
{
    return busy_;
}

int Cobolt::LaserOnOff(int onoff)
{
    std::string answer;
    std::ostringstream command;
    
    if (onoff == 0)
    {
        command << "l0";
        laserOn_ = "Off";
    }
    else if (onoff == 1)
    {
        command << "l1";
        laserOn_ = "On";
    }
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    return DEVICE_OK;
}

int Cobolt::GetState(int &value)
{
    std::ostringstream command;
    std::string answer;
    
    command << "l? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    if (answer.at(0) == '0')
        value = 0;
    else if (answer.at(0) == '1')
        value = 1;
    
    return DEVICE_OK;
    
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Cobolt::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Cobolt::OnPowerMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    double powerSetMax;
    if (eAct == MM::BeforeGet)
    {
        powerSetMax = maxPower_;
        pProp->Set(powerSetMax);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(powerSetMax);
        maxPower_= powerSetMax;
    }
    
    return DEVICE_OK;
}

int Cobolt::OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    
    double powerSetpoint;
    if (eAct == MM::BeforeGet)
    {
        GetPowerSetpoint(powerSetpoint);
        pProp->Set(powerSetpoint*1000);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(powerSetpoint);
        SetPowerSetpoint(powerSetpoint/1000);
    }
    
    return DEVICE_OK;
}


int Cobolt::OnPowerSetMax(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    pProp->Set(maxPower_);
    return DEVICE_OK;
}

int Cobolt::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    std::string answer;
    std::ostringstream command;
    
    command << "pa? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    pProp->Set(atof(answer.c_str()));
    
    return DEVICE_OK;
}

int Cobolt::OnHours(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    std::ostringstream command;
    
    command << "hrs? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, hours_);
    if (ret != DEVICE_OK) return ret;
    
    pProp->Set(hours_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    std::ostringstream command;
    
    command << "sn? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, serialNumber_);
    if (ret != DEVICE_OK) return ret;
    
    pProp->Set(serialNumber_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnVersion(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    std::ostringstream command;
    
    command << "ver? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, version_);
    if (ret != DEVICE_OK) return ret;
     
    pProp->Set(version_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnCurrent(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    
    
    std::ostringstream command;
    
    command << "i? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, current_);
    if (ret != DEVICE_OK) return ret;
    
    pProp->Set(current_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnInterlock(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    
    std::ostringstream command;
    std::string answer;
    
    command << "ilk? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    if (answer.at(0) == '0')
        interlock_ = "Ok";
    else if (answer.at(0) == '1')
        interlock_ = "Interlock Open";
    
    pProp->Set(interlock_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnFault(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    
    std::ostringstream command;
    std::string answer;
    
    command << "f? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    if (answer.at(0) == '0')
        fault_ = "No Fault";
    else if (answer.at(0) == '1')
        fault_ = "Temperature Fault";
    else if (answer.at(0) == '3')
        fault_ = "Open Interlock";
    else if (answer.at(0) == '4')
        fault_ = "Constant Power Fault";
    
    pProp->Set(fault_.c_str());
    
    return DEVICE_OK;
}

int Cobolt::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    
    
    std::ostringstream command;
    std::string answer;
    
    command << "l? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    if (answer.at(0) == '0')
        laserStatus_ = "Off";
    else if (answer.at(0) == '1')
        laserStatus_ = "On";
    
    pProp->Set(laserStatus_.c_str());
    
    return DEVICE_OK;
}
int Cobolt::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    
    std::ostringstream command;
    std::string answer;
    laserOn_ = "Undefined";
    if (eAct == MM::BeforeGet)
    {
        command << "l? ";
        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
        if (ret != DEVICE_OK) return ret;
        ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
        if (ret != DEVICE_OK) return ret;
        //fprintf(stderr,"Cobolt::LaserOnOff() %s\n",answer.c_str());
        
        if (answer.at(0) == '0')
            laserOn_ = "Off";
        else if (answer.at(0) == '1')
            laserOn_ = "On";
        else if (answer.at(1) == '0')
            laserOn_ = "Off";
        else if (answer.at(1) == '1')
            laserOn_ = "On";
        
        pProp->Set(laserOn_.c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (answer == "Off")
        {
            LaserOnOff(false);
        }
        else
        {
            LaserOnOff(true);
        }
        
    }
    return DEVICE_OK;
}


int Cobolt::SetPowerSetpoint(double requestedPowerSetpoint)
{
    std::string answer;
    std::ostringstream command;
    std::ostringstream setpointString;
	// Check that Requested value is below Max Power otherwise  trim it
	if(requestedPowerSetpoint> maxPower_/1000){
		requestedPowerSetpoint = maxPower_/1000;
	}
	setpointString << requestedPowerSetpoint;
	command << "p " <<setpointString.str();
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    
    return DEVICE_OK;
}


int Cobolt::GetPowerSetpoint(double& value)
{
    std::ostringstream command;
    std::string answer;
    
    command << "p? ";
    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return ret;
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return ret;
    value = atof(answer.c_str());
    
    return DEVICE_OK;
}

//********************
// Shutter API
//********************

int Cobolt::SetOpen(bool open)
{
    return LaserOnOff((int) open);
}

int Cobolt::GetOpen(bool& open)
{
    int state;
    int ret = GetState(state);
    if (ret != DEVICE_OK) return ret;
    
    if (state==1)
        open = true;
    else if (state==0)
        open = false;
    else
        return  DEVICE_UNKNOWN_POSITION;
    
    return DEVICE_OK;
}

// ON for deltaT milliseconds
// other implementations of Shutter don't implement this
// is this perhaps because this blocking call is not appropriate
int Cobolt::Fire(double deltaT)
{
	SetOpen(true);
	CDeviceUtils::SleepMs((long)(deltaT+.5));
	SetOpen(false);
    return DEVICE_OK;
}
