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

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

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
// CSlider
//
CFWheel::CFWheel(const char* name) :
   ASIPeripheralBase< ::CStateDeviceBase, CFWheel >(name),
   numPositions_(0),  // will read actual number of positions
   curPosition_(0),   // will read actual position
   spinning_(0),
   axisLetter_(g_EmptyAxisLetterStr)  // 0..9 for filter wheels instead of A..Z
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetter_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
   }
}

int CFWheel::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize(true) );

   ostringstream command;

   // turn off prompts
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify("VB 6", "VB 6", g_SerialTerminatorFW) );

   // activate the correct filterwheel (will receive all subsequent commands)
   // Tiger controller recognizes multiple cards installed and gives them increasing FW
   //   addresses (e.g. with 3 cards now there will be 0..5) => no need for using card address
   // need to use SelectWheel before any query/command to filterwheel
   // if making multiple queries/commands in a row then once is enough
   RETURN_ON_MM_ERROR ( SelectWheel() );

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
   command << g_FWheelDeviceDescription << " WheelNum=" << axisLetter_ << " HexAddr=" << addressString_;
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
   UpdateProperty(g_FWSpinStatePropertyName);
   AddAllowedValue(g_FWSpinStatePropertyName, g_OffState);
   AddAllowedValue(g_FWSpinStatePropertyName, g_OnState);

   // max velocity
   pAct = new CPropertyAction (this, &CFWheel::OnVelocity);
   CreateProperty(g_FWVelocityRunPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_FWVelocityRunPropertyName);
   SetPropertyLimits(g_FWVelocityRunPropertyName, 0, 12500);

   // preset speed settings from 0 to 9
   pAct = new CPropertyAction (this, &CFWheel::OnSpeedSetting);
   CreateProperty(g_FWSpeedSettingPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_FWSpeedSettingPropertyName);
   SetPropertyLimits(g_FWSpeedSettingPropertyName, 0, 9);

   // lock mode
   pAct = new CPropertyAction (this, &CFWheel::OnLockMode);
   CreateProperty(g_FWLockModePropertyName, g_OffState, MM::String, false, pAct);
   UpdateProperty(g_FWLockModePropertyName);
   AddAllowedValue(g_FWLockModePropertyName, g_OffState);
   AddAllowedValue(g_FWLockModePropertyName, g_OnState);

   initialized_ = true;
   return DEVICE_OK;
}

bool CFWheel::Busy()
{
   // this actually will return status of the two wheels on the same card
   // this is a firmware limitation
   if (SelectWheel() != DEVICE_OK)  // say we aren't busy if we can't communicate
      return false;
   if (hub_->QueryCommand("?", g_SerialTerminatorFW) != DEVICE_OK)  // say we aren't busy if we can't communicate
      return false;
   unsigned int i;
   if (hub_->ParseAnswerAfterPosition(0, i) != DEVICE_OK)  // say we aren't busy if we can't parse
      return false;
   // ASI documentation seems to be out of date here, but we'll take codes 1-3 or 12 as moving
   return (i==12 || (i>=1 && i<=3));
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
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW) );
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

int CFWheel::SelectWheel()
{
   ostringstream command; command.str("");
   command << "FW" << axisLetter_;
   // if we sent an invalid address then Tiger responds with a <NAK>-terminated reply
   //   which leads to a timeout.  note this is different in Tiger than in stand-alone filterwheel
   if (hub_->QueryCommandVerify(command.str(),"FW", g_SerialTerminatorFW) != DEVICE_OK)
      return ERR_FILTER_WHEEL_NOT_READY;
   return DEVICE_OK;
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
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW, (long)200) );  // note added 200ms delay
      pProp->Set(g_SaveSettingsDone);
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
         spinning_ = tmp;
      }
      else
      {
         success = pProp->Set(g_OffState);
         spinning_ = 0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      string str;
      pProp->Get(str);
      if (str.compare(g_OnState) == 0)
         command << "SF1";
      else
         command << "SF0\rHO";  // make it stop at home
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW) );
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
      pProp->Get(tmp);
      command << "VR " << tmp;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW) );
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
      pProp->Get(tmp);
      command << "SV " << tmp;
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW) );
      OnPropertiesChanged(); // get all other properties again since this changes velocity
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
      string str;
      pProp->Get(str);
      if (str.compare(g_OnState) == 0)
         command << "LM1";
      else
         command << "LM0";
      RETURN_ON_MM_ERROR ( SelectWheel() );
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str(), g_SerialTerminatorFW) );
   }
   return DEVICE_OK;
}
