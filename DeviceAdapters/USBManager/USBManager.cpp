///////////////////////////////////////////////////////////////////////////////
// FILE:          USBManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   General interface to USB devices
//
// COPYRIGHT:     University of California, San Francisco, 2007
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
// NOTE:          Uses libusb 0.1.12
//                http://libusb.sourceforge.net/ 
//                
// AUTHOR:        Nico Stuurman, Dec. 2007
//
//

#ifndef _std_iostream_INCLUDED_
#include <iostream>
#define _std_iostream_INCLUDED_
#endif

#include <usb.h>


#ifdef __GNUC__
void __attribute__((constructor)) my_init(void)
{
   usb_init();
}
#elif defined(WIN32)
BOOL APIENTRY DllMain(HANDLE /*hModule*/,
                      DWORD  ul_reason_for_call,
                      LPVOID /*lpReserved*/)
{
   switch (ul_reason_for_call)
   {
      case DLL_PROCESS_ATTACH:
         usb_init();
         break;
   }
   return TRUE;
}
#else
#error A call to usb_init() must be implemented.
#endif


#ifdef WIN32    
#include <time.h>
#pragma warning(disable : 4290)
#else
#include <sys/time.h>
#endif

#ifndef _USB_h_
#include "USBManager.h"
#endif


#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include <cstdio>
#include <sstream>
#include <algorithm>

#define USB_TIMEOUT 20

using namespace std;

USBManager g_usbManager;
std::vector<std::string> g_deviceList;
MM::MMTime g_deviceListLastUpdated = MM::MMTime(0);

/*
 * Struct containing device name, vendor ID, device ID, ports for sending and receiving and data length
 */
