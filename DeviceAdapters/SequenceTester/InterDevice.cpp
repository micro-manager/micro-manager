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

#include "InterDevice.h"
#include "SequenceTester.h"


SettingLogger*
InterDevice::GetLogger()
{
   return GetHub()->GetLogger();
}


EdgeTriggerSignal*
InterDevice::GetEdgeTriggerSource(const std::string& port)
{
   boost::unordered_map<std::string, EdgeTriggerSignal*>::const_iterator
      found = edgeTriggersSources_.find(port);
   if (found == edgeTriggersSources_.end())
      return 0;
   return found->second;
}


void
InterDevice::RegisterEdgeTriggerSource(const std::string& port,
      EdgeTriggerSignal& signal)
{
   edgeTriggersSources_[port] = &signal;
}
