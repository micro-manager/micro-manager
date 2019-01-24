///////////////////////////////////////////////////////////////////////////////
// FILE:          SutterWheel.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
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
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/26/2005
//                Nico Stuurman, Oct. 2010
//            Nick Anthony, Oct. 2018
//

#ifndef _SUTTER_WHEEL_H_
#define _SUTTER_WHEEL_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "WheelBase.h"

class Wheel: public WheelBase<Wheel>{
public:
	Wheel(const char* name, unsigned id):
		WheelBase(name, id, false,  "Sutter Lambda Filter Wheel")
	{};
};


class LambdaVF5: public WheelBase<LambdaVF5>
{
public:
	LambdaVF5(const char* name);

	int Initialize();

	//VF-5 special commands
	int onWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);//(unsigned int wavelength);
	int onWheelTilt(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onTiltSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onTTLOut(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onTTLIn(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onSequenceType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onTTLOutPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int onTTLInPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
	int configureTTL( bool risingEdge, bool enabled, bool output, unsigned char channel);
	long wv_;
	long uSteps_;
	long tiltSpeed_;
	bool ttlInEnabled_;
	bool ttlInRisingEdge_;
	bool ttlOutEnabled_;
	bool ttlOutRisingEdge_;
	bool sequenceEvenlySpaced_;
};

#endif
