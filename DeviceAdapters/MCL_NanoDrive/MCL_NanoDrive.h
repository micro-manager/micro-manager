#ifndef _MCL_NANODRIVE_H_
#define _MCL_NANODRIVE_H_

// x = 1, y = 2, z = 3 as defined by Madlib.h
#define XAXIS 1
#define YAXIS 2
#define ZAXIS 3  

#define VALIDX 0x1
#define VALIDY 0x2
#define VALIDZ 0x4
#define AXIS_MASK 0x7

#define NUM_STEPS_16 65535		// total number of steps that can be made by a 16 bit device
#define NUM_STEPS_20 1048575	// total number of steps that can be made by a 20 bit device

// External names used used by the rest of the system
// to load particular device from the "MCL_NanoDrive.dll" library
static const char* g_StageDeviceName = "MCL NanoDrive Z Stage";
static const char* g_XYStageDeviceName = "MCL NanoDrive XY Stage";

// Keywords - used when creating properties
static const char* g_Keyword_SetPosZUm = "Set position Z (um)";
static const char* g_Keyword_SetPosXUm = "Set position X (um)";
static const char* g_Keyword_SetPosYUm = "Set position Y (um)";
static const char* g_Keyword_SetOrigin = "Set origin here";
static const char* g_Keyword_SetSequence = "Use Sequence";
static const char* g_Keyword_ShiftSequence = "Shift sequence by 1";

#endif //_MCL_NANODRIVE_H_
