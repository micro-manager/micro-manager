//==========================================================================
//  File:       Focus.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CFocus_Data
//				(dataclass for z-drive)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "focus.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CFocus_Data::CFocus_Data() {

	// no library loaded
	m_Library_hinst = NULL;

	// no z-drive connected
	m_FocusName = "No z-drive connected";
	m_FocusType = 0;
   m_FocusStep = 0.0;
   m_ZMin = 0.0;
   m_ZMax = 0.0;
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CFocus_Data::~CFocus_Data() {
	// if z-drive connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_FocusName and m_FocusType to default
/////////////////////////////////////////////////////////////////////////////
void CFocus_Data::Disconnect(){
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no z-drive connected
	m_FocusName = "No z-drive connected";
	m_FocusType = 0;
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
//	- check z-drive
//	- set parameter m_FocusName and m_FocusType
//  result :
//	- if connection was successful m_FocusType != 0
/////////////////////////////////////////////////////////////////////////////
void CFocus_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	// if z-drive already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Focus_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) // load lbrary OK
	{
       // determine function pointer
	   FocusDLL_LoadDefParam    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefFocParam");
       FocusDLL_GetInfo    =(int (PASCAL *)(LPFOCINFO))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFocInfo");
	   FocusDLL_Move = (int (PASCAL *)(double))GetProcAddress(m_Library_hinst,(LPCSTR)"MoveFoc");
       FocusDLL_Go    =(int (PASCAL *)(double, UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"GoFoc");
       FocusDLL_Get    =(int (PASCAL *)(double*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFoc");
       FocusDLL_Stop    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"StopFoc");
       FocusDLL_SetLoadWork    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"Set_Load_Work");
       FocusDLL_GetLoadWork    =(int (PASCAL *)(UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"Get_Load_Work");
       FocusDLL_Init    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"InitFoc");
       FocusDLL_GetFocLimits  =(int (PASCAL *)(double*, double*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFocLimits");
       FocusDLL_GetFocStat    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFocStat");
	} else {
		// library not available
		return;
	}

	if (FocusDLL_LoadDefParam 
		&& FocusDLL_GetInfo
		&& FocusDLL_Move
		&& FocusDLL_Go
		&& FocusDLL_Get
		&& FocusDLL_Stop
		&& FocusDLL_SetLoadWork
		&& FocusDLL_GetLoadWork) 
	{	// if all functions available
		// init MicroToolbox library
			FocusDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// check z-drive
	// info-stuct defined in interface definition foc_api.h
	FOCINFO foc_Info;  
	FocusDLL_GetInfo(&foc_Info);
	if (foc_Info.nType == 1 || foc_Info.nType == 2) {
		// z-drive available ?
		m_FocusType= foc_Info.nType;
		m_FocusName = foc_Info.name;
      m_FocusStep = foc_Info.dMotor;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	MoveFocus(double dPos)
//	Parameter : 
//		dPos : new z-position in µm
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start z-move to the new position
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::MoveFocus(double dPos){
	int iRet;
	if (m_FocusType != 2) {
		// z not motorized !
		return 128;
	}
	iRet = FocusDLL_Move(dPos);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	ReadFocusPos(double* dPos)
//	Parameter : 
//		dPos : pointer for actual z-position in µm
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the actual z position
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::ReadFocusPos(double* dPos){
	int iRet;
	double dTempPos;
	if (!m_FocusType) {
		// z-drive not available
		*dPos = 0;
		return 128;
	}
	iRet = FocusDLL_Get(&dTempPos);
	if (!iRet) 
		*dPos = dTempPos;
	else 
		*dPos = 0;
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GoFocus(double dSpeed)
//	Parameter : 
//		dSpeed : new z-speed in µm/sec
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start unlimited z-move with the given speed
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::GoFocus(double dSpeed){
	int iRet;
	if (m_FocusType != 2) {
		// z not motorizesd
		return 128;
	}
	iRet = FocusDLL_Go(dSpeed,0);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	StopFocus()
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		stop z-move 
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::StopFocus(){
	int iRet;
	if (m_FocusType != 2) {
		// z-drive not available
		return 128;
	}
	iRet = FocusDLL_Stop();
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetLoadWork(UINT uPos)
//	Parameter : 
//		uPos :	0 : load position
//				1 :	work position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start z-move to load- or workposition
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::SetLoadWork(UINT uPos){
	int iRet;
	if (m_FocusType != 2 || !FocusDLL_Init) {
		// z-drive not motorized
		return 128;
	}
	iRet = FocusDLL_SetLoadWork(uPos);
	return iRet;
}


/////////////////////////////////////////////////////////////////////////////
//	GetLoadWork(UINT* uPos)
//	Parameter : 
//		uPos :  pointer for result
//				4 : load position
//				1 : workposition
//				other : is moving between this positions yet
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		determine if z-drive is in load or workposition
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::GetLoadWork(UINT* uPos){
	int iRet;
	UINT uTempPos;
	if (m_FocusType != 2) {
		// z-drive not motorized
		return 128;
	}
	iRet = FocusDLL_GetLoadWork(&uTempPos);
	if (!iRet) 
		*uPos = uTempPos;
	else 
		*uPos = 0;
	return iRet;
}


/////////////////////////////////////////////////////////////////////////////
//	FocInit()
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		call z initialization
/////////////////////////////////////////////////////////////////////////////
int CFocus_Data::FocInit(){
	int iRet;
	if (m_FocusType != 2) {
		// z-drive not motorized
		return 128;
	}
	iRet = FocusDLL_Init();
	return iRet;
}


bool CFocus_Data::Busy()
{
	int ret = FocusDLL_GetFocStat();
   if (ret & 0x01)
      return true;
   else
      return false;
}
