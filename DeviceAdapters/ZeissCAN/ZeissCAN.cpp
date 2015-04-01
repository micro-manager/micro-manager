///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissCAN.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN bus controller, see Zeiss CAN bus documentation
//                
// AUTHOR: Nico Stuurman, 1/16/2006 - 5/14/2006
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


#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ZeissCAN.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>

ZeissHub g_hub;
ZeissTurret g_turret;

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Zeiss Devices
const char* g_ZeissDeviceName = "ZeissScope";
const char* g_ZeissShutter = "ZeissShutter";
const char* g_ZeissShutterMF = "ZeissShutterMFFirmware";
const char* g_ZeissShutterNr = "ZeissShutterNr";
const char* g_ZeissReflector = "ZeissReflectorTurret";
const char* g_ZeissSidePort = "ZeissSidePortTurret";
const char* g_ZeissBasePort = "ZeissBasePortSlider";
const char* g_ZeissObjectives = "ZeissObjectives";
const char* g_ZeissOptovar = "ZeissOptovar";
const char* g_ZeissTubelens = "ZeissTubelens";
const char* g_ZeissCondenser = "ZeissCondenser";
const char* g_ZeissLampMirror = "ZeissExcitationLampSwitcher";
const char* g_ZeissHalogenLamp = "ZeissHalogenLamp";
const char* g_ZeissFocusName = "Focus";
const char* g_ZeissExternal = "External-Internal Shutter";
const char* g_ZeissExtFilterWheel = "ZeissExternalFilterWheel";
const char* g_ZeissFilterWheel1 = "ZeissFilterWheel1";
const char* g_ZeissFilterWheel2 = "ZeissFilterWheel2";
const char* g_ZeissXYStage = "XYStage";
const char* g_ZStage = "ZStage";
const char* g_PhotoModule = "PhotoModule";

// List of Turret numbers (from Zeiss documentation)
int g_ReflectorTurret = 1;
int g_ObjectiveTurret = 2;
int g_ExtFilterWheel = 4;
int g_OptovarTurret = 6;
int g_FilterWheel1 = 7;
int g_FilterWheel2 = 8;
int g_CondenserTurret = 32;
int g_CondenserFrontlens = 34;
int g_TubelensTurret = 36;
int g_BasePortSlider = 38;
int g_SidePortTurret = 39;
int g_LampMirror = 51;

// Property names
const char* g_Keyword_LoadSample = "Load Position";

///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZeissDeviceName, MM::HubDevice, "Zeiss Axiovert 200m controlled through serial interface");
   RegisterDevice(g_ZeissShutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_ZeissShutterMF, MM::ShutterDevice, "ShutterMFFirmware");
   RegisterDevice(g_ZeissReflector, MM::StateDevice, "Reflector Turret (dichroics)");
   RegisterDevice(g_ZeissSidePort, MM::StateDevice, "SidePort switcher");
   RegisterDevice(g_ZeissBasePort, MM::StateDevice, "BasePort Slider");
   RegisterDevice(g_ZeissObjectives, MM::StateDevice, "Objective Turret");
   RegisterDevice(g_ZeissCondenser, MM::StateDevice, "Condenser Turret");
   RegisterDevice(g_ZeissOptovar, MM::StateDevice, "Optovar");
   RegisterDevice(g_ZeissTubelens, MM::StateDevice, "Tubelens");
   RegisterDevice(g_ZeissLampMirror, MM::StateDevice, "Lamp Switcher");
   RegisterDevice(g_ZeissHalogenLamp, MM::ShutterDevice, "Halogen Lamp");
   RegisterDevice(g_ZeissFocusName, MM::StageDevice, "Z-Drive");
   RegisterDevice(g_ZeissExtFilterWheel, MM::StateDevice, "External FilterWheel");
   RegisterDevice(g_ZeissFilterWheel1, MM::StateDevice, "FilterWheel 1");
   RegisterDevice(g_ZeissFilterWheel2, MM::StateDevice, "FilterWheel 2");
   RegisterDevice(g_ZeissXYStage, MM::XYStageDevice, "XY Stage (MCU 28)");
   RegisterDevice(g_ZStage, MM::StageDevice, "Z Stage on Axioskop 2");
   RegisterDevice(g_PhotoModule, MM::StateDevice, "AxioPhot 2 Photo Module");
}

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_ZeissDeviceName) == 0)
        return new ZeissScope();
   else if (strcmp(deviceName, g_ZeissShutter) == 0)
        return new ZeissShutter();
   else if (strcmp(deviceName, g_ZeissShutterMF) == 0)
        return new ZeissShutterMF();
   else if (strcmp(deviceName, g_ZeissReflector) == 0)
        return new ReflectorTurret();
   else if (strcmp(deviceName, g_ZeissSidePort) == 0)
        return new SidePortTurret();
   else if (strcmp(deviceName, g_ZeissBasePort) == 0)
        return new BasePortSlider();
   else if (strcmp(deviceName, g_ZeissObjectives) == 0)
        return new ObjectiveTurret();
   else if (strcmp(deviceName, g_ZeissCondenser) == 0)
        return new CondenserTurret();
   else if (strcmp(deviceName, g_ZeissOptovar) == 0)
        return new OptovarTurret();
   else if (strcmp(deviceName, g_ZeissTubelens) == 0)
        return new TubelensTurret();
   else if (strcmp(deviceName, g_ZeissLampMirror) == 0)
        return new LampMirror();
   else if (strcmp(deviceName, g_ZeissHalogenLamp) == 0)
        return new HalogenLamp();
   else if (strcmp(deviceName, g_ZeissFocusName) == 0)
      return new FocusStage();
   else if (strcmp(deviceName, g_ZeissFilterWheel1) == 0)
      return new FilterWheel(1);
   else if (strcmp(deviceName, g_ZeissFilterWheel2) == 0)
      return new FilterWheel(2);
   else if (strcmp(deviceName, g_ZeissExtFilterWheel) == 0)
      return new FilterWheel(3);
   else if (strcmp(deviceName, g_ZeissXYStage) == 0)
      return new XYStage();
   else if (strcmp(deviceName, g_ZStage) == 0)
      return new ZStage();
   else if (strcmp(deviceName, g_PhotoModule) == 0)
      return new PhotoModule();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}

