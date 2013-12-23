///////////////////////////////////////////////////////////////////////////////
// FILE:          PriorLegacy.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Prior ProScan controller adapter
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
// CVS:           $Id$
//
// MODIFIED:      Michael Lin, mzlin@stanford.edu, 01/03/2012
//                Changed XYStage AND ZSTAGE commands to use the older H127/H128 "Legacy" protocol.
//                H129 or later stage controller may still work with this library
//                in reduced functionality if it has been set to compatibility mode on, 
//                but full functionality including synchronization and resolution
//                calibration will require use of the standard Prior.cpp library

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "PriorLegacy.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_LegacyXYStageDeviceName = "XYStage"; // LIN 01/01/2012 CHANGED FROM XYStage
const char* g_LegacyZStageDeviceName = "ZStage"; // LIN 01/01/2012 CHANGED FROM ZStage

//using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_LegacyZStageDeviceName, MM::StageDevice, "Legacy Z stage"); // LIN 01/01/2012 CHANGED
   RegisterDevice(g_LegacyXYStageDeviceName, MM::XYStageDevice, "Legacy XY Stage"); // LIN 01/01/2012 CHANGED
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_LegacyZStageDeviceName) == 0) // LIN 01/01/2012 CHANGED
   {
      ZStage* s = new ZStage(); // LIN 01/01/2012 DID NOT CHANGE NAME. ASSUMING ZStage IS FIXED DEVICE NAME MMCORE WILL CALL ON.
      return s;
   }
   else if (strcmp(deviceName, g_LegacyXYStageDeviceName) == 0)
   {
      XYStage* s = new XYStage();  // LIN 01/01/2012 DID NOT CHANGE NAME. ASSUMING XYStage IS FIXED DEVICE NAME MMCORE WILL CALL ON.
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
// XYStageH128
//
XYStage::XYStage() : // LIN 01/01/2012 DIDN'T RENAME FUNCTION. ASSUMING MMCORE WILL EXPECT TO CALL ON XYStage()
   CXYStageBase<XYStage>(),
   initialized_(false), 
   port_("Undefined"), 
   stepSizeXUm_(0.1), // LIN 01/02/2012 changed initial value from 0.0 to 0.1
   stepSizeYUm_(0.1), // LIN 01/02/2012 changed initial value from 0.0 to 0.1
   answerTimeoutMs_(1000),
   originX_(0),
   originY_(0),
   mirrorX_(false),
   mirrorY_(false),
   busy_(false) // LIN 01/01/2012 ADDED
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   // CreateProperty(MM::g_Keyword_Name, g_LegacyXYStageDeviceName, MM::String, true); // LIN 01/01/2012 RENAMED

   // Description 
   CreateProperty(MM::g_Keyword_Description, "Prior H128 XY stage driver adapter", MM::String, true); // LIN 01/01/2012 RENAMED
   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay(); // LIN 01-01-2012 ADDED SO WE CAN SET DELAY INSTEAD OF QUERYING H128 BUSY STATUS

}

XYStage::~XYStage() // LIN 01/01/2012 DIDN'T RENAME FUNCTION. ASSUMING MMCORE WILL EXPECT TO CALL ON ~XYStage()
{
   Shutdown();
}

void XYStage::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LegacyXYStageDeviceName); // LIN 01/01/2012 RENAMED
}

int XYStage::Initialize()
{
   
   // LIN 01-01-2012 H128 DOES NOT HAVE ADJUSTABLE RESOLUTIONS
   // LIN 01-02-2012 STEP SIZE ALREADY SET IN XYSTAGE::XYSTAGE() FUNCTION INITIALIZER LIST TO 0.1

   // Max Speed
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnMaxSpeed);
   CreateProperty("MaxSpeed", "20", MM::Integer, false, pAct); 
   SetPropertyLimits("MaxSpeed", 1, 100); // LIN 01-01-2012 H128 MAX SPEED IS 100 NOT 250

   // Acceleration
   pAct = new CPropertyAction (this, &XYStage::OnAcceleration);
   CreateProperty("Acceleration", "20", MM::Integer, false, pAct);
   SetPropertyLimits("Acceleration", 1, 100); // LIN 01-01-2012 H128 MAX ACCELERATION IS 100 NOT 150

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

