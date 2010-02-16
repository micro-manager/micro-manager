///////////////////////////////////////////////////////////////////////////////
// FILE:          SerialManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial port device adapter, Windows only
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
// NOTE:          Windows specific implementation uses CSerial classes by Ramon Klein
//                http://home.ict.nl/~ramklein/Projects/Serial.html 
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/21/2005
//
// CVS:           $Id$
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "../../MMDevice/ModuleInterface.h"
#include "SerialManager.h"
#include "serial.h"
#include <sstream>
#include <cstdio>

using namespace std;

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

   if (g_PortList.size() == 0 || stale) {
      char portName[16];
      char portNameWinAPI[16];
      for (int i=1; i<=256; i++) {
         sprintf(portName, "COM%d", i);
         sprintf(portNameWinAPI, "\\\\.\\%s",portName);
         if (CSerial::EPortAvailable == CSerial::CheckPort(portNameWinAPI)){
            g_PortList.push_back(portName);
         }
      }
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
   vector<SerialPort*>::iterator i;
   for (i=ports_.begin(); i!=ports_.end(); i++)
      delete *i;
}

MM::Device* SerialManager::CreatePort(const char* portName)
{
   // check if the port already exists
   vector<SerialPort*>::iterator i;
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
   SerialPort* pPort = new SerialPort(portName);
//   if (pPort->Open(portName) == DEVICE_OK)
//   {
      // open port succeeded, so add it to the list
      ports_.push_back(pPort);
      pPort->AddReference();
      return pPort;
//   }

   // open port failed
//   delete pPort;
//   return 0;
}

void SerialManager::DestroyPort(MM::Device* port)
{
   vector<SerialPort*>::iterator i;
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

SerialPort::SerialPort(const char* portName) :
   refCount_(0),
   port_(0),
   busy_(false),
   initialized_(false),
   portTimeoutMs_(2000.0),
   answerTimeoutMs_(500),
   transmitCharWaitMs_(0.0),
   stopBits_(g_StopBits_1),
   parity_(g_Parity_None)
{
   MMThreadGuard g(portLock_);
   port_ = new CSerial();

   portName_ = portName;
   portNameWinAPI_ = "\\\\.\\";
   portNameWinAPI_ += portName;


   InitializeDefaultErrorMessages();

   // configure pre-initialization properties
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, portName_.c_str(), MM::String, true);
   assert(ret == DEVICE_OK);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Serial port driver (Win32)", MM::String, true);
   assert(ret == DEVICE_OK);

   // baud
   CPropertyAction* pActBaud = new CPropertyAction (this, &SerialPort::OnBaud);
   ret = CreateProperty(MM::g_Keyword_BaudRate, g_Baud_9600, MM::String, false, pActBaud, true);
   assert(DEVICE_OK == ret);

   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_110, (long)CSerial::EBaud110);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_300, (long)CSerial::EBaud300);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_600, (long)CSerial::EBaud600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_1200, (long)CSerial::EBaud1200);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_2400, (long)CSerial::EBaud2400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_4800, (long)CSerial::EBaud4800);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_9600, (long)CSerial::EBaud9600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_14400, (long)CSerial::EBaud14400);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_19200, (long)CSerial::EBaud19200);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_57600, (long)CSerial::EBaud57600);
   AddAllowedValue(MM::g_Keyword_BaudRate, g_Baud_115200, (long)CSerial::EBaud115200);

   // data bits
   ret = CreateProperty(MM::g_Keyword_DataBits, "8", MM::String, true);
   assert(ret == DEVICE_OK);

   // stop bits
   CPropertyAction* pActStopBits = new CPropertyAction (this, &SerialPort::OnStopBits);
   ret = CreateProperty(MM::g_Keyword_StopBits, g_StopBits_1, MM::String, false, pActStopBits, true);
   assert(ret == DEVICE_OK);

   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_1, (long)CSerial::EStop1);
   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_1_5, (long)CSerial::EStop1_5);
   AddAllowedValue(MM::g_Keyword_StopBits, g_StopBits_2, (long)CSerial::EStop2);

   // parity
   CPropertyAction* pActParity = new CPropertyAction (this, &SerialPort::OnParity);
   ret = CreateProperty(MM::g_Keyword_Parity, g_Parity_None, MM::String, false, pActParity, true);
   assert(ret == DEVICE_OK);

   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_None, (long)CSerial::EParNone);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Odd, (long)CSerial::EParOdd);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Even, (long)CSerial::EParEven);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Mark, (long)CSerial::EParMark);
   AddAllowedValue(MM::g_Keyword_Parity, g_Parity_Space, (long)CSerial::EParSpace);

   // handshaking
   CPropertyAction* pActHandshaking = new CPropertyAction (this, &SerialPort::OnHandshaking);
   ret = CreateProperty(MM::g_Keyword_Handshaking, "Off", MM::String, false, pActHandshaking, true);
   assert(ret == DEVICE_OK);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Off, (long)CSerial::EHandshakeOff);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Hardware, (long)CSerial::EHandshakeHardware);
   AddAllowedValue(MM::g_Keyword_Handshaking, g_Handshaking_Software, (long)CSerial::EHandshakeSoftware);

   // answer timeout
   CPropertyAction* pActTimeout = new CPropertyAction (this, &SerialPort::OnTimeout);
   ret = CreateProperty("AnswerTimeout", "500", MM::Float, false, pActTimeout, true);
   assert(ret == DEVICE_OK);

   // transmission Delay                                                     
   CPropertyAction* pActTD = new CPropertyAction (this, &SerialPort::OnDelayBetweenCharsMs);
   ret = CreateProperty("DelayBetweenCharsMs", "0", MM::Float, false, pActTD, true);
   assert(ret == DEVICE_OK);                                                 

   // ret = UpdateStatus();
   // assert(ret == DEVICE_OK);
}

