
///////////////////////////////////////////////////////////////////////////////
// FILE:          pgFocus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Focus stability
// COPYRIGHT:     University of Massachusetts Medical School, Worcester, MA 2014
//
//
//
// LICENSE:       LGPL
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
	RegisterDevice(g_DeviceNamepgFocusHub, MM::HubDevice, "Hub (required)");
	RegisterDevice(g_DeviceNamepgFocusStabilization, MM::AutoFocusDevice, "Focus Stabilization");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
	  return 0;

	if (strcmp(deviceName, g_DeviceNamepgFocusStabilization) == 0)
	{
		return new pgFocusStabilization ();
	}
	if (strcmp(deviceName, g_DeviceNamepgFocusHub) == 0)
	{
		return new pgFocusHub ();
	}

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

//////////////////////////////////////////////////////////////////////
// pgFocusHub: Open Source and Open Hardware reflection-based auto focusing.
// ----------------
pgFocusHub::pgFocusHub() :
initialized_ (false)
{
	portAvailable_ = false;
	autoExposure_ = true;
	version_ = "0";
	ret_ = DEVICE_OK;
	monitoringThread_ = NULL;
	pgFocusStabilizationDevice = NULL;
	debug_ = true;
	mode_ = 0;
	loopTime_ = 0;
	runningTime_ = 0;
	min_ = 0;
	max_ = 1023;
	DAC_ = 8192;
	exposure_ = 10000;
	ADC_ = 0;
	diffADC_ = 0;
	slope_ = 0;
	intercept_ = 0;
	residuals_ = 0;
	offset_ = 0;
	micronPerVolt_ = 10;
	qoffsetMax_ = 30;
	gain_ = 1.0;
	stepSizeUm_ = 0;
	continuousFocusing_ = 0;
	standardDeviation_ = 0.0;
	standardDeviation_nM_ = 0.0;
	version_ = g_Default_String;
	error_ = g_Default_String;
	status_ = g_Default_String;
	identity_ = g_Default_String;
	serialTerminator_ = g_SerialTerminator_2;
	serialTerminatorText_ = g_SerialTerminator_2_Text;

	//	"The communication port to the microscope can not be opened");
	SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	SetErrorText(ERR_NO_PORT_SET, "Port not set. The pgFocus Hub needs to use a Serial Port");
	SetErrorText(ERR_DEVICE_NOT_FOUND,"Could not find pgFocus with the correct firmware");

	CPropertyAction* pAct = new CPropertyAction(this, &pgFocusHub::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

pgFocusHub::~pgFocusHub()
{
   Shutdown();
}

void pgFocusHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNamepgFocusHub);
}

bool pgFocusHub::Busy()
{
   return false;
}

int pgFocusHub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNamepgFocusHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   CDeviceUtils::SleepMs(2000);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());

   CPropertyAction* pAct;

   pAct = new CPropertyAction(this, &pgFocusHub::OnFirmwareVersion);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Firmware, g_Default_String, MM::String, true, pAct));

   pAct = new CPropertyAction (this, &pgFocusHub::OnSerialTerminator);
   CreateProperty(g_SerialTerminator, g_SerialTerminator_0_Text, MM::String, false, pAct);
   	   AddAllowedValue(g_SerialTerminator, g_SerialTerminator_0_Text);
   	   AddAllowedValue(g_SerialTerminator, g_SerialTerminator_1_Text);
   	   AddAllowedValue(g_SerialTerminator, g_SerialTerminator_2_Text);
   	   AddAllowedValue(g_SerialTerminator, g_SerialTerminator_3_Text);


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;


   monitoringThread_ = new pgFocusMonitoringThread(*this,*GetCoreCallback(), debug_);
   monitoringThread_->Start();

   SendCommand("version");
   SendCommand("slope");
   SendCommand("gain");
   SendCommand("intercept");
   SendCommand("residuals");
   SendCommand("l");

   initialized_ = true;
   return DEVICE_OK;
}

