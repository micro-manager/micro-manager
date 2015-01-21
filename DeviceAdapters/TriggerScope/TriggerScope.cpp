///////////////////////////////////////////////////////////////////////////////
// FILE:          TriggerScope.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the ARC TriggerScope device adapter.
//                See http://www.trggerscope.com
//
// AUTHOR:        Austin Blanco, 5 Oct 2014
//
// COPYRIGHT:     Advanced Research Consulting. (2014)
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.

#include "TriggerScope.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "..\..\MMDevice\ModuleInterface.h"
#include "..\..\MMCore\Error.h"
#include <sstream>
#include <algorithm>
#include <iostream>

#include "TriggerScope.h"

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "TriggerScope.dll" library
const char* g_TriggerScopeDeviceName = "TriggerScope";
const char* serial_terminator = "\n";
const unsigned char uc_serial_terminator = 10;
const char* line_feed = "\n";
const char * g_TriggerScope_Version = "v1.0.2, 28/9/2014";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice("TriggerScope", MM::GenericDevice, "TriggerScope");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_TriggerScopeDeviceName) == 0)
   {
      // create camera
      return new CTriggerScope();
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CTriggerScope implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * CTriggerScope constructor.
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

CTriggerScope::CTriggerScope(void)  :
   busy_(false),
   error_(0),
   timeOutTimer_(0),
   fidSerialLog_(NULL),
   firmwareVer_(0.0),
   cmdInProgress_(0),
   initialized_(0),
   nUseSerialLog_(1)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   pResourceLock_ = new MMThreadLock();

   //Com port
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScope::OnCOMPort);

   //CreateProperty(MM::g_Keyword_BaudRate, "57600", MM::String, false);
   CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
}

