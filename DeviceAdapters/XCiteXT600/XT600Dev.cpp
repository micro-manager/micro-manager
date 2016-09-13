///////////////////////////////////////////////////////////////////////////////
// FILE:          XLedDev.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implementation of X-Cite Led Device Class
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
#include "../../MMDevice/ModuleInterface.h"
//#include "../../MMDevice/DeviceUtils.h"
#include "XT600.h"
//#include "XLedCtrl.h"
#include "XT600Dev.h"

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// C400 Channel
///////////////////////////////////////////////////////////////////////////////
//
// C400 Channel - single axis stage device.
// Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
// system with the X and Y axis going the 'Micro-Manager standard' direction
// Then, there is the MP285s native system.  All functions using 'steps' use the MP285 system
// All functions using Um use the Micro-Manager coordinate system
//

//
// Single axis stage constructor
//
XLedDev::XLedDev(int nLedDevNumber) :
    m_yInitialized(false),
    m_dAnswerTimeoutMs(5000.),
    m_nLedDevNumber(nLedDevNumber),
    m_lOnOffState(0),                     // Led on/off state
    m_lIntensity(0),                      // Led intensity
    m_lSignalDelayTime(0),                // Led signal delay time
    m_lSignalOnTime(0),                   // Led signal on time
    m_lSignalOffTime(0),                  // Led signal off time
    m_lTriggerDelay(0),                   // Led trigger delay time
    m_lPWMUnit(0),                        // Led PWM unit
	m_lTriggerState(0)					  // Led Trigger Sequence
{
    InitializeDefaultErrorMessages();
}


