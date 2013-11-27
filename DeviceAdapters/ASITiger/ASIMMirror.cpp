///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIMicromirror.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI micromirror device adapter
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
// BASED ON:      MicroPoint.cpp and others
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "ASIMMirror.h"
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

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CMMirror
//
CMMirror::CMMirror(const char* name) :
   ASIDevice(this,name),
   axisLetterX_(g_EmptyAxisLetterStr),    // value determined by extended name
   axisLetterY_(g_EmptyAxisLetterStr),    // value determined by extended name
   unitMultX_(g_MicromirrorDefaultUnitMult),  // later will try to read actual setting
   unitMultY_(g_MicromirrorDefaultUnitMult),  // later will try to read actual setting
   limitX_(0),   // later will try to read actual setting
   limitY_(0),   // later will try to read actual setting
   shutterX_(0), // home position, used to turn beam off
   shutterY_(0), // home position, used to turn beam off
   lastX_(0),    // cached position, used for SetIlluminationState
   lastY_(0),    // cached position, used for SetIlluminationState
   polygonRepetitions_(0)
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetterX_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterXPropertyName, axisLetterX_.c_str(), MM::String, true);
      axisLetterY_ = GetAxisLetterFromExtName(name,1);
      CreateProperty(g_AxisLetterYPropertyName, axisLetterY_.c_str(), MM::String, true);
   }
}

int CMMirror::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( ASIDevice::Initialize() );

   // read the unit multiplier for X and Y axes
   // ASI's unit multiplier is how many units per degree rotation for the micromirror card
   ostringstream command;
   command.str("");
   command << "UM " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   unitMultX_ = hub_->ParseAnswerAfterEquals();
   command.str("");
   command << "UM " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   unitMultY_ = hub_->ParseAnswerAfterEquals();

   // read the home position (used for beam shuttering)
   command.str("");
   command << "HM " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   shutterX_ = hub_->ParseAnswerAfterEquals();
   command.str("");
   command << "HM " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   shutterY_ = hub_->ParseAnswerAfterEquals();

   // set controller card to return positions with 3 decimal places (max allowed currently)
   command.str("");
   command << addressChar_ << "VB Z=3";
   RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_MMirrorDeviceDescription << " Xaxis=" << axisLetterX_ << " Yaxis=" << axisLetterY_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // remove for now because of bug in PZINFO, will replace by RDADC command later (Jon 23-Oct-13)
