///////////////////////////////////////////////////////////////////////////////
// FILE:          MoticMicroscope.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Motic microscope device adapter
// COPYRIGHT:     2012 Motic China Group Co., Ltd.
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Motic

#include "MoticMicroscopeSDK.h"
#include "MoticMicroscope.h"

// Microscope connect correct
static bool g_device_connected = false;

// Device name
enum MoticDevice
{
	MoticHub = 0,
	MoticXYStage,
	MoticZ,
	MoticObjectives,
	MoticIllumination,
	MoticDevCount
};
// Device list
const char* g_devlist[][2] =
{
	{"MoticMicroscopeHub", "Motic Microscope Hub"},
	{"MoticStage", "Motic Stage"},
	{"MoticZAxis", "Motic Z Axis"},
	{"MoticObjectives", "Motic Objectives"},
	{"MoticIllumination", "Motic Illumination"}
};

const char* PROP_XYSPEED = "XYSpeed(um/s)";
const char* PROP_ZSPEED = "ZSpeed(um/s)";
const char* PROP_ILLUMINATION_INTENSITY = "Intensity";


// Module export
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_devlist[MoticHub][0], MM::HubDevice, g_devlist[MoticHub][1]);
	RegisterDevice(g_devlist[MoticXYStage][0], MM::XYStageDevice, g_devlist[MoticXYStage][1]);
	RegisterDevice(g_devlist[MoticZ][0], MM::StageDevice, g_devlist[MoticZ][1]);
	RegisterDevice(g_devlist[MoticObjectives][0], MM::StateDevice, g_devlist[MoticObjectives][1]);
	RegisterDevice(g_devlist[MoticIllumination][0], MM::GenericDevice, g_devlist[MoticIllumination][1]);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if(!deviceName)
		return 0;

	if(strcmp(deviceName, g_devlist[MoticHub][0]) == 0)
		return new Hub();
	else if(strcmp(deviceName, g_devlist[MoticXYStage][0]) == 0)
		return new XYStage();
	else if(strcmp(deviceName, g_devlist[MoticZ][0]) == 0)
		return new ZStage();
	else if(strcmp(deviceName, g_devlist[MoticObjectives][0]) == 0)
		return new Objectives();
	else if(strcmp(deviceName, g_devlist[MoticIllumination][0]) == 0)
		return new Illumination();
	else
		return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

// MMS event handler
void MMSEventHandler(int eventId, int data, void* userdata)
{
	if(userdata)
	{
		((Hub*)userdata)->MicroscopeEventHandler(eventId, data);
	}
}

// Implement Hub
Hub::Hub()
{
	_init = false;
	_busy = false;
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_HUB_PATH,
		"Motic microscope sdk environment variable 'MMSDKPath' is incorrect");
	_event = CreateEvent(0, TRUE, FALSE, 0);
	_timeout = 10000;
}

Hub::~Hub()
{
	Shutdown();
	CloseHandle(_event);
}

bool Hub::Busy()
{
	return _busy;
}

int Hub::Initialize()
{
	int res = -1;
	wchar_t* path = _wgetenv(L"MMSDKPath");
	wchar_t sdkpath[256] = {0};
	if(!path)
	{
		HKEY key;
		long res = RegOpenKeyExW(HKEY_LOCAL_MACHINE,
			L"SOFTWARE\\Motic China Group Co., Ltd.\\DSScanner",
			0,
			KEY_QUERY_VALUE,
			&key);
		if(res == ERROR_SUCCESS)
		{
			DWORD len = _countof(sdkpath);
			res = RegQueryValueExW(key, L"DSScannerPath", 0, 0, (LPBYTE)sdkpath, &len);
			if(res == ERROR_SUCCESS)
			{
				path = sdkpath;
			}
			RegCloseKey(key);
		}
	}
	if(path)
	{
		wchar_t buf[1024];
		wcscpy_s(buf, path);
		wcscat_s(buf, L"\\MMSDK.ini");
		res = mms_Initialize(buf);
	}

	if(res == 0)
	{
		mms_SetEventHandler(MMSEventHandler, this);
	}

	_init = res == 0;
	g_device_connected = (res == 0);

	return res == 0 ? DEVICE_OK : DEVICE_NOT_CONNECTED;
}

int Hub::Shutdown()
{
	_busy = false;
	_init = false;
	g_device_connected = false;

	mms_SetEventHandler(0, 0);
	mms_Uninitialize();
	return DEVICE_OK;
}

void Hub::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_devlist[MoticHub][0]);
}

