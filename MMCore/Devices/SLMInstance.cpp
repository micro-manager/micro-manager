// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   SLM device instance wrapper
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

#include "SLMInstance.h"


int SLMInstance::SetImage(unsigned char* pixels) { return GetImpl()->SetImage(pixels); }
int SLMInstance::SetImage(unsigned int* pixels) { return GetImpl()->SetImage(pixels); }
int SLMInstance::DisplayImage() { return GetImpl()->DisplayImage(); }
int SLMInstance::SetPixelsTo(unsigned char intensity) { return GetImpl()->SetPixelsTo(intensity); }
int SLMInstance::SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue) { return GetImpl()->SetPixelsTo(red, green, blue); }
int SLMInstance::SetExposure(double interval_ms) { return GetImpl()->SetExposure(interval_ms); }
double SLMInstance::GetExposure() { return GetImpl()->GetExposure(); }
unsigned SLMInstance::GetWidth() { return GetImpl()->GetWidth(); }
unsigned SLMInstance::GetHeight() { return GetImpl()->GetHeight(); }
unsigned SLMInstance::GetNumberOfComponents() { return GetImpl()->GetNumberOfComponents(); }
unsigned SLMInstance::GetBytesPerPixel() { return GetImpl()->GetBytesPerPixel(); }
int SLMInstance::IsSLMSequenceable(bool& isSequenceable)
{ return GetImpl()->IsSLMSequenceable(isSequenceable); }
int SLMInstance::GetSLMSequenceMaxLength(long& nrEvents)
{ return GetImpl()->GetSLMSequenceMaxLength(nrEvents); }
int SLMInstance::StartSLMSequence() { return GetImpl()->StartSLMSequence(); }
int SLMInstance::StopSLMSequence() { return GetImpl()->StopSLMSequence(); }
int SLMInstance::ClearSLMSequence() { return GetImpl()->ClearSLMSequence(); }
int SLMInstance::AddToSLMSequence(const unsigned char * pixels)
{ return GetImpl()->AddToSLMSequence(pixels); }
int SLMInstance::AddToSLMSequence(const unsigned int * pixels)
{ return GetImpl()->AddToSLMSequence(pixels); }
int SLMInstance::SendSLMSequence() { return GetImpl()->SendSLMSequence(); }
