///////////////////////////////////////////////////////////////////////////////
// FILE:          Ludl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Ludl device adapter.
//                
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 4/12/2007, XYStage and ZStage by Nenad Amdoaj
//                automatic device detection by Karl Hoover
//
// COPYRIGHT:     University of California, San Francisco, 2007
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

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif

#include "Ludl.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"

#include <cstdio>
#include <math.h>

// XYStage
const char* g_XYStageDeviceName = "XYStage";

// single axis stage
const char* g_StageDeviceName = "Stage";

const char* g_Controller = "LudlController";
const char* g_Shutter = "LudlShutter";
const char* g_Wheel = "LudlWheel";
const char* g_Ludl_Reset = "Reset";
const char* g_Ludl_Version = "Version";
const char* g_Ludl_TransmissionDelay = "TransmissionDelay";
const char* g_Ludl_Axis_Id = "LudlSingleAxisName";
const char* g_CommandLevelHigh = "High";
const char* g_CommandLevelLow = "Low";
const char* g_Device_Number_Shutter = "LudlDeviceNumberShutter";
const char* g_Device_Number_Wheel = "LudlDeviceNumberWheel";
const char* g_Shutter_Number = "LudlShutterNumber";
const char* g_Wheel_Number = "LudlWheelNumber";
const char* g_Wheel_Nr_Pos = "Fiter Positions";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_Controller, MM::GenericDevice, "Ludl Controller");
   RegisterDevice(g_Shutter, MM::ShutterDevice, "Ludl Shutter");
   RegisterDevice(g_Wheel, MM::StateDevice, "Ludl Filter Wheel");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY Stage");
   RegisterDevice(g_StageDeviceName, MM::StageDevice, "Single Axis Stage");
}                                                                            
                                                                             
MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_Controller) == 0)
   {
      Hub* hub = new Hub();
      Peripheral::SetHubToUseForNewPeripherals(hub);
      return hub;
   }
   else if (strcmp(deviceName, g_Shutter) == 0 )
   {
      return new Shutter();
   }
   else if (strcmp(deviceName, g_Wheel) == 0)
   {
      return new Wheel();
   }
   if (strcmp(deviceName, g_XYStageDeviceName) == 0)                         
   {                                                                         
      XYStage* pXYStage = new XYStage();                                     
      return pXYStage;                                                       
   }
   else if (strcmp(deviceName, g_StageDeviceName) == 0)
   {
      return new Stage();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   Peripheral::UnsetHubToUseForNewPeripherals(pDevice);
   delete pDevice;
}

// TODO: fold the following globals into a class

/*
 * Global Utility function for communication with the controller
 */
