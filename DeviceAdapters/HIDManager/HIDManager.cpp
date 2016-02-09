///////////////////////////////////////////////////////////////////////////////
// FILE:          HIDManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   General interface to HID devices
//
// COPYRIGHT:     University of California, San Francisco, 2013
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Nico Stuurman, Dec. 2013
//
//

#ifndef _std_iostream_INCLUDED_
#include <iostream>
#define _std_iostream_INCLUDED_
#endif

#include <hidapi/hidapi.h>

#ifndef _HID_h_
#include "HIDManager.h"
#endif


#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include <cstdio>
#include <cmath>
#include <sstream>
#include <algorithm>

#ifndef WIN32
   #include <sys/time.h>
#endif

#define HID_TIMEOUT 20

using namespace std;

static HIDManager g_hidManager;
static std::vector<std::string> g_deviceList;
static MM::MMTime g_deviceListLastUpdated = MM::MMTime(0);

/*
 * Struct containing device name, vendor ID, device ID, ports for sending and receiving and data length
 */
static HIDDeviceInfo g_knownDevices[] = {
   {"K8055-0-HID", 0x10cf, 0x5500},
   {"K8055-1-HID", 0x10cf, 0x5501},
   {"K8055-2-HID", 0x10cf, 0x5502},
   {"K8055-3-HID", 0x10cf, 0x5503},
   {"LMM5-HID", 0x1bdb, 0x0300},
   {"Andor-AMH200-FOS", 0x136e, 0x1001},
   {"MJC-SaimScan", 0x0461, 0x0004}
};
static int g_numberKnownDevices = sizeof(g_knownDevices) / sizeof(HIDDeviceInfo);

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   std::string deviceName;
   // Get the ports available on this system from our HIDDeviceLister class
   HIDDeviceLister* deviceLister = new HIDDeviceLister();
   std::vector<std::string> availableDevices;
   deviceLister->ListCachedHIDDevices(availableDevices);

   // Now make them known to the core
   vector<string>::iterator iter = availableDevices.begin();                  
   while (iter != availableDevices.end()) {                                   
      deviceName = *iter;
      RegisterDevice(deviceName.c_str(), MM::SerialDevice, "HID device");
      ++iter;                                                                
   }
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   return g_hidManager.CreatePort(deviceName);
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   g_hidManager.DestroyPort(pDevice);
}

///////////////////////////////////////////////////////////////////////////////
// HIDManager
///////////////////////////////////////////////////////////////////////////////

HIDManager::HIDManager()
{
}

HIDManager::~HIDManager()
{
   vector<MDHIDDevice*>::iterator i;
   for (i=devices_.begin(); i!=devices_.end(); i++)
      delete *i;
}

MM::Device* HIDManager::CreatePort(const char* deviceName)
{
   // check if the port already exists
   vector<MDHIDDevice*>::iterator i;
   for (i=devices_.begin(); i!=devices_.end(); i++)
   {
      char name[MM::MaxStrLength];
      (*i)->GetName(name);
      if (strcmp(name, deviceName) == 0)
      {
         (*i)->AddReference();
         return *i;
      }
   }

   // no such port found, so try to create a new one
   MDHIDDevice* pHIDDevice = new MDHIDDevice(deviceName);

   devices_.push_back(pHIDDevice);
   pHIDDevice->AddReference();
   return pHIDDevice;
}

void HIDManager::DestroyPort(MM::Device* device)
{
   vector<MDHIDDevice*>::iterator i;
   for (i=devices_.begin(); i!=devices_.end(); i++)
   {
      if (*i == device)
      {
         (*i)->RemoveReference();

         // really destroy only if there are no references pointing to the port
         if ((*i)->OKToDelete())
         {
            delete *i;
            devices_.erase(i);
         }
         return;       
      }
   }
}


