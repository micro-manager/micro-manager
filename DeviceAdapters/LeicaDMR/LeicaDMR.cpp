///////////////////////////////////////////////////////////////////////////////
// FILE:          LeicaDMR.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   LeicaDMR controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                   
//
// AUTHOR:        Nico Stuurman (based on code by Nenad Amodaj), nico@cmp.ucsf.edu, April 2007
//

#ifdef WIN32
   //#include <windows.h>
   #define snprintf _snprintf 
#endif

#include "LeicaDMR.h"
#include "LeicaDMRHub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"

using namespace std;

// Device strings
const char* g_LeicaDMRHub = "Leica DM microscope";
const char* g_LeicaDMRRLModule = "Reflected Light Module";
const char* g_LeicaDMRLamp = "Halogen Lamp";
const char* g_LeicaDMRShutter = "Shutter";
const char* g_LeicaDMRZDrive = "Z Drive";
const char* g_LeicaDMRObjNosepiece = "Objective Nosepiece";
const char* g_LeicaDMRApertureDiaphragm = "Aperture Diaphragm";
const char* g_LeicaDMRFieldDiaphragm = "Field Diaphragm";

// Property strings
const char* g_Threshold = "Threshold";
const char* g_Set = "Set";
const char* g_Update = "Update";

const char* g_OperatingMode = "Operating Mode";
const char* g_ImmMode = "Immersion";
const char* g_DryMode = "Dry";

const char* g_RotationMode = "Rotation Mode";
const char* g_LowerMode = "Lower";
const char* g_NoLowerMode = "Do not lower";

const char* g_Break = "Interrupt";
const char* g_On = "Now";
const char* g_Off = " ";

const char* g_Condensor = "Condensor Top";
const char* g_In = "In";
const char* g_Out = "Out";
const char* g_Undefined = "Undefined";

