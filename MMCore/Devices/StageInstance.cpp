// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Stage device instance wrapper
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

#include "StageInstance.h"


int StageInstance::SetPositionUm(double pos) { return GetImpl()->SetPositionUm(pos); }
int StageInstance::SetRelativePositionUm(double d) { return GetImpl()->SetRelativePositionUm(d); }
int StageInstance::Move(double velocity) { return GetImpl()->Move(velocity); }
int StageInstance::Stop() { return GetImpl()->Stop(); }
int StageInstance::Home() { return GetImpl()->Home(); }
int StageInstance::SetAdapterOriginUm(double d) { return GetImpl()->SetAdapterOriginUm(d); }
int StageInstance::GetPositionUm(double& pos) { return GetImpl()->GetPositionUm(pos); }
int StageInstance::SetPositionSteps(long steps) { return GetImpl()->SetPositionSteps(steps); }
int StageInstance::GetPositionSteps(long& steps) { return GetImpl()->GetPositionSteps(steps); }
int StageInstance::SetOrigin() { return GetImpl()->SetOrigin(); }
int StageInstance::GetLimits(double& lower, double& upper) { return GetImpl()->GetLimits(lower, upper); }

MM::FocusDirection
StageInstance::GetFocusDirection()
{
   // Default to what the device adapter says.
   if (!focusDirectionHasBeenSet_)
   {
      MM::FocusDirection direction;
      int err = GetImpl()->GetFocusDirection(direction);
      ThrowIfError(err, "Cannot get focus direction");

      focusDirection_ = direction;
      focusDirectionHasBeenSet_ = true;
   }
   return focusDirection_;
}

void
StageInstance::SetFocusDirection(MM::FocusDirection direction)
{
   focusDirection_ = direction;
   focusDirectionHasBeenSet_ = true;
}

int StageInstance::IsStageSequenceable(bool& isSequenceable) const { return GetImpl()->IsStageSequenceable(isSequenceable); }
int StageInstance::IsStageLinearSequenceable(bool& isSequenceable) const { return GetImpl()->IsStageLinearSequenceable(isSequenceable); }
bool StageInstance::IsContinuousFocusDrive() const { return GetImpl()->IsContinuousFocusDrive(); }
int StageInstance::GetStageSequenceMaxLength(long& nrEvents) const { return GetImpl()->GetStageSequenceMaxLength(nrEvents); }
int StageInstance::StartStageSequence() { return GetImpl()->StartStageSequence(); }
int StageInstance::StopStageSequence() { return GetImpl()->StopStageSequence(); }
int StageInstance::ClearStageSequence() { return GetImpl()->ClearStageSequence(); }
int StageInstance::AddToStageSequence(double position) { return GetImpl()->AddToStageSequence(position); }
int StageInstance::SendStageSequence() { return GetImpl()->SendStageSequence(); }
int StageInstance::SetStageLinearSequence(double dZ_um, long nSlices)
{ return GetImpl()->SetStageLinearSequence(dZ_um, nSlices); }