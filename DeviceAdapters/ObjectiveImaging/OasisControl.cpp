///////////////////////////////////////////////////////////////////////////////
// FILE:          Oasis.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Objective Imaging Oasis device adapter.
//                
// AUTHOR:        Don Laferty, 4/9/12
// MAINTAINER:    Egor Zindy, University of Manchester, 2018-01-26
//                
//
// COPYRIGHT:     Objective Imaging,Ltd., Cambridge, UK, 2012
// LICENSE:       This library is free software; you can redistribute it and/or
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
//
// vim: set autoindent tabstop=3 softtabstop=3 shiftwidth=3 expandtab textwidth=78:

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

// The main Oasis controller API
#include "oasis4i.h"

#include "OasisControl.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"

#include <cstdio>
#include <math.h>
#include "boost/lexical_cast.hpp"


// Main Controller Hub
const char* g_Controller = "Oasis";

// XYStage
const char* g_XYStageDeviceName = "OasisXYStage";

// single axis stage
const char* g_ZStageDeviceName = "OasisZFocus";

const char* g_Wheel1 = "OasisWheel_1";
const char* g_Wheel2 = "OasisWheel_2";
const char* g_Wheel3 = "OasisWheel_3";
const char* g_Wheel4 = "OasisWheel_4";

const char* g_Shutter1 = "OasisShutter_1";
const char* g_Shutter2 = "OasisShutter_2";
const char* g_Shutter3 = "OasisShutter_3";
const char* g_Shutter4 = "OasisShutter_4";

const char* g_DAC1 = "OasisDAC_1";
const char* g_DAC2 = "OasisDAC_2";
const char* g_DAC3 = "OasisDAC_3";
const char* g_DAC4 = "OasisDAC_4";

const char* g_TTL1 = "OasisTTL_1";
const char* g_TTL2 = "OasisTTL_2";
const char* g_TTL3 = "OasisTTL_3";
const char* g_TTL4 = "OasisTTL_4";

const char* g_Oasis_Version = "DriverVersion";
const char* g_Oasis_Serial_Number = "SerialNumber";
const char* g_Oasis_Axis_Id = "OasisSingleAxisName";
const char* g_Device_Number_Shutter = "OasisDeviceNumberShutter";
const char* g_Device_Number_Wheel = "OasisDeviceNumberWheel";
const char* g_Shutter_Number = "OasisShutterNumber";
const char* g_Wheel_Number = "OasisWheelNumber";
const char* g_Wheel_Nr_Pos = "FilterPositions";
const char* g_TTL_Number = "OasisTTLNumber";

//Other properties
const char* g_Oasis_Board_Id= "BoardId";
const char* g_Oasis_StepSize_Um = "StepSize_um";
const char* g_Oasis_SpeedX_MmS = "SpeedX_mm_s";
const char* g_Oasis_SpeedY_MmS = "SpeedY_mm_s";
const char* g_Oasis_SpeedZ_MmS = "SpeedZ_mm_s";
const char* g_Oasis_Accel_RampX_Index = "AccelRampX";
const char* g_Oasis_Accel_RampY_Index = "AccelRampY";
const char* g_Oasis_Accel_RampZ_Index = "AccelRampZ";
const char* g_Oasis_Backlash_Correction = "BacklashCorrection";

// static lock
MMThreadLock Hub::lock_;


using namespace std;

/**
 * Look at currently installed Oasis controller hardware
 * to define what facilities are available to us
 */
