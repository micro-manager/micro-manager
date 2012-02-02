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
#include <cstdlib>
#include <sstream>
#include <iostream>
#include <string>
#include "ASIFW1000Hub.h"

#ifdef WIN32
#include <windows.h>
#endif

using namespace std;

ASIFW1000Hub::ASIFW1000Hub ()  :
	oldProtocol_(false),
	port_("Undefined")
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
   int ret;

   oldProtocol_ = false;
   ret = ExecuteCommand(device, core, "VN ");
   if (ret != DEVICE_OK)
      return ret;

   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
   {
      // test for older LX-4000 protocol
      oldProtocol_ = true;
      ret = ExecuteCommand(device, core, "VN");
      if (ret == DEVICE_OK)
         ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   }
   if (ret != DEVICE_OK)
      return ret;

   // get substring containing version number.  Offset is caused by echoing of the command given
   int i=3;
   do
   {
      i++;
      version[i-4]=rcvBuf_[i];
   }
   while (rcvBuf_[i]);
   version[i-3] = '\0'; // terminate the string

   // Don't know why, but we need a little break after asking for version number:
   #ifdef WIN32
   Sleep(100);
   #else
   usleep(100000);
   #endif

   if (strlen(rcvBuf_) < 3)
   {
      return ERR_NOT_CONNECTED;
   }

   return DEVICE_OK;
}

int ASIFW1000Hub::SetVerboseMode(MM::Device& device, MM::Core& core, int level)
{
   ostringstream os;
   os << "VB " <<  level;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
                          
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;

   int levelRead = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if ( level != levelRead)
      return ERR_SETTING_VERBOSE_LEVEL;

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
   int ret = ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // devices echos command and returns position
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;

   int posread = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if ( pos != posread)
      return ERR_SETTING_WHEEL;

   return DEVICE_OK;
}

int ASIFW1000Hub::GetFilterWheelPosition(MM::Device& device, MM::Core& core, int wheelNr, int& pos)
{
   if (wheelNr != activeWheel_)
      SetCurrentWheel(device, core, wheelNr);

   //ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "MP");
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;
   // TODO: error checking
   pos = rcvBuf_[strlen(rcvBuf_)-1] - '0';

   return DEVICE_OK;
}


// Gets the current wheels from the controller
// Also sets private variable activeWheel_
int ASIFW1000Hub::GetCurrentWheel(MM::Device& device, MM::Core& core, int& wheelNr)
{
   const char* command = "FW";

   // send command
   int ret = ExecuteCommand(device, core, command);
   if (ret != DEVICE_OK)
      return ret;

   // make sure there were no errors:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");  
   if (ret != DEVICE_OK)                                                     
      return ret;                                                           

   wheelNr = rcvBuf_[strlen(rcvBuf_)-1] - '0';

   if ( (wheelNr == 0) || (wheelNr == 1) )
   {
      activeWheel_ = wheelNr;
      return DEVICE_OK;
   }
   else
   {
      return ERR_UNEXPECTED_ANSWER;
   }
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
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");  
   if (ret != DEVICE_OK)                                                     
      return ret;                                                           
   int x = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if (x == wheelNr)
   {
      activeWheel_ = wheelNr;
      return DEVICE_OK;
   }
   else
   {
      return ERR_SETTING_WHEEL;
   }
}

//
//TODO: Error checking
int ASIFW1000Hub::GetNumberOfPositions(MM::Device& device, MM::Core& core, int wheelNr, int& nrPos)
{
   if (wheelNr != activeWheel_)
      SetCurrentWheel(device, core, wheelNr);

   ClearAllRcvBuf(device, core);
   int ret = ExecuteCommand(device, core, "NF");
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;

   if (strlen (rcvBuf_) < 2)
      return ERR_NO_ANSWER;

   // TODO: error checking
   nrPos = rcvBuf_[strlen(rcvBuf_)-1] - '0';
   if (! (nrPos==6 || nrPos==8))
         nrPos = 6;

   return DEVICE_OK;
}