int Hub::DetectInstalledDevices()
{
	ClearInstalledDevices();

	InitializeModuleData();

	char hubname[MM::MaxStrLength];
	GetName(hubname);
	for(unsigned i = 0; i < GetNumberOfDevices(); i++)
	{
		char devname[MM::MaxStrLength];
		if(GetDeviceName(i, devname, MM::MaxStrLength) &&
			strcmp(hubname, devname) != 0)
		{
			MM::Device* dev = CreateDevice(devname);
			AddInstalledDevice(dev);
		}
	}

	return DEVICE_OK;
}

void Hub::MicroscopeEventHandler(int eventId, int data)
{
	for(set<EventReceiver*>::iterator i = _ers.begin(); i != _ers.end(); i++)
	{
		(*i)->EventHandler(eventId, data);
	}
}

void Hub::SetOn()
{
	ResetEvent(_event);
}

void Hub::SetOff()
{
	SetEvent(_event);
}

bool Hub::Wait()
{
	return WaitForSingleObject(_event, _timeout) == WAIT_OBJECT_0;
}

void Hub::AddEventReceiver(EventReceiver* er)
{
	_ers.insert(er);
}

void Hub::RemoveEventReceiver(EventReceiver* er)
{
	_ers.erase(er);
}

// XYStage implement
XYStage::XYStage()
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_XY_INVALID, "XY stage unavailable");
	SetErrorText(ERR_XY_TIMEOUT, "XY move timeout");
	SetErrorText(ERR_XY_MOVE, "XY move error");

	CreateHubIDProperty();

	_init = false;
	_busy = false;
}

XYStage::~XYStage()
{
	Shutdown();
}

bool XYStage::Busy()
{
	return _busy;
}

int XYStage::Initialize()
{
	if(!g_device_connected)
		return DEVICE_NOT_CONNECTED;

	if(_init)
		return DEVICE_OK;

	// Name and description
	CreateProperty(MM::g_Keyword_Name, g_devlist[MoticXYStage][0],
		MM::String, true);
	CreateProperty(MM::g_Keyword_Description, g_devlist[MoticXYStage][1],
		MM::String, true);

	double l(0), r(0);
	xy_SpeedRange(&l, &r);
	CreateProperty(PROP_XYSPEED, CDeviceUtils::ConvertToString(r / 2), MM::Float, false, 0);
	SetPropertyLimits(PROP_XYSPEED, l, r);

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->AddEventReceiver(this);
	}

	UpdateStatus();

	_init = true;

	return DEVICE_OK;
}

int XYStage::Shutdown()
{
	_busy = false;
	_init = false;

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->RemoveEventReceiver(this);
	}
	return DEVICE_OK;
}

void XYStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_devlist[MoticXYStage][0]);
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
	if(xy_Available())
	{
		xy_XRange(&xMin, &xMax);
		xy_YRange(&yMin, &yMax);
		return DEVICE_OK;
	}
	return ERR_XY_INVALID;
}

int XYStage::SetPositionSteps(long x, long y)
{
	if(xy_Available())
	{
		double sx(0), sy(0);
		xy_GetHardwareMotionScale(&sx, &sy);
		double posx = x * sx;
		double posy = y * sy;
		double spd(0);
		GetProperty(PROP_XYSPEED, spd);
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOn();
		}
		_busy = true;
		if(xy_MoveAbsolutely(posx, posy, spd))
		{
			if(hub)
			{
				if(hub->Wait())
				{
					return DEVICE_OK;
				}
				else
				{
					xy_Stop();
					return ERR_XY_TIMEOUT;
				}
			}
			return DEVICE_OK;
		}
		else
		{
			_busy = false;
			return ERR_XY_MOVE;
		}
	}
	return ERR_XY_INVALID;
}

int XYStage::GetPositionSteps(long& x, long& y)
{
	if(xy_Available())
	{
		double posx(0), posy(0);
		xy_CurrentPosition(&posx, &posy);
		double sx(0), sy(0);
		xy_GetHardwareMotionScale(&sx, &sy);

		x = (int)(posx / sx);
		y = (int)(posy / sy);

		return DEVICE_OK;
	}
	return ERR_XY_INVALID;
}

int XYStage::Home()
{
	return DEVICE_OK;
}

int XYStage::Stop()
{
	if(xy_Available())
	{
		xy_Stop();
		return DEVICE_OK;
	}
	return ERR_XY_INVALID;
}

int XYStage::SetOrigin()
{
	return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	if(xy_Available())
	{
		double l(0), r(0), t(0), b(0);
		xy_XRange(&l, &r);
		xy_YRange(&t, &b);
		double sx(0), sy(0);
		xy_GetHardwareMotionScale(&sx, &sy);
		xMin = (long)(l / sx);
		xMax = (long)(r / sx);
		yMin = (long)(t / sx);
		yMax = (long)(b / sy);
		return DEVICE_OK;
	}
	return ERR_XY_INVALID;
}

