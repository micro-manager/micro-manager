///////////////////////////////////////////////////////////////////////////////
// FILE:          Lumencor.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Lumencor Light Engine driver for all light engines
//						including GEN3
//
// AUTHOR:        Nenad Amodaj
//
// COPYRIGHT:     Lumencor 2019         
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

#ifndef _LUMENCOR_H_
#define _LUMENCOR_H_

#include "MMDevice.h"
#include "DeviceBase.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    13001
#define ERR_PARAMETER_ERROR          13002
#define ERR_INIT							 13003
#define ERR_INTERNAL						 13004

static const char* g_LightEngine = "LightEngine";
static const char* g_Prop_Connection = "Connection";
static const char* g_Prop_Model = "Model";
static const char* g_Prop_ModelName = "LEModel";
static const char* g_Prop_SerialNumber = "SerialNumber";
static const char* g_Prop_FirmwareVersion = "FirmwareVersion";

class LightEngineAPI;

class LightEngine : public CShutterBase<LightEngine>
{
public:
   LightEngine();
   ~LightEngine();

   // Device API
   // ----------
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   // ---------
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double /*interval*/) { return DEVICE_UNSUPPORTED_COMMAND; }

   // action interface
   // ----------------
   int OnConnection(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnModel(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnChannelEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized;
	void* engine;
	std::string model;
	std::string connection;
	std::vector<std::string> channels;
	std::map<std::string, int> channelLookup;
	bool shutterState;
	std::vector<bool> channelStates;

	int RetrieveError(void* engine);
	int ZeroAll();
	int ApplyStates();
	int TurnAllOff();
};


#endif //_LUMENCOR_H_
