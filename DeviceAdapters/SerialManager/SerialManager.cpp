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
//
// CVS:           $Id$
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
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

#ifdef linux
#include <dirent.h>
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "SerialManager.h"
#include <sstream>
#include <cstdio>

#include <deque> 
#include <iostream> 
#include <boost/bind.hpp> 
#include <boost/asio.hpp> 
#include <boost/asio/serial_port.hpp> 
#include <boost/thread.hpp> 
#include <boost/lexical_cast.hpp> 
#include <boost/date_time/posix_time/posix_time_types.hpp> 

// serial device implementation class
#include "AsioClient.h"


MMThreadLock g_portLock;

SerialManager g_serialManager;

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
const char* g_Baud_57600 = "57600";
const char* g_Baud_115200 = "115200";

const char* g_Handshaking_Off = "Off";
const char* g_Handshaking_Hardware = "Hardware";
const char* g_Handshaking_Software = "Software";

const char* g_Parity_None = "None";
const char* g_Parity_Odd = "Odd";
const char* g_Parity_Even = "Even";
const char* g_Parity_Mark = "Mark";
const char* g_Parity_Space = "Space";

#ifdef WIN32
#include <time.h>
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

/*
 * Tests whether given serial port can be used by opening it
 */
bool SerialPortLister::portAccessible(const char* portName)
{
   MMThreadGuard g2(g_portLock);
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

void SerialPortLister::ListPorts(std::vector<std::string> &availablePorts)
{
#ifdef WIN32
   char allDeviceNames[100000];

   // on Windows the serial ports are devices that begin with "COM"
   int ret = QueryDosDevice( 0, allDeviceNames, 100000);
   if( 0!= ret)
   {
      for( int ii = 0; ii < ret; ++ii)
      {
         if ( 0 == allDeviceNames[ii])
            allDeviceNames[ii] = ' ';
      }
      std::string all(allDeviceNames, ret);
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
   // Look for /dev files with correct signature in their name                   
   DIR* pdir = opendir("/dev");                                                  
   struct dirent *pent;                                                          
   if (pdir) {                                                                   
      while (pent = readdir(pdir)) {                                             
         if ( (strstr(pent->d_name, "ttyS") != 0) || (strstr(pent->d_name, "ttyUSB") != 0) )  {
            string p = ("/dev/");                                                
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
       printf("IOServiceMatching returned a NULL dictionary.\n");                
   } else {                                                                      
       CFDictionarySetValue(classesToMatch,                                      
                           CFSTR(kIOSerialBSDTypeKey),                           
                           CFSTR(kIOSerialBSDAllTypes));                         
   }                                                                             
   kernResult = IOServiceGetMatchingServices(kIOMasterPortDefault, classesToMatch, &serialPortIterator);
   if (KERN_SUCCESS != kernResult) {                                             
      printf("IOServiceGetMatchingServices returned %d\n", kernResult);          
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
          printf("%s\n", bsdPath);                                                
                                                                                    
           // add the name to our vector<string> only when this is not a dialup port
          std::string rresult (bsdPath);                                              
          std::string::size_type loc = rresult.find("DialupNetwork", 0);              
          if (result && (loc == std::string::npos)) {                                 
              if (portAccessible(bsdPath))  {                                     
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
      AddAvailableDeviceName((*it).c_str(), "Serial communication port");
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
   pPort->LogMessage(("created new Port " + std::string(portName)).c_str() , true);
   ports_.push_back(pPort);
   pPort->AddReference();
   pPort->LogMessage(("adding reference to Port " + std::string(portName)).c_str() , true);
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
         //(*i)->LogMessage("Removing reference to Port " + std::string(theName) , true);
         (*i)->RemoveReference();

         // really destroy only if there are no references pointing to the port
         if ((*i)->OKToDelete())
         {
            //(*i)->LogMessage("deleting Port " + std::string(theName)) , true);
            delete *i;
            ports_.erase(i);
         }
         return;       
      }
   }
}

SerialPort::SerialPort(const char* portName) :
   refCount_(0),
   pService_(0),
   pPort_(0),
   pThread_(0),
   busy_(false),
   initialized_(false),
   portTimeoutMs_(2000.0),
   answerTimeoutMs_(500),
   transmitCharWaitMs_(0.0),
   stopBits_(g_StopBits_1),
   parity_(g_Parity_None)
{
   MMThreadGuard g(portLock_);
   charsFoundBeyondTerminator_.clear();

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
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_57600, (long)57600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_115200, (long)115200);

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

   // answer timeout
   CPropertyAction* pActTimeout = new CPropertyAction (this, &SerialPort::OnTimeout);
   ret = CreateProperty("AnswerTimeout", "500", MM::Float, false, pActTimeout, true);
   assert(ret == DEVICE_OK);

   // transmission Delay                                                     
   CPropertyAction* pActTD = new CPropertyAction (this, &SerialPort::OnDelayBetweenCharsMs);
   ret = CreateProperty("DelayBetweenCharsMs", "0", MM::Float, false, pActTD, true);
   assert(ret == DEVICE_OK);                                                 

}

SerialPort::~SerialPort()
{
   Shutdown();
   MMThreadGuard g(portLock_);
   delete pPort_;
   delete pThread_;
   delete pService_;
}


int SerialPort::Initialize()
{
   // verify callbacks are supported
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;

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

   MMThreadGuard g2(portLock_);
   try
   {
      pPort_ = new AsioClient (*pService_, boost::lexical_cast<unsigned int>(baud), this->portName_,
          boost::asio::serial_port_base::flow_control::type(handshake),
          boost::asio::serial_port_base::parity::type(parity),
          boost::asio::serial_port_base::stop_bits::type(sb),
          this
            ); 
   }
   catch( std::exception& what)
   {
      LogMessage(what.what(),false);
      return DEVICE_ERR;
   }

   try
   {
      pThread_ = new boost::thread(boost::bind(&boost::asio::io_service::run, pService_)); 
   }
   catch(std::exception& what)
   {
      LogMessage(what.what(), false);
      return DEVICE_ERR;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int SerialPort::Shutdown()
{
   //std::ostringstream logMsg;
   //logMsg << "shutting down Serial port " << portName_  << std::endl; 
   //LogMessage(logMsg.str().c_str());

   do
   {
      MMThreadGuard g(portLock_);
      if( 0 != pPort_)
      {
         CDeviceUtils::SleepMs(100);
         pPort_->Close();
      }
   }while(bfalse_s);
   if( 0 != pThread_)
   {
      CDeviceUtils::SleepMs(100);
      pThread_->join();
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
   bool bfalse = false;
   std::string sendText(command);
   if (term != 0)
      sendText += term;

   // send characters one by one to accomodate slow devices
   unsigned long written = 0;

   if (transmitCharWaitMs_==0)
   {
      MMThreadGuard g(portLock_);
      for( std::string::iterator jj = sendText.begin(); jj != sendText.end(); ++jj)
      {
         pPort_->WriteOneCharacter(*jj);
         ++written;
      }
   }
   else
   {
      for( std::string::iterator jj = sendText.begin(); jj != sendText.end(); ++jj)
      {
         const MM::MMTime maxTime (5, 0);
         MM::MMTime startTime (GetCurrentMMTime());
         do
         {
            MMThreadGuard g(portLock_);
            pPort_->WriteOneCharacter(*jj);
         }while(bfalse);
         CDeviceUtils::SleepMs((long)(0.5+transmitCharWaitMs_));         
         ++written;
      }
   }
   LogMessage( (std::string("SetCommand -> ") + sendText.substr(0,written)).c_str(), true);
   return DEVICE_OK;
}

int SerialPort::GetAnswer(char* answer, unsigned bufLen, const char* term)
{
   if (bufLen < 1)
   {
      LogMessage("BUFFER_OVERRUN error occured!");
      return ERR_BUFFER_OVERRUN;
   }
   std::ostringstream logMsg;
   unsigned long nCharactersRead(0);
   std::vector<char> theData;
   memset(answer,0,bufLen);
   bool dataAlreadyAvailableOnFirstIteration = false;

   // load the remainder from the last packet into the buffer.
   for( std::vector<char>::iterator cby = charsFoundBeyondTerminator_.begin(); cby != charsFoundBeyondTerminator_.end(); ++cby)
   {
      answer[nCharactersRead++] = *cby;
   }
   charsFoundBeyondTerminator_.clear();
   if( 0 < nCharactersRead)
      dataAlreadyAvailableOnFirstIteration = true;

   CDeviceUtils::SleepMs((long)(0.5 + transmitCharWaitMs_));

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime retryWarnTime(0);
   MM::MMTime answerTimeout(answerTimeoutMs_ * 1000.0);
   // warn of retries every 200 ms.
   MM::MMTime retryWarnInterval(0, 200000);
   int retryCounter = 0;
   int retryCounterDisplayedInLog = 0;
   while ((GetCurrentMMTime() - startTime)  < answerTimeout)
   {
      if (retryCounter > 2)
      {
         MM::MMTime tNow = GetCurrentMMTime();
         if ( retryWarnInterval < tNow - retryWarnTime)
         {
            retryWarnTime = tNow;
            LogMessage((std::string("GetAnswer # retries = ") + 
               boost::lexical_cast<std::string,int>(retryCounter)).c_str(), true);
            retryCounterDisplayedInLog = retryCounter;
         }
      }
      int nRead = 0;
      do  // just a scope for the thread guard
      {
         MMThreadGuard g(portLock_);
         theData = pPort_->ReadData();
         nRead = theData.size();
         if( 0 < nRead)
            LogBinaryMessage(true, theData, true);

      }
      while ( bfalse_s );

      if( 0 == nRead && (!dataAlreadyAvailableOnFirstIteration) )
      {
         CDeviceUtils::SleepMs((long)(0.5 + transmitCharWaitMs_));
         retryCounter++;
      }
      else
      {  // append these characters onto the received message
         dataAlreadyAvailableOnFirstIteration = false;  // afterwards, we want to increment the retry counter
         for( int offset = 0; offset < nRead; ++offset)
            answer[nCharactersRead + offset] = theData.at(offset);
         nCharactersRead += nRead;
         if (bufLen <= nCharactersRead)
         {
            answer[bufLen-1] = '\0';
            LogMessage("BUFFER_OVERRUN error occured!");
            return ERR_BUFFER_OVERRUN;
         }
         else
         {
            answer[nCharactersRead] = '\0';
         }

         if (term == 0 )
         {
            if( retryCounterDisplayedInLog < retryCounter )
               LogMessage((std::string("GetAnswer # retries = ") + 
                  boost::lexical_cast<std::string,int>(retryCounter)).c_str(), true);
            return DEVICE_OK; // no termintor specified
         }
         else
         {
            // check for terminating sequence
            char* termPos = strstr(answer, term);
            if (termPos != 0)
            {
               // there may be characters in the buffer beyond the terminator !!
               for( char* pbeyond = termPos+strlen(term)+1; pbeyond < (answer + nCharactersRead); ++ pbeyond)
               {
                  charsFoundBeyondTerminator_.push_back(*pbeyond);
                  *pbeyond = '\0';
               }
               *termPos = '\0';
               MM::MMTime totalTime = GetCurrentMMTime() - startTime;
               if( retryCounterDisplayedInLog < retryCounter )
                  LogMessage((std::string("GetAnswer # retries = ") + 
                     boost::lexical_cast<std::string,int>(retryCounter)).c_str(), true);
               return DEVICE_OK;
            }
         }
      }
   }

   LogMessage("TERM_TIMEOUT error occured!");
   return ERR_TERM_TIMEOUT;
}

int SerialPort::Write(const unsigned char* buf, unsigned long bufLen)
{
   // send characters one by one to accomodate for slow devices
   std::ostringstream logMsg;
   for (unsigned i=0; i<bufLen; i++)
   {
      MMThreadGuard g(portLock_);
      pPort_->WriteOneCharacter(*(buf + i));
      CDeviceUtils::SleepMs((unsigned long)(0.5+transmitCharWaitMs_));
      logMsg << (int) *(buf + i) << " ";
   }
   if( 0 < bufLen)
      LogBinaryMessage(false, buf, bufLen, true);

   return DEVICE_OK;
}
 
int SerialPort::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   int r = DEVICE_OK;
   // fill the buffer with zeros
   memset(buf, 0, bufLen);

   charsRead = 0;
   MMThreadGuard g(portLock_);
   std::vector<char> d = pPort_->ReadData();
   if( 0<d.size())
      LogBinaryMessage(true, d, true);
   if( bufLen < d.size())
   {
      LogMessage("BUFFER_OVERRUN error occured!");
      r = ERR_BUFFER_OVERRUN;
   }
   charsRead = d.size();
   unsigned int oi = 0;
   for( std::vector<char>::iterator ii = d.begin(); (oi < bufLen)&&(ii!=d.end()); ++oi, ++ii)
   {
      buf[oi] = (unsigned char)(*ii);
   }
   charsRead = oi;


   return r;
}

int SerialPort::Purge()
{
   MMThreadGuard g(portLock_);
   charsFoundBeyondTerminator_.clear();
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
         pProp->Set(stopBits_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
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
         pProp->Set(parity_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
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
         return ERR_PORT_CHANGE_FORBIDDEN;

      // TODO: allow changing baud rate on-the-fly
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
         return ERR_PORT_CHANGE_FORBIDDEN;
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

void SerialPort::LogBinaryMessage( const bool isInput, const unsigned char*const pdata, const int length, bool debugOnly)
{
   std::vector<unsigned char> tmpString;
   for( const unsigned char* p = pdata; p < pdata+length; ++ p)
   {
      tmpString.push_back(*p);
   }
   LogBinaryMessage(isInput, tmpString, debugOnly);
}

void SerialPort::LogBinaryMessage( const bool isInput, const std::vector<char>& data, bool debugOnly)
{
   std::vector<unsigned char> tmpString;
   for( std::vector<char>::const_iterator p = data.begin(); p != data.end(); ++ p)
   {
      tmpString.push_back((unsigned char)*p);
   }
   LogBinaryMessage(isInput, tmpString, debugOnly);
}

#define DECIMALREPRESENTATIONOFCONTROLCHARACTERS 1
void SerialPort::LogBinaryMessage( const bool isInput, const std::vector<unsigned char>& data, bool debugOnly)
{
   std::string messs;
   // input or output
   messs += (isInput ? "<- " : "-> ");
   for( std::vector<unsigned char>::const_iterator ii = data.begin(); ii != data.end(); ++ii)
   {
#ifndef DECIMALREPRESENTATIONOFCONTROLCHARACTERS
      // caret representation:
            messs +=   ( 31 < *ii) ? boost::lexical_cast<std::string, unsigned char>(*ii) : 
         ("^" + boost::lexical_cast<std::string, unsigned char>(64+*ii));
#else
      messs +=   ( 31 < *ii)    ?  boost::lexical_cast<std::string, unsigned char>(*ii) : 
         ("<" + boost::lexical_cast<std::string, unsigned short>((unsigned short)*ii) + ">");
#endif
   }
   LogMessage(messs.c_str(),debugOnly);
}
