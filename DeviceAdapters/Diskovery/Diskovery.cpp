/////////////////////////////////////////////////////////////////////////////
// FILE:       Diskovery.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Andor/Spectral Diskovery Device adapter
//                
// AUTHOR: Nico Stuurman,  06/31/2015
//
// COPYRIGHT:  Regents of the University of California, 2015
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

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Diskovery.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Diskovery device
const char* g_DiskoveryHub = "Diskovery-Hub";
const char* g_DiskoverySD = "Diskovery-Disk-Position";
const char* g_DiskoveryWF = "Diskovery-Illumination-Size";
const char* g_DiskoveryTIRF = "Diskovery-TIRF-Position";
const char* g_DiskoveryIris = "Diskovery-Objective-Select";
const char* g_DiskoveryFilterW = "Diskovery-Filter-W";
const char* g_DiskoveryFilterT = "Diskovery-Filter-T";

// Constants for the TIRF device
const char* g_TubeLens = "Tube Lens";
const char* g_Leica = "Leica(200mm)";
const char* g_Nikon = "Nikon(200mm)";
const char* g_Olympus = "Olympus(180mm)";
const char* g_Zeiss = "Zeiss(160mm)";
///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DiskoveryHub, MM::HubDevice, "Diskovery Hub (required)");
   RegisterDevice(g_DiskoverySD, MM::StateDevice, "Diskovery Spinning Disk Position");
   RegisterDevice(g_DiskoveryWF, MM::StateDevice, "Diskovery Illumination Size");
   RegisterDevice(g_DiskoveryTIRF, MM::StateDevice, "Diskovery TIRF Position");
   RegisterDevice(g_DiskoveryIris, MM::StateDevice, "Diskovery Objective Select");
   RegisterDevice(g_DiskoveryFilterW, MM::StateDevice, "Diskovery Filter W");
   RegisterDevice(g_DiskoveryFilterT, MM::StateDevice, "Diskovery Filter T");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_DiskoveryHub) == 0)
   {
        return new DiskoveryHub();
   }
   if (strcmp(deviceName, g_DiskoverySD) == 0)
   {
        return new DiskoveryStateDev(g_DiskoverySD, g_DiskoverySD, SD);
   }
   if (strcmp(deviceName, g_DiskoveryWF) == 0)
   {
        return new DiskoveryStateDev(g_DiskoveryWF, g_DiskoveryWF, WF);
   }
   if (strcmp(deviceName, g_DiskoveryTIRF) == 0)
   {
        return new DiskoveryStateDev(g_DiskoveryTIRF, g_DiskoveryTIRF, TIRF);
   }
   if (strcmp(deviceName, g_DiskoveryIris) == 0)
   {
        return new DiskoveryStateDev(g_DiskoveryIris, g_DiskoveryIris, IRIS);
   }
   if (strcmp(deviceName, g_DiskoveryFilterW) == 0)
   {
        return new DiskoveryStateDev(g_DiskoveryFilterW, g_DiskoveryFilterW, FILTERW);
   }
   if (strcmp(deviceName, g_DiskoveryFilterT) == 0)
   {
        return new DiskoveryStateDev(g_DiskoveryFilterT, g_DiskoveryFilterT, FILTERT);
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// DiskoveryHub
//
DiskoveryHub::DiskoveryHub() :
   port_("Undefined"),                                                       
   model_(0),
   listener_(0),
   commander_(0),
   initialized_(false)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DiskoveryHub, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Spinning Disk Confocal and TIRF module", MM::String, true);
 
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &DiskoveryHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

DiskoveryHub::~DiskoveryHub() 
{
   Shutdown();
}

int DiskoveryHub::Initialize() 
{
   // The Discovery reboots when the USB-serial connection is first made
   // (usually the first time we connect), and it takes up to 3 minutes.
   // Keep retrying every 10 seconds, up to 5 minutes.
   const long sleepMs = 10000;
   bool present = false;
   for (int i = 0; i < (5 * 60 / 10); ++i)
   {
      int err = IsControllerPresent(port_, present);
      if (err != DEVICE_OK)
         return err;
      if (present)
         break;

      CDeviceUtils::SleepMs(sleepMs);
   }
   if (!present) {
      return DEVICE_NOT_CONNECTED;
   }

   model_ = new DiskoveryModel(this, *GetCoreCallback());
   listener_ = new DiskoveryListener(*this, *GetCoreCallback(), port_, model_);
   commander_ = new DiskoveryCommander(*this, *GetCoreCallback(), port_, model_);
   listener_->Start();
   RETURN_ON_MM_ERROR( commander_->Initialize() );
   RETURN_ON_MM_ERROR( commander_->CheckCapabilities() );

   // Create properties storing information from the device 
  
   // Hardware version
   CPropertyAction *pAct = new CPropertyAction (this, &DiskoveryHub::OnHardwareVersion);
   int nRet = CreateStringProperty(model_->hardwareVersionProp_, 
         model_->GetHardwareVersion().c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Firmware version
   pAct = new CPropertyAction (this, &DiskoveryHub::OnFirmwareVersion);
   nRet = CreateStringProperty(model_->firmwareVersionProp_, 
         model_->GetFirmwareVersion().c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Manufacturing date
   pAct = new CPropertyAction (this, &DiskoveryHub::OnManufacturingDate);
   nRet = CreateStringProperty(model_->manufacturingDateProp_, 
         "", true, pAct);

   // Serial Number
   pAct = new CPropertyAction (this, &DiskoveryHub::OnSerialNumber);
   nRet = CreateStringProperty(model_->serialNumberProp_, 
         model_->GetSerialNumber().c_str(), true, pAct);

   // motor running
   pAct = new CPropertyAction(this, &DiskoveryHub::OnMotorRunning);
   nRet = CreateIntegerProperty(model_->motorRunningProp_, 
         model_->GetMotorRunningSD(), false, pAct);
   SetPropertyLimits(model_->motorRunningProp_, 0, 1);

   initialized_ = true;
   return DEVICE_OK;
}

int DiskoveryHub::Shutdown() 
{
   if (listener_ != 0)
   {
      // speed up exciting by sending the stop signal
      // and sending a query command to the diskovery so that the
      // listener can exit
      listener_->Stop();
      commander_->GetProductModel();
      delete(listener_);
      listener_ = 0;
   }
   if (commander_ != 0)
   {
      delete(commander_);
      commander_ = 0;
   }
   if (model_ != 0)
   {
      delete(model_);
      model_ = 0;
   }
   initialized_ = false;
   return DEVICE_OK;
}

void DiskoveryHub::GetName (char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DiskoveryHub);
}

bool DiskoveryHub::Busy() 
{
   if (model_ != 0)
   {
      return model_->GetBusy();
   }
   return false;
}

bool DiskoveryHub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus DiskoveryHub::DetectDevice(void )
{
   char answerTO[MM::MaxStrLength];

   if (initialized_)
      return MM::CanCommunicate;

   MM::DeviceDetectionStatus result = MM::Misconfigured;
   
   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {                                                       
         *its = (char)tolower(*its);                          
      }                                                       
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      { 
         result = MM::CanNotCommunicate;
         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Hardware");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "100.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         // Attempt to communicate through the port
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         PurgeComPort(port_.c_str());
         bool present = false;
         int ret = IsControllerPresent(port_, present);
         if (ret != DEVICE_OK)
            return result;
         if (present)
         {
            result = MM::CanCommunicate;
            // set the timeout to a value higher than the heartbeat frequency
            // so that the logs will not overflow with errors
            GetCoreCallback()->SetDeviceProperty(port_.c_str(), 
                  "AnswerTimeout", "6000");
         } else
         {
            GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
         }
         pS->Shutdown();
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}

/**
 * Simple way to see if the Diskovery is attached to the serial port
 */
int DiskoveryHub::IsControllerPresent(const std::string port, bool& present)
{
   present = false;
   // device seems to generate some garbage on opening port
   CDeviceUtils::SleepMs(100);
   RETURN_ON_MM_ERROR( PurgeComPort(port.c_str()) );
   // strange, this sleep really helps successful device detection!
   CDeviceUtils::SleepMs(100);
   RETURN_ON_MM_ERROR( SendSerialCommand(port.c_str(), "Q:PRODUCT_MODEL", 
            "\r\n") );
   std::string answer;
   RETURN_ON_MM_ERROR( GetSerialAnswer(port.c_str(), "\r\n", answer) );
   if (answer.empty()) // We should get at least garbage if attached
      return DEVICE_NOT_CONNECTED;
   while (answer == "STATUS=1")
      RETURN_ON_MM_ERROR( GetSerialAnswer(port.c_str(), "\r\n", answer) );
   if (answer == "PRODUCT_MODEL=DISKOVERY")
      present = true;

   return DEVICE_OK;
}

int DiskoveryHub::DetectInstalledDevices()
{
   if (MM::CanCommunicate == DetectDevice() )
   {
      // there is a controller, now figure out which devices it has
      // this code can only work if the Hub::Initialize function 
      // has been called before.  Check to avoid a crash
      if (commander_ == 0)
         return DEVICE_ERR;
      
     RETURN_ON_MM_ERROR( commander_->CheckCapabilities() );

      std::vector<std::string> peripherals;
      peripherals.clear();
      // TODO: actually detect devices
      if (model_->GetHasSD())
         peripherals.push_back(g_DiskoverySD);
      // NOTE: the manual states that the controller should have both WF_X and 
      // WF_Y.  However, the demo unit has only WF_X, so only require that...
      // if (model_->GetHasWFX() && model_->GetHasWFY())
      if (model_->GetHasWFX())
         peripherals.push_back(g_DiskoveryWF);
      if (model_->GetHasROT() && model_->GetHasLIN() && 
            model_->GetHasP1() && model_->GetHasP2())
         peripherals.push_back(g_DiskoveryTIRF);
      if (model_->GetHasIRIS())
         peripherals.push_back(g_DiskoveryIris);
      if (model_->GetHasFilterW())
         peripherals.push_back(g_DiskoveryFilterW);
      if (model_->GetHasFilterT())
         peripherals.push_back(g_DiskoveryFilterT);
      for (size_t i=0; i < peripherals.size(); i++) 
      {
         MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
         if (pDev)
         {
            AddInstalledDevice(pDev);
         }
      }
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int DiskoveryHub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(port_.c_str());
   } else if (eAct == MM::AfterSet) {
      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int DiskoveryHub::OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetHardwareVersion().c_str());
   }
   
   return DEVICE_OK;
}

int DiskoveryHub::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetFirmwareVersion().c_str());
   }
   
   return DEVICE_OK;
}

