///////////////////////////////////////////////////////////////////////////////
// FILE:          Aladdin.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Aladdin pump controller adapter
// COPYRIGHT:     University of California, San Francisco, 2011
//
// AUTHOR:        Kurt Thorn, UCSF, November 2011
//
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
//
// CVS:           
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "../../MMDevice/MMDevice.h"
#include "Aladdin.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

// Controller
const char* g_ControllerName = "Aladdin";
const char* g_Keyword_Infuse = "Infuse";
const char * g_Keyword_Withdraw = "Withdraw";
const char * carriage_return = "\r";
const char * line_feed = "\n";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::GenericDevice, "Aladdin Syringe Pump");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create AladdinController
      AladdinController* pController = new AladdinController(g_ControllerName);
      return pController;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// AladdinController implementation
// ~~~~~~~~~~~~~~~~~~~~

AladdinController::AladdinController(const char* name) :
   initialized_(false), 
   name_(name), 
   error_(0),
   changedTime_(0.0)
{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Aladdin Syringe Pump", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();
}

AladdinController::~AladdinController()
{
   Shutdown();
}

bool AladdinController::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void AladdinController::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int AladdinController::Initialize()
{
   this->LogMessage("AladdinController::Initialize()");

   GeneratePropertyVolume();
   GeneratePropertyDiameter();
   GeneratePropertyRate();
   GeneratePropertyDirection();
   GeneratePropertyRun();
   
   CreateDefaultProgram();

   initialized_ = true;
   return HandleErrors();

}

/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void AladdinController::GeneratePropertyVolume()
{
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnVolume);
   CreateProperty("Volume-uL", "0", MM::Float, false, pAct);
}

void AladdinController::GeneratePropertyDiameter()
{
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnDiameter);
   CreateProperty("SyringeDiameter", "4.699", MM::Float, false, pAct);
}

void AladdinController::GeneratePropertyRate()
{
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnRate);
   CreateProperty("FlowRate-uL/min", "0", MM::Float, false, pAct);
}

void AladdinController::GeneratePropertyDirection()
{
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnDirection);
   CreateProperty("Direction", g_Keyword_Infuse, MM::String, false, pAct);
   AddAllowedValue("Direction", g_Keyword_Infuse);  
   AddAllowedValue("Direction", g_Keyword_Withdraw);  
}

void AladdinController::GeneratePropertyRun()
{
   CPropertyAction* pAct = new CPropertyAction (this, &AladdinController::OnRun);
   CreateProperty("Run", "0", MM::Integer, false, pAct);
   AddAllowedValue("Run", "0");  
   AddAllowedValue("Run", "1");  
}

int AladdinController::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return HandleErrors();
}



///////////////////////////////////////////////////////////////////////////////
// String utilities
///////////////////////////////////////////////////////////////////////////////


void AladdinController::StripString(string& StringToModify)
{
   if(StringToModify.empty()) return;

   size_t startIndex = StringToModify.find_first_not_of(" ");
   size_t endIndex = StringToModify.find_last_not_of(" ");
   string tempString = StringToModify;
   StringToModify.erase();

   StringToModify = tempString.substr(startIndex, (endIndex-startIndex+ 1) );
}





///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int AladdinController::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

   return HandleErrors();
}

int AladdinController::OnVolume(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   double volume;
   if (eAct == MM::BeforeGet)
   {
      GetVolume(volume);
      pProp->Set(volume);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(volume);
      SetVolume(volume);
   }   

   return HandleErrors();
}

int AladdinController::OnDiameter(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   double diameter;
   if (eAct == MM::BeforeGet)
   {
      GetDiameter(diameter);
      pProp->Set(diameter);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(diameter);
      SetDiameter(diameter);
   }   

   return HandleErrors();
}

int AladdinController::OnRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   double rate;
   if (eAct == MM::BeforeGet)
   {
      GetRate(rate);
      pProp->Set(rate);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(rate);
      SetRate(rate);
   }   

   return HandleErrors();
}

int AladdinController::OnDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   string dir;
   if (eAct == MM::BeforeGet)
   {
      GetDirection(dir);
      pProp->Set(dir.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(dir);
      SetDirection(dir);
   }   

   return HandleErrors();
}

