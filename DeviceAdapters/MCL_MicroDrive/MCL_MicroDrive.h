/*
File:		MCL_MicroDrive.h
Copyright:	Mad City Labs Inc., 2019
License:	Distributed under the BSD license.
*/
#pragma once 

#define M1AXIS 1
#define M2AXIS 2
#define M3AXIS 3
#define M4AXIS 4
#define M5AXIS 5
#define M6AXIS 6

#define BITMASK_M1 0x01
#define BITMASK_M2 0x02
#define BITMASK_M3 0x04
#define BITMASK_M4 0x08
#define BITMASK_M5 0x10
#define BITMASK_M6 0x20

#define MICRODRIVE                  0x2500
#define MICRODRIVE1					0x2501
#define MICRODRIVE3                 0x2503
#define MICRODRIVE4					0x2504
#define MICRODRIVE6					0x2506
#define NC_MICRODRIVE				0x3500

static const char* g_StageDeviceName = "MicroDrive Z Stage";
static const char* g_XYStageDeviceName = "MicroDrive XY Stage";

static const char* g_Keyword_SetPosXmm = "Set position X axis (mm)";
static const char* g_Keyword_SetPosYmm = "Set position Y axis (mm)";
static const char* g_Keyword_SetPosZmm = "Set position Z axis (mm)";
static const char* g_Keyword_SetOriginHere = "Set origin here";
static const char* g_Keyword_Calibrate = "Calibrate";
static const char* g_Keyword_ReturnToOrigin = "Return to origin";
static const char* g_Keyword_PositionTypeAbsRel = "Position type (absolute/relative)";
static const char* g_Keyword_Encoded = "EncodersPresent";
static const char* g_Keyword_IterativeMove = "Enable iterative moves";
static const char* g_Keyword_ImRetry = "IM number of retries";
static const char* g_Keyword_ImTolerance = "IM tolerance in Um";
static const char* g_Keyword_IsTirfModuleAxis = "TIRF module axis";
static const char* g_Keyword_IsTirfModuleAxis1 = "TIRF module axis1";
static const char* g_Keyword_IsTirfModuleAxis2 = "TIRF module axis2";

static const char* g_Listword_No = "No";
static const char* g_Listword_Yes = "Yes";
static const char* g_Listword_AbsPos = "Absolute Position";
static const char* g_Listword_RelPos = "Relative Position";

static const char* g_Keyword_FindEpi = "Find Epi";
