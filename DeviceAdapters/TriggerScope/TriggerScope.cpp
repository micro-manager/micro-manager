///////////////////////////////////////////////////////////////////////////////
// FILE:          TriggerScope.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the ARC TriggerScope device adapter.
//				  See http://www.trggerscope.com
//                
// AUTHOR:        Austin Blanco, 21 July 2015
//
// COPYRIGHT:     Advanced Research Consulting. (2014-2015)
//
// LICENSE:       

//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "TriggerScope.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include <sstream>
#include <algorithm>
#include <iostream>

#include "TriggerScope.h"

using namespace std;

// External names used used by the rest of the system
// to load particular device from the "TriggerScope.dll" library
const char* g_TriggerScopeHubDeviceName = "TriggerScope-Hub";
const char* g_TriggerScopeDACDeviceName = "TriggerScope-DAC00";
const char* g_TriggerScopeTTLDeviceName = "TriggerScope-TTL00";
const char* g_TriggerScopeTTLMasterDeviceName = "TriggerScope-TTL-Master";
const char* g_TriggerScopeCAMDeviceName = "TriggerScope-CAM0";
const char* g_TriggerScopeFocusDeviceName = "TriggerScope-Focus";

const char* g_Keyword_Clear = "Clear Arrays";
const char* g_Keyword_Clear_Off = "Off";
const char* g_Keyword_Clear_Active_Array = "Clear Active Array";
const char* g_Keyword_Clear_All_Arrays = "Clear All Arrays";


const char* serial_terminator = "\n";
const unsigned char uc_serial_terminator = 10;
const char* line_feed = "\n";
const char * g_TriggerScope_Version = "v1.6.5, 8/16/16";
bool g_bTS16 = false;

const char* g_On = "On";
const char* g_Off = "Off";

// static lock
MMThreadLock CTriggerScopeHub::lock_;

// TODO: linux entry code

// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

