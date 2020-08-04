///////////////////////////////////////////////////////////////////////////////
// FILE:          BDPathway.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   BDPathway adapter.
//
// AUTHOR:        Egor Zindy, ezindy@gmail.com, 01/01/2020
//
//                A big "Thank you" to Liam P. who spent a week mashing all the
//                AttoVision buttons, menus and options and helped me make sense
//                of the Portmon output. And to the Manchester crew (Peter, 
//                Steve, Roger and Kang) who let us play with the BDPathway 855.
//                It was all worth it in the end ;-)
//
//                Based on the Nikon TE2000 adapter by
//                Nenad Amodaj, nenad@amodaj.com, 05/03/2006
// COPYRIGHT:     University of California San Francisco, 2006
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
// CVS:           $Id$
// 

#include "BDPathway.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "PathwayHub.h"
#include <sstream>

// Shutters
const char* g_HubName = "BDPathway-Controller";
const char* g_ShutterAName = "BDPathway-ShutterA";
const char* g_ShutterBName = "BDPathway-ShutterB";
const char* g_ShutterTName = "BDPathway-ShutterTrans";

// Filter wheels
const char* g_WheelAName = "BDPathway-WheelA";
const char *g_WheelAFilters[] = {"P1 (470/40)", "P2 (488/10A)", "P3 (555/28)", "P4 (334/10)", "P5 (380/10ND)", "P6 (360/10)", "P7 (380/10)", "P8 (440/10)"};

const char* g_WheelBName = "BDPathway-WheelB";
const char *g_WheelBFilters[] = {"P1 (334/10)", "P2 (548/20)", "P3 (460/10)", "P4 (560/55)", "P5 (635/20)", "P6 (488/10B)", "P7 (Open)", "P8 (Open)"};

const char* g_WheelExcitDichroicName = "BDPathway-WheelExcitDichroic";
const char* g_WheelExcitDichroicFilters[] = {"P1 (Mirror)", "P2 (370DCLP)", "P3 (Open)", "P4 (50%%A10%%B)", "P5 (10%%A40%%B)"};

const char* g_WheelEpiDichroicName = "BDPathway-WheelEpiDichroic";
const char* g_WheelEpiDichroicFilters[] = {"P1 (Fura/FITC)", "P2 (Fura/TRITC)", "P3 (400DCLP)", "P4 (595LP)", "P5 (84000)"};

const char* g_WheelEmisionName = "BDPathway-WheelEmission";
const char* g_WheelEmisionFilters[] = {"P1 (FURA/FITC)", "P2 (FURA/TRITC)", "P3 (435LP)", "P4 (84101)", "P5 (515LP)", "P6 (570LP)", "P7 (645/75)", "P8 (335)"};

//Other state devices
const char* g_AccessDoor = "BDPathway-AccessDoor"; //R
const char* g_AccessDoorStates[] = {"Closed", "Open"};

const char* g_ViewportName = "BDPathway-Viewport"; //M
const char* g_ViewportStates[] = {"Camera", "Occular"};

const char* g_ConfocalModeName = "BDPathway-ConfocalMode"; //D
const char* g_ConfocalModeStates[] = {"Off", "On"};

const char* g_SpinningDisk = "BDPathway-SpinningDisk"; //G
const char* g_SpinningDiskStates[] = {"Off", "On"};

//Stages
const char* g_FocusName = "BDPathway-ZStage";
const char* g_XYStageName = "BDPathway-XYStage";

const char* g_UnknownName = "BDPathway-UNKNOWN";

//Sequences used to initialize and release the hardware. Any guesses as to some of these do?

/*
const char *g_InitSequence[] = {"R0", "C1", "R1", "rd", "WoXC-509997,-313501", "WnXC8,12", "WsXC90001,90089", "gdL", "LzZ0", "gfWS", "WpX0", "WpY0", "gdS", "WoSC270,4460", "WnSC8,12", "WsSC-354,355", "gdW", "mM", "gtX", "gtY", "gtZ", "gtz", "FeS-4100,-1200", "FcS-4500,500", "WnST8,12", "WoST230,-70", "WsST-353,355", "FhST-1715", "FhSC-1655", "FhSL-1645", "frS", "WoSL220,8360", "WnSL8,12", "WsSL-355,365", "gdT", "gdE", "gdF", "gdD", "gdR", "\0"};
*/

