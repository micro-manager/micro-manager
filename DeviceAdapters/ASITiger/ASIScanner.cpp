///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIScanner.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI scanner device adapter
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


#include "ASIScanner.h"
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
// CScanner
//
CScanner::CScanner(const char* name) :
   ASIPeripheralBase< ::CGalvoBase, CScanner >(name),
   axisLetterX_(g_EmptyAxisLetterStr),    // value determined by extended name
   axisLetterY_(g_EmptyAxisLetterStr),    // value determined by extended name
   unitMultX_(g_ScannerDefaultUnitMult),  // later will try to read actual setting
   unitMultY_(g_ScannerDefaultUnitMult),  // later will try to read actual setting
   upperLimitX_(0),   // later will try to read actual setting
   upperLimitY_(0),   // later will try to read actual setting
   lowerLimitX_(0),   // later will try to read actual setting
   lowerLimitY_(0),   // later will try to read actual setting
   shutterX_(0), // home position, used to turn beam off
   shutterY_(0), // home position, used to turn beam off
   lastX_(0),    // cached position before blanking, used for SetIlluminationState
   lastY_(0),    // cached position before blanking, used for SetIlluminationState
   illuminationState_(true),
   saStateX_(),
   saStateY_(),
   polygonRepetitions_(0),
   ring_buffer_supported_(false),
   laser_side_(0),   // will be set to 1 or 2 if used
   laserTTLenabled_(false),
   mmTarget_(false),
   targetExposure_(0),
   targetSettling_(5),
   axisIndexX_(0),
   axisIndexY_(1)
{

   // initialize these structs
   saStateX_.mode = -1;
   saStateX_.pattern = -1;
   saStateY_.mode = -1;
   saStateY_.pattern = -1;

   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetterX_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterXPropertyName, axisLetterX_.c_str(), MM::String, true);
      axisLetterY_ = GetAxisLetterFromExtName(name,1);
      CreateProperty(g_AxisLetterYPropertyName, axisLetterY_.c_str(), MM::String, true);
   }
}

