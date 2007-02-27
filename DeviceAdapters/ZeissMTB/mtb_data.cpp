//==========================================================================
//  File:       Mtb_Data.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CMTB_Data
//				(main dataclass for MicroToolbox)
//
//  Copyright:  © CARL ZEISS 1998-2001
//==========================================================================

#include "stdafx.h"
#include "mtb_data.h"
#include "afxdisp.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CMTB_Data::CMTB_Data() {
	// nothing connected
	// no paths available
	m_connected = 0;
	m_IniPath = "";
	m_DLLPath = "";
   m_automatStatus = PS_AUTOMAT_OFF;

   TRACE("CMTB_Data object instantiated!!!\n");

   HRESULT hr = OleInitialize(NULL);
   ASSERT(!FAILED(hr));
}


/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CMTB_Data::~CMTB_Data() {
   try {
	   Disconnect_MTB();
	   m_connected = 0;

      TRACE("CMTB_Data object destroyed!!!\n");

      OleUninitialize();
   }
   catch (COleException* err)
   {
      err->ReportError();
      TRACE("OLE error occured!!!\n");
   }
}

/////////////////////////////////////////////////////////////////////////////
//	Connect_MTB()
//	desciption
//	- if already connected : only beep
//	- determine actual path for configuration file and Toolbox libraries
//	- try to connect all defined devices
//		(in this demonstration not all possible devices are defined)
/////////////////////////////////////////////////////////////////////////////
void CMTB_Data::Connect_MTB() {
	int iError;
   TRACE("CMTB_Data object connected!!!\n");
	if (m_connected) {
		// already connected
		MessageBeep(0);
		return;
	}
	// determine m_IniPath and m_DLLPath
	iError = GetPaths();
	if (iError) {
		// no path found
		MessageBeep(0);
		MessageBox(NULL,"Path not found",NULL,MB_OK);
		return;
	}
	// try to connect all defined devices
	m_revolvers.Connect(m_IniPath, m_DLLPath);
	m_servos.Connect(m_IniPath, m_DLLPath);
	m_lamps.Connect(m_IniPath, m_DLLPath);
	m_focus.Connect(m_IniPath, m_DLLPath);
	m_stage.Connect(m_IniPath, m_DLLPath);
	m_lightpath.Connect(m_IniPath, m_DLLPath);
	m_stand.Connect(m_IniPath, m_DLLPath);
	
	// set flag, that Microtoolbox is connected
	m_connected = 1;

   // turn the light manager off
   m_stand.GetAutomat(&m_automatStatus);
   m_stand.SetAutomat(PS_AUTOMAT_OFF);
}

/////////////////////////////////////////////////////////////////////////////
//	Reload_MTB()
//	desciption
//	- try to disconnect and connect MicroToolbox again
/////////////////////////////////////////////////////////////////////////////
void CMTB_Data::Reload_MTB() {
	Disconnect_MTB();
	Connect_MTB();
}

/////////////////////////////////////////////////////////////////////////////
//	Disonnect_MTB()
//	desciption
//	- try to disconnect all defined devices
//		(in this demonstration not all possible devices are defined)
/////////////////////////////////////////////////////////////////////////////
void CMTB_Data::Disconnect_MTB() {
   try {
      // restore light manager status
      m_stand.SetAutomat(m_automatStatus);

      TRACE("CMTB_Data object disconnected!!!\n");
	   m_revolvers.Disconnect();
	   m_servos.Disconnect();
	   m_lamps.Disconnect();
	   m_focus.Disconnect();
	   m_stage.Disconnect();
	   m_lightpath.Disconnect();
	   m_stand.Disconnect();
    
	   // set flag: Microtoolbox  not connected
	   m_connected = 0;
   }
   catch (COleException* err)
   {
      err->ReportError();
      TRACE("OLE error occured!!!\n");
   }
}

/////////////////////////////////////////////////////////////////////////////
//	GetPaths()()
//
//	return	0 : OK
//			1 : function failed
//	desciption
//	- check the registry to determine
//	   m_IniPath
//	 	 (the filename (with full path) for the actual MicroToolbox-configuration file)
//     m_DLLPath
//		 (the path for the MicroToolbox libraries)
/////////////////////////////////////////////////////////////////////////////
int CMTB_Data::GetPaths() {
	HKEY hKeyMTB;		// Handle for the key "Carl_Zeiss_MTB"
	int ireturn = 0;	// return value  = = OK
	char stBuffer[256];	// buffer for entries
	long length = 256;	// length of the buffer stBuffer

	if(RegOpenKeyEx(HKEY_CLASSES_ROOT, "Carl_Zeiss_MTB", 0, KEY_QUERY_VALUE, &hKeyMTB) != ERROR_SUCCESS)
		return 1;	// Key "Carl_Zeiss_MTB" not found

	if (RegQueryValue(hKeyMTB,"DLL",(LPSTR)stBuffer,&length) == ERROR_SUCCESS) {
		// The key "DLL" contains the path for libraries
		m_DLLPath = stBuffer;
	} else {
		// Key "DLL" not found
		ireturn =  1;
	}
	// reset bufferlength for the next entry
	length = 256; 
	if (RegQueryValue(hKeyMTB,"INI",(LPSTR)stBuffer,&length) == ERROR_SUCCESS) {
		// The key "INI" contains the path for conf. file
		// For the full Filenname add filename "Toolbox.ini" to the path
		m_IniPath = (CString)stBuffer + "\\Toolbox.Ini";
	} else {
		// Key "INI" not found
		ireturn =  1;
	}
	RegCloseKey(hKeyMTB);
	return ireturn;
}
