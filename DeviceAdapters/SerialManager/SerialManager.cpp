///////////////////////////////////////////////////////////////////////////////
// FILE:          SerialManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial port device adapter
//
// COPYRIGHT:     University of California, San Francisco, 2010
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
// AUTHOR:        Nenad Amodaj - Mark I - use CSerial class
//                Karl Hoover - Mark II - use boost, also simplify handling of terminators

#include "SerialManager.h"

#include "AsioClient.h"

#include "ModuleInterface.h"
#include "DeviceUtils.h"

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/IOBSD.h>
#include <IOKit/IOKitLib.h>
#include <IOKit/serial/IOSerialKeys.h>
#endif

#ifdef __linux__
#include <dirent.h>
#endif

#include <boost/bind.hpp>
#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/thread.hpp>

#include <iostream>
#include <sstream>


SerialManager g_serialManager;

std::vector<std::string> g_BlackListedPorts;
std::vector<std::string> g_PortList;
time_t g_PortListLastUpdated = 0;

const char* g_StopBits_1 = "1";
const char* g_StopBits_1_5 = "1.5";
const char* g_StopBits_2 = "2";

const char* g_Baud_110 = "110";
const char* g_Baud_300 = "300";
const char* g_Baud_600 = "600";
const char* g_Baud_1200 = "1200";
const char* g_Baud_2400 = "2400";
const char* g_Baud_4800 = "4800";
const char* g_Baud_9600 = "9600";
const char* g_Baud_14400 = "14400";
const char* g_Baud_19200 = "19200";
const char* g_Baud_38400 = "38400";
const char* g_Baud_57600 = "57600";
const char* g_Baud_115200 = "115200";
const char* g_Baud_128000 = "128000";
const char* g_Baud_230400 = "230400";
const char* g_Baud_460800 = "460800";
const char* g_Baud_500000 = "500000";
const char* g_Baud_576000 = "576000";
const char* g_Baud_921600 = "921600";

const char* g_Handshaking_Off = "Off";
const char* g_Handshaking_Hardware = "Hardware";
const char* g_Handshaking_Software = "Software";

const char* g_Parity_None = "None";
const char* g_Parity_Odd = "Odd";
const char* g_Parity_Even = "Even";
const char* g_Parity_Mark = "Mark";
const char* g_Parity_Space = "Space";


/*
 * Tests whether given serial port can be used by opening it
 */
bool SerialPortLister::portAccessible(const char* portName)
{
   try
   {
      boost::asio::io_service service;
      boost::asio::serial_port sp(service, portName);

      if (sp.is_open()) {
         sp.close();
         return true;
      }
   }
   catch( std::exception& )
   {
      
      return false;
   }

   return false;                                                             
}


#ifdef WIN32
const int MaxBuf = 100000;
typedef struct 
	{
		char buffer[MaxBuf];
} B100000;
#endif