MDHIDDevice::MDHIDDevice(std::string deviceName) :
   handle_(NULL),
   refCount_(0),
   busy_(false),
   open_(false),
   initialized_(false),
   portTimeoutMs_(2000.0),
   answerTimeoutMs_(20),
   overflowBufferOffset_(0),
   overflowBufferLength_(0),
   verbose_(true)
{
   deviceLister = new HIDDeviceLister();
   deviceLister->ListCachedHIDDevices(availableDevices_);

   // Debug: list the ports we found:
   vector<string>::iterator iter = availableDevices_.begin();
   ostringstream logMsg;
   logMsg << "In MDHIDDevice: HID devices found :"  << endl;
   while (iter != availableDevices_.end()) {
      logMsg << *iter << "\n";
      ++iter;
   }
   this->LogMessage(logMsg.str().c_str(), true);

   // Name of the device
   CPropertyAction* pAct = new CPropertyAction (this, &MDHIDDevice::OnDevice);
   int ret = CreateProperty( MM::g_Keyword_Port, deviceName.c_str(), MM::String, true, pAct);
   assert(ret == DEVICE_OK);
   ret = SetAllowedValues(MM::g_Keyword_Port, availableDevices_);

   deviceName_ = deviceName;

   SetErrorText(ERR_TRANSMIT_FAILED, "Failed transmitting data to the serial port");
   SetErrorText(ERR_RECEIVE_FAILED, "Failed reading data from the serial port");
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Can not change ports");
   InitializeDefaultErrorMessages();

   // configure pre-initialization properties
   // Name of the driver
   ret = CreateProperty(MM::g_Keyword_Name, "HID device driver", MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "HID device driver", MM::String, true);
   assert(ret == DEVICE_OK);

   // answer timeout                                                         
   pAct = new CPropertyAction (this, &MDHIDDevice::OnTimeout);      
   ret = CreateProperty("AnswerTimeout", "20", MM::Float, false, pAct, true);
   assert(ret == DEVICE_OK);                                                 

   // verbose debug messages
   pAct = new CPropertyAction (this, &MDHIDDevice::OnVerbose);
   string verbose = "Verbose logging";
   CreateProperty(verbose.c_str(), (verbose_?"On":"Off"), MM::String, false, pAct, true);
   AddAllowedValue(verbose.c_str(), "On");
   AddAllowedValue(verbose.c_str(), "Off");
}

MDHIDDevice::~MDHIDDevice()
{
   Shutdown();
}

int MDHIDDevice::Open(const char* /*portName*/)
{
   if (open_) {
      return ERR_PORT_ALREADY_OPEN;
   }

   bool deviceFound = false;
   int i;

   ostringstream logMsg;
   logMsg << "Opening HID device: " << deviceName_.c_str() ; 
   this->LogMessage(logMsg.str().c_str(), true);

   for (i=0; i< g_numberKnownDevices; i++) {
      if (strcmp(deviceName_.c_str(), g_knownDevices[i].name.c_str()) == 0) {
         ostringstream logMsgMsg;
         deviceFound = true;
         handle_ = hid_open(g_knownDevices[i].idVendor, 
               g_knownDevices[i].idProduct, NULL);
         if (handle_ == NULL)
         {
            logMsgMsg << "Failed to open HID device " << deviceName_;
            deviceFound = false;
         }
         else
         {
            logMsgMsg << "Opened HID device with vendor ID: " << std::hex << g_knownDevices[i].idVendor;
            logMsgMsg << ", and product ID: " << std::hex << g_knownDevices[i].idProduct;
            open_ = true;
         }
         LogMessage(logMsgMsg.str().c_str());
         
         break;
      }
   }

   if (!deviceFound)
      return ERR_INTERNAL_ERROR;

   wchar_t wstr[255];
   char str[255];
   wstr[0] = 0x0000;
   int res = hid_get_manufacturer_string(handle_, wstr, 255);
   if (res < 0) 
   {
      ostringstream logMsg2;
      logMsg2 << "hid_get_manufacturer_string error: " << res << ", " << wstr ; 
      this->LogMessage(logMsg2.str().c_str(), true);
   }
   ostringstream logMsg2;
   sprintf(str, "%ls", wstr);
   logMsg2 << "Manufacturer String: " << str ; 
   this->LogMessage(logMsg2.str().c_str(), true);

   wstr[0] = 0x0000;
   res = hid_get_product_string(handle_, wstr, 255);
   if (res < 0) 
   {
      ostringstream logMsg3;
      logMsg2 << "hid_get_product_string: " << res << ", " << wstr ; 
      this->LogMessage(logMsg3.str().c_str(), true);
   }
   ostringstream logMsg3;
   sprintf(str, "%ls", wstr);
   logMsg3 << "Product String: " << str ; 
   this->LogMessage(logMsg3.str().c_str(), true);

   wstr[0] = 0x0000;
   res = hid_get_serial_number_string(handle_, wstr, 255);
   if (res < 0) 
   {
      ostringstream logMsg4;
      logMsg4 << "hid_get_serial_number error: " << res << ", " << wstr ; 
      this->LogMessage(logMsg4.str().c_str(), true);
   }
   ostringstream logMsg4;
   sprintf(str, "%ls", wstr);
   logMsg4 << "Serial Number String: " << str ; 
   this->LogMessage(logMsg4.str().c_str(), true);

   return DEVICE_OK;

}

