///////////////////////////////////////////////////////////////////////////////
// FILE:          Arduino.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Arduino adapter.  Needs acompanying firmware
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       LGPL
// 
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 11/09/2008
//
//

#include "Arduino.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

const char* g_DeviceNameArduinoHub = "Arduino-Hub";
const char* g_DeviceNameArduinoSwitch = "Arduino-Switch";
const char* g_DeviceNameArduinoShutter = "Arduino-Shutter";
const char* g_DeviceNameArduinoDA = "Arduino-DAC";
const char* g_DeviceNameArduinoInput = "Arduino-Input";


// Global info about the state of the Arduino.  This should be folded into a class
unsigned g_switchState = 0;
unsigned g_shutterState = 0;
std::string g_port;
MMThreadLock g_lock;
int g_version;
const int g_Min_MMVersion = 1;
const int g_Max_MMVersion = 2;
bool g_portAvailable = false;
bool g_invertedLogic = false;
bool g_triggerMode = false;
bool g_timedOutputActive = false;
const char* g_normalLogicString = "Normal";
const char* g_invertedLogicString = "Inverted";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameArduinoHub, "Hub");
   AddAvailableDeviceName(g_DeviceNameArduinoSwitch, "Switch");
   AddAvailableDeviceName(g_DeviceNameArduinoShutter, "Shutter");
   AddAvailableDeviceName(g_DeviceNameArduinoDA, "DA");
   AddAvailableDeviceName(g_DeviceNameArduinoInput, "Input");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameArduinoHub) == 0)
   {
      return new CArduinoHub;
   }
   else if (strcmp(deviceName, g_DeviceNameArduinoSwitch) == 0)
   {
      return new CArduinoSwitch;
   }
   else if (strcmp(deviceName, g_DeviceNameArduinoShutter) == 0)
   {
      return new CArduinoShutter;
   }
   else if (strcmp(deviceName, g_DeviceNameArduinoDA) == 0)
   {
      return new CArduinoDA();
   }
   else if (strcmp(deviceName, g_DeviceNameArduinoInput) == 0)
   {
      return new CArduinoInput;
   }



   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CArduinoHUb implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~
//
CArduinoHub::CArduinoHub() :
initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_PORT_OPEN_FAILED, "Failed opening Arduino USB device");
   SetErrorText(ERR_BOARD_NOT_FOUND, "Did not find an Arduino board with the correct firmware.  Is the Arduino board connected to this serial port?");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino Hub device is needed to create this device");
   std::ostringstream errorText;
   errorText << "The firmware version on the Arduino is not compatible with this adapter.  Please use firmware version ";
   errorText <<  g_Min_MMVersion << " to " << g_Max_MMVersion;
   SetErrorText(ERR_VERSION_MISMATCH, errorText.str().c_str());

   CPropertyAction* pAct = new CPropertyAction(this, &CArduinoHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   pAct = new CPropertyAction(this, &CArduinoHub::OnLogic);
   CreateProperty("Logic", g_invertedLogicString, MM::String, false, pAct, true);

   AddAllowedValue("Logic", g_invertedLogicString);
   AddAllowedValue("Logic", g_normalLogicString);
}

CArduinoHub::~CArduinoHub()
{
   Shutdown();
}

void CArduinoHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduinoHub);
}

bool CArduinoHub::Busy()
{
   return false;
}

// private and expects caller to:
// 1. guard the port
// 2. purge the port
int CArduinoHub::GetControllerVersion(int& version)
{
   int ret = DEVICE_OK;
   unsigned char command[1];
   command[0] = 30;
   version = 0;

   ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(g_port.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer != "MM-Ard")
      return ERR_BOARD_NOT_FOUND;

   // Check version number of the Arduino
   command[0] = 31;
   ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   std::string ans;
   ret = GetSerialAnswer(g_port.c_str(), "\r\n", ans);
   if (ret != DEVICE_OK) {
         return ret;
   }
   std::istringstream is(ans);
   is >> version;

   return ret;

}

