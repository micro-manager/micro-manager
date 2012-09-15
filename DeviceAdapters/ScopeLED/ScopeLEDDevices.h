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

#ifndef ScopeLEDDevices_h
#define ScopeLEDDevices_h

#include "ScopeLEDBasicIlluminator.h"

#define SCOPELED_ILLUMINATOR_CHANNELS_MAX 4
class ScopeLEDMSBMicroscopeIlluminator : public ScopeLEDBasicIlluminator<ScopeLEDMSBMicroscopeIlluminator>
{
    bool m_state;
public:
    ScopeLEDMSBMicroscopeIlluminator(); 
    ~ScopeLEDMSBMicroscopeIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

    // Shutter API
    int SetOpen (bool open = true);
    int GetOpen(bool& open);

    // action interface
    int OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    static const char* DeviceName;
    static const char* DeviceDescription;
protected:
    void ClearOpticalState();
    
private:
    double brightness[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
};

class ScopeLEDMSMMicroscopeIlluminator : public ScopeLEDBasicIlluminator<ScopeLEDMSMMicroscopeIlluminator>
{    
public:
    ScopeLEDMSMMicroscopeIlluminator(); 
    ~ScopeLEDMSMMicroscopeIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

// Shutter API
    int SetOpen (bool open = true);
    int GetOpen(bool& open);

// action interface
    int OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnPresetModeBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode5Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode6Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;
protected:
    void ClearOpticalState();

private:
    double brightnessRawChannel[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
    double activePresetModeBrightness;
    int activePresetModeIndex;

    int SetColor(bool on);
    //int GetPresetMode(unsigned char mode);
    int PlayPresetMode(int mode, double brightness);
};

#define MAX_FMI_LED_GROUPS 9
class ScopeLEDFluorescenceIlluminator : public ScopeLEDBasicIlluminator<ScopeLEDFluorescenceIlluminator>
{
    bool led_group_channels_initialized[MAX_FMI_LED_GROUPS];
    long led_group_channels[MAX_FMI_LED_GROUPS];
    
    bool channel_wavelengths_initialized[4];
    long channel_wavelengths[4];
    
public:
    ScopeLEDFluorescenceIlluminator(); 
    ~ScopeLEDFluorescenceIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

    // Shutter API
    int SetOpen (bool open = true);
    int GetOpen(bool& open);

    // action interface
    int OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    int OnLEDGroup(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnLEDGroup1Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup2Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup3Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup4Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup5Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup6Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup7Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup8Channels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnLEDGroup9Channels(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnChannel1Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Wavelength(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnActiveChannelString(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnActiveWavelengthString(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnOptimalPositionString(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;

    long m_CachedLEDGroup;
    long m_NumChannels;

    std::string StatusDescription;

    struct
    {
        long led_group;
        std::string info;
    } m_ActiveWavelengths, m_ActiveChannels;

protected:
    void ClearOpticalState();
    
private:

    int GetChannelWavelength(int channel, long& wavelength);
    
    int SetChannelBrightness(int channel, double brightness);
    int GetChannelBrightness(int channel, double& brightness);

    int SetLEDGroup(long group);
    int GetLEDGroup(long& group);
    int GetNumChannels(long& count);

    int GetLEDGroupChannels(int group, long& channels);
    int OnLEDGroupChannels(int group, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannelWavelength(int index, MM::PropertyBase* pProp, MM::ActionType eAct);

    int UpdateActiveChannelString();
    int UpdateActiveWavelengthString();
    int GetOptimalPositionString(std::string& str);
};

#endif