int DiskoveryHub::OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) {
      if (manufacturingDate_ == "") 
      {
         std::ostringstream oss3;
         oss3 << "20" << model_->GetManufactureYear() << "-" 
               << model_->GetManufactureMonth() << "-" 
               << model_->GetManufactureDay();
         manufacturingDate_ = oss3.str();
      }
      pProp->Set(manufacturingDate_.c_str());
   }
   
   return DEVICE_OK;
}

int DiskoveryHub::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetSerialNumber().c_str());
   }
   
   return DEVICE_OK;
}

int DiskoveryHub::OnMotorRunning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set((long) model_->GetMotorRunningSD() );
   }
   else if (eAct == MM::AfterSet) {
      long tmp;
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR( commander_->SetMotorRunningSD( (uint16_t) tmp) );
   }
   
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// DiskoverStateDev
//
DiskoveryStateDev::DiskoveryStateDev(
      const std::string devName, const std::string description, const DevType devType)  :
   wavelength1_(0),
   wavelength2_(0),
   tubeLensFocalLength_(200),
   depth_(100.0),
   na_(1.45),
   ri_(1.33),
   exitTIRF_(false),
   devName_(devName),
   devType_ (devType),
   initialized_(false),
   hub_(0)
{
   firstPos_ = 1;
   numPos_ = 4;
   if (devType_ == SD || devType == TIRF) 
      numPos_ = 5;
   if (devType_ == TIRF) 
      firstPos_ = 0;

   InitializeDefaultErrorMessages();

   // Description
   int ret = CreateProperty(MM::g_Keyword_Description, description.c_str(), MM::String, true);
   assert(DEVICE_OK == ret);

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, devName_.c_str(), MM::String, true);
   assert(DEVICE_OK == ret);

   // parent ID display
   CreateHubIDProperty();

   if (devType == TIRF)
   {
       CPropertyAction* pAct = new CPropertyAction(this, &DiskoveryStateDev::OnTubeLensFocalLength);
       CreateProperty(g_TubeLens, g_Leica, MM::String, false, pAct, true);
       AddAllowedValue(g_TubeLens, g_Leica);
       AddAllowedValue(g_TubeLens, g_Nikon);
       AddAllowedValue(g_TubeLens, g_Olympus);
       AddAllowedValue(g_TubeLens, g_Zeiss);
   }
}