MM::DeviceDetectionStatus CArduinoHub::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   std::vector< std::string> propertiesToRestore;
   std::map< std::string, std::string> valuesToRestore;

   // gather the properties that will be restored if we don't find the device
   propertiesToRestore.push_back(MM::g_Keyword_BaudRate);
   propertiesToRestore.push_back(MM::g_Keyword_DataBits);
   propertiesToRestore.push_back(MM::g_Keyword_StopBits);
   propertiesToRestore.push_back(MM::g_Keyword_Parity);
   propertiesToRestore.push_back(MM::g_Keyword_Handshaking);
   propertiesToRestore.push_back("AnswerTimeout");
   propertiesToRestore.push_back("DelayBetweenCharsMs");
   
   try
   {
      std::string portLowerCase = g_port;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         // record the default parameters
         char previousValue[MM::MaxStrLength];
         for( std::vector< std::string>::iterator sit = propertiesToRestore.begin(); sit!= propertiesToRestore.end(); ++sit)
         {
            GetCoreCallback()->GetDeviceProperty(g_port.c_str(),(*sit).c_str(), previousValue);
            valuesToRestore[*sit] = std::string(previousValue);
         }  
         // device specific default communication parameters
         // for Arduino Duemilanova
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), MM::g_Keyword_BaudRate, "57600" );
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), MM::g_Keyword_StopBits, "1");
         // Arduino timed out in GetControllerVersion even if AnswerTimeout  = 300 ms
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, g_port.c_str());
         pS->Initialize();
         MMThreadGuard myLock(g_lock);
         PurgeComPort(g_port.c_str());
         int v = 0;
         int ret = GetControllerVersion(v);
         // later, Initialize will explicitly check the version #
         v = v;
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
         GetCoreCallback()->SetDeviceProperty(g_port.c_str(), "AnswerTimeout", (valuesToRestore["AnswerTimeout"]).c_str());

      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   // if the device is not there, restore the parameters to the original settings
   if ( MM::CanCommunicate != result)
   {

      for( std::vector< std::string>::iterator sit = propertiesToRestore.begin(); sit!= propertiesToRestore.end(); ++sit)
      {
         try
         {
            GetCoreCallback()->SetDeviceProperty(g_port.c_str(), (*sit).c_str(), (valuesToRestore[*sit]).c_str());
         }
         catch(...)
         {}
      }

   }
   return result;
}


int CArduinoHub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduinoHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // The first second or so after opening the serial port, the Arduino is waiting for firmwareupgrades.  Simply sleep 1 second.
   CDeviceUtils::SleepMs(2000);

   MMThreadGuard myLock(g_lock);

   // Check that we have a controller:
   PurgeComPort(g_port.c_str());
   ret = GetControllerVersion(g_version);
   if( DEVICE_OK != ret)
      return ret;

   if (g_version < g_Min_MMVersion || g_version > g_Max_MMVersion)
      return ERR_VERSION_MISMATCH;

   CPropertyAction* pAct = new CPropertyAction(this, &CArduinoHub::OnVersion);
   std::ostringstream sversion;
   sversion << g_version;
   CreateProperty("Version", sversion.str().c_str(), MM::Integer, true, pAct);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // turn off verbose serial debug messages
   GetCoreCallback()->SetDeviceProperty(g_port.c_str(), "Verbose", "0");

   initialized_ = true;
   return DEVICE_OK;
}

int CArduinoHub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduinoHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(g_port.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(g_port);
      g_portAvailable = true;
   }
   return DEVICE_OK;
}

int CArduinoHub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set((long)g_version);
   }
   return DEVICE_OK;
}

int CArduinoHub::OnLogic(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      if (g_invertedLogic)
         pProp->Set(g_invertedLogicString);
      else
         pProp->Set(g_normalLogicString);
   } else if (pAct == MM::AfterSet)
   {
      std::string logic;
      pProp->Get(logic);
      if (logic.compare(g_invertedLogicString)==0)
         g_invertedLogic = true;
      else g_invertedLogic = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CArduinoSwitch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduinoSwitch::CArduinoSwitch() : 
   nrPatternsUsed_(0),
   currentDelay_(0),
   blanking_(false),
   initialized_(false),
   numPos_(64), 
   busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
   SetErrorText(ERR_COMMUNICATION, "Error in communication with Arduino board");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino Hub device is needed to create this device");

   for (int i=0; i < NUMPATTERNS; i++)
      pattern_[i] = 0;
}