/////////////
// Interface to the Zeis microscope
//
ZeissHub::ZeissHub() :
   firmware_ ("MF"),
   version_(0.0),
   initialized_ (false)
{
   ClearRcvBuf();
}

ZeissHub::~ZeissHub()
{
}

/**
 * Clears the serial receive buffer.
 */
void ZeissHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/**
 * Reads in version info
 */
int ZeissHub::GetVersion(MM::Device& device, MM::Core& core, std::string& ver)
{
   // get the stand firmware
   const char* command = "HPTv0";
   int ret = ExecuteCommand(device, core,  command);
   if (ret != DEVICE_OK)
      return ret;

   // first two chars should read 'PH'
   string response;
   ret = GetAnswer(device, core, response);
   if (ret != DEVICE_OK)
      return ret;
   if (response.substr(0,2).compare("PH") == 0) 
   {
      // This is a bit crooked, the variable 'firmware_' refers to the focus drive firmware
      ver = "Application version: " + response.substr(2);
      if (response.substr(2,2).compare("AP") == 0)
         firmware_ = "ZM";
      else if (response.substr(2,2).compare("AV") == 0)
         firmware_ = "ZM";
      else
         firmware_ = "MF";
      string tmp = response.substr(6);
      // MF firmware reports: 1.17, ZM firmware reports: 2_09
      if (tmp.find("_", 0) != string::npos)
            tmp.replace(tmp.rfind("_"), 1, ".");
      version_ = atof(tmp.c_str());
   }
   else
      return ERR_UNEXPECTED_ANSWER;
   // TODO report Bios version as well (get it with "FPTv1")

   return DEVICE_OK;
}

/**
 * Sends command to serial port and received anser from microscope
 */
int ZeissHub::ExecuteCommand(MM::Device& device, MM::Core& core, const char* command) 
{     
   // empty the Rx serial buffer before sending command, we'll only send commands one by one
   ClearPort(device, core);
   ClearRcvBuf();

   // send command
   int ret = core.SetSerialCommand(&device, port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   //core.LogMessage(&device, strCommand.c_str(), true);

   return DEVICE_OK;                                                         
}

/**
 * Received answers from the microscope and stores them in rcvBuf_
 */
int ZeissHub::GetAnswer(MM::Device& device, MM::Core& core, string& answer) 
{     
   // get response
   int ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, rcvBuf_, "\r");
   //core.LogMessage(&device, rcvBuf_, true);                                
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   answer = rcvBuf_; 
   return DEVICE_OK;                                                         
}
         
int ZeissHub::ClearPort(MM::Device& device, MM::Core& core)
{
   // Clear contents of serial port 
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
} 



/////////////////////////////////////////////////////////////
// Utility class to make it easier for 'turret-based' devices
// 
ZeissTurret::ZeissTurret() 
{
   groupOne.push_back(g_ReflectorTurret);
   groupOne.push_back(g_ObjectiveTurret);
   groupOne.push_back(g_FilterWheel1);
   groupOne.push_back(g_FilterWheel2);
   groupOne.push_back(g_CondenserTurret);
   groupOne.push_back(g_OptovarTurret);
   groupOne.push_back(g_TubelensTurret);
   groupTwo.push_back(g_BasePortSlider);
   groupTwo.push_back(g_SidePortTurret);
   groupTwo.push_back(34);
   groupTwo.push_back(g_LampMirror);
//   groupTwo.push_back(g_Shutter);
   groupTwo.push_back(g_ExtFilterWheel);
   bitPosition[g_ReflectorTurret] = 0;
   bitPosition[g_ObjectiveTurret] = 1;
   bitPosition[g_FilterWheel1] = 2;
   bitPosition[g_FilterWheel2] = 3;
   bitPosition[g_CondenserTurret] = 4;
   bitPosition[g_OptovarTurret] = 5;
   bitPosition[g_TubelensTurret] = 5;
   bitPosition[g_BasePortSlider] = 0;
   bitPosition[g_SidePortTurret] = 1;
//   bitPosition[g_Shutter] = 4;
   bitPosition[g_LampMirror] = 6;
   bitPosition[g_ExtFilterWheel] = 7;
}

ZeissTurret::~ZeissTurret()
{
}

