///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIFWheel.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI filter wheel adapter for Tiger
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
// BASED ON:      ASIStage.cpp and others
//


#include "ASITiger.h"
#include "ASIFWheel.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <iostream>
#include <vector>
#include <string>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CFWheel
//

// initialize static member
string CFWheel::selectedWheel_ = g_EmptyAxisLetterStr;

CFWheel::CFWheel(const char* name) :
   ASIPeripheralBase< ::CStateDeviceBase, CFWheel >(name),
   numPositions_(0),  // will read actual number of positions
   curPosition_(0),   // will read actual position
   spinning_(false),
   wheelNumber_(g_EmptyAxisLetterStr)  // 0..9 for filter wheels instead of A..Z
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      wheelNumber_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, wheelNumber_.c_str(), MM::String, true);
   }
}

int CFWheel::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize(true) );

   ostringstream command;

   // turn off prompt characters; this has the effect of always setting to a known verbose mode
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("VB 6", "VB 6", g_SerialTerminatorFW) );

   // activate the correct filterwheel (will receive all subsequent commands)
   // Tiger controller recognizes multiple cards installed and gives them increasing FW
   //   addresses (e.g. with 3 cards now there will be 0..5) => no need for using card address
   // need to use SelectWheel before any query/command to filterwheel
   RETURN_ON_MM_ERROR ( SelectWheelOverride() );

   // get the firmware version and expose that as property plus store it
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("VN","VN Version: v", g_SerialTerminatorFW) );
   RETURN_ON_MM_ERROR (hub_->ParseAnswerAfterPosition(13, firmwareVersion_ ));
   command.str("");
   command << firmwareVersion_;
   CreateProperty(g_FirmwareVersionPropertyName, command.str().c_str(), MM::Float, true);

   // make sure it isn't spinning
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("SF0", "SF0", g_SerialTerminatorFW) );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_FWheelDeviceDescription << " WheelNum=" << wheelNumber_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // serial query to find out how many positions we have
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("NF", "NF ", g_SerialTerminatorFW) );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(numPositions_) );
   command.str("");
   command << numPositions_;
   CreateProperty("NumPositions", command.str().c_str(), MM::Integer, true);

   // add allowed values to the special state/position property for state devices
   CPropertyAction* pAct = new CPropertyAction (this, &CFWheel::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   for (unsigned int i=0; i<numPositions_; i++)
   {
      command.str("");
      command << i;
      AddAllowedValue(MM::g_Keyword_State, command.str().c_str());
   }

   // add default labels for the states
   pAct = new CPropertyAction (this, &CFWheel::OnLabel);
   CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   for (unsigned int i=0; i<numPositions_; i++)
   {
      command.str("");
      command << "Position-" << i+1;
      SetPositionLabel(i, command.str().c_str());
   }

   // refresh properties from controller every time - default is not to refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CFWheel::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CFWheel::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // get current position and cache in curPosition_
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("MP", "MP ", g_SerialTerminatorFW) );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(curPosition_) );

   // property for spinning or not
   pAct = new CPropertyAction (this, &CFWheel::OnSpin);
   CreateProperty(g_FWSpinStatePropertyName, g_OffState, MM::String, false, pAct);
   AddAllowedValue(g_FWSpinStatePropertyName, g_OffState);
   AddAllowedValue(g_FWSpinStatePropertyName, g_OnState);
   UpdateProperty(g_FWSpinStatePropertyName);

   // max velocity
   pAct = new CPropertyAction (this, &CFWheel::OnVelocity);
   CreateProperty(g_FWVelocityRunPropertyName, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_FWVelocityRunPropertyName, 0, 12500);
   UpdateProperty(g_FWVelocityRunPropertyName);

   // preset speed settings from 0 to 9
   pAct = new CPropertyAction (this, &CFWheel::OnSpeedSetting);
   CreateProperty(g_FWSpeedSettingPropertyName, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_FWSpeedSettingPropertyName, 0, 9);
   UpdateProperty(g_FWSpeedSettingPropertyName);

   // lock mode
   pAct = new CPropertyAction (this, &CFWheel::OnLockMode);
   CreateProperty(g_FWLockModePropertyName, g_OffState, MM::String, false, pAct);
   AddAllowedValue(g_FWLockModePropertyName, g_OffState);
   AddAllowedValue(g_FWLockModePropertyName, g_OnState);
   UpdateProperty(g_FWLockModePropertyName);

   initialized_ = true;
   return DEVICE_OK;
}

