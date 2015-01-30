///////////////////////////////////////////////////////////////////////////////
// FILE:          PI.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
//                Nico Stuurman, nico@cmp.ucsf.edu, 3/19/2008
// COPYRIGHT:     University of California, San Francisco, 2006, 2008
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
// CVS:           $Id: Nikon.cpp 574 2007-11-07 21:00:45Z nenad $
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PI.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_PI_ZStageDeviceName = "PIZStage";
const char* g_PI_ZStageAxisName = "Axis";
const char* g_PropertyMaxUm = "MaxZ_um";
const char* g_PropertyWaitForResponse = "WaitForResponse";
const char* g_Yes = "Yes";
const char* g_No = "No";

const char* g_LocalControl = "Local: Frontpanel control";
const char* g_RemoteControl = "Remote: Interface commands mode";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_PI_ZStageDeviceName, MM::StageDevice, "PI E-662 Z-stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_PI_ZStageDeviceName) == 0)
   {
      PIZStage* s = new PIZStage();
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
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];                      
   unsigned long read = bufSize;
   int ret;                                                                   
   while (read == bufSize)                                                   
   {                                                                     
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)                               
         return ret;                                               
   }
   return DEVICE_OK;                                                           
} 
 

///////////////////////////////////////////////////////////////////////////////
// PIZStage

PIZStage::PIZStage() :
   port_("Undefined"),
   stepSizeUm_(0.1),
   initialized_(false),
   answerTimeoutMs_(1000),
   pos_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_PI_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) E-662 Adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &PIZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   CreateProperty(g_PropertyMaxUm, "500.0", MM::Float, false, 0, true);
   CreateProperty(g_PropertyWaitForResponse, g_Yes, MM::String, false, 0, true);
   AddAllowedValue(g_PropertyWaitForResponse, g_Yes);
   AddAllowedValue(g_PropertyWaitForResponse, g_No);
}

PIZStage::~PIZStage()
{
   Shutdown();
}

void PIZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_PI_ZStageDeviceName);
}

int PIZStage::Initialize()
{
   // switch on servo, otherwise "MOV" will fail
   /*
   ostringstream command;
   command << "SVO " << axisName_<<" 1";
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
      return ret;

   CDeviceUtils::SleepMs(10);
   */
   int ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

   // StepSize
   CPropertyAction* pAct = new CPropertyAction (this, &PIZStage::OnStepSizeUm);
   CreateProperty("StepSizeUm", "0.01", MM::Float, false, pAct);
   stepSizeUm_ = 0.01;

   // Remote/Local
   pAct = new CPropertyAction (this, &PIZStage::OnInterface);
   CreateProperty("Interface", "Computer", MM::String, false, pAct);
   AddAllowedValue("Interface", g_RemoteControl);
   AddAllowedValue("Interface", g_LocalControl);

   pAct = new CPropertyAction (this, &PIZStage::OnPosition);
   CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   double upperLimit = getAxisLimit();
   if (upperLimit > 0.0)
      SetPropertyLimits(MM::g_Keyword_Position, 0.0, upperLimit);
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int PIZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool PIZStage::Busy()
{
   // never busy because all commands block
   return false;
}

int PIZStage::SetPositionSteps(long steps)
{
   double pos = steps * stepSizeUm_;
   return SetPositionUm(pos);
}

int PIZStage::GetPositionSteps(long& steps)
{
   double pos;
   int ret = GetPositionUm(pos);
   if (ret != DEVICE_OK)
      return ret;
   steps = (long) ((pos / stepSizeUm_) + 0.5);
   return DEVICE_OK;
}
  
int PIZStage::SetPositionUm(double pos)
{
   ostringstream command;
   command << "POS " << axisName_<< " " << pos;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
      return ret;

   CDeviceUtils::SleepMs(10);

   if (waitForResponse())
   {

      // block/wait for acknowledge, or until we time out;
      ret = SendSerialCommand(port_.c_str(), "ERR?", "\n");
      if (ret != DEVICE_OK)
         return ret;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      int errNo = atoi(answer.c_str());
      if (errNo == 0)
	      return DEVICE_OK;

      return ERR_OFFSET + errNo;
   }
   else
   {
      pos_ = pos;
      return DEVICE_OK;
   }
}
  
int PIZStage::GetPositionUm(double& pos)
{
   if (waitForResponse())
   {
      ostringstream command;
      command << "POS? " << axisName_;

      // send command
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      string answer;
      ret = GetSerialAnswer(port_.c_str(), "\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (!GetValue(answer, pos))
         return ERR_UNRECOGNIZED_ANSWER;
   }
   else
   {
      pos = pos_;
   }

   return DEVICE_OK;
}

int PIZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int PIZStage::GetLimits(double&, double&)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int PIZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int PIZStage::OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(stepSizeUm_);
   }

   return DEVICE_OK;
}

int PIZStage::OnInterface(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {

      // block/wait for acknowledge, or until we time out;
      if (waitForResponse())
      {
         // send command
         int ret = SendSerialCommand(port_.c_str(), "DEV:CONT?", "\n");
         if (ret != DEVICE_OK)
            return ret;
         
         string answer;

         ret = GetSerialAnswer(port_.c_str(), "\n", answer);
         if (ret != DEVICE_OK)
            return ret;
         LogMessage(answer.c_str(), false);
         // NOTE: there are conflicting answers for what strings a device can
         // return as its control mode (e.g. "Remote interface command control"
         // vs. "Remote: Interface commands mode"). We should only set values
         // that match the ones we allow.
         string match = "Remote";
         if (answer.compare(match) == 0) {
             pProp->Set(g_RemoteControl);
         }
         else {
             pProp->Set(g_LocalControl);
         }
      }
   }
   else if (eAct == MM::AfterSet)
   {
      std::string mode;
      pProp->Get(mode);
      ostringstream command;
      command << "DEV:CONT ";
      if (mode.compare(g_LocalControl) == 0)
         command << "LOC";
      else
         command << "REM";
      int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int PIZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      int ret = SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;

   }

   return DEVICE_OK;
}



bool PIZStage::GetValue(string& sMessage, double& pos)
{
   // value is after last '=', if any '=' is found
   size_t p = sMessage.find_last_of('=');
   if ( p == std::string::npos )
      p=0;
   else
      p++;
   
   // trim whitspaces from right ...
   p = sMessage.find_last_not_of(" \t\r\n");
      if (p != std::string::npos)
     sMessage.erase(++p);
   
   // ... and left
   p = sMessage.find_first_not_of(" \n\t\r");
   if (p != std::string::npos)
     sMessage.erase(0,p);
   else
        return false;
   
   char *pend;
   const char* szMessage = sMessage.c_str();
   double dValue = strtod(szMessage, &pend);
   
   // return true only if scan was stopped by spaces, linefeed or the terminating NUL and if the
   // string was not empty to start with
   if (pend != szMessage)
   {
      while( *pend!='\0' && (*pend==' '||*pend=='\n')) pend++;
      if (*pend=='\0')
      {
         pos = dValue;
         return true;
      }
   }
   return false;
}

bool PIZStage::waitForResponse()
{
   return IsPropertyEqualTo(g_PropertyWaitForResponse, g_Yes);
}

double PIZStage::getAxisLimit()
{
   double limit;
   int ret = GetProperty(g_PropertyMaxUm, limit);

   if (ret == DEVICE_OK)
      return limit;
   else
      return 0.0;
}
