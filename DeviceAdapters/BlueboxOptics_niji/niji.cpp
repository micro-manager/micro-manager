///////////////////////////////////////////////////////////////////////////////
// FILE:          niji.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   niji controller adapter
// APPLIES TO:    niji with firmware version >= V2.101.000(Beta)
//                (please contact Bluebox Optics if your firmware is older)
//
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 2017-08-15
//
// BASED ON:      CoherentCube, UserDefinedSerial and PVCAM
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



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <boost/lexical_cast.hpp>
#include <string>
#include "niji.h"

// Controller
const char* g_ControllerName = "niji";
const char* g_Keyword_Prefix = "Channel";
const char* g_Keyword_MultipleSelection = "Multiple selection";
const char* g_Keyword_Intensity = "Intensity";
const char* g_Keyword_State = "State";
const char* g_Keyword_ChannelLabel = "ChannelLabel";
const char* g_Keyword_TriggerSource = "TriggerSource";
const char* g_Keyword_TriggerLogic = "TriggerLogic";
const char* g_Keyword_TriggerResistor = "TriggerResistor";

//Readonly properties
const char* g_Keyword_Temp1 = "OutputTemp";
const char* g_Keyword_Temp2 = "AmbientTemp";
const char* g_Keyword_Firmware = "FirmwareVersion";
const char* g_Keyword_GlobalStatus = "GlobalStatus";
const char* g_Keyword_GlobalIntensity = "GlobalIntensity";

const char* terminator = "\r\n";

// The wavelengths expected in a 7 LED niji...
const char* g_ChannelNames[] = {"395/14", "445/15", "470/25", "515/30", "575/25 or 554/23", "630/20", "745/30"};
const char* g_TriggerSources[] = {"Internal", "External"};
const char* g_TriggerLogics[] = {"Active Low", "Active High"};
const char* g_TriggerResistors[] = {"Pull Down", "Pull Up"};


void CreatePropertyName(string& Name, const char* prefix, const char *suffix, long index)
{
   Name.erase();
   Name = prefix;
   Name += boost::lexical_cast<std::string>(index+1);
   Name += suffix;
}

// static lock
MMThreadLock Controller::lock_;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::ShutterDevice, "niji LED illuminator");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      Controller* pController = new Controller(g_ControllerName);
      return pController;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
///////////////////////////////////////////////////////////////////////////////

Controller::Controller(const char* name) :
   initialized_(false), 
   state_(0),
   intensity_(100),
   name_(name), 
   busy_(false),
   error_(0),
   mThread_(0),
   hasUpdated_(true),
   globalStatus_(0),
   tempOutput_(0.0),
   tempAmbient_(0.0),
   firmwareVersion_(""),
   triggerSource_(0),
   triggerLogic_(1),
   triggerResistor_(1),
   changedTime_(0.0)
{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "niji LED Illuminator", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Controller::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   EnableDelay(); // signals that the delay setting will be used
   UpdateStatus();
}

Controller::~Controller()
{
   Shutdown();
}

bool Controller::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}

void Controller::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int Controller::Initialize()
{

   this->LogMessage("Controller::Initialize()");
   // set property list
   // -----------------

   MM::Device* serialDevice = GetCoreCallback()->GetDevice(this, port_.c_str());
   if (serialDevice == NULL)
   {
      LogMessage("Serial port not found");
      return DEVICE_SERIAL_COMMAND_FAILED;
   }

   //saving original baud rate choice
   GetCoreCallback()->GetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, (char *)buf_);

   GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
   SendSerialCommand(port_.c_str(), "\x4f\xff\x50\x4f\xff\x50\x4f\xff\x50\x4f\xff\x50", "");
   CDeviceUtils::SleepMs(100);

   //restoring original choice...
   GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, (char *)buf_ );
   PurgeComPort(port_.c_str());

   //The channels are hard coded for now.
   int result = ReadChannelLabels();
   if (result != DEVICE_OK)
      return result;

   GenerateStateProperties();
   GenerateIntensityProperties();
   GenerateChannelChooser();
   GenerateTriggerProperties();
   GenerateReadOnlyProperties();
   
   //This queries the number of lines returned by the "r" and "?" commands
   ReadGreeting();

   initialized_ = true;

   mThread_ = new PollingThread(*this);
   mThread_->Start();

   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Property Generators
