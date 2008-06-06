///////////////////////////////////////////////////////////////////////////////
// FILE:          DAZStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter to drive Z-stages through a DA device
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 03/28/2008
// COPYRIGHT:     University of California, San Francisco, 2008


#include "DAZStage.h"
#include "../../MMDevice/ModuleInterface.h"

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


const char* g_DeviceNameDAZStage = "DA Z Stage";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameDAZStage, "DA-controlled Z-stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
      return 0;                                                              
                                                                             
   if (strcmp(deviceName, g_DeviceNameDAZStage) == 0)                        
   {                                                                         
      return new DAZStage();
   }                                                                         

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// ZStage implementation
///////////////////////////////////////////////////////////////////////////////
DAZStage::DAZStage() :
   DADeviceName_ (""),
   initialized_ (false),
   minDAVolt_ (0.0),
   maxDAVolt_ (10.0),
   minStageVolt_ (0.0),
   maxStageVolt_ (5.0),
   minStagePos_ (0.0),
   maxStagePos_ (200.0),
   pos_ (0.0),
   originPos_ (0.0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_INVALID_DEVICE_NAME, "Please select a valid DA device");
   SetErrorText(ERR_NO_DA_DEVICE, "No DA Device selected");
   SetErrorText(ERR_VOLT_OUT_OF_RANGE, "The DA Device cannot set the requested voltage");
   SetErrorText(ERR_POS_OUT_OF_RANGE, "The requested position is out of range");
   SetErrorText(ERR_NO_DA_DEVICE_FOUND, "No DA Device loaded");

   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_DeviceNameDAZStage, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "ZStage controlled with voltage provided by a DA board", MM::String, true);

   CPropertyAction* pAct = new CPropertyAction (this, &DAZStage::OnStageMinVolt);      
   CreateProperty("Stage Low Voltage", "0", MM::Float, false, pAct, true);         

   pAct = new CPropertyAction (this, &DAZStage::OnStageMaxVolt);      
   CreateProperty("Stage High Voltage", "5", MM::Float, false, pAct, true);         

   pAct = new CPropertyAction (this, &DAZStage::OnStageMinPos); 
   CreateProperty("Stage Low Position(um)", "0", MM::Float, false, pAct, true); 

   pAct = new CPropertyAction (this, &DAZStage::OnStageMaxPos);      
   CreateProperty("Stage High Position(um)", "200", MM::Float, false, pAct, true);         
}  
 
DAZStage::~DAZStage()
{
}

void DAZStage::GetName(char* Name) const                                       
{                                                                            
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNameDAZStage);                
}                                                                            
                                                                             
int DAZStage::Initialize() 
{
  // get list with available DA devices.   TODO: this is a initialization parameter, which makes it harder for the end-user to set up!
   availableDAs_ = GetLoadedDevicesOfType(MM::SignalIODevice);

   CPropertyAction* pAct = new CPropertyAction (this, &DAZStage::OnDADevice);      
   std::string defaultDA = "Undefined";
   if (availableDAs_.size() >= 1)
      defaultDA = availableDAs_[0];
   CreateProperty("DA Device", defaultDA.c_str(), MM::String, false, pAct, false);         
   if (availableDAs_.size() >= 1)
      SetAllowedValues("DA Device", availableDAs_);
   else
      return ERR_NO_DA_DEVICE_FOUND;

   // This is needed, otherwise DeviceDA_ is not always set resulting in crashes
   // This could lead to strange problems if multiple DA devices are loaded
   SetProperty("DA Device", defaultDA.c_str());

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream tmp;
   tmp << DADevice_;
   LogMessage(tmp.str().c_str());

   if (DADevice_ != 0)
      DADevice_->GetLimits(minDAVolt_, maxDAVolt_);

   if (minStageVolt_ < minDAVolt_)
      return ERR_VOLT_OUT_OF_RANGE;

   originPos_ = minStagePos_;

   initialized_ = true;

   return DEVICE_OK;
}

int DAZStage::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}

bool DAZStage::Busy()
{
   if (DADevice_ != 0)
      return DADevice_->Busy();

   // If we are here, there is a problem.  No way to report it.
   return false;
}

/*
 * Sets the position of the stage in um relative to the position of the origin
 */
