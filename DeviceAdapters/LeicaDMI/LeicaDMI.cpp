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
const char* g_LeicaFieldDiaphragmTL = "TL-FieldDiaphragm";
const char* g_LeicaApertureDiaphragmTL = "TL-ApertureDiaphragm";
const char* g_LeicaFieldDiaphragmIL = "IL-FieldDiaphragm";
const char* g_LeicaApertureDiaphragmIL = "IL-ApertureDiaphragm";
const char* g_LeicaFocusAxis = "FocusDrive";
const char* g_LeicaMagChanger = "MagnificationChanger";
const char* g_LeicaTubeLensShutter = "TubeLensShutter";
const char* g_LeicaSidePort = "SidePort";
const char* g_LeicaIncidentLightShutter = "IL-Shutter";
const char* g_LeicaTransmittedLightShutter = "TL-Shutter";
const char* g_LeicaHalogenLightSwitch = "HalogenLightSwitch";
const char* g_LeicaRLFLAttenuator = "IL-FLAttenuator";
const char* g_LeicaXYStage = "XYStage";
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
   AddAvailableDeviceName(g_LeicaFocusAxis,"Z-drive");
   AddAvailableDeviceName(g_LeicaXYStage,"XYStage");
   AddAvailableDeviceName(g_LeicaFieldDiaphragmTL,"Field Diaphragm (Trans)");
   AddAvailableDeviceName(g_LeicaApertureDiaphragmTL,"Aperture Diaphragm (Condensor)");
   AddAvailableDeviceName(g_LeicaFieldDiaphragmIL,"Field Diaphragm (Fluorescence)");
   AddAvailableDeviceName(g_LeicaApertureDiaphragmIL,"Aperture Diaphragm (Fluorescence)");
   AddAvailableDeviceName(g_LeicaMagChanger,"Tube Lens (magnification changer)");
   /*
   AddAvailableDeviceName(g_LeicaTubeLensShutter,"Tube Lens Shutter");
   AddAvailableDeviceName(g_LeicaSidePort,"Side Port");
   AddAvailableDeviceName(g_LeicaReflectedLightShutter,"Reflected Light Shutter"); 
   AddAvailableDeviceName(g_LeicaRLFLAttenuator,"Reflected (fluorescence) light attenuator");
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
   else if (strcmp(deviceName, g_LeicaFocusAxis) == 0)
        return new ZDrive();
   else if (strcmp(deviceName, g_LeicaFieldDiaphragmTL) == 0)
        return new Diaphragm(&g_ScopeModel.fieldDiaphragmTL_, g_Field_Diaphragm_TL, g_LeicaFieldDiaphragmTL);
   else if (strcmp(deviceName, g_LeicaApertureDiaphragmTL) == 0)
        return new Diaphragm(&g_ScopeModel.apertureDiaphragmTL_, g_Aperture_Diaphragm_TL, g_LeicaApertureDiaphragmTL);
   else if (strcmp(deviceName, g_LeicaFieldDiaphragmIL) == 0)
        return new Diaphragm(&g_ScopeModel.fieldDiaphragmIL_, g_Field_Diaphragm_IL, g_LeicaFieldDiaphragmIL);
   else if (strcmp(deviceName, g_LeicaApertureDiaphragmIL) == 0)
        return new Diaphragm(&g_ScopeModel.apertureDiaphragmIL_, g_Aperture_Diaphragm_IL, g_LeicaApertureDiaphragmIL);
   else if (strcmp(deviceName, g_LeicaXYStage) == 0)
	   return new XYStage();
   else if (strcmp(deviceName, g_LeicaMagChanger) == 0)
	   return new MagChanger();
   /*
   else if (strcmp(deviceName, g_LeicaTubeLensShutter) == 0)
        return new Shutter(g_TubeShutter, g_LeicaTubeLensShutter, "Tube Lens Shutter");
   else if (strcmp(deviceName, g_LeicaSidePort) == 0)
        return new SidePortTurret(g_SidePortChanger, g_LeicaSidePort, "Side Port");
   else if (strcmp(deviceName, g_LeicaReflectedLightShutter) == 0)
        return new  Shutter(g_ReflectedLightShutter, g_LeicaReflectedLightShutter, "Leica Reflected Light Shutter");
   else if (strcmp(deviceName, g_LeicaRLFLAttenuator) == 0)
        return new Turret(g_RLFLAttenuatorChanger, g_LeicaRLFLAttenuator, "Attenuator (reflected light)");
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
// ReFlected Light Turret
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
         if (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(method)) {
            // the new cube does not support the current method.  Look for a method:
            // Look first in the FLUO methods, than in all available methods
            int i = 10;
            while (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i) && (i < 13)) {
               i++;
            }
            if (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i)) {
               i = 0;
               while (!g_ScopeModel.ILTurret_.cube_[pos].IsMethodAvailable(i) && (i < 16)) {
                  i++;
               }
            }
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

   // Article Number
   // -----
   pAct = new CPropertyAction(this, &ObjectiveTurret::OnArticleNumber);
   ret = CreateProperty("Article Number", "", MM::String, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

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

int ObjectiveTurret::OnArticleNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.ObjectiveTurret_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         return ERR_TURRET_NOT_ENGAGED;
      ostringstream os;
      os << g_ScopeModel.ObjectiveTurret_.objective_[pos].articleNumber_;
      pProp->Set(os.str().c_str());
   }
   return DEVICE_OK;
}

/*
 * LeicaFocusStage: Micro-Manager implementation of focus drive
 */
