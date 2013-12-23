///////////////////////////////////////////////////////////////////////////////
// FILE:          Oxxius.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Oxxius lasers through a serial port
// COPYRIGHT:     Oxxius SA, 2013
// LICENSE:       LGPL
// AUTHOR:        Julien Beaurepaire
//

#include "Oxxius.h"


const char* g_DeviceOxxLBXLDName = "Oxxius LBX-LD";
const char* g_DeviceOxxLBXDPSSName = "Oxxius LBX-DPSS";



// Windows DLL entry routine
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
    switch(ul_reason_for_call)
    {
        case DLL_PROCESS_ATTACH:
        case DLL_THREAD_ATTACH:
        case DLL_THREAD_DETACH:
        case DLL_PROCESS_DETACH:
            break;
    }
    return TRUE;
}
#endif



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceOxxLBXLDName, MM::GenericDevice, "Oxxius LBX-LD Laser Controller");
   RegisterDevice(g_DeviceOxxLBXDPSSName, MM::GenericDevice, "Oxxius LBX-DPSS Laser Controller");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (deviceName == 0)
	   return 0;

    if (strcmp(deviceName, g_DeviceOxxLBXLDName) == 0)
    {
	   return new OxxLBXLD;
    }else if(strcmp(deviceName, g_DeviceOxxLBXDPSSName) == 0)
	{
		return new OxxLBXDPSS;
	}

    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Oxxius LBX DPSS
///////////////////////////////////////////////////////////////////////////////
OxxLBXDPSS::OxxLBXDPSS() :
   port_("Undefined"),
   initialized_(false),
   busy_(false),
   answerTimeoutMs_(1000)
{
     InitializeDefaultErrorMessages();
     SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

     // Name
     CreateProperty(MM::g_Keyword_Name, g_DeviceOxxLBXDPSSName, MM::String, true);

     // Description
     CreateProperty(MM::g_Keyword_Description, "Oxxius Laser Controller", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &OxxLBXDPSS::OnPort);
     CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

OxxLBXDPSS::~OxxLBXDPSS()
{
     Shutdown();
}




void OxxLBXDPSS::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, g_DeviceOxxLBXDPSSName);
}


int OxxLBXDPSS::Initialize()
{
	 CPropertyAction* pAct = new CPropertyAction (this, &OxxLBXDPSS::OnSerialNumber);
     int nRet = CreateProperty("Serial Number", "0", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 
	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnVersion);
     nRet = CreateProperty("Soft Version", "0", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 CPropertyActionEx* pActEx = new CPropertyActionEx (this, &OxxLBXDPSS::OnPowerSP, 0);
     nRet = CreateProperty("PowerSetPoint (%)", "0.00", MM::Float, false, pActEx);
     if (DEVICE_OK != nRet)
          return nRet;
	 SetPropertyLimits("PowerSetPoint (%)", 0, 100);
	 

	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnPower);
	 nRet = CreateProperty("Power", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnLaserOnOff);
     nRet = CreateProperty("Laser", "Off", MM::String, false, pAct);    
	 if (DEVICE_OK != nRet)
          return nRet;

     std::vector<std::string> commands;
     commands.push_back("Off");
     commands.push_back("On");
     SetAllowedValues("Laser", commands);

	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnHours);
	 nRet = CreateProperty("Hours", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
  
     pAct = new CPropertyAction (this, &OxxLBXDPSS::OnFault);
     nRet = CreateProperty("Fault", "No Fault", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     

	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnWaveLength);
	 nRet = CreateProperty("Wavelength", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnBaseTemp);
     nRet = CreateProperty("Base Temperature", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 pAct = new CPropertyAction (this, &OxxLBXDPSS::OnControllerTemp);
     nRet = CreateProperty("Controller Temperature", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &OxxLBXDPSS::OnLaserStatus);
     nRet = CreateProperty("LaserStatus", "Off", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 
     nRet = UpdateStatus();
     if (nRet != DEVICE_OK)
          return nRet;

     
     initialized_ = true;
     return DEVICE_OK;
}







int OxxLBXDPSS::Shutdown()
{
   if (initialized_)
   {
      LaserOnOff(false);
	 initialized_ = false;
	 
   }
   return DEVICE_OK;
}

bool OxxLBXDPSS::Busy()
{
   return busy_;
}

int OxxLBXDPSS::LaserOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0) 
     {
          command << "dl 0";
          laserOn_ = "Off";
     }
     else if (onoff == 1) 
     {
          command << "dl 1";
          laserOn_ = "On";
     }
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
     
	 return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int OxxLBXDPSS::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int OxxLBXDPSS::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;
	 std::string answer;
     command << "hid?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
	 
     pProp->Set(answer.c_str());
     
     return DEVICE_OK;
}

