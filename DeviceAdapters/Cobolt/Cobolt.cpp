///////////////////////////////////////////////////////////////////////////////
// FILE:          Cobolt.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Cobolt and Cobolt lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2019
// LICENSE:       LGPL
// LICENSE:       https://www.gnu.org/licenses/lgpl-3.0.txt
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu, Karl.Bellve@gmail.com
//                with contribution from alexis Maizel, alexis.maizel@cos.uni-heidelberg.de

#include "Cobolt.h"

//#define _SKYRA_DEBUG_

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
///////////////////////////////////////////////////////////////////////////////
Cobolt::Cobolt() :
bInitialized_(false),
    bBusy_(false),
    bModulation_(true),
    bAnalogModulation_(false),
    bDigitalModulation_(false),
    bInternalModulation_(false),
    nCobolt_(0),
    serialNumber_(g_Default_Integer),
    version_(g_Default_Integer),
    hours_(g_Default_Integer),
    keyStatus_(g_PropertyOff),
    Current_(g_Default_Float),
    currentSetPoint_(g_Default_Float),
    currentStatus_(g_Default_Float),
    currentModulationMinimum_(g_Default_Float),
    Power_(g_Default_Float),
    powerSetPoint_(g_Default_Float),
    powerStatus_(g_Default_Float),
    controlMode_(g_Default_ControlMode),
#ifndef _SKYRA_DEBUG_
    ID_(g_Default_Empty),
#else
    ID_("3"),