ZDrive::ZDrive (): 
   initialized_(false),
   name_("ZDrive"),
   description_("Focus Drive")
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No ZDrive found in this Leica microscope");
}

ZDrive::~ZDrive()
{
   Shutdown();
}

bool ZDrive::Busy()
{
   bool busy;
   int ret = g_ScopeModel.ZDrive_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;
   return busy;
}

void ZDrive::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaFocusAxis);
}


int ZDrive::Initialize()
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
   if (! g_ScopeModel.IsDeviceAvailable(g_ZDrive))
      return ERR_MODULE_NOT_FOUND;

   // Acceleration 
   CPropertyAction* pAct = new CPropertyAction(this, &ZDrive::OnAcceleration);
   ret = CreateProperty("Acceleration", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Acceleration", g_ScopeModel.ZDrive_.GetMinRamp(), g_ScopeModel.ZDrive_.GetMaxRamp());

   // Speed 
   pAct = new CPropertyAction(this, &ZDrive::OnSpeed);
   ret = CreateProperty("Speed", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Speed", g_ScopeModel.ZDrive_.GetMinSpeed(), g_ScopeModel.ZDrive_.GetMaxSpeed());

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int ZDrive::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

int ZDrive::SetPositionUm(double pos)
{
   long steps = (long)(pos / g_ScopeModel.ZDrive_.GetStepSize());
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int ZDrive::GetPositionUm(double& pos)
{
   long steps;                                                
   int ret = GetPositionSteps(steps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   pos = steps * g_ScopeModel.ZDrive_.GetStepSize();

   return DEVICE_OK;
}

int ZDrive::SetPositionSteps(long steps)
{
   return g_ScopeInterface.SetDrivePosition(*this, *GetCoreCallback(), g_ScopeModel.ZDrive_, g_ZDrive, (int) steps);
}

int ZDrive::GetPositionSteps(long& steps)
{
   return g_ScopeModel.ZDrive_.GetPosition((int&) steps);
}

int ZDrive::SetOrigin()
{
   return DEVICE_OK;
}

int ZDrive::GetLimits(double& lower, double& upper)
{
   int min, max;
   g_ScopeModel.ZDrive_.GetMinPosition(min);
   g_ScopeModel.ZDrive_.GetMaxPosition(max);
   lower = min * g_ScopeModel.ZDrive_.GetStepSize();
   upper = max * g_ScopeModel.ZDrive_.GetStepSize();

   return DEVICE_OK;
}

int ZDrive::Home()
{
   // TODO: Implement!
   return DEVICE_OK;
}

int ZDrive::Stop()
{
   // TODO: Implement!
   return DEVICE_OK;
}

int ZDrive::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      int acc;
      int ret = g_ScopeModel.ZDrive_.GetRamp(acc);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)acc);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long acc;
      pProp->Get(acc);
      int ret = g_ScopeInterface.SetDriveAcceleration(*this, *GetCoreCallback(), g_ScopeModel.ZDrive_, g_ZDrive, (int) acc);
      if (ret != DEVICE_OK)
         return ret;
   }
                                                              
   return DEVICE_OK;                                          
}

int ZDrive::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      int speed;
      int ret = g_ScopeModel.ZDrive_.GetSpeed(speed);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)speed);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long speed;
      pProp->Get(speed);
      int ret = g_ScopeInterface.SetDriveSpeed(*this, *GetCoreCallback(), g_ScopeModel.ZDrive_, g_ZDrive, (int) speed);
      if (ret != DEVICE_OK)
         return ret;
   }
                                                              
   return DEVICE_OK;                                          
}