//
// channel destructor
//
XLedDev::~XLedDev()
{
    Shutdown();
    m_yInitialized = false;
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

//
// channel initialization
//
int XLedDev::Initialize()
{
    int ret = DEVICE_OK;
    int nDebugLog = XLed::Instance()->GetDebugLogFlag();
    std::ostringstream osMessage;

    // Led Device Name
    char sLedDevNameLabel[120];
    memset(sLedDevNameLabel, 0, 120);
    sprintf(sLedDevNameLabel, "%s%s", XLed::Instance()->GetXLedStr(XLed::XL_LedDevNameLabel).c_str(), MM::g_Keyword_Name);
    ret = CreateProperty(sLedDevNameLabel, XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str(), MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << sLedDevNameLabel << "=" << XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str() << "), ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Description
    if (strcmp(XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str(), XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str()) != 0)
    {
        char sLedDevDescLabel[120];
        memset(sLedDevDescLabel, 0, 120);
        sprintf(sLedDevDescLabel, "%s%s", XLed::Instance()->GetXLedStr(XLed::XL_LedDevDescLabel).c_str(), MM::g_Keyword_Description);
        ret = CreateProperty(sLedDevDescLabel, XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str(), MM::String, true);

        if (nDebugLog > 0)
        {
            osMessage.str("");
            osMessage << "<XLedDev::Initialize> CreateProperty(" << MM::g_Keyword_Description << XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str() << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }

    // Led Device Name
    char sParm[XLed::XL_MaxPropSize];
    memset(sParm, 0, XLed::XL_MaxPropSize);
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LedName);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedDevDescLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedDevNameLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Type
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_LedType);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedDevTypeLabel).c_str(), (const char*)sParm, MM::String, true);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedDevTypeLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Wavelength
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedWaveLength);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedWaveLengthLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedWaveLengthLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Serial Number
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_LedSerialNo);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedSerialNoLabel).c_str(), (const char*)sParm, MM::String, true);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedSerialNoLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Manufacturing Date
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMakeDate);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedMfgDateLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedMfgDateLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Minimum Allowed Pulse Width
    // memset(sParm, 0, XLed::XL_MaxPropSize);

    // sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinPulseWidth);
    // if (ret != DEVICE_OK) strcpy((char*)sResp, sParm);
    // ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedPulseWidthLabel).c_str(), (const char*)sParm, MM::Integer, true);

    // if (nDebugLog > 0)
    // {
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedPulseWidthLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    // }

    // if (ret != DEVICE_OK) return ret;

    // Led Device Full Width Half Maximum Value
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedFWHM);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedFWHMLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedFWHMLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Maximum Allowed Temperature
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMaxTemp);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedMaxTempLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedMaxTempLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Minimum Allowed Temperature
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinTemp);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedMinTempLabel).c_str(), (const char*)sParm, MM::String, true);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedMinTempLabel).c_str() << " = [" << sParm << "] ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create Led Status property
    long lLedStatus = 0;
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_UnitStatus);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK)
        strcpy(sParm, "Undefined");
    else
    {
        lLedStatus = atol((const char*)sParm);
        memset(sParm, 0, XLed::XL_MaxPropSize);
        sprintf(sParm, "%02lx", lLedStatus);
    }

    CPropertyAction* pAct = new CPropertyAction(this, &XLedDev::OnState);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedStatusLabel).c_str(), (const char*)sParm, MM::Integer, true, pAct);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedStatusLabel).c_str() << "=" << sParm << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    char sStatus[400];
    memset(sStatus, 0, 400);

    GetStatusDescription(lLedStatus, sStatus);

    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedStatusDescLabel).c_str(), (const char*)sStatus, MM::String, true);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedStatusDescLabel).c_str() << "=" << lLedStatus << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create Led Hours property
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedHours);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");

    //pAct = new CPropertyAction(this, &XLedDev::OnHours);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str(), (const char*)sParm, MM::Integer, false, pAct);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str(), (const char*)sParm, MM::Integer, true);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str() << "=" << sParm << ", ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create Led Minimum intensity property
    char sMinIntensity[20];
	double dMinIntensity;

    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedMinIntensity);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    else
    {
        m_lIntensity = atol(sParm);
        dMinIntensity = (double) m_lIntensity / 10.0;
        sprintf(sMinIntensity, "%.1f", dMinIntensity);
    }
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedMinIntensityLabel).c_str() , (const char*)sMinIntensity, MM::String, false, pAct);


    //pAct = new CPropertyAction(this, &XLedDev::OnHours);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str(), (const char*)sParm, MM::Integer, false, pAct);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedMinIntensityLabel).c_str(), (const char*)sMinIntensity, MM::Integer, true);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedMinIntensityLabel).c_str() << "=" << sParm << ", ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;


    // Create Led Temperature property
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTemperature);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");

    pAct = new CPropertyAction(this, &XLedDev::OnTemperature);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str(), (const char*)sParm, MM::Integer, true, pAct);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str() << "=" << sParm << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create Led Temperature Hysteresis property
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTempHyst);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) strcpy(sParm, "Undefined");

    //pAct = new CPropertyAction(this, &XLedDev::OnTempHyst);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str(), (const char*)sParm, MM::Integer, false, pAct);
    ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str(), (const char*)sParm, MM::Integer, true);
    
    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=" << sParm << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;


    // Led Trigger Sequence
	int nTriggerSequence;
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedTriggerSequence);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) {
		strcpy(sParm, "Undefined");
		nTriggerSequence = 0;
	} else {
		nTriggerSequence = atoi(sParm);
	}

    pAct = new CPropertyAction (this, &XLedDev::OnLedTrigger);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() , (const char*)sParm, MM::Integer, false, pAct);
    ret = CreateIntegerProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() , nTriggerSequence, false, pAct);
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"0");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"1");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"2");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"3");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"4");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"5");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str(),"6");
	//SetPropertyLimits(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str(),0,1);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() << "=" << sParm << ") ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;	

    // Led On/Off State
	int nOnOff;
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) {
		strcpy(sParm, "Undefined");
		nOnOff = 0;
	} else {
		nOnOff = atoi(sParm);
	}

    pAct = new CPropertyAction (this, &XLedDev::OnLedOnOff);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() , (const char*)sParm, MM::Integer, false, pAct);
    ret = CreateIntegerProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() , nOnOff, false, pAct);
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str(),"0");
	AddAllowedValue(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str(),"1");
	//SetPropertyLimits(XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str(),0,1);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << sParm << ") ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device Intensity
    char sIntensity[20];
	double dIntensity;
    char sLedDevIntLabel[120];
 
	memset(sLedDevIntLabel, 0, 120);
    sprintf(sLedDevIntLabel, "%s%.01f - 100.0)%%", XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str(), dMinIntensity);

    memset(sIntensity, 0, 20);
    memset(sParm, 0, XLed::XL_MaxPropSize);
    sResp = XLed::Instance()->GetParameter(XLed::XL_LedIntensity);
    ret = GetLedParmVal(sResp, sParm);
    if (ret != DEVICE_OK) {
		dIntensity = 0.0;
		strcpy(sIntensity, "Undefined");
	}
    else
    {
        m_lIntensity = atol(sParm);
        dIntensity = (double) m_lIntensity / 10.0;
        //sprintf(sIntensity, "%.1f", dIntensity);
    }
    pAct = new CPropertyAction (this, &XLedDev::OnLedIntensity);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() , (const char*)sIntensity, MM::String, false, pAct);
    ret = CreateFloatProperty(sLedDevIntLabel , dIntensity, false, pAct);
	SetPropertyLimits(sLedDevIntLabel,0.0,100.0);
    //ret = CreateFloatProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() , dIntensity, false, pAct);
	//SetPropertyLimits(XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str(),0.0,100.0);

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() << "=" << sIntensity << ") ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Led Device PWM Unit
    //const char *sPWMUnit[] = { "uS", "ms", "S" };
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_PWMUnit);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sParm, "Undefined");
    //    m_lPWMUnit = 0;
    //}
    //else
    //{
    //    m_lPWMUnit = atol(sParm);
    //    if (m_lPWMUnit < 0) m_lPWMUnit = 0;
    //    if (m_lPWMUnit > 2) m_lPWMUnit = 2;
    //}

    //pAct = new CPropertyAction (this, &XLedDev::OnPWMUnit);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str(), (const char*)sParm, MM::Integer, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=" << sParm << ") ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}
    
    //if (ret != DEVICE_OK) return ret;

    // get Pulse Mode
    //char sPulseMode[20];
    //memset(sPulseMode, 0, 20);
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigOnTime);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sPulseMode, "Undefined");
    //    m_lPulseMode = 0;
    //}
    //else
    //{
    //    m_lPulseMode = atol(sParm);
    //    if (m_lPulseMode < 0) m_lPulseMode = 0;
    //    if (m_lPulseMode > 3) m_lPulseMode = 3;
    //    sprintf(sPulseMode, "%ld", m_lPulseMode);
    //}
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigPulseMode);
    //pAct = new CPropertyAction(this, &XLedDev::OnPulseMode);

    // Create pulse mode property
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str(), (const char*)sPulseMode, MM::Integer, false, pAct);
    
    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=" << sPulseMode << "), ReturnCode = " << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Signal Delay Time
    //char sSignalDelayTime[20];
    //memset(sSignalDelayTime, 0, 20);
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigDelayTime);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sSignalDelayTime, "Undefined");
    //    m_lSignalDelayTime = 0;
    //}
    //else
    //{
    //    m_lSignalDelayTime = atol(sParm);
    //    if (m_lSignalDelayTime < 0) m_lSignalDelayTime = 0;
    //    if (m_lSignalDelayTime > 1000) m_lSignalDelayTime = 1000;
    //    sprintf(sSignalDelayTime, "%ld%s", m_lSignalDelayTime, sPWMUnit[m_lPWMUnit]);
    //}

    //pAct = new CPropertyAction (this, &XLedDev::OnSignalDelayTime);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str(), (const char*)sSignalDelayTime, MM::String, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=" << sSignalDelayTime << ") ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Signal On Time
    //char sSignalOnTime[20];
    //memset(sSignalOnTime, 0, 20);
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigOnTime);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sSignalOnTime, "Undefined");
    //    m_lSignalOnTime = 0;
    //}
    //else
    //{
    //    m_lSignalOnTime = atol(sParm);
    //    if (m_lSignalOnTime < 0) m_lSignalOnTime = 0;
    //    if (m_lSignalOnTime > 1000) m_lSignalOnTime = 1000;
    //    sprintf(sSignalOnTime, "%ld%s", m_lSignalOnTime, sPWMUnit[m_lPWMUnit]);
    //}

    //pAct = new CPropertyAction (this, &XLedDev::OnSignalOnTime);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str(), (const char*)sSignalOnTime, MM::String, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << "=" << sSignalOnTime << ") ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Signal Off Time
    //char sSignalOffTime[20];
    //memset(sSignalOffTime, 0, 20);
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigOffTime);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sSignalOffTime, "Undefined");
    //    m_lSignalOffTime = 0;
    //}
    //else
    //{
    //    m_lSignalOffTime = atol(sParm);
    //    if (m_lSignalOffTime < 0) m_lSignalOffTime = 0;
    //    if (m_lSignalOffTime > 1000) m_lSignalOffTime = 1000;
    //    sprintf(sSignalOnTime, "%ld%s", m_lSignalOffTime, sPWMUnit[m_lPWMUnit]);
    //}

    //pAct = new CPropertyAction (this, &XLedDev::OnSignalOffTime);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str(), (const char*)sSignalOffTime, MM::String, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=" << sSignalOffTime << ") ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    // Led Device Trigger Delay Time
    //char sTriggerDelayTime[20];
    //memset(sTriggerDelayTime, 0, 20);
    //memset(sParm, 0, XLed::XL_MaxPropSize);
    //sResp = XLed::Instance()->GetParameter(XLed::XL_SigAdvTime);
    //ret = GetLedParmVal(sResp, sParm);
    //if (ret != DEVICE_OK) strcpy(sParm, "Undefined");
    //if (ret != DEVICE_OK)
    //{
    //    strcpy(sTriggerDelayTime, "Undefined");
    //    m_lTriggerDelay = 0;
    //}
    //else
    //{
    //    m_lTriggerDelay = atol(sParm);
    //    if (m_lTriggerDelay < 0) m_lTriggerDelay = 0;
    //    if (m_lTriggerDelay > 1000) m_lTriggerDelay = 1000;
    //    sprintf(sTriggerDelayTime, "%ld%s", m_lTriggerDelay, sPWMUnit[m_lPWMUnit]);
    //}

    //pAct = new CPropertyAction (this, &XLedDev::OnTriggerDelayTime);
    //ret = CreateProperty(XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str(), (const char*)sTriggerDelayTime, MM::String, false, pAct);

    //if (nDebugLog > 0)
    //{
    //    osMessage.str("");
    //    osMessage << "<XLedDev::Initialize> CreateProperty(" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=" << sTriggerDelayTime << ") ReturnCode=" << ret;
    //    this->LogMessage(osMessage.str().c_str());
    //}

    //if (ret != DEVICE_OK) return ret;

    ret = UpdateStatus();

    if (nDebugLog > 0)
    {
        osMessage.str("");
        osMessage << "<XLedDev::Initialize> UpdateStatus(); ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    m_yInitialized = true;

    return DEVICE_OK;
}