int CScanner::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // read the unit multiplier for X and Y axes
   // ASI's unit multiplier is how many units per degree rotation for the micromirror card
   ostringstream command;
   command.str("");
   command << "UM " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(unitMultX_) );
   command.str("");
   command << "UM " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(unitMultY_) );

   // read the home position (used for beam shuttering)
   command.str("");
   command << "HM " << axisLetterX_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(shutterX_) );  // already in units of degrees

   command.str("");
   command << "HM " << axisLetterY_ << "? ";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(shutterY_) ); // already in units of degrees

   // set controller card to return positions with 1 decimal places (3 is max allowed currently, units are millidegrees)
   command.str("");
   command << addressChar_ << "VB Z=1";
   RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );  // special case, no :A returned

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_ScannerDeviceDescription << " Xaxis=" << axisLetterX_ << " Yaxis=" << axisLetterY_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // TODO put this HV info back in
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

   // refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CScanner::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CScanner::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZJoystick);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // upper and lower limits (SU and SL) (limits not as useful for micromirror as for stage but they work)
   pAct = new CPropertyAction (this, &CScanner::OnLowerLimX);
   CreateProperty(g_ScannerLowerLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerLowerLimXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnLowerLimY);
   CreateProperty(g_ScannerLowerLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerLowerLimYPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnUpperLimX);
   CreateProperty(g_ScannerUpperLimXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerUpperLimXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnUpperLimY);
   CreateProperty(g_ScannerUpperLimYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerUpperLimYPropertyName);

   // for older firmware fix the blanking position of 1000 degrees which is far beyond the limits
   // assume that the blanking position is the maximum limit
   // firmware 3.10+ this is fixed, but this code can still execute without problem
   if (shutterX_ > 100) {
      GetProperty(g_ScannerUpperLimXPropertyName, shutterX_);
   }
   if (shutterY_ > 100) {
      GetProperty(g_ScannerUpperLimYPropertyName, shutterY_);
   }

   // mode, currently just changes between internal and external input
   pAct = new CPropertyAction (this, &CScanner::OnMode);
   CreateProperty(g_ScannerInputModePropertyName, "0", MM::String, false, pAct);
   AddAllowedValue(g_ScannerInputModePropertyName, g_ScannerMode_internal);
   AddAllowedValue(g_ScannerInputModePropertyName, g_ScannerMode_external);
   UpdateProperty(g_ScannerInputModePropertyName);

   // filter cut-off frequency
   // decided to implement separately for X and Y axes so can have one fast and other slow
   pAct = new CPropertyAction (this, &CScanner::OnCutoffFreqX);
   CreateProperty(g_ScannerCutoffFilterXPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_ScannerCutoffFilterXPropertyName, 0.1, 650);
   UpdateProperty(g_ScannerCutoffFilterXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnCutoffFreqY);
   CreateProperty(g_ScannerCutoffFilterYPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_ScannerCutoffFilterYPropertyName, 0.1, 650);
   UpdateProperty(g_ScannerCutoffFilterYPropertyName);

   // attenuation factor for movement
   pAct = new CPropertyAction (this, &CScanner::OnAttenuateTravelX);
   CreateProperty(g_ScannerAttenuateXPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_ScannerAttenuateXPropertyName, 0, 1);
   UpdateProperty(g_ScannerAttenuateXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnAttenuateTravelY);
   CreateProperty(g_ScannerAttenuateYPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_ScannerAttenuateYPropertyName, 0, 1);
   UpdateProperty(g_ScannerAttenuateYPropertyName);

   // joystick fast speed (JS X=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CScanner::OnJoystickFastSpeed);
   CreateProperty(g_JoystickFastSpeedPropertyName, "100", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickFastSpeedPropertyName, 0, 100);
   UpdateProperty(g_JoystickFastSpeedPropertyName);

   // joystick slow speed (JS Y=) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CScanner::OnJoystickSlowSpeed);
   CreateProperty(g_JoystickSlowSpeedPropertyName, "10", MM::Float, false, pAct);
   SetPropertyLimits(g_JoystickSlowSpeedPropertyName, 0, 100);
   UpdateProperty(g_JoystickSlowSpeedPropertyName);

   // joystick mirror (changes joystick fast/slow speeds to negative) (per-card, not per-axis)
   pAct = new CPropertyAction (this, &CScanner::OnJoystickMirror);
   CreateProperty(g_JoystickMirrorPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_NoState);
   AddAllowedValue(g_JoystickMirrorPropertyName, g_YesState);
   UpdateProperty(g_JoystickMirrorPropertyName);

   // joystick disable and select which knob
   pAct = new CPropertyAction (this, &CScanner::OnJoystickSelectX);
   CreateProperty(g_JoystickSelectXPropertyName, g_JSCode_0, MM::String, false, pAct);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_0, 0);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_2, 2);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_3, 3);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_22, 22);
   AddAllowedValue(g_JoystickSelectXPropertyName, g_JSCode_23, 23);
   UpdateProperty(g_JoystickSelectXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnJoystickSelectY);
   CreateProperty(g_JoystickSelectYPropertyName, g_JSCode_0, MM::String, false, pAct);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_0, 0);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_2, 2);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_3, 3);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_22, 22);
   AddAllowedValue(g_JoystickSelectYPropertyName, g_JSCode_23, 23);
   UpdateProperty(g_JoystickSelectYPropertyName);

   if (FirmwareVersionAtLeast(2.87))  // changed behavior of JS F and T as of v2.87
   {
      // fast wheel speed (JS F) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CScanner::OnWheelFastSpeed);
      CreateProperty(g_WheelFastSpeedPropertyName, "10", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelFastSpeedPropertyName, 0, 100);
      UpdateProperty(g_WheelFastSpeedPropertyName);

      // slow wheel speed (JS T) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CScanner::OnWheelSlowSpeed);
      CreateProperty(g_WheelSlowSpeedPropertyName, "5", MM::Float, false, pAct);
      SetPropertyLimits(g_WheelSlowSpeedPropertyName, 0, 100);
      UpdateProperty(g_WheelSlowSpeedPropertyName);

      // wheel mirror (changes wheel fast/slow speeds to negative) (per-card, not per-axis)
      pAct = new CPropertyAction (this, &CScanner::OnWheelMirror);
      CreateProperty(g_WheelMirrorPropertyName, g_NoState, MM::String, false, pAct);
      AddAllowedValue(g_WheelMirrorPropertyName, g_NoState);
      AddAllowedValue(g_WheelMirrorPropertyName, g_YesState);
      UpdateProperty(g_WheelMirrorPropertyName);
   }

   if (FirmwareVersionAtLeast(2.83))  // added in v2.83
   {
      // scanner range
      command.str("");
      command << "PR " << axisLetterX_ << "?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      command.str("");
      long scannerrange;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(scannerrange) );
      switch (scannerrange)
      {
         case 5: command << "5"; break;
         case 6: command << "6"; break;
         case 10: command << "10"; break;
         case 8:
         default: command << "8"; break;
      }
      CreateProperty(g_ScannerTravelRangePropertyName, command.str().c_str(), MM::Integer, true);
      UpdateProperty(g_ScannerTravelRangePropertyName);
   }

   // single-axis mode settings
   // todo fix firmware TTL initialization problem where SAM p=2 triggers by itself 1st time
   pAct = new CPropertyAction (this, &CScanner::OnSAAmplitudeX);
   CreateProperty(g_ScannerSAAmplitudeXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerSAAmplitudeXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAOffsetX);
   CreateProperty(g_ScannerSAOffsetXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerSAOffsetXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAPeriodX);
   CreateProperty(g_SAPeriodXPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_SAPeriodXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAModeX);
   CreateProperty(g_SAModeXPropertyName, g_SAMode_0, MM::String, false, pAct);
   AddAllowedValue(g_SAModeXPropertyName, g_SAMode_0, 0);
   AddAllowedValue(g_SAModeXPropertyName, g_SAMode_1, 1);
   AddAllowedValue(g_SAModeXPropertyName, g_SAMode_2, 2);
   AddAllowedValue(g_SAModeXPropertyName, g_SAMode_3, 3);
   UpdateProperty(g_SAModeXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAPatternX);
   CreateProperty(g_SAPatternXPropertyName, g_SAPattern_0, MM::String, false, pAct);
   AddAllowedValue(g_SAPatternXPropertyName, g_SAPattern_0, 0);
   AddAllowedValue(g_SAPatternXPropertyName, g_SAPattern_1, 1);
   AddAllowedValue(g_SAPatternXPropertyName, g_SAPattern_2, 2);
   if (FirmwareVersionAtLeast(3.14))
	   {	//sin pattern was implemeted much later atleast firmware 3/14 needed
		   AddAllowedValue(g_SAPatternXPropertyName, g_SAPattern_3, 3);
	   }
   UpdateProperty(g_SAPatternXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAAmplitudeY);
   CreateProperty(g_ScannerSAAmplitudeYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerSAAmplitudeYPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAOffsetY);
   CreateProperty(g_ScannerSAOffsetYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_ScannerSAOffsetYPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAPeriodY);
   CreateProperty(g_SAPeriodYPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_SAPeriodYPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAModeY);
   CreateProperty(g_SAModeYPropertyName, g_SAMode_0, MM::String, false, pAct);
   AddAllowedValue(g_SAModeYPropertyName, g_SAMode_0, 0);
   AddAllowedValue(g_SAModeYPropertyName, g_SAMode_1, 1);
   AddAllowedValue(g_SAModeYPropertyName, g_SAMode_2, 2);
   AddAllowedValue(g_SAModeYPropertyName, g_SAMode_3, 3);
   UpdateProperty(g_SAModeYPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAPatternY);
   CreateProperty(g_SAPatternYPropertyName, g_SAPattern_0, MM::String, false, pAct);
   AddAllowedValue(g_SAPatternYPropertyName, g_SAPattern_0, 0);
   AddAllowedValue(g_SAPatternYPropertyName, g_SAPattern_1, 1);
   AddAllowedValue(g_SAPatternYPropertyName, g_SAPattern_2, 2);
   if (FirmwareVersionAtLeast(3.14))
	   {	//sin pattern was implemeted much later atleast firmware 3/14 needed
		   AddAllowedValue(g_SAPatternYPropertyName, g_SAPattern_3, 3);
	   }
   UpdateProperty(g_SAPatternYPropertyName);

   // generates a set of additional advanced properties that are rarely used
   pAct = new CPropertyAction (this, &CScanner::OnSAAdvancedX);
   CreateProperty(g_AdvancedSAPropertiesXPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_AdvancedSAPropertiesXPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedSAPropertiesXPropertyName, g_YesState);
   UpdateProperty(g_AdvancedSAPropertiesXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnSAAdvancedY);
   CreateProperty(g_AdvancedSAPropertiesYPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_AdvancedSAPropertiesYPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedSAPropertiesYPropertyName, g_YesState);
   UpdateProperty(g_AdvancedSAPropertiesYPropertyName);

   // invert axis by changing unitMult in Micro-manager's eyes (not actually on controller)
   pAct = new CPropertyAction (this, &CScanner::OnAxisPolarityX);
   CreateProperty(g_AxisPolarityX, g_AxisPolarityNormal, MM::String, false, pAct);
   AddAllowedValue(g_AxisPolarityX, g_AxisPolarityReversed);
   AddAllowedValue(g_AxisPolarityX, g_AxisPolarityNormal);
   pAct = new CPropertyAction (this, &CScanner::OnAxisPolarityY);
   CreateProperty(g_AxisPolarityY, g_AxisPolarityNormal, MM::String, false, pAct);
   AddAllowedValue(g_AxisPolarityY, g_AxisPolarityReversed);
   AddAllowedValue(g_AxisPolarityY, g_AxisPolarityNormal);

   // get build info so we can add optional properties
   build_info_type build;
   RETURN_ON_MM_ERROR( hub_->GetBuildInfo(addressChar_, build) );

   // add phototargeting (MM_TARGET) properties if supported
   if (build.vAxesProps[0] & BIT3)
   {
      mmTarget_ = true;

      pAct = new CPropertyAction (this, &CScanner::OnTargetExposureTime);
      CreateProperty(g_TargetExposureTimePropertyName, "0", MM::Integer, false, pAct);
      UpdateProperty(g_TargetExposureTimePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnTargetSettlingTime);
      CreateProperty(g_TargetSettlingTimePropertyName, "0", MM::Integer, false, pAct);
      UpdateProperty(g_TargetSettlingTimePropertyName);
   }

   // turn the beam on and off
   // need to do this after finding the correct value for mmTarget_
   // also after creating single-axis properties which we use when turning off beam
   pAct = new CPropertyAction (this, &CScanner::OnBeamEnabled);
   CreateProperty(g_ScannerBeamEnabledPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_ScannerBeamEnabledPropertyName, g_NoState);
   AddAllowedValue(g_ScannerBeamEnabledPropertyName, g_YesState);
   UpdateProperty(g_ScannerBeamEnabledPropertyName);
   UpdateIlluminationState();
   // always start with the beam off for safety
   SetProperty(g_ScannerBeamEnabledPropertyName, g_NoState);

   // everything below only supported in firmware 2.8 and newer

   // add SPIM properties if SPIM is supported
   if (build.vAxesProps[0] & BIT4)
   {
      pAct = new CPropertyAction (this, &CScanner::OnSPIMScansPerSlice);
      CreateProperty(g_SPIMNumScansPerSlicePropertyName, "1", MM::Integer, false, pAct);
      UpdateProperty(g_SPIMNumScansPerSlicePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMNumSlices);
      CreateProperty(g_SPIMNumSlicesPropertyName, "1", MM::Integer, false, pAct);
      UpdateProperty(g_SPIMNumSlicesPropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMNumRepeats);
      CreateProperty(g_SPIMNumRepeatsPropertyName, "1", MM::Integer, false, pAct);
      UpdateProperty(g_SPIMNumRepeatsPropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMNumSides);
      CreateProperty(g_SPIMNumSidesPropertyName, "1", MM::Integer, false, pAct);
      SetPropertyLimits(g_SPIMNumSidesPropertyName, 1, 2);
      UpdateProperty(g_SPIMNumSidesPropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMFirstSide);
      CreateProperty(g_SPIMFirstSidePropertyName, g_SPIMSideAFirst, MM::String, false, pAct);
      AddAllowedValue(g_SPIMFirstSidePropertyName, g_SPIMSideAFirst);
      AddAllowedValue(g_SPIMFirstSidePropertyName, g_SPIMSideBFirst);
      UpdateProperty(g_SPIMFirstSidePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMModeByte);
      CreateProperty(g_SPIMModePropertyName, "1", MM::Integer, false, pAct);
      UpdateProperty(g_SPIMModePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMDelayBeforeSide);
      CreateProperty(g_SPIMDelayBeforeSidePropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_SPIMDelayBeforeSidePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMDelayBeforeScan);
      // as of v2.85 this is delay before scan starts, previously was OnSPIMDelayBeforeSlice which included camera
      CreateProperty(g_SPIMDelayBeforeScanPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_SPIMDelayBeforeScanPropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnSPIMState);
      CreateProperty(g_SPIMStatePropertyName, g_SPIMStateIdle, MM::String, false, pAct);
      AddAllowedValue(g_SPIMStatePropertyName, g_SPIMStateIdle);
      AddAllowedValue(g_SPIMStatePropertyName, g_SPIMStateArmed);
      AddAllowedValue(g_SPIMStatePropertyName, g_SPIMStateRunning);
      UpdateProperty(g_SPIMStatePropertyName);

      if (FirmwareVersionAtLeast(2.84))
      {
         pAct = new CPropertyAction (this, &CScanner::OnSPIMDelayBeforeRepeat);
         CreateProperty(g_SPIMDelayBeforeRepeatPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMDelayBeforeRepeatPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSPIMDelayBeforeCamera);
         CreateProperty(g_SPIMDelayBeforeCameraPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMDelayBeforeCameraPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSPIMDelayBeforeLaser);
         CreateProperty(g_SPIMDelayBeforeLaserPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMDelayBeforeLaserPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSPIMCameraDuration);
         CreateProperty(g_SPIMCameraDurationPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMCameraDurationPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSPIMLaserDuration);
         CreateProperty(g_SPIMLaserDurationPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMLaserDurationPropertyName);

         if (!FirmwareVersionAtLeast(2.88))  // as of v2.88 this changed; property will be added later if define present
         {
            pAct = new CPropertyAction (this, &CScanner::OnLaserOutputMode);
            CreateProperty(g_LaserOutputModePropertyName, "0", MM::String, false, pAct);
            AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_0);
            AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_1);
            AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_2);
            UpdateProperty(g_LaserOutputModePropertyName);
         }
      }

      if (FirmwareVersionAtLeast(2.89))  // get 2.89 features
      {
         pAct = new CPropertyAction (this, &CScanner::OnSPIMScannerHomeDisable);
         CreateProperty(g_SPIMScannerHomeDisable, g_NoState, MM::String, false, pAct);
         AddAllowedValue(g_SPIMScannerHomeDisable, g_YesState);
         AddAllowedValue(g_SPIMScannerHomeDisable, g_NoState);
         UpdateProperty(g_SPIMScannerHomeDisable);

         pAct = new CPropertyAction (this, &CScanner::OnSPIMPiezoHomeDisable);
         CreateProperty(g_SPIMPiezoHomeDisable, "0", MM::String, false, pAct);
         AddAllowedValue(g_SPIMPiezoHomeDisable, g_YesState);
         AddAllowedValue(g_SPIMPiezoHomeDisable, g_NoState);
         UpdateProperty(g_SPIMPiezoHomeDisable);
      }

      if (FirmwareVersionAtLeast(3.01))  // in 3.01 added setting to allow multiple slices per piezo movement
      {
         pAct = new CPropertyAction (this, &CScanner::OnSPIMNumSlicesPerPiezo);
         CreateProperty(g_SPIMNumSlicesPerPiezoPropertyName, "1", MM::Integer, false, pAct);
         UpdateProperty(g_SPIMNumSlicesPerPiezoPropertyName);
      } else {
         // create read-only property if version is too old
         CreateProperty(g_SPIMNumSlicesPerPiezoPropertyName, "1", MM::Integer, true);
      }

      if (FirmwareVersionAtLeast(3.09)) {  // in 3.09 added bit 4 of SPIM mode for interleaved slices
         pAct = new CPropertyAction (this, &CScanner::OnSPIMInterleaveSidesEnable);
         CreateProperty(g_SPIMInterleaveSidesEnable, g_NoState, MM::String, false, pAct);
         AddAllowedValue(g_SPIMInterleaveSidesEnable, g_YesState);
         AddAllowedValue(g_SPIMInterleaveSidesEnable, g_NoState);
         UpdateProperty(g_SPIMInterleaveSidesEnable);
      }

      if (FirmwareVersionAtLeast(3.14)) {
         // in 3.14 added bit 5 of SPIM mode for alternate directions
         pAct = new CPropertyAction (this, &CScanner::OnSPIMAlternateDirectionsEnable);
         CreateProperty(g_SPIMAlternateDirectionsEnable, g_NoState, MM::String, false, pAct);
         AddAllowedValue(g_SPIMAlternateDirectionsEnable, g_YesState);
         AddAllowedValue(g_SPIMAlternateDirectionsEnable, g_NoState);
         UpdateProperty(g_SPIMAlternateDirectionsEnable);

         // before 3.14 the single axis period value of the axis (OnSAPeriodX) was used
         // now we have a dedicated property (card-specific, not axis-specific)
         pAct = new CPropertyAction (this, &CScanner::OnSPIMScanDuration);
         CreateProperty(g_SPIMScanDurationPropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_SPIMScanDurationPropertyName);
      }

   } // adding SPIM properties

   // add ring buffer properties if supported (starting 2.81)
   if ((FirmwareVersionAtLeast(2.81)) && (build.vAxesProps[0] & BIT1))
   {
      ring_buffer_supported_ = true;

      pAct = new CPropertyAction (this, &CScanner::OnRBMode);
      CreateProperty(g_RB_ModePropertyName, g_RB_OnePoint_1, MM::String, false, pAct);
      AddAllowedValue(g_RB_ModePropertyName, g_RB_OnePoint_1);
      AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayOnce_2);
      AddAllowedValue(g_RB_ModePropertyName, g_RB_PlayRepeat_3);
      UpdateProperty(g_RB_ModePropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnRBDelayBetweenPoints);
      CreateProperty(g_RB_DelayPropertyName, "0", MM::Integer, false, pAct);
      UpdateProperty(g_RB_DelayPropertyName);

      // "do it" property to do TTL trigger via serial
      pAct = new CPropertyAction (this, &CScanner::OnRBTrigger);
      CreateProperty(g_RB_TriggerPropertyName, g_IdleState, MM::String, false, pAct);
      AddAllowedValue(g_RB_TriggerPropertyName, g_IdleState, 0);
      AddAllowedValue(g_RB_TriggerPropertyName, g_DoItState, 1);
      AddAllowedValue(g_RB_TriggerPropertyName, g_DoneState, 2);
      UpdateProperty(g_RB_TriggerPropertyName);

      pAct = new CPropertyAction (this, &CScanner::OnRBRunning);
      CreateProperty(g_RB_AutoplayRunningPropertyName, g_NoState, MM::String, false, pAct);
      AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_NoState);
      AddAllowedValue(g_RB_AutoplayRunningPropertyName, g_YesState);
      UpdateProperty(g_RB_AutoplayRunningPropertyName);
   }

   if (FirmwareVersionAtLeast(2.88))  // 2.88+
   {
      // populate laser_side_ appropriately
      command.str("");
      command << "Z2B " << axisLetterX_ << "?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(axisIndexX_) );
      // pre-1.94 comm firmware with 2.88+ micro-mirror firmware doesn't give back equals
      //  => we assume comm/stage firmwares are updated together
      // 1.94+ comm firmware with pre-2.88 stage firmware reports back 0 for all axis indexes
      switch (axisIndexX_)
      {
         case 1:
         case 0: laser_side_ = 1; break;
         case 3:
         case 2: laser_side_ = 2; break;
      }
      axisIndexY_ = axisIndexX_ + 1;

      laserTTLenabled_ = hub_->IsDefinePresent(build, "MM_LASER_TTL");
      if (laserTTLenabled_)
      {
         pAct = new CPropertyAction (this, &CScanner::OnLaserOutputMode);
         CreateProperty(g_LaserOutputModePropertyName, "0", MM::String, false, pAct);
         AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_0);
         AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_1);
         AddAllowedValue(g_LaserOutputModePropertyName, g_SPIMLaserOutputMode_2);
         UpdateProperty(g_LaserOutputModePropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnLaserSwitchTime);
         CreateProperty(g_LaserSwitchTimePropertyName, "0", MM::Float, false, pAct);
         UpdateProperty(g_LaserSwitchTimePropertyName);

         // update the laser settings based on the beam on/off
         SetIlluminationStateHelper(illuminationState_);
      }
   }

      //Vector Move VE X=### Y=###
   pAct = new CPropertyAction (this, &CScanner::OnVectorX);
   CreateProperty(g_VectorXPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_VectorXPropertyName, -10, 10);//hardcoded as -+10mm/sec , can he higher 
   UpdateProperty(g_VectorXPropertyName);
   pAct = new CPropertyAction (this, &CScanner::OnVectorY);
   CreateProperty(g_VectorYPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_VectorYPropertyName, -10 , 10);//hardcoded as -+10mm/sec , can he higher 
   UpdateProperty(g_VectorYPropertyName);

   initialized_ = true;
   return DEVICE_OK;
}

