///////////////////////////////////////////////////////////////////////////////
// FILE:          Conex_Axis.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Newport Conex axes Driver
//                X Stage
//                Y Stage
//                Z Stage
//
// AUTHORS:       Jean_Pierre Gaillet JPG Micro-Services
// COPYRIGHT:     JPG Micro-Services Newport 2013
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifdef _MSC_VER
#pragma warning(disable: 4355)
#endif

#include "Conex_Axis.h"
#include <string>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_X_AxisDeviceName  = "XAxis";
const char* g_Y_AxisDeviceName  = "YAxis";
const char* g_Z_AxisDeviceName  = "ZAxis";

const char* g_SearchForHomeNowProp = "SearchForHome";
const char* g_SearchForHomeNowValue = "Search for HOME position now";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_X_AxisDeviceName, MM::StageDevice, "Conex_Axis X Axis");
   RegisterDevice(g_Y_AxisDeviceName, MM::StageDevice, "Conex_Axis Y Axis");
   RegisterDevice(g_Z_AxisDeviceName, MM::StageDevice, "Conex_Axis Z Axis");
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_X_AxisDeviceName)  == 0) return new X_Axis();
   if (strcmp(deviceName, g_Y_AxisDeviceName)  == 0) return new Y_Axis();
   if (strcmp(deviceName, g_Z_AxisDeviceName)  == 0) return new Z_Axis();
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Conex_AxisBase (convenience parent class)
//
Conex_AxisBase::Conex_AxisBase(MM::Device *device) :
   initialized_(false),
   port_("Undefined"),
   device_(device),
   core_(0),
   coef_(1000)
{
}

Conex_AxisBase::~Conex_AxisBase()
{
}


// Communication "clear buffer" utility function:
int Conex_AxisBase::ClearPort(void)
{
   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;                                                           
} 


// Communication "send" utility function:
int Conex_AxisBase::SendCommand(const char *command) const
{
   const char* g_TxTerm = "\r\n";
   int ret;

   std::string base_command = "";
   base_command += command;
   // send command
   ret = core_->SetSerialCommand(device_, port_.c_str(), base_command.c_str(), g_TxTerm);
   return ret;
}


// Communication "send & receive" utility function:
int Conex_AxisBase::QueryCommand(const char *command, std::string &answer) const
{
   const char* g_RxTerm = "\r\n";
   int ret;
   // send command
   ret = SendCommand(command);
   if (ret != DEVICE_OK)
      return ret;
   // block/wait for acknowledge (or until we time out)
   const size_t BUFSIZE = 2048;
   char buf[BUFSIZE] = {'\0'};
   ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, g_RxTerm);
   answer = buf;
   return ret;
}


