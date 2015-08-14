/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#include "ArduinoNeoPixelShutter.h"
#include "ModuleInterface.h"
#include <sstream>
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

const char* g_DeviceNameArduinoNeoPixelHub = "ArduinoNeoPixel-Hub";
const char* g_DeviceNameArduinoNeoPixelShutter = "ArduinoNeoPixel-Shutter";
const char* g_versionProp = "Version";


// static lock
MMThreadLock CArduinoNeoPixelHub::lock_;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameArduinoNeoPixelHub, MM::HubDevice, "Hub (required)");
   RegisterDevice(g_DeviceNameArduinoNeoPixelShutter, MM::ShutterDevice, "Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameArduinoNeoPixelHub) == 0)
   {
      return new CArduinoNeoPixelHub;
   }
   else if (strcmp(deviceName, g_DeviceNameArduinoNeoPixelShutter) == 0)
   {
      return new CArduinoNeoPixelShutter;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CArduinoNeoPixelHUb implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CArduinoNeoPixelHub::CArduinoNeoPixelHub() :
   initialized_ (false),
   shutterState_ (0),
   intensity_(255),
   red_(255), blue_(255), green_(255),
   multi_(7)
{
   portAvailable_ = false;

   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_OPEN_FAILED, "Failed opening Arduino USB device");
   SetErrorText(ERR_BOARD_NOT_FOUND, "Did not find an Arduino board with the correct firmware.  Is the Arduino board connected to this serial port?");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The ArduinoNeoPixel Hub device is needed to create this device");
   std::ostringstream errorText;
   errorText << "The firmware version on the Arduino is not compatible with this adapter.  Please use firmware version ";
   // errorText <<  g_Min_MMVersion << " to " << g_Max_MMVersion;
   SetErrorText(ERR_VERSION_MISMATCH, errorText.str().c_str());

   CPropertyAction* pAct = new CPropertyAction(this, &CArduinoNeoPixelHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

CArduinoNeoPixelHub::~CArduinoNeoPixelHub()
{
   Shutdown();
}

void CArduinoNeoPixelHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduinoNeoPixelHub);
}

bool CArduinoNeoPixelHub::Busy()
{
   return false;
}

// private and expects caller to:
// 1. guard the port
// 2. purge the port
int CArduinoNeoPixelHub::GetControllerVersion(int& version)
{
   int ret = DEVICE_OK;
   version = 0;

  if(!portAvailable_)
    return ERR_NO_PORT_SET;

  std::string answer;

  ret = SendSerialCommand(port_.c_str(), "V", "\r");
  if (ret != DEVICE_OK)
      return ret;

   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK) {
      return ret;
   }

     if (answer != "ArduinoNeoPixelShutter" && answer != "\nArduinoNeoPixelShutter") {
      return ERR_BOARD_NOT_FOUND;
     }
   version = 1; 
   return ret;

}

MM::DeviceDetectionStatus CArduinoNeoPixelHub::DetectDevice(void)
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
         // for Arduino Duemilanova
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         // Arduino timed out in GetControllerVersion even if AnswerTimeout  = 300 ms
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "1000.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 2 seconds.
         CDeviceUtils::SleepMs(2000);
         MMThreadGuard myLock(lock_);
         PurgeComPort(port_.c_str());
         int v = 0;
         int ret = GetControllerVersion(v);
         // later, Initialize will explicitly check the version #
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


int CArduinoNeoPixelHub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduinoNeoPixelHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 1 second.
   CDeviceUtils::SleepMs(2000);

   MMThreadGuard myLock(lock_);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());
   ret = GetControllerVersion(version_);
   if( DEVICE_OK != ret)
      return ret;

   // if (version_ < g_Min_MMVersion || version_ > g_Max_MMVersion)
   //    return ERR_VERSION_MISMATCH;

   CPropertyAction* pAct = new CPropertyAction(this, &CArduinoNeoPixelHub::OnVersion);
   std::ostringstream sversion;
   sversion << version_;
   CreateProperty(g_versionProp, sversion.str().c_str(), MM::Integer, true, pAct);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // turn on verbose serial debug messages
   GetCoreCallback()->SetDeviceProperty(port_.c_str(), "Verbose", "1");

   initialized_ = true;
   return DEVICE_OK;
}

int CArduinoNeoPixelHub::DetectInstalledDevices()
{
   if (MM::CanCommunicate == DetectDevice()) 
   {
      std::vector<std::string> peripherals; 
      peripherals.clear();
      peripherals.push_back(g_DeviceNameArduinoNeoPixelShutter);
      for (size_t i=0; i < peripherals.size(); i++) 
      {
         MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
         if (pDev) 
         {
            AddInstalledDevice(pDev);
         }
      }
   }

   return DEVICE_OK;
}



int CArduinoNeoPixelHub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduinoNeoPixelHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
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

int CArduinoNeoPixelHub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set((long)version_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CArduinoNeoPixelShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduinoNeoPixelShutter::CArduinoNeoPixelShutter() : initialized_(false), name_(g_DeviceNameArduinoNeoPixelShutter)  
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The ArduinoNeoPixel Hub device is needed to create this device");

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduinoNeoPixelShutter, MM::String, true);
   assert(DEVICE_OK == ret);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ArduinoNeoPixel shutter driver", MM::String, true);
   assert(DEVICE_OK == ret);

   // parent ID display
   CreateHubIDProperty();
}

