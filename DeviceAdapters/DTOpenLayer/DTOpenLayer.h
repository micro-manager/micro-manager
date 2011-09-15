///////////////////////////////////////////////////////////////////////////////
// FILE:          DTOpenLayer.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Data Translation data I/O board adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 03/15/2006
//
// CVS:           $Id$
//

#ifndef _DTOPENLAYER_H_
#define _DTOPENLAYER_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
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
#define DEVICE_RANGE_EXCEEDED 106

class CDTOLShutter : public CShutterBase<CDTOLShutter>  
{
public:
   CDTOLShutter();
   ~CDTOLShutter();
  
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
   long openTimeUs_;
   std::string name_;

   bool initialized_;
};

class CDTOLSwitch : public CStateDeviceBase<CDTOLSwitch>  
{
public:
   CDTOLSwitch();
   ~CDTOLSwitch();
  
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
   bool busy_;
   long numPos_;
};

class CDTOLDA : public CSignalIOBase<CDTOLDA>  
{
public:
   CDTOLDA(unsigned channel, const char* name);
   ~CDTOLDA();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   int SetGateOpen(bool open) {gateOpen_ = open; return DEVICE_OK;}
   int GetGateOpen(bool& open) {open = gateOpen_; return DEVICE_OK;}
   int SetSignal(double volts);
   int GetSignal(double& /*volts*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   int GetLimits(double& minVolts, double& maxVolts);

   int IsDASequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct);



private:
   int WriteToPort(long lnValue);
   int SetVolts(double v);

   bool initialized_;
   bool busy_;
   double volts_;
   double gatedVolts_;
   unsigned int encoding_;
   unsigned int resolution_;
   unsigned channel_;
   std::string name_;
   bool gateOpen_;
   double minV_;
   double maxV_;
};

#endif //_DTOPENLAYER_H_