///////////////////////////////////////////////////////////////////////////////

//With this property, we can easily choose a single wavelength. There are individual state properties if more than one wavelength must be activated at once.
void Controller::GenerateChannelChooser()
{
   if (! channelLabels_.empty()) {   
      CPropertyAction* pAct;
      pAct = new CPropertyAction (this, &Controller::OnChannelLabel);
      CreateProperty(g_Keyword_ChannelLabel, "", MM::String, false, pAct);

      SetAllowedValues(g_Keyword_ChannelLabel, channelLabels_);
      //SetProperty(g_Keyword_ChannelLabel, channelLabels_[0].c_str());
            
   }
}

void Controller::GenerateIntensityProperties()
{
   // The master intensity property affects all the intensities simultaneously.
   //
   // An LED intensity in percent is therefore (0.01*intensity_) * LEDxIntensity
   //
   // This can be used to cap the intensities to a Maximum output or
   // to provide a means of changing all the intensities with a single master control.
   //
   // NOTE: By default, all the intensities, including the global intensity is 100%
   // This is to avoid any confusion as to whether the niji is working or not.
   // If needed, intensities can be set in mmStartup.bsh:
   //
   // sd = mmc.getShutterDevice();
   // for (long index=1; index < 8; index++)
   //    mmc.setProperty(sd, "Channel"+index+"Intensity", "20");
   //

   CPropertyAction* pAct;
   pAct = new CPropertyAction (this, &Controller::OnIntensity);
   CreateProperty(g_Keyword_GlobalIntensity, "100", MM::Integer, false, pAct);
   SetPropertyLimits(g_Keyword_GlobalIntensity, 0, 100);

   string propertyName;
   CPropertyActionEx* pActEx; 
   for (long index=0; index<NLED; index++) 
   {
      pActEx = new CPropertyActionEx(this, &Controller::OnChannelIntensity, index);
      CreatePropertyName(propertyName,g_Keyword_Prefix,"Intensity",index);
      CreateProperty(propertyName.c_str(), "100", MM::Integer, false, pActEx);
      SetPropertyLimits(propertyName.c_str(), 0, 100);

      // channel intensities are initialized to 100%
      channelIntensities_.push_back(100);
      SetChannelIntensity(100, index);
   }
}

