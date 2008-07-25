///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMI.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
//
// COPYRIGHT:     100xImaging, Inc. 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
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
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  



#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#else
#include <netinet/in.h>
#endif

#include "LeicaDMI.h"
#include "LeicaDMIModel.h"
#include "LeicaDMIScopeInterface.h"
#include "LeicaDMICodes.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

// TODO: linux entry code
// Note that this only works with gcc (which we should be testing for)
#ifdef __GNUC__
void __attribute__ ((constructor)) my_init(void)
{
}
void __attribute__ ((destructor)) my_fini(void)
{
}
#endif

// windows dll entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/,                                
                          DWORD  ul_reason_for_call,                         
                          LPVOID /*lpReserved*/                              
                   )                                                         
   {                                                                         
      switch (ul_reason_for_call)                                            
      {                                                                      
      case DLL_PROCESS_ATTACH:                                               
      break;                                                                 
      case DLL_THREAD_ATTACH:    
      break;                                                                 
      case DLL_THREAD_DETACH:
      break;
      case DLL_PROCESS_DETACH:
      break;
      }
       return TRUE;
   }
#endif

using namespace std;

LeicaScopeInterface g_ScopeInterface;
LeicaDMIModel g_ScopeModel;

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Leica Devices
const char* g_LeicaDeviceName = "Scope";
const char* g_LeicaReflector = "IL-Turret";
const char* g_LeicaNosePiece = "ObjectiveTurret";
const char* g_LeicaFieldDiaphragm = "FieldDiaphragm";
const char* g_LeicaApertureDiaphragm = "ApertureDiaphragm";
const char* g_LeicaFocusAxis = "FocusAxis";
const char* g_LeicaTubeLens = "TubeLens";
const char* g_LeicaTubeLensShutter = "TubeLensShutter";
const char* g_LeicaSidePort = "SidePort";
const char* g_LeicaIncidentLightShutter = "IL-Shutter";
const char* g_LeicaTransmittedLightShutter = "TL-Shutter";
const char* g_LeicaHalogenLightSwitch = "HalogenLightSwitch";
const char* g_LeicaRLFLAttenuator = "RL-FLAttenuator";
const char* g_LeicaCondenserContrast = "CondenserContrast";
const char* g_LeicaCondenserAperture = "CondenserAperture";
const char* g_LeicaXYStage = "XYStage";
const char* g_LeicaHBOLamp = "HBOLamp";
const char* g_LeicaHalogenLamp = "HalogenLamp";
const char* g_LeicaLSMPort = "LSMPort";
const char* g_LeicaBasePort = "BasePort";
const char* g_LeicaUniblitz = "Uniblitz";
const char* g_LeicaFilterWheel = "FilterWheel";


