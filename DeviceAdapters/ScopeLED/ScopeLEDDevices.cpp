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
#include <sstream>

#define round(x) ((int)(x+0.5))

USBCommAdapter g_USBCommAdapter;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
    RegisterDevice(ScopeLEDMSBMicroscopeIlluminator::DeviceName, MM::ShutterDevice, ScopeLEDMSBMicroscopeIlluminator::DeviceDescription);
    RegisterDevice(ScopeLEDMSMMicroscopeIlluminator::DeviceName, MM::ShutterDevice, ScopeLEDMSMMicroscopeIlluminator::DeviceDescription);
    RegisterDevice(ScopeLEDFluorescenceIlluminator::DeviceName, MM::ShutterDevice, ScopeLEDFluorescenceIlluminator::DeviceDescription);
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
        intensity[i] = 0.0;
    }
}

ScopeLEDMSBMicroscopeIlluminator::ScopeLEDMSBMicroscopeIlluminator()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::ScopeLEDMSBMicroscopeIlluminator ctor" << std::endl;
#endif
    ClearOpticalState();

    // Name
    CreateStringProperty(MM::g_Keyword_Name, DeviceName, true);

    // Description
    CreateStringProperty(MM::g_Keyword_Description, DeviceDescription, true);

    CreateStringProperty("VendorID", STR(DICON_USB_VID_OLD), false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    CreateIntegerProperty("ProductID", 0x1303, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnLastError);
    CreateIntegerProperty("LastError", 0, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnLastDeviceResult);
    CreateIntegerProperty("LastDeviceResult", 0, true, pAct);

    //pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnTransactionTime);
    //nRet = CreateIntegerProperty("TxnTime", 0, true, pAct);

    // state
    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnState);
    CreateIntegerProperty(MM::g_Keyword_State, 1, false, pAct);
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
	GetVersion();
    nRet = CreateStringProperty("Version",J_version.c_str(), true, NULL); //20150203 Jason modify  CreateIntegerProperty => CreateStringProperty
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateStringProperty("SerialNumber", m_SerialNumber.c_str(), true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel1);
    nRet = CreateFloatProperty("IntensityChannel1", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("IntensityChannel1", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel2);
    nRet = CreateFloatProperty("IntensityChannel2", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("IntensityChannel2", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel3);
    nRet = CreateFloatProperty("IntensityChannel3", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("IntensityChannel3", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel4);
    nRet = CreateFloatProperty("IntensityChannel4", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("IntensityChannel4", 0.0, 100.0);
    
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
            cmdbuf[i+3] = (unsigned char) round((intensity[i] * (unsigned)0xFF) / 100);
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

//==================== 20140821 Jason add =================================================================
int ScopeLEDMSBMicroscopeIlluminator::SetShutterChannel()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::SetShutterChannel. Open=" << std::dec << m_state << "." << std::endl;
#endif
    unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 3;  // Length Byte
    cmdbuf[2] = 85;  // Command Byte
    cmdbuf[3] = 0;  // No blinking
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];

    //if (m_state)
    //{
    //    for (long i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    //    {
    //        cmdbuf[i+3] = (unsigned char) round((ShutterChannel[i] * (unsigned)0xFF) / 100);
    //    }
    //}
    //else
    //{
    //    for (long i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    //    {
    //        cmdbuf[i+3] = 0;
    //    }
    //}

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    const int result = Transact(cmdbuf, sizeof(cmdbuf));
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDMSBMicroscopeIlluminator::SetShutterChannel. Result=" << std::dec << result << "." << std::endl;
#endif
    return result;
}
//===============================================================================================

int ScopeLEDMSBMicroscopeIlluminator::SetOpen (bool open)
{
    m_state = open;
    return SetIntensity();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
//=======================  20140821 Jason add ===========================================================
int ScopeLEDMSBMicroscopeIlluminator::OnChannelShutter(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(ShutterChannel[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(ShutterChannel[index]);
        SetShutterChannel();
    }
    return DEVICE_OK;
}

int ScopeLEDMSBMicroscopeIlluminator::OnShutterChannel1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelShutter(0, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnShutterChannel2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelShutter(1, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnShutterChannel3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelShutter(2, pProp, eAct);
}

//=========================================================================================================

int ScopeLEDMSBMicroscopeIlluminator::OnChannelIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(intensity[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(intensity[index]);
        SetIntensity();
    }
    return DEVICE_OK;
}

int ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelIntensity(0, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelIntensity(1, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelIntensity(2, pProp, eAct);
}
int ScopeLEDMSBMicroscopeIlluminator::OnIntensityChannel4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return OnChannelIntensity(3, pProp, eAct);
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
    "4300K",
    "5600K",
    "Red",
    "Green",
    "Blue"
}; // Jason 20150203 modify 3000,4500,6500 => 3000,4300,5600
void ScopeLEDMSMMicroscopeIlluminator::ClearOpticalState()
{
    int i;
    for (i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
        intensityRawChannelDAC[i] = 0;
#else
        intensityRawChannel[i] = 0.0;
		ShutterRawChannel[i] = 0.0;
#endif
    }
    for (i=0; i < SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX; i++)
    {
        intensityPresetMode[i] = 0.0;
    }
    activePresetModeIndex = 0L;
}

ScopeLEDMSMMicroscopeIlluminator::ScopeLEDMSMMicroscopeIlluminator()
{
    ClearOpticalState();

    // Name
    CreateStringProperty(MM::g_Keyword_Name, DeviceName, true);

    // Description
    CreateStringProperty(MM::g_Keyword_Description, DeviceDescription, true);

    CreateStringProperty("VendorID", STR(DICON_USB_VID_NEW), false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    CreateIntegerProperty("ProductID", 0x130A, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnLastError);
    CreateIntegerProperty("LastError", 0, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnLastDeviceResult);
    CreateIntegerProperty("LastDeviceResult", 0, true, pAct);
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
	
	GetVersion();
    nRet = CreateStringProperty("Version",J_version.c_str(), true, NULL); //20150203 Jason modify nRet = CreateIntegerProperty("Version", GetVersion(), true, NULL); => nRet = CreateStringProperty("Version", GetVersion.c_str(), true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateStringProperty("SerialNumber", m_SerialNumber.c_str(), true, NULL);
    if (nRet != DEVICE_OK) return nRet;

#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel1);
    nRet = CreateIntegerProperty("ManualIntensityChannel1", 0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel1", 0, 4095);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel2);
    nRet = CreateIntegerProperty("ManualIntensityChannel2", 0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel2", 0, 4095);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel3);
    nRet = CreateIntegerProperty("ManualIntensityChannel3", 0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel3", 0, 4095);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel4);
    nRet = CreateIntegerProperty("ManualIntensityChannel4", 0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel4", 0, 4095);
#else
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel1);
    nRet = CreateFloatProperty("ManualIntensityChannel1", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel1", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel2);
    nRet = CreateFloatProperty("ManualIntensityChannel2", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel2", 0.0, 100.0);

    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel3);
    nRet = CreateFloatProperty("ManualIntensityChannel3", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ManualIntensityChannel3", 0.0, 100.0);

	//20140910 Jason modify 
    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel4);
    //nRet = CreateFloatProperty("ManualIntensityChannel4", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("ManualIntensityChannel4", 0.0, 100.0);
#endif

    /*
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnActivePresetMode);
    nRet = CreateIntegerProperty("ActivePresetMode", 0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("ActivePresetMode", 0, SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX);
    */

    long color = 0;
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnActiveColor);
    nRet = CreateStringProperty("ActiveColor", s_PresetModeStrings[color], false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (color=0; color <= SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX; color++)
    {
        AddAllowedValue("ActiveColor", s_PresetModeStrings[color], color);
    }
    
	pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode1Intensity);
    nRet = CreateFloatProperty("Intensity", 0.0, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Intensity", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode1Intensity);
    //nRet = CreateFloatProperty("Intensity3000K", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("Intensity3000K", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode2Intensity);
    //nRet = CreateFloatProperty("Intensity4500K", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("Intensity4500K", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode3Intensity);
    //nRet = CreateFloatProperty("Intensity6500K", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("Intensity6500K", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode4Intensity);
    //nRet = CreateFloatProperty("IntensityRed", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("IntensityRed", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode5Intensity);
    //nRet = CreateFloatProperty("IntensityGreen", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("IntensityGreen", 0.0, 100.0);

    //pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnPresetMode6Intensity);
    //nRet = CreateFloatProperty("IntensityBlue", 0.0, false, pAct);
    //if (nRet != DEVICE_OK) return nRet;
    //SetPropertyLimits("IntensityBlue", 0.0, 100.0);

    long mode;
    nRet = GetControlMode(mode);
    if (nRet != DEVICE_OK) return nRet;
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnControlMode);
	nRet = CreateStringProperty("ControlMode", b_ControModeStrings[mode], false, pAct); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
    if (nRet != DEVICE_OK) return nRet;
    for (mode=0; mode < MAX_CONTROL_MODE; mode++) //20140910 Jason modify  mode=1 => mode=0
    {
        AddAllowedValue("ControlMode", b_ControModeStrings[mode], mode); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
    }

	//================ 20150203 Jason add
 //   pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnControlMode_b);
	//nRet = CreateStringProperty("ControlMode-Intensity", b_ControModeStrings_Intensity[mode], false, pAct); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
 //   if (nRet != DEVICE_OK) return nRet;
 //   for (mode=0; mode < MAX_CONTROL_MODE_b; mode++) //20140910 Jason modify  mode=1 => mode=0
 //   {
 //       AddAllowedValue("ControlMode-Intensity", b_ControModeStrings_Intensity[mode], mode); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
 //   }

 //   pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnControlMode);
	//nRet = CreateStringProperty("ControlMode-Shutter", b_ControModeStrings_Shutter[mode], false, pAct); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
 //   if (nRet != DEVICE_OK) return nRet;
 //   for (mode=0; mode < MAX_CONTROL_MODE_b; mode++) //20140910 Jason modify  mode=1 => mode=0
 //   {
 //       AddAllowedValue("ControlMode-Shutter", b_ControModeStrings_Shutter[mode], mode); //20140910 Jason modify  s_ControModeStrings => b_ControModeStrings
 //   }
	//================
    // state
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnState);
    CreateIntegerProperty(MM::g_Keyword_State, 0, false, pAct);
    AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
    AddAllowedValue(MM::g_Keyword_State, "1"); // Open

	// ================ShutterChannel 20140821 Jason add========================================
    pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel1);
    nRet = CreateIntegerProperty("ShutterChannel1", 0, false, pAct);
	 if (nRet != DEVICE_OK) return nRet;
    //AddAllowedValue("ShutterChannel1", "0"); // Closed
    //AddAllowedValue("ShutterChannel1", "1"); // Open
	SetPropertyLimits("ShutterChannel1", 0, 1);

	pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel2);
    nRet = CreateIntegerProperty("ShutterChannel2", 0, false, pAct);
	 if (nRet != DEVICE_OK) return nRet;
    //AddAllowedValue("ShutterChannel2", "0"); // Closed
    //AddAllowedValue("ShutterChannel2", "1"); // Open
	SetPropertyLimits("ShutterChannel2", 0, 1);

	pAct = new CPropertyAction (this, &ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel3);
    nRet = CreateIntegerProperty("ShutterChannel3", 0, false, pAct);
	 if (nRet != DEVICE_OK) return nRet;
    //AddAllowedValue("ShutterChannel3", "0"); // Closed
    //AddAllowedValue("ShutterChannel3", "1"); // Open
	SetPropertyLimits("ShutterChannel3", 0, 1);
	// ===============================================================================
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



#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
int ScopeLEDMSMMicroscopeIlluminator::SetManualColorDAC()
{
    unsigned char cmdbuf[13];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x0A;  // Length Byte
    cmdbuf[2] =   73;  // Command Byte
    cmdbuf[12] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[11];
    unsigned char* const pStart = &cmdbuf[1];

    for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    {
        cmdbuf[(2*i)+3] = (unsigned char) (intensityRawChannelDAC[i] >> 8);
        cmdbuf[(2*i)+4] = (unsigned char) (intensityRawChannelDAC[i] & 0xFF);
    }

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);
    return result;
}
#else
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
        cmdbuf[i+3] = (unsigned char) round((intensityRawChannel[i] * (unsigned)0xFF) / 100);
    }

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);
    return result;
}

#endif
//20140821 Jason add
int ScopeLEDMSMMicroscopeIlluminator::SetShutterChannel_Real()
{
	unsigned char cmdbuf[6];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 3;  // Length Byte
    cmdbuf[2] = 85;  // Command Byte
    cmdbuf[3] = 0;  // No blinking
    cmdbuf[5] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[4];
    unsigned char* const pStart = &cmdbuf[1];
	cmdbuf[3] = (unsigned char) (ShutterRawChannel[2] * 4+ ShutterRawChannel[1] *2+ShutterRawChannel[0] );
    // for (int i=0; i < SCOPELED_ILLUMINATOR_CHANNELS_MAX; i++)
    //{
    //    cmdbuf[i+3] = (unsigned char) round((ShutterRawChannel[i] * (unsigned)0xFF) / 100);
    //}

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    const int result = Transact(cmdbuf, sizeof(cmdbuf));
    return result;
}

int ScopeLEDMSMMicroscopeIlluminator::PlayPresetMode()
{
    unsigned char cmdbuf[7];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x04;  // Length Byte
    cmdbuf[2] = 24;    // Play Preset Mode
    cmdbuf[3] = (unsigned char) activePresetModeIndex;
    cmdbuf[4] = (unsigned char) round((intensityPresetMode[activePresetModeIndex-1] * (unsigned)0xFF) / 100);
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

#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
int ScopeLEDMSMMicroscopeIlluminator::OnChannelIntensityDAC(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(intensityRawChannelDAC[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(intensityRawChannelDAC[index]);
        if (0L == activePresetModeIndex)
        {
            SetManualColorDAC();
        }
    }
    return DEVICE_OK;
}
#else
int ScopeLEDMSMMicroscopeIlluminator::OnChannelIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(intensityRawChannel[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(intensityRawChannel[index]);
        if (0L == activePresetModeIndex)
        {
            SetManualColor();
        }
    }
    return DEVICE_OK;
}
#endif

#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
#define SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND OnChannelIntensityDAC
#else
#define SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND OnChannelIntensity
#endif

int ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND(0, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND(1, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND(2, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnIntensityChannel4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_INTENSITY_COMMAND(3, pProp, eAct);
}

//================================ 20140821 Jason add ================================================
int ScopeLEDMSMMicroscopeIlluminator::OnChannelShutter(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(ShutterRawChannel[index]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(ShutterRawChannel[index]);
        SetShutterChannel_Real();
    }  
    return DEVICE_OK;
}


#define SCOPELED_ILLUMINATOR_MSM_SHUTTER_COMMAND OnChannelShutter
int ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_SHUTTER_COMMAND(0, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_SHUTTER_COMMAND(1, pProp, eAct);
}
int ScopeLEDMSMMicroscopeIlluminator::OnShutterChannel3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    return SCOPELED_ILLUMINATOR_MSM_SHUTTER_COMMAND(2, pProp, eAct);
}
//======================================================================================================

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
#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
                    SetManualColorDAC();
#else
                    SetManualColor();
#endif
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

int ScopeLEDMSMMicroscopeIlluminator::OnPresetModeIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(intensityPresetMode[index-1]);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(intensityPresetMode[index-1]);
        if (index == activePresetModeIndex)
        {
            PlayPresetMode();
        }
    }
    return DEVICE_OK;
}

int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode1Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnPresetModeIntensity(activePresetModeIndex, pProp, eAct);  //20150203 Jason modify 1 => activePresetModeIndex
    //return OnPresetModeIntensity(1, pProp, eAct);
}
//int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode2Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//    return OnPresetModeIntensity(2, pProp, eAct);
//}
//int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode3Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//    return OnPresetModeIntensity(3, pProp, eAct);
//}
//int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode4Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//    return OnPresetModeIntensity(4, pProp, eAct);
//}
//int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode5Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//    return OnPresetModeIntensity(5, pProp, eAct);
//}
//int ScopeLEDMSMMicroscopeIlluminator::OnPresetMode6Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//    return OnPresetModeIntensity(6, pProp, eAct);
//}


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
    m_SettlingTime = 0;

    memset(channel_wavelengths_initialized, false, NUM_FMI_CHANNELS);
}

ScopeLEDFluorescenceIlluminator::ScopeLEDFluorescenceIlluminator()
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::ctor" << std::endl;
#endif
    
    ClearOpticalState();

    // Name
    CreateStringProperty(MM::g_Keyword_Name, DeviceName, true);

    // Description
    CreateStringProperty(MM::g_Keyword_Description, DeviceDescription, true);

    CreateStringProperty("VendorID", STR(DICON_USB_VID_NEW), false, NULL, true);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_NEW), DICON_USB_VID_NEW);
    AddAllowedValue("VendorID", STR(DICON_USB_VID_OLD), DICON_USB_VID_OLD);

    CreateIntegerProperty("ProductID", 0x1305, false, NULL, true);
    SetPropertyLimits("ProductID", 0x0, 0xFFFF);

    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastError);
    CreateIntegerProperty("LastError", 0, true, pAct);

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnLastDeviceResult);
    CreateIntegerProperty("LastDeviceResult", 0, true, pAct);
    
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
	GetVersion();
    nRet = CreateStringProperty("Version",J_version.c_str(), true, NULL); // 20150203 Jason modify nRet = CreateIntegerProperty("Version", GetVersion(), true, NULL); => CreateStringProperty
    if (nRet != DEVICE_OK) return nRet;

    nRet = CreateStringProperty("SerialNumber", m_SerialNumber.c_str(), true, NULL);
    if (nRet != DEVICE_OK) return nRet;

    long ActiveChannel;
    nRet = GetActiveChannel(ActiveChannel);
    if (nRet != DEVICE_OK) return nRet;

    static const char* StrNoActiveWL = "None";
    long wavelength = 0;    
    std::stringstream strm;
    if ((ActiveChannel > 0) && (ActiveChannel <= m_NumChannels))
    {
        nRet = GetChannelWavelength(ActiveChannel, wavelength);
        if (nRet != DEVICE_OK) return nRet;
        
        if (wavelength > 0)
        {
            strm << std::dec << wavelength << "nm";
        }
    }
    else strm << StrNoActiveWL;
    
    CPropertyAction* pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnActiveWavelength);
    nRet = CreateStringProperty("ActiveWavelength", strm.str().c_str(), false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    AddAllowedValue("ActiveWavelength", StrNoActiveWL, 0);
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
        nRet = GetChannelIntensity(ActiveChannel, CurrentIntensity);
        if (nRet != DEVICE_OK) return nRet;
    }

    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnIntensity);
    nRet = CreateFloatProperty("Intensity", CurrentIntensity, false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    SetPropertyLimits("Intensity", 0.0, 100.0);

    long mode;
    nRet = GetControlMode(mode);
    if (nRet != DEVICE_OK) return nRet;
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnControlMode);
    nRet = CreateStringProperty("ControlMode", s_ControModeStrings[mode], false, pAct);
    if (nRet != DEVICE_OK) return nRet;
    for (mode=1; mode < MAX_CONTROL_MODE; mode++)
    {
        AddAllowedValue("ControlMode", s_ControModeStrings[mode], mode);
    }

    nRet = GetSettlingTime(m_SettlingTime);
    if (nRet == DEVICE_OK)
    {
        pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnSettlingTime);
        nRet = CreateIntegerProperty("SettlingTime", m_SettlingTime, false, pAct);
        if (nRet != DEVICE_OK) return nRet;
        SetPropertyLimits("SettlingTime", 0, 30000);
    }
        
    // state
    pAct = new CPropertyAction (this, &ScopeLEDFluorescenceIlluminator::OnState);
    nRet = CreateIntegerProperty(MM::g_Keyword_State, 0, false, pAct);
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