/**
 * List all suppoerted hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   RegisterDevice("TriggerScope-CAM1", MM::StateDevice, "CAM1");
   RegisterDevice("TriggerScope-CAM2", MM::StateDevice, "CAM2");
   RegisterDevice("TriggerScope-DAC01", MM::SignalIODevice, "DAC01");
   RegisterDevice("TriggerScope-DAC02", MM::SignalIODevice, "DAC02");
   RegisterDevice("TriggerScope-DAC03", MM::SignalIODevice, "DAC03");
   RegisterDevice("TriggerScope-DAC04", MM::SignalIODevice, "DAC04");
   RegisterDevice("TriggerScope-DAC05", MM::SignalIODevice, "DAC05");
   RegisterDevice("TriggerScope-DAC06", MM::SignalIODevice, "DAC06");
   RegisterDevice("TriggerScope-DAC07", MM::SignalIODevice, "DAC07");
   RegisterDevice("TriggerScope-DAC08", MM::SignalIODevice, "DAC08");
   RegisterDevice("TriggerScope-DAC09", MM::SignalIODevice, "DAC09");
   RegisterDevice("TriggerScope-DAC10", MM::SignalIODevice, "DAC10");
   RegisterDevice("TriggerScope-DAC11", MM::SignalIODevice, "DAC11");
   RegisterDevice("TriggerScope-DAC12", MM::SignalIODevice, "DAC12");
   RegisterDevice("TriggerScope-DAC13", MM::SignalIODevice, "DAC13");
   RegisterDevice("TriggerScope-DAC14", MM::SignalIODevice, "DAC14");
   RegisterDevice("TriggerScope-DAC15", MM::SignalIODevice, "DAC15");
   RegisterDevice("TriggerScope-DAC16", MM::SignalIODevice, "DAC16");

   //RegisterDevice("TriggerScope-TTL-Master", MM::StateDevice, "TTL-Master");
   RegisterDevice("TriggerScope-TTL01", MM::StateDevice, "TTL01");
   RegisterDevice("TriggerScope-TTL02", MM::StateDevice, "TTL02");
   RegisterDevice("TriggerScope-TTL03", MM::StateDevice, "TTL03");
   RegisterDevice("TriggerScope-TTL04", MM::StateDevice, "TTL04");
   RegisterDevice("TriggerScope-TTL05", MM::StateDevice, "TTL05");
   RegisterDevice("TriggerScope-TTL06", MM::StateDevice, "TTL06");
   RegisterDevice("TriggerScope-TTL07", MM::StateDevice, "TTL07");
   RegisterDevice("TriggerScope-TTL08", MM::StateDevice, "TTL08");
   RegisterDevice("TriggerScope-TTL09", MM::StateDevice, "TTL09");
   RegisterDevice("TriggerScope-TTL10", MM::StateDevice, "TTL10");
   RegisterDevice("TriggerScope-TTL11", MM::StateDevice, "TTL11");
   RegisterDevice("TriggerScope-TTL12", MM::StateDevice, "TTL12");
   RegisterDevice("TriggerScope-TTL13", MM::StateDevice, "TTL13");
   RegisterDevice("TriggerScope-TTL14", MM::StateDevice, "TTL14");
   RegisterDevice("TriggerScope-TTL15", MM::StateDevice, "TTL15");
   RegisterDevice("TriggerScope-TTL16", MM::StateDevice, "TTL16");
   RegisterDevice("TriggerScope-Focus", MM::StageDevice, "Focus");
   RegisterDevice("TriggerScope-Hub", MM::HubDevice, "Hub");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, "TriggerScope-Hub") == 0)
   {
      // create camera
      return new CTriggerScopeHub();
   }
   else if (strcmp(deviceName, "TriggerScope-CAM1") == 0)
   {
      // create camera
      return new CTriggerScopeCAM(1);
   }
   else if (strcmp(deviceName, "TriggerScope-CAM2") == 0)
   {
      // create camera
      return new CTriggerScopeCAM(2);
   }
   else if (strcmp(deviceName, "TriggerScope-DAC01") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(1);
   }
   else if (strcmp(deviceName, "TriggerScope-DAC02") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(2);
   }
   else if (strcmp(deviceName, "TriggerScope-DAC03") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(3);
   }
   else if (strcmp(deviceName, "TriggerScope-DAC04") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(4);
   }
   else if (strcmp(deviceName, "TriggerScope-DAC05") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(5);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC06") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(6);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC07") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(7);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC08") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(8);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC09") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(9);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC10") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(10);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC11") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(11);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC12") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(12);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC13") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(13);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC14") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(14);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC15") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(15);
   }
    else if (strcmp(deviceName, "TriggerScope-DAC16") == 0)
   {
      // create camera
      return new CTriggerScopeDAC(16);
   }

   else if (strcmp(deviceName, "TriggerScope-TTL-Master") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(0);
   }

	else if (strcmp(deviceName, "TriggerScope-TTL01") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(1);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL02") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(2);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL03") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(3);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL04") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(4);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL05") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(5);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL06") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(6);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL07") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(7);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL08") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(8);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL09") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(9);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL10") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(10);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL11") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(11);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL12") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(12);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL13") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(13);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL14") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(14);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL15") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(15);
   }
   else if (strcmp(deviceName, "TriggerScope-TTL16") == 0)
   {
      // create camera
      return new CTriggerScopeTTL(16);
   }
   else if (strcmp(deviceName, "TriggerScope-Focus") == 0)
   {
      // create camera
      return new CTriggerScopeFocus();
   }
   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CTriggerScopeHub implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* CTriggerScopeHub constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/

///////////////////////////////////////////////////////////////////////////////
// TriggerScope implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~
 
CTriggerScopeHub::CTriggerScopeHub(void)  :
   busy_(false),
   error_(0),
   timeOutTimer_(0),
   fidSerialLog_(NULL),
   firmwareVer_(0.0),
   cmdInProgress_(0),
   initialized_(0),
   nUseSerialLog_(1),
   progfileCSV_("TriggerScope.csv"),
   clearStr_(g_Keyword_Clear_Off)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   pResourceLock_ = new MMThreadLock();

      // parent ID display
   CreateHubIDProperty();

   //Com port
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeHub::OnCOMPort);

   //CreateProperty(MM::g_Keyword_BaudRate, "57600", MM::String, false);
   CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);

}

int CTriggerScopeHub::DetectInstalledDevices()
{  
   ClearInstalledDevices();

   // make sure this method is called before we look for available devices
   InitializeModuleData();

   char hubName[MM::MaxStrLength];
   GetName(hubName); // this device name
   for (unsigned i=0; i<GetNumberOfDevices(); i++)
   { 
      char deviceName[MM::MaxStrLength];
      bool success = GetDeviceName(i, deviceName, MM::MaxStrLength);
      if (success && (strcmp(hubName, deviceName) != 0))
      {
         MM::Device* pDev = CreateDevice(deviceName);
         AddInstalledDevice(pDev);
      }
   }
   return DEVICE_OK; 
}

/**
* CTriggerScopeHub destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
CTriggerScopeHub::~CTriggerScopeHub(void)
{
                                                             
   Shutdown();
   delete pResourceLock_;
}


void CTriggerScopeHub::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeHubDeviceName);
}
void CTriggerScopeDAC::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeDACDeviceName);
   snprintf(&Name[strlen(Name)-2], 3, "%02d", nChannel_);
}
void CTriggerScopeTTL::GetName(char* Name) const
{	
   if(nChannel_==0)
   {
	   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeTTLMasterDeviceName);   
   }
   else
   {
	   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeTTLDeviceName);   
	   snprintf(&Name[strlen(Name)-2], 3, "%02d", nChannel_);
   }
}
void CTriggerScopeCAM::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeCAMDeviceName);
   Name[strlen(Name)-1] += (char)nChannel_;
}
void CTriggerScopeFocus::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TriggerScopeFocusDeviceName);
}

int CTriggerScopeHub::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

	char profilepath[1000];
#ifdef _WIN32
	ExpandEnvironmentStrings(TEXT("%userprofile%"),profilepath,1000);

#else
	//strcpy_s(profilepath,1000,"/tmp");
#endif
	char strLog[1024];

	time_t rawtime;
	time ( &rawtime );

#ifdef _WIN32
	sprintf_s(strLog,1024,"%s\\TriggerScope_SerialLog.txt",profilepath);
#else
	snprintf(strLog,1024,"/tmp/TriggerScope_Serial_Log_%d.txt",getpid());
#endif
	fidSerialLog_ = fopen(strLog,"w");

	nUseSerialLog_ = 1;
	if(fidSerialLog_) 
		fprintf(fidSerialLog_, "Version: %s, Time: %s\n", g_TriggerScope_Version, ctime (&rawtime) );
	else
		nUseSerialLog_ = 0;

   zeroTime_ = GetCurrentMMTime();   

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_TriggerScopeHubDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "TriggerScope", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = HandleErrors();
   if (DEVICE_OK != ret)
      return ret;

   // Version
   char str[256];
  
   cmdInProgress_ = 1; 

   CPropertyAction* pAct = NULL;
   portAvailable_ = true;
   g_bTS16 = false;

	firmwareVer_  = 0.0;
	for(int ii=0;ii<10;ii++)
	{
      CDeviceUtils::SleepMs(1000);
		Purge();
		Send("*");
		ReceiveOneLine(1);
		if(buf_string_.length()>0)
		{
			size_t idx = buf_string_.find("ARC TRIGGERSCOPE 16");
			if(idx!=string::npos)
				g_bTS16 = true;			

			idx = buf_string_.find("ARC_LED 16");
			if(idx!=string::npos)
				g_bTS16 = true;			
				
			idx = buf_string_.find("ARC TRIGGERSCOPE");

			if(idx==string::npos)
				idx = buf_string_.find("ARC_LED");

			if(idx!=string::npos)
			{
				idx = buf_string_.find("v.");
				if(idx!=string::npos)
					firmwareVer_ = atof(&(buf_string_.c_str()[idx+2]));
				else
				{
					idx = buf_string_.find("v");
					if(idx!=string::npos)
						firmwareVer_ = atof(&(buf_string_.c_str()[idx+1]));
				}	
				break;
			}
		}
	}
	if(firmwareVer_==0.0)
		return DEVICE_SERIAL_TIMEOUT;

	if(buf_string_.length()>0)
		snprintf(str, 256, "%s", buf_string_.c_str());
	else
	{
      return DEVICE_SERIAL_TIMEOUT;
	}
   ret = CreateProperty("Firmware Version", str, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = CreateProperty("Software Version", g_TriggerScope_Version, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   if(g_bTS16)
	   ret = CreateProperty("DAC Bits", "16", MM::String, true);
   else
	   ret = CreateProperty("DAC Bits", "12", MM::String, true);

   if (DEVICE_OK != ret)
      return ret;

    string strBuffer;
    CDeviceUtils::SleepMs(600);
	Send("STAT?");
   CDeviceUtils::SleepMs(10);
	for(int ii=0; ii<5; ii++)
	{
		ReceiveOneLine(1);
		if(buf_string_.length()>0)
			break;
	}
	if(buf_string_.length()==0)
	{
		Send("STAT?");
      CDeviceUtils::SleepMs(10);
		ReceiveOneLine(2);
	}
	strBuffer = buf_string_;
	while(buf_string_.length()>0)
	{
		ReceiveOneLine(1);
		if(buf_string_.length()>0)
			strBuffer.append(buf_string_);
	}

   CreateProperty("COM Port", port_.c_str(), MM::String, true);

	CreateProperty("Trigger Time Delta", "", MM::String, true);


   	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnSendSerialCmd);
	ret = CreateProperty("Serial TX", "", MM::String, false, pAct);
	assert(ret == DEVICE_OK);

   	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnRecvSerialCmd);
	ret = CreateProperty("Serial RX", "", MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnProgFile);
	ret = CreateProperty("Program File", "TriggerScope.csv", MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnLoadProg);
	ret = CreateProperty("Program Load", "0", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);
	ret = SetPropertyLimits("Program Load", 0, 1);

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnStepMode);
	ret = CreateProperty("Step Mode", "0", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);
	stepMode_ = 0;

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnArmMode);
	ret = CreateProperty("Arm Mode", "0", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);
	armMode_ = 0;
	ret = SetPropertyLimits("Arm Mode", 0, 1);
	if (ret != DEVICE_OK) 
		return ret;

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnArrayNum);
	ret = CreateProperty("Array #", "1", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);
	armMode_ = 0;
	ret = SetPropertyLimits("Array #", 1, 6);
	if (ret != DEVICE_OK) 
		return ret;

	pAct = new CPropertyAction (this, &CTriggerScopeHub::OnClear);
	ret = CreateProperty(g_Keyword_Clear, g_Keyword_Clear_Off, MM::String, false, pAct);
	assert(ret == DEVICE_OK);
	AddAllowedValue(g_Keyword_Clear, g_Keyword_Clear_Off);
	AddAllowedValue(g_Keyword_Clear, g_Keyword_Clear_Active_Array);
	AddAllowedValue(g_Keyword_Clear, g_Keyword_Clear_All_Arrays);


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   cmdInProgress_ = 0;

   //thd_->Start();

   return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int CTriggerScopeHub::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool CTriggerScopeHub::Busy()
{
   if (timeOutTimer_ == 0)
      return false;
   if (timeOutTimer_->expired(GetCurrentMMTime()))
   {
      // delete(timeOutTimer_);
      return false;
   }
   return true;
}

int CTriggerScopeHub::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}


CTriggerScopeTTL::CTriggerScopeTTL(int nChannel)
{
	nChannel_ = nChannel;
	initialized_ = false;
	ttl_ = 0;
	busy_ = false;
	numPos_ = 0;
	sequenceOn_ = true;

}

int CTriggerScopeTTL::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

  CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   if(nChannel_==0)
   {
	   CPropertyAction *pAct = new CPropertyAction(this, &CTriggerScopeTTL::OnSequence);
	   int nRet = CreateProperty("Sequence", g_On, MM::String, false, pAct);
	   if (nRet != DEVICE_OK)
		  return nRet;
	   AddAllowedValue("Sequence", g_On);
	   AddAllowedValue("Sequence", g_Off);
   }

   // State
   // -----
   CPropertyAction *pAct = new CPropertyAction (this, &CTriggerScopeTTL::OnState);
   
	int nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (nRet != DEVICE_OK)
		return nRet;

   if(nChannel_==0)
   {
	   SetPropertyLimits(MM::g_Keyword_State, 0, 16);
   }
   else
   {
	   SetPropertyLimits(MM::g_Keyword_State, 0, 1);
   }

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateStringProperty(MM::g_Keyword_Label, "", false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

	/////////////////////////
// Gate Closed Position
   //nRet = CreateIntegerProperty(MM::g_Keyword_Closed_Position, 0, false);
   //if (nRet != DEVICE_OK)
   //   return nRet;

   // create default positions and labels
   SetPositionLabel(0, "Closed");
   //AddAllowedValue(MM::g_Keyword_Closed_Position, "0");
   SetPositionLabel(1, "Open");
   //AddAllowedValue(MM::g_Keyword_Closed_Position, "1");


   /////////////////////////////////////////////////

/*   pAct = new CPropertyAction (this, &CTriggerScopeTTL::OnTTL);
   int ret = CreateProperty("TTL", "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("TTL", 0, 1);
   if (ret != DEVICE_OK) 
	  return ret;
*/