int XLedDev::SetOpen(bool yOpen)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8] = { 0x6F, 0x6E, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x6F, 0x6E, 0x3D, 0x31, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    sCmdSet[1] = (yOpen) ? 0x6E : 0x66;
    sCmdSet[3] += (unsigned char)m_nLedDevNumber;

    ret = XLedSerialIO(sCmdSet, sResp);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage << "<XLedDev::SetOpen>::<" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << yOpen << "), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    memset(sResp, 0, XLed::XL_MaxPropSize);

    ret = GetLedParm(sCmdGet, sResp, sParm);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage << "<XLedDev::SetOpen>::<" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << sParm << "), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    m_lOnOffState = (sParm[0] == '0') ? 0 : 1;

    return DEVICE_OK;
}

int XLedDev::GetOpen(bool& yOpen)
{
    std::ostringstream osMessage;
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    ret = GetLedParmVal(sResp, sParm);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage << "<XLedDev::OnLedOnOff> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret !=DEVICE_OK) return ret;

    if (*sParm == '0')
    {
        m_lOnOffState = 0;
        yOpen = false;
    }
    else
    {
        m_lOnOffState = 1;
        yOpen = true;
    }

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnLedOnOff> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=<" << sParm << "," << m_lOnOffState << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}


//
// shutdown the Led device
//
int XLedDev::Shutdown()
{ 
    m_yInitialized = false;

    return DEVICE_OK;
}

//
// Get the Led device name 
//
void XLedDev::GetName(char* Name) const
{
    CDeviceUtils::CopyLimitedString(Name, XLed::Instance()->GetXLedStr(XLed::XL_ULedDevName + m_nLedDevNumber).c_str());
}

//
// filter out Led parameter Value
//
int XLedDev::GetLedParmVal(unsigned char* sResp, char* sParm)
{
    std::ostringstream osMessage;
    char sData[60];

    if (sResp == NULL || sParm == NULL) return DEVICE_ERR;

    memset(sData, 0, 60);

    // locate the string section describe the Led parameter
    strcpy(sData, (char*)sResp);

    char* sValue = sData;
    int nDev = 0;
    for (nDev = 0; nDev < m_nLedDevNumber && sValue != NULL; nDev++)
    {
        sValue = strchr((char*)sValue, ',');
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::GetLedParm> Dev(" << m_nLedDevNumber << "," << nDev << ")";
            if (sValue != NULL) osMessage << "<" << sValue << ">";
            else osMessage << "<NULL>";
            this->LogMessage(osMessage.str().c_str());
        }
        if (sValue != NULL) sValue++;
    }

    // fill in the NULL to terminate string
    if (sValue != NULL) 
    {
        char* sNext = strchr(sValue, ',');
        if (sNext != NULL) *sNext = '\0';;
    }

    if (sValue != NULL) strcpy(sParm, sValue);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        if (sValue == NULL) osMessage << "<XLedDev::GetLedParm> sValue(NULL)";
        else osMessage << "<XLedDev::GetLedParm> sValue(" << sParm << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// get Led parameter Value
//
int XLedDev::GetLedParm(unsigned char* sCmd, unsigned char* sResp, char* sParm)
{
    // check input paramter
    if (sCmd == NULL || sResp == NULL) return DEVICE_ERR;

    // call serial I/O to get message
    int ret = XLedSerialIO(sCmd, sResp);

    if (ret != DEVICE_OK) return ret;

    if (sParm !=NULL)
    {
        ret = GetLedParmVal(sResp, sParm);
        if (ret != DEVICE_OK) return ret;
    }

    return DEVICE_OK;
}

//
// Get Status Text Message
//
int XLedDev::GetStatusDescription(long lStatus, char* sStatus)
{
    const char* sStatusBitsOn[] =
    {
        "LED On",
        "Reserved",
        "Under Temperature",
        "Reserved",
        "Reserved",
        "Reserved",
        "LED is present",
        "Over Temperature"
    };

    const char* sStatusBitsOff[] =
    {
        "LED Off",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X",
        "X"
    };

    sprintf(sStatus, "%s", "[");
    long lValue = 1;
    for (int nBit = 0; nBit < 8; nBit++, lValue *= 2)
    {
        long lBit = lStatus & lValue;
        if (lBit == lValue)
        {
            sprintf(&sStatus[strlen(sStatus)], " %s,", sStatusBitsOn[nBit]);
        }
        else if (lBit == 0)
        {
            if (strlen(sStatusBitsOff[nBit]) > 1)
                sprintf(&sStatus[strlen(sStatus)], " %s,", sStatusBitsOff[nBit]);
        }
    }
    sStatus[strlen(sStatus) - 1] = ']';

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

//
// Update Led Status Byte
//
int XLedDev::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    // Get Led Status Byte
    unsigned char sCmd[8] = { 0x75, 0x73, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_UnitStatus);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Status
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnState> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedStatusLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Status
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnState> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedStatusLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

    }

    if (ret != DEVICE_OK) return ret;

    long lStatus = atol((const char*)sParm);
    pProp->Set(lStatus);

    char sStatus[400];
    memset(sStatus, 0, 400);

    GetStatusDescription(lStatus, sStatus);

    SetProperty(XLed::Instance()->GetXLedStr(XLed::XL_LedStatusDescLabel).c_str(), sStatus);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnState> (" << XLed::Instance()->GetXLedStr(XLed::XL_LedStatusDescLabel).c_str() << "=<"  << sParm << "," << lStatus << ">), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    return DEVICE_OK;
}

