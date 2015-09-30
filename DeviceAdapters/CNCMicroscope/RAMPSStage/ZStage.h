/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifndef _RAMPS_ZSTAGE_H_
#define _RAMPS_ZSTAGE_H_

#include "MMDevice.h"
#include "DeviceBase.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//

#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_INVALID_MODE             10008
#define ERR_CANNOT_CHANGE_PROPERTY   10009
#define ERR_UNEXPECTED_ANSWER        10010
#define ERR_INVALID_TURRET           10011
#define ERR_SCOPE_NOT_ACTIVE         10012
#define ERR_INVALID_TURRET_POSITION  10013
#define ERR_MODULE_NOT_FOUND         10014
#define ERR_NO_FOCUS_DRIVE           10015
#define ERR_NO_XY_DRIVE              10016

// Axioskope 2 Z stage
//
class RAMPSZStage : public CStageBase<RAMPSZStage>
{
 public:
  RAMPSZStage();
  ~RAMPSZStage();

  bool Busy();
  void GetName(char* pszName) const;

  int Initialize();
  int Shutdown();

  // Stage API
  virtual int SetPositionUm(double pos);
  virtual int GetPositionUm(double& pos);
  virtual double GetStepSize() const;
  virtual int SetPositionSteps(long steps) ;
  virtual int GetPositionSteps(long& steps);
  virtual int SetOrigin();
  virtual int GetLimits(double& lower, double& upper);

  bool IsContinuousFocusDrive() const;

  // action interface
  // ----------------
  int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnLoadSample(MM::PropertyBase* pProp, MM::ActionType eAct);

  // Sequence functions (unimplemented)
  int IsStageSequenceable(bool& isSequenceable) const;
  int GetStageSequenceMaxLength(long& nrEvents) const;
  int StartStageSequence();
  int StopStageSequence();
  int ClearStageSequence();
  int AddToStageSequence(double /*position*/);
  int SendStageSequence();



 private:
  int GetFocusFirmwareVersion();
  int GetUpperLimit();
  int GetLowerLimit();
  double stepSize_um_;
  double posZ_um_;
  bool initialized_;
  double lowerLimit_;
  double upperLimit_;

};

#endif // _RAMPS_ZSTAGE_H_
