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


#include "ASIPiezo.h"
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

// Shared properties not implemented for piezo because as of mid-2017 any piezo
//   occupies an entire card and so never would have another device sharing the same card.
//   Exception is save settings b/c focus device could be on the same card but they won't share any properties.

///////////////////////////////////////////////////////////////////////////////
// CPiezo
//
CPiezo::CPiezo(const char* name) :
   ASIPeripheralBase< ::CStageBase, CPiezo >(name),
   unitMult_(g_StageDefaultUnitMult),  // later will try to read actual setting
   stepSizeUm_(g_StageMinStepSize),    // we'll use 1 nm as our smallest possible step size, this is somewhat arbitrary and doesn't change during the program
   axisLetter_(g_EmptyAxisLetterStr),  // value determined by extended name
   ring_buffer_supported_(false),
   ring_buffer_capacity_(0),
   ttl_trigger_supported_(false),
   ttl_trigger_enabled_(false)
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
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // read the unit multiplier
   // ASI's unit multiplier is how many units per mm, so divide by 1000 here to get units per micron
   // we store the micron-based unit multiplier for MM use, not the mm-based one ASI uses
   ostringstream command;
   command.str("");
   double tmp;
   command << "UM " << axisLetter_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   unitMult_ = tmp/1000;
   command.str("");

   // set controller card to return positions with 2 decimal places (3 is max allowed currently, 2 gives 1nm resolution)
   command.str("");
   command << addressChar_ << "VB Z=2";
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
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZJoystick);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // upper and lower limits (SU and SL)
   pAct = new CPropertyAction (this, &CPiezo::OnLowerLim);
   CreateProperty(g_LowerLimPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_LowerLimPropertyName);
   pAct = new CPropertyAction (this, &CPiezo::OnUpperLim);
   CreateProperty(g_UpperLimPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_UpperLimPropertyName);

   // remove for now because of bug in PZINFO, will replace by RDADC command later (Jon 23-Oct-13)
