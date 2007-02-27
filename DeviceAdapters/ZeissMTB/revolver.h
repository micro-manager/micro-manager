//==========================================================================
//  File:       Revolver.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CRevolver_Data
//				(dataclass for revolver devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef REVOLVER_H
#define REVOLVER_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/rev_api.h"


class CRevolver_Data
{
public:
	enum { REVMAX = MTB_CHG, LSTRING = 128 };

	// constructor - destructor
	CRevolver_Data();
	~CRevolver_Data();
	// log and unlog revolver devices via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// description of each revolver
	CString m_RevolverName[_R_LAST_REVOLVER+1];
	// number of revolver positions of each revolver
	UINT m_RevolverNumberOfPositions[_R_LAST_REVOLVER];
	// type of revolver  (1 = encoded, 2 = motorized, 0 = not available
	// of each revolver
	UINT m_RevolverType[_R_LAST_REVOLVER];

    // revolver has intermediate positions?
	UINT m_RevolverHasInterPos[_R_LAST_REVOLVER];

	// public functions for revolver devices
	int SetRevolverPos(UINT uRevID, UINT uPos);
	int ReadRevolverPos(UINT uRevID, UINT * uPos);
	int GetRevolverPosName(UINT uRevID, UINT uPos, CString & strPosName );
	int GetRevolverName(UINT uRevID, CString & strRevName ) const;
   bool Busy(UINT uRevID);

private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-interface-functions
	int (WINAPI *RevDLL_LoadDefParam  )(void);  
	int (WINAPI *RevDLL_GetStat  )( UINT uRevNbr);  
	int (WINAPI *RevDLL_Check  )( UINT uRevNbr);  
	int (WINAPI *RevDLL_Get  )( UINT uRevNbr, UINT* uPos);  
	int (WINAPI *RevDLL_Set  )( UINT uRevNbr, UINT uPos);  
	int (WINAPI *RevDLL_GetPositions  )( UINT uRevNbr, UINT* uNbr);  
	int (WINAPI *RevDLL_GetInfoExt  )( UINT uRevNbr, riRev_Info_TypeExt *pInfo);  
	int (WINAPI *RevDLL_SetSecurity )( UINT uSecVal);  
	int (WINAPI *RevDLL_GetRevName  )( UINT uRevNbr, char* sDescr);  
	int (WINAPI *RevDLL_GetPosName  )( UINT uRevNbr, UINT uPosNbr, char* sDescr);  
}; 

#endif //REVOLVER_H
