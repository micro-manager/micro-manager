///////////////////////////////////////////////////////////////////////////////
// FILE:          SerialManager.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial port device adapter - UNIX version
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
// NOTE:          UNIX implementation uses libserial
//                http://libserial.sourceforge.net/mediawiki/index.php/Main_Pagel 
//                
// AUTHOR:        Nenad Amodaj, 10/21/2005, Nico Stuurman, Dec. 2005
// CVS:           $Id$
//


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

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
#define ERR_PORT_DOES_NOT_EXIST 110
#define ERR_PORT_ALREADY_OPEN 111
#define ERR_PORT_DISAPPEARED 112

class CSerial;

class SerialPortLister
{
   public:
      SerialPortLister();
      ~SerialPortLister();

      // returns the list of ports discovered in the constructor
      void ListPorts(std::vector<std::string> &availablePorts);
      // returns the list of ports discover now and stored this for future use in ListPorts
      void ListCurrentPorts(std::vector<std::string> &availablePorts);

   private:
      std::vector<std::string> storedAvailablePorts_;
      void ListSerialPorts(std::vector<std::string> &availablePorts);
};

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMStateDevice interfaces
//

class MDSerialPort : public CSerialBase<MDSerialPort>  
{
public:
   MDSerialPort(std::string portName);
   ~MDSerialPort();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   int SetCommand(const char* command, const char* term);
   int GetAnswer(char* answer, unsigned bufLength, const char* term);
   int Write(const char* buf, unsigned long bufLen);
   int Read(char* buf, unsigned long bufLen, unsigned long& charsRead);
   int Purge();
   
   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDataBits(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStopBits(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBaudRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTimeout(MM::PropertyBase* pProp, MM::ActionType eAct);

   int Open(const char* portName);
   void Close();
   void AddReference() {refCount_++;}
   void RemoveReference() {refCount_--;}
   bool OKToDelete() {return refCount_ < 1;}

private:
   std::string portName_;
   bool initialized_;
   bool busy_;
   SerialPort* port_;
   double portTimeoutMs_;
   double answerTimeoutMs_;
   int refCount_;
   double transmitCharWaitMs_;

   long int stopBits_;
   long int dataBits_;
   long int baudRate_;

   std::vector<std::string> availablePorts_;
   SerialPortLister* portLister;

   int MDSerialPort::HandleError(int errorCode);

};

class SerialManager
{
public:
   SerialManager();
   ~SerialManager();

   MM::Device* CreatePort(const char* portName);
   void DestroyPort(MM::Device* port);

private:
   std::vector<MDSerialPort*> ports_;
};