int clearPort(MM::Device& device, MM::Core& core, const char* port)
{
   // Clear contents of serial port
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port, clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

/*
 * Change command level, 65 = high command set, 66 = low command set
 */
int changeCommandLevel(MM::Device& device, MM::Core& core, const char* commandLevel,
      const char* port)
{
   int level;
   if (strcmp(commandLevel, g_CommandLevelHigh) == 0)
      level = 65;
   else if (strcmp(commandLevel, g_CommandLevelLow) == 0)
      level = 66;
   else 
      return ERR_INVALID_COMMAND_LEVEL;
   
   if (!port || strlen(port) == 0)
      return ERR_NO_CONTROLLER;

   const unsigned cmdLen = 2;
   unsigned char cmd[cmdLen];
   cmd[0] = (unsigned)255;
   cmd[1] = (unsigned char)level;
   int ret = core.WriteToSerial(&device, port, cmd, cmdLen);
   if (ret !=DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
  
///////////////////////////////////////////////////////////////////////////////
// Ludl Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   transmissionDelay_(10),
   initialized_(false)
{
   for (int i = 0; i < nrDevicesPerController; ++i)
   {
      for (int j = 0; j < nrShuttersPerDevice; ++j)
         shuttersUsed_[i][j] = false;
      for (int j = 0; j < nrWheelsPerDevice; ++j)
         wheelsUsed_[i][j] = false;
   }

   InitializeDefaultErrorMessages();

   // custom error messages:
   SetErrorText(ERR_INVALID_COMMAND_LEVEL, "Internal error: command level is either 65 (high) or 66(low)");
   SetErrorText(ERR_NO_ANSWER, "No answer from the controller.  Is it connected?");
   
   // pre-initialization properties

   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
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

bool Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Hub::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {
      std::string transformed = port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "2");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "600.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "11.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         std::string v;
         int qvStatus = this->QueryVersion(v);
         LogMessage(std::string("version : ")+v, true);
         if( DEVICE_OK != qvStatus )
         {
            LogMessageCode(qvStatus,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "2000.0");
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}

int Hub::QueryVersion(std::string& version)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "VER";
   returnStatus = SendSerialCommand(port_.c_str(), cm, "\r");
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\n", version);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (version.length() < 2) {
      // if we get no answer, try setting the controller to high command level and retry
      LogMessage("Attempt setting controller to 'high' command level",true);

      returnStatus = changeCommandLevel(*this, *GetCoreCallback(), g_CommandLevelHigh,
            port_.c_str());
      if (returnStatus != DEVICE_OK)
         return returnStatus;
   }
   if (version.length() < 2 || version[1] == 'N') {
      // try getting version again (TODO: refactor this!)
      PurgeComPort(port_.c_str()); 
      
      // Version of the controller:
      const char* cm = "VER";
      returnStatus = SendSerialCommand(port_.c_str(), cm, "\r");
      if (returnStatus != DEVICE_OK) 
         return returnStatus;

      returnStatus = GetSerialAnswer(port_.c_str(), "\n", version);
      if (returnStatus != DEVICE_OK) 
         return returnStatus;
      if (version.length() < 2) {
         // no answer, 
         return ERR_NO_ANSWER;
      }
   }

   // there is still a :A in the buffer that we should read away:
   std::string ignoreThis;
   (void)GetSerialAnswer(port_.c_str(), "\n", ignoreThis);
   // When the controller answers with :N -1, ignore

   return returnStatus;
}



int Hub::Initialize()
{
   clearPort(*this, *GetCoreCallback(), port_.c_str());

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_Controller, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Ludl Controller (Mac 2000/5000)", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Make sure controller uses high level command set
   ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh,
         GetPort().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Version of the controller:
   std::string result;
   ret = QueryVersion(result);
   if( DEVICE_OK != ret)
      return ret;

   // Create read-only property with version info
   ret = CreateProperty(g_Ludl_Version, result.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   // Interface to a hard reset of the controller
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnReset);
   ret = CreateProperty(g_Ludl_Reset, "Operate", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Ludl_Reset, "Operate");
   AddAllowedValue(g_Ludl_Reset, "Reset");
   
   // Transmission Delay, Delay between chars send by controller (number * 0.5 msec)
   pAct = new CPropertyAction(this, &Hub::OnTransmissionDelay);
   ret = CreateProperty(g_Ludl_TransmissionDelay, "4", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Get description of attached modules
   const char* cmd = "Rconfig";
   ret = SendSerialCommand(port_.c_str(), cmd, "\r");
   if (ret != DEVICE_OK) 
      return ret;

   // Read out result
   result = "";
   ret = GetSerialAnswer(port_.c_str(), ":", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result.length() < 1)
      return ERR_NO_ANSWER;
   // Looks like there are controllers that start with ':'
   if (result.length() == 1) 
   {
      result = "";
      ret = GetSerialAnswer(port_.c_str(), ":", result);
      if (ret != DEVICE_OK) 
         return ret;
      if (result.length() < 1)
         return ERR_NO_ANSWER;
   }

   LogMessage (result.c_str());

   result = "A module with this ID was not found.  Use any of the following Dev. Addresses: \n" + result;
   SetErrorText(ERR_MODULE_NOT_FOUND, result.c_str());

   // There is still a line ending that should be read away:
   ret = GetSerialAnswer(port_.c_str(), "\n", result);
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

   return DEVICE_OK;
}



void Hub::QueryPeripheralInventory()
{
   discoverableDevices_.clear();
   inventoryDeviceAddresses_.clear();
   inventoryDeviceIDs_.clear();

      std::string v;
      QueryVersion(v);

      clearPort(*this, *GetCoreCallback(), port_.c_str());

      // Get description of attached modules
      const char* cmd = "Rconfig";
      int ret;
      ret = SendSerialCommand(port_.c_str(), cmd, "\r");
      if (ret != DEVICE_OK) 
         return;

      // Read out report
      std::string report;
      report = "";
      ret = GetSerialAnswer(port_.c_str(), ":", report);
      if (ret != DEVICE_OK) 
         return;
      if (report.length() < 1)
         return;
      // Looks like there are controllers that start with ':'
      if (report.length() == 1) 
      {
         report = "";
         ret = GetSerialAnswer(port_.c_str(), ":", report);
         if (ret != DEVICE_OK) 
            return;
         if (report.length() < 1)
            return;
      }

      LogMessage(std::string("Inventory report:: ")+ report, false);

   /* from MAC200 PDF
   Modul-Id   Address  Label  Description 
    
     X  1  EMOT  stage X axis 
     Y  2  EMOT  stage Y axis 
     B  3  EMOT  aux axis 
     R  4  EMOT  aux axis 
     C  5  EMOT  aux axis 
     Z  6  EMOT  aux axis 
     T  7  EMOT  aux axis 
    
     I  9 & 8  EDAIO  digital in ports 
     O  9 & 8  EDAIO  digital out ports 
    
     F  11  EAFC  auto focus finder 
   P[1]  12  HPHCD           photometer No. 1 
     P2  13  HPHCD           photometer No. 2 
     P3  14  HPHCD           photometer No. 3 
     P4  15  HPHCD           photometer No. 4 
     P5  16  HPHCD           photometer No. 5 
    
   S[1]  17  EFILS  filter shutter No. 1 
     S2  18  EFILS  filter shutter No. 2 
     S3  19  EFILS  filter shutter No. 3 
     S4  20  EFILS  filter shutter No. 4 
     S5  21  EFILS  filter shutter No. 5 
   */

      /* from a MAC 5000 i see the following kind of report:

      "

Dev Addres   Label   Id   Description 

    1       EMOT    X    X axis stage        MCMSE
    2       EMOT    Y    Y axis stage        MCMSE
   17       EFILS   S    S Filter Shutter    FWSHC

      "

   I don't know why in the world the filter wheel token appears on the shutter device entry!
      */
      std::string shutterToken = "EFILS";
      std::string wheelToken = "FWSHC";
      std::string motorToken = "EMOT"; // Mac2000 x,y, or 'aux'
     
      std::stringstream ireport(report);
      std::string line;
      while( std::getline(ireport, line))
      {
         if( 0 < line.length())
         {
            std::vector<std::string> tokens;
            // tokenize the line
            std::stringstream iline(line);
            std::string word;
            while (iline >> word)
               tokens.push_back(word);
           
            bool numericIdAtStart = false;
            if( 0 < tokens.size())
            {
               if( 0 < tokens.at(0).length())
               {
                  numericIdAtStart = true;
                  for( std::string::iterator ii = tokens.at(0).begin(); tokens.at(0).end()!=ii; ++ii)
                  {
                     if(!isdigit(*ii))
                     {
                        numericIdAtStart = false;
                        break;
                     }
                  }
               }
            }
            if( numericIdAtStart)  // parse the inventory line
            {
               std::stringstream ids(tokens.at(0));
               // for MAC2000 this is on [1,21]
               int deviceID;
               ids >> deviceID;
               // if it's a Y axis assume it's controlled together with the X axis
               if (2==deviceID)
                  continue;
               std::vector<std::string>::iterator itt = tokens.begin();
               for(itt++ ; itt != tokens.end(); ++itt)
               {
                  bool validEntry = false;
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
                  if(0 == (*itt).compare(motorToken))
                  {
                     if (1 == deviceID) // if we find the X axis, say we have an XY stage
                     {
                        validEntry = true;
                        discoverableDevices_.push_back(g_XYStageDeviceName);
                     }
                     else if( 6 == deviceID)
                     {
                        validEntry = true;
                        discoverableDevices_.push_back(g_StageDeviceName);
                     }
                  }
                  if(validEntry)
                  {
                     inventoryDeviceAddresses_.push_back((const short) deviceID);
                     char ID = '?';
                     if ( (itt+1) != tokens.end())
                     {
                        ID = (itt+1)->at(0);
                     }
                     inventoryDeviceIDs_.push_back( ID);
                     break;
                  }
               }
            }
         }
      }
}

int Hub::GetNumberOfDiscoverableDevices()
{
   QueryPeripheralInventory();
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

   // the LUDL controller always reports an integer 'device number' and char 'id'
   // assume client has always already called QueryPeripheralInventory...
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

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}

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


// Issue a hard reset of the Ludl Controller and installed controller cards
int Hub::OnReset(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      string request;
      pProp->Get(request);
      if (request == "Reset")
      {
         // Send the Reset Command
         const char* cmd = "Remres";
         int ret = SendSerialCommand(port_.c_str(), cmd, "\r");
         if (ret !=DEVICE_OK)
            return ret;
         // TODO: Do we need to wait until the reset is completed?
      }
   }
   return DEVICE_OK;
}
         
// Sets the transmission delay that th econtroller uses in between bytes
int Hub::OnTransmissionDelay(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      string cmd = "TRXDEL";
      int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r");
      if (ret !=DEVICE_OK)
         return ret;

      string result;
      ret = GetSerialAnswer(port_.c_str(), "\n", result);
      if (ret != DEVICE_OK) 
         return ret;
      if (result[1] == 'N')  { // error, try once more
         ret = SendSerialCommand(port_.c_str(), cmd.c_str(), "\r");
         if (ret !=DEVICE_OK)
            return ret;
         ret = GetSerialAnswer(port_.c_str(), "\n", result);
         if (ret != DEVICE_OK)
            return ret;
      }

      if (result[1] != 'A' && result[0]!='A') 
         return ERR_UNRECOGNIZED_ANSWER;

      // tokenize on space
      stringstream ss(result);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      if (tokens.size() > 1)
         transmissionDelay_ = atoi(tokens.at(1).c_str());
      // TODO: check that this value makes sense...

      pProp->Set((double)transmissionDelay_);
   }
   else if (pAct == MM::AfterSet)
   {
      double tmp;
      pProp->Get(tmp);
      if (tmp < 1) 
         tmp = 1;
      if (tmp > 255)
         tmp = 255;
      ostringstream  cmd;
      cmd << "TRXDEL " << tmp;
      int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
      if (ret !=DEVICE_OK)
         return ret;
      transmissionDelay_ = (int) tmp;

      // Read away the confirmation. 
      string resp;
      ret = GetSerialAnswer(port_.c_str(), "\n", resp);
      if (ret != DEVICE_OK) 
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
   }
   return DEVICE_OK;
}


bool
Hub::IsShutterInUse(int deviceNumber, int shutterNumber) const
{
   return shuttersUsed_[deviceNumber - 1][shutterNumber - 1];
}


bool
Hub::IsWheelInUse(int deviceNumber, int wheelNumber) const
{
   return wheelsUsed_[deviceNumber - 1][wheelNumber - 1];
}


void
Hub::SetShutterInUse(int deviceNumber, int shutterNumber, bool inUse)
{
   shuttersUsed_[deviceNumber - 1][shutterNumber - 1] = inUse;
}


void
Hub::SetWheelInUse(int deviceNumber, int wheelNumber, bool inUse)
{
   wheelsUsed_[deviceNumber - 1][wheelNumber - 1] = inUse;
}


//
// Peripheral Mixin
//

Hub* Peripheral::lastCreatedHub_s = 0;


std::string
Peripheral::GetPort() const
{
   return hub_ ? hub_->GetPort() : std::string();
}


bool
Peripheral::IsShutterInUse(int deviceNumber, int shutterNumber) const
{
   if (hub_)
      return hub_->IsShutterInUse(deviceNumber, shutterNumber);
   return false;
}


bool
Peripheral::IsWheelInUse(int deviceNumber, int wheelNumber) const
{
   if (hub_)
      return hub_->IsWheelInUse(deviceNumber, wheelNumber);
   return false;
}


void
Peripheral::SetShutterInUse(int deviceNumber, int shutterNumber, bool inUse)
{
   if (hub_)
      hub_->SetShutterInUse(deviceNumber, shutterNumber, inUse);
}


void
Peripheral::SetWheelInUse(int deviceNumber, int wheelNumber, bool inUse)
{
   if (hub_)
      hub_->SetWheelInUse(deviceNumber, wheelNumber, inUse);
}


///////////////////////////////////////////////////////////////////////////////
// Wheel 
/////// 

Wheel::Wheel() : 
   name_("Undefined"), 
   pos_(1),
   initialized_(false), 
   deviceNumber_(1),
   moduleId_(17),
   wheelNumber_(1),
   answerTimeoutMs_(1000),
   wheelHomeTimeoutS_(10),
   numPos_(6)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer recived from the device");
   SetErrorText(ERR_INVALID_ID, "Ludl Module ID is a number between 0 and 20");
   SetErrorText(ERR_INVALID_WHEEL_NUMBER, "There can be 1 or 2 filterwheels in each Ludl module");
   SetErrorText(ERR_NO_CONTROLLER, "Please add the LudlController device first!");
   SetErrorText(ERR_INVALID_WHEEL_POSITION, "Invalid filter position requested.  Positions 1 through 6 are valid");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");
   SetErrorText(ERR_WHEEL_HOME_FAILED, "Failed to Home the filter wheel.");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl filter wheel adapter", MM::String, true);

   // Device Number
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnDeviceNumber);
   int ret = CreateProperty(g_Device_Number_Wheel, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Device_Number_Wheel, "1");
   AddAllowedValue(g_Device_Number_Wheel, "2");
   AddAllowedValue(g_Device_Number_Wheel, "3");
   AddAllowedValue(g_Device_Number_Wheel, "4");
   AddAllowedValue(g_Device_Number_Wheel, "5");

   // Wheel Number
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnWheelNumber);
   ret = CreateProperty(g_Wheel_Number, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Wheel_Number, "1");
   AddAllowedValue(g_Wheel_Number, "2");

   // Number of positions in the wheel
   // -----
   pAct = new CPropertyAction (this, &Wheel::OnWheelNrPos);
   ret = CreateProperty(g_Wheel_Nr_Pos, "6", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Wheel_Nr_Pos, "6");
   AddAllowedValue(g_Wheel_Nr_Pos, "10");

   // Wait this long for wheel to time out:
   pAct = new CPropertyAction (this, &Wheel::OnWheelHomeTimeout);
   ret = CreateProperty("Home-Timeout-(s)", "10", MM::Float, false, pAct, true);
   /*
   // device ID for the EFILS
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnID);
   CreateProperty("ID", "17", MM::Integer, false, pAct, true);
   */

   // UpdateStatus();

}

