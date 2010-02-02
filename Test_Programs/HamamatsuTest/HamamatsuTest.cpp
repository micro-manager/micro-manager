// HamamatsuTest.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include <windows.h>
#include "../../../3rdparty/Hamamatsu/DCAMSDK/2004-10/inc/dcamapi.h"

int _tmain(int argc, _TCHAR* argv[])
{
	long nCameras = 0;
   HINSTANCE hInstance = GetModuleHandle(NULL);
	if (!dcam_init( hInstance, &nCameras, NULL) && nCameras)
      return 1;
		
   
   HDCAM	hDCAM = NULL;
   if (!dcam_open(&hDCAM, 0))
      return 2;

   if (!dcam_precapture(hDCAM, ccCapture_Snap))
      return 3;

   if (!dcam_allocframe(hDCAM, 1))
      return 4;

   DWORD event = DCAM_EVENT_CYCLEEND;
   if (!dcam_capture(hDCAM) && dcam_wait(hDCAM, &event, INFINITE))
      return 5;

	return 0;
}

