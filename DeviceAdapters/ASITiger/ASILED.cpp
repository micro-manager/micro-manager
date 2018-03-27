///////////////////////////////////////////////////////////////////////////////
// FILE:          ASILED.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI LED shutter device adapter
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 05/2014
//
// BASED ON:      ASIStage.cpp and others
//


#include "ASILED.h"
#include "ASITiger.h"
#include "ASIHub.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"
#include <iostream>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CLED
//
CLED::CLED(const char* name) :
   ASIPeripheralBase< ::CShutterBase, CLED >(name),
   open_(false),
   intensity_(50),
   channel_(0),  // 0 for LED on 2-axis card
   channelAxisChar_('X')
{
   //Figure out what channel we are on
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      channel_= GetChannelFromExtName(name);
   }

   //Pick AxisChar to use.
   switch(channel_)
   {
   case 2:
      channelAxisChar_='Y';
      break;
   case 3:
      channelAxisChar_='Z';
      break;
   case 4:
      channelAxisChar_='F';
      break;
   case 5:
      channelAxisChar_='T';
      break;
   case 6:
      channelAxisChar_='R';
      break;
   case 1:
   case 0:  // use 'X' for supplemental LED on two-axis card (e.g. XY card)
   default:
      channelAxisChar_='X';
      break;
   }
}

int CLED::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   ostringstream command;
   command.str("");
   command << g_LEDDeviceDescription << " HexAddr=" << addressString_;
   if (channel_ > 0)
   {
      command << " Channel=" << channel_ << ":" << channelAxisChar_;
   }
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   CPropertyAction* pAct;

   pAct = new CPropertyAction (this, &CLED::OnIntensity);
   CreateProperty(g_LEDIntensityPropertyName, "50", MM::Integer, false, pAct);
   SetPropertyLimits(g_LEDIntensityPropertyName, 1, 100);
   UpdateProperty(g_LEDIntensityPropertyName);  // this takes care of initializing open_ and intensity_

   // always start shutter in closed state
   SetOpen(false);

   pAct = new CPropertyAction (this, &CLED::OnState);
   CreateProperty(g_ShutterState, g_OpenState, MM::String, false, pAct);
   AddAllowedValue(g_ShutterState, g_OpenState);
   AddAllowedValue(g_ShutterState, g_ClosedState);
   UpdateProperty(g_ShutterState);

   // refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CLED::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CLED::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // LED current limit, card wide setting
   // once mechanism for shared settings devices gets implemented use for this one
   if (channel_ > 0)
   {
      pAct = new CPropertyAction (this, &CLED::OnCurrentLimit);
      CreateProperty(g_LEDCurrentLimitPropertyName, "700", MM::Integer, false, pAct);
      SetPropertyLimits(g_LEDCurrentLimitPropertyName , 0, 1000);
      UpdateProperty(g_LEDCurrentLimitPropertyName );
   }

   initialized_ = true;
   return DEVICE_OK;
}


int CLED::SetOpen(bool open)
{
   ostringstream command; command.str("");
   if (open)
      command << addressChar_ << "LED " << channelAxisChar_ << "="<< intensity_;
   else
      command << addressChar_ << "LED " << channelAxisChar_ << "=0";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   open_ = open;
   return DEVICE_OK;
}

int CLED::GetOpen(bool& open)
// returns the cached value instead of querying controller itself
{
   open = open_;
   return DEVICE_OK;
}

int CLED::UpdateOpenIntensity()
// updates open_ and intensity_ via the controller
// controller says intensity is 0 if LED is turned off =>
//   we don't update intensity_ if controller reports 0, only set open_ to false
{
   ostringstream command; command.str("");
   ostringstream replyprefix; replyprefix.str("");
   long tmp = 0;
   command << addressChar_ << "LED " << channelAxisChar_ << "?";
   replyprefix << channelAxisChar_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), replyprefix.str()) );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   open_ = tmp > 0;
   if (open_)
      intensity_ = tmp;
   return DEVICE_OK;
}



////////////////
// action handlers

int CLED::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      if (hub_->UpdatingSharedProperties())
         return DEVICE_OK;
      command << addressChar_ << "SS ";
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SaveSettingsOrig) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsDone) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsX) == 0)
         command << 'X';
      else if (tmpstr.compare(g_SaveSettingsY) == 0)
         command << 'Y';
      else if (tmpstr.compare(g_SaveSettingsZ) == 0)
         command << 'Z';
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A", (long)200) );  // note 200ms delay added
      pProp->Set(g_SaveSettingsDone);
      command.str(""); command << g_SaveSettingsDone;
      RETURN_ON_MM_ERROR ( hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()) );
   }
   return DEVICE_OK;
}

int CLED::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CLED::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");

   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      UpdateOpenIntensity();  // will set intensity_ unless LED is turned off
      if (!pProp->Set((long)intensity_))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      if(open_)  // if we are closed then don't actually want to set the controller, only the internal
      {
         command << addressChar_ << "LED " << channelAxisChar_ << "=" << tmp;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
      }
      intensity_ = tmp;


   }
   return DEVICE_OK;
}

int CLED::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if(open_)
         pProp->Set(g_OpenState);
      else
         pProp->Set(g_ClosedState);
   }
   else if (eAct == MM::AfterSet)
   {
      string tmpstr;
      pProp->Get(tmpstr);
      RETURN_ON_MM_ERROR( SetOpen(tmpstr.compare(g_OpenState) == 0) );
   }

   return DEVICE_OK;
}


int CLED::OnCurrentLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   //sets the LED current limit, which can be used to control brightness but is a card-wide setting
   ostringstream command; command.str("");

   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "WRDAC X?"; //same syntax for all channels
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "X=") );
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp *= 12;   // controller units are percent of 1.2A, convert to milliamps
      if (!pProp->Set((long)tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      if (hub_->UpdatingSharedProperties())
         return DEVICE_OK;
      pProp->Get(tmp);
      tmp /= 12;  // convert from milliamps into percent of 1.2A
      command << addressChar_ << "WRDAC X="<< tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
      command.str(""); command << tmp;
      RETURN_ON_MM_ERROR ( hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()) );
   }

   return DEVICE_OK;
}