CArduinoSwitch::~CArduinoSwitch()
{
   Shutdown();
}

void CArduinoSwitch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameArduinoSwitch);
}


int CArduinoSwitch::Initialize()
{
   if (!g_portAvailable) {
      return ERR_NO_PORT_SET;
   }

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameArduinoSwitch, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Arduino digital output driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 65;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CArduinoSwitch::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits(MM::g_Keyword_State, 0, numPos_ - 1);

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // Patterns are used for triggered output
   // Set Pattern will set the current switch state in the Arduino as the specified pattern
   // Get Pattern will match the current switch state to specified patterns
   // Beside the switch State, also the Delay will be stored
   pAct = new CPropertyAction (this, &CArduinoSwitch::OnSetPattern);
   nRet = CreateProperty("SetPattern", "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   pAct = new CPropertyAction (this, &CArduinoSwitch::OnGetPattern);
   nRet = CreateProperty("GetPattern", "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue("SetPattern", "");
   AddAllowedValue("GetPattern", "");
   for (int i=0; i< NUMPATTERNS; i++) {
      std::ostringstream os;
      os.fill(' ');
      os.width(2);
      os << i;
      os.flags(std::ios::left);
      AddAllowedValue("SetPattern", os.str().c_str());
      AddAllowedValue("GetPattern", os.str().c_str());
   }

   // Sets how many of the patterns will actually be used
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnPatternsUsed);
   nRet = CreateProperty("Nr. Patterns Used", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Nr. Patterns Used", 0, NUMPATTERNS);

   // How many triggers to skip before firing digital patterns in trigger mode
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnSkipTriggers);
   nRet = CreateProperty("Skip Patterns (Nr.)", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // Starts producing digital output patterns upon external triggers
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnStartTrigger);
   nRet = CreateProperty("TriggerMode", "Idle", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue("TriggerMode", "Stop");
   AddAllowedValue("TriggerMode", "Start");
   AddAllowedValue("TriggerMode", "Running");
   AddAllowedValue("TriggerMode", "Idle");

   // Starts "blanking" mode: goal is to synchronize laser light with camera exposure
   std::string blankMode = "Blanking Mode";
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnBlanking);
   nRet = CreateProperty(blankMode.c_str(), "Idle", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue(blankMode.c_str(), "Stop");
   AddAllowedValue(blankMode.c_str(), "Start");
   AddAllowedValue(blankMode.c_str(), "Running");
   AddAllowedValue(blankMode.c_str(), "Idle");

   // Starts producing timed digital output patterns 
   // Parameters that influence the pattern are 'Repeat Timed Pattern', 'Delay', 'State' where the latter two are manipulated with the Get and SetPattern functions
   std::string timedOutput = "Timed Output Mode";
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnStartTimedOutput);
   nRet = CreateProperty(timedOutput.c_str(), "Idle", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue(timedOutput.c_str(), "Stop");
   AddAllowedValue(timedOutput.c_str(), "Start");
   AddAllowedValue(timedOutput.c_str(), "Running");
   AddAllowedValue(timedOutput.c_str(), "Idle");

   // Blank on TTL high or low
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnBlankingTriggerDirection);
   nRet = CreateProperty("Blank On", "Low", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue("Blank On", "Low");
   AddAllowedValue("Blank On", "High");

   // Sets a delay (in ms) to be used in timed output mode
   // This delay will be transferred to the Arduino using the Get and SetPattern commands
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnDelay);
   nRet = CreateProperty("Delay (ms)", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Delay (ms)", 0, 65535);

   // Repeat the timed Pattern this many times:
   pAct = new CPropertyAction(this, &CArduinoSwitch::OnRepeatTimedPattern);
   nRet = CreateProperty("Repeat Timed Pattern", "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Repeat Timed Pattern", 0, 255);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CArduinoSwitch::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduinoSwitch::WriteToPort(long value)
{
   MMThreadGuard myLock(g_lock);

   value = 63 & value;
   if (g_invertedLogic)
      value = ~value;

   PurgeComPort(g_port.c_str());

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = ReadFromComPort(g_port.c_str(), answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

   g_triggerMode = false;
   g_timedOutputActive = false;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduinoSwitch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      g_switchState = pos;
      if (g_shutterState > 0)
         return WriteToPort(pos);
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnSetPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // Never set anything here 
      pProp->Set(" ");
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      std::string tmp;
      pProp->Get(tmp);
      if (tmp == " ")
         return DEVICE_OK;
      int pos =  atoi(tmp.c_str());

      // Set digital pattern
      unsigned value = g_switchState;
      pattern_[pos] = g_switchState;

      value = 63 & value;
      if (g_invertedLogic)
         value = ~value;

      PurgeComPort(g_port.c_str());

      unsigned char command[3];
      command[0] = 5;
      command[1] = (unsigned char) pos;
      command[2] = (unsigned char) value;
      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 3);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[3];
      while ((bytesRead < 3) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 3, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 5)
         return ERR_COMMUNICATION;

      // Set delay
      delay_[pos] = currentDelay_;
      unsigned highByte = (currentDelay_ >> 8) & 255;
      unsigned lowByte = currentDelay_ & 255;

      PurgeComPort(g_port.c_str());

      unsigned char commandd[4];
      commandd[0] = 10;
      commandd[1] = (unsigned char) pos;
      commandd[2] = (unsigned char) highByte;
      commandd[3] = (unsigned char) lowByte;
      ret = WriteToComPort(g_port.c_str(), (const unsigned char*) commandd, 4);
      if (ret != DEVICE_OK)
         return ret;

      startTime = GetCurrentMMTime();
      bytesRead = 0;
      unsigned char answerd[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answerd + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }

      if (answerd[0] != 10)
         return ERR_COMMUNICATION;

      g_triggerMode = false;
      g_timedOutputActive = false;
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnGetPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // find the first pattern that corresponds to the current switch state
      int i = 0;
      bool found = false;
      while (!found && i<NUMPATTERNS) {
         if (pattern_[i] == g_switchState && delay_[i] == currentDelay_)
            found = true;
         else
            i++;
      }
      std::ostringstream os;
      if (found)
         os << i;
      else 
         os << " ";
      pProp->Set(os.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string tmp;
      pProp->Get(tmp);
      if (tmp == " ")
         return DEVICE_OK;
      int pos =  atoi(tmp.c_str());

      g_switchState = pattern_[pos];
      std::ostringstream os;
      os << g_switchState;
      SetProperty(MM::g_Keyword_State, os.str().c_str());
      currentDelay_ = delay_[pos];
      std::ostringstream oss;
      SetProperty("Delay", oss.str().c_str());
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnPatternsUsed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      long pos;
      pProp->Get(pos);

      PurgeComPort(g_port.c_str());

      unsigned char command[2];
      command[0] = 6;
      command[1] = (unsigned char) pos;
      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 6)
         return ERR_COMMUNICATION;

      g_triggerMode = false;
      g_timedOutputActive = false;
   }

   return DEVICE_OK;
}


int CArduinoSwitch::OnSkipTriggers(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      long prop;
      pProp->Get(prop);

      PurgeComPort(g_port.c_str());
      unsigned char command[2];
      command[0] = 7;
      command[1] = (unsigned char) prop;
      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 7)
         return ERR_COMMUNICATION;

      g_triggerMode = false;
      g_timedOutputActive = false;
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnStartTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (g_triggerMode)
         pProp->Set("Running");
      else
         pProp->Set("Idle");
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      std::string prop;
      pProp->Get(prop);

      if (prop =="Start") {
         PurgeComPort(g_port.c_str());
         unsigned char command[1];
         command[0] = 8;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[1];
         while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 1, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 8)
            return ERR_COMMUNICATION;
         g_triggerMode = true;
         g_timedOutputActive = false;
      } else {
         unsigned char command[1];
         command[0] = 9;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[2];
         while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 9)
            return ERR_COMMUNICATION;
         g_triggerMode = false;
         g_timedOutputActive = false;
      }
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnStartTimedOutput(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (g_timedOutputActive)
         pProp->Set("Running");
      else
         pProp->Set("Idle");
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      std::string prop;
      pProp->Get(prop);

      if (prop =="Start") {
         PurgeComPort(g_port.c_str());
         unsigned char command[1];
         command[0] = 12;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[1];
         while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 1, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 12)
            return ERR_COMMUNICATION;
         g_timedOutputActive = true;
         g_triggerMode = false;
      } else {
         unsigned char command[1];
         command[0] = 9;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[2];
         while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 9)
            return ERR_COMMUNICATION;
         g_triggerMode = false;
         g_timedOutputActive = false;
         g_timedOutputActive = false;
      }
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (blanking_)
         pProp->Set("Running");
      else
         pProp->Set("Idle");
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      std::string prop;
      pProp->Get(prop);

      if (prop =="Start" && !blanking_) {
         PurgeComPort(g_port.c_str());
         unsigned char command[1];
         command[0] = 20;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[1];
         while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 1, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 20)
            return ERR_COMMUNICATION;
         blanking_ = true;
         g_triggerMode = false;
         g_timedOutputActive = false;
      } else if (prop =="Stop" && blanking_){
         unsigned char command[1];
         command[0] = 21;
         int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
         if (ret != DEVICE_OK)
            return ret;

         MM::MMTime startTime = GetCurrentMMTime();
         unsigned long bytesRead = 0;
         unsigned char answer[2];
         while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
            unsigned long br;
            ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
            if (ret != DEVICE_OK)
               return ret;
            bytesRead += br;
         }
         if (answer[0] != 21)
            return ERR_COMMUNICATION;
         blanking_ = false;
         g_triggerMode = false;
         g_timedOutputActive = false;
      }
   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      std::string direction;
      pProp->Get(direction);


      PurgeComPort(g_port.c_str());
      unsigned char command[2];
      command[0] = 22;
      if (direction == "Low") 
         command[1] = 1;
      else
         command[1] = 0;

      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[1];
      while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 1, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 22)
         return ERR_COMMUNICATION;

   }

   return DEVICE_OK;
}

