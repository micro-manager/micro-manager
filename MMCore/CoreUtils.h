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
//#include "ace/High_Res_Timer.h"
//#include "ace/Log_Msg.h"

//#include "../../3rdparty/boost/boost/date_time/posix_time/posix_time.hpp"
#include "boost/date_time/posix_time/posix_time.hpp"


//#define CORE_LOG_PREFIX "LOG(%P, %t:): "

///////////////////////////////////////////////////////////////////////////////
// Utility classes
// ---------------



class TimeoutMs
{
	

public:
	// ASSUME boost::posix_time::time_duration contructor TAKES microseconds !!!!!!!!!!!!!!!!!
	TimeoutMs(double intervalMs):interval_(0,0,0,static_cast<boost::posix_time::time_duration::fractional_seconds_type>(0.5+intervalMs*1000.)), startTime_(boost::posix_time::microsec_clock::local_time() )
   {
   }
   ~TimeoutMs()
   {
    
   }
   bool expired()
   {
      boost::posix_time::time_duration elapsed = boost::posix_time::microsec_clock::local_time() - startTime_;
      return (interval_ < elapsed);
   }

private:
   TimeoutMs(const TimeoutMs&) {}
   const TimeoutMs& operator=(const TimeoutMs&) {return *this;}

   boost::posix_time::time_duration  interval_;
	boost::posix_time::ptime startTime_;
};

class TimerMs
{
public:
	TimerMs():startTime_(boost::posix_time::microsec_clock::local_time() )
   {
   }
   ~TimerMs()
   {
   }
   double elapsed()
   {
		boost::posix_time::time_duration delta = boost::posix_time::microsec_clock::local_time() - startTime_;

		return (double)delta.total_microseconds() ;
		//MM::MMTime mt0((long)(delta.ticks()/time_duration::rep_type::res_adjust()), (long) (1000000L*delta.fractional_seconds())/delta.ticks_per_second()   );
 		//return mt0.getMsec() + mt0.getUsec()/1000.;
		//return (double)( (long long)(delta.ticks()/time_duration::rep_type::res_adjust())*1000L + (long long)(delta.fractional_seconds()/1000L)) ;
   }

private:
	// ?
 //  TimerMs(const TimeoutMs&) {}
  // const TimerMs& operator=(const TimeoutMs&) {return *this;}

    boost::posix_time::ptime startTime_;
};

//NB we are starting the 'epoch' on 2000 01 01
inline MM::MMTime GetMMTimeNow()
{
	using namespace boost::posix_time;
	using namespace boost::gregorian;
	boost::posix_time::ptime t0 = boost::posix_time::microsec_clock::local_time();
	ptime timet_start(date(2000,1,1)); 
	time_duration diff = t0 - timet_start; 
	return MM::MMTime( (double) diff.total_microseconds());


   //struct timeval t;
   //gettimeofday(&t,NULL);
   //return MM::MMTime(t.tv_sec, t.tv_usec);
}

