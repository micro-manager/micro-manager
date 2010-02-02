 //////////////////////////////////////////////////////////////////////////////
// FILE:          Arduino.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino board
//                Needs accompanying firmware to be installed on the board
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       LGPL
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 11/09/2008
//
//

#ifndef _Arduino_H_
#define _Arduino_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
//#include "ArduinoInterface.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109


class CArduinoHub : public CGenericBase<CArduinoHub>  
{
public:
   CArduinoHub();
   ~CArduinoHub();

   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

   int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnLogic(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnVersion(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
   std::string port_;
   bool initialized_;
};

class CArduinoShutter : public CShutterBase<CArduinoShutter>  
{
public:
   CArduinoShutter();
   ~CArduinoShutter();
  
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

class CArduinoSwitch : public CStateDeviceBase<CArduinoSwitch>  
{
public:
   CArduinoSwitch();
   ~CArduinoSwitch();
  
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
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRepeatTimedPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGetPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPatternsUsed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSkipTriggers(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStartTrigger(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStartTimedOutput(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   static const int NUMPATTERNS = 12;
   unsigned pattern_[NUMPATTERNS];
   unsigned delay_[NUMPATTERNS];
   int nrPatternsUsed_;
   unsigned currentDelay_;
   int OpenPort(const char* pszName, long lnValue);
   int WriteToPort(long lnValue);
   int ClosePort();

   bool blanking_;
   bool initialized_;
   long numPos_;
   bool busy_;
};

class CArduinoDA : public CSignalIOBase<CArduinoDA>  
{
public:
   CArduinoDA();
   ~CArduinoDA();
  
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
   
   // action interface
   // ----------------
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WriteToPort(unsigned long lnValue);
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
   unsigned maxChannel_;
   std::string name_;
   bool gateOpen_;
};

class CArduinoInput : public CGenericBase<CArduinoInput>  
{
public:
   CArduinoInput();
   ~CArduinoInput();

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
#endif //_Arduino_H_
