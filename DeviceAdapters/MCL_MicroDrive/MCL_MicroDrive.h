/*
File:		MCL_MicroDrive.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_MICRO_DRIVE_H
#define _MCL_MICRO_DRIVE_H

#define XAXIS 1
#define YAXIS 2
#define ZAXIS 3

#define VALIDX 0x1
#define VALIDY 0x2
#define VALIDZ 0x4
#define AXIS_MASK 0x7

#define X_REVERSE_LIMIT 0x01
#define X_FORWARD_LIMIT 0x02
#define Y_REVERSE_LIMIT 0x04
#define Y_FORWARD_LIMIT 0x08
#define Z_REVERSE_LIMIT 0x10
#define Z_FORWARD_LIMIT 0x20
#define BOTH_FORWARD_LIMITS 0xA

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

static const char* g_Listword_No = "No";
static const char* g_Listword_Yes = "Yes";
static const char* g_Listword_AbsPos = "Absolute Position";
static const char* g_Listword_RelPos = "Relative Position";

#endif