///////////////////////////////////////////////////////////////////////////////
// FILE:          ZStage.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Oasis Controller / ZStage (Objective Imaging)
//
// AUTHOR:        Egor Zindy, egor.zindy@manchester.ac.uk
//                mostly based on ZStage.h from NiMotionControl by
//                Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     University of Manchester, 2014 (ZStage.h)
//                Leiden University, Leiden, 2009 (ZStage.h)
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
// vim: set autoindent tabstop=3 softtabstop=3 shiftwidth=3 expandtab textwidth=78:

#ifndef _NIZStage_H_
#define _NIZStage_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "oasis4i.h"
#include "oi_const.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103


//////////////////////////////////////////////////////////////////////////////
// OasisZStage class
// Uses flexmotion to automate a focus stage(or any single axis)
//////////////////////////////////////////////////////////////////////////////


class OasisZStage : public CStageBase<OasisZStage>
{
private:
   u8 Axis;
   u8 BoardID;					// Board identification number
   i32 MoveVelocity;
   u32 MoveAcceleration;
   u16 MoveJerk;

   double stepSize_um_;
   double pos_um_;
   bool busy_;
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;

   int SetBoard();
   int MoveBoard(int pos_steps );
   int Stop();
   const char* ZStageDeviceName;
public:
   OasisZStage();
   ~OasisZStage();

   bool Busy();// {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // Stage API
    int SetPositionUm(double pos);// {pos_um_ = pos; return DEVICE_OK;}
    int GetPositionUm(double& pos);// {pos = pos_um_; return DEVICE_OK;}
   virtual double GetStepSize() {return stepSize_um_;}
    int SetPositionSteps(long steps);// {pos_um_ = steps * stepSize_um_; return DEVICE_OK;}
    int GetPositionSteps(long& steps);// {steps = (long)(pos_um_ / stepSize_um_); return DEVICE_OK;}
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
   int OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetJerk(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSetStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsStageSequenceable(bool & seq) const { seq=false; return DEVICE_OK; }
   bool IsContinuousFocusDrive() const { return false; }



};



#endif //_NIZStage_H_
