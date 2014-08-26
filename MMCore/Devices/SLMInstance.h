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


class SLMInstance : public DeviceInstanceBase<MM::SLM>
{
public:
   SLMInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstanceBase<MM::SLM>(core, adapter, name, pDevice, deleteFunction, label, deviceLogger, coreLogger)
   {}

   int SetImage(unsigned char * pixels);
   int SetImage(unsigned int * pixels);
   int DisplayImage();
   int SetPixelsTo(unsigned char intensity);
   int SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue);
   int SetExposure(double interval_ms);
   double GetExposure();
   unsigned GetWidth();
   unsigned GetHeight();
   unsigned GetNumberOfComponents();
   unsigned GetBytesPerPixel();
   int IsSLMSequenceable(bool& isSequenceable);
   int GetSLMSequenceMaxLength(long& nrEvents);
   int StartSLMSequence();
   int StopSLMSequence();
   int ClearSLMSequence();
   int AddToSLMSequence(const unsigned char * pixels);
   int AddToSLMSequence(const unsigned int * pixels);
   int SendSLMSequence();
};
