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
        MPSTR_LogFilename       = 4,            // MP285 log filename
        MPSTR_CtrlDevNameLabel  = 5,            // MP285 controller device name label
		MPSTR_CtrlDevDescLabel  = 6,			// MP285 controller device decription label
        MPSTR_FirmwareVerLabel  = 7,            // MP285 FIRMWARE VERSION label
        MPSTR_MP285VerLabel     = 8,            // MP285 Adapter version label
        MPSTR_DebugLogFlagLabel = 9,            // MP285 Debug Log Flag label
        MPSTR_CommStateLabel    = 10,           // MP285 COMM. STATUS label
        MPSTR_ResolutionLabel   = 11,           // MP285 RESOLUION label
        MPSTR_AccelLabel        = 12,           // MP285 ACCELERATION label
        MPSTR_Um2UStepUnit      = 13,           // MP285 um to ustep label
        MPSTR_UStep2NmUnit      = 14,           // MP285 ustep to nm label
        MPSTR_VelocityLabel     = 15,           // MP285 VELOCITY label
        MPSTR_MotionMode        = 16,           // MP285 MODE label
        MPSTR_SetOrigin         = 17,           // MP285 ORIGIN label
        MPSTR_TimeoutInterval   = 18,           // MP285 Timeout Interval label
        MPSTR_TimeoutTrys       = 19,           // MP285 Timeout Trys label
        MPSTR_XYDevNameLabel    = 20,           // MP285 controller device name label
		MPSTR_XYDevDescLabel    = 21,			// MP285 controller device decription label
        MPSTR_SetPositionX      = 22,           // MP285 POSITION X label
        MPSTR_SetPositionY      = 23,           // MP285 POSITION Y label
        MPSTR_GetPositionX      = 24,           // MP285 CURRENT POSITION X label
        MPSTR_GetPositionY      = 25,           // MP285 CURRENT POSITION Y label
        MPSTR_ZDevNameLabel     = 26,           // MP285 controller device name label
		MPSTR_ZDevDescLabel     = 27,			// MP285 controller device decription label
        MPSTR_SetPositionZ      = 28,           // MP285 POSITION Z label
        MPSTR_GetPositionZ      = 29,           // MP285 CURRENT POSITION Z label
        MPSTR_PauseMode         = 30,           // property PAUSE label
        MPSTR_Reset             = 31,           // property RESET label
        MPSTR_Status            = 32            // property STATUS label
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
	static int  GetDebugLogFlag() { return m_nDebugLogFlag; }								// get MP285 debug log flag
	static void SetDebugLogFlag(int nDebugLogFlag) { m_nDebugLogFlag = nDebugLogFlag; }		// set MP285 debug log flag
    static void SetVelocity(long lVelocity) { m_lVelocity = lVelocity; }                    // set MP285 device velocity
    static long GetVelocity() { return m_lVelocity; }                                       // get MP285 device velocity
    static void SetResolution(int nResolution) { m_nResolution = nResolution; }             // set MP285 device resolution
    static int  GetResolution() { return m_nResolution; }                                   // get MP285 resolution
    static void SetUm2UStep(int nUm2UStep) { m_nUm2UStep = nUm2UStep; }                     // set MP285 Um to UStep conversion unit
    static int  GetUm2UStep() { return m_nUm2UStep; }                                       // get MP285 Um to UStep conversion unit
    static void SetUStep2Nm(int nUStep2Nm) { m_nUStep2Nm = nUStep2Nm; }                     // set MP285 UStep to Nm conversion unit
    static int  GetUStep2Nm() { return m_nUStep2Nm; }                                       // get MP285 UStep to NM conversion unit
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
	static int					m_nDebugLogFlag;			// MP285 debug log flag
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