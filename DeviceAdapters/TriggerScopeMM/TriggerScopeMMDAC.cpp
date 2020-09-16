///////////////////////////////////////////////////////////////////////////////
// FILE:          TriggerScopeMM.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the ARC TriggerScope device adapter.
//				  See http://www.trggerscope.com
//                
// AUTHOR:        Austin Blanco, 21 July 2015
//                Nico Stuurman, 3 Sept 2020                  
//
// COPYRIGHT:     Advanced Research Consulting. (2014-2015)
//                Regents of the University of California (2020)
//
// LICENSE:       This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "TriggerScopeMM.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <algorithm>
#include <iostream>


using namespace std;



/**** CTriggerScopeMMDAC ****/


CTriggerScopeMMDAC::CTriggerScopeMMDAC(int dacNr) :
   voltRangeS_(g_DACR1)
{
   bTS16_ = false;
   dacNr_ = dacNr;
   initialized_ = false;
   volts_ = 0.0;
   minV_ = 0.0;
   maxV_ = 10.0;
   busy_ = false;
   open_ = false;
   blanking_ = false;
   blankOnLow_ = true;

   gateOpen_ = true;
   gatedVolts_ = 0.0;

   const char* vRange = "Voltage Range";
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeMMDAC::OnVoltRange);
   CreateProperty(vRange, g_DACR1, MM::String, false, pAct, true); 

   AddAllowedValue(vRange, g_DACR1);
   AddAllowedValue(vRange, g_DACR2);
   AddAllowedValue(vRange, g_DACR3);
   AddAllowedValue(vRange, g_DACR4);
   AddAllowedValue(vRange, g_DACR5);
}


void CTriggerScopeMMDAC::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_TriggerScopeMMDACDeviceName);
   snprintf(&name[strlen(name)-2], 3, "%02d", dacNr_);
}


int CTriggerScopeMMDAC::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   pHub_ = static_cast<CTriggerScopeMMHub*>(GetParentHub());
   if (!pHub_ || !pHub_->IsInitialized()) {
      return ERR_NO_PORT_SET;
   }
   bTS16_ = pHub_->GetTS16();
   char hubLabel[MM::MaxStrLength];
   pHub_->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   std::ostringstream os;
   os << "SAR" << dacNr_ << "-" << (int) voltrange_;
   int ret = pHub_->SendAndReceive(os.str().c_str());

   std::string tmp;
   std::istringstream is(voltRangeS_);
   is >> minV_;
   is >> tmp;  // reads away the dash
   is >> maxV_;

   std::ostringstream oss;
   oss << "PAN" << dacNr_;
   std::string answer;
   int nRet = pHub_->SendAndReceive(oss.str().c_str(), answer);
   if (nRet != DEVICE_OK)
      return nRet;
   // answer looks like !PAN1-560 or !PAN11-560
   std::string token = answer.substr(answer.find("-") + 1);
   std::stringstream as (token);
   as >> nrEvents_;

   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeMMDAC::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   pAct = new CPropertyAction (this, &CTriggerScopeMMDAC::OnVolts);
   ret = CreateProperty("Volts", "0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("Volts", minV_, maxV_);
   if (ret != DEVICE_OK) 
	  return ret;

   pAct = new CPropertyAction (this, &CTriggerScopeMMDAC::OnSequence);
	ret = CreateProperty("Sequence", g_On, MM::String, false, pAct);
	if (ret != DEVICE_OK)
		return ret;
	AddAllowedValue("Sequence", g_On);
	AddAllowedValue("Sequence", g_Off);

   std::string sequenceTriggerDirection = "Sequence Trigger Edge";
   pAct = new CPropertyAction(this, &CTriggerScopeMMDAC::OnSequenceTriggerDirection);
   ret = CreateProperty(sequenceTriggerDirection.c_str(), g_Rising, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(sequenceTriggerDirection.c_str(), g_Falling);
   AddAllowedValue(sequenceTriggerDirection.c_str(), g_Rising);

   std::string blankMode = "Blanking";
   pAct = new CPropertyAction(this, &CTriggerScopeMMDAC::OnBlanking);
   nRet = CreateProperty(blankMode.c_str(), g_Off, MM::String, false, pAct);
   if (nRet != DEVICE_OK) 
      return nRet;
	AddAllowedValue(blankMode.c_str(), g_Off);
	AddAllowedValue(blankMode.c_str(), g_On);

   std::string blankOn = "Blank On";
   pAct = new CPropertyAction(this, &CTriggerScopeMMDAC::OnBlankingTriggerDirection);
   nRet = CreateProperty(blankOn.c_str(), g_Low, MM::String, false, pAct);
   if (nRet != DEVICE_OK) 
      return nRet;
	AddAllowedValue(blankOn.c_str(), g_Low);
	AddAllowedValue(blankOn.c_str(), g_High);

   initialized_ = true;
   return DEVICE_OK;
}


int CTriggerScopeMMDAC::SetGateOpen(bool open)
{
   if (open) {
      int ret = SetSignal(volts_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = true;
   } else {
      int ret = SetSignal(0);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
   }
   return DEVICE_OK;
}

int CTriggerScopeMMDAC::GetGateOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}


int CTriggerScopeMMDAC::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetGateOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1) {
         ret = this->SetGateOpen(true);
      } else {
         ret = this->SetGateOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int CTriggerScopeMMDAC::WriteSignal(double volts)
{
	if(volts < minV_)
		volts = minV_ ;
	if(volts > maxV_)
		volts = maxV_ ;

	volts_ = volts;
	double dMaxCount = 4095;
	if(bTS16_)
		dMaxCount = 65535;

   long value = (long) ( (volts - minV_) / maxV_ * dMaxCount);

   std::ostringstream os;
    os << "Volts: " << volts << " Max Voltage: " << maxV_ << " digital value: " << value;
    LogMessage(os.str().c_str(), true);

    
	char str[32];
	snprintf(str, 32, "SAO%d-%d", dacNr_, int(value));
   return pHub_->SendAndReceive(str);

}

int CTriggerScopeMMDAC::SetSignal(double volts)
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


int CTriggerScopeMMDAC::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(volts_);
   }
   else if (eAct == MM::AfterSet)
   {
		double volts;
	   pProp->Get(volts);
		WriteSignal(volts);
   }

   return DEVICE_OK;
}


