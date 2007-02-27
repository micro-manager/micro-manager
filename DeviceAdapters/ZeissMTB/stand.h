//==========================================================================
//  File:       Stand.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CStand_Data
//				(dataclass for stand control)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef STAND_H
#define STAND_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/stat_api.h"


class CStand_Data
{
public:
	// constructor - destructor
	CStand_Data();
	~CStand_Data();

	// log and unlog stand control via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// public functions for stand control
	int GetFirmwareVersion(CString& stversion);  
	int GetFocusFirmwareVersion(CString& stversion);  
	int GetChangedComponents(UINT* uState);  
	int SetAutomat(UINT uStat);  
	int GetAutomat(UINT* uStat);  

private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-interface-functions
	int (WINAPI *StandDLL_LoadDefParam  )(void);  
	int (WINAPI *StandDLL_GetInfo  )(LPSTATIVINFO pInfo);  
	int (WINAPI *StandDLL_GetFirmwareVersion  )(LPSTR str);  
	int (WINAPI *StandDLL_GetFocusFirmwareVersion  )(LPSTR str);  
	int (WINAPI *StandDLL_GetChangedComponents  )( UINT* uState);  
	int (WINAPI *StandDLL_SetAutomat  )(UINT uStat);  
	int (WINAPI *StandDLL_GetAutomat  )(UINT* uStat);  
public :

	STATIVINFO m_Info;			// infostruct typdef. in stat_api.h
	BOOL m_Stand_available;		// TRUE if stand-control available
	CString m_MicroscopeName;	// Microscopname
};

#endif //STAND_H
