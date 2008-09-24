///////////////////////////////////////////////////////////////////////////////
// FILE:          NoiseProcessor.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Demo Streaming Camera library
//-----------------------------------------------------------------------------
// DESCRIPTION:   Demonstration of the real-time processing module. It adds noise
//                to images acquired from the camera.
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 07/05/2007
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
// CVS:           $Id: DemoCamera.h 73 2007-04-19 00:11:35Z nenad $
//
#include "DemoStreamingCamera.h"
#define _USE_MATH_DEFINES
#include <math.h>

extern const char* g_NoiseProcessorName;
const char* g_StdDevProp = "StdDev";

double noise1(double ampl)
{
   /* Setup constants */
   const static int q = 15;
   const static double c1 = (1 << q) - 1;
   const static double c2 = ((int)(c1 / 3)) + 1;
   const static double c3 = 1.0 / c1;

   /* random number in range 0 - 1 not including 1 */
   static double random = 0.0;

   /* the white noise */
   static double noise = 0.0;

   random = ((float)rand() / (float)(RAND_MAX + 1));
   noise = (2.0 * ((random * c2) + (random * c2) + (random * c2)) - 3.0 * (c2 - 1.0)) * c3;

   return ampl * noise;
}

DemoNoiseProcessor::DemoNoiseProcessor() : 
   initialized_(false),
   stdDev_(50.0)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   
   /* Generate a new random seed from system time - do this once in your constructor */
   srand((unsigned int)GetClockTicksUs());
}

DemoNoiseProcessor::~DemoNoiseProcessor()
{
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void DemoNoiseProcessor::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_NoiseProcessorName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool DemoNoiseProcessor::Busy()
{
   return false;
}

/**
 * Intializes the module.
 * Required by the MM::Device API.
 */
int DemoNoiseProcessor::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_NoiseProcessorName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Demo real-time processor: adds noise", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // stdDev
   CPropertyAction *pAct = new CPropertyAction (this, &DemoNoiseProcessor::OnStdDev);
   nRet = CreateProperty("StdDev", "50.0", MM::Float, false, pAct);
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
int DemoNoiseProcessor::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

/**
 * Performs processing. In this demo example we will add noise to the image.
 * Required by the MM::ImageProcessor API.
 */
int DemoNoiseProcessor::Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth)
{
   if (stdDev_ <= 0.0)
      return DEVICE_OK; // no need to process

   unsigned long size = width * height;
   if (byteDepth == 1)
   {
      unsigned char* pBuf = (unsigned char*) buffer;
      for (unsigned long i=0; i<size; i++)
      {
         double val = noise1(stdDev_);
         if (val > UCHAR_MAX)
            val = UCHAR_MAX;
         if (val < 0.0)
            val = 0.0;
         pBuf[i] = pBuf[i] + (unsigned char) val; 
      }
   }
   else if (byteDepth == 2)
   {
      unsigned short* pBuf = (unsigned short*) buffer;
      for (unsigned long i=0; i<size; i++)
      {
         double val = noise1(stdDev_);
         if (val > USHRT_MAX)
            val = USHRT_MAX;
         if (val < 0.0)
            val = 0.0;
         pBuf[i] = pBuf[i] + (unsigned short) val; 
      }
   }
   else
      return ERR_UNSUPPORTED_IMAGE_TYPE;

   return DEVICE_OK;
}

int DemoNoiseProcessor::OnStdDev(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      pProp->Get(stdDev_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(stdDev_);
   }

   return DEVICE_OK; 
}

