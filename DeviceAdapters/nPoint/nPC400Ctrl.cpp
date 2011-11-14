/////////////////////////////////////////////////////////////////////////////
// FILE:          nPC400Ctrl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   nPoint C400 Controller Driver
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
//#include <strsafe.h>
#include "../../MMCore/MMCore.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "nPC400.h"
#include "nPC400Ctrl.h"
#include "nPC400Channel.h"

using namespace std;



//////////////////////////////////////////////////////
// C400 Controller
//////////////////////////////////////////////////////
//
// Controller - Controller for nPoint PZT channels.
// Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
// system with the axis going the 'Micro-Manager standard' direction
// Then, there is the C400s native system.  All functions using 'steps' use the C400 system
// All functions using Um use the Micro-Manager coordinate system
//


//
// nPC400 Controller Constructor
//
nPC400Ctrl::nPC400Ctrl() :
    m_dAnswerTimeoutMs(1000),   // wait time out set 1000 ms
    m_yInitialized(false)       // initialized flag set to false
{
    // call initialization of error messages
    InitializeDefaultErrorMessages();

    // Port:
    CPropertyAction* pAct = new CPropertyAction(this, &nPC400Ctrl::OnPort);
    int ret = CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        std::ostringstream osMessage;
        osMessage << "<nPC400Ctrl::class-constructor> CreateProperty(" << MM::g_Keyword_Name << "=Undfined), ReturnCode=" << ret;
        this->LogMessage(osMessage.str().c_str());
    }
}

//
// nPC400 Controller Destructor
//
nPC400Ctrl::~nPC400Ctrl()
{
    Shutdown();
}

//
// return device name of the nPC400 controller
//
void nPC400Ctrl::GetName(char* sName) const
{
    CDeviceUtils::CopyLimitedString(sName, nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str());
}

//
// Initialize the nPC400 controller
//
int nPC400Ctrl::Initialize()
{
    std::ostringstream osMessage;

    // empty the Rx serial buffer before sending command
    int ret = ClearPort(*this, *GetCoreCallback(), nPC400::Instance()->GetSerialPort().c_str());

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> ClearPort(Port = " << nPC400::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Name
    char sBoardName[120];
    sprintf(sBoardName, "%s%s", nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardNameLabel).c_str(), MM::g_Keyword_Name);
    ret = CreateProperty(sBoardName, nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str(), MM::String, true);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << sBoardName << " = " << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Description
    char sBoardDesc[120];
    sprintf(sBoardDesc, "%s%s", nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardDescLabel).c_str(), MM::g_Keyword_Description);
    ret = CreateProperty(sBoardDesc, nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str(), MM::String, true);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << sBoardDesc << " = " << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardName).c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK)  return ret;

    // Create read-only property for version info
    ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_SoftwareVersionLabel).c_str(), nPC400::Instance()->GetC400Str(nPC400::C400_SoftwareVersion).c_str(), MM::String, true);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_SoftwareVersionLabel).c_str() << " = " << nPC400::Instance()->GetC400Str(nPC400::C400_SoftwareVersion).c_str() << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Read available channels
    unsigned char sMessage[20];
    memset(sMessage, 0, 20);
    ret = GetChannelsConnected(sMessage);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> (sMessage = <";
        for (unsigned long lIndx=0; lIndx < 10; lIndx++)
        {
            char sHex[3];
            nPC400::Instance()->Byte2Hex(sMessage[lIndx], sHex);
            osMessage << "[" << sHex  << "]";
        }
        osMessage << ">";
        this->LogMessage(osMessage.str().c_str());
    }

    int nChannelsAvailability = (int)(~sMessage[5] & 0x3F);


    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardConnLabel).c_str() << " = [" << nChannelsAvailability << "]), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    if (ret != DEVICE_OK) return ret;

    // Create read-only property for version info
    nPC400::Instance()->SetChannelsAvailable(nChannelsAvailability);
    ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_ChannelBoardConnLabel).c_str(), CDeviceUtils::ConvertToString(nChannelsAvailability), MM::Integer, true);

    // Create read-only property for version info
    int nAxes = GetNumberOfAxes(nChannelsAvailability);
    ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_NumberOfAxesLabel).c_str(), CDeviceUtils::ConvertToString(nAxes), MM::Integer, true);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_NumberOfAxesLabel).c_str() << " = " << nAxes << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    // Create  property for debug log flag
    int nDebugLogFlag = nPC400::Instance()->GetDebugLogFlag();
    CPropertyAction* pActDebugLogFlag = new CPropertyAction (this, &nPC400Ctrl::OnDebugLogFlag);
    ret = CreateProperty(nPC400::Instance()->GetC400Str(nPC400::C400_DebugLogFlagLabel).c_str(), CDeviceUtils::ConvertToString(nDebugLogFlag), MM::Integer, false, pActDebugLogFlag);

    if (nPC400::Instance()->GetDebugLogFlag() > 0)
    {
        osMessage.str("");
        osMessage << "<nPC400Ctrl::Initialize> CreateProperty(" << nPC400::Instance()->GetC400Str(nPC400::C400_DebugLogFlagLabel).c_str() << " = " << nDebugLogFlag << "), ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }

    m_yInitialized = true;
    nPC400::Instance()->SetChannelsAvailable(nChannelsAvailability);

    return DEVICE_OK;
}

//
// shutdown the controller
//
int nPC400Ctrl::Shutdown()
{ 
    m_yInitialized = false;
    int nAvailable = 0;
    nPC400::Instance()->SetChannelsAvailable(nAvailable);
    return DEVICE_OK;
}

