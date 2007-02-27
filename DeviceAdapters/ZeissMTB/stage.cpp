//==========================================================================
//  File:       Stage.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CStage_Data
//				(dataclass for xy-stages)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "stage.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CStage_Data::CStage_Data() {

	// no library loaded
	m_Library_hinst = NULL;

	// no stage connected
	m_StageName = "No stage connected";
	m_StageType = 0;
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CStage_Data::~CStage_Data() {
	// if stage connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_StageType and m_StageName to default
/////////////////////////////////////////////////////////////////////////////
void CStage_Data::Disconnect(){
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no stage connected
	m_StageName = "No stage connected";
	m_StageType = 0;
}

/////////////////////////////////////////////////////////////////////////////
//	Connect((CString IniFile, CString DLLPath)
//	Parameter : 
//		Inifile : filename for the MicroToolbox configurationfile toolbox.ini
//					(with full path)
//		DLLPath : path of the MicroToolbox-DLL
//
//  description : 
//	- determine the name of the MicroToolbox library
//	- load library
//	- determine function pointer
//	- initialize MicroToolbox library
//	- check stage
//	- set parameter m_StageType, m_StageName
//  result :
//	- if connection was successful m_StageType != 0
/////////////////////////////////////////////////////////////////////////////
void CStage_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	// if stage already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Stage_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) 
	{
       // determine function pointer
       StageDLL_LoadDefParam    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefStageParam");
       StageDLL_GetInfo    =(int (PASCAL *)(LPSTAINFO))GetProcAddress(m_Library_hinst,(LPCSTR)"GetStageInfo");
       StageDLL_Get    =(int (PASCAL *)(double*, double*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetStage");
       StageDLL_Move    =(int (PASCAL *)(double, double))GetProcAddress(m_Library_hinst,(LPCSTR)"MoveStage");
       StageDLL_Go    =(int (PASCAL *)(double, double, unsigned))GetProcAddress(m_Library_hinst,(LPCSTR)"GoStage");
       StageDLL_Stop    =(int (PASCAL *)())GetProcAddress(m_Library_hinst,(LPCSTR)"StopStage");
       StageDLL_Set    =(int (PASCAL *)(double, double))GetProcAddress(m_Library_hinst,(LPCSTR)"SetStage");
	} else {
		// library not available
		return;
	}
	if (StageDLL_LoadDefParam 
		&& StageDLL_GetInfo
		&& StageDLL_Move
		&& StageDLL_Get
		&& StageDLL_Go
		&& StageDLL_Stop
		&& StageDLL_Set) 
	{	// if all functions available
		// initialize MicroToolbox library
			StageDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// Check stage
	// info-stuct defined in interface definition stageapi.h
	STAINFO stage_Info;
	StageDLL_GetInfo(&stage_Info);
	if (stage_Info.nType == 1 || stage_Info.nType == 2) {
		// stage available
		m_StageType = stage_Info.nType;
		m_StageName = stage_Info.name;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	MoveStage(double dPosX, double dPosY)
//	Parameter : 
//		dPosX : new x-position
//		dPosY : new y-position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start moving to the desired position
/////////////////////////////////////////////////////////////////////////////
int CStage_Data::MoveStage(double dPosX, double dPosY){
	int iRet;
	if (m_StageType != 2) {
		// not motorized
		return 128;
	}
	iRet = StageDLL_Move(dPosX, dPosY);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	ReadStagePos(double* dPosX, double* dPosY)
//	Parameter : 
//		dPosX : pointer for  x-position
//		dPosY : pointer for  y-position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		read actual position
/////////////////////////////////////////////////////////////////////////////
int CStage_Data::ReadStagePos(double* dPosX, double* dPosY){
	int iRet;
	double dTempPosX, dTempPosY;
	if (!m_StageType) {
		// not available
		*dPosX = 0;
		*dPosY = 0;
		return 128;
	}
	iRet = StageDLL_Get(&dTempPosX, &dTempPosY);
	if (!iRet) {
		*dPosX = dTempPosX;
		*dPosY = dTempPosY;
	} else {
		// error
		*dPosX = 0;
		*dPosY = 0;
	}
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetStagePos(double dPosX, double dPosY)
//	Parameter : 
//		dPosX : new x-position
//		dPosY : new y-position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		sets the actual position to the given values (no move)
/////////////////////////////////////////////////////////////////////////////
int CStage_Data::SetStagePos(double dPosX, double dPosY){
	int iRet;
	if (!m_StageType) {
		// not available
		return 128;
	}
	iRet = StageDLL_Set(dPosX, dPosY);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GoStage(double dSpeedX, double dSpeedY)
//	Parameter : 
//		dSpeedX : speed for x-axis
//		dSpeedY : speed for y-axis
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start moving with the given speed
/////////////////////////////////////////////////////////////////////////////
int CStage_Data::GoStage(double dSpeedX, double dSpeedY){
	int iRet;
	if (m_StageType != 2) {
		// not motorized
		return 128;
	}
	iRet = StageDLL_Go(dSpeedX, dSpeedY,0);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	StopStage()
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		stop moving of the stage
/////////////////////////////////////////////////////////////////////////////
int CStage_Data::StopStage(){
	int iRet;
	if (m_StageType != 2) {
		// not motorized
		return 128;
	}
	iRet = StageDLL_Stop();
	return iRet;
}