DiskoveryStateDev::~DiskoveryStateDev() 
{
   Shutdown();
}

int DiskoveryStateDev::Shutdown()
{
   initialized_ = false;
   hub_ = 0;
   return DEVICE_OK;
}

void DiskoveryStateDev::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, devName_.c_str());
}

int DiskoveryStateDev::Initialize() 
{
   hub_ = static_cast<DiskoveryHub*>(GetParentHub());
   if (!hub_)
      return DEVICE_COMM_HUB_MISSING;
   char hubLabel[MM::MaxStrLength];
   hub_->GetLabel(hubLabel);
   SetParentID(hubLabel); // for backward compatibility (delete?)

   for (uint16_t i = 0; i < numPos_; i++) {
      ostringstream os;
      os << "Preset-" << (i + firstPos_);
      // set default label
      const char* label = os.str().c_str();
      if (devType_ == WF) 
      {
         label = hub_->GetModel()->GetButtonWFLabel(i + firstPos_);
      } else if (devType_ == IRIS) 
      {
         label = hub_->GetModel()->GetButtonIrisLabel(i + firstPos_);
         uint16_t val;
         std::stringstream ss;
         std::string strLabel(label);
         ss << strLabel.substr(0, strLabel.size() - 1);
         ss >> val;
         irisValues_.push_back(val);
      } else if (devType_ == FILTERW) 
      {
         label = hub_->GetModel()->GetButtonFilterWLabel(i + firstPos_);
      } else if (devType_ == FILTERT) 
      {
         label = hub_->GetModel()->GetButtonFilterTLabel(i + firstPos_);
      } else if (devType_ == SD) {
         if (i == 0)
            label = "Disk Out";
         else if (i == 1) 
            label = "Exchange";
         else
            label = hub_->GetModel()->GetDiskLabel(i - 2 + firstPos_);
      } else if (devType_ == TIRF) {
         if (i == 0)
            label = "Disabled";
         else 
         {
            std::ostringstream os;
            os << "OM " << i; 
            label = os.str().c_str();;
         }
      }
      SetPositionLabel(i, label);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &DiskoveryStateDev::OnState);
   int nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;
   for (unsigned i = 0; i < numPos_; i++)
   {
      std::ostringstream os;
      os << i;
      AddAllowedValue(MM::g_Keyword_State, os.str().c_str());
   }

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

    // TIRF rotational and linear stages moving prisms
    if (devType_ == TIRF)
    {
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnPositionRot);
       RETURN_ON_MM_ERROR(  CreateProperty("PositionRot", "0", MM::Integer, false, pAct)) ;
       RETURN_ON_MM_ERROR( SetPropertyLimits("PositionRot", 
                hub_->GetModel()->GetMinRotation(), hub_->GetModel()->GetMaxRotation()) );
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnPositionLin);
       RETURN_ON_MM_ERROR ( CreateProperty("PositionLin", "0", MM::Integer, false, pAct) );
       RETURN_ON_MM_ERROR ( SetPropertyLimits("PositionLin", 
                hub_->GetModel()->GetMinLinear(), hub_->GetModel()->GetMaxLinear()) );
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnWavelength1);
       RETURN_ON_MM_ERROR ( CreateIntegerProperty("Wavelength1", 0, false, pAct) );
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnWavelength2);
       RETURN_ON_MM_ERROR ( CreateIntegerProperty("Wavelength2", 0, false, pAct) );
       AddAllowedValue("Wavelength2", CDeviceUtils::ConvertToString(0));
       bool wavelengthSet = false;
       for (uint16_t i = 0; i < 7; i++) 
       {
          if (hub_->GetModel()->GetLineEnabled(i)) 
          {
             uint16_t wavelength = hub_->GetModel()->GetLineWavelength(i);
             const char* val = CDeviceUtils::ConvertToString(wavelength);
             AddAllowedValue("Wavelength1", val);
             // Set Wavelength1 to the first possible wavelength, leave #2 at 0
             if (!wavelengthSet)
             {
                SetProperty("Wavelength1", val);
                wavelengthSet = true;
             }
             AddAllowedValue("Wavelength2", val);
          }
       }
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnExitTIRF);
       RETURN_ON_MM_ERROR ( CreateProperty("ExitTIRF", "No", MM::String, false, pAct) );
       AddAllowedValue("ExitTIRF", "No");
       AddAllowedValue("ExitTIRF", "Yes");
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnDepth);
       RETURN_ON_MM_ERROR ( CreateFloatProperty("TIRFDepth", depth_, false, pAct) );
       RETURN_ON_MM_ERROR ( SetPropertyLimits("TIRFDepth", 0.0, 1000.0) );
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnNA);
       RETURN_ON_MM_ERROR ( CreateFloatProperty("Num. Ap.", na_, false, pAct) );
       RETURN_ON_MM_ERROR ( SetPropertyLimits("Num. Ap.", 1.40, 1.49) );
       pAct = new CPropertyAction(this, &DiskoveryStateDev::OnRI);
       RETURN_ON_MM_ERROR ( CreateFloatProperty("Refr. Index", ri_, false, pAct) );
       RETURN_ON_MM_ERROR ( SetPropertyLimits("Refr. Index", 1.33, 1.40) );

       hub_->GetModel()->SetTubeLensFocalLength(tubeLensFocalLength_);
    }

   // Register our instance for callbacks
   if (devType_ == SD)
      hub_->RegisterSDDevice(this);
   else if (devType_ == WF)
      hub_->RegisterWFDevice(this);
   else if (devType_ == TIRF)
      hub_->RegisterTIRFDevice(this);
   else if (devType_ == IRIS)
      hub_->RegisterIRISDevice(this);
   else if (devType_ == FILTERW)
      hub_->RegisterFILTERWDevice(this);
   else if (devType_ == FILTERT)
      hub_->RegisterFILTERTDevice(this);

   initialized_ = true;

   return DEVICE_OK;
}

