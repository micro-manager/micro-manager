///////////////////////////////////////////////////////////////////////////////
// FILE:          XLed.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Definition of X-Cite Led Singleton Class
//
// COPYRIGHT:     Lumen Dynamics
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com) created on July 2011
//

#pragma once

#include <string>
#include <map>

// Global function to reset the serial port
int ClearPort(MM::Device& device, MM::Core& core, const char* port);

class XLed
{
public:
    ~XLed();   // Destructor

    typedef int XLED;
    enum _XLED
    {
        XL_CtrlBoardName            = 0,            // XLed controller name
        XL_CtrlBoardDesc            = 1,            // XLed controller description
        XL_WLedDevName              = 2,            // W LED device name
        XL_XLedDevName              = 3,            // X LED device name
        XL_YLedDevName              = 4,            // Y LED device name
        XL_ZLedDevName              = 5,            // Z LED device name
        XL_WLedDevDesc              = 6,            // W LED device name
        XL_XLedDevDesc              = 7,            // X LED device name
        XL_YLedDevDesc              = 8,            // Y LED device name
        XL_ZLedDevDesc              = 9,            // Z LED device name
        XL_XLedSoftVer              = 10,           // XLed software version
        XL_LogFilename              = 11,           // XLed logfile name
        XL_CtrlBoardNameLabel       = 12,           // XLed controller name label
        XL_CtrlBoardDescLabel       = 13,           // XLed controller description label
        XL_SerialNumberLabel        = 14,           // Seerial Number label
        XL_UnitSoftVerLabel         = 15,           // Led device software version label
        XL_XLedSoftVerLabel         = 16,           // XLed software version label
        XL_CtrlBoardConnLabel       = 17,           // XLed controller connected label
        XL_XLedDebugLogFlag         = 18,           // XLed debug flag label
        XL_XLedStatusLabel          = 19,           // XLed status label
        XL_XLedStatusDescLabel      = 20,           // XLed status description label
        XL_AllOnOffLabel            = 21,           // All leds ON/OFF label
        XL_PWMStateLabel            = 22,           // PWM state label
        XL_PWMModeLabel             = 23,           // PWM mode label
        XL_FrontPanelLabel          = 24,           // Front Panel (Lock/UnLock) label
        XL_LCDScrnNumberLabel       = 25,           // LCD screen number label
        XL_LCDScrnBriteLabel        = 26,           // LCD Screen brightness label
        XL_LCDScrnSaverLabel        = 27,           // LCD screen saver label
        XL_ClearAlarmLebel          = 28,           // Clear Alarm Label
        XL_SpeakerVolumeLabel       = 29,           // Speaker volume label
        XL_LedDevNameLabel          = 30,           // Led device name label
        XL_LedDevDescLabel          = 31,           // Led device name label
        XL_LedDevTypeLabel          = 32,           // Led device type label
        XL_LedSerialNoLabel         = 33,           // Led device serial number label
        XL_LedMfgDateLabel          = 34,           // Led device manufacturing date label
        XL_LedWaveLengthLabel       = 35,           // Led device wavelength label
        XL_LedFWHMLabel             = 36,           // Led device FWHM label
        XL_LedStatusLabel           = 37,           // Led device Status Label
        XL_LedStatusDescLabel       = 38,          // Led device Status Description Label
        XL_LedHoursLabel            = 39,           // Led device used hours label
        XL_LedOnOffStateLabel       = 40,           // Led device ON/OFF state label
        XL_LedIntensityLabel        = 41,           // Led device intensity label
        XL_LedPulseWidthLabel       = 42,           // Led device min pulse width label
        XL_PulseModeLabel           = 43,           // XLed pulse mode lael
        XL_SignalOnTimeLabel        = 44,           // Led device signal on time label
        XL_SignalOffTimeLabel       = 45,           // Led device signal off time label
        XL_SignalDelayTimeLabel     = 46,           // Led device signal delay time label
        XL_TriggerDelayTimeLabel    = 47,           // Led device trigger delay time label
        XL_PWMUnitsLabel            = 48,           // Led device PWM units label
        XL_LedTempLabel             = 49,           // Led device temperature label
        XL_LedMaxTempLabel          = 50,           // Led device max allowed temperature label
        XL_LedMinTempLabel          = 51,           // Led device min allowed temperature label
        XL_LedTempHystLabel         = 52,           // Led devicetemperature hysteresis label
        XL_Reserved                 = 53            // Led device software version label
    };

