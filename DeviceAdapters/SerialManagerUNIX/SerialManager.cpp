///////////////////////////////////////////////////////////////////////////////
// FILE:          SerialManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial port device adapter - UNIX version
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// NOTE:          UNIX implementation uses libserial
//                http://libserial.sourceforge.net/mediawiki/index.php/Main_Pagel 
//                
// AUTHOR:        Nenad Amodaj, 10/21/2005, Nico Stuurman, Dec. 2005
//
// CVS:           $Id$
//

#ifndef _std_iostream_INCLUDED_
#include <iostream>
#define _std_iostream_INCLUDED_
#endif

#ifndef _SerialPort_h_
#include "SerialPort.h"
#endif

#ifdef __APPLE__    
#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/IOKitLib.h>
#include <IOKit/serial/IOSerialKeys.h>
#if defined(MAC_OS_X_VERSION_10_3) && (MAC_OS_X_VERSION_MIN_REQUIRED >= MAC_OS_X_VERSION_10_3)
   #include <IOKit/serial/ioss.h>
#endif
#include <IOKit/IOBSD.h>                                                     
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "SerialManager.h"
#include <sstream>

using namespace std;

SerialManager g_serialManager;

const char* g_StopBits_1 = "1";
const char* g_StopBits_1_5 = "1.5";
const char* g_StopBits_2 = "2";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   std::string portName;
   // Get the ports available on this system from our SerialPortLister class
   SerialPortLister* portLister = new SerialPortLister();
   std::vector<std::string> availablePorts;
   portLister->ListPorts(availablePorts);
   // Now make them known to the core and output them to the log as well
   vector<string>::iterator iter = availablePorts.begin();                  
   ostringstream logMsg;
   logMsg << "Serial ports found :"  << endl;
   while (iter != availablePorts.end()) {                                   
      portName = *iter;
      logMsg << *iter << endl;
      AddAvailableDeviceName(portName.c_str(),"Serial communication port");
      ++iter;                                                                
   }
   // Todo: output to log message
   //this->LogMessage(logMsg.str().c_str(), true);
   //cout << logMsg.str();
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   return g_serialManager.CreatePort(deviceName);
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   g_serialManager.DestroyPort(pDevice);
}

///////////////////////////////////////////////////////////////////////////////
// SerialManager
///////////////////////////////////////////////////////////////////////////////

SerialManager::SerialManager()
{
}

SerialManager::~SerialManager()
{
   vector<MDSerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
      delete *i;
}

MM::Device* SerialManager::CreatePort(const char* portName)
{
   // check if the port already exists
   vector<MDSerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
   {
      char name[MM::MaxStrLength];
      (*i)->GetName(name);
      if (strcmp(name, portName) == 0)
      {
         (*i)->AddReference();
         return *i;
      }
   }

   // no such port found, so try to create a new one
   MDSerialPort* pPort = new MDSerialPort(portName);
   // try opening, do not check whether this succeeded
   printf("Opening port %s\n",portName);
   pPort->Open(portName);
   printf("Port opened\n");
   ports_.push_back(pPort);
   printf("Added to the list\n");
   pPort->AddReference();
   printf("Reference added\n");
   return pPort;
      /*
   if (pPort->Open(portName) == DEVICE_OK)
   {
      // open port succeeded, so add it to the list
      ports_.push_back(pPort);
      pPort->AddReference();
      return pPort;
   }

   // open port failed
   delete pPort;
   return 0;
   */
}

void SerialManager::DestroyPort(MM::Device* port)
{
   vector<MDSerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
   {
      if (*i == port)
      {
         (*i)->RemoveReference();

         // really destroy only if there are no references pointing to the port
         if ((*i)->OKToDelete())
         {
            delete *i;
            ports_.erase(i);
         }
         return;       
      }
   }
}


