///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMRHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMR hub module. Required for operation of all 
//                LeicaDMR devices
//                
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 07/02/2009
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                100xImaging, Inc. 2009
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
#include <iomanip>
#include <string>
#include "LeicaDMRHub.h"
#include "LeicaDMR.h"

#ifdef WIN32
#include <windows.h>
#endif

using namespace std;

LeicaDMRHub::LeicaDMRHub () :
   rLFA_(0),
   port_(""),
   initialized_(false)
{
   expireTimeUs_ = 5000000; // each command will finish within 5sec

   ClearRcvBuf();
}

LeicaDMRHub::~LeicaDMRHub()
{
   initialized_ = false;
}


int LeicaDMRHub::Initialize(MM::Device& device, MM::Core& core)
{
   if (initialized_)
      return DEVICE_OK;

   int ret = GetVersion (device, core, version_);
   if (ret != DEVICE_OK) {
      // some serial ports do not open correctly right away, so try once more:
      ret = GetVersion(device, core, version_);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = GetMicroscope(device, core, microscope_);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream os;
   os << "Microscope type: " << microscope_;
   core.LogMessage(&device, os.str().c_str(), false);
   os.str("");
   os << "Firmware version: " << version_;
   core.LogMessage(&device, os.str().c_str(), false);

   for (int i=0; i<maxNrDevices_; i++)
      present_[i] = false;

   if (microscope_ == "DMIRBE" || microscope_ == "DMRXA" || microscope_ == "DMRA") {
      GetPresence(device, core, zDrive_);
      GetPresence(device, core, objNosepiece_);
   }
   if (microscope_ == "DMRXA" || microscope_ == "DMRA") {
      GetPresence(device, core, fDia_);
      GetPresence(device, core, rLFA4_);
      GetPresence(device, core, rLFA8_);
      GetPresence(device, core, lamp_);
      if (present_[rLFA4_])
         rLFA_ = rLFA4_;
      else if (present_[rLFA8_])
         rLFA_ = rLFA8_;
   }
   if (microscope_ == "DMIRBE") {
      GetPresence(device, core, iCPrismTurret_);
   }


   initialized_ = true;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Device commands
///////////////////////////////////////////////////////////////////////////////

int LeicaDMRHub::GetVersion(MM::Device& device, MM::Core& core, std::string& version)
{
   return GetCommand(device, core, gMic_, 25, version);
}

int LeicaDMRHub::GetMicroscope(MM::Device& device, MM::Core& core, std::string& microscope)
{
   int ret = GetCommand(device, core, gMic_, 26, microscope);
   if (ret != DEVICE_OK) {
      // the dmrxe does not have this command and will time out
      microscope = "DMRXE";
   } else
      microscope = microscope.erase(microscope.find_last_not_of(" ") + 1);
   return DEVICE_OK;
}

// Halogen Lamp

int LeicaDMRHub::GetLampIntensity(MM::Device& device, MM::Core& core, int& intensity)
{
   return GetCommand(device, core, lamp_, 10, intensity);
}


int LeicaDMRHub::SetLampIntensity(MM::Device& device, MM::Core& core, int intensity)
{
   return SetCommand(device, core, lamp_, 02, intensity);
}

// Reflected Light Turret

int LeicaDMRHub::SetRLModulePosition(MM::Device& device, MM::Core& core, int pos)
{
   if (! (rLFA_ == rLFA4_ || rLFA_ == rLFA8_))
      return ERR_INVALID_REFLECTOR_TURRET;

   return SetCommand(device, core, rLFA_, 2, pos);
}

/**
 * Request the current position from the RL Module
 * Sometimes, it does not answer correctly (i.e., 12010 yields 12010)
 * Keep on trying for up to 500 mseconds, 50 msec intervals
 */
int LeicaDMRHub::GetRLModulePosition(MM::Device& device, MM::Core& core, int& pos)
{
   if (! (rLFA_ == rLFA4_ || rLFA_ == rLFA8_))
      return ERR_INVALID_REFLECTOR_TURRET;

   pos = 0;
   MM::MMTime start = core.GetCurrentMMTime();
   int ret;
   while (pos == 0 && (core.GetCurrentMMTime() - start < 500000) ) {
      ret = GetCommand(device, core, rLFA_, 10, pos);
      if (ret != DEVICE_OK)
         return ret;
      CDeviceUtils::SleepMs(50);
   }
   if (pos == 0) 
      return ERR_INVALID_POSITION;

   return DEVICE_OK;
}

int LeicaDMRHub::GetRLModuleNumberOfPositions(MM::Device& /*device*/, MM::Core& /*core*/, int& nrPos)
{
   if (rLFA_ == rLFA4_)
      nrPos = 4;
   else if (rLFA_ == rLFA8_)
      nrPos = 8;
   return DEVICE_OK;
}

// Reflected Light Shutter

int LeicaDMRHub::SetRLShutter(MM::Device& device, MM::Core& core, bool open)
{
   if (! (rLFA_ == rLFA4_ || rLFA_ == rLFA8_))
      return ERR_INVALID_REFLECTOR_TURRET;

   int pos = 0;
   if (open)
      pos = 1 ;
   return SetCommand(device, core, rLFA_, 12, pos);
}

int LeicaDMRHub::GetRLShutter(MM::Device& device, MM::Core& core, bool& open)
{
   if (! (rLFA_ == rLFA4_ || rLFA_ == rLFA8_))
      return ERR_INVALID_REFLECTOR_TURRET;

   int pos = 0;
   int ret = GetCommand(device, core, rLFA_, 13, pos);
   if (ret != DEVICE_OK)
	   return ret;

   open = false;
   if (pos == 1)
      open = true;

   return DEVICE_OK;
}

// Z Drive
int LeicaDMRHub::SetZAbs(MM::Device& device, MM::Core& core, long position)
{
   return SetCommand(device, core, zDrive_, 2, (int) position);
}

int LeicaDMRHub::SetZRel(MM::Device& device, MM::Core& core, long position)
{
   return SetCommand(device, core, zDrive_, 3, (int) position);
}

int LeicaDMRHub::MoveZConst(MM::Device& device, MM::Core& core, int speed)
{
   return SetCommand(device, core, zDrive_, 4, speed);
}

int LeicaDMRHub::StopZ(MM::Device& device, MM::Core& core)
{
   return SetCommand(device, core, zDrive_, 6);
}

int LeicaDMRHub::GetZAbs(MM::Device& device, MM::Core& core, long& position)
{
   int pos;
   int ret =  GetCommand(device, core, zDrive_, 10, pos);
   if (ret != DEVICE_OK)
      return ret;
   position = (long) pos;

   return DEVICE_OK;
}

int LeicaDMRHub::GetZUpperThreshold(MM::Device& device, MM::Core& core, long& position)
{
   int pos;
   int ret =  GetCommand(device, core, zDrive_, 14, pos);
   if (ret != DEVICE_OK)
      return ret;
   position = (long) pos;

   return DEVICE_OK;
}

int LeicaDMRHub::SetObjNosepiecePosition(MM::Device& device, MM::Core& core, int pos)
{
   return SetCommand(device, core, objNosepiece_, 2, pos);
}

int LeicaDMRHub::GetObjNosepiecePosition(MM::Device& device, MM::Core& core, int& pos)
{
   return GetCommand(device, core, objNosepiece_, 10, pos);
}

int LeicaDMRHub::SetObjNosepieceImmMode(MM::Device& device, MM::Core& core, int mode)
{
   return SetCommand(device, core, objNosepiece_, 16, mode);
}

int LeicaDMRHub::GetObjNosepieceImmMode(MM::Device& device, MM::Core& core, int& mode)
{
   return GetCommand(device, core, objNosepiece_, 17, mode);
}

int LeicaDMRHub::SetObjNosepieceRotationMode(MM::Device& device, MM::Core& core, int mode)
{
   return SetCommand(device, core, objNosepiece_, 12, mode);
}

int LeicaDMRHub::GetObjNosepieceRotationMode(MM::Device& device, MM::Core& core, int& mode)
{
   return GetCommand(device, core, objNosepiece_, 13, mode);
}

int LeicaDMRHub::GetObjNosepieceNumberOfPositions(MM::Device& device, MM::Core& core, int& nrPos)
{
   std::string status;
   int ret = GetCommand(device, core, objNosepiece_, 11, status);
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream ss;
   ss << status[2];
   ss >> nrPos;

   return DEVICE_OK;
}


int LeicaDMRHub::SetApertureDiaphragmPosition(MM::Device& device, MM::Core& core, int pos)
{
   return SetCommand(device, core, aDia_, 2, pos);
}

int LeicaDMRHub::GetApertureDiaphragmPosition(MM::Device& device, MM::Core& core, int& pos)
{
   return GetCommand(device, core, aDia_, 10, pos);
}

int LeicaDMRHub::BreakApertureDiaphragm(MM::Device& device, MM::Core& core)
{
   return SetCommand(device, core, aDia_, 6);
}


int LeicaDMRHub::SetFieldDiaphragmPosition(MM::Device& device, MM::Core& core, int pos)
{
   return SetCommand(device, core, fDia_, 2, pos);
}

int LeicaDMRHub::GetFieldDiaphragmPosition(MM::Device& device, MM::Core& core, int& pos)
{
   return GetCommand(device, core, fDia_, 10, pos);
}

int LeicaDMRHub::SetCondensorPosition(MM::Device& device, MM::Core& core, int pos)
{
   return SetCommand(device, core, fDia_, 12, pos);
}

int LeicaDMRHub::GetCondensorPosition(MM::Device& device, MM::Core& core, int& pos)
{
   return GetCommand(device, core, fDia_, 13, pos);
}

int LeicaDMRHub::BreakFieldDiaphragm(MM::Device& device, MM::Core& core)
{
   return SetCommand(device, core, fDia_, 6);
}


///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

int LeicaDMRHub::GetPresence(MM::Device& device, MM::Core& core, int deviceId)
{
   if (deviceId > maxNrDevices_)
      return ERR_INDEX_OUT_OF_BOUNDS;

   int present;
   GetCommand(device, core, deviceId, 1, present);
   if (present == 1)
      present_[deviceId] = true;

   return DEVICE_OK;
}

/**
 * Clears the serial receive buffer.
 */
void LeicaDMRHub::ClearAllRcvBuf(MM::Device& device, MM::Core& core)
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

void LeicaDMRHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}


/**
 * Sends serial command to the MMCore virtual serial port.
 * returns data from the microscope as a string
 */
int LeicaDMRHub::GetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, std::string& answer)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);
 
   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2, s3;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER;

   if (strlen(rcvBuf) > 5)
      s3 << rcvBuf + 5;

   answer = s3.str();

   return DEVICE_OK;

}