// global hub object, very important!
LeicaDMRHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_LeicaDMRHub,"LeicaDMR (DM RXA, DM RME, RD RBE, DM RXA, DM RA, DM IRBE) Controller");
   AddAvailableDeviceName(g_LeicaDMRRLModule,"Reflected Light LModule");   
   AddAvailableDeviceName(g_LeicaDMRLamp,"Halogen Lamp"); 
   AddAvailableDeviceName(g_LeicaDMRShutter, "Reflected Light Shutter"); 
   AddAvailableDeviceName(g_LeicaDMRZDrive, "Z Drive"); 
   AddAvailableDeviceName(g_LeicaDMRObjNosepiece, "Objective Nosepiece"); 
   AddAvailableDeviceName(g_LeicaDMRApertureDiaphragm, "Aperture Diaphragm");
   AddAvailableDeviceName(g_LeicaDMRFieldDiaphragm, "Field Diaphragm");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_LeicaDMRHub) == 0) {
      return new Hub();
   } else if (strcmp(deviceName, g_LeicaDMRRLModule) == 0 ) {
      return new RLModule();
   } else if (strcmp(deviceName, g_LeicaDMRLamp) == 0 ) {
      return new Lamp();
   } else if (strcmp(deviceName, g_LeicaDMRShutter) == 0 ) {
      return new RLShutter;
   } else if (strcmp(deviceName, g_LeicaDMRZDrive) == 0 ) {
      return new ZStage;
   } else if (strcmp(deviceName, g_LeicaDMRObjNosepiece) == 0 ) {
      return new ObjNosepiece;
   } else if (strcmp(deviceName, g_LeicaDMRApertureDiaphragm) == 0 ) {
      return new ApertureDiaphragm;
   } else if (strcmp(deviceName, g_LeicaDMRFieldDiaphragm) == 0 ) {
      return new FieldDiaphragm;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   port_("Undefined")
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_COMMAND_CANNOT_EXECUTE, "Command cannot be executed");
   SetErrorText(ERR_NO_ANSWER, "No answer received.  Is the Leica microscope connected to the correct serial port and switched on?");
   SetErrorText(ERR_NOT_CONNECTED, "No answer received.  Is the Leica microscope connected to the correct serial port and switched on?");

   // create pre-initialization properties
   // ------------------------------------

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
   CDeviceUtils::CopyLimitedString(name, g_LeicaDMRHub);
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_LeicaDMRHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version
   std::string version = g_hub.Version();
   ret = CreateProperty("Firmware version", version.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Microscope
   std::string microscope = g_hub.Microscope();
   ret = CreateProperty("Microscope", microscope.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // We might also get the available pieces of hardware at this point

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   
   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_) {
      initialized_ = false;
      g_hub.DeInitialize();
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         //return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LeicaDMR RLModule
///////////////////////////////////////////////////////////////////////////////
RLModule::RLModule () :
   initialized_ (false),
   name_ (g_LeicaDMRRLModule),
   pos_ (0),
   numPos_ (4)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   SetErrorText(ERR_INVALID_POSITION, "Reflector Module reports an invalid position.  Is it clicked into position correctly?");

   //
   // create pre-initialization properties
   // ------------------------------------

}

RLModule::~RLModule ()
{
   Shutdown();
}

void RLModule::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int RLModule::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }


   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR RLModule", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &RLModule::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // Gate Closed Position
   ret = CreateProperty(MM::g_Keyword_Closed_Position,"", MM::String, false);

   // Get the number of filters in this RLModule
   ret = g_hub.GetRLModuleNumberOfPositions(*this, *GetCoreCallback(), numPos_);
   if (ret != DEVICE_OK) 
      return ret; 
   char pos[3];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(pos, "%d", i);
      AddAllowedValue(MM::g_Keyword_State, pos);
      AddAllowedValue(MM::g_Keyword_Closed_Position, pos);
   }

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret;

   // create default positions and labels
   char state[8];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(state, "C%d", i + 1);
      SetPositionLabel(i,state);
   }

   GetGateOpen(open_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool RLModule::Busy()
{
   return false;
}

int RLModule::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////


int RLModule::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_hub.GetRLModulePosition(*this, *GetCoreCallback(), pos);
	  if (ret != DEVICE_OK)
		  return ret;
      if (pos == 0)
         return ERR_INVALID_POSITION;
      pProp->Set((long) pos - 1);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      int ret;
      bool gateOpen;
      GetGateOpen(gateOpen);
      pProp->Get(pos);
      // sanity check
      if (pos < 0)
         pos = 0;
      if (pos >= numPos_)
         pos = numPos_ - 1;
      if ((pos == pos_) && (open_ == gateOpen))
         return DEVICE_OK;
      if (gateOpen)
         ret = g_hub.SetRLModulePosition(*this, *GetCoreCallback(), pos + 1 );
      else {
         char closedPos[MM::MaxStrLength];
         GetProperty(MM::g_Keyword_Closed_Position, closedPos);
         int gateClosedPosition = atoi(closedPos);
         ret = g_hub.SetRLModulePosition(*this, *GetCoreCallback(), gateClosedPosition + 1);
      }
      if (ret != DEVICE_OK)
         return ret;

      pos_ = pos;
      open_ = gateOpen;
      pProp->Set(pos_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Lamp
///////////////////////////////////////////////////////////////////////////////
Lamp::Lamp () :
   initialized_ (false),
   name_ (g_LeicaDMRLamp),
   open_(false),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No Lamp found in this microscope");
   SetErrorText(ERR_PORT_NOT_SET, "No serial port found.  Did you include the Leica DM microscope and set its serial port property?");

   // Todo: Add custom messages

   // EnableDelay();
}

Lamp::~Lamp ()
{
   Shutdown();
}

void Lamp::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Lamp::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.LampPresent())
      return ERR_DEVICE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR Lamp", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

  // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 5s into the past
   changedTime_ = GetCurrentMMTime() - 5000000; 
   
   // Check current intensity of lamp
   ret = g_hub.GetLampIntensity(*this, *GetCoreCallback(), intensity_);
   if (DEVICE_OK != ret)
      return ret;
   // The following seemed a good idea, but ends up being annoying
   /*
   if (intensity_ > 0)
      open_ = true;
   */

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Lamp::OnState);
   if (intensity_ > 0)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // Intensity
   pAct = new CPropertyAction(this, &Lamp::OnIntensity);
   CreateProperty("Intensity", "0", MM::Integer, false, pAct);
   SetPropertyLimits("Intensity", 0, 1275);

   EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Lamp::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;

   return false;
}