int ASIFW1000Hub::FilterWheelBusy(MM::Device& device, MM::Core& core, bool& busy)
{ 
   busy = false;
   ClearAllRcvBuf(device, core);
   int ret = core.SetSerialCommand(&device, port_.c_str(), "?", "");
   if (ret != DEVICE_OK) {
      std::ostringstream os;
      os << "ERROR: SetSerialCommand returned error code: " << ret;
      core.LogMessage(&device, os.str().c_str(), false);
      return ret;
   }

   unsigned long read = 0;
   MM::TimeoutMs timerOut(core.GetCurrentMMTime(), 200 );
   while (read == 0 && ( !timerOut.expired(core.GetCurrentMMTime()) ) )
   {
      ret = core.ReadFromSerial(&device, port_.c_str(), (unsigned char*)rcvBuf_, 1, read);  
   }

   if (ret != DEVICE_OK) {
      std::ostringstream os;
      os << "ERROR: ReadFromSerial returned error code: " << ret;
      core.LogMessage(&device, os.str().c_str(), false);
      return ret;
   }

   if (read != 1) {
      std::ostringstream os;
      os << "ERROR: Read " << read << " characters instead of 1";
      core.LogMessage(&device, os.str().c_str(), false);
      return ERR_NO_ANSWER;
   }

   if (rcvBuf_[0] == '0' || rcvBuf_[0] == '1' || rcvBuf_[0] =='2')
      busy = false;
   else if (rcvBuf_[0] == '3')
      busy = true;

   return DEVICE_OK;
}




int ASIFW1000Hub::OpenShutter(MM::Device& device, MM::Core& core, int shutterNr)
{
   ostringstream os;
   os << "SO " << shutterNr;
   int ret =  ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
   // Shutter will answer '1', we need to read this out or there will be trouble down the line:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ASIFW1000Hub::CloseShutter(MM::Device& device, MM::Core& core, int shutterNr)
{
   ostringstream os;
   os << "SC " << shutterNr;
   int ret =  ExecuteCommand(device, core, os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
   // Shutter will answer '0', we need to read this out or there will be trouble down the line:
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

/*
 * 1 = open
 * 0 = close
 */
int ASIFW1000Hub::GetShutterPosition(MM::Device& device, MM::Core& core, int shutterNr, bool& pos)
{
   //ClearAllRcvBuf(device, core);
   ostringstream os;
   os << "SQ " << shutterNr;
   int ret = ExecuteCommand(device, core, os.str().c_str());
   // analyze answer
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
   if (ret != DEVICE_OK)
      return ret;
   string response = rcvBuf_;

   // If the answer is too short, there is likely no shutter card (we could do a better check)
   if (response.length() < 2)
   {
      return ERR_SHUTTER_NOT_FOUND;
   }

   int x = atoi(response.substr(response.length() - 2).c_str());
   // sometimes, the controller does not answer, but sends 0. ask once more
   if (x < 16)
   {
      ret = ExecuteCommand(device, core, os.str().c_str());
      ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\n\r");
      response = rcvBuf_;
      x = atoi(response.substr(response.length() - 2).c_str());
   }
   if (shutterNr==1)
      x = x >> 1;

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
   unsigned long read = RCV_BUF_LENGTH;
   while (read == (unsigned long) RCV_BUF_LENGTH)
   {
      core.ReadFromSerial(&device, port_.c_str(), (unsigned char*)rcvBuf_, RCV_BUF_LENGTH,  read);
   }
   // Delete it all:
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

void ASIFW1000Hub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}


/**
 * Sends serial command to the MMCore virtual serial port.
 */
int ASIFW1000Hub::ExecuteCommand(MM::Device& device, MM::Core& core,  const char* command)
{
   std::string base_command = "";
   ClearAllRcvBuf(device, core);

   if (oldProtocol_)
      base_command += "3F"; // prefix to all commands for old devices
   base_command += command;
   // send command
   return core.SetSerialCommand(&device, port_.c_str(), base_command.c_str(), "\r");
}

/**
 * Tests if serial port is connected.
 */
bool ASIFW1000Hub::IsConnected()
{
   return (port_.compare("Undefined") != 0);
}