MM::DeviceDetectionStatus pgFocusHub::DetectDevice(void)
{
	// Code modified from Nico's Arduino Device Adapter

	if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
         CDeviceUtils::SleepMs(2000);
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Off);
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "57600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();

         CDeviceUtils::SleepMs(2000);
         PurgeComPort(port_.c_str());

         ret_ = GetIdentity();

         if( ret_ != DEVICE_OK )
         {
            LogMessageCode(ret_,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // always restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}

int pgFocusHub::DetectInstalledDevices()
{
	// Code modified from Nico's Arduino Device Adapter
	if (MM::CanCommunicate == DetectDevice())
	{
		std::vector<std::string> peripherals;
		peripherals.clear();
		peripherals.push_back(g_DeviceNamepgFocusStabilization);
		for (size_t i=0; i < peripherals.size(); i++)
		{
			MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
			if (pDev)
			{
				AddInstalledDevice(pDev);
			}
		}
	}

	return DEVICE_OK;
}

int pgFocusHub::Shutdown()
{
   initialized_ = false;

   if (monitoringThread_ != NULL) {
	   GetCoreCallback()->LogMessage(this, "Waiting for service thread to stop", false);
	   monitoringThread_->Stop();
	   CDeviceUtils::SleepMs(monitoringThread_->getWaitTime());
	   delete(monitoringThread_);
   }

   monitoringThread_ = NULL;
   pgFocusStabilizationDevice = NULL;

   return DEVICE_OK;
}

int pgFocusHub::GetIdentity()
{

	std::string answer;

	if (identity_.compare(g_Default_String) == 0 ) {
		SendCommand("i");
		RETURN_ON_MM_ERROR(GetSerialAnswer(port_.c_str(), "\r\n", answer));

		if (answer != g_pgFocus_Identity) {
			identity_ = g_Default_String;
			return ERR_DEVICE_NOT_FOUND;
		} else identity_ = answer;
	}

	return DEVICE_OK;

}

const char * pgFocusHub::GetFirmwareVersion() {

	std::string answer;

	if (version_.compare(g_Default_String) == 0) {
		SendCommand("version");
		if (monitoringThread_ != NULL) {
			if (monitoringThread_->isRunning() == false) {
				GetSerialAnswer(port_.c_str(), "\r\n", answer);

				MM_THREAD_GUARD_LOCK(&mutex);
				version_ = answer;
				MM_THREAD_GUARD_UNLOCK(&mutex);
			}
			else answer = version_; // we already should have the version if thread is started
		}
	}
	else answer = version_;

	return answer.c_str();
}

void pgFocusHub::SetFocusDevice (pgFocusStabilization *device)
{
	pgFocusStabilizationDevice = device;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int pgFocusHub::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(version_.c_str());
	}
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::OnSerialTerminator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		bool success = 0;
		if (serialTerminatorText_.compare(g_SerialTerminator_0_Text) == 0)
			success = pProp->Set(g_SerialTerminator_0_Text);
		else if (serialTerminatorText_.compare(g_SerialTerminator_1_Text) == 0)
			success = pProp->Set(g_SerialTerminator_1_Text);
		else if (serialTerminatorText_.compare(g_SerialTerminator_2_Text) == 0)
			success = pProp->Set(g_SerialTerminator_2_Text);
		else if (serialTerminatorText_.compare(g_SerialTerminator_3_Text) == 0)
			success = pProp->Set(g_SerialTerminator_3_Text);

		if (!success)
		 return DEVICE_INVALID_PROPERTY_VALUE;
	}
	else if (eAct == MM::AfterSet) {
		string tmpstr;
		pProp->Get(tmpstr);

		if (tmpstr.compare(g_SerialTerminator_0_Text) == 0){
			MM_THREAD_GUARD_LOCK(&mutex);
			serialTerminator_ = g_SerialTerminator_0;
			serialTerminatorText_ = g_SerialTerminator_0_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_1_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			serialTerminator_ = g_SerialTerminator_1;
			serialTerminatorText_ = g_SerialTerminator_1_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_2_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			serialTerminator_ = g_SerialTerminator_2;
			serialTerminatorText_ = g_SerialTerminator_2_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else if (tmpstr.compare(g_SerialTerminator_3_Text) == 0)
		{
			MM_THREAD_GUARD_LOCK(&mutex);
			serialTerminator_ = g_SerialTerminator_3;
			serialTerminatorText_ = g_SerialTerminator_3_Text;
			MM_THREAD_GUARD_UNLOCK(&mutex);
		}
		else {
		 return DEVICE_INVALID_PROPERTY_VALUE;
		}
	}
	return DEVICE_OK;
}

int pgFocusHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      portAvailable_ = true;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Serial Communication
///////////////////////////////////////////////////////////////////////////////


int pgFocusHub::SetCommand(std::string command)
{
   string answer;
   // query command

   int ret = SendCommand(command.c_str());

   if (ret != DEVICE_OK)
      return ret;

   return ret;
}

// Communication "send" utility functions:
int pgFocusHub::SendCommand(const char *command)
{

	RETURN_ON_MM_ERROR ( ClearPort() );
	// send command
	RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command, "\r"));

	return DEVICE_OK;
}

// Communication "send" utility function:
int pgFocusHub::SendCommand(std::string command)
{

	RETURN_ON_MM_ERROR ( ClearPort() );
	// send command
	RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command.c_str(), "\r"));

	return DEVICE_OK;
}

int pgFocusHub::ClearPort()
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

std::string pgFocusHub::GetPort()
{
	std::string port;
	MM_THREAD_GUARD_LOCK(&mutex);
	port = port_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return port;
}

///////////////////////////////////////////////////////////////////////////////
// Normal Functions
///////////////////////////////////////////////////////////////////////////////