int DAZStage::SetPositionUm(double pos)
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   double volt = ( (pos + originPos_) / (maxStagePos_ - minStagePos_)) * (maxStageVolt_ - minStageVolt_);
   if (volt > maxStageVolt_ || volt < minStageVolt_)
      return ERR_POS_OUT_OF_RANGE;

   pos_ = pos;
   return DADevice_->SetSignal(volt);
}

/*
 * Reports the current position of the stage in um relative to the origin
 */
int DAZStage::GetPositionUm(double& pos)
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   double volt;
   int ret = DADevice_->GetSignal(volt);
   if (ret != DEVICE_OK) 
      // DA Device cannot read, set position from cache
      pos = pos_;
   else
      pos = volt/(maxStageVolt_ - minStageVolt_) * (maxStagePos_ - minStagePos_) + originPos_;

   return DEVICE_OK;
}

/*
 * Sets a voltage (in mV) on the DA, relative to the minimum Stage position
 * The origin is NOT taken into account
 */
int DAZStage::SetPositionSteps(long steps)
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   // Interpret steps to be mV
   double volt = minStageVolt_  + (steps / 1000.0);
   if (volt < maxStageVolt_)
      DADevice_->SetSignal(volt);
   else
      return ERR_VOLT_OUT_OF_RANGE;

   pos_ = volt/(maxStageVolt_ - minStageVolt_) * (maxStagePos_ - minStagePos_) + originPos_;

   return DEVICE_OK;
}

int DAZStage::GetPositionSteps(long& steps)
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   double volt;
   int ret = DADevice_->GetSignal(volt);
   if (ret != DEVICE_OK)
      steps = (long) ((pos_ + originPos_)/(maxStagePos_ - minStagePos_) * (maxStageVolt_ - minStageVolt_) * 1000.0); 
   else
      steps = (long) ((volt - minStageVolt_) * 1000.0);

   return DEVICE_OK;
}

/*
 * Sets the origin (relative position 0) to the current absolute position
 */
int DAZStage::SetOrigin()
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   double volt;
   int ret = DADevice_->GetSignal(volt);
   if (ret != DEVICE_OK)
      return ret;

   // calculate absolute current position:
   originPos_ = volt/(maxStageVolt_ - minStageVolt_) * (maxStagePos_ - minStagePos_);

   if (originPos_ < minStagePos_ || originPos_ > maxStagePos_)
      return ERR_POS_OUT_OF_RANGE;

   return DEVICE_OK;
}

int DAZStage::GetLimits(double& min, double& max)
{
   min = minStagePos_;
   max = maxStagePos_;
   return DEVICE_OK;
}


///////////////////////////////////////
// Action Interface
//////////////////////////////////////
int DAZStage::OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(DADeviceName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string DADeviceName;
      pProp->Get(DADeviceName);
      MM::SignalIO* DADevice = (MM::SignalIO*) GetDevice(DADeviceName.c_str());
      if (DADevice != 0) {
         DADevice_ = DADevice;
         DADeviceName_ = DADeviceName;
      } else
         return ERR_INVALID_DEVICE_NAME;
      if (initialized_)
         DADevice_->GetLimits(minDAVolt_, maxDAVolt_);
   }
   return DEVICE_OK;
}

int DAZStage::OnStageMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minStageVolt_);
   }
   else if (eAct == MM::AfterSet)
   {
      double minStageVolt;
      pProp->Get(minStageVolt);
      if (minStageVolt >= minDAVolt_ && minStageVolt < maxDAVolt_)
         minStageVolt_ = minStageVolt;
      else
         return ERR_VOLT_OUT_OF_RANGE;
   }
   return DEVICE_OK;
}

int DAZStage::OnStageMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxStageVolt_);
   }
   else if (eAct == MM::AfterSet)
   {
      double maxStageVolt;
      pProp->Get(maxStageVolt);
      if (maxStageVolt > minDAVolt_ && maxStageVolt <= maxDAVolt_)
         maxStageVolt_ = maxStageVolt;
      else
         return ERR_VOLT_OUT_OF_RANGE;
   }
   return DEVICE_OK;
}

int DAZStage::OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(minStagePos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(minStagePos_);
   }
   return DEVICE_OK;
}

int DAZStage::OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(maxStagePos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(maxStagePos_);
   }
   return DEVICE_OK;
}
