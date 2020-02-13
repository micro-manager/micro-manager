///////////////////////////////////////////////////////////////////////////////
// FILE:          Arduino32.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino 32bit boards
//                Needs accompanying firmware to be installed on the board 
//				  Available at https://github.com/bonnom/Arduino32BitBoards
//                
//                This device Adapter is a modded Arduino Device Adapter
//
// COPYRIGHT:     University of California, San Francisco, 2008
//
// LICENSE:       LGPL
//
// AUTHOR:        Original Author Arduino Device Adapter:
//				  Nico Stuurman, nico@cmp.ucsf.edu, 11/09/2008
//
//                Automatic device detection by Karl Hoover
//				  
//                Author 32-Bit-Boards adaptation: 
//                Bonno Meddens, 30/07/2019
//
//

#include "Arduino32bitBoards.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#endif
#include "FixSnprintf.h"

const char* g_DeviceNameArduino32Hub = "Arduino32-Hub";
const char* g_DeviceNameArduino32Switch = "Arduino32-Switch";
const char* g_DeviceNameArduino32Shutter = "Arduino32-Shutter";
const char* g_DeviceNameArduino32Input = "Arduino32-Input";
const char* g_DeviceNameArduino32DAPWM1 = "Arduino32-DAC/PWM-1";
const char* g_DeviceNameArduino32DAPWM2 = "Arduino32-DAC/PWM-2";
const char* g_DeviceNameArduino32DAPWM3 = "Arduino32-DAC/PWM-3";
const char* g_DeviceNameArduino32DAPWM4 = "Arduino32-DAC/PWM-4";
const char* g_DeviceNameArduino32DAPWM5 = "Arduino32-DAC/PWM-5";
const char* g_DeviceNameArduino32DAPWM6 = "Arduino32-DAC/PWM-6";
const char* g_DeviceNameArduino32DAPWM7 = "Arduino32-DAC/PWM-7";
const char* g_DeviceNameArduino32DAPWM8 = "Arduino32-DAC/PWM-8";

// Global info about the state of the Arduino32.  This should be folded into a class
const int g_Min_MMVersion = 3;
const int g_Max_MMVersion = 3;
const char* g_versionProp = "Version";
const char* g_normalLogicString = "Normal";
const char* g_invertedLogicString = "Inverted";

const char* g_On = "On";
const char* g_Off = "Off";

