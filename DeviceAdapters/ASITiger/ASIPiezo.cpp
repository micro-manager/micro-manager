///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIPiezo.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI motorized one-axis stage device adapter
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

#include "ASIPiezo.h"
#include "ASITiger.h"
#include "ASIHub.h"
#include "ASIDevice.h"
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
// CPiezo
//
CPiezo::CPiezo(const char* name) :
   ASIDevice(this,name),
   unitMult_(g_StageDefaultUnitMult),  // later will try to read actual setting
   stepSizeUm_(g_StageMinStepSize),    // we'll use 1 nm as our smallest possible step size, this is somewhat arbitrary and doesn't change during the program
   axisLetter_(g_EmptyAxisLetterStr)   // value determined by extended name
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetter_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
   }
}

int CPiezo::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( ASIDevice::Initialize() );

   // read the unit multiplier
   // ASI's unit multiplier is how many units per mm, so divide by 1000 here to get units per micron
   // we store the micron-based unit multiplier for MM use, not the mm-based one ASI uses
   ostringstream command;
   command.str("");
   command << "UM " << axisLetter_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   unitMult_ = hub_->ParseAnswerAfterEquals()/1000;
   command.str("");

   // set controller card to return positions with 3 decimal places (max allowed currently)
   command.str("");
   command << addressChar_ << "VB Z=3";
   RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );

   // expose the step size to user as read-only property (no need for action handler)
   command.str("");
   command << g_StageMinStepSize;
   CreateProperty(g_StepSizePropertyName , command.str().c_str(), MM::Float, true);

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_PiezoDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // now for properties that are read-write, mostly parameters that set aspects of stage behavior
   // parameters exposed for user to set easily: SL, SU, JS X=, JS Y=, JS mirror

   CPropertyAction* pAct;

   // refresh properties from controller every time - default is not to refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CPiezo::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CPiezo::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);

   // upper and lower limits (SU and SL)
   pAct = new CPropertyAction (this, &CPiezo::OnLowerLim);
   CreateProperty(g_LowerLimPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_LowerLimPropertyName);
   pAct = new CPropertyAction (this, &CPiezo::OnUpperLim);
   CreateProperty(g_UpperLimPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_UpperLimPropertyName);

   // high voltage reading for diagnostics
   command.str("");
   command << addressChar_ << "PZINFO";
   RETURN_ON_MM_ERROR( hub_->QueryCommand(command.str()));
   vector<string> vReply = hub_->SplitAnswerOnCR();
   hub_->SetLastSerialAnswer(vReply[2]);  // 3rd line has the HV info
   command.str("");
   command << hub_->ParseAnswerAfterColon();
   CreateProperty(g_CardVoltagePropertyName, command.str().c_str(), MM::Float, true);
   UpdateProperty(g_CardVoltagePropertyName);

   // piezo range
   command.str("");
   command << "PR " << axisLetter_ << "?";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   command.str("");
   switch ((long) hub_->ParseAnswerAfterEquals())
   {
      case 1: command << "50"; break;
      case 2: command << "100"; break;
      case 3: command << "150"; break;
      case 4: command << "200"; break;
      case 5: command << "300"; break;
      case 6: command << "350"; break;
      case 7: command << "500"; break;
      default: command << "0"; break;
   }
   CreateProperty(g_PiezoTravelRangePropertyName, command.str().c_str(), MM::Integer, true);
   UpdateProperty(g_PiezoTravelRangePropertyName);

   // operational mode: closed vs. open loop, internal vs. external input
   pAct = new CPropertyAction (this, &CPiezo::OnPiezoMode);
   CreateProperty(g_PiezoModePropertyName, g_AdeptMode_0, MM::String, false, pAct);
   UpdateProperty(g_PiezoModePropertyName);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_0);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_1);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_2);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_3);

   // Motor enable/disable (MC)
   pAct = new CPropertyAction (this, &CPiezo::OnMotorControl);
   CreateProperty(g_MotorControlPropertyName, g_OnState, MM::String, false, pAct);
   UpdateProperty(g_MotorControlPropertyName);
   AddAllowedValue(g_MotorControlPropertyName, g_OnState);
   AddAllowedValue(g_MotorControlPropertyName, g_OffState);

   // joystick fast speed (JS X=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickFastSpeed);
   CreateProperty(g_JoystickFastSpeedPropertyName, "100", MM::Integer, false, pAct);
   UpdateProperty(g_JoystickFastSpeedPropertyName);
   SetPropertyLimits(g_JoystickFastSpeedPropertyName, 0, 100);

   // joystick slow speed (JS Y=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickSlowSpeed);
   CreateProperty(g_JoystickSlowSpeedPropertyName, "10", MM::Integer, false, pAct);
   UpdateProperty(g_JoystickSlowSpeedPropertyName);
   SetPropertyLimits(g_JoystickSlowSpeedPropertyName, 0, 100);

   // joystick mirror (changes joystick fast/slow speeds to negative) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickMirror);
   CreateProperty(g_JoystickMirrorPropertyName, g_NoState, MM::String, false, pAct);
   UpdateProperty(g_JoystickMirrorPropertyName);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_NoState);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_YesState);

   // joystick disable and select which knob
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickSelect);
   CreateProperty(g_JoystickSelectPropertyName, g_JSCode_0, MM::String, false, pAct);
   UpdateProperty(g_JoystickSelectPropertyName);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_0);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_2);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_3);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_22);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_23);

   initialized_ = true;
   return DEVICE_OK;
}