//   // high voltage reading for diagnostics
//   command.str("");
//   command << addressChar_ << "PZINFO";
//   RETURN_ON_MM_ERROR( hub_->QueryCommand(command.str()));
//   vector<string> vReply = hub_->SplitAnswerOnCR();
//   hub_->SetLastSerialAnswer(vReply[0]);  // 1st line has the HV info for micromirror vs. 3rd for piezo
//   command.str("");
//   command << hub_->ParseAnswerAfterColon();
//   CreateProperty(g_CardVoltagePropertyName, command.str().c_str(), MM::Float, true);

   // now create properties
   CPropertyAction* pAct;

   // refresh properties from controller every time - default is not to refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CMMirror::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CMMirror::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);

   // upper and lower limits (SU and SL) (limits not as useful for micromirror as for stage but they work)
   pAct = new CPropertyAction (this, &CMMirror::OnLowerLimX);
   CreateProperty(g_MMirrorLowerLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorLowerLimXPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnLowerLimY);
   CreateProperty(g_MMirrorLowerLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorLowerLimYPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnUpperLimX);
   CreateProperty(g_MMirrorUpperLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorUpperLimXPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnUpperLimY);
   CreateProperty(g_MMirrorUpperLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorUpperLimYPropertyName);

   // mode, currently just changes between internal and external input
   pAct = new CPropertyAction (this, &CMMirror::OnMode);
   CreateProperty(g_MMirrorModePropertyName, "0", MM::String, false, pAct);
   UpdateProperty(g_MMirrorModePropertyName);
   AddAllowedValue(g_MMirrorModePropertyName, g_MMirrorMode_0);
   AddAllowedValue(g_MMirrorModePropertyName, g_MMirrorMode_1);

   // filter cut-off frequency
   // decided to implement separately for X and Y axes so can have one fast and other slow
   pAct = new CPropertyAction (this, &CMMirror::OnCutoffFreqX);
   CreateProperty(g_MMirrorCutoffFilterXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorCutoffFilterXPropertyName);
   SetPropertyLimits(g_MMirrorCutoffFilterXPropertyName, 0.1, 650);
   pAct = new CPropertyAction (this, &CMMirror::OnCutoffFreqY);
   CreateProperty(g_MMirrorCutoffFilterYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorCutoffFilterYPropertyName);
   SetPropertyLimits(g_MMirrorCutoffFilterYPropertyName, 0.1, 650);

   // attenuation factor for movement
   pAct = new CPropertyAction (this, &CMMirror::OnAttenuateTravelX);
   CreateProperty(g_MMirrorAttenuateXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorAttenuateXPropertyName);
   SetPropertyLimits(g_MMirrorAttenuateXPropertyName, 0, 1);
   pAct = new CPropertyAction (this, &CMMirror::OnAttenuateTravelY);
   CreateProperty(g_MMirrorAttenuateYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorAttenuateYPropertyName);
   SetPropertyLimits(g_MMirrorAttenuateYPropertyName, 0, 1);

   // joystick fast speed (JS X=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CMMirror::OnJoystickFastSpeed);
   CreateProperty(g_JoystickFastSpeedPropertyName, "100", MM::Integer, false, pAct);
   UpdateProperty(g_JoystickFastSpeedPropertyName);
   SetPropertyLimits(g_JoystickFastSpeedPropertyName, 0, 100);

   // joystick slow speed (JS Y=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CMMirror::OnJoystickSlowSpeed);
   CreateProperty(g_JoystickSlowSpeedPropertyName, "10", MM::Integer, false, pAct);
   UpdateProperty(g_JoystickSlowSpeedPropertyName);
   SetPropertyLimits(g_JoystickSlowSpeedPropertyName, 0, 100);

   // joystick mirror (changes joystick fast/slow speeds to negative) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CMMirror::OnJoystickMirror);
   CreateProperty(g_JoystickMirrorPropertyName, g_NoState, MM::String, false, pAct);
   UpdateProperty(g_JoystickMirrorPropertyName);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_NoState);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_YesState);

   // joystick disable and select which knob
   pAct = new CPropertyAction (this, &CMMirror::OnJoystickSelectX);
   CreateProperty(g_JoystickSelectXPropertyName, g_JSCode_0, MM::String, false, pAct);
   UpdateProperty(g_JoystickSelectXPropertyName);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_0);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_2);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_3);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_22);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_23);
   pAct = new CPropertyAction (this, &CMMirror::OnJoystickSelectY);
   CreateProperty(g_JoystickSelectYPropertyName, g_JSCode_0, MM::String, false, pAct);
   UpdateProperty(g_JoystickSelectYPropertyName);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_0);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_2);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_3);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_22);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_23);

   // turn the beam on and off
   pAct = new CPropertyAction (this, &CMMirror::OnBeamEnabled);
   CreateProperty(g_MMirrorBeamEnabledPropertyName, g_YesState, MM::String, false, pAct);
   AddAllowedValue(g_MMirrorBeamEnabledPropertyName, g_NoState);
   AddAllowedValue(g_MMirrorBeamEnabledPropertyName, g_YesState);

   // single-axis mode settings
   // todo fix firmware TTL initialization problem where SAM p=2 triggers by itself 1st time
   pAct = new CPropertyAction (this, &CMMirror::OnSAAmplitudeX);
   CreateProperty(g_MMirrorSAAmplitudeXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorSAAmplitudeXPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAOffsetX);
   CreateProperty(g_MMirrorSAOffsetXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorSAOffsetXPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAPeriodX);
   CreateProperty(g_MMirrorSAPeriodXPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_MMirrorSAPeriodXPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAModeX);
   CreateProperty(g_MMirrorSAModeXPropertyName, g_SAMode_0, MM::String, false, pAct);
   UpdateProperty(g_MMirrorSAModeXPropertyName);
   AddAllowedValue(g_MMirrorSAModeXPropertyName, g_SAMode_0);
   AddAllowedValue(g_MMirrorSAModeXPropertyName, g_SAMode_1);
   AddAllowedValue(g_MMirrorSAModeXPropertyName, g_SAMode_2);
   AddAllowedValue(g_MMirrorSAModeXPropertyName, g_SAMode_3);
   pAct = new CPropertyAction (this, &CMMirror::OnSAPatternX);
   CreateProperty(g_MMirrorSAPatternXPropertyName, g_SAPattern_0, MM::String, false, pAct);
   UpdateProperty(g_MMirrorSAPatternXPropertyName);
   AddAllowedValue(g_MMirrorSAPatternXPropertyName, g_SAPattern_0);
   AddAllowedValue(g_MMirrorSAPatternXPropertyName, g_SAPattern_1);
   AddAllowedValue(g_MMirrorSAPatternXPropertyName, g_SAPattern_2);
   pAct = new CPropertyAction (this, &CMMirror::OnSAAmplitudeY);
   CreateProperty(g_MMirrorSAAmplitudeYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorSAAmplitudeYPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAOffsetY);
   CreateProperty(g_MMirrorSAOffsetYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_MMirrorSAOffsetYPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAPeriodY);
   CreateProperty(g_MMirrorSAPeriodYPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_MMirrorSAPeriodYPropertyName);
   pAct = new CPropertyAction (this, &CMMirror::OnSAModeY);
   CreateProperty(g_MMirrorSAModeYPropertyName, g_SAMode_0, MM::String, false, pAct);
   UpdateProperty(g_MMirrorSAModeYPropertyName);
   AddAllowedValue(g_MMirrorSAModeYPropertyName, g_SAMode_0);
   AddAllowedValue(g_MMirrorSAModeYPropertyName, g_SAMode_1);
   AddAllowedValue(g_MMirrorSAModeYPropertyName, g_SAMode_2);
   AddAllowedValue(g_MMirrorSAModeYPropertyName, g_SAMode_3);
   pAct = new CPropertyAction (this, &CMMirror::OnSAPatternY);
   CreateProperty(g_MMirrorSAPatternYPropertyName, g_SAPattern_0, MM::String, false, pAct);
   UpdateProperty(g_MMirrorSAPatternYPropertyName);
   AddAllowedValue(g_MMirrorSAPatternYPropertyName, g_SAPattern_0);
   AddAllowedValue(g_MMirrorSAPatternYPropertyName, g_SAPattern_1);
   AddAllowedValue(g_MMirrorSAPatternYPropertyName, g_SAPattern_2);

   // generates a set of additional advanced properties that are rarely used
   pAct = new CPropertyAction (this, &CMMirror::OnSAAdvancedX);
   CreateProperty(g_AdvancedSAPropertiesXPropertyName, g_NoState, MM::String, false, pAct);
   UpdateProperty(g_AdvancedSAPropertiesXPropertyName);
   AddAllowedValue(g_AdvancedSAPropertiesXPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedSAPropertiesXPropertyName, g_YesState);
   pAct = new CPropertyAction (this, &CMMirror::OnSAAdvancedY);
   CreateProperty(g_AdvancedSAPropertiesYPropertyName, g_NoState, MM::String, false, pAct);
   UpdateProperty(g_AdvancedSAPropertiesYPropertyName);
   AddAllowedValue(g_AdvancedSAPropertiesYPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedSAPropertiesYPropertyName, g_YesState);

   initialized_ = true;
   return DEVICE_OK;
}