int ZeissTurret::GetPosition(MM::Device& device, MM::Core& core, int turretNr, int& position)
{
   ostringstream cmd;
   cmd << "HPCr" << turretNr << ",1";
   int ret = g_hub.ExecuteCommand(device, core,  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(device, core, response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PH") 
      position = atoi(response.substr(2).c_str());
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int ZeissTurret::SetPosition(MM::Device& device, MM::Core& core, int turretNr, int position)
{
   ostringstream cmd;
   cmd << "HPCR" << turretNr << "," << position;
   int ret = g_hub.ExecuteCommand(device, core,  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ZeissTurret::GetMaxPosition(MM::Device& device, MM::Core& core, int turretNr, int& position)
{
   ostringstream cmd;
   // NS 12/2007: Now reads directly from subbus module, might be slower but nominal buffer sometimes gives the wrong answer, this only works with the ZM firmware though..
   if (g_hub.firmware_ == "MF") 
      cmd << "HPCr" << turretNr << ",2";
   else
      cmd << "HPCr" << turretNr << ",3";
   int ret = g_hub.ExecuteCommand(device, core,  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(device, core, response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PH") 
      position = atoi(response.substr(2).c_str());
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int ZeissTurret::GetBusy(MM::Device& device, MM::Core& core, int turretNr, bool& busy)
{
   // First we need to associate turretNr with groupNumber (1 or 2, see Zeiss documentation)
   int groupNumber;
   // check whether this turret is in group one or two
   vector<int>::iterator result;
   result = find (groupOne.begin(), groupOne.end(), turretNr);
   if (result != groupOne.end())
      groupNumber = 1;
   else 
   {
      result = find (groupTwo.begin(), groupTwo.end(), turretNr);
      if (result != groupTwo.end())
         groupNumber = 2;
      else
         return ERR_INVALID_TURRET;
   }

   // query the group for busy devices
   ostringstream cmd;
   cmd << "HPSb" << groupNumber ;
   int ret = g_hub.ExecuteCommand(device, core,  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(device, core, response);
   if (ret != DEVICE_OK)
      return ret;
   int statusByte;
   if (response.substr(0,2) == "PH") 
      statusByte = atoi(response.substr(2).c_str());
   else
      return ERR_UNEXPECTED_ANSWER;

   // interpret the status byte returned by the micrsocope
   busy = (1 & (statusByte >> bitPosition[turretNr]));

   return DEVICE_OK;
}




int ZeissTurret::GetPresence(MM::Device& device, MM::Core& core,  int turretNr, bool& present)
{
   present = false;

   ostringstream cmd;
   cmd << "HPCr" << turretNr << ",0";
   int ret = g_hub.ExecuteCommand(device, core,  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(device, core, response);
   if (ret != DEVICE_OK)
      return ret;

   int answer;
   if (response.substr(0,2) == "PH") 
      answer = atoi(response.substr(2).c_str());
   else
      return ERR_UNEXPECTED_ANSWER;

   if (answer == 0)
      present = false;
   else if ( (answer == 1) || (answer == 2) )
      present = true;
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// ZeissScope
//
ZeissScope::ZeissScope() :
   initialized_(false),                                                     
   pTurretIDMap_(NULL)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZeissDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Zeiss microscope CAN bus adapter", MM::String, true);
 
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissScope::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZeissScope::~ZeissScope() 
{
   delete pTurretIDMap_;
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZeissScope::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_hub.port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // TODO: allow port changes
         pProp->Set(g_hub.port_.c_str());
         //return ERR_PORT_CHANGE_FORBIDDEN;
      } else {
       // take this port.  TODO: should we check if this is a valid port?
         pProp->Get(g_hub.port_);

         g_hub.initialized_ = true;
         //initialized_ = true;
      }
   }

   return DEVICE_OK;
}

int ZeissScope::Initialize() 
{
   if(!initialized_)
   {
      // Version
      string version;
      int ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
      // if this fails, try one more time
      // The device detection code may have left crud in the device
      if (ret != DEVICE_OK)
         ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
      if (DEVICE_OK != ret)
      {
         // We have not detected the CAN microscope stand. However, there may
         // be an MCU 28 connected directly to the computer.
         int mcu28Status = GetMCU28Version(version);
         if (mcu28Status != DEVICE_OK)
            return ret;
      }

      ret = CreateProperty("Microscope Version", version.c_str(), MM::String, true);
      if (DEVICE_OK != ret)
         return ret;
       
      ret = UpdateStatus();
      if (DEVICE_OK != ret)
         return ret;

      initialized_ = true;
   }
   return 0;
}

int ZeissScope::Shutdown() 
{
   return 0;
}

void ZeissScope::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDeviceName);
}

bool ZeissScope::Busy() 
{
   return false;
}

bool ZeissScope::IsMCU28Present()
{
   // get firmware info
   const char * command = "NPTv0";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return false;

   // first two chars should read 'PF'
   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return false;

   if (response.substr(0,2).compare("PN") == 0) 
      return true; // got the right answer
   else
      return false;
}

int ZeissScope::GetMCU28Version(std::string& ver)
{
   // get firmware info
   const char * command = "NPTv0";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   // first two chars should read 'PN'
   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;
   if (response.substr(0,2).compare("PN") == 0) 
      ver = "Application version: " + response.substr(2);
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

MM::DeviceDetectionStatus ZeissScope::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   try
   {
      std::string transformed = g_hub.port_;//\port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      // ensure we’ve been provided with a valid serial port device name
      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), MM::g_Keyword_StopBits, "1");
         
         // we can speed up detection with shorter answer timeout here
         GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), "DelayBetweenCharsMs", "0.0");

         std::vector<std::string> handShakeSettings;
         handShakeSettings.push_back("Off");
         //handShakeSettings.push_back("Hardware");  trying this without i/o partner hangs serial manager - both original windows version and boost asio verion !!!!!!

         for( std::vector<std::string>::iterator shakeSetIterator = handShakeSettings.begin();  
            shakeSetIterator != handShakeSettings.end(); 
            ++shakeSetIterator)
         {

            GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), MM::g_Keyword_Handshaking, shakeSetIterator->c_str());

            MM::Device* pS = GetCoreCallback()->GetDevice(this, g_hub.port_.c_str());

            pS->Initialize();
            std::string v;
            // GetVersion is also used during initialization
            int gvStatus = g_hub.GetVersion(*this, *GetCoreCallback(), v);
            if( DEVICE_OK != gvStatus )
            {
               LogMessageCode(gvStatus,true);
            }
            else
            {
               // to succeed must reach here....
               result = MM::CanCommunicate;
            }
            LogMessage(std::string("version : ")+v, true);
            pS->Shutdown();
            // quit when we find the setting that works
            if( MM::CanCommunicate == result)
               break;
            else
               // try to yield to GUI
            CDeviceUtils::SleepMs(10);
         }
         GetCoreCallback()->SetDeviceProperty(g_hub.port_.c_str(), "AnswerTimeout", "2000.0");
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}


std::map<int,std::string>& ZeissScope::turretIDMap()
{

   if( NULL == pTurretIDMap_)
   {
      pTurretIDMap_ =  new std::map<int, std::string>();
      (*pTurretIDMap_)[g_ReflectorTurret] = g_ZeissReflector;
      (*pTurretIDMap_)[g_ObjectiveTurret] = g_ZeissObjectives;
      (*pTurretIDMap_)[g_ExtFilterWheel] = g_ZeissExtFilterWheel;
      (*pTurretIDMap_)[g_OptovarTurret] = g_ZeissOptovar;
      (*pTurretIDMap_)[g_FilterWheel1] = g_ZeissFilterWheel1;
      (*pTurretIDMap_)[g_FilterWheel2] = g_ZeissFilterWheel2;
      (*pTurretIDMap_)[g_CondenserTurret] = g_ZeissCondenser;
      //(*pTurretIDMap_)[g_CondenserFrontlens] = g_ZeissObjectives;
      (*pTurretIDMap_)[g_TubelensTurret] = g_ZeissTubelens;
      (*pTurretIDMap_)[g_BasePortSlider] = g_ZeissBasePort;
      (*pTurretIDMap_)[g_SidePortTurret] = g_ZeissSidePort;
      (*pTurretIDMap_)[g_LampMirror] = g_ZeissLampMirror;

/*
const char* g_ZeissDeviceName = "ZeissScope";
const char* g_ZeissShutter = "ZeissShutter";
const char* g_ZeissShutterMF = "ZeissShutterMFFirmware";
const char* g_ZeissShutterNr = "ZeissShutterNr";
const char* g_ZeissReflector = "ZeissReflectorTurret";
const char* g_ZeissSidePort = "ZeissSidePortTurret";
const char* g_ZeissBasePort = "ZeissBasePortSlider";
const char* g_ZeissObjectives = "ZeissObjectives";
const char* g_ZeissOptovar = "ZeissOptovar";
const char* g_ZeissTubelens = "ZeissTubelens";
const char* g_ZeissCondenser = "ZeissCondenser";
const char* g_ZeissLampMirror = "ZeissExcitationLampSwitcher";
const char* g_ZeissHalogenLamp = "ZeissHalogenLamp";
const char* g_ZeissFocusName = "Focus";
const char* g_ZeissExternal = "External-Internal Shutter";
const char* g_ZeissExtFilterWheel = "ZeissExternalFilterWheel";
const char* g_ZeissFilterWheel1 = "ZeissFilterWheel1";
const char* g_ZeissFilterWheel2 = "ZeissFilterWheel2";
*/

   }
   return *pTurretIDMap_;
}


int ZeissScope::Query(std::string queryCode, std::string& answer)
{
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  queryCode.c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   answer = response.substr(2).c_str();

   return DEVICE_OK;
};


void ZeissScope::CreateAndAddDevice(std::string deviceName)
{
   MM::Device* pDev = ::CreateDevice(deviceName.c_str());
   if (pDev)
   {
      AddInstalledDevice(pDev);
   }
}

int ZeissScope::DetectInstalledDevices()
{
   int ret;
   bool exists;

   std::map<int,std::string>& turrr = turretIDMap();
   std::map<int, std::string>::iterator iii;

   // It is possible to use an MCU 28 XY stage connected directly to the computer.
   // In that case, all checks for the other devices (with HP* commands) will
   // time out, and we don't want to wait for all of them. So, we skip all of
   // those checks in the case where the first one times out.
   bool couldNotCommunicateWithStandDevice = false;

   for( iii = turrr.begin(); turrr.end() != iii; ++iii)
   {
      ret = g_turret.GetPresence(*this, *GetCoreCallback(), iii->first, exists);
      if (DEVICE_OK == ret)
      {
         if(exists)
         {
            MM::Device* pDev = ::CreateDevice(iii->second.c_str());
            if (pDev)
               AddInstalledDevice(pDev);
         }
      }
      else if (ret != ERR_UNEXPECTED_ANSWER) // Serial communication failed (e.g. timed out)
      {
         // Skip further checks for stand-attached devices
         couldNotCommunicateWithStandDevice = true;
         break;
      }
   }

   if (!couldNotCommunicateWithStandDevice)
   {
      CreateAndAddDevice(g_ZeissHalogenLamp);

      std::string response;
      Query("HPCk1,0", response);
      if (0 != response.compare("0"))
      {
         CreateAndAddDevice(g_ZeissShutter);
      }

      Query("HPCk1,0", response);
      if (0 != response.compare("1F"))
      {
         CreateAndAddDevice(g_ZeissFocusName);
      }

      // NS 2015-04: I think this should always create an MF shutter, 
      // but play it safe
      Query("HPCm1,0", response);
      if (!(  (0 == response.compare("0"))
              ||(0 == response.compare("55"))  ))
      {
         if (g_hub.firmware_ == "MF")
         {
            CreateAndAddDevice(g_ZeissShutterMF);
         } 
         else 
         {
            CreateAndAddDevice(g_ZeissShutter);
         }
      }
   }

   // finally check if MCU28 is installed
   if (IsMCU28Present())
   {
      MM::Device* pDev = ::CreateDevice(g_ZeissXYStage);
      if (pDev)
         AddInstalledDevice(pDev);
   }

   return DEVICE_OK;
}


void ZeissScope::GetDiscoverableDevice(int peripheralNum, char* peripheralName, unsigned int maxNameLen)
{ 
   if( -1 < peripheralNum)
   {
      if( peripheralNum < int(peripherals_.size()))
      {
            strncpy(peripheralName, peripherals_[peripheralNum].c_str(), maxNameLen - 1);
            peripheralName[maxNameLen - 1] = 0;
      }
   
   }
   return;
} 




///////////////////////////////////////////////////////////////////////////////
// Zeiss Shutter
///////////////////////////////////////////////////////////////////////////////
ZeissShutter::ZeissShutter () :
   initialized_ (false),
   name_ (g_ZeissShutter),
   shutterNr_ (1),
   external_ (false),
   state_ (1),
   changedTime_ (0.0)
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   // Todo: Add custom messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Shutter to work");
   
   // Shutter Nr (1 = fluorescence shutter, 2 = ?)
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissShutter::OnShutterNr);
   CreateProperty(g_ZeissShutterNr, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_ZeissShutterNr, "1");

   // Is this shutter internal or external?
   pAct = new CPropertyAction (this, &ZeissShutter::OnExternal);
   CreateProperty(g_ZeissExternal, "Internal", MM::String, false, pAct, true);
   AddAllowedValue(g_ZeissExternal, "Internal");
   AddAllowedValue(g_ZeissExternal, "External");
}