//
// Update Led Hours
//
int XLedDev::OnHours(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmd[8] = { 0x6C, 0x68, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LedHours);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        // Get Led Hours Ued
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnHours> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnHours> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }
    else if  (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Hours Ued
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnHours> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnHours> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedHoursLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }

    return DEVICE_OK;
}

//
// Update Led Temperature
//
int XLedDev::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmd[8] = { 0x67, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedTemperature);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        // Get Led Temperaturey
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnTemperaturee> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str() << "=" <<  sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTemperaturee> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }
    else if  (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Temperaturey
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnTemperaturee> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str() << "=" <<  sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTemperaturee> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempLabel).c_str() << "=<" <<  sParm << "," << lValue << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }

    return DEVICE_OK;
}

//
// Update Led Temperature Hysteresis
//
int XLedDev::OnTempHyst(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmd[8] = { 0x74, 0x68, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedTempHyst);;
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        // Get Led Temperature Hysteresis
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnTempHyst> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTempHyst> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }
    else if (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Temperature Hysteresis
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnTempHyst> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTempHyst> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }

    return DEVICE_OK;
}

//
// Turn On/Off Led
//
int XLedDev::OnLedOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8] = { 0x6F, 0x6E, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x6F, 0x6E, 0x3D, 0x31, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedOnStat);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedOnOff> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret !=DEVICE_OK) return ret;

        if (*sParm == '0') m_lOnOffState = 0;
        else m_lOnOffState = 1;
        pProp->Set(m_lOnOffState);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnLedOnOff> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=<" << sParm << "," << m_lOnOffState << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(m_lOnOffState);

        sCmdSet[1] = (m_lOnOffState > 0) ? 0x6E : 0x66;
        sCmdSet[3] += (unsigned char)m_nLedDevNumber;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedOnOff> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << m_lOnOffState << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedOnOff> AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_LedOnOffStateLabel).c_str() << "=" << sParm << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        m_lOnOffState = (sParm[0] == '0') ? 0 : 1;

        pProp->Set(m_lOnOffState);
    }

    return DEVICE_OK;
}

