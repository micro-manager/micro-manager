//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MP285s Controller Driver
//                XY Stage
//                Z  Stage
//
// COPYRIGHT:     Sutter Instrument,
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com), created on March 2011
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
#include "MP285Error.h"
#include "MP285Ctrl.h"
#include "MP285XYStage.h"
#include "MP285ZStage.h"

using namespace std;

MP285* g_pMP285;
MPError* g_pMPError;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

//
// Initialize the MMDevice name
//
MODULE_API void InitializeModuleData()
{
    g_pMP285 = MP285::Instance();       // Initiate the MP285 instance
    g_pMPError = MPError::Instance();   // Initiate the MPError instance

    // log the InitializeModuleData function call
    //if (MP285::Instance()->GetMPStr(MP285::MPSTR_LogFilename).length() > 0)
	//{
    //    struct tm tmNewTime;
    //    __time64_t lLongTime;
    //
    //    _time64(&lLongTime);                        // Get time as 64-bit integer.
    //                                                // Convert to local time.
    //    _localtime64_s(&tmNewTime, &lLongTime );    // C4996
    //
    //    ofstream ofsLogfile;
    //    ofsLogfile.open(MP285::Instance()->GetMPStr(MP285::MPSTR_LogFilename).c_str(), ios_base::out | ios_base::app);
    //    if (ofsLogfile.is_open())
    //    {
    //        ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //        ofsLogfile << "<MP285::AddAvailableDeviceName> :: MP285Ctrl = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str() << ")";
    //        ofsLogfile << " :: MP285XY = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str() << ")";
    //        ofsLogfile << " :: MP285Z = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str() << ")\n" << flush;
    //        ofsLogfile.close();
    //    }
    //    else
    //    {
    //        std::cerr << "LogFile - <" << MP285::Instance()->GetMPStr(MP285::MPSTR_LogFilename).c_str() << "> open failed." << flush;
    //    }
	//}

	// initialize the controller device name
	AddAvailableDeviceName( MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str(),  MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str());

	// initialize the XY stage device name
	AddAvailableDeviceName(MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str(), MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str());

	// initialize the Z stage device name
	AddAvailableDeviceName(MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str(), MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str());
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
    //ofsLogfile.open(MP285::Instance()->GetMPStr(MP285::MPSTR_LogFilename).c_str(), ios_base::out | ios_base::app);

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285Ctrl = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str()) == 0) 
    {
        // if device name is MP285 Controller, create the MP285 device
        MP285Ctrl*  pCtrlDev = new MP285Ctrl();
        return pCtrlDev;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285Ctrl = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_CtrlDevName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285XY = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str()) == 0)
    {
        // if device name is XY Stage, create the XY Stage Device 
        XYStage* pXYStage =  new XYStage();
        return pXYStage;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285XY = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str() << ") ";
    //    std::cerr << sMessage.str().c_str() << "\n" << flush;
    //}

    //if (ofsLogfile.is_open())
    //{
    //    ofsLogfile << "[" << tmNewTime.tm_year << "::" << tmNewTime.tm_mon << "::" << tmNewTime.tm_mday << "::" << tmNewTime.tm_hour << "::" << tmNewTime.tm_min << "::" << tmNewTime.tm_sec << "]   ";
    //    ofsLogfile << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285XY = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str() << ") \n" << flush;
    //}

    if (strcmp(sDeviceName, MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str()) == 0)
    {
        // if device name is Z Stage, create the Z Stage Device 
        ZStage* pZStage = new ZStage();
        return pZStage;
    }
    //else
    //{
    //    std::ostringstream sMessage;
    //    sMessage << "<MP285::CreateDevice> deviceName = (" << sDeviceName << ") :: MP285Z = (" << MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str() << ") ";
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
        sMessage << "<MP285::ClearPort> port = (" <<  sPort << ") :: clearBuffer(" << lRead << ")  = (" << sClear << ")";
        core.LogMessage(&device, sMessage.str().c_str(), false);

        // verify the read operation
        if (ret != DEVICE_OK) return ret;                                                           
    } 

    // upon successful restting the port
    return DEVICE_OK;                                                           
} 

bool            MP285::m_yInstanceFlag      = false;        // instance flag
bool            MP285::m_yDeviceAvailable   = false;        // MP285 devices availability
MP285*          MP285::m_pMP285             = NULL;         // single copy MP285
int             MP285::m_nResolution        = 10;           // MP285 resolution
int             MP285::m_nMotionMode        = 0;            // motor motion mode
int             MP285::m_nUm2UStep          = 25;           // unit to convert um to uStep
int             MP285::m_nUStep2Nm          = 40;           // unit to convert uStep to nm
int             MP285::m_nTimeoutInterval   = 1000;         // timeout interval
int             MP285::m_nTimeoutTrys       = 5;            // timeout trys
long            MP285::m_lVelocity          = 2000;         // velocity
double          MP285::m_dPositionX         = 0.00;         // X Position
double          MP285::m_dPositionY         = 0.00;         // Y Position
double          MP285::m_dPositionZ         = 0.00;         // Z Position
//int           MP285::m_nNumberOfAxes      = 3;            // number of axes attached to the controller, initial set to zero
std::string MP285::m_sPort;                                 // serial port symbols

