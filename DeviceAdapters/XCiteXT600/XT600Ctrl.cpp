///////////////////////////////////////////////////////////////////////////////
// FILE:          XLedCtrl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of X-Cite Led Controller Class
//
// COPYRIGHT:     Lumen Dynamics,
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
//#include <strsafe.h>
#include "../../MMDevice/ModuleInterface.h"
//#include "../../MMDevice/DeviceUtils.h"
#include "XT600.h"
#include "XT600Ctrl.h"
//#include "XLedDev.h"

using namespace std;


//////////////////////////////////////////////////////
// XLed Controller
//////////////////////////////////////////////////////
//
// Controller for Lumen Dynamic XLed devices.
//


//
// XLed Controller Constructor
//
XLedCtrl::XLedCtrl() :
    m_dAnswerTimeoutMs(5000.),   // wait time out set 1000 ms
    m_yInitialized(false),      // initialized flag set to false
    m_lAllOnOff(0),             // ALl On/Off flag
    m_lPWMState(0),             // PWM status
    m_lPWMMode(0),              // PWM mode
    m_lScrnLock(0),             // front panel lock
    m_lScrnNumber(0),           // screen number
    m_lScrnBrite(0),            // screen brightness
    m_lScrnTimeout(0),          // screen saver time out
    m_lSpeakerVol(0)            // speaker volume
{
    // call initialization of error messages
    InitializeDefaultErrorMessages();

    // Port:
    CPropertyAction* pAct = new CPropertyAction(this, &XLedCtrl::OnPort);
    int ret = CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
    
    std::ostringstream osMessage;
    osMessage << "<XLedCtrl::class-constructor> CreateProperty(" << MM::g_Keyword_Name << "=Undfined), ReturnCode=" << ret;
    this->LogMessage(osMessage.str().c_str());
}

//
// XLed Controller Destructor
//
XLedCtrl::~XLedCtrl()
{
    Shutdown();
}

//
// return device name of the nPC400 controller
//
void XLedCtrl::GetName(char* sName) const
{
    CDeviceUtils::CopyLimitedString(sName, XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardName).c_str());
}

//
// shutdown the controller
//
int XLedCtrl::Shutdown()
{
    m_yInitialized = false;
    return DEVICE_OK; 
}

//
// Read/Load in All Propertey
//
int XLedCtrl::ReadAllProperty()
{
    unsigned char sCmdGet[8] = { 0x00, 0x00, 0x3F, (unsigned char)XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = NULL;
    int ret = DEVICE_OK;

    // get unit serial number (sn?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitSerialNo);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x6E;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get unit status (us?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitStatus);
    sCmdGet[0] = 0x75; sCmdGet[1] = 0x73;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Signal Pulse Mode (pm?)
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigPulseMode);
    //sCmdGet[0] = 0x70; sCmdGet[1] = 0x6D;
    ////memset(sResp, 0, XLed::XL_MaxPropSize);
    //ret = XLedSerialIO(sCmdGet, sResp);
    //if (ret != DEVICE_OK) return ret;

    // Signal Delay Time (dt?)
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigDelayTime);
    //sCmdGet[0] = 0x64; sCmdGet[1] = 0x74;
    ////memset(sResp, 0, XLed::XL_MaxPropSize);
    //ret = XLedSerialIO(sCmdGet, sResp);
    //if (ret != DEVICE_OK) return ret;

    // Signal On Time (ot?)
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigOnTime);
    //sCmdGet[0] = 0x6F; sCmdGet[1] = 0x74;
    ////memset(sResp, 0, XLed::XL_MaxPropSize);
    //ret = XLedSerialIO(sCmdGet, sResp);
    //if (ret != DEVICE_OK) return ret;

    // Signal Off Time (ft?)
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigOffTime);
    //sCmdGet[0] = 0x66; sCmdGet[1] = 0x74;
    ////memset(sResp, 0, XLed::XL_MaxPropSize);
    //ret = XLedSerialIO(sCmdGet, sResp);
    //if (ret != DEVICE_OK) return ret;

    // Signal Advance Time (tt?)
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigAdvTime);
    //sCmdGet[0] = 0x74; sCmdGet[1] = 0x74;
    ////memset(sResp, 0, XLed::XL_MaxPropSize);
    //ret = XLedSerialIO(sCmdGet, sResp);
    //if (ret != DEVICE_OK) return ret;

    // PWM Start/Stop Status (is?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_PWMStat);
    sCmdGet[0] = 0x69; sCmdGet[1] = 0x73;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // PWM Mode (sc?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_PWMMode);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x63;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // PWM Units (su?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_PWMUnit);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x75;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Front Panel (lo?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_FrontPanel);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x6F;
    memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // LCD SCreen Number (ss?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LCDScrnNo);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x73;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // LCD Brightness (lb?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LCDBrite);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x62;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // LCD Screen Saver (st?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LCDSaver);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x74;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Clear Alarm (ca?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_ClearAlarm);
    sCmdGet[0] = 0x63; sCmdGet[1] = 0x61;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Speaker Volume (vo?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_SpeakVol);
    sCmdGet[0] = 0x76; sCmdGet[1] = 0x6F;
    memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led name (ln?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedName);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x6E;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led serial number (ls?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedSerialNo);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x73;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Device Software Version (sv?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitSoftVer);
    sCmdGet[0] = 0x73; sCmdGet[1] = 0x76;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led manufacturing date (md?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMakeDate);
    sCmdGet[0] = 0x6D; sCmdGet[1] = 0x64;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Type (lt?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedType);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x74;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Wavelength (lw?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedWaveLength);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x77;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Temperature (gt?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTemperature);
    sCmdGet[0] = 0x67; sCmdGet[1] = 0x74;
    memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Maximum Temperature (mt?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMaxTemp);
    sCmdGet[0] = 0x6D; sCmdGet[1] = 0x74;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Minimum Temperature (nt?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinTemp);
    sCmdGet[0] = 0x6E; sCmdGet[1] = 0x74;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Temperature Hysteresis (th?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTempHyst);
    sCmdGet[0] = 0x74; sCmdGet[1] = 0x68;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led FWHM (lf?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedFWHM);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x66;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Hours (lh?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedHours);
    sCmdGet[0] = 0x6C; sCmdGet[1] = 0x68;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Minimum Intensity (ni?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinIntensity);
    sCmdGet[0] = 0x6E; sCmdGet[1] = 0x69;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led On stat (on?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    sCmdGet[0] = 0x6F; sCmdGet[1] = 0x6E;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // get Led Intensity (ip?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedIntensity);
    sCmdGet[0] = 0x69; sCmdGet[1] = 0x70;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Minimum Pulse Width (mw?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinPulseWidth);
    sCmdGet[0] = 0x6D; sCmdGet[1] = 0x77;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    // Minimum Pulse Width (mw?)
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTriggerSequence);
    sCmdGet[0] = 0x74; sCmdGet[1] = 0x73;
    //memset(sResp, 0, XLed::XL_MaxPropSize);
    ret = XLedSerialIO(sCmdGet, sResp);
    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Get XLED System Status
