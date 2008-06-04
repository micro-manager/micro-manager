///////////////////////////////////////////////////////////////////////////////
// FILE:          AZHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon AZ100 hub module. Required for operation of all 
//                AZ100 microscope devices
//                
// AUTHOR:        NIco STuurman, nico@cmp.ucsf.edu, 5/22/2008
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id: AZHub.cpp 1136 2008-04-28 22:41:49Z nico $
//

#define _CRT_SECURE_NO_DEPRECATE

#include "AZHub.h"
#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>
#include "../../MMDevice/ModuleInterface.h"

using namespace std; 

AZHub::AZHub() :
   controlMode_ (manual),
   initialized_ (false)
{
   commandMode_ = "c"; // set sync mode as default

   // Not used now, we rely on the serial port time out which should be set to 5 sec...
   // Set this time out ourselves??
   // expireTimeUs_ = 5000000; //  Expect an answer within 5 sec  

   ClearRcvBuf();
}

AZHub::~AZHub()
{
}

void AZHub::LogError(int id, MM::Device& device, MM::Core& core, const char* functionName)
{
   ostringstream os;
   char deviceName[MM::MaxStrLength];
   device.GetName(deviceName);
   os << "Error " << id << ", " << deviceName << ", " << functionName << endl;
   core.LogMessage(&device, os.str().c_str(), false);
}


int AZHub::Initialize(MM::Device& device, MM::Core& core)
{
   if (!initialized_) {
      int ret =  SetControlMode(device, core, computer);
      if (ret != DEVICE_OK)
         return ret;
      initialized_ = true;
   }
   return DEVICE_OK;
}

