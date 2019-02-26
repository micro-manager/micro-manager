///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissMonitor.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN29 bus controller, see Zeiss CAN29 bus documentation
//                
// AUTHOR: Nico Stuurman, 6/18/2007 - 
//
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (permission re-granted on 7/3/2008, 7/1/2009//                after changes to the code).
//                If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications. 
//                
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



#include "ZeissAxioZoom.h"

/*
 * Utility class for ZeissMonitoringThread
 * Takes an input stream and returns CAN29 messages in the GetNextMessage method
 */
ZeissMessageParser::ZeissMessageParser(unsigned char* inputStream, long inputStreamLength) :
   index_(0)
{
   inputStream_ = inputStream;
   inputStreamLength_ = inputStreamLength;
}

/*
 * Find a message starting with 0x10 0x02 and ends with 0x10 0x03.  
 * Strips escaped 0x10 and 0x0D chars (which are escaped with 0x10)
 */
int ZeissMessageParser::GetNextMessage(unsigned char* nextMessage, int& nextMessageLength) {
   bool startFound = false;
   bool endFound = false;
   bool tenFound = false;
   nextMessageLength = 0;
   long remainder = index_;
   while ( (endFound == false) && (index_ < inputStreamLength_) && (nextMessageLength < messageMaxLength_) ) {
      if (tenFound && (inputStream_[index_] == 0x02) ) {
         startFound = true;
         tenFound = false;
      }
      else if (tenFound && (inputStream_[index_] == 0x03) ) {
         endFound = true;
         tenFound = false;
      }
      else if (tenFound && (inputStream_[index_] == 0x10) ) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
         tenFound = false;
      }
      else if (tenFound && (inputStream_[index_] == 0x0D) ) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
         tenFound = false;
      }
      else if (inputStream_[index_] == 0x10)
         tenFound = true;
      else if (startFound) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
         if (tenFound)
            tenFound = false;
      }
      index_++;
   }
   if (endFound)
      return 0;
   else {
      // no more complete message found, return the whole stretch we were considering:
      for (long i = remainder; i < inputStreamLength_; i++)
         nextMessage[i-remainder] = inputStream_[i];
      nextMessageLength = inputStreamLength_ - remainder;
      return -1;
   }
}

/*
 * Thread that continuously monitors messages from the Zeiss scope and inserts them into a model of the microscope
 */
ZeissMonitoringThread::ZeissMonitoringThread(MM::Device& device, MM::Core& core, ZeissHub& hub, ZeissDeviceInfo* deviceInfo, bool debug) :
   device_ (device),
   core_ (core),
   hub_ (hub),
   debug_ (debug),
   stop_ (true),
   intervalUs_(10000) // check every 10 ms for new messages, 
{
   deviceInfo = deviceInfo_;
}

ZeissMonitoringThread::~ZeissMonitoringThread()
{
   Stop();
   wait();
   core_.LogMessage(&device_, "Destructing MonitoringThread", true);
}



/**
 * Interpret messages for AxioZoom 
 */
