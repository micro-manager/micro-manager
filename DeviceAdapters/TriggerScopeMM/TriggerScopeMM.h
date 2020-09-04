///////////////////////////////////////////////////////////////////////////////
// FILE:          TriggerScope.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the ARC TriggerScope device adapter.
//				  See http://www.trggerscope.com
//                
// AUTHOR:        Austin Blanco, 5 Oct 2014
//
// COPYRIGHT:     Advanced Research Consulting. (2014)
//
// LICENSE:       
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#pragma once

#ifndef _TriggerScope_H_
#define _TriggerScope_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>
#include <algorithm>
#ifdef win32
#include <cstdint>
#else
#include <stdint.h>
#endif

using namespace std;

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
#define ERR_INVALID_VALUE 110

static const char* g_TriggerScopeDACDeviceName = "TriggerScope_DAC00";
static const char* g_TriggerScopeTTLDeviceName1 = "TriggerScope_TTL1-8";
static const char* g_TriggerScopeTTLDeviceName2 = "TriggerScope_TTL9-16";
static const char* g_TriggerScopeTTLMasterDeviceName = "TriggerScope-TTL-Master";
static const char* g_On = "On";
static const char* g_Off = "Off";
static const char* g_High = "High";
static const char* g_Low = "Low";
static const char* g_Rising = "Rising";
static const char* g_Falling = "Falling";
static const char* g_DACR1 = "0 - 5V";
static const char* g_DACR2 = "0 - 10V";
static const char* g_DACR3 = "-5 - 5V";
static const char* g_DACR4 = "-10 - 10V";
static const char* g_DACR5 = "-2 - 2V";


//////////////////////////////////////////////////////////////////////////////
// CTriggerScope class
// TriggerScope device control
//////////////////////////////////////////////////////////////////////////////

class CTriggerScopeMMHub : public HubBase<CTriggerScopeMMHub>  
{
//class CTriggerScope : public CStateBase<CTriggerScope>

public:
	CTriggerScopeMMHub(void);
	~CTriggerScopeMMHub(void);

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   bool IsInitialized() {return initialized_;}
   
   int DetectInstalledDevices();

   // action interface
   // ----------------
   int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnSendSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecvSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct);

   static MMThreadLock& GetLock() { return lock_; }
   bool GetTS16() { return bTS16_; }
   

/////////////////////////////////////
//  Communications
/////////////////////////////////////


   int SendAndReceive(string cmd);
   int SendAndReceive(string cmd, string &answer);
	int Purge();


private:
   
   int SendAndReceiveNoCheck(string cmd, string &answer);

   bool bTS16_;
   bool busy_;
   string serial_string_;
   
   string port_;
   string sendSerialCmd_;
   string recvSerialCmd_;
   int error_;
   FILE* fidSerialLog_;
   double firmwareVer_;
   bool initialized_;

   long stepMode_ ;
   long armMode_;
   long loadProg_;
   long arrayNum_;
 
   MMThreadLock* pResourceLock_;
   static MMThreadLock lock_;

   long nUseSerialLog_;

   MM::MMTime zeroTime_, currentTime_;

};


class CTriggerScopeMMDAC : public CSignalIOBase<CTriggerScopeMMDAC>   
{
public:
   CTriggerScopeMMDAC(int channel);
   ~CTriggerScopeMMDAC(){};
   int Initialize();
   int Shutdown(){return DEVICE_OK;};
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   // DA API
   int SetGateOpen(bool open);
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts) {volts = volts_; return DEVICE_OK;}     
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = minV_; maxVolts = maxV_; return DEVICE_OK;}

   int OnState     (MM::PropertyBase* pProp, MM::ActionType eAct);
    
   // Sequence functions
   int IsDASequenceable(bool& isSequenceable) const
   {
      isSequenceable = sequenceOn_;  
      return DEVICE_OK;
   }
   int GetDASequenceMaxLength(long& nrEvents) const
   {
      nrEvents = 50;  // TODO: can this number be queried from the controller?
      return DEVICE_OK;
   }
   int StartDASequence();
   int StopDASequence();
   int SendDASequence();
   int ClearDASequence();
   int AddToDASequence(double voltage);

   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   // int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   // int OnMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);   
   int OnSequenceTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
  int WriteSignal(double volts);

   CTriggerScopeMMHub *pHub_;

   bool bTS16_;
   int dacNr_;
	bool initialized_ ;
	bool busy_;
	bool gateOpen_;
	bool open_;
   bool sequenceOn_;
   bool sequenceTransitionOnRising_;
   uint8_t voltrange_;
   std::string voltRangeS_;
	double volts_;
	double minV_;
	double maxV_;
	double gatedVolts_;
	std::string name_;
	std::vector<double> sequence_;

};

class CTriggerScopeMMTTL : public CStateDeviceBase<CTriggerScopeMMTTL>  
{
public:
   CTriggerScopeMMTTL(uint8_t pinGroup);
   CTriggerScopeMMTTL & operator=( const CTriggerScopeMMTTL & ) { return *this; }
   ~CTriggerScopeMMTTL(){ Shutdown(); };
   int Initialize();
   int Shutdown(){ initialized_ = false; return DEVICE_OK; };
  
   void GetName(char* pszName) const;
   bool Busy() { return busy_; }

   unsigned long GetNumberOfPositions()const { return 256; }
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);   
   int OnBlanking(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBlankingTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequenceTriggerDirection(MM::PropertyBase* pProp, MM::ActionType eAct);
 
	int SetGateOpen(bool open);
	int GetGateOpen(bool& open);


private:
   
   int SendBlankingCommand();
   int SendStateCommand(uint8_t value);

   const uint8_t pinGroup_;  // 1: 1-8, 2: 9-16
   
   CTriggerScopeMMHub *pHub_;
   uint16_t numPatterns_;
 	long ttl_;
	bool initialized_;
	bool busy_;
	unsigned long numPos_;
	bool sequenceOn_;
   bool sequenceTransitionOnRising_;
   bool blanking_;
   bool blankOnLow_;
	bool gateOpen_;
   bool isClosed_;
	long curPos_;

};

#endif
