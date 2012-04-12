/*
ScopeLED Device Adapters for Micro-Manager. 
Copyright (C) 2011-2012 ScopeLED

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

#include "ScopeLEDDevices.h"
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
    AddAvailableDeviceName(ScopeLEDMicroscopeIlluminator::DeviceName, ScopeLEDMicroscopeIlluminator::DeviceDescription);
    AddAvailableDeviceName(ScopeLEDFluorescenceIlluminator::DeviceName, ScopeLEDFluorescenceIlluminator::DeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
    if (NULL == deviceName)
        return NULL;

    if (strcmp(deviceName, ScopeLEDFluorescenceIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new ScopeLEDFluorescenceIlluminator();
    }
    else if (strcmp(deviceName, ScopeLEDMicroscopeIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new ScopeLEDMicroscopeIlluminator();
    }

    // ...supplied name not recognized
    return NULL;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// ScopeLEDMicroscopeIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* ScopeLEDMicroscopeIlluminator::DeviceName = "ScopeLED-MSB/MSP";
const char* ScopeLEDMicroscopeIlluminator::DeviceDescription = "ScopeLED MSB/MSP Microscope Illuminator";

ScopeLEDMicroscopeIlluminator::ScopeLEDMicroscopeIlluminator() : m_state(false)
{
    m_pid = 0x1303;
    for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        brightness[i] = 0.0;
    }

    // Name
    CreateProperty(MM::g_Keyword_Name, DeviceName, MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, DeviceDescription, MM::String, true);
    
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnVendorID);
    CreateProperty("VendorID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("VendorID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnProductID);
    CreateProperty("ProductID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnLastError);
    CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnLastDeviceResult);
    CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);

    //pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnTransactionTime);
    //nRet = CreateProperty("TxnTime", "0", MM::Integer, true, pAct);

    // state
    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnState);
    CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open
}

ScopeLEDMicroscopeIlluminator::~ScopeLEDMicroscopeIlluminator()
{

}

void ScopeLEDMicroscopeIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, ScopeLEDMicroscopeIlluminator::DeviceName);
}

int ScopeLEDMicroscopeIlluminator::Initialize()
{
    if (NULL != m_hDevice)
        return DEVICE_OK;

    char serial[MM::MaxStrLength];
    memset(serial, '\0', sizeof(serial));
    GetProperty("SerialNumber", serial);
    bool no_serial = 0 == strlen(serial);

    if (no_serial)
    {
        m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)m_vid, (unsigned short)m_pid);
    }
    else
    {
        m_hDevice = g_USBCommAdapter.OpenDevice((unsigned short)m_vid, (unsigned short)m_pid, serial);
    }
    
    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    if (no_serial)
    {
        QuerySerialNumber();
    }

    // set property list
    // -----------------

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnVersion);
    int nRet = CreateProperty("Version", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("Channel1Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel1Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("Channel2Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel2Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("Channel3Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel3Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMicroscopeIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("Channel4Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel4Brightness", 0.0, 100.0);
    
    nRet = UpdateStatus();
    return nRet;
}

int ScopeLEDMicroscopeIlluminator::GetOpen(bool& open)
{
    open = m_state;
    return DEVICE_OK;
}

int ScopeLEDMicroscopeIlluminator::SetOpen (bool open)
{
    unsigned char cmdbuf[10];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x07;  // Length Byte
    cmdbuf[2] = 0x01;  // Command Byte
    cmdbuf[7] = 0x00;  // No blinking
    cmdbuf[9] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[8];
    unsigned char* const pStart = &cmdbuf[1];

    m_state = open;
    if (m_state)
    {
        for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
        {
            cmdbuf[i+3] = (unsigned char) ((int) ((brightness[i] * (unsigned)0xFF) / 100) + 0.5);
        }
    }
    else
    {
        for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
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

int ScopeLEDMicroscopeIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
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

int ScopeLEDMicroscopeIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}

int ScopeLEDMicroscopeIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}

int ScopeLEDMicroscopeIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}

int ScopeLEDMicroscopeIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}


///////////////////////////////////////////////////////////////////////////////
// ScopeLEDFluorescenceIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* ScopeLEDFluorescenceIlluminator::DeviceName = "ScopeLED-FMI";
const char* ScopeLEDFluorescenceIlluminator::DeviceDescription = "ScopeLED Fluorescence Microscope Illuminator";

ScopeLEDFluorescenceIlluminator::ScopeLEDFluorescenceIlluminator() : m_CachedLEDGroup(0), m_NumChannels(0)
{
    m_pid = 0x1305;

    // Name
    int nRet = CreateProperty(MM::g_Keyword_Name, DeviceName, MM::String, true);

    // Description
    nRet = CreateProperty(MM::g_Keyword_Description, DeviceDescription, MM::String, true);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnVendorID);
    CreateProperty("VendorID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("VendorID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnProductID);
    CreateProperty("ProductID", "0", MM::Integer, false, pAct, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastError);
    CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastDeviceResult);
    CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);

    //pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnTransactionTime);
    //CreateProperty("TxnTime", "0", MM::Integer, true, pAct);

    // state
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnState);
    CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open
}

ScopeLEDFluorescenceIlluminator::~ScopeLEDFluorescenceIlluminator()
{

}

void ScopeLEDFluorescenceIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, ScopeLEDFluorescenceIlluminator::DeviceName);
}

int ScopeLEDFluorescenceIlluminator::Initialize()
{
    if (NULL != m_hDevice)
        return DEVICE_OK;

    char serial[MM::MaxStrLength];
    memset(serial, '\0', sizeof(serial));
    GetProperty("SerialNumber", serial);
    bool no_serial = 0 == strlen(serial);

    if (no_serial)
    {
        m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)m_vid, (unsigned short)m_pid);
    }
    else
    {
        m_hDevice = g_USBCommAdapter.OpenDevice((unsigned short)m_vid, (unsigned short)m_pid, serial);
    }

    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    if (no_serial)
    {
        QuerySerialNumber();
    }
    
    memset(led_group_channels_initialized, false, MAX_FMI_LED_GROUPS);
    memset(channel_wavelengths_initialized, false, 4);

    m_CachedLEDGroup = 0;
    m_NumChannels = 0;

    int nRet = GetNumChannels(m_NumChannels);
    if (nRet != DEVICE_OK) return nRet;

    // set property list
    // -----------------
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnVersion);
    nRet = CreateProperty("Version", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("Channel1Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel1Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("Channel2Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel2Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("Channel3Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel3Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("Channel4Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel4Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup);
    nRet = CreateProperty("LEDGroup", "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("LEDGroup", 1, MAX_FMI_LED_GROUPS);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup1Channels);
    nRet = CreateProperty("LEDGroup1Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup2Channels);
    nRet = CreateProperty("LEDGroup2Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup3Channels);
    nRet = CreateProperty("LEDGroup3Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup4Channels);
    nRet = CreateProperty("LEDGroup4Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup5Channels);
    nRet = CreateProperty("LEDGroup5Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup6Channels);
    nRet = CreateProperty("LEDGroup6Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup7Channels);
    nRet = CreateProperty("LEDGroup7Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup8Channels);
    nRet = CreateProperty("LEDGroup8Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLEDGroup9Channels);
    nRet = CreateProperty("LEDGroup9Channels", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel1Wavelength);
    nRet = CreateProperty("Channel1Wavelength", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel2Wavelength);
    nRet = CreateProperty("Channel2Wavelength", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel3Wavelength);
    nRet = CreateProperty("Channel3Wavelength", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnChannel4Wavelength);
    nRet = CreateProperty("Channel4Wavelength", "0", MM::Integer, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnControlMode);
    nRet = CreateProperty("ControlMode", "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ControlMode", 1, 3);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnActiveChannelString);
    nRet = CreateProperty("ActiveChannels", "", MM::String, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnActiveWavelengthString);
    nRet = CreateProperty("ActiveWavelengths", "", MM::String, true, pAct);
    if (nRet != DEVICE_OK) return nRet;

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnOptimalPositionString);
    nRet = CreateProperty("OptimalStagePosition", "", MM::String, true, pAct);
    if (nRet != DEVICE_OK) return nRet;    

    nRet = UpdateStatus();
    return nRet;
}

int ScopeLEDFluorescenceIlluminator::SetOpen(bool open)
{
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   41;  // Command Byte - MSG_SET_MASTER_ENABLE
    cmdbuf[3] = open;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
    return Transact(cmdbuf, sizeof(cmdbuf));
}

int ScopeLEDFluorescenceIlluminator::GetOpen(bool& open)
{
    unsigned char cmdbuf[5];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x02;  // Length Byte
    cmdbuf[2] =   42;  // Command Byte - MSG_GET_MASTER_ENABLE
    cmdbuf[4] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[3];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
    {
        open = 0 != RxBuffer[4];
    }
    else
    {
        open = false;
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::GetNumChannels(long& count)
{
    unsigned char cmdbuf[5];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x02;  // Length Byte
    cmdbuf[2] = 31;    // Command Byte - MSG_GET_CHANNEL_COUNT
    cmdbuf[4] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[3];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
    {
        count = RxBuffer[4];
    }
    else
    {
        count = 0;
    }
    return result;
}


int ScopeLEDFluorescenceIlluminator::SetChannelBrightness(int channel, double brightness)
{
    unsigned char cmdbuf[8];
    memset(cmdbuf, 0, sizeof(cmdbuf));
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

int ScopeLEDFluorescenceIlluminator::GetChannelBrightness(int channel, double& brightness)
{
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
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
    else
    {
        brightness = 0.0;
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    double brightness = 0.0;
    int result = DEVICE_OK;
    if (eAct == MM::BeforeGet)
    {
        result = GetChannelBrightness(index, brightness);
        if (DEVICE_OK == result)
        {
            pProp->Set(brightness);
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightness);
        result = SetChannelBrightness(index, brightness);
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::SetLEDGroup(long group)
{
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   70;  // Command Byte - MSG_SET_LED_GROUP_STATE
    cmdbuf[3] = (unsigned char) group;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    int result = Transact(cmdbuf, sizeof(cmdbuf));

    if (DEVICE_OK == result)
    {
        m_CachedLEDGroup = group;
    }

    return result;
}

int ScopeLEDFluorescenceIlluminator::GetLEDGroup(long& group)
{
    unsigned char cmdbuf[5];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x02;  // Length Byte
    cmdbuf[2] =   71;  // Command Byte - MSG_GET_LED_GROUP_STATE
    cmdbuf[4] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[3];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
    {
        group = RxBuffer[4];
        m_CachedLEDGroup = group;
    }
    else
    {
        group = 0;
    }    
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnLEDGroup(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long group = 0;
    int result = DEVICE_OK;
    if (eAct == MM::BeforeGet)
    {
        result = GetLEDGroup(group);
        if (DEVICE_OK == result)
        {
            pProp->Set(group);
        }
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(group);
        result = SetLEDGroup(group);
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::GetLEDGroupChannels(int group, long& channels)
{
   int result = DEVICE_OK;
    
    if (!led_group_channels_initialized[group-1])
    {
        unsigned char cmdbuf[6];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x03;  // Length Byte
        cmdbuf[2] =   72;  // Command Byte - MSG_GET_LED_GROUP_STATE_CHANNELS
        cmdbuf[3] = (unsigned char) group;
        cmdbuf[5] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[4];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

        unsigned char RxBuffer[16];
        unsigned long cbRxBuffer = sizeof(RxBuffer);
        int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

        if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
        {
            led_group_channels[group-1] = RxBuffer[4];
            led_group_channels_initialized[group-1] = true;
        }
        else
        {
            led_group_channels[group-1] = 0;
        }
    }
    channels = led_group_channels[group-1];
    
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnLEDGroupChannels(int group, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    int result = DEVICE_CAN_NOT_SET_PROPERTY;
    if (eAct == MM::BeforeGet)
    {
        long channels = 0;
        result = GetLEDGroupChannels(group, channels);
        if (DEVICE_OK == result)
        {
            pProp->Set(channels);
        }
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnLEDGroup1Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(1, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup2Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(2, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup3Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(3, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup4Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(4, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup5Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(5, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup6Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(6, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup7Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(7, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup8Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(8, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnLEDGroup9Channels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnLEDGroupChannels(9, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::GetChannelWavelength(int channel, long& wavelength)
{
    int result = DEVICE_OK;

    if (!channel_wavelengths_initialized[channel])
    {
        unsigned char cmdbuf[6];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x03;  // Length Byte
        cmdbuf[2] =   30;  // Command Byte - MSG_GET_CHANNEL_WAVELENGTH
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
            channel_wavelengths[channel] = (RxBuffer[4] << 8) | RxBuffer[5];
        }
        else
        {
            channel_wavelengths[channel] = 0;
        }
    }
    wavelength = channel_wavelengths[channel];
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnChannelWavelength(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long wavelength = 0;
    int result = DEVICE_CAN_NOT_SET_PROPERTY;
    if (eAct == MM::BeforeGet)
    {
        result = GetChannelWavelength(index, wavelength);
        if (DEVICE_OK == result)
        {
            pProp->Set(wavelength);
        }
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnChannel1Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelWavelength(0, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnChannel2Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelWavelength(1, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnChannel3Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelWavelength(2, pProp, eAct);
}
int ScopeLEDFluorescenceIlluminator::OnChannel4Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelWavelength(3, pProp, eAct);
}

int ScopeLEDFluorescenceIlluminator::SetControlMode(long mode)
{
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   39;  // Command Byte - MSG_SET_OPERATING_MODE
    cmdbuf[3] = (unsigned char) mode;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);
    return Transact(cmdbuf, sizeof(cmdbuf));
}

int ScopeLEDFluorescenceIlluminator::GetControlMode(long& mode)
{
    unsigned char cmdbuf[5];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x02;  // Length Byte
    cmdbuf[2] =   38;  // Command Byte - MSG_GET_OPERATING_MODE
    cmdbuf[4] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[3];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer >= 5))
    {
        mode = RxBuffer[4];
    }
    else
    {
        mode = 0;
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnControlMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long mode = 0;
    if (eAct == MM::BeforeGet)
    {
        GetControlMode(mode);
        pProp->Set(mode);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(mode);
        SetControlMode(mode);
    }
    return DEVICE_OK;
}

int ScopeLEDFluorescenceIlluminator::UpdateActiveChannelString()
{
    int result = DEVICE_OK;
    bool ok = m_CachedLEDGroup > 0;

    if (!ok)
    {
        long group = 0;
        result = GetLEDGroup(group);
    }

    ok = DEVICE_OK == result;
    if (ok)
    {
        ok = m_CachedLEDGroup > 0;
        if (ok)
        {
            if (m_ActiveChannels.led_group != m_CachedLEDGroup)
            {
                std::stringstream strm;
                long channels = 0;
                result = GetLEDGroupChannels(m_CachedLEDGroup, channels);
                ok = DEVICE_OK == result;
                if (ok)
                {
                    bool first = true;
                    int i=1;
                    while (channels)
                    {
                        if (channels & 0x1)
                        {
                            if (!first)
                            {
                                strm << ",";
                            }
                            strm << "Ch" << i;
                            first = false;
                        }
                        channels = channels >> 1;
                        i++;
                    }
                    m_ActiveChannels.led_group = m_CachedLEDGroup;
                    m_ActiveChannels.info = strm.str();
                }
            }
        }
        else
        {
            result = DEVICE_INTERNAL_INCONSISTENCY;
        }
    }

    if (!ok)
    {
        m_ActiveChannels.led_group = 0;
        m_ActiveChannels.info.clear();
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnActiveChannelString(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long mode = 0;
    if (eAct == MM::BeforeGet)
    {
        int result = UpdateActiveChannelString();
        if (DEVICE_OK == result)
        {
            pProp->Set(m_ActiveChannels.info.c_str());
        }
        return result;                       
    }                                           
    return DEVICE_CAN_NOT_SET_PROPERTY;
}

int ScopeLEDFluorescenceIlluminator::UpdateActiveWavelengthString()
{
    int result = DEVICE_OK;
    bool ok = m_CachedLEDGroup > 0;

    if (!ok)
    {
        long group = 0;
        result = GetLEDGroup(group);
    }

    ok = DEVICE_OK == result;
    if (ok)
    {
        ok = m_CachedLEDGroup > 0;
        if (ok)
        {
            if (m_ActiveWavelengths.led_group != m_CachedLEDGroup)
            {
                std::stringstream strm;
                long channels = 0;
                result = GetLEDGroupChannels(m_CachedLEDGroup, channels);
                ok = DEVICE_OK == result;
                if (ok)
                {
                    bool first = true;
                    int i=1;
                    while (channels)
                    {
                        if (channels & 0x1)
                        {
                            long wavelength = 0;
                            result = GetChannelWavelength(i-1, wavelength);
                            if ((DEVICE_OK == result) && (wavelength > 0))
                            {
                                if (!first)
                                {
                                    strm << ",";
                                }                        
                                strm << wavelength << "nm";
                                first = false;
                            }
                        }
                        channels = channels >> 1;
                        i++;
                    }
                    m_ActiveWavelengths.led_group = m_CachedLEDGroup;
                    m_ActiveWavelengths.info = strm.str();
                }
            }
        }
        else
        {
            result = DEVICE_INTERNAL_INCONSISTENCY;
        }
    }

    if (!ok)
    {
        m_ActiveWavelengths.led_group = 0;
        m_ActiveWavelengths.info.clear();
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnActiveWavelengthString(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long mode = 0;
    if (eAct == MM::BeforeGet)
    {
        int result = UpdateActiveWavelengthString();
        if (DEVICE_OK == result)
        {
            pProp->Set(m_ActiveWavelengths.info.c_str());
        }
        return result;                       
    }                                           
    return DEVICE_CAN_NOT_SET_PROPERTY;
}


int ScopeLEDFluorescenceIlluminator::GetOptimalPositionString(std::string& str)
{
    static const char* OptimalPositions_4WL[] =
    {
        "X1,Y1",
        "X3,Y1",
        "X3,Y3",
        "X1,Y3",
        "X2,Y1",
        "X3,Y2",
        "X2,Y3",
        "X1,Y2",
        "X2,Y2"
    };

    static const char* OptimalPositions_2WL[] =
    {
        "X1",
        "X3",
        "X2"
    };
    
    int result = DEVICE_OK;
    bool ok = m_CachedLEDGroup > 0;

    if (!ok)
    {
        long group = 0;
        result = GetLEDGroup(group);
    }

    str.clear();

    ok = DEVICE_OK == result;
    if (ok)
    {
        ok = m_CachedLEDGroup > 0;
        if (ok)
        {
            switch (m_NumChannels)
            {
            case 1:
                // There is no position adjustment necessary.
                break;
            case 2:
                if (m_CachedLEDGroup <= 3)
                {
                    str = OptimalPositions_2WL[m_CachedLEDGroup-1];
                }
                break;
            case 4:
                if (m_CachedLEDGroup <= 9)
                {
                    str = OptimalPositions_4WL[m_CachedLEDGroup-1];
                }
                break;
            default:
                // Device is probably not configured. Just leave this property blank.
                break;
            }
        }
        else
        {
            result = DEVICE_INTERNAL_INCONSISTENCY;
        }
    }
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnOptimalPositionString(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    long mode = 0;
    if (eAct == MM::BeforeGet)
    {
        std::string str;
        int result = GetOptimalPositionString(str);
        if (DEVICE_OK == result)
        {
            pProp->Set(str.c_str());
        }
        return result;                       
    }                                           
    return DEVICE_CAN_NOT_SET_PROPERTY;
}
