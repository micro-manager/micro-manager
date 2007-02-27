//==========================================================================
//  File:       L_Path.cpp
//  Project:    Demo.mak
//
//  Purpose:    Implementation file for the class CLightPath_Data
//				(dataclass for lightpath device )
//
//  Copyright:  © CARL ZEISS 1998
//==========================================================================

#include "stdafx.h"
#include "l_path.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
//	constructor
/////////////////////////////////////////////////////////////////////////////
CLightPath_Data::CLightPath_Data() {

	// no library loaded
	m_Library_hinst = NULL;

	// no device connected
	m_DeviceName = "no light path control connected";
	m_DeviceType = 0;
}

/////////////////////////////////////////////////////////////////////////////
//	destructor
/////////////////////////////////////////////////////////////////////////////
CLightPath_Data::~CLightPath_Data() {
	// if device connected : disconnect
	Disconnect();
}

/////////////////////////////////////////////////////////////////////////////
//	Disconnect
//	unload MicroToolbox library
//	set parameter m_DeviceName and m_DeviceType to default
/////////////////////////////////////////////////////////////////////////////
void CLightPath_Data::Disconnect(){
	// unload MicroToolbox library
	if (m_Library_hinst) FreeLibrary(m_Library_hinst);
	m_Library_hinst = NULL;

	// no device connected
	m_DeviceName = "no light path control connected";
	m_DeviceType = 0;
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
//	- check lightpath device
//	- set parameter m_DeviceName and m_DeviceType
//  result :
//	- if connection was successful m_DeviceType != 0
/////////////////////////////////////////////////////////////////////////////
void CLightPath_Data::Connect(CString IniFile, CString DLLPath){
	char buffer[100];
	int i;
	// if device already connected : disconnect
	Disconnect();

	// determine the name of the MicroToolbox library
	::GetPrivateProfileString ( "Libraries", "Lightpath_dll", "", buffer, sizeof(buffer), IniFile );

	// try to load library
	m_Library_hinst = LoadLibrary(DLLPath + "\\" + buffer);
	if (m_Library_hinst) // load lbrary OK
	{
       // determine function pointer
		PathDLL_LoadDefParam =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst, "LoadDefLightPathParam");  
		PathDLL_GetLightPathInfo =(int (PASCAL *)(LPLightPathInfo))GetProcAddress(m_Library_hinst, "GetLightPathInfo");
		PathDLL_GetAllOutletID =(int (PASCAL *)(int*))GetProcAddress(m_Library_hinst, "GetAllOutletID");
		PathDLL_GetAllBeamID =(int (PASCAL *)(int*))GetProcAddress(m_Library_hinst, "GetAllBeamID");
		PathDLL_GetAllBeamSplittingID =(int (PASCAL *)(int*))GetProcAddress(m_Library_hinst, "GetAllBeamSplittingID");
		PathDLL_GetOutletInfo =(int (PASCAL *)(int, LPOutletInfo))GetProcAddress(m_Library_hinst, "GetOutletInfo");
		PathDLL_GetBeamInfo =(int (PASCAL *)(int, LPSTR))GetProcAddress(m_Library_hinst, "GetBeamInfo");
		PathDLL_GetBeamSplittingInfo =(int (PASCAL *)(int, LPSTR))GetProcAddress(m_Library_hinst, "GetBeamSplittingInfo"); 
		PathDLL_SetOutlet =(int (PASCAL *)(int, int))GetProcAddress(m_Library_hinst, "SetOutlet");
		PathDLL_GetActualOutlet =(int (PASCAL *)(int, int*, int*))GetProcAddress(m_Library_hinst, "GetActualOutlet");
		PathDLL_GetOutletState =(int (PASCAL *)(int, int*, int*))GetProcAddress(m_Library_hinst, "GetOutletState");
		PathDLL_GetLightPathWorkState =(int (PASCAL *)(void))GetProcAddress(m_Library_hinst, "GetLightPathWorkState");
	} else {
		// library not available
		return;
	}

	if (PathDLL_LoadDefParam 
		&& PathDLL_GetLightPathInfo
		&& PathDLL_GetAllOutletID
		&& PathDLL_GetAllBeamID
		&& PathDLL_GetAllBeamSplittingID
		&& PathDLL_GetOutletInfo
		&& PathDLL_GetBeamInfo
		&& PathDLL_GetBeamSplittingInfo
		&& PathDLL_SetOutlet
		&& PathDLL_GetActualOutlet
		&& PathDLL_GetOutletState
		&& PathDLL_GetLightPathWorkState) 
	{	// if all functions available
		// initialize MicroToolbox library
			PathDLL_LoadDefParam();
	} else 
	{	// if not all functions available
		// free MicroToolbox library
		Disconnect();
		return;
	}

	// Check light path control
	m_Info.nNumberOfOutlets = 0;
	m_Info.nNumberOfBeams = 0;
	m_Info.nNumberOfBeamSplittings = 0;
	// read Info structure
	PathDLL_GetLightPathInfo(&m_Info);
	if (m_Info.nType == 1 || m_Info.nType == 2) {
		m_DeviceType= m_Info.nType;
		m_DeviceName = "";

		// read all outlet-ID's
		PathDLL_GetAllOutletID(m_Outlets);
		for (i=0; i<m_Info.nNumberOfOutlets && i<_PH_MAXOUTLET ; i++) 
		{
			// read outlet-Info-structure
			PathDLL_GetOutletInfo(m_Outlets[i],&(m_OutletTab[i]));
		}

		// read all beam-ID's
		PathDLL_GetAllBeamID(m_Beams);
		for (i=0; i<m_Info.nNumberOfBeams && i < _PH_MAXBEAM; i++) {
			// read beam desciption
			if (PathDLL_GetBeamInfo(m_Beams[i],buffer)== 0) 
				m_BeamTab[i] = buffer;
			else 
				m_BeamTab[i] = "";
		}

		// read all splitting-pos. ID's 
		PathDLL_GetAllBeamSplittingID(m_Splitpos);
		for (i=0; i<m_Info.nNumberOfBeamSplittings && i < _PH_MAXSPLITPOS; i++) {
			// read description for splitting pos
			if (PathDLL_GetBeamSplittingInfo(m_Splitpos[i],buffer)== 0) 
				m_SplitPosTab[i] = buffer;
			else 
				m_SplitPosTab[i] = "";
		}
	}
}