USBDeviceInfo g_knownDevices[] = {
   {"Velleman K8055-0", 0x10cf, 0x5500, 0x01, 0x81, 8, false},
   {"Velleman K8055-1", 0x10cf, 0x5501, 0x01, 0x81, 8, false},
   {"Velleman K8055-2", 0x10cf, 0x5502, 0x01, 0x81, 8, false},
   {"Velleman K8055-3", 0x10cf, 0x5503, 0x01, 0x81, 8, false},
   {"Velleman K8061-0", 0x10cf, 0x8061, 0x01, 0x81, 64, false},
   {"Velleman K8061-1", 0x10cf, 0x8062, 0x01, 0x81, 64, false},
   {"Ludl Mac 5000", 0x6969, 0x1235, 0x02, 0x82, 64, false},
   {"ASI MS-2000", 0x0b54, 0x2000, 0x02, 0x82, 64, false},
   {"Spectral LMM5", 0x1bdb, 0x0300, 0x02, 0x81, 64, false},
   {"Nikon AZ100m", 0x04b0, 0x7804, 0x05, 0x84, 64, false},
   {"Nikon ECLIPSE 90i", 0x04b0, 0x7301, 0x01, 0x81, 64, false},
   {"Zeiss AxioObserver Z1", 0x0758, 0x1004, 0x02, 0x81, 64, true}
};
int g_numberKnownDevices = 12;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   std::string deviceName;
   // Get the ports available on this system from our USBDeviceLister class
   USBDeviceLister* deviceLister = new USBDeviceLister();
   std::vector<std::string> availableDevices;
   deviceLister->ListCachedUSBDevices(availableDevices);

   // Now make them known to the core
   vector<string>::iterator iter = availableDevices.begin();                  
   while (iter != availableDevices.end()) {                                   
      deviceName = *iter;
      RegisterDevice(deviceName.c_str(), MM::SerialDevice, "USB device");
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
// USBManager
///////////////////////////////////////////////////////////////////////////////

USBManager::USBManager()
{
}

USBManager::~USBManager()
{
   vector<MDUSBDevice*>::iterator i;
   for (i=devices_.begin(); i!=devices_.end(); i++)
      delete *i;
}

MM::Device* USBManager::CreatePort(const char* deviceName)
{
   // check if the port already exists
   vector<MDUSBDevice*>::iterator i;
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
   MDUSBDevice* pUSBDevice = new MDUSBDevice(deviceName);

   devices_.push_back(pUSBDevice);
   pUSBDevice->AddReference();
   return pUSBDevice;
}

void USBManager::DestroyPort(MM::Device* device)
{
   vector<MDUSBDevice*>::iterator i;
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


MDUSBDevice::MDUSBDevice(std::string deviceName) :
refCount_(0),
busy_(false),
open_(false),
initialized_(false),
deviceHandle_(0),
answerTimeoutMs_(20),
overflowBufferOffset_(0),
overflowBufferLength_(0)
{
   deviceLister = new USBDeviceLister();
   deviceLister->ListCachedUSBDevices(availableDevices_);

   // Debug: list the ports we found:
   vector<string>::iterator iter = availableDevices_.begin();
   ostringstream logMsg;
   logMsg << "In MDUSBDevice: USB devices found :"  << endl;
   while (iter != availableDevices_.end()) {
      logMsg << *iter << "\n";
      ++iter;
   }
   this->LogMessage(logMsg.str().c_str(), true);

   // Name of the device
   CPropertyAction* pAct = new CPropertyAction (this, &MDUSBDevice::OnDevice);
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
   ret = CreateProperty(MM::g_Keyword_Name, "USB device driver", MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "USB device driver", MM::String, true);
   assert(ret == DEVICE_OK);

   // answer timeout                                                         
   pAct = new CPropertyAction (this, &MDUSBDevice::OnTimeout);      
   ret = CreateProperty("AnswerTimeout", "20", MM::Float, false, pAct, true);
   assert(ret == DEVICE_OK);                                                 

   ret = UpdateStatus();
   assert(ret == DEVICE_OK);
}

MDUSBDevice::~MDUSBDevice()
{
   Shutdown();
}

int MDUSBDevice::Open(const char* /*portName*/)
{
   if (open_) {
      return ERR_PORT_ALREADY_OPEN;
   }

   ostringstream logMsg;
   bool deviceFound = false;
   int i;
   static struct usb_bus *bus, *busses;
   static struct usb_device *dev;

   for (i=0; i< g_numberKnownDevices; i++) {
      if (strcmp(deviceName_.c_str(), g_knownDevices[i].name.c_str()) == 0) {
         deviceFound = true;
         break;
      }
   }

   if (!deviceFound)
      return ERR_INTERNAL_ERROR;

   usb_find_busses();
   usb_find_devices();
   busses = usb_get_busses();

   for (bus=busses; bus; bus=bus->next) {
      for (dev=bus->devices; dev; dev=dev->next) {
         if ( (dev->descriptor.idVendor == g_knownDevices[i].idVendor) &&
            (dev->descriptor.idProduct == g_knownDevices[i].idProduct) ) {
               deviceHandle_ = usb_open(dev);
               if (deviceHandle_ <= (void*) 0)
                  printf ("Received bad deviceHandle\n");
               if (TakeOverDevice(0) != DEVICE_OK) {
                  printf ("Failed taking over device \n");
                  logMsg << "Can not take over device " << deviceName_ << " from the OS driver";
                  this->LogMessage(logMsg.str().c_str());
                  usb_close(deviceHandle_);
                  return ERR_OPEN_FAILED;
               }
               deviceInputEndPoint_ = g_knownDevices[i].inputEndPoint;
               deviceOutputEndPoint_ = g_knownDevices[i].outputEndPoint;
               deviceUsesBulkEndPoint_ = g_knownDevices[i].bulkEndPoint;
               maxPacketSize_ = g_knownDevices[i].maxPacketSize;
               ostringstream logMsg;
               logMsg << "USB Device " << deviceName_ << " opened."; 
               this->LogMessage(logMsg.str().c_str(), true);
               open_ = true;
               return DEVICE_OK;
         }
      }
   }

   logMsg << "USB Device " << deviceName_ << " disappeared."; 
   this->LogMessage(logMsg.str().c_str());
   return ERR_PORT_DISAPPEARED;
}

int MDUSBDevice::Close()
{
   ostringstream logMsg;
   logMsg << "Closing USB Device " << deviceName_ ; 
   this->LogMessage(logMsg.str().c_str());
   if (open_) {
      int ret = usb_close(deviceHandle_);
      open_ = false;
      return ret;
   }
   return ERR_CLOSING_DEVICE;
}

int MDUSBDevice::Initialize()
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

int MDUSBDevice::Shutdown()
{
   if (open_)
      Close();
   initialized_ = false;

   return DEVICE_OK;
}

void MDUSBDevice::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, deviceName_.c_str());
}