    enum
    {
        XL_LedDevW                  = 0,            // Led device W
        XL_LedDevX                  = 1,            // Led device X
        XL_LedDevY                  = 2,            // Led device Y
        XL_LedDevZ                  = 3,            // Led device Z
        XL_TxTerm                   = '\r',         // unique termination from MM to XLED communication - host mode
        XL_RxTerm                   = '\r',         // unique termination from XLED to MM communication
        XL_MaxPropSize              = 50            // property size
    };

    enum
    {
        XL_UnitSerialNo             = 0,            // unit serial number
        XL_UnitStatus               = 1,            // unit status
        XL_SigPulseMode             = 2,            // Signal Pulse Mode
        XL_SigDelayTime             = 3,            // Signal Delay Time
        XL_SigOnTime                = 4,            // Signal On Time
        XL_SigOffTime               = 5,            // Signal Off Time
        XL_SigAdvTime               = 6,            // Signal Advance Time
        XL_PWMStat                  = 7,            // PWM Start/Stop Status
        XL_PWMMode                  = 8,            // PWM Shot (Single/Continuous)
        XL_PWMUnit                  = 9,            // PWM Units
        XL_UnitSoftVer              = 10,           // Unit Software Version 
        XL_FrontPanel               = 11,           // Front Pnel Status
        XL_LCDScrnNo                = 12,           // LCD Screen Number
        XL_LCDBrite                 = 13,           // LCD Brightness
        XL_LCDSaver                 = 14,           // LCD Screen Saver
        XL_ClearAlarm               = 15,           // Clear Alarm
        XL_SpeakVol                 = 16,           // Speaker Volume
        XL_LedName                  = 17,           // Led Name
        XL_LedSerialNo              = 18,           // Led serial number
        XL_LedMakeDate              = 19,           // Led Manufacture Date
        XL_LedType                  = 20,           // Led Type
        XL_LedWaveLength            = 21,           // Led wavelength
        XL_LedTemperature           = 22,           // Led Temperature
        XL_LedMaxTemp               = 23,           // Led maximum Temperature
        XL_LedMinTemp               = 24,           // Led Minimum Temperature
        XL_LedTempHyst              = 25,           // Led Temperature Hysteresis
        XL_LedFWHM                  = 26,           // Led Full Width Half Maximum
        XL_LedHours                 = 27,           // Led Hours
        XL_LedOnStat                = 28,           // Led On Stat
        XL_LedIntensity             = 29,           // Led Intensity
        XL_LedMinPulseWidth         = 30,           // Led minimujm pulse width
        XL_MaxParameters            = 31,           // maximum parameters
        XL_LedSoftVer               = 32            // Led Software Version
    };

    static XLed* Instance();                                                                                    // only interface for singleton
    std::string GetXLedStr(int nC400StrCode) const;                                                             // access prdefined strings
    static int ByteCopy(unsigned char* bDst, const unsigned char* bSrc, int nLength);                           // copy byte buffer for iLength
    static void Byte2Hex(const unsigned char bByte, char* sHex);                                                // convert byte number to hex
    static std::string& GetSerialPort() { return m_sPort; }                                                     // get serial port symbol
    static void SetSerialPort(std::string sPort) { m_sPort = sPort; }                                           // set serial port symbol
    static void SetXLedConnected(bool yConnected) { m_yConnected = yConnected; }                                // set XLED connected flag
    static bool GetXLedConnected() { return m_yConnected; }                                                     // get XLED connected flag
    static void SetDebugLogFlag(int nDebugLogFlag) { m_nDebugLogFlag = nDebugLogFlag; }                         // set debug log flag
    static int GetDebugLogFlag() { return m_nDebugLogFlag; }                                                    // get debug log flag
    static unsigned char* GetParameter(int nParameterID);                                                       // return address of the parameter ID
    static void ResetCache() { memset(m_sParamData, 0, 2000); }                                                 // reset cache memory

protected:
    XLed();    // Constructor

private:
    static bool                 m_yInstanceFlag;            // singleton flag
    static bool                 m_yConnected;               // XLed connected
    static int                  m_nDebugLogFlag;            // debug log flag
    static unsigned char        m_sParamData[2000];         // parameter data
    static XLed*                m_pXLed;                    // singleton copy
    static std::string          m_sPort;                    // serial port symbols
    std::map<int, std::string>  m_sXLedStr;                 // constant strings
};