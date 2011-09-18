///////////////////////////////////////////////////////////////////////////////
// FILE:          PiezoZStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: Piezo Z Stage BPC301
//
// COPYRIGHT:     Thorlabs, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, http://nenad.amodaj.com, 2011
//

// #define TESTRUN // uncomment this macro to run in 'dry' testing mode (no COM port queries)

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Thorlabs.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>

extern const char* g_PiezoZStageDeviceName;
const char* g_PositionProp = "Position";

///////////
// commands
///////////
const unsigned char setPosCmd[] =  {         0x46, // cmd low byte
                                             0x06, // cmd high byte
                                             0x04,
                                             0x00,
                                             0xD0, // CH_VAR
                                             0x01,
                                             0x01, // SUB_CH
                                             0x00,
                                             0x00, // position low byte
                                             0x00  // position high byte 
                                          };             

const unsigned char getPosCmd[] = {          0x47, // cmd low byte
                                             0x06, // cmd high byte
                                             0x01, // SUB_CH
                                             0x00,
                                             0x50, // CH_BASE
                                             0x01 
                                          };

const unsigned char getPosRsp[] = {          0x48, // cmd low byte
                                             0x06, // cmd high byte
                                             0x04,
                                             0x00,
                                             0x81,
                                             0x50, // CH_BASE
                                             0x01, // SUB_CH
                                             0x00,
                                             0x00, // position low byte
                                             0x00  // position high byte 
                                          };             

const unsigned char getMaxTravelCmd[] = {    0x50, // cmd low byte
                                             0x06, // cmd high byte
                                             0x01, // SUB_CH
                                             0x00,
                                             0x50, // CH_BASE
                                             0x01 
                                          };

const unsigned char getMaxTravelRsp[] = {    0x51, // cmd low byte
                                             0x06, // cmd high byte
                                             0x04,
                                             0x00,
                                             0x81,
                                             0x50, // CH_BASE
                                             0x01, // SUB_CH
                                             0x00,
                                             0x00, // position low byte
                                             0x00  // position high byte 
                                          }; 

const unsigned char setZeroCmd[] = {         0x58, // cmd low byte
                                             0x06, // cmd high byte
                                             0x01, // SUB_CH
                                             0x01,
                                             0x50, // CH_BASE
                                             0x01
                                          };

const unsigned char getControlModeCmd[] = {  0x41, // cmd low byte
                                             0x06, // cmd high byte
                                             0x01, // SUB_CH
                                             0x00,
                                             0x50, // CH_BASE
                                             0x01 
                                          };

const unsigned char getControlModeRsp[] = {  0x42, // cmd low byte
                                             0x06, // cmd high byte
                                             0x01, // SUB_CH
                                             0x00, // mode
                                             0x01, // position low byte
                                             0x50  // CH_BASE 
                                          }; 
using namespace std;


///////////////////////////////////////////////////////////////////////////////
// PiezoZStage class
///////////////////////////////////////////////////////////////////////////////

PiezoZStage::PiezoZStage() :
   port_("Undefined"),
   stepSizeUm_(0.1),
   initialized_(false),
   answerTimeoutMs_(1000),
   maxTravelUm_(500.0),
   curPosUm_(0.0),
   zeroed_(false),
   zeroInProgress_(false)
{
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Serial port can't be changed at run-time."
                                           " Use configuration utility or modify configuration file manually.");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Invalid response from the device");
   SetErrorText(ERR_UNSPECIFIED_ERROR, "Unspecified error occured.");
   SetErrorText(ERR_RESPONSE_TIMEOUT, "Device timed-out: no response received withing expected time interval.");
   SetErrorText(ERR_BUSY, "Device busy.");
   SetErrorText(ERR_STAGE_NOT_ZEROED, "Zero sequence still in progress.\n"
                                      "Wait for few more seconds before trying again."
                                      "Zero sequence executes only once per power cycle");
 

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_PiezoZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Thorlabs piezo Z Stage", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &PiezoZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