int AZHub::Shutdown(MM::Device& device, MM::Core& core)
{
   if (initialized_) {
      int ret =  SetControlMode(device, core, manual);
      if (ret != DEVICE_OK)
         return ret;
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Hub commands
///////////////////////////////////////////////////////////////////////////////

int AZHub::SetControlMode(MM::Device& device, MM::Core& core, ControlMode pos)
{
   const char* command = "PMS";
   ostringstream os;
   os << command << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Wait until the focus drive is not busy anymore
   while (strcmp(rcvBuf_,"xXXX") == 0) {
      ret = GetAnswer(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   controlMode_ = (ControlMode) pos;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Nosepiece commands
///////////////////////////////////////////////////////////////////////////////

int AZHub::GetNosepiecePosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "DPS";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());
   return DEVICE_OK;
}

int AZHub::GetNosepieceMountingStatus(MM::Device& device, MM::Core& core, int& status)
{
   const char* command = "RCR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   status = atoi(value.c_str());
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FilterBlock commands
///////////////////////////////////////////////////////////////////////////////

int AZHub::GetFilterBlockPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "HAR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   if (strlen(rcvBuf_) < 5)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());

   return DEVICE_OK;
}

bool AZHub::IsFilterBlockBusy(MM::Device& device, MM::Core& core)
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Focus commands
///////////////////////////////////////////////////////////////////////////////

int AZHub::SetFocusPosition(MM::Device& device, MM::Core& core, int pos)
{
   if (controlMode_ != computer)
      return ERR_NEED_COMPUTER_CONTROL;

   const char* command = "SMC";
   ostringstream os;
   os << command << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Wait until the focus drive is not busy anymore
   while (strcmp(rcvBuf_,"xXXX") == 0) {
      ret = GetAnswer(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

bool AZHub::IsFocusBusy(MM::Device& device, MM::Core& core)
{
   // We do all in blocking mode (for the time being at least)
   return false;
}

int AZHub::GetFocusPosition(MM::Device& device, MM::Core& core, int& pos)
{
   if (controlMode_ != computer)
      return ERR_NEED_COMPUTER_CONTROL;

   const char* command = "SMR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());
   return DEVICE_OK;
}

int AZHub::SetFocusStepSize(MM::Device& device, MM::Core& core, int stepsize)
{
   int resolution;
   const char* command = "SJS";
   ostringstream os;
   // translate stepsize into resolution (coarse, medium, fine
   switch (stepsize) {
      case 100: resolution = 0; break;
      case 50: resolution = 1; break;
      case 25: resolution = 2; break;
      // TODO: return appropriate error code
      default: return 1; break;
   }
   os << command << resolution;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


int AZHub::GetFocusStepSize(MM::Device& device, MM::Core& core, int& stepSize)
{
   const char* command="SJR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   int resolution = atoi(value.c_str());
   switch (resolution) {
      case 0:
         stepSize = 100;
         break;
      case 1:
         stepSize = 50;
         break;
      case 2:
         stepSize = 25;
         break;
      default:
         // TODO: find the right error code:
         return 1;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Zoom position
///////////////////////////////////////////////////////////////////////////////

int AZHub::SetZoomPosition(MM::Device& device, MM::Core& core, double pos)
{
   if (controlMode_ != computer)
      return ERR_NEED_COMPUTER_CONTROL;

   const char* command = "ZZC";
   ostringstream os;
   os.precision(1);
   os << command << fixed << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Wait until the focus drive is not busy anymore
   while (strcmp(rcvBuf_,"xXXX") == 0) {
      ret = GetAnswer(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int AZHub::GetZoomPosition(MM::Device& device, MM::Core& core, double& pos)
{
   if (controlMode_ != computer)
      return ERR_NEED_COMPUTER_CONTROL;

   const char* command = "ZZR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atof(value.c_str());
   return DEVICE_OK;
}

bool AZHub::IsZoomBusy(MM::Device& device, MM::Core& core)
{
   // We do all in blocking mode (for the time being at least)
   return false;
}
///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void AZHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Returns the current firmware version.
 */
int AZHub::GetVersion(MM::Device& device, MM::Core& core, std::string& ver)
{
   const char* command = "VEN";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   ver = value;
   return DEVICE_OK;
}

/**
 * Returns the hub model.
 */
int AZHub::GetModelType(MM::Device& device, MM::Core& core, int& type)
{
   const char* command = "VTR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   type = atoi(value.c_str());
   return DEVICE_OK;
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool AZHub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port.
 */
int AZHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* type, const char* command)
{
   // empty the Rx serial buffer before sending command
   FetchSerialData(device, core);

   string strCommand(type);
   strCommand += command;

   // send command
   int ret = core.SetSerialCommand(&device, port_.c_str(), strCommand.c_str(), "\r");
   if (ret != DEVICE_OK)
   {
      LogError(ret, device, core, "ExecuteCommand-SetSerialCommand");
      return ret;
   }
  
   // >>
   core.LogMessage(&device, strCommand.c_str(), true);

   return GetAnswer(device, core);
}

int AZHub::GetAnswer(MM::Device& device, MM::Core& core)
{
   ClearRcvBuf();

   // get response
   int ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
   
   // >>
   core.LogMessage(&device, rcvBuf_, true);
   if (ret != DEVICE_OK)
   {
      LogError(ret, device, core, "ExecuteCommand-GetSerialAnswer");
      // Keep on trying until we have an answer or 5 seconds passed
      MM::MMTime maxTimeMs (5000);
      // Wait 10 ms in between each try
      int delayMs = 10;
      // keep track of how often we tried
      int counter = 0;
      bool done = false;
      MM::MMTime startTime (core.GetCurrentMMTime());
      while (!done) 
      {
         counter++;
         ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
         if (ret == DEVICE_OK)
            done = true;
         else
         {
            CDeviceUtils::SleepMs(delayMs);
            if ( (core.GetCurrentMMTime() - startTime) > maxTimeMs)
               done = true;
         }
      }
      ostringstream os;
      if (ret == DEVICE_OK)
         os << "ExecuteCommand-GetSerialAnswer: Succeeded reading from serial port after trying " << counter << "times.";
      else
         os << "ExecuteCommand-GetSreialAnswer: Failed reading from serial port after trying " << counter << "times.";

      core.LogMessage(&device, os.str().c_str(), true);
      core.LogMessage(&device, rcvBuf_, true);
      return ret;
   }

   return DEVICE_OK;
}

/**
 * Interprets the string returned from the microscope.
 */
int AZHub::ParseResponse(MM::Device& device, MM::Core& core, const char* cmd, string& value)
{
   if (strlen(rcvBuf_) < 4)
   {
      LogError(DEVICE_SERIAL_INVALID_RESPONSE, device, core, "ParseResponse-invalid response");
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   char cmdIdBuf[4];
   memcpy(cmdIdBuf, rcvBuf_+1, 3);
   cmdIdBuf[3] = 0;

   if (strcmp(cmdIdBuf, cmd) != 0)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   value = rcvBuf_ + 4;
   if (rcvBuf_[0] == 'n' && strlen(rcvBuf_) > 4)
   {
      int err = atoi(value.c_str()); // error occured
      LogError(err, device, core, "ParseResponse-device reported an error");
      return err;
   }
   else if (rcvBuf_[0] == 'o' || rcvBuf_[0] == 'a' || rcvBuf_[0] == 'q')
   {
      string trncommand(rcvBuf_ + 1);
      // special processing for TRN1 commands
      if (trncommand.length() >= 4 && trncommand.substr(0,4).compare("TRN1") == 0)
      {
         if (strlen(rcvBuf_) > 8)
         {
            value = rcvBuf_ + 9;
            if (rcvBuf_[5] == 'n')
            {
               int err = atoi(value.c_str()); // error occured
               LogError(err, device, core, "ParseResponse-device reported an error");
               return err;
            }
            else if (rcvBuf_[5] == 'o' || rcvBuf_[5] == 'a' || rcvBuf_[5] == 'q')
               return DEVICE_OK;
            else
            {
              LogError(DEVICE_SERIAL_INVALID_RESPONSE, device, core, "ParseResponse-invalid response");
              return DEVICE_SERIAL_INVALID_RESPONSE;
            }
         }
         else
         {
            LogError(DEVICE_SERIAL_INVALID_RESPONSE, device, core, "ParseResponse-invalid response");
            return DEVICE_SERIAL_INVALID_RESPONSE;
         }
      }
      else
         // normal commands
         return DEVICE_OK;
   }
   else
   {
      LogError(DEVICE_SERIAL_INVALID_RESPONSE, device, core, "ParseResponse-invalid response");
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }
}

/**
 * Gets characters from the virtual serial port and stores them in the receive buffer.
 */
void AZHub::FetchSerialData(MM::Device& device, MM::Core& core)
{
   unsigned long read = 0;
   do {
      read = 0;
      core.ReadFromSerial(&device, port_.c_str(), (unsigned char*)asynchRcvBuf_, RCV_BUF_LENGTH, read);
   
      // enter serial data into the buffer
      for (unsigned long i=0; i<read; i++)
      {
         answerBuf_.push_back(asynchRcvBuf_[i]);
         size_t size = answerBuf_.size();
         if (size >= 2 && answerBuf_[size-2] == '\r' && answerBuf_[size-1] == '\n')
         {
            if (size-2 >= 4 && (answerBuf_[0] == 'o' || answerBuf_[0] == 'n'))
            {
               // proper command
               answerBuf_[4] = '\0';
               string cmdresp = (char*)(&answerBuf_[1]);
               // >>
               //if (read > 0)
               //   core.LogMessage(&device, cmdresp.c_str(), true);

               // remove command from the wait list
               multimap<string, long>::iterator it;
               it = waitingCommands_.find(cmdresp);
               if (it != waitingCommands_.end())
               {
                  // command found -> remove it from the queue
                  cout << "Response received " << it->first << ", " << it->second << endl;
                  waitingCommands_.erase(it);
               }
               else
                  // shouldn't happen
                  core.LogMessage(&device, "Received confirmation for command that wasn't sent", true);
            }
            else
            {
               // shouldn't happen
               core.LogMessage(&device, "Unrecognized response received in async mode", true);
            }
            answerBuf_.clear();
         }
      }
   } while (read > 0);
}
