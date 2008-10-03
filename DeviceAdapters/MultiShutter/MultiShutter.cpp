///////////////////////////////////////////////////////////////////////////////
// FILE:          MultiShutter.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter that combines multiple physical shutters into a single logical shutter
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 10/01/2008
// COPYRIGHT:     University of California, San Francisco, 2008


#include "MultiShutter.h"
#include "../../MMDevice/ModuleInterface.h"

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


const char* g_DeviceNameMultiShutter = "Multi Shutter";
const char* g_Undefined = "Undefined";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_DeviceNameMultiShutter, "Combine multiple physical shutters into a single logical shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameMultiShutter) == 0)                        
   {                                                                         
      return new MultiShutter();
   }                                                                         

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// Multi Shutter implementation
///////////////////////////////////////////////////////////////////////////////
MultiShutter::MultiShutter() :
   nrPhysicalShutters_(5),
   open_(false),
   initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_INVALID_DEVICE_NAME, "Please select a valid shutter");

   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_DeviceNameMultiShutter, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Combines multiple physical shutters into a single ", MM::String, true);

   for (int i = 0; i < nrPhysicalShutters_; i++) {
      usedShutters_.push_back(g_Undefined);
      physicalShutters_.push_back(0);
   }
}  
 
MultiShutter::~MultiShutter()
{
   Shutdown();
}

void MultiShutter::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNameMultiShutter);
}                                                                            
                                                                             
int MultiShutter::Initialize() 
{
  // get list with available Shutters.   
  // TODO: this is a initialization parameter, which makes it harder for the end-user to set up!
   std::vector<std::string> availableShutters;
   availableShutters = GetLoadedDevicesOfType(MM::ShutterDevice);
   availableShutters_.push_back(g_Undefined);
   std::vector<std::string>::iterator iter;
   for (iter = availableShutters.begin(); iter != availableShutters.end(); iter++ ) {
      MM::Device* shutter = GetDevice((*iter).c_str());
      std::ostringstream os;
      os << this << " " << shutter;
      LogMessage(os.str().c_str());
      if (shutter &&  (this != shutter))
         availableShutters_.push_back(*iter);
   }

   for (long i = 0; i < nrPhysicalShutters_; i++) {
      CPropertyActionEx* pAct = new CPropertyActionEx (this, &MultiShutter::OnPhysicalShutter, i);
      std::ostringstream os;
      os << "Physical Shutter " << i+1;
      CreateProperty(os.str().c_str(), availableShutters_[0].c_str(), MM::String, false, pAct, false);
      SetAllowedValues(os.str().c_str(), availableShutters_);
   }

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool MultiShutter::Busy()
{
   std::vector<MM::Shutter*>::iterator iter;
   for (iter = physicalShutters_.begin(); iter != physicalShutters_.end(); iter++ ) {
      if ( (*iter != 0) && (*iter)->Busy())
         return true;
   }

   return false;
}

/*
 * Opens or closes all physical shutters.
 */
int MultiShutter::SetOpen(bool open)
{
   std::vector<MM::Shutter*>::iterator iter;
   for (iter = physicalShutters_.begin(); iter != physicalShutters_.end(); iter++ ) {
      if (*iter != 0) {
         int ret = (*iter)->SetOpen(open);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   return DEVICE_OK;
}

///////////////////////////////////////
// Action Interface
//////////////////////////////////////
int MultiShutter::OnPhysicalShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long i)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(usedShutters_[i].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string shutterName;
      pProp->Get(shutterName);
      if (shutterName == g_Undefined) {
         usedShutters_[i] = g_Undefined;
         physicalShutters_[i] = 0;
      } else {
         MM::Shutter* shutter = (MM::Shutter*) GetDevice(shutterName.c_str());
         if (shutter != 0) {
            usedShutters_[i] = shutterName;
            physicalShutters_[i] = shutter;
         } else
            return ERR_INVALID_DEVICE_NAME;
      }
   }

   return DEVICE_OK;
}
