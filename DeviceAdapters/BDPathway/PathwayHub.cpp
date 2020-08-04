///////////////////////////////////////////////////////////////////////////////
// FILE:          PathwayHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BDPathway hub module. Required for operation of all 
//                BDPathway devices
//                
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 01/01/2020
//
//                Based on the Nikon TE2000 adapter by
//                Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id$
//

#define _CRT_SECURE_NO_DEPRECATE

#include "PathwayHub.h"
#include "assert.h"
#include <cstdio>
#include <memory.h>
#include <sstream>
#include <iostream>
#include "../../MMDevice/ModuleInterface.h"

using namespace std; 

PathwayHub::PathwayHub()
{
   commandMode_ = ""; // set sync mode as default
   // to run microscope in the async mode use: commandMode_ = "f";

   // TODO: "f" mode not fully tested

   expireTimeUs_ = 5000000; // each command will finish within 5sec
   // TODO: 5sec is stated in the Nikon manual as the max timeout. This may not be true.

   ClearRcvBuf();
}

PathwayHub::~PathwayHub()
{
}

void PathwayHub::LogError(int id, MM::Device& device, MM::Core& core, const char* functionName)
{
   ostringstream os;
   char deviceName[MM::MaxStrLength];
   device.GetName(deviceName);
   os << "Error " << id << ", " << deviceName << ", " << functionName << endl;
   core.LogMessage(&device, os.str().c_str(), false);
}


///////////////////////////////////////////////////////////////////////////////
// Detecting devices
///////////////////////////////////////////////////////////////////////////////

bool PathwayHub::IsComponentMounted(MM::Device& device, MM::Core& core, const char* deviceCode)
{
   long numRetries = 3;
   long counter = 0;
   int mountingStatus = 0;

   //FIXME I saw some communication errors, so worth trying a couple of times just to make sure...
   for (counter; counter<numRetries; counter++)
   {
      ExecuteCommand(device, core, deviceCode);
      string value;
      ParseResponse(device, core, value);

      //These can be any value (e.g. filterwheel position).
      //However, -1 means not present (XXX or not initialized?)
      //If the response cannot be converted to a number, is it 0? In any case, not what we want.
      mountingStatus = atoi(value.c_str());
      if (mountingStatus > 0)
         break;
   }

   if (counter>0 && mountingStatus>0)
   {
      ostringstream os;
      os << "IsComponentMounted: Succeeded after trying " << counter << " times.";
      core.LogMessage(&device, os.str().c_str(), true);
   }

   return mountingStatus > 0;
}

///////////////////////////////////////////////////////////////////////////////
// Every wheel device is controlled in the same way
// Send MX,pos -- X is one of the devices, pos is a number between 1 and ...
// To read, send mX -> mX,pos to read but only seems to work with two state dev
///////////////////////////////////////////////////////////////////////////////

int PathwayHub::SetPosition(MM::Device& device, MM::Core& core, char deviceId, int pos)
{
   ostringstream os;
   os << 'M' << deviceId << ',' << pos;

   // send command
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse response
   /*
   string value;
   core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   ret = ParseResponse(device, core, value);
   if (ret != DEVICE_OK)
      return ret;
      */

   /*
   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
      */
   return DEVICE_OK;
}

int PathwayHub::GetPosition(MM::Device& device, MM::Core& core, char deviceId, int& pos)
{
   ostringstream os;
   os << 'm' << deviceId;

   // send command
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   if (strlen(rcvBuf_) < 3)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   // parse the response
   string value;
   ret = ParseResponse(device, core, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());

   return DEVICE_OK;
}

bool PathwayHub::IsDeviceBusy(MM::Device& /* device */, MM::Core& /* core */)
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Focus commands
///////////////////////////////////////////////////////////////////////////////

int PathwayHub::SetFocusPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "TaZ";
   ostringstream os;
   os << command << pos ;

   // send command
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   /*
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   */
   return DEVICE_OK;
}

bool PathwayHub::IsFocusBusy(MM::Device& /* device */, MM::Core& /* core */)
{
   return false;
}

int PathwayHub::GetFocusPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "tZ";
   int ret = ExecuteCommand(device, core, command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());
   return DEVICE_OK;
}