int Lamp::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Lamp::SetOpen(bool open)
{
   if (open) {
      int ret = g_hub.SetLampIntensity(*this, *GetCoreCallback(), intensity_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = true;
   } else {
      int ret = g_hub.SetLampIntensity(*this, *GetCoreCallback(), 0);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
   }
   changedTime_ = GetCurrentMMTime();

   return DEVICE_OK;
}

int Lamp::GetOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}

int Lamp::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Lamp::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetOpen(open);
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
         ret = this->SetOpen(true);
      } else {
         ret = this->SetOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int Lamp::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (open_) {
         int ret = g_hub.GetLampIntensity(*this, *GetCoreCallback(), intensity_);
         if (ret != DEVICE_OK)
            return ret;
      } else {
         // shutter is closed.  Return the cached value
         // TODO: check if user increased brightness
      }
      pProp->Set((long)intensity_);
   } else if (eAct == MM::AfterSet) {
      long intensity;
      pProp->Get(intensity);
      intensity_ = (int) intensity;
      if (open_) {
         int ret = g_hub.SetLampIntensity(*this, *GetCoreCallback(), intensity_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Reflected Light Shutter 
///////////////////////////////////////////////////////////////////////////////
RLShutter::RLShutter () :
   initialized_ (false),
   name_ (g_LeicaDMRShutter),
   open_(false),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No Reflected light shutter found in this microscope");
   SetErrorText(ERR_PORT_NOT_SET, "No serial port found.  Did you include the Leica DM microscope and set its serial port property?");

}

RLShutter::~RLShutter ()
{
   Shutdown();
}

void RLShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int RLShutter::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.RLModulePresent())
      return ERR_DEVICE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR Reflected Light Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

  // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 5s into the past
   changedTime_ = GetCurrentMMTime() - 5000000; 
   
   // State
   CPropertyAction* pAct = new CPropertyAction (this, &RLShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool RLShutter::Busy()
{
   // TODO: determine whether we need to use delay
   /*
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   */
   return false;
}

int RLShutter::Shutdown()
{
   if (initialized_) {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int RLShutter::SetOpen(bool open)
{
   changedTime_ = GetCurrentMMTime();
   return g_hub.SetRLShutter(*this, *GetCoreCallback(), open);
}

int RLShutter::GetOpen(bool &open)
{
   return g_hub.GetRLShutter(*this, *GetCoreCallback(), open);
}

int RLShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int RLShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetOpen(open);
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
         ret = this->SetOpen(true);
      } else {
         ret = this->SetOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// LeicaDMR ZDrive 
///////////////////////////////////////////////////////////////////////////////
ZStage::ZStage () :
   stepSize_um_(0.1),
   initialized_ (false),
   name_ (g_LeicaDMRZDrive)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No Z-Drive found in this microscope");
   SetErrorText(ERR_PORT_NOT_SET, "No serial port found.  Did you include the Leica DM microscope and set its serial port property?");
}

ZStage::~ZStage ()
{
   Shutdown();
}

int ZStage::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

void ZStage::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ZStage::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.ZDrivePresent())
      return ERR_DEVICE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR Z Drive", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

  // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 5s into the past
   changedTime_ = GetCurrentMMTime() - 5000000; 
   
   // Position
   // There are two reference frames.  An absolute reference frame (implemeted here)
   // and a relative reference frame, implemented with a upper and lower lower limit
   // The display on the DMRXA shows the difference with the upper limit
   lowerLimit_ = 0.0;
   if (g_hub.Microscope() == "DMRXA" || g_hub.Microscope() == "DMRA") {
      upperLimit_ = 25000.0;
   } else if (g_hub.Microscope() == "DMIRBE") {
      upperLimit_ = 7000.0;
   }

   // To implement the relative reference frame we need the upper threshold
   g_hub.GetZUpperThreshold(*this, *GetCoreCallback(), upperThreshold_);
   // Allow user to update threshold
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnThreshold);
   ret = CreateProperty(g_Threshold, g_Set, MM::String, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue(g_Threshold, g_Set);
   AddAllowedValue(g_Threshold, g_Update);


   // Do not implement the position property as it can lead to trouble
   /*
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPosition);
   ret = CreateProperty("Position", "0", MM::Float, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits("Position", lowerLimit_, upperLimit_);
   */

   // Emergency stop
   pAct = new CPropertyAction (this, &ZStage::OnStop);
   ret = CreateProperty("Emergency Stop", "Off", MM::String, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   AddAllowedValue("Emergency Stop", "Off");
   AddAllowedValue("Emergency Stop", "Apply Now!");

   return DEVICE_OK;
}

bool ZStage::Busy()
{
   return false;
}

int ZStage::SetPositionUm(double position)
{
   long positionSteps = (long) (position / stepSize_um_);
   if (upperThreshold_ != -1)
      positionSteps = upperThreshold_ + positionSteps;

   return SetPositionSteps(positionSteps);
}

int ZStage::SetRelativePositionUm(double position)
{
   long positionSteps = (long) (position / stepSize_um_);

   return SetRelativePositionSteps(positionSteps);
}

int ZStage::GetPositionUm(double& position)
{
   long positionSteps;
   int ret = GetPositionSteps(positionSteps);
   if (ret != DEVICE_OK)
      return ret;
   if (upperThreshold_ != -1)
      positionSteps =  positionSteps - upperThreshold_;
   position = (double) positionSteps * stepSize_um_;

   return DEVICE_OK;
}

int ZStage::SetPositionSteps(long position)
{
   return g_hub.SetZAbs(*this, *GetCoreCallback(), position);
}

int ZStage::SetRelativePositionSteps(long position)
{
   return g_hub.SetZRel(*this, *GetCoreCallback(), position);
}

int ZStage::GetPositionSteps(long& position)
{
   return g_hub.GetZAbs(*this, *GetCoreCallback(), position);
}

int ZStage::SetOrigin() 
{
   return DEVICE_OK;
}

/*
 * Assume that the parameter is in um/sec!
 */
int ZStage::Move(double speed)
{
   int speedNumber;
   double  minSpeed, maxSpeed;
   if (g_hub.Microscope() == "DMRXA" || g_hub.Microscope() == "DMRA") {
      minSpeed = 18.4;
      maxSpeed = 4700;
   } else {
      minSpeed = 4.6;
      maxSpeed = 1175;
   }
   speedNumber = (int) (speed /maxSpeed * 255);
   if (speedNumber > 255)
      speedNumber = 255;
   if (speedNumber < -255)
      speedNumber = -255;

   return g_hub.MoveZConst(*this, *GetCoreCallback(), speedNumber);
}

int ZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   } else if (eAct == MM::AfterSet) {
      double pos;
      pProp->Get(pos);
      int ret = SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int ZStage::OnStop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set("Off");
   } else if (eAct == MM::AfterSet) {
      std::string value;
      pProp->Get(value);
      if (value == "Apply Now!") {
         g_hub.StopZ(*this, *GetCoreCallback());
      }
   }
   return DEVICE_OK;
}