//
char* XLedCtrl::GetXLedStatus(unsigned char* sResp, char* sXLedStatus)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();

    if ((sResp == NULL) || (sXLedStatus == NULL)) return NULL;

    int nIndx = static_cast<int>(strlen((const char*)sResp)) - 1;

    while ((nIndx >= 0) && (sResp[nIndx] != ',')) nIndx--;

    if (nDebugLog > 1)
    {
        osMessage << "<XLedCtrl::GetXLedStatus> (" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << "=[" << sResp << "," << nIndx;
    }

    if (nIndx < 0) return NULL;

    strcpy(sXLedStatus, (const char*)&sResp[nIndx+1]);

    if (nDebugLog > 1)
    {
        osMessage << "," << strlen(sXLedStatus) << "," << sXLedStatus;
        this->LogMessage(osMessage.str().c_str());
    }

    return sXLedStatus;
}

//
// Get Status Text Message
//
int XLedCtrl::GetStatusDescription(long lStatus, char* sStatus)
{
    const char* sStatusBitsOn[] =
    {
        "Alarm on",
        "Light guide sensor",
        "Reserved",
        "One or more LEDs on",
        "Reserved",
        "Reserved",
        "SpeedDIAL Lock",
        "Reserved",
        "Reserved",
        "reserved",
        "NVM Error",
        "A/D Error",
        "System Performance Error",
        "Reserved",
        "Reserved",
        "Reserved",
        NULL
    };

    const char* sStatusBitsOff[] =
    {
        "Alarm off",
        "X",
        "X",
        "All off",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        NULL
    };

    long lValue = 1;
    // memset(sStatus, 0, 800);
    sprintf(sStatus, "%s", "[");
    for (int nBit = 0; nBit < 16; nBit++, lValue *= 2)
    {
        long lBit = lStatus & lValue;
        if (lBit == lValue)
        {
            if (strcmp(sStatusBitsOn[nBit],"Reserved") != 0)
                sprintf(&sStatus[strlen(sStatus)], " %s,", sStatusBitsOn[nBit]);
        }
        else if (lBit == 0)
        {
            if (strcmp(sStatusBitsOff[nBit],"X") != 0)
                sprintf(&sStatus[strlen(sStatus)], " %s,", sStatusBitsOff[nBit]);
        }
    }
    sStatus[strlen(sStatus) - 1] = ']';
    return DEVICE_OK;
}