double pgFocusHub::standard_deviation ()
{

	double mean=0.0, sum_deviation=0.0;

	int i;
	size_t n;

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


///////////////////////////////////////////////////////////////////////////////
// Set/Get Parameters
///////////////////////////////////////////////////////////////////////////////


double pgFocusHub::GetOffset()
{
	double answer;
	MM_THREAD_GUARD_LOCK(&mutex);
	answer = offset_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::AddOffset(double offset)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	offset_ = offset;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (qoffsets_.size() > qoffsetMax_) qoffsets_.pop_back();
	qoffsets_.push_front(offset_);
	standardDeviation_ = standard_deviation();

	return DEVICE_OK;
}

int pgFocusHub::SetOffset(double offset, bool hardware)
{
	if (offset >=0 && offset < 128) {
		MM_THREAD_GUARD_LOCK(&mutex);
		offset_ = offset;
		MM_THREAD_GUARD_UNLOCK(&mutex);
		if (hardware) {
			std::ostringstream oss;
			oss << "offset " << offset;
			SendCommand(oss.str().c_str());
		}
	} else return ERR_OUT_OF_RANGE;

	return DEVICE_OK;
}

long pgFocusHub::GetMicronPerVolt()
{
	int answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = micronPerVolt_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

std::string pgFocusHub::GetFocusMode()
{

	std::string focusMode;

	MM_THREAD_GUARD_LOCK(&mutex);
	if (mode_ == PGFOCUS_MODE_UNLOCK) {
		focusMode = focusMode_ = g_pgFocus_Unlock;
	}

	if (mode_ == PGFOCUS_MODE_LOCK) {
		focusMode = focusMode_ = g_pgFocus_Lock;
	}

	if (mode_ == PGFOCUS_MODE_CALIBRATION) {
		focusMode = focusMode_ = g_pgFocus_Calibration;
	}

	MM_THREAD_GUARD_UNLOCK(&mutex);

	return focusMode;
}

int pgFocusHub::SetFocusMode(std::string focusMode)
{

	if (focusMode_.compare(focusMode) != 0) {
		focusMode_ = focusMode;
		if (focusMode_.compare(g_pgFocus_Lock) == 0) {
			LogMessage("Continuous focusing enabled");
			SetContinuousFocusing(true);
		}

		if (focusMode_.compare(g_pgFocus_Unlock) == 0) {
			LogMessage("Continuous focusing disabled");
			SetContinuousFocusing(false);
		}

		if (focusMode_.compare(g_pgFocus_Calibration) == 0) {
			LogMessage("Calibration enabled");
			SetContinuousFocusing(false); // must be first
			SetFocusMode(PGFOCUS_MODE_CALIBRATION);
			ClearCalibrationCurve();
			SendCommand("c");
		}
	}

	return DEVICE_OK;
}

int pgFocusHub::SetFocusMode(int mode)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	mode_ = mode;
	if (mode_ == PGFOCUS_MODE_UNLOCK) {
		focusMode_ = g_pgFocus_Unlock;
	}

	if (mode_ == PGFOCUS_MODE_LOCK) {
		focusMode_ = g_pgFocus_Lock;
	}

	if (mode_ == PGFOCUS_MODE_CALIBRATION) {
		focusMode_ = g_pgFocus_Calibration;
	}

	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::SetContinuousFocusing(bool mode)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	continuousFocusing_ = mode;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (continuousFocusing_) {
		SendCommand("f"); // start pgFocus
		SetFocusMode(PGFOCUS_MODE_LOCK);
	}
	else {
		SendCommand("s"); // stop pgFocus;
		SetFocusMode(PGFOCUS_MODE_UNLOCK);
	}



	return DEVICE_OK;
}

int pgFocusHub::GetContinuousFocusing(bool& state)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	state = continuousFocusing_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}


int pgFocusHub::SetMicronPerVolt(int mpv, bool hardware) {

	if (mpv > 0) {

		MM_THREAD_GUARD_LOCK(&mutex);
		micronPerVolt_ = mpv;
		MM_THREAD_GUARD_UNLOCK(&mutex);

		if (hardware) {
			std::ostringstream oss;
			oss << "mpv " << mpv;
			SendCommand(oss.str().c_str());
		}

	} else return ERR_OUT_OF_RANGE;

	return DEVICE_OK;
}

double pgFocusHub::GetStandardDeviation()
{
	double answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = standardDeviation_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}