//   // high voltage reading for diagnostics
//   command.str("");
//   command << addressChar_ << "PZINFO";
//   RETURN_ON_MM_ERROR( hub_->QueryCommand(command.str()));
//   vector<string> vReply = hub_->SplitAnswerOnCR();
//   hub_->SetLastSerialAnswer(vReply[2]);  // 3rd line has the HV info
//   command.str("");
//   command << hub_->ParseAnswerAfterColon();
//   CreateProperty(g_CardVoltagePropertyName, command.str().c_str(), MM::Float, true);
//   UpdateProperty(g_CardVoltagePropertyName);

   // piezo range
   command.str("");
   command << "PR " << axisLetter_ << "?";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   command.str("");
   long piezorange;
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(piezorange) );
   switch (piezorange)
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
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_0);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_1);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_2);
   AddAllowedValue(g_PiezoModePropertyName, g_AdeptMode_3);

   // Motor enable/disable (MC)
   pAct = new CPropertyAction (this, &CPiezo::OnMotorControl);
   CreateProperty(g_MotorControlPropertyName, g_OnState, MM::String, false, pAct);
   AddAllowedValue(g_MotorControlPropertyName, g_OnState);
   AddAllowedValue(g_MotorControlPropertyName, g_OffState);
   UpdateProperty(g_MotorControlPropertyName);

   // joystick fast speed (JS X=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickFastSpeed);
   CreateProperty(g_JoystickFastSpeedPropertyName, "100", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickFastSpeedPropertyName, 0.1, 100);
   UpdateProperty(g_JoystickFastSpeedPropertyName);

   // joystick slow speed (JS Y=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickSlowSpeed);
   CreateProperty(g_JoystickSlowSpeedPropertyName, "10", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickSlowSpeedPropertyName, 0.1, 100);
   UpdateProperty(g_JoystickSlowSpeedPropertyName);

   // joystick mirror (changes joystick fast/slow speeds to negative) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickMirror);
   CreateProperty(g_JoystickMirrorPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_NoState);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_YesState);
   UpdateProperty(g_JoystickMirrorPropertyName);

   // select which joystick or wheel is attached
   pAct = new CPropertyAction (this, &CPiezo::OnJoystickSelect);
   CreateProperty(g_JoystickSelectPropertyName, g_JSCode_0, MM::String, false, pAct);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_0);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_2);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_3);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_22);
   AddAllowedValue(g_JoystickSelectPropertyName, g_JSCode_23);
   UpdateProperty(g_JoystickSelectPropertyName);

   if (FirmwareVersionAtLeast(2.87))  // changed behavior of JS F and T as of v2.87
   {
      // fast wheel speed (JS F) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CPiezo::OnWheelFastSpeed);
      CreateProperty(g_WheelFastSpeedPropertyName, "10", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelFastSpeedPropertyName, 0, 1000);
      UpdateProperty(g_WheelFastSpeedPropertyName);

      // slow wheel speed (JS T) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CPiezo::OnWheelSlowSpeed);
      CreateProperty(g_WheelSlowSpeedPropertyName, "5", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelSlowSpeedPropertyName, 0, 100);
      UpdateProperty(g_WheelSlowSpeedPropertyName);

      // wheel mirror (changes wheel fast/slow speeds to negative) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CPiezo::OnWheelMirror);
      CreateProperty(g_WheelMirrorPropertyName, g_NoState, MM::String, false, pAct);
      AddAllowedValue(g_WheelMirrorPropertyName, g_NoState);
      AddAllowedValue(g_WheelMirrorPropertyName, g_YesState);
      UpdateProperty(g_WheelMirrorPropertyName);
   }

   // is negative towards sample (ASI firmware convention) or away from sample (Micro-manager convention)
   pAct = new CPropertyAction (this, &CPiezo::OnAxisPolarity);
   CreateProperty(g_AxisPolarity, g_FocusPolarityASIDefault, MM::String, false, pAct);
   AddAllowedValue(g_AxisPolarity, g_FocusPolarityASIDefault);
   AddAllowedValue(g_AxisPolarity, g_FocusPolarityMicroManagerDefault);


   // end now if we are pre-2.8 firmware
   if (!FirmwareVersionAtLeast(2.8))
   {
      initialized_ = true;
      return DEVICE_OK;
   }

   // everything below only supported in firmware 2.8 and prior
   // single-axis and SPIM function only supported in Micromanager with firmware 2.8 and above for simplicity

   // "do it" property to set home position
   pAct = new CPropertyAction (this, &CPiezo::OnSetHomeHere);
   CreateProperty(g_SetHomeHerePropertyName, g_IdleState, MM::String, false, pAct);
   AddAllowedValue(g_SetHomeHerePropertyName, g_IdleState, 0);
   AddAllowedValue(g_SetHomeHerePropertyName, g_DoItState, 1);
   AddAllowedValue(g_SetHomeHerePropertyName, g_DoneState, 2);
   UpdateProperty(g_SetHomeHerePropertyName);

   pAct = new CPropertyAction (this, &CPiezo::OnHomePosition);
   CreateProperty(g_HomePositionPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_HomePositionPropertyName);

   // "do it" property to go home removed with addition of Home() API call
   // will use API call in diSPIM plugin; anybody else using it contact author
   // if you really need the property to continue to exist
   //   pAct = new CPropertyAction (this, &CPiezo::OnMoveToHome);
   //   CreateProperty(g_MoveToHomePropertyName, g_IdleState, MM::String, false, pAct);
   //   AddAllowedValue(g_MoveToHomePropertyName, g_IdleState, 0);
   //   AddAllowedValue(g_MoveToHomePropertyName, g_DoItState, 1);
   //   AddAllowedValue(g_MoveToHomePropertyName, g_DoneState, 2);
   //   UpdateProperty(g_MoveToHomePropertyName);

   // "do it" property to run piezo calibration (not normally required)
   pAct = new CPropertyAction (this, &CPiezo::OnRunPiezoCalibration);
   CreateProperty(g_RunPiezoCalibrationPropertyName, g_IdleState, MM::String, false, pAct);
   AddAllowedValue(g_RunPiezoCalibrationPropertyName, g_IdleState, 0);
   AddAllowedValue(g_RunPiezoCalibrationPropertyName, g_DoItState, 1);
   AddAllowedValue(g_RunPiezoCalibrationPropertyName, g_DoneState, 2);
   UpdateProperty(g_RunPiezoCalibrationPropertyName);

   // get build info so we can add optional properties
   build_info_type build;
   RETURN_ON_MM_ERROR( hub_->GetBuildInfo(addressChar_, build) );

   // add SPIM properties if supported
   if (build.vAxesProps[0] & BIT4)
   {
      pAct = new CPropertyAction (this, &CPiezo::OnSPIMNumSlices);
      CreateProperty(g_SPIMNumSlicesPropertyName, "1", MM::Integer, false, pAct);
      UpdateProperty(g_SPIMNumSlicesPropertyName);

      pAct = new CPropertyAction (this, &CPiezo::OnSPIMState);
      CreateProperty(g_SPIMStatePropertyName, g_SPIMStateIdle, MM::String, false, pAct);
      AddAllowedValue(g_SPIMStatePropertyName, g_SPIMStateIdle);
      AddAllowedValue(g_SPIMStatePropertyName, g_SPIMStateArmed);
      UpdateProperty(g_SPIMStatePropertyName);
   }


   // add single-axis properties if supported
   // (single-axis support existed prior pre-2.8 firmware, but now we have easier way to tell if it's present using axis properties
   //   and it wasn't used very much before SPIM)
   if(build.vAxesProps[0] & BIT5)//      if(hub_->IsDefinePresent(build, g_Define_SINGLEAXIS_FUNCTION))
   {
      // copied from ASIMMirror.cpp
      pAct = new CPropertyAction (this, &CPiezo::OnSAAmplitude);
      CreateProperty(g_SAAmplitudePropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_SAAmplitudePropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnSAOffset);
      CreateProperty(g_SAOffsetPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_SAOffsetPropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnSAPeriod);
      CreateProperty(g_SAPeriodPropertyName, "0", MM::Integer, false, pAct);
      UpdateProperty(g_SAPeriodPropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnSAMode);
      CreateProperty(g_SAModePropertyName, g_SAMode_0, MM::String, false, pAct);
      AddAllowedValue(g_SAModePropertyName, g_SAMode_0);
      AddAllowedValue(g_SAModePropertyName, g_SAMode_1);
      AddAllowedValue(g_SAModePropertyName, g_SAMode_2);
      AddAllowedValue(g_SAModePropertyName, g_SAMode_3);
      UpdateProperty(g_SAModePropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnSAPattern);
      CreateProperty(g_SAPatternPropertyName, g_SAPattern_0, MM::String, false, pAct);
      AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_0);
      AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_1);
      AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_2);
	  if (FirmwareVersionAtLeast(3.14))
	   {	//sin pattern was implemeted much later atleast firmware 3/14 needed
		   AddAllowedValue(g_SAPatternPropertyName, g_SAPattern_3);
	   }
      UpdateProperty(g_SAPatternPropertyName);
      // generates a set of additional advanced properties that are rarely used
      pAct = new CPropertyAction (this, &CPiezo::OnSAAdvanced);
      CreateProperty(g_AdvancedSAPropertiesPropertyName, g_NoState, MM::String, false, pAct);
      AddAllowedValue(g_AdvancedSAPropertiesPropertyName, g_NoState);
      AddAllowedValue(g_AdvancedSAPropertiesPropertyName, g_YesState);
      UpdateProperty(g_AdvancedSAPropertiesPropertyName);
   }

   // add ring buffer properties if supported (starting version 2.81)
   if (FirmwareVersionAtLeast(2.81) && (build.vAxesProps[0] & BIT1))
   {
      // get the number of ring buffer positions from the BU X output
      string rb_define = hub_->GetDefineString(build, "RING BUFFER");

      ring_buffer_capacity_ = 0;
      if (rb_define.size() > 12)
      {
         ring_buffer_capacity_ = atol(rb_define.substr(11).c_str());
      }

      if (ring_buffer_capacity_ != 0)
      {
         ring_buffer_supported_ = true;

         pAct = new CPropertyAction (this, &CPiezo::OnRBMode);
         CreateProperty(g_RB_ModePropertyName, g_RB_OnePoint_1, MM::String, false, pAct);
         AddAllowedValue(g_RB_ModePropertyName, g_RB_OnePoint_1);
         AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayOnce_2);
         AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayRepeat_3);
         UpdateProperty(g_RB_ModePropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnRBDelayBetweenPoints);
         CreateProperty(g_RB_DelayPropertyName, "0", MM::Integer, false, pAct);
         UpdateProperty(g_RB_DelayPropertyName);

         // "do it" property to do TTL trigger via serial
         pAct = new CPropertyAction (this, &CPiezo::OnRBTrigger);
         CreateProperty(g_RB_TriggerPropertyName, g_IdleState, MM::String, false, pAct);
         AddAllowedValue(g_RB_TriggerPropertyName, g_IdleState, 0);
         AddAllowedValue(g_RB_TriggerPropertyName, g_DoItState, 1);
         AddAllowedValue(g_RB_TriggerPropertyName, g_DoneState, 2);
         UpdateProperty(g_RB_TriggerPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnRBRunning);
         CreateProperty(g_RB_AutoplayRunningPropertyName, g_NoState, MM::String, false, pAct);
         AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_NoState);
         AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_YesState);
         UpdateProperty(g_RB_AutoplayRunningPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnUseSequence);
         CreateProperty(g_UseSequencePropertyName, g_NoState, MM::String, false, pAct);
         AddAllowedValue(g_UseSequencePropertyName, g_NoState);
         AddAllowedValue(g_UseSequencePropertyName, g_YesState);
         ttl_trigger_enabled_ = false;
      }

   }

   if (FirmwareVersionAtLeast(3.09) && (hub_->IsDefinePresent(build, "IN0_INT"))
         && ring_buffer_supported_)
   {
      ttl_trigger_supported_ = true;
   }

   if (FirmwareVersionAtLeast(3.11))
   {
      // starting in firmware 2.8 there was a PM mode 4 which tried to drive the piezo
      // faster but required user tuning of overshoot and max time
      // PM mode 4 could not be set in MM due to a bug, so apparently nobody was using it ;-)
      // starting in firmware v3.11 the same functionality is present but moved to the MA command (MA mode 1)
      pAct = new CPropertyAction (this, &CPiezo::OnMaintainMode);
      CreateProperty(g_PiezoMaintainStatePropertyName, g_PiezoMaintain_0, MM::String, false, pAct);
      AddAllowedValue(g_PiezoMaintainStatePropertyName, g_PiezoMaintain_0);
      AddAllowedValue(g_PiezoMaintainStatePropertyName, g_PiezoMaintain_1);
      UpdateProperty(g_PiezoMaintainStatePropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnMaintainOneOvershoot);
      CreateProperty(g_PiezoMaintainOneOvershootPropertyName, "100", MM::Integer, false, pAct);
      SetPropertyLimits(g_PiezoMaintainOneOvershootPropertyName, 0, 400);
      UpdateProperty(g_PiezoMaintainOneOvershootPropertyName);
      pAct = new CPropertyAction (this, &CPiezo::OnMaintainOneMaxTime);
      CreateProperty(g_PiezoMaintainOneMaxTimePropertyName, "10", MM::Integer, false, pAct);
      SetPropertyLimits(g_PiezoMaintainOneMaxTimePropertyName, 0, 50);
      UpdateProperty(g_PiezoMaintainOneMaxTimePropertyName);

      // piezo auto sleep feature added in v3.11
      pAct = new CPropertyAction (this, &CPiezo::OnAutoSleepDelay);
      CreateProperty(g_AutoSleepDelayPropertyName, "5", MM::Integer, false, pAct);
      UpdateProperty(g_AutoSleepDelayPropertyName);
   }

      //VectorMove
   pAct = new CPropertyAction (this, &CPiezo::OnVector);
   CreateProperty(g_VectorPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_VectorPropertyName, -10,10); //hardcoded as -+10mm/sec , piezo r fast
   UpdateProperty(g_VectorPropertyName);

   initialized_ = true;
   return DEVICE_OK;
}

