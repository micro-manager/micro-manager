///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIXYStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI XY Stage device adapter
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

#include "ASIXYStage.h"
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

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CXYStage
//
CXYStage::CXYStage(const char* name) :
   ASIPeripheralBase< ::CXYStageBase, CXYStage >(name),
   unitMultX_(g_StageDefaultUnitMult),  // later will try to read actual setting
   unitMultY_(g_StageDefaultUnitMult),  // later will try to read actual setting
   stepSizeXUm_(g_StageMinStepSize),    // we'll use 1 nm as our smallest possible step size, this is somewhat arbitrary
   stepSizeYUm_(g_StageMinStepSize),    //   and doesn't change during the program
   axisLetterX_(g_EmptyAxisLetterStr),    // value determined by extended name
   axisLetterY_(g_EmptyAxisLetterStr)     // value determined by extended name
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetterX_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterXPropertyName, axisLetterX_.c_str(), MM::String, true);
      axisLetterY_ = GetAxisLetterFromExtName(name,1);
      CreateProperty(g_AxisLetterYPropertyName, axisLetterY_.c_str(), MM::String, true);
   }
}

int CXYStage::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // read the unit multiplier for X and Y axes
   // ASI's unit multiplier is how many units per mm, so divide by 1000 here to get units per micron
   // we store the micron-based unit multiplier for MM use, not the mm-based one ASI uses
   ostringstream command;
   command.str("");
   double tmp;
   command << "UM " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   unitMultX_ = tmp/1000;
   command.str("");
   command << "UM " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   unitMultY_ = tmp/1000;

   // set controller card to return positions with 1 decimal places (3 is max allowed currently, 1 gives 10nm resolution)
   for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
   {  // handle case where logical device is split across cards
      command.str("");
      command << addressChar_[iii] << "VB Z=1";
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );
   }

   // expose the step size to user as read-only property (no need for action handler)
   command.str("");
   command << g_StageMinStepSize;
   CreateProperty(g_StepSizeXPropertyName , command.str().c_str(), MM::Float, true);
   CreateProperty(g_StepSizeYPropertyName , command.str().c_str(), MM::Float, true);

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_XYStageDeviceDescription << " Xaxis=" << axisLetterX_ << " Yaxis=" << axisLetterY_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // max motor speed - read only property
   command.str("");
   command << "S " << axisLetterX_ << "?";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   double origSpeed;
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(origSpeed) );
   ostringstream command2; command2.str("");
   command2 << "S " << axisLetterX_ << "=10000";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command2.str(), ":A")); // set too high
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));  // read actual max
   double maxSpeed;
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(maxSpeed) );
   command2.str("");
   command2 << "S " << axisLetterX_ << "=" << origSpeed;
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command2.str(), ":A")); // restore
   command2.str("");
   command2 << maxSpeed;
   CreateProperty(g_MaxMotorSpeedPropertyName, command2.str().c_str(), MM::Float, true);

   // now for properties that are read-write, mostly parameters that set aspects of stage behavior
   // our approach to parameters: read in value for X, if user changes it in MM then change for both X and Y
   // if user wants different ones for X and Y then he/she should set outside MM (using terminal program)
   //    and then not change in MM (and realize that Y isn't being shown by MM)
   // parameters exposed for user to set easily: SL, SU, PC, E, S, AC, WT, MA, JS X=, JS Y=, JS mirror
   // parameters maybe exposed with some hurdle to user: B, OS, AA, AZ, KP, KI, KD, AZ, CCA Y (in OnAdvancedProperties())

   CPropertyAction* pAct;

   // refresh properties from controller every time - default is not to refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CXYStage::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CXYStage::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZJoystick);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // Motor speed (S)
   pAct = new CPropertyAction (this, &CXYStage::OnSpeed);
   CreateProperty(g_MotorSpeedPropertyName, "1", MM::Float, false, pAct);
   SetPropertyLimits(g_MotorSpeedPropertyName, 0, maxSpeed);
   UpdateProperty(g_MotorSpeedPropertyName);

   // drift error (E)
   pAct = new CPropertyAction (this, &CXYStage::OnDriftError);
   CreateProperty(g_DriftErrorPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_DriftErrorPropertyName);

   // finish error (PC)
   pAct = new CPropertyAction (this, &CXYStage::OnFinishError);
   CreateProperty(g_FinishErrorPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_FinishErrorPropertyName);

   // acceleration (AC)
   pAct = new CPropertyAction (this, &CXYStage::OnAcceleration);
   CreateProperty(g_AccelerationPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_AccelerationPropertyName);

   // upper and lower limits (SU and SL)
   pAct = new CPropertyAction (this, &CXYStage::OnLowerLimX);
   CreateProperty(g_LowerLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_LowerLimXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnLowerLimY);
   CreateProperty(g_LowerLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_LowerLimYPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnUpperLimX);
   CreateProperty(g_UpperLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_UpperLimXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnUpperLimY);
   CreateProperty(g_UpperLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_UpperLimYPropertyName);

   // maintain behavior (MA)
   pAct = new CPropertyAction (this, &CXYStage::OnMaintainState);
   CreateProperty(g_MaintainStatePropertyName, g_StageMaintain_0, MM::String, false, pAct);
   AddAllowedValue(g_MaintainStatePropertyName, g_StageMaintain_0);
   AddAllowedValue(g_MaintainStatePropertyName, g_StageMaintain_1);
   AddAllowedValue(g_MaintainStatePropertyName, g_StageMaintain_2);
   AddAllowedValue(g_MaintainStatePropertyName, g_StageMaintain_3);
   UpdateProperty(g_MaintainStatePropertyName);

   // Wait time, default is 0 (WT)
   pAct = new CPropertyAction (this, &CXYStage::OnWaitTime);
   CreateProperty(g_StageWaitTimePropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_StageWaitTimePropertyName);

   // joystick fast speed (JS X=)
   pAct = new CPropertyAction (this, &CXYStage::OnJoystickFastSpeed);
   CreateProperty(g_JoystickFastSpeedPropertyName, "100", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickFastSpeedPropertyName, 0, 100);
   UpdateProperty(g_JoystickFastSpeedPropertyName);

   // joystick slow speed (JS Y=)
   pAct = new CPropertyAction (this, &CXYStage::OnJoystickSlowSpeed);
   CreateProperty(g_JoystickSlowSpeedPropertyName, "10", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickSlowSpeedPropertyName, 0, 100);
   UpdateProperty(g_JoystickSlowSpeedPropertyName);

   // joystick mirror (changes joystick fast/slow speeds to negative)
   pAct = new CPropertyAction (this, &CXYStage::OnJoystickMirror);
   CreateProperty(g_JoystickMirrorPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_NoState);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_YesState);
   UpdateProperty(g_JoystickMirrorPropertyName);

   // joystick rotate (interchanges X and Y axes, useful if camera is rotated
   pAct = new CPropertyAction (this, &CXYStage::OnJoystickRotate);
   CreateProperty(g_JoystickRotatePropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_JoystickRotatePropertyName, g_NoState);
   AddAllowedValue(g_JoystickRotatePropertyName, g_YesState);
   UpdateProperty(g_JoystickRotatePropertyName);

   // joystick enable/disable
   pAct = new CPropertyAction (this, &CXYStage::OnJoystickEnableDisable);
   CreateProperty(g_JoystickEnabledPropertyName, g_YesState, MM::String, false, pAct);
   AddAllowedValue(g_JoystickEnabledPropertyName, g_NoState);
   AddAllowedValue(g_JoystickEnabledPropertyName, g_YesState);
   UpdateProperty(g_JoystickEnabledPropertyName);

   if (firmwareVersion_ > 2.865)  // changed behavior of JS F and T as of v2.87
   {
      // fast wheel speed (JS F) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CXYStage::OnWheelFastSpeed);
      CreateProperty(g_WheelFastSpeedPropertyName, "10", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelFastSpeedPropertyName, 0, 100);
      UpdateProperty(g_WheelFastSpeedPropertyName);

      // slow wheel speed (JS T) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CXYStage::OnWheelSlowSpeed);
      CreateProperty(g_WheelSlowSpeedPropertyName, "5", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelSlowSpeedPropertyName, 0, 100);
      UpdateProperty(g_WheelSlowSpeedPropertyName);

      // wheel mirror (changes wheel fast/slow speeds to negative) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CXYStage::OnWheelMirror);
      CreateProperty(g_WheelMirrorPropertyName, g_NoState, MM::String, false, pAct);
      AddAllowedValue(g_WheelMirrorPropertyName, g_NoState);
      AddAllowedValue(g_WheelMirrorPropertyName, g_YesState);
      UpdateProperty(g_WheelMirrorPropertyName);
   }


   // generates a set of additional advanced properties that are rarely used
   pAct = new CPropertyAction (this, &CXYStage::OnAdvancedProperties);
   CreateProperty(g_AdvancedPropertiesPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_AdvancedPropertiesPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedPropertiesPropertyName, g_YesState);
   UpdateProperty(g_AdvancedPropertiesPropertyName);

   // invert axis by changing unitMult in Micro-manager's eyes (not actually on controller)
   pAct = new CPropertyAction (this, &CXYStage::OnAxisPolarityX);
   CreateProperty(g_AxisPolarityX, g_AxisPolarityNormal, MM::String, false, pAct);
   AddAllowedValue(g_AxisPolarityX, g_AxisPolarityReversed);
   AddAllowedValue(g_AxisPolarityX, g_AxisPolarityNormal);
   pAct = new CPropertyAction (this, &CXYStage::OnAxisPolarityY);
   CreateProperty(g_AxisPolarityY, g_AxisPolarityNormal, MM::String, false, pAct);
   AddAllowedValue(g_AxisPolarityY, g_AxisPolarityReversed);
   AddAllowedValue(g_AxisPolarityY, g_AxisPolarityNormal);

   initialized_ = true;
   return DEVICE_OK;
}

