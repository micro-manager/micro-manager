///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreCallback.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback object for MMCore device interface. Encapsulates
//                (bottom) internal API for calls going from devices to the 
//                core.
//
//                This class is essentialy an extension of the CMMCore class
//                and has full access to CMMCore private members.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/05/2007

// COPYRIGHT:     University of California, San Francisco, 2007
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
// CVS:           $Id: CoreCallback.h 2 2007-02-27 23:33:17Z nenad $
//

#include "CoreCallback.h"
#include "CircularBuffer.h"

int CoreCallback::InsertImage(const MM::Device* /*caller*/, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, MM::ImageMetadata* pMd)
{
   if (core_->cbuf_->InsertImage(buf, width, height, byteDepth, pMd))
      return DEVICE_OK;
   else
      return DEVICE_BUFFER_OVERFLOW;
}

int CoreCallback::InsertMultiChannel(const MM::Device* /*caller*/,
                              const unsigned char* buf,
                              unsigned numChannels,
                              unsigned width,
                              unsigned height,
                              unsigned byteDepth,
                              MM::ImageMetadata* pMd)
{
   if (core_->cbuf_->InsertMultiChannel(buf, numChannels, width, height, byteDepth, pMd))
      return DEVICE_OK;
   else
      return DEVICE_BUFFER_OVERFLOW;
}

void CoreCallback::SetAcqStatus(const MM::Device* /*caller*/, int /*statusCode*/)
{
   // ???
}

int CoreCallback::OpenFrame(const MM::Device* /*caller*/)
{
   return DEVICE_OK;
}

int CoreCallback::CloseFrame(const MM::Device* /*caller*/)
{
   return DEVICE_OK;
}

int CoreCallback::AcqFinished(const MM::Device* /*caller*/, int /*statusCode*/)
{
   // close the shutter if we are in auto mode
   if (core_->autoShutter_ && core_->shutter_)
   {
      core_->shutter_->SetOpen(false);
      core_->waitForDevice(core_->shutter_);
   }
   return DEVICE_OK;
}

int CoreCallback::PrepareForAcq(const MM::Device* /*caller*/)
{
   // open the shutter if we are in auto mode
   if (core_->autoShutter_ && core_->shutter_)
   {
      core_->shutter_->SetOpen(true);
      core_->waitForDevice(core_->shutter_);
   }
   return DEVICE_OK;
}

/**
 * Handler for the status change event from the device.
 */
int CoreCallback::OnStatusChanged(const MM::Device* /* caller */)
{
   return DEVICE_OK;
}

/**
 * Handler for the property change event from the device.
 */
int CoreCallback::OnPropertiesChanged(const MM::Device* /* caller */)
{
   if (core_->externalCallback_)
      core_->externalCallback_->onPropertiesChanged();

   return DEVICE_OK;
}
   
/**
 * Handler for the operation finished event from the device.
 */
int CoreCallback::OnFinished(const MM::Device* /* caller */)
{
   return DEVICE_OK;
}

/**
 * Sends an array of bytes to the port.
 */
int CoreCallback::WriteToSerial(const MM::Device* caller, const char* portName, const unsigned char* buf, unsigned long length)
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
int CoreCallback::ReadFromSerial(const MM::Device* caller, const char* portName, unsigned char* buf, unsigned long bufLength, unsigned long &bytesRead)
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
int CoreCallback::PurgeSerial(const MM::Device* caller, const char* portName)
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
int CoreCallback::SetSerialCommand(const MM::Device*, const char* portName, const char* command, const char* term)
{
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
int CoreCallback::GetSerialAnswer(const MM::Device*, const char* portName, unsigned long ansLength, char* answerTxt, const char* term)
{
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

const char* CoreCallback::GetImage()
{
   try
   {
      core_->snapImage();
      return (const char*) core_->getImage();
   }
   catch (...)
   {
      return 0;
   }
}

int CoreCallback::GetImageDimensions(int& /*width*/, int& /*height*/, int& /*depth*/)
{
   return DEVICE_OK;
}

int CoreCallback::GetFocusPosition(double& /*pos*/)
{
   return DEVICE_OK;
}

int CoreCallback::SetFocusPosition(double /*pos*/)
{
   return DEVICE_OK;
}