int CPiezo::GetPositionUm(double& pos)
{
   ostringstream command; command.str("");
   command << "W " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(pos) );
   pos = pos/unitMult_;
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
   double tmp;
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(tmp) );
   steps = (long)(tmp/unitMult_/stepSizeUm_);
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
   double tmp;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   min = tmp*1000;
   command.str("");
   command << "SU " << axisLetter_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   max = tmp*1000;
   return DEVICE_OK;
}

int CPiezo::Stop()
{
   // note this stops the card, \ stops all stages
   ostringstream command; command.str("");
   command << addressChar_ << "halt";
   return hub_->QueryCommand(command.str());
}

int CPiezo::Home()
{
   // turn off single-axis mode action if it's going on
   // firmware will turn it off anyway, but this way settings will be saved/restored
   char SAMode[MM::MaxStrLength];
   RETURN_ON_MM_ERROR ( GetProperty(g_SAModePropertyName, SAMode) );
   if (strcmp(SAMode, g_SAMode_0) != 0 )
   {
      SetProperty(g_SAModePropertyName, g_SAMode_0);
   }
   ostringstream command; command.str("");
   command << "! " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   return DEVICE_OK;
}

bool CPiezo::Busy()
{
   return false;
}

int CPiezo::SetOrigin()
{
   ostringstream command; command.str("");
   command << "H " << axisLetter_ << "=" << 0;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CPiezo::StopStageSequence()
// disables TTL triggering; doesn't actually stop anything already happening on controller
{
   ostringstream command; command.str("");
   if (!ttl_trigger_supported_)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   command << addressChar_ << "TTL X=0";  // switch off TTL triggering
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return DEVICE_OK;
}

int CPiezo::StartStageSequence()
// enables TTL triggering; doesn't actually start anything going on controller
{
   ostringstream command; command.str("");
   if (!ttl_trigger_supported_)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   // ensure that ringbuffer pointer points to first entry and
   // that we only trigger the first axis (assume only 1 axis on piezo card)
   command << addressChar_ << "RM Y=1 Z=0";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );

   command.str("");
   command << addressChar_ << "TTL X=1";  // switch on TTL triggering
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return DEVICE_OK;
}