//
// Initialize the XLed controller
//
int XLedCtrl::Initialize()
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();

    XLed::Instance()->ResetCache();

    // empty the Rx serial buffer before sending command
    int ret = ClearPort(*this, *GetCoreCallback(), XLed::Instance()->GetSerialPort().c_str());

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> ClearPort(Port = " << XLed::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // XLed Connected?
    unsigned char sConnection[XLed::XL_MaxPropSize];
    memset(sConnection, 0, XLed::XL_MaxPropSize);
    ret = ConnectXLed(sConnection);

    if (ret != DEVICE_OK) return ret;

    ret = ReadAllProperty();
    
    if (ret != DEVICE_OK) return ret;

    // Name
    char sCtrlNameLabel[120];
    memset(sCtrlNameLabel, 0, 120);
    sprintf(sCtrlNameLabel, "%s%s", XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardNameLabel).c_str(), MM::g_Keyword_Name);
    ret = CreateProperty(sCtrlNameLabel/*MM::g_Keyword_Name*/, XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardName).c_str(), MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << sCtrlNameLabel << "=" << XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardName).c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Description
    if (strcmp(XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardName).c_str(),  XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardDesc).c_str()) != 0)
    {

        char sCtrlDescLabel[120];
        memset(sCtrlDescLabel, 0, 120);
        sprintf(sCtrlDescLabel, "%s%s", XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardDescLabel).c_str(), MM::g_Keyword_Description);
        ret = CreateProperty(sCtrlDescLabel/*MM::g_Keyword_Description*/, XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardDesc).c_str(), MM::String, true);

        if (nDebugLog > 0)
        {
            osMessage.str("");
            osMessage << "<XLedCtrl::Initialize> CreateProperty(" << sCtrlDescLabel << "=" << XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardDesc).c_str() << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK)  return ret;
    }

    // Debug Log Flag
    CPropertyAction* pAct = new CPropertyAction(this, &XLedCtrl::OnDebugLogFlag);

    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_XLedDebugLogFlag).c_str(), "1", MM::Integer, false, pAct);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedDebugLogFlag).c_str() << "=" << XLed::Instance()->GetDebugLogFlag() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // set XLED connected flag
    char sConnected[8];
    bool yConnected = (sConnection[0] == 0x65) ? false : true;
    strcpy(sConnected, (yConnected) ? "TRUE" : "FALSE");
    XLed::Instance()->SetXLedConnected(yConnected);

    // Create read-only property for control board connection
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardConnLabel).c_str(), sConnected, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_CtrlBoardConnLabel).c_str() << "=" << sConnected << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // get serial number
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_UnitSerialNo);

    // Create read-only property for serial number info
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_SerialNumberLabel).c_str(), (const char*)sResp, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_SerialNumberLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create XLED1 Unit Status property
    long lLedStatus = 0;
    char sXLedStatus[XLed::XL_MaxPropSize];
    memset(sXLedStatus, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitStatus);
    if (GetXLedStatus(sResp, sXLedStatus) == NULL)
    {
        memset(sXLedStatus, 0, XLed::XL_MaxPropSize);
        strcpy(sXLedStatus, "Undefined");
    }
    else
    {
        lLedStatus = atol((const char*)sXLedStatus);
        memset(sXLedStatus, 0, XLed::XL_MaxPropSize);
        sprintf(sXLedStatus, "%04lx", lLedStatus);
    }

    pAct = new CPropertyAction(this, &XLedCtrl::OnState);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusLabel).c_str(), (const char*)sXLedStatus, MM::Integer, false, pAct);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusLabel).c_str() << "=" << sXLedStatus << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    char sStatus[800];
    memset(sStatus, 0, 800);
    GetStatusDescription(lLedStatus, sStatus);

    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusDescLabel).c_str(), (const char*)sStatus, MM::String, false);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusDescLabel).c_str() << "=" << lLedStatus << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
        
    // get Led Device Software Version
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitSoftVer);
    // Create read-only property for XLed software version info
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_UnitSoftVerLabel).c_str(), (const char*)sResp, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_UnitSoftVerLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // get Led Device Software Version
    // sResp = XLed::Instance()->GetParameter(XLed::XL_UnitSoftVer);

    // Create read-only property for XLed software version info
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_XLedSoftVerLabel).c_str(), XLed::Instance()->GetXLedStr(XLed::XL_XLedSoftVer).c_str(), MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedSoftVerLabel).c_str();
        osMessage << "=" << XLed::Instance()->GetXLedStr(XLed::XL_XLedSoftVer).c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // get All On/Off
    char sAllOnOff[8];
    sprintf(sAllOnOff, "%ld", m_lAllOnOff);
    pAct = new CPropertyAction(this, &XLedCtrl::OnAllOnOff);

    // Create all on/off property
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_AllOnOffLabel).c_str(), (const char*)sAllOnOff, MM::Integer, false, pAct);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_AllOnOffLabel).c_str() << "=" << sAllOnOff << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // get PWM Status
    //sResp = XLed::Instance()->GetParameter(XLed::XL_PWMStat);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnPWMStatus);

    // Create read-only property for PWM status
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // get PWM Mode
    //sResp = XLed::Instance()->GetParameter(XLed::XL_PWMMode);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnPWMMode);

    // Create read-only property for PWM mode
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_PWMModeLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMModeLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // get Front Panel lock status
    sResp = XLed::Instance()->GetParameter(XLed::XL_FrontPanel);
    pAct = new CPropertyAction(this, &XLedCtrl::OnFrontPanelLock);

    // Create read-only property for front panel info
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // get LCD Screen info
    //sResp = XLed::Instance()->GetParameter(XLed::XL_LCDScrnNo);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnLCDScrnNumber);

    // Create read-only property for LCD screen info
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnNumberLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnNumberLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // get LCD Brightness info
    //sResp = XLed::Instance()->GetParameter(XLed::XL_LCDBrite);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnLCDScrnBrite);

    // Create read-only property for LCD brightness info
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnBriteLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnBriteLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // get LCD Screen Saver info
    //sResp = XLed::Instance()->GetParameter(XLed::XL_LCDSaver);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnLCDScrnSaver);

    // Create read-only property for LCD screen saver info
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnSaverLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnSaverLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // get Clear Alarm info
    sResp = XLed::Instance()->GetParameter(XLed::XL_ClearAlarm);
    pAct = new CPropertyAction(this, &XLedCtrl::OnClearAlarm);
   
    // Create read-only property for clear alarm info
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_ClearAlarmLebel).c_str(), (const char*)sResp, MM::Integer, false, pAct);
    
    if (nDebugLog > 0)
    {
         osMessage.str("");
         osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_ClearAlarmLebel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
         this->LogMessage(osMessage.str().c_str());
    }
    
    if (ret != DEVICE_OK) return ret;

    // get Speaker Volume info
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SpeakVol);
    //pAct = new CPropertyAction(this, &XLedCtrl::OnSpeakerVolume);

    // Create read-only property for speaker volume info
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_SpeakerVolumeLabel).c_str(), (const char*)sResp, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedCtrl::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_SpeakerVolumeLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    ret = UpdateStatus();

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::Initialize> UpdateStatus(); RetuenCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    m_yInitialized = true;

    return DEVICE_OK;
}

