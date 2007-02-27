///////////////////////////////////////////////////////////////////////////////
// FILE:          ZeissMTB.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ZEISS MTB adapter
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/30/2005
//
// CVS:           $Id$
//
#include "StdAfx.h"

#ifdef WIN32
   #define snprintf _snprintf 
#endif

#include "ZeissMTB.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

// z-stage
const char* g_FocusName = "Focus";

// revolvers
const char* g_ReflectorName = "Reflector";
const char* g_ObjectiveName = "Objective";
const char* g_OptovarName = "Optovar";
const char* g_CondenserName = "Condenser";
const char* g_TubelensName = "Tubelens";
const char* g_RShutterName = "RShutter";
const char* g_ShutterName = "Shutter";
const char* g_SideportName = "Sideport";
const char* g_BaseportName = "Baseport";
const char* g_HalogenLampName = "Halogen";
const char* g_AttoArc2LampName = "AttoArc2";
const char* g_FluoArcLampName = "FluoArc";
const char* g_StandName = "Stand";

// global instance of the MTB library
CMTB_Data g_mtbData;

using namespace std;

// global constants
const char* g_MTBName = "MTBName";


//#ifdef WIN32
//   BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
//                          DWORD  ul_reason_for_call, 
//                          LPVOID /*lpReserved*/
//		   			 )
//   {
//   	switch (ul_reason_for_call)
//   	{
//   	   case DLL_PROCESS_ATTACH:
//            AfxOleInit();
//            g_mtbData.Connect_MTB();
//         break;
//   	
//         case DLL_THREAD_ATTACH:
//         break;
//
//   	   case DLL_THREAD_DETACH:
//         break;
//
//   	   case DLL_PROCESS_DETACH:
//            g_mtbData.Disconnect_MTB();
//         break;
//   	}
//      
//      return TRUE;
//   }
//#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ReflectorName);
   AddAvailableDeviceName(g_ObjectiveName);
   AddAvailableDeviceName(g_OptovarName);
   AddAvailableDeviceName(g_TubelensName);
   AddAvailableDeviceName(g_CondenserName);
   AddAvailableDeviceName(g_ShutterName);
   AddAvailableDeviceName(g_FocusName);
   AddAvailableDeviceName(g_StandName);
   AddAvailableDeviceName(g_BaseportName);
   AddAvailableDeviceName(g_SideportName);
   AddAvailableDeviceName(g_HalogenLampName);
   AddAvailableDeviceName(g_AttoArc2LampName);
   AddAvailableDeviceName(g_FluoArcLampName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ReflectorName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_REFL);
      pRev->SetName(g_ReflectorName);
      return pRev;
   }
   else if (strcmp(deviceName, g_ObjectiveName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_OBJ);
      pRev->SetName(g_ObjectiveName);
      return pRev;
   }
   else if (strcmp(deviceName, g_OptovarName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_OPTOVAR);
      pRev->SetName(g_OptovarName);
      return pRev;
   }
   else if (strcmp(deviceName, g_TubelensName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_TUBELENS);
      pRev->SetName(g_TubelensName);
      return pRev;
   }
   else if (strcmp(deviceName, g_CondenserName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_COND);
      pRev->SetName(g_CondenserName);
      return pRev;
   }
   else if (strcmp(deviceName, g_RShutterName) == 0)
   {
      // create shutter as revolver
      Revolver* pRev = new Revolver(MTB_CHG_SHUTTER);
      pRev->SetName(g_RShutterName);
      return pRev;
   }
   else if (strcmp(deviceName, g_ShutterName) == 0)
   {
      // create shutter as shutter
      return new Shutter;
   }
   else if (strcmp(deviceName, g_BaseportName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_BASEPORT);
      pRev->SetName(g_BaseportName);
      return pRev;
   }
   else if (strcmp(deviceName, g_SideportName) == 0)
   {
      // create revolver
      Revolver* pRev = new Revolver(MTB_CHG_SIDEPORT);
      pRev->SetName(g_SideportName);
      return pRev;
   }
   else if (strcmp(deviceName, g_FocusName) == 0)
   {
      // create focusing stage
      return new FocusStage();
   }
   else if (strcmp(deviceName, g_StandName) == 0)
   {
      // create focusing stage
      return new Stand();
   }
   else if (strcmp(deviceName, g_HalogenLampName) == 0)
   {
      Lamp* pLamp = new Lamp(_M_HALOGEN_LAMP);
      pLamp->SetName(g_HalogenLampName);
      return pLamp;
   }
   else if (strcmp(deviceName, g_AttoArc2LampName) == 0)
   {
      // create revolver
      Lamp* pLamp = new Lamp(_M_ATTOARC2_LAMP);
      pLamp->SetName(g_AttoArc2LampName);
      return pLamp;
   }
   else if (strcmp(deviceName, g_FluoArcLampName) == 0)
   {
      Lamp* pLamp = new Lamp(_M_FLUOARC_LAMP);
      pLamp->SetName(g_FluoArcLampName);
      return pLamp;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Revolver implementation
// ~~~~~~~~~~~~~~~~~~~~~~~
// Revolver class is somewhat different from the others beacause the device
// is not 'unique' - i.e. the same class is used for several actual devices
// such as filter wheel, mirror, objective turret, etc.
// By specifying MTB device ID in the constructor, the instantiated object
// will connect and configure itself using the information obtained at
// run-time

Revolver::Revolver(unsigned typeID) : initialized_(false), numPos_(0), name_("Revolver"), type_(typeID)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "MTB not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected by MTB");
}

