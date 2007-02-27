///////////////////////////////////////////////////////////////////////////////
// FILE:          ZeissMTB.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ZEISS MTB adapter
//
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
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/30/2005
//
// CVS:           $Id$
//

#ifndef _ZEISS_MTB_H_
#define _ZEISS_MTB_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "mtb_data.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NOT_CONNECTED         10002
#define ERR_UNKNOWN_POSITION      10003
#define ERR_TYPE_NOT_DETECTED     10004

class Revolver : public CStateDeviceBase<Revolver>
{
public:
   Revolver(unsigned type);
   ~Revolver();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   // additional (local) API
   // ----------------------
   void SetName(const char* name) {name_ = name;}

private:
   bool initialized_;
   unsigned numPos_;
   const unsigned type_;
   std::string name_;
};


class FocusStage : public CStageBase<FocusStage>
{
public:
   FocusStage();
   ~FocusStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual double GetStepSize() const {return stepSize_um_;}
   virtual int SetPositionSteps(long steps) ;
   virtual int GetPositionSteps(long& steps);
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

   bool Busy();
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();
      
   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   bool initialized_;
   const unsigned type_;
};

class Lamp : public CShutterBase<Lamp>
{
public:
   Lamp(unsigned type);
   ~Lamp();
  
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
   int OnLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPower(MM::PropertyBase* pProp, MM::ActionType eAct);

   // additional (local) API
   // ----------------------
   void SetName(const char* name) {name_ = name;}

private:
   bool initialized_;
   const unsigned type_;
   unsigned id_;
   std::string name_;
   long openTimeUs_;
};

class Stand : public CGenericBase<Stand>
{
public:
   Stand();
   ~Stand();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   int OnLightManager(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
};

#endif //_ZEISS_MTB_H_
