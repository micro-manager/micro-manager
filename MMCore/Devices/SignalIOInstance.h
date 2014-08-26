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


class SignalIOInstance : public DeviceInstanceBase<MM::SignalIO>
{
public:
   SignalIOInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstanceBase<MM::SignalIO>(core, adapter, name, pDevice, deleteFunction, label, deviceLogger, coreLogger)
   {}

   int SetGateOpen(bool open = true);
   int GetGateOpen(bool& open);
   int SetSignal(double volts);
   int GetSignal(double& volts);
   int GetLimits(double& minVolts, double& maxVolts);
   int IsDASequenceable(bool& isSequenceable) const;
   int GetDASequenceMaxLength(long& nrEvents) const;
   int StartDASequence();
   int StopDASequence();
   int ClearDASequence();
   int AddToDASequence(double voltage);
   int SendDASequence();
};