// const char *g_InitSequence[] = {"R0", "C1", "R1", "rd", "WoXC-509997,-313501", "WnXC8,12", "WsXC90001,90089", "gdL", "LzZ0", "gfWS", "WpX0", "WpY0", "gdS", "WoSC270,4460", "WnSC8,12", "WsSC-354,355", "gdW", "mM", "FeS-4100,-1200", "FcS-4500,500", "WnST8,12", "WoST230,-70", "WsST-353,355", "FhST-1715", "FhSC-1655", "FhSL-1645", "frS", "WoSL220,8360", "WnSL8,12", "WsSL-355,365", "gdT", "gdE", "gdF", "gdD", "gdR", "\0"};
//const char *g_ReleaseSequence[] = {"P00", "ME,1", "MF,1", "MT,1", "R0", "C0", "gtX", "gtY", "gtZ", "\0"};
const char *g_InitSequence[] = {"R0", "C1", "R1", "WoXC-487525,-309471", "WnXC8,12", "\0"};

//shut the transmitted light off...
const char *g_ReleaseSequence[] = {"P00", "MT,1", "R0", "C0", "\0"};

using namespace std;

PathwayHub g_hub;
bool g_PFSinstalled = false;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_HubName, MM::HubDevice, "BDPathway controller - required for all other devices");
   RegisterDevice(g_FocusName, MM::StageDevice, "Z-stage");
   RegisterDevice(g_XYStageName, MM::XYStageDevice, "XY-stage");

   //Filter wheels on the 855. Some of these will not be available on the 435
   RegisterDevice(g_WheelAName, MM::StateDevice, "Excitation filter wheel A");
   RegisterDevice(g_WheelBName, MM::StateDevice, "Excitation filter wheel B");
   RegisterDevice(g_WheelExcitDichroicName , MM::StateDevice, "Excitation Dichroic wheel");
   RegisterDevice(g_WheelEpiDichroicName, MM::StateDevice, "Epifluorescence Dichroic wheel");
   RegisterDevice(g_WheelEmisionName, MM::StateDevice, "Emission filter wheel");

   //Shutters on the 855. Some of these will not be available on the 435
   RegisterDevice(g_ShutterAName, MM::ShutterDevice, "Excitation shutter A");
   RegisterDevice(g_ShutterBName, MM::ShutterDevice, "Excitation shutter B");
   RegisterDevice(g_ShutterTName, MM::ShutterDevice, "Transmitted light shutter");

   //Other state devices
   RegisterDevice(g_AccessDoor, MM::StateDevice, "Sample loading");
   RegisterDevice(g_ViewportName, MM::StateDevice, "Viewport");
   RegisterDevice(g_ConfocalModeName, MM::StateDevice, "Confocal mode");
   RegisterDevice(g_SpinningDisk, MM::StateDevice, "Turn the spinning disk on/off");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_HubName) == 0)
   {
      // create BDPathway hub
      return new Hub;
   }

   if (strcmp(deviceName, g_FocusName) == 0)
   {
      // create shutter
      return new FocusStage;
   }

   if (strcmp(deviceName, g_XYStageName) == 0)
   {
      // create shutter
      return new XYStage;
   }

   if (strcmp (deviceName, g_WheelAName) == 0)
   {
	   // Return the State device (8 pos filter wheel)
		 return new StateDevice('A',8, g_WheelAName, g_WheelAFilters);
   }

   if (strcmp (deviceName, g_WheelBName) == 0)
   {
	   // Return the State device (8 pos filter wheel)
		 return new StateDevice('B',8, g_WheelBName, g_WheelBFilters);
   }

   if (strcmp (deviceName, g_WheelExcitDichroicName) == 0)
   {
	   // Return the State device (5 pos filter wheel)
		 return new StateDevice('C',5, g_WheelExcitDichroicName, g_WheelExcitDichroicFilters);
   }

   if (strcmp (deviceName, g_WheelEpiDichroicName) == 0)
   {
	   // Return the State device (5 pos filter wheel)
		 return new StateDevice('c',5, g_WheelEpiDichroicName, g_WheelEpiDichroicFilters);
   }

   if (strcmp (deviceName, g_WheelEmisionName) == 0)
   {
	   // Return the State device (8 pos filter wheel)
		 return new StateDevice('a',8, g_WheelEmisionName, g_WheelEmisionFilters);
   }

   if (strcmp (deviceName, g_ShutterAName) == 0)
   {
	   // Return the Shutter device
		 return new BDShutter('E', g_ShutterAName);
   }

   if (strcmp (deviceName, g_ShutterBName) == 0)
   {
	   // Return the Shutter device
		 return new BDShutter('F', g_ShutterBName);
   }

   if (strcmp (deviceName, g_ShutterTName) == 0)
   {
	   // Return the Shutter device
		 return new BDShutter('T', g_ShutterTName);
   }

   if (strcmp (deviceName, g_ViewportName) == 0)
   {
	   // Return the State device (2 positions)
		 return new StateDevice('M',2, g_ViewportName, g_ViewportStates);
   }

   if (strcmp (deviceName, g_ConfocalModeName) == 0)
   {
	   // Return the State device (2 positions)
		 return new StateDevice('D',2, g_ConfocalModeName, g_ConfocalModeStates);
   }

   if (strcmp (deviceName, g_SpinningDisk) == 0)
   {
	   // Return the State device (2 positions)
		 return new StateDevice('G',2, g_SpinningDisk, g_SpinningDiskStates);
   }

   if (strcmp (deviceName, g_AccessDoor) == 0)
   {
	   // Return the State device (2 positions)
		 return new StateDevice('R',2, g_AccessDoor, g_AccessDoorStates);
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// BD Pathway controller hub
// ~~~~~~~~~~~~~~~~~~~~~~~~~

Hub::Hub() : initialized_(false), name_(g_HubName)
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   SetErrorText(DEVICE_SERIAL_INVALID_RESPONSE, "The response was not recognized");

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BDPathway hub", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // try to obtain version info
   if (!IsCallbackRegistered())
      return DEVICE_NO_CALLBACK_REGISTERED;

   // Initialize the BDPathway here...
   ret = g_hub.ExecuteSequence(*this, *GetCoreCallback(), g_InitSequence);
   if (ret != DEVICE_OK)
      return ret;

   // Version
   string ver = "0.0";
   ret = g_hub.GetVersion(*this, *GetCoreCallback(), ver);
   if (ret != DEVICE_OK)
      return ret;

   ret = CreateProperty(MM::g_Keyword_Version, ver.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // model type (a private variable)
   string model = "AttoXXX";
   ret = g_hub.GetModelType(*this, *GetCoreCallback(), model);
   if (ret != DEVICE_OK)
      return ret;

   ret = CreateProperty("ModelType", model.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Hub::InstallIfMounted(std::string deviceName, const char* deviceCode)
{
   int ret = -1;
   if (g_hub.IsComponentMounted(*this, *GetCoreCallback(), deviceCode))
   {
      MM::Device* pDev = ::CreateDevice(deviceName.c_str());
      if (pDev)
      {
         AddInstalledDevice(pDev);
	 ret = DEVICE_OK;
      }
   }
   return ret;
}

int Hub::DetectInstalledDevices()
{
   AddInstalledDevice(CreateDevice(g_FocusName));
   AddInstalledDevice(CreateDevice(g_XYStageName));

   InstallIfMounted(g_WheelAName, "mA");
   InstallIfMounted(g_WheelBName, "mB");
   InstallIfMounted(g_WheelExcitDichroicName, "mC");
   InstallIfMounted(g_WheelEpiDichroicName, "mc");
   InstallIfMounted(g_WheelEmisionName, "ma");

   InstallIfMounted(g_ShutterAName, "mE");
   InstallIfMounted(g_ShutterBName, "mF");
   InstallIfMounted(g_ShutterTName, "mT");

   InstallIfMounted(g_ViewportName, "mM");
   InstallIfMounted(g_ConfocalModeName, "mD");
   InstallIfMounted(g_SpinningDisk, "mG");
   InstallIfMounted(g_AccessDoor, "mR");
   
   return DEVICE_OK;
}

int Hub::Shutdown()
{
   int ret = g_hub.ExecuteSequence(*this, *GetCoreCallback(), g_ReleaseSequence);

   if (initialized_)
   {
      initialized_ = false;
   }

   return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(port_);
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Excitation side filter wheel
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

StateDevice::StateDevice(char deviceId, unsigned int numPos, const char *name, const char **posNames): initialized_(false), pos_(0), posNames_(NULL)  
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware" );
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range ? ");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device Not detected");

   //The initialisation, etc commands are pretty standard to all filter wheels in the BDPathway...
   deviceId_ = deviceId;

   //The number of positions varies...
   numPos_ = numPos;
   name_ = name;
   if (posNames != NULL) posNames_ = posNames;
}

StateDevice::~StateDevice()
{
	Shutdown();
}

void StateDevice::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool StateDevice::Busy()
{
	// Change this to the appropriate value for the excitation filter 
   return g_hub.IsDeviceBusy(*this, *GetCoreCallback());
}

int StateDevice::Initialize()
{
   string command = "gd";
   command += deviceId_;

   if (!g_hub.IsComponentMounted(*this, *GetCoreCallback(), (const char *)(command.c_str())))
   {
      return DEVICE_ERR;
   }

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BDPathway state device or filter wheel", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &StateDevice::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);

   if (ret != DEVICE_OK)
      return ret;

   // set allowed states
   SetPropertyLimits(MM::g_Keyword_State, 0, numPos_ - 1);

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   for (unsigned i=0; i<numPos_; i++)
   {
      if (posNames_ == NULL) {
          ostringstream os;
          os << "Position-" << i+1;
          SetPositionLabel(i, os.str().c_str());
      } else {
          SetPositionLabel(i, posNames_[i]);
      }
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int StateDevice::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int StateDevice::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  //int ret = g_hub.GetPosition(*this, *GetCoreCallback(), deviceId_, pos);
      //if (ret != DEVICE_OK)
      //   return ret;
      //pos -= 1;
      pProp->Set((long)pos_);
      //pos_ = pos;
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos > (long)numPos_-1 || pos < 0)
      {
         // restore current position
         int oldPos = pos_;
         //int oldPos;
         //int ret = g_hub.GetPosition(*this, *GetCoreCallback(), deviceId_, oldPos);
         //if (ret != 0)
         //   return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_hub.SetPosition(*this, *GetCoreCallback(), deviceId_, (int)(pos+1));
      if (ret != 0)
         return ret;
	  pProp->Set((long)pos);
      pos_ = (int)pos;
	  ret = UpdateStatus();
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// BDShutter
// ~~~~

BDShutter::BDShutter(char deviceId, const char *name) : initialized_(false), changedTime_(0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected");
   
   //The initialisation, etc commands are pretty standard to all filter wheels in the BDPathway...
   deviceId_ = deviceId;
   name_ = name;

   EnableDelay();
}

BDShutter::~BDShutter()
{
   Shutdown();
}

void BDShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool BDShutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if(interval < MM::MMTime(1000.0 * GetDelayMs()))
      return true;
   else
      return false;
      //return g_hub.IsLampBusy(*this, *GetCoreCallback());
}

int BDShutter::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BDPathway Illumination Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &BDShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   changedTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}

int BDShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int BDShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty(MM::g_Keyword_State, "1");
   else
      return SetProperty(MM::g_Keyword_State, "0");
}

int BDShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int BDShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// BDShutter Action handlers
///////////////////////////////////////////////////////////////////////////////

int BDShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // Our definition: 0=Closed, 1 = Open, BD Pathway definition: 0=undefined, 2=Open, 1=Closed
  if (eAct == MM::BeforeGet)
   {
      int state;
	  int ret = g_hub.GetPosition(*this, *GetCoreCallback(), deviceId_, state);
      if (ret != DEVICE_OK)
         return ret;

      // TODO: when state==0 return error 
      pProp->Set((long)(state-1));
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);

      // apply the value
      int ret = g_hub.SetPosition(*this, *GetCoreCallback(), deviceId_, (int)(state+1));
      if (ret != DEVICE_OK)
         return ret;
     changedTime_ = GetCurrentMMTime();
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// FocusStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

FocusStage::FocusStage() : 
   zStepSize_nm_(0),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(80000.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
}

FocusStage::~FocusStage()
{
   Shutdown();
}

void FocusStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_FocusName);
}

