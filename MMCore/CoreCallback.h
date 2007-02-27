///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreCallback.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback object for MMCore device interface. Encapsulates
//                (bottom) internal API for calls going from devices to the 
//                core.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/23/2006

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
// CVS:           $Id$
//

#include <ace/OS.h>
#include <ace/High_Res_Timer.h>
#include <ace/Log_Msg.h>
#ifdef __APPLE__
#include <sys/time.h>
#endif

#include "CoreUtils.h"
#include "MMCore.h"

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CoreCallback class
// ------------------

class CoreCallback : public MM::Core
{
public:
   CoreCallback(CMMCore* c) : core_(c) {}
   ~CoreCallback() {}

   /**
    * Writes a message to the Micro-Manager log file.
    */
   int LogMessage(const MM::Device* caller, const char* msg, bool debugOnly)
   {
      char label[MM::MaxStrLength];
      caller->GetLabel(label);
      if (debugOnly)
         CORE_DEBUG2("Device %s debug message: %s\n", label, msg);
      else
         CORE_LOG2("Device %s message: %s\n", label, msg);
      return DEVICE_OK;
   }

   long GetNumberOfDevices() const
   {
      assert(core_);
      return (long)core_->getLoadedDevices().size();
   }

   /**
    * Returns a direct pointer to the device with the specified name.
    */
   MM::Device* GetDevice(const MM::Device* caller, const char* label)
   {
      assert(core_);
      MM::Device* pDev = 0;

      try
      {
         pDev = core_->getDevice(label);
         if (pDev == caller)
            return 0; // prevent caller from obtaining it's own address
      }
      catch (...)
      {
         // trap all exceptions
      }

      return pDev;
   }
   
   /**
    * Sends an array of bytes to the port.
    */
   int WriteToSerial(const MM::Device* caller, const char* portName, const char* buf, unsigned long length)
   {
      MM::Serial* pSerial = 0;
      try
      {
         pSerial = core_->getSpecificDevice<MM::Serial>(portName);
      }
      catch (CMMError& err)
      {
         return err.getCode();    
      }
      catch (...)
      {
         return DEVICE_SERIAL_COMMAND_FAILED;
      }

      // don't allow self reference
      if (dynamic_cast<MM::Device*>(pSerial) == caller)
         return DEVICE_SELF_REFERENCE;

      return pSerial->Write(buf, length);
   }
   
   /**
    * Reads bytes form the port, up to the buffer length.
    */
   int ReadFromSerial(const MM::Device* caller, const char* portName, char* buf, unsigned long bufLength, unsigned long &bytesRead)
   {
      MM::Serial* pSerial = 0;
      try
      {
         pSerial = core_->getSpecificDevice<MM::Serial>(portName);
      }
      catch (CMMError& err)
      {
         return err.getCode();    
      }
      catch (...)
      {
         return DEVICE_SERIAL_COMMAND_FAILED;
      }

      // don't allow self reference
      if (dynamic_cast<MM::Device*>(pSerial) == caller)
         return DEVICE_SELF_REFERENCE;

      return pSerial->Read(buf, bufLength, bytesRead);
   }

   /**
    * Clears port buffers.
    */
   int PurgeSerial(const MM::Device* caller, const char* portName)
   {
      MM::Serial* pSerial = 0;
      try
      {
         pSerial = core_->getSpecificDevice<MM::Serial>(portName);
      }
      catch (CMMError& err)
      {
         return err.getCode();    
      }
      catch (...)
      {
         return DEVICE_SERIAL_COMMAND_FAILED;
      }

      // don't allow self reference
      if (dynamic_cast<MM::Device*>(pSerial) == caller)
         return DEVICE_SELF_REFERENCE;

      return pSerial->Purge();
   }

   /**
    * Sends an ASCII command terminated by the specified character sequence.
    */
   int SetSerialCommand(const MM::Device*, const char* portName, const char* command, const char* term)
   {
      assert(core_);
      try {
         core_->setSerialPortCommand(portName, command, term);
      }
      catch (...)
      {
         // trap all exceptions and return generic serial error
         return DEVICE_SERIAL_COMMAND_FAILED;
      }
      return DEVICE_OK;
   }
   
   /**
    * Receives an ASCII string terminated by the specified character sequence.
    * The terminator string is stripped of the answer. If the termination code is not
    * received within the com port timeout and error will be flagged.
    */
   int GetSerialAnswer(const MM::Device*, const char* portName, unsigned long ansLength, char* answerTxt, const char* term)
   {
      assert(core_);
      string answer;
      try {
         answer = core_->getSerialPortAnswer(portName, term);
         if (answer.length() >= ansLength)
            return DEVICE_SERIAL_BUFFER_OVERRUN;
      }
      catch (...)
      {
         // trap all exceptions and return generic serial error
         return DEVICE_SERIAL_COMMAND_FAILED;
      }
      strcpy(answerTxt, answer.c_str());
      return DEVICE_OK;
   }

   /**
    * Handler for the status change event from the device.
    */
   int OnStatusChanged(const MM::Device* /* caller */)
   {
      return DEVICE_OK;
   }
   
   /**
    * Handler for the operation finished event from the device.
    */
   int OnFinished(const MM::Device* /* caller */)
   {
      return DEVICE_OK;
   }

   // to work around a bug in ACE implementation on Mac:
#ifdef __APPLE__
   long GetClockTicksUs(const MM::Device* /*caller*/)
   {
      struct timeval t;
      gettimeofday(&t,NULL);
      return t.tv_sec * 1000000L + t.tv_usec;
   }
#else
   long GetClockTicksUs(const MM::Device* /*caller*/)
   {
      ACE_High_Res_Timer timer;
      ACE_Time_Value t = timer.gettimeofday();
      return t.sec() * 1000000L + t.usec();
   }
#endif

   void Sleep (const MM::Device* /*caller*/, double intervalMs)
   {
      ACE_Time_Value tv(0, (long)intervalMs * 1000);
      ACE_OS::sleep(tv);
   }


private:
   CMMCore* core_;
};

