///////////////////////////////////////////////////////////////////////////////
// FILE:          ASITiger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI Tiger common defines and ASIUtility class
//                Note this is for the "Tiger" MM set of adapters, which should
//                  work for more than just the TG-1000 "Tiger" controller
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
// BASED ON:      ASIStage.h, ASIFW1000.h, Arduino.h, and DemoCamera.h
//

#ifndef _ASITiger_H_
#define _ASITiger_H_

#include <string>
#include <vector>
#include <iostream>

using namespace std;


//////////////////////////////////////////////////////////////////////////////
// ASI-specific macros
//
#define RETURN_ON_MM_ERROR( result ) if( DEVICE_OK != (ret_ = result) ) return ret_;
// NB: assert shouldn't be used to do useful work because release version strips it out
// e.g. instead of "assert(foo() == 1);" should do "int tmp = foo(); assert(tmp==1);"
//#define ASSERT_DEVICE_OK( result ) assert( DEVICE_OK == (result) )

// some shortcuts for bit manipulations
#define BIT0   0x01
#define BIT1   0x02
#define BIT2   0x04
#define BIT3   0x08
#define BIT4   0x10
#define BIT5   0x20
#define BIT6   0x40
#define BIT7   0x80

//////////////////////////////////////////////////////////////////////////////
// ASI-specific error codes and messages
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_UNRECOGNIZED_ANSWER      10009
const char* const g_Msg_ERR_UNRECOGNIZED_ANSWER = "Unrecognized serial answer from ASI device";
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_NOT_LOCKED               10011
#define ERR_NOT_CALIBRATED           10012
#define ERR_NOT_ENOUGH_AXES          10021   // if TigerComm gets back too few axes on BU X
const char* const g_Msg_ERR_NOT_ENOUGH_AXES = "Do not have any axes installed";
#define ERR_TOO_LARGE_ADDRESSES      10022   // if we have addresses 0x81 and higher without new firmware
const char* const g_Msg_ERR_TOO_LARGE_ADDRESSES = "Need new firmware for more than 10 cards";
#define ERR_FILTER_WHEEL_NOT_READY   10030   // if filter wheel responds with error, e.g. it is not plugged in
const char* const g_Msg_ERR_FILTER_WHEEL_NOT_READY = "Filter wheel doesn't appear to be connected";
#define ERR_FILTER_WHEEL_SPINNING    10031   // if filter wheel is spinning and try to do something with it
const char* const g_Msg_ERR_FILTER_WHEEL_SPINNING = "Filter wheel cannot be set to position when spinning";
#define ERR_TIGER_DEV_NOT_SUPPORTED  10040
const char* const g_Msg_ERR_TIGER_DEV_NOT_SUPPORTED = "Device type not yet supported by Tiger";
#define ERR_TIGER_PAIR_NOT_PRESENT   10041
const char* const g_Msg_ERR_TIGER_PAIR_NOT_PRESENT = "Axis should be present in pair";
#define ERR_CRISP_NOT_CALIBRATED     10050
const char* const g_Msg_ERR_CRISP_NOT_CALIBRATED = "CRISP is not calibrated.  Try focusing close to a coverslip and selecting 'Calibrate'";
#define ERR_CRISP_NOT_LOCKED         10051
const char* const g_Msg_ERR_CRISP_NOT_LOCKED = "The CRISP failed to lock";

#define ERR_ASICODE_OFFSET 10100  // offset when reporting error number from controller


// External device names used used by the rest of the system to load particular device from the .dll library
const char* const g_TigerCommHubName =  "TigerCommHub";
const char* const g_ZStageDeviceName =  "ZStage";
const char* const g_XYStageDeviceName = "XYStage";
const char* const g_FSliderDeviceName =  "FilterSlider";
const char* const g_TurretDeviceName =  "Turret";
const char* const g_FWheelDeviceName =  "FilterWheel";
const char* const g_MMirrorDeviceName =  "MicroMirror";
const char* const g_PiezoDeviceName = "PiezoStage";
const char* const g_CRISPDeviceName = "CRISPAFocus";

// corresponding device descriptions
const char* const g_TigerCommHubDescription = "ASI TigerComm Hub (TG-1000)";
const char* const g_ZStageDeviceDescription =    "ASI Z Stage";
const char* const g_XYStageDeviceDescription =   "ASI XY Stage";
const char* const g_FSliderDeviceDescription =   "ASI Filter Slider";
const char* const g_FWheelDeviceDescription =   "ASI Filter Wheel";
const char* const g_TurretDeviceDescription =   "ASI Turret";
const char* const g_MMirrorDeviceDescription = "ASI 2-axis MicroMirror";
const char* const g_PiezoDeviceDescription = "ASI Piezo Stage";
const char* const g_CRISPDeviceDescription = "ASI CRISP AutoFocus";

