///////////////////////////////////////////////////////////////////////////////
// FILE:          MicroPoint.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Andor MicroPoint Scanner adapter
//
// COPYRIGHT:     University of California, San Francisco
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER(S) OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Arthur Edelstein, 2013
//

#ifndef _MicroPoint_H_
#define _MicroPoint_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

class MicroPoint : public CGalvoBase<MicroPoint>
{
public:
   MicroPoint();
   ~MicroPoint();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Galvo API
   int PointAndFire(double x, double y, double pulseTime_us);
   int SetSpotInterval(double pulseTime_us);
   int SetPosition(double x, double y);
   int GetPosition(double& x, double& y);
   int SetIlluminationState(bool on);
   int AddPolygonVertex(int polygonIndex, double x, double y);
   int DeletePolygons();
   int LoadPolygons();
   int SetPolygonRepetitions(int repetitions);
   int RunPolygons();
   int RunSequence();
   int StopSequence();
   int GetChannel(char* channelName);

   double GetXRange();
   double GetYRange();

   // Property action handlers
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAttenuator(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRepetitions(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string port_;
   double x_;
   double y_;
   long attenuatorPosition_;
   long repetitions_;
   std::string attenuatorText_;

   int WriteBytes(unsigned char* buf, int numBytes);
   int ConfigurePortDirectionRegisters();

   double AttenuatorTransmissionFromIndex(long n);
   int StepAttenuatorPosition(bool positive);
   int MoveAttenuator(long steps);
   int CreateAttenuatorProperty();
   int CreateRepetitionsProperty();
   bool IsAttenuatorHome();
   long FindAttenuatorPosition();
   
   std::vector<std::vector<std::pair<double,double> > > polygons_;
   long polygonRepetitions_;
};

#endif //_MicroPoint_H_