bool CheckOasisConfig( UINT* lpWheelCount, UINT* lpShutterCount, UINT* lpTTLCount, UINT* lpDACCount )
{

   // Is the controller already open?
   BOOL bOpen = FALSE;
   int ret;

   ret = OI_GetDriverOpen( &bOpen );

   // if not, we need to open it temporarily
   if( !bOpen )
   {
      ret = OI_Open();
      if( OIFAILED(ret) )
         return false;
   }

   // how many controllers in this system?
   int nCards = 0;
   OI_CountCards( &nCards );

   int nType = -1;
   BOOL bFitted = FALSE;

   *lpWheelCount = *lpShutterCount = *lpTTLCount = *lpDACCount = 0;

   // loop through all controllers to see what's available
   // This defines the maximum number of devices required for any device
   for( int i=0; i<nCards; i++ )
   {
      OI_GetCardType( i, &nType );
      switch(nType)
      {
      case OASIS_4I:
         if (*lpWheelCount < 3) *lpWheelCount = 3;
         if (*lpTTLCount < 4) *lpTTLCount = 4;
         if (*lpShutterCount < 4) *lpShutterCount = 4;
         break;

      case OASIS_BLUE:
      case OASIS_EXPRESS:
         OI_IsModuleFitted(BLUE_DAC,&bFitted);
         if (*lpWheelCount < 4) *lpWheelCount = 4;
         if (*lpTTLCount < 4) *lpTTLCount = 4;
         if (*lpShutterCount < 2) *lpShutterCount = 2;
         if (bFitted && *lpDACCount < 4) *lpDACCount = 4;
         break;

      case OASIS_GLIDE:
      case OASIS_USB:
         if (*lpTTLCount < 2) *lpTTLCount = 2;
         if (*lpDACCount < 1) *lpDACCount = 1;
         break;
      }
   }

   // If we opened hardware just for this function, then
   // return it back to closed state
   if( !bOpen )
      OI_Close();

   return true;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   UINT uWheelCount = 0;
   UINT uShutterCount = 0;
   UINT uTTLCount = 0;
   UINT uDACCount = 0;

   // Available devices will be dependant on exactly what type of Oasis hardare is fitted
   // CheckOasisConfig will return information on what's there
   // Note that all Oasis controllers offer XY and Z
   bool bOk = CheckOasisConfig( &uWheelCount, &uShutterCount, &uTTLCount, &uDACCount );

   if( !bOk )
        return;

   // Hub
   RegisterDevice(g_Controller, MM::HubDevice, "OASIS Controller Hub (Add this first)");             

   // Stage and focus
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "OASIS/Glide XY Stage");
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "OASIS/Glide Z Focus");

   // Filter wheels
   if( uWheelCount > 0 )
      RegisterDevice(g_Wheel1, MM::StateDevice, "OASIS Wheel 1");
   if( uWheelCount > 1 )
      RegisterDevice(g_Wheel2, MM::StateDevice, "OASIS Wheel 2");
   if( uWheelCount > 2 )
      RegisterDevice(g_Wheel3, MM::StateDevice, "OASIS Wheel 3");
   if( uWheelCount > 3 )
      RegisterDevice(g_Wheel4, MM::StateDevice, "OASIS Wheel 4");

   // Shutters
   if( uShutterCount > 0 )
      RegisterDevice(g_Shutter1, MM::ShutterDevice, "OASIS Shutter 1");
   if( uShutterCount > 1 )
      RegisterDevice(g_Shutter2, MM::ShutterDevice, "OASIS Shutter 2");
   if( uShutterCount > 2 )
      RegisterDevice(g_Shutter3, MM::ShutterDevice, "OASIS Shutter 3");
   if( uShutterCount > 3 )
      RegisterDevice(g_Shutter4, MM::ShutterDevice, "OASIS Shutter 4");

   // TTL I/O
   if( uTTLCount > 0 )
      RegisterDevice(g_TTL1, MM::ShutterDevice, "OASIS TTL Output 1");
   if( uTTLCount > 1 )
      RegisterDevice(g_TTL2, MM::ShutterDevice, "OASIS TTL Output 2");
   if( uTTLCount > 2 )
      RegisterDevice(g_TTL3, MM::ShutterDevice, "OASIS TTL Output 3");
   if( uTTLCount > 3 )
      RegisterDevice(g_TTL4, MM::ShutterDevice, "OASIS TTL Output 4");

   // DACs
   if( uDACCount > 0 )
      RegisterDevice(g_DAC1, MM::SignalIODevice, "OASIS DAC 1");
   if( uDACCount > 1 )
      RegisterDevice(g_DAC2, MM::SignalIODevice, "OASIS DAC 2");
   if( uDACCount > 2 )
      RegisterDevice(g_DAC3, MM::SignalIODevice, "OASIS DAC 3");
   if( uDACCount > 3 )
      RegisterDevice(g_DAC4, MM::SignalIODevice, "OASIS DAC 4");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0)
      return 0;

   // Hub
   if (strcmp(deviceName, g_Controller) == 0)
   {
      return new Hub();                           
   }

   // XY Stage
   else if (strcmp(deviceName, g_XYStageDeviceName) == 0)                         
   {                                                                         
      XYStage* pXYStage = new XYStage();                                     
      return pXYStage;                                                       
   }

   // Focus
   else if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      return new ZStage();
   }


   // Filter wheels
   else if (strcmp(deviceName, g_Wheel1) == 0)
   {
      return new Wheel(1);
   }
   else if (strcmp(deviceName, g_Wheel2) == 0)
   {
      return new Wheel(2);
   }
   else if (strcmp(deviceName, g_Wheel3) == 0)
   {
      return new Wheel(3);
   }
   else if (strcmp(deviceName, g_Wheel4) == 0)
   {
      return new Wheel(4);
   }

   // Shutters
   else if (strcmp(deviceName, g_Shutter1) == 0 )
   {
      return new Shutter(1);
   }
   else if (strcmp(deviceName, g_Shutter2) == 0 )
   {
      return new Shutter(2);
   }
   else if (strcmp(deviceName, g_Shutter3) == 0 )
   {
      return new Shutter(3);
   }
   else if (strcmp(deviceName, g_Shutter4) == 0 )
   {
      return new Shutter(4);
   }

   // TTL I/O
   else if (strcmp(deviceName, g_TTL1) == 0 )
   {
      return new TTL(1);
   }
   else if (strcmp(deviceName, g_TTL2) == 0 )
   {
      return new TTL(2);
   }
   else if (strcmp(deviceName, g_TTL3) == 0 )
   {
      return new TTL(3);
   }
   else if (strcmp(deviceName, g_TTL4) == 0 )
   {
      return new TTL(4);
   }
 
   // DACs
   else if (strcmp(deviceName, g_DAC1) == 0 )
   {
      return new DAC(1);
   }
   else if (strcmp(deviceName, g_DAC2) == 0 )
   {
      return new DAC(2);
   }
   else if (strcmp(deviceName, g_DAC3) == 0 )
   {
      return new DAC(3);
   }
   else if (strcmp(deviceName, g_DAC4) == 0 )
   {
      return new DAC(4);
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
 
  
///////////////////////////////////////////////////////////////////////////////
// Oasis Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   boardId_(0)
{
   InitializeDefaultErrorMessages();

   // custom error messages:
   
   // pre-initialization properties

   //Board ID
   int ret;
   int nCards = 0;

   ret = OI_Open();
   assert( TranslateReturn( ret ) == DEVICE_OK);

   OI_CountCards( &nCards );
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnBoardID);

   ret = CreateProperty(g_Oasis_Board_Id,"0",MM::Integer,false, pAct, true);
   assert(ret == DEVICE_OK);
   SetPropertyLimits(g_Oasis_Board_Id, 0, nCards-1);

   ret = OI_Close();
   assert( TranslateReturn( ret ) == DEVICE_OK);
}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_Controller);
}

bool Hub::Busy()
{
   return false;
}

/**
 * Translate an Oasis return code into uM return code
 */
int TranslateReturn(int oi_ret)
{
   switch(oi_ret)
   {
   case OI_OK:
      return DEVICE_OK;

   default:
      if(oi_ret & OI_NOTSUPPORTED)
         return DEVICE_NOT_SUPPORTED;

      // None of the Oasis controllers are serial controllers, but
      // this is the only uM timeout error offered, so we'll just use it
      else if(oi_ret & OI_TIMEOUT)
         return DEVICE_SERIAL_TIMEOUT;

      else if(oi_ret & OI_INVALIDARG)
         return DEVICE_INVALID_PROPERTY_VALUE;

      else if(oi_ret & OI_NOHARDWARE)
         return DEVICE_NOT_CONNECTED;

      else
         return DEVICE_ERR;
   }
}

