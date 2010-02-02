///////////////////////////////////////////////////////////////////////////////
// FILE:          FilterWheel.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Brian Ashcroft, ashcroft@physics.leidenuniv.nl 03/14/2009
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "FilterWheel.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
using namespace std;

// External names used used by the rest of the system
// to load particular device from the "DemoCamera.dll" library

const char* g_WheelDeviceName = "Thorlabs Filter Wheel";


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

MODULE_API void InitializeModuleData()
{
   
   AddAvailableDeviceName(g_WheelDeviceName, "Thorlabs filter wheel");
  
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

 
   if (strcmp(deviceName, g_WheelDeviceName) == 0)
   {
      // create filter wheel
      return new CDemoFilterWheel();
   }


   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}



///////////////////////////////////////////////////////////////////////////////
// CDemoFilterWheel implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDemoFilterWheel::CDemoFilterWheel() : 
   numPos_(6), 
   busy_(false), 
   initialized_(false), 
   changedTime_(0.0),
   position_(0),
   port_("")
{
   InitializeDefaultErrorMessages();

   //Com port
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoFilterWheel::OnCOMPort);
   CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);

   EnableDelay(); // signals that the dealy setting will be used
}

CDemoFilterWheel::~CDemoFilterWheel()
{
   Shutdown();
}

void CDemoFilterWheel::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_WheelDeviceName);
}


int CDemoFilterWheel::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   


   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_WheelDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Thorlabs filter wheel driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 'delay' time into the past
   changedTime_ = GetCurrentMMTime();   

   // create default positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%ld", i + 1);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDemoFilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool CDemoFilterWheel::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}


int CDemoFilterWheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDemoFilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      printf("Getting position of Wheel device\n");
      pProp->Set(position_);
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      // Set timer for the Busy signal
      changedTime_ = GetCurrentMMTime();

      long pos;
      pProp->Get(pos);
      //char* deviceName;
      //GetName(deviceName);
      printf("Moving to position %ld\n", position_);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_UNKNOWN_POSITION;
      }
	  const int bufSize = 20;
      char buf[bufSize];
      snprintf(buf, bufSize, "pos=%ld", pos + 1);
	   
	  SendSerialCommand(port_.c_str(),buf,"\r");
      position_ = pos;
   }

   return DEVICE_OK;
}

int CDemoFilterWheel::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

void CDemoFilterWheel::InitializeFilterWheel()
{   
	
	SendSerialCommand(port_.c_str(),"sensors=0","\r");
	SendSerialCommand(port_.c_str(),"pos?","\r");
    std::string ans="";
	GetSerialAnswer (port_.c_str(), "\n",  ans);
    int i=0;
    from_string<int>(i, (ans).c_str(), std::dec);
    position_=i;

}
