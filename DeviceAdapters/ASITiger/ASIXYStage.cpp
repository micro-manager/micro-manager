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


// TODO faster busy check for typical case where axes are on same card by just querying card busy


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
   axisLetterY_(g_EmptyAxisLetterStr),    // value determined by extended name
   advancedPropsEnabled_(false),
   speedTruth_(false)
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
   command.str("");
   command << addressChar_ << "VB Z=1";
   RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );

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
   double maxSpeedX = getMaxSpeed(axisLetterX_);
   command.str("");
   command << maxSpeedX;
   CreateProperty(g_MaxMotorSpeedXPropertyName, command.str().c_str(), MM::Float, true);
   double maxSpeedY = getMaxSpeed(axisLetterY_);
   command.str("");
   command << maxSpeedY;
   CreateProperty(g_MaxMotorSpeedYPropertyName, command.str().c_str(), MM::Float, true);

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

   // Motor speed (S) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnSpeedX);
   CreateProperty(g_MotorSpeedXPropertyName, "1", MM::Float, false, pAct);
   SetPropertyLimits(g_MotorSpeedXPropertyName, 0, maxSpeedX);
   UpdateProperty(g_MotorSpeedXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnSpeedY);
   CreateProperty(g_MotorSpeedYPropertyName, "1", MM::Float, false, pAct);
   SetPropertyLimits(g_MotorSpeedYPropertyName, 0, maxSpeedY);
   UpdateProperty(g_MotorSpeedYPropertyName);

   // Backlash (B) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnBacklashX);
   CreateProperty(g_BacklashXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_BacklashXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnBacklashY);
   CreateProperty(g_BacklashYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_BacklashYPropertyName);

   // drift error (E) for both X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnDriftErrorX);
   CreateProperty(g_DriftErrorXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_DriftErrorXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnDriftErrorY);
   CreateProperty(g_DriftErrorYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_DriftErrorYPropertyName);

   // finish error (PC) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnFinishErrorX);
   CreateProperty(g_FinishErrorXPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_FinishErrorXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnFinishErrorY);
   CreateProperty(g_FinishErrorYPropertyName, "0", MM::Float, false, pAct);
   UpdateProperty(g_FinishErrorYPropertyName);

   // acceleration (AC) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnAccelerationX);
   CreateProperty(g_AccelerationXPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_AccelerationXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnAccelerationY);
   CreateProperty(g_AccelerationYPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_AccelerationYPropertyName);

   // upper and lower limits (SU and SL) for X and Y
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

   // maintain behavior (MA) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnMaintainStateX);
   CreateProperty(g_MaintainStateXPropertyName, g_StageMaintain_0, MM::String, false, pAct);
   AddAllowedValue(g_MaintainStateXPropertyName, g_StageMaintain_0);
   AddAllowedValue(g_MaintainStateXPropertyName, g_StageMaintain_1);
   AddAllowedValue(g_MaintainStateXPropertyName, g_StageMaintain_2);
   AddAllowedValue(g_MaintainStateXPropertyName, g_StageMaintain_3);
   UpdateProperty(g_MaintainStateXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnMaintainStateY);
   CreateProperty(g_MaintainStateYPropertyName, g_StageMaintain_0, MM::String, false, pAct);
   AddAllowedValue(g_MaintainStateYPropertyName, g_StageMaintain_0);
   AddAllowedValue(g_MaintainStateYPropertyName, g_StageMaintain_1);
   AddAllowedValue(g_MaintainStateYPropertyName, g_StageMaintain_2);
   AddAllowedValue(g_MaintainStateYPropertyName, g_StageMaintain_3);
   UpdateProperty(g_MaintainStateYPropertyName);

   // Motor enable/disable (MC) for X and Y
   pAct = new CPropertyAction (this, &CXYStage::OnMotorControlX);
   CreateProperty(g_MotorControlXPropertyName, g_OnState, MM::String, false, pAct);
   AddAllowedValue(g_MotorControlXPropertyName, g_OnState);
   AddAllowedValue(g_MotorControlXPropertyName, g_OffState);
   UpdateProperty(g_MotorControlPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnMotorControlY);
   CreateProperty(g_MotorControlYPropertyName, g_OnState, MM::String, false, pAct);
   AddAllowedValue(g_MotorControlYPropertyName, g_OnState);
   AddAllowedValue(g_MotorControlYPropertyName, g_OffState);
   UpdateProperty(g_MotorControlYPropertyName);

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

   if (FirmwareVersionAtLeast(2.87))  // changed behavior of JS F and T as of v2.87
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

   // get build info so we can add optional properties
   build_info_type build;
   RETURN_ON_MM_ERROR( hub_->GetBuildInfo(addressChar_, build) );
   speedTruth_ = hub_->IsDefinePresent(build, "SPEED TRUTH");

   // add SCAN properties if supported
   if (build.vAxesProps[0] & BIT2)
   {
      pAct = new CPropertyAction (this, &CXYStage::OnScanState);
      CreateProperty(g_ScanStatePropertyName, g_ScanStateIdle, MM::String, false, pAct);
      AddAllowedValue(g_ScanStatePropertyName, g_ScanStateIdle);
      AddAllowedValue(g_ScanStatePropertyName, g_ScanStateRunning);
      UpdateProperty(g_ScanStatePropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanFastAxis);
      CreateProperty(g_ScanFastAxisPropertyName, g_ScanAxisX, MM::String, false, pAct);
      AddAllowedValue(g_ScanFastAxisPropertyName, g_ScanAxisX);
      AddAllowedValue(g_ScanFastAxisPropertyName, g_ScanAxisY);
      UpdateProperty(g_ScanFastAxisPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanSlowAxis);
      CreateProperty(g_ScanSlowAxisPropertyName, g_ScanAxisX, MM::String, false, pAct);
      AddAllowedValue(g_ScanSlowAxisPropertyName, g_ScanAxisX);
      AddAllowedValue(g_ScanSlowAxisPropertyName, g_ScanAxisY);
      AddAllowedValue(g_ScanSlowAxisPropertyName, g_ScanAxisNull);
      UpdateProperty(g_ScanSlowAxisPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanPattern);
      CreateProperty(g_ScanPatternPropertyName, g_ScanPatternRaster, MM::String, false, pAct);
      AddAllowedValue(g_ScanPatternPropertyName, g_ScanPatternRaster);
      AddAllowedValue(g_ScanPatternPropertyName, g_ScanPatternSerpentine);
      UpdateProperty(g_ScanPatternPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanFastStartPosition);
      CreateProperty(g_ScanFastAxisStartPositionPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_ScanFastAxisStartPositionPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanFastStopPosition);
      CreateProperty(g_ScanFastAxisStopPositionPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_ScanFastAxisStopPositionPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanSlowStartPosition);
      CreateProperty(g_ScanSlowAxisStartPositionPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_ScanSlowAxisStartPositionPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanSlowStopPosition);
      CreateProperty(g_ScanSlowAxisStopPositionPropertyName, "0", MM::Float, false, pAct);
      UpdateProperty(g_ScanSlowAxisStopPositionPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanNumLines);
      CreateProperty(g_ScanNumLinesPropertyName, "1", MM::Integer, false, pAct);
      SetPropertyLimits(g_ScanNumLinesPropertyName, 1, 100);  // upper limit is arbitrary, have limits to enforce > 0
      UpdateProperty(g_ScanNumLinesPropertyName);

      pAct = new CPropertyAction (this, &CXYStage::OnScanSettlingTime);
      CreateProperty(g_ScanSettlingTimePropertyName, "1", MM::Float, false, pAct);
      SetPropertyLimits(g_ScanSettlingTimePropertyName, 0., 5000.);  // limits are arbitrary really, just give a reasonable range
      UpdateProperty(g_ScanSettlingTimePropertyName);

      if (FirmwareVersionAtLeast(3.17)) {
         pAct = new CPropertyAction (this, &CXYStage::OnScanOvershootDistance);
         CreateProperty(g_ScanOvershootDistancePropertyName, "0", MM::Integer, false, pAct);  // on controller it is float but <1um precision isn't important and easier to deal with integer
         SetPropertyLimits(g_ScanOvershootDistancePropertyName, 0, 500);  // limits are arbitrary really, just give a reasonable range
         UpdateProperty(g_ScanOvershootDistancePropertyName);
      }

   }

   //Vector Move VE X=### Y=###
   pAct = new CPropertyAction (this, &CXYStage::OnVectorX);
   CreateProperty(g_VectorXPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_VectorXPropertyName, maxSpeedX*-1, maxSpeedX);
   UpdateProperty(g_VectorXPropertyName);
   pAct = new CPropertyAction (this, &CXYStage::OnVectorY);
   CreateProperty(g_VectorYPropertyName, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_VectorYPropertyName, maxSpeedY*-1 , maxSpeedY);
   UpdateProperty(g_VectorYPropertyName);

   initialized_ = true;
   return DEVICE_OK;
}

