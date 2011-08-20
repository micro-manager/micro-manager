///////////////////////////////////////////////////////////////////////////////
// FILE:       CARVII.cpp
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BD/CrEST CARVII adapter
//                                                                                     
// AUTHOR:        G. Esteban Fernandez, g.esteban.fernandez@gmail.com, 08/19/2011
//                Based on CSU22 and LeicaDMR adapters by Nico Stuurman.
//
// COPYRIGHT:     2011, Children's Hospital Los Angeles
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "CARVII.h"
#include "CARVIIHub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_CARVIIHub = "CARVII Hub";
const char* g_CARVIIShutter = "CARVII Shutter";
const char* g_CARVIIExFilter = "CARVII Ex filter";
const char* g_CARVIIEmFilter = "CARVII Em filter";
const char* g_CARVIIDichroicFilter = "CARVII Dichroic";
const char* g_CARVIIFRAPIris = "CARVII FRAP iris";
const char* g_CARVIIIntensityIris = "CARVII Intensity iris";
const char* g_CARVIIDiskSlider = "CARVII Disk slider";
const char* g_CARVIISpinMotor = "CARVII Spin motor";
const char* g_CARVIIPrismSlider = "CARVII Prism slider";
const char* g_CARVIITouchScreen = "CARVII Touchscreen";

using namespace std;

CARVIIHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData() {
    AddAvailableDeviceName(g_CARVIIHub, "Hub (Needed for CARVII)");
    AddAvailableDeviceName(g_CARVIIShutter, "Confocal shutter");
    AddAvailableDeviceName(g_CARVIIExFilter, "Confocal excitation filter wheel");
    AddAvailableDeviceName(g_CARVIIEmFilter, "Confocal emission filter wheel");
    AddAvailableDeviceName(g_CARVIIDichroicFilter, "Confocal dichroic wheel");
    AddAvailableDeviceName(g_CARVIIFRAPIris, "Confocal field aperture");
    AddAvailableDeviceName(g_CARVIIIntensityIris, "Confocal \"Condenser\" illumination aperture");
    AddAvailableDeviceName(g_CARVIIDiskSlider, "Spinning disk slider");
    AddAvailableDeviceName(g_CARVIISpinMotor, "Spinning disk motor");
    AddAvailableDeviceName(g_CARVIIPrismSlider, "Light path slider");
    AddAvailableDeviceName(g_CARVIITouchScreen, "Touchscreen lockout control");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName) {
    if (deviceName == 0)
        return 0;

    if (strcmp(deviceName, g_CARVIIHub) == 0) {
        return new Hub();
    } else if (strcmp(deviceName, g_CARVIIShutter) == 0) {
        return new Shutter();
    } else if (strcmp(deviceName, g_CARVIIExFilter) == 0) {
        return new ExFilter();
    } else if (strcmp(deviceName, g_CARVIIEmFilter) == 0) {
        return new EmFilter();
    } else if (strcmp(deviceName, g_CARVIIDichroicFilter) == 0) {
        return new Dichroic();
    } else if (strcmp(deviceName, g_CARVIIFRAPIris) == 0) {
        return new FRAPIris();
    } else if (strcmp(deviceName, g_CARVIIIntensityIris) == 0) {
        return new IntensityIris();
    } else if (strcmp(deviceName, g_CARVIIDiskSlider) == 0) {
        return new DiskSlider();
    } else if (strcmp(deviceName, g_CARVIISpinMotor) == 0) {
        return new SpinMotor();
    } else if (strcmp(deviceName, g_CARVIIPrismSlider) == 0) {
        return new PrismSlider();
    } else if (strcmp(deviceName, g_CARVIITouchScreen) == 0) {
        return new TouchScreen();
    }
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice) {

    delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CARVII Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
initialized_(false),
port_("Undefined") {

    InitializeDefaultErrorMessages();

    // custom error messages
    SetErrorText(ERR_COMMAND_CANNOT_EXECUTE, "Command cannot be executed");

    // create pre-initialization properties
    // ------------------------------------

    // Port
    CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
    CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Hub::~Hub() {

    Shutdown();
}

void Hub::GetName(char* name) const {
    //assert(name_.length() < CDeviceUtils::GetMaxStringLength());   

    CDeviceUtils::CopyLimitedString(name, g_CARVIIHub);
}

bool Hub::Busy() {

    return false;
}

int Hub::Initialize() {
    // set property list
    // -----------------

    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, g_CARVIIHub, MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII hub", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    ret = g_hub.LockoutTouchscreen(*this, *GetCoreCallback());
    if (ret != DEVICE_OK)
        return ret;

    ret = g_hub.StartSpinningDisk(*this, *GetCoreCallback());
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

int Hub::Shutdown() {

    if (initialized_) {
        int ret = g_hub.ActivateTouchscreen(*this, *GetCoreCallback());
        if (ret != DEVICE_OK)

            return ret;

        initialized_ = false;
    }

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        pProp->Set(port_.c_str());
    } else if (eAct == MM::AfterSet) {
        if (initialized_) {
            // revert

            pProp->Set(port_.c_str());
            //return ERR_PORT_CHANGE_FORBIDDEN;
        }

        pProp->Get(port_);
        g_hub.SetPort(port_.c_str());
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Shutter
///////////////////////////////////////////////////////////////////////////////

Shutter::Shutter() :
initialized_(false),
name_(g_CARVIIShutter),
state_(0) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

Shutter::~Shutter() {

    Shutdown();
}

void Shutter::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Shutter", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Check current state of shutter:
    ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), state_);
    if (DEVICE_OK != ret)
        return ret;
    // State
    CPropertyAction * pAct = new CPropertyAction(this, &Shutter::OnState);
    if (state_ == 0)
        ret = CreateProperty(MM::g_Keyword_State, "Closed", MM::String, false, pAct);
    else
        ret = CreateProperty(MM::g_Keyword_State, "Open", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    AddAllowedValue(MM::g_Keyword_State, "Closed");
    AddAllowedValue(MM::g_Keyword_State, "Open");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool Shutter::Busy() {
    // Who knows?

    return false;
}

int Shutter::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

int Shutter::SetOpen(bool open) {
    if (open) {
        int ret = g_hub.SetShutterPosition(*this, *GetCoreCallback(), 1);
        if (ret != DEVICE_OK)
            return ret;
        state_ = 1;
    } else {
        int ret = g_hub.SetShutterPosition(*this, *GetCoreCallback(), 0);
        if (ret != DEVICE_OK)

            return ret;
        state_ = 0;
    }
    return DEVICE_OK;
}

int Shutter::GetOpen(bool &open) {

    // Check current state of shutter: (this might not be necessary, since we cash ourselves)
    int ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), state_);
    if (DEVICE_OK != ret)
        return ret;
    if (state_ == 1)
        open = true;
    else
        open = false;

    return DEVICE_OK;
}

int Shutter::Fire(double /*deltaT*/) {

    return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        if (state_ == 1)
            pProp->Set("Open");
        else
            pProp->Set("Closed");
    } else if (eAct == MM::AfterSet) {
        std::string state;
        pProp->Get(state);
        if (state == "Open") {
            pProp->Set("Open");
            return this->SetOpen(true);
        } else {
            pProp->Set("Closed");

            return this->SetOpen(false);
        }
    }
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CARVII Excitation Filters
///////////////////////////////////////////////////////////////////////////////

ExFilter::ExFilter() :
initialized_(false),
numPos_(8),
pos_(0),
name_(g_CARVIIExFilter) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

ExFilter::~ExFilter() {

    Shutdown();
}

void ExFilter::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ExFilter::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Ex filter", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &ExFilter::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");
    AddAllowedValue(MM::g_Keyword_State, "2");
    AddAllowedValue(MM::g_Keyword_State, "3");
    AddAllowedValue(MM::g_Keyword_State, "4");
    AddAllowedValue(MM::g_Keyword_State, "5");
    AddAllowedValue(MM::g_Keyword_State, "6");
    AddAllowedValue(MM::g_Keyword_State, "7");


    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "ExFilter-0");
    SetPositionLabel(1, "ExFilter-1");
    SetPositionLabel(2, "ExFilter-2");
    SetPositionLabel(3, "ExFilter-3");
    SetPositionLabel(4, "ExFilter-4");
    SetPositionLabel(5, "ExFilter-5");
    SetPositionLabel(6, "ExFilter-6");
    SetPositionLabel(7, "ExFilter-7");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool ExFilter::Busy() {
    // Who knows?

    return false;
}

