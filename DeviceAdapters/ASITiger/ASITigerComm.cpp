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


#include "ASITiger.h"
#include "ASITigerComm.h"
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

int CTigerCommHub::Initialize()
{
   // if we can communicate over serial and get back the controller version then we're in good shape
   // make sure we are on Tiger
   RETURN_ON_MM_ERROR ( TalkToTiger() );

   // make sure we are using the old reply syntax
   // unfortunately the newer and easier-to-use syntax came after these device adapters
   // older firmware should just accept this command without any side effects
   // newer firmware will set to Whizkid syntax (:A everywhere, inconsistent axis specifiers, etc.)
   RETURN_ON_MM_ERROR ( QueryCommand("VB F=0") );

   // get version information from the controller, this is just for TigerComm (hub/serial card)
   int ret = QueryCommandVerify("0 V", ":A v");  // N.B. this is different for non-Tiger controllers like MS/WK-2000
   if(ret == ERR_UNRECOGNIZED_ANSWER)
      ret = DEVICE_NOT_SUPPORTED;
   RETURN_ON_MM_ERROR (ret);
   RETURN_ON_MM_ERROR ( ParseAnswerAfterPosition(4, firmwareVersion_) );
   stringstream command; command.str("");
   command << firmwareVersion_;
   RETURN_ON_MM_ERROR ( CreateProperty(g_FirmwareVersionPropertyName, command.str().c_str(), MM::Float, true) );

   // add a description
   RETURN_ON_MM_ERROR ( CreateProperty(MM::g_Keyword_Description, g_TigerCommHubDescription, MM::String, true) );

   // say which com port we are on
   RETURN_ON_MM_ERROR ( CreateProperty(g_SerialComPortPropertyName, port_.c_str(), MM::String, true) );

   // if we made it this far everything looks good
   initialized_ = true;
   return DEVICE_OK;
}

