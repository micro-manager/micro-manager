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
#include "../Utilities/Utilities.h"
#include <string>
#include <map>
#include <algorithm>
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


//////////////////////////////////////////////////////////////////////////////
// CTriggerScope class
// TriggerScope device control
//////////////////////////////////////////////////////////////////////////////

class CTriggerScopeHub : public HubBase<CTriggerScopeHub>  
{
//class CTriggerScope : public CStateBase<CTriggerScope>

public:
	CTriggerScopeHub(void);
	~CTriggerScopeHub(void);

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   bool IsPortAvailable() {return portAvailable_;}

   void TestResourceLocking(const bool recurse);

   int DetectInstalledDevices();

   // action interface
   // ----------------
   int OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   
   int OnArmMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnArrayNum(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClear(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnProgFile(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLoadProg(MM::PropertyBase* pProp, MM::ActionType eAct);
   int LoadProgFile();
   int OnSendSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecvSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct);

   int PurgeComPortH() {return PurgeComPort(port_.c_str());}
   int WriteToComPortH(const unsigned char* command, unsigned len) {return WriteToComPort(port_.c_str(), command, len);}
   int ReadFromComPortH(unsigned char* answer, unsigned maxLen, unsigned long& bytesRead)
   {
      return ReadFromComPort(port_.c_str(), answer, maxLen, bytesRead);
   }

   int GetTriggerTimeDelta(const char *str);
   static MMThreadLock& GetLock() {return lock_;}
   

/////////////////////////////////////
//  Communications
/////////////////////////////////////


	void Send(string cmd);
	void ReceiveOneLine(int nLoopMax=5);
	void Purge();
	int HandleErrors();

	void ReceiveSerialBytes(unsigned char* buf, unsigned long buflen, unsigned long bytesToRead, unsigned long &totalBytes);
	void SendSerialBytes(unsigned char* cmd, unsigned long len);
	void ConvertSerialData(unsigned char *cmd, unsigned char *buf, unsigned long buflen, unsigned long *data, unsigned long datalen );
	void FlushSerialBytes(unsigned char* buf, unsigned long buflen);

	string buf_string_;
private:
   bool busy_;
   MM::TimeoutMs* timeOutTimer_;
   string serial_string_;
   
   string port_;
   string progfileCSV_;
   string sendSerialCmd_;
   string recvSerialCmd_;
   int error_;
   FILE* fidSerialLog_;
   double firmwareVer_;
   int cmdInProgress_;
   int initialized_;

   long stepMode_ ;
   long armMode_;
   long loadProg_;
   long arrayNum_;
   string clearStr_;
 
   MMThreadLock* pResourceLock_;
   static MMThreadLock lock_;

   long nUseSerialLog_;
   bool portAvailable_;

   MM::MMTime zeroTime_, currentTime_;

};


class CTriggerScopeDAC : public CSignalIOBase<CTriggerScopeDAC>   
{
public:
   CTriggerScopeDAC(int channel);
   ~CTriggerScopeDAC(){};
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
   int IsPropertySequenceable(const char* name, bool& isSequenceable) const 
   {
	   if(strcmp(name,"Volts")==0)
		   isSequenceable = true;
	   else
		   isSequenceable = false;

	   return DEVICE_OK;
   }
   int GetPropertySequenceMaxLength(const char* name, long& nrEvents) const
   {
	   if(strcmp(name,"Volts")==0)
		   nrEvents = 50;
	   else
		   nrEvents = 0;

	   return DEVICE_OK;
   }
   int IsDASequenceable(bool& isSequenceable) const
   {
      isSequenceable = true;
      return DEVICE_OK;
   }
   int GetDASequenceMaxLength(long& nrEvents) const
   {
      nrEvents = 50;
      return DEVICE_OK;
   }
   int StartDASequence();
   int StopDASequence();
   int SendDASequence();
   int ClearDASequence();
   int AddToDASequence(double voltage);

    /** 
    * Starts execution of the sequence
    */
    int StartPropertySequence(const char* propertyName) ;
    /**
    * Stops execution of the device
    */
    int StopPropertySequence(const char* propertyName) ;
    /**
    * remove previously added sequence
    */
    int ClearPropertySequence(const char* propertyName) ;
    /**
    * Add one value to the sequence
    */
    int AddToPropertySequence(const char* propertyName, const char* value) ;
    /**
    * Signal that we are done sending sequence values so that the adapter can send the whole sequence to the device
    */
    int SendPropertySequence(const char* propertyName) ;

   //int LoadProperySequence(const char* name, std::vector<double>) ;

   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
  int WriteSignal(double volts);
  int WriteToPort(unsigned long value);

    int nChannel_;
	bool initialized_ ;
	bool busy_;
	bool gateOpen_;
	bool open_;
	double volts_;
	double minV_;
	double maxV_;
	double gatedVolts_;
	std::string name_;
	std::vector<double> sequence_;

};

class CTriggerScopeTTL : public CStateDeviceBase<CTriggerScopeTTL>  
{
public:
   CTriggerScopeTTL(int channel);
   ~CTriggerScopeTTL(){};
   int Initialize();
   int Shutdown(){return DEVICE_OK;};
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   unsigned long GetNumberOfPositions()const {return numPos_;}
   int OnTTL(MM::PropertyBase* pProp, MM::ActionType eAct);
   int WriteToPort(unsigned long value);
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int LoadSequence(unsigned size, unsigned char* seq);

   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int SetOpen(bool bOpen = true);
   //int GetOpen(bool &bOpen);

	int SetGateOpen(bool open);
	int GetGateOpen(bool& open);


private:
	CTriggerScopeHub *pHub_;
	int nChannel_;
	long ttl_;
	bool initialized_;
	bool busy_;
	unsigned long numPos_;
	bool sequenceOn_;
	bool open_;
	long curPos_;

    static const unsigned int NUMPATTERNS = 50;
    unsigned pattern_[NUMPATTERNS];

};

class CTriggerScopeCAM : public CStateDeviceBase<CTriggerScopeCAM>  
{
public:
   CTriggerScopeCAM(int channel);
   ~CTriggerScopeCAM(){};
   int Initialize();
   int Shutdown(){return DEVICE_OK;};
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   unsigned long GetNumberOfPositions()const {return numPos_;}
   int OnCAM(MM::PropertyBase* pProp, MM::ActionType eAct);
   int WriteToPort(unsigned long value);
 
private:
	CTriggerScopeHub *pHub_;
	int nChannel_;
	long cam_;
	bool initialized_;
	bool busy_;
	unsigned long numPos_;
};

class CTriggerScopeFocus : public CStageBase<CTriggerScopeFocus>  
{
public:
   CTriggerScopeFocus();
   ~CTriggerScopeFocus(){};
   int Initialize();
   int Shutdown(){return DEVICE_OK;};
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

  // Stage API
   int SetPositionUm(double pos) ;
   int GetPositionUm(double& pos) {pos = pos_um_; LogMessage("Reporting position", true); return DEVICE_OK;}
   //double GetStepSize() {return stepSize_um_;}
   int SetPositionSteps(long steps) 
   {
      pos_um_ = steps * stepSize_um_; 
      return  OnStagePositionChanged(pos_um_);
   }
   int GetPositionSteps(long& steps)
   {
      steps = (long)(pos_um_ / stepSize_um_);
      return DEVICE_OK;
   }

   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int WriteToPort(unsigned long value);

   //int SetOrigin() { return DEVICE_OK; }
   /*int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }*/
   int Move(double /*v*/) {return DEVICE_OK;}

   bool IsContinuousFocusDrive() const {return false;}

   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDACNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnUpperLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLowerLimit(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions  - from Marzhauser
   int IsStageSequenceable(bool& isSequenceable) const;
   int GetStageSequenceMaxLength(long& nrEvents) const {nrEvents = nrEvents_; return DEVICE_OK;}
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();



	virtual int SetOrigin()
   {
      return DEVICE_OK;
   }
   virtual int GetLimits(double& lower, double& upper)
   {

      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
private:
	CTriggerScopeHub *pHub_;
	bool initialized_;
	bool busy_;
	double pos_um_;
	double stepSize_um_;
	double lowerLimit_;
	double upperLimit_;
	bool sequenceOn_;
	long nDAC_;
	bool sequenceable_;
	long nrEvents_;
	std::vector<double> sequence_;

};


#endif //_TriggerScope_H_