bool Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Hub::DetectDevice(void)
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {
      // see if any Oasis controllers are installed
      int nCards = 0;
      OI_CountCards( &nCards );
      if( nCards == 0 )
      {
         LogMessage("No Oasis controller(s) found",true);
      }
      else
      {
         // to succeed must reach here....
         result = MM::CanCommunicate;
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}

int Hub::QuerySerialNum(std::string& sn)
{
   int returnStatus = DEVICE_OK;

   char szSerialNum[256];
   OI_ReadSerialNum(boardId_, szSerialNum, sizeof(szSerialNum));
   sn = szSerialNum;

   return returnStatus;
}

int Hub::QueryDescription(std::string& desc)
{
   int returnStatus = DEVICE_OK;

   char szDescription[256];
   OI_ReadPCBID(szDescription, sizeof(szDescription));
   desc = szDescription;
   std::replace( desc.begin(), desc.end()-1, '\n', ',');

   return returnStatus;
}

int Hub::QueryVersion(std::string& version)
{
   int returnStatus = DEVICE_OK;

   char szVersion[256];
   OI_GetDriverVersion(szVersion, sizeof(szVersion));
   version = szVersion;

   /*
   ret = OI_ReadPCBID(szVersion, sizeof(szVersion));
   version += ",";
   version += szVersion;

   ret = OI_ReadPCBVersion(szVersion, sizeof(szVersion));
   version += ",";
   version += szVersion;
   */

   return returnStatus;
}


int Hub::Initialize()
{

   MMThreadGuard myLock(lock_);
   // Open the Oasis library
   OI_Open();

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_Controller, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   std::string result;

   // Description
   ret = QueryDescription(result);
   if( DEVICE_OK != ret)
      return ret;

   // Create read-only property with descriptiun info
   //ret = CreateProperty(MM::g_Keyword_Description, "OASIS Controller", MM::String, true);
   ret = CreateProperty(MM::g_Keyword_Description, result.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version of the controller:
   ret = QueryVersion(result);
   if( DEVICE_OK != ret)
      return ret;

   // Create read-only property with version info
   ret = CreateProperty(g_Oasis_Version, result.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   //Serial number of the controller
   ret = QuerySerialNum(result);
   if( DEVICE_OK != ret)
      return ret;

   // Create read-only property with serial info
   ret = CreateProperty(g_Oasis_Serial_Number, result.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   //  Close the Oasis library
   OI_Close();

   return DEVICE_OK;
}

int Hub::DetectInstalledDevices()
{

   ClearInstalledDevices();

   // make sure this method is called before we look for available devices
   InitializeModuleData();

   char hubName[MM::MaxStrLength];
   GetName(hubName); // this device name
   for (unsigned i=0; i<GetNumberOfDevices(); i++)
   { 
      char deviceName[MM::MaxStrLength];
      bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
      if (success && (strcmp(hubName, deviceName) != 0))
      {
         MM::Device* pDev = CreateDevice(deviceName);
         AddInstalledDevice(pDev);
      }
   }
   return DEVICE_OK; 

   //TODO: This code is not accessed at the moment... Needs review?
   int ret;
   discoverableDevices_.clear();
   inventoryDeviceAddresses_.clear();
   inventoryDeviceIDs_.clear();

   //std::string v;
   //QueryVersion(v);

   bool validEntry = false;

   WORD wX, wY, wZ, wF;
   ret = OI_ReadStatusXY( &wX, &wY );
   if( OISUCCESS(ret) && ((wX & S_MOTOR_DETECTED) || (wY & S_MOTOR_DETECTED)) )
   {
      validEntry = true;
      discoverableDevices_.push_back(g_XYStageDeviceName);
   }

   ret = OI_ReadStatusZ( &wZ );
   if( OISUCCESS(ret) && (wZ & S_MOTOR_DETECTED) )
   {
      validEntry = true;
      discoverableDevices_.push_back(g_ZStageDeviceName);
   }

   // TODO: Handle other devices
     /*
                  if(0==(*itt).compare(shutterToken))
                  {
                     discoverableDevices_.push_back(g_Shutter);
                     validEntry = true;
                  }
                  if(0==(*itt).compare(wheelToken))
                  {
                     discoverableDevices_.push_back(g_Wheel);
                     validEntry = true;
                  }
   */
   return DEVICE_OK;
}

int Hub::GetNumberOfDiscoverableDevices()
{
   //QueryPeripheralInventory();
   return (int) discoverableDevices_.size();

}

void Hub::GetDiscoverableDevice(int peripheralNum, char* peripheralName, unsigned int maxNameLen)
{ 
   if( -1 < peripheralNum)
   {
      if( peripheralNum < int(discoverableDevices_.size()))
      {
            strncpy(peripheralName, discoverableDevices_[peripheralNum].c_str(), maxNameLen - 1);
            peripheralName[maxNameLen - 1] = 0;
      }
   
   }
   return;
} 

int Hub::GetDiscoDeviceNumberOfProperties(int peripheralNum)
{
   int retv = 0;

   if( -1 < peripheralNum)
   {
      if( peripheralNum < (int)inventoryDeviceAddresses_.size())
         retv =2;
   }
   return retv;

}

void Hub::GetDiscoDeviceProperty(int peripheralNum, short propertyNumber,char* propertyName, char* propValue, unsigned int maxValueLen)
{
   if( -1 < peripheralNum)
   {
      if( peripheralNum < (int)inventoryDeviceAddresses_.size())
      {
         switch( propertyNumber)
         {
            //there is something interesting in here, some of the axes use the the 1-character device ID as the "ID" and some use something else...
            // so for today I will call these two properties ONECHARACTERDEVICEID and DEVICEADDRESS
         case 0:
            if( 0 != propertyName)
               strncpy(propertyName,"DEVICEADDRESS",maxValueLen-1);
            if(0!=propValue)
            {
               std::ostringstream os;
               os << inventoryDeviceAddresses_[peripheralNum];
               strncpy(propValue, os.str().c_str(), maxValueLen-1);
            }
            break;

         case 1:
            if( 0 != propertyName)
               strncpy(propertyName,"ONECHARACTERDEVICEID",maxValueLen-1);
            if(0!=propValue)
            {
               std::ostringstream os;
               os <<  inventoryDeviceIDs_[peripheralNum];
               strncpy(propValue, os.str().c_str(), maxValueLen-1);
            }
            break;
         }
      }
   }
}



//////////////// Action Handlers (Hub) /////////////////


int Hub::OnConfig(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      // TODO check that we were initialized
      string request;
      pProp->Get(request);
      if (request == "GetInfo")
      {
         // Get Info and write to debug output:
      }
   }
   return DEVICE_OK;
}
     
int Hub::OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)boardId_);
   }
   else if (eAct == MM::AfterSet)
   {
      long ID;
      pProp->Get(ID);
      boardId_ = (int)ID;
   }

   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// XYStage
