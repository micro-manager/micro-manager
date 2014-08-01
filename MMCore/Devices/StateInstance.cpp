// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   State device instance wrapper
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

#include "StateInstance.h"


int StateInstance::SetPosition(long pos) { return GetImpl()->SetPosition(pos); }
int StateInstance::SetPosition(const char* label) { return GetImpl()->SetPosition(label); }
int StateInstance::GetPosition(long& pos) const { return GetImpl()->GetPosition(pos); }

std::string StateInstance::GetPositionLabel() const
{
   DeviceStringBuffer labelBuf(this, "GetPosition");
   int err = GetImpl()->GetPosition(labelBuf.GetBuffer());
   ThrowIfError(err, "Cannot get current position label");
   return labelBuf.Get();
}

std::string StateInstance::GetPositionLabel(long pos) const
{
   DeviceStringBuffer labelBuf(this, "GetPositionLabel");
   int err = GetImpl()->GetPositionLabel(pos, labelBuf.GetBuffer());
   ThrowIfError(err, "Cannot get position label at index " + ToString(pos));
   return labelBuf.Get();
}

int StateInstance::GetLabelPosition(const char* label, long& pos) const { return GetImpl()->GetLabelPosition(label, pos); }
int StateInstance::SetPositionLabel(long pos, const char* label) { return GetImpl()->SetPositionLabel(pos, label); }
unsigned long StateInstance::GetNumberOfPositions() const { return GetImpl()->GetNumberOfPositions(); }
int StateInstance::SetGateOpen(bool open) { return GetImpl()->SetGateOpen(open); }
int StateInstance::GetGateOpen(bool& open) { return GetImpl()->GetGateOpen(open); }