//
// check for valid communication port
//
int XLedCtrl::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(XLed::Instance()->GetSerialPort().c_str());
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPort> (BeforeGet::PORT=<" << XLed::Instance()->GetSerialPort().c_str() << ">";
            osMessage << " PROPSET=<" << XLed::Instance()->GetSerialPort().c_str() << ">)";
        }
    }
    else if (pAct == MM::AfterSet)
    {
        osMessage << "<XLedCtrl::OnPort> (AfterSet::PORT=<" << XLed::Instance()->GetSerialPort().c_str() << ">";
        if (m_yInitialized)
        {
            pProp->Set(XLed::Instance()->GetSerialPort().c_str());
            if (nDebugLog > 1)
            {
                osMessage << "Initialized::SET=<" << XLed::Instance()->GetSerialPort().c_str() << ">";
            }
            return DEVICE_INVALID_INPUT_PARAM;
        }

        pProp->Get(XLed::Instance()->GetSerialPort());

        if (nDebugLog > 1)
        {
            osMessage << " PROPGET=<" << XLed::Instance()->GetSerialPort().c_str() << ">)";
        }
    }

    if (nDebugLog > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// lock/unlock the fron panel
//
int XLedCtrl::OnDebugLogFlag(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    long lDebugLogFlag = (long)XLed::Instance()->GetDebugLogFlag();

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(lDebugLogFlag);

        if (lDebugLogFlag > 1)
        {
            osMessage << "<XLedCtrl::OnDebugLogFlag> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_XLedDebugLogFlag).c_str() << "> PROPSET=<" << lDebugLogFlag << ">)";
        }
    }
    else if (pAct == MM::AfterSet)
    {
        pProp->Get(lDebugLogFlag);
        XLed::Instance()->SetDebugLogFlag((int)lDebugLogFlag);
       if (lDebugLogFlag > 1)
       {
           osMessage << "<XLedCtrl::OnDebugLogFlag> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_XLedDebugLogFlag).c_str() << "> PROPGET=<" << lDebugLogFlag << ">)";
       }
    }
    
    if (lDebugLogFlag > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// Update Led Status Byte
//
int XLedCtrl::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    // Get Led Status Byte
    unsigned char sCmd[8] = { 0x75, 0x73, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_UnitStatus);
    char sXLedStatus[XLed::XL_MaxPropSize];
    memset(sXLedStatus, 0, XLed::XL_MaxPropSize);
    int ret = DEVICE_OK;

    if (eAct == MM::BeforeGet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Status
        ret = XLedSerialIO(sCmd, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnState> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Status
        ret = XLedSerialIO(sCmd, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnState> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }

    if (ret != DEVICE_OK) return ret;

    memset(sXLedStatus, 0, XLed::XL_MaxPropSize);

    // Get XLed Status
    if (GetXLedStatus(sResp, sXLedStatus) != NULL)
    {
        long lStatus = atol((const char*)sXLedStatus);
        pProp->Set(lStatus);

        char sStatus[800];
        memset(sStatus, 0, 800);
        GetStatusDescription(lStatus, sStatus);

        SetProperty(XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusDescLabel).c_str(), (const char*) sStatus);
    }
    else
    {
        strcpy(sXLedStatus, "Undefined");
    }

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnState> (" << XLed::Instance()->GetXLedStr(XLed::XL_XLedStatusDescLabel).c_str() << "="  << sXLedStatus << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// lock/unlock the fron panel
//
int XLedCtrl::OnAllOnOff(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdSet[16] = { 0x6F, 0x6E, 0x3D, 0x61, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        pProp->Set(m_lAllOnOff);
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_AllOnOffLabel).c_str() << "> PROPSET=<" << m_lAllOnOff << ">)";
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lAllOnOff;
        pProp->Get(lAllOnOff);
        sCmdSet[1] = (lAllOnOff > 0) ? 0x6E : 0x66;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (ret != DEVICE_OK) return ret;

        if (sResp[0] == 0x0D)
        {
            m_lAllOnOff = (lAllOnOff > 0) ? 1 : 0;
            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnAllOnOff> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_AllOnOffLabel).c_str() << "> PROPGET=<" << lAllOnOff << ">)";
            }
            m_lAllOnOff = lAllOnOff;
        }
        else
        {
            char sHex[3];
            XLed::Instance()->Byte2Hex(sResp[0],sHex);
            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnAllOnOff> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_AllOnOffLabel).c_str() << "> PROPGET=<" << sHex << "{rejected}>)";
            }
        }
    }
    
    if (nDebugLog > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// check for the PWM status
//
int XLedCtrl::OnPWMStatus(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x69, 0x73, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x69, 0x73, 0x3D, 0x30, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_PWMStat);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (BeforeGet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << " = " << sResp << ">), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

    }
    else if (pAct == MM::AfterSet)
    {
        long lPWMState;
        pProp->Get(lPWMState);

        if (lPWMState < 0) lPWMState = 0;
        if (lPWMState > 1) lPWMState = 1;

        sCmdSet[3] = (unsigned char)lPWMState + 0x30;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (AfterSet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << " = [" << lPWMState << "," << sResp << "]>), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << " = " << sResp << ">), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }

    if (sResp[0] >= 0x30 && sResp[0] <= 0x31)
    {
        m_lPWMState = (long)(sResp[0] - 0x30);

        pProp->Set(m_lPWMState);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus>::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << " = " << m_lPWMState << ">)";
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else
    {
        char sHex[3];
        XLed::Instance()->Byte2Hex(sResp[0],sHex);
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus><" << XLed::Instance()->GetXLedStr(XLed::XL_PWMStateLabel).c_str() << " = [" << sHex << ",{Undefined PWM State}]>)";
            this->LogMessage(osMessage.str().c_str());
        }
    }

    return DEVICE_OK;
}

