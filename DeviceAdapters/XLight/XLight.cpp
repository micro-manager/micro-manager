///////////////////////////////////////////////////////////////////////////////
// FILE:       XLIGHT.cpp
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CrEST XLight adapter
//                                                                                     
// AUTHOR:        E. Chiarappa echiarappa@libero.it, 01/20/2014
//                Based on CARVII adapter by  G. Esteban Fernandez.
//
// COPYRIGHT:     2014, Crestoptics s.r.l.
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

#include "XLight.h"
#include "XLIGHTHub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <iostream>

const char* g_XLIGHTHub = "XLIGHT Hub";
//const char* g_XLIGHTSpeckleReducer = "XLIGHT SpeckleReducer";
const char* g_XLIGHTDichroicFilter = "XLIGHT Dichroic";
const char* g_XLIGHTDiskSlider = "XLIGHT Disk slider";
const char* g_XLIGHTSpinMotor = "XLIGHT Spin motor";
const char* g_XLIGHTEmissionWheel = "XLIGHT Emission Wheel";
const char* g_XLIGHTExcitationWheel = "XLIGHT Excitation Wheel";
const char* g_XLIGHTTouchScreen = "XLIGHT Touchscreen";

using namespace std;

XLIGHTHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData() {
 
	RegisterDevice(g_XLIGHTHub, MM::GenericDevice, "Hub (Needed for XLIGHT)");
    //RegisterDevice(g_XLIGHTSpeckleReducer, MM::StateDevice, "Confocal SpeckleReducer");
    RegisterDevice(g_XLIGHTDichroicFilter, MM::StateDevice, "Confocal dichroic wheel");
    RegisterDevice(g_XLIGHTDiskSlider, MM::StateDevice, "Spinning disk slider");
    RegisterDevice(g_XLIGHTSpinMotor, MM::StateDevice, "Spinning disk motor");
	RegisterDevice(g_XLIGHTEmissionWheel, MM::StateDevice, "Confocal Emission wheel");
	RegisterDevice(g_XLIGHTExcitationWheel, MM::StateDevice, "Confocal Excitation wheel");
    RegisterDevice(g_XLIGHTTouchScreen, MM::StateDevice, "Touchscreen lockout control");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName) {
    if (deviceName == 0)
        return 0;

    if (strcmp(deviceName, g_XLIGHTHub) == 0) {
        return new Hub();
    } /*else if (strcmp(deviceName, g_XLIGHTSpeckleReducer) == 0) {
        return new SpeckleReducer();
    }*/ else if (strcmp(deviceName, g_XLIGHTDichroicFilter) == 0) {
        return new Dichroic();
    } else if (strcmp(deviceName, g_XLIGHTDiskSlider) == 0) {
        return new DiskSlider();
    } else if (strcmp(deviceName, g_XLIGHTSpinMotor) == 0) {
        return new SpinMotor();
	} else if (strcmp(deviceName, g_XLIGHTEmissionWheel) == 0) {
        return new Emission();
	} else if (strcmp(deviceName, g_XLIGHTExcitationWheel) == 0) {
        return new Excitation();
    } else if (strcmp(deviceName, g_XLIGHTTouchScreen) == 0) {
        return new TouchScreen();
    }
    return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice) {

    delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// XLIGHT Hub
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

    CDeviceUtils::CopyLimitedString(name, g_XLIGHTHub);
}

bool Hub::Busy() {

    return false;
}

