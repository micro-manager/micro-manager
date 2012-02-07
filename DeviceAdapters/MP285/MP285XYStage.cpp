//////////////////////////////////////////////////////////////////////////////
// FILE:          MP285XYStage.cpp
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
#include "MP285XYStage.h"

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////
//
// XYStage - two axis stage device.
// Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
// system with the X and Y axis going the 'Micro-Manager standard' direction
// Then, there is the MP285s native system.  All functions using 'steps' use the MP285 system
// All functions using Um use the Micro-Manager coordinate system
//

//
// XY Stage constructor
//
XYStage::XYStage() :
    m_yInitialized(false)
    //range_measured_(false),
    //m_nAnswerTimeoutMs(1000)
    //stepSizeUm_(1.0),
    //set speed & accel variables?
    //originX_(0),
    //originY_(0)
{
    InitializeDefaultErrorMessages();

    // create pre-initialization properties
    // ------------------------------------
    // NOTE: pre-initialization properties contain parameters which must be defined fo
    // proper startup

    m_nAnswerTimeoutMs = MP285::Instance()->GetTimeoutInterval();
    m_nAnswerTimeoutTrys = MP285::Instance()->GetTimeoutTrys();

    // Name, read-only (RO)
    int ret = CreateProperty(MM::g_Keyword_Name, MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str(), MM::String, true);

    std::ostringstream osMessage;
    osMessage << "<XYStage::class-constructor> CreateProperty(" << MM::g_Keyword_Name << "=" << MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str() << "), ReturnCode=" << ret;
    this->LogMessage(osMessage.str().c_str());

    // Description, RO
    ret = CreateProperty(MM::g_Keyword_Description, "MP-285 XY Stage Driver", MM::String, true);

    osMessage.clear();
    osMessage << "<XYStage::class-constructor> CreateProperty(" << MM::g_Keyword_Description << " = MP-285 XY Stage Driver), ReturnCode=" << ret;
    this->LogMessage(osMessage.str().c_str());
}

//
// XY Stage destructor
//
XYStage::~XYStage()
{
    Shutdown();
}

void XYStage::GetName(char* sName) const
{
    CDeviceUtils::CopyLimitedString(sName, MP285::Instance()->GetMPStr(MP285::MPSTR_XYStgaeDevName).c_str());
}

//
// Performs device initialization.
// Additional properties can be defined here too.
//
int XYStage::Initialize()
{
    std::ostringstream osMessage;

    if (!MP285::Instance()->GetDeviceAvailability()) return DEVICE_NOT_CONNECTED;

    int ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str(), "Undefined", MM::Float, true);  // Get Position X 

    this->LogMessage(osMessage.str().c_str());
    osMessage << "<XYStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str() << " = " << "Undfined, ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str(), "Undefined", MM::Float, true);  // Get Position Y 
 
    osMessage.str("");
    osMessage << "<XYStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str() << " = " << "Undefined, ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    double dPosX = 0.;
    double dPosY = 0.;

    ret = GetPositionUm(dPosX, dPosY);

    // osMessage.str("");
    osMessage << "<XYStage::Initialize> GetPosSteps(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str() << " = " << dPosX << ",";
    osMessage << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str() << " = " << dPosY << "), ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret!=DEVICE_OK) return ret;

    CPropertyAction* pActOnSetPosX = new CPropertyAction(this, &XYStage::OnSetPositionX);
    ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str(), "Undefined", MM::Float, false, pActOnSetPosX);  // Set Position X 
    //ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str(), "Undefined", MM::Integer, true);  // Set Position X 

    osMessage.str("");
    osMessage << "<XYStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str() << " = Undefined), ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    CPropertyAction* pActOnSetPosY = new CPropertyAction(this, &XYStage::OnSetPositionY);
    ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionY).c_str(), "Undefined", MM::Float, false, pActOnSetPosY);  // Set Position Y 
    //ret = CreateProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionY).c_str(), "Undefined", MM::Integer, true);  // Set Position Y 

    osMessage.str("");
    osMessage << "<XYStage::Initialize> CreateProperty(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionY).c_str() << " = Undefined), ReturnCode = " << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;


    ret = UpdateStatus();
    if (ret != DEVICE_OK) return ret;

    m_yInitialized = true;
    return DEVICE_OK;
}

//
// shutdown X-Y stage
//
int XYStage::Shutdown()
{
    m_yInitialized= false;
    MP285::Instance()->SetDeviceAvailable(false);
    return DEVICE_OK;
}

