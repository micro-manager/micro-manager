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

#include "GenericEntryFilter.h"
#include "GenericLinePacket.h"
#include "GenericPacketArray.h"

#include <boost/container/vector.hpp>
#include <boost/shared_ptr.hpp>


namespace mm
{
namespace logging
{
namespace internal
{


template <class TMetadata>
class GenericSink
{
private:
   boost::shared_ptr< GenericEntryFilter<TMetadata> > filter_;

protected:
   boost::shared_ptr< GenericEntryFilter<TMetadata> > GetFilter() const
   { return filter_; }

public:
   typedef GenericPacketArray<TMetadata> PacketArrayType;

   virtual ~GenericSink() {}
   virtual void Consume(const PacketArrayType& packets) = 0;

   // Note: If setting the filter while the sink is in use, you must pause the
   // logger. See the LoggingCore member function AtomicSetSinkFilters().
   void SetFilter(boost::shared_ptr< GenericEntryFilter<TMetadata> > filter)
   { filter_ = filter; }
};


} // namespace internal
} // namespace logging
} // namespace mm