ZeissShutter::~ZeissShutter ()
{
   Shutdown();
}

void ZeissShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ZeissShutter::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set timer for the Busy signal, or we'll get a time-out the first time 
   // we check the state of the shutter, for good measure, go back 'delay' 
   // time into the past
   changedTime_ = GetCurrentMMTime();

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissShutter::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool ZeissShutter::Busy()
{
   bool timer_busy, shutter_busy;
   ostringstream cmd;

   // first check the timer
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   timer_busy = (interval < delay );

   if (external_)
      cmd << "SPSb2";
   else
      cmd << "HPSb2";

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd.str().c_str());
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("ExecuteCommand failed in ZeisShutter::Busy");
      return false; // can't write so say we're not busy
   }


   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("GetAnswer failed in ZeisShutter::Busy");
      return false;
   }

   int statusByte;
   if (response.substr(0,2) == "PH" || response.substr(0,2) == "PS")
      statusByte = atoi(response.substr(2).c_str());
   else
   {
      this->LogMessage("Incomprehensible answer from microscope in ZeisShutter::Busy");
      return false;
   }

   // interpret the status byte returned by the micrsocope
   shutter_busy = (1 & (statusByte >> 4));

   return timer_busy || shutter_busy;
}

int ZeissShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int ZeissShutter::SetOpen(bool open)
{
   int ret;

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
   
   char command[8] = "HPCK1,2";
   if (external_)
      command[0] = 'S';
   if (open)
   {
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   } else 
   {
      command[6] = '1';
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int ZeissShutter::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   char command[8] = "HPCk1,1";
   if (external_)
      command[0] = 'S';
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   // HACK: sometimes we get 'H1' on startup??? Catch it here:
   if (response == "H1")
      open = false;
   // PH is internal shutter, PS is external shutter
   else if ((response.substr(0,2) == "PH") || (response.substr(0,2) == "PS")) 
   {
      if (response.substr(2,1)=="1")
         open = false;
      else if (response.substr(2,1)=="2")
         open = true;
      else
         return ERR_UNEXPECTED_ANSWER;
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int ZeissShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ZeissShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         return this->SetOpen(true);
      }
      else
      {
         return this->SetOpen(false);
      }
   }
   return DEVICE_OK;
}

