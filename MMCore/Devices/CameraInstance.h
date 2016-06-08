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


class CameraInstance : public DeviceInstanceBase<MM::Camera>
{
public:
   CameraInstance(CMMCore* core,
         boost::shared_ptr<LoadedDeviceAdapter> adapter,
         const std::string& name,
         MM::Device* pDevice,
         DeleteDeviceFunction deleteFunction,
         const std::string& label,
         mm::logging::Logger deviceLogger,
         mm::logging::Logger coreLogger) :
      DeviceInstanceBase<MM::Camera>(core, adapter, name, pDevice, deleteFunction, label, deviceLogger, coreLogger)
   {}

   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned char* GetImageBuffer(unsigned channelNr);
   const unsigned int* GetImageBufferAsRGB32();
   unsigned GetNumberOfComponents() const;
   std::string GetComponentName(unsigned component);
   int unsigned GetNumberOfChannels() const;
   std::string GetChannelName(unsigned channel);
   long GetImageBufferSize()const;
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   double GetPixelSizeUm() const;
   int GetBinning() const;
   int SetBinning(int binSize);
   void SetExposure(double exp_ms);
   double GetExposure() const;

   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
   int ClearROI();
   bool SupportsMultiROI();
   bool IsMultiROISet();
   int GetMultiROICount(unsigned& count);
   int SetMultiROI(const unsigned* xs, const unsigned* ys,
           const unsigned* widths, const unsigned* heights,
           unsigned numROIs);
   int GetMultiROI(unsigned* xs, unsigned* ys, unsigned* widths,
           unsigned* heights, unsigned* length);

   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StartSequenceAcquisition(double interval_ms);
   int StopSequenceAcquisition();
   int PrepareSequenceAcqusition();
   bool IsCapturing();
   std::string GetTags();
   void AddTag(const char* key, const char* deviceLabel, const char* value);
   void RemoveTag(const char* key);
   int IsExposureSequenceable(bool& isSequenceable) const;
   int GetExposureSequenceMaxLength(long& nrEvents) const;
   int StartExposureSequence();
   int StopExposureSequence();
   int ClearExposureSequence();
   int AddToExposureSequence(double exposureTime_ms);
   int SendExposureSequence() const;
};