int CTriggerScopeMMDAC::OnVoltRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(voltRangeS_.c_str());
   }
      else if (eAct == MM::AfterSet)
   {
      std::string voltR;
      pProp->Get(voltR);
      if (voltR == g_DACR1) { voltrange_ = 1; }
      else if (voltR == g_DACR2) { voltrange_ = 2; }      
      else if (voltR == g_DACR3) { voltrange_ = 3; }
      else if (voltR == g_DACR4) { voltrange_ = 4; }
      else if (voltR == g_DACR5) { voltrange_ = 5; }
      else 
         return DEVICE_INVALID_PROPERTY_VALUE;

      voltRangeS_ = voltR;

   }

   return DEVICE_OK;
}

int CTriggerScopeMMDAC::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int CTriggerScopeMMDAC::OnSequenceTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceTransitionOnRising_)
         pProp->Set(g_Rising);
      else
         pProp->Set(g_Falling);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string val;
      pProp->Get(val);
      if (val == g_Rising)
         sequenceTransitionOnRising_ = true;
      else
         sequenceTransitionOnRising_ = false;
   }
   return DEVICE_OK;
}


int CTriggerScopeMMDAC::OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (blanking_)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == g_On)
         blanking_ = true;
      else
         blanking_ = false;
      return SendBlankingCommand();
   }
   return DEVICE_OK;
}

int CTriggerScopeMMDAC::OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (blankOnLow_)
         pProp->Set(g_Low);
      else
         pProp->Set(g_High);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == g_Low)
         blankOnLow_ = true;
      else
         blankOnLow_ = false;
      return SendBlankingCommand();
   }
   return DEVICE_OK;
}



int CTriggerScopeMMDAC::SendBlankingCommand()
{   
   std::ostringstream os;
   os << "BAO" << (int) dacNr_ << "-" << (blanking_ ? "1" : "0") << 
               "-" << (blankOnLow_ ? "0" : "1"); 
   return pHub_->SendAndReceive(os.str().c_str());
}


int CTriggerScopeMMDAC::StartDASequence()
{
   std::ostringstream os;
   os << "PAS" << (int) dacNr_ << "-1-" << (sequenceTransitionOnRising_ ? "1" : "0"); 
   return pHub_->SendAndReceive(os.str().c_str());
}

int CTriggerScopeMMDAC::StopDASequence()
{
	std::ostringstream os;
   os << "PAS" << (int) dacNr_ << "-0-" << (sequenceTransitionOnRising_ ? "1" : "0"); 
   return pHub_->SendAndReceive(os.str().c_str());
}

int CTriggerScopeMMDAC::SendDASequence()
{
	char str[128];
	snprintf(str, 128, "PAC%d", dacNr_);
   int ret = pHub_->SendAndReceive(str);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream os;
   os << "PAO" << (int) dacNr_ << "-" << "0";

   double dMaxCount = 4095, volts;
	if(bTS16_)
		dMaxCount = 65535;

   for (unsigned int i = 0; i < sequence_.size(); i++) 
   {
      volts = sequence_[i];

		if(volts < minV_)
			volts = minV_ ;
		if(volts > maxV_)
			volts = maxV_ ;

     int  value = (int) ( (volts - minV_) / maxV_ * dMaxCount);

     os << "-" << value;
   }

   return pHub_->SendAndReceive(os.str().c_str());

}

int CTriggerScopeMMDAC::ClearDASequence()
{
	sequence_.clear();
   // clear sequence from the device (as per API documentation)
   char str[128];
   snprintf(str, 128, "PAC%d", dacNr_);
   return pHub_->SendAndReceive(str);
}

int CTriggerScopeMMDAC::AddToDASequence(double voltage)
{
   sequence_.push_back(voltage);

   return DEVICE_OK;
}


