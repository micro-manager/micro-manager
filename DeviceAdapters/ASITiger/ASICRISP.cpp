///////////////////////////////////////////////////////////////////////////////
// FILE:          ASICRISP.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI CRISP autofocus device adapter
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

#include "ASICRISP.h"
#include "ASIHub.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"
#include <iostream>
#include <cmath>
#include <sstream>
#include <string>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CCRISP
//
CCRISP::CCRISP(const char* name) :
   ASIPeripheralBase< ::CAutoFocusBase, CCRISP >(name),
   axisLetter_(g_EmptyAxisLetterStr),    // value determined by extended name
   waitAfterLock_(1000)
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetter_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
   }
}

int CCRISP::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   ostringstream command;
   command.str("");
   command << g_CRISPDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // create properties and corresponding action handlers

   CPropertyAction* pAct;

   // refresh properties from controller every time - default is not to refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CCRISP::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   pAct = new CPropertyAction(this, &CCRISP::OnFocusState);
   CreateProperty (g_CRISPState, g_CRISP_I, MM::String, false, pAct);
   AddAllowedValue(g_CRISPState, g_CRISP_I, 79);
   AddAllowedValue(g_CRISPState, g_CRISP_R, 85);
   AddAllowedValue(g_CRISPState, g_CRISP_D);
   AddAllowedValue(g_CRISPState, g_CRISP_K, 83);
   AddAllowedValue(g_CRISPState, g_CRISP_F);
   AddAllowedValue(g_CRISPState, g_CRISP_N);
   AddAllowedValue(g_CRISPState, g_CRISP_E);
   AddAllowedValue(g_CRISPState, g_CRISP_G, 72);
   AddAllowedValue(g_CRISPState, g_CRISP_SG, 67);
   AddAllowedValue(g_CRISPState, g_CRISP_f, 102);
   AddAllowedValue(g_CRISPState, g_CRISP_C, 97);
   AddAllowedValue(g_CRISPState, g_CRISP_B, 66);
   AddAllowedValue(g_CRISPState, g_CRISP_RFO, 111);
   AddAllowedValue(g_CRISPState, g_CRISP_SSZ);

   pAct = new CPropertyAction(this, &CCRISP::OnWaitAfterLock);
   CreateProperty(g_CRISPWaitAfterLockPropertyName, "1000", MM::Integer, false, pAct);
   UpdateProperty(g_CRISPWaitAfterLockPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnNA);
   CreateProperty(g_CRISPObjectiveNAPropertyName, "0.8", MM::Float, false, pAct);
   SetPropertyLimits(g_CRISPObjectiveNAPropertyName, 0, 1.65);
   UpdateProperty(g_CRISPObjectiveNAPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnLockRange);
   CreateProperty(g_CRISPLockRangePropertyName, "0.05", MM::Float, false, pAct);
   UpdateProperty(g_CRISPLockRangePropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnCalGain);
   CreateProperty(g_CRISPCalibrationGainPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_CRISPCalibrationGainPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnLEDIntensity);
   CreateProperty(g_CRISPLEDIntensityPropertyName, "50", MM::Integer, false, pAct);
   SetPropertyLimits(g_CRISPLEDIntensityPropertyName, 0, 100);
   UpdateProperty(g_CRISPLEDIntensityPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnLoopGainMultiplier);
   CreateProperty(g_CRISPLoopGainMultiplierPropertyName, "10", MM::Integer, false, pAct);
   SetPropertyLimits(g_CRISPLoopGainMultiplierPropertyName, 0, 100);
   UpdateProperty(g_CRISPLoopGainMultiplierPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnNumAvg);
   CreateProperty(g_CRISPNumberAveragesPropertyName, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_CRISPNumberAveragesPropertyName, 0, 8);
   UpdateProperty(g_CRISPNumberAveragesPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnSNR);
   CreateProperty(g_CRISPSNRPropertyName, "", MM::Float, true, pAct);
   UpdateProperty(g_CRISPSNRPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnDitherError);
   CreateProperty(g_CRISPDitherErrorPropertyName, "", MM::Integer, true, pAct);
   UpdateProperty(g_CRISPDitherErrorPropertyName);

   pAct = new CPropertyAction(this, &CCRISP::OnLogAmpAGC);
   CreateProperty(g_CRISPLogAmpAGCPropertyName, "", MM::Integer, true, pAct);
   UpdateProperty(g_CRISPLogAmpAGCPropertyName);

   initialized_ = true;
   return DEVICE_OK;
}

bool CCRISP::Busy()
{
   // not sure how to define it, Nico's ASIStage adapter hard-codes it false so I'll do same thing
   return false;
}


int CCRISP::SetContinuousFocusing(bool state)
{
   ostringstream command; command.str("");
   command << addressChar_ << "LK F=";
   if(state)
      command << "83";
   else
      command << "85";
   return hub_->QueryCommandVerify(command.str(), ":A");
}

int CCRISP::GetContinuousFocusing(bool& state)
{
   // this returns true if trying to focus but not yet locked, not sure if that is intended use
   RETURN_ON_MM_ERROR( UpdateFocusState() );
   state = (focusState_ == g_CRISP_K);
   return DEVICE_OK;
}

bool CCRISP::IsContinuousFocusLocked()
{
   // this returns true if focus already locked
   if (UpdateFocusState() == DEVICE_OK)
      return (focusState_ == g_CRISP_F);
   else
      return false;
}