int ScopeLEDFluorescenceIlluminator::SetChannelIntensity(long channel, double intensity)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::SetChannelIntensity. Channel="
            << std::dec << channel
            << ". Intensity=" << std::fixed << std::setprecision(2) << intensity
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

    unsigned short b = (unsigned short) round(intensity * 10.0);
    cmdbuf[4] = (b >> 8);
    cmdbuf[5] = (b & 0xFF);

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    const int result = Transact(cmdbuf, sizeof(cmdbuf));
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::SetChannelIntensity. Result=" << std::dec << result << "." << std::endl;
#endif
    return result;
}

int ScopeLEDFluorescenceIlluminator::GetChannelIntensity(long channel, double& intensity)
{
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "+ScopeLEDFluorescenceIlluminator::GetChannelIntensity. Channel=" << std::dec << channel
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
        intensity = b / 10.0;
    }
    else
    {
        intensity = 0.0;
    }
#ifdef SCOPELED_DEBUGLOG
    m_LogFile << "-ScopeLEDFluorescenceIlluminator::GetChannelIntensity. Channel=" << std::dec << channel
            << ". Intensity=" << std::fixed << std::setprecision(2) << intensity
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
                result = SetChannelIntensity(channel, intensity);
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
        result = SetChannelIntensity(channel, intensity);
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