Wheel::~Wheel()
{
   SetWheelInUse(deviceNumber_, wheelNumber_, false);
   Shutdown();
}

void Wheel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Wheel::Initialize()
{
   // Make sure that the combination of Shutter# and Device# is unique
   if (IsWheelInUse(deviceNumber_, wheelNumber_))
      return ERR_WHEEL_USED;
   else
      SetWheelInUse(deviceNumber_, wheelNumber_, true);

   // Make sure that a valid number was used in the constructor:
   if (wheelNumber_ < 1 || wheelNumber_ > 2)
      return ERR_INVALID_WHEEL_NUMBER;

   // Make sure controller uses high level command set
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh,
         GetPort().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Wheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

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
   clearPort(*this, *GetCoreCallback(), GetPort().c_str());
   // This checks all device in the module, not only our Wheel
   const char* cmd = "STATUS S"; 
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK) // Bad, but what else can we do?
      return false;

   unsigned long read = 0;
   unsigned char reply;
   unsigned long startTime = GetClockTicksUs();
   do {
      if (DEVICE_OK != ReadFromComPort(GetPort().c_str(), &reply, 1, read))
         return false;
   }
   while(read==0 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (ret != DEVICE_OK || (read != 1) ) // Error detected: we are probably busy
      return true;

   if (reply == 'N')
      return false;
   else if (reply == 'B')
      return true;
   
   // error
   return true;
}

