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

#include <vector>

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
  virtual int SetRelativePositionUm(double d); 
  virtual double GetStepSize();
  virtual int SetPositionSteps(long steps);
  virtual int GetPositionSteps(long& steps);
  virtual int SetOrigin();
  virtual int GetLimits(double& lower, double& upper);
  virtual int IsStageSequenceable(bool& isSequenceable) const;
  virtual int GetStageSequenceMaxLength(long& nrEvents) const;
  virtual int StartStageSequence();
  virtual int StopStageSequence();
  virtual int ClearStageSequence();
  virtual int AddToStageSequence(double position);
  virtual int SendStageSequence();
  virtual bool IsContinuousFocusDrive() const;

  int getHandle(){ return MCLhandle_;}

  // Action interface
  int OnPositionUm(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSettlingTimeZMs(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSetOrigin(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnCommandChanged(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSetSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSetShiftSequence(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnSetTirfLock(MM::PropertyBase* pProp, MM::ActionType eAct);

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
   double commandedZ_;
   int dacBits_;

   double curZpos_;
   bool firstWrite_;
   
   bool canSupportSeq_;
   bool supportsSeq_;
   int seqMaxSize_;
   bool shiftSequence_;
   std::vector<double> sequence_;

   bool axisUsedForTirfControl_;

   int axis_;
};

#endif // _MCL_NANODRIVE_ZSTAGE_H_