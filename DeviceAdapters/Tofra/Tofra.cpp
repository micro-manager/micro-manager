///////////////////////////////////////////////////////////////////////////////
// FILE:          Tofra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for TOFRA devices
//
// COPYRIGHT:     TOFRA, Inc., Palo Alto, CA
//                University of California, San Francisco, CA
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Ilya Ravkin, based on Code by Nenad Amodaj and Nico Stuurman
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Tofra.h"
#include <boost/lexical_cast.hpp>
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>
using namespace std;

const char* g_WheelDeviceName = "TOFRA Filter Wheel";
const char* g_ZStageDeviceName = "TOFRA Z-Drive";
const char* g_XYStageDeviceName = "TOFRA XYStage";
const char* g_SliderDeviceName = "TOFRA Cube Slider";
const char* g_rgbLEDDeviceName = "TOFRA RGB LED";
const char* g_RGBLEDShutterDeviceName = "TOFRA RGB LED Shutter";


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_WheelDeviceName, MM::StateDevice, "TOFRA Filter Wheel with Integrated Controller");
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "TOFRA Z-Drive with Integrated Controller");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "TOFRA XYStage with Integrated Controller");
   RegisterDevice(g_SliderDeviceName, MM::StateDevice, "TOFRA Filter Cube Slider with Integrated Controller");
   RegisterDevice(g_rgbLEDDeviceName, MM::StateDevice, "TOFRA RGB LED Light Source");
	RegisterDevice(g_RGBLEDShutterDeviceName, MM::ShutterDevice,
			"TOFRA RGB LED as Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0) return 0;
 
	if (strcmp(deviceName, g_WheelDeviceName) == 0){
		// create filter wheel
		return new TofraFilterWheel();
	}
	else if (strcmp(deviceName, g_ZStageDeviceName) == 0){
		// create Z-Drive
		return new ZStage();
	}
	else if (strcmp(deviceName, g_XYStageDeviceName) == 0){
		// create XYStage
		return new XYStage();
	}
	else if (strcmp(deviceName, g_SliderDeviceName) == 0){
		// create CubeSlider
		return new CubeSlider();
	}
	else if (strcmp(deviceName, g_rgbLEDDeviceName) == 0){
		// create rgbLED
		return new rgbLED();
	}
	else if (strcmp(deviceName, g_RGBLEDShutterDeviceName) == 0) {
		return new RGBLEDShutter();
	}
	// ...supplied name not recognized
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// TOFRA Filter Wheel Implementation
///////////////////////////////////////////////////////////////////////////////

TofraFilterWheel::TofraFilterWheel() : 
	ControllerName("1"),
	HomeOffset(0), // offset from flag to pos 1 in microsteps, usually in [-4, +2] 
	SlewVelocity(5000), // microsteps/sec; default value assumes microstep = 1/16 of step
	InitVelocity(500),  // microsteps/sec; default value assumes microstep = 1/16 of step
	Acceleration(10),
	HoldCurrent(5), // in % of max (which is 2A)
	RunCurrent (60),
	NumPos(10), 
	initialized_(false), 
	changedTime_(0.0),
	position_(0),
	port_("")
{
	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	CPropertyAction* pAct;
	// Name
	CreateProperty(MM::g_Keyword_Name, g_WheelDeviceName, MM::String, true);
	// Description
	CreateProperty(MM::g_Keyword_Description, "TOFRA Filter Wheel with Integrated Controller 10, 12, 18 or 22 pos.", MM::String, true);
	// Com port
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnCOMPort);
	CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
	// Number of positions
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnNumPos);
	CreateProperty("NumPos", "10", MM::Integer, false, pAct, true);
	// ControllerName - Name (number) of the motor controller on the serial line
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnControllerName);
	CreateProperty("ControllerName", "1", MM::String, false, pAct, true);
	// HomeOffset
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnHomeOffset);
	CreateProperty("HomeOffset", "0", MM::Integer, false, pAct, true);
	// SlewVelocity
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnSlewVelocity);
	CreateProperty("SlewVelocity", "5000", MM::Integer, false, pAct, true);
	// InitVelocity
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnInitVelocity);
	CreateProperty("InitVelocity", "500", MM::Integer, false, pAct, true);
	// Acceleration
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnAcceleration);
	CreateProperty("Acceleration", "10", MM::Integer, false, pAct, true);
	// HoldCurrent
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnHoldCurrent);
	CreateProperty("HoldCurrent", "5", MM::Integer, false, pAct, true);
	// RunCurrent
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnRunCurrent);
	CreateProperty("RunCurrent", "60", MM::Integer, false, pAct, true);
}

TofraFilterWheel::~TofraFilterWheel()
{
   Shutdown();
}

void TofraFilterWheel::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_WheelDeviceName);
}

int TofraFilterWheel::Initialize()
{
	int ret;
	CPropertyAction* pAct;

	// State
	pAct = new CPropertyAction (this, &TofraFilterWheel::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Label
	pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Create default positions and labels
	const int bufSize = 1024;
	char buf[bufSize];
	for (long i=0; i<NumPos; i++)
	{
		snprintf(buf, bufSize, "Filter-%.2ld", i + 1);
		SetPositionLabel(i, buf);
	}

	// Update Status 
	ret = UpdateStatus();
	if (ret != DEVICE_OK) return ret;

	// Initialize and Home	
	position_ = 0;
	ret = InitializeFilterWheel();
	if (ret != DEVICE_OK) return ret;

	changedTime_ = GetCurrentMMTime();
	initialized_ = true;
	return DEVICE_OK;
}

int TofraFilterWheel::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool TofraFilterWheel::Busy()
{
	// In case of error we postulate that device is not busy
	// Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return false;

	// Query current status
	const int bufSize = 20;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sQ", ControllerName.c_str());
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return false;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return false;
	if (answer.substr(ret,1).compare("@") == 0) return true;
	return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers - Filter Wheel
///////////////////////////////////////////////////////////////////////////////

int TofraFilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(position_);
		// nothing to do, let the caller use the cached property
   }
   else if (eAct == MM::AfterSet)
   {
		// Set timer for the Busy signal
		changedTime_ = GetCurrentMMTime();

		long pos;
		pProp->Get(pos);
		if (pos >= NumPos || pos < 0)
		{
			pProp->Set(position_);
			return ERR_UNKNOWN_POSITION;
		}
		if (position_ == pos) return DEVICE_OK;
		
		// form relative move command
		double mstepsperfilt;
		int msfiltpos[50],d1,d2,d;
		const int bufSize = 40;
		const int turnmsteps = 3200;
		char buf[bufSize];
		mstepsperfilt = (double)turnmsteps/(double)NumPos;
		for (int i=0; i<NumPos; i++)
		{
			msfiltpos[i] = (int)floor(mstepsperfilt*(double)i+0.5);
		}
		d1 = msfiltpos[pos] - msfiltpos[position_];
		if (d1>0) {
			d2 = d1-turnmsteps;
		}
		else {
			d2 = turnmsteps - d1;
		}
		if(abs(d1)>abs(d2)) {
			d = d2;
		}
		else {
			d = d1;
		}

		if (d>0) {
			snprintf(buf, bufSize, "/%sP%dR", ControllerName.c_str(), d);
		} else {
			snprintf(buf, bufSize, "/%sD%dR", ControllerName.c_str(), -d);
		}

		// Clear serial port from previous stuff
		int ret = ClearPort(*this, *GetCoreCallback(), port_);
		if (ret != DEVICE_OK) return ret;

		// Send move command
		ret = SendSerialCommand(port_.c_str(),buf,"\r");
		if (ret != DEVICE_OK) return ret;

		// Read and analyze response
		std::string answer;
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) return ret;
		ret = AnalyzeAnswer(answer);
		if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

		position_ = pos;
   }
   return DEVICE_OK;
}

int TofraFilterWheel::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str()); // revert
      }
      pProp->Get(port_);                                                     
   }                                                                        
   return DEVICE_OK;                                                         
} 