int pgFocusHub::SetStandardDeviation(double SD )
{
	MM_THREAD_GUARD_LOCK(&mutex);
	standardDeviation_ = SD;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::SetWaitTime(long time)
{
	if (!monitoringThread_) return ERR_SERVICE_THREAD_NOT_FOUND;

	MM_THREAD_GUARD_LOCK(&mutex);
	if (time >=0) monitoringThread_->setWaitTime(time);
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetWaitTime()
{
	long answer;

	if (!monitoringThread_) return ERR_SERVICE_THREAD_NOT_FOUND;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = monitoringThread_->getWaitTime();
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

long pgFocusHub::GetLoopTime()
{
	long answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = loopTime_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetLoopTime(long time)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	loopTime_ = time;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetRunningTime()
{
	long answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = runningTime_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetRunningTime(long time)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	runningTime_ = time;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetExposure()
{
	long answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = exposure_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetExposure(long exposure, bool hardware)
{
	// This should only be called from monitoring thread

	if (exposure < 0) return ERR_OUT_OF_RANGE;

	if (exposure != exposure_) {
		MM_THREAD_GUARD_LOCK(&mutex);
		exposure_ = exposure;
		MM_THREAD_GUARD_UNLOCK(&mutex);

		if (hardware) {
			std::ostringstream oss;
			oss << "exposure " << exposure;
			SendCommand(oss.str().c_str());
		}
	}

	return DEVICE_OK;
}


bool pgFocusHub::GetAutoExposure()
{
	bool autoExposure;

	MM_THREAD_GUARD_LOCK(&mutex);
	autoExposure = autoExposure_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return (autoExposure);

}

int pgFocusHub::SetAutoExposure(bool autoExposure)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	autoExposure_ = autoExposure;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (autoExposure == true) SendCommand("E"); // default, turn on auto exposure
	else SendCommand("e"); // default, turn off auto exposure

	return DEVICE_OK;
}


std::string pgFocusHub::GetCalibrationCurve()
{

	std::ostringstream oss;

	MM_THREAD_GUARD_LOCK(&mutex);

	for (std::vector<std::string>::size_type i = 0; i< calibration_curve_.size();i++)
	{
		oss << calibration_curve_[i] << ",";
		oss << calibration_curve_[++i];
		if ((i + 1) < calibration_curve_.size()) oss << ",";
	}

	MM_THREAD_GUARD_UNLOCK(&mutex);

	return(oss.str());
}

int pgFocusHub::AddCalibration(std::string dau, std::string pixel)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	calibration_curve_.push_back(dau);
	calibration_curve_.push_back(pixel);
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::ClearCalibrationCurve()
{

	MM_THREAD_GUARD_LOCK(&mutex);
	calibration_curve_.clear();
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

std::string pgFocusHub::GetSerialTerminator()
{

	std::string serialTerminator;

	MM_THREAD_GUARD_LOCK(&mutex);

	serialTerminator = serialTerminator_;

	MM_THREAD_GUARD_UNLOCK(&mutex);


	return(serialTerminator);
}

int pgFocusHub::SetSerialTerminator(std::string serialTerminator)
{


	MM_THREAD_GUARD_LOCK(&mutex);

	serialTerminator_ = serialTerminator;

	MM_THREAD_GUARD_UNLOCK(&mutex);


	return DEVICE_OK;
}

double pgFocusHub::GetGain()
{

	double answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = gain_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}


int pgFocusHub::SetGain(double gain, bool hardware)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	gain_ = gain;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (hardware) {
		std::ostringstream oss;
		oss << "gain " << gain;
		SendCommand(oss.str().c_str());
	}

	return DEVICE_OK;
}

// in DAU, not volts
long pgFocusHub::GetADC()
{

	long DAU;

	MM_THREAD_GUARD_LOCK(&mutex);
	DAU = ADC_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DAU;
}



int pgFocusHub::SetADC(long DAU)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	ADC_ = DAU;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetDAC()
{

	long DAU;

	MM_THREAD_GUARD_LOCK(&mutex);
	DAU = DAC_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DAU;
}


int pgFocusHub::SetDAC(long DAU, bool hardware)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	DAC_ = DAU;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	if (hardware) {
                std::ostringstream oss;
                oss << "voltage " << DAU;
                SendCommand(oss.str().c_str());
        }

	return DEVICE_OK;
}

int pgFocusHub::SetDiffADC(long DAU)
{


	MM_THREAD_GUARD_LOCK(&mutex);
	diffADC_ = DAU;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

double pgFocusHub::GetSlope()
{

	double answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = slope_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetSlope(double slope)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	slope_ = slope;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

double pgFocusHub::GetResiduals()
{

	double answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = residuals_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;

}

int pgFocusHub::SetResiduals(double residuals)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	residuals_ = residuals;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

double pgFocusHub::GetIntercept()
{

	double answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = intercept_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetIntercept(double intercept)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	intercept_ = intercept;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetMaxLight()
{

	long answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = max_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

long pgFocusHub::GetMinLight()
{

	long answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = min_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer;
}

int pgFocusHub::SetMaxLight(long max)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	max_ = max;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::SetMinLight(long min)
{

	MM_THREAD_GUARD_LOCK(&mutex);
	min_ = min;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

const char * pgFocusHub::GetLastError()
{

	std::string answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = error_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer.c_str();
}

const char * pgFocusHub::GetLastStatus()
{

	std::string answer;

	MM_THREAD_GUARD_LOCK(&mutex);
	answer = status_;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return answer.c_str();
}


int pgFocusHub::SetLastError(std::string error)
{


	MM_THREAD_GUARD_LOCK(&mutex);
	error_ = error;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

int pgFocusHub::SetLastStatus(std::string status)
{


	MM_THREAD_GUARD_LOCK(&mutex);
	status_ = status;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;

}

int pgFocusHub::SetFirmwareVersion(std::string version)
{
	MM_THREAD_GUARD_LOCK(&mutex);
	version_ = version;
	MM_THREAD_GUARD_UNLOCK(&mutex);

	return DEVICE_OK;
}

long pgFocusHub::GetLight(long index)
{

	long answer = 0;

	if ((index >= 0) && (index < 128))
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		answer  = lightArray_[index];
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return answer;
}

int pgFocusHub::SetLight(long index, long light)
{


	if ((index >= 0) && (index < 128))
	{
		MM_THREAD_GUARD_LOCK(&mutex);
		lightArray_[index] = light;
		MM_THREAD_GUARD_UNLOCK(&mutex);
	}

	return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////
// pgFocus: Z Axis
// ---

/*************************************************************
 * ZeissFocusStage: Micro-Manager implementation of focus drive
 */
/*pgFocusAxis::pgFocusAxis ():
   stepSize_um_(0.001),
   initialized_ (false),
{

	InitializeDefaultErrorMessages();

	SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Zeiss Axis to work");
	SetErrorText(ERR_MODULE_NOT_FOUND, "This Axis is not installed in this Zeiss microscope");

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceNamepgFocusAxis, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Open Source and Open Hardware Focus Stabilization Device", MM::String, true);

	// parent ID display
	CreateHubIDProperty();
}

pgFocusAxis::~pgFocusAxis()
{
	Shutdown();
}

bool pgFocusAxis::Busy()
{
	return false;
}

void pgFocusAxis::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNamepgFocusAxis);
}


int pgFocusAxis::Initialize()
{


	// set property list
	// ----------------
	// Position
	CPropertyAction* pAct = new CPropertyAction(this, &pgFocusAxis::OnPosition);
	int ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
	  return ret;

	ret = UpdateStatus();
	if (ret!= DEVICE_OK)
	  return ret;

	initialized_ = true;

	return DEVICE_OK;
}

int pgFocusAxis::Shutdown()
{
	if (initialized_) initialized_ = false;
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Axis API
///////////////////////////////////////////////////////////////////////////////

int pgFocusAxis::SetPositionUm(double pos)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	long steps = (long)(pos / stepSize_um_);
	int ret = SetPositionSteps(steps);
	if (ret != DEVICE_OK)
	  return ret;

	return DEVICE_OK;
}

int pgFocusAxis::GetPositionUm(double& pos)
{
	long steps;
	int ret = GetPositionSteps(steps);
	if (ret != DEVICE_OK)
	  return ret;
	pos = steps * stepSize_um_;

	return DEVICE_OK;
}

int pgFocusAxis::SetPositionSteps(long steps)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	return ZeissAxis::SetPosition(*this, *GetCoreCallback(), devId_, steps, (ZeissByte) (moveMode_ & velocity_));
}

int pgFocusAxis::GetPositionSteps(long& steps)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	return ZeissDevice::GetPosition(*this, *GetCoreCallback(), devId_, (ZeissLong&) steps);
}

int pgFocusAxis::SetOrigin()
{
	return DEVICE_OK;
}
*/
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int pgFocusAxis::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)   {
		double pos;
		int ret = GetPositionUm(pos);
		if (ret != DEVICE_OK)
		 return ret;
		pProp->Set(pos);
	}
	else if (eAct == MM::AfterSet)
	{
		double pos;
		pProp->Get(pos);
		int ret = SetPositionUm(pos);
		if (ret != DEVICE_OK)
		 return ret;
	}

	return DEVICE_OK;
}

//////////////////////////////////////////////////////////////////////
// pgFocus: Focus Stabilization
// ----------------

pgFocusStabilization::pgFocusStabilization() :
	initialized_(false)
{
	busy_ = false;
	justCalibrated_ = false;
	ret_ = DEVICE_OK;
	waitAfterLock_ = 100;
	waitAfterLight_ = 1000;
	exposure_ = 10000;
	DAC_nM_ = 0;
	ADC_nM_ = 0;

	InitializeDefaultErrorMessages();

	SetErrorText(ERR_HUB_NOT_FOUND,			"This device need the pgFocus Hub to be installed");
	SetErrorText(ERR_NOT_LOCKED, 			"The pgFocus failed to lock");
	SetErrorText(ERR_NOT_CALIBRATED, 		"pgFocus is not calibrated. Try focusing close to a coverslip and selecting 'Calibrate'");
	SetErrorText(ERR_OUT_OF_RANGE, 			"The number you entered is outside of the range");
	SetErrorText(ERR_STILL_CALIBRATING,		"pgFocus is calibrating. Please wait...");

	// create pre-initialization properties
	// ------------------------------------

	// Name
	CreateProperty(MM::g_Keyword_Name, g_DeviceNamepgFocusStabilization, MM::String, true);

	// Description
	CreateProperty(MM::g_Keyword_Description, "Open Source and Open Hardware Focus Stabilization Device", MM::String, true);

	// parent ID display
	CreateHubIDProperty();
}

pgFocusStabilization::~pgFocusStabilization()
{
	Shutdown();
}

int pgFocusStabilization::Shutdown()
{

	initialized_ = false;
	GetCoreCallback()->LogMessage(this, "pgFocusStabilization::Shutdown()", false);

	return DEVICE_OK;
}

int pgFocusStabilization::Initialize()
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable()) {
		   return ERR_NO_PORT_SET;
	}
	char hubLabel[MM::MaxStrLength];
	hub->GetLabel(hubLabel);
	SetParentID(hubLabel);

	hub->SetFocusDevice(this); // introduce ourselves to our hub

	CPropertyAction* pAct;
	LogMessage("pgFocus::Initialize()");

	if (initialized_)
	  return DEVICE_OK;

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnFocusMode);
	RETURN_ON_MM_ERROR(CreateProperty (g_pgFocus_Mode, g_pgFocus_Unlock, MM::String, false, pAct));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Lock));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Unlock));
	RETURN_ON_MM_ERROR(AddAllowedValue(g_pgFocus_Mode, g_pgFocus_Calibration));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Exposure, "10000", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnInputGain);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_Gain, g_Default_Float, MM::Float, false, pAct));

	// User Modifiable
	pAct = new CPropertyAction(this, &pgFocusStabilization::OnWaitAfterLock);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Wait_Time_Lock, "100", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnMicronsPerVolt);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Microns_Volt, "10", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnWaitAfterMessage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Wait_Time_Message, "250", MM::Integer, false, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnAutoExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Auto_Exposure, g_On, MM::String, false, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnWaitAfterLight);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Wait, "1000", MM::Integer, false, pAct))

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnOffset);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Offset, g_Default_Float, MM::Float, false, pAct));

	// Non User  Modifiable
	pAct = new CPropertyAction(this, &pgFocusStabilization::OnFirmwareVersion);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Firmware, g_Default_String, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnOutputVoltage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Output_Voltage, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnOutputnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Output_nM, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnInputVoltage);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_Voltage, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnInputnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Input_nM, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnSlope);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Slope, g_Default_Integer, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnSD);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Standard_Deviation, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnSDnM);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Standard_Deviation_nM, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnCurrentExposure);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Actual_Exposure, "10000", MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnMin);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Min, g_Default_Integer, MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnMax);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Light_Max, g_Default_Integer, MM::Integer, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnResiduals);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Residuals, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnIntercept);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Intercept, g_Default_Float, MM::Float, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnLight);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_LightProfile, g_Default_String, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnCalibrationCurve);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_CalibrationCurve, g_pgFocus_CalibrationCurveRequest, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnLastError);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Last_Error, g_Default_String, MM::String, true, pAct));

	pAct = new CPropertyAction(this, &pgFocusStabilization::OnLastStatus);
	RETURN_ON_MM_ERROR(CreateProperty(g_pgFocus_Last_Status, g_Default_String, MM::String, true, pAct));


	ret_ = UpdateStatus();
	if (ret_ != DEVICE_OK)
		return ret_;

	initialized_ = true;
	return DEVICE_OK;
}

