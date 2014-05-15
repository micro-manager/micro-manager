// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Autofocus device instance wrapper
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

#include "AutoFocusInstance.h"


int AutoFocusInstance::SetContinuousFocusing(bool state) { return GetImpl()->SetContinuousFocusing(state); }
int AutoFocusInstance::GetContinuousFocusing(bool& state) { return GetImpl()->GetContinuousFocusing(state); }
bool AutoFocusInstance::IsContinuousFocusLocked() { return GetImpl()->IsContinuousFocusLocked(); }
int AutoFocusInstance::FullFocus() { return GetImpl()->FullFocus(); }
int AutoFocusInstance::IncrementalFocus() { return GetImpl()->IncrementalFocus(); }
int AutoFocusInstance::GetLastFocusScore(double& score) { return GetImpl()->GetLastFocusScore(score); }
int AutoFocusInstance::GetCurrentFocusScore(double& score) { return GetImpl()->GetCurrentFocusScore(score); }
int AutoFocusInstance::AutoSetParameters() { return GetImpl()->AutoSetParameters(); }
int AutoFocusInstance::GetOffset(double &offset) { return GetImpl()->GetOffset(offset); }
int AutoFocusInstance::SetOffset(double offset) { return GetImpl()->SetOffset(offset); }
