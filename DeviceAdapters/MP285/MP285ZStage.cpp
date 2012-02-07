//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285ZStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   MP285s Controller Driver
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
// AUTHOR:        Lon Chu (lonchu@yahoo.com), created on June 2011
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
#include "MP285Error.h"
#include "MP285ZStage.h"

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Z - Stage
///////////////////////////////////////////////////////////////////////////////
//
// Z Stage - single axis stage device.
// Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
// system with the X and Y axis going the 'Micro-Manager standard' direction
// Then, there is the MP285s native system.  All functions using 'steps' use the MP285 system
// All functions using Um use the Micro-Manager coordinate system
//

//
// Single axis stage constructor
//
ZStage::ZStage() :
    m_yInitialized(false)
    //m_nAnswerTimeoutMs(1000)
    //, stepSizeUm_(1)
{
    InitializeDefaultErrorMessages();

    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str(), MM::String, true);

    m_nAnswerTimeoutMs = MP285::Instance()->GetTimeoutInterval();
    m_nAnswerTimeoutTrys = MP285::Instance()->GetTimeoutTrys();

    std::ostringstream osMessage;
    osMessage << "<ZStage::class-constructor> CreateProperty(" << MM::g_Keyword_Name << "=" << MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str() << "), ReturnCode=" << ret << endl;
    this->LogMessage(osMessage.str().c_str());

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "MP-285 Z Stage Driver", MM::String, true);

    // osMessage.clear();
    osMessage << "<ZStage::class-constructor> CreateProperty(" << MM::g_Keyword_Description << " = MP-285 Z Stage Driver), ReturnCode=" << ret << endl;
    this->LogMessage(osMessage.str().c_str());
}


//
// Z stage destructor
//
ZStage::~ZStage()
{
    Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

//
// Z stage initialization
//
int ZStage::Initialize()
{
    std::ostringstream osMessage;

    if (!MP285::Instance()->GetDeviceAvailability()) return DEVICE_NOT_CONNECTED;
    //if (MP285::Instance()->GetNumberOfAxes() < 3) return DEVICE_NOT_CONNECTED;

    int ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionZ).c_str(), "Undefined", MM::Float, true);  // get position Z 

    osMessage << "<ZStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionZ).c_str() << " = " << "Undefined, ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    double dPosZ = MP285::Instance()->GetPositionZ();
    ret = GetPositionUm(dPosZ);


    osMessage.str("");
    osMessage << "<ZStage::Initialize> GetPosSteps(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionZ).c_str() << " = " << dPosZ << "), ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    CPropertyAction* pActOnSetPosZ = new CPropertyAction(this, &ZStage::OnSetPositionZ);
    ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionZ).c_str(), "Undefined", MM::Float, false, pActOnSetPosZ);  // Absolute  vs Relative 
    // ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionZ).c_str(), "Undefined", MM::Integer, true);  // Absolute  vs Relative 

    osMessage.str("");
    osMessage << "<ZStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionZ).c_str() << " = Undefined), ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    ret = UpdateStatus();
    if (ret != DEVICE_OK) return ret;

    m_yInitialized = true;
    return DEVICE_OK;
}

//
//  Shutdown Z stage
//
int ZStage::Shutdown()
{
    m_yInitialized = false;
    MP285::Instance()->SetDeviceAvailable(false);
    return DEVICE_OK;
}

//
// Get the device name of the Z stage
//
void ZStage::GetName(char* Name) const
{
    CDeviceUtils::CopyLimitedString(Name, MP285::Instance()->GetMPStr(MP285::MPSTR_ZStageDevName).c_str());
}

//
// Get Z stage position in um
//
int ZStage::GetPositionUm(double& dZPosUm)
{
    long lZPosSteps = 0;

    int ret = GetPositionSteps(lZPosSteps);
    if (ret != DEVICE_OK) return ret;

    dZPosUm = (double)lZPosSteps / (double)MP285::Instance()->GetUm2UStep();

    ostringstream osMessage;
    osMessage << "<ZStage::GetPositionUm> (z=" << dZPosUm << ")";
    this->LogMessage(osMessage.str().c_str());

    MP285::Instance()->SetPositionZ(dZPosUm);

    char sPosition[20];
    sprintf(sPosition, "%.2f", dZPosUm);
    ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionZ).c_str(), sPosition);

    osMessage.str("");
    osMessage << "<ZStage::GetPositionUm> Z=[" << dZPosUm << "," << sPosition << "], Returncode=" << ret ;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;


    ret = UpdateStatus();
    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Move to Z stage position in um