//
// Set Trigger Sequence
//
int XLedDev::OnLedTrigger(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8] = { 0x74, 0x73, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x74, 0x73, 0x3D, 0x31, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedTriggerSequence);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedTrigger> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret !=DEVICE_OK) return ret;

        m_lTriggerState = *sParm - '0';
        //else m_lOnOffState = 1;
        pProp->Set(m_lTriggerState);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnLedTrigger> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() << "=<" << sParm << "," << m_lOnOffState << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(m_lTriggerState);

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';

        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", m_lTriggerState);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;
        //sCmdSet[3] += (unsigned char)m_nLedDevNumber;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedTrigger> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() << "=" << m_lOnOffState << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedTrigger> AfterSet(2)::<" << XLed::Instance()->GetXLedStr(XLed::XL_LedTriggerSequenceLabel).c_str() << "=" << sParm << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        m_lTriggerState = sParm[0] - '0';

        pProp->Set(m_lTriggerState);
    }

    return DEVICE_OK;
}

//
// Get/Set Led Intensity
//
int XLedDev::OnLedIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x69, 0x70, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x69, 0x70, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_LedIntensity);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;
    double dIntensity = 0.0;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedIntensity> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        std::string sIntensity;
        pProp->Get(sIntensity);

        dIntensity = atof(sIntensity.c_str());

        long lIntensity = (long) (dIntensity * 10.0);

        if (lIntensity < 0) lIntensity = 0;
        if (lIntensity > 0 && lIntensity < 50) lIntensity = 50;
        if (lIntensity > 1000) lIntensity = 1000;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", lIntensity);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedIntensity> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() << "=[" << sIntensity.c_str() << "," << lIntensity << "]), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnLedIntensity> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() << "=<" << sParm << "," << m_lIntensity << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }


    }

    if (ret != DEVICE_OK) return ret;

    m_lIntensity = atol((const char*)sParm);
    dIntensity = (double) m_lIntensity / 10.0;
    char sIntensity[20];
    memset(sIntensity, 0, 20);
    sprintf(sIntensity, "%.1f", dIntensity);

    pProp->Set(sIntensity);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnLedIntensity> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedIntensityLabel).c_str() << "=<" << sParm << "," << sIntensity << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Update Led Temperature Hysteresis