int ZStage::OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_Set);
   } else if (eAct == MM::AfterSet) {
      std::string value;
      pProp->Get(value);
      if (value == g_Update) {
         g_hub.GetZUpperThreshold(*this, *GetCoreCallback(), upperThreshold_);
      }
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Objective Nosepiece
///////////////////////////////////////////////////////////////////////////////
ObjNosepiece::ObjNosepiece () :
   initialized_ (false),
   name_ (g_LeicaDMRObjNosepiece),
   pos_ (0),
   numPos_ (6)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   SetErrorText(ERR_INVALID_POSITION, "Objective nosepiece reports an invalid position.  Is it clicked into position correctly?");
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No objective nosepiece in this microscope.");
   SetErrorText(ERR_OBJECTIVE_SET_FAILED, "Failed changing objectives.  Is the Immersion mode appropriate for the new objective?");


   //
   // create pre-initialization properties
   // ------------------------------------

}

ObjNosepiece::~ObjNosepiece ()
{
   Shutdown();
}

void ObjNosepiece::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ObjNosepiece::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.ObjNosepiecePresent())
      return ERR_DEVICE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LeicaDMR Objective Nosepiece", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &ObjNosepiece::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // Get the number of Objectives
   ret = g_hub.GetObjNosepieceNumberOfPositions(*this, *GetCoreCallback(), numPos_);
   if (ret != DEVICE_OK) 
      return ret; 
   char pos[3];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(pos, "%d", i);
      AddAllowedValue(MM::g_Keyword_State, pos);
   }

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret;

   // create default positions and labels
   char state[8];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(state, "Obj-%d", i + 1);
      SetPositionLabel(i,state);
   }

   pAct = new CPropertyAction(this, &ObjNosepiece::OnImmersionMode);
   ret = CreateProperty(g_OperatingMode, g_DryMode, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_OperatingMode, g_ImmMode);
   AddAllowedValue(g_OperatingMode, g_DryMode);

   pAct = new CPropertyAction(this, &ObjNosepiece::OnRotationMode);
   ret = CreateProperty(g_RotationMode, g_LowerMode, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_RotationMode, g_LowerMode);
   AddAllowedValue(g_RotationMode, g_NoLowerMode);

   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool ObjNosepiece::Busy()
{
   return false;
}

