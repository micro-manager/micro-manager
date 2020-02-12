///////////////////////////////////////////////////////////////////////////////
// FILE:          Spectra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Lumencore Light Engine driver
//                Spectra
//
// AUTHOR:        Louis Ashford (adapted from sample code provided for MicroManager)
// COPYRIGHT:     Ashford Solutions LLC
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

#ifdef WIN32
   #include <windows.h>
   #include <iostream>
#endif
#include "FixSnprintf.h"

#include <string>
#include <math.h>
#include <sstream>

char EnableMask = 0x7f;
enum LEType {Aura_Type,Sola_Type,Spectra_Type,SpectraX_Type};
LEType LightEngine = Spectra_Type; // Light Engine Type
using namespace std;

 
/****************************************************************************
* DBGprintf
*
* This debugging function prints out a string to the debug output.
* An optional set of substitutional parameters can be specified,
* and the final output will be the processed result of these combined
* with the format string, just like printf.  A newline is always
* output after every call to this function.
*
* Arguments:
*   LPTSTR fmt - Format string (printf style).
*   ...        - Variable number of arguments.
* Returns:
*    VOID
\****************************************************************************/

#ifdef Win32
void DbgPrintf(LPTSTR fmt,...    )
{
    va_list marker;
    char szBuf[256];
 
    va_start(marker, fmt);
    //wvsprintf(szBuf, fmt, marker);
    //vswprintf(szBuf, fmt, marker);
	vsprintf(szBuf, fmt, marker);
	va_end(marker);
 
    //OutputDebugString(szBuf);
    //OutputDebugString(TEXT("\r\n"));
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Lumencor

// *****************************************************************************
// Sends color level command to Lumencor LightEngine
// *****************************************************************************
int SendColorLevelCmd(ColorNameT ColorName,int ColorLevel)
{
	unsigned int ColorValue;
	unsigned char DACSetupArray[]= "\x53\x18\x03\x00\x00\x00\x50\x00";
	unsigned char CmdPrefix[]="#ZS9";
	std::string Cmd;

	ColorValue = 255-(unsigned int)(2.55 * ColorLevel); // map color to range
	ColorValue &= 0xFF;  // Mask to one byte
	ColorValue = (ColorLevel == 100) ? 0 : ColorValue;
	ColorValue = (ColorLevel == 0) ? 0xFF : ColorValue;  // coherce to correct values at limits
	if(LightEngine == Sola_Type)
	{
		ColorName = ALL;
	}
	switch(ColorName)
	{
		case  RED:
			DACSetupArray[3] = 0x08;
			break;
		case  GREEN:
			DACSetupArray[3] = 0x04;
			break;
		case  VIOLET:
			DACSetupArray[3] = 0x01;
			break;
		case  CYAN:
			DACSetupArray[3] = 0x02;
			break;
		case  BLUE:
			DACSetupArray[3] = 0x01;
			break;
		case  TEAL:
			DACSetupArray[3] = 0x02;
			break;
		case ALL:
		case WHITE:
			DACSetupArray[4] = (char) ((ColorValue >> 4) & 0x0F) | 0xF0;
			DACSetupArray[5] = (char) (ColorValue << 4) & 0xF0;
			DACSetupArray[3] = 0x0F; // setup for RGCV 
			DACSetupArray[1] = 0x18;
			WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device

			DACSetupArray[3] = 0x03; // BT
			DACSetupArray[1] = 0x1A;
			WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
			break;
		default:
			break;		
	}
	if(ColorName != ALL && ColorName != WHITE)
	{
		DACSetupArray[4] = (char) ((ColorValue >> 4) & 0x0F) | 0xF0;
		DACSetupArray[5] = (char) (ColorValue << 4) & 0xF0;

		WriteToComPort(port_.c_str(),CmdPrefix,4);      // write command prefix
		WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
		WriteToComPort(port_.c_str(),"\n",1);           // write ending linefeed
	}
	// block/wait no acknowledge so just give it time                      
	// CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}



// *****************************************************************************
// Sends color Enable/Disable command to Lumencor LightEngine
// *****************************************************************************
int SendGetColorEnableCmd(ColorNameT ColorName,bool State, char* EnableMask)
{
	enum StateValue {OFF=0, ON=1};
	unsigned char DACSetupArray[]= "\x4F\x00\x50\x00";
	unsigned char CmdPrefix[] = "#ZP3";
	if(LightEngine == Sola_Type)
	{
		 ColorName = ALL;
	}
	switch (ColorName)
	{	
		case  RED:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7E;
			else
				DACSetupArray[1] = *EnableMask | 0x01;
			break;
		case  GREEN:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7D;
			else
				DACSetupArray[1] = *EnableMask | 0x02;
			break;
		case  VIOLET:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x77;
			else
				DACSetupArray[1] = *EnableMask | 0x08;
			break;
		case  CYAN:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7B;
			else
				DACSetupArray[1] = *EnableMask | 0x04;
			break;
		case  BLUE:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x5F;
			else
				DACSetupArray[1] = *EnableMask | 0x20;
			break;
		case  TEAL:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x3F;
			else
				DACSetupArray[1] = *EnableMask | 0x40;
			break;
		case  YGFILTER:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x6F;
			else
				DACSetupArray[1] = *EnableMask | 0x10;
			break;
		case ALL:
		case WHITE:
			if(State==ON)
			{
				DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x40 : 0x00;
			}
			else
				DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x7F : 0xCF; // dont toggle YG filter if not needed
			break;
		case SHUTTER:
			if(State== ON)
			{
				DACSetupArray[1] = *EnableMask;  // set enabled channels on
			}
			else
			{
				DACSetupArray[1] = 0x7F; // all off
			}
		default:
			break;		
	}
	if (LightEngine == Aura_Type)
	{
		// See Aura TTL IF Doc: Front Panel Control/DAC for more detail
		DACSetupArray[1] = DACSetupArray[1] | 0x20; // Mask for Aura to be sure DACs are Enabled 
	}

	if(ColorName != SHUTTER) // shutter is a unique case were we dont want to change our mask
	{
		*EnableMask = DACSetupArray[1]; // Sets the Mask to current state
	}
    WriteToComPort(port_.c_str(),CmdPrefix,4);       // write command prefix
	WriteToComPort(port_.c_str(),DACSetupArray, 3);  // Write Event Data to device
	WriteToComPort(port_.c_str(),"\n",1);            // Write linefeed

   // block/wait no acknowledge so just give it time                     
   // CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}



	
