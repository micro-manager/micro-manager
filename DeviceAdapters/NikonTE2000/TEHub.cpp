///////////////////////////////////////////////////////////////////////////////
// FILE:          TEHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon TE2000 hub module. Required for operation of all 
//                TE2000 microscope devices
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
// CVS:           $Id$
//

#define _CRT_SECURE_NO_DEPRECATE

#include "TEHub.h"
#include "assert.h"
#include <cstdio>
#include <memory.h>
#include <sstream>
#include <iostream>
#include "../../MMDevice/ModuleInterface.h"

using namespace std; 

TEHub::TEHub()
{
   commandMode_ = "c"; // set sync mode as default
   // to run microscope in the async mode use: commandMode_ = "f";

   // TODO: "f" mode not fully tested

   expireTimeUs_ = 5000000; // each command will finish within 5sec
   // TODO: 5sec is stated in the Nikon manual as the max timeout. This may not be true.

   ClearRcvBuf();
}

TEHub::~TEHub()
{
}

void TEHub::LogError(int id, MM::Device& device, MM::Core& core, const char* functionName)
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


bool TEHub::IsComponentMounted(MM::Device& device, MM::Core& core, const char* deviceCode)
{
   ExecuteCommand(device, core, "r", deviceCode);
   string value;
   ParseResponse(device, core, deviceCode, value);

   int mountingStatus = atoi(value.c_str());
   return mountingStatus == 1;
}

bool TEHub::DetectPerfectFocus(MM::Device& device, MM::Core& core)
{
   const char* command = "TRN1rVEN";
   ExecuteCommand(device, core, "c", command);
   string value;
   return DEVICE_OK == ParseResponse(device, core, "TRN", value);
}

///////////////////////////////////////////////////////////////////////////////
// Nosepiece commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetNosepiecePosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "RDM";
   ostringstream os;
   os << command << "1" << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
   {
      return ret;
   }

   if (GetCommandMode() == Async)
      waitingCommands_.insert(pair<string,long>(command, core.GetClockTicksUs(&device)));

   return DEVICE_OK;
}

int TEHub::GetNosepiecePosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "RAR";
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

int TEHub::GetNosepieceMountingStatus(MM::Device& device, MM::Core& core, int& status)
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

bool TEHub::IsNosepieceBusy(MM::Device& device, MM::Core& core)
{
   FetchSerialData(device, core);
   return IsCommandWaiting("RDM",device,core);
}


///////////////////////////////////////////////////////////////////////////////
// FilterBlock commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetFilterBlockPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "HDM";
   ostringstream os;
   os << command << "1" << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   return DEVICE_OK;
}

int TEHub::GetFilterBlockPosition(MM::Device& device, MM::Core& core, int& pos)
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

bool TEHub::IsFilterBlockBusy(MM::Device& device, MM::Core& core)
{
   if (this->GetCommandMode() == Async)
   {
      FetchSerialData(device, core);
      return IsCommandWaiting("HDM", device, core);
   }
   return
      false;
}


///////////////////////////////////////////////////////////////////////////////
// Excitation Side Filter Block Commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetExcitationFilterBlockPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "FDM";
   ostringstream os;
   os << command << pos << '\r';

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse response
   string value;
   core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   return DEVICE_OK;
}

int TEHub::GetExcitationFilterBlockPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "FAR";
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

bool TEHub::IsExcitationFilterBlockBusy(MM::Device& device, MM::Core& core)
{
   if (this->GetCommandMode() == Async)
   {
      FetchSerialData(device, core);
      return IsCommandWaiting("FCR", device, core);
   }
   return
      false;
}

///////////////////////////////////////////////////////////////////////////////
// Focus commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetFocusPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "SMV";
   ostringstream os;
   os << command << pos;

   // send command
   int ret = ExecuteCommand(device, core, commandMode_.c_str(), os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   return DEVICE_OK;
}

