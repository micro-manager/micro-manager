///////////////////////////////////////////////////////////////////////////////
// FILE:          Ludl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The implementation of the Ludl device adapter.
//                Should also work with ASI devices.
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/27/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
//                CVS: $Id$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Ludl.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

// XYStage
const char* g_XYStageDeviceName = "XYStage";

// single axis stage
const char* g_StageDeviceName = "Stage";

// generic ASI controller
const char* g_ASIControllerName = "ASIController";

// property names
const char* g_CommandSet = "CommandSet";
const char* g_HighLevel = "high-level";
const char* g_LowLevel = "low-level";

// global contoller variable
bool g_HLCommandSet = false;


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * Declare which devices are available in this library.
 * Use this method for other global initializations if needed.
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_StageDeviceName, "Single axis stage");
   AddAvailableDeviceName(g_XYStageDeviceName, "XY stage");
   AddAvailableDeviceName(g_ASIControllerName, "Generalized serial adapter for all ASI devices");
}

/**
 * Entry point for creating devices.
 * Based on the request by device name, the device is created and the pointer returned.
 */
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* pXYStage = new XYStage();
      return pXYStage;
   }
   else if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      Stage* pStage = new Stage();
      return pStage;
   }
   else if (strcmp(deviceName, g_ASIControllerName) == 0)
   {
      ASIController* pASI = new ASIController();
      return pASI;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// XYStage
//

/**
 * XYStage - two axis stage device.
 */
XYStage::XYStage() :
   initialized_(false), port_("Undefined"), stepSizeUm_(0.1), answerTimeoutMs_(1000), idX_(1), idY_(2)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Ludl XY stage driver adapter", MM::String, true);

   // Port, read-write (RW)
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // device ID for the X stage, RW
   pAct = new CPropertyAction (this, &XYStage::OnIDX);
   CreateProperty("IDX", "1", MM::Integer, false, pAct, true);

   // device ID for the Y stage, RW
   pAct = new CPropertyAction (this, &XYStage::OnIDY);
   CreateProperty("IDY", "2", MM::Integer, false, pAct, true);

   // define command set
   pAct = new CPropertyAction (this, &XYStage::OnCommandSet);
   CreateProperty(g_CommandSet, g_LowLevel, MM::String, false, pAct, true);

   // limit the range of values for the command set
   AddAllowedValue(g_CommandSet, g_LowLevel);
   AddAllowedValue(g_CommandSet, g_HighLevel);
}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   const unsigned cmdLen = 2;
   char cmd[cmdLen];
   if (g_HLCommandSet)
   {
      // high level commands
      cmd[0] = (char)0xFF;
      cmd[1] = (char)65;
   }
   else
   {
      // low level commands
      cmd[0] = (char)0xFF;
      cmd[1] = (char)66;
   }

   ret = WriteToComPort(port_.c_str(), cmd, cmdLen);
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

/**
 * Returns true if either of the axis (X or Y) are still moving.
 */
bool XYStage::Busy()
{
   if (g_HLCommandSet)
   {
      // format the command
      const char* cmd = "STATUS X Y\r";

      // send command
      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, (unsigned)strlen(cmd)))
         return false; // can't write so say we're not busy

      char status=0;
      unsigned long read=0;
      int numTries=0;
      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), &status, 1, read))
            return false;
         numTries++;
         #ifdef WIN32
         Sleep(5);
         #endif
      }
      while(read==0 && numTries < 3);

      if (status == 'B')
         return true;
      else
         return false;
   }
   else
   {
      // check X stage first
      const unsigned cmdLen = 3;
      char cmd[cmdLen];
      cmd[0] = idX_; // X
      cmd[1] = 63; // status
      cmd[2] = 58;  // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, cmdLen))
         return false;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      char reply;
      unsigned long read=0;
      unsigned long startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), &reply, 1, read))
            return false;
      }
      while(read==0 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (read == 0)
         return false; // we timed out, but we'll claim not busy

      if (reply == 66)
         return true; // busy

      // then check Y stage
      cmd[0] = idY_; // X

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, cmdLen))
         return false;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), &reply, 1, read))
            return false;
      }
      while(read==0 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (read == 0)
         return false; // we timed out, but we'll claim not busy

      if (reply == 66)
         return true; // busy
      else
         return false;
   }
}

