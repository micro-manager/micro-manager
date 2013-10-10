/*
ScopeLED Device Adapters for Micro-Manager. 
Copyright (C) 2011-2013 ScopeLED

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
#include <sstream>

#define round(x) ((int)(x+0.5))

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
    AddAvailableDeviceName(ScopeLEDMSBMicroscopeIlluminator::DeviceName, ScopeLEDMSBMicroscopeIlluminator::DeviceDescription);
    AddAvailableDeviceName(ScopeLEDMSMMicroscopeIlluminator::DeviceName, ScopeLEDMSMMicroscopeIlluminator::DeviceDescription);
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
    else if (strcmp(deviceName, ScopeLEDMSMMicroscopeIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new ScopeLEDMSMMicroscopeIlluminator();
    }
    else if (strcmp(deviceName, ScopeLEDMSBMicroscopeIlluminator::DeviceName) == 0)
    {
        if (g_USBCommAdapter.InitializeLibrary())
            return new ScopeLEDMSBMicroscopeIlluminator();
    }

    // ...supplied name not recognized
    return NULL;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
    delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// ScopeLEDMSBMicroscopeIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* ScopeLEDMSBMicroscopeIlluminator::DeviceName = "ScopeLED-G";
const char* ScopeLEDMSBMicroscopeIlluminator::DeviceDescription = "ScopeLED Fiber Guide Illuminator";

void ScopeLEDMSBMicroscopeIlluminator::ClearOpticalState()
{
    m_state = true;
    for (long i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        brightness[i] = 0.0;
    }
}

ScopeLEDMSBMicroscopeIlluminator::ScopeLEDMSBMicroscopeIlluminator()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::ScopeLEDMSBMicroscopeIlluminator ctor" << std::endl;
#endif
    ClearOpticalState();

    // Name
    CreateProperty(MM::g_Keyword_Name, DeviceName, MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, DeviceDescription, MM::String, true);

    CreateProperty("VendorID", STR(DICON_USB_VID_OLD), MM::String, false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    std::stringstream strm;
    strm << std::dec << 0x1303;
    CreateProperty("ProductID", strm.str().c_str(), MM::Integer, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnLastError);
    CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnLastDeviceResult);
    CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);

    //pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnTransactionTime);
    //nRet = CreateProperty("TxnTime", "0", MM::Integer, true, pAct);

    // state
    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnState);
    CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open

#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDMSBMicroscopeIlluminator::ScopeLEDMSBMicroscopeIlluminator ctor" << std::endl;
#endif
}

ScopeLEDMSBMicroscopeIlluminator::~ScopeLEDMSBMicroscopeIlluminator()
{

}

void ScopeLEDMSBMicroscopeIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, ScopeLEDMSBMicroscopeIlluminator::DeviceName);
}

int ScopeLEDMSBMicroscopeIlluminator::Initialize()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::Initialize" << std::endl;
#endif
    
    if (NULL != m_hDevice)
        return DEVICE_OK;

    long vid = 0;
    int nRet = GetCurrentPropertyData("VendorID", vid);
    if (nRet != DEVICE_OK) return nRet;

    long pid = 0;
    nRet = GetProperty("ProductID", pid);
    if (nRet != DEVICE_OK) return nRet;

    char serial[MM::MaxStrLength];
    memset(serial, '\0', sizeof(serial));
    nRet = GetProperty("InitSerialNumber", serial);
    if (nRet != DEVICE_OK) return nRet;
    bool no_serial = 0 == strlen(serial);

    if (no_serial)
    {
        m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)vid, (unsigned short)pid);
    }
    else
    {
        m_hDevice = g_USBCommAdapter.OpenDevice((unsigned short)vid, (unsigned short)pid, serial);
    }

    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    ClearOpticalState();
    InitSerialNumber();

    // set property list
    // -----------------

    std::stringstream strm;
    strm << std::dec << GetVersion();
    nRet = CreateProperty("Version", strm.str().c_str(), MM::Integer, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateProperty("SerialNumber", m_SerialNumber.c_str(), MM::String, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("Channel1Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel1Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("Channel2Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel2Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("Channel3Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel3Brightness", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("Channel4Brightness", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Channel4Brightness", 0.0, 100.0);
    
    nRet = UpdateStatus();

#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDMSBMicroscopeIlluminator::Initialize" << std::endl;
#endif
    return nRet;
}

int ScopeLEDMSBMicroscopeIlluminator::GetOpen(bool& open)
{
    open = m_state;
    return DEVICE_OK;
}

int ScopeLEDMSBMicroscopeIlluminator::SetIntensity()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::SetIntensity. Open=" << std::dec << m_state << "." << std::endl;
#endif
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
        for (long i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
        {
            cmdbuf[i+3] = (unsigned char) round((brightness[i] * (unsigned)0xFF) / 100);
        }
    }
    else
    {
        for (long i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
        {
            cmdbuf[i+3] = 0;
        }
    }

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    const int result = Transact(cmdbuf, sizeof(cmdbuf));
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::SetIntensity. Result=" << std::dec << result << "." << std::endl;
#endif
    return result;
}

int ScopeLEDMSBMicroscopeIlluminator::SetOpen (bool open)
{
    m_state = open;
    return SetIntensity();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ScopeLEDMSBMicroscopeIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(brightness[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightness[index]);
        SetIntensity();
    }
    return DEVICE_OK;
}

int ScopeLEDMSBMicroscopeIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}

///////////////////////////////////////////////////////////////////////////////
// ScopeLEDMSMMicroscopeIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* ScopeLEDMSMMicroscopeIlluminator::DeviceName = "ScopeLED-B";
const char* ScopeLEDMSMMicroscopeIlluminator::DeviceDescription = "ScopeLED Brightfield Microscope Illuminator";
const char* const ScopeLEDMSMMicroscopeIlluminator::s_PresetModeStrings[] =
{
    "Manual",
    "3000K",
    "4500K",
    "6500K",
    "Red",
    "Green",
    "Blue"
};

void ScopeLEDMSMMicroscopeIlluminator::ClearOpticalState()
{
    int i;
    for (i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        brightnessRawChannel[i] = 0.0;
    }
    for (i=0; i < SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX; i++)
    {
        brightnessPresetMode[i] = 0.0;
    }
    activePresetModeIndex = 0L;
}

ScopeLEDMSMMicroscopeIlluminator::ScopeLEDMSMMicroscopeIlluminator()
{
    ClearOpticalState();

    // Name
    CreateProperty(MM::g_Keyword_Name, DeviceName, MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, DeviceDescription, MM::String, true);

    CreateProperty("VendorID", STR(DICON_USB_VID_NEW), MM::String, false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    std::stringstream strm;
    strm << std::dec << 0x130A;
    CreateProperty("ProductID", strm.str().c_str(), MM::Integer, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnLastError);
    CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnLastDeviceResult);
    CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);
}

ScopeLEDMSMMicroscopeIlluminator::~ScopeLEDMSMMicroscopeIlluminator()
{

}

void ScopeLEDMSMMicroscopeIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, ScopeLEDMSMMicroscopeIlluminator::DeviceName);
}

int ScopeLEDMSMMicroscopeIlluminator::Initialize()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDMSMMicroscopeIlluminator::Initialize" << std::endl;
#endif
    
    if (NULL != m_hDevice)
        return DEVICE_OK;

    long vid = 0;
    int nRet = GetCurrentPropertyData("VendorID", vid);
    if (nRet != DEVICE_OK) return nRet;

    long pid = 0;
    nRet = GetProperty("ProductID", pid);
    if (nRet != DEVICE_OK) return nRet;

    char serial[MM::MaxStrLength];
    memset(serial, '\0', sizeof(serial));
    nRet = GetProperty("InitSerialNumber", serial);
    if (nRet != DEVICE_OK) return nRet;
    bool no_serial = 0 == strlen(serial);

    if (no_serial)
    {
        m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)vid, (unsigned short)pid);
    }
    else
    {
        m_hDevice = g_USBCommAdapter.OpenDevice((unsigned short)vid, (unsigned short)pid, serial);
    }

    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    ClearOpticalState();
    InitSerialNumber();

    // set property list
    // -----------------

    std::stringstream strm;
    strm << std::dec << GetVersion();
    nRet = CreateProperty("Version", strm.str().c_str(), MM::Integer, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateProperty("SerialNumber", m_SerialNumber.c_str(), MM::String, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnChannel1Brightness);
    nRet = CreateProperty("ManualBrightnessChannel1", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualBrightnessChannel1", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnChannel2Brightness);
    nRet = CreateProperty("ManualBrightnessChannel2", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualBrightnessChannel2", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnChannel3Brightness);
    nRet = CreateProperty("ManualBrightnessChannel3", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualBrightnessChannel3", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnChannel4Brightness);
    nRet = CreateProperty("ManualBrightnessChannel4", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualBrightnessChannel4", 0.0, 100.0);

    /*
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnActivePresetMode);
    nRet = CreateProperty("ActivePresetMode", "0", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ActivePresetMode", 0, SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX);
    */

    long color = 0;
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnActiveColor);
    nRet = CreateProperty("ActiveColor", s_PresetModeStrings[color], MM::String, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (color=0; color <= SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX; color++)
    {
        AddAllowedValue("ActiveColor", s_PresetModeStrings[color], color);
    }
    
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode1Brightness);
    nRet = CreateProperty("Brightness3000K", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Brightness3000K", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode2Brightness);
    nRet = CreateProperty("Brightness4500K", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Brightness4500K", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode3Brightness);
    nRet = CreateProperty("Brightness6500K", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Brightness6500K", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode4Brightness);
    nRet = CreateProperty("BrightnessRed", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("BrightnessRed", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode5Brightness);
    nRet = CreateProperty("BrightnessGreen", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("BrightnessGreen", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode6Brightness);
    nRet = CreateProperty("BrightnessBlue", "0.0", MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("BrightnessBlue", 0.0, 100.0);

    long mode;
    nRet = GetControlMode(mode);
    if (nRet != DEVICE_OK) return nRet;
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnControlMode);
    nRet = CreateProperty("ControlMode", s_ControModeStrings[mode], MM::String, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (mode=1; mode < MAX_CONTROL_MODE; mode++)
    {
        AddAllowedValue("ControlMode", s_ControModeStrings[mode], mode);
    }

    // state
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnState);
    CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open

    nRet = UpdateStatus();
    return nRet;
}

int ScopeLEDMSMMicroscopeIlluminator::SetOpen(bool open)
{
    return SetShutter(open);
}

int ScopeLEDMSMMicroscopeIlluminator::GetOpen(bool& open)
{
    return GetShutter(open);
}

int ScopeLEDMSMMicroscopeIlluminator::SetManualColor()
{
    unsigned char cmdbuf[9];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x06;  // Length Byte
    cmdbuf[2] = 0x01;  // Command Byte
    cmdbuf[8] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[7];
    unsigned char* const pStart = &cmdbuf[1];

    for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        cmdbuf[i+3] = (unsigned char) round((brightnessRawChannel[i] * (unsigned)0xFF) / 100);
    }

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);
    return result;
}

/*
int ScopeLEDMSMMicroscopeIlluminator::GetPresetMode(unsigned char mode)
{
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] = 23;    // Command Byte - Get Preset Mode
    cmdbuf[3] = mode;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);
    if ((DEVICE_OK == result) && (cbRxBuffer >= 8) && (0==RxBuffer[3]))
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "GetPresetMode " << (int) mode;
        for (int i=0; i < 4; i++)
        {
            m_LogFile << " " << (int) RxBuffer[i+4];
        }
        m_LogFile << std::endl;
#endif
    }
    else
    {
#ifdef SCOPELED_DEBUGLOG
        m_LogFile << "GetPresetMode Failed." << std::endl;
#endif
    }
    return result;
}
*/

int ScopeLEDMSMMicroscopeIlluminator::PlayPresetMode()
{
    unsigned char cmdbuf[7];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x04;  // Length Byte
    cmdbuf[2] = 24;    // Play Preset Mode
    cmdbuf[3] = (unsigned char) activePresetModeIndex;
    cmdbuf[4] = (unsigned char) round((brightnessPresetMode[activePresetModeIndex-1] * (unsigned)0xFF) / 100);
    cmdbuf[6] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[5];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    return Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ScopeLEDMSMMicroscopeIlluminator::OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(brightnessRawChannel[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightnessRawChannel[index]);
        if (0L == activePresetModeIndex)
        {
            SetManualColor();
        }
    }
    return DEVICE_OK;
}

int ScopeLEDMSMMicroscopeIlluminator::OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(0, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(1, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(2, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelBrightness(3, pProp, eAct);
}

int ScopeLEDMSMMicroscopeIlluminator::OnActiveColor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    int result = DEVICE_OK;
    if (eAct == MM::AfterSet)
    {
        std::string strColor;        
        if (pProp->Get(strColor))
        {
            /*pProp->GetData(strColor.c_str(), mode))*/
            result = GetPropertyData("ActiveColor", strColor.c_str(), activePresetModeIndex);
            if (DEVICE_OK == result)
            {
                if (0L == activePresetModeIndex)
                {
                    SetManualColor();
                }
                else
                {
                    PlayPresetMode();
                }
            }
        }
        else result = DEVICE_NO_PROPERTY_DATA;
    }
    return result;
}

int ScopeLEDMSMMicroscopeIlluminator::OnPresetModeBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(brightnessPresetMode[index-1]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(brightnessPresetMode[index-1]);
        if (index == activePresetModeIndex)
        {
            PlayPresetMode();
        }
    }
    return DEVICE_OK;
}

int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(1, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(2, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(3, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(4, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode5Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(5, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode6Brightness(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnPresetModeBrightness(6, pProp, eAct);
}


///////////////////////////////////////////////////////////////////////////////
// ScopeLEDFluorescenceIlluminator class implementation
///////////////////////////////////////////////////////////////////////////////

const char* ScopeLEDFluorescenceIlluminator::DeviceName = "ScopeLED-F";
const char* ScopeLEDFluorescenceIlluminator::DeviceDescription = "ScopeLED Fluorescence Microscope Illuminator";

void ScopeLEDFluorescenceIlluminator::ClearOpticalState()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDFluorescenceIlluminator::ClearOpticalState" << std::endl;
#endif
    m_NumChannels = 0;

    memset(channel_wavelengths_initialized, false, NUM_FMI_CHANNELS);
}

ScopeLEDFluorescenceIlluminator::ScopeLEDFluorescenceIlluminator()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::ctor" << std::endl;
#endif
    
    ClearOpticalState();

    // Name
    CreateProperty(MM::g_Keyword_Name, DeviceName, MM::String, true);

    // Description
    CreateProperty(MM::g_Keyword_Description, DeviceDescription, MM::String, true);

    CreateProperty("VendorID", STR(DICON_USB_VID_NEW), MM::String, false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    std::stringstream strm;
    strm << std::dec << 0x1305;
    CreateProperty("ProductID", strm.str().c_str(), MM::Integer, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastError);
    CreateProperty("LastError", "0", MM::Integer, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastDeviceResult);
    CreateProperty("LastDeviceResult", "0", MM::Integer, true, pAct);
    
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::ctor" << std::endl;
#endif
}

ScopeLEDFluorescenceIlluminator::~ScopeLEDFluorescenceIlluminator()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDFluorescenceIlluminator::dtor" << std::endl;
#endif
}

void ScopeLEDFluorescenceIlluminator::GetName(char* name) const
{
    CDeviceUtils::CopyLimitedString(name, ScopeLEDFluorescenceIlluminator::DeviceName);
}

int ScopeLEDFluorescenceIlluminator::Initialize()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::Initialize" << std::endl;
#endif
    
    if (NULL != m_hDevice)
        return DEVICE_OK;

    long vid = 0;
    int nRet = GetCurrentPropertyData("VendorID", vid);
    if (nRet != DEVICE_OK) return nRet;

    long pid = 0;
    nRet = GetProperty("ProductID", pid);
    if (nRet != DEVICE_OK) return nRet;

    char serial[MM::MaxStrLength];
    memset(serial, '\0', sizeof(serial));
    nRet = GetProperty("InitSerialNumber", serial);
    if (nRet != DEVICE_OK) return nRet;
    bool no_serial = 0 == strlen(serial);

    if (no_serial)
    {
        m_hDevice = g_USBCommAdapter.OpenFirstDevice((unsigned short)vid, (unsigned short)pid);
    }
    else
    {
        m_hDevice = g_USBCommAdapter.OpenDevice((unsigned short)vid, (unsigned short)pid, serial);
    }

    if (NULL == m_hDevice) return DEVICE_NOT_CONNECTED;

    ClearOpticalState();
    InitSerialNumber();

    nRet = GetNumChannels(m_NumChannels);
    if (nRet != DEVICE_OK) return nRet;

#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDFluorescenceIlluminator::Initialize> NumChannels = " << m_NumChannels << std::endl;
#endif

    // set property list
    // -----------------

    std::stringstream strm;
    strm << std::dec << GetVersion();
    nRet = CreateProperty("Version", strm.str().c_str(), MM::Integer, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateProperty("SerialNumber", m_SerialNumber.c_str(), MM::String, true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    long ActiveChannel;
    nRet = GetActiveChannel(ActiveChannel);
    if (nRet != DEVICE_OK) return nRet;

    long wavelength = 0;    
    strm.str("");
    if ((ActiveChannel > 0) && (ActiveChannel <= m_NumChannels))
    {
        nRet = GetChannelWavelength(ActiveChannel, wavelength);
        if (nRet != DEVICE_OK) return nRet;
        
        if (wavelength > 0)
        {
            strm << std::dec << wavelength << "nm";
        }
    }
    else strm << "None";
    
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnActiveWavelength);
    nRet = CreateProperty("ActiveWavelength", strm.str().c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (long channel=1; channel <= m_NumChannels; channel++)
    {
        if (DEVICE_OK == GetChannelWavelength(channel, wavelength))
        {
            strm.str("");
            strm << std::dec << wavelength << "nm";
            AddAllowedValue("ActiveWavelength", strm.str().c_str(), channel);
        }
    }

    double CurrentIntensity = 0.0;
    if ((ActiveChannel > 0) && (ActiveChannel <= m_NumChannels))
    {
        nRet = GetChannelBrightness(ActiveChannel, CurrentIntensity);
        if (nRet != DEVICE_OK) return nRet;
    }
    strm.str("");
    strm << std::fixed << std::setprecision(3) << CurrentIntensity;
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnIntensity);
    nRet = CreateProperty("Intensity", strm.str().c_str(), MM::Float, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Intensity", 0.0, 100.0);

    long mode;
    nRet = GetControlMode(mode);
    if (nRet != DEVICE_OK) return nRet;
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnControlMode);
    nRet = CreateProperty("ControlMode", s_ControModeStrings[mode], MM::String, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (mode=1; mode < MAX_CONTROL_MODE; mode++)
    {
        AddAllowedValue("ControlMode", s_ControModeStrings[mode], mode);
    }
        
    // state
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnState);
    nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open

    nRet = UpdateStatus();

#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::Initialize" << std::endl;
#endif
    
    return nRet;
}

int ScopeLEDFluorescenceIlluminator::SetOpen(bool open)
{
    return SetShutter(open);
}

int ScopeLEDFluorescenceIlluminator::GetOpen(bool& open)
{
    return GetShutter(open);
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

    if ((DEVICE_OK == result) && (cbRxBuffer == 7) && (0==RxBuffer[3]))
    {
        count = RxBuffer[4];
    }
    else
    {
        count = 0;
    }
    return result;
}


int ScopeLEDFluorescenceIlluminator::SetChannelBrightness(long channel, double brightness)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::SetChannelBrightness. Channel="
            << std::dec << channel
            << ". Intensity=" << std::fixed << std::setprecision(2) << brightness
            << std::endl;
#endif
    
    unsigned char cmdbuf[8];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x05;  // Length Byte
    cmdbuf[2] =   28;  // Command Byte - MSG_SET_BRIGHTNESS
    cmdbuf[3] = (unsigned char) channel;  // Channel
    cmdbuf[7] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[6];
    unsigned char* const pStart = &cmdbuf[1];

    unsigned short b = (unsigned short) round(brightness * 10.0);
    cmdbuf[4] = (b >> 8);
    cmdbuf[5] = (b & 0xFF);

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    const int result = Transact(cmdbuf, sizeof(cmdbuf));
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::SetChannelBrightness. Result=" << std::dec << result << "." << std::endl;
#endif
    return result;
}

int ScopeLEDFluorescenceIlluminator::GetChannelBrightness(long channel, double& brightness)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::GetChannelBrightness. Channel=" << std::dec << channel
            << "." << std::endl;
#endif
    
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   27;  // Command Byte - MSG_GET_BRIGHTNESS
    cmdbuf[3] = (unsigned char) channel;  // Channel
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer == 8) && (0==RxBuffer[3]))
    {
        unsigned short b = (RxBuffer[4] << 8) | RxBuffer[5];
        brightness = b / 10.0;
    }
    else
    {
        brightness = 0.0;
    }
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::GetChannelBrightness. Channel=" << std::dec << channel
            << ". Brightness=" << std::fixed << std::setprecision(2) << brightness
            << ". Result=" << std::dec << result << "." << std::endl;
#endif
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::OnIntensity" << std::endl;
#endif
    
    int result = DEVICE_OK;
    if (eAct == MM::AfterSet)
    {
        double intensity = 0.0;
        if (pProp->Get(intensity))
        {
#ifdef SCOPELED_DEBUGLOG
            m_LogFile << "ScopeLEDFluorescenceIlluminator::OnIntensity. Intensity=" << std::fixed << std::setprecision(2) << intensity << std::endl;
#endif
            long channel;
            result = GetCurrentPropertyData("ActiveWavelength", channel);
            if (DEVICE_OK == result)
            {
#ifdef SCOPELED_DEBUGLOG
                m_LogFile << "ScopeLEDFluorescenceIlluminator::OnIntensity. Channel=" << std::dec << channel << std::endl;
#endif
                result = SetChannelBrightness(channel, intensity);
            }
        }
        else result = DEVICE_NO_PROPERTY_DATA;
    }
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::OnIntensity. Result=" << std::dec << result << std::endl;
#endif
    return result;
}

int ScopeLEDFluorescenceIlluminator::SetActiveChannel(long channel)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDFluorescenceIlluminator::SetActiveChannel" << std::endl;
#endif
    
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x03;  // Length Byte
    cmdbuf[2] =   70;  // Command Byte - MSG_SET_LED_GROUP_STATE
    cmdbuf[3] = (unsigned char) channel;
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    int result = Transact(cmdbuf, sizeof(cmdbuf));

    if (DEVICE_OK == result)
    {
        long intensity;
        GetProperty("Intensity", intensity);
        result = SetChannelBrightness(channel, intensity);
    }

    return result;
}

