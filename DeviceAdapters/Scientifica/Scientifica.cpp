///////////////////////////////////////////////////////////////////////////////
// FILE:          Scientifica.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Scientifica XYZStage adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/01/2006
//
//				  Scientifica Specific Parts
// AUTHOR:		  Matthew Player (ELECSOFT)
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Scientifica.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName = "ZStage";

//using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Z Stage");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY Stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
   {
      return 0;
   }
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* s = new XYStage();
      return s;
   }
   else if (strcmp(deviceName, g_ZStageDeviceName) == 0)
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

int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// XYStage
//
XYStage::XYStage() :
   CXYStageBase<XYStage>(),
   initialized_(false), 
   port_("Undefined"), 
   stepSizeXUm_(0.1), 
   stepSizeYUm_(0.1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   
   // Name
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Scientifica XY Stage Driver Adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

XYStage::~XYStage()
{
   Shutdown();
}

void XYStage::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}
//Tells micromanager the step size, top speed and acceleration. 
//Speed and acceleration ranges does not refer to a meaningful unit but
//the range that the hardware uses.
int XYStage::Initialize()
{
   // Step size
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   CreateProperty("StepSizeX_um", "0.1", MM::Float, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   CreateProperty("StepSizeY_um", "0.1", MM::Float, true, pAct);

   // Max Speed
   pAct = new CPropertyAction (this, &XYStage::OnMaxSpeed);
   CreateProperty("MaxSpeed", "30000", MM::Integer, false, pAct);
   SetPropertyLimits("MaxSpeed", 1000, 50000);

   // Acceleration
   pAct = new CPropertyAction (this, &XYStage::OnAcceleration);
   CreateProperty("Acceleration", "500", MM::Integer, false, pAct);
   SetPropertyLimits("Acceleration", 1, 1000);
   
   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

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

// Checks if stage is busy, returns true if the stage is curently moving false otherwise 
// N.B. This will be effected by ZStage movement
bool XYStage::Busy()
{
   MMThreadGuard guard(lock_);

   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return false; 

   const char* command = "s";

   ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return false;

   std::string answer="";
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return false;
   
   if (answer.length() >=1)
   {
      int status = atoi(answer.substr(0,1).c_str());
      if (status==0) 
         return false;
      else 
         return true;
   }

   return false;
}
 
//Moves stage to given absolute xy coordinates
int XYStage::SetPositionSteps(long x, long y)
{
   MMThreadGuard guard(lock_);

   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream command;
   command << "abs " << x << " " << y;

   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
      return DEVICE_OK;
   }
   return ERR_UNRECOGNIZED_ANSWER;   
}

//Moves stage relative to current position by given number of steps
int XYStage::SetRelativePositionSteps(long x, long y)
{
   MMThreadGuard guard(lock_);

   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream command;
   command << "rel " << x << " " << y;

   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
      return DEVICE_OK;
   }
   return ERR_UNRECOGNIZED_ANSWER;   
}

// reports back the coordinates of the device
int XYStage::GetPositionSteps(long& x, long& y)
{
   int ret = GetPositionStepsSingle('X', x);
   if (ret != DEVICE_OK)
      return ret;

   return GetPositionStepsSingle('Y', y);
}

//Sends to corner and sets them postions to 0
int XYStage::Home()
{
   MMThreadGuard guard(lock_);

   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

	// move to corner
   ret = SendSerialCommand(port_.c_str(), "vj 30000 30000 0", "\r");
    if (ret != DEVICE_OK)
      return ret;
	
	std::string answer;
    ret = GetSerialAnswer(port_.c_str(), "\r", answer);
    if (ret != DEVICE_OK)
       return ret;

    if (answer.substr(0,1).compare("A") == 0)
    {
		//set x postion to 0
	    ret = SendSerialCommand(port_.c_str(), "px = 0", "\r");
		if (ret != DEVICE_OK)
		   return ret;

		//set y postion to 0
	    ret = SendSerialCommand(port_.c_str(), "py = 0", "\r");
		if (ret != DEVICE_OK)
		   return ret;

	    std::string answer;
	    ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	    if (ret != DEVICE_OK)
		   return ret;

	    if (answer.substr(0,1).compare("A") == 0)
	    {
		  return DEVICE_OK;
        }
	}
   return ERR_UNRECOGNIZED_ANSWER;
}