// constant values
const double g_StageMinStepSize = 0.001;   // in units of um
const double g_StageDefaultUnitMult = 10;  // in units of um
const double g_MicromirrorDefaultUnitMult = 1000;  // units per degree
const char* const g_SerialTerminatorDefault = "\r\n";
const char* const g_SerialTerminatorFW = "\n\r";
const string g_EmptyAxisLetterStr = " ";     // single char but like convenience of strings
const string g_EmptyCardAddressCode = " ";   // ascii 0x31 for '1' through ascii 0x39 for '9', then 0x81 upward (extended ascii)
const string g_EmptyCardAddressStr = "00";   // hex representation of the address, eg 31..39, 81 upward
const char g_NameInfoDelimiter = ':';

// general device property names
const char* const g_FirmwareVersionPropertyName = "FirmwareVersion";
const char* const g_SaveSettingsPropertyName = "SaveCardSettings";
const char* const g_RefreshPropValsPropertyName = "RefreshPropertyValues";
const char* const g_AxisLetterXPropertyName = "AxisLetterX";
const char* const g_AxisLetterYPropertyName = "AxisLetterY";
const char* const g_AxisLetterPropertyName = "AxisLetter";
const char* const g_SetHomeHerePropertyName = "SetHomeToCurrentPosition";

// Hub property names
const char* const g_HubDevicePropertyName = "HubDeviceName";
const char* const g_TigerHexAddrPropertyName = "TigerHexAddress";
const char* const g_SerialCommandPropertyName = "SerialCommand";
const char* const g_SerialResponsePropertyName = "SerialResponse";
const char* const g_SerialTerminatorPropertyName = "SerialResponseTerminator";
const char* const g_SerialCommandOnlySendChangedPropertyName = "OnlySendSerialCommandOnChange";
const char* const g_SerialCommandRepeatDurationPropertyName = "SerialCommandRepeatDuration(s)";
const char* const g_SerialCommandRepeatPeriodPropertyName = "SerialCommandRepeatPeriod(ms)";

// XY stage property names
const char* const g_StepSizeXPropertyName = "StepSizeX(um)";
const char* const g_StepSizeYPropertyName = "StepSizeY(um)";
const char* const g_StageWaitTimePropertyName = "WaitTime_when_MaintainState=3(ms)";
const char* const g_NrExtraMoveRepsPropertyName = "ExtraMoveRepetitions";
const char* const g_MotorSpeedPropertyName = "MotorSpeed-S(mm/s)";
const char* const g_MaxMotorSpeedPropertyName = "MotorSpeedMaximum(mm/s)";
const char* const g_BacklashPropertyName = "Backlash-B(um)";
const char* const g_DriftErrorPropertyName =  "DriftError-E(um)";
const char* const g_FinishErrorPropertyName =  "FinishError-PC(um)";
const char* const g_AccelerationPropertyName = "Acceleration-AC(ms)";
const char* const g_LowerLimXPropertyName =  "LowerLimX(mm)";
const char* const g_LowerLimYPropertyName =  "LowerLimY(mm)";
const char* const g_UpperLimXPropertyName =  "UpperLimX(mm)";
const char* const g_UpperLimYPropertyName =  "UpperLimY(mm)";
const char* const g_MaintainStatePropertyName = "MaintainState-MA";
const char* const g_AdvancedPropertiesPropertyName = "EnableAdvancedProperties";
const char* const g_OvershootPropertyName = "Overshoot-O(um)";
const char* const g_KIntegralPropertyName = "ServoIntegral-KI";
const char* const g_KProportionalPropertyName = "ServoProportional-KP";
const char* const g_KDerivativePropertyName = "ServoIntegral-KD";
const char* const g_AAlignPropertyName = "MotorAlign-AA";
const char* const g_AZeroXPropertyName = "AutoZeroXResult-AZ";
const char* const g_AZeroYPropertyName = "AutoZeroYResult-AZ";
const char* const g_MotorControlPropertyName = "MotorOnOff";
const char* const g_JoystickMirrorPropertyName = "JoystickReverse";
const char* const g_JoystickSlowSpeedPropertyName = "JoystickSlowSpeed";
const char* const g_JoystickFastSpeedPropertyName = "JoystickFastSpeed";
const char* const g_JoystickEnabledPropertyName = "JoystickEnabled";

