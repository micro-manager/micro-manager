//////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400Channel.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   C400 Controller Driver
//
// COPYRIGHT:     NPoint,
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
#include "../../MMCore/MMCore.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "nPC400.h"
#include "nPC400Ctrl.h"
#include "nPC400Channel.h"

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
nPC400CH::nPC400CH(int nChannel) :
    m_yInitialized(false),
    m_yChannelAvailable(false),
    m_dAnswerTimeoutMs(5000.),
    m_nChannel(nChannel),
    m_dRadius(1.0),
    m_nRangeUnit(0),
    m_nRange(1048575),
    m_nMinPosSteps(-524287),
    m_nMaxPosSteps(524287),
    m_dStepSizeUm(1.0)
{
    InitializeDefaultErrorMessages();
}


//
// channel destructor
//
nPC400CH::~nPC400CH()
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
int nPC400CH::Initialize()
{
    // Channel Availability?
    int nChannelBit[6] = { 0x01, 0x02, 0x04, 0x08, 0x10, 0x20 };
    m_yChannelAvailable = (nPC400::Instance()->GetChannelsAvailability() & nChannelBit[m_nChannel]) ? true : false;
    
    int ret = DEVICE_OK;
    std::ostringstream osMessage;
    // Channel Available
    if (m_yChannelAvailable)
    {
        // Channel Device Name
        char sChDevName[120];
        sprintf(sChDevName, "%s%s", nPC400::Instance()->GetC400Str(nPC400::C400_ChannelDeviceNameLabel).c_str(), MM::g_Keyword_Name);
        int ret = CreateProperty(sChDevName, nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName + m_nChannel).c_str(), MM::String, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << sChDevName << "=" << nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName + m_nChannel).c_str() << "), ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        // Description
        char sChDevDesc[120];
        sprintf(sChDevDesc, "%s%s", nPC400::Instance()->GetC400Str(nPC400::C400_ChannelDeviceDescLabel).c_str(), MM::g_Keyword_Description);
        ret = CreateProperty(sChDevDesc, nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName + m_nChannel).c_str(), MM::String, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << sChDevDesc << nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName + m_nChannel).c_str() << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        // channel connected property
        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelDeviceConnLabel).c_str(), "YES", MM::String, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelDeviceConnLabel).c_str() << " = [" << "YES" << "] ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        // Chaneel Radius
        CPropertyAction* pActRadius = new CPropertyAction (this, &nPC400CH::OnRadius);

        char sRadius[20];
        sprintf(sRadius, "%.2f", m_dRadius);
        
        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeRadLabel).c_str() , sRadius, MM::Float, false, pActRadius);
        //ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeUnitLabel).c_str() , m_nRangeUnit, MM::String, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeRadLabel).c_str() << " = " << m_dRadius << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        // Chaneel Range Unit
        CPropertyAction* pActRangeUnit = new CPropertyAction (this, &nPC400CH::OnRangeUnit);
        ret = GetRangeUnit_(m_nRangeUnit);

        if (ret != DEVICE_OK) return ret;

        char sRangeUnit[20];
        sprintf(sRangeUnit, "%d", m_nRangeUnit);
        
        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeUnitLabel).c_str() , sRangeUnit, MM::Integer, false, pActRangeUnit);
        //ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeUnitLabel).c_str() , m_nRangeUnit, MM::String, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeUnitLabel).c_str() << " = " << m_nRangeUnit << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;


        // Channel Range
        CPropertyAction* pActRange = new CPropertyAction (this, &nPC400CH::OnRange);
        ret = GetRange_(m_nRange);
        if (ret != DEVICE_OK) m_nRange = 1;
        char sRange[20];
        sprintf(sRange, "%d", m_nRange);

        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeLabel).c_str() , sRange, MM::Integer, false, pActRange);
        //ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeLabel).c_str() , sRange, MM::Integer, true);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelRangeLabel).c_str() << " = " << m_nRange << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        m_dStepSizeUm = SetStepSize_(m_nRange, m_nRangeUnit);

        // Channel Position Set Property
        CPropertyAction* pActPositionSet = new CPropertyAction (this, &nPC400CH::OnPositionSet);
        long lPos = 0;
        ret = GetSetPos_(lPos);
        if (ret != DEVICE_OK) lPos = 0;
        double dPos = (double)lPos / m_dStepSizeUm;
        char sPosSet[20];
        sprintf(sPosSet, "%.2f", dPos);

        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionSetLabel).c_str(), sPosSet, MM::Float, false, pActPositionSet);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionSetLabel).c_str() << " = " << dPos << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        // Channel Position Property
        CPropertyAction* pActPositionGet = new CPropertyAction (this, &nPC400CH::OnPositionGet);
        lPos = 0;
        ret = GetPos_(lPos);
        if (ret != DEVICE_OK) lPos = 0;
        dPos = (double)lPos / m_dStepSizeUm;
        char sPosGet[20];
        sprintf(sPosGet, "%.2f", dPos);

        ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str(), sPosGet, MM::Float, false, pActPositionGet);

        if (nPC400::Instance()->GetDebugLogFlag() > 0)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str() << " = " << dPos << ") ReturnCode=" << ret;
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }

    ret = UpdateStatus();
    if (ret != DEVICE_OK) return ret;

    m_yInitialized = true;
    return DEVICE_OK;
}

