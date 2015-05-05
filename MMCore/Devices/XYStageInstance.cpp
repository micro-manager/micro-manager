// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   XY Stage device instance wrapper
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

#include "XYStageInstance.h"


int XYStageInstance::SetPositionUm(double x, double y) { return GetImpl()->SetPositionUm(x, y); }
int XYStageInstance::SetRelativePositionUm(double dx, double dy) { return GetImpl()->SetRelativePositionUm(dx, dy); }
int XYStageInstance::SetAdapterOriginUm(double x, double y) { return GetImpl()->SetAdapterOriginUm(x, y); }
int XYStageInstance::GetPositionUm(double& x, double& y) { return GetImpl()->GetPositionUm(x, y); }
int XYStageInstance::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax) { return GetImpl()->GetLimitsUm(xMin, xMax, yMin, yMax); }
int XYStageInstance::Move(double vx, double vy) { return GetImpl()->Move(vx, vy); }
int XYStageInstance::SetPositionSteps(long x, long y) { return GetImpl()->SetPositionSteps(x, y); }
int XYStageInstance::GetPositionSteps(long& x, long& y) { return GetImpl()->GetPositionSteps(x, y); }
int XYStageInstance::SetRelativePositionSteps(long x, long y) { return GetImpl()->SetRelativePositionSteps(x, y); }
int XYStageInstance::Home() { return GetImpl()->Home(); }
int XYStageInstance::Stop() { return GetImpl()->Stop(); }
int XYStageInstance::SetOrigin() { return GetImpl()->SetOrigin(); }
int XYStageInstance::SetXOrigin() { return GetImpl()->SetXOrigin(); }
int XYStageInstance::SetYOrigin() { return GetImpl()->SetYOrigin(); }
int XYStageInstance::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax) { return GetImpl()->GetStepLimits(xMin, xMax, yMin, yMax); }
double XYStageInstance::GetStepSizeXUm() { return GetImpl()->GetStepSizeXUm(); }
double XYStageInstance::GetStepSizeYUm() { return GetImpl()->GetStepSizeYUm(); }
int XYStageInstance::IsXYStageSequenceable(bool& isSequenceable) const { return GetImpl()->IsXYStageSequenceable(isSequenceable); }
int XYStageInstance::GetXYStageSequenceMaxLength(long& nrEvents) const { return GetImpl()->GetXYStageSequenceMaxLength(nrEvents); }
int XYStageInstance::StartXYStageSequence() { return GetImpl()->StartXYStageSequence(); }
int XYStageInstance::StopXYStageSequence() { return GetImpl()->StopXYStageSequence(); }
int XYStageInstance::ClearXYStageSequence() { return GetImpl()->ClearXYStageSequence(); }
int XYStageInstance::AddToXYStageSequence(double positionX, double positionY) { return GetImpl()->AddToXYStageSequence(positionX, positionY); }
int XYStageInstance::SendXYStageSequence() { return GetImpl()->SendXYStageSequence(); }
