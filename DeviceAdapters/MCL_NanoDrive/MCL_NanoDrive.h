/*
File:		MCL_NanoDrive.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once

#define XAXIS 1
#define YAXIS 2
#define ZAXIS 3  
#define AAXIS 4

#define NANODRIVE_FX_1AXIS				0x1000
#define NANODRIVE_FX_Z_ENCODER			0x2000
#define NANODRIVE_FX_2AXIS				0x1020
#define NANODRIVE_FX_3AXIS				0x1030
#define NANODRIVE_FX_3AXIS_20			0x1230
#define NANOALIGN_FX_3AXIS_20			0x1253
#define NANODRIVE_FX2_1AXIS				0x2001
#define NANODRIVE_FX2_3AXIS				0x2003
#define NANODRIVE_FX2_4AXIS				0x2004
#define NANODRIVE_FX2_1AXIS_20			0x2201
#define NANODRIVE_FX2_3AXIS_20			0x2203
#define NANOALIGN_FX2_3AXIS_16			0x2053
#define NANOALIGN_FX2_3AXIS_20			0x2253
#define NANOGAUGE_FX2					0x2100
#define NANODRIVE_FX2_CFOCUS			0x2401
#define NANODRIVE_FX2_DDS				0x2601
#define NC_FX2_THREEAXIS				0x3003

#define VALIDX 0x1
#define VALIDY 0x2
#define VALIDZ 0x4
#define VALIDA 0x8

#define NUM_STEPS_16 65535		// total number of steps that can be made by a 16 bit device
#define NUM_STEPS_20 1048575	// total number of steps that can be made by a 20 bit device

// External names used used by the rest of the system
// to load particular device from the "MCL_NanoDrive.dll" library
static const char* g_StageDeviceName = "MCL NanoDrive Z Stage";
static const char* g_XYStageDeviceName = "MCL NanoDrive XY Stage";

// Keywords - used when creating properties
static const char* g_Keyword_Handle = "Device Handle";
static const char* g_Keyword_Calibration = "Calibration";
static const char* g_Keyword_CalibrationX = "Calibration X";
static const char* g_Keyword_CalibrationY = "Calibration Y";
static const char* g_Keyword_SerialNumber = "Serial Number";
static const char* g_Keyword_DacSteps = "DacBits";
static const char* g_Keyword_DeviceAxisInUse = "Device axis in use";
static const char* g_Keyword_LowerLimit = "Lower Limit (um)";
static const char* g_Keyword_UpperLimit = "Upper Limit (um)";
static const char* g_Keyword_LowerLimitX = "Lower Limit X (um)";
static const char* g_Keyword_UpperLimitX = "Upper Limit X (um)";
static const char* g_Keyword_LowerLimitY = "Lower Limit Y (um)";
static const char* g_Keyword_UpperLimitY = "Upper Limit Y (um)";
static const char* g_Keyword_SettlingTime = "Settling time (ms)";
static const char* g_Keyword_SettlingTimeX = "Settling time X axis (ms)";
static const char* g_Keyword_SettlingTimeY = "Settling time Y axis (ms)";
static const char* g_Keyword_CommandedX = "CommandedX";
static const char* g_Keyword_CommandedY = "CommandedY";
static const char* g_Keyword_CommandedZ = "CommandedZ";
static const char* g_Keyword_TLC = "TLC";

static const char* g_Keyword_SetPosZUm = "Set position Z (um)";
static const char* g_Keyword_SetPosXUm = "Set position X (um)";
static const char* g_Keyword_SetPosYUm = "Set position Y (um)";
static const char* g_Keyword_SetOrigin = "Set origin here";
static const char* g_Keyword_SetSequence = "Use Sequence";
static const char* g_Keyword_ShiftSequence = "Shift sequence by 1";
static const char* g_Keyword_AxisUsedForTirf = "Axis used to maintain Tirf-Lock";
static const char* g_Keyword_ActionOnBlockedZMove = "Action on Z axis move while maintaining Tirf-Lock";

static const char* g_Value_ZStageDescription = "ZStage driver";
static const char* g_Value_XYStageDescription = "XY Stage Driver";
static const char* g_Value_No = "No";
static const char* g_Value_Yes = "Yes";
static const char* g_Value_Ignore = "Ignore";
static const char* g_Value_Error = "Error";


