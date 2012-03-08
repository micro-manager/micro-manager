///////////////////////////////////////////////////////////////////////////////
// FILE:          Coboltcpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls power levels of Cobolt lasers through a serial port
// COPYRIGHT:     University of Massachusetts Medical School, 2009
// LICENSE:       LGPL
// AUTHOR:        Karl Bellve, Karl.Bellve@umassmed.edu
//

#include "Cobolt.h"

const char* g_DeviceCoboltName = "Cobolt";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceCoboltName, "Cobolt Laser Controller");
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
   answerTimeoutMs_(1000),
   power_(0.00),
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
     CPropertyAction* pAct = new CPropertyAction (this, &Cobolt::OnPower);
     int nRet = CreateProperty("Power", "0.00", MM::Float, false, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Cobolt::OnLaserOnOff);
     CreateProperty("Laser", "Off", MM::String, false, pAct);    

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
     nRet = CreateProperty("PowerStatus", "0.00", MM::String, true, pAct);    
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
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", answer);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::LaserOnOff() %s\n",answer.c_str());   

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

int Cobolt::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{

     if (eAct == MM::BeforeGet)
     {
          pProp->Set(power_);
     }
     else if (eAct == MM::AfterSet)
     {
          std::string answer;
          std::ostringstream command;
          //int laseron = 0;
          
          // Our Cobolt Jive doesn't like the power level to change while the laser is lasering, so turn the laser off, then set the power level, and then turn it back on
          //if (laserOn_ == "On") {
          //     LaserOnOff(0);
          //     laseron = 1;
          //}
          
          pProp->Get(power_);
          command << "p " << power_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\n", answer);
          if (ret != DEVICE_OK) return ret;
          //if (laseron == 1) LaserOnOff(1);
     }

   return DEVICE_OK;
}

int Cobolt::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
               
     command << "p? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     
     return DEVICE_OK;
}

int Cobolt::OnHours(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::ostringstream command;

     command << "hrs? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", hours_);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::OnHours() %s\n",hours_.c_str());

     pProp->Set(hours_.c_str());

     return DEVICE_OK;
}

int Cobolt::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::ostringstream command;

     command << "sn? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", serialNumber_);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::OnSerialNumber() %s\n",serialNumber_.c_str());

     pProp->Set(serialNumber_.c_str());
     
     return DEVICE_OK;
}

int Cobolt::OnVersion(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::ostringstream command;
     
     command << "ver? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", version_);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::OnSerialNumber() %s\n",serialNumber_.c_str());
     
     pProp->Set(version_.c_str());
     
     return DEVICE_OK;
}

int Cobolt::OnCurrent(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

          
     std::ostringstream command;
     
     command << "i? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", current_);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::OnCurrent() %s\n",current_.c_str());

     pProp->Set(current_.c_str());

     return DEVICE_OK;
}

int Cobolt::OnInterlock(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{

    std::ostringstream command;
    std::string answer;

     command << "ilk? ";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", answer);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::Interlock() %s\n",answer.c_str());

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
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", answer);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::Fault() %s\n",answer.c_str());

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
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n", answer);
     if (ret != DEVICE_OK) return ret;
     //fprintf(stderr,"Cobolt::LaserStatus() %s\n",answer.c_str());

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
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\n", answer);
          if (ret != DEVICE_OK) return ret;
          //fprintf(stderr,"Cobolt::LaserOnOff() %s\n",answer.c_str());

          if (answer.at(0) == '0') 
               laserOn_ = "Off";
          else if (answer.at(0) == '1') 
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