bool TEHub::IsFocusBusy(MM::Device& device, MM::Core& core)
{
   if (this->GetCommandMode() == Async)
   {
      FetchSerialData(device, core);
      return IsCommandWaiting("SMV", device, core);
   }
   else
      return false;
}

int TEHub::GetFocusPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "SPR";
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

int TEHub::SetFocusStepSize(MM::Device& device, MM::Core& core, int stepsize)
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

   if (GetCommandMode() == Async)
      waitingCommands_.insert(make_pair(command, core.GetClockTicksUs(&device)));
   return DEVICE_OK;
}


int TEHub::GetFocusStepSize(MM::Device& device, MM::Core& core, int& stepSize)
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
// OpticalPath commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetOpticalPathPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "PDM";
   ostringstream os;
   os << command << pos;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetOpticalPathPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "PAR";
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

bool TEHub::IsOpticalPathBusy(MM::Device& device, MM::Core& core)
{
   FetchSerialData(device, core);
   return IsCommandWaiting("PDM", device, core);
}

///////////////////////////////////////////////////////////////////////////////
// Analyzer commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetAnalyzerPosition(MM::Device& device, MM::Core& core, int pos)
{
   const char* command = "ALC";
   ostringstream os;
   os << command << pos;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetAnalyzerPosition(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "ALR";
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

bool TEHub::IsAnalyzerBusy(MM::Device& device, MM::Core& core)
{
   FetchSerialData(device, core);
   return IsCommandWaiting("ALC", device, core);
}

///////////////////////////////////////////////////////////////////////////////
// Lamp commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetLampOnOff(MM::Device& device, MM::Core& core, int state)
{
   const char* command = "LMS";
   ostringstream os;
   os << command << state;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::SetLampControlTarget(MM::Device& device, MM::Core& core, int target)
{
   const char* command = "LCS";
   ostringstream os;
   os << command << target;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetLampControlTarget(MM::Device& device, MM::Core& core, int& target)
{
   const char* command = "LCR";

   // send command
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   target = atoi(value.c_str());
   return DEVICE_OK;
}

int TEHub::GetLampOnOff(MM::Device& device, MM::Core& core, int& state)
{
   const char* command = "LSR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   state = atoi(value.c_str());
   return DEVICE_OK;
}

int TEHub::SetLampVoltage(MM::Device& device, MM::Core& core, double voltage)
{
   const char* command = "LMC";
   char fmtBuf[100];
   sprintf(fmtBuf, "%s%.1f", command, voltage);

   // send command
   int ret = ExecuteCommand(device, core, "c", fmtBuf);
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetLampVoltage(MM::Device& device, MM::Core& core, double& voltage)
{
   const char* command = "LVR";
   int ret = ExecuteCommand(device, core, "r", command);
   if (ret != DEVICE_OK)
      return ret;

   // parse the response
   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   voltage = atof(value.c_str());
   return DEVICE_OK;
}
bool TEHub::IsLampBusy(MM::Device& /*device*/, MM::Core& /*core*/)
{
   return false;
}


///////////////////////////////////////////////////////////////////////////////
// Perfect Focus commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetPFocusOn(MM::Device& device, MM::Core& core)
{
   const char* command = "TRN1cAFG1";

   // send command
   int ret = ExecuteCommand(device, core, "c", command);
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::SetPFocusOff(MM::Device& device, MM::Core& core)
{
   const char* command = "TRN1cAFG2";

   // send command
   int ret = ExecuteCommand(device, core, "c", command);
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetPFocusStatus(MM::Device& device, MM::Core& core, int& status)
{
   const char* command = "TRN1rAFR";

   // send command
   int ret = ExecuteCommand(device, core, "c", command);
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   status = atoi(value.c_str());
   return DEVICE_OK;
}

int TEHub::GetPFocusVersion(MM::Device& device, MM::Core& core, std::string& version)
{
   const char* command = "TRN1rVEN";

   // send command
   int ret = ExecuteCommand(device, core, "c", command);
   if (ret != DEVICE_OK)
      return ret;

   ret = ParseResponse(device, core, "TRN", version);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Sets Absolute PFS Ofsset lens focus position in Steps
 */
int TEHub::SetPFocusPosition(MM::Device& device, MM::Core& core, int pos)
{
   ostringstream os;
   os <<  "TRN1cOLA" << pos;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Sets Relative PFS Ofsset lens focus position in Steps
 */
int TEHub::SetRelativePFocusPosition(MM::Device& device, MM::Core& core, int pos)
{
   ostringstream os;
   os <<  "TRN1cOLB" << pos;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Returns PFS Ofsset lens focus position in Steps
 */
int TEHub::GetPFocusPosition(MM::Device& device, MM::Core& core, int& pos)
{
   ostringstream os;
   os <<  "TRN1rOLR";

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, "TRN", value);
   if (ret != DEVICE_OK)
      return ret;

   pos = atoi(value.c_str());

   return DEVICE_OK;
}


/*
 * Epi-illumination Shutter
 */

int TEHub::SetEpiShutterStatus(MM::Device& device, MM::Core& core, int status)
{
   const char* command = "SHC";
   ostringstream os;
   os << command <<  status;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TEHub::GetEpiShutterStatus(MM::Device& device, MM::Core& core, int& pos)
{
   const char* command = "SSR";
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


///////////////////////////////////////////////////////////////////////////////
// Uniblitz commands
///////////////////////////////////////////////////////////////////////////////

int TEHub::SetUniblitzStatus(MM::Device& device, MM::Core& core, int shutterNumber, int status)
{
   const char* command = "DSC";
   ostringstream os;
   os << command << shutterNumber + status % 1;

   // send command
   int ret = ExecuteCommand(device, core, "c", os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string value;
   ret = ParseResponse(device, core, command, value);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// HUB generic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears the serial receive buffer.
 */
void TEHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Returns the current firmware version.
 */
int TEHub::GetVersion(MM::Device& device, MM::Core& core, std::string& ver)
{
   const char* command = "VER";
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
int TEHub::GetModelType(MM::Device& device, MM::Core& core, int& type)
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
bool TEHub::IsBusy()
{
   return waitingCommands_.size() != 0;
}
/**
 * Sends serial command to the MMCore virtual serial port.
 */
int TEHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* type, const char* command)
{
   // empty the Rx serial buffer before sending command
   FetchSerialData(device, core);

   ClearRcvBuf();
   string strCommand(type);
   strCommand += command;

   // send command
   int ret = core.SetSerialCommand(&device, port_.c_str(), strCommand.c_str(), "\r");
   if (ret != DEVICE_OK)
   {
      LogError(ret, device, core, "ExecuteCommand-SetSerialCommand");
      return ret;
   }
  
   // get response
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r\n");
   
   if (ret != DEVICE_OK)
   {
      LogError(ret, device, core, "ExecuteCommand-GetSerialAnswer");
      // Keep on trying until we have an answer or 5 seconds have passed
      int maxTimeMs = 5000;
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
            if ( (core.GetCurrentMMTime() - startTime) > (maxTimeMs*1000.0) )
               done = true;
         }
      }
      ostringstream os;
      if (ret == DEVICE_OK)
         os << "ExecuteCommand-GetSerialAnswer: Succeeded reading from serial port after trying " << counter << "times.";
      else
         os << "ExecuteCommand-GetSreialAnswer: Failed reading from serial port after trying " << counter << "times.";

      core.LogMessage(&device, os.str().c_str(), true);
      return ret;
   }

   return DEVICE_OK;
}

/**
 * Interprets the string returned from the microscope.
 */
int TEHub::ParseResponse(MM::Device& device, MM::Core& core, const char* cmd, string& value)
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
   size_t responselength_ = strlen(rcvBuf_);
   if (rcvBuf_[0] == 'n' && responselength_ > 4)
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
void TEHub::FetchSerialData(MM::Device& device, MM::Core& core)
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
bool TEHub::IsCommandWaiting(const char* command, MM::Device& device, MM::Core& core)
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