//Generate the shutter state, as well as individual states for each channel
void Controller::GenerateStateProperties()
{
   //The general state turns all the individual channels on and off.
   CPropertyAction* pAct;
   pAct = new CPropertyAction (this, &Controller::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   //then we can control each of the individual channel states separately
   string propertyName;
   CPropertyActionEx* pActEx; 
   for (long index=0; index<NLED; index++) 
   {
      pActEx = new CPropertyActionEx(this, &Controller::OnChannelState, index);
      CreatePropertyName(propertyName,g_Keyword_Prefix,"State",index);
      CreateProperty(propertyName.c_str(), "0", MM::Integer, false, pActEx);
      SetPropertyLimits(propertyName.c_str(), 0, 1);
      AddAllowedValue(propertyName.c_str(), "0");
      AddAllowedValue(propertyName.c_str(), "1");

      //channel states are initialized to 0
      channelStates_.push_back(-1);
      SetChannelState(0, index);
   }
}

void Controller::GenerateTriggerProperties()
{
   CPropertyAction* pAct;

   //Trigger Source (default internal)
   pAct = new CPropertyAction (this, &Controller::OnTriggerSource);
   CreateProperty(g_Keyword_TriggerSource , g_TriggerSources[0], MM::String, false, pAct);
   AddAllowedValue(g_Keyword_TriggerSource , g_TriggerSources[0]);
   AddAllowedValue(g_Keyword_TriggerSource , g_TriggerSources[1]);
   SetProperty(g_Keyword_TriggerSource , g_TriggerSources[0]);

   //Trigger Logic (default active high)
   pAct = new CPropertyAction (this, &Controller::OnTriggerLogic);
   CreateProperty(g_Keyword_TriggerLogic , g_TriggerLogics[0], MM::String, false, pAct);
   AddAllowedValue(g_Keyword_TriggerLogic , g_TriggerLogics[0]);
   AddAllowedValue(g_Keyword_TriggerLogic , g_TriggerLogics[1]);
   SetProperty(g_Keyword_TriggerLogic , g_TriggerLogics[1]);

   //Trigger Resistor (default pull-up)
   pAct = new CPropertyAction (this, &Controller::OnTriggerResistor);
   CreateProperty(g_Keyword_TriggerResistor , g_TriggerResistors[0], MM::String, false, pAct);
   AddAllowedValue(g_Keyword_TriggerResistor , g_TriggerResistors[0]);
   AddAllowedValue(g_Keyword_TriggerResistor , g_TriggerResistors[1]);
   SetProperty(g_Keyword_TriggerResistor , g_TriggerResistors[1]);
}

void Controller::GenerateReadOnlyProperties()
{
   CPropertyAction* pAct;

   pAct = new CPropertyAction(this, &Controller::OnFirmwareVersion);
   CreateProperty(g_Keyword_Firmware , firmwareVersion_.c_str(), MM::String, true, pAct);

   pAct = new CPropertyAction(this, &Controller::OnTemperature1);
   CreateProperty(g_Keyword_Temp1, CDeviceUtils::ConvertToString(tempOutput_), MM::Float, true, pAct);
   
   pAct = new CPropertyAction(this, &Controller::OnTemperature2);
   CreateProperty(g_Keyword_Temp2, CDeviceUtils::ConvertToString(tempAmbient_), MM::Float, true, pAct);
   
   pAct = new CPropertyAction(this, &Controller::OnGlobalStatus);
   CreateProperty(g_Keyword_GlobalStatus, CDeviceUtils::ConvertToString(globalStatus_), MM::Integer, true, pAct);
}


int Controller::Shutdown()
{
   if (initialized_)
   {
      for (long index=0; index<NLED; index++)
      {
         SetChannelState(0, index);
         SetChannelIntensity(0, index);
      }

      initialized_ = false;
      delete(mThread_);
   }
   return HandleErrors();
}



///////////////////////////////////////////////////////////////////////////////
// String utilities
///////////////////////////////////////////////////////////////////////////////


void Controller::StripString(string& StringToModify)
{
   if(StringToModify.empty()) return;

   const char* spaces = " \f\n\r\t\v";
   size_t startIndex = StringToModify.find_first_not_of(spaces);
   size_t endIndex = StringToModify.find_last_not_of(spaces);
   string tempString = StringToModify;
   StringToModify.erase();

   StringToModify = tempString.substr(startIndex, (endIndex-startIndex+ 1) );
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int Controller::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return HandleErrors();
}


int Controller::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long state;
   if (eAct == MM::BeforeGet)
   {
      GetState(state);
      pProp->Set(state);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(state);
      SetState(state);

      Illuminate();
      hasUpdated_ = true;
   }
   
   return HandleErrors();
}


int Controller::OnChannelLabel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(currentChannelLabel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(currentChannelLabel_);
      SetActiveChannel(currentChannelLabel_);
      hasUpdated_ = true;
   }

   return HandleErrors();
}


int Controller::OnChannelState(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   long state;
   if (eAct == MM::BeforeGet)
   {
      GetChannelState(state,index);
      pProp->Set(state);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(state);
      SetChannelState(state, index);
      UpdateChannelLabel();
      hasUpdated_ = true;
   }

   return HandleErrors();
}


