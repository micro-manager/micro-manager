///////////////////////////////////////////////////////////////////////////////
// FILE:          ImageProcessorChain.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Runs a chain of other ImageProcessors on each image 
//                
// AUTHOR:        Karl Hoover
//
// COPYRIGHT:     University of California, San Francisco, 2011
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
// CVS:           $Id: ImageProcessorChain.cpp 6586 2011-02-23 00:06:40Z karlh $
//

#include "ImageProcessorChain.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <algorithm>




#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName("ImageProcessorChain", "ImageProcessorChain");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;


   else if(strcmp(deviceName, "ImageProcessorChain") == 0)
   {

      return new ImageProcessorChain();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


int ImageProcessorChain::Initialize()
{


   std::vector<std::string> availableProcessors;
   availableProcessors.clear();
   availableProcessors.push_back("");
   char deviceName[MM::MaxStrLength];
   unsigned int deviceIterator = 0;
   for(;;)
   {
      GetLoadedDeviceOfType(MM::ImageProcessorDevice, deviceName, deviceIterator++);
      if( 0 < strlen(deviceName))
      {
         // let's not recursively chain the image processors....
         if( 0 != std::string(deviceName).compare(std::string("ImageProcessorChain")))
            availableProcessors.push_back(std::string(deviceName));
      }
      else
         break;
   }


   CPropertyActionEx* pAct = NULL;
   
   for( int ip = 0; ip < nSlots_; ++ip)
   {
      std::ostringstream processorSlotName;
      processorSlotName << "ProcessorSlot" << ip;
      pAct = new CPropertyActionEx (this, &ImageProcessorChain::OnProcessor, ip);
      (void)CreateProperty(processorSlotName.str().c_str(), "", MM::String, false, pAct); 
      for (std::vector<std::string>::iterator iap = availableProcessors.begin();  iap != availableProcessors.end(); ++iap)
         AddAllowedValue(processorSlotName.str().c_str(), iap->c_str());

   }

   return DEVICE_OK;
}

   // action interface
   // ----------------
int ImageProcessorChain::OnProcessor(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
{
   if (eAct == MM::BeforeGet)
   {
      std::string name;
      if (processorNames_.end() != processorNames_.find((int)indexx))
         name = processorNames_[(int)indexx];
      pProp->Set(name.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string name;
      pProp->Get(name);
      processorNames_[indexx] = name;

      for( int islot = 0; islot < this->nSlots_; ++islot)
      {
         processors_[islot] = NULL;
         if( processorNames_.end() != processorNames_.find(islot))
            if ( 0 < processorNames_[islot].length())
            {
               MM::Device* pDevice = GetDevice(processorNames_[islot].c_str());
               if( NULL != pDevice)
                  if( MM::ImageProcessorDevice == pDevice->GetType())
                     processors_[islot] = (MM::ImageProcessor*) pDevice;
            }
      }
      
   }

   return DEVICE_OK;
}


int ImageProcessorChain::Process(unsigned char *pBuffer, unsigned int width, unsigned int height, unsigned int byteDepth)
{
   int ret = DEVICE_OK;
   busy_ = true;


   for( int islot = 0; islot < this->nSlots_; ++islot)
   {
      if( processors_.end() != processors_.find(islot))
      {

         MM::ImageProcessor* pP = processors_[islot];
         if( NULL != pP)
         {
            try
            {
               pP->Process(pBuffer, width, height,byteDepth);
            }
            catch(...)
            {
               std::ostringstream m;
               char name[MM::MaxStrLength];
               pP->GetName(name);
               m << "Error in processor " << name;
               LogMessage(m.str().c_str(), false);
            }
         }
      }
   }

   busy_ = false;

   return ret;
}