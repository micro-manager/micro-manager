//==========================================================================
//  File:       Servo.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CServo_Data
//				(dataclass for servo devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "servo.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CServo_Data::CServo_Data() {
	int i;

	// no library loaded
	m_Library_hinst = NULL;

	// no servo connected
	for(i=0; i<=_PL_LAST_SERVO; i++) {
		m_ServoName[i] = "No servo connected";
		m_ServoMin[i] = 0;
		m_ServoMax[i] = 0;
		m_ServoType[i] = 0;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CServo_Data::~CServo_Data() {
	// if servo connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_ServoName[] and m_ServoType[] to default
/////////////////////////////////////////////////////////////////////////////
void CServo_Data::Disconnect(){
	UINT i;
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no servo connected
	for(i=0; i<=_PL_LAST_SERVO; i++) {
		m_ServoName[i] = "No servo connected";
		m_ServoMin[i] = 0;
		m_ServoMax[i] = 0;
		m_ServoType[i] = 0;
	}
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
//	- check servos
//	- set parameter m_ServoName[], m_ServoType[], m_ServoMin[], m_ServoMax[]
//  result :
//	- if connection was successful m_ServoType[] != 0
/////////////////////////////////////////////////////////////////////////////
void CServo_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	int i;
	UINT uPos;
	// if servos already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Servo_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) 
	{
       // determine function pointer
       ServoDLL_LoadDefParam    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefServoParam");
       ServoDLL_GetInfo    =(int (PASCAL *)(UINT, LPSERVOINFO))GetProcAddress(m_Library_hinst,(LPCSTR)"GetServoInfo");
	   ServoDLL_GetName = (int (PASCAL *)(UINT, LPSTR))GetProcAddress(m_Library_hinst,(LPCSTR)"GetServoName");
       ServoDLL_Check    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"CheckServo");
       ServoDLL_Get    =(int (PASCAL *)(UINT, double*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetServo");
       ServoDLL_Set    =(int (PASCAL *)(UINT, double))GetProcAddress(m_Library_hinst,(LPCSTR)"SetServo");
       ServoDLL_Go    =(int (PASCAL *)(UINT, int))GetProcAddress(m_Library_hinst,(LPCSTR)"GoServo");
       ServoDLL_Stop    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"StopServo");
	} else {
		// library not available
		return;
	}
	if (ServoDLL_LoadDefParam 
		&& ServoDLL_Check
		&& ServoDLL_GetName
		&& ServoDLL_Get
		&& ServoDLL_Set
		&& ServoDLL_Go
		&& ServoDLL_Stop) 
	{	// if all functions available
		// initialize MicroToolbox library
			ServoDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// Check all servos
	SERVOINFO servo_Info;
	for (i=0; i<_PL_LAST_SERVO; i++) {
		uPos = ServoDLL_Check(i);
		if (uPos == _E_ENC_NOT_MOT || uPos == _E_ENC_AND_MOT) {
			m_ServoType[i]= uPos;
			ServoDLL_GetInfo(i,&servo_Info);
			m_ServoMin[i] = servo_Info.dMinValue;
			m_ServoMax[i] = servo_Info.dMaxValue;
			ServoDLL_GetName(i, buffer);
			m_ServoName[i] = buffer;
		}
	}
}


/////////////////////////////////////////////////////////////////////////////
//	SetServoPos(UINT uServoID, double dPos)
//	Parameter : 
//		uServoID : ID for the desired servo
//		dPos : desired position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		set the new position
/////////////////////////////////////////////////////////////////////////////
int CServo_Data::SetServoPos(UINT uServoID, double dPos){
	int iRet;
	// check ID
	if (uServoID >= _PL_LAST_SERVO) return 128;

	if (m_ServoType[uServoID] != _E_ENC_AND_MOT) {
		// servo not motorized
		return 128;
	}
	iRet = ServoDLL_Set(uServoID,dPos);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	ReadServoPos(UINT uServoID, double* dPos)
//	Parameter : 
//		uServoID : ID for the desired servo
//		dPos : pointer for actual position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the actual position
/////////////////////////////////////////////////////////////////////////////
int CServo_Data::ReadServoPos(UINT uServoID, double* dPos){
	int iRet;
	double dTempPos;
	// check ID
	if (uServoID >= _PL_LAST_SERVO) return 128;

	if (!m_ServoType[uServoID]) {
		// servo not available
		*dPos = 0;
		return 128;
	}
	iRet = ServoDLL_Get(uServoID,&dTempPos);
	if (!iRet) 
		*dPos = dTempPos;
	else 
		*dPos = 0;	// error
	return iRet;
}


/////////////////////////////////////////////////////////////////////////////
//	GoServo(UINT uServoID, int iDir)
//	Parameter : 
//		uServoID : ID for the desired servo
//		iDir : desired direction
//				+1 enlarge position
//				-1 reduce position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		start moving until StopServo(..) is called 
/////////////////////////////////////////////////////////////////////////////
int CServo_Data::GoServo(UINT uServoID, int iDir){
	int iRet;
	// check ID
	if (uServoID >= _PL_LAST_SERVO) return 128;

	if (m_ServoType[uServoID] != _E_ENC_AND_MOT) {
		// servo not motorized
		return 128;
	}
	iRet = ServoDLL_Go(uServoID, iDir);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	StopServo(UINT uServoID)
//	Parameter : 
//		uServoID : ID for the desired servo
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		stop moving after GoServo(..) was called 
/////////////////////////////////////////////////////////////////////////////
int CServo_Data::StopServo(UINT uServoID){
	int iRet;
	// check ID
	if (uServoID >= _PL_LAST_SERVO) return 128;

	if (m_ServoType[uServoID] != _E_ENC_AND_MOT) {
		// servo not motorized
		return 128;
	}
	iRet = ServoDLL_Stop(uServoID);
	return iRet;
}