bool pgFocusStabilization::Busy()
{
	return busy_;
}



void pgFocusStabilization::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_DeviceNamepgFocusStabilization);
}



///////////////////////////////////////////////////////////////////////////////
// Autofocus API
///////////////////////////////////////////////////////////////////////////////


bool pgFocusStabilization::IsContinuousFocusLocked()
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return false;
		//return ERR_NO_PORT_SET;

	bool continuousFocusing_;
	hub->GetContinuousFocusing(continuousFocusing_);

	return (continuousFocusing_);

}


int pgFocusStabilization::SetOffset(double offset)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	return (hub->SetOffset(offset));

}


int pgFocusStabilization::GetOffset(double& offset)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	offset = hub->GetOffset();

	return DEVICE_OK;
}

/**
 * Does a "one-shot" autofocus: locks and then unlocks again
 */
int pgFocusStabilization::FullFocus()
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

int pgFocusStabilization::IncrementalFocus()
{
   return FullFocus();
}

int pgFocusStabilization::GetLastFocusScore(double& score)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;
	// Perhaps return standard deviation?
	score = hub->GetStandardDeviation();

	return DEVICE_OK;
}

int pgFocusStabilization::GetCurrentFocusScore(double& score)
{

	score = GetLastFocusScore(score);
	// compute Standard Deviation
	//standardDeviation_ = ;

	return 0;
}