// Gate Closed Position
/*   nRet = CreateProperty(MM::g_Keyword_Closed_Position, "0", MM::Integer, false);
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }
   SetPropertyLimits(MM::g_Keyword_Closed_Position, 0, 1); 
*/   
   GetGateOpen(open_);
   numPos_ = 2;
   curPos_ = 0;

   initialized_ = true;
   return DEVICE_OK;
}


int CTriggerScopeTTL::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceOn_)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == g_On)
         sequenceOn_ = true;
      else
         sequenceOn_ = false;
   }
   return DEVICE_OK;
}

int CTriggerScopeTTL::LoadSequence(unsigned size, unsigned char* seq)
{
    CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   char str[128];
   snprintf(str, 128, "CLEAR_TTL,%d",nChannel_);

   //hub->PurgeComPortH();
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();

   for(unsigned int ii=0; ii<size; ii++)
   {
		
		snprintf(str, 128, "PROG_TTL,%d,%d,%d",ii+1,nChannel_,int(seq[ii]));
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
		
		if(hub->buf_string_.length()==0)
		{
			hub->Purge();
			hub->Send(str);
         CDeviceUtils::SleepMs(10);
			hub->ReceiveOneLine();
		}
   }
   return DEVICE_OK;
}


/**
   * Implements a gate, i.e. a position where the state device is closed
   * The gate needs to be implemented in the adapter's 'OnState function
   * (which is called through SetPosition)
   */
