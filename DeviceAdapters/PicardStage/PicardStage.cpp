///////////////////////////////////////////////////////////////////////////////
// FILE:          PicardStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The drivers required for the Picard Industries USB stages
//
// AUTHORS:       Johannes Schindelin, Luke Stuyvenberg, 2011 - 2014
//
// COPYRIGHT:     Board of Regents of the University of Wisconsin -- Madison,
//					Copyright (C) 2011 - 2014
//
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

#include <iostream>

#include "../../MMDevice/ModuleInterface.h"
#include "PiUsb.h"

#include "PicardStage.h"

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "PicardStage.dll" library
const char* g_TwisterDeviceName = "Picard Twister";
const char* g_StageDeviceName = "Picard Z Stage";
const char* g_XYStageDeviceName = "Picard XY Stage";
const char* g_XYAdapterDeviceName = "Picard XY Stage Adapter";
const char* g_Keyword_SerialNumber = "Serial Number";
const char* g_Keyword_SerialNumberX = "Serial Number (X)";
const char* g_Keyword_SerialNumberY = "Serial Number (Y)";
const char* g_Keyword_Min = "Min";
const char* g_Keyword_MinX = "X-Min";
const char* g_Keyword_MinY = "Y-Min";
const char* g_Keyword_Max = "Max";
const char* g_Keyword_MaxX = "X-Max";
const char* g_Keyword_MaxY = "Y-Max";
const char* g_Keyword_Velocity = "Velocity";
const char* g_Keyword_VelocityX = "X-Velocity";
const char* g_Keyword_VelocityY = "Y-Velocity";
const char* g_Keyword_StepSize = "StepSize";
const char* g_Keyword_StepSizeX = "X-StepSize";
const char* g_Keyword_StepSizeY = "Y-StepSize";

#define TO_STRING_INTERNAL(x) #x
#define FIXED_TO_STRING(x) TO_STRING_INTERNAL(x)

#define CLOCKDIFF(now, then) ((static_cast<double>(now) - static_cast<double>(then))/(static_cast<double>(CLOCKS_PER_SEC)))
#define MAX_WAIT 0.05 // Maximum time to wait for the motors to begin motion, in seconds.

// Something in the PiUsb routines changed, and now twister values are in degrees. For now, default step size to that value.
#define TWISTER_STEP_SIZE 1.0 // deg/step
#define TWISTER_LOWER_LIMIT_STEPS -32767
#define TWISTER_UPPER_LIMIT_STEPS 32767
#define TWISTER_LOWER_LIMIT -32767.6 // TWISTER_LOWER_LIMIT_STEPS * TWISTER_STEP_SIZE
#define TWISTER_UPPER_LIMIT 32767.6 // TWISTER_UPPER_LIMIT_STEPS * TWISTER_STEP_SIZE

// These constants are per the Picard Industries documentation.
#define MOTOR_STEP_SIZE 1.5 // um/step
#define MOTOR_LOWER_LIMIT_STEPS 0
#define MOTOR_UPPER_LIMIT_STEPS 5200 // Officially, the limit is 5800; leave a physical margin.
#define MOTOR_LOWER_LIMIT 0 // MOTOR_LOWER_LIMIT_STEPS * MOTOR_STEP_SIZE
#define MOTOR_UPPER_LIMIT 7800 // MOTOR_UPPER_LIMIT_STEPS * MOTOR_STEP_SIZE

// These apply to both motors and twisters.
#define PICARD_MIN_VELOCITY 1
#define PICARD_MAX_VELOCITY 10

#define DEFAULT_SERIAL_UNKNOWN -1 // This is the default serial value, before serial numbers are pinged.
#define MAX_SERIAL_IDX 500 // Highest serial number index to ping.

#define PICARDSTAGE_ERROR_OFFSET 1327 // Error codes are unique to device classes, but MM defines some basic ones (see MMDeviceConstants.h). Make sure we're past them.

inline static char* VarFormat(const char* fmt, ...)
{
	static char buffer[MM::MaxStrLength];

	memset(buffer, 0x00, MM::MaxStrLength);
	va_list va;
	va_start(va, fmt);
	vsnprintf(buffer, MM::MaxStrLength, fmt, va);
	va_end(va);

	return buffer;
}

