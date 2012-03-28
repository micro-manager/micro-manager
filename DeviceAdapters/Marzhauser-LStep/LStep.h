///////////////////////////////////////////////////////////////////////////////
// FILE:          LStep.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser L-Step Controller Driver
//                XY Stage
//                
//
// AUTHORS:        Original Marzhauser Tango adapter code by Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
//					Modifications for Marzhauser L-Step controller by Gilles Courtand, gilles.courtand@u-bordeaux.fr, 
//					and Brice Bonheur brice.bonheur@u-bordeaux.fr 08/03/2012
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2009
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

#ifndef _LSTEP_H_
#define _LSTEP_H_


#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////



// MMCore name of serial port
std::string port_;

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

      void GetName(char* pszName) const;
      bool Busy();

//      int Initialize(MM::Device& device, MM::Core& core);
      int DeInitialize() {initialized_ = false; return DEVICE_OK;};
      bool Initialized() {return initialized_;};

      int ClearPort(void);
      int SendCommand (const char *command) const;
      int QueryCommand(const char *command, std::string &answer) const;


      // action interface
      // ---------------
      int OnPort    (MM::PropertyBase* pProp, MM::ActionType eAct);

   private:
      // Command exchange with MMCore
      std::string command_;
      bool initialized_;
      double answerTimeoutMs_;

   protected:

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
	int SetRelativePositionUm(double dx, double dy);
	int SetAdapterOriginUm(double x, double y);
	int GetPositionUm(double& x, double& y);
	int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
	int Move(double vx, double vy);

	int SetPositionSteps(long x, long y);
	int GetPositionSteps(long& x, long& y);
	int SetRelativePositionSteps(long x, long y);
	int Home();
	int Stop();
	int SetOrigin();
	int SetAdapterOrigin();
	int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
	double GetStepSizeXUm() {return stepSizeXUm_;}
	double GetStepSizeYUm() {return stepSizeYUm_;}

	int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnStepSizeX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeedY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelX    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAccelY    (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashX (MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBacklashY (MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int GetCommand(const std::string& cmd, std::string& response);

   bool initialized_;
   bool range_measured_;
   double answerTimeoutMs_;
   double stepSizeXUm_;
   double stepSizeYUm_;
   double speedX_;
   double velocityXmm_;//G
   double velocityYmm_;//G
   double speedY_;
   double accelX_;
   double accelY_;
   double originX_;
   double originY_;
};



#endif //_LSTEP_H_