int CTriggerScopeTTL::SetGateOpen(bool open)
{  
    if (open_ != open) {
        return SetPosition((int)open);
    }
    return DEVICE_OK;
}

int CTriggerScopeTTL::GetGateOpen(bool& open)
{
    open = open_; 
    return DEVICE_OK;
}

int CTriggerScopeTTL::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      bool gateOpen;
      GetGateOpen(gateOpen);
      long pos;
      pProp->Get(pos);

      if (pos >= (long)numPos_ || pos < 0)
      {
         pProp->Set((long)curPos_); // revert
         return ERR_UNKNOWN_POSITION;
      }
// For efficiency, the previous state and gateOpen position is cached
      if (1) {
         // check if we are already in that state
         if (((unsigned)pos == curPos_) && open_)
            return DEVICE_OK;

         // try to apply the value
         if (WriteToPort(pos)!=DEVICE_OK)
         {
            pProp->Set((long)curPos_); // revert
            return ERR_UNKNOWN_POSITION;
         }
      } else {
         if (!open_) {
            curPos_ = pos;
            return DEVICE_OK;
         }

         //char closedPos[MM::MaxStrLength];
		 int gateClosedPosition = 0;
         //if(!GetProperty(MM::g_Keyword_Closed_Position, closedPos))
	     //    gateClosedPosition = atoi(closedPos);

         if (WriteToPort(gateClosedPosition)!=DEVICE_OK)
         {
            pProp->Set((long) curPos_); // revert
            return ERR_UNKNOWN_POSITION;
         }
      }
      curPos_ = pos;
      open_ = pos>0;
	  

   }
   else if (eAct == MM::IsSequenceable)                                      
   {                                                                         
      if (sequenceOn_)                                                       
         pProp->SetSequenceable(NUMPATTERNS);                           
      else                                                                   
         pProp->SetSequenceable(0);                                          
   } 
   else if (eAct == MM::AfterLoadSequence)                                   
   {                                                                         
      std::vector<std::string> sequence = pProp->GetSequence();              
      std::ostringstream os;
      if (sequence.size() > NUMPATTERNS)                                
         return DEVICE_SEQUENCE_TOO_LARGE;                                   
      unsigned char* seq = new unsigned char[sequence.size()];               
      for (unsigned int i=0; i < sequence.size(); i++)                       
      {
         std::istringstream os (sequence[i]);
         int val;
         os >> val;
         seq[i] = (unsigned char) val;
      }                                                                      
      int ret = LoadSequence((unsigned) sequence.size(), seq);
      if (ret != DEVICE_OK)                                                  
         return ret;                                                         
                                                                             
      delete[] seq;                                                          
   }                                                                         
   else if (eAct == MM::StartSequence)
   { 
      MMThreadGuard myLock(hub->GetLock());

	char str[128];
	snprintf(str, 128, "ARM");

	//hub->PurgeComPortH();
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();

/*      hub->PurgeComPortH();
      unsigned char command[1];
      command[0] = 8;
      int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[1];
      while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 1, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 8)
         return ERR_COMMUNICATION;*/
   }
   else if (eAct == MM::StopSequence)                                        
   {
/*      MMThreadGuard myLock(hub->GetLock());

	char str[128];
	sprintf_s(str, 128, "SAFE");

	hub->PurgeComPortH();
	hub->Purge();
	hub->Send(str);
	Sleep(10);
	hub->ReceiveOneLine();
*/	

/*      unsigned char command[1];
      command[0] = 9;
      int ret = hub->WriteToComPortH((const unsigned char*) command, 1);
      if (ret != DEVICE_OK)
         return ret;

      MM::MMTime startTime = GetCurrentMMTime();
      unsigned long bytesRead = 0;
      unsigned char answer[2];
      while ((bytesRead < 2) && ( (GetCurrentMMTime() - startTime).getMsec() < 250)) {
         unsigned long br;
         ret = hub->ReadFromComPortH(answer + bytesRead, 2, br);
         if (ret != DEVICE_OK)
            return ret;
         bytesRead += br;
      }
      if (answer[0] != 9)
         return ERR_COMMUNICATION;

      std::ostringstream os;
      os << "Sequence had " << (int) answer[1] << " transitions";
      LogMessage(os.str().c_str(), false);
	  */
   }                                                                         

   return DEVICE_OK;
}

CTriggerScopeCAM::CTriggerScopeCAM(int nChannel)
{
	nChannel_ = nChannel;
	initialized_ = false;
	cam_ = 0;
	busy_ = false;
	numPos_ = 0;
}

int CTriggerScopeCAM::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

  CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.


   CPropertyAction *pAct = new CPropertyAction (this, &CTriggerScopeCAM::OnCAM);
   int ret = CreateProperty("CAM", "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("CAM", 0, 1);
   if (ret != DEVICE_OK) 
	  return ret;

   initialized_ = true;
   return DEVICE_OK;
}

CTriggerScopeDAC::CTriggerScopeDAC(int nChannel)
{
	nChannel_ = nChannel;
	initialized_ = false;
	volts_ = 0.0;
	minV_ = 0.0;
	maxV_ = 10.0;
	busy_ = false;
	open_ = false;

	gateOpen_ = true;
	gatedVolts_ = 0.0;
}

int CTriggerScopeDAC::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

  CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

   int ret;
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeDAC::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   pAct = new CPropertyAction (this, &CTriggerScopeDAC::OnVolts);
   ret = CreateProperty("Volts", "0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetPropertyLimits("Volts", minV_, maxV_);
   if (ret != DEVICE_OK) 
	  return ret;

   initialized_ = true;
   return DEVICE_OK;
}


int CTriggerScopeDAC::SetGateOpen(bool open)
{
   if (open) {
      int ret = SetSignal(volts_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = true;
   } else {
      int ret = SetSignal(0);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
   }
   return DEVICE_OK;
}

int CTriggerScopeDAC::GetGateOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}