/**
 * Sets position in um.
 */
int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = (long) (x / stepSizeUm_ + 0.5);
   long ySteps = (long) (y / stepSizeUm_ + 0.5);
   
   return SetPositionSteps(xSteps, ySteps);
}

/**
 * Gets current position in um.
 */
int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   x = xSteps * stepSizeUm_;
   y = ySteps * stepSizeUm_;

   return DEVICE_OK;
}

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   if (g_HLCommandSet)
   {
      // format the command
      ostringstream cmd;
      cmd << "HERE ";
      cmd << "X=" << x << " ";
      cmd << "Y=" << y << "\r";

      // TODO: what if we are busy???

      string resp;
      int ret = ExecuteCommand(cmd.str(), resp);
      if (ret != DEVICE_OK)
         return ret;

      // parse the answer and return the proper error code
      // TODO...

      istringstream is(resp);
      string outcome;
      is >> outcome;

      if (outcome.compare(":A") == 0)
         return DEVICE_OK; // success!

      // figure out the error code
      int code;
      is >> code;
      return code;
   }
   else
   {
      PurgeComPort(port_.c_str());

      // X axis
      const unsigned cmdLen = 7;
      unsigned char cmdx[cmdLen];
      cmdx[0] = idX_; // X
      cmdx[1] = 84; // move
      cmdx[2] = 3;  // data length
      cmdx[3] = (unsigned char)x; // lsb
      cmdx[4] = (unsigned char)(x >> 8); // mb
      cmdx[5] = (unsigned char)(x >> 16); // lsb
      cmdx[6] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), (char*)cmdx, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // move command
      char cmdm[3];
      cmdm[0] = idX_; // x
      cmdm[1] = 71; // move
      cmdm[2] = 58;
      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdm, 3))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // Y axis
      unsigned char cmdy[cmdLen];
      cmdy[0] = idY_; // Y
      cmdy[1] = 84; // move
      cmdy[2] = 3;  // data length
      cmdy[3] = (unsigned char) y; // lsb
      cmdy[4] = (unsigned char)(y >> 8); // mb
      cmdy[5] = (unsigned char)(y >> 16); // lsb
      cmdy[6] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), (char*)cmdy, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      cmdm[0] = idY_; // y
      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdm, 3))
         return DEVICE_SERIAL_COMMAND_FAILED;

      return DEVICE_OK;
   }
}
  
/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   PurgeComPort(port_.c_str());
   
   if (g_HLCommandSet)
   {
      // format the command
      static const string cmd = "WHERE X Y\r";

      // TODO: what if we are busy???

      string resp;
      int ret = ExecuteCommand(cmd, resp);
      if (ret != DEVICE_OK)
         return ret;

      // parse the answer and return the proper error code
      // TODO...

      istringstream is(resp);
      string outcome;
      is >> outcome;

      if (outcome.compare(":A") == 0)
      {
         is >> x;
         is >> y;
         return DEVICE_OK; // success!
      }

      // figure out the error code
      int code;
      is >> code;
      return code;
   }
   else
   {
      // X axis
      const unsigned cmdLen = 4;
      char cmdx[cmdLen];
      cmdx[0] = idX_; // X
      cmdx[1] = 97; // read pos
      cmdx[2] = 3;  // data length
      cmdx[3] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdx, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      const long replyLen = 3;
      char reply[3];
      unsigned curIdx = 0;
      unsigned long read;
      unsigned long startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), reply + curIdx, 1, read))
            return DEVICE_SERIAL_COMMAND_FAILED;
         curIdx += read;
      }
      while(curIdx < 3 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (curIdx != 3)
         return DEVICE_SERIAL_TIMEOUT;

      x = (unsigned char)reply[0] + 256 * (unsigned char)reply[1]  + 256 * 256 * (unsigned char)reply[2];

      // Y axis
      char cmdy[cmdLen];
      cmdy[0] = idY_; // Y
      cmdy[1] = 97; // read pos
      cmdy[2] = 3;  // data length
      cmdy[3] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdy, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      curIdx = 0;
      startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), reply + curIdx, 1, read))
            return DEVICE_SERIAL_COMMAND_FAILED;
         curIdx += read;
      }
      while(curIdx < 3 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (curIdx != 3)
         return DEVICE_SERIAL_TIMEOUT;

      y = (unsigned char)reply[0] + 256 * (unsigned char)reply[1] + 256 * 256 * (unsigned char)reply[2];

      return DEVICE_OK;
   }
}