//

/**
 * XYStage - two axis stage device.
 * Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
 * system with the X and Y axis going the 'Micro-Manager standard' direction
 * Then, there is the  native system.  All functions using 'steps' use the controller system
 * All functions using Um use the Micro-Manager coordinate system
 */
XYStage::XYStage() :
   initialized_(false), 
   stepSizeUm_(0.1), 
   speedX_(10.0),
   speedY_(10.0),
   rampX_(1),
   rampY_(1),
   backlash_(0),
   originX_(0),
   originY_(0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup
   SetErrorText(ERR_NO_CONTROLLER, "Please add the OasisController device first!");

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Oasis and Glide XY stage driver adapter", MM::String, true);

}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int ret;
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSize);
   ret = CreateProperty(g_Oasis_StepSize_Um, "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed (in mm/sec)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);
   ret = CreateProperty(g_Oasis_SpeedX_MmS, "10.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
   ret = CreateProperty(g_Oasis_SpeedY_MmS, "10.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // read speed ranges
   double dminX, dminY, dmaxX, dmaxY;
   OI_LookupSpeedXY( 0, 0, &dminX, &dminY );
   OI_LookupSpeedXY( 511, 511, &dmaxX, &dmaxY );
   SetPropertyLimits(g_Oasis_SpeedX_MmS, dminX, dmaxX); // mm/s
   SetPropertyLimits(g_Oasis_SpeedY_MmS, dminY, dmaxY); // mm/s

   // Oasis Ramp LUTs
   pAct = new CPropertyAction (this, &XYStage::OnRampX);
   ret = CreateProperty(g_Oasis_Accel_RampX_Index, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Oasis_Accel_RampX_Index, "0");
   AddAllowedValue(g_Oasis_Accel_RampX_Index, "1");
   AddAllowedValue(g_Oasis_Accel_RampX_Index, "2");
   AddAllowedValue(g_Oasis_Accel_RampX_Index, "3");

   pAct = new CPropertyAction (this, &XYStage::OnRampY);
   ret = CreateProperty(g_Oasis_Accel_RampY_Index, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Oasis_Accel_RampY_Index, "0");
   AddAllowedValue(g_Oasis_Accel_RampY_Index, "1");
   AddAllowedValue(g_Oasis_Accel_RampY_Index, "2");
   AddAllowedValue(g_Oasis_Accel_RampY_Index, "3");

   // Backlash correction enable
   pAct = new CPropertyAction (this, &XYStage::OnBacklash);
   ret = CreateProperty(g_Oasis_Backlash_Correction, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(g_Oasis_Backlash_Correction, "0");
   AddAllowedValue(g_Oasis_Backlash_Correction, "1");
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

/**
 * Returns true if any axis (X or Y) is still moving.
 */
bool XYStage::Busy()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return false;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   WORD wX=0, wY=0;
   ret = OI_ReadStatusXY( &wX, &wY );
   bool bMoving =  ((wX & S_MOVING) || (wY & S_MOVING));
   return (bMoving && OISUCCESS(ret) );
}

int XYStage::SetPositionUm(double x, double y)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   //get the limits
   double xmin, xmax, ymin, ymax;
   OI_GetUserLimitsXY( &xmin, &xmax,&ymin, &ymax );

   // go to origin
   bool mirrorX, mirrorY;
   GetOrientation(mirrorX, mirrorY);

   if (mirrorX) x = xmax-x;
   if (mirrorY) y = ymax-y;

   // We do an asynchronous move, relying the application
   // to call AxisBusy if it needs to know whether the stage is moving
   return TranslateReturn( OI_MoveToXY( x, y, 0 ) );
}

int XYStage::GetPositionUm(double& x, double& y)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   double dx, dy;
   ret = OI_ReadXY( &dx, &dy );

   //get the limits
   double xmin, xmax, ymin, ymax;
   ret = OI_GetUserLimitsXY( &xmin, &xmax,&ymin, &ymax );

   // go to origin
   bool mirrorX, mirrorY;
   GetOrientation(mirrorX, mirrorY);

   if (mirrorX) x = xmax-x;
   if (mirrorY) y = ymax-y;

   x = (mirrorX)? xmax-dx : dx;
   y = (mirrorY)? ymax-dy : dy;
   y = dy;
   return TranslateReturn( ret );
}

int XYStage::SetRelativePositionUm(double dx, double dy)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   // go to origin
   bool mirrorX, mirrorY;
   GetOrientation(mirrorX, mirrorY);

   if (mirrorX) dx = -dx;
   if (mirrorY) dy = -dy;

   return TranslateReturn( OI_StepXY( dx, dy, 0 ) );
} 

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   //get the limits
   double xmin, xmax, ymin, ymax;
   long xmax_steps, ymax_steps;

   ret = OI_GetUserLimitsXY( &xmin, &xmax,&ymin, &ymax );
   ret = OI_MicronsToStepsX(xmax, &xmax_steps);
   ret = OI_MicronsToStepsY(ymax, &ymax_steps);

   // go to origin
   bool mirrorX, mirrorY;
   GetOrientation(mirrorX, mirrorY);

   if (mirrorX) x = xmax_steps-x;
   if (mirrorY) y = ymax_steps-y;

   return TranslateReturn( OI_MoveToXY_Abs( x, y, 0 ) );
}
  
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long x, long y)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   // go to origin
   bool mirrorX, mirrorY;
   GetOrientation(mirrorX, mirrorY);

   if (mirrorX) x = -x;
   if (mirrorY) y = -y;

   long lX, lY;
   ret = OI_ReadXY_Abs( &lX, &lY );
   lX += x;
   lY += y;
   if( OISUCCESS(ret) )
   {
      ret |= OI_MoveToXY_Abs( lX, lY, 0 );
   }
   return TranslateReturn( ret );
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   long lX, lY;
   ret = OI_ReadXY_Abs( &lX, &lY );
   x = lX;
   y = lY;
   return TranslateReturn( ret );
}

/**
 * Defines current position as origin (0,0) coordinate of the controller.
 */
int XYStage::SetOrigin()
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_SetOriginXY() );
}

