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

#ifndef _RAMPS_XYSTAGE_H_
#define _RAMPS_XYSTAGE_H_

#include "DeviceBase.h"
#include "DeviceThreads.h"

class RAMPSXYStage : public CXYStageBase<RAMPSXYStage>
{
 public:
  RAMPSXYStage();
  ~RAMPSXYStage();

  bool Busy();
  void GetName(char* pszName) const;

  int Initialize();
  int Shutdown();

  // XYStage API
  /* Note that only the Set/Get PositionStep functions are implemented in the adapter
   * It is best not to override the Set/Get PositionUm functions in DeviceBase.h, since
   * those implement corrections based on whether or not X and Y directionality should be
   * mirrored and based on a user defined origin
   */

  // This must be correct or the conversions between steps and Um will go wrong
  virtual double GetStepSize();
  virtual int SetPositionSteps(long x, long y);
  virtual int GetPositionSteps(long& x, long& y);
  virtual int SetRelativePositionSteps(long x, long y);
  virtual int Home();
  virtual int Stop();

  /* This sets the 0,0 position of the adapter to the current position.
   * If possible, the stage controller itself should also be set to 0,0
   * Note that this differs form the function SetAdapterOrigin(), which
   * sets the coordinate system used by the adapter
   * to values different from the system used by the stage controller
   */
  virtual int SetOrigin();
  virtual int SetAdapterOriginUm(double x, double y);

  virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);

  virtual int GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/);

  double GetStepSizeXUm();
  double GetStepSizeYUm();
  int Move(double /*vx*/, double /*vy*/);

  int IsXYStageSequenceable(bool& isSequenceable) const;


  // action interface
  // ----------------
  int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

  int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);

 private:
  double stepSize_um_;
  double posX_um_;
  double posY_um_;
  bool initialized_;
  double lowerLimit_;
  double upperLimit_;
  bool is_moving_;
  std::string status_;
};

#endif // _RAMPS_XYSTAGE_H_