int FocusStage::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_FocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BDPathway focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &FocusStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   //Get the step size
   ret = g_hub.GetFocusStepSizeNm(*this, *GetCoreCallback(), zStepSize_nm_);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int FocusStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool FocusStage::Busy()
{
   return g_hub.IsFocusBusy(*this, *GetCoreCallback());
}

int FocusStage::SetPositionUm(double pos)
{
   return SetProperty(MM::g_Keyword_Position, CDeviceUtils::ConvertToString(pos));
}

int FocusStage::GetPositionUm(double& pos)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Position, buf);
   if (ret != DEVICE_OK)
      return ret;
   pos = atof(buf);
   return DEVICE_OK;
}

int FocusStage::SetPositionSteps(long steps)
{
   if (zStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;
   return SetPositionUm(steps * zStepSize_nm_ / 1000); 
}

int FocusStage::GetPositionSteps(long& steps)
{
   if (zStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;
   double posUm;
   int ret = GetPositionUm(posUm);
   if (ret != DEVICE_OK)
      return ret;

   steps = (long)(posUm / ((double)zStepSize_nm_ / 1000.) + 0.5);
   return DEVICE_OK;
}

int FocusStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int FocusStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
       //the pos is returned in 1/100. microns.
      int pos;
      int ret = g_hub.GetFocusPosition(*this, *GetCoreCallback(), pos);
      if (ret != 0)
         return ret;
      pProp->Set((double)(pos) / 100.);
   }
   else if (eAct == MM::AfterSet)
   {
      if (g_PFSinstalled)
      {
         // check if the perfect focus is active
         // and ignore this command if it is
         //int status = PFS_DISABLED;
        // g_hub.GetPFocusStatus(*this, *GetCoreCallback(), status);
         //if (status == PFS_RUNNING || status == PFS_JUST_PINT)
        return DEVICE_OK;
      }

      double pos;
      pProp->Get(pos);
      int focusPos = (int)(pos*100.);
      int ret = g_hub.SetFocusPosition(*this, *GetCoreCallback(), focusPos);
      if (ret != 0)
         return ret;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// XYStage
//

/**
 * XYStage - two axis stage device.
 * Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
 * system with the X and Y axis going the 'Micro-Manager standard' direction
 * Then, there is the BDPathway native system.  All functions using 'steps' use the BDPathway system
 * All functions using Um use the Micro-Manager coordinate system
 * Origin and limits are absolute in the stage coordinates (-556352 to 600000, -335459 to 335459).
 */
XYStage::XYStage() :
   xStepSize_nm_(0),
   yStepSize_nm_(0),
   initialized_(false), 
   originX_(-550000),
   originY_(-330000),
   limitXmin_(-550000),
   limitYmin_(-330000),
   limitXmax_(550000),
   limitYmax_(330000)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "Not connected with the hardware");
}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_XYStageName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BDPathway XY stage adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   //Get the step size
   ret = g_hub.GetXYStepSizeNm(*this, *GetCoreCallback(), xStepSize_nm_, yStepSize_nm_);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool XYStage::Busy()
{
   return g_hub.IsFocusBusy(*this, *GetCoreCallback());
}

