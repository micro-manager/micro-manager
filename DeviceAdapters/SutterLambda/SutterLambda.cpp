///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterLambda.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
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
// CVS:           $Id$
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "SutterLambda.h"
#include <vector>
#include <memory>
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>



// Wheels
const char* g_WheelAName = "Wheel-A";
const char* g_WheelBName = "Wheel-B";
const char* g_WheelCName = "Wheel-C";
const char* g_ShutterAName = "Shutter-A";
const char* g_ShutterBName = "Shutter-B";

#ifdef DefineShutterOnTenDashTwo
const char* g_ShutterAName10dash2 = "Shutter-A 10-2";
const char* g_ShutterBName10dash2 = "Shutter-B 10-2";
const double g_busyTimeoutMs = 500;
#endif

const char* g_DG4WheelName = "Wheel-DG4";
const char* g_DG4ShutterName = "Shutter-DG4";

const char* g_ShutterModeProperty = "Mode";
const char* g_FastMode = "Fast";
const char* g_SoftMode = "Soft";
const char* g_NDMode = "ND";
const char* g_ControllerID = "Controller Info";

using namespace std;

std::map<std::string, bool> g_Busy;
std::map<std::string, MMThreadLock*> gplocks_;

int g_DG4Position = 0;
bool g_DG4State = false;

void newGlobals(std::string p)
{
   if( g_Busy.end() ==  g_Busy.find(p))
   {
      g_Busy[p] = false;
      gplocks_[p] = new MMThreadLock();
   }
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_WheelAName, MM::StateDevice, "Lambda 10 filter wheel A");
   RegisterDevice(g_WheelBName, MM::StateDevice, "Lambda 10 filter wheel B");
   RegisterDevice(g_WheelCName, MM::StateDevice, "Lambda 10 wheel C (10-3 only)");
   RegisterDevice(g_ShutterAName, MM::ShutterDevice, "Lambda 10 shutter A");
   RegisterDevice(g_ShutterBName, MM::ShutterDevice, "Lambda 10 shutter B");
#ifdef DefineShutterOnTenDashTwo
   RegisterDevice(g_ShutterAName10dash2, MM::ShutterDevice, "Lambda 10-2 shutter A");
   RegisterDevice(g_ShutterBName10dash2, MM::ShutterDevice, "Lambda 10-2 shutter B");
#endif
   RegisterDevice(g_DG4ShutterName, MM::ShutterDevice, "DG4 shutter");
   RegisterDevice(g_DG4WheelName, MM::StateDevice, "DG4 filter changer");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_WheelAName) == 0)
   {
      // create Wheel A
      Wheel* pWheel = new Wheel(g_WheelAName, 0);
      return pWheel;
   }
   else if (strcmp(deviceName, g_WheelBName) == 0)
   {
      // create Wheel B
      Wheel* pWheel = new Wheel(g_WheelBName, 1);
      return pWheel;
   }
   else if (strcmp(deviceName, g_WheelCName) == 0)
   {
      // create Wheel C
      Wheel* pWheel = new Wheel(g_WheelCName, 2);
      return pWheel;
   }
   else if (strcmp(deviceName, g_ShutterAName) == 0)
   {
      // create Shutter A
      Shutter* pShutter = new Shutter(g_ShutterAName, 0);
      return pShutter;
   }
   else if (strcmp(deviceName, g_ShutterBName) == 0)
   {
      // create Shutter B
      Shutter* pShutter = new Shutter(g_ShutterBName, 1);
      return pShutter;
   }
#ifdef DefineShutterOnTenDashTwo
   else if (strcmp(deviceName, g_ShutterAName10dash2) == 0)
   {
      // create Shutter A
      ShutterOnTenDashTwo* pShutter = new ShutterOnTenDashTwo(g_ShutterAName10dash2, 0);
      return pShutter;
   }
   else if (strcmp(deviceName, g_ShutterBName10dash2) == 0)
   {
      // create Shutter B
      ShutterOnTenDashTwo* pShutter = new ShutterOnTenDashTwo(g_ShutterBName10dash2, 1);
      return pShutter;
   }