int CArduinoSwitch::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CArduinoSwitch::OnRepeatTimedPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
   }
   else if (eAct == MM::AfterSet)
   {
      MMThreadGuard myLock(g_lock);

      long prop;
      pProp->Get(prop);

      PurgeComPort(g_port.c_str());
      unsigned char command[2];
      command[0] = 11;
      command[1] = (unsigned char) prop;

      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 11)
         return ERR_COMMUNICATION;

      g_triggerMode = false;
      g_timedOutputActive = false;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CArduinoDA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

CArduinoDA::CArduinoDA() :
      busy_(false), 
      minV_(0.0), 
      maxV_(5.0), 
      volts_(0.0),
      gatedVolts_(0.0),
      encoding_(0), 
      resolution_(8), 
      channel_(1), 
      maxChannel_(2),
      name_(""),
      gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino Hub device is needed to create this device");

   CPropertyAction* pAct = new CPropertyAction(this, &CArduinoDA::OnChannel);
   CreateProperty("Channel", "2", MM::Integer, false, pAct, true);
   for (int i=1; i<= 2; i++){
      std::ostringstream os;
      os << i;
      AddAllowedValue("Channel", os.str().c_str());
   }

   pAct = new CPropertyAction(this, &CArduinoDA::OnMaxVolt);
   CreateProperty("MaxVolt", "5.0", MM::Float, false, pAct, true);

}