MP285::MP285()
{
    MP285::m_sMPStr[MP285::MPSTR_CtrlDevName]       = "MP285 Controller";               // MP285 Controllet device name
    MP285::m_sMPStr[MP285::MPSTR_XYStgaeDevName]    = "MP285 XY Stage";                 // MP285 XY Stage device name
    MP285::m_sMPStr[MP285::MPSTR_ZStageDevName]     = "MP285 Z Stage";                  // MP286 Z Stage device name
    MP285::m_sMPStr[MP285::MPSTR_MP285Version]      = "2.05.028";                       // MP285 adpater version number
    MP285::m_sMPStr[MP285::MPSTR_MP285VerLabel]     = "MP285 Adapter Version";          // property MP285 ADAPTER VERSION label
    MP285::m_sMPStr[MP285::MPSTR_CommStateLabel]    = "MP285 Comm. Status";             // property MP285 COMM. STATUS label
    MP285::m_sMPStr[MP285::MPSTR_FirmwareVerLabel]  = "Firmware Version";               // property FIRMWARE VERSION label
    MP285::m_sMPStr[MP285::MPSTR_ResolutionLabel]   = "Resolution (10 or 50)";          // property RESOLUION label
    MP285::m_sMPStr[MP285::MPSTR_AccelLabel]        = "Acceleration";                   // Property ACCELERATION label
    MP285::m_sMPStr[MP285::MPSTR_Um2UStepUnit]      = "um to uStep";                    // property um to ustep label
    MP285::m_sMPStr[MP285::MPSTR_UStep2NmUnit]      = "uStep to nm";                    // property ustep to nm label
    MP285::m_sMPStr[MP285::MPSTR_SetPositionX]      = "Set Position X (um)";            // property get POSITION X label
    MP285::m_sMPStr[MP285::MPSTR_SetPositionY]      = "Set Position Y (um)";            // property get POSITION Y label
    MP285::m_sMPStr[MP285::MPSTR_SetPositionZ]      = "Set Position Z (um)";            // property get POSITION Z label
    MP285::m_sMPStr[MP285::MPSTR_GetPositionX]      = "Get Position X (um)";            // property set POSITION X label
    MP285::m_sMPStr[MP285::MPSTR_GetPositionY]      = "Get Position Y (um)";            // property set POSITION Y label
    MP285::m_sMPStr[MP285::MPSTR_GetPositionZ]      = "Get Position Z (um)";            // property set POSITION Z label
    MP285::m_sMPStr[MP285::MPSTR_VelocityLabel]     = "Velocity (um/s)";                // property VELOCITY label
    MP285::m_sMPStr[MP285::MPSTR_MotionMode]        = "Mode (0=absolute/1=relative)";   // property MODE label
    MP285::m_sMPStr[MP285::MPSTR_PauseMode]         = "Pause (0=continue/1=pause)";     // property PAUSE label
    MP285::m_sMPStr[MP285::MPSTR_SetOrigin]         = "Origin (1=set)";                 // property ORIGIN label
    MP285::m_sMPStr[MP285::MPSTR_Reset]             = "Reset (1=reset)";                // property RESET label
    MP285::m_sMPStr[MP285::MPSTR_Status]            = "Status (1=update)";              // property STATUS label
    MP285::m_sMPStr[MP285::MPSTR_TimeoutInterval]   = "Timeout Interval (ms)";          // property Timeout Interval
    MP285::m_sMPStr[MP285::MPSTR_TimeoutTrys]       = "Timeout Trys";                   // property Timeout Trys
    MP285::m_sMPStr[MP285::MPSTR_LogFilename]       = "MP285Log.txt";                   // MP285 Logfile name
}

MP285::~MP285()
{
    if (m_pMP285) delete m_pMP285;
    m_yInstanceFlag = false;
}

MP285* MP285::Instance()
{
    if(!m_yInstanceFlag)
    {
        m_pMP285 = new MP285();
        m_yInstanceFlag = true;
    }

    return m_pMP285;
}

//
// Get MP285 constant string
//
std::string MP285::GetMPStr(int nMPStrCode) const
{ 
   string sText;        // MP285 String

   if (m_pMP285 != NULL)
   {
       map<int, string>::const_iterator nIterator;
       nIterator = m_sMPStr.find(nMPStrCode);   
       if (nIterator != m_sMPStr.end())
          sText = nIterator->second;
   }

   return sText;
}

//
// Copy byte data buffer for iLength
//
int MP285::ByteCopy(unsigned char* bDst, const unsigned char* bSrc, int nLength)
{
    int nBytes = 0;
    if (bSrc == NULL || bDst == NULL) return(nBytes);
    for (nBytes = 0; nBytes < nLength; nBytes++) bDst[nBytes] = bSrc[nBytes];
    return nBytes;
}

//
// Convert byte data to hex string
//
void MP285::Byte2Hex(const unsigned char bByte, char* sHex)
{
    char sHexDigit[16] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    sHex[2] =  NULL;
    sHex[1] = sHexDigit[(int)(bByte & 0xF)];
    sHex[0] = sHexDigit[(int)(bByte / 0x10)];
    return;
}