SerialPort::~SerialPort()
{
   MMThreadGuard g(portLock_);
   Shutdown();
   delete port_;
}

int SerialPort::Open()
{
   MMThreadGuard g(portLock_);
   assert(port_);

   long lastError;
   lastError = port_->Open(portNameWinAPI_.c_str(), 0, 0, false);
	if (lastError != ERROR_SUCCESS)
		return ERR_OPEN_FAILED;

   ostringstream logMsg;
   logMsg << "Serial port " << portName_ << " opened." << endl; 
   this->LogMessage(logMsg.str().c_str());

   return DEVICE_OK;
}

int SerialPort::Initialize()
{
   MMThreadGuard g(portLock_);

   assert(port_);

   // verify callbacks are supported and refuse to continue if not
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;

   int ret = Open();
   if (ret != DEVICE_OK)
      return ret;

   long sb;
   ret = GetPropertyData(MM::g_Keyword_StopBits, stopBits_.c_str(), sb);
   assert(ret == DEVICE_OK);

   long parity;
   ret = GetPropertyData(MM::g_Keyword_Parity, parity_.c_str(), parity);

   long baud;
   ret = GetCurrentPropertyData(MM::g_Keyword_BaudRate, baud);
   assert(ret == DEVICE_OK);

   long lastError = port_->Setup((CSerial::EBaudrate)baud, CSerial::EData8, (CSerial::EParity)parity, (CSerial::EStopBits)sb);
	if (lastError != ERROR_SUCCESS)
		return ERR_SETUP_FAILED;

   // set-up handshaking
   long handshake;
   ret = GetCurrentPropertyData(MM::g_Keyword_Handshaking, handshake);
   assert(ret == DEVICE_OK);

   lastError = port_->SetupHandshaking((CSerial::EHandshake)handshake);
	if (lastError != ERROR_SUCCESS)
		return ERR_HANDSHAKE_SETUP_FAILED;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int SerialPort::Shutdown()
{
   if (port_->IsOpen())
      port_->Close();
   initialized_ = false;

   ostringstream logMsg;
   logMsg << "Serial port " << portName_ << " closed." << endl; 
   this->LogMessage(logMsg.str().c_str());
   
   return DEVICE_OK;
}
  
void SerialPort::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, portName_.c_str());
}