int pgFocusStabilization::AutoSetParameters() {
   return 0;
};

int pgFocusStabilization::UpdateFocusMode()
{
	// query pgFocus current focus mode
	return DEVICE_OK;
}

int pgFocusStabilization::SetContinuousFocusing(bool mode)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	hub->SetContinuousFocusing(mode);

	OnPropertyChanged(g_pgFocus_Mode, hub->GetFocusMode().c_str());

	return DEVICE_OK;
}

int pgFocusStabilization::GetContinuousFocusing(bool& state)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	hub->GetContinuousFocusing(state);
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int pgFocusStabilization::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetFirmwareVersion());
	}
	return DEVICE_OK;
}

int pgFocusStabilization::OnMicronsPerVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;


	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetMicronPerVolt());
	}else if (eAct == MM::AfterSet)
	{
		long temp;
		pProp->Get(temp);
		if (temp > 0) {
			hub->SetMicronPerVolt(temp);
			OnPropertyChanged(g_pgFocus_Microns_Volt, CDeviceUtils::ConvertToString(temp));
		} else return ERR_OUT_OF_RANGE;
	}

	return DEVICE_OK;

}

int pgFocusStabilization::OnSD(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{

		pProp->Set(hub->GetStandardDeviation());
	}


	return DEVICE_OK;

}

int pgFocusStabilization::OnSDnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		double answer= hub->GetStandardDeviation();
		pProp->Set((answer * hub->GetSlope() / DAUPERVOLT) * hub->GetMicronPerVolt() * NMPERMICON);
	}

	return DEVICE_OK;

}

int pgFocusStabilization::OnFocusMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	focusMode_ = hub->GetFocusMode();

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(focusMode_.c_str());
	}
	else if (eAct == MM::AfterSet)
	{
		string focusMode;
		pProp->Get(focusMode);
		if (focusMode.compare(focusMode_) != 0) {
			hub->SetFocusMode(focusMode);
			OnPropertyChanged(g_pgFocus_Mode, focusMode.c_str());
			focusMode_ = focusMode;
		}
	}

	return DEVICE_OK;

}

int pgFocusStabilization::OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(waitAfterLock_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(waitAfterLock_);
		OnPropertyChanged(g_pgFocus_Wait_Time_Lock, CDeviceUtils::ConvertToString(waitAfterLock_));
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnWaitAfterMessage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
	   pProp->Set(hub->GetWaitTime());
	}
	else if (eAct == MM::AfterSet)
	{
		long time;
		pProp->Get(time);
		if (time >=0) {
			hub->SetWaitTime(time);
			OnPropertyChanged(g_pgFocus_Wait_Time_Message, CDeviceUtils::ConvertToString(time));
		}
	}

   return DEVICE_OK;
}