///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_LeicaDeviceName,"Leica DMI microscope controlled through serial interface");
   AddAvailableDeviceName(g_LeicaTransmittedLightShutter,"Transmitted Light Shutter"); 
   AddAvailableDeviceName(g_LeicaIncidentLightShutter,"Incident Light Shutter"); 
   AddAvailableDeviceName(g_LeicaReflector,"Reflector Turret (dichroics)"); 
   AddAvailableDeviceName(g_LeicaNosePiece,"Objective Turret");
   /*
   AddAvailableDeviceName(g_LeicaFocusAxis,"Z-drive");
   AddAvailableDeviceName(g_LeicaXYStage,"XYStage");
   AddAvailableDeviceName(g_LeicaFieldDiaphragm,"Field Diaphragm (fluorescence)");
   AddAvailableDeviceName(g_LeicaApertureDiaphragm,"Aperture Diaphragm (fluorescence)");
   AddAvailableDeviceName(g_LeicaTubeLens,"Tube Lens (optovar)");
   AddAvailableDeviceName(g_LeicaTubeLensShutter,"Tube Lens Shutter");
   AddAvailableDeviceName(g_LeicaSidePort,"Side Port");
   AddAvailableDeviceName(g_LeicaReflectedLightShutter,"Reflected Light Shutter"); 
   AddAvailableDeviceName(g_LeicaRLFLAttenuator,"Reflected (fluorescence) light attenuator");
   AddAvailableDeviceName(g_LeicaCondenserContrast,"Condenser Contrast");
   AddAvailableDeviceName(g_LeicaCondenserAperture,"Condenser Aperture");
   AddAvailableDeviceName(g_LeicaHBOLamp,"HBO Lamp");
   AddAvailableDeviceName(g_LeicaHalogenLamp,"Halogen Lamp"); 
   AddAvailableDeviceName(g_LeicaLSMPort,"LSM Port (rearPort)"); 
   AddAvailableDeviceName(g_LeicaBasePort,"Base Port switcher"); 
   AddAvailableDeviceName(g_LeicaUniblitz,"Uniblitz Shutter"); 
   AddAvailableDeviceName(g_LeicaFilterWheel,"Filter Wheel"); 
   */
}
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_LeicaDeviceName) == 0)
        return new LeicaScope();
   else if (strcmp(deviceName, g_LeicaTransmittedLightShutter) == 0)
        return new  TLShutter();
   else if (strcmp(deviceName, g_LeicaIncidentLightShutter) == 0)
        return new  ILShutter();
   else if (strcmp(deviceName, g_LeicaReflector) == 0)
        return new ILTurret();
   else if (strcmp(deviceName, g_LeicaNosePiece) == 0)
        return new ObjectiveTurret();
   /*
   else if (strcmp(deviceName, g_LeicaFieldDiaphragm) == 0)
        return new Servo(g_FieldDiaphragmServo, g_LeicaFieldDiaphragm, "Field Diaphragm");
   else if (strcmp(deviceName, g_LeicaApertureDiaphragm) == 0)
        return new Servo(g_ApertureDiaphragmServo, g_LeicaApertureDiaphragm, "Aperture Diaphragm");
   else if (strcmp(deviceName, g_LeicaFocusAxis) == 0)
        return new Axis(g_FocusAxis, g_LeicaFocusAxis, "Z-drive");
   else if (strcmp(deviceName, g_LeicaXYStage) == 0)
	   return new XYStage();
   else if (strcmp(deviceName, g_LeicaTubeLens) == 0)
        return new TubeLensTurret(g_TubeLensChanger, g_LeicaTubeLens, "Tube Lens (optoavar)");
   else if (strcmp(deviceName, g_LeicaTubeLensShutter) == 0)
        return new Shutter(g_TubeShutter, g_LeicaTubeLensShutter, "Tube Lens Shutter");
   else if (strcmp(deviceName, g_LeicaSidePort) == 0)
        return new SidePortTurret(g_SidePortChanger, g_LeicaSidePort, "Side Port");
   else if (strcmp(deviceName, g_LeicaReflectedLightShutter) == 0)
        return new  Shutter(g_ReflectedLightShutter, g_LeicaReflectedLightShutter, "Leica Reflected Light Shutter");
   else if (strcmp(deviceName, g_LeicaRLFLAttenuator) == 0)
        return new Turret(g_RLFLAttenuatorChanger, g_LeicaRLFLAttenuator, "Attenuator (reflected light)");
   else if (strcmp(deviceName, g_LeicaCondenserContrast) == 0)
        return new CondenserTurret(g_CondenserContrastChanger, g_LeicaCondenserContrast, "Condenser Contrast");
   else if (strcmp(deviceName, g_LeicaCondenserAperture) == 0)
        return new Servo(g_CondenserApertureServo, g_LeicaCondenserAperture, "Condenser Aperture");
   else if (strcmp(deviceName, g_LeicaHBOLamp) == 0)
        return new Servo(g_HBOLampServo, g_LeicaHBOLamp, "HBO Lamp intensity");
   else if (strcmp(deviceName, g_LeicaHalogenLamp) == 0)
        return new Servo(g_HalogenLampServo, g_LeicaHalogenLamp, "Halogen Lamp intensity");
   else if (strcmp(deviceName, g_LeicaLSMPort) == 0)
        return new Turret(g_LSMPortChanger, g_LeicaLSMPort, "LSM Port (rear port)");
   else if (strcmp(deviceName, g_LeicaBasePort) == 0)
        return new Turret(g_BasePortChanger, g_LeicaBasePort, "Base Port");
   else if (strcmp(deviceName, g_LeicaUniblitz) == 0)
        return new Shutter(g_UniblitzShutter, g_LeicaUniblitz, "Uniblitz Shutter");
   else if (strcmp(deviceName, g_LeicaFilterWheel) == 0)
        return new Turret(g_FilterWheelChanger, g_LeicaFilterWheel, "Filter Wheel");
        */

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}