class CPiDetector
{
	private:
	CPiDetector(MM::Core& core, MM::Device& device)
	{
		core.LogMessage(&device, "Pinging motors...", false);

		m_pMotorList = new int[16];
		m_pTwisterList = new int[4];

		int error = PingDevices(core, device, &piConnectMotor, &piDisconnectMotor, m_pMotorList, 16, &m_iMotorCount);
		if(error > 1)
			core.LogMessage(&device, VarFormat(" Error detecting motors: %d", error), false);

		error = PingDevices(core, device, &piConnectTwister, &piDisconnectTwister, m_pTwisterList, 4, &m_iTwisterCount);
		if(error > 1)
			core.LogMessage(&device, VarFormat(" Error detecting twisters: %d", error), false);

		core.LogMessage(&device, VarFormat("Found %d motors and %d twisters.", m_iMotorCount, m_iTwisterCount), false);
	}

	~CPiDetector()
	{
		delete[] m_pMotorList;
		delete[] m_pTwisterList;
	}

	public:
	int GetMotorSerial(int idx)
	{
		if(idx < m_iMotorCount)
			return m_pMotorList[idx];

		return DEFAULT_SERIAL_UNKNOWN;
	}

	int GetTwisterSerial(int idx)
	{
		if(idx < m_iTwisterCount)
			return m_pTwisterList[idx];

		return DEFAULT_SERIAL_UNKNOWN;
	}

	private:
	int PingDevices(MM::Core& core, MM::Device& device, void* (__stdcall* connfn)(int*, int), void (__stdcall* discfn)(void*), int* pOutArray, const int iMax, int* pOutCount)
	{
		void* handle = NULL;
		int error = 0;
		int count = 0;
		for(int idx = 0; idx < MAX_SERIAL_IDX && count < iMax; ++idx)
		{
			if((handle = (*connfn)(&error, idx)) != NULL && error <= 1)
			{
				pOutArray[count++] = idx;
				(*discfn)(handle);
				handle = NULL;
			}
			else if(error > 1)
			{
				core.LogMessage(&device, VarFormat("Error scanning index %d: %d", idx, error), false);
				*pOutCount = count;
				return error;
			}
		}

		*pOutCount = count;
		return 0;
	}

	int *m_pMotorList;
	int m_iMotorCount;

	int *m_pTwisterList;
	int m_iTwisterCount;

	private:
	static CPiDetector *pPiDetector;

	public:
	static CPiDetector *GetInstance(MM::Core& core, MM::Device& device)
	{
		if(pPiDetector == NULL)
			pPiDetector = new CPiDetector(core, device);

		return pPiDetector;
	}
};

CPiDetector* CPiDetector::pPiDetector;

inline static void GenerateAllowedVelocities(vector<string>& vels)
{
	vels.clear();

	for(int i = PICARD_MIN_VELOCITY; i <= PICARD_MAX_VELOCITY; ++i)
		vels.push_back(VarFormat("%d", i));
}

// This routine handles a very generic sense of the OnVelocity PropertyAction.
// Get/set the velocity to a member variable, and optionally invoke PiUsb routines to change the motor's on-board velocity.
inline static int OnVelocityGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, void* handle, int& velocity, int (__stdcall* pGet)(int*, void*), int (__stdcall* pSet)(int, void*))
{
	if(handle == NULL)
		return eAct == MM::BeforeGet ? DEVICE_OK : DEVICE_NOT_CONNECTED;

	switch(eAct)
	{
	case MM::BeforeGet:
		{
			if(pGet != NULL && (*pGet)(&velocity, handle) != PI_NO_ERROR)
				return DEVICE_NOT_CONNECTED;

			pProp->Set(static_cast<long>(velocity));

			break;
		}
	case MM::AfterSet:
		{
			long vel_temp = static_cast<long>(velocity);
			pProp->Get(vel_temp);
			velocity = static_cast<int>(vel_temp);

			if(pSet != NULL && (*pSet)(velocity, handle) != PI_NO_ERROR)
				return DEVICE_NOT_CONNECTED;

			break;
		}
	}

	return DEVICE_OK;
}

// Similar to the above routine, this one handles the OnSerialNumber PropertyAction.
inline static int OnSerialGeneric(MM::PropertyBase* pProp, MM::ActionType eAct, MM::Core& core, MM::Device& self, int& serial, bool twister, int serialidx)
{
	switch(eAct)
	{
	case MM::BeforeGet:
		{
			if(serial == DEFAULT_SERIAL_UNKNOWN)
			{
				if(twister)
					serial = CPiDetector::GetInstance(core, self)->GetTwisterSerial(serialidx);
				else
					serial = CPiDetector::GetInstance(core, self)->GetMotorSerial(serialidx);
			}

			pProp->Set(static_cast<long>(serial));

			break;
		}
	case MM::AfterSet:
		{
			long serial_temp = static_cast<long>(serial);
			pProp->Get(serial_temp);
			serial = static_cast<int>(serial_temp);

			return self.Initialize();
		}
	}

	return DEVICE_OK;
}

