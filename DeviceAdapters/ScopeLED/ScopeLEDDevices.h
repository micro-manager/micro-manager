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
    int SetIntensity();
    
private:
    double brightness[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
};

#define SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX 6
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

    int OnActiveColor(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;
protected:
    void ClearOpticalState();

private:
    double brightnessRawChannel[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
    double brightnessPresetMode[SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX];
    long activePresetModeIndex;
    static const char* const s_PresetModeStrings[];

    int SetManualColor();
    int PlayPresetMode();
};

#define NUM_FMI_CHANNELS 4
class ScopeLEDFluorescenceIlluminator : public ScopeLEDBasicIlluminator<ScopeLEDFluorescenceIlluminator>
{
    bool channel_wavelengths_initialized[NUM_FMI_CHANNELS];
    long channel_wavelengths[NUM_FMI_CHANNELS];
    
public:
    ScopeLEDFluorescenceIlluminator(); 
    ~ScopeLEDFluorescenceIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

    // Shutter API
    int SetOpen (bool open = true);
    int GetOpen(bool& open);

    // action interface
    int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    int OnActiveWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;

    long m_NumChannels;

protected:
    void ClearOpticalState();
    
private:

    int GetChannelWavelength(long channel, long& wavelength);
    
    int SetChannelBrightness(long channel, double brightness);
    int GetChannelBrightness(long channel, double& brightness);

    int SetActiveChannel(long channel);
    int GetActiveChannel(long& channel);
    int GetNumChannels(long& count);

};

#endif
