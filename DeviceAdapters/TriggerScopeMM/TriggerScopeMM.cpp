///////////////////////////////////////////////////////////////////////////////
// FILE:          TriggerScopeMM.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the ARC TriggerScope device adapter.
//				  See http://www.trggerscope.com
//                
// AUTHOR:        Austin Blanco, 21 July 2015
//                Nico Stuurman, 3 Sept 2020                  
//
// COPYRIGHT:     Advanced Research Consulting. (2014-2015)
//                Regents of the University of California
//
// LICENSE:       

//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "TriggerScopeMM.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <algorithm>
#include <iostream>


using namespace std;

// External names used used by the rest of the system
// to load particular device from the "TriggerScope.dll" library
const char* g_TriggerScopeHubDeviceName = g_TriggerScopeMMHubName;

const char* g_Keyword_Clear = "Clear Arrays";
const char* g_Keyword_Clear_Off = "Off";
const char* g_Keyword_Clear_Active_Array = "Clear Active Array";
const char* g_Keyword_Clear_All_Arrays = "Clear All Arrays";


const char* serial_terminator = "\n";
const char* answer_terminator = "\r\n";
const unsigned char uc_serial_terminator = 10;
const char* line_feed = "\n";
const char * g_TriggerScope_Version = "v1.0-MM, 8/24/2020";




// static lock
MMThreadLock CTriggerScopeMMHub::lock_;

// TODO: linux entry code

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supperted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_TriggerScopeMMHubName, MM::HubDevice, "Hub (required)");

   RegisterDevice(g_TriggerScopeMMTTLDeviceName1, MM::StateDevice, "TTL1-8");
   RegisterDevice(g_TriggerScopeMMTTLDeviceName2, MM::StateDevice, "TTL9-16");

   std::string deviceName = g_TriggerScopeMMDACDeviceName;
   std::string shortName = "DAC01";
   char number[3] = "01";
   for (int c = 1; c <= 16; c++) 
   {
      snprintf(number, 3, "%02d", c);
      deviceName.replace(deviceName.length() - 2, 2, number);
      shortName.replace(shortName.length() - 2, 2, number);
      RegisterDevice(deviceName.c_str(), MM::SignalIODevice, shortName.c_str());
   }

}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   char baseName[MM::MaxStrLength];
   strncpy(baseName, deviceName, strlen(deviceName) - 2);
   baseName[strlen(deviceName) - 2] = 0;

   char referenceName[MM::MaxStrLength];
   strncpy(referenceName, g_TriggerScopeMMDACDeviceName, strlen(g_TriggerScopeMMDACDeviceName) - 2);
   referenceName[strlen(g_TriggerScopeMMDACDeviceName) - 2] = 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_TriggerScopeMMHubName) == 0)
   {
      // create hub
      return new CTriggerScopeMMHub();
   }
   else if (strcmp(baseName, referenceName) == 0)
   {
      std::string dName = deviceName;
      std::string number = dName.substr(dName.length() - 2, 2);
      int nr = atoi(number.c_str());
      return new CTriggerScopeMMDAC(nr);
   }
   else if (strcmp(deviceName, g_TriggerScopeMMTTLDeviceName1) == 0)
   {
      return new CTriggerScopeMMTTL(0);
   }

	else if (strcmp(deviceName, g_TriggerScopeMMTTLDeviceName2) == 0)
   {
      return new CTriggerScopeMMTTL(1);
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CTriggerScopeMMHub implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CTriggerScopeMMHub constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/

///////////////////////////////////////////////////////////////////////////////
// TriggerScope implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~
 
CTriggerScopeMMHub::CTriggerScopeMMHub(void)  :
   busy_(false),
   error_(0),
   fidSerialLog_(NULL),
   firmwareVer_(0.0),
   initialized_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   pResourceLock_ = new MMThreadLock();

   //Com port
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeMMHub::OnCOMPort);
   CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);

   // Error messages
   SetErrorText(ERR_NON_MM_FIRMWARE, "Firmware is not MM firmware and will not work with this adapter");
}

int CTriggerScopeMMHub::DetectInstalledDevices()
{  
   ClearInstalledDevices();

   // make sure this method is called before we look for available devices
   InitializeModuleData();

   char hubName[MM::MaxStrLength];
   GetName(hubName); // this device name
   for (unsigned i=0; i<GetNumberOfDevices(); i++)
   { 
      char deviceName[MM::MaxStrLength];
      bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
      if (success && (strcmp(hubName, deviceName) != 0))
      {
         MM::Device* pDev = CreateDevice(deviceName);
         AddInstalledDevice(pDev);
      }
   }
   return DEVICE_OK; 
}