// static lock
MMThreadLock CArduino32Hub::lock_;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameArduino32Hub, MM::HubDevice, "Hub (required)");
   RegisterDevice(g_DeviceNameArduino32Switch, MM::StateDevice, "Switch on/off channels 1 to 8");
   RegisterDevice(g_DeviceNameArduino32Shutter, MM::ShutterDevice, "Shutter");
   RegisterDevice(g_DeviceNameArduino32Input, MM::GenericDevice, "ADC");
   RegisterDevice(g_DeviceNameArduino32DAPWM1, MM::SignalIODevice, "DAC/PWM channel 1");
   RegisterDevice(g_DeviceNameArduino32DAPWM2, MM::SignalIODevice, "DAC/PWM channel 2");
   RegisterDevice(g_DeviceNameArduino32DAPWM3, MM::SignalIODevice, "DAC/PWM channel 3");
   RegisterDevice(g_DeviceNameArduino32DAPWM4, MM::SignalIODevice, "DAC/PWM channel 4");
   RegisterDevice(g_DeviceNameArduino32DAPWM5, MM::SignalIODevice, "DAC/PWM channel 5");
   RegisterDevice(g_DeviceNameArduino32DAPWM6, MM::SignalIODevice, "DAC/PWM channel 6");
   RegisterDevice(g_DeviceNameArduino32DAPWM7, MM::SignalIODevice, "DAC/PWM channel 7");
   RegisterDevice(g_DeviceNameArduino32DAPWM8, MM::SignalIODevice, "DAC/PWM channel 8");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameArduino32Hub) == 0)
   {
      return new CArduino32Hub;
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32Switch) == 0)
   {
      return new CArduino32Switch;
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32Shutter) == 0)
   {
      return new CArduino32Shutter;
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32Input) == 0)
   {
	   return new CArduino32Input;
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM1) == 0)
   {
      return new CArduino32DA(1); // channel 1
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM2) == 0)
   {
      return new CArduino32DA(2); // channel 2
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM3) == 0)
   {
	   return new CArduino32DA(3); // channel 3
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM4) == 0)
   {
	   return new CArduino32DA(4); // channel 4
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM5) == 0)
   {
	   return new CArduino32DA(5); // channel 5
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM6) == 0)
   {
	   return new CArduino32DA(6); // channel 6
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM7) == 0)
   {
	   return new CArduino32DA(7); // channel 7
   }
   else if (strcmp(deviceName, g_DeviceNameArduino32DAPWM8) == 0)
   {
	   return new CArduino32DA(8); // channel 8
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CArduino32HUb implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CArduino32Hub::CArduino32Hub() :
   initialized_ (false),
   switchState_ (0),
   shutterState_ (0)
{
   portAvailable_ = false;
   invertedLogic_ = false;
   timedOutputActive_ = false;

   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_OPEN_FAILED, "Failed opening Arduino32 USB device");
   SetErrorText(ERR_BOARD_NOT_FOUND, "Did not find an Arduino32 board with the correct firmware.  Is the Arduino32 board connected to this serial port?");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino32 Hub device is needed to create this device");
   std::ostringstream errorText;
   errorText << "The firmware version on the Arduino32 is not compatible with this adapter.  Please use firmware version ";
   errorText <<  g_Min_MMVersion << " to " << g_Max_MMVersion;
   SetErrorText(ERR_VERSION_MISMATCH, errorText.str().c_str());

   CPropertyAction* pAct = new CPropertyAction(this, &CArduino32Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   pAct = new CPropertyAction(this, &CArduino32Hub::OnLogic);
   CreateProperty("Logic", g_invertedLogicString, MM::String, false, pAct, true);

   AddAllowedValue("Logic", g_invertedLogicString);
   AddAllowedValue("Logic", g_normalLogicString);
}

CArduino32Hub::~CArduino32Hub()
{
   Shutdown();
}

void CArduino32Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduino32Hub);
}

bool CArduino32Hub::Busy()
{
   return false;
}

// private and expects caller to:
// 1. guard the port
// 2. purge the port
int CArduino32Hub::GetControllerVersion(int& version)
{
   int ret = DEVICE_OK;
   unsigned char command[1];
   command[0] = 30;
   version = 0;

   ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer != "MM-Ard")
      return ERR_BOARD_NOT_FOUND;

   // Check version number of the Arduino32
   command[0] = 31;
   ret = WriteToComPort(port_.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   std::string ans;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", ans);
   if (ret != DEVICE_OK) {
         return ret;
   }
   std::istringstream is(ans);
   is >> version;

   return ret;

}

bool CArduino32Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus CArduino32Hub::DetectDevice(void)
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

         // device specific default communication parameters
         // for Arduino Duemilanova
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Off);
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "57600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         // Arduino32 timed out in GetControllerVersion even if AnswerTimeout  = 300 ms
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         // The first second or so after opening the serial port, the Arduino32 is waiting for firmwareupgrades.  Simply sleep 2 seconds.
         CDeviceUtils::SleepMs(2000);
         MMThreadGuard myLock(lock_);
         PurgeComPort(port_.c_str());
         int v = 0;
         int ret = GetControllerVersion(v);
         // later, Initialize will explicitly check the version #
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
         // always restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}


int CArduino32Hub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduino32Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // The first second or so after opening the serial port, the Arduino32 is waiting for firmwareupgrades.  Simply sleep 1 second.
   CDeviceUtils::SleepMs(2000);

   MMThreadGuard myLock(lock_);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());
   ret = GetControllerVersion(version_);
   if( DEVICE_OK != ret)
      return ret;

   if (version_ < g_Min_MMVersion || version_ > g_Max_MMVersion)
      return ERR_VERSION_MISMATCH;

   CPropertyAction* pAct = new CPropertyAction(this, &CArduino32Hub::OnVersion);
   std::ostringstream sversion;
   sversion << version_;
   CreateProperty(g_versionProp, sversion.str().c_str(), MM::Integer, true, pAct);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // turn off verbose serial debug messages
   // GetCoreCallback()->SetDeviceProperty(port_.c_str(), "Verbose", "0");

   initialized_ = true;
   return DEVICE_OK;
}

