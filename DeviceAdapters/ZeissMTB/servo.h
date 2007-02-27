//==========================================================================
//  File:       Servo.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CServo_Data
//				(dataclass for servo devices)
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#ifndef SERVO_H
#define SERVO_H

// insert interface definitions of the MicroToolbox
#include "Zeiss/MTB/API/serv_api.h"


class CServo_Data
{
public:
	// constructor - destructor
	CServo_Data();
	~CServo_Data();
	// log and unlog lamp control via MicroToolbox 
	void Connect(CString Inipath, CString DLLPath);
	void Disconnect();

	// desciption for each servo
	CString m_ServoName[_PL_LAST_SERVO+1];
	// min. and max of available positions
	double m_ServoMin[_PL_LAST_SERVO];
	double m_ServoMax[_PL_LAST_SERVO];
	// type  (1 = encoded, 2 = motorized, 0 = not available
	UINT m_ServoType[_PL_LAST_SERVO];

	// public functions to control the servo devices
	int SetServoPos(UINT uServoID, double dPos);
	int ReadServoPos(UINT uServoID, double* dPos);
	int GoServo(UINT uServoID, int iDir);
	int StopServo(UINT uServoID);


private:
	// Instance-handle of the MicroToolbox-DLL
	HINSTANCE m_Library_hinst;

	// function-pointer for MicroToolbox-interface-functions
	int (WINAPI *ServoDLL_LoadDefParam  )(void);  
	int (WINAPI *ServoDLL_GetInfo  )( UINT uRevNbr,LPSERVOINFO pInfo);  
	int (WINAPI *ServoDLL_GetName  )( UINT uServoNbr, LPSTR str);  
	int (WINAPI *ServoDLL_Check  )( UINT uServoNbr);  
	int (WINAPI *ServoDLL_Get  )( UINT uServoNbr, double* dPos);  
	int (WINAPI *ServoDLL_Set  )( UINT uServoNbr, double dPos);  
	int (WINAPI *ServoDLL_Go  )( UINT uServoNbr, int iSpeed);  
	int (WINAPI *ServoDLL_Stop)( UINT uServoNbr);  
};

#endif //SERVO_H
