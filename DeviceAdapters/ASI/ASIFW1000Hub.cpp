///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIFW1000Hub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASIFW1000 hub module. Required for operation of all 
//                ASIFW1000 devices
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

#include "assert.h"
#include <memory.h>
#include <sstream>
#include <iostream>
#include <string>
#include "ASIFW1000Hub.h"

using namespace std;

ASIFW1000Hub::ASIFW1000Hub ()
{
   expireTimeUs_ = 5000000; // each command will finish within 5sec

   ClearRcvBuf();
}

ASIFW1000Hub::~ASIFW1000Hub()
{
}


///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

int ASIFW1000Hub::GetVersion(MM::Device& device, MM::Core& core, char* version)
{
   int ret = ExecuteCommand(device, core, "VN ");
   if (ret != DEVICE_OK)
      return ret;

   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // get substring containing version number TODO: check offset
   int i=4;
   do
   {
      i++;
      version[i-5]=rcvBuf_[i];
   }
   while (rcvBuf_[i]);

   return DEVICE_OK;
}

/*
 * 
 */
int ASIFW1000Hub::SetFilterWheelPosition(MM::Device& device, MM::Core& core, int wheelNr, int pos)
{
   if (wheelNr != activeWheel_)
      // TODO: error checking
      SetCurrentWheel(device, core, wheelNr);

   const char* command = "MP ";
   ostringstream os;
   os << command <<  pos;

   // send command
   return ExecuteCommand(device, core, os.str().c_str());
}

int ASIFW1000Hub::GetFilterWheelPosition(MM::Device& device, MM::Core& core, int wheelNr, int& pos)
{
   if (wheelNr != activeWheel_)
      SetCurrentWheel(device, core, wheelNr);

   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "MP ");
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   // TODO: error checking
   pos = rcvBuf_[strlen(rcvBuf_)-1] - '0';

   return DEVICE_OK;
}



int ASIFW1000Hub::SetCurrentWheel(MM::Device& device, MM::Core& core, int wheelNr)
{
   const char* command = "FW ";
   ostringstream os;
   os << command <<  wheelNr;

   // send command
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // make sure there were no errors:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");  
   if (ret != DEVICE_OK)                                                     
      return ret;                                                           
   int x = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if (x == wheelNr)
      return DEVICE_OK;
   else
      // TODO: return correct error code
      return 1;
}

//
//TODO: Error checking
int ASIFW1000Hub::GetNumberOfPositions(MM::Device& device, MM::Core& core,unsigned int wheelNr, unsigned int& nrPos)
{
   if (wheelNr != activeWheel_)
      SetCurrentWheel(device, core, wheelNr);

   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "NF");
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // TODO: error checking
   nrPos = rcvBuf_[strlen(rcvBuf_)-1] - '0';

   return DEVICE_OK;
}



int ASIFW1000Hub::FilterWheelBusy(MM::Device& device, MM::Core& core, bool& busy)
{
   int ret = ExecuteCommand(device, core, "?");
   if (ret != DEVICE_OK)                                                     
      return ret;                                                           

   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");  
   if (ret != DEVICE_OK)                                                     
      return ret;                                                           

   int x = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if (x < 4)
      busy = false;
   else
      busy = true;

   return DEVICE_OK;
}




int ASIFW1000Hub::OpenShutter(MM::Device& device, MM::Core& core, int shutterNr)
{
   ostringstream os;
   os << "SO " << shutterNr;
   return ExecuteCommand(device, core, os.str().c_str());
}

int ASIFW1000Hub::CloseShutter(MM::Device& device, MM::Core& core, int shutterNr)
{
   ostringstream os;
   os << "SC " << shutterNr;
   return ExecuteCommand(device, core, os.str().c_str());
}

/*
 * 1 = open
 * 0 = close
 */
int ASIFW1000Hub::GetShutterPosition(MM::Device& device, MM::Core& core, int shutterNr, bool& pos)
{
   ClearAllRcvBuf(device, core);
   ostringstream os;
   os << "SQ " << shutterNr;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   // analyze answer
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
      return ret;
   int x = rcvBuf_[strlen(rcvBuf_)-1];
   if (shutterNr==1)
      x = x << 1;
   pos = x & 1;

   return DEVICE_OK;
}

      
///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void ASIFW1000Hub::ClearAllRcvBuf(MM::Device& device, MM::Core& core)
{
   // Read whatever has been received so far:
   unsigned long read;
   int ret = core.ReadFromSerial(&device, port_.c_str(), rcvBuf_, RCV_BUF_LENGTH,  read);
   // Delete it all:
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void ASIFW1000Hub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Buys flag. True if the command processing is in progress.
 */
bool ASIFW1000Hub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port.
 */
int ASIFW1000Hub::ExecuteCommand(MM::Device& device, MM::Core& core,  const char* command)
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
int ASIFW1000Hub::GetAcknowledgment(MM::Device& device, MM::Core& core)
{
   // block until we get a response
   int ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   if (ret != DEVICE_OK)
       return ret;
   if (rcvBuf_[1]=='N')
      return 1;
   if (rcvBuf_[1]!='A')
      return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}




/**
 * Interprets the string returned from the microscope.
 */
/*
int ASIFW1000Hub::ParseResponse(const char* cmd, string& value)
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