int CTriggerScopeDAC::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetGateOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1) {
         ret = this->SetGateOpen(true);
      } else {
         ret = this->SetGateOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


CTriggerScopeFocus::CTriggerScopeFocus()
{
	initialized_ = false;	
	busy_ = false;	
	stepSize_um_ = 1.0;
	lowerLimit_ = 0.0;
	upperLimit_ = 1000.0;
	pos_um_ = 0.0;
	lowerLimit_ = 0;
	upperLimit_ = 1000;
	sequenceOn_ = true;
	nDAC_ = 1;
	sequenceable_ = true;
	nrEvents_ = 1024;
	sequenceOn_ = true;

   CPropertyAction* pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnUpperLimit);
   int ret = CreateProperty("Upper Limit", "1000", MM::Float, false, pAct, true);

   pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnLowerLimit);
   ret = CreateProperty("Lower Limit", "0", MM::Float, false, pAct, true);

}

int CTriggerScopeFocus::IsStageSequenceable(bool& isSequenceable) const 
{
	isSequenceable = sequenceable_ && sequenceOn_ && g_bTS16; 
	return DEVICE_OK;
}


int CTriggerScopeFocus::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


  CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable()) {
      return ERR_NO_PORT_SET;
   }
   char hubLabel[MM::MaxStrLength];
   hub->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward comp.

  // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &CTriggerScopeFocus::OnPosition);
   int ret = CreateFloatProperty(MM::g_Keyword_Position, 0, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnSequence);
   ret = CreateProperty("Sequence", g_On, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Sequence", g_On);
   AddAllowedValue("Sequence", g_Off);

   //pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnDACNumber);
   ret = CreateProperty("DAC Number", "16", MM::Integer, true);

   char str[32];
   snprintf(str, 32, "%g", upperLimit_ );
   pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnUpperLimit);
   ret = CreateProperty("Z Upper Limit", str, MM::Float, true);

   snprintf(str, 32, "%g", lowerLimit_ );
   pAct = new CPropertyAction(this, &CTriggerScopeFocus::OnLowerLimit);
   ret = CreateProperty("Z Lower Limit", str, MM::Float, true);

   SetPropertyLimits(MM::g_Keyword_Position, lowerLimit_, upperLimit_);

   initialized_ = true;
   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CTriggerScopeFocus::OnDACNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << nDAC_;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      long num;
      pProp->Get(num);
      if (num < 1 || num > 16)
      {
         pProp->Set(nDAC_); // revert
         return ERR_INVALID_VALUE;
      }
	  else
		  nDAC_ = num ;
   }
   return DEVICE_OK;
}

int CTriggerScopeFocus::OnUpperLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << upperLimit_;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(upperLimit_);
      SetPropertyLimits(MM::g_Keyword_Position, lowerLimit_, upperLimit_);
   }
   return DEVICE_OK;
}

int CTriggerScopeFocus::OnLowerLimit(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << lowerLimit_;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(lowerLimit_);
      SetPropertyLimits(MM::g_Keyword_Position, lowerLimit_, upperLimit_);
   }
   return DEVICE_OK;
}


int CTriggerScopeFocus::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      std::stringstream s;
      s << pos_um_;
      pProp->Set(s.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      if (pos > upperLimit_ || lowerLimit_ > pos)
      {
         pProp->Set(pos_um_); // revert
         return ERR_UNKNOWN_POSITION;
      }
	  SetPositionUm(pos) ;
   }

   return DEVICE_OK;
}

int CTriggerScopeFocus::SetPositionUm(double pos) 
{
	if (pos > upperLimit_ || lowerLimit_ > pos)
	{
		return ERR_UNKNOWN_POSITION;
	}

    pos_um_ = pos;
    double dPos;
	int nPos;
	dPos = (pos - lowerLimit_) / (upperLimit_ - lowerLimit_);
	dPos = dPos <= 1.0 ? dPos : 1.0;
	dPos = dPos >= 0.0 ? dPos : 0.0;

	if(g_bTS16)
		nPos = int(dPos*65535.0);
	else
		nPos = int(dPos*4095.0);

	WriteToPort(nPos);

   return OnStagePositionChanged(pos_um_);
}


int CTriggerScopeFocus::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceOn_)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string state;
      pProp->Get(state);
      if (state == g_On)
         sequenceOn_ = true;
      else
         sequenceOn_ = false;
   }
   return DEVICE_OK;
}

int CTriggerScopeFocus::StartStageSequence()
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();   
	char str[] = "ARM";
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	
   return DEVICE_OK;
}


int CTriggerScopeFocus::StopStageSequence() 
{
/*	
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   hub->PurgeComPortH();
	char str[] = "SAFE";
	hub->Purge();
	hub->Send(str);
	Sleep(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
		Sleep(10);
		hub->ReceiveOneLine();
	}
*/	
   return DEVICE_OK;
}



int CTriggerScopeFocus::SendStageSequence()
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();
   hub->Purge();


	char str[256];
	unsigned int nNum, nDir=1, nSlave=0, nStartVal, nStepVal, nSize ;
	double dStart, dStep, dEnd ;

	nSize  = (unsigned int) sequence_.size();
	dStart = (double) sequence_[0];
	dEnd   = (double) sequence_[nSize-1];
	nSlave = 0;

	nNum = 1;
	for(unsigned int ii=1; ii<nSize; ii++)
	{
		if(sequence_[ii] != sequence_[ii-1] )
			nNum++;
	}

	if(nNum==1)
		dStep = 0;
	else if(dEnd>dStart)
	{
		dStep  = (dEnd-dStart)/(nNum-1);
		nDir = 1;
	}
	else
	{
		dStep  = (dStart-dEnd)/(nNum-1);
		nDir = 0;
	}

	double dVal ;

	dVal = (double(dStart) - lowerLimit_) / (upperLimit_ - lowerLimit_);
	dVal = dVal < 1.0 ? dVal : 1.0;
	dVal = dVal > 0.0 ? dVal : 0.0;
	nStartVal = (unsigned int)(65535.0*dVal);

	dVal = double(dStep) / (upperLimit_ - lowerLimit_);
	dVal = dVal < 1.0 ? dVal : 1.0;
	dVal = dVal > 0.0 ? dVal : 0.0;
	nStepVal = (unsigned int)(65535.0*dVal);

    //hub->PurgeComPortH();
	hub->Purge();
	hub->Send("CLEAR_FOCUS");
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();

	snprintf(str, 256, "PROG_FOCUS,%d,%d,%d,%d,%d", nStartVal, nStepVal, nNum, nDir, nSlave);

	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
	}

    return DEVICE_OK;
}