#endif
    laserStatus_(g_Default_String),
    Type_(g_Default_String),
    autostartStatus_(g_Default_String),
    interlock_ (g_Default_String),
    fault_(g_Default_String),
    identity_(g_Default_String),
    port_(g_Default_String)
{
    InitializeDefaultErrorMessages();
    SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

    // Name
    CreateProperty(MM::g_Keyword_Name, g_DeviceCoboltName, MM::String, true); 

    // Description
    CreateProperty(MM::g_Keyword_Description, g_DeviceCoboltDescription, MM::String, true);

    // Port
    CPropertyAction* pAct = new CPropertyAction (this, &Cobolt::OnPort);
    CreateProperty(MM::g_Keyword_Port, g_Default_String, MM::String, false, pAct, true);

    // Company Name
    CreateProperty("Vendor", g_DeviceVendorName, MM::String, true);

    // Deprecated
    // Max Power
    pAct = new CPropertyAction (this, &Cobolt::OnPowerMax);
    CreateProperty("Max Power", g_Deprecated, MM::Float, false, pAct, true);
}
Cobolt::~Cobolt()
{
    Shutdown();
}
void Cobolt::GetName(char* Name) const
{
    CDeviceUtils::CopyLimitedString(Name, g_DeviceCoboltName);
}
bool Cobolt::SupportsDeviceDetection(void)
{
    return true;
}
MM::DeviceDetectionStatus Cobolt::DetectDevice(void)
{
    // Code modified from Nico's Arduino Device Adapter

    if (bInitialized_)
        return MM::CanCommunicate;

    // all conditions must be satisfied...
    MM::DeviceDetectionStatus result = MM::Misconfigured;
    char answerTO[MM::MaxStrLength];

    try
    {
        std::string portLowerCase = GetPort();
        for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
        {
            *its = (char)tolower(*its);
        }
        if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
        {
            result = MM::CanNotCommunicate;
            // record the default answer time out
            GetCoreCallback()->GetDeviceProperty(GetPort().c_str(), "AnswerTimeout", answerTO);
            CDeviceUtils::SleepMs(2000);
            GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_Handshaking, g_PropertyOff);
            GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_StopBits, "1");
            GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "AnswerTimeout", "500.0");
            GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "DelayBetweenCharsMs", g_Default_Integer);
            MM::Device* pS = GetCoreCallback()->GetDevice(this, GetPort().c_str());

            // This next Function Block is adapted from Jon Daniels ASISStage Device Adapter
            std::vector<std::string> possibleBauds;
            possibleBauds.push_back("115200");
            possibleBauds.push_back("19200");
            for( std::vector< std::string>::iterator bit = possibleBauds.begin(); bit!= possibleBauds.end(); ++bit )
            {
                GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), MM::g_Keyword_BaudRate, (*bit).c_str());
                pS->Initialize();
                PurgeComPort(GetPort().c_str());
                // First check if the Cobolt/Cobolt can communicate at 115,200 baud.
                if (ConfirmIdentity() == DEVICE_OK) {
                    result = MM::CanCommunicate;
                }
                pS->Shutdown();
                if (MM::CanCommunicate == result) break;
                else CDeviceUtils::SleepMs(10);
            }

            // always restore the AnswerTimeout to the default
            GetCoreCallback()->SetDeviceProperty(GetPort().c_str(), "AnswerTimeout", answerTO);
        }
    }
    catch(...)
    {
        //LogMessage("Exception in DetectDevice!",false);
    }

    return result;
}
int Cobolt::ConfirmIdentity()
{

    std::string answer;

    answer = SerialCommand("@cob0");
    if (answer == "OK") {
        answer = SerialCommand("l0");
        if (answer == "OK") {
            return DEVICE_OK;
        }
        else DEVICE_SERIAL_INVALID_RESPONSE;
    }
    else return DEVICE_SERIAL_INVALID_RESPONSE;

    return DEVICE_ERR;
}
int Cobolt::DetectInstalledDevices()
{
    // Code modified from Nico's Arduino Device Adapter
    if (MM::CanCommunicate == DetectDevice())
    {
        std::vector<std::string> peripherals;
        peripherals.clear();
        peripherals.push_back(g_DeviceCoboltName);
        for (size_t i=0; i < peripherals.size(); i++)
        {
            MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
            if (pDev)
            {
                //AddInstalledDevice(pDev);
            }
        }
    }

    return DEVICE_OK;
}
int Cobolt::Initialize()
{   
    CPropertyAction* pAct;

    //AllLasersOn(true);
    pAct = new CPropertyAction (this, &Cobolt::OnAllLasers);
    int nRet = CreateProperty(g_PropertyCoboltAllLaser, laserStatus_.c_str(), MM::String, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    std::vector<std::string> commands;
    commands.clear();
    commands.push_back(g_PropertyOff);
    commands.push_back(g_PropertyOn);
    SetAllowedValues(g_PropertyCoboltAllLaser, commands);

    pAct = new CPropertyAction (this, &Cobolt::OnLaserHelp1);
    nRet = CreateProperty("Laser Help #1", g_PropertyCoboltAutostartHelp1, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnLaserHelp2);
    nRet = CreateProperty("Laser Help #2", g_PropertyCoboltAutostartHelp2, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnHours);
    nRet = CreateProperty("Hours", "0.00", MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnKeyStatus);
    nRet = CreateProperty("Key On/Off", g_PropertyOff, MM::String, true, pAct);
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

    pAct = new CPropertyAction (this, &Cobolt::OnOperatingStatus);
    nRet = CreateProperty(g_PropertyCoboltOperatingStatus, g_Default_String, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // The following should never change, so read once and remember
    serialNumber_ = SerialCommand("sn?");
    nRet = CreateProperty("Serial Number", serialNumber_.c_str(), MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    model_ = SerialCommand("glm?");
    nRet = CreateProperty("Model", model_.c_str(), MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    version_ = SerialCommand("ver?");
    nRet = CreateProperty("Firmware Version", version_.c_str(), MM::String, true);
    if (DEVICE_OK != nRet)
        return nRet;

    // Direct Serial Commands
    pAct = new CPropertyAction (this, &Cobolt::OnSerialCommand);
    nRet = CreateProperty(g_PropertyCoboltSerialCommand, g_Default_Empty, MM::String, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    // Direct Serial Command Response
    pAct = new CPropertyAction (this, &Cobolt::OnSerialCommandResponse);
    nRet = CreateProperty(g_PropertyCoboltSerialCommandResponse, g_Default_Empty, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // even though this should be only set by OEM, users requested it. - kdb
    // Also, individual lasers in a Cobolt doesn't appear to support Autostart, despite documentation saying otherwise :(
    // But make autostart available for those who can't modulate their lasers.
    AutoStartStatus();
    pAct = new CPropertyAction (this, &Cobolt::OnAutoStart);
    nRet = CreateProperty(g_PropertyCoboltAutostart, autostartStatus_.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
        return nRet;

    SetAllowedValues(g_PropertyCoboltAutostart, commands);
    pAct = new CPropertyAction (this, &Cobolt::OnAutoStartStatus);
    nRet = CreateProperty(g_PropertyCoboltAutostartStatus, autostartStatus_.c_str(), MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    commands.clear();
    commands.push_back(g_PropertyEnabled);
    commands.push_back(g_PropertyDisabled);
    SetAllowedValues(g_PropertyCoboltAutostart, commands);

    // get default type if not Cobolt
    Type_ = SerialCommand("glm?");

    if (SerialCommand (ID_ + "em").compare(g_Msg_UNSUPPORTED_COMMAND) == 0) bModulation_ = false;
    bModulation_ = false;

    // POWER
    // current setpoint power, not current output power
    pAct = new CPropertyAction (this, &Cobolt::OnPower);
    nRet = CreateProperty(g_PropertyCoboltPower, g_Default_Float, MM::Float, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // output, not setpoint power
    pAct = new CPropertyAction (this, &Cobolt::OnPowerStatus);
    nRet = CreateProperty(g_PropertyCoboltPowerStatus, powerStatus_.c_str(), MM::Float, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // setpoint power, not current output
    pAct = new CPropertyAction (this, &Cobolt::OnPowerSetPoint);
    nRet = CreateProperty(g_PropertyCoboltPowerSetpoint, powerSetPoint_.c_str(),  MM::Float, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // Power Maximum
    powerMaximum_ = SerialCommand (ID_ + "gmlp?");
    pAct = new CPropertyAction (this, &Cobolt::OnPowerMaximum);
    nRet = CreateProperty(g_PropertyCoboltPowerMaximum, powerMaximum_.c_str(), MM::Float, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    // Set Property Limits for Power
    double powerMaximum = std::atof(powerMaximum_.c_str());
    SetPropertyLimits(g_PropertyCoboltPower,0,powerMaximum);

    CreateProperty("Power Units", g_PropertyCoboltPowerHelp, MM::String, true);
    CreateProperty("Power Help", g_PropertyCoboltPowerHelpOn, MM::String, true);

    /// CURRENT
    pAct = new CPropertyAction (this, &Cobolt::OnCurrent);
    nRet = CreateProperty(g_PropertyCoboltCurrent, g_Default_Float, MM::Float, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnCurrentStatus);
    nRet = CreateProperty(g_PropertyCoboltCurrentStatus, g_Default_Float, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;
    
    pAct = new CPropertyAction (this, &Cobolt::OnCurrentModulationMinimum);
    nRet = CreateProperty(g_PropertyCoboltCurrentModulationMinimum, g_Default_Float, MM::Float, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnCurrentModulationMaximum);
    nRet = CreateProperty(g_PropertyCoboltCurrentModulationMaximum, g_Default_Float, MM::Float, false, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnCurrentMaximum);
    nRet = CreateProperty(g_PropertyCoboltCurrentMaximum, g_Default_Float, MM::Float, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    pAct = new CPropertyAction (this, &Cobolt::OnCurrentSetPoint);
    nRet = CreateProperty(g_PropertyCoboltCurrentSetpoint, g_Default_Float, MM::String, true, pAct);
    if (DEVICE_OK != nRet)
        return nRet;

    // Set Property Limits for Current
    currentMaximum_ = SerialCommand (ID_ + "gmlc?");
    double currentMaximum = std::atof(currentMaximum_.c_str());
    SetPropertyLimits(g_PropertyCoboltCurrent,0,currentMaximum);
    SetPropertyLimits(g_PropertyCoboltCurrentModulationMinimum,0,currentMaximum);
    SetPropertyLimits(g_PropertyCoboltCurrentModulationMaximum,0,currentMaximum);

    CreateProperty("Current Units", g_PropertyCoboltCurrentHelp, MM::String, true);
    CreateProperty("Current Modulation Minimum Help", g_PropertyCoboltCurrentModulationHelpMinimum, MM::String, true);
    CreateProperty("Current Modulation Maximum Help", g_PropertyCoboltCurrentModulationHelpMaximum, MM::String, true);
    CreateProperty("Current Help", g_PropertyCoboltCurrentHelpOn, MM::String, true);
        

    // check if Laser supports supports analog impedance
    AnalogImpedanceStatus();	
    if (bImpedance_ == true) {
        pAct = new CPropertyAction (this, &Cobolt::OnAnalogImpedance);
        nRet = CreateProperty(g_PropertyCoboltAnalogImpedance, impedanceStatus_.c_str(), MM::String, false, pAct);
        if (DEVICE_OK != nRet)
            return nRet;

        commands.clear();
        commands.push_back(g_PropertyEnabled);
        commands.push_back(g_PropertyDisabled);
        SetAllowedValues(g_PropertyCoboltAnalogImpedance, commands);

        pAct = new CPropertyAction (this, &Cobolt::OnAnalogImpedanceStatus);
        nRet = CreateProperty(g_PropertyCoboltAnalogImpedanceStatus, impedanceStatus_.c_str(), MM::String, true, pAct);
        if (DEVICE_OK != nRet)
            return nRet;
    }


    // Constant Power (Default) or Constant Current or Modulation Mode
    pAct = new CPropertyAction (this, &Cobolt::OnControlMode);
    nRet = CreateProperty(g_PropertyCoboltControlMode, controlMode_.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
        return nRet;

    commands.clear();
    commands.push_back(g_PropertyCoboltControlModePower);
    commands.push_back(g_PropertyCoboltControlModeCurrent);
    if (bModulation_ == true)  commands.push_back(g_PropertyCoboltControlModeModulation);
    SetAllowedValues(g_PropertyCoboltControlMode, commands);  

    // Modulation
    if (bModulation_ == true) {
        pAct = new CPropertyAction (this, &Cobolt::OnAnalogModulation);
        CreateProperty(g_PropertyCoboltAnalogModulation, g_PropertyDisabled, MM::String, false, pAct);

        pAct = new CPropertyAction (this, &Cobolt::OnDigitalModulation);
        CreateProperty(g_PropertyCoboltDigitalModulation, g_PropertyDisabled, MM::String, false, pAct);

        commands.clear();
        commands.push_back(g_PropertyEnabled);
        commands.push_back(g_PropertyDisabled);

        SetAllowedValues(g_PropertyCoboltDigitalModulation, commands);
        SetAllowedValues(g_PropertyCoboltAnalogModulation, commands);
    } 

   
    nRet = UpdateStatus();
    if (nRet != DEVICE_OK)
        return nRet;

    bInitialized_ = true;

    //LogMessage("Cobolt::Initialize: Success", true);

    return DEVICE_OK;
}
int Cobolt::Shutdown()
{
    if (bInitialized_)
    {
        //AllLasersOnOff(false);
        bInitialized_ = false;

    }
    return DEVICE_OK;
}
bool Cobolt::Busy()
{
    return bBusy_;
}
int Cobolt::AllLasersOn(int onoff)
{

    if (onoff == 0)
    {
        SerialCommand(ID_ + "l0");
        laserStatus_ = g_PropertyOff;
    }
    else if (onoff == 1)
    {
        SerialCommand(ID_ + "l1");
        laserStatus_ = g_PropertyOn;
    }

    return DEVICE_OK;
}
int Cobolt::GetState(int &value )
{

    value = 0;
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
        if (bInitialized_)
        {
            // revert
            pProp->Set(port_.c_str());
            return ERR_PORT_CHANGE_FORBIDDEN;
        }

        pProp->Get(port_);
    }

    return DEVICE_OK;
}

int Cobolt::OnSerialCommand(MM::PropertyBase* pProp , MM::ActionType eAct)
{
    if (eAct == MM::AfterSet)
    {
        std::string answer;

        pProp->Get(answer);

        if (answer.empty() == false) {
            serialResponse_ = SerialCommand(answer);
            SetProperty(g_PropertyCoboltSerialCommandResponse, serialResponse_.c_str());
        }
    } 
    return DEVICE_OK;
}
int Cobolt::OnSerialCommandResponse(MM::PropertyBase* pProp , MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(serialResponse_.c_str());
    } 
    return DEVICE_OK;
}
int Cobolt::OnPowerMaximum(MM::PropertyBase* pProp , MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        powerMaximum_ = SerialCommand (ID_ + "gmlp?");
        pProp->Set(powerMaximum_.c_str());
    } 
    return DEVICE_OK;
}
int Cobolt::OnControlMode(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    std::string answer;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(controlMode_.c_str());  
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (controlMode_.compare(answer) != 0) {
            controlMode_ = answer;
            if (controlMode_.compare(g_PropertyCoboltControlModePower) == 0) {
                SerialCommand(ID_ + "cp");
                SetProperty(g_PropertyCoboltModulationStatus, g_PropertyDisabled);
            }
            else if (controlMode_.compare(g_PropertyCoboltControlModeCurrent) == 0) {
                SerialCommand(ID_ + "ci");
                SetProperty(g_PropertyCoboltModulationStatus, g_PropertyDisabled);
            }
            if (controlMode_.compare(g_PropertyCoboltControlModeModulation) == 0) {
                SerialCommand(ID_ + "em");
                SetProperty(g_PropertyCoboltModulationStatus, g_PropertyEnabled);
            }
        }
    }

    return DEVICE_OK;
}
int Cobolt::OnAutoStart(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    if (eAct == MM::BeforeGet)
    {

    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(autostartStatus_);

        if (autostartStatus_.compare("Enabled") == 0) {
            SerialCommand("@cobas 1");
        }
        else if (autostartStatus_.compare("Disabled") == 0) {
            SerialCommand("@cobas 0");
        }
    }

    return DEVICE_OK;
}
int Cobolt::OnAutoStartStatus(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    if (eAct == MM::BeforeGet)
    {
        AutoStartStatus();

        pProp->Set(autostartStatus_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnActive(MM::PropertyBase* pProp, MM::ActionType  eAct)
{

    // This property is only shown if an actual Cobolt

    std::string answer;

    if (eAct == MM::BeforeGet)
    {	
        answer = SerialCommand(ID_ + "gla? ");
        if (answer.compare("0") == 0) pProp->Set(g_PropertyInactive);
        if (answer.compare("1") == 0) pProp->Set(g_PropertyActive);
        //LogMessage("Cobolt::OnActive 1 " + answer);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (answer.compare(g_PropertyActive) == 0) SerialCommand(ID_ + "sla 1");
        if (answer.compare(g_PropertyInactive) == 0) SerialCommand(ID_ + "sla 0");
        //LogMessage("Cobolt::OnActive 2" + answer);

    }

    return DEVICE_OK;
}
int Cobolt::OnPowerMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(g_Deprecated);
    }
   
    return DEVICE_OK;
}
int Cobolt::OnPower(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(Power_.c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        double power; 
        pProp->Get(power);
        if (power >= 0) {
            if (power <= std::atof(powerMaximum_.c_str())) {
            std::ostringstream strs;
            strs << (long double) power;
            Power_ = strs.str();
            // Power_ = std::to_string((long double)power);
            if (power > 0) {
                //Power_ = std::to_string((long double)power);
                powerSetPoint_ = Power_; // remember high power value
            }
        }

        // Switch to constant power mode if not already set that way
        if (controlMode_.compare(g_PropertyCoboltControlModePower)  != 0) 
            SetProperty(g_PropertyCoboltControlMode,g_PropertyCoboltControlModePower);

        SetPower(Power_,ID_);
        }
    }
    return DEVICE_OK;
}
int Cobolt::OnPowerSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct )
{
    if (eAct == MM::BeforeGet)
    {
        std::string answer;
        double value = std::atof((SerialCommand(ID_ + "p?")).c_str()) * 1000; 
        std::ostringstream strs;
        strs << (long double) value;
        powerSetPoint_ = strs.str();
        // powerSetPoint_ = std::to_string((long double)value); 
        pProp->Set(powerSetPoint_.c_str());
    } 

    return DEVICE_OK;
}
int Cobolt::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType  eAct)
{
    if (eAct == MM::BeforeGet)
    {
        //if (nCobolt_) powerStatus_ = SerialCommand(ID_ + "glp?");
        //else  {
            double value = std::atof((SerialCommand(ID_ + "pa?")).c_str()) * 1000; 
            std::ostringstream strs;
            strs << (long double) value;
            powerStatus_ = strs.str();
            //powerStatus_ = std::to_string((long double)value); 
        //}
        pProp->Set(powerStatus_.c_str());
    }
    return DEVICE_OK;
}
int Cobolt::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        hours_= SerialCommand ("hrs?");

        pProp->Set(hours_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnKeyStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    if (eAct == MM::BeforeGet)
    {
        std::string answer;
        std::ostringstream command;

        answer = SerialCommand("@cobasks?");

        if (answer.at(0) == '0')
            keyStatus_ = g_PropertyOff;
        else if (answer.at(0) == '1')
            keyStatus_ = g_PropertyOn;

        pProp->Set(keyStatus_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnLaserType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(Type_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnCurrentStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    if (eAct == MM::BeforeGet)
    {
        std::string answer;
        answer = SerialCommand(ID_ + "i? ");
        pProp->Set(answer.c_str());
    }
    return DEVICE_OK;
}
int Cobolt::OnCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(Current_.c_str());

    } else 
    {
        if (eAct == MM::AfterSet)
        {
            double current;
            pProp->Get(current);
            
            if (current >= 0) {
            if (current <= std::atof(currentMaximum_.c_str())) {
               std::ostringstream strs;
               strs << (long double) current;
               Current_ = strs.str();
                //Current_ = std::to_string((long double) current);
                if (current > 0) {
                    currentSetPoint_ = Current_;
                }
                // Switch to Current regulated mode and then set Current Level
                SetProperty(g_PropertyCoboltControlMode,g_PropertyCoboltControlModeCurrent);
                SetProperty(g_PropertyCoboltModulationStatus, g_PropertyDisabled);
                SerialCommand (ID_ + "slc " + Current_);
                }
            }
        }
    }
    return DEVICE_OK;
}
int Cobolt::OnCurrentSetPoint(MM::PropertyBase*  pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        // not sure of this command works on a non Cobolt
        if (nCobolt_) currentSetPoint_ = SerialCommand (ID_ + "glc?");
        pProp->Set(currentSetPoint_.c_str());
    } 
    return DEVICE_OK;
}
int Cobolt::OnCurrentMaximum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        currentMaximum_ = SerialCommand (ID_ + "gmlc?");
        pProp->Set(currentMaximum_.c_str());
    } 
    return DEVICE_OK;
}
int Cobolt::OnCurrentModulationMaximum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        currentModulationMaximum_ = SerialCommand (ID_ + "gmc?");
        pProp->Set(currentModulationMaximum_.c_str());
    } else 
    {
        if (eAct == MM::AfterSet)
        {
            pProp->Get(currentModulationMaximum_);
            SerialCommand (ID_ + "smc " + currentModulationMaximum_);
            SetPropertyLimits(g_PropertyCoboltCurrentModulationMinimum,0,std::atof(currentModulationMaximum_.c_str()));
        }
    }
    return DEVICE_OK;
}
int Cobolt::OnCurrentModulationMinimum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        currentModulationMinimum_ = SerialCommand (ID_ + "glth?");
        pProp->Set(currentModulationMinimum_.c_str());
    } else 
        if (eAct == MM::AfterSet)
        {
            pProp->Get(currentModulationMinimum_);
            SerialCommand (ID_ + "slth " + currentModulationMinimum_);
            SetPropertyLimits(g_PropertyCoboltCurrentModulationMaximum, 
            std::atof(currentModulationMinimum_.c_str()),std::atof(currentMaximum_.c_str()));
        }
        return DEVICE_OK;
}
int Cobolt::OnAnalogImpedance(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    if (eAct == MM::AfterSet)
    {
        std::string answer;
        pProp->Get(answer);

        if (answer.compare("Enabled") == 0)
            SerialCommand("salis 1");
        if (answer.compare("Disabled") == 0)
            SerialCommand("salis 0");
    }

    return DEVICE_OK;
}
int Cobolt::OnAnalogImpedanceStatus(MM::PropertyBase* pProp, MM::ActionType  eAct )
{
    if (eAct == MM::BeforeGet) {
        AnalogImpedanceStatus();
        pProp->Set(impedanceStatus_.c_str());
    }
    return DEVICE_OK;
}
int Cobolt::OnInterlock(MM::PropertyBase* pProp, MM::ActionType  eAct )
{
    if (eAct == MM::BeforeGet) {

        std::string answer;

        answer = SerialCommand ("ilk?");

        if (answer.at(0) == '0')
            interlock_ = "Closed";
        else if (answer.at(0) == '1')
            interlock_ = "Open";

        pProp->Set(interlock_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnOperatingStatus(MM::PropertyBase* pProp, MM::ActionType eAct )
{
    if (eAct == MM::BeforeGet) {
        std::string answer;

        answer = SerialCommand ("gom?");

#ifndef _SKYRA_DEBUG_
        if (answer.at(0) == '0')
            operatingStatus_ = g_PropertyOff;
        else if (answer.at(0) == '1')
            operatingStatus_ = "Waiting for temperature";
        else if (answer.at(0) == '2')
            operatingStatus_ = "Continuous";
        if (answer.at(0) == '3')
            operatingStatus_ = "On/Off Modulation";
        else if (answer.at(0) == '4') {
            operatingStatus_ = g_PropertyCoboltControlModeModulation;
            SetProperty(g_PropertyCoboltControlMode,operatingStatus_.c_str());
        }
        else if (answer.at(0) == '5')
            operatingStatus_ = "Fault";
        else if (answer.at(0) == '6')
            operatingStatus_ = "Aborted: Complete Laser Start Required";     
#else
        if (answer.at(0) == '0')
            operatingStatus_ = g_PropertyOff;
        else if (answer.at(0) == '1')
            operatingStatus_ = "Waiting for temperature";
        else if (answer.at(0) == '2')
            operatingStatus_ = "Waiting for key";
        else if (answer.at(0) == '3')
            operatingStatus_ = "Warm-up";
        else if (answer.at(0) == '4')
             operatingStatus_ = "Completed";
        else if (answer.at(0) == '5')
            operatingStatus_ = "Fault";
        else if (answer.at(0) == '6')
            operatingStatus_ = "Aborted: Complete Laser Start Required";             
#endif
        pProp->Set(operatingStatus_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnFault(MM::PropertyBase* pProp, MM::ActionType eAct )
{
    if (eAct == MM::BeforeGet) {
        std::string answer;

        answer = SerialCommand ("f?");

        if (answer.at(0) == '0')
            fault_ = "No Fault";
        else if (answer.at(0) == '1')
            fault_ = "Temperature Fault";
        else if (answer.at(0) == '3')
            fault_ = "Open Interlock";
        else if (answer.at(0) == '4')
            fault_ = "Constant Power Fault";

        pProp->Set(fault_.c_str());
    }

    return DEVICE_OK;
}
int Cobolt::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType  eAct)
{

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(waveLength_.c_str()); 
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(waveLength_);

        for (int x = 0; x < waveLengths_.size(); x++) {
            if (waveLengths_[x].compare(waveLength_) == 0) {
                ID_ = IDs_[x]; 
                UpdateWaveLength(ID_);
                break;
            }
        }
        
    }
    return DEVICE_OK;
}
int Cobolt::OnAnalogModulation(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
    std::string answer;

    if (eAct == MM::BeforeGet)
    {
        MM::Property* pChildProperty = (MM::Property*)pProp;
        if (bInternalModulation_ == true) {
            pChildProperty->SetReadOnly(true);
            pProp->Set(g_PropertyDisabled);
        } else {
            pChildProperty->SetReadOnly(false);
            if (bAnalogModulation_) pProp->Set(g_PropertyEnabled);
            else pProp->Set(g_PropertyDisabled);
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);

        if (answer.compare(g_PropertyEnabled) == 0) SetModulation(MODULATION_ANALOG, true);
        else SetModulation(MODULATION_ANALOG, false);
    }

    return DEVICE_OK;
}
int Cobolt::OnDigitalModulation(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
    std::string answer;

    if (eAct == MM::BeforeGet)
    {
        MM::Property* pChildProperty = (MM::Property*)pProp;
        if (bInternalModulation_ == true) {
            pChildProperty->SetReadOnly(true);
            pProp->Set(g_PropertyDisabled);
        } else {
            pChildProperty->SetReadOnly(false);
            if (bDigitalModulation_) pProp->Set(g_PropertyEnabled);
            else pProp->Set(g_PropertyDisabled);
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (answer.compare(g_PropertyEnabled) == 0) SetModulation(MODULATION_DIGITAL, true);
        else SetModulation(MODULATION_DIGITAL, false);
    }

    return DEVICE_OK;
}
int Cobolt::OnInternalModulationPeriod(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
    

    if (eAct == MM::BeforeGet)
    {
        nInternalModulationPeriodTime_ = std::atoi(SerialCommand(ID_ + "gswmp?").c_str());
        pProp->Set(nInternalModulationPeriodTime_);
    }
    else if (eAct == MM::AfterSet)
    {	
        std::string answer;

        pProp->Get(answer);
        nInternalModulationPeriodTime_ = std::atoi(answer.c_str());
        // Limited to INT_MAX and >= On Time
        if (nInternalModulationPeriodTime_ >= 0 && nInternalModulationPeriodTime_ <= INT_MAX) {
            if (nInternalModulationPeriodTime_ < nInternalModulationOnTime_) {
                nInternalModulationOnTime_ = nInternalModulationPeriodTime_;
                SerialCommand(ID_ + "sswmo " + answer);
            }
            SerialCommand(ID_ + "sswmp " + answer);
        }
    }

    return DEVICE_OK;
}
int Cobolt::OnInternalModulationOn(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 

    if (eAct == MM::BeforeGet)
    {
      nInternalModulationOnTime_ = std::atoi(SerialCommand(ID_ + "gswmo?").c_str());
        pProp->Set(nInternalModulationOnTime_);
    }
    else if (eAct == MM::AfterSet)
    {
        std::string answer;
        // Limited to INT_MAX or OnInternalModulationPeriod, whichever is less
        pProp->Get(answer);

        nInternalModulationOnTime_ = std::atoi(answer.c_str());
        if (nInternalModulationOnTime_ >= 0 && nInternalModulationOnTime_ <= INT_MAX) {
            if (nInternalModulationOnTime_ > nInternalModulationPeriodTime_) {
                nInternalModulationPeriodTime_ = nInternalModulationOnTime_;
                SerialCommand(ID_ + "sswmp " + answer);
            }
            SerialCommand(ID_ + "sswmo " + answer);
        }
    }

    return DEVICE_OK;
}
int Cobolt::OnInternalModulationDelay(MM::PropertyBase* pProp, MM::ActionType  eAct)
{ 
    std::string answer;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(SerialCommand(ID_ + "gswmod?").c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        int value = std::atoi(answer.c_str());
        if (value >= 0) if (value <= INT_MAX) SerialCommand(ID_ + "sswmod " + answer);
    }

    return DEVICE_OK;
}
int Cobolt::OnLaserHelp1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        if (laserStatus_.compare(g_PropertyOn) == 0) pProp->Set(g_PropertyCoboltAutostartHelp3); 
        else if (laserStatus_.compare(g_PropertyOff) == 0) pProp->Set(g_PropertyCoboltAutostartHelp1); 
    }

    return DEVICE_OK;
}
int Cobolt::OnLaserHelp2(MM::PropertyBase* pProp , MM::ActionType eAct)
{	
    if (eAct == MM::BeforeGet)
    {
        if (laserStatus_.compare(g_PropertyOn) == 0) pProp->Set(g_PropertyCoboltAutostartHelp4); 
        else if (laserStatus_.compare(g_PropertyOff) == 0) pProp->Set(g_PropertyCoboltAutostartHelp2); 
    }

    return DEVICE_OK;
}
int Cobolt::OnAllLasers(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    std::string answer;

    if (eAct == MM::BeforeGet) {
        answer = SerialCommand(ID_ + "l?");
        if (answer.compare("1") == 0) pProp->Set(g_PropertyOn);
        else pProp->Set(g_PropertyOff);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (answer.compare(g_PropertyOn) == 0)
        {
            AllLasersOn(true);
        }
        else
        {
            AllLasersOn(false);
        }

    }
    return DEVICE_OK;
} 
int Cobolt::OnLaser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    // This property is only shown if an actual Cobolt
    std::string answer;
    if (eAct == MM::BeforeGet)
    {
        answer = SerialCommand(ID_ + "l?");  
        if (answer.compare("1") == 0)
        {
            pProp->Set(g_PropertyOn);
            bOn_ = true;
        }
        else
        {
            pProp->Set(g_PropertyOff);
            bOn_ = false;
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(answer);
        if (answer.compare(g_PropertyOn) == 0)
        {
            SerialCommand(ID_ + "l1");
            bOn_ = true;
        }
        else
        {
            SerialCommand(ID_ + "l0"); 
            bOn_ = false;
        }
    }
    return DEVICE_OK;
} 
int Cobolt::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

    std::string answer;

    if (eAct == MM::BeforeGet)
    {
        answer = SerialCommand(ID_ + "l?"); 

        if (answer.compare("1") == 0)
        {
            pProp->Set(g_PropertyOn);
        }
        else
        {
            if (answer.compare("0") == 0) pProp->Set(g_PropertyOff);
        }
    }

    return DEVICE_OK;
} 
///////////////////////////////////////////////////////////////////////////////
// Base Calls  
///////////////////////////////////////////////////////////////////////////////
std::string Cobolt::SetPower(std::string requestedPowerSetpoint, std::string laserid = "")
{
    std::string answer;

    double dPower = std::atof(requestedPowerSetpoint.c_str())/1000;
    std::ostringstream strs;
    strs << (long double)dPower;
    answer = SerialCommand (ID_ + "p " + strs.str());

    // answer = SerialCommand (ID_ + "p " + std::to_string((long double)dPower));

    //LogMessage("SetPower: " + ID_ + std::to_string((long double)dPower));

    return answer;
}
std::string Cobolt::AutoStartStatus() {

    std::string answer;

    answer = SerialCommand("@cobas?");

    if (answer.at(0) == '0')
        autostartStatus_ = g_PropertyDisabled;
    else if (answer.at(0) == '1')
        autostartStatus_ = g_PropertyEnabled;

    return answer;
}
std::string Cobolt::SetModulation(int modulation, bool value) 
{
    std::string answer;

    // Analog and Digital can be active at the same time, but Internal must only be activated by itself
    if (modulation == MODULATION_ANALOG) {
        bAnalogModulation_ = value;
        if (bAnalogModulation_) {
            SerialCommand (ID_ + "eswm 0");
            answer = SerialCommand (ID_ + "sames 1");
            
        }
        else answer = SerialCommand (ID_ + "sames 0");
    }

    if (modulation == MODULATION_DIGITAL) {
        bDigitalModulation_ = value;
        if (bDigitalModulation_) {
            SerialCommand (ID_ + "eswm 0");
            answer = SerialCommand (ID_ + "sdmes 1");
        }
        else  answer = SerialCommand (ID_ + "sdmes 0");
    }

    return answer;
}
std::string Cobolt::UpdateWaveLength(std::string id) 
{
    std::string value;
    double dValue;
    
    RASP_ = GetRASP(id);
    RA_ = GetReadAll(id);

    //LogMessage("Cobolt::UpdateWaveLength " + id, true);

    // update Current Values
    // RA
    SetProperty(g_PropertyCoboltCurrentStatus,currentStatus_.c_str());

    // RASP
    SetProperty(g_PropertyCoboltCurrentSetpoint,currentSetPoint_.c_str());

    currentMaximum_ = SerialCommand (ID_ + "gmlc?");
    dValue = std::atof(currentMaximum_.c_str());
    // Reset Limits when switching wavelengths
    SetPropertyLimits(g_PropertyCoboltCurrent,0,dValue);
    SetPropertyLimits(g_PropertyCoboltCurrentModulationMinimum,0,dValue);
    SetPropertyLimits(g_PropertyCoboltCurrentModulationMaximum,0,dValue);

    // Current Maximum
    SetProperty(g_PropertyCoboltCurrentMaximum,currentModulationMaximum_.c_str());
    
    // Current Modulation Maximum
    currentModulationMaximum_ = SerialCommand (ID_ + "gmc?");
    SetProperty(g_PropertyCoboltCurrentModulationMaximum,currentModulationMaximum_.c_str());

    // Current Modulation Minimum
    currentModulationMinimum_ = SerialCommand (ID_ + "glth?");
    SetProperty(g_PropertyCoboltCurrentModulationMinimum,currentModulationMinimum_.c_str());
             
    // update Power Output -- RA
    SetProperty(g_PropertyCoboltPowerStatus,powerStatus_.c_str());

    // update Power Setting -- RASP
    SetProperty(g_PropertyCoboltPowerSetpoint,powerSetPoint_.c_str());

    // Power Maximum
    powerMaximum_ = SerialCommand (ID_ + "gmlp?");
    // Set Property Limits for Power
    dValue = std::atof(powerMaximum_.c_str());
    SetPropertyLimits(g_PropertyCoboltPower,0,dValue);
    
    SetProperty(g_PropertyCoboltCurrentMaximum,powerMaximum_.c_str());

    // Laser Type
    // get default type if not Cobolt
    Type_ = SerialCommand(id + "glm?");
    if (Type_.length() > 3) Type_.resize(3);

    SetProperty(g_PropertyCoboltLaserType,Type_.c_str());

    // update active status
    value = SerialCommand(id + "gla? ");
    if (value.compare("0") == 0) SetProperty(g_PropertyCoboltActiveStatus, g_PropertyInactive);
    else if (value.compare("1") == 0) SetProperty(g_PropertyCoboltActiveStatus, g_PropertyActive);
    
    // Control Mode
    SetProperty(g_PropertyCoboltControlMode, controlMode_.c_str());

    // update modulation status
    if (bAnalogModulation_) SetProperty(g_PropertyCoboltAnalogModulation, g_PropertyEnabled);
    else SetProperty(g_PropertyCoboltAnalogModulation, g_PropertyDisabled);

    if (bDigitalModulation_) SetProperty(g_PropertyCoboltDigitalModulation, g_PropertyEnabled);
    else  SetProperty(g_PropertyCoboltDigitalModulation, g_PropertyDisabled);

    if (bInternalModulation_) SetProperty(g_PropertyCoboltInternalModulation, g_PropertyEnabled);
    else SetProperty(g_PropertyCoboltInternalModulation, g_PropertyDisabled);

    return value;

}
std::string Cobolt::GetRASP(std::string laserID) {

    char * pch = NULL;
    
    if (laserID.empty() == true) return ("Cobolt::GetReadAll:Empty ID");
    
    RASP_ = SerialCommand (laserID + "rasp?");

    if (!RASP_.empty()) {	
        strncpy(tempstr_, RASP_.c_str(),SERIAL_BUFFER);
            
        //
        // Position 1: Unknown
        // 
        pch = strtok (tempstr_, " ");
        if (pch != NULL) //LogMessage("Cobolt::GetReadAll: Unknown " + *pch, true);
        //}

        //
        // Position 2: Current
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            currentSetPoint_ = pch;
            //LogMessage("Cobolt::GetReadAll: Current Set " + *pch, true);
        }

        //
        // Position 3: Powwer
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            powerSetPoint_ = pch;
            //LogMessage("Cobolt::GetReadAll: Power Set " + *pch, true);
        }
    }

    return RASP_;

}
std::string Cobolt::GetReadAll(std::string laserID) {

    // #ra?
    // 6 or 7 numbers with the string format: 
    //
    // "mA mW on(1 = on, 0 = off) mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
    //
    // "Photodiode(V) Current(mA) Power(mW) on(1 = on, 0 = off) Mode(0 = ci, 1 = cp,2 = em) unknown Modulation"
    //   
    // Mode: is set to 0 if autostart is disabled, and if autostart is re-enabled, it will be set to 1 (cp)
    // Modulation: Digital = 1, Analog = 2, both = 3

    // #rasp?
    
    
    char * pch = NULL;
    std::string answer;
    
    if (laserID.empty() == true) return ("Cobolt::GetReadAll:Empty ID");

    RA_ = SerialCommand (laserID + "ra?");
    if (!RA_.empty()) {	
        
        strncpy(tempstr_, RA_.c_str(),SERIAL_BUFFER);
            
        pch = strtok (tempstr_, " ");

        //
        // DLP Position 1: Photodiode Voltage
        //
        // 
        if (pch != NULL) {
            Type_ = SerialCommand (ID_ + "glm?");
            if (Type_.length() > 3) Type_.resize(3);
            if (Type_.compare("DPL") == 0) { 
                photoDiode_ = pch;
                pch = strtok (NULL, " ");
                //LogMessage("Cobolt::GetReadAll: PV " + *pch, true);
            }
        }
        
        //
        //
        // Position 1: Current in mA
        //
        if (pch != NULL) {
            currentStatus_ = *pch;
            //LogMessage("Cobolt::GetReadAll: Current " + *pch, true);
        }
        
        //
        // Position 2: Power in mW
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            powerStatus_ = pch;
            //LogMessage("Cobolt::GetReadAll: Power " + *pch, true); 
        }

        //
        // Position 3: Laser On?
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            if (strcmp(pch,"1") == 0) bOn_ = true;
            else bOn_ = false;
            //LogMessage("Cobolt::GetReadAll: On " + *pch, true);
        }
        
        //
        // Position 4: Control Mode?
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            if (pch[0] == '0') controlMode_ = g_PropertyCoboltControlModeCurrent;
            if (pch[0] == '1')  controlMode_= g_PropertyCoboltControlModePower;
            if (pch[0] == '2')  controlMode_ = g_PropertyCoboltControlModeModulation;
            //LogMessage("Cobolt::GetReadAll: Control Mode " + *pch, true);
        } 

        //
        // Position 5:   Control Mode?
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            //LogMessage("Cobolt::GetReadAll: Unknown " + *pch, true);
        }
        
        //
        // Position 6: Modulation Mode
        //
        pch = strtok (NULL, " ");
        if (pch != NULL) {
            bDigitalModulation_ = bAnalogModulation_ = false;
            if (strcmp(pch,"1") == 0) bDigitalModulation_ = true;
            if (strcmp(pch,"2") == 0) bAnalogModulation_ = true;
            if (strcmp(pch,"3") == 0) bDigitalModulation_ = bAnalogModulation_ = true;
            //LogMessage("Cobolt::GetReadAll: Modulation Mode " + *pch, true);
        } 
    }

    // get Internal, even though it isn't convered by ra?
    answer = SerialCommand (ID_ + "gswm?");
    if (answer.at(0) == '1')  bInternalModulation_ = true;
    else bInternalModulation_ = false;

    return RA_;
}
std::string Cobolt::AnalogImpedanceStatus() {

    std::string answer;

    answer = SerialCommand ("galis?");   
    if (answer.at(0) == '0') impedanceStatus_ = g_PropertyDisabled;
    else if (answer.at(0) == '1') impedanceStatus_ = g_PropertyEnabled;

    if (answer.compare(g_Msg_UNSUPPORTED_COMMAND) == 0 ) bImpedance_ = false;
    else bImpedance_ = true;

    return answer;
}
//********************
// Shutter API
//********************