//
// shutdown the controller
//
int nPC400CH::Shutdown()
{ 
    if (m_yChannelAvailable)
    {
        m_yChannelAvailable = false;
        int nChannelBit[6] = { 0x01, 0x02, 0x04, 0x08, 0x10, 0x20 };
        int nChannelAvailable = nPC400::Instance()->GetChannelsAvailability();
        nChannelAvailable &= ~nChannelBit[m_nChannel];
        nPC400::Instance()->SetChannelsAvailable(nChannelAvailable);
        int nNumberOfAxes = nPC400::Instance()->GetNumberOfAxes() - 1;
        if (nNumberOfAxes < 0) nNumberOfAxes = 0;
        nPC400::Instance()->SetNumberOfAxes(nNumberOfAxes);
    }

    m_yInitialized = false;

    return DEVICE_OK;
}

//
// Get the device name of the channel
//
void nPC400CH::GetName(char* Name) const
{
    CDeviceUtils::CopyLimitedString(Name, nPC400::Instance()->GetC400Str(nPC400::C400_CH1DeviceName + m_nChannel).c_str());
}

//
// Get channel position in um
//
int nPC400CH::GetPositionUm(double& dPosUm)
{
    long lPosSteps = 0;

    int ret = GetPositionSteps(lPosSteps);

    if (ret != DEVICE_OK) return ret;

    dPosUm = (double)lPosSteps / m_dStepSizeUm;

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH:GetPositionUm> (pos = " << dPosUm << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Move to channel position in um
//
int nPC400CH::SetPositionUm(double dPosUm)
{
    long lPosSteps = (long)(dPosUm * m_dStepSizeUm);

    int ret = SetPositionSteps(lPosSteps);

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH:SetPositionUm> (pos = " << dPosUm << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// send command to read current position
//
int nPC400CH::GetPos_(long& lPos)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[6] = { 0xA0, 0x34, 0x03, 0x83, 0x11, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (char)((m_nChannel + 1) * 0x10);

    int ret = WriteCommand(sCmd, 6);

    if (ret != DEVICE_OK) return ret;

    unsigned  char sResp[20];
    memset(sResp, 0, 20);
    ret = ReadMessage(sResp);

    if (ret != DEVICE_OK) return ret;

    lPos = (long)sResp[5] + (long)sResp[6] * 0x100L + (long)sResp[7] * 0x10000L;
    if (sResp[8] == 0xFF)  lPos -= 0x1000000L;

    return DEVICE_OK;
}

//
// Get channel position in steps
//
int nPC400CH::GetPositionSteps(long& lPosSteps)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    int ret = GetPos_(lPosSteps);
    
    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH:GetPositionSteps> (pos = " << lPosSteps << ") ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    double dPos = (double) lPosSteps / m_dStepSizeUm;
    char sPos[20];
    sprintf(sPos, "%.2f", dPos);

    // Channel Position Get Property
    ret = SetProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str(), (const char*)sPos);

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH::GetPositionSteps> SetProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str() << " = <" << lPosSteps << "," << sPos << ">) ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// send coomand to set positions
//
int nPC400CH::SetPos_(long lPos)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[10] = { 0xA2, 0x18, 0x02, 0x83, 0x11, 0x00, 0x00, 0x00, 0x00, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char)((m_nChannel + 1) * 0x10);
  
    long lSteps = (lPos > m_nMaxPosSteps) ? m_nMaxPosSteps : lPos;
    lSteps = (lPos < m_nMinPosSteps) ? m_nMinPosSteps : lPos;

    sCmd[5] = (unsigned char)(lSteps & 0xFF);
    sCmd[6] = (unsigned char)((lSteps & 0xFF00)/0x100);
    sCmd[7] = (unsigned char)((lSteps & 0xFF0000)/0x10000);
    sCmd[8] = (unsigned char)((lSteps & 0xFF000000)/0x1000000);

    int ret = WriteCommand(sCmd, 10);
    
    if (ret != DEVICE_OK) return ret;


    //ret = GetSetPos_(lSteps);

    //std::ostringstream osMessage;
    //osMessage << "<nPC400CH::SetPos_> (lPos  = " << lPos << ", lSteps = " << lSteps << ") ReturnCode=" << ret;
    //this->LogMessage(osMessage.str().c_str());

    //if (ret != DEVICE_OK) return ret;

    //ret = SetProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str(), CDeviceUtils::ConvertToString(lSteps));

    //osMessage.str("");
    //osMessage << "<nPC400CH::SetPos_> (" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionGetLabel).c_str() << " = " << lSteps << ") ReturnCode=" << ret;
    //this->LogMessage(osMessage.str().c_str());

    return DEVICE_OK;
}
  
//
// send coomand to read set positions
//
int nPC400CH::GetSetPos_(long& lPos)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[6] = { 0xA0, 0x18, 0x02, 0x83, 0x11, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char)((m_nChannel + 1) * 0x10);
  
    int ret = WriteCommand(sCmd, 6);
    
    if (ret != DEVICE_OK) return ret;

    unsigned  char sResp[20];
    memset(sResp, 0, 20);
    ret = ReadMessage(sResp);

    if (ret != DEVICE_OK) return ret;

    lPos = (long)sResp[5] + (long)sResp[6] * 0x100L + (long)sResp[7] * 0x10000L;
    if (sResp[8] == 0xFF)  lPos -= 0x1000000L;

    return DEVICE_OK;
}
  
//
// move channel in steps
//
int nPC400CH::SetPositionSteps(long lPosSteps)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    long lSteps = (lPosSteps > m_nMaxPosSteps) ? m_nMaxPosSteps : lPosSteps;
    lSteps = (lSteps < m_nMinPosSteps) ? m_nMinPosSteps : lSteps;
    
    int ret = SetPos_(lSteps);

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH:SetPositionSteps> (pos = " << lPosSteps << ", steps = " << lSteps << ") ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    double dPos = (double) lPosSteps / m_dStepSizeUm;
    char sPos[20];
    sprintf(sPos, "%.2f", dPos);

    // Channel Position Get Property
    ret = SetProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionSetLabel).c_str(), sPos);

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        ostringstream osMessage;
        osMessage << "<nPC400CH::SetPositionSteps> SetProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelPositionSetLabel).c_str() << " = <" << lPosSteps << "," << sPos << ">) ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    return (DEVICE_OK);
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int nPC400CH::OnPositionSet(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    int ret = DEVICE_OK;

    long lPos = 0;
    double dPos = 0.;

    if (eAct == MM::BeforeGet)
    {
        ret = GetSetPos_(lPos);

        if (ret != DEVICE_OK) return ret;

        dPos = (double) lPos / m_dStepSizeUm;

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnPositionSet> (Position = <" << lPos << "," << dPos << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        pProp->Set(dPos);

    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(dPos);

        lPos = (long)(dPos  * m_dStepSizeUm);

        if (lPos < (long)m_nMinPosSteps) lPos = (long)m_nMinPosSteps;
        if (lPos > (long)m_nMaxPosSteps) lPos = (long)m_nMaxPosSteps;

        ret = SetPos_(lPos);

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnPositionSet(AfterSet)> (Position = <" << lPos << "," << dPos << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;

        dPos = (double)lPos / m_dStepSizeUm;

        pProp->Set(dPos);
    }

    return DEVICE_OK;
}

int nPC400CH::OnPositionGet(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    int ret = DEVICE_OK;
    long lPos = 0;
    double dPos = 0.;

    if (eAct == MM::BeforeGet)
    {
        ret = GetPos_(lPos);

        if (ret != DEVICE_OK) return ret;

        //lPos &= 0xFFFFF;

        dPos = (double) lPos / m_dStepSizeUm;

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnPositionGet(BeforeGet)> (Position = <" << lPos << "," << dPos << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        pProp->Set(dPos);
    }
    else if (eAct == MM::AfterSet)
    {
        ret = GetPos_(lPos);

        if (ret != DEVICE_OK) return ret;

        //lPos &= 0xFFFFF;

        dPos = (double) lPos / m_dStepSizeUm;

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnPositionGet(AfterSet)> (Position = <" << lPos << "," << dPos << ">)";
            this->LogMessage(osMessage.str().c_str());
        }

        pProp->Set(dPos);
    }

    return DEVICE_OK;
}

int nPC400CH::OnRange(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    int ret = DEVICE_OK;

    if (eAct == MM::BeforeGet)
    {
        int nRange = 0;

        ret = GetRange_(nRange);

        if (ret != DEVICE_OK) return ret;

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnRange(BeforeGet)> (range = " << nRange << ")";
            this->LogMessage(osMessage.str().c_str());
        }

        pProp->Set((long)nRange);
    }
    else if (eAct == MM::AfterSet)
    {
        long lRange;
        pProp->Get(lRange);
        if (lRange <= 0)
        {
            pProp->Set((long)m_nRange);
            return DEVICE_INVALID_INPUT_PARAM;
        }

        ret = SetRange_((int)lRange);

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            ostringstream osMessage;
            osMessage << "<nPC400CH:OnRange(AfterSet)> (range = " << lRange << ")";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret != DEVICE_OK) return ret;
    }
    
    //m_dStepSizeUm = (double)(m_nMaxPosSteps-m_nMinPosSteps)/(double)m_nRange;

    return DEVICE_OK;
}