bool CTriggerScopeMMHub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus CTriggerScopeMMHub::DetectDevice(void)
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];
   
   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Off);
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 2 seconds.
         CDeviceUtils::SleepMs(1000);
         MMThreadGuard myLock(lock_);
         PurgeComPort(port_.c_str());

         std::string answer;
         int ret = SendAndReceiveNoCheck("*", answer);

         if(answer.length() > 0)
	      {
            size_t idx = answer.find("ARC TRIGGERSCOPE 16");
            if(idx!=string::npos)
            {
               if (answer.substr(answer.length() - 2, 2) == "MM") 
               {
                  result = MM::CanCommunicate;
               }
            }
         }
            
         if( DEVICE_OK != ret )
         {
            LogMessageCode(ret,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // always restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}


/**
* CTriggerScopeMMHub destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CTriggerScopeMMHub::~CTriggerScopeMMHub(void)
{                                                             
   Shutdown();
   delete pResourceLock_;
}


void CTriggerScopeMMHub::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeHubDeviceName);
}


int CTriggerScopeMMHub::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_TriggerScopeHubDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "TriggerScope", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version
   char str[256];
   bTS16_ = false;

   // get firmware version
   ret = Purge();
   if (ret != DEVICE_OK)
      return ret;
	firmwareVer_  = 0.0;
   std::string answer = "";
   ret = SendAndReceiveNoCheck("*", answer);
   int attempts = 1;
   while (ret != DEVICE_OK && attempts < 10)
   {
      CDeviceUtils::SleepMs(1000);
      ret = SendAndReceiveNoCheck("*", answer);
      attempts++;
   }
   if (ret != DEVICE_OK)
      return ret;

   if(answer.length()>0)
	{
      size_t idx = answer.find("ARC TRIGGERSCOPE 16");
      if(idx!=string::npos)
         bTS16_ = true;			

      idx = answer.find("ARC_LED 16");
      if (idx!=string::npos)
      bTS16_ = true;			
				
      idx = answer.find("ARC TRIGGERSCOPE");

      if (idx==string::npos)   
         idx = answer.find("ARC_LED");

      if (idx!=string::npos)
      {
         idx = answer.find("v.");
         if (idx!=string::npos)
            firmwareVer_ = atof(&(answer.c_str()[idx+2]));
         else
         {
            idx = answer.find("v");
            if (idx!=string::npos)
               firmwareVer_ = atof(&(answer.c_str()[idx+1]));
         }
         if (answer.substr(answer.length() - 2, 2) != "MM") 
         {
            return ERR_NON_MM_FIRMWARE;
         }
		}
	}

	if (firmwareVer_==0.0)
		return DEVICE_SERIAL_TIMEOUT;

	if (answer.length()>0)
		snprintf(str, 256, "%s", answer.c_str());
	else
	{
      return DEVICE_SERIAL_TIMEOUT;
	}
   ret = CreateProperty("Firmware Version", str, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = CreateProperty("Software Version", g_TriggerScope_Version, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   if(bTS16_)
	   ret = CreateProperty("DAC Bits", "16", MM::String, true);
   else
	   ret = CreateProperty("DAC Bits", "12", MM::String, true);

   if (DEVICE_OK != ret)
      return ret;


   CreateProperty("COM Port", port_.c_str(), MM::String, true);

   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeMMHub::OnSendSerialCmd);
	ret = CreateProperty("Serial Send", "", MM::String, false, pAct);
	assert(ret == DEVICE_OK);

   pAct = new CPropertyAction (this, &CTriggerScopeMMHub::OnRecvSerialCmd);
	ret = CreateProperty("Serial Receive", "", MM::String, true, pAct);
	assert(ret == DEVICE_OK);
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   
   return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CTriggerScopeMMHub::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool CTriggerScopeMMHub::Busy()
{
   // TODO: return true if lock is in use?  How does one code that?
   return false;
}



int CTriggerScopeMMHub::Purge()
{
	unsigned char buf[1024];
	unsigned long bytesRead=0;

	int ret = ReadFromComPort(port_.c_str(), &buf[0], 1024, bytesRead);
   if (ret != DEVICE_OK)
      return ret;

   return PurgeComPort(port_.c_str());
}



int CTriggerScopeMMHub::OnSendSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(sendSerialCmd_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(sendSerialCmd_);
	   recvSerialCmd_ = "";

      MMThreadGuard myLock(lock_);
      int ret = SendSerialCommand(port_.c_str(), sendSerialCmd_.c_str(), serial_terminator);
      if (ret != DEVICE_OK)
         return ret;
      ret = GetSerialAnswer(port_.c_str(), answer_terminator, recvSerialCmd_);
      if (ret != DEVICE_OK)
         return ret;

      // TODO: for commands that generate more than one line, continue reading, e.g. "?", "STAT?"

      return PurgeComPort(port_.c_str());

   }
   return DEVICE_OK;
}

int CTriggerScopeMMHub::OnRecvSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(recvSerialCmd_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(recvSerialCmd_);
   }
   return DEVICE_OK;
}


int CTriggerScopeMMHub::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int CTriggerScopeMMHub::SendAndReceive(string command)
{
   std::string answer;
   return SendAndReceive(command, answer);
}

int CTriggerScopeMMHub::SendAndReceiveNoCheck(string command, string &answer)
{
   MMThreadGuard myLock(lock_);
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), serial_terminator);
   if (ret != DEVICE_OK)
      return ret;
   return GetSerialAnswer(port_.c_str(), answer_terminator, answer);
}

int CTriggerScopeMMHub::SendAndReceive(string command, string &answer)
{
   MMThreadGuard myLock(lock_);
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), serial_terminator);
   if (ret != DEVICE_OK)
      return ret;
   ret = GetSerialAnswer(port_.c_str(), answer_terminator, answer);
   if (ret != DEVICE_OK)
      return ret;
   if (command.substr(0,3) == answer.substr(1,3))
      return DEVICE_OK;

   return DEVICE_SERIAL_INVALID_RESPONSE;
}
