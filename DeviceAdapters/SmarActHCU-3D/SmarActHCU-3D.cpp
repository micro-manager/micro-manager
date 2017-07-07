///////////////////////////////////////////////////////////////////////////////
// FILE:          SmarActHCU-3D.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SmarAct HCU 3D stage, need special firmware 
//
// AUTHOR:        Joran Deschamps, EMBL, 2014 
//				  joran.deschamps@embl.de 
//
// LICENSE:       LGPL
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "SmarActHCU-3D.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>
#include <fstream>

const char* g_XYStageDeviceName = "SmaractXY";
const char* g_ZStageDeviceName = "SmaractZ";

int busy_count = 0;

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Smaract Z stage");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Smaract XY stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* s = new XYStage();
      return s;
   }
   if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      ZStage* s = new ZStage();
      return s;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                      
   unsigned long read = bufSize;
   int ret;                                                                   
   while (read == (unsigned) bufSize) 
   {                                                                     
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)                               
         return ret;                                               
   }
   return DEVICE_OK;                                                           
} 
 

///////////////////////////////////////////////////////////////////////////////
/////////////////////////////// XYStage ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
   port_("Undefined"),
   initialized_(false),
   reverseX_(1),
   reverseY_(1),
   freqXY_(5000),
   channelX_(0),
   channelY_(1),
   holdtime_(10),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   CreateProperty("X channel", "0", MM::Integer, false, 0, true);
   AddAllowedValue("X channel", "0");
   AddAllowedValue("X channel", "1");
   AddAllowedValue("X channel", "2");

   CreateProperty("X direction", "1", MM::Integer, false, 0, true);
   AddAllowedValue("X direction", "-1");
   AddAllowedValue("X direction", "1");

   CreateProperty("Y channel", "1", MM::Integer, false, 0, true);
   AddAllowedValue("Y channel", "0");
   AddAllowedValue("Y channel", "1");
   AddAllowedValue("Y channel", "2");

   CreateProperty("Y direction", "1", MM::Integer, false, 0, true);
   AddAllowedValue("Y direction", "-1");
   AddAllowedValue("Y direction", "1");

   // Name
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Smaract XYStage", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

XYStage::~XYStage()
{
   Shutdown();
}

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int XYStage::Initialize()
{
   //////////////////////////////////////////////////////////////////
   // define channel and direction
   char charbuff[MM::MaxStrLength];
   int ret = GetProperty("X direction", charbuff);
   if (ret != DEVICE_OK)
      return ret;
   reverseX_ = atoi(charbuff); 
   ret = GetProperty("X channel", charbuff);
   if (ret != DEVICE_OK)
      return ret;
   channelX_ = atoi(charbuff); 

   ret = GetProperty("Y direction", charbuff);
   if (ret != DEVICE_OK)
      return ret;
   reverseY_ = atoi(charbuff); 
   ret = GetProperty("Y channel", charbuff);
   if (ret != DEVICE_OK)
      return ret;
   channelY_ = atoi(charbuff); 

   //////////////////////////////////////////////////////////////////
   // Hold time property
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnHold);
   CreateProperty("Hold time (ms)", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Hold time (ms)", 1, 60000);
   
   // Frequency
   pAct = new CPropertyAction (this, &XYStage::OnFrequency);
   CreateProperty("Speed", "5000", MM::Integer, false, pAct);
   SetPropertyLimits("Speed", 1, 18500);

   /////////////////////////////////////////////////////////////////
   // get identification
   const char* command=":GID";		
   ret = SendSerialCommand(port_.c_str(), command, "\n");
 
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);	

   CreateProperty("ID", answer.substr(3).c_str(), MM::String, true);
   
    if (ret != DEVICE_OK)
	{
   	    return ret;
	}
   //////////////////////////////////////////////////////////////////

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool XYStage::Busy()
{
   	   string answer;
       std::stringstream command;
	   command << ":M" << channelX_;
	   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");									
	   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	   if (ret != DEVICE_OK){
		   return true;
	   }
	
	   if(strcmp(answer.substr(3).c_str(),"S") != 0){
		   return true;
	   }

	   std::stringstream command2;
	   command2 << ":M" << channelY_;
	   ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");									
	   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
	   if (ret != DEVICE_OK){
   			return true;
	   }
	
	   if(strcmp(answer.substr(3).c_str(),"S") != 0){  
		   return true;
	   }

   return false;
}

int XYStage::SetRelativePositionUm(double x, double y)
{
	int ret = 0;
	if(x != 0){
		std::stringstream command;
		command << ":MPR" << channelX_ << "P" << x*reverseX_ << "H" << holdtime_;
		ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	}
	
	if(y != 0){
		std::stringstream command2;
		command2 << ":MPR" << channelY_ << "P" << y*reverseY_ << "H" << holdtime_;
		ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
	}
   return DEVICE_OK;
}

int XYStage::SetPositionUm(double x, double y)
{
	std::stringstream command;
	command << ":MPA" << channelX_ << "P" << x*reverseX_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}

	std::stringstream command2;
    command2 << ":MPA" << channelY_ << "P" << y*reverseY_ << "H" << holdtime_;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
	if (ret != DEVICE_OK){
		return ret;
	}

   return DEVICE_OK;
}


