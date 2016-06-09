///////////////////////////////////////////////////////////////////////////////
// FILE:          pgFocus.h
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

#ifndef _PGFOCUS_H_
#define _PGFOCUS_H_

#ifdef WIN32
#include <windows.h>
#include <winsock.h>
#define snprintf _snprintf
#else
#include <netinet/in.h>
#endif


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <queue>
#include <vector>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//


#define ERR_UNKNOWN_POSITION         	10002
#define ERR_PORT_CHANGE_FORBIDDEN    	10004
#define ERR_SET_POSITION_FAILED      	10005
#define ERR_INVALID_STEP_SIZE        	10006
#define ERR_INVALID_MODE             	10008
#define ERR_UNRECOGNIZED_ANSWER      	10009
#define ERR_UNSPECIFIED_ERROR        	10010
#define ERR_NOT_LOCKED               	10011
#define ERR_NOT_CALIBRATED           	10012
#define ERR_OUT_OF_RANGE	        	10012
#define ERR_ANSWER_TIMEOUT           	10015
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND	10022
#define ERR_VERSION_MISMATCH 			10023
#define ERR_NO_PORT_SET					10024
#define ERR_HUB_NOT_FOUND				10025
#define ERR_DEVICE_NOT_FOUND			10026
#define ERR_SERVICE_THREAD_NOT_FOUND	10027
#define ERR_STILL_CALIBRATING			10028


#define ERR_PORT_NOT_OPEN            	104
#define ERR_OFFSET 10100

#define RETURN_ON_MM_ERROR( result ) if( DEVICE_OK != (ret_ = result) ) return ret_;

#define MAX_DAU   			( 16384 ) // should be zero index...but I like this way better.
#define MIN_DAU    			( 5000 )
#define DAUPERVOLT 			( 1638 ) // 14bit over 10V
#define NMPERMICON			( 1000 )
#define MIDDLE_DAU 			( MAX_DAU/2 )
#define ADC_VOLTAGE( x )	( ( ( float ) x / MIDDLE_DAU ) * 10 )
#define DAC_VOLTAGE( x ) 	( ( ( ( float ) x / MAX_DAU ) * 10 ) -5 )

#define PGFOCUS_MODE_UNLOCK 			0
#define PGFOCUS_MODE_LOCK				1
#define PGFOCUS_MODE_CALIBRATION		2

//////////////////////////////////////////////////////////////////////////////
// Common Values
//

//////////////////////////////////////////////////////////////////////////////
// Device Names Strings
//
const char * const g_DeviceNamepgFocusHub = "pgFocus";
const char * const g_DeviceNamepgFocusStabilization = "pgFocus-Stabilization";
const char * const g_DeviceNamepgFocusAxis = "pgFocus-Axis";

//////////////////////////////////////////////////////////////////////////////
// General Property Identifiers
//

const char * const g_pgFocus_Identity = "BIG-pgFocus";

const char * const g_pgFocus_Mode = "Focus Mode";
	const char * const g_pgFocus_Lock = "Lock Focus";
	const char * const g_pgFocus_Unlock = "Unlock Focus";
	const char * const g_pgFocus_Calibration = "Calibration";

const char * const g_pgFocus_ThisPosition = "Measure Position";
const char * const g_pgFocus_LastPosition = "Last Position";
const char * const g_pgFocus_ApplyPosition = "Apply Position";

const char * const g_pgFocus_LightProfile = "Light Profile";
const char * const g_pgFocus_Light_Min = "Minimum Light Profile Intensity";
const char * const g_pgFocus_Light_Max = "Maximum Light Profile Intensity";
const char * const g_pgFocus_Light_Wait = "Wait ms after Light";

const char * const g_SerialTerminator = "Serial Terminator";
	const char * const g_SerialTerminator_0 = "\n";
	const char * const g_SerialTerminator_0_Text = "\\n";
	const char * const g_SerialTerminator_1 = "\r";
	const char * const g_SerialTerminator_1_Text = "\\r";
	const char * const g_SerialTerminator_2 = "\r\n";
	const char * const g_SerialTerminator_2_Text = "\\r\\n";
	const char * const g_SerialTerminator_3 = "\n\r";
	const char * const g_SerialTerminator_3_Text = "\\n\\r";

const char * const g_pgFocus_CalibrationCurve = "Calibration Curve";
const char * const g_pgFocus_CalibrationCurveRequest = "Please perform a calibration";
const char * const g_pgFocus_CalibrationCurveWait = "Please wait while calibrating...";

