// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
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
// AUTHOR:        Mark Tsuchida

#pragma once

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

#include <boost/date_time/posix_time/posix_time.hpp>


namespace mm
{
namespace logging
{
namespace detail
{

// Platform-dependent types

typedef boost::posix_time::ptime TimestampType;

// Note: Boost's local_time() internally calls the C library function
// localtime_r() or localtime(). On the platforms we are interested in, either
// the thread-safe localtime_r() is provided (OS X, Linux), or localtime() is
// made thread-safe by using thread-local storage (Windows).
inline TimestampType
Now()
{ return boost::posix_time::microsec_clock::local_time(); }

inline void
WriteTimeToStream(std::ostream& stream, TimestampType timestamp)
{ stream << boost::posix_time::to_iso_extended_string(timestamp); }


#ifdef _WIN32
typedef DWORD ThreadIdType;

inline ThreadIdType
GetTid() { return ::GetCurrentThreadId(); }
#else
typedef pthread_t ThreadIdType;

inline ThreadIdType
GetTid() { return ::pthread_self(); }
#endif

} // namespace detail
} // namespace logging
} // namespace mm
