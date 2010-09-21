///////////////////////////////////////////////////////////////////////////////
// FILE:          FreeSerialPort.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial Port Device Adapter for communication with a serial
//                port connected to a device without a specialized device adapter
//
// COPYRIGHT:     University of California San Francisco, 2010
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
// AUTHOR:        Karl Hoover
//
// CVS:           $Id: FreeSerialPort.h 4054 2010-02-25 21:59:20Z karlh $
//

#ifndef _FREESERIALPORT_H_
#define _FREESERIALPORT_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_BLAH BLAH

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice interface
//

class FreeSerialPort : public CGenericBase<FreeSerialPort>  
{
public:
   FreeSerialPort();
   ~FreeSerialPort();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   
   // action interface
   // ----------------
   // serial port device - this is a pre-initialization setting
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

   // serial port settings - this adapter is unique in that it allows these to be changed on-the-fly
   int OnCommunicationSetting(MM::PropertyBase* pProp, MM::ActionType eAct, long);

   // i/o buffers
   int OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCommandTerminator(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnResponse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnResponseTerminator(MM::PropertyBase* pProp, MM::ActionType eAct);

   // reflect the pre-initialization property so user can see it
   int OnShowPort(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   FreeSerialPort& operator=(FreeSerialPort& /*rhs*/) {assert(false); return *this;}

   std::vector < std::pair<std::string, std::string>  > communicationSettings_;

   std::string port_;
   std::string command_; // string to send
   std::string commandTerminator_; // terminator to append to to sent string
   std::string response_;
   std::string responseTerminator_; // response terminator

   bool initialized_;
   bool busy_;
   bool detailedLog_;

   std::string TokenizeControlCharacters(const std::string ) const;
   std::string DetokenizeControlCharacters(const std::string ) const;

};


#endif //_FREESERIALPORT_H_
