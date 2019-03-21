///////////////////////////////////////////////////////////////////////////////
// FILE:          Lumencor.cpp
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

#include "Lumencor.h"
#include "ModuleInterface.h"
#include "LightEngineAPI.h"
#include <algorithm>
#include <boost/algorithm/string.hpp>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_LightEngine, MM::ShutterDevice, "Lumencor Light Engine");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_LightEngine) == 0)
   {
      return new LightEngine();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// Lida

LightEngine::LightEngine() :
   initialized(false),
	engine(0),
	shutterState(false)
{
   InitializeDefaultErrorMessages();

	   // set device specific error messages
   SetErrorText(ERR_INIT, "Light engine initialization error, see log file for details");
   SetErrorText(ERR_INTERNAL, "Internal driver error, see log file for details");

                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------
   //
                                                                          
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_LightEngine, MM::String, true);
   //
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Lumencor Light Engine", MM::String, true);

   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction(this, &LightEngine::OnConnection);
   CreateProperty(g_Prop_Connection, "", MM::String, false, pAct, true);

	// model
	vector<string> models;
	models.push_back("GEN3");
	models.push_back("AURA2");
	models.push_back("LIDA");
	models.push_back("MIRA");
	models.push_back("RETRA");
	models.push_back("SOLASE");
	models.push_back("SOLA");
	models.push_back("SPECTRA7");
	models.push_back("SPECTRAX");
	models.push_back("LUMA");
	model = models[0];

   pAct = new CPropertyAction(this, &LightEngine::OnModel);
   CreateProperty(g_Prop_Model, model.c_str(), MM::String, false, pAct, true);
	SetAllowedValues(g_Prop_Model, models);
}                                                                            
                                                                             
LightEngine::~LightEngine()                                                            
{                                                                            
   Shutdown();                                                               
} 

void LightEngine::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LightEngine);
}  

int LightEngine::Initialize()
{
   if (initialized)
      return DEVICE_OK;

	int ret(DEVICE_OK);

	// create light engine
	bool legacy(false);
	if (model.compare("GEN3") == 0)
		ret = lum_createLightEngine(&engine); // gen3 (universal)
	else
	{
		ret = lum_createLegacyLightEngineByName(&engine, model.c_str()); // legacy
		legacy = true;
	}

	if (ret != LUM_OK)
	{
		ostringstream os;
		os << "Light Engine create() failed for model: " << model;
		LogMessage(os.str());
		return ERR_INIT;
	}

	size_t numDots = count(connection.begin(), connection.end(), '.');
	if (numDots == 3)
	{
		// interpreting destination as IP address
		ret = lum_connectTCP(engine, connection.c_str(), LUM_DEFAULT_TCP_PORT);
	}
	else
	{
		ret = lum_connectCOM(engine, connection.c_str(), legacy ? LUM_LEGACY_BAUD_RATE : LUM_STANDARD_BAUD_RATE);
	}

	if (ret != LUM_OK)
		return RetrieveError(engine);

	// get light engine info
	// obtain model
	char engModel[LUM_MAX_MESSAGE_LENGTH];
	ret = lum_getModel(engine, engModel, LUM_MAX_MESSAGE_LENGTH);
	if (ret != LUM_OK)
		return RetrieveError(engine);
	CreateProperty(g_Prop_ModelName, engModel, MM::String, true);

	// obtain firmware version
	// (not available in legacy mode)
	if (!legacy)
	{
		char version[LUM_MAX_MESSAGE_LENGTH];
		ret = lum_getVersion(engine, version, LUM_MAX_MESSAGE_LENGTH);
		if (ret != LUM_OK)
			return RetrieveError(engine);
		CreateProperty(g_Prop_FirmwareVersion, version, MM::String, true);
	}

	// obtain device serial number
	// (not available on legacy light engines)
	if (!legacy)
	{
		char serialNumber[LUM_MAX_MESSAGE_LENGTH];
		ret = lum_getSerialNumber(engine, serialNumber, LUM_MAX_MESSAGE_LENGTH);
		if (ret != LUM_OK)
			return RetrieveError(engine);
		CreateProperty(g_Prop_SerialNumber, serialNumber, MM::String, true);
	}

	int maxIntensity(0);
	ret = lum_getMaximumIntensity(engine, &maxIntensity);
	if (ret != LUM_OK)
		return RetrieveError(engine);

	// discover light channels
	int numChannels(0);
	ret = lum_getNumberOfChannels(engine, &numChannels);
	if (ret != LUM_OK)
		return RetrieveError(engine);

	channels.clear();
	for (int i = 0; i < numChannels; i++)
	{
		char chName[LUM_MAX_MESSAGE_LENGTH];
		ret = lum_getChannelName(engine, i, chName, LUM_MAX_MESSAGE_LENGTH);
		if (ret != LUM_OK)
			return RetrieveError(engine);

		channels.push_back(chName);
	}

	channelLookup.clear();
	for (size_t i=0; i<channels.size(); i++)
	{
		CPropertyAction* pAct = new CPropertyAction(this, &LightEngine::OnChannelEnable);
		ret = CreateProperty(channels[i].c_str(), "0", MM::Integer, false, pAct);
		SetPropertyLimits(channels[i].c_str(), 0, 1);                                

		ostringstream os;
		os << channels[i] << "_" << "Intensity";
	   pAct = new CPropertyAction(this, &LightEngine::OnChannelIntensity);
		ret = CreateProperty(os.str().c_str(), "0", MM::Integer, false, pAct);
		SetPropertyLimits(os.str().c_str(), 0, maxIntensity);

		channelLookup[channels[i]] = (int)i; 
	}

   // reset light engine
   // ------------------
	channelStates.resize(numChannels, false);
	ret = ZeroAll();
	if (ret != DEVICE_OK)
		return ret;

	ret = TurnAllOff();
	if (ret != DEVICE_OK)
		return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &LightEngine::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
   // switch all channels off on startup
   SetProperty(MM::g_Keyword_State, "0");

   UpdateStatus();

   initialized = true;
   return DEVICE_OK;
}

