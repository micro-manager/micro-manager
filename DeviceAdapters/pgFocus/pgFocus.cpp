
///////////////////////////////////////////////////////////////////////////////
// FILE:          pgFocus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Focus stability
// COPYRIGHT:     
//                University of Massachusetts Medical School, Worcester, 2014
//                All rights reserved
//
// LICENSE:       
//
// AUTHOR:        Karl Bellve
// MAINTAINER     Karl Bellve Karl.Bellve@umassmed.edu
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "pgFocus.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
#include <string.h>
#include <iostream>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_pgFocus, MM::AutoFocusDevice, "Focus Stabilization");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_pgFocus) == 0)
   {
      return new pgFocus (g_pgFocus);
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


//////////////////////////////////////////////////////////////////////
// pgFocus: Open Source and Open Hardware reflection-based auto focusing.
// ----------------

pgFocus::pgFocus(const char* name) :
		name_(name),
		port_("Undefined"),
		version_("0"),
		serialNumber_("0"),
		focusMode_(g_pgFocus_Unlock),
		focusCurrentMode_(g_pgFocus_Unlocked),
		initialized_(false),
		busy_(false),
		debug_(true),
		justCalibrated_(false),
		autoExposure_(true),
		continuousFocusing_(false),
		answerTimeoutMs_(1000),
		ret_(DEVICE_OK),
		waitAfterLock_(100),
		waitAfterLight_(1000),
		exposure_(10000),
		DAC_nM_ (0),
		ADC_nM_ (0),

		monitoringThread_(NULL)
{

	InitializeDefaultErrorMessages();

	//	"The communication port to the microscope can not be opened");
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_NOT_LOCKED, 			"The pgFocus failed to lock");
	SetErrorText(ERR_NOT_CALIBRATED, 		"pgFocus is not calibrated. Try focusing close to a coverslip and selecting 'Calibrate'");
	SetErrorText(ERR_OUT_OF_RANGE, 			"The number you entered is outside of the range");

	// create pre-initialization properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_pgFocus, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Open Source and Open Hardware Focus Stabilization Device", MM::String, true);

	// Port
	CPropertyAction* pAct = new CPropertyAction (this, &pgFocus::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

	pAct = new CPropertyAction (this, &pgFocus::OnSerialTerminator);
	CreateProperty(g_SerialTerminator, g_SerialTerminator_0_Text, MM::String, false, pAct);
	AddAllowedValue(g_SerialTerminator, g_SerialTerminator_0_Text);
	AddAllowedValue(g_SerialTerminator, g_SerialTerminator_1_Text);
	AddAllowedValue(g_SerialTerminator, g_SerialTerminator_2_Text);
	AddAllowedValue(g_SerialTerminator, g_SerialTerminator_3_Text);

	MM_THREAD_INITIALIZE_GUARD(&mutex);

}

pgFocus::~pgFocus()
{
   Shutdown();
}

int pgFocus::Shutdown()
{

	initialized_ = false;

	if (monitoringThread_ != 0)
		delete(monitoringThread_);
	MM_THREAD_DELETE_GUARD(&mutex);

	return DEVICE_OK;
}

int pgFocus::Initialize()
{
	CPropertyAction* pAct;
	LogMessage("pgFocus::Initialize()");

	if (initialized_)
	  return DEVICE_OK;

	ClearPort();

	pAct = new CPropertyAction(this, &pgFocus::OnFocusMode);
	RETURN_ON_MM_ERROR(CreateProperty (g_pgFocus_Mode, g_pgFocus_Unlock, MM::String, false, pAct));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Lock));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Unlock));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Calibration));

	pAct = new CPropertyAction(this, &pgFocus::OnFocusCurrentMode);
	RETURN_ON_MM_ERROR(CreateProperty (g_pgFocus_Current_Mode, g_pgFocus_Unlocked, MM::String, true, pAct));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Current_Mode, g_pgFocus_Locked));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Current_Mode, g_pgFocus_Unlocked));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Current_Mode, g_pgFocus_Calibrating));

	pAct = new CPropertyAction(this, &pgFocus::OnExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Exposure, "10000", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnInputGain);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_Gain, g_pgFocus_Default_Integer, MM::Float, false, pAct));

	// User Modifiable
	pAct = new CPropertyAction(this, &pgFocus::OnWaitAfterLock);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Wait_Time_Lock, "100", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnMicronsPerVolt);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Microns_Volt, "10", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnWaitAfterMessage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Wait_Time_Message, "250", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnAutoExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Auto_Exposure, g_pgFocus_On, MM::String, false, pAct));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Auto_Exposure, g_pgFocus_On));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Auto_Exposure, g_pgFocus_Off));

	pAct = new CPropertyAction(this, &pgFocus::OnWaitAfterLight);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Wait, "1000", MM::Integer, false, pAct))

	pAct = new CPropertyAction(this, &pgFocus::OnOffset);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Offset, g_pgFocus_Default_Integer, MM::Float, false, pAct));

	// Non User  Modifiable
	pAct = new CPropertyAction(this, &pgFocus::OnOutputVoltage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Output_Voltage, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnOutputnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Output_nM, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnInputVoltage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_Voltage, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnInputnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_nM, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnSlope);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Slope, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnSD);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Standard_Deviation, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnSDnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Standard_Deviation_nM, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnCurrentExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Actual_Exposure, "10000", MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnMin);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Min, g_pgFocus_Default_Integer, MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnMax);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Max, g_pgFocus_Default_Integer, MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnFirmware);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Firmware, g_pgFocus_Default_String, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnResiduals);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Residuals, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnIntercept);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Intercept, g_pgFocus_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnLight);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_LightProfile, g_pgFocus_Default_String, MM::String, true, pAct));
	RETURN_ON_MM_ERROR(UpdateProperty(g_pgFocus_LightProfile));;

	pAct = new CPropertyAction(this, &pgFocus::OnCalibrationCurve);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_CalibrationCurve, g_pgFocus_CalibrationCurveRequest, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnLastError);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Last_Error, g_pgFocus_Default_String, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocus::OnLastStatus);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Last_Status, g_pgFocus_Default_String, MM::String, true, pAct));

	ret_ = UpdateStatus();
	if (ret_ != DEVICE_OK)
		return ret_;


	monitoringThread_ = new pgFocusMonitoringThread(*this,*GetCoreCallback(),&deviceInfo_, debug_);
	monitoringThread_->Start();

	SendCommand("s"); 			// stop pgFocus
	SendCommand("v"); 			// get stats
	SendCommand("version"); 	// Get Firmware Version

	initialized_ = true;
	return DEVICE_OK;
}

