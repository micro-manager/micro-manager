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
#include <string.h>
#include <math.h>
#include <sstream>
#include <algorithm>


using namespace std;

LeicaScopeInterface g_ScopeInterface;
LeicaDMIModel g_ScopeModel;

const char* g_LevelProp = "Level";

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Leica Devices
const char* g_LeicaDeviceName = "Scope";
const char* g_LeicaReflector = "IL-Turret";
const char* g_LeicaNosePiece = "ObjectiveTurret";
const char* g_LeicaFastFilterWheel = "FastFilterWheel";
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
const char* g_LeicaTLPolarizer = "TLPolarizer";
const char* g_LeicaDICTurret = "DIC Turret";
const char* g_LeicaCondensorTurret = "Condensor";
const char* g_LeicaTransmittedLight = "Transmitted Light";
const char* g_LeicaAFC = "Adaptive Focus Control";
const char* g_LeicaAFCOffset = "Adaptive Focus Control Offset";

///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_LeicaDeviceName, MM::HubDevice, "Leica DMI microscope controlled through serial interface");
   RegisterDevice(g_LeicaTransmittedLightShutter, MM::ShutterDevice, "Transmitted Light Shutter");
   RegisterDevice(g_LeicaIncidentLightShutter, MM::ShutterDevice, "Incident Light Shutter");
   RegisterDevice(g_LeicaReflector, MM::StateDevice, "Reflector Turret (dichroics)");
   RegisterDevice(g_LeicaNosePiece, MM::StateDevice, "Objective Turret");
   RegisterDevice(g_LeicaFastFilterWheel, MM::StateDevice, "Fast Filter Wheel");
   RegisterDevice(g_LeicaFocusAxis, MM::StageDevice, "Z-drive");
   RegisterDevice(g_LeicaXYStage, MM::XYStageDevice, "XYStage");
   RegisterDevice(g_LeicaFieldDiaphragmTL, MM::GenericDevice, "Field Diaphragm (Trans)");
   RegisterDevice(g_LeicaApertureDiaphragmTL, MM::GenericDevice, "Aperture Diaphragm (Condensor)");
   RegisterDevice(g_LeicaFieldDiaphragmIL, MM::GenericDevice, "Field Diaphragm (Fluorescence)");
   RegisterDevice(g_LeicaApertureDiaphragmIL, MM::GenericDevice, "Aperture Diaphragm (Fluorescence)");
   RegisterDevice(g_LeicaMagChanger, MM::MagnifierDevice, "Tube Lens (magnification changer)");
   RegisterDevice(g_LeicaTLPolarizer, MM::StateDevice, "Transmitted light Polarizer");
   RegisterDevice(g_LeicaDICTurret, MM::StateDevice, "DIC Turret");
   RegisterDevice(g_LeicaCondensorTurret, MM::StateDevice, "Condensor Turret");
   RegisterDevice(g_LeicaTransmittedLight, MM::ShutterDevice, "Transmitted Light");
   RegisterDevice(g_LeicaAFC, MM::AutoFocusDevice, "Adaptive Focus Control");
   RegisterDevice(g_LeicaAFCOffset, MM::StageDevice, "Adaptive Focus Control Offset");
   RegisterDevice(g_LeicaSidePort, MM::StateDevice, "Side Port");
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
   else if (strcmp(deviceName, g_LeicaFastFilterWheel) == 0)
        return new FastFilterWheel();
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
   else if (strcmp(deviceName, g_LeicaTLPolarizer) == 0)
	   return new TLPolarizer();
   else if (strcmp(deviceName, g_LeicaDICTurret) == 0)
	   return new DICTurret();
   else if (strcmp(deviceName, g_LeicaCondensorTurret) == 0)
	   return new CondensorTurret();
   else if (strcmp(deviceName, g_LeicaTransmittedLight) == 0)
	   return new TransmittedLight();
   else if (strcmp(deviceName, g_LeicaAFC) == 0)
	   return new AFC();
   else if (strcmp(deviceName, g_LeicaAFCOffset) == 0)
      return new AFCOffset();
	else if (strcmp(deviceName, ::g_LeicaSidePort) ==0)
		return new SidePort();

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
   initialized_(false)
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
   if (g_ScopeInterface.monitoringThread_ != 0) {
      g_ScopeInterface.monitoringThread_->Stop();
      g_ScopeInterface.monitoringThread_->wait();
      delete g_ScopeInterface.monitoringThread_;
      g_ScopeInterface.monitoringThread_ = 0;
   }
   g_ScopeInterface.initialized_ = false;

   Shutdown();
}


MM::DeviceDetectionStatus LeicaScope::DetectDevice()
{
   MM::Device* pS = GetCoreCallback()->GetDevice(this, g_ScopeInterface.port_.c_str());

   int ret;
   ret = GetCoreCallback()->SetSerialProperties(g_ScopeInterface.port_.c_str(), "500.0", "19200", "0.0", "Off", "None", "1");
   if (ret != 0)
   {
      return MM::CanNotCommunicate;
   }
   ret = pS->Initialize();

   if (ret == DEVICE_OK)
   {
       ret = g_ScopeInterface.GetDevicesPresent(*this, *GetCoreCallback());
   }
   
   pS->Shutdown();
   if (ret != DEVICE_OK)
   {
      return MM::CanNotCommunicate;
   }
   else
   {
      return MM::CanCommunicate;
   }
}