int OxxLBXDPSS::OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long )
{
	std::string answer;
	std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          
          command << "ip";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  pProp->Set(atof(answer.c_str()));
     }
     else if (eAct == MM::AfterSet)
     {
          
          
          
          pProp->Get(powerSP_);
          command << "ip " << powerSP_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
          if (ret != DEVICE_OK) return ret;
          
     }

   return DEVICE_OK;
}



int OxxLBXDPSS::OnPower(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "ip?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     
     return DEVICE_OK;
}

int OxxLBXDPSS::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{

     std::ostringstream command;
     std::string answer; 
     laserOn_ = "Undefined";
     if (eAct == MM::BeforeGet)
     {
          command << "dl?";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
          if (ret != DEVICE_OK) return ret;
          
		  if (answer.compare("Laser off") == 0) 
               laserOn_ = "Off";
          else if (answer.compare("Laser alarm") == 0) 
               laserOn_ = "Off";
		  else
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
		  CDeviceUtils::SleepMs(500);
     }
     return DEVICE_OK;
}
int OxxLBXDPSS::OnHours(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
	 std::ostringstream command;

     command << "tm?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
     std::string::size_type Pos1 = answer.find_first_of("= ", 0)+2;
	 std::string::size_type Pos2 = answer.find_first_of(" hrs", 0);

	 pProp->Set(atof(answer.substr(Pos1,Pos2-Pos1).c_str()));

     return DEVICE_OK;
}




int OxxLBXDPSS::OnFault(MM::PropertyBase* pProp, MM::ActionType )
{

     std::ostringstream command;
     std::string answer;
     
     command << "al?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", fault_);
     if (ret != DEVICE_OK) return ret;
     
     pProp->Set(fault_.c_str());

     return DEVICE_OK;
}

int OxxLBXDPSS::OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "bt?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
	 std::string::size_type Pos = answer.find_first_of(" C", 0);
     pProp->Set(atof(answer.substr(0,Pos).c_str()));
     
     return DEVICE_OK;
}
int OxxLBXDPSS::OnControllerTemp(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "et?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;
	 std::string::size_type Pos = answer.find_first_of(" C", 0);
	 pProp->Set(atof(answer.substr(0,Pos).c_str()));
     
     return DEVICE_OK;
}





int OxxLBXDPSS::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;
	 std::string answer;
     command << "inf?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\n\r", answer);
     if (ret != DEVICE_OK) return ret;

	 std::string::size_type Pos = answer.find_first_of("-", 0)+1;
	 answer = answer.substr(Pos,std::string::npos);
	 Pos = answer.find_first_of("-", 0);
	 waveLength_ = answer.substr( 0,Pos);
     pProp->Set(waveLength_.c_str());
     
     return DEVICE_OK;
}
int OxxLBXDPSS::OnVersion(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;

     command << "ve?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\n\r", softVersion_);
     if (ret != DEVICE_OK) return ret;
     
     pProp->Set(softVersion_.c_str());
     
     return DEVICE_OK;
}