bool CTigerCommHub::SupportsDeviceDetection(void)
{
   return true;
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
      LogMessage("Exception in DetectDevice!");
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
   //   whatever device is created here... basically it creates a config file and then reloads it

   // make sure we can communicate with an initialized hub
   if (DetectDevice() != MM::CanCommunicate)
      return DEVICE_COMM_HUB_MISSING;

   // resets the list of possible devices
   ClearInstalledDevices();
   InitializeModuleData();

   build_info_type build;
   RETURN_ON_MM_ERROR ( GetBuildInfo("", build) );

   if (build.buildname.compare("TIGER_COMM") != 0)
      return ERR_UNRECOGNIZED_ANSWER;

   // go through the Axis Type reply one at a time to assign the correct MM device type
   // these calls to AddAvailableDeviceName seem to be different than those in InitializeModuleData
   //   because it doesn't matter what description I put here and also because these calls
   //   appear to populate the Peripheral device list of the hub but the InitializeModuleData don't
   MM::Device* pDev;
   string name;
   bool twoaxis = 0;
   unsigned int channels=1;
   char splitAxisLetter = ' '; // used only if XY stage split across two cards
   string splitAddrHex = "";  // used only if XY stage split across two cards
   int ret=0;
   for (unsigned int i=0; i<build.numAxes; i++)
   {
      twoaxis = 0;
	  channels=1; // One Channel per Axis is default
      name = "";
      ostringstream command;
      command << "Adding axis " << build.vAxesLetter[i] << " with type " << build.vAxesType[i] << " at address " << build.vAxesAddrHex[i];
      LogMessage(command.str());
      switch (build.vAxesType[i])
      {
         case 'x': // XYMotor type
            if (build.vAxesType[i+1] == 'x')
            {
               // we have an XY pair
               name = g_XYStageDeviceName;
               twoaxis = 1;
               i++; // skip one code because we added two axes in one step
            }
            else if (build.vAxesType[i+1] == 'z')
            {
               // we have one XY axis on this card (and presumably corresponding axis on other card)
               if(splitAxisLetter != ' ')
               {
                  // we already saw another unpaired axis, pair it with this one
                  name = g_XYStageDeviceName;
                  name.push_back(g_NameInfoDelimiter);
                  name.push_back(splitAxisLetter);  // prior unpaired axis
                  name.push_back(build.vAxesLetter[i]);
                  name.push_back(g_NameInfoDelimiter);
                  name.append(splitAddrHex); // prior unpaired address
                  name.append(build.vAxesAddrHex[i]);
                  pDev = CreateDevice(name.c_str());
                  AddInstalledDevice(pDev);

                  // we found our awaited match, get ready to look for any other split XY pairs
                  splitAxisLetter = ' ';
                  splitAddrHex = "";

                  continue;  // skip ahead to next axis; already created device
               }
               else
               {
                  // we haven't seen the corresponding axis yet
                  // start looking for next unpaired one
                  splitAxisLetter = build.vAxesLetter[i];
                  splitAddrHex = build.vAxesAddrHex[i];

                  continue;  // skip ahead to next axis without creating device
               }
            }
            else
               return ERR_TIGER_PAIR_NOT_PRESENT;
            break;
         case 'u': // scanner type (used to be MMirror type)
            if (build.vAxesType[i+1] == 'u')  // skip one code because we added two axes in one step
            {
               name = g_ScannerDeviceName;
               twoaxis = 1;
               i++; // skip one code because we added two axes in one step
            }
            else
               return ERR_TIGER_PAIR_NOT_PRESENT;
            break;
         case 'p':  // piezo focus like ADEPT
         case 'a':  // generic piezo axis
            name = g_PiezoDeviceName;
            break;
         case 'z':  // ZMotor like LS50, Z scope focus, etc.
         case 'l':  // generic linear motorized stage
            name = g_ZStageDeviceName;
            break;
         case 'w':  // filter wheel, uses different command set
            name = g_FWheelDeviceName;
            break;
         case 's':  // shutter not yet implemented
            break;
         case 'o':  // turret, a clocked device
            name = g_TurretDeviceName;
            break;
         case 'f': // filter slider, a clocked device
            name = g_FSliderDeviceName;
            break;
         case 'g': // programmable logic
            name = g_PLogicDeviceName;
            break;
         case 'i': // TGLED
            name = g_LEDDeviceName;
			// on TGLED card multiple channels fall under 1 axis
			//Now lets figure out how many channels there are onboard
			 command.str("");
			 command << build.vAxesAddr[i] << "BU";
			 ret=QueryCommandVerify(command.str(), "TGLED");
		     if(ret == ERR_UNRECOGNIZED_ANSWER)
			 { //error , lets go with a guess which is 4
			 channels=4;
			 }
			 else
			 {
			 ParseAnswerAfterUnderscore(channels);
			 }
            break; 
          case 'c': // TGPMT
            name = g_PMTDeviceName;
			// on TGPMT card multiple channels fall under 1 axis
			//Now lets figure out how many channels there are onboard
			 command.str("");
			 command << build.vAxesAddr[i] << "BU";
			 ret=QueryCommandVerify(command.str(), "TGPMT");
		     if(ret == ERR_UNRECOGNIZED_ANSWER)
			 { //error , lets go with a guess which is 2
			 channels=2;
			 }
			 else
			 {
			 ParseAnswerAfterUnderscore(channels);
			 }
            break; 
		 default:
            command.str("");
            command << "Device type " <<  build.vAxesType[i] << " not supported by Tiger device adapter, skipping it";
            LogMessage(command.str());
            continue; // go on to next axis (skips below code and goes to next for loop iteration)
      }

      // now form rest of extended name
      name.push_back(g_NameInfoDelimiter);
      if (twoaxis)
         name.push_back(build.vAxesLetter[i-1]);
      name.push_back(build.vAxesLetter[i]);
      name.push_back(g_NameInfoDelimiter);
      name.append(build.vAxesAddrHex[i]);
     if(channels>1)
	 {
		  for(unsigned int j=1;j<=channels;j++)  //create devices based on number of channels
		  {
			command.str("");
			command<<name<<g_NameInfoDelimiter<<j;
			pDev = CreateDevice(command.str().c_str());
			AddInstalledDevice(pDev);
		  }
	 }
	 else
	 {
	       pDev = CreateDevice(name.c_str());
           AddInstalledDevice(pDev);
	 }

}

   // now look for CRISP
   for (unsigned int i=0; i<build.numAxes; i++)
   {
      if (build.vAxesProps[i] & BIT0)  // BIT0 indicates CRISP
      {
         name = g_CRISPDeviceName;
         name.push_back(g_NameInfoDelimiter);
         name.push_back(build.vAxesLetter[i]);
         name.push_back(g_NameInfoDelimiter);
         name.append(build.vAxesAddrHex[i]);
         pDev = CreateDevice(name.c_str());
         AddInstalledDevice(pDev);
      }
   }

   // look for LED
   for (unsigned int i=0; i<build.numAxes; i++)
   {
      if (build.vAxesProps[i] & BIT6)  // BIT6 indicates LED, will only appear once per card address (enforced by firmware)
      {
         name = g_LEDDeviceName;
         name.push_back(g_NameInfoDelimiter);
         name.push_back(build.vAxesLetter[i]);  // need a char between colons for extended name functions to work, but LED device actually associated with card, not an axis
         name.push_back(g_NameInfoDelimiter);
         name.append(build.vAxesAddrHex[i]);
         pDev = CreateDevice(name.c_str());
         AddInstalledDevice(pDev);
      }
   }

   return DEVICE_OK;
}

int CTigerCommHub::TalkToTiger()
{
   RETURN_ON_MM_ERROR( ClearComPort() );
   // make sure we are on Tiger
   int ret = QueryCommandVerify("BU", "TIGER_COMM");
   if(ret == ERR_UNRECOGNIZED_ANSWER)
      ret = DEVICE_NOT_SUPPORTED;
   return ret;
}

bool CTigerCommHub::Busy()
{
   // this is a query that the MM core can make of our device, i.e. it's a required part of our public API
   // define the hub to never be busy, i.e. it can always accept a new command or query
   return false;

}
