///////////////////////////////////////////////////////////////////////////////
// FILE:          AOTF.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   AOTF parallel port adapter 
//
// NOTE:          Windows specific implementation uses inpout.dll by
//                http://www.logix4u.net/inpout32.htm to talk to the parallel port
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
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 03/15/2006
//
// CVS:           $Id$
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "AOTF.h"
#include "../../MMDevice/ModuleInterface.h"

const char* g_DeviceNameAOTFSwitch = "AOTF-Switch";
const char* g_DeviceNameAOTFShutter = "AOTF-Shutter";

// hardcoded to LPT1 
const short g_addrLPT1 = 0x378;

// Global state of the AOTF switch to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_switchState = 0;
unsigned g_shutterState = 0;

using namespace std;

// global constants

/* ----Prototypes for inpout.dll--- */
short _stdcall Inp32(short PortAddress);
void _stdcall Out32(short PortAddress, short data);


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
   AddAvailableDeviceName(g_DeviceNameAOTFSwitch, "TLL digital out AOTF wavelength selector");
   AddAvailableDeviceName(g_DeviceNameAOTFShutter, "TTL digital out AOTF shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameAOTFSwitch) == 0)
   {
      return new CAOTFSwitch;
   }
   else if (strcmp(deviceName, g_DeviceNameAOTFShutter) == 0)
   {
      return new CAOTFShutter;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CAOTFSwitch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CAOTFSwitch::CAOTFSwitch() : numPos_(256), busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the port failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the port");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the port");
}

CAOTFSwitch::~CAOTFSwitch()
{
   Shutdown();
}

void CAOTFSwitch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameAOTFSwitch);
}


int CAOTFSwitch::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameAOTFSwitch, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "AOTF swicthbox driver (LPT)", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CAOTFSwitch::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CAOTFSwitch::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CAOTFSwitch::WriteToPort(long lnValue)
{
   unsigned short buf = (unsigned short) lnValue;
   Out32(g_addrLPT1, buf);
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CAOTFSwitch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      g_switchState = pos;
      if (g_shutterState > 0)
         return WriteToPort(pos);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CAOTFShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CAOTFShutter::CAOTFShutter() : initialized_(false), name_(g_DeviceNameAOTFShutter), openTimeUs_(0)
{
   InitializeDefaultErrorMessages();
   EnableDelay();
}

CAOTFShutter::~CAOTFShutter()
{
   Shutdown();
}

void CAOTFShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CAOTFShutter::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;

   if (interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int CAOTFShutter::Initialize()
{

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "AOTF shutter driver (LPT)", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CAOTFShutter::OnOnOff);
   ret = CreateProperty("OnOff", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set shutter into the off state
   WriteToPort(0);

   vector<string> vals;
   vals.push_back("0");
   vals.push_back("1");
   ret = SetAllowedValues("OnOff", vals);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   openTimeUs_ = GetClockTicksUs();

   return DEVICE_OK;
}

int CAOTFShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CAOTFShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CAOTFShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CAOTFShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CAOTFShutter::WriteToPort(long lnValue)
{
   unsigned short buf = (unsigned short) lnValue;
   Out32(g_addrLPT1, buf);
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CAOTFShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // use cached state
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos == 0)
         WriteToPort(0); // turn everything off
      else
         WriteToPort(g_switchState); // restore old setting
      g_shutterState = pos;
      openTimeUs_ = GetClockTicksUs();
   }

   return DEVICE_OK;
}