/*
//
// check for the PWM running mode
//
int XLedCtrl::OnPWMMode(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[6] = { 0x73, 0x63, 0x3F, XLed::XL_TxTerm, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x73, 0x63, 0x3D, 0x30, XLed::XL_TxTerm, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_PWMMode);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        //memset(sResp, 0, XLed::XL_MaxPropSize);
        //ret = XLedSerialIO(sCmdGet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (BeforeGet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMModeLabel).c_str() << " = " << sResp << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        //if (ret != DEVICE_OK) return ret;
    }
    else if (pAct == MM::AfterSet)
    {
        long lPWMMode;
        pProp->Get(lPWMMode);

        if (lPWMMode < 0) lPWMMode = 0;
        if (lPWMMode > 1) lPWMMode = 1;

        sCmdSet[3] = (unsigned char)lPWMMode + 0x30;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (AfterSet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << " = " << lPWMMode << ">), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus> (AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << " = " << sResp << ">), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }

    if (sResp[0] >= 0x30 && sResp[0] <= 0x31)
    {
        m_lPWMMode = (long)(sResp[0] - 0x30);
        pProp->Set(m_lPWMMode);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus>::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMModeLabel).c_str() << " = " << m_lPWMMode << ">)";
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else
    {
        char sHex[3];
        XLed::Instance()->Byte2Hex(sResp[0], sHex);
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnPWMStatus>::<" << XLed::Instance()->GetXLedStr(XLed::XL_PWMModeLabel).c_str() << " = [" << sHex << ",{Undefined PWM Mode}]>)";
            this->LogMessage(osMessage.str().c_str());
        }
    }

    return DEVICE_OK;
} */