//
int XLedDev::OnMinimumIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmd[8] = { 0x6E, 0x69, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp =  XLed::Instance()->GetParameter(XLed::XL_LedMinIntensity);;
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        // Get Led Temperature Hysteresis
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnMinimumIntensity> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnMinimumIntensity> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }
    else if (eAct == MM::AfterSet)
    {
        memset(sResp, 0, XLed::XL_MaxPropSize);

        // Get Led Temperature Hysteresis
        ret = GetLedParm(sCmd, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnMinimumIntensity> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=" << sResp << "), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        long lValue = atol((const char*)sParm);
        pProp->Set(lValue);
    
        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnMinimumIntensity> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_LedTempHystLabel).c_str() << "=<" << sParm << "," << lValue << ">), ReturnCode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        // if (ret != DEVICE_OK) return ret;
    }

    return DEVICE_OK;
}

//
// check for the pulse mode
//
/*int XLedDev::OnPulseMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8] = { 0x70, 0x6D, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x70, 0x6D, 0x3D, 0x30, XLed::XL_TxTerm, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SigPulseMode);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnPulseMode> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        long lPulseMode;
        pProp->Get(lPulseMode);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnPulseMode> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=" << lPulseMode << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (lPulseMode < 0) lPulseMode = 0;
        if (lPulseMode > 3) lPulseMode = 3;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", lPulseMode);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPulseMode> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=<" << lPulseMode << "," <<  m_lPulseMode << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPulseMode> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

    }

    if (ret != DEVICE_OK) return ret;

    m_lPulseMode = atol(sParm);
    pProp->Set(m_lPulseMode);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnPulseMode> (" << XLed::Instance()->GetXLedStr(XLed::XL_PulseModeLabel).c_str() << "=<" << sParm << "," << m_lPulseMode << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// Get/Set SIgnal Delay Time
//
int XLedDev::OnSignalDelayTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x64, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x64, 0x74, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SigDelayTime);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalDelayTime> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        std::string sSignalDelayTime;
        pProp->Get(sSignalDelayTime);
        long  lSignalDelayTime;
        sscanf(sSignalDelayTime.c_str(), "%ld", &lSignalDelayTime);
        if (m_lPWMUnit == 0) lSignalDelayTime /= 10;

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalDelayTime> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=" << lSignalDelayTime << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (lSignalDelayTime < 0) lSignalDelayTime = 0;
        if (lSignalDelayTime > 65535) lSignalDelayTime = 65535;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", lSignalDelayTime);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalDelayTime> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=<" << lSignalDelayTime << "," <<  m_lSignalDelayTime << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalDelayTime> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

    }

    if (ret != DEVICE_OK) return ret;

    const char* sPWMUnit[] = { "0uS", "mS", "S" };
    m_lSignalDelayTime= atol(sParm);
    char sSignalDelayTime[20];
    memset(sSignalDelayTime, 0, 20);
    sprintf(sSignalDelayTime, "%ld%s", m_lSignalDelayTime, sPWMUnit[m_lPWMUnit]);
    pProp->Set(sSignalDelayTime);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnSignalDelayTime> (" << XLed::Instance()->GetXLedStr(XLed::XL_SignalDelayTimeLabel).c_str() << "=<" << sParm << "," << sSignalDelayTime << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// Ge/Set Signal On Time
//
int XLedDev::OnSignalOnTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x6F, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x6F, 0x74, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SigOnTime);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalOnTime> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        std::string sSignalOnTime;
        pProp->Get(sSignalOnTime);
        long lSignalOnTime;
        sscanf(sSignalOnTime.c_str(), "%ld", &lSignalOnTime);
        if (m_lPWMUnit == 0) lSignalOnTime /= 10;

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalOnTime> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << "=[" << sSignalOnTime << "," << lSignalOnTime << "]), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
        
        if (lSignalOnTime < 0) lSignalOnTime = 0;
        if (lSignalOnTime > 65535) lSignalOnTime = 65535;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%0ld", lSignalOnTime);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalOnTime> AfterSet(2)(" << 
               XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << 
               "=<" << lSignalOnTime << "," <<  m_lSignalOnTime << 
               ">), Returncode = " << ret << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalOnTime> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }

    if (ret != DEVICE_OK) return ret;

    const char* sPWMUnit[] = { "0uS", "mS", "S" };
    m_lSignalOnTime = atol(sParm);
    char sSignalOnTime[20];
    memset(sSignalOnTime, 0, 20);
    sprintf(sSignalOnTime, "%ld%s", m_lSignalOnTime, sPWMUnit[m_lPWMUnit]);
    pProp->Set(sSignalOnTime);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnSignalOnTime> (" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOnTimeLabel).c_str() << "=<" << sParm << "," << sSignalOnTime << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// Update Led Status including temperature
//
int XLedDev::OnSignalOffTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x66, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x66, 0x74, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SigOffTime);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalOffTime> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        std::string sSignalOffTime;
        pProp->Get(sSignalOffTime);
        long lSignalOffTime;
        sscanf(sSignalOffTime.c_str(), "%ld", &lSignalOffTime);
        if (m_lPWMUnit == 0) lSignalOffTime /= 10;

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalOffTime> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=[" << sSignalOffTime << "," << lSignalOffTime << "]), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (lSignalOffTime < 0) lSignalOffTime = 0;
        if (lSignalOffTime > 65535) lSignalOffTime = 65535;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", lSignalOffTime);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalOffTime> AfterSet(2)(" << 
               XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=<" << 
               lSignalOffTime << "," <<  m_lSignalOffTime << ">), Returncode = " << 
               ret << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnSignalOffTime> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }

    if (ret != DEVICE_OK) return ret;

    const char* sPWMUnit[] = { "0uS", "mS", "S" };
    m_lSignalOffTime = atol(sParm);
    char sSignalOffTime[20];
    memset(sSignalOffTime, 0, 20);
    sprintf(sSignalOffTime, "%ld%s", m_lSignalOffTime, sPWMUnit[m_lPWMUnit]);
    pProp->Set(sSignalOffTime);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnSignalOffTime> (" << XLed::Instance()->GetXLedStr(XLed::XL_SignalOffTimeLabel).c_str() << "=<" << sParm << "," << sSignalOffTime << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
}

//
// Update Led Status including temperature
//
int XLedDev::OnTriggerDelayTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x74, 0x74, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x74, 0x74, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_SigAdvTime);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnSignalOnTime> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        std::string sTriggerDelay;
        pProp->Get(sTriggerDelay);
        long lTriggerDelay;
        sscanf(sTriggerDelay.c_str(), "%ld", & lTriggerDelay);
        if (m_lPWMUnit == 0) lTriggerDelay /= 10;

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnTriggerDelayTime> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=" << lTriggerDelay << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (lTriggerDelay < 0) lTriggerDelay = 0;
        if (lTriggerDelay > 65535) lTriggerDelay = 65535;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%0ld", lTriggerDelay);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTriggerDelayTime> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=<" << lTriggerDelay << "," <<  m_lTriggerDelay << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnTriggerDelayTime> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }

    if (ret != DEVICE_OK) return ret;

    const char* sPWMUnit[3] = { "0uS", "mS", "S" };
    m_lTriggerDelay = atol(sParm);
    char sTriggerDelay[20];
    sprintf(sTriggerDelay, "%ld%s", m_lTriggerDelay, sPWMUnit[m_lPWMUnit]);
    pProp->Set(sTriggerDelay);

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<XLedDev::OnSignalOnTime> (" << XLed::Instance()->GetXLedStr(XLed::XL_TriggerDelayTimeLabel).c_str() << "=<" << sParm << "," << sTriggerDelay << ">), Returncode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    return DEVICE_OK;
} */