#endif
   else if (strcmp(deviceName, g_DG4ShutterName) == 0)
   {
      // create DG4 shutter
      return new DG4Shutter();
   }
   else if (strcmp(deviceName, g_DG4WheelName) == 0)
   {
      // create DG4 Wheel
      return new DG4Wheel();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////////
//  SutterUtils: Static utility functions that can be used from all devices
//////////////////////////////////////////////////////////////////////////////////
bool SutterUtils::ControllerBusy(MM::Device& /*device*/, MM::Core& /*core*/, std::string port, 
                                 unsigned long /*answerTimeoutMs*/)
{

   MMThreadGuard g(*(::gplocks_[port]));
   return g_Busy[port];
}
int SutterUtils::GoOnLine(MM::Device& device, MM::Core& core, 
                          std::string port, unsigned long answerTimeoutMs) 
{
   // Transfer to On Line
   unsigned char setSerial = (unsigned char)238;
   int ret = core.WriteToSerial(&device, port.c_str(), &setSerial, 1);
   if (DEVICE_OK != ret)
      return ret;

   unsigned char answer = 0;
   bool responseReceived = false;
   int unsigned long read;
   MM::MMTime startTime = core.GetCurrentMMTime();
   do {
      if (DEVICE_OK != core.ReadFromSerial(&device, port.c_str(), &answer, 1, read))
         return false;
      if (answer == 238)
         responseReceived = true;
   }
   while( !responseReceived && (core.GetCurrentMMTime() - startTime) < (answerTimeoutMs * 1000.0) );
   if (!responseReceived)
      return ERR_NO_ANSWER;

   return DEVICE_OK;
}

int SutterUtils::GetControllerType(MM::Device& device, MM::Core& core, std::string port, 
                                   unsigned long answerTimeoutMs, std::string& type, std::string& id) 
{
   core.PurgeSerial(&device, port.c_str());
   int ret = DEVICE_OK;

   std::vector<unsigned char> ans;
   std::vector<unsigned char> emptyv;
   std::vector<unsigned char> command;
   command.push_back((unsigned char)253);

   ret = SutterUtils::SetCommandNoCR(device, core, port, command, emptyv, answerTimeoutMs, ans, true);
   if (ret != DEVICE_OK)
      return ret;

   char ans2[128];
   ret = core.GetSerialAnswer(&device, port.c_str(), 128, ans2, "\r");

   std::string answer = ans2;
   if (ret != DEVICE_OK) {
      std::ostringstream errOss;
      errOss << "Could not get answer from 253 command (GetSerialAnswer returned " << ret << "). Assuming a 10-2";
      core.LogMessage(&device, errOss.str().c_str(), true);
      type = "10-2";
      id = "10-2";
   }
   else if (answer.length() == 0 ) {
      core.LogMessage(&device, "Answer from 253 command was empty. Assuming a 10-2", true);
      type = "10-2";
      id = "10-2";
   }
   else {
      if (answer.substr(0, 2) == "SC") {
         type = "SC";
      } else if (answer.substr(0, 4) == "10-3") {
         type = "10-3";
      }
      id = answer.substr(0, answer.length() - 2);
      core.LogMessage(&device, ("Controller type is " + std::string(type)).c_str(), true);
   }

   return DEVICE_OK;
}

/**
* Queries the controller for its status
* Meaning of status depends on controller type
* status should be allocated by the caller and at least be 21 bytes long
* status will be the answer returned by the controller, stripped from the first byte (which echos the command) 
*/
int SutterUtils::GetStatus(MM::Device& device, MM::Core& core,        
                           std::string port, unsigned long answerTimeoutMs,
                           unsigned char* status)
{
   core.PurgeSerial(&device, port.c_str());
   unsigned char msg[1];
   msg[0]  = 204;
   // send command
   int ret = core.WriteToSerial(&device, port.c_str(), msg, 1);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char ans = 0;
   bool responseReceived = false;
   unsigned long read;
   MM::MMTime startTime = core.GetCurrentMMTime();
   do {
      if (DEVICE_OK != core.ReadFromSerial(&device, port.c_str(), &ans, 1, read))
         return false;
      /*if (read > 0)
      printf("Read char: %x", ans);*/
      if (ans == 204)
         responseReceived = true;
      CDeviceUtils::SleepMs(2);
   }
   while( !responseReceived && (core.GetCurrentMMTime() - startTime) < (answerTimeoutMs * 1000.0) );
   if (!responseReceived)
      return ERR_NO_ANSWER;

   responseReceived = false;
   int j = 0;
   startTime = core.GetCurrentMMTime();
   do {
      if (DEVICE_OK != core.ReadFromSerial(&device, port.c_str(), &ans, 1, read))
         return false;
      if (read > 0) {
         /* printf("Read char: %x", ans);*/
         status[j] = ans;
         j++;
         if (ans == '\r')
            responseReceived = true;
      }
      CDeviceUtils::SleepMs(2);
   }
   while( !responseReceived && (core.GetCurrentMMTime() - startTime) < (answerTimeoutMs * 1000.0) && j < 22);
   if (!responseReceived)
      return ERR_NO_ANSWER;

   return DEVICE_OK;
}





int SutterUtils::SetCommand(MM::Device& device, MM::Core& core, 
                            const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho,
                            const unsigned long answerTimeoutMs, const bool responseRequired, const bool CRExpected )
{
   std::vector<unsigned char> tmpr;
   return  SutterUtils::SetCommand(device, core, port,  command, alternateEcho, answerTimeoutMs, tmpr,  responseRequired, CRExpected);
}



// lock the port for access,
// write 1, 2, or 3 char. command to equipment
// ensure the command completed by waiting for \r
// pass response back in argument
int SutterUtils::SetCommand(MM::Device& device, MM::Core& core, 
                            const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho,
                            const unsigned long answerTimeoutMs, std::vector<unsigned char>& response,  const bool responseRequired, const bool CRExpected)



{
   int ret = DEVICE_OK;
   bool responseReceived = false;

   // block if port is accessed by another thread
   MMThreadGuard g(*(::gplocks_[port]));
   if( ::g_Busy[port])
      core.LogMessage(&device, "busy entering SetCommand", false);

   if(!::g_Busy[port] )
   {
      g_Busy[port] = true;
      if( ! command.empty())
      {
         core.PurgeSerial(&device, port.c_str());
         // start time of entire transaction
         MM::MMTime commandStartTime = core.GetCurrentMMTime();
         // write command to the port
         ret = core.WriteToSerial(&device, port.c_str(), &command[0], (unsigned long) command.size());

         if( DEVICE_OK == ret)
         {
            if( responseRequired)
            {
               // now ensure that command is echoed from controller
               bool expectA13 = CRExpected;
               std::vector<unsigned char>::const_iterator allowedResponse = alternateEcho.begin();
               for( std::vector<unsigned char>::const_iterator irep = command.begin(); irep != command.end(); ++irep)
               {
                  // the echoed command can be different from the command, for example, the SC controller
                  // echoes close 172 for open 170 and vice versa....

                  // and Wheel set position INTERMITTENTLY echos the position with the command and speed stripped out

                  // block/wait for acknowledge, or until we time out;
                  unsigned char answer = 0;
                  unsigned long read;
                  MM::MMTime responseStartTime = core.GetCurrentMMTime();
                  // read the response
                  for(;;)
                  {
                     answer = 0;
                     int status = core.ReadFromSerial( &device, port.c_str(), &answer, 1, read);
                     if (DEVICE_OK != status)
                     {
                        // give up - somebody pulled the serial hardware out of the computer
                        core.LogMessage(&device, "ReadFromSerial failed", false);
                        return status;
                     }
                     if( 0 < read)
                        response.push_back(answer);
                     if (answer == *irep)
                     {
                        if (!CRExpected)
                        {
                           responseReceived = true;
                        }
                        break;
                     }
                     else if (answer == 13) // CR
                     {
                        if(CRExpected)
                        {
                           expectA13 = false;
                           core.LogMessage(&device, "error, command was not echoed!", false);
                        }
                        else
                        {
                           // todo: can 13 ever be a command echo?? (probably not - no 14 position filter wheels)
                           ;
                        }
                        break;
                     }
                     else if( answer != 0)
                     {
                        if( alternateEcho.end() != allowedResponse )
                        {
                           // command was echoed, after a fashion....
                           if( answer == *allowedResponse)
                           {

                              core.LogMessage(&device, ("command " + CDeviceUtils::HexRep(command) + 
                                  " was echoed as "+ CDeviceUtils::HexRep(response)).c_str(), true);
                              ++allowedResponse;
                              break;
                           }
                        }
                        std::ostringstream bufff;
                        bufff.flags(  ios::hex | ios::showbase );

                        bufff << (unsigned int)answer;
                        core.LogMessage(&device, (std::string("unexpected response: ") + bufff.str()).c_str(), false);
                        break;
                     }
                     MM::MMTime delta = core.GetCurrentMMTime() - responseStartTime;
                     if( 1000.0 * answerTimeoutMs < delta.getUsec()   )
                     {
                        expectA13 = false;
                        std::ostringstream bufff;
                        bufff << delta.getUsec() << " microsec";

                        // in some cases it might be normal for the controller to not respond,
                        // for example, whenever the command is the same as the previous command or when
                        // go on-line sent to a controller already on-line
                        core.LogMessage(&device, (std::string("command echo timeout after ") + bufff.str()).c_str(), !responseRequired );
                        break;
                     }
                     CDeviceUtils::SleepMs(5);
                     // if we are not at the end of the 'alternate' echo, iterate forward in that alternate echo string
                     if(alternateEcho.end() != allowedResponse)
                       ++allowedResponse;
                  } // loop over timeout
               } // the command was echoed  entirely...
               if( expectA13)
               {
                  MM::MMTime startTime = core.GetCurrentMMTime();
                  // now look for a 13 - this indicates that the command has really completed!
                  unsigned char answer = 0;
                  unsigned long read;
                  for(;;)
                  {
                     answer = 0;
                     int status = core.ReadFromSerial(&device, port.c_str(), &answer, 1, read);
                     if (DEVICE_OK != status )
                     {
                        core.LogMessage(&device, "ReadFromComPort failed", false);
                        return status;
                     }
                     if( 0 < read)
                        response.push_back(answer);
                     if (answer == 13) // CR  - sometimes the SC sends a 1 before the 13....
                     {
                        responseReceived = true;
                        break;
                     }
                     if( 0 < read)
                     {
                        std::ostringstream bufff;
                        bufff.flags(  ios::hex | ios::showbase );
                        bufff << (unsigned int)answer;
                        core.LogMessage(&device, ("error, extraneous response  " + bufff.str()).c_str(), false);
                     }

                     MM::MMTime del2 = core.GetCurrentMMTime() - startTime;
                     if( 1000.0 * answerTimeoutMs < del2.getUsec())
                     {
                        std::ostringstream bufff;
                        MM::MMTime del3 = core.GetCurrentMMTime() - commandStartTime;
                        bufff << "command completion timeout after " << del3.getUsec() << " microsec";
                        core.LogMessage(&device, bufff.str().c_str(),false);
                        break;
                     }
                  }
               }
            } // response required
            else // no response required / expected
            {
               CDeviceUtils::SleepMs(5); // docs say controller echoes any command within 100 microseconds
               // 3 ms is enough time for controller to send 3 bytes @  9600 baud
               core.PurgeSerial(&device, port.c_str());
            }
         }
         else
         {
            core.LogMessage(&device,"WriteToComPort failed!", false);
         }
         g_Busy[port] = false;
      }// a command was specified

      return responseRequired?(responseReceived?DEVICE_OK:DEVICE_ERR):DEVICE_OK;
   }// not busy...
   else
   {
      core.LogMessage(&device, ("Sequence error, port " + port + " was busy!").c_str(), false);
      ret =  DEVICE_INTERNAL_INCONSISTENCY;
   }

   return ret;

}

// some commands don't send a \r!!!
int SutterUtils::SetCommandNoCR(MM::Device& device, MM::Core& core, 
                                const std::string port, const std::vector<unsigned char> command, const std::vector<unsigned char> alternateEcho, 
                                const unsigned long answerTimeoutMs, std::vector<unsigned char>& response, const bool responseRequired)
{

   return  SutterUtils::SetCommand(device, core, port,  command, alternateEcho, answerTimeoutMs, response,  responseRequired, false);
}






///////////////////////////////////////////////////////////////////////////////
// Wheel implementation
// ~~~~~~~~~~~~~~~~~~~~

Wheel::Wheel(const char* name, unsigned id) :
initialized_(false), 
numPos_(10), 
id_(id), 
name_(name), 
curPos_(0), 
speed_(3), 
answerTimeoutMs_(500)
{
   assert(id==0 || id==1 || id==2);
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
   SetErrorText(ERR_INVALID_SPEED, "Invalid speed setting. You must enter a number between 0 to 7.");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Sutter Lambda filter wheel adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // Answertimeout
   pAct = new CPropertyAction (this, &Wheel::OnAnswerTimeout);
   CreateProperty("Timeout(ms)", "500", MM::Integer, false, pAct, true);

   UpdateStatus();
}

Wheel::~Wheel()
{
   Shutdown();
}

void Wheel::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

/**
* Kludgey implementation of the status check
*
*/
bool Wheel::Busy()
{
   MMThreadGuard g(*(::gplocks_[port_]));
   return g_Busy[port_];
}

int Wheel::Initialize()
{


  newGlobals(port_);
   // set property list
   // -----------------

   // Gate Closed Position
   int ret = CreateProperty(MM::g_Keyword_Closed_Position,"", MM::String, false);

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnSpeed);
   ret = CreateProperty(MM::g_Keyword_Speed, "3", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   for (int i=0; i<8; i++) {
      std::ostringstream os;
      os << i;
      AddAllowedValue(MM::g_Keyword_Speed, os.str().c_str());
   }

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Delay
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

	newGlobals(port_);



   // Busy
   // -----
   // NOTE: in the lack of better status checking scheme,
   // this is a kludge to allow resetting the busy flag.  
   pAct = new CPropertyAction (this, &Wheel::OnBusy);
   ret = CreateProperty("Busy", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (unsigned i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%d", i);
      SetPositionLabel(i, buf);
      // Also give values for Closed-Position state while we are at it
      snprintf(buf, bufSize, "%d", i);
      AddAllowedValue(MM::g_Keyword_Closed_Position, buf); 
   }

   GetGateOpen(open_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // Transfer to On Line
   unsigned char setSerial = (unsigned char)238;
   ret = WriteToComPort(port_.c_str(), &setSerial, 1);
   if (DEVICE_OK != ret)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Wheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/**
* Sends a command to Lambda through the serial port.
* The command format is one byte encoded as follows:
* bits 0-3 : position
* bits 4-6 : speed
* bit  7   : wheel id
*/

bool Wheel::SetWheelPosition(unsigned pos)
{

   // build the respective command for the wheel

   unsigned char upos = (unsigned char) pos;


   std::vector<unsigned char> command;

   // the Lambda 10-2, at least, SOMETIMES echos only the pos, not the command and speed - really confusing!!!
   std::vector<unsigned char> alternateEcho;

   switch( id_)
   {
   case 0:
      command.push_back( (unsigned char) (speed_ * 16 + pos) );
      alternateEcho.push_back( upos );
      break;
   case 1:
      command.push_back( (unsigned char)(128 + speed_ * 16 + pos));
      alternateEcho.push_back( upos );
      break;
   case 2:
      alternateEcho.push_back( upos);    // TODO!!!  G.O.K. what they really echo...
      command.push_back( (unsigned char)252) ; // used for filter C
      command.push_back( (unsigned char) (speed_ * 16 + pos));
      break;
   default:
      break;
   }

   std::vector<unsigned char> tmp;

   int ret2 = SutterUtils::SetCommand(*this, *GetCoreCallback(), port_, command, alternateEcho, 
      (unsigned long)(0.5+answerTimeoutMs_), tmp, true, true);
   return (DEVICE_OK == ret2) ? true: false;

}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Wheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      bool gateOpen;
      GetGateOpen(gateOpen);
      long pos;
      pProp->Get(pos);

      if (pos >= (long)numPos_ || pos < 0)
      {
         pProp->Set((long)curPos_); // revert
         return ERR_UNKNOWN_POSITION;
      }

      // For efficiency, the previous state and gateOpen position is cached
      if (gateOpen) {
         // check if we are already in that state
         if (((unsigned)pos == curPos_) && open_)
            return DEVICE_OK;

         // try to apply the value
         if (!SetWheelPosition(pos))
         {
            pProp->Set((long)curPos_); // revert
            return ERR_SET_POSITION_FAILED;
         }
      } else {
         if (!open_) {
            curPos_ = pos;
            return DEVICE_OK;
         }

         char closedPos[MM::MaxStrLength];
         GetProperty(MM::g_Keyword_Closed_Position, closedPos);
         int gateClosedPosition = atoi(closedPos);

         if (!SetWheelPosition(gateClosedPosition))
         {
            pProp->Set((long) curPos_); // revert
            return ERR_SET_POSITION_FAILED;
         }
      }
      curPos_ = pos;
      open_ = gateOpen;
   }

   return DEVICE_OK;
}

int Wheel::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Wheel::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      long speed;
      pProp->Get(speed);
      if (speed < 0 || speed > 7)
      {
         pProp->Set((long)speed_); // revert
         return ERR_INVALID_SPEED;
      }
      speed_ = speed;
   }

   return DEVICE_OK;
}

