///////////////////////////////////////////////////////////////////////////////
// FILE:          MP285.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MP285 Micromanipulator Controller
//                XY Stage
//                Z  Stage
//
// COPYRIGHT:     Sutter Instrument,
//                Mission Bay Imaging, San Francisco, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on March 2011
//                Lon Chu (lonchu@yahoo.com) modified on 
//

#ifndef _MP285_H_
#define _MP285_H_

#include <string>
#include <map>	

// Global function to reset the serial port
int ClearPort(MM::Device& device, MM::Core& core, const char* port);

//
// class MP285 define all global varaiable in singleton class
// it can only be accessed via Instance().
//
class MP285
{
public:
    ~MP285();   // Destructor

    typedef int MPStr;
    enum _MPStr
    {
        MPSTR_CtrlDevName       = 0,            // MP285 controller device name
        MPSTR_XYStgaeDevName    = 1,            // MP285 XY stage device name
        MPSTR_ZStageDevName     = 2,            // MP285 Z stage device name
        MPSTR_MP285Version      = 3,            // MP285 adapter version
        MPSTR_MP285VerLabel     = 4,            // Adapter version label
        MPSTR_CommStateLabel    = 5,            // property MP285 COMM. STATUS label
        MPSTR_FirmwareVerLabel  = 6,            // property FIRMWARE VERSION label
        MPSTR_ResolutionLabel   = 7,            // property RESOLUION label
        MPSTR_AccelLabel        = 8,            // Property ACCELERATION label
        MPSTR_Um2UStepUnit      = 9,            // property um to ustep label
        MPSTR_UStep2NmUnit      = 10,           // property ustep to nm label
        MPSTR_SetPositionX      = 11,           // property POSITION X label
        MPSTR_SetPositionY      = 12,           // property POSITION Y label
        MPSTR_SetPositionZ      = 13,           // property POSITION Z label
        MPSTR_GetPositionX      = 14,           // property CURRENT POSITION X label
        MPSTR_GetPositionY      = 15,           // property CURRENT POSITION Y label
        MPSTR_GetPositionZ      = 16,           // property CURRENT POSITION Z label
        MPSTR_VelocityLabel     = 17,           // property VELOCITY label
        MPSTR_MotionMode        = 18,           // property MODE label
        MPSTR_PauseMode         = 19,           // property PAUSE label
        MPSTR_SetOrigin         = 20,           // property ORIGIN label
        MPSTR_Reset             = 21,           // property RESET label
        MPSTR_Status            = 22,           // property STATUS label
        MPSTR_TimeoutInterval   = 23,           // property Timeout Interval label
        MPSTR_TimeoutTrys       = 24,           // property Timeout Trys label
        MPSTR_LogFilename       = 25            // MP285 log filename
    };

    enum
    {
        MP285_TxTerm            = 0x0D,         // EOL transmit symbole
        MP285_RxTerm            = 0x0D,         // EOL receiving symbol
                                                //////////////////////////////////////
                                                // Serial CommunicationError codes
                                                //////////////////////////////////////
        MP285_SP_OVER_RUN		= 0x30,			// The previous character was not unloaded before the latest was received
        MP285_FRAME_ERROR		= 0x31,			// The vald stop bits was not received during the appropriate time period
        MP285_BUFFER_OVER_RUN	= 0x32,			// The input buffer is filled and CR has not been received
        MP285_BAD_COMMAND		= 0x34,			// Input cannot be interpreted -- command byte not valid
        MP285_MOVE_INTERRUPTED	= 0x38			// A requested move was interrupted by input of serial port.  This code is
                                                // ORed with any other error code.  The value normally returned is "*",
                                                // i.e., 8 ORed with 4. "4" is reported on the vacuum fluorescent display
    };

