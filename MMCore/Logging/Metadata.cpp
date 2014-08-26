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

#include "Metadata.h"

#include <boost/thread.hpp>

#include <set>
#include <string>


namespace mm
{
namespace logging
{


const char*
LoggerData::InternString(const std::string& s)
{
   // Never remove strings from this set. Since we only ever insert into
   // this set, iterators (and thus const char* to the contained strings)
   // are never invalidated and can be used as a light-weight handle. Thus,
   // we need to protect only insertion by a mutex.
   static boost::mutex mutex;
   static std::set<std::string> strings;

   boost::lock_guard<boost::mutex> lock(mutex);
   return strings.insert(s).first->c_str();
}


} // namespace logging
} // namespace mm