// PiUsb generates a specific set of error messages. This routine maps them onto base MM device errors (see MMDeviceConstants.h).
inline static int InterpretPiUsbError(int pi_error)
{
	switch(pi_error)
	{
	case PI_NO_ERROR:
		return DEVICE_OK;
	case PI_DEVICE_NOT_FOUND:
		return DEVICE_NOT_CONNECTED;
	case PI_OBJECT_NOT_FOUND:
	case PI_CANNOT_CREATE_OBJECT:
	default:
		return DEVICE_ERR;
	};
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all supported hardware devices here Do not discover devices at runtime.
 * To avoid warnings about missing DLLs, Micro-Manager maintains a list of
 * supported device (MMDeviceList.txt).  This list is generated using
 * information supplied by this function, so runtime discovery will create
 * problems.
 */
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_TwisterDeviceName, MM::StageDevice, "Twister");
	RegisterDevice(g_StageDeviceName, MM::StageDevice, "Z stage");
	RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_TwisterDeviceName) == 0)
	{
		// create twister
		return new CPiTwister();
	}
	else if (strcmp(deviceName, g_StageDeviceName) == 0)
	{
		// create stage
		return new CPiStage();
	}
	else if (strcmp(deviceName, g_XYStageDeviceName) == 0)
	{
		// create X/Y stage
		return new CPiXYStage();
	}

	// ...supplied name not recognized
	return NULL;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

// The twister

CPiTwister::CPiTwister()
: serial_(DEFAULT_SERIAL_UNKNOWN), handle_(NULL)
{
	CPropertyAction* pAct = new CPropertyAction (this, &CPiTwister::OnSerialNumber);
	CreateProperty(g_Keyword_SerialNumber, FIXED_TO_STRING(DEFAULT_SERIAL_UNKNOWN), MM::String, false, pAct, true);
	SetErrorText(1, "Could not initialize twister");

	CreateProperty(g_Keyword_Velocity, FIXED_TO_STRING(MOTOR_MAX_VELOCITY), MM::Integer, false, new CPropertyAction(this, &CPiTwister::OnVelocity), false);
	vector<string> vels;
	GenerateAllowedVelocities(vels);
	SetAllowedValues(g_Keyword_Velocity, vels);

	CreateProperty(g_Keyword_Min, FIXED_TO_STRING(TWISTER_LOWER_LIMIT), MM::Integer, false, NULL, true);
	CreateProperty(g_Keyword_Max, FIXED_TO_STRING(TWISTER_UPPER_LIMIT), MM::Integer, false, NULL, true);

	CreateProperty(g_Keyword_StepSize, FIXED_TO_STRING(TWISTER_STEP_SIZE), MM::Float, false, NULL, true);
}

CPiTwister::~CPiTwister()
{
}

int CPiTwister::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// Usually only 1 twister, so expect index 0.
	return OnSerialGeneric(pProp, eAct, *GetCoreCallback(), *this, serial_, true, 0);
}

int CPiTwister::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// Although there *is* a piGetTwisterVelocity, the absence of a piSetTwisterVelocity tends to override the property change.
	return OnVelocityGeneric(pProp, eAct, handle_, velocity_, NULL, NULL);
}

bool CPiTwister::Busy()
{
	if(handle_ == NULL)
		return false;

	BOOL moving;
	if (piGetTwisterMovingStatus(&moving, handle_) == PI_NO_ERROR)
		return moving != 0;

	return false;
}

int CPiTwister::Initialize()
{
	if(handle_ != NULL)
		Shutdown();

	int pi_error = PI_NO_ERROR;
	handle_ = piConnectTwister(&pi_error, serial_);

	if (handle_ != NULL && pi_error == PI_NO_ERROR)
		pi_error = piGetTwisterVelocity(&velocity_, handle_);
	else
		LogMessage(VarFormat("Could not initialize twister %d (PiUsb error code %d)", serial_, pi_error), false);

	return InterpretPiUsbError(pi_error);
}

int CPiTwister::Shutdown()
{
	if (handle_ != NULL) {
		piDisconnectTwister(handle_);
		handle_ = NULL;
	}

	return DEVICE_OK;
}

void CPiTwister::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_TwisterDeviceName);
}

int CPiTwister::SetPositionUm(double pos)
{
	return SetPositionSteps(static_cast<long>(pos / GetStepSizeUm()));
}

