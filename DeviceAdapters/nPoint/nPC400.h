///////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   nPoint C400 Controller
//
// COPYRIGHT:     nPoint,
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on August 2011
//                Lon Chu (lonchu@yahoo.com) modified on 
//

#pragma once

#include <string>
#include <map>

// Global function to reset the serial port
int ClearPort(MM::Device& device, MM::Core& core, const char* port);

class nPC400
{
public:
    ~nPC400();   // Destructor

    typedef int C400;
    enum _C400
    {
        C400_ChannelBoardName        = 0,            // C400 channel board name
        C400_CH1DeviceName           = 1,            // Channel 1 device name
        C400_CH2DeviceName           = 2,            // Channel 2 device name
        C400_CH3DeviceName           = 3,            // Channel 3 device name
        C400_CH4DeviceName           = 4,            // Channel 4 device name
        C400_CH5DeviceName           = 5,            // Channel 5 device name
        C400_CH6DeviceName           = 6,            // Channel 6 device name
        C400_SoftwareVersion         = 7,            // C400 software version string
        C400_LogFilename             = 8,            // C400 log filename
        C400_ChannelBoardNameLabel   = 9,            // C400 channels board connected variable label
        C400_ChannelBoardDescLabel   = 10,           // C400 channels board connected variable label
        C400_ChannelBoardConnLabel   = 11,           // C400 channels board connected variable label
        C400_SoftwareVersionLabel    = 12,           // C400 software version vabel
        C400_NumberOfAxesLabel       = 13,           // C400 number of axes controlled label
        C400_DebugLogFlagLabel       = 14,           // C400 debug log flag label
        C400_ChannelDeviceNameLabel  = 15,           // Channel device name label
        C400_ChannelDeviceDescLabel  = 16,           // Channel device description label
        C400_ChannelDeviceConnLabel  = 17,           // Channel device connection label
        C400_ChannelRangeRadLabel    = 18,           // Channel device range radius label
        C400_ChannelRangeUnitLabel   = 19,           // Channel device range unit label
        C400_ChannelRangeLabel       = 20,           // Channel device range label
        C400_ChannelPositionSetLabel = 21,           // Channel device position set label
        C400_ChannelPositionGetLabel = 22,           // Channel device position get label
        C400_ChannelSrvoStatLabel    = 23,           // Channel device servo state label
        C400_ChannelPropGainLabel    = 24,           // Channel device propotional gain label
        C400_ChannelIntgGainLabel    = 25,           // Channel deviceIntegral gain label
        C400_ChannelDervGainLabel    = 26            // Channel device position get name
    };

    enum
    {
        C400_CH1 = 0,                               // channel 1
        C400_CH2 = 1,                               // channel 2
        C400_CH3 = 2,                               // channel 3
        C400_CH4 = 3,                               // channel 4
        C400_CH5 = 4,                               // channel 5
        C400_CH6 = 5,                               // channel 6
        C400_TxTerm = 0x55,                         // unique termination from MM to C400 communication - host mode
        C400_RxTerm = 0x55                          // unique termination from C400 to MM communication
    };

    static nPC400* Instance();                                                                                  // only interface for singleton
    std::string GetC400Str(int nC400StrCode) const;                                                             // access prdefined strings
    static int ByteCopy(unsigned char* bDst, const unsigned char* bSrc, int nLength);                           // copy byte buffer for iLength
    static void Byte2Hex(const unsigned char bByte, char* sHex);                                                // convert byte number to hex
    static void SetChannelsAvailable(int nChannelsAvailable) { m_nChannelsAvailable = nChannelsAvailable; }     // set channels availability
    static int GetChannelsAvailability() { return m_nChannelsAvailable; }                                       // get channels availability
    static void SetNumberOfAxes(int nNumberOfAxes) { m_nNumberOfAxes = nNumberOfAxes; }                         // set number of axes controlled by C400
    static int  GetNumberOfAxes() { return m_nNumberOfAxes; }                                                   // get numebr of axes controlled by C400
    static int GetDebugLogFlag() { return m_nDebugLogFlag; }                                                    // get debug log flag
    static void SetDebugLogFlag(int nDebugLogFlag) { m_nDebugLogFlag = nDebugLogFlag; }                         // set debug log flag
    static std::string& GetSerialPort() { return m_sPort; }                                                     // get serial port symbol
    static void SetSerialPort(std::string sPort) { m_sPort = sPort; }                                           // set serial port symbol

protected:
    nPC400();    // Constructor

private:
    static bool                 m_yInstanceFlag;            // singleton flag
    static nPC400*              m_pC400;                    // singleton copy
    static int                  m_nChannelsAvailable;       // C400 availability
    static int                  m_nNumberOfAxes;            // number of axes (channels)
    static int                  m_nDebugLogFlag;            // debug log flag
    static std::string          m_sPort;                    // serial port symbols
    std::map<int, std::string>  m_sC400Str;                 // constant strings
};