int ScopeLEDFluorescenceIlluminator::OnSettlingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set(m_SettlingTime);
    }
    else if (eAct == MM::AfterSet)
    {
        pProp->Get(m_SettlingTime);
        SetSettlingTime(m_SettlingTime);
    }
    return DEVICE_OK;
}

int ScopeLEDFluorescenceIlluminator::SetSettlingTime(long time)
{
    unsigned char cmdbuf[7];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x04;  // Length Byte
    cmdbuf[2] =   79;  // Command Byte - MSG_SAVE_SETTLING_TIME
    cmdbuf[3] = ((time >> 8) & 0xFF);
    cmdbuf[4] = (time & 0xFF);
    cmdbuf[6] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[5];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    return Transact(cmdbuf, sizeof(cmdbuf));
}

int ScopeLEDFluorescenceIlluminator::GetSettlingTime(long& time)
{
    unsigned char cmdbuf[5];
    memset(cmdbuf, 0, sizeof(cmdbuf));
    cmdbuf[0] = 0xA9;  // Start Byte
    cmdbuf[1] = 0x02;  // Length Byte
    cmdbuf[2] =   80;  // Command Byte - MSG_GET_SETTLING_TIME
    cmdbuf[4] = 0x5C;  // End Byte

    unsigned char* const pChecksum = &cmdbuf[3];
    unsigned char* const pStart = &cmdbuf[1];

    *pChecksum = g_USBCommAdapter.CalculateChecksum(pStart, *pStart);

    unsigned char RxBuffer[16];
    unsigned long cbRxBuffer = sizeof(RxBuffer);
    int result = Transact(cmdbuf, sizeof(cmdbuf), RxBuffer, &cbRxBuffer);

    if ((DEVICE_OK == result) && (cbRxBuffer == 8) && (0==RxBuffer[3]))
    {
        time = (RxBuffer[4] << 8) | RxBuffer[5];
    }
    else
    {
        time = 0;
    }
    return result;
}