    static MP285* Instance();                                                               // only interface for singleton
    std::string GetMPStr(int nMPStrCode) const;                                             // access prdefined strings
    static void SetDeviceAvailable(bool yFlag) { m_yDeviceAvailable = yFlag; }              // set MP285 device availability
    static bool GetDeviceAvailability() { return m_yDeviceAvailable; }                      // get MP285 device availability
    static void SetVelocity(long lVelocity) { m_lVelocity = lVelocity; }                    // set MP285 device velocity
    static long GetVelocity() { return m_lVelocity; }                                       // get MP285 device velocity
    static void SetResolution(int nResolution) { m_nResolution = nResolution; }             // set MP285 device resolution
    static int  GetResolution() { return m_nResolution; }                                   // get MP285 resolution
    static void SetUm2UStep(int nUm2UStep) { m_nUm2UStep = nUm2UStep; }                     // set Um to UStep conversion unit
    static int  GetUm2UStep() { return m_nUm2UStep; }                                       // get Um to UStep conversion unit
    static void SetUStep2Nm(int nUStep2Nm) { m_nUStep2Nm = nUStep2Nm; }                     // set UStep to Nm conversion unit
    static int  GetUStep2Nm() { return m_nUStep2Nm; }                                       // get UStep to NM conversion unit
    static void SetMotionMode(int nMotionMode) { m_nMotionMode = nMotionMode; }             // set Motor motion mode
    static int  GetMotionMode() { return m_nMotionMode; }                                   // get Motor motion mode
    static void SetTimeoutInterval(int nInterval) { m_nTimeoutInterval = nInterval; }       // set Timwout Interval
    static int  GetTimeoutInterval() { return m_nTimeoutInterval; }                         // get Timeout Interval
    static void SetTimeoutTrys(int nTrys) { m_nTimeoutTrys = nTrys; }                       // set Timeout Trys
    static int  GetTimeoutTrys() { return m_nTimeoutTrys; }                                 // get Timeout Trys
    //static void SetNumberOfAxes(int nNumberOfAxes) { m_nNumberOfAxes = nNumberOfAxes; }   // set number of axes controlled by MP285
    //static int  GetNumberOfAxes() { return m_nNumberOfAxes; }                             // get numebr of axes controlled by MP285
    static void SetPositionX(double dPosition) { m_dPositionX = dPosition; }                // set position x
    static double GetPositionX() { return m_dPositionX; }                                   // get position x
    static void SetPositionY(double dPosition) { m_dPositionY = dPosition; }                // set position y
    static double GetPositionY() { return m_dPositionY; }                                   // get position y
    static void SetPositionZ(double dPosition) { m_dPositionZ = dPosition; }                // set position z
    static double GetPositionZ() { return m_dPositionZ; }                                   // get position z
    static std::string& GetSerialPort() { return m_sPort; }                                 // get serial port symbol
    static void SetSerialPort(std::string sPort) { m_sPort = sPort; }                       // set serial port symbol
    static int ByteCopy(unsigned char* bDst, const unsigned char* bSrc, int nLength);       // copy byte buffer for iLength
    static void Byte2Hex(const unsigned char bByte, char* sHex);                            // convert byte number to hex

protected:
    MP285();    // Constructor

private:
    static bool                 m_yInstanceFlag;            // singleton flag
    static MP285*               m_pMP285;                   // singleton copy
    static bool                 m_yDeviceAvailable;         // MP285 availability
    static int                  m_nResolution;              // MP285 resolution
    static int                  m_nUm2UStep;                // unit to convert um to uStep
    static int                  m_nUStep2Nm;                // unit to convert uStep to nm
    static int                  m_nTimeoutInterval;         // timeout interval
    static int                  m_nTimeoutTrys;             // timeout trys
    //static int                m_nNumberOfAxes;            // number of MP285 axes
    static int                  m_nMotionMode;              // motor motion mode
    static double               m_dPositionX;               // position X
    static double               m_dPositionY;               // position Y
    static double               m_dPositionZ;               // position Z
    static long                 m_lVelocity;                // MP285 velocity
    static std::string          m_sPort;                    // serial port symbols
    std::map<int, std::string>  m_sMPStr;                   // constant strings
};

#endif  //_MP285_H_