int Controller::OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{

   long intensity;
   if (eAct == MM::BeforeGet)
   {
      GetChannelIntensity(intensity, index);
      pProp->Set(intensity);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(intensity);
      SetChannelIntensity(intensity, index);
      hasUpdated_ = true;
   }

   return HandleErrors();
}

int Controller::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   long intensity;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(intensity_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(intensity);
      SetGlobalIntensity(intensity);
      hasUpdated_ = true;
   }

   return HandleErrors();
}

int Controller::OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(g_TriggerSources[triggerSource_]);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);

      for (long index=0; index<2; index++) 
      {
         if (state.compare(g_TriggerSources[index])==0)
         {
            triggerSource_ = index;
            break;
         }
      }
      SetTrigger();
      hasUpdated_ = true;
   }
   return HandleErrors();
}


int Controller::OnTriggerLogic(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(g_TriggerLogics[triggerLogic_]);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      for (long index=0; index<2; index++) 
      {
         if (state.compare(g_TriggerLogics[index])==0)
         {
            triggerLogic_ = index;
            break;
         }
      }
      SetTrigger();
      hasUpdated_ = true;
   }
   return HandleErrors();
}


int Controller::OnTriggerResistor(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(g_TriggerResistors[triggerResistor_]);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      for (long index=0; index<2; index++) 
      {
         if (state.compare(g_TriggerResistors[index])==0)
         {
            triggerResistor_ = index;
            break;
         }
      }
      SetTrigger();
      hasUpdated_ = true;
   }
   return HandleErrors();
}


//
//Read-only properties...
//
//
int Controller::OnTemperature1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(tempOutput_);
   }
   else if (eAct == MM::AfterSet)
   {
         // never do anything!!
   }
   return HandleErrors();
}


int Controller::OnTemperature2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(tempAmbient_);
   }
   else if (eAct == MM::AfterSet)
   {
         // never do anything!!
   }
   return HandleErrors();
}


int Controller::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(firmwareVersion_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
         // never do anything!!
   }
   return HandleErrors();
}


int Controller::OnGlobalStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(globalStatus_);
   }
   else if (eAct == MM::AfterSet)
   {
         // never do anything!!
   }
   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

//
//For now, these are hardcoded in the device adapter.
int Controller::ReadChannelLabels()
{
   for (long index=0; index<NLED; index++) 
      channelLabels_.push_back(g_ChannelNames[index]);

   return DEVICE_OK;
}

// <Command>, <ON/OFF>, <Active State>, <Pull up/down>
void Controller::SetTrigger()
{
   stringstream msg;
   msg << "TTL," << triggerSource_ << "," << triggerLogic_ << "," << triggerResistor_;
   {
      MMThreadGuard myLock(lock_);
      Purge();
      Send(msg.str());
      ReadResponseLines(1);
   }
}

//This only changes the states vector. You need to also illuminate() if you want to output light
void Controller::SetActiveChannel(long channel)
{
   long channelIndex = -1;
   for (long index=0; index<NLED; index++)
   {
      if (index == channel)
      {
         channelStates_[index] = 1;
         channelIndex = index;
      }
      else
         channelStates_[index] = 0;
   }

   if (state_ == 1 && channelIndex >= 0)
       Illuminate();
}

void Controller::SetActiveChannel(string channelName)
{
   long channelIndex = -1;
   for (long index=0; index<NLED; index++)
   {
      if (channelLabels_[index].compare(channelName) == 0)
      {
         channelStates_[index] = 1;
         channelIndex = index;
      }
      else
         channelStates_[index] = 0;
   }

   if (state_ == 1 && channelIndex >= 0)
       Illuminate();
}

void Controller::GetActiveChannel(long &channel)
{
   channel = -1;
   for (long index=0; index<NLED; index++)
   {
      if ( channelStates_[index] == 1)
      {
         channel = index;
         break;
      }
   }
}

void Controller::SetState(long state)
{
   state_ = state;
}

void Controller::GetState(long &state)
{
   state = state_;
}