int CCRISP::FullFocus()
{
   // Does a "one-shot" autofocus: locks and then unlocks again
   RETURN_ON_MM_ERROR ( SetContinuousFocusing(true) );

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime wait(0, waitAfterLock_ * 1000);
   while (!IsContinuousFocusLocked() && ( (GetCurrentMMTime() - startTime) < wait) ) {
      CDeviceUtils::SleepMs(25);
   }

   CDeviceUtils::SleepMs(waitAfterLock_);

   if (!IsContinuousFocusLocked()) {
      SetContinuousFocusing(false);
      return ERR_CRISP_NOT_LOCKED;
   }

   return SetContinuousFocusing(false);
}

int CCRISP::IncrementalFocus()
{
   return FullFocus();
}

int CCRISP::GetLastFocusScore(double& score)
{
   score = 0; // init in case we can't read it
   ostringstream command; command.str("");
   command << addressChar_ << "LK Y?";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return hub_->ParseAnswerAfterPosition3(score);
}

int CCRISP::GetCurrentFocusScore(double& score)
{
   return GetLastFocusScore(score);
}

int CCRISP::GetOffset(double& offset)
{
   ostringstream command; command.str("");
   command << addressChar_ << "LK Z?";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return hub_->ParseAnswerAfterPosition3(offset);
}

int CCRISP::SetOffset(double offset)
{
   ostringstream command; command.str("");
   command << addressChar_ << "LK Z=" << offset;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return DEVICE_OK;
}

int CCRISP::UpdateFocusState()
{
   ostringstream command; command.str("");
   command << addressChar_ << "LK X?";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );

   char c;
   RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );

   switch (c)
   {
      case 'I': focusState_ = g_CRISP_I; break;
      case 'R': focusState_ = g_CRISP_R; break;
      case 'D': focusState_ = g_CRISP_D; break;
      case 'K': focusState_ = g_CRISP_K; break;  // trying to lock, goes to F when locked
      case 'F': focusState_ = g_CRISP_F; break;  // this is read-only state
      case 'N': focusState_ = g_CRISP_N; break;
      case 'E': focusState_ = g_CRISP_E; break;
      case 'G': focusState_ = g_CRISP_G; break;
      case 'f': focusState_ = g_CRISP_f; break;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case 'g':
      case 'h':
      case 'i':
      case 'j': focusState_ = g_CRISP_Cal; break;
      case 'c': focusState_ = g_CRISP_C; break;
      case 'B': focusState_ = g_CRISP_B; break;
      case 'o': focusState_ = g_CRISP_RFO; break;
      default:  return ERR_UNRECOGNIZED_ANSWER;
   }
   return DEVICE_OK;
}

int CCRISP::SetFocusState(string focusState)
{
   RETURN_ON_MM_ERROR ( UpdateFocusState() );

   if (focusState == focusState_)
      return DEVICE_OK;

   if (focusState == g_CRISP_R)  // Unlock
      return SetContinuousFocusing(false);

   if (focusState == g_CRISP_K)  // Unlock
      return SetContinuousFocusing(true);

   ostringstream command; command.str("");
   if (focusState == g_CRISP_SSZ) // save settings to controller
      command << addressChar_ << "SS Z";
   else if (focusState == g_CRISP_I)  // Idle (switch off LED)
      command << addressChar_ << "LK F=" << "79";
   else if (focusState == g_CRISP_G) // log-amp calibration
         command << addressChar_ << "LK F=" << "72";
   else if (focusState == g_CRISP_SG) // gain_cal (servo) calibration
         command << addressChar_ << "LK F=" << "67";
   else if (focusState == g_CRISP_f) // dither
         command << addressChar_ << "LK F=" << "102";
   else if (focusState == g_CRISP_RFO) // reset focus offset
         command << addressChar_ << "LK F=" << "108";

   if (command.str() == "")
      return DEVICE_OK;  // don't complain if we try to set to something else
   else
      return hub_->QueryCommandVerify(command.str(), ":A");
}


////////////////
// action handlers

int CCRISP::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CCRISP::OnFocusState(MM::PropertyBase* pProp, MM::ActionType eAct)
// read this every time
{
   if (eAct == MM::BeforeGet)
   {
      RETURN_ON_MM_ERROR( UpdateFocusState() );
      pProp->Set(focusState_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string focusState;
      pProp->Get(focusState);
      RETURN_ON_MM_ERROR( SetFocusState(focusState) );
   }
   return DEVICE_OK;
}

int CCRISP::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
// property value set in MM only, not read from nor written to controller
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(waitAfterLock_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(waitAfterLock_);
   }

   return DEVICE_OK;
}

int CCRISP::OnNA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "LR Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "LR Y=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnCalGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "LR X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "LR X=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnLockRange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "LR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "LR Z=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnLEDIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "UL X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "UL X=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnLoopGainMultiplier(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "KA " << axisLetter_ << "?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "KA " << axisLetter_ << "="<< tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnNumAvg(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      command << addressChar_ << "RT F=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CCRISP::OnSNR(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      // always read
      command << addressChar_ << "EXTRA Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommand(command.str()) );
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterPosition(0, tmp));
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CCRISP::OnDitherError(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      // always read
      command << addressChar_ << "EXTRA X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommand(command.str()) );
      vector<string> vReply = hub_->SplitAnswerOnSpace();
      if (vReply.size() <= 2)
         return DEVICE_INVALID_PROPERTY_VALUE;
      if (!pProp->Set(vReply[2].c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CCRISP::OnLogAmpAGC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      // always read
      command << addressChar_ << "AFLIM X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

