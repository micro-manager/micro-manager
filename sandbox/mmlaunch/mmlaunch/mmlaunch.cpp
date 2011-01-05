// mmlaunch.cpp : Defines the entry point for the application.
//

#include "stdafx.h"
#include "mmlaunch.h"
#include <process.h>


int APIENTRY _tWinMain(HINSTANCE hInstance,
                     HINSTANCE hPrevInstance,
                     LPTSTR    lpCmdLine,
                     int       nCmdShow)
{
	UNREFERENCED_PARAMETER(hPrevInstance);
	UNREFERENCED_PARAMETER(lpCmdLine);

 	_execl("C:\\Progra~1\\Java\\jre6\\bin\\javaw","C:\\Progra~1\\Java\\jre6\\bin\\javaw", "-Xmx1000M -cp ij.jar ij.ImageJ", NULL);
 	//system("javaw -cp ij.jar ij.ImageJ");

	return 0;
}

