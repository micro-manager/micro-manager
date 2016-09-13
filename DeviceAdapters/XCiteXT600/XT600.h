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
        XL_CtrlBoardDesc            ,            // XLed controller description
        XL_ULedDevName              ,            // W LED device name
        XL_VLedDevName              ,            // X LED device name
        XL_WLedDevName              ,            // W LED device name
        XL_XLedDevName              ,            // X LED device name
        XL_YLedDevName              ,            // Y LED device name
        XL_ZLedDevName              ,            // Z LED device name
        XL_ULedDevDesc              ,            // W LED device name
        XL_VLedDevDesc              ,            // X LED device name
        XL_WLedDevDesc              ,            // W LED device name
        XL_XLedDevDesc              ,            // X LED device name
        XL_YLedDevDesc              ,            // Y LED device name
        XL_ZLedDevDesc              ,            // Z LED device name
        XL_XLedSoftVer              ,           // XLed software version
        XL_LogFilename              ,           // XLed logfile name
        XL_CtrlBoardNameLabel       ,           // XLed controller name label
        XL_CtrlBoardDescLabel       ,           // XLed controller description label
        XL_SerialNumberLabel        ,           // Seerial Number label
        XL_UnitSoftVerLabel         ,           // Led device software version label
        XL_XLedSoftVerLabel         ,           // XLed software version label
        XL_CtrlBoardConnLabel       ,           // XLed controller connected label
        XL_XLedDebugLogFlag         ,           // XLed debug flag label
        XL_XLedStatusLabel          ,           // XLed status label
        XL_XLedStatusDescLabel      ,           // XLed status description label
        XL_AllOnOffLabel            ,           // All leds ON/OFF label
        XL_PWMStateLabel            ,           // PWM state label
        XL_PWMModeLabel             ,           // PWM mode label
        XL_FrontPanelLabel          ,           // Front Panel (Lock/UnLock) label
        XL_LCDScrnNumberLabel       ,           // LCD screen number label
        XL_LCDScrnBriteLabel        ,           // LCD Screen brightness label
        XL_LCDScrnSaverLabel        ,           // LCD screen saver label
        XL_ClearAlarmLebel          ,           // Clear Alarm Label
        XL_SpeakerVolumeLabel       ,           // Speaker volume label
        XL_LedDevNameLabel          ,           // Led device name label
        XL_LedDevDescLabel          ,           // Led device name label
        XL_LedDevTypeLabel          ,           // Led device type label
        XL_LedSerialNoLabel         ,           // Led device serial number label
        XL_LedMfgDateLabel          ,           // Led device manufacturing date label
        XL_LedWaveLengthLabel       ,           // Led device wavelength label
        XL_LedFWHMLabel             ,           // Led device FWHM label
        XL_LedStatusLabel           ,           // Led device Status Label
        XL_LedStatusDescLabel       ,          // Led device Status Description Label
        XL_LedHoursLabel            ,           // Led device used hours label
        XL_LedOnOffStateLabel       ,           // Led device ON/OFF state label
		XL_LedTriggerSequenceLabel	,			// Led trigger sequence
        XL_LedIntensityLabel        ,           // Led device intensity label
        XL_LedPulseWidthLabel       ,           // Led device min pulse width label
        //XL_PulseModeLabel           ,           // XLed pulse mode lael
        //XL_SignalOnTimeLabel        ,           // Led device signal on time label
        //XL_SignalOffTimeLabel       ,           // Led device signal off time label
        //XL_SignalDelayTimeLabel     ,           // Led device signal delay time label
        //XL_TriggerDelayTimeLabel    ,           // Led device trigger delay time label
        XL_PWMUnitsLabel            ,           // Led device PWM units label
        XL_LedTempLabel             ,           // Led device temperature label
        XL_LedMaxTempLabel          ,           // Led device max allowed temperature label
        XL_LedMinTempLabel          ,           // Led device min allowed temperature label
        XL_LedTempHystLabel         ,           // Led devicetemperature hysteresis label
		XL_LedMinIntensityLabel		,			// Led Minimum intensity label
        XL_Reserved                             // Led device software version label
    };

    enum
    {
        XL_LedDevU                  = 0,         // Led device W
        XL_LedDevV                  ,            // Led device X
        XL_LedDevW                  ,            // Led device W
        XL_LedDevX                  ,            // Led device X
        XL_LedDevY                  ,            // Led device Y
        XL_LedDevZ                  ,            // Led device Z
        XL_TxTerm                   = '\r',      // unique termination from MM to XLED communication - host mode
        XL_RxTerm                   = '\r',      // unique termination from XLED to MM communication
        XL_MaxPropSize              = 50         // property size
    };

    enum
    {
        XL_UnitSerialNo             = 0,         // unit serial number
        XL_UnitStatus               ,            // unit status
        //XL_SigPulseMode             ,            // Signal Pulse Mode
        //XL_SigDelayTime             ,            // Signal Delay Time
        //XL_SigOnTime                ,            // Signal On Time
        //XL_SigOffTime               ,            // Signal Off Time
        //XL_SigAdvTime               ,            // Signal Advance Time
        XL_PWMStat                  ,            // PWM Start/Stop Status
        XL_PWMMode                  ,            // PWM Shot (Single/Continuous)
        XL_PWMUnit                  ,            // PWM Units
        XL_UnitSoftVer              ,            // Unit Software Version 
        XL_FrontPanel               ,            // Front Pnel Status
        XL_LCDScrnNo                ,            // LCD Screen Number
        XL_LCDBrite                 ,            // LCD Brightness
        XL_LCDSaver                 ,            // LCD Screen Saver
        XL_ClearAlarm               ,            // Clear Alarm
        XL_SpeakVol                 ,            // Speaker Volume
        XL_LedName                  ,            // Led Name
        XL_LedSerialNo              ,            // Led serial number
        XL_LedMakeDate              ,            // Led Manufacture Date
        XL_LedType                  ,            // Led Type
        XL_LedWaveLength            ,            // Led wavelength
        XL_LedTemperature           ,            // Led Temperature
        XL_LedMaxTemp               ,            // Led maximum Temperature
        XL_LedMinTemp               ,            // Led Minimum Temperature
        XL_LedTempHyst              ,            // Led Temperature Hysteresis
        XL_LedFWHM                  ,            // Led Full Width Half Maximum
        XL_LedHours                 ,            // Led Hours
        XL_LedOnStat                ,            // Led On Stat
		XL_LedTriggerSequence		,			 // Led Trigger Sequence
        XL_LedIntensity             ,            // Led Intensity
        XL_LedMinPulseWidth         ,            // Led minimum pulse width
		XL_LedMinIntensity			,			 // Led minimum intensity
        XL_MaxParameters            ,            // maximum parameters
        XL_LedSoftVer                            // Led Software Version
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