int Cobolt::SetOpen(bool open)
{
    if (autostartStatus_.compare(g_PropertyDisabled)== 0) AllLasersOn((int) open);
    else {
        if (controlMode_.compare(g_PropertyCoboltControlModeCurrent) !=0) { 
            SetProperty(g_PropertyCoboltControlMode,g_PropertyCoboltControlModeCurrent);
        }

        if (open) {
            // return to last Current Setting
             SerialCommand(ID_ + "slc " + currentModulationMaximum_);
        }
        else {
            if (bModulation_) SerialCommand(ID_ + "slc " + currentModulationMinimum_);
            else SerialCommand(ID_ + "slc 0");
        }
    }
    return DEVICE_OK;
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
std::string Cobolt::SerialCommand (std::string serialCommand) {

    std::ostringstream command;
    std::string answer;

    command << serialCommand;

    int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), g_SendTerm);
    if (ret != DEVICE_OK) return "Sending Serial Command Failed";
    ret = GetSerialAnswer(port_.c_str(), g_RecvTerm, answer);
    if (ret != DEVICE_OK) return  "Receiving Serial Command Failed";

    if (answer.compare ("Syntax error: illegal command") == 0) return g_Msg_UNSUPPORTED_COMMAND;
    if (answer.compare ("Syntax error: only allowed when operating mode is \'Completed\'.") == 0) {
        SetProperty(g_PropertyCoboltOperatingStatus,"Aborted: Complete Laser Start Required");
    }

    return answer;
}
std::string Cobolt::GetPort()
{
    std::string port;

    port = port_;

    return port;
}