PiezoZStage::~PiezoZStage()
{
   Shutdown();
}

void PiezoZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_PiezoZStageDeviceName);
}

int PiezoZStage::Initialize()
{
   int ret(DEVICE_OK);

#ifndef TESTRUN
   // try to obtain maximum travel
   // if this doesn't work initialization will fail
   ret = SetMaxTravel();
   if (ret != DEVICE_OK)
      return ret;

   // start zero sequence
   ret = SetZero();
   if (ret != DEVICE_OK)
      return ret;
#endif

   CPropertyAction* pAct = new CPropertyAction (this, &PiezoZStage::OnPosition);
   CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(MM::g_Keyword_Position, 0.0, maxTravelUm_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int PiezoZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool PiezoZStage::Busy()
{
   // never busy because all commands block
   return false;
}

int PiezoZStage::SetPositionSteps(long steps)
{
   if (steps < 0 || steps > SHRT_MAX)
      return ERR_STEPS_OUT_OF_RANGE;

   if (!IsZeroed())
      return ERR_STAGE_NOT_ZEROED;

   const int len = sizeof(setPosCmd);
   unsigned char cmd[len];
   memcpy(cmd, setPosCmd, len);
   short stepsTrunc = (short)steps; // convert to fixed size var
   cmd[len-2] = (unsigned char) (stepsTrunc & 0x00ff); // low byte
   cmd[len-1] = (unsigned char) ((stepsTrunc >> 8) & 0x00ff); // high byte

   // send command
   int ret = SetCommand(cmd, sizeof(cmd));
   if (ret != DEVICE_OK)
      return ret;

   // block until done
   CDeviceUtils::SleepMs((long)GetTravelTimeMs(steps)); 

   return DEVICE_OK;
}

int PiezoZStage::GetPositionSteps(long& steps)
{
#ifdef TESTRUN
   steps = (long)(curPosUm_ / maxTravelUm_ * SHRT_MAX);
#else
   const int len = sizeof(getPosCmd);
   unsigned char cmd[len];
   memcpy(cmd, getPosCmd, len);

   // send command
   int ret = SetCommand(cmd, sizeof(cmd));
   if (ret != DEVICE_OK)
      return ret;

   // parse response
   const int rspLen = sizeof(getPosRsp);
   unsigned char answer[rspLen];
   ret = GetCommand(answer, rspLen, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   // check response signature
   if (memcmp(getPosRsp, answer, rspLen - 2) != 0)
      return ERR_UNRECOGNIZED_ANSWER;

   // get the value
   short stepsTrunc = answer[rspLen - 1]; // high byte
   stepsTrunc <<= 8;
   stepsTrunc += answer[rspLen - 2]; // low byte

   steps = stepsTrunc;
#endif

   return DEVICE_OK;
}
  
int PiezoZStage::SetPositionUm(double posUm)
{
   long steps = (long)(posUm / maxTravelUm_ * SHRT_MAX + 0.5);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   curPosUm_ = posUm;
   return DEVICE_OK;
}
  
int PiezoZStage::GetPositionUm(double& posUm)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   posUm = (double)steps / SHRT_MAX * maxTravelUm_;
   curPosUm_ = posUm;

   return DEVICE_OK;
}

int PiezoZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int PiezoZStage::GetLimits(double& min, double& max)
{
   min = 0.0;
   max = maxTravelUm_;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int PiezoZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int PiezoZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      int ret = SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;

   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// private methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a binary seqence of bytes to the com port
 */
int PiezoZStage::SetCommand(const unsigned char* command, unsigned length)
{
   int ret = WriteToComPort(port_.c_str(), command, length);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

/**
 * Retrieves specified number of bytes (length) from the Rx buffer.
 * We block until we collect (length) bytes from the port.
 * As soon as bytes are retrieved the method returns with DEVICE_OK code.
 * If the specified number of bytes is not retrieved from the port within
 * (timeoutMs) interval, we return with error.
 */
int PiezoZStage::GetCommand(unsigned char* response, unsigned length, double timeoutMs)
{
   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long totalBytesRead = 0;
   while ((totalBytesRead < length))
   {
      if ((GetCurrentMMTime() - startTime).getMsec() > timeoutMs)
         return ERR_RESPONSE_TIMEOUT;

      unsigned long bytesRead(0);
      int ret = ReadFromComPort(port_.c_str(), response + totalBytesRead, length-totalBytesRead, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
      totalBytesRead += bytesRead;
   }
   return DEVICE_OK;
}

/**
 * Obtains maximum travel distance from the stage
 */
int PiezoZStage::SetMaxTravel()
{
   const int len = sizeof(getMaxTravelCmd);
   unsigned char cmd[len];
   memcpy(cmd, getMaxTravelCmd, len);

   // send command
   int ret = SetCommand(cmd, sizeof(cmd));
   if (ret != DEVICE_OK)
      return ret;

   // parse response
   const int rspLen = sizeof(getMaxTravelRsp);
   unsigned char answer[rspLen];
   ret = GetCommand(answer, rspLen, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   // check response signature
   if (memcmp(getMaxTravelRsp, answer, rspLen - 2) != 0)
      return ERR_UNRECOGNIZED_ANSWER;

   // get the value
   short travelTrunc = answer[rspLen - 1]; // high byte
   travelTrunc <<= 8;
   travelTrunc += answer[rspLen - 2]; // low byte

   maxTravelUm_ = travelTrunc / 10.0;

   return DEVICE_OK;
}

/**
 * Send zero command to the stage
 * If stage was already zeroed, this command has no effect.
 * If not, then the zero sequence will be initiated.
 */
int PiezoZStage::SetZero()
{
   if (!IsZeroed() && !zeroInProgress_)
   {
      // send command
      int ret = SetCommand(setZeroCmd, sizeof(setZeroCmd));
      if (ret != DEVICE_OK)
         return ret;

      zeroInProgress_ = true;
   }
   return DEVICE_OK;
}

/**
 * Check if the stage is already zeroed or not.
 */
bool PiezoZStage::IsZeroed()
{
   if (zeroed_)
      return true;

#ifndef TESTRUN
   // if not zeroed previously, check the status

   // send command
   int ret = SetCommand(getControlModeCmd, sizeof(getControlModeCmd));
   if (ret != DEVICE_OK)
      return false; // command failed, assume not zeroed

   // parse response
   const int rspLen = sizeof(getControlModeRsp);
   unsigned char answer[rspLen];
   ret = GetCommand(answer, rspLen, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return false;

   // check response signature
   if (memcmp(getControlModeRsp, answer, 2) != 0)
      return false; // answer did not match, assume not-zeroed

   // get the value
   unsigned char mode = answer[3]; // mode
   if (mode == 2 || mode==4)
   {
      zeroed_ = true;
      zeroInProgress_ = false;
   }
#endif

   return zeroed_;
}

/**
 * Estimates travel time based on the requested number of steps
 * Based on the assumption that it takes 30ms for distances less than
 * 10um, and up to 50ms for travel of 500um
 */
double PiezoZStage::GetTravelTimeMs(long steps)
{
   static const double minTimeMs = 30.0;
   static const double maxTimeMs = 50.0;
   static const double travelUm = 490.0;

   if (maxTravelUm_ <= 10.0)
      return minTimeMs;

   double stepSizeUm = maxTravelUm_ / SHRT_MAX;
   double distanceUm = fabs(steps * stepSizeUm - curPosUm_);
   return minTimeMs + max(distanceUm - 10.0, 0.0) / (travelUm - 10.0) * (maxTimeMs - minTimeMs);
}