int CPiezo::SendStageSequence()
{
   ostringstream command; command.str("");
   if (!ttl_trigger_supported_)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   command << addressChar_ << "RM X=0"; // clear ring buffer
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   for (unsigned i=0; i< sequence_.size(); i++)  // send new points
   {
      command.str("");
      command << "LD " << axisLetter_ << "=" << sequence_[i]*unitMult_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }

   return DEVICE_OK;
}

int CPiezo::ClearStageSequence()
{
   ostringstream command; command.str("");
   if (!ttl_trigger_supported_)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   sequence_.clear();
   command << addressChar_ << "RM X=0";  // clear ring buffer
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   return DEVICE_OK;
}

int CPiezo::AddToStageSequence(double position)
{
   if (!ttl_trigger_supported_)
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
   sequence_.push_back(position);
   return DEVICE_OK;
}


////////////////
// action handlers

int CPiezo::OnSaveJoystickSettings()
// redoes the joystick settings so they can be saved using SS Z
{
   long tmp;
   string tmpstr;
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   command << "J " << axisLetter_ << "?";
   response << ":A " << axisLetter_ << "=";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
   tmp += 100;
   command.str("");
   command << "J " << axisLetter_ << "=" << tmp;
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   return DEVICE_OK;
}

int CPiezo::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      if (hub_->UpdatingSharedProperties())
         return DEVICE_OK;
      pProp->Get(tmpstr);
      command << addressChar_ << "SS ";
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
      else if (tmpstr.compare(g_SaveSettingsZJoystick) == 0)
      {
         command << 'Z';
         // do save joystick settings first
         RETURN_ON_MM_ERROR (OnSaveJoystickSettings());
      }
      RETURN_ON_MM_ERROR (hub_->QueryCommandVerify(command.str(), ":A", (long)200));  // note 200ms delay added
      pProp->Set(g_SaveSettingsDone);
      command.str(""); command << g_SaveSettingsDone;
      RETURN_ON_MM_ERROR ( hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()) );
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS X?";
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
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = abs(tmp);
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
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "JS X?";  // query only the fast setting to see if already mirrored
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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