int pgFocusStabilization::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	double offset;

	if (eAct == MM::BeforeGet)
	{
		GetOffset(offset);
		pProp->Set(offset);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(offset);
		SetOffset(offset);
		OnPropertyChanged(g_pgFocus_Offset, CDeviceUtils::ConvertToString(offset));
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(exposure_);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(exposure_);
		if (exposure_ > 0) {
			hub->SetExposure(exposure_);
			OnPropertyChanged(g_pgFocus_Actual_Exposure, CDeviceUtils::ConvertToString(exposure_));
			OnPropertyChanged(g_pgFocus_Exposure, CDeviceUtils::ConvertToString(exposure_));
		}
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		if (hub->GetAutoExposure()) pProp->Set(g_On);
		else pProp->Set(g_Off);
	}
	else if (eAct == MM::AfterSet)
	{
		std::string result;
		pProp->Get(result);
		if (result.compare(g_On) == 0) {
			hub->SetAutoExposure(true);
			OnPropertyChanged(g_pgFocus_Auto_Exposure, g_On);
		}
		else {
			hub->SetAutoExposure(false);
			OnPropertyChanged(g_pgFocus_Auto_Exposure, g_Off);
		}
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnCurrentExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetExposure());
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnCalibrationCurve(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		std::string Curve = hub->GetCalibrationCurve();

		if (Curve.empty() == false)  {
			pProp->Set(Curve.c_str());
		}
		else {
			if (hub->GetFocusMode().compare(g_pgFocus_Calibration) == 0)
				pProp->Set(g_pgFocus_CalibrationCurveWait);
			else pProp->Set(g_pgFocus_CalibrationCurveRequest);
		}
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnInputGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetGain());
	}
	else if (eAct == MM::AfterSet)
	{
		double result;

		pProp->Get(result);

		if (result > 0) {
			hub->SetGain(result);
			OnPropertyChanged(g_pgFocus_Input_Gain, CDeviceUtils::ConvertToString(result));
		}
	}


   return DEVICE_OK;
}


int pgFocusStabilization::OnInputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(ADC_VOLTAGE(hub->GetADC()));
	}


   return DEVICE_OK;
}

int pgFocusStabilization::OnOutputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(DAC_VOLTAGE(hub->GetDAC()));
	}

   return DEVICE_OK;
}

int pgFocusStabilization::OnInputnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(ADC_VOLTAGE(hub->GetADC()) * hub->GetMicronPerVolt() * NMPERMICON);
	}


   return DEVICE_OK;
}

int pgFocusStabilization::OnOutputnM(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		float result = DAC_VOLTAGE(hub->GetDAC())  * hub->GetMicronPerVolt() * NMPERMICON;
		pProp->Set(result);
	}


   return DEVICE_OK;
}

int pgFocusStabilization::OnSlope(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetSlope());
	}

	return DEVICE_OK;
}

int pgFocusStabilization::OnResiduals(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetResiduals());
	}

	return DEVICE_OK;

}

int pgFocusStabilization::OnIntercept(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;


	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetIntercept());
	}

	return DEVICE_OK;

}

int pgFocusStabilization::OnMin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetMinLight());
	}


	return DEVICE_OK;
}

int pgFocusStabilization::OnMax(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetMaxLight());
	}


   return DEVICE_OK;
}

int pgFocusStabilization::OnLastError(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		//return ERR_NO_PORT_SET;
		return false;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetLastError());
	}


   return DEVICE_OK;
}

int pgFocusStabilization::OnLastStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(hub->GetLastStatus());
	}


   return DEVICE_OK;
}


int pgFocusStabilization::OnLight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	pgFocusHub* hub = static_cast<pgFocusHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	hub->SendCommand("l"); // get new light profile

	if (eAct == MM::BeforeGet)
	{
		std::ostringstream oss;
		for (int x = 0; x < 128; x++)  {
			oss << hub->GetLight(x);
			if ((x + 1)< 128) oss << ",";
		}

		pProp->Set(oss.str().c_str());
	}

   return DEVICE_OK;
}

