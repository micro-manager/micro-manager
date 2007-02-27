//==========================================================================
//  File:       Revolver.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CRevolver_Data
//				(dataclass for revolver devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "revolver.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CRevolver_Data::CRevolver_Data() {
	int i;

	// no library loaded
	m_Library_hinst = NULL;

	// no revolver connected
	for(i=0; i<=_R_LAST_REVOLVER; i++) {
		m_RevolverName[i] = "No revolver connected";
		m_RevolverNumberOfPositions[i] = 0;
		m_RevolverType[i] = 0;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CRevolver_Data::~CRevolver_Data() {
	// if revolver connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_RevolverName[], m_RevolverType[], 
//				  m_RevolverNumberOfPositions[] to default
/////////////////////////////////////////////////////////////////////////////
void CRevolver_Data::Disconnect(){
	UINT i;
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	for(i=0; i<=_R_LAST_REVOLVER; i++) {
		m_RevolverName[i] = "No revolver connected";
		m_RevolverNumberOfPositions[i] = 0;
		m_RevolverType[i] = 0;
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
//	- check revolver devices
//	- set parameter m_RevolverName[], m_RevolverType[], m_RevolverNumberOfPositions[]
//  result :
//	- if connection was successful m_RevolverType[] != 0
/////////////////////////////////////////////////////////////////////////////
void CRevolver_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	int i;
	UINT uPos;
	// if revolvers already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Revolver_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library	
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) 
	{
       // determine function pointer
       RevDLL_LoadDefParam    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefRevParam");
       RevDLL_GetStat    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"GetRevStat");
       RevDLL_Check    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"CheckRev");
       RevDLL_Get    =(int (PASCAL *)(UINT, UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetRev");
       RevDLL_Set    =(int (PASCAL *)(UINT, UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"SetRev");
       RevDLL_GetPositions    =(int (PASCAL *)(UINT, UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetRevPositions");
       RevDLL_GetInfoExt    =(int (PASCAL *)(UINT, riRev_Info_TypeExt*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetRevolverInfoExt");
       RevDLL_SetSecurity    =(int (PASCAL *)(UINT))GetProcAddress(m_Library_hinst,(LPCSTR)"SetSecurity");
       RevDLL_GetRevName    =(int (PASCAL *)(UINT, char*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetRevName");
       RevDLL_GetPosName    =(int (PASCAL *)(UINT, UINT,char*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetPosName");
	} else {
		// library not available
		return;
	}

	if (RevDLL_LoadDefParam 
		&& RevDLL_Check
		&& RevDLL_Get
		&& RevDLL_Set
		&& RevDLL_GetStat
		&& RevDLL_GetPositions
		&& RevDLL_GetRevName
        && RevDLL_SetSecurity
        && RevDLL_GetInfoExt
		&& RevDLL_GetPosName) 
	{	// if all functions available
		// initialize MicroToolbox library
			RevDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

    riRev_Info_TypeExt Info;
	// Check all revolvers
	for (i=0; i<_R_LAST_REVOLVER; i++) {
		uPos = RevDLL_Check(i);
		if (uPos == _R_ENC_NOT_MOT || uPos == _R_ENC_AND_MOT) 
		{	// if revolver available 
			// then determine parameters
			m_RevolverType[i]= uPos;
			RevDLL_GetPositions(i, &m_RevolverNumberOfPositions[i]);
			RevDLL_GetRevName(i, buffer);
			m_RevolverName[i] = buffer;
            RevDLL_GetInfoExt(i, &Info );
            m_RevolverHasInterPos[i] = Info.uHasInterPos;
		}
	}
}

/////////////////////////////////////////////////////////////////////////////
//	SetRevolverPos(UINT uRevolverID, UINT uPos)
//	Parameter : 
//		uRevolverID : ID for the desired revolver
//		uPos : desired position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		change the revolver position
/////////////////////////////////////////////////////////////////////////////
int CRevolver_Data::SetRevolverPos(UINT uRevolverID, UINT uPos){
   try {
	   int iRet;
	   // ID OK ?
	   if (uRevolverID > _R_LAST_REVOLVER) return 128;

	   if (m_RevolverType[uRevolverID] != _R_ENC_AND_MOT) {
		   // not motorized !
		   return 128;
	   }
	   iRet = RevDLL_Set(uRevolverID,uPos);
	   return iRet;
   }
   catch (COleException* err)
   {
      err->ReportError();
      TRACE("OLE error occured!!!\n");
      return 150;
   }
}

/////////////////////////////////////////////////////////////////////////////
//	ReadRevolverPos(UINT uRevolverID, UINT* uPos)
//	Parameter : 
//		uRevolverID : ID for the desired revolver
//		uPos : pointer for actual position
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the actual position
/////////////////////////////////////////////////////////////////////////////
int CRevolver_Data::ReadRevolverPos(UINT uRevolverID, UINT* uPos){
	int iRet;
	UINT uTempPos;
	// ID OK ?
	if (uRevolverID > _R_LAST_REVOLVER) return 128;

	if (!m_RevolverType[uRevolverID]) {
		// revolver not available
		*uPos = 0;
		return 128;
	}
	iRet = RevDLL_Get(uRevolverID,&uTempPos);
	if (!iRet) 
		*uPos = uTempPos;
	else 
		*uPos = 0;
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GetRevolverPosName(UINT uRevolverID, UINT uPos, CString* stDescr)
//	Parameter : 
//		uRevolverID : ID for the desired revolver
//		uPos : desired position
//		stDescr : pointer to a string for description
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the description for the  revolverposition
/////////////////////////////////////////////////////////////////////////////
int CRevolver_Data::GetRevolverPosName(UINT uRevolverID, UINT uPos, CString & strPosName ){
	int iRet;
	char buffer[128];
	// ID OK ?
	if (uRevolverID > _R_LAST_REVOLVER) return 128;

	if (!m_RevolverType[uRevolverID]) {
		// revolver not available
		strPosName = "";
		return 128;
	}
	// check uPos
	if (uPos < 1 || uPos >m_RevolverNumberOfPositions[uRevolverID]) {
		// uPos out of range
		strPosName = "";
		return 128;
	}
	iRet = RevDLL_GetPosName(uRevolverID,uPos-1,buffer);
	if (!iRet) 
		strPosName = buffer;
	else 
		strPosName = "";
	return iRet;
}

int CRevolver_Data::GetRevolverName( UINT uRevID, CString & strRevName ) const {

	strRevName = "";
	// ID OK ?
	if ( uRevID > _R_LAST_REVOLVER) 
		return 128;
	if (! m_RevolverType[uRevID]) 	// revolver not available
		return 128;
	
	int iRet;
	LPTSTR pstr = strRevName.GetBuffer( LSTRING );
	iRet = RevDLL_GetRevName( uRevID, pstr );
	strRevName.ReleaseBuffer( );

	return iRet;
}

bool CRevolver_Data::Busy(UINT uRevID)
{
   ASSERT(uRevID <= _R_LAST_REVOLVER);
   int ret = RevDLL_GetStat(uRevID);
   if (ret == _E_NO_ERROR)
      return false;
   else
      return true;
}
