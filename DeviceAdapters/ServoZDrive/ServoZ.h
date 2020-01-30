//////////////////////////////////////////////////////////////////////////////
// FILE:          ServoZ.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino (Teensy) controlled servo motor z focus drive
// COPYRIGHT:     Regents of the University of California
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016
// AUTHOR:        Zack Phillips, zkphil@berkeley.edu, 3/1/2019
//
//
//////////////////////////////////////////////////////////////////////////////


#ifndef _ILLUMINATE_H_
#define _ILLUMINATE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//////////////////////////////////////////////////////////////////////////////
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109
#define COMMAND_TERMINATOR '\n'
#define SERIAL_DELAY_MS 30

const char* g_Keyword_DeviceName = "ServoZ";



// Low-Level Serial IO
const char * g_Keyword_Response = "SerialResponse";
const char * g_Keyword_Command = "SerialCommand";

const char * g_Keyword_Reset = "Reset";

const char * g_Keyword_Acceleration = "Accleration";
const char * g_Keyword_Speed = "Speed";
const char * g_Keyword_Calibration = "um_per_step";



class ServoZ : public  CStageBase<ServoZ>
{
public:
	ServoZ();
	~ServoZ();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	bool Busy();
	void GetName(char *) const;

	//MM::Stage
	virtual int SetPositionSteps(long steps)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	virtual int GetPositionSteps(long& steps)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	virtual int SetOrigin()
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}
	virtual int GetLimits(double& lower, double& upper)
	{
		return DEVICE_UNSUPPORTED_COMMAND;
	}

	/**
   * Indicates whether a stage can be sequenced (synchronized by TTLs).
   *
   * If true, the following methods must be implemented:
   * GetStageSequenceMaxLength(), StartStageSequence(), StopStageSequence(),
   * ClearStageSequence(), AddToStageSequence(), and SendStageSequence().
   */
	virtual int IsStageSequenceable(bool& isSequenceable) const 
	{
		isSequenceable = false;
		return DEVICE_OK;
	}

	// Check if a stage has continuous focusing capability (positions can be set while continuous focus runs).
	virtual bool IsContinuousFocusDrive() const
	{
		return false;
	}


	//CStageBase
	int GetPositionUm(double& pos);
	int SetPositionUm(double pos);

	




	// action interface
	// ----------------
	int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnReset(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnCommand(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnAcceleration(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnSpeed(MM::PropertyBase* pPropt, MM::ActionType eAct);
	int OnCalibration(MM::PropertyBase* pPropt, MM::ActionType eAct);



private:

	bool initialized_;
	std::string name_;
	std::string port_;
	bool portAvailable_;
	MMThreadLock lock_;
	bool IsPortAvailable() { return portAvailable_; }
	long positionSteps_;
	long acceleration_;
	long speed_;
	double umPerStep_;

	std::string _command;
	std::string _serial_answer;


	// Action functions with LEDs:
	int Clear();
	int Reset();
	int SetMachineMode(bool mode);
	int SendCommand(const char * command, bool get_response);
	int GetResponse();
	int SyncState();
	int SetAccleration();
	int SetSpeed();
	int SetPosition();


	unsigned char lastModVal_;

	MMThreadLock& GetLock() { return lock_; }



};

#endif 