//
// setup step size
//
double nPC400CH::SetStepSize_(int nRange, int nRangeUnit)
{
    double dStepSize = 0.0;

    dStepSize = (double)(m_nMaxPosSteps - m_nMinPosSteps) / (double) nRange;
    if (nRangeUnit == 1) dStepSize /= 1000.0;
    if (nRangeUnit == 2) dStepSize /= m_dRadius;

    return dStepSize;
}

//
// send coand to read range value
//
int nPC400CH::GetRange_(int& nRange)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[6] = { 0xA0, 0x78, 0x00, 0x83, 0x11, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char) ((m_nChannel + 1) * 0x10);

    int ret = WriteCommand(sCmd, 6);

    if (ret != DEVICE_OK) return ret;

    unsigned char sResp[20];
    memset(sResp, 0, 20);

    ret = ReadMessage(sResp);

    if (ret !=DEVICE_OK) return ret;

    nRange = sResp[5] + sResp[6] * 0x100 + sResp[7] * 0x10000 + sResp[8] * 0x1000000;

    if (nRange != m_nRange)
    {
        m_dStepSizeUm = SetStepSize_(nRange, m_nRangeUnit);
        m_nRange = nRange;
    }

    return DEVICE_OK;
}

//
// send command to set range value
//
int nPC400CH::SetRange_(int nRange)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[10] = { 0xA2, 0x78, 0x00, 0x83, 0x11, 0x00, 0x00, 0x00, 0x00, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char)((m_nChannel + 1) * 0x10);
    sCmd[5] = (unsigned char)(nRange & 0xFF);
    sCmd[6] = (unsigned char)((nRange & 0xFF00) / 0x100);
    sCmd[7] = (unsigned char)((nRange & 0xFF0000) / 0x10000);
    sCmd[8] = (unsigned char)((nRange & 0xFF000000) / 0x1000000);

    int ret = WriteCommand(sCmd, 10);

    if (ret != DEVICE_OK) return ret;

    if (nRange != m_nRange)
    {
        m_dStepSizeUm = SetStepSize_(nRange, m_nRangeUnit);
        m_nRange = nRange;
    }

    return DEVICE_OK;
}