void SerialPortLister::ListPorts(std::vector<std::string> &availablePorts)
{
#ifdef WIN32

   availablePorts.clear();
   std::auto_ptr<B100000> allDeviceNames (new B100000());

   // on Windows the serial ports are devices that begin with "COM"
   int ret = QueryDosDevice( 0, allDeviceNames->buffer, MaxBuf);
   if( 0!= ret)
   {
      for( int ii = 0; ii < ret; ++ii)
      {
         if ( 0 == allDeviceNames->buffer[ii])
            allDeviceNames->buffer[ii] = ' ';
      }
      std::string all(allDeviceNames->buffer, ret);
      std::vector<std::string> tokens;
      CDeviceUtils::Tokenize(all, tokens, " ");
      for( std::vector<std::string>::iterator jj = tokens.begin(); jj != tokens.end(); ++jj)
      {
         if( 0 == jj->substr(0,3).compare("COM"))
            availablePorts.push_back(*jj);
      }
      
   }
#endif // WIN32

#ifdef linux 
   // Look for /dev files with correct signature 
   DIR* pdir = opendir("/dev");
   struct dirent *pent;
   if (pdir) {
      while (pent = readdir(pdir)) {
         if ( (strstr(pent->d_name, "ttyS") != 0) || 
               (strstr(pent->d_name, "ttyUSB") != 0)  || 
               (strstr(pent->d_name, "ttyACM") != 0))  
         {
            std::string p = ("/dev/");
            p.append(pent->d_name);
            if (portAccessible(p.c_str()))
               availablePorts.push_back(p.c_str());
         }
      }
   }
#endif // linux 

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
      std::cerr << "IOServiceMatching returned a NULL dictionary.\n";
   } else {
       CFDictionarySetValue(classesToMatch,                         
                           CFSTR(kIOSerialBSDTypeKey),        
                           CFSTR(kIOSerialBSDAllTypes));   
   }
   kernResult = IOServiceGetMatchingServices(kIOMasterPortDefault, classesToMatch, &serialPortIterator);
   if (KERN_SUCCESS != kernResult) {
      std::cerr << "IOServiceGetMatchingServices returned " << kernResult << "\n";
   }
                                                                        
   // Given an iterator across a set of modems, return the BSD path to the first one.
   // If no modems are found the path name is set to an empty string.            
   io_object_t      modemService;
   
   // Initialize the returned path                                              
   *bsdPath = '\0';           
   // Iterate across all modems found.                                          
   while ( (modemService = IOIteratorNext(serialPortIterator)) ) {
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
         result = CFStringGetCString( (const __CFString*) bsdPathAsCFString, 
                                         bsdPath,   
                                         sizeof(bsdPath), 
                                         kCFStringEncodingUTF8); 

         CFRelease(bsdPathAsCFString);

         // add the name to our vector<string> only when this is not a dialup port
         std::string rresult (bsdPath);
         std::string::size_type loc = rresult.find("DialupNetwork", 0);
         if (result && (loc == std::string::npos)) {
             bool blackListed = false;
             std::vector<std::string>::iterator it = g_BlackListedPorts.begin();
             while (it < g_BlackListedPorts.end()) {
                if( bsdPath == (*it))
                   blackListed = true;
            }
            if (portAccessible(bsdPath) && ! blackListed)  {
                 availablePorts.push_back(bsdPath);
            }                 
            kernResult = KERN_SUCCESS;
         }
      }
   } 

    // Release the io_service_t now that we are done with it.
    (void) IOObjectRelease(modemService);

#endif // __APPLE
}



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
/*
 * Determines list of available ports and feeds this back to the Core
 * Caches this list in g_PortList and only queries hardware when this cache is absent or stale
 */