// Z stage property names
const char* const g_StepSizePropertyName = "StepSize(um)";
const char* const g_LowerLimPropertyName =  "LowerLim(mm)";
const char* const g_UpperLimPropertyName =  "UpperLim(mm)";
const char* const g_JoystickSelectPropertyName = "JoystickInput";

// filter wheel property names
const char* const g_FWSpinStatePropertyName = "SpinOffOn";
const char* const g_FWVelocityRunPropertyName = "VelocityRun";
const char* const g_FWSpeedSettingPropertyName = "SpeedSetting";
const char* const g_FWLockModePropertyName = "LockMode";

// micromirror property names
const char* const g_MMirrorLowerLimXPropertyName = "MinDeflectionX(deg)";
const char* const g_MMirrorUpperLimXPropertyName = "MaxDeflectionX(deg)";
const char* const g_MMirrorLowerLimYPropertyName = "MinDeflectionY(deg)";
const char* const g_MMirrorUpperLimYPropertyName = "MaxDeflectionY(deg)";
const char* const g_JoystickFastSpeedXPropertyName = "JoystickFastSpeedX";
const char* const g_JoystickFastSpeedYPropertyName = "JoystickFastSpeedY";
const char* const g_JoystickSlowSpeedXPropertyName = "JoystickSlowSpeedX";
const char* const g_JoystickSlowSpeedYPropertyName = "JoystickSlowSpeedY";
const char* const g_JoystickMirrorXPropertyName = "JoystickReverseX";
const char* const g_JoystickMirrorYPropertyName = "JoystickReverseY";
const char* const g_JoystickSelectXPropertyName = "JoystickInputX";
const char* const g_JoystickSelectYPropertyName = "JoystickInputY";
const char* const g_AdvancedSAPropertiesXPropertyName = "SingleAxisAdvancedXPropertiesEnable";
const char* const g_AdvancedSAPropertiesYPropertyName = "SingleAxisAdvancedYPropertiesEnable";
const char* const g_MMirrorSAAmplitudeXPropertyName = "SingleAxisXAmplitude(deg)";
const char* const g_MMirrorSAOffsetXPropertyName = "SingleAxisXOffset(deg)";
const char* const g_MMirrorSAPeriodXPropertyName = "SingleAxisXPeriod(ms)";
const char* const g_MMirrorSAModeXPropertyName = "SingleAxisXMode";
const char* const g_MMirrorSAPatternXPropertyName = "SingleAxisXPattern";
const char* const g_MMirrorSAClkSrcXPropertyName = "SingleAxisXClockSource";
const char* const g_MMirrorSAClkPolXPropertyName = "SingleAxisXClockPolarity";
const char* const g_MMirrorSATTLOutXPropertyName = "SingleAxisXTTLOut";
const char* const g_MMirrorSATTLPolXPropertyName = "SingleAxisXTTLPolarity";
const char* const g_MMirrorSAPatternModeXPropertyName = "SingleAxisXPatternByte";
const char* const g_MMirrorSAAmplitudeYPropertyName = "SingleAxisYAmplitude(deg)";
const char* const g_MMirrorSAOffsetYPropertyName = "SingleAxisYOffset(deg)";
const char* const g_MMirrorSAPeriodYPropertyName = "SingleAxisYPeriod(ms)";
const char* const g_MMirrorSAModeYPropertyName = "SingleAxisYMode";
const char* const g_MMirrorSAPatternYPropertyName = "SingleAxisYPattern";
const char* const g_MMirrorSAClkSrcYPropertyName = "SingleAxisYClockSource";
const char* const g_MMirrorSAClkPolYPropertyName = "SingleAxisYClockPolarity";
const char* const g_MMirrorSATTLOutYPropertyName = "SingleAxisYTTLOut";
const char* const g_MMirrorSATTLPolYPropertyName = "SingleAxisYTTLPolarity";
const char* const g_MMirrorSAPatternModeYPropertyName = "SingleAxisYPatternByte";
const char* const g_MMirrorModePropertyName = "InputMode";
const char* const g_MMirrorCutoffFilterXPropertyName = "FilterFreqX(kHz)";
const char* const g_MMirrorCutoffFilterYPropertyName = "FilterFreqY(kHz)";
const char* const g_MMirrorAttenuateXPropertyName = "AttenuateX(0..1)";
const char* const g_MMirrorAttenuateYPropertyName = "AttenuateY(0..1)";
const char* const g_MMirrorBeamEnabledPropertyName = "BeamEnabled";

