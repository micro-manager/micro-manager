///////////////////////////////////////////////////////////////////////////////
// FILE:          K8061.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Velleman K8061 DAQ board
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 03/01/2008
//
//

#ifndef _K8061_H_
#define _K8061_H_

#include "../../MMDevice/DeviceBase.h"
#include "K8061Interface.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_LABEL 100
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106


class CK8061Hub : public CGenericBase<CK8061Hub>  
{
public:
   CK8061Hub();
   ~CK8061Hub();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnLogic(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
   std::string port_;
   bool initialized_;
};

class CK8061Shutter : public CShutterBase<CK8061Shutter>  
{
public:
   CK8061Shutter();
   ~CK8061Shutter();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteToPort(long lnValue);
   MM::MMTime changedTime_;
   bool initialized_;
   std::string name_;
};

class CK8061Switch : public CStateDeviceBase<CK8061Switch>  
{
public:
   CK8061Switch();
   ~CK8061Switch();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int OpenPort(const char* pszName, long lnValue);
   int WriteToPort(long lnValue);
   int ClosePort();

   bool initialized_;
   long numPos_;
   bool busy_;
};

class CK8061DA : public CSignalIOBase<CK8061DA>  
{
public:
   CK8061DA();
   ~CK8061DA();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   // DA API
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;};
   int SetSignal(double volts);
   int GetSignal(double& volts) {volts_ = volts; return DEVICE_UNSUPPORTED_COMMAND;}     
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = minV_; maxVolts = maxV_; return DEVICE_OK;}

   // Sequence functions
   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteToPort(long lnValue);
   int WriteSignal(double volts);

   bool initialized_;
   bool busy_;
   double minV_;
   double maxV_;
   double volts_;
   double gatedVolts_;
   unsigned int encoding_;
   unsigned int resolution_;
   unsigned channel_;
   std::string name_;
   bool gateOpen_;
};

class CK8061Input : public CGenericBase<CK8061Input>  
{
public:
   CK8061Input();
   ~CK8061Input();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   int OnDigitalInput(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnAnalogInput(MM::PropertyBase* pProp, MM::ActionType eAct, long channel);

private:
   bool initialized_;
   std::string name_;
};
#endif //_K8061_H_