int PathwayHub::GetFocusStepSizeNm(MM::Device& device, MM::Core& core, long& zstep)
{
   const char* Zcommand = "gtZ";
   string value;

   //send command to get the Z step size
   int ret = ExecuteCommand(device, core, Zcommand);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response (response comes in microns, want nm)
   ret = ParseResponse(device, core, value);
   zstep = (long)(atof(value.c_str()) * 1000.);

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// XY Stage commands
///////////////////////////////////////////////////////////////////////////////

int PathwayHub::SetXYPosition(MM::Device& device, MM::Core& core, long xpos, long ypos)
{
   const char* command = "TaX";
   ostringstream os;
   os << command << xpos << ",Y" << ypos;

   // send command
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   /*
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   */
   return DEVICE_OK;
}

bool PathwayHub::IsXYStageBusy(MM::Device& /* device */, MM::Core& /* core */)
{
   return false;
}

int PathwayHub::GetXYPosition(MM::Device& device, MM::Core& core, long& xpos, long& ypos)
{
   const char* Xcommand = "tX";
   const char* Ycommand = "tY";
   string value;

   //send command to get the X position
   int ret = ExecuteCommand(device, core, Xcommand);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   ret = ParseResponse(device, core, value);
   xpos = atoi(value.c_str());

   //send command to get the Y position
   ret = ExecuteCommand(device, core, Ycommand);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   ret = ParseResponse(device, core, value);
   ypos = atoi(value.c_str());

   return DEVICE_OK;
}

int PathwayHub::GetXYStepSizeNm(MM::Device& device, MM::Core& core, long& xstep, long& ystep)
{
   const char* Xcommand = "gtX";
   const char* Ycommand = "gtY";
   string value;

   //send command to get the X step size
   int ret = ExecuteCommand(device, core, Xcommand);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response (response comes in microns, want nm)
   ret = ParseResponse(device, core, value);
   xstep = (long)(atof(value.c_str()) * 1000.);

   //send command to get the Y step size
   ret = ExecuteCommand(device, core, Ycommand);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response (response comes in microns, want nm)
   ret = ParseResponse(device, core, value);
   ystep = (long)(atof(value.c_str()) * 1000.);

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void PathwayHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Use this method to initialize or release the BDPathway.
 * TODO find the shortest sequence to achieve initialisation / release
 */

int PathwayHub::ExecuteSequence(MM::Device& device, MM::Core& core, const char **sequence)
{
   int i = 0;
   int ret = DEVICE_OK;

   while(!(sequence[i][0] == 0)) {
      //not waiting for an answer...
      ret = ExecuteCommand(device, core, sequence[i]);

      if (ret != DEVICE_OK)
         break;

      i += 1;
   }
   return ret;
}

/**
 * Returns the current firmware version. FIXME I think.
 * I don't actually know if gfWS means firmware, only that 1.01 sounds like a plausible answer.
 */
int PathwayHub::GetVersion(MM::Device& device, MM::Core& core, std::string& ver)
{
   int ret = ExecuteCommand(device, core, "gfWS");
   if (ret != DEVICE_OK)
      return ret;

   ver = string(rcvBuf_+4);
   return DEVICE_OK;
}

/**
 * Returns the hub model (FIXME, I have no idea if the answer to this is the model type,
 * only that this particular function is called after the three strings that serve as initialization).
 */
int PathwayHub::GetModelType(MM::Device& device, MM::Core& core, std::string& model)
{
   is855_ = false;
   string value = "435 - ";

   //Assuming shutter B is only available on an 855...
   if ( IsComponentMounted(device, core, "mF") )
   {
      is855_ = true;
      value = "855 - ";
   }

   int ret = ExecuteCommand(device, core, "rd");
   if (ret != DEVICE_OK)
      return ret;

   model = value + string(rcvBuf_);
   return DEVICE_OK;
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool PathwayHub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port. By default, expect a response. 
 * A couple of initialization commands don't return anything (R0, C1) whereas R1 does.
 * Does that mean that the hardware associated with R1 is available, but not the hardware
 * associated with R0 and C1? FIXME I don't know.
 */
int PathwayHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* command)
{
   return ExecuteCommand(device, core, command, true);
}

int PathwayHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* command, bool expectResponse)
{
   //More than 3 times would be beating a dead horse...
   int ret = DEVICE_OK;
   int numRetries = 5;
   long delayMs = 50;
   long timeoutMs = 5000;

   for (int i=0; i<numRetries; i++)
   {
      // empty the Rx serial buffer before sending command
      FetchSerialData(device, core);
      ClearRcvBuf();

      // send command
      ret = core.SetSerialCommand(&device, port_.c_str(), command, "\r");

      if (ret != DEVICE_OK) // || !expectResponse)
      {
         LogError(ret, device, core, "ExecuteCommand-SetSerialCommand");
	 break;
      }
  
      //R1 seems to give an answer on my machine. But not R0,C0,C1
      //FIXME, what happens if we sent a wrong command and there is an answer?
      if (!expectResponse || strcmp(command,"R0")==0 || strcmp(command,"C0")==0 || strcmp(command,"C1")==0)
         break;

      // get response
      // ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   
      // Keep on trying until we have an answer or 5 seconds passed
      // keep track of how often we tried
      int counter = 0;

      MM::MMTime startTime = core.GetCurrentMMTime();
      do
      {
         // Wait 20 ms before the answer and in between each try
         CDeviceUtils::SleepMs(delayMs);

         ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
         if (ret == DEVICE_OK)
            break;

         LogError(ret, device, core, "ExecuteCommand-GetSerialAnswer");
	 counter++;
      } while( (core.GetCurrentMMTime() - startTime).getMsec() < timeoutMs);

      // Log something...
      if (counter > 0)
      {
         ostringstream os;
         os << "ExecuteCommand-GetSerialAnswer: ";

         if (ret == DEVICE_OK)
            os << "Succeeded";
         else
            os << "Failed";
	 os << " reading from serial port after trying " << counter << " times.";
         core.LogMessage(&device, os.str().c_str(), true);
      }

      //An error occured, lets clear the buffer and try again...
      if (ret != DEVICE_OK)
         continue;

      //O-Length? wasn't expecting an answer, then...
      if (strlen(rcvBuf_) == 0)
         break;

      //Now compare....
      string strBuffer;
      strBuffer += rcvBuf_;

      //string strCommand;
      //strCommand += command;

      //Check if the response starts with the command
      if (strBuffer.rfind(command,0) != 0)
      {
         ostringstream os;
         os << "ExecuteCommand-SerialMismatch: sent \'" << command << "\' received \'" << strBuffer << "\'";
         core.LogMessage(&device, os.str().c_str(), true);
	 continue;
      }

      //All done, we can break out!
      break;
   }

   return ret;
}

/**
 * Interprets the string returned from the microscope.
 */
int PathwayHub::ParseResponse(MM::Device& /* device */, MM::Core& /* core */, string& value)
{
    //On the BDPathway, a comma separates the command part from the response part.
    //However, these do not have a comma: "tX","tY","tZ"
    //
    value = string(rcvBuf_);

    string::size_type pos_end;
    unsigned int index;

    pos_end = value.find_first_of(',');
    if (pos_end == string::npos) {
        index = 2;
        //TODO check if length of response > 2
    }
    else {
        index = (unsigned int)(pos_end)+1;
    }
    value = value.substr(index);

    /*
    ostringstream os;
    os << "Substring is \'" << value << "\'" << endl;
    core.LogMessage(&device, os.str().c_str(), false);
    */

    return DEVICE_OK;
}

/**
 * Gets characters from the virtual serial port and stores them in the receive buffer.
 */
void PathwayHub::FetchSerialData(MM::Device& device, MM::Core& core)
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

/**
 * Checks the command queue for commands that are still processing - waiting for the terminating sequence.
 * This method is critical for async mode of operation. In sync mode it doesn't do much since there will not be
 * any commands waiting in queue for confirmating.
 */
bool PathwayHub::IsCommandWaiting(const char* command, MM::Device& device, MM::Core& core)
{
   // clean-up the queue from the expired commands
   multimap<string, long>::iterator it;
   if (waitingCommands_.size() > 0)
   {
      cout << "command que:" << endl;
      for (it = waitingCommands_.begin(); it!=waitingCommands_.end(); /* increment ommited on purpose */)
      {
         long timeDif = core.GetClockTicksUs(&device) - it->second;
         if (timeDif < 0 || timeDif > expireTimeUs_)
         {
            // remove command from the queue
            cout << "Erasing due to timeout " << it->first << ", " << it->second << ", " << timeDif << endl;
            waitingCommands_.erase(it++);
         }
         else
         {
            cout << it->first << ", " << it->second << endl;
            ++it;
         }
      }
   }

   it = waitingCommands_.find(command);
   if (it != waitingCommands_.end()) {
      // command found => device is busy
      return true;
   }

   // command not found in the queue => device in not busy
   return false;
}