int CPiTwister::GetPositionUm(double& pos)
{
	long position = 0;
	int error;
	if ((error = GetPositionSteps(position)) == DEVICE_OK)
		pos = position * GetStepSizeUm();

	return error;
}

double CPiTwister::GetStepSizeUm()
{
	double stepsize = TWISTER_STEP_SIZE;

	if(GetProperty(g_Keyword_StepSize, stepsize) != DEVICE_OK)
		return TWISTER_STEP_SIZE;

	// This is technically wrong, since the step size is not in um, but in degrees.
	// MM does not have a concept of a rotational stage, however, so 'overload' this.
	return stepsize;
}

int CPiTwister::SetPositionSteps(long steps)
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	long min = TWISTER_LOWER_LIMIT_STEPS, max = TWISTER_UPPER_LIMIT_STEPS;
	int error = DEVICE_OK;

	if((error = GetStepLimits(min, max)) != DEVICE_OK)
		return error;

	steps = steps < min ? min : (steps > max ? max : steps); // Clamp to min..max

	int to = static_cast<int>(steps);
	int pi_error = piRunTwisterToPosition(to, velocity_, handle_); // Be sure not to confuse errors...

	if(pi_error != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error);

	int at = 0;
	if((pi_error = piGetTwisterPosition(&at, handle_)) != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error);;

	if(at != to) {
		clock_t start = clock();
		clock_t last = start;
		while(!Busy() && at != to && CLOCKDIFF(last = clock(), start) < MAX_WAIT) {
			CDeviceUtils::SleepMs(0);

			if((pi_error = piGetTwisterPosition(&at, handle_)) != PI_NO_ERROR)
				return InterpretPiUsbError(pi_error);
		};

		if(CLOCKDIFF(last, start) >= MAX_WAIT)
			LogMessage(VarFormat("Long wait (twister): %d / %d (%d != %d).", last - start, static_cast<int>(MAX_WAIT*CLOCKS_PER_SEC), at, to), true);
	};

	return InterpretPiUsbError(pi_error);
}

int CPiTwister::GetPositionSteps(long& steps)
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	int position, pi_error;
	if ((pi_error = piGetTwisterPosition(&position, handle_)) == PI_NO_ERROR)
		steps = static_cast<long>(position);

	return InterpretPiUsbError(pi_error);
}

int CPiTwister::SetOrigin()
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	return InterpretPiUsbError(piSetTwisterPositionZero(handle_));
}

int CPiTwister::GetLimits(double& lower, double& upper)
{
	int error = DEVICE_OK;

	if((error = GetProperty(g_Keyword_Min, lower)) != DEVICE_OK)
		return error;

	if((error = GetProperty(g_Keyword_Max, upper)) != DEVICE_OK)
		return error;

	return DEVICE_OK;
}

int CPiTwister::GetStepLimits(long& lower, long& upper)
{
	double low, high, stepsize = GetStepSizeUm();
	int error;

	if((error = GetLimits(low, high)) != DEVICE_OK)
		return error;

	lower = static_cast<long>(low / stepsize);
	upper = static_cast<long>(high / stepsize);

	return DEVICE_OK;
}

int CPiTwister::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

bool CPiTwister::IsContinuousFocusDrive() const
{
	return false;
}

// The Stage

CPiStage::CPiStage()
: serial_(DEFAULT_SERIAL_UNKNOWN), handle_(NULL)
{
	CreateProperty(g_Keyword_SerialNumber, FIXED_TO_STRING(DEFAULT_SERIAL_UNKNOWN), MM::Integer, false, new CPropertyAction (this, &CPiStage::OnSerialNumber), true);

	CreateProperty(g_Keyword_Velocity, FIXED_TO_STRING(MOTOR_MAX_VELOCITY), MM::Integer, false, new CPropertyAction (this, &CPiStage::OnVelocity), false);
	std::vector<std::string> allowed_velocities;
	GenerateAllowedVelocities(allowed_velocities);
	SetAllowedValues(g_Keyword_Velocity, allowed_velocities);

	CreateProperty(g_Keyword_StepSize, FIXED_TO_STRING(MOTOR_STEP_SIZE), MM::Float, false, NULL, true);

	CreateProperty(g_Keyword_Min, FIXED_TO_STRING(MOTOR_LOWER_LIMIT), MM::Integer, false, NULL, true);
	CreateProperty(g_Keyword_Max, FIXED_TO_STRING(MOTOR_UPPER_LIMIT), MM::Integer, false, NULL, true);

	CreateProperty("GoHome", "0", MM::Integer, false, new CPropertyAction(this, &CPiStage::OnGoHomeProp), false);

	SetErrorText(1, "Could not initialize motor (Z stage)");
}