//
// Update Led Status including temperature
//
int XLedDev::OnPWMUnit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    unsigned char sCmdGet[8]  = { 0x73, 0x75, 0x3F, XLed::XL_TxTerm, 0x00, 0x00, 0x00, 0x00 };
    unsigned char sCmdSet[16] = { 0x73, 0x75, 0x3D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    unsigned char* sResp = XLed::Instance()->GetParameter(XLed::XL_PWMUnit);
    char sParm[XLed::XL_MaxPropSize];
    int ret = DEVICE_OK;

    memset(sParm, 0, XLed::XL_MaxPropSize);

    if (eAct == MM::BeforeGet)
    {
        ret = GetLedParmVal(sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnPWMUnit> BeforeGet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        m_lPWMUnit = atol((const char*)sParm);
        pProp->Set(m_lPWMUnit);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPWMUnit> BeforeGet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=<" << sParm << "," << m_lPWMUnit << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(m_lPWMUnit);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<XLedDev::OnPWMUnit> AfterSet(1)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=" << m_lPWMUnit << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (m_lPWMUnit < 0) m_lPWMUnit = 0;
        if (m_lPWMUnit > 2) m_lPWMUnit = 2;

        for (int nDev = 0; nDev < m_nLedDevNumber; nDev++) sCmdSet[3+nDev] = ',';
        sprintf((char*)&sCmdSet[3+m_nLedDevNumber], "%ld", m_lPWMUnit);
        sCmdSet[strlen((const char*)sCmdSet)] = XLed::XL_TxTerm;

        ret = XLedSerialIO(sCmdSet, sResp);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPWMUnit> AfterSet(2)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=<" << sResp << "," <<  m_lPWMUnit << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        memset(sResp, 0, XLed::XL_MaxPropSize);

        ret = GetLedParm(sCmdGet, sResp, sParm);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPWMUnit> AfterSet(3)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=" << sResp << "), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        m_lPWMUnit = atol((const char*)sParm);
        pProp->Set(m_lPWMUnit);

        if (XLed::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<XLedDev::OnPWMUnit> AfterSet(4)(" << XLed::Instance()->GetXLedStr(XLed::XL_PWMUnitsLabel).c_str() << "=<" << sParm << "," << m_lPWMUnit << ">), Returncode = " << ret;
            this->LogMessage(osMessage.str().c_str());
        }
    }

    return DEVICE_OK;
}

