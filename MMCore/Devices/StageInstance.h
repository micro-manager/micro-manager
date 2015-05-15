// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Mark Tsuchida

#pragma once

#include "DeviceInstanceBase.h"


class StageInstance : public DeviceInstanceBase<MM::Stage>
{
   MM::FocusDirection focusDirection_;
   bool focusDirectionHasBeenSet_;

public:
   StageInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstanceBase<MM::Stage>(core, adapter, name, pDevice, deleteFunction, label, deviceLogger, coreLogger)
   {}

   int SetPositionUm(double pos);
   int SetRelativePositionUm(double d);
   int Move(double velocity);
   int Stop();
   int Home();
   int SetAdapterOriginUm(double d);
   int GetPositionUm(double& pos);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int GetLimits(double& lower, double& upper);
   MM::FocusDirection GetFocusDirection();
   void SetFocusDirection(MM::FocusDirection direction);
   int IsStageSequenceable(bool& isSequenceable) const;
   bool IsContinuousFocusDrive() const;
   int GetStageSequenceMaxLength(long& nrEvents) const;
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();
};
