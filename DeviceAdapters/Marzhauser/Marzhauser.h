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

//////////////////////////////////////////////////////////////////////////////

#define MAX_ADCHANNELS 16
#define MAX_DACHANNELS 3

#define ERR_PORT_CHANGE_FORBIDDEN    10004


// N.B. Concrete device classes deriving TangoBase must set core_ in
// Initialize().
class TangoBase
{
public:
   TangoBase(MM::Device *device);
   virtual ~TangoBase();

   int ClearPort(void);
   int CheckDeviceStatus(void);
   int SendCommand(const char *command) const;
   int QueryCommand(const char *command, std::string &answer) const;

protected:
   bool initialized_;
   int  Configuration_;
   std::string port_;
   MM::Device *device_;
   MM::Core *core_;
};



class XYStage : public CXYStageBase<XYStage>, public TangoBase
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
   int OnPort      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashY (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool range_measured_;
   double stepSizeXUm_;
   double stepSizeYUm_;
   double speedX_;
   double speedY_;
   double accelX_;
   double accelY_;
   double originX_;
   double originY_;
};


class ZStage : public CStageBase<ZStage>, public TangoBase
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

   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = sequenceable_; return DEVICE_OK;}
   int GetStageSequenceMaxLength(long& nrEvents) const {nrEvents = nrEvents_; return DEVICE_OK;}
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();

   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequence (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool range_measured_;
   double stepSizeUm_;
   double speedZ_;
   double accelZ_;
   double originZ_;

   bool sequenceable_;
   long nrEvents_;
   std::vector<double> sequence_;
};


class AStage : public CStageBase<AStage>, public TangoBase
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
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklash (MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool range_measured_;
   double speed_;
   double accel_;
   double origin_;
   double stepSizeUm_;
};


class Shutter : public CShutterBase<Shutter>, public TangoBase
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
   int SetOpen(bool open);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnPort     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState    (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetShutterPosition(bool state);
   int GetShutterPosition(bool& state);
   std::string name_;
};


class LED100 : public CShutterBase<LED100>, public TangoBase
{
public:
   LED100(const char* name, int id);
   ~LED100();

   // Device API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnPort      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFire      (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetIntensity(double& intensity);
   int SetIntensity(double  intensity);
   std::string name_;  
   const int id_;
   double intensity_;
   double fireT_;
   LED100& operator=(LED100&) {assert(false); return *this;} 
};


class DAC : public CSignalIOBase<DAC>, public TangoBase
{
public:
   DAC();
   ~DAC();

   // Device API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   // SignalIO API
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = 0.0; maxVolts = 10.0; return DEVICE_OK;};

   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnPort      (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState     (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDACPort   (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int  DACPort_;
   std::string name_;  
   bool open_;
   double volts_;
};


class ADC : public CSignalIOBase<ADC>, public TangoBase
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
   int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVolts   (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnADCPort (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::string name_;  
   double volts_;
   int ADCPort_;
};


#endif //_TANGO_H_