MODULE_API void InitializeModuleData()
{  
   // Determine whether portList is fresh enough (i.e. younger than 15 seconds):
   time_t seconds = time(NULL);
   time_t timeout = 15;
   bool stale = seconds - g_PortListLastUpdated > timeout ? true : false;

   if (g_PortList.size() == 0 || stale)
   {
      SerialPortLister::ListPorts(g_PortList); 
      g_PortListLastUpdated = time(NULL);
   }

   std::vector<std::string>::iterator it = g_PortList.begin();
   while (it < g_PortList.end()) {
      RegisterDevice((*it).c_str(), MM::SerialDevice, "Serial communication port");
      it++;
   }

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

SerialManager::~SerialManager()
{
   std::vector<SerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
      delete *i;
}

MM::Device* SerialManager::CreatePort(const char* portName)
{
   // check if the port already exists
   std::vector<SerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
   {
      char name[MM::MaxStrLength];
      (*i)->GetName(name);
      if (strcmp(name, portName) == 0)
      {
          (*i)->LogMessage(("adding reference to Port " + std::string(portName)).c_str() , true);
         (*i)->AddReference();
         return *i;
      }
   }

   // no such port found, so try to create a new one
   SerialPort* pPort = new SerialPort(portName);
   ports_.push_back(pPort);
   pPort->AddReference();
   return pPort;

}

void SerialManager::DestroyPort(MM::Device* port)
{
   std::vector<SerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
   {
      if (*i == port)
      {
         char theName[MM::MaxStrLength];
         (*i)->GetName(theName);
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

SerialPort::SerialPort(const char* portName) :
   initialized_(false),
   busy_(false),
   answerTimeoutMs_(500),
   refCount_(0),
   transmitCharWaitMs_(0.0),
   stopBits_(g_StopBits_1),
   parity_(g_Parity_None),
   pService_(0),
   pPort_(0),
   pThread_(0),
   verbose_(true),
   dtrEnable_(true)
{

   portName_ = portName;

   InitializeDefaultErrorMessages();

   // configure pre-initialization properties
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, portName_.c_str(), MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Serial port driver (boost:asio)", MM::String, true);
   assert(ret == DEVICE_OK);

   // baud
   CPropertyAction* pActBaud = new CPropertyAction (this, &SerialPort::OnBaud);
   ret = CreateProperty(MM::g_Keyword_BaudRate, g_Baud_9600, MM::String, false, pActBaud, true);
   assert(DEVICE_OK == ret);

   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_110, (long)110);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_300, (long)300);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_600, (long)600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_1200, (long)1200);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_2400, (long)2400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_4800, (long)4800);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_9600, (long)9600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_14400, (long)14400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_19200, (long)19200);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_38400, (long)38400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_57600, (long)57600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_115200, (long)115200);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_128000, (long)128000);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_230400, (long)230400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_460800, (long)460800);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_500000, (long)500000);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_921600, (long)921600);

   // data bits
   ret = CreateProperty(MM::g_Keyword_DataBits, "8", MM::String, true);
   assert(ret == DEVICE_OK);

   // stop bits
   CPropertyAction* pActStopBits = new CPropertyAction (this, &SerialPort::OnStopBits);
   ret = CreateProperty(MM::g_Keyword_StopBits, g_StopBits_1, MM::String, false, pActStopBits, true);
   assert(ret == DEVICE_OK);

   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_1, (long)boost::asio::serial_port_base::stop_bits::one);
   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_1_5, (long)boost::asio::serial_port_base::stop_bits::onepointfive);
   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_2, (long)boost::asio::serial_port_base::stop_bits::two);

   // parity
   CPropertyAction* pActParity = new CPropertyAction (this, &SerialPort::OnParity);
   ret = CreateProperty(MM::g_Keyword_Parity, g_Parity_None, MM::String, false, pActParity, true);
   assert(ret == DEVICE_OK);

   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_None, (long)boost::asio::serial_port_base::parity::none);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Odd, (long)boost::asio::serial_port_base::parity::odd);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Even, (long)boost::asio::serial_port_base::parity::even);

   // handshaking
   CPropertyAction* pActHandshaking = new CPropertyAction (this, &SerialPort::OnHandshaking);
   ret = CreateProperty(MM::g_Keyword_Handshaking, "Off", MM::String, false, pActHandshaking, true);
   assert(ret == DEVICE_OK);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Off, (long)boost::asio::serial_port_base::flow_control::none);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Hardware, (long)boost::asio::serial_port_base::flow_control::hardware);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Software, (long)boost::asio::serial_port_base::flow_control::software);

// on Windows only, DTR Enable:
// Since this does not make a difference for Sam Lord and Diskovery, comment out for now
// #ifdef WIN32
//   CPropertyAction* pActDTREnable = new CPropertyAction(this, &SerialPort::OnDTR);
//   ret = CreateProperty("DTR Control", "Enable", MM::String, false, pActDTREnable, true);
//   assert (ret == DEVICE_OK);
//   AddAllowedValue("DTR Control", "Enable");
//   AddAllowedValue("DTR Control", "Disable");
//#endif

   // answer timeout
   CPropertyAction* pActTimeout = new CPropertyAction (this, &SerialPort::OnTimeout);
   ret = CreateProperty("AnswerTimeout", "500", MM::Float, false, pActTimeout, true);
   assert(ret == DEVICE_OK);

   // transmission Delay                                                     
   CPropertyAction* pActTD = new CPropertyAction (this, &SerialPort::OnDelayBetweenCharsMs);
   ret = CreateProperty("DelayBetweenCharsMs", "0", MM::Float, false, pActTD, true);
   assert(ret == DEVICE_OK);   

   // verbose debug messages
   pActTD = new CPropertyAction (this, &SerialPort::OnVerbose);
   (void)CreateProperty("Verbose", (verbose_?"1":"0"), MM::Integer, false, pActTD, true);
   AddAllowedValue("Verbose", "0");
   AddAllowedValue("Verbose", "1");

}

SerialPort::~SerialPort()
{
   Shutdown();

   delete pPort_;
   delete pThread_;
   delete pService_;
}