void LeicaScope::AttemptToDiscover(int deviceCode, const char* deviceName)
{
   if (g_ScopeInterface.scopeModel_->IsDeviceAvailable(deviceCode))
   {
      discoveredDevices_.push_back(std::string(deviceName));
   }
}


int LeicaScope::GetNumberOfDiscoverableDevices()
{
   discoveredDevices_.clear();
   if (!g_ScopeInterface.IsInitialized())
   {
      int ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
      if (ret != DEVICE_OK)
         return 0;
   }

      AttemptToDiscover(g_IL_Turret, g_LeicaReflector);
      AttemptToDiscover(g_Revolver, g_LeicaNosePiece);
      AttemptToDiscover(g_Fast_Filter_Wheel, g_LeicaFastFilterWheel);
      AttemptToDiscover(g_Field_Diaphragm_TL, g_LeicaFieldDiaphragmTL);
      AttemptToDiscover(g_Aperture_Diaphragm_TL, g_LeicaApertureDiaphragmTL);
      AttemptToDiscover(g_Field_Diaphragm_IL, g_LeicaFieldDiaphragmIL);
      AttemptToDiscover(g_Aperture_Diaphragm_IL, g_LeicaApertureDiaphragmIL);
      AttemptToDiscover(g_ZDrive, g_LeicaFocusAxis);
      AttemptToDiscover(g_Mag_Changer_Mot, g_LeicaMagChanger);
     // AttemptToDiscover(___, g_LeicaTubeLensShutter); Not supported.
      AttemptToDiscover(g_Side_Port, g_LeicaSidePort);
      AttemptToDiscover(g_Lamp, g_LeicaIncidentLightShutter);
      AttemptToDiscover(g_Lamp, g_LeicaTransmittedLightShutter);
      // AttemptToDiscover(___, g_LeicaHalogenLightSwitch); Not supported.
      // AttemptToDiscover(___, g_LeicaRLFLAttenuator); Not supported.
      AttemptToDiscover(g_XDrive, g_LeicaXYStage);
      // AttemptToDiscover(___, g_LeicaBasePort); Not supported.
      // AttemptToDiscover(___, g_LeicaUniblitz); Not supported.
      // AttemptToDiscover(___, g_LeicaFilterWheel); Not supported.
      AttemptToDiscover(g_TL_Polarizer, g_LeicaTLPolarizer);
      AttemptToDiscover(g_DIC_Turret, g_LeicaDICTurret);
      AttemptToDiscover(g_Condensor, g_LeicaCondensorTurret);
      AttemptToDiscover(g_Lamp, g_LeicaTransmittedLight);
      AttemptToDiscover(g_AFC, g_LeicaAFC);
      AttemptToDiscover(g_AFC, g_LeicaAFCOffset);

   return (int) discoveredDevices_.size();
}

void LeicaScope::GetDiscoverableDevice(int deviceNum, char *deviceName,
                                       unsigned int maxLength)
{
   if (0 <= deviceNum && deviceNum < static_cast<int>(discoveredDevices_.size()))
   {
      strncpy(deviceName, discoveredDevices_[deviceNum].c_str(), maxLength - 1);
      deviceName[maxLength - 1] = 0;
   }
   return;
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
   if (g_ScopeModel.UsesMethods()) {
      CPropertyAction* pAct = new CPropertyAction(this, &LeicaScope::OnMethod);
      CreateProperty("Method", "", MM::String, false, pAct);
      for (int i=0; i<16; i++) {
         if (g_ScopeModel.IsMethodAvailable(i)) {
            AddAllowedValue("Method", g_ScopeModel.GetMethod(i).c_str());
         }
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

int LeicaScope::DetectInstalledDevices()
{
   discoveredDevices_.clear();
   if (!g_ScopeInterface.IsInitialized())
   {
      return ERR_NOT_INITIALIZED;
   }

   AttemptToDiscover(g_IL_Turret, g_LeicaReflector);
   AttemptToDiscover(g_Revolver, g_LeicaNosePiece);
   AttemptToDiscover(g_Fast_Filter_Wheel, g_LeicaFastFilterWheel);
   AttemptToDiscover(g_Field_Diaphragm_TL, g_LeicaFieldDiaphragmTL);
   AttemptToDiscover(g_Aperture_Diaphragm_TL, g_LeicaApertureDiaphragmTL);
   AttemptToDiscover(g_Field_Diaphragm_IL, g_LeicaFieldDiaphragmIL);
   AttemptToDiscover(g_Aperture_Diaphragm_IL, g_LeicaApertureDiaphragmIL);
   AttemptToDiscover(g_ZDrive, g_LeicaFocusAxis);
   AttemptToDiscover(g_Mag_Changer_Mot, g_LeicaMagChanger);
   // AttemptToDiscover(___, g_LeicaTubeLensShutter); Not supported.
   AttemptToDiscover(g_Side_Port, g_LeicaSidePort);
   AttemptToDiscover(g_Lamp, g_LeicaIncidentLightShutter);
   AttemptToDiscover(g_Lamp, g_LeicaTransmittedLightShutter);
   // AttemptToDiscover(___, g_LeicaHalogenLightSwitch); Not supported.
   // AttemptToDiscover(___, g_LeicaRLFLAttenuator); Not supported.
   AttemptToDiscover(g_XDrive, g_LeicaXYStage);
   // AttemptToDiscover(___, g_LeicaBasePort); Not supported.
   // AttemptToDiscover(___, g_LeicaUniblitz); Not supported.
   // AttemptToDiscover(___, g_LeicaFilterWheel); Not supported.
   AttemptToDiscover(g_TL_Polarizer, g_LeicaTLPolarizer);
   AttemptToDiscover(g_DIC_Turret, g_LeicaDICTurret);
   AttemptToDiscover(g_Condensor, g_LeicaCondensorTurret);
   AttemptToDiscover(g_Lamp, g_LeicaTransmittedLight);
   AttemptToDiscover(g_AFC, g_LeicaAFC);
   AttemptToDiscover(g_AFC, g_LeicaAFCOffset);

   for (size_t i=0; i<discoveredDevices_.size(); i++)
   {
      MM::Device* pDev = ::CreateDevice(discoveredDevices_[i].c_str());
      if (pDev)
         AddInstalledDevice(pDev);
   }

   return DEVICE_OK;
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
      if (position >= 0) {
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
   state_(0),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This shutter is not installed in this Leica microscope");
   EnableDelay(); // signals that the delay setting will be used
}

ILShutter::~ILShutter ()
{
   Shutdown();
}

void ILShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, g_LeicaIncidentLightShutter);
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
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);

   int ret = g_ScopeModel.ILShutter_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      std::ostringstream os;
      os << "ILShutter did not respond to GetBusy: " << ret;
      LogMessage(os.str().c_str(), false);
      return false;
   }

   if (interval < delay)
       busy = true;

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

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();

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
   {
      std::ostringstream os;
      os << "ILShutter was found in a strange position: " << position;
      LogMessage(os.str().c_str(), false);
      return ERR_UNEXPECTED_ANSWER;
   }

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
   state_(0),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Leica Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This shutter is not installed in this Leica microscope");
   EnableDelay(); // signals that the delay setting will be used
}