int TofraFilterWheel::OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(ControllerName.c_str());
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(ControllerName);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnHomeOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(HomeOffset);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(HomeOffset);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SlewVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SlewVelocity);  
		//changedTime_ = GetCurrentMMTime();
		//const int bufSize = 40;
		//char cbuf[bufSize];
		//snprintf(cbuf,bufSize, "/%sV%ldR", ControllerName.c_str(), SlewVelocity);
		//int ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
		//if (ret != DEVICE_OK) return ret;
		//std::string answer;
		//ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		//if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(InitVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(InitVelocity);  
		//changedTime_ = GetCurrentMMTime();
		//const int bufSize = 40;
		//char cbuf[bufSize];
		//// snprintf(cbuf,bufSize, "/%sv%ldR", ControllerName.c_str(), InitVelocity);
		//int ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
		//if (ret != DEVICE_OK) return ret;
		//std::string answer;
		//ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		//if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Acceleration);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Acceleration);  
		//changedTime_ = GetCurrentMMTime();
		//const int bufSize = 40;
		//char cbuf[bufSize];
		//// snprintf(cbuf,bufSize, "/%sL%ldR", ControllerName.c_str(), Acceleration);
		//int ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
		//if (ret != DEVICE_OK) return ret;
		//std::string answer;
		//ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		//if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HoldCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HoldCurrent);  
		//changedTime_ = GetCurrentMMTime();
		//const int bufSize = 40;
		//char cbuf[bufSize];
		//// snprintf(cbuf,bufSize, "/%sh%ldR", ControllerName.c_str(), HoldCurrent);
		//int ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
		//if (ret != DEVICE_OK) return ret;
		//std::string answer;
		//ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		//if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(RunCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(RunCurrent);  
		//changedTime_ = GetCurrentMMTime();
		//const int bufSize = 40;
		//char cbuf[bufSize];
		//// snprintf(cbuf,bufSize, "/%sm%ldR", ControllerName.c_str(), RunCurrent);
		//int ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
		//if (ret != DEVICE_OK) return ret;
		//std::string answer;
		//ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		//if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int TofraFilterWheel::OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(NumPos);
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(NumPos);    
		// (re)create default positions and labels
		const int bufSize = 1024;
		char buf[bufSize];
		for (long i=0; i<NumPos; i++)
		{
			snprintf(buf, bufSize, "Filter-%.2ld", i + 1);
			SetPositionLabel(i, buf);
		}
		// Update Status - what does it do?
		int ret = UpdateStatus();
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 

int TofraFilterWheel::InitializeFilterWheel()
{  
	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// Set controller parameters and do homing
	const int bufSize = 200;
	char cbuf[bufSize],hbuf[bufSize];

	// Form command for home offset
	if(HomeOffset>0) snprintf(hbuf,bufSize,"D%ld",HomeOffset);
	else if(HomeOffset<0) snprintf(hbuf,bufSize,"P%ld",-HomeOffset);
	else snprintf(hbuf,bufSize,"");
	
	// Form command string to set current values of parameters and home the wheel
	// Homing is at "higher speed" to the beginning of flag, then at "lower speed" to the end of flag
	snprintf(cbuf,bufSize, "/%sj16h%ldm%ldV%ldv%ldL%ldf0n0gD10S13G0D1gD1S03G0%sR",ControllerName.c_str(),HoldCurrent,RunCurrent,SlewVelocity,InitVelocity,Acceleration,hbuf);
	ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
	// Change for faster homing ...D40... or ...D20... instead of ...D10...
	// ret = SendSerialCommand(port_.c_str(),"/1j16h5m60V5000v500L12f0n0gD40S13G0D1gD1S03G0D2R","\r");
	// Default parameters:
	// Parameters:  "/1j16h5m60V5000v500L12f0n0R"
	// Homing:  "/1gD10S13G0D1gD1S03G0D2R"
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}

int AnalyzeAnswer(std::string answer)
{
	//Search for /0 in answer and return index after it
	//basic_string <char>::size_type ind;
	size_t ind;
	ind = answer.find("/0",0);
	if (ind == string::npos) return ERR_BAD_CONTROLLER_RESPONSE;
	return (int)ind+2;
}

int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int isnumeric(const char *str)
{
	while(*str)
	{
		if(!(isdigit(*str)||(strncmp(str,".",1)==0))) return 0;
		str++;
	}
	return 1;
}

///////////////////////////////////////////////////////////////////////////////
// TOFRA Z Drive Implementation
///////////////////////////////////////////////////////////////////////////////

ZStage::ZStage() :
	ControllerName("2"),
	SlewVelocity(40),	// microns/sec
	InitVelocity(4),	// microns/sec
	Acceleration(1),	// microns/sec^2
	HoldCurrent(5),	 // in % of max (which is 2A)
	RunCurrent (25), // in % of max (which is 2A)
	MotorSteps(400), // steps in one revolution
	FullTurnUm(100), // microns per full turn of fine focus knob
	WithLimits(0),	 // 1 if Z-drive includes limits
	Position(""),	 // Numeric or symbolic (ORIGIN, HOME, LIM1, LIM2) position
	Speed(0),		 // Speed for continuous move in microns/sec; to stop set to 0
	initialized_(false),
	changedTime_(0.0),   
	stepSizeUm_(0.0009765625), //assuming 100um full turn of focus knob and 400 step motor; actual value is calculated
	port_("")
{
   InitializeDefaultErrorMessages();

	// create pre-initialization properties
	CPropertyAction* pAct;
	// Name
	CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);
	// Description
	CreateProperty(MM::g_Keyword_Description, "TOFRA Z-Drive with Integrated Controller", MM::String, true);
	// Port
	pAct = new CPropertyAction (this, &ZStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
	// ControllerName - Name (number) of the motor controller on the serial line
	pAct = new CPropertyAction (this, &ZStage::OnControllerName);
	CreateProperty("ControllerName", "2", MM::String, false, pAct, true);
	// SlewVelocity
	pAct = new CPropertyAction (this, &ZStage::OnSlewVelocity);
	CreateProperty("SlewVelocity", "40", MM::Float, false, pAct, true);
	// InitVelocity
	pAct = new CPropertyAction (this, &ZStage::OnInitVelocity);
	CreateProperty("InitVelocity", "4", MM::Float, false, pAct, true);
	// Acceleration
	pAct = new CPropertyAction (this, &ZStage::OnAcceleration);
	CreateProperty("Acceleration", "1", MM::Float, false, pAct, true);
	// HoldCurrent
	pAct = new CPropertyAction (this, &ZStage::OnHoldCurrent);
	CreateProperty("HoldCurrent", "5", MM::Integer, false, pAct, true); // set to 0 for easy manual operation of the fine focus
	// RunCurrent
	pAct = new CPropertyAction (this, &ZStage::OnRunCurrent);
	CreateProperty("RunCurrent", "25", MM::Integer, false, pAct, true);
	// MotorSteps
	pAct = new CPropertyAction (this, &ZStage::OnMotorSteps);
	CreateProperty("MotorSteps", "400", MM::Integer, false, pAct, true);
	// FullTurnUm
	pAct = new CPropertyAction (this, &ZStage::OnFullTurnUm);
	CreateProperty("FullTurnUm", "100", MM::Integer, false, pAct, true);
	// WithLimits
	pAct = new CPropertyAction (this, &ZStage::OnWithLimits);
	CreateProperty("WithLimits", "0", MM::Integer, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
	// create pre-initialization properties
	CPropertyAction* pAct;

   // Position - Numeric or symbolic value of position
	pAct = new CPropertyAction (this, &ZStage::OnPosition);
	CreateProperty("Position", "", MM::String, false, pAct);
   // Out1 - Digital output 1 
	pAct = new CPropertyAction (this, &ZStage::OnOut1);
	CreateProperty("Out1", "", MM::Integer, false, pAct);
	// Out2 - Digital output 2 
	pAct = new CPropertyAction (this, &ZStage::OnOut2);
	CreateProperty("Out2", "", MM::Integer, false, pAct);
	// Execute
	pAct = new CPropertyAction (this, &ZStage::OnExecute);
	CreateProperty("Execute", "", MM::String, false, pAct);
	// Speed for continuous move
	pAct = new CPropertyAction (this, &ZStage::OnSpeed);
	CreateProperty("Speed", "", MM::Float, false, pAct);

	int ret = UpdateStatus();
	if (ret != DEVICE_OK) return ret;

	ret = InitializeZDrive();
	if (ret != DEVICE_OK) return ret;

	changedTime_ = GetCurrentMMTime();   
	initialized_ = true;
	return DEVICE_OK;
}

int ZStage::Shutdown()
{
	if (initialized_) initialized_ = false;
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers - Z Drive
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str()); // revert
      }
      pProp->Get(port_);
	}
	return DEVICE_OK;
}
int ZStage::OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(ControllerName.c_str());
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(ControllerName);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int ZStage::OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SlewVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SlewVelocity);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(InitVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(InitVelocity);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Acceleration);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Acceleration);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HoldCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HoldCurrent);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(RunCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(RunCurrent);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnMotorSteps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(MotorSteps);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(MotorSteps);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnFullTurnUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(FullTurnUm);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(FullTurnUm);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnWithLimits(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(WithLimits);
	}
	else if (eAct == MM::AfterSet) {
		long wthlim;
		pProp->Get(wthlim);
		if (wthlim != 0) wthlim = 1;
		WithLimits = wthlim;
		pProp->Set(WithLimits);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		double pos;
		const int bufSize = 40;
		char buf[bufSize];
		int ret = GetPositionUm(pos);
		if (ret != DEVICE_OK) return ret;
		snprintf(buf, bufSize, "%f", pos);
		pProp->Set(buf);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Position);   
		if(Position.compare("ORIGIN") == 0) return SetOrigin();
		if(Position.compare("HOME") == 0) return Home();
//		if(Position.compare("LIM1") == 0) return ToLimit(1);
//		if(Position.compare("LIM2") == 0) return ToLimit(2);
		if(isnumeric(Position.c_str())){
			double pos;
			pos = atof(Position.c_str());
			return SetPositionUm(pos);
		}
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Speed);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Speed);
		int ret = Move(Speed);
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
} 
int ZStage::OnOut1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out1);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out1);  
		int ret = Out();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int ZStage::OnOut2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out2);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out2);  
		int ret = Out();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int ZStage::OnExecute(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
		pProp->Set(Execute.c_str());
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(Execute);   
		int ret = SendCommand(Execute.c_str());
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
////////////////////////////////////////////////////////////////

bool ZStage::Busy()
{
	// In case of error we postulate that device is not busy
	// Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return false;

	// Query current status
	const int bufSize = 20;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sQ", ControllerName.c_str());
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return false;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return false;
	if (answer.substr(ret,1).compare("@") == 0) return true;
	return false;
}

int ZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   return SetPositionSteps(steps);
}