/**
 * Defines current position as origin (0,0) coordinate.
 * TODO: implement!!!
 */
int XYStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
 * Returns the stage position limits in um.
 * TODO: implement!!!
 */
int XYStage::GetLimits(double& xMin, double& xMax, double& yMin, double& yMax)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a specified command to the controller
 */
int XYStage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd.c_str(), (unsigned) cmd.length()))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned long bufLen = 80;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), answer + curIdx, bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
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

int XYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

int XYStage::OnIDX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idX_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idX_ = (unsigned) id;
   }

   return DEVICE_OK;
}

int XYStage::OnIDY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idY_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idY_ = (unsigned) id;
   }

   return DEVICE_OK;
}
int XYStage::OnCommandSet(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(g_HLCommandSet ? g_HighLevel : g_LowLevel);
   }
   else if (eAct == MM::AfterSet)
   {
      string cmd;
      pProp->Get(cmd);
      if (cmd.compare(g_HighLevel) == 0)
         g_HLCommandSet =  true;
      else
         g_HLCommandSet = false;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
Stage::Stage() :
   initialized_(false),
   port_("Undefined"),
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000),
   id_(3)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl stage driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // device id
   pAct = new CPropertyAction (this, &Stage::OnID);
   CreateProperty("ID", "3", MM::Integer, false, pAct, true);

   // define command set
   pAct = new CPropertyAction (this, &Stage::OnCommandSet);
   CreateProperty(g_CommandSet, g_LowLevel, MM::String, false, pAct, true);

   // limit the range of values for the command set
   AddAllowedValue(g_CommandSet, g_LowLevel);
   AddAllowedValue(g_CommandSet, g_HighLevel);

}

Stage::~Stage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{
   // set property list
   // -----------------
   
   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Autofocus
   // ---------
   if (id_ == 'Z')
   {
      // this property only makes sense to the ASI Z stage
      pAct = new CPropertyAction (this, &Stage::OnAutofocus);
      ret = CreateProperty("Autofocus", "5", MM::Integer, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // switch to selected command protocol;
   const unsigned cmdLen = 2;
   char cmd[cmdLen];

   if (g_HLCommandSet)
   {
      // high level commands
      cmd[0] = (char)0xFF;
      cmd[1] = (char)65;
   }
   else
   {
      // low level commands
      cmd[0] = (char)0xFF;
      cmd[1] = (char)66;
   }

   ret = WriteToComPort(port_.c_str(), cmd, cmdLen);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Stage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Stage::Busy()
{
   if (g_HLCommandSet)
   {
      // HIGH_LEVEL
      // ==========

      // format the command
      char devId[2] = {0, 0};
      devId[1] = (char) id_;
      char cmd[256];
      sprintf(cmd, "STATUS %s\r", devId);

      // >>> NOTE: status will check all devices in the controller.

      // send command
      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, (unsigned)strlen(cmd)))
         return false; // can't write so say we're not busy

      char status=0;
      unsigned long read=0;
      int numTries=0;
      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), &status, 1, read))
            return false;
         numTries++;
         #ifdef WIN32
         Sleep(5);
         #endif
      }
      while(read==0 && numTries < 3);

      if (status == 'B')
         return true;
      else
         return false;
   }
   else
   {
      // LOW_LEVEL
      // =========

      const unsigned cmdLen = 3;
      char cmd[cmdLen];
      cmd[0] = (unsigned char)id_;
      cmd[1] = 63; // status
      cmd[2] = 58;  // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, cmdLen))
         return false;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      char reply;
      unsigned long read=0;
      unsigned long startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), &reply, 1, read))
            return false;
      }
      while(read==0 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (read == 0)
         return false; // we timed out, but we'll claim not busy

      if (reply == 66)
         return true;
      else
         return false;
   }
}

