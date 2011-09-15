///////////////////////////////////////////////////////////////////////////////
// FILE:          K8055.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Velleman K8055 DAQ board
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       LGPLi
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 11/02/2007
//
//

#ifndef _K8055_H_
#define _K8055_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"
#include "K8055Interface.h"
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


class CK8055Hub : public CGenericBase<CK8055Hub>  
{
public:
   CK8055Hub();
   ~CK8055Hub();

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

class CK8055Shutter : public CShutterBase<CK8055Shutter>  
{
public:
   CK8055Shutter();
   ~CK8055Shutter();
  
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

class CK8055Switch : public CStateDeviceBase<CK8055Switch>  
{
public:
   CK8055Switch();
   ~CK8055Switch();
  
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

class CK8055DA : public CSignalIOBase<CK8055DA>  
{
public:
   CK8055DA(unsigned channel, const char* name);
   ~CK8055DA();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}
   
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;}
   int SetSignal(double volts);
   int GetSignal(double& /*volts*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   int GetLimits(double& minVolts, double& maxVolts) {minVolts =minV_; maxVolts = maxV_; return DEVICE_OK;}

   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteSignal(double volts);
   int WriteToPort(long lnValue);

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

class CK8055Input : public CGenericBase<CK8055Input>  
{
public:
   CK8055Input();
   ~CK8055Input();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   int OnDigitalInput(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnAnalogInput1(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnAnalogInput2(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
};
#endif //_K8055_H_