CPiStage::~CPiStage()
{
}

int CPiStage::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	// Index derived via magic. (The Z stage is presumed to be the 3rd index in numerical order.)
	return OnSerialGeneric(pProp, eAct, *GetCoreCallback(), *this, serial_, false, 2);
}

int CPiStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnVelocityGeneric(pProp, eAct, handle_, velocity_, &piGetMotorVelocity, &piSetMotorVelocity);
}

int CPiStage::OnGoHomeProp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if(handle_ == NULL)
		return (eAct == MM::BeforeGet ? DEVICE_OK : DEVICE_ERR);

	long lval = -1;
	if(!(pProp->Get(lval)))
		return DEVICE_ERR;

	switch(eAct)
	{
	case MM::AfterSet:
		{
			if(lval == 1)
			{
				return InterpretPiUsbError(piHomeMotor(10, handle_));
			}
			else
			{
				pProp->Set((long)0);
				return DEVICE_OK;
			}
		};
	case MM::BeforeGet:
		{
			if(lval == 1)
			{
				BOOL home = FALSE;
				if(piGetMotorHomeStatus(&home, handle_) != PI_NO_ERROR)
					return DEVICE_ERR;

				pProp->Set((long)(home ? 0 : 1));
			}

			return DEVICE_OK;
		};
	default:
		return DEVICE_OK; // Don't care.
	};
}

bool CPiStage::Busy()
{
	if(handle_ == NULL)
		return false;

	long homing = 0;
	if(GetProperty("GoHome", homing) == DEVICE_OK && homing == 1)
	{
		BOOL home = FALSE;
		if(piGetMotorHomeStatus(&home, handle_) != PI_NO_ERROR)
			return false;

		return !home;
	}

	BOOL moving;
	if (piGetMotorMovingStatus(&moving, handle_) == PI_NO_ERROR)
		return moving != 0;

	return false;
}

int CPiStage::Initialize()
{
	if(handle_ != NULL)
		Shutdown();

	int pi_error = PI_NO_ERROR;
	handle_ = piConnectMotor(&pi_error, serial_);

	if (handle_ != NULL && pi_error == PI_NO_ERROR)
		pi_error = piGetMotorVelocity(&velocity_, handle_);
	else
		LogMessage(VarFormat("Could not initialize motor %i (PiUsb error code %i)", serial_, pi_error));

	return InterpretPiUsbError(pi_error);
}

int CPiStage::Shutdown()
{
	if (handle_ != NULL) {
		piDisconnectMotor(handle_);
		handle_ = NULL;
	}

	return DEVICE_OK;
}

void CPiStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_StageDeviceName);
}

double CPiStage::GetStepSizeUm()
{
	double out = MOTOR_STEP_SIZE;

	if(GetProperty(g_Keyword_StepSize, out) != DEVICE_OK)
		return MOTOR_STEP_SIZE;

	return out;
}

int CPiStage::SetPositionUm(double pos)
{
	return SetPositionSteps(static_cast<long>(pos / GetStepSizeUm()));
}

int CPiStage::GetPositionUm(double& pos)
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	int error;
	long position = 0;
	if ((error = GetPositionSteps(position)) == DEVICE_OK)
		pos = position * GetStepSizeUm();

	return error;
}

int CPiStage::SetPositionSteps(long steps)
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	long min = MOTOR_LOWER_LIMIT_STEPS, max = MOTOR_UPPER_LIMIT_STEPS;
	int error = DEVICE_OK;

	if((error = GetStepLimits(min, max)) != DEVICE_OK)
		return error;

	steps = steps < min ? min : (steps > max ? max : steps); // Clamp to min..max

	int to = static_cast<int>(steps);

	int pi_error = piRunMotorToPosition(to, velocity_, handle_);

	if(pi_error != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error);

	int at = 0;
	if((pi_error = piGetMotorPosition(&at, handle_)) != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error);

	// WORKAROUND: piRunMotorToPosition doesn't wait for the motor to get
	// underway. Wait a bit here.
	if(at != to) {
		clock_t start = clock();
		clock_t last = start;
		while(!Busy() && at != to && CLOCKDIFF(last = clock(), start) < MAX_WAIT) {
			CDeviceUtils::SleepMs(0);

			if((pi_error = piGetMotorPosition(&at, handle_)) != PI_NO_ERROR)
				return InterpretPiUsbError(pi_error);
		};

		if(CLOCKDIFF(last, start) >= MAX_WAIT)
			LogMessage(VarFormat("Long wait (Z stage): %d / %d (%d != %d).", last - start, static_cast<int>(MAX_WAIT*CLOCKS_PER_SEC), at, to), true);
	};

	return InterpretPiUsbError(pi_error);
}