//
// Returns current X-Y position in µm.
//
int XYStage::GetPositionUm(double& dXPosUm, double& dYPosUm)
{
    long lXPosSteps = 0;
    long lYPosSteps = 0;

    int ret = GetPositionSteps(lXPosSteps, lYPosSteps);

    if (ret != DEVICE_OK) return ret;

    dXPosUm = (double)lXPosSteps / (double)MP285::Instance()->GetUm2UStep();
    dYPosUm = (double)lYPosSteps / (double)MP285::Instance()->GetUm2UStep();

    ostringstream osMessage;
    osMessage << "<MP285::XYStage::GetPositionUm> (x=" << dXPosUm << ", y=" << dYPosUm << ")";
    this->LogMessage(osMessage.str().c_str());

    MP285::Instance()->SetPositionX(dXPosUm);
    MP285::Instance()->SetPositionY(dYPosUm);

    char sPosition[20];
    sprintf(sPosition, "%.2f", dXPosUm);
    ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str(), sPosition);

    osMessage.str("");
    osMessage << "<XYStage::GetPositionUm> X=[" << dXPosUm << "," << sPosition << "], Returncode=" << ret ;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    sprintf(sPosition, "%.2f", dYPosUm);
    ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str(), sPosition);

    osMessage.str("");
    osMessage << "<XYStage::GetPositionUm> Y=[" << dYPosUm << "," << sPosition << "], Returncode=" << ret ;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    ret = UpdateStatus();
    if (ret != DEVICE_OK) return ret;


    return DEVICE_OK;
}


//
// Move 2 x-y position in µm
//
int XYStage::SetPositionUm(double dXPosUm, double dYPosUm)
{
    ostringstream osMessage;
    osMessage << "<XYStage::SetPositionUm> (x=" << dXPosUm << ", y=" << dYPosUm << ")";
    this->LogMessage(osMessage.str().c_str());

    // convert um to steps 
    long lXPosSteps = (long)(dXPosUm * (double)MP285::Instance()->GetUm2UStep());
    long lYPosSteps = (long)(dYPosUm * (double)MP285::Instance()->GetUm2UStep());

    // send move command to controller
    int ret = SetPositionSteps(lXPosSteps, lYPosSteps);

    if (ret != DEVICE_OK) return ret;

    double dPosX = 0.;
    double dPosY = 0.;

    ret = GetPositionUm(dPosX, dPosY);

    if (ret != DEVICE_OK) return ret;

    return ret;
}
  
//
// Returns current position in steps.
//
int XYStage::GetPositionSteps(long& lXPosSteps, long& lYPosSteps)
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
        long lZPosSteps = (long) (MP285::Instance()->GetPositionZ() * (double)MP285::Instance()->GetUm2UStep());

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

            osMessage.str("");
            osMessage << "<XYStage::GetPositionSteps> Response(X = <" << lXPosSteps << ">, Y = <" << lYPosSteps << ">, Z = <"<< lZPosSteps << ">), ReturnCode=" << ret;
            nTrys = MP285::Instance()->GetTimeoutTrys();
            strcpy(sCommStat, "Success");
           
        }

        this->LogMessage(osMessage.str().c_str());

        //ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_CommStateLabel).c_str(), sCommStat);
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long lXPosSteps, long lYPosSteps)
{
    // clean up COM port
    // PurgeComPort(MP285::Instance()->GetSerialPort().c_str());

    ostringstream osMessage;

    //long lPosX = 0;
    //long lPosY = 0;
    //int ret = GetPositionSteps(lPosX, lPosY);

    osMessage << "<XYStage::SetPositionSteps> (x=" << lXPosSteps << ", y=" << lYPosSteps << ")";
    this->LogMessage(osMessage.str().c_str());

    //if (ret != DEVICE_OK) return ret;

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
    *plPositionX = lXPosSteps;
    plPositionY  = (long*)(&sCommand[5]);
    *plPositionY = lYPosSteps;
    plPositionZ  = (long*)(&sCommand[9]);
    *plPositionZ = (long) (MP285::Instance()->GetPositionZ() * (double)MP285::Instance()->GetUm2UStep());

    int ret = WriteCommand(sCommand, 15);

    osMessage.str("");
    osMessage << "<XYStage::SetPositionSteps> Command(<0x6D>, X = <" << *plPositionX << ">,<" << *plPositionY << ">,<" << *plPositionZ << ">), ReturnCode=" << ret;
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK)  return ret;

    double dSteps = labs(lXPosSteps) > labs(lYPosSteps) ? (double)labs(lXPosSteps) : (double)labs(lYPosSteps);
    unsigned long dwSleep = (unsigned long) (dSteps * 3.0);
    CDeviceUtils::SleepMs(dwSleep);
    
    osMessage.str("");
    osMessage << "<XYStage::SetPositionSteps> Sleep..." << dwSleep << " millisec...";
    this->LogMessage(osMessage.str().c_str());

    bool yCommError = true;

    while (yCommError)
    {
        unsigned char sResponse[64];
        memset(sResponse, 0, 64);

        ret = ReadMessage(sResponse, 2);

        //char sCommStat[30];
        yCommError = CheckError(sResponse[0]) != 0;
        //if (yCommError)
        //{
        //    sprintf(sCommStat, "Error Code ==> <%2x>", sResponse[0]);
        //}
        //else
        //{
        //    strcpy(sCommStat, "Success");
        //}

        //ret = SetProperty(MP285::Instance()->GetMPStr(MP285::MPSTR_CommStateLabel).c_str(), sCommStat);
    }

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