double XYStage::GetStepSizeXUm()
{
	if(xy_Available())
	{
		double sx(0);
		xy_GetHardwareMotionScale(&sx, 0);
		return sx;
	}
	return 0.0;
}

double XYStage::GetStepSizeYUm()
{
	if(xy_Available())
	{
		double sy(0);
		xy_GetHardwareMotionScale(0, &sy);
		return sy;
	}
	return 0.0;
}

int XYStage::IsXYStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

void XYStage::EventHandler(int eventId, int /*data*/)
{
	if(eventId == XY_MOVED)
	{
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOff();
		}
		_busy = false;
		// Notify
		double x(0), y(0);
		xy_CurrentPosition(&x, &y);
		OnXYStagePositionChanged(x, y);
	}
}

// Z implement
ZStage::ZStage()
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_Z_INVALID, "Z axis unavailable");
	SetErrorText(ERR_Z_TIMEOUT, "Z move timeout");
	SetErrorText(ERR_Z_MOVE, "Z move error");

	CreateHubIDProperty();

	_init = false;
	_busy = false;
}

ZStage::~ZStage()
{
	Shutdown();
}

bool ZStage::Busy()
{
	return _busy;
}

int ZStage::Initialize()
{
	if(!g_device_connected)
		return DEVICE_NOT_CONNECTED;

	if(_init)
		return DEVICE_OK;

	// Name and description
	CreateProperty(MM::g_Keyword_Name, g_devlist[MoticZ][0],
		MM::String, true);
	CreateProperty(MM::g_Keyword_Description, g_devlist[MoticZ][1],
		MM::String, true);

	double l(0), r(0);
	z_SpeedRange(&l, &r);
	CreateProperty(PROP_ZSPEED, CDeviceUtils::ConvertToString(r / 2), MM::Float, false, 0);
	SetPropertyLimits(PROP_ZSPEED, l, r);

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->AddEventReceiver(this);
	}

	UpdateStatus();

	_init = true;
	return DEVICE_OK;
}

int ZStage::Shutdown()
{
	_init = false;
	_busy = false;

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->RemoveEventReceiver(this);
	}

	return DEVICE_OK;
}

void ZStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_devlist[MoticZ][0]);
}

int ZStage::SetPositionUm(double pos)
{
	if(z_Available())
	{
		double spd(0);
		GetProperty(PROP_ZSPEED, spd);
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOn();
		}
		_busy = true;
		if(z_MoveAbsolutely(pos, spd))
		{
			if(hub)
			{
				if(hub->Wait())
				{
					OnStagePositionChanged(pos);
					return DEVICE_OK;
				}
				else
				{
					z_Stop();
					return ERR_Z_TIMEOUT;
				}
			}
			return DEVICE_OK;
		}
		else
		{
			_busy = false;
			return ERR_Z_MOVE;
		}
	}
	return ERR_Z_INVALID;
}

int ZStage::GetPositionUm(double& pos)
{
	if(z_Available())
	{
		pos = z_CurrentPosition();
		return DEVICE_OK;
	}
	return ERR_Z_INVALID;
}

int ZStage::SetPositionSteps(long steps)
{
	if(z_Available())
	{
		return SetPositionUm(steps * z_GetHardwareMotionScale());
	}
	return ERR_Z_INVALID;
}

int ZStage::GetPositionSteps(long& steps)
{
	if(z_Available())
	{
		steps = (long)(z_CurrentPosition() / z_GetHardwareMotionScale());
		return DEVICE_OK;
	}
	return ERR_Z_INVALID;
}

int ZStage::SetOrigin()
{
	return DEVICE_OK;
}

int ZStage::GetLimits(double& lower, double& upper)
{
	if(z_Available())
	{
		z_Range(&lower, &upper);
		return DEVICE_OK;
	}

	return ERR_Z_INVALID;
}

int ZStage::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

bool ZStage::IsContinuousFocusDrive() const
{
	return false;
}

void ZStage::EventHandler(int eventId, int /*data*/)
{
	if(eventId == Z_MOVED)
	{
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOff();
		}
		_busy = false;
		double z = z_CurrentPosition();
		OnStagePositionChanged(z);
	}
}

// Implement Objectives
Objectives::Objectives()
{
	InitializeDefaultErrorMessages();
	SetErrorText(ERR_OBJECTIVE_NOTFOUND, "Specified objective not found");
	SetErrorText(ERR_OBJECTIVE_TIMEOUT, "Objective move timeout");

	CreateHubIDProperty();

	_init = false;
	_busy = false;
}

Objectives::~Objectives()
{
	Shutdown();
}

bool Objectives::Busy()
{
	return _busy;
}