void Controller::UpdateChannelLabel()
{
   //Count how many LED channels are currently active
   long nActive = 0;
   long index = 0;
   for (long i=0; i<NLED; i++)
   {
      if (channelStates_[i] == 1) {
         nActive++;
         index = i;
      }
   }

   if (nActive == 1)
   {
      //With exactly one channel active, the current channel label
      //is set according to the index passed to SetChannelState()
      currentChannelLabel_ = g_ChannelNames[index];
      state_ = 1;
   }
   else if (nActive > 1)
   {
      //The channel label is changed to the multiple selection string
      //if we have more than one LED selected
      currentChannelLabel_ = g_Keyword_MultipleSelection;
      state_ = 1;
   }
   else state_ = 0;
}

void Controller::SetChannelState(long state, long index)
{
   long channelState = channelStates_[index];
   channelStates_[index] = state;

   //Only send a serial command if the shutter is open (and we are actually changing the channel state)
   //However, when we are initializing, we really want all the LEDs to be off
   if (channelState < 0 || (state_ == 1 && channelState != state))
   {
      stringstream msg;
      msg << "D," << index+1 << "," << state;

      {
         MMThreadGuard myLock(lock_);
         Purge();
         Send(msg.str());
         ReceiveOneLine();
      }
   }
}

//TODO We do not have a means of getting the current state, so relying on the saved version
void Controller::GetChannelState(long& state, long index)
{
   state = channelStates_[index];
}

void Controller::SetGlobalIntensity(long intensity)
{
   intensity_ = intensity;

   //Now update the intensities...
   stringstream msg;

   {
      MMThreadGuard myLock(lock_);
      Purge();

      for (long index=0; index<NLED; index++) 
      {
         msg.str("");
         msg.clear();

         intensity = channelIntensities_[index];
         msg << "d," << index+1 << "," << (long)(0.01*(double)intensity_*(double)intensity);
         Send(msg.str());
         ReadResponseLines(1);
      }
   }
}

void Controller::SetChannelIntensity(long intensity, long index)
{
   channelIntensities_[index] = intensity;

   stringstream msg;
   msg << "d," << index+1 << "," << (long)(0.01*(double)intensity_*(double)intensity);

   {
      MMThreadGuard myLock(lock_);
      Purge();
      Send(msg.str());
      ReadResponseLines(1);
   }
}

//TODO We do not have a means of getting the current intensity, so relying on the saved version
void Controller::GetChannelIntensity(long& intensity, long index)
{
   intensity = channelIntensities_[index];
}

//Illuminates locks the thread until all NLED states are set
void Controller::Illuminate()
{
   stringstream msg;
   long channelState;

   {
      MMThreadGuard myLock(lock_);
      Purge();
      for (long index=0; index<NLED; index++)
      {
         channelState = (state_ == 0)?0:channelStates_[index];
         msg.str("");
         msg.clear();
         msg << "D," << index+1 << "," << channelState;
         Send(msg.str());
         ReadResponseLines(1);
      }
   }
}



///////////////////////////////////////////////////////////////////////////////
//  Communications


void Controller::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), terminator);
   if (ret!=DEVICE_OK)
   {
      LogMessage("Send issue...");
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
   }

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
}


void Controller::ReceiveOneLine()
{
   buf_string_.clear();
   //CDeviceUtils::SleepMs(100);
   GetSerialAnswer(port_.c_str(), terminator, buf_string_);

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
}

void Controller::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=DEVICE_OK)
   {
      LogMessage("Purge issue...");
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
   }
}

int Controller::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}


void Controller::ReadUpdate()
{
   MMThreadGuard myLock(lock_);
   {
      //Any new response?
      //TODO This logs a 107 error if the read buffer is empty.
      //Any way round it?
      ReadResponseLines(-1);

      //TODO Use a time counter in the polling thread, no need to update those every cycle.

      //To update the global status
      /*
      Send("?");
      ReadResponseLines(n_lines1);

      //To update the temperatures
      Send("r");
      ReadResponseLines(n_lines2);
      */
   }
}