int CXYStage::GetPositionSteps(long& x, long& y)
{
   ostringstream command; command.str("");
   command << "W " << axisLetterX_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   double tmp;
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(tmp) );
   x = (long)(tmp/unitMultX_/stepSizeXUm_);
   command.str("");
   command << "W " << axisLetterY_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(tmp) );
   y = (long)(tmp/unitMultY_/stepSizeYUm_);
   return DEVICE_OK;
}

int CXYStage::SetPositionSteps(long x, long y)
{
   ostringstream command; command.str("");
   command << "M " << axisLetterX_ << "=" << x*unitMultX_*stepSizeXUm_ << " " << axisLetterY_ << "=" << y*unitMultY_*stepSizeYUm_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::SetRelativePositionSteps(long x, long y)
{
   ostringstream command; command.str("");
   command << "R " << axisLetterX_ << "=" << x*unitMultX_*stepSizeXUm_ << " " << axisLetterY_ << "=" << y*unitMultY_*stepSizeYUm_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   // limits are always represented in terms of mm, independent of unit multiplier
   ostringstream command; command.str("");
   command << "SL " << axisLetterX_ << "? ";
   double tmp;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   xMin = (long) (tmp*1000/stepSizeXUm_);
   command.str("");
   command << "SU " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
   xMax = (long) (tmp*1000/stepSizeXUm_);
   command.str("");
   command << "SL " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   yMin = (long) (tmp*1000/stepSizeYUm_);
   command.str("");
   command << "SU " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
   yMax = (long) (tmp*1000/stepSizeYUm_);
   return DEVICE_OK;
}

int CXYStage::Stop()
{
   // note this stops the card which usually is synonymous with the stage, \ stops all stages
   ostringstream command; command.str("");
   for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
   {  // handle case where logical device is split across cards
      command.str("");
      command << addressChar_[iii] << "HALT";
      RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );
   }
   return DEVICE_OK;
}