//
// lock/unlock the fron panel
//
int XLedCtrl::OnFrontPanelLock(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x6C, 0x6F, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x6C, 0x6F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_FrontPanel);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        // ret = XLedSerialIO(sCmdGet, sResp);
        // if (ret != DEVICE_OK) return ret;
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel> (BeforeGet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << " = " << sResp << ">)";
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lScrnLock;
        pProp->Get(lScrnLock);

        if (lScrnLock > 0)
        {
            sCmdSet[0] = 0x6C;
            sCmdSet[1] = 0x6F;
        }
        else
        {
            sCmdSet[0] = 0x75;
            sCmdSet[1] = 0x6C;
        }

        ret = XLedSerialIO(sCmdSet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel> (AfterSet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << " = [" << lScrnLock << "," << sResp << "]>), Return = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel> (AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << " = " << sResp << ">), Return =" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }

    if (sResp[0] >= 0x30 && sResp[0] <= 0x31)
    {
        m_lScrnLock = (long)(sResp[0] - 0x30);
        pProp->Set(m_lScrnLock);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel>::<" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << " = " << m_lScrnLock << ">)";
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else
    {
        char sHex[3];
        XLed::Instance()->Byte2Hex(sResp[0], sHex);
        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnFrontPanel>:<" << XLed::Instance()->GetXLedStr(XLed::XL_FrontPanelLabel).c_str() << " = [" << sHex << ",{Undefined Value}>)";
            this->LogMessage(osMessage.str().c_str());
        }
    }

    return DEVICE_OK;
}

//
// check for the pLCD screen
//
int XLedCtrl::OnLCDScrnNumber(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x73, 0x73, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x73, 0x73, 0x3D, 0x32, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LCDScrnNo);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        // ret = XLedSerialIO(sCmdGet, sResp);
        // if (ret != DEVICE_OK) return ret;
        int lScrnNumber = atol((const char*)sResp);
        if (lScrnNumber >= 2 && lScrnNumber <= 13)
        {
            m_lScrnNumber = lScrnNumber;
            pProp->Set(m_lScrnNumber);
            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnLCDScrnNumber> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnNumberLabel).c_str() << ">";
                osMessage << " PROPSET=<" << m_lScrnNumber << ">)";
            }
        }
        else
        {
            char sHex[3];
            XLed::Instance()->Byte2Hex(sResp[0], sHex);
            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnLCDScrnNumber> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnNumberLabel).c_str() << "> PROPSET=<" << sHex << "{Undefined Scrn Number}>)";
            }
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lScrnNumber;
        pProp->Get(lScrnNumber);

        if (lScrnNumber < 2)  lScrnNumber = 2;
        if (lScrnNumber > 13) lScrnNumber = 13;
        if (lScrnNumber == 7) lScrnNumber = 6;

        sprintf((char*)&sCmdSet[3], "%ld", lScrnNumber);
        sCmdSet[strlen((char*)sCmdSet)] = (unsigned char)XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);
        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);
        if (ret != DEVICE_OK) return ret;

        m_lScrnNumber = atol((const char*)sResp);

        pProp->Set(m_lScrnNumber);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnLCDScrnNumber> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnNumberLabel).c_str() << "> PROPGET=<" << lScrnNumber << "," << m_lScrnNumber << ">)";
        }
    }

    if (nDebugLog > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// check for the LCD brightness
//
int XLedCtrl::OnLCDScrnBrite(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x6C, 0x62, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00};
    unsigned char sCmdSet[16] = { 0x6C, 0x62, 0x3D, 0x30, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LCDBrite);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        //ret = XLedSerialIO(sCmdGet, sResp);
        //if (ret != DEVICE_OK) return ret;
        long lBrite = atol((const char*)sResp);
        if (lBrite >= 0 && lBrite <= 255)
        {
            m_lScrnBrite = lBrite;
            pProp->Set(m_lScrnBrite);

            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnLCDBrigntness> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnBriteLabel).c_str() << ">";
                osMessage << " PROPSET=<" << m_lScrnBrite << ">)";
            }
        }
        else
        {
            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnLCDBrigntness> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnBriteLabel).c_str() << ">=<" << sResp << "{Undefined Mode}>)";
            }
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lScrnBrite;
        pProp->Get(lScrnBrite);

        if (lScrnBrite < 0) lScrnBrite = 0;
        if (lScrnBrite > 255) lScrnBrite = 255;

        sprintf((char*)&sCmdSet[3], "%ld", lScrnBrite);
        sCmdSet[strlen((char*)sCmdSet)] = (unsigned char)XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);
        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);
        if (ret != DEVICE_OK) return ret;

        m_lScrnBrite = atol((const char*)sResp);

        pProp->Set(m_lScrnBrite);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnLCDBrigntness> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnBriteLabel).c_str() << "> PROPGET=<" << lScrnBrite << "," << m_lScrnBrite << ">)";
            this->LogMessage(osMessage.str().c_str());
        }
    }

    return DEVICE_OK;
}