int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK) return ret;
   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// form absolute move command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sA%ldR", ControllerName.c_str(), pos);

	// send command
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
  
int ZStage::GetPositionSteps(long& steps)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// send command to read position
   	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf,bufSize, "/%s?0", ControllerName.c_str());
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;
	steps = atol(answer.substr(ret+1,answer.length()-ret-1).c_str());
	return DEVICE_OK;
}

int ZStage::SetOrigin()
{
	// Form origin command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sz0R", ControllerName.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}

int ZStage::GetLimits(double& min, double& max)
{
	// Placeholder for now
	min = 0.0;
	max = 10000.0;
	return DEVICE_OK;
}

int ZStage::Stop()
{
	// Form stop command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sT", ControllerName.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}

int ZStage::Home()
{
	if (WithLimits==0) return SetOrigin();
	// Form home command (will use SlewVelocity as speed)
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sZ1000000000R", ControllerName.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int ZStage::SetRelativePositionUm(double d)
{
	if(d==0) return DEVICE_OK;
	long steps = (long) (d / stepSizeUm_ + 0.5);

	// Form relative move command
	const int bufSize = 40;
	char buf[bufSize];
	if(steps>0) snprintf(buf, bufSize, "/%sP%ldR", ControllerName.c_str(), steps);
	if(steps<0) snprintf(buf, bufSize, "/%sD%ldR", ControllerName.c_str(), -steps);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int ZStage::Move(double speed)
{
  	// Speed is in um/sec
	// This will change max velocity, so after finished in velocity mode - initialize
	if(speed==0) return Stop();
	long stepspeed = (long) (speed / stepSizeUm_ + 0.5);

	// Form constant speed move command
	const int bufSize = 40;
	char buf[bufSize];
	if(stepspeed>0) snprintf(buf, bufSize, "/%sV%ldP0R", ControllerName.c_str(), stepspeed);
	if(stepspeed<0) snprintf(buf, bufSize, "/%sV%ldD0R", ControllerName.c_str(), -stepspeed);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int ZStage::Out()
{
  	// Two outputs 1 and 2 with values 0 and 1
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sJ%ldR", ControllerName.c_str(), Out1+2*Out2);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int ZStage::InitializeZDrive()
{  
	// Calculate step size
	stepSizeUm_ = (double)FullTurnUm/(256.*MotorSteps);

	// From microns to microsteps
	long slvel = (long) (SlewVelocity / stepSizeUm_ + 0.5);
	long invel = (long) (InitVelocity / stepSizeUm_ + 0.5);
	long accel = (long) (Acceleration / stepSizeUm_ + 0.5);

	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// Set controller parameters 
	const int bufSize = 200;
	char buf[bufSize];
//	snprintf(buf,bufSize, "/%sj256h%ldm%ldV%ldv%ldL%ldn0R", ControllerName.c_str(),HoldCurrent,RunCurrent,SlewVelocity,InitVelocity,Acceleration);
	snprintf(buf,bufSize, "/%sj256h%ldm%ldV%ldv%ldL%ldn%ldR", ControllerName.c_str(),HoldCurrent,RunCurrent,slvel,invel,accel,WithLimits*2);
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int ZStage::SendCommand(const char* cmd)
{
	const int bufSize = 1024;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%s%s", ControllerName.c_str(), cmd);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// TOFRA XYStage Implementation
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
	ControllerNameX("3"), // name (number) of controller for X axis
	StepDivideX(256),	 // microsteps in one full step
	SlewVelocityX(1000),	// microns/sec
	InitVelocityX(100),	// microns/sec
	AccelerationX(10),	// microns/sec^2
	HoldCurrentX(5),	 // in % of max (which is 2A)
	RunCurrentX(50), // in % of max (which is 2A)
	MotorStepsX(200), // steps in one revolution
	LeadUmX(1000), // microns per full motor turn (for 1mm lead screw)
	PositionX(""),	 // Numeric or symbolic (ORIGIN, HOME, LIM1, LIM2) position
	SpeedX(0),		 // Speed for continuous move in microns/sec; to stop set to 0
	Out1X(0),	// Output 1
	Out2X(0),	// Output 2
	LimitPolarityX(0), // limit polarity: 0 normally closed, 1 mormally open
	stepSizeUmX_(0.01953125), // assuming 1000um lead and 200 step motor; actual value is calculated
	ControllerNameY("4"), // name (number) of controller for Y axis
	StepDivideY(256),	 // microsteps in one full step
	SlewVelocityY(1000),	// microns/sec
	InitVelocityY(100),	// microns/sec
	AccelerationY(10),	// microns/sec^2
	HoldCurrentY(5),	 // in % of max (which is 2A)
	RunCurrentY(50), // in % of max (which is 2A)
	MotorStepsY(200), // steps in one revolution
	LeadUmY(1000), // microns per full motor turn (for 1mm lead screw)
	PositionY(""),	 // Numeric or symbolic (ORIGIN, HOME, LIM1, LIM2) position
	SpeedY(0),		 // Speed for continuous move in microns/sec; to stop set to 0
	Out1Y(0),	// Output 1
	Out2Y(0),	// Output 2
	LimitPolarityY(0), // limit polarity: 0 normally closed, 1 mormally open
	stepSizeUmY_(0.01953125), // assuming 1000um lead and 200 step motor; actual value is calculated
	initialized_(false),
	changedTime_(0.0),   
	port_("")
{

	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	CPropertyAction* pAct;
	// Name
	CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);
	// Description
	CreateProperty(MM::g_Keyword_Description, "TOFRA XYStage with Integrated Controller", MM::String, true);
	// Port
	pAct = new CPropertyAction (this, &XYStage::OnPort);
	CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
	// ControllerName - Name (number) of the motor controller on the serial line
	pAct = new CPropertyAction (this, &XYStage::OnControllerNameX);
	CreateProperty("ControllerNameX", "3", MM::String, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnControllerNameY);
	CreateProperty("ControllerNameY", "4", MM::String, false, pAct, true);
	// StepDivide
	pAct = new CPropertyAction (this, &XYStage::OnStepDivideX);
	CreateProperty("StepDivideX", "256", MM::Integer, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnStepDivideY);
	CreateProperty("StepDivideY", "256", MM::Integer, false, pAct, true);
	// SlewVelocity
	pAct = new CPropertyAction (this, &XYStage::OnSlewVelocityX);
	CreateProperty("SlewVelocityX", "1000", MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnSlewVelocityY);
	CreateProperty("SlewVelocityY", "1000", MM::Float, false, pAct, true);
	// InitVelocity
	pAct = new CPropertyAction (this, &XYStage::OnInitVelocityX);
	CreateProperty("InitVelocityX", "100", MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnInitVelocityY);
	CreateProperty("InitVelocityY", "100", MM::Float, false, pAct, true);
	// Acceleration
	pAct = new CPropertyAction (this, &XYStage::OnAccelerationX);
	CreateProperty("AccelerationX", "10", MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnAccelerationY);
	CreateProperty("AccelerationY", "10", MM::Float, false, pAct, true);
	// HoldCurrent
	pAct = new CPropertyAction (this, &XYStage::OnHoldCurrentX);
	CreateProperty("HoldCurrentX", "5", MM::Integer, false, pAct, true); 
	pAct = new CPropertyAction (this, &XYStage::OnHoldCurrentY);
	CreateProperty("HoldCurrentY", "5", MM::Integer, false, pAct, true); 
	// RunCurrent
	pAct = new CPropertyAction (this, &XYStage::OnRunCurrentX);
	CreateProperty("RunCurrentX", "50", MM::Integer, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnRunCurrentY);
	CreateProperty("RunCurrentY", "50", MM::Integer, false, pAct, true);
	// MotorSteps
	pAct = new CPropertyAction (this, &XYStage::OnMotorStepsX);
	CreateProperty("MotorStepsX", "200", MM::Integer, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnMotorStepsY);
	CreateProperty("MotorStepsY", "200", MM::Integer, false, pAct, true);
	// LeadUm
	pAct = new CPropertyAction (this, &XYStage::OnLeadUmX);
	CreateProperty("LeadUmX", "1000", MM::Integer, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnLeadUmY);
	CreateProperty("LeadUmY", "1000", MM::Integer, false, pAct, true);
	// Limit polarity
	pAct = new CPropertyAction (this, &XYStage::OnLimitPolarityX);
	CreateProperty("LimitPolarityX", "", MM::Integer, false, pAct, true);
	pAct = new CPropertyAction (this, &XYStage::OnLimitPolarityY);
	CreateProperty("LimitPolarityY", "", MM::Integer, false, pAct, true);
}
XYStage::~XYStage()
{
   Shutdown();
}
void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}
int XYStage::Initialize()
{
   CPropertyAction* pAct;
	// Position - Numeric or symbolic value of position
	pAct = new CPropertyAction (this, &XYStage::OnPositionX);
	CreateProperty("PositionX", "", MM::String, false, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnPositionY);
	CreateProperty("PositionY", "", MM::String, false, pAct);
	// Execute string
	pAct = new CPropertyAction (this, &XYStage::OnExecuteX);
	CreateProperty("ExecuteX", "", MM::String, false, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnExecuteY);
	CreateProperty("ExecuteY", "", MM::String, false, pAct);
   // Speed - for continuous move
	pAct = new CPropertyAction (this, &XYStage::OnSpeedX);
	CreateProperty("SpeedX", "", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
	CreateProperty("SpeedY", "", MM::Float, false, pAct);
	// Out1 - Digital output 1 from stage controller
	pAct = new CPropertyAction (this, &XYStage::OnOut1X);
	CreateProperty("Out1X", "", MM::Integer, false, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnOut1Y);
	CreateProperty("Out1Y", "", MM::Integer, false, pAct);
	// Out2 - Digital output 2 from stage controller
	pAct = new CPropertyAction (this, &XYStage::OnOut2X);
	CreateProperty("Out2X", "", MM::Integer, false, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnOut2Y);
	CreateProperty("Out2Y", "", MM::Integer, false, pAct);

	int ret = UpdateStatus();
	if (ret != DEVICE_OK) return ret;

	ret = InitializeXYStage();
	if (ret != DEVICE_OK) return ret;

	changedTime_ = GetCurrentMMTime();   
	initialized_ = true;
	return DEVICE_OK;
}
int XYStage::Shutdown()
{
	if (initialized_) initialized_ = false;
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers - XYStage
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str()); // revert
      }
      pProp->Get(port_);
	}
	return DEVICE_OK;
}
int XYStage::OnControllerNameX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(ControllerNameX.c_str());
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(ControllerNameX);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int XYStage::OnControllerNameY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(ControllerNameY.c_str());
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(ControllerNameY);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int XYStage::OnSlewVelocityX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SlewVelocityX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SlewVelocityX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnSlewVelocityY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SlewVelocityY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SlewVelocityY);  
	}                                                                         
	return DEVICE_OK;                                                         
}
int XYStage::OnInitVelocityX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(InitVelocityX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(InitVelocityX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnInitVelocityY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(InitVelocityY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(InitVelocityY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnAccelerationX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(AccelerationX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(AccelerationX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnAccelerationY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(AccelerationY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(AccelerationY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnHoldCurrentX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HoldCurrentX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HoldCurrentX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnHoldCurrentY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HoldCurrentY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HoldCurrentY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnRunCurrentX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(RunCurrentX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(RunCurrentX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnRunCurrentY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(RunCurrentY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(RunCurrentY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnMotorStepsX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(MotorStepsX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(MotorStepsX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnMotorStepsY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(MotorStepsY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(MotorStepsY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnLeadUmX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(LeadUmX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(LeadUmX);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnLeadUmY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(LeadUmY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(LeadUmY);  
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnLimitPolarityX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(LimitPolarityX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(LimitPolarityX);  
	}                                                                         
	return DEVICE_OK;
}
int XYStage::OnLimitPolarityY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(LimitPolarityY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(LimitPolarityY);  
	}                                                                         
	return DEVICE_OK;
}
int XYStage::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		double pos;
		const int bufSize = 40;
		char buf[bufSize];
		int ret = GetPositionUmX(pos);
		if (ret != DEVICE_OK) return ret;
		snprintf(buf, bufSize, "%f", pos);
		pProp->Set(buf);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(PositionX);   
		if(PositionX.compare("ORIGIN") == 0) return SetOriginX();
		if(PositionX.compare("HOME") == 0) return HomeX();
//		if(PositionX.compare("LIM1") == 0) return ToLimitX(1);
//		if(PositionX.compare("LIM2") == 0) return ToLimitX(2);
		if(isnumeric(PositionX.c_str())){
			double pos;
			pos = atof(PositionX.c_str());
			return SetPositionUmX(pos);
		}
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		double pos;
		const int bufSize = 40;
		char buf[bufSize];
		int ret = GetPositionUmY(pos);
		if (ret != DEVICE_OK) return ret;
		snprintf(buf, bufSize, "%f", pos);
		pProp->Set(buf);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(PositionY);   
		if(PositionY.compare("ORIGIN") == 0) return SetOriginY();
		if(PositionY.compare("HOME") == 0) return HomeY();
//		if(PositionY.compare("LIM1") == 0) return ToLimitY(1);
//		if(PositionY.compare("LIM2") == 0) return ToLimitY(2);
		if(isnumeric(PositionY.c_str())){
			double pos;
			pos = atof(PositionY.c_str());
			return SetPositionUmY(pos);
		}
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SpeedX);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SpeedX);
		int ret = MoveX(SpeedX);
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SpeedY);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SpeedY);
		int ret = MoveY(SpeedY);
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
} 
int XYStage::OnOut1X(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out1X);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out1X);  
		int ret = OutX();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int XYStage::OnOut1Y(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out1Y);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out1Y);
		int ret = OutY();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int XYStage::OnOut2X(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out2X);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out2X);
		int ret = OutX();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int XYStage::OnOut2Y(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Out2Y);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Out2Y);
		int ret = OutY();
		if (ret != DEVICE_OK) return ret;
	}                                                                         
	return DEVICE_OK;                                                         
}
int XYStage::OnExecuteX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
		pProp->Set(ExecuteX.c_str());
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(ExecuteX);   
		int ret = SendCommand(ControllerNameX.c_str(), ExecuteX.c_str());
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int XYStage::OnExecuteY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
		pProp->Set(ExecuteY.c_str());
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(ExecuteY);                                                     
		int ret = SendCommand(ControllerNameY.c_str(), ExecuteY.c_str());
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int XYStage::OnStepDivideX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(StepDivideX);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(StepDivideX);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int XYStage::OnStepDivideY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(StepDivideY);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(StepDivideY);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 

////////////////////////////////////////////////////////////////

bool XYStage::BusyX()
{
	// In case of error we postulate that device is not busy
	// Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return false;

	// Query current status
	const int bufSize = 20;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sQ", ControllerNameX.c_str());
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return false;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return false;
	if (answer.substr(ret,1).compare("@") == 0) return true;
	return false;
}
bool XYStage::BusyY()
{
	// In case of error we postulate that device is not busy
	// Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return false;

	// Query current status
	const int bufSize = 20;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sQ", ControllerNameY.c_str());
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return false;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return false;
	if (answer.substr(ret,1).compare("@") == 0) return true;
	return false;
}
bool XYStage::Busy()
{
	if(BusyX()) return true;
	if(BusyY()) return true;
	return false;
}

int XYStage::SetPositionUmX(double pos)
{
   long steps = (long) (pos / stepSizeUmX_ + 0.5);
   return SetPositionStepsX(steps);
}
int XYStage::SetPositionUmY(double pos)
{
   long steps = (long) (pos / stepSizeUmY_ + 0.5);
   return SetPositionStepsY(steps);
}
int XYStage::SetPositionUm(double x, double y)
{
	int ret = SetPositionUmX(x);
	if (ret != DEVICE_OK) return ret;
	return SetPositionUmY(y);
}

int XYStage::GetPositionUmX(double& pos)
{
   long steps;
   int ret = GetPositionStepsX(steps);
   if (ret != DEVICE_OK) return ret;
   pos = steps * stepSizeUmX_;
   return DEVICE_OK;
}
int XYStage::GetPositionUmY(double& pos)
{
   long steps;
   int ret = GetPositionStepsY(steps);
   if (ret != DEVICE_OK) return ret;
   pos = steps * stepSizeUmY_;
   return DEVICE_OK;
}
int XYStage::GetPositionUm(double& x, double& y)
{
	int retx = GetPositionUmX(x);
	int rety = GetPositionUmY(y);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::SetPositionStepsX(long pos)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// form absolute move command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sA%ldR", ControllerNameX.c_str(), pos);

	// send command
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
} 
int XYStage::SetPositionStepsY(long pos)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// form absolute move command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sA%ldR", ControllerNameY.c_str(), pos);

	// send command
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SetPositionSteps(long x, long y)
{
	int retx = SetPositionStepsX(x);
	int rety = SetPositionStepsY(y);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::GetPositionStepsX(long& steps)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// send command to read position
   	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf,bufSize, "/%s?0", ControllerNameX.c_str());
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;
	steps = atol(answer.substr(ret+1,answer.length()-ret-1).c_str());
	return DEVICE_OK;
}
int XYStage::GetPositionStepsY(long& steps)
{
	// First Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// send command to read position
   	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf,bufSize, "/%s?0", ControllerNameY.c_str());
	ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;
	steps = atol(answer.substr(ret+1,answer.length()-ret-1).c_str());
	return DEVICE_OK;
}
int XYStage::GetPositionSteps(long& x, long& y)
{
	int retx = GetPositionStepsX(x);
	int rety = GetPositionStepsY(y);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::SetOriginX()
{
	// Form origin command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sz0R", ControllerNameX.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SetOriginY()
{
	// Form origin command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sz0R", ControllerNameY.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SetOrigin()
{
	int retx = SetOriginX();
	int rety = SetOriginY();
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::GetLimitsUm(double& /*xMin*/, double& /*xMax*/, double& /*yMin*/, double& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int XYStage::StopX()
{
	// Form stop command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sT", ControllerNameX.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::StopY()
{
	// Form stop command
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sT", ControllerNameY.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::Stop()
{
	int retx = StopX();
	int rety = StopY();
	if (retx != DEVICE_OK) return retx;
	return rety;
}
int XYStage::HomeX()
{
	// Form home command (will use SlewVelocity as speed)
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sZ1000000000R", ControllerNameX.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::HomeY()
{
	// Form home command (will use SlewVelocity as speed)
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sZ1000000000R", ControllerNameY.c_str());

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::Home()
{
	int retx = HomeX();
	int rety = HomeY();
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::SetRelativePositionStepsX(long steps)
{
	if(steps==0) return DEVICE_OK;

	// Form relative move command
	const int bufSize = 40;
	char buf[bufSize];
	if(steps>0) snprintf(buf, bufSize, "/%sP%ldR", ControllerNameX.c_str(), steps);
	if(steps<0) snprintf(buf, bufSize, "/%sD%ldR", ControllerNameX.c_str(), -steps);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SetRelativePositionStepsY(long steps)
{
	if(steps==0) return DEVICE_OK;

	// Form relative move command
	const int bufSize = 40;
	char buf[bufSize];
	if(steps>0) snprintf(buf, bufSize, "/%sP%ldR", ControllerNameY.c_str(), steps);
	if(steps<0) snprintf(buf, bufSize, "/%sD%ldR", ControllerNameY.c_str(), -steps);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SetRelativePositionSteps(long x, long y)
{
	int retx = SetRelativePositionStepsX(x);
	int rety = SetRelativePositionStepsY(y);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::SetRelativePositionUmX(double d)
{
	if(d==0) return DEVICE_OK;
	long steps = (long) (d / stepSizeUmX_ + 0.5);
	return SetRelativePositionStepsX(steps);
}
int XYStage::SetRelativePositionUmY(double d)
{
	if(d==0) return DEVICE_OK;
	long steps = (long) (d / stepSizeUmY_ + 0.5);
	return SetRelativePositionStepsY(steps);
}
int XYStage::SetRelativePositionUm(double dx, double dy)
{
	int retx = SetRelativePositionUmX(dx);
	int rety = SetRelativePositionUmY(dy);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::MoveX(double speed)
{
  	// Speed is in um/sec
	// This will change max velocity, so after finished in velocity mode - initialize
	if(speed==0) return StopX();
	long stepspeed = (long) (speed / stepSizeUmX_ + 0.5);

	// Form constant speed move command
	const int bufSize = 40;
	char buf[bufSize];
	if(stepspeed>0) snprintf(buf, bufSize, "/%sV%ldP0R", ControllerNameX.c_str(), stepspeed);
	if(stepspeed<0) snprintf(buf, bufSize, "/%sV%ldD0R", ControllerNameX.c_str(), -stepspeed);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::MoveY(double speed)
{
  	// Speed is in um/sec
	// This will change max velocity, so after finished in velocity mode - initialize
	if(speed==0) return StopY();
	long stepspeed = (long) (speed / stepSizeUmY_ + 0.5);

	// Form constant speed move command
	const int bufSize = 40;
	char buf[bufSize];
	if(stepspeed>0) snprintf(buf, bufSize, "/%sV%ldP0R", ControllerNameY.c_str(), stepspeed);
	if(stepspeed<0) snprintf(buf, bufSize, "/%sV%ldD0R", ControllerNameY.c_str(), -stepspeed);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::Move(double speedx, double speedy)
{
	int retx = MoveX(speedx);
	int rety = MoveY(speedy);
	if (retx != DEVICE_OK) return retx;
	return rety;
}

int XYStage::OutX()
{
  	// Two outputs 1 and 2 with values 0 and 1
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sJ%ldR", ControllerNameX.c_str(), Out1X+2*Out2X);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::OutY()
{
  	// Two outputs 1 and 2 with values 0 and 1
	const int bufSize = 40;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sJ%ldR", ControllerNameY.c_str(), Out1Y+2*Out2Y);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::SendCommand(const char* con, const char* cmd)
{
	const int bufSize = 1024;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%s%s", con, cmd);

	// Send command
	int ret = SendSerialCommand(port_.c_str(), buf, "\r");
	if (ret != DEVICE_OK) return ret;

	// Read and analyze response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}

int XYStage::InitializeXYStageX()
{  
	// Calculate step size
	stepSizeUmX_ = (double)LeadUmX/((double)StepDivideX*MotorStepsX);

	// From microns to microsteps
	long slvel = (long) (SlewVelocityX / stepSizeUmX_ + 0.5);
	long invel = (long) (InitVelocityX / stepSizeUmX_ + 0.5);
	long accel = (long) (AccelerationX / stepSizeUmX_ + 0.5);

	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// Set controller parameters 
	const int bufSize = 200;
	char buf[bufSize];
	snprintf(buf,bufSize, "/%sj%ldh%ldm%ldV%ldv%ldL%ldn2f%ldR", ControllerNameX.c_str(),StepDivideX,HoldCurrentX,RunCurrentX,slvel,invel,accel,LimitPolarityX);
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::InitializeXYStageY()
{  
	// Calculate step size
	stepSizeUmY_ = (double)LeadUmY/((double)StepDivideY*MotorStepsY);

	// From microns to microsteps
	long slvel = (long) (SlewVelocityY / stepSizeUmY_ + 0.5);
	long invel = (long) (InitVelocityY / stepSizeUmY_ + 0.5);
	long accel = (long) (AccelerationY / stepSizeUmY_ + 0.5);

	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// Set controller parameters 
	const int bufSize = 200;
	char buf[bufSize];
	snprintf(buf,bufSize, "/%sj%ldh%ldm%ldV%ldv%ldL%ldn2f%ldR", ControllerNameY.c_str(),StepDivideY,HoldCurrentY,RunCurrentY,slvel,invel,accel,LimitPolarityY);
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
int XYStage::InitializeXYStage()
{
	int retx = InitializeXYStageX();
	int rety = InitializeXYStageY();
	if (retx != DEVICE_OK) return retx;
	return rety;
}
///////////////////////////////////////////////////////////////////////////////
// TOFRA CubeSlider Implementation
///////////////////////////////////////////////////////////////////////////////

CubeSlider::CubeSlider() : 
	ControllerName("6"),  // name (number) of controller for X axis
	SlewVelocity(400000), // microns/sec
	InitVelocity(200000), // microns/sec
	Acceleration(1000),   // microns/sec^2
	HomeSlewVel(50000),   // microns/sec
	HomeInitVel(5000),    // microns/sec
	HomeAccel(100),       // microns/sec^2
	stepSizeUm_(0),		 // microstep size in microns (calculated)
	HoldCurrent(5),      // in % of max (which is 2A)
	RunCurrent(70),		 // in % of max (which is 2A)
	NumPos(6),			 // number of cubes in slider
	HomeOffsetUm(700),   // offset from switch to pos 1 in microns
	BetweenPosUm(40000), // distance between centers of adjacent cubes in microns
	LeadUm(25400),		 // microns of linear motion per full motor turn
	MotorSteps(200),	 // steps in one revolution
	StepDivide(16),		 // microsteps in one full step
	initialized_(false), 
	changedTime_(0.0),
	position_(0),
	port_("")
{
	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	CPropertyAction* pAct;
	// Name
	CreateProperty(MM::g_Keyword_Name, g_SliderDeviceName, MM::String, true);
	// Description
	CreateProperty(MM::g_Keyword_Description, "TOFRA CubeSlider with Integrated Controller", MM::String, true);
	// Com port
	pAct = new CPropertyAction (this, &CubeSlider::OnCOMPort);
	CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
	// Number of positions
	pAct = new CPropertyAction (this, &CubeSlider::OnNumPos);
	CreateProperty("NumPos", "6", MM::Integer, false, pAct, true);
	// ControllerName - Name (number) of the motor controller on the serial line
	pAct = new CPropertyAction (this, &CubeSlider::OnControllerName);
	CreateProperty("ControllerName", "6", MM::String, false, pAct, true);
	// SlewVelocity
	pAct = new CPropertyAction (this, &CubeSlider::OnSlewVelocity);
	CreateProperty("SlewVelocity", "400000", MM::Float, false, pAct, true);
	// InitVelocity
	pAct = new CPropertyAction (this, &CubeSlider::OnInitVelocity);
	CreateProperty("InitVelocity", "200000", MM::Float, false, pAct, true);
	// Acceleration
	pAct = new CPropertyAction (this, &CubeSlider::OnAcceleration);
	CreateProperty("Acceleration", "1000", MM::Float, false, pAct, true);
	// HomeSlewVel
	pAct = new CPropertyAction (this, &CubeSlider::OnHomeSlewVel);
	CreateProperty("HomeSlewVel", "50000", MM::Float, false, pAct, true);
	// HomeInitVel
	pAct = new CPropertyAction (this, &CubeSlider::OnHomeInitVel);
	CreateProperty("HomeInitVel", "5000", MM::Float, false, pAct, true);
	// HomeAccel
	pAct = new CPropertyAction (this, &CubeSlider::OnHomeAccel);
	CreateProperty("HomeAccel", "100", MM::Float, false, pAct, true);
	// HoldCurrent
	pAct = new CPropertyAction (this, &CubeSlider::OnHoldCurrent);
	CreateProperty("HoldCurrent", "5", MM::Integer, false, pAct, true);
	// RunCurrent
	pAct = new CPropertyAction (this, &CubeSlider::OnRunCurrent);
	CreateProperty("RunCurrent", "70", MM::Integer, false, pAct, true);
	// HomeOffsetUm
	pAct = new CPropertyAction (this, &CubeSlider::OnHomeOffsetUm);
	CreateProperty("HomeOffsetUm", "700", MM::Integer, false, pAct, true);
	// BetweenPosUm
	pAct = new CPropertyAction (this, &CubeSlider::OnBetweenPosUm);
	CreateProperty("BetweenPosUm", "40000", MM::Integer, false, pAct, true);
	// LeadUm
	pAct = new CPropertyAction (this, &CubeSlider::OnLeadUm);
	CreateProperty("LeadUm", "25400", MM::Integer, false, pAct, true);
	// MotorSteps
	pAct = new CPropertyAction (this, &CubeSlider::OnMotorSteps);
	CreateProperty("MotorSteps", "200", MM::Integer, false, pAct, true);
	// StepDivide
	pAct = new CPropertyAction (this, &CubeSlider::OnStepDivide);
	CreateProperty("StepDivide", "16", MM::Integer, false, pAct, true);
}

CubeSlider::~CubeSlider()
{
   Shutdown();
}

void CubeSlider::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_SliderDeviceName);
}

int CubeSlider::Initialize()
{
	int ret;
	CPropertyAction* pAct;

	// State
	pAct = new CPropertyAction (this, &CubeSlider::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Label
	pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Create default positions and labels
	const int bufSize = 1024;
	char buf[bufSize];
	for (long i=0; i<NumPos; i++)
	{
		snprintf(buf, bufSize, "Cube-%.2ld", i + 1);
		SetPositionLabel(i, buf);
	}

	// Update Status 
	ret = UpdateStatus();
	if (ret != DEVICE_OK) return ret;

	// Initialize and Home	
	position_ = 0;
	ret = InitializeCubeSlider();
	if (ret != DEVICE_OK) return ret;

	changedTime_ = GetCurrentMMTime();
	initialized_ = true;
	return DEVICE_OK;
}

int CubeSlider::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool CubeSlider::Busy()
{
	// In case of error we postulate that device is not busy
	// Clear serial port from previous stuff
	int ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return false;

	// Query current status
	const int bufSize = 20;
	char buf[bufSize];
	snprintf(buf, bufSize, "/%sQ", ControllerName.c_str());
	ret = SendSerialCommand(port_.c_str(),buf,"\r");
	if (ret != DEVICE_OK) return false;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return false;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return false;
	if (answer.substr(ret,1).compare("@") == 0) return true;
	return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers - CubeSlider
///////////////////////////////////////////////////////////////////////////////

int CubeSlider::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(position_);
		// nothing to do, let the caller use the cached property
   }
   else if (eAct == MM::AfterSet)
   {
		// Set timer for the Busy signal
		changedTime_ = GetCurrentMMTime();

		long pos;
		pProp->Get(pos);
		if (pos >= NumPos || pos < 0)
		{
			pProp->Set(position_);
			return ERR_UNKNOWN_POSITION;
		}
		if (position_ == pos) return DEVICE_OK;
		
		// form absolute move command
		long utarget,mtarget;
		const int bufSize = 1024;
		char buf[bufSize];
		char mbuf[bufSize];
		utarget = pos * BetweenPosUm + HomeOffsetUm;
		mtarget = (long) (utarget / stepSizeUm_ + 0.5);
		snprintf(mbuf, bufSize, "Target Position %ld, Command A%ldR", pos, mtarget);
		LogMessage(mbuf, true);
		snprintf(buf, bufSize, "/%sA%ldR", ControllerName.c_str(), mtarget);

		// Clear serial port from previous stuff
		int ret = ClearPort(*this, *GetCoreCallback(), port_);
		if (ret != DEVICE_OK) return ret;

		// Send move command
		ret = SendSerialCommand(port_.c_str(),buf,"\r");
		if (ret != DEVICE_OK) return ret;

		// Read and analyze response
		std::string answer;
		ret = GetSerialAnswer(port_.c_str(), "\r", answer);
		if (ret != DEVICE_OK) return ret;
		ret = AnalyzeAnswer(answer);
		if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

		position_ = pos;
   }
   return DEVICE_OK;
}

int CubeSlider::OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(NumPos);
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(NumPos);    
		// (re)create default positions and labels
		const int bufSize = 1024;
		char buf[bufSize];
		for (long i=0; i<NumPos; i++)
		{
			snprintf(buf, bufSize, "Cube-%.2ld", i + 1);
			SetPositionLabel(i, buf);
		}
		// Update Status - what does it do?
		int ret = UpdateStatus();
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
}

int CubeSlider::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str()); // revert
      }
      pProp->Get(port_);                                                     
   }                                                                        
   return DEVICE_OK;                                                         
} 

int CubeSlider::OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(ControllerName.c_str());
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(ControllerName);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnSlewVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(SlewVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(SlewVelocity);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnInitVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(InitVelocity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(InitVelocity);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Acceleration);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Acceleration);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnHomeSlewVel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HomeSlewVel);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HomeSlewVel);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnHomeInitVel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HomeInitVel);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HomeInitVel);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnHomeAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HomeAccel);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HomeAccel);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnHoldCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(HoldCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(HoldCurrent);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnRunCurrent(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(RunCurrent);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(RunCurrent);  
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnHomeOffsetUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(HomeOffsetUm);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(HomeOffsetUm);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnBetweenPosUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(BetweenPosUm);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(BetweenPosUm);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnLeadUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(LeadUm);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(LeadUm);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnMotorSteps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(MotorSteps);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(MotorSteps);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 
int CubeSlider::OnStepDivide(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(StepDivide);
   }
   else if (eAct == MM::AfterSet) {
      pProp->Get(StepDivide);                                                     
   }                                                                         
   return DEVICE_OK;                                                         
} 

int CubeSlider::InitializeCubeSlider()
{  
	// Calculate step size
	stepSizeUm_ = (double)LeadUm/((double)StepDivide*MotorSteps);

	// From microns to microsteps
	long slvel = (long) (SlewVelocity / stepSizeUm_ + 0.5);
	long invel = (long) (InitVelocity / stepSizeUm_ + 0.5);
	long accel = (long) (Acceleration / stepSizeUm_ + 0.5);
	long hslvel = (long) (HomeSlewVel / stepSizeUm_ + 0.5);
	long hinvel = (long) (HomeInitVel / stepSizeUm_ + 0.5);
	long haccel = (long) (HomeAccel / stepSizeUm_ + 0.5);

	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// for absolute move command for first position
	long mtarget;
	mtarget = (long) (HomeOffsetUm / stepSizeUm_ + 0.5);
	
	// Set controller parameters and do homing
	const int bufSize = 1024;
	char cbuf[bufSize];

	// Form command string to set current values of parameters and home the slider
	//snprintf(cbuf,bufSize, "/%sj%ldh%ldm%ldV%ldv%ldL%ldZ1000000000R",ControllerName.c_str(),
	//	StepDivide,HoldCurrent,RunCurrent,slvel,invel,accel);
	snprintf(cbuf,bufSize, "/%sj%ldh%ldm%ldV%ldv%ldL%ldZ1000000000A%ldV%ldv%ldL%ldR",ControllerName.c_str(),
		StepDivide,HoldCurrent,RunCurrent,hslvel,hinvel,haccel,mtarget,slvel,invel,accel);
	ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
	if (ret != DEVICE_OK) return ret;

	// Read response
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), "\r", answer);
	if (ret != DEVICE_OK) return ret;

	// Analyze response
	ret = AnalyzeAnswer(answer);
	if (ret == ERR_BAD_CONTROLLER_RESPONSE) return ERR_BAD_CONTROLLER_RESPONSE;

	return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// TOFRA rgbLED Implementation
///////////////////////////////////////////////////////////////////////////////

rgbLED::rgbLED() : 
	initialized_(false), 
	changedTime_(0.0),
	position_(0),
	port_(""),
	delay_(20),
	Channel1Intensity(0), // Intensity in scale [0 1] of Channel 1
	Channel2Intensity(0), // Intensity in scale [0 1] of Channel 2
	Channel3Intensity(0), // Intensity in scale [0 1] of Channel 3
	Channel4Intensity(0), // Intensity in scale [0 1] of Channel 4
	NumPos(5)			  // number of states in rgbLED (R, G, B, A, Off)

{
	InitializeDefaultErrorMessages();

	// create pre-initialization properties
	CPropertyAction* pAct;
	// Name
	CreateProperty(MM::g_Keyword_Name, g_rgbLEDDeviceName, MM::String, true);
	// Description
	CreateProperty(MM::g_Keyword_Description, "TOFRA rgbLED", MM::String, true);
	// Com port
	pAct = new CPropertyAction (this, &rgbLED::OnCOMPort);
	CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);
	// Number of positions
	pAct = new CPropertyAction (this, &rgbLED::OnNumPos);
	CreateProperty("NumPos", "5", MM::Integer, false, pAct, true);
}

rgbLED::~rgbLED()
{
   Shutdown();
}

void rgbLED::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_rgbLEDDeviceName);
}
int rgbLED::Initialize()
{
	int ret;
	CPropertyAction* pAct;

	// State
	pAct = new CPropertyAction (this, &rgbLED::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Label
	pAct = new CPropertyAction (this, &CStateBase::OnLabel);
	ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
	if (ret != DEVICE_OK) return ret;

	// Create default positions and labels
	SetPositionLabel(0, "Off");
	SetPositionLabel(1, "R");
	SetPositionLabel(2, "G");
	SetPositionLabel(3, "B");
	SetPositionLabel(4, "A");

	// Channel1Intensity
	pAct = new CPropertyAction (this, &rgbLED::OnChannel1Intensity);
	ret = CreateProperty("Channel1Intensity", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK) return ret;
	ret = SetPropertyLimits("Channel1Intensity", 0.0, 1.0);
	if (ret != DEVICE_OK) return ret;

	// Channel2Intensity
	pAct = new CPropertyAction (this, &rgbLED::OnChannel2Intensity);
	ret = CreateProperty("Channel2Intensity", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK) return ret;
	ret = SetPropertyLimits("Channel2Intensity", 0.0, 1.0);
	if (ret != DEVICE_OK) return ret;

	// Channel3Intensity
	pAct = new CPropertyAction (this, &rgbLED::OnChannel3Intensity);
	ret = CreateProperty("Channel3Intensity", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK) return ret;
	ret = SetPropertyLimits("Channel3Intensity", 0.0, 1.0);
	if (ret != DEVICE_OK) return ret;

	// Channel4Intensity
	pAct = new CPropertyAction (this, &rgbLED::OnChannel4Intensity);
	ret = CreateProperty("Channel4Intensity", "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK) return ret;
	ret = SetPropertyLimits("Channel4Intensity", 0.0, 1.0);
	if (ret != DEVICE_OK) return ret;

	// Update Status 
	ret = UpdateStatus();
	if (ret != DEVICE_OK) return ret;

	// Initialize
	position_ = 0;
	ret = InitializergbLED();
	if (ret != DEVICE_OK) return ret;

	changedTime_ = GetCurrentMMTime();
	initialized_ = true;
	return DEVICE_OK;
}

int rgbLED::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool rgbLED::Busy()
{
	return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers - rgbLED
///////////////////////////////////////////////////////////////////////////////

int rgbLED::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
		pProp->Set(position_);
		// nothing to do, let the caller use the cached property
   }
   else if (eAct == MM::AfterSet)
   {
		// Set timer for the Busy signal
		changedTime_ = GetCurrentMMTime();

		long pos;
		pProp->Get(pos);
		if (pos >= NumPos || pos < 0)
		{
			pProp->Set(position_);
			return ERR_UNKNOWN_POSITION;
		}
		if (position_ == pos) return DEVICE_OK;

		// Clear serial port from previous stuff
		int ret = ClearPort(*this, *GetCoreCallback(), port_);
		if (ret != DEVICE_OK) return ret;

		// Send Intensity Values
		const char* rOff = "AVA500"; const char* rMax = "AVA000";
		const char* gOff = "AVB500"; const char* gMax = "AVB000";
		const char* bOff = "AVC500"; const char* bMax = "AVC000";
		const char* aOff = "AVD500"; const char* aMax = "AVD000";
		switch(pos)
		{
			case 0:
				ret = SendSerialCommand(port_.c_str(),rOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),gOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),bOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),aOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
			break;
			case 1:
				ret = SendSerialCommand(port_.c_str(),gOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),bOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),aOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),rMax,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
			break;
			case 2:
				ret = SendSerialCommand(port_.c_str(),rOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),bOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),aOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),gMax,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
			break;
			case 3:
				ret = SendSerialCommand(port_.c_str(),rOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),gOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),aOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),bMax,"\r"); if (ret != DEVICE_OK) return ret;	CDeviceUtils::SleepMs(delay_);
			break;
			case 4:
				ret = SendSerialCommand(port_.c_str(),rOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),gOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),bOff,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
				ret = SendSerialCommand(port_.c_str(),aMax,"\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
			break;
		}

		position_ = pos;
   }
   return DEVICE_OK;
}

int rgbLED::OnNumPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long np;
	if (eAct == MM::BeforeGet) {
      pProp->Set(NumPos);
   }
   else if (eAct == MM::AfterSet) {
		pProp->Get(np);    
		// Ignore set 
   }                                                                         
   return DEVICE_OK;                                                         
}