int Wheel::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}

int Wheel::OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      MMThreadGuard g(*(::gplocks_[port_]));
      pProp->Set(g_Busy[port_] ? 1L : 0L);
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard g(*(::gplocks_[port_]));
      long b;
      pProp->Get(b);
      g_Busy[port_] = !(b==0);
   }

   return DEVICE_OK;
}

int Wheel::OnAnswerTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(answerTimeoutMs_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(answerTimeoutMs_);
   }

   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

Shutter::Shutter(const char* name, int id) :
initialized_(false), 
id_(id), 
name_(name), 
nd_(1),
controllerType_("10-2"),
controllerId_(""),
answerTimeoutMs_(500), 
curMode_(g_FastMode)
{
   InitializeDefaultErrorMessages();
   SetErrorText (ERR_NO_ANSWER, "No Sutter Controller found.  Is it switched on and connected to the specified comunication port?");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Sutter Lambda shutter adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay();
}

Shutter::~Shutter()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   if (initialized_) 
      return DEVICE_OK;

newGlobals(port_);


   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");


   int j=0;
   while (GoOnLine() != DEVICE_OK && j < 4)
      j++;
   if (j >= 4)
      return ERR_NO_ANSWER;

   ret = GetControllerType(controllerType_, controllerId_);
   if (ret != DEVICE_OK)
      return ret;

   CreateProperty(g_ControllerID, controllerId_.c_str(), MM::String, true);
   std::string msg = "Controller reported ID " +  controllerId_;
   LogMessage(msg.c_str());

   // Shutter mode
   // ------------
   if (controllerType_ == "SC" || controllerType_ == "10-3") {
      pAct = new CPropertyAction (this, &Shutter::OnMode);
      vector<string> modes;
      modes.push_back(g_FastMode);
      modes.push_back(g_SoftMode);
      modes.push_back(g_NDMode);

      CreateProperty(g_ShutterModeProperty, g_FastMode, MM::String, false, pAct);
      SetAllowedValues(g_ShutterModeProperty, modes);
      // set initial value
      //SetProperty(g_ShutterModeProperty, curMode_.c_str());

      // Neutral Density-mode Shutter (will not work with 10-2 controller)
      pAct = new CPropertyAction(this, &Shutter::OnND);
      CreateProperty("NDSetting", "1", MM::Integer, false, pAct);
      SetPropertyLimits("NDSetting", 1, 144);
   }

   unsigned char status[22];
   ret = SutterUtils::GetStatus(*this, *GetCoreCallback(), port_, 100, status);
   // note: some controllers will not know this command and return an error, 
   // so do not balk if we do not get an answer

   // status meaning is different on different controllers:
   if (controllerType_ == "10-3") {
      if (id_ == 0) {
         if (status[4] == 170)
            SetOpen(true);
         if (status[4] == 172)
            SetOpen(false);
         if (status[6] == 0xDC)
            curMode_ = g_FastMode;
         if (status[6] == 0xDD)
            curMode_ = g_SoftMode;
         if (status[6] == 0xDE)
            curMode_ = g_NDMode;
         if (curMode_ == g_NDMode)
            nd_ = (unsigned int) status[7];
      } else if (id_ == 1) {
         if (status[5] == 186)
            SetOpen(true);
         if (status[5] == 188)
            SetOpen(false);
         int offset = 0;
         if (status[6] == 0xDE)
            offset = 1;
         if (status[7+offset] == 0xDC)
            curMode_ = g_FastMode;
         if (status[7+offset] == 0xDD)
            curMode_ = g_SoftMode;
         if (status[7+offset] == 0xDE)
            curMode_ = g_NDMode;
         if (curMode_ == g_NDMode)
            nd_ = (unsigned int) status[8 + offset];
      }
   } else if (controllerType_ == "SC") {
      if (status[0] == 170)
         SetOpen(true);
      if (status[0] == 172)
         SetOpen(false);
      if (status[1] == 0xDC)
         curMode_ = g_FastMode;
      if (status[1] == 0xDD)
         curMode_ = g_SoftMode;
      if (status[1] == 0xDE)
         curMode_ = g_NDMode;
      if (curMode_ == g_NDMode)
         nd_ = (unsigned int) status[2];
   }

   // Needed for Busy flag
   changedTime_ = GetCurrentMMTime();

   initialized_ = true;

   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


