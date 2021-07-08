///////////////////////////////////////////////////////////////////////////////
// FILE:          MPBLaser.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Unofficial device adapter for lasers from MPB Communications Inc.
//                
// AUTHOR:        Kyle M. Douglass, http://kmdouglass.github.io
//
// VERSION:       0.0.0
//                Update the changelog.md after every increment of the version.
//
// FIRMWARE:      VFL_MLDDS_SHGTT_VER_2.5.1.0
//                (Device adapter is known to work with this firmware version.)
//                
// COPYRIGHT:     ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland
//                Laboratory of Experimental Biophysics (LEB), 2017-2018
//

#pragma once
#ifndef _MPBLASER_H_
#define _MPBLASER_H_

#include "DeviceBase.h"
#include <map>

class MPBLaser : public CGenericBase<MPBLaser>
{
	//	Unofficial device adapter for lasers from MPB Communications Inc.

public:
	MPBLaser();
	~MPBLaser();

	// MMDevice API
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;
	bool Busy() { return false; };

	// Settable properties
	int OnLaserMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLDEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnCurrentSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Read-only properties
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLaserState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnKeyLock(MM::PropertyBase* pProp, MM::ActionType eAct);

	// Serial communications
	std::string QueryLaser(std::string msg);
	const std::string GetLastMsg();

private:
	// Enumerations
	enum class LaserMode   { autoCurrentControl, autoPowerControl };
	enum class DeviceOnOff { off, on };
	enum class LaserState  { off, keylock, interlock, fault, startup,
					         manualTurningOn, manualOn, autoOn};

	// Property labels
	// Change these to change the MM property labels
	const std::map<LaserMode, char*> laserModeLabels_ =         { {LaserMode::autoCurrentControl, "Constant Current"},
														          {LaserMode::autoPowerControl,   "Constant Power"} };
	const std::map<DeviceOnOff, char*> laserDiodeOnOffLabels_ = { { DeviceOnOff::off, "Off" },
																  { DeviceOnOff::on,  "On" } };
	const std::map<DeviceOnOff, char*> keyLockLabels_ =         { { DeviceOnOff::off, "Off" },
												                  { DeviceOnOff::on,  "On" } };
	const std::map<LaserState, char*> laserStateLabels_ =       { {LaserState::off,             "Off"},
															      {LaserState::keylock,         "Key Lock"},
															      {LaserState::interlock,       "Interlock"},
															      {LaserState::fault,           "Fault"},
															      {LaserState::startup,         "Startup"},
															      {LaserState::manualTurningOn, "Manual Turning On"},
															      {LaserState::manualOn,        "Manual On"},
															      {LaserState::autoOn,          "Auto On"} };

	// Mapping between integers returned by the laser device and its states.
	const std::map<int, LaserState> laserStateCodes_ = { {0,  LaserState::off },
														 {6,  LaserState::keylock },
													     {7,  LaserState::interlock },
	                                                     {8,  LaserState::fault },
	                                                     {20, LaserState::startup },
	                                                     {31, LaserState::manualTurningOn },
	                                                     {41, LaserState::manualOn },
	                                                     {42, LaserState::autoOn } };

	// Settable device properties
	LaserMode laserMode_;
	DeviceOnOff ldEnable_;
	LaserState laserState_;
	double ldCurrentSetpoint_; 
	double powerSetpoint_;
	float shgTemp_;
	
	void GenerateControlledProperties();
	void GetLaserMode(LaserMode &laserMode);
	void SetLaserMode(LaserMode laserModeIn);
	void GetLDEnable(DeviceOnOff &deviceState);
	void SetLDEnable(DeviceOnOff deviceState);
	void GetPowerSetpoint(double &setpoint);
	void SetPowerSetpoint(double setpoint);

	// Read-only device properties
	std::string port_;
	float tecTemp_;
	float tecCurrent_;
	float ldCaseTemp_;
	float ldCurrent_;
	float opticalOutputPower_;
	float shgTECTemp_;
	float shgTECCurrent_;
	DeviceOnOff keyLock_;

	void GenerateReadOnlyProperties();
	int GetMaxLDCurrent();
	int GetMinLDCurrent();
	std::pair<double, double> GetPowerSetPtLim();
	void GetLaserState(LaserState &laserStateIn);

	// Serial communications
	std::string buffer_;
	const std::string CMD_TERM_ = "\r";
	const std::string ANS_TERM_ = "\r";
	const std::string PROMPT_  = " >";
	const char VALID_CMD_   = 'D';
	const char INVALID_CMD_ = 'F';

	int PurgeBuffer();
	int SendMsg(std::string msg);
	int ReadBuffer();

	// MM API
	bool initialized_;

	// Error codes
	const int ERR_PORT_CHANGE_FORBIDDEN = 101;
	const int ERR_UNRECOGNIZED_KEYLOCK_STATE = 102;
};

#endif // _MPBLaser_H