//Stops the current motion. N.B. This will effect ZStage movement
int XYStage::Stop()
{
   MMThreadGuard guard(lock_);

   int ret = SendSerialCommand(port_.c_str(), "STOP", "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
      return DEVICE_OK;
   }
   return ERR_UNRECOGNIZED_ANSWER;
}

//Zeros XY position
int XYStage::SetOrigin()
{
   MMThreadGuard guard(lock_);

   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   ret = SendSerialCommand(port_.c_str(), "PX 0", "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
		ret = ClearPort(*this, *GetCoreCallback(), port_);
	   if (ret != DEVICE_OK)
		  return ret;

	   ret = SendSerialCommand(port_.c_str(), "PY 0", "\r");
	   if (ret != DEVICE_OK)
		  return ret;

	   std::string answer2;
	   ret = GetSerialAnswer(port_.c_str(), "\r", answer2);
	   if (ret != DEVICE_OK)
		  return ret;

	   if (answer2.substr(0,1).compare("A") == 0)
	   {
		  return DEVICE_OK;
	   }
   }
   return ERR_UNRECOGNIZED_ANSWER; 
}
 
int XYStage::GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

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

int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeXUm_);
   }

   return DEVICE_OK;
}

int XYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeYUm_);
   }

   return DEVICE_OK;
}

//Gets and sets the maximum speed with which the stage travels
int XYStage::OnMaxSpeed(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   if (eAct == MM::BeforeGet) 
   {
      ret = SendSerialCommand(port_.c_str(), "TOP", "\r");
      if (ret != DEVICE_OK)
         return ret;

      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      int maxSpeed = atoi(answer.c_str());
      
      pProp->Set((long)maxSpeed);

   } 
   else if (eAct == MM::AfterSet) 
   {
      long maxSpeed;
      pProp->Get(maxSpeed);

      std::ostringstream os;
      os << "TOP " <<  maxSpeed;

      ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,1).compare("A") == 0)
      {
         return DEVICE_OK;
      }
      return ERR_UNRECOGNIZED_ANSWER;

   }

   return DEVICE_OK;
}


//Gets and sets the Acceleration of the stage travels
int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   if (eAct == MM::BeforeGet) 
   {
      ret = SendSerialCommand(port_.c_str(), "ACC", "\r");
      if (ret != DEVICE_OK)
         return ret;

      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      int acceleration = atoi(answer.c_str());

      pProp->Set((long)acceleration);

   } 
   else if (eAct == MM::AfterSet) 
   {
      long acceleration;
      pProp->Get(acceleration);

      std::ostringstream os;
      os << "ACC " <<  acceleration;

      ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,1).compare("A") == 0)
      {
         return DEVICE_OK;
      }
      return ERR_UNRECOGNIZED_ANSWER;

   }

   return DEVICE_OK;
}

// XYStage utility functions
int XYStage::GetPositionStepsSingle(char axis, long& steps)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream command;
   command << "P" << axis;

   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   if (answer.length() > 0)
   {
      steps = atol(answer.c_str());
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage() :
   initialized_(false),
   port_("Undefined"),
   stepSizeUm_(0.1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Scientifica Z stage Driver Adapter", MM::String, true);

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
   int ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSizeUm);
   CreateProperty("StepSizeUm", "0.1", MM::Float, false, pAct);
   stepSizeUm_ = 0.1;
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

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

// Checks if stage is busy, returns true if the stage is curently moving false otherwise
// This is effected by XYStage movement
bool ZStage::Busy()
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return false; 

   const char* command = "s";

   ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return false;

   std::string answer="";
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return false;
   
   if (answer.length() >=1)
   {
      int status = atoi(answer.substr(0,1).c_str());
      if (status==0) 
         return false;
      else 
         return true;
   }

   return false;
}

//Moves Stage to absolute position
int ZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   return SetPositionSteps(steps);
}

//Reports postion of stage in um
int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}
  
//Moves stage to given absolute z coordinate
int ZStage::SetPositionSteps(long pos)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream command;
   command << "absz " << pos;

   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
      curSteps_ = pos;
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}

//Reports coordinate the stage is at
int ZStage::GetPositionSteps(long& steps)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   const char* command="PZ";

   ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      steps = atol(answer.c_str());
      curSteps_ = steps;
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

//Zeros z position
int ZStage::SetOrigin()
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   ret = SendSerialCommand(port_.c_str(), "pz 0 ", "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("A") == 0)
   {
      return DEVICE_OK;
   }
   return ERR_UNRECOGNIZED_ANSWER; 
}
int ZStage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

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
int ZStage::OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }

   return DEVICE_OK;
}
