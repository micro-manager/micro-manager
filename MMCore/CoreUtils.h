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


///////////////////////////////////////////////////////////////////////////////
// Utility classes
// ---------------



//NB we are starting the 'epoch' on 2000 01 01
inline MM::MMTime GetMMTimeNow()
{
   using namespace boost::posix_time;
   using namespace boost::gregorian;
   boost::posix_time::ptime t0 = boost::posix_time::microsec_clock::local_time();
   ptime timet_start(date(2000,1,1)); 
   time_duration diff = t0 - timet_start; 
   return MM::MMTime( (double) diff.total_microseconds());
}