double CXYStage::getMaxSpeed(string axisLetter)
{
   double maxSpeed;
   ostringstream command;
   command << "S " << axisLetter << "?";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   double origSpeed;
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(origSpeed) );
   ostringstream command2; command2.str("");
   command2 << "S " << axisLetter << "=10000";
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command2.str(), ":A")); // set too high
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));  // read actual max
   RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(maxSpeed) );
   command2.str("");
   command2 << "S " << axisLetter << "=" << origSpeed;
   RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command2.str(), ":A")); // restore
   return maxSpeed;
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

//rewritten to get 2 position from one serial command query, require half the time
//But may cause problem for cases where xy axis are on different cards , reply may not be in correct order
//int CXYStage::GetPositionSteps(long& x, long& y)
//{
//	 ostringstream command;	 command.str("");
//	 command << "W " << axisLetterX_<<" "<<axisLetterY_;
//	 RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
//	 vector<string> elems=hub_->SplitAnswerOnDelim(" ");
//	 //check if reply is in correct format
//	 //has exactly 3 strings after split, and first string is ":A"
//	 //W X Y
//	 //:A 123 123
//	 if(elems.size()<3 || elems[0].find(":A")== string::npos)
//	 {
//		RETURN_ON_MM_ERROR(DEVICE_SERIAL_INVALID_RESPONSE);
//	 }
//	 double xtmp,ytmp;
//	 xtmp=atoi(elems[1].c_str());
//	 ytmp=atoi(elems[2].c_str());
//	 x = (long)(xtmp/unitMultX_/stepSizeXUm_);
//	 y = (long)(ytmp/unitMultY_/stepSizeYUm_);
//
//	  return DEVICE_OK;
//}