int SerialPort::SetCommand(const char* command, const char* term)
{
   string sendText(command);

   if (term != 0)
      sendText += term;

   // send characters one by one to accomodate for slow devices
   unsigned long written = 0;
   long lastError;

/*   MM::MMTime beginWriteTime;
   MM::MMTime beginWriteTime0 = GetCurrentMMTime();
   MM::MMTime totalWriteTime[32];
   */

   if (transmitCharWaitMs_==0)
   {
      MMThreadGuard g(portLock_);
      lastError = port_->Write(sendText.c_str(), sendText.length(), (DWORD *) &written);
      if (lastError != ERROR_SUCCESS)
      {
         LogMessage("TRANSMIT_FAILED error occured!");
	      return ERR_TRANSMIT_FAILED;
      }
   }
   else
   {
      for (unsigned i=0; i<sendText.length(); i++)
      {
         
         unsigned long one = 0;


         const MM::MMTime maxTime (5, 0);
         MM::MMTime startTime (GetCurrentMMTime());
         int retryCounter = 0;
         do
         {
            MMThreadGuard g(portLock_);
            lastError = port_->Write(sendText.c_str() + written, 1, &one);
            Sleep((DWORD)transmitCharWaitMs_);         
            if (retryCounter > 0)
               LogMessage("Retrying serial Write command!");
            retryCounter++;
         }
         while (lastError != ERROR_SUCCESS && ((GetCurrentMMTime() - startTime) < maxTime));
         
	      if (lastError != ERROR_SUCCESS)
         {
            LogMessage("TRANSMIT_FAILED error occured!");
		      return ERR_TRANSMIT_FAILED;
         }
         
         assert (one == 1);
         written++;
         
      }
   }

//   MM::MMTime totalWriteTimeAll = GetCurrentMMTime()-beginWriteTime0;
   

   

   assert(written == sendText.length());
   ostringstream logMsg;                                                     
   logMsg << "Serial port " << portName_ << " wrote: " << sendText;
   this->LogMessage(logMsg.str().c_str(), true);

   return DEVICE_OK;
}