// pizeo property names
const char* const g_CardVoltagePropertyName = "CardVoltage(V)"; // also used for micromirror
const char* const g_PiezoModePropertyName = "PiezoMode";
const char* const g_PiezoTravelRangePropertyName = "PiezoTravelRange(um)";
const char* const g_PiezoModeFourOvershoot = "PiezoModeFourOvershoot(percent)";
const char* const g_PiezoModeFourMaxTime = "PiezoModeFourMaxTime(ms)";
const char* const g_AdvancedSAPropertiesPropertyName = "SingleAxisAdvancedPropertiesEnable";
const char* const g_MMirrorSAAmplitudePropertyName = "SingleAxisAmplitude(um)";
const char* const g_MMirrorSAOffsetPropertyName = "SingleAxisOffset(um)";
const char* const g_MMirrorSAPeriodPropertyName = "SingleAxisPeriod(ms)";
const char* const g_MMirrorSAModePropertyName = "SingleAxisMode";
const char* const g_MMirrorSAPatternPropertyName = "SingleAxisPattern";
const char* const g_MMirrorSAClkSrcPropertyName = "SingleAxisClockSource";
const char* const g_MMirrorSAClkPolPropertyName = "SingleAxisClockPolarity";
const char* const g_MMirrorSATTLOutPropertyName = "SingleAxisTTLOut";
const char* const g_MMirrorSATTLPolPropertyName = "SingleAxisTTLPolarity";
const char* const g_MMirrorSAPatternModePropertyName = "SingleAxisPatternByte";

// CRISP property names
const char* const g_WaitAfterLockPropertyName = "Wait time after Lock(ms)";
const char* const g_ObjectiveNAPropertyName = "Objective NA";
const char* const g_LockRangePropertyName = "Lock Range(mm)";
const char* const g_CalibrationGainPropertyName = "Calibration Gain";
const char* const g_LEDIntensityPropertyName = "LED Intensity";
const char* const g_LoopGainMultiplierPropertyName = "LoopGainMultiplier";
const char* const g_NumberAveragesPropertyName = "Number of Averages";
const char* const g_SNRPropertyName = "Signal to Noise Ratio";
const char* const g_DitherErrorPropertyName = "Dither Error";

// SPIM property names
const char* const g_SPIMNumSlicesPropertyName = "SPIMNumSlices"; // used by both piezos and micromirror
const char* const g_SPIMNumScansPerSlicePropertyName = "SPIMNumScansPerSlice";
const char* const g_SPIMNumSidesPropertyName = "SPIMNumSides";
const char* const g_SPIMFirstSidePropertyName = "SPIMFirstSide";
const char* const g_SPIMNumRepeatsPropertyName = "SPIMNumRepeats";
const char* const g_SPIMArmForTTLPropertyName = "SPIMArm";
const char* const g_SPIMStatePropertyName = "SPIMState";
const char* const g_SPIMDelayBeforeSlicePropertyName = "SPIMDelayBeforeSlice(ms)";
const char* const g_SPIMDelayBeforeSidePropertyName = "SPIMDelayBeforeSide(ms)";

// SPIM enums
// which side first
const char* const g_SPIMSideAFirst = "A";
const char* const g_SPIMSideBFirst = "B";
// SPIM state for micro-manager
const char* const g_SPIMStateIdle = "Idle";
const char* const g_SPIMStateArmed = "Armed";
const char* const g_SPIMStateRunning = "Running";
// SPIM state on micromirror card
const char g_SPIMStateCode_Idle = 'I';
const char g_SPIMStateCode_Stop = 'P';
const char g_SPIMStateCode_Start = 'S';
const char g_SPIMStateCode_Arm =  'a';
const char g_SPIMStateCode_Armed ='A';
// SPIM state on piezo card
const char g_PZSPIMStateCode_Idle = 'I';
const char g_PZSPIMStateCode_Arm =  'a';
const char g_PZSPIMStateCode_Armed ='A';
const char g_PZSPIMStateCode_TimingPulse ='P';


