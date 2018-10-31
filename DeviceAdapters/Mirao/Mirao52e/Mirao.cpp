///////////////////////////////////////////////////////////////////////////////
//// FILE:       Mirao.cpp
//// PROJECT:    MicroManager
//// SUBSYSTEM:  DeviceAdapters
////-----------------------------------------------------------------------------
//// DESCRIPTION:
//// The basic functions of deformable mirror Mirao52e
////                
//// AUTHOR: Nikita Vladimirov 2018/10/30
////
//

#ifdef WIN32
   #include <windows.h>
#endif

#include "Mirao.h"
#include <string>
#include <math.h>
#include "../MMDevice/ModuleInterface.h"
#include <sstream>
#include "../../3rdparty/x64/mirao52e.h"


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// device properties
const char* g_DefMirrorName = "DefMirror";
///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DefMirrorName, MM::GenericDevice, "DefMirror");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_DefMirrorName) == 0)
   {
	   DefMirror* pDefMirror = new DefMirror();
        return pDefMirror;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// DefMirror
//
DefMirror::DefMirror() :
   initialized_(false),                                                    
   answerTimeoutMs_(1000),
	version_("undefined")
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DefMirrorName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Deformable mirror Mirao52e", MM::String, true);
 
}

DefMirror::~DefMirror()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int DefMirror::Initialize()
{
	int nRet = 0;
	int status;
	MroBoolean ret;
	ret = mro_open(&status);
	if (status != MRO_OK) {
		return status;
	}
	assert(ret == MRO_TRUE);

	CPropertyAction* pAct = new CPropertyAction(this, &DefMirror::OnVersion);
	nRet = CreateProperty("Firmware version", "undefined", MM::String, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

	initialized_ = true;
	return DEVICE_OK;
}

int DefMirror::Shutdown()
{
	int status;
	MroBoolean ret;
	ret = mro_close(&status);
	if (status != MRO_OK) {
		return status;
	}
	//assert(ret == MRO_TRUE);
	return DEVICE_OK;
}

void DefMirror::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DefMirrorName);
}

bool DefMirror::Busy()
{
   return false;
}

//Firmware Version
int DefMirror::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct) {
	int status;
	MroBoolean ret;
	char dllVersion[32];
	ret = mro_getVersion(dllVersion, &status);
	assert(ret == MRO_TRUE);
	if (status != MRO_OK) {
		return status;
	}
	pProp->Set(dllVersion);
	version_ = dllVersion;
	return DEVICE_OK;
}