bool CFWheel::Busy()
{
   // say that spinning is "not busy"
   // otherwise we get a timeout when waiting for device
   // also if user put into spin mode then we are done with that action
   // and awaiting further instructions
   if (spinning_)
      return false;

   // this actually will return status of the two wheels on the same card
   // this is a firmware limitation
   if (SelectWheel() != DEVICE_OK)  // say we aren't busy if we can't communicate
      return false;

   // note special case: FW busy query won't reply with line terminator
   // send query command and grab first character that comes (up to 200ms before timing out)
   // anything other than 0 is still moving (other codes are apparently 12 and 16)
   // errors are reported as "not busy"
   if (hub_->QueryCommandUnterminatedResponse("?", 200) != DEVICE_OK)  // say we aren't busy if we can't communicate
   {
      LogMessage("ERROR: filterwheel didn't respond with status");
      return false;
   }
   if (hub_->LastSerialAnswer().length() < 1)
   {
      LogMessage("ERROR: failed to correctly read status of filterwheel");
      return false;
   }
   long code = atol(hub_->LastSerialAnswer().c_str());
   return (code != 0);
}

int CFWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPosition_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (spinning_)
         return ERR_FILTER_WHEEL_SPINNING;
      long pos;
      pProp->Get(pos);
      RETURN_ON_MM_ERROR ( SelectWheel() );
      command << "MP" << pos;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), "MP", g_SerialTerminatorFW) );
      curPosition_ = pos;
   }
   return DEVICE_OK;
}

int CFWheel::OnLabel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      char buf[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetPosition(buf) );
      pProp->Set(buf);
   }
   else if (eAct == MM::AfterSet)
   {
      if (spinning_)
         return ERR_FILTER_WHEEL_SPINNING;
      string buf;
      pProp->Get(buf);
      RETURN_ON_MM_ERROR ( SetPosition(buf.c_str()) );
   }
   return DEVICE_OK;
}

int CFWheel::SelectWheelOverride()
{
   ostringstream command; command.str("");
   command << "FW" << wheelNumber_;
   // if we sent an invalid address then Tiger responds with a <NAK>-terminated reply
   //   which leads to a timeout.  note this is different in Tiger than in stand-alone filterwheel
   // note that we cannot check the response filterwheel because, for example, we have 2 Tiger cards
   // then setting to wheel #3 will return FW1 because it's the 1st wheel on the card and the
   // card doesn't know any better
   if (hub_->QueryCommandVerify(command.str(),"FW", g_SerialTerminatorFW) != DEVICE_OK)
   {
      selectedWheel_ = g_EmptyAxisLetterStr;
      return ERR_FILTER_WHEEL_NOT_READY;
   }
   selectedWheel_ = wheelNumber_;
   return DEVICE_OK;
}

int CFWheel::SelectWheel()
{
   if (selectedWheel_ != wheelNumber_)
      return SelectWheelOverride();
   else
      return DEVICE_OK;
}

void CFWheel::ForcePropertyRefresh()
{
   if (!refreshProps_)
   {
      refreshProps_ = true;
      OnPropertiesChanged();
      refreshProps_ = false;
   }
}

////////////////
// action handlers