const char * const g_pgFocus_Wait_Time_Message = "Wait ms after Message";
const char * const g_pgFocus_Wait_Time_Lock = "Wait ms after Lock";
const char * const g_pgFocus_Standard_Deviation = "Standard Deviation";
const char * const g_pgFocus_Standard_Deviation_nM = "Standard Deviation nM";
const char * const g_pgFocus_Output_nM = "Output nM";
const char * const g_pgFocus_Output_Voltage = "Output Voltage";
const char * const g_pgFocus_Input_nM = "Input nM";
const char * const g_pgFocus_Input_Voltage = "Input Voltage";
const char * const g_pgFocus_Input_Gain = "Input Gain";
const char * const g_pgFocus_Microns_Volt = "Microns Per Volt";
const char * const g_pgFocus_Exposure = "Exposure";
const char * const g_pgFocus_Actual_Exposure = "Actual Exposure";
const char * const g_pgFocus_Auto_Exposure = "Auto Exposure";
const char * const g_pgFocus_Residuals = "Residuals";
const char * const g_pgFocus_Intercept = "Intercept";
const char * const g_pgFocus_Firmware = "Firmware";
const char * const g_pgFocus_Slope = "Slope";
const char * const g_pgFocus_Offset = "Offset";
const char * const g_pgFocus_Last_Error = "Last Error";
const char * const g_pgFocus_Last_Status = "Last Status";

const char * const g_On = "On";
const char * const g_Off = "Off";
const char * const g_Default_String = "NA";
const char * const g_Default_Integer = "0";
const char * const g_Default_Float = "0.00";

const int g_Min_MMVersion = 1;
const int g_Max_MMVersion = 2;
const char* g_versionProp = "Version";


class pgFocusMonitoringThread;

class pgFocusAxis : public CStageBase<pgFocusAxis>
{
public:
	pgFocusAxis();
   ~pgFocusAxis();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // Stage API
   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual double GetStepSize() const {return stepSize_um_;}
   virtual int SetPositionSteps(long steps) ;
   virtual int GetPositionSteps(long& steps);
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return true;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   //int GetUpperLimit();
   //int GetLowerLimit();
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   long moveMode_;
   long velocity_;
   std::string name_;
   std::string description_;

};

class pgFocusStabilization : public CAutoFocusBase<pgFocusStabilization>
{

public:
	pgFocusStabilization();
	~pgFocusStabilization();

    // MMDevice API
    // ------------
    int Initialize();
    int Shutdown();
    void GetName(char* name) const;
    bool Busy();


    // AutoFocus API
	virtual bool IsContinuousFocusLocked();
	virtual int  FullFocus();
	virtual int  IncrementalFocus();
	virtual int  GetLastFocusScore(double& score);
	virtual int  GetCurrentFocusScore(double& score);
	virtual int  GetOffset(double& offset);
	virtual int  SetOffset(double offset);;
	virtual int  AutoSetParameters();
	virtual int  SetContinuousFocusing(bool state);
	virtual int  GetContinuousFocusing(bool& state);

	int  UpdateFocusMode();