//
// check for valid communication port
//
int nPC400Ctrl::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    std::ostringstream osMessage;
    if (pAct == MM::BeforeGet)
    {
        pProp->Set(nPC400::Instance()->GetSerialPort().c_str());
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<nPC400Ctrl::OnPort> (BeforeGet::PORT=<" << nPC400::Instance()->GetSerialPort().c_str() << ">";
            osMessage << " PROPSET=<" << nPC400::Instance()->GetSerialPort().c_str() << ">)";
        }
    }
    else if (pAct == MM::AfterSet)
    {
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<nPC400Ctrl::OnPort> (AfterSet::PORT=<" << nPC400::Instance()->GetSerialPort().c_str() << ">";
        }
        if (m_yInitialized)
        {
            pProp->Set(nPC400::Instance()->GetSerialPort().c_str());
            if (nPC400::Instance()->GetDebugLogFlag() > 1)
            {
                osMessage << "Initialized::SET=<" << nPC400::Instance()->GetSerialPort().c_str() << ">";
            }
            return DEVICE_INVALID_INPUT_PARAM;
        }
        pProp->Get(nPC400::Instance()->GetSerialPort());
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << " PROPGET=<" << nPC400::Instance()->GetSerialPort().c_str() << ">)";
        }
    }
    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }
    return DEVICE_OK;
}

//
// get/set debug log flag
//
int nPC400Ctrl::OnDebugLogFlag(MM::PropertyBase* pProp, MM::ActionType pAct)
{
    long lDebugLogFlag = (long)nPC400::Instance()->GetDebugLogFlag();
    std::ostringstream osMessage;
    if (pAct == MM::BeforeGet)
    {
        pProp->Set(lDebugLogFlag);
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<nPC400Ctrl::OnDebugLogFalg> (BeforeGet::<" << nPC400::Instance()->GetC400Str(nPC400::C400_DebugLogFlagLabel).c_str() << "> PROPSET=<" << lDebugLogFlag << ">)";

        }
    }
    else if (pAct == MM::AfterSet)
    {
        pProp->Get(lDebugLogFlag);
        nPC400::Instance()->SetDebugLogFlag((int)lDebugLogFlag);
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage << "<nPC400Ctrl::OnDebugLogFalg> (AfterSet::<" << nPC400::Instance()->GetC400Str(nPC400::C400_DebugLogFlagLabel).c_str() << "> PROPSET=<" << lDebugLogFlag << ">)";

        }
    }
    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        this->LogMessage(osMessage.str().c_str());
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

int nPC400Ctrl::GetChannelsConnected(unsigned char* sResp)
{
    unsigned char sCmd[6] = { 0xA0, 0xA0, 0x03, 0x83, 0x11, (char)nPC400::C400_TxTerm };
    //strncpy(sCmd[5], nPC400::Instance()->GetC400Str(nPC400::C400_TxTerm).c_str, 1);

    // send command to read available channels
    int ret = WriteCommand(sCmd);
    if (ret != DEVICE_OK) return ret;

    // read message returned for available channels
    ret = ReadMessage(sResp);
    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Calculate number of channels connected
//
int nPC400Ctrl::GetNumberOfAxes(int nChannelsConnected)
{
    int nConnected = nChannelsConnected & 0x3F;
    int nBit = 1;
    int nNumAxes = 0;
    for (int nChannel = 0; nChannel < 6; nChannel++, nBit *= 2)
    {
        if (nConnected & nBit)
        {
            nNumAxes++;
        }
    }
    return nNumAxes;
}

//
// Sends a specified command to the controller
//
//void nPC400Ctrl::tohex(const unsigned char bByte, char* sHex)
//{
//    char sHexDigit[16] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
//    sHex[2] =  NULL;
//    sHex[1] = sHexDigit[(int)(bByte & 0xF)];
//    sHex[0] = sHexDigit[(int)(bByte / 0x10)];
//}

int nPC400Ctrl::WriteCommand(const unsigned char* sCommand)
{
    // reset serial buffer
    // int ret = ClearPort(*this, *GetCoreCallback(), nPC400::Instance()->GetSerialPort().c_str());

    int ret = DEVICE_OK;
    ostringstream osMessage;

    if (nPC400::Instance()->GetDebugLogFlag() > 1)
    {
        //osMessage << "<nPC400Ctrl::Initialize> ClearPort(Port = " << nPC400::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret;
        //this->LogMessage(osMessage.str().c_str());

        char sHex[3];
        osMessage.str("");
        osMessage << "<nPC400Ctyrl::WriteCommand> (cmd ";
        for (int n=0; n < 6; n++)
        {
            nPC400::Instance()->Byte2Hex(sCommand[n], sHex);
            osMessage << "[" << n << "]=<" << sHex << ">";
        }
        osMessage << ")";
        this->LogMessage(osMessage.str().c_str());
    }

    // write command out
    ret = DEVICE_OK;
    for (int nByte = 0; nByte < 6 && ret == DEVICE_OK; nByte++)
    {
        ret = WriteToComPort(nPC400::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCommand[nByte], 1);
        CDeviceUtils::SleepMs(1);
    }

    return ret;
}

//
// Read responded message
//
int nPC400Ctrl::ReadMessage(unsigned char* sMessage)
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
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, nPC400::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)nLength, lByteRead);
       
        if (nPC400::Instance()->GetDebugLogFlag() > 1)
        {
            osMessage.str("");
            osMessage << "<nPC400Ctrl::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
        }

        for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)
        {
            yRead = yRead || sAnswer[lRead+lIndx] == nPC400::C400_RxTerm;
            if (nPC400::Instance()->GetDebugLogFlag() > 1)
            {
                nPC400::Instance()->Byte2Hex(sAnswer[lRead+lIndx], sHex);
                osMessage << "[" << sHex  << "]";
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