CArduinoDA::~CArduinoDA()
{
   Shutdown();
}

void CArduinoDA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CArduinoDA::Initialize()
{
   if (!g_portAvailable) {
      return ERR_NO_PORT_SET;
   }

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Arduino DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CArduinoDA::OnVolts);
   nRet = CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   SetPropertyLimits("Volts", minV_, maxV_);

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CArduinoDA::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int CArduinoDA::WriteToPort(unsigned long value)
{
   MMThreadGuard myLock(g_lock);

   PurgeComPort(g_port.c_str());

   unsigned char command[4];
   command[0] = 3;
   command[1] = (unsigned char) (channel_ -1);
   command[2] = (unsigned char) (value / 256L);
   command[3] = (unsigned char) (value & 255);
   int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 4);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[4];
   while ((bytesRead < 4) && ( (GetCurrentMMTime() - startTime).getMsec() < 2500)) {
      unsigned long bR;
      ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, 4 - bytesRead, bR);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += bR;
   }
   if (answer[0] != 3)
      return ERR_COMMUNICATION;

   g_triggerMode = false;
   g_timedOutputActive = false;

   return DEVICE_OK;
}


int CArduinoDA::WriteSignal(double volts)
{
   long value = (long) ( (volts - minV_) / maxV_ * 4095);

   std::ostringstream os;
    os << "Volts: " << volts << " Max Voltage: " << maxV_ << " digital value: " << value;
    LogMessage(os.str().c_str(), true);

   return WriteToPort(value);
}