MM::DeviceDetectionStatus Shutter::DetectDevice(void)
{
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {
      // convert into lower case to detect invalid port names:
      std::string test = port_;
      for(std::string::iterator its = test.begin(); its != test.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      // ensure we have been provided with a valid serial port device name
      if( 0< test.length() &&  0 != test.compare("undefined")  && 0 != test.compare("unknown") )
      {


         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");

         // we can speed up detection with shorter answer timeout here
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "50.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());

         if (DEVICE_OK == pS->Initialize()) 
         {
            int status = SutterUtils::GoOnLine(*this, *GetCoreCallback(), port_, nint(answerTimeoutMs_));
            if( DEVICE_OK == status)
               result = MM::CanCommunicate;
            pS->Shutdown();
         }
         // but for operation, we'll need a longer timeout
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "2000.0");
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice");
   }

   return result;
}



int Shutter::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
* Sends a command to Lambda through the serial port.
*/
bool Shutter::SetShutterPosition(bool state)
{
   std::vector<unsigned char> command;
   std::vector<unsigned char> alternateEcho;

   // the Lambda intermittently SC echoes inverted commands!

   if (id_ == 0)
   {
      // shutter A
      command.push_back( state ? 170 : 172);
      alternateEcho.push_back( state? 172 : 170);
   }
   else
   {
      // shutter B
      command.push_back( state ? 186 : 188);
      alternateEcho.push_back( state? 188 : 186);
   }

   int ret = SutterUtils::SetCommand( *this, *GetCoreCallback(),  port_, command, alternateEcho, 
      (unsigned long)(0.5+answerTimeoutMs_));

   // Start timer for Busy flag
   changedTime_ = GetCurrentMMTime();

   return (DEVICE_OK == ret)? true: false;
}

bool Shutter::Busy()
{
   if (::g_Busy[port_])
      return true;

   if (GetDelayMs() > 0.0) {
      MM::MMTime interval = GetCurrentMMTime() - changedTime_;
      MM::MMTime delay(GetDelayMs()*1000.0);
      if (interval < delay ) {
         return true;
      }
   }

   return false;
}

bool Shutter::ControllerBusy()
{
   return SutterUtils::ControllerBusy(*this, *GetCoreCallback(), port_, static_cast<long>(0.5+answerTimeoutMs_));
}

bool Shutter::SetShutterMode(const char* mode)
{
   if (strcmp(mode, g_NDMode) == 0)
      return SetND(nd_);

   std::vector<unsigned char> command;
   std::vector<unsigned char> alternateEcho;

   if( 0 == strcmp( mode, g_FastMode) )
      command.push_back((unsigned char)220);
   else if( 0 == strcmp( mode, g_SoftMode) )
      command.push_back((unsigned char)221);

   if( "SC" != controllerType_)
      command.push_back((unsigned char)(id_+1));


   int ret = SutterUtils::SetCommand( *this, *GetCoreCallback(),  port_, command, alternateEcho, 
      (unsigned long)(0.5+answerTimeoutMs_));
   return (DEVICE_OK == ret)? true: false;
}



bool Shutter::SetND(unsigned int nd)
{
   std::vector<unsigned char> command;
   std::vector<unsigned char> alternateEcho;

   command.push_back((unsigned char)222);

   if( "SC" == controllerType_)
      command.push_back((unsigned char)nd);
   else
   {
      command.push_back((unsigned char)(id_ + 1));
      command.push_back((unsigned char)(nd));
   }

   int ret = SutterUtils::SetCommand( *this, *GetCoreCallback(),  port_, command, alternateEcho, 
      (unsigned long)(0.5+answerTimeoutMs_));
   return (DEVICE_OK == ret)? true: false;

}

int Shutter::GoOnLine() 
{
   return SutterUtils::GoOnLine(*this, *GetCoreCallback(), port_, nint(answerTimeoutMs_));
}

int Shutter::GetControllerType(std::string& type, std::string& id) 
{
   return SutterUtils::GetControllerType(*this, *GetCoreCallback(), port_, nint(answerTimeoutMs_),
      type, id);
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      // apply the value
      SetShutterPosition(pos == 0 ? false : true);
   }

   return DEVICE_OK;
}

int Shutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Shutter::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(curMode_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string mode;
      pProp->Get(mode);

      if (SetShutterMode(mode.c_str()))
         curMode_ = mode;
      else
         return ERR_UNKNOWN_SHUTTER_MODE;
   }

   return DEVICE_OK;
}

int Shutter::OnND(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) nd_);
   }
   else if (eAct == MM::AfterSet)
   {
      string ts;
      pProp->Get(ts);
      std::istringstream os(ts);
      os >> nd_;
      if (curMode_ == g_NDMode) {
         SetND(nd_);
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~
#ifdef DefineShutterOnTenDashTwo
ShutterOnTenDashTwo::ShutterOnTenDashTwo(const char* name, int id) :
initialized_(false), 
id_(id), 
name_(name), 
answerTimeoutMs_(500), 
curMode_(g_FastMode)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Sutter Lambda shutter adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ShutterOnTenDashTwo::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay();
   //UpdateStatus();
}

ShutterOnTenDashTwo::~ShutterOnTenDashTwo()
{
   Shutdown();
}

void ShutterOnTenDashTwo::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ShutterOnTenDashTwo::Initialize()
{
   if (initialized_) 
      return DEVICE_OK;


 newGlobals(port_);

   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &ShutterOnTenDashTwo::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Shutter mode
   // ------------
   pAct = new CPropertyAction (this, &ShutterOnTenDashTwo::OnMode);
   vector<string> modes;
   modes.push_back(g_FastMode);
   modes.push_back(g_SoftMode);

   CreateProperty(g_ShutterModeProperty, g_FastMode, MM::String, false, pAct);
   SetAllowedValues(g_ShutterModeProperty, modes);



   // Transfer to On Line
   unsigned char setSerial = (unsigned char)238;
   ret = WriteToComPort(port_.c_str(), &setSerial, 1);
   if (DEVICE_OK != ret)
      return ret;

   // set initial values
   SetProperty(g_ShutterModeProperty, curMode_.c_str());

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;


   // Needed for Busy flag
   changedTime_ = GetCurrentMMTime();

   initialized_ = true;

   return DEVICE_OK;
}

int ShutterOnTenDashTwo::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int ShutterOnTenDashTwo::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int ShutterOnTenDashTwo::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
int ShutterOnTenDashTwo::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
* Sends a command to Lambda through the serial port.
*/
bool ShutterOnTenDashTwo::SetShutterPosition(bool state)
{
   bool ret = true;
   unsigned char command;
   if (id_ == 0)
   {
      // shutter A
      command = state ? 170 : 172;
   }
   else
   {
      // shutter B
      command = state ? 186 : 188;
   }

   // wait if the device is busy
   MMThreadGuard g(*(::gplocks_[port_]));

   if( ::g_Busy[port_])
      LogMessage("busy entering SetShutterPosition",true);

   MM::MMTime startTime = GetCurrentMMTime();
   while (::g_Busy[port_] && (GetCurrentMMTime() - startTime) < (g_busyTimeoutMs * 1000.0) )
   {
      CDeviceUtils::SleepMs(10);
   }

   if(g_Busy[port_] )
      LogMessage(std::string("Sequence error, port ") + port_ + std::string("was busy!"), false);

   g_Busy[port_] = true;

   unsigned char msg[2];
   msg[0] = command;
   msg[1] = 13; // CR

   (void)PurgeComPort(port_.c_str());

   // send command
   if (DEVICE_OK != WriteToComPort(port_.c_str(), msg, 1))
      return false;

   // Start timer for Busy flag
   changedTime_ = GetCurrentMMTime();

   // block/wait for acknowledge, or until we time out;
   unsigned char answer = 0;
   unsigned long read;
   startTime = GetCurrentMMTime();

   // first we will see the echo
   startTime = GetCurrentMMTime();
   // read the response
   for(;;)
   {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &answer, 1, read))
      {
         LogMessage("ReadFromComPort failed", false);
         return false;
      }
      if (answer == command)
      {
         ret = true;
         break;
      }
      else if (answer == 13) // CR
      {
         LogMessage("error, command was not echoed!", false);
         break;
      }
      else if( answer != 0)
      {
         std::ostringstream bufff;
         bufff << answer;
         LogMessage("unexpected response: " + bufff.str(), false);
      }
      MM::MMTime del2 = GetCurrentMMTime() - startTime;
      if( 1000.0 * answerTimeoutMs_ < del2.getUsec())
      {
         std::ostringstream bufff;
         bufff << "answer timeout after " << del2.getUsec() << " microsec";
         LogMessage(bufff.str(),false);
         break;
      }
   }


   startTime = GetCurrentMMTime();
   answer = 0;
   // now look for a 13
   for(;;){
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &answer, 1, read))
      {
         LogMessage("ReadFromComPort failed", false);
         return false;
      }
      if (answer == 13) // CR
      {
         ret = true;
         break;
      }
      if( 0 < read)
      {
         LogMessage("error, extraneous response " + std::string((char*)&answer), false);
      }
      if( 1000.0 * answerTimeoutMs_ < (GetCurrentMMTime() - startTime).getUsec())
      {
         std::ostringstream bufff;
         bufff << answerTimeoutMs_*2. << " msec";
         LogMessage("answer timeout after " + bufff.str(),false);
         break;
      }
   }

   g_Busy[port_] = false;

   return ret;
}

