///////////////////////////////////////////////////////////////////////////////
// FILE:          BDPathway.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BDPathway adapter.
//
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 01/01/2020
//
//                Based on the Nikon TE2000 adapter by
//                Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco
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
// CVS:           $Id$
//

#ifndef _BDPATHWAY_H_
#define _BDPATHWAY_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NOT_CONNECTED         10002
#define ERR_UNKNOWN_POSITION      10003
#define ERR_TYPE_NOT_DETECTED     10004
#define ERR_EMPTY_ANSWER_RECEIVED 10005
#define ERR_PFS_NOT_CONNECTED     10006
#define ERR_PFS_FOCUS_FAILED      10007

class Hub : public HubBase<Hub>
{
public:
   Hub();
   ~Hub();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   int DetectInstalledDevices();

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int InstallIfMounted(std::string deviceName, const char* deviceCode);
   bool initialized_;
   std::string name_;
   std::string port_;

};

class StateDevice : public CStateDeviceBase<StateDevice>
{
public:
	StateDevice(char deviceId, unsigned int numPos, const char *name, const char **posNames);
	~StateDevice();

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

private:
   bool initialized_;
   char deviceId_;
   unsigned int numPos_;
   const char **posNames_;
   int pos_;
   std::string name_;
};

class BDShutter : public CShutterBase<BDShutter>
{
public:
	BDShutter(char deviceId, const char *name);
	~BDShutter();

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
   bool initialized_;
   char deviceId_;
   std::string name_;
   MM::MMTime changedTime_;
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
   virtual int SetPositionSteps(long steps) ;
   virtual int GetPositionSteps(long& steps);
   virtual double GetStepSize() const {return (double)zStepSize_nm_/1000;}
   virtual int SetOrigin();
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long zStepSize_nm_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
  int GetPositionSteps(long& x, long& y);
  int GetPositionUm(double& x, double& y);
  int SetPositionSteps(long x, long y);
  int SetPositionUm(double x, double y);
  int SetRelativePositionSteps(long x, long y);
  int SetRelativePositionUm(double x, double y);
  int SetAdapterOriginUm(double x, double y);
  int SetOrigin();
  int Home();
  int Stop();
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  double GetStepSizeXUm() {return xStepSize_nm_ / 1000.;}
  double GetStepSizeYUm() {return yStepSize_nm_ / 1000.;}
  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

private:
   long xStepSize_nm_;
   long yStepSize_nm_;
   bool initialized_;

   //These are the origin coordinates in steps (maybe breaking the convention here...)
   long originX_;
   long originY_;
   long limitXmin_;
   long limitYmin_;
   long limitXmax_;
   long limitYmax_;
};

#endif //_BDPATHWAY_H_