int OxxLBXDPSS::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType )
{
	 std::ostringstream command;
     std::string answer;
     
     command << "dl?";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\n\r", laserStatus_);
     if (ret != DEVICE_OK) return ret;
     

     pProp->Set(laserStatus_.c_str());

     return DEVICE_OK;
}



OxxLBXLD::OxxLBXLD() :
   port_("Undefined"),
   initialized_(false),
   busy_(false),
   answerTimeoutMs_(1000)
{
     InitializeDefaultErrorMessages();
     SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

     // Name
     CreateProperty(MM::g_Keyword_Name, g_DeviceOxxLBXLDName, MM::String, true);

     // Description
     CreateProperty(MM::g_Keyword_Description, "Oxxius Laser Controller", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &OxxLBXLD::OnPort);
     CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

OxxLBXLD::~OxxLBXLD()
{
     Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Oxxius LBX Laser diode
///////////////////////////////////////////////////////////////////////////////

void OxxLBXLD::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, g_DeviceOxxLBXLDName);
}


int OxxLBXLD::Initialize()
{
	 CPropertyAction* pAct = new CPropertyAction (this, &OxxLBXLD::OnVersion);
     int nRet = CreateProperty("Soft Version", "0", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 CPropertyActionEx* pActEx = new CPropertyActionEx (this, &OxxLBXLD::OnPowerSP, 0);
     nRet = CreateProperty("PowerSetPoint", "0.00", MM::Float, false, pActEx);
     if (DEVICE_OK != nRet)
          return nRet;

	 

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnPower);
	 nRet = CreateProperty("Power", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnCurrentSP);
     nRet = CreateProperty("CurrentSetPoint", "0.00", MM::Float, false, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &OxxLBXLD::OnCurrent);
     nRet = CreateProperty("Current", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnLaserOnOff);
     nRet = CreateProperty("Laser", "Off", MM::String, false, pAct);    
	 if (DEVICE_OK != nRet)
          return nRet;

     std::vector<std::string> commands;
     commands.push_back("Off");
     commands.push_back("On");
     SetAllowedValues("Laser", commands);

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnHours);
	 nRet = CreateProperty("Hours", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
     
     pAct = new CPropertyAction (this, &OxxLBXLD::OnInterlock);
     nRet = CreateProperty("Interlock", "Interlock Open", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &OxxLBXLD::OnFault);
     nRet = CreateProperty("Fault", "No Fault", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &OxxLBXLD::OnSerialNumber);
     nRet = CreateProperty("Serial Number", "0", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnWaveLength);
	 nRet = CreateProperty("Wavelength", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnMaxPower);
	 nRet = CreateProperty("Max Power", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnMaxCurrent);
	 nRet = CreateProperty("Max Current", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
     
	 

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnBaseTemp);
     nRet = CreateProperty("Base Temperature", "0.00", MM::Float, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnControlMode);
     nRet = CreateProperty("Control mode", "APC", MM::String, false, pAct);    
	 if (DEVICE_OK != nRet)
          return nRet;
	 commands.clear();
     commands.push_back("APC");
     commands.push_back("ACC");
     SetAllowedValues("Control mode", commands);

	 pAct = new CPropertyAction (this, &OxxLBXLD::OnAnalogMod);
     nRet = CreateProperty("Analog mod", "Disable", MM::String, false, pAct);    
	 if (DEVICE_OK != nRet)
          return nRet;
	 commands.clear();
     commands.push_back("Enable");
     commands.push_back("Disable");
	 SetAllowedValues("Analog mod", commands);
	 


     
	 
	 pAct = new CPropertyAction (this, &OxxLBXLD::OnDigitalMod);
     nRet = CreateProperty("Digital mod", "Off", MM::String, false, pAct);    
	 if (DEVICE_OK != nRet)
          return nRet;
	 SetAllowedValues("Digital mod", commands);

	 
     pAct = new CPropertyAction (this, &OxxLBXLD::OnLaserStatus);
     nRet = CreateProperty("LaserStatus", "Off", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 

   
     nRet = UpdateStatus();
     if (nRet != DEVICE_OK)
          return nRet;

     //LaserOnOff(false);

     initialized_ = true;
     return DEVICE_OK;
}







int OxxLBXLD::Shutdown()
{
   if (initialized_)
   {
      LaserOnOff(false);
	 initialized_ = false;
	 
   }
   return DEVICE_OK;
}

bool OxxLBXLD::Busy()
{
   return busy_;
}

int OxxLBXLD::LaserOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0) 
     {
          command << "l 0";
          laserOn_ = "Off";
     }
     else if (onoff == 1) 
     {
          command << "l 1";
          laserOn_ = "On";
     }
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     
	 return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int OxxLBXLD::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int OxxLBXLD::OnPowerSP(MM::PropertyBase* pProp, MM::ActionType eAct, long /*index*/)
{
	std::string answer;
	std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          //pProp->Set(powerSP_);
          command << "?sp";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  pProp->Set(atof(answer.c_str()));
     }
     else if (eAct == MM::AfterSet)
     {
          
          
          
          pProp->Get(powerSP_);
          command << "p " << powerSP_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          
     }

   return DEVICE_OK;
}
int OxxLBXLD::OnCurrentSP(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	 std::string answer;
     std::ostringstream command;
          
     if (eAct == MM::BeforeGet)
     {
          //pProp->Set(currentSP_);
		  command << "?sc";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  pProp->Set(atof(answer.c_str()));
     }
     else if (eAct == MM::AfterSet)
     {
          
          pProp->Get(currentSP_);
          command << "c " << currentSP_;
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          
     }

   return DEVICE_OK;
}