MDSerialPort::MDSerialPort(std::string portName) :
   refCount_(0),
   //port_(0),
   busy_(false),
   initialized_(false),
   portTimeoutMs_(2000.0),
   answerTimeoutMs_(500),
   transmitCharWaitMs_(10),
   stopBits_(1),
   dataBits_(8),
   baudRate_(9600)
{
   portLister = new SerialPortLister();
   portLister->ListPorts(availablePorts_);
   // Debug: list the ports we found:
   vector<string>::iterator iter = availablePorts_.begin();
   ostringstream logMsg;
   logMsg << "Serial ports found :"  << endl;
   while (iter != availablePorts_.end()) {
      logMsg << *iter << endl;
      ++iter;
   }
   this->LogMessage(logMsg.str().c_str(), true);
  
   // Name of the port
   CPropertyAction* pAct = new CPropertyAction (this, &MDSerialPort::OnPort);
   int ret = CreateProperty( MM::g_Keyword_Port, portName.c_str(), MM::String, true, pAct);
   assert(ret == DEVICE_OK);
   ret = SetAllowedValues(MM::g_Keyword_Port, availablePorts_);

   portName_=portName;
   port_ = new SerialPort(portName_.c_str());

   SetErrorText(ERR_TRANSMIT_FAILED, "Failed transmitting data to the serial port");
   SetErrorText(ERR_RECEIVE_FAILED, "Failed reading data from the serial port");
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Can not change ports");
   InitializeDefaultErrorMessages();

   // configure pre-initialization properties
   // Name of the driver
   ret = CreateProperty(MM::g_Keyword_Name, "Serial Port driver", MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Serial port driver (Unix)", MM::String, true);
   assert(ret == DEVICE_OK);

   // baud
   pAct = new CPropertyAction (this, &MDSerialPort::OnBaudRate);
   ret = CreateProperty(MM::g_Keyword_BaudRate, "9600" , MM::Integer, false, pAct);
   assert(DEVICE_OK == ret);
   vector<string> baudRateValues;
   baudRateValues.push_back("200");
   baudRateValues.push_back("300");
   baudRateValues.push_back("600");
   baudRateValues.push_back("1200");
   baudRateValues.push_back("1800");
   baudRateValues.push_back("2400");
   baudRateValues.push_back("4800");
   baudRateValues.push_back("9600");
   baudRateValues.push_back("19200");
   baudRateValues.push_back("38400");
   baudRateValues.push_back("57600");
   baudRateValues.push_back("115200");
   baudRateValues.push_back("230400");
   ret = SetAllowedValues(MM::g_Keyword_BaudRate,baudRateValues);

   // data bits
   pAct = new CPropertyAction (this, &MDSerialPort::OnDataBits);
   ret = CreateProperty(MM::g_Keyword_DataBits, "8", MM::String, false);
   assert(ret == DEVICE_OK);
   vector<string> dataBitValues;
   dataBitValues.push_back("8");
   dataBitValues.push_back("7");
   dataBitValues.push_back("6");
   dataBitValues.push_back("5");
   ret = SetAllowedValues(MM::g_Keyword_DataBits,dataBitValues);

   // stop bits
   pAct = new CPropertyAction (this, &MDSerialPort::OnStopBits);
   ret = CreateProperty(MM::g_Keyword_StopBits, "1", MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   vector<string> stopBitValues;
   stopBitValues.push_back(g_StopBits_1);
   stopBitValues.push_back(g_StopBits_1_5);
   stopBitValues.push_back(g_StopBits_2);
   ret = SetAllowedValues(MM::g_Keyword_StopBits,stopBitValues);

   // parity
   ret = CreateProperty(MM::g_Keyword_Parity, "None", MM::String, true);
   assert(ret == DEVICE_OK);

   // handshaking
   ret = CreateProperty(MM::g_Keyword_Handshaking, "Off", MM::String, true);
   assert(ret == DEVICE_OK);

   // answer timeout                                                         
   CPropertyAction* pActTimeout = new CPropertyAction (this, &MDSerialPort::OnTimeout);      
   ret = CreateProperty("AnswerTimeout", "500", MM::Float, false, pActTimeout);
   assert(ret == DEVICE_OK);                                                 

   ret = UpdateStatus();
   assert(ret == DEVICE_OK);
}

MDSerialPort::~MDSerialPort()
{
   Shutdown();
   delete port_;
}

int MDSerialPort::Open(const char* portName)
{
   printf("In MDSerialPort\n");
   std::string ErrorMsg; 
   assert(port_);

   // We could bail if the portName is not in the vector of availablePorts_, but try anyways
   try {
      port_->Open(baudRate_,dataBits_, port_->PARITY_NONE,stopBits_,port_->FLOW_CONTROL_DEFAULT);
   } catch ( SerialPort::OpenFailed ) {
      printf("OpenFailed\n");
		return HandleError(ERR_OPEN_FAILED);
   } catch (SerialPort::AlreadyOpen) {
      printf("Port already open\n");
      return ERR_PORT_ALREADY_OPEN;
   } 

   printf("In MDSerialPort, opened the port\n");
   ostringstream logMsg;
   logMsg << "Serial port " << portName_ << " opened." << endl; 
   this->LogMessage(logMsg.str().c_str());

   printf("In MDSerialPort, Added the log\n");

   return DEVICE_OK;
}

int MDSerialPort::Initialize()
{
   cerr << "Initializing Port" << endl;
   assert(port_);

   // verify callbacks are supported and refuse to continue if not
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;

   long sb;
   int ret;
   //int ret = GetPropertyData(MM::g_Keyword_StopBits, stopBits_.c_str(), sb);
   //assert(ret == DEVICE_OK);

   cerr << "Setting Baud Rate" << endl;
   try {
      port_->SetBaudRate(port_->BAUD_9600);
      port_->SetCharSize(port_->CHAR_SIZE_8);
      port_->SetParity(port_->PARITY_NONE);
      port_->SetNumOfStopBits(port_->STOP_BITS_1);
      //long lastError = port_->Setup(CSerial::EBaud9600, CSerial::EData8, CSerial::EParNone, (CSerial::EStopBits)sb);
   } catch ( ... ) {
		return HandleError(ERR_SETUP_FAILED);
   }

   // set-up handshaking
   try {
      port_->SetFlowControl(port_->FLOW_CONTROL_NONE);
   } catch ( ... ) {
		return ERR_HANDSHAKE_SETUP_FAILED;
   }
   

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int MDSerialPort::Shutdown()
{
   if (port_->IsOpen())
      port_->Close();
   initialized_ = false;

   ostringstream logMsg;
   logMsg << "Serial port " << portName_ << " closed." << endl; 
   this->LogMessage(logMsg.str().c_str());
   
   return DEVICE_OK;
}
  
void MDSerialPort::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, portName_.c_str());
}


int MDSerialPort::SetCommand(const char* command, const char* term)
{
   string sendText(command);

   if (term != 0)
      sendText += term;

   // send characters one by one to accomodate for slow devices

   unsigned long written = 0;
   for (unsigned i=0; i<sendText.length(); i++)
   {
      unsigned long one = 0;
      try {
         port_->WriteByte(sendText[written]);
      } catch ( ... ) {
         return ERR_TRANSMIT_FAILED;
      }
      usleep((int)transmitCharWaitMs_*1000);
      //assert (one == 1);
      written++;
   }
   assert(written == sendText.length());
   ostringstream logMsg;
   logMsg << "Serial port " << portName_ << " wrote: " << sendText;
   this->LogMessage(logMsg.str().c_str());
   return DEVICE_OK;
}

/**
 *  Reads from port into buffer answer of length bufLen untill full, or the terminating
 *  character term was found or a timeout (local variable maxWait) was reached
 */
int MDSerialPort::GetAnswer(char* answer, unsigned bufLen, const char* term)
{
   if (bufLen < 1)
      return ERR_BUFFER_OVERRUN;
   ostringstream logMsg;
   
   // fill the buffer with zeroes  (really needed??)
   //memset(answer, 0, bufLen);
   std::string result;
   try {
      result = port_->ReadLine((int)answerTimeoutMs_,*term);
   }
   catch ( SerialPort::ReadTimeout ) {
      logMsg << "Timeout on read from Serial port " << portName_ << "..."; 
      this->LogMessage(logMsg.str().c_str(), true);
   }
   logMsg << " From port: " << portName_ << "." << "Read: " << result;
   this->LogMessage(logMsg.str().c_str(), true);
   strcpy(answer,result.c_str());
   return DEVICE_OK;
}

int MDSerialPort::Write(const char* buf, unsigned long bufLen)
{
   // send characters one by one to accomodate for slow devices
   string sendText(buf);
   ostringstream logMsg;
   logMsg << "Serial TX: ";
   for (unsigned i=0; i<bufLen; i++)
   {
      //unsigned long written = 0;
      try {
         port_->WriteByte(sendText[i]);
      } catch ( ... ) {
		   return ERR_TRANSMIT_FAILED;      
      }
      usleep((int)transmitCharWaitMs_);
      logMsg << (int) *(buf + i) << " ";
   }
   
   logMsg << endl;
   LogMessage(logMsg.str().c_str(), true);

   return DEVICE_OK;
}
 
int MDSerialPort::Read(char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   // fill the buffer with zeros
   memset(buf, 0, bufLen);
   ostringstream logMsg;
   logMsg << "Serial RX: ";
   charsRead = 0;
   char readChar;
   
   do {
      readChar = 0;
      try {
         if (port_->IsDataAvailable()) {
            readChar = port_->ReadByte((int)answerTimeoutMs_);
         } else
            return DEVICE_OK;
      } catch ( ... ) {
		   return ERR_RECEIVE_FAILED;
      }
      buf[charsRead] = readChar;
      charsRead++;
   } while (charsRead < bufLen);

   for (unsigned long i=0; i<charsRead; i++)
      logMsg << (int) *(buf + i) << " ";
   logMsg << endl;
   if (charsRead > 0)
      LogMessage(logMsg.str().c_str(), true);

   return DEVICE_OK;
}

int MDSerialPort::Purge()
{
   /* TODO: purge the libserial buffer (if possible)
   long lastError = port_->Purge();
	if (lastError != ERROR_SUCCESS)
		return ERR_PURGE_FAILED;
   */
   return DEVICE_OK;
}

int MDSerialPort::HandleError(int errorCode)
{
   std::string ErrorMsg;
   if (errorCode == ERR_OPEN_FAILED || errorCode == ERR_SETUP_FAILED)
   {
      // figure out if this port is in the list of ports discovered on startup:
      vector<std::string>::iterator pResult = find(availablePorts_.begin(),availablePorts_.end(),portName_);
      if (pResult == availablePorts_.end()) {
         ErrorMsg = "This Serial Port does not exist.  Available ports are: \n";
         for (vector<std::string>::iterator it=availablePorts_.begin();
               it != availablePorts_.end(); it++) {
            ErrorMsg += *it + "\n";
         }
         SetErrorText(ERR_PORT_DOES_NOT_EXIST, ErrorMsg.c_str());
         return (ERR_PORT_DOES_NOT_EXIST);
         // TODO: port was not in initial list, set error accordingly
      } else {
         // Re-discover which ports are currently available
         portLister->ListCurrentPorts(availablePorts_);
         pResult = find(availablePorts_.begin(),availablePorts_.end(),portName_);
         if (pResult == availablePorts_.end()) {
            ErrorMsg = "This Serial Port was disconnected.  Currently Available ports are: \n";
            for (vector<std::string>::iterator it=availablePorts_.begin();
                  it != availablePorts_.end(); it++) {
               ErrorMsg += *it + "\n";
            }
            SetErrorText(ERR_PORT_DISAPPEARED, ErrorMsg.c_str());
            // not in list anymore, report it disappeared:
            return (ERR_PORT_DISAPPEARED);
         } else {
            // in list, but does not open
            return (ERR_OPEN_FAILED);
         }
      }
   }
   return errorCode;
}

//////////////////////////////////////////////////////////////////////////////
// Action interface
//
int MDSerialPort::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(portName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_) 
      {
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      else
      {
         pProp->Get(portName_);
         Open(portName_.c_str() );
      }
   }

   return DEVICE_OK;
}


