//==========================================================================
//  File:       Stand.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CStand_Data
//				(dataclass for stand control)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "stand.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CStand_Data::CStand_Data() {
	// no library loaded
	m_Library_hinst = NULL;
	// no stand available
	m_Stand_available = FALSE;
	// Name = default
	m_MicroscopeName = "";
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CStand_Data::~CStand_Data() {
	// if stand control connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_Stand_available and m_MicroscopeName to default
/////////////////////////////////////////////////////////////////////////////
void CStand_Data::Disconnect(){
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no stand control connected
	m_Stand_available = FALSE;
	m_MicroscopeName = "";
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
//	- check stand control
//	- set parameter m_Stand_available, m_MicroscopeName
//  result :
//	- if connection was successful m_Stand_available = TRUE
/////////////////////////////////////////////////////////////////////////////
void CStand_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	// if stand control already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Stativ_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) 
	{
       // determine function pointer
       StandDLL_LoadDefParam    =(int (WINAPI *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefStandParam");
       StandDLL_GetInfo    =(int (WINAPI *)(LPSTATIVINFO))GetProcAddress(m_Library_hinst,(LPCSTR)"GetStativInfo");
	   StandDLL_GetFirmwareVersion = (int (WINAPI *)(LPSTR))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFirmwareVersion");
	   StandDLL_GetFocusFirmwareVersion = (int (WINAPI *)(LPSTR))GetProcAddress(m_Library_hinst,(LPCSTR)"GetFoc_FirmwareVersion");
       StandDLL_GetChangedComponents  =(int (WINAPI *)(UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetStativChangingBytes");
       StandDLL_SetAutomat =(int (WINAPI *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"SetAutomat");
       StandDLL_GetAutomat =(int (WINAPI *)(UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetAutomat");
	} else {
		// library not available
		return;
	}
	if (StandDLL_LoadDefParam 
		&& StandDLL_GetInfo
		&& StandDLL_GetFirmwareVersion
		&& StandDLL_GetFocusFirmwareVersion
		&& StandDLL_GetChangedComponents
		&& StandDLL_SetAutomat
		&& StandDLL_GetAutomat) 
	{	// if all functions available
		// initialize MicroToolbox library
			StandDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// Check stand control
	StandDLL_GetInfo(&m_Info);
	if (!m_Info.nComType) {
		// stand control not available
		Disconnect();
		return;
	} else {
		// read microscope name 
		if (::GetPrivateProfileString ( "Microscope", "Typ", "", buffer, sizeof(buffer), IniFile ) > 0)
			m_MicroscopeName = buffer;
		// stand control available
		m_Stand_available = TRUE;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	GetFirmwareVersion(CString& stVersion)
//	Parameter : 
//		stVersion : string for version
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		read firmware version-string of the microscope stand
/////////////////////////////////////////////////////////////////////////////
int CStand_Data::GetFirmwareVersion(CString& stVersion){
	int iRet;
	char buffer [128];
	// check : standcontrol available ?
	if (!m_Stand_available) return 128;

	iRet = StandDLL_GetFirmwareVersion(buffer);
	if (!iRet) 
		stVersion = buffer;
	else 
		// error
		stVersion = "No Version available";
	return iRet;
}


/////////////////////////////////////////////////////////////////////////////
//	GetFocusFirmwareVersion(CString& stVersion)
//	Parameter : 
//		stVersion : string for version
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		read firmware version-string of the z-drive
/////////////////////////////////////////////////////////////////////////////
int CStand_Data::GetFocusFirmwareVersion(CString& stVersion){
	int iRet;
	char buffer [128];
	// check : standcontrol available ?
	if (!m_Stand_available) return 128;

	iRet = StandDLL_GetFocusFirmwareVersion(buffer);
	if (!iRet) 
		stVersion = buffer;
	else 
		// error
		stVersion = "No Version available";
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GetChangedComponents(UINT* uStat)
//	Parameter : 
//		uStat : pointer for state
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		read, which components are changed since last question
//		(for the meaning of the bits see the description of the MicroToolbx
/////////////////////////////////////////////////////////////////////////////
int CStand_Data::GetChangedComponents(UINT* uStat){
	int iRet;
	UINT uTempStat;
	if (!m_Stand_available) {
		// standcontrol not available
		*uStat = 0;
		return 128;
	}
	iRet = StandDLL_GetChangedComponents(&uTempStat);
	if (!iRet) { 
		*uStat = uTempStat;
	} else 
		// error
		*uStat = 0;
	return iRet;
}


/////////////////////////////////////////////////////////////////////////////
//	SetAutomat(UINT uOnOff)
//	Parameter : 
//		uOnOff : 1 = On, 0 = Off
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		activate lightmanager
/////////////////////////////////////////////////////////////////////////////
int CStand_Data::SetAutomat(UINT uOnOff){
	int iRet;
	if (!m_Stand_available || ! m_Info.uAutomatExist) {
		// standcontrol or lightmanager not available
		return 128;
	}
	iRet = StandDLL_SetAutomat(uOnOff);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GetAutomat(UINT* uOnOff)
//	Parameter : 
//		uOnOff : pointer for result
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		check if lightmanager is activ
/////////////////////////////////////////////////////////////////////////////
int CStand_Data::GetAutomat(UINT* uOnOff){
	int iRet;
	UINT uTempStat;
	if (!m_Stand_available || ! m_Info.uAutomatExist) {
		// standcontrol or lightmanager not available
		*uOnOff = 0;
		return 128;
	}
	iRet = StandDLL_GetAutomat(&uTempStat);
	if (!iRet) { 
		*uOnOff = uTempStat;
	} else 
		// error
		*uOnOff = 0;
	return iRet;
}