int CTriggerScopeFocus::ClearStageSequence()
{
   sequence_.clear();

   return DEVICE_OK;
}



int CTriggerScopeFocus::AddToStageSequence(double position)
{
   sequence_.push_back(position);

   return DEVICE_OK;
}



int CTriggerScopeHub::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

/*
int CTriggerScopeTTL::SetOpen(bool bOpen)
{
   long pos;
   if (bOpen)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int CTriggerScopeTTL::GetOpen(bool &bOpen)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   bOpen = (pos==1);

   return DEVICE_OK;
}
*/

int CTriggerScopeTTL::OnTTL(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(ttl_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(ttl_);
		WriteToPort(ttl_);
   }
   return DEVICE_OK;
}
int CTriggerScopeCAM::OnCAM(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(cam_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(cam_);
		WriteToPort(cam_);
   }
   return DEVICE_OK;
}


int CTriggerScopeDAC::WriteToPort(unsigned long value)
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();
   hub->Purge();


	char str[32];
	snprintf(str, 32, "DAC%d,%d",nChannel_,int(value));
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
	}

   return DEVICE_OK;
}

int CTriggerScopeDAC::StartDASequence()
{
    //(const_cast<DemoDA *>(this))->SetSequenceStateOn();
    return DEVICE_OK;
}
int CTriggerScopeDAC::StopDASequence()
{
/*	CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	MMThreadGuard myLock(hub->GetLock());

	hub->PurgeComPortH();
	hub->Purge();
	hub->Send("SAFE");
	Sleep(10);
	hub->ReceiveOneLine();
*/
	return DEVICE_OK;
    //(const_cast<DemoDA *>(this))->SetSequenceStateOff();
}
int CTriggerScopeDAC::SendDASequence()
{
	CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
	if (!hub || !hub->IsPortAvailable())
		return ERR_NO_PORT_SET;

	MMThreadGuard myLock(hub->GetLock());

	char str[128];
	snprintf(str, 128, "CLEAR_DAC,%d",nChannel_);

	//hub->PurgeComPortH();
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();

	double dMaxCount = 4095, volts;
	if(g_bTS16)
		dMaxCount = 65535;

	for(unsigned int ii=0; ii<sequence_.size(); ii++)
	{		
		volts = sequence_[ii];

		if(volts < minV_)
			volts = minV_ ;
		if(volts > maxV_)
			volts = maxV_ ;

		volts_ = volts;

	    long value = (long) ( (volts - minV_) / maxV_ * dMaxCount);

		snprintf(str, 128, "PROG_DAC,%d,%d,%d",ii+1,nChannel_,int(value));
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
		
		if(hub->buf_string_.length()==0)
		{
			hub->Purge();
			hub->Send(str);
         CDeviceUtils::SleepMs(10);
			hub->ReceiveOneLine();
		}
	}
	return DEVICE_OK;
}

int CTriggerScopeDAC::ClearDASequence()
{
	sequence_.clear();
    return DEVICE_OK;
}

int CTriggerScopeDAC::AddToDASequence(double voltage)
{
   sequence_.push_back(voltage);
   return DEVICE_OK;
}

/** 
* Starts execution of the sequence
*/
int CTriggerScopeDAC::StartPropertySequence(const char* propertyName) 
{
	if(strcmp(propertyName,"Volts")==0)
	{
		CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
		if (!hub || !hub->IsPortAvailable())
			return ERR_NO_PORT_SET;

		MMThreadGuard myLock(hub->GetLock());

		//hub->PurgeComPortH();
		hub->Purge();
		hub->Send("ARM");
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();

	    return DEVICE_OK;
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}
/**
* Stops execution of the device
*/
int CTriggerScopeDAC::StopPropertySequence(const char* propertyName) 
{
	if(strcmp(propertyName,"Volts")==0)
	{
		return StopDASequence();
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}
/**
* remove previously added sequence
*/
int CTriggerScopeDAC::ClearPropertySequence(const char* propertyName) 
{
	if(strcmp(propertyName,"Volts")==0)
	{
	   return ClearDASequence();
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}
/**
* Add one value to the sequence
*/
int CTriggerScopeDAC::AddToPropertySequence(const char* propertyName, const char* value) 
{
	if(strcmp(propertyName,"Volts")==0)
	{
		double voltage;
		voltage = atof(value);
		return AddToDASequence(voltage);
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}
/**
* Signal that we are done sending sequence values so that the adapter can send the whole sequence to the device
*/
int CTriggerScopeDAC::SendPropertySequence(const char* propertyName) 
{
	if(strcmp(propertyName,"Volts")==0)
	{
		return SendDASequence();
	}
	else
		return DEVICE_UNSUPPORTED_COMMAND;
}

int CTriggerScopeFocus::WriteToPort(unsigned long value)
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();
   hub->Purge();


	char str[16];
	snprintf(str, 16, "FOCUS,%d",int(value));
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
	}

   return DEVICE_OK;
}

int CTriggerScopeTTL::WriteToPort(unsigned long value)
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();
   
	char str[16];
	snprintf(str, 16, "TTL%d,%d",nChannel_,int(value));
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
	}

   return DEVICE_OK;
}

int CTriggerScopeCAM::WriteToPort(unsigned long value)
{
   CTriggerScopeHub* hub = static_cast<CTriggerScopeHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_NO_PORT_SET;

   MMThreadGuard myLock(hub->GetLock());

   //hub->PurgeComPortH();


	char str[16];
	snprintf(str, 16, "CAM%d,%d",nChannel_,int(value));
	hub->Purge();
	hub->Send(str);
   CDeviceUtils::SleepMs(10);
	hub->ReceiveOneLine();
		
	if(hub->buf_string_.length()==0)
	{
		hub->Purge();
		hub->Send(str);
      CDeviceUtils::SleepMs(10);
		hub->ReceiveOneLine();
	}

   return DEVICE_OK;
}


int CTriggerScopeDAC::WriteSignal(double volts)
{
	if(volts < minV_)
		volts = minV_ ;
	if(volts > maxV_)
		volts = maxV_ ;

	volts_ = volts;
	double dMaxCount = 4095;
	if(g_bTS16)
		dMaxCount = 65535;

   long value = (long) ( (volts - minV_) / maxV_ * dMaxCount);

   std::ostringstream os;
    os << "Volts: " << volts << " Max Voltage: " << maxV_ << " digital value: " << value;
    LogMessage(os.str().c_str(), true);

   return WriteToPort((unsigned long)value);
}