int SerialPort::GetAnswer(char* answer, unsigned bufLen, const char* term)
{
   if (bufLen < 1)
   {
      LogMessage("BUFFER_OVERRUN error occured!");
      return ERR_BUFFER_OVERRUN;
   }

   ostringstream logMsg;
   unsigned long read(0);
   unsigned long totalRead(0);
   char* bufPtr = answer;
   //long lastError = port_->Read(bufPtr, bufLen, &read, 0, (DWORD)portTimeoutMs_);

   long lastError;
   const MM::MMTime maxTime (5, 0);
   MM::MMTime retryStart (GetCurrentMMTime());
   int retryCounter = 0;
   do
   {
      MMThreadGuard g(portLock_);
      lastError = port_->Read(bufPtr, 1 /*bufLen*/, &read);
      if (retryCounter > 0)
         LogMessage("Retrying serial Read command!\n");
      retryCounter++;
   }
   while (lastError != ERROR_SUCCESS && ((GetCurrentMMTime() - retryStart) < maxTime));

	if (lastError != ERROR_SUCCESS)
   {
      answer[0] = '\0';
      LogMessage("RECEIVE_FAILED error occured!");
		return ERR_RECEIVE_FAILED;
   }

   // append zero
   bufPtr[read] = '\0';

   bufPtr += read;
   totalRead += read;

   if (term == 0)
      return DEVICE_OK; // not term specified

   // check for terminating sequence
   char* termPos = strstr(answer, term);
   if (termPos != 0)
   {
      *termPos = '\0';
      logMsg << " Port: " << portName_ << "." << "Read: " << answer;       
      LogMessage(logMsg.str().c_str(), true);
      return DEVICE_OK;
   }

   // wait a little more for the proper termination sequence
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime answerTimeout(answerTimeoutMs_ * 1000.0);
   while ((GetCurrentMMTime() - startTime)  < answerTimeout)
   {
      if (bufLen <= totalRead)
      {
         answer[bufLen-1] = '\0';
         LogMessage("BUFFER_OVERRUN error occured!");
         return ERR_BUFFER_OVERRUN;
      }

      read = 0;
      retryCounter = 0;
      retryStart = GetCurrentMMTime();
      do
      {
         MMThreadGuard g(portLock_);
         lastError = port_->Read(bufPtr , /*bufLen-totalRead*/1, &read);
         if (retryCounter > 0)
            LogMessage("Retrying serial Read command!\n");
         retryCounter++;
      }
      while (lastError != ERROR_SUCCESS && (GetCurrentMMTime() - retryStart) < maxTime);

      if (lastError != ERROR_SUCCESS)
      {
         answer[totalRead] = '\0';
         LogMessage("RECEIVE_FAILED error occured!");
		   return ERR_RECEIVE_FAILED;
      }

      bufPtr[read] = '\0';
      bufPtr += read;
      totalRead += read;
      termPos = strstr(answer, term);
      if (termPos != 0)
      {
         *termPos = '\0';
         logMsg << " Port: " << portName_ << "." << "Read: " << answer;       
         this->LogMessage(logMsg.str().c_str(), true);
         MM::MMTime totalTime = GetCurrentMMTime() - startTime;
         return DEVICE_OK;
      }
   }


   LogMessage("TERM_TIMEOUT error occured!");
   return ERR_TERM_TIMEOUT;
}

int SerialPort::Write(const unsigned char* buf, unsigned long bufLen)
{
   // send characters one by one to accomodate for slow devices
   ostringstream logMsg;
   logMsg << "Serial TX: ";
   for (unsigned i=0; i<bufLen; i++)
   {
      MMThreadGuard g(portLock_);
      unsigned long written = 0;
      long lastError = port_->Write(buf + i, 1, &written);
      Sleep((DWORD)transmitCharWaitMs_);
	   if (lastError != ERROR_SUCCESS || written != 1)
      {
         LogMessage("TRANSMIT_FAILED error occured!");
		   return ERR_TRANSMIT_FAILED;      
      }
      logMsg << (int) *(buf + i) << " ";
   }
   
   logMsg << endl;
   LogMessage(logMsg.str().c_str(), true);

   return DEVICE_OK;
}
 
int SerialPort::Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead)
{
   // fill the buffer with zeros
   memset(buf, 0, bufLen);
   ostringstream logMsg;
   logMsg << "Serial RX: ";

   charsRead = 0;
   MMThreadGuard g(portLock_);
   long lastError = port_->Read(buf, bufLen, &charsRead);
	if (lastError != ERROR_SUCCESS)
   {
      LogMessage("RECEIVE_FAILED error occured!");
		return ERR_RECEIVE_FAILED;
   }

   //for (unsigned long i=0; i<charsRead; i++)
   //   if (buf[i]==10)
   //      logMsg << "\\n";
   //   else
   //      logMsg << buf[i];

   //logMsg << " [";

   //for (unsigned long i=0; i<charsRead; i++)
   //   logMsg << (int) *(buf + i) << " ";

   //logMsg << "]" << endl;

   //if (charsRead > 0)
   //   LogMessage(logMsg.str().c_str(), true);

   return DEVICE_OK;
}

int SerialPort::Purge()
{
   MMThreadGuard g(portLock_);
   long lastError = port_->Purge();
	if (lastError != ERROR_SUCCESS)
		return ERR_PURGE_FAILED;
   
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