int XYStage::SetFrequency(int freq)
{
	std::stringstream command;
	command << ":SCLF" << channelX_ << "F" << freq;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");

	std::stringstream command2;
	command2 << ":SCLF" << channelY_ << "F" << freq;
	ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");

   return DEVICE_OK;
}

int XYStage::GetPositionUm(double& x, double& y)
{	
   string answer;
   std::stringstream command;
   command << ":GP" << channelX_;
   
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");			
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK){
     return ret;
   }

   curPos_x_=atof(answer.substr(4).c_str())*reverseX_;
   
   std::stringstream command2;
   command2 << ":GP" << channelY_;
   ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");	
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK){
     return ret;
   }

   curPos_y_=atof(answer.substr(4).c_str())*reverseY_;

   x =  curPos_x_;
   y =  curPos_y_;
 
   return DEVICE_OK;
}
 
int XYStage::SetOrigin()											
{
   std::stringstream command;
   command << ":SZ" << channelX_;
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");

   std::stringstream command2;
   command2 << ":SZ" << channelY_;
   ret = SendSerialCommand(port_.c_str(), command2.str().c_str(), "\n");
   return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////
/////////////// Unsupported commands ////////////////////////////

int XYStage::SetPositionSteps(long x, long y)
{

	return DEVICE_UNSUPPORTED_COMMAND;
}
  
int XYStage::GetPositionSteps(long& x, long& y)
{

	return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)			
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::Home()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::Stop()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
    
double XYStage::GetStepSizeXUm()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

double XYStage::GetStepSizeYUm()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   { 
      pProp->Set((long)freqXY_);
   }
   else if (eAct == MM::AfterSet)
   {
	  long pos;
      pProp->Get(pos);
	  freqXY_ = pos;
	  SetFrequency(freqXY_);
   }

   return DEVICE_OK;
}

int XYStage::OnHold(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)holdtime_);
   }
   else if (eAct == MM::AfterSet)
   {
	  long pos;
      pProp->Get(pos);
	  holdtime_ = pos;
   }

   return DEVICE_OK;
}

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

///////////////////////////////////////////////////////////////////////////////
//////////////////////////////// ZStage ///////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

ZStage::ZStage() :
   port_("Undefined"),
   initialized_(false),
   reverseZ_(1),
   freqZ_(5000),
   channelZ_(2),
   holdtime_(10),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   CreateProperty("Z channel", "2", MM::Integer, false, 0, true);
   AddAllowedValue("Z channel", "0");
   AddAllowedValue("Z channel", "1");
   AddAllowedValue("Z channel", "2");

   CreateProperty("Z direction", "1", MM::Integer, false, 0, true);
   AddAllowedValue("Z direction", "-1");
   AddAllowedValue("Z direction", "1");

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);
   
   // Description
   CreateProperty(MM::g_Keyword_Description, "Smaract ZStage", MM::String, true);
	
   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
   char charbuff[MM::MaxStrLength];
   int ret = GetProperty("Z direction", charbuff);
   if (ret != DEVICE_OK)
      return ret;
   reverseZ_ = atoi(charbuff); 
   ret = GetProperty("Z channel", charbuff);
   if (ret != DEVICE_OK){
      return ret;
   }
   channelZ_ = atoi(charbuff); 

   // Frequency
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnSpeed);
   CreateProperty("Speed", "5000", MM::Integer, false, pAct);
   SetPropertyLimits("Speed", 1, 18500);
   initialized_ = true;
     
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
   string answer;   
   std::stringstream command;

   command << ":M" << channelZ_;

   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");		
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);

   if (ret != DEVICE_OK){
     return ret;
   }
   if(strcmp(answer.substr(3).c_str(),"S") != 0){
	   return true;
   }

   return false;
}

int ZStage::SetPositionUm(double pos)
{
	std::stringstream command;
	command << ":MPA" << channelZ_ << "P" << pos*reverseZ_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
      return ret;
	}
	return DEVICE_OK;
}

int ZStage::SetRelativePositionUm(double pos)
{
	std::stringstream command;
	command << ":MPR" << channelZ_ << "P" << pos*reverseZ_ << "H" << holdtime_;
	int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
	if (ret != DEVICE_OK){
      return ret;
	}
	return DEVICE_OK;
}

int ZStage::GetPositionUm(double& pos)
{
   string answer;
   std::stringstream command;
   command << ":GP" << channelZ_;

   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");	
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK){	
     return ret;
   }

   pos = atof(answer.substr(4).c_str());
   
   return DEVICE_OK;
}


int ZStage::SetOrigin()
{
   std::stringstream command;
   command << ":SZ" << channelZ_;
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
     return ret;
   return DEVICE_OK;
}

int ZStage::SetFrequency(int freq)
{
   std::stringstream command;
   command << ":SCLF" << channelZ_ << "F" << freq;
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
     return ret;

   return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////
/////////////// Unsupported commands ////////////////////////////
  
int ZStage::SetPositionSteps(long pos)
{

   return DEVICE_UNSUPPORTED_COMMAND;   
}
  
int ZStage::GetPositionSteps(long& steps)
{

   return DEVICE_UNSUPPORTED_COMMAND;
}

int ZStage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set((long)freqZ_);
   }
   else if (eAct == MM::AfterSet)
   {
	  long pos;
      pProp->Get(pos);
	  freqZ_ = pos;
	  SetFrequency(freqZ_);
   }

   return DEVICE_OK;
}

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)								
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