int MDUSBDevice::SetCommand(const char* command, const char* term)
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
int MDUSBDevice::GetAnswer(char* answer, unsigned answerLen, const char* term)
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
* Writes content of buf to the USB device
* Splits up into chunks with a maxlength of maxPacketSize_
* Last packet is not padded
*/
int MDUSBDevice::Write(const unsigned char* buf, unsigned long bufLen)
{
   int packet, nrPackets, packetLength, status;
   unsigned long i;
   bool success = false;
   ostringstream logMsg;

   logMsg << "USB Out: ";
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

         if (deviceUsesBulkEndPoint_)
         {
             status = usb_bulk_write(deviceHandle_, deviceOutputEndPoint_, (char *) buf + (packet * maxPacketSize_), packetLength, (int)answerTimeoutMs_);
         } else
         {
            status = usb_interrupt_write(deviceHandle_, deviceOutputEndPoint_, (char *) buf + (packet * maxPacketSize_), packetLength, (int)answerTimeoutMs_);
         }
         // printf ("status: %d, bufLen: %lu, packetLength: %d, OutputEndpoint: %d, AnswerTimeout: %f\n", status, bufLen, packetLength, deviceOutputEndPoint_, answerTimeoutMs_);
         if (status != (int) bufLen)
         {
            logMsg.clear();
            logMsg << "USB write failed...";
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
   }

   return DEVICE_OK;
}

/*
* This functions does the actual reading from the USB device
*/
int MDUSBDevice::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   ostringstream logMsg;
   //usb_interrupt_read really does send us negative numbers
   signed int charsReceived = 0;
   int nrPackets, packet;
   bool statusContinue = true;
   char* internalBuf;

   charsRead = 0;
   memset(buf, 0, bufLen);

   logMsg << "USB read ";

   // keep an internal buffer in case we read too many chars from USB
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

   // buffer internally to be sure that there will be no buffer overruns
   internalBuf = (char*) malloc (maxPacketSize_);

   for (packet = 0; (packet < nrPackets) && statusContinue; packet++) 
   {
      memset(internalBuf, 0, maxPacketSize_);
      if (deviceUsesBulkEndPoint_)
      {
         charsReceived = usb_bulk_read(deviceHandle_, deviceInputEndPoint_, (char *) internalBuf, maxPacketSize_, (int)answerTimeoutMs_);
      } else 
      {
         charsReceived = usb_interrupt_read(deviceHandle_, deviceInputEndPoint_, (char *) internalBuf, maxPacketSize_, (int)answerTimeoutMs_);
      }
      if (charsReceived == 0)
         statusContinue = false;
      else if (charsReceived > 0)
      {
         if (charsReceived > (int)(bufLen - charsRead)) {
            memcpy((unsigned char*) buf + charsRead, internalBuf, bufLen - charsRead);
            overflowBufferLength_ = charsReceived - bufLen + charsRead;
            // we have extra chars, save in persistent buffer
            overflowBuffer_ = (char*) malloc(overflowBufferLength_);
            overflowBufferOffset_ = 0;
            memcpy ((char*) overflowBuffer_, internalBuf + bufLen - charsRead, overflowBufferLength_);
            charsRead += bufLen;
            statusContinue = false;
         } else {
            memcpy((unsigned char*) buf + (packet * maxPacketSize_), internalBuf, charsReceived);
            charsRead += charsReceived;
         }
      }
      else if (charsReceived < 0)
         statusContinue = false;
      if (charsRead >= bufLen)
         statusContinue = false;
   }
   for (unsigned long j=0; j<charsRead; j++)
      logMsg << hex << (unsigned int) *(buf + j) << " ";
   this->LogMessage(logMsg.str().c_str(), true);

   free (internalBuf);

   if (charsReceived < 0 && charsReceived != -110)
   {
      return DEVICE_ERR;
   }
   else
   {
      return DEVICE_OK;
   }
}

int MDUSBDevice::Purge()
{
   return DEVICE_OK;
}

int MDUSBDevice::HandleError(int errorCode)
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
         deviceLister->ListUSBDevices(availableDevices_);
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