int Conex_AxisBase::CheckDeviceStatus(void)
{
  string resp;

  int ret = ClearPort();
  if (ret != DEVICE_OK) return ret;
  // Conex_Axis Version
  ret = QueryCommand("1VE",resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;
  if (resp.find("CONEX") <=0) return DEVICE_SERIAL_COMMAND_FAILED;
  initialized_ = true;
  return DEVICE_OK;
}

double Conex_AxisBase:: GetAcceleration(void)
{
   string resp;
   float Vfloat;
   char iBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   ret = QueryCommand("1AC?", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   if (resp.length() < 4) return DEVICE_SERIAL_COMMAND_FAILED;
   strcpy(iBufString,resp.c_str()+4);
   sscanf(iBufString, "%f", &Vfloat);
   return Vfloat*coef_;
}	

double Conex_AxisBase:: GetPosition(void)
{
   string resp;
   float Vfloat;
   char iBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   ret = QueryCommand("1TP", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   if (resp.length() < 4) return DEVICE_SERIAL_COMMAND_FAILED;
   resp.erase(0,3);
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f", &Vfloat);
   return Vfloat*coef_ ;
}	

double Conex_AxisBase:: GetPositiveLimit(void)
{
   string resp;
   float Vfloat;
   char iBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   ret = QueryCommand("1SR?", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   if (resp.length() < 4) return DEVICE_SERIAL_COMMAND_FAILED;
   resp.erase(0,3);
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f", &Vfloat);
   return Vfloat*coef_;
}	


double Conex_AxisBase:: GetNegativeLimit(void)
{
   string resp;
   float Vfloat;
   char iBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   ret = QueryCommand("1SL?", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   if (resp.length() < 4) return DEVICE_SERIAL_COMMAND_FAILED;
   resp.erase(0,3);
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f", &Vfloat);
   return Vfloat*coef_;
}	

double Conex_AxisBase:: GetSpeed(void)
{
   string resp;
   float Vfloat;//=0.123;
   char iBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   ret = QueryCommand("1VA?", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   if (resp.length() < 4) return DEVICE_SERIAL_COMMAND_FAILED;
   resp.erase(0,3);
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f", &Vfloat);
   return Vfloat*coef_;
}	


int Conex_AxisBase:: SetSpeed(double speed)
{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1VA%6.6f",speed/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	


int Conex_AxisBase:: SetAcceleration(double acceleration)
{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1AC%6.6f",acceleration/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	

int Conex_AxisBase:: SetPositiveLimit(double limit)
{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1SR%6.6f",limit/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	


int Conex_AxisBase:: SetNegativeLimit(double limit)
{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1SL%6.6f",limit/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	

bool Conex_AxisBase::Moving()
{
  string resp;
  setlocale(LC_ALL,"C");
  int ret;
  
  ret = QueryCommand("1TS", resp);
  if (ret == DEVICE_OK)
      {
      string rep;
	  rep=resp.substr(7,2);
	  if ((rep=="1E") || (rep=="28"))
		  return true;
	  else
		  return false;
      }
  else  return false; 
}

bool Conex_AxisBase::Referenced()
{
  string resp;
  int ret;

  setlocale(LC_ALL,"C");
  ret = QueryCommand("1TS", resp);
  if (ret == DEVICE_OK)
      {
      string rep;
      rep=resp.substr(7,2);
	  if ((rep=="0A") || (rep=="0B")|| (rep=="0C")|| (rep=="0D")|| (rep=="0E")|| (rep=="0F")|| (rep=="10"))
		  return false;
	  else
		  return true;
      }
  else  return false;  
}

bool Conex_AxisBase::Ready()
{
  string resp;
  int ret;
  
  setlocale(LC_ALL,"C"); 
  ret = QueryCommand("1TS", resp);
  if (ret == DEVICE_OK)
      {
      string rep;
      rep=resp.substr(7,2);
	  if ((rep=="32") || (rep=="33")|| (rep=="34")|| (rep=="35")|| (rep=="36")|| (rep=="37")|| (rep=="38"))
		  return true;
	  else
		  return false;
      }
  else  return false;  
}

bool Conex_AxisBase::Enabled()
{
  string resp;
  int ret;
  
  setlocale(LC_ALL,"C");
  ret = QueryCommand("1TS", resp);
  if (ret == DEVICE_OK)
      {
      string rep;
      rep=resp.substr(7,2);
	  if ((rep=="3C") || (rep=="3D")|| (rep=="3E")|| (rep=="3F"))
		  return false;
	  else
		  return true;
      }
   else  return false;  
}

int Conex_AxisBase::Stop()
{
   int ret;

   ret = SendCommand("1ST"); //Conex_Axis abort 
   return ret;
}

int Conex_AxisBase::BaseHome()
{
   int ret;

   ret = SendCommand("1OR"); 
   return ret;
}

int Conex_AxisBase::HomeCurrentPosition()
{
   int ret;

   ret = SendCommand("1HT1"); 
   return ret;
}

int Conex_AxisBase::Enable()
{
   int ret;

   ret = SendCommand("1MM1"); 
   return ret;
}

int Conex_AxisBase::Disable()
{
   int ret;

   ret = SendCommand("1MM0"); 
   return ret;
}

int  Conex_AxisBase::MoveRelative(double position)
	{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1PR%6.6f",position/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	

int Conex_AxisBase:: MoveAbsolute( double target)
{
   char oBufString[256];
   int ret;

   setlocale(LC_ALL,"C");
   sprintf(oBufString,"1PA%6.6f",target/coef_);
   ret=SendCommand(oBufString);
   return ret;
}	

 int Conex_AxisBase:: ChangeCoef(double coef)
 {
	 coef_=coef;
	 return 1;
 }
// to test driver
void Conex_AxisBase::test()
{
	if ( !Referenced() ) 
		{
		BaseHome();
		while (Moving()) ;
	    }
	if ( !Enabled()) Enable();
	SetSpeed(0.21);
    SetAcceleration(0.43);
}

///////////////// //////////////////////////////////////////////////////////////
// X - Stage (Single axis stage
///////////////////////////////////////////////////////////////////////////////


X_Axis::X_Axis() :
   Conex_AxisBase(this)
{
   InitializeDefaultErrorMessages();
   // create pre-initialization properties
   // ------------------------------------
   // Name
   CreateProperty(MM::g_Keyword_Name, g_X_AxisDeviceName, MM::String, true);
   // Description
   CreateProperty(MM::g_Keyword_Description, "Conex X Axis driver", MM::String, true);
   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &X_Axis::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);  
}

X_Axis::~X_Axis()
{
if (initialized_)
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void X_Axis::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_X_AxisDeviceName);
}


int X_Axis::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;
   speed_=GetSpeed();
   acceleration_=GetAcceleration();
   initialized_ = true;

   ret = CreateStringProperty(g_SearchForHomeNowProp, "", false,
         new CPropertyAction(this, &X_Axis::OnSearchHomeNow));
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, "");
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, g_SearchForHomeNowValue);
   if (ret != DEVICE_OK)
      return ret;
   
/* these lines can added to test function when the axis is initialized
test();
   SetPositionUm(2.345);
   while (Moving()) ;
  
   char reponse[255];
   if (Moving())
		{
        sprintf(reponse,"V=%6.3f  A=%6.3f Moving",speed_,acceleration_);
	    }
	else
		{
        sprintf(reponse,"V=%6.3f  A=%6.3f Not moving",speed_,acceleration_);
	    }
   MessageBox(0,reponse,g_X_AxisDeviceName,0);  
*/
   return DEVICE_OK;
}

  
int X_Axis::Shutdown()
{
   initialized_    = false;
   return DEVICE_OK;
}

bool X_Axis::Busy()
{
  return Moving();
}



int X_Axis::SetPositionUm(double pos)
{
   return MoveAbsolute(pos);
}

int X_Axis::SetRelativePositionUm(double d)
{
	return MoveRelative(d);
}

int X_Axis::GetPositionUm(double& pos)
{
	pos=GetPosition();
	 return DEVICE_OK;
}
  
int X_Axis::SetPositionSteps(long)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
  
int X_Axis::GetPositionSteps(long&)
{
	  return DEVICE_UNSUPPORTED_COMMAND;
}
int X_Axis::SetOrigin()
{
 return BaseHome();
}


int X_Axis::Move(double)
{
    return DEVICE_UNSUPPORTED_COMMAND;
}


/**
 * Returns the stage position limits in um.
 */
int X_Axis::GetLimits(double& min, double& max)
{
	min=GetNegativeLimit();
	max=GetPositiveLimit();
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int X_Axis::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int X_Axis::OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set("");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string value;
      pProp->Get(value);
      if (value == g_SearchForHomeNowValue)
      {
         return BaseHome();
      }
   }
   return DEVICE_OK;
}



///////////////// //////////////////////////////////////////////////////////////
// Y - Stage (Single axis stage
///////////////////////////////////////////////////////////////////////////////


Y_Axis::Y_Axis() :
   Conex_AxisBase(this)
{
   InitializeDefaultErrorMessages();
   // create pre-initialization properties
   // ------------------------------------
   // Name
   CreateProperty(MM::g_Keyword_Name, g_Y_AxisDeviceName, MM::String, true);
   // Description
   CreateProperty(MM::g_Keyword_Description, "Conex X Axis driver", MM::String, true);
   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Y_Axis::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);  
}

Y_Axis::~Y_Axis()
{
if (initialized_)
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Y_Axis::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_Y_AxisDeviceName);
}


int Y_Axis::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;
   speed_=GetSpeed();
   acceleration_=GetAcceleration();
   initialized_ = true;

   ret = CreateStringProperty(g_SearchForHomeNowProp, "", false,
         new CPropertyAction(this, &Y_Axis::OnSearchHomeNow));
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, "");
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, g_SearchForHomeNowValue);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

  
int Y_Axis::Shutdown()
{
   initialized_    = false;
   return DEVICE_OK;
}

bool Y_Axis::Busy()
{
  return Moving();
}



int Y_Axis::SetPositionUm(double pos)
{
   return MoveAbsolute(pos);
}

int Y_Axis::SetRelativePositionUm(double d)
{
	return MoveRelative(d);
}

int Y_Axis::GetPositionUm(double& pos)
{
	pos=GetPosition();
    return DEVICE_OK;
}
  
int Y_Axis::SetPositionSteps(long)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
  
int Y_Axis::GetPositionSteps(long&)
{
	  return DEVICE_UNSUPPORTED_COMMAND;
}
int Y_Axis::SetOrigin()
{
 return BaseHome();
}


int Y_Axis::Move(double)
{
    return DEVICE_UNSUPPORTED_COMMAND;
}


/**
 * Returns the stage position limits in um.
 */
int Y_Axis::GetLimits(double& min, double& max)
{
	min=GetNegativeLimit();
	max=GetPositiveLimit();
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int Y_Axis::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int Y_Axis::OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set("");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string value;
      pProp->Get(value);
      if (value == g_SearchForHomeNowValue)
      {
         return BaseHome();
      }
   }
   return DEVICE_OK;
}


