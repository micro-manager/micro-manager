///////////////////////////////////////////////////////////////////////////////
// FILE:          CoherentScientificRemote.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Coherent Scientific Remote controller adapter for up to 6 Coherent Obis lasers
//    
// COPYRIGHT:     35037 Marburg, Germany
//                Max Planck Institute for Terrestrial Microbiology, 2017 
//
// AUTHOR:        Raimo Hartmann
//                Adapted from CoherentScientificRemote driver written by Forrest Collman
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

#ifndef _CoherentScientificRemote_H_
#define _CoherentScientificRemote_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
//#include <iostream>
#include <vector>
using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_DEVICE_NOT_FOUND         10005

#define POWERCONVERSION              1000 //convert the power into mW from the W it wants the commands in

// MM::DeviceDetectionStatus CheckConnection(MM::Device& device, MM::Core& core, std::string port, double ato);

class CoherentScientificRemote : public CShutterBase<CoherentScientificRemote>
{

public:
   // bool SupportsDeviceDetection(void);
   // MM::DeviceDetectionStatus DetectDevice(void);
   // for any queriable token X such as P, SP, E, etc.
   // send the string ?X to the laser and then read a response such as
   // X=xyzzy, just return the result token xyzzy.
   std::string queryLaser( std::string thisToken )
   {
      std::string result;
      std::stringstream msg;
      msg <<  thisToken << "?";

      Purge();
      Send(msg.str());
      ReceiveOneLine();
      string buf_string = currentIOBuffer();
      return buf_string;
   }

   // for any settable token X, such as P, T, etc.
   // send the string X=value, then query ?X and return the string result
   std::string  setLaser( std::string thisToken, std::string thisValue)
   {
      stringstream msg;
      std::string result;

      msg << thisToken << " " << thisValue;
      Purge();
      Send(msg.str());
      ReceiveOneLine();
      string buf_string = currentIOBuffer();

      result = queryLaser(thisToken);
      return result;
   }

   CoherentScientificRemote(const char* name);
   ~CoherentScientificRemote();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   const std::string currentIOBuffer() { return buf_string_; }
   
   // action interface
   // ----------------
   int OnRemoteDescription(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerNum(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnPowerSetpointPercent(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnModulation(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);

   // some important read-only properties
   int OnHeadID(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnLaserPort(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnHeadUsageHours(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnMinimumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnTemperatureDiode(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnTemperatureInternal(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);
   int OnTemperatureBase(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum);

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   void SetLaserNumber(std::string LaserNumber);

private:
   bool initialized_;
   long state_;
   std::string name_;
   int error_;
   MM::MMTime changedTime_;
   std::string modulation_;

   std::string queryToken_;
   std::string descriptionToken_;
   std::string powerSetpointToken_;
   std::string powerReadbackToken_;
   std::string CDRHToken_;  // if this is on, laser delays 5 SEC before turning on
   std::string CWToken_;
   std::string laserOnToken_;
   std::string TECServoToken_;
   std::string headSerialNoToken_;
   std::string headUsageHoursToken_;
   std::string wavelengthToken_;
   std::string externalPowerControlToken_;
   std::string maxPowerToken_;
   std::string minPowerToken_;
   std::string laserHandToken_;
   std::string laserPromToken_;
   std::string laserErrorToken_;
   std::string temperatureDiodeToken_;
   std::string temperatureInternalToken_;
   std::string temperatureBaseToken_;
   std::string modulationReadbackToken_;
   std::string modulationSetpointEXTToken_;
   std::string modulationSetpointINTToken_;
   std::string g_LaserStr_;

   double triggerNum_;

   std::string port_;
   std::string laserNumber_;

   string buf_string_;

   std::string replaceLaserNum(std::string inputToken, long laserNum);

   void SetPowerSetpointPercent(double powerSetpointPercent__, double& achievedSetpointPercent__, long laserNum);
   
   void GetPowerSetpointPercent(double& powerSetpoint__, long laserNum);
   void GetPowerReadback(double& value__, long laserNum);

   void GeneratePowerProperties(long laserNum);
   void GeneratePropertyState(long laserNum);
   void GenerateReadOnlyIDProperties(long laserNum);

   void SetState(long state, long laserNum);
   void GetState(long &state, long laserNum);

   void GetExternalLaserPowerControl(int& control__, long laserNum);
   void SetExternalLaserPowerControl(int control__, long laserNum);

   void Send(string cmd);
   int ReceiveOneLine();
   void Purge();
   int HandleErrors();
   

   CoherentScientificRemote& operator=(CoherentScientificRemote& /*rhs*/) {assert(false); return *this;}
};


#endif // _CoherentScientificRemote_H_