int SerialPort::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // do not initialize if this port has been blacklisted
   std::vector<std::string>::iterator it = g_BlackListedPorts.begin();
   while (it < g_BlackListedPorts.end()) {
      if( portName_ == (*it))
         return ERR_PORT_BLACKLISTED;
      it++;
   }

   long sb;
   int ret = GetPropertyData(MM::g_Keyword_StopBits, stopBits_.c_str(), sb);
   assert(ret == DEVICE_OK);

   long parity;
   ret = GetPropertyData(MM::g_Keyword_Parity, parity_.c_str(), parity);

   long baud;
   ret = GetCurrentPropertyData(MM::g_Keyword_BaudRate, baud);
   assert(ret == DEVICE_OK);

   long handshake;
   ret = GetCurrentPropertyData(MM::g_Keyword_Handshaking, handshake);
   assert(ret == DEVICE_OK);

   pService_ = new boost::asio::io_service();

   try
   {
#ifdef _WIN32
      // On Windows, we create the native port ourselves, instead of letting
      // Boost.Asio do it. This is in order to work around an incompatibility
      // between certain USB-serial drivers (some versions of Silicon Labs) and
      // the manner in which Boost.Asio
      // (boost::asio::detail::win_iocp_serial_port_service) initializes serial
      // ports (the bug results in a stackoverflow or an error).
      HANDLE portHandle;
      int ret = OpenWin32SerialPort(portName_, portHandle);
      if (ret != DEVICE_OK)
      {
         return ret;
      }

      pPort_ = new AsioClient(*pService_,
            this->portName_,
            portHandle,
            boost::lexical_cast<unsigned int>(baud),
            boost::asio::serial_port::flow_control::type(handshake),
            boost::asio::serial_port::parity::type(parity),
            boost::asio::serial_port::stop_bits::type(sb),
            this);
#else
      pPort_ = new AsioClient(*pService_,
            boost::lexical_cast<unsigned int>(baud),
            this->portName_,
            boost::asio::serial_port::flow_control::type(handshake),
            boost::asio::serial_port::parity::type(parity),
            boost::asio::serial_port::stop_bits::type(sb),
            this);
#endif
   }
   catch (std::exception& e)
   {
      SetErrorText(ERR_OPEN_FAILED, e.what());
      LogMessage(e.what());
      return ERR_OPEN_FAILED;
   }

   try
   {
      pThread_ = new boost::thread(boost::bind(
               &boost::asio::io_service::run, pService_));
   }
   catch(std::exception& what)
   {
      SetErrorText(ERR_OPEN_FAILED, what.what());
      LogMessage(what.what(), false);
      return ERR_OPEN_FAILED;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

#ifdef _WIN32
int SerialPort::OpenWin32SerialPort(const std::string& portName,
      HANDLE& portHandle)
{
   std::string portPath = portName[0] == '\\' ?
      portName : "\\\\.\\" + portName;

   portHandle = CreateFileA(portPath.c_str(),
         GENERIC_READ | GENERIC_WRITE, 0, 0,
         OPEN_EXISTING, FILE_FLAG_OVERLAPPED, 0);
   if (portHandle == INVALID_HANDLE_VALUE)
   {
      DWORD err = GetLastError();
      LogMessage(("Failed to open serial port " + portPath + ": "
            "CreateFileA() returned Windows system error code " +
            boost::lexical_cast<std::string>(err)).c_str());
      return DEVICE_ERR;
   }

   DCB dcb;
   memset(&dcb, 0, sizeof(DCB));
   dcb.DCBlength = sizeof(DCB);
   if (!GetCommState(portHandle, &dcb))
   {
      DWORD err = GetLastError();
      LogMessage(("Failed to read serial port parameters: "
            "GetCommState() returned Windows system error code " +
            boost::lexical_cast<std::string>(err)).c_str());
      CloseHandle(portHandle);
      return DEVICE_ERR;
   }

   dcb.fBinary = TRUE;
   dcb.fDsrSensitivity = FALSE;
   dcb.fNull = FALSE;
   dcb.fAbortOnError = FALSE;
   // Since this does not make a difference for Sam Lord and Diskovery, comment out for now
   //if (dtrEnable_)
   //   dcb.fDtrControl = DTR_CONTROL_ENABLE;
   //else
   //   dcb.fDtrControl = DTR_CONTROL_DISABLE;

   // The following lines work around crashes caused by invalid or incorrect
   // values returned by some serial port drivers (some versions of Silicon
   // Labs USB-serial drivers).
   if (dcb.XonLim > 4096)
   {
      dcb.XonLim = 2048;
   }
   if (dcb.XoffLim > 4096)
   {
      dcb.XoffLim = 512;
   }
   dcb.ByteSize = 8;
   dcb.BaudRate = CBR_9600;
   // End of workaround settings.

   if (!SetCommState(portHandle, &dcb))
   {
      DWORD err = GetLastError();
      LogMessage(("Failed to set serial port parameters: "
            "SetCommState() returned Windows system error code " +
            boost::lexical_cast<std::string>(err)).c_str());
      CloseHandle(portHandle);
      return DEVICE_ERR;
   }

   COMMTIMEOUTS timeouts;
   memset(&timeouts, 0, sizeof(timeouts));
   timeouts.ReadIntervalTimeout = 1;
   if (!SetCommTimeouts(portHandle, &timeouts))
   {
      DWORD err = GetLastError();
      LogMessage(("Failed to set serial port parameters: "
            "SetCommTimeouts() returned Windows system error code " +
            boost::lexical_cast<std::string>(err)).c_str());
      CloseHandle(portHandle);
      return DEVICE_ERR;
   }

   return DEVICE_OK;
}
#endif // _WIN32

int SerialPort::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   if( 0 != pPort_)
   {
      pPort_->ShutDownInProgress(true);
      CDeviceUtils::SleepMs(100);
      pPort_->Close();
   }

   if( 0 != pThread_)
   {
      CDeviceUtils::SleepMs(100);
      if (!pThread_->timed_join(boost::posix_time::millisec(1000) )) {
         LogMessage("Failed to cleanly close port (thread join timed out)");
         pThread_->detach();
         g_BlackListedPorts.push_back(portName_);
      }
   }
   initialized_ = false;
 
   return DEVICE_OK;
}
  