bool XYStage::Busy()
{
// LIN 01-01-2012 H128 USES # COMMAND TO QUERY BUSY BUT ANSWER DEPENDS ON DEVICES INSTALLED AND IS THUS DIFFICULT TO PARSE, INSTEAD SIMPLY RETURN FALSE
   return false;
}
 
int XYStage::SetPositionSteps(long x, long y)
{
   MMThreadGuard guard(lock_);

   // First Clear serial port from previous stuff
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream command;
   command << "G," << x << "," << y;

   // send command
   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("R") == 0)
   {
      return DEVICE_OK; // LIN 01-03-2012 note "R" is the correct response from H128
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}
 

int XYStage::SetRelativePositionSteps(long /*x*/, long /*y*/)
// LIN 01-01-2012 H128 DOES NOT HAVE GR COMMAND, INSTEAD JUST RETURN DEVICE_UNSUPPORTED_COMMAND
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetPositionSteps(long& x, long& y)
// LIN 01-01-2012 NOTE function takes x and y variables passed to it by reference and revises them
{
   int ret = GetPositionStepsSingle('X', x);
   if (ret != DEVICE_OK)
      return ret; 
// LIN 01-01-2012 NOTE on above line, if error message was sent by GetPositionStepSingle it will get passed back as output of this subroutine and the routine will end and won't get to next step
// LIN 01-01-2012 NOTE if no error message nothing will happen above (but x already modified in GetPositionStepSingle because it is being passed as reference variable)
   return GetPositionStepsSingle('Y', y);
// LIN 01-01-2012 NOTE on above line, either 0 meaning okay or non-zero error code from GetPositionStepSingle will get passed back as output to this routine. y value may have been modified in GetPositionStepSingle

}
 
int XYStage::Home()
// LIN 01-01-2012 H128 DOES NOT HAVE GO TO LIMITS FUNCTION, INSTEAD JUST RETURN DEVICE_UNSUPPORTED_COMMAND
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


int XYStage::Stop()
// LIN 01-01-2012 H128 USES I INSTEAD OF K AS COMMAND FOR STOP
{
   MMThreadGuard guard(lock_);
   int ret = SendSerialCommand(port_.c_str(), "I", "\r"); // LIN 01-01-2012 CHANGED COMMAND FOR STOP FROM K TO I
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("R") == 0) // LIN 01-03-2012 note "R" is the correct response from H128
   {
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int XYStage::SetOrigin()
// LIN 01-01-2012 H128 USES Z INSTEAD OF PS TO SET ORIGIN AND IT DOES IT FOR ALL 3 AXES
{
   MMThreadGuard guard(lock_);

   // First Clear serial port from previous stuff
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   // send command
   ret = SendSerialCommand(port_.c_str(), "Z", "\r"); // LIN 01-01-2012 CHANGED COMMAND FOR ORIGIN FROM PS TO Z
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("0") == 0)
   {
      return DEVICE_OK; // LIN 01-03-2012 note "0" is the correct response from H128
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
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

/**
 * Gets and sets the maximum speed with which the Prior stage travels
 */
int XYStage::OnMaxSpeed(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   if (eAct == MM::BeforeGet) 
   {
      // send command
      ret = SendSerialCommand(port_.c_str(), "SMX", "\r"); // LIN 01-01-2012 H128 USES SMX INSTEAD OF SMS
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      int maxSpeed = atoi(answer.c_str());
      if (maxSpeed < 1 || maxSpeed > 100) // LIN 01-01-2012 H128 MAX SPEED IS 100 not 250
         return  ERR_UNRECOGNIZED_ANSWER;

      pProp->Set((long)maxSpeed);

   } 
   else if (eAct == MM::AfterSet) 
   {
      long maxSpeed;
      pProp->Get(maxSpeed);

      std::ostringstream os;
       os << "SMX," <<  maxSpeed; // LIN 01-01-2012 H128 USES SMX,maxSpeed INSTEAD OF SMS,maxSpeed

      // send command
      ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r"); 
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,1).compare("0") == 0)
      {
         return DEVICE_OK; // LIN 01-03-2012 note "0" is the correct response from H128
      }
      else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
      {
         int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;

   }

   return DEVICE_OK;
}


/**
 * Gets and sets the Acceleration of the Prior stage travels
 */
int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   if (eAct == MM::BeforeGet) 
   {
      // send command
     ret = SendSerialCommand(port_.c_str(), "SRX", "\r"); // LIN 01-01-2012 H128 USES SRX INSTEAD OF SAS
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      int acceleration = atoi(answer.c_str());
      if (acceleration < 1 || acceleration > 100) // LIN 01-01-2012 H128 ACCELERATION VALUE MAX 100 NOT 150
         return  ERR_UNRECOGNIZED_ANSWER;

      pProp->Set((long)acceleration);

   } 
   else if (eAct == MM::AfterSet) 
   {
      long acceleration;
      pProp->Get(acceleration);

      std::ostringstream os;
      os << "SRX," <<  acceleration; // LIN 01-01-2012 H128 USES SRX,acceleration INSTEAD OF SAS,acceleration

      // send command
      ret = SendSerialCommand(port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      // block/wait for acknowledge, or until we time out;
      std::string answer;
      ret = GetSerialAnswer(port_.c_str(), "\r", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer.substr(0,1).compare("0") == 0)
      {
         return DEVICE_OK; // LIN 01-03-2012 note "0" is the correct response from H128
      }
      else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
      {
         int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
         return ERR_OFFSET + errNo;
      }
      return ERR_UNRECOGNIZED_ANSWER;

   }

   return DEVICE_OK;
}


int XYStage::GetPositionStepsSingle(char axis, long& steps) 
// LIN 01-01-2012 NOTE this function is called by function GetPositionSteps(long& x, long& y) where long& x is a variable x of type long, passed here by reference with modification rights
{
   // First Clear serial port from previous stuff
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream command;
   command << "P" << axis; //LIN assembles command as either PX or PY depending on whether X or Y is being asked by GetPositionSteps

   // send command
   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      steps = atol(answer.c_str()); // LIN 01-03-2012 this is where x or y is updated, atol converts string to long integer
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
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000),
   busy_(false) // LIN 01-03-2012 ADDED
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_LegacyZStageDeviceName, MM::String, true); // LIN 01-03-2012 CHANGED NAME

   // Description
   CreateProperty(MM::g_Keyword_Description, "Prior Legacy Z-stage driver adapter", MM::String, true); // LIN 01-03-2012 CHANGED NAME

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
   CDeviceUtils::CopyLimitedString(Name, g_LegacyZStageDeviceName); // LIN 01-03-2012 CHANGED NAME
}

