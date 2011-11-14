///////////////////////////////////////////////////////////////////////////////
// FILE:          Marzhauser.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser Tango Controller Driver
//                XY Stage
//                Z  Stage
//
// AUTHOR:        Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2009
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifndef _TANGO_H_
#define _TANGO_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////

#define MAX_ADCHANNELS 16
#define MAX_DACHANNELS 3


// MMCore name of serial port
std::string port_;

int ClearPort(MM::Device& device, MM::Core& core, const char* port);


class Hub : public CGenericBase<Hub>
{
   public:
      Hub();
      ~Hub();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const;
      bool Busy();

//      int Initialize(MM::Device& device, MM::Core& core);
      int DeInitialize() {initialized_ = false; return DEVICE_OK;};
      bool Initialized() {return initialized_;};

      int ClearPort(void);
      int SendCommand (const char *command) const;
      int QueryCommand(const char *command, std::string &answer) const;


      // action interface
      // ---------------
      int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      // Command exchange with MMCore
      std::string command_;
      bool initialized_;
      double answerTimeoutMs_;

   protected:

};



class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();


   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
   int SetPositionUm(double x, double y);
int SetRelativePositionUm(double dx, double dy);
int SetAdapterOriginUm(double x, double y);
   int GetPositionUm(double& x, double& y);
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
int Move(double vx, double vy);

   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int SetRelativePositionSteps(long x, long y);
   int Home();
   int Stop();
   int SetOrigin();
   int SetAdapterOrigin();
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   double GetStepSizeXUm() {return stepSizeXUm_;}
   double GetStepSizeYUm() {return stepSizeYUm_;}

   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnStepSizeX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashY (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetCommand(const std::string& cmd, std::string& response);

   bool initialized_;
   bool range_measured_;
   double answerTimeoutMs_;
   double stepSizeXUm_;
   double stepSizeYUm_;
   double speedX_;
   double speedY_;
   double accelX_;
   double accelY_;
   double originX_;
   double originY_;
};


class ZStage : public CStageBase<ZStage>
{
public:
   ZStage();
   ~ZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);
   int SetAdapterOriginUm(double d);

   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int SetAdapterOrigin();
   int Stop();
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int GetCommand(const std::string& cmd, std::string& response);

   bool initialized_;
   bool range_measured_;
   double answerTimeoutMs_;
   double stepSizeUm_;
   double speedZ_;
   double accelZ_;
   double originZ_;
};


class AStage : public CStageBase<AStage>
{
public:
   AStage();
   ~AStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);
   int SetAdapterOriginUm(double d);
   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int SetAdapterOrigin();
   int Stop();
   int GetLimits(double& min, double& max);

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int GetCommand(const std::string& cmd, std::string& response);

   bool initialized_;
   bool range_measured_;
   double answerTimeoutMs_;
   double stepSizeUm_;
   double speed_;
   double accel_;
   double origin_;
};


class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

   // Device API
   int Initialize();
   int Shutdown();
   bool Busy();
   void GetName(char* pszName) const;
      
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetCommand(const std::string& cmd, std::string& response);
   int SetShutterPosition(bool state);
   int GetShutterPosition(bool& state);
   std::string name_;
   bool initialized_;
   double answerTimeoutMs_;
};


class Lamp : public CShutterBase<Lamp>
{
public:
   Lamp(const char* name, int id);
   ~Lamp();

   // Device API
   int Initialize();
   int Shutdown();
   bool Busy();
   void GetName(char* pszName) const;

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFire     (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool open_;
   double intensity_;
   double fireT_;
   int usec_;
   int GetCommand(const std::string& cmd, std::string& response);
   int GetLampIntensity(double& intensity);
   int SetLampIntensity(double  intensity);
   const int id_;
   std::string name_;  
   double answerTimeoutMs_;
   Lamp& operator=(Lamp&) {assert(false); return *this;} 
};


class DAC : public CSignalIOBase<DAC>
{
public:
   DAC();
   ~DAC();

   // Device API
   int Initialize();
   int Shutdown();
   bool Busy();
   void GetName(char* pszName) const;

   // SignalIO API
   int SetGateOpen(bool open = true);
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = 0.0; maxVolts = 10.0; return DEVICE_OK;};

   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnState  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDACPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   int  DACPort_;
   bool open_;
   double volts_;
   int GetCommand(const std::string& cmd, std::string& response);
   std::string name_;  
   double answerTimeoutMs_;
};


class ADC : public CSignalIOBase<ADC>
{
public:
   ADC();
   ~ADC();

   // Device API
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy(){return false;};

   // ADC API
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open) {open = true; return DEVICE_OK;};
   int SetSignal(double /*volts*/) {return DEVICE_UNSUPPORTED_COMMAND;};
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = 0.0; maxVolts = 5.0; return DEVICE_OK;};

   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnVolts  (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnADCPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ADCPort_;
   bool initialized_;
   double volts_;
   int GetCommand(const std::string& cmd, std::string& response);
   std::string name_;  
   double answerTimeoutMs_;
};

#endif //_TANGO_H_