///////////////// //////////////////////////////////////////////////////////////
// Z - Stage (Single axis stage
///////////////////////////////////////////////////////////////////////////////


Z_Axis::Z_Axis() :
   Conex_AxisBase(this)
{
   InitializeDefaultErrorMessages();
   // create pre-initialization properties
   // ------------------------------------
   // Name
   CreateProperty(MM::g_Keyword_Name, g_Z_AxisDeviceName, MM::String, true);
   // Description
   CreateProperty(MM::g_Keyword_Description, "Conex X Axis driver", MM::String, true);
   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Z_Axis::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);  
}

Z_Axis::~Z_Axis()
{
if (initialized_)
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Z_Axis::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_Z_AxisDeviceName);
}


int Z_Axis::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;
   speed_=GetSpeed();
   acceleration_=GetAcceleration();
   initialized_ = true;

   ret = CreateStringProperty(g_SearchForHomeNowProp, "", false,
         new CPropertyAction(this, &Z_Axis::OnSearchHomeNow));
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, "");
   if (ret != DEVICE_OK)
      return ret;
   ret = AddAllowedValue(g_SearchForHomeNowProp, g_SearchForHomeNowValue);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

  
int Z_Axis::Shutdown()
{
   initialized_    = false;
   return DEVICE_OK;
}

bool Z_Axis::Busy()
{
  return Moving();
}



int Z_Axis::SetPositionUm(double pos)
{
    return MoveAbsolute(pos);
}

int Z_Axis::SetRelativePositionUm(double d)
{
	return MoveRelative(d);
}

int Z_Axis::GetPositionUm(double& pos)
{
	pos=GetPosition();
	return DEVICE_OK;
}
  
int Z_Axis::SetPositionSteps(long)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
  
int Z_Axis::GetPositionSteps(long&)
{
	  return DEVICE_UNSUPPORTED_COMMAND;
}
int Z_Axis::SetOrigin()
{
 return BaseHome();
}


int Z_Axis::Move(double)
{
    return DEVICE_UNSUPPORTED_COMMAND;
}


/**
 * Returns the stage position limits in um.
 */
int Z_Axis::GetLimits(double& min, double& max)
{
	min=GetNegativeLimit();
	max=GetPositiveLimit();
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int Z_Axis::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int Z_Axis::OnSearchHomeNow(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set("");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string value;
      pProp->Get(value);
      if (value == g_SearchForHomeNowValue)
      {
         return BaseHome();
      }
   }
   return DEVICE_OK;
}