bool DiskoveryStateDev::Busy() 
{
   if (hub_ != 0)
   {
      return hub_->Busy();
   }
   return false;
}


int DiskoveryStateDev::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (devType_ == SD)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetSD() - 1) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetSD( (uint16_t) (pos + 1));
      }
   } 
   else if (devType_ == WF)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetWF() - firstPos_) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetWF( (uint16_t) (pos + firstPos_));
      }
   }
   else if (devType_ == TIRF)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetTIRF() - firstPos_) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetTIRF( (uint16_t) (pos + firstPos_));
      }
   }
   else if (devType_ == IRIS)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetIris() - firstPos_) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetIris( (uint16_t) (pos + firstPos_));
      }
   }
   else if (devType_ == FILTERW)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetFilterW() - firstPos_) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetFilterW( (uint16_t) (pos + firstPos_));
      }
   }
   else if (devType_ == FILTERT)
   {
      if (eAct == MM::BeforeGet)
      {
         pProp->Set( (long) (hub_->GetModel()->GetPresetFilterT() - firstPos_) );
      }
      else if (eAct == MM::AfterSet)
      {
         long pos;
         pProp->Get(pos);
         hub_->GetCommander()->SetPresetFilterT( (uint16_t) (pos + firstPos_));
      }
   }

   return DEVICE_OK;
}

