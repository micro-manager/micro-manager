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

#include "/usr/local/src/hidapi-0.7.0/hidapi/hidapi.h"

#ifndef _HID_h_
#include "HIDManager.h"
#endif


#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include <cstdio>
#include <sstream>
#include <algorithm>

#define HID_TIMEOUT 20

using namespace std;

HIDManager g_usbManager;
std::vector<std::string> g_deviceList;
MM::MMTime g_deviceListLastUpdated = MM::MMTime(0);

/*
 * Struct containing device name, vendor ID, device ID, ports for sending and receiving and data length
 */
HIDDeviceInfo g_knownDevices[] = {
   {"Velleman K8055-0", 0x10cf, 0x5500, 0x01},
   {"Velleman K8055-1", 0x10cf, 0x5501, 0x01},
};
int g_numberKnownDevices = 2;

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
      AddAvailableDeviceName(deviceName.c_str(),"HID device");
      ++iter;                                                                
   }
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   return g_usbManager.CreatePort(deviceName);
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   g_usbManager.DestroyPort(pDevice);
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
refCount_(0),
busy_(false),
open_(false),
initialized_(false),
portTimeoutMs_(2000.0),
answerTimeoutMs_(20),
overflowBufferOffset_(0),
overflowBufferLength_(0)
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

   ret = UpdateStatus();
   assert(ret == DEVICE_OK);
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

   ostringstream logMsg;
   bool deviceFound = false;
   int i;

   for (i=0; i< g_numberKnownDevices; i++) {
      if (strcmp(deviceName_.c_str(), g_knownDevices[i].name.c_str()) == 0) {
         deviceFound = true;
         break;
      }
   }

   if (!deviceFound)
      return ERR_INTERNAL_ERROR;

   return DEVICE_OK;

}

int MDHIDDevice::Close()
{
   int ret = DEVICE_OK;
   ostringstream logMsg;
   logMsg << "Closing HID Device " << deviceName_ ; 
   this->LogMessage(logMsg.str().c_str());
   if (open_) {
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
int MDHIDDevice::GetAnswer(char* answer, unsigned answerLen, const char* term)
{
   unsigned long charsRead;
   ostringstream logMsg;
   char* termpos = 0;
   char* internalBuf;
   bool termFound = false;

   if (answerLen < 1)
      return ERR_BUFFER_OVERRUN;

   internalBuf = (char*) malloc(answerLen);
   memset(internalBuf, 0, answerLen);
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime timeOut = MM::MMTime(answerTimeoutMs_ * 1000.0);

   while (!termFound && ((GetCurrentMMTime() - startTime) < timeOut)) {
      int ret = Read((unsigned char *) internalBuf, answerLen, charsRead);

      if (ret != DEVICE_OK) {
         free(internalBuf);
         return ret;
      }
      termpos = strstr(internalBuf, term);
      if (termpos != 0)
         termFound = true;
   }

   if (!termFound)
      return ERR_RECEIVE_FAILED;

   *termpos = '\0';

   strcpy(answer, internalBuf);

   free (internalBuf);

   return DEVICE_OK;
}

/*
* Writes content of buf to the HID device
* Splits up into chunks with a maxlength of maxPacketSize_
* Last packet is not padded
*/
int MDHIDDevice::Write(const unsigned char* buf, unsigned long bufLen)
{
   int packet, nrPackets, packetLength, status;
   unsigned long i;
   bool success = false;
   ostringstream logMsg;

   logMsg << "HID Out: ";
   for (i=0; i<bufLen; i++)
      logMsg << hex << (unsigned int) *(buf + i) << " ";
   LogMessage(logMsg.str().c_str(), true);
   // check if the message is larger than the package size and split up if needed
   if ( (bufLen % maxPacketSize_) == 0)
      nrPackets = bufLen/maxPacketSize_;
   else
      nrPackets = bufLen/maxPacketSize_ + 1;

   for (packet = 0; packet < nrPackets; packet++) 
   {
      // don't know why, but we'll try up to 3 times:
      for (i = 0;  (i < 3) && !success; i++)
      {
         if (packet < nrPackets -1)
            packetLength = maxPacketSize_;
         else
         {
            packetLength = bufLen % maxPacketSize_;
            if (packetLength == 0)
               packetLength = maxPacketSize_;
         }

         }
         // write stuff here
      
         if (status != (int) bufLen)
         {
            logMsg.clear();
            logMsg << "HID write failed...";
            LogMessage(logMsg.str().c_str());
            if (i == 2)
            {
               return ERR_WRITE_FAILED;
            }
         }
         else 
         {
            success = true;
         }
      }

   return DEVICE_OK;
}

/*
* This functions does the actual reading from the HID device
*/
int MDHIDDevice::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   ostringstream logMsg;
   //usb_interrupt_read really does send us negative numbers
   signed int charsReceived = 0;
   int nrPackets, packet;
   bool statusContinue = true;
   char* internalBuf;

   charsRead = 0;
   memset(buf, 0, bufLen);

   logMsg << "HID read ";

   // keep an internal buffer in case we read too many chars from HID
   if (overflowBufferLength_ > 0) {
      unsigned int size = overflowBufferLength_ - overflowBufferOffset_;
      if (size >= bufLen) {
         memcpy(buf, overflowBuffer_ + overflowBufferOffset_, bufLen);
         charsRead = bufLen;
      } else {
         memcpy(buf, overflowBuffer_ + overflowBufferOffset_, size);
         charsRead = size;
      }
      overflowBufferOffset_ += charsRead;
      if (overflowBufferOffset_ >= overflowBufferLength_) {
         overflowBufferOffset_ = 0;
         overflowBufferLength_ = 0;
         free (overflowBuffer_);
      }
      if (charsRead >= bufLen) {
         for (unsigned long j=0; j<bufLen; j++)
            logMsg << hex << (unsigned int) *(buf + j) << " ";
         this->LogMessage(logMsg.str().c_str(), true);
         return DEVICE_OK;
      }
   }

   // we will try to read up to bufLen characters, even if bufLen is larger than maxPacketSize
   int neededChars = bufLen - charsRead;
   if ( (neededChars % maxPacketSize_) == 0)
      nrPackets = neededChars/maxPacketSize_;
   else
      nrPackets = neededChars/maxPacketSize_ + 1;

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

   // loop through the busses found and remember the ones that match our list
/*   for (bus = busses; bus; bus = bus->next) {
      for (dev = bus->devices; dev; dev = dev->next) {
         for (int i=0; i<g_numberKnownDevices; i++) {
            if ( (dev->descriptor.idVendor == g_knownDevices[i].idVendor) &&
               (dev->descriptor.idProduct == g_knownDevices[i].idProduct) ) {
                  availableDevices.push_back(g_knownDevices[i].name);
                  printf ("HID Device found: %s\n", g_knownDevices[i].name.c_str());
                  break;
            }
         }
      }
   }
      */
}