int ZeissShutter::OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return shutter nr
      pProp->Set((long)shutterNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long pos;
      pProp->Get(pos);
      if (pos==1 || pos==2)
         shutterNr_ = pos;
   }
   return DEVICE_OK;
}

int ZeissShutter::OnExternal(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (external_)
         pProp->Set("External");
      else
         pProp->Set("Internal");
   }
   else if (eAct == MM::AfterSet)
   {
      string shutterLocation;
      pProp->Get(shutterLocation);
      if (shutterLocation == "External")
         external_ = true;
      else
         external_ = false;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Zeiss Shutter for MF Firmware only
///////////////////////////////////////////////////////////////////////////////
ZeissShutterMF::ZeissShutterMF () :
   initialized_ (false),
   name_ (g_ZeissShutter),
   shutterNr_ (1),
   state_ (1)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Shutter to work");
   
   // Shutter Nr (1 = incident light, 2= condenser front lens, 3 = external motorized Mirror with two lamps)
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissShutterMF::OnShutterNr);
   CreateProperty(g_ZeissShutterNr, "1", MM::Integer, false, pAct, true);
   AddAllowedValue(g_ZeissShutterNr, "1");
   AddAllowedValue(g_ZeissShutterNr, "2");
   AddAllowedValue(g_ZeissShutterNr, "3");
}

ZeissShutterMF::~ZeissShutterMF ()
{
   Shutdown();
}

void ZeissShutterMF::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ZeissShutterMF::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissShutterMF::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool ZeissShutterMF::Busy()
{
   bool busy;
   ostringstream cmd;
   cmd << "HPSb2";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd.str().c_str());
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("ExecuteCommand failed in ZeisShutterMF::Busy");
      return false; // can't write so say we're not busy
   }

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("GetAnswer failed in ZeisShutterMF::Busy");
      return false;
   }

   int statusByte;
   if (response.substr(0,2) == "PH" || response.substr(0,2) == "PS")
      statusByte = atoi(response.substr(2).c_str());
   else
   {
      this->LogMessage("Incomprehensible answer from microscope in ZeisShutter::Busy");
      return false;
   }
   // Remove after debugging with Kate Phelps
   std::ostringstream os;
   os << "Shutter status byte: " << statusByte;
   this->LogMessage(os.str().c_str(), true);

   // interpret the status byte returned by the micrsocope
   busy = (1 & (statusByte >> (3 + shutterNr_)));

   return busy;
}

int ZeissShutterMF::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int ZeissShutterMF::SetOpen(bool open)
{
   ostringstream os;
   os << "HPCM" << shutterNr_ << ",";
   if (open)
      os << 2;
   else 
      os << 1;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ZeissShutterMF::GetOpen(bool &open)
{

   // Check current state of shutter: (this might not be necessary, since we cash ourselves)
   ostringstream os;
   os << "HPCm" <<shutterNr_ << "," << 1;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  os.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   // HACK: sometimes we get 'H1' on startup??? Catch it here:
   if (response == "H1")
      open = false;
   // PH is internal shutter, PS is external shutter
   else if ((response.substr(0,2) == "PH") ) 
   {
      int position = atoi(response.substr(2).c_str());
      if (position == 1)
         open = false;
      else if (position == 2)
         open = true;
      else
         return ERR_UNEXPECTED_ANSWER;
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int ZeissShutterMF::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ZeissShutterMF::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
         return this->SetOpen(true);
      else
         return this->SetOpen(false);
   }
   return DEVICE_OK;
}

int ZeissShutterMF::OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return shutter nr
      pProp->Set((long)shutterNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long nr;
      pProp->Get(nr);
      if (nr==1 || nr==2 || nr==3)
         shutterNr_ = nr;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Zeiss HalogenLamp
///////////////////////////////////////////////////////////////////////////////
HalogenLamp::HalogenLamp () :
   initialized_ (false),
   name_ (g_ZeissHalogenLamp),
   changedTime_(0.0),
   state_ (1)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Halogen to work");
   EnableDelay();
   
}

HalogenLamp::~HalogenLamp ()
{
   Shutdown();
}

void HalogenLamp::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int HalogenLamp::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "HalogenLamp", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 'delay' time into the past
   changedTime_ = GetCurrentMMTime();

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &HalogenLamp::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // switch off the light manager
   ret = SetLM(false);
   if (ret != DEVICE_OK) 
      return ret; 
   pAct = new CPropertyAction(this, &HalogenLamp::OnLightManager);
   ret = CreateProperty("LightManager", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue("LightManager", "0"); // Closed
   AddAllowedValue("LightManager",  "1"); // Open

   // Intensity
   // -----
   CPropertyAction* pAct2 = new CPropertyAction(this, &HalogenLamp::OnIntensity);
   ret = CreateProperty("Intensity", "0", MM::Integer, false, pAct2);
   SetPropertyLimits("Intensity", 0, 256);

   if (ret != DEVICE_OK)
      return ret;


   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool HalogenLamp::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay )
      return true;
   else
      return false;
}

