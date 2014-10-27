// Mock device adapter for testing of device sequencing
//
// Copyright (C) 2014 University of California, San Francisco.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida

#pragma once

#include "InterDevice.h"
#include "LoggedSetting.h"

#include <string>


// Common implementation for devices that receive trigger input
class TriggerInput
{
   const std::string settingNamePrefix_;

   InterDevice::Ptr device_;
   LoggedSetting::Ptr sequencedSetting_;

   StringSetting::Ptr triggerSourceDevice_;
   StringSetting::Ptr triggerSourcePort_;
   IntegerSetting::Ptr sequenceMaxLength_;

public:
   TriggerInput(const std::string& settingNamePrefix = "") :
      settingNamePrefix_(settingNamePrefix)
   {}

   // We may in the future need a mechanism to sequence more than one setting
   // in parallel with the same trigger input. For now, just allow one.
   void Initialize(InterDevice::Ptr device,
         LoggedSetting::Ptr sequencedSetting);

   StringSetting::Ptr GetSourceDeviceSetting()
   { return triggerSourceDevice_; }
   StringSetting::Ptr GetSourcePortSetting()
   { return triggerSourcePort_; }
   IntegerSetting::Ptr GetSequenceMaxLengthSetting()
   { return sequenceMaxLength_; }

private:
   void UpdateTriggerConnection();
};