bool CMMirror::Busy()
{
   ostringstream command; command.str("");
   if (firmwareVersion_ > 2.7) // can use more accurate RS <axis>?
   {
      command << "RS " << axisLetterX_ << "?";
      ret_ = hub_->QueryCommandVerify(command.str(),":A");
      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      if (hub_->LastSerialAnswer().at(3) == 'B')
         return true;
      command.str("");
      command << "RS " << axisLetterY_ << "?";
      return (hub_->LastSerialAnswer().at(3) == 'B');
   }
   else  // use LSB of the status byte as approximate status, not quite equivalent
   {
      command << "RS " << axisLetterX_;
      ret_ = hub_->QueryCommandVerify(command.str(),":A");
      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      int i = (int) (hub_->ParseAnswerAfterPosition(2));
      if (i & (int)BIT0)  // mask everything but LSB
         return true; // don't bother checking other axis
      command.str("");
      command << "RS " << axisLetterY_;
      ret_ = hub_->QueryCommandVerify(command.str(),":A");
      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      i = (int) (hub_->ParseAnswerAfterPosition(2));
      return (i & (int)BIT0);  // mask everything but LSB
   }
}

int CMMirror::SetPosition(double x, double y)
{
   ostringstream command; command.str("");
   command << "M " << axisLetterX_ << "=" << x*unitMultX_ << " " << axisLetterY_ << "=" << y*unitMultY_;
   ret_ = hub_->QueryCommandVerify(command.str(),":A");
   if (ret_ == DEVICE_OK)
   {
      // cache the position (if it worked) for SetIlluminationState
      lastX_ = x;
      lastY_ = y;
      return DEVICE_OK;
   }
   else
      return ret_;
}