//
// send command to read range unit
//
int nPC400CH::GetRangeUnit_(int& nRangeUnit)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[6] = { 0xA0, 0x44, 0x00, 0x83, 0x11, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char) ((m_nChannel + 1) * 0x10);

    int ret = WriteCommand(sCmd, 6);

    if (ret != DEVICE_OK) return ret;

    unsigned char sResp[20];
    memset(sResp, 0, 20);

    ret = ReadMessage(sResp);

    if (ret !=DEVICE_OK) return ret;

    nRangeUnit = sResp[5] - 0x30;

    if ((nRangeUnit >= 0) && (nRangeUnit <= 2))
    {
        m_dStepSizeUm = SetStepSize_(m_nRange, nRangeUnit);
        m_nRangeUnit = nRangeUnit;
    }

    return DEVICE_OK;
}

//
//  send command to set range unit
//
int nPC400CH::SetRangeUnit_(int nRangeUnit)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    unsigned char sCmd[10] = { 0xA0, 0x44, 0x00, 0x83, 0x11, 0x00, 0x00, 0x00, 0x00, (unsigned char)nPC400::C400_TxTerm };
    sCmd[2] += (unsigned char) ((m_nChannel + 1) * 0x10);

    sCmd[5] = (unsigned char)(nRangeUnit & 0xFF);

    int ret = WriteCommand(sCmd, 10);

    if (ret != DEVICE_OK) return ret;

    if ((nRangeUnit >= 0) && (nRangeUnit <= 2))
    {
        m_dStepSizeUm = SetStepSize_(m_nRange, nRangeUnit);
        m_nRangeUnit = nRangeUnit;
    }

    return DEVICE_OK;
}