int OxxLBXLD::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;
	std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          //pProp->Set(powerSP_);
          command << "?APC";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  if (answer.at(0) == '0') 
			  controlMode_ = "ACC";
		 else if (answer.at(0) == '1') 
			  controlMode_ = "APC";
		  pProp->Set(controlMode_.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          
          
          
          pProp->Get(controlMode_);
		  if(controlMode_.compare("APC")==0) 
			command << "APC 1";
		  else
			command << "ACC 1";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          
     }

   return DEVICE_OK;
}
int OxxLBXLD::OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;
	std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          
          command << "?AM";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  if (answer.at(0) == '0') 
			  analogMod_ = "Disable";
		 else if (answer.at(0) == '1') 
			  analogMod_ = "Enable";
		  pProp->Set(analogMod_.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(analogMod_);
		  if(analogMod_.compare("Enable")==0) 
			command << "AM 1";
		  else
			command << "AM 0";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          
     }
	return DEVICE_OK;
}


int OxxLBXLD::OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;
	std::ostringstream command;

     if (eAct == MM::BeforeGet)
     {
          
          command << "?TTL";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) 
			  return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) 
			  return ret;
		  if (answer.at(0) == '0') 
			  digitalMod_ = "Disable";
		 else if (answer.at(0) == '1') 
			  digitalMod_ = "Enable";
		  pProp->Set(digitalMod_.c_str());
     }
     else if (eAct == MM::AfterSet)
     {
          pProp->Get(digitalMod_);
		  if(digitalMod_.compare("Enable")==0) 
			command << "TTL 1";
		  else
			command << "TTL 0";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          
     }
	return DEVICE_OK;
}