int CPiStage::GetPositionSteps(long& steps)
{
	if(handle_ == NULL)
		return DEVICE_NOT_CONNECTED;

	int position, pi_error;
	if ((pi_error = piGetMotorPosition(&position, handle_)) == PI_NO_ERROR)
		steps = static_cast<long>(position);

	return InterpretPiUsbError(pi_error);
}

int CPiStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int CPiStage::GetLimits(double& lower, double& upper)
{
	int error = DEVICE_OK;

	if((error = GetProperty(g_Keyword_Min, lower)) != DEVICE_OK)
		return error;

	if((error = GetProperty(g_Keyword_Max, upper)) != DEVICE_OK)
		return error;

	return DEVICE_OK;
}

int CPiStage::GetStepLimits(long& lower, long& upper)
{
	double low, high, stepsize = GetStepSizeUm();
	int error;

	if((error = GetLimits(low, high)) != DEVICE_OK)
		return error;

	lower = static_cast<long>(low / stepsize);
	upper = static_cast<long>(high / stepsize);

	return DEVICE_OK;
}

int CPiStage::IsStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}

bool CPiStage::IsContinuousFocusDrive() const
{
	return false;
}

// The XY Stage
enum XYSTAGE_ERRORS {
	XYERR_INIT_X = PICARDSTAGE_ERROR_OFFSET,
	XYERR_INIT_Y,
	XYERR_MOVE_X,
	XYERR_MOVE_Y
};

CPiXYStage::CPiXYStage()
: serialX_(DEFAULT_SERIAL_UNKNOWN), serialY_(DEFAULT_SERIAL_UNKNOWN), handleX_(NULL), handleY_(NULL)
{
	CreateProperty(g_Keyword_SerialNumberX, FIXED_TO_STRING(DEFAULT_SERIAL_UNKNOWN), MM::Integer, false, new CPropertyAction (this, &CPiXYStage::OnSerialNumberX), true);
	CreateProperty(g_Keyword_SerialNumberY, FIXED_TO_STRING(DEFAULT_SERIAL_UNKNOWN), MM::Integer, false, new CPropertyAction (this, &CPiXYStage::OnSerialNumberY), true);

	SetErrorText(XYERR_INIT_X, "Could not initialize motor (X stage)");
	SetErrorText(XYERR_INIT_Y, "Could not initialize motor (Y stage)");
	SetErrorText(XYERR_MOVE_X, "X stage out of range.");
	SetErrorText(XYERR_MOVE_Y, "Y stage out of range.");

	CreateProperty(g_Keyword_VelocityX, FIXED_TO_STRING(MOTOR_MAX_VELOCITY), MM::Integer, false, new CPropertyAction(this, &CPiXYStage::OnVelocityX), false);
	CreateProperty(g_Keyword_VelocityY, FIXED_TO_STRING(MOTOR_MAX_VELOCITY), MM::Integer, false, new CPropertyAction(this, &CPiXYStage::OnVelocityY), false);

	std::vector<std::string> allowed_values = std::vector<std::string>();
	GenerateAllowedVelocities(allowed_values);
	SetAllowedValues(g_Keyword_VelocityX, allowed_values);
	SetAllowedValues(g_Keyword_VelocityY, allowed_values);

	CreateProperty(g_Keyword_MinX, FIXED_TO_STRING(MOTOR_LOWER_LIMIT), MM::Integer, false, NULL, true);
	CreateProperty(g_Keyword_MaxX, FIXED_TO_STRING(MOTOR_UPPER_LIMIT), MM::Integer, false, NULL, true);
	CreateProperty(g_Keyword_MinY, FIXED_TO_STRING(MOTOR_LOWER_LIMIT), MM::Integer, false, NULL, true);
	CreateProperty(g_Keyword_MaxY, FIXED_TO_STRING(MOTOR_UPPER_LIMIT), MM::Integer, false, NULL, true);

	CreateProperty(g_Keyword_StepSizeX, FIXED_TO_STRING(MOTOR_STEP_SIZE), MM::Float, false, NULL, true);
	CreateProperty(g_Keyword_StepSizeY, FIXED_TO_STRING(MOTOR_STEP_SIZE), MM::Float, false, NULL, true);
}

CPiXYStage::~CPiXYStage()
{
}

