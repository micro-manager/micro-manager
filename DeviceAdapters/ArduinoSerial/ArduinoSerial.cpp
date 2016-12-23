///////////////////////////////////////////////////////////////////////////////
// FILE:          Arduino.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Arduino adapter for sending serial commands as a property value.  Needs accompanying firmware
// COPYRIGHT:     University of California, Berkeley, 2016
// LICENSE:       LGPL
// 
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016         
//
//

#include "ArduinoSerial.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


const char* g_DeviceNameArduinoSerial = "Arduino-Serial";
const char* g_Prop_SerialCommand = "Serial-Command";


// Global info about the state of the Arduino.  This should be folded into a class
const int g_Min_MMVersion = 1;
const int g_Max_MMVersion = 2;
const char* g_versionProp = "Version";
const char* g_normalLogicString = "Normal";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameArduinoSerial, MM::GenericDevice, "Arduino serial device");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameArduinoSerial) == 0)
   {
      return new CArduinoSerial;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CArduinoSerial implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduinoSerial::CArduinoSerial() : initialized_(false), name_(g_DeviceNameArduinoSerial)
{
   portAvailable_ = false;
   timedOutputActive_ = false;

   InitializeDefaultErrorMessages();
  // EnableDelay();

   //initialization property: port name
    CPropertyAction* pAct = new CPropertyAction(this, &CArduinoSerial::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
  
}

CArduinoSerial::~CArduinoSerial()
{
   Shutdown();
}

bool CArduinoSerial::Busy() 
{
	return false;
}

void CArduinoSerial::GetName(char* name) const 
{
CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduinoSerial);
}

int CArduinoSerial::Initialize()
{
	   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduinoSerial, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

	 // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Arduino serial driver", MM::String, true);
   assert(DEVICE_OK == ret);

    //Serial command
    CPropertyAction* pAct2 = new CPropertyAction(this, &CArduinoSerial::OnSerialCommand);
   CreateProperty(g_Prop_SerialCommand, "", MM::String, false, pAct2);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());


   // set property list
   // -----------------
   



   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   changedTime_ = GetCurrentMMTime();
   initialized_ = true;

   return DEVICE_OK;
}

int CArduinoSerial::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


int CArduinoSerial::WriteToPort(long value)
{

   if (IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(GetLock());

   value = 63 & value;

   PurgeComPort(port_.c_str());

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret =  WriteToComPort(port_.c_str(), command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = ReadFromComPort(port_.c_str(), answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

   SetTimedOutput(false);

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduinoSerial::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      portAvailable_ = true;
   }
   return DEVICE_OK;
}

int CArduinoSerial::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("");
   }
   else if (pAct == MM::AfterSet)
   {
      //Send the command
	   pProp->Get(lastCommand_);
	   WriteToComPort(port_.c_str(), reinterpret_cast<const unsigned char *>(lastCommand_.c_str()),lastCommand_.length());

   }
   return DEVICE_OK;
}