/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 */
int XYStage::SetAdapterOrigin()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   double dx, dy;
   ret = OI_ReadXY( &dx, &dy );
   if( OISUCCESS(ret) )
   {
      originX_ = dx;
      originY_ = dy;
   }
   return TranslateReturn(ret);
}


int XYStage::Home()
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_InitializeXY() );
}


int XYStage::Stop()
{  
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_HaltXY() );
}


/**
 * Returns the stage position limits in um.
 */
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   double xmin, xmax, ymin, ymax;
   ret = OI_GetUserLimitsXY( &xmin, &xmax,&ymin, &ymax );

   xMin = xmin;
   yMin = ymin;
   xMax = xmax;
   yMax = ymax;
   return TranslateReturn(ret);
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

//copied from the zaber stage
void XYStage::GetOrientation(bool& mirrorX, bool& mirrorY) 
{
   // copied from DeviceBase.h
   this->LogMessage("XYStage::GetOrientation\n", true);

   char val[MM::MaxStrLength];
   int ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorX, val);
   assert(ret == DEVICE_OK);
   mirrorX = strcmp(val, "1") == 0 ? true : false;

   ret = this->GetProperty(MM::g_Keyword_Transpose_MirrorY, val);
   assert(ret == DEVICE_OK);
   mirrorY = strcmp(val, "1") == 0 ? true : false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
       Hub* hub = static_cast<Hub*>(GetParentHub());
       if (!hub || !hub->isInitialized())
       {
          return ERR_NO_CONTROLLER;
       }

       //select the right card
       OI_SelectCard(hub->GetBoardID());

      double dStep = 1.0;
      int ret = OI_GetAxisStepSize(OI_XAXIS,&dStep);
      if( OISUCCESS(ret) )
      {
         stepSizeUm_ = dStep;
         pProp->Set(stepSizeUm_);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

/*
 * Speed as returned by device is in pulses per second
 * We convert that to um per second using the factor stepSizeUm)
 */
int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double xspeed, yspeed;

   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetSpeedXY( &xspeed, &yspeed );

   if (eAct == MM::BeforeGet)
   {
      speedX_ = xspeed;
      pProp->Set(speedX_);
   }
   else if (eAct == MM::AfterSet)
   {
      double speed;
      pProp->Get(speed);
      speedX_ = speed;
      ret = OI_SelectSpeedXY( speedX_, yspeed, 0 );
   }
   return TranslateReturn( ret );
}

int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double xspeed, yspeed;

   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetSpeedXY( &xspeed, &yspeed );

   if (eAct == MM::BeforeGet)
   {
      speedY_ = yspeed;
      pProp->Set(speedY_);
   }
   else if (eAct == MM::AfterSet)
   {
      double speed;
      pProp->Get(speed);
      speedY_ = speed;
      ret = OI_SelectSpeedXY( xspeed, speedY_, 0 );
   }
   return TranslateReturn( ret );
}

int XYStage::OnRampX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int xramp, yramp;

   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetRampXY( &xramp, &yramp );

   if (eAct == MM::BeforeGet)
   {
      rampX_ = xramp;
      pProp->Set((long)rampX_);
   }
   else if (eAct == MM::AfterSet)
   {
      int ramp;
      pProp->Get((long&)ramp);
      rampX_ = ramp;
      ret = OI_SetRampXY( rampX_, yramp );
   }
   return TranslateReturn( ret );

}
int XYStage::OnRampY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int xramp, yramp;

   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetRampXY( &xramp, &yramp );

   if (eAct == MM::BeforeGet)
   {
      rampY_ = yramp;
      pProp->Set((long)rampY_);
   }
   else if (eAct == MM::AfterSet)
   {
      int ramp;
      pProp->Get((long&)ramp);
      rampY_ = ramp;
      ret = OI_SetRampXY( xramp, rampY_ );
   }
   return TranslateReturn( ret );

}

int XYStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   BOOL bX, bY;

   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetAxisBacklash( OI_XAXIS, &bX );
   ret |= OI_GetAxisBacklash( OI_XAXIS, &bY );

   if (eAct == MM::BeforeGet)
   {
      backlash_ = (bX || bY);
      pProp->Set((long)backlash_);
   }
   else if (eAct == MM::AfterSet)
   {
      long backlash;
      pProp->Get(backlash);
      backlash_ = backlash;
      ret = OI_SetAxisBacklash( OI_XAXIS, (BOOL)backlash_);
      ret |= OI_SetAxisBacklash( OI_YAXIS, (BOOL)backlash_);
   }
   return TranslateReturn( ret );

}

///////////////////////////////////////////////////////////////////////////////
// Z - Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
ZStage::ZStage() :
   initialized_(false),
   range_measured_(false),
   speedZ_(0.3), //[mm/s]
   accelZ_(1),
   originZ_(0),
   stepSizeUm_(0.1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Oasis and Glide Z axis driver", MM::String, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}


int ZStage::Initialize()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   double dval;
   int nval;
   BOOL bOpen = FALSE;
   ret = OI_GetDriverOpen( &bOpen );
   if ( !bOpen ) return DEVICE_NOT_CONNECTED;

   // set property list
   // -----------------
   
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSize);
   // get current step size from the controller

   OI_GetAxisStepSize( OI_ZAXIS, &stepSizeUm_ );

   ret = CreateProperty(g_Oasis_StepSize_Um, CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   pAct = new CPropertyAction (this, &ZStage::OnSpeed);
   OI_GetSpeedZ( &dval );
   ret = CreateProperty(g_Oasis_SpeedZ_MmS, CDeviceUtils::ConvertToString(dval), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;

   // read speed ranges
   double dmin, dmax;
   OI_LookupSpeedZ( 0, &dmin );
   OI_LookupSpeedZ( 511, &dmax );
   SetPropertyLimits(g_Oasis_SpeedZ_MmS, dmin, dmax); // mm/s


   // Accel Ramp LUT selection
   // -----

   pAct = new CPropertyAction (this, &ZStage::OnAccel);
   OI_GetRampZ( &nval );
   ret = CreateProperty(g_Oasis_Accel_RampZ_Index,  CDeviceUtils::ConvertToString(nval), MM::Integer, false, pAct);
   if (ret != DEVICE_OK) return ret;
   AddAllowedValue(g_Oasis_Accel_RampZ_Index, "0");
   AddAllowedValue(g_Oasis_Accel_RampZ_Index, "1");
   AddAllowedValue(g_Oasis_Accel_RampZ_Index, "2");
   AddAllowedValue(g_Oasis_Accel_RampZ_Index, "3");

   pAct = new CPropertyAction (this, &ZStage::OnBacklash);
   ret = CreateProperty(g_Oasis_Backlash_Correction, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Oasis_Backlash_Correction, "0");
   AddAllowedValue(g_Oasis_Backlash_Correction, "1");

   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return false;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   WORD wZ = 0;
   ret = OI_ReadStatusZ( &wZ );
   return ( (bool)((wZ & S_MOVING)>0) && OISUCCESS(ret) );
}

int ZStage::Stop()
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_HaltZ() );
}