int CPiezo::OnWheelFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << addressChar_ << "JS F?";
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
      if (strcmp(wheelMirror, g_YesState) == 0)
         command << addressChar_ << "JS F=-" << tmp;
      else
         command << addressChar_ << "JS F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnWheelSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << addressChar_ << "JS T?";
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
      if (strcmp(wheelMirror, g_YesState) == 0)
         command << addressChar_ << "JS T=-" << tmp;
      else
         command << addressChar_ << "JS T=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnWheelMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << addressChar_ << "JS F?";  // query only the fast setting to see if already mirrored
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
      if (tmpstr.compare(g_YesState) == 0)
         command << addressChar_ << "JS F=-" << wheelFast << " T=-" << wheelSlow;
      else
         command << addressChar_ << "JS F=" << wheelFast << " T=" << wheelSlow;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnAxisPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      // change the unit mult that converts controller coordinates to micro-manager coordinates
      // micro-manager defines positive towards sample, ASI controllers just opposite
      if (tmpstr.compare(g_FocusPolarityMicroManagerDefault) == 0) {
         unitMult_ = -1*abs(unitMult_);
      } else {
         unitMult_ = abs(unitMult_);
      }
   }
   return DEVICE_OK;
}

int CPiezo::OnMaintainMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MA " << axisLetter_ << "?";
      ostringstream response; response.str(""); response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      switch (tmp)
      {
         case 0: success = pProp->Set(g_PiezoMaintain_0); break;
         case 1: success = pProp->Set(g_PiezoMaintain_1); break;
         default: success = 0;                            break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {

      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_PiezoMaintain_0) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_PiezoMaintain_1) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << addressChar_ << "MA " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CPiezo::OnMaintainOneOvershoot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "PZ T?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "T="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "PZ T=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnMaintainOneMaxTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "PZ R?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "R="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "PZ R=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnAutoSleepDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "PZ F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "F="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "PZ F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAAdvanced(MM::PropertyBase* pProp, MM::ActionType eAct)
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

         pAct = new CPropertyAction (this, &CPiezo::OnSAClkSrc);
         CreateProperty(g_SAClkSrcPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkSrcPropertyName, g_SAClkSrc_0);
         AddAllowedValue(g_SAClkSrcPropertyName, g_SAClkSrc_1);
         UpdateProperty(g_SAClkSrcPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnSAClkPol);
         CreateProperty(g_SAClkPolPropertyName, g_SAClkPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkPolPropertyName, g_SAClkPol_0);
         AddAllowedValue(g_SAClkPolPropertyName, g_SAClkPol_1);
         UpdateProperty(g_SAClkPolPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnSATTLOut);
         CreateProperty(g_SATTLOutPropertyName, g_SATTLOut_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLOutPropertyName, g_SATTLOut_0);
         AddAllowedValue(g_SATTLOutPropertyName, g_SATTLOut_1);
         UpdateProperty(g_SATTLOutPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnSATTLPol);
         CreateProperty(g_SATTLPolPropertyName, g_SATTLPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLPolPropertyName, g_SATTLPol_0);
         AddAllowedValue(g_SATTLPolPropertyName, g_SATTLPol_1);
         UpdateProperty(g_SATTLPolPropertyName);

         pAct = new CPropertyAction (this, &CPiezo::OnSAPatternByte);
         CreateProperty(g_SAPatternModePropertyName, "0", MM::Integer, false, pAct);
         SetPropertyLimits(g_SAPatternModePropertyName, 0, 255);
         UpdateProperty(g_SAPatternModePropertyName);
      }
   }
   return DEVICE_OK;
}

