///////////////////////////////////////////////////////////////////////////////
// FILE:          PICAMAdapter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PICAM camera module
//                
// AUTHOR:        Toshio Suzuki
//
// PORTED from    PVCAMAdapter.cpp
//                (AUTHOR:        Nico Stuurman, Nenad Amodaj nenad@amodaj.com, 09/13/2005)
//                (COPYRIGHT:     University of California, San Francisco, 2006)
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include "ModuleInterface.h"
#include "PICAMAdapter.h"

#ifdef WIN64
#pragma warning(push)
#include "picam.h"
#pragma warning(pop)
#endif

#ifdef __APPLE__
#define __mac_os_x
#endif

#ifdef linux
#endif

#include <string>
#include <sstream>
#include <iomanip>


using namespace std;

// global constants
#define	MIN_CAMERAS	4
char g_DeviceName[MIN_CAMERAS][128];

const char* g_ReadoutRate = "ReadoutRate";
const char* g_ReadoutRate_Slow = "Slow";
const char* g_ReadoutRate_Fast = "Fast";
const char* g_ReadoutPort = "Port";
const char* g_ReadoutPort_Normal = "Normal";
const char* g_ReadoutPort_Multiplier = "EM";
const char* g_ReadoutPort_LowNoise = "LowNoise";
const char* g_ReadoutPort_HighCap = "HighCap";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	piint demoList[] = { PicamModel_Pixis1024B, PicamModel_Nirvana640, PicamModel_ProEM1024B, PicamModel_Pylonir102417  };
	const pichar *sn[] = { "1000000001", "1000000002", "1000000003" , "1000000004" };
    piint numCamsAvailable = 0;
    const PicamCameraID *camID;
	piint numDemos = 0;
    PicamCameraID *demoID = NULL;


   /*	Initialize PICAM */
	if (Picam_InitializeLibrary()==PicamError_None){
		Picam_GetAvailableCameraIDs( &camID, &numCamsAvailable );

		//	Add demo Camera
		if( numCamsAvailable < MIN_CAMERAS )
		{
			numDemos = MIN_CAMERAS - numCamsAvailable;
			demoID = (PicamCameraID*) malloc( sizeof( PicamCameraID ) * numDemos );
		}
		piint count = 0;
		while( numCamsAvailable < MIN_CAMERAS )
		{
			//need a minimum of MIN_CAMERAS(2) for multi-camera example
			Picam_ConnectDemoCamera( (PicamModel)demoList[count], sn[count], &demoID[count] );
			++numCamsAvailable;
			++count;
		}

		// may get 4 cameras
		Picam_GetAvailableCameraIDs( &camID, &numCamsAvailable );

	    const pichar* string;

		Picam_GetEnumerationString( PicamEnumeratedType_Model, camID[0].model, &string );
		RegisterDevice(string, MM::CameraDevice, camID[0].serial_number);
		strcpy(g_DeviceName[0], string);
	    Picam_DestroyString( string );

		Picam_GetEnumerationString( PicamEnumeratedType_Model, camID[1].model, &string );
		RegisterDevice(string, MM::CameraDevice, camID[1].serial_number);
		strcpy(g_DeviceName[1], string);
	    Picam_DestroyString( string );


		Picam_GetEnumerationString( PicamEnumeratedType_Model, camID[2].model, &string );
		RegisterDevice(string, MM::CameraDevice, camID[2].serial_number);
		strcpy(g_DeviceName[2], string);
	    Picam_DestroyString( string );

		Picam_GetEnumerationString( PicamEnumeratedType_Model, camID[3].model, &string );
		RegisterDevice(string, MM::CameraDevice, camID[3].serial_number);
		strcpy(g_DeviceName[3], string);
	    Picam_DestroyString( string );

		Picam_DestroyCameraIDs( camID );

		Picam_UninitializeLibrary();
	}
	else{
		strcpy(g_DeviceName[0], "Error1");
		strcpy(g_DeviceName[1], "Error2");
		strcpy(g_DeviceName[2], "Error3");
		strcpy(g_DeviceName[3], "Error4");
		RegisterDevice(g_DeviceName[0], MM::CameraDevice, "Universal PICAM interface - camera 1");
		RegisterDevice(g_DeviceName[1], MM::CameraDevice, "Universal PICAM interface - camera 2");
		RegisterDevice(g_DeviceName[2], MM::CameraDevice, "Universal PICAM interface - camera 3");
		RegisterDevice(g_DeviceName[3], MM::CameraDevice, "Universal PICAM interface - camera 4");
	}
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   for (short i = 0; i < 4; ++i)
   {
      if (strcmp(deviceName, g_DeviceName[i]) == 0)
         return new Universal(i, deviceName);
   }
   return 0;
}