int CArduino32Hub::DetectInstalledDevices()
{
   if (MM::CanCommunicate == DetectDevice()) 
   {
      std::vector<std::string> peripherals; 
      peripherals.clear();
      peripherals.push_back(g_DeviceNameArduino32Switch);
      peripherals.push_back(g_DeviceNameArduino32Shutter);
      peripherals.push_back(g_DeviceNameArduino32Input);
      peripherals.push_back(g_DeviceNameArduino32DAPWM1);
      peripherals.push_back(g_DeviceNameArduino32DAPWM2);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM3);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM4);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM5);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM6);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM7);
	  peripherals.push_back(g_DeviceNameArduino32DAPWM8);

      for (size_t i=0; i < peripherals.size(); i++) 
      {
         MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
         if (pDev) 
         {
            AddInstalledDevice(pDev);
         }
      }
   }

   return DEVICE_OK;
}



int CArduino32Hub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduino32Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      portAvailable_ = true;
   }
   return DEVICE_OK;
}

int CArduino32Hub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set((long)version_);
   }
   return DEVICE_OK;
}

int CArduino32Hub::OnLogic(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      if (invertedLogic_)
         pProp->Set(g_invertedLogicString);
      else
         pProp->Set(g_normalLogicString);
   } else if (pAct == MM::AfterSet)
   {
      std::string logic;
      pProp->Get(logic);
      if (logic.compare(g_invertedLogicString)==0)
         invertedLogic_ = true;
      else invertedLogic_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CArduino32Switch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduino32Switch::CArduino32Switch() : 
   nrPatternsUsed_(0),
   currentDelay_(0),
   sequenceOn_(false),
   blanking_(false),
   initialized_(false),
   numPos_(256), 
   busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
   SetErrorText(ERR_COMMUNICATION, "Error in communication with Arduino32 board");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino32 Hub device is needed to create this device");

   for (unsigned int i=0; i < NUMPATTERNS; i++)
      pattern_[i] = 0;

   // Description
   int ret = CreateProperty(MM::g_Keyword_Description, "Arduino32 digital output driver", MM::String, true);
   assert(DEVICE_OK == ret);

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduino32Switch, MM::String, true);
   assert(DEVICE_OK == ret);

   // parent ID display
   CreateHubIDProperty();
   }

CArduino32Switch::~CArduino32Switch()
{
   Shutdown();
}

void CArduino32Switch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduino32Switch);
}


