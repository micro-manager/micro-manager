//==========================================================================
//  File:       Lamp.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CLamp_Data
//				(dataclass for lamp devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "lamp.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif


/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CLamp_Data::CLamp_Data() {
	int i;

	// no library loaded
	m_Library_hinst = NULL;

	// no lamp connected
	for(i=0; i<_PLA_LAST_LAMP; i++) {
		m_LampName[i] = "No lamp connected";
		m_LampMin[i] = 0;
		m_LampMax[i] = 0;
		m_LampType[i] = 0;
        m_LampKind[i] = 0;
	}
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CLamp_Data::~CLamp_Data() {
	// if lamp connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_LampName[.] and m_LampType[ ] to default
/////////////////////////////////////////////////////////////////////////////
void CLamp_Data::Disconnect(){
	UINT i;
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no lamp connected
	for(i=0; i<_PLA_LAST_LAMP; i++) {
		m_LampName[i] = "No lamp connected";
		m_LampMin[i] = 0;
		m_LampMax[i] = 0;
		m_LampType[i] = 0;
      m_LampKind[i] = 0;
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
//	- check lamps
//	- set parameter m_LampName[], m_LampType[], m_LampMin[], m_LampMax[]
//  result :
//	- if connection was successful m_LampType[] != 0
/////////////////////////////////////////////////////////////////////////////
void CLamp_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	int i;
	// if lamps already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Lamp_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
    if (m_Library_hinst) 
    {
        // determine function pointer
        LampDLL_LoadDefParam    =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst,(LPCSTR)"LoadDefLampParam");
        LampDLL_GetInfo    =(int (PASCAL *)(UINT, LPLAMPINFO))GetProcAddress(m_Library_hinst,(LPCSTR)"GetLampInfo");
        LampDLL_GetInfoExt =(int (PASCAL *)( UINT, LPLAMPINFOEXT))GetProcAddress(m_Library_hinst,(LPCSTR)"GetLampInfoExt");
        LampDLL_GetName = (int (PASCAL *)(UINT, LPSTR))GetProcAddress(m_Library_hinst,(LPCSTR)"GetLampName");
        LampDLL_GetState    =(int (PASCAL *)(UINT, UINT*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetLampStatus");
        LampDLL_Get    =(int (PASCAL *)(UINT, double*))GetProcAddress(m_Library_hinst,(LPCSTR)"GetLamp");
        LampDLL_Set    =(int (PASCAL *)(UINT, double))GetProcAddress(m_Library_hinst,(LPCSTR)"SetLamp");
        LampDLL_SetOnOff =(int (PASCAL *)(UINT, BOOL))GetProcAddress(m_Library_hinst,(LPCSTR)"SetLamponoff");
        LampDLL_SetRemote =(int (PASCAL *)(UINT, BOOL))GetProcAddress(m_Library_hinst,(LPCSTR)"SetLampRemote");
        LampDLL_Set3200K =(int (PASCAL *)(UINT, BOOL))GetProcAddress(m_Library_hinst,(LPCSTR)"SetLamp3200K");
        LampDLL_Set3200K_Comp =(int (PASCAL *)(UINT, BOOL))GetProcAddress(m_Library_hinst,(LPCSTR)"SetLamp3200K_KFILTER");
        
        LampDLL_SetLevelIntensity = (int (PASCAL *) (UINT, UINT, double))  GetProcAddress(m_Library_hinst,(LPCSTR)"SetLevelIntensity");
        LampDLL_GetLevelIntensity = (int (PASCAL *) ( UINT, UINT, double*)) GetProcAddress(m_Library_hinst,(LPCSTR)"GetLevelIntensity");
        LampDLL_SetLevel          = (int (PASCAL *) ( UINT, UINT )) GetProcAddress(m_Library_hinst,(LPCSTR)"SetLevel");
        LampDLL_GetLevel          = (int (PASCAL *) ( UINT, UINT*)) GetProcAddress(m_Library_hinst,(LPCSTR)"GetLevel");
        
        
        
    } else {
        // library not available
        return;
	}

	if (LampDLL_LoadDefParam 
		&& LampDLL_GetInfo
        && LampDLL_GetInfoExt
		&& LampDLL_GetName
		&& LampDLL_GetState
		&& LampDLL_Get
		&& LampDLL_Set
		&& LampDLL_SetOnOff
		&& LampDLL_SetRemote
		&& LampDLL_Set3200K
		&& LampDLL_Set3200K_Comp
        && LampDLL_SetLevelIntensity
        && LampDLL_GetLevelIntensity
        && LampDLL_SetLevel
        && LampDLL_GetLevel) 
	{	// if all functions available
		// initialize MicroToolbox library
			LampDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// Check all lamps
	// info-stuct defined in interface definition lamp_api.h
	LAMPINFOEXT lamp_Info;
	for (i=0; i<_PLA_LAST_LAMP; i++) {
		if (LampDLL_GetInfoExt(i,&lamp_Info) == _E_NO_ERROR)
		    if (lamp_Info.uCanIdent) {
			    m_LampType[i]= 2;
			    m_LampMin[i] = lamp_Info.dMinValue_out;
			    m_LampMax[i] = lamp_Info.dMaxValue_out;
			    LampDLL_GetName(i, buffer);
			    m_LampName[i] = buffer;
                m_LampKind[i] = lamp_Info.uLampType;
		    }
	}
}

/////////////////////////////////////////////////////////////////////////////
//	SetLampPos(UINT uLampID, double dPos)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		dPos : desired lamp voltage
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		set a intensity
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLampPos(UINT uLampID, double dPos){
	int iRet;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (m_LampType[uLampID] < 2) {
		// lamp not writable
		return 128;
	}
	iRet = LampDLL_Set(uLampID,dPos);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	ReadLampPos(UINT uLampID, double* dPos)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		dPos : pointer for actual voltage
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the actual lamp voltage
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::ReadLampPos(UINT uLampID, double* dPos){
	int iRet;
	double dTempPos;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (!m_LampType[uLampID]) {
		// lamp not available !
		*dPos = 0;
		return 128;
	}
	iRet = LampDLL_Get(uLampID,&dTempPos);
	if (!iRet) { 
		*dPos = dTempPos;
	} else 
		*dPos = 0;
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	GetLampStatus(UINT uLampID, UINT* uStat)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		uStat : pointer for actual state
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		reads the actual lamp state (see MicroToolbox description)
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::GetLampStatus(UINT uLampID, UINT* uStat){
	int iRet;
	UINT uTmp;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (!m_LampType[uLampID]) {
		// lamp not available !
		*uStat = 1;  // lamp out
		return 128;
	}
	iRet = LampDLL_GetState(uLampID, &uTmp);
	if (!iRet) 
		*uStat = uTmp;
	else 
		*uStat = 1;
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetLampOnOff(UINT uLampID, BOOL bOnOff)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		bOnOff :	TRUE : switch lamp on
//					FALSE : switch lamp off
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		switch the lamp on ore off
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLampOnOff(UINT uLampID, BOOL bOnOff){
	int iRet;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (m_LampType[uLampID]< 2) {
		// lamp not writeable !
		return 128;
	}
	iRet = LampDLL_SetOnOff(uLampID, bOnOff);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetLampRemote(UINT uLampID, BOOL bOnOff)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		bOnOff :	TRUE : switch remote state on
//					FALSE : switch remote state off
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		switch the lamp remote state on ore off
//		(see MicroToolbox description)
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLampRemote(UINT uLampID, BOOL bOnOff){
	int iRet;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (!m_LampType[uLampID]) {
		// lamp not writeable !
		return 128;
	}
	iRet = LampDLL_SetRemote(uLampID, bOnOff);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetLamp3200K(UINT uLampID, BOOL bOnOff)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		bOnOff :	TRUE ore FALSE
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		bOnOff = TRUE : sets the lamp voltage to a defined value 
//			so that the color temperature of the lamp is 3200K
//		bOnOff = FALSE : The lamp is returned to the status in 
//			which it was before the function bOnOff == TRUE was called
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLamp3200K(UINT uLampID, BOOL bOnOff){
	int iRet;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;
	if (!m_LampType[uLampID]) {
		// lamp not writeable !
		return 128;
	}
	iRet = LampDLL_Set3200K(uLampID, bOnOff);
	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	SetLamp3200K(UINT uLampID, BOOL bOnOff)
//	Parameter : 
//		uLampID : ID for the desired lamp
//		bOnOff :	TRUE ore FALSE
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		This function uses compensation filters available in the 
//		filter wheels to determine the 3200 K color temperature 
//		and then sets the lamp voltage to the appropriate value. 
//      bOnOff == TRUE :	Sets the lamp voltage to the determined
//				value so that the color temperature of the lamp is 3200K.
//		bOnOff == FALSE :	The lamp is returned to the status in 
//				which it was before the function bOnOff == TRUE was called.
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLamp3200K_Comp(UINT uLampID, BOOL bOnOff){
	int iRet;
	// check ID
	if (uLampID >= _PLA_LAST_LAMP) return 128;

	if (!m_LampType[uLampID]) {
		// lamp not writeable !
		return 128;
	}
	iRet = LampDLL_Set3200K_Comp(uLampID, bOnOff);
	return iRet;
}



/////////////////////////////////////////////////////////////////////////////
//	SetLevel ( UINT uLampID, UINT uLevel )
//	Parameter : 
//		uLampID : ID for the desired lamp
//		uLevel :  desired level of the AttoArc lamp (1 or 2)
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		This function allows to switch between the levels of
//      the lamp (only available for AttoArc lamp)
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::SetLevel ( UINT uLampID, UINT uLevel )
{
    // check ID
    if (uLampID >= _PLA_LAST_LAMP) return 128;
    
    if (!m_LampType[uLampID]) 
    {
        // lamp not writeable !
        return 128;
    }
    return LampDLL_SetLevel(uLampID, uLevel);
    
}



/////////////////////////////////////////////////////////////////////////////
//	GetLevel ( UINT uLampID, UINT *uLevel )
//	Parameter : 
//		uLampID : ID for the desired lamp
//		uLevel :  currently set level of the AttoArc lamp (1 or 2)
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		This function returns the currently set level of
//      the lamp (only available for AttoArc lamp)
/////////////////////////////////////////////////////////////////////////////
int CLamp_Data::GetLevel ( UINT uLampID, UINT *uLevel )
{
    // check ID
    if (uLampID >= _PLA_LAST_LAMP) return 128;
    
    if (!m_LampType[uLampID]) 
    {
        // lamp not writeable !
        return 128;
    }
    return LampDLL_GetLevel(uLampID, uLevel);
    
}