//Position in our coordinate system
//is relative to origin
int XYStage::GetPositionSteps(long& x, long& y)
{
   int ret = g_hub.GetXYPosition(*this, *GetCoreCallback(), x, y);
   if (ret != DEVICE_OK)
      return ret;

   //Account for origin...
   x -= originX_;
   y -= originY_;

   return DEVICE_OK; // success!
}

int XYStage::GetPositionUm(double& x, double& y)
{
   if (xStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;

   long xStep,yStep;
   int ret = GetPositionSteps(xStep,yStep);
   if (ret != DEVICE_OK)
      return ret;

   x = xStep *xStepSize_nm_ / 1000.;
   y = yStep *yStepSize_nm_ / 1000.;

   return DEVICE_OK; // success!
}

int XYStage::SetPositionSteps(long x, long y)
{
   //Account for origin...
   x += originX_;
   y += originY_;

   if (x < limitXmin_) x = limitXmin_; else if (x > limitXmax_) x = limitXmax_;
   if (y < limitYmin_) y = limitYmin_; else if (y > limitYmax_) y = limitYmax_;

   int ret = g_hub.SetXYPosition(*this, *GetCoreCallback(), x, y);
   if (ret != DEVICE_OK)
      return ret;

   if (g_hub.Is855())
   {
      //Check if the Transmitted light is On. That probably means we want the head
      //to follow to the new position. TODO Have a flag for this behaviour?
      int state;
      ret = g_hub.GetPosition(*this, *GetCoreCallback(), 'T', state);
      if (ret != DEVICE_OK)
         return ret;

      if (state == 2) {
         //Turn it off and on again
         ret = g_hub.SetPosition(*this, *GetCoreCallback(), 'T', 1);
         ret = g_hub.SetPosition(*this, *GetCoreCallback(), 'T', 2);
      }
   }
}
  
int XYStage::SetPositionUm(double x, double y)
{
   if (xStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;

   long xStep = (long)(x * 1000 / xStepSize_nm_);
   long yStep = (long)(y * 1000 / yStepSize_nm_);

   return SetPositionSteps(xStep,yStep);
}

int XYStage::SetRelativePositionSteps(long x, long y)
{
   long xStep, yStep;

   //Get current position...
   int ret = GetPositionSteps(xStep, yStep);
   if (ret != DEVICE_OK)
      return ret;

   x += xStep;
   y += yStep;

   return SetPositionSteps(x,y);
}

int XYStage::SetRelativePositionUm(double x, double y)
{
   if (xStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;

   long xStep = (long)(x * 1000 / xStepSize_nm_);
   long yStep = (long)(y * 1000 / yStepSize_nm_);

   return SetRelativePositionSteps(xStep,yStep);
}

/**
 * Defines the coordinates of the current position in our
 * coordinate system by changing the position of the origin.
 * Get the current (stage-native) XY position and subtract
 * the desired current position coordinates.
 * This is going to be the origin in our coordinate system
 */
int XYStage::SetAdapterOriginUm(double x, double y)
{
   int ret = DEVICE_OK;

   if (xStepSize_nm_ == 0)
      return DEVICE_UNSUPPORTED_COMMAND;

   long desiredX, desiredY, hubX, hubY;

   desiredX = (long)(x * 1000 / xStepSize_nm_);
   desiredY = (long)(y * 1000 / yStepSize_nm_);

   //This is the current position read from the hub (so current origin not taken into account)
   ret = g_hub.GetXYPosition(*this, *GetCoreCallback(), hubX, hubY);

   //the new origin is calculated as:
   originX_ = hubX - desiredX;
   originY_ = hubY - desiredY;

   ostringstream os;
   os << "Origin now set to: " << originX_ << "," <<originY_;
   LogMessage(os.str().c_str(), true);

   return ret;
}

/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 */
int XYStage::SetOrigin()
{
   return SetAdapterOriginUm(0., 0.);
}

/**
 * On the BDPathway, (0,0) is in the centre of the stage.
 * Let's move to the upper left corner and define whatever read position as the new home
 * On ours, we need to move to at least tX-556089 tY-334697
 */
int XYStage::Home()
{
    //these are steps on the BDPathway, read directly from the hardware
   long x, y;

   //A sufficiently large jump to the upper left corner
   int ret = g_hub.SetXYPosition(*this, *GetCoreCallback(),-580000, -360000 );
   if (ret != DEVICE_OK)
      return ret;

   ret = g_hub.GetXYPosition(*this, *GetCoreCallback(), x, y);
   if (ret != DEVICE_OK)
      return ret;

   //Absolute limits of the stage...
   limitXmin_= x;
   limitYmin_= y;

   //This is for our BDPathway.
   //Should get the job done, but may need tweaking...
   //Roughly 100000 steps (1 cm off?)
   originX_ = x - 103063;
   originY_ = y - 96122;

   //A sufficiently large jump to the upper left corner
   ret = g_hub.SetXYPosition(*this, *GetCoreCallback(),580000, 360000 );
   if (ret != DEVICE_OK)
      return ret;

   ret = g_hub.GetXYPosition(*this, *GetCoreCallback(), x, y);
   if (ret != DEVICE_OK)
      return ret;

   limitXmax_ = x;
   limitYmax_ = y;

   //Now jump back to the centre using the newly set origin and limits...
   ret = SetPositionSteps((limitXmax_-limitXmin_)/2, (limitYmax_-limitYmin_)/2);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "Stage Homed. Xrange:" << limitXmin_ << "," << limitXmax_ << " Yrange:" << limitYmin_ << "," << limitYmax_;
   LogMessage(os.str().c_str(), true);

   return DEVICE_OK;
}

int XYStage::Stop()
{
   return DEVICE_OK;
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   xMin = limitXmin_ * xStepSize_nm_ / 1000;
   yMin = limitYmin_ * yStepSize_nm_ / 1000;
   xMax = limitXmax_ * xStepSize_nm_ / 1000;
   yMax = limitYmax_ * yStepSize_nm_ / 1000;
   return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   xMin = limitXmin_;
   yMin = limitYmin_;
   xMax = limitXmax_;
   yMax = limitYmax_;
   return DEVICE_OK;
}
