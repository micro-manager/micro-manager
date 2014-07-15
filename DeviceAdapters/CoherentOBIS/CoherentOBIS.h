///////////////////////////////////////////////////////////////////////////////
// FILE:          CoherentObis.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   CoherentObis controller adapter
// COPYRIGHT:     
//                MBL, Woods Hole, MA 2014
//                University of California, San Francisco, 2009 (Hoover)
//
// AUTHOR:        Forrest Collman
//                Adapted from CoherentCube driver written by Karl Hoover, UCSF
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

#ifndef _CoherentObis_H_
#define _CoherentObis_H_

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

class CoherentObis : public CShutterBase<CoherentObis>
{
private:
   double minlp_;
   double maxlp_;

public:
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

   void initLimits()
   {
      std::string val = queryLaser(maxPowerToken_);
      minlp(atof(val.c_str())*1000);
      val = queryLaser(maxPowerToken_);
      maxlp(atof(val.c_str())*1000);
   }

   // power setting limits:
   double minlp() { return minlp_; }
   void minlp(double v__) { minlp_= v__; }
   double maxlp() { return maxlp_; }
   void maxlp(double v__) { maxlp_= v__; }
   CoherentObis(const char* name);
   ~CoherentObis();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   const std::string currentIOBuffer() { return buf_string_; }
   
   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   // some important read-only properties
   int OnHeadID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHeadUsageHours(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinimumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct/*, long*/);

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);


private:
   bool initialized_;
   long state_;
   std::string name_;
   int error_;
   MM::MMTime changedTime_;

   // todo move these to DevImpl for better data hiding
   const std::string queryToken_;
   const std::string powerSetpointToken_;
   const std::string powerReadbackToken_;
   const std::string CDRHToken_;  // if this is on, laser delays 5 SEC before turning on
   const std::string CWToken_;
   const std::string laserOnToken_;
   const std::string TECServoToken_;
   const std::string headSerialNoToken_;
   const std::string headUsageHoursToken_;
   const std::string wavelengthToken_;
   const std::string externalPowerControlToken_;
   const std::string maxPowerToken_;
   const std::string minPowerToken_;

   std::string port_;

   string buf_string_;


   void SetPowerSetpoint(double powerSetpoint__, double& achievedSetpoint__);
   void GetPowerSetpoint(double& powerSetpoint__);
   void GetPowerReadback(double& value__);

   void GeneratePowerProperties();
   void GeneratePropertyState();

   void GenerateReadOnlyIDProperties();

   void SetState(long state);
   void GetState(long &state);

   void GetExternalLaserPowerControl(int& control__);
   void SetExternalLaserPowerControl(int control__);

   // todo -- can move these to the implementation
   void Send(string cmd);
   int ReceiveOneLine();
   void Purge();
   int HandleErrors();
   

   CoherentObis& operator=(CoherentObis& /*rhs*/) {assert(false); return *this;}
};


#endif // _CoherentObis_H_