///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// LeicaScope
//
LeicaScope::LeicaScope() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(250)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_ANSWER_TIMEOUT, "The Leica microscope does not answer.  Is it switched on and connected to this computer?");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_LeicaDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Leica microscope CAN bus adapter", MM::String, true);
   
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &LeicaScope::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // AnswerTimeOut                                                                  
   pAct = new CPropertyAction (this, &LeicaScope::OnAnswerTimeOut);
   CreateProperty("AnswerTimeOut", "250", MM::Float, false, pAct, true);
}

LeicaScope::~LeicaScope() 
{
   printf ("In LeicaScope destructor\n");
   if (g_ScopeInterface.monitoringThread_ != 0) {
      g_ScopeInterface.monitoringThread_->Stop();
   printf ("Stopping monitoringThread\n");
      g_ScopeInterface.monitoringThread_->wait();
   printf ("Thread stopped\n");
      delete g_ScopeInterface.monitoringThread_;
      g_ScopeInterface.monitoringThread_ = 0;
   }
   g_ScopeInterface.initialized_ = false;

   Shutdown();
}

int LeicaScope::Initialize() 
{
   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // Version
   string model;
   ret = g_ScopeModel.GetStandType(model);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Microscope Stand", model.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
  
   // Firmware
   string version;
   ret = g_ScopeModel.GetStandVersion(version);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Microscope Firmware", version.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
  
   // Method
   CPropertyAction* pAct = new CPropertyAction(this, &LeicaScope::OnMethod);
   CreateProperty("Method", "", MM::String, false, pAct);
   for (int i=0; i<16; i++) {
      if (g_ScopeModel.IsMethodAvailable(i)) {
         AddAllowedValue("Method", g_ScopeModel.GetMethod(i).c_str());
      }
   }

   ret = UpdateStatus();
   if (DEVICE_OK != ret)
      return ret;

   initialized_ = true;
   return 0;
}

int LeicaScope::Shutdown() 
{
   return 0;
}

void LeicaScope::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaDeviceName);
}

bool LeicaScope::Busy() 
{
   bool busy;
   g_ScopeModel.method_.GetBusy(busy);
   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int LeicaScope::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_ScopeInterface.port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // revert
         pProp->Set(g_ScopeInterface.port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      // take this port.  TODO: should we check if this is a valid port?
      pProp->Get(g_ScopeInterface.port_);
      // Provide a pointer to the scope model
      g_ScopeInterface.scopeModel_ = &g_ScopeModel;
      // set flags indicating we have a port
      g_ScopeInterface.portInitialized_ = true;
      initialized_ = true;
   }

   return DEVICE_OK;
}

int LeicaScope::OnMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int position;
      int ret = g_ScopeModel.method_.GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      std::string method = g_ScopeModel.GetMethod(position);
      pProp->Set(method.c_str());
   } else if (eAct == MM::AfterSet) {
      std::string method;
      pProp->Get(method);
      int position = g_ScopeModel.GetMethodID(method);
      if (position > 0) {
         g_ScopeInterface.SetMethod(*this, *GetCoreCallback(), position);
      }
   }

   return DEVICE_OK;
}

