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

#define SCOPELED_ILLUMINATOR_CHANNELS_MAX 3 //20140821 Jason modify 4=>3
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

	//================ 20140821 jason add =================================
	int OnChannelShutter(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterChannel1(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel2(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel3(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel4(MM::PropertyBase* pProp, MM::ActionType eAct);
	//===================================================================//

    // action interface
    int OnChannelIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel1(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel2(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel3(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel4(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    static const char* DeviceName;
    static const char* DeviceDescription;
	 int SetShutterChannel();  //20140821 Jason add
protected:
    void ClearOpticalState();
    int SetIntensity();
		
private:
    double intensity[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
	double ShutterChannel[SCOPELED_ILLUMINATOR_CHANNELS_MAX]; //20140821 Jason add
};

#define SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX 6
// #define SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC 1 // <-- For testing with uncalibrated devices. 
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
#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
    int OnChannelIntensityDAC(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
#else
    int OnChannelIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
#endif
	//================ 20140821 jason add =================================
	int OnChannelShutter(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterChannel1(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel2(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel3(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnShutterChannel4(MM::PropertyBase* pProp, MM::ActionType eAct);
	//===================================================================//

    int OnIntensityChannel1(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel2(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel3(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnIntensityChannel4(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnPresetModeIntensity(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode1Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode2Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode3Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode4Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode5Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnPresetMode6Intensity(MM::PropertyBase* pProp, MM::ActionType eAct);

    int OnActiveColor(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;
protected:
    void ClearOpticalState();

private:
	double ShutterRawChannel[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
#ifdef SCOPELED_ILLUMINATOR_MSM_INTENSITY_DAC
    long intensityRawChannelDAC[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
    int SetManualColorDAC();
#else
    double intensityRawChannel[SCOPELED_ILLUMINATOR_CHANNELS_MAX];
    int SetManualColor();
#endif
	int SetShutterChannel_Real();
    double intensityPresetMode[SCOPELED_ILLUMINATOR_MSM_PRESET_CHANNELS_MAX];
    long activePresetModeIndex;
	long activePresetShutterndex;
    static const char* const s_PresetModeStrings[];

    int PlayPresetMode();
};

#define NUM_FMI_CHANNELS 4
class ScopeLEDFluorescenceIlluminator : public ScopeLEDBasicIlluminator<ScopeLEDFluorescenceIlluminator>
{
    bool channel_wavelengths_initialized[NUM_FMI_CHANNELS];
    long channel_wavelengths[NUM_FMI_CHANNELS];
    long m_SettlingTime;

    long m_NumChannels;
    
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
    int OnSettlingTime(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;
    static const char* DeviceDescription;

protected:
    void ClearOpticalState();
    
private:

    int GetChannelWavelength(long channel, long& wavelength);
    
    int SetChannelIntensity(long channel, double intensity);
    int GetChannelIntensity(long channel, double& intensity);

    int SetActiveChannel(long channel);
    int GetActiveChannel(long& channel);
    int GetNumChannels(long& count);

    int SetSettlingTime(long time);
    int GetSettlingTime(long& time);

};

#endif