TLShutter::~TLShutter ()
{
   Shutdown();
}

void TLShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, g_LeicaTransmittedLightShutter);
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
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);

   int ret = g_ScopeModel.TLShutter_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
   {
      std::ostringstream os;
      os << "TLShutter did not respond to GetBusy: " << ret;
      LogMessage(os.str().c_str(), false);
      return false;
   }

   if (interval < delay)
       busy = true;

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

   // Set timer for the Busy signal
   changedTime_ = GetCurrentMMTime();

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
   CDeviceUtils::CopyLimitedString(name, g_LeicaReflector);
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

   if (busy)
      return true;

   // We may have switched the Method, so we need to wait for it, too.
   ret = g_ScopeModel.method_.GetBusy(busy);
   if (ret != DEVICE_OK)
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
         if (g_ScopeModel.UsesMethods()) {
            // check if the new position is allowed with this method
            int method;
            g_ScopeModel.method_.GetPosition(method);
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

               // Now, although not ideal, we need to wait for the Method
               // switch to complete before we set the turret position.
               // Otherwise, the turret position command can get overridden by
               // the turret movement that is part of the method switch. As
               // observed on a DMi8.
               bool busy = true;
               while (busy) {
                  g_ScopeModel.method_.GetBusy(busy);
                  CDeviceUtils::SleepMs(20);
               }
            }
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
   CDeviceUtils::CopyLimitedString(name, g_LeicaNosePiece);
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

   // Immersion medium
   pAct = new CPropertyAction(this, &ObjectiveTurret::OnImmersion);
   ret = CreateProperty("Immersion Medium", "Dry", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue("Immersion Medium", "Dry");
   AddAllowedValue("Immersion Medium", "Immersion");

   //ret = UpdateStatus();
   //if (ret!= DEVICE_OK)
   //   return ret;

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
         if (g_ScopeModel.UsesMethods()) {
            // check if the new position is allowed with this method
            int method;
            g_ScopeModel.method_.GetPosition(method);
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

int ObjectiveTurret::OnImmersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      char method;
      int ret = g_ScopeModel.ObjectiveTurret_.GetImmersion(method);
      if (ret != DEVICE_OK)
         return ret;
      std::string message = "Dry";
      if (method == 'I')
         message = "Immersion";
      pProp->Set(message.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string message;
      pProp->Get(message);
      char method = 'I';
      if (message == "Dry")
         method = 'D';
      int ret = g_ScopeInterface.SetObjectiveImmersion(*this, *GetCoreCallback(), method);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// FastFilterWheel.  
///////////////////////////////////////////////////////////////////////////////
FastFilterWheel::FastFilterWheel() :
   numPos_(5),
   initialized_(false),
   name_(g_LeicaFastFilterWheel),
   description_("Fast Filter Wheel"),
   filterWheelID_(0)
{
   InitializeDefaultErrorMessages();

   // Error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  The Fast Filter Wheel cannot work without it");
   SetErrorText(ERR_SET_POSITION_FAILED, "Unable to set requested position");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Fast Filter Wheel installed in this Leica microscope");

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

   CreatePropertyWithHandler("Filter Wheel ID", "0", MM::Integer, false, &FastFilterWheel::OnFilterWheelID,true);
   for (int i=0; i<4; ++i) {
      stringstream ss;
      ss << i;
      AddAllowedValue("Filter Wheel ID", ss.str().c_str());
   }

}

FastFilterWheel::~FastFilterWheel()
{
   Shutdown();
}

void FastFilterWheel::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaFastFilterWheel);
}

int FastFilterWheel::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   if (! g_ScopeModel.IsDeviceAvailable(g_Revolver))
      return ERR_MODULE_NOT_FOUND;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &FastFilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label,  "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = g_ScopeModel.FastFilterWheel_[filterWheelID_].GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   for (unsigned i=0; i < numPos_; i++)
   {
      std::stringstream ss;
      ss << i << "_" << g_ScopeModel.FastFilterWheel_[filterWheelID_].GetPositionLabel(i+1);
      SetPositionLabel(i, ss.str().c_str());
   }

   initialized_ = true;

   return DEVICE_OK;
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int FastFilterWheel::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool FastFilterWheel::Busy()
{
   bool busy;
   int ret = g_ScopeModel.FastFilterWheel_[filterWheelID_].GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

int FastFilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.FastFilterWheel_[filterWheelID_].GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) (pos - 1));
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      return g_ScopeInterface.SetFastFilterWheelPosition(*this, *GetCoreCallback(), filterWheelID_ + 1, pos + 1);
   }
   return DEVICE_OK;
}


