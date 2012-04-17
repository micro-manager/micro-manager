///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
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
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "NI100X.h"
#include <string>
#include <math.h>
#include <sstream>

extern const char* g_PIGCS_ZStageDeviceName;
extern const char* g_PI_ZStageAxisName;
extern const char* g_PI_ZStageAxisLimitUm;

extern const char* g_PropertyWaitForResponse;
extern const char* g_Yes;
extern const char* g_No;
extern const char* g_DepthControl;
extern const char* g_DepthList;

using namespace std;
extern set<string> g_analogDevs;


// General utility function:
extern int ClearPort(MM::Device& device, MM::Core& core, std::string port);
 

///////////////////////////////////////////////////////////////////////////////
// PIGCSZStage

PIGCSZStage::PIGCSZStage() :
   port_("Undefined"),
   stepSizeUm_(0.1),
   initialized_(false),
   answerTimeoutMs_(1000),
   axisLimitUm_(500.0),
   depthList_(0),
   posUm_(0.0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_PIGCS_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) GCS Adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &PIGCSZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Axis name
   pAct = new CPropertyAction (this, &PIGCSZStage::OnAxisName);
   CreateProperty(g_PI_ZStageAxisName, "A", MM::String, false, pAct, true);

   // axis limit in um
   pAct = new CPropertyAction (this, &PIGCSZStage::OnAxisLimit);
   CreateProperty(g_PI_ZStageAxisLimitUm, "500.0", MM::Float, false, pAct, true);

   // TODO: This might not be neccessary and should be removed later, N.A. 2.26.10.
   // determine whether to wait for response from the stage
   CreateProperty(g_PropertyWaitForResponse, g_Yes, MM::String, false, 0, true);
   AddAllowedValue(g_PropertyWaitForResponse, g_Yes);
   AddAllowedValue(g_PropertyWaitForResponse, g_No);

}

PIGCSZStage::~PIGCSZStage()
{
   Shutdown();
}

void PIGCSZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_PIGCS_ZStageDeviceName);
}