bool pgFocus::Busy()
{
	return busy_;
}

///////////////////////////////////////////////////////////////////////////////
// Serial Communication
///////////////////////////////////////////////////////////////////////////////


int pgFocus::SetCommand(std::string command)
{
   string answer;
   // query command
   int ret = SendCommand(command.c_str());
   if (ret != DEVICE_OK)
      return ret;

   return ERR_UNRECOGNIZED_ANSWER;
}

// Communication "send" utility functions:
int pgFocus::SendCommand(const char *command)
{

	RETURN_ON_MM_ERROR ( ClearPort() );
	// send command
	MM_THREAD_GUARD_LOCK(&mutex);
	RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command, "\r"));
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

// Communication "send" utility function:
int pgFocus::SendCommand(std::string command)
{
	RETURN_ON_MM_ERROR ( ClearPort() );
	// send command
	MM_THREAD_GUARD_LOCK(&mutex);
	RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command.c_str(), "\r"));
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

void pgFocus::GetName(char* name) const
{
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(name, g_pgFocus);
}


int pgFocus::ClearPort()
{
   // Clear contents of serial port
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = GetCoreCallback()->ReadFromSerial(this, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Autofocus API
///////////////////////////////////////////////////////////////////////////////


bool pgFocus::IsContinuousFocusLocked()
{

	MM_THREAD_GUARD_LOCK(&mutex);
	continuousFocusing_ = deviceInfo_.continuousFocusing;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return (continuousFocusing_);

}


int pgFocus::SetOffset(double offset)
{

	ostringstream command;
	if (offset >= 0 && offset < 128) {
		command << "offset " << offset;
		SendCommand(command.str());
		return DEVICE_OK;
	}
	else return ERR_OUT_OF_RANGE;

}


int pgFocus::GetOffset(double& offset)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	deviceInfo_.offset = offset;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

/**
 * Does a "one-shot" autofocus: locks and then unlocks again
 */
int pgFocus::FullFocus()
{
   int ret = SetContinuousFocusing(true);
   if (ret != DEVICE_OK)
      return ret;

   MM::MMTime startTime = GetCurrentMMTime();
   MM::MMTime wait(0, waitAfterLock_ * 1000);
   while (!IsContinuousFocusLocked() && ( (GetCurrentMMTime() - startTime) < wait) ) {
	      CDeviceUtils::SleepMs(25);
   }

   CDeviceUtils::SleepMs(waitAfterLock_);

   if (!IsContinuousFocusLocked()) {
      SetContinuousFocusing(false);
      return ERR_NOT_LOCKED;
   }

   return SetContinuousFocusing(false);
}

int pgFocus::IncrementalFocus()
{
   return FullFocus();
}

int pgFocus::GetLastFocusScore(double& score)
{

	// Perhaps return standard deviation?
	MM_THREAD_GUARD_LOCK(&mutex);
	score = deviceInfo_.standardDeviation;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocus::GetCurrentFocusScore(double& score)
{

	score = GetLastFocusScore(score);
	// compute Standard Deviation
	//standardDeviation_ = ;

	return 0;
}

int pgFocus::AutoSetParameters() {
   return 0;
};

int pgFocus::SetFocusMode(std::string focusMode)
{

	focusMode_ = focusMode;

	if (focusMode_.compare(g_pgFocus_Lock) == 0) {
		SetContinuousFocusing(true);
	}

	if (focusMode_.compare(g_pgFocus_Unlock) == 0) {
		SetContinuousFocusing(false);
	}

	if (focusMode_.compare(g_pgFocus_Light) == 0) {
		SetContinuousFocusing(false);
		deviceInfo_.mode = PGFOCUS_MODE_LIGHT_PROFILE;
		SendCommand("l");
	}

	if (focusMode_.compare(g_pgFocus_Calibration) == 0) {
		SetContinuousFocusing(false); // must be first
		MM_THREAD_GUARD_LOCK(&mutex);
		deviceInfo_.mode = PGFOCUS_MODE_CALIBRATING;
		deviceInfo_.calibration_curve.clear();
		MM_THREAD_GUARD_UNLOCK(&mutex);
		SendCommand("c");
		UpdateProperty(g_pgFocus_CalibrationCurve);
	}

	RETURN_ON_MM_ERROR(UpdateProperty(g_pgFocus_Current_Mode));

	return DEVICE_OK;
}

std::string pgFocus::GetFocusCurrentMode()
{

	MM_THREAD_GUARD_LOCK(&mutex);
	if (deviceInfo_.mode == PGFOCUS_MODE_UNLOCKED) {
		focusCurrentMode_ = g_pgFocus_Unlocked;
	}

	if (deviceInfo_.mode == PGFOCUS_MODE_LOCKED) {
		focusCurrentMode_ = g_pgFocus_Locked;
	}

	if (deviceInfo_.mode == PGFOCUS_MODE_CALIBRATING) {
		focusCurrentMode_ = g_pgFocus_Calibrating;
	}

	if (deviceInfo_.mode == PGFOCUS_MODE_LIGHT_PROFILE) {
		focusCurrentMode_ = g_pgFocus_LightProfile;
	}
	MM_THREAD_GUARD_UNLOCK(&mutex);


	return focusCurrentMode_;
}

int pgFocus::UpdateFocusMode()
{
	// query pgFocus current focus mode
	return DEVICE_OK;
}

int pgFocus::SetContinuousFocusing(bool mode)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	continuousFocusing_ = deviceInfo_.continuousFocusing = mode;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (continuousFocusing_) {
		SendCommand("f"); // start pgFocus
		MM_THREAD_GUARD_LOCK(&mutex);
		deviceInfo_.mode = PGFOCUS_MODE_LOCKED;
		MM_THREAD_GUARD_UNLOCK(&mutex);
		LogMessage("Locking Focus", true);
	}
	else {
		SendCommand("s"); // stop pgFocus
		MM_THREAD_GUARD_LOCK(&mutex);
		deviceInfo_.mode = PGFOCUS_MODE_UNLOCKED;
		MM_THREAD_GUARD_UNLOCK(&mutex);
		LogMessage("Stopping Focus", true);
	}

	RETURN_ON_MM_ERROR(UpdateProperty(g_pgFocus_Current_Mode));

	return DEVICE_OK;
}

int pgFocus::GetContinuousFocusing(bool& state)
{
	state = continuousFocusing_;
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int pgFocus::OnMicronsPerVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.micronPerVolt);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}else if (eAct == MM::AfterSet)
	{
		long temp;
		pProp->Get(temp);
		if (temp > 0) {
			std::ostringstream oss;
			oss << "mpv " << temp;
			SendCommand(oss.str().c_str());
			MM_THREAD_GUARD_LOCK(&mutex);
			deviceInfo_.micronPerVolt = temp;
			MM_THREAD_GUARD_LOCK(&mutex);
		} else return ERR_OUT_OF_RANGE;
	}

	return DEVICE_OK;

}

