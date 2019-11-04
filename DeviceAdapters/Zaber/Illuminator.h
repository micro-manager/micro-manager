///////////////////////////////////////////////////////////////////////////////
// FILE:          Illuminator.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device adapter for Zaber's X-LCA series LED drivers.
//
// AUTHOR:        Soleil Lapierre (contact@zaber.com)

// COPYRIGHT:     Zaber Technologies, 2019

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

#ifndef _ILLUMINATOR_H_
#define _ILLUMINATOR_H_

#include "Zaber.h"

extern const char* g_IlluminatorName;
extern const char* g_IlluminatorDescription;


class Illuminator : public CShutterBase<Illuminator>, public ZaberBase
{
public:
	Illuminator();
	~Illuminator();

	// Device API
	// ----------
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	int SetOpen(bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT);

	// Properties
	// ----------------
	int PortGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);
	int DeviceAddressGetSet(MM::PropertyBase* pProp, MM::ActionType eAct);
	int IntensityGetSet(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

private:

	int RefreshLampStatus();

	long deviceAddress_;

	// Total number of lamps supported by the device.
	int numLamps_;

	// Prevents sending commands to missing axes.
	bool *lampExists_;

	// Enables the optimization of using the device-scope lamp on command.
	bool allLampsPresent_; 

	// Enables the optimization of only turning on individual axes that
	// have nonzero flux when the shutter opens.
	double* currentFlux_;

	// Maximum flux in device units for each lamp.
	double* maxFlux_;

	// These variables exist only to enable good UX in the absence of
	// a device-scope lamp on command. The specific behavior these are 
	// for is when you are tuning presets, you leave the shutter open
	// and adjust intensities. If you change a zero intensity to 
	// nonzero, it must turn that axis on, only if the shutter is open.
	bool *lampIsOn_;
	bool isOpen_;
};

#endif