int CPiXYStage::InitStage(void** handleptr, int newserial)
{
	int* velptr = NULL;

	if(handleptr == &handleX_) {
		serialX_ = newserial;
		velptr = &velocityX_;
	} else if(handleptr == &handleY_) {
		serialY_ = newserial;
		velptr = &velocityY_;
	} else {
		return DEVICE_INTERNAL_INCONSISTENCY;
	};

	if(*handleptr != NULL)
		ShutdownStage(handleptr);

	int pi_error = PI_NO_ERROR;
	*handleptr = piConnectMotor(&pi_error, newserial); // assignment intentional

	if(*handleptr != NULL && pi_error == PI_NO_ERROR)
		pi_error = piGetMotorVelocity(velptr, *handleptr);
	else
		LogMessage(VarFormat("Could not initialize motor %d (PiUsb error code %d)\n", newserial, pi_error));

	return InterpretPiUsbError(pi_error);
}

void CPiXYStage::ShutdownStage(void** handleptr)
{
	if(*handleptr != NULL)
		piDisconnectMotor(*handleptr);

	*handleptr = NULL;
}

int CPiXYStage::OnSerialNumberX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnSerialGeneric(pProp, eAct, *GetCoreCallback(), *this, serialX_, false, 0); // X is (usually) the first stage serial.
}

int CPiXYStage::OnSerialNumberY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	return OnSerialGeneric(pProp, eAct, *GetCoreCallback(), *this, serialY_, false, 1); // And Y is (usually) the second stage serial.
}

int CPiXYStage::OnVelocityX(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	return OnVelocityGeneric(pProp, eAct, handleX_, velocityX_, &piGetMotorVelocity, &piSetMotorVelocity);
}

int CPiXYStage::OnVelocityY(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	return OnVelocityGeneric(pProp, eAct, handleY_, velocityY_, &piGetMotorVelocity, &piSetMotorVelocity);
}

bool CPiXYStage::Busy()
{
	if(handleX_ == NULL || handleY_ == NULL)
		return false;

	BOOL movingX = FALSE, movingY = FALSE;

	if (handleX_)
		if(piGetMotorMovingStatus(&movingX, handleX_) != PI_NO_ERROR)
			return false;

	if (handleY_)
		if(piGetMotorMovingStatus(&movingY, handleY_) != PI_NO_ERROR)
			return false;

	return movingX != FALSE || movingY != FALSE;
}

int CPiXYStage::Initialize()
{
	int error = 0;

	if(serialX_ != DEFAULT_SERIAL_UNKNOWN)
		if((error = InitStage(&handleX_, serialX_)) != DEVICE_OK)
			return error;

	if(serialY_ != DEFAULT_SERIAL_UNKNOWN)
		if((error = InitStage(&handleY_, serialY_)) != DEVICE_OK)
			return error;

	return DEVICE_OK;
}

int CPiXYStage::Shutdown()
{
	ShutdownStage(&handleX_);
	ShutdownStage(&handleY_);
	return 0;
}

void CPiXYStage::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

int CPiXYStage::SetPositionUm(double x, double y)
{
	return SetPositionSteps(static_cast<long>(x / GetStepSizeXUm()), static_cast<int>(y / GetStepSizeYUm()));
}

int CPiXYStage::GetPositionUm(double& x, double& y)
{
	int error;
	long posX = MOTOR_LOWER_LIMIT_STEPS, posY = MOTOR_UPPER_LIMIT_STEPS;

	if((error = GetPositionSteps(posX, posY)) != DEVICE_OK)
		return error;

	x = posX * GetStepSizeXUm();
	y = posY * GetStepSizeYUm();

	return DEVICE_OK;
}

int CPiXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
	int error = DEVICE_OK;

	if((error = GetProperty(g_Keyword_MinX, xMin)) != DEVICE_OK)
		return error;

	if((error = GetProperty(g_Keyword_MaxX, xMax)) != DEVICE_OK)
		return error;

	if((error = GetProperty(g_Keyword_MinY, yMin)) != DEVICE_OK)
		return error;

	if((error = GetProperty(g_Keyword_MaxY, yMax)) != DEVICE_OK)
		return error;

	return DEVICE_OK;
}