/**
 * Homes the wheel
 */
int Wheel::HomeWheel()
{
   ostringstream  cmd;
   cmd << "Rotat S" << deviceNumber_ << " ";
   if (wheelNumber_==1)
      cmd << "M ";
   else
      cmd << "A ";
   cmd << "H";
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret !=DEVICE_OK)
      return ret;

   // Check that the command was valid
   string result;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result[1] != 'A') 
      return ERR_WHEEL_HOME_FAILED;

   // now wait till the wheel homed or 3 seconds
   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime maxWait(wheelHomeTimeoutS_ * 1000000);
   while ( (GetCurrentMMTime() - startTime) < maxWait)
   {
      if (!Busy())
         return DEVICE_OK;
      CDeviceUtils::SleepMs(200);
   }

   return DEVICE_OK;
}
   

/**
 * Sets wheel position
 */
int Wheel::SetWheelPosition(int position)
{
   int pos = position;
   // 10 position Ludl Wheel needs 0 to move to position 10
   if (position == 10) 
   {
      pos = 0;
   }
   
   ostringstream  cmd;
   cmd << "Rotat S" << deviceNumber_ << " ";
   if (wheelNumber_==1)
      cmd << "M ";
   else
      cmd << "A ";
   cmd << pos;
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret !=DEVICE_OK)
      return ret;

   // Check that the command was valid
   string result;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result[1] != 'A') 
      return ERR_WHEEL_POSITION_FAILED;

   pos_ = position;
   
   return DEVICE_OK;
}

