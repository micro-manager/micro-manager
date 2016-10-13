///////////////////////////////////////////////////////////////////////////////
// FILE:          PrecisExcite.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PrecisExcite controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
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
// CVS:           
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "../../MMDevice/MMDevice.h"
#include "PrecisExcite.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>

// Controller
const char* g_ControllerName = "PrecisExcite";
const char* g_Keyword_Intensity = "Intensity";
const char* g_Keyword_Trigger = "Trigger";
const char* g_Keyword_Trigger_Sequence = "TriggerSequence";
const char * carriage_return = "\r";
const char * line_feed = "\n";



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::ShutterDevice, "PrecisExcite LED illuminator");
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
// ~~~~~~~~~~~~~~~~~~~~

Controller::Controller(const char* name) :
   initialized_(false), 
   intensity_(0),
   state_(0),
   name_(name), 
   busy_(false),
   error_(0),
   changedTime_(0.0)
{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PrecisExcite LED Illuminator", MM::String, true);

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

   // set property list
   // -----------------
   /*
   MM::Device* serialDevice = GetCoreCallback()->GetDevice(this, port_.c_str());
   if (serialDevice == NULL) {
      LogMessage("Serial port not found");
      return DEVICE_SERIAL_COMMAND_FAILED;
   }
*/
   this->LogMessage("Controller::Initialize()");

   currentChannel_ = 0;

   ReadGreeting();
   int result = ReadChannelLabels();
   if (result != DEVICE_OK)
	   return result;

   GenerateChannelChooser();
   GeneratePropertyIntensity();
   GeneratePropertyState();
   GeneratePropertyTrigger();
   GeneratePropertyTriggerSequence();
   
   initialized_ = true;
   return HandleErrors();

}

void Controller::ReadGreeting()
{
   do {
      ReceiveOneLine();
   } while (! buf_string_.empty());
}

int Controller::ReadChannelLabels()
{
   buf_tokens_.clear();
   string label;

   Purge();
   Send("LAMS");
   do {
      ReceiveOneLine();
      buf_tokens_.push_back(buf_string_);
   }
      while(! buf_string_.empty());
   
   for (unsigned int i=0;i<buf_tokens_.size();i++)
   {
      if (buf_tokens_[i].substr(0,3).compare("LAM")==0) {
         string label = buf_tokens_[i].substr(6);
         StripString(label);

         //This skips invalid channels
         if (label.compare("----") == 0)
            continue;

         channelLetters_.push_back(buf_tokens_[i][4]); // Read 4th character
         // This is a temporary log entry to debug an issue with channel labels
         // that appear to contain an invalid character at the end.
         std::ostringstream ss;
         ss << "debug: last char of stripped label is: \'" <<
            static_cast<int>(label[label.size()]) << "\' (as decimal int)";
         LogMessage(ss.str().c_str(), true);

         channelLabels_.push_back(label);
      }
   }

   if (channelLabels_.size() == 0)
	   return DEVICE_ERR;
   else
	   return DEVICE_OK;
}


/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////

void Controller::GeneratePropertyState()
{
   CPropertyAction* pAct = new CPropertyAction (this, &Controller::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");
}

void Controller::GeneratePropertyTrigger()
{
   CPropertyAction* pAct = new CPropertyAction (this, &Controller::OnTrigger);
   CreateProperty("Trigger", "Off", MM::String, false, pAct);
   for (TriggerType i=OFF;i<=FOLLOW_PULSE;i=TriggerType(i+1))
      AddAllowedValue("Trigger", TriggerLabels[i].c_str());
   SetProperty("Trigger","Off");
}

void Controller::GenerateChannelChooser()
{
   if (! channelLabels_.empty()) {   
      CPropertyAction* pAct;
      pAct = new CPropertyAction (this, &Controller::OnChannelLabel);
      CreateProperty("ChannelLabel", channelLabels_[0].c_str(), MM::String, false, pAct);

      SetAllowedValues("ChannelLabel",channelLabels_);
      SetProperty("ChannelLabel",channelLabels_[0].c_str());
            
   }
}

void Controller::GeneratePropertyIntensity()
{
   string intensityName;
   CPropertyActionEx* pAct; 
   for (unsigned i=0;i<channelLetters_.size();i++)
   {
      pAct = new CPropertyActionEx(this, &Controller::OnIntensity, i);
      intensityName = g_Keyword_Intensity;
      intensityName.push_back(channelLetters_[i]);
      CreateProperty(intensityName.c_str(), "0", MM::Integer, false, pAct);
      SetPropertyLimits(intensityName.c_str(), 0, 100);
   }
}



int Controller::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return HandleErrors();
}