/*
 * LeicaXYStage: Micro-Manager implementation of X and Y Stage
*/
XYStage::XYStage (): 
   busy_ (false),
   initialized_ (false),
   originX_(0),
   originY_(0),
   mirrorX_(false),
   mirrorY_(false)

{
   name_ = g_LeicaXYStage;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica XYStage to work");
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
   int ret = g_ScopeModel.XDrive_.GetBusy(xBusy);
   if (ret != DEVICE_OK)  
      return false;
   ret = g_ScopeModel.YDrive_.GetBusy(yBusy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return (xBusy || yBusy);
}

void XYStage::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaXYStage);
}


int XYStage::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if the XY stage exists
   bool present;
   if (! (g_ScopeModel.IsDeviceAvailable(g_XDrive) && g_ScopeModel.IsDeviceAvailable(g_YDrive)))
      return ERR_MODULE_NOT_FOUND;

   // Acceleration 
   CPropertyAction* pAct = new CPropertyAction(this, &XYStage::OnAcceleration);
   ret = CreateProperty("Acceleration", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   int minRamp = g_ScopeModel.XDrive_.GetMinRamp();
   if (g_ScopeModel.YDrive_.GetMinRamp() > minRamp)
      minRamp = g_ScopeModel.YDrive_.GetMinRamp();
   int maxRamp = g_ScopeModel.XDrive_.GetMaxRamp();
   if (g_ScopeModel.YDrive_.GetMaxRamp() < maxRamp)
      maxRamp = g_ScopeModel.YDrive_.GetMaxRamp();
   SetPropertyLimits("Acceleration", minRamp, maxRamp);

   // Speed 
   pAct = new CPropertyAction(this, &XYStage::OnSpeed);
   ret = CreateProperty("Speed", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   int minSpeed = g_ScopeModel.XDrive_.GetMinSpeed();
   if (g_ScopeModel.YDrive_.GetMinSpeed() > minSpeed)
      minSpeed = g_ScopeModel.YDrive_.GetMinSpeed();
   int maxSpeed = g_ScopeModel.XDrive_.GetMaxSpeed();
   if (g_ScopeModel.YDrive_.GetMaxSpeed() < maxSpeed)
      maxSpeed = g_ScopeModel.YDrive_.GetMaxSpeed();
   SetPropertyLimits("Speed", minSpeed, maxSpeed);

   // Directionality
   pAct = new CPropertyAction (this, &XYStage::OnMirrorX);
   CreateProperty("MirrorX", "0", MM::Integer, false, pAct);
   AddAllowedValue("MirrorX", "0");
   AddAllowedValue("MirrorX", "1");
   pAct = new CPropertyAction (this, &XYStage::OnMirrorY);
   CreateProperty("MirrorY", "0", MM::Integer, false, pAct);
   AddAllowedValue("MirrorY", "0");
   AddAllowedValue("MirrorY", "1");

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
   int xMinStep, yMinStep, xMaxStep, yMaxStep;
   g_ScopeModel.XDrive_.GetMinPosition(xMinStep);
   xMin = xMinStep * g_ScopeModel.XDrive_.GetStepSize();
   g_ScopeModel.YDrive_.GetMinPosition(yMinStep);
   yMin = yMinStep * g_ScopeModel.YDrive_.GetStepSize();
   g_ScopeModel.XDrive_.GetMaxPosition(xMaxStep);
   xMax = xMaxStep * g_ScopeModel.XDrive_.GetStepSize();
   g_ScopeModel.YDrive_.GetMaxPosition(yMaxStep);
   yMax = yMaxStep * g_ScopeModel.YDrive_.GetStepSize();
   return DEVICE_OK;
}

int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = 0;
   long ySteps = 0;

   /*
   long xSteps = (long)(x / g_ScopeModel.XDrive_.GetStepSize());
   long ySteps = (long)(y / g_ScopeModel.XDrive_.GetStepSize());
   */

   if (mirrorX_)
      xSteps = (long) ((originX_ - x) / g_ScopeModel.XDrive_.GetStepSize() + 0.5);
   else
      xSteps = (long) ((originX_ + x) / g_ScopeModel.XDrive_.GetStepSize() + 0.5);

   if (mirrorY_)
      ySteps = (long) ((originY_ - y) / g_ScopeModel.XDrive_.GetStepSize() + 0.5);
   else
      ySteps = (long) ((originY_ + y) / g_ScopeModel.XDrive_.GetStepSize() + 0.5);

   return SetPositionSteps(xSteps, ySteps);
}