int CArduino32Switch::Initialize()
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // set property list
   // -----------------
   
   // create positions and labels
   const int bufSize = 257;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CArduino32Switch::OnState);
   int nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits(MM::g_Keyword_State, 0, numPos_ - 1);

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   pAct = new CPropertyAction(this, &CArduino32Switch::OnSequence);
   nRet = CreateProperty("Sequence", g_On, MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue("Sequence", g_On);
   AddAllowedValue("Sequence", g_Off);

   // Starts "blanking" mode: goal is to synchronize laser light with camera exposure
   std::string blankMode = "Blanking Mode";
   pAct = new CPropertyAction(this, &CArduino32Switch::OnBlanking);
   nRet = CreateProperty(blankMode.c_str(), "Idle", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue(blankMode.c_str(), g_On);
   AddAllowedValue(blankMode.c_str(), g_Off);

   // Blank on TTL high or low
   pAct = new CPropertyAction(this, &CArduino32Switch::OnBlankingTriggerDirection);
   nRet = CreateProperty("Blank On", "Low", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue("Blank On", "Low");
   AddAllowedValue("Blank On", "High");

   /*
   // Starts producing timed digital output patterns 
   // Parameters that influence the pattern are 'Repeat Timed Pattern', 'Delay', 'State' where the latter two are manipulated with the Get and SetPattern functions
   std::string timedOutput = "Timed Output Mode";
   pAct = new CPropertyAction(this, &CArduino32Switch::OnStartTimedOutput);
   nRet = CreateProperty(timedOutput.c_str(), "Idle", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue(timedOutput.c_str(), "Stop");
   AddAllowedValue(timedOutput.c_str(), "Start");
   AddAllowedValue(timedOutput.c_str(), "Running");
   AddAllowedValue(timedOutput.c_str(), "Idle");

   // Sets a delay (in ms) to be used in timed output mode
   // This delay will be transferred to the Arduino32 using the Get and SetPattern commands
   pAct = new CPropertyAction(this, &CArduino32Switch::OnDelay);
   nRet = CreateProperty("Delay (ms)", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Delay (ms)", 0, 65535);

   // Repeat the timed Pattern this many times:
   pAct = new CPropertyAction(this, &CArduino32Switch::OnRepeatTimedPattern);
   nRet = CreateProperty("Repeat Timed Pattern", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Repeat Timed Pattern", 0, 255);
   */

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CArduino32Switch::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduino32Switch::WriteToPort(long value)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }

   MMThreadGuard myLock(hub->GetLock());

   value = 254 & value;
   if (hub->IsLogicInverted())
      value = ~value;

   hub->PurgeComPortH();

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = hub->ReadFromComPortH(answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

   hub->SetTimedOutput(false);

   return DEVICE_OK;
}

int CArduino32Switch::LoadSequence(unsigned size, unsigned char* seq)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   hub->PurgeComPortH();

   for (unsigned i=0; i < size; i++)
   {
      unsigned char value = seq[i];

      value = 63 & value;
      if (hub->IsLogicInverted())
         value = ~value;

      unsigned char command[3];
      command[0] = 5;
      command[1] = (unsigned char) i;
      command[2] = value;
      int ret = hub->WriteToComPortH((const unsigned char*) command, 3);
      if (ret != DEVICE_OK)
         return ret;


      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[3];
      while ((bytesRead < 3) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 3, br);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += br;
      }
      if (answer[0] != 5)
         return ERR_COMMUNICATION;

   }

   unsigned char command[2];
   command[0] = 6;
   command[1] = (unsigned char) size;
   int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[2];
   while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      unsigned long br;
      ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += br;
   }
   if (answer[0] != 6)
      return ERR_COMMUNICATION;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduino32Switch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      hub->SetSwitchState(pos);
      if (hub->GetShutterState() > 0)
         return WriteToPort(pos);
   }
   else if (eAct == MM::IsSequenceable)                                      
   {                                                                         
      if (sequenceOn_)                                                       
         pProp->SetSequenceable(NUMPATTERNS);                           
      else                                                                   
         pProp->SetSequenceable(0);                                          
   } 
   else if (eAct == MM::AfterLoadSequence)                                   
   {                                                                         
      std::vector<std::string> sequence = pProp->GetSequence();              
      std::ostringstream os;
      if (sequence.size() > NUMPATTERNS)                                
         return DEVICE_SEQUENCE_TOO_LARGE;                                   
      unsigned char* seq = new unsigned char[sequence.size()];               
      for (unsigned int i=0; i < sequence.size(); i++)                       
      {
         std::istringstream os (sequence[i]);
         int val;
         os >> val;
         seq[i] = (unsigned char) val;
      }                                                                      
      int ret = LoadSequence((unsigned) sequence.size(), seq);
      if (ret != DEVICE_OK)                                                  
         return ret;                                                         
                                                                             
      delete[] seq;                                                          
   }                                                                         
   else if (eAct == MM::StartSequence)
   { 
      MMThreadGuard myLock(hub->GetLock());

      hub->PurgeComPortH();
      unsigned char command[1];
      command[0] = 8;
      int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[1];
      while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 1, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 8)
         return ERR_COMMUNICATION;
   }
   else if (eAct == MM::StopSequence)                                        
   {
      MMThreadGuard myLock(hub->GetLock());

      unsigned char command[1];
      command[0] = 9;
      int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 9)
         return ERR_COMMUNICATION;

      std::ostringstream os;
      os << "Sequence had " << (int) answer[1] << " transitions";
      LogMessage(os.str().c_str(), false);

   }                                                                         

   return DEVICE_OK;
}