int nPC400CH::OnRadius (MM::PropertyBase* pProp, MM::ActionType eAct) 
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    //int ret = DEVICE_OK;

    double dRadius = m_dRadius;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dRadius);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(dRadius);
        if (dRadius <= 0.0) dRadius = 1.0;
        pProp->Set(dRadius);
        m_dRadius = dRadius;
    }

    return DEVICE_OK;
}

int nPC400CH::OnRangeUnit (MM::PropertyBase* pProp, MM::ActionType eAct) 
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    int ret = DEVICE_OK;

    string sUnit;
    int nRangeUnit = m_nRangeUnit;

    if (eAct == MM::BeforeGet)
    {
        ret = GetRangeUnit_(nRangeUnit);

        if (ret != DEVICE_OK) return ret;

        if (nRangeUnit < 0) nRangeUnit = 0;
        if (nRangeUnit > 2) nRangeUnit = 2;

        long lRangeUnit = (long) nRangeUnit;
        pProp->Set(lRangeUnit);
        m_nRangeUnit = nRangeUnit;
    }
    else if (eAct == MM::AfterSet)
    {
        long lRangeUnit;
        pProp->Get(lRangeUnit);

        if (lRangeUnit < 0) lRangeUnit = 0;
        if (lRangeUnit > 2) lRangeUnit = 2;

        ret = SetRangeUnit_((int)lRangeUnit);

        if (ret != DEVICE_OK) return ret;

        m_nRangeUnit = (int)lRangeUnit;
    }

    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