bool CXYStage::Busy()
{
   ostringstream command; command.str("");
   if (firmwareVersion_ > 2.7) // can use more accurate RS <axis>?
   {
      command << "RS " << axisLetterX_ << "?";
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      char c;
      if (hub_->GetAnswerCharAtPosition3(c) != DEVICE_OK)
         return false;
      if (c == 'B')
         return true;
      command.str("");
      command << "RS " << axisLetterY_ << "?";
      if (hub_->GetAnswerCharAtPosition3(c) != DEVICE_OK)
         return false;
      return (c == 'B');
   }
   else  // use LSB of the status byte as approximate status, not quite equivalent
   {
      command << "RS " << axisLetterX_;
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      unsigned int i;
      if (hub_->ParseAnswerAfterPosition2(i) != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      if (i & (unsigned int)BIT0)  // mask everything but LSB
         return true; // don't bother checking other axis
      command.str("");
      command << "RS " << axisLetterY_;
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      if (hub_->ParseAnswerAfterPosition2(i) != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      return (i & (unsigned int)BIT0);  // mask everything but LSB
   }
}

int CXYStage::SetOrigin()
{
   ostringstream command; command.str("");
   command << "H " << axisLetterX_ << "=" << 0 << " " << axisLetterY_ << "=" << 0;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::Home()
{
   ostringstream command; command.str("");
   command << "! " << axisLetterX_ << " " << axisLetterY_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::SetHome()
{
   if (firmwareVersion_ > 2.7) {
      ostringstream command; command.str("");
      command << "HM " << axisLetterX_ << "+" << " " << axisLetterY_ << "+";
      return hub_->QueryCommandVerify(command.str(),":A");
   }
   else
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
}


////////////////
// action handlers

int CXYStage::OnSaveJoystickSettings()
// redoes the joystick settings so they can be saved using SS Z
{
   long tmp;
   string tmpstr;
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   command << "J " << axisLetterX_ << "?";
   response << ":A " << axisLetterX_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   tmp += 100;
   command.str("");
   command << "J " << axisLetterX_ << "=" << tmp;
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   command.str("");
   response.str("");
   command << "J " << axisLetterY_ << "?";
   response << ":A " << axisLetterY_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   tmp += 100;
   command.str("");
   command << "J " << axisLetterY_ << "=" << tmp;
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   return DEVICE_OK;
}

int CXYStage::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      { // handle case where logical device is split across cards
         command.str("");
         command << addressChar_[iii] << "SS ";
         pProp->Get(tmpstr);
         if (tmpstr.compare(g_SaveSettingsOrig) == 0)
            return DEVICE_OK;
         if (tmpstr.compare(g_SaveSettingsDone) == 0)
            return DEVICE_OK;
         if (tmpstr.compare(g_SaveSettingsX) == 0)
            command << 'X';
         else if (tmpstr.compare(g_SaveSettingsY) == 0)
            command << 'X';
         else if (tmpstr.compare(g_SaveSettingsZ) == 0)
            command << 'Z';
         else if (tmpstr.compare(g_SaveSettingsZJoystick) == 0)
         {
            command << 'Z';
            // do save joystick settings first
            RETURN_ON_MM_ERROR (OnSaveJoystickSettings());
         }
         RETURN_ON_MM_ERROR (hub_->QueryCommandVerify(command.str(), ":A", (long)200));  // note added 200ms delay
      }
      pProp->Set(g_SaveSettingsDone);
   }
   return DEVICE_OK;
}

int CXYStage::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int CXYStage::OnAdvancedProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
// special property, when set to "yes" it creates a set of little-used properties that can be manipulated thereafter
// these parameters exposed with some hurdle to user: B, OS, AA, AZ, KP, KI, KD, AZ
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

         // Backlash (B)
         pAct = new CPropertyAction (this, &CXYStage::OnBacklash);
         CreateProperty(g_BacklashPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_BacklashPropertyName);

         // overshoot (OS)
         pAct = new CPropertyAction (this, &CXYStage::OnOvershoot);
         CreateProperty(g_OvershootPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_OvershootPropertyName);

         // servo integral term (KI)
         pAct = new CPropertyAction (this, &CXYStage::OnKIntegral);
         CreateProperty(g_KIntegralPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_KIntegralPropertyName);

         // servo proportional term (KP)
         pAct = new CPropertyAction (this, &CXYStage::OnKProportional);
         CreateProperty(g_KProportionalPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_KProportionalPropertyName);

         // servo derivative term (KD)
         pAct = new CPropertyAction (this, &CXYStage::OnKDerivative);
         CreateProperty(g_KDerivativePropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_KDerivativePropertyName);

         // Align calibration/setting for pot in drive electronics (AA)
         pAct = new CPropertyAction (this, &CXYStage::OnAAlign);
         CreateProperty(g_AAlignPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_AAlignPropertyName);

         // Autozero drive electronics (AZ)
         pAct = new CPropertyAction (this, &CXYStage::OnAZeroX);
         CreateProperty(g_AZeroXPropertyName, "0", MM::String, false, pAct);
         pAct = new CPropertyAction (this, &CXYStage::OnAZeroY);
         CreateProperty(g_AZeroYPropertyName, "0", MM::String, false, pAct);
         UpdateProperty(g_AZeroYPropertyName);

         // Motor enable/disable (MC)
         pAct = new CPropertyAction (this, &CXYStage::OnMotorControl);
         CreateProperty(g_MotorControlPropertyName, g_OnState, MM::String, false, pAct);
         AddAllowedValue(g_MotorControlPropertyName, g_OnState);
         AddAllowedValue(g_MotorControlPropertyName, g_OffState);
         UpdateProperty(g_MotorControlPropertyName);

         // number of extra move repetitions
         pAct = new CPropertyAction (this, &CXYStage::OnNrExtraMoveReps);
         CreateProperty(g_NrExtraMoveRepsPropertyName, "0", MM::Integer, false, pAct);
         SetPropertyLimits(g_NrExtraMoveRepsPropertyName, 0, 3);  // don't let the user set too high, though there is no actual limit
         UpdateProperty(g_NrExtraMoveRepsPropertyName);
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnWaitTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "WT " << axisLetterX_ << "?";
      response << ":" << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      pProp->Set(tmp);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "WT " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "S " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "S " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnDriftError(MM::PropertyBase* pProp, MM::ActionType eAct)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "E " << axisLetterX_ << "?";
      response << ":" << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "E " << axisLetterX_ << "=" << tmp/1000 << " " << axisLetterY_ << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnFinishError(MM::PropertyBase* pProp, MM::ActionType eAct)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "PC " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "PC " << axisLetterX_ << "=" << tmp/1000 << " " << axisLetterY_ << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnLowerLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CXYStage::OnLowerLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CXYStage::OnUpperLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnUpperLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "AC " << axisLetterX_ << "?";
      ostringstream response; response.str(""); response << ":" << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "AC " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnMaintainState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_StageMaintain_0); break;
         case 1: success = pProp->Set(g_StageMaintain_1); break;
         case 2: success = pProp->Set(g_StageMaintain_2); break;
         case 3: success = pProp->Set(g_StageMaintain_3); break;
         default: success = 0;                            break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_StageMaintain_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_StageMaintain_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_StageMaintain_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_StageMaintain_3) == 0)
         tmp = 3;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << "MA " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