int MDSerialPort::OnDataBits(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(dataBits_);
   }
   else if (eAct == MM::AfterSet)
   {
      long int oldDataBits=dataBits_;
      pProp->Get(dataBits_);
      if (initialized_)
      {
         try {
            port_->SetCharSize(dataBits_);
         } catch ( ... ) {
            pProp->Set(oldDataBits);
            return ERR_SETUP_FAILED;
         }
      }
   }

   return DEVICE_OK;
}


int MDSerialPort::OnStopBits(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stopBits_);
   }
   else if (eAct == MM::AfterSet)
   {
      long int oldStopBits=stopBits_;
      pProp->Get(stopBits_);
      if (initialized_)
      {
         try {
            port_->SetNumOfStopBits(stopBits_);
         } catch ( ... ) {
            pProp->Set(oldStopBits);
            return ERR_SETUP_FAILED;
         }
      }
   }

   return DEVICE_OK;
}


int MDSerialPort::OnBaudRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(baudRate_);
   }
   else if (eAct == MM::AfterSet)
   {
      long int oldBaudRate = baudRate_;
      pProp->Get(baudRate_);
      if (initialized_)
      {
         try {
            port_->SetBaudRate(baudRate_);
         } catch ( ... ) {
            pProp->Set(oldBaudRate);
            return ERR_SETUP_FAILED;
         }
      }
   }

   return DEVICE_OK;
}