int CArduinoDA::SetSignal(double volts)
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

int CArduinoDA::SetGateOpen(bool open)
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

int CArduinoDA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CArduinoDA::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CArduinoDA::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
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
// CArduinoShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CArduinoShutter::CArduinoShutter() : initialized_(false), name_(g_DeviceNameArduinoShutter)
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   SetErrorText(ERR_NO_PORT_SET, "Hub Device not found.  The Arduino Hub device is needed to create this device");
}

CArduinoShutter::~CArduinoShutter()
{
   Shutdown();
}

void CArduinoShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CArduinoShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;

   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   else
       return false;
}

int CArduinoShutter::Initialize()
{
   if (!g_portAvailable) {
      return ERR_NO_PORT_SET;
   }

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Arduino shutter driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CArduinoShutter::OnOnOff);
   ret = CreateProperty("OnOff", "0", MM::Integer, false, pAct);
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

int CArduinoShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CArduinoShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CArduinoShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CArduinoShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CArduinoShutter::WriteToPort(long value)
{
   MMThreadGuard myLock(g_lock);

   value = 63 & value;
   if (g_invertedLogic)
      value = ~value;

   PurgeComPort(g_port.c_str());

   unsigned char command[2];
   command[0] = 1;
   command[1] = (unsigned char) value;
   int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   unsigned char answer[1];
   while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
      ret = ReadFromComPort(g_port.c_str(), answer, 1, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
   }
   if (answer[0] != 1)
      return ERR_COMMUNICATION;

   g_triggerMode = false;
   g_timedOutputActive = false;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduinoShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // use cached state
      pProp->Set((long)g_shutterState);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      if (pos == 0)
         ret = WriteToPort(0); // turn everything off
      else
         ret = WriteToPort(g_switchState); // restore old setting
      if (ret != DEVICE_OK)
         return ret;
      g_shutterState = pos;
      changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}


/*
 * Arduino input.  Can either be for all pins (0-6)
 * or for an individual pin only
 */

CArduinoInput::CArduinoInput() :
   mThread_(0),
   pin_(0),
   name_(g_DeviceNameArduinoInput)
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

   CreateProperty("Pull-Up-Resistor", "On", MM::String, false, 0, true);
   AddAllowedValue("Pull-Up-Resistor", "On");
   AddAllowedValue("Pull-Up-Resistor", "Off");
}

CArduinoInput::~CArduinoInput()
{
   Shutdown();
}

int CArduinoInput::Shutdown()
{
   if (initialized_)
      delete(mThread_);
   initialized_ = false;
   return DEVICE_OK;
}

