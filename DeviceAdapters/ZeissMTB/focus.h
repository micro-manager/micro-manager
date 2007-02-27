//==========================================================================
//  File:       Focus.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CFocus_Data
//				(dataclass for z-drive)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef FOCUS_H
#define FOCUS_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/foc_api.h"


class CFocus_Data
{
public:
	// constructor - destructor
	CFocus_Data();
	~CFocus_Data();
	// log and unlog z-drive via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// desciption
	CString m_FocusName;
	// typeof z-drive  (1 = encoded, 2 = motorized, 0 = not available
	UINT m_FocusType;
   double m_FocusStep;
   double m_ZMin;
   double m_ZMax;

	// public functions to control the z-drive
	int MoveFocus(double dPos);
	int ReadFocusPos(double* dPos);
	int GoFocus(double sSpeed);
	int StopFocus();
	int SetLoadWork(UINT uPos);
	int GetLoadWork(UINT* uPos);
	int FocInit();
   bool Busy();


private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-Interface-functions
	int (WINAPI *FocusDLL_LoadDefParam  )(void);  
	int (WINAPI *FocusDLL_GetInfo  )( LPFOCINFO pInfo);  
	int (WINAPI *FocusDLL_Move  )( double dPos);  
	int (WINAPI *FocusDLL_Go  )( double dSpeed, unsigned flag);  
	int (WINAPI *FocusDLL_Get  )( double* dPos);  
	int (WINAPI *FocusDLL_Stop  )();  
	int (WINAPI *FocusDLL_SetLoadWork  )(UINT uPos);  
	int (WINAPI *FocusDLL_GetLoadWork  )(UINT* uPos);  
	int (WINAPI *FocusDLL_Init  )();
   int (WINAPI *FocusDLL_GetFocLimits) (double* zmin, double* zmax);
   int (WINAPI *FocusDLL_GetFocStat) ();
};

#endif //FOCUS_H