bool ShutterOnTenDashTwo::Busy()
{

   {
      std::auto_ptr<MMThreadGuard> pg ( new MMThreadGuard(*(::gplocks_[port_])));
      if(::g_Busy[port_])
         return true;
   }

   if (GetDelayMs() > 0.0) {
      MM::MMTime interval = GetCurrentMMTime() - changedTime_;
      MM::MMTime delay(GetDelayMs()*1000.0);
      if (interval < delay ) {
         return true;
      }
   }

   return false;
}

bool ShutterOnTenDashTwo::ControllerBusy()
{

   return g_Busy[port_];
}

bool ShutterOnTenDashTwo::SetShutterMode(const char* mode)
{
   unsigned char msg[3];
   //msg[0] = 0;

   if (strcmp(mode, g_FastMode) == 0)
      msg[0] = 220;
   else if (strcmp(mode, g_SoftMode) == 0)
      msg[0] = 221;
   else
      return false;

   msg[1] = (unsigned char)id_ + 1;
   msg[2] = 13; // CR

   // send command
   if (DEVICE_OK != WriteToComPort(port_.c_str(), msg, 3))
      return false;

   return true;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ShutterOnTenDashTwo::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      // apply the value
      SetShutterPosition(pos == 0 ? false : true);
   }

   return DEVICE_OK;
}

