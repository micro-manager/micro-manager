/*
File:		MCL_MicroDrive.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_MICRO_DRIVE_H
#define _MCL_MICRO_DRIVE_H

#define XAXIS 1
#define YAXIS 2

#define X_REVERSE_LIMIT 0x1
#define X_FORWARD_LIMIT 0x2
#define Y_REVERSE_LIMIT 0x4
#define Y_FORWARD_LIMIT 0x8
#define BOTH_FORWARD_LIMITS 0xA

static const char* g_XYStageDeviceName = "MicroDrive XY Stage";

static const char* g_Keyword_SetPosXmm = "Set position X axis (mm)";
static const char* g_Keyword_SetPosYmm = "Set position Y axis (mm)";
static const char* g_Keyword_SetOriginHere = "Set origin here";
static const char* g_Keyword_Calibrate = "Calibrate";
static const char* g_Keyword_ReturnToOrigin = "Return to origin";
static const char* g_Keyword_PositionTypeAbsRel = "Position type (absolute/relative)";
static const char* g_Keyword_SetPosXYmm = "Set position XY axis (mm) (X= Y=)";

static const char* g_Listword_No = "No";
static const char* g_Listword_Yes = "Yes";
static const char* g_Listword_AbsPos = "Absolute Position";
static const char* g_Listword_RelPos = "Relative Position";

#endif