Revolver::~Revolver()
{
   Shutdown();
}

void Revolver::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Revolver::Busy()
{
   if (g_mtbData.m_connected == 0)
      return false;

//   return g_mtbData.m_revolvers.Busy(type_);
   unsigned pos;
   int ret = g_mtbData.m_revolvers.ReadRevolverPos(type_, &pos);
   if (ret != _E_NO_ERROR)
   {
      TRACE("Revolver reported error %d while checking busy status\n", ret);
      return false; // declare not busy on error
   }

   if (pos <= 0)
      return true;
   else
      return false;
}

int Revolver::Initialize()
{
   // establish link to the MTB software
   if (g_mtbData.m_connected == 0)
   {
      g_mtbData.Connect_MTB();
	  LogMessage("MTB CONNECTED!\n");
   }

   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;
   
   if (!g_mtbData.m_revolvers.m_RevolverType[type_])
      return ERR_TYPE_NOT_DETECTED;

   numPos_ = g_mtbData.m_revolvers.m_RevolverNumberOfPositions[type_];

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss MTB revolver adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // MTB Name
   CString strName;
   g_mtbData.m_revolvers.GetRevolverName(type_, strName);
   ret = CreateProperty(g_MTBName, strName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Revolver::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   CString strPosName;
   // indices are not 0-based ???
   for (unsigned i=0; i<numPos_; i++)
   {
      g_mtbData.m_revolvers.GetRevolverPosName(type_, i+1, strPosName);
      if (DEVICE_DUPLICATE_LABEL == SetPositionLabel(i, strPosName))
      {
         /// make sure that we have unnique labels
         ostringstream os;
         os << strPosName << "-" << i+1;
         SetPositionLabel(i, os.str().c_str());
      }
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Revolver::Shutdown()
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

int Revolver::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      UINT pos;
      int ret = g_mtbData.m_revolvers.ReadRevolverPos(type_, &pos);
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set(max(0L, (long)pos - 1));
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos >= (long)numPos_ || pos < 0)
      {
         // restore current position
         UINT oldPos;
         int ret = g_mtbData.m_revolvers.ReadRevolverPos(type_, &oldPos);
         if (ret != _E_NO_ERROR)
            return ret;
         pProp->Set((long)oldPos - 1); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_mtbData.m_revolvers.SetRevolverPos(type_, (UINT)pos + 1);
      if (ret != _E_NO_ERROR)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// FocusStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

FocusStage::FocusStage() : 
   stepSize_um_(0.0),
   busy_(false),
   initialized_(false),
   lowerLimit_(0.0),
   upperLimit_(20000.0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "MTB not connected with the hardware");
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
   // establish link to the MTB software
   if (g_mtbData.m_connected == 0)
   {
      g_mtbData.Connect_MTB();
	  LogMessage("MTB CONNECTED!\n");
   }

   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;
   
   if (g_mtbData.m_focus.m_FocusType == 0)
      return ERR_TYPE_NOT_DETECTED;

   stepSize_um_ = g_mtbData.m_focus.m_FocusStep;


   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_FocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss MTB focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // MTB Name
   ret = CreateProperty(g_MTBName, g_mtbData.m_focus.m_FocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &FocusStage::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
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
   if (g_mtbData.m_connected == 0)
      return false;

   return g_mtbData.m_focus.Busy();
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
   if (stepSize_um_ == 0.0)
      return DEVICE_UNSUPPORTED_COMMAND;
   return SetPositionUm(steps * stepSize_um_); 
}

int FocusStage::GetPositionSteps(long& steps)
{
   if (stepSize_um_ == 0.0)
      return DEVICE_UNSUPPORTED_COMMAND;
   double posUm;
   int ret = GetPositionUm(posUm);
   if (ret != DEVICE_OK)
      return ret;

   steps = (long)(posUm / stepSize_um_ + 0.5);
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
      double pos;
      int ret = g_mtbData.m_focus.ReadFocusPos(&pos);
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      int ret = g_mtbData.m_focus.MoveFocus(pos);
      if (ret != _E_NO_ERROR)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Stand 
// ~~~~~

Stand::Stand() : 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NOT_CONNECTED, "MTB not connected with the hardware");
}

Stand::~Stand()
{
   Shutdown();
}

void Stand::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_StandName);
}