int CMMirror::GetPosition(double& x, double& y)
{
   // read from card instead of using cached values directly, could be slight mismatch
   ostringstream command; command.str("");
   command << "W " << axisLetterX_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   x = hub_->ParseAnswerAfterPosition(2)/unitMultX_;
   command.str("");
   command << "W " << axisLetterY_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   y = hub_->ParseAnswerAfterPosition(2)/unitMultY_;
   return DEVICE_OK;
}

int CMMirror::SetIlluminationState(bool on)
// we can't turn off beam but we can steer beam to corner where hopefully it is blocked internally
{
   if (!on)
   {
//      GetPosition(lastX_, lastY_);  // cache position so we can undo
      return SetPosition(shutterX_, shutterY_);
   }
   else
   {
//      SetPosition(lastX_, lastY_);  // move to where it was when last turned off
      SetPosition(0,0);
      return DEVICE_OK;
   }
}

int CMMirror::AddPolygonVertex(int polygonIndex, double x, double y)
{
   if (polygons_.size() <  (unsigned) (1 + polygonIndex))
      polygons_.resize(polygonIndex + 1);
   polygons_[polygonIndex].first = x;
   polygons_[polygonIndex].second = y;
   return DEVICE_OK;
}

int CMMirror::DeletePolygons()
{
   polygons_.clear();
   return DEVICE_OK;
}

int CMMirror::LoadPolygons()
{
   // do nothing since device doesn't store polygons in HW
   return DEVICE_OK;
}

int CMMirror::SetPolygonRepetitions(int repetitions)
{
   polygonRepetitions_ = repetitions;
   return DEVICE_OK;
}

int CMMirror::RunPolygons()
{
   for (int j=0; j<polygonRepetitions_; ++j)
      for (int i=0; i< (int) polygons_.size(); ++i)
         SetPosition(polygons_[i].first,polygons_[i].second);
   return DEVICE_OK;
}


////////////////
// action handlers

int CMMirror::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMMirror::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CMMirror::OnLowerLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SL " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnLowerLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SL " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnUpperLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SU " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      limitX_ = tmp;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnUpperLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SU " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      limitY_ = tmp;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
// assume X axis's mode is for both, and then set mode for both axes together just like XYStage properties
// todo change to using PM for v2.8 and above
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MA " << axisLetterX_ << "?";
      ostringstream response; response.str(""); response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success = 0;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_MMirrorMode_0); break;
         case 1: success = pProp->Set(g_MMirrorMode_1); break;
         default: success = 0;                        break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_MMirrorMode_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_MMirrorMode_1) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "MA " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnCutoffFreqX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "B " << axisLetterX_ << "?";
      response << ":" << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "B " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnCutoffFreqY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "B " << axisLetterY_ << "?";
      response << ":" << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "B " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnAttenuateTravelX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "D " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "D " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnAttenuateTravelY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "D " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "D " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnBeamEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         SetIlluminationState(true);
      else
         SetIlluminationState(false);
   }
   return DEVICE_OK;
}

int CMMirror::OnSAAdvancedX(MM::PropertyBase* pProp, MM::ActionType eAct)
// special property, when set to "yes" it creates a set of little-used properties that can be manipulated thereafter
{
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
      {
         CPropertyAction* pAct;

         pAct = new CPropertyAction (this, &CMMirror::OnSAClkSrcX);
         CreateProperty(g_MMirrorSAClkSrcXPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSAClkSrcXPropertyName);
         AddAllowedValue(g_MMirrorSAClkSrcXPropertyName, g_SAClkSrc_0);
         AddAllowedValue(g_MMirrorSAClkSrcXPropertyName, g_SAClkSrc_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSAClkPolX);
         CreateProperty(g_MMirrorSAClkPolXPropertyName, g_SAClkPol_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSAClkPolXPropertyName);
         AddAllowedValue(g_MMirrorSAClkPolXPropertyName, g_SAClkPol_0);
         AddAllowedValue(g_MMirrorSAClkPolXPropertyName, g_SAClkPol_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSATTLOutX);
         CreateProperty(g_MMirrorSATTLOutXPropertyName, g_SATTLOut_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSATTLOutXPropertyName);
         AddAllowedValue(g_MMirrorSATTLOutXPropertyName, g_SATTLOut_0);
         AddAllowedValue(g_MMirrorSATTLOutXPropertyName, g_SATTLOut_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSATTLPolX);
         CreateProperty(g_MMirrorSATTLPolXPropertyName, g_SATTLPol_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSATTLPolXPropertyName);
         AddAllowedValue(g_MMirrorSATTLPolXPropertyName, g_SATTLPol_0);
         AddAllowedValue(g_MMirrorSATTLPolXPropertyName, g_SATTLPol_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSAPatternByteX);
         CreateProperty(g_MMirrorSAPatternModeXPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_MMirrorSAPatternModeXPropertyName);
         SetPropertyLimits(g_MMirrorSAPatternModeXPropertyName, 0, 255);
      }
   }
   return DEVICE_OK;
}

