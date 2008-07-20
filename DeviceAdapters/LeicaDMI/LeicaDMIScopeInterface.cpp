///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIScopeInterace.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
//
// COPYRIGHT:     100xImaging, Inc. 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  



#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#else
#include <netinet/in.h>
#endif

#include "LeicaDMIScopeInterface.h"
#include "LeicaDMIModel.h"
#include "LeicaDMI.h"
#include "LeicaDMICodes.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>


//////////////////////////////////////////
// Interface to the Leica microscope
//
LeicaScopeInterface::LeicaScopeInterface() :
   portInitialized_ (false),
   monitoringThread_(0),
   timeOutTime_(250000),
   initialized_ (false)
{
}

LeicaScopeInterface::~LeicaScopeInterface()
{
}

/**
 * Clears the serial receive buffer.
 */
void LeicaScopeInterface::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/*
 * Reads version number, available devices, device properties and some labels from the microscope and then starts a thread that keeps on reading data from the scope.  Data are stored in the array deviceInfo from which they can be retrieved by the device adapters
 */
int LeicaScopeInterface::Initialize(MM::Device& device, MM::Core& core)
{
   if (!portInitialized_)
      return ERR_PORT_NOT_OPEN;
   
   std::ostringstream os;
   os << "Initializing Hub";
   core.LogMessage (&device, os.str().c_str(), false);
   os.str("");
   // empty the Rx serial buffer before sending commands
   ClearRcvBuf();
   ClearPort(device, core);

   std::string version;
   // Get info about stand, firmware and available devices and store in the model
   int ret = GetStandInfo(device, core);
   if (ret != DEVICE_OK) 
      return ret;

   // TODO: get all necessary information from the stand and store in the model

   monitoringThread_ = new LeicaMonitoringThread(device, core, port_);
   monitoringThread_->Start();
   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Reads model, version and available devices from the stand
 * Stores directly into the model
 */
int LeicaScopeInterface::GetStandInfo(MM::Device& device, MM::Core& core)
{
   std::ostringstream os;
   // returns the stand deisgnation and list of IDs of all addressabel IDs
   os << g_Master << "001";
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // Version string starts in 6th character, length is in first char
   long unsigned int responseLength = RCV_BUF_LENGTH;
   char response[RCV_BUF_LENGTH] = "";
   unsigned long signatureLength = 4;
   ret = core.GetSerialAnswer(&device, port_.c_str(), responseLength, response, "\r");
   if (ret != DEVICE_OK)
      return ret;
   // TODO: parse repsonse and set device type and available devices in model
  
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


int LeicaScopeInterface::ClearPort(MM::Device& device, MM::Core& core)
{
   // Clear contents of serial port 
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
} 


/*
 * Utility class for LeicaMonitoringThread
 */
LeicaMessageParser::LeicaMessageParser(unsigned char* inputStream, long inputStreamLength) :
   index_(0)
{
   inputStream_ = inputStream;
   inputStreamLength_ = inputStreamLength;
}

int LeicaMessageParser::GetNextMessage(unsigned char* nextMessage, int& nextMessageLength) {
   bool startFound = false;
   bool endFound = false;
   bool tenFound = false;
   nextMessageLength = 0;
   long remainder = index_;
   while ( (endFound == false) && (index_ < inputStreamLength_) && (nextMessageLength < messageMaxLength_) ) {
      if (tenFound && inputStream_[index_] == 0x02) {
         startFound = true;
         tenFound = false;
      }
      else if (tenFound && inputStream_[index_] == 0x03) {
         endFound = true;
         tenFound = false;
      }
      else if (tenFound && inputStream_[index_] == 0x10) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
         tenFound = false;
      }
      else if (inputStream_[index_] == 0x10)
         tenFound = true;
      else if (startFound) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
      }
      index_++;
   }
   if (endFound)
      return 0;
   else {
      // no more complete message found, return the whole stretch we were considering:
      for (long i= remainder; i < inputStreamLength_; i++)
         nextMessage[i-remainder] = inputStream_[i];
      nextMessageLength = inputStreamLength_ - remainder;
      return -1;
   }
}

/*
 * Thread that continuously monitors messages from the Leica scope and inserts them into a model of the microscope
 */
LeicaMonitoringThread::LeicaMonitoringThread(MM::Device& device, MM::Core& core, std::string port) :
   port_(port),
   device_ (device),
   core_ (core),
   stop_ (true),
   intervalUs_(5000) // check every 5 ms for new messages, 
{
}

LeicaMonitoringThread::~LeicaMonitoringThread()
{
   printf("Destructing monitoringThread\n");
}

void LeicaMonitoringThread::interpretMessage(unsigned char* message)
{

}

//MM_THREAD_FUNC_DECL LeicaMonitoringThread::svc(void *arg) {
int LeicaMonitoringThread::svc() {
   //LeicaMonitoringThread* thd = (LeicaMonitoringThread*) arg;

   printf ("Starting MonitoringThread\n");

   unsigned long dataLength;
   unsigned long charsRead = 0;
   unsigned long charsRemaining = 0;
   unsigned char rcvBuf[LeicaScopeInterface::RCV_BUF_LENGTH];
   memset(rcvBuf, 0, LeicaScopeInterface::RCV_BUF_LENGTH);

   while (!stop_) 
   {
      do { 
         dataLength = LeicaScopeInterface::RCV_BUF_LENGTH - charsRemaining;
         // Do the scope monitoring stuff here
         int ret = core_.ReadFromSerial(&(device_), port_.c_str(), rcvBuf + charsRemaining, dataLength, charsRead); 
         if (ret != DEVICE_OK) {
            std::ostringstream oss;
            oss << "Monitoring Thread: ERROR while reading from serial port, error code: " << ret;
            core_.LogMessage(&(device_), oss.str().c_str(), false);
         } else if (charsRead > 0) {
            LeicaMessageParser* parser = new LeicaMessageParser(rcvBuf, charsRead + charsRemaining);
            do {
               unsigned char message[LeicaMessageParser::messageMaxLength_];
               int messageLength;
               ret = parser->GetNextMessage(message, messageLength);
               if (ret == 0) {
                  // Report 
                  std::ostringstream os;
                  os << "Monitoring Thread incoming message: ";
                  for (int i=0; i< messageLength; i++)
                     os << std::hex << (unsigned int)message[i] << " ";
                  core_.LogMessage(&(device_), os.str().c_str(), true);
                  // and do the real stuff
                  interpretMessage(message);
                }
               else {
                  // no more messages, copy remaining (if any) back to beginning of buffer
                  memset(rcvBuf, 0, LeicaScopeInterface::RCV_BUF_LENGTH);
                  for (int i = 0; i < messageLength; i++)
                     rcvBuf[i] = message[i];
                  charsRemaining = messageLength;
               }
            } while (ret == 0);
         }
      } while ((charsRead != 0) && (!stop_)); 

       CDeviceUtils::SleepMs(intervalUs_/1000);
   }
   printf("Monitoring thread finished\n");
   return 0;
}

void LeicaMonitoringThread::Start()
{
   stop_ = false;
   activate();
}