int rgbLED::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str()); // revert
      }
      pProp->Get(port_);                                                     
   }                                                                        
   return DEVICE_OK;                                                         
} 
int rgbLED::OnChannel1Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Channel1Intensity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Channel1Intensity);  
		int ret = ChannelIntensity(1,Channel1Intensity);
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int rgbLED::OnChannel2Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Channel2Intensity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Channel2Intensity);
		int ret = ChannelIntensity(2,Channel2Intensity);
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int rgbLED::OnChannel3Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Channel3Intensity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Channel3Intensity);
		int ret = ChannelIntensity(3,Channel3Intensity);
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int rgbLED::OnChannel4Intensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet) {
		pProp->Set(Channel4Intensity);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(Channel4Intensity);
		int ret = ChannelIntensity(4,Channel4Intensity);
		if (ret != DEVICE_OK) return ret;
   }                                                                         
   return DEVICE_OK;                                                         
} 
int rgbLED::InitializergbLED()
{  
	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;

	// Turn off all channels
	ret = SendSerialCommand(port_.c_str(),"AVA500","\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
	ret = SendSerialCommand(port_.c_str(),"AVB500","\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
	ret = SendSerialCommand(port_.c_str(),"AVC500","\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);
	ret = SendSerialCommand(port_.c_str(),"AVD500","\r"); if (ret != DEVICE_OK) return ret; CDeviceUtils::SleepMs(delay_);

	return DEVICE_OK;
}
int rgbLED::ChannelIntensity(long channel, double intensity)
{  
	if (intensity > 1 || intensity < 0) return DEVICE_INVALID_PROPERTY_VALUE;

	int ret;
	// Clear serial port from previous stuff
	ret = ClearPort(*this, *GetCoreCallback(), port_);
	if (ret != DEVICE_OK) return ret;
	// Recalculate intensity into [500 0] scale
	int val;
	val = (int) floor(500 - intensity*500);
	// Form channel letter
	const char* sch;
	switch(channel)
	{
		case 1: sch = "A"; break;
		case 2: sch = "B"; break;
		case 3: sch = "C"; break;
		case 4: sch = "D"; break;
		default: sch = " ";
	}
	// Form command and send it
	const int bufSize = 1024;
	char cbuf[bufSize];
	snprintf(cbuf,bufSize, "AV%s%.3d",sch,val);
	ret = SendSerialCommand(port_.c_str(),cbuf,"\r");
	if (ret != DEVICE_OK) return ret;
	// Wait
	CDeviceUtils::SleepMs(delay_);

	return DEVICE_OK;
}


//
// RGBLEDShutter Implementation
//

RGBLEDShutter::RGBLEDShutter() :
	initialized_(false),
	open_(false)
{
	for (unsigned i = 0; i < nChannels_; ++i) {
		channelIntensities_[i] = 0.20;
	}

	InitializeDefaultErrorMessages();

	CPropertyAction* pAct =
		new CPropertyAction(this, &RGBLEDShutter::OnCOMPort);
	CreateStringProperty(MM::g_Keyword_Port, "", false, pAct, true);
}


RGBLEDShutter::~RGBLEDShutter()
{
}


int
RGBLEDShutter::Initialize()
{
	int err;

	err = SetOpen(false);
	if (err != DEVICE_OK)
		return err;

	if (initialized_)
		return DEVICE_OK;

	err = CreateStringProperty(MM::g_Keyword_Name, g_RGBLEDShutterDeviceName, true);
	if (err != DEVICE_OK)
		return err;

	err = CreateStringProperty(MM::g_Keyword_Description, "TOFRA RGB LED as Shutter", true);
	if (err != DEVICE_OK)
		return err;

	err = CreateIntegerProperty(MM::g_Keyword_State, 0, false,
			new CPropertyAction(this, &RGBLEDShutter::OnState));
	if (err != DEVICE_OK)
		return err;
	err = SetPropertyLimits(MM::g_Keyword_State, 0, 1);
	if (err != DEVICE_OK)
		return err;

	for (unsigned i = 0; i < nChannels_; ++i)
	{
		const std::string propName = "Channel" +
			boost::lexical_cast<std::string>(i + 1) + "Intensity";

		err = CreateFloatProperty(propName.c_str(),
				channelIntensities_[i], false,
				new CPropertyActionEx(this,
					&RGBLEDShutter::OnChannelIntensity, i));
		if (err != DEVICE_OK)
			return err;

		err = SetPropertyLimits(propName.c_str(), 0.0, 1.0);
		if (err != DEVICE_OK)
			return err;
	}

	initialized_ = true;
	return DEVICE_OK;
}


int
RGBLEDShutter::Shutdown()
{
	if (!initialized_)
		return DEVICE_OK;

	int err = SetOpen(false);
	if (err != DEVICE_OK)
		return err;

	initialized_ = false;
	return DEVICE_OK;
}


void
RGBLEDShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_RGBLEDShutterDeviceName);
}


bool
RGBLEDShutter::Busy()
{
	return false;
}


int
RGBLEDShutter::GetOpen(bool& open)
{
	open = open_;
	return DEVICE_OK;
}


int
RGBLEDShutter::SetOpen(bool open)
{
	int err = ApplyIntensities(open);
	if (err != DEVICE_OK)
		return err;
	open_ = open;
	return DEVICE_OK;
}


int
RGBLEDShutter::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (initialized_)
		{
			// Don't allow change after initialization
			pProp->Set(port_.c_str());
			return DEVICE_ERR;
		}
		pProp->Get(port_);
	}
	return DEVICE_OK;
}