int CArduino32Switch::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceOn_)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == g_On)
         sequenceOn_ = true;
      else
         sequenceOn_ = false;
   }
   return DEVICE_OK;
}

int CArduino32Switch::OnStartTimedOutput(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet) {
      if (hub->IsTimedOutputActive())
         pProp->Set("Running");
      else
         pProp->Set("Idle");
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(hub->GetLock());

      std::string prop;
      pProp->Get(prop);

      if (prop =="Start") {
         hub->PurgeComPortH();
         unsigned char command[1];
         command[0] = 12;
         int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[1];
         while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = hub->ReadFromComPortH(answer + bytesRead, 1, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 12)
            return ERR_COMMUNICATION;
         hub->SetTimedOutput(true);
      } else {
         unsigned char command[1];
         command[0] = 9;
         int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[2];
         while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 9)
            return ERR_COMMUNICATION;
         hub->SetTimedOutput(false);
      }
   }

   return DEVICE_OK;
}

int CArduino32Switch::OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet) {
      if (blanking_)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(hub->GetLock());

      std::string prop;
      pProp->Get(prop);

      if (prop == g_On && !blanking_) {
         hub->PurgeComPortH();
         unsigned char command[1];
         command[0] = 20;
         int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[1];
         while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = hub->ReadFromComPortH(answer + bytesRead, 1, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 20)
            return ERR_COMMUNICATION;
         blanking_ = true;
         hub->SetTimedOutput(false);
         LogMessage("Switched blanking on", true);

      } else if (prop == g_Off && blanking_){
         unsigned char command[1];
         command[0] = 21;
         int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[2];
         while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 21)
            return ERR_COMMUNICATION;
         blanking_ = false;
         hub->SetTimedOutput(false);
         LogMessage("Switched blanking off", true);
      }
   }

   return DEVICE_OK;
}

int CArduino32Switch::OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet) {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(hub->GetLock());

      std::string direction;
      pProp->Get(direction);

      hub->PurgeComPortH();
      unsigned char command[2];
      command[0] = 22;
      if (direction == "Low") 
         command[1] = 1;
      else
         command[1] = 0;

      int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[1];
      while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 1, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 22)
         return ERR_COMMUNICATION;

   }

   return DEVICE_OK;
}

int CArduino32Switch::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set((long int)currentDelay_);
   }
   else if (eAct == MM::AfterSet)
   {
      long prop;
      pProp->Get(prop);
      currentDelay_ = (int) prop;
   }

   return DEVICE_OK;
}

int CArduino32Switch::OnRepeatTimedPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet) {
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(hub->GetLock());

      long prop;
      pProp->Get(prop);

      hub->PurgeComPortH();
      unsigned char command[2];
      command[0] = 11;
      command[1] = (unsigned char) prop;

      int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 11)
         return ERR_COMMUNICATION;

      hub->SetTimedOutput(false);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CArduino32DA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

