///////////////////////////////////////////////////////////////////////////////
// FILE:          OasisControl.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Oasis Controller (Objective Imaging)
//
// AUTHOR:        Egor Zindy, egor.zindy@manchester.ac.uk
//                mostly based on NiMotionControl.h by
//                Brian Ashcroft, ashcroft@leidenuniv.nl
//
// COPYRIGHT:     University of Manchester, 2014 (OasisControl.h)
//                Leiden University, Leiden, 2009 (NiMotionControl.h)
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


#ifndef _NIMOTION_H_
#define _NIMOTION_H_

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
// Global flag used for the initialisation of the OASIS subsystem.
// Want to initialise only once for any number of controllers
//
bool oasisInitialized = false;


//////////////////////////////////////////////////////////////////////////////
// OasisXYStage class
// Uses flexmotion to control an xy stage
//////////////////////////////////////////////////////////////////////////////

class OasisXYStage : public CXYStageBase<OasisXYStage>
{
private:
   int BoardID;					// Board identification number

   double stepSize_um_;
   double posX_um_;
   double posY_um_;
   bool busy_;
   bool initialized_;

   double lowerLimitX_um_;
   double upperLimitX_um_;
   double lowerLimitY_um_;
   double upperLimitY_um_;

   std::stringstream tmpMessage;

public:
   OasisXYStage();
   ~OasisXYStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // XYStage API
   int SetPositionUm(double x, double y);// {posX_um_ = x; posY_um_ = y; return DEVICE_OK;};
   int SetPositionSteps(long x, long y);
   int GetPositionUm(double& x, double& y);// {x = posX_um_; y = posY_um_; return DEVICE_OK;}
   int GetPositionSteps(long& x, long& y);
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   int Stop();// {return DEVICE_OK;}
   int Home();
   int SetOrigin();

   //FIXME need real values here...
   virtual double GetStepSize() {return stepSize_um_;}
   virtual double GetStepSizeXUm() {return stepSize_um_;}
   virtual double GetStepSizeYUm() {return stepSize_um_;}

   //virtual int GetLimits(double& lower, double& upper)

   // action interface
   // ----------------
   int OnBoardID(MM::PropertyBase* pProp, MM::ActionType eAct);
   int IsXYStageSequenceable(bool & seq) const { seq=false; return DEVICE_OK;}

   void LogInit();
   void LogIt();
};

#endif //_DEMOCAMERA_H_