int CTriggerScopeDAC::SetSignal(double volts)
{
   volts_ = volts;
   if (gateOpen_) {
      gatedVolts_ = volts_;
      return WriteSignal(volts_);
   } else {
      gatedVolts_ = 0;
   }

   return DEVICE_OK;
}


int CTriggerScopeDAC::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(volts_);
   }
   else if (eAct == MM::AfterSet)
   {
		double volts;
	    pProp->Get(volts);
		WriteSignal(volts);
   }

   return DEVICE_OK;
}


int CTriggerScopeDAC::OnMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxV_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxV_);
      if (HasProperty("Volts"))
         SetPropertyLimits("Volts", 0.0, maxV_);

   }
   return DEVICE_OK;
}
/*
int CTriggerScopeFocus::OnFocus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(focus_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(focus_);
		char str[16];
		double focusDAC = 0.0;
		if(focusMax_ != focusMin)
			focusDAC = (focus_ - focusMin_) / (focusMax_ - focusMin);

		if(focusDAC > 1.0)
			focusDAC = 1.0;
		if(focusDAC < 0.0)
			focusDAC = 0.0;
		// 12 bit DAC, 5V max
		sprintf_s(str, "FOCUS,%d",int(4095.0*focusDAC));
		pHub_->Purge();
		pHub_->Send(str);
		Sleep(10);
		pHub_->ReceiveOneLine();
		
		if(buf_string_.length()==0)
		{
			pHub_->Purge();
			pHub_->Send(str);
			Sleep(10);
			pHub_->ReceiveOneLine();
		}

   }

   return DEVICE_OK;
}*/

int CTriggerScopeHub::OnStepMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepMode_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(stepMode_);
		char str[16];
		snprintf(str, 16, "STEP,%d", (int) stepMode_);
		Purge();
		Send(str);
		ReceiveOneLine();
		
		if(buf_string_.length()==0)
		{
			Purge();
			Send(str);
			ReceiveOneLine();
		}

   }

   return DEVICE_OK;
}
int CTriggerScopeHub::OnArrayNum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(arrayNum_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(arrayNum_);
		char str[16];
		snprintf(str, 16, "ARRAY,%d", (int) arrayNum_);
		Purge();
		Send(str);
		ReceiveOneLine();
		
		if(buf_string_.length()==0)
		{
			Purge();
			Send(str);
			ReceiveOneLine();
		}

   }

   return DEVICE_OK;
}
int CTriggerScopeHub::OnArmMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(armMode_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(armMode_);
		if(armMode_>0)
		{
			char str[16];
			snprintf(str, 16, "ARM");
			Purge();
			Send(str);
			ReceiveOneLine();
		
			if(buf_string_.length()==0)
			{
				Purge();
				Send(str);
				ReceiveOneLine();
			}
			armMode_ = 0;
		}
   }

   return DEVICE_OK;
}

int CTriggerScopeHub::OnClear(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(clearStr_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   char str[16];
	   pProp->Get(clearStr_);

	   if(clearStr_.compare(g_Keyword_Clear_Active_Array)==0)
			snprintf(str, 16, "CLEARTABLE");
	   else if(clearStr_.compare(g_Keyword_Clear_All_Arrays)==0)
			snprintf(str, 16, "CLEARALL");

	   if(clearStr_.compare(g_Keyword_Clear_Off)!=0)
	   {
			Purge();
			Send(str);
			ReceiveOneLine();
		
			if(buf_string_.length()==0)
			{
				Purge();
				Send(str);
				ReceiveOneLine();
			}
		}
   }

   return DEVICE_OK;
}

int CTriggerScopeHub::OnProgFile(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(progfileCSV_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(progfileCSV_);
   }
   return DEVICE_OK;
}

int CTriggerScopeHub::OnLoadProg(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(loadProg_);
   }
   else if (eAct == MM::AfterSet)
   {
	    pProp->Get(loadProg_);

		if(loadProg_>0)
		    LoadProgFile();

		loadProg_ = 0;
   }

   return DEVICE_OK;
}


int CTriggerScopeHub::OnSendSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(sendSerialCmd_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(sendSerialCmd_);
	   recvSerialCmd_ = "";
	   Purge();
		Send(sendSerialCmd_.c_str());
      CDeviceUtils::SleepMs(10);
		ReceiveOneLine();
		if(buf_string_.length()>0)
			recvSerialCmd_ = buf_string_;

   }
   return DEVICE_OK;
}
int CTriggerScopeHub::OnRecvSerialCmd(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(recvSerialCmd_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   pProp->Get(recvSerialCmd_);
   }
   return DEVICE_OK;
}

int CTriggerScopeHub::LoadProgFile()
{
	FILE *fid;

	fid = fopen(progfileCSV_.c_str(), "r");
	char strLine[1024], strOut[1024], *str, *str1, *str2;
	int nNum=0, nTable[16];

	if(fid > (void*) 0)
	{
		Send("CLEARTABLE");
		ReceiveOneLine();

		while(fgets(strLine, 1024, fid))
		{
			str = strtok(strLine,",\t\n");
			if(strncmp(strLine,"PROG",4)==0)
			{
				str = strtok(NULL,",\t\n");
				if(str)
				{
					if(sscanf(str,"%d",&nNum)>0)
					{
						nTable[0] = nNum;

						for(int ii=1; ii<16; ii++)
						{
							str1 = strtok(NULL,",\t\n");
							if(str1)
							{
								nTable[ii] = -1;
								if(str1[0] != 'N')
								{
									sscanf(str1,"%d",&nTable[ii]);
								}
							}
						}
						str2 = strOut;
						
						snprintf(str2, 1024, "PROG");
						for(int ii=0; ii<16; ii++)
						{
							str2 = strchr(str2, 0);
							if(str2)
							{
								if(nTable[ii]==-1)
									snprintf(str2, 1024-strlen(strOut), ",N");
								else
									snprintf(str2, 1024-strlen(strOut), ",%d",nTable[ii]);
							}
						}

						Purge();
						Send(strOut);
						ReceiveOneLine();

					}
				}
			}
		}
      CDeviceUtils::SleepMs(500);
		Send("STAT?");
		ReceiveOneLine(2);
		while(buf_string_.length()>0)
		{
			ReceiveOneLine(2);
		}
	}

	return DEVICE_OK;
}