//
// check for the LCD screen saver
//
int XLedCtrl::OnLCDScrnSaver(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x73, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00};
    unsigned char sCmdSet[16] = { 0x73, 0x74, 0x3D, 0x00, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LCDSaver);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        //ret = XLedSerialIO(sCmdGet, sResp);
        //if (ret != DEVICE_OK) return ret;
        m_lScrnTimeout = atol((const char*)sResp);

        pProp->Set(m_lScrnTimeout);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnLCDBrigntness> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnSaverLabel).c_str() << ">";
            osMessage << " PROPSET=<" << m_lScrnTimeout << ">)";
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lScrnTimeout; 
        pProp->Get(lScrnTimeout);

        if (lScrnTimeout < 0) lScrnTimeout = 0;
        if (lScrnTimeout > 9999) lScrnTimeout = 9999;

        sprintf((char*)&sCmdSet[3], "%ld", lScrnTimeout);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);
        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = XLedSerialIO(sCmdGet, sResp);
        if (ret != DEVICE_OK) return ret;

        m_lScrnTimeout = atol((const char*)sResp);

        pProp->Set(m_lScrnTimeout);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnLCDBrigntness> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_LCDScrnSaverLabel).c_str() << "> PROPGET=<" << lScrnTimeout << "," << m_lScrnTimeout << ">)";
        }
    }

    if (nDebugLog > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// clear alarm
//
int XLedCtrl::OnClearAlarm(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmd[8] = { 0x63, 0x61, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00};
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_ClearAlarm);
    int ret = DEVICE_OK;
    long lClear = 0;

    if (pAct == MM::BeforeGet)
    {
        if (sResp[0] == 0x0D) lClear = 0;
        else lClear = 1;

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnClearAlarm> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_ClearAlarmLebel).c_str() << "> = [" << sResp << "," << lClear <<  "])";
            this->LogMessage(osMessage.str().c_str());
        }
   
        pProp->Set(lClear);

    }
    else if (pAct == MM::AfterSet)
    {
        pProp->Get(lClear);

        ret = XLedSerialIO(sCmd, sResp);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnClearAlarm> (AfterSet(1)::<" << XLed::Instance()->GetXLedStr(XLed::XL_ClearAlarmLebel).c_str() << "> = [" << sResp << "])";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
   
        if (sResp[0] == 0x0D) lClear = 0;
        else lClear = 1;

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnClearAlarm> (AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_ClearAlarmLebel).c_str() << "> = [" << lClear << "])";
            this->LogMessage(osMessage.str().c_str());
        }
   
        pProp->Set(lClear);

    }

    return DEVICE_OK;
}