int ZStage::SetPositionUm(double pos)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_MoveToZ( pos, 0 ) );
}

int ZStage::SetRelativePositionUm(double d)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_StepZ( d, 0 ) );
}

int ZStage::GetPositionUm(double& pos)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_ReadZ( &pos ) );
}
  
int ZStage::SetPositionSteps(long pos)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_MoveToZ_Abs( pos, 0 ) );
}
  
int ZStage::GetPositionSteps(long& steps)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_ReadZ_Abs( &steps ) );
}
  
int ZStage::SetAdapterOrigin()
{
   double zz;
   int ret = GetPositionUm(zz);
   if (ret != DEVICE_OK)
      return ret;
   originZ_ = zz;

   return DEVICE_OK;
}


int ZStage::SetOrigin()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_SetOriginZ();
   return SetAdapterOrigin();
}


/**
 * Defines current position as (d) coordinate of the controller.
 */
int ZStage::SetAdapterOriginUm(double d)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   return TranslateReturn( OI_SetPositionZ( d ) );
}

int ZStage::Move(double v)
{
   // TODO: if v is not in mm/s then please convert here to mm/s
   double v_ = v;
   return DEVICE_OK;
}

/**
 * Returns the stage position limits in um.
 */
int ZStage::GetLimits(double& min, double& max)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   BOOL bmin, bmax;
   ret = OI_GetUserLimitsEnabledZ( &bmin, &bmax );
   if( !bmin || !bmax) return DEVICE_UNKNOWN_POSITION;

   ret = OI_GetUserLimitsZ( &min, &max );
   return TranslateReturn( ret );
}

///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret;
   if (eAct == MM::BeforeGet)
   {
       Hub* hub = static_cast<Hub*>(GetParentHub());
       if (!hub || !hub->isInitialized())
       {
          return ERR_NO_CONTROLLER;
       }

       //select the right card
       OI_SelectCard(hub->GetBoardID());

      double dStep= 1.0;
      ret = OI_GetAxisStepSize(OI_ZAXIS,&dStep);
      if( OISUCCESS(ret) )
      {
         stepSizeUm_ = dStep;
         pProp->Set(stepSizeUm_);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}



int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = OI_OK;
   if (eAct == MM::BeforeGet)
   {
      Hub* hub = static_cast<Hub*>(GetParentHub());
      if (!hub || !hub->isInitialized())
      {
         return ERR_NO_CONTROLLER;
      }

      //select the right card
      OI_SelectCard(hub->GetBoardID());

      double speed;
      ret = OI_GetSpeedZ( &speed );
      speedZ_ = speed;
      pProp->Set(speedZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      double speed; // Speed in mm/sec
      pProp->Get(speed);
      speedZ_ = speed;
      ret = OI_SelectSpeedZ( speedZ_, 0 );
   }
   return TranslateReturn(ret);
}


int ZStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = OI_OK;
   if (eAct == MM::BeforeGet)
   {
      Hub* hub = static_cast<Hub*>(GetParentHub());
      if (!hub || !hub->isInitialized())
      {
         return ERR_NO_CONTROLLER;
      }

      //select the right card
      OI_SelectCard(hub->GetBoardID());

      int accel;
      ret = OI_GetRampZ( &accel );
      accelZ_ = accel;
      pProp->Set((long)accelZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      int accel;
      pProp->Get((long&)accel);
      accelZ_ = accel;
      ret = OI_SetRampZ( accelZ_ );
   }
   return TranslateReturn(ret);
}

int ZStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = OI_OK;
   if (eAct == MM::BeforeGet)
   {
      Hub* hub = static_cast<Hub*>(GetParentHub());
      if (!hub || !hub->isInitialized())
      {
         return ERR_NO_CONTROLLER;
      }

      //select the right card
      OI_SelectCard(hub->GetBoardID());

      BOOL bZ = FALSE;
      ret = OI_GetAxisBacklash( OI_ZAXIS, &bZ );
      backlash_ = (int)bZ;
      pProp->Set((long)backlash_);
   }
   else if (eAct == MM::AfterSet)
   {
      long backlash;
      pProp->Get(backlash);
      backlash_ = backlash;
      ret = OI_SetAxisBacklash( OI_ZAXIS, (BOOL)backlash_);
   }
   return TranslateReturn(ret);
}


///////////////////////////////////////////////////////////////////////////////
// Wheel 
///////////////////////////////////////////////////////////////////////////////

Wheel::Wheel(int index) : 
   name_("Undefined"), 
   pos_(1),
   initialized_(false), 
   wheelHomeTimeoutS_(10),
   numPos_(6)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer recived from the device");
   SetErrorText(ERR_INVALID_WHEEL_NUMBER, "The Oasis controller can have up to 4 filter wheels");
   SetErrorText(ERR_NO_CONTROLLER, "Please add the Oasis Controller device first!");
   SetErrorText(ERR_INVALID_WHEEL_POSITION, "Invalid filter position requested.  Positions 1 through 10 are valid");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");
   SetErrorText(ERR_WHEEL_HOME_FAILED, "Failed to Home the filter wheel.");

   // create pre-initialization properties
   // ------------------------------------

   // Description
   CreateProperty(MM::g_Keyword_Description, "Oasis filter wheel adapter", MM::String, true);

   // Number of positions in the wheel
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnWheelNrPos);
   CreateProperty(g_Wheel_Nr_Pos, "6", MM::Integer, false, pAct, true);
   SetPropertyLimits(g_Wheel_Nr_Pos, 2, 16);

   // Wait this long for wheel to time out:
   pAct = new CPropertyAction (this, &Wheel::OnWheelHomeTimeout);
   CreateProperty("Home-Timeout-(s)", "10", MM::Float, false, pAct, true);

   // which filter wheel are we?
   wheelNumber_ = index;

   switch(index)
   {
       case 1: name_ = g_Wheel1; break;
       case 2: name_ = g_Wheel2; break;
       case 3: name_ = g_Wheel3; break;
       case 4: name_ = g_Wheel4; break;
   }

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   UpdateStatus();
}