int MDHIDDevice::Close()
{
   int ret = DEVICE_OK;
   ostringstream logMsg;
   logMsg << "Closing HID Device " << deviceName_ ; 
   this->LogMessage(logMsg.str().c_str());
   if (open_) 
   {
      hid_close(handle_);
      open_ = false;
      return ret;
   }
   return ERR_CLOSING_DEVICE;
}

int MDHIDDevice::Initialize()
{
   // verify callbacks are supported and refuse to continue if not
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;

   // open the port:
   if (open_)
      return DEVICE_OK;

   int ret = Open(deviceName_.c_str());
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int MDHIDDevice::Shutdown()
{
   if (open_)
      Close();

   initialized_ = false;

   return DEVICE_OK;
}

void MDHIDDevice::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, deviceName_.c_str());
}


int MDHIDDevice::SetCommand(const char* command, const char* term)
{
   string sendText(command);

   if (term != 0)
      sendText += term;

   int ret = Write ( (unsigned char *) sendText.c_str(), (unsigned long) sendText.length() );
   return ret;
}

/**
*  Reads from port into buffer answer of length answerLen untill full, or the terminating
*  character term was found or a timeout (local variable maxWait) was reached
*/
int MDHIDDevice::GetAnswer(char* answer, unsigned answerLen, const char* /* term */)
{
   // Need to parse the answer for a terminating character.
   // Also, need to figure out to work with answerLen
   // Conversion between unsigned and signed char is also bad
   // Is this function useful at all in HID devices?
   int timeout = static_cast<int>(std::ceil(answerTimeoutMs_));
   int res = hid_read_timeout(handle_, (unsigned char*) answer, answerLen, timeout);
   if (res == -1)
      return ERR_RECEIVE_FAILED;

   return DEVICE_OK;
}

/**
* Writes content of buf to the HID device
*/
int MDHIDDevice::Write(const unsigned char* buf, unsigned long bufLen)
{
   unsigned char* reportBuf = new unsigned char[bufLen + 1];
   reportBuf[0] = 0x0;
   memcpy(reportBuf + 1, buf, bufLen);
   int res = hid_write(handle_, reportBuf, bufLen + 1);
   delete[] reportBuf;
   if (-1 == res)
      return ERR_WRITE_FAILED;

   // logging
   if (verbose_) 
   {
      ostringstream os;
      os << "Wrote: " << std::hex << std::setfill('0');
      for (unsigned long i=0; i < bufLen; i++) 
      {
         os << std::setw(2) << (unsigned int) buf[i] << " ";
      }
      LogMessage(os.str().c_str(), true);
   }

   return DEVICE_OK;
}

/*
* This function does the actual reading from the HID device
*/
int MDHIDDevice::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   int res = hid_read(handle_, buf, bufLen);
   if (res == -1) 
      return ERR_RECEIVE_FAILED;
   charsRead = res;

   // logging:
   if (verbose_)
   {
      ostringstream os;
      os << "Read: " << std::hex << std::setfill('0');
      for (unsigned long i=0; i < charsRead; i++) 
      {
         os << std::setw(2) << (unsigned int) buf[i] << " ";
      }
      LogMessage(os.str().c_str(), true);
   }

   return DEVICE_OK;
}

int MDHIDDevice::Purge()
{
   return DEVICE_OK;
}