int ScopeLEDFluorescenceIlluminator::GetActiveChannel(long& channel)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "ScopeLEDFluorescenceIlluminator::GetActiveChannel" << std::endl;
#endif
    
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

    if ((DEVICE_OK == result) && (cbRxBuffer == 7) && (0==RxBuffer[3]))
    {
        channel = RxBuffer[4];
    }
    else
    {
        channel = 0;
    }    
    return result;
}

int ScopeLEDFluorescenceIlluminator::OnActiveWavelength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::OnActiveWavelength" << std::endl;
#endif
    
    int result = DEVICE_OK;
    if (eAct == MM::AfterSet)
    {
        std::string strWavelength;        
        if (pProp->Get(strWavelength))
        {
            long channel;
            /*pProp->GetData(strWavelength.c_str(), channel))*/
            result = GetPropertyData("ActiveWavelength", strWavelength.c_str(), channel);
            if (DEVICE_OK == result)
            {
                result = SetActiveChannel(channel);
            }
        }
        else result = DEVICE_NO_PROPERTY_DATA;
    }
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::OnActiveWavelength. Result=" << std::dec << result << std::endl;
#endif
    return result;
}

int ScopeLEDFluorescenceIlluminator::GetChannelWavelength(long channel, long& wavelength)
{
    int result = DEVICE_OK;

#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::GetChannelWavelength" << std::endl;
#endif

    wavelength = 0;
    const long index = channel - 1;
    if ((index < 0) || (index >= m_NumChannels))
    {
        return DEVICE_INVALID_INPUT_PARAM;
    }
    if (!channel_wavelengths_initialized[index])
    {
        unsigned char cmdbuf[6];
        memset(cmdbuf, 0, sizeof(cmdbuf));
        cmdbuf[0] = 0xA9;  // Start Byte
        cmdbuf[1] = 0x03;  // Length Byte
        cmdbuf[2] =   30;  // Command Byte - MSG_GET_CHANNEL_WAVELENGTH
        cmdbuf[3] = (unsigned char) channel;  // Channel
        cmdbuf[5] = 0x5C;  // End Byte

        unsigned char* const pChecksum = &cmdbuf[4];
        unsigned char* const pStart = &cmdbuf[1];

        *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

        unsigned char RxBuffer[16];
        unsigned long cbRxBuffer = sizeof(RxBuffer);
        result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

        if ((DEVICE_OK == result) && (cbRxBuffer == 8) && (0==RxBuffer[3]))
        {
            channel_wavelengths[index] = (RxBuffer[4] << 8) | RxBuffer[5];
            channel_wavelengths_initialized[index] = true;
        }
        else
        {
            channel_wavelengths[index] = 0;
        }
    }
    wavelength = channel_wavelengths[index];
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::GetChannelWavelength index=" << index << ", wavelength=" << std::dec << wavelength << std::endl;
#endif
    return result;
}