/////////////////////////////////////
//  Communications
/////////////////////////////////////


void CTriggerScopeHub::Send(string cmd)
{
   int ret = SendSerialCommand(port_.c_str(), cmd.c_str(), serial_terminator);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
   currentTime_ = GetCurrentMMTime();   
   if(strlen(cmd.c_str()) && fidSerialLog_ && nUseSerialLog_)
   {
		fprintf(fidSerialLog_, "%.3f > ",(currentTime_-zeroTime_).getMsec()/1000.0 );

	   fprintf(fidSerialLog_, "%s\n", cmd.c_str());
   }
}

void CTriggerScopeHub::SendSerialBytes(unsigned char* cmd, unsigned long len)
{
   int ret = WriteToComPort(port_.c_str(), cmd, len);
   if (ret!=DEVICE_OK)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;

	currentTime_ = GetCurrentMMTime();   
	if(nUseSerialLog_ && fidSerialLog_)
	{
		fprintf(fidSerialLog_, "%.3f > ",(currentTime_-zeroTime_).getMsec()/1000.0 );
	   
	   for(unsigned long ii=0; ii<len; ii++)
		   fprintf(fidSerialLog_, "%02X ",cmd[ii]);

	   fprintf(fidSerialLog_, "\n");
	}
}




void CTriggerScopeHub::ReceiveOneLine(int nLoopMax)
{
   buf_string_ = "";
   int nLoop=0, nRet=-1;
   string buf_str;
   while(nRet!=0 && nLoop<nLoopMax)
   {
	   nRet = GetSerialAnswer(port_.c_str(), serial_terminator, buf_str);	   
	   nLoop++;
	   if(buf_str.length()>0)
		   buf_string_.append(buf_str);
   }
   if(nLoop>1)
	   nLoop += 0;

   if(strlen(buf_string_.c_str())>0)
   {
	   GetTriggerTimeDelta(buf_string_.c_str());

   }

   currentTime_ = GetCurrentMMTime();   
   if(strlen(buf_string_.c_str()) && fidSerialLog_ && nUseSerialLog_ )
   {
   		fprintf(fidSerialLog_, "%.3f < ",(currentTime_-zeroTime_).getMsec()/1000.0 );

	   fprintf(fidSerialLog_, "%s\n", buf_string_.c_str());
   }
}

void CTriggerScopeHub::ReceiveSerialBytes(unsigned char* buf, unsigned long buflen, unsigned long bytesToRead, unsigned long &totalBytes)
{
   buf_string_ = "";
   int nLoop=0, nRet=0;
   unsigned long bytesRead=0;
   totalBytes=0;
   buf[0] = 0;
   
   MM::MMTime timeStart, timeNow;
   timeStart = GetCurrentMMTime();   

   while(nRet==0 && totalBytes < bytesToRead && (timeNow.getMsec()-timeStart.getMsec()) < 5000)
   {
	   nRet = ReadFromComPort(port_.c_str(), &buf[totalBytes], buflen-totalBytes, bytesRead);
	   nLoop++;
	   totalBytes += bytesRead;
	   timeNow = GetCurrentMMTime();   
      CDeviceUtils::SleepMs(1);
   }
   if(nLoop>1)
	   nLoop += 0;
 
   	if(nUseSerialLog_ && fidSerialLog_)
	{
	   if(totalBytes>0)
	   {
			currentTime_ = GetCurrentMMTime();   
			fprintf(fidSerialLog_, "%.3f < ",(currentTime_-zeroTime_).getMsec()/1000.0 );
		   for(unsigned long ii=0; ii<totalBytes; ii++)
			   fprintf(fidSerialLog_, "%02X ",buf[ii]);

		   fprintf(fidSerialLog_, "\n");
	   }

	   if(timeNow.getMsec()-timeStart.getMsec() > 5000)
			fprintf(fidSerialLog_, "$ Timeout\n");
	}
}

void CTriggerScopeHub::FlushSerialBytes(unsigned char* buf, unsigned long buflen)
{
   buf_string_ = "";
   int nRet=0;
   unsigned long bytesRead=0;
   buf[0] = 0;
   
   nRet = ReadFromComPort(port_.c_str(), buf, buflen, bytesRead);

   	if(nUseSerialLog_ && fidSerialLog_)
	{
	   if(bytesRead>0)
	   {
		   fprintf(fidSerialLog_, "* ");
		   for(unsigned long ii=0; ii<bytesRead; ii++)
			   fprintf(fidSerialLog_, "%02X ",buf[ii]);

		   fprintf(fidSerialLog_, "\n");
	   }
	}
}


void CTriggerScopeHub::Purge()
{
	unsigned char buf[1024];
	unsigned long bytesRead=0;

	int nRet = ReadFromComPort(port_.c_str(), &buf[0], 1024, bytesRead);
	if(bytesRead>0)
		GetTriggerTimeDelta((const char*)&buf[0]);

	if(strlen((const char*)&buf[0]) && fidSerialLog_ && nUseSerialLog_ )
   {
   		fprintf(fidSerialLog_, "* %.3f < ",(currentTime_-zeroTime_).getMsec()/1000.0 );

	   fprintf(fidSerialLog_, "%s\n", buf);
   }

   int ret = PurgeComPort(port_.c_str());
   if (ret!=0)
      error_ = DEVICE_SERIAL_COMMAND_FAILED;
}

int CTriggerScopeHub::GetTriggerTimeDelta(const char *buf_string)
{
	const char *str=NULL;
	char str2[32], *str3;
	double dTime = -1.0f;
	str = strstr(buf_string, "@TIME");
	if(str)
	{
		dTime = atof(&str[7]);
		if(dTime>=0.0)
		{
			strcpy(str2, &str[7]);
			str3 = strchr(&str2[0], 10);
			if(str3)
				str3[0] = 0;
			str3 = strchr(&str2[0], 13);
			if(str3)
				str3[0] = 0;

			OnPropertyChanged("Trigger Time Delta", str2);
		}
	}
	return DEVICE_OK;

}
