// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Camera device instance wrapper
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

#include "CameraInstance.h"


int CameraInstance::SnapImage() { return GetImpl()->SnapImage(); }
const unsigned char* CameraInstance::GetImageBuffer() { return GetImpl()->GetImageBuffer(); }
const unsigned char* CameraInstance::GetImageBuffer(unsigned channelNr) { return GetImpl()->GetImageBuffer(channelNr); }
const unsigned int* CameraInstance::GetImageBufferAsRGB32() { return GetImpl()->GetImageBufferAsRGB32(); }
unsigned CameraInstance::GetNumberOfComponents() const { return GetImpl()->GetNumberOfComponents(); }

std::string CameraInstance::GetComponentName(unsigned component)
{
   DeviceStringBuffer nameBuf(this, "GetComponentName");
   int err = GetImpl()->GetComponentName(component, nameBuf.GetBuffer());
   ThrowIfError(err, "Cannot get component name at index " +
         ToString(component));
   return nameBuf.Get();
}

int unsigned CameraInstance::GetNumberOfChannels() const { return GetImpl()->GetNumberOfChannels(); }

std::string CameraInstance::GetChannelName(unsigned channel)
{
   DeviceStringBuffer nameBuf(this, "GetChannelName");
   int err = GetImpl()->GetChannelName(channel, nameBuf.GetBuffer());
   ThrowIfError(err, "Cannot get channel name at index " + ToString(channel));
   return nameBuf.Get();
}

long CameraInstance::GetImageBufferSize()const { return GetImpl()->GetImageBufferSize(); }
unsigned CameraInstance::GetImageWidth() const { return GetImpl()->GetImageWidth(); }
unsigned CameraInstance::GetImageHeight() const { return GetImpl()->GetImageHeight(); }
unsigned CameraInstance::GetImageBytesPerPixel() const { return GetImpl()->GetImageBytesPerPixel(); }
unsigned CameraInstance::GetBitDepth() const { return GetImpl()->GetBitDepth(); }
double CameraInstance::GetPixelSizeUm() const { return GetImpl()->GetPixelSizeUm(); }
int CameraInstance::GetBinning() const { return GetImpl()->GetBinning(); }
int CameraInstance::SetBinning(int binSize) { return GetImpl()->SetBinning(binSize); }
void CameraInstance::SetExposure(double exp_ms) { return GetImpl()->SetExposure(exp_ms); }
double CameraInstance::GetExposure() const { return GetImpl()->GetExposure(); }
int CameraInstance::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize) { return GetImpl()->SetROI(x, y, xSize, ySize); }
int CameraInstance::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize) { return GetImpl()->GetROI(x, y, xSize, ySize); }
int CameraInstance::ClearROI() { return GetImpl()->ClearROI(); }
int CameraInstance::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow) { return GetImpl()->StartSequenceAcquisition(numImages, interval_ms, stopOnOverflow); }
int CameraInstance::StartSequenceAcquisition(double interval_ms) { return GetImpl()->StartSequenceAcquisition(interval_ms); }
int CameraInstance::StopSequenceAcquisition() { return GetImpl()->StopSequenceAcquisition(); }
int CameraInstance::PrepareSequenceAcqusition() { return GetImpl()->PrepareSequenceAcqusition(); }
bool CameraInstance::IsCapturing() { return GetImpl()->IsCapturing(); }

std::string CameraInstance::GetTags()
{
   // TODO Probably makes sense to deserialize here.
   // Also note the danger of limiting serialized metadata to MM::MaxStrLength
   // (CCameraBase takes no precaution to limit string length; it is an
   // interface bug).
   DeviceStringBuffer serializedMetadataBuf(this, "GetTags");
   GetImpl()->GetTags(serializedMetadataBuf.GetBuffer());
   return serializedMetadataBuf.Get();
}

void CameraInstance::AddTag(const char* key, const char* deviceLabel, const char* value) { return GetImpl()->AddTag(key, deviceLabel, value); }
void CameraInstance::RemoveTag(const char* key) { return GetImpl()->RemoveTag(key); }
int CameraInstance::IsExposureSequenceable(bool& isSequenceable) const { return GetImpl()->IsExposureSequenceable(isSequenceable); }
int CameraInstance::GetExposureSequenceMaxLength(long& nrEvents) const { return GetImpl()->GetExposureSequenceMaxLength(nrEvents); }
int CameraInstance::StartExposureSequence() { return GetImpl()->StartExposureSequence(); }
int CameraInstance::StopExposureSequence() { return GetImpl()->StopExposureSequence(); }
int CameraInstance::ClearExposureSequence() { return GetImpl()->ClearExposureSequence(); }
int CameraInstance::AddToExposureSequence(double exposureTime_ms) { return GetImpl()->AddToExposureSequence(exposureTime_ms); }
int CameraInstance::SendExposureSequence() const { return GetImpl()->SendExposureSequence(); }
