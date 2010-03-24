///////////////////////////////////////////////////////////////////////////////
// FILE:          OlympusMT.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   OlympusMT MTShutter adapter
// COPYRIGHT:     University of California, San Francisco, 2010
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/24/2010
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           
//



#ifdef WIN32
//   #include <windows.h>
//   #define snprintf _snprintf 

#endif


#include "../../MMDevice/MMDevice.h"
#include "OlympusMT.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"


// MTShutter
const char* g_MTShutterName = "OlympusMTShutter";




///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_MTShutterName, "OlympusMTShutter");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_MTShutterName) == 0)
   {
      // create MTShutter
      MTShutter* pMTShutter = new MTShutter(g_MTShutterName);
      return pMTShutter;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// MTShutter implementation
// ~~~~~~~~~~~~~~~~~~~~

MTShutter::MTShutter(const char* name) :
   initialized_(false), 
   state_(false),
   name_(name), 
   error_(0),
   changedTime_(0)
{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "OlympusMT Shutter", MM::String, true);

}

MTShutter::~MTShutter()
{
   Shutdown();
}

bool MTShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void MTShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int MTShutter::Initialize()
{

   // set property list
   // -----------------
  
   this->LogMessage("MTShutter::Initialize()");

   int err = socket_.connect();
   if (err != DEVICE_OK)
      return err;

   initialized_ = true;
   return DEVICE_OK; //HandleErrors();

}

int MTShutter::Shutdown()
{
   if (initialized_)
   {
      socket_.disconnect();
      initialized_ = false;
   }
   return DEVICE_OK; //HandleErrors();
}


/////////////////////////////////////
//  Communications
/////////////////////////////////////


void MTShutter::Send(string cmd)
{
   socket_.Send(cmd);
}


void MTShutter::Receive()
{

}

//********************
// Shutter API
//********************

int MTShutter::SetOpen(bool open)
{
   stringstream buf;
   buf << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<PCMsg MsgId=\"9\">\n<Experiment ExpId=\"999\">\n<Commands ComId=\"1\">\n<SetShutter Id=\"BB.0-LS.0-Shut.0\" Par=\"Val\"><WaitTime>100</WaitTime><State>";
   if (open)
      buf << "1";
   else
      buf << "0";
   buf << "</State><Store>0</Store></SetShutter>\n</Commands>\n</Experiment>\n</PCMsg>";

   buf << "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<PCMsg MsgId=\"13\">\n<Controlling>\n<GoExp>999</GoExp>\n</Controlling>\n</PCMsg>\n";

   string cmd;
   buf >> cmd;

   Send(cmd);

   state_ = open;
   changedTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}

int MTShutter::GetOpen(bool& open)
{
   open = state_;
   return DEVICE_OK;
}

int MTShutter::Fire(double deltaT)
{
   deltaT=0; // Suppress warning
   return DEVICE_UNSUPPORTED_COMMAND;
}