int CMMirror::OnSAAdvancedY(MM::PropertyBase* pProp, MM::ActionType eAct)
// special property, when set to "yes" it creates a set of little-used properties that can be manipulated thereafter
{
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
      {
         CPropertyAction* pAct;

         pAct = new CPropertyAction (this, &CMMirror::OnSAClkSrcY);
         CreateProperty(g_MMirrorSAClkSrcYPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSAClkSrcYPropertyName);
         AddAllowedValue(g_MMirrorSAClkSrcYPropertyName, g_SAClkSrc_0);
         AddAllowedValue(g_MMirrorSAClkSrcYPropertyName, g_SAClkSrc_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSAClkPolY);
         CreateProperty(g_MMirrorSAClkPolYPropertyName, g_SAClkPol_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSAClkPolYPropertyName);
         AddAllowedValue(g_MMirrorSAClkPolYPropertyName, g_SAClkPol_0);
         AddAllowedValue(g_MMirrorSAClkPolYPropertyName, g_SAClkPol_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSATTLOutY);
         CreateProperty(g_MMirrorSATTLOutYPropertyName, g_SATTLOut_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSATTLOutYPropertyName);
         AddAllowedValue(g_MMirrorSATTLOutYPropertyName, g_SATTLOut_0);
         AddAllowedValue(g_MMirrorSATTLOutYPropertyName, g_SATTLOut_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSATTLPolY);
         CreateProperty(g_MMirrorSATTLPolYPropertyName, g_SATTLPol_0, MM::String, false, pAct);
         UpdateProperty(g_MMirrorSATTLPolYPropertyName);
         AddAllowedValue(g_MMirrorSATTLPolYPropertyName, g_SATTLPol_0);
         AddAllowedValue(g_MMirrorSATTLPolYPropertyName, g_SATTLPol_1);

         pAct = new CPropertyAction (this, &CMMirror::OnSAPatternByteY);
         CreateProperty(g_MMirrorSAPatternModeYPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_MMirrorSAPatternModeYPropertyName);
         SetPropertyLimits(g_MMirrorSAPatternModeYPropertyName, 0, 255);
      }
   }
   return DEVICE_OK;
}

