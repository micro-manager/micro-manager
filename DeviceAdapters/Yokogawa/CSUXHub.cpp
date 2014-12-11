///////////////////////////////////////////////////////////////////////////////
// FILE:          CSUXHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CSUX hub module. Required for operation of all 
//                CSUX devices
//                
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 02/03/2007
// BASED ON:      TEHub.cpp by Nenad Amodaj
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                All rights reserved
//
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
                                                                                     
#define _CRT_SECURE_NO_DEPRECATE

#include "CSUXHub.h"
#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>

#include "../../MMDevice/DeviceUtils.h"

using namespace std;

CSUXHub::CSUXHub ()
{
   ClearRcvBuf();
}

CSUXHub::~CSUXHub()
{
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

/*
 * Set FilterWheel Position
 * TODO: Make sure that CSUX can handle commands send in sequence without delay
 */
int CSUXHub::SetFilterWheelPosition(MM::Device& device, MM::Core& core, long wheelNr, long pos)
{
   // Set Filter Wheel 
   ostringstream os;
   os << "FW_POS, " << wheelNr << ", " << pos;
   bool succeeded = false;
   int counter = 0;
   // try up to 10 times, wait 50 ms in between tries
   int ret = DEVICE_OK;
   while (!succeeded && counter < 10)
   {
      ret = ExecuteCommand(device, core, os.str().c_str());
      if (ret != DEVICE_OK)
         return ret;
      ret = GetAcknowledgment(device,core);
      if (ret != DEVICE_OK)  {
         CDeviceUtils::SleepMs(50);
         counter++;
      } else
         succeeded = true;
   }
   if (!succeeded)
       return ret;

   return DEVICE_OK;
}

/*
 * Queries CSU for current filter wheel position
 */
int CSUXHub::GetFilterWheelPosition(MM::Device& device, MM::Core& core, long wheelNr, long& pos)
{
   ClearAllRcvBuf(device, core);
   ostringstream os;
   os << "FW_POS, " << wheelNr  << ", ?";
   int ret = ExecuteCommand(device, core, os.str().c_str());
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;

   pos = atol(rcvBuf_);
   return DEVICE_OK;
}
    
 
/*
 * Sets speed of filter wheel (0-3)
 */
int CSUXHub::SetFilterWheelSpeed(MM::Device& device, MM::Core& core, long wheelNr, long speed)
{
   // Set Filter Wheel  Speeds
   ostringstream os;
   os << "FW_SPEED, " << wheelNr << ", " << speed;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
   ret = GetAcknowledgment(device,core);
   if (ret != DEVICE_OK) 
      return ret;

   return DEVICE_OK;
}

/*
 * Gets speed of filter wheel (0-3)
 */
int CSUXHub::GetFilterWheelSpeed(MM::Device& device, MM::Core& core, long wheelNr, long& speed)
{
   ClearAllRcvBuf(device, core);
   ostringstream os;
   os << "FW_SPEED, " << wheelNr  << ", ?";
   int ret = ExecuteCommand(device, core, os.str().c_str());
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;
   speed = atol(rcvBuf_);
   return DEVICE_OK;
}

/*
 * 
 */
int CSUXHub::SetDichroicPosition(MM::Device& device, MM::Core& core, long dichroic)
{
   ostringstream os;
   os << "DM_POS, " <<  dichroic ;

   bool succeeded = false;
   int counter = 0;
   // try up to 10 times, wait 50 ms in between tries
   int ret = DEVICE_OK;
   while (!succeeded && counter < 10)
   {
      ret = ExecuteCommand(device, core, os.str().c_str());
      if (ret != DEVICE_OK)
         return ret;
      ret = GetAcknowledgment(device,core);
      if (ret != DEVICE_OK)  {
         CDeviceUtils::SleepMs(50);
         counter++;
      } else
         succeeded = true;
   }
   if (!succeeded)
       return ret;

   return DEVICE_OK;
}

/*
 * 
 */
int CSUXHub::GetDichroicPosition(MM::Device& device, MM::Core& core, long &dichroic)
{
   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "DM_POS, ?");
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;
   dichroic = atol(rcvBuf_);

   return DEVICE_OK;
}


/*
 * 
 */
int CSUXHub::SetShutterPosition(MM::Device& device, MM::Core& core, int pos)
{
   ostringstream os;
   os << "SH";
   if (pos == 0)
      os << "O";
   else 
      os << "C";
   int ret =  ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
   return GetAcknowledgment(device, core);
}

/*
 * 1 = closed
 * 0 = open
 */
int CSUXHub::GetShutterPosition(MM::Device& device, MM::Core& core, int& pos)
{
   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "SH, ?");
   // analyze what comes back:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   if (strstr(rcvBuf_, "CLOSE") != 0)
      pos = 1;
   pos = 0;

   return DEVICE_OK;
}

/*
 * Block till the CSUX acknowledges receipt of command (at which point it should be up 
 * to speed)
 */
int CSUXHub::SetDriveSpeed(MM::Device& device, MM::Core& core, int pos)
{
   // Note Speed must be 1500-5000, this should be asserted in calling function
   ostringstream os;
   os << "MS, " << pos;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // TODO: wait till the drive reaches speed, try 10 times before giving up
   ret = GetAcknowledgment(device, core);

   return ret;
}

/*
 * Reports the current drive speed
 */
int CSUXHub::GetDriveSpeed(MM::Device& device, MM::Core& core, long& speed)
{
   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "MS, ?");
   // analyze what comes back:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;

   speed = atol(rcvBuf_);
   return DEVICE_OK;
}

/*
 * Reports the maximum drive speed available from this model
 */
int CSUXHub::GetMaxDriveSpeed(MM::Device& device, MM::Core& core, long& maxSpeed)
{
   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "MS_MAX, ?");
   // analyze what comes back:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;

   maxSpeed = atol(rcvBuf_);
   return DEVICE_OK;
}
   