int ObjNosepiece::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////


int ObjNosepiece::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int pos;
      int ret = g_hub.GetObjNosepiecePosition(*this, *GetCoreCallback(), pos);
	   if (ret != DEVICE_OK)
		   return ret;
      if (pos == 0)
         return ERR_INVALID_POSITION;
      pProp->Set((long) pos - 1);
   } else if (eAct == MM::AfterSet) {
      long pos;
      int ret;
      pProp->Get(pos);
      // sanity check
      if (pos < 0)
         pos = 0;
      if (pos >= numPos_)
         pos = numPos_ - 1;
      if (pos == pos_)
         return DEVICE_OK;
      /*
      // lower the stage by 4 cm if so requested (funny that this is not done in the firmware)
      char mode[MM::MaxStrLength];
      GetProperty(g_RotationMode, mode);
      if (strcmp(mode, g_LowerMode) == 0)
         g_hub.SetZRel(*this, *GetCoreCallback(), -40000);
      */
      // Actual nosepiece move
      ret = g_hub.SetObjNosepiecePosition(*this, *GetCoreCallback(), pos + 1 );      
      // return focus if needed
      /*
      if (strcmp(mode, g_LowerMode) == 0)
         g_hub.SetZRel(*this, *GetCoreCallback(), 40000);
      */
      if (ret != DEVICE_OK)
         return ERR_OBJECTIVE_SET_FAILED;

      pos_ = pos;
      pProp->Set(pos_);
   }
   return DEVICE_OK;
}


int ObjNosepiece::OnImmersionMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int mode;
      int ret = g_hub.GetObjNosepieceImmMode(*this, *GetCoreCallback(), mode);
      if (ret != DEVICE_OK)
         return ret;
      if (mode == 0)
         pProp->Set(g_ImmMode);
      else
         pProp->Set(g_DryMode);
   } else if (eAct == MM::AfterSet) {
      std::string mode;
      pProp->Get(mode);
      if (mode == g_ImmMode)
         return g_hub.SetObjNosepieceImmMode(*this, *GetCoreCallback(), 0);
      else
         return g_hub.SetObjNosepieceImmMode(*this, *GetCoreCallback(), 1);
   }

   return DEVICE_OK;
}

int ObjNosepiece::OnRotationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int mode;
      int ret = g_hub.GetObjNosepieceRotationMode(*this, *GetCoreCallback(), mode);
      if (ret != DEVICE_OK)
         return ret;
      if (mode == 0)
         pProp->Set(g_LowerMode);
      else
         pProp->Set(g_NoLowerMode);
   } else if (eAct == MM::AfterSet) {
      std::string mode;
      pProp->Get(mode);
      if (mode == g_LowerMode)
         return g_hub.SetObjNosepieceRotationMode(*this, *GetCoreCallback(), 0);
      else
         return g_hub.SetObjNosepieceRotationMode(*this, *GetCoreCallback(), 1);
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Aperture Diaphragm
///////////////////////////////////////////////////////////////////////////////

ApertureDiaphragm::ApertureDiaphragm() :
   initialized_(false)
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No Aperture Diaphragm found in this microscope");

}

ApertureDiaphragm::~ApertureDiaphragm()
{
   Shutdown();
}

void ApertureDiaphragm::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaDMRApertureDiaphragm);
}

bool ApertureDiaphragm::Busy()
{
   return false;
}