// note ASI units are in millimeters but MM units are in micrometers
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "B " << axisLetterX_ << "=" << tmp/1000 << " " << axisLetterY_ << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnOvershoot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "OS " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "OS " << axisLetterX_ << "=" << tmp/1000 << " " << axisLetterY_ << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnKIntegral(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "KI " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "KI " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnKProportional(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "KP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "KP " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnKDerivative(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "KD " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "KD " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnAAlign(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "AA " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "AA " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnAZeroX(MM::PropertyBase* pProp, MM::ActionType eAct)
// on property change the AZ command is issued, and the reported result becomes the property value
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      command << "AZ " << axisLetterX_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      // last line has result, echo result to user as property
      vector<string> vReply = hub_->SplitAnswerOnCR();
      if (!pProp->Set(vReply.back().c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CXYStage::OnAZeroY(MM::PropertyBase* pProp, MM::ActionType eAct)
// on property change the AZ command is issued, and the reported result becomes the property value
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      command << "AZ " << axisLetterY_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      // last line has result, echo result to user as property
      vector<string> vReply = hub_->SplitAnswerOnCR();
      if (!pProp->Set(vReply.back().c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CXYStage::OnMotorControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MC " << axisLetterX_ << "?";
      response << ":A ";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterPosition3(tmp) );
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
         command << "MC " << axisLetterX_ << "-" << " " << axisLetterY_ << "-";
      else
         command << "MC " << axisLetterX_ << "+" << " " << axisLetterY_ << "+";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnJoystickFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS X?";  // if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = abs(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      char joystickMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, joystickMirror) );
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (strcmp(joystickMirror, g_YesState) == 0)
            command << addressChar_[iii] << "JS X=-" << tmp;
         else
            command << addressChar_[iii] << "JS X=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnJoystickSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS Y?";  // if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      char joystickMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, joystickMirror) );
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (strcmp(joystickMirror, g_YesState) == 0)
            command << addressChar_[iii] << "JS Y=-" << tmp;
         else
            command << addressChar_[iii] << "JS Y=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnJoystickMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS X?";  // query only the fast setting to see if already mirrored; if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (tmpstr.compare(g_YesState) == 0)
            command << addressChar_[iii] << "JS X=-" << joystickFast << " Y=-" << joystickSlow;
         else
            command << addressChar_[iii] << "JS X=" << joystickFast << " Y=" << joystickSlow;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnJoystickRotate(MM::PropertyBase* pProp, MM::ActionType eAct)
// interchanges axes for X and Y on the joystick
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "J " << axisLetterX_ << "?";  // only look at X axis for joystick
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      if (tmp == 3) // if set to be Y joystick direction then we are rotated, otherwise assume not rotated
         success = pProp->Set(g_YesState);
      else
         success = pProp->Set(g_NoState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      // ideally would call OnJoystickEnableDisable but don't know how to get the appropriate pProp
      string tmpstr;
      pProp->Get(tmpstr);
      char joystickEnabled[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickEnabledPropertyName, joystickEnabled) );
      if (strcmp(joystickEnabled, g_YesState) == 0)
      {
         if (tmpstr.compare(g_YesState) == 0)
            command << "J " << axisLetterX_ << "=3" << " " << axisLetterY_ << "=2";  // rotated
         else
            command << "J " << axisLetterX_ << "=2" << " " << axisLetterY_ << "=3";
      }
      else  // No = disabled
         command << "J " << axisLetterX_ << "=0" << " " << axisLetterY_ << "=0";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnJoystickEnableDisable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      if (tmp) // treat anything nozero as enabled when reading
         success = pProp->Set(g_YesState);
      else
         success = pProp->Set(g_NoState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
      {
         char joystickRotate[MM::MaxStrLength];
         RETURN_ON_MM_ERROR ( GetProperty(g_JoystickRotatePropertyName, joystickRotate) );
         if (strcmp(joystickRotate, g_YesState) == 0)
            command << "J " << axisLetterX_ << "=3" << " " << axisLetterY_ << "=2";  // rotated
         else
            command << "J " << axisLetterX_ << "=2" << " " << axisLetterY_ << "=3";
      }
      else  // No = disabled
         command << "J " << axisLetterX_ << "=0" << " " << axisLetterY_ << "=0";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnWheelFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS F?";  // if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = abs(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      char wheelMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_WheelMirrorPropertyName, wheelMirror) );
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (strcmp(wheelMirror, g_YesState) == 0)
            command << addressChar_[iii] << "JS F=-" << tmp;
         else
            command << addressChar_[iii] << "JS F=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnWheelSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS T?";  // if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A T="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = abs(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      char wheelMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, wheelMirror) );
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (strcmp(wheelMirror, g_YesState) == 0)
            command << addressChar_[iii] << "JS T=-" << tmp;
         else
            command << addressChar_[iii] << "JS T=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnWheelMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
// ASI controller mirrors by having negative speed, but here we have separate property for mirroring
//   and for speed (which is strictly positive)... that makes this code a bit odd
// note that this setting is per-card, not per-axis
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "JS F?";  // query only the fast setting to see if already mirrored; if device is split across cards, assume speed is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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
      double wheelFast = 0.0;
      RETURN_ON_MM_ERROR ( GetProperty(g_WheelFastSpeedPropertyName, wheelFast) );
      double wheelSlow = 0.0;
      RETURN_ON_MM_ERROR ( GetProperty(g_WheelSlowSpeedPropertyName, wheelSlow) );
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         if (tmpstr.compare(g_YesState) == 0)
            command << addressChar_[iii] << "JS F=-" << wheelFast << " T=-" << wheelSlow;
         else
            command << addressChar_[iii] << "JS F=" << wheelFast << " T=" << wheelSlow;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnNrExtraMoveReps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_[0] << "CCA Y?";  // if device is split across cards, assume setting is same
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      // don't complain if value is larger than MM's "artificial" limits, it just won't be set
      pProp->Set(tmp);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      for(std::string::size_type iii = 0; iii < addressChar_.size(); ++iii)
      {  // handle case where logical device is split across cards
         command.str("");
         command << addressChar_[iii] << "CCA Y=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnAxisPolarityX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_AxisPolarityReversed) == 0) {
         unitMultX_ = -1*abs(unitMultX_);
      } else {
         unitMultX_ = abs(unitMultX_);
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnAxisPolarityY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_AxisPolarityReversed) == 0) {
         unitMultY_ = -1*abs(unitMultY_);
      } else {
         unitMultY_ = abs(unitMultY_);
      }
   }
   return DEVICE_OK;
}