int ExFilter::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ExFilter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 7)
            pos = 7;
        int ret = g_hub.SetExFilterPosition(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CARVII Emission Filters
///////////////////////////////////////////////////////////////////////////////

EmFilter::EmFilter() :
initialized_(false),
numPos_(8),
pos_(0),
name_(g_CARVIIEmFilter) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

EmFilter::~EmFilter() {

    Shutdown();
}

void EmFilter::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int EmFilter::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Em Filter", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &EmFilter::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");
    AddAllowedValue(MM::g_Keyword_State, "2");
    AddAllowedValue(MM::g_Keyword_State, "3");
    AddAllowedValue(MM::g_Keyword_State, "4");
    AddAllowedValue(MM::g_Keyword_State, "5");
    AddAllowedValue(MM::g_Keyword_State, "6");
    AddAllowedValue(MM::g_Keyword_State, "7");

    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "EmFilter-0");
    SetPositionLabel(1, "EmFilter-1");
    SetPositionLabel(2, "EmFilter-2");
    SetPositionLabel(3, "EmFilter-3");
    SetPositionLabel(4, "EmFilter-4");
    SetPositionLabel(5, "EmFilter-5");
    SetPositionLabel(6, "EmFilter-6");
    SetPositionLabel(7, "EmFilter-7");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool EmFilter::Busy() {
    // Who knows?

    return false;
}

int EmFilter::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int EmFilter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 7)
            pos = 7;
        int ret = g_hub.SetEmFilterPosition(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CARVII Dichroic Filters
///////////////////////////////////////////////////////////////////////////////

Dichroic::Dichroic() :
initialized_(false),
numPos_(5),
pos_(0),
name_(g_CARVIIDichroicFilter) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

Dichroic::~Dichroic() {

    Shutdown();
}

void Dichroic::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Dichroic::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Dichroic filter", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &Dichroic::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");
    AddAllowedValue(MM::g_Keyword_State, "2");
    AddAllowedValue(MM::g_Keyword_State, "3");
    AddAllowedValue(MM::g_Keyword_State, "4");

    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "Dichroic-0");
    SetPositionLabel(1, "Dichroic-1");
    SetPositionLabel(2, "Dichroic-2");
    SetPositionLabel(3, "Dichroic-3");
    SetPositionLabel(4, "Dichroic-4");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool Dichroic::Busy() {
    // Who knows?

    return false;
}