/**
 * CTriggerScope destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
CTriggerScope::~CTriggerScope(void)
{
   Shutdown();
   delete pResourceLock_;
}


void CTriggerScope::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeDeviceName);
}

int CTriggerScope::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   char profilepath[1000];
#ifdef _WIN32
   ExpandEnvironmentStrings(TEXT("%userprofile%"),profilepath,1000);

#else
   //strcpy_s(profilepath,1000,"/tmp");
#endif
   char strLog[1024];

   time_t rawtime;
   time ( &rawtime );

#ifdef _WIN32
   sprintf_s(strLog,1024,"%s\\TriggerScope_SerialLog.txt",profilepath);
#else
   sprintf_s(strLog,1024,"/tmp/TriggerScope_Serial_Log_%s_%d.txt",buffer,getpid());
#endif
   fidSerialLog_ = fopen(strLog,"w");

   nUseSerialLog_ = 1;
   if(fidSerialLog_)
      fprintf(fidSerialLog_, "Version: %s, Time: %s\n", g_TriggerScope_Version, ctime (&rawtime) );
   else
      nUseSerialLog_ = 0;

   zeroTime_ = GetCurrentMMTime();

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_TriggerScopeDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "TriggerScope", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = HandleErrors();
   if (DEVICE_OK != ret)
      return ret;

   // Version
   char str[256];

   cmdInProgress_ = 1;

   CPropertyAction* pAct = NULL;


   firmwareVer_  = 0.0;
   for(int ii=0;ii<10;ii++)
   {
      Sleep(1000);
      Purge();
      Send("*");
      ReceiveOneLine(1);
      if(buf_string_.length()>0)
      {
         size_t idx = buf_string_.find("ARC TRIGGERSCOPE");
         if(idx!=string::npos)
         {
            idx = buf_string_.find("v.");
            firmwareVer_ = atof(&(buf_string_.c_str()[idx+2]));
            break;
         }
      }
   }
   if(firmwareVer_==0.0)
      return DEVICE_SERIAL_TIMEOUT;

   if(buf_string_.length()>0)
      sprintf_s(str, 256, "%s", buf_string_.c_str());
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

   Send("STAT?");
   ReceiveOneLine(1);
   if(buf_string_.length()>0)
   {
   }

   CreateProperty("COM Port", port_.c_str(), MM::String, true);

   pAct = new CPropertyAction (this, &CTriggerScope::OnTTL1);
   ret = CreateProperty("TTL 1", "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("TTL 1", 0, 1);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &CTriggerScope::OnTTL2);
   ret = CreateProperty("TTL 2", "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("TTL 2", 0, 1);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &CTriggerScope::OnDAC);
   ret = CreateProperty("Analog Out", "0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("Analog Out", 0, 5);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   cmdInProgress_ = 0;

   //thd_->Start();

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
int CTriggerScope::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool CTriggerScope::Busy()
{
   if (timeOutTimer_ == 0)
      return false;
   if (timeOutTimer_->expired(GetCurrentMMTime()))
   {
      // delete(timeOutTimer_);
      return false;
   }
   return true;
}

int CTriggerScope::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
// none implemented


int CTriggerScope::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CTriggerScope::OnTTL1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ttl1_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ttl1_);
      char str[16];
      sprintf_s(str, "TTL1,%d",ttl1_);
      Purge();
      Send(str);
      ReceiveOneLine();
      if(buf_string_.length()==0)
      {
         Purge();
         Send(str);
         ReceiveOneLine();
      }
   }

   return DEVICE_OK;
}

int CTriggerScope::OnTTL2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ttl2_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(ttl2_);
      char str[16];
      sprintf_s(str, "TTL2,%d",ttl2_);
      Purge();
      Send(str);
      ReceiveOneLine();
      if(buf_string_.length()==0)
      {
         Purge();
         Send(str);
         ReceiveOneLine();
      }
   }

   return DEVICE_OK;
}

int CTriggerScope::OnDAC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(dac_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(dac_);
      char str[16];
      // 12 bit DAC, 5V max
      sprintf_s(str, "DAC,%d",int(4095.0*dac_/5.0));
      Purge();
      Send(str);
      ReceiveOneLine();

      if(buf_string_.length()==0)
      {
         Purge();
         Send(str);
         ReceiveOneLine();
      }
   }

   return DEVICE_OK;
}

/////////////////////////////////////
//  Communications
/////////////////////////////////////


void CTriggerScope::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), serial_terminator);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
   currentTime_ = GetCurrentMMTime();
   if(strlen(cmd.c_str()) && fidSerialLog_ && nUseSerialLog_)
   {
      fprintf(fidSerialLog_, "%.3f > ",(currentTime_-zeroTime_).getMsec()/1000.0 );

      fprintf(fidSerialLog_, "%s\n", cmd.c_str());
   }
}

void CTriggerScope::SendSerialBytes(unsigned char* cmd, unsigned long len)
{
   int ret = WriteToComPort(port_.c_str(), cmd, len);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;

   currentTime_ = GetCurrentMMTime();
   if(nUseSerialLog_ && fidSerialLog_)
   {
      fprintf(fidSerialLog_, "%.3f > ",(currentTime_-zeroTime_).getMsec()/1000.0 );

      for(unsigned long ii=0; ii<len; ii++)
         fprintf(fidSerialLog_, "%02X ",cmd[ii]);

      fprintf(fidSerialLog_, "\n");
   }
}




void CTriggerScope::ReceiveOneLine(int nLoopMax)
{
   buf_string_ = "";
   int nLoop=0, nRet=-1;
   string buf_str;
   while(nRet!=0 && nLoop<nLoopMax)
   {
      nRet = GetSerialAnswer(port_.c_str(), serial_terminator, buf_str);
      nLoop++;
      if(buf_str.length()>0)
         buf_string_.append(buf_str);
   }
   if(nLoop>1)
      nLoop += 0;
   currentTime_ = GetCurrentMMTime();
   if(strlen(buf_string_.c_str()) && fidSerialLog_ && nUseSerialLog_ )
   {
      fprintf(fidSerialLog_, "%.3f < ",(currentTime_-zeroTime_).getMsec()/1000.0 );

      fprintf(fidSerialLog_, "%s\n", buf_string_.c_str());
   }
}

void CTriggerScope::ReceiveSerialBytes(unsigned char* buf, unsigned long buflen, unsigned long bytesToRead, unsigned long &totalBytes)
{
   buf_string_ = "";
   int nLoop=0, nRet=0;
   unsigned long bytesRead=0;
   totalBytes=0;
   buf[0] = NULL;

   MM::MMTime timeStart, timeNow;
   timeStart = GetCurrentMMTime();

   while(nRet==0 && totalBytes < bytesToRead && (timeNow.getMsec()-timeStart.getMsec()) < 5000)
   {
      nRet = ReadFromComPort(port_.c_str(), &buf[totalBytes], buflen-totalBytes, bytesRead);
      nLoop++;
      totalBytes += bytesRead;
      timeNow = GetCurrentMMTime();
      Sleep(1);
   }
   if(nLoop>1)
      nLoop += 0;

   if(nUseSerialLog_ && fidSerialLog_)
   {
      if(totalBytes>0)
      {
         currentTime_ = GetCurrentMMTime();
         fprintf(fidSerialLog_, "%.3f < ",(currentTime_-zeroTime_).getMsec()/1000.0 );
         for(unsigned long ii=0; ii<totalBytes; ii++)
            fprintf(fidSerialLog_, "%02X ",buf[ii]);

         fprintf(fidSerialLog_, "\n");
      }

      if(timeNow.getMsec()-timeStart.getMsec() > 5000)
         fprintf(fidSerialLog_, "$ Timeout\n");
   }
}

void CTriggerScope::FlushSerialBytes(unsigned char* buf, unsigned long buflen)
{
   buf_string_ = "";
   int nRet=0;
   unsigned long bytesRead=0;
   buf[0] = NULL;

   nRet = ReadFromComPort(port_.c_str(), buf, buflen, bytesRead);

   if(nUseSerialLog_ && fidSerialLog_)
   {
      if(bytesRead>0)
      {
         fprintf(fidSerialLog_, "* ");
         for(unsigned long ii=0; ii<bytesRead; ii++)
            fprintf(fidSerialLog_, "%02X ",buf[ii]);

         fprintf(fidSerialLog_, "\n");
      }
   }
}


void CTriggerScope::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}
