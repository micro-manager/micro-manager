///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreUtils.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Utility classes and functions for use in MMCore
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 09/27/2005
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

#pragma once

#include "../MMDevice/MMDevice.h"

// suppress hideous boost warnings
#ifdef WIN32
#pragma warning( push )
#pragma warning( disable : 4244 )
#pragma warning( disable : 4127 )
#endif

#include "boost/date_time/posix_time/posix_time.hpp"

#ifdef WIN32
#pragma warning( pop )
#endif

#include <boost/lexical_cast.hpp>
#include <string>


template <typename T>
inline std::string ToString(const T& d)
{ return boost::lexical_cast<std::string>(d); }

template <>
inline std::string ToString<const char*>(char const* const& d)
{
   if (!d) 
      return "(null)";
   return d;
}

template <>
inline std::string ToString<const MM::DeviceType>(const MM::DeviceType& d)
{
   // TODO Any good way to ensure this doesn't get out of sync with the enum
   // definition?
   switch (d)
   {
      case MM::UnknownType: return "Unknown";
      case MM::AnyType: return "Any";
      case MM::CameraDevice: return "Camera";
      case MM::ShutterDevice: return "Shutter";
      case MM::StateDevice: return "State";
      case MM::StageDevice: return "Stage";
      case MM::XYStageDevice: return "XYStageDevice";
      case MM::SerialDevice: return "Serial";
      case MM::GenericDevice: return "Generic";
      case MM::AutoFocusDevice: return "Autofocus";
      case MM::CoreDevice: return "Core";
      case MM::ImageProcessorDevice: return "ImageProcessor";
      case MM::SignalIODevice: return "SignalIO";
      case MM::MagnifierDevice: return "Magnifier";
      case MM::SLMDevice: return "SLM";
      case MM::HubDevice: return "Hub";
      case MM::GalvoDevice: return "Galvo";
   }
   return "Invalid";
}

template <typename T>
inline std::string ToQuotedString(const T& d)
{ return "\"" + ToString(d) + "\""; }

template <>
inline std::string ToQuotedString<const char*>(char const* const& d)
{
   if (!d) // Don't quote if null
      return ToString(d);
   return "\"" + ToString(d) + "\"";
}


//NB we are starting the 'epoch' on 2000 01 01
inline MM::MMTime GetMMTimeNow(boost::posix_time::ptime t0)
{
   boost::posix_time::ptime timet_start(boost::gregorian::date(2000,1,1)); 
   boost::posix_time::time_duration diff = t0 - timet_start; 
   return MM::MMTime( (double) diff.total_microseconds());
}

//NB we are starting the 'epoch' on 2000 01 01
inline MM::MMTime GetMMTimeNow()
{
   return GetMMTimeNow(boost::posix_time::microsec_clock::local_time());
}