int CMMirror::OnSAAmplitudeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAA " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals()/unitMultX_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAA " << axisLetterX_ << "=" << tmp*unitMultX_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAOffsetX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAO " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals()/unitMultX_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAO " << axisLetterX_ << "=" << tmp*unitMultX_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPeriodX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAF " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAF " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAModeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   static bool justSet = false;
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !justSet)
         return DEVICE_OK;
      command << "SAM " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAMode_0); break;
         case 1: success = pProp->Set(g_SAMode_1); break;
         case 2: success = pProp->Set(g_SAMode_2); break;
         case 3: success = pProp->Set(g_SAMode_3); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
      justSet = false;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAMode_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAMode_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_SAMode_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_SAMode_3) == 0)
         tmp = 3;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "SAM " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      // get the updated value right away
      justSet = true;
      return OnSAModeX(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPatternX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT2|BIT1|BIT0));  // zero all but the lowest 3 bits
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAPattern_0); break;
         case 1: success = pProp->Set(g_SAPattern_1); break;
         case 2: success = pProp->Set(g_SAPattern_2); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAPattern_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAPattern_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_SAPattern_2) == 0)
         tmp = 2;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bits 0-2 from there
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT2|BIT1|BIT0));  // set lowest 3 bits to zero
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAAmplitudeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAA " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals()/unitMultX_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAA " << axisLetterY_ << "=" << tmp*unitMultY_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAOffsetY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAO " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = hub_->ParseAnswerAfterEquals()/unitMultX_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAO " << axisLetterY_ << "=" << tmp*unitMultY_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPeriodY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAF " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAF " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAModeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   static bool justSet = false;
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !justSet)
         return DEVICE_OK;
      command << "SAM " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAMode_0); break;
         case 1: success = pProp->Set(g_SAMode_1); break;
         case 2: success = pProp->Set(g_SAMode_2); break;
         case 3: success = pProp->Set(g_SAMode_3); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
      justSet = false;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAMode_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAMode_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_SAMode_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_SAMode_3) == 0)
         tmp = 3;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "SAM " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      // get the updated value right away
      justSet = true;
      return OnSAModeY(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPatternY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT2|BIT1|BIT0));  // zero all but the lowest 3 bits
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAPattern_0); break;
         case 1: success = pProp->Set(g_SAPattern_1); break;
         case 2: success = pProp->Set(g_SAPattern_2); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAPattern_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAPattern_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_SAPattern_2) == 0)
         tmp = 2;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bits 0-2 from there
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT2|BIT1|BIT0));  // set lowest 3 bits to zero
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPatternByteX(MM::PropertyBase* pProp, MM::ActionType eAct)
// get every single time
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAPatternByteY(MM::PropertyBase* pProp, MM::ActionType eAct)
// get every single time
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAClkSrcX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT7));  // zero all but bit 7
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAClkSrc_0); break;
         case BIT7: success = pProp->Set(g_SAClkSrc_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAClkSrc_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAClkSrc_1) == 0)
         tmp = BIT7;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 7 from there
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT7));  // clear bit 7
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAClkSrcY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT7));  // zero all but bit 7
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAClkSrc_0); break;
         case BIT7: success = pProp->Set(g_SAClkSrc_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAClkSrc_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAClkSrc_1) == 0)
         tmp = BIT7;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 7 from there
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT7));  // clear bit 7
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAClkPolX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT6));  // zero all but bit 6
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAClkPol_0); break;
         case BIT6: success = pProp->Set(g_SAClkPol_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAClkPol_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAClkPol_1) == 0)
         tmp = BIT6;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 6 from there
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT6));  // clear bit 6
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSAClkPolY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT6));  // zero all but bit 6
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAClkPol_0); break;
         case BIT6: success = pProp->Set(g_SAClkPol_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SAClkPol_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SAClkPol_1) == 0)
         tmp = BIT6;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 6 from there
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT6));  // clear bit 6
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSATTLOutX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT5));  // zero all but bit 5
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SATTLOut_0); break;
         case BIT5: success = pProp->Set(g_SATTLOut_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SATTLOut_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SATTLOut_1) == 0)
         tmp = BIT5;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 5 from there
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT5));  // clear bit 5
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSATTLOutY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT5));  // zero all but bit 5
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SATTLOut_0); break;
         case BIT5: success = pProp->Set(g_SATTLOut_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SATTLOut_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SATTLOut_1) == 0)
         tmp = BIT5;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 5 from there
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT5));  // clear bit 5
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSATTLPolX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT4));  // zero all but bit 4
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SATTLPol_0); break;
         case BIT4: success = pProp->Set(g_SATTLPol_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SATTLPol_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SATTLPol_1) == 0)
         tmp = BIT4;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 4 from there
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT4));  // clear bit 4
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnSATTLPolY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      tmp = (long) hub_->ParseAnswerAfterEquals();
      bool success;
      tmp = tmp & ((long)(BIT4));  // zero all but bit 4
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SATTLPol_0); break;
         case BIT4: success = pProp->Set(g_SATTLPol_1); break;
         default:success = 0;                      break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SATTLPol_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_SATTLPol_1) == 0)
         tmp = BIT4;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bit 4 from there
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current = (long) hub_->ParseAnswerAfterEquals();
      current = current & (~(long)(BIT4));  // clear bit 4
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}


int CMMirror::OnJoystickFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
// todo fix firmware so joystick speed works
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

int CMMirror::OnJoystickSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
// todo fix firmware so joystick speed works
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

int CMMirror::OnJoystickMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
// todo fix firmware so joystick speed works
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

int CMMirror::OnJoystickSelectX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "J " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
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
      command << "J " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CMMirror::OnJoystickSelectY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "J " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
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
      command << "J " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