void SerialPort::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, portName_.c_str());
}

std::string SerialPort::Name(void) const
{
   char value[MM::MaxStrLength];
   value[0] = 0;
   GetName(value);
   return std::string(value);
}

int SerialPort::SetCommand(const char* command, const char* term)
{
   if (!initialized_)
      return ERR_PORT_NOTINITIALIZED;

   std::string sendText(command);
   if (term != 0)
      sendText += term;

   if (sendText.size() == 0) {
      return DEVICE_OK;
   }

   if (transmitCharWaitMs_ < 0.001)
   {
      pPort_->WriteCharactersAsynchronously(sendText.c_str(), sendText.length());
   }
   else
   {
      for (std::string::iterator jj = sendText.begin(); jj != sendText.end(); ++jj)
      {
         pPort_->WriteOneCharacterAsynchronously(*jj);
         CDeviceUtils::SleepMs(static_cast<long>(0.5 + transmitCharWaitMs_));
      }
   }

   LogAsciiCommunication("SetCommand", false, sendText);

   return DEVICE_OK;
}

int SerialPort::GetAnswer(char* answer, unsigned bufLen, const char* term)
{
   if (!initialized_)
      return ERR_PORT_NOTINITIALIZED;

   if (bufLen < 1)
   {
      LogMessage("BUFFER_OVERRUN error occured!");
      return ERR_BUFFER_OVERRUN;
   }
   std::ostringstream logMsg;
   unsigned long answerOffset = 0;
   memset(answer,0,bufLen);
   char theData = 0;

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime answerTimeout(answerTimeoutMs_ * 1000.0);
   MM::MMTime nonTerminatedAnswerTimeout(5.0 * 1000.0); // For bug-compatibility
   while ((GetCurrentMMTime() - startTime)  < answerTimeout)
   {
      bool anyRead =  pPort_->ReadOneCharacter(theData);        
      if( anyRead )
      {
         if (bufLen <= answerOffset)
         {
            answer[answerOffset] = '\0';
            LogMessage("BUFFER_OVERRUN error occured!");
            return ERR_BUFFER_OVERRUN;
         }
         answer[answerOffset++] = theData;
      }
      else
      {
         //Yield to other threads:
         CDeviceUtils::SleepMs(1);
      }

      // look for the terminator, if any
      if (term && term[0])
      {
         // check for terminating sequence
         char* termPos = strstr(answer, term);
         if (termPos != 0) // found the terminator
         {
            LogAsciiCommunication("GetAnswer", true, answer);

            // erase the terminator from the answer:
            *termPos = '\0';

            return DEVICE_OK;
         }
      }
      else
      {
         // XXX Shouldn't it be an error to not have a terminator?
         // TODO Make it a precondition check (immediate error) once we've made
         // sure that no device adapter calls us without a terminator. For now,
         // keep the behavior for the sake of bug-compatibility.

         MM::MMTime elapsed = GetCurrentMMTime() - startTime;
         if (elapsed > nonTerminatedAnswerTimeout)
         {
            LogAsciiCommunication("GetAnswer", true, answer);
            long millisecs = static_cast<long>(elapsed.getMsec());
            LogMessage(("GetAnswer without terminator returning after " +
                     boost::lexical_cast<std::string>(millisecs) +
                     "msec").c_str(), true);
            return DEVICE_OK;
         }
      }
   }

   LogMessage("TERM_TIMEOUT error occured!");
   return ERR_TERM_TIMEOUT;
}