int ApertureDiaphragm::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.ApertureDiaphragmPresent())
      return ERR_DEVICE_NOT_FOUND;

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_LeicaDMRApertureDiaphragm, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Aperture Diaphragm", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   CPropertyAction* pAct = new CPropertyAction (this, &ApertureDiaphragm::OnPosition);
   ret = CreateProperty("Position", "90", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits("Position", 0, 90);

   // Interrupt
   pAct = new CPropertyAction (this, &ApertureDiaphragm::OnBreak);
   ret = CreateProperty(g_Break, g_Off, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Break, g_Off);
   AddAllowedValue(g_Break, g_On);

   initialized_ = true;
   
   return DEVICE_OK;
}

int ApertureDiaphragm::Shutdown()
{
   if (initialized_) {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int ApertureDiaphragm::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int pos;
      int ret = g_hub.GetApertureDiaphragmPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   } else if (eAct == MM::AfterSet) {
      long pos;
      pProp->Get(pos);
      return g_hub.SetApertureDiaphragmPosition(*this, *GetCoreCallback(), (int) pos);
   }

   return DEVICE_OK;
}


int ApertureDiaphragm::OnBreak(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_Off);
   } else if (eAct == MM::AfterSet) {
      std::string value;
      pProp->Get(value);
      if (value == g_On)
         return g_hub.BreakApertureDiaphragm(*this, *GetCoreCallback());
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LeicaDMR Field Diaphragm
///////////////////////////////////////////////////////////////////////////////

FieldDiaphragm::FieldDiaphragm() :
   initialized_(false)
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_DEVICE_NOT_FOUND, "No Field Diaphragm found in this microscope");

}

FieldDiaphragm::~FieldDiaphragm()
{
   Shutdown();
}

void FieldDiaphragm::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaDMRFieldDiaphragm);
}

bool FieldDiaphragm::Busy()
{
   return false;
}

int FieldDiaphragm::Initialize()
{
   int ret;
   if (!g_hub.Initialized()) {
      ret = g_hub.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return ret;
   }

   if (!g_hub.FieldDiaphragmPresent())
      return ERR_DEVICE_NOT_FOUND;

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_LeicaDMRFieldDiaphragm, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Field Diaphragm", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   CPropertyAction* pAct = new CPropertyAction (this, &FieldDiaphragm::OnPosition);
   ret = CreateProperty("Position", "97", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 
   SetPropertyLimits("Position", 0, 97);

   // Interrupt
   pAct = new CPropertyAction (this, &FieldDiaphragm::OnBreak);
   ret = CreateProperty(g_Break, g_Off, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Break, g_Off);
   AddAllowedValue(g_Break, g_On);

   // Condensor top in/out
   pAct = new CPropertyAction (this, &FieldDiaphragm::OnCondensor);
   ret = CreateProperty(g_Condensor, g_In, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_Condensor, g_In);
   AddAllowedValue(g_Condensor, g_Out);
   AddAllowedValue(g_Condensor, g_Undefined);

   initialized_ = true;
   
   return DEVICE_OK;
}

int FieldDiaphragm::Shutdown()
{
   if (initialized_) {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int FieldDiaphragm::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int pos;
      int ret = g_hub.GetFieldDiaphragmPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   } else if (eAct == MM::AfterSet) {
      long pos;
      pProp->Get(pos);
      return g_hub.SetFieldDiaphragmPosition(*this, *GetCoreCallback(), (int) pos);
   }

   return DEVICE_OK;
}


int FieldDiaphragm::OnBreak(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_Off);
   } else if (eAct == MM::AfterSet) {
      std::string value;
      pProp->Get(value);
      if (value == g_On)
         return g_hub.BreakFieldDiaphragm(*this, *GetCoreCallback());
   }

   return DEVICE_OK;
}

int FieldDiaphragm::OnCondensor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int pos;
      int ret = g_hub.GetCondensorPosition(*this, *GetCoreCallback(), pos);
      if (ret != DEVICE_OK)
         return ret;
      switch (pos) {
         case 0: pProp->Set(g_Out); break;
         case 1: pProp->Set(g_In); break;
         default: pProp->Set(g_Undefined); break;
      }
   } else if (eAct == MM::AfterSet) {
      std::string value;
      pProp->Get(value);
      if (value == g_Out)
         return g_hub.SetCondensorPosition(*this, *GetCoreCallback(), 0);
      else if (value == g_Out)
         return g_hub.SetCondensorPosition(*this, *GetCoreCallback(), 1);
   }

   return DEVICE_OK;
}
