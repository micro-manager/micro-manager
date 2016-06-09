///////////////////////////////////////////////////////////////////////////////
// FILE:          ASITiger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI Tiger common defines and ASIUtility class
//                Note this is for the "Tiger" MM set of adapters, which should
//                  work for more than just the TG-1000 "Tiger" controller
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 06/2014
//
// BASED ON:      ASIStage.h, ASIFW1000.h, Arduino.h, and DemoCamera.h
//

#ifndef _OVP_ECS2_H_
#define _OVP_ECS2_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <vector>
#include <iostream>


using namespace std;


//////////////////////////////////////////////////////////////////////////////
// ASI-specific macros
// Use the name 'return_value' that is unlikely to appear within 'result'.
#define RETURN_ON_MM_ERROR( result ) do { \
   int return_value = (result); \
   if (return_value != DEVICE_OK) { \
      return return_value; \
   } \
} while (0)

// some shortcuts for bit manipulations
#define BIT0   0x01
#define BIT1   0x02
#define BIT2   0x04
#define BIT3   0x08
#define BIT4   0x10
#define BIT5   0x20
#define BIT6   0x40
#define BIT7   0x80


// define our own type for string/vector of unsigned characters
// use this to represent messages being passed back and forth to the ECS unit
// the checksum is added/removed from this during send/receive; doesn't usually contain the CRC
typedef std::vector<unsigned char> Message;


// External device names used used by the rest of the system to load particular device from the .dll library
const char* const g_ECSDeviceName =  "ECS-2";

// corresponding device descriptions
const char* const g_ECSDeviceDescription = "OVP ECS-2";

// error messages and error code definitions
#define ERR_PORT_CHANGE_FORBIDDEN            10004
#define ERR_NO_SERIAL_COMM                   10201
const char* const g_Msg_ERR_NO_SERIAL_COMM = "No serial response received or else incorrect CRC.";
#define ERR_SERIAL_COMM_BAD_RESPONSE         10202
const char* const g_Msg_ERR_SERIAL_COMM_BAD_RESPONSE = "Serial response didn't include expected prefix.";

// property names
const char* const g_SerialCommandPropertyName = "SerialCommand(Hex)";
const char* const g_SerialResponsePropertyName = "SerialResponse(Hex)";
const char* const g_SerialWriteReadDelayPropertyName = "SerialWriteReadDelay(ms)";
const char* const g_SerialMaximumAttempts = "SerialMaximumAttempts";
const char* const g_TemperatureCurrent = "Temperature(degC)";
const char* const g_TemperatureSetPoint = "TemperatureSetPoint(degC)";
const char* const g_HeaterPower = "HeaterPower(%)";
const char* const g_CO2Level = "CO2Level(%)";
const char* const g_CO2SetPoint = "CO2SetPoint(%)";
const char* const g_CO2SetPointDeadband = "CO2SetPointDeadband(%)";

// serial defines
#define MAX_RESPONSE_LENGTH 20  // the most bytes we can get back from a serial command
#define RECEIVE_RETRIES     10
#define RECEIVE_DELAY_MS   100

class ECS: public CGenericBase<ECS>
{
public:
   ECS();
   ~ECS();

   // Device API
   // ----------
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();
   bool SupportsDeviceDetection(void);
   MM::DeviceDetectionStatus DetectDevice();

   // Incubation "API" (copied from another adapter, no Micro-manager API yet)
   // ---------
   int GetTempSP(double& sp);
   int SetTempSP(double sp);
   int GetTemp(double& temp);
   int GetCO2(double& co2);
   int GetCO2SP(double& sp);
   int SetCO2SP(double sp);
   int GetCO2Deadband(double& band);
   int SetCO2Deadband(double band);

   // Communication base functions
   int ClearComPort();
   int QueryCommand(Message command);  // updates serialAnswer_ with response
   int QueryCommandVerify(Message command, Message responsePrefix, size_t expectedLength);  // QueryCommand but makes sure reply is prefixed correctly and has the expected length


   // action interface
   // ----------------
   int OnPort                 (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommand        (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialWriteReadDelay (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialMaximumAttempts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTempSetPoint         (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHeaterPower          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCO2Level             (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCO2SetPoint          (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCO2Deadband          (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool initialized_;
   std::string port_;

   Message serialCommand_;     // the last command sent
   Message serialAnswer_;      // the last answer received

   static Message GetCRCChecksum(Message msg);
   static Message HexString2Message(string str);
   static string Message2HexString(Message msg);
   static unsigned long Message2ULong(Message msg);
   static unsigned int Message2UInt(Message msg);
   static Message ULong2Message(unsigned long myint, size_t length);
   static Message GetSubMessage(Message msg, int start, size_t length);
   static Message GetMessageEnd(Message msg, size_t len);
};


#endif //_OVP_ECS2_H_