int DiskoveryStateDev::OnPositionRot(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set( (long) (hub_->GetModel()->GetPositionRot() ) );
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      hub_->GetCommander()->SetPositionRot( (uint32_t) (pos) );
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnPositionLin(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long pos = hub_->GetModel()->GetPositionLin();
      pProp->Set( pos );
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      hub_->GetCommander()->SetPositionLin( (uint32_t) (pos) );
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnWavelength1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set( wavelength1_ );
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get( wavelength1_);
      hub_->GetModel()->SetTIRFWavelength1(wavelength1_);
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnWavelength2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set( wavelength2_ );
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get( wavelength2_);
      hub_->GetModel()->SetTIRFWavelength2(wavelength2_);
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnTubeLensFocalLength(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      std::string tubeLens;
      pProp->Get( tubeLens );
      if (tubeLens == g_Leica)
         tubeLensFocalLength_ = 200;
      else if (tubeLens == g_Nikon)
         tubeLensFocalLength_ = 200;
      else if (tubeLens == g_Olympus)
         tubeLensFocalLength_ = 180;
      else if (tubeLens == g_Zeiss)
         tubeLensFocalLength_ = 150;

   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnExitTIRF(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      exitTIRF_ = hub_->GetModel()->GetExitTIRF();
      std::string msg = "No";
      if (exitTIRF_)
         msg = "Yes";
      pProp->Set( msg.c_str() );
   }
   else if (eAct == MM::AfterSet)
   {
      std::string msg;
      pProp->Get( msg );
      if (msg == "Yes")
         exitTIRF_ = true;
      else
         exitTIRF_ = false;
      hub_->GetModel()->SetExitTIRF(exitTIRF_);
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      depth_ = hub_->GetModel()->GetDepth();
      pProp->Set( depth_ );
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get( depth_ );
      hub_->GetModel()->SetDepth( depth_ );
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnNA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      na_ = hub_->GetModel()->GetNA();
      pProp->Set( na_ );
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get( na_);
      hub_->GetModel()->SetNA(na_);
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}

int DiskoveryStateDev::OnRI(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      ri_ = hub_->GetModel()->GetRI();
      pProp->Set( ri_ );
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get( ri_);
      hub_->GetModel()->SetRI(ri_);
      if (initialized_)
         hub_->GetCommander()->SendTIRFGOTO();
   }
   return DEVICE_OK;
}