Wheel::~Wheel()
{
   Shutdown();
}

void Wheel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Wheel::Initialize()
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   int ret;

   // Make sure that a valid number was used in the constructor:
   if (wheelNumber_ < 1 || wheelNumber_ > 4)
      return ERR_INVALID_WHEEL_NUMBER;


   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   /*
   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   */

   // create default positions and labels
   const int bufSize = 64;
   char buf[bufSize];
   for (int i=0; i< numPos_; i++)
   {
      snprintf(buf, bufSize, "Filter-%d", i + 1);
      SetPositionLabel(i, buf);
   }
   
   if (HomeWheel() != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Wheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Wheel::Busy()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return false;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int nCard = 0, nAxis = 0;
   ret = OI_GetFilterChangerInfo(wheelNumber_-1,&nCard, &nAxis);

   WORD wStatus = 0;
   ret = OI_ReadAxisStatusEx( nCard, nAxis, &wStatus );

   return ((wStatus & S_MOVING)>0) && OISUCCESS(ret);
}

/**
 * Homes the wheel
 */
int Wheel::HomeWheel()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_InitFilterChangerEx( wheelNumber_-1, OI_FILTER_INIT_HOME, 0.0 );
   return TranslateReturn( ret );
}

/**
 * Sets wheel position
 */
int Wheel::SetWheelPosition(int position)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_MoveToFilterEx( wheelNumber_-1, position, 0 );
   pos_ = position;
   return TranslateReturn( ret );
}

/**
 * Ask controller about filter position
 */
int Wheel::GetWheelPosition(int& position)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   ret = OI_GetFilterPositionEx( wheelNumber_-1, &position );
   if( position < 1 ) position = 1;
   pos_ = position;

   return TranslateReturn( ret );
}

//////////////// Action Handlers (Wheel) /////////////////
//


int Wheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetWheelPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos-1);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      pos +=1;
      if ((pos > 0) && (pos<=numPos_))
         return SetWheelPosition(pos);
      else
         return ERR_INVALID_WHEEL_POSITION;
   }
   return DEVICE_OK;
}

int Wheel::OnWheelNrPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)numPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long tmp;
      pProp->Get(tmp);
      numPos_ = (int) tmp;
   }
   return DEVICE_OK;
}

int Wheel::OnWheelHomeTimeout(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(wheelHomeTimeoutS_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(wheelHomeTimeoutS_);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Shutter
///////////////////////////////////////////////////////////////////////////////

Shutter::Shutter(int index) : 
   name_("Undefined"), 
   shutterNumber_(1), 
   initialized_(false), 
   changedTime_ (0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer received from the device");
   SetErrorText(ERR_INVALID_SHUTTER_STATE, "Shutter state can only be 0 or 1");
   SetErrorText(ERR_INVALID_SHUTTER_NUMBER, "The Oasis controllers can offer between 1 and 4 shutters");
   SetErrorText(ERR_SHUTTER_COMMAND_FAILED, "Error while sending a command to the shutter");
   SetErrorText(ERR_DEVICE_CHANGE_NOT_ALLOWED, "Device number can not be changed");
   SetErrorText(ERR_SHUTTER_USED, "A shutter with this device and shutter number is already in use");
   SetErrorText(ERR_NO_CONTROLLER, "Please add the Oasis device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Description
   CreateProperty(MM::g_Keyword_Description, "Oasis shutter", MM::String, true);

   shutterNumber_= index;

   switch(index)
   {
       case 1: name_ = g_Shutter1; break;
       case 2: name_ = g_Shutter2; break;
       case 3: name_ = g_Shutter3; break;
       case 4: name_ = g_Shutter4; break;
   }

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   UpdateStatus();

}

Shutter::~Shutter()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   int ret;

   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // Make sure that a valid number was used in the constructor:
   if (shutterNumber_ < 1 || shutterNumber_ > 4)
      return ERR_INVALID_SHUTTER_NUMBER;


   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   /* 
   // Label
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   SetPositionLabel(0, "Closed");
   SetPositionLabel(1, "Open");

   
   // Delay
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnDelay);
   ret = CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   */

   EnableDelay();
   changedTime_ = GetCurrentMMTime();

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Shutter::Busy()
{
   BOOL bMoving = FALSE;
   int ret = OI_IsShutterMoving( shutterNumber_, &bMoving );
   return ( OISUCCESS(ret) && bMoving );
}

int Shutter::SetOpen(bool open)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   changedTime_ = GetCurrentMMTime();
   int pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   OI_SetShutter( pos, shutterNumber_ );
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int pos = 0;
   int ret = OI_GetShutter( &pos, shutterNumber_ );
   open = (pos == 1) ? true : false;
   return TranslateReturn(ret);
}

int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

/**
 * Sends an open/close command through the serial port.
 */
int Shutter::SetShutterPosition(bool state)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int pos  = (state) ? 1 : 0;
   return TranslateReturn( OI_SetShutter( pos, shutterNumber_ ) );
}

/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int pos;
   ret = OI_GetShutter( &pos, shutterNumber_ );
   state = (pos == 0) ? false : true;
   return TranslateReturn( ret );
}

//////////////// Action Handlers (Shutter) /////////////////
//

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetShutterPosition(open);
      if (ret != DEVICE_OK)
         return ret;
      if (open)
         pProp->Set((long)1);
      else
         pProp->Set((long)0);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      if (state == 1)
         return SetShutterPosition(true);
      else if (state == 0)
         return SetShutterPosition(false);
      else
         return ERR_INVALID_SHUTTER_STATE;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// TTL I/O
///////////////////////////////////////////////////////////////////////////////

