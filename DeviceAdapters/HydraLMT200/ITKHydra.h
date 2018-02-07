///////////////////////////////////////////////////////////////////////////////
// FILE:          ITKHydra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ITK Hydra Controller Driver
//                XY Stage
//
// AUTHOR:        Steven Fletcher, derived from Corvus adapter written by Johan Henriksson, mahogny@areta.org, derived from Märzhauser adapter
// COPYRIGHT:     Steven Fletcher 2017
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifndef _HYDRA_H_
#define _HYDRA_H_



#include <string>
#include <map>

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
//////////////////////////////////////////////////////////////////////////////



// MMCore name of serial port
std::string port_; // SG: global variable! Weird underscore convention...

int ClearPort(MM::Device& device, MM::Core& core, const char* port);

class Hub : public CGenericBase<Hub>
{
   public:
      Hub();
      ~Hub();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();

      void GetName(char* pszName) const; // SG No std::string? But const is there! 
	  //tried replacing this and all instances of GetName with std::string gives me compiling error : is abstract
      bool Busy(); // SG no const...
	  // added const and where it seemed necesarry in .cpp file but couldn't make it compile

//      int Initialize(MM::Device& device, MM::Core& core);
      int DeInitialize() {initialized_ = false; return DEVICE_OK;};
      bool Initialized() {return initialized_;}; // SG no const... initialized_ not yet defined; I'm not sure all compilers would be ok.

      // action interface
      // ---------------
      int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);

   private:

		int checkError(std::string what); // SG Consistent naming convention is important!
		int checkStatus();

      // Command exchange with MMCore
      std::string command_;
      bool initialized_;
};



class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();


   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
   int SetPositionUm(double x, double y);
   int GetPositionUm(double& x, double& y);
   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
   int SetRelativePositionUm(double x, double y);
   int SetRelativePositionSteps(long x, long y);
   int SetOrigin();
   int SetAdapterOrigin();
   int Home();
   int Stop();
   int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   double GetStepSizeXUm() {return stepSizeUm_;}
   double GetStepSizeYUm() {return stepSizeUm_;}
   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
//   int OnStepSizeX (MM::PropertyBase* pProp, MM::ActionType eAct);
//   int OnStepSizeY (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed    (MM::PropertyBase* pProp, MM::ActionType eAct);
//   int OnSpeedY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccel    (MM::PropertyBase* pProp, MM::ActionType eAct);
//   int OnJoystick(MM::PropertyBase* pProp, MM::ActionType eAct);
//   int OnAccelY    (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetCommand(const std::string& cmd, std::string& response);
   
   bool initialized_;
   bool range_measured_;
   double answerTimeoutMs_;
   double stepSizeUm_;
   double speed_;
   double accel_;
   double originX_;
   double originY_;
};

#endif //_HYDRA_H_