void Controller::ReadGreeting()
{
   //The number of lines returned by the niji may vary from firmware to firmware.
   //The first time we interrogate the niji, we let Readstaty
   //Then we know how many lines are returned by the niji in response to these commands
   MMThreadGuard myLock(lock_);
   {
      Purge();
      //Any greeting message when the niji is first turned on are parsed by ReadResponseLines()
      //ReadResponseLines(-1);

      //To update the global status (and store the number of lines returned by the command)
      Send("?");
      n_lines1 = ReadResponseLines(-1);

      //To update the temperatures (and store the number of lines returned by the command)
      Send("r");
      n_lines2 = ReadResponseLines(-1);
   }
}

//Here we analyze any possible response and return the number of lines actually read
int Controller::ReadResponseLines(int n)
{
   string prefix;
   int i = 0;

   while ( i<n || n<0)
   {
      i++;
      ReceiveOneLine();
      if (buf_string_.empty())
          break;

      // Output temperature
      // R,22.5, 0, 3, 0, 0, 0, 0, 1, 6, 94,
      prefix = "R,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         tempOutput_ = atof(buf_string_.substr(2).c_str());
         continue;
      }

      // Ambient temperature
      // R2,22.0, 0, 1,
      prefix = "R2,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         tempAmbient_= atof(buf_string_.substr(3).c_str());
         continue;
      }

      // Emission wavelengths?
      // M,593, 874, 515, 563, 561, 593, 516,
      prefix = "M,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         continue;
      }

      // This is to read the channel intensity
      // DAC (1-7),4,[2]  = 56
      prefix = "DAC (";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         continue;
      }

      // This is to read the channel state
      // DAC ON/OFF (1-7),4,[2]  = 1
      prefix = "DAC ON/OFF (";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         continue;
      }

      // The firmware version
      // Firmware,MWLS-DU-V1.17
      prefix = "Firmware,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         firmwareVersion_ = buf_string_.substr(prefix.size());
         continue;
      }

      // The response to a TTL setup
      // -> TTL,0,1,0
      // <- TTL,0,0,0,
      prefix = "TTL,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         continue;
      }

      // niji status
      // Status,0,
      prefix = "Status,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         long status = atoi(buf_string_.substr(prefix.size()).c_str());
         if (status != globalStatus_)
         {
             globalStatus_ = status;

             //This is used to kill the ligh in case we have a globalStatus error
             if (globalStatus_ > 0) {
                Illuminate();
                LogMessage("Keyswitch Off, Lid Open or Light Guide Missing - Lockout!");
             }
         }
         continue;
      }

      // OpAmp command?
      // -> 0
      // <- OpAmp OFF
      prefix = "OpAmp";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         continue;
      }

      //intensity response
      //d,4,80,
      prefix = "d,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         long index = atoi(buf_string_.substr(2).c_str())-1;  //need 0-index for arrays
         long intensity = atoi(buf_string_.substr(4).c_str());

         if (index >=0 && index < NLED) {
            channelIntensities_[index] = intensity;
         }

         continue;
      }

      //State response
      //D,3,0,
      prefix = "D,";
      if(buf_string_.substr(0, prefix.size()) == prefix) {
         long index = atoi(buf_string_.substr(2).c_str())-1;  //need 0-index for arrays
         long state = atoi(buf_string_.substr(4).c_str());

         if (index >=0 && index < NLED) {
            channelStates_[index] = state;
            UpdateChannelLabel();
         }
         continue;
      }

      //if we got here, that means buf_string_ could not be parsed...
      std::ostringstream ss;
      ss << "Not parsed: " << buf_string_;
      LogMessage(ss.str().c_str(), true);
   } 

   //CDeviceUtils::SleepMs(5);
   return i;
}


///////////////////////////////////////////////////////////////////////////////
// Shutter API
//