int OxxLBXLD::OnPower(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "?p";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     
     return DEVICE_OK;
}
int OxxLBXLD::OnCurrent(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "?c";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     
     return DEVICE_OK;
}
int OxxLBXLD::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{

     std::ostringstream command;
     std::string answer; 
     laserOn_ = "Undefined";
     if (eAct == MM::BeforeGet)
     {
          command << "?l";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
          if (ret != DEVICE_OK) return ret;
          //fprintf(stderr,"Oxxius::LaserOnOff() %s\n",answer.c_str());

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
int OxxLBXLD::OnHours(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
	 std::ostringstream command;

     command << "?hh";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     
     pProp->Set(atof(answer.c_str()));

     return DEVICE_OK;
}

int OxxLBXLD::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;
	 std::string answer;
     command << "?hid";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;

	 std::string::size_type Pos = answer.find_first_of(',', 0);
		 //.find_first_not_of(delimiters, 0);
	 serialNumber_ = answer.substr(0,Pos);
     pProp->Set(serialNumber_.c_str());
     
     return DEVICE_OK;
}
int OxxLBXLD::OnInterlock(MM::PropertyBase* pProp, MM::ActionType )
{

    std::ostringstream command;
    std::string answer;

     command << "?int";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     
     if (answer.at(0) == '0') 
          interlock_ = "Interlock Open";
     else if (answer.at(0) == '1') 
          interlock_ = "Interlock Closed";

     pProp->Set(interlock_.c_str());
   
     return DEVICE_OK;
}

int OxxLBXLD::OnFault(MM::PropertyBase* pProp, MM::ActionType )
{

     std::ostringstream command;
     std::string answer;
     
     command << "?f";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     
     if (answer.at(0) == '0') 
          fault_ = "No Fault";
     else if (answer.at(0) == '1') 
          fault_ = "Diode current";
     else if (answer.at(0) == '3') 
          fault_ = "Laser power";
     else if (answer.at(0) == '4') 
          fault_ = "Diode temperature";
	 else if (answer.at(0) == '5') 
          fault_ = "Base temperature";
	 else if (answer.at(0) == '4') 
          fault_ = "Warning end of life";

     pProp->Set(fault_.c_str());

     return DEVICE_OK;
}

int OxxLBXLD::OnBaseTemp(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "?bt";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     
     return DEVICE_OK;
}

int OxxLBXLD::OnMaxCurrent(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "?maxlc";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     SetPropertyLimits("CurrentSetPoint", 0, maxCurrent_);
     return DEVICE_OK;
}
int OxxLBXLD::OnMaxPower(MM::PropertyBase* pProp, MM::ActionType )
{
     std::string answer;
     std::ostringstream command;
               
     command << "?maxlp";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     pProp->Set(atof(answer.c_str()));
     SetPropertyLimits("PowerSetPoint", 0, maxPower_);  // milliWatts
     return DEVICE_OK;
}


int OxxLBXLD::OnWaveLength(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;
	 std::string answer;
     command << "?hid";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;

	 std::string::size_type Pos = answer.find_first_of(',', 0);
		 
	 waveLength_ = answer.substr(Pos+1,std::string::npos);
     pProp->Set(waveLength_.c_str());
     
     return DEVICE_OK;
}
int OxxLBXLD::OnVersion(MM::PropertyBase* pProp, MM::ActionType)
{
     std::ostringstream command;

     command << "?sv";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
	 
     ret = GetSerialAnswer(port_.c_str(), "\r\n", softVersion_);
     if (ret != DEVICE_OK) return ret;
     
     pProp->Set(softVersion_.c_str());
     
     return DEVICE_OK;
}



int OxxLBXLD::OnLaserStatus(MM::PropertyBase* pProp, MM::ActionType )
{
	 std::ostringstream command;
     std::string answer;
     
     command << "?sta";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
     if (ret != DEVICE_OK) return ret;
     
     if (answer.at(0) == '1') 
          laserStatus_ = "Warm Up";
     else if (answer.at(0) == '2') 
          laserStatus_ = "Standby";
     else if (answer.at(0) == '3') 
          laserStatus_ = "Laser ON";
     else if (answer.at(0) == '4') 
          laserStatus_ = "Error";
	 else if (answer.at(0) == '5') 
          laserStatus_ = "Alarm";
	 else if (answer.at(0) == '4') 
          laserStatus_ = "Sleep";

     pProp->Set(laserStatus_.c_str());

     return DEVICE_OK;
}
