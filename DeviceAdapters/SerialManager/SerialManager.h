///////////////////////////////////////////////////////////////////////////////
// FILE:          SerialManager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   serial port device adapter 
//                
// AUTHOR:        
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
// CVS:           $Id$
//

#ifndef _SERIALMANAGER_H_
#define _SERIALMANAGER_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <iostream>

#ifdef WIN32
// suppress an incomprehensible warning from inside boost future.
#pragma warning( push )
#pragma warning( disable : 4512 )
#endif

#include <boost/bind.hpp> 
#include <boost/asio.hpp> 
#include <boost/asio/serial_port.hpp> 
#include <boost/thread.hpp> 

#ifdef WIN32
#pragma warning( pop )
#endif


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_LABEL 100
#define ERR_OPEN_FAILED 101
#define ERR_SETUP_FAILED 102
#define ERR_HANDSHAKE_SETUP_FAILED 103
#define ERR_TRANSMIT_FAILED 104
#define ERR_RECEIVE_FAILED 105
#define ERR_BUFFER_OVERRUN 106
#define ERR_TERM_TIMEOUT 107
#define ERR_PURGE_FAILED 108
#define ERR_PORT_CHANGE_FORBIDDEN 109
#define ERR_PORT_BLACKLISTED 110
#define ERR_PORT_NOTINITIALIZED 111

class AsioClient;

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMStateDevice interfaces
//

class SerialPort : public CSerialBase<SerialPort>  
{
public:
   SerialPort(const char* portName);
   ~SerialPort();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   int SetCommand(const char* command, const char* term);
   int GetAnswer(char* answer, unsigned bufLength, const char* term);
   int Write(const unsigned char* buf, unsigned long bufLen);
   int Read(unsigned char* buf, unsigned long bufLen, unsigned long& charsRead);
   MM::PortType GetPortType() const {return MM::SerialPort;}    
   int Purge();

   std::string Name(void) const;

   void LogMessage(const char *const p, bool debugOnly = false)
	{
      if(this->IsCallbackRegistered())
         CSerialBase<SerialPort>::LogMessage(std::string(p), debugOnly);
      else
         std::cerr << p << std::endl;;
	};

   void LogBinaryMessage( const bool isInput, const unsigned char*const pdata, const int length, bool debugOnly = false);
   void LogBinaryMessage( const bool isInput, const std::vector<unsigned char>& data, bool debugOnly = false);
   void LogBinaryMessage( const bool isInput, const std::vector<char>& data, bool debugOnly = false);

   
   // action interface
   // ----------------
   int OnStopBits(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnParity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHandshaking(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBaud(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelayBetweenCharsMs(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnVerbose(MM::PropertyBase* pProp, MM::ActionType eAct);

   void AddReference() {refCount_++;}
   void RemoveReference() {refCount_--;}
   bool OKToDelete() {return refCount_ < 1;}


   bool Initialized(void) { return initialized_;};


private:
   std::string portName_;

   bool initialized_;
   bool busy_;

   // thread locking for the port 
   MMThreadLock portLock_;

   double portTimeoutMs_;
   double answerTimeoutMs_;
   int refCount_;
   double transmitCharWaitMs_;
   std::map<std::string, int> baudList_;

   std::string stopBits_;
   std::string parity_;

   // create these guys in the order of declaration
   boost::asio::io_service* pService_;
   AsioClient* pPort_;
   // the worker thread
   boost::thread* pThread_;
   bool verbose_; // if false, turn off LogBinaryMessage even in Debug Log


};

class SerialManager
{
public:
   SerialManager() {}
   ~SerialManager();

   MM::Device* CreatePort(const char* portName);
   void DestroyPort(MM::Device* port);

private:
   std::vector<SerialPort*> ports_;
};

class SerialPortLister
{
   public:                                                                   
      // returns list of serial ports that can be opened
      static void ListPorts(std::vector<std::string> &availablePorts);
      static bool portAccessible(const char*  portName);                     
};

#endif //_SERIALMANAGER_H_