int CXYStage::SetPositionSteps(long x, long y)
{
   ostringstream command; command.str("");
   command << "M " << axisLetterX_ << "=" << x*unitMultX_*stepSizeXUm_ << " " << axisLetterY_ << "=" << y*unitMultY_*stepSizeYUm_;
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::SetRelativePositionSteps(long x, long y)
{
   ostringstream command; command.str("");
   if ( (x == 0) && (y != 0) )
   {
      command << "R " << axisLetterY_ << "=" << y*unitMultY_*stepSizeYUm_;
   }
   else if ( (x != 0) && (y == 0) )
   {
      command << "R " << axisLetterX_ << "=" << x*unitMultX_*stepSizeXUm_;
   }
   else
   {
      command << "R " << axisLetterX_ << "=" << x*unitMultX_*stepSizeXUm_ << " " << axisLetterY_ << "=" << y*unitMultY_*stepSizeYUm_;
   }
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
   command.str("");
   command << addressChar_ << "HALT";
   RETURN_ON_MM_ERROR ( hub_->QueryCommand(command.str()) );
   return DEVICE_OK;
}

bool CXYStage::Busy()
{
   ostringstream command; command.str("");
   if (FirmwareVersionAtLeast(2.7)) // can use more accurate RS <axis>?
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
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
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
   command << "H " << axisLetterX_ << "=0 " << axisLetterY_ << "=0";
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::SetXOrigin()
{
   ostringstream command; command.str("");
   command << "H " << axisLetterX_ << "=0 ";
   return hub_->QueryCommandVerify(command.str(),":A");
}

int CXYStage::SetYOrigin()
{
   ostringstream command; command.str("");
   command << "H " << axisLetterY_ << "=0";
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
   if (FirmwareVersionAtLeast(2.7)) {
      ostringstream command; command.str("");
      command << "HM " << axisLetterX_ << "+" << " " << axisLetterY_ << "+";
      return hub_->QueryCommandVerify(command.str(),":A");
   }
   else
   {
      return DEVICE_UNSUPPORTED_COMMAND;
   }
}

int CXYStage::Move (double vx, double vy)
{
ostringstream command; command.str("");
command << "VE " << axisLetterX_ << "=" << vx <<" "<< axisLetterY_ << "=" << vy ;
return hub_->QueryCommandVerify(command.str(), ":A") ;

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
      command.str("");
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
      RETURN_ON_MM_ERROR (hub_->QueryCommandVerify(command.str(), ":A", (long)200));  // note added 200ms delay
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
      if ((tmpstr.compare(g_YesState) == 0) && !advancedPropsEnabled_) // after creating advanced properties once no need to repeat
      {
         CPropertyAction* pAct;
         advancedPropsEnabled_ = true;

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

int CXYStage::OnSpeedGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !refreshOverride_)
         return DEVICE_OK;
      refreshOverride_ = false;
      command << "S " << axisLetter << "?";
      response << ":A " << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "S " << axisLetter << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      if (speedTruth_) {
         refreshOverride_ = true;
         return OnSpeedGeneric(pProp, MM::BeforeGet, axisLetter);
      }
   }
   return DEVICE_OK;
}

int CXYStage::OnBacklashGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "B " << axisLetter << "?";
      response << ":" << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "B " << axisLetter << "=" << tmp/1000;;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnDriftErrorGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "E " << axisLetter << "?";
      response << ":" << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "E " << axisLetter << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnFinishErrorGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "PC " << axisLetter << "?";
      response << ":A " << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      tmp = 1000*tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "PC " << axisLetter << "=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnLowerLimGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SL " << axisLetter << "?";
      response << ":A " << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SL " << axisLetter << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnUpperLimGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "SU " << axisLetter << "?";
      response << ":A " << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "SU " << axisLetter << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnAccelerationGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "AC " << axisLetter << "?";
      ostringstream response; response.str(""); response << ":" << axisLetter << "=";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), response.str()));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << "AC " << axisLetter << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnMaintainStateGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MA " << axisLetter << "?";
      ostringstream response; response.str(""); response << ":A " << axisLetter << "=";
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
      command << "MA " << axisLetter << "=" << tmp;
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