int XYStage::SetRelativePositionUm(double x, double y)
{
/*
   long xSteps = (long)(x / g_ScopeModel.XDrive_.GetStepSize());
   long ySteps = (long)(y / g_ScopeModel.XDrive_.GetStepSize());
*/
   long xSteps = (long) (x / g_ScopeModel.XDrive_.GetStepSize() + 0.5);                            
   if (mirrorX_)                                                             
      xSteps = -xSteps;                                                      
   long ySteps = (long) (y / g_ScopeModel.XDrive_.GetStepSize() + 0.5);                            
   if (mirrorY_)                                                             
      ySteps = -ySteps;                                                      

   return  SetRelativePositionSteps(xSteps, ySteps);
}

int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
/*
   x = xSteps * g_ScopeModel.XDrive_.GetStepSize();
   y = ySteps * g_ScopeModel.XDrive_.GetStepSize();
*/
   if (mirrorX_)                                                             
      x = originX_ - (xSteps * g_ScopeModel.XDrive_.GetStepSize());                                
   else                                                                      
      x = originX_ + (xSteps * g_ScopeModel.XDrive_.GetStepSize());                                
                                                                             
   if (mirrorY_)                                                             
      y = originY_ - (ySteps * g_ScopeModel.XDrive_.GetStepSize());                                
   else                                                                      
      y = originY_ + (ySteps * g_ScopeModel.XDrive_.GetStepSize());                                

   return DEVICE_OK;
}

