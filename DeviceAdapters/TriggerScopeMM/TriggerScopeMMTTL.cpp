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
//                Regents of the University of California
//
// LICENSE:       

//
//                This file is distributed in the hope that it will be useful,
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
/**** CTriggerScopeMMTTL  ****/


CTriggerScopeMMTTL::CTriggerScopeMMTTL(uint8_t pinGroup) :
   pinGroup_(pinGroup)
{
   pHub_ = 0;
   numPatterns_ = 50;
	initialized_ = false;
	ttl_ = 0;
	busy_ = false;
   numPos_ = 256;
	sequenceOn_ = true;
   sequenceTransitionOnRising_ = true;
   gateOpen_ = true;
   blanking_ = false;
   blankOnLow_ = true; 
   isClosed_ = false;
}

void CTriggerScopeMMTTL::GetName(char* name) const
{	
   if(pinGroup_ == 1)
   {
	   CDeviceUtils::CopyLimitedString(name, g_TriggerScopeMMTTLDeviceName1);   
   }
   else
   {
      CDeviceUtils::CopyLimitedString(name, g_TriggerScopeMMTTLDeviceName2);
   }
}


int CTriggerScopeMMTTL::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   pHub_ = static_cast<CTriggerScopeMMHub*>(GetParentHub());
   if (!pHub_ || !pHub_->IsInitialized()) 
      return ERR_NO_PORT_SET;
   
   char hubLabel[MM::MaxStrLength];
   pHub_->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.


   std::ostringstream os;
   os << "PDN" << (int) pinGroup_;
   std::string answer;
   int nRet = pHub_->SendAndReceive(os.str().c_str(), answer);
   if (nRet != DEVICE_OK)
      return nRet;
   // asnwer looks like !PDN0-560
   std::stringstream as (answer.substr(6));
   as >> numPatterns_;

   CPropertyAction* pAct = new CPropertyAction(this, &CTriggerScopeMMTTL::OnSequence);
	nRet = CreateProperty("Sequence", g_On, MM::String, false, pAct);
	if (nRet != DEVICE_OK)
	   return nRet;
	AddAllowedValue("Sequence", g_On);
	AddAllowedValue("Sequence", g_Off);

   std::string sequenceTriggerDirection = "Sequence Trigger Edge";
   pAct = new CPropertyAction(this, &CTriggerScopeMMTTL::OnSequenceTriggerDirection);
   nRet = CreateProperty(sequenceTriggerDirection.c_str(), g_Rising, MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   AddAllowedValue(sequenceTriggerDirection.c_str(), g_Falling);
   AddAllowedValue(sequenceTriggerDirection.c_str(), g_Rising);
   
   std::string blankMode = "Blanking";
   pAct = new CPropertyAction(this, &CTriggerScopeMMTTL::OnBlanking);
   nRet = CreateProperty(blankMode.c_str(), g_Off, MM::String, false, pAct);
   if (nRet != DEVICE_OK) 
      return nRet;
	AddAllowedValue(blankMode.c_str(), g_Off);
	AddAllowedValue(blankMode.c_str(), g_On);

   std::string blankOn = "Blank On";
   pAct = new CPropertyAction(this, &CTriggerScopeMMTTL::OnBlankingTriggerDirection);
   nRet = CreateProperty(blankOn.c_str(), g_Low, MM::String, false, pAct);
   if (nRet != DEVICE_OK) 
      return nRet;
	AddAllowedValue(blankOn.c_str(), g_Low);
	AddAllowedValue(blankOn.c_str(), g_High);


   // State
   // -----
   pAct = new CPropertyAction (this, &CTriggerScopeMMTTL::OnState);   
	nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;
   SetPropertyLimits(MM::g_Keyword_State, 0, numPos_ - 1);

   for (long ttlNr = 1; ttlNr <= 8; ttlNr ++) 
   {
      long pinNr = ttlNr + (pinGroup_ * 8l);
      std::ostringstream os;
      if (pinNr == 9) 
         os << "TTL-" << std::setfill('0') << std::setw(2) << pinNr;
      else
         os << "TTL-" << pinNr;
      std::string propName = os.str();
      CPropertyActionEx* pActEx = new CPropertyActionEx(this, &CTriggerScopeMMTTL::OnTTL, ttlNr - 1);
      nRet = CreateProperty(propName.c_str(), "0", MM::Integer, false, pActEx, false);
      if (nRet != DEVICE_OK)
      {
         return nRet;
      }
      SetPropertyLimits(propName.c_str(), 0, 1);
   }

   curPos_ = 0;

   initialized_ = true;
   return DEVICE_OK;
}


int CTriggerScopeMMTTL::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CTriggerScopeMMTTL::OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CTriggerScopeMMTTL::OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
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