TTL::TTL(int index) : 
   name_("Undefined"), 
   ttlNumber_(0), 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer received from the device");
   SetErrorText(ERR_INVALID_SHUTTER_STATE, "Shutter state can only be 0 or 1");
   SetErrorText(ERR_INVALID_SHUTTER_NUMBER, "The Oasis controllers can offer between 1 and 4 shutters");
   SetErrorText(ERR_SHUTTER_COMMAND_FAILED, "Error while sending a command to the shutter");
   SetErrorText(ERR_DEVICE_CHANGE_NOT_ALLOWED, "Device number can not be changed");
   SetErrorText(ERR_SHUTTER_USED, "A shutter with this device and shutter number is already in use");
   SetErrorText(ERR_NO_CONTROLLER, "Please add the Oasis device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Description
   CreateProperty(MM::g_Keyword_Description, "Oasis TTL Output", MM::String, true);

   ttlNumber_ = index - 1;

   switch(index)
   {
       case 1: name_ = g_TTL1; break;
       case 2: name_ = g_TTL2; break;
       case 3: name_ = g_TTL3; break;
       case 4: name_ = g_TTL4; break;
   }

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   UpdateStatus();

}

TTL::~TTL()
{
   Shutdown();
}

void TTL::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int TTL::Initialize()
{
   int ret;

   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // Make sure that a valid number was used in the constructor:
   if (ttlNumber_ < 0 || ttlNumber_ > 3)
      return ERR_INVALID_SHUTTER_NUMBER;


   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &TTL::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   EnableDelay();
   return DEVICE_OK;
}

int TTL::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool TTL::Busy()
{
   return false;
}

int TTL::SetOpen(bool open)
{
   int pos = (open) ? 1 : 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int TTL::GetOpen(bool& open)
{
   return GetTTL(open);
}
int TTL::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int TTL::SetTTL(bool state)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   BYTE byval = 0;

   ret = OI_ReadIO( &byval );
   if(state)
      byval |= 1 << (ttlNumber_);
   else
      byval &= ~(1 << (ttlNumber_));

   this->LogMessage("TTL::SetTTL " + boost::lexical_cast<std::string>((int)((byval & (1 << ttlNumber_)) > 0)), true);

   ret |= OI_WriteIO( byval );

   return TranslateReturn( ret );
}

int TTL::GetTTL(bool& state)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   int pos = 0;
   BYTE byval = 0;
   int ret = OI_ReadIO( &byval );

    //std::bitset<8> x(byval);
   this->LogMessage("TTL::GetTTL " + boost::lexical_cast<std::string>((int)((byval & (1 << ttlNumber_)) > 0)), true);

   if( byval & (1 << (ttlNumber_)) )
      pos = 1;
   else
      pos = 0;

   state = (pos == 1) ? true : false;

   return TranslateReturn(ret);
}


//////////////// Action Handlers (TTL) /////////////////
//
int TTL::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetTTL(open);
      if (ret != DEVICE_OK)
         return ret;
      if (open)
         pProp->Set((long)1);
      else
         pProp->Set((long)0);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      if (state == 1)
         return SetTTL(true);
      else if (state == 0)
         return SetTTL(false);
      else
         return ERR_INVALID_SHUTTER_STATE;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// DAC
///////////////////////////////////////////////////////////////////////////////

DAC::DAC(int index) :
   name_("Undefined"), 
   initialized_ (false),
   DACPort_(0),
   open_(false),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // Description
   CreateProperty(MM::g_Keyword_Description, "Oasis DAC", MM::String, true);

   DACPort_ = index - 1;

   switch(index)
   {
       case 1: name_ = g_DAC1; break;
       case 2: name_ = g_DAC2; break;
       case 3: name_ = g_DAC3; break;
       case 4: name_ = g_DAC4; break;
   }

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   UpdateStatus();
   
}

DAC::~DAC ()
{
   Shutdown();
}

void DAC::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int DAC::GetSignal(double& volts)
{
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   WORD wDacs[16];
   int ret = OI_ReadDAC( wDacs );

   if( DACPort_ < 0 )
      return DEVICE_ERR;

   volts = wDacs[DACPort_] * 10.0 / (double)(0xFFFF);

   return TranslateReturn(ret);
}


int DAC::SetSignal(double volts)
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }

   //select the right card
   OI_SelectCard(hub->GetBoardID());

   WORD wDacVal;

   wDacVal = (WORD)((volts/10) * 0xFFFF);
   switch(DACPort_)
   {
   case 0:
      ret = OI_SetDAC( 2, wDacVal, 0, 0, 0, 0, 0, 0 );
      return TranslateReturn(ret);
      break;
   case 1:
      ret = OI_SetDAC( 0, 0, 2, wDacVal, 0, 0, 0, 0 );
      return TranslateReturn(ret);
      break;
   case 2:
      ret = OI_SetDAC( 0, 0, 0, 0, 2, wDacVal, 0, 0 );
      return TranslateReturn(ret);
      break;
   case 3:
      ret = OI_SetDAC( 0, 0, 0, 0, 0, 0, 2, wDacVal );
      return TranslateReturn(ret);
      break;
   }
   return DEVICE_OK;   
}


int DAC::Initialize()
{
   int ret;
   Hub* hub = static_cast<Hub*>(GetParentHub());
   if (!hub || !hub->isInitialized())
   {
      return ERR_NO_CONTROLLER;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // Check current intensity of DAC
   ret = GetSignal(volts_);
   if (DEVICE_OK != ret)
      return ret;

   // Voltage
   CPropertyAction* pAct = new CPropertyAction(this, &DAC::OnVoltage);
   CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   SetPropertyLimits("Volts", 0.0, 10.0); // [0..10V]

   // EnableDelay();
   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool DAC::Busy()
{
  return false;
}

int DAC::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int DAC::SetGateOpen(bool open)
{
   int ret = DEVICE_OK;

   if (open)
   {
      ret = SetSignal(volts_);
      open_ = true;
   }
   else
   {
      ret = SetSignal(0);
      open_ = false;
   }

   return ret;
}

int DAC::GetGateOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int DAC::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetGateOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         ret = this->SetGateOpen(true);
      }
      else
      {
         ret = this->SetGateOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int DAC::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (open_)
      {
         int ret = GetSignal(volts_);
         if (ret != DEVICE_OK)
            return ret;
      } else {
         // gate is closed.  Return the cached value
         // TODO: check if user changed voltage
      }
      pProp->Set(volts_);
   }
   else if (eAct == MM::AfterSet)
   {
      double intensity;
      pProp->Get(intensity);
      volts_ = intensity;
      if (open_)
      {
         int ret = SetSignal(volts_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   return DEVICE_OK;
}

