//////////////////////////////////////////////////////////////////////////////
// FILE:          NeoPixel.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino board controlling Adafruit NeoPixel
//                Needs accompanying firmware to be installed on the board
// COPYRIGHT:     University of California, San Francisco, 2018
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 09/18/2018
//


#include "NeoPixel.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

const char* g_DeviceNameShutter = "NeoPixel-Shutter";

const int g_Min_MMVersion = 1;
const int g_Max_MMVersion = 1;
const char* g_versionProp = "Version";

const char* g_On = "On";
const char* g_Off = "Off";
const char* g_Blue = "Blue";
const char* g_Red = "Red";
const char* g_Green = "Green";
const char* g_AllActive = "AllPixelsActive";
const char* g_All = "All";
const char* g_None = "None";
const char* g_Some = "Some";

MMThreadLock NeoPixelShutter::lock_;

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameShutter, MM::ShutterDevice, "Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameShutter) == 0) 
   {
      return new NeoPixelShutter();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)             
{ 
      delete pDevice;
}


NeoPixelShutter::NeoPixelShutter() : 
   open_(false),
   portAvailable_(false),                                  
   initialized_ (false),
   color_(g_Blue),
   activeState_(g_None)
{
   InitializeDefaultErrorMessages();   

   SetErrorText(ERR_BOARD_NOT_FOUND, "Did not find an Arduino board with the correct firmware.  Is the Arduino board connected to this serial port?");

   CPropertyAction* pAct = new CPropertyAction(this, &NeoPixelShutter::OnPort);  
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}


NeoPixelShutter::~NeoPixelShutter()
{
   Shutdown();
}


void NeoPixelShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameShutter);
}



int NeoPixelShutter::GetFirmwareVersion(int& version) {
   int ret = DEVICE_OK;
   unsigned char command[1];
   command[0] = 30;

   ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer != "MM-NeoPixel")
      return ERR_BOARD_NOT_FOUND;

   // Check version number of the NeoPixel firmware
   command[0] = 31;
   ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;
                                                              
   std::string ans;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", ans);
   if (ret != DEVICE_OK) {
         return ret;
   }
   std::istringstream is(ans);
   is >> version;

   return ret;
}

int NeoPixelShutter::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameShutter, MM::String, true);
   if (DEVICE_OK != ret)
      return ret; 

   // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 1 second.
   CDeviceUtils::SleepMs(2000);
 
   CPropertyAction* pAct = new CPropertyAction(this, &NeoPixelShutter::OnColor);
   CreateProperty("NeoPixelColor", color_.c_str(), MM::String, false, pAct);
   AddAllowedValue("NeoPixelColor", g_Red);
   AddAllowedValue("NeoPixelColor", g_Green);
   AddAllowedValue("NeoPixelColor", g_Blue);

   pAct = new CPropertyAction(this, &NeoPixelShutter::OnAllActive);
   CreateProperty(g_AllActive, activeState_.c_str(), MM::String, false, pAct);
   AddAllowedValue(g_AllActive, g_All);
   AddAllowedValue(g_AllActive, g_None);
   AddAllowedValue(g_AllActive, g_Some);

   pAct = new CPropertyAction(this, &NeoPixelShutter::OnOnOff);
   CreateProperty("OnOff", g_Off, MM::String, false, pAct);
   AddAllowedValue("OnOff", g_On);
   AddAllowedValue("OnOff", g_Off);


   MMThreadGuard myLock(lock_);
   // Check that we have a controller:
   PurgeComPort(port_.c_str());
   ret = GetFirmwareVersion(version_);
   if( DEVICE_OK != ret)
      return ret;
                                                              
   if (version_ < g_Min_MMVersion || version_ > g_Max_MMVersion)
      return ERR_VERSION_MISMATCH;                            
                                                              
   pAct = new CPropertyAction(this, &NeoPixelShutter::OnVersion);
   std::ostringstream sversion;                               
   sversion << version_;                                      
   CreateProperty(g_versionProp, sversion.str().c_str(), MM::Integer, true, pAct);

   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
} 

int NeoPixelShutter::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

bool NeoPixelShutter::Busy()
{
   return false;
}
   
int NeoPixelShutter::SetOpen(bool open)
{
   unsigned char command[1];
   command[0] = 2;
   if (open)
      command[0] = 1;

   MMThreadGuard myLock(lock_);
   int ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
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
   if (answer[0] != command[0])                                        
      return ERR_COMMUNICATION;                               

   open_ = open;

   return DEVICE_OK;
}

int NeoPixelShutter::GetOpen(bool& open)
{
   open = open_;
   return DEVICE_OK;
}

int NeoPixelShutter::Fire(double deltaT)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int NeoPixelShutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)                             
   {                                                          
      pProp->Get(port_);                                      
      portAvailable_ = true;                                  
   }                                                          
   return DEVICE_OK;   
} 

int NeoPixelShutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)                                 
   { 
      pProp->Set((long)version_);                             
   } 
   return DEVICE_OK; 
}

int NeoPixelShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::string state = g_Off;
      if (open_)
        state = g_On;
     pProp->Set(state.c_str());
   } else if (eAct == MM::AfterSet) 
   {
      std::string state;
      pProp->Get(state);
      bool set = false;
      if (state == g_On)
         set = true;
      return SetOpen(set);
   }
   return DEVICE_OK;
}

int NeoPixelShutter::OnColor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(color_.c_str());
   } else if (eAct == MM::AfterSet) 
   {
     pProp->Get(color_);

     unsigned char command[] = {7, 0, 0, 0};
     if (color_ == g_Red)
        command[1] = 255;
     if (color_ == g_Green)
        command[2] = 255;
     if (color_ == g_Blue)
        command[3] = 255;

      MMThreadGuard myLock(lock_);
      int ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 4);
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
      if (answer[0] != command[0])                                        
         return ERR_COMMUNICATION;                               
   }

   return DEVICE_OK;
}

int NeoPixelShutter::OnAllActive(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(activeState_.c_str());
   } 
   else if (eAct == MM::AfterSet) 
   {
      std::string state;
      pProp->Get(state);

      if (state == g_Some) 
      {
         // g_Some should be a read-only property
         return DEVICE_OK;
      }


      unsigned char command[] = {3};
      if (state == g_None)
         command[0] = 4;

      MMThreadGuard myLock(lock_);
      int ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
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
      if (answer[0] != command[0])                                        
         return ERR_COMMUNICATION;                               
   }
   return DEVICE_OK;
   
} 

int NeoPixelShutter::OnSelectPixel(MM::PropertyBase* pProp, MM::ActionType eAct){
   return DEVICE_OK;
};
                                                              