int Hub::Initialize() {
    // set property list
    // -----------------

    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, g_XLIGHTHub, MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT hub", MM::String, true);
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
// XLIGHT Dichroic Filters
///////////////////////////////////////////////////////////////////////////////

Dichroic::Dichroic() :
initialized_(false),
numPos_(5),
pos_(0),
name_(g_XLIGHTDichroicFilter) {

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
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Dichroic filter", MM::String, true);
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


	// read-back position
	ostringstream os;
	os << "rC";
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	int pos = g_hub.GetDevicePosition() - 1;
	SetPosition(pos);
	// pAct->Set(pos);

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
    int delay = 90;
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
        // int ret = g_hub.SetDichroicPosition(*this, *GetCoreCallback(), pos, delay);
		int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
		ostringstream os;
		os << "C" << posCommand;
		g_hub.SetDeviceWait(delay);
		int ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

//-------------------------------------------------------------------
///////////////////////////////////////////////////////////////////////////////
// XLIGHT Emission Wheel
///////////////////////////////////////////////////////////////////////////////

Emission::Emission() :
initialized_(false),
numPos_(8),
pos_(0),
name_(g_XLIGHTEmissionWheel) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

Emission::~Emission() {

    Shutdown();
}

void Emission::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Emission::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Emission Wheel Position", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &Emission::OnState);
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
    SetPositionLabel(0, "Emission-0");
    SetPositionLabel(1, "Emission-1");
    SetPositionLabel(2, "Emission-2");
    SetPositionLabel(3, "Emission-3");
    SetPositionLabel(4, "Emission-4");
	SetPositionLabel(5, "Emission-5");
	SetPositionLabel(6, "Emission-6");
	SetPositionLabel(7, "Emission-7");

	// read-back position
	ostringstream os;
	os << "rB";
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	int pos = g_hub.GetDevicePosition() - 1;
	SetPosition(pos);

	ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool Emission::Busy() {
    // Who knows?

    return false;
}

int Emission::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Emission::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    int delay = 75;
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
        //int ret = g_hub.SetDichroicPosition(*this, *GetCoreCallback(), pos, delay);
		//int ret = g_hub.SetEmissionWheelPosition(*this, *GetCoreCallback(), pos/*, delay*/); // GPU first fix
		int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
		ostringstream os;
		os << "B" << posCommand;
		g_hub.SetDeviceWait(delay);
		int ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}


//-------------------------------------------------------------------
///////////////////////////////////////////////////////////////////////////////
// XLIGHT Excitation Wheel
///////////////////////////////////////////////////////////////////////////////

Excitation::Excitation() :
initialized_(false),
numPos_(8),
pos_(0),
use_new_command_(false),
name_(g_XLIGHTExcitationWheel) {

    InitializeDefaultErrorMessages();

    // Todo: Add custom messages
}

Excitation::~Excitation() {

    Shutdown();
}

void Excitation::GetName(char* name) const {

    assert(name_.length() < CDeviceUtils::GetMaxStringLength());
    CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Excitation::Initialize() {
    // Name
    int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // Description
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Excitation Wheel Position", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &Excitation::OnState);
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
    SetPositionLabel(0, "Excitation-0");
    SetPositionLabel(1, "Excitation-1");
    SetPositionLabel(2, "Excitation-2");
    SetPositionLabel(3, "Excitation-3");
    SetPositionLabel(4, "Excitation-4");
	SetPositionLabel(5, "Excitation-5");
	SetPositionLabel(6, "Excitation-6");
	SetPositionLabel(7, "Excitation-7");

	// read-back position
	ostringstream os;
	//os << "rE";
	os << "r" << cmd_letter; 
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	if (DEVICE_OK == ret)
	{
		int pos = g_hub.GetDevicePosition() - 1;
		SetPosition(pos);
	}
	else 
	{
		os.str(""); // this actually clears the oss, the line below clears erros and flags instead
		os.clear();
		os << "rA";
		ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
		if (DEVICE_OK == ret)
		{
			int pos = g_hub.GetDevicePosition() - 1;
			SetPosition(pos);
			use_new_command_ = true;
		}
	}

	ret = UpdateStatus();
    if (ret != DEVICE_OK)
        return ret;

    initialized_ = true;

    return DEVICE_OK;
}

bool Excitation::Busy() {
    // Who knows?

    return false;
}

int Excitation::Shutdown() {
    if (initialized_) {

        initialized_ = false;
    }
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Excitation::OnState(MM::PropertyBase* pProp, MM::ActionType eAct) {
    int delay = 75;
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
		int posCommand = pos + 1; //MM positions start at 0, CARVII pos start at 1
		ostringstream os;
		if (use_new_command_)
			os << "A" << posCommand;
		else
			os << cmd_letter << posCommand;
		g_hub.SetDeviceWait(delay);
		int ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
        if (ret == DEVICE_OK) {
            pos_ = pos;
            pProp->Set(pos_);
            return DEVICE_OK;
        } else
            return ret;
    }
    return DEVICE_OK;
}

//---------------------------------------------------------------------
 
///////////////////////////////////////////////////////////////////////////////
// XLIGHT Disk Slider
///////////////////////////////////////////////////////////////////////////////

DiskSlider::DiskSlider() :
initialized_(false),
 numPos_(3),
 pos_(0),
name_(g_XLIGHTDiskSlider) {

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
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Disk slider", MM::String, true);
    if (DEVICE_OK != ret)
        return ret;

    // State
    CPropertyAction * pAct = new CPropertyAction(this, &DiskSlider::OnState);
    ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

//--------------------------------------------------------
	
   vector<string> DiskSliderValues;
   DiskSliderValues.push_back("0");
    DiskSliderValues.push_back("1"); 
   DiskSliderValues.push_back("2"); 

   ret = SetAllowedValues(MM::g_Keyword_State, DiskSliderValues);

//----------------------------------------------------------
  
     // Label                                                                  
    pAct = new CPropertyAction(this, &CStateBase::OnLabel);
    ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
    if (ret != DEVICE_OK)
        return ret;

    // create default positions and labels
    SetPositionLabel(0, "Disk Out");
    SetPositionLabel(1, "Disk Pos 70um");
	SetPositionLabel(2, "Disk Pos 40um");
 
	// read-back position
	ostringstream os;
	os << "rD";
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	int pos = g_hub.GetDevicePosition();
	SetPosition(pos);

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
    int delay = 0;
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
        else if (pos > 2)
            pos = 2;
			if (( pos_ == 0 && pos == 1 )|| ( pos_ == 1 && pos == 0 ))
			delay = 600;
		if ( (pos_ == 0 && pos == 2) || (pos_ == 2 && pos == 0))
			delay = 300;
		 if ( (pos_ == 1 && pos == 2) || (pos_ == 2 && pos == 1))
			delay = 700;
        // int ret = g_hub.SetDiskSliderPosition(*this, *GetCoreCallback(), pos, delay);
		ostringstream os;
		os << "D" << pos;
		g_hub.SetCommandTimeout(20000);
		g_hub.SetDeviceWait(delay);
 		int ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
		g_hub.RestoreCommandTimeout();
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
// XLIGHT Disk spin motor
///////////////////////////////////////////////////////////////////////////////

SpinMotor::SpinMotor() :
initialized_(false),
numPos_(2),
pos_(1),
name_(g_XLIGHTSpinMotor) {

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
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Disk spin motor", MM::String, true);
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

	// read-back position
	ostringstream os;
	os << "rN";
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	int pos = g_hub.GetDevicePosition();
	SetPosition(pos);

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
		g_hub.SetCommandTimeout(20000);
        int ret = g_hub.SetSpinMotorState(*this, *GetCoreCallback(), pos);
		g_hub.RestoreCommandTimeout();
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
// XLIGHT Touchscreen controller
///////////////////////////////////////////////////////////////////////////////

TouchScreen::TouchScreen() :
initialized_(false),
numPos_(2),
pos_(1),
name_(g_XLIGHTTouchScreen) {

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
    ret = CreateProperty(MM::g_Keyword_Description, "XLIGHT Touchscreen lockout", MM::String, true);
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

	// read-back position
	ostringstream os;
	os << "rM";
	ret = g_hub.ExecuteCommandEx(*this, *GetCoreCallback(), os.str().c_str());
	int pos = g_hub.GetDevicePosition();
	SetPosition(pos);

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