int Stage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   
   ostringstream os;
   os << id_ << " SetPosition() " << pos;
   this->LogMessage(os.str().c_str());

   return SetPositionSteps(steps);
}

int Stage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;

   ostringstream os;
   os << id_ << " GetPosition() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int Stage::SetPositionSteps(long pos)
{
   if (g_HLCommandSet)
   {

      // format the command
      ostringstream cmd;
      cmd << "MOVE ";
      cmd << (char) id_ << "=" << pos << "\r";

      // TODO: what if we are busy???

      string resp;
      int ret = ExecuteCommand(cmd.str(), resp);
      if (ret != DEVICE_OK)
         return ret;

      // parse the answer and return the proper error code
      // TODO...

      istringstream is(resp);
      string outcome;
      is >> outcome;

      if (outcome.compare(":A") == 0)
         return DEVICE_OK; // success!

      // figure out the error code
      int code;
      is >> code;
      return code;
   }
   else
   {
      PurgeComPort(port_.c_str());

      const unsigned cmdLen = 7;
      unsigned char cmdx[cmdLen];
      cmdx[0] = id_;
      cmdx[1] = 84; // move
      cmdx[2] = 3;  // data length
      cmdx[3] = (unsigned char)pos; // lsb
      cmdx[4] = (unsigned char)(pos >> 8); // mb
      cmdx[5] = (unsigned char)(pos >> 16); // lsb
      cmdx[6] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), (char*)cmdx, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // move command
      char cmdm[3];
      cmdm[0] = id_;
      cmdm[1] = 71; // move
      cmdm[2] = 58;
      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdm, 3))
         return DEVICE_SERIAL_COMMAND_FAILED;

      return DEVICE_OK;
   }
}
  
int Stage::GetPositionSteps(long& steps)
{
   if (g_HLCommandSet)
   {
      // format the command
      ostringstream cmd;
      cmd << "WHERE " << (char)id_ << "\r";

      // TODO: what if we are busy???

      string resp;
      int ret = ExecuteCommand(cmd.str(), resp);
      if (ret != DEVICE_OK)
         return ret;

      // parse the answer and return the proper error code
      // TODO...

      istringstream is(resp);
      string outcome;
      is >> outcome;

      if (outcome.compare(":A") == 0)
      {
         is >> steps;
         return DEVICE_OK; // success!
      }

      // figure out the error code
      int code;
      is >> code;
      return code;
   }
   else
   {
      PurgeComPort(port_.c_str());

      const unsigned cmdLen = 4;
      char cmdx[cmdLen];
      cmdx[0] = id_;
      cmdx[1] = 97; // read pos
      cmdx[2] = 3;  // data length
      cmdx[3] = 58; // :

      if (DEVICE_OK != WriteToComPort(port_.c_str(), cmdx, cmdLen))
         return DEVICE_SERIAL_COMMAND_FAILED;

      // read the reply
      // block/wait for acknowledge, or until we time out;
      const long replyLen = 3;
      char reply[3];
      unsigned curIdx = 0;
      unsigned long read;
      unsigned long startTime = GetClockTicksUs();

      do {
         if (DEVICE_OK != ReadFromComPort(port_.c_str(), reply + curIdx, 1, read))
            return DEVICE_SERIAL_COMMAND_FAILED;
         curIdx += read;
      }
      while(curIdx < 3 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

      if (curIdx != 3)
         return DEVICE_SERIAL_TIMEOUT;

      steps = (unsigned char)reply[0] + 256 * (unsigned char)reply[1]  + 256 * 256 * (unsigned char)reply[2];

      return DEVICE_OK;
   }
}
  
int Stage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
 
int Stage::GetLimits(double& min, double& max)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

int Stage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd.c_str(), (unsigned) cmd.length()))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned long bufLen = 80;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), answer + curIdx, bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}