//
// stop and interrupt Z stage motion
//
int XYStage::Stop()
{
    unsigned char sCommand[6] = { 0x03, MP285::MP285_TxTerm , 0x00, 0x00, 0x00, 0x00};

    int ret = WriteCommand(sCommand, 2);

    ostringstream osMessage;
    osMessage << "<XYStage::Stop> (ReturnCode = " << ret << ")";
    this->LogMessage(osMessage.str().c_str());

    return ret;
}

//
// Set current position as origin (0,0) coordinate of the controller.
//
int XYStage::SetOrigin()
{
    unsigned char sCommand[6] = { 0x6F, MP285::MP285_TxTerm, 0x0A, 0x00, 0x00, 0x00 };
    int ret = WriteCommand(sCommand, 3);

    std::ostringstream osMessage;
    osMessage << "<XYStage::SetOrigin> (ReturnCode=" << ret << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret!=DEVICE_OK) return ret;

    unsigned char sResponse[64];

    memset(sResponse, 0, 64);
    ret = ReadMessage(sResponse, 2);
    osMessage << "<XYStage::CheckStatus::SetOrigin> (ReturnCode = " << ret << ")";
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



///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnSpeed(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
    return DEVICE_OK;
}

int XYStage::OnGetPositionX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPosX = MP285::Instance()->GetPositionX();
    double dPosY = MP285::Instance()->GetPositionY();

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dPosX);

        osMessage << "<MP285Ctrl::OnGetPositionX> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str() << " = [" << dPosX << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        // pProp->Get(dPos);

        ret = GetPositionUm(dPosX, dPosY);

        if (ret == DEVICE_OK) pProp->Set(dPosX);

        osMessage << "<MP285Ctrl::OnGetPositionX> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionX).c_str() << " = [" << dPosX << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());

    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

int XYStage::OnGetPositionY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPosX = MP285::Instance()->GetPositionX();
    double dPosY = MP285::Instance()->GetPositionY();

    if (eAct == MM::BeforeGet)
    {        
        pProp->Set(dPosY);

        osMessage << "<MP285Ctrl::OnGetPositionY> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str() << " = [" << dPosY << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        // pProp->Get(dPos)

        ret = GetPositionUm(dPosX, dPosY);

        if (ret == DEVICE_OK) pProp->Set(dPosY);

        osMessage << "<MP285Ctrl::OnGetPositionY> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_GetPositionY).c_str() << " = [" << dPosY << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

int XYStage::OnSetPositionX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPosX = MP285::Instance()->GetPositionX();
    double dPosY = MP285::Instance()->GetPositionY();

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dPosX);

        osMessage << "<MP285Ctrl::OnSetPositionX> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str() << " = [" << dPosX << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(dPosX);

        ret = SetPositionUm(dPosX, dPosY);

        osMessage << "<MP285Ctrl::OnSetPositionX> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionX).c_str() << " = [" << dPosX << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    osMessage << ")";
    this->LogMessage(osMessage.str().c_str());

    if (ret != DEVICE_OK) return ret;

    return DEVICE_OK;
}

int XYStage::OnSetPositionY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    std::ostringstream osMessage;
    int ret = DEVICE_OK;
    double dPosX = MP285::Instance()->GetPositionX();
    double dPosY = MP285::Instance()->GetPositionY();

    if (eAct == MM::BeforeGet)
    {
        pProp->Set(dPosY);

        osMessage << "<MP285Ctrl::OnSetPositionY> BeforeGet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionY).c_str() << " = [" << dPosY << "], ReturnCode = " << ret;
        this->LogMessage(osMessage.str().c_str());
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(dPosY);

        ret = SetPositionUm(dPosX, dPosY);

        osMessage << "<MP285Ctrl::OnSetPositionY> AfterSet(" << MP285::Instance()->GetMPStr(MP285::MPSTR_SetPositionY).c_str() << " = [" << dPosY << "], ReturnCode = " << ret;
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
int XYStage::WriteCommand(unsigned char* sCommand, int nLength)
{
    int ret = DEVICE_OK;
    ostringstream osMessage;
    osMessage << "<XYStage::WriteCommand> (Command=";
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
int XYStage::ReadMessage(unsigned char* sResponse, int nBytesRead)
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
        // osMessage << "<MP285Ctrl::ReadMessage> (ReadFromSerial = (" << lByteRead << ")::<";
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
        else if (lRead == 2)
        {
            yRead = (sAnswer[0] == 0x0D) && (sAnswer[1] == 0x0D);
        }

        yRead = yRead || (lRead >= (unsigned long)nBytesRead);

        if (yRead) break;
        
        // check for timeout
        yTimeout = ((double)(GetClockTicksUs() - lStartTime) / 1000.) > (double)m_nAnswerTimeoutMs;
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
int XYStage::CheckError(unsigned char bErrorCode)
{
    // if the return message is 2 bytes message including CR
    unsigned int nErrorCode = 0;
    ostringstream osMessage;

    // check 4 error code
    if (bErrorCode == MP285::MP285_SP_OVER_RUN)
    {
        // Serial command buffer over run
        nErrorCode = MPError::MPERR_SerialOverRun;       
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_FRAME_ERROR)
    {
        // Receiving serial command time out
        nErrorCode = MPError::MPERR_SerialTimeout;       
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_BUFFER_OVER_RUN)
    {
        // Serial command buffer full
        nErrorCode = MPError::MPERR_SerialBufferFull;       
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_BAD_COMMAND)
    {
        // Invalid serial command
        nErrorCode = MPError::MPERR_SerialInpInvalid;       
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }
    else if (bErrorCode == MP285::MP285_MOVE_INTERRUPTED)
    {
        // Serial command interrupt motion
        nErrorCode = MPError::MPERR_SerialIntrupMove;       
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
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
        osMessage << "<XYStage::checkError> ErrorCode=[" << MPError::Instance()->GetErrorText(nErrorCode).c_str() << "])";
    }

    this->LogMessage(osMessage.str().c_str());

    return (nErrorCode);
}

#if 0

//
// Sends a specified command to the controller
//
int XYStage::GetCommand(const string& sCommand, string& sResponse)
{
    // reset serial buffer
    int ret = ClearPort(*this, *GetCoreCallback(), MP285::Instance()->GetSerialPort().c_str());

    ostringstream osMessage;
    osMessage << "<MP285::Ctrl::Initialize> ClearPort(Port = " << MP285::Instance()->GetSerialPort().c_str() << "), ReturnCode = " << ret << endl;
    this->LogMessage(osMessage.str().c_str());


    osMessage.str("");
    osMessage << "<MP285::XYStage::GetCommand> (SndCmd=" << hex << sCommand.c_str() << ")";
    this->LogMessage(osMessage.str().c_str());

    char sCmd[20];
    memset(sCmd, 0, 20);
    strcpy(sCmd, sCommand.c_str());
    sCmd[strlen(sCmd)] = MP285::MP285_TxTerm;

    // write command out
    ret = DEVICE_OK;
    for (unsigned int nByte = 0; nByte < strlen(sCmd) && ret == DEVICE_OK; nByte++)
    {
        ret = WriteToComPort(MP285::Instance()->GetSerialPort().c_str(), (const unsigned char*)&sCmd[nByte], 1);
        CDeviceUtils::SleepMs(1);
    }

    if (ret != DEVICE_OK) return ret;

    // block/wait for acknowledge, or until we time out;
    unsigned int nLength = 256;
    char sAnswer[256];
    memset(sAnswer, 0, nLength);
    unsigned long lRead = 0;
    unsigned long lStartTime = GetClockTicksUs();

    // terminate character
    char sRxTerm[8];
    memset(sRxTerm, 0, 8);
    sRxTerm[0] = MP285::MP285_RxTerm;

    bool yRead = false;
    bool yTimeout = false;
    while (!yRead && !yTimeout && ret == DEVICE_OK )
    {
        unsigned long lByteRead;

        const MM::Device* pDevice = this;
        ret = (GetCoreCallback())->ReadFromSerial(pDevice, MP285::Instance()->GetSerialPort().c_str(), (unsigned char *)&sAnswer[lRead], (unsigned long)nLength, lByteRead);
       
        osMessage.str("");
        osMessage << "<MP285::XYStage::ReadFromSerial> (ReadFromSerial = (" << lByteRead << ")::<";
        for (unsigned long lIndx=0; lIndx < lByteRead; lIndx++)  { osMessage << "[" << hex << sAnswer[lRead+lIndx] << "]"; }
        osMessage << ">" << endl;
        this->LogMessage(osMessage.str().c_str());

        if (ret == DEVICE_OK && lByteRead > 0)
        {
            lRead += lByteRead;
            if (strstr(&sAnswer[lRead], sRxTerm)) yRead = true;
            if (sAnswer[lRead] == 0) yRead = true;
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
