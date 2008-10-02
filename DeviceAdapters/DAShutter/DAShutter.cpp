///////////////////////////////////////////////////////////////////////////////
// FILE:          DAShutter.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter make a DA device behave like a shutter
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 09/29/2008
// COPYRIGHT:     University of California, San Francisco, 2008


#include "DAShutter.h"
#include "../../MMDevice/ModuleInterface.h"

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


const char* g_DeviceNameDAShutter = "DA Shutter";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameDAShutter, "DA used as a shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
      return 0;                                                              
                                                                             
   if (strcmp(deviceName, g_DeviceNameDAShutter) == 0)                        
   {                                                                         
      return new DAShutter();
   }                                                                         

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// Shutter implementation
///////////////////////////////////////////////////////////////////////////////
DAShutter::DAShutter() :
   DADeviceName_ (""),
   initialized_ (false),
   originalVolt_ (0.0)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_INVALID_DEVICE_NAME, "Please select a valid DA device");
   SetErrorText(ERR_NO_DA_DEVICE, "No DA Device selected");
   SetErrorText(ERR_NO_DA_DEVICE_FOUND, "No DA Device loaded");

   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_DeviceNameDAShutter, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "DA device that is used as a shutter", MM::String, true);

}  
 
DAShutter::~DAShutter()
{
   Shutdown();
}

void DAShutter::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNameDAShutter);
}                                                                            
                                                                             
int DAShutter::Initialize() 
{
  // get list with available DA devices.   TODO: this is a initialization parameter, which makes it harder for the end-user to set up!
   availableDAs_ = GetLoadedDevicesOfType(MM::SignalIODevice);

   CPropertyAction* pAct = new CPropertyAction (this, &DAShutter::OnDADevice);      
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

   ret = DADevice_->GetSignal(originalVolt_);
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool DAShutter::Busy()
{
   if (DADevice_ != 0)
      return DADevice_->Busy();

   // If we are here, there is a problem.  No way to report it.
   return false;
}

/*
 * Opens or closes the shutter.  Remembers voltage from the 'open' position
 */
int DAShutter::SetOpen(bool open)
{
   if (DADevice_ == 0)
      return ERR_NO_DA_DEVICE;

   if (open) {
      open_ = true;
      double volt;
      int ret = DADevice_->GetSignal(volt);
      if (ret != DEVICE_OK)
         return ret;
      if (volt > 0) {
         originalVolt_ = volt;
         return DEVICE_OK;
      }
      return DADevice_->SetSignal(originalVolt_);
   } else {
      int ret = DADevice_->GetSignal(originalVolt_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
      return DADevice_->SetSignal(0.0);
   }

   return DEVICE_OK;
}

///////////////////////////////////////
// Action Interface
//////////////////////////////////////
int DAShutter::OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct)
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
