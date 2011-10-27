///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreProperty.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Implements the "core property" mechanism. The MMCore exposes
//                some of its own settings as a virtual device.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/23/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// CVS:           $Id$
//

#include "CoreProperty.h"
#include "MMCore.h"
#include "Error.h"
#include "../MMDevice/DeviceUtils.h"
#include <assert.h>
#include <stdlib.h>
using namespace std;

vector<string> CoreProperty::GetAllowedValues() const
{
   vector<string> allowedVals;
   for (set<string>::const_iterator it=values_.begin(); it!=values_.end(); ++it)
      allowedVals.push_back(*it);
   return allowedVals;
}

void CoreProperty::AddAllowedValue(const char* value)
{
   values_.insert(value);
}

bool CoreProperty::IsAllowed(const char* value) const
{
   if (values_.size() == 0)
      return true;

   set<string>::const_iterator it;
   if (values_.find(value) == values_.end())
      return false;
   else
      return true;
}

bool CoreProperty::Set(const char* value)
{
   if (IsReadOnly())
      return false;

   value_ = value;
   return true;
}

string CoreProperty::Get() const
{
   return value_;
}

void CorePropertyCollection::Set(const char* propName, const char* value)
{
   map<string, CoreProperty>::iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   if (!it->second.IsAllowed(value) || it->second.IsReadOnly())
   {
      std::stringstream msg;
      msg << "Attempted to set \"Core-" << propName << "\" to \"" << value << "\".";
      throw CMMError(msg.str().c_str(), core_->getCoreErrorText(MMERR_InvalidCoreValue).c_str(), MMERR_InvalidCoreValue);
   }

   // execute property set command
   //
   it->second.Set(value); // throws on failure
}


void CorePropertyCollection::Execute(const char* propName, const char* value)
{
   Set(propName, value); // throws on failure
   
   // initialization
   if (strcmp(propName, MM::g_Keyword_CoreInitialize) == 0)
   {
      if (strcmp(value, "0") == 0)
         core_->unloadAllDevices();
      else if (strcmp(value, "1") == 0)
         core_->initializeAllDevices();
      else
         assert(!"Invalid value for the core property.\n");
   }
   else if (strcmp(propName, MM::g_Keyword_CoreAutoShutter) == 0)
   {
      if (strcmp(value, "0") == 0)
         core_->setAutoShutter(false);
      else if (strcmp(value, "1") == 0)
         core_->setAutoShutter(true);
      else
         assert(!"Invalid value for the core property.\n");
   }
  // shutter
   else if (strcmp(propName, MM::g_Keyword_CoreShutter) == 0)
   {
      core_->setShutterDevice(value);
   }
   // camera
   else if (strcmp(propName, MM::g_Keyword_CoreCamera) == 0)
   {
      core_->setCameraDevice(value);
   }
   // focus
   else if (strcmp(propName, MM::g_Keyword_CoreFocus) == 0)
   {
      core_->setFocusDevice(value);
   }
   // xy stage
   else if (strcmp(propName, MM::g_Keyword_CoreXYStage) == 0)
   {
      core_->setXYStageDevice(value);
   }
   else if (strcmp(propName, MM::g_Keyword_CoreAutoFocus) == 0)
   {
      core_->setAutoFocusDevice(value);
   }
   else if (strcmp(propName, MM::g_Keyword_CoreImageProcessor) == 0)
   {
      core_->setImageProcessorDevice(value);
   }
   else if (strcmp(propName, MM::g_Keyword_CoreSLM) == 0)
   {
      core_->setSLMDevice(value);
   }
   else if (strcmp(propName, MM::g_Keyword_CoreTimeoutMs) == 0)
   {
      core_->setTimeoutMs(atol(value));
   }
   else if (strcmp(propName, MM::g_Keyword_CoreChannelGroup) == 0)
   {
      core_->setChannelGroup(value);
   }
   // unknown property
   else
   {
      // should never get here...
      assert(!"Unable to execute set property command.\n");
   }

   if (core_->externalCallback_)
   {
      core_->externalCallback_->onPropertyChanged("Core", propName, value); 
   }
}

string CorePropertyCollection::Get(const char* propName) const
{
   map<string, CoreProperty>::const_iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   return it->second.Get();
}

bool CorePropertyCollection::Has(const char* propName) const
{
   map<string, CoreProperty>::const_iterator it = properties_.find(propName);
   if (it == properties_.end())
      return false; // not defined

   return true;
}

vector<string> CorePropertyCollection::GetNames() const
{
   vector<string> names;
   for (map<string, CoreProperty>::const_iterator it=properties_.begin(); it!=properties_.end(); ++it)
      names.push_back(it->first);
   return names;
}

void CorePropertyCollection::Refresh() 
{
   assert(core_);

   // Initialize
   // no need to update

   // Auto shutter
   Set(MM::g_Keyword_CoreAutoShutter, core_->getAutoShutter() ? "1" : "0");

   // Camera
   Set(MM::g_Keyword_CoreCamera, core_->getCameraDevice().c_str());

   // Shutter
   Set(MM::g_Keyword_CoreShutter, core_->getShutterDevice().c_str());

   // Focus
   Set(MM::g_Keyword_CoreFocus, core_->getFocusDevice().c_str());

   // XYStage
   Set(MM::g_Keyword_CoreXYStage, core_->getXYStageDevice().c_str());

   // Auto-Focus
   Set(MM::g_Keyword_CoreAutoFocus, core_->getAutoFocusDevice().c_str());

   // Image processor
   Set(MM::g_Keyword_CoreImageProcessor, core_->getImageProcessorDevice().c_str());

   // SLM
   Set(MM::g_Keyword_CoreSLM, core_->getSLMDevice().c_str());

   // Timeout for Device Busy checking
   Set(MM::g_Keyword_CoreTimeoutMs, CDeviceUtils::ConvertToString(core_->getTimeoutMs()));

   // Channel group
   Set(MM::g_Keyword_CoreChannelGroup, core_->getChannelGroup().c_str());

}

bool CorePropertyCollection::IsReadOnly(const char* propName) const
{
   map<string, CoreProperty>::const_iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   return it->second.IsReadOnly();
}

vector<string> CorePropertyCollection::GetAllowedValues(const char* propName) const
{
   map<string, CoreProperty>::const_iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   return it->second.GetAllowedValues();
}

void CorePropertyCollection::ClearAllowedValues(const char* propName)
{
   map<string, CoreProperty>::iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   it->second.ClearAllowedValues();
}

void CorePropertyCollection::AddAllowedValue(const char* propName, const char* value)
{
   map<string, CoreProperty>::iterator it = properties_.find(propName);
   if (it == properties_.end())
      throw CMMError(propName, core_->getCoreErrorText(MMERR_InvalidCoreProperty).c_str(), MMERR_InvalidCoreProperty);

   it->second.AddAllowedValue(value);
}


