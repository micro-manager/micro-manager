///////////////////////////////////////////////////////////////////////////////
// FILE:          SignalGenerator.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Demo Streaming Camera library
//-----------------------------------------------------------------------------
// DESCRIPTION:   Demonstration of the real-time processing module, with interface
//                to other state device. It sends out digital word proportional to
//                the value of the central image pixel
//
//
// COPYRIGHT:     University of California, San Francisco, 2007
//
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
//
#include "DemoRGBCamera.h"
#define _USE_MATH_DEFINES
#include <math.h>

extern const char* g_SignalGeneratorName;


RGBSignalGenerator::RGBSignalGenerator() : 
   initialized_(false), pStateDevice_(0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   
   /* Generate a new random seed from system time - do this once in your constructor */
   srand((unsigned int)GetClockTicksUs());
}

RGBSignalGenerator::~RGBSignalGenerator()
{
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void RGBSignalGenerator::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_SignalGeneratorName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool RGBSignalGenerator::Busy()
{
   return false;
}

/**
 * Intializes the module.
 * Required by the MM::Device API.
 */
int RGBSignalGenerator::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_SignalGeneratorName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Demo signal generator", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // stdDev
   CPropertyAction *pAct = new CPropertyAction (this, &RGBSignalGenerator::OnOutputDevice);
   nRet = CreateProperty("OutputDevice", "LPT1", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 */
int RGBSignalGenerator::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int RGBSignalGenerator::Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth)
{
   // obtain output device
   if (pStateDevice_ == 0)
   {
      if (outputDevice_.empty())
         return DEVICE_OK; // skip processing

      pStateDevice_ = GetCoreCallback()->GetStateDevice(this, outputDevice_.c_str());
      if (pStateDevice_ == 0)
         return ERR_DEVICE_NOT_AVAILABLE;
   }

   // pixel value in the image center
   unsigned char* pVal = buffer + height/2 * width + width/2;
   unsigned int val(0);
   if (byteDepth == 1)
      val = *pVal;
   else if (byteDepth == 2)
      val = *reinterpret_cast<unsigned short*>(pVal) / 256;
   else if (byteDepth == 4)
      val = ((*reinterpret_cast<unsigned int*>(pVal))<<16)&0x00ffffff;
   else
      return ERR_UNSUPPORTED_IMAGE_TYPE;

   // output the signal
   pStateDevice_->SetPosition((long)val);

   return DEVICE_OK;
}

int RGBSignalGenerator::OnOutputDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(outputDevice_);
      pStateDevice_ = 0;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(outputDevice_.c_str());
   }

   return DEVICE_OK; 
}

