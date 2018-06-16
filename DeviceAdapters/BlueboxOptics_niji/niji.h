///////////////////////////////////////////////////////////////////////////////
// FILE:          niji.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   niji controller adapter
// APPLIES TO:    niji with firmware version >= V2.101.000(Beta)
//                (please contact Bluebox Optics if your firmware is older)
//
// COPYRIGHT:     University of California, San Francisco, 2009
// 
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 2017-08-15
//
// BASED ON:      CoherentCube, UserDefinedSerial and PVCAM
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

#ifndef _NIJI_H_
#define _NIJI_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
//#include <iostream>
#include <vector>
using namespace std;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_DEVICE_NOT_FOUND         10005

#define NLED 7

class PollingThread;

class Controller : public CShutterBase<Controller>
{
public:
   Controller(const char* name);
   ~Controller();
  
   friend class PollingThread;

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   //int OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnChannelLabel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelState(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

   int OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerLogic(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerResistor(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnTemperature1(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature2(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnErrorCode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGlobalStatus(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnOutputMode(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   static MMThreadLock& GetLock() {return lock_;}

protected:
   size_t nChannels_;
   long state_;
   long intensity_;
   bool hasUpdated_;

   vector<string> channelLabels_;
   string currentChannelLabel_;

   //individual channels intensity and state.
   //ledStates_ is a vector of the real LED state as sent back from the niji.
   //channelStates_ holds the desired states when combined (boolean and) with the niji shutter state_
   vector<long> channelIntensities_;
   vector<long> channelStates_;
   vector<long> ledStates_;

   //Are we using external or internal trigger
   long triggerSource_;

   //Is it trigger high or trigger low
   long triggerLogic_;

   //Is the internal resistor a pull-up or pull-down
   long triggerResistor_;

   //the niji status
   long errorCode_;

   //Temperatures (towards the light output and ambient)
   double tempOutput_;
   double tempAmbient_;

   string firmwareVersion_;

   long outputMode_;

   int ReadChannelLabels();

private:

   //We want to keep a record of the individual LED intensities and states
   bool initialized_;
   std::string name_;
   bool busy_;
   int error_;
   MM::MMTime changedTime_;

   std::string port_;
   unsigned char buf_[1000];
   string buf_string_;
   vector<string> buf_tokens_;
   unsigned long buf_bytes_;
   long armState_;

   double answerTimeoutMs_;

   //The number of lines returned by the "?" and "r" commands.
   int n_lines1;
   int n_lines2;

   void GenerateChannelChooser();
   void GenerateIntensityProperties();
   void GenerateStateProperties();
   void GenerateTriggerProperties();
   void GenerateReadOnlyProperties();
   void GenerateOtherProperties();

   void SetTrigger();
   void SetOutputMode();
   void SetActiveChannel(long state);
   void SetActiveChannel(string channelName);
   void GetActiveChannel(long &state);
   void SetState(long state);
   void GetState(long &state);
   void SetChannelState(long state, long index);
   void GetChannelState(long &state, long index);
   void SetGlobalIntensity(long intensity);
   void SetChannelIntensity(long intensity, long index);
   void GetChannelIntensity(long& intensity, long index);
   long UpdateChannelLabel();

   void Illuminate();

   void StripString(string& StringToModify);

   void Send(string cmd);
   void ReceiveOneLine();
   void Purge();
   int HandleErrors();

   void ReadGreeting();
   void ReadUpdate();
   int ReadResponseLines(int n);


   static MMThreadLock lock_;
   PollingThread* mThread_;

   

   Controller& operator=(Controller& /*rhs*/) {assert(false); return *this;}
};


class PollingThread: public MMDeviceThreadBase
{

public:
   PollingThread(Controller& aController);
  ~PollingThread();
   int svc();
   int open (void*) { return 0;}
   int close(unsigned long) {return 0;}

   void Start();
   void Stop() {stop_ = true;}
   void ResetVariables();

   PollingThread & operator=( const PollingThread & ) 
   {
      return *this;
   }


private:
   Controller& aController_;

   bool stop_;
   long state_;

   //These are used for comparison...

   //individual channels intensity and state
   vector<long> channelIntensities_;
   vector<long> channelStates_;

   //Are we using external or internal trigger
   long triggerSource_;

   //Is it trigger high or trigger low
   long triggerLogic_;

   //Is the internal resistor a pull-up or pull-down
   long triggerResistor_;

   //the niji status
   long errorCode_;

   //the current channel label
   string currentChannelLabel_;

   //Temperatures (towards the light output and ambient)
   double tempOutput_;
   double tempAmbient_;

   //power output mode
   long outputMode_;
};



#endif // _NIJI_H_
