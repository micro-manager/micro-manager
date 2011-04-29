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

#ifndef DiConIlluminator_h
#define DiConIlluminator_h

#include "DiConBasicIlluminator.h"

#define DICON_ILLUMINATOR_CHANNELS_MAX 4
class DiConBrightfieldIlluminator : public DiConBasicIlluminator<DiConBrightfieldIlluminator>
{
public:
    DiConBrightfieldIlluminator(); 
    ~DiConBrightfieldIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

    // Shutter API
    int SetOpen (bool open = true);

    // action interface
    int OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    
    static const char* DeviceName;

private:
    double brightness[DICON_ILLUMINATOR_CHANNELS_MAX];
};

class DiConFluorescenceIlluminator : public DiConBasicIlluminator<DiConFluorescenceIlluminator>
{
public:
    DiConFluorescenceIlluminator(); 
    ~DiConFluorescenceIlluminator();

    int Initialize();

    void GetName (char* pszName) const;

    // Shutter API
    int SetOpen (bool open = true);

    // action interface
    int OnChannelBrightness(int index, MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel1Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel2Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel3Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnChannel4Brightness(MM::PropertyBase* pProp, MM::ActionType eAct);

    static const char* DeviceName;

private:

    int SetBrightness(int channel, double brightness);
    int GetBrightness(int channel, double& brightness); 

};

#endif