int LightEngine::Shutdown()
{
   if (initialized)
   {
		lum_disconnect(engine);
		lum_deleteLightEngine(engine);
		engine = 0;
      initialized = false;
   }
   return DEVICE_OK;
}

// Never busy because all commands block
bool LightEngine::Busy()
{
   return false;
}

int LightEngine::SetOpen(bool open)
{
   if (open)
		// open shutter
		return ApplyStates();
	else
		// close shutter
		return TurnAllOff();
} 

int LightEngine::GetOpen(bool& open)
{
	open = shutterState;
   return DEVICE_OK;                                                         
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int LightEngine::OnConnection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(connection.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized)
      {
         // revert
         pProp->Set(connection.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
                                                                             
      pProp->Get(connection);                                                     
   }                                                                         
   return DEVICE_OK;     
}

int LightEngine::OnModel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(model.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized)
      {
         // revert
         pProp->Set(model.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
                                                                             
      pProp->Get(model);                                                     
   }                                                                         
   return DEVICE_OK;     
}

int LightEngine::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetOpen(open);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(open ? 1L : 0L);
      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      return SetOpen(pos != 0);
   }

   return DEVICE_OK;
}

// *****************************************************************************
// Color Value Change Handlers
// *****************************************************************************

int LightEngine::OnChannelIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	vector<string> tokens;
	string name = pProp->GetName();
	boost::split(tokens, name, boost::is_any_of("_"));
	if (tokens.size() == 0)
	{
		ostringstream os;
		os << "Invalid channel name: " << pProp->GetName();
		LogMessage(os.str());
		return ERR_INTERNAL;
	}

	string channel = tokens[0];
	map<string, int>::iterator it = channelLookup.find(channel);
	if (it == channelLookup.end())
	{
		ostringstream os;
		os << "Invalid channel name: " << channel;
		LogMessage(os.str());
		return ERR_INTERNAL;
	}

	int channelIdx = it->second;

   if (eAct == MM::AfterSet)
   {
      long val;
      pProp->Get(val);
		int ret = lum_setIntensity(engine, channelIdx, val);
      if (ret != LUM_OK)
			return RetrieveError(engine);
   }
   if (eAct == MM::BeforeGet)
   {
      int inten;
      int ret = lum_getIntensity(engine, channelIdx, &inten);
      if (ret != DEVICE_OK)
         RetrieveError(engine);

      pProp->Set((long)inten);
   }
   return DEVICE_OK;
}


int LightEngine::OnChannelEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string channel = pProp->GetName();
	map<string, int>::iterator it = channelLookup.find(channel);
	if (it == channelLookup.end())
	{
		ostringstream os;
		os << "Invalid channel name: " << channel;
		LogMessage(os.str());
		return ERR_INTERNAL;
	}

	int channelIdx = it->second;

   if (eAct == MM::AfterSet)
   {
      long enable;
      pProp->Get(enable);
		if (shutterState)
		{
			// apply command to light engine if shutter is open
			int ret = lum_setChannel(engine, channelIdx, enable == 0 ? false : true);
			if (ret != LUM_OK)
				return RetrieveError(engine);
		}

		channelStates[channelIdx] = enable == 0 ? false : true;
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(channelStates[channelIdx] ? 1L : 0L);
   }
   return DEVICE_OK;
}

// Get error from light engine
int LightEngine::RetrieveError(void* engine)
{
	const int maxLength(1024);
	int errorCode;
	char errorText[maxLength];
	lum_getLastErrorCode(engine, &errorCode);
	lum_getLastErrorText(engine, errorText, maxLength);

	ostringstream os;
	os << "Error : " << errorCode << ", " << errorText << endl;
	SetErrorText(errorCode, os.str().c_str());

	return errorCode;
}

// turns channels off but does not record change in channel state cache: channelStates
// used by the shutter emulator to implement closed shutter state
int LightEngine::TurnAllOff()
{
	vector<lum_bool> states;
	for (size_t i=0; i<channels.size(); i++) states.push_back(false);
	int ret = lum_setMultipleChannels(engine, &states[0], (int)channels.size());
	if (ret != LUM_OK)
		return RetrieveError(engine);

	// do not update channel cache
	shutterState = false;
	return DEVICE_OK;
}

int LightEngine::ZeroAll()
{
	vector<int> ints;
	for (size_t i=0; i<channels.size(); i++) ints.push_back(0);
	int ret = lum_setMultipleIntensities(engine, &ints[0], (int)channels.size());
	if (ret != LUM_OK)
		return RetrieveError(engine);
	return DEVICE_OK;
}

// simulates "open shutter" command by appling states that were in effect when shutter was closed
//
int LightEngine::ApplyStates()
{
	vector<lum_bool> states;
	for (size_t i=0; i<channels.size(); i++) states.push_back(channelStates[i]);
	int ret = lum_setMultipleChannels(engine, &states[0], (int)channels.size());
	if (ret != LUM_OK)
		return RetrieveError(engine);
	shutterState = true;

	return DEVICE_OK;
}