int CArduinoInput::Initialize()
{
   if (!g_portAvailable) {
      return ERR_NO_PORT_SET;
   }

   if (g_version < 2)
      return ERR_VERSION_MISMATCH;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = GetProperty("Pin", pins_);
   if (ret != DEVICE_OK)
      return ret;
   if (strcmp("All", pins_) != 0)
      pin_ = atoi(pins_); 

   ret = GetProperty("Pull-Up-Resistor", pullUp_);
   if (ret != DEVICE_OK)
      return ret;
 
   // Digital Input
   CPropertyAction* pAct = new CPropertyAction (this, &CArduinoInput::OnDigitalInput);
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
      CPropertyActionEx *pExAct = new CPropertyActionEx(this, &CArduinoInput::OnAnalogInput, i);
      std::ostringstream os;
      os << "AnalogInput" << i;
      ret = CreateProperty(os.str().c_str(), "0.0", MM::Float, true, pExAct);
      if (ret != DEVICE_OK)
         return ret;
      // set pull up resistor state for this pin
      if (strcmp("On", pullUp_) == 0) {
         SetPullUp(i, 1);
      } else {
         SetPullUp(i, 0);
      }

   }

   mThread_ = new ArduinoInputMonitorThread(*this, true);
   mThread_->Start();

   initialized_ = true;

   return DEVICE_OK;
}

void CArduinoInput::GetName(char* name) const
{
  CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CArduinoInput::Busy()
{
   return false;
}

int CArduinoInput::GetDigitalInput(long* state)
{
   MMThreadGuard myLock(g_lock);

   unsigned char command[1];
   command[0] = 40;

   int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 1);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[2];
   ret = ReadNBytes(2, answer);
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

int CArduinoInput::ReportStateChange(long newState)
{
   std::ostringstream os;
   os << newState;
   return OnPropertyChanged("DigitalInput", os.str().c_str());
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CArduinoInput::OnDigitalInput(MM::PropertyBase*  pProp, MM::ActionType eAct)
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

int CArduinoInput::OnAnalogInput(MM::PropertyBase* pProp, MM::ActionType eAct, long  channel )
{
   if (eAct == MM::BeforeGet)
   {
      MMThreadGuard myLock(g_lock);

      unsigned char command[2];
      command[0] = 41;
      command[1] = (unsigned char) channel;

      int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, 2);
      if (ret != DEVICE_OK)
         return ret;

      unsigned char answer[4];
      ret = ReadNBytes(4, answer);
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

int CArduinoInput::SetPullUp(int pin, int state)
{
   MMThreadGuard myLock(g_lock);

   const int nrChrs = 3;
   unsigned char command[nrChrs];
   command[0] = 42;
   command[1] = (unsigned char) pin;
   command[2] = (unsigned char) state;

   int ret = WriteToComPort(g_port.c_str(), (const unsigned char*) command, nrChrs);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[3];
   ret = ReadNBytes(3, answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != 42)
      return ERR_COMMUNICATION;
   if (answer[1] != pin)
      return ERR_COMMUNICATION;

   return DEVICE_OK;
}


int CArduinoInput::ReadNBytes(unsigned int n, unsigned char* answer)
{
   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long bytesRead = 0;
   while ((bytesRead < n) && ( (GetCurrentMMTime() - startTime).getMsec() < 500)) {
      unsigned long bR;
      int ret = ReadFromComPort(g_port.c_str(), answer + bytesRead, n - bytesRead, bR);
      if (ret != DEVICE_OK)
         return ret;
      bytesRead += bR;
   }

   return DEVICE_OK;
}

ArduinoInputMonitorThread::ArduinoInputMonitorThread(CArduinoInput& aInput, bool debug) :
   state_(0),
   aInput_(aInput),
   debug_(debug)
{
};

ArduinoInputMonitorThread::~ArduinoInputMonitorThread()
{
   Stop();
   wait();
}

int ArduinoInputMonitorThread::svc() 
{
   while (!stop_)
   {
      long state;
      aInput_.GetDigitalInput(&state);
      if (state != state_) 
      {
         aInput_.ReportStateChange(state);
         state_ = state;
      }
      CDeviceUtils::SleepMs(500);
   }
   return DEVICE_OK;
}


void ArduinoInputMonitorThread::Start()
{
   stop_ = false;
   activate();
}