void Controller::GeneratePropertyTriggerSequence()
{
   int ret;
   CPropertyAction* pAct = new CPropertyAction (this, &Controller::OnTriggerSequence);
   ret = CreateProperty(g_Keyword_Trigger_Sequence, "ABCD0", MM::String, false, pAct);
   SetProperty(g_Keyword_Trigger_Sequence, "ABCD0");
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

int Controller::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{

   long intensity;
   if (eAct == MM::BeforeGet)
   {
      GetIntensity(intensity,index);
      pProp->Set(intensity);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(intensity);
      SetIntensity(intensity, index);
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
      GetState(state_);
      pProp->Get(currentChannelLabel_);
      for (unsigned int i=0;i<channelLabels_.size();i++)
         if (channelLabels_[i].compare(currentChannelLabel_) == 0)
         {
            currentChannel_ = i;
            SetState(state_);
         }

   }

   return HandleErrors();
}



int Controller::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      GetState(state_);
      pProp->Set(state_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(state_);
      SetState(state_);
   }
   
   return HandleErrors();
}


int Controller::OnTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(trigger_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      char cmd=0;
      pProp->Get(trigger_);
      for (int i=0;i<5;i++)
      {
         if (trigger_.compare(TriggerLabels[i])==0)
         {
            cmd = TriggerCmd[i];
            triggerMode_ = (TriggerType) i;
         }
      }
      
      SetTrigger();

   }
   return HandleErrors();
}


int Controller::OnTriggerSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerSequence_.c_str());
   }
   else if (eAct == MM::AfterSet)
   { 
      pProp->Get(triggerSequence_);
      SetTrigger();
   }
   return HandleErrors();
}


///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

void Controller::SetTrigger()
{
   stringstream msg;
   msg << "SQX" << carriage_return;

   for (unsigned int i=0;i<triggerSequence_.size();i++)
   {
      msg << "SQ" << triggerSequence_[i] << carriage_return;
   }

   triggerMessage_ = msg.str();

   Illuminate();

}

void Controller::Illuminate()
{
   stringstream msg;
   if (state_==0)
   {
      if (triggerMode_ == OFF || triggerMode_ == FOLLOW_PULSE)
         msg << "SQX" << carriage_return << "C" << channelLetters_[currentChannel_] << "F" << carriage_return << "AZ";
      else
         msg << "SQX" << "AZ";
   }
   else if (state_==1)
   {
      if (triggerMode_ == OFF) {
         msg << "SQZ" << carriage_return;
         for (unsigned int i=0; i<channelLetters_.size(); i++) {
            msg << "C" << channelLetters_[i];
            if (i == currentChannel_)
               msg << "N";
            else
               msg << "F";
            msg << carriage_return;
         }
      }
      else if (triggerMode_ == FOLLOW_PULSE)
         msg << "SQZ" << carriage_return << "A" << channelLetters_[currentChannel_] << "#";
      else
         msg << triggerMessage_ << "SQ" << TriggerCmd[triggerMode_];
   }
            
   Send(msg.str());
}

void Controller::SetIntensity(long intensity, long index)
{
   stringstream msg;
   msg << "C" << channelLetters_[index] << "I" << intensity;
   Purge();
   Send(msg.str());
   ReceiveOneLine();

}

void Controller::GetIntensity(long& intensity, long index)
{
   stringstream msg;
   string ans;
   msg << "C" << channelLetters_[index] << "?";
   Purge();
   Send(msg.str());
   ReceiveOneLine();

   if (! buf_string_.empty())
      if (0 == buf_string_.compare(0,2,msg.str(),0,2))
      {
         intensity = atol(buf_string_.substr(2,3).c_str());
      }

}

void Controller::SetState(long state)
{
   state_ = state;
   stringstream msg;
   Illuminate();

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();
}

void Controller::GetState(long &state)
{
   if (triggerMode_ == OFF) {
      Purge();
      Send("C?");
      long stateTmp = 0;

      for (unsigned int i=0;i<channelLetters_.size();i++)
      {
         ReceiveOneLine();

         if (! buf_string_.empty())
            if (buf_string_[5]=='N')
               stateTmp = 1;       
      }
      state = stateTmp;
   }
   else
      state = state_;

}

int Controller::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}



/////////////////////////////////////
//  Communications
/////////////////////////////////////


void Controller::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), carriage_return);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}


void Controller::ReceiveOneLine()
{
   buf_string_ = "";
   GetSerialAnswer(port_.c_str(), line_feed, buf_string_);

}

void Controller::Purge()
{
   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

//********************
// Shutter API
//********************

int Controller::SetOpen(bool open)
{
   SetState((long) open);
   return HandleErrors();
}

int Controller::GetOpen(bool& open)
{
   long state;
   GetState(state);
   if (state==1)
      open = true;
   else if (state==0)
      open = false;
   else
      error_ = DEVICE_UNKNOWN_POSITION;

   return HandleErrors();
}

int Controller::Fire(double deltaT)
{
   deltaT=0; // Suppress warning
   error_ = DEVICE_UNSUPPORTED_COMMAND;
   return HandleErrors();
}