int Dichroic::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Dichroic::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 4)
            pos = 4;
        int ret = g_hub.SetDichroicPosition(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII FRAP (Field) iris
///////////////////////////////////////////////////////////////////////////////

FRAPIris::FRAPIris() :
initialized_(false),
name_(g_CARVIIFRAPIris) {
}

FRAPIris::~FRAPIris() {

    Shutdown();
}

void FRAPIris::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int FRAPIris::Initialize() {
    int ret;

    // Name
    ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII FRAP (field) iris", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Check current position of FRAP iris
    ret = g_hub.GetFRAPIrisPosition(*this, *GetCoreCallback(), pos_);
    if (DEVICE_OK != ret)
        return ret;
    // The following seemed a good idea, but ends up being annoying
    /*
    if (intensity_ > 0)
       open_ = true;
     */

    // Position
    CPropertyAction * pAct = new CPropertyAction(this, &FRAPIris::OnPosition);
    CreateProperty("Position", "1050", MM::Integer, false, pAct);
    SetPropertyLimits("Position", 450, 1050);

    EnableDelay();

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool FRAPIris::Busy() {

    return false;
}

int FRAPIris::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int FRAPIris::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        int ret = g_hub.GetFRAPIrisPosition(*this, *GetCoreCallback(), pos_);
        if (ret != DEVICE_OK)
            return ret;
        pProp->Set((long) pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        pos_ = (int) pos;
        int ret = g_hub.SetFRAPIrisPosition(*this, *GetCoreCallback(), pos_);
        if (ret != DEVICE_OK)

            return ret;
    }

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Intensity iris
///////////////////////////////////////////////////////////////////////////////

IntensityIris::IntensityIris() :
initialized_(false),
name_(g_CARVIIIntensityIris) {
}

IntensityIris::~IntensityIris() {

    Shutdown();
}

void IntensityIris::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int IntensityIris::Initialize() {
    int ret;

    // Name
    ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Intensity iris", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Check current position of Intensity iris
    ret = g_hub.GetIntensityIrisPosition(*this, *GetCoreCallback(), pos_);
    if (DEVICE_OK != ret)
        return ret;
    // The following seemed a good idea, but ends up being annoying
    /*
    if (intensity_ > 0)
       open_ = true;
     */

    // Position
    CPropertyAction * pAct = new CPropertyAction(this, &IntensityIris::OnPosition);
    CreateProperty("Position", "1050", MM::Integer, false, pAct);
    SetPropertyLimits("Position", 450, 1050);

    EnableDelay();

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool IntensityIris::Busy() {

    return false;
}

int IntensityIris::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int IntensityIris::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        int ret = g_hub.GetIntensityIrisPosition(*this, *GetCoreCallback(), pos_);
        if (ret != DEVICE_OK)
            return ret;
        pProp->Set((long) pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        pos_ = (int) pos;
        int ret = g_hub.SetIntensityIrisPosition(*this, *GetCoreCallback(), pos_);
        if (ret != DEVICE_OK)

            return ret;
    }

    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Disk Slider
///////////////////////////////////////////////////////////////////////////////

DiskSlider::DiskSlider() :
initialized_(false),
numPos_(2),
pos_(1),
name_(g_CARVIIDiskSlider) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

DiskSlider::~DiskSlider() {

    Shutdown();
}

void DiskSlider::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int DiskSlider::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Disk slider", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &DiskSlider::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");

    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "Out");
    SetPositionLabel(1, "In");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool DiskSlider::Busy() {
    // Who knows?

    return false;
}

int DiskSlider::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int DiskSlider::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 1)
            pos = 1;
        int ret = g_hub.SetDiskSliderPosition(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Prism Slider
///////////////////////////////////////////////////////////////////////////////

PrismSlider::PrismSlider() :
initialized_(false),
numPos_(2),
pos_(0),
name_(g_CARVIIPrismSlider) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

PrismSlider::~PrismSlider() {

    Shutdown();
}

void PrismSlider::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int PrismSlider::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Prism slider", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &PrismSlider::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");

    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "To camera");
    SetPositionLabel(1, "To eyepieces");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool PrismSlider::Busy() {
    // Who knows?

    return false;
}

int PrismSlider::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int PrismSlider::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 1)
            pos = 1;
        int ret = g_hub.SetPrismSliderPosition(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Disk spin motor
///////////////////////////////////////////////////////////////////////////////

SpinMotor::SpinMotor() :
initialized_(false),
numPos_(2),
pos_(1),
name_(g_CARVIISpinMotor) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

SpinMotor::~SpinMotor() {

    Shutdown();
}

void SpinMotor::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int SpinMotor::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Disk spin motor", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &SpinMotor::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");
    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "Off (no spin)");
    SetPositionLabel(1, "On (spinning)");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool SpinMotor::Busy() {
    // Who knows?

    return false;
}

int SpinMotor::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int SpinMotor::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 1)
            pos = 1;
        int ret = g_hub.SetSpinMotorState(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CARVII Touchscreen controller
///////////////////////////////////////////////////////////////////////////////

TouchScreen::TouchScreen() :
initialized_(false),
numPos_(2),
pos_(1),
name_(g_CARVIITouchScreen) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

TouchScreen::~TouchScreen() {

    Shutdown();
}

void TouchScreen::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int TouchScreen::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "CARVII Touchscreen lockout", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &TouchScreen::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;
    AddAllowedValue(MM::g_Keyword_State, "0");
    AddAllowedValue(MM::g_Keyword_State, "1");
    // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "Screen active");
    SetPositionLabel(1, "Screen locked");

    ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool TouchScreen::Busy() {
    // Who knows?

    return false;
}

int TouchScreen::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int TouchScreen::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    if (eAct == MM::BeforeGet) {
        // return pos as we know it
        pProp->Set(pos_);
    } else if (eAct == MM::AfterSet) {
        long pos;
        pProp->Get(pos);
        if (pos == pos_)
            return DEVICE_OK;
        if (pos < 0)
            pos = 0;
        else if (pos > 1)
            pos = 1;
        int ret = g_hub.SetTouchScreenState(*this, *GetCoreCallback(), pos);
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}