int ShutterOnTenDashTwo::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int ShutterOnTenDashTwo::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(curMode_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string mode;
      pProp->Get(mode);

      if (SetShutterMode(mode.c_str()))
         curMode_ = mode;
      else
         return ERR_UNKNOWN_SHUTTER_MODE;
   }

   return DEVICE_OK;
}

#endif


///////////////////////////////////////////////////////////////////////////////
// DG4 Devices
///////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// DG4Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

DG4Shutter::DG4Shutter() : 
initialized_(false), 
name_(g_DG4ShutterName), 
answerTimeoutMs_(500)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Sutter Lambda shutter adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &DG4Shutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   UpdateStatus();
}

DG4Shutter::~DG4Shutter()
{
   Shutdown();
}

void DG4Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int DG4Shutter::Initialize()
{
   // set property list
   // -----------------

   // State
   // -----

   newGlobals(port_);
   
   CPropertyAction* pAct = new CPropertyAction (this, &DG4Shutter::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Delay
   // -----
   pAct = new CPropertyAction (this, &DG4Shutter::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   SetProperty(MM::g_Keyword_State, "0");

   initialized_ = true;

   return DEVICE_OK;
}

int DG4Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int DG4Shutter::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int DG4Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
int DG4Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
* Sends a command to Lambda through the serial port.
*/
bool DG4Shutter::SetShutterPosition(bool state)
{
   unsigned char command;

   if (state == false)
   {
      command = 0; // close
   }
   else
   {
      command = (unsigned char)g_DG4Position; // open
   }
   if (DEVICE_OK != WriteToComPort(port_.c_str(), &command, 1))
      return false;

   unsigned char answer = 0;
   unsigned long read;
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime now = startTime;
   MM::MMTime timeout (answerTimeoutMs_ * 1000);
   bool eol = false;
   bool ret = false;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &answer, 1, read))
         return false;
      if (answer == command)      {
         ret = true;
         g_DG4State = state;
      }
      if (answer == 13)
         eol = true;
      now = GetCurrentMMTime();
   }
   while(!(eol && ret) && ( (now - startTime)  < timeout) );

   if (!ret)
      LogMessage("Shutter read timed out", true);

   return ret;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DG4Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      // apply the value
      SetShutterPosition(pos == 0 ? false : true);
   }

   return DEVICE_OK;
}