int CPiezo::OnSAAmplitude(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAA " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMult_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAA " << axisLetter_ << "=" << tmp*unitMult_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAO " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMult_;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAO " << axisLetter_ << "=" << tmp*unitMult_;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAPeriod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAF " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAF " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   static bool justSet = false;
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !justSet)
         return DEVICE_OK;
      command << "SAM " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
      command << "SAM " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      // get the updated value right away
      justSet = true;
      return OnSAMode(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CPiezo::OnSAPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success;
      tmp = tmp & ((long)(BIT2|BIT1|BIT0));  // zero all but the lowest 3 bits
      switch (tmp)
      {
         case 0: success = pProp->Set(g_SAPattern_0); break;
         case 1: success = pProp->Set(g_SAPattern_1); break;
         case 2: success = pProp->Set(g_SAPattern_2); break;
		 case 3: success = pProp->Set(g_SAPattern_3); break;
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
      else if (tmpstr.compare(g_SAPattern_3) == 0)
         tmp = 3;
	  else
         return DEVICE_INVALID_PROPERTY_VALUE;
      // have to get current settings and then modify bits 0-2 from there
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT2|BIT1|BIT0));  // set lowest 3 bits to zero
      tmp += current;
      command.str("");
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAPatternByte(MM::PropertyBase* pProp, MM::ActionType eAct)
// get every single time
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAClkSrc(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT7));  // clear bit 7
      tmp += current;
      command.str("");
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSAClkPol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT6));  // clear bit 6
      tmp += current;
      command.str("");
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSATTLOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT5));  // clear bit 5
      tmp += current;
      command.str("");
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSATTLPol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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
      command << "SAP " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT4));  // clear bit 4
      tmp += current;
      command.str("");
      command << "SAP " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSetHomeHere(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_IdleState);
   }
   else  if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_DoItState) == 0)
      {
         // old code used the controller's "+" ability but adapter needs to know the home position
         // so now implement in less efficient way that keeps OnHomePosition value up to date
         // one unfortunate side effect of this approach is that due to precision considerations
         //    the home position is limited to increments of 0.1um (probably sufficient)
         double homePos;
         ostringstream tmp; tmp.str("");
         RETURN_ON_MM_ERROR ( GetPositionUm(homePos) );
         tmp << homePos/1000;  // divide by 1000 b/c home position read out in mm
         RETURN_ON_MM_ERROR ( SetProperty(g_HomePositionPropertyName, tmp.str().c_str()) );
         pProp->Set(g_DoneState);
      }
   }
   return DEVICE_OK;
}

