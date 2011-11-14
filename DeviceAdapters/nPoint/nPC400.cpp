//////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   nPoint C400 Driver
//
// COPYRIGHT:     nPoint,
//				  Mission Bay Imaging, San Francisco, 2011
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
//
// AUTHOR:        Lon Chu (lonchu@yahoo.com), created on August 2011
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <stdio.h>
#include <string>
#include <fstream>
#include <iostream>
#include <sstream>
#include <math.h>
#include <time.h>
#include "../../MMCore/MMCore.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "nPC400.h"
#include "nPC400Ctrl.h"
#include "nPC400Channel.h"

using namespace std;

nPC400* g_pC400;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

//
// Initialize the MMDevice name
//
MODULE_API void InitializeModuleData()
{
    g_pC400 = nPC400::Instance();       // Initiate the nPC400 instance

    // log the InitializeModuleData function call
    //if (nPC400::Instance()->GetC400Str(nPC400::C400_LogFilename).length() > 0)
	//{
    //    struct tm tmNewTime;
    //    __time64_t lLongTime;

    //    _time64(&lLongTime);                        // Get time as 64-bit integer.
                                                    // Convert to local time.
    //    _localtime64_s(&tmNewTime, &lLongTime );    // C4996

    //    ofstream ofsLogfile;
    //    ofsLogfile.open(nPC400::Instance()->GetC400Str(nPC400::C400_LogFilename).c_str(), ios_base::out | ios_base::app);
    //    if (ofsLogfile.is_open())
    //    {
    //        ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //        ofsLogfile << "<nPC400::AddAvailableDeviceName> :: nPC400Ctrl = (" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH1 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH2 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH3 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH4 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH5 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str() << ")";
    //        ofsLogfile << " :: nPC400CH6 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str() << ")\n" << flush;
    //        ofsLogfile.close();
    //    }
    //    else
    //    {
    //        std::cerr << "LogFile - <" << nPC400::Instance()->GetC400Str(nPC400::C400_LogFilename).c_str() << "> open failed." << flush;
    //    }
	//}

	// initialize the controller device name
	AddAvailableDeviceName( nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str(),  nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str());

	// initialize the channel 1 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str());

	// initialize the channel 2 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str());

	// initialize the channel 3 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str());

	// initialize the channel 4 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str());

	// initialize the channel 5 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str());

	// initialize the channel 6 device name
	AddAvailableDeviceName(nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str());
}

