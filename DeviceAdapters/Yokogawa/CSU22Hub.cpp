///////////////////////////////////////////////////////////////////////////////
// FILE:          CSU22Hub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CSU22 hub module. Required for operation of all 
//                CSU22 devices
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

#include "CSU22Hub.h"
#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>

using namespace std;

CSU22Hub::CSU22Hub()
{
   expireTimeUs_ = 5000000; // each command will finish within 5sec

   ClearRcvBuf();
}

CSU22Hub::~CSU22Hub()
{
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

/*
 * Set ND Filter (0=10%, 1=100% Transmission
 * Close shutter while doing this
 * TODO: Make sure that CSU22 can handle commands send in sequence without delay
 */
int CSU22Hub::SetNDFilterPosition(MM::Device& device, MM::Core& core, int pos)
{
   // Close shutter
   const char* command = "sh1";
   int ret = ExecuteCommand(device, core, command);
   if (ret != DEVICE_OK)
      return ret;

   // Set ND filter
   ostringstream os;
   os << "nd" << pos;
   ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Open shutter
   command = "sh0";
   ret = ExecuteCommand(device, core, command);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * The CSU22 does not have a method to query the current filter position or to 
 * signal that the filters have moved
 */
int CSU22Hub::SetFilterSetPosition(MM::Device& device, MM::Core& core, int filter, int dichroic)
{
   const char* command = "edb";
   ostringstream os;
   os << command <<  filter << dichroic << filter;

   // send command
   return ExecuteCommand(device, core, os.str().c_str());
}

int CSU22Hub::SetShutterPosition(MM::Device& device, MM::Core& core, int pos)
{
   ostringstream os;
   os << "sh" << pos;
   return ExecuteCommand(device, core, os.str().c_str());
}

/*
 * This is the only CSU22 command that acknowledges when desired state is a achieved
 * We set a busy flag and read the return in the busy function
 */
int CSU22Hub::SetDriveSpeedPosition(MM::Device& device, MM::Core& core, int pos)
{
   // Note Speed must be 1500-5000, this should be asserted in calling function
   ostringstream os;
   os << "ms" << pos;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   driveSpeedBusy_ = true;
   return DEVICE_OK;
}

bool CSU22Hub::IsDriveSpeedBusy(MM::Device& device, MM::Core& core)
{
   if (driveSpeedBusy_)
   {
      // if there is an "A" in the receive buffer we are done:
      unsigned long read;
      int ret = core.ReadFromSerial(&device, port_.c_str(), rcvBuf_, RCV_BUF_LENGTH,  read);
      if (ret != DEVICE_OK)
         return true;
      if ((read > 0) && *rcvBuf_ == 'A')
      {
         driveSpeedBusy_ = false;
         return false;
      } else
      return true;
   } else
      return false;
}
      
///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void CSU22Hub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}


/**
 * Buys flag. True if the command processing is in progress.
 */
bool CSU22Hub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port.
 */
int CSU22Hub::ExecuteCommand(MM::Device& device, MM::Core& core,  const char* command)
{
   // empty the Rx serial buffer before sending command
   //FetchSerialData(device, core);

   ClearRcvBuf();

   // send command
   return core.SetSerialCommand(&device, port_.c_str(), command, "\r");
  
}


/*
int CSU22Hub::GetAnswer(MM::Device& device, MM::Core& core)
{
   // get response
   return core.ReadFromSerial(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   // ReadFromSerial()
}
*/


/**
 * Interprets the string returned from the microscope.
 */
/*
int CSU22Hub::ParseResponse(const char* cmd, string& value)
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