//
int ZStage::SetPositionUm(double dZPosUm)
{
    ostringstream osMessage;
    osMessage << "<ZStage::SetPositionUm> (z=" << dZPosUm << ")";
    this->LogMessage(osMessage.str().c_str());

    // convert um to steps 
    long lZPosSteps = (long)(dZPosUm * (double)MP285::Instance()->GetUm2UStep());

    // send move command to controller
    int ret = SetPositionSteps(lZPosSteps);

    if (ret != DEVICE_OK) return ret;

    MP285::Instance()->SetPositionZ(dZPosUm);

    double dPosZ = 0.;

    ret = GetPositionUm(dPosZ);

    if (ret != DEVICE_OK) return ret;

    return ret;
}

//
// Get Z stage position in steps
//
int ZStage::GetPositionSteps(long& lZPosSteps)
{
    // get current position
    unsigned char sCommand[6] = { 0x63, MP285::MP285_TxTerm, 0x0A, 0x00, 0x00, 0x00 };
    int ret = WriteCommand(sCommand, 3);

    if (ret != DEVICE_OK)  return ret;

    unsigned char sResponse[64];
    memset(sResponse, 0, 64);

    bool yCommError = false;
    int nTrys = 0;

    while (!yCommError && nTrys < MP285::Instance()->GetTimeoutTrys())
    {
        long lXPosSteps = (long) (MP285::Instance()->GetPositionX() * (double) MP285::Instance()->GetUm2UStep());
        long lYPosSteps = (long) (MP285::Instance()->GetPositionY() * (double) MP285::Instance()->GetUm2UStep());

        ret = ReadMessage(sResponse, 14);

        ostringstream osMessage;
        char sCommStat[30];
        int nError = CheckError(sResponse[0]);
        yCommError = (sResponse[0] == 0) ? false : nError != 0;
        if (yCommError)
        {
            if (nError == MPError::MPERR_SerialZeroReturn && nTrys < MP285::Instance()->GetTimeoutTrys()) { nTrys++; yCommError = false; }
            osMessage.str("");
            osMessage << "<XYStage::GetPositionSteps> Response = (" << nError << "," << nTrys << ")" ;
            sprintf(sCommStat, "Error Code ==> <%2x>", sResponse[0]);
        }
        else
        {
            lXPosSteps = *((long*)(&sResponse[0]));
            lYPosSteps = *((long*)(&sResponse[4]));
            lZPosSteps = *((long*)(&sResponse[8]));
            //MP285::Instance()->SetPositionX(lXPosSteps);
            //MP285::Instance()->SetPositionY(lYPosSteps);
            //MP285::Instance()->SetPositionZ(lZPosSteps);
            strcpy(sCommStat, "Success");

            osMessage.str("");
            osMessage << "<ZStage::GetPositionSteps> Response(X = <" << lXPosSteps << ">, Y = <" << lYPosSteps << ">, Z = <"<< lZPosSteps << ">), ReturnCode=" << ret;
            nTrys = MP285::Instance()->GetTimeoutTrys();

        }

        this->LogMessage(osMessage.str().c_str());

        //ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_CommStateLabel).c_str(), sCommStat);

    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}


  
//
// move z stage in steps
//
int ZStage::SetPositionSteps(long lZPosSteps)
{
    // clean up COM port
    // PurgeComPort(MP285::Instance()->GetSerialPort().c_str());

    ostringstream osMessage;
    
    //double dPosZ = 0.;
    //int ret = GetPositionSteps(lPosZ);

    osMessage << "<ZStage::SetPositionSteps> (z=" << lZPosSteps << ")";
    this->LogMessage(osMessage.str().c_str());

    // get current position command
    // get current position
    long* plPositionX = NULL;
    long* plPositionY = NULL;
    long* plPositionZ = NULL;
    unsigned char sCommand[16];
    memset(sCommand, 0, 16);
    sCommand[0]  = 0x6D;
    sCommand[13] = MP285::MP285_TxTerm;
    sCommand[14] = 0x0A;
    plPositionX  = (long*)(&sCommand[1]);
    *plPositionX = (long) (MP285::Instance()->GetPositionX() * (double)MP285::Instance()->GetUm2UStep());
    plPositionY  = (long*)(&sCommand[5]);
    *plPositionY = (long) (MP285::Instance()->GetPositionY() * (double)MP285::Instance()->GetUm2UStep());
    plPositionZ  = (long*)(&sCommand[9]);
    *plPositionZ = lZPosSteps;

    int ret = WriteCommand(sCommand, 15);

    osMessage.str("");
    osMessage << "<ZStage::SetPositionSteps> Command(<0x6D>, X = <" << *plPositionX << ">,<" << *plPositionY << ">,<" << *plPositionZ << ">), ReturnCode=" << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    double dSteps = (double)labs(lZPosSteps);
    DWORD dwSleep = (DWORD) (dSteps * 2.0);
    Sleep(dwSleep);
    
    osMessage.str("");
    osMessage << "<ZStage::SetPositionSteps> Sleep..." << dwSleep << " millisec...";
    this->LogMessage(osMessage.str().c_str());

    bool yCommError = true;

    while (yCommError)
    {
        unsigned char sResponse[64];
        memset(sResponse, 0, 64);

        ret = ReadMessage(sResponse, 2);

        //char sCommStat[30];
        yCommError = CheckError(sResponse[0]) !=0;
        //if (yCommError)
        //{
        //    sprintf(sCommStat, "Error Code ==> <%2x>", sResponse[0]);
        //}
        // else
        //{
        //     strcpy(sCommStat, "Success");
        //}

        //ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_CommStateLabel).c_str(), sCommStat);
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Set current position as origin
//
int ZStage::SetOrigin()
{
    unsigned char sCommand[6] = { 0x6F, MP285::MP285_TxTerm, 0x0A, 0x00, 0x00, 0x00 };
    int ret = WriteCommand(sCommand, 3);

    std::ostringstream osMessage;
    osMessage << "<ZStage::SetOrigin> (ReturnCode=" << ret << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret!=DEVICE_OK) return ret;

    unsigned char sResponse[64];

    memset(sResponse, 0, 64);
    ret = ReadMessage(sResponse, 2);
    osMessage << "<ZStage::CheckStatus::SetOrigin> (ReturnCode = " << ret << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    bool yCommError = CheckError(sResponse[0]) != 0;

    char sCommStat[30];
    if (yCommError)
        sprintf(sCommStat, "Error Code ==> <%2x>", sResponse[0]);
    else
        strcpy(sCommStat, "Success");

    //ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_CommStateLabel).c_str(), sCommStat);

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// stop and interrupt Z stage motion
//
int ZStage::Stop()
{
    unsigned char sCommand[6] = { 0x03, MP285::MP285_TxTerm, 0x00, 0x00, 0x00, 0x00 };

    int ret = WriteCommand(sCommand, 2);

    ostringstream osMessage;
    osMessage << "<ZStage::Stop> (ReturnCode = " << ret << ")";
    this->LogMessage(osMessage.str().c_str());

    return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

//
// Unsupported command from MP285
//
int ZStage::OnStepSize (MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/) 
{
    return DEVICE_OK;
}

int ZStage::OnSpeed(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
    return DEVICE_OK;
}

int ZStage::OnGetPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPos = MP285::Instance()->GetPositionZ();

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dPos);

        osMessage << "<MP285Ctrl::OnSetPositionX> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str() << " = [" << dPos << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        // pProp->Get(dPos);  // not used

        ret = GetPositionUm(dPos);

        if (ret == DEVICE_OK) pProp->Set(dPos);

        osMessage << "<MP285Ctrl::OnSetPositionX> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str() << " = [" << dPos << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());

    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

int ZStage::OnSetPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPos = MP285::Instance()->GetPositionZ();;

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dPos);

        osMessage << "<MP285Ctrl::OnSetPositionZ> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionZ).c_str() << " = [" << dPos << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(dPos);

        ret = SetPositionUm(dPos);

        osMessage << "<MP285Ctrl::OnSetPositionZ> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionZ).c_str() << " = [" << dPos << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());

    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;


    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

//
// Write a coomand to serial port
//
int ZStage::WriteCommand(unsigned char* sCommand, int nLength)
{
    int ret = DEVICE_OK;
    ostringstream osMessage;
    osMessage << "<ZStage::WriteCommand> (Command=";
    char sHex[4] = { NULL, NULL, NULL, NULL };
    for (int nBytes = 0; nBytes < nLength && ret == DEVICE_OK; nBytes++)
    {
        ret = WriteToComPort(MP285::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCommand[nBytes], 1);
        MP285::Instance()->Byte2Hex((const unsigned char)sCommand[nBytes], sHex);
        osMessage << "[" << nBytes << "]=<" << sHex << ">";
        CDeviceUtils::SleepMs(1);
    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// Read a message from serial port
//
int ZStage::ReadMessage(unsigned char* sResponse, int nBytesRead)
{
    // block/wait for acknowledge, or until we time out;
    unsigned int nLength = 256;
    unsigned char sAnswer[256];
    memset(sAnswer, 0, nLength);
    unsigned long lRead = 0;
    unsigned long lStartTime = GetClockTicksUs();

    ostringstream osMessage;
    char sHex[4] = { NULL, NULL, NULL, NULL };
    int ret = DEVICE_OK;
    bool yRead = false;
    bool yTimeout = false;
    while (!yRead && !yTimeout && ret == DEVICE_OK )
    {
        unsigned long lByteRead;

        const MM::Device* pDevice = this;
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, MP285::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)nLength-lRead, lByteRead);
       
        //osMessage.str("");
        //osMessage << "<MP285Ctrl::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
        //for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)
        //{
        //    // convert to hext format
        //    MP285::Instance()->Byte2Hex(sAnswer[lRead+lIndx], sHex);
        //    osMessage << "[" << sHex  << "]";
        //}
        //osMessage << ">";
        //this->LogMessage(osMessage.str().c_str());

        // concade new string
        lRead += lByteRead;

        if (lRead > 2)
        {
            yRead = (sAnswer[0] == 0x30 || sAnswer[0] == 0x31 || sAnswer[0] == 0x32 || sAnswer[0] == 0x34 || sAnswer[0] == 0x38) &&
                    (sAnswer[1] == 0x0D) &&
                    (sAnswer[2] == 0x0D);
        }

        yRead = yRead || (lRead >= (unsigned long)nBytesRead);

        if (yRead) break;
        
        // check for timeout
        yTimeout = ((double)(GetClockTicksUs() - lStartTime) / 1000.) > (double) m_nAnswerTimeoutMs;
        if (!yTimeout) CDeviceUtils::SleepMs(1);
    }

    // block/wait for acknowledge, or until we time out
    // if (!yRead || yTimeout) return DEVICE_SERIAL_TIMEOUT;
    // MP285::Instance()->ByteCopy(sResponse, sAnswer, nBytesRead);
    // if (checkError(sAnswer[0]) != 0) ret = DEVICE_SERIAL_COMMAND_FAILED;

    osMessage.str("");
    osMessage << "<MP285Ctrl::ReadMessage> (ReadFromSerial = <";
    for (unsigned long lIndx=0; lIndx < (unsigned long)nBytesRead; lIndx++)
    {
        sResponse[lIndx] = sAnswer[lIndx];
        MP285::Instance()->Byte2Hex(sResponse[lIndx], sHex);
        osMessage << "[" << sHex  << ",";
        MP285::Instance()->Byte2Hex(sAnswer[lIndx], sHex);
        osMessage << sHex  << "]";
    }
    osMessage << ">";
    this->LogMessage(osMessage.str().c_str());

    return DEVICE_OK;
}

//
// check the error code for the message returned from serial communivation
//
int ZStage::CheckError(unsigned char bErrorCode)
{
    // if the return message is 2 bytes message including CR
    unsigned int nErrorCode = 0;
    ostringstream osMessage;

    // check 4 error code
    if (bErrorCode == MP285::MP285_SP_OVER_RUN)
    {
        // Serial command buffer over run
        nErrorCode = MPError::MPERR_SerialOverRun;       
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_FRAME_ERROR)
    {
        // Receiving serial command time out
        nErrorCode = MPError::MPERR_SerialTimeout;       
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_BUFFER_OVER_RUN)
    {
        // Serial command buffer full
        nErrorCode = MPError::MPERR_SerialBufferFull;       
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_BAD_COMMAND)
    {
        // Invalid serial command
        nErrorCode = MPError::MPERR_SerialInpInvalid;       
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_MOVE_INTERRUPTED)
    {
        // Serial command interrupt motion
        nErrorCode = MPError::MPERR_SerialIntrupMove;       
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == 0x0D)
    {
        // read carriage return
        nErrorCode = MPError::MPERR_OK;
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == 0x00)
    {
        // No response from serial port
        nErrorCode = MPError::MPERR_SerialZeroReturn;
        osMessage << "<ZStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }

    this->LogMessage(osMessage.str().c_str());

    return (nErrorCode);
}

#if 0
int ZStage::GetCommand(const string& sCmd, string& sResponse)
{

    // reset serial buffer
    int ret = ClearPort(*this, *GetCoreCallback(), MP285::Instance()->GetSerialPort().c_str());

    ostringstream osMessage;
    osMessage << "<MP285::Ctrl::Initialize> ClearPort(Port = " << MP285::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret << endl;
    this->LogMessage(osMessage.str().c_str());


    osMessage.str("");
    osMessage << "<MP285::XYStage::GetCommand> (SndCmd=" << hex << sCmd.c_str() << ")";
    this->LogMessage(osMessage.str().c_str());

    char sCommand[20];
    memset(sCommand, 0, 20);
    strcpy(sCommand, sCmd.c_str());
//    strcpy(&sCommand[strlen(sCommand)],  MP285::Instance()->GetMPStr(MP285::Instance()->MPSTR_TxTerm).c_str());

    // write command out
    ret = DEVICE_OK;
    for (int nByte = 0; nByte < strlen(sCommand) && ret == DEVICE_OK; nByte++)
    {
        ret = WriteToComPort(MP285::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCmd[nByte], 1);
        CDeviceUtils::SleepMs(1);
    }

    // block/wait for acknowledge, or until we time out;
    unsigned int nLength = 256;
    char sAnswer[256];
    memset(sAnswer, 0, nLength);
    unsigned long lRead = 0;
    unsigned long lStartTime = GetClockTicksUs();

    // terminate character
    char sRxTerm[8];
    memset(sRxTerm, 0, 8);
//    strncpy(sRxTerm, MP285::Instance()->GetMPStr(MP285::MPSTR_RxTerm).c_str(), 1);

    bool yRead = false;
    bool yTimeout = false;
    while (!yRead && !yTimeout && ret == DEVICE_OK )
    {
        unsigned long lByteRead;

        const MM::Device* pDevice = this;
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, MP285::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)nLength, lByteRead);
       
        osMessage.str("");
        osMessage << "<MP285::Ctrl::checkStatus> (ReadFromSerial = (" << lByteRead << ")::<";
        for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)  { osMessage << "[" << hex << sAnswer[lRead+lIndx] << "]"; }
        osMessage << ">" << endl;
        this->LogMessage(osMessage.str().c_str());

        if (ret == DEVICE_OK && lByteRead > 0)
        {
            lRead += lByteRead;
            if (strstr(&sAnswer[lRead], sRxTerm)) yRead = true;
        }

        yTimeout = ((GetClockTicksUs() - lStartTime) / 1000) > m_nAnswerTimeoutMs;

        // delay 1ms
        if (!yTimeout) CDeviceUtils::SleepMs(1);
    }

    if (ret != DEVICE_OK) return ret;
    if (yTimeout) return DEVICE_SERIAL_TIMEOUT;

    // check error code from returned message
    osMessage.str("");
    unsigned int nErrorCode = 0;
    nLength = strlen(sAnswer);
    if (nLength > 0)
    {
        // check for error code
        if (sAnswer[0] == '0')
        {
            // Serial command buffer over run
            nErrorCode = MPError::MPERR_SerialOverRun;       
            osMessage << "<MP285::Ctrl::checkError> ("<< "XYStage Read" << ") ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
        }
        else if (sAnswer[0] == '1')
        {
            // Receiving serial command time out
            nErrorCode = MPError::MPERR_SerialTimeout;       
            osMessage << "<MP285::Ctrl::checkError> ("<< "XYStage Read" << ") ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
        }
        else if (sAnswer[0] == '2')
        {
            // Serial command buffer full
            nErrorCode = MPError::MPERR_SerialBufferFull;       
            osMessage << "<MP285::Ctrl::checkError> ("<< "XYStage Read" << ") ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
        }
        else if (sAnswer[0] == '4')
        {
            // Invalid serial command
            nErrorCode = MPError::MPERR_SerialInpInvalid;       
            osMessage << "<MP285::Ctrl::checkError> ("<< "XYStage Read" << ") ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
        }
        else if (sAnswer[0] == '8')
        {
            // Serial command interrupt motion
            nErrorCode = MPError::MPERR_SerialIntrupMove;       
            osMessage << "<MP285::Ctrl::checkError> ("<< "XYStage Read" << ") ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
        }
    }
    else
    {
        // No response from serial port
        nErrorCode = MPError::MPERR_SerialZeroReturn;
        osMessage << "<MP285::Ctrl::checkError> (" <<  "XYStage Read" << ") Response=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }

    this->LogMessage(osMessage.str().c_str());

    if (nErrorCode > 0) return DEVICE_SERIAL_INVALID_RESPONSE;
    
    sResponse.append(sAnswer, strlen(sAnswer));

    return DEVICE_OK;
}

#endif 