//
// check for speaker volume
//
int XLedCtrl::OnSpeakerVolume(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    unsigned char sCmdGet[8] = { 0x76, 0x6F, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00};
    unsigned char sCmdSet[16] = { 0x76, 0x6F, 0x3D, 0x30, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SpeakVol);
    int ret = DEVICE_OK;

    if (pAct == MM::BeforeGet)
    {
        //ret = XLedSerialIO(sCmdGet, sResp);
        //if (ret != DEVICE_OK) return ret;
        long lSpeakerVol = atol((const char*)sResp);
        if (lSpeakerVol >= 0 && lSpeakerVol <= 255)
        {
            m_lSpeakerVol = lSpeakerVol;
            pProp->Set(m_lSpeakerVol);

            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnSpeakerVolume> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_SpeakerVolumeLabel).c_str() << ">";
                osMessage << " PROPSET=<" << m_lSpeakerVol << ">)";
            }
        }
        else
        {
            char sHex[3];
            XLed::Instance()->Byte2Hex(sResp[0], sHex);

            if (nDebugLog > 1)
            {
                osMessage << "<XLedCtrl::OnSpeakerVolume> (BeforeGet::<" << XLed::Instance()->GetXLedStr(XLed::XL_SpeakerVolumeLabel).c_str() << "> PROPSET=<" << sHex << "{Undefined Value}>)";
            }
        }
    }
    else if (pAct == MM::AfterSet)
    {
        long lSPeakerVol;
        pProp->Get(lSPeakerVol);

        if (lSPeakerVol < 0) lSPeakerVol = 0;
        if (lSPeakerVol > 255) lSPeakerVol = 255;

        sprintf((char*)&sCmdSet[3], "%ld", lSPeakerVol);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);
        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);
        
        ret = XLedSerialIO(sCmdGet, sResp);
        if (ret != DEVICE_OK) return ret;

        m_lSpeakerVol = atol((const char*)sResp);

        pProp->Set(m_lSpeakerVol);

        if (nDebugLog > 1)
        {
            osMessage << "<XLedCtrl::OnSpeakerVolume> (AfterSet::<" << XLed::Instance()->GetXLedStr(XLed::XL_SpeakerVolumeLabel).c_str() << "> PROPGET=<" << lSPeakerVol << "," << m_lSpeakerVol << ">)";
        }
    }

    if (nDebugLog > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

int XLedCtrl::ConnectXLed(unsigned char* sResp)
{
    unsigned char sCmdDC[8] = { 0x64, 0x63, (char)XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdCO[8] = { 0x63, 0x6F, (char)XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00, 0x00 };
 
    // send out disconnect command
    // to make sure XLED unit is disconnected
    XLedSerialIO(sCmdDC, sResp);

    // send out connect command
    // to connect the XLED unit
    int ret = XLedSerialIO(sCmdCO, sResp);

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// send a command and read responded message
//
int XLedCtrl::XLedSerialIO(unsigned char* sCmd, unsigned char* sResp)
{
    int nTrys = 0;
    bool yDevErr = false;

    do {
        nTrys++;
        // send command to disconnect XLed
        int ret = WriteCommand(sCmd);
        if (ret != DEVICE_OK) return ret;

        // Sleep(5);

        // read returned Message
        ret = ReadMessage(sResp);
        if (ret != DEVICE_OK) return ret;

        yDevErr = sResp[0] == 0x65 && sResp[1] == 0x0D;

    } while (yDevErr && nTrys <= 3);

    // if (yDevErr) return DEVICE_ERR;
    
    return DEVICE_OK;
}


//
// write a command string to serial port
//
int XLedCtrl::WriteCommand(const unsigned char* sCommand)
{
    int ret = DEVICE_OK;
    std::size_t nCmdLength = strlen((const char*)sCommand);
    ostringstream osMessage;

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        char sHex[3];
        osMessage.str("");
        osMessage << "<XLedCtrl::WriteCommand> (cmd ";
        for (unsigned n=0; n < nCmdLength; n++)
        {
            XLed::Byte2Hex(sCommand[n], sHex);
            osMessage << "[" << n << "]=<" << sHex << ">";
        }
        osMessage << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    // write command out
    ret = DEVICE_OK;
    for (unsigned nByte = 0; nByte < nCmdLength && ret == DEVICE_OK; nByte++)
    {
        ret = WriteToComPort(XLed::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCommand[nByte], 1);
        // CDeviceUtils::SleepMs(1);
    }

    return ret;
}

//
// Read responded message
//
int XLedCtrl::ReadMessage(unsigned char* sMessage)
{
    // block/wait for acknowledge, or until we time out;
    unsigned int nLength = 256;
    unsigned char sAnswer[256];
    memset(sAnswer, 0, nLength);
    unsigned long lRead = 0;
    unsigned long lStartTime = GetClockTicksUs();

    char sHex[6];
    ostringstream osMessage;
    int ret = DEVICE_OK;
    bool yRead = false;
    bool yTimeout = false;
    while (!yRead && !yTimeout && ret == DEVICE_OK )
    {
        unsigned long lByteRead;

        const MM::Device* pDevice = this;
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, XLed::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)(nLength-lRead), lByteRead);
       
        //if (XLed::Instance()->GetDebugLogFlag() > 1)
        //{
        //    osMessage.str("");
        //    osMessage << "<XLedCtrl::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
        //}
        for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)
        {
            yRead = yRead || sAnswer[lRead+lIndx] == XLed::XL_RxTerm;
            //if (XLed::Instance()->GetDebugLogFlag() > 1) 
            //{
            //    XLed::Instance()->Byte2Hex(sAnswer[lRead+lIndx], sHex);
            //    osMessage << "[" << sHex  << "]";
            //}
        }
        //if (XLed::Instance()->GetDebugLogFlag() > 1)
        //{
        //    osMessage << "> (" << yRead << ")";
        //    this->LogMessage(osMessage.str().c_str());
        //}

        if (ret == DEVICE_OK && lByteRead > 0)
        {
            // yRead = strchr((char*)&sAnswer[lRead], XLed::XL_RxTerm) != NULL; // don't change the following order
            lRead += lByteRead;                                                  // otherwise message will not resturned
            if (yRead) break;
        }

        yTimeout = ((GetClockTicksUs() - lStartTime) / 1000) > m_dAnswerTimeoutMs;

        // delay 1ms
        if (!yTimeout) CDeviceUtils::SleepMs(1);
    }

    //if (!yRead || yTimeout) return DEVICE_SERIAL_TIMEOUT;

    //XLed::Instance()->ByteCopy(sMessage, sAnswer, 10);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedCtrl::ReadMessage> (ReadFromSerial = (" << lRead << "," << yTimeout << ") <";
    }
    for (unsigned long lIndx=0; lIndx < lRead; lIndx++)
    {
        sMessage[lIndx] = sAnswer[lIndx];
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            XLed::Instance()->Byte2Hex(sMessage[lIndx], sHex);
            osMessage << "[" << sHex  << "]";
            //XLed::Instance()->Byte2Hex(sAnswer[lIndx], sHex);
            //osMessage << sHex  << "]";
        }
        if (sAnswer[lIndx] == XLed::XL_RxTerm) break;
    }
    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage << ">";
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}