int SerialPort::Write(const unsigned char* buf, unsigned long bufLen)
{
   if (!initialized_)
      return ERR_PORT_NOTINITIALIZED;

   if (bufLen == 0) {
      return DEVICE_OK;
   }

   if (transmitCharWaitMs_ < 0.001)
   {
      pPort_->WriteCharactersAsynchronously(reinterpret_cast<const char*>(buf), bufLen);
   }
   else
   {
      for (size_t i = 0; i < bufLen; ++i)
      {
         pPort_->WriteOneCharacterAsynchronously(buf[i]);
         CDeviceUtils::SleepMs(static_cast<long>(0.5 + transmitCharWaitMs_));
      }
   }

   if (verbose_)
      LogBinaryCommunication("Write", false, buf, bufLen);

   return DEVICE_OK;
}
 
int SerialPort::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   if (!initialized_)
      return ERR_PORT_NOTINITIALIZED;

   int r = DEVICE_OK;
   if( 0 < bufLen)
   {
      // zero the buffer
      memset(buf, 0, bufLen);
      charsRead = 0;
      
      bool anyRead = false;
      char theData = 0;
      for( ;; )
      {
         anyRead = pPort_->ReadOneCharacter(theData); 
         if( anyRead)
         {
            buf[charsRead] = (unsigned char)theData;
            if( bufLen <= ++charsRead)
            {
               // buffer is full
               break;
            }
         }
         else
         {
            // no data was available
            break;
         }
      }
      if( 0 < charsRead)
      {
         if(verbose_)
            LogBinaryCommunication("Read", true, buf, charsRead);
      }
   }
   else
      r = ERR_BUFFER_OVERRUN;

   return r;
}

int SerialPort::Purge()
{
   if (!initialized_)
      return ERR_PORT_NOTINITIALIZED;

   pPort_->Purge();
   return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////////////
// Action interface
//
int SerialPort::OnStopBits(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stopBits_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         long stopBits;
         int ret;

         ret = GetCurrentPropertyData(MM::g_Keyword_StopBits, stopBits);

         if (ret != DEVICE_OK)
            return ret;
         pPort_->ChangeStopBits(boost::asio::serial_port_base::stop_bits::type(stopBits));
      }
      pProp->Get(stopBits_);
   }

   return DEVICE_OK;
}

int SerialPort::OnParity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(parity_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         long parity;
         int ret;

         ret = GetCurrentPropertyData(MM::g_Keyword_Parity, parity);

         if (ret != DEVICE_OK)
            return ret;
         pPort_->ChangeParity(boost::asio::serial_port_base::parity::type(parity));
      }
      pProp->Get(parity_);
   }

   return DEVICE_OK;
}

int SerialPort::OnBaud(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         long baud;
         int ret;

         ret = GetCurrentPropertyData(MM::g_Keyword_BaudRate, baud);
         if (ret != DEVICE_OK)
            return ret;
         pPort_->ChangeBaudRate((unsigned int)baud);
         pPort_->Purge();
      }
   }

   return DEVICE_OK;
}

int SerialPort::OnHandshaking(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         long handshake;
         int ret;

         ret = GetCurrentPropertyData(MM::g_Keyword_Handshaking, handshake);
         if (ret != DEVICE_OK)
            return ret;
         pPort_->ChangeFlowControl(boost::asio::serial_port_base::flow_control::type(handshake));
      }
   }

   return DEVICE_OK;
}

int SerialPort::OnDTR(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (dtrEnable_)
         pProp->Set("Enable");
      else
         pProp->Set("Disable");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string answer;
      pProp->Get(answer);
      if (answer == "Enable")
         dtrEnable_ = true;
      else
         dtrEnable_ = false;
   }

   return DEVICE_OK;
}


int SerialPort::OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int SerialPort::OnVerbose(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(verbose_?1L:0L);
   }
   else if (eAct == MM::AfterSet)
   {  
      long value;
      pProp->Get(value);
      verbose_ = !!value;
   }     

   return DEVICE_OK;

}


