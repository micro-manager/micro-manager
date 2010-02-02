///////////////////////////////////////////////////////////////////////////////
// FILE:          AndorLaserCombiner.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   AndorLaserCombiner controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
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
// AUTHOR:        Karl Hoover, UCSF
//
//

#ifndef _AndorLaserCombiner_H_
#define _AndorLaserCombiner_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
//#include <iostream>
#include <vector>
//using namespace std;
const int MaxLasers = 10;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
class ALCImpl;

class AndorLaserCombiner : public CShutterBase<AndorLaserCombiner>
{
private:
	double minlp_;
	double maxlp_;

public:


	// power setting limits:
	double minlp(){ return minlp_;};
	void minlp(double v__) { minlp_= v__;};
	double maxlp(){ return maxlp_;};
	void maxlp(double v__) { maxlp_= v__;};
   AndorLaserCombiner(const char* name);
   ~AndorLaserCombiner();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   // action interface
   // ----------------
   int OnAddress(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPowerSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnPowerReadback(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnConnectionType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReceivedData(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

	// some important read-only properties
	int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnMaximumLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
	int OnWaveLength(MM::PropertyBase* pProp, MM::ActionType eAct, long index);


	//ALC mechanics
   int OnPiezoRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPiezoPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDIN(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDOUT(MM::PropertyBase* pProp, MM::ActionType eAct);


   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);


	int Wavelength(const int laserIndex__);
	int PowerFullScale(const int laserIndex__);
	bool Ready(const int laserIndex__);
	float PowerReadback(const int laserIndex__);

	// setpoint in milliwatts
	float PowerSetpoint(const int laserIndex__);
	void PowerSetpoint( const int laserIndex__, const float);

	

	float PiezoRange(void);
	void PiezoRange(const float);
	float PiezoPosition(void);
	void PiezoPosition(const float);
	unsigned char DIN(void);
	void DOUT(const unsigned char);



private:

  // double powerSetpoint_;
//	double powerReadback_;
   long state_;
   int error_;

   bool initialized_;
   std::string name_;


   unsigned char buf_[1000];

   long armState_;

   bool busy_;
   double answerTimeoutMs_;


   MM::MMTime changedTime_;

   void GenerateALCProperties();
   void GeneratePropertyState();

	void GenerateReadOnlyIDProperties();

	// todo -- can move these to the implementation
   int HandleErrors();
   AndorLaserCombiner& operator=(AndorLaserCombiner& /*rhs*/) {assert(false); return *this;}

	// implementation
	ALCImpl* pImpl_;
	int nLasers_;
	// 1 based array
	float powerSetPoint_[MaxLasers+1];
	bool openRequest_;
	unsigned char DOUT_;

};


#endif // _AndorLaserCombiner_H_