int LeicaScope::OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_ScopeInterface.GetTimeOutTime().getMsec());
   } else if (eAct == MM::AfterSet) {
      double tmp;
      pProp->Get(tmp);
      g_ScopeInterface.SetTimeOutTime(MM::MMTime(tmp * 1000.0));
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Leica Incident light Shutter
///////////////////////////////////////////////////////////////////////////////
ILShutter::ILShutter (): 
   initialized_ (false),
   state_(0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This shutter is not installed in this Leica microscope");
}

ILShutter::~ILShutter ()
{
   Shutdown();
}

void ILShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ILShutter::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this shutter exists:
   bool present;
   if (!g_ScopeModel.IsDeviceAvailable(g_Lamp))
      return ERR_MODULE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &ILShutter::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool ILShutter::Busy()
{
   bool busy;
   int ret = g_ScopeModel.ILShutter_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

int ILShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int ILShutter::SetOpen(bool open)
{
   int position;
   if (open)
      position = 1;
   else
      position = 0;

   int ret = g_ScopeInterface.SetILShutterPosition(*this, *GetCoreCallback(), position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ILShutter::GetOpen(bool &open)
{

   int position;
   int ret = g_ScopeModel.ILShutter_.GetPosition(position);
   if (ret != DEVICE_OK)
      return ret;

   if (position == 0)
      open = false;
   else if (position == 1)
      open = true;
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int ILShutter::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ILShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         return this->SetOpen(true);
      }
      else
      {
         return this->SetOpen(false);
      }
   }
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Leica Transmitted light Shutter
///////////////////////////////////////////////////////////////////////////////
TLShutter::TLShutter (): 
   initialized_ (false),
   state_(0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This shutter is not installed in this Leica microscope");
}

TLShutter::~TLShutter ()
{
   Shutdown();
}

void TLShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int TLShutter::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this shutter exists:
   bool present;
   if (!g_ScopeModel.IsDeviceAvailable(g_Lamp))
      return ERR_MODULE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &TLShutter::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool TLShutter::Busy()
{
   bool busy;
   int ret = g_ScopeModel.TLShutter_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

int TLShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int TLShutter::SetOpen(bool open)
{
   int position;
   if (open)
      position = 1;
   else
      position = 0;

   int ret = g_ScopeInterface.SetTLShutterPosition(*this, *GetCoreCallback(), position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TLShutter::GetOpen(bool &open)
{

   int position;
   int ret = g_ScopeModel.TLShutter_.GetPosition(position);
   if (ret != DEVICE_OK)
      return ret;

   if (position == 0)
      open = false;
   else if (position == 1)
      open = true;
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int TLShutter::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int TLShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         return this->SetOpen(true);
      }
      else
      {
         return this->SetOpen(false);
      }
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// General Turret Object, implement all Changers. Inherit and override for
// more specialized requirements (like specific labels)
///////////////////////////////////////////////////////////////////////////////
ILTurret::ILTurret():
   numPos_(5),
   initialized_ (false),
   name_("Dichroics turret"),
   description_("Incident Light Reflector Turret"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This turret is not installed in this Leica microscope");
   SetErrorText(ERR_TURRET_NOT_ENGAGED, "Incident Light Turret is not engaged");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

   //UpdateStatus();
}

ILTurret::~ILTurret()
{
   Shutdown();
}

void ILTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ILTurret::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   bool present;
   if (! g_ScopeModel.IsDeviceAvailable(g_IL_Turret))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &ILTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "1-", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_ScopeModel.ILTurret_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   for (unsigned i=0; i < numPos_; i++)
   {
      ostringstream os;
      os << i+1 << "-" << g_ScopeModel.ILTurret_.cube_[i+1].name;
      SetPositionLabel(i, os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int ILTurret::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool ILTurret::Busy()
{
   bool busy;
   int ret = g_ScopeModel.ILTurret_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ILTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.ILTurret_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         return ERR_TURRET_NOT_ENGAGED;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= (int) numPos_)) {
         // check if the new position is allowed with this method
         int method;
         int ret = g_ScopeModel.method_.GetPosition(method);
         printf ("Method: %d\n", method);
         if (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(method)) {
            printf ("Not available\n");
            // the new cube does not support the current method.  Look for a method:
            // Look first in the FLUO methods, than in all available methods
            int i = 10;
            while (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i) && (i < 13)) {
               i++;
            }
            printf ("Propose method %d\n", i);
            if (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i)) {
               i = 0;
               while (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i) && (i < 16)) {
                  i++;
               }
            }
            printf ("Propose method %d\n", i);
            if (g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i))
               g_ScopeInterface.SetMethod(*this, *GetCoreCallback(), i);
         }

         return g_ScopeInterface.SetILTurretPosition(*this, *GetCoreCallback(), pos);
      } else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// ObjectiveTurret.  
///////////////////////////////////////////////////////////////////////////////
ObjectiveTurret::ObjectiveTurret() :
   numPos_(7),
   initialized_ (false),
   name_("Objective turret"),
   description_("Objective Turret"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  The Objective Turret cannot work without it");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Objective turret installed in this Leica microscope");
   SetErrorText(ERR_TURRET_NOT_ENGAGED, "Objective Light Turret is not engaged");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

}

ObjectiveTurret::~ObjectiveTurret()
{
   Shutdown();
}

void ObjectiveTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int ObjectiveTurret::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   bool present;
   if (! g_ScopeModel.IsDeviceAvailable(g_Revolver))
      return ERR_MODULE_NOT_FOUND;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &ObjectiveTurret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "1-", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_ScopeModel.ObjectiveTurret_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   for (unsigned i=0; i < numPos_; i++)
   {
      ostringstream os;
      os << i+1 << "-" << g_ScopeModel.ObjectiveTurret_.objective_[i+1].magnification_ << "x ";
      os << g_ScopeModel.ObjectiveTurret_.objective_[i+1].NA_ << "na";
      SetPositionLabel(i, os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ObjectiveTurret::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool ObjectiveTurret::Busy()
{
   bool busy;
   int ret = g_ScopeModel.ObjectiveTurret_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

int ObjectiveTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.ObjectiveTurret_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         return ERR_TURRET_NOT_ENGAGED;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= (int) numPos_)) {
         // check if the new position is allowed with this method
         int method;
         int ret = g_ScopeModel.method_.GetPosition(method);
         if (!g_ScopeModel.ObjectiveTurret_.objective_[pos].IsMethodAvailable(method)) {
            // the new cube does not support the current method.  Look for a method:
            // Look first in the FLUO methods, than in all available methods
            int i = 10;
            while (!g_ScopeModel.ObjectiveTurret_.objective_[pos].IsMethodAvailable(i) && i < 13) {
               i++;
            }
            if (!g_ScopeModel.ObjectiveTurret_.objective_[pos].IsMethodAvailable(i)) {
               int i = 0;
               while (!g_ScopeModel.ObjectiveTurret_.objective_[pos].IsMethodAvailable(i) && i < 16) {
                  i++;
               }
            }
            if (g_ScopeModel.ObjectiveTurret_.objective_[pos].IsMethodAvailable(i))
               g_ScopeInterface.SetMethod(*this, *GetCoreCallback(), i);
         }

         return g_ScopeInterface.SetRevolverPosition(*this, *GetCoreCallback(), pos);
      } else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}


/*
// TubeLensTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Leica microscope
///////////////////////////////////////////////////////////////////////////////
TubeLensTurret::TubeLensTurret(int devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

TubeLensTurret::~TubeLensTurret()
{
   Shutdown();
}

int TubeLensTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_ScopeInterface.tubeLensList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// SidePortTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Leica microscope
///////////////////////////////////////////////////////////////////////////////
SidePortTurret::SidePortTurret(int devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

SidePortTurret::~SidePortTurret()
{
   Shutdown();
}

int SidePortTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_ScopeInterface.sidePortList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CondenserTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Leica microscope
///////////////////////////////////////////////////////////////////////////////
CondenserTurret::CondenserTurret(int devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

CondenserTurret::~CondenserTurret()
{
   Shutdown();
}

int CondenserTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_ScopeInterface.condenserList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// General Servo Object, implements all Servos
///////////////////////////////////////////////////////////////////////////////
Servo::Servo(int devId, std::string name, std::string description):
   initialized_ (false),
   numPos_(5)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This device is not installed in this Leica microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

   UpdateStatus();
}

Servo::~Servo()
{
   Shutdown();
}

void Servo::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Servo::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // Position
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &Servo::OnPosition);
   // if there are multiple units, which one will we take?  For simplicity, use the last one for now
   unit_ = g_deviceInfo[devId_].deviceScalings[g_deviceInfo[devId_].deviceScalings.size()-1];

   ret = CreateProperty(unit_.c_str(), "1", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   minPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][0];
   maxPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][0];
   minPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][0];
   maxPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][0];
   for (size_t i=0; i < g_deviceInfo[devId_].scaledScale[unit_].size(); i++) {
      if (minPosScaled_ > g_deviceInfo[devId_].scaledScale[unit_][i])
      {
         minPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][i];
         minPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][i];
      }
      if (maxPosScaled_ < g_deviceInfo[devId_].scaledScale[unit_][i])
      {
         maxPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][i];
         maxPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][i];
      }
   }

   SetPropertyLimits(unit_.c_str(), minPosScaled_, maxPosScaled_);

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Servo::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool Servo::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Servo::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = LeicaServo::GetPosition(*this, *GetCoreCallback(), devId_, pos);
      if (ret != DEVICE_OK)
         return ret;
      // We have the native position here, translate to the 'scaled' position'
      // For simplicities sake we just do linear interpolation
      double posScaled = ((double)pos/(maxPosNative_-minPosNative_) * (maxPosScaled_ - minPosScaled_)) + minPosScaled_; 
      pProp->Set(posScaled);
   }
   else if (eAct == MM::AfterSet)
   {
      double posScaled;
      pProp->Get(posScaled);
      int posNative = (int) (posScaled/(maxPosScaled_-minPosScaled_) * (maxPosNative_ - minPosNative_)) + minPosNative_;
      return LeicaServo::SetPosition(*this, *GetCoreCallback(), devId_, posNative);
   }
   return DEVICE_OK;
}

//
// LeicaFocusStage: Micro-Manager implementation of focus drive
//
Axis::Axis (int devId, std::string name, std::string description): 
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth"),
   busyCounter_(0)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This Axis is not installed in this Leica microscope");
}

Axis::~Axis()
{
   Shutdown();
}

bool Axis::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;
   if (busy) {
	   busyCounter_++;
	   if (busyCounter_ > 30) {
		   // TODO: send another status request, hack: set Busy to false now
		   busyCounter_ = 0;
		   g_ScopeInterface.SetModelBusy(devId_, false);
	   }
   } else
	   busyCounter_ = 0;

   return busy;
}

void Axis::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaFocusAxis);
}


int Axis::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // Position
   CPropertyAction* pAct = new CPropertyAction(this, &Axis::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // MoveMode
   pAct = new CPropertyAction(this, &Axis::OnMoveMode);
   ret = CreateProperty("Move Mode", direct_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Move Mode", direct_.c_str()); 
   AddAllowedValue("Move Mode", uni_.c_str()); 
   AddAllowedValue("Move Mode", biSup_.c_str()); 
   AddAllowedValue("Move Mode", biAlways_.c_str()); 

   // velocity
   pAct = new CPropertyAction(this, &Axis::OnVelocity);
   ret = CreateProperty("Velocity-Acceleration", fast_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   

   // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized



   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Axis::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int Axis::SetPositionUm(double pos)
{
   long steps = (long)(pos / stepSize_um_);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int Axis::GetPositionUm(double& pos)
{
   long steps;                                                
   int ret = GetPositionSteps(steps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   pos = steps * stepSize_um_;

   return DEVICE_OK;
}

int Axis::SetPositionSteps(long steps)
{
   return LeicaAxis::SetPosition(*this, *GetCoreCallback(), devId_, steps, (LeicaByte) (moveMode_ & velocity_));
}

int Axis::GetPositionSteps(long& steps)
{
   return LeicaDevice::GetPosition(*this, *GetCoreCallback(), devId_, (LeicaLong&) steps);
}

int Axis::SetOrigin()
{
   return DEVICE_OK;
}

int Axis::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      double pos;                                             
      pProp->Get(pos);                                        
      int ret = SetPositionUm(pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}


int Axis::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int Axis::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

//
// LeicaXYStage: Micro-Manager implementation of X and Y Stage
//
XYStage::XYStage (): 
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth")
{
   name_ = g_LeicaXYStage;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No XYStage installed on this Leica microscope");
}

XYStage::~XYStage()
{
   Shutdown();
}

bool XYStage::Busy()
{
   bool xBusy = false;
   bool yBusy = false;
   int ret = GetBusy(*this, *GetCoreCallback(), g_StageXAxis, xBusy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;
   ret = GetBusy(*this, *GetCoreCallback(), g_StageYAxis, yBusy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return xBusy && yBusy;
}

void XYStage::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaXYStage);
}


int XYStage::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   bool presentX, presentY;
   // TODO: check both stages
   int ret = GetPresent(*this, *GetCoreCallback(), g_StageYAxis, presentY);
   if (ret != DEVICE_OK)
      return ret;
   ret = GetPresent(*this, *GetCoreCallback(), g_StageXAxis, presentX);
   if (ret != DEVICE_OK)
      return ret;
   if (!(presentX && presentY))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // MoveMode
   CPropertyAction* pAct = new CPropertyAction(this, &XYStage::OnMoveMode);
   ret = CreateProperty("Move Mode", direct_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Move Mode", direct_.c_str()); 
   AddAllowedValue("Move Mode", uni_.c_str()); 
   AddAllowedValue("Move Mode", biSup_.c_str()); 
   AddAllowedValue("Move Mode", biAlways_.c_str()); 

   // velocity
   pAct = new CPropertyAction(this, &XYStage::OnVelocity);
   ret = CreateProperty("Velocity-Acceleration", fast_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   

   // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized


   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int XYStage::GetLimits(double& xMin, double& xMax, double& yMin, double& yMax) 
{
   // TODO: rework to our own coordinate system
   xMin = 0;
   yMin = 0;
   xMax = g_deviceInfo[g_StageXAxis].maxPos;
   yMax = g_deviceInfo[g_StageYAxis].maxPos;
   return DEVICE_OK;
}

int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = (long)(x / stepSize_um_);
   long ySteps = (long)(y / stepSize_um_);
   int ret = SetPositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetRelativePositionUm(double x, double y)
{
   long xSteps = (long)(x / stepSize_um_);
   long ySteps = (long)(y / stepSize_um_);
   int ret = SetRelativePositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   x = xSteps * stepSize_um_;
   y = ySteps * stepSize_um_;

   return DEVICE_OK;
}

int XYStage::SetPositionSteps(long xSteps, long ySteps)
{
   int ret = LeicaAxis::SetPosition(*this, *GetCoreCallback(), g_StageXAxis, xSteps, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = LeicaAxis::SetPosition(*this, *GetCoreCallback(), g_StageYAxis, ySteps, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetRelativePositionSteps(long xSteps, long ySteps)
{
   int ret = LeicaAxis::SetRelativePosition(*this, *GetCoreCallback(), g_StageXAxis, xSteps, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = LeicaAxis::SetRelativePosition(*this, *GetCoreCallback(), g_StageYAxis, ySteps, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& xSteps, long& ySteps)
{
   int ret = LeicaDevice::GetPosition(*this, *GetCoreCallback(), g_StageXAxis, (LeicaLong&) xSteps);
   if (ret != DEVICE_OK)
      return ret;

   ret = LeicaDevice::GetPosition(*this, *GetCoreCallback(), g_StageYAxis, (LeicaLong&) ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::Home()
{
   int ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageXAxis, LOWER);
   if (ret != DEVICE_OK)
      return ret;
   ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageYAxis, LOWER);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::Stop()
{
   int ret = LeicaAxis::StopMove(*this, *GetCoreCallback(), g_StageXAxis, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = LeicaAxis::StopMove(*this, *GetCoreCallback(), g_StageYAxis, (LeicaByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetOrigin()
{
   return DEVICE_OK;
}

int XYStage::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int XYStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}
*/
