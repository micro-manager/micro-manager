//==========================================================================
//  File:       Mtb_Data.h
//  Project:    Demo.mak
//
//  Purpose:    Header file for the class CMTB_Data
//				(main dataclass for MicroToolbox)
//
//  Copyright:  © CARL ZEISS 1998-2001
//==========================================================================

#ifndef MTB_DATA_H
#define MTB_DATA_H

// include declarations of dataclasses for different devices
#include "revolver.h"
#include "servo.h"
#include "lamp.h"
#include "focus.h"
#include "stage.h"
#include "l_path.h"
#include "stand.h"



class CMTB_Data
{
public:
	// constructor, destructor
	CMTB_Data();
	~CMTB_Data();
	// flag m_connected	= 1	: MicroToolbox is successful connected
	//					= 0 : MicroToolbox not connected
	int m_connected;

	// connection functions for the MicroToolbox
	////////////////////////////////////////////
	void Connect_MTB();		// try connect MicroToolbox
	void Disconnect_MTB();	// disconnect MicroToolbox
	void Reload_MTB();		// disconnect and connect MicroToolbox again

	// dataclasses for different devices
	////////////////////////////////////////////
	CRevolver_Data m_revolvers;	// for revolvers
	CServo_Data m_servos;		// for servos
	CLamp_Data m_lamps;			// for lamps
	CFocus_Data m_focus;		// for z-drive
	CStage_Data m_stage;		// for xy-stages
	CLightPath_Data m_lightpath;// for lightpath devices	
	CStand_Data m_stand;		// for stand devices

   UINT m_automatStatus;
	
private:
	// filename (with full path) for the actual MicroToolbox-configuration file
	CString m_IniPath;
	// path for the MicroToolbox libraries
	CString m_DLLPath;
	// determine m_Inipath and m_DLLPath
	int GetPaths();
};

#endif // MTB_DATA_H