//
// Sends a specified command to the controller
//
int nPC400CH::WriteCommand(const unsigned char* sCommand, int nLength)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    // reset serial buffer
    // int ret = ClearPort(*this, *GetCoreCallback(), nPC400::Instance()->GetSerialPort().c_str());

    int ret = DEVICE_OK;
    ostringstream osMessage;
    //osMessage << "<nPC400CH::Initialize> ClearPort(Port = " << nPC400::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret;
    //this->LogMessage(osMessage.str().c_str());

    // write command out
    ret = DEVICE_OK;
    char sHex[3];
    memset(sHex, 0, 3);

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage.str("");
        osMessage << "<nPC400CH::WriteCommand> (port=<" << nPC400::Instance()->GetSerialPort().c_str() << ">) (command";
    }

    int nBytes = 0;
    for (nBytes = 0; nBytes < nLength && ret == DEVICE_OK; nBytes++)
    {
        ret = WriteToComPort(nPC400::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCommand[nBytes], 1);
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            nPC400::Instance()->Byte2Hex(sCommand[nBytes],sHex);
            osMessage << "[" << nBytes << "]=<" << sHex << ">";
        }
        CDeviceUtils::SleepMs(1);
    }

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        osMessage << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    return ret;
    //return DEVICE_OK;
}

//
// Read responded message
//
int nPC400CH::ReadMessage(unsigned char* sMessage)
{
    if (!m_yChannelAvailable) return DEVICE_UNSUPPORTED_COMMAND;

    // clean up COM port
    //PurgeComPort(nPC400::Instance()->GetSerialPort().c_str());

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
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, nPC400::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)nLength, lByteRead);
 
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<nPC400CH::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
        }

        for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)
        {
            yRead = yRead || sAnswer[lRead+lIndx] == nPC400::C400_RxTerm;

            if (nPC400::Instance()->GetDebugLogFlag() > 1)
            {
                nPC400::Instance()->Byte2Hex(sAnswer[lRead+lIndx], sHex);
                osMessage << "[" << sHex << "]";
            }
        }

        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "> (" << yRead << ")";
            this->LogMessage(osMessage.str().c_str());
        }

        if (ret == DEVICE_OK && lByteRead > 0)
        {
            // yRead = strchr((char*)&sAnswer[lRead], nPC400::C400_RxTerm) != NULL; // don't change the following order
            lRead += lByteRead;                                                  // otherwise message will not resturned
            if (yRead) break;
        }

        yTimeout = ((GetClockTicksUs() - lStartTime) / 1000) > m_dAnswerTimeoutMs;

        // delay 1ms
        if (!yTimeout) CDeviceUtils::SleepMs(1);
    }

    //if (!yRead || yTimeout) return DEVICE_SERIAL_TIMEOUT;

    nPC400::Instance()->ByteCopy(sMessage, sAnswer, 10);

    //if (nPC400::Instance()->GetDebugLogFlag() > 1)
    //{
        //osMessage.str("");
        //osMessage << "<nPC400Ctrl::ReadMessage> (ReadFromSerial = <";
        //for (unsigned long lIndx=0; lIndx < 10; lIndx++)
        //{
        //    nPC400::Instance()->Byte2Hex(sMessage[lIndx], sHex);
        //    osMessage << "[" << sHex  << ",";
        //    nPC400::Instance()->Byte2Hex(sAnswer[lIndx], sHex);
        //    osMessage << sHex  << "]";
        //}
        //osMessage << ">";
        //this->LogMessage(osMessage.str().c_str());
    //}

    return DEVICE_OK;
}