CArduino32DA::CArduino32DA(int channel) :
      busy_(false), 
      minV_(0.0), 
      maxV_(100.0), 
      volts_(0.0),
      gatedVolts_(0.0),
      channel_(channel), 
      maxChannel_(8),
      gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino32 Hub device is needed to create this device");

   /* Channel property is not needed
   CPropertyAction* pAct = new CPropertyAction(this, &CArduino32DA::OnChannel);
   CreateProperty("Channel", channel_ == 1 ? "1" : "2", MM::Integer, false, pAct, true);
   for (int i=1; i<= 2; i++){
      std::ostringstream os;
      os << i;
      AddAllowedValue("Channel", os.str().c_str());
   }
   */

   CPropertyAction* pAct = new CPropertyAction(this, &CArduino32DA::OnMaxVolt);
   CreateProperty("Power %", "100", MM::Float, false, pAct, true);
   
   if (channel_ == 1)
   {
      name_ = g_DeviceNameArduino32DAPWM1;
   }
   else if(channel_ == 2)
   {
      name_ = g_DeviceNameArduino32DAPWM2;
   }
   else if (channel_ == 3)
   {
	   name_ = g_DeviceNameArduino32DAPWM3;
   }
   else if (channel_ == 4)
   {
	   name_ = g_DeviceNameArduino32DAPWM4;
   }
   else if (channel_ == 5)
   {
	   name_ = g_DeviceNameArduino32DAPWM5;
   }
   else if (channel_ == 6)
   {
	   name_ = g_DeviceNameArduino32DAPWM6;
   }
   else if (channel_ == 7)
   {
	   name_ = g_DeviceNameArduino32DAPWM7;
   }
   else if (channel_ == 8)
   {
	   name_ = g_DeviceNameArduino32DAPWM8;
   }


   //name_ = channel_ == 1 ? g_DeviceNameArduino32DA1 : g_DeviceNameArduino32DA2;

   // Description
   int nRet = CreateProperty(MM::g_Keyword_Description, "Arduino32 DAC driver", MM::String, true);
   assert(DEVICE_OK == nRet);

   // Name
   nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   assert(DEVICE_OK == nRet);

   // parent ID display
   CreateHubIDProperty();
}

CArduino32DA::~CArduino32DA()
{
   Shutdown();
}

void CArduino32DA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CArduino32DA::Initialize()
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CArduino32DA::OnVolts);
   int nRet = CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Volts", minV_, maxV_);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CArduino32DA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduino32DA::WriteToPort(unsigned long value)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   hub->PurgeComPortH();

   unsigned char command[4];
   command[0] = 3;
   command[1] = (unsigned char) (channel_ -1);
   command[2] = (unsigned char) (value / 256L);
   command[3] = (unsigned char) (value & 255);
   int ret = hub->WriteToComPortH((const unsigned char*) command, 4);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[4];
   while ((bytesRead < 4) && ( (GetCurrentMMTime() - startTime).getMsec() < 2500)) {
      unsigned long bR;
      ret = hub->ReadFromComPortH(answer + bytesRead, 4 - bytesRead, bR);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += bR;
   }
   if (answer[0] != 3)
      return ERR_COMMUNICATION;

   hub->SetTimedOutput(false);

   return DEVICE_OK;
}


int CArduino32DA::WriteSignal(double volts)
{
   long value = (long) ( (volts - minV_) / maxV_ * 4095);

   std::ostringstream os;
    os << "Volts: " << volts << " Max Voltage: " << maxV_ << " digital value: " << value;
    LogMessage(os.str().c_str(), true);

   return WriteToPort(value);
}

int CArduino32DA::SetSignal(double volts)
{
   volts_ = volts;
   if (gateOpen_) {
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } else {
      gatedVolts_ = 0;
   }

   return DEVICE_OK;
}

int CArduino32DA::SetGateOpen(bool open)
{
   if (open) {
      gateOpen_ = true;
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } 
   gateOpen_ = false;
   gatedVolts_ = 0;
   return WriteSignal(0.0);

}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduino32DA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double volts;
      pProp->Get(volts);
      return SetSignal(volts);
   }

   return DEVICE_OK;
}

int CArduino32DA::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxV_);
      if (HasProperty("Volts"))
         SetPropertyLimits("Volts", 0.0, maxV_);

   }
   return DEVICE_OK;
}

int CArduino32DA::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long int)channel_);
   }
   else if (eAct == MM::AfterSet)
   {
      long channel;
      pProp->Get(channel);
      if (channel >=1 && ( (unsigned) channel <=maxChannel_) )
         channel_ = channel;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CArduino32Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduino32Shutter::CArduino32Shutter() : initialized_(false), name_(g_DeviceNameArduino32Shutter)
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino32 Hub device is needed to create this device");

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduino32Shutter, MM::String, true);
   assert(DEVICE_OK == ret);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Arduino32 shutter driver", MM::String, true);
   assert(DEVICE_OK == ret);

   // parent ID display
   CreateHubIDProperty();
}