int MDSerialPort::OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
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
 * Class whose sole function is to list serial ports available on the user's system
 * Methods are provided to give a fresh or a cached list
 */
SerialPortLister::SerialPortLister()
{
   // TODO make storedAvailablePorts_ persist between instances
   ListSerialPorts(storedAvailablePorts_);
}

SerialPortLister::~SerialPortLister()
{
}

void SerialPortLister::ListPorts(std::vector<std::string> &availablePorts)
{
   // TODO check that we actually have a list
   availablePorts =  storedAvailablePorts_;
}

void SerialPortLister::ListCurrentPorts(std::vector<std::string> &availablePorts)
{
   ListSerialPorts(storedAvailablePorts_);
   availablePorts =  storedAvailablePorts_;
}

// lists all serial ports available on this system
//std::vector<string> SerialManager::ListPorts()
void SerialPortLister::ListSerialPorts(std::vector<std::string> &availablePorts)
{
#ifdef WIN32
   // WIndows has its port names pre-defined:
   availablePorts.push_back("COM1");
   availablePorts.push_back("COM2");
   availablePorts.push_back("COM3");
   availablePorts.push_back("COM4");
   availablePorts.push_back("COM5");
   availablePorts.push_back("COM6");
   availablePorts.push_back("COM7");
   availablePorts.push_back("COM8");
#endif

//TODO: Need port discovery code for linus
   
#ifdef __APPLE__    
   // port discovery code for Darwin/Mac OS X
   // Derived from Apple's examples at: http://developer.apple.com/samplecode/SerialPortSample/SerialPortSample.html
   io_iterator_t   serialPortIterator;
   char            bsdPath[256];
   kern_return_t       kernResult;                                          
   CFMutableDictionaryRef classesToMatch;
       
   // Serial devices are instances of class IOSerialBSDClient               
   classesToMatch = IOServiceMatching(kIOSerialBSDServiceValue);
   if (classesToMatch == NULL) {
       printf("IOServiceMatching returned a NULL dictionary.\n");
   } else {
       CFDictionarySetValue(classesToMatch,                                 
                           CFSTR(kIOSerialBSDTypeKey),                     
                           CFSTR(kIOSerialBSDRS232Type));    
   }
   kernResult = IOServiceGetMatchingServices(kIOMasterPortDefault, classesToMatch, &serialPortIterator);
   if (KERN_SUCCESS != kernResult) {
      printf("IOServiceGetMatchingServices returned %d\n", kernResult);
   }

   // Given an iterator across a set of modems, return the BSD path to the first one.
   // If no modems are found the path name is set to an empty string.
   io_object_t      modemService;
   Boolean       modemFound = false;    
      
    // Initialize the returned path    
    *bsdPath = '\0';    
    // Iterate across all modems found. 
    while (modemService = IOIteratorNext(serialPortIterator)) {
       CFTypeRef bsdPathAsCFString;      
       // Get the device's path (/dev/tty.xxxxx).
       bsdPathAsCFString = IORegistryEntryCreateCFProperty(modemService,
                                                           CFSTR(kIODialinDeviceKey),     
                                                           kCFAllocatorDefault,
                                                           0);              
      if (bsdPathAsCFString) { 
          Boolean result;                                                  
          // Convert the path from a CFString to a C (NUL-terminated) string for use     
          // with the POSIX open() call.                                      
          result = CFStringGetCString(bsdPathAsCFString,                      
                                      bsdPath,                             
                                      sizeof(bsdPath),                         
                                      kCFStringEncodingUTF8);              

          CFRelease(bsdPathAsCFString);                                    

	       // add the name to our vector<string>
          if (result) {
               availablePorts.push_back(bsdPath);
               modemFound = true;                                           
               kernResult = KERN_SUCCESS;
           }                                                                
        }
     }
 
    // Release the io_service_t now that we are done with it.            
    (void) IOObjectRelease(modemService);                                  

    return;
#endif
}


