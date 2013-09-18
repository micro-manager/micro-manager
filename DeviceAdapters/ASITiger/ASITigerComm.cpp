///////////////////////////////////////////////////////////////////////////////
// FILE:          ASITigerComm.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI TIGER comm card/controller adapter
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.cpp, ASIFW1000.cpp, Arduino.cpp, and DemoCamera.cpp
//
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "ASITiger.h"
#include "ASITigerComm.h"
#include "ASIDevice.h"
#include "ASIXYStage.h" 
#include "ASIFSlider.h"  
#include <cstdio>
#include <string>
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"
#include <iostream>
#include <assert.h>
#include <vector>

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CTigerHub implementation
//
CTigerCommHub::CTigerCommHub()
{
   CreateProperty(MM::g_Keyword_Name, g_TigerCommHubName, MM::String, true);
}

CTigerCommHub::~CTigerCommHub()
{
   // Shutdown() called in ASIDevice class which sets initialized_ to false
}

int CTigerCommHub::Initialize()
{
   // call generic Initialize first (getting hub not needed here because we are a hub already)
   RETURN_ON_MM_ERROR ( ASIDevice::Initialize(true) );

   // if we can communicate over serial and get back the controller version then we're in good shape
   // make sure we are on Tiger
   RETURN_ON_MM_ERROR ( TalkToTiger() );

   // get version information from the controller, this is just for TigerComm (hub/serial card)
   ret_ = QueryCommandVerify("V", ":A v");  // N.B. this is different for non-Tiger controllers like MS/WK-2000
   if(ret_ == ERR_UNRECOGNIZED_ANSWER)
      ret_ = DEVICE_NOT_SUPPORTED;
   RETURN_ON_MM_ERROR (ret_);
   firmwareVersion_ = hub_->ParseAnswerAfterPosition(4);
   stringstream command; command.str("");
   command << firmwareVersion_;
   RETURN_ON_MM_ERROR ( CreateProperty(g_FirmwareVersionPropertyName, command.str().c_str(), MM::Float, true) );

   // add a description
   RETURN_ON_MM_ERROR ( CreateProperty(MM::g_Keyword_Description, g_TigerCommHubDescription, MM::String, true) );

   // if we made it this far everything looks good
   initialized_ = true;
   return DEVICE_OK;
}