int CPiezo::GetPositionUm(double& pos)
{
   ostringstream command; command.str("");
   command << "W " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   pos = hub_->ParseAnswerAfterPosition(2)/unitMult_;
   return DEVICE_OK;
}

int CPiezo::SetPositionUm(double pos)
{
   ostringstream command; command.str("");
   command << "M " << axisLetter_ << "=" << pos*unitMult_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CPiezo::GetPositionSteps(long& steps)
{
   ostringstream command; command.str("");
   command << "W " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   steps = (long) (hub_->ParseAnswerAfterPosition(2)/unitMult_/stepSizeUm_);
   return DEVICE_OK;
}

int CPiezo::SetPositionSteps(long steps)
{
   ostringstream command; command.str("");
   command << "M " << axisLetter_ << "=" << steps*unitMult_*stepSizeUm_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CPiezo::SetRelativePositionUm(double d)
{
   ostringstream command; command.str("");
   command << "R " << axisLetter_ << "=" << d*unitMult_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CPiezo::GetLimits(double& min, double& max)
{
   // ASI limits are always returned in terms of mm, independent of unit multiplier
   ostringstream command; command.str("");
   command << "SL " << axisLetter_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   min = hub_->ParseAnswerAfterEquals()*1000;
   command.str("");
   command << "SU " << axisLetter_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   max = hub_->ParseAnswerAfterEquals()*1000;
   return DEVICE_OK;
}

int CPiezo::Stop()
{
   // note this stops the card, \ stops all stages
   ostringstream command; command.str("");
   command << addressChar_ << "halt";
   return hub_->QueryCommand(command.str());
}

bool CPiezo::Busy()
{
   ostringstream command; command.str("");
   if (firmwareVersion_ > 2.7) // can use more accurate RS <axis>?
   {
      command << "RS " << axisLetter_ << "?";
      ret_ = hub_->QueryCommandVerify(command.str(),":A");
      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      return (hub_->LastSerialAnswer().at(3) == 'B');
   }
   else  // use LSB of the status byte as approximate status, not quite equivalent
   {
      command << "RS " << axisLetter_;
      ret_ = hub_->QueryCommandVerify(command.str(),":A");
      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      int i = (int) (hub_->ParseAnswerAfterPosition(2));
      return (i & (int)BIT0);  // mask everything but LSB
   }
}

int CPiezo::SetOrigin()
{
   ostringstream command; command.str("");
   command << "H " << axisLetter_ << "=" << 0;
   return hub_->QueryCommandVerify(command.str(),":A");
}


////////////////
// action handlers

int CPiezo::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      command << addressChar_ << "SS ";
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SaveSettingsOrig) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsX) == 0)
         command << 'X';
      else if (tmpstr.compare(g_SaveSettingsY) == 0)
         command << 'X';
      else if (tmpstr.compare(g_SaveSettingsZ) == 0)
         command << 'Z';
      RETURN_ON_MM_ERROR (hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CPiezo::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CPiezo::OnLowerLim(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SL " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnUpperLim(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SU " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnPiezoMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "PM " << axisLetter_ << "?";
      ostringstream response; response.str(""); response << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success = 0;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_AdeptMode_0); break;
         case 1: success = pProp->Set(g_AdeptMode_1); break;
         case 2: success = pProp->Set(g_AdeptMode_2); break;
         case 3: success = pProp->Set(g_AdeptMode_3); break;
         default: success = 0;                        break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_AdeptMode_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_AdeptMode_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_AdeptMode_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_AdeptMode_3) == 0)
         tmp = 3;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "PM " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnMotorControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MC " << axisLetter_ << "?";
      response << ":A ";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterPosition(3);
      bool success = 0;
      if (tmp)
         success = pProp->Set(g_OnState);
      else
         success = pProp->Set(g_OffState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_OffState) == 0)
         command << "MC " << axisLetter_ << "-";
      else
         command << "MC " << axisLetter_ << "+";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnJoystickFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS X?";
      response << ":A X=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = abs(hub_->ParseAnswerAfterEquals());
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
     char joystickMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, joystickMirror) );
      if (strcmp(joystickMirror, g_YesState) == 0)
         command << addressChar_ << "JS X=-" << tmp;
      else
         command << addressChar_ << "JS X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnJoystickSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS Y?";
      response << ":A Y=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = abs(hub_->ParseAnswerAfterEquals());
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
     char joystickMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, joystickMirror) );
      if (strcmp(joystickMirror, g_YesState) == 0)
         command << addressChar_ << "JS Y=-" << tmp;
      else
         command << addressChar_ << "JS Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnJoystickMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS X?";  // query only the fast setting to see if already mirrored
      response << ":A X=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      bool success = 0;
      if (tmp < 0) // speed negative <=> mirrored
         success = pProp->Set(g_YesState);
      else
         success = pProp->Set(g_NoState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      double joystickFast = 0.0;
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickFastSpeedPropertyName, joystickFast) );
      double joystickSlow = 0.0;
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickSlowSpeedPropertyName, joystickSlow) );
      if (tmpstr.compare(g_YesState) == 0)
         command << addressChar_ << "JS X=-" << joystickFast << " Y=-" << joystickSlow;
      else
         command << addressChar_ << "JS X=" << joystickFast << " Y=" << joystickSlow;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnJoystickSelect(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "J " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success = 0;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_JSCode_0); break;
         case 1: success = pProp->Set(g_JSCode_1); break;
         case 2: success = pProp->Set(g_JSCode_2); break;
         case 3: success = pProp->Set(g_JSCode_3); break;
         case 22: success = pProp->Set(g_JSCode_22); break;
         case 23: success = pProp->Set(g_JSCode_23); break;
         default: success=0;
      }
      // don't complain if value is unsupported, just leave as-is
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_JSCode_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_JSCode_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_JSCode_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_JSCode_3) == 0)
         tmp = 3;
      else if (tmpstr.compare(g_JSCode_22) == 0)
         tmp = 22;
      else if (tmpstr.compare(g_JSCode_23) == 0)
         tmp = 23;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "J " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}