void ZeissMonitoringThread::interpretMessageAZ(unsigned char* message)
{
   const int TARGET = 0;
   const int SOURCE = 1;
   const int DATABYTES = 2;
   const int CLASS = 3;
   const int NUMBER = 4;
   const int PROCID = 5;// data1
   const int SUBID = 6; // data2

   if (message[SOURCE] == MOTOR_FOCUS)
   {
      const int DATA3 = 7;
      const int DATA4 = 8;
      const int DATA5 = 9;
      const int DATA6 = 10;

      if (message[CLASS] == 0x07) // EVENT
      {
         if (message[NUMBER] == 0xB0 && message[SUBID] == 0x01)
         {
            // STATE, data3 contains state byte
            hub_.motorFocusModel_.SetState(message[DATA3]);
         }
         else if (message[NUMBER] == 0xB0 && message[SUBID] == 0x06)
         {
            // POSITION in um
            assert(message[DATABYTES] == 6);
            ZeissLong position = 0;
            memcpy(&position, message + DATA3, 4);
            position = ntohl(position);
            hub_.motorFocusModel_.SetPosition(position);
         }
      }
      else if (message[CLASS] == 0x08) // DIRECT ANSWER
      {
         if (message[NUMBER] == 0xB0 && message[SUBID] == 0x06)
         {
            // GET POSITION in um
            assert(message[DATABYTES] == 6);
            ZeissLong position = 0;
            memcpy(&position, message + DATA3, 4);
            position = ntohl(position);
            hub_.motorFocusModel_.SetPosition(position);
            hub_.motorFocusModel_.setWaiting(false);
         }
      }
   }

   else if (message[SOURCE] == STAGEX || message[SOURCE] == STAGEY)
   {
      const int DEV = 7; // data 3
      const int DATA4 = 8;
      const int DATA5 = 9;
      const int DATA6 = 10;
      const int DATA7 = 11;

      bool stageX = message[SOURCE] == STAGEX ? true : false;

      if (message[CLASS] == 0x07) // EVENT
      {
         if (message[NUMBER] == 0x5F && message[SUBID] == 0x01)
         {
            assert(message[DATABYTES] == 7);
            ZeissULong state = 0;
            memcpy(&state, message + DATA4, 4);
            state = ntohl(state);
            if (stageX)
               hub_.stageModelX_.SetState(state);
            else
               hub_.stageModelY_.SetState(state);
         }
         else if (message[NUMBER] == 0x5F && message[SUBID] == 0x02)
         {
            // POSITION in um
            assert(message[DATABYTES] == 7);
            ZeissLong position = 0;
            memcpy(&position, message + DATA4, 4);
            position = ntohl(position);
            if (stageX)
               hub_.stageModelX_.SetPosition(position);
            else
               hub_.stageModelY_.SetPosition(position);
         }
      }
      else if (message[CLASS] == 0x08) // DIRECT ANSWER
      {
         if (message[NUMBER] == 0x5F && message[SUBID] == 0x02)
         {
            // GET POSITION in um
            assert(message[DATABYTES] == 7);
            ZeissLong position = 0;
            memcpy(&position, message + DATA4, 4);
            position = ntohl(position);
            if (stageX)
            {
               hub_.stageModelX_.SetPosition(position);
               hub_.stageModelX_.setWaiting(false);
            }
            else
            {
               hub_.stageModelY_.SetPosition(position);
               hub_.stageModelY_.setWaiting(false);
           }
         }
      }
   }
   // OPTICS
   else if (message[SOURCE] == OPTICS)
   {
      const int DATA3 = 7;
      const int DATA4 = 8;
      const int DATA5 = 9;
      const int DATA6 = 10;

      if (message[CLASS] == 0x07) // EVENT
      {
         if (message[NUMBER] == 0xA0 && message[SUBID] == 0x01)
         {
            // STATE, data3 contains state byte
            hub_.opticsUnitModel_.SetState(message[DATA3]);
         }
         else if (message[NUMBER] == 0xA0 && message[SUBID] == 0x06)
         {
            // zoom (x1000)
            assert(message[DATABYTES] == 6);
            ZeissUShort level = 0;
            memcpy(&level, message + DATA3, 2);
            level = ntohs(level);
            hub_.opticsUnitModel_.SetZoomLevel(level);
         }
         else if (message[NUMBER] == 0xA0 && message[SUBID] == 0x21)
         {
            // aperture
            assert(message[DATABYTES] == 3);
            hub_.opticsUnitModel_.SetAperture(message[DATA3]);
         }
      }
      else if (message[CLASS] == 0x08) // DIRECT ANSWER
      {
         if (message[NUMBER] == 0xA0 && message[SUBID] == 0x06)
         {
            // get zoom
            assert(message[DATABYTES] == 6);
            ZeissUShort level = 0;
            memcpy(&level, message + DATA3, 2);
            level = ntohs(level);
            hub_.opticsUnitModel_.SetZoomLevel(level);
            hub_.opticsUnitModel_.setWaiting(false);
         }
         else if (message[NUMBER] == 0xA0 && message[SUBID] == 0x21)
         {
            // aperture
            assert(message[DATABYTES] == 3);
            hub_.opticsUnitModel_.SetAperture(message[DATA3]);
            hub_.opticsUnitModel_.setWaiting(false);
         }
      }
   }

   // FLUO_TUBE
   else if (message[SOURCE] == FLUO_TUBE)
   {
      const int DEV = 7;
      const int DATA4 = 8;
      const int DATA5 = 9;
      const int DATA6 = 10;

      if (message[CLASS] == 0x07) // EVENT
      {
         if (message[NUMBER] == 0x50 && message[SUBID] == 0x02 && message[DEV] == 0x02)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.fluoTubeModel_.SetPosition(pos);
         }
         if (message[NUMBER] == 0x50 && message[SUBID] == 0x02 && message[DEV] == 0x01)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.fluoTubeModel_.SetShutterPosition(pos);
         }
      }
      else if (message[CLASS] == 0x08) // DIRECT ANSWER
      {
         if (message[NUMBER] == 0x50 && message[SUBID] == 0x02 && message[DEV] == 0x02)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.fluoTubeModel_.SetPosition(pos);
            hub_.fluoTubeModel_.setWaiting(false);
         }
         else if (message[NUMBER] == 0x50 && message[SUBID] == 0x01 && message[DEV] == 0x02)
         {
            ZeissUShort stat = 0;
            memcpy(&stat, message + DATA4, 2);
            stat = ntohs(stat);
            hub_.fluoTubeModel_.SetState(stat);
            hub_.fluoTubeModel_.setWaiting(false);
         }
         else if (message[NUMBER] == 0x50 && message[SUBID] == 0x02 && message[DEV] == 0x01)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.fluoTubeModel_.SetShutterPosition(pos);
            hub_.fluoTubeModel_.setWaiting(false);
         }
         else if (message[NUMBER] == 0x50 && message[SUBID] == 0x01 && message[DEV] == 0x01)
         {
            ZeissUShort stat = 0;
            memcpy(&stat, message + DATA4, 2);
            stat = ntohs(stat);
            hub_.fluoTubeModel_.SetShutterState(stat);
            hub_.fluoTubeModel_.setWaiting(false);
         }
      }
   }

   // DL450
   else if (message[SOURCE] == DL450)
   {
      const int DEV = 7;
      const int DATA4 = 8;
      const int DATA5 = 9;
      const int DATA6 = 10;

      if (message[CLASS] == 0x07 && message[DEV] == 0x07) // EVENT
      {
         if (message[NUMBER] == 0x51 && message[SUBID] == 0x02)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.dl450Model_.SetPosition(pos);
         }
         else if (message[NUMBER] == 0x51 && message[SUBID] == 0x01)
         {
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.dl450Model_.SetState(pos);
         }
     }
      else if (message[CLASS] == 0x08) // DIRECT ANSWER
      {
         if (message[NUMBER] == 0x51 && message[SUBID] == 0x02)
         {
             // 
            ZeissUShort pos = 0;
            memcpy(&pos, message + DATA4, 2);
            pos = ntohs(pos);
            hub_.dl450Model_.SetPosition(pos);
            hub_.dl450Model_.setWaiting(false);
         }
         else if (message[NUMBER] == 0x51 && message[SUBID] == 0x01)
         {
            ZeissUShort stat = 0;
            memcpy(&stat, message + DATA4, 2);
            stat = ntohs(stat);
            hub_.dl450Model_.SetState(stat);
            hub_.dl450Model_.setWaiting(false);
         }
      }
   }

}