/////////////////////////////////////////////////////////////////////////////
//	SetOutlet(int nOutletID, int nSplitterPos)
//	Parameter : 
//		nOutletID :		outlet-ID
//		nSplitterPos	splittingpos-ID
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		activates the desired outlet
/////////////////////////////////////////////////////////////////////////////
int CLightPath_Data::SetOutlet(int nOutletID, int nSplitterPos){
	int iRet;
	if (m_DeviceType != 2) {
		// not motorized !
		return 128;
	}
	iRet = PathDLL_SetOutlet(nOutletID, nSplitterPos);
	TRACE ("Set Outlet = %d, Beam = %d\n",nOutletID, nSplitterPos);

	return iRet;
}

/////////////////////////////////////////////////////////////////////////////
//	ReadOutlet(int nBeam, int* nOutletID, int* nSplitterPos){
//	Parameter : 
//		nBeam :			beam-ID, for which beam the outlet will be determined
//		nOutletID :		pointer for outlet-ID
//		nSplitterPos :	pointer for splittingpos-ID
//	return :
//		0 OK
//		other : error (see MicroToolbox description)
//  description : 
//		determines the active outlet and splittin position for the desired beam
/////////////////////////////////////////////////////////////////////////////
int CLightPath_Data::ReadOutlet(int nBeam, int* nOutletID, int* nSplitterPos){
	int iRet;
	int nTempOutlet, nTempSplitterPos;
	if (!m_DeviceType) {
		// not available
		*nOutletID = 0;
		*nSplitterPos = 0;
		return 128;
	}
	// Toolbox-command for reading
	iRet = PathDLL_GetActualOutlet(nBeam, &nTempOutlet, &nTempSplitterPos);
	if (!iRet) {
		// no error
		*nOutletID = nTempOutlet;
		*nSplitterPos = nTempSplitterPos;
	} else  {
		// error
		*nOutletID = 0;
		*nSplitterPos = 0;
	}
	return iRet;
}