CArduino32Shutter::~CArduino32Shutter()
{
   Shutdown();
}

void CArduino32Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduino32Shutter);
}

bool CArduino32Shutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   else
       return false;
}

int CArduino32Shutter::Initialize()
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   // set property list
   // -----------------
   
   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CArduino32Shutter::OnOnOff);
   int ret = CreateProperty("OnOff", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set shutter into the off state
   //WriteToPort(0);

   std::vector<std::string> vals;
   vals.push_back("0");
   vals.push_back("1");
   ret = SetAllowedValues("OnOff", vals);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   changedTime_ = GetCurrentMMTime();
   initialized_ = true;

   return DEVICE_OK;
}

int CArduino32Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CArduino32Shutter::SetOpen(bool open)
{
	std::ostringstream os;
	os << "Request " << open;
	LogMessage(os.str().c_str(), true);

   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CArduino32Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CArduino32Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CArduino32Shutter::WriteToPort(long value)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   value = 63 & value;
   if (hub->IsLogicInverted())
      value = ~value;

   hub->PurgeComPortH();

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = hub->ReadFromComPortH(answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

   hub->SetTimedOutput(false);

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduino32Shutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)hub->GetShutterState());
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      if (pos == 0)
         ret = WriteToPort(0); // turn everything off
      else
         ret = WriteToPort(hub->GetSwitchState()); // restore old setting
      if (ret != DEVICE_OK)
         return ret;
      hub->SetShutterState(pos);
      changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}


/*
 * Arduino32 input.  Can either be for all pins (0-6)
 * or for an individual pin only
 */

CArduino32Input::CArduino32Input() :
   mThread_(0),
   pin_(0),
   name_(g_DeviceNameArduino32Input)
{
   std::string errorText = "To use the Input function you need firmware version 2 or higher";
   SetErrorText(ERR_VERSION_MISMATCH, errorText.c_str());

   CreateProperty("Pin", "All", MM::String, false, 0, true);
   AddAllowedValue("Pin", "All");
   AddAllowedValue("Pin", "0");
   AddAllowedValue("Pin", "1");
   AddAllowedValue("Pin", "2");
   AddAllowedValue("Pin", "3");
   AddAllowedValue("Pin", "4");
   AddAllowedValue("Pin", "5");

   CreateProperty("Pull-Up-Resistor", g_On, MM::String, false, 0, true);
   AddAllowedValue("Pull-Up-Resistor", g_On);
   AddAllowedValue("Pull-Up-Resistor", g_Off);

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   assert(DEVICE_OK == ret);

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Arduino32 shutter driver", MM::String, true);
   assert(DEVICE_OK == ret);

   // parent ID display
   CreateHubIDProperty();
}

CArduino32Input::~CArduino32Input()
{
   Shutdown();
}

int CArduino32Input::Shutdown()
{
   if (initialized_)
      delete(mThread_);
   initialized_ = false;
   return DEVICE_OK;
}

int CArduino32Input::Initialize()
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   char ver[MM::MaxStrLength] = "0";
   hub->GetProperty(g_versionProp, ver);
   int version = atoi(ver);
   if (version < 2)
      return ERR_VERSION_MISMATCH;

   int ret = GetProperty("Pin", pins_);
   if (ret != DEVICE_OK)
      return ret;
   if (strcmp("All", pins_) != 0)
      pin_ = atoi(pins_); 

   ret = GetProperty("Pull-Up-Resistor", pullUp_);
   if (ret != DEVICE_OK)
      return ret;
 
   // Digital Input
   CPropertyAction* pAct = new CPropertyAction (this, &CArduino32Input::OnDigitalInput);
   ret = CreateProperty("DigitalInput", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   int start = 0;
   int end = 5;
   if (strcmp("All", pins_) != 0) {
      start = pin_;
      end = pin_;
   }

   for (long i=start; i <=end; i++) 
   {
      CPropertyActionEx *pExAct = new CPropertyActionEx(this, &CArduino32Input::OnAnalogInput, i);
      std::ostringstream os;
      os << "AnalogInput" << i;
      ret = CreateProperty(os.str().c_str(), "0.0", MM::Float, true, pExAct);
      if (ret != DEVICE_OK)
         return ret;
      // set pull up resistor state for this pin
      if (strcmp(g_On, pullUp_) == 0) {
         SetPullUp(i, 1);
      } else {
         SetPullUp(i, 0);
      }

   }

   mThread_ = new Arduino32InputMonitorThread(*this);
   mThread_->Start();

   initialized_ = true;

   return DEVICE_OK;
}