int XYStage::SetPositionSteps(long xSteps, long ySteps)
{
   int ret = g_ScopeInterface.SetDrivePosition(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive, xSteps);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_ScopeInterface.SetDrivePosition(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetRelativePositionSteps(long xSteps, long ySteps)
{
   int ret = g_ScopeInterface.SetDrivePositionRelative(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive, xSteps);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_ScopeInterface.SetDrivePositionRelative(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& xSteps, long& ySteps)
{
   int ret = g_ScopeModel.XDrive_.GetPosition((int&) xSteps);
   if (ret != DEVICE_OK)
      return ret;

   ret = g_ScopeModel.YDrive_.GetPosition((int&) ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/**
 * Defines position x,y (relative to current position) as the origin of our coordinate system
 * Get the current (stage-native) XY position
 */
int XYStage::SetAdapterOriginUm(double x, double y)
{
   long xStep, yStep;

   int ret = GetPositionSteps(xStep, yStep);
   if (ret != DEVICE_OK)
      return ret;
   originX_ = (xStep * g_ScopeModel.XDrive_.GetStepSize()) + x;
   originY_ = (yStep * g_ScopeModel.XDrive_.GetStepSize()) + y;

   return DEVICE_OK;
}

int XYStage::Home()
{
   int ret = g_ScopeInterface.HomeDrive(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_ScopeInterface.HomeDrive(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::Stop()
{
   int ret = g_ScopeInterface.StopDrive(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_ScopeInterface.StopDrive(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetOrigin()
{
   return SetAdapterOriginUm(0,0);
}

int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      int acc;
      int ret = g_ScopeModel.XDrive_.GetRamp(acc);
      if (ret != DEVICE_OK)
         return ret;
      // assume that YDrive ramp is the same
      pProp->Set((long)acc);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long acc;
      pProp->Get(acc);
      int ret = g_ScopeInterface.SetDriveAcceleration(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive, (int) acc);
      if (ret != DEVICE_OK)
         return ret;
      ret = g_ScopeInterface.SetDriveAcceleration(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive, (int) acc);
      if (ret != DEVICE_OK)
         return ret;
   }
                                                              
   return DEVICE_OK;                                          
}


int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      int speed;
      int ret = g_ScopeModel.XDrive_.GetSpeed(speed);
      if (ret != DEVICE_OK)
         return ret;
      // Assume Y-drive has the same setting
      pProp->Set((long)speed);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long speed;
      pProp->Get(speed);
      int ret = g_ScopeInterface.SetDriveSpeed(*this, *GetCoreCallback(), g_ScopeModel.XDrive_, g_XDrive, (int) speed);
      if (ret != DEVICE_OK)
         return ret;
      ret = g_ScopeInterface.SetDriveSpeed(*this, *GetCoreCallback(), g_ScopeModel.YDrive_, g_YDrive, (int) speed);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;                                          
}

int XYStage::OnMirrorX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (mirrorX_)
         pProp->Set("1");
      else
         pProp->Set("0");
   } else if (eAct == MM::AfterSet) {
      long mirrorX;
      pProp->Get(mirrorX);

      if (mirrorX == 1)
         mirrorX_ = true;
      else                                                                   
         mirrorX_ = false;                                                   
   }                                                                         
   return DEVICE_OK;                                                         
}

int XYStage::OnMirrorY(MM::PropertyBase* pProp, MM::ActionType eAct)         
{                                                                            
   if (eAct == MM::BeforeGet)                                                
   {                                                                         
      if (mirrorY_)                                                          
         pProp->Set("1");                                                    
      else                                                                   
         pProp->Set("0");                                                    
   } else if (eAct == MM::AfterSet) {                                        
      long mirrorY;                                                          
      pProp->Get(mirrorY);                                                   
                                                                             
      if (mirrorY == 1)                                                      
         mirrorY_ = true;                                                    
      else                                                                   
         mirrorY_ = false;                                                   
   }                                                                         
   return DEVICE_OK;                                                         
}
///////////////////////////////////////////////////////////////////////////////
// General Diaphragm Object, implements all Diaphragms. 
///////////////////////////////////////////////////////////////////////////////
Diaphragm::Diaphragm(LeicaDeviceModel* diaphragm, int deviceID, std::string name):
   numPos_(5),
   initialized_ (false)
{
   diaphragm_ = diaphragm;
   deviceID_ = deviceID;
   name_ = name;

   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Diaphrgam to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this Diaphragm");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This Diaphragm is not installed in this Leica microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
}

Diaphragm::~Diaphragm()
{
   Shutdown();
}

void Diaphragm::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Diaphragm::Initialize()
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
   if (! g_ScopeModel.IsDeviceAvailable(deviceID_))
      return ERR_MODULE_NOT_FOUND;

   // Position
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &Diaphragm::OnPosition);
   ret = CreateProperty("Position", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   int max, min;
   diaphragm_->GetMaxPosition(max);
   diaphragm_->GetMinPosition(min);
   ret = SetPropertyLimits("Position", min, max);
   if (ret != DEVICE_OK)
      return ret;

   numPos_ = max;

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Diaphragm::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool Diaphragm::Busy()
{
   bool busy;
   int ret = diaphragm_->GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Diaphragm::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = diaphragm_->GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         return ERR_TURRET_NOT_ENGAGED;
      pProp->Set((long) pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if ((pos > 0) && (pos <= (int) numPos_)) {
         return g_ScopeInterface.SetDiaphragmPosition(*this, *GetCoreCallback(), diaphragm_, deviceID_, (int) pos);
      } else
         return ERR_INVALID_TURRET_POSITION;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Motorized Tube lens switcher (magnifier)
///////////////////////////////////////////////////////////////////////////////
MagChanger::MagChanger() :
   numPos_(4),
   initialized_ (false),
   name_("Magnifier"),
   description_("Motorized Magnifier"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized. ");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this motorized magnifier");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No motorized magnifier installed in this Leica microscope");
   SetErrorText(ERR_MAGNIFIER_NOT_ENGAGED, "Magnifier is not engaged");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

}

MagChanger::~MagChanger()
{
   Shutdown();
}

void MagChanger::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int MagChanger::Initialize()
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
   if (! g_ScopeModel.IsDeviceAvailable(g_Mag_Changer_Mot))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // Position 
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &MagChanger::OnPosition);
   ret = CreateProperty("Position", "1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   int minPos, maxPos;
   ret = g_ScopeModel.magChanger_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;
   ret = g_ScopeModel.magChanger_.GetMinPosition(minPos);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   for (unsigned i=minPos; i <= numPos_; i++)
   {
      ostringstream os;
      double mag;
      g_ScopeModel.magChanger_.GetMagnification(i, mag);
      os << i << "-" << mag << "x";
      AddAllowedValue("Position", os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int MagChanger::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool MagChanger::Busy()
{
   bool busy;
   int ret = g_ScopeModel.magChanger_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

double MagChanger::GetMagnification()
{
   int pos;
   g_ScopeModel.magChanger_.GetPosition(pos);
   double mag;
   g_ScopeModel.magChanger_.GetMagnification(pos, mag);
   return mag;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int MagChanger::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.magChanger_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      if (pos == 0)
         return ERR_TURRET_NOT_ENGAGED;
      ostringstream os;
      double mag;
      g_ScopeModel.magChanger_.GetMagnification(pos, mag);
      os << pos << "-" << mag << "x";
      pProp->Set(os.str().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string label;
      pProp->Get(label);
      std::stringstream st(label);
      int pos;
      st >> pos;
      if ((pos > 0) && (pos <= (int) numPos_)) {
         return g_ScopeInterface.SetMagChangerPosition(*this, *GetCoreCallback(), pos);
      } else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}