int SerialPort::OnDelayBetweenCharsMs(MM::PropertyBase* pProp, MM::ActionType eAct)
{  
   if (eAct == MM::BeforeGet)
   {  
      pProp->Set(transmitCharWaitMs_);
   }
   else if (eAct == MM::AfterSet)
   {  
      double transmitCharWaitMs;
      pProp->Get(transmitCharWaitMs);
      if (transmitCharWaitMs >= 0.0 && transmitCharWaitMs < 250.0)
         transmitCharWaitMs_ = transmitCharWaitMs;
   }     

   return DEVICE_OK;
}


// Helper functions for message logging
// (TODO: Do these have any utility outside of SerialManager?)

// Note: This returns true for ' ' (space).
static bool ShouldEscape(char ch)
{
   if (ch >= 0 && std::isgraph(ch))
      if (std::string("\'\"\\").find(ch) == std::string::npos)
         return false;
   return true;
}

static void PrintEscaped(std::ostream& strm, char ch)
{
   switch (ch)
   {
      // We leave out some less common C escape sequences that are more handy
      // to read as hex values (\a, \b, \f, \v).
      case '\'': strm << "\\\'"; break;
      case '\"': strm << "\\\""; break;
      case '\\': strm << "\\\\"; break;
      case '\0': strm << "\\0"; break;
      case '\n': strm << "\\n"; break;
      case '\r': strm << "\\r"; break;
      case '\t': strm << "\\t"; break;
      default:
      {
         // boost::format doesn't work with "%02hhx". Also note that the
         // reinterpret_cast to unsigned char is necessary to prevent sign
         // extension.
         unsigned char byte = *reinterpret_cast<unsigned char*>(&ch);
         strm << boost::format("\\x%02x") % static_cast<unsigned int>(byte);
         break;
      }
   }
}

static void FormatAsciiContent(std::ostream& strm, const char* begin, const char* end)
{
   // We log ASCII data in an unambiguous format free of control characters and
   // spaces. The format used is a valid C escaped string (without the
   // surrounding quotes), with the exception of '?' not being escaped even if
   // it constitutes part of a trigraph.

   // We want to escape leading and trailing spaces, but not internal spaces,
   // for maximum readability and zero ambiguity.
   bool hasEncounteredNonSpace = false;
   unsigned pendingSpaces = 0;

   for (const char* p = begin; p != end; ++p)
   {
      if (*p == ' ')
      {
         if (!hasEncounteredNonSpace)
            PrintEscaped(strm, ' '); // Leading space
         else
            ++pendingSpaces; // Don't know yet if internal or trailing space
      }
      else // *p != ' '
      {
         if (!hasEncounteredNonSpace)
            hasEncounteredNonSpace = true;
         else
         {
            while (pendingSpaces > 0)
            {
               strm << ' '; // Internal space
               --pendingSpaces;
            }
         }

         if (ShouldEscape(*p))
            PrintEscaped(strm, *p);
         else
            strm << *p;
      }
   }

   while (pendingSpaces > 0)
   {
      PrintEscaped(strm, ' '); // Trailing space
      --pendingSpaces;
   }
}

static void FormatBinaryContent(std::ostream& strm, const unsigned char* begin, const unsigned char* end)
{
   for (const unsigned char* p = begin; p != end; ++p)
   {
      if (p != begin)
         strm << ' ';
      strm << boost::format("%02x") % static_cast<unsigned int>(*p);
   }
}

static void PrintCommunicationPrefix(std::ostream& strm, const char* prefix, bool isInput)
{
   strm << prefix;
   strm << (isInput ? " <- " : " -> ");
}

void SerialPort::LogAsciiCommunication(const char* prefix, bool isInput, const std::string& data)
{
   std::ostringstream oss;
   PrintCommunicationPrefix(oss, prefix, isInput);
   FormatAsciiContent(oss, data.c_str(), data.c_str() + data.size());
   LogMessage(oss.str().c_str(), true);
}

void SerialPort::LogBinaryCommunication(const char* prefix, bool isInput, const unsigned char* pdata, std::size_t length)
{
   std::ostringstream oss;
   PrintCommunicationPrefix(oss, prefix, isInput);
   oss << "(hex) ";
   FormatBinaryContent(oss, pdata, pdata + length);
   LogMessage(oss.str().c_str(), true);
}