int ZeissMonitoringThread::svc() {

   core_.LogMessage(&device_, "Starting MonitoringThread", true);

   unsigned long dataLength;
   unsigned long charsRead = 0;
   unsigned long charsRemaining = 0;
   unsigned char rcvBuf[ZeissHub::RCV_BUF_LENGTH];
   memset(rcvBuf, 0, ZeissHub::RCV_BUF_LENGTH);

   while (!stop_) 
   {
      do { 
         if (charsRemaining > (ZeissHub::RCV_BUF_LENGTH -  ZeissMessageParser::messageMaxLength_) ) {
            // for one reason or another, our buffer is overflowing.  Empty it out before we crash
            charsRemaining = 0;
         }
         dataLength = ZeissHub::RCV_BUF_LENGTH - charsRemaining;

         // Do the scope monitoring stuff here
         // MM::MMTime _start = core_.GetCurrentMMTime();

         int ret = core_.ReadFromSerial(&device_, hub_.port_.c_str(), rcvBuf + charsRemaining, dataLength, charsRead); 

         // MM::MMTime _end = core_.GetCurrentMMTime();
         // std::ostringstream os;
         // MM::MMTime t = _end-_start;
         // os << "ReadFromSerial took: " << t.sec_ << " seconds and " << t.uSec_ / 1000.0 << " msec";
         // core_.LogMessage(&device_, os.str().c_str(), true);

         if (ret != DEVICE_OK) {
            std::ostringstream oss;
            oss << "Monitoring Thread: ERROR while reading from serial port, error code: " << ret;
            core_.LogMessage(&device_, oss.str().c_str(), false);
         } else if (charsRead > 0) {
            ZeissMessageParser parser(rcvBuf, charsRead + charsRemaining);
            do {
               unsigned char message[ZeissHub::RCV_BUF_LENGTH];
               int messageLength;
               ret = parser.GetNextMessage(message, messageLength);
               if (ret == 0) {                  
                  // Report 
                  if (debug_) {
                     std::ostringstream os;
                     os << "Monitoring Thread incoming message: ";
                     for (int i=0; i< messageLength; i++) {
                        os << std::hex << (unsigned int)message[i] << " ";
                     }
                     core_.LogMessage(&device_, os.str().c_str(), true);
                  }
                  // and do the real stuff
                  if (hub_.GetAxioType() == EMS3)
                     interpretMessageAZ(message);
                  else
                     assert(!"Works only for AxioZoom");
                     
                }
               else {
                  // no more messages, copy remaining (if any) back to beginning of buffer
                  if (debug_ && messageLength > 0) {
                     std::ostringstream os;
                     os << "Monitoring Thread no message found!: ";
                     for (int i = 0; i < messageLength; i++) {
                        os << std::hex << (unsigned int)message[i] << " ";
                        rcvBuf[i] = message[i];
                     }
                     core_.LogMessage(&device_, os.str().c_str(), true);
                  }
                  memset(rcvBuf, 0, ZeissHub::RCV_BUF_LENGTH);
                  for (int i = 0; i < messageLength; i++) {
                     rcvBuf[i] = message[i];
                  }
                  charsRemaining = messageLength;
               }
            } while (ret == 0);
         }
      } while ((charsRead != 0) && (!stop_)); 
          CDeviceUtils::SleepMs(intervalUs_/1000);
   }
   core_.LogMessage(&device_, "Monitoring Thread finished", true);
   return 0;
}

void ZeissMonitoringThread::Start()
{

   stop_ = false;
   activate();
}
