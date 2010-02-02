///////////////////////////////////////////////////////////////////////////////
// FILE:          PIMotionControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//         
// AUTHOR:        Brian Ashcroft, ashcroft@physics.leidenuniv.nl
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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



#ifndef _PIMOTION_H_
#define _PIMOTION_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include <MMC410.H>
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103


//////////////////////////////////////////////////////////////////////////////
// CNIMotionStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CPIMotionStage : public CStageBase<CPIMotionStage>
{
public:
   CPIMotionStage();
   ~CPIMotionStage();

   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // Stage API
   virtual int SetPositionUm(double pos);// {pos_um_ = pos; return DEVICE_OK;}
   virtual int GetPositionUm(double& pos);// {pos = pos_um_; return DEVICE_OK;}
   virtual double GetStepSize() {return stepSize_um_;}
   virtual int SetPositionSteps(long steps);// {pos_um_ = steps * stepSize_um_; return DEVICE_OK;}
   virtual int GetPositionSteps(long& steps);// {steps = (long)(pos_um_ / stepSize_um_); return DEVICE_OK;}
   virtual int SetOrigin() {return DEVICE_OK;}
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCOM(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetBaud(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
   double stepSize_um_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
   int DevAxis;
   int COMPort;
   int Baud;
};


#endif //_DEMOCAMERA_H_
