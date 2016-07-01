///////////////////////////////////////////////////////////////////////////////
// FILE:          AgilentLaserCombiner.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Agilent AgilentLaserCombiner
// COPYRIGHT:     100xImaging, Inc, 2010
//
//

#ifndef _AGILENTLASERCOMBINER_H_
#define _AGILENTLASERCOMBINER_H_

#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_LABEL 100
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_CLOSE_FAILED 104
#define ERR_PORT_OPEN_FAILED 106


class LCShutter : public CShutterBase<LCShutter>  
{
public:
   LCShutter();
   ~LCShutter();
  
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
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLineState(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine);
   int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine);
   int OnBlank(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExternalControl(MM::PropertyBase* pProp, MM::ActionType eAct);   
   int OnSync(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnND(MM::PropertyBase* pProp, MM::ActionType eAct, long laserLine);
   int OnOutput(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<double> currentMW_;
   std::map<std::string, unsigned char> laserStateByName_;
   std::map<unsigned char, std::string> laserNameByState_;
   MM::MMTime changedTime_;
   bool initialized_;
   unsigned int nrLines_;
   unsigned char state_;
   bool blank_;
   std::string name_;
   std::string driverVersion_;
   float driverVersionNum_;
   std::string firmwareVersion_;
   bool sequenceOn_;
   bool sequenceIsRunning_;
   long maxSequenceSize_;
};

class LCSafetyShutter : public CShutterBase<LCSafetyShutter>  
{
public:
   LCSafetyShutter();
   ~LCSafetyShutter();
  
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
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   MM::MMTime changedTime_;
   bool initialized_;
   unsigned char state_;
   std::string name_;
};


class LCDA : public CSignalIOBase<LCDA>  
{
public:
   LCDA();
   ~LCDA();
  
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
   int GetSignal(double& volts) {volts_ = (float) volts; return DEVICE_UNSUPPORTED_COMMAND;}     
   int GetLimits(double& minVolts, double& maxVolts) {minVolts = (float) minV_; maxVolts = (float) maxV_; return DEVICE_OK;}
   
   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   bool busy_;
   float minV_;
   float maxV_;
   unsigned int bitDepth_;
   float volts_;
   float gatedVolts_;
   unsigned int encoding_;
   unsigned int resolution_;
   unsigned int channel_;
   std::string name_;
   bool gateOpen_;
};

#endif //_AGILENTLASERCOMBINER_H_