int pgFocus::OnSD(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.standardDeviation);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


	return DEVICE_OK;

}

int pgFocus::OnSDnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set((deviceInfo_.standardDeviation * deviceInfo_.slope / DAUPERVOLT) * deviceInfo_.micronPerVolt * NMPERMICON);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


	return DEVICE_OK;

}

int pgFocus::OnFocusMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		RETURN_ON_MM_ERROR( UpdateFocusMode() );
		pProp->Set(focusMode_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		string focusMode;
		pProp->Get(focusMode);
		RETURN_ON_MM_ERROR( SetFocusMode(focusMode) );
	}

	return DEVICE_OK;

}

int pgFocus::OnFocusCurrentMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(GetFocusCurrentMode().c_str());
	}

	return DEVICE_OK;

}

int pgFocus::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
	   pProp->Set(waitAfterLock_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(waitAfterLock_);
	}

	return DEVICE_OK;
}

int pgFocus::OnWaitAfterMessage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (monitoringThread_) {
		MM_THREAD_GUARD_LOCK(&mutex);
		if (eAct == MM::BeforeGet)
		{
		   pProp->Set(monitoringThread_->getWaitTime());
		}
		else if (eAct == MM::AfterSet)
		{
			long time;
			pProp->Get(time);
			if (time >=0) {
				monitoringThread_->setWaitTime(time);
			}
		}
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

   return DEVICE_OK;
}