	// Action Interface
	// ----------------
	int OnSD(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSDnM(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMin(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMax(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLight(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLastError(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLastStatus(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWaitAfterMessage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWaitAfterLight(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnMicronsPerVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFirmware(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInputnM(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInputGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOutputVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOutputnM(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSlope(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFocusMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResiduals(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnIntercept(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCalibrationCurve(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct);


private:

	bool initialized_;
	bool busy_;
	bool justCalibrated_;

	int ret_;

	long waitAfterLock_;
	long waitAfterLight_;
	long exposure_;
	long DAC_nM_;
	long ADC_nM_;

	std::string focusMode_;

	int GetValue(std::string cmd, float& val);


};

class pgFocusHub :  public HubBase<pgFocusHub>
{
	friend class pgFocusMonitoringThread;

	public:
		pgFocusHub();
		~pgFocusHub();

		int Initialize();
		int Shutdown();
		void GetName(char* pszName) const;
		bool Busy();

		bool SupportsDeviceDetection(void);
		MM::DeviceDetectionStatus DetectDevice(void);
		int DetectInstalledDevices();

		// property handlers
		// ----------------
		int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
		int OnFirmwareVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);
		int OnSerialTerminator(MM::PropertyBase* pProp, MM::ActionType eAct);

		// custom interface for child devices
		bool IsPortAvailable() {return portAvailable_;}


		// Serial Port
		// ----------------
		int ClearPort(void);
		int SendCommand(const char *command);
		int SendCommand(std::string command);
		int SetCommand(std::string cmd);
		std::string GetPort();


		// Commands to get or apply pgFocus parameters
		// ----------------
		double GetOffset();
		long GetMicronPerVolt();
		double GetStandardDeviation();
		long GetWaitTime();
		int SetWaitTime(long time);
		long GetLoopTime();
		long GetRunningTime();
		long GetExposure();

		bool GetAutoExposure();
		int SetAutoExposure(bool autoExposure);

		std::string GetCalibrationCurve();
		int ClearCalibrationCurve();
		int AddCalibration(std::string dau, std::string pixel); // for Standard deviation calculation of streaming offsets returned by pgFocus

		double GetGain();

		// returns Digital to Analog Units, not Volts
		long GetDAC();
		long GetADC();
		double GetSlope();
		double GetResiduals();
		double GetIntercept();
		long GetMaxLight();
		long GetMinLight();
		long GetLight(long index);

		std::string GetLastError();
		std::string GetLastStatus();
		int GetIdentity();
		std::string GetFirmwareVersion();

		std::string GetSerialTerminator();
		int SetSerialTerminator(std::string);

		std::string GetFocusMode();
		int SetFocusMode(std::string focusMode);
		int SetFocusMode(int focusMode);
		int SetContinuousFocusing(bool state);
		int GetContinuousFocusing(bool& state);

		void SetFocusDevice (pgFocusStabilization *device);
		static const int RCV_BUF_LENGTH = 1024;
		unsigned char rcvBuf_[RCV_BUF_LENGTH];

		// update local variables and/or hardware
		int SetExposure(long exposure) {return(SetExposure(exposure,true));};
		int SetDAC(long DAU) {return(SetDAC(DAU,true));};
		int SetOffset(double offset) {return(SetOffset(offset,true));};
		int SetGain(double gain) {return(SetGain(gain,true));};
		int SetMicronPerVolt(long mpv) {return(SetMicronPerVolt(mpv,true));};

	protected: // functions used by monitoring thread to update values, but not trigger sending those values back

		int SetExposure(long exposure, bool hardware);
		int SetDAC(long DAU, bool hardware);
		int SetOffset(double offset, bool hardware);
		int SetGain (double gain, bool hardware);
		int SetMicronPerVolt(int mpv, bool hardware);

		int SetDiffADC(long DAU);
		int SetADC(long DAU);
		int AddOffset(double offset); // for Standard deviation calculation of streaming offsets returned by pgFocus
		int SetLight(long index, long light);
		int SetMinLight(long min);
		int SetMaxLight(long max);
		int SetFirmwareVersion(std::string firmware);
		int SetIntercept(double intercept);
		int SetSlope(double slope);
		int SetLastError(std::string error);
		int SetLastStatus(std::string status);
		int SetResiduals(double residuals);
		int SetStandardDeviation(double SD);
		int SetRunningTime(long time);
		int SetLoopTime(long time);


	private:
		bool initialized_;
		bool portAvailable_;
		bool debug_;
		bool continuousFocusing_;
		bool autoExposure_;

		int ret_;

		// focus parameters
		long mode_;
		long lightArray_[128];
		long loopTime_;
		long min_;
		long max_;
		long DAC_;
		long exposure_;
		long ADC_;
		long diffADC_;
		long runningTime_;
		long micronPerVolt_;
		std::deque<double>::size_type qoffsetMax_; // length of queue used to store offsets for standard deviation calculation

		double gain_;
		double slope_;
		double intercept_;
		double residuals_;
		double offset_;
		double stepSizeUm_;
		double standardDeviation_;
		double standardDeviation_nM_;

		std::string version_;
		std::string status_;
		std::string error_;
		std::string serialTerminator_;
		std::string serialTerminatorText_;
		std::string focusMode_;  		// Requested Focus mode
		std::string focusCurrentMode_;	// Current focus status

		std::vector<std::string> calibration_curve_;
		std::deque<double> qoffsets_;

		std::string name_;				// Name assigned by the user during installation
		std::string port_;				// Serial Port assigned by the user during installation
		std::string serialNumber_;		// Not used yet, but will be serial version of the hardware
		std::string identity_;			// Used to idenditfy the board as a pgFocus board

		MMThreadLock mutex_;

		pgFocusMonitoringThread* monitoringThread_;
		pgFocusStabilization* pgFocusStabilizationDevice;

		int GetControllerVersion(int&);
		double standard_deviation ();
};


class pgFocusMonitoringThread : public MMDeviceThreadBase
{
	public:
		pgFocusMonitoringThread(pgFocusHub &pgfocushub,MM::Core& core, bool debug);
		~pgFocusMonitoringThread();
		int svc();
		int open (void*) { return 0;}
		int close(unsigned long) {return 0;}
		long getWaitTime();
		void setWaitTime(long intervalUs);

		void Start();
		void Stop() {threadStop_ = true;}
		bool isRunning();

   private:
		pgFocusHub& pgfocushub_;
		MM::Core& core_;

		bool debug_;
		bool threadStop_;
		long intervalMs_;

		std::string port_;
		std::string serialTerminator_;

		pgFocusMonitoringThread& operator=(pgFocusMonitoringThread& ) {assert(false); return *this;}

		void parseMessage(std::string message);
		double standard_deviation();
};

float dacVoltage(float  dac) {
	return ( ( ( dac / MAX_DAU ) * 10 ) -5 );
}

float adcVoltage(float  adc) {
	return ( ( adc / MIDDLE_DAU ) * 10 );
}

#endif //_PGFOCUS_H_