//
// Creating the MMDevice
//
MODULE_API MM::Device* CreateDevice(const char* sDeviceName)
{
    // checking for null pinter
    if (sDeviceName == 0) return 0;

    //struct tm tmNewTime;
    //__time64_t lLongTime;

    //_time64(&lLongTime);                        // Get time as 64-bit integer.
    //                                            // Convert to local time.
    //_localtime64_s(&tmNewTime, &lLongTime );    // C4996

    //std::ofstream ofsLogfile;
    //ofsLogfile.open(nPC400::Instance()->GetC400Str(nPC400::C400_LogFilename).c_str(), ios_base::out | ios_base::app);

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400Ctrl = (" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str()) == 0) 
    {
        // if device name is C400 Controller, create the C400 device
        nPC400Ctrl*  pC400CtrlDev = new nPC400Ctrl();
        return pC400CtrlDev;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400Ctrl = (" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH1 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str()) == 0)
    {
        // if device name is XY Stage, create the XY Stage Device 
        nPC400CH* pC400CH1 =  new nPC400CH(nPC400::C400_CH1);
        return pC400CH1;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400XY = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH2 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        nPC400CH* pC400CH2 = new nPC400CH(nPC400::C400_CH2);
        return pC400CH2;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH2 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH2DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH3 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        nPC400CH* pC400CH3 = new nPC400CH(nPC400::C400_CH3);
        return pC400CH3;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH3 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH3DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH4 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        nPC400CH* pC400CH4 = new nPC400CH(nPC400::C400_CH4);
        return pC400CH4;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH4 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH4DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH5 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        nPC400CH* pC400CH5 = new nPC400CH(nPC400::C400_CH5);
        return pC400CH5;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH5 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH5DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH6 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        nPC400CH* pC400CH6 = new nPC400CH(nPC400::C400_CH6);
        return pC400CH6;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<nPC400::CreateDevice> deviceName = (" << sDeviceName << ") :: nPC400CH6 = (" << nPC400::Instance()->GetC400Str(nPC400::C400_CH6DeviceName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}
    //ofsLogfile.close();

    // device name is not recognized, return null
    return NULL;
}

//
// delete the device --> invoke device destructor
//
MODULE_API void DeleteDevice(MM::Device* pDevice)
{
     if (pDevice != 0) delete pDevice;
}

//
// General utility function
//
int ClearPort(MM::Device& device, MM::Core& core, const char* sPort)
{
    // Clear contents of serial port 
    const int nBufSize = 255;
    unsigned char sClear[nBufSize];                                                        
    unsigned long lRead = nBufSize;                                               
    int ret;

    // reset the communication port buffer
    while ((int) lRead == nBufSize)                                                     
    { 
        // reading from the serial port
        ret = core.ReadFromSerial(&device, sPort, sClear, nBufSize, lRead);

        std::ostringstream sMessage;
        sMessage << "<nPC400::ClearPort> port = (" <<  sPort << ") :: clearBuffer(" << lRead << ")  = (" << sClear << ")";
        core.LogMessage(&device, sMessage.str().c_str(), false);

        // verify the read operation
        if (ret != DEVICE_OK) return ret;                                                           
    } 

    // upon successful restting the port
    return DEVICE_OK;                                                           
} 

bool        nPC400::m_yInstanceFlag         = false;        // instance flag
int         nPC400::m_nChannelsAvailable    = false;        // MP285 devices availability
nPC400*     nPC400::m_pC400                 = NULL;         // single copy MP285
int         nPC400::m_nNumberOfAxes         = 1;            // number of axes attached to the controller, initial set to zero
int         nPC400::m_nDebugLogFlag         = 0;            // debug log flag
std::string nPC400::m_sPort;                                // serial port symbols


nPC400::nPC400()
{
    nPC400::m_sC400Str[nPC400::C400_ChannelBoardName]         = "nPoint C400 Controller";                   // C400 controller device name
    nPC400::m_sC400Str[nPC400::C400_CH1DeviceName]            = "Stage 1";                                  // Channel 1 device name
    nPC400::m_sC400Str[nPC400::C400_CH2DeviceName]            = "Stage 2";                                  // Channel 2 device name
    nPC400::m_sC400Str[nPC400::C400_CH3DeviceName]            = "Stage 3";                                  // Channel 3 device name
    nPC400::m_sC400Str[nPC400::C400_CH4DeviceName]            = "Stage 4";                                  // Channel 4 device name
    nPC400::m_sC400Str[nPC400::C400_CH5DeviceName]            = "Stage 5";                                  // Channel 5 device name
    nPC400::m_sC400Str[nPC400::C400_CH6DeviceName]            = "Stage 6";                                  // Channel 6 device name
    nPC400::m_sC400Str[nPC400::C400_SoftwareVersion]          = "v1.08.032";                                // C400 software version
    nPC400::m_sC400Str[nPC400::C400_LogFilename]              = "C400Log.txt";                              // C400 logfile name
    nPC400::m_sC400Str[nPC400::C400_ChannelBoardNameLabel]    = "C.00 Board ";                              // C400 channel board name label
    nPC400::m_sC400Str[nPC400::C400_ChannelBoardDescLabel]    = "C.01 Board ";                              // C400 channel board description label
    nPC400::m_sC400Str[nPC400::C400_SoftwareVersionLabel]     = "C.02 Software Version";                    // C400 software version label
    nPC400::m_sC400Str[nPC400::C400_ChannelBoardConnLabel]    = "C.03 Board Connected";                     // C400 channel board connected label
    nPC400::m_sC400Str[nPC400::C400_NumberOfAxesLabel]        = "C.04 Number Of Axes";                      // C400 number of axes label
    nPC400::m_sC400Str[nPC400::C400_DebugLogFlagLabel]        = "C.05 Debug Log Flag";                      // C400 debug log flag label
    nPC400::m_sC400Str[nPC400::C400_ChannelDeviceNameLabel]   = "H.00 Channel ";                            // Channel devive conneced falg label
    nPC400::m_sC400Str[nPC400::C400_ChannelDeviceDescLabel]   = "H.01 Channel ";                            // Channel devive conneced falg label
    nPC400::m_sC400Str[nPC400::C400_ChannelDeviceConnLabel]   = "H.02 Channel Device Connected";            // Channel devive conneced falg label
    nPC400::m_sC400Str[nPC400::C400_ChannelRangeRadLabel]     = "H.03 Channel Radius";                      // Channel device radius label
    nPC400::m_sC400Str[nPC400::C400_ChannelRangeUnitLabel]    = "H.04 Range Unit (0:um/1:mm/2:ur)";         // Channel device range unit label
    nPC400::m_sC400Str[nPC400::C400_ChannelRangeLabel]        = "H.05 Range";                               // Channel device range label
    nPC400::m_sC400Str[nPC400::C400_ChannelPositionSetLabel]  = "H.06 Position Set @ (um)";                 // Channel device position set label
    nPC400::m_sC400Str[nPC400::C400_ChannelPositionGetLabel]  = "H.07 Position Get @ (um)";                 // Channel device position get label
    nPC400::m_sC400Str[nPC400::C400_ChannelSrvoStatLabel]     = "H.08 Servo State (0:disable 1:enable)";    // Channel device servo state
    nPC400::m_sC400Str[nPC400::C400_ChannelPropGainLabel]     = "H.09 Proporational Gain";                  // Channel device proportional gain
    nPC400::m_sC400Str[nPC400::C400_ChannelIntgGainLabel]     = "H.10 Integral Gain";                       // Channel device integral gain
    nPC400::m_sC400Str[nPC400::C400_ChannelDervGainLabel]     = "H.11 Derivative Gain";                     // Channel device derivative gain
}

nPC400::~nPC400()
{
    if (m_pC400) delete m_pC400;
    m_yInstanceFlag = false;
}

nPC400* nPC400::Instance()
{
    if(!m_yInstanceFlag)
    {
        m_pC400 = new nPC400();
        m_yInstanceFlag = true;
    }

    return m_pC400;
}

//
// Get C400 constant string
//
std::string nPC400::GetC400Str(int nC400StrCode) const
{ 
   string sText;        // C400 String

   if (m_pC400 != NULL)
   {
       map<int, string>::const_iterator nIterator;
       nIterator = m_sC400Str.find(nC400StrCode);   
       if (nIterator != m_sC400Str.end())
          sText = nIterator->second;
   }

   return sText;
}

//
// Copy byte data buffer for iLength
//
int nPC400::ByteCopy(unsigned char* bDst, const unsigned char* bSrc, int nLength)
{
    int nBytes = 0;
    if (bSrc == NULL || bDst == NULL) return(nBytes);
    for (nBytes = 0; nBytes < nLength; nBytes++) bDst[nBytes] = bSrc[nBytes];
    return nBytes;
}

//
// Convert byte data to hex string
//
void nPC400::Byte2Hex(const unsigned char bByte, char* sHex)
{
    char sHexDigit[16] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    sHex[2] =  NULL;
    sHex[1] = sHexDigit[(int)(bByte & 0xF)];
    sHex[0] = sHexDigit[(int)(bByte / 0x10)];
    return;
}