bool CScanner::Busy()
{
//   ostringstream command; command.str("");
//   if (firmwareVersion_ > 2.7) // can use more accurate RS <axis>?
//   {
//      command << "RS " << axisLetterX_ << "?";
//      ret_ = hub_->QueryCommandVerify(command.str(),":A");
//      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
//         return false;
//      if (hub_->LastSerialAnswer().at(3) == 'B')
//         return true;
//      command.str("");
//      command << "RS " << axisLetterY_ << "?";
//      return (hub_->LastSerialAnswer().at(3) == 'B');
//   }
//   else  // use LSB of the status byte as approximate status, not quite equivalent
//   {
//      command << "RS " << axisLetterX_;
//      ret_ = hub_->QueryCommandVerify(command.str(),":A");
//      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
//         return false;
//      int i = (int) (hub_->ParseAnswerAfterPosition(2));
//      if (i & (int)BIT0)  // mask everything but LSB
//         return true; // don't bother checking other axis
//      command.str("");
//      command << "RS " << axisLetterY_;
//      ret_ = hub_->QueryCommandVerify(command.str(),":A");
//      if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
//         return false;
//      i = (int) (hub_->ParseAnswerAfterPosition(2));
//      return (i & (int)BIT0);  // mask everything but LSB
//   }
   return false;
}