int FastFilterWheel::OnFilterWheelID(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(filterWheelID_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(filterWheelID_);
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
   //g_scopeInterface.
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
   CXYStageBase<XYStage>(),
   initialized_ (false),
   originXSteps_(0),
   originYSteps_(0)

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
   /*
   pAct = new CPropertyAction (this, &XYStage::OnMirrorX);
   CreateProperty("MirrorX", "0", MM::Integer, false, pAct);
   AddAllowedValue("MirrorX", "0");
   AddAllowedValue("MirrorX", "1");
   pAct = new CPropertyAction (this, &XYStage::OnMirrorY);
   CreateProperty("MirrorY", "0", MM::Integer, false, pAct);
   AddAllowedValue("MirrorY", "0");
   AddAllowedValue("MirrorY", "1");
   */

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

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax) 
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

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax) 
{
   int xMinStep, yMinStep, xMaxStep, yMaxStep;
   g_ScopeModel.XDrive_.GetMinPosition(xMinStep);
   g_ScopeModel.YDrive_.GetMinPosition(yMinStep);
   g_ScopeModel.XDrive_.GetMaxPosition(xMaxStep);
   g_ScopeModel.YDrive_.GetMaxPosition(yMaxStep);
   xMin = xMinStep;
   yMin = yMinStep;
   xMax = xMaxStep;
   yMax = yMaxStep;

   return DEVICE_OK;
}

double XYStage::GetStepSizeXUm()
{
   return g_ScopeModel.XDrive_.GetStepSize();
}

double XYStage::GetStepSizeYUm()
{
   return g_ScopeModel.YDrive_.GetStepSize();
}

/*
int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = 0;
   long ySteps = 0;

   //long xSteps = (long)(x / g_ScopeModel.XDrive_.GetStepSize());
   //long ySteps = (long)(y / g_ScopeModel.XDrive_.GetStepSize());

   if (mirrorX_)
      xSteps = (long) (originXSteps_ - (x / g_ScopeModel.XDrive_.GetStepSize() + 0.5));
   else
      xSteps = (long) (originXSteps_ + (x / g_ScopeModel.XDrive_.GetStepSize() + 0.5));

   if (mirrorY_)
      ySteps = (long) (originYSteps_ - (x / g_ScopeModel.XDrive_.GetStepSize() + 0.5));
   else
      ySteps = (long) (originYSteps_ + (x / g_ScopeModel.XDrive_.GetStepSize() + 0.5));

   return SetPositionSteps(xSteps, ySteps);
}

int XYStage::SetRelativePositionUm(double x, double y)
{
   //long xSteps = (long)(x / g_ScopeModel.XDrive_.GetStepSize());
   //long ySteps = (long)(y / g_ScopeModel.XDrive_.GetStepSize());
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
   //x = xSteps * g_ScopeModel.XDrive_.GetStepSize();
   //y = ySteps * g_ScopeModel.XDrive_.GetStepSize();

   if (mirrorX_)                                                             
      x = (xSteps - originXSteps_) * g_ScopeModel.XDrive_.GetStepSize();
   else
      x = - (xSteps - originXSteps_) * g_ScopeModel.XDrive_.GetStepSize();

   if (mirrorY_)                                                             
      y = (ySteps - originYSteps_) * g_ScopeModel.XDrive_.GetStepSize();
   else
      y = - (ySteps - originYSteps_) * g_ScopeModel.XDrive_.GetStepSize();

   return DEVICE_OK;
}
*/

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
   originXSteps_ = (long) (xStep + (x / g_ScopeModel.XDrive_.GetStepSize()));
   originYSteps_ = (long) (yStep + (y / g_ScopeModel.XDrive_.GetStepSize()));

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

