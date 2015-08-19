///////////////////////////////////////////////////////////////////////////////
// FILE:          Newport.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Newport Controller Driver
//
// AUTHOR:        Liisa Hirvonen, 03/17/2009
// AUTHOR:        Nico Stuurman 08/18/2005, added velocity, multiple addresses, enabling multiple controllers, relative position, easier busy check and multiple fixes for annoying behavior, see repository logs for complete list
// COPYRIGHT:     University of Melbourne, Australia, 2009-2013
// COPYRIGHT:     Regents of the University of California, 2015
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

#ifndef _Newport_H_
#define _Newport_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN	10004
#define ERR_UNRECOGNIZED_ANSWER		10009
#define ERR_OFFSET					   10100
#define ERR_POSITION_BEYOND_LIMITS  10300
#define ERR_TIMEOUT                 10301
#define CONTROLLER_ERROR            20000

class NewportZStage : public CStageBase<NewportZStage>
{

public:
	NewportZStage();
	~NewportZStage();

	// Device API
	// ----------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	// Stage API
	// ---------
	int SetPositionUm(double pos);
	int SetRelativePositionUm(double pos);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& min, double& max);

	// action interface
	// ----------------
	int OnConversionFactor(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnControllerAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsStageSequenceable(bool& isSequenceable) const {
      isSequenceable = false;
      return DEVICE_OK;
   }
   bool IsContinuousFocusDrive() const {return false;}

private:
   int SetVelocity(double velocity);
   int GetVelocity(double& velocity);
   int GetError(bool& error, std::string& errorCode);
   int WaitForBusy();
   int GetValue(const char* cmd, double& val);
   std::string MakeCommand(const char* cmd);

   std::string port_;
   double stepSizeUm_;
   double conversionFactor_;
   int cAddress_;
   bool initialized_;
   double lowerLimit_; // limit in native coordinates
   double upperLimit_; // limit in native coordinates
   double velocity_;   // velocity in native coordinates
   double velocityLowerLimit_; // limit in native coordinates
   double velocityUpperLimit_; // limit in native coordinates
};


#endif //_Newport_H_