/**
 * Ask controller about filter position
 */
int Wheel::GetWheelPosition(int& position)
{
   // For now, we use a cached value
   position = pos_;

   return DEVICE_OK;
}


//////////////// Action Handlers (Wheel) /////////////////
//
int Wheel::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)moduleId_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      if (id >= 0 && id <= 20)
         moduleId_ = (unsigned) id;
      else 
         return ERR_INVALID_ID;

      // To check that there is a controller with this ID, check whether the busy check works
      // Only do this check when we have an por
      if (!GetPort().empty()) {
         clearPort(*this, *GetCoreCallback(), GetPort().c_str());
         const int cmdLength = 2;
         char cmd[cmdLength];
         cmd[0] = (char)moduleId_;
         cmd[1] = (char)63;
         int ret = SendSerialCommand(GetPort().c_str(), cmd, ":");
         if (ret != DEVICE_OK)
            return ERR_MODULE_NOT_FOUND;

         string result;
         ret = GetSerialAnswer(GetPort().c_str(), ":", result);
         if (ret != DEVICE_OK) 
            return ERR_MODULE_NOT_FOUND;

         if (result.size() < 1) 
            return ERR_MODULE_NOT_FOUND;

         if (!(result.c_str()[0] == 66 || result.c_str()[0] == 98))
            return ERR_MODULE_NOT_FOUND;
      }
   }

   return DEVICE_OK;
}

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


int Wheel::OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)deviceNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set((double)deviceNumber_);
         return ERR_DEVICE_CHANGE_NOT_ALLOWED;
      }
      long tmp;
      pProp->Get(tmp);
      // There are between a and 5 shutter/filterwheel controllers in a Mac5000
      if (tmp > 0  && tmp <= 5)
      {
         deviceNumber_ = tmp;
      }
      else
      {
         // return
         pProp->Set((long)deviceNumber_);
         return ERR_INVALID_DEVICE_NUMBER;
      }
   }

   return DEVICE_OK;
}

int Wheel::OnWheelNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)wheelNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      long tmp;
      pProp->Get(tmp);
      wheelNumber_ = tmp;
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
// ~~~~~~~

Shutter::Shutter() : 
   name_(g_Shutter), 
   shutterNumber_(1), 
   initialized_(false), 
   deviceNumber_(1),
   moduleId_(17),
   answerTimeoutMs_(1000),
   changedTime_ (0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Unrecognized answer received from the device");
   SetErrorText(ERR_INVALID_ID, "Ludl Module ID is a number between 0 and 20");
   SetErrorText(ERR_INVALID_SHUTTER_STATE, "Shutter state can only be 0 or 1");
   SetErrorText(ERR_INVALID_SHUTTER_NUMBER, "There can be between 1 and 3 shutters in every Ludl module");
   SetErrorText(ERR_INVALID_DEVICE_NUMBER, "There can be between 1 and 5 shutter/filter wheel controllers in a MAC5000");
   SetErrorText(ERR_SHUTTER_COMMAND_FAILED, "Error while sending a command to the shutter");
   SetErrorText(ERR_DEVICE_CHANGE_NOT_ALLOWED, "Device number can not be changed");
   SetErrorText(ERR_SHUTTER_USED, "A shutter with this device and shutter number is already in use");
   SetErrorText(ERR_NO_CONTROLLER, "Please add the LudlController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl shutter adapter", MM::String, true);

   // Device Number
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnDeviceNumber);
   int ret = CreateProperty(g_Device_Number_Shutter, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Device_Number_Shutter, "1");
   AddAllowedValue(g_Device_Number_Shutter, "2");
   AddAllowedValue(g_Device_Number_Shutter, "3");
   AddAllowedValue(g_Device_Number_Shutter, "4");
   AddAllowedValue(g_Device_Number_Shutter, "5");

   // Shutter Number
   // -----
   pAct = new CPropertyAction (this, &Shutter::OnShutterNumber);
   ret = CreateProperty(g_Shutter_Number, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_Shutter_Number, "1");
   AddAllowedValue(g_Shutter_Number, "2");
   AddAllowedValue(g_Shutter_Number, "3");

   UpdateStatus();

}

