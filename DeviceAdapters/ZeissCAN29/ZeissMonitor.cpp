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



#include "ZeissCAN29.h"

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

void ZeissMonitoringThread::interpretMessage(unsigned char* message)
{
   //if (!(message[5] == 0)) // only message with Proc Id 0 are meant for us
   //  In reality I see here 0, 6, and 7???
   //   return;
   if (message[1] == AXIOOBSERVER || message[1] == AXIOIMAGER) {
      if (message[3] == 0x07) { // events/unsolicited message
         if (message[6] == 0x01) // leaving settled position
         {
            hub_.SetModelBusy(message[7], true);
         }
         else if (message[6] == 0x02) { // actual moving position 
            if (message[4] == 0xA3) { // axis device (position is LONG)
               ZeissLong position = 0;
               memcpy(&position, message + 8, 4);
               position = ntohl(position);
               hub_.SetModelPosition(message[7], position);
            } else {
               ZeissShort position = 0;
               memcpy(&position, message + 8, 2);
               position = ntohs((unsigned short) position);
               // Changers and Shutters will report 0 when they are in transit
               if (position != 0)
                  hub_.SetModelPosition(message[7], position);
            }
            hub_.SetModelBusy(message[7], true);
         }
         else if (message[6] == 0x03) { // target position settled
            if (message[4] == 0xA3) { // axis device (position is LONG)
               ZeissLong position = 0;
               memcpy(&position, message + 8, 4);
               position = ntohl(position);
               hub_.SetModelPosition(message[7], position);
            } else {
               ZeissShort position = 0;
               memcpy(&position, message + 8, 2);
               position = ntohs((unsigned short) position);
               hub_.SetModelPosition(message[7], position);
               // TODO: remove after debugging
               if (debug_) {
                  std::ostringstream os;
                  os << "Monitoring Thread setting device " << std::hex << (int) message[7] << " to position: " << (long) position;
                  core_.LogMessage(&device_, os.str().c_str(), true);
               }
            }
            hub_.SetModelBusy(message[7], false);
         }
         else if (message[6] == 0x04) { // status changed
            if (hub_.GetAxioType() == AXIOOBSERVER) { // status is a short in Imager and documentation is unclear, so only use on Observer
               ZeissULong status = 0;
               memcpy(&status, message + 8, 4);
               status = ntohl(status);
               hub_.SetModelStatus(message[7], status);
               hub_.SetModelBusy(message[7], !(status & 32)); // 'is settled' bit 
               // TODO: remove after debugging
               if (debug_) {
                  std::ostringstream os;
                  os << "Busy flag of device " << std::hex << (int) message[7] << " changed to ";
                  if (!(status & 32))
                     os << " true";
                  else
                     os << "false";
                  core_.LogMessage(&device_, os.str().c_str(), true);
               }
            }
         } else if (message[6] == 0x2B) { // Axis:: Trajectory Velocity
            ZeissLong velocity = 0;
            memcpy(&velocity, message + 8, 4);
            velocity = ntohl(velocity);
            hub_.SetTrajectoryVelocity(message[5], velocity);
            hub_.SetModelBusy(message[5], false);
         } else if (message[6] == 0x2C) { // Axis:: Trajectory Acceleration
            ZeissLong accel = 0;
            memcpy(&accel, message + 8, 4);
            accel = ntohl(accel);
            hub_.SetTrajectoryAcceleration(message[5], accel);
            hub_.SetModelBusy(message[5], false);
         }
      } else if (message[3] == 0x08) { // Some direct answers that we want to interpret
         if (message[6] == 0x20) { // Axis: Upper hardware stop reached
            hub_.SetModelBusy(message[5], false);
            ZeissLong position = 0;
            memcpy(&position, message + 8, 4);
            position = ntohl(position);
            hub_.SetUpperHardwareStop(message[5], position);
            // TODO: How to unlock the stage from here?
         } else if (message[6] == 0x21) { // Axis:: Lower hardware stop reached
            hub_.SetModelBusy(message[5], false);
            ZeissLong position = 0;
            memcpy(&position, message + 8, 4);
            position = ntohl(position);
            hub_.SetLowerHardwareStop(message[5], position);
            // TODO: How to unlock the stage from here?
         } else if (message[6] == 0x2B) { // Axis:: Trajectory Velocity
            hub_.SetModelBusy(message[5], false);
            ZeissLong velocity = 0;
            memcpy(&velocity, message + 8, 4);
            velocity = ntohl(velocity);
            hub_.SetTrajectoryVelocity(message[5], velocity);
         } else if (message[6] == 0x2C) { // Axis:: Trajectory Acceleration
            hub_.SetModelBusy(message[5], false);
            ZeissLong accel = 0;
            memcpy(&accel, message + 8, 4);
            accel = ntohl(accel);
            hub_.SetTrajectoryAcceleration(message[5], accel);
          } else if (message[6] == 0x07) { // Status updated as we requested (only on AxioImager)
             ZeissShort status = 0;
             memcpy(&status, message + 8, 2);
             status = ntohs((unsigned short) status);
             if ( (status & 1024) > 0)
                hub_.SetModelBusy(message[7], true);
             else
                hub_.SetModelBusy(message[7], false);
         }
      }
   } else if (message[1] == DEFINITEFOCUS && message[7] == 0) {
      if (message[3] == 0x07 || message[3] == 0x08 || message[3] == 0x09) {  // events and direct answers
         switch (message[6]) {
            case 0x01:
               hub_.definiteFocusModel_.SetControlOnOff(message[8]);
               hub_.definiteFocusModel_.SetBusy(false);
               break;
            case 0x02: {// current stabilizing position
               hub_.definiteFocusModel_.SetData(message[8], &message[9]);
               ZeissLong position = 0;
               hub_.GetModelPosition(device_, core_, 0x0F, position);
               hub_.definiteFocusModel_.SetWorkingPosition(position * 0.001);
               hub_.definiteFocusModel_.SetBusy(false);
               hub_.definiteFocusModel_.SetWaitForStabilizationData(false);
               break;
                       }
            case 0x03: // do stabilize position
               hub_.definiteFocusModel_.SetBusy(false);
               break;
            case 0x10: {// Status
               ZeissUShort status = 0;
               ZeissUShort error = 0;
               memcpy(&status, message + 8, 2);
               status = ntohs((ZeissUShort) status);
               hub_.definiteFocusModel_.SetStatus(status);
               memcpy(&error, message + 10, 2);
               error = ntohs((ZeissUShort) error);
               hub_.definiteFocusModel_.SetError(error);
               if (error != 0) {
                  std::ostringstream os;
                  os << "Monitoring Thread: Error reported by Definite Focus: " << std::hex << error;
                  core_.LogMessage(&device_, os.str().c_str(), false);
               }
               break;
                       }
            case 0x0B: // original stabilizing position (treat this differently???)
               hub_.definiteFocusModel_.SetData(message[8], &message[9]);
               hub_.definiteFocusModel_.SetBusy(false);
               break;
            case 0x13: {// Illumination Features
               hub_.definiteFocusModel_.SetBrightnessLED(message[8]);
               ZeissShort shutterFactor = 0;
               memcpy (&shutterFactor, message + 9, 2);
               shutterFactor = ntohs (shutterFactor);
               hub_.definiteFocusModel_.SetShutterFactor(shutterFactor);
               hub_.definiteFocusModel_.SetBusy(false);
               break;
                       }
            case 0x04:
            case 0x84:
               hub_.definiteFocusModel_.SetPeriod(message[8]);
               hub_.definiteFocusModel_.SetBusy(false);
               break;
         }
      }
   } else if (message[1] == COLIBRI) {
      if (message[3] == 0x07 || message[3] == 0x08 || message[3] == 0x09) {  // events and direct answers
         switch (message[6]) {
            case 0x01: {// Brightness
               ZeissShort brightness = 0;
               memcpy(&brightness, message + 8, 2);
               brightness = ntohs((ZeissShort) brightness);
               hub_.colibriModel_.SetBrightness(message[7] - 1, brightness);
               // update LED on/off state depending on general shutter status
               if (brightness == 0) {
                  if (hub_.colibriModel_.GetOnOff(message[7] - 1) == 2) {
                     hub_.ColibriOnOff(device_, core_, message[7] - 1, false);
                  }
               } else {
                  if (hub_.colibriModel_.GetOpen() && (hub_.colibriModel_.GetOnOff(message[7] - 1) < 2)) {
                     hub_.ColibriOnOff(device_, core_, message[7] - 1, true);
                  }
               }
               hub_.colibriModel_.SetBusy(message[7] - 1, false);
               break;
                       }
            case 0x04: // LED on/off
               hub_.colibriModel_.SetOnOff(message[7] - 1, message[8]);
               hub_.colibriModel_.SetBusy(message[7] - 1, false);
               break;
            case 0x40: // Operation Mode
               if (message[7]==0) {
                  hub_.colibriModel_.SetMode(message[8]);
               }
               break;
            case 0x70: // External Shutter
               if (message[8]>0) {
                  hub_.colibriModel_.SetExternalShutterState(message[8]);
                  hub_.colibriModel_.SetBusyExternal(false);
               }
               break;
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
                  interpretMessage(message);
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
