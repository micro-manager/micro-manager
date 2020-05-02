///////////////////////////////////////////////////////////////////////////////
// FILE:          MMEventCallback.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback class used to send notifications from MMCore to
//                higher levels (such as GUI)
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 12/10/2007
// COPYRIGHT:     University of California, San Francisco, 2007
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
// CVS:           $Id: Configuration.h 2 2007-02-27 23:33:17Z nenad $
//
#pragma once
#include <iostream>

class MMEventCallback
{
public:
   MMEventCallback() {}
   virtual ~MMEventCallback() {}

   virtual void onPropertiesChanged()
   {
      std::cout << "onPropertiesChanged()" << std:: endl; 
   }

   virtual void onPropertyChanged(const char* name, const char* propName, const char* propValue)
   {
      std::cout << "onPropertyChanged() " << name << " " << propName << " " << propValue;
      std::cout << std:: endl; 
   }

   virtual void onChannelGroupChanged(const char* newChannelGroupName)
   {
      std::cout << "onChannelGroupChanged() " << newChannelGroupName << std::endl;
   }

   virtual void onConfigGroupChanged(const char* groupName, const char* newConfigName)
   {
      std::cout << "onConfigGroupChanged() " << groupName << " " << newConfigName;
      std::cout << std:: endl; 
   }

   virtual void onSystemConfigurationLoaded()
   {
      std::cout << "onSystemConfigurationLoaded() ";
      std::cout << std::endl;
   }

   virtual void onPixelSizeChanged(double newPixelSizeUm)
   {
      std::cout << "onPixelSizeChanged() " << newPixelSizeUm << std::endl;
   }

   virtual void onPixelSizeAffineChanged(double v0, double v1, double v2, double v3, double v4, double v5)
   {
      std::cout << "onPixelSizeAffineChanged() " << v0 << "-" << v1 << "-" << v2 << "-" << v3 << "-" << v4 << "-" << v5 << std::endl;
   }

   virtual void onStagePositionChanged(char* name, double pos)
   {
      std::cout << "onStagePositionChanged()" << name << " " << pos  << "\n"; 
   }

   virtual void onXYStagePositionChanged(char* name, double xpos, double ypos)
   {
      std::cout << "onXYStagePositionChanged()" << name << " " << xpos;
      std::cout << " " <<  ypos << "\n"; 
   }

   virtual void onExposureChanged(char* name, double newExposure)
   {
      std::cout << "onExposureChanged()" << name << " " << newExposure << "\n";
   }

   virtual void onSLMExposureChanged(char* name, double newExposure)
   {
      std::cout << "onSLMExposureChanged()" << name << " " << newExposure << "\n";
   }

};