Shutter::~Shutter()
{
   SetShutterInUse(deviceNumber_, shutterNumber_, false);
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   // Make sure that the combination of Shutter# and Device# is unique
   if (IsShutterInUse(deviceNumber_, shutterNumber_))
      return ERR_SHUTTER_USED;
   else
      SetShutterInUse(deviceNumber_, shutterNumber_, true);

   // Make sure that a valid number was used in the constructor:
   if (shutterNumber_ < 1 || shutterNumber_ > 3)
      return ERR_INVALID_SHUTTER_NUMBER;

   // Make sure controller uses high level command set
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh,
         GetPort().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   /*
   // NOTE: Can not add allowed values to numeric fields!
   //AddAllowedValue(MM::g_Keyword_State, "0");
   //AddAllowedValue(MM::g_Keyword_State, "1");

   // TODO: Figure out why Labels can not be added:
    
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
   // First check delay, then query controller
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs()))
         return true;

   clearPort(*this, *GetCoreCallback(), GetPort().c_str());
   // This checks all device in the module, not only our Shutter
   const char* cmd = "STATUS S"; 
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK) // This is bad, but what else can we do?
      return false;

   unsigned long read = 0;
   unsigned char reply;
   unsigned long startTime = GetClockTicksUs();
   do {
      if (DEVICE_OK != ReadFromComPort(GetPort().c_str(), &reply, 1, read))
         return false;
   }
   while(read==0 && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (ret != DEVICE_OK || (read != 1) ) // Error detected: we are probably busy
      return true;

   if (reply == 'N')
      return false;
   else if (reply == 'B')
      return true;
   
   // error
   return true;
}

int Shutter::SetOpen(bool open)
{
   changedTime_ = GetCurrentMMTime();
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
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
   ostringstream  cmd;
   if (state)
      cmd << "OPEN S";
   else
      cmd << "CLOSE S";
   cmd << deviceNumber_ << ' ' << shutterNumber_;
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret !=DEVICE_OK)
      return ret;

   // Check that the command was valid
   string result;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", result);
   if (ret != DEVICE_OK) 
      return ret;
   if (result[1] != 'A') 
      return ERR_SHUTTER_COMMAND_FAILED;

   return DEVICE_OK;
}


/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   clearPort(*this, *GetCoreCallback(), GetPort().c_str());
   
   // request shutter status
   ostringstream  cmd;
   cmd << "RDSTAT S" << deviceNumber_ << ' ';
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret !=DEVICE_OK)
      return ret;

   // get result and interpret
   string result;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", result);
   if (ret != DEVICE_OK)
      return ret;

   if (result.size() < 1)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   int x = atoi(result.substr(2,64).c_str());
   int mask = 2;
   mask = mask << shutterNumber_;
   if ( (x & mask) == 0)
      state = false;
   else
      state = true;

   return DEVICE_OK;
}

//////////////// Action Handlers (Shutter) /////////////////
//
int Shutter::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)moduleId_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      if (id >= 0 && id <= 20)
         moduleId_ = (unsigned) id;
      else 
         return ERR_INVALID_ID;
   }

   return DEVICE_OK;
}

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

int Shutter::OnDeviceNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)deviceNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set((long)deviceNumber_);
         return ERR_DEVICE_CHANGE_NOT_ALLOWED;
      }
      long tmp;
      pProp->Get(tmp);
      // There are between a and 5 shutter/filterwheel controllers in a Mac5000
      if (tmp > 0  && tmp <= 5)
      {
         deviceNumber_ = (unsigned int)tmp;
      }
      else
      {
         // return
         pProp->Set((long)deviceNumber_);
         return ERR_INVALID_DEVICE_NUMBER;
      }
   }

   return DEVICE_OK;
}

int Shutter::OnShutterNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)shutterNumber_);
   }
   else if (eAct == MM::AfterSet)
   {
      double shutterNumber;
      pProp->Get(shutterNumber);
      shutterNumber_ = (int) shutterNumber;
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
 * Then, there is the Ludl native system.  All functions using 'steps' use the Ludl system
 * All functions using Um use the Micro-Manager coordinate system
 */
XYStage::XYStage() :
   initialized_(false), 
   stepSizeUm_(0.1), 
   stepSizeXUm_(0.1), 
   stepSizeYUm_(0.1), 
   speed_(2500.0),
   startSpeed_(500.0),
   accel_(75),
   idX_(1), 
   idY_(2),
   originX_(0),
   originY_(0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup
   SetErrorText(ERR_NO_CONTROLLER, "Please add the LudlController device first!");

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Ludl XY stage driver adapter", MM::String, true);

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
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   // Make sure controller uses high level command set
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh,
         GetPort().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSize);
   ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   ret = CreateProperty("StepSize-X", "0.1", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   ret = CreateProperty("StepSize-Y", "0.1", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed (in um/sec)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeed);
   // TODO: get current speed from the controller
   ret = CreateProperty("Speed", "2500.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Initial Speed (in um/sec) (i.e. lowest speed)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnStartSpeed);
   // TODO: get current speed from the controller
   ret = CreateProperty("StartSpeed", "500.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Accel (Acceleration is a number between 1 and 255, lower numbers give shorter ramp time, i.e. faster acceleration
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccel);
   // TOD: get current Aceeleration from the controller
   ret = CreateProperty("Acceleration", "100", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   /*  Probably not a good idea to reset the origin every time - Bruno Alfonso did not like it.
   ret = SetAdapterOrigin();
   if (ret != DEVICE_OK)
      return ret;
*/

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

bool XYStage::Busy()
{
   if (AxisBusy("X") || AxisBusy("Y"))
      return true;
   return false;
}

/**
 * Returns true if any axis (X or Y) is still moving.
 */
bool XYStage::AxisBusy(const char* axis)
{
    clearPort(*this, *GetCoreCallback(), GetPort().c_str());
   // format the command
   //const char* cmd = "STATUS X Y";
   ostringstream cmd;
   cmd << "STATUS " << axis;
   // send command
 
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   // Poll for the response to the Busy status request
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 5;
   this->LogMessage("Starting read in XY-Stage Busy", true);
   do {
      ret = ReadFromComPort(GetPort().c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in XYStage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(os.str().c_str(), true);

   if (status == 'B')
      return true;
   else
      return false;
}


/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   // format the command
   ostringstream cmd;
   cmd << "MOVE ";
   cmd << "X=" << x << " ";
   cmd << "Y=" << y ;

   // TODO: what if we are busy???

   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}
  
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long x, long y)
{
   // format the command
   ostringstream cmd;
   cmd << "MOVREL ";
   cmd << "X=" << x << " ";
   cmd << "Y=" << y ;

   // TODO: what if we are busy???

   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   PurgeComPort(GetPort().c_str());
   
   // format the command
   const char* cmd = "WHERE X Y";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
   {
      is >> x;
      is >> y;
      return DEVICE_OK; // success!
   }

   // figure out the error code
   int code;
   is >> code;
   return code;
}

/**
 * Defines current position as origin (0,0) coordinate of the controller.
 */
int XYStage::SetOrigin()
{
   PurgeComPort(GetPort().c_str());

   // format the command
   const char* cmd = "HERE X=0 Y=0";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   //TODO parse the answer and return error code
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);

   return SetAdapterOrigin();
}

/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 * The Ludl stage X axis is the same orientation as out coordinate system, the Y axis is reversed
 */
int XYStage::SetAdapterOrigin()
{
   long xStep, yStep;
   int ret = GetPositionSteps(xStep, yStep);
   if (ret != DEVICE_OK)
      return ret;
   originX_ = xStep * stepSizeXUm_;
   originY_ = yStep * stepSizeYUm_;

   return DEVICE_OK;
}


int XYStage::Home()
{
   PurgeComPort(GetPort().c_str());

   // format the command
   const char* cmd = "HOME X Y";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   //TODO:  set a separate time-out since it can take a long time for the home command to complete
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=200;
   long pollIntervalMs = 50;
   this->LogMessage("Starting read in XY-Stage HOME\n", true);
   do {
      ret = ReadFromComPort(GetPort().c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in XYStage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(os.str().c_str());

   //TODO parse the answer and return error code
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   
   return DEVICE_OK;
}


int XYStage::Stop()
{
   PurgeComPort(GetPort().c_str());
   
   // format the command
   const char* cmd = "HALT";

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd, "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   //TODO parse the answer and return error code
   
   return DEVICE_OK;
}


/**
 * Returns the stage position limits in um.
 * TODO: implement!!!
 */
int XYStage::GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a specified command to the controller
 */
int XYStage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   clearPort(*this, *GetCoreCallback(), GetPort().c_str());
   if (DEVICE_OK != SendSerialCommand(GetPort().c_str(), cmd.c_str(), "\r"))
      return DEVICE_SERIAL_COMMAND_FAILED;

   int ret = GetSerialAnswer(GetPort().c_str(), "\n", response);
   if (ret != DEVICE_OK) 
      return ret;
   if (response.length() < 1)
      return ERR_NO_ANSWER;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

/**
 * Deprecated.  Used OnStepSizeXUm and OnStepSizeYUm instead
 */
int XYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeUm_);
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
      // to stay backwards compatible:
      stepSizeXUm_ = stepSize;
      stepSizeYUm_ = stepSize;
   }

   return DEVICE_OK;
}

int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeXUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeXUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeXUm_ = stepSize;
   }

   return DEVICE_OK;
}


int XYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeYUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeYUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeYUm_ = stepSize;
   }

   return DEVICE_OK;
}


/*
 * Speed as returned by device is in pulses per second
 * We convert that to um per second using the factor stepSizeUm)
 */
int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("SPEED X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long speedX = atol(tokens.at(1).c_str());
      // long speedY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      speed_ = speedX * stepSizeUm_;
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      double uiSpeed; // Start Speed in um/sec
      long speed; // Speed in rotations per sec
      pProp->Get(uiSpeed);
      speed = (long)(uiSpeed / stepSizeUm_);
      if (speed < 85 ) speed = 85;
      if (speed > 2764800) speed = 276480;
      string resp;
      ostringstream os;
      os << "SPEED X=" << speed << " Y=" << speed;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      speed_ = speed * stepSizeUm_;
   }
   return DEVICE_OK;
}

int XYStage::OnStartSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("STSPEED X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long speedX = atol(tokens.at(1).c_str());
      // long speedY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      startSpeed_ = speedX * stepSizeUm_;
      pProp->Set(startSpeed_);
   }
   else if (eAct == MM::AfterSet)
   {
      double uiStartSpeed; // this is the Start Speed in um/sec
      long startSpeed; // startSpeed in rotations per sec
      pProp->Get(uiStartSpeed);
      startSpeed = (long)(uiStartSpeed / stepSizeUm_);
      if (startSpeed < 1000 ) startSpeed = 1000;
      if (startSpeed > 2764800) startSpeed = 276480;
      string resp;
      ostringstream os;
      os << "STSPEED X=" << startSpeed << " Y=" << startSpeed;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      startSpeed_ = startSpeed * stepSizeUm_;
   }
   return DEVICE_OK;
}

int XYStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = ExecuteCommand("ACCEL X Y", resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      long accelX = atol(tokens.at(1).c_str());
      // long accelY = atol(tokens.at(2).c_str());
      // TODO, deal with situation when these two are not the same
      accel_ = accelX;
      pProp->Set(accel_);
   }
   else if (eAct == MM::AfterSet)
   {
      long accel;
      pProp->Get(accel);
      if (accel < 1 ) accel = 1;
      if (accel > 255) accel = 255;
      string resp;
      ostringstream os;
      os << "ACCEL X=" << accel << " Y=" << accel;
      int ret = ExecuteCommand(os.str().c_str(), resp);
      if (ret != DEVICE_OK)
         return ret;
      if (resp.substr(0,2) != ":A")
         return ERR_COMMAND_FAILED;
      accel_ = accel;
   }
   return DEVICE_OK;
}


int XYStage::OnIDX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idX_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idX_ = (unsigned) id;
   }

   return DEVICE_OK;
}