int
RGBLEDShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(open_ ? 1L : 0L);
	}
	else if (eAct == MM::AfterSet)
	{
		long val;
		pProp->Get(val);
		int err = SetOpen(val != 0);
		if (err != DEVICE_OK)
			return err;
	}
	return DEVICE_OK;
}


int
RGBLEDShutter::OnChannelIntensity(MM::PropertyBase* pProp,
		MM::ActionType eAct, long chan)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(channelIntensities_[chan]);
	}
	else if (eAct == MM::AfterSet)
	{
		double val;
		pProp->Get(val);
		channelIntensities_[chan] = val;
		if (open_)
		{
			int err = ApplyIntensities(true);
			if (err != DEVICE_OK)
				return err;
		}
	}
	return DEVICE_OK;
}


int
RGBLEDShutter::ApplyIntensities(bool open)
{
	int err;
	for (unsigned i = 0; i < nChannels_; ++i)
	{
		err = SetChannelIntensity(i, open ? channelIntensities_[i] : 0.0);
		if (err != DEVICE_OK)
			return err;
	}
	return DEVICE_OK;
}


int
RGBLEDShutter::SetChannelIntensity(int chan, double intensity)
{
	if (intensity > 1.0 || intensity < 0.0)
		return DEVICE_INVALID_PROPERTY_VALUE;

	int ret;
	ret = PurgeComPort(port_.c_str());
	if (ret != DEVICE_OK)
		return ret;

	int deviceIntensity = static_cast<int>(
			500.0 * (1.0 - intensity));

	char deviceChannel = 'A' + chan;

	const int bufSize = 32;
	char cbuf[bufSize];
	snprintf(cbuf, bufSize, "AV%c%.3d", deviceChannel, deviceIntensity);

	ret = SendSerialCommand(port_.c_str(), cbuf, "\r");
	if (ret != DEVICE_OK)
		return ret;

	CDeviceUtils::SleepMs(delay_);

	return DEVICE_OK;
}
