

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
#define ERR_NO_AUTOFOCUS_DEVICE       	10023


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

#define PGFOCUS_MODE_UNLOCKED 			0
#define PGFOCUS_MODE_LOCKED				1
#define PGFOCUS_MODE_CALIBRATING		2
#define PGFOCUS_MODE_LIGHT_PROFILE		3

//////////////////////////////////////////////////////////////////////////////
// Common Values
//

//////////////////////////////////////////////////////////////////////////////
// Device Names Strings
//
const char * const g_pgFocus = "pgFocus";

//////////////////////////////////////////////////////////////////////////////
// General Property Identifiers
//
const char * const g_pgFocus_Mode = "Focus Mode";
	const char * const g_pgFocus_Lock = "Lock Focus";
	const char * const g_pgFocus_Unlock = "Unlock Focus";
	const char * const g_pgFocus_Light = "Light Profile";
	const char * const g_pgFocus_Calibration = "Calibration";

const char * const g_pgFocus_Current_Mode = "Focus Current Mode";
	const char * const g_pgFocus_Calibrating = "Calibrating";
	const char * const g_pgFocus_Locked = "Locked Focus";
	const char * const g_pgFocus_Unlocked = "Unlocked Focus";

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

const char * const g_pgFocus_On = "On";
const char * const g_pgFocus_Off = "Off";
const char * const g_pgFocus_Default_String = "NA";
const char * const g_pgFocus_Default_Integer = "0";
const char * const g_pgFocus_Last_Error = "Last Error";
const char * const g_pgFocus_Last_Status = "Last Status";


struct pgFocusInfo {

	pgFocusInfo() {
		mode = 0;
		loopTime = 0;
		startTime = 0;
		min = 0;
		max = 1023;
		DAC = 8192;
		exposure = 10000;
		ADC = 0;
		diffADC = 0;
		slope = 0;
		intercept = 0;
		residuals = 0;
		offset = 0;
		micronPerVolt = 10;
		qoffsetMax = 30;
		stepSizeUm = 0;
		continuousFocusing = 0;
		standardDeviation = 0;
		standardDeviation_nM = 0;
		version = g_pgFocus_Default_String;
		error = g_pgFocus_Default_String;
		status = g_pgFocus_Default_String;
		serialTerminator = g_SerialTerminator_2;
		serialTerminatorText = g_SerialTerminator_2_Text;
	}

	~pgFocusInfo() {
	}

	bool continuousFocusing;
	// focus parameters
	long mode;
	long lightArray[128];
	long loopTime;
	long min;
	long max;
	long DAC;
	long exposure;
	long ADC;
	long diffADC;
	long startTime;
	long micronPerVolt;
	unsigned long qoffsetMax; // length of queue used to store offsets for standard deviation calculation

	double gain;
	double slope;
	double intercept;
	double residuals;
	double offset;
	double stepSizeUm;
	double standardDeviation;
	double standardDeviation_nM;

	std::string version;
	std::string status;
	std::string error;
	std::string serialTerminator;
	std::string serialTerminatorText;

	std::vector<std::string> calibration_curve;

};

class pgFocusMonitoringThread;

class pgFocus : public CAutoFocusBase<pgFocus>
{
	friend class pgFocusMonitoringThread;

public:
	pgFocus(const char* name);
	~pgFocus();

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

	std::string GetFocusCurrentMode();
	int  SetFocusMode(std::string focusMode_);
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
	int OnFocusCurrentMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWaitAfterLock(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSerialTerminator(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCalibrationCurve(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Serial Port
	// ----------------
	int ClearPort(void);
	int SendCommand(const char *command);
	int SendCommand(std::string command);
	static const int RCV_BUF_LENGTH = 1024;
	unsigned char rcvBuf_[RCV_BUF_LENGTH];


private:

	bool debug_;
	bool busy_;
	bool autoExposure_;
	bool initialized_;
	bool justCalibrated_;
	bool continuousFocusing_;

	int GetValue(std::string cmd, float& val);
	int SetCommand(std::string cmd);

	long waitAfterLock_;
	long waitAfterLight_;
	long DAC_nM_;
	long ADC_nM_;
	long exposure_;

	std::string name_;
	std::string port_;
	std::string focusMode_;  	// Requested Focus mode
	std::string focusCurrentMode_;	// Current focus status
	std::string version_;
	std::string serialNumber_;


    MM_THREAD_GUARD mutex;
    MMThreadLock executeLock_;
	pgFocusMonitoringThread* monitoringThread_;
	int answerTimeoutMs_;
	int ret_;

	pgFocusInfo deviceInfo_;

};

class pgFocusMonitoringThread : public MMDeviceThreadBase
{
	public:
		pgFocusMonitoringThread(pgFocus &focus,MM::Core& core,  pgFocusInfo *deviceInfo, bool debug);
		~pgFocusMonitoringThread();
		int svc();
		int open (void*) { return 0;}
		int close(unsigned long) {return 0;}
		long getWaitTime();
		void setWaitTime(long intervalUs);

		void Start();
		void Stop() {threadStop_ = true;}

   private:
		//MM_THREAD_HANDLE thread_;
		void parseMessage(char* message);
		double standard_deviation();
		MM::Core& core_;
		pgFocus& pgfocus_;

		pgFocusInfo *deviceInfo_;

		std::string port_;
		std::string serialTerminator_;
		std::deque<double> qoffsets_;

		bool debug_;
		bool threadStop_;
		long intervalMs_;
		pgFocusMonitoringThread& operator=(pgFocusMonitoringThread& ) {assert(false); return *this;}
};

float dacVoltage(float  dac) {
	return ( ( ( dac / MAX_DAU ) * 10 ) -5 );
}

float adcVoltage(float  adc) {
	return ( ( adc / MIDDLE_DAU ) * 10 );
}

#endif //_PGFOCUS_H_