int CScanner::SetPosition(double x, double y)
// will not change the position of an axis unless single-axis functions are inactive
// also will not change the position if the beam is turned off, but it will change the
// cached positions that are used when the beam is turned back on
{
   ostringstream command; command.str("");
   if (illuminationState_) {  // beam is turned on
      char SAMode[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_SAModeXPropertyName, SAMode) );
      bool xMovable = strcmp(SAMode, g_SAMode_0) == 0;
      RETURN_ON_MM_ERROR ( GetProperty(g_SAModeYPropertyName, SAMode) );
      bool yMovable = strcmp(SAMode, g_SAMode_0) == 0;
      if (xMovable && yMovable) {
         command << "M " << axisLetterX_ << "=" << x*unitMultX_ << " " << axisLetterY_ << "=" << y*unitMultY_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      } else if (xMovable && !yMovable) {
         command << "M " << axisLetterX_ << "=" << x*unitMultX_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      } else if (!xMovable && yMovable) {
         command << "M " << axisLetterY_ << "=" << y*unitMultY_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      }
   } else {  // beam is turned off
      // update the cached position to be the current commanded position
      // so that beam will go there when turned back on
      // except don't do anything if we are setting position to blanking position
      // this way we can update just one of the two cached positions by passing
      // the other axis as the corresponding blanking position
      if (double_cmp(x, shutterX_) != 0
            && double_cmp(x, upperLimitX_) < 0
            && double_cmp(x, lowerLimitX_) > 0) {
         lastX_ = x;
      }
      if (double_cmp(y, shutterY_) != 0
            && double_cmp(y, upperLimitY_) < 0
            && double_cmp(y, lowerLimitY_) > 0) {
         lastY_ = y;
      }
   }
   return DEVICE_OK;
}

int CScanner::GetPosition(double& x, double& y)
{
//   // read from card instead of using cached values directly, could be slight mismatch
   // TODO implement as single serial command for speed (know that X axis always before Y on card)
   ostringstream command; command.str("");
   command << "W " << axisLetterX_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(x) );
   x = x/unitMultX_;
   command.str("");
   command << "W " << axisLetterY_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(y) );
   y = y/unitMultY_;
   return DEVICE_OK;
}

void CScanner::UpdateIlluminationState()
{
   ostringstream command; command.str("");
   long tmp;
   if (mmTarget_) {
      // should consider having a dedicated property for TTL output state; for now just do this
      command << addressChar_ << "TTL Y?";
      hub_->QueryCommandVerify(command.str(), ":A Y=");
      hub_->ParseAnswerAfterEquals(tmp);
      illuminationState_ = (tmp == 1);
      return;
   }
   else
   {
      // no direct way to query the controller if we are in "home" position or not
      // here we make the assumption that if both axes are at upper limits we are at home
      if (FirmwareVersionAtLeast(2.8))  // require version 2.8 to do this
      {
         command << "RS " << axisLetterX_ << "-";
         if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // don't choke on comm error
            return;
         if (hub_->LastSerialAnswer().at(3) != 'U')
         {
            illuminationState_ = true;
            return;
         }
         command.str("");
         command << "RS " << axisLetterY_ << "-";
         if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // don't choke on comm error
            return;
         if (hub_->LastSerialAnswer().at(3) != 'U')
         {
            illuminationState_ = true;
            return;
         }
         // if we made it this far then both axes are at upper limits
         illuminationState_ = false;
         return;
      }
   }
}

