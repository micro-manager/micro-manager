// Olympus MT20 Device Adapter
//
// Copyright 2010 
// Michael Mitchell
// mich.r.mitchell@gmail.com
//
// Last modified 27.7.10
//
//
// This file is part of the Olympus MT20 Device Adapter.
//
// This device adapter requires the Real-Time Controller board that came in the original
// Cell^R/Scan^R/Cell^M/etc. computer to work. It uses TinyXML ( http://www.grinninglizard.com/tinyxml/ )
// to parse XML messages from the device.
//
// The Olympus MT20 Device Adapter is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// any later version.
//
// The Olympus MT20 Device Adapter is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the Olympus MT20 Device Adapter.  If not, see <http://www.gnu.org/licenses/>.

#ifndef _MT20_H_
#define _MT20_H_

#ifdef WIN32
	#define WIN32_LEAN_AND_MEAN 
	#define __USE_W32_SOCKETS
	#include <winsock2.h>
	#include <ws2tcpip.h>
	#include <windows.h>
#else
	#include <netdb.h>
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"

#include "MT20hub.h"

char* g_BurnerHours = "Burner Hours";

//////////////////////////////////////////////////////////////////////////////
// MT20 hub class
//////////////////////////////////////////////////////////////////////////////
class MT20HUB : public CGenericBase<MT20hub>
{
public:
	MT20HUB();
	~MT20HUB();

	// MMDevice API
	int Initialize();
	int Shutdown();
	
	void GetName(char* pszName) const;
	bool Busy();

private:
	bool initialized_;
	bool busy_;
};


//////////////////////////////////////////////////////////////////////////////
// Burner class
// Turn burner on or off, or query burner state
//////////////////////////////////////////////////////////////////////////////
class MT20Burner : public CStateDeviceBase<MT20Burner>
{
public:
	MT20Burner();
	~MT20Burner();

	// MMDevice API
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions() const {return numStates_;}

	// return number of hours on burner
	int OnHours(MM::PropertyBase* pProp, MM::ActionType eAct);

	// action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	unsigned long numStates_;
	bool busy_;
	bool initialized_;
	long state_;
	long hours_;
};


//////////////////////////////////////////////////////////////////////////////
// Shutter class
// Open, close, or get state of shutter
//////////////////////////////////////////////////////////////////////////////
class MT20Shutter : public CShutterBase<MT20Shutter>
{
public:
	MT20Shutter();
	~MT20Shutter();

	// MMDevice API
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();

	// Shutter API
	int SetOpen(bool open = true);
	int GetOpen(bool& open);
	int Fire(double deltaT) {return DEVICE_UNSUPPORTED_COMMAND;}

	// action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
private:
	bool state_;
	bool initialized_;
	bool busy_;
};


//////////////////////////////////////////////////////////////////////////////
// Filterwheel class
// Query position of or reposition 8-position excitation filterwheel
//////////////////////////////////////////////////////////////////////////////
class MT20Filterwheel : public CStateDeviceBase<MT20Filterwheel>
{
public:
	MT20Filterwheel();
	~MT20Filterwheel();

	// MMDevice API
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions() const {return numPos_;}

	// action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:

	const unsigned long numPos_;
	bool busy_;
	bool initialized_;
	long position_;
};

//////////////////////////////////////////////////////////////////////////////
// Attenuator class
// Query position of or reposition 14-position attenuator
//////////////////////////////////////////////////////////////////////////////
class MT20Attenuator : public CStateDeviceBase<MT20Attenuator>
{
public:
	MT20Attenuator();
	~MT20Attenuator();

	// MMDevice API
	int Initialize();
	int Shutdown();

	void GetName(char* pszName) const;
	bool Busy();
	unsigned long GetNumberOfPositions() const {return numPos_;}

	// action interface
	int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	const unsigned long numPos_;
	bool busy_;
	bool initialized_;
	long position_;
};


#endif //_MT20_H_