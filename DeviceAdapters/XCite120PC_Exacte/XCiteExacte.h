///////////////////////////////////////////////////////////////////////////////
// FILE:         XCiteExacte.h
// PROJECT:      Micro-Manager
// SUBSYSTEM:    DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  This is the Micro-Manager device adapter for the X-Cite Exacte
//            
// AUTHOR:       Mark Allen Neil, markallenneil@yahoo.com
//               This code reuses work done by Jannis Uhlendorf, 2010
//
//				 Modified by Lon Chu (lonchu@yahoo.com) on September 26, 2013
//				 add protection from shutter close-open sequence, shutter will be
//			     dwell an interval after cloased and before opening again
//
// COPYRIGHT:    Mission Bay Imaging, 2010-2011
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifndef _XCiteExacte_H_
#define _XCiteExacte_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#include <string>
#include <map>

using namespace std;

// Error codes
#define ERR_PORT_CHANGE_FORBIDDEN   10004

class XCiteExacte : public CShutterBase<XCiteExacte>
{
public:
   XCiteExacte(const char* name);
   ~XCiteExacte();

   // Device API
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // Action Interfaces
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPanelLock(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLampState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClearAlarm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPcShutterControl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIrisControl(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPowerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClfMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClearCalib(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGetCalibTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOutputPower(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGetPowerFactor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGetLampHours(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusAlarmState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusLampState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusShutterState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusHome(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusLampReady(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusFrontPanel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusPowerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusExacteMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusLightGuideInserted(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusCLFMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUnitStatusIrisMoving(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterDwellTime(MM::PropertyBase* pProp, MM::ActionType eAct);	// delay time between "Close Shutter" and "Open Shutter" sequence

private:
   int GetDeviceStatus(int statusBit,  std::string* retStatus);
   int ExecuteCommand(const std::string& cmd, const char* input=NULL, int input_len=0, std::string* ret=NULL); 

   bool initialized_;
   string deviceName_;
   string serialPort_;
   bool shutterOpen_;
   string frontPanelLocked_;
   long lampIntensity_;
   string lampState_;
   string pcShutterControl_;
   string irisControl_;
   string powerMode_;
   string clfMode_;
   double outputPower_;
   string powerFactor_;
   long shutterDwellTime_;	// delay time between "Close Shutter" and "Open Shutter" sequence
   MM::MMTime timeShutterClosed_;	// time shutter closed
   MM::MMTime lastShutterTime_;

   static const char* cmdConnect;
   static const char* cmdLockFrontPanel;
   static const char* cmdUnlockFrontPanel;
   static const char* cmdClearAlarm;
   static const char* cmdOpenShutter;
   static const char* cmdCloseShutter;
   static const char* cmdTurnLampOn;
   static const char* cmdTurnLampOff;
   static const char* cmdGetSoftwareVersion;
   static const char* cmdGetLampHours;   
   static const char* cmdGetUnitStatus;
   static const char* cmdGetIntensityLevel;
   static const char* cmdSetIntensityLevel;
   static const char* cmdEnableExtendedCommands;
   static const char* cmdEnableShutterControl;
   static const char* cmdDisableShutterControl;
   static const char* cmdGetSerialNumber;
   static const char* cmdIncrementIris;
   static const char* cmdDecrementIris;
   static const char* cmdChangePowerMode;
   static const char* cmdEnableCLF;
   static const char* cmdDisableCLF;
   static const char* cmdClearCalib;
   static const char* cmdGetCalibTime;
   static const char* cmdSetOutputPower;
   static const char* cmdGetOutputPower;
   static const char* cmdGetPowerFactor;

   static const char* retOk;
   static const char* retError;
};

#endif // _XCiteExacte_H_