int HalogenLamp::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int HalogenLamp::SetOpen(bool open)
{
   int ret;
   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();

   if (open)
   {
      const char* command = "HPCT8,0";
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   } else 
   {
      const char* command = "HPCT8,1";
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int HalogenLamp::GetOpen(bool &open)
{
   // Check current state of shutter
   const char * command = "HPCt8";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PH") 
   {
      if (response.substr(2,1)=="0")
         open = true;
      else if (response.substr(2,1)=="1")
         open = false;
      else
         return ERR_UNEXPECTED_ANSWER;
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int HalogenLamp::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

int HalogenLamp::SetLM(bool on)
{
   int ret;
   if (on)
   {
      const char* command = "HPCT12,2";
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   } else 
   {
      const char* command = "HPCT12,1";
      ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int HalogenLamp::GetLM(bool &on)
{
   // Check current state of LightManager
   const char * command = "HPCt12";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PH") 
   {
      if (response.substr(2,1)=="1")
         on = false;
      else if (response.substr(2,1)=="2")
         on = true;
      else
         return ERR_UNEXPECTED_ANSWER;
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int HalogenLamp::SetIntensity(long intensity)
{
   const char* prefix = "HPCV1,";
   std::stringstream command_stream;
   command_stream << prefix << intensity;
   return g_hub.ExecuteCommand(*this, *GetCoreCallback(), command_stream.str().c_str());
}

int HalogenLamp::GetIntensity(long &intensity)
{
   const char* command = "HPCv1";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PH") 
   {
      intensity = atoi(response.substr(2).c_str());
      if (intensity < 0 || intensity > 255) {
         return ERR_UNEXPECTED_ANSWER;
      } else {
         return DEVICE_OK;
      }
   }
   else
   {
      return ERR_UNEXPECTED_ANSWER;
   }

}



///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int HalogenLamp::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      int ret;
      pProp->Get(pos);
      if (pos==1)
      {
         ret = SetOpen(true);
      }
      else
      {
         ret = SetOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int HalogenLamp::OnLightManager(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool LMOn;
      GetLM(LMOn);
      if (LMOn)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         return this->SetLM(true);
      }
      else
      {
         return this->SetLM(false);
      }
   }
   return DEVICE_OK;
}


int HalogenLamp::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long intensity;
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      int ret = GetIntensity(intensity);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(intensity);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(intensity);
      return SetIntensity(intensity);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Reflector Turret
///////////////////////////////////////////////////////////////////////////////
ReflectorTurret::ReflectorTurret():
   initialized_ (false),
   name_ (g_ZeissReflector),
   pos_(1),
   numPos_(5),
   turretId_ (g_ReflectorTurret)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss Reflector Turret adapter", MM::String, true);

   UpdateStatus();
}

ReflectorTurret::~ReflectorTurret()
{
   Shutdown();
}

void ReflectorTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ReflectorTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &ReflectorTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Dichroic-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Dichroic-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int ReflectorTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool ReflectorTurret::Busy()
{
   
   int position;
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK) {
      this->LogMessage("GetBusy failed in Reflector::Busy, GetPosition");
      return false; // error, so say we're not busy
   }
   if (position == 0)
      return true;

   bool busy;
   ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in Reflector::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int ReflectorTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ReflectorTurret::GetTurretPosition(int& position)
{
   position = 0;
   int count = 0;
   int ret = DEVICE_OK;
   while (position == 0 && count < 15)
   {
	   ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
       if (ret != DEVICE_OK)
		 return ret;
	   count++;
	   CDeviceUtils::SleepMs(200);
   }

   // Deal with faulty position given by Axiovert200m on startup here:
   if ((position < 1) || (position > numPos_))
   {
      position = 1;
      LogMessage("Received false position for Reflector Turret position from Microscope", true);
      // ret = SetPosition(position);
      // if (ret != DEVICE_OK)
      //   return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ReflectorTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// SidePort Turret
///////////////////////////////////////////////////////////////////////////////
SidePortTurret::SidePortTurret():
   initialized_ (false),
   name_ (g_ZeissSidePort),
   pos_(1),
   numPos_(5),
   turretId_ (g_SidePortTurret)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss SidePort Turret adapter", MM::String, true);

   UpdateStatus();
}

SidePortTurret::~SidePortTurret()
{
   Shutdown();
}

void SidePortTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int SidePortTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &SidePortTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int SidePortTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool SidePortTurret::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in SidePortTurret::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int SidePortTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int SidePortTurret::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////
int SidePortTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// BasePort Slider
///////////////////////////////////////////////////////////////////////////////
BasePortSlider::BasePortSlider():
   initialized_ (false),
   name_ (g_ZeissBasePort),
   pos_(1),
   numPos_(5),
   turretId_ (g_BasePortSlider)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss BasePort Slider adapter", MM::String, true);

   UpdateStatus();
}

BasePortSlider::~BasePortSlider()
{
   Shutdown();
}

void BasePortSlider::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int BasePortSlider::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &BasePortSlider::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int BasePortSlider::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool BasePortSlider::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in BasePortSlider::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int BasePortSlider::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int BasePortSlider::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int BasePortSlider::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ObjectiveTurret
///////////////////////////////////////////////////////////////////////////////
ObjectiveTurret::ObjectiveTurret():
   initialized_ (false),
   name_ (g_ZeissObjectives),
   pos_(1),
   numPos_(5),
   turretId_ (g_ObjectiveTurret)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss Objective Turret adapter", MM::String, true);

   UpdateStatus();
}

ObjectiveTurret::~ObjectiveTurret()
{
   Shutdown();
}

void ObjectiveTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ObjectiveTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &ObjectiveTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int ObjectiveTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool ObjectiveTurret::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in Objectiveurret::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int ObjectiveTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ObjectiveTurret::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ObjectiveTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Optovar Turret
///////////////////////////////////////////////////////////////////////////////
OptovarTurret::OptovarTurret():
   initialized_ (false),
   name_ (g_ZeissOptovar),
   pos_(1),
   numPos_(5),
   turretId_ (g_OptovarTurret)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "Optovar was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss Optovar Turret adapter", MM::String, true);

   UpdateStatus();
}

OptovarTurret::~OptovarTurret()
{
   Shutdown();
}

void OptovarTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int OptovarTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &OptovarTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int OptovarTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool OptovarTurret::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in OptovarTurret::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int OptovarTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int OptovarTurret::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int OptovarTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Tubelens Turret
///////////////////////////////////////////////////////////////////////////////
TubelensTurret::TubelensTurret():
   initialized_ (false),
   name_ (g_ZeissTubelens),
   pos_(1),
   numPos_(5),
   turretId_ (g_TubelensTurret)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "Tubelens was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss Tubelens Turret adapter", MM::String, true);

   UpdateStatus();
}

TubelensTurret::~TubelensTurret()
{
   Shutdown();
}

void TubelensTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int TubelensTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &TubelensTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int TubelensTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool TubelensTurret::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in OptovarTurret::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int TubelensTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TubelensTurret::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int TubelensTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CondenserTurret
///////////////////////////////////////////////////////////////////////////////
CondenserTurret::CondenserTurret():
   initialized_ (false),
   name_ (g_ZeissCondenser),
   pos_(1),
   numPos_(5),
   turretId_ (g_CondenserTurret),
   apertureChangingTimeout_ (0)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss Condenser Turret adapter", MM::String, true);

   UpdateStatus();
}