void CArduino32Input::GetName(char* name) const
{
  CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CArduino32Input::Busy()
{
   return false;
}

int CArduino32Input::GetDigitalInput(long* state)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   unsigned char command[1];
   command[0] = 40;

   int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[2];
   ret = ReadNBytes(hub, 2, answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != 40)
      return ERR_COMMUNICATION;

   if (strcmp("All", pins_) != 0) {
      answer[1] = answer[1] >> pin_;
      answer[1] &= answer[1] & 1;
   }
   
   *state = (long) answer[1];

   return DEVICE_OK;
}

int CArduino32Input::ReportStateChange(long newState)
{
   std::ostringstream os;
   os << newState;
   return OnPropertyChanged("DigitalInput", os.str().c_str());
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduino32Input::OnDigitalInput(MM::PropertyBase*  pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long state;
      int ret = GetDigitalInput(&state);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(state);
   }

   return DEVICE_OK;
}

int CArduino32Input::OnAnalogInput(MM::PropertyBase* pProp, MM::ActionType eAct, long  channel )
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet)
   {
      MMThreadGuard myLock(hub->GetLock());

      unsigned char command[2];
      command[0] = 41;
      command[1] = (unsigned char) channel;

      int ret = hub->WriteToComPortH((const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      unsigned char answer[4];
      ret = ReadNBytes(hub, 4, answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer[0] != 41)
         return ERR_COMMUNICATION;
      if (answer[1] != channel)
         return ERR_COMMUNICATION;

      int tmp = answer[2];
      tmp = tmp << 8;
      tmp = tmp | answer[3];

      pProp->Set((long) tmp);
   }
   return DEVICE_OK;
}

int CArduino32Input::SetPullUp(int pin, int state)
{
   CArduino32Hub* hub = static_cast<CArduino32Hub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   const int nrChrs = 3;
   unsigned char command[nrChrs];
   command[0] = 42;
   command[1] = (unsigned char) pin;
   command[2] = (unsigned char) state;

   int ret = hub->WriteToComPortH((const unsigned char*) command, nrChrs);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[3];
   ret = ReadNBytes(hub, 3, answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != 42)
      return ERR_COMMUNICATION;
   if (answer[1] != pin)
      return ERR_COMMUNICATION;

   return DEVICE_OK;
}


int CArduino32Input::ReadNBytes(CArduino32Hub* hub, unsigned int n, unsigned char* answer)
{
   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   while ((bytesRead < n) && ( (GetCurrentMMTime() - startTime).getMsec() < 500)) {
      unsigned long bR;
      int ret = hub->ReadFromComPortH(answer + bytesRead, n - bytesRead, bR);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += bR;
   }

   return DEVICE_OK;
}

Arduino32InputMonitorThread::Arduino32InputMonitorThread(CArduino32Input& aInput) :
   state_(0),
   aInput_(aInput)
{
}

Arduino32InputMonitorThread::~Arduino32InputMonitorThread()
{
   Stop();
   wait();
}

int Arduino32InputMonitorThread::svc() 
{
   while (!stop_)
   {
      long state;
      int ret = aInput_.GetDigitalInput(&state);
      if (ret != DEVICE_OK)
      {
         stop_ = true;
         return ret;
      }

      if (state != state_) 
      {
         aInput_.ReportStateChange(state);
         state_ = state;
      }
      CDeviceUtils::SleepMs(500);
   }
   return DEVICE_OK;
}


void Arduino32InputMonitorThread::Start()
{
   stop_ = false;
   activate();
}