int pgFocus::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.offset);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}
	else if (eAct == MM::AfterSet)
	{
		double offset;
		pProp->Get(offset);
		return (SetOffset(offset));
	}

	return DEVICE_OK;
}

int pgFocus::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(exposure_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(exposure_);
		if (exposure_ > 0) {
			std::ostringstream oss;
			oss << "exposure " << exposure_;
			SendCommand(oss.str().c_str());
		}
	}

	return DEVICE_OK;
}

int pgFocus::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		if (autoExposure_) pProp->Set(g_pgFocus_On);
		else pProp->Set(g_pgFocus_Off);
	}
	else if (eAct == MM::AfterSet)
	{
		std::string result;
		pProp->Get(result);
		if (result.compare(g_pgFocus_On) == 0) {
			autoExposure_ = true;
			SendCommand("E"); // default, turn on auto exposure
		}
		else {
			autoExposure_ = false;
			SendCommand("e"); // default, turn off auto exposure
		}
	}

	return DEVICE_OK;
}

int pgFocus::OnCurrentExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SendCommand("exposure"); 		// get exposure

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.exposure);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return DEVICE_OK;
}

int pgFocus::OnCalibrationCurve(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	MM_THREAD_GUARD_LOCK(&mutex);

	std::ostringstream oss;



	for (std::vector<std::string>::size_type i = 0; i<deviceInfo_.calibration_curve.size();i++)
	{
		oss << deviceInfo_.calibration_curve[i] << ",";
		oss << deviceInfo_.calibration_curve[++i];
		if ((i + 1) < deviceInfo_.calibration_curve.size()) oss << ",";
	}

	LogMessage(oss.str());

	if (deviceInfo_.calibration_curve.size() > 0)  pProp->Set(oss.str().c_str());
	else pProp->Set(g_pgFocus_CalibrationCurveRequest);

	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocus::OnInputGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		SendCommand("gain"); 		// Get ADC gain
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.gain);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}
	else if (eAct == MM::AfterSet)
	{
		double result;
		pProp->Get(result);
		if (result > 0) {
			std::ostringstream oss;
			oss << "gain " << result;
			SendCommand(oss.str().c_str());
		}

	}


   return DEVICE_OK;
}


