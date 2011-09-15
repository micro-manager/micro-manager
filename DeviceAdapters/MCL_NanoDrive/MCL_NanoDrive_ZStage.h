/*
File:		MCL_NanoDrive_ZStage.h
Copyright:	Mad City Labs Inc., 2008
License:	Distributed under the BSD license.
*/
#ifndef _MCL_NANODRIVE_ZSTAGE_H_
#define _MCL_NANODRIVE_ZSTAGE_H_

// MCL headers
#include "Madlib.h"
#include "MCL_NanoDrive.h"

// MM headers
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

// List/heap headers
#include "device_list.h"
#include "handle_list_if.h"
#include "HandleListType.h"
#include "heap.h"

class MCL_NanoDrive_ZStage : public CStageBase<MCL_NanoDrive_ZStage>
{
public:
  MCL_NanoDrive_ZStage();
  ~MCL_NanoDrive_ZStage();

  bool Busy();
  void GetName(char* pszName) const;

  int Initialize();
  int Shutdown();
     
  // Stage API
  virtual int SetPositionUm(double pos);
  virtual int GetPositionUm(double& pos);
  virtual double GetStepSize();
  virtual int SetPositionSteps(long steps);
  virtual int GetPositionSteps(long& steps);
  virtual int SetOrigin();
  virtual int GetLimits(double& lower, double& upper);
  int getHandle(){ return MCLhandle_;}

  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  bool IsContinuousFocusDrive() const {return false;}

  // Action interface
  int OnPositionUm(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSettlingTimeZMs(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int CreateZStageProperties();

   double stepSize_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   int MCLhandle_;
   double calibration_;
   int serialNumber_;
   int settlingTimeZ_ms_;

   double curZpos_;

   bool firstWrite_;

   int axis_;
};

#endif // _MCL_NANODRIVE_ZSTAGE_H_