MM::DeviceDetectionStatus CTigerCommHub::DetectDevice()   // looks for hub, not child devices
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;

         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters for ASI Tiger controller
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         PurgeComPort(port_.c_str());
         int ret = TalkToTiger();  // this line unique to this hub, most of rest is copied from existing code
         if( DEVICE_OK != ret )
         {
            LogMessageCode(ret,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}

int CTigerCommHub::DetectInstalledDevices()
{
   // "installed" means "installed on the hardware", not "instantiated in Micro-manager"
   // this will create new class instances for all the installed hardware, but only when
   //   the user checks "selected" in the Peripherals list will it become accessible to MM
   // importantly MM's behavior is not to use the instance created in this function
   //   but create another instance by calling CreateDevice with the name parameter of
   //   whatever device is created here

   // make sure we can communicate with an initialized hub
   if (DetectDevice() != MM::CanCommunicate)
      return DEVICE_COMM_HUB_MISSING;

   // resets the list of possible devices
   ClearInstalledDevices();
   InitializeModuleData();

   RETURN_ON_MM_ERROR ( QueryCommandVerify("BU X", "TIGER_COMM") );

   // get the axis letters, types, and addresses from BU X reply
   //   example reply:
   //      TIGER_COMM<CR>
   //      Motor Axes: Z F P Q R S X Y V W<CR>
   //      Axis Types: p p u u u u x x z z<CR>
   //      Axis Addr: 1 2 3 3 3 3 4 4 5 5<CR>

   // parse the reply into vectors containing axis types, letters, and card addresses (binary and hex)
   vector<string> vReply = hub_->SplitAnswerOnCR();
   hub_->SetLastSerialAnswer(vReply[2]);
   vector<string> vAxesType = hub_->SplitAnswerOnSpace();
   if (vAxesType.size() < 3)
      return ERR_NOT_ENOUGH_AXES;
   vAxesType.erase(vAxesType.begin()); vAxesType.erase(vAxesType.begin());  // remove "Axis Types:"
   hub_->SetLastSerialAnswer(vReply[1]);
   vector<string> vAxesLetter = hub_->SplitAnswerOnSpace();
   vAxesLetter.erase(vAxesLetter.begin()); vAxesLetter.erase(vAxesLetter.begin()); // remove "Motor Axes:"
   hub_->SetLastSerialAnswer(vReply[3]);
   vector<string> vAxesAddr = hub_->SplitAnswerOnSpace();
   vAxesAddr.erase(vAxesAddr.begin()); vAxesAddr.erase(vAxesAddr.begin());      // remove "Axis Addr:"
   vector<string> vAxesAddrHex;
   if (vReply.size() > 4) // firmware Sep2013 onward
   {
      hub_->SetLastSerialAnswer(vReply[4]);
      vAxesAddrHex = hub_->SplitAnswerOnSpace();
      vAxesAddrHex.erase(vAxesAddrHex.begin()); vAxesAddrHex.erase(vAxesAddrHex.begin());      // remove "Hex Addr:"
   }
   else // old firmware doesn't have hex addresses so we create them here
   {
      string hex = "3x";
      for(unsigned int i=0; i<vAxesAddr.size(); i++)
      {
         char c = vAxesAddr[i].at(0);
         if (c < '1' || c > '9')
            return ERR_TOO_LARGE_ADDRESSES;
         hex.replace(1,1,vAxesAddr[i]);
         vAxesAddrHex.push_back(hex.c_str());
      }
   }

   // go through the Axis Type reply one at a time to assign the correct MM device type
   // these calls to AddAvailableDeviceName seem to be different than those in InitializeModuleData
   //   because it doesn't matter what description I put here and also because these calls
   //   appear to populate the Peripheral device list of the hub but the InitializeModuleData don't
   MM::Device* pDev;
   for (unsigned int i=0; i<vAxesType.size(); i++)
   {
      ostringstream debug;
      string name;
      char code = vAxesType[i].at(0);
      switch (code)
      {
         case 'x': // XYMotor type
            if (vAxesType[i+1].at(0) == 'x')  // make sure we have a pair
            {
               name = g_XYStageDeviceName;
               name.push_back(g_NameInfoDelimiter);
               name.push_back((char)toupper(vAxesLetter[i].at(0)));
               name.push_back((char)toupper(vAxesLetter[i+1].at(0)));
               name.push_back(g_NameInfoDelimiter);
               name.append(vAxesAddrHex[i]);
               pDev = CreateDevice(name.c_str());
               AddInstalledDevice(pDev);
               i++; // skip one code because we added two axes in one step
            }
            else
            {
               return ERR_TIGER_PAIR_NOT_PRESENT;
            }
            break;
         case 'u': // MMirror type
            if (vAxesType[i+1].at(0) == 'u')  // skip one code because we added two axes in one step
            {
               name = g_MMirrorDeviceName;
               name.push_back(g_NameInfoDelimiter);
               name.push_back((char)toupper(vAxesLetter[i].at(0)));
               name.push_back((char)toupper(vAxesLetter[i+1].at(0)));
               name.push_back(g_NameInfoDelimiter);
               name.append(vAxesAddrHex[i]);
               pDev = CreateDevice(name.c_str());
               AddInstalledDevice(pDev);
               i++; // skip one code because we added two axes in one step
            }
            else
            {
               return ERR_TIGER_PAIR_NOT_PRESENT;
            }
            break;
         case 'p':
         case 'a':
            name = g_PiezoDeviceName;
            name.push_back(g_NameInfoDelimiter);
            name.push_back((char)toupper(vAxesLetter[i].at(0)));
            name.push_back(g_NameInfoDelimiter);
            name.append(vAxesAddrHex[i]);
            pDev = CreateDevice(name.c_str());
            AddInstalledDevice(pDev);
            break;
         case 'z':
         case 'l':
            name = g_ZStageDeviceName;
            name.push_back(g_NameInfoDelimiter);
            name.push_back((char)toupper(vAxesLetter[i].at(0)));
            name.push_back(g_NameInfoDelimiter);
            name.append(vAxesAddrHex[i]);
            pDev = CreateDevice(name.c_str());
            AddInstalledDevice(pDev);
            break;
         case 'w':  // filter wheel
            name = g_FWheelDeviceName;
            name.push_back(g_NameInfoDelimiter);
            name.push_back((char)toupper(vAxesLetter[i].at(0)));
            name.push_back(g_NameInfoDelimiter);
            name.append(vAxesAddrHex[i]);
            pDev = CreateDevice(name.c_str());
            AddInstalledDevice(pDev);
            break;
         case 's':  // shutter not yet implemented
            break;
         case 'o':  // turret not yet implemented
            break;
         case 'f': // filter slider type
            name = g_FSliderDeviceName;
            name.push_back(g_NameInfoDelimiter);
            name.push_back((char)toupper(vAxesLetter[i].at(0)));
            name.push_back(g_NameInfoDelimiter);
            name.append(vAxesAddrHex[i]);
            pDev = CreateDevice(name.c_str());
            AddInstalledDevice(pDev);
            break;
         default:
            return ERR_TIGER_DEV_NOT_SUPPORTED;
      }
   }
   return DEVICE_OK;
}

int CTigerCommHub::TalkToTiger()
{
   // N.B. because we are descended from the ASIHub class we can call QueryCommandVerify directly
   //    but in other non-hub classes we would need to access method via hub_ (defined in ASIDevice)

   RETURN_ON_MM_ERROR( ClearComPort() );
   // make sure we are on Tiger
   ret_ = QueryCommandVerify("BU", "TIGER_COMM");
   if(ret_ == ERR_UNRECOGNIZED_ANSWER)
      ret_ = DEVICE_NOT_SUPPORTED;
   return ret_;
}

bool CTigerCommHub::Busy()
{
   // this is a query that the MM core can make of our device, i.e. it's a required part of our public API
   // we choose to define it as equivalent of issuing the STATUS command or /
   //    which reports whether any of the motors are moving by replying "B" or "N"
   int ret = QueryCommand("/");
   // not sure how to answer if there was a comm error; let's say we are not busy
   if (ret != DEVICE_OK)
      return false;
   return (LastSerialAnswer().substr(0,1).compare("B") == 0);
}