int pgFocus::OnInputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(ADC_VOLTAGE(deviceInfo_.ADC));
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnOutputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(DAC_VOLTAGE(deviceInfo_.DAC));
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnInputnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(ADC_VOLTAGE(deviceInfo_.ADC) * deviceInfo_.micronPerVolt * NMPERMICON);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnOutputnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		float result = DAC_VOLTAGE(deviceInfo_.DAC)  * deviceInfo_.micronPerVolt * NMPERMICON;
		pProp->Set(result);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnSlope(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SendCommand("slope");

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.slope);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return DEVICE_OK;
}

int pgFocus::OnResiduals(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SendCommand("residuals");
	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.residuals);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return DEVICE_OK;

}

int pgFocus::OnIntercept(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SendCommand("intercept");
	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.intercept);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return DEVICE_OK;

}

int pgFocus::OnMin(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.min);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


	return DEVICE_OK;
}

int pgFocus::OnMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.max);
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnLastError(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.error.c_str());
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnLastStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.status.c_str());
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}


   return DEVICE_OK;
}

int pgFocus::OnFirmware(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (deviceInfo_.version.compare(g_pgFocus_Default_String) == 0) SendCommand("version");

	if (eAct == MM::BeforeGet)
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		pProp->Set(deviceInfo_.version.c_str());
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

   return DEVICE_OK;
}

int pgFocus::OnLight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	SendCommand("l"); // get new light profile

	MM_THREAD_GUARD_LOCK(&mutex);
	if (eAct == MM::BeforeGet)
	{
		std::ostringstream oss;
		for (int x = 0; x < 128; x++)  {
			oss << deviceInfo_.lightArray[x];
			if ((x + 1)< 128) oss << ",";
		}

		pProp->Set(oss.str().c_str());
	}
	MM_THREAD_GUARD_UNLOCK(&mutex);

   return DEVICE_OK;
}

int pgFocus::OnWaitAfterLight(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(waitAfterLight_);
	} else if (eAct == MM::AfterSet)
	{
		pProp->Get(waitAfterLight_);
	}

   return DEVICE_OK;
}

int pgFocus::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		if (initialized_)
		{
			// revert
			pProp->Set(port_.c_str());
			return ERR_PORT_CHANGE_FORBIDDEN;
		}

	  pProp->Get(port_);
	}
	return DEVICE_OK;
}

int pgFocus::OnSerialTerminator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		bool success = 0;
		if (deviceInfo_.serialTerminatorText.compare(g_SerialTerminator_0_Text) == 0)
			success = pProp->Set(g_SerialTerminator_0_Text);
		else if (deviceInfo_.serialTerminatorText.compare(g_SerialTerminator_1_Text) == 0)
			success = pProp->Set(g_SerialTerminator_1_Text);
		else if (deviceInfo_.serialTerminatorText.compare(g_SerialTerminator_2_Text) == 0)
			success = pProp->Set(g_SerialTerminator_2_Text);
		else if (deviceInfo_.serialTerminatorText.compare(g_SerialTerminator_3_Text) == 0)
			success = pProp->Set(g_SerialTerminator_3_Text);

		if (!success)
		 return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);

		if (tmpstr.compare(g_SerialTerminator_0_Text) == 0){
			MM_THREAD_GUARD_LOCK(&mutex);
			deviceInfo_.serialTerminator = g_SerialTerminator_0;
			deviceInfo_.serialTerminatorText = g_SerialTerminator_0_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_1_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			deviceInfo_.serialTerminator = g_SerialTerminator_1;
			deviceInfo_.serialTerminatorText = g_SerialTerminator_1_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_2_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			deviceInfo_.serialTerminator = g_SerialTerminator_2;
			deviceInfo_.serialTerminatorText = g_SerialTerminator_2_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_3_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			deviceInfo_.serialTerminator = g_SerialTerminator_3;
			deviceInfo_.serialTerminatorText = g_SerialTerminator_3_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else {
		 return DEVICE_INVALID_PROPERTY_VALUE;
		}
	}
	return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Serial Port Monitor
