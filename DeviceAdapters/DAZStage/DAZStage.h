///////////////////////////////////////////////////////////////////////////////
// FILE:          DAZStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter to drive Z-stages through a DA device
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 03/28/2008
// COPYRIGHT:     University of California, San Francisco, 2008
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
//

#ifndef _DAZSTAGE_H_
#define _DAZSTAGE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_DEVICE_NAME            10001
#define ERR_NO_DA_DEVICE                   10002
#define ERR_VOLT_OUT_OF_RANGE              10003
#define ERR_POS_OUT_OF_RANGE               10004
#define ERR_NO_DA_DEVICE_FOUND             10005

class DAZStage : public CStageBase<DAZStage>
{
public:
   DAZStage();
   ~DAZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName_;
   MM::SignalIO* DADevice_;
   bool initialized_;
   double minDAVolt_;
   double maxDAVolt_;
   double minStageVolt_;
   double maxStageVolt_;
   double minStagePos_;
   double maxStagePos_;
   double pos_;
   double originPos_;
};

#endif //_DAZSTAGE_H_