/*
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
*/

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
   description_("Motorized Magnifier")
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
   CDeviceUtils::CopyLimitedString(name, g_LeicaMagChanger);
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

///////////////////////////////////////////////////////////////////////////////
// TL Polarizer (the one in the transmitted light path??)
///////////////////////////////////////////////////////////////////////////////
TLPolarizer::TLPolarizer():
   numPos_(1),
   initialized_ (false),
   name_("TL Polarizer"),
   description_("Transmitted light Polarizer"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This turret is not installed in this Leica microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

}

TLPolarizer::~TLPolarizer()
{
   Shutdown();
}

void TLPolarizer::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaTLPolarizer);
}

int TLPolarizer::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   if (! g_ScopeModel.IsDeviceAvailable(g_TL_Polarizer))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &TLPolarizer::OnState);
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
	ret = g_ScopeModel.tlPolarizer_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   SetPositionLabel(0, "Out");
   SetPositionLabel(1, "In");

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int TLPolarizer::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool TLPolarizer::Busy()
{
   bool busy;
   int ret = g_ScopeModel.tlPolarizer_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int TLPolarizer::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.tlPolarizer_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int curr_pos;
      int ret = g_ScopeModel.tlPolarizer_.GetPosition(curr_pos);
      if (ret != DEVICE_OK)
         return ret;
      if (curr_pos == pos_)
         return DEVICE_OK;

      return g_ScopeInterface.SetTLPolarizerPosition(*this, *GetCoreCallback(), pos_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// DIC Turret
///////////////////////////////////////////////////////////////////////////////
DICTurret::DICTurret():
   numPos_(4),
   initialized_ (false),
   name_("DIC Turret"),
   description_("DIC Prism Turret"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This turret is not installed in this Leica microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

}

DICTurret::~DICTurret()
{
   Shutdown();
}

void DICTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaDICTurret);
}

int DICTurret::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   if (! g_ScopeModel.IsDeviceAvailable(g_DIC_Turret))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &DICTurret::OnState);
   if (g_ScopeModel.dicTurret_.isMotorized())
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   else
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "1-", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int minPos, maxPos;
   ret = g_ScopeModel.dicTurret_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_ScopeModel.dicTurret_.GetMinPosition(minPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;
   
   for (int i=minPos; i <= maxPos; i++)
   {
      ostringstream os;
      os << i << "-" << g_ScopeModel.dicTurret_.prismName_[i];
      SetPositionLabel(i-minPos, os.str().c_str());
   }

   // Fine Position 
   // -----
   if (g_ScopeModel.dicTurret_.isMotorized()) {
      pAct = new CPropertyAction(this, &DICTurret::OnPrismFinePosition);
      ret = CreateProperty("Prism Fine Position", "0.5", MM::Float, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }
   int max, min;
   min = g_ScopeModel.dicTurret_.GetMinFinePosition();
   max = g_ScopeModel.dicTurret_.GetMaxFinePosition();
   ret = SetPropertyLimits("Prism Fine Position", min, max);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int DICTurret::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool DICTurret::Busy()
{
   bool busy;
   int ret = g_ScopeModel.dicTurret_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int DICTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.dicTurret_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos;
      pProp->Set(pos_ - 1);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      return g_ScopeInterface.SetDICPrismTurretPosition(*this, *GetCoreCallback(), pos_ + 1);
   }
   return DEVICE_OK;
}

int DICTurret::OnPrismFinePosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int finePos;
      int ret = g_ScopeModel.dicTurret_.GetFinePosition(finePos);
      if (ret != DEVICE_OK)
         return ret;
      finePos_ = finePos;
      pProp->Set(finePos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(finePos_);
      return g_ScopeInterface.SetDICPrismFinePosition(*this, *GetCoreCallback(), (int) finePos_);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Condensor Turret
///////////////////////////////////////////////////////////////////////////////
CondensorTurret::CondensorTurret():
   numPos_(7),
   initialized_ (false),
   name_("Condensor turret"),
   description_("Conensor Turret"),
   pos_(1)
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This turret is not installed in this Leica microscope");
   SetErrorText(ERR_TURRET_NOT_ENGAGED, "Conensor Turret is not engaged");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);
}

CondensorTurret::~CondensorTurret()
{
   Shutdown();
}

void CondensorTurret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaCondensorTurret);
}

