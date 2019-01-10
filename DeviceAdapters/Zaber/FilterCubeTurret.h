///////////////////////////////////////////////////////////////////////////////
// FILE:          FilterCubeTurret.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device adapter for Zaber's X-FCR series filter cube turrets
//                for microscopes.
//
// AUTHOR:        Soleil Lapierre (contact@zaber.com)

// COPYRIGHT:     Zaber Technologies Inc., 2019

// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifndef _FILTERCUBETURRET_H_
#define _FILTERCUBETURRET_H_

#include "Zaber.h"

extern const char* g_FilterTurretName;
extern const char* g_FilterTurretDescription;


class FilterCubeTurret : public CStateDeviceBase<FilterCubeTurret>, public ZaberBase
{
public:
   FilterCubeTurret();
   ~FilterCubeTurret();

   // Device API
   // ----------
   int Initialize();
   int Shutdown();
   void GetName(char* name) const;
   bool Busy();

   // Stage API
   // ---------
   unsigned long GetNumberOfPositions() const
   {
      return numPositions_;
   }

   // Base class overrides
   // ----------------
   virtual int GetPositionLabel(long pos, char* label) const;

   // Properties
   // ----------------
   int DelayGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);
   int PortGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);
   int DeviceAddressGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);
   int PositionGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   long deviceAddress_;
   long numPositions_;
   MM::MMTime changedTime_;
};

#endif // Include guard