/*
 * Sets the drive speed to work well with exposure time
 */
int CSUXHub::SetAutoAdjustDriveSpeed(MM::Device& device, MM::Core& core, double exposureMs)
{
   ClearAllRcvBuf(device, core);
   ostringstream os;
   os << "MS_ADJUST, " << exposureMs;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return  GetAcknowledgment(device, core);
}

/*
 * 
 */
int CSUXHub::RunDisk(MM::Device& device, MM::Core& core, bool run)
{
   ostringstream os;
   os << "MS_";
   if (run)
      os << "RUN";
   else
      os << "STOP";

   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
   // TODO: wait for ackowledgement?
   return  GetAcknowledgment(device, core);
}

/*
 * Set BrightFieldPort
 * 0 = off
 * 1 = on
 * TODO: Make sure that CSUX can handle commands send in sequence without delay
 */
int CSUXHub::SetBrightFieldPort(MM::Device& device, MM::Core& core, int pos)
{
   std::string cmd = "BF_ON";
   if (pos == 0)
      cmd = "BF_OFF";

   bool succeeded = false;
   int counter = 0;
   // try up to 10 times, wait 50 ms in between tries
   int ret = DEVICE_OK;
   while (!succeeded && counter < 10)
   {
      ret = ExecuteCommand(device, core, cmd.c_str());
      if (ret != DEVICE_OK)
         return ret;
      ret = GetAcknowledgment(device,core);
      if (ret != DEVICE_OK)  {
         CDeviceUtils::SleepMs(50);
         counter++;
      } else
         succeeded = true;
   }
   if (!succeeded)
       return ret;

   return DEVICE_OK;
}

/*
 * Queries CSU for current BrightFieldPort position
 * 0 - Confocal
 * 1 - BrightfieldPort
 */
int CSUXHub::GetBrightFieldPort(MM::Device& device, MM::Core& core, int& pos)
{
   ClearAllRcvBuf(device, core);
   std::string cmd;
   cmd = "BF_POS, ?";
   int ret = ExecuteCommand(device, core, cmd.c_str());
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   ret = Acknowledge();
   if (ret != DEVICE_OK)
      return ret;
   std::ostringstream os;
   os << "Get BrightFieldPort answer is: " << rcvBuf_;
core.LogMessage(&device, os.str().c_str(), false);
   if (strstr(rcvBuf_, "OFF") != 0)
      pos = 0;
   else
      pos = 1;
   return DEVICE_OK;
}
    
 
///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void CSUXHub::ClearAllRcvBuf(MM::Device& device, MM::Core& core)
{
   // Read whatever has been received so far:
   unsigned long read;
   core.ReadFromSerial(&device, port_.c_str(), (unsigned char*)rcvBuf_, RCV_BUF_LENGTH,  read);
   // Delete it all:
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void CSUXHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool CSUXHub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port.
 */
int CSUXHub::ExecuteCommand(MM::Device& device, MM::Core& core,  const char* command)
{
   // empty the Rx serial buffer before sending command
   //FetchSerialData(device, core);

   ClearAllRcvBuf(device, core);

   // send command
   return core.SetSerialCommand(&device, port_.c_str(), command, "\r");
  
}


/*
 * An 'A' acknowledges receipt of a command, ('N' means it is not understood)
 * Function is not really appropriately named
 */
int CSUXHub::GetAcknowledgment(MM::Device& device, MM::Core& core)
{
   // block until we get a response
   int ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
       return ret;
   return Acknowledge();
}

int CSUXHub::Acknowledge()
{
   if (rcvBuf_[strlen(rcvBuf_) - 1] == 'N')
      return ERR_NEGATIVE_RESPONSE;;
   if (rcvBuf_[strlen(rcvBuf_) - 1] != 'A')
      return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}


/**
 * Interprets the string returned from the microscope.
 */
/*
int CSUXHub::ParseResponse(const char* cmd, string& value)
{
   if (strlen(rcvBuf_) < 4)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   char cmdIdBuf[4];
   memcpy(cmdIdBuf, rcvBuf_+1, 3);
   cmdIdBuf[3] = 0;

   if (strcmp(cmdIdBuf, cmd) != 0)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   value = rcvBuf_ + 4;
   if (rcvBuf_[0] == 'n' && strlen(rcvBuf_) > 4)
   {
      return atoi(value.c_str()); // error occured
   }
   else if (rcvBuf_[0] == 'o' || rcvBuf_[0] == 'a' || rcvBuf_[0] == 'q')
   {
      // special processing for TRN1 commands
      if (value.substr(0,3).compare("TRN") == 0)
      {
         if (strlen(rcvBuf_) > 8)
         {
            value = rcvBuf_ + 9;
            if (rcvBuf_[5] == 'n')
               return atoi(value.c_str()); // error occured
            else if (rcvBuf_[5] == 'o' || rcvBuf_[5] == 'a' || rcvBuf_[5] == 'q')
               return DEVICE_OK;
            else
               return DEVICE_SERIAL_INVALID_RESPONSE;
         }
         else
         {
            return DEVICE_SERIAL_INVALID_RESPONSE;
         }
      }
      else
         return DEVICE_OK;
   }
   else
      return DEVICE_SERIAL_INVALID_RESPONSE;
}
*/

/**
 * Gets characters from the virtual serial port and stores them in the receive buffer.
 */
/*
void Hub::FetchSerialData(MM::Device& device, MM::Core& core)
{
   unsigned long read = 0;
   do {
      read = 0;
      core.ReadFromSerial(&device, port_.c_str(), asynchRcvBuf_, RCV_BUF_LENGTH, read);
   
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
*/