int DG4Shutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int DG4Shutter::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// DG4Wheel implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

DG4Wheel::DG4Wheel() :
   initialized_(false),
   numPos_(13),
   name_(g_DG4WheelName),
   curPos_(0),
   answerTimeoutMs_(500)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
   SetErrorText(ERR_INVALID_SPEED, "Invalid speed setting. You must enter a number between 0 to 7.");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Sutter Lambda filter wheel adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &DG4Wheel::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   UpdateStatus();
}

DG4Wheel::~DG4Wheel()
{
   Shutdown();
}

void DG4Wheel::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool DG4Wheel::Busy()
{
   // TODO: Implement as for shutter and wheel (ASCII 13 is send as a completion message)
   return false;
}

int DG4Wheel::Initialize()
{

	newGlobals(port_);

   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &DG4Wheel::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Delay
   // -----
   pAct = new CPropertyAction (this, &DG4Wheel::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Busy
   // -----
   // NOTE: in the lack of better status checking scheme,
   // this is a kludge to allow resetting the busy flag.  
   pAct = new CPropertyAction (this, &DG4Wheel::OnBusy);
   ret = CreateProperty("Busy", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (unsigned i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%d", i);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   unsigned char setSerial = (unsigned char)238;
   ret = WriteToComPort(port_.c_str(), &setSerial, 1);
   if (DEVICE_OK != ret)
      return ret;

   SetProperty(MM::g_Keyword_State, "0");

   initialized_ = true;

   return DEVICE_OK;
}

int DG4Wheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/**
* Sends a command to Lambda through the serial port.
*/
bool DG4Wheel::SetWheelPosition(unsigned pos)
{
   unsigned char command = (unsigned char) pos;

   if (g_DG4State == false)
      return true; // assume that position has been set (shutter is closed)

   // send command
   if (DEVICE_OK != WriteToComPort(port_.c_str(), &command, 1))
      return false;

   // block/wait for acknowledge, or until we time out;
   unsigned char answer = 0;
   unsigned long read;
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime now = startTime;
   MM::MMTime timeout (answerTimeoutMs_ * 1000);
   bool eol = false;
   bool ret = false;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), &answer, 1, read))
         return false;
      if (answer == command)
         ret = true;
      if (answer == 13)
         eol = true;
      now = GetCurrentMMTime();
   } while(!(eol && ret) && ( (now - startTime)  < timeout) );

   if (!ret)
      LogMessage("Wheel read timed out", true);

   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DG4Wheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      //check if we are already in that state
      if ((unsigned)pos == curPos_)
         return DEVICE_OK;

      if (pos >= (long)numPos_ || pos < 0)
      {
         pProp->Set((long)curPos_); // revert
         return ERR_UNKNOWN_POSITION;
      }

      // try to apply the value
      if (!SetWheelPosition(pos))
      {
         pProp->Set((long)curPos_); // revert
         return ERR_SET_POSITION_FAILED;
      }
      curPos_ = pos;
      g_DG4Position = curPos_;
   }

   return DEVICE_OK;
}

int DG4Wheel::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int DG4Wheel::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(this->GetDelayMs());
   }
   else if (eAct == MM::AfterSet)
   {
      double delay;
      pProp->Get(delay);
      this->SetDelayMs(delay);
   }

   return DEVICE_OK;
}

int DG4Wheel::OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      MMThreadGuard g(*(::gplocks_[port_]));
      pProp->Set(g_Busy[port_] ? 1L : 0L);
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard g(*(::gplocks_[port_]));
      long b;
      pProp->Get(b);
      g_Busy[port_] = !(b==0);
   }

   return DEVICE_OK;
}