//
// send a command and read responded message
//
int XLedDev::XLedSerialIO(unsigned char* sCmd, unsigned char* sResp)
{ 
    int nTrys = 0;
    bool yDevErr = false;

    do {
        // send command to disconnect XLed
        int ret = WriteCommand(sCmd);
        if (ret != DEVICE_OK) return ret;

        // Sleep(5);

        // read returned Message
        ret = ReadMessage(sResp);
        if (ret != DEVICE_OK) return ret;

        yDevErr = sResp[0] == 0x65 && sResp[1] == 0x0D;

    } while (yDevErr && nTrys++ <= 3);

    // if (yDevErr) return DEVICE_ERR; 

    return DEVICE_OK;
}


//
// write a command string to serial port
//
int XLedDev::WriteCommand(const unsigned char* sCommand)
{
    int ret = DEVICE_OK;
    std::size_t nCmdLength = strlen((const char*)sCommand);
    ostringstream osMessage;

    if (XLed::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        char sHex[3];
        osMessage << "<XLedDev::WriteCommand> (cmd ";
        for (unsigned n=0; n < nCmdLength; n++)
        {
            XLed::Instance()->Byte2Hex(sCommand[n], sHex);
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
int XLedDev::ReadMessage(unsigned char* sMessage)
{
    // block/wait for acknowledge, or until we time out;
    unsigned int nLength = 256;
    unsigned char sAnswer[256];
    memset(sAnswer, 0, 256);
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
        //    osMessage << "<XLedDev::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
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
        osMessage << "<XLedDev::ReadMessage> (ReadFromSerial = (" << lRead << "," << yTimeout << ") <";
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
