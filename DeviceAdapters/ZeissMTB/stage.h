//==========================================================================
//  File:       Stage.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CStage_Data
//				(dataclass for xy-stages)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef STAGE_H
#define STAGE_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/stageapi.h"


class CStage_Data
{
public:
	// constructor - destructor
	CStage_Data();
	~CStage_Data();
	// log and unlog lamp control via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// desciption
	CString m_StageName;

	// type (1 = encoded, 2 = motorized, 0 = not available
	UINT m_StageType;

	// public functions to control xy-stage
	int MoveStage(double dPosX, double dPosY);
	int ReadStagePos(double* dPosX, double* dPosY);
	int GoStage(double dSpeedX, double dSpeedY);
	int StopStage();
	int SetStagePos(double dPosX, double dPosY);


private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-interface-functions
	int (WINAPI *StageDLL_LoadDefParam  )(void);  
	int (WINAPI *StageDLL_GetInfo  )(LPSTAINFO pInfo);  
	int (WINAPI *StageDLL_Get  )(double* dPosX, double* dPosY);  
	int (WINAPI *StageDLL_Move  )(double dPosX, double dPosY);  
	int (WINAPI *StageDLL_Go  )(double dSpeedX, double dSpeedY, unsigned flag);  
	int (WINAPI *StageDLL_Stop)(void);  
	int (WINAPI *StageDLL_Set  )(double dPosX, double dPosY);  
};

#endif //STAGE_H