int CFWheel::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      RETURN_ON_MM_ERROR ( SelectWheel() );
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SaveSettingsOrig) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsDone) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsX) == 0)
         command << "RD";
      else if (tmpstr.compare(g_SaveSettingsY) == 0)
         command << "RR";
      else if (tmpstr.compare(g_SaveSettingsZ) == 0)
         command << "RW";
      // these commands elicit an echo response but not one with termination
      RETURN_ON_MM_ERROR ( hub_->QueryCommandUnterminatedResponse(command.str(), 400) );
      if (hub_->LastSerialAnswer().substr(0, 2).compare(command.str().substr(0,2)) != 0)
      {
         return ERR_UNRECOGNIZED_ANSWER;
      }
      pProp->Set(g_SaveSettingsDone);
      // refresh properties if we just restored them
      if (tmpstr.compare(g_SaveSettingsX) || tmpstr.compare(g_SaveSettingsY)) {
         ForcePropertyRefresh();
      }
   }
   return DEVICE_OK;
}

int CFWheel::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         refreshProps_ = true;
      else
         refreshProps_ = false;
   }
   return DEVICE_OK;
}

int CFWheel::OnSpin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand("SF ", g_SerialTerminatorFW) );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(tmp) );
      bool success = 0;
      if (tmp)
      {
         success = pProp->Set(g_OnState);
         spinning_ = true;
      }
      else
      {
         success = pProp->Set(g_OffState);
         spinning_ = false;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      string str;
      pProp->Get(str);
      RETURN_ON_MM_ERROR ( SelectWheel() );
      if (str.compare(g_OnState) == 0)
      {
         command << "SF1";
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), "SF1", g_SerialTerminatorFW) );
         spinning_ = true;
      }
      else
      {
         command << "SF0";
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), "SF0", g_SerialTerminatorFW) );
         spinning_ = false;
         command.str("");
         command << "HO";  // make it stop at home
         // HO command echoed but without termination
         RETURN_ON_MM_ERROR ( hub_->QueryCommandUnterminatedResponse(command.str(), 400) );
         if (hub_->LastSerialAnswer().substr(0, 2).compare(command.str().substr(0,2)) != 0)
         {
            return ERR_UNRECOGNIZED_ANSWER;
         }
      }
      curPosition_ = 0;  // set position to be home during spin
      SetPosition("0");  //  (do at both start and stop but doesn't matter)
   }
   return DEVICE_OK;
}

int CFWheel::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand("VR", g_SerialTerminatorFW) );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      if (spinning_)
      {
         RETURN_ON_MM_ERROR ( OnVelocity(pProp, MM::BeforeGet) );
         return ERR_FILTER_WHEEL_SPINNING;
      }
      pProp->Get(tmp);
      command << "VR " << tmp;
      ostringstream response; response.str("");
      response << tmp << "VR";  // echoed in reverse order
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), response.str(), g_SerialTerminatorFW) );
   }
   return DEVICE_OK;
}

int CFWheel::OnSpeedSetting(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand("SV", g_SerialTerminatorFW) );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      if (spinning_)
      {
         RETURN_ON_MM_ERROR ( OnSpeedSetting(pProp, MM::BeforeGet) );
         return ERR_FILTER_WHEEL_SPINNING;
      }
      pProp->Get(tmp);
      command << "SV " << tmp;
      ostringstream response; response.str("");
      response << tmp << "SV";  // echoed in reverse order
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), response.str(), g_SerialTerminatorFW) );
      // this command changes velocity and possibly other settings; refresh all properties
      ForcePropertyRefresh();
   }
   return DEVICE_OK;
}

int CFWheel::OnLockMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand("LM ", g_SerialTerminatorFW) );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition3(tmp) );
      bool success = 0;
      if (tmp)
         success = pProp->Set(g_OnState);
      else
         success = pProp->Set(g_OffState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      if (spinning_)
      {
         RETURN_ON_MM_ERROR ( OnLockMode(pProp, MM::BeforeGet) );
         return ERR_FILTER_WHEEL_SPINNING;
      }
      string str;
      pProp->Get(str);
      if (str.compare(g_OnState) == 0)
         command << "LM1";
      else
         command << "LM0";
      RETURN_ON_MM_ERROR ( SelectWheel() );
      // command itself is echoed
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), command.str(), g_SerialTerminatorFW) );
   }
   return DEVICE_OK;
}