///////////////////////////////////////////////////////////////////////////////

/*
 * Thread that continuously monitors messages from the pgFocus
 *
 * Inspired by ZeissCAN9 Monitor Class by Nico
 *
 */
pgFocusMonitoringThread::pgFocusMonitoringThread(pgFocus &pgfocus, MM::Core& core, pgFocusInfo *deviceInfo, bool debug) :
	pgfocus_(pgfocus),
	core_ (core),
	deviceInfo_(deviceInfo),
	debug_(debug),
	threadStop_ (true),
	intervalMs_(100) // check every 100 ms for new messages,
{
	port_ = pgfocus_.port_;
}

pgFocusMonitoringThread::~pgFocusMonitoringThread()
{
   Stop();
   wait();
   core_.LogMessage(&pgfocus_, "pgFocusMonitoringThread: Destructing pgFocus MonitoringThread", true);
}

void pgFocusMonitoringThread::Start()
{
	threadStop_ = false;
	activate();
}

long pgFocusMonitoringThread::getWaitTime()
{
	return (intervalMs_);
}

void pgFocusMonitoringThread::setWaitTime(long intervalUs)
{
	intervalMs_ = intervalUs;
}

int pgFocusMonitoringThread::svc()
{

	long wait;

	core_.LogMessage(&pgfocus_, "pgFocusMonitoringThread: Starting pgFocus Monitoring Thread", true);

	std::string serialAnswer_;

	char rcvBuf_[pgFocus::RCV_BUF_LENGTH];
	memset(rcvBuf_, 0, pgFocus::RCV_BUF_LENGTH);

	while (!threadStop_)
	{

		  int ret_ = core_.GetSerialAnswer(&pgfocus_,port_.c_str(), pgFocus::RCV_BUF_LENGTH, rcvBuf_, deviceInfo_->serialTerminator.c_str());
		  wait = 0;
		  if (ret_ == DEVICE_OK) {
			  if (debug_) core_.LogMessage(&pgfocus_, rcvBuf_, true);
			  parseMessage(rcvBuf_);
		  } else
		  {
			  wait = intervalMs_;
			  if (debug_) {
				  std::ostringstream oss;
				  oss << "Monitoring Thread: Waiting for pgFocus to bless us with its brilliance: " << ret_;
				  core_.LogMessage(&pgfocus_, oss.str().c_str(), true);
			  }
		  }

		  memset(rcvBuf_, 0, pgFocus::RCV_BUF_LENGTH);

		  if (wait > 0 ) {
			  CDeviceUtils::SleepMs(wait);
			  core_.SetSerialCommand(&pgfocus_,port_.c_str(),"v", "\r");
		  }
	}

	core_.LogMessage(&pgfocus_, "pgFocusMonitoringThread: Monitoring Thread finished", true);

	return 0;
}