int MDUSBDevice::TakeOverDevice(int iface)
{
   ostringstream logMsg;
   char driver_name[256];
   memset(driver_name, 0, 256);

   assert(deviceHandle_ != NULL);
#ifdef LIBUSB_HAS_GET_DRIVER_NP
   int ret = usb_get_driver_np(deviceHandle_, iface, driver_name, sizeof(driver_name));
   if (ret == 0)
   {
      logMsg << "USB Drive name : " << driver_name;
      this->LogMessage(logMsg.str().c_str(), true);
      if (0 > usb_detach_kernel_driver_np(deviceHandle_, iface)) {
         logMsg.clear();
         logMsg << "Disconnect OS driver: " << usb_strerror();
         this->LogMessage(logMsg.str().c_str(), true);
      }
      else {
         logMsg.clear();
         logMsg << "Disconnected OS driver: " << usb_strerror();
         this->LogMessage(logMsg.str().c_str(), true);
      }
   }
   else  {
      logMsg << "get driver name: " << usb_strerror();
      this->LogMessage(logMsg.str().c_str(), true);
   }
#endif
   /* claim interface */
   usb_set_configuration(deviceHandle_, 1);
   if (usb_claim_interface(deviceHandle_, iface) < 0)
   {
      // printf("USB: Claim interface error\n");
      logMsg << "Claim interface error: " << usb_strerror();
      this->LogMessage(logMsg.str().c_str(), true);
      return ERR_CLAIM_INTERFACE;
   }
   else
   {
      int status = usb_set_altinterface(deviceHandle_, iface);
      if (status < 0)
      {
         // printf("USB: Set alt interface error: %d\n", status);
         logMsg << "Set alternate interface error: " << usb_strerror();
         this->LogMessage(logMsg.str().c_str(), true);
         return ERR_CLAIM_INTERFACE;
      }
   }

   logMsg << "Found interface " << iface << "\nTook over the device";
   this->LogMessage(logMsg.str().c_str(), false);

   return DEVICE_OK;
}


int MDUSBDevice::WriteByte(const unsigned char /*dataByte*/)
{
   return DEVICE_OK;
}

std::string MDUSBDevice::ReadLine(const unsigned int /*msTimeOut*/, const char* /*lineTerminator*/) throw (NotOpen, ReadTimeout, std::runtime_error)
{
   return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////////
// Action interface
//
int MDUSBDevice::OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int MDUSBDevice::OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
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
* Class whose sole function is to list USB devices available on the user's system
* Methods are provided to give a fresh or a cached list
*/
USBDeviceLister::USBDeviceLister()
{
   bool stale = GetCurrentMMTime() - g_deviceListLastUpdated > MM::MMTime(5,0) ? true : false;

   if ((int) g_deviceList.size() == 0 || stale) {
      g_deviceList.clear();
      ListUSBDevices(g_deviceList);
      g_deviceListLastUpdated = GetCurrentMMTime();
   } else {
      storedAvailableUSBDevices_ = g_deviceList;
   }
}

USBDeviceLister::~USBDeviceLister()
{
}

MM::MMTime USBDeviceLister::GetCurrentMMTime() 
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

void USBDeviceLister::ListCachedUSBDevices(std::vector<std::string> &availableDevices)
{
   availableDevices =  storedAvailableUSBDevices_;
}

void USBDeviceLister::ListUSBDevices(std::vector<std::string> &availableDevices)
{
   FindUSBDevices(storedAvailableUSBDevices_);
   availableDevices =  storedAvailableUSBDevices_;
}

// lists all USB Devices on this system that we know about
void USBDeviceLister::FindUSBDevices(std::vector<std::string> &availableDevices)
{
   printf("Discovering USB Devices......\n");
   static struct usb_bus *bus, *busses;
   static struct usb_device *dev;
   //static usb_dev_handle *device_handle = '\0';

   //usb_init();
   usb_find_busses();
   usb_find_devices();
   busses = usb_get_busses();

   // loop through the busses found and remember the ones that match our list
   for (bus = busses; bus; bus = bus->next) {
      for (dev = bus->devices; dev; dev = dev->next) {
         for (int i=0; i<g_numberKnownDevices; i++) {
            if ( (dev->descriptor.idVendor == g_knownDevices[i].idVendor) &&
               (dev->descriptor.idProduct == g_knownDevices[i].idProduct) ) {
                  availableDevices.push_back(g_knownDevices[i].name);
                  printf ("USB Device found: %s\n", g_knownDevices[i].name.c_str());
                  break;
            }
         }
      }
   }
}