/**
 * This may be problematic since it makes sense and works only for a Z-stage.
 * WARNING: it explicitly uses Z stage name and may not be what the user intended to do.
 */
int Stage::Autofocus(long param)
{
   // format the command
   ostringstream cmd;
   cmd << "AF Z=";
   cmd << param << "\r";

   string resp;
   int ret = ExecuteCommand(cmd.str(), resp);
   if (ret != DEVICE_OK)
      return ret;

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Stage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Stage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

int Stage::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)id_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      id_ = (unsigned) id;
   }

   return DEVICE_OK;
}

int Stage::OnCommandSet(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)g_HLCommandSet);
   }
   else if (eAct == MM::AfterSet)
   {
      long cmd;
      pProp->Get(cmd);
      g_HLCommandSet = cmd == 0 ? false : true;
   }

   return DEVICE_OK;
}


int Stage::OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
      long param;
      pProp->Get(param);
      if (!g_HLCommandSet)
         return ERR_INVALID_MODE;

      return Autofocus(param);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ASIController device
///////////////////////////////////////////////////////////////////////////////

/**
 * Generic device to send any command to the ASI controller.
 * This device is not suitable for interactive use from the GUI.
 * Designed for flexible scripting and debugging.
 */
ASIController::ASIController() :
   initialized_(false),
   port_("Undefined"),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ASIControllerName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "ASI(Ludl) controller adtapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ASIController::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

ASIController::~ASIController()
{
   Shutdown();
}

void ASIController::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ASIControllerName);
}

int ASIController::Initialize()
{
   // set property list
   // -----------------
   
   // command
   CPropertyAction *pAct = new CPropertyAction (this, &ASIController::OnCommand);
   CreateProperty("Command", "", MM::String, false, pAct);

   pAct = new CPropertyAction (this, &ASIController::OnResponse);
   CreateProperty("Response", "", MM::String, true, pAct);

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // switch to selected command protocol;
   const unsigned cmdLen = 2;
   char cmd[cmdLen];

   // set-up high level commands
   cmd[0] = (char)0xFF;
   cmd[1] = (char)65;
   ret = WriteToComPort(port_.c_str(), cmd, cmdLen);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ASIController::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ASIController::Busy()
{
   const char* cmd = "STATUS\r";

   // send command
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd, (unsigned)strlen(cmd)))
      return false; // can't write so say we're not busy

   char status=0;
   unsigned long read=0;
   int numTries=0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &status, 1, read))
         return false;
      numTries++;
   } while(read==0 && numTries < 3);

   if (status == 'B')
      return true;
   else
      return false;
}
    
int ASIController::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != WriteToComPort(port_.c_str(), cmd.c_str(), (unsigned) cmd.length()))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned long bufLen = 80;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), answer + curIdx, bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ASIController::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int ASIController::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(command_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      return ExecuteCommand(command_, response_);
   }

   return DEVICE_OK;
}

int ASIController::OnResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(response_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      assert(!"Read-only property modified");
   }

   return DEVICE_OK;
}