int CScanner::SetIlluminationStateHelper(bool on)
// takes care of setting LED X appropriately, preserving existing setting for other scanner
{
   // don't do this if we have phototargeting firmware
   if (mmTarget_)
   {
      return DEVICE_OK;
   }
   ostringstream command; command.str("");
   long tmp;
   if (!FirmwareVersionAtLeast(2.88)) // doesn't work before 2.88
      return DEVICE_OK;
   if(!laserTTLenabled_)
      return DEVICE_OK;
   if (laser_side_ != 1 && laser_side_ != 2)
   {
      // should only get here if laser_side_ didn't get properly read somehow
      return DEVICE_OK;
   }
   // starting with firmware v3.11 we have way of setting laser state without querying
   // other scanner device on same card
   if (FirmwareVersionAtLeast(3.11))
   {
      command.str("");
      if (laser_side_ == 1)
      {

      }
      else
      {

      }
      command << addressChar_ << "LED ";
      if (laser_side_ == 1)
         command << "R";
      else
         command << "T";
      command << "=";
      if (on)
         command << "1";
      else
         command << "0";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   else
   {
      // need to know whether other scanner device is turned on => must query it
      // would be nice if there was some way this information could be stored in hub object
      // and cut down on serial communication
      command << addressChar_ << "LED X?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),"X=") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp &= 0x03;  // strip all but the two LSBs
      if (laser_side_ == 1)
      {
         if(on)
            tmp |= 0x01;
         else
            tmp &= ~0x01;
      }
      else
      {
         if(on)
            tmp |= 0x02;
         else
            tmp &= ~0x02;
      }

      command.str("");
      command << addressChar_ << "LED X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CScanner::SetIlluminationState(bool on)
{
   if (mmTarget_)
   {  // for phototargeting firmware
      // should consider having a dedicated property for TTL output state; for now just do this
      ostringstream command; command.str("");
      if (on && !illuminationState_)  // was off, turning on
      {
         illuminationState_ = true;
         command << addressChar_ << "TTL Y=1";
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      }
      else if (!on && illuminationState_) // was on, turning off
      {
         illuminationState_ = false;
         command << addressChar_ << "TTL Y=" << (FirmwareVersionAtLeast(3.12) ? 21 : 11);
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      }
      // if was off, turning off do nothing
      // if was on, turning on do nothing
   }
   else
   { // for standard micro-mirror firmware
      // we can't turn off beam but we can steer beam to corner where hopefully it is blocked internally
      // to reduce serial traffic we count on illuminationState_ being up to date
      // if user manually moves to home position then we won't know it
      // UpdateIlluminationState();  // don't do to reduce traffic
      if (on && !illuminationState_)  // was off, turning on
      {
         illuminationState_ = true;
         RETURN_ON_MM_ERROR ( SetIlluminationStateHelper(true) );
         return SetPosition(lastX_, lastY_);  // move to where it was when last turned off
      }
      else if (!on && illuminationState_) // was on, turning off
      {
         // stop any single-axis action happening first; should go to position before single-axis was started
         // firmware will stop single-axis actions anyway but this gives us the right position
         char SAModeX[MM::MaxStrLength];
         RETURN_ON_MM_ERROR ( GetProperty(g_SAModeXPropertyName, SAModeX) );
         if (strcmp(SAModeX, g_SAMode_0) != 0 )
         {
            SetProperty(g_SAModeXPropertyName, g_SAMode_0);
         }
         char SAModeY[MM::MaxStrLength];
         RETURN_ON_MM_ERROR ( GetProperty(g_SAModeYPropertyName, SAModeY) );
         if (strcmp(SAModeY, g_SAMode_0) != 0 )
         {
            SetProperty(g_SAModeYPropertyName, g_SAMode_0);
         }
         GetPosition(lastX_, lastY_);  // read and store pre-off position so we can undo
         illuminationState_ = false;
         ostringstream command; command.str("");
         command << "! " << axisLetterX_ << " " << axisLetterY_;
         RETURN_ON_MM_ERROR ( SetIlluminationStateHelper(false) );
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
         return DEVICE_OK;
      }
      // if was off, turning off do nothing
      // if was on, turning on do nothing
   }
   return DEVICE_OK;
}

int CScanner::AddPolygonVertex(int polygonIndex, double x, double y)
{
   if (polygons_.size() <  (unsigned) (1 + polygonIndex))
      polygons_.resize(polygonIndex + 1);
   polygons_[polygonIndex].first = x;
   polygons_[polygonIndex].second = y;
   return DEVICE_OK;
}

int CScanner::DeletePolygons()
{
   polygons_.clear();
   return DEVICE_OK;
}

int CScanner::LoadPolygons()
{
   if (ring_buffer_supported_)
   {
      ostringstream command; command.str("");
      command << addressChar_ << "RM X=0";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      for (int i=0; i< (int) polygons_.size(); ++i)
      {
         command.str("");
         command << "LD " << axisLetterX_ << "=" << polygons_[i].first*unitMultX_
               << " " << axisLetterY_ << "=" << polygons_[i].second*unitMultY_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
      if (!mmTarget_)
      {
         // make the last point the home/shutter position for non-target firmware
         command.str("");
         command << "LD " << axisLetterX_ << "=" << shutterX_*unitMultX_
               << " " << axisLetterY_ << "=" << shutterY_*unitMultY_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   else
   {
      // do nothing since device doesn't store polygons in HW
   }
   return DEVICE_OK;
}

int CScanner::SetPolygonRepetitions(int repetitions)
{
   polygonRepetitions_ = repetitions;
   return DEVICE_OK;
}

int CScanner::RunPolygons()
{
   if (ring_buffer_supported_)
   {
      RETURN_ON_MM_ERROR ( SetProperty(g_RB_ModePropertyName, g_RB_PlayOnce_2) );

      // essentially like SetIlluminationState(true) but no caching
      illuminationState_ = true;
      RETURN_ON_MM_ERROR ( SetIlluminationStateHelper(true) );

      // trigger ring buffer requested number of times, sleeping until done
      // TODO support specified # of repeats in firmware directly
      for (int j=0; j<polygonRepetitions_; ++j) {
         RETURN_ON_MM_ERROR ( SetProperty(g_RB_TriggerPropertyName, g_DoItState) );
         bool done = false;
         do {
            char propValue[MM::MaxStrLength];
            refreshOverride_ = true;
            // check flag to see if we are still playing
            // flag goes low as soon as last point is moved to, which we have as home/shutter
            // so will be briefly shuttered and then start up again
            RETURN_ON_MM_ERROR ( GetProperty(g_RB_AutoplayRunningPropertyName, propValue) );
            if (strcmp(propValue, g_YesState) == 0) {
               CDeviceUtils::SleepMs(100);
            } else {
               done = true;
            }
         } while (!done);
      }

      // essentially like SetIlluminationState(false) but no caching
      // we have already turned the beam off by setting last ring buffer location to home
      illuminationState_ = false;
      RETURN_ON_MM_ERROR ( SetIlluminationStateHelper(false) );
   }
   else
   {
      // no HW support via ring buffer => have to repeatedly call PointAndFire
      // ideally targetExposure_ will have been set by a call to SetSpotInterval before this
      // so that PointAndFire doesn't have to change/restore the exposure or "on" time
      for (int j=0; j<polygonRepetitions_; ++j)
         for (int i=0; i< (int) polygons_.size(); ++i)
            PointAndFire(polygons_[i].first,polygons_[i].second, targetExposure_*1000);
   }
   return DEVICE_OK;
}

int CScanner::GetChannel(char* channelName)
{
   ostringstream command; command.str("");
   command << "Axes_ " << axisLetterX_ << axisLetterY_;
   CDeviceUtils::CopyLimitedString(channelName, command.str().c_str());
   return DEVICE_OK;
}

int CScanner::RunSequence()
{
   if (ring_buffer_supported_)
   {
      // should consider ensuring that RM Y is set appropriately with axisIndexX_ and axisIndexY_

      // note that this simply sends a trigger, which will also turn it off if it's currently running
      RETURN_ON_MM_ERROR ( SetProperty(g_RB_TriggerPropertyName, g_DoItState) );
      return DEVICE_OK;
   }
   else
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
}

int CScanner::SetSpotInterval(double pulseInterval_us)
{
   // sets time between points in sequence (and also "on" time for PointAndFire)
   // it appears from SLM code in Projector plugin that this is used to set the actual "on" time
   // and that the interval will depend on hardware overhead time to switch positions
   // so we take the same general approach here: use the requested pulseInterval_us NOT
   // as an actual interval but as the on-time and set the hardware interval to include the on time
   // plus the time required to move to a new position (wait time plus a bit of overhead)
   long targetExposure = long (pulseInterval_us/1000 + 0.5);  // our instance variable gets updated in the property handler
   ostringstream command; command.str("");
   command << targetExposure;
   RETURN_ON_MM_ERROR ( SetProperty(g_TargetExposureTimePropertyName, command.str().c_str()) );
   long intervalMs = targetExposure_ + targetSettling_ + 3;  // 3 ms extra cushion, need 1-2 ms for busy signal to go low beyond wait time
   command.str("");
   command << intervalMs;
   RETURN_ON_MM_ERROR ( SetProperty(g_RB_DelayPropertyName, command.str().c_str()) );
   return DEVICE_OK;
}

int CScanner::PointAndFire(double x, double y, double time_us)
{
   long exposure_ms = (long)(time_us/1000 + 0.5);
   long orig_exposure = 0;
   bool changeExposure = false;
   ostringstream command; command.str("");
   if (mmTarget_)
   {  // we have phototargeting-specific firmware
      // change exposure if needed; will restore afterwards
      // decided to do this rather than change the exposure time as side effect
      // if decide to set exposure time every time could use Z parameter of AIJ
      // which would reduce serial communication time
      // but ProjectorPlugin uses same "Exposure Time" over and over it will only send AIJ
      if (exposure_ms != targetExposure_)
      {
         changeExposure = true;
         orig_exposure = targetExposure_;
         command << exposure_ms;
         SetProperty(g_TargetExposureTimePropertyName, command.str().c_str());
         command.str("");
      }
      // send the AIJ command to do the move and fire
      command << addressChar_ << "AIJ X=" << x*unitMultX_ << " Y=" << y*unitMultY_;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      // restore prior exposure time
      if (changeExposure)
      {
         command.str("");
         command << orig_exposure;
         SetProperty(g_TargetExposureTimePropertyName, command.str().c_str());
      }

   }
   else
   {
      // no hardware timing => have to do timing in software and use SetPosition() instead of AIJ command
      SetIlluminationState(false);
      SetPosition(x, y);
      SetIlluminationState(true);
      CDeviceUtils::SleepMs(exposure_ms);
      SetIlluminationState(false);
   }
   return DEVICE_OK;
}

////////////////
// action handlers

int CScanner::OnSaveJoystickSettings()
// redo the joystick settings so they can be saved using SS Z
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

int CScanner::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         command << 'X';
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
   }
   return DEVICE_OK;
}

int CScanner::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnLowerLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SL " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      lowerLimitX_ = tmp;  // already in units of degrees
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      refreshOverride_ = true;
      return OnLowerLimX(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CScanner::OnLowerLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SL " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      lowerLimitY_ = tmp;  // already in units of degrees
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      refreshOverride_ = true;
      return OnLowerLimY(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CScanner::OnUpperLimX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SU " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      upperLimitX_ = tmp;  // already in units of degrees
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      refreshOverride_ = true;
      return OnUpperLimX(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CScanner::OnUpperLimY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SU " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      upperLimitY_ = tmp;  // already in units of degrees
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      refreshOverride_ = true;
      return OnUpperLimY(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CScanner::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
// assume X axis's mode is for both, and then set mode for both axes together just like XYStage properties
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      ostringstream response; response.str("");
      if (FirmwareVersionAtLeast(2.7))
      {
         command << "PM " << axisLetterX_ << "?";
         response << axisLetterX_ << "=";
      }
      else
      {
         command << "MA " << axisLetterX_ << "?";
         response << ":A " << axisLetterX_ << "=";
      }
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      if (FirmwareVersionAtLeast(2.7))  // using PM command
      {
         switch (tmp)
         {
            case 0: success = pProp->Set(g_ScannerMode_internal); break;
            case 1: success = pProp->Set(g_ScannerMode_external); break;
            default: success = 0;                        break;
         }
      }
      else
      {
         switch (tmp)
         {
            case 0: success = pProp->Set(g_ScannerMode_external); break;
            case 1: success = pProp->Set(g_ScannerMode_internal); break;
            default: success = 0;                        break;
         }
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (FirmwareVersionAtLeast(2.7))  // using PM command
      {
         if (tmpstr.compare(g_ScannerMode_internal) == 0)
            tmp = 0;
         else if (tmpstr.compare(g_ScannerMode_external) == 0)
            tmp = 1;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
         command << "PM " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
      else
      {
         if (tmpstr.compare(g_ScannerMode_external) == 0)
            tmp = 0;
         else if (tmpstr.compare(g_ScannerMode_internal) == 0)
            tmp = 1;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
         command << "MA " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   return DEVICE_OK;
}

int CScanner::OnCutoffFreqX(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnCutoffFreqY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnAttenuateTravelX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnAttenuateTravelY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnBeamEnabled(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::BeforeGet)
   {
      bool success;
      // we assume our state variable is up to date
      if (illuminationState_)
         success = pProp->Set(g_YesState);
      else
         success = pProp->Set(g_NoState);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         SetIlluminationState(true);
      else
         SetIlluminationState(false);
   }
   return DEVICE_OK;
}

int CScanner::OnSAAdvancedX(MM::PropertyBase* pProp, MM::ActionType eAct)
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

         pAct = new CPropertyAction (this, &CScanner::OnSAClkSrcX);
         CreateProperty(g_SAClkSrcXPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkSrcXPropertyName, g_SAClkSrc_0);
         AddAllowedValue(g_SAClkSrcXPropertyName, g_SAClkSrc_1);
         UpdateProperty(g_SAClkSrcXPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSAClkPolX);
         CreateProperty(g_SAClkPolXPropertyName, g_SAClkPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkPolXPropertyName, g_SAClkPol_0);
         AddAllowedValue(g_SAClkPolXPropertyName, g_SAClkPol_1);
         UpdateProperty(g_SAClkPolXPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSATTLOutX);
         CreateProperty(g_SATTLOutXPropertyName, g_SATTLOut_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLOutXPropertyName, g_SATTLOut_0);
         AddAllowedValue(g_SATTLOutXPropertyName, g_SATTLOut_1);
         UpdateProperty(g_SATTLOutXPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSATTLPolX);
         CreateProperty(g_SATTLPolXPropertyName, g_SATTLPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLPolXPropertyName, g_SATTLPol_0);
         AddAllowedValue(g_SATTLPolXPropertyName, g_SATTLPol_1);
         UpdateProperty(g_SATTLPolXPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSAPatternByteX);
         CreateProperty(g_SAPatternModeXPropertyName, "0", MM::Integer, false, pAct);
         SetPropertyLimits(g_SAPatternModeXPropertyName, 0, 255);
         UpdateProperty(g_SAPatternModeXPropertyName);
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAAdvancedY(MM::PropertyBase* pProp, MM::ActionType eAct)
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

         pAct = new CPropertyAction (this, &CScanner::OnSAClkSrcY);
         CreateProperty(g_SAClkSrcYPropertyName, g_SAClkSrc_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkSrcYPropertyName, g_SAClkSrc_0);
         AddAllowedValue(g_SAClkSrcYPropertyName, g_SAClkSrc_1);
         UpdateProperty(g_SAClkSrcYPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSAClkPolY);
         CreateProperty(g_SAClkPolYPropertyName, g_SAClkPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SAClkPolYPropertyName, g_SAClkPol_0);
         AddAllowedValue(g_SAClkPolYPropertyName, g_SAClkPol_1);
         UpdateProperty(g_SAClkPolYPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSATTLOutY);
         CreateProperty(g_SATTLOutYPropertyName, g_SATTLOut_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLOutYPropertyName, g_SATTLOut_0);
         AddAllowedValue(g_SATTLOutYPropertyName, g_SATTLOut_1);
         UpdateProperty(g_SATTLOutYPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSATTLPolY);
         CreateProperty(g_SATTLPolYPropertyName, g_SATTLPol_0, MM::String, false, pAct);
         AddAllowedValue(g_SATTLPolYPropertyName, g_SATTLPol_0);
         AddAllowedValue(g_SATTLPolYPropertyName, g_SATTLPol_1);
         UpdateProperty(g_SATTLPolYPropertyName);

         pAct = new CPropertyAction (this, &CScanner::OnSAPatternByteY);
         CreateProperty(g_SAPatternModeYPropertyName, "0", MM::Integer, false, pAct);
         SetPropertyLimits(g_SAPatternModeYPropertyName, 0, 255);
         UpdateProperty(g_SAPatternModeYPropertyName);
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAAmplitudeX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMultX_;
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

int CScanner::OnSAOffsetX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMultX_;
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

int CScanner::OnSAPeriodX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnSAModeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SAM " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
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
      saStateX_.mode = tmp;
   }
   else if (eAct == MM::AfterSet) {
      if (!illuminationState_)  // don't do anything if beam is turned off
      {
         pProp->Set(g_SAMode_0);
         return DEVICE_OK;
      }
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SAModeXPropertyName, tmp) );
      if (saStateX_.mode != tmp) {
         // avoid unnecessary serial traffic
         // requires assuming that value is only changed using this function
         command << "SAM " << axisLetterX_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         saStateX_.mode = tmp;
         // get the updated value right away if it isn't just turning on/off
         if (tmp > 1) {
            refreshOverride_ = true;
            return OnSAModeX(pProp, MM::BeforeGet);
         }
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAPatternX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      saStateX_.pattern = tmp;
   }
   else if (eAct == MM::AfterSet) {
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SAPatternXPropertyName, tmp) );
      // avoid unnecessary serial traffic
      // requires assuming that value is only changed using this function
      if (saStateX_.pattern != tmp) {
         // have to get current settings and then modify bits 0-2 from there
         command << "SAP " << axisLetterX_ << "?";
         response << ":A " << axisLetterX_ << "=";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
         long current;
         RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
         current = current & (~(long)(BIT2|BIT1|BIT0));  // set lowest 3 bits to zero
         tmp += current;
         command.str("");
         command << "SAP " << axisLetterX_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         saStateX_.pattern = tmp;
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAAmplitudeY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMultX_;
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

int CScanner::OnSAOffsetY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp/unitMultX_;
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

int CScanner::OnSAPeriodY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnSAModeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "SAM " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
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
      saStateY_.mode = tmp;
   }
   else if (eAct == MM::AfterSet) {
      if (!illuminationState_)  // don't do anything if beam is turned off
      {
         pProp->Set(g_SAMode_0);
         return DEVICE_OK;
      }
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SAModeYPropertyName, tmp) );
      if (saStateY_.mode != tmp) {
         // avoid unnecessary serial traffic
         // requires assuming that value is only changed using this function
         command << "SAM " << axisLetterY_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         saStateY_.mode = tmp;
         // get the updated value right away if it isn't just turning on/off
         if (tmp > 1) {
            refreshOverride_ = true;
            return OnSAModeY(pProp, MM::BeforeGet);
         }
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAPatternY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      saStateY_.pattern = tmp;
   }
   else if (eAct == MM::AfterSet) {
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SAPatternYPropertyName, tmp) );
      // avoid unnecessary serial traffic
      // requires assuming that value is only changed using this function
      if (saStateY_.pattern != tmp) {
         // have to get current settings and then modify bits 0-2 from there
         command << "SAP " << axisLetterY_ << "?";
         response << ":A " << axisLetterY_ << "=";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
         long current;
         RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
         current = current & (~(long)(BIT2|BIT1|BIT0));  // set lowest 3 bits to zero
         tmp += current;
         command.str("");
         command << "SAP " << axisLetterY_ << "=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         saStateY_.pattern = tmp;
      }
   }
   return DEVICE_OK;
}

int CScanner::OnSAPatternByteX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
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

int CScanner::OnSAPatternByteY(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnSAClkSrcX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT7));  // clear bit 7
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSAClkSrcY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT7));  // clear bit 7
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSAClkPolX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT6));  // clear bit 6
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSAClkPolY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT6));  // clear bit 6
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSATTLOutX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT5));  // clear bit 5
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSATTLOutY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT5));  // clear bit 5
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSATTLPolX(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterX_ << "?";
      response << ":A " << axisLetterX_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT4));  // clear bit 4
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterX_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSATTLPolY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      command << "SAP " << axisLetterY_ << "?";
      response << ":A " << axisLetterY_ << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      long current;
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(current) );
      current = current & (~(long)(BIT4));  // clear bit 4
      tmp += current;
      command.str("");
      command << "SAP " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}


