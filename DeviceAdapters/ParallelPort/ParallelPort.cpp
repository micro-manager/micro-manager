///////////////////////////////////////////////////////////////////////////////
// FILE:       ParallelPort.cpp
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Parallel (printer) port device adapter, for use as a digital
//                output device (8-bit) - Windows only
//
// COPYRIGHT:     University of California San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/01/2005
//
// NOTE:          Windows specific implementation uses inpout.dll by
//                http://www.logix4u.net/inpout32.htm to talk to the
//                parallel port
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/01/2005
//
// CVS:           $Id$
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ParallelPort.h"
#include "../../MMDevice/ModuleInterface.h"


const char* g_DeviceName = "LPT1";

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
   AddAvailableDeviceName(g_DeviceName, "Printer port TTL digital output");
}

   MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceName) == 0)
   {
      return new CParallelPort;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CParallelPort implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~

CParallelPort::CParallelPort() : numPos_(256), busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the port failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the port");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the port");
}

CParallelPort::~CParallelPort()
{
   Shutdown();
}

void CParallelPort::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceName);
}


int CParallelPort::Initialize()
{
   // open the port
   int nRet = OpenPort(g_DeviceName, 0);
   if (nRet != DEVICE_OK)
      return nRet;

   // set property list
   // -----------------
   
   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Parallel port driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "State-%ld", i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CParallelPort::OnState);
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

int CParallelPort::Shutdown()
{
   if (initialized_)
   {
      ClosePort();
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CParallelPort::OpenPort(const char* /*Name*/, long /*lnValue*/)
{
   return DEVICE_OK;
}

int CParallelPort::WriteToPort(long lnValue)
{
   unsigned short buf = (unsigned short) lnValue;
   const short addrLPT1 = 0x378;
   Out32(addrLPT1, buf);
   return DEVICE_OK;
}

int CParallelPort::ClosePort()
{
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CParallelPort::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      return WriteToPort(pos);
   }

   return DEVICE_OK;
}
