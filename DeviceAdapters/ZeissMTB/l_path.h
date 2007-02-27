//==========================================================================
//  File:       L_Path.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CLightPath_Data
//				(dataclass for lightpath devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef L_PATH_H
#define L_PATH_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/path_api.h"


class CLightPath_Data
{
public:
	// constructor - destructor
	CLightPath_Data();
	~CLightPath_Data();
	// log and unlog lightpath device via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// desciption
	CString m_DeviceName;
	// typeof lpath device  (1 = encoded, 2 = motorized, 0 = not available
	UINT m_DeviceType;

	// public functions to contol the z-drive
	int SetOutlet(int nOutletID, int nSplitterPos);
	int ReadOutlet(int nBeam, int* nOutletID, int* nSplitterPos);

private:

	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-Interface-functions
	int (WINAPI *PathDLL_LoadDefParam  )(void);  
	int (WINAPI *PathDLL_GetLightPathInfo)(LPLightPathInfo Info);
	int (WINAPI *PathDLL_GetAllOutletID)(int* ID_field);
	int (WINAPI *PathDLL_GetAllBeamID)(int* ID_field);
	int (WINAPI *PathDLL_GetAllBeamSplittingID)(int* ID_field);
	int (WINAPI *PathDLL_GetOutletInfo)(int nOutletID, LPOutletInfo Info);
	int (WINAPI *PathDLL_GetBeamInfo)(int nBeamID, LPSTR stdescr);
	int (WINAPI *PathDLL_GetBeamSplittingInfo)(int nSplitID, LPSTR stdescr); 
	int (WINAPI *PathDLL_SetOutlet)(int nOutletID, int nSplitterPos);
	int (WINAPI *PathDLL_GetActualOutlet)(int nBeam, int* nOutletID, int* nSplitterPos);
	int (WINAPI *PathDLL_GetOutletState)(int nOutletID, int* nBeam, int* nPercent);
	int (WINAPI *PathDLL_GetLightPathWorkState)(void);
public:
	// Info-structure for lightpath devices
	// definition LightPathInfo: see "path_api.h"
	LightPathInfo m_Info;

	// array of Infostructures for all outlets
	// definition OutletInfo: see "path_api.h"
	OutletInfo m_OutletTab[_PH_MAXOUTLET];

	// array for descriptions for all beams
	CString m_SplitPosTab[_PH_MAXBEAM];

	// array for descriptions for all splitpositions
	CString m_BeamTab[_PH_MAXSPLITPOS];

	// array for all outlet-ID's
	int m_Outlets[_PH_MAXOUTLET];

	// array for all beam-ID's
	int m_Beams[_PH_MAXBEAM];

	// array for all splitposition-ID's
	int m_Splitpos[_PH_MAXSPLITPOS];
};

#endif //L_PATH_H