int Controller::SetOpen(bool open)
{
   SetState((long)open);
   Illuminate();

   return HandleErrors();
}

int Controller::GetOpen(bool& open)
{
   long state;
   GetState(state);

   open = (state == 1);
   return HandleErrors();
}

int Controller::Fire(double deltaT)
{
   deltaT=0; // Suppress warning
   error_ = DEVICE_UNSUPPORTED_COMMAND;
   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Polling thread implementation
// ~~~~~~~~~~~~~~~~~~~~

PollingThread::PollingThread(Controller& aController) :
   state_(0),
   aController_(aController)
{
}

PollingThread::~PollingThread()
{
   Stop();
   wait();
}

int PollingThread::svc() 
{
   state_ = aController_.state_;

   string propertyName;

   while (!stop_)
   {
      CDeviceUtils::SleepMs(500);

      if (aController_.hasUpdated_) {
         aController_.hasUpdated_ = false;
         ResetVariables();
         continue;
      }

      aController_.ReadUpdate();

      if (globalStatus_ != aController_.globalStatus_)
      {
         globalStatus_ = aController_.globalStatus_;
         aController_.OnPropertyChanged(g_Keyword_GlobalStatus, CDeviceUtils::ConvertToString(globalStatus_));
      }

      for (long index=0; index<NLED; index++) 
      {
         //individual intensities
         if (channelIntensities_[index] != aController_.channelIntensities_[index])
         {
            channelIntensities_[index] = aController_.channelIntensities_[index];
            CreatePropertyName(propertyName,g_Keyword_Prefix,"Intensity",index);
            aController_.OnPropertyChanged(propertyName.c_str(), CDeviceUtils::ConvertToString(channelIntensities_[index]));
         }

         //individual states
         if (channelStates_[index] != aController_.channelStates_[index])
         {
            channelStates_[index] = aController_.channelStates_[index];
            CreatePropertyName(propertyName,g_Keyword_Prefix,"State",index);
            aController_.OnPropertyChanged(propertyName.c_str(), CDeviceUtils::ConvertToString(channelStates_[index]));
         }
      }

      //State_ is also modified if one of the channel states is modified (state_ follows nactive>0)
      if (state_ != aController_.state_)
      {
         state_ = aController_.state_;
         aController_.OnPropertyChanged(MM::g_Keyword_State, CDeviceUtils::ConvertToString(state_));
      }


      //TODO Not probing for those at the moment
      if (tempOutput_ != aController_.tempOutput_)
      {
         tempOutput_ = aController_.tempOutput_;
         aController_.OnPropertyChanged(g_Keyword_Temp1, CDeviceUtils::ConvertToString(tempOutput_));
      }

      //TODO Not probing for those at the moment
      if (tempAmbient_ != aController_.tempAmbient_)
      {
         tempAmbient_ = aController_.tempAmbient_;
         aController_.OnPropertyChanged(g_Keyword_Temp2, CDeviceUtils::ConvertToString(tempAmbient_));
      }

      //currentChannelLabel is also updated when states are changed (MM or external keypad)
      if (currentChannelLabel_ != aController_.currentChannelLabel_)
      {
         currentChannelLabel_ = aController_.currentChannelLabel_;
         aController_.OnPropertyChanged(g_Keyword_ChannelLabel, currentChannelLabel_.c_str());
      }

   }

   return DEVICE_OK;
}

void PollingThread::ResetVariables()
{
   channelIntensities_ = aController_.channelIntensities_;
   channelStates_ = aController_.channelStates_;

   triggerSource_ = aController_.triggerSource_;
   triggerLogic_ = aController_.triggerLogic_;
   triggerResistor_ = aController_.triggerResistor_;

   globalStatus_ = aController_.globalStatus_;

   tempOutput_ = aController_.tempOutput_;
   tempAmbient_ = aController_.tempAmbient_;

   currentChannelLabel_ = aController_.currentChannelLabel_;
}

void PollingThread::Start()
{
   stop_ = false;
   activate();
}