int AladdinController::OnRun(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   long run;
   if (eAct == MM::BeforeGet)
   {
      GetRun(run);
      pProp->Set(run);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(run);
      SetRun(run);
   }   

   return HandleErrors();
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////


void AladdinController::CreateDefaultProgram()
//Set up the default program that we can run to do a simple infusion
{
   double rate;
   GetRate(rate); //Get current rate and set it in the program
   Purge();
   Send("PHN1"); //Phase 1
   ReceiveOneLine();
   stringstream msg;
   msg << "FUN RAT" << rate << "UM"; //set pumping rate in uL/min 
   Purge();
   Send(msg.str());
   ReceiveOneLine();
   Purge();
   Send("PHN2"); //Phase 2
   ReceiveOneLine();
   Purge();
   Send("FUN STP"); //Stop
   ReceiveOneLine();
   Send("PHN1"); //Set back to Phase 1
   ReceiveOneLine();
}

void AladdinController::SetVolume(double volume)
{
   volume = volume/1000; //pump volume is always set in mL
   stringstream msg;
   msg << "VOL" << volume;
   Purge();
   Send(msg.str());
   ReceiveOneLine();

}

void AladdinController::GetVolume(double& volume)
{
   stringstream msg;
   string ans;
   string units;
   msg << "VOL";
   Purge();
   Send(msg.str());
   buf_string_ = "";
   GetSerialAnswer(port_.c_str(), "L", buf_string_); //Volume string ends in UL or ML

   if (! buf_string_.empty())
       {
         volume = atof(buf_string_.substr(4,5).c_str());
		 units = buf_string_.substr(9,1).c_str();
		 if (units.compare("M") == 0)
			 volume = volume*1000; //return volume in uL
      }

}

void AladdinController::SetDiameter(double diameter)
{
   stringstream msg;
   msg << "DIA" << diameter;
   Purge();
   Send(msg.str());
   ReceiveOneLine();

}

void AladdinController::GetDiameter(double& diameter)
{
   stringstream msg;
   msg << "DIA";
   Purge();
   Send(msg.str());
   string answer = "";
   GetUnterminatedSerialAnswer(answer, 9); //awaiting 9 response characters
   if (! answer.empty())
       {
         diameter = atof(answer.substr(4,5).c_str());
      }

}

void AladdinController::SetRate(double rate)
{
   stringstream msg;
   msg << "RAT" << rate <<"UM"; //Always set rate in uL/min
   Purge();
   Send(msg.str());
   ReceiveOneLine();

}

void AladdinController::GetRate(double& rate)
{
   stringstream msg;
   msg << "RAT";
   Purge();
   Send(msg.str());
   string answer = "";
   string units;
   GetUnterminatedSerialAnswer(answer, 10); //awaiting 9 response characters
   if (! answer.empty())
       {
         rate = atof(answer.substr(4,5).c_str());
		 units = answer.substr(9,2).c_str();
      }
   //units is UM uL/min, UH uL/hr, MM mL/min, MH, mL/hr
   //we want to return it in uL/min
   if (units.compare("UH") == 0)
			 rate = rate/60; 
   if (units.compare("MH") == 0)
			 rate = (rate*1000)/60;
   if (units.compare("MM") == 0)
			 rate = rate*1000;
}

void AladdinController::SetDirection(string direction)
{
   stringstream msg;
   if (direction.compare(g_Keyword_Infuse) == 0)
   {
	   msg << "DIR INF";
	   Purge();
	   Send(msg.str());
	   ReceiveOneLine();
   }
   else if (direction.compare(g_Keyword_Withdraw) == 0)
   {
	   msg << "DIR WDR";
	   Purge();
	   Send(msg.str());
	   ReceiveOneLine();
   }

}

void AladdinController::GetDirection(string& direction)
{
   stringstream msg;
   msg << "DIR";
   Purge();
   Send(msg.str());
   string answer = "";
   GetUnterminatedSerialAnswer(answer, 6); //awaiting 9 response characters
   if (! answer.empty())
       {
         if (answer.substr(4,3).compare("INF") == 0)
			 direction = g_Keyword_Infuse;
		 else if (answer.substr(4,3).compare("WDR") == 0)
			 direction = g_Keyword_Withdraw;
      }

}

void AladdinController::GetRun(long& run)
{
   stringstream msg;
   msg << ""; //A CR will get status
   Purge();
   Send(msg.str());
   string answer = "";
   string status = "";
   GetUnterminatedSerialAnswer(answer, 4); //awaiting 9 response characters
   if (! answer.empty())
       {
         if(answer.substr(3,1).compare("I") == 0 || answer.substr(3,1).compare("W") == 0)
			run = 1;
		 else run = 0;
      }

}

void AladdinController::SetRun(long run)
{
   stringstream msg;
   if (run == 0) //Stop pumping
   {
     msg << "STP";
     Purge();
     Send(msg.str());
     ReceiveOneLine();
   }
   else if (run == 1) //Start pumping
   {
     msg << "RUN";
     Purge();
     Send(msg.str());
     ReceiveOneLine();
   }

}

int AladdinController::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}



/////////////////////////////////////
//  Communications
/////////////////////////////////////


void AladdinController::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), carriage_return);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}


void AladdinController::ReceiveOneLine()
{
   buf_string_ = "";
   GetSerialAnswer(port_.c_str(), line_feed, buf_string_);

}

void AladdinController::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

void AladdinController::GetUnterminatedSerialAnswer (std::string& ans, unsigned int count)
{
	const unsigned long MAX_BUFLEN = 20; 
	char buf[MAX_BUFLEN];
	unsigned char* cc = new unsigned char[MAX_BUFLEN];
	int ret = 0;
    unsigned long read = 0;
    MM::MMTime startTime = GetCurrentMMTime();
    // Time out of 1 sec.  Make this a property?
    MM::MMTime timeOut(1000000);
	unsigned long offset = 0;
	while((offset < count) && ( (GetCurrentMMTime() - startTime) < timeOut))
	{
		ret = ReadFromComPort(port_.c_str(), cc, MAX_BUFLEN,read);
		for(unsigned int i=0; i<read; i++)      
		{
			buf[offset+i] = cc[i];
		}
		offset += read;
	}
	ans = buf;
}