int CondensorTurret::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
   if (! g_ScopeModel.IsDeviceAvailable(g_Condensor))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &CondensorTurret::OnState);
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
   ret = g_ScopeModel.Condensor_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   for (unsigned i=0; i < numPos_; i++)
   {
      ostringstream os;
      os << i+1 << "-" << g_ScopeModel.Condensor_.filter_[i+1];
      SetPositionLabel(i, os.str().c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CondensorTurret::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool CondensorTurret::Busy()
{
   bool busy;
   int ret = g_ScopeModel.Condensor_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int CondensorTurret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.Condensor_.GetPosition(pos);
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
      int pos;
      g_ScopeModel.Condensor_.GetPosition(pos);
      if (pos == pos_ + 1)
         return DEVICE_OK;
      pos = pos_ + 1;
      if ((pos > 0) && (pos <= (int) numPos_)) {
         return g_ScopeInterface.SetCondensorPosition(*this, *GetCoreCallback(), pos);
      } else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Transmitted Light
///////////////////////////////////////////////////////////////////////////////
TransmittedLight::TransmittedLight():   
   initialized_ (false),
   state_(false), 
   level_(1),
   changedTime_(0.0)
{
   InitializeDefaultErrorMessages();
   EnableDelay();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for the Transmitted Light module to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This device is not installed in this Leica microscope");
}

TransmittedLight::~TransmittedLight ()
{
   Shutdown();
}

void TransmittedLight::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, g_LeicaTransmittedLight);
}

int TransmittedLight::Initialize()
{
	if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this shutter exists:
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

   // Set timer for Busy signal
   changedTime_ = GetCurrentMMTime();

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &TransmittedLight::OnState);
   ret = CreateProperty(MM::g_Keyword_State, state_ ? "1" : "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   pAct = new CPropertyAction (this, &TransmittedLight::OnLevel);
   ret = CreateProperty(g_LevelProp, "1", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   SetPropertyLimits(g_LevelProp, 0.0, 255.0);

   // Manual control
   pAct = new CPropertyAction(this, &TransmittedLight::OnManual);
   ret = CreateProperty("Control", "Manual", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue("Control", "Computer");
   AddAllowedValue("Control", "Manual");

   // set TL to known initial state: shutter=closed, level = 0
   level_ = 0;
   state_ = false;
   SetProperty(g_LevelProp, "0");
   SetProperty(MM::g_Keyword_State, "0");
	ret = g_ScopeInterface.SetTransmittedLightShutterPosition(*this, *GetCoreCallback(), 0);
	if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int TransmittedLight::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool TransmittedLight::Busy()
{
   bool model_busy;
   int ret = g_ScopeModel.TransmittedLight_.GetBusy(model_busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   bool timer_busy = (interval < delay);

   return model_busy || timer_busy;
}

int TransmittedLight::OnState(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
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
         return SetOpen(true);	
      }
      else
      {
         return SetOpen(false);
      }
   }
	return DEVICE_OK;
}

int TransmittedLight::OnLevel(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // checking the actual level of the lamp does not work properly, i.e.
      // it always returns "1" regardless
      // so we are just returning state that we assume is set
     // if (state_)
     //{
     //    int val;
     //    int ret = g_ScopeInterface.GetTransmittedLightShutterPosition(*this, *GetCoreCallback(), val);
     //    level_ = val;
     //    if (ret != DEVICE_OK)
     //       return ret;
     // }
      pProp->Set(level_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(level_);

      // apply level is shutter is open
      if (state_)
      {
         // Set timer for Busy signal
         changedTime_ = GetCurrentMMTime();
         int ret = g_ScopeInterface.SetTransmittedLightShutterPosition(*this, *GetCoreCallback(), (int)level_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
	return DEVICE_OK;
}

int TransmittedLight::OnManual(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int manual = 0;
      g_ScopeInterface.GetTransmittedLightManual(*this, *GetCoreCallback(), manual);
      if (manual == 0)
      {
         pProp->Set("Computer");
      }
      else
      {
         pProp->Set("Manual");
      }
   }
   else if (eAct == MM::AfterSet)
   {
      std::string tmp = "";
      pProp->Get(tmp);

      int manual = 0;
      if (tmp == "Manual")
         manual = 1;
      int ret = g_ScopeInterface.SetTransmittedLightManual(*this, *GetCoreCallback(), manual);
      if (ret != DEVICE_OK)
         return ret;
   }
	return DEVICE_OK;
}

int TransmittedLight::SetOpen(bool open)
{
   if (open && !state_)
   {
      changedTime_ = GetCurrentMMTime();
      // shutter opening
	   int ret = g_ScopeInterface.SetTransmittedLightShutterPosition(*this, *GetCoreCallback(), (int)level_);
	   if (ret != DEVICE_OK)
		   return ret;
   }
   else if (!open && state_)
   {
      changedTime_ = GetCurrentMMTime();
      // shutter closing
      int ret = g_ScopeInterface.SetTransmittedLightShutterPosition(*this, *GetCoreCallback(), 0);
	   if (ret != DEVICE_OK)
		   return ret;
   }

   // apply current state
   state_ = open;

   return DEVICE_OK;
}

int TransmittedLight::GetOpen(bool &open)
{
   open = state_;
   return DEVICE_OK;
}

int TransmittedLight::Fire(double)
{   return DEVICE_UNSUPPORTED_COMMAND;  
}


///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// AFC
//
AFC::AFC() :
   initialized_(false),    
   name_(g_LeicaAFC),
   timeOut_(5000),
   fullFocusTime_(300),
   lockThreshold_(3)
{

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_LeicaAFC, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Leica Adaptive Focus Control (Hardware autofocus)", MM::String, true);
 
   
}

AFC::~AFC() 
{
   if (initialized_) {
      Shutdown();
   }
}

int AFC::Initialize() 
{
   int ret = DEVICE_OK;
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this shutter exists:
   if (!g_ScopeModel.IsDeviceAvailable(g_AFC))
      return ERR_MODULE_NOT_FOUND;

      // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &AFC::OnDichroicMirrorPosition);
   ret = CreateProperty("DichroicMirrorIn", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("DichroicMirrorIn", "0", 0);
   AddAllowedValue("DichroicMirrorIn", "1", 1);
   if (ret != DEVICE_OK)
      return ret;

   CPropertyAction* pAct2 = new CPropertyAction(this, &AFC::OnFullFocusTime);
   ret = CreateProperty("FullFocusTime", "300", MM::Integer, false, pAct2);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("FullFocusTime", "0", 0);
   AddAllowedValue("FullFocusTime", "100", 100);
   AddAllowedValue("FullFocusTime", "200", 200);
   AddAllowedValue("FullFocusTime", "300", 300);
   AddAllowedValue("FullFocusTime", "500", 500);
   AddAllowedValue("FullFocusTime", "700", 700);
   AddAllowedValue("FullFocusTime", "1000", 1000);

   pAct = new CPropertyAction(this, &AFC::OnOffset);
   ret = CreateProperty("Offset", "0.0", MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &AFC::OnLockThreshold);
   ret = CreateProperty("LockThreshold","1.0",MM::Float,false,pAct);
  
   pAct = new CPropertyAction(this, &AFC::OnLEDIntensity);
   ret = CreateProperty("LEDIntensity","200",MM::Integer,false,pAct);
   SetPropertyLimits("LEDIntensity",0,255);
   initialized_ = true;
   return 0;
}

int AFC::Shutdown() 
{
   return 0;
}

void AFC::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_LeicaAFC);
}

bool AFC::Busy() 
{
   bool busy;
   g_ScopeModel.afc_.GetBusy(busy);
   return busy;
}

int AFC::SetContinuousFocusing(bool state) {
   int ret = g_ScopeInterface.SetAFCMode(*this, *GetCoreCallback(), state);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

int AFC::GetContinuousFocusing(bool& state) {
   return g_ScopeModel.afc_.GetMode(state);
}

int AFC::GetCurrentFocusScore(double& score){
   //std::ostringstream command;
   //std::string answer;
   
   int ret;
   ret = g_ScopeInterface.GetAFCFocusScore(*this,*GetCoreCallback());
   bool busy = true;
   while (busy) {
		g_ScopeModel.afc_.GetBusy(busy);
		CDeviceUtils::SleepMs(10);
   }
   ret = g_ScopeModel.afc_.GetScore(score);
   if (ret != DEVICE_OK)
     return ret;
   return ret;
}
bool AFC::IsContinuousFocusLocked() {
   int topColor, bottomColor;
   int ret;
   ret = g_ScopeModel.afc_.GetLEDColors(topColor, bottomColor);
   if (ret != DEVICE_OK)
      return false;
   if (bottomColor == 2 /* green */) {
       return true;
    } else {
       return false;
   }
  /* double score;
   ret = GetCurrentFocusScore(score);
   if (ret != DEVICE_OK)
      return false;

   if (abs(score)<lockThreshold_){
	   return true;
   }
   else{
	   return false;
   }
*/
}

int AFC::FullFocus() {
   int ret = SetContinuousFocusing(true);
   if (ret != DEVICE_OK)
      return ret;
   int time = 0;
   int delta = 10;
   for (int time = 0;
        !IsContinuousFocusLocked() && (time < timeOut_);
        time += delta) {
        CDeviceUtils::SleepMs(delta);
   }

   if (time > timeOut_) {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "Autofocus Lock Timeout");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
   CDeviceUtils::SleepMs(fullFocusTime_);
   ret = SetContinuousFocusing(false);
   return ret;
}

int AFC::IncrementalFocus() {
   return FullFocus();
}

int AFC::GetLEDIntensity(int &intensity){ 
   int ret = g_ScopeInterface.GetAFCLEDIntensity(*this,*GetCoreCallback());
   if (ret != DEVICE_OK) {
      return ret;
   }
   bool busy = true;
   while (busy) {
		g_ScopeModel.afc_.GetBusy(busy);
		CDeviceUtils::SleepMs(10);
   }
	return g_ScopeModel.afc_.GetLEDIntensity(intensity);
}

int AFC::SetLEDIntensity(int intensity){
	return g_ScopeInterface.SetAFCLEDIntensity(*this, *GetCoreCallback(),intensity);
}

int AFC::GetOffset(double &offset) {
   return g_ScopeModel.afc_.GetOffset(offset);
}

int AFC::SetOffset(double offset) {
   return g_ScopeInterface.SetAFCOffset(*this, *GetCoreCallback(), offset);;
}
//int AFC::GetLockThreshold(double &threshold){
//	return g_ScopeModel.afc_.GetThreshold(threshold);
//}
//int AFC::SetLockThreshold(double threshold){
//	return g_ScopeModel.afc_.SetThreshold(threshold);
//}
///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int AFC::OnDichroicMirrorPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = g_ScopeModel.afc_.GetPosition(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      return g_ScopeInterface.SetAFCDichroicMirrorPosition(*this, *GetCoreCallback(), (int) pos);
   }
   return DEVICE_OK;
}

int AFC::OnFullFocusTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long) fullFocusTime_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(fullFocusTime_);
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int AFC::OnLockThreshold(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(lockThreshold_);
	
   }
   else if (eAct == MM::AfterSet)
   {
      
      pProp->Get(lockThreshold_);
	 
      return DEVICE_OK;
   }

   return DEVICE_OK;
}

int AFC::OnLEDIntensity(MM::PropertyBase* pProp,MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  int intensity;
	  int ret = GetLEDIntensity(intensity);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) intensity);
	
   }
   else if (eAct == MM::AfterSet)
   {
      
      pProp->Get(LEDIntensity_);
	  return SetLEDIntensity((int) LEDIntensity_);
   }

   return DEVICE_OK;

}