/**
 * Sends serial command to the MMCore virtual serial port.
 * returns data from the microscope as an integer
 */
int LeicaDMRHub::GetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, int& answer)
{
   std::string reply;
   int ret = GetCommand(device, core, deviceId, command, reply);
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream ss;
   ss << reply;
   ss >> answer;

   return DEVICE_OK;
}

/**
 * Sends serial command to the MMCore virtual serial port appended with data.
 */
int LeicaDMRHub::SetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command, int data)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);

   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command << data;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2, s3;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER;

   // TODO: error checking in the answer

   return DEVICE_OK;
}


/**
 * Sends serial command to the MMCore virtual serial port.
 */
int LeicaDMRHub::SetCommand(MM::Device& device, MM::Core& core,  int deviceId, int command)
{
   if (port_ == "")
      return ERR_PORT_NOT_SET;

   ClearAllRcvBuf(device, core);

   // send command
   std::ostringstream os;
   os << std::setw(2) << std::setfill('0') << deviceId;
   os << std::setw(3) << std::setfill('0') << command;
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   char rcvBuf[RCV_BUF_LENGTH];
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf, "\r");
   if (ret != DEVICE_OK)
      return ret;

   std::stringstream s1, s2, s3;
   int commandCheck, deviceIdCheck;
   s1 << rcvBuf[0] << rcvBuf[1];
   s1 >> deviceIdCheck;
   s2 << rcvBuf[2] << rcvBuf[3] << rcvBuf[4];
   s2 >> commandCheck;
   if ( (deviceId != deviceIdCheck) || (command != commandCheck))
      return ERR_UNEXPECTED_ANSWER;

   // TODO: error checking in the answer

   return DEVICE_OK;
}
