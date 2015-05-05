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


class XYStageInstance : public DeviceInstanceBase<MM::XYStage>
{
public:
   XYStageInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstanceBase<MM::XYStage>(core, adapter, name, pDevice, deleteFunction, label, deviceLogger, coreLogger)
   {}

   int SetPositionUm(double x, double y);
   int SetRelativePositionUm(double dx, double dy);
   int SetAdapterOriginUm(double x, double y);
   int GetPositionUm(double& x, double& y);
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   int Move(double vx, double vy);
   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int SetRelativePositionSteps(long x, long y);
   int Home();
   int Stop();
   int SetOrigin();
   int SetXOrigin();
   int SetYOrigin();
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   double GetStepSizeXUm();
   double GetStepSizeYUm();
   int IsXYStageSequenceable(bool& isSequenceable) const;
   int GetXYStageSequenceMaxLength(long& nrEvents) const;
   int StartXYStageSequence();
   int StopXYStageSequence();
   int ClearXYStageSequence();
   int AddToXYStageSequence(double positionX, double positionY);
   int SendXYStageSequence();
};
