///////////////////////////////////////////////////////////////////////////////
// FILE:          NiMotionControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The example implementation of the demo camera.
//                Simulates generic digital camera and associated automated
//                microscope devices and enables testing of the rest of the
//                system without the need to connect to the actual hardware. 
//                
// AUTHOR:        Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     Leiden University, Leiden, 2009
//
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


#ifndef _NIMOTION_H_
#define _NIMOTION_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "flexmotn.h"
#include "MotnCnst.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103



//////////////////////////////////////////////////////////////////////////////
// CNIMotionXYStage class
// Uses flexmotion to control an xy stage
//////////////////////////////////////////////////////////////////////////////

class CNIMotionXYStage : public CXYStageBase<CNIMotionXYStage>
{
private:
   u8 XAxis;
   u8 YAxis;
   u8	BoardID;					// Board identification number
   u8	VectorSpace;
   i32 MoveVelocity;
   u32 MoveAcceleration;
   u16 MoveJerk;

   double stepSize_um_;
   double posX_um_;
   double posY_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;

   int SetBoard();
   int MoveBoard(int x,int y );
   void nimcDisplayError(i32 errorCode, u16 commandID, u16 resourceID);
public:
   CNIMotionXYStage();
   ~CNIMotionXYStage();

   bool Busy(); 
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();
     
   // XYStage API
   int SetPositionUm(double x, double y);// {posX_um_ = x; posY_um_ = y; return DEVICE_OK;};
   int SetPositionSteps(long x, long y);
   virtual int GetPositionUm(double& x, double& y);// {x = posX_um_; y = posY_um_; return DEVICE_OK;}
   virtual double GetStepSize() {return stepSize_um_;}
   virtual double GetStepSizeXUm() {return stepSize_um_;}
   virtual double GetStepSizeYUm() {return stepSize_um_;}
   virtual int GetPositionSteps(long& x, long& y)
   {
      x = (long)(posX_um_ / stepSize_um_);
      y = (long)(posY_um_ / stepSize_um_);
      return DEVICE_OK;
   }
   virtual int Home() {return DEVICE_OK;}
   virtual int Stop();// {return DEVICE_OK;}
   virtual int SetOrigin() {return DEVICE_OK;}//jizhen 4/12/2007
   virtual int GetLimits(double& lower, double& upper)
   {
      lower = lowerLimit_;
      upper = upperLimit_;
      return DEVICE_OK;
   }
   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
   {
      xMin = lowerLimit_; xMax = upperLimit_;
      yMin = lowerLimit_; yMax = upperLimit_;
      return DEVICE_OK;
   }

   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
   {
      xMin = (long) (lowerLimit_/stepSize_um_);
      xMax = (long) (upperLimit_/stepSize_um_);
      yMin = (long) (lowerLimit_/stepSize_um_);
      yMax = (long) (upperLimit_/stepSize_um_);
      return DEVICE_OK;
   }

   // action interface
   // ----------------
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnXAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnYAxis(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetJerk(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
//int (MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsXYStageSequenceable(bool & seq) const { seq=false; return DEVICE_OK;}

};



#endif //_DEMOCAMERA_H_
