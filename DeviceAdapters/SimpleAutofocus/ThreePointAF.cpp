///////////////////////////////////////////////////////////////////////////////
// FILE:          ThreePointAF.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Focusing with only three measurements
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, February 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010, http://www.100ximaging.com
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
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "SimpleAutofocus.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cmath>
#include <sstream>

using namespace std;

extern const char* g_TPFocusDeviceName;
extern const char* g_PropertyScore;
const char* g_PropertyStepSize = "StepSize";
const char* g_PropertyChannel = "FocusChannel";
const char* g_PropertyCropFactor = "CropFactor";

ThreePointAF::ThreePointAF():
		busy_(false), initialized_(false), score_(0.0), cropFactor_(1.0), stepSize_(1.0)
{
}

void ThreePointAF::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_TPFocusDeviceName);
}

int ThreePointAF::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_TPFocusDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Three point autofocus - 100X Inc", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set Exposure
   CPropertyAction *pAct = new CPropertyAction (this, &ThreePointAF::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct); 

   // step size
   pAct = new CPropertyAction(this, &ThreePointAF::OnStepsize);
   CreateProperty(g_PropertyStepSize, "10", MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &ThreePointAF::OnFocusChannel);
   CreateProperty(g_PropertyChannel, "", MM::String, false, pAct);

   // Set the cropping factor to speed up computation
   pAct = new CPropertyAction(this, &ThreePointAF::OnCropFactor);
   CreateProperty(g_PropertyCropFactor, "1.0", MM::Float, false, pAct);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ThreePointAF::Shutdown()
{
	return DEVICE_OK;
}

int ThreePointAF::FullFocus()
{
	return DEVICE_OK;
}

int ThreePointAF::SetOffset(double /* offset */)
{
	return DEVICE_OK;
}

int ThreePointAF::GetOffset(double &offset)
{
   offset = 0.0;
	return DEVICE_OK;
}

int ThreePointAF::GetLastFocusScore(double & score)
{
	score = score_;
	return DEVICE_OK;
}

// Calculate the focus score at a given point
int ThreePointAF::GetCurrentFocusScore(double &score)
{
   score = score_;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Properties
///////////////////////////////////////////////////////////////////////////////

int ThreePointAF::OnStepsize(MM::PropertyBase* /* pProp */, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
	}
	else if(eAct == MM::BeforeGet)
	{
	}
	return DEVICE_OK;
}

int ThreePointAF::OnFocusChannel(MM::PropertyBase* /* pProp */, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
	}
   else if(eAct == MM::BeforeGet)
   {
      std::vector<std::string> presets;
      presets.clear();

      char channelConfigName[MM::MaxStrLength];
      unsigned int channelConfigIterator = 0;
      for(;;)
      {
         GetCoreCallback()->GetChannelConfig(channelConfigName, channelConfigIterator++);
         if( 0 < strlen(channelConfigName))
         {
            presets.push_back( std::string(channelConfigName));
         }
         else
            break;
      }

      presets.push_back("");
      SetAllowedValues(g_PropertyChannel, presets);
   }

	return DEVICE_OK;
}


int ThreePointAF::OnExposure(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double exp = 0.0;
		pProp->Get(exp);
		exposure_ = exp;
	}
	else if(eAct == MM::BeforeGet)
	{
		pProp->Set(exposure_);
	}
	return DEVICE_OK;
}

int ThreePointAF::OnCropFactor(MM::PropertyBase *pProp, MM::ActionType eAct)
{
	if(eAct == MM::AfterSet)
	{
		double cr;
		pProp->Get(cr);
		cropFactor_ = cr;
	}
	else if(eAct == MM::BeforeGet)
	{
      pProp->Set(cropFactor_);
	}
	return DEVICE_OK;
}