int CPiezo::OnHomePosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "HM " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "HM " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnRunPiezoCalibration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_IdleState);
   }
   else  if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_DoItState) == 0)
      {
         command << addressChar_ << "PZC";
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         pProp->Set(g_DoneState);
      }
   }
   return DEVICE_OK;
}

int CPiezo::OnSPIMNumSlices(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NR Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnSPIMState(MM::PropertyBase* pProp, MM::ActionType eAct)
// somewhat similar to same function for MicroMirror, but changed codes that are sent/compared like g_SPIMStateCode_Idle for g_PZSPIMStateCode_Idle
// see other marked differences
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "SN X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      bool success;
      char c;
      RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
      switch ( c )
      {
         case g_PZSPIMStateCode_Idle:  success = pProp->Set(g_SPIMStateIdle); break;
         case g_PZSPIMStateCode_Arm:   success = pProp->Set(g_SPIMStateArmed); break;  // about to be armed so report that
         case g_PZSPIMStateCode_Armed: success = pProp->Set(g_SPIMStateArmed); break;
         case g_PZSPIMStateCode_Stop:  success = pProp->Set(g_SPIMStateIdle); break;  // about to be idle so report that
         case g_PZSPIMStateCode_Timing:success = pProp->Set(g_SPIMStateArmed); break;  // this is part of being armed, caught it it middle of pulse
         default:                      success = false;                        break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      char c;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SPIMStateIdle) == 0)
      {
         // check status and stop if it's not idle already
         command << addressChar_ << "SN X?";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
         if (c!=g_SPIMStateCode_Idle)
         {
            command.str("");
            if(FirmwareVersionAtLeast(2.87))
            {
               command << addressChar_ << "SN X=" << (int)g_PZSPIMStateCode_Stop;
            }
            else  // older version
            {
               command << addressChar_ << "SN X=" << (int)g_PZSPIMStateCode_Idle;
            }
            RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         }
      }
      else if (tmpstr.compare(g_SPIMStateArmed) == 0)
      {
         // can arm directly for piezo even if it is already running
         command.str("");
         command << addressChar_ << "SN X=" << (int)g_PZSPIMStateCode_Arm;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      }
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CPiezo::OnRBMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   string pseudoAxisChar = FirmwareVersionAtLeast(2.89) ? "F" : "X";
   long tmp;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RM " << pseudoAxisChar << "?";
      response << ":A " << pseudoAxisChar << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()) );
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (tmp >= 128)
      {
         tmp -= 128;  // remove the "running now" code if present
      }
      bool success;
      switch ( tmp )
      {
         case 1: success = pProp->Set(g_RB_OnePoint_1); break;
         case 2: success = pProp->Set(g_RB_PlayOnce_2); break;
         case 3: success = pProp->Set(g_RB_PlayRepeat_3); break;
         default: success = false;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {

      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_RB_OnePoint_1) == 0)
         tmp = 1;
      else if (tmpstr.compare(g_RB_PlayOnce_2) == 0)
         tmp = 2;
      else if (tmpstr.compare(g_RB_PlayRepeat_3) == 0)
         tmp = 3;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      command << addressChar_ << "RM " << pseudoAxisChar << "=" << tmp;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CPiezo::OnRBTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_IdleState);
   }
   else  if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_DoItState) == 0)
      {
         command << addressChar_ << "RM";
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         pProp->Set(g_DoneState);
      }
   }
   return DEVICE_OK;
}

int CPiezo::OnRBRunning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   string pseudoAxisChar = FirmwareVersionAtLeast(2.89) ? "F" : "X";
   long tmp = 0;
   static bool justSet;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !justSet)
         return DEVICE_OK;
      command << addressChar_ << "RM " << pseudoAxisChar << "?";
      response << ":A " << pseudoAxisChar << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()) );
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      bool success;
      if (tmp >= 128)
      {
         success = pProp->Set(g_YesState);
      }
      else
      {
         success = pProp->Set(g_NoState);
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
      justSet = false;
   }
   else if (eAct == MM::AfterSet)
   {
      justSet = true;
      return OnRBRunning(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CPiezo::OnRBDelayBetweenPoints(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "RT Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CPiezo::OnUseSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (ttl_trigger_enabled_)
         pProp->Set(g_YesState);
      else
         pProp->Set(g_NoState);
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      ttl_trigger_enabled_ = (ttl_trigger_supported_ && (tmpstr.compare(g_YesState) == 0));
      return OnUseSequence(pProp, MM::BeforeGet);  // refresh value
   }
   return DEVICE_OK;
}

   int CPiezo::OnVector(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "VE " << axisLetter_ << "?";
      response << ":A " << axisLetter_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "VE " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}