int Stand::Initialize()
{
   // establish link to the MTB software
   if (g_mtbData.m_connected == 0)
   {
      g_mtbData.Connect_MTB();
	  LogMessage("MTB CONNECTED!\n");
   }

   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;
   
   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_FocusName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss MTB stand adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // LM
   // --
   CPropertyAction* pAct = new CPropertyAction (this, &Stand::OnLightManager);
   ret = CreateProperty("LightManager", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Stand::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Stand::Busy()
{
   return false;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Stand::OnLightManager(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   
   if (eAct == MM::BeforeGet)
   {
      UINT pos;
      int ret = g_mtbData.m_stand.GetAutomat(&pos);
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret = g_mtbData.m_stand.SetAutomat((UINT)pos);
      if (ret != _E_NO_ERROR)
         return ret;
   }
   
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Shutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~

Shutter::Shutter() : initialized_(false), type_(MTB_CHG_SHUTTER)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "MTB not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected by MTB");
}

Shutter::~Shutter()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_ShutterName);
}

bool Shutter::Busy()
{
   if (g_mtbData.m_connected == 0)
      return false;

   return g_mtbData.m_revolvers.Busy(type_);
}

int Shutter::Initialize()
{
   // establish link to the MTB software
   if (g_mtbData.m_connected == 0)
   {
      g_mtbData.Connect_MTB();
	  LogMessage("MTB CONNECTED!\n");
   }

   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;
   
   if (!g_mtbData.m_revolvers.m_RevolverType[type_])
      return ERR_TYPE_NOT_DETECTED;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ShutterName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss MTB shutter adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // MTB Name
   CString strName;
   g_mtbData.m_revolvers.GetRevolverName(type_, strName);
   ret = CreateProperty(g_MTBName, strName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "1");
   AddAllowedValue(MM::g_Keyword_State, "2");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Shutter::SetOpen(bool open)
{
   if (g_mtbData.m_connected == 0)
      return false;

   long pos;
   if (open)
      pos = 2;
   else
      pos = 1;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   if (g_mtbData.m_connected == 0)
      return false;

   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 2 ? open = true : open = false;

   return DEVICE_OK;
}
int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      UINT pos;
      int ret = g_mtbData.m_revolvers.ReadRevolverPos(type_, &pos);
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (!(pos == 1 || pos == 2))
      {
         // restore current position
         UINT oldPos;
         int ret = g_mtbData.m_revolvers.ReadRevolverPos(type_, &oldPos);
         if (ret != _E_NO_ERROR)
            return ret;
         pProp->Set((long)oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_mtbData.m_revolvers.SetRevolverPos(type_, (UINT)pos);
      if (ret != _E_NO_ERROR)
         return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Lamp implementation
// ~~~~~~~~~~~~~~~~~~~

Lamp::Lamp(unsigned typeID) : initialized_(false), name_("Lamp"), type_(typeID),
                              id_(0), openTimeUs_(0)
{
   InitializeDefaultErrorMessages();

   // add custom MTB messages
   SetErrorText(ERR_NOT_CONNECTED, "MTB not connected with the hardware");
   SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
   SetErrorText(ERR_TYPE_NOT_DETECTED, "Device not detected by MTB");
}

Lamp::~Lamp()
{
   Shutdown();
}

void Lamp::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool Lamp::Busy()
{
   if (g_mtbData.m_connected == 0)
      return false;

   unsigned stat;
   int ret = g_mtbData.m_lamps.GetLampStatus(id_, &stat);
   if (ret != _E_NO_ERROR)
   {
      TRACE("Lamp reported error %d while checking busy status\n", ret);
      return false; // declare not busy on error
   }

   long interval = GetClockTicksUs() - openTimeUs_;
   //if (stat <= 0 || interval < GetDelayMs())
   // >> NOTE: if the stat is in the error state the device ramains forever busy!
   if(interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int Lamp::Initialize()
{
   // establish link to the MTB software
   if (g_mtbData.m_connected == 0)
   {
      g_mtbData.Connect_MTB();
   }

   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;

   // check lamps
   bool found = false;
   for(int i=0; i < _PLA_LAST_LAMP; i++)   // for all lamps
      if(g_mtbData.m_lamps.m_LampType[0])   // if lamp exists
         if (g_mtbData.m_lamps.m_LampKind[i] == type_)
         {
            id_ = i;
            found = true;
         }

   if (!found)
      return ERR_TYPE_NOT_DETECTED;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss MTB lamp adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // MTB Name
   CString strName = g_mtbData.m_lamps.m_LampName[id_];
   ret = CreateProperty(g_MTBName, strName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &Lamp::OnOnOff);
   ret = CreateProperty("OnOff", "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Power
   // -----
   pAct = new CPropertyAction (this, &Lamp::OnPower);
   ret = CreateProperty("Power", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Level
   // -----
   /*
   pAct = new CPropertyAction (this, &Lamp::OnLevel);
   ret = CreateProperty("Level", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;
   */

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   openTimeUs_ = GetClockTicksUs();

   return DEVICE_OK;
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
   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;

   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int Lamp::GetOpen(bool& open)
{
   if (g_mtbData.m_connected == 0)
      return ERR_NOT_CONNECTED;

   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int Lamp::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Lamp::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      UINT state;
      int ret = g_mtbData.m_lamps.GetLampStatus(id_, &state);
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set((long)(state & 1) ? 0L : 1L);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      // apply the value
      int ret = g_mtbData.m_lamps.SetLampOnOff(id_, state > 0 ? TRUE : FALSE);
      if (ret != _E_NO_ERROR)
         return ret;
      openTimeUs_ = GetClockTicksUs();
   }

   return DEVICE_OK;
}


int Lamp::OnLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      UINT level;
      int ret = g_mtbData.m_lamps.GetLevel(id_, &level);
         
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set((long)level);
   }
   else if (eAct == MM::AfterSet)
   {
   }

   return DEVICE_OK;
}

int Lamp::OnPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      double pos;
      int ret = g_mtbData.m_lamps.ReadLampPos(id_, &pos);
         
      if (ret != _E_NO_ERROR)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      if (pos > g_mtbData.m_lamps.m_LampMax[id_] || pos < g_mtbData.m_lamps.m_LampMin[id_])
      {
         // restore current position
         double oldPos;
         int ret = g_mtbData.m_lamps.ReadLampPos(id_, &oldPos);
         if (ret != _E_NO_ERROR)
            return ret;
         pProp->Set(oldPos); // revert
         return ERR_UNKNOWN_POSITION;
      }
      // apply the value
      int ret = g_mtbData.m_lamps.SetLampPos(id_, pos);
      if (ret != _E_NO_ERROR)
         return ret;
   }

   return DEVICE_OK;
}