int PIGCSZStage::Initialize()
{
	// test ismoving
	checkIsMoving_ = true;
	Busy();
   // switch on servo, otherwise "MOV" will fail
   ostringstream command;
   command << "SVO " << axisName_<<" 1";
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
      return ret;

   CDeviceUtils::SleepMs(10);
   ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

   // StepSize
   CPropertyAction* pAct = new CPropertyAction (this, &PIGCSZStage::OnStepSizeUm);
   CreateProperty("StepSizeUm", "0.01", MM::Float, false, pAct);
   stepSizeUm_ = 0.01;

   // axis limits
   pAct = new CPropertyAction (this, &PIGCSZStage::OnPosition);
   CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(MM::g_Keyword_Position, 0, axisLimitUm_);

   CreateProperty(g_DepthControl, g_No, MM::String, false);
   AddAllowedValue(g_DepthControl, g_No);
   AddAllowedValue(g_DepthControl, g_Yes);

   pAct = new CPropertyAction (this, &PIGCSZStage::OnDepthList);
   CreateProperty(g_DepthList, "0", MM::Integer, false, pAct);
   AddAllowedValue(g_DepthList, "0");
   AddAllowedValue(g_DepthList, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int PIGCSZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool PIGCSZStage::Busy()
{
   if (!waitForResponse())
      return false;

   if (!checkIsMoving_)
      return false;

   unsigned char c = (unsigned char) 5;
   // send command
   int ret = WriteToComPort(port_.c_str(), &c, 1);
   if (ret != DEVICE_OK)
      return false;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
   {
      // "#5" failed, maybe controller does not support this
      // clear error with two "ERR?"
      GetError();
      GetError();
      checkIsMoving_ = false;
      return false;
   }

   long isMoving;
   if (!GetValue(answer, isMoving))
      return false;

   return (isMoving != 0);
}

int PIGCSZStage::SetPositionSteps(long steps)
{
   double pos = steps * stepSizeUm_;
   return SetPositionUm(pos);
}

int PIGCSZStage::GetPositionSteps(long& steps)
{
   double pos;
   int ret = GetPositionUm(pos);
   if (ret != DEVICE_OK)
      return ret;
   steps = (long) ((pos / stepSizeUm_) + 0.5);
   return DEVICE_OK;
}
  
int PIGCSZStage::SetPositionUm(double pos)
{
   ostringstream command;
   command << "MOV " << axisName_<< " " << pos;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\n");
   if (ret != DEVICE_OK)
      return ret;

   CDeviceUtils::SleepMs(20);

   if (waitForResponse())
   {
      // block/wait for acknowledge, or until we time out;
      ret = GetError();
   }
   else
      ret = DEVICE_OK;

   if (ret == DEVICE_OK)
   {
      posUm_ = pos;
      // apply depth control
      if (IsPropertyEqualTo(g_DepthControl, g_Yes))
      {
         ApplyDepthControl(pos);
      }
   }
   else
   {
      ostringstream os;
      os << "2P >>>> PI GCS stage failed, Z=" << pos << " um, error=" << ret;
      LogMessage(os.str());
   }

   return ret;
}

void PIGCSZStage::ApplyDepthControl(double pos)
{
   for (set<string>::iterator it = g_analogDevs.begin(); it != g_analogDevs.end(); it++)
   {
      MM::Device* pDev = GetDevice(it->c_str());
      AnalogIO *pADev = dynamic_cast<AnalogIO*> (pDev);
      if (pADev)
      {
         ostringstream os;
         os << "2P >>>> Z stage requesting depth control, Z=" << pos << " um";
         LogMessage(os.str());
         pADev->ApplyDepthControl(pos, depthList_);
      }
   }
}

int PIGCSZStage::GetError()
{
   int ret = SendSerialCommand(port_.c_str(), "ERR?", "\n");
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

int PIGCSZStage::GetPositionUm(double& pos)
{
   if (!waitForResponse())
   {
      pos = posUm_;
      return DEVICE_OK;
   }

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

   posUm_ = pos;

   return DEVICE_OK;
}

int PIGCSZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int PIGCSZStage::GetLimits(double& min, double& max)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

bool PIGCSZStage::GetValue(string& sMessage, double& dval)
{
   if (!ExtractValue(sMessage))
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
         dval = dValue;
         return true;
      }
   }
   return false;
}

bool PIGCSZStage::GetValue(string& sMessage, long& lval)
{
   if (!ExtractValue(sMessage))
      return false;

   char *pend;
   const char* szMessage = sMessage.c_str();
   long lValue = strtol(szMessage, &pend, 0);
   
   // return true only if scan was stopped by spaces, linefeed or the terminating NUL and if the
   // string was not empty to start with
   if (pend != szMessage)
   {
      while( *pend!='\0' && (*pend==' '||*pend=='\n')) pend++;
      if (*pend=='\0')
      {
         lval = lValue;
         return true;
      }
   }
   return false;
}

bool PIGCSZStage::ExtractValue(std::string& sMessage)
{
   // value is after last '=', if any '=' is found
   size_t p = sMessage.find_last_of('=');
   if ( p != std::string::npos )
       sMessage.erase(0,p+1);
   
   // trim whitspaces from right ...
   p = sMessage.find_last_not_of(" \t\r\n");
   if (p != std::string::npos)
       sMessage.erase(++p);
   
   // ... and left
   p = sMessage.find_first_not_of(" \n\t\r");
   if (p == std::string::npos)
      return false;
   
   sMessage.erase(0,p);
   return true;
}

bool PIGCSZStage::waitForResponse()
{
   char val[MM::MaxStrLength];
   int ret = GetProperty(g_PropertyWaitForResponse, val);
   assert(ret == DEVICE_OK);
   if (strcmp(val, g_Yes) == 0)
      return true;
   else
      return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int PIGCSZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int PIGCSZStage::OnAxisName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisName_);
   }

   return DEVICE_OK;
}

int PIGCSZStage::OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int PIGCSZStage::OnAxisLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(axisLimitUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(axisLimitUm_);
   }

   return DEVICE_OK;
}

int PIGCSZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int PIGCSZStage::OnDepthList(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)depthList_);
   }
   else if (eAct == MM::AfterSet)
   {
      long lst;
      pProp->Get(lst);
      if (lst < 0 || lst > 1)
         return ERR_WRONG_DEPTH_LIST;

      depthList_ = (int)lst;

      ApplyDepthControl(posUm_); // use cached value for position
   }

   return DEVICE_OK;
}