int CPiXYStage::SetPositionSteps(long x, long y)
{
	if(handleX_ == NULL || handleY_ == NULL)
		return DEVICE_NOT_CONNECTED;

	long minX = MOTOR_LOWER_LIMIT_STEPS, maxX = MOTOR_UPPER_LIMIT_STEPS, minY = MOTOR_LOWER_LIMIT_STEPS, maxY = MOTOR_UPPER_LIMIT_STEPS;
	int error = DEVICE_OK;

	if((error = GetStepLimits(minX, maxX, minY, maxY)) != DEVICE_OK)
		return error;

	x = x < minX ? minX : (x > maxX ? maxX : x);
	y = y < minY ? minY : (y > maxY ? maxY : y);

	int toX = static_cast<int>(x);
	int toY = static_cast<int>(y);

	int pi_error_x = piRunMotorToPosition(toX, velocityX_, handleX_);
	int pi_error_y = piRunMotorToPosition(toY, velocityY_, handleY_) << 1;

	int atX, atY;

	if((pi_error_x = piGetMotorPosition(&atX, handleX_)) != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error_x);

	if((pi_error_y = piGetMotorPosition(&atY, handleY_)) != PI_NO_ERROR)
		return InterpretPiUsbError(pi_error_y);

	if(atX != toX || atY != toY) {
		clock_t start = clock();
		clock_t last = start;
		while(!Busy() && (atX != toX || atY != toY) && CLOCKDIFF(last = clock(), start) < MAX_WAIT) {
			CDeviceUtils::SleepMs(0);

			if((pi_error_x = piGetMotorPosition(&atX, handleX_)) != PI_NO_ERROR)
				return InterpretPiUsbError(pi_error_x);

			if((pi_error_y = piGetMotorPosition(&atY, handleY_)) != PI_NO_ERROR)
				return InterpretPiUsbError(pi_error_y);
		};

		if(CLOCKDIFF(last, start) >= MAX_WAIT)
			LogMessage(VarFormat("Long wait (XY): %d / %d (%d != %d || %d != %d).", last - start, static_cast<int>(MAX_WAIT*CLOCKS_PER_SEC), atX, toX, atY, toY), true);
	};

	return InterpretPiUsbError(pi_error_x != PI_NO_ERROR ? pi_error_x : pi_error_y);
}

int CPiXYStage::GetPositionSteps(long& x, long& y)
{
	if(handleX_ == NULL || handleY_ == NULL)
		return DEVICE_ERR;

	int positionX = MOTOR_LOWER_LIMIT_STEPS, positionY = MOTOR_LOWER_LIMIT_STEPS;
	if (piGetMotorPosition(&positionX, handleX_) ||
			piGetMotorPosition(&positionY, handleY_))
		return DEVICE_ERR;

	x = static_cast<long>(positionX);
	y = static_cast<long>(positionY);

	return DEVICE_OK;
}

int CPiXYStage::Home()
{
	if(handleX_ == NULL || handleY_ == NULL)
		return DEVICE_NOT_CONNECTED;

	int error;

	if((error = piHomeMotor(velocityX_, handleX_)) != PI_NO_ERROR)
		return InterpretPiUsbError(error);

	if((error = piHomeMotor(velocityY_, handleY_)) != PI_NO_ERROR)
		return InterpretPiUsbError(error);

	return DEVICE_OK;
}

int CPiXYStage::Stop()
{
	if(handleX_ == NULL || handleY_ == NULL)
		return DEVICE_NOT_CONNECTED;

	int error;

	if((error = piHaltMotor(handleX_)) != PI_NO_ERROR)
		return InterpretPiUsbError(error);

	if((error = piHaltMotor(handleY_)) != PI_NO_ERROR)
		return InterpretPiUsbError(error);

	return DEVICE_OK;
}

int CPiXYStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}

int CPiXYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	int error;
	double xMinUm, xMaxUm, yMinUm, yMaxUm;
	double stepsizeX = GetStepSizeXUm(), stepsizeY = GetStepSizeYUm();

	if((error = GetLimitsUm(xMinUm, xMaxUm, yMinUm, yMaxUm)) != DEVICE_OK)
		return error;

	xMin = static_cast<long>(xMinUm / stepsizeX);
	xMax = static_cast<long>(xMaxUm / stepsizeX);
	yMin = static_cast<long>(yMinUm / stepsizeY);
	yMax = static_cast<long>(yMaxUm / stepsizeY);

	return DEVICE_OK;
}

double CPiXYStage::GetStepSizeXUm()
{
	double out = MOTOR_STEP_SIZE;

	if(GetProperty(g_Keyword_StepSizeX, out) != DEVICE_OK)
		return MOTOR_STEP_SIZE;

	return out;
}

double CPiXYStage::GetStepSizeYUm()
{
	double out = MOTOR_STEP_SIZE;

	if(GetProperty(g_Keyword_StepSizeY, out) != DEVICE_OK)
		return MOTOR_STEP_SIZE;

	return out;
}

int CPiXYStage::IsXYStageSequenceable(bool& isSequenceable) const
{
	isSequenceable = false;
	return DEVICE_OK;
}
