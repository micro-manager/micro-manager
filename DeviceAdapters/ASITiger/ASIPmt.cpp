///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIPmt.c
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI PMT device adapter
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
// AUTHOR:        Vikram Kopuri (vik@asiimaging.com) 04/2016
//
// BASED ON:      ASILED.c and others
//


#include "ASIPmt.h"
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
// CPMT
//
CPMT::CPMT(const char* name) :
   ASIPeripheralBase< ::CSignalIOBase, CPMT >(name),
   channel_(1),
   channelAxisChar_('X'), 
   axisLetter_(g_EmptyAxisLetterStr)
{
   //Figure out what channel we are on
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      channel_= GetChannelFromExtName(name);
	   axisLetter_ = GetAxisLetterFromExtName(name);
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
   default:
      channelAxisChar_='X';
      break;
   }
}

int CPMT::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   ostringstream command;
   command.str("");
   command << g_PMTDeviceDescription << " HexAddr=" << addressString_<<" Axis Char="<<axisLetter_<<" Channel="<<channel_<<":"<<channelAxisChar_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);
   
   CPropertyAction* pAct;

   //PMT Gain
   pAct = new CPropertyAction (this, &CPMT::OnGain);
   CreateProperty(g_PMTGainPropertyName, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_PMTGainPropertyName, 0, 1000);
   UpdateProperty(g_PMTGainPropertyName);  

   //ADC Avg
   pAct = new CPropertyAction (this, &CPMT::OnAverage);
   CreateProperty(g_PMTAVGPropertyName, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_PMTAVGPropertyName, 0, 5);
   UpdateProperty(g_PMTAVGPropertyName);  

   // refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CPMT::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CPMT::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   //Overload Reset
   pAct = new CPropertyAction (this, &CPMT::OnOverloadReset);
   CreateProperty(g_PMTOverloadReset,  g_OffState, MM::String, false, pAct);
   AddAllowedValue(g_PMTOverloadReset, g_OffState);
   AddAllowedValue(g_PMTOverloadReset, g_OnState);
   AddAllowedValue(g_PMTOverloadReset, g_PMTOverloadDone);

   //PMT signal, ADC readout
   pAct = new CPropertyAction (this, &CPMT::OnPMTSignal);
   CreateProperty(g_PMTSignal, "0", MM::Integer, true, pAct);
   UpdateProperty(g_PMTSignal);

   //PMT Overload
   pAct = new CPropertyAction (this, &CPMT::OnPMTOverload);
   CreateProperty(g_PMTOverload, g_NoState, MM::String, true, pAct);
   AddAllowedValue(g_PMTOverload, g_NoState);
   AddAllowedValue(g_PMTOverload, g_YesState);
   UpdateProperty(g_PMTOverload);

   initialized_ = true;
   return DEVICE_OK;
}

// This is the overload reset 
int CPMT::SetGateOpen(bool open)
{
	ostringstream command; command.str("");
   if(open)
   {
      command << addressChar_ << "LOCK " << channelAxisChar_ ;
   }
   else
   {
     // can't do opposite the reset
   }
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   return DEVICE_OK;
}

// Get overload status
int CPMT::GetGateOpen(bool& open)
{
   unsigned int val;
	// This is the overload reset 
	ostringstream command; command.str("");
    command << addressChar_ << "LOCK " << channelAxisChar_ << "?" ;
  // reply is 0 or 1 , 0 is overloaded , 1 is enabled
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
   if(val)
   {
   open = true;
  
   }
   else
   {
   open=false;

   }
	return DEVICE_OK;
}

// Get PMT's ADC reading
int CPMT::GetSignal(double& volts)
{
   unsigned int val;
	// This is the overload reset 
	ostringstream command; command.str("");
    command << addressChar_ << "RDADC " << channelAxisChar_ << "?" ;
   // reply is 0 or 1 , 0 is overloaded , 1 is enabled
    RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
    RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
   
    volts=val;

	return DEVICE_OK;
}

int CPMT::UpdateGain()
// updates PMT gain device property via the controller
{
   ostringstream command; command.str("");
   ostringstream replyprefix; replyprefix.str("");
   long tmp = 0;
   command << addressChar_ << "WRDAC " << channelAxisChar_ << "?";
   replyprefix << channelAxisChar_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), replyprefix.str()) );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   gain_ = tmp;

   return DEVICE_OK;
}

int CPMT::UpdateAvg()
// updates PMT average length property via the controller
{
   ostringstream command; command.str("");
   ostringstream replyprefix; replyprefix.str("");
   long tmp = 0;
  // command << addressChar_ << "RT F?";
   command << "E "<<axisLetter_<<"?";
   replyprefix << ":" << axisLetter_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), replyprefix.str()) );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   avg_length_ = tmp;

   return DEVICE_OK;
}

////////////////
// action handlers

int CPMT::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
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
   }
   return DEVICE_OK;
}

int CPMT::OnOverloadReset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_OffState) == 0)
         return DEVICE_OK;
      else if (tmpstr.compare(g_PMTOverloadDone) == 0)
         return DEVICE_OK;
	  else if (tmpstr.compare(g_OnState) == 0)
         command << addressChar_ << "LOCK " << channelAxisChar_ ;

      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A", (long)200) );  // note 200ms delay added
      pProp->Set(g_PMTOverloadDone);
   }
   return DEVICE_OK;
}


int CPMT::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

//Get and Set PMT Gain
int CPMT::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   { //Query the controller for gain
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      UpdateGain();  // will set gain_ 
      if (!pProp->Set((long)gain_))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
         command << addressChar_ << "WRDAC " << channelAxisChar_ << "=" << tmp;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
      gain_ = tmp;
   }
   return DEVICE_OK;
}

//Get and Set PMT Average length
//Note this is a common property for both the channels
int CPMT::OnAverage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   { //Query the controller for gain
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      UpdateAvg();  // will set avg_length_ 
      if (!pProp->Set((long)avg_length_ ))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
         //command << addressChar_ << "RT F=" << tmp;
      command << "E "<<axisLetter_<<"="<<tmp;   
	  RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
         avg_length_ = tmp;

   }
   return DEVICE_OK;
}

int CPMT::OnPMTSignal(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   unsigned int val;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      command << addressChar_ << "RDADC " << channelAxisChar_ << "?" ;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
      if (!pProp->Set((long)val))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CPMT::OnPMTOverload(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   unsigned int val;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      command << addressChar_ << "LOCK " << channelAxisChar_ << "?" ;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
      if(val)
	  {
	  if (!pProp->Set(g_NoState))
         return DEVICE_INVALID_PROPERTY_VALUE;
	  }
	  else
	  {
	  	  if (!pProp->Set(g_YesState))
         return DEVICE_INVALID_PROPERTY_VALUE;
	  }
   }
   return DEVICE_OK;
}