int MDHIDDevice::HandleError(int errorCode)
{
   std::string ErrorMsg;
   if (errorCode == ERR_OPEN_FAILED || errorCode == ERR_SETUP_FAILED)
   {
      // figure out if this port is in the list of ports discovered on startup:
      vector<std::string>::iterator pResult = find(availableDevices_.begin(),availableDevices_.end(),deviceName_);
      if (pResult == availableDevices_.end()) {
         ErrorMsg = "This Device does not exist.  Available devices are: \n";
         for (vector<std::string>::iterator it=availableDevices_.begin();
            it != availableDevices_.end(); it++) {
               ErrorMsg += *it + "\n";
         }
         SetErrorText(ERR_PORT_DOES_NOT_EXIST, ErrorMsg.c_str());
         return (ERR_PORT_DOES_NOT_EXIST);
         // TODO: port was not in initial list, set error accordingly
      } else {
         // Re-discover which ports are currently available
         deviceLister->ListHIDDevices(availableDevices_);
         pResult = find(availableDevices_.begin(),availableDevices_.end(),deviceName_);
         if (pResult == availableDevices_.end()) {
            ErrorMsg = "This Devices was disconnected.  Currently available Devices are: \n";
            for (vector<std::string>::iterator it=availableDevices_.begin();
               it != availableDevices_.end(); it++) {
                  ErrorMsg += *it + "\n";
            }
            SetErrorText(ERR_PORT_DISAPPEARED, ErrorMsg.c_str());
            // not in list anymore, report it disappeared:
            return (ERR_PORT_DISAPPEARED);
         } else {
            // in list, but does not open
            return (errorCode);
         }
      }
   }
   return errorCode;
}


int MDHIDDevice::WriteByte(const unsigned char /*dataByte*/)
{
   return DEVICE_OK;
}

std::string MDHIDDevice::ReadLine(const unsigned int /*msTimeOut*/, const char* /*lineTerminator*/) throw (NotOpen, ReadTimeout, std::runtime_error)
{
   return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////////
// Action interface
//
int MDHIDDevice::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(deviceName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_) 
      {
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      else
      {
         pProp->Get(deviceName_);
         Open(deviceName_.c_str() );
      }
   }

   return DEVICE_OK;
}

int MDHIDDevice::OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(answerTimeoutMs_);
   }
   else if (eAct == MM::AfterSet)
   {  
      pProp->Get(answerTimeoutMs_);
   }     

   return DEVICE_OK;
} 

int MDHIDDevice::OnVerbose(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(verbose_?"On":"Off");
   }
   else if (eAct == MM::AfterSet)
   {
      string value;
      pProp->Get(value);
      verbose_ = (value == "On");
   }                                                          
                                                              
   return DEVICE_OK;                                          
                                                              
}

/*
* Class whose sole function is to list HID devices available on the user's system
* Methods are provided to give a fresh or a cached list
*/
HIDDeviceLister::HIDDeviceLister()
{
   bool stale = GetCurrentMMTime() - g_deviceListLastUpdated > MM::MMTime(5,0) ? true : false;

   if ((int) g_deviceList.size() == 0 || stale) {
      g_deviceList.clear();
      ListHIDDevices(g_deviceList);
      g_deviceListLastUpdated = GetCurrentMMTime();
   } else {
      storedAvailableHIDDevices_ = g_deviceList;
   }
}

HIDDeviceLister::~HIDDeviceLister()
{
}

MM::MMTime HIDDeviceLister::GetCurrentMMTime() 
{
#ifdef WIN32
   time_t seconds;
   seconds = time (NULL);
   return MM::MMTime((long)seconds, 0);
#else
   struct timeval t;
   gettimeofday(&t,NULL);
   return MM::MMTime(t.tv_sec, t.tv_usec);
#endif
}

void HIDDeviceLister::ListCachedHIDDevices(std::vector<std::string> &availableDevices)
{
   availableDevices =  storedAvailableHIDDevices_;
}

void HIDDeviceLister::ListHIDDevices(std::vector<std::string> &availableDevices)
{
   FindHIDDevices(storedAvailableHIDDevices_);
   availableDevices =  storedAvailableHIDDevices_;
}

// lists all HID Devices on this system that we know about
void HIDDeviceLister::FindHIDDevices(std::vector<std::string> &availableDevices)
{
   printf("Discovering HID Devices......\n");

   struct hid_device_info *devs, *curDev;

   devs = hid_enumerate(0x0, 0x0);

   curDev = devs;

   while (curDev)
   {
      printf ("HID Device pid: %04hx vid: %04hx\n", curDev->product_id, curDev->product_id);
      for (int i=0; i<g_numberKnownDevices; i++) 
      {
         if ( (curDev->vendor_id == g_knownDevices[i].idVendor) &&
            (curDev->product_id == g_knownDevices[i].idProduct) ) 
         {
            availableDevices.push_back(g_knownDevices[i].name);
            printf ("HID Device found: %s\n", g_knownDevices[i].name.c_str());
         }
      }
      curDev = curDev->next;
   }

   hid_free_enumeration(devs);

}