/**
   * Implements a gate, i.e. a position where the state device is closed
   * The gate needs to be implemented in the adapter's 'OnState function
   * (which is called through SetPosition)
   */
int CTriggerScopeMMTTL::SetGateOpen(bool open)
{  
   if (gateOpen_ != open) {
      gateOpen_ = open;
      SetPosition((int)open);
   }
   return DEVICE_OK;
}


int CTriggerScopeMMTTL::GetGateOpen(bool& open)
{
   open = gateOpen_; 
   return DEVICE_OK;
}


int CTriggerScopeMMTTL::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      if (pos >= (long)numPos_ || pos < 0)
      {
         pProp->Set((long)curPos_); // revert
         return ERR_UNKNOWN_POSITION;
      }

      if (gateOpen_) {
         if ((pos == curPos_ && !isClosed_)) {
            return DEVICE_OK;
         }

         if (SendStateCommand((uint8_t) pos) != DEVICE_OK)
         {
            pProp->Set((long)curPos_); // revert
            return ERR_UNKNOWN_POSITION;
         }
         isClosed_ = false;
      } 
      else if (!isClosed_) 
      {
         uint8_t gateClosedPosition = 0;

         if (SendStateCommand(gateClosedPosition) != DEVICE_OK)
         {
            pProp->Set((long) curPos_); // revert
            return ERR_UNKNOWN_POSITION;
         }
         isClosed_ = true;
      }

      curPos_ = pos;
	   return DEVICE_OK;
   }

   else if (eAct == MM::IsSequenceable)                                      
   {                                                                         
      if (sequenceOn_)                                                       
         pProp->SetSequenceable(numPatterns_);                           
      else                                                                   
         pProp->SetSequenceable(0);                                          
   } 
   else if (eAct == MM::AfterLoadSequence)                                   
   {                                                                         
      std::vector<std::string> sequence = pProp->GetSequence();  
      // check for invalid values
      std::ostringstream os;
      if (sequence.size() > numPatterns_)                                
         return DEVICE_SEQUENCE_TOO_LARGE;  
      int val = -1;
      for (unsigned int i=0; i < sequence.size(); i++)                       
      {
         std::istringstream os (sequence[i]);
         os >> val;
         if (val < 0 || val > 255)
            return ERR_INVALID_VALUE;
      }  

      // first clear the device's memory 
      std::ostringstream clCmd;
      clCmd << "PDC" << (int) pinGroup_;
      int ret = pHub_->SendAndReceive(clCmd.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      // then send the sequence
      std::ostringstream cout;
      cout << "PDO" << (int) pinGroup_ << "-0";

      for(std::vector<std::string>::iterator it = sequence.begin(); it != sequence.end(); ++it) 
      {
         cout << "-" << *it;
      }

      return pHub_->SendAndReceive(cout.str().c_str());
                                                                                                                      
   }                                                                         
   else if (eAct == MM::StartSequence)
   { 
      std::ostringstream cout;
      cout << "PDS" << (int) pinGroup_ << "-1-" << (int) sequenceTransitionOnRising_;

      return pHub_->SendAndReceive(cout.str().c_str());
    }
   else if (eAct == MM::StopSequence)                                        
   {
      std::ostringstream cout;
      cout << "PDS" << (int) pinGroup_ << "-0-" << (int) sequenceTransitionOnRising_;

      return pHub_->SendAndReceive(cout.str().c_str());
   }                                                                         

   return DEVICE_OK;
}


int CTriggerScopeMMTTL::OnTTL(MM::PropertyBase* pProp, MM::ActionType eActEx, long ttlNr)
{
   if (eActEx == MM::BeforeGet)
   {
      long state =  (curPos_ >> ttlNr) & 1 ;
      pProp->Set(state);
   } else if (eActEx == MM::AfterSet)
   {
      long prop;
      pProp->Get(prop);

      if(prop)
      {
         prop |= prop << ttlNr; //set desired bit
      } else {
         prop &= ~(1 << ttlNr); // clear the second lowest bit
      }
      std::ostringstream os;
      os << prop;
      return SetProperty(MM::g_Keyword_State, os.str().c_str());
   }
   return DEVICE_OK;
}

int CTriggerScopeMMTTL::OnSequenceTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int CTriggerScopeMMTTL::SendStateCommand(uint8_t value)
{
	char str[18];
	snprintf(str, 18, "SDO%d-%d", pinGroup_,int(value));
	return pHub_->SendAndReceive(str);
}

int CTriggerScopeMMTTL::SendBlankingCommand()
{
   char str[128];
   snprintf(str, 128, "BDO%d-%d-%d", pinGroup_, blanking_, !blankOnLow_);

   return pHub_->SendAndReceive(str);
}
