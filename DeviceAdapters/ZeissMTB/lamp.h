//==========================================================================
//  File:       Lamp.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CLamp_Data
//				(dataclass for lamp devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef LAMP_H
#define LAMP_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/lamp_api.h"


class CLamp_Data
{
public:
	// constructor - destructor
	CLamp_Data();
	~CLamp_Data();
	// log and unlog lamp control via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// desciption
	CString m_LampName[_PLA_LAST_LAMP];

	// min. and max of available lamp voltage
	double m_LampMin[_PLA_LAST_LAMP];
	double m_LampMax[_PLA_LAST_LAMP];

	// type of lamp  (1 = read only, 2 = read/write, 0 = not available
	UINT m_LampType[_PLA_LAST_LAMP];

  	// kind of lamp  (0=unknown 1=Halogen, 2=AttoArc2, 3= FluoArc
	UINT m_LampKind[_PLA_LAST_LAMP];

	// public functions to control the lamp devices
	int SetLampPos(UINT uLampID, double dPos);
	int ReadLampPos(UINT uLampID, double* dPos);
	int GetLampStatus(UINT uLampID, UINT* uStat);
	int SetLampOnOff(UINT uLampID, BOOL bOn);
	int SetLampRemote(UINT uLampID, BOOL bOn);
	int SetLamp3200K(UINT uLampID, BOOL bOn);
    int SetLamp3200K_Comp(UINT uLampID, BOOL bOn);
    int SetLevel ( UINT uLampID, UINT uLevel );
    int GetLevel ( UINT uLampID, UINT *uLevel );


private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-interface-functions
	int (WINAPI *LampDLL_LoadDefParam  )(void);  
	int (WINAPI *LampDLL_GetInfo  )( UINT uLampNbr,LPLAMPINFO pInfo);  
	int (WINAPI *LampDLL_GetInfoExt  )( UINT uLampNbr,LPLAMPINFOEXT pInfo);  
	int (WINAPI *LampDLL_GetName  )( UINT uLampNbr, LPSTR str);  
	int (WINAPI *LampDLL_GetState  )( UINT uLampNbr, UINT* uState);  
	int (WINAPI *LampDLL_Get  )( UINT uLampNbr, double* dPos);  
	int (WINAPI *LampDLL_Set  )( UINT uLampNbr, double dPos);  
	int (WINAPI *LampDLL_SetOnOff  )( UINT uLampNbr, BOOL bOn);  
	int (WINAPI *LampDLL_SetRemote  )( UINT uLampNbr, BOOL bOn);  
	int (WINAPI *LampDLL_Set3200K  )( UINT uLampNbr, BOOL bOn);  
	int (WINAPI *LampDLL_Set3200K_Comp  )( UINT uLampNbr, BOOL bOn);  

    int (WINAPI *LampDLL_SetLevelIntensity)    ( UINT uLamp, UINT Level, double dPos );
    int (WINAPI *LampDLL_GetLevelIntensity)    ( UINT uLamp, UINT Level, double *dPos );
    int (WINAPI *LampDLL_SetLevel)             ( UINT uLamp, UINT Level );
    int (WINAPI *LampDLL_GetLevel)             ( UINT uLamp, UINT *Level );

};

#endif //SERVO_H