int Objectives::Initialize()
{
	if(!g_device_connected)
		return DEVICE_NOT_CONNECTED;

	if(_init)
		return DEVICE_OK;

	// Name and description
	CreateProperty(MM::g_Keyword_Name, g_devlist[MoticObjectives][0],
		MM::String, true);
	CreateProperty(MM::g_Keyword_Description, g_devlist[MoticObjectives][1],
		MM::String, true);

	_mag.clear();
	if(obj_Available())
	{
		for(int i = 0; i < obj_GetCount(); i++)
		{
			double m = obj_GetObjectiveMagnification(i);
			if(m > 0)
			{
				_mag[i] = m;
			}
		}
		char buf[64];
		for(map<int, double>::iterator i = _mag.begin(); i != _mag.end(); ++i)
		{
			sprintf(buf, "%dX", (int)i->second);
			SetPositionLabel(i->first, buf);
		}
		_curpos = obj_GetCurrentObjective();

		CPropertyAction* act = new CPropertyAction(this, &Objectives::OnState);
		CreateProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(_curpos), MM::Integer, false, act);

		act = new CPropertyAction(this, &CStateBase::OnLabel);
		CreateProperty(MM::g_Keyword_Label, "", MM::String, false, act);
	}

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->AddEventReceiver(this);
	}

	UpdateStatus();

	_init = true;
	return DEVICE_OK;;
}

int Objectives::Shutdown()
{
	_busy = false;
	_init = false;

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->RemoveEventReceiver(this);
	}

	return DEVICE_OK;
}

void Objectives::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_devlist[MoticObjectives][0]);
}

unsigned long Objectives::GetNumberOfPositions() const
{
	return (unsigned long)_mag.size();
}

int Objectives::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		long pos;
		pProp->Get(pos);

		if(_mag.find(pos) == _mag.end())
		{
			pProp->Set(_curpos);
			return ERR_OBJECTIVE_NOTFOUND;
		}
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOn();
		}
		_busy = true;
		if(obj_SwitchObjective(pos))
		{
			if(hub->Wait())
			{
				_curpos = pos;
				return DEVICE_OK;
			}
			else
			{
				return ERR_OBJECTIVE_TIMEOUT;
			}
		}
		else
		{
			return DEVICE_OK;
		}
	}
	return DEVICE_OK;
}

void Objectives::EventHandler(int eventId, int /*data*/)
{
	if(eventId == OBJECTIVE_CHANGED)
	{
		Hub* hub = static_cast<Hub*>(GetParentHub());
		if(hub)
		{
			hub->SetOff();
		}
		_busy = false;
	}
}

// Implement Illumination
Illumination::Illumination()
{
	InitializeDefaultErrorMessages();

	CreateHubIDProperty();

	_init = false;
	_busy = false;
}

Illumination::~Illumination()
{
}

bool Illumination::Busy()
{
	return false;
}

int Illumination::Initialize()
{
	if(!g_device_connected)
		return DEVICE_NOT_CONNECTED;

	if(_init)
		return DEVICE_OK;

	// Name and description
	CreateProperty(MM::g_Keyword_Name, g_devlist[MoticIllumination][0],
		MM::String, true);
	CreateProperty(MM::g_Keyword_Description, g_devlist[MoticIllumination][1],
		MM::String, true);

	if(illumination_Available())
	{
		int v = illumination_GetValue();
		CPropertyAction* act = new CPropertyAction(this, &Illumination::OnIntensity);
		CreateProperty(PROP_ILLUMINATION_INTENSITY, CDeviceUtils::ConvertToString(v), MM::Integer, false, act);
		int l(0), r(0);
		illumination_GetRange(&l, &r);
		SetPropertyLimits(PROP_ILLUMINATION_INTENSITY, l, r);
	}

	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->AddEventReceiver(this);
	}

	UpdateStatus();

	_init = true;

	return DEVICE_OK;
}

int Illumination::Shutdown()
{
	Hub* hub = static_cast<Hub*>(GetParentHub());
	if(hub)
	{
		hub->RemoveEventReceiver(this);
	}

	_init = false;
	return DEVICE_OK;
}

void Illumination::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_devlist[MoticIllumination][0]);
}

int Illumination::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		long val(0);
		pProp->Get(val);
		illumination_SetValue(val);
	}
	return DEVICE_OK;
}

void Illumination::EventHandler(int eventId, int /*data*/)
{
	if(eventId == ILLUMINATION_CHANGED)
	{
		long val = illumination_GetValue();
		SetProperty(PROP_ILLUMINATION_INTENSITY, CDeviceUtils::ConvertToString(val));
		// notify
		OnPropertyChanged(PROP_ILLUMINATION_INTENSITY, CDeviceUtils::ConvertToString(val));
	}
}
