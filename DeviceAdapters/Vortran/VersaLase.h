/*&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// FILE:          VersaLase.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Vortran VersaLase
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 05/2016
//                Complete re-write of the original VersaLase.cpp by David Sweeney
//                    with heavy influence from Jon's ASITiger device adapter
//                Summary of major changes from original:
//                   - more judicious in refreshing values from hardware than original
//                       -- still re-query often these read-only properties: interlock status and base plate temp,
//                               actual laser power for any laser that is on but not in digital mode
//                   - complete re-write of code structure, e.g. common methods for all 4 lasers,
//                         factored out repetative serial code, etc.
//                   - turn off laser emission during init per MM convention (also turn off when unloading, this was already done)
//                   - warn user of invalid operations (e.g. changing laser power when emission
//                         is off, changing normal power when digital modulation enabled, etc)
//                   - add DetectDevice() to allow automated detection of port settings
//                   - added property for user to type own commands (hidden until "SerialControlEnable" property is enabled)
//                   - removed redundant "LASER_*_DigitalPeakPower" read-only property (have separate read/write property already)
//                   - expose delay setting as property "LASER_*_LaserEmissionDelay"
//                   - added read-only max power property "LASER_*_PowerMaximum"; use this to bound power-setting properties
//                   - added property "RefreshAllProperties" which will force-refresh everything from hardware
//                   - removed lots of explicit delays and instead increase serial timeout when hardware may take a long time to respond
//                   - should work equally well when echo and propmt are enabled or disabled (original assumed echo was on)
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
*/

#ifndef _VERSALASE_H_
#define _VERSALASE_H_
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"

#include <string>
#include <map>
#include <vector>

using namespace std;

// make it easy to return from function on non-DEVICE_OK response from subfunction
// Use the name 'return_value_aoeu' that is unlikely to appear within 'result'.
#define RETURN_ON_MM_ERROR( result ) do { \
   int return_value_aoeu = (result); \
   if (return_value_aoeu != DEVICE_OK) { \
      return return_value_aoeu; \
   } \
} while (0)


// string constants
const char* const g_NAMES = "ABCD";
const char* const g_ON = "ON";
const char* const g_OFF = "OFF";
const char* const g_IDLE = "IDLE";
const char* const g_GO = "GO";
const char* const g_Interlock_Open = "INTERLOCK OPEN!";
const char* const g_Interlock_OK = "OK";
const char* const g_DeviceName = "VLT_VersaLase";
const char* const g_DeviceDescription = "VLT_VersaLase";
const char* const g_KeywordDescription = "VORTRAN Stradus VersaLase";
const char* const g_SerialEnable_PN = "SerialControlEnable";
const char* const g_SerialCommand_PN = "SerialCommand";
const char* const g_SerialResponse_PN = "SerialResponse";
const char* const g_BaseTemp_PN = "BaseplateTemp";
const char* const g_FirmwareVersion_PN = "FirmwareVersion";
const char* const g_Interlock_PN = "Interlock";
const char* const g_RefreshAll_PN = "RefreshAllProperties";
const char* const g_LaserID_PN = "LASER_*_LaserID";
const char* const g_Hours_PN = "LASER_*_Hours";
const char* const g_Shutter_PN = "LASER_*_Shutter";
const char* const g_Emission_PN = "LASER_*_LaserEmission";
const char* const g_EmissionDelay_PN = "LASER_*_LaserEmissionDelay";
const char* const g_FaultCode_PN = "LASER_*_FaultCode";
const char* const g_FaultDesc_PN = "LASER_*_OperatingCondition";
const char* const g_LaserPower_PN = "LASER_*_PowerSetting";
const char* const g_LaserPowerActual_PN = "LASER_*_Power";
const char* const g_PowerMax_PN = "LASER_*_PowerMaximum";
const char* const g_DigitalMod_PN = "LASER_*_DigitalModulation";
const char* const g_DigPeakPower_PN = "LASER_*_DigitalPeakPowerSetting";
const char* const g_LaserCurrent_PN = "LASER_*_Current";
const char* const g_AnalogMod_PN = "LASER_*_AnalogModulation";


// error code constants
#define ERR_PORT_CHANGE         102
#define ERR_MISSING_DELIMITER   103
#define ERR_TOO_MANY_DELIMITERS 104
#define ERR_NON_ZERO_FAULT      105
#define ERR_CANNOT_CHANGE       106

#define MAX_LASERS 4



class VersaLase: public CShutterBase<VersaLase>
{
public:
   VersaLase();
   ~VersaLase();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();
   MM::DeviceDetectionStatus DetectDevice();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------

   // laser-specific properties
   int OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnEmission(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnEmissionDelay(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnFaultCode(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnFaultDescription(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnLaserPowerActual(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnLaserCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnDigitalPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);

   // global properties
   int OnBaseT(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnForceRefresh(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSerialResponse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   string port_;
   long portTimeoutMs_;  // port timeout in milliseconds (sometimes overriden temporarily)
   bool initialized_;
   bool open_;    // store whether shutter has been set to open
   bool interlockOK_;   // true if interlock is OK, meaning can fire
   bool refreshProps_;  // set to true to force re-reading hardware values
   string serialCommand_;
   string serialResponse_;
   string manualSerialCommand_;
   string manualSerialResponse_;

   // variables to store current state
   // most of these are also present as properties, but to reference
   //   them elsewhere in code it is convenient to store them in this form
   bool laserPresent_[MAX_LASERS];
   bool shutter_[MAX_LASERS];  // true for emission to be controlled by MM shutter
   bool emissionOn_[MAX_LASERS];
   bool emissionDelay_[MAX_LASERS]; // true if emission delay is enabled
   long faultCode_[MAX_LASERS];
   double powerMax_[MAX_LASERS];
   bool digitalMod_[MAX_LASERS];
   bool analogMod_[MAX_LASERS];
   double laserPower_[MAX_LASERS];
   double digitalPower_[MAX_LASERS];

   char propName_[MM::MaxStrLength];  // set via GetPropertyNameWithLetter()

   // action handlers use these
   int SerialQuery(string cmd);
   int SerialQuery(size_t laser, string cmd);
   int SerialSet(size_t laser, string cmd, double val, long maxTimeMs=0);  // calls with int vals will cast to this, implementation is fine for either one

   // low-level communication
   int SerialTransaction(string cmd, long maxTimeMs=0);
   void SetSerialTimeout(long timeout) const;
   void RestoreSerialTimeout() const;

   // parsing utilities
   vector<string> SplitAnswerOnDelim(string delims) const;
   int GetStringAfterEquals(string &str) const;
   int GetFloatAfterEquals(double &val) const;
   int GetIntAfterEquals(long &val) const;
   int GetBoolAfterEquals(bool &val) const;

   // property utilities
   bool Int2Bool(long val) const;
   int GetPropertyNameWithLetter(const char* baseName, size_t num);
   int ForcePropertyUpdate(const char* name);
   int ForcePropertyUpdate(string name) { return ForcePropertyUpdate(name.c_str()); };
   int ForcePropertyUpdate(const char* name, size_t num);
};