CArduinoNeoPixelShutter::~CArduinoNeoPixelShutter()
{
   Shutdown();
}

void CArduinoNeoPixelShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduinoNeoPixelShutter);
}

bool CArduinoNeoPixelShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   else
       return false;
}

int CArduinoNeoPixelShutter::Initialize()
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // set property list
   // -----------------
   
   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CArduinoNeoPixelShutter::OnOnOff);
   int ret = CreateProperty("OnOff", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &CArduinoNeoPixelShutter::OnIntensity);
   ret = CreateProperty("Intensity", "255", MM::Integer, false, pAct);
   SetPropertyLimits("Intensity", 0, 255);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction(this, &CArduinoNeoPixelShutter::OnRedBrightness);
   ret = CreateProperty("Red brightness", "255", MM::Integer, false, pAct);
   SetPropertyLimits("Red brightness", 0, 255);
   if (ret != DEVICE_OK)
      return ret;
   pAct = new CPropertyAction(this, &CArduinoNeoPixelShutter::OnGreenBrightness);
   ret = CreateProperty("Green brightness", "255", MM::Integer, false, pAct);
   SetPropertyLimits("Green brightness", 0, 255);
   if (ret != DEVICE_OK)
      return ret;
   pAct = new CPropertyAction(this, &CArduinoNeoPixelShutter::OnBlueBrightness);
   ret = CreateProperty("Blue brightness", "255", MM::Integer, false, pAct);
   SetPropertyLimits("Blue brightness", 0, 255);
   if (ret != DEVICE_OK)
      return ret;
   pAct = new CPropertyAction(this, &CArduinoNeoPixelShutter::OnMulti);
   ret = CreateProperty("Multi", "8", MM::Integer, false, pAct);
   SetPropertyLimits("Multi", 0, 1<<3);
   if (ret != DEVICE_OK)
      return ret;


   // set shutter into the off state
   //WriteToPort(0);
   
   std::vector<std::string> vals;
   vals.push_back("0");
   vals.push_back("1");
   ret = SetAllowedValues("OnOff", vals);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   changedTime_ = GetCurrentMMTime();
   initialized_ = true;

   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::SetOpen(bool open)
{
	std::ostringstream os;
	os << "Request " << open;
	LogMessage(os.str().c_str(), true);

   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CArduinoNeoPixelShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduinoNeoPixelShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetShutterState());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      std::string command = "I" + std::to_string((long long)hub->GetIntensity());
      ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
      if (ret != DEVICE_OK)
	return ret;
      if (pos == 0) {
	std::string command = "P0\r";
	int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	if (ret != DEVICE_OK)
	  return ret;
      }
      else {
	std::string command = "P111111111111111111111111111111111111111\r";
	int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	if (ret != DEVICE_OK)
	  return ret;
	int multi = hub->GetMulti();
	LogMessage("multi:");
	if (multi & 0x1) {
	  std::string command = "R" + std::to_string((long long)hub->GetRedBrightness()) + "\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	} else {
	  std::string command = "R0\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	}

	if (multi & 0x2) {
	  std::string command = "G" + std::to_string((long long)hub->GetGreenBrightness()) + "\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	} else {
	  std::string command = "G0\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	}
	if (multi & 0x4) {
	  std::string command = "B" + std::to_string((long long)hub->GetBlueBrightness()) + "\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	} else {
	  std::string command = "B0\r";
	  int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
	  if (ret != DEVICE_OK)
	    return ret;
	}
      }
      hub->SetShutterState(pos);
      changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}


int CArduinoNeoPixelShutter::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetIntensity());
   }
   else if (eAct == MM::AfterSet)
   {
      long intensity;
      pProp->Get(intensity);
      std::string command = "I" + std::to_string((long long)intensity) + "\r";
      int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
      if (ret != DEVICE_OK)
	return ret;
      hub->SetIntensity(intensity);
   }
   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::OnRedBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetRedBrightness());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      std::string command = "R" + std::to_string((long long)pos) + "\r";
      int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
      if (ret != DEVICE_OK)
	return ret;
      hub->SetRedBrightness(pos);
   }
   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::OnGreenBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetGreenBrightness());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      std::string command = "G" + std::to_string((long long)pos) + "\r";
      int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
      if (ret != DEVICE_OK)
	return ret;
      hub->SetGreenBrightness(pos);
   }
   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::OnBlueBrightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetBlueBrightness());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      std::string command = "B" + std::to_string((long long)pos) + "\r";
      int ret = hub->WriteToComPortH((const unsigned char*) command.c_str(), command.length());
      if (ret != DEVICE_OK)
	return ret;
      hub->SetBlueBrightness(pos);
   }
   return DEVICE_OK;
}

int CArduinoNeoPixelShutter::OnMulti(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduinoNeoPixelHub* hub = static_cast<CArduinoNeoPixelHub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetMulti());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      hub->SetMulti(pos);
   }
   return DEVICE_OK;
}
