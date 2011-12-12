/*
DiCon Illuminator Device Adapter for Micro-Manager. 
Copyright (C) 2011 DiCon Lighting, Inc

This adapter is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This software is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this software.  If not, see
<http://www.gnu.org/licenses/>.

*/

#include "DiConIlluminator.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"

USBCommAdapter g_USBCommAdapter;

// windows DLL entry code
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                       DWORD  ul_reason_for_call, 
                       LPVOID /*lpReserved*/
                     )
{
    switch (ul_reason_for_call)
    {
        case DLL_PROCESS_ATTACH:
            break;
        case DLL_PROCESS_DETACH:
            break;
        case DLL_THREAD_ATTACH:
        case DLL_THREAD_DETACH:
            break;
    }
    return TRUE;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
    AddAvailableDeviceName(DiConBrightfieldIlluminator::DeviceName, "DiCon Brightfield Microscopy Illuminator");
    AddAvailableDeviceName(DiConFluorescenceIlluminator::DeviceName, "DiCon Fluorescence Microscopy Illuminator");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (NULL == deviceName)
        return NULL;

    if (strcmp(deviceName, DiConFluorescenceIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new DiConFluorescenceIlluminator();
    }
    else if (strcmp(deviceName, DiConBrightfieldIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new DiConBrightfieldIlluminator();
    }

    // ...supplied name not recognized
    return NULL;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// DiConBrightfieldIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* DiConBrightfieldIlluminator::DeviceName = "DiConBrightfieldIlluminator";

DiConBrightfieldIlluminator::DiConBrightfieldIlluminator()
{
    m_pid = 0x1303;
    for (int i=0; i < DICON_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        brightness[i] = 0.0;
    }
    
    CPropertyAction* pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnVendorID);
    int nRet = CreateProperty("VendorID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("VendorID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnProductID);
    nRet = CreateProperty("ProductID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnLastError);
    nRet = CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnLastDeviceResult);
    nRet = CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnTransactionTime);
    nRet = CreateProperty("TxnTime", "0", MM::Integer, true, pAct);

    // state
    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnState);
    nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open
}

DiConBrightfieldIlluminator::~DiConBrightfieldIlluminator()
{

}

void DiConBrightfieldIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, DiConBrightfieldIlluminator::DeviceName);
}

int DiConBrightfieldIlluminator::Initialize()
{
    if (NULL != m_hDevice)
        return DEVICE_OK;

    m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)m_vid, (unsigned short)m_pid);
    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    // set property list
    // -----------------

    // Name
    int nRet = CreateProperty(MM::g_Keyword_Name, DiConBrightfieldIlluminator::DeviceName, MM::String, true);
    if (nRet != DEVICE_OK) return nRet;

    // Description
    nRet = CreateProperty(MM::g_Keyword_Description, "DiCon LED Brightfield Illuminator", MM::String, true);
    if (nRet != DEVICE_OK) return nRet;

    CPropertyAction* pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("Channel1Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel1Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("Channel2Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel2Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("Channel3Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel3Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConBrightfieldIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("Channel4Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel4Brightness", 0.0, 100.0);
    
    nRet = UpdateStatus();
    if (nRet != DEVICE_OK) return nRet;

    return DEVICE_OK;
}

int DiConBrightfieldIlluminator::SetOpen (bool open)
{
    m_state = open;
    unsigned char cmdbuf[10];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x07;  // Length Byte
    cmdbuf[2] = 0x01;  // Command Byte
    cmdbuf[7] = 0x00;  // No blinking
    cmdbuf[9] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[8];
    unsigned char* const pStart = &cmdbuf[1];

    if (m_state)
    {
        for (int i=0; i < DICON_ILLUMINATOR_CHANNELS_MAX; i++)
        {
            cmdbuf[i+3] = (unsigned char) ((int) ((brightness[i] * (unsigned)0xFF) / 100) + 0.5);
        }
    }
    else
    {
        for (int i=0; i < DICON_ILLUMINATOR_CHANNELS_MAX; i++)
        {
            cmdbuf[i+3] = 0;
        }
    }

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
    
    return Transact(cmdbuf, sizeof(cmdbuf));
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DiConBrightfieldIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(brightness[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightness[index]);
    }
    return DEVICE_OK;
}

int DiConBrightfieldIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}

int DiConBrightfieldIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}

int DiConBrightfieldIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}

int DiConBrightfieldIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}


///////////////////////////////////////////////////////////////////////////////
// DiConFluorescenceIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* DiConFluorescenceIlluminator::DeviceName = "DiConFluorescenceIlluminator";

DiConFluorescenceIlluminator::DiConFluorescenceIlluminator()
{
    m_pid = 0x1305;

    CPropertyAction* pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnVendorID);
    int nRet = CreateProperty("VendorID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("VendorID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnProductID);
    nRet = CreateProperty("ProductID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnLastError);
    nRet = CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnLastDeviceResult);
    nRet = CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnTransactionTime);
    nRet = CreateProperty("TxnTime", "0", MM::Integer, true, pAct);

    // state
    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnState);
    nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open
}

DiConFluorescenceIlluminator::~DiConFluorescenceIlluminator()
{

}

void DiConFluorescenceIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, DiConFluorescenceIlluminator::DeviceName);
}

int DiConFluorescenceIlluminator::Initialize()
{
    if (NULL != m_hDevice)
        return DEVICE_OK;

    m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)m_vid, (unsigned short)m_pid);
    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    // set property list
    // -----------------

    // Name
    int nRet = CreateProperty(MM::g_Keyword_Name, DiConFluorescenceIlluminator::DeviceName, MM::String, true);
    if (nRet != DEVICE_OK) return nRet;

    // Description
    nRet = CreateProperty(MM::g_Keyword_Description, "DiCon LED Fluorescence Illuminator", MM::String, true);
    if (nRet != DEVICE_OK) return nRet;

    CPropertyAction* pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("Channel1Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel1Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("Channel2Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel2Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("Channel3Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel3Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &DiConFluorescenceIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("Channel4Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel4Brightness", 0.0, 100.0);

    nRet = UpdateStatus();
    if (nRet != DEVICE_OK) return nRet;

    return DEVICE_OK;
}

int DiConFluorescenceIlluminator::SetOpen (bool open)
{
    m_state = open;
    unsigned char cmdbuf[6];
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   41;  // Command Byte - MSG_SET_MASTER_ENABLE
    cmdbuf[3] = m_state;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
    return Transact(cmdbuf, sizeof(cmdbuf));
}

int DiConFluorescenceIlluminator::SetBrightness(int channel, double brightness)
{
    unsigned char cmdbuf[8];
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x05;  // Length Byte
    cmdbuf[2] =   28;  // Command Byte - MSG_SET_BRIGHTNESS
    cmdbuf[3] = (unsigned char) (channel+1);  // Channel
    cmdbuf[7] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[6];
    unsigned char* const pStart = &cmdbuf[1];

    unsigned short b = (unsigned short) ((brightness * 10.0) + 0.5); // round
    cmdbuf[4] = (b >> 8);
    cmdbuf[5] = (b & 0xFF);

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    return Transact(cmdbuf, sizeof(cmdbuf));
}

int DiConFluorescenceIlluminator::GetBrightness(int channel, double& brightness)
{
    brightness = 0.0;
    
    unsigned char cmdbuf[6];
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   27;  // Command Byte - MSG_GET_BRIGHTNESS
    cmdbuf[3] = (unsigned char) (channel+1);  // Channel
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];    

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer >= 6))
    {
        unsigned short b = (RxBuffer[4] << 8) | RxBuffer[5];
        brightness = b / 10.0;
    }
    return result;
}

int DiConFluorescenceIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    double brightness = 0.0;
    int result = DEVICE_OK;
    if (eAct == MM::BeforeGet)
    {
        result = GetBrightness(index, brightness);
        if (DEVICE_OK == result)
        {
            pProp->Set(brightness);
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightness);
        result = SetBrightness(index, brightness);
    }
    return result;
}

int DiConFluorescenceIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}

int DiConFluorescenceIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}

int DiConFluorescenceIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}

int DiConFluorescenceIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}



