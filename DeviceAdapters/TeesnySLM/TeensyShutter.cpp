//#include "TwoPhoton.h"
#include <sstream>
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "TeensyShutter.h"

using namespace std;

const char* g_VShutterDeviceName = "V2Shutter";
const char* g_PropertyShutter = "Shutter";
const char* g_PropertyDev1 = "TeensySLM1";
const char* g_PropertyDev2 = "TeensySLM2";

///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
TeensyShutter::TeensyShutter() : initialized_(false) {
	// call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // device 1
   CPropertyAction* pAct1 = new CPropertyAction(this, &TeensyShutter::OnDevice1Name);
   CPropertyAction* pAct2 = new CPropertyAction(this, &TeensyShutter::OnDevice2Name);
   int ret = CreateProperty(g_PropertyDev1, "Undefined", MM::String, false,pAct1,true);
   assert(ret == DEVICE_OK);
   // device 2
   ret = CreateProperty(g_PropertyDev2, "Undefined", MM::String, false,pAct2,true);
   assert(ret == DEVICE_OK);
}

TeensyShutter::~TeensyShutter()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void TeensyShutter::GetName(char* name) const {
   CDeviceUtils::CopyLimitedString(name, g_VShutterDeviceName);
}

/**
 * Intializes the hardware.
 */
int TeensyShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_VShutterDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for D/A channels using Teensy SLM", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int TeensyShutter::SetOpen(bool open) {
	if (deviceName1_ != "Undefined")
   {
	  const char* val = open ? "1" : "0";
	  int ret = GetCoreCallback()->SetDeviceProperty(deviceName1_, MM::g_Keyword_State, val);
      if (ret != DEVICE_OK)
         return ret;
   }

   if (deviceName2_ != "Undefined")
   {
      const char* val2 = open ? "1" : "0";
	  int ret2 = GetCoreCallback()->SetDeviceProperty(deviceName2_, MM::g_Keyword_State, val2);
      if (ret2 != DEVICE_OK)
         return ret2;
   }
   return DEVICE_OK;
}

int TeensyShutter::OnDevice1Name(MM::PropertyBase* pProp, MM::ActionType pAct) 
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(deviceName1_);
	}
	if (pAct == MM::AfterSet)
	{
		std::string name;
		pProp->Get(name);
		 CDeviceUtils::CopyLimitedString(deviceName1_,name.c_str());
		
	}
	return DEVICE_OK;
}

int TeensyShutter::OnDevice2Name(MM::PropertyBase* pProp, MM::ActionType pAct) 
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(deviceName2_);
	}
	if (pAct == MM::AfterSet)
	{
		std::string name;
		pProp->Get(name);
		 CDeviceUtils::CopyLimitedString(deviceName2_,name.c_str());
	}
	return DEVICE_OK;
}

int TeensyShutter::GetOpen(bool& open) {
   bool open1(false);

   if (deviceName1_ != "Undefined")
   {
	   char value[MM::MaxStrLength];
       int ret = GetCoreCallback()->GetDeviceProperty(deviceName1_, MM::g_Keyword_State,value);
       if (ret != DEVICE_OK)
            return ret;
		open1 = value[0] == '1';
   }

   bool open2;

   if (deviceName2_ != "Undefined")
   {
      char value2[MM::MaxStrLength];
       int ret2 = GetCoreCallback()->GetDeviceProperty(deviceName1_, MM::g_Keyword_State,value2);
       if (ret2 != DEVICE_OK)
            return ret2;
		open2 = value2[0] == '1';
   }
   else
      open2 = open1;

   assert(open1 == open2);
   open = open1;

   return DEVICE_OK;
}