CondenserTurret::~CondenserTurret()
{
   Shutdown();
}

void CondenserTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int CondenserTurret::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &CondenserTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   // Aperture
   // -----
   pAct = new CPropertyAction(this, &CondenserTurret::OnAperture);
   ret = CreateProperty("Diaphram Aperture", "0", MM::Float, false, pAct);
   SetPropertyLimits("Diaphram Aperture", 0.09, 0.55);

   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CondenserTurret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool CondenserTurret::Busy()
{
   // aperture busy?
   bool aperture_busy = GetCurrentMMTime() < apertureChangingTimeout_;

   // turret busy?
   bool turret_busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, turret_busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in CondenserTurret::Busy");
      return false; // error, so say we're not busy
   }

   return aperture_busy || turret_busy;
}

int CondenserTurret::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int CondenserTurret::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int CondenserTurret::SetAperture(long aperture)
{
	// set the timeout for the Busy signal.  assume that the aperture takes 4 seconds to go
	// from full-closed (1) to full-open (395).  get the current aperture and the dest
	// aperture and calculate how long it will take to effect that change.  add 1/4 sec
	// for comms delay, too.

	long curr_aperture;
	GetAperture(curr_aperture);
	double aperture_timeout_ms = fabs((float)curr_aperture - (float)aperture) / 394.0 * 4000.0 + 250.0;
	apertureChangingTimeout_ = GetCurrentMMTime() + MM::MMTime(aperture_timeout_ms * 1000.0);

    const char* prefix = "HPCS33,";
    std::stringstream command_stream;
    command_stream << prefix << aperture;
    command_stream.str().c_str();
    return g_hub.ExecuteCommand(*this, *GetCoreCallback(), command_stream.str().c_str());
}

int CondenserTurret::GetAperture(long &aperture)
{
    const char* command = "HPCs33,1";
    int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command);
    if (ret != DEVICE_OK)
       return ret;

    string response;
    ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
    if (ret != DEVICE_OK)
       return ret;

    if (response.substr(0,2) == "PH")
    {
        aperture = atoi(response.substr(2).c_str());
        if (aperture < 1 || aperture > 395) {
            return ERR_UNEXPECTED_ANSWER;
        } else {
            return DEVICE_OK;
        }
    }
    else
    {
        return ERR_UNEXPECTED_ANSWER;
    }
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int CondenserTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

int CondenserTurret::OnAperture(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    double aperture_prop;
	long aperture_dev;

    if (eAct == MM::BeforeGet)
    {
        // return aperture
        int ret = GetAperture(aperture_dev);
        if (ret != DEVICE_OK)
            return ret;
		aperture_prop = ((double)aperture_dev - 1.0) / 394.0 * 0.46 + 0.09;
        pProp->Set(aperture_prop);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(aperture_prop);
		aperture_dev = (long)(((aperture_prop - 0.09)) / 0.46 * 394.0 + 1.0);
        return SetAperture(aperture_dev);
    }
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// LampMirror
///////////////////////////////////////////////////////////////////////////////
LampMirror::LampMirror():
   initialized_ (false),
   name_ (g_ZeissLampMirror),
   pos_(1),
   numPos_(5),
   turretId_ (g_LampMirror)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Zeiss LampMirror adapter", MM::String, true);

   UpdateStatus();
}

LampMirror::~LampMirror()
{
   Shutdown();
}

void LampMirror::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int LampMirror::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &LampMirror::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int LampMirror::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool LampMirror::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in LampMirror::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int LampMirror::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int LampMirror::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int LampMirror::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Focus Stage (z-drive)
///////////////////////////////////////////////////////////////////////////////
FocusStage::FocusStage() :
stepSize_um_ (0.025),  // note: this is 0.050 in the Axioplan 2
initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Focus drive to work");
   SetErrorText(ERR_NO_FOCUS_DRIVE, "No focus drive found in this microscopes");
}

FocusStage::~FocusStage()
{
   Shutdown();
}

void FocusStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissFocusName);
}