int CXYStage::OnMotorControlGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "MC " << axisLetter << "?";
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
         command << "MC " << axisLetter << "-";
      else
         command << "MC " << axisLetter << "+";
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
      command.str("");
      if (strcmp(joystickMirror, g_YesState) == 0)
         command << addressChar_ << "JS X=-" << tmp;
      else
         command << addressChar_ << "JS X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command << addressChar_ << "JS Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      char joystickMirror[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetProperty(g_JoystickMirrorPropertyName, joystickMirror) );
      command.str("");
      if (strcmp(joystickMirror, g_YesState) == 0)
         command << addressChar_ << "JS Y=-" << tmp;
      else
         command << addressChar_ << "JS Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command.str("");
      if (tmpstr.compare(g_YesState) == 0)
         command << addressChar_ << "JS X=-" << joystickFast << " Y=-" << joystickSlow;
      else
         command << addressChar_ << "JS X=" << joystickFast << " Y=" << joystickSlow;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command << addressChar_ << "JS F?";  // query only the fast setting to see if already mirrored
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
      command.str("");
      if (strcmp(wheelMirror, g_YesState) == 0)
         command << addressChar_ << "JS F=-" << tmp;
      else
         command << addressChar_ << "JS F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command.str("");
      if (strcmp(wheelMirror, g_YesState) == 0)
         command << addressChar_ << "JS T=-" << tmp;
      else
         command << addressChar_ << "JS T=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command.str("");
      if (tmpstr.compare(g_YesState) == 0)
         command << addressChar_ << "JS F=-" << wheelFast << " T=-" << wheelSlow;
      else
         command << addressChar_ << "JS F=" << wheelFast << " T=" << wheelSlow;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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
      command << addressChar_ << "CCA Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      // don't complain if value is larger than MM's "artificial" limits, it just won't be set
      pProp->Set(tmp);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command.str("");
      command << addressChar_ << "CCA Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
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

