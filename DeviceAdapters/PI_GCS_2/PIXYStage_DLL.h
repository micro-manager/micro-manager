///////////////////////////////////////////////////////////////////////////////
// FILE:          PIZStage_DLL.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL ZStage
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 28/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PIXYStage_DLL.h,v 1.7, 2011-10-12 11:48:46Z, Steffen Rau$
//

#ifndef _PI_XYSTAGE_DLL_H_
#define _PI_XYSTAGE_DLL_H_

#include "PI_GCS_2.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

class PIController;

class PIXYStage : public CXYStageBase<PIXYStage>
{
public:
   PIXYStage();
   ~PIXYStage();
     
   // XYStage API
   bool Busy();
   void GetName(char* name) const;
   static const char* DeviceName_;

   int Initialize();
   int Shutdown();

   // XY Stage API
   virtual double GetStepSize();
   virtual int SetPositionSteps(long x, long y);
   virtual int GetPositionSteps(long &x, long &y);
   virtual int Home();
   virtual int Stop();
   virtual int SetOrigin();
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   virtual int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   virtual double GetStepSizeXUm();
   virtual double GetStepSizeYUm();

   // action interface
   // ----------------
   int OnControllerName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisXName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisXStageType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisXHoming(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisYName(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisYStageType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxisYHoming(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnXVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnControllerNameYAxis(MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}


private:
   std::string axisXName_;
   std::string axisXStageType_;
   std::string axisXHomingMode_;
   std::string axisYName_;
   std::string axisYStageType_;
   std::string axisYHomingMode_;
   std::string controllerName_;
   std::string controllerNameYAxis_;

   PIController* ctrl_;
   PIController* ctrlYAxis_;

   double stepSize_um_;
   double originX_;
   double originY_;
   //bool busy_;
   bool initialized_;
   //double lowerLimit_;
   //double upperLimit_;
};


#endif //_PI_XYSTAGE_DLL_H_