int ZStage::Initialize()
{
   int ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

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

bool ZStage::Busy()
// LIN 2012-01-03 JUST SET TO FALSE
{
   return false;
}

int ZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   return SetPositionSteps(steps);
}

int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
   // First Clear serial port from previous stuff
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   long delta = pos - curSteps_;

   // LIN 01-03-2012 ADDED C COMMAND BELOW. H128 USES C TO SET # STEPS THEN DOES U OR D WITHOUT ARGUMENTS
   std::ostringstream command;
   if (delta >= 0)
      command << "C," << delta; 
   else
      command << "C," << -delta;

   // send command
   ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
   }

   // LIN 01-03-2012 DIRECTLY SEND "U" OR "D" AS APPROPRIATE
   if (delta >= 0)
      ret = SendSerialCommand(port_.c_str(), "U", "\r");
   else
      ret = SendSerialCommand(port_.c_str(), "D", "\r");

   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0,1).compare("R") == 0)
   {
      curSteps_ = pos;
      return DEVICE_OK;
   }
   else if (answer.substr(0, 1).compare("E") == 0 && answer.length() > 2) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted, atol converts string to short integer
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}
  
int ZStage::GetPositionSteps(long& steps)
{
   // First Clear serial port from previous stuff
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   const char* command="PZ"; // LIN 01-03-2012 SAME COMMAND FOR H128 AS NEWER CONTROLLERS

   // send command
   ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
   {
      // failed reading the port
      return ret;
   }

   if (answer.length() > 2 && answer.substr(0, 1).compare("E") == 0) // LIN 01-03-2012 note I don't think the H128 reports E## errors but we can leave this in
   {
      int errNo = atoi(answer.substr(2).c_str()); // LIN 01-03-2012 note here the error number is extracted
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 0)
   {
      steps = atol(answer.c_str());
      curSteps_ = steps; // LIN 01-03-2012 note curSteps was passed to this function by reference and here gets modified to the reported steps number
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::SetOrigin()
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
