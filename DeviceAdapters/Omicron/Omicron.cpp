//-----------------------------------------------------------------------------
// FILE:          Omicron.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Omicron xX-laserseries modules through serial port
// COPYRIGHT:     Omicron Laserage Laserprodukte GmbH, 2012
// LICENSE:       LGPL
// AUTHOR:        Jan-Erik Herche, Ralf Schlotter
//-----------------------------------------------------------------------------

#include "Omicron.h"
// Code compiles on Mac without this include
// If needed on PC, use ifdefs

#ifdef WIN32
#include "winuser.h"
#endif

const char* g_DeviceoldOmicronName = "Omicron";


//-----------------------------------------------------------------------------
// Omicron device adapter
//-----------------------------------------------------------------------------

Omicron::Omicron() :
   port_("Undefined"),
   initialized_(false),
   busy_(false),
   power1_(0.00),
   power2_(0.00),
   laserOn_("On"),
   operatingmode_("Undefined"),
   fault_("No Error"),
   serialNumber_("0")

{
     InitializeDefaultErrorMessages();
     SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");

     // Description
     CreateProperty(MM::g_Keyword_Description, "Omicron Laser Controller", MM::String, true);

     // Port
     CPropertyAction* pAct = new CPropertyAction (this, &Omicron::OnPort);
     CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Omicron::~Omicron()
{
     Shutdown();
}

void Omicron::GetName(char* Name) const
{
     CDeviceUtils::CopyLimitedString(Name, g_DeviceoldOmicronName);
}

int Omicron::Initialize()
{
	 std::ostringstream command1;
	 std::ostringstream command2;
	 std::ostringstream command3;
	 std::string answer1;
	 std::string answer2;
	 std::string answer3;
	 std::stringstream help1;
	 std::stringstream help3;
	 std::vector<std::string> help2;
	 static int i;
	 
	 command2 << "?GOM";
     int ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer2);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
		  
	 answer2 = answer2.substr(4);
	 help3 << answer2;
	 help3 >> std::hex >> i;

	 i &= ~(1<<13);

	 command3 << "?SOM" << std::hex << i;
	 		
     ret = SendSerialCommand(port_.c_str(), command3.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
	 CDeviceUtils::SleepMs(1000);
     ret = GetSerialAnswer(port_.c_str(), "\r", answer3);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;  

	 command1 << "?GFw";
	 ret = SendSerialCommand(port_.c_str(), command1.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer1);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 PharseAnswerString(answer1, "!GFw", help2);

	 help1 << help2[1];
	 help1 >> device_;

	 int nRet;

	 CPropertyAction* pAct = new CPropertyAction (this, &Omicron::OnPower1);
     nRet = CreateProperty("Laser Power Set-point Select [%]", "0.00", MM::Float, false, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &Omicron::OnPower2);
     nRet = CreateProperty("Laser Power Set-point Select [mW]", "0.00", MM::Float, false, pAct);
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Omicron::OnLaserOnOff);
     CreateProperty("Laser Operation Select", "Off", MM::String, false, pAct);    

     std::vector<std::string> commands1;
     commands1.push_back("Off");
     commands1.push_back("On");
     SetAllowedValues("Laser Operation Select", commands1);

	 pAct = new CPropertyAction (this, &Omicron::OnOperatingmode);
     CreateProperty("Laser Operatingmode Select", "Undefined", MM::String, false, pAct);    

     std::vector<std::string> commands2;
     commands2.push_back("CW");
     commands2.push_back("Standby");
	 commands2.push_back("Analog Modulation");
	 if (device_ != 4)
	 {
	 commands2.push_back("Digital Modulation");
	 commands2.push_back("Analog + Digital Modulation");
	 }
     SetAllowedValues("Laser Operatingmode Select", commands2);

	 if (device_ != 3)
	 {
     pAct = new CPropertyAction (this, &Omicron::OnCWSubOperatingmode);
     CreateProperty("Laser CW Sub Operatingmode Select", "Undefined", MM::String, false, pAct);    

     std::vector<std::string> commands3;
     commands3.push_back("ACC (auto current control)");
     commands3.push_back("APC (auto power control)");
	 
     SetAllowedValues("Laser CW Sub Operatingmode Select", commands3);
	 }

	 pAct = new CPropertyAction (this, &Omicron::OnReset);
     CreateProperty("Laser Reset", "Choose 'Reset now' to reset Laser", MM::String, false, pAct);    

     std::vector<std::string> commands4;
     commands4.push_back("Reset now");
	 
     SetAllowedValues("Laser Reset", commands4);

	 pAct = new CPropertyAction (this, &Omicron::OnHours);
     nRet = CreateProperty("Workinghours [h]", "Undefined", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 
	 pAct = new CPropertyAction (this, &Omicron::OnWavelength);
     nRet = CreateProperty("Wavelength [nm]", "Undefined", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 
	 if (device_ == 3)
	 {
	 pAct = new CPropertyAction (this, &Omicron::OnDevice);
     nRet = CreateProperty("Device", "PhoxX diode laser", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 }
	 else if (device_ == 4)
	 {
	 pAct = new CPropertyAction (this, &Omicron::OnDevice);
     nRet = CreateProperty("Device", "LuxX diode laser", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 }
	 else if (device_ == 100)
	 {
	 pAct = new CPropertyAction (this, &Omicron::OnDevice);
     nRet = CreateProperty("Device", "BrixX diode laser", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
	 }
	 
	 pAct = new CPropertyAction (this, &Omicron::OnSpecPower);
     nRet = CreateProperty("Specified Power [mW]", "Undefined", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Omicron::OnFault);
     nRet = CreateProperty("Error", "No Error", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     pAct = new CPropertyAction (this, &Omicron::OnSerialNumber);
     nRet = CreateProperty("Serial Number", "Undefined", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;
     
     pAct = new CPropertyAction (this, &Omicron::OnPowerStatus);
     nRet = CreateProperty("Laser Power Status [mW]", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &Omicron::OnTemperatureDiode);
     nRet = CreateProperty("Temperature Diode [degree centigrade]", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &Omicron::OnTemperatureBaseplate);
     nRet = CreateProperty("Temperature Baseplate [degree centigrade]", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &Omicron::OnPowerSetpoint1);
     nRet = CreateProperty("Laser Power Set-point [%]", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

	 pAct = new CPropertyAction (this, &Omicron::OnPowerSetpoint2);
     nRet = CreateProperty("Laser Power Set-point [mW]", "0.00", MM::String, true, pAct);    
     if (DEVICE_OK != nRet)
          return nRet;

     nRet = UpdateStatus();
     if (nRet != DEVICE_OK)
          return nRet;

     LaserOnOff(false);

     initialized_ = true;
     return DEVICE_OK;
}

int Omicron::Shutdown()
{
   if (initialized_)
   {
		LaserOnOff(false);
		initialized_ = false;	 
   }
   return DEVICE_OK;
}

bool Omicron::Busy()
{
   return busy_;
}

//---------------------------------------------------------------------------
// Read only properties
//---------------------------------------------------------------------------

int Omicron::OnDevice(MM::PropertyBase* /* pProp */, MM::ActionType /*eAct*/)
{
     return DEVICE_OK;
}

int Omicron::OnPort(MM::PropertyBase* pProp , MM::ActionType eAct)
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

int Omicron::OnFault(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{
	 std::ostringstream command;
     std::string answer;
	 std::stringstream help;
	 int i;
	 int j;
     
     command << "?GFB";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 answer = answer.substr(4);
	 help << answer;
	 help >> std::hex >> i;

	 j = i & 1;

	 if (j == 1) fault_ = "Interlock but no failure is pending - please reset laser";
	 
	 j = (i >> 4) & 1;

	 if (j == 1) fault_ = "CDRH error";

	 j = (i >> 5) & 1;

	 if (j == 1) fault_ = "Internal communication error";

	 j = (i >> 8) & 1;

	 if (j == 1) fault_ = "Under/over-voltage error";

	 j = (i >> 9) & 1;

	 if (j == 1) fault_ = "Interlock loop is open";

	 j = (i >> 10) & 1;

	 if (j == 1) fault_ = "Diode current exceeded the maximum allowed value";

	 j = (i >> 11) & 1;

	 if (j == 1) fault_ = "Ambient temperature out of range";

	 j = (i >> 12) & 1;

	 if (j == 1) fault_ = "Diode temperature out of range";

	 j = (i >> 14) & 1;

	 if (j == 1) fault_ = "Internal error";

	 j = (i >> 15) & 1;

	 if (j == 1) fault_ = "Diode power exceeded the maximum allowed value";
	 
	 if (i == 0) fault_ = "No Error";

	 pProp->Set(fault_.c_str());

     return DEVICE_OK;
}

int Omicron::OnHours(MM::PropertyBase* pProp, MM::ActionType /*eAct*/)
{
     std::ostringstream command;

     command << "?GWH";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", hours_);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 hours_ = hours_.substr(4) + " h";

     pProp->Set(hours_.c_str());

     return DEVICE_OK;
}

int Omicron::OnPowerSetpoint1(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
	 std::stringstream help;
	 std::stringstream help2;
	 int i;
               
     command << "?GLP";
	 int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
     
	 answer = answer.substr(4);
	 help << answer;
	 help >> std::hex >> i;

	 double d = (i * 100.0 / 4095.0 );

	 help2 << std::fixed << std::setprecision(1) << d << " %";

	 pProp->Set(help2.str().c_str());
	
     return DEVICE_OK;
}

int Omicron::OnPowerSetpoint2(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
	 std::stringstream help;
	 std::stringstream help2;
	 int i;
               
     command << "?GLP";
	 int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
     
	 answer = answer.substr(4);
	 help << answer;
	 help >> std::hex >> i;

	 double d = (i * specpower / 4095.0 );

	 help2 << std::fixed << std::setprecision(1) << d << " mW";

	 pProp->Set(help2.str().c_str());
	
     return DEVICE_OK;
}

int Omicron::OnPowerStatus(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
	                
	 command << "?MDP";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
     
	 answer = answer.substr(4) + " mW";

	 pProp->Set(answer.c_str());
     
     return DEVICE_OK;
}

int Omicron::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::ostringstream command;

     command << "?GSN";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", serialNumber_);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 serialNumber_ = serialNumber_.substr(4);

     pProp->Set(serialNumber_.c_str());
     
     return DEVICE_OK;
}

int Omicron::OnSpecPower(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
    std::ostringstream command;
    std::string answer;
    std::vector<std::string> help3;
    std::stringstream help4;

     command << "?GSI";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 PharseAnswerString(answer, "!GSI", help3);

	 help4 << help3[1];

	 help4 >> specpower;

	 specpower_ = help3[1] + " mW";

     pProp->Set(specpower_.c_str());

     return DEVICE_OK;
}

int Omicron::OnTemperatureBaseplate(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
	                
	 command << "?MTA";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
     
	 answer = answer.substr(4);

	 pProp->Set(answer.c_str());
     
     return DEVICE_OK;
}

int Omicron::OnTemperatureDiode(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::string answer;
     std::ostringstream command;
	                
	 command << "?MTD";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;
     
	 answer = answer.substr(4);

	 pProp->Set(answer.c_str());
     
     return DEVICE_OK;
}

int Omicron::OnWavelength(MM::PropertyBase* pProp, MM::ActionType /* eAct */)
{
     std::ostringstream command;
	 std::string answer;
	 std::vector<std::string> help3;
	 std::stringstream help4;

     command << "?GSI";
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;

	 PharseAnswerString(answer, "!GSI", help3);

	 wavelength_ = help3[0] + " nm";

     pProp->Set(wavelength_.c_str());

     return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Action handlers
//---------------------------------------------------------------------------

int Omicron::LaserOnOff(int onoff)
{
     std::string answer;
     std::ostringstream command;

     if (onoff == 0) 
     {
          command << "?LOf";
          laserOn_ = "Off";
     }
     else if (onoff == 1) 
     {
          command << "?LOn";
          laserOn_ = "On";
     }
     int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;  

	 return DEVICE_OK;
}

int Omicron::OnCWSubOperatingmode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	 std::ostringstream command2;
	 std::ostringstream command3;
     std::string answer2;
	 std::string answer3;
	 std::stringstream help2;
	 static int i;
	 int S;
	 operatingmode_ = "Undefined";
     
	 if (eAct == MM::BeforeGet)
     {
          		  
		command2 << "?GOM";
        int ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        ret = GetSerialAnswer(port_.c_str(), "\r", answer2);
		PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;
		  
		answer2 = answer2.substr(4);
		help2 << answer2;
	    help2 >> std::hex >> i;

		S = (i >> 8) & 1;

		
		if (S == 1)
		{
			suboperatingmode_ = "APC";
		}
		else if (S == 0)
		{
			suboperatingmode_ = "ACC";
		}
		
		pProp->Set(suboperatingmode_.c_str());
     }
     
	 else if (eAct == MM::AfterSet)
     { 
          pProp->Get(answer3);
          
		   
		if (answer3 == "ACC (auto current control)") 
        {
			i &= ~(1<<8);
              
			suboperatingmode_ = "ACC";
        }
        else if (answer3 == "APC (auto power control)") 
        {
            i |= (1<<8);
			suboperatingmode_ = "APC";
        }
		
		command3 << "?SOM" << std::hex << i;
	 }
		
     int ret = SendSerialCommand(port_.c_str(), command3.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer3);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;  

     return DEVICE_OK;
}

int Omicron::OnLaserOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
     std::ostringstream command;
     std::string answer;
	 std::stringstream help;
	 int i;
     laserOn_ = "Undefined";
     if (eAct == MM::BeforeGet)
     {
          command << "?GAS";
          int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
          if (ret != DEVICE_OK) return ret;
          ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		  PurgeComPort(port_.c_str());
          if (ret != DEVICE_OK) return ret;
		  
		  answer = answer.substr(4);
		  help << answer;
	      help >> std::hex >> i;

		  i = (i >> 1) & 1;

		  if (i == 0)
               laserOn_ = "Off";
          else if (i == 1)
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

int Omicron::OnOperatingmode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	 std::ostringstream command2;
	 std::ostringstream command3;
     std::string answer2;
	 std::string answer3;
	 std::stringstream help2;
	 static int i;
	 int m;
	 int T;
	 int A;
	 operatingmode_ = "Undefined";
     
	 if (eAct == MM::BeforeGet)
     {
          		  
		command2 << "?GOM";
        int ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        ret = GetSerialAnswer(port_.c_str(), "\r", answer2);
		PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;
		  
		answer2 = answer2.substr(4);
		help2 << answer2;
	    help2 >> std::hex >> i;

		m = (i >> 4) & 1;
		T = (i >> 5) & 1;
		A = (i >> 7) & 1;

		
		if (m == 1)
		{
			if (T == 1)
			{
				if (A == 1)
				{
					operatingmode_ = "Analog + Digital Modulation";
				}
				else
				{
					operatingmode_ = "Digital Modulation";
				}
			}
			else if (A == 1)
			{
				operatingmode_ = "Analog Modulation";
			}
			else
			{
				operatingmode_ = "CW";
			}
		}
		else
		{
			operatingmode_ = "Standby";
		}

			pProp->Set(operatingmode_.c_str());
     }
     
	 else if (eAct == MM::AfterSet)
     { 
          pProp->Get(answer3);
          
		   
		if (answer3 == "Standby") 
        {
			i &= ~(1<<3);
			i &= ~(1<<4);
              
			operatingmode_ = "Standby";
        }
        else if (answer3 == "CW") 
        {
            i |= (1<<4);
			i &= ~(1<<5);
			i &= ~(1<<7);
			operatingmode_ = "CW";
        }
		else if (answer3 == "Analog Modulation") 
        {
            i |= (1<<4);
			i &= ~(1<<5);
			i |= (1<<7);
			operatingmode_ = "Analog Modulation";
        }
		else if (answer3 == "Digital Modulation") 
		{
			i |= (1<<4);
			i |= (1<<5);
			i &= ~(1<<7);
			operatingmode_ = "Digital Modulation";
		}
		else if (answer3 == "Analog + Digital Modulation") 
		{
			i |= (1<<4);
			i |= (1<<5);
			i |= (1<<7);
			operatingmode_ = "Analog + Digital Modulation";
		}

		  command3 << "?SOM" << std::hex << i;
	 }
		
     int ret = SendSerialCommand(port_.c_str(), command3.str().c_str(), "\r");
     if (ret != DEVICE_OK) return ret;
     ret = GetSerialAnswer(port_.c_str(), "\r", answer3);
	 PurgeComPort(port_.c_str());
     if (ret != DEVICE_OK) return ret;  

     return DEVICE_OK;
}

int Omicron::OnPower1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;
	std::stringstream help;
	std::ostringstream command;
	int i;

     if (eAct == MM::BeforeGet)
     {
        command << "?GLP";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		PurgeComPort(port_.c_str());
		if (ret != DEVICE_OK) return ret;

		answer = answer.substr(4);
		help << answer;
		help >> std::hex >> i;

		power1_ = (i * 100.0 / 4095.0 );
		 
		pProp->Set(power1_);
		 
     }
     else if (eAct == MM::AfterSet)
     {
          std::string answer;
          std::ostringstream command;
          
          pProp->Get(power1_);

		  if (power1_ > 100)
		  {
			  power1_ = 100;
		  }
		  else if (power1_ < 0)
		  {
			  power1_ = 0;
		  }

        i = (int) ((power1_ / 100.0 * 4095) + 0.5);
        help << std::setw(3) << std::setfill('0') << std::hex << i; 
		  
		command << "?SLP" << help.str();

        int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;
     }

   return DEVICE_OK;
}

int Omicron::OnPower2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string answer;
	std::stringstream help;
	std::ostringstream command;
	int i;

     if (eAct == MM::BeforeGet)
     {
        command << "?GLP";
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		if (ret != DEVICE_OK) return ret;
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		PurgeComPort(port_.c_str());
		if (ret != DEVICE_OK) return ret;

		answer = answer.substr(4);
		help << answer;
		help >> std::hex >> i;

		power2_ = (i * specpower / 4095.0 );
		 
		pProp->Set(power2_);
		 
     }
     else if (eAct == MM::AfterSet)
     {
          std::string answer;
          std::ostringstream command;
          
          pProp->Get(power2_);

		  if (power2_ > specpower)
		  {
			  power2_ = specpower;
		  }
		  else if (power2_ < 0)
		  {
			  power2_ = 0;
		  }

        i = (int) ((power2_ / specpower * 4095) + 0.5);
        help << std::setw(3) << std::setfill('0') << std::hex << i; 
        
		command << "?SLP" << help.str();	
          
		int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
        if (ret != DEVICE_OK) return ret;
        ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		PurgeComPort(port_.c_str());
        if (ret != DEVICE_OK) return ret;
     }

   return DEVICE_OK;
}

int Omicron::OnReset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	 std::ostringstream command;
     std::string answer;
     
	 if (eAct == MM::BeforeGet)
     {
          		  
		pProp->Set("Choose 'Reset now' to reset Laser");
     }
     
	 else if (eAct == MM::AfterSet)
     { 
          pProp->Get(answer);
          
		   
		if (answer == "Reset now") 
        {
				command << "?RsC";
	    }
	
		 int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
		 if (ret != DEVICE_OK) return ret;
		 CDeviceUtils::SleepMs(5000);
		 ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		 PurgeComPort(port_.c_str());
		 if (ret != DEVICE_OK) return ret;
       	 }

     return DEVICE_OK;
}

//---------------------------------------------------------------------------
// Help functions
//---------------------------------------------------------------------------

bool Omicron::PharseAnswerString(std::string &InputBuffer, const std::string &Kommando, std::vector<std::string> &ParameterVec)
{
    std::string Seperator = "\xA7";

    size_t StrIndex = InputBuffer.find(Kommando);
    if( StrIndex != std::string::npos )
    {
        InputBuffer = InputBuffer.substr(StrIndex + Kommando.length());  // Erase command

	    StrIndex = InputBuffer.find(Seperator);
		
		while(StrIndex != std::string::npos)
		{
			ParameterVec.push_back(InputBuffer.substr(0,StrIndex));  
	        InputBuffer.erase(0,StrIndex+1);
			StrIndex = InputBuffer.find(Seperator);
		}
		ParameterVec.push_back( InputBuffer );
		
		if( ParameterVec.size() == 1 && ParameterVec[0].compare("x") == 0)       return false;   
        return true;
    }
    else
    {
        return false;
    }
}
//---------------------------------------------------------------------------