int CXYStage::OnScanState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         case g_ScanStateCodeIdle:  success = pProp->Set(g_ScanStateIdle); break;
         default:                   success = pProp->Set(g_ScanStateRunning); break;  // a bunch of different codes are possible while running
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      char c;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_ScanStateIdle) == 0)
      {
         // TODO cleanup code by calling action handler with MM::BeforeGet?
         // check status and stop if it's not idle already
         command << addressChar_ << "SN X?";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
         if (c!=g_ScanStateCodeIdle)
         {
            // this will stop state machine if it's running, if we do SN without args we run the risk of it stopping itself before we send the next command
            // after we stop it, it will automatically go to idle state
            command.str("");
            command << addressChar_ << "SN X=" << (int)g_ScanStateCodeStop;
            RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         }
      }
      else if (tmpstr.compare(g_ScanStateRunning) == 0)
      {
         // check status and start if it's idle
         command << addressChar_ << "SN X?";
         RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
         RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
         if (c==g_SPIMStateCode_Idle)
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

int CXYStage::OnScanFastAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "SN Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      bool success;
      char c;
      RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
      switch ( c )
      {
         case g_ScanAxisXCode:  success = pProp->Set(g_ScanAxisX); break;
         case g_ScanAxisYCode:  success = pProp->Set(g_ScanAxisY); break;
         default:               success = false; break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      char c = ' ';
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_ScanAxisX) == 0) {
         c = g_ScanAxisXCode;
      } else if (tmpstr.compare(g_ScanAxisY) == 0) {
         c = g_ScanAxisYCode;
      }
      if (c == ' ')
      {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }
      command << addressChar_ << "SN Y=" << c;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CXYStage::OnScanSlowAxis(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "SN Z?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      bool success;
      char c;
      RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
      switch ( c )
      {
         case g_ScanAxisXCode:    success = pProp->Set(g_ScanAxisX); break;
         case g_ScanAxisYCode:    success = pProp->Set(g_ScanAxisY); break;
         case g_ScanAxisNullCode: success = pProp->Set(g_ScanAxisNull); break;
         default:                 success = false; break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      char c = ' ';
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_ScanAxisX) == 0) {
         c = g_ScanAxisXCode;
      } else if (tmpstr.compare(g_ScanAxisY) == 0) {
         c = g_ScanAxisYCode;
      } else if (tmpstr.compare(g_ScanAxisNull) == 0) {
         c = g_ScanAxisNullCode;
      }
      if (c == ' ')
      {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }
      command << addressChar_ << "SN Z=" << c;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CXYStage::OnScanPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "SN F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
      bool success;
      char c;
      RETURN_ON_MM_ERROR( hub_->GetAnswerCharAtPosition3(c) );
      switch ( c )
      {
         case g_ScanPatternRasterCode:      success = pProp->Set(g_ScanPatternRaster); break;
         case g_ScanPatternSerpentineCode:  success = pProp->Set(g_ScanPatternSerpentine); break;
         default:               success = false; break;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      char c = ' ';
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_ScanPatternRaster) == 0) {
         c = g_ScanPatternRasterCode;
      } else if (tmpstr.compare(g_ScanPatternSerpentine) == 0) {
         c = g_ScanPatternSerpentineCode;
      }
      if (c == ' ')
      {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }
      command << addressChar_ << "SN F=" << c;
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A"));
   }
   return DEVICE_OK;
}

int CXYStage::OnScanFastStartPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR X?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A X="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CXYStage::OnScanFastStopPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NR Y?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A Y="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
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

int CXYStage::OnScanSlowStartPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CXYStage::OnScanSlowStopPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CXYStage::OnScanNumLines(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   ostringstream response; response.str("");
   long tmp = 0;
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

int CXYStage::OnScanSettlingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   double tmp = 0;
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV F?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A F="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnScanOvershootDistance(MM::PropertyBase* pProp, MM::ActionType eAct)
// note ASI units are in millimeters but MM units are in micrometers
{
   ostringstream command; command.str("");
   double tmp = 0;  // represent as integer in um, but controller gives as float in mm
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << addressChar_ << "NV T?";
      RETURN_ON_MM_ERROR( hub_->QueryCommandVerify(command.str(), ":A T="));
      RETURN_ON_MM_ERROR( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(1000*tmp+0.5))  // convert to um, then round to nearest by adding 0.5 before implicit floor operation
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      command << addressChar_ << "NV T=" << tmp/1000;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   }
   return DEVICE_OK;
}

int CXYStage::OnVectorGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, string axisLetter)
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

