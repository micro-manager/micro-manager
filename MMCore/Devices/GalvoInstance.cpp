// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Galvo device instance wrapper
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

#include "GalvoInstance.h"


int GalvoInstance::PointAndFire(double x, double y, double time_us) { return GetImpl()->PointAndFire(x, y, time_us); }
int GalvoInstance::SetSpotInterval(double pulseInterval_us) { return GetImpl()->SetSpotInterval(pulseInterval_us); }
int GalvoInstance::SetPosition(double x, double y) { return GetImpl()->SetPosition(x, y); }
int GalvoInstance::GetPosition(double& x, double& y) { return GetImpl()->GetPosition(x, y); }
int GalvoInstance::SetIlluminationState(bool on) { return GetImpl()->SetIlluminationState(on); }
double GalvoInstance::GetXRange() { return GetImpl()->GetXRange(); }
double GalvoInstance::GetXMinimum() { return GetImpl()->GetXMinimum(); }
double GalvoInstance::GetYRange() { return GetImpl()->GetYRange(); }
double GalvoInstance::GetYMinimum() { return GetImpl()->GetYMinimum(); }
int GalvoInstance::AddPolygonVertex(int polygonIndex, double x, double y) { return GetImpl()->AddPolygonVertex(polygonIndex, x, y); }
int GalvoInstance::DeletePolygons() { return GetImpl()->DeletePolygons(); }
int GalvoInstance::RunSequence() { return GetImpl()->RunSequence(); }
int GalvoInstance::LoadPolygons() { return GetImpl()->LoadPolygons(); }
int GalvoInstance::SetPolygonRepetitions(int repetitions) { return GetImpl()->SetPolygonRepetitions(repetitions); }
int GalvoInstance::RunPolygons() { return GetImpl()->RunPolygons(); }
int GalvoInstance::StopSequence() { return GetImpl()->StopSequence(); }

std::string GalvoInstance::GetChannel()
{
   DeviceStringBuffer nameBuf(this, "GetChannel");
   int err = GetImpl()->GetChannel(nameBuf.GetBuffer());
   ThrowIfError(err, "Cannot get current channel name");
   return nameBuf.Get();
}