int AFC::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double offset;
      int ret = GetOffset(offset);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(offset);
   }
   else if (eAct == MM::AfterSet)
   {
      double offset;
      pProp->Get(offset);
      return SetOffset(offset);
   }

   return DEVICE_OK;
}


//
// AFCOffset
//

AFCOffset::AFCOffset() :
   initialized_(false),
   name_(g_LeicaAFCOffset)
{
   CreateProperty(MM::g_Keyword_Name, g_LeicaAFCOffset, MM::String, true);
   CreateProperty(MM::g_Keyword_Description, "Leica Adaptive Focus Control Offset",
         MM::String, true);
}

AFCOffset::~AFCOffset()
{
   Shutdown();
}

int AFCOffset::Initialize()
{
   int ret = DEVICE_OK;
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;

   if (!g_ScopeModel.IsDeviceAvailable(g_AFC))
      return ERR_MODULE_NOT_FOUND;

   initialized_ = true;
   return DEVICE_OK;
}

int AFCOffset::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   // Nothing to shut down

   return DEVICE_OK;
}

void AFCOffset::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaAFCOffset);
}

bool AFCOffset::Busy()
{
   bool busy;
   g_ScopeModel.afc_.GetBusy(busy);
   return busy;
}

int AFCOffset::GetPositionUm(double& offset)
{
   return g_ScopeModel.afc_.GetOffset(offset);
}

