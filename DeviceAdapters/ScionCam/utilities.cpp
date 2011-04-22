//////////////////////////////////////////////////////////////////////////////////////
//
//
//	ScionCam -	mm_manager device adapter for scion 1394 cameras
//
//	Version	1.3
//
//	Copyright 2004-2009 Scion Corporation  		(Win XP/Vista, OS/X Platforms)
//
//	Implemented using Micro-Manager DemoCamera module as a baseline
//	Micro-Manager is copyright of University of California, San Francisco.
//
//////////////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// FILE:          utilties.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Scion Firewire Camera Device Adapter 
//                
// AUTHOR:        Scion Corporation, 2009
//
// COPYRIGHT:     Scion Corporation, 2004-2009
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
//
// CVS:           $Id: utilities.cpp,v 1.33 2009/08/19 22:40:57 nenad Exp $
//													

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include	"ScionCamera.h"

// local references

void	sLogMessage(char msg[]);
void	sLogReset(void);


//----------------------------------------------------------------------------
//
//	LogMessage - routine to write message to log plug-in logfile
//
//----------------------------------------------------------------------------

void sLogMessage(char msg[])
{ 
#ifdef	LOG_ENABLED

#ifdef	WIN32
HFILE file;
OFSTRUCT of;

if (OpenFile("mm_sfwcam.log",&of,OF_EXIST)==HFILE_ERROR)
    file = OpenFile("mm_sfwcam.log",&of,OF_CREATE|OF_WRITE);
else
    file = OpenFile("mm_sfwcam.log",&of,OF_WRITE);

_llseek(file,0,FILE_END);
_lwrite(file,msg,lstrlen(msg));
_lclose(file);

#else
FILE     *fp;
  
fp = fopen("mm_sfwcam.log", "a+");
if (fp != NULL)
    {
    fprintf(fp, "%s\n", msg);
    fclose(fp);
    }
#endif

#endif

return;
}


//----------------------------------------------------------------------------
//
//	LogReset - routine to reset plug-in logfile
//
//----------------------------------------------------------------------------

void sLogReset(void)
{  
#ifdef	LOG_ENABLED

#ifdef	WIN32
HFILE		file;
OFSTRUCT	of;

if((file = OpenFile("mm_sfwcam.log", &of, OF_DELETE)) != HFILE_ERROR)
	{
	_lclose(file);		
	} 

#else
FILE     *fp;
  
fp = fopen("mm_sfwcam.log", "w");
if (fp != NULL)
    {
    fseek(fp, 0l, SEEK_SET);
    fclose(fp);
    }
#endif

#endif

return;
}