// property descriptions for enums
// serial terminators for hub
const char* const g_SerialTerminator_0 = "Tiger+WK Default - \\r\\n";
const char* const g_SerialTerminator_0_Value = "\r\n";
const char* const g_SerialTerminator_1 = "FilterWheel - \\n\\r";
const char* const g_SerialTerminator_1_Value = "\n\r";
const char* const g_SerialTerminator_2 = "<NAK> - \\0x15";
const char* const g_SerialTerminator_2_Value = "\0x15";
const char* const g_SerialTerminator_3 = "return only - \\r";
const char* const g_SerialTerminator_3_Value = "\r";
const char* const g_SerialTerminator_4 = "newline only - \\n";
const char* const g_SerialTerminator_4_Value = "\\n";
// joystick codes
const char* const g_JSCode_0 = "0 - none";
const char* const g_JSCode_1 = "1 - factory default";
const char* const g_JSCode_2 = "2 - joystick X";
const char* const g_JSCode_3 = "3 - joystick Y";
const char* const g_JSCode_22 = "22 - right wheel";
const char* const g_JSCode_23 = "23 - left wheel";
// stage maintain behavior for motorized XY/Z stages
const char* const g_StageMaintain_0 = "0 - Motors off but correct drift for 0.5 sec";
const char* const g_StageMaintain_1 = "1 - Motors off but correct drift indefinitely";
const char* const g_StageMaintain_2 = "2 - Motors on indefinitely";
const char* const g_StageMaintain_3 = "3 - Motors on during wait time";
// on/off control settings
const char* const g_OffState = "Off";
const char* const g_OnState = "On";
// yes/no control settings
const char* const g_YesState = "Yes";
const char* const g_NoState = "No";
// single-axis mode
const char* const g_SAMode_0 = "0 - Disabled";
const char* const g_SAMode_1 = "1 - Enabled";
const char* const g_SAMode_2 = "2 - Armed for TTL trigger";
const char* const g_SAMode_3 = "3 - Enabled with axes synced";
// single-axis pattern
const char* const g_SAPattern_0 = "0 - Ramp";
const char* const g_SAPattern_1 = "1 - Triangle";
const char* const g_SAPattern_2 = "2 - Square";
// single-axis clock source
const char* const g_SAClkSrc_0 = "internal 4kHz clock";
const char* const g_SAClkSrc_1 = "external clock";
// single-axis clock polarity
const char* const g_SAClkPol_0 = "positive edge";
const char* const g_SAClkPol_1 = "negative edge";
// micromirror TTL out enable
const char* const g_SATTLOut_0 = g_NoState;
const char* const g_SATTLOut_1 = g_YesState;
// micromirror TTL polarity
const char* const g_SATTLPol_0 = "active high";
const char* const g_SATTLPol_1 = "active low";
// micromirror input modes
const char* const g_MMirrorMode_external = "external input";
const char* const g_MMirrorMode_internal = "internal input";
// piezo control modes
const char* const g_AdeptMode_0 = "0 - internal input closed-loop";
const char* const g_AdeptMode_1 = "1 - external input closed-loop";
const char* const g_AdeptMode_2 = "2 - internal input open-loop";
const char* const g_AdeptMode_3 = "3 - external input open-loop";
const char* const g_AdeptMode_4 = "4 - internal input closed-loop, speedup";
// save settings options
const char* const g_SaveSettingsX = "X - reload factory defaults on startup to card";
const char* const g_SaveSettingsY = "Y - restore last saved settings from card";
const char* const g_SaveSettingsZ = "Z - save settings to card (partial)";
const char* const g_SaveSettingsOrig = "no action";
const char* const g_SaveSettingsDone = "save settings done";
// command execute settings
const char* const g_IdleState = "Not done";
const char* const g_DoItState = "Do it";
const char* const g_DoneState = "Done";

// CRISP states
const char* const g_CRISPState = "CRISP State";
const char* const g_CRISP_I = "Idle";
const char* const g_CRISP_R = "Ready";
const char* const g_CRISP_D = "Dim";
const char* const g_CRISP_K = "Lock";  // enter this state to try to lock, system will move to F when locked
const char* const g_CRISP_F = "In Focus";  // a "read only" state, don't go to directly but via K state
const char* const g_CRISP_N = "Inhibit";
const char* const g_CRISP_E = "Error";
const char* const g_CRISP_G = "loG_cal";
const char* const g_CRISP_SG = "gain_Cal";
const char* const g_CRISP_Cal = "Calibrating";
const char* const g_CRISP_f = "Dither";
const char* const g_CRISP_C = "Curve";
const char* const g_CRISP_B = "Balance";
const char* const g_CRISP_RFO = "Reset Focus Offset";
const char* const g_CRISP_SSZ = "Save to Controller";


struct build_info_type
{
   string buildname;
   unsigned char numAxes;
   vector<char> vAxesLetter;
   vector<char> vAxesType;
   vector<string> vAxesAddr;  // string to handle unprintable characters
   vector<string> vAxesAddrHex;  // string for simplicity, logically it should be int though
   vector<int> vAxesProps;
   vector<string> defines;
};

// define names
const char* const g_Define_SINGLEAXIS_FUNCTION = "SINGLEAXIS_FUNCTION";


#endif //_ASITiger_H_
