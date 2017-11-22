///////////////////////////////////////////////////////////////////////////////
// FILE:          SmarActHCU-3D.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SmarAct HCU 3D stage, need special firmware 
//
// AUTHOR:        Joran Deschamps, EMBL, 2014 
//				  joran.deschamps@embl.de 
//
// LICENSE:       LGPL
//


#ifndef _SMARACT_H_
#define _SMARACT_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_PORT_CHANGE_FORBIDDEN    10004


class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double x, double y);
  int GetPositionUm(double& x, double& y);

  int SetPositionSteps(long x, long y);
  int SetFrequency(int x);
  int SetRelativePositionUm(double x, double y);
  int GetPositionSteps(long& x, long& y);
  int SetOrigin();
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);

  int Home();
  int Stop();
  int GetStepLimits(long &xMin, long &xMax, long &yMin, long &yMax);
  double GetStepSizeXUm();
  double GetStepSizeYUm();

  int SetErrorStatus(int i);

  int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  bool IsContinuousFocusDrive() const {return false;}

    // action interface
   // ----------------
   int OnLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFrequency(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnHold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   int ExecuteCommand(const std::string& cmd, std::string& response);

   std::string port_;
   bool initialized_;
   double curPos_x_;
   double curPos_y_;
   double answerTimeoutMs_;
   int reverseX_;
   int reverseY_;
   int freqXY_;
   int channelX_;
   int channelY_;
   int holdtime_;
};

class ZStage : public CStageBase<ZStage>
{
public:
   ZStage();
   ~ZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int SetRelativePositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int SetFrequency(int x);
  int GetLimits(double& min, double& max);

  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  bool IsContinuousFocusDrive() const {return false;}

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ExecuteCommand(const std::string& cmd, std::string& response);

   std::string port_;
   bool initialized_;
   int channelZ;
   double answerTimeoutMs_;
   int reverseZ_;
   int freqZ_;
   int channelZ_;
   int holdtime_;
};


#endif //_Smaract_H_