void pgFocusMonitoringThread::parseMessage(char *message) {

	if (!deviceInfo_) return;

	if (!message) return;

	istringstream iss(message);

	do {
		std::string Mode;
		if (iss) iss >> Mode;

		// First word should be "STATS:"
		if (Mode.compare("STATS:") == 0) {
			// found STATS:
			// copy all the variables into local variables.

			if (iss) iss >> deviceInfo_->startTime;
			if (iss) iss >> deviceInfo_->loopTime;
			if (iss) iss >> deviceInfo_->min;
			if (iss) iss >> deviceInfo_->max;
			if (iss) iss >> deviceInfo_->DAC;
			if (iss) {
				iss >> deviceInfo_->offset;
				if (qoffsets_.size() > deviceInfo_->qoffsetMax) qoffsets_.pop_back();
				qoffsets_.push_front(deviceInfo_->offset);
				deviceInfo_->standardDeviation = standard_deviation();
			}
			if (iss) iss >> deviceInfo_->slope;
			if (iss) iss >> deviceInfo_->exposure;
			if (iss) iss >> deviceInfo_->ADC;
			if (iss) iss >> deviceInfo_->diffADC;

			if (debug_) {
				std::ostringstream oss;
				oss << Mode;
				oss << " " << deviceInfo_->startTime;
				oss << " " << deviceInfo_->loopTime;
				oss << " " << deviceInfo_->min;
				oss << " " << deviceInfo_->max;
				oss << " " << deviceInfo_->DAC;
				oss << " " << deviceInfo_->offset;
				oss << " " << deviceInfo_->slope;
				oss << " " << deviceInfo_->exposure;
				oss << " " << deviceInfo_->ADC;
				oss << " " << deviceInfo_->diffADC;
				core_.LogMessage(&pgfocus_, oss.str().c_str(), true);

			}
			if (deviceInfo_->continuousFocusing == true)
				deviceInfo_->mode = PGFOCUS_MODE_LOCKED;
			else deviceInfo_->mode = PGFOCUS_MODE_UNLOCKED;

			return;


		}

		if (Mode.compare("LIGHT:") == 0) {
			std::string Time;
			if (iss) iss >> Time;
			for (int x = 0; x < 128; x++)  {
				if (iss) iss >> deviceInfo_->lightArray[x];
			}
			deviceInfo_->mode = PGFOCUS_MODE_LIGHT_PROFILE;
			return;
		}

		if (Mode.compare("VERSION:") == 0) {
			if (iss) iss >> deviceInfo_->version;
			core_.LogMessage(&pgfocus_,  deviceInfo_->version.c_str(), true);
			return;
		}

		if (Mode.compare("CAL:") == 0) {
			deviceInfo_->mode = PGFOCUS_MODE_CALIBRATING;
			std::string temp;
			if (iss) { // first is voltage represented as DAU
				iss >> temp;
				deviceInfo_->calibration_curve.push_back(temp);
				if (debug_) core_.LogMessage(&pgfocus_, temp.c_str(), true);
			}

			if (iss) { // second is pixel position
				iss >> temp;
				deviceInfo_->calibration_curve.push_back(temp);
				if (debug_) core_.LogMessage(&pgfocus_, temp.c_str(), true);
			}

			return;
		}

		if (iss.str().compare("INFO: Calibration Activated") == 0) {
			deviceInfo_->mode = PGFOCUS_MODE_CALIBRATING;
			return;
		}

		if (iss.str().compare("INFO: Returning objective to default position") == 0) {
			deviceInfo_->mode = PGFOCUS_MODE_UNLOCKED;
			return;
		}

		if (Mode.compare("INTERCEPT:") == 0) {
			if (iss) iss >> deviceInfo_->intercept;
			return;
		}

		if (Mode.compare("SLOPE:") == 0) {
			if (iss) iss >> deviceInfo_->slope;
			return;
		}

		if (Mode.compare("GAIN:") == 0) {
			if (iss) iss >> deviceInfo_->gain;
			return;
		}

		if (Mode.compare("MPV:") == 0) {
			if (iss) iss >> deviceInfo_->micronPerVolt;
			return;
		}
		if (Mode.compare("RESIDUALS:") == 0) {
			if (iss) iss >> deviceInfo_->residuals;
			return;
		}
		if (Mode.compare("INFO:") == 0) {
			if (iss) iss >> deviceInfo_->status;
			return;
		}
		if (Mode.compare("ERROR:") == 0) {
			if (iss) iss >> deviceInfo_->error;
			return;
		}

	} while (iss);


	return;

}

double pgFocusMonitoringThread::standard_deviation ()
{

	double mean=0.0, sum_deviation=0.0;

	int i, n;

	n = qoffsets_.size();

	for(i=0; i < n; i++)
	{
		mean+=qoffsets_.at(i);
	}

	mean=mean/n;
	for(i=0; i<n;i++)
	{
		sum_deviation+=(qoffsets_.at(i) - mean) * (qoffsets_.at(i)- mean);
	}

	return sqrt(sum_deviation/(n - 1)); // sampling population
	//return sqrt(sum_deviation/(n)); // full population
}