int FocusStage::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // set property list
   // ----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ZeissFocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Z-drive", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = GetFocusFirmwareVersion();
   if (ret != DEVICE_OK)
      return ERR_NO_FOCUS_DRIVE;

   // Firmware version
   ret = CreateProperty("Focus firmware", focusFirmware_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   CPropertyAction* pAct = new CPropertyAction(this, &FocusStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float,false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Working position
   std::vector<std::string> allowedValues;
   allowedValues.push_back("0");
   allowedValues.push_back("1");
   CPropertyAction* pActLoadSample = new CPropertyAction(this, &FocusStage::OnLoadSample);
   ret = CreateProperty(g_Keyword_LoadSample, "0", MM::Integer,false, pActLoadSample);
   SetAllowedValues(g_Keyword_LoadSample, allowedValues);
   if (ret != DEVICE_OK)
      return ret;

   // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized
   ret = GetUpperLimit();
   if (ret != DEVICE_OK)
      return ret;
   ret = GetLowerLimit();
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int FocusStage::Shutdown()
{
   initialized_ = false;

   return DEVICE_OK;
}

bool FocusStage::Busy()
{
   // TODO: figure out how to get a busy signal on MF firmware
   if (firmware_ == "MF")
      return false;

   const char * command = "FPZFs";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("ExecuteCommand failed in FocusStage::Busy");
      return false; // error, so say we're not busy
   }

   // first two chars should read 'PF'
   string response;
   unsigned long flags;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
   {
      this->LogMessage("GetAnswer failed in FocusStage::Busy");
      return false; // error, so say we're not busy
   }

   // Note: if the controller reports that the motors are moving or settling, we'll consider the z-drive to be busy
   if (response.substr(0,2) == "PF") 
   {
      flags = strtol(response.substr(2,4).c_str(), NULL, 16);
      if ( (flags & ZMSF_MOVING) || (flags & ZMSF_SETTLE) )
         return true;
      else
         return false;
   }
   // this is actually an unexpected answer, but we can not communicate this up the choain
   this->LogMessage("Unexpected answer from Microscope in FocusStage::Busy");
   return false;

}

int FocusStage::SetPositionUm(double pos)
{
   long steps = (long)(pos / stepSize_um_ + 0.5);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int FocusStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSize_um_;

   return DEVICE_OK;
}

/*
 * Requests movement to new z postion from the controller.  This function does the actual communication 
 */
int FocusStage::SetPositionSteps(long steps)
{
   // the hard part is to get the formatting of the string right.
   // it is a hex number from 800000 .. 7FFFFF, where everything larger than 800000 is a negative number!?
   // We can speed up communication by skipping leading 0s, but that makes the following more complicated:
   char tmp[98];
   // convert the steps into a twos-complement 6bit number
   if (steps<0)
      steps = steps+0xffffff+1;
   snprintf(tmp, 9, "%08lX", steps);
   string tmp2 = tmp;
   ostringstream cmd;
   cmd << "FPZT" << tmp2.substr(2,6).c_str();
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Requests current z postion from the controller.  This function does the actual communication
 */
int FocusStage::GetPositionSteps(long& steps)
{
   const char* cmd ="FPZp" ;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PF") 
   {
      steps = strtol(response.substr(2).c_str(), NULL, 16);
   }
   else  
      return ERR_UNEXPECTED_ANSWER;

   // To 'translate' 'negative' numbers according to the Zeiss schema (there must be a more elegant way of doing this:
   long sign = strtol(response.substr(2,1).c_str(), NULL, 16);
   if (sign > 7)  // negative numbers
   {
      steps = steps - 0xFFFFFF - 1;
   }

   return DEVICE_OK;
}

int FocusStage::SetOrigin()
{
   const char* cmd ="FPZP0" ;
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int FocusStage::GetUpperLimit()
{
   const char* cmd = "FPZu";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PF") 
   {
      long steps = strtol(response.substr(2).c_str(), NULL, 16);
      upperLimit_ = steps * stepSize_um_; 
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int FocusStage::GetLowerLimit()
{
   const char* cmd = "FPZl";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd);
   if (ret != DEVICE_OK)
      return ret;

   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;

   if (response.substr(0,2) == "PF") 
   {
      long steps = strtol(response.substr(2).c_str(), NULL, 16);
      lowerLimit_ = steps * stepSize_um_; 
   }
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int FocusStage::GetFocusFirmwareVersion()
{
   // get firmware info
   const char * command = "FPTv0";
   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command);
   if (ret != DEVICE_OK)
      return ret;

   // first two chars should read 'PF'
   string response;
   ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
   if (ret != DEVICE_OK)
      return ret;
   if (response.substr(0,2).compare("PF") == 0) 
   {
      focusFirmware_ = response.substr(2);
      firmware_ = response.substr(2,2);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////
/*
 * Uses the Get and Set PositionUm functions to communicate with controller
 */
int FocusStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      int ret = SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


/*
 * Set stage in load sample mode
 */
int FocusStage::OnLoadSample(MM::PropertyBase* pProp, MM::ActionType eAct)
{
//1: up
//0: down. but can also return 4.

   if (eAct == MM::BeforeGet)
   {
     const char* cmd = "FPZw";
     int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd);
     if (ret != DEVICE_OK)
        return ret;

     string response;
     ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response);
     if (ret != DEVICE_OK)
        return ret;

     if (response.substr(0,2) == "PF") 
     {
        long state = strtol(response.substr(2).c_str(), NULL, 10);
        state=state==0 || state==4;
        pProp->Set(state);
     }
     else
        return ERR_UNEXPECTED_ANSWER;

     return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);

     ostringstream cmd;
     cmd << "FPZW" << (!state);
     int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  cmd.str().c_str());
     if (ret != DEVICE_OK)
        return ret;
     return DEVICE_OK;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FilterWheel
///////////////////////////////////////////////////////////////////////////////
FilterWheel::FilterWheel(int wheelNr):
   initialized_ (false),
   pos_(1),
   numPos_(5)
{
   InitializeDefaultErrorMessages();

   wheelNr_ = wheelNr;
   
   std::string description = "";
   if (wheelNr_ == 1) {
      name_ = g_ZeissFilterWheel1;
      turretId_ = g_FilterWheel1;
      description = "Zeiss Filter Wheel 1";
   } else if (wheelNr_ == 2) {
      name_ = g_ZeissFilterWheel2;
      turretId_ = g_FilterWheel2;
      description = "Zeiss Filter Wheel 2";
   } else if (wheelNr_ == 3) {
      name_ = g_ZeissExtFilterWheel;
      turretId_ = g_ExtFilterWheel;
      description = "Zeiss External Filter Wheel";
   }

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Reflector Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "A position was requested that is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This module was not found in the Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description.c_str(), MM::String, true);

   UpdateStatus();
}

FilterWheel::~FilterWheel()
{
   Shutdown();
}

void FilterWheel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int FilterWheel::Initialize()
{
   if (!g_hub.initialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = g_turret.GetPresence(*this, *GetCoreCallback(), turretId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &FilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_turret.GetMaxPosition(*this, *GetCoreCallback(), turretId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (int i=0; i < numPos_; i++)
   {
      snprintf(buf, bufSize, "Position-%d", i+1);
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int FilterWheel::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool FilterWheel::Busy()
{
   bool busy;
   int ret = g_turret.GetBusy(*this, *GetCoreCallback(), turretId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      this->LogMessage("GetBusy failed in FilterWheel::Busy");
      return false; // error, so say we're not busy
   }

   return busy;
}

int FilterWheel::SetTurretPosition(int position)
{
   int ret = g_turret.SetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int FilterWheel::GetTurretPosition(int& position)
{
   int ret = g_turret.GetPosition(*this, *GetCoreCallback(), turretId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int FilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = GetTurretPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= numPos_))
         return SetTurretPosition(pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}