int AFCOffset::SetPositionUm(double offset)
{
   return g_ScopeInterface.SetAFCOffset(*this, *GetCoreCallback(), offset);
}

int AFCOffset::GetPositionSteps(long& steps)
{
   // Arbitrarily define 1 step = 1 nm
   double offset = 0.0;
   int ret = g_ScopeModel.afc_.GetOffset(offset);
   if (ret == DEVICE_OK)
      steps = static_cast<long>(1000.0 * offset);
   return ret;
}

int AFCOffset::SetPositionSteps(long steps)
{
   // 1 step = 1 nm
   double offset = static_cast<double>(steps) / 1000.0;
   return g_ScopeInterface.SetAFCOffset(*this, *GetCoreCallback(), offset);
}


//
// SidePort
//

SidePort::SidePort():
   numPos_(3),
   initialized_ (false),
   name_("Side Port"),
   description_("Side Port")
{
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Leica Scope is not initialized.  It is needed for this port to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this port");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This port is not installed in this Leica microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

}

SidePort::~SidePort()
{
   Shutdown();
}

void SidePort::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_LeicaSidePort);
}

int SidePort::Initialize()
{
   if (!g_ScopeInterface.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   int ret = DEVICE_OK;
   if (!g_ScopeInterface.IsInitialized())
      ret = g_ScopeInterface.Initialize(*this, *GetCoreCallback());
   if (ret != DEVICE_OK)
      return ret;
   
   // check if this turret exists:
	if (! g_ScopeModel.IsDeviceAvailable(::g_Side_Port))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   //state

   // create property
   int maxPos;
	ret = g_ScopeModel.sidePort_.GetMaxPosition(maxPos);
   if (ret != DEVICE_OK)
      return ret;
	int minPos ;
	g_ScopeModel.sidePort_.GetMinPosition(minPos);
   numPos_ = maxPos-minPos+1;


   CPropertyAction* pAct = new CPropertyAction (this, &SidePort::OnState);
	(void)CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);


   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   (void)CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   

	std::ostringstream dmess;
	dmess << "minPos " << minPos << " numPos_ " << numPos_ << " labels: ";

   for (unsigned i=0; i < numPos_; ++i)
   {
	   std::ostringstream os;
      os << i+minPos ;
		// better name for this would be SetStateLabel
      SetPositionLabel((long)i,  os.str().c_str());
		dmess << os.str();
   }
	LogMessage(dmess.str().c_str(),true);

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;
   
	initialized_ = true;
   return DEVICE_OK;
}

int SidePort::Shutdown()
{
   if (initialized_) 
      initialized_ = false;
   return DEVICE_OK;
}

bool SidePort::Busy()
{
   bool busy;
   int ret = g_ScopeModel.sidePort_.GetBusy(busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////
int SidePort::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      int pos;
		// 1 - based position
      ret = g_ScopeModel.sidePort_.GetPosition(pos);
		std::ostringstream o;
		o<<" in OnState BeforeGet " << pos;
		LogMessage(o.str().c_str(),true);
      ostringstream os;
		// 0 - based 'state'
      pProp->Set((long)(pos-1));
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
		std::ostringstream o;
		o<<" in OnState AfterSet " << pos;
		LogMessage(o.str().c_str(),true);
      ret = g_ScopeInterface.SetSidePortPosition(*this, *GetCoreCallback(), (int)(pos+1));

   }
   return ret;
}