int pgFocusStabilization::OnWaitAfterLight(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
	{
		pProp->Set(waitAfterLight_);
	} else if (eAct == MM::AfterSet)
	{
		pProp->Get(waitAfterLight_);
		OnPropertyChanged(g_pgFocus_Light_Wait, CDeviceUtils::ConvertToString(waitAfterLight_));
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
pgFocusMonitoringThread::pgFocusMonitoringThread(pgFocusHub &pgfocushub, MM::Core& core, bool debug) :
	pgfocushub_(pgfocushub),
	core_ (core),
	debug_(debug),
	threadStop_ (true),
	intervalMs_(250) // check every 100 ms for new messages,
{
	port_ = pgfocushub_.GetPort();
}

pgFocusMonitoringThread::~pgFocusMonitoringThread()
{
   Stop();
   wait();
   core_.LogMessage(&pgfocushub_, "pgFocusMonitoringThread: Destructing pgFocus MonitoringThread", true);
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

bool pgFocusMonitoringThread::isRunning()
{
	if (!threadStop_) return true;
	return false;
}

void pgFocusMonitoringThread::setWaitTime(long intervalMs)
{
	intervalMs_ = intervalMs;
}

int pgFocusMonitoringThread::svc()
{

	core_.LogMessage(&pgfocushub_, "pgFocusMonitoringThread: Starting pgFocus Monitoring Thread", true);
	unsigned long charsRead = 0;
	char rcvBuf[pgFocusHub::RCV_BUF_LENGTH];
	std::string message;
	memset(rcvBuf, 0, pgFocusHub::RCV_BUF_LENGTH);

	while (!threadStop_)
	{
		do {

			int ret = core_.ReadFromSerial(&pgfocushub_, pgfocushub_.port_.c_str(), (unsigned char *)rcvBuf, pgFocusHub::RCV_BUF_LENGTH, charsRead);

			if (ret == DEVICE_OK) {
				if (debug_) {
					std::ostringstream os;
					os << "Monitoring Thread incoming message: " << rcvBuf;
					core_.LogMessage(&pgfocushub_, os.str().c_str(), true);
				}
				if (charsRead > 0) {
					for (unsigned int x = 0; x < charsRead; x++) {
						if (rcvBuf[x] != '\r' && rcvBuf[x] != '\n' ) message += rcvBuf[x];
						else if (rcvBuf[x] == '\r') {
							// found carriage return
							parseMessage(message);
							// only clear if found a carriage return, otherwise, save for next ReadFromSerial
							message.clear();
						}
					}
				}

			} else
			{
				std::ostringstream oss;
				oss << "Monitoring Thread: ERROR while reading from serial port, error code: " << ret;
				core_.LogMessage(&pgfocushub_, oss.str().c_str(), false);
			}

			memset(rcvBuf, 0, pgFocusHub::RCV_BUF_LENGTH);


		} while ((charsRead != 0) && (!threadStop_));

		CDeviceUtils::SleepMs(intervalMs_);
		core_.SetSerialCommand(&pgfocushub_,port_.c_str(),"v", "\r");
	}

	core_.LogMessage(&pgfocushub_, "pgFocusMonitoringThread: Monitoring Thread finished", true);

	return 0;
}

void pgFocusMonitoringThread::parseMessage(std::string message) {


	if (message.empty() == true) return;

	istringstream iss(message);

	long lResult;
	double dResult;
	std::string sResult, sDAU, sPixel;

	do {
		std::string Mode;
		if (iss) iss >> Mode;

		core_.LogMessage(&pgfocushub_, message.c_str(), true);
		// First word should be "STATS:"
		if (Mode.compare("STATS:") == 0) {
			// found STATS:
			// copy all the variables into local variables.

			if (iss) {iss >> lResult; pgfocushub_.SetRunningTime(lResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetLoopTime(lResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetMinLight(lResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetMaxLight(lResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetDAC(lResult, false);}
			if (iss) {iss >> dResult; pgfocushub_.AddOffset(dResult);}
			if (iss) {iss >> dResult; pgfocushub_.SetSlope(dResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetExposure(lResult,false);}
			if (iss) {iss >> lResult; pgfocushub_.SetADC(lResult);}
			if (iss) {iss >> lResult; pgfocushub_.SetDiffADC(lResult);}

			return;

		}

		if (Mode.compare("LIGHT:") == 0) {
			std::string Time;
			if (iss) iss >> Time;
			for (int x = 0; x < 128; x++)  {
				if (iss) {iss >> lResult;pgfocushub_.SetLight(x,lResult);}
			}
			return;
		}

		if (Mode.compare("CAL:") == 0) {
			pgfocushub_.SetFocusMode(PGFOCUS_MODE_CALIBRATION);
			if (iss) {
				iss >> sDAU;
				iss >> sPixel;
				pgfocushub_.AddCalibration(sDAU,sPixel);
				core_.LogMessage(&pgfocushub_, "Found CAL: adding", true);
			}
			return;
		}

		// These two comparisons must come before the "INFO:" comparison done later
		if (iss.str().compare("INFO: Calibration Activated") == 0) {pgfocushub_.SetFocusMode(PGFOCUS_MODE_CALIBRATION); return;	}

		if (iss.str().compare("INFO: Returning objective to default position") == 0) {pgfocushub_.SetFocusMode(PGFOCUS_MODE_UNLOCK); return;}

		if (Mode.compare("VERSION:") == 0) { if (iss) { iss >> sResult;pgfocushub_.SetFirmwareVersion(sResult); } return;}

		if (Mode.compare("INTERCEPT:") == 0) { if (iss) { iss >> dResult;pgfocushub_.SetIntercept(dResult);} return;}

		if (Mode.compare("SLOPE:") == 0) { if (iss) { iss >> dResult; pgfocushub_.SetSlope(dResult);} return;}

		if (Mode.compare("GAIN:") == 0) { if (iss) { iss >> dResult; pgfocushub_.SetGain(dResult);} return;}

		if (Mode.compare("MPV:") == 0) { if (iss) { iss >> lResult; pgfocushub_.SetMicronPerVolt(lResult);} return;}

		if (Mode.compare("RESIDUALS:") == 0) { if (iss) { iss >> dResult; pgfocushub_.SetResiduals(dResult);} return;}

		if (Mode.compare("INFO:") == 0) { if (iss) { pgfocushub_.SetLastStatus(iss.str().substr(6,string::npos));} return; }

		if (Mode.compare("ERROR:") == 0) { if (iss) { pgfocushub_.SetLastError(iss.str().substr(7, string::npos));} return; }




	} while (iss);


	return;

}