int XYStage::OnIDY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)idY_);
   }
   else if (eAct == MM::AfterSet)
   {
      long id;
      pProp->Get(id);
      idY_ = (unsigned) id;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
Stage::Stage() :
   initialized_(false),
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the LudlController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Ludl stage driver adapter", MM::String, true);

   // Axis ID
   id_ = "Z";
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnID);
   CreateProperty(g_Ludl_Axis_Id, id_.c_str(), MM::String, false, pAct, true); 
   AddAllowedValue(g_Ludl_Axis_Id, "X");
   AddAllowedValue(g_Ludl_Axis_Id, "Y");
   AddAllowedValue(g_Ludl_Axis_Id, "Z");
   AddAllowedValue(g_Ludl_Axis_Id, "R");
   AddAllowedValue(g_Ludl_Axis_Id, "T");
   AddAllowedValue(g_Ludl_Axis_Id, "F");
   AddAllowedValue(g_Ludl_Axis_Id, "A");
   AddAllowedValue(g_Ludl_Axis_Id, "B");
   AddAllowedValue(g_Ludl_Axis_Id, "C");
}

Stage::~Stage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{
   // Make sure controller uses high level command set
   int ret = changeCommandLevel(*this,  *GetCoreCallback(), g_CommandLevelHigh,
         GetPort().c_str());
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStepSize);
   ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Autofocus
   // ---------
   if (id_ == "Z")
   {
      // this property only makes sense to the ASI Z stage
      pAct = new CPropertyAction (this, &Stage::OnAutofocus);
      ret = CreateProperty("Autofocus", "5", MM::Integer, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Stage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Stage::Busy()
{
   ostringstream os;
   os << "STATUS " << id_;

   int ret = SendSerialCommand(GetPort().c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in Stage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   // Poll for the response to the Busy status request
   unsigned char status=0;
   unsigned long read=0;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 5;
   this->LogMessage("Starting read in Stage Busy\n", true);
   do {
      ret = ReadFromComPort(GetPort().c_str(), &status, 1, read);
      if (ret != DEVICE_OK)
      {
         ostringstream os;
         os << "ReadFromComPort failed in Stage::Busy, error code:" << ret;
         this->LogMessage(os.str().c_str(), false);
         return false; // Error, let's pretend all is fine
      }
      numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(read==0 && numTries < maxTries); // keep trying up to 2 sec
   ostringstream ds;
   ds << "Tried reading "<< numTries << " times, and finally read " << read << " chars";
   this->LogMessage(ds.str().c_str(), true);

   if (status == 'B')
      return true;
   else
      return false;
}

int Stage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   
   ostringstream os;
   os << id_ << " SetPosition() " << pos;
   this->LogMessage(os.str().c_str());

   return SetPositionSteps(steps);
}

int Stage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;

   ostringstream os;
   os << id_ << " GetPosition() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int Stage::SetPositionSteps(long pos)
{
   // format the command
   ostringstream cmd;
   cmd << "MOVE ";
   cmd << id_ << "=" << pos;

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string answer;
   is >> answer;

   if (answer.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   this->LogMessage(answer.c_str());
   istringstream os(resp.substr(2));
   int code;
   os >> code;
   return code;
}
  
int Stage::GetPositionSteps(long& steps)
{
   // format the command
   ostringstream cmd;
   cmd << "WHERE " << id_;

   // TODO: what if we are busy???
   int ret = SendSerialCommand(GetPort().c_str(), cmd.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = GetSerialAnswer(GetPort().c_str(), "\n", resp);
   if (ret != DEVICE_OK) 
      return ret;

   if (resp.length() < 1)
      return ERR_NO_ANSWER;

   // parse the answer and return the proper error code
   // TODO...

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
   {
      is >> steps;
      return DEVICE_OK; // success!
   }

   // figure out the error code
   int code;
   is >> code;

   return code;
}
  
int Stage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
 
int Stage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

int Stage::ExecuteCommand(const string& cmd, string& response)
{
   // send command
   PurgeComPort(GetPort().c_str());
   if (DEVICE_OK != SendSerialCommand(GetPort().c_str(), cmd.c_str(), "\r"))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned long bufLen = 80;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(GetPort().c_str(),
               reinterpret_cast<unsigned char*>(answer + curIdx),
               bufLen - curIdx, read))
      {
         return DEVICE_SERIAL_COMMAND_FAILED;
      }
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, "\n");
      if (pLF)
         *pLF = 0; // terminate the string
   }
   while(!pLF && (GetClockTicksUs() - startTime) / 1000.0 < answerTimeoutMs_);

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}

/**
 * This may be problematic since it makes sense and works only for a Z-stage.
 * WARNING: it explicitly uses Z stage name and may not be what the user intended to do.
 */
int Stage::Autofocus(long param)
{
   // format the command
   ostringstream cmd;
   cmd << "AF Z=";
   cmd << param ;

   string resp;
   int ret = ExecuteCommand(cmd.str(), resp);
   if (ret != DEVICE_OK)
      return ret;

   istringstream is(resp);
   string outcome;
   is >> outcome;

   if (outcome.compare(":A") == 0)
      return DEVICE_OK; // success!

   // figure out the error code
   int code;
   is >> code;
   return code;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Stage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
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

int Stage::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //const char* id = id_;
      pProp->Set(id_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id_);
      // Only allow axis that we know:
      if (id == "X" || id == "Y" || id == "Z" || id == "R" || id == "T" || id == "F")
         id_ = id;
   }

   return DEVICE_OK;
}

int Stage::OnAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   else if (eAct == MM::AfterSet)
   {
      long param;
      pProp->Get(param);

      return Autofocus(param);
   }

   return DEVICE_OK;
}