int CScanner::OnJoystickFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnJoystickSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnJoystickMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnJoystickSelectX(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnJoystickSelectY(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         default: success = 0;
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

int CScanner::OnWheelFastSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnWheelSlowSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnWheelMirror(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnAxisPolarityX(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnAxisPolarityY(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnSPIMScansPerSlice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NR X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMNumSlices(MM::PropertyBase* pProp, MM::ActionType eAct)
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
      if (hub_->UpdatingSharedProperties())
         return DEVICE_OK;
      pProp->Get(tmp);
      command << addressChar_ << "NR Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      command.str(""); command << tmp;
      RETURN_ON_MM_ERROR ( hub_->UpdateSharedProperties(addressChar_, pProp->GetName(), command.str()) );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMNumSlicesPerPiezo(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR R?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A R="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NR R=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMNumSides(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp &= (0x03);          // mask off all but the two LSBs
      if (tmp==3)   tmp = 2;  // 3 means two-sided but opposite side
      if (tmp==0)   tmp = 1;  // 0 means one-sided but opposite side
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      char FirstSideVal[MM::MaxStrLength];
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( GetProperty(g_SPIMFirstSidePropertyName, FirstSideVal) );
      if (strcmp(FirstSideVal, g_SPIMSideBFirst) == 0)
      {
         if (tmp==1)   tmp = 0;
         if (tmp==2)   tmp = 3;
      }
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xFC));  // preserve the upper 6 bits from before, change only the two LSBs
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMFirstSide(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp &= (0x03);          // mask off all but the two LSBs
      if (tmp==3 || tmp==0)  // if opposite side
      {
         success = pProp->Set(g_SPIMSideBFirst);
      }
      else
      {
         success = pProp->Set(g_SPIMSideAFirst);
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      long NumSides = 1;
      string tmpstr;
      pProp->Get(tmpstr);
      RETURN_ON_MM_ERROR ( GetProperty(g_SPIMNumSidesPropertyName, NumSides) );
      if (tmpstr.compare(g_SPIMSideAFirst) == 0)
      {
         tmp = NumSides;
      }
      else
      {
         if (NumSides==1)   tmp = 0;
         if (NumSides==2)   tmp = 3;
      }
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xFC));  // preserve the upper 6 bits from before, change only the two LSBs
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnLaserSwitchTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "LED Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "LED Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnLaserOutputMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;
   if (FirmwareVersionAtLeast(2.88))  // corresponding serial command changed to LED Z in v2.88
   {
      if (eAct == MM::BeforeGet)
      {
         if (!refreshProps_ && initialized_)
            return DEVICE_OK;
         command << addressChar_ << "LED Z?";
         if(!laserTTLenabled_)
            return DEVICE_UNSUPPORTED_COMMAND;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "Z="));
         RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
         tmp &= (0x07);    // only care about 3 LSBs
         switch (tmp)
         {
            case 0: success = pProp->Set(g_SPIMLaserOutputMode_0); break;
            case 1: success = pProp->Set(g_SPIMLaserOutputMode_1); break;
            case 2: success = pProp->Set(g_SPIMLaserOutputMode_2); break;
            default: success = 0;
         }
         if (!success)
            return DEVICE_INVALID_PROPERTY_VALUE;
      }
      else if (eAct == MM::AfterSet) {
         string tmpstr;
         pProp->Get(tmpstr);
         if (tmpstr.compare(g_SPIMLaserOutputMode_0) == 0)
            tmp = 0;
         else if (tmpstr.compare(g_SPIMLaserOutputMode_1) == 0)
            tmp = 1;
         else if (tmpstr.compare(g_SPIMLaserOutputMode_2) == 0)
            tmp = 2;
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
         command << addressChar_ << "LED Z?";
         long tmp2;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), "Z="));
         RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
         tmp += (tmp2 & (0xFC));  // preserve the upper 6 bits from prior setting
         command.str("");
         command << addressChar_ << "LED Z=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      }
   }
   else  // before v2.88
   {
      if (eAct == MM::BeforeGet)
            {
               if (!refreshProps_ && initialized_)
                  return DEVICE_OK;
               command << addressChar_ << "NR Z?";
               RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
               RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
               tmp = tmp >> 2;   // shift left to get bits 2 and 3 in position of two LSBs
               tmp &= (0x03);    // mask off all but what used to be bits 2 and 3; this mitigates uncertainty of whether 1 or 0 was shifted in
               switch (tmp)
               {
                  case 0: success = pProp->Set(g_SPIMLaserOutputMode_0); break;
                  case 1: success = pProp->Set(g_SPIMLaserOutputMode_1); break;
                  case 2: success = pProp->Set(g_SPIMLaserOutputMode_2); break;
                  default: success = 0;
               }
               if (!success)
                  return DEVICE_INVALID_PROPERTY_VALUE;
            }
            else if (eAct == MM::AfterSet) {
               string tmpstr;
               pProp->Get(tmpstr);
               if (tmpstr.compare(g_SPIMLaserOutputMode_0) == 0)
                  tmp = 0;
               else if (tmpstr.compare(g_SPIMLaserOutputMode_1) == 0)
                  tmp = 1;
               else if (tmpstr.compare(g_SPIMLaserOutputMode_2) == 0)
                  tmp = 2;
               else
                  return DEVICE_INVALID_PROPERTY_VALUE;
               tmp = tmp << 2;  // right shift to get the value to bits 2 and 3
               command << addressChar_ << "NR Z?";
               long tmp2;
               RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
               RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
               tmp += (tmp2 & (0xF3));  // preserve the upper 4 bits and the two LSBs from prior setting, add bits 2 and 3 in manually
               if (tmp == tmp2)
                  return DEVICE_OK;  // don't need to set value if it's already correct
               command.str("");
               command << addressChar_ << "NR Z=" << tmp;
               RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
            }
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMScannerHomeDisable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;

   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp >> 2;   // shift left to get bits 2 in position of LSB
      tmp &= (0x01);    // mask off all but what used to be bit 2
      switch (tmp)
      {
         // note that bit set high _disables_ the feature
         case 0: success = pProp->Set(g_NoState); break;
         case 1: success = pProp->Set(g_YesState); break;
         default: success = 0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_NoState) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_YesState) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      tmp = tmp << 2;  // right shift to get the value to bit 2
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xFB));  // keep bit 2 from tmp, all others use current setting
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMPiezoHomeDisable(MM::PropertyBase* pProp, MM::ActionType eAct)
// TODO have mode byte be cached and shared between cards so we don't need to query before setting
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;

   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp >> 3;   // shift left to get bits 3 in position of LSB
      tmp &= (0x01);    // mask off all but what used to be bit 3
      switch (tmp)
      {
         // note that bit set high _disables_ the feature
         case 0: success = pProp->Set(g_NoState); break;
         case 1: success = pProp->Set(g_YesState); break;
         default: success = 0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_NoState) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_YesState) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      tmp = tmp << 3;  // right shift to get the value to bit 3
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xF7));  // keep bit 3 from tmp, all others use current setting
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMInterleaveSidesEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;

   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp >> 4;   // shift left to get bits 4 in position of LSB
      tmp &= (0x01);    // mask off all but what used to be bit 4
      switch (tmp)
      {
         case 0: success = pProp->Set(g_NoState); break;
         case 1: success = pProp->Set(g_YesState); break;
         default: success = 0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_NoState) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_YesState) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      tmp = tmp << 4;  // right shift to get the value to bit 4
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xEF));  // keep bit 4 from tmp, all others use current setting
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMAlternateDirectionsEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   bool success;

   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = tmp >> 5;   // shift left to get bits 5 in position of LSB
      tmp &= (0x01);    // mask off all but what used to be bit 4
      switch (tmp)
      {
         case 0: success = pProp->Set(g_NoState); break;
         case 1: success = pProp->Set(g_YesState); break;
         default: success = 0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_NoState) == 0)
         tmp = 0;
      else if (tmpstr.compare(g_YesState) == 0)
         tmp = 1;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
      tmp = tmp << 5;  // right shift to get the value to bit 5
      command << addressChar_ << "NR Z?";
      long tmp2;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp2) );
      tmp += (tmp2 & (0xDF));  // keep bit 5 from tmp, all others use current setting
      if (tmp == tmp2)
         return DEVICE_OK;  // don't need to set value if it's already correct
      command.str("");
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMModeByte(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NR Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMNumRepeats(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NR F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMDelayBeforeScan(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMDelayBeforeSide(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMDelayBeforeRepeat(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Z="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMDelayBeforeCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV T?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A T="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV T=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMDelayBeforeLaser(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV R?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A R="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV R=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMScanDuration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "RT F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMLaserDuration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT R?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A R="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "RT R=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMCameraDuration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT T?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A T="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "RT T=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CScanner::OnSPIMState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         case g_SPIMStateCode_Idle:  success = pProp->Set(g_SPIMStateIdle); break;
         case g_SPIMStateCode_Arm:   success = pProp->Set(g_SPIMStateArmed); break; // about to be armed so go ahead and report that
         case g_SPIMStateCode_Armed: success = pProp->Set(g_SPIMStateArmed); break;
         case g_SPIMStateCode_Stop : success = pProp->Set(g_SPIMStateIdle); break;  // about to be idle so go ahead and report that
         default:                    success = pProp->Set(g_SPIMStateRunning); break;  // a bunch of different letter codes are possible while running
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
            // this will stop state machine if it's running, if we do SN without args we run the risk of it stopping itself before we send the next command
            // after we stop it, it will automatically go to idle state
            command.str("");
            command << addressChar_ << "SN X=" << (int)g_SPIMStateCode_Stop;
            RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         }
      }
      else if (tmpstr.compare(g_SPIMStateArmed) == 0)
      {
         // stop it if we need to, then change to armed state
         command << addressChar_ << "SN X?";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
         if (c!=g_SPIMStateCode_Idle)
         {
            // this will stop state machine if it's running, if we do SN without args we run the risk of it stopping itself (e.g. finishing) before we send the next command
            command.str("");
            command << addressChar_ << "SN X=" << (int)g_SPIMStateCode_Stop;
            RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         }
         // now change to armed state
         command.str("");
         command << addressChar_ << "SN X=" << (int)g_SPIMStateCode_Arm;
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      }
      else if (tmpstr.compare(g_SPIMStateRunning) == 0)
      {
         // check status and start if it's idle or armed
         command << addressChar_ << "SN X?";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
         if ((c==g_SPIMStateCode_Idle) || (c==g_SPIMStateCode_Armed))
         {
            // if we are idle or armed then start it
            // assume that nothing else could have started it since our query moments ago
            command.str("");
            command << addressChar_ << "SN";
            RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         }
      }
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CScanner::OnRBMode(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnRBTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnRBRunning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   string pseudoAxisChar = FirmwareVersionAtLeast(2.89) ? "F" : "X";
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
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
   }
   else if (eAct == MM::AfterSet)
   {
      refreshOverride_ = true;
      return OnRBRunning(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CScanner::OnRBDelayBetweenPoints(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CScanner::OnTargetExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "RT Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      targetExposure_ = tmp;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      // make sure we don't set it below 1 because the firmware can get confused
      if (tmp < 1L)
      {
         pProp->Set(1L);
         tmp = 1;
      }
      command << addressChar_ << "RT Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      targetExposure_ = tmp;
   }
   return DEVICE_OK;
}

int CScanner::OnTargetSettlingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
// same as CXYStage::OnWaitTime()
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
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      targetSettling_ = tmp;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "WT " << axisLetterX_ << "=" << tmp << " " << axisLetterY_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      targetSettling_ = tmp;
   }
   return DEVICE_OK;
}

   int CScanner::OnVectorGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "VE " << axisLetter << "?";
      response << ":A " << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "VE " << axisLetter << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );

   }
